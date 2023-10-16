package com.alibaba.lindorm.contest.file;

import java.util.Objects;

public class WriteEntryKey {
    private int fileIndex;
    private long maxTimeStamp;

    public WriteEntryKey(int fileIndex, long maxTimeStamp) {
        this.fileIndex = fileIndex;
        this.maxTimeStamp = maxTimeStamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WriteEntryKey that = (WriteEntryKey) o;
        return fileIndex == that.fileIndex && maxTimeStamp == that.maxTimeStamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileIndex, maxTimeStamp);
    }

    public int getFileIndex() {
        return fileIndex;
    }

    public void setFileIndex(int fileIndex) {
        this.fileIndex = fileIndex;
    }

    public long getMaxTimeStamp() {
        return maxTimeStamp;
    }

    public void setMaxTimeStamp(long maxTimeStamp) {
        this.maxTimeStamp = maxTimeStamp;
    }
}
