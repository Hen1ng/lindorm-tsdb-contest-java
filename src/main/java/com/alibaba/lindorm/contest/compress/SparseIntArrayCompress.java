package com.alibaba.lindorm.contest.compress;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SparseIntArrayCompress {

    public static void main(String[] args) {
        int[] ints = new int[] {
                0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,0,2,1,5,4,5,3,3,5,1,2,7,0,0,7,2,1,4,7,6,6,2,5,6,1,6,6,1,4,7,0,7,2,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
        };
        final byte[] compress = compress(ints);
        final Map<Integer, Integer> decompress = decompress(compress);
        System.out.println(1);
    }

    public static byte[] compress(int[] ints) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < ints.length; i++) {
            if (ints[i] != 0) {
                if (ints[i] > Byte.MAX_VALUE) {
                    throw new RuntimeException("SparseIntArrayCompress int large than byte max value");
                }
                list.add(i);
                list.add(ints[i]);
            }
        }
        final ByteBuffer allocate = ByteBuffer.allocate(list.size() * 2 + list.size()/2);
        for (int i = 0; i < list.size(); i = i + 2) {
            allocate.putInt(list.get(i));
            int m = list.get(i + 1);
            allocate.put((byte) m);
        }
        return allocate.array();
    }

    public static Map<Integer, Integer> decompress(byte[] bytes) {
        final ByteBuffer wrap = ByteBuffer.wrap(bytes);
        Map<Integer, Integer> map = new HashMap<>();
        while (wrap.hasRemaining()) {
            map.put(wrap.getInt(), (int) wrap.get());
        }
        return map;
    }
}
