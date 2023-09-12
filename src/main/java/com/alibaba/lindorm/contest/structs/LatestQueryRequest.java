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
import java.util.Set;

/**
 * Request several target columns of several vins.
 * If requestedColumns is empty, return all columns.
 * Return the rows with the newest timestamp for these vins.
 */
public class LatestQueryRequest {
  private final String tableName;
  private final Collection<Vin> vins;
  @Constant private final Set<String> requestedColumns;

  public LatestQueryRequest(String tableName, Collection<Vin> vins,
      @Constant Set<String> requestedColumns) {
    this.tableName = tableName;
    this.vins = vins;
    this.requestedColumns = requestedColumns;
  }

  public Collection<Vin> getVins() {
    return vins;
  }

  public @Constant Set<String> getRequestedColumns() {
    return requestedColumns;
  }

  public String getTableName() {
    return tableName;
  }
}
