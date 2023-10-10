package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.compress.CompressResult;
import com.alibaba.lindorm.contest.compress.IntCompress;
import com.alibaba.lindorm.contest.compress.StringCompress;
import com.alibaba.lindorm.contest.index.Bindex;
import com.alibaba.lindorm.contest.index.Index;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class StringFile {
    private File file;

    private FileChannel fileChannel;
    private ReentrantLock lock;

    private AtomicLong position;

    /**
     * 缓存的byteBuffer
     */
    private List<ByteBuffer> byteBuffers;

    private Bindex bindex;

    /**
     * 缓存的byteBuffer的总数量，目前需要是{@link com.alibaba.lindorm.contest.util.Constants#CACHE_VINS_LINE_NUMS}的整数倍
     */
    private int totalByteBufferSize;

    /**
     * 缓存的字符串的总长度，所有的缓存的byteBuffer#capacity()的总和
     */
    private int totalSize;

    /**
     * 当前list里面的byteBuffer的数量
     */
    private int currentByteBufferSize;

    private long append;


    public StringFile(String filePath, String key, int totalByteBufferSize) {
        try {
            String tsFilePath = filePath + "/" + key;
            this.file = new File(tsFilePath);
            if (!file.exists()) {
                file.createNewFile();
            }
            this.totalByteBufferSize = totalByteBufferSize;
            this.byteBuffers = new LinkedList<>();
            this.lock = new ReentrantLock();
            this.position = new AtomicLong(0);
            this.fileChannel = FileChannel.open(file.toPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class WriteResult {
        public long fileOffset;
        public int batchSize;

        public int totalLength;

        public WriteResult(long fileOffset, int batchSize, int totalLength) {
            this.fileOffset = fileOffset;
            this.batchSize = batchSize;
            this.totalLength = totalLength;
        }
    }

    public int write(List<ByteBuffer> buffers, int start, int end, boolean flush, int column, Index index) {
        this.lock.lock();
        try {
            int batchSize = totalSize;
            for (int i = start; i < end; i++) {
                ByteBuffer byteBuffer = buffers.get(i);
                byteBuffers.add(byteBuffer);
                totalSize += byteBuffer.capacity();
                currentByteBufferSize += 1;
            }
            if (currentByteBufferSize >= totalByteBufferSize || flush) {
                final ByteBuffer allocate = ByteBuffer.allocate(totalSize);
                List<ByteBuffer> subBuffers = new ArrayList<>(currentByteBufferSize);
                for (int i = 0; i < currentByteBufferSize; i++) {
                    subBuffers.add(buffers.get(i));
                }
                final CompressResult compressResult = StringCompress.compress1(subBuffers, subBuffers.size());
                final byte[] compressedData = compressResult.compressedData;
                final short[] stringLengthArray = compressResult.stringLengthArray;
                byte[] stringLengthArrayCompress = IntCompress.compressShort(stringLengthArray, subBuffers.size());
                int totalLength = compressedData.length + stringLengthArrayCompress.length;
                totalLength += 6;
                final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(totalLength);
                byteBuffer.putShort((short) stringLengthArrayCompress.length);
                byteBuffer.put(stringLengthArrayCompress);
                byteBuffer.putInt(compressedData.length);
                byteBuffer.put(compressedData);
                this.append = append(allocate);
                byteBuffers.clear();
                totalSize = 0;
                totalByteBufferSize = 0;
                currentByteBufferSize = 0;
                return new WriteResult(append, batchSize, totalLength);
            }

            return new WriteResult(append, batchSize, -1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        } finally {
            this.lock.unlock();
        }
        return null;
    }


    public long append(ByteBuffer byteBuffer) throws IOException {
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
