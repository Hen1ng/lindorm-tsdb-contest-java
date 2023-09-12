package com.alibaba.lindorm.contest.example;
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

import com.alibaba.lindorm.contest.TSDBEngineImpl;
import com.alibaba.lindorm.contest.structs.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TestMyDb {
    public static void main(String[] args) {

        File dataDir = new File("data_dir_test");

        if (dataDir.isFile()) {
            throw new IllegalStateException("Clean the directory before we start the demo");
        }


        boolean ret = dataDir.mkdirs();
        if (!ret) {
            throw new IllegalStateException("Cannot create the temp data directory: " + dataDir);
        }

        TSDBEngineImpl tsdbEngineSample = new TSDBEngineImpl(dataDir);

        try {
            // Stage1: write
            tsdbEngineSample.connect();

            Map<String, ColumnValue> columns = new HashMap<>();
            ByteBuffer buffer = ByteBuffer.allocate(3);
            buffer.put((byte) 70);
            buffer.put((byte) 71);
            buffer.put((byte) 72);
            buffer.flip();
            columns.put("col1", new ColumnValue.IntegerColumn(123));

            columns.put("col2", new ColumnValue.DoubleFloatColumn(1.23));
            columns.put("col3", new ColumnValue.StringColumn(buffer));
            String str = "12345678912345678";
            ArrayList<Row> rowList = new ArrayList<>();
            rowList.add(new Row(new Vin(str.getBytes(StandardCharsets.UTF_8)), 1, columns));

            Map<String, ColumnValue.ColumnType> cols = new HashMap<>();
            cols.put("col1", ColumnValue.ColumnType.COLUMN_TYPE_INTEGER);

            cols.put("col2", ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT);
            cols.put("col3", ColumnValue.ColumnType.COLUMN_TYPE_STRING);
            Schema schema = new Schema(cols);

            tsdbEngineSample.createTable("test", schema);
            tsdbEngineSample.write(new WriteRequest("test", rowList));

            tsdbEngineSample.shutdown();

            // Stage2: read
            tsdbEngineSample.connect();

            ArrayList<Vin> vinList = new ArrayList<>();
            vinList.add(new Vin(str.getBytes(StandardCharsets.UTF_8)));
            Set<String> requestedColumns = new HashSet<>(Arrays.asList("col1", "col2", "col3"));
            ArrayList<Row> resultSet = tsdbEngineSample.executeLatestQuery(new LatestQueryRequest("test", vinList, requestedColumns));
            showResult(resultSet);

            resultSet = tsdbEngineSample.executeTimeRangeQuery(new TimeRangeQueryRequest("test",
                    new Vin(str.getBytes(StandardCharsets.UTF_8)), requestedColumns, 1, 2));
            showResult(resultSet);

            tsdbEngineSample.shutdown();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void showResult(ArrayList<Row> resultSet) {
        for (Row result : resultSet)
            System.out.println(result);
        System.out.println("-------next query-------");
    }
}