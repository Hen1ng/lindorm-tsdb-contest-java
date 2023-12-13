package com.alibaba.lindorm.contest.test;

import com.alibaba.lindorm.contest.util.list.SortedList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;

public class ListTest {
    public static void main(String[] args) {
        var i = 0;
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
