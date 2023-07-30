//package com.alibaba.lindorm.contest.compress;
//
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//
//import org.anarres.lzo.LzoAlgorithm;
//import org.anarres.lzo.LzoCompressor;
//import org.anarres.lzo.LzoDecompressor;
//import org.anarres.lzo.LzoInputStream;
//import org.anarres.lzo.LzoLibrary;
//import org.anarres.lzo.LzoOutputStream;
//
//
///**
// * The Data Compression Based on lzo.
// *
// * @date 2022-1-11
// *
// * @author javanoteany
// *
// */
//public class LzoCompress implements Compress {
//
//    @Override
//    public byte[] compress(byte[] data)  {
//        LzoCompressor compressor = LzoLibrary.getInstance().newCompressor(LzoAlgorithm.LZO1X, null);
//        ByteArrayOutputStream os = new ByteArrayOutputStream();
//        LzoOutputStream cs = new LzoOutputStream(os, compressor);
//        cs.write(data);
//        cs.close();
//
//        return os.toByteArray();
//    }
//
//    @Override
//    public byte[] deCompress(byte[] data)  {
//        LzoDecompressor decompressor = LzoLibrary.getInstance().newDecompressor(LzoAlgorithm.LZO1X, null);
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        ByteArrayInputStream is = new ByteArrayInputStream(data);
//        @SuppressWarnings("resource")
//        LzoInputStream us = new LzoInputStream(is, decompressor);
//
//        int count;
//        byte[] buffer = new byte[2048];
//        while ((count = us.read(buffer)) != -1) {
//            baos.write(buffer, 0, count);
//        }
//
//        return baos.toByteArray();
//    }
//
//}