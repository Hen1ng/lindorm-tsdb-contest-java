package com.alibaba.lindorm.contest.compress;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GptSimple8B {

    public static byte[] compress(int[] input) {
        byte[] output = new byte[input.length * 4];
        int bitIndex = 0;
        int valueMask = 0;
        int valueCount = 0;
        int valueWidth = 8;
        int groupCount = 0;
        int byteIndex = 0;
        int bufferValue = 0;
        for (int value : input) {
            if (valueMask == 0) {
                // Start a new group
                bitIndex = byteIndex * 8;
                valueMask = 0x80;
                valueCount = 0;
                groupCount++;
                valueWidth = 8;
            }
            if (value >= (1 << valueWidth) || value < -(1 << (valueWidth - 1))) {
                // Value out of range, start a new group
                bitIndex = byteIndex * 8;
                valueMask = 0x80;
                valueCount = 0;
                groupCount++;
                valueWidth = 8;
            }
            int valueBits = value & ((1 << valueWidth) - 1);
            bufferValue |= valueBits << (32 - valueWidth - valueCount * valueWidth);
            valueCount++;
            valueMask >>>= 1;
            if (valueCount == 8) {
                // End of group, update value width
                if (groupCount % 2 == 0) {
                    valueWidth = 4;
                } else {
                    valueWidth = Math.max(1, (32 - Integer.numberOfLeadingZeros(bufferValue)) / 4);
                }
                valueCount = 0;
                valueMask = 0x80;
                output[byteIndex++] = (byte) ((valueWidth << 4) | 7);
                for (int i = 0; i < 4; i++) {
                    output[byteIndex + i] = (byte) (bufferValue >>> ((3 - i) * 8));
                }
                byteIndex += 4;
                bufferValue = 0;
            }
        }
        return Arrays.copyOf(output, byteIndex);
    }

    public static int[] decompress(byte[] input) {
        List<Integer> outputList = new ArrayList<Integer>();
        int bitIndex = 0;
        int byteIndex = 0;
        while (bitIndex < input.length * 8) {
            int groupHeader = input[byteIndex++] & 0xFF;
            int groupType = groupHeader >>> 4;
            int valueWidth = 0;
            int valueCount = 0;
            if (groupType == 0) {
                valueWidth = 1;
                valueCount = 28;
            } else if (groupType == 1) {
                valueWidth = 2;
                valueCount = 14;
            } else if (groupType == 2) {
                valueWidth = 4;
                valueCount = 7;
            } else if (groupType == 3) {
                valueWidth = groupHeader & 0x0F;
                valueCount = (input[byteIndex++] & 0xFF) << 24 |
                        (input[byteIndex++] & 0xFF) << 16 |
                        (input[byteIndex++] & 0xFF) << 8 |
                        (input[byteIndex++] & 0xFF);
            }
            for (int i = 0; i < valueCount; i++) {
                int valueBits = 0;
                for (int j = 0; j < valueWidth; j++) {
                    int bit = input[byteIndex] & 0xFF;
                    valueBits = (valueBits << 8) | bit;
                    byteIndex += (bitIndex % 8 == 0) ? 1 : 0;
                    bitIndex += 8;
                }
                int value = valueBits & ((1 << valueWidth) - 1);
                if ((value & (1 << (valueWidth - 1))) != 0) {
                    value |= (-1 << valueWidth);
                }
                outputList.add(value);
            }
        }
        int[] output = new int[outputList.size()];
        for (int i = 0; i < output.length; i++) {
            output[i] = outputList.get(i).intValue();
        }
        return output;
    }
}
