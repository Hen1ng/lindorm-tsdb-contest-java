package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.util.Constants;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class IntColumnHashMapCompress implements  Serializable {
    private static final long serialVersionUID = 1L;

    // compress column numbers
    private Integer compressColumnNum = 0;

    // columnName -> Index
    private HashMap<String,Integer> columnNameToIndexMap;
    // Index -> need bytes
    private HashMap<Integer,Integer> columnNameToBytesMap;

    private AtomicInteger[] autoIncrementArray;

    // compress column names
    private String[] columnNames;

    private ConcurrentHashMap<Integer,Integer>[] hashMaps;

    private ArrayList<ArrayList<Integer>> hashMapReverses;

    private byte[][] data;

    private AtomicInteger positionAtomic;

    public HashMap<String,Integer> GetcolumnNameToIndexMap(){
        return columnNameToIndexMap;
    }

    public int[][] GetTempArray(int lineNum){
        int[][] res = new int[compressColumnNum][];
        for(int i=0;i<compressColumnNum;i++){
            res[i] = new int[lineNum];
        }
        return res;
    }


    public IntColumnHashMapCompress() {
        columnNameToIndexMap = new HashMap<>();
        columnNameToBytesMap = new HashMap<>();
    }

    public void addColumns(String key,int columnBytes){
        columnNameToIndexMap.put(key,compressColumnNum);
        columnNameToBytesMap.put(compressColumnNum,columnBytes);
        compressColumnNum++;
    }
    public boolean Exist(String columnName){
        return columnNameToIndexMap.containsKey(columnName);
    }

    public void Prepare(){
        columnNames = new String[compressColumnNum];
        hashMaps = new ConcurrentHashMap[compressColumnNum];
        positionAtomic = new AtomicInteger();
        hashMapReverses = new ArrayList<>();
        for(int i=0;i<compressColumnNum;i++){
            hashMaps[i] = new ConcurrentHashMap<>();
            hashMapReverses.add(new ArrayList<>(Collections.nCopies(120, 0)));
        }
        data = new byte[compressColumnNum][];
        for(int i=0;i<data.length;i++){
            data[i]= new byte[columnNameToBytesMap.get(i)];
        }
        autoIncrementArray = new AtomicInteger[compressColumnNum];
        for(int i=0;i<compressColumnNum;i++){
            autoIncrementArray[i] = new AtomicInteger(0);
        }
    }

    public int addElement(String column,Integer element){
        Integer i = columnNameToIndexMap.get(column);
        int andAdd;
        if(!hashMaps[i].containsKey(element)) {
            andAdd = autoIncrementArray[i].getAndAdd(1);
            hashMapReverses.get(i).set(andAdd,element);
            hashMaps[i].put(element,andAdd);
        }else{
            andAdd =  hashMaps[i].get(element);
        }
        return andAdd;
    }
    public int GetColumnIndex(String key){
        return columnNameToIndexMap.get(key);
    }
    public int GetColumnSize(){
        return columnNameToIndexMap.size();
    }

    public int CompressAndadd(int[][] ints){
        int offSet = positionAtomic.getAndAdd(ints[0].length);
        for (int i=0;i<ints.length;i++) {
            assert(ints[i].length == ints[0].length);
            for (int j = 0; j < ints[i].length; j++) {
                if (ints[i][j] > 255) {
                    throw new IllegalArgumentException("Int value at index " + i + " value :" + ints[i] + "cannot be safely converted to byte");
                }
                data[i][offSet+j] = (byte) (ints[i][j] & 0xFF);
            }
        }
        return offSet;
    }

    public Integer getElement(String column,int index){
        try {
            Integer i = columnNameToIndexMap.get(column);
            int b = data[i][index] & 0xFF;
            return hashMapReverses.get(i).get(b);
        }catch (IndexOutOfBoundsException e){
            System.out.println(column+ " IntHashMapCompress.getElement outofIndex " + index );
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
        System.out.println("delete file "+ filePath + " result :" + delete);
        return obj;
    }



}
