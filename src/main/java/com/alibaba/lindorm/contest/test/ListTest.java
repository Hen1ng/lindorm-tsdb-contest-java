package com.alibaba.lindorm.contest.test;

import java.util.concurrent.CopyOnWriteArrayList;

public class ListTest {
    public static void main(String[] args) {
        final CopyOnWriteArrayList<Integer> integers = new CopyOnWriteArrayList<>();
        new Thread() {
            @Override
            public void run() {
                for (int i = 0; i < 1000000; i++) {
                    integers.add(i);
                }

            }
        }.start();
        new Thread() {
            @Override
            public void run() {
                for (Integer integer : integers) {
                    System.out.println(integer);
                }
            }
        }.start();

    }
}
