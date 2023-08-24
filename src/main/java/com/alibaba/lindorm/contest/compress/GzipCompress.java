package com.alibaba.lindorm.contest.compress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;



/**
 * The Data Compression Based on gzip.
 *
 * @date 2022-1-11
 *
 * @author javanoteany
 *
 */
public class GzipCompress implements Compress {

    @Override
    public byte[] compress(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip;

        try {
            gzip = new GZIPOutputStream(out);
            gzip.write(data);
            gzip.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }

    @Override
    public byte[] deCompress(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        GZIPInputStream ungzip = null;
        try {
            ungzip = new GZIPInputStream(in);
            byte[] buffer = new byte[1024];
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