package com.alibaba.lindorm.contest.compress;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ZstdCompress {

    public static byte[] compress(byte[] data, int level) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZstdOutputStream zstdOutputStream = null;

        try {

            zstdOutputStream = new ZstdOutputStream(out, level);
            zstdOutputStream.write(data);
            zstdOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (zstdOutputStream != null) {
                    zstdOutputStream.close();
                }
            } catch (Exception e) {

            }
        }

        return out.toByteArray();
    }

    public static byte[] decompress(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ZstdInputStream ungzip = null;
        try {
            ungzip = new ZstdInputStream(in);
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
}
