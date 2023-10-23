package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.compress.*;
import com.alibaba.lindorm.contest.index.AggBucket;
import com.alibaba.lindorm.contest.index.BucketArrayFactory;
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

import static com.alibaba.lindorm.contest.util.Constants.isBigString;


public class TSFileService {

    public static final ThreadLocal<ByteBuffer> TOTAL_INT_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocate(Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS * 4));
    public static final ThreadLocal<ByteBuffer> TOTAL_DOUBLE_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS * 8));
    public static final ThreadLocal<ByteBuffer> TOTAL_LONG_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * 8));
    public static final ThreadLocal<ByteBuffer> TOTAL_STRING_LENGTH_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocate(Constants.CACHE_VINS_LINE_NUMS * Constants.STRING_NUMS * 4));
    public static final ThreadLocal<ByteBuffer> TOTAL_DIRECT_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(1024 * 6 * 10));
    public static final ThreadLocal<ArrayList<ByteBuffer>> STRING_BUFFER_LIST = ThreadLocal.withInitial(() -> new ArrayList<>(Constants.CACHE_VINS_LINE_NUMS * Constants.STRING_NUMS));
    public static final ThreadLocal<ArrayList<Row>> LIST_THREAD_LOCAL = ThreadLocal.withInitial(ArrayList::new);

    public static final ThreadLocal<ArrayList<ColumnValue>> LIST_THREAD_VALUE_LOCAL = ThreadLocal.withInitial(ArrayList::new);

    public static final ThreadLocal<GzipCompress> GZIP_COMPRESS_THREAD_LOCAL = ThreadLocal.withInitial(GzipCompress::new);
    public static final ThreadLocal<int[]> INT_ARRAY_BUFFER = ThreadLocal.withInitial(() -> new int[Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS]);
    public static final ThreadLocal<double[]> DOUBLE_ARRAY_BUFFER = ThreadLocal.withInitial(() -> new double[Constants.CACHE_VINS_LINE_NUMS * Constants.FLOAT_NUMS]);
    public static final ThreadLocal<long[]> LONG_ARRAY_BUFFER = ThreadLocal.withInitial(() -> new long[Constants.CACHE_VINS_LINE_NUMS]);

    private final TSFile[] tsFiles;
    private final AtomicLong writeTimes = new AtomicLong(0);

    private final BucketArrayFactory bucketArrayFactory = new BucketArrayFactory((180000000 / Constants.CACHE_VINS_LINE_NUMS) + 5000);

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

    public ArrayList<ColumnValue> getSingleValueByIndex(Vin vin, long timeLowerBound, long timeUpperBound, Index index, Set<String> requestedColumns, int j) {
        if (StaticsUtil.GET_SINGPLE_VALUE_TIMES.addAndGet(1) % 1000000 == 0) {
            System.out.println("getSingleValueByIndex cost all time : " + StaticsUtil.SINGLEVALUE_TOTAL_TIME);
            System.out.println("getSingleValueByIndex cost read time : " + StaticsUtil.READ_DATA_TIME);
            System.out.println("getSingleValueByIndex cost decompress time : " + StaticsUtil.COMPRESS_DATA_TIME);
            System.out.println("getSingleValueByIndex cost value time : " + StaticsUtil.GET_VALUE_TIMES);

        }
        long start = System.currentTimeMillis();
        ArrayList<ColumnValue> rowArrayList = LIST_THREAD_VALUE_LOCAL.get();
        rowArrayList.clear();
        try {
            long offset = index.getOffset();
            final int valueSize = index.getValueSize();
            int length = index.getDoubleLength();
            for (String requestedColumn : requestedColumns) {
                final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(requestedColumn);
                if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_INTEGER)) {
                    offset = index.getOffset();
                    length = index.getIntLength();
                }else{
                    offset = index.getOffset()+index.getIntLength();
                    length = index.getDoubleLength() - index.getIntLength();
                }
            }
            int m = j % Constants.TS_FILE_NUMS;
            final TSFile tsFile = getTsFileByIndex(m);
            ByteBuffer dataBuffer = ByteBuffer.allocate(length);
            long startRead = System.currentTimeMillis();
            tsFile.getFromOffsetByFileChannel(dataBuffer, offset);
            dataBuffer.flip();
            long longPrevious = index.getPreviousTimeStamp();
            byte[] longBytes = index.getTimeStampBytes();
            long[] decompress = LongCompress.decompress(longBytes, longPrevious, valueSize);
            int i = 0;//多少行
            double[] doubles = null;
            Map<Integer, int[]> intMap = null;
            List<Integer> doubleColumnIndex = new ArrayList<>();
            List<Integer> intColumnIndex = new ArrayList<>();
            long endRead = System.currentTimeMillis();
            StaticsUtil.READ_DATA_TIME.addAndGet(endRead - startRead);
            for (String requestedColumn : requestedColumns) {
                final int columnIndex = SchemaUtil.getIndexByColumn(requestedColumn);
                final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(requestedColumn);
                if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT)) {
                    doubleColumnIndex.add(columnIndex);
                } else if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_INTEGER)) {
                    intColumnIndex.add(columnIndex);
                }
            }
            for (long aLong : decompress) {
                if (aLong >= timeLowerBound && aLong < timeUpperBound) {
                    long startGetValue = System.currentTimeMillis();
                    ColumnValue value = null;
                    for (String requestedColumn : requestedColumns) {
                        final int columnIndex = SchemaUtil.getIndexByColumn(requestedColumn); //多少列
                        final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(requestedColumn);
                        if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_INTEGER)) {
                            long compressStart = System.currentTimeMillis();
                            try {
                                if (intMap == null) {
                                    int intCompressLength = dataBuffer.getShort();
                                    dataBuffer.position( 2);
                                    intMap = IntCompress.getByLineNum(dataBuffer, index.getValueSize(), intColumnIndex, intCompressLength);
                                }
                                final int[] ints1 = intMap.get(columnIndex);
                                value = new ColumnValue.IntegerColumn(ints1[i]);
                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("getByIndex time range COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                            }
                            long compressEnd = System.currentTimeMillis();
                            StaticsUtil.COMPRESS_DATA_TIME.addAndGet(compressEnd - compressStart);
                        } else if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT)) {
                            try {
                                long compressStart = System.currentTimeMillis();
                                if (doubles == null) {
                                    int doubleCompressInt = dataBuffer.getShort();
                                    final byte[] allocate1 = new byte[doubleCompressInt];
                                    dataBuffer.position(
                                            + 2);
                                    dataBuffer.get(allocate1);
                                    doubles = DoubleCompress.decode2(ByteBuffer.wrap(allocate1), Constants.FLOAT_NUMS * valueSize, valueSize);
                                }
                                int position = ((columnIndex - Constants.INT_NUMS) * valueSize + i);
                                value = new ColumnValue.DoubleFloatColumn(doubles[position]);
                                long compressEnd = System.currentTimeMillis();
                                StaticsUtil.COMPRESS_DATA_TIME.addAndGet(compressEnd - compressStart);
                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                            }
                        } else {
                            long compressStart = System.currentTimeMillis();
                            long compressEnd = System.currentTimeMillis();
                            StaticsUtil.COMPRESS_DATA_TIME.addAndGet(compressEnd - compressStart);
                        }
                    }
                    long endGetValue = System.currentTimeMillis();
                    StaticsUtil.GET_VALUE_TIMES.addAndGet(endGetValue - startGetValue);
                    if (value != null) {
                        rowArrayList.add(value);
                    }
                }
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("getByIndexV2 e" + e);
            System.exit(-1);
        }
        long end = System.currentTimeMillis();
        StaticsUtil.SINGLEVALUE_TOTAL_TIME.getAndAdd(end - start);
        return rowArrayList;
    }

    public static void main(String[] args) {
        String s = "G0-\\O8\\eeG444'Gu8WSOq\\'Gym'.80.Smu&GOe\\uuWSi''44e..-S-0y\\mme8ymu-8KGyy[i4m#\\yWe'C4auei4\\ym#\\ma4\\\\CqO";
        System.out.println(s.length());
        final byte[] compress = Zstd.compress(s.getBytes(), 3);
        final byte[] decompress = Zstd.decompress(compress, 100);
        final ByteBuffer allocate = ByteBuffer.allocate(4 * 4);
        allocate.putInt(1);
        allocate.putInt(2);
        allocate.putInt(3);
        allocate.putInt(4);
        allocate.flip();

        final int anInt = allocate.getInt(2 * 4);
        final int position = allocate.position();

    }

    public ArrayList<Row> getByIndexV2(Vin vin, long timeLowerBound, long timeUpperBound, Index index, Set<String> requestedColumns, int j) {
        ArrayList<Row> rowArrayList = LIST_THREAD_LOCAL.get();
        rowArrayList.clear();
        boolean containsBigString = requestedColumns.contains(Constants.bigStringColumn);
        ByteBuffer dataBuffer = null;
        byte[] bigStringBytes = null;
        try {
            final long offset = index.getOffset();
            final int valueSize = index.getValueSize();
            final int length = index.getLength();
            int m = j % Constants.TS_FILE_NUMS;
            final TSFile tsFile = getTsFileByIndex(m);
            if (containsBigString) {
                dataBuffer = ByteBuffer.allocate(length);
                tsFile.getFromOffsetByFileChannel(dataBuffer, offset);
                dataBuffer.flip();
                if (bigStringBytes == null) {
                    int bigStringLength = dataBuffer.getInt(index.getBigStringOffset());
                    bigStringBytes = new byte[bigStringLength];
                    dataBuffer.position(index.getBigStringOffset() + 4);
                    dataBuffer.get(bigStringBytes, 0, bigStringLength);
                    bigStringBytes = Zstd.decompress(bigStringBytes, valueSize * 100);
                }
            } else {
                System.out.println("containsBigString false");
                dataBuffer = ByteBuffer.allocate(index.getBigStringOffset());
                tsFile.getFromOffsetByFileChannel(dataBuffer, offset);
                dataBuffer.flip();
            }
            dataBuffer.position(0);
            long longPrevious = index.getPreviousTimeStamp();
            byte[] longBytes = index.getTimeStampBytes();
            long[] decompress = LongCompress.decompress(longBytes, longPrevious, valueSize);
            int intCompressLength = dataBuffer.getShort();
            int doubleCompressInt = dataBuffer.getShort(intCompressLength + 2);
            int i = 0;//多少行
            int[] ints = null;
            double[] doubles = null;
            List<ByteBuffer> stringBytes = null;
            Short everyStringLength = null;
            ByteBuffer stringLengthBuffer = null;
            Set<Integer> queryStringNum = new HashSet<>();
            boolean judgeTimeRangeError = true;
            for (long aLong : decompress) {
                if (aLong >= timeLowerBound && aLong < timeUpperBound) {
                    judgeTimeRangeError = false;
                    Map<String, ColumnValue> columns = new HashMap<>(requestedColumns.size());
                    for (String requestedColumn : requestedColumns) {
                        final int columnIndex = SchemaUtil.getIndexByColumn(requestedColumn); //多少列
                        final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(requestedColumn);
                        if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_INTEGER)) {
                            try {
                                if (ints == null) {
                                    final byte[] allocate1 = new byte[intCompressLength];
                                    dataBuffer.position( 2);
                                    dataBuffer.get(allocate1);
                                    ints = IntCompress.decompress4(allocate1, index.getValueSize());
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
                                    dataBuffer.position(
                                            + intCompressLength + 2
                                            + 2);
                                    dataBuffer.get(allocate1);
                                    doubles = DoubleCompress.decode2(ByteBuffer.wrap(allocate1), Constants.FLOAT_NUMS * valueSize, valueSize);
                                }
                                int position = ((columnIndex - Constants.INT_NUMS) * valueSize + i);
                                columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubles[position]));
                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                            }
                        } else {
                            queryStringNum.add(columnIndex);
                            if (isBigString(columnIndex)) {
                                final ByteBuffer wrap = ByteBuffer.wrap(bigStringBytes, i * 100, 100);
                                columns.put(requestedColumn, new ColumnValue.StringColumn(wrap));
                            } else {
                                if (stringLengthBuffer == null) {
                                    everyStringLength = dataBuffer.getShort(
                                            +  intCompressLength + 2
                                                    + doubleCompressInt + 2);
                                    final byte[] bytes = new byte[everyStringLength - 4];
                                    dataBuffer.position(
                                            +intCompressLength + 2
                                                    + doubleCompressInt + 2
                                                    + 2);
                                    int totalLength = dataBuffer.getInt();
                                    dataBuffer.get(bytes, 0, bytes.length);
                                    final short[] decompress1 = IntCompress.decompressShort(bytes, index.getValueSize(), totalLength);
                                    stringLengthBuffer = ByteBuffer.allocateDirect(decompress1.length * 2);
                                    for (short i1 : decompress1) {
                                        stringLengthBuffer.putShort(i1);
                                    }
                                }
                                if (stringBytes == null) {
                                    int stringLength = index.getBigStringOffset() - (
                                                      intCompressLength + 2
                                                    + doubleCompressInt + 2
                                                    + everyStringLength + 2
                                    );
                                    byte[] bytes = new byte[stringLength - 4];
                                    dataBuffer.position(
                                            +intCompressLength + 2
                                                    + doubleCompressInt + 2
                                                    + everyStringLength + 2
                                    );
                                    int totalLength = dataBuffer.getInt();
                                    dataBuffer.get(bytes, 0, bytes.length);
                                    stringBytes = StringCompress.decompress1(bytes, stringLengthBuffer, index.getValueSize(), totalLength);
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
                    }
                    rowArrayList.add(new Row(vin, aLong, columns));
                }
                i++;
            }
            if (judgeTimeRangeError) {
                StaticsUtil.JUDGE_TIME_RANGE_ERROR_TIMES.getAndIncrement();
            }
            if (queryStringNum.size() >= 3) {
                String s = "";
                for (Integer integer : queryStringNum) {
                    s += integer + ",";
                }
                System.out.println("get time range string size > 3 , queryColumns " + s);
            }
        } catch (Exception e) {
            e.printStackTrace();
//            System.out.println("getByIndexV2 e"  + " containsBigString: " + containsBigString + "index.getBigStringOffset()" + index.getBigStringOffset() + "data limit" + dataBuffer.limit() + "data position" + dataBuffer.position() + "data capacity" + dataBuffer.capacity() );
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
            boolean containsBigString = requestedColumns.contains(Constants.bigStringColumn);
            int m = j % Constants.TS_FILE_NUMS;
            final TSFile tsFile = getTsFileByIndex(m);
            ByteBuffer dataBuffer;
            byte[] bigStringBytes = null;
            if (containsBigString) {
                dataBuffer = ByteBuffer.allocate(length);
                tsFile.getFromOffsetByFileChannel(dataBuffer, offset);
                dataBuffer.flip();
                int bigStringLength = dataBuffer.getInt(index.getBigStringOffset());
                if (bigStringBytes == null) {
                    bigStringBytes = new byte[bigStringLength];
                    dataBuffer.position(index.getBigStringOffset() + 4);
                    dataBuffer.get(bigStringBytes, 0, bigStringLength);
                }
            } else {
                dataBuffer = ByteBuffer.allocate(index.getBigStringOffset());
                tsFile.getFromOffsetByFileChannel(dataBuffer, offset);
                dataBuffer.flip();
            }
            dataBuffer.position(0);
            long longPrevious = index.getPreviousTimeStamp();
            byte[] longBytes = index.getTimeStampBytes();
            long[] decompress = LongCompress.decompress(longBytes, longPrevious, valueSize);
            int intCompressLength = dataBuffer.getShort();
            int doubleCompressInt = dataBuffer.getShort( intCompressLength + 2);
            int i = 0;//多少行
            int[] ints = null;
            double[] doubles = null;
            List<ByteBuffer> stringBytes = null;
            Short everyStringLength = null;
            ByteBuffer stringLengthBuffer = null;
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
                                    dataBuffer.position(  2);
                                    dataBuffer.get(allocate1);
                                    ints = IntCompress.decompress4(allocate1, index.getValueSize());
                                }
                                int off = columnIndex * valueSize + i;
                                columns.put(requestedColumn, new ColumnValue.IntegerColumn(ints[off]));

                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                            }
                        } else if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT)) {
                            try {
                                if (doubles == null) {
                                    final byte[] allocate1 = new byte[doubleCompressInt];
                                    dataBuffer.position(
                                            + intCompressLength + 2
                                            + 2);
                                    dataBuffer.get(allocate1);
                                    doubles = DoubleCompress.decode2(ByteBuffer.wrap(allocate1), Constants.FLOAT_NUMS * valueSize, valueSize);
                                }
                                int position = ((columnIndex - Constants.INT_NUMS) * valueSize + i);
                                columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubles[position]));
                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                            }
                        } else {
                            if (isBigString(columnIndex)) {
                                final ByteBuffer wrap = ByteBuffer.wrap(bigStringBytes, i * 100, 100);
                                columns.put(requestedColumn, new ColumnValue.StringColumn(wrap));
                            } else {
                                if (stringLengthBuffer == null) {
                                    everyStringLength = dataBuffer.getShort(
                                            +intCompressLength + 2
                                                    + doubleCompressInt + 2);
                                    final byte[] bytes = new byte[everyStringLength - 4];
                                    dataBuffer.position(
                                            +intCompressLength + 2
                                                    + doubleCompressInt + 2
                                                    + 2);
                                    int totalLength = dataBuffer.getInt();
                                    dataBuffer.get(bytes, 0, bytes.length);
                                    final short[] decompress1 = IntCompress.decompressShort(bytes, index.getValueSize(), totalLength);
                                    stringLengthBuffer = ByteBuffer.allocateDirect(decompress1.length * 2);
                                    for (short i1 : decompress1) {
                                        stringLengthBuffer.putShort(i1);
                                    }
                                }
                                if (stringBytes == null) {
                                    int stringLength = index.getBigStringOffset() - (

                                            + intCompressLength + 2
                                                    + doubleCompressInt + 2
                                                    + everyStringLength + 2

                                    );
                                    byte[] bytes = new byte[stringLength - 4];
                                    dataBuffer.position(
                                            +intCompressLength + 2
                                                    + doubleCompressInt + 2
                                                    + everyStringLength + 2
                                    );
                                    int totalLength = dataBuffer.getInt();
                                    dataBuffer.get(bytes, 0, bytes.length);
                                    stringBytes = StringCompress.decompress1(bytes, stringLengthBuffer, index.getValueSize(), totalLength);
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
            long start = System.nanoTime();
            AggBucket aggBucket = bucketArrayFactory.getAggBucket();
            writeTimes.getAndIncrement();
            int m = j % Constants.TS_FILE_NUMS;
            String[] indexArray = SchemaUtil.getIndexArray();
            ByteBuffer intBuffer;
            ByteBuffer doubleBuffer;
            ByteBuffer longBuffer;
            ByteBuffer stringLengthBuffer;
            List<ByteBuffer> stringList = new ArrayList<>(9 * lineNum);
            List<ByteBuffer> stringList1 = new ArrayList<>(lineNum);
            int bigStringLength = 0;
            double[] doubles;
            long[] longs;
            int[] ints;
            int longPosition = 0;
            int doublePosition = 0;
            int intPosition = 0;
            if (lineNum == Constants.CACHE_VINS_LINE_NUMS) {
                intBuffer = TOTAL_INT_BUFFER.get();
                doubleBuffer = TOTAL_DOUBLE_BUFFER.get();
                longBuffer = TOTAL_LONG_BUFFER.get();
                stringLengthBuffer = TOTAL_STRING_LENGTH_BUFFER.get();
//                stringList = STRING_BUFFER_LIST.get();

                ints = INT_ARRAY_BUFFER.get();
//                Arrays.fill(ints, 0);

                doubles = DOUBLE_ARRAY_BUFFER.get();
//                Arrays.fill(doubles, 0);

                longs = LONG_ARRAY_BUFFER.get();
//                Arrays.fill(longs, 0);

                intBuffer.clear();
                doubleBuffer.clear();
                longBuffer.clear();
                stringLengthBuffer.clear();
//                stringList.clear();
            } else {
                ints = new int[lineNum * Constants.INT_NUMS];
                doubles = new double[lineNum * Constants.FLOAT_NUMS];
                longs = new long[lineNum];
                //存储每个字符串的长度
//                stringList = new ArrayList<>(lineNum * Constants.STRING_NUMS);
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
                        ints[intPosition++] = integerValue;
                    } else if (i < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                        final double doubleFloatValue = columns.get(key).getDoubleFloatValue();
                        aggBucket.updateDouble(doubleFloatValue, i);
                        doubles[doublePosition] = doubleFloatValue;
                        doublePosition++;
                    } else {
                        final ByteBuffer stringValue = columns.get(key).getStringValue();
                        if (isBigString(i)) {
                            stringList1.add(stringValue);
                            bigStringLength += stringValue.remaining();
                            if (stringValue.remaining() != 100) {
                                System.out.println("write length != 100");
                            }
                        } else  {
                            stringList.add(stringValue);
                        }
                        totalStringLength += stringValue.remaining();
                    }
                }
            }
            long prepareDataTime = System.nanoTime();
            //压缩string
            CompressResult compressResult = StringCompress.compress1(stringList, lineNum);
            final ByteBuffer allocate = ByteBuffer.allocate(bigStringLength);
            for (ByteBuffer byteBuffer : stringList1) {
                allocate.put(byteBuffer);
            }
            final byte[] stringCompress = compressResult.compressedData;
            final byte[] stringCompress1 = Zstd.compress(allocate.array(), 3);
            short[] stringLengthArray = compressResult.stringLengthArray;


            //压缩double
            final byte[] compressDouble = DoubleCompress.encode2(doubles, valueList.size());
            // 压缩long
            final byte[] compress1 = LongCompress.compress(longs);
            long previousLong = longs[longs.length - 1];

            //压缩int
            byte[] compress2 = IntCompress.compress4(ints, lineNum);
            byte[] stringLengthArrayCompress = IntCompress.compressShort(stringLengthArray, lineNum);
            int total =  //timestamp
                     compress2.length + 2 //int
                    + (2 + compressDouble.length) //double
                    + stringLengthArrayCompress.length + 2 //string长度记录
                    + stringCompress.length //string存储string
                    + stringCompress1.length + 4; //string存储string
            long compressTime = System.nanoTime();
            ByteBuffer byteBuffer = TOTAL_DIRECT_BUFFER.get();
            byteBuffer.clear();
            byteBuffer.limit(total);
            StaticsUtil.STRING_BYTE_LENGTH.getAndAdd(stringLengthArrayCompress.length);
            StaticsUtil.STRING_SHORT_LENGTH.getAndAdd(stringLengthArray.length * 2L);
            StaticsUtil.STRING_TOTAL_LENGTH.getAndAdd(totalStringLength);
            StaticsUtil.STRING_COMPRESS_LENGTH.getAndAdd(stringCompress.length + stringLengthArrayCompress.length + 2 + stringCompress1.length + 4);
            StaticsUtil.DOUBLE_COMPRESS_LENGTH.getAndAdd(2 + compressDouble.length);
            StaticsUtil.LONG_COMPRESS_LENGTH.getAndAdd(8 + 2 + compress1.length);
            StaticsUtil.INT_COMPRESS_LENGTH.getAndAdd(compress2.length + 2);
            //long
//               byteBuffer.putLong(previousLong);
//               byteBuffer.putShort((short) compress1.length);
//               byteBuffer.put(compress1);
            //int
            byteBuffer.putShort((short) compress2.length);
            byteBuffer.put(compress2);
            // double
            byteBuffer.putShort((short) compressDouble.length);
            byteBuffer.put(compressDouble);
            //string
            byteBuffer.putShort((short) stringLengthArrayCompress.length);
            byteBuffer.put(stringLengthArrayCompress);
            byteBuffer.put(stringCompress);

            int bigOffset = byteBuffer.position();
            byteBuffer.putInt(stringCompress1.length);
            byteBuffer.put(stringCompress1);

            long putTime = System.nanoTime();
            try {
                TSFile tsFile = getTsFileByIndex(m);
                final long append = tsFile.append(byteBuffer);
                final Index index = new Index(append
                        , maxTimestamp
                        , minTimestamp
                        , total
                        , lineNum
                        , aggBucket
                        , 2 + compress2.length
                        , 2 + compress2.length + 2 + compressDouble.length
                        , previousLong
                        , compress1
                        , bigOffset);
                MapIndex.put(j, index);
                valueList.clear();
            } catch (Exception e) {
                System.out.println("write append error" + e);
            }
            long append = System.nanoTime();
            if (writeTimes.get() % 100000 == 0) {
                System.out.println("write total cost: " + (System.nanoTime() - start) + " ns"
                        + " write size: " + total
                        + " prepareData time " + (prepareDataTime - start) + " ns"
                        + " compress time " + (compressTime - prepareDataTime) + " ns"
                        + " put time " + (putTime - compressTime) + " ns"
                        + " append time " + (append - putTime) + " ns");
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

