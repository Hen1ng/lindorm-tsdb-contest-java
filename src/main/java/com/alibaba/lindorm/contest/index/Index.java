package com.alibaba.lindorm.contest.index;


import java.util.List;
import java.util.Objects;

public class Index {

    private long offset;
    private long maxTimestamp;
    private long minTimestamp;

    private AggBucket aggBucket;

    private int bigIntOffset;

    public void setTimestampList(List<Long> timestampList) {
        this.timestampList = timestampList;
    }

    public List<Long> getTimestampList() {
        return timestampList;
    }

    private List<Long> timestampList;

    public int getValueSize() {
        return valueSize;
    }

    private int valueSize;

    public AggBucket getAggBucket() {
        return aggBucket;
    }

    public long getOffset() {
        return offset;
    }

    public int getOffsetLine() {
        return offsetLine;
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

    private int offsetLine;

    public int getDoubleLine() {
        return doubleLine;
    }

    private int doubleLine;

    private DoubleIndexMap doubleIndexMap;

    public Index(long offset
            , long maxTimestamp
            , long minTimestamp
            , int length
            , int valueSize
            , int offsetLine
            , int doubleLine
            , AggBucket aggBucket
//            , DoubleIndexMap doubleIndexMap
    ) {
        this.offset = offset;
        this.maxTimestamp = maxTimestamp;
        this.minTimestamp = minTimestamp;
        this.length = length;
        this.valueSize = valueSize;
        this.offsetLine = offsetLine;
        this.doubleLine = doubleLine;
        this.aggBucket = aggBucket;
//        this.doubleIndexMap = doubleIndexMap;
    }

    @Override
    public String toString() {
        return offset +
                "@" +
                maxTimestamp +
                "@" +
                minTimestamp +
                "@" +
                length +
                "@" +
                valueSize +
                "@" +
                offsetLine +
                "@" +
                doubleLine +
                "@" +
                aggBucket.toString()
                ;

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Index index = (Index) o;
        return offset == index.offset && maxTimestamp == index.maxTimestamp && minTimestamp == index.minTimestamp && valueSize == index.valueSize && length == index.length;
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, maxTimestamp, minTimestamp, valueSize, length);
    }

    public int getBigIntOffset() {
        return bigIntOffset;
    }

    public void setBigIntOffset(int offset) {
        this.bigIntOffset = offset;
    }

}
