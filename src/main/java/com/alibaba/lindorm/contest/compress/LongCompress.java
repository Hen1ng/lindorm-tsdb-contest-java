package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.util.ArrayUtils;
import com.alibaba.lindorm.contest.util.BytesUtil;

import java.nio.ByteBuffer;

public class LongCompress {

    public static byte[] compress(long[] longs) {
        final ByteBuffer allocate = ByteBuffer.allocate(longs.length * 8);
        for (long aLong : longs) {
            allocate.putLong(aLong);
        }
        return allocate.array();
//        long[] output = new long[longs.length];
//        long[] deltaArray = new long[longs.length - 1];
//        for (int i = 0; i < longs.length - 1; i++) {
//            deltaArray[i] = (longs[i] - longs[i + 1]);
//        }
//        final int compress = Simple8.compress(deltaArray, output);
//        byte[] result = new byte[compress * 8];
//        int position = 0;
//        for (int i = 0; i < compress; i++) {
//            final long l = output[i];
//            final byte[] bytes = BytesUtil.long2Bytes(l);
//            ArrayUtils.copy(bytes, 0, result, position, 8);
//            position += 8;
//        }
//        return result;
    }

    public static long[] decompress(byte[] bytes, long previous, int lineNum) {
//        long[] output = new long[bytes.length / 8];
//        int position = 0;
//        for (int i = 0; i < bytes.length; i += 8) {
//            final long l = BytesUtil.bytes2Long(bytes, i);
//            output[position] = l;
//            position++;
//        }
//        final long[] longs = new long[lineNum - 1];
//        final long[] result = new long[lineNum];
//        result[lineNum - 1] = previous;
//        Simple8.decompress(output, 0, bytes.length / 8, longs, 0);
//        for (int j = lineNum - 2; j >= 0; j--) {
//            result[j] = result[j + 1] + longs[j];
//        }
//        return result;
        long[] result = new long[lineNum];
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            for (int i = 0; i < lineNum; i++) {
                result[i] = byteBuffer.getLong();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void main(String[] args) {
        long[] longs = new long[]{
                9000, 8000, 7000, 6000,5000,4000,3000,2000,1000,0
        };
        final byte[] compress = LongCompress.compress(longs);
        final long[] decompress = LongCompress.decompress(compress, 0, 10);

    }
}
