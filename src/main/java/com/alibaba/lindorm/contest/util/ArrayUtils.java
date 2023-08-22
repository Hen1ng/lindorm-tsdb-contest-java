package com.alibaba.lindorm.contest.util;

import com.alibaba.lindorm.contest.structs.Schema;

public class ArrayUtils {
    public static boolean equals(byte[] a, int aStart, int aEnd, byte[] b) {
        if (aEnd - aStart != b.length) {
            return false;
        }
        for (int i = 0, j = aStart; i < b.length && j < aEnd; i++, j++) {
            if (a[j] != b[i]) {
                return false;
            }
        }
        return true;
    }
    
    public static void copy(byte[] source, int srcPos, byte[] des, int desPos, int length) {
        UnsafeUtil.getUnsafe().copyMemory(source, 16 + srcPos, des, 16 + desPos, length);
    }

    public static synchronized void printDouble(double[] doubles) {
        int i = 0;
        for (double aDouble : doubles) {
            if (i % Constants.CACHE_VINS_LINE_NUMS == 0) {
                System.out.println();
                int index = i / Constants.CACHE_VINS_LINE_NUMS;
                System.out.println(SchemaUtil.getIndexArray()[index + 45]);
            }
            System.out.print(aDouble + ",");
            i++;
        }
        System.out.println("-------------------------------------------------------");

    }
}
