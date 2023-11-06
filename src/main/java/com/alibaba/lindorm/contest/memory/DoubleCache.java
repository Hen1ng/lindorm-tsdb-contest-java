package com.alibaba.lindorm.contest.memory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DoubleCache {
    public static Map<Long, byte[]> cache = new ConcurrentHashMap<>(100000);

    public static AtomicInteger cacheSize = new AtomicInteger(0);
    public static AtomicLong cacheNums = new AtomicLong(0);

    public static void put(long key, byte[] value) {
        if (cache.size() > 100000) {
            return;
        }
        cache.put(key, value);
    }

    public static byte[] get(long key) {
        cacheNums.getAndIncrement();
        return cache.get(key);
    }
}
