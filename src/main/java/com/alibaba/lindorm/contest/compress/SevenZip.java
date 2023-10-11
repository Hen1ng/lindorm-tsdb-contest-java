package com.alibaba.lindorm.contest.compress;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;

/**
 * @Description: 7Z工具类
 * @author qinglj
 **/
public class SevenZip {

    private SevenZip() {}

    /**
     *7Z 压缩
     * @param name 压缩后的文件路径（如 D:\SevenZip\test.7z）
     * @param files 需要压缩的文件
     */
    public static void compress(String name, File... files) {
        try (
                SevenZOutputFile out = new SevenZOutputFile(new File(name))){
            for (File file : files){
                addToArchiveCompression(out, file, ".");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 解压7z文件
     * @param orgPath 源压缩文件地址
     * @param tarpath 解压后存放的目录地址
     */
    public static void decompress(String orgPath, String tarpath) {
        try {
            SevenZFile sevenZFile = new SevenZFile(new File(orgPath));
            SevenZArchiveEntry entry = sevenZFile.getNextEntry();
            while (entry != null) {
                File file = new File(tarpath + File.separator + entry.getName());
                if (entry.isDirectory()) {
                    if(!file.exists()) {
                        file.mkdirs();
                    }
                    entry = sevenZFile.getNextEntry();
                    continue;
                }
                if(!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                FileOutputStream out = new FileOutputStream(file);
                byte[] content = new byte[(int) entry.getSize()];
                sevenZFile.read(content, 0, content.length);
                out.write(content);
                out.close();
                entry = sevenZFile.getNextEntry();
            }
            sevenZFile.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void addToArchiveCompression(SevenZOutputFile out, File file, String dir) {
        String name = dir + File.separator + file.getName();
        if(dir.equals(".")) {
            name = file.getName();
        }
        if (file.isFile()){
            SevenZArchiveEntry entry = null;
            FileInputStream in = null;
            try {
                entry = out.createArchiveEntry(file, name);
                out.putArchiveEntry(entry);
                in = new FileInputStream(file);
                byte[] b = new byte[1024];
                int count = 0;
                while ((count = in.read(b)) > 0) {
                    out.write(b, 0, count);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    out.closeArchiveEntry();
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        } else if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null){
                for (File child : children){
                    addToArchiveCompression(out, child, name);
                }
            }
        } else {
            System.out.println(file.getName() + " is not supported");
        }
    }
}

