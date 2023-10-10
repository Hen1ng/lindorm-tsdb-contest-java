package com.alibaba.lindorm.contest.index;


public class Bindex {
    public long[] fileOffset;
    public int[] totalLength;

    public Bindex(int[] totalLength, long[] fileOffset) {
        this.fileOffset = fileOffset;
        this.totalLength = totalLength;
    }
}
