//
// You should modify this file.
//
// Refer TSDBEngineSample.java to ensure that you have understood
// the interface semantics correctly.
//

package com.alibaba.lindorm.contest;

import com.alibaba.lindorm.contest.compress.DoubleColumnHashMapCompress;
import com.alibaba.lindorm.contest.compress.IntColumnHashMapCompress;
import com.alibaba.lindorm.contest.compress.StringColumnHashMapCompress;
import com.alibaba.lindorm.contest.compress.StringCompress;
import com.alibaba.lindorm.contest.file.DoubleFileService;
import com.alibaba.lindorm.contest.file.FilePosition;
import com.alibaba.lindorm.contest.file.TSFile;
import com.alibaba.lindorm.contest.file.TSFileService;
import com.alibaba.lindorm.contest.index.AggBucket;
import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.index.MapIndex;
import com.alibaba.lindorm.contest.memory.MemoryTable;
import com.alibaba.lindorm.contest.memory.VinDictMap;
import com.alibaba.lindorm.contest.structs.*;
import com.alibaba.lindorm.contest.util.*;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
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
    private File ZstdDictionary;
    private File vinDictFile;
    private File schemaFile;
    private File bigIntFile;
    private File bigIntMapFile;
    private FilePosition filePosition;

    private DoubleFileService doubleFileService;


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
        this.ZstdDictionary = new File(dataPath.getPath()+"/zstdDictionary");
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
        long start = System.currentTimeMillis();
        MapIndex.loadMapFromFileunCompress(indexFile);
//        MapIndex.loadMapFromFile(indexFile);
        VinDictMap.loadMapFromFile(vinDictFile);
        SchemaUtil.loadMapFromFile(schemaFile);
        for (int i = 0; i < 40; i++) {
            StaticsUtil.columnInfos.add(new ColumnInfo());
        }
        if (RestartUtil.IS_FIRST_START) {
            Constants.intColumnHashMapCompress = new IntColumnHashMapCompress(this.dataPath);
            Constants.doubleColumnHashMapCompress = new DoubleColumnHashMapCompress(this.dataPath);
            Constants.stringColumnHashMapCompress = new StringColumnHashMapCompress();
            Constants.intColumnHashMapCompress.prepare();
            Constants.doubleColumnHashMapCompress.prepare();
            Constants.stringColumnHashMapCompress.Prepare();
        } else {
            Constants.intColumnHashMapCompress = IntColumnHashMapCompress.loadFromFile(dataPath.getPath());
            Constants.doubleColumnHashMapCompress = DoubleColumnHashMapCompress.loadFromFile(dataPath.getPath());
            Constants.stringColumnHashMapCompress = StringColumnHashMapCompress.loadFromFile(dataPath.getPath());
            StringCompress.loadDirectoryFromFile(this.ZstdDictionary);
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
        try {
            memoryTable.fixThreadPool.shutdown();
            memoryTable.fixThreadPool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("fixThreadPool completed shutdown");
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
        System.out.println("compress indexFile size: " + indexFile.length());
        System.out.println("idle Buffer size : " + StaticsUtil.MAX_IDLE_BUFFER);
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
                StringCompress.saveDirectory(this.ZstdDictionary);
                final Future<Void> submit1 = executorService1.submit(() -> {
                    Constants.intColumnHashMapCompress.saveToFile(dataPath.getPath());
                    Constants.doubleColumnHashMapCompress.saveToFile(dataPath.getPath());
                    Constants.stringColumnHashMapCompress.saveToFile(dataPath.getPath());
                    return null;
                });
                final Future<Void> submit2 = executorService1.submit(() -> {
//                    MapIndex.saveMapToFile(indexFile);
                    MapIndex.saveMaPToFileCompress(indexFile);
                    return null;
                });
                final Future<Void> submit3 = executorService1.submit(() -> {
                    VinDictMap.saveMapToFile(vinDictFile);
                    return null;
                });
                final Future<Void> submit4 = executorService1.submit(() -> {
                    filePosition.save(fileService.getTsFiles());
                    return null;
                });
                submit1.get();
                submit2.get();
                submit3.get();
                submit4.get();
                submit.get();

            }
            for (TSFile tsFile : fileService.getTsFiles()) {
                System.out.println("tsFile: " + tsFile.getFileName() + "position: " + tsFile.getPosition().get());
            }
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
        if (executeLatestQueryTimes.incrementAndGet() == 1) {
            System.out.println("executeLatestQuery start, ts:" + System.currentTimeMillis());
        }
        try {
            ArrayList<Row> rows = new ArrayList<>();
            for (Vin vin : pReadReq.getVins()) {
                final Row lastRow = memoryTable.getLastRow(vin, pReadReq.getRequestedColumns());
                if (lastRow != null) {
                    rows.add(lastRow);
                }
            }
            executeLatestQueryVinsSize.getAndAdd(pReadReq.getVins().size());
            if (executeLatestQueryTimes.get() % 200000 == 0) {
//                MemoryUtil.printJVMHeapMemory();
                System.out.println("executeLatestQuery query vin size:" + pReadReq.getVins().size());
            }
            return rows;
        } catch (Exception e) {
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
            MemoryUtil.printJVMHeapMemory();
            System.out.println("executeTimeRangeQuery times :" + executeTimeRangeQueryTimes.get() + " querySize:" + trReadReq.getRequestedColumns().size());
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
        return executeAggregateQueryByBucket(aggregationReq);
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

    public ArrayList<Row> executeAggregateQueryByBucket(TimeRangeAggregationRequest aggregationReq) throws IOException {
        ArrayList<Row> rows = new ArrayList<>();
        final String columnName = aggregationReq.getColumnName();
        final Aggregator aggregator = aggregationReq.getAggregator();
        final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(columnName);
        long timeLower = aggregationReq.getTimeLowerBound();
        long timeUpper = aggregationReq.getTimeUpperBound();
        Integer columnIndex = SchemaUtil.COLUMNS_INDEX.get(columnName);
        Set<String> requestedColumns = new HashSet<>();
        requestedColumns.add(columnName);
        int i1 = VinDictMap.get(aggregationReq.getVin());
        List<Index> indices = MapIndex.get(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), aggregationReq.getTimeUpperBound());
        ArrayList<Row> timeRangeRow = new ArrayList<>();
        switch (aggregator) {
            case AVG:
                int size = 0;
                double intSum = 0;
                double doubleSum = 0;
                for (Index index : indices) {
                    if (index.getMinTimestamp() >= aggregationReq.getTimeLowerBound() && index.getMaxTimestamp() <= aggregationReq.getTimeUpperBound() - 1) {
                        if (columnType.equals(COLUMN_TYPE_INTEGER)) {
                            intSum += index.getAggBucket().getiSum(columnIndex);
                            size += index.getValueSize();
                        } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                            doubleSum += index.getAggBucket().getdSum(columnIndex);
                            size += index.getValueSize();
                        } else {
                            System.out.println("executeAggregateQuery columnValue string type not support compare");
                        }
                    } else if (index.getMaxTimestamp() >= aggregationReq.getTimeLowerBound() && index.getMinTimestamp() <= aggregationReq.getTimeLowerBound()) {
                        timeRangeRow.addAll(fileService.getByIndexV2(aggregationReq.getVin(), timeLower, timeUpper, index, requestedColumns, i1));
                    } else if (index.getMinTimestamp() <= aggregationReq.getTimeUpperBound() - 1 && index.getMaxTimestamp() >= aggregationReq.getTimeUpperBound() - 1) {
                        timeRangeRow.addAll(fileService.getByIndexV2(aggregationReq.getVin(), timeLower, timeUpper, index, requestedColumns, i1));
                    }
                }
                if (columnType.equals(COLUMN_TYPE_INTEGER)) {
                    for (Row row : timeRangeRow) {
                        intSum += row.getColumns().get(columnName).getIntegerValue();
                    }
                } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                    for (Row row : timeRangeRow) {
                        doubleSum += row.getColumns().get(columnName).getDoubleFloatValue();
                    }
                } else {
                    System.out.println("executeAggregateQuery columnValue string type not support compare");
                }
                size += timeRangeRow.size();
                if (columnType.equals(COLUMN_TYPE_INTEGER)) {
                    Map<String, ColumnValue> columns = new HashMap<>(1);
                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(intSum / size));
                    rows.add(new Row(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), columns));
                } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                    Map<String, ColumnValue> columns = new HashMap<>(1);
                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(doubleSum / size));
                    rows.add(new Row(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), columns));
                }
                break;
            case MAX:
                int maxInt = Integer.MIN_VALUE;
                double maxDouble = -Double.MAX_VALUE;
                for (Index index : indices) {
                    if (index.getMinTimestamp() >= aggregationReq.getTimeLowerBound() && index.getMaxTimestamp() < aggregationReq.getTimeUpperBound() - 1) {
                        if (columnType.equals(COLUMN_TYPE_INTEGER)) {
                            maxInt = Math.max(index.getAggBucket().getiMax(columnIndex), maxInt);
                        } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                            maxDouble = Math.max(index.getAggBucket().getdMax(columnIndex), maxDouble);
                        } else {
                            System.out.println("executeAggregateQuery columnValue string type not support compare");
                        }
                    } else if (index.getMaxTimestamp() >= aggregationReq.getTimeLowerBound() && index.getMinTimestamp() <= aggregationReq.getTimeLowerBound()) {
                        timeRangeRow.addAll(fileService.getByIndexV2(aggregationReq.getVin(), timeLower, timeUpper, index, requestedColumns, i1));
                    } else if (index.getMinTimestamp() <= aggregationReq.getTimeUpperBound() - 1 && index.getMaxTimestamp() >= aggregationReq.getTimeUpperBound() - 1) {
                        timeRangeRow.addAll(fileService.getByIndexV2(aggregationReq.getVin(), timeLower, timeUpper, index, requestedColumns, i1));
                    }
                }
                if (columnType.equals(COLUMN_TYPE_INTEGER)) {
                    for (Row row : timeRangeRow) {
                        final ColumnValue columnValue = row.getColumns().get(columnName);
                        int integerValue = columnValue.getIntegerValue();
                        if (integerValue >= maxInt) {
                            maxInt = integerValue;
                        }
                    }
                } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                    for (Row row : timeRangeRow) {
                        final ColumnValue columnValue = row.getColumns().get(columnName);
                        double doubleFloatValue = columnValue.getDoubleFloatValue();
                        if (doubleFloatValue >= maxDouble) {
                            maxDouble = doubleFloatValue;
                        }
                    }
                } else {
                    System.out.println("executeAggregateQuery columnValue string type not support compare");
                }
                Map<String, ColumnValue> columns = new HashMap<>();
                if (columnType.equals(COLUMN_TYPE_INTEGER)) {
                    columns.put(columnName, new ColumnValue.IntegerColumn(maxInt));
                } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(maxDouble));
                }
                if (columns != null) {
                    rows.add(new Row(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), columns));
                } else {
                    System.out.println("columns is null");
                    System.out.println("maxInt: " + maxInt + " maxDouble : " + maxDouble + "timeRangeRow size : " + timeRangeRow.size());
                    for (Row row : timeRangeRow) {
                        System.out.println(row.toString());
                    }
                }
                break;
            default:
                System.out.println("executeAggregateQuery aggregator error, not support");
                System.exit(-1);
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

    @Override
    public ArrayList<Row> executeDownsampleQuery(TimeRangeDownsampleRequest downsampleReq) throws IOException {
        if (executeDownsampleQueryTimes.getAndIncrement() % 100000 == 0) {
            System.out.println("executeDownsampleQuery interval: " + downsampleReq.getInterval());
        }
        ArrayList<Row> rows = new ArrayList<>();
        final String columnName = downsampleReq.getColumnName();
        final Aggregator aggregator = downsampleReq.getAggregator();
        final long interval = downsampleReq.getInterval();
        final Vin vin = downsampleReq.getVin();
        final long timeLowerBound = downsampleReq.getTimeLowerBound();
        final long timeUpperBound = downsampleReq.getTimeUpperBound();
        final CompareExpression columnFilter = downsampleReq.getColumnFilter();
        final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(columnName);
        int columnIndex = SchemaUtil.COLUMNS_INDEX.get(columnName);
        Set<String> requestedColumns = new HashSet<>();
        requestedColumns.add(columnName);
        Map<Long, ArrayList<Row>> buffer = new HashMap<>();
        int i1 = VinDictMap.get(vin);
        int i = 0;
        while (timeLowerBound + i * interval < timeUpperBound) {
            Map<String, ColumnValue> columns = new HashMap<>(1);
            ArrayList<Row> timeRangeRow = new ArrayList<>();
            ArrayList<Row> timeRangeRow2 = new ArrayList<>();
            long startTime = timeLowerBound + i * interval;
            long endTime = timeLowerBound + (i + 1) * interval;
            // [start,end)
            List<Index> indices = MapIndex.get(vin, startTime, endTime);
            if (columnType.equals(COLUMN_TYPE_INTEGER)) {
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
                                ArrayList<Row> rowArrayList = null;
                                if (buffer.containsKey(index.getOffset())) {
                                    rowArrayList = buffer.get(index.getOffset());
                                } else {
                                    rowArrayList = fileService.getByIndexV2(vin, timeLowerBound, timeUpperBound, index, requestedColumns, i1);
                                    buffer.put(index.getOffset(), rowArrayList);
                                }
                                for (Row row : rowArrayList) {
                                    if (row.getTimestamp() < startTime || row.getTimestamp() >= endTime) continue;
                                    if (columnFilter.doCompare(row.getColumns().get(columnName))) {
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
                                ArrayList<Row> rowArrayList;
                                if (buffer.containsKey(index.getOffset())) {
                                    rowArrayList = buffer.get(index.getOffset());
                                } else {
                                    rowArrayList = fileService.getByIndexV2(vin, timeLowerBound, timeUpperBound, index, requestedColumns, i1);
                                    buffer.put(index.getOffset(), rowArrayList);
                                }
                                for (Row row : rowArrayList) {
                                    if (row.getTimestamp() < startTime || row.getTimestamp() >= endTime) continue;
                                    if (columnFilter.doCompare(row.getColumns().get(columnName))) {
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
                                    if (buffer.containsKey(index.getOffset())) {
                                        timeRangeRow.addAll(buffer.get(index.getOffset()));
                                    } else {
                                        ArrayList<Row> rowArrayList = fileService.getByIndexV2(vin, timeLowerBound, timeUpperBound, index, requestedColumns, i1);
                                        timeRangeRow.addAll(rowArrayList);
                                        buffer.put(index.getOffset(), rowArrayList);
                                    }
                                }
                            }
                            for (Row row : timeRangeRow) {
                                if (row.getTimestamp() < startTime || row.getTimestamp() > endTime - 1) {
                                    continue;
                                }
                                if (columnFilter.doCompare(row.getColumns().get(columnName))) {
                                    sum += row.getColumns().get(columnName).getIntegerValue();
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
                                    if (!buffer.containsKey(index.getOffset())) {
                                        ArrayList<Row> rowArrayList = fileService.getByIndexV2(vin, timeLowerBound, timeUpperBound, index, requestedColumns, i1);
                                        timeRangeRow.addAll(rowArrayList);
                                        buffer.put(index.getOffset(), rowArrayList);
                                    } else {
                                        timeRangeRow.addAll(buffer.get(index.getOffset()));
                                    }
                                }
                            }
                            for (Row row : timeRangeRow) {
                                if (row.getTimestamp() < startTime || row.getTimestamp() >= endTime) continue;
                                if (columnFilter.doCompare(row.getColumns().get(columnName))) {
                                    maxInt = Math.max(maxInt, row.getColumns().get(columnName).getIntegerValue());
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
            } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                if (columnFilter.getCompareOp().equals(CompareExpression.CompareOp.EQUAL)) {
                    // first remove useless index
                    boolean isExist = false;
                    double doubleFloatValue = columnFilter.getValue().getDoubleFloatValue();
                    switch (aggregator) {
                        case AVG:
                            for (Index index : indices) {
                                if (index.getAggBucket().getdMin(columnIndex) > doubleFloatValue) continue;
                                if (index.getAggBucket().getdMax(columnIndex) < doubleFloatValue) continue;
                                ArrayList<Row> rowArrayList;
                                if (buffer.containsKey(index.getOffset())) {
                                    rowArrayList = buffer.get(index.getOffset());
                                } else {
                                    rowArrayList = fileService.getByIndexV2(vin, timeLowerBound, timeUpperBound, index, requestedColumns, i1);
                                    buffer.put(index.getOffset(), rowArrayList);
                                }
                                for (Row row : rowArrayList) {
                                    if (row.getTimestamp() < startTime || row.getTimestamp() >= endTime) continue;
                                    if (columnFilter.doCompare(row.getColumns().get(columnName))) {
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
                                    ArrayList<Row> rowArrayList;
                                    if (!buffer.containsKey(index.getOffset())) {
                                        rowArrayList = fileService.getByIndexV2(vin, timeLowerBound, timeUpperBound, index, requestedColumns, i1);
                                        buffer.put(index.getOffset(), rowArrayList);
                                    } else {
                                        rowArrayList = buffer.get(index.getOffset());
                                    }
                                    for (Row row : rowArrayList) {
                                        if (row.getTimestamp() < startTime || row.getTimestamp() >= endTime) continue;
                                        if (columnFilter.doCompare(row.getColumns().get(columnName))) {
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
                                    if (!buffer.containsKey(index.getOffset())) {
                                        ArrayList<Row> rowArrayList = fileService.getByIndexV2(vin, timeLowerBound, timeUpperBound, index, requestedColumns, i1);
                                        timeRangeRow.addAll(rowArrayList);
                                        buffer.put(index.getOffset(), rowArrayList);
                                    } else {
                                        timeRangeRow.addAll(buffer.get(index.getOffset()));
                                    }
                                }
                            }
                            for (Row row : timeRangeRow) {
                                if (row.getTimestamp() < startTime || row.getTimestamp() >= endTime) {
                                    continue;
                                }
                                if (columnFilter.doCompare(row.getColumns().get(columnName))) {
                                    sum += row.getColumns().get(columnName).getDoubleFloatValue();
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
                                    if (!buffer.containsKey(index.getOffset())) {
                                        ArrayList<Row> rowArrayList = fileService.getByIndexV2(vin, timeLowerBound, timeUpperBound, index, requestedColumns, i1);
                                        timeRangeRow.addAll(rowArrayList);
                                        buffer.put(index.getOffset(), rowArrayList);
                                    } else {
                                        timeRangeRow.addAll(buffer.get(index.getOffset()));
                                    }
                                }
                            }
                            for (Row row : timeRangeRow) {
                                if (row.getTimestamp() < startTime || row.getTimestamp() >= endTime) {
                                    continue;
                                }
                                if (columnFilter.doCompare(row.getColumns().get(columnName))) {
                                    maxDouble = Math.max(maxDouble, row.getColumns().get(columnName).getDoubleFloatValue());
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
                System.out.println("Unexpected columnType: " + columnType);
            }
            i++;
            rows.add(new Row(vin, startTime, columns));
        }
        return rows;
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
