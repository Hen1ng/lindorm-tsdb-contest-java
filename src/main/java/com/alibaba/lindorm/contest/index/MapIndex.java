package com.alibaba.lindorm.contest.index;

import com.alibaba.lindorm.contest.compress.GzipCompress;
import com.alibaba.lindorm.contest.file.TSFileService;
import com.alibaba.lindorm.contest.memory.VinDictMap;
import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.Pair;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MapIndex {

    public static List<BigBucket>[] BUCKET_ARRAY = new List[Constants.TOTAL_VIN_NUMS];

    public static  List<Index>[] INDEX_ARRAY = new List[Constants.TOTAL_VIN_NUMS];

    public static void clear() {
        List<Index>[] INDEX_ARRAY = null;
    }

    static {
        for (int i = 0; i < INDEX_ARRAY.length; i++) {
            INDEX_ARRAY[i] = new LinkedList<>();
        }
        for(int i=0;i<BUCKET_ARRAY.length;i++){
            BUCKET_ARRAY[i] = new LinkedList<>();
        }
    }

    public static void put(int vinIndex, Index index) {
        INDEX_ARRAY[vinIndex].add(index);
    }

    public static List<BigBucket> getBucket(int vinIndex, long timeLowerBound, long timeUpperBound){
        List<BigBucket> bigBuckets = BUCKET_ARRAY[vinIndex];
        List<BigBucket> indexList = new ArrayList<>();
        if (bigBuckets == null) {
            return bigBuckets;
        }
        for (BigBucket bigBucket : bigBuckets) {
            final long maxTimestamp = bigBucket.getMaxTimestamp();
            final long minTimestamp = bigBucket.getMinTimestamp();
            if (maxTimestamp < timeLowerBound) {
                continue;
            }
            if (minTimestamp >= timeUpperBound) {
                continue;
            }
            indexList.add(bigBucket);
        }
        return indexList;
    }

    public static List<Index> get(int vinIndex, long timeLowerBound, long timeUpperBound) {
        List<Index> indices = INDEX_ARRAY[vinIndex];
        List<Index> indexList = new ArrayList<>();
        if (indices == null) {
            return indexList;
        }
        for (Index index : indices) {
            final long maxTimestamp = index.getMaxTimestamp();
            final long minTimestamp = index.getMinTimestamp();
            if (maxTimestamp < timeLowerBound) {
                continue;
            }
            if (minTimestamp >= timeUpperBound) {
                continue;
            }
            indexList.add(index);
        }
        return indexList;
    }

    public static Set<Index> getV2(int vinIndex, long timeLowerBound, long timeUpperBound) {
        List<Index> indices = INDEX_ARRAY[vinIndex];
        if (indices == null) {
            return null;
        }
        Set<Index> resultSet = new HashSet<>();
        for (Index index : indices) {
            if (index.getMaxTimestamp() < timeLowerBound) {
                continue;
            }
            if (index.getMinTimestamp() >= timeUpperBound) {
                continue;
            }
            resultSet.add(index);
        }
        return resultSet;
    }

    public static Pair<Index, Long> getLast(int vinIndex) {
        List<Index> indices = INDEX_ARRAY[vinIndex];
        if (indices == null) {
            return null;
        }
        Index i = null;
        long maxTs = Long.MIN_VALUE;
        for (Index index : indices) {
            if (index.getMaxTimestamp() > maxTs) {
                i = index;
                maxTs = index.getMaxTimestamp();
            }
        }
        return Pair.of(i, maxTs);
    }

    public static void saveMaPToFileCompress(File file) throws IOException {
        long start = System.currentTimeMillis();
        FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        // 先压缩vin

        long indexFileLength = 0;
        for (int i = 0; i < INDEX_ARRAY.length; i++) {
            List<Index> indices = INDEX_ARRAY[i];
            if (indices.isEmpty()) {
                continue;
            }
            final byte[] vin = VinDictMap.get(i);
            List<byte[]> indexBytes = new ArrayList<>();
            int totalBytes = 0;
            for (Index index : indices) {
                byte[] bytes = index.bytes();
                indexBytes.add(bytes);
                totalBytes += bytes.length;
            }
            // vin length + vin
            // index length + index's length + index bytes
            ByteBuffer allocate = ByteBuffer.allocate(vin.length + 4 +
                    4 * indexBytes.size() + totalBytes);

            allocate.put(vin);
            allocate.putInt(indices.size());
            for (byte[] indexByte : indexBytes) {
                allocate.putInt(indexByte.length);
                allocate.put(indexByte);
            }
            final byte[] array = allocate.array();
            final GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
            final byte[] compress = gzipCompress.compress(array);
            final ByteBuffer allocate1 = ByteBuffer.allocate(compress.length + 4);
            indexFileLength += compress.length + 4;
            allocate1.putInt(compress.length);
            allocate1.put(compress);
            allocate1.flip();
            fileChannel.write(allocate1);
        }
        System.out.println("INDEX FILE LEN : " + indexFileLength + " time : " + (System.currentTimeMillis() - start) + " ms");
    }

//    public static void saveMapToFile(File file) {
//        try {
//            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
//                for (Vin vin : INDEX_MAP.keySet()) {
//                    final byte[] vin1 = vin.getVin();
//                    writer.write(new String(vin1));
//                    writer.write("]");
//                    final List<Index> indices = INDEX_MAP.get(vin);
//                    for (Index index : indices) {
//                        writer.write(index.toString());
//                        writer.write(" ");
//                    }
//                    writer.write("]");
//                    writer.newLine();
//                }
//            }
//        } catch (Exception e) {
//            System.out.println("saveMapToFile error, e" + e);
//        }
//    }

    public static void loadMapFromFileunCompress(File file)
            throws IOException {
        FileChannel fileChannel = new RandomAccessFile(file, "r").getChannel();
        ByteBuffer intBuffer = ByteBuffer.allocate(4);
        while (fileChannel.read(intBuffer) > 0) {
            intBuffer.flip();
            int compressLength = intBuffer.getInt();
            ByteBuffer dataBuffer = ByteBuffer.allocate(compressLength);
            fileChannel.read(dataBuffer);
            final byte[] array = dataBuffer.array();
            final GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
            final byte[] bytes = gzipCompress.deCompress(array);
            final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            byte[] vinArray = new byte[17];
            byteBuffer.get(vinArray);
            Vin vin = new Vin(vinArray);
            final int indexSize = byteBuffer.getInt();
            List<Index> indices = new ArrayList<>();
            for (int i = 0; i < indexSize; i++) {
                final int anInt = byteBuffer.getInt();
                byte[] bytes1 = new byte[anInt];
                byteBuffer.get(bytes1);
                Index index = Index.uncompress(bytes1);
                indices.add(index);
            }
            final Integer i = VinDictMap.get(vin);
            INDEX_ARRAY[i] = indices;
            intBuffer.flip();
        }
        for(int i=0;i<INDEX_ARRAY.length;i++){
            int j = 0;
            BigBucket bigBucket = new BigBucket();
            while (j < INDEX_ARRAY[i].size()){
                bigBucket.addBucket(INDEX_ARRAY[i].get(j));
                if(j%10==0){
                    BUCKET_ARRAY[i].add(bigBucket);
                    bigBucket = new BigBucket();
                }
                j++;
            }
            if(bigBucket.getIndexSize() != 0){
                BUCKET_ARRAY[i].add(bigBucket);
            }
        }
        System.out.println("load Index into memory size : " + INDEX_ARRAY.length);

    }

//    public static void loadMapFromFile(File file)
//            throws IOException {
//        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
//            String line;
//            while ((line = reader.readLine()) != null) {
//                try {
//                    CopyOnWriteArrayList<Index> indices = new CopyOnWriteArrayList<>();
//                    final String[] split1 = line.split("]");
//                    final String s = split1[1];
//                    final String[] s1 = s.split(" ");
//                    for (String s2 : s1) {
//                        final String[] split2 = s2.split("@");
//                        indices.add(new Index(
//                                Long.parseLong(split2[0])
//                                , Long.parseLong(split2[1])
//                                , Long.parseLong(split2[2])
//                                , Integer.parseInt(split2[3])
//                                , Integer.parseInt(split2[4])
//                                , AggBucket.fromString(split2[7])
////                                , DoubleIndexMap.fromString(split2[8])
//                        ));
//                    }
//                    INDEX_MAP.put(new Vin(split1[0].getBytes(StandardCharsets.UTF_8)), indices);
//
//                } catch (Exception e) {
//                    System.out.println("loadMapIndexFromFile e" + e + "line " + line);
//                }
//
//            }
//        }
//        System.out.println("index file length : " + file.length());
//        file.delete();
//    }

    public static void main(String[] args) throws IOException {
        Map<Vin, CopyOnWriteArrayList<Index>> indexMap = new ConcurrentHashMap<>();
        CopyOnWriteArrayList<Index> list = new CopyOnWriteArrayList<>();
        final File file = new File("./data_dir/index.txt");
        if (!file.exists()) {
            file.createNewFile();
        }
//        saveMapToFile(file);
//        loadMapFromFile(file);
        System.out.println(1);
    }
}
