package com.alibaba.lindorm.contest.index;

import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.Pair;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MapIndex {

    private static final Map<Vin, CopyOnWriteArrayList<Index>> INDEX_MAP = new ConcurrentHashMap<>();

    public static synchronized void put(Vin vin, Index index) {
        CopyOnWriteArrayList<Index> indices = INDEX_MAP.get(vin);
        if (indices == null) {
            indices = new CopyOnWriteArrayList<>();
            indices.add(index);
            INDEX_MAP.put(vin, indices);
        } else {
            indices.add(index);
        }
    }

    public static synchronized List<Index> get(Vin vin, long timeLowerBound, long timeUpperBound) {
        CopyOnWriteArrayList<Index> indices = INDEX_MAP.get(vin);
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

    public static synchronized Pair<Index, Long> getLast(Vin vin) {
        CopyOnWriteArrayList<Index> indices = INDEX_MAP.get(vin);
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
                    final CopyOnWriteArrayList<Index> indices = INDEX_MAP.get(vin);
                    for (Index index : indices) {
                        writer.write(index.toString());
                        writer.write(" ");
                    }
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
                CopyOnWriteArrayList<Index> indices = new CopyOnWriteArrayList<>();
                final String[] split1 = line.split("]");
                final String s = split1[1];
                final String[] s1 = s.split(" ");
                for (String s2 : s1) {
                    final String[] split2 = s2.split(",");
                    indices.add(new Index(
                            Long.parseLong(split2[0])
                            , Long.parseLong(split2[1])
                            , Long.parseLong(split2[2])
                            , Integer.parseInt(split2[3])
                            , Integer.parseInt(split2[4])));
                }
                INDEX_MAP.put(new Vin(split1[0].getBytes(StandardCharsets.UTF_8)), indices);

            }
        }
    }

    public static void main(String[] args) throws IOException {
        Map<Vin, CopyOnWriteArrayList<Index>> indexMap = new ConcurrentHashMap<>();
        CopyOnWriteArrayList<Index> list = new CopyOnWriteArrayList<>();

        list.add(new Index(100L, 1, 0, 10, 3));
        list.add(new Index(200L, 19, 10, 8, 2));
        MapIndex.INDEX_MAP.put(new Vin("19293857575".getBytes(StandardCharsets.UTF_8)), list);

        CopyOnWriteArrayList<Index> list1 = new CopyOnWriteArrayList<>();
        list1.add(new Index(999, 1, 0, 10, 1));
        list1.add(new Index(1002, 19, 10, 8, 4));
        MapIndex.INDEX_MAP.put(new Vin("9hhahdiennhtlajd".getBytes(StandardCharsets.UTF_8)), list1);
        final File file = new File("./data_dir/index.txt");
        if (!file.exists()) {
            file.createNewFile();
        }
//        saveMapToFile(file);
        loadMapFromFile(file);
        System.out.println(1);
    }
}
