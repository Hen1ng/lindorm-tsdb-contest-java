//
// A sample implement of TSDBEngine.
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

import com.alibaba.lindorm.contest.TSDBEngine;
import com.alibaba.lindorm.contest.structs.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A sample implement of TSDBEngine.
 * This class implements protocols of TSDBEngine conforming to the definitions.
 */
public class TSDBEngineSample extends TSDBEngine {
  private boolean connected = false;
  private final Map<String, ArrayList<Row>> data;

  public TSDBEngineSample(File dataPath) {
    super(dataPath);
    data = new HashMap<>();
  }

  @Override
  public void connect() throws IOException {
    if (connected) {
      throw new IOException("Connected");
    }
    File directory = getDataPath();
    File datasetFile = new File(directory, "dataset.txt");
    if (datasetFile.exists()) {
      loadMapFromFile(datasetFile, data);
      System.out.println("Loaded data from file: " + datasetFile);
    }
    connected = true;
  }

  @Override
  public void createTable(String tableName, Schema schema) throws IOException {
    if (data.containsKey(tableName)) {
      throw new IOException("Table already exists: " + tableName);
    }
    data.put(tableName, new ArrayList<>());
  }

  @Override
  public void shutdown() {
    if (!connected) {
      return;
    }
    File directory = getDataPath();
    File datasetFile = new File(directory, "dataset.txt");
    try {
      saveMapToFile(data, datasetFile);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    data.clear();
    connected = false;
  }

  @Override
  public void write(WriteRequest wReq) throws IOException {
    synchronized (data) {
      if (data.containsKey(wReq.getTableName())) {
        ArrayList<Row> curRows = data.get(wReq.getTableName());
        for (Row row : wReq.getRows()) {
          boolean flag = false;
          for (int i = 0; i < curRows.size(); i++) {
            if (curRows.get(i).getVin().equals(row.getVin()) && curRows.get(i).getTimestamp() == row.getTimestamp()) {
              flag = true;
              curRows.set(i, row);
              break;
            }
          }
          if (!flag) {
            curRows.add(row);
          }
        }
      } else {
        throw new IOException("Table does not exist: " + wReq.getTableName());
      }
    }
  }

  @Override
  public ArrayList<Row> executeLatestQuery(LatestQueryRequest pReadReq) throws IOException {
    synchronized (data) {
      ArrayList<Row> ans = new ArrayList<>();
      if (!data.containsKey(pReadReq.getTableName())) {
        return ans;
      }
      ArrayList<Row> rows = data.get(pReadReq.getTableName());
      Map<Vin, Row> midAns = new HashMap<>();
      for (Row row : rows) {
        Vin vin = row.getVin();
        if (pReadReq.getVins().contains(vin)) {
          if (!midAns.containsKey(vin) || row.getTimestamp() > midAns.get(vin).getTimestamp()) {
            midAns.put(vin, row);
          }
        }
      }
      if (midAns.size() == 0) {
        return ans;
      }
      Set<String> requestedColumns = pReadReq.getRequestedColumns();
      if (requestedColumns == null) {
        return new ArrayList<>(midAns.values());
      }

      for (Row row : midAns.values()) {
        Map<String, ColumnValue> filteredColumns = row.getColumns().entrySet().stream()
                .filter(entry -> requestedColumns.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        ans.add(new Row(row.getVin(), row.getTimestamp(), filteredColumns));
      }
      return ans;
    }
  }

  @Override
  public ArrayList<Row> executeTimeRangeQuery(TimeRangeQueryRequest trReadReq) throws IOException {
    synchronized (data) {
      ArrayList<Row> ans = new ArrayList<>();
      if (!data.containsKey(trReadReq.getTableName())) {
        return ans;
      }
      ArrayList<Row> rows = data.get(trReadReq.getTableName());

      for (Row row : rows) {
        Vin vin = row.getVin();
        if (trReadReq.getVin().equals(vin)) {
          if (row.getTimestamp() >= trReadReq.getTimeLowerBound() && row.getTimestamp() < trReadReq.getTimeUpperBound()) {
            Map<String, ColumnValue> filteredColumns = row.getColumns().entrySet().stream()
                    .filter(entry -> trReadReq.getRequestedColumns().contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            ans.add(new Row(row.getVin(), row.getTimestamp(), filteredColumns));
          }
        }
      }
      return ans;
    }
  }

  @Override
  public ArrayList<Row> executeAggregateQuery(TimeRangeAggregationRequest aggregationReq) throws IOException {
    return null;
  }

  @Override
  public ArrayList<Row> executeDownsampleQuery(TimeRangeDownsampleRequest downsampleReq) throws IOException {
    return null;
  }

  public static String rowToString(Row row) {
    StringBuilder sb = new StringBuilder();
    sb.append(new String(row.getVin().getVin(), StandardCharsets.UTF_8))
            .append(",")
            .append(row.getTimestamp())
            .append(",")
            .append(row.getColumns().size());

    for (Map.Entry<String, ColumnValue> entry : row.getColumns().entrySet()) {
      sb.append(",")
              .append(entry.getKey())
              .append(",")
              .append(entry.getValue().getColumnType())
              .append(",");

      switch (entry.getValue().getColumnType()) {
        case COLUMN_TYPE_INTEGER:
          sb.append(entry.getValue().getIntegerValue());
          break;
        case COLUMN_TYPE_DOUBLE_FLOAT:
          sb.append(entry.getValue().getDoubleFloatValue());
          break;
        case COLUMN_TYPE_STRING:
          sb.append(new String(entry.getValue().getStringValue().array(), StandardCharsets.UTF_8));
          break;
      }
    }
    return sb.toString();
  }

  public static Row stringToRow(String rowStr) {
    String[] parts = rowStr.split(",");

    // 解析 vin
    Vin vin = new Vin(parts[0].getBytes(StandardCharsets.UTF_8));

    // 解析 timestamp
    long timestamp = Long.parseLong(parts[1]);

    // 解析 columns
    int columnsSize = Integer.parseInt(parts[2]);
    Map<String, ColumnValue> columns = new HashMap<>();
    int index = 3;

    for (int i = 0; i < columnsSize; i++) {
      String columnFieldName = parts[index++];
      ColumnValue.ColumnType columnType = ColumnValue.ColumnType.valueOf(parts[index++]);

      ColumnValue columnValue;
      switch (columnType) {
        case COLUMN_TYPE_INTEGER:
          columnValue = new ColumnValue.IntegerColumn(Integer.parseInt(parts[index++]));
          break;
        case COLUMN_TYPE_DOUBLE_FLOAT:
          columnValue = new ColumnValue.DoubleFloatColumn(Double.parseDouble(parts[index++]));
          break;
        case COLUMN_TYPE_STRING:
          ByteBuffer buffer = ByteBuffer.wrap(parts[index++].getBytes(StandardCharsets.UTF_8));
          columnValue = new ColumnValue.StringColumn(buffer);
          break;
        default:
          throw new IllegalStateException("Invalid column type");
      }
      columns.put(columnFieldName, columnValue);
    }
    return new Row(vin, timestamp, columns);
  }

  private static void saveMapToFile(Map<String, ArrayList<Row>> map, File file)
      throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      for (Map.Entry<String, ArrayList<Row>> entry : map.entrySet()) {
        writer.write(entry.getKey());
        writer.newLine();

        for (Row row : entry.getValue()) {
          writer.write(rowToString(row));
          writer.newLine();
        }
        writer.newLine();
      }
    }
  }

  private static void loadMapFromFile(File file, Map<String, ArrayList<Row>> map)
      throws IOException {
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String key = line;
        ArrayList<Row> rows = new ArrayList<>();

        while ((line = reader.readLine()) != null && !line.isEmpty()) {
          rows.add(stringToRow(line));
        }
        map.put(key, rows);
      }
    }
  }

}
