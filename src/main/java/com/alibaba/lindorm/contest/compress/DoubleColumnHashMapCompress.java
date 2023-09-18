package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.util.RestartUtil;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DoubleColumnHashMapCompress implements Serializable {
    private static final long serialVersionUID = 1L;

    // compress column numbers
    private int compressColumnNum = 0;

    // columnName -> Index
    private final HashMap<String, Integer> columnNameToIndexMap;
    // Index -> need bytes
    private final HashMap<Integer, Integer> columnNameToBytesMap;

    private AtomicInteger[] autoIncrementArray;

    private ConcurrentHashMap<Double, Integer>[] hashMaps;

    private List<double[]> hashMapReverses;

    private Lock[] locks;

    private byte[][] data;

    private AtomicInteger positionAtomic;

    private transient MappedByteBuffer[] mappedByteBuffers;

    private File dataPath;

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


    public DoubleColumnHashMapCompress(File dataPath) {
        columnNameToIndexMap = new HashMap<>();
        columnNameToBytesMap = new HashMap<>();
        this.dataPath = dataPath;
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
        try {
            hashMaps = new ConcurrentHashMap[compressColumnNum];
            positionAtomic = new AtomicInteger();
            this.mappedByteBuffers = new MappedByteBuffer[compressColumnNum];
            for (int i = 0; i < compressColumnNum; i++) {
                final File file = new File(dataPath.getPath() + "/double_column_" + i);
                final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
                mappedByteBuffers[i] = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, columnNameToBytesMap.get(i));
            }
            hashMapReverses = new ArrayList<>(compressColumnNum);
            locks = new ReentrantLock[compressColumnNum];
            for (int i = 0; i < compressColumnNum; i++) {
                locks[i] = new ReentrantLock();
                hashMaps[i] = new ConcurrentHashMap<>();
                hashMapReverses.add(new double[200]);
            }
//            data = new byte[compressColumnNum][];
//            for (int i = 0; i < data.length; i++) {
//                data[i] = new byte[columnNameToBytesMap.get(i)];
//            }
            autoIncrementArray = new AtomicInteger[compressColumnNum];
            for (int i = 0; i < compressColumnNum; i++) {
                autoIncrementArray[i] = new AtomicInteger(0);
            }
        } catch (Exception e) {

        }
    }

    public int addElement(String column, double element) {
        try {
            int i = columnNameToIndexMap.get(column);
            int andAdd;
            try {
                locks[i].lock();
                if (!hashMaps[i].containsKey(element)) {
                    andAdd = autoIncrementArray[i].getAndAdd(1);
                    hashMapReverses.get(i)[andAdd] = element;
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
        if (compressColumnNum == 0) {
            return 0;
        }
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

    public int compressAndAdd2(int[][] ints) {
        try {
            if (compressColumnNum == 0) {
                return -1;
            }
            int offSet = positionAtomic.getAndAdd(ints[0].length);
            for (int i = 0; i < ints.length; i++) {
                assert (ints[i].length == ints[0].length);
                for (int j = 0; j < ints[i].length; j++) {
                    if (ints[i][j] > 255) {
                        throw new IllegalArgumentException("Int value at index " + i + " value :" + ints[i] + "cannot be safely converted to byte");
                    }
                    mappedByteBuffers[i].put(offSet + j, (byte) (ints[i][j] & 0xFF));
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

    public double getElement(String column, int index) {
        try {
            Integer i = columnNameToIndexMap.get(column);
            int b = data[i][index] & 0xFF;
            return hashMapReverses.get(i)[b];
        } catch (IndexOutOfBoundsException e) {
            System.out.println(column + " IntHashMapCompress.getElement outofIndex " + index);
        }
        return -1;
    }

    public double getElement2(String column, int index) {
        try {
            Integer i = columnNameToIndexMap.get(column);
            int b;
            if (RestartUtil.IS_FIRST_START) {
                b = mappedByteBuffers[i].get(index) & 0xFF;
            } else {
                b = data[i][index] & 0xFF;
            }
            return hashMapReverses.get(i)[b];
        } catch (IndexOutOfBoundsException e) {
            System.out.println(column + " IntHashMapCompress.getElement outofIndex " + index);
        }
        return -1;
    }

    public void saveToFile(String dir) {
        if (compressColumnNum == 0) {
            return;
        }
        String filePath = dir + "/DoubleHashMapCompress.dict";
        try (FileOutputStream fileOut = new FileOutputStream(filePath);
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(this);
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    public static DoubleColumnHashMapCompress loadFromFile(String dir) {
        DoubleColumnHashMapCompress obj = null;
        String filePath = dir + "/DoubleHashMapCompress.dict";
        try (FileInputStream fileIn = new FileInputStream(filePath);
             ObjectInputStream in = new ObjectInputStream(fileIn)) {
            obj = (DoubleColumnHashMapCompress) in.readObject();
        } catch (IOException i) {
            i.printStackTrace();
        } catch (ClassNotFoundException c) {
            System.out.println("DoubleColumnHashMapCompress class not found");
            c.printStackTrace();
        }
        if (obj == null) {
            return null;
        }
        byte[][] data = new byte[obj.compressColumnNum][];
        obj.mappedByteBuffers = new MappedByteBuffer[obj.compressColumnNum];
        try {
            for (int i = 0; i < data.length; i++) {
                data[i] = new byte[obj.columnNameToBytesMap.get(i)];
                final File file = new File(obj.dataPath.getPath() + "/double_column_" + i);
                final FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
                obj.mappedByteBuffers[i] = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, obj.columnNameToBytesMap.get(i));
                final MappedByteBuffer mappedByteBuffer = obj.mappedByteBuffers[i];
                for (int integer = 0; integer < obj.columnNameToBytesMap.get(i); integer++) {
                    data[i][integer] = mappedByteBuffer.get();
                }
                file.delete();
            }
            obj.data = data;
            File file = new File(filePath);
            final boolean delete = file.delete();
            System.out.println("delete file " + filePath + " result :" + delete);
            System.out.println("DoubleColumnHashMapCompress data.length" + obj.data.length + "data[0].length" + obj.data[0].length);
            return obj;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("DoubleColumnHashMapCompress error, e" + e.getLocalizedMessage());
        }
        return obj;
    }



}
