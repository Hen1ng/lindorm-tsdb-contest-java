package com.alibaba.lindorm.contest.compress;

import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BigIntArray {

    public byte[] data;
    private static int byteToInt(byte b) {
        return b & 0xFF;
    }
    public AtomicInteger position;
    public BigIntArray(){
        data = new byte[2*3600*30000];
        position = new AtomicInteger(0);
    }

    public void add(byte value){
        data[position.getAndAdd(1)] = value;
    }
    public int get(int index){
        return byteToInt(data[index]);
    }

    public void savaToFile(File file){
        GzipCompress gzipCompress = new GzipCompress();
        byte[] compress = gzipCompress.compress(data);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(compress);
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception as needed
        }
    }
    public void loadFromFile(File file) {
        GzipCompress gzipCompress = new GzipCompress();
        byte[] compressedData;

        // Read compressed data from the file
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }

            compressedData = bos.toByteArray();
        } catch (IOException e) {
            System.out.println("loadFromFile error, e: " + e);

            // Handle the exception as needed
            return; // Or handle it in another way you prefer
        }

        // Decompress the data
        this.data = gzipCompress.deCompress(compressedData);
    }

}
