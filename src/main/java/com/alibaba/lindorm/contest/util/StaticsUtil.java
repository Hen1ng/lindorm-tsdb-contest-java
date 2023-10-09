package com.alibaba.lindorm.contest.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

public class StaticsUtil {
    public static AtomicLong STRING_TOTAL_LENGTH = new AtomicLong(0);
    public static AtomicLong STRING_COMPRESS_LENGTH = new AtomicLong(0);
    public static AtomicLong LONG_COMPRESS_LENGTH = new AtomicLong(0);
    public static AtomicLong DOUBLE_COMPRESS_LENGTH = new AtomicLong(0);
    public static AtomicLong INT_COMPRESS_LENGTH = new AtomicLong(0);
    public static AtomicLong MAP_COMPRESS_TIME = new AtomicLong(0);

    public static AtomicLong STRING_BYTE_LENGTH = new AtomicLong(0);

    public static AtomicLong STRING_SHORT_LENGTH = new AtomicLong(0);

    public static ArrayList<ColumnInfo> columnInfos = new ArrayList<>(40);

    public static int MAX_IDLE_BUFFER = Integer.MAX_VALUE;

    public static int MAX_INT = Integer.MIN_VALUE;
    public static int MIN_INT = Integer.MAX_VALUE;
}
