package com.alibaba.lindorm.contest.memory;

import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.util.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ThreadSafeLRUCache<K, V> {
    private final Map<K, V> map;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final int capacity;

    public ThreadSafeLRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new LinkedHashMap<K, V>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > ThreadSafeLRUCache.this.capacity;
            }
        };
    }

    public V get(K key) {
        lock.readLock().lock();
        try {
            if(!map.containsKey(key))return null;
            return map.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            map.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return map.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 10;  // 使用10个线程进行测试
        int operationsPerThread = 100000;  // 每个线程进行1000000次操作

        ThreadSafeLRUCache<Integer, String> cache = new ThreadSafeLRUCache<>(3);
        ConcurrentLRUHashMap<Integer, String> cache1 = new ConcurrentLRUHashMap<>(3);

        // 测试ThreadSafeLRUCache
        long start = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    cache.put(j, "Value" + j);
                    cache.get(j % 3);  // 模拟获取操作
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        long end = System.nanoTime();
        System.out.println("ThreadSafeLRUCache took: " + (end - start) / 1_000_000 + " ms");

        // 测试ConcurrentLRUHashMap
        start = System.nanoTime();
        executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    cache1.put(j, "Value" + j);
                    cache1.get(j % 3);  // 模拟获取操作
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        end = System.nanoTime();
        System.out.println("ConcurrentLRUHashMap took: " + (end - start) / 1_000_000 + " ms");
    }
}
