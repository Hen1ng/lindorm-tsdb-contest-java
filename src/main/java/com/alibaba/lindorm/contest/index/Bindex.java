package com.alibaba.lindorm.contest.index;


public class Bindex {
    public long[] fileOffset;
    public int[] totalLength;

    public Bindex(int[] totalLength, long[] fileOffset) {
        this.fileOffset = fileOffset;
        this.totalLength = totalLength;
    }

    public Bindex() {

    }

    public Bindex deepCopy() {
        long[] fileOffset1 = new long[fileOffset.length];
        int[] totalLength1 = new int[this.totalLength.length];
        for (int i = 0; i < fileOffset.length; i++) {
            fileOffset1[i] = fileOffset[i];
        }
        for (int i = 0; i < totalLength1.length; i++) {
            totalLength1[i] = totalLength[i];
        }
        return new Bindex(totalLength1, fileOffset1);
    }
}
