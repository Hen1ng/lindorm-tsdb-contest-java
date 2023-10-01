package com.alibaba.lindorm.contest.index;

import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.Pair;

import java.io.*;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MapIndex {

    public static final Map<Vin, List<Index>> INDEX_MAP = new ConcurrentHashMap<>();

    public static void put(Vin vin, Index index) {
        List<Index> indices = INDEX_MAP.get(vin);
        if (indices == null) {
            indices = new ArrayList<>();
            indices.add(index);
            INDEX_MAP.put(vin, indices);
        } else {
            indices.add(index);
        }
    }

    public static List<Index> getByVin(Vin vin) {
        return INDEX_MAP.get(vin);
    }

    public static List<Index> get(Vin vin, long timeLowerBound, long timeUpperBound) {
        List<Index> indices = INDEX_MAP.get(vin);
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

    public static Set<Index> getV2(Vin vin, long timeLowerBound, long timeUpperBound) {
        List<Index> indices = INDEX_MAP.get(vin);
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

    public static Pair<Index, Long> getLast(Vin vin) {
        List<Index> indices = INDEX_MAP.get(vin);
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
        FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
        // 先压缩vin

        for (Vin vin : INDEX_MAP.keySet()) {
            List<Index> indices = INDEX_MAP.get(vin);
            List<byte[]> indexBytes = new ArrayList<>();
            int totalBytes = 0;
            for (Index index : indices) {
                byte[] bytes = index.bytes();
                indexBytes.add(bytes);
                totalBytes += bytes.length;
            }
            // vin length + vin
            // index length + index's length + index bytes
            ByteBuffer allocate = ByteBuffer.allocate(4 + vin.getVin().length +
                    4 + 4 * indexBytes.size() + totalBytes);

            allocate.putInt(vin.getVin().length);
            allocate.put(vin.getVin());
            allocate.putInt(indices.size());
            for (byte[] indexByte : indexBytes) {
                allocate.putInt(indexByte.length);
                allocate.put(indexByte);
            }
            try {
                int length = 0;
                allocate.flip();
                while(allocate.hasRemaining()) {
                    length += fileChannel.write(allocate);
                }
                if(length !=  4 +vin.getVin().length +
                        4 + 4 * indexBytes.size() + totalBytes){
                    System.out.println("write index file error by fileChanel");
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
    public static void saveMapToFile(File file) {
        try {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (Vin vin : INDEX_MAP.keySet()) {
                    final byte[] vin1 = vin.getVin();
                    writer.write(new String(vin1));
                    writer.write("]");
                    final List<Index> indices = INDEX_MAP.get(vin);
                    for (Index index : indices) {
                        writer.write(index.toString());
                        writer.write(" ");
                    }
                    writer.write("]");
                    writer.newLine();
                }
            }
        } catch (Exception e) {
            System.out.println("saveMapToFile error, e" + e);
        }
    }
    public static void loadMapFromFileunCompress(File file)
            throws IOException {
        FileChannel fileChannel = new RandomAccessFile(file, "r").getChannel();
        ByteBuffer intBuffer = ByteBuffer.allocate(4);
        while (fileChannel.read(intBuffer) > 0){
            intBuffer.flip();
            int vinLength = intBuffer.getInt();
            ByteBuffer vinBuffer = ByteBuffer.allocate(vinLength);
            int vinBufferlength = fileChannel.read(vinBuffer);
            vinBuffer.flip();
            if(vinBufferlength != vinLength){
                System.out.println("read vin error");
            }
            Vin vin = new Vin(vinBuffer.array());
            intBuffer.flip();
            fileChannel.read(intBuffer);
            intBuffer.flip();
            int indexLength = intBuffer.getInt();
            List<Index> indices = new ArrayList<>();
            for(int i=0;i<indexLength;i++){
                intBuffer.flip();
                fileChannel.read(intBuffer);
                intBuffer.flip();
                int indexByteLen = intBuffer.getInt();
                ByteBuffer indexBytes = ByteBuffer.allocate(indexByteLen);
                fileChannel.read(indexBytes);
                indexBytes.flip();
                Index index = Index.uncompress(indexBytes.array());
                indices.add(index);
            }
            INDEX_MAP.put(vin,indices);
        }

    }

    public static void loadMapFromFile(File file)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    CopyOnWriteArrayList<Index> indices = new CopyOnWriteArrayList<>();
                    final String[] split1 = line.split("]");
                    final String s = split1[1];
                    final String[] s1 = s.split(" ");
                    for (String s2 : s1) {
                        final String[] split2 = s2.split("@");
                        indices.add(new Index(
                                Long.parseLong(split2[0])
                                , Long.parseLong(split2[1])
                                , Long.parseLong(split2[2])
                                , Integer.parseInt(split2[3])
                                , Integer.parseInt(split2[4])
                                , Integer.parseInt(split2[5])
                                , Integer.parseInt(split2[6])
                                ,AggBucket.fromString(split2[7])
//                                , DoubleIndexMap.fromString(split2[8])
                        ));
                    }
                    INDEX_MAP.put(new Vin(split1[0].getBytes(StandardCharsets.UTF_8)), indices);

                } catch (Exception e) {
                    System.out.println("loadMapIndexFromFile e" + e + "line " + line);
                }

            }
        }
        System.out.println("index file length : " + file.length());
        file.delete();
    }

    public static void main(String[] args) throws IOException {
        Map<Vin, CopyOnWriteArrayList<Index>> indexMap = new ConcurrentHashMap<>();
        CopyOnWriteArrayList<Index> list = new CopyOnWriteArrayList<>();
        final File file = new File("./data_dir/index.txt");
        if (!file.exists()) {
            file.createNewFile();
        }
//        saveMapToFile(file);
        loadMapFromFile(file);
        System.out.println(1);
    }
}
