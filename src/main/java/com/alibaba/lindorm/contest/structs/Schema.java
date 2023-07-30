//
// Don't modify this file, the evaluation program is compiled
// based on this header file.
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

import com.alibaba.lindorm.contest.TSDBEngine;

import java.util.Collections;
import java.util.Map;

/**
 * Delivered into TSDB Engine when {@link TSDBEngine#createTable(String, Schema)} ()} is called.
 * This object describe the table schema.
 */
public class Schema {
  // KEY: columnFieldName, VALUE: The column data type of this column field.
  private final Map<String, ColumnValue.ColumnType> columnTypeMap;

  public Schema(Map<String, ColumnValue.ColumnType> columnTypeMap) {
    this.columnTypeMap = Collections.unmodifiableMap(columnTypeMap);
  }

  public Map<String, ColumnValue.ColumnType> getColumnTypeMap() {
    return columnTypeMap;
  }
}
