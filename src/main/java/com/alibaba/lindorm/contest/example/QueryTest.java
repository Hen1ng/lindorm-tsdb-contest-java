package com.alibaba.lindorm.contest.example;

import com.alibaba.lindorm.contest.TSDBEngineImpl;
import com.alibaba.lindorm.contest.structs.*;
import com.alibaba.lindorm.contest.util.BytesUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class QueryTest {
    static int threadNum = 15;
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

            for (int i = 0; i < 45; i++) {
                String key = String.valueOf(i);
                if (i == 3) {
                    key = "LONGITUDE";
                }
                if (i == 4) {
                    key = "YXMS";
                }if (i == 5) {
                    key = "LATITUDE";
                }
                columnTypeMap.put(key, ColumnValue.ColumnType.COLUMN_TYPE_INTEGER);
                columns.put(key, new ColumnValue.IntegerColumn(i));
            }
//            for (int i = 0; i < 2; i++) {
//                String key = bigIntKey[i];
//                columnTypeMap.put(key, ColumnValue.ColumnType.COLUMN_TYPE_INTEGER);
//                columns.put(key, new ColumnValue.IntegerColumn(random.nextInt()));
//            }
            for (int i = 0; i < 9; i++) {
                String key = i + "double";
                columnTypeMap.put(key, ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT);
                columns.put(key, new ColumnValue.DoubleFloatColumn(i / 10d));
            }
            for (int i = 0; i < 6; i++) {
                final StringBuilder sb = new StringBuilder();
                int j = i;
                while (j >= 0) {
                    sb.append(j);
                    j--;
                }
                String key = i + "String" + sb;
                columnTypeMap.put(key, ColumnValue.ColumnType.COLUMN_TYPE_STRING);
                if (i == 3) {
                    ByteBuffer buffer = ByteBuffer.wrap("".getBytes(StandardCharsets.UTF_8));
                    columns.put(key, new ColumnValue.StringColumn(buffer));
                } else {
                    ByteBuffer buffer = ByteBuffer.wrap(key.getBytes(StandardCharsets.UTF_8));
                    columns.put(key, new ColumnValue.StringColumn(buffer));
                }
            }

            Schema schema = new Schema(columnTypeMap);
            tsdbEngineSample.connect();
            tsdbEngineSample.createTable("haha", schema);
            String v1 =  BytesUtil.getRandomString(17);
            System.out.println("V1 " + v1);
            AtomicLong atomicLong = new AtomicLong(0);
            long start = System.currentTimeMillis();
            for (int i = 0; i < threadNum; i++) {
                new Thread(() -> {
                    for (int j = 0; j < 1000; j++) {
                        List<Row> rowList = new ArrayList<>();
                        for (int i1 = 0; i1 < 10; i1++) {
                            Vin vin = vins[random.nextInt(100)];
                            if (j == 100) {
                                vin = new Vin("3TZgBg7DMD2awLDNC".getBytes(StandardCharsets.UTF_8));
                            }
                            rowList.add(new Row( vin, atomicLong.getAndIncrement() * 1000, columns));
                        }
                        try {
                            tsdbEngineSample.upsert(new WriteRequest("test", rowList));
//
                        } catch (Exception e) {

                        }
                    }
                    countDownLatch.countDown();

                }).start();
            }

            countDownLatch.await();
            System.out.println("cost:" +(System.currentTimeMillis() - start) + " ms");

            tsdbEngineSample.shutdown();
            tsdbEngineSample.connect();
//            List<Vin> list = new ArrayList<>();
//            list.add(new Vin("3TZgBg7DMD2awLDNC".getBytes(StandardCharsets.UTF_8)));
//            Set<String> requestedColumns = new HashSet<>();
//            requestedColumns.add("5String543210");
//            requestedColumns.add("3String3210");
//            requestedColumns.add("0String0");
//            requestedColumns.add("0double");
//            requestedColumns.add("7double");
//            requestedColumns.add("1");
//            requestedColumns.add("LONGITUDE");
//            requestedColumns.add("LATITUDE");
//            requestedColumns.add("YXMS");
//
//            final LatestQueryRequest latestQueryRequest = new LatestQueryRequest("", list, requestedColumns);
//            final ArrayList<Row> rows = tsdbEngineSample.executeLatestQuery(latestQueryRequest);
//            final TimeRangeQueryRequest timeRangeQueryRequest = new TimeRangeQueryRequest("", new Vin("3TZgBg7DMD2awLDNC".getBytes(StandardCharsets.UTF_8)), requestedColumns, 0, Long.MAX_VALUE);
//            final ArrayList<Row> rowArrayList = tsdbEngineSample.executeTimeRangeQuery(timeRangeQueryRequest);
//            System.out.println(1);
//            tsdbEngineSample.shutdown();
        } catch (Exception e) {
            System.out.println("executeLatestQuery error" + e);
        }
    }
}
