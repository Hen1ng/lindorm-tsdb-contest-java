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
 * Request for dividing the data within a specified time range for a given vehicle (represented by Vin)
 * into multiple windows based on a specified interval and calculating the aggregation result of requested
 * columns for each window.
 * <br>
 * timeLowerBound is included, timeUpperBound is excluded [timeLowerBound, timeUpperBound).<br>
 * "interval" means the downsample interval for each aggregation.<br>
 * "columnFilter" means the value compare expression for filtering rows,
 * the filter is applied to the requested column and takes effect during the aggregation phase only.
 */
public class TimeRangeDownsampleRequest extends TimeRangeAggregationRequest {
  private final long interval;
  private final CompareExpression columnFilter;

  public TimeRangeDownsampleRequest(String tableName, Vin vin, String columnName,
      long timeLowerBound, long timeUpperBound, Aggregator aggregator,
      long interval, CompareExpression columnFilter) {
    super(tableName, vin, columnName, timeLowerBound, timeUpperBound, aggregator);
    if (interval <= 0L || timeUpperBound <= timeLowerBound) {
      throw new IllegalArgumentException("Invalid arguments.");
    }
    this.interval = interval;
    this.columnFilter = columnFilter;
  }

  public long getInterval() {
    return interval;
  }

  public CompareExpression getColumnFilter() {
    return columnFilter;
  }
}
