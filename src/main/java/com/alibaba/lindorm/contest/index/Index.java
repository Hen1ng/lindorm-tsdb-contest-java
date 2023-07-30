package com.alibaba.lindorm.contest.index;


public class Index {

    private long offset;
    private long maxTimestamp;
    private long minTimestamp;

    public int getValueSize() {
        return valueSize;
    }

    private int valueSize;

    public long getOffset() {
        return offset;
    }

    public long getMaxTimestamp() {
        return maxTimestamp;
    }

    public long getMinTimestamp() {
        return minTimestamp;
    }

    public int getLength() {
        return length;
    }

    private int length;

    public Index(long offset, long maxTimestamp, long minTimestamp, int length, int valueSize) {
        this.offset = offset;
        this.maxTimestamp = maxTimestamp;
        this.minTimestamp = minTimestamp;
        this.length = length;
        this.valueSize = valueSize;
    }

    @Override
    public String toString() {
        return offset +
                "," +
                maxTimestamp +
                "," +
                minTimestamp +
                "," +
                length +
                "," +
                valueSize
                ;

    }
}
