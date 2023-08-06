package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.util.ArrayUtils;
import com.alibaba.lindorm.contest.util.ZigZagUtil;

public class IntCompress {

    public static byte[] compress(int[] ints) {
        int[] deltaArray = new int[ints.length];
        deltaArray[0] = ints[0];
        for (int i = 1; i < ints.length; i++) {
            deltaArray[i] = ints[i] - ints[i - 1];
        }
        byte[] temp = new byte[4 * ints.length];
        int position = 0;
        for (int delta : deltaArray) {
            int i = ZigZagUtil.intToZigZag(delta);
            int length = ZigZagUtil.writeVarint32(i, temp, position);
            position += length;
        }
        byte[] result = new byte[position];
        ArrayUtils.copy(temp, 0, result, 0, position);
        return result;
    }
//
    public static int[] decompress(byte[] bytes) {
        return null;
    }
}
