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

    public  static synchronized void print(int[] ints) {
        int i = 0;
        for (int anInt : ints) {
            System.out.print(anInt + ",");
            if (i % 35 == 0) {
                System.out.println(SchemaUtil.getIndexArray()[i]);
            }
            i += 1;
        }
        System.out.println("----------------------------------------------------------------------");
    }
}
