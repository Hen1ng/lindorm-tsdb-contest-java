package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.util.Constants;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BigStringFile {
    private FileChannel fileChannel;
    private AtomicLong position;
    private long initPosition;

    private Lock lock;
    private long fileSize;
    private int fileName;
    private File file;

    public BigStringFile(String filePath, int fileName, long initPosition) {
        try {
            this.fileName = fileName;
            String tsFilePath = filePath + "/" + fileName + "-bigString";
            this.fileSize = Constants.TS_FILE_SIZE;
            this.initPosition = initPosition;
            this.position = new AtomicLong(0);
            this.file = new File(tsFilePath);
            this.lock = new ReentrantLock();
            this.fileChannel = new RandomAccessFile(file, "rw").getChannel();
            if (!file.exists()) {
                file.createNewFile();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public long append(ByteBuffer byteBuffer) {
        this.lock.lock();
        long currentPos = this.position.get();
        byteBuffer.flip();
        int remaining = byteBuffer.remaining();
        long andAdd = this.position.getAndAdd(remaining);
        this.lock.unlock();
        try {
            fileChannel.write(byteBuffer, currentPos);
            return initPosition + andAdd;
        } catch (Exception e) {
            System.out.println("TSFile append error, e" + e + "currentPos" + currentPos);
        }
        System.out.println("TSFile not enough, return -2");
        return -2;
    }

    public void getByFileChannel(ByteBuffer byteBuffer, long offset) {
        try {
            this.fileChannel.read(byteBuffer, offset - initPosition);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
