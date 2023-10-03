package com.alibaba.lindorm.contest.memory;

import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.util.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ThreadSafeLRUCache<K, V> {
    private final Map<K, V> map;

    private final Queue<K> queue;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final int capacity;

    public ThreadSafeLRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.queue = new LinkedList<>();
    }

    public V get(K key) {
        lock.readLock().lock();
        try {
            return map.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            if(queue.size() == capacity){
                K poll = queue.poll();
                map.remove(poll);
            }
            queue.add(key);
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
        int operationsPerThread = 1000000;  // 每个线程进行1000000次操作

        ThreadSafeLRUCache<Integer, String> cache = new ThreadSafeLRUCache<>(3);
        ConcurrentLRUHashMap<Integer, String> cache1 = new ConcurrentLRUHashMap<>(1000, 0.75F,3);

        cache.put(1,"123");
        cache.put(2,"123");
        cache.put(3,"123");
        cache.put(1,"123");
        cache.put(5,"123");
        cache.put(4,"123");
        System.out.println(cache);
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
