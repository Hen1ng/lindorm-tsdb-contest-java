package com.alibaba.lindorm.contest.test;

import java.nio.ByteBuffer;

public class ByteBufferTest {
    public static void main(String[] args) {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(12);
        byteBuffer.put((byte) 1);
        byteBuffer.put((byte) 2);
        ByteBuffer byteBuffer1 = byteBuffer;
        byteBuffer1.flip();
        final ByteBuffer slice = byteBuffer.slice();
        final int remaining = slice.remaining();
//        System.out.println(byteBuffer.capacity());
//        final ByteBuffer slice = byteBuffer.slice();
//        final ByteBuffer byteBuffer1 = ByteBuffer.allocateDirect(2);
//        byteBuffer1.put(slice);
//        byteBuffer1.flip();
        System.out.println(slice.capacity());
        System.out.println(slice.get());
        System.out.println(slice.get());

    }
}
