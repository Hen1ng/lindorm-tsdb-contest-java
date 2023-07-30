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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * VIN: Vehicle Identification Number.<br>
 * The key type of our data schema.
 * One vin is similar to the key of a KV-Engine Database.
 * In our data scheme, one Vin may be related to several rows, where one
 * row corresponds to a specific timestamp of this vin.
 */
public class Vin implements Comparable<Vin> {
  public static final int VIN_LENGTH = 17;

  @Constant private final byte[] vin;

  public Vin(@Constant byte[] vin) {
    assert vin.length == VIN_LENGTH;
    this.vin = vin;
  }

  public @Constant byte[] getVin() {
    return vin;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Vin vin1 = (Vin) o;
    return Arrays.equals(vin, vin1.vin);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(vin);
  }

  @Override
  public int compareTo(Vin o) {
    for (int i = 0; i < VIN_LENGTH; ++i) {
      if (this.vin[i] == o.vin[i]) {
        continue;
      }
      return (this.vin[i] & 0xFF) - (o.vin[i] & 0xFF);
    }
    return 0;
  }

  @Override
  public String toString() {
    return String.format("Vin: [%s]", new String(vin, StandardCharsets.UTF_8));
  }
}
