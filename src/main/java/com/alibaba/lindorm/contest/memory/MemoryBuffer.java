package com.alibaba.lindorm.contest.memory;

import com.alibaba.lindorm.contest.util.list.SortedList;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemoryBuffer {

    private ValueListBuffer[] valueListBuffers;
    public final ReentrantReadWriteLock[] locks;

    public MemoryBuffer() {
        valueListBuffers = new ValueListBuffer[30000];
        locks = new ReentrantReadWriteLock[30000];
        for (int i = 0; i < 30000; i++) {
            valueListBuffers[i] = new ValueListBuffer(5);
            locks[i] = new ReentrantReadWriteLock();
        }
    }

    public SortedList<Value> getFreeList(int i) {
        final ReentrantReadWriteLock lock = locks[i];
        lock.writeLock().lock();
        try {
            return valueListBuffers[i].getFreeList();
        } finally {
            lock.writeLock().unlock();
        }
    }


    public ArrayBlockingQueue<SortedList<Value>> getList(int i) {
         return valueListBuffers[i].getSortedLists();
    }

    public void freeBuffer(SortedList<Value> sortedList, int i) {
        valueListBuffers[i].getSortedLists().offer(sortedList);
    }
}
