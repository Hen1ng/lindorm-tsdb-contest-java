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
import static com.alibaba.lindorm.contest.util.Constants.TS_FILE_NUMS;
import static com.alibaba.lindorm.contest.util.Constants.isBigString;


public class TSFileService {

    public static final ThreadLocal<ByteBuffer> TOTAL_LONG_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * 8));
    public static final ThreadLocal<ByteBuffer> TOTAL_STRING_LENGTH_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocate(Constants.CACHE_VINS_LINE_NUMS * Constants.STRING_NUMS * 4));
    public static final ThreadLocal<ByteBuffer> TOTAL_DIRECT_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(1024 * 6 * 10));
    public static final ThreadLocal<ByteBuffer> TOTAL_INT_DIRECT_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(1024 * 6 * 10));
    public static final ThreadLocal<ArrayList<Row>> LIST_THREAD_LOCAL = ThreadLocal.withInitial(ArrayList::new);

    public static final ThreadLocal<int[]> INT_ARRAY_BUFFER = ThreadLocal.withInitial(() -> new int[Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS]);
    public static final ThreadLocal<double[]> DOUBLE_ARRAY_BUFFER = ThreadLocal.withInitial(() -> new double[Constants.CACHE_VINS_LINE_NUMS * Constants.FLOAT_NUMS]);
    public static final ThreadLocal<long[]> LONG_ARRAY_BUFFER = ThreadLocal.withInitial(() -> new long[Constants.CACHE_VINS_LINE_NUMS]);

    private final TSFile[] tsFiles;

    private final BigStringFile[] bigStringFiles;

    public IntFile[] getIntFiles() {
        return intFiles;
    }

    private final IntFile[] intFiles;
    private final AtomicLong writeTimes = new AtomicLong(0);

    private BucketArrayFactory bucketArrayFactory;
    private ExecutorService executorService;

    public TSFileService(String file) {
        this.executorService = Executors.newFixedThreadPool(48);
        this.tsFiles = new TSFile[Constants.TS_FILE_NUMS];
        this.intFiles = new IntFile[Constants.TS_FILE_NUMS];
        this.bigStringFiles = new BigStringFile[TS_FILE_NUMS];
        if (RestartUtil.IS_FIRST_START) {
            bucketArrayFactory = new BucketArrayFactory(Constants.TOTAL_BUCKET + 100);
        }
        for (int i = 0; i < Constants.TS_FILE_NUMS; i++) {
            long initPosition = (long) i * Constants.TS_FILE_SIZE;
            tsFiles[i] = new TSFile(file, i, initPosition);
            intFiles[i] = new IntFile(file, i, initPosition);
            bigStringFiles[i] = new BigStringFile(file, i, initPosition);
        }
    }

    public TSFile getTsFileByIndex(int i) {
        return tsFiles[i];
    }

    public IntFile getIntFileByIndex(int i) {
        return intFiles[i];
    }

    public BigStringFile getBigStringFileByIndex(int i) {
        return bigStringFiles[i];
    }

    public int getHashCodeByByteArray(byte[] bytes) {
        int h = 0;
        for (int i = 0; i < 17; i++) {
            h = 31 * h + bytes[i];
        }
        return Math.abs(h);
    }


    public ArrayList<ColumnValue> getSingleValueByIndex(Vin vin, long timeLowerBound, long timeUpperBound, Index index, int columnIndex, int j, Map<Long, ByteBuffer> map, Context ctx, Map<Long, TSDBEngineImpl.CacheData> cacheDataMap, String queryType, Map<Long, Long> queryTimeMap) {
        ArrayList<ColumnValue> rowArrayList = new ArrayList<>();
        try {
            long longPrevious = index.getPreviousTimeStamp();
            byte[] longBytes = index.getTimeStampBytes();
            final int valueSize = index.getValueSize();
            long[] decompress = LongCompress.decompress(longBytes, longPrevious, valueSize);
            double[] doubles = null;
            int[] ints1 = null;
            ColumnValue value;
            if (cacheDataMap != null) {
                TSDBEngineImpl.CacheData cacheData = cacheDataMap.get(index.getOffset());
                if (columnIndex < Constants.INT_NUMS) {
                    if (cacheData != null) {
                        ArrayList<ColumnValue> arrayList = new ArrayList<>(32);
                        ints1 = cacheData.ints;
                        int i = 0;
                        for (long aLong : decompress) {
                            if (aLong >= timeLowerBound && aLong < timeUpperBound) {
                                int off = columnIndex * valueSize + i;
                                value = new ColumnValue.IntegerColumn(ints1[off]);
                                arrayList.add(value);
                            }
                            i++;
                        }
                        if (!arrayList.isEmpty()) {
                            return arrayList;
                        }
                    }
                } else {
                    if (cacheData != null) {
                        ArrayList<ColumnValue> arrayList = new ArrayList<>(32);
                        doubles = cacheData.doubles;
                        int i = 0;
                        for (long aLong : decompress) {
                            if (aLong >= timeLowerBound && aLong < timeUpperBound) {
                                value = new ColumnValue.DoubleFloatColumn(doubles[i]);
                                arrayList.add(value);
                            }
                            i++;
                        }
                        if (!arrayList.isEmpty()) {
                            return arrayList;
                        }
                    }
                }
            }
            int m = j % Constants.TS_FILE_NUMS;
            final TSFile tsFile = getTsFileByIndex(m);
            final IntFile intFile = getIntFileByIndex(m);
            ByteBuffer dataBuffer;
            long offset;
            int length;
//            long startRead = System.nanoTime();
            if (columnIndex < Constants.INT_NUMS) {
                offset = index.getIntOffset() + 2;
                length = index.getIntLength() - 2;
                dataBuffer = ByteBuffer.allocate(length);
                intFile.getFromOffsetByFileChannel(dataBuffer, offset);
            } else {
                offset = index.getOffset();
                byte[] doubleHeader = index.getDoubleHeader();
                ByteBuffer header = ByteBuffer.wrap(doubleHeader);
                int doubleDeltaLength = header.getInt();
                int corrilaLength = header.getInt();
                if (DoubleCompress.doubleDeltaFlag[columnIndex - 40]) {
                    offset += 2;
                    length = doubleDeltaLength;
                } else {
                    offset += 2 + doubleDeltaLength;
                    length = corrilaLength;
                }
                dataBuffer = ByteBuffer.allocate(length);
                tsFile.getFromOffsetByFileChannel(dataBuffer, offset, ctx);
                dataBuffer.flip();
            }

            int i = 0;//多少行
            for (long aLong : decompress) {
                if (aLong >= timeLowerBound && aLong < timeUpperBound) {
//                    long startGetValue = System.nanoTime();
                    value = null;
                    if (columnIndex < Constants.INT_NUMS) {
//                        long compressStart = System.nanoTime();
                        try {
                            if (ints1 == null) {
                                ints1 = IntCompress.decompressOriginBySingle(dataBuffer.array(), valueSize, columnIndex);
                                if (cacheDataMap != null) {
                                    final TSDBEngineImpl.CacheData cacheData = new TSDBEngineImpl.CacheData(ints1, null);
                                    cacheDataMap.put(index.getOffset(), cacheData);
                                }
                            }
                            int off = columnIndex * valueSize + i;
                            value = new ColumnValue.IntegerColumn(ints1[off]);
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("getSingleValueByIndex COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                        }

                    } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                        try {
                            if (doubles == null) {
                                doubles = DoubleCompress.decodeByIndex(dataBuffer, Constants.FLOAT_NUMS * valueSize, valueSize, index.getDoubleHeader(), columnIndex - 40);
                                if (cacheDataMap != null) {
                                    final TSDBEngineImpl.CacheData cacheData = new TSDBEngineImpl.CacheData(null, doubles);
                                    cacheDataMap.put(index.getOffset(), cacheData);
                                }
                            }
                            value = new ColumnValue.DoubleFloatColumn(doubles[i]);
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("getSingleValueByIndex COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                        }
                    } else {

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

    public int[] decompressInts(Index index, IntFile intFile,Set<Integer> columns) {
        final long intOffset = index.getIntOffset();
        final ByteBuffer wrap = ByteBuffer.allocate(index.getIntLength() - 2);
        intFile.getFromOffsetByFileChannel(wrap, intOffset + 2);
//        long start = System.nanoTime();
        int[] ints = IntCompress.decompressOriginByColumns(wrap.array(), index.getValueSize(), columns);
//        StaticsUtil.TIMERANGE_UNCOMPRESS_INT_TIME.addAndGet(System.nanoTime()-start);
        return ints;
    }

    public double[] decompressDoubles(Index index, ByteBuffer dataBuffer, int doubleCompressInt, int valueSize,Set<Integer> columns) throws IOException {
        final byte[] allocate1 = new byte[doubleCompressInt];
        dataBuffer.position(2);
        dataBuffer.get(allocate1);
//        long start = System.nanoTime();
        double[] doubles = DoubleCompress.decode2ByColumns(ByteBuffer.wrap(allocate1), Constants.FLOAT_NUMS * valueSize, valueSize, index.getDoubleHeader(), columns);
//        StaticsUtil.TIMERANGE_UNCOMPRESS_DOUBLE_TIME.addAndGet(System.nanoTime()-start);
        return doubles;
//        double[] doubles = DoubleCompress.decode2(ByteBuffer.wrap(allocate1), Constants.FLOAT_NUMS * valueSize, valueSize, index.getDoubleHeader());
//        return doubles;
    }

    public List<ByteBuffer> decompressString(Index index, ByteBuffer dataBuffer, int doubleCompressInt) throws IOException {
//        long start = System.nanoTime();
        ByteBuffer stringLengthBuffer = null;
        Short everyStringLength = null;
        everyStringLength = dataBuffer.getShort(

                +doubleCompressInt + 2);
        final byte[] bytes = new byte[everyStringLength - 4];
        dataBuffer.position(

                +doubleCompressInt + 2
                        + 2);
        int totalLength = dataBuffer.getInt();
        dataBuffer.get(bytes, 0, bytes.length);
        final short[] decompress1 = IntCompress.decompressShort(bytes, index.getValueSize(), totalLength);
        stringLengthBuffer = ByteBuffer.allocateDirect(decompress1.length * 2);
        for (short i1 : decompress1) {
            stringLengthBuffer.putShort(i1);
        }
        int stringLength = index.getLength() - (
                +doubleCompressInt + 2
                        + everyStringLength + 2
        );
        byte[] bytes1 = new byte[stringLength - 4];
        dataBuffer.position(

                +doubleCompressInt + 2
                        + everyStringLength + 2
        );
        int totalLength1 = dataBuffer.getInt();
        dataBuffer.get(bytes1, 0, bytes1.length);
        ArrayList<ByteBuffer> byteBuffers = StringCompress.decompress1(bytes1, stringLengthBuffer, index.getValueSize(), totalLength1);
//        StaticsUtil.TIMERANGE_UNCOMPRESS_STRING_TIME.addAndGet(System.nanoTime()-start);
        return byteBuffers;
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
            final IntFile intFile = getIntFileByIndex(m);
            final BigStringFile bigStringFile = getBigStringFileByIndex(m);
            if (containsBigString) {
                final ByteBuffer byteBuffer = ByteBuffer.allocate(index.getBigStringLength());
                bigStringFile.getFromOffsetByFileChannel(byteBuffer, index.getBigStringOffset());
                byteBuffer.flip();
                if (bigStringBytes == null) {
                    bigStringBytes = byteBuffer.array();
                }
            }
            dataBuffer = TOTAL_DIRECT_BUFFER.get();
            dataBuffer.clear();
            dataBuffer.position(0);
            dataBuffer.limit(index.getLength());
            tsFile.getFromOffsetByFileChannel(dataBuffer, offset, null);
            dataBuffer.flip();
            dataBuffer.position(0);
            long longPrevious = index.getPreviousTimeStamp();
            byte[] longBytes = index.getTimeStampBytes();
            long[] decompress = LongCompress.decompress(longBytes, longPrevious, valueSize);
            int doubleCompressInt = dataBuffer.getShort();
            int i = 0;//多少行
            int[] ints = null;
            double[] doubles = null;
            List<ByteBuffer> stringBytes = null;
            Future<int[]> intsFuture = null;
            Future<double[]> doubleFuture = null;
            Future<List<ByteBuffer>> stringFuture = null;
            Future<byte[]> bigstringFuture = null;
            Set<Integer> doubleColumns = new HashSet<>();
            Set<Integer> intColumns = new HashSet<>();
            for (String requestedColumn : requestedColumns) {
                final int columnIndex = SchemaUtil.getIndexByColumn(requestedColumn);
                if(columnIndex<Constants.INT_NUMS){
                    intColumns.add(columnIndex);
                } else if(columnIndex<Constants.INT_NUMS+Constants.FLOAT_NUMS){
                    doubleColumns.add(columnIndex-Constants.INT_NUMS);
                }
            }
            for (String requestedColumn : requestedColumns) {
                final int columnIndex = SchemaUtil.getIndexByColumn(requestedColumn);
                if (intsFuture == null && columnIndex < Constants.INT_NUMS) {
                    intsFuture = executorService.submit(() -> decompressInts(index, intFile,intColumns));
                } else if (doubleFuture == null && columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                    doubleFuture = executorService.submit(() -> {
                        try {
                            return decompressDoubles(index, dataBuffer.asReadOnlyBuffer().duplicate(), doubleCompressInt, valueSize,doubleColumns);
                        } catch (IOException e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    });
                } else {
                    if (isBigString(columnIndex)) {
                        //donothing
                        byte[] finalBigStringBytes = bigStringBytes;
                        bigstringFuture = executorService.submit(() -> Zstd.decompress(finalBigStringBytes, valueSize * 130));
                    } else {
                        if (stringFuture != null) continue;
                        stringFuture = executorService.submit(() -> {
                            try {
                                return decompressString(index, dataBuffer.asReadOnlyBuffer().duplicate(), doubleCompressInt);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                }
            }
            if (intsFuture != null) {
                ints = intsFuture.get();
            }
            if (doubleFuture != null) {
                doubles = doubleFuture.get();
            }
            if (stringFuture != null) {
                stringBytes = stringFuture.get();
            }
            if (bigstringFuture != null) {
                bigStringBytes = bigstringFuture.get();
            }
            for (long aLong : decompress) {
                if (aLong >= timeLowerBound && aLong < timeUpperBound) {
                    Map<String, ColumnValue> columns = new HashMap<>(requestedColumns.size());
                    for (String requestedColumn : requestedColumns) {
                        final int columnIndex = SchemaUtil.getIndexByColumn(requestedColumn); //多少列
                        if (columnIndex < Constants.INT_NUMS) {
                            try {
                                int off = columnIndex * valueSize + i;
                                assert ints != null;
                                columns.put(requestedColumn, new ColumnValue.IntegerColumn(ints[off]));
                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                            }
                        } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                            try {
                                int position = ((columnIndex - Constants.INT_NUMS) * valueSize + i);
                                assert doubles != null;
                                columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubles[position]));
                            } catch (Exception e) {
                                e.printStackTrace();
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
                                assert bigStringBytes != null;
                                columns.put(requestedColumn, new ColumnValue.StringColumn(ByteBuffer.wrap(bigStringBytes, off, bigStringSize)));
                            } else {
                                try {
                                    int stringNum = columnIndex - (Constants.INT_NUMS + Constants.FLOAT_NUMS);
                                    int stringPosition = (stringNum * valueSize + i);
                                    assert stringBytes != null;
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
            final IntFile intFile = getIntFileByIndex(m);
            final BigStringFile bigStringFile = getBigStringFileByIndex(m);
            ByteBuffer dataBuffer;
            byte[] bigStringBytes = null;
            if (containsBigString) {
                final ByteBuffer byteBuffer = ByteBuffer.allocate(index.getBigStringLength());
                bigStringFile.getFromOffsetByFileChannel(byteBuffer, index.getBigStringOffset());
                byteBuffer.flip();
                if (bigStringBytes == null) {
                    bigStringBytes = Zstd.decompress(byteBuffer.array(), valueSize * 130);
                }
            }
            dataBuffer = ByteBuffer.allocate(index.getLength());
            tsFile.getFromOffsetByFileChannel(dataBuffer, offset, null);
            dataBuffer.flip();
            dataBuffer.position(0);
            long longPrevious = index.getPreviousTimeStamp();
            byte[] longBytes = index.getTimeStampBytes();
            long[] decompress = LongCompress.decompress(longBytes, longPrevious, valueSize);
            int doubleCompressInt = dataBuffer.getShort();
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
                                    final long intOffset = index.getIntOffset();
                                    final ByteBuffer wrap = ByteBuffer.allocate(index.getIntLength() - 2);
                                    intFile.getFromOffsetByFileChannel(wrap, intOffset + 2);
                                    ints = IntCompress.decompressOrigin(wrap.array(), index.getValueSize());
                                }
                                int off = columnIndex * valueSize + i;
                                columns.put(requestedColumn, new ColumnValue.IntegerColumn(ints[off]));

                            } catch (Exception e) {
                                System.out.println("getByIndex COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                            }
                        } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                            try {
                                if (doubles == null) {
                                    final byte[] allocate1 = new byte[doubleCompressInt];
                                    dataBuffer.position(

                                            +2);
                                    dataBuffer.get(allocate1);
                                    doubles = DoubleCompress.decode2(ByteBuffer.wrap(allocate1), Constants.FLOAT_NUMS * valueSize, valueSize, index.getDoubleHeader());
                                }
                                int position = ((columnIndex - Constants.INT_NUMS) * valueSize + i);
                                columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubles[position]));
                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("getByIndex COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
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

                                            +doubleCompressInt + 2);
                                    final byte[] bytes = new byte[everyStringLength - 4];
                                    dataBuffer.position(

                                            +doubleCompressInt + 2
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
                                    int stringLength = index.getLength() - (


                                            +doubleCompressInt + 2
                                                    + everyStringLength + 2

                                    );
                                    byte[] bytes = new byte[stringLength - 4];
                                    dataBuffer.position(

                                            +doubleCompressInt + 2
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
                                    System.out.println("getByIndex String error, e:" + e + "index:" + index);
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
            AggBucket aggBucket = bucketArrayFactory.getAggBucket();
            int m = j % Constants.TS_FILE_NUMS;
            ByteBuffer[] stringList = new ByteBuffer[8 * lineNum];
            ByteBuffer[] bigStringList = new ByteBuffer[2 * lineNum];
            AtomicInteger bigStringLength = new AtomicInteger();
            double[] doubles;
            long[] longs;
            int[] ints;
            AtomicInteger longPosition = new AtomicInteger();
            if (lineNum == Constants.CACHE_VINS_LINE_NUMS) {
                ints = INT_ARRAY_BUFFER.get();
                doubles = DOUBLE_ARRAY_BUFFER.get();
                longs = LONG_ARRAY_BUFFER.get();
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
            byte[] compress2 = IntCompress.compressOrigin(ints, lineNum);
            byte[] stringLengthArrayCompress = IntCompress.compressShort(stringLengthArray, lineNum);
            int total =  //timestamp
//                    compress2.length + 2 //int
                    +(2 + compressDouble.length) //double
                            + stringLengthArrayCompress.length + 2 //string长度记录
                            + stringCompress.length; //string存储string
            ByteBuffer byteBuffer = TOTAL_DIRECT_BUFFER.get();
            ByteBuffer intByteBuffer = TOTAL_INT_DIRECT_BUFFER.get();
            intByteBuffer.position(0);
            intByteBuffer.limit(compress2.length + 2);
            byteBuffer.clear();
            byteBuffer.limit(total);

            //int 单独处理
            intByteBuffer.putShort((short) compress2.length);
            intByteBuffer.put(compress2);
            // double
            byteBuffer.putShort((short) compressDouble.length);
            byteBuffer.put(compressDouble);
            //string
            byteBuffer.putShort((short) stringLengthArrayCompress.length);
            byteBuffer.put(stringLengthArrayCompress);
            byteBuffer.put(stringCompress);

            try {
                TSFile tsFile = getTsFileByIndex(m);
                IntFile intFile = getIntFileByIndex(m);
                final BigStringFile bigStringFile = getBigStringFileByIndex(m);
                final long append = tsFile.append(byteBuffer);
                final long intAppend = intFile.append(intByteBuffer);
                final long bigStringAppend = bigStringFile.append(ByteBuffer.wrap(stringCompress1));
                final Index index = new Index(append
                        , intAppend
                        , maxTimestamp
                        , minTimestamp
                        , total
                        , lineNum
                        , aggBucket
                        , 2 + compress2.length
                        , 2 + compress2.length + 2 + compressDouble.length
                        , previousLong
                        , compress1
                        , bigStringAppend
                        , doubleHeader
                        , null
                        , stringCompress1.length);
                MapIndex.put(j, index);
                valueList.clear();
            } catch (Exception e) {
                System.out.println("write append error" + e);
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
        tsFile.getFromOffsetByFileChannel(timestampBuffer, offset, null);
        timestampBuffer.flip();
        return timestampBuffer;
    }


    public void loadBucket() {
        long startTime = System.currentTimeMillis();
        ExecutorService executorService = new ThreadPoolExecutor(24, 24,
                2L, TimeUnit.MINUTES, new LinkedBlockingQueue<>(100));
        int batch = 24;
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

                                //int
                                final TSFile tsFileByIndex = getTsFileByIndex(indexListWrapper.i % Constants.TS_FILE_NUMS);
                                IntFile intFile = getIntFileByIndex(indexListWrapper.i % Constants.TS_FILE_NUMS);
                                final long intOffset = index.getIntOffset();
                                final ByteBuffer wrap = ByteBuffer.allocate(index.getIntLength() - 2);
                                intFile.getFromOffsetByFileChannel(wrap, intOffset + 2);
                                int[] ints = IntCompress.decompressOrigin(wrap.array(), index.getValueSize());

                                //double
                                final int bigStringOffset = index.getLength();
                                final long offset = index.getOffset();
                                final ByteBuffer allocate = ByteBuffer.allocate(bigStringOffset);
                                tsFileByIndex.getFromOffsetByFileChannel(allocate, offset, null);
                                allocate.flip();
                                int doubleCompressInt = allocate.getShort();
                                byte[] allocate1 = new byte[doubleCompressInt];
                                allocate.position(2);
                                allocate.get(allocate1);
                                double[] doubles = new double[0];
                                try {
                                    doubles = DoubleCompress.decode2(ByteBuffer.wrap(allocate1), Constants.FLOAT_NUMS * index.getValueSize(), index.getValueSize(), index.getDoubleHeader());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                final AggBucket aggBucket = new AggBucket();
                                aggBucket.updateDoubleByBatch(doubles, index.getValueSize());
                                aggBucket.updateIntByBatch(ints,index.getValueSize());
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

    public void loadInt() {
        long start = System.currentTimeMillis();
        ExecutorService executorService1 = Executors.newFixedThreadPool(16);
        for (IntFile intFile : intFiles) {
            executorService1.execute(intFile::loadInt);
        }
        executorService1.shutdown();
        boolean b;
        try {
            b = executorService1.awaitTermination(30, TimeUnit.SECONDS);
        }catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if(b) {
            System.out.println("load int cost" + (System.currentTimeMillis() - start) + " ms");
        }else{
            System.out.println("load int file fail");
        }
        MemoryUtil.printJVMHeapMemory();
    }
}

