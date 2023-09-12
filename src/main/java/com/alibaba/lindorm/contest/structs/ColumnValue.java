//
// Don't modify the method definitions of this class.
// Our evaluation program will call the methods defined here.
//
// Column definitions.
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A column object relates to a data stored in this column for a specific row and a specific vin.
 * A column's data may be of type integer, double float or string. Each column has its columnFieldName for navigating.
 * <p></p>
 * Usage example: <br>
 * &nbsp;&nbsp;Column c1 = new IntegerColumn(15); // Create integer column. <br>
 * &nbsp;&nbsp;Column c2 = new DoubleFloatColumn(12243.324D); // Create double float column. <br>
 * &nbsp;&nbsp;Column c3 = new StringColumn(buffer); // Create string column. <br>
 * &nbsp;&nbsp;Column c = ...; <br>
 * &nbsp;&nbsp;ColumnType columnType = c.getColumnType(); <br>
 * &nbsp;&nbsp;if (columnType == ColumnType.COLUMN_TYPE_INTEGER) { <br>
 * &nbsp;&nbsp;&nbsp;&nbsp;System.out.println(c.getIntegerValue()); <br>
 * &nbsp;&nbsp;} else if (columnType == ColumnType.COLUMN_TYPE_DOUBLE_FLOAT) { <br>
 * &nbsp;&nbsp;&nbsp;&nbsp;System.out.println(c.getIntegerValue()); <br>
 * &nbsp;&nbsp;} else if (columnType == ColumnType.COLUMN_TYPE_STRING) { <br>
 * &nbsp;&nbsp;&nbsp;&nbsp;ByteBuffer content = c.getStringValue(); <br>
 * &nbsp;&nbsp;} <br>
 */
public abstract class ColumnValue {
  public enum ColumnType {
    COLUMN_TYPE_STRING, // Undefined length.
    COLUMN_TYPE_INTEGER, // 4B.
    COLUMN_TYPE_DOUBLE_FLOAT // 8B.
  }

  private ColumnValue() {
  }

  public abstract ColumnType getColumnType();

  public int getIntegerValue() {
    throw new IllegalStateException("Is not a integer type column");
  }

  public double getDoubleFloatValue() {
    throw new IllegalStateException("Is not a double float type column");
  }

  /**
   * The {@link ByteBuffer#remaining()} targets the length of this string, which can be 0 meaning an empty string.
   */
  @Constant public ByteBuffer getStringValue() {
    throw new IllegalStateException("Is not a string type column");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ColumnValue rhs = (ColumnValue) o;
    if (getColumnType() == ColumnType.COLUMN_TYPE_INTEGER) {
      return getIntegerValue() == rhs.getIntegerValue();
    } else if (getColumnType() == ColumnType.COLUMN_TYPE_DOUBLE_FLOAT) {
      return getDoubleFloatValue() == rhs.getDoubleFloatValue();
    } else {
      return getStringValue().equals(rhs.getStringValue());
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Column. ");
    sb.append("Type: [").append(getColumnType()).append("]. ");
    sb.append("Value: [");
    if (getColumnType() == ColumnType.COLUMN_TYPE_INTEGER) {
      int i = getIntegerValue();
      sb.append(i == Integer.MIN_VALUE ? "NaN[0x80000000]" : getIntegerValue());
    } else if (getColumnType() == ColumnType.COLUMN_TYPE_DOUBLE_FLOAT) {
      double d = getDoubleFloatValue();
      sb.append(d == Double.NEGATIVE_INFINITY ? "NaN[0xfff0000000000000L]" : d);
    } else {
      int length = getStringValue().remaining();
      byte[] b = new byte[length];
      getStringValue().get(b, 0, b.length);
      sb.append(new String(b, StandardCharsets.UTF_8));
    }
    sb.append("]");
    return sb.toString();
  }

  public static class IntegerColumn extends ColumnValue {
    private final int value;

    public IntegerColumn(int value) {
      this.value = value;
    }

    @Override
    public ColumnType getColumnType() {
      return ColumnType.COLUMN_TYPE_INTEGER;
    }

    @Override
    public int getIntegerValue() {
      return value;
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value);
    }
  }

  public static class DoubleFloatColumn extends ColumnValue {
    private final double value;

    public DoubleFloatColumn(double value) {
      this.value = value;
    }

    @Override
    public ColumnType getColumnType() {
      return ColumnType.COLUMN_TYPE_DOUBLE_FLOAT;
    }

    @Override
    public double getDoubleFloatValue() {
      return value;
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value);
    }
  }

  public static class StringColumn extends ColumnValue {
    @Constant private final ByteBuffer value;

    public StringColumn(@Constant ByteBuffer value) {
      this.value = value;
    }

    @Override
    public ColumnType getColumnType() {
      return ColumnType.COLUMN_TYPE_STRING;
    }

    @Override
    public @Constant ByteBuffer getStringValue() {
      return value.slice();
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.value);
    }
  }
}
