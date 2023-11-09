package com.alibaba.lindorm.contest.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

public class StaticsUtil {




    public static AtomicLong READ_TIME = new AtomicLong(0);

    public static AtomicLong READ_TIMES = new AtomicLong(0);
    public static AtomicLong READ_FILE_SIZE = new AtomicLong(0);

    public static AtomicLong STRING_TOTAL_LENGTH = new AtomicLong(0);
    public static AtomicLong STRING_COMPRESS_LENGTH = new AtomicLong(0);
    public static AtomicLong LONG_COMPRESS_LENGTH = new AtomicLong(0);
    public static AtomicLong DOUBLE_COMPRESS_LENGTH = new AtomicLong(0);
    public static AtomicLong INT_COMPRESS_LENGTH = new AtomicLong(0);
    public static AtomicLong MAP_COMPRESS_TIME = new AtomicLong(0);

    public static AtomicLong STRING_BYTE_LENGTH = new AtomicLong(0);

    public static AtomicLong STRING_SHORT_LENGTH = new AtomicLong(0);

    public static ArrayList<ColumnInfo> columnInfos = new ArrayList<>(40);
    public static AtomicLong READ_DATA_TIME = new AtomicLong(0);
    public static AtomicLong COMPRESS_DATA_TIME = new AtomicLong(0);

    public static AtomicLong SINGLEVALUE_TOTAL_TIME = new AtomicLong(0);

    public static AtomicLong GET_SINGPLE_VALUE_TIMES = new AtomicLong(0);
    public static AtomicLong GET_VALUE_TIMES = new AtomicLong(0);

    public static AtomicLong TIMERANGE_READ_TIME = new AtomicLong();
    public static AtomicLong TIMERANGE_TOTAL_TIME = new AtomicLong();

    public static AtomicLong TIMERANGE_UNCOMPRESS_TIME = new AtomicLong();
    public static AtomicLong TIMERANGE_UNCOMPRESS_INT_TIME = new AtomicLong();
    public static AtomicLong TIMERANGE_UNCOMPRESS_DOUBLE_TIME = new AtomicLong();
    public static AtomicLong TIMERANGE_UNCOMPRESS_STRING_TIME = new AtomicLong();

    public static AtomicLong WRITE_TOTAL_COST = new AtomicLong();
    public static AtomicLong WRITE_PREPART_TIME = new AtomicLong();
    public static AtomicLong WRITE_COMPRESS_TIME = new AtomicLong();
    public static AtomicLong WRITE_PUT_TIME = new AtomicLong();
    public static AtomicLong WRITE_APPEND_TIME = new AtomicLong();

    public static int MAX_IDLE_BUFFER = Integer.MAX_VALUE;

    public static AtomicLong FIRST_READ_TIME = new AtomicLong(0);
    public static AtomicLong SECOND_READ_TIME = new AtomicLong(0);

    public static Set<String> AGG_QUERY_THREAD = new ConcurrentSkipListSet<>();
    public static Set<String> DOWNSAMPLE_QUERY_THREAD = new ConcurrentSkipListSet<>();

    public static AtomicLong AGG_TOTAL_TIME = new AtomicLong(0);
    public static AtomicLong AGG_TOTAL_READ_FILE_TIME = new AtomicLong(0);
    public static AtomicLong DOWNSAMPLE_TOTAL_TIME = new AtomicLong(0);


    public static AtomicLong TIME_RANGE_READ_FILE_SIZE = new AtomicLong(0);
    public static AtomicLong TIME_RANGE_READ_TIME = new AtomicLong(0);


    public static AtomicLong DOWN_SAMPLE_IOPS = new AtomicLong(0);

    public static long START_COUNT_IOPS = 0;

    public static int MAX_INT = Integer.MIN_VALUE;
    public static int MIN_INT = Integer.MAX_VALUE;

    public static void printCPU() {
        try {
            String line;
            Process process = Runtime.getRuntime().exec("top -b -n1");
            BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((line = input.readLine()) != null) {
                if (line.contains("Cpu(s):")) {
                    // 提取CPU使用率
                    System.out.println(line);
                    break;
                }
            }
            input.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
