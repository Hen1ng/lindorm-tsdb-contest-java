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

import com.alibaba.lindorm.contest.structs.LatestQueryRequest;
import com.alibaba.lindorm.contest.structs.Row;
import com.alibaba.lindorm.contest.structs.Schema;
import com.alibaba.lindorm.contest.structs.TimeRangeQueryRequest;
import com.alibaba.lindorm.contest.structs.WriteRequest;

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
   * 2. If a row for the same vin and same timestamp exists, overwrite it.
   */
  public abstract void upsert(WriteRequest wReq) throws IOException;

  /**
   * Read the rows related to several vins with the newest timestamp.<br>
   * Attention:<br>
   * 1. Any implements of this function should be multi-thread friendly.<br>
   * 2. Do not return any column that was not requested.<br>
   * 3. If no column is requested (pReadReq.requestedColumns.empty() == true), return all columns.<br>
   * 4. If no data for a vin, skip this vin.<br>
   * 5. The returned value must be of type java.util.ArrayList, you should not inherit this class and return
   *    a subclass instance of java.util.ArrayList.
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
   * 5. The returned value must be of type java.util.HashSet, you should not inherit this class and return
   *    a subclass instance of java.util.HashSet.
   */
  public abstract ArrayList<Row> executeTimeRangeQuery(TimeRangeQueryRequest trReadReq) throws IOException;

  /**
   * Get the data path in which the TSDB Engine stores data.
   * @return The data path of this TSDB Engine.
   */
  public File getDataPath() {
    return dataPath;
  }
}
