package com.alibaba.lindorm.contest.index;

import com.alibaba.lindorm.contest.util.Constants;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DoubleIndexMap {

    public void setIndexMap(Map<String, Integer> indexMap) {
        this.indexMap = indexMap;
    }

    Map<String, Integer> indexMap = new ConcurrentHashMap<>(Constants.FLOAT_NUMS);

    public void put(String key, int offset) {
        indexMap.put(key, offset);
    }

    public int get(String key) {
        return indexMap.get(key);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String key : indexMap.keySet()) {
            sb.append(key);
            sb.append("%");
            sb.append(indexMap.get(key));
            sb.append(",");
        }
        return sb.toString();
    }

    public static DoubleIndexMap fromString(String str) {
        final DoubleIndexMap doubleIndexMap = new DoubleIndexMap();
        Map<String, Integer> indexMap = new ConcurrentHashMap<>(Constants.FLOAT_NUMS);
        final String[] split = str.split(",");
        for (String s : split) {
            if (s != null) {
                final String[] split1 = s.split("%");
                indexMap.put(split1[0], Integer.parseInt(split1[1]));
            }
        }
        doubleIndexMap.setIndexMap(indexMap);
        return doubleIndexMap;
    }

    public static void main(String[] args) {
        final DoubleIndexMap doubleIndexMap = new DoubleIndexMap();
        doubleIndexMap.put("hahah", 1);
        doubleIndexMap.put("hahah2", 2);
        doubleIndexMap.put("hah2", 3);
        final String string = doubleIndexMap.toString();
        final DoubleIndexMap doubleIndexMap1 = fromString(string);

    }
}
