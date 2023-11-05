package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.util.ArrayUtils;
import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.RestartUtil;
import com.alibaba.lindorm.contest.util.UnsafeUtil;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class IntFile {
    private FileChannel fileChannel;
    private AtomicLong position;
    private long initPosition;

    private Lock lock;
    private long fileSize;
    private int fileName;
    private File file;

    private byte[] array;

    public IntFile(String filePath, int fileName, long initPosition) {
        try {
            this.fileName = fileName;
            String tsFilePath = filePath + "/" + fileName + "-int";
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
                if (fileName < Constants.LOAD_FILE_TO_MEMORY_NUM) {
                    final long position = FilePosition.FILE_POSITION_ARRAY[fileName];
                    final ByteBuffer allocate = ByteBuffer.allocate((int) position);
                    getFromOffsetByFileChannel(allocate, initPosition);
                    array = allocate.array();
                    final boolean delete = file.delete();
                    System.out.println("delete file " + fileName + "result " + delete + " array length" + array.length);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            System.out.println("IntFile append error, e" + e + "currentPos" + currentPos);
        } finally {
            this.lock.unlock();
        }
        System.out.println("IntFile not enough, return -2");
        return -2;
    }

    public void getFromOffsetByFileChannel(ByteBuffer byteBuffer, long offset) {
        try {
            if (array != null) {
                byteBuffer.put(array, (int) (offset - initPosition), byteBuffer.remaining());
                return;
            }
            this.fileChannel.read(byteBuffer, offset - initPosition);
        } catch (Exception e) {
            System.out.println("IntFile getFromOffsetByFileChannel error, e" + e + "offset:" + offset + "initPosition " + initPosition);
            e.printStackTrace();
        }
    }

    public void getByteFromArray(byte[] dst, long offset) {
        if (array != null) {
            ArrayUtils.copy(dst, (int) offset, dst, 0, dst.length);
        }

    }

    public AtomicLong getPosition() {
        return position;
    }

}
