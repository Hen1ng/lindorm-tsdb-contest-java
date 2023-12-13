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
 * An enum stands for the aggregator type. <br>
 * For the sake of simplicity, we only support AVG and MAX for this contest. <br>
 * The data type of the aggregation result is assumed as follows for simplicity: <br>
 * - AVG: DOUBLE <br>
 *        If all row were filtered, the result should be NaN (Bits as long: 0xfff0000000000000L).<br>
 *        You can use Double.NEGATIVE_INFINITY to conveniently get the NaN value.<br>
 * - MAX: The same as the column type of the source column type<br>
 *        If all row were filtered, the result should be NaN (0x80000000).<br>
 */
public enum Aggregator {
  AVG,
  MAX
}
