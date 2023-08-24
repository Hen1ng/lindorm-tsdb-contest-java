package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.util.Constants;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DoubleColumnHashMapCompress implements  Serializable {
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

    private ConcurrentHashMap<Double,Integer>[] hashMaps;

    private ArrayList<ArrayList<Double>> hashMapReverses;

    private AtomicInteger[] positionAtomic;


    private byte[][] data;

    public int[][] GetTempArray(int lineNum){
        int[][] res = new int[compressColumnNum][];
        for(int i=0;i<compressColumnNum;i++){
            res[i] = new int[lineNum];
        }
        return res;
    }
    public void CompressAndadd(int[][] ints){
        for (int i=0;i<ints.length;i++) {
            int offSet = positionAtomic[i].getAndAdd(ints[i].length);
            for (int j = 0; j < ints[i].length; j++) {
                if (ints[i][j] > 255) {
                    throw new IllegalArgumentException("Int value at index " + i + " value :" + ints[i] + "cannot be safely converted to byte");
                }
                data[i][offSet+j] = (byte) (ints[i][j] & 0xFF);
            }
        }
    }

    public DoubleColumnHashMapCompress() {
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
        hashMaps = new ConcurrentHashMap[compressColumnNum];
        hashMapReverses = new ArrayList<>();
        for(int i=0;i<compressColumnNum;i++){
            hashMapReverses.add(new ArrayList<>());
            hashMaps[i] = new ConcurrentHashMap<>();
        }
        data = new byte[compressColumnNum][];
        for(int i=0;i<data.length;i++){
            data[i]= new byte[columnNameToBytesMap.get(i)];
        }
        autoIncrementArray = new AtomicInteger[compressColumnNum];
        positionAtomic = new AtomicInteger[compressColumnNum];
        for(int i=0;i<compressColumnNum;i++){
            autoIncrementArray[i] = new AtomicInteger(0);
            positionAtomic[i] = new AtomicInteger(0);
        }
    }
    public int GetColumnIndex(String key){
        return columnNameToIndexMap.get(key);
    }

    public int GetColumnSize(){
        return columnNameToIndexMap.size();
    }

    public int addElement(String column,Double element){
        Integer i = columnNameToIndexMap.get(column);
        int andAdd;
        if(!hashMaps[i].containsKey(element)) {
            andAdd = autoIncrementArray[i].getAndAdd(1);
            hashMapReverses.get(i).add(element);
            hashMaps[i].put(element,andAdd);
        }else{
            andAdd =  hashMaps[i].get(element);
        }
        return andAdd;
    }


    public Double getElement(String column,int index){
        Integer i = columnNameToIndexMap.get(column);
        int b = data[i][index] & 0XFF;
        return hashMapReverses.get(i).get(b);
    }

    public void saveToFile(String dir) {
        String filePath = dir + "/DoubleHashMapCompress.dict";
        try (FileOutputStream fileOut = new FileOutputStream(filePath);
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(this);
        } catch (IOException i) {
            i.printStackTrace();
        }
    }
    public HashMap<String,Integer> GetcolumnNameToIndexMap(){
        return columnNameToIndexMap;
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
            System.out.println("ColumnHashMapCompress class not found");
            c.printStackTrace();
        }
        File file = new File(filePath);
        final boolean delete = file.delete();
        System.out.println("delete file "+ filePath + " result :" + delete);
        return obj;
    }



}
