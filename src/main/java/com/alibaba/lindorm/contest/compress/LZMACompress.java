package com.alibaba.lindorm.contest.compress;

import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class LZMACompress {

    public static byte[] compress(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            final LZMACompressorOutputStream lzmaCompressorOutputStream = new LZMACompressorOutputStream(out);
            lzmaCompressorOutputStream.write(data);
            lzmaCompressorOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }

    public static byte[] decompress(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        LZMACompressorInputStream ungzip = null;
        try {
            ungzip = new LZMACompressorInputStream(in);
            byte[] buffer = new byte[2048];
            int n;
            while ((n = ungzip.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (ungzip != null) {
                try {
                    ungzip.close();
                } catch (Exception e) {
                    System.out.println("GzipCompress unzip close erorr, " + e);
                }
            }

        }

        return null;
    }

    public static void main(String[] args) {
        double[] values = new double[]{0.0, 0.0, 0.0, 0.0, 0.1, 0.1, 0.1, 0.2, 0.2, 0.2};
        final ByteBuffer allocate = ByteBuffer.allocate(values.length * 8);
        for (double value : values) {
            allocate.putDouble(value);
        }
        final byte[] array = allocate.array();

        final byte[] compress = LZMACompress.compress(array);

        byte[] decompress = LZMACompress.decompress(compress);

        System.out.println(1);

    }
}
