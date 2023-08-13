package com.alibaba.lindorm.contest.test;


import com.alibaba.lindorm.contest.compress.DeflaterCompress;
import com.alibaba.lindorm.contest.compress.GzipCompress;
import com.alibaba.lindorm.contest.compress.ZlibCompress;
import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.BytesUtil;
import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.list.SortedList;
import com.alibaba.lindorm.contest.util.list.hppc.ObjectObjectHashMap;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class BechmarkTest {

    volatile long start = System.currentTimeMillis();

    public void testSSDWriteIops(String path)
        throws Exception {
        int threadNum = 50;
        long totalSize = 15L * 1024 * 1024 * 1024;
        long maxSingleFileSize = totalSize / threadNum;
        int bufferSize = 4 * 1024;
        List<FileChannel> fileChannelList = new ArrayList<>();

        CountDownLatch countDownLatch = new CountDownLatch(threadNum);

        for (int i = 0; i < threadNum; i++) {
            File file = new File(path + "/test_" + i);
            if (!file.exists()) {
                file.createNewFile();
            }
            RandomAccessFile rw = new RandomAccessFile(file, "rw");
            FileChannel fileChannel = rw.getChannel();
            warmCommitLog2(fileChannel, maxSingleFileSize);
            fileChannelList.add(fileChannel);
        }

        for (int i = 0; i < threadNum; i++) {
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize);
            for (int j = 0; j < bufferSize; j++) {
                byteBuffer.put((byte)2);
            }
            this.start = System.currentTimeMillis();
            System.out.println("start = " + start);
            int finalI = i;
            new Thread(() -> {
                try {
                    long loopTime = totalSize / bufferSize / threadNum;
                    for (long t = 0; t < loopTime; t++) {
                        byteBuffer.flip();
                        fileChannelList.get(finalI).write(byteBuffer, t * bufferSize);
                    }
                    countDownLatch.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
        countDownLatch.await();


        System.out.println("finish=" + System.currentTimeMillis());
        System.out.println(
            "threadNum " + threadNum + " write " + totalSize + " bufferSize " + bufferSize + " cost " + (
                System.currentTimeMillis()
                    - start) + " ms");
    }



    public void warmCommitLog(FileChannel fileChannel, long needWarmLength) {
        try {
            long start = System.currentTimeMillis();
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 1024);
            for (int j = 0; j < 4 * 1024; j++) {
                byteBuffer.put((byte)1);
            }
            for (long i = 0; i < needWarmLength; i += Constants.OS_PAGE_SIZE) {
                byteBuffer.flip();
                fileChannel.write(byteBuffer, i);
            }
            fileChannel.force(true);
            fileChannel.position(0);
            System.out.println("aep test warm file finish"  + "cost: " + (System.currentTimeMillis() - start));
        } catch (Exception e) {
            System.out.println("warmCommitLog error, e" + e);
            System.exit(-1);
        }
    }

    public void warmCommitLog2(FileChannel fileChannel, long needWarmLength) {
        try {
            long start = System.currentTimeMillis();
            byte[] bytes = new byte[Constants.OS_PAGE_SIZE];
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Constants.OS_PAGE_SIZE);
            byteBuffer.put(bytes);
            for (long i = 0; i < needWarmLength; i += Constants.OS_PAGE_SIZE) {
                byteBuffer.flip();
                fileChannel.write(byteBuffer, i);
            }
            fileChannel.force(true);
            fileChannel.position(0);
            System.out.println("warmCommitLog finish, "  + "cost: " + (System.currentTimeMillis() - start));
        } catch (Exception e) {
            System.out.println("warmCommitLog error, e" + e);
            System.exit(-1);
        }
    }

    public static void main(String[] args) throws Exception {
        SortedList<Long> sortedList = new SortedList<>((l1, l2) -> -1 * l1.compareTo(l2));

        sortedList.add(2L);
        sortedList.add(2L);
        sortedList.add(1L);
        sortedList.add(7L);
        sortedList.add(0L);
        sortedList.add(10L);
        SortedList<Long> sortedList1 = sortedList;
        sortedList.clear();
        System.out.println(sortedList1.size());

//        for (Long aLong : sortedList) {
//            System.out.println(aLong);
//        }
//        ObjectObjectHashMap<Vin, SortedList<Long>> map = new ObjectObjectHashMap<>();
//        Vin vin = new Vin("abcdjdhebdhiopkjh".getBytes(StandardCharsets.UTF_8));
//        Vin vin1 = new Vin("abcdjdhebdhiopkoh".getBytes(StandardCharsets.UTF_8));
//        Vin vin2 = new Vin("abcdjdhebdhiopkjh".getBytes(StandardCharsets.UTF_8));
//        map.put(vin, sortedList);
//        map.put(vin1, sortedList1);
//        map.get(vin1).add(1L);
//        map.get(vin1).add(10L);
//        System.out.println(map);
        final String randomString = BytesUtil.getRandomString(20 );
//        System.out.println(randomString);
        final ZlibCompress zlibCompress = new ZlibCompress();
        final GzipCompress gzipCompress = new GzipCompress();
        final DeflaterCompress deflaterCompress = new DeflaterCompress();
        final byte[] compress = zlibCompress.compress(randomString.getBytes(StandardCharsets.UTF_8));
        System.out.println(compress.length);
        System.out.println(new String(deflaterCompress.deCompress(compress)).equals(randomString));

        final String randomString1 = BytesUtil.getRandomString(1024 * 4 );
        final byte[] compress1 = gzipCompress.compress(randomString1.getBytes(StandardCharsets.UTF_8));
        System.out.println(compress1.length);
        System.out.println(new String(gzipCompress.deCompress(compress1)).equals(randomString1));
    }

}
