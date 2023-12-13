//
// Don't modify the method definitions of this class.
// Our evaluation program will call the methods defined here.
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

package com.alibaba.lindorm.contest;

import com.alibaba.lindorm.contest.structs.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * The TSDBEngine interface.
 */
public abstract class TSDBEngine {
  /**
   * The data path that all data stored. No other directory should be
   * written when the Database is running, or you will be called for a foul.<br>
   * Attention:<br>
   *   1. the dataPath targets a directory.<br>
   *   2. the targeted directory has been created before the constructor is called.
   */
  protected final File dataPath;

  /**
   * Create a new instance of TSDB Engine using the designated
   * local path for it storing data. If there is no data stored
   * in this local path, a new Database would be created without
   * any data, or else, a Database would be created and all
   * existing data would be loaded.
   * @param dataPath The path targeting a local position, all data
   *                 of this created TS Database Engine should be
   *                 stored in this path. After we restart the
   *                 program and re-construct a new instance of
   *                 this class with the same local path, all
   *                 previous written data at this path should be
   *                 loaded and readable for the new TSDB instance.
   */
  public TSDBEngine(File dataPath) {
    if (!dataPath.isDirectory()) {
      throw new IllegalStateException("The data path should be an existing directory");
    }
    this.dataPath = dataPath;
  }

  /**
   * Start the Engine and load any data stored in data path if existing.
   */
  public abstract void connect() throws IOException;

  /**
   * Create a new table.
   * @param schema   Describe the table schema of this TSDB instance,
   *                 i.e., how many columns are there for each row,
   *                 the name of each column and what the columns'
   *                 data types are.
   */
  public abstract void createTable(String tableName, Schema schema) throws IOException;

  /**
   * Shutdown the Engine, after this function is returned, all dirty data
   * should be persisted, i.e., written to the data path and can be read
   * by any same-class instances using the same data path.
   */
  public abstract void shutdown();

  /**
   * Write several rows to the Engine and should be readable immediately by
   * #read function of this instance. Whether or not should data be persisted
   * immediately to data path is not defined.<br>
   * Attention:<br>
   * 1. Any implements of this function should be multi-thread friendly.<br>
   * 2. If a row for the same vin and same timestamp exists, the action is undefined.
   */
  public abstract void write(WriteRequest wReq) throws IOException;

  /**
   * Read the rows related to several vins with the newest timestamp.<br>
   * Attention:<br>
   * 1. Any implements of this function should be multi-thread friendly.<br>
   * 2. Do not return any column that was not requested.<br>
   * 3. If no column is requested (pReadReq.requestedColumns.empty() == true), return all columns.<br>
   * 4. If no data for a vin, skip this vin.<br>
   */
  public abstract ArrayList<Row> executeLatestQuery(LatestQueryRequest pReadReq) throws IOException;

  /**
   * Read rows related to the vin from trReadReq and with a timestamp [timeLowerBound, timeUpperBound).
   * timeLowerBound is included, and timeUpperBound is excluded.
   * The key of the returned value is the timestamp of the Row of its value.<br>
   * Attention: <br>
   * 1. Any implements of this function should be multi-thread friendly.<br>
   * 2. Do not return any column that was not requested.<br>
   * 3. If no column is requested (trReadReq.requestedColumnFieldNames.empty() == true), return all columns.<br>
   * 4. If no such vin, return an empty List.<br>
   */
  public abstract ArrayList<Row> executeTimeRangeQuery(TimeRangeQueryRequest trReadReq) throws IOException;

  /**
   * Calculate the aggregation result of requested columns for the specified time range of a given vehicle (represented by vin).
   * <br><br>
   * For instance, given a series of data in 10 seconds for a vin:
   * <br>
   * Assuming that we do aggregation to column named "AZKP": <br>
   * timestamp(ms)  | AZKP  <br>
   * ---------------|------- <br>
   * 1693274400000  | 10     <br>
   * 1693274401000  | 11     <br>
   * 1693274402000  | 13     <br>
   * 1693274403000  | 15     <br>
   * 1693274404000  | 17     <br>
   * 1693274405000  | 19     <br>
   * 1693274406000  | 21     <br>
   * 1693274407000  | 20     <br>
   * 1693274408000  | 18     <br>
   * 1693274409000  | 16     <br>
   * <br>
   * If a TimeRangeAggregationRequest with the following properties received: <br>
   * - aggregator = Aggregator.MAX <br>
   * - columnName = "AZKP" <br>
   * - lowerBound = 1693274400155 <br>
   * - upperBound = 1693274410235 <br>
   * <br>
   * Then the row in result should be like this:<br>
   * <br>
   * 1693274400155  | 21     (9 rows in range) <br>
   *
   * @note <br>
   * 1. The names in the Map<String, ColumnValue> of the returned row should be the same as the requested column name. <br>
   * 2. The timestamp in the returned row should be the start timestamp of the given time range. <br>
   * 3. If there is a row whose timestamp equals upperBound, this row should not be included.<br>
   * 4. If there is a row whose timestamp equals lowerBound, this row should be included.<br>
   * 5. The result should only contain 1 row.<br>
   * 6. Any implementations for this interface should be thread-safe. <br>
   */
  public abstract ArrayList<Row> executeAggregateQuery(TimeRangeAggregationRequest aggregationReq) throws IOException;

  /**
   * Divide the data within a specified time range for a given vehicle (represented by Vin)
   * into multiple windows based on a specified interval.
   * Calculate the aggregation result of requested column for each window.
   * <br><br>
   * For instance, given a series of data in 10 seconds for a vin: <br>
   * Assuming that we do down sample to column named "AZKP": <br>
   * timestamp(ms)  | AZKP  <br>
   * ---------------|------- <br>
   * 1693274400000  | 15     <br>
   * 1693274401000  | 11     <br>
   * 1693274402000  | 13     <br>
   * 1693274403000  | 15     <br>
   * 1693274404000  | 17     <br>
   * 1693274405000  | 19     <br>
   * 1693274406000  | 21     <br>
   * 1693274407000  | 20     <br>
   * 1693274408000  | 18     <br>
   * 1693274409000  | 16     <br>
   * <br>
   * If a TimeRangeDownsampleRequest with the following properties received, <br>
   * - interval = 5000 ms <br>
   * - columnFilter = CompareExpression.compare(GREATER, 11) <br>
   * - aggregator = Aggregator.AVG <br>
   * - columnName = "AZKP" <br>
   * - lowerBound = 1693274400183 <br>
   * - upperBound = 1693274410183 <br>
   * <br>
   * Then the rows in result should be like this:<br>
   * <br>
   * 1693274400183  | 16.0      (13, 15, 17, 19 -> 4 rows in range) <br>
   * 1693274405183  | 18.75     (21, 20, 18, 16 -> 4 rows in range) <br>
   * <br>
   * If we modified the filter to <br>
   * - columnFilter = CompareExpression.compare(EQUAL, 20) <br>
   * Then the rows in result should be like this:<br>
   * <br>
   * 1693274400183  | NaN      (0 row in range) <br>
   * 1693274405183  | 20.0     (20 -> 1 row in range) <br>
   * <br>
   * For DOUBLE, the NaN is 0xfff0000000000000L, <br>
   * For INTEGER, the NaN is 0x80000000. <br>
   * <br>
   * On the other hand, if a TimeRangeDownsampleRequest with the following properties received, <br>
   * - interval = 5000 ms <br>
   * - columnFilter = CompareExpression.compare(GREATER, 10) <br>
   * - aggregator = Aggregator.AVG <br>
   * - columnName = "AZKP" <br>
   * - lowerBound = 1693274410000 <br>
   * - upperBound = 1693274420000 <br>
   * <br>
   * Since no row is in such timestamp section, an EMPTY list would be returned (Not NaN).<br>
   * <br>
   *
   * @note <br>
   * 1. The names in the Map<String, ColumnValue> of the returned rows should be the same as the requested column name.<br>
   * 2. For the purpose of simplicity, (timeUpperBound - timeLowerBound) is guaranteed to be divisible by interval. <br>
   * 3. If there is a row whose timestamp equals upperBound, this row should not be included.<br>
   * 4. If there is a row whose timestamp equals lowerBound, this row should be included.<br>
   * 5. Any implementation for this interface should be thread-safe. <br>
   */
  public abstract ArrayList<Row> executeDownsampleQuery(TimeRangeDownsampleRequest downsampleReq) throws IOException;

  /**
   * Get the data path in which the TSDB Engine stores data.
   * @return The data path of this TSDB Engine.
   */
  public File getDataPath() {
    return dataPath;
  }
}
