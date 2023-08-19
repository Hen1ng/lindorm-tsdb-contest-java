package com.alibaba.lindorm.contest.index;


import java.util.List;
import java.util.Objects;

public class Index {

    private long offset;
    private long maxTimestamp;
    private long minTimestamp;

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

    public int valueSize;

    public int latitudeOffset;

    public int longitudeOffset;

    public int yxmsOffset;


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

    public Index(long offset, long maxTimestamp, long minTimestamp, int length, int valueSize, int latitudeOffset, int longitudeOffset, int yxmsOffset) {
        this.offset = offset;
        this.maxTimestamp = maxTimestamp;
        this.minTimestamp = minTimestamp;
        this.length = length;
        this.valueSize = valueSize;
        this.latitudeOffset = latitudeOffset;
        this.longitudeOffset = longitudeOffset;
        this.yxmsOffset = yxmsOffset;
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
                valueSize +
                "," +
                latitudeOffset +
                "," +
                longitudeOffset +
                "," +
                yxmsOffset
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

}
