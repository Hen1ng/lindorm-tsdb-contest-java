package com.alibaba.lindorm.contest.compress;

public class doubleCompressResult{
    byte[] data;
    byte[] header;

    public doubleCompressResult(byte[] data, byte[] header) {
        this.data = data;
        this.header = header;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte[] getHeader() {
        return header;
    }

    public void setHeader(byte[] header) {
        this.header = header;
    }
}