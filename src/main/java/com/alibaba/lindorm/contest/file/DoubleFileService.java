package com.alibaba.lindorm.contest.file;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DoubleFileService {
    private Map<String, DoubleFile> map = new ConcurrentHashMap<>(10);

    public void add(String key, String dataPath) {
        map.put(key, new DoubleFile(dataPath, key));
    }

    public DoubleFile get(String key) {
        return map.get(key);
    }

    public int write(double[] doubles, String key) {
        final DoubleFile doubleFile = get(key);
        if (doubleFile == null) {
            return -1;
        }
        return doubleFile.write(doubles);
    }

    public int write(double d, String key, int lineNum) {
        final DoubleFile doubleFile = get(key);
        if (doubleFile == null) {
            return -1;
        }
        return doubleFile.write(d, lineNum);
    }

    public int size() {
        return map.keySet().size();
    }

    public Map<String, DoubleFile> getMap() {
        return map;
    }
}
