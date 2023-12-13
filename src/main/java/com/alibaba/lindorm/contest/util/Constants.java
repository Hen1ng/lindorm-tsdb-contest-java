package com.alibaba.lindorm.contest.util;

public class Constants {
    public static final int OS_PAGE_SIZE = 1024 * 4;

    public static final int TS_FILE_NUMS = 60;
    public static final long TS_FILE_SIZE = 2L * 1024 * 1024 * 1024;
    public static final long WARM_FILE_SIZE = 1024 * 1024 * 1024;
    public static final int LOAD_FILE_TO_MEMORY_NUM = 60;
    public static final int LOAD_TS_FILE_TO_DIRECT_MEMORY_NUM = 5;
    public static final int LOAD_TS_FILE_TO__MEMORY_ARRAY_NUM = 9;

    public static final int COMPRESS_BATCH_SIZE = 5000;


    public static boolean isBigString(int columnIndex) {
        return columnIndex == 59 || columnIndex == 58;
    }
    public static boolean isBigString(String column) {
        return bigStringColumn.equals(column) || bigStringColumn1.equals(column)  ;
    }

    public static String bigStringColumn = "JUBK";
    public static String bigStringColumn1 = "ORNI";

    public static final int CACHE_VINS_LINE_NUMS = 250;
    public static int INT_NUMS;
    public static int FLOAT_NUMS;
    public static int STRING_NUMS;

    public static final int TOTAL_VIN_NUMS = 5000;

    public static final int TOTAL_BUFFER_NUMS = 1200;
    public static final int TOTAL_COMPRESS_NUMS = 1;

    public static final int TOTAL_BUCKET = 0;


    public static final boolean OPEN_DOWNSAMPLE_BYPUCKET_OPT = true;



    // ---------- BIGINT DICT COMPRESS --------------------





    static{
//
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
