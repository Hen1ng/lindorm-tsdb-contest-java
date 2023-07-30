package com.alibaba.lindorm.contest.util;

public class AssertUtil {
    public static void assertTrue(boolean b) {
        if (!b) {
            throw new RuntimeException("exception");
        }
    }

    public static void notNull(Object o) {
        if (o == null) {
            throw new RuntimeException("exception");
        }
    }
}
