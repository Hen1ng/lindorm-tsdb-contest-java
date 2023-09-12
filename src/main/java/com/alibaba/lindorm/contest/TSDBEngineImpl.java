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
import com.alibaba.lindorm.contest.file.FilePosition;
import com.alibaba.lindorm.contest.file.TSFile;
import com.alibaba.lindorm.contest.file.TSFileService;
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
            this.fileService = new TSFileService(dataPath.getPath(), indexFile);
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
    }

    @Override
    public void connect() throws IOException {
        System.out.println("connect start...");
        MapIndex.loadMapFromFile(indexFile);
        VinDictMap.loadMapFromFile(vinDictFile);
        SchemaUtil.loadMapFromFile(schemaFile);
        if (RestartUtil.IS_FIRST_START) {
            Constants.intColumnHashMapCompress = new IntColumnHashMapCompress(this.dataPath);
            Constants.doubleColumnHashMapCompress = new DoubleColumnHashMapCompress(this.dataPath);
            Constants.stringColumnHashMapCompress = new StringColumnHashMapCompress();
//            Constants.intColumnHashMapCompress.addColumns("LATITUDE", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("LONGITUDE", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("YXMS", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("LJLC", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("DCDC", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("RLDCDY", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("DCDTDYZGZ", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("RLDCRLXHL", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("QQZGYLCGQDH", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("RLDCDL", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("QQZGND", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("CDZT", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("QDDJGZZS", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("ZDDYDCZXTDH", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("QQZGNDCGQDH", 3600 * 30000);
//            Constants.intColumnHashMapCompress.addColumns("QQZGYL", 3600 * 30000);
            //double
//            Constants.doubleColumnHashMapCompress.addColumns("QDDJKZWD", 3600 * 30000);
//            Constants.doubleColumnHashMapCompress.addColumns("QDDJWD", 3600 * 30000);
//            Constants.doubleColumnHashMapCompress.addColumns("QDDJXH", 3600 * 30000);
//            Constants.doubleColumnHashMapCompress.addColumns("QDDJZJ", 3600 * 30000);
//            Constants.doubleColumnHashMapCompress.addColumns("QDDJZS", 3600 * 30000);
//            Constants.doubleColumnHashMapCompress.addColumns("QDDJGS", 3600 * 30000);
//            Constants.doubleColumnHashMapCompress.addColumns("JYDZ", 3600 * 30000);
//            Constants.doubleColumnHashMapCompress.addColumns("DJKZQDY", 3600 * 30000);
//            Constants.doubleColumnHashMapCompress.addColumns("DJKZQDL", 3600 * 30000);
            Constants.intColumnHashMapCompress.prepare();
            Constants.doubleColumnHashMapCompress.prepare();
            Constants.stringColumnHashMapCompress.Prepare();
        } else {
            Constants.intColumnHashMapCompress = IntColumnHashMapCompress.loadFromFile(dataPath.getPath());
            Constants.doubleColumnHashMapCompress = DoubleColumnHashMapCompress.loadFromFile(dataPath.getPath());
            Constants.stringColumnHashMapCompress = StringColumnHashMapCompress.loadFromFile(dataPath.getPath());
            memoryTable.loadLastTsToMemory();
        }
        System.gc();
        MemoryUtil.printMemory();
    }

    @Override
    public void createTable(String tableName, Schema schema) throws IOException {
        System.out.println("createTable tableName:" + tableName);
        SchemaUtil.setSchema(schema);
    }

    @Override
    public void shutdown() {
        try {
            memoryTable.fixThreadPool.shutdown();
            memoryTable.fixThreadPool.awaitTermination(60, TimeUnit.SECONDS);
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
        System.out.println("compress double rate: " + StaticsUtil.DOUBLE_COMPRESS_LENGTH.get() * 1.0d / (30000L * 3600L * 9L * 8L));
        System.out.println("compress long length: " + StaticsUtil.LONG_COMPRESS_LENGTH.get());
        System.out.println("compress long rate: " + (StaticsUtil.LONG_COMPRESS_LENGTH.get() * 1.0d) / (30000L * 3600L * 8L));
        System.out.println("compress int length: " + StaticsUtil.INT_COMPRESS_LENGTH.get());
        System.out.println("compress int rate: " + (StaticsUtil.INT_COMPRESS_LENGTH.get() * 1.0d) / (30000 * 3600L * 45L * 4L));
        System.out.println("indexFile size: " + indexFile.length());
        System.out.println("idle Buffer size : " + StaticsUtil.MAX_IDLE_BUFFER);
        for (String s : SchemaUtil.maps.keySet()) {
            System.out.println("key: " + s + "size " + SchemaUtil.maps.get(s).size());
        }
        try {
            MapIndex.saveMapToFile(indexFile);
            VinDictMap.saveMapToFile(vinDictFile);
            SchemaUtil.saveMapToFile(schemaFile);
            Constants.intColumnHashMapCompress.saveToFile(dataPath.getPath());
            Constants.doubleColumnHashMapCompress.saveToFile(dataPath.getPath());
            Constants.stringColumnHashMapCompress.saveToFile(dataPath.getPath());
            for (TSFile tsFile : fileService.getTsFiles()) {
                System.out.println("tsFile: " + tsFile.getFileName() + "position: " + tsFile.getPosition().get());
            }
            if (RestartUtil.IS_FIRST_START) {
                memoryTable.writeToFileBeforeShutdown();
                filePosition.save(fileService.getTsFiles());
            }
        } catch (Exception e) {
            System.out.println("shutdown error, e" + e);
        }

        GCUtil.printGCInfo();
        MemoryUtil.printMemory();
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
//        executeLatestQueryThreadSet.add(Thread.currentThread().getName());
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
                MemoryUtil.printJVMHeapMemory();
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
//        executeTimeRangeQueryThreadSet.add(Thread.currentThread().getName());
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
        ArrayList<Row> rows = new ArrayList<>();
        final String columnName = aggregationReq.getColumnName();
        final Aggregator aggregator = aggregationReq.getAggregator();
        final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(columnName);
        Set<String> requestedColumns = new HashSet<>();
        requestedColumns.add(columnName);
        final ArrayList<Row> timeRangeRow = memoryTable.getTimeRangeRow(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), aggregationReq.getTimeUpperBound(), requestedColumns);
        switch (aggregator) {
            case AVG:
                double intSum = 0;
                double doubleSum = 0;
                for (Row row : timeRangeRow) {
                    final ColumnValue columnValue = row.getColumns().get(columnName);
                    if (columnType.equals(COLUMN_TYPE_INTEGER)) {
                        intSum += columnValue.getIntegerValue();
                    } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                        doubleSum += columnValue.getDoubleFloatValue();
                    } else {
                        System.out.println("executeAggregateQuery columnValue string type not support compare");
                    }
                }
                if (columnType.equals(COLUMN_TYPE_INTEGER)) {
                    Map<String, ColumnValue> columns = new HashMap<>(1);
                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(intSum / timeRangeRow.size()));
                    rows.add(new Row(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), columns));
                } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                    Map<String, ColumnValue> columns = new HashMap<>(1);
                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(doubleSum / timeRangeRow.size()));
                    rows.add(new Row(aggregationReq.getVin(), aggregationReq.getTimeLowerBound(), columns));
                }
                break;
            case MAX:
                int maxInt = Integer.MIN_VALUE;
                double maxDouble = Double.MIN_VALUE;
                long timestamp = -1L;
                Map<String, ColumnValue> columns = null;
                for (Row row : timeRangeRow) {
                    final ColumnValue columnValue = row.getColumns().get(columnName);
                    if (columnType.equals(COLUMN_TYPE_INTEGER)) {
                        int integerValue = columnValue.getIntegerValue();
                        if (integerValue > maxInt) {
                            columns = row.getColumns();
                            maxInt = integerValue;
                            timestamp = row.getTimestamp();
                        }
                    } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                        double doubleFloatValue = columnValue.getDoubleFloatValue();
                        if (doubleFloatValue > maxDouble) {
                            maxDouble = doubleFloatValue;
                            columns = row.getColumns();
                            timestamp = row.getTimestamp();
                        }
                    } else {
                        System.out.println("executeAggregateQuery columnValue string type not support compare");
                    }
                }
                if (columns != null) {
                    rows.add(new Row(aggregationReq.getVin(), timestamp, columns));
                }
                break;
            default:
                System.out.println("executeAggregateQuery aggregator error, not support");
                System.exit(-1);
        }
        return rows;
    }

    @Override
    public ArrayList<Row> executeDownsampleQuery(TimeRangeDownsampleRequest downsampleReq) throws IOException {
        ArrayList<Row> rows = new ArrayList<>();
        final String columnName = downsampleReq.getColumnName();
        final Aggregator aggregator = downsampleReq.getAggregator();
        final ColumnValue value = downsampleReq.getColumnFilter().getValue();
        final long interval = downsampleReq.getInterval();
        final Vin vin = downsampleReq.getVin();
        final long timeLowerBound = downsampleReq.getTimeLowerBound();
        final long timeUpperBound = downsampleReq.getTimeUpperBound();
        final CompareExpression columnFilter = downsampleReq.getColumnFilter();
        final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(columnName);
        Set<String> requestedColumns = new HashSet<>();
        requestedColumns.add(columnName);
        Map<Long, List<Integer>> intMaps = new HashMap<>();
        Map<Long, List<Double>> doubleMaps = new HashMap<>();
        final ArrayList<Row> timeRangeRow = memoryTable.getTimeRangeRow(downsampleReq.getVin(), downsampleReq.getTimeLowerBound(), downsampleReq.getTimeUpperBound(), requestedColumns);
        for (Row row : timeRangeRow) {
            final ColumnValue columnValue = row.getColumns().get(columnName);
            final long timestamp = row.getTimestamp();
            if (columnType.equals(COLUMN_TYPE_INTEGER)) {
                final int integerValue = columnValue.getIntegerValue();
                if (CompareExpression.CompareOp.EQUAL.equals(columnFilter.getCompareOp())) {
                    if (integerValue == value.getIntegerValue()) {
                        final long startTime = judgeTimeRange(interval, timestamp, timeLowerBound, timeUpperBound);
                        if (intMaps.containsKey(startTime)) {
                            intMaps.get(startTime).add(integerValue);
                        } else {
                            List<Integer> lists = new ArrayList<>();
                            lists.add(integerValue);
                            intMaps.put(startTime, lists);
                        }
                    }
                } else {
                    if (integerValue > value.getIntegerValue()) {
                        final long startTime = judgeTimeRange(interval, timestamp, timeLowerBound, timeUpperBound);
                        if (intMaps.containsKey(startTime)) {
                            intMaps.get(startTime).add(integerValue);
                        } else {
                            List<Integer> lists = new ArrayList<>();
                            lists.add(integerValue);
                            intMaps.put(startTime, lists);
                        }
                    }
                }
            } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                final double doubleFloatValue = columnValue.getDoubleFloatValue();
                if (CompareExpression.CompareOp.EQUAL.equals(columnFilter.getCompareOp())) {
                    if (doubleFloatValue == value.getDoubleFloatValue()) {
                        final long startTime = judgeTimeRange(interval, timestamp, timeLowerBound, timeUpperBound);
                        if (doubleMaps.containsKey(startTime)) {
                            doubleMaps.get(startTime).add(doubleFloatValue);
                        } else {
                            List<Double> lists = new ArrayList<>();
                            lists.add(doubleFloatValue);
                            doubleMaps.put(startTime, lists);
                        }
                    }
                } else {
                    if (doubleFloatValue > value.getDoubleFloatValue()) {
                        final long startTime = judgeTimeRange(interval, timestamp, timeLowerBound, timeUpperBound);
                        if (doubleMaps.containsKey(startTime)) {
                            doubleMaps.get(startTime).add(doubleFloatValue);
                        } else {
                            List<Double> lists = new ArrayList<>();
                            lists.add(doubleFloatValue);
                            doubleMaps.put(startTime, lists);
                        }
                    }
                }
            } else {
                System.out.println("executeDownsampleQuery columnValue string type not support compare");
            }
        }
        int i = 0;
        while (timeLowerBound + i * interval < timeUpperBound) {
            Map<String, ColumnValue> columns = new HashMap<>(1);
            if (columnType.equals(COLUMN_TYPE_INTEGER)) {
                final List<Integer> integers = intMaps.get(timeLowerBound + i * interval);
                if (intMaps.containsKey(timeLowerBound + i * interval)) {
                    if (aggregator.equals(Aggregator.AVG)) {
                        //integers求和
                        if (!integers.isEmpty()) {
                            int sum = 0;
                            for (Integer integer : integers) {
                                sum += integer;
                            }
                            columns.put(columnName, new ColumnValue.DoubleFloatColumn((double) sum / integers.size()));
                            rows.add(new Row(vin, timeLowerBound + i * interval, columns));
                        }
                    } else {
                        //integers求最大
                        if (!integers.isEmpty()) {
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
                } else {
                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(Double.NEGATIVE_INFINITY));
                    rows.add(new Row(vin, timeLowerBound + i * interval, columns));
                }
            } else if (columnType.equals(COLUMN_TYPE_DOUBLE_FLOAT)) {
                if (doubleMaps.containsKey(timeLowerBound + i * interval)) {
                    final List<Double> doubles = doubleMaps.get(timeLowerBound + i * interval);
                    if (aggregator.equals(Aggregator.AVG)) {
                        //integers求和
                        if (!doubles.isEmpty()) {
                            double sum = 0;
                            for (double integer : doubles) {
                                sum += integer;
                            }
                            columns.put(columnName, new ColumnValue.DoubleFloatColumn((double) sum / doubles.size()));
                            rows.add(new Row(vin, timeLowerBound + i * interval, columns));
                        }
                    } else {
                        //doubles求最大值
                        if (!doubles.isEmpty()) {
                            double max = Double.MIN_VALUE;
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
                    columns.put(columnName, new ColumnValue.DoubleFloatColumn(Double.NEGATIVE_INFINITY));
                    rows.add(new Row(vin, timeLowerBound + i * interval, columns));
                }
            } else {
                System.out.println("executeDownsampleQuery columnValue string type not support compare");
            }
            i++;
        }
        return rows;
    }

    public long judgeTimeRange(long interval, long timestamp, long timeLowerBound, long timeUpperBound) {
        int i = 0;
        while (timeLowerBound + i * interval < timeUpperBound) {
            if (timeLowerBound + i * interval > timestamp) {
                return timeLowerBound + i * interval;
            }
            i++;
        }
        return -1;
    }

    public static void main(String[] args) {
        System.out.println(1);
    }
}
