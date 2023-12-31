package com.alibaba.lindorm.contest.example;

import com.alibaba.lindorm.contest.TSDBEngineImpl;
import com.alibaba.lindorm.contest.memory.VinDictMap;
import com.alibaba.lindorm.contest.structs.*;
import com.alibaba.lindorm.contest.util.BytesUtil;

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
    static int threadNum = 1;
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
            boolean write = false;
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
                        for (int j = 0; j < 10; j++) {
                            List<Row> rowList = new ArrayList<>();
                            for (int i1 = 0; i1 < 10; i1++) {
                                Vin vin = vins[random.nextInt(20)];
                                final long andIncrement = atomicLong.getAndIncrement();
                                Map<String, ColumnValue> columns1 = new HashMap<>();
                                for (String s : columns.keySet()) {
                                    columns1.put(s, columns.get(s));
                                }
                                rowList.add(new Row(vin, andIncrement * 1000, columns1));
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
                String v = "OIY7RrTHsftX0TisQ";
                tsdbEngineSample.connect();
                List<Vin> list = new ArrayList<>();
                list.add(new Vin(v.getBytes(StandardCharsets.UTF_8)));
                Set<String> requestedColumns = new HashSet<>();
//                requestedColumns.add("JUBK");
                requestedColumns.add("0String0");
                requestedColumns.add("1String10");
                requestedColumns.add("0double");
                requestedColumns.add("7double");
                requestedColumns.add("1");
                requestedColumns.add("2");
                requestedColumns.add("33");
                requestedColumns.add("34");
                requestedColumns.add("35");
                requestedColumns.add("51");
                requestedColumns.add("53");
                requestedColumns.add("0");
                requestedColumns.add("9double");
                requestedColumns.add("ORNI");
                requestedColumns.add("JUBK");

                final Random random1 = new Random();
                List<String> queryList = new ArrayList<>();
                queryList.add("0String0");
                queryList.add("0double");
                queryList.add("7double");
                queryList.add("1");
                queryList.add("2");
                queryList.add("33");
                queryList.add("34");
                queryList.add("35");
                queryList.add("0");
                queryList.add("9double");
                queryList.add("ORNI");
                queryList.add("JUBK");
                final ArrayList<Vin> vins1 = new ArrayList<>(VinDictMap.getVinDictMap().keySet());

                final LatestQueryRequest latestQueryRequest = new LatestQueryRequest("", list, requestedColumns);
                final ArrayList<Row> rows = tsdbEngineSample.executeLatestQuery(latestQueryRequest);
                final TimeRangeQueryRequest timeRangeQueryRequest = new TimeRangeQueryRequest("", new Vin(v.getBytes(StandardCharsets.UTF_8)), requestedColumns, 0, Long.MAX_VALUE);
                ArrayList<Row> rowArrayList = tsdbEngineSample.executeTimeRangeQuery(timeRangeQueryRequest);
                TimeRangeAggregationRequest timeRangeAggregationRequest = new TimeRangeAggregationRequest("", new Vin(v.getBytes(StandardCharsets.UTF_8)), "13", 0, 1000, Aggregator.MAX);
                rowArrayList = tsdbEngineSample.executeAggregateQuery(timeRangeAggregationRequest);
                timeRangeAggregationRequest = new TimeRangeAggregationRequest("", new Vin(v.getBytes(StandardCharsets.UTF_8)), "3", 0, 1000, Aggregator.MAX);
                rowArrayList = tsdbEngineSample.executeAggregateQuery(timeRangeAggregationRequest);
//                ExecutorService executorService1 = Executors.newFixedThreadPool(20);
//                CountDownLatch countDownLatch1 = new CountDownLatch(20);
//                executorService1.submit(() -> {
//                    for (int i = 0; i < 1000000; i++) {
//                        try {
//                            Vin vin = vins1.get(random1.nextInt(vins1.size()));
//                            final TimeRangeDownsampleRequest timeRangeDownsampleRequest = new TimeRangeDownsampleRequest("", vin, "7double", 0, 50000, Aggregator.AVG, 5000L, new CompareExpression(new ColumnValue.DoubleFloatColumn(0.7d), CompareExpression.CompareOp.EQUAL));
//                            ArrayList<Row> rowArrayList = tsdbEngineSample.executeDownsampleQuery(timeRangeDownsampleRequest);
//                        } catch (IOException e) {
//                            throw new RuntimeException(e);
//                        }
//                    }
//                    countDownLatch1.countDown();
//                });
//                countDownLatch1.await();
                System.out.println(1);
//                tsdbEngineSample.shutdown();
            }
        } catch (Exception e) {
            System.out.println("executeLatestQuery error" + e);
        }
    }
}
