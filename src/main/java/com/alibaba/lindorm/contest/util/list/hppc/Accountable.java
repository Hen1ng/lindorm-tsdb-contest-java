package com.alibaba.lindorm.contest.util.list.hppc;

public interface Accountable {
    /**
     * Allocated memory estimation
     *
     * @return Ram allocated in bytes
     */
    long ramBytesAllocated();

    /**
     * Bytes that is actually been used
     *
     * @return Ram used in bytes
     */
    long ramBytesUsed();
}