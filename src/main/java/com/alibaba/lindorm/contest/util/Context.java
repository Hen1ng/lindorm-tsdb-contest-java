package com.alibaba.lindorm.contest.util;

public class Context {
    private int accessTimes;
    private int hitTimes;

    private long readFileTime;
    private long readFileSize;

    private long getSingleReadFileTime;

    private long getSingleReadFileSize;

    private long getSingleDecompressTime;

    private long hitArray;

    @Override
    public String toString() {
        return "Context{" +
                "accessTimes=" + accessTimes +
                ", hitTimes=" + hitTimes +
                ", readFileTime=" + readFileTime +
                ", readFileSize=" + readFileSize +
                ", getSingleReadFileTime=" + getSingleReadFileTime +
                ", getSingleReadFileSize=" + getSingleReadFileSize +
                ", getSingleDecompressTime=" + getSingleDecompressTime +
                ", hitArray=" + hitArray +
                '}';
    }

    public long getHitArray() {
        return hitArray;
    }

    public void setHitArray(long hitArray) {
        this.hitArray = hitArray;
    }

    public long getReadFileTime() {
        return readFileTime;
    }

    public void setReadFileTime(long readFileTime) {
        this.readFileTime = readFileTime;
    }

    public long getReadFileSize() {
        return readFileSize;
    }

    public void setReadFileSize(long readFileSize) {
        this.readFileSize = readFileSize;
    }

    public long getGetSingleReadFileTime() {
        return getSingleReadFileTime;
    }

    public void setGetSingleReadFileTime(long getSingleReadFileTime) {
        this.getSingleReadFileTime = getSingleReadFileTime;
    }

    public long getGetSingleReadFileSize() {
        return getSingleReadFileSize;
    }

    public void setGetSingleReadFileSize(long getSingleReadFileSize) {
        this.getSingleReadFileSize = getSingleReadFileSize;
    }

    public long getGetSingleDecompressTime() {
        return getSingleDecompressTime;
    }

    public void setGetSingleDecompressTime(long getSingleDecompressTime) {
        this.getSingleDecompressTime = getSingleDecompressTime;
    }

    public Context(int accessTimes, int hitTimes) {
        this.accessTimes = accessTimes;
        this.hitTimes = hitTimes;
    }

    public int getAccessTimes() {
        return accessTimes;
    }

    public void setAccessTimes(int accessTimes) {
        this.accessTimes = accessTimes;
    }

    public int getHitTimes() {
        return hitTimes;
    }

    public void setHitTimes(int hitTimes) {
        this.hitTimes = hitTimes;
    }
    public void addAccessTime(){
        this.accessTimes++;
    }
    public void addHitTime(){
        this.hitTimes++;
    }
}
