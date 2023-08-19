package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.file.TSFileService;
import com.alibaba.lindorm.contest.util.ArrayUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BigIntArrayNew {

    public static byte[] LONGITUDE_BYTES = new byte[3600 * 3000];
    public static byte[] LATITUDE_BYTES = new byte[3600 * 3000];
    public static byte[] YXMS_BYTES = new byte[3600 * 3000];

    public static AtomicInteger longitudePosition = new AtomicInteger(0);
    public static AtomicInteger latitudePosition = new AtomicInteger(0);
    public static AtomicInteger yxmsPosition = new AtomicInteger(0);

    public static AtomicInteger longitudeMappingIndex= new AtomicInteger(0);
    public static AtomicInteger latitudeMappingIndex = new AtomicInteger(0);
    public static AtomicInteger yxmsMappingIndex = new AtomicInteger(0);

    public static Map<Integer, Byte> longitudeMap = new HashMap<>(100);
    public static Map<Integer,Byte> latitudeMap = new HashMap<>(100);
    public static Map<Integer,Byte> yxmsMap = new HashMap<>(100);

    public static Map<Byte, Integer> longitudeMapReverse = new HashMap<>(100);
    public static Map<Byte,Integer> latitudeMapReverse = new HashMap<>(100);
    public static Map<Byte,Integer> yxmsMapReverse = new HashMap<>(100);

    public static int copyLongitude(byte[] bytes) {
        final int andAdd = longitudePosition.getAndAdd(bytes.length);
        ArrayUtils.copy(bytes, 0, LONGITUDE_BYTES, andAdd, bytes.length);
        return andAdd;
    }

    public static int copyLatitude(byte[] bytes) {
        final int andAdd = latitudePosition.getAndAdd(bytes.length);
        ArrayUtils.copy(bytes, 0, LATITUDE_BYTES, andAdd, bytes.length);
        return andAdd;
    }

    public static int copyYxms(byte[] bytes) {
        final int andAdd = yxmsPosition.getAndAdd(bytes.length);
        ArrayUtils.copy(bytes, 0, YXMS_BYTES, andAdd, bytes.length);
        return andAdd;
    }

    public static Lock longitudeLock = new ReentrantLock();
    public static Lock latitudeLock = new ReentrantLock();
    public static Lock yxmsLock = new ReentrantLock();

    public static void shutdown(String filePath) {
        byte[] bytes = new byte[3600 * 3000 * 3];
        ArrayUtils.copy(LONGITUDE_BYTES, 0, bytes, 0, LONGITUDE_BYTES.length);
        ArrayUtils.copy(LATITUDE_BYTES, 0, bytes, 3600 * 3000, LATITUDE_BYTES.length);
        ArrayUtils.copy(YXMS_BYTES, 0, bytes, 3600 * 3000 * 2, YXMS_BYTES.length);
        final GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
        final byte[] compress = gzipCompress.compress(bytes);
        final File file = new File(filePath + "/bigIntData");
        try {
            if (!file.exists() ) {
                file.createNewFile();
            }
            FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
            final ByteBuffer allocate = ByteBuffer.allocate(4);
            allocate.putInt(compress.length);
            fileChannel.write(ByteBuffer.wrap(compress));
        } catch (Exception e) {
            System.out.println("BigIntArrayNew shutdown error, e" + e);
        }
        final File latitudeMapFile  = new File(filePath + "/latitudeMap");
        saveBigIntMapToFile(latitudeMapFile, latitudeMap);

        final File longitudeFile  = new File(filePath + "/longitudeMap");
        saveBigIntMapToFile(longitudeFile, longitudeMap);

        final File yxmsMapFile  = new File(filePath + "/yxmsMap");
        saveBigIntMapToFile(yxmsMapFile, yxmsMap);
    }



    public static void load(String filePath) {
        try {
            File file = new File(filePath + "/bigIntData");
            FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
            final ByteBuffer allocate = ByteBuffer.allocate(4);
            fileChannel.read(allocate, 0);
            allocate.flip();
            final int anInt = allocate.getInt();
            ByteBuffer allocate1 = ByteBuffer.allocate(anInt);
            fileChannel.read(allocate1, 4);
            final byte[] array = allocate1.array();
            final GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
            final byte[] bytes = gzipCompress.deCompress(array);
            System.out.println("BigIntArrayNew decompress bytes length " + bytes.length);
            ArrayUtils.copy(bytes, 0, LONGITUDE_BYTES, 0, LONGITUDE_BYTES.length);
            ArrayUtils.copy(bytes, 3600 * 3000, LATITUDE_BYTES, 0, LONGITUDE_BYTES.length);
            ArrayUtils.copy(bytes, 3600 * 3000 * 2, YXMS_BYTES, 0, LONGITUDE_BYTES.length);

            final File latitudeMapFile  = new File(filePath + "/latitudeMap");
            loadBigIntMapFromFile(latitudeMapFile, latitudeMap, latitudeMapReverse);

            final File longitudeFile  = new File(filePath + "/longitudeMap");
            loadBigIntMapFromFile(longitudeFile, longitudeMap, longitudeMapReverse);

            final File yxmsMapFile  = new File(filePath + "/yxmsMap");
            loadBigIntMapFromFile(yxmsMapFile, yxmsMap, yxmsMapReverse);
        } catch (Exception e) {
            System.out.println("BigIntArrayNew load error, e" + e);
        }
    }

    public static void saveBigIntMapToFile(File file, Map<Integer, Byte> map) {
        try {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (Integer key : map.keySet()) {
                    writer.write(String.valueOf(key));
                    writer.write(":");
                    writer.write(String.valueOf(map.get(key)));
                    writer.newLine();
                }
            }
        } catch (Exception e) {
            System.out.println("saveMapToFile error, e" + e);
        }
    }

    public static void loadBigIntMapFromFile(File file, Map<Integer, Byte> map, Map<Byte, Integer> mapReverse) {
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
                    Byte value = Byte.parseByte(parts[1].trim());

                    map.put(key, value);
                    mapReverse.put(value, key);
                }
            }
        } catch (IOException e) {
            System.out.println("loadMapFromFile error, e: " + e);
        } catch (NumberFormatException nfe) {
            System.out.println("Error parsing number: " + nfe.getMessage());
        }
    }



    public static void main(String[] args) {
        longitudeMapReverse.put((byte) 1, 1);
        longitudeMapReverse.put((byte) 2, 1);
        System.out.println(longitudeMapReverse.get((byte) 2));
    }

}
