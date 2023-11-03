package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.TSDBEngineImpl;
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.alibaba.lindorm.contest.index.MapIndex.INDEX_ARRAY;
import static com.alibaba.lindorm.contest.util.Constants.isBigString;


public class TSFileService {

    public static final ThreadLocal<ByteBuffer> TOTAL_INT_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocate(Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS * 4));
    public static final ThreadLocal<ByteBuffer> TOTAL_DOUBLE_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS * 8));
    public static final ThreadLocal<ByteBuffer> TOTAL_LONG_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * 8));
    public static final ThreadLocal<ByteBuffer> TOTAL_STRING_LENGTH_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocate(Constants.CACHE_VINS_LINE_NUMS * Constants.STRING_NUMS * 4));
    public static final ThreadLocal<ByteBuffer> TOTAL_DIRECT_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(1024 * 6 * 10));
    public static final ThreadLocal<ByteBuffer> TOTAL_DIRECT_BUFFER1 = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(1024 * 6 * 10));
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

    private AtomicLong atomicLong = new AtomicLong(0);

    public ArrayList<ColumnValue> getSingleValueByIndex(Vin vin, long timeLowerBound, long timeUpperBound, Index index, int columnIndex, int j, Map<Long, ByteBuffer> map, Context ctx, Map<Long, TSDBEngineImpl.CacheData> cacheDataMap, String queryType, Map<Long, Long> queryTimeMap) {
        if ("downSample".equals(queryType)) {
            if (StaticsUtil.GET_SINGPLE_VALUE_TIMES.addAndGet(1) % 1000000 == 0) {
                System.out.println("getSingleValueByIndex times : " + StaticsUtil.GET_SINGPLE_VALUE_TIMES.get() + " ns");
                System.out.println("getSingleValueByIndex hitCacheTimes : " + atomicLong.get() + " ns");
                System.out.println("getSingleValueByIndex cost all time : " + StaticsUtil.SINGLEVALUE_TOTAL_TIME + " ns");
                System.out.println("getSingleValueByIndex cost read time : " + StaticsUtil.READ_DATA_TIME);
                System.out.println("getSingleValueByIndex cost decompress time : " + StaticsUtil.COMPRESS_DATA_TIME + " ns");
                System.out.println("getSingleValueByIndex cost value time : " + StaticsUtil.GET_VALUE_TIMES + " ns");
                System.out.println("getSingleValueByIndex FIRST_READ_TIME : " + StaticsUtil.FIRST_READ_TIME.get() + " ns");
                System.out.println("getSingleValueByIndex SECOND_READ_TIME : " + StaticsUtil.SECOND_READ_TIME.get() + " ns");
            }
        }
        long start = System.nanoTime();
        ctx.addAccessTime();
        ArrayList<ColumnValue> rowArrayList = new ArrayList<>();
        try {
            long longPrevious = index.getPreviousTimeStamp();
            byte[] longBytes = index.getTimeStampBytes();
            final int valueSize = index.getValueSize();
            long[] decompress = LongCompress.decompress(longBytes, longPrevious, valueSize);
            ColumnValue value = null;
//
            long offset;
            int length;
            List<Integer> intColumnIndex = new ArrayList<>();
            if (columnIndex < Constants.INT_NUMS) {
                offset = index.getOffset();
                length = index.getIntLength();
                intColumnIndex.add(columnIndex);
            } else {
                offset = index.getOffset() + index.getIntLength();
                length = index.getDoubleLength() - index.getIntLength();

                byte[] doubleHeader = index.getDoubleHeader();
                ByteBuffer header = ByteBuffer.wrap(doubleHeader);
                int doubleDeltaLength = header.getInt();
                int corrilaLength = header.getInt();
                if (DoubleCompress.doubleDelta.contains(columnIndex)) {
                    offset += 2;
                    length = doubleDeltaLength;
                } else {
                    offset += 2 + doubleDeltaLength;
                    length = corrilaLength;
                }
            }
            int m = j % Constants.TS_FILE_NUMS;
            final TSFile tsFile = getTsFileByIndex(m);
            ByteBuffer dataBuffer;
            long startRead = System.nanoTime();
//            if (map != null && map.containsKey(offset)) {
//                long s = System.nanoTime();
//                dataBuffer = ByteBuffer.allocate(length);
//                tsFile.getFromOffsetByFileChannel(dataBuffer, offset);
//                ctx.addHitTime();
//                long useTime = System.nanoTime() - s;
//                StaticsUtil.SECOND_READ_TIME.getAndAdd(useTime);
//            } else {
            long s = System.nanoTime();
            dataBuffer = ByteBuffer.allocate(length);
            tsFile.getFromOffsetByFileChannel(dataBuffer, offset);
//                if (map != null) {
//                    map.put(offset, dataBuffer);
//                    StaticsUtil.FIRST_READ_TIME.getAndAdd((System.nanoTime() - s));
//                }
//            }
            if (dataBuffer.position() != 0) {
                dataBuffer.flip();
            }
            if ("downSample".equals(queryType)) {
                long endRead = System.nanoTime();
                StaticsUtil.READ_DATA_TIME.addAndGet(endRead - startRead);
            }

            int i = 0;//多少行
            double[] doubles = null;
//            Map<Integer, int[]> intMap = null;
            int[] ints1 = null;
            for (long aLong : decompress) {
                if (aLong >= timeLowerBound && aLong < timeUpperBound) {
                    long startGetValue = System.nanoTime();
                    value = null;
                    if (columnIndex < Constants.INT_NUMS) {
                        long compressStart = System.nanoTime();
                        try {
                            if (ints1 == null) {
                                int intCompressLength = dataBuffer.getShort();
                                dataBuffer.position(2);
                                ints1 = IntCompress.getByLineNum(dataBuffer, index.getValueSize(), columnIndex, intCompressLength);
                            }
//                            final int[] ints1 = intMap.get(columnIndex);
//                            if (cacheDataMap != null) {
//                                final TSDBEngineImpl.CacheData cacheData = new TSDBEngineImpl.CacheData(ints1, null);
//                                cacheDataMap.put(index.getOffset(), cacheData);
//                            }
                            value = new ColumnValue.IntegerColumn(ints1[i]);
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("getByIndex time range COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                        }
                        if ("downSample".equals(queryType)) {
                            long compressEnd = System.nanoTime();
                            StaticsUtil.COMPRESS_DATA_TIME.addAndGet(compressEnd - compressStart);
                        }
                    } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                        try {
                            long compressStart = System.nanoTime();
                            if (doubles == null) {
                                doubles = DoubleCompress.decodeByIndex(dataBuffer, Constants.FLOAT_NUMS * valueSize, valueSize, index.getDoubleHeader(),columnIndex-40);
                                if (cacheDataMap != null) {
                                    final TSDBEngineImpl.CacheData cacheData = new TSDBEngineImpl.CacheData(null, doubles);
                                    cacheDataMap.put(index.getOffset(), cacheData);
                                }
                            }
                            value = new ColumnValue.DoubleFloatColumn(doubles[i]);
                            if ("downSample".equals(queryType)) {
                                long compressEnd = System.nanoTime();
                                StaticsUtil.COMPRESS_DATA_TIME.addAndGet(compressEnd - compressStart);
                            }
                        } catch (Exception e) {
                            System.out.println("getByIndex time range COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                        }
                    } else {
                        long compressStart = System.nanoTime();
                        long compressEnd = System.nanoTime();
                        if ("downSample".equals(queryType)) {
                            StaticsUtil.COMPRESS_DATA_TIME.addAndGet(compressEnd - compressStart);
                        }
                    }
                    long endGetValue = System.nanoTime();
                    if ("downSample".equals(queryType)) {
                        StaticsUtil.GET_VALUE_TIMES.addAndGet(endGetValue - startGetValue);
                    }

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
        long end = System.nanoTime();
        if ("downSample".equals(queryType)) {
            StaticsUtil.SINGLEVALUE_TOTAL_TIME.getAndAdd(end - start);
        }
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
        boolean containsBigString = requestedColumns.contains(Constants.bigStringColumn) || requestedColumns.contains(Constants.bigStringColumn1);
        ByteBuffer dataBuffer;
        byte[] bigStringBytes = null;
        try {
            final long offset = index.getOffset();
            final int valueSize = index.getValueSize();
            final int length = index.getLength();
            int m = j % Constants.TS_FILE_NUMS;
            final TSFile tsFile = getTsFileByIndex(m);
            if (containsBigString) {
//                dataBuffer = ByteBuffer.allocate(length);
                dataBuffer = TOTAL_DIRECT_BUFFER.get();
                dataBuffer.clear();
                dataBuffer.position(0);
                dataBuffer.limit(length);
                tsFile.getFromOffsetByFileChannel(dataBuffer, offset);
                dataBuffer.flip();
                if (bigStringBytes == null) {
                    int bigStringLength = dataBuffer.getInt(index.getBigStringOffset());
                    bigStringBytes = new byte[bigStringLength];
                    dataBuffer.position(index.getBigStringOffset() + 4);
                    dataBuffer.get(bigStringBytes, 0, bigStringLength);
                    bigStringBytes = Zstd.decompress(bigStringBytes, valueSize * 130);
                }
            } else {
//                dataBuffer = ByteBuffer.allocate(index.getBigStringOffset());
                dataBuffer = TOTAL_DIRECT_BUFFER.get();
                dataBuffer.clear();
                dataBuffer.position(0);
                dataBuffer.limit(index.getBigStringOffset());
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
            for (long aLong : decompress) {
                if (aLong >= timeLowerBound && aLong < timeUpperBound) {
                    Map<String, ColumnValue> columns = new HashMap<>(requestedColumns.size());
                    for (String requestedColumn : requestedColumns) {
                        final int columnIndex = SchemaUtil.getIndexByColumn(requestedColumn); //多少列
                        if (columnIndex < Constants.INT_NUMS) {
                            try {
                                if (ints == null) {
                                    final byte[] allocate1 = new byte[intCompressLength];
                                    dataBuffer.position(2);
                                    dataBuffer.get(allocate1);
                                    ints = IntCompress.decompress4(allocate1, index.getValueSize());
                                }
                                int off = columnIndex * valueSize + i;
                                columns.put(requestedColumn, new ColumnValue.IntegerColumn((int) ints[off]));

                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                            }
                        } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                            try {
                                if (doubles == null) {
                                    final byte[] allocate1 = new byte[doubleCompressInt];
                                    dataBuffer.position(
                                            +intCompressLength + 2
                                                    + 2);
                                    dataBuffer.get(allocate1);
                                    doubles = DoubleCompress.decode2(ByteBuffer.wrap(allocate1), Constants.FLOAT_NUMS * valueSize, valueSize, index.getDoubleHeader());
                                }
                                int position = ((columnIndex - Constants.INT_NUMS) * valueSize + i);
                                columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubles[position]));
                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                            }
                        } else {
                            if (isBigString(columnIndex)) {
                                int bigStringSize;
                                int off;
                                if (columnIndex == 58) {
                                    off = i * 30;
                                    bigStringSize = 30;
                                } else {
                                    off = 30 * valueSize + i * 100;
                                    bigStringSize = 100;
                                }
//                                byte[] dest = new byte[bigStringSize];
//                                ArrayUtils.copy(bigStringBytes, off, dest, 0, bigStringSize);
//                                columns.put(requestedColumn, new ColumnValue.StringColumn(ByteBuffer.wrap(dest)));
                                columns.put(requestedColumn, new ColumnValue.StringColumn(ByteBuffer.wrap(bigStringBytes, off, bigStringSize)));
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
            boolean containsBigString = requestedColumns.contains(Constants.bigStringColumn) || requestedColumns.contains(Constants.bigStringColumn1);
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
                    bigStringBytes = Zstd.decompress(bigStringBytes, valueSize * 130);
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
            int doubleCompressInt = dataBuffer.getShort(intCompressLength + 2);
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
                        if (columnIndex < Constants.INT_NUMS) {
                            try {
                                if (ints == null) {
                                    final byte[] allocate1 = new byte[intCompressLength];
                                    dataBuffer.position(2);
                                    dataBuffer.get(allocate1);
                                    ints = IntCompress.decompress4(allocate1, index.getValueSize());
                                }
                                int off = columnIndex * valueSize + i;
                                columns.put(requestedColumn, new ColumnValue.IntegerColumn(ints[off]));

                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                            }
                        } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                            try {
                                if (doubles == null) {
                                    final byte[] allocate1 = new byte[doubleCompressInt];
                                    dataBuffer.position(
                                            +intCompressLength + 2
                                                    + 2);
                                    dataBuffer.get(allocate1);
                                    doubles = DoubleCompress.decode2(ByteBuffer.wrap(allocate1), Constants.FLOAT_NUMS * valueSize, valueSize, index.getDoubleHeader());
                                }
                                int position = ((columnIndex - Constants.INT_NUMS) * valueSize + i);
                                columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubles[position]));
                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                            }
                        } else {
                            if (isBigString(columnIndex)) {
                                int bigStringSize;
                                int off;
                                if (columnIndex == 58) {
                                    off = i * 30;
                                    bigStringSize = 30;
                                } else {
                                    off = 30 * valueSize + i * 100;
                                    bigStringSize = 100;
                                }
//                                byte[] dest = new byte[bigStringSize];
//                                ArrayUtils.copy(bigStringBytes, off, dest, 0, bigStringSize);
                                columns.put(requestedColumn, new ColumnValue.StringColumn(ByteBuffer.wrap(bigStringBytes, off, bigStringSize)));
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

                                            +intCompressLength + 2
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
            ByteBuffer intBuffer;
            ByteBuffer doubleBuffer;
            ByteBuffer longBuffer;
            ByteBuffer stringLengthBuffer;
            ByteBuffer[] stringList = new ByteBuffer[8 * lineNum];
            ByteBuffer[] bigStringList = new ByteBuffer[2 * lineNum];
            AtomicInteger bigStringLength = new AtomicInteger();
            double[] doubles;
            long[] longs;
            int[] ints;
            AtomicInteger longPosition = new AtomicInteger();
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
            } else {
                ints = new int[lineNum * Constants.INT_NUMS];
                doubles = new double[lineNum * Constants.FLOAT_NUMS];
                longs = new long[lineNum];
            }
            AtomicInteger totalStringLength = new AtomicInteger();
            long maxTimestamp = Long.MIN_VALUE;
            long minTimestamp = Long.MAX_VALUE;
            final int valueSize = valueList.size();
            int l = 0;
            for (Value value : valueList) {
                long timestamp = value.getTimestamp();
                Map<String, ColumnValue> columns = value.getColumns();
                maxTimestamp = Math.max(maxTimestamp, timestamp);
                minTimestamp = Math.min(minTimestamp, timestamp);
                AtomicInteger i = new AtomicInteger();
                int finalL = l;
                columns.forEach((k, columnValue) -> {
                    final int columnIndex = SchemaUtil.COLUMNS_INDEX_ARRAY[i.getAndIncrement()];
                    if (columnIndex < Constants.INT_NUMS) {
                        int integerValue = columnValue.getIntegerValue();
                        if (aggBucket != null) {
                            aggBucket.updateInt(integerValue, columnIndex);
                        }
                        ints[columnIndex * valueSize + finalL] = integerValue;
                    } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                        final double doubleFloatValue = columnValue.getDoubleFloatValue();
                        if (aggBucket != null) {
                            aggBucket.updateDouble(doubleFloatValue, columnIndex);
                        }
                        doubles[(columnIndex - Constants.INT_NUMS) * valueSize + finalL] = doubleFloatValue;
                    } else {
                        final ByteBuffer stringValue = columnValue.getStringValue();
                        if (isBigString(k)) {
                            bigStringList[(columnIndex - Constants.INT_NUMS - Constants.FLOAT_NUMS - 8) * valueSize + finalL] = stringValue;
                            bigStringLength.addAndGet(stringValue.remaining());
                        } else {
                            stringList[(columnIndex - Constants.INT_NUMS - Constants.FLOAT_NUMS) * valueSize + finalL] = stringValue;
                        }
                        totalStringLength.getAndAdd(stringValue.remaining());
                    }
                    if (columnIndex == 0) {
                        longs[longPosition.getAndIncrement()] = value.getTimestamp();
                    }
                });
                l++;
            }

            long prepareDataTime = System.nanoTime();
            //压缩string
            CompressResult compressResult = StringCompress.compress1(stringList, lineNum);
            final ByteBuffer allocate = ByteBuffer.allocate(bigStringLength.get());
            for (ByteBuffer byteBuffer : bigStringList) {
                allocate.put(byteBuffer);
            }
            final byte[] stringCompress = compressResult.compressedData;
            final byte[] stringCompress1 = Zstd.compress(allocate.array(), 3);
            short[] stringLengthArray = compressResult.stringLengthArray;


            //压缩double
            final doubleCompressResult compressResult1 = DoubleCompress.encode2(doubles, valueList.size());
            final byte[] compressDouble = compressResult1.getData();
            final byte[] doubleHeader = compressResult1.getHeader();
            // 压缩long
            final byte[] compress1 = LongCompress.compress(longs);
            long previousLong = longs[longs.length - 1];

            //压缩int
            final IntCompress.IntCompressResult intCompressResult = IntCompress.compress4(ints, lineNum);
            byte[] compress2 = intCompressResult.data;
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
            StaticsUtil.STRING_TOTAL_LENGTH.getAndAdd(totalStringLength.get() + bigStringLength.get());
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
                intCompressResult.data = null;
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
                        , bigOffset
                        , doubleHeader
                        , intCompressResult);
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

    public void totalCompressInShutDown() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < Constants.TOTAL_COMPRESS_NUMS; i++) {
            tsFiles[i].totalCompressInShutDown();
        }
        System.out.println("totalCompressInShutDown cost: " + (System.currentTimeMillis() - start));
    }

    public void loadBucket() {
        long startTime = System.currentTimeMillis();
        ExecutorService executorService = new ThreadPoolExecutor(500, 600,
                2L, TimeUnit.MINUTES, new LinkedBlockingQueue<>(100));
        int batch = Constants.TS_FILE_NUMS * 4;
        List<IndexListWrapper>[] indexWrapperList = new ArrayList[batch];
        for (int i = 0; i < batch; i++) {
            indexWrapperList[i] = new ArrayList<>();
        }
        for (int i = 0; i < INDEX_ARRAY.length; i++) {
            indexWrapperList[i % batch].add(new IndexListWrapper(INDEX_ARRAY[i], i));
        }
        try {
            final CountDownLatch countDownLatch = new CountDownLatch(batch);
            AtomicInteger atomicInteger = new AtomicInteger(0);
            for (int i = 0; i < indexWrapperList.length; i++) {
                final List<IndexListWrapper> indices = indexWrapperList[i];
                executorService.submit(() -> {
                    try {
                        for (IndexListWrapper indexListWrapper : indices) {
                            for (Index index : indexListWrapper.indexList) {
                                if (index.getAggBucket() != null) {
                                    continue;
                                }
                                final int bigStringOffset = index.getBigStringOffset();
                                final long offset = index.getOffset();
                                final TSFile tsFileByIndex = getTsFileByIndex(indexListWrapper.i % Constants.TS_FILE_NUMS);
                                final ByteBuffer allocate = ByteBuffer.allocate(bigStringOffset);
                                tsFileByIndex.getFromOffsetByFileChannel(allocate, offset);
                                allocate.flip();
                                int intCompressLength = allocate.getShort();
                                int doubleCompressInt = allocate.getShort(intCompressLength + 2);
                                byte[] allocate1 = new byte[intCompressLength];
                                allocate.position(2);
                                allocate.get(allocate1);
                                int[] ints = IntCompress.decompress4(allocate1, index.getValueSize());
                                allocate1 = new byte[doubleCompressInt];
                                allocate.position(
                                        +intCompressLength + 2
                                                + 2);
                                allocate.get(allocate1);
                                double[] doubles = new double[0];
                                try {
                                    doubles = DoubleCompress.decode2(ByteBuffer.wrap(allocate1), Constants.FLOAT_NUMS * index.getValueSize(), index.getValueSize(), index.getDoubleHeader());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                final AggBucket aggBucket = new AggBucket();
                                for (int i1 = 0; i1 < doubles.length; i1++) {
                                    int columnIndex = i1 / Constants.CACHE_VINS_LINE_NUMS + Constants.INT_NUMS;
                                    aggBucket.updateDouble(doubles[i1], columnIndex);

                                }
                                for (int i1 = 0; i1 < ints.length; i1++) {
                                    int columnIndex = i1 / Constants.CACHE_VINS_LINE_NUMS;
                                    aggBucket.updateInt(ints[i1], columnIndex);
                                }
                                index.setAggBucket(aggBucket);
                                if (atomicInteger.getAndIncrement() % 100000 == 0) {
                                    System.out.println("load bucket num" + atomicInteger.get());
                                }
                            }

                        }
                    } catch (Exception e) {
                        System.out.println("loadBucket error" + e);
                        e.printStackTrace();
                    } finally {
                        countDownLatch.countDown();

                    }
                });
            }
            countDownLatch.await();
            System.out.println("loadBucket cost: " + (System.currentTimeMillis() - startTime) + " ms");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class IndexListWrapper {
        List<Index> indexList;
        int i;

        public IndexListWrapper(List<Index> indexList, int i) {
            this.indexList = indexList;
            this.i = i;
        }
    }

}

