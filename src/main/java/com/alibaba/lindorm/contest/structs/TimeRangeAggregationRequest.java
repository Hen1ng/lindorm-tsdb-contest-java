/*
 * Copyright Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.lindorm.contest.structs;

/**
 * Request for aggregation of some specified columns of this vin within a time range.
 * <br>
 * timeLowerBound is included, timeUpperBound is excluded [timeLowerBound, timeUpperBound).<br>
 * "aggregator" means the aggregator type.<br>
 * "columnName" means the column name for aggregation.
 */
public class TimeRangeAggregationRequest {
  protected final String tableName;
  protected final Vin vin;
  protected final String columnName;
  protected final long timeLowerBound;
  protected final long timeUpperBound;
  protected final Aggregator aggregator;

  public TimeRangeAggregationRequest(String tableName, Vin vin, String columnName,
      long timeLowerBound, long timeUpperBound, Aggregator aggregator) {
    this.tableName = tableName;
    this.vin = vin;
    this.columnName = columnName;
    this.timeLowerBound = timeLowerBound;
    this.timeUpperBound = timeUpperBound;
    this.aggregator = aggregator;
  }

  public String getTableName() {
    return tableName;
  }

  public Vin getVin() {
    return vin;
  }

  public String getColumnName() {
    return columnName;
  }

  public long getTimeLowerBound() {
    return timeLowerBound;
  }

  public long getTimeUpperBound() {
    return timeUpperBound;
  }

  public Aggregator getAggregator() {
    return aggregator;
  }
}
