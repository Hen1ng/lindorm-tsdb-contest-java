package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.RestartUtil;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
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

    private int offsetLine;
    private Lock lock;
    private long fileSize;
    private int fileName;
    private File file;
    private byte[] array;

    private MappedByteBuffer mappedByteBuffer;

    public TSFile(String filePath, int fileName, long initPosition) {
        try {
            this.fileName = fileName;
            String tsFilePath = filePath + "/" + fileName + ".txt";
            this.fileSize = Constants.TS_FILE_SIZE;
            this.initPosition = initPosition;
            this.position = new AtomicLong(0);
            this.file = new File(tsFilePath);
            this.lock = new ReentrantLock();
            this.fileChannel = new RandomAccessFile(file, "rw").getChannel();
            if (!file.exists()) {
                file.createNewFile();
            }
            if (!RestartUtil.IS_FIRST_START) {
                final long position = FilePosition.FILE_POSITION_ARRAY[fileName];
                this.mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, position);
                if (fileName < Constants.LOAD_FILE_TO_MEMORY_NUM) {
                    final ByteBuffer allocate = ByteBuffer.allocate((int) position);
                    getFromOffsetByFileChannel(allocate, initPosition);
                    array = allocate.array();
                    final boolean delete = file.delete();
                    System.out.println("delete file " + fileName + "result " + delete);
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
        try {
            byteBuffer.flip();
            int remaining = byteBuffer.remaining();
            fileChannel.write(byteBuffer, currentPos);
            return initPosition + this.position.getAndAdd(remaining);
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
            if (array != null) {
                byteBuffer.put(array, (int) (offset - initPosition), byteBuffer.capacity());
                return;
            }
            this.fileChannel.read(byteBuffer, offset - initPosition);
        } catch (Exception e) {
            System.out.println("getFromOffsetByFileChannel error, e" + e + "offset:" + offset + "initPosition " + initPosition);
            e.printStackTrace();
        }
    }


    public ByteBuffer getFromMMap(long offset, int size) {
        if (mappedByteBuffer != null) {
            try {
                byte[] bytes = new byte[size];
                mappedByteBuffer.position((int) (offset - initPosition));
                mappedByteBuffer.get(bytes);
                return ByteBuffer.wrap(bytes);
            } catch (Throwable e) {
                System.out.println("Error occurred when get message" + e);
            }
        }
        return null;
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
}
