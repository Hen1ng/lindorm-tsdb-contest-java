package com.alibaba.lindorm.contest.file;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class StringFile {
    private File file;

    private FileChannel fileChannel;
    private ReentrantLock lock;

    private AtomicInteger position;

    private List<ByteBuffer> byteBuffers;

    private int totalCacheSize;

    private int totalSize;

    public StringFile(String filePath, String key, int totalCacheSize) {
        try {
            String tsFilePath = filePath + "/" + key;
            this.file = new File(tsFilePath);
            if (!file.exists()) {
                file.createNewFile();
            }
            this.totalCacheSize = totalCacheSize;
            this.lock = new ReentrantLock();
            this.position = new AtomicInteger(0);
            this.fileChannel = FileChannel.open(file.toPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int write(ByteBuffer byteBuffer) {
        this.lock.lock();
        try {
            byteBuffers.add(byteBuffer);
            if (byteBuffers.size() >= totalCacheSize) {
                final ByteBuffer allocate = ByteBuffer.allocate(totalSize);
                for (ByteBuffer buffer : byteBuffers) {
                    allocate.put(buffer);
                }
                final int append = append(allocate);
                byteBuffers.clear();
                totalSize = 0;
                return append;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            this.lock.unlock();
        }
        return -1;
    }

    public int append(ByteBuffer byteBuffer) throws IOException {
        long currentPos = this.position.get();
        byteBuffer.flip();
        int remaining = byteBuffer.remaining();
        fileChannel.write(byteBuffer, currentPos);
        return this.position.getAndAdd(remaining);
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
