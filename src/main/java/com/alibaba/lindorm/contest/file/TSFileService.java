package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.compress.GzipCompress;
import com.alibaba.lindorm.contest.compress.IntCompress;
import com.alibaba.lindorm.contest.compress.LongCompress;
import com.alibaba.lindorm.contest.compress.StringCompress;
import com.alibaba.lindorm.contest.index.AggBucket;
import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.index.MapIndex;
import com.alibaba.lindorm.contest.memory.Value;
import com.alibaba.lindorm.contest.structs.ColumnValue;
import com.alibaba.lindorm.contest.structs.Row;
import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.*;
import com.github.luben.zstd.Zstd;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;


public class TSFileService {

    public static final ThreadLocal<ByteBuffer> TOTAL_INT_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocate(Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS * 4));
    public static final ThreadLocal<ByteBuffer> TOTAL_DOUBLE_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS * 8));
    public static final ThreadLocal<ByteBuffer> TOTAL_LONG_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * 8));
    public static final ThreadLocal<ByteBuffer> TOTAL_STRING_LENGTH_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocate(Constants.CACHE_VINS_LINE_NUMS * Constants.STRING_NUMS * 4));
    public static final ThreadLocal<ByteBuffer> INT_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocate(4));
    public static final ThreadLocal<ByteBuffer> DOUBLE_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocate(8));
    public static final ThreadLocal<ArrayList<ByteBuffer>> STRING_BUFFER_LIST = ThreadLocal.withInitial(() -> new ArrayList<>(Constants.CACHE_VINS_LINE_NUMS * Constants.STRING_NUMS));
    public static final ThreadLocal<ArrayList<Row>> LIST_THREAD_LOCAL = ThreadLocal.withInitial(ArrayList::new);
    public static final ThreadLocal<GzipCompress> GZIP_COMPRESS_THREAD_LOCAL = ThreadLocal.withInitial(GzipCompress::new);

    private final TSFile[] tsFiles;
    private final AtomicLong writeTimes = new AtomicLong(0);

    public TSFileService(String file) {
        this.tsFiles = new TSFile[Constants.TS_FILE_NUMS];
        for (int i = 0; i < Constants.TS_FILE_NUMS; i++) {
            long initPosition = (long) i * Constants.TS_FILE_SIZE;
            tsFiles[i] = new TSFile(file, i, initPosition);
        }
    }

    public TSFile getTsFileByVin(Vin vin) {
        final byte[] vin1 = vin.getVin();
        final int hash = getHashCodeByByteArray(vin1);
        int slot = hash % Constants.TS_FILE_NUMS;
        return tsFiles[slot];
    }

    public TSFile getTsFileByIndex(int i) {
        return tsFiles[i];
    }

    public int getHashCodeByByteArray(byte[] bytes) {
        int h = 0;
        for (int i = 0; i < 17; i++) {
            h = 31 * h + bytes[i];
        }
        return Math.abs(h);
    }

    public ArrayList<Row> getByIndexV2(Vin vin, long timeLowerBound, long timeUpperBound, Index index, Set<String> requestedColumns, int j) {
        ArrayList<Row> rowArrayList = LIST_THREAD_LOCAL.get();
        rowArrayList.clear();
        try {
            final long offset = index.getOffset();
            final int valueSize = index.getValueSize();
            final int length = index.getLength();
            int m = j % Constants.TS_FILE_NUMS;
            final TSFile tsFile = getTsFileByIndex(m);
            ByteBuffer dataBuffer = ByteBuffer.allocate(length);
            tsFile.getFromOffsetByFileChannel(dataBuffer, offset);
            dataBuffer.flip();
            long longPrevious = dataBuffer.getLong();
            int compressLength = dataBuffer.getShort();
            byte[] longBytes = new byte[compressLength];
            dataBuffer.get(longBytes);
            long[] decompress = LongCompress.decompress(longBytes, longPrevious, valueSize);
            int intCompressLength = dataBuffer.getShort();
            int doubleCompressInt = dataBuffer.getShort(10 + compressLength + intCompressLength + 2);
            int i = 0;//多少行
            long[] ints = null;
            double[] doubles = null;
            List<ByteBuffer> stringBytes = null;
            Short everyStringLength = null;
            ByteBuffer stringLengthBuffer = null;
            short totalStringLength = 0;
            for (long aLong : decompress) {
                if (aLong >= timeLowerBound && aLong < timeUpperBound) {
                    Map<String, ColumnValue> columns = new HashMap<>(requestedColumns.size());
                    for (String requestedColumn : requestedColumns) {
                        final int columnIndex = SchemaUtil.getIndexByColumn(requestedColumn); //多少列
                        final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(requestedColumn);
                        if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_INTEGER)) {
                            try {
                                if (ints == null) {
                                    final byte[] allocate1 = new byte[intCompressLength];
                                    dataBuffer.position(10 + compressLength + 2);
                                    dataBuffer.get(allocate1);
                                    ints = IntCompress.decompress2(allocate1, index.getValueSize() * Constants.INT_NUMS);
                                }
                                int off = columnIndex * valueSize + i;
                                columns.put(requestedColumn, new ColumnValue.IntegerColumn((int) ints[off]));

                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                            }
                        } else if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT)) {
                            try {
                                if (doubles == null) {
                                    final byte[] allocate1 = new byte[doubleCompressInt];
                                    dataBuffer.position(10 + compressLength
                                            + intCompressLength + 2
                                            + 2);
                                    dataBuffer.get(allocate1);
                                    final byte[] bytes = Zstd.decompress(allocate1, valueSize * Constants.FLOAT_NUMS * 8);
                                    final ByteBuffer wrap = ByteBuffer.wrap(bytes);
                                    doubles = new double[bytes.length / 8];
                                    for (int i1 = 0; i1 < doubles.length; i1++) {
                                        doubles[i1] = wrap.getDouble();
                                    }
                                }
                                int position = ((columnIndex - Constants.INT_NUMS) * valueSize + i);
                                columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubles[position]));
                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                            }
                        } else {
                            if (stringLengthBuffer == null) {
                                everyStringLength = dataBuffer.getShort(10 + compressLength
                                        + intCompressLength + 2
                                        + doubleCompressInt + 2);
                                final byte[] bytes = new byte[everyStringLength];
                                dataBuffer.position(10 + compressLength
                                        + intCompressLength + 2
                                        + doubleCompressInt + 2
                                        + 2);
                                dataBuffer.get(bytes);
                                final short[] decompress1 = IntCompress.decompressShort(bytes);
                                stringLengthBuffer = ByteBuffer.allocateDirect(decompress1.length * 2);
                                for (short i1 : decompress1) {
                                    totalStringLength += i1;
                                    stringLengthBuffer.putShort(i1);
                                }
                            }
                            if (stringBytes == null) {
                                int stringLength = length - (
                                        10 + compressLength
                                                + intCompressLength + 2
                                                + doubleCompressInt + 2
                                                + everyStringLength + 2
                                );
                                byte[] bytes = new byte[stringLength-4];
                                dataBuffer.position(10 + compressLength
                                        + intCompressLength + 2
                                        + doubleCompressInt + 2
                                        + everyStringLength + 2
                                );
                                int totalLength = dataBuffer.getInt();
                                dataBuffer.get(bytes,0,bytes.length);
                                stringBytes = StringCompress.decompress1(bytes, stringLengthBuffer,index.getValueSize(),totalLength);
                            }
                            try {
                                int stringNum = columnIndex - (Constants.INT_NUMS + Constants.FLOAT_NUMS);
                                int stringPosition = (stringNum * valueSize + i);
                                columns.put(requestedColumn, new ColumnValue.StringColumn(stringBytes.get(stringPosition)));
                            } catch (Exception e) {
                                System.out.println("getByIndex time range String error, e:" + e + "index:" + index);
                            }
                        }
                    }
                    rowArrayList.add(new Row(vin, aLong, columns));
                }
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("getByIndexV2 e" + e);
            System.exit(-1);
        }
        return rowArrayList;
    }


    public Row getByIndex(Vin vin, long timestamp, Index index, Set<String> requestedColumns, int j) {
        final long offset = index.getOffset();
        final int valueSize = index.getValueSize();
        final int length = index.getLength();
        int m = j % Constants.TS_FILE_NUMS;
        final TSFile tsFile = getTsFileByIndex(m);
        //解压long
        ByteBuffer timestampMetaBuffer = ByteBuffer.allocateDirect(10);
        ByteBuffer stringLengthBuffer = null;
        tsFile.getFromOffsetByFileChannel(timestampMetaBuffer, offset);
        timestampMetaBuffer.flip();
        long longPrevious = timestampMetaBuffer.getLong();
        int compressLength = timestampMetaBuffer.getShort();
        ByteBuffer compressLong = ByteBuffer.allocate(compressLength);
        tsFile.getFromOffsetByFileChannel(compressLong, offset + 10);
        final long[] decompress = LongCompress.decompress(compressLong.array(), longPrevious, valueSize);
        //解压int
        long[] ints = null;
        final ByteBuffer allocate = ByteBuffer.allocate(2);
        tsFile.getFromOffsetByFileChannel(allocate, offset + 10 + compressLength);
        allocate.flip();
        final int intCompressLength = allocate.getShort();
        //解压double
        final ByteBuffer byteBuffer1 = ByteBuffer.allocateDirect(2);
        tsFile.getFromOffsetByFileChannel(byteBuffer1, offset + 10 + compressLength + intCompressLength + 2);
        byteBuffer1.flip();
        final int doubleCompressInt = byteBuffer1.getShort();
        double[] doubles = null;
        //string
        List<ByteBuffer> stringBytes = null;
        short everyStringLength = -1;
        int totalStringLength = 0;
        int i = 0;//多少行
        for (long aLong : decompress) {
            if (aLong == timestamp) {
                Map<String, ColumnValue> columns = new HashMap<>(requestedColumns.size());
                for (String requestedColumn : requestedColumns) {
                    final int columnIndex = SchemaUtil.getIndexByColumn(requestedColumn); //多少列 44
                    final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(requestedColumn);
                    if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_INTEGER)) {
                        try {
                            if (ints == null) {
                                final ByteBuffer allocate1 = ByteBuffer.allocate(intCompressLength);
                                tsFile.getFromOffsetByFileChannel(allocate1, offset + 10 + compressLength + 2);
                                allocate1.flip();
                                ints = IntCompress.decompress2(allocate1.array(), index.getValueSize() * Constants.INT_NUMS);
                            }
                            final ByteBuffer intBuffer = INT_BUFFER.get();
                            intBuffer.clear();
                            int off = columnIndex * valueSize + i;
                            columns.put(requestedColumn, new ColumnValue.IntegerColumn((int) ints[off]));
                        } catch (Exception e) {
                            System.out.println("getByIndex COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                        }
                    } else if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT)) {
                        try {
                            if (doubles == null) {
                                final ByteBuffer byteBuffer = ByteBuffer.allocate(doubleCompressInt);
                                tsFile.getFromOffsetByFileChannel(byteBuffer, offset + 10 + compressLength
                                        + intCompressLength + 2
                                        + 2);
                                final byte[] array = byteBuffer.array();
                                final byte[] bytes = Zstd.decompress(array, valueSize * Constants.FLOAT_NUMS * 8);
                                final ByteBuffer wrap = ByteBuffer.wrap(bytes);
                                doubles = new double[bytes.length / 8];
                                for (int i1 = 0; i1 < doubles.length; i1++) {
                                    doubles[i1] = wrap.getDouble();
                                }
                            }
                            int position = ((columnIndex - Constants.INT_NUMS) * valueSize + i);
                            columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubles[position]));
                        } catch (Exception e) {
                            System.out.println("getByIndex COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                        }
                    } else {
                        try {
                            if (stringLengthBuffer == null) {
                                stringLengthBuffer = ByteBuffer.allocateDirect(2);
                                tsFile.getFromOffsetByFileChannel(stringLengthBuffer, offset
                                        + 10 + compressLength
                                        + intCompressLength + 2
                                        + doubleCompressInt + 2);
                                stringLengthBuffer.flip();
                                everyStringLength = stringLengthBuffer.getShort();
                                final ByteBuffer byteBuffer = ByteBuffer.allocate(everyStringLength);
                                tsFile.getFromOffsetByFileChannel(byteBuffer, offset
                                        + 10 + compressLength
                                        + intCompressLength + 2
                                        + doubleCompressInt + 2
                                        + 2);
                                final byte[] array = byteBuffer.array();
                                final short[] decompress1 = IntCompress.decompressShort(array);
                                stringLengthBuffer = ByteBuffer.allocateDirect(decompress1.length * 2);
                                for (short i1 : decompress1) {
                                    totalStringLength += i1;
                                    stringLengthBuffer.putShort(i1);
                                }
                            }
                            if (stringBytes == null) {
                                int stringLength = length -
                                        (10 + compressLength  //long
                                                + intCompressLength + 2 // int
                                                + doubleCompressInt + 2 // double
                                                + everyStringLength + 2);
                                final ByteBuffer stringBuffer = ByteBuffer.allocate(stringLength);
                                tsFile.getFromOffsetByFileChannel(stringBuffer, offset
                                        + 10 + compressLength
                                        + intCompressLength + 2
                                        + doubleCompressInt + 2
                                        + everyStringLength + 2);
                                stringBuffer.flip();
                                int totalLength = stringBuffer.getInt();
                                byte[] bytes = new byte[stringLength-4];
                                stringBuffer.get(bytes,0,bytes.length);
                                stringBytes = StringCompress.decompress1(bytes, stringLengthBuffer,index.getValueSize(),totalLength);
                            }
                            int stringNum = columnIndex - (Constants.INT_NUMS + Constants.FLOAT_NUMS);
                            int stringPosition = (stringNum * valueSize + i) ;
                            columns.put(requestedColumn,new ColumnValue.StringColumn(stringBytes.get(stringPosition)));
                        } catch (Exception e) {
                            System.out.println("getByIndex String error, e:" + e + "index:" + index);
                            e.printStackTrace();
                        }
                    }
                }
                return new Row(vin, aLong, columns);
            }
            i++;
        }
        return null;
    }


    /**
     * @param vin
     * @param valueList
     * @param lineNum
     */
    public void write(Vin vin, List<Value> valueList, int lineNum, int j) {
        try {
            AggBucket aggBucket = new AggBucket();
            long start = System.currentTimeMillis();
            final long writeTimes = this.writeTimes.getAndIncrement();
            int m = j % Constants.TS_FILE_NUMS;
            String[] indexArray = SchemaUtil.getIndexArray();
            ByteBuffer intBuffer;
            ByteBuffer doubleBuffer;
            ByteBuffer longBuffer;
            ByteBuffer stringLengthBuffer;
            List<ByteBuffer> stringList;
            double[] doubles = null;
            long[] longs = new long[lineNum];
            long[] ints = new long[lineNum * Constants.INT_NUMS];
            short[] stringLengthArray = new short[lineNum * Constants.STRING_NUMS];
            int stringLengthPosition = 0;
            int longPosition = 0;
            int doublePosition = 0;
            int intPosition = 0;
            if (lineNum == Constants.CACHE_VINS_LINE_NUMS) {
                intBuffer = TOTAL_INT_BUFFER.get();
                doubleBuffer = TOTAL_DOUBLE_BUFFER.get();
                longBuffer = TOTAL_LONG_BUFFER.get();
                stringLengthBuffer = TOTAL_STRING_LENGTH_BUFFER.get();
                stringList = STRING_BUFFER_LIST.get();
                intBuffer.clear();
                doubleBuffer.clear();
                longBuffer.clear();
                stringLengthBuffer.clear();
                stringList.clear();
            } else {
                //存储int类型，大小为缓存数据行 * 每行多少个int * 4
                intBuffer = ByteBuffer.allocate(lineNum * Constants.INT_NUMS * 4);
                //存储double类型，大小为缓存数据行 * 每行多少个double * 8
                doubleBuffer = ByteBuffer.allocateDirect(lineNum * Constants.FLOAT_NUMS * 8);
                //存储时间戳，总共有多少行，有多少个时间戳
                longBuffer = ByteBuffer.allocateDirect(lineNum * 8);
                //存储每个字符串的长度
                stringLengthBuffer = ByteBuffer.allocate(lineNum * Constants.STRING_NUMS * 4);
                stringList = new ArrayList<>(lineNum * Constants.STRING_NUMS);
            }
            int totalStringLength = 0;
            long maxTimestamp = Long.MIN_VALUE;
            long minTimestamp = Long.MAX_VALUE;
            for (int i = 0; i < indexArray.length; i++) {
                final String key = indexArray[i];
                for (Value value : valueList) {
                    long timestamp = value.getTimestamp();
                    maxTimestamp = Math.max(maxTimestamp, timestamp);
                    minTimestamp = Math.min(minTimestamp, timestamp);
                    if (i == 0) {
                        longs[longPosition++] = value.getTimestamp();
                    }
                    Map<String, ColumnValue> columns = value.getColumns();
                    if (i < Constants.INT_NUMS) {
                        int integerValue = columns.get(key).getIntegerValue();
                        aggBucket.updateInt(integerValue, i);
//                        SchemaUtil.maps.get(key).add(integerValue);
                        ints[intPosition++] = integerValue;
                    } else if (i < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                        if (doubles == null) {
                            doubles = new double[lineNum * Constants.FLOAT_NUMS];
                        }
                        final double doubleFloatValue = columns.get(key).getDoubleFloatValue();
                        aggBucket.updateDouble(doubleFloatValue, i);
                        doubles[doublePosition] = doubleFloatValue;
                        doublePosition++;
                    } else {
                        final ByteBuffer stringValue = columns.get(key).getStringValue();
                        totalStringLength += stringValue.remaining();
                        stringList.add(stringValue);
                        stringLengthArray[stringLengthPosition++] = (short) stringValue.remaining();
                    }
                }
            }
            //压缩string
//            byte[] bytes = new byte[totalStringLength];
//            int position = 0;
//            for (ByteBuffer buffer : stringList) {
//                final byte[] array = buffer.array();
//                ArrayUtils.copy(array, 0, bytes, position, array.length);
//                position += array.length;
//            }
            final byte[] compress = StringCompress.compress1(stringList,lineNum);
//            if (writeTimes == 10000) {
//                System.out.println("--------------------------------------------------------------------");
//                for (ByteBuffer byteBuffer : stringList) {
//                    if (byteBuffer.capacity() == 0) {
//                        System.out.println("\"\"");
//                    } else {
//                        System.out.print(new String(byteBuffer.array()));
//                    }
//                    System.out.print(",");
//
//                }
//                System.out.println("--------------------------------------------------------------------");
//            }
            //压缩double
            final ByteBuffer allocate = ByteBuffer.allocate(doubles.length * 8);
            for (double value : doubles) {
                allocate.putDouble(value);
            }
            final byte[] array = allocate.array();
            final byte[] compressDouble = Zstd.compress(array, 12);
            // 压缩long
            final byte[] compress1 = LongCompress.compress(longs);
            long previousLong = longs[longs.length - 1];

            //压缩int
            byte[] compress2 = IntCompress.compress2(ints);
            byte[] stringLengthArrayCompress = IntCompress.compressShort(stringLengthArray);
            int total = 8 + 2 + compress1.length //timestamp
                    + compress2.length + 2 //int
                    + (2 + compressDouble.length) //double
                    + stringLengthArrayCompress.length + 2 //string长度记录
                    + compress.length; //string存储string
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(total);
            try {
                StaticsUtil.STRING_TOTAL_LENGTH.getAndAdd(totalStringLength);
                StaticsUtil.STRING_COMPRESS_LENGTH.getAndAdd(compress.length + stringLengthArrayCompress.length + 2);
                StaticsUtil.DOUBLE_COMPRESS_LENGTH.getAndAdd(2 + compressDouble.length);
                StaticsUtil.LONG_COMPRESS_LENGTH.getAndAdd(8 + 2 + compress1.length);
                StaticsUtil.INT_COMPRESS_LENGTH.getAndAdd(compress2.length + 2);
                //long
                byteBuffer.putLong(previousLong);
                byteBuffer.putShort((short) compress1.length);
                byteBuffer.put(compress1);
                //int
                byteBuffer.putShort((short) compress2.length);
                byteBuffer.put(compress2);
                // double
                byteBuffer.putShort((short) compressDouble.length);
                byteBuffer.put(compressDouble);
                //string
                stringLengthBuffer.flip();
                byteBuffer.putShort((short) stringLengthArrayCompress.length);
                byteBuffer.put(stringLengthArrayCompress);
                byteBuffer.put(compress);
            } catch (Exception e) {
                System.out.println("write bytebuffer error" + e);
            }
            try {
                TSFile tsFile = getTsFileByIndex(m);
                final long append = tsFile.append(byteBuffer);
                final Index index = new Index(append
                        , maxTimestamp
                        , minTimestamp
                        , total
                        , lineNum
                        , aggBucket);
                MapIndex.put(j, index);
                valueList.clear();
            } catch (Exception e) {
                System.out.println("write append error" + e);
            }
            if (this.writeTimes.get() % 1000000 == 0) {
                System.out.println("write cost: " + (System.currentTimeMillis() - start) + " ms, write size: " + total);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("write to file error, e :" + e.getLocalizedMessage() + "value size:" + valueList.size());
            System.exit(-1);
        }
    }

    public TSFile[] getTsFiles() {
        return tsFiles;
    }

    public List<Long> getTimestampList(Index index, int j) {
        final long offset = index.getOffset();
        final int valueSize = index.getValueSize();
        int m = j % Constants.TS_FILE_NUMS;
        final TSFile tsFile = getTsFileByIndex(m);
        ByteBuffer timestampMetaBuffer = ByteBuffer.allocateDirect(10);
        ByteBuffer stringLengthBuffer = null;
        tsFile.getFromOffsetByFileChannel(timestampMetaBuffer, offset);
        timestampMetaBuffer.flip();
        long longPrevious = timestampMetaBuffer.getLong();
        int compressLength = timestampMetaBuffer.getShort();
        ByteBuffer compressLong = ByteBuffer.allocate(compressLength);
        tsFile.getFromOffsetByFileChannel(compressLong, offset + 10);
        long[] decompress = LongCompress.decompress(compressLong.array(), longPrevious, valueSize);
        List<Long> result = new ArrayList<>();
        for (long aLong : decompress) {
            result.add(aLong);
        }
        return result;

    }

}

