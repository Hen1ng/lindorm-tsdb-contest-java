package com.alibaba.lindorm.contest.util;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * @version 1.0
 */
public class SpinLockArray {
    private final AtomicIntegerArray spinLocks;

    public SpinLockArray(int number) {
        this.spinLocks = new AtomicIntegerArray(number);
    }

    public void lockRead(int idx) {
        while (true) {
            final int before = spinLocks.get(idx);
            if (before < 0) {
                continue; // write lock holed
            }
            final int after = before + 1;
            final boolean b = spinLocks.compareAndSet(idx, before, after);
            if (b) {
                break;
            }
        }
    }

    public void unlockRead(int idx) {
        final int before = spinLocks.getAndDecrement(idx);
        assert before > 0;
    }

    public void lockWrite(int idx) {
        while (!spinLocks.compareAndSet(idx, 0, -1))
            ;
    }

    public void unlockWrite(int idx) {
        final boolean b = spinLocks.compareAndSet(idx, -1, 0);
        assert b;
    }


}
