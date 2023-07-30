package com.alibaba.lindorm.contest.util.list.hppc;

public interface Preallocable {
    /**
     * Ensure this container can hold at least the given number of elements without resizing its
     * buffers.
     *
     * @param expectedElements The total number of elements, inclusive.
     */
    public void ensureCapacity(int expectedElements);
}
