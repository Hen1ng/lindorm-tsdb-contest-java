package com.alibaba.lindorm.contest.util;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

public class BytesUtil {

    public static byte[] genWriteByte(long id, String userId, String name, long salary) {
        final byte[] bytes = new byte[272];
        int index = 0;
        byte[] bytes1 = reserveArray(longToBytes(id));
        for (int i = 0; i < 8; i++) {
            bytes[index] = bytes1[i];
            index++;
        }
        final byte[] userIdBytes = userId.getBytes();
        for (int i = 0; i < 128; ++i) {
            if (i > userIdBytes.length - 1) {
                break;
            }
            bytes[index++] = userIdBytes[i];
        }
        final byte[] nameBytes = name.getBytes();
        for (int i = 0; i < 128; ++i) {
            if (i > nameBytes.length - 1) {
                break;
            }
            bytes[index++] = nameBytes[i];
        }
        byte[] bytes2 = reserveArray(longToBytes(salary));
        for (int i = 0; i < 8; i++) {
            bytes[index] = bytes2[i];
            index++;
        }
        return bytes;
    }

    public static byte[] long2Bytes(long num) {
        byte[] byteNum = new byte[8];
        for (int ix = 0; ix < 8; ++ix) {
            int offset = 64 - (ix + 1) * 8;
            byteNum[ix] = (byte) ((num >> offset) & 0xff);
        }
        return byteNum;
    }

    public static long bytes2Long(byte[] byteNum) {
        long num = 0;
        for (int ix = 7; ix >= 0; --ix) {
            num <<= 8;
            num |= (byteNum[ix] & 0xff);
        }
        return num;
    }

    public static long bytes2Long(byte[] bytes, int start) {
        long num = 0;
        for (int ix = start + 7; ix >= start; --ix) {
            num <<= 8;
            num |= (bytes[ix] & 0xff);
        }
        return num;
    }

    public static String getRandomString(int length) {
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(62);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }

    public static long bytesToLong(byte[] input, int offset, boolean littleEndian) {
        // 将byte[] 封装为 ByteBuffer
        ByteBuffer buffer = ByteBuffer.wrap(input, offset, 8);
        if (littleEndian) {
            // ByteBuffer.order(ByteOrder) 方法指定字节序,即大小端模式(BIG_ENDIAN/LITTLE_ENDIAN)
            // ByteBuffer 默认为大端(BIG_ENDIAN)模式
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        return buffer.getLong();
    }

    public static byte[] reserveArray(byte[] bytes) {
        byte[] bytes1 = new byte[bytes.length];
        int j = 0;
        for (int i = bytes.length - 1; i >= 0; i--) {
            bytes1[j] = bytes[i];
            j++;
        }
        return bytes1;
    }

    public static byte[] longToBytes(long x) {
        final ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(0, x);
        return buffer.array();
    }

    public static int toInt(byte[] bytes, int offset, int length) {
        int n = 0;
        for (int i = offset; i < (offset + length); i++) {
            n <<= 8;
            n ^= bytes[i] & 0xFF;
        }
        return n;
    }

    public static void main(String[] args) {
        final byte[] bytes = new byte[2];
        bytes[0] = 0;
        bytes[1] = 1;
        System.out.println(toInt(bytes, 0 , 2));
    }
}
