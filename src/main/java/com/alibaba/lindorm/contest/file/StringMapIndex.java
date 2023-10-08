package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.index.StringIndex;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StringMapIndex {
    public static Map<Long, Map<Integer, StringIndex>> maps = new ConcurrentHashMap<>(180000000);

    public static StringIndex get(int vinIndex, long ts) {
        if (maps.containsKey(ts)) {
            return maps.get(ts).get(vinIndex);
        } else {
            return null;
        }
    }

    public static void put(int vinIndex, long ts, StringIndex index) {
        if (!maps.containsKey(ts)) {
            maps.put(ts, new ConcurrentHashMap<>(5000));
        }
        maps.get(ts).put(vinIndex, index);
    }
}
