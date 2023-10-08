package com.alibaba.lindorm.contest.index;

public class StringIndex {
    private long fileOffset;

    private int arrayOffset;

    public StringIndex(long fileOffset, int arrayOffset) {
        this.fileOffset = fileOffset;
        this.arrayOffset = arrayOffset;
    }

}
