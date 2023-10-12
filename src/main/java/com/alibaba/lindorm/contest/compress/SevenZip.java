package com.alibaba.lindorm.contest.compress;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SevenZip {
    public static byte[]compress(byte[]arr,int level){
        try {
            ByteArrayOutputStream compr = new ByteArrayOutputStream();
            LZMA2Options options = new LZMA2Options();
            options.setPreset(level); // play with this number: 6 is default but 7 works better for mid sized archives ( > 8mb)
            XZOutputStream out = new XZOutputStream(compr, options);
            out.write(arr);
            out.finish();
            return compr.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[]decompress(byte[]bts){
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bts);
            XZInputStream is = new XZInputStream(bis);
//            ByteArrayInputStream decomp = new ByteArrayInputStream(is.readAllBytes());
//            ObjectInputStream ois = new ObjectInputStream(decomp);
//            byte data[]= (byte[]) ois.readObject();
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
