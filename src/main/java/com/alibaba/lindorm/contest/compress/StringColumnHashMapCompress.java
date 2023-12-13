package com.alibaba.lindorm.contest.compress;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StringColumnHashMapCompress implements  Serializable {
    private static final long serialVersionUID = 1L;

    // 需要压缩的列的数目
    private Integer compressColumnNum = 0;

    // 列名映射到索引
    private HashMap<String,Integer> columnNameToIndexMap;

    // 索引映射到需要的byte数组的大小，因为记录数据需要开一个定长的byte数组,
    private HashMap<Integer,Integer> columnNameToBytesMap;

    // 每一列对应的AtomicInteger，用来映射的单调int
    private AtomicInteger[] autoIncrementArray;

    // 列名
    private String[] columnNames;

    // 从String到int的映射
    private ConcurrentHashMap<ByteBuffer,Integer>[] hashMaps;

    // 反向映射，因为使用index，所以可以使用两个ArrayList
    private ArrayList<ArrayList<ByteBuffer>> hashMapReverses;

    // 记录的最终数据,第一维通过key查找到index，第二维确定插入的具体位置，将这两个暴露出去，就可以进行插入
    public short[][] data;

    // 记录当前第二维度要插入的位置
    public AtomicInteger[] dataIndexs;

    private AtomicInteger[] positionAtomic;



    public int[][] GetTempArray(int lineNum){
        int[][] res = new int[compressColumnNum][];
        for(int i=0;i<compressColumnNum;i++){
            res[i] = new int[lineNum];
        }
        return res;
    }
    public StringColumnHashMapCompress() {
        columnNameToIndexMap = new HashMap<>();
        columnNameToBytesMap = new HashMap<>();
    }

    public void addColumns(String key,int columnBytes){
        columnNameToIndexMap.put(key,compressColumnNum);
        columnNameToBytesMap.put(compressColumnNum,columnBytes);
        compressColumnNum++;
    }

    public int GetColumnIndex(String key){
        return columnNameToIndexMap.get(key);
    }
    public boolean Exist(String columnName){
        return columnNameToIndexMap.containsKey(columnName);
    }

    public void Prepare(){
        hashMaps = new ConcurrentHashMap[compressColumnNum];
        hashMapReverses = new ArrayList<>();
        for(int i=0;i<compressColumnNum;i++){
            hashMapReverses.add(new ArrayList<>());
        }
        data = new short[compressColumnNum][];
        for(int i=0;i<data.length;i++){
            data[i]= new short[columnNameToBytesMap.get(i)];
        }
        autoIncrementArray = new AtomicInteger[compressColumnNum];
        positionAtomic = new AtomicInteger[compressColumnNum];
        for(int i=0;i<compressColumnNum;i++){
            autoIncrementArray[i] = new AtomicInteger(0);
            dataIndexs[i] = new AtomicInteger();
            positionAtomic[i] = new AtomicInteger();
        }
    }

    public int addElement(String column,ByteBuffer element){
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

    public ByteBuffer getElement(String column,int index){
        Integer i = columnNameToIndexMap.get(column);
        return hashMapReverses.get(i).get(index);
    }

    public int GetColumnSize(){
        return columnNameToIndexMap.size();
    }

    public void CompressAndadd(int[][] ints){
        for (int i=0;i<ints.length;i++) {
            int offSet = positionAtomic[i].getAndAdd(ints[i].length);
            for (int j = 0; j < ints[i].length; j++) {
                if (ints[i][j] > 255*255) {
                    throw new IllegalArgumentException("Int value at index " + i + " value :" + ints[i] + "cannot be safely converted to byte");
                }
                data[i][offSet+j] = (short) (ints[i][j] & 0xFFFF);
            }
        }
    }
    public void saveToFile(String dir) {
        if (compressColumnNum == 0) {
            return;
        }
        String filePath = dir + "/StringHashMapCompress.dict";

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
    public static StringColumnHashMapCompress loadFromFile(String dir) {
        StringColumnHashMapCompress obj = null;
        String filePath = dir + "/StringHashMapCompress.dict";
        try (FileInputStream fileIn = new FileInputStream(filePath);
             ObjectInputStream in = new ObjectInputStream(fileIn)) {
            obj = (StringColumnHashMapCompress) in.readObject();
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
