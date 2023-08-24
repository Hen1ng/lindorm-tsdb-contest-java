//
// A simple evaluation program example helping you to understand how the
// evaluation program calls the protocols you will implement.
// Formal evaluation program is much more complex than this.
//

/*
 * Copyright Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.lindorm.contest.example;

import com.alibaba.lindorm.contest.TSDBEngineImpl;
import com.alibaba.lindorm.contest.structs.*;
import com.alibaba.lindorm.contest.util.BytesUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is an evaluation program sample.
 * The evaluation program will create a new Database using the targeted
 * local disk path, then write several rows, then check the correctness
 * of the written data, and then run the read test.
 * <p>
 * The actual evaluation program is far more complex than the sample, e.g.,
 * it might contain the restarting progress to clean all memory cache, it
 * might test the memory cache strategies by a pre-warming procedure, and
 * it might perform read and write tests crosswise, or even concurrently.
 * Besides, as long as you write to the interface specification, you don't
 * have to worry about incompatibility with our evaluation program.
 */
public class EvaluationSample {
    private static int threadNum = 30;
    private static ExecutorService executorService = Executors.newFixedThreadPool(threadNum);

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

        TSDBEngineImpl tsdbEngineSample = new TSDBEngineImpl(dataDir);

        try {
            // Stage1: write
            tsdbEngineSample.connect();
            Random random = new Random();
            ArrayList<Row> rowList = new ArrayList<>();
            Map<String, ColumnValue.ColumnType> columnTypeMap = new HashMap<>();
            Schema schema = new Schema(columnTypeMap);
            for (int j = 0; j < 10; j++) {
                Map<String, ColumnValue> columns = new HashMap<>();
                ByteBuffer buffer = ByteBuffer.allocate(3);
                buffer.put((byte) 70);
                buffer.put((byte) 71);
                buffer.put((byte) 72);
                buffer.flip();
                String vin = BytesUtil.getRandomString(17);
                String[] bigIntKey = {"LATITUDE","LONGITUDE"};
                for (int i = 0; i < 42; i++) {
                    String key = String.valueOf(i);
                    columnTypeMap.put(key, ColumnValue.ColumnType.COLUMN_TYPE_INTEGER);
                    columns.put(key, new ColumnValue.IntegerColumn(random.nextInt()%10));
                }
                for (int i = 0; i < 2; i++) {
                    String key = bigIntKey[i];
                    columnTypeMap.put(key, ColumnValue.ColumnType.COLUMN_TYPE_INTEGER);
                    columns.put(key, new ColumnValue.IntegerColumn(random.nextInt()%100));
                }
                {
                    String key = "YXMS";
                    columnTypeMap.put(key, ColumnValue.ColumnType.COLUMN_TYPE_INTEGER);
                    columns.put(key, new ColumnValue.IntegerColumn(random.nextInt()%100));
                }
                for (int i = 0; i < 9; i++) {
                    String key = i + "haha";
                    columnTypeMap.put(key, ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT);
                    columns.put(key, new ColumnValue.DoubleFloatColumn(random.nextDouble()));
                }
                for (int i = 0; i < 6; i++) {
                    String key = i + "heihei";
                    columnTypeMap.put(key, ColumnValue.ColumnType.COLUMN_TYPE_STRING);
                    columns.put(key, new ColumnValue.StringColumn(buffer));
                }
                final long timestamp = Math.abs(random.nextLong()%1000);
                rowList.add(new Row(new Vin(vin.getBytes(StandardCharsets.UTF_8)), timestamp, columns));
            }
            CountDownLatch countDownLatch = new CountDownLatch(threadNum);
            AtomicInteger atomicInteger = new AtomicInteger(0);
            tsdbEngineSample.createTable("haha", schema);
            for (int i = 0; i < threadNum; i++) {
                ArrayList<Row> finalRowList = rowList;
                executorService.submit(() -> {
                    try {
                        for (int m = 0; m < 3000; m++) {
                            tsdbEngineSample.upsert(new WriteRequest("test", finalRowList));
                            atomicInteger.getAndIncrement();
                        }
                        countDownLatch.countDown();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
            countDownLatch.await();
            System.out.println("upsert times " + atomicInteger.get());

            tsdbEngineSample.shutdown();
            try {
                executorService.shutdown();
                executorService.awaitTermination(60, TimeUnit.SECONDS);
            }catch (Exception e){
                e.printStackTrace();
            }

//      // Stage2: read
      tsdbEngineSample.connect();
//
//      ArrayList<Vin> vinList = new ArrayList<>();
//      vinList.add(new Vin(str.getBytes(StandardCharsets.UTF_8)));
//      Set<String> requestedColumns = new HashSet<>(Arrays.asList("col1", "col2", "col3"));
//      ArrayList<Row> resultSet = tsdbEngineSample.executeLatestQuery(new LatestQueryRequest("test", vinList, requestedColumns));
//      showResult(resultSet);
//
      tsdbEngineSample.shutdown();
//
//      // Stage3: overwrite
      tsdbEngineSample.connect();
//
//      buffer.flip();
//      columns = new HashMap<>();
//      columns.put("col1", new ColumnValue.IntegerColumn(321));
//      columns.put("col2", new ColumnValue.DoubleFloatColumn(1.23));
//      columns.put("col3", new ColumnValue.StringColumn(buffer));
//      str = "12345678912345678";
//      rowList = new ArrayList<>();
//      rowList.add(new Row(new Vin(str.getBytes(StandardCharsets.UTF_8)), 1, columns));
//      str = "98765432123456789";
//      rowList.add(new Row(new Vin(str.getBytes(StandardCharsets.UTF_8)), 1, columns));
//      rowList.add(new Row(new Vin(str.getBytes(StandardCharsets.UTF_8)), 2, columns));
//      rowList.add(new Row(new Vin(str.getBytes(StandardCharsets.UTF_8)), 3, columns));
//      vinList.add(new Vin(str.getBytes(StandardCharsets.UTF_8)));
//
//      tsdbEngineSample.upsert(new WriteRequest("test", rowList));
//      resultSet = tsdbEngineSample.executeLatestQuery(new LatestQueryRequest("test", vinList, requestedColumns));
//      showResult(resultSet);
//      resultSet = tsdbEngineSample.executeTimeRangeQuery(new TimeRangeQueryRequest("test", new Vin(str.getBytes(StandardCharsets.UTF_8)), requestedColumns, 1, 3));
//      showResult(resultSet);
//
//
//      tsdbEngineSample.shutdown();

        } catch (IOException | InterruptedException e) {
            System.out.println(e.getMessage());
        }
    }

//  public static void showResult(ArrayList<Row> resultSet) {
//    for (Row result : resultSet)
//      System.out.println(result);
//    System.out.println("-------next query-------");
//  }
}