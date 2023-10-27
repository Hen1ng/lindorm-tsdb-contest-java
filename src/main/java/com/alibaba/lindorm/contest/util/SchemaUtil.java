package com.alibaba.lindorm.contest.util;

import com.alibaba.lindorm.contest.structs.ColumnValue;
import com.alibaba.lindorm.contest.structs.Schema;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SchemaUtil {

    private static final TreeMap<String, ColumnValue.ColumnType> INT_MAP = new TreeMap<>();
    private static final TreeMap<String, ColumnValue.ColumnType> STRING_MAP = new TreeMap<>();
    private static final TreeMap<String, ColumnValue.ColumnType> FLOAT_MAP = new TreeMap<>();
    public static final Map<String, Integer> COLUMNS_INDEX = new ConcurrentHashMap<>(60);
    private static final String[] INDEX_ARRAY = new String[60];
    public static final int[] COLUMNS_INDEX_ARRAY = new int[60];
    public static final Map<String, Set<Integer>> maps = new ConcurrentHashMap();


    public static Schema getSchema() {
        return schema1;
    }

    public static void setSchema(Schema schema) {
        schema1 = schema;
        for (String key : schema.getColumnTypeMap().keySet()) {
            final ColumnValue.ColumnType columnType = schema.getColumnTypeMap().get(key);
            if (ColumnValue.ColumnType.COLUMN_TYPE_INTEGER.equals(columnType)) {
                INT_MAP.put(key, columnType);
            } else if (ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT.equals(columnType)) {
                FLOAT_MAP.put(key, columnType);
            } else {
                STRING_MAP.put(key, columnType);
            }
        }
        Constants.setFloatNums(FLOAT_MAP.size());
        Constants.setIntNums(INT_MAP.size());
        Constants.setStringNums(STRING_MAP.size());
        int i = 0;
        for (String key : INT_MAP.keySet()) {
            INDEX_ARRAY[i] = key;
            COLUMNS_INDEX.put(key, i);
            System.out.println("key: " + key + " index : " + i);
            i++;
        }
        for (String key : FLOAT_MAP.keySet()) {
            INDEX_ARRAY[i] = key;
            COLUMNS_INDEX.put(key, i);
            System.out.println("key: " + key + " index : " + i);
            i++;
        }
        for (String key : STRING_MAP.keySet()) {
            if ("JUBK".equals(key) || "LIYD".equals(key)) {
                continue;
            }
            INDEX_ARRAY[i] = key;
            COLUMNS_INDEX.put(key, i);
            System.out.println("key: " + key + " index : " + i);
            i++;
        }
        INDEX_ARRAY[i] = "LIYD";
        COLUMNS_INDEX.put("LIYD", i);
        System.out.println("key: " + "LIYD" + " index : " + i);
        i++;
        INDEX_ARRAY[i] = "JUBK";
        COLUMNS_INDEX.put("JUBK", i);
        System.out.println("key: " + "JUBK" + " index : " + i);
        for (String s : INDEX_ARRAY) {
            maps.put(s, new HashSet<>());
        }
        int j = 0;
        for (String key : schema.getColumnTypeMap().keySet()) {
            COLUMNS_INDEX_ARRAY[j] = COLUMNS_INDEX.get(key);
            j++;
        }
    }


    private static Schema schema1;

    public static String[] getIndexArray() {
        return INDEX_ARRAY;
    }

    public static int getIndexByColumn(String key) {
        return COLUMNS_INDEX.get(key);
    }

    public static void saveMapToFile(File file) {
        try {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (String field : COLUMNS_INDEX.keySet()) {
                    writer.write(field);
                    writer.write(":");
                    writer.write(COLUMNS_INDEX.get(field) + "-" + schema1.getColumnTypeMap().get(field));
                    writer.newLine();
                }
            }
        } catch (Exception e) {
            System.out.println("saveMapToFile error, e" + e);
        }
    }

    public static void loadMapFromFile(File file)
            throws IOException {
        Map<String, ColumnValue.ColumnType> map = new HashMap<>();
        int intNums = 0;
        int stringNums = 0;
        int doubleNums = 0;
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String[] split = line.split(":");
                final String[] split1 = split[1].split("-");
                COLUMNS_INDEX.put(split[0], Integer.parseInt(split1[0]));
                INDEX_ARRAY[Integer.parseInt(split1[0])] = split[0];
                if ("COLUMN_TYPE_STRING".equals(split1[1])) {
                    stringNums++;
                    map.put(split[0], ColumnValue.ColumnType.COLUMN_TYPE_STRING);
                } else if ("COLUMN_TYPE_INTEGER" .equals(split1[1])) {
                    intNums++;
                    map.put(split[0], ColumnValue.ColumnType.COLUMN_TYPE_INTEGER);
                } else {
                    doubleNums++;
                    map.put(split[0], ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT);
                }
            }
        }
        if (schema1 == null) {
            schema1 = new Schema(map);
            Constants.setStringNums(stringNums);
            Constants.setFloatNums(doubleNums);
            Constants.setIntNums(intNums);
        }
        int j = 0;
        for (String key : schema1.getColumnTypeMap().keySet()) {
            COLUMNS_INDEX_ARRAY[j] = COLUMNS_INDEX.get(key);
            j++;
        }
        file.delete();
    }
}
