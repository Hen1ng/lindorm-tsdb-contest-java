package com.alibaba.lindorm.contest.index;

import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.Pair;

import java.io.*;
import java.nio.ByteBuffer;
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
            final List<Long> timestampList = index.getTimestampList();
            for (Long aLong : timestampList) {
                if (aLong >= timeLowerBound && aLong < timeUpperBound) {
                    resultSet.add(index);
                }
            }
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
