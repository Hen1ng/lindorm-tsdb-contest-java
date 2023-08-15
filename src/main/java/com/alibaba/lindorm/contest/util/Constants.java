package com.alibaba.lindorm.contest.util;

import java.util.ArrayList;
import java.util.List;

public class Constants {
    public static final int OS_PAGE_SIZE = 1024 * 4;

    public static final int TS_FILE_NUMS = 30;
    public static final long TS_FILE_SIZE = 2L * 1024 * 1024 * 1024;
    public static final long WARM_FILE_SIZE = 1024 * 1024 * 1024;
    public static final boolean USE_ZIGZAG = false;
    public static final int LOAD_FILE_TO_MEMORY_NUM = 15;

    public static final int CACHE_VINS_LINE_NUMS = 35;
    public static final List<String> SPARSE_COLUMN_INDEX = new ArrayList<>();
    public static int SPARSE_COLUMN_NUM;

    static {
        SPARSE_COLUMN_INDEX.add("FDJGZLB");
        SPARSE_COLUMN_INDEX.add("DW");
        SPARSE_COLUMN_NUM = SPARSE_COLUMN_INDEX.size();
    }

    public static int INT_NUMS;
    public static int FLOAT_NUMS;
    public static int STRING_NUMS;

    public static final int TOTAL_VIN_NUMS = 30000;

    public static final int TOTAL_BUFFER_NUMS = 10000;

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
