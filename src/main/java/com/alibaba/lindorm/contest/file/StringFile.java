package com.alibaba.lindorm.contest.file;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class StringFile {
    private File file;

    private FileChannel fileChannel;
    private ReentrantLock lock;

    private AtomicInteger position;

    public StringFile(String filePath, String key) {
        try {
            String tsFilePath = filePath + "/" + key;
            this.file = new File(tsFilePath);
            if (!file.exists()) {
                file.createNewFile();
            }
            this.lock = new ReentrantLock();
            this.position = new AtomicInteger(0);
            this.fileChannel = FileChannel.open(file.toPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            e.printStackTrace();
        }
    }
}
