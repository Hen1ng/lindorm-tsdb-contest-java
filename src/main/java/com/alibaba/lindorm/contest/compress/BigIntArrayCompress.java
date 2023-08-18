package com.alibaba.lindorm.contest.compress;

import java.nio.ByteBuffer;

public class BigIntArrayCompress {
    public static byte[] compress(int []ints){
        byte[] bytes = new byte[ints.length];
        ByteBuffer allocate = ByteBuffer.allocate(ints.length);
        for (int anInt : ints) {
            assert (anInt < 128);
            allocate.put((byte) anInt);
        }
        byte[] array = allocate.array();
        GzipCompress gzipCompress = new GzipCompress();
        return gzipCompress.compress(array);
    }

    private static int byteToInt(byte b) {
        return b & 0xFF;
    }
    public static int[] decompress(byte[] bytes){
        GzipCompress gzipCompress = new GzipCompress();
        bytes = gzipCompress.deCompress(bytes);
        int[] result = new int[bytes.length];
        for(int i=0;i<bytes.length;i++){
            result[i] = byteToInt(bytes[i]);
        }
        return result;
    }
}
