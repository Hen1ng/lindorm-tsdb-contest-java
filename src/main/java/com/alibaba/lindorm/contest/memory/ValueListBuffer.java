package com.alibaba.lindorm.contest.memory;

import com.alibaba.lindorm.contest.util.list.SortedList;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ValueListBuffer {

    private AtomicInteger position = new AtomicInteger(0);

    public ArrayBlockingQueue<SortedList<Value>> getSortedLists() {
        return sortedLists;
    }

    private ArrayBlockingQueue<SortedList<Value>> sortedLists;

    private int bufferSize;


    public ValueListBuffer(int bufferSize) {
        this.bufferSize = bufferSize;
        this.sortedLists = new ArrayBlockingQueue<>(bufferSize);
        for (int i = 0; i < bufferSize; i++) {
            sortedLists.offer(new SortedList<>((v1, v2) -> (int) (v2.getTimestamp() - v1.getTimestamp())));
        }
    }

    public SortedList<Value> getFreeList() {
        return sortedLists.poll();
    }

    public void freeList(SortedList<Value> sortedList) {
        sortedLists.offer(sortedList);
    }
}
