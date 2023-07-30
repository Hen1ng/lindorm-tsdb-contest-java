package com.alibaba.lindorm.contest.test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MapTest {
    public static void main(String[] args) {
        Map<byte[], Integer> map = new HashMap<>();
        map.put("1234".getBytes(StandardCharsets.UTF_8), 1);
        final Integer integer = map.get("1234".getBytes(StandardCharsets.UTF_8));
        System.out.println(integer);
    }
}
