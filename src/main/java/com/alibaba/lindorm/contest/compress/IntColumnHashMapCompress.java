package com.alibaba.lindorm.contest.compress;

import java.io.*;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class IntColumnHashMapCompress implements Serializable {
    private static final long serialVersionUID = 1L;

    // compress column numbers
    private int compressColumnNum = 0;

    // columnName -> Index
    private final HashMap<String, Integer> columnNameToIndexMap;
    // Index -> need bytes
    private final HashMap<Integer, Integer> columnNameToBytesMap;

    private AtomicInteger[] autoIncrementArray;

    private ConcurrentHashMap<Integer, Integer>[] hashMaps;

    private int[][] hashMapReverses;

    private Lock[] locks;

    private byte[][] data;

    private AtomicInteger positionAtomic;

    public HashMap<String, Integer> getColumnNameToIndexMap() {
        return columnNameToIndexMap;
    }

    public int[][] getTempArray(int lineNum) {
        int[][] res = new int[compressColumnNum][];
        for (int i = 0; i < compressColumnNum; i++) {
            res[i] = new int[lineNum];
        }
        return res;
    }


    public IntColumnHashMapCompress() {
        columnNameToIndexMap = new HashMap<>();
        columnNameToBytesMap = new HashMap<>();
    }

    public void addColumns(String key, int columnBytes) {
        columnNameToIndexMap.put(key, compressColumnNum);
        columnNameToBytesMap.put(compressColumnNum, columnBytes);
        compressColumnNum++;
    }

    public boolean exist(String columnName) {
        return columnNameToIndexMap.containsKey(columnName);
    }

    public void prepare() {
        hashMaps = new ConcurrentHashMap[compressColumnNum];
        positionAtomic = new AtomicInteger();
        hashMapReverses = new int[compressColumnNum][];
        locks = new ReentrantLock[compressColumnNum];
        for (int i = 0; i < compressColumnNum; i++) {
            locks[i] = new ReentrantLock();
            hashMaps[i] = new ConcurrentHashMap<>();
            hashMapReverses[i] = new int[120];
        }
        data = new byte[compressColumnNum][];
        for (int i = 0; i < data.length; i++) {
            data[i] = new byte[columnNameToBytesMap.get(i)];
        }
        autoIncrementArray = new AtomicInteger[compressColumnNum];
        for (int i = 0; i < compressColumnNum; i++) {
            autoIncrementArray[i] = new AtomicInteger(0);
        }
    }

    public int addElement(String column, Integer element) {
        try {
            Integer i = columnNameToIndexMap.get(column);
            int andAdd;
            try {
                locks[i].lock();
                if (!hashMaps[i].containsKey(element)) {
                    andAdd = autoIncrementArray[i].getAndAdd(1);
                    hashMapReverses[i][andAdd] = element;
                    hashMaps[i].put(element, andAdd);
                } else {
                    andAdd = hashMaps[i].get(element);
                }
            } finally {
                locks[i].unlock();
            }
            return andAdd;
        } catch (Exception e) {
            System.out.println("IntColumnHashMapCompress error, e" + e);
        }
        return -1;
    }

    public int getColumnIndex(String key) {
        return columnNameToIndexMap.get(key);
    }

    public int getColumnSize() {
        return columnNameToIndexMap.size();
    }

    public int compressAndAdd(int[][] ints) {
        try {
            int offSet = positionAtomic.getAndAdd(ints[0].length);
            for (int i = 0; i < ints.length; i++) {
                assert (ints[i].length == ints[0].length);
                for (int j = 0; j < ints[i].length; j++) {
                    if (ints[i][j] > 255) {
                        throw new IllegalArgumentException("Int value at index " + i + " value :" + ints[i] + "cannot be safely converted to byte");
                    }
                    data[i][offSet + j] = (byte) (ints[i][j] & 0xFF);
                }
            }
            return offSet;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("CompressAndadd error, e" + e);
            System.exit(1);
        }
        return -1;
    }

    public Integer getElement(String column, int index) {
        try {
            Integer i = columnNameToIndexMap.get(column);
            int b = data[i][index] & 0xFF;
            return hashMapReverses[i][b];
        } catch (IndexOutOfBoundsException e) {
            System.out.println(column + " IntHashMapCompress.getElement outofIndex " + index);
        }
        return -1;
    }

    public void saveToFile(String dir) {
        String filePath = dir + "/IntHashMapCompress.txt";
        try (FileOutputStream fileOut = new FileOutputStream(filePath);
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(this);
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    public static IntColumnHashMapCompress loadFromFile(String dir) {
        IntColumnHashMapCompress obj = null;
        String filePath = dir + "/IntHashMapCompress.txt";
        try (FileInputStream fileIn = new FileInputStream(filePath);
             ObjectInputStream in = new ObjectInputStream(fileIn)) {
            obj = (IntColumnHashMapCompress) in.readObject();
        } catch (IOException i) {
            i.printStackTrace();
        } catch (ClassNotFoundException c) {
            System.out.println("ColumnHashMapCompress class not found");
            c.printStackTrace();
        }
        File file = new File(filePath);
        final boolean delete = file.delete();
        System.out.println("delete file " + filePath + " result :" + delete);
        System.out.println("IntColumnHashMapCompress data.length" + obj.data.length + "data[0].length" + obj.data[0].length);
        return obj;
    }


}
