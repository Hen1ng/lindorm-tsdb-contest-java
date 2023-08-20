package com.alibaba.lindorm.contest.util;

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

    public static void printDouble(double[] doubles) {
        for (double aDouble : doubles) {
            System.out.print(aDouble + ",");
        }
        System.out.println("-------------------------------------------------------");

    }
}
