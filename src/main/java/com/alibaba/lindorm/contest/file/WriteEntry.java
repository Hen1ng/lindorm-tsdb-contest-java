package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.index.AggBucket;
import com.alibaba.lindorm.contest.index.Index;

import java.nio.ByteBuffer;

public class WriteEntry {
    private int j;

    private TSFile tsFile;

    private ByteBuffer byteBuffer;

    private Index index;

    public Index getIndex() {
        return index;
    }

    public void setIndex(Index index) {
        this.index = index;
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public void setByteBuffer(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    public WriteEntry(Index index) {
        this.index = index;
    }

    public WriteEntry(TSFile tsFile, ByteBuffer byteBuffer, Index index) {
        this.j = j;
        this.tsFile = tsFile;
        this.byteBuffer = byteBuffer;
        this.index = index;
    }

    public TSFile getTsFile() {
        return tsFile;
    }

    public void setTsFile(TSFile tsFile) {
        this.tsFile = tsFile;
    }

    public int getJ() {
        return j;
    }

    public void setJ(int j) {
        this.j = j;
    }
}
