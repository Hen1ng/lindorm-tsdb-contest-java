package com.alibaba.lindorm.contest.compress;

import java.nio.ByteBuffer;

public class CompressResult {
    public byte[] compressedData;
    public short[] stringLengthArray;

    public CompressResult(byte[] compressedData, short[] stringLengthArray) {
        this.compressedData = compressedData;
        this.stringLengthArray = stringLengthArray;
    }
}