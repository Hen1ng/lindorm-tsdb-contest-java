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

package com.alibaba.lindorm.contest.structs;

import java.util.Set;

/**
 * Request several target columns of this vin.
 * If requestedColumnFieldNames is empty, return all columns.
 * Return all rows with timestamp during [timeLowerBound, timeUpperBound).
 * timeLowerBound is included, timeUpperBound is excluded.
 */
public class TimeRangeQueryRequest {
  private final String tableName;
  private final Vin vin;
  @Constant private final Set<String> requestedColumns;
  private final long timeLowerBound;
  private final long timeUpperBound;

  public TimeRangeQueryRequest(String tableName, Vin vin,
      @Constant Set<String> requestedColumns,
      long timeLowerBound, long timeUpperBound) {
    this.tableName = tableName;
    this.vin = vin;
    this.requestedColumns = requestedColumns;
    this.timeLowerBound = timeLowerBound;
    this.timeUpperBound = timeUpperBound;
  }

  public Vin getVin() {
    return vin;
  }

  public @Constant Set<String> getRequestedColumns() {
    return requestedColumns;
  }

  public long getTimeLowerBound() {
    return timeLowerBound;
  }

  public long getTimeUpperBound() {
    return timeUpperBound;
  }

  public String getTableName() {
    return tableName;
  }
}
