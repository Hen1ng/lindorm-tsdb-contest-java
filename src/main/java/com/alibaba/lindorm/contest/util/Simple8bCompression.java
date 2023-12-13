package com.alibaba.lindorm.contest.util;

import java.util.ArrayList;
import java.util.List;

public class Simple8bCompression {

    public static final int[] BLOCK_SIZES = { 28, 14, 9, 7, 5, 4, 3, 2 };
    public static final int[] BIT_COUNTS = { 1, 2, 3, 4, 5, 7, 9, 14 };
    public static final int BLOCKS_PER_CHUNK = 64;
    public static final int BITS_PER_BLOCK = 32;

    public static void main(String[] args) {
        int[] test = { 4424, 6533, 7742, 9239, 1234, 1233, 5042, 9999 };
        final int[] compress = compress(test);
    }

    public static int[] compress(int[] data) {
        List<Integer> compressedData = new ArrayList<Integer>();
        int index = 0;

        while (index < data.length) {
            int bitCount = 0;
            int blockSize = 0;

            // Determine the optimal block size and bit count
            for (int i = 0; i < BLOCK_SIZES.length; i++) {
                if (index + BLOCK_SIZES[i] > data.length) {
                    break;
                }

                int max = (1 << BIT_COUNTS[i]) - 1;
                boolean fitsInBlock = true;

                for (int j = 0; j < BLOCK_SIZES[i]; j++) {
                    if (data[index + j] > max) {
                        fitsInBlock = false;
                        break;
                    }
                }

                if (fitsInBlock) {
                    blockSize = BLOCK_SIZES[i];
                    bitCount = BIT_COUNTS[i];
                }
            }

            // Pack the block of integers
            int[] block = new int[blockSize];

            for (int i = 0; i < blockSize; i++) {
                block[i] = data[index + i];
            }

            int packedBlock = packBlock(block, bitCount);
            compressedData.add(packedBlock);

            index += blockSize;
        }

        // Copy compressed data to output array
        int[] compressedArray = new int[compressedData.size()];

        for (int i = 0; i < compressedData.size(); i++) {
            compressedArray[i] = compressedData.get(i);
        }

        return compressedArray;
    }

    private static int packBlock(int[] block, int bitCount) {
        int packedBlock = bitCount;

        for (int i = 0; i < block.length; i++) {
            packedBlock |= block[i] << (BITS_PER_BLOCK - ((i + 1) * bitCount));
        }

        return packedBlock;
    }

    public static int[] decompress(int[] compressedData) {
        List<Integer> decompressedData = new ArrayList<Integer>();

        for (int i = 0; i < compressedData.length; i++) {
            int bitCount = compressedData[i] & 0x0F;
            int blockSize = BLOCKS_PER_CHUNK * bitCount;

            int[] block = new int[blockSize];

            for (int j = 0; j < blockSize; j++) {
                int shift = BITS_PER_BLOCK - ((j + 1) * bitCount);
                int value = (compressedData[i] >> shift) & ((1 << bitCount) - 1);
                block[j] = value;
            }

            for (int j = 0; j < blockSize; j++) {
                decompressedData.add(block[j]);
            }
        }

        // Copy decompressed data to output array
        int[] decompressedArray = new int[decompressedData.size()];

        for (int i = 0; i < decompressedData.size(); i++) {
            decompressedArray[i] = decompressedData.get(i);
        }

        return decompressedArray;
    }
}

