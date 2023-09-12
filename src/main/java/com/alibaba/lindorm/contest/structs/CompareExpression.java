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
 * A class for the expression of a column compare operation.
 */
public class CompareExpression {
  private final ColumnValue value;
  private final CompareOp compareOp;

  public CompareExpression(ColumnValue value, CompareOp compareOp) {
    this.value = value;
    this.compareOp = compareOp;
  }

  public ColumnValue getValue() {
    return value;
  }

  public CompareOp getCompareOp() {
    return compareOp;
  }

  public boolean doCompare(ColumnValue value1) {
    switch (compareOp) {
      case EQUAL:
        return value.equals(value1);
      case GREATER:
        ColumnValue.ColumnType columnType = value1.getColumnType();
        if (columnType != value.getColumnType()) {
          return false;
        }
        switch (columnType) {
          case COLUMN_TYPE_INTEGER:
            return value1.getIntegerValue() > value.getIntegerValue();
          case COLUMN_TYPE_DOUBLE_FLOAT:
            return value1.getDoubleFloatValue() > value.getDoubleFloatValue();
          default:
            throw new IllegalArgumentException("Unsupported column type for comparing: " + columnType);
        }
      default:
        throw new IllegalArgumentException("Unsupported compare op: " + compareOp);
    }
  }

  /**
   * An enum stands for the column filter operator.
   * For the sake of simplicity, we only support EQUAL and GREATER for this contest.
   */
  public enum CompareOp {
    EQUAL, GREATER
  }
}
