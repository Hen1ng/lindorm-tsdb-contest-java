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

import java.util.Collection;

/**
 * Write several rows for this table.
 * All rows must be complete, i.e., containing all columns defined in schema.
 */
public class WriteRequest {
  private final String tableName;
  @Constant private final Collection<Row> rows;

  public WriteRequest(String tableName, @Constant Collection<Row> rows) {
    this.tableName = tableName;
    this.rows = rows;
  }

  public String getTableName() {
    return tableName;
  }

  public @Constant Collection<Row> getRows() {
    return rows;
  }
}
