package com.alibaba.lindorm.contest.memory;

import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.structs.Vin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class VinDictMap {

    private static final byte[][] INDEX_VIN = new byte[30000][17];

    public static Map<Vin, Integer> getVinDictMap() {
        return VIN_DICT_MAP;
    }

    private static Map<Vin, Integer> VIN_DICT_MAP = new ConcurrentHashMap<>(30000);

    public static void put(Vin vin, Integer index) {
        VIN_DICT_MAP.put(vin, index);
        INDEX_VIN[index] = vin.getVin();
    }

    public static Integer get(Vin vin) {
        return VIN_DICT_MAP.get(vin);
    }

    public static byte[] get(int index) {
        return INDEX_VIN[index];
    }

    public static boolean contains(Vin vin) {
        return VIN_DICT_MAP.containsKey(vin);
    }

    public static void saveMapToFile(File file) {
        try {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (Vin vin1 : VIN_DICT_MAP.keySet()) {
                    writer.write(new String(vin1.getVin()));
                    writer.write(":");
                    writer.write(String.valueOf(VIN_DICT_MAP.get(vin1)));
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
                final String[] split = line.split(":");
                VIN_DICT_MAP.put(new Vin(split[0].getBytes(StandardCharsets.UTF_8)), Integer.parseInt(split[1]));
                INDEX_VIN[Integer.parseInt(split[1])] = split[0].getBytes(StandardCharsets.UTF_8);
            }
        }
        file.delete();
    }

}
