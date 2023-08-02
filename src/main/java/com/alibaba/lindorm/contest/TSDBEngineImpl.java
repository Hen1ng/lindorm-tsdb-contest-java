//
// You should modify this file.
//
// Refer TSDBEngineSample.java to ensure that you have understood
// the interface semantics correctly.
//

package com.alibaba.lindorm.contest;

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

public class TSDBEngineImpl extends TSDBEngine {

    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong upsertTimes;
    private final AtomicLong executeLatestQueryTimes;
    private final AtomicLong executeLatestQueryVinsSize;
    private final AtomicLong executeTimeRangeQueryTimes;
    private final Set<String> writeThreadSet = new HashSet<>();
    private final Set<String> executeLatestQueryThreadSet = new HashSet<>();
    private final Set<String> executeTimeRangeQueryThreadSet = new HashSet<>();
    private TSFileService fileService = null;
    private final MemoryTable memoryTable;
    private Unsafe unsafe = UnsafeUtil.getUnsafe();
    private File indexFile;
    private File vinDictFile;
    private File schemaFile;


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
        try {
            RestartUtil.setFirstStart(indexFile);
            if (!dataPath.exists()) {
                dataPath.createNewFile();
            }
            this.fileService = new TSFileService(dataPath.getPath(), indexFile);
//            if (!RestartUtil.IS_FIRST_START) {
//                executorService = new ThreadPoolExecutor(300, 1000,
//                        0L, TimeUnit.MILLISECONDS,
//                        new LinkedBlockingQueue<Runnable>());
//                for (int i = 0; i < 300; i++) {
//                    executorService.submit(() -> System.out.println("init thread threadName:" + Thread.currentThread().getName()));
//                }
//            } else {
//                executorService = new ThreadPoolExecutor(60, 120,
//                        0L, TimeUnit.MILLISECONDS,
//                        new LinkedBlockingQueue<Runnable>());
//                for (int i = 0; i < 60; i++) {
//                    executorService.submit(() -> System.out.println("init thread threadName:" + Thread.currentThread().getName()));
//                }
//            }
            if (!indexFile.exists()) {
                indexFile.createNewFile();
            }
            if (!vinDictFile.exists()) {
                vinDictFile.createNewFile();
            }
            if (!schemaFile.exists()) {
                schemaFile.createNewFile();
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
        if (!RestartUtil.IS_FIRST_START) {
            memoryTable.loadLastTsToMemory();
        }
        System.gc();
        MemoryUtil.printMemory();
//        executorService.scheduleAtFixedRate(MemoryUtil::printMemory, 10, 1, TimeUnit.SECONDS);
    }

    @Override
    public void createTable(String tableName, Schema schema) throws IOException {
        System.out.println("createTable tableName:" + tableName);
        SchemaUtil.setSchema(schema);
    }

    @Override
    public void shutdown() {
        System.out.println("execute shut down... ts: " + System.currentTimeMillis());
        System.out.println("upsertTimes:" + upsertTimes.get());
        System.out.println("executeTimeRangeQueryTimes: " + executeTimeRangeQueryTimes.get());
        System.out.println("executeLatestQueryTimes: " + executeLatestQueryTimes.get());
        System.out.println("writeThreadSet size: " + writeThreadSet.size());
        for (String threadName : writeThreadSet) {
            System.out.println("threadName: " + threadName);
        }
        System.out.println("executeLatestQueryThreadSet size: " + executeLatestQueryThreadSet.size());
        System.out.println("executeLatestQueryVinsSize query vins size: " + executeLatestQueryVinsSize.get());
        System.out.println("executeTimeRangeQueryThreadSet size: " + executeTimeRangeQueryThreadSet.size());
        System.out.println("total string length:" + StateUtil.STRING_TOTAL_LENGTH.get());
        System.out.println("compress string length:" + StateUtil.STRING_COMPRESS_LENGTH.get());
        try {
            memoryTable.writeToFileBeforeShutdown();
            MapIndex.saveMapToFile(indexFile);
            VinDictMap.saveMapToFile(vinDictFile);
            SchemaUtil.saveMapToFile(schemaFile);
            for (TSFile tsFile : fileService.getTsFiles()) {
                System.out.println("tsFile: " + tsFile.getFileName() + "position: " + tsFile.getPosition().get());
            }
        } catch (Exception e) {
            System.out.println("shutdown error, e" + e);
        }

        GCUtil.printGCInfo();
        MemoryUtil.printMemory();
    }

    @Override
    public void upsert(WriteRequest wReq) throws IOException {
        if (upsertTimes.incrementAndGet() == 1) {
            System.out.println("start upsert, ts:" + System.currentTimeMillis());
        }
        writeThreadSet.add(Thread.currentThread().getName());
        try {
            final Collection<Row> rows = wReq.getRows();
            for (Row row : rows) {
                memoryTable.put(row);
            }
        } catch (Exception e) {
            System.out.println("upsert error, e" + e);
        }
    }

    @Override
    public ArrayList<Row> executeLatestQuery(LatestQueryRequest pReadReq) throws IOException {
        if (executeLatestQueryTimes.incrementAndGet() == 1) {
            System.out.println("executeLatestQuery start, ts:" + System.currentTimeMillis());
        }
        executeLatestQueryThreadSet.add(Thread.currentThread().getName());
        try {
            ArrayList<Row> rows = new ArrayList<>();
//            List<Future<Row>> rowFutureList = new ArrayList<>(pReadReq.getVins().size());
            for (Vin vin : pReadReq.getVins()) {
                final Row lastRow = memoryTable.getLastRow(vin, pReadReq.getRequestedColumns());
                if (lastRow != null) {
                    rows.add(lastRow);
                }
//                final Future<Row> rowFuture = executorService.submit(() -> memoryTable.getLastRow(vin, pReadReq.getRequestedColumns()));
//                rowFutureList.add(rowFuture);
            }
//            for (Future<Row> rowFuture : rowFutureList) {
//                final Row row = rowFuture.get();
//                if (row != null) {
//                    rows.add(row);
//                }
//            }
            executeLatestQueryVinsSize.getAndAdd(pReadReq.getVins().size());
            if (executeLatestQueryTimes.get() % 100000 == 0) {
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
        executeTimeRangeQueryThreadSet.add(Thread.currentThread().getName());
        if (executeTimeRangeQueryTimes.get() % 10000 == 0) {
            MemoryUtil.printJVMHeapMemory();
            System.out.println("executeTimeRangeQuery times :" + executeTimeRangeQueryTimes.get() + " querySize:" + trReadReq.getRequestedFields().size());
        }
        try {
            return memoryTable.getTimeRangeRow(trReadReq.getVin(), trReadReq.getTimeLowerBound(), trReadReq.getTimeUpperBound(), trReadReq.getRequestedFields());
        } catch (Exception e) {
            System.out.println("executeTimeRangeQuery error, e" + e);
        }
        return null;
    }
}
