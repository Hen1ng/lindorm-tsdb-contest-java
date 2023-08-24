package com.alibaba.lindorm.contest.util;

import com.alibaba.lindorm.contest.compress.BigIntArray;
import com.alibaba.lindorm.contest.compress.DoubleColumnHashMapCompress;
import com.alibaba.lindorm.contest.compress.IntColumnHashMapCompress;
import com.alibaba.lindorm.contest.compress.StringColumnHashMapCompress;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

public class Constants {
    public static final int OS_PAGE_SIZE = 1024 * 4;

    public static final int TS_FILE_NUMS = 30;
    public static final long TS_FILE_SIZE = 2L * 1024 * 1024 * 1024;
    public static final long WARM_FILE_SIZE = 1024 * 1024 * 1024;
    public static final boolean USE_ZIGZAG = false;
    public static final int LOAD_FILE_TO_MEMORY_NUM = 15;

    public static final int CACHE_VINS_LINE_NUMS = 40;
    public static int INT_NUMS;
    public static int FLOAT_NUMS;
    public static int STRING_NUMS;

    public static final int TOTAL_VIN_NUMS = 30000;

    public static final int TOTAL_BUFFER_NUMS = 10000;



    // ---------- BIGINT DICT COMPRESS --------------------


    public static ConcurrentSkipListSet<Integer> YXMSset;

    public static IntColumnHashMapCompress intColumnHashMapCompress;

    public static DoubleColumnHashMapCompress doubleColumnHashMapCompress;
    public static StringColumnHashMapCompress stringColumnHashMapCompress;



    static{
        YXMSset = new ConcurrentSkipListSet<>();
    }

    public static void setIntNums(int intNums) {
        INT_NUMS = intNums;
        System.out.println("Schema int num:" + intNums);
    }

    public static void setFloatNums(int floatNums) {
        FLOAT_NUMS = floatNums;
        System.out.println("Schema floatNums num:" + floatNums);
    }

    public static void setStringNums(int stringNums) {
        STRING_NUMS = stringNums;
        System.out.println("Schema stringNums num:" + stringNums);
    }


}
