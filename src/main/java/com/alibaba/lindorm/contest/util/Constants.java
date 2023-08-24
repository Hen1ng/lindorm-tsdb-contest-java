package com.alibaba.lindorm.contest.util;

import com.alibaba.lindorm.contest.compress.BigIntArray;

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
    public static final int LOAD_FILE_TO_MEMORY_NUM = 18;

    public static final int CACHE_VINS_LINE_NUMS = 45;
    public static int INT_NUMS;
    public static int FLOAT_NUMS;
    public static int STRING_NUMS;

    public static final int TOTAL_VIN_NUMS = 30000;

    public static final int TOTAL_BUFFER_NUMS = 10000;

    public static final List<String> BIGINT_COLUMN_INDEX;


    // ---------- BIGINT DICT COMPRESS --------------------
    public static int BIGINT_COLUMN_NUM;

    public static ConcurrentHashMap<Integer,Integer> IntCompressMap;
    public static ConcurrentHashMap<Integer,Integer> IntCompressMapReverse;

    public static  AtomicInteger INT_NUMBER_INDEX;

    public static BigIntArray bigIntArray;

    public static ConcurrentSkipListSet<Integer> YXMSset;
    public static ConcurrentSkipListSet<Integer> DCDCset;
    public static ConcurrentSkipListSet<Integer> RLDCRLXHLSet;
    public static ConcurrentSkipListSet<Integer> LJLCSet;
    public static ConcurrentSkipListSet<Integer> DCDTDYZGZSet;
    public static ConcurrentSkipListSet<Integer> RLDCDYSet;
    public static ConcurrentSkipListSet<Double> DJKZQDLSet = new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<Double> DJKZQDYSet = new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<Double> JYDZSet    = new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<Double> QDDJGSSet  = new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<Double> QDDJKZWDSet= new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<Double> QDDJWDSet  = new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<Double> QDDJXHSet  = new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<Double> QDDJZJSet  = new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<Double> QDDJZSSet  = new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<String> CSSet  = new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<String> DCDTDYZDZSet  = new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<String> KCDCNZZGZDMLBSet  = new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<String> QDDJZTSet  = new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<String> QZZSSet  = new ConcurrentSkipListSet<>();
    public static ConcurrentSkipListSet<String> RLDCTZGSSet  = new ConcurrentSkipListSet<>();

    public static void saveBigIntMapToFile(File file) {
        try {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (Integer key : IntCompressMap.keySet()) {
                    writer.write(String.valueOf(key));
                    writer.write(":");
                    writer.write(String.valueOf(IntCompressMap.get(key)));
                    writer.newLine();
                }
            }
        } catch (Exception e) {
            System.out.println("saveMapToFile error, e" + e);
        }
    }

    public static void loadBigIntMapFromFile(File file) {
        try {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":");
                    if (parts.length != 2) {
                        System.out.println("Invalid line format: " + line);
                        continue;
                    }
                    Integer key = Integer.parseInt(parts[0].trim());
                    Integer value = Integer.parseInt(parts[1].trim());

                    IntCompressMap.put(key, value);
                    IntCompressMapReverse.put(value, key);
                }
            }
        } catch (IOException e) {
            System.out.println("loadMapFromFile error, e: " + e);
        } catch (NumberFormatException nfe) {
            System.out.println("Error parsing number: " + nfe.getMessage());
        }
    }

    static{
        BIGINT_COLUMN_INDEX = new ArrayList<>();
        BIGINT_COLUMN_INDEX.add("LATITUDE");
        BIGINT_COLUMN_INDEX.add("LONGITUDE");
        BIGINT_COLUMN_NUM = BIGINT_COLUMN_INDEX.size();
        INT_NUMBER_INDEX = new AtomicInteger(0);
        IntCompressMap = new ConcurrentHashMap<>();
        IntCompressMapReverse = new ConcurrentHashMap<>();
        bigIntArray = new BigIntArray();
        YXMSset = new ConcurrentSkipListSet<>();
        RLDCRLXHLSet = new ConcurrentSkipListSet<>();
        DCDCset = new ConcurrentSkipListSet<>();
        LJLCSet = new ConcurrentSkipListSet<>();
        DCDTDYZGZSet = new ConcurrentSkipListSet<>();
        RLDCDYSet = new ConcurrentSkipListSet<>();
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
