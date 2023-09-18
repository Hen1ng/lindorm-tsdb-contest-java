package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.compress.GzipCompress;
import com.alibaba.lindorm.contest.compress.IntCompress;
import com.alibaba.lindorm.contest.compress.LongCompress;
import com.alibaba.lindorm.contest.index.AggBucket;
import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.index.MapIndex;
import com.alibaba.lindorm.contest.memory.Value;
import com.alibaba.lindorm.contest.structs.ColumnValue;
import com.alibaba.lindorm.contest.structs.Row;
import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.*;
import com.github.luben.zstd.Zstd;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
    public static final ThreadLocal<AtomicInteger[]> INT_POSITION_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        AtomicInteger[] bigIntPosition = new AtomicInteger[Constants.intColumnHashMapCompress.getColumnSize()];
        for (int i = 0; i < bigIntPosition.length; i++) {
            bigIntPosition[i] = new AtomicInteger();
        }
        return bigIntPosition;
    });

    public static final ThreadLocal<AtomicInteger[]> DOUBLE_POSITION_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        AtomicInteger[] bigIntPosition = new AtomicInteger[Constants.doubleColumnHashMapCompress.getColumnSize()];
        for (int i = 0; i < bigIntPosition.length; i++) {
            bigIntPosition[i] = new AtomicInteger();
        }
        return bigIntPosition;
    });

    private final TSFile[] tsFiles;
    private final AtomicLong writeTimes = new AtomicLong(0);

    public TSFileService(String file, File indexFile) {
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

    public ArrayList<Row> getByIndex(Vin vin, long timeLowerBound, long timeUpperBound, Index index, Set<String> requestedColumns, int j) {
        ArrayList<Row> rowArrayList = LIST_THREAD_LOCAL.get();
        rowArrayList.clear();
        try {
            final long offset = index.getOffset();
            final int valueSize = index.getValueSize();
            final int length = index.getLength();
            int m = j % Constants.TS_FILE_NUMS;
            final TSFile tsFile = getTsFileByIndex(m);
            ByteBuffer timestampMetaBuffer = ByteBuffer.allocateDirect(12);
            ByteBuffer stringLengthBuffer = null;
            tsFile.getFromOffsetByFileChannel(timestampMetaBuffer, offset);
            timestampMetaBuffer.flip();
            long longPrevious = timestampMetaBuffer.getLong();
            int compressLength = timestampMetaBuffer.getInt();
            ByteBuffer compressLong = ByteBuffer.allocate(compressLength);
            tsFile.getFromOffsetByFileChannel(compressLong, offset + 12);
            final long[] decompress = LongCompress.decompress(compressLong.array(), longPrevious, valueSize);
            int[] ints = null;
            final ByteBuffer allocate = ByteBuffer.allocate(4);
            tsFile.getFromOffsetByFileChannel(allocate, offset + 12 + compressLength);
            allocate.flip();
            final int intCompressLength = allocate.getInt();
            byte[] stringBytes = null;
            final ByteBuffer byteBuffer1 = ByteBuffer.allocateDirect(4);
            tsFile.getFromOffsetByFileChannel(byteBuffer1, offset + 12 + compressLength + intCompressLength + 4);
            byteBuffer1.flip();
            final int doubleCompressInt = byteBuffer1.getInt();
            double[] doubles = null;
            Integer everyStringLength = null;
            int i = 0;//多少行
            for (long aLong : decompress) {
                if (aLong >= timeLowerBound && aLong < timeUpperBound) {
                    Map<String, ColumnValue> columns = new HashMap<>(requestedColumns.size());
                    for (String requestedColumn : requestedColumns) {
                        final int columnIndex = SchemaUtil.getIndexByColumn(requestedColumn); //多少列
                        final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(requestedColumn);
                        if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_INTEGER)) {
                            try {
                                if (Constants.ZEROSET.contains(requestedColumn)) {
                                    columns.put(requestedColumn, new ColumnValue.IntegerColumn(0));
                                } else {
                                    if (Constants.intColumnHashMapCompress != null && Constants.intColumnHashMapCompress.exist(requestedColumn)) {
                                        try {
                                            Integer element = Constants.intColumnHashMapCompress.getElement2(requestedColumn, (index.getOffsetLine() + i));
                                            columns.put(requestedColumn, new ColumnValue.IntegerColumn(element));
                                        } catch (Exception e) {
                                            System.out.println("intColumnHashMapCompress COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                                        }
                                    } else {
                                        if (ints == null) {
                                            final ByteBuffer allocate1 = ByteBuffer.allocate(intCompressLength);
                                            tsFile.getFromOffsetByFileChannel(allocate1, offset + 12 + compressLength + 4);
                                            allocate1.flip();
                                            ints = IntCompress.decompress(allocate1.array());
                                        }
                                        final ByteBuffer intBuffer = INT_BUFFER.get();
                                        intBuffer.clear();
                                        int off = columnIndex * valueSize + i;
                                        columns.put(requestedColumn, new ColumnValue.IntegerColumn(ints[off]));
                                    }
                                }
                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                            }
                        } else if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT)) {
                            try {
                                if (Constants.doubleColumnHashMapCompress != null && Constants.doubleColumnHashMapCompress.exist(requestedColumn)) {
                                    try {
                                        double element = Constants.doubleColumnHashMapCompress.getElement2(requestedColumn, (index.getDoubleLine() + i));
                                        columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(element));
                                    } catch (Exception e) {
                                        System.out.println("doubleColumnHashMapCompress COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                                    }
                                } else {
                                    if (doubles == null) {
                                        final ByteBuffer byteBuffer = ByteBuffer.allocate(doubleCompressInt);
                                        tsFile.getFromOffsetByFileChannel(byteBuffer, offset + 12 + compressLength
                                                + intCompressLength + 4
                                                + 4);
                                        final byte[] array = byteBuffer.array();
                                        final GzipCompress gzipCompress = GZIP_COMPRESS_THREAD_LOCAL.get();
                                        final byte[] bytes = Zstd.decompress(array, valueSize * Constants.FLOAT_NUMS * 8);
                                        final ByteBuffer wrap = ByteBuffer.wrap(bytes);
                                        doubles = new double[bytes.length / 8];
                                        for (int i1 = 0; i1 < doubles.length; i1++) {
                                            doubles[i1] = wrap.getDouble();
                                        }
                                    }
                                    int position = ((columnIndex - Constants.INT_NUMS) * valueSize + i);
                                    columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubles[position]));
                                }
                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                            }
                        } else {
                            if (stringLengthBuffer == null) {
                                stringLengthBuffer = ByteBuffer.allocateDirect(4);
                                tsFile.getFromOffsetByFileChannel(stringLengthBuffer, offset
                                        + 12 + compressLength
                                        + intCompressLength + 4
                                        + doubleCompressInt + 4);
                                stringLengthBuffer.flip();
                                everyStringLength = stringLengthBuffer.getInt();
                                final ByteBuffer byteBuffer = ByteBuffer.allocate(everyStringLength);
                                tsFile.getFromOffsetByFileChannel(byteBuffer, offset
                                        + 12 + compressLength
                                        + intCompressLength + 4
                                        + doubleCompressInt + 4
                                        + 4);
                                final byte[] array = byteBuffer.array();
                                final int[] decompress1 = IntCompress.decompress(array);
                                stringLengthBuffer = ByteBuffer.allocateDirect(decompress1.length * 4);
                                for (int i1 : decompress1) {
                                    stringLengthBuffer.putInt(i1);
                                }
                            }
                            if (stringBytes == null) {
                                int stringLength = length - (
                                        12 + compressLength
                                                + intCompressLength + 4
                                                + doubleCompressInt + 4
                                                + everyStringLength + 4);
                                final ByteBuffer stringBuffer = ByteBuffer.allocate(stringLength);
                                tsFile.getFromOffsetByFileChannel(stringBuffer, offset
                                        + 12 + compressLength
                                        + intCompressLength + 4
                                        + doubleCompressInt + 4
                                        + everyStringLength + 4);
                                stringBuffer.flip();
                                GzipCompress gzipCompress = GZIP_COMPRESS_THREAD_LOCAL.get();
                                stringBytes = gzipCompress.deCompress(stringBuffer.array());
                            }
                            try {
                                int stringNum = columnIndex - (Constants.INT_NUMS + Constants.FLOAT_NUMS);
                                int stringPosition = (stringNum * valueSize + i) * 4;
                                int anInt = 0;
                                try {
                                    anInt = stringLengthBuffer.getInt(stringPosition);
                                } catch (Exception e) {
                                    System.out.println("getByIndex get string length error, e" + e);
                                }
                                if (anInt != 0) {
                                    int position = 0;
                                    try {
                                        for (int i1 = 0; i1 < stringPosition; i1 += 4) {
                                            position += stringLengthBuffer.getInt(i1);
                                        }
                                    } catch (Exception e) {
                                        System.out.println("getByIndex get string offset error, e" + e);
                                    }
                                    byte[] string = new byte[anInt];
                                    ArrayUtils.copy(stringBytes, position, string, 0, anInt);
                                    columns.put(requestedColumn, new ColumnValue.StringColumn(ByteBuffer.wrap(string)));
                                } else {
                                    columns.put(requestedColumn, new ColumnValue.StringColumn(ByteBuffer.allocate(0)));
                                }
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
            System.out.println("getByIndex error, e" + e);
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
        ByteBuffer timestampMetaBuffer = ByteBuffer.allocateDirect(12);
        ByteBuffer stringLengthBuffer = null;
        tsFile.getFromOffsetByFileChannel(timestampMetaBuffer, offset);
        timestampMetaBuffer.flip();
        long longPrevious = timestampMetaBuffer.getLong();
        int compressLength = timestampMetaBuffer.getInt();
        ByteBuffer compressLong = ByteBuffer.allocate(compressLength);
        tsFile.getFromOffsetByFileChannel(compressLong, offset + 12);
        final long[] decompress = LongCompress.decompress(compressLong.array(), longPrevious, valueSize);
        //解压int
        int[] ints = null;
        final ByteBuffer allocate = ByteBuffer.allocate(4);
        tsFile.getFromOffsetByFileChannel(allocate, offset + 12 + compressLength);
        allocate.flip();
        final int intCompressLength = allocate.getInt();
        //解压double
        byte[] stringBytes = null;
        final ByteBuffer byteBuffer1 = ByteBuffer.allocateDirect(4);
        tsFile.getFromOffsetByFileChannel(byteBuffer1, offset + 12 + compressLength + intCompressLength + 4);
        byteBuffer1.flip();
        final int doubleCompressInt = byteBuffer1.getInt();
        double[] doubles = null;
        Integer everyStringLength = null;
        int i = 0;//多少行
        for (long aLong : decompress) {
            if (aLong == timestamp) {
                Map<String, ColumnValue> columns = new HashMap<>(requestedColumns.size());
                for (String requestedColumn : requestedColumns) {
                    final int columnIndex = SchemaUtil.getIndexByColumn(requestedColumn); //多少列 44
                    final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(requestedColumn);
                    if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_INTEGER)) {
                        try {
                            if (Constants.ZEROSET.contains(requestedColumn)) {
                                columns.put(requestedColumn, new ColumnValue.IntegerColumn(0));
                            } else {
                                if (Constants.intColumnHashMapCompress != null && Constants.intColumnHashMapCompress.exist(requestedColumn)) {
                                    try {
                                        Integer element = Constants.intColumnHashMapCompress.getElement2(requestedColumn, (index.getOffsetLine() + i));
                                        columns.put(requestedColumn, new ColumnValue.IntegerColumn(element));
                                    } catch (Exception e) {
                                        System.out.println("getBigInt COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                                    }
                                } else {
                                    try {
                                        if (ints == null) {
                                            final ByteBuffer allocate1 = ByteBuffer.allocate(intCompressLength);
                                            tsFile.getFromOffsetByFileChannel(allocate1, offset + 12 + compressLength + 4);
                                            allocate1.flip();
                                            ints = IntCompress.decompress(allocate1.array());
                                        }
                                        final ByteBuffer intBuffer = INT_BUFFER.get();
                                        intBuffer.clear();
                                        int off = columnIndex * valueSize + i;
                                        columns.put(requestedColumn, new ColumnValue.IntegerColumn(ints[off]));
                                    } catch (Exception e) {
                                        System.out.println("getNormalIndex COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("getByIndex COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                        }
                    } else if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT)) {
                        try {
                            if (Constants.doubleColumnHashMapCompress != null && Constants.doubleColumnHashMapCompress.exist(requestedColumn)) {
                                double element = Constants.doubleColumnHashMapCompress.getElement2(requestedColumn, (index.getDoubleLine() + i));
                                columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(element));
                            } else {
                                if (doubles == null) {
                                    final ByteBuffer byteBuffer = ByteBuffer.allocate(doubleCompressInt);
                                    tsFile.getFromOffsetByFileChannel(byteBuffer, offset + 12 + compressLength
                                            + intCompressLength + 4
                                            + 4);
                                    final byte[] array = byteBuffer.array();
                                    final GzipCompress gzipCompress = GZIP_COMPRESS_THREAD_LOCAL.get();
                                    final byte[] bytes = Zstd.decompress(array, valueSize * Constants.FLOAT_NUMS * 8);
                                    final ByteBuffer wrap = ByteBuffer.wrap(bytes);
                                    doubles = new double[bytes.length / 8];
                                    for (int i1 = 0; i1 < doubles.length; i1++) {
                                        doubles[i1] = wrap.getDouble();
                                    }
                                }
                                int position = ((columnIndex - Constants.INT_NUMS) * valueSize + i);
                                columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubles[position]));

                            }
                        } catch (Exception e) {
                            System.out.println("getByIndex COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                        }
                    } else {
                        try {
                            if (stringLengthBuffer == null) {
                                stringLengthBuffer = ByteBuffer.allocateDirect(4);
                                tsFile.getFromOffsetByFileChannel(stringLengthBuffer, offset
                                        + 12 + compressLength
                                        + intCompressLength + 4
                                        + doubleCompressInt + 4);
                                stringLengthBuffer.flip();
                                everyStringLength = stringLengthBuffer.getInt();
                                final ByteBuffer byteBuffer = ByteBuffer.allocate(everyStringLength);
                                tsFile.getFromOffsetByFileChannel(byteBuffer, offset
                                        + 12 + compressLength
                                        + intCompressLength + 4
                                        + doubleCompressInt + 4
                                        + 4);
                                final byte[] array = byteBuffer.array();
                                final int[] decompress1 = IntCompress.decompress(array);
                                stringLengthBuffer = ByteBuffer.allocateDirect(decompress1.length * 4);
                                for (int i1 : decompress1) {
                                    stringLengthBuffer.putInt(i1);
                                }
                            }
                            if (stringBytes == null) {
                                int stringLength = length -
                                        (12 + compressLength  //long
                                                + intCompressLength + 4 // int
                                                + doubleCompressInt + 4 // double
                                                + everyStringLength + 4);
                                final ByteBuffer stringBuffer = ByteBuffer.allocate(stringLength);
                                tsFile.getFromOffsetByFileChannel(stringBuffer, offset
                                        + 12 + compressLength
                                        + intCompressLength + 4
                                        + doubleCompressInt + 4
                                        + everyStringLength + 4);
                                stringBuffer.flip();
                                GzipCompress gzipCompress = GZIP_COMPRESS_THREAD_LOCAL.get();
                                stringBytes = gzipCompress.deCompress(stringBuffer.array());
                            }
                            int stringNum = columnIndex - (Constants.INT_NUMS + Constants.FLOAT_NUMS);
                            int stringPosition = (stringNum * valueSize + i) * 4;
                            int anInt = 0;
                            try {
                                anInt = stringLengthBuffer.getInt(stringPosition);
                            } catch (Exception e) {
                                System.out.println("getByIndex get string length error, e" + e);
                            }
                            if (anInt != 0) {
                                int position = 0;
                                try {
                                    for (int i1 = 0; i1 < stringPosition; i1 += 4) {
                                        position += stringLengthBuffer.getInt(i1);
                                    }
                                } catch (Exception e) {
                                    System.out.println("getByIndex get string offset error, e" + e);
                                }
                                byte[] string = new byte[anInt];
                                ArrayUtils.copy(stringBytes, position, string, 0, anInt);
                                columns.put(requestedColumn, new ColumnValue.StringColumn(ByteBuffer.wrap(string)));
                            } else {
                                columns.put(requestedColumn, new ColumnValue.StringColumn(ByteBuffer.allocate(0)));
                            }
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
            writeTimes.getAndIncrement();
            int m = j % Constants.TS_FILE_NUMS;
            String[] indexArray = SchemaUtil.getIndexArray();
            ByteBuffer intBuffer;
            ByteBuffer doubleBuffer;
            ByteBuffer longBuffer;
            ByteBuffer stringLengthBuffer;
            List<ByteBuffer> stringList;
            double[] doubles = null;
            long[] longs = new long[lineNum];
            int[] ints = new int[lineNum * (Constants.INT_NUMS - Constants.intColumnHashMapCompress.getColumnSize())];
            int[][] bigInts = Constants.intColumnHashMapCompress.getTempArray(lineNum);
            int[][] doubleInts = Constants.doubleColumnHashMapCompress.getTempArray(lineNum);
//            int[][] stringInts = Constants.stringColumnHashMapCompress.GetTempArray(lineNum);
            int[] stringLengthArray = new int[lineNum * Constants.STRING_NUMS];
            int stringLengthPosition = 0;
            int longPosition = 0;
            int doublePosition = 0;
            final AtomicInteger[] bigIntPosition = INT_POSITION_THREAD_LOCAL.get();
            for (AtomicInteger atomicInteger : bigIntPosition) {
                atomicInteger.set(0);
            }
            final AtomicInteger[] bigDoublePosition = DOUBLE_POSITION_THREAD_LOCAL.get();
            for (AtomicInteger atomicInteger : bigDoublePosition) {
                atomicInteger.set(0);
            }
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
//                        longBuffer.putLong(value.getTimestamp());
                    }
                    Map<String, ColumnValue> columns = value.getColumns();
                    if (i < Constants.INT_NUMS) {
                        int integerValue = columns.get(key).getIntegerValue();
                        aggBucket.updateInt(integerValue, i);
//                        SchemaUtil.maps.get(key).add(integerValue);
                        if (Constants.ZEROSET.contains(key)) {
                            continue;
                        }
                        if (Constants.intColumnHashMapCompress != null && Constants.intColumnHashMapCompress.exist(key)) {
                            int i1 = Constants.intColumnHashMapCompress.getColumnIndex(key);
                            integerValue = Constants.intColumnHashMapCompress.addElement(key, integerValue);
                            bigInts[i1][bigIntPosition[i1].getAndAdd(1)] = integerValue;
                        } else {
                            ints[intPosition++] = integerValue;
                        }
                    } else if (i < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                        if (doubles == null) {
                            doubles = new double[lineNum * Constants.FLOAT_NUMS];
                        }
                        final double doubleFloatValue = columns.get(key).getDoubleFloatValue();
                        aggBucket.updateDouble(doubleFloatValue, i);
                        if (Constants.doubleColumnHashMapCompress.exist(key)) {
                            int i1 = Constants.doubleColumnHashMapCompress.getColumnIndex(key);
                            int integerValue = Constants.doubleColumnHashMapCompress.addElement(key, doubleFloatValue);
                            doubleInts[i1][bigDoublePosition[i1].getAndAdd(1)] = integerValue;
                        } else {
                            doubles[doublePosition] = doubleFloatValue;
                            doublePosition++;
                        }
//                        doubleBuffer.putDouble(doubleFloatValue);
                    } else {
                        final ByteBuffer stringValue = columns.get(key).getStringValue();
//                        if(Constants.stringColumnHashMapCompress.Exist(key)){
//                            int i1 = Constants.stringColumnHashMapCompress.GetColumnIndex(key);
//                            int i2 = Constants.stringColumnHashMapCompress.addElement(key, stringValue);
//                            stringInts[i1][stringIntPosition[i1].getAndAdd(1)]=i2;
//                        } else {
                        totalStringLength += stringValue.remaining();
                        stringList.add(stringValue);
                        stringLengthArray[stringLengthPosition++] = stringValue.remaining();
//                        }
                    }
                }
            }
            //压缩string
            byte[] bytes = new byte[totalStringLength];
            int position = 0;
            for (ByteBuffer buffer : stringList) {
                final byte[] array = buffer.array();
                ArrayUtils.copy(array, 0, bytes, position, array.length);
                position += array.length;
            }
            final GzipCompress gzipCompress = GZIP_COMPRESS_THREAD_LOCAL.get();
            final byte[] compress = gzipCompress.compress(bytes);

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
            byte[] compress2 = null;
            byte[] stringLengthArrayCompress = null;
            try {
                compress2 = IntCompress.compress(ints);
                stringLengthArrayCompress = IntCompress.compress(stringLengthArray);
            } catch (Exception e) {
                System.out.println("compress int error" + e);
            }
            // 存储bigInt
            int offsetLine = Constants.intColumnHashMapCompress.compressAndAdd2(bigInts);
            // 存储DoubleHashMapCompress
            final int doubleOfferLine = Constants.doubleColumnHashMapCompress.compressAndAdd2(doubleInts);
//            // 存储StringHashMapCompress
//            Constants.stringColumnHashMapCompress.CompressAndadd(stringInts);
            int total = 8 + 4 + compress1.length //timestamp
                    + compress2.length + 4 //int
//                    + (8 + 4 + 4 + doubleCompress1.length) //double
                    + (4 + compressDouble.length) //double
                    + stringLengthArrayCompress.length + 4 //string长度记录
                    + compress.length; //string存储string
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(total);
            try {
                StaticsUtil.STRING_TOTAL_LENGTH.getAndAdd(totalStringLength);
                StaticsUtil.STRING_COMPRESS_LENGTH.getAndAdd(compress.length + stringLengthArrayCompress.length + 4);
                StaticsUtil.DOUBLE_COMPRESS_LENGTH.getAndAdd(4 + compressDouble.length);
                StaticsUtil.LONG_COMPRESS_LENGTH.getAndAdd(8 + 4 + compress1.length);
                StaticsUtil.INT_COMPRESS_LENGTH.getAndAdd(compress2.length + 4);
                byteBuffer.putLong(previousLong);
                byteBuffer.putInt(compress1.length);
                byteBuffer.put(compress1);
                byteBuffer.putInt(compress2.length);
                byteBuffer.put(compress2);
                // double
                byteBuffer.putInt(compressDouble.length);
                byteBuffer.put(compressDouble);
                //string
                stringLengthBuffer.flip();
                byteBuffer.putInt(stringLengthArrayCompress.length);
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
                        , offsetLine
                        , doubleOfferLine
                        , aggBucket);
                MapIndex.put(vin, index);
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
