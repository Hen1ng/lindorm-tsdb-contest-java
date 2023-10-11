package com.alibaba.lindorm.contest.index;

import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.Pair;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BindexFactory {

    private static ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();
    private static Bindex[] bindices = new Bindex[180000000 / 2 / Constants.CACHE_VINS_LINE_NUMS];;

    private static int position = 0;

//    public static void init() {
//        bindices = new Bindex[180000000 / 2 / Constants.CACHE_VINS_LINE_NUMS];
//        for (int i = 0; i < bindices.length; i++) {
//            long[] fileOffset1 = new long[10];
//            int[] totalLength1 = new int[10];
//            bindices[i] = new Bindex(totalLength1, fileOffset1);
//        }
//    }

    public static Pair<Integer, Bindex> getAndCreateBindex() {
        reentrantReadWriteLock.readLock().lock();
        try {
            int andIncrement = position++;
            bindices[andIncrement] = new Bindex(-1, -1);
            return Pair.of(andIncrement, bindices[andIncrement]);
        } finally {
            reentrantReadWriteLock.readLock().unlock();
        }
    }

    public static Bindex getByPosition(int position) {
        reentrantReadWriteLock.readLock().lock();
        try {
            return bindices[position];
        } finally {
            reentrantReadWriteLock.readLock().unlock();
        }
    }

    public static void updateByPosition(int position, Bindex bindex) {
        reentrantReadWriteLock.writeLock().lock();
        try {
            bindices[position] = bindex;
        } finally {
            reentrantReadWriteLock.writeLock().unlock();
        }
    }

}
