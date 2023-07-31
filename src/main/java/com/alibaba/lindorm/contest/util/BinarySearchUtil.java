package com.alibaba.lindorm.contest.util;

import java.nio.ByteBuffer;
import java.util.Random;

public class BinarySearchUtil {

    public static int binarySearch(ByteBuffer timestampBuffer, long target, int low, int high) {
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (timestampBuffer.getLong(mid * 8) > target) {
                high = mid - 1;
            } else if (timestampBuffer.getLong(mid * 8) < target) {
                low = mid + 1;
            } else {
                return mid ;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(40 * 8);
        Random random = new Random();
        byteBuffer.putLong(4);
        for (int i = 1; i < 40; i++) {
            byteBuffer.putLong(i * 8, random.nextLong());
        }
        long start = System.currentTimeMillis();
        System.out.println(binarySearch(byteBuffer, 4, 0, 40));
        System.out.println("cost:" + (System.currentTimeMillis() - start));
    }


}
