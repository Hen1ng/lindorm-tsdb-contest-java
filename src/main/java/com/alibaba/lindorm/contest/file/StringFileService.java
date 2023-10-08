package com.alibaba.lindorm.contest.file;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StringFileService {
    private Map<String, StringFile> map = new ConcurrentHashMap<>(10);

    public void add(String key, String dataPath, int cacheSize) {
        map.put(key, new StringFile(dataPath, key, cacheSize));
    }

    public StringFile get(String key) {
        return map.get(key);
    }


    public int write(ByteBuffer byteBuffer, String key, int lineNum) {
        final StringFile stringFile = get(key);
        if (stringFile == null) {
            return -1;
        }
        return stringFile.write(byteBuffer);
    }

    public int size() {
        return map.keySet().size();
    }

    public Map<String, StringFile> getMap() {
        return map;
    }
}
