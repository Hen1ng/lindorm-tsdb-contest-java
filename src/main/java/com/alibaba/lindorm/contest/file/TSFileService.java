package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.compress.*;
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
    public static final ThreadLocal<ArrayList<ByteBuffer>> STRING_BUFFER_LIST = ThreadLocal.withInitial(() -> new ArrayList<>(Constants.CACHE_VINS_LINE_NUMS * Constants.STRING_NUMS));
    public static final ThreadLocal<ArrayList<Row>> LIST_THREAD_LOCAL = ThreadLocal.withInitial(ArrayList::new);
    public static final ThreadLocal<GzipCompress> GZIP_COMPRESS_THREAD_LOCAL = ThreadLocal.withInitial(GzipCompress::new);
    public static final ThreadLocal<long[]> INT_ARRAY_BUFFER = ThreadLocal.withInitial(() -> new long[Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS]);
    public static final ThreadLocal<double[]> DOUBLE_ARRAY_BUFFER = ThreadLocal.withInitial(() -> new double[Constants.CACHE_VINS_LINE_NUMS * Constants.FLOAT_NUMS]);
    public static final ThreadLocal<long[]> LONG_ARRAY_BUFFER = ThreadLocal.withInitial(() -> new long[Constants.CACHE_VINS_LINE_NUMS ]);

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
                                    doubles = DoubleCompress.decode2(ByteBuffer.wrap(allocate1), Constants.FLOAT_NUMS * valueSize, valueSize);
//                                    final byte[] bytes = Zstd.decompress(allocate1, valueSize * Constants.FLOAT_NUMS * 8);
//                                    final ByteBuffer wrap = ByteBuffer.wrap(bytes);
//                                    doubles = new double[bytes.length / 8];
//                                    for (int i1 = 0; i1 < doubles.length; i1++) {
//                                        doubles[i1] = wrap.getDouble();
//                                    }
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
                                final byte[] bytes = new byte[everyStringLength-4];
                                dataBuffer.position(10 + compressLength
                                        + intCompressLength + 2
                                        + doubleCompressInt + 2
                                        + 2);
                                int totalLength = dataBuffer.getInt();
                                dataBuffer.get(bytes,0,bytes.length);
                                final short[] decompress1 = IntCompress.decompressShort(bytes,index.getValueSize(),totalLength);
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
                if (aLong == timestamp) {
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
                                    doubles = DoubleCompress.decode2(ByteBuffer.wrap(allocate1), Constants.FLOAT_NUMS * valueSize, valueSize);
//                                    final byte[] bytes = Zstd.decompress(allocate1, valueSize * Constants.FLOAT_NUMS * 8);
//                                    final ByteBuffer wrap = ByteBuffer.wrap(bytes);
//                                    doubles = new double[bytes.length / 8];
//                                    for (int i1 = 0; i1 < doubles.length; i1++) {
//                                        doubles[i1] = wrap.getDouble();
//                                    }
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
                                final byte[] bytes = new byte[everyStringLength-4];
                                dataBuffer.position(10 + compressLength
                                        + intCompressLength + 2
                                        + doubleCompressInt + 2
                                        + 2);
                                int totalLength = dataBuffer.getInt();
                                dataBuffer.get(bytes,0,bytes.length);
                                final short[] decompress1 = IntCompress.decompressShort(bytes,index.getValueSize(),totalLength);
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
                    return new Row(vin, aLong, columns);
                }
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("getByIndexV2 e" + e);
            System.exit(-1);
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
            writeTimes.getAndIncrement();
            int m = j % Constants.TS_FILE_NUMS;
            String[] indexArray = SchemaUtil.getIndexArray();
            ByteBuffer intBuffer;
            ByteBuffer doubleBuffer;
            ByteBuffer longBuffer;
            ByteBuffer stringLengthBuffer;
            List<ByteBuffer> stringList;
            double[] doubles;
            long[] longs ;
            long[] ints;
            int longPosition = 0;
            int doublePosition = 0;
            int intPosition = 0;
            if (lineNum == Constants.CACHE_VINS_LINE_NUMS) {
                intBuffer = TOTAL_INT_BUFFER.get();
                doubleBuffer = TOTAL_DOUBLE_BUFFER.get();
                longBuffer = TOTAL_LONG_BUFFER.get();
                stringLengthBuffer = TOTAL_STRING_LENGTH_BUFFER.get();
                stringList = STRING_BUFFER_LIST.get();

                ints = INT_ARRAY_BUFFER.get();
                Arrays.fill(ints, 0);

                doubles = DOUBLE_ARRAY_BUFFER.get();
                Arrays.fill(doubles, 0);

                longs = LONG_ARRAY_BUFFER.get();
                Arrays.fill(longs, 0);

                intBuffer.clear();
                doubleBuffer.clear();
                longBuffer.clear();
                stringLengthBuffer.clear();
                stringList.clear();
            } else {
                ints = new long[lineNum * Constants.INT_NUMS];
                doubles = new double[lineNum * Constants.FLOAT_NUMS];
                longs = new long[lineNum];
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
                        final double doubleFloatValue = columns.get(key).getDoubleFloatValue();
                        aggBucket.updateDouble(doubleFloatValue, i);
                        doubles[doublePosition] = doubleFloatValue;
                        doublePosition++;
                    } else {
                        final ByteBuffer stringValue = columns.get(key).getStringValue();
                        totalStringLength += stringValue.remaining();
                        stringList.add(stringValue);
                    }
                }
            }
            //压缩string
            CompressResult compressResult = StringCompress.compress1(stringList, lineNum);
            final byte[] compress = compressResult.compressedData;
            short[] stringLengthArray = compressResult.stringLengthArray;

            //压缩double
            final byte[] compressDouble = DoubleCompress.encode2(doubles, valueList.size());
            // 压缩long
            final byte[] compress1 = LongCompress.compress(longs);
            long previousLong = longs[longs.length - 1];

            //压缩int
            byte[] compress2 = IntCompress.compress2(ints);
            byte[] stringLengthArrayCompress = IntCompress.compressShort(stringLengthArray,lineNum);
            int total = 8 + 2 + compress1.length //timestamp
                    + compress2.length + 2 //int
                    + (2 + compressDouble.length) //double
                    + stringLengthArrayCompress.length + 2 //string长度记录
                    + compress.length; //string存储string
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(total);
            try {
                StaticsUtil.STRING_BYTE_LENGTH.getAndAdd(stringLengthArrayCompress.length);
                StaticsUtil.STRING_SHORT_LENGTH.getAndAdd(stringLengthArray.length* 2L);
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
            if (writeTimes.get() % 1000000 == 0) {
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

    public ByteBuffer getTimestampList(Index index, int j) {
        final long offset = index.getOffset();
        final int valueSize = index.getValueSize();
        int m = j % Constants.TS_FILE_NUMS;
        final TSFile tsFile = getTsFileByIndex(m);
        ByteBuffer timestampBuffer;
        if (valueSize == Constants.CACHE_VINS_LINE_NUMS) {
            timestampBuffer = TOTAL_LONG_BUFFER.get();
            timestampBuffer.clear();
        } else {
            timestampBuffer = ByteBuffer.allocateDirect(valueSize * 8);
        }
        tsFile.getFromOffsetByFileChannel(timestampBuffer, offset);
        timestampBuffer.flip();
        return timestampBuffer;
    }

}

