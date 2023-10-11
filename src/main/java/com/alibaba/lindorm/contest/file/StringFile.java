package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.compress.CompressResult;
import com.alibaba.lindorm.contest.compress.IntCompress;
import com.alibaba.lindorm.contest.compress.StringCompress;
import com.alibaba.lindorm.contest.index.Bindex;
import com.alibaba.lindorm.contest.index.BindexFactory;
import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.util.Pair;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
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
    private int currentByteBufferNum;

    private long append;

    private int bindexPosition = 0;

    private int batchSize = 0;


    public StringFile(String filePath, int totalByteBufferSize) {
        try {
            String tsFilePath = filePath;
            this.file = new File(tsFilePath);
            if (!file.exists()) {
                file.createNewFile();
            }
            this.totalByteBufferSize = totalByteBufferSize;
            this.byteBuffers = new LinkedList<>();
            this.lock = new ReentrantLock();
            this.position = new AtomicLong(0);
            this.fileChannel = new RandomAccessFile(file, "rw").getChannel();
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

    public void write(List<ByteBuffer> buffers, int start, int end, boolean flush, int column, Index index) {
        this.lock.lock();
        try {
            if (bindex == null) {
                Pair<Integer, Bindex> bindex1 = BindexFactory.getNewBindex();
                bindexPosition = bindex1.getLeft();
                bindex = bindex1.getRight();
            }
            for (int i = start; i < end; i++) {
                ByteBuffer byteBuffer = buffers.get(i);
                byteBuffers.add(byteBuffer);
                totalSize += byteBuffer.capacity();
                currentByteBufferNum += 1;
            }
            if (currentByteBufferNum >= totalByteBufferSize || flush) {
                List<ByteBuffer> subBuffers = new ArrayList<>(currentByteBufferNum);
                for (int i = 0; i < currentByteBufferNum; i++) {
                    subBuffers.add(buffers.get(i));
                }
                final CompressResult compressResult = StringCompress.compress1(subBuffers, subBuffers.size());
                final byte[] compressedData = compressResult.compressedData;
                final short[] stringLengthArray = compressResult.stringLengthArray;
                byte[] stringLengthArrayCompress = IntCompress.compressShort(stringLengthArray, index.getValueSize());
                int totalLength = compressedData.length + stringLengthArrayCompress.length;
                totalLength += 16;
                final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(totalLength);
                byteBuffer.putInt(stringLengthArrayCompress.length);
                byteBuffer.put(stringLengthArrayCompress);
                byteBuffer.putInt(stringLengthArray.length * 2);
                byteBuffer.putInt(compressedData.length);
                byteBuffer.put(compressedData);
                byteBuffer.putInt(totalSize);
                byteBuffer.flip();
                this.append = append(byteBuffer);
                bindex.totalLength[column] = totalLength;
                bindex.fileOffset[column] = append;
                final Bindex bindex1 = bindex.deepCopy();
                index.getStringOffset()[column] = batchSize;
                index.setBindexIndex(bindexPosition);
                BindexFactory.updateByPosition(bindexPosition, bindex1);
                byteBuffers.clear();
                totalSize = 0;
                batchSize = 0;
                totalByteBufferSize = 0;
                currentByteBufferNum = 0;
                bindex = null;
                bindexPosition = -1;
                return;
            }
            batchSize = totalSize;
            bindex.fileOffset[column] = append;
            bindex.totalLength[column] = -1;
            index.getStringOffset()[column] = batchSize;
            index.setBindexIndex(bindexPosition);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        } finally {
            this.lock.unlock();
        }
    }

    public List<ByteBuffer> getFromBuffer(int stringOffset, int valueSize) {
        this.lock.lock();
        List<ByteBuffer> buffers = new ArrayList<>(valueSize);
        int i = 0;
        try {
            for (ByteBuffer byteBuffer : this.byteBuffers) {
                if (i >= stringOffset && i < stringOffset + valueSize) {
                    buffers.add(ByteBuffer.wrap(byteBuffer.array()));
                }
                i += byteBuffer.capacity();

            }
        } finally {
            this.lock.unlock();
        }
        return buffers;
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
