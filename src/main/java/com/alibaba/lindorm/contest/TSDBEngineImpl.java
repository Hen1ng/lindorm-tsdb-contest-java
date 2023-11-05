//
// You should modify this file.
//
// Refer TSDBEngineSample.java to ensure that you have understood
// the interface semantics correctly.
//

package com.alibaba.lindorm.contest;

import com.alibaba.lindorm.contest.file.FilePosition;
import com.alibaba.lindorm.contest.file.TSFile;
import com.alibaba.lindorm.contest.file.TSFileService;
import com.alibaba.lindorm.contest.index.BigBucket;
import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.index.MapIndex;
import com.alibaba.lindorm.contest.memory.MemoryTable;
import com.alibaba.lindorm.contest.memory.VinDictMap;
import com.alibaba.lindorm.contest.structs.*;
import com.alibaba.lindorm.contest.util.*;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.alibaba.lindorm.contest.structs.ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT;
import static com.alibaba.lindorm.contest.structs.ColumnValue.ColumnType.COLUMN_TYPE_INTEGER;

public class TSDBEngineImpl extends TSDBEngine {

    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong upsertTimes;
    private final AtomicLong executeLatestQueryTimes;
    private final AtomicLong executeLatestQueryVinsSize;
    private final AtomicLong executeTimeRangeQueryTimes;
    private final AtomicLong executeAggregateQueryTimes;

    private final AtomicLong executeDownsampleQueryTimes;
    private TSFileService fileService = null;
    private final MemoryTable memoryTable;
    private Unsafe unsafe = UnsafeUtil.getUnsafe();
    private File indexFile;
    private File vinDictFile;
    private File schemaFile;
    private File bigIntFile;
    private File bigIntMapFile;
    private FilePosition filePosition;


    /**
     * This constructor's function signature should not be modified.
     * Our evaluation program will call this constructor.
     * The function's body can be modified.
     */
    public TSDBEngineImpl(File dataPath) {
        super(dataPath);
        this.indexFile = new File(dataPath.getPath() + "/index.txt");
        this.vinDictFile = new File(dataPath.getPath() + "/vinDict.txt");
        this.schemaFile = new File(dataPath.getPath() + "/schema.txt");
        this.bigIntFile = new File(dataPath.getPath() + "/bigInt.txt");
        this.bigIntMapFile = new File(dataPath.getPath() + "/bigIntMap.txt");
        try {
            RestartUtil.setFirstStart(indexFile);
            this.filePosition = new FilePosition(dataPath.getPath() + "/file_position.txt");
            if (!dataPath.exists()) {
                dataPath.createNewFile();
            }
            this.fileService = new TSFileService(dataPath.getPath());
            if (!indexFile.exists()) {
                indexFile.createNewFile();
            }
            if (!vinDictFile.exists()) {
                vinDictFile.createNewFile();
            }
            if (!schemaFile.exists()) {
                schemaFile.createNewFile();
            }
            if (!bigIntMapFile.exists()) {
                bigIntMapFile.createNewFile();
            }
            if (!bigIntFile.exists()) {
                bigIntFile.createNewFile();
            }
        } catch (Exception e) {
            System.out.println("create dataPath error, e" + e);
        }
        this.upsertTimes = new AtomicLong(0);
        this.memoryTable = new MemoryTable(Constants.TOTAL_VIN_NUMS, fileService);
        this.executeLatestQueryTimes = new AtomicLong(0);
        this.executeTimeRangeQueryTimes = new AtomicLong(0);
        this.executeLatestQueryVinsSize = new AtomicLong(0);
        this.executeAggregateQueryTimes = new AtomicLong(0);
        this.executeDownsampleQueryTimes = new AtomicLong(0);
    }

    @Override
    public void connect() throws IOException {
        System.out.println("connect start...");
        InetAddress localhost = InetAddress.getLocalHost();
        System.out.println("host: " + localhost.getHostAddress());
        long start = System.currentTimeMillis();
//        MapIndex.loadMapFromFile(indexFile);
        VinDictMap.loadMapFromFile(vinDictFile);
        SchemaUtil.loadMapFromFile(schemaFile);
        MapIndex.loadMapFromFileunCompress(indexFile);
        for (int i = 0; i < 40; i++) {
            StaticsUtil.columnInfos.add(new ColumnInfo());
        }
        if (RestartUtil.IS_FIRST_START) {

        } else {
            fileService.loadBucket();
            MapIndex.loadBigBucket();
            memoryTable.loadLastTsToMemory();
        }
        System.gc();
        MemoryUtil.printMemory();
        System.out.println("connect finish, cost: " + (System.currentTimeMillis() - start) + " ms");
    }

    @Override
    public void createTable(String tableName, Schema schema) throws IOException {
        System.out.println("createTable tableName:" + tableName);
        SchemaUtil.setSchema(schema);
    }

    @Override
    public void shutdown() {
        long start = System.currentTimeMillis();
        System.out.println("upsertTimes:" + upsertTimes.get());
        System.out.println("total string length:" + StaticsUtil.STRING_TOTAL_LENGTH.get());
        System.out.println("compress string length:" + StaticsUtil.STRING_COMPRESS_LENGTH.get());
        if (StaticsUtil.STRING_TOTAL_LENGTH.get() != 0) {
            System.out.println("compress string rate:" + StaticsUtil.STRING_COMPRESS_LENGTH.get() * 1.0d / StaticsUtil.STRING_TOTAL_LENGTH.get());
        }
        System.out.println("compress double length: " + StaticsUtil.DOUBLE_COMPRESS_LENGTH.get());
        System.out.println("compress double rate: " + StaticsUtil.DOUBLE_COMPRESS_LENGTH.get() * 1.0d / (180000000L * 10L * 8L));
        System.out.println("compress long length: " + StaticsUtil.LONG_COMPRESS_LENGTH.get());
        System.out.println("compress long rate: " + (StaticsUtil.LONG_COMPRESS_LENGTH.get() * 1.0d) / (180000000L * 8L));
        System.out.println("compress int length: " + StaticsUtil.INT_COMPRESS_LENGTH.get());
        System.out.println("compress int rate: " + (StaticsUtil.INT_COMPRESS_LENGTH.get() * 1.0d) / (180000000L * 40L * 4L));
        System.out.println("compress short :" + (StaticsUtil.STRING_SHORT_LENGTH));
        System.out.println("compress short bytes : " + (StaticsUtil.STRING_BYTE_LENGTH));
        System.out.println("compress indexFile size: " + indexFile.length());
        System.out.println("idle Buffer size : " + StaticsUtil.MAX_IDLE_BUFFER);
        System.out.println("compress use map times : " + StaticsUtil.MAP_COMPRESS_TIME.get());
//        for (String s : SchemaUtil.maps.keySet()) {
//            System.out.println("key: " + s + "size " + SchemaUtil.maps.get(s).size());
//        }
        try {
            if (RestartUtil.IS_FIRST_START) {
                final ExecutorService executorService1 = Executors.newFixedThreadPool(8);
                final Future<Void> submit = executorService1.submit(() -> {
                    SchemaUtil.saveMapToFile(schemaFile);
                    return null;
                });
                memoryTable.writeToFileBeforeShutdown();

                final Future<Void> submit2 = executorService1.submit(() -> {
                    MapIndex.saveMaPToFileCompress(indexFile);
                    return null;
                });
                final Future<Void> submit3 = executorService1.submit(() -> {
                    VinDictMap.saveMapToFile(vinDictFile);
                    return null;
                });
                final Future<Void> submit4 = executorService1.submit(() -> {
                    filePosition.save(fileService.getIntFiles());
                    return null;
                });
                submit2.get();
                submit3.get();
                submit4.get();
                submit.get();


            }
            for (TSFile tsFile : fileService.getTsFiles()) {
                System.out.println("tsFile: " + tsFile.getFileName() + "position: " + tsFile.getPosition().get());
            }
//            fileService.totalCompressInShutDown();
        } catch (Exception e) {
            System.out.println("shutdown error, e" + e);
        }

        GCUtil.printGCInfo();
        MemoryUtil.printMemory();
        System.out.println("shutdown finish, cost: " + (System.currentTimeMillis() - start) + " ms");
    }

    @Override
    public void write(WriteRequest wReq) throws IOException {
        if (upsertTimes.incrementAndGet() == 1) {
            System.out.println("start upsert, ts:" + System.currentTimeMillis());
        }
//        writeThreadSet.add(Thread.currentThread().getName());
        try {
            final Collection<Row> rows = wReq.getRows();
            for (Row row : rows) {
                memoryTable.put(row);
            }
        } catch (Exception e) {
            System.out.println("upsert error, e" + e);
            System.out.println(memoryTable);
        }
    }

    @Override
    public ArrayList<Row> executeLatestQuery(LatestQueryRequest pReadReq) throws IOException {
        try {
            ArrayList<Row> rows = new ArrayList<>(pReadReq.getVins().size());
//            rows.clear();
//            final int size = pReadReq.getRequestedColumns().size();
            final int[] ints = new int[pReadReq.getRequestedColumns().size()];
            int i = 0;
            for (String requestedColumn : pReadReq.getRequestedColumns()) {
                ints[i] = SchemaUtil.getIndexByColumn(requestedColumn);
                i++;
            }
            for (Vin vin : pReadReq.getVins()) {
                Row lastRow = memoryTable.getLastRow(vin, ints, pReadReq.getRequestedColumns());
//                final Row lastRow = memoryTable.getLastRow(vin, pReadReq.getRequestedColumns());
                if (lastRow != null) {
                    rows.add(lastRow);
                }
            }
            return rows;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("executeLatestQuery error, e" + e);
        }
        return null;
    }

    @Override
    public ArrayList<Row> executeTimeRangeQuery(TimeRangeQueryRequest trReadReq) throws IOException {
        if (executeTimeRangeQueryTimes.getAndIncrement() == 1) {
            System.out.println("executeTimeRangeQuery start, ts:" + System.currentTimeMillis());
        }
        if (executeTimeRangeQueryTimes.get() % 200000 == 0) {
//            MemoryUtil.printJVMHeapMemory();
            System.out.println("executeTimeRangeQuery times :" + executeTimeRangeQueryTimes.get() + " querySize:" + trReadReq.getRequestedColumns().size());
            System.out.printf("TIME_RANGE_READ_FILE_SIZE " + StaticsUtil.TIME_RANGE_READ_FILE_SIZE.get() + "TIME_RANGE_READ_TIME " + StaticsUtil.TIME_RANGE_READ_TIME.get());
        }
        try {
            return memoryTable.getTimeRangeRow(trReadReq.getVin(), trReadReq.getTimeLowerBound(), trReadReq.getTimeUpperBound(), trReadReq.getRequestedColumns());
        } catch (Exception e) {
            System.out.println("executeTimeRangeQuery error, e" + e);
        }
        return null;
    }

    @Override
    public ArrayList<Row> executeAggregateQuery(TimeRangeAggregationRequest aggregationReq) throws IOException {
        try {
            return executeAggregateQueryByBucket(aggregationReq);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("executeAggregateQuery error, e" + e);
            System.exit(-1);
        }
        return null;
//        ArrayList<Row> rows = new ArrayList<>();
//        final String columnName = aggregationReq.getColumnName();
//        final Aggregator aggregator = aggregationReq.getAggregator();
//        final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(columnName);
//        Set<String> requestedColumns = new HashSet<>();
//        requestedColumns.add(columnName);
//        final ArrayList<Row> timeRangeRow = memoryTable.getTimeRangeRow(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), aggregationReq.getTimeUpperBound(), requestedColumns);
//        if (timeRangeRow == null || timeRangeRow.isEmpty()) {
//            return rows;
//        }
//        switch (aggregator) {
//            case AVG:
//                double intSum = 0;
//                double doubleSum = 0;
//                if (columnType.equals(COLUMN_TYPE_INTEGER)) {
//                    for (Row row : timeRangeRow) {
//                        intSum += row.getColumns().get(columnName).getIntegerValue();
//                    }
//                } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
//                    for (Row row : timeRangeRow) {
//                        doubleSum += row.getColumns().get(columnName).getDoubleFloatValue();
//                    }
//                } else {
//                    System.out.println("executeAggregateQuery columnValue string type not support compare");
//                }
//                if (columnType.equals(COLUMN_TYPE_INTEGER)) {
//                    Map<String, ColumnValue> columns = new HashMap<>(1);
//                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(intSum / timeRangeRow.size()));
//                    rows.add(new Row(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), columns));
//                } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
//                    Map<String, ColumnValue> columns = new HashMap<>(1);
//                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(doubleSum / timeRangeRow.size()));
//                    rows.add(new Row(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), columns));
//                }
//                break;
//            case MAX:
//                int maxInt = Integer.MIN_VALUE;
//                double maxDouble = -Double.MAX_VALUE;
//                final ColumnValue firstValue = timeRangeRow.get(0).getColumns().get(columnName);
//                if (columnType.equals(COLUMN_TYPE_INTEGER)) {
//                    maxInt = firstValue.getIntegerValue();
//                } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
//                    maxDouble = firstValue.getDoubleFloatValue();
//                }
//                Map<String, ColumnValue> columns = timeRangeRow.get(0).getColumns();
//                if (columnType.equals(COLUMN_TYPE_INTEGER)) {
//                    for (Row row : timeRangeRow) {
//                        final ColumnValue columnValue = row.getColumns().get(columnName);
//                        int integerValue = columnValue.getIntegerValue();
//                        if (integerValue >= maxInt) {
//                            columns = row.getColumns();
//                            maxInt = integerValue;
//                        }
//                    }
//                } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
//                    for (Row row : timeRangeRow) {
//                        final ColumnValue columnValue = row.getColumns().get(columnName);
//                        double doubleFloatValue = columnValue.getDoubleFloatValue();
//                        if (doubleFloatValue >= maxDouble) {
//                            maxDouble = doubleFloatValue;
//                            columns = row.getColumns();
//                        }
//                    }
//                } else {
//                    System.out.println("executeAggregateQuery columnValue string type not support compare");
//                }
//                if (columns != null) {
//                    rows.add(new Row(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), columns));
//                } else {
//                    System.out.println("columns is null");
//                    System.out.println("maxInt: " + maxInt + " maxDouble : " + maxDouble + "timeRangeRow size : " + timeRangeRow.size());
//                    for (Row row : timeRangeRow) {
//                        System.out.println(row.toString());
//                    }
//                }
//                break;
//            default:
//                System.out.println("executeAggregateQuery aggregator error, not support");
//                System.exit(-1);
//        }
//        return rows;
    }

    public ArrayList<Row> getRowsFromIndex(Vin vin, Index index, Set<String> requestColumns) {
        Integer i = VinDictMap.get(vin);
        return fileService.getByIndexV2(vin, index.getMinTimestamp(), index.getMaxTimestamp(), index, requestColumns, i);
    }

    private final AtomicLong aggQueryTimes = new AtomicLong(0);
    public ArrayList<Row> executeAggregateQueryByBucket(TimeRangeAggregationRequest aggregationReq) throws IOException {
        long start1 = System.nanoTime();
        ArrayList<Row> rows = new ArrayList<>();
        final String columnName = aggregationReq.getColumnName();
        final Aggregator aggregator = aggregationReq.getAggregator();
//        final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(columnName);
        long timeLower = aggregationReq.getTimeLowerBound();
        long timeUpper = aggregationReq.getTimeUpperBound();
        Integer columnIndex = SchemaUtil.COLUMNS_INDEX.get(columnName);
        int i1 = VinDictMap.get(aggregationReq.getVin());
        List<BigBucket> bigBuckets = MapIndex.getBucket(i1, aggregationReq.getTimeLowerBound(), aggregationReq.getTimeUpperBound());
        Context ctx = new Context(0, 0);
        ArrayList<ColumnValue> timeRangeColumnValue = new ArrayList<>();
        int accessFile = 0;
        long readFileCost = 0;
        switch (aggregator) {
            case AVG:
                int size = 0;
                double intSum = 0;
                double doubleSum = 0;
                for (BigBucket bigBucket : bigBuckets) {
                    if (bigBucket.getMinTimestamp() >= aggregationReq.getTimeLowerBound() && bigBucket.getMaxTimestamp() <= aggregationReq.getTimeUpperBound() - 1) {
                        if (columnIndex < Constants.INT_NUMS) {
                            intSum += bigBucket.getiSum(columnIndex);
                            size += bigBucket.getValueSize();
                        } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                            doubleSum += bigBucket.getdSum(columnIndex);
                            size += bigBucket.getValueSize();
                        } else {
                            System.out.println("executeAggregateQuery columnValue string type not support compare");
                        }
                    } else {
                        List<Index> indices = bigBucket.getIndexList();
                        for (Index index : indices) {
                            if (index.getMinTimestamp() >= aggregationReq.getTimeLowerBound() && index.getMaxTimestamp() <= aggregationReq.getTimeUpperBound() - 1) {
                                if (columnIndex < Constants.INT_NUMS) {
                                    intSum += index.getAggBucket().getiSum(columnIndex);
                                    size += index.getValueSize();
                                } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                                    doubleSum += index.getAggBucket().getdSum(columnIndex);
                                    size += index.getValueSize();
                                } else {
                                    System.out.println("executeAggregateQuery columnValue string type not support compare");
                                }
                            } else if (index.getMaxTimestamp() >= aggregationReq.getTimeLowerBound() && index.getMinTimestamp() <= aggregationReq.getTimeLowerBound()) {
                                long start = System.nanoTime();
                                timeRangeColumnValue.addAll(fileService.getSingleValueByIndex(aggregationReq.getVin(), timeLower, timeUpper, index, columnIndex, i1, null, ctx, null, "agg", null));
                                readFileCost += (System.nanoTime() - start);
                                accessFile++;
                            } else if (index.getMinTimestamp() <= aggregationReq.getTimeUpperBound() - 1 && index.getMaxTimestamp() >= aggregationReq.getTimeUpperBound() - 1) {
                                long start = System.nanoTime();
                                timeRangeColumnValue.addAll(fileService.getSingleValueByIndex(aggregationReq.getVin(), timeLower, timeUpper, index, columnIndex, i1, null, ctx, null, "agg", null));
                                readFileCost += (System.nanoTime() - start);
                                accessFile++;
                            }
                        }
                    }
                }
                if (columnIndex < Constants.INT_NUMS) {
                    for (ColumnValue value : timeRangeColumnValue) {
                        intSum += value.getIntegerValue();
                    }
                } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                    for (ColumnValue value : timeRangeColumnValue) {
                        doubleSum += value.getDoubleFloatValue();
                    }
                } else {
                    System.out.println("executeAggregateQuery columnValue string type not support compare");
                }
                size += timeRangeColumnValue.size();
                if (columnIndex < Constants.INT_NUMS) {
                    Map<String, ColumnValue> columns = new HashMap<>(1);
                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(intSum / size));
                    rows.add(new Row(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), columns));
                } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                    Map<String, ColumnValue> columns = new HashMap<>(1);
                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(doubleSum / size));
                    rows.add(new Row(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), columns));
                }
                break;
            case MAX:
                int maxInt = Integer.MIN_VALUE;
                double maxDouble = -Double.MAX_VALUE;
                for (BigBucket bigBucket : bigBuckets) {
                    if (bigBucket.getMinTimestamp() >= aggregationReq.getTimeLowerBound() && bigBucket.getMaxTimestamp() < aggregationReq.getTimeUpperBound() - 1) {
                        if (columnIndex < Constants.INT_NUMS) {
                            maxInt = Math.max(bigBucket.getiMax(columnIndex), maxInt);
                        } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                            maxDouble = Math.max(bigBucket.getdMax(columnIndex), maxDouble);
                        } else {
                            System.out.println("executeAggregateQuery columnValue string type not support compare");
                        }
                    } else {
                        List<Index> indices = bigBucket.getIndexList();
                        for (Index index : indices) {
                            if (index.getMinTimestamp() >= aggregationReq.getTimeLowerBound() && index.getMaxTimestamp() < aggregationReq.getTimeUpperBound() - 1) {
                                if (columnIndex < Constants.INT_NUMS) {
                                    maxInt = Math.max(index.getAggBucket().getiMax(columnIndex), maxInt);
                                } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                                    maxDouble = Math.max(index.getAggBucket().getdMax(columnIndex), maxDouble);
                                } else {
                                    System.out.println("executeAggregateQuery columnValue string type not support compare");
                                }
                            } else if (index.getMaxTimestamp() >= aggregationReq.getTimeLowerBound() && index.getMinTimestamp() <= aggregationReq.getTimeLowerBound()) {
                                long start = System.nanoTime();
                                timeRangeColumnValue.addAll(fileService.getSingleValueByIndex(aggregationReq.getVin(), timeLower, timeUpper, index, columnIndex, i1, null, ctx, null, "agg", null));
                                readFileCost += (System.nanoTime() - start);
                                accessFile++;
                            } else if (index.getMinTimestamp() <= aggregationReq.getTimeUpperBound() - 1 && index.getMaxTimestamp() >= aggregationReq.getTimeUpperBound() - 1) {
                                long start = System.nanoTime();
                                timeRangeColumnValue.addAll(fileService.getSingleValueByIndex(aggregationReq.getVin(), timeLower, timeUpper, index, columnIndex, i1, null, ctx, null, "agg", null));
                                readFileCost += (System.nanoTime() - start);
                                accessFile++;
                            }
                        }
                    }
                }
                if (columnIndex < Constants.INT_NUMS) {
                    for (ColumnValue value : timeRangeColumnValue) {
                        int integerValue = value.getIntegerValue();
                        if (integerValue >= maxInt) {
                            maxInt = integerValue;
                        }
                    }
                } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                    for (ColumnValue value : timeRangeColumnValue) {
                        double doubleFloatValue = value.getDoubleFloatValue();
                        if (doubleFloatValue >= maxDouble) {
                            maxDouble = doubleFloatValue;
                        }
                    }
                } else {
                    System.out.println("executeAggregateQuery columnValue string type not support compare");
                }
                Map<String, ColumnValue> columns = new HashMap<>();
                if (columnIndex < Constants.INT_NUMS) {
                    columns.put(columnName, new ColumnValue.IntegerColumn(maxInt));
                } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(maxDouble));
                }
                if (columns != null) {
                    rows.add(new Row(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), columns));
                } else {
                    System.out.println("columns is null");
                    System.out.println("maxInt: " + maxInt + " maxDouble : " + maxDouble + "timeRangeRow size : " + timeRangeColumnValue.size());
                    for (ColumnValue value : timeRangeColumnValue) {
                        System.out.println(value.getStringValue().toString());
                    }
                }
                break;
            default:
                System.out.println("executeAggregateQuery aggregator error, not support");
                System.exit(-1);
        }
        long gap = System.nanoTime() - start1;
        StaticsUtil.AGG_TOTAL_TIME.getAndAdd(gap);
        StaticsUtil.AGG_TOTAL_READ_FILE_TIME.getAndAdd(readFileCost);
        if (aggQueryTimes.getAndIncrement() % 200000 == 0) {
            StaticsUtil.printCPU();
            System.out.println("aggQueryTimes "+ aggQueryTimes.get() + "total cost " + (gap) + "readFileCost " + readFileCost + "accessFile " + accessFile + "AGG_TOTAL_TIME " + StaticsUtil.AGG_TOTAL_TIME.get() + " ns" + "AGG_TOTAL_READ_FILE_TIME " + StaticsUtil.AGG_TOTAL_READ_FILE_TIME.get() + ctx);
        }
        return rows;
    }

    public ArrayList<Row> getCrossRows(Vin vin, Index index, long startTime, long endTime, Set<String> requestedColumns) {
        long sTime = Math.max(startTime, index.getMinTimestamp());
        long eTime = Math.min(endTime, index.getMaxTimestamp());
        Integer i = VinDictMap.get(vin);
        ArrayList<Row> timeRangeRow = new ArrayList<>(fileService.getByIndexV2(vin, sTime, eTime, index, requestedColumns, i));
        return timeRangeRow;
    }

    public static class CacheData {

        public CacheData(int[] ints, double[] doubles) {
            this.ints = ints;
            this.doubles = doubles;
        }

        public int[] ints;
        public double[] doubles;
    }

    @Override
    public ArrayList<Row> executeDownsampleQuery(TimeRangeDownsampleRequest downsampleReq) throws IOException {
        try {
            long beginTime = System.nanoTime();
            ArrayList<Row> rows = new ArrayList<>();
            final String columnName = downsampleReq.getColumnName();
            final Aggregator aggregator = downsampleReq.getAggregator();
            final long interval = downsampleReq.getInterval();
            final Vin vin = downsampleReq.getVin();
            final long timeLowerBound = downsampleReq.getTimeLowerBound();
            final long timeUpperBound = downsampleReq.getTimeUpperBound();
            final CompareExpression columnFilter = downsampleReq.getColumnFilter();
//            final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(columnName);
            int columnIndex = SchemaUtil.COLUMNS_INDEX.get(columnName);
//            Set<String> requestedColumns = new HashSet<>();
//            requestedColumns.add(columnName);
            int i1 = VinDictMap.get(vin);
            int i = 0;
            Context ctx = new Context(0, 0);
            Map<Long, ByteBuffer> bufferMap = null;
            Map<Long, CacheData> cacheDataMap = null;
            Map<Long, Long> queryTimeMap = null;
            long readFileTime = 0;
            while (timeLowerBound + i * interval < timeUpperBound) {
                Map<String, ColumnValue> columns = new HashMap<>(1);
                ArrayList<ColumnValue> timeRangeRow = new ArrayList<>();
                long startTime = timeLowerBound + i * interval;
                long endTime = timeLowerBound + (i + 1) * interval;
                // [start,end)
                List<Index> indices = MapIndex.get(i1, startTime, endTime);
                if (columnIndex < Constants.INT_NUMS) {
                    if (columnFilter.getCompareOp().equals(CompareExpression.CompareOp.EQUAL)) {
                        // first remove useless index
                        int integerValue = columnFilter.getValue().getIntegerValue();
                        boolean isExist = false;
                        // second
                        switch (aggregator) {
                            case AVG:
                                for (Index index : indices) {
                                    if (index.getAggBucket().getiMax(columnIndex) < integerValue) continue;
                                    if (index.getAggBucket().getiMin(columnIndex) > integerValue) continue;
                                    long readStart = System.nanoTime();
                                    ArrayList<ColumnValue> rowArrayList = fileService.getSingleValueByIndex(vin, startTime, endTime, index, columnIndex, i1, bufferMap, ctx, cacheDataMap, "downSample", queryTimeMap);
                                    long readEnd = System.nanoTime();
                                    readFileTime += readEnd - readStart;
                                    for (ColumnValue value : rowArrayList) {
                                        if (columnFilter.doCompare(value)) {
                                            isExist = true;
                                            break;
                                        }
                                    }
                                    if (isExist) break;
                                }
                                if (!isExist) {
                                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(Double.NEGATIVE_INFINITY));
                                } else {
                                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(integerValue));
                                }
                                break;
                            case MAX:
                                // equal and max
                                for (Index index : indices) {
                                    if (index.getAggBucket().getiMin(columnIndex) > integerValue) continue;
                                    if (index.getAggBucket().getiMax(columnIndex) < integerValue) continue;
                                    long readStart = System.nanoTime();
                                    ArrayList<ColumnValue> rowArrayList = fileService.getSingleValueByIndex(vin, startTime, endTime, index, columnIndex, i1, bufferMap, ctx, cacheDataMap, "downSample", queryTimeMap);
                                    long readEnd = System.nanoTime();
                                    readFileTime += readEnd - readStart;
                                    for (ColumnValue value : rowArrayList) {
                                        if (columnFilter.doCompare(value)) {
                                            isExist = true;
                                            break;
                                        }
                                    }
                                    if (isExist) break;
                                }
                                if (!isExist) {
                                    columns.put(columnName, new ColumnValue.IntegerColumn(0x80000000));
                                } else {
                                    columns.put(columnName, new ColumnValue.IntegerColumn(integerValue));
                                }
                                break;
                            default:
                                System.out.println("error aggregator type");
                        }
                    } else if (columnFilter.getCompareOp().equals(CompareExpression.CompareOp.GREATER)) {
                        int integerValue = columnFilter.getValue().getIntegerValue();
                        switch (aggregator) {
                            case AVG:
                                double sum = 0;
                                int size = 0;
                                for (Index index : indices) {
                                    if (index.getMinTimestamp() >= startTime && index.getMaxTimestamp() <= endTime - 1 && index.getAggBucket().getiMin(columnIndex) > integerValue) {
                                        // interval
                                        sum += index.getAggBucket().getiSum(columnIndex);
                                        size += index.getValueSize();
                                    } else if (index.getMinTimestamp() >= startTime && index.getMaxTimestamp() <= endTime - 1 && index.getAggBucket().getiMax(columnIndex) <= integerValue) {
                                        continue;
                                    } else {
                                        //[start,end) Row
                                        long readStart = System.nanoTime();
                                        timeRangeRow.addAll(fileService.getSingleValueByIndex(vin, startTime, endTime, index, columnIndex, i1, bufferMap, ctx, cacheDataMap, "downSample", queryTimeMap));
                                        long readEnd = System.nanoTime();
                                        readFileTime += readEnd - readStart;
                                    }
                                }
                                for (ColumnValue value : timeRangeRow) {
                                    if (columnFilter.doCompare(value)) {
                                        sum += value.getIntegerValue();
                                        size++;
                                    }
                                }
                                if (size == 0) {
                                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(Double.NEGATIVE_INFINITY));
                                } else {
                                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(sum / size));
                                }
                                break;
                            case MAX:
                                // greater and max
                                int maxInt = Integer.MIN_VALUE;
                                for (Index index : indices) {
                                    if (index.getAggBucket().getiMax(columnIndex) <= integerValue) continue;
                                    if (index.getMinTimestamp() >= startTime && index.getMaxTimestamp() <= endTime - 1) {
                                        maxInt = Math.max(maxInt, index.getAggBucket().getiMax(columnIndex));
                                    } else {
                                        long readStart = System.nanoTime();
                                        timeRangeRow.addAll(fileService.getSingleValueByIndex(vin, startTime, endTime, index, columnIndex, i1, bufferMap, ctx, cacheDataMap, "downSample", queryTimeMap));
                                        long readEnd = System.nanoTime();
                                        readFileTime += readEnd - readStart;
                                    }
                                }
                                for (ColumnValue value : timeRangeRow) {
                                    if (columnFilter.doCompare(value)) {
                                        maxInt = Math.max(maxInt, value.getIntegerValue());
                                    }
                                }
                                if (maxInt == Integer.MIN_VALUE) {
                                    columns.put(columnName, new ColumnValue.IntegerColumn(0x80000000));
                                } else {
                                    columns.put(columnName, new ColumnValue.IntegerColumn(maxInt));
                                }
                                break;
                            default:
                                System.out.println("error aggregator type");
                        }
                    }
                } else if (columnIndex < Constants.INT_NUMS + Constants.FLOAT_NUMS) {
                    if (columnFilter.getCompareOp().equals(CompareExpression.CompareOp.EQUAL)) {
                        // first remove useless index
                        boolean isExist = false;
                        double doubleFloatValue = columnFilter.getValue().getDoubleFloatValue();
                        switch (aggregator) {
                            case AVG:
                                for (Index index : indices) {
                                    if (index.getAggBucket().getdMin(columnIndex) > doubleFloatValue) continue;
                                    if (index.getAggBucket().getdMax(columnIndex) < doubleFloatValue) continue;
                                    long readStart = System.nanoTime();
                                    ArrayList<ColumnValue> rowArrayList = fileService.getSingleValueByIndex(vin, startTime, endTime, index, columnIndex, i1, bufferMap, ctx, cacheDataMap, "downSample", queryTimeMap);
                                    long readEnd = System.nanoTime();
                                    readFileTime += readEnd - readStart;
                                    for (ColumnValue value : rowArrayList) {
                                        if (columnFilter.doCompare(value)) {
                                            isExist = true;
                                            break;
                                        }
                                    }
                                    if (isExist) break;
                                }
                                if (!isExist) {
                                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(Double.NEGATIVE_INFINITY));
                                } else {
                                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(doubleFloatValue));
                                }
                                break;
                            case MAX:
                                // equal and max
                                for (Index index : indices) {
                                    if (index.getMinTimestamp() >= startTime && index.getMaxTimestamp() <= endTime - 1
                                            && !(index.getAggBucket().getiMax(columnIndex) < doubleFloatValue || index.getAggBucket().getiMin(columnIndex) > doubleFloatValue)) {
                                        long readStart = System.nanoTime();
                                        ArrayList<ColumnValue> rowArrayList = fileService.getSingleValueByIndex(vin, startTime, endTime, index, columnIndex, i1, bufferMap, ctx, cacheDataMap, "downSample", queryTimeMap);
                                        long readEnd = System.nanoTime();
                                        readFileTime += readEnd - readStart;
                                        for (ColumnValue value : rowArrayList) {
                                            if (columnFilter.doCompare(value)) {
                                                isExist = true;
                                                break;
                                            }
                                        }
                                        if (isExist) break;
                                    }
                                }
                                if (!isExist) {
                                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(Double.NEGATIVE_INFINITY));
                                } else {
                                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(doubleFloatValue));
                                }
                                break;
                            default:
                                System.out.println("error aggregator type");
                        }
                    } else if (columnFilter.getCompareOp().equals(CompareExpression.CompareOp.GREATER)) {
                        double doubleFloatValue = columnFilter.getValue().getDoubleFloatValue();
                        switch (aggregator) {
                            case AVG:
                                double sum = 0;
                                int size = 0;
                                for (Index index : indices) {
                                    if (index.getMinTimestamp() >= startTime && index.getMaxTimestamp() <= endTime - 1 && index.getAggBucket().getdMin(columnIndex) > doubleFloatValue) {
                                        // interval
                                        sum += index.getAggBucket().getdSum(columnIndex);
                                        size += index.getValueSize();
                                    } else if (index.getMinTimestamp() >= startTime && index.getMaxTimestamp() <= endTime - 1 && index.getAggBucket().getdMax(columnIndex) <= doubleFloatValue) {
                                        continue;
                                    } else {
                                        //[start,end) Row
                                        long readStart = System.nanoTime();
                                        timeRangeRow.addAll(fileService.getSingleValueByIndex(vin, startTime, endTime, index, columnIndex, i1, bufferMap, ctx, cacheDataMap, "downSample", queryTimeMap));
                                        long readEnd = System.nanoTime();
                                        readFileTime += readEnd - readStart;
                                    }
                                }
                                for (ColumnValue value : timeRangeRow) {
                                    if (columnFilter.doCompare(value)) {
                                        sum += value.getDoubleFloatValue();
                                        size++;
                                    }
                                }
                                if (size == 0) {
                                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(Double.NEGATIVE_INFINITY));
                                } else {
                                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(sum / size));
                                }
                                break;
                            case MAX:
                                // greater and max
                                double maxDouble = -Double.MAX_VALUE;
                                for (Index index : indices) {
                                    if (index.getAggBucket().getdMax(columnIndex) <= doubleFloatValue) continue;
                                    if (index.getMinTimestamp() >= startTime && index.getMaxTimestamp() <= endTime - 1) {
                                        maxDouble = Math.max(maxDouble, index.getAggBucket().getdMax(columnIndex));
                                    } else {
                                        long readStart = System.nanoTime();
                                        timeRangeRow.addAll(fileService.getSingleValueByIndex(vin, startTime, endTime, index, columnIndex, i1, bufferMap, ctx, cacheDataMap, "downSample", queryTimeMap));
                                        long readEnd = System.nanoTime();
                                        readFileTime += readEnd - readStart;
                                    }
                                }
                                for (ColumnValue value : timeRangeRow) {
                                    if (columnFilter.doCompare(value)) {
                                        maxDouble = Math.max(maxDouble, value.getDoubleFloatValue());
                                    }
                                }
                                if (maxDouble == -Double.MAX_VALUE) {
                                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(Double.NEGATIVE_INFINITY));
                                } else {
                                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(maxDouble));
                                }
                                break;
                            default:
                                System.out.println("error aggregator type");
                        }
                    }
                } else {
                    System.out.println("Unexpected columnType: ");
                }
                i++;
                rows.add(new Row(vin, startTime, columns));
            }
            long endTime = System.nanoTime();
            long gap = endTime - beginTime;
            StaticsUtil.DOWNSAMPLE_TOTAL_TIME.getAndAdd(gap);
            if (executeDownsampleQueryTimes.getAndIncrement() % 100000 == 0) {
                if (StaticsUtil.START_COUNT_IOPS != 0) {
                    StaticsUtil.START_COUNT_IOPS = System.currentTimeMillis();
                }
//                System.out.println("executeDownSampleQeury " + downsampleReq.getAggregator() +"  filter : " + downsampleReq.getColumnFilter().getCompareOp());
//                System.out.println("executeDownsampleQuery Access File: " + ctx.getAccessTimes());
//                System.out.println("executeDownsampleQuery hit : " + ctx.getHitTimes());
                System.out.println("executeDownsampleQueryTimes" + executeDownsampleQueryTimes.get() + " executeDownsampleQuery useTime : " + (gap) + "ns" + "DOWNSAMPLE_TOTAL_TIME useTime : " + StaticsUtil.DOWNSAMPLE_TOTAL_TIME.get() + " ns" );
                StaticsUtil.printCPU();
                System.out.println("IPOS: " + StaticsUtil.DOWN_SAMPLE_IOPS.getAndIncrement() * 1.0d /(System.currentTimeMillis() - StaticsUtil.START_COUNT_IOPS));

//                System.out.println("executeDownsampleQuery readFile useTime : " + readFileTime);
            }
            return rows;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("executeDownsampleQuery error, " + e);
            System.exit(-1);
        }
        return null;
    }


    public ArrayList<Row> executeDownsampleQueryByBucket(TimeRangeDownsampleRequest downsampleReq) throws IOException {
        ArrayList<Row> rows = new ArrayList<>();
        final String columnName = downsampleReq.getColumnName();
        final Aggregator aggregator = downsampleReq.getAggregator();
        final long interval = downsampleReq.getInterval();
        final Vin vin = downsampleReq.getVin();
        final long timeLowerBound = downsampleReq.getTimeLowerBound();
        final long timeUpperBound = downsampleReq.getTimeUpperBound();
        final CompareExpression columnFilter = downsampleReq.getColumnFilter();
        final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(columnName);
        Set<String> requestedColumns = new HashSet<>();
        requestedColumns.add(columnName);
        final ArrayList<Row> timeRangeRow = memoryTable.getTimeRangeRow(downsampleReq.getVin(), downsampleReq.getTimeLowerBound(), downsampleReq.getTimeUpperBound(), requestedColumns);
        if (timeRangeRow == null || timeRangeRow.isEmpty()) {
            return rows;
        }
        Map<Long, List<ColumnValue>> intMaps = new HashMap<>(timeRangeRow.size());
        Map<Long, List<ColumnValue>> doubleMaps = new HashMap<>(timeRangeRow.size());
        for (Row row : timeRangeRow) {
            final ColumnValue columnValue = row.getColumns().get(columnName);
            final long timestamp = row.getTimestamp();
            if (columnType.equals(COLUMN_TYPE_INTEGER)) {
                long startTime = judgeTimeRange(interval, timestamp, timeLowerBound, timeUpperBound);
                if (intMaps.containsKey(startTime)) {
                    intMaps.get(startTime).add(columnValue);
                } else {
                    List<ColumnValue> lists = new ArrayList<>();
                    lists.add(columnValue);
                    intMaps.put(startTime, lists);
                }
            } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                long startTime = judgeTimeRange(interval, timestamp, timeLowerBound, timeUpperBound);
                if (doubleMaps.containsKey(startTime)) {
                    doubleMaps.get(startTime).add(columnValue);
                } else {
                    List<ColumnValue> lists = new ArrayList<>();
                    lists.add(columnValue);
                    doubleMaps.put(startTime, lists);
                }
            } else {
                System.out.println("executeDownsampleQuery columnValue string type not support compare");
            }
        }
        int i = 0;
        while (timeLowerBound + i * interval < timeUpperBound) {
            Map<String, ColumnValue> columns = new HashMap<>(1);
            if (columnType.equals(COLUMN_TYPE_INTEGER)) {
                if (intMaps.containsKey(timeLowerBound + i * interval)) {
                    final List<ColumnValue> columnValues = intMaps.get(timeLowerBound + i * interval);
                    List<Integer> integers = new ArrayList<>();
                    for (ColumnValue columnValue : columnValues) {
                        if (columnFilter.doCompare(columnValue)) {
                            integers.add(columnValue.getIntegerValue());
                        }
                    }
                    if (aggregator.equals(Aggregator.AVG)) {
                        if (integers.isEmpty()) {
                            columns.put(columnName, new ColumnValue.DoubleFloatColumn(Double.NEGATIVE_INFINITY));
                            rows.add(new Row(vin, timeLowerBound + i * interval, columns));
                            i++;
                            continue;
                        }
                        //integers求和
                        double sum = 0;
                        for (Integer integer : integers) {
                            sum += integer;
                        }
                        columns.put(columnName, new ColumnValue.DoubleFloatColumn(sum / integers.size()));
                        rows.add(new Row(vin, timeLowerBound + i * interval, columns));
                    } else {
                        if (integers.isEmpty()) {
                            columns.put(columnName, new ColumnValue.IntegerColumn(0x80000000));
                            rows.add(new Row(vin, timeLowerBound + i * interval, columns));
                            i++;
                            continue;
                        }
                        //integers求最大
                        int max = Integer.MIN_VALUE;
                        for (int integer : integers) {
                            if (max < integer) {
                                max = integer;
                            }
                        }
                        columns.put(columnName, new ColumnValue.IntegerColumn(max));
                        rows.add(new Row(vin, timeLowerBound + i * interval, columns));
                    }
                }
            } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                if (doubleMaps.containsKey(timeLowerBound + i * interval)) {
                    final List<ColumnValue> columnValues = doubleMaps.get(timeLowerBound + i * interval);
                    List<Double> doubles = new ArrayList<>();
                    for (ColumnValue columnValue : columnValues) {
                        if (columnFilter.doCompare(columnValue)) {
                            doubles.add(columnValue.getDoubleFloatValue());
                        }
                    }
                    //区间内有值，但是都被过滤了返回nan
                    if (doubles.isEmpty()) {
                        columns.put(columnName, new ColumnValue.DoubleFloatColumn(Double.NEGATIVE_INFINITY));
                        rows.add(new Row(vin, timeLowerBound + i * interval, columns));
                        i++;
                        continue;
                    }
                    if (aggregator.equals(Aggregator.AVG)) {
                        //integers求和
                        double sum = 0;
                        for (double integer : doubles) {
                            sum += integer;
                        }
                        columns.put(columnName, new ColumnValue.DoubleFloatColumn(sum / doubles.size()));
                        rows.add(new Row(vin, timeLowerBound + i * interval, columns));
                    } else {
                        //doubles求最大值
                        double max = doubles.get(0);
                        for (double d : doubles) {
                            if (max < d) {
                                max = d;
                            }
                        }
                        columns.put(columnName, new ColumnValue.DoubleFloatColumn(max));
                        rows.add(new Row(vin, timeLowerBound + i * interval, columns));
                    }
                }
            } else {
                System.out.println("executeDownsampleQuery columnValue string type not support compare");
            }
            i++;
        }
        return rows;
    }

    public long judgeTimeRange(long interval, long timestamp, long timeLowerBound, long timeUpperBound) {
        if (timestamp < timeLowerBound || timestamp >= timeUpperBound) {
            return -1;
        }
        int i = 0;
        while (timeLowerBound + i * interval <= timeUpperBound) {
            if (timeLowerBound + i * interval == timestamp) {
                return timeLowerBound + i * interval;
            }
            if (timeLowerBound + i * interval > timestamp) {
                return timeLowerBound + (i - 1) * interval;
            }
            i++;
        }
        return -1;
    }

    public static void main(String[] args) {
        System.out.println(1);
    }
}
