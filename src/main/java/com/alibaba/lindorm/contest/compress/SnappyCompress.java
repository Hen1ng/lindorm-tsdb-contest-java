//package com.alibaba.lindorm.contest.compress;
//
//import org.xerial.snappy.Snappy;
//
//
///**
// * The Data Compression Based on snappy.
// *
// * @date 2022-1-11
// *
// * @author javanoteany
// *
// */
//public class SnappyCompress implements Compress {
//
//    @Override
//    public byte[] compress(byte[] data)  {
//        try {
//            return Snappy.compress(data);
//        } catch (Exception e) {
//
//        }
//        return null;
//    }
//
//    @Override
//    public byte[] deCompress(byte[] data) {
//        try {
//            return Snappy.uncompress(data);
//        } catch (Exception e) {
//            return null;
//        }
//    }
//
//}