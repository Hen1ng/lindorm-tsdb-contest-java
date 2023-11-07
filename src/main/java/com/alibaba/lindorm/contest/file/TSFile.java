package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.compress.GzipCompress;
import com.alibaba.lindorm.contest.compress.ZlibCompress;
import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.Context;
import com.alibaba.lindorm.contest.util.RestartUtil;
import com.alibaba.lindorm.contest.util.StaticsUtil;
import com.github.luben.zstd.Zstd;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 存储实际的数据
 *
 * @author hn
 * @date 2023-07-15
 */
public class TSFile {

    private FileChannel fileChannel;
    private AtomicLong position;
    private long initPosition;
    private Lock lock;
    private long fileSize;
    private int fileName;
    private File file;
    private byte[] array;

    private ByteBuffer directByteBuffer;

    private RandomAccessFile rwFile;

    public TSFile(String filePath, int fileName, long initPosition) {
        try {
            this.fileName = fileName;
            String tsFilePath = filePath + "/" + fileName + ".txt";
            this.fileSize = Constants.TS_FILE_SIZE;
            this.initPosition = initPosition;
            this.position = new AtomicLong(0);
            this.file = new File(tsFilePath);
            this.lock = new ReentrantLock();
            this.rwFile = new RandomAccessFile(file, "rw");
            this.fileChannel = this.rwFile.getChannel();
            if (!file.exists()) {
                file.createNewFile();
            }
            if (!RestartUtil.IS_FIRST_START) {
                if (fileName < Constants.LOAD_TS_FILE_TO_DIRECT_MEMORY_NUM) {
                    final long position = FilePosition.TS_FILE_POSITION_ARRAY[fileName];
                    directByteBuffer = ByteBuffer.allocateDirect((int) position);
                    getFromOffsetByFileChannel(directByteBuffer, initPosition,null);
                    System.out.println("load file to bytebuffer" + fileName + "result " + "directByteBuffer capacity" + directByteBuffer.capacity());
                }
            }
//            this.mappedByteBuffer = this.fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
        } catch (Exception e) {
            System.out.println("create TSFile error, e" + e);
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

    public void getFromOffsetByFileChannel(ByteBuffer byteBuffer, long offset, Context ctx) {
        try {
            if (StaticsUtil.START_COUNT_IOPS != 0) {
                StaticsUtil.DOWN_SAMPLE_IOPS.getAndIncrement();
            }
            int remaining = byteBuffer.remaining();
            long start = System.nanoTime();
            if (array != null) {
                byteBuffer.put(array, (int) (offset - initPosition), byteBuffer.remaining());
                long end = System.nanoTime();
                if (ctx != null) {
                    ctx.setReadFileSize(ctx.getReadFileSize() + remaining);
                    ctx.setHitArray(ctx.getHitArray() + 1);
                    ctx.setReadFileTime(ctx.getReadFileTime() + (end - start));
                }
                return;
            }
            if (directByteBuffer != null) {
                directByteBuffer.position((int) (offset - initPosition));
                for (int i = 0; i < remaining; i++) {
                    byteBuffer.put(directByteBuffer.get());
                }
                return;
            }
            this.fileChannel.read(byteBuffer, offset - initPosition);
            long end = System.nanoTime();
            if (ctx != null) {
                ctx.setReadFileSize(ctx.getReadFileSize() + remaining);
                ctx.setReadFileTime(ctx.getReadFileTime() + (end - start));
            }
        } catch (Exception e) {
            System.out.println("getFromOffsetByFileChannel error, e" + e + "offset:" + offset + "initPosition " + initPosition);
            e.printStackTrace();
        }
    }

//    public long append(byte[] data) {
//        long currentPos = this.position.get();
//        if ((currentPos + data.length) <= this.fileSize) {
//            try {
//                this.mappedByteBuffer.position(currentPos);
//                this.mappedByteBuffer.put(data);
//            } catch (Throwable e) {
//                System.out.println("Error occurred when append message" + e);
//            }
//            return initPosition + this.position.getAndAdd(data.length + 1);
//        }
//        return -1;
//    }

    public void warmTsFile() {
        long start = System.currentTimeMillis();
        try {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1);
            byteBuffer.put((byte) 0);
            for (int i = 0, j = 0; i < Constants.WARM_FILE_SIZE && i >= 0; i += Constants.OS_PAGE_SIZE, j++) {
                byteBuffer.flip();
                fileChannel.write(byteBuffer, i);
                // prevent gc
                if (j % 1000 == 0) {
                    try {
                        Thread.sleep(0);
                    } catch (InterruptedException ignore) {

                    }
                }
            }
            fileChannel.force(true);
            fileChannel.position(0);
        } catch (Exception e) {
            System.out.println("warmTsFile error, e" + e);
        }
        System.out.println("warm tsFile fileName:" + fileName + " cost: " + (System.currentTimeMillis() - start) + " ms");
    }

    public FileChannel getFileChannel() {
        return fileChannel;
    }

    public AtomicLong getPosition() {
        return position;
    }

    public long getInitPosition() {
        return initPosition;
    }

    public Lock getLock() {
        return lock;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getFileName() {
        return fileName;
    }

    public void totalCompressInShutDown() {
        final ByteBuffer allocate = ByteBuffer.allocate((int) (this.position.get() - initPosition));
        getFromOffsetByFileChannel(allocate, initPosition,null);
        final byte[] array1 = allocate.array();
        final ZlibCompress gzipCompress = new ZlibCompress();
        final byte[] compress = gzipCompress.compress(array1);
        System.out.println("totalCompressInShutDown before size :" + array1.length + " after size :" + compress.length);
    }
}
