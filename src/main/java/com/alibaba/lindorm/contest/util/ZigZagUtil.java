package com.alibaba.lindorm.contest.util;

public class ZigZagUtil {

    public static int writeVarint32(int n, byte[] temp, int startPosition) {
        int length = 0;
        while (true) {
            if ((n & ~0x7F) == 0) {
                temp[startPosition++] = (byte) n;
                length++;
                // writeByteDirect((byte)n);
                break;
                // return;
            } else {
                temp[startPosition++] = (byte) ((n & 0x7F) | 0x80);
                length++;
                // writeByteDirect((byte)((n & 0x7F) | 0x80));
                n >>>= 7;
            }
        }
        return length;
    }

    public static int intToZigZag(int n) {
        return (n << 1) ^ (n >> 31);
    }

    /**
     * Convert from zigzag int to int.
     */
    public static int zigzagToInt(int n) {
        return (n >>> 1) ^ -(n & 1);
    }

    public static void main(String[] args) {
        byte[] temp = new byte[1024];
        final int i = intToZigZag(247167);
        final int i4 = zigzagToInt(i);
        final int i1 = writeVarint32(Integer.MIN_VALUE, temp, 0);
        System.out.println(i1);
        final int i2 = readFromBuffer(temp, 3);
        final int i3 = zigzagToInt(i2);
        System.out.println(i3);
    }

    //    public static int readVarint32() {
//        int result = 0;
//        int shift = 0;
//        if (trans_.getBytesRemainingInBuffer() >= 5) {
//            byte[] buf = trans_.getBuffer();
//            int pos = trans_.getBufferPosition();
//            int off = 0;
//            while (true) {
//                byte b = buf[pos + off];
//                result |= (b & 0x7f) << shift;
//                if ((b & 0x80) != 0x80) break;
//                shift += 7;
//                off++;
//            }
//            trans_.consumeBuffer(off + 1);
//        } else {
//            while (true) {
//                byte b = readByte();
//                result |= (b & 0x7f) << shift;
//                if ((b & 0x80) != 0x80) break;
//                shift += 7;
//            }
//        }
//        return result;
//    }
    public static int readFromBuffer(byte[] temp, int maxSize) {

        int ret = 0;

        int offset = 0;

        for (int i = 0; i < maxSize; i++, offset += 7) {

            byte n = temp[i];

            if ((n & 0x80) != 0x80) {

                ret |= (n << offset);

                break;

            } else {

                ret |= ((n & 0x7f) << offset);

            }

        }

        return ret;

    }

}
