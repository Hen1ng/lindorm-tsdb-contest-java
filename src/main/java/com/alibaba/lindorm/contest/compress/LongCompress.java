package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.util.ArrayUtils;
import com.alibaba.lindorm.contest.util.BytesUtil;
import com.alibaba.lindorm.contest.util.Constants;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class LongCompress {

    public static final ThreadLocal<long[]> LONG_ARRAY_BUFFER = ThreadLocal.withInitial(() -> new long[Constants.CACHE_VINS_LINE_NUMS]);
    public static byte[] compress(long[] longs) {
        long[] output = LONG_ARRAY_BUFFER.get();
        Arrays.fill(output, 0);
        long[] deltaArray = new long[longs.length - 1];
        for (int i = 0; i < longs.length - 1; i++) {
            deltaArray[i] = (longs[i] - longs[i + 1]) / 1000;
        }
        final int compress = Simple8.compress(deltaArray, output);
        byte[] result = new byte[compress * 8];
        int position = 0;
        for (int i = 0; i < compress; i++) {
            final long l = output[i];
            final byte[] bytes = BytesUtil.long2Bytes(l);
            ArrayUtils.copy(bytes, 0, result, position, 8);
            position += 8;
        }
        return result;
    }

    public static long[] decompress(byte[] bytes, long previous, int lineNum) {
        long[] output = new long[bytes.length / 8];
        int position = 0;
        for (int i = 0; i < bytes.length; i += 8) {
            final long l = BytesUtil.bytes2Long(bytes, i);
            output[position] = l;
            position++;
        }
        final long[] longs = new long[lineNum - 1];
        final long[] result = new long[lineNum];
        result[lineNum - 1] = previous;
        Simple8.decompress(output, 0, bytes.length / 8, longs, 0);
        for (int j = lineNum - 2; j >= 0; j--) {
            result[j] = result[j + 1] + longs[j] * 1000;
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
