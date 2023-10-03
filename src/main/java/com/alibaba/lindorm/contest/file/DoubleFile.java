package com.alibaba.lindorm.contest.file;


import com.alibaba.lindorm.contest.compress.DoubleCompress;
import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.StaticsUtil;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class DoubleFile {
    private String key;
    private File file;

    private FileChannel fileChannel;
    private ReentrantLock lock;

    private AtomicInteger position;

    private double[] values;

    private int i = 0;

    public int getCacheSize() {
        return cacheSize;
    }

    private int cacheSize ;

    private int offset;

    public DoubleFile(String filePath, String key) {
        try {
            this.key = key;
            this.values = new double[Constants.CACHE_VINS_LINE_NUMS];
            String tsFilePath = filePath + "/" + key;
            this.position = new AtomicInteger(0);
            this.file = new File(tsFilePath);
            if (!file.exists()) {
                file.createNewFile();
            }
            this.offset = 0;
            this.lock = new ReentrantLock();
            this.fileChannel = new RandomAccessFile(file, "rw").getChannel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized int write(double d, int lineNum) {
        values[i++] = d;
        if (i >= lineNum) {
            i = 0;
            offset = write(values, lineNum);
            return offset;
        }
        return offset;
    }

    public int write(double[] values) {
        final ByteBuffer encode = DoubleCompress.encode(values);
        final ByteBuffer allocate = ByteBuffer.allocate(4 + encode.capacity());
        StaticsUtil.DOUBLE_COMPRESS_LENGTH.getAndAdd(4 + encode.capacity());
        allocate.putInt(encode.capacity());
        allocate.put(encode);
        return append(allocate);
    }

    public int write(double[] values, int lineNum) {
        final ByteBuffer encode = DoubleCompress.encode(values, lineNum);
        final ByteBuffer allocate = ByteBuffer.allocate(4 + encode.capacity());
        allocate.putInt(encode.capacity());
        allocate.put(encode);
        return append(allocate);
    }

    public int append(ByteBuffer byteBuffer) {
        this.lock.lock();
        long currentPos = this.position.get();
        try {
            byteBuffer.flip();
            int remaining = byteBuffer.remaining();
            fileChannel.write(byteBuffer, currentPos);
            return this.position.getAndAdd(remaining);
        } catch (Exception e) {
            System.out.println("TSFile append error, e" + e + "currentPos" + currentPos);
        } finally {
            this.lock.unlock();
        }
        System.out.println("TSFile not enough, return -2");
        return -2;
    }

    public void getFromOffsetByFileChannel(ByteBuffer byteBuffer, long offset) {
        try {
            this.fileChannel.read(byteBuffer, offset);
        } catch (Exception e) {
            System.out.println("getFromOffsetByFileChannel error, e" + e + "offset:" + offset);
        }
    }

    public AtomicInteger getPosition() {
        return position;
    }

    public static void main(String[] args) {
        System.out.println(1);
    }
}
