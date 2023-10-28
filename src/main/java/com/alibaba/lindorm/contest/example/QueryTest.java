package com.alibaba.lindorm.contest.example;

import com.alibaba.lindorm.contest.TSDBEngineImpl;
import com.alibaba.lindorm.contest.memory.VinDictMap;
import com.alibaba.lindorm.contest.structs.*;
import com.alibaba.lindorm.contest.util.BytesUtil;
import com.alibaba.lindorm.contest.util.SchemaUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class QueryTest {
    static int threadNum = 16;
    static ExecutorService executorService = Executors.newFixedThreadPool(15);
    static AtomicLong writeTimes = new AtomicLong(0);
    static CountDownLatch countDownLatch = new CountDownLatch(threadNum);
    public static void main(String[] args) {
        File dataDir = new File("./data_dir");
        if (dataDir.isFile()) {
            throw new IllegalStateException("Clean the directory before we start the demo");
        }
        if (!dataDir.isDirectory()) {
            boolean ret = dataDir.mkdirs();
            if (!ret) {
                throw new IllegalStateException("Cannot create the temp data directory: " + dataDir);
            }
        }
        Vin[] vins = new Vin[30000];
        for (int i = 0; i < 30000; i++) {
            String vin = BytesUtil.getRandomString(17);
            vins[i] = new Vin(vin.getBytes(StandardCharsets.UTF_8));
        }
        TSDBEngineImpl tsdbEngineSample = new TSDBEngineImpl(dataDir);
        String[] bigIntKey = {"LATITUDE","LONGITUDE"};
        Random random = new Random();
        AtomicLong maxTime = new AtomicLong(-1);
        try {
            Map<String, ColumnValue> columns = new HashMap<>();
            Map<String, ColumnValue.ColumnType> columnTypeMap = new HashMap<>();

            for (int i = 0; i < 40; i++) {
                String key = String.valueOf(i);
                columnTypeMap.put(key, ColumnValue.ColumnType.COLUMN_TYPE_INTEGER);
                columns.put(key, new ColumnValue.IntegerColumn(i));
            }
//            for (int i = 0; i < 2; i++) {
//                String key = bigIntKey[i];
//                columnTypeMap.put(key, ColumnValue.ColumnType.COLUMN_TYPE_INTEGER);
//                columns.put(key, new ColumnValue.IntegerColumn(random.nextInt()));
//            }
            for (int i = 0; i < 10; i++) {
                String key = i + "double";
                columnTypeMap.put(key, ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT);
                columns.put(key, new ColumnValue.DoubleFloatColumn(i / 10d));
            }
            for (int i = 0; i < 10; i++) {
                final StringBuilder sb = new StringBuilder();
                int j = i;
                while (j >= 0) {
                    sb.append(j);
                    j--;
                }
                String key = i + "String" + sb;
                if (i == 3) {
                    key = "JUBK";
                    String s = "";
                    for (int i1 = 0; i1 < 100; i1++) {
                        s+="x";
                    }
                    ByteBuffer buffer = ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
                    columns.put(key, new ColumnValue.StringColumn(buffer));
                } else if (i == 5) {
                    key = "ORNI";
                    String s = "";
                    for (int i1 = 0; i1 < 30; i1++) {
                        s+="s";
                    }
                    ByteBuffer buffer = ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
                    columns.put(key, new ColumnValue.StringColumn(buffer));
                } else {
                    ByteBuffer buffer = ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8));
                    columns.put(key, new ColumnValue.StringColumn(buffer));
                }
                columnTypeMap.put(key, ColumnValue.ColumnType.COLUMN_TYPE_STRING);

            }
            boolean write = true;
            if (write) {
                Schema schema = new Schema(columnTypeMap);
                tsdbEngineSample.connect();
                tsdbEngineSample.createTable("haha", schema);
                String v1 = BytesUtil.getRandomString(17);
                System.out.println("V1 " + v1);
                AtomicLong atomicLong = new AtomicLong(0);
                long start = System.currentTimeMillis();
                for (int i = 0; i < threadNum; i++) {
                    new Thread(() -> {
                        for (int j = 0; j < 10000; j++) {
                            List<Row> rowList = new ArrayList<>();
                            for (int i1 = 0; i1 < 60; i1++) {
                                Vin vin = vins[random.nextInt(5000)];
                                final long andIncrement = atomicLong.getAndIncrement() * 1000;
                                maxTime.set(andIncrement);
                                Map<String, ColumnValue> columns1 = new HashMap<>();
                                for (String s : columns.keySet()) {
                                    columns1.put(s, columns.get(s));
                                }
                                rowList.add(new Row(vin, andIncrement, columns1));
                            }
                            try {
                                tsdbEngineSample.write(new WriteRequest("test", rowList));
//
                            } catch (Exception e) {

                            }
                        }
                        countDownLatch.countDown();

                    }).start();
                }

                countDownLatch.await();
                System.out.println("cost:" + (System.currentTimeMillis() - start) + " ms");

                tsdbEngineSample.shutdown();
            } else {
                boolean benchmarkDownSample = true;
                if (!benchmarkDownSample) {
                    String v = "lEthBFkW0jIFOqzT3";
                    tsdbEngineSample.connect();
                    List<Vin> list = new ArrayList<>();
                    list.add(new Vin(v.getBytes(StandardCharsets.UTF_8)));
                    Set<String> requestedColumns = new HashSet<>();
                    requestedColumns.add("5String543210");
                    requestedColumns.add("JUBK");
                    requestedColumns.add("0String0");
                    requestedColumns.add("0double");
                    requestedColumns.add("7double");
                    requestedColumns.add("1");
                    requestedColumns.add("2");
                    requestedColumns.add("33");
                    requestedColumns.add("9double");

                    final LatestQueryRequest latestQueryRequest = new LatestQueryRequest("", list, requestedColumns);
                    final ArrayList<Row> rows = tsdbEngineSample.executeLatestQuery(latestQueryRequest);
                    final TimeRangeQueryRequest timeRangeQueryRequest = new TimeRangeQueryRequest("", new Vin(v.getBytes(StandardCharsets.UTF_8)), requestedColumns, 0, Long.MAX_VALUE);
                    ArrayList<Row> rowArrayList = tsdbEngineSample.executeTimeRangeQuery(timeRangeQueryRequest);
                    final TimeRangeAggregationRequest timeRangeAggregationRequest = new TimeRangeAggregationRequest("", new Vin(v.getBytes(StandardCharsets.UTF_8)), "7double", 0, Long.MAX_VALUE, Aggregator.MAX);
                    rowArrayList = tsdbEngineSample.executeAggregateQuery(timeRangeAggregationRequest);
                    final TimeRangeDownsampleRequest timeRangeDownsampleRequest = new TimeRangeDownsampleRequest("", new Vin(v.getBytes(StandardCharsets.UTF_8)), "7double", 9120000L, 10923000L, Aggregator.AVG, 1000L, new CompareExpression(new ColumnValue.DoubleFloatColumn(0.7d), CompareExpression.CompareOp.EQUAL));
                    rowArrayList = tsdbEngineSample.executeDownsampleQuery(timeRangeDownsampleRequest);
                    System.out.println(1);
                    tsdbEngineSample.shutdown();
                } else {
                    tsdbEngineSample.connect();
                    final Set<Vin> vins1 = VinDictMap.getVinDictMap().keySet();
                    final ArrayList<Vin> vins2 = new ArrayList<>(vins1);
                    final int size = vins1.size();
                    int randomVinIndex = random.nextInt(size);
                    final String[] indexArray = SchemaUtil.getIndexArray();
                    int interval = 3000;
                    int gap = 1000 * 60 * 60;
                    long l = maxTime.get() - 3000;
                    ExecutorService executorService1 = Executors.newFixedThreadPool(50);
                    executorService1.submit(() -> {
                        try {
                            final Vin vin = vins2.get(randomVinIndex);
                            String queryColumns = indexArray[random.nextInt(40)];
                            final long l1 = random.nextInt((int)l);
                            long start = l1 - interval * 5;
                            if (start > 0) {
                                final TimeRangeDownsampleRequest timeRangeDownsampleRequest = new TimeRangeDownsampleRequest("", new Vin(vin.getVin()), queryColumns, start, 10923000L, Aggregator.AVG, interval, new CompareExpression(new ColumnValue.DoubleFloatColumn(0.7d), CompareExpression.CompareOp.GREATER));
                                ArrayList<Row> rowArrayList = tsdbEngineSample.executeDownsampleQuery(timeRangeDownsampleRequest);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            System.out.println("executeLatestQuery error" + e);
        }
    }
}
