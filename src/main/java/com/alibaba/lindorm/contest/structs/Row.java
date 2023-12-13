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

import java.util.Map;

/**
 * A row corresponds to a specific vin.
 * One vin may have several rows, where each row has its unique timestamp.
 * In write request, the columns map contains all columns in our schema, which
 * form a complete row. In read request, the result may only contain several
 * columns according to our request.
 */
public class Row {
  private final Vin vin;
  private final long timestamp;

  // For write request, this map must contain all columns defined in schema.
  // For read request, this is the result set only containing the columns we queried.
  @Constant private final Map<String, ColumnValue> columns; // KEY: columnFieldName, VALVE: column data.

  public Row(Vin vin, long timestamp, @Constant Map<String, ColumnValue> columns) {
    this.vin = vin;
    this.timestamp = timestamp;
    this.columns = columns;
  }

  public Vin getVin() {
    return vin;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public @Constant Map<String, ColumnValue> getColumns() {
    return columns;
  }

  @Override
  public String toString() {
    return String.format("Row. Vin: [%s]. Timestamp: [%d]. Columns: [%s]", vin, timestamp, columns);
  }
}
