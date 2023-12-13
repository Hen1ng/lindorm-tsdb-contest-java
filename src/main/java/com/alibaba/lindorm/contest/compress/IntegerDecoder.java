package com.alibaba.lindorm.contest.compress;

import java.nio.ByteBuffer;

public class IntegerDecoder {
    private static final byte INT_COMPRESSED_RLE = 2;
    private static final byte INT_UNCOMPRESSED = 0;

    private static final byte INT_COMPRESSED_SIMPLE =1;
    private byte encodingType;

    private byte[] bytes;

    public int[] decompress(byte[] decompressBytes){
        encodingType = (byte) (decompressBytes[0] >> 4);
        switch (encodingType){
            case INT_UNCOMPRESSED:
                return decodeUncompressed(decompressBytes);
            case INT_COMPRESSED_RLE:
                break;
            case INT_COMPRESSED_SIMPLE:
                break;
        }
        return new int[1];
    }
    private int[] decodeRLE(byte[] decompressBytes){
        if(decompressBytes.length==0){
            return null;
        }
        return null;
    }
    private int[] decodeUncompressed(byte[] decompressBytes){
        int i = 1;
        ByteBuffer wrap = ByteBuffer.wrap(decompressBytes);
        int[] results = new int[(decompressBytes.length-1)/4];
        wrap.get(); // delete first
        for(int j=0;j<results.length;j++){
            results[j] = wrap.getInt();
        }
        return results;
    }
}
