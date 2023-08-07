package com.alibaba.lindorm.contest.util;

import java.util.concurrent.atomic.AtomicLong;

public class StateUtil {
    public static AtomicLong STRING_TOTAL_LENGTH = new AtomicLong(0);
    public static AtomicLong STRING_COMPRESS_LENGTH = new AtomicLong(0);
    public static AtomicLong LONG_COMPRESS_LENGTH = new AtomicLong(0);
    public static AtomicLong DOUBLE_COMPRESS_LENGTH = new AtomicLong(0);
    public static AtomicLong INT_COMPRESS_LENGTH = new AtomicLong(0);

    public static int MAX_INT = Integer.MIN_VALUE;
    public static int MIN_INT = Integer.MAX_VALUE;
}
