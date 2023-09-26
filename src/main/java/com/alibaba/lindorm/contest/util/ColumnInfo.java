package com.alibaba.lindorm.contest.util;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class ColumnInfo {
    public int maxInt = Integer.MIN_VALUE;
    public int minInt = Integer.MAX_VALUE;

    public Set<Integer> sets = new ConcurrentSkipListSet<>();

    public void update(int values) {
        maxInt = Math.max(maxInt, values);
        minInt = Math.min(minInt, values);
        sets.add(values);
    }
}
