package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.RestartUtil;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class FilePosition {

    public static long[] FILE_POSITION_ARRAY;

    private File file;
    private FileChannel fileChannel;
    private MappedByteBuffer mappedByteBuffer;

    public FilePosition(String filePath) {
        try {
            this.file = new File(filePath);
            if (!file.exists()) {
                file.createNewFile();
            }
            this.fileChannel = new RandomAccessFile(file, "rw").getChannel();
            this.mappedByteBuffer = this.fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, Constants.TS_FILE_NUMS * 8);
            if (!RestartUtil.IS_FIRST_START) {
                FILE_POSITION_ARRAY = new long[Constants.TS_FILE_NUMS];
                for (int i = 0; i < Constants.TS_FILE_NUMS; i++) {
                    FILE_POSITION_ARRAY[i] = mappedByteBuffer.getLong();
                    System.out.println("FILE NAME:" + i + "position " + FILE_POSITION_ARRAY[i]);
                }
            }
        } catch (Exception e) {

        }
    }

    public void save(TSFile[] tsFiles) {
        for (TSFile tsFile : tsFiles) {
            final long l = tsFile.getPosition().get();
            mappedByteBuffer.putLong(l);
        }
    }

    public void delete() {
        file.delete();
    }
}
