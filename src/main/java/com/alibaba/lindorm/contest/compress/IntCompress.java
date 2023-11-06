package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.compress.intcodec.simple.Simple9Codes;
import com.alibaba.lindorm.contest.compress.intcodec2.integercompression.*;
import com.alibaba.lindorm.contest.file.TSFileService;
import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.util.*;
import com.alibaba.lindorm.contest.util.ZigZagUtil;
import com.github.luben.zstd.Zstd;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IntCompress {
//    static Composition codec = new Composition(new NewPFDS9(), new VariableByte());

    static int[] testNumReal = new int[8400];
    static int originLength;
    //    static int[] testNum2 = new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 513, 1025};
    public static ThreadLocal<BitSet> SHORT_ARRTY_TYPE_THREAD_LOCAL = ThreadLocal.withInitial(() -> BitSet.valueOf(new byte[2]));

//    static long[] testNum4 = new long[1575];

//    static {
//        Random random = new Random();
//        for (int i = 0; i < 1575; i++) {
//            testNum4[i] = Math.abs(random.nextInt(30000));
//        }
//    }

    public static void setBit(int index, byte[] bytes) {
        int byteIndex = index / 8; // 每个字节有8位
        int bitIndex = index % 8;

        // 创建一个字节，其中只有所需的那一位被设置为1
        byte mask = (byte) (1 << (7 - bitIndex)); // 7 - bitIndex是因为我们从左边的最高位开始计数

        bytes[byteIndex] |= mask; // 使用OR操作来设置所需的那一位
    }

    public static boolean getBit(int index, byte[] bytes) {
        int byteIndex = index / 8;  // Every byte has 8 bits
        int bitIndex = index % 8;

        // Create a byte where only the desired bit is set to 1
        byte mask = (byte) (1 << (7 - bitIndex));  // 7 - bitIndex because we count from the leftmost highest bit

        // Use AND operation to check if the desired bit is set
        return (bytes[byteIndex] & mask) != 0;
    }

    public static void setTwoBit(byte[] values, int index, int value) {
        if (index < 0 || index > (values.length * 8) - 2) {
            throw new IllegalArgumentException("Index out of range.");
        }
        if (value < 0 || value > 3) {
            throw new IllegalArgumentException("Value must be between 0 and 3 (inclusive).");
        }

        int byteIndex = index / 4; // 2 bits represent a value. So, 4 values per byte.
        int bitIndex = (index % 4) * 2;

        // Clear the two bits at the position
        values[byteIndex] &= ~(3 << bitIndex);  // 3 in binary is 11. So, this clears the two bits at the position.

        // Set the two bits with the value
        values[byteIndex] |= (value << bitIndex);
    }

    public static int getTwoBit(byte[] values, int index) {
        if (index < 0 || index > (values.length * 8) - 2) {
            throw new IllegalArgumentException("Index out of range.");
        }

        int byteIndex = index / 4; // 2 bits represent a value. So, 4 values per byte.
        int bitIndex = (index % 4) * 2;

        // Extract the two bits at the position
        return (values[byteIndex] >> bitIndex) & 3; // 3 in binary is 11. This will mask the two bits we are interested in.
    }

    public static int[] readIntsFromFile(String fileName) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line = br.readLine();
            if (line != null) {
                String[] numberStrings = line.split(",");
                int[] numbers = new int[numberStrings.length];
                for (int i = 0; i < numberStrings.length; i++) {
                    numbers[i] = Integer.parseInt(numberStrings[i].trim());
                }
                return numbers;
            }
        }
        return new int[0];
    }

    /**
     * Splits a one-dimensional array into a two-dimensional array.
     *
     * @param array the one-dimensional array to be split
     * @param size  the size of the sub-arrays
     * @return a two-dimensional array
     */
    public static int[][] splitArray(int[] array, int size, int num) {
        int n = array.length;
        int chunks = (n + size - 1) / size;

        int[][] result = new int[chunks][];

        Random random = new Random();

        for (int i = 0; i < chunks; i++) {
            int start = i * size;
            int length = Math.min(n - start, size);

            int[] temp = new int[length];
            System.arraycopy(array, start, temp, 0, length);
            result[i] = temp;
        }

        return result;
    }

    public static int[] combineArray(int[][] splitArray) {
        int totalLength = 0;
        for (int[] subArray : splitArray) {
            totalLength += subArray.length;
        }

        int[] result = new int[totalLength];
        int position = 0;
        for (int[] subArray : splitArray) {
            for (int value : subArray) {
                result[position++] = value;
            }
        }

        return result;
    }

    static {
        String fileName = "int.txt";  // 替换为你的文件路径
        try {
            testNumReal = readIntsFromFile(fileName);
            System.out.println(testNumReal.length);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        for(int i=0;i<40;i++){
            Set<Integer> dependencies = dependencyFinder.findDependencies(i);
            System.out.println(dependencies);
        }
        int[] data = testNumReal.clone();
//        data = combineArray(splitArray);
//        final byte[] compress = compress(data);
//        final byte[] compress4 = compress4(data);
//        System.out.println("cost:" + (System.currentTimeMillis() - start) + " ms");
//        final int[] decompress = decompress(compress);
//        boolean b = Arrays.equals(testNum3, decompress);
//        for (int i = 0; i < data.length; i++) {
//            if (data[i] != decompress[i]) {
//                System.out.println("index i : " + i + " data :" + data[i] + " decompress : " + decompress[i]);
//            }
//        }
//        long[] longs1 = new long[testNumReal.length];
//        for (int i = 0; i < longs1.length; i++) {
//            longs1[i] = testNumReal[i];
//        }
//        int[] data = testNumReal.clone();
//        int[] data1 = testNumReal.clone();
//        preProcess(data1, 210);
//        int[][] splitArray = splitArray(data1, 210, 100 * 35);
//        int index = 0;
//        for (int[] ints : splitArray) {
////            toGapArray(ints);
//            System.out.printf("%d:", index++);
//            for (int anInt : ints) {
//                System.out.printf("%10d,", anInt);
//            }
//            System.out.println("");
//        }
//        preProcess(data1, 210);
//        recoveryProcess(data1, 210);
//
//        System.out.println(Arrays.equals(data1,data));
        byte[] bytes = compressOrigin(data, 210);
        long byteLength = bytes.length;
////        final int[] output = new int[data.length];
        final int[] longs = decompressOrigin(bytes, 210);
//        final byte[] bytes1 = compressZstd(data1);
        for (int i = 0; i < longs.length; i++) {
            if (longs[i] != testNumReal[i]) {
                System.out.printf("%d:%d->%d\n", i, longs[i], testNumReal[i]);
            }
        }
//        boolean a = Arrays.equals(longs, testNumReal);
////        for (int i = 0; i < 40; i++) {
//            int columnIndex = 15;
//            final int[] singleColumn = getSingleColumn(ByteBuffer.wrap(bytes), 210, columnIndex, intCompressResult);
//            final boolean equals = ArrayUtils.equals(testNumReal, 210 * columnIndex, +210 * (columnIndex + 1), singleColumn);
//            if (!equals) {
//                System.out.println("wrong result, column index : " + columnIndex);
//            }
//        }

//
//        final byte[] compress = compress(data1);
//        final int[] decompress = decompress(compress);
//
//        final ByteBuffer allocate = ByteBuffer.allocate(data.length * 4);
//        for (int datum : data) {
//            allocate.putInt(datum);
//        }
//        final GzipCompress gzipCompress = new GzipCompress();
//        final byte[] compress1 = gzipCompress.compress(allocate.array());
//        final byte[] bytes1 = gzipCompress.deCompress(compress1);
//
//
//        final byte[] compress2 = ZstdCompress.compress(allocate.array(), 12);
//        final byte[] decompress2 = ZstdCompress.decompress(compress2);
//        final byte[] compress3 = gzipCompress.compress(compress2);
//        final boolean equals = Arrays.equals(testNum3, longs);
//        System.out.println(a);
        System.out.println("compress rate : " + 1.0d * byteLength / (data.length * 4));
    }

//    public static final ThreadLocal<long[]> INT_ARRAY_BUFFER = ThreadLocal.withInitial(() -> new long[Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS]);

    public static int UpperBoundByte(int valueSize) {
        return ((valueSize + 7) / 8);
    }

    public static ArrayList<Pair<Integer, Integer>> subSet;

    public static DependencyFinder dependencyFinder;
    public static ArrayList<Integer> notFirstDelta;

    public static ArrayList<Integer> notSecondDelta;
    public static ArrayList<Integer> divSet;

    public static ArrayList<Integer> split1 = new ArrayList<>(10);
    public static ArrayList<Integer> split2 = new ArrayList<>(10);
    //    public static ArrayList<Integer> split3 = new ArrayList<>(10);
//    public static ArrayList<Integer> split4 = new ArrayList<>(10);
    public static int[] relation = new int[40];

    static {
        subSet = new ArrayList<>();
        subSet.add(new Pair<>(3, 4));
        subSet.add(new Pair<>(4, 6));
        subSet.add(new Pair<>(6, 19));
        subSet.add(new Pair<>(15, 19));
        subSet.add(new Pair<>(19, 20));
        subSet.add(new Pair<>(12, 39));
        subSet.add(new Pair<>(34, 39));
        subSet.add(new Pair<>(11, 39));
        dependencyFinder = new DependencyFinder(subSet);

        relation[3] = 4;
        relation[4] = 6;
        relation[6] = 19;
        relation[11] = 39;
        relation[12] = 39;
        relation[15] = 19;
        relation[19] = 20;
        relation[34] = 39;

        /*split1*/

        split1.add(3);
        split1.add(4);
        split1.add(6);
        split1.add(11);
        split1.add(12);
        split1.add(15);
        split1.add(19);
        split1.add(20);
        split1.add(34);
        split1.add(39);


        /*split3*/
        split2.add(0);
        split2.add(1);
        split2.add(2);
        split2.add(5);
        split2.add(6);
        split2.add(7);
        split2.add(8);
        split2.add(9);
        split2.add(10);
        split2.add(13);
        split2.add(14);
        split2.add(16);
        split2.add(17);
        split2.add(18);
        split2.add(21);
        split2.add(22);
        split2.add(23);
        split2.add(24);
        split2.add(25);
        split2.add(26);

        /*split4*/
        split2.add(27);
        split2.add(28);
        split2.add(29);
        split2.add(30);
        split2.add(31);
        split2.add(32);
        split2.add(33);
        split2.add(35);
        split2.add(36);
        split2.add(37);
        split2.add(38);

        notFirstDelta = new ArrayList<>();


        notSecondDelta = new ArrayList<>();

        notSecondDelta.add(1);
        notSecondDelta.add(0);
//        notSecondDelta.add(2);
        notSecondDelta.add(4);
//        notSecondDelta.add(5);
        notSecondDelta.add(3);

        notSecondDelta.add(6);
        notSecondDelta.add(7);
        notSecondDelta.add(8);
//        notSecondDelta.add(9);
        notSecondDelta.add(10);
        notSecondDelta.add(11);
        notSecondDelta.add(12);
        notSecondDelta.add(13);
        notSecondDelta.add(14);
        notSecondDelta.add(15);
        notSecondDelta.add(16);
        notSecondDelta.add(17);
//        notSecondDelta.add(18);
        notSecondDelta.add(19);
        notSecondDelta.add(20);
//        notSecondDelta.add(21);
//        notSecondDelta.add(22);
//        notSecondDelta.add(23);
        notSecondDelta.add(24);
//        notSecondDelta.add(25);
//        notSecondDelta.add(26);
//        notSecondDelta.add(27);
//        notSecondDelta.add(28);
        notSecondDelta.add(29);
//        notSecondDelta.add(30);
//        notSecondDelta.add(31);
        notSecondDelta.add(32);
//        notSecondDelta.add(33);
        notSecondDelta.add(35);
        notSecondDelta.add(34);
        notSecondDelta.add(36);
//        notSecondDelta.add(37);
//        notSecondDelta.add(38);
        notSecondDelta.add(39);
        divSet = new ArrayList<>();
        divSet.add(19);

    }

    public static void setFourBit(byte[] values, int index, int value) {
        if (index < 0 || index > (values.length * 2) - 1) {
            throw new IllegalArgumentException("Index out of range.");
        }
        if (value < 0 || value > 15) { // 4 bits can represent values from 0 to 15
            throw new IllegalArgumentException("Value must be between 0 and 15 (inclusive).");
        }

        int byteIndex = index / 2; // 4 bits represent a value. So, 2 values per byte.
        int bitIndex = (index % 2) * 4; // either 0 or 4 depending on which half of the byte we're setting

        // Clear the four bits at the position
        values[byteIndex] &= ~(15 << bitIndex);  // 15 in binary is 1111. This clears the four bits at the position.

        // Set the four bits with the value
        values[byteIndex] |= (value << bitIndex);
    }

    public static int getFourBit(byte[] values, int index) {
        if (index < 0 || index > (values.length * 2) - 1) {
            throw new IllegalArgumentException("Index out of range.");
        }

        int byteIndex = index / 2; // 4 bits represent a value. So, 2 values per byte.
        int bitIndex = (index % 2) * 4; // either 0 or 4 depending on which half of the byte we're reading

        // Extract the four bits at the position
        return (values[byteIndex] >> bitIndex) & 15; // 15 in binary is 1111. This will mask the four bits we are interested in.
    }

    public static void preProcess(int[] ints, int valueSize) {
        // delta
        for (int i = 0; i < 40; i++) {
            if (notFirstDelta.contains(i)) continue;
            int start = i * valueSize;
            int end = (i + 1) * valueSize;
            int pre = ints[start];
            for (int j = start + 1; j < end; j++) {
                int tmp = ints[j];
                ints[j] = ints[j] - pre;
                pre = tmp;
            }
        }
        for (Pair<Integer, Integer> integerIntegerPair : subSet) {
            int start = integerIntegerPair.getLeft() * valueSize;
            int end = start + valueSize;
            int subIndex = integerIntegerPair.getRight();
            for (int i = start; i < end; i++) {
                ints[i] -= ints[subIndex * valueSize + (i - start)];
            }
        }
//        for (Integer integer : divSet) {
//            int start = integer * valueSize;
//            int end = start + valueSize;
//            for (int i = start+1; i < end; i++) {
//                if(ints[i]%1000!=0){
//                    System.out.printf("%d%%1000!=0",ints[i]);
//                }
//                ints[i] /= 1000;
//            }
//        }
        for (int i = 0; i < 40; i++) {
            if (notSecondDelta.contains(i)) continue;
            int start = i * valueSize;
            int end = (i + 1) * valueSize;
            int pre = ints[start + 1];
            for (int j = start + 2; j < end; j++) {
                int tmp = ints[j];
                ints[j] = ints[j] - pre;
                pre = tmp;
            }
        }
    }

    public static void recoverProcessBySingple(int[] ints,int valueSize,int index,Set<Integer> dependencies){
        for (int i = 0; i < 40; i++) {
            if (notSecondDelta.contains(i)) continue;
            if (!dependencies.contains(i))continue;
            int start = i * valueSize;
            int end = (i + 1) * valueSize;
            for (int j = start + 2; j < end; j++) {
                ints[j] += ints[j - 1];
            }
        }
        Stack<Pair<Integer, Integer>> stack = new Stack<>();
        for (Pair<Integer, Integer> integerIntegerPair : subSet) {
            stack.add(integerIntegerPair);
        }
        while (!stack.isEmpty()) {
            Pair<Integer, Integer> pop = stack.pop();
            Integer left = pop.getLeft();
            if(!dependencies.contains(left))continue;
            int start = pop.getLeft() * valueSize;
            int end = start + valueSize;
            int subIndex = pop.getRight();
            for (int i = start; i < end; i++) {
                ints[i] += ints[subIndex * valueSize + (i - start)];
            }
        }
        for (int i = 0; i < 40; i++) {
            if (notFirstDelta.contains(i)) continue;
            if (!dependencies.contains(i))continue;
            int start = i * valueSize;
            int end = (i + 1) * valueSize;
            for (int j = start + 1; j < end; j++) {
                ints[j] += ints[j - 1];
            }
        }
    }

    public static void recoveryProcess(int[] ints, int valueSize) {
        for (int i = 0; i < 40; i++) {
            if (notSecondDelta.contains(i)) continue;
            int start = i * valueSize;
            int end = (i + 1) * valueSize;
            for (int j = start + 2; j < end; j++) {
                ints[j] += ints[j - 1];
            }
        }
        Stack<Pair<Integer, Integer>> stack = new Stack<>();
        for (Pair<Integer, Integer> integerIntegerPair : subSet) {
            stack.add(integerIntegerPair);
        }
        while (!stack.isEmpty()) {
            Pair<Integer, Integer> pop = stack.pop();
            int start = pop.getLeft() * valueSize;
            int end = start + valueSize;
            int subIndex = pop.getRight();
            for (int i = start; i < end; i++) {
                ints[i] += ints[subIndex * valueSize + (i - start)];
            }
        }
        for (int i = 0; i < 40; i++) {
            if (notFirstDelta.contains(i)) continue;
            int start = i * valueSize;
            int end = (i + 1) * valueSize;
            for (int j = start + 1; j < end; j++) {
                ints[j] += ints[j - 1];
            }
        }
    }

    public static IntCompressResult compress4(int[] ints, int valueSize) {
        List<ByteBuffer> arrayList = new ArrayList<>();
        List<Integer> notUseDictArray = new ArrayList<>();
        int start = 0;
        int length = ints.length;
        int totalLength = 0;
        byte[] compressType = new byte[5];
        int batchIndex = 0;
        preProcess(ints, valueSize);
        while (start < length) {
            int count = 0;
            Map<Integer, Integer> map = new HashMap<>();
            Map<Integer, Integer> invMap = new HashMap<>();
            boolean isUseMap = true;
            for (int index = start + 1; index < start + valueSize; index++) {
                if (isUseMap && !map.containsKey(ints[index])) {
                    map.put(ints[index], count);
                    invMap.put(count, ints[start]);
                    count++;
                }
                if (map.size() > 4) {
                    isUseMap = false;
                    break;
                }
            }
            if (isUseMap) {
                setBit(batchIndex, compressType);
                // write dict
                byte dictSize = (byte) map.size();
                if (dictSize > 16) dictSize = 64;
                else if (dictSize > 4) dictSize = 16;
//                else if (dictSize > 4) dictSize = 8;
                else if (dictSize > 2) dictSize = 4;
                int bitSize = valueSize;
                if (dictSize == 64) bitSize *= 8;
                if (dictSize == 16) bitSize *= 4;
                if (dictSize == 4) bitSize *= 2;
                if (dictSize == 1) bitSize = 0;
                ByteBuffer allocate = ByteBuffer.allocate(4 + 1 + dictSize * 4 + UpperBoundByte(bitSize));
                allocate.putInt(ints[start]);
                // put dictSize
                allocate.put(dictSize);
                // put dict
                for (int i = 0; i < dictSize; i++) {
                    boolean exist = false;
                    for (Integer integer : map.keySet()) {
                        if (map.get(integer) == i) {
                            allocate.putInt(integer);
                            exist = true;
                            break;
                        }
                    }
                    if (!exist) allocate.putInt(0);
                }
                // put index value
                if (dictSize != 0) {
                    if (dictSize == 2) {
                        byte[] bitSet = new byte[UpperBoundByte(bitSize)];
                        for (int j = start + 1; j < start + valueSize; j++) {
                            Integer i = map.get(ints[j]);
                            if (i == 1) {
                                setBit(j - start, bitSet);
                            }
                        }
                        allocate.put(bitSet);
                    } else if (dictSize == 4) {
                        byte[] bitSet = new byte[UpperBoundByte(bitSize)];
                        for (int j = start + 1; j < start + valueSize; j++) {
                            Integer i = map.get(ints[j]);
                            setTwoBit(bitSet, j - start, i);
                        }
                        allocate.put(bitSet);
                    } else if (dictSize == 16) {
                        byte[] bitSet = new byte[UpperBoundByte(bitSize)];
                        for (int j = start + 1; j < start + valueSize; j++) {
                            Integer i = map.get(ints[j]);
                            setFourBit(bitSet, j - start, i);
                        }
                        allocate.put(bitSet);
                    } else if (dictSize == 64) {
                        byte[] bitSet = new byte[UpperBoundByte(bitSize)];
                        for (int j = start + 1; j < start + valueSize; j++) {
                            int i = map.get(ints[j]);
                            bitSet[j - start] = (byte) i;
                        }
                        allocate.put(bitSet);
                    }
                }
                totalLength += allocate.array().length;
                arrayList.add(allocate);
            } else {
                long[] longs2 = new long[valueSize];
                for (int j = start; j < start + valueSize; j++) {
                    longs2[j - start] = ints[j];
                }
                byte[] bytes = compress2WithoutZstd(longs2);
                ByteBuffer allocate = ByteBuffer.allocate(4 + bytes.length);
                allocate.putInt(bytes.length);
                allocate.put(bytes);
                totalLength += allocate.array().length;
                arrayList.add(allocate);
            }
            batchIndex++;
            start += valueSize;
        }

        // compressType 5B
        //

        List<ByteBuffer> list1 = new ArrayList<>();
        List<ByteBuffer> list2 = new ArrayList<>();
//        List<ByteBuffer> list3 = new ArrayList<>(valueSize * 10);
//        List<ByteBuffer> list4 = new ArrayList<>(valueSize * 11);
        int total1 = 0;
        int total2 = 0;
//        int total3 = 0;
//        int total4 = 0;
        int j = 0;
        for (ByteBuffer byteBuffer : arrayList) {
//            allocate.put(byteBuffer.array());
            if (split1.contains(j)) {
                list1.add(byteBuffer);
                total1 += byteBuffer.capacity();
            }
            if (split2.contains(j)) {
                list2.add(byteBuffer);
                total2 += byteBuffer.capacity();
            }
//            if (split3.contains(j)) {
//                list3.add(byteBuffer);
//                total3 += byteBuffer.capacity();
//            }
//            if (split4.contains(j)) {
//                list4.add(byteBuffer);
//                total4 += byteBuffer.capacity();
//            }
            j++;
        }
        final ByteBuffer allocate1 = ByteBuffer.allocate(total1);
        for (ByteBuffer byteBuffer : list1) {
            allocate1.put(byteBuffer.array());
        }
        byte[] compress1 = Zstd.compress(allocate1.array(), 3);
        final ByteBuffer allocate2 = ByteBuffer.allocate(total2);
        for (ByteBuffer byteBuffer : list2) {
            allocate2.put(byteBuffer.array());
        }
        byte[] compress2 = Zstd.compress(allocate2.array(), 3);

//        final ByteBuffer allocate3 = ByteBuffer.allocate(total3);
//        for (ByteBuffer byteBuffer : list3) {
//            allocate3.put(byteBuffer.array());
//        }
//        byte[] compress3 = Zstd.compress(allocate3.array(), 3);
//
//        final ByteBuffer allocate4 = ByteBuffer.allocate(total4);
//        for (ByteBuffer byteBuffer : list4) {
//            allocate4.put(byteBuffer.array());
//        }
//        byte[] compress4 = Zstd.compress(allocate4.array(), 3);

        ByteBuffer allocate = ByteBuffer.allocate(compress1.length + compress2.length);
//        allocate.put(compressType);
//        allocate.putShort((short)compress1.length);
        allocate.put(compress1);
//        allocate.putShort((short)compress2.length);
        allocate.put(compress2);
        final short[] ints1 = new short[2];
        final int[] ints2 = new int[2];
        ints1[0] = (short) compress1.length;
        ints1[1] = (short) compress2.length;
        ints2[0] = allocate1.array().length;
        ints2[1] = allocate2.array().length;
//        allocate.putShort((short)compress3.length);
//        allocate.put(compress3);
//        allocate.putShort((short)compress4.length);
//        allocate.put(compress4);


//        byte[] compress = allocate.array();
//        ByteBuffer buffer = ByteBuffer.allocate(4 + allocate.capacity());
//        buffer.putInt(allocate.array().length);
//        buffer.put(allocate.array());
        return new IntCompressResult(compressType, ints1, allocate.array(), ints2);
    }

    public static class IntCompressResult {
        public IntCompressResult(byte[] compressType, short[] splitLength, byte[] data, int[] beforeCompressLength) {
            this.compressType = compressType;
            this.splitLength = splitLength;
            this.data = data;
            this.beforeCompressLength = beforeCompressLength;
        }

        public IntCompressResult(byte[] bytes) {
            final ByteBuffer wrap = ByteBuffer.wrap(bytes);
            this.compressType = new byte[5];
            wrap.get(compressType, 0, 5);
            this.splitLength = new short[2];
            splitLength[0] = wrap.getShort();
            splitLength[1] = wrap.getShort();
            this.beforeCompressLength = new int[2];
            beforeCompressLength[0] = wrap.getInt();
            beforeCompressLength[1] = wrap.getInt();
        }

        public byte[] compressType;
        public short[] splitLength;
        public int[] beforeCompressLength;

        public byte[] data;

        public byte[] bytes() {
            final ByteBuffer allocate = ByteBuffer.allocate(compressType.length + splitLength.length * 2 + beforeCompressLength.length * 4);
            allocate.put(compressType);
            for (short i : splitLength) {
                allocate.putShort(i);
            }
            for (int i : beforeCompressLength) {
                allocate.putInt(i);
            }
            return allocate.array();
        }

    }

    public static int[] getSingleColumn(ByteBuffer byteBuffer, int valueSize, int columnIndex, IntCompressResult intCompressResult) {
        try {

            int originalSize = 0;
            int which = 0;
            int[] result = new int[valueSize * 40];
            if (split1.contains(columnIndex)) {

                originalSize = intCompressResult.beforeCompressLength[0];
                which = 1;
            } else {

                originalSize = intCompressResult.beforeCompressLength[1];
                which = 2;
            }
            final byte[] decompress = Zstd.decompress(byteBuffer.array(), originalSize);
            final byte[] compressType = intCompressResult.compressType;
            final ByteBuffer wrap = ByteBuffer.wrap(decompress);
            List<Integer> list;
            if (which == 1) {
                list = split1;
            } else {
                list = split2;
            }
            List<Integer> needCompressColumnIndex = new ArrayList<>();
            needCompressColumnIndex.add(columnIndex);
            int c = columnIndex;
            while (relation[c] != 0) {
                c = relation[c];
                needCompressColumnIndex.add(c);
            }

            for (Integer i : list) {
                final boolean bit = getBit(i, compressType);
                if (bit) {
                    if (!needCompressColumnIndex.contains(i)) {
                        int offset1 = 0;
                        byte dictSize = wrap.get();
                        int bitSize = valueSize;
                        switch (dictSize) {
                            case 1:
                                offset1 += 4;
                                break;
                            case 2:
                                offset1 += 2 * 4 + UpperBoundByte(bitSize);
                                break;
                            case 4:
                                offset1 += 4 * 4 + UpperBoundByte(bitSize * 2);
                                break;
                            case 16:
                                offset1 += 16 * 4 + UpperBoundByte(bitSize * 4);
                                break;
                            case 64:
                                offset1 += 64 * 4 + UpperBoundByte(bitSize * 8);
                                break;
                        }
                        wrap.position(wrap.position() + offset1);
                        continue;
                    }
//                // use map
                    byte dictSize = wrap.get();
                    List<Integer> dict = new ArrayList<>();
                    for (int j = 0; j < dictSize; j++) {
                        int anInt1 = wrap.getInt();
                        dict.add(anInt1);
                    }
                    int bitSize = valueSize;
                    if (dictSize == 1) {
                        for (int j = 0; j < valueSize; j++) {
                            result[i * valueSize + j] = dict.get(0);
                        }
                    } else if (dictSize == 2) {
                        byte[] indexBit = new byte[UpperBoundByte(bitSize)];
                        wrap.get(indexBit, 0, indexBit.length);
                        for (int j = 0; j < valueSize; j++) {
                            boolean bit1 = getBit(j, indexBit);
                            result[i * valueSize + j] = bit1 ? dict.get(1) : dict.get(0);
                        }
                    } else if (dictSize == 4) {
                        byte[] indexBit = new byte[UpperBoundByte(bitSize * 2)];
                        wrap.get(indexBit, 0, indexBit.length);
                        for (int j = 0; j < valueSize; j++) {
                            int ix = getTwoBit(indexBit, j);
                            result[i * valueSize + j] = dict.get(ix);
                        }
                    } else if (dictSize == 16) {
                        byte[] indexBit = new byte[UpperBoundByte(bitSize * 4)];
                        wrap.get(indexBit, 0, indexBit.length);
                        for (int j = 1; j < valueSize; j++) {
                            int ix = getFourBit(indexBit, j);
                            result[i * valueSize + j] = dict.get(ix);
                        }
                    } else if (dictSize == 64) {
                        byte[] indexBit = new byte[UpperBoundByte(bitSize * 8)];
                        wrap.get(indexBit, 0, indexBit.length);
                        for (int j = 1; j < valueSize; j++) {
                            int ix = indexBit[j];
                            result[i * valueSize + j] = dict.get(ix);
                        }
                    }
                } else {
                    if (!needCompressColumnIndex.contains(i)) {
                        int length1 = wrap.getInt();
                        wrap.position(wrap.position() + length1);
                        continue;
                    }
                    // not use map
                    int length = wrap.getInt();
                    byte[] bytes2 = new byte[length];
                    wrap.get(bytes2, 0, bytes2.length);
                    long[] longs = decompress2WithoutZstd(bytes2, valueSize);
                    result[i * valueSize] = (int) longs[0];
                    for (int j = 0; j < longs.length; j++) {
                        result[i * valueSize + j] = (int) (longs[j]);
                    }
                }
            }
            recoveryProcess(result, valueSize);
            return Arrays.copyOfRange(result, columnIndex * valueSize, (columnIndex + 1) * valueSize);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean openSub = true;

    public static int[] getByLineNum(ByteBuffer byteBuffer, int valueSize, int columnIndex, int compressLength) {
        if (openSub) {
            byte[] bytes = new byte[compressLength];
            byteBuffer.get(bytes, 0, bytes.length);
            int[] ints = decompress4(bytes, valueSize);
            for (int i = 0; i < 40; i++) {
                if (i == columnIndex) {
                    int[] ints1 = new int[valueSize];
                    for (int j = 0; j < ints1.length; j++) {
                        ints1[j] = ints[i * valueSize + j];
                    }
                    return ints1;
                }
            }
        }
        return null;
//        ByteBuffer wrap = byteBuffer;
//        int anInt = wrap.getInt();
//        byte[] bytes1 = new byte[compressLength - 4];
//        wrap.get(bytes1, 0, bytes1.length);
//        byte[] decompress = Zstd.decompress(bytes1, anInt);
//        ByteBuffer wrap1 = ByteBuffer.wrap(decompress);
//        byte[] compressType = new byte[5];
//        int[] result = new int[valueSize * 40];
//        wrap1.get(compressType, 0, compressType.length);
//        for (int i = 0; i < 40; i++) {
//            boolean bit = getBit(i, compressType);
//            if (bit) {
//                if (columnIndex != i) {
//                    int offset = 0;
//                    byte dictSize = wrap1.get();
//                    int bitSize = valueSize;
//                    switch (dictSize) {
//                        case 1:
//                            offset += 4;
//                            break;
//                        case 2:
//                            offset += 2 * 4 + UpperBoundByte(bitSize);
//                            break;
//                        case 4:
//                            offset += 4 * 4 + UpperBoundByte(bitSize * 2);
//                    }
//                    wrap1.position(wrap1.position() + offset);
//                    continue;
//                }
//                int[] ints = new int[valueSize];
//                // use map
//                byte dictSize = wrap1.get();
//                List<Integer> dict = new ArrayList<>();
//                for (int j = 0; j < dictSize; j++) {
//                    int anInt1 = wrap1.getInt();
//                    dict.add(anInt1);
//                }
//                int bitSize = valueSize;
//                if (dictSize == 1) {
//                    for (int j = 0; j < valueSize; j++) {
//                        ints[j] = dict.get(0);
//                    }
//                } else if (dictSize == 2) {
//                    byte[] indexBit = new byte[UpperBoundByte(bitSize)];
//                    wrap1.get(indexBit, 0, indexBit.length);
//                    for (int j = 0; j < valueSize; j++) {
//                        boolean bit1 = getBit(j, indexBit);
//                        ints[j] = bit1 ? dict.get(1) : dict.get(0);
//                    }
//                } else if (dictSize == 4) {
//                    byte[] indexBit = new byte[UpperBoundByte(bitSize * 2)];
//                    wrap1.get(indexBit, 0, indexBit.length);
//                    for (int j = 0; j < valueSize; j++) {
//                        int ix = getTwoBit(indexBit, j);
//                        ints[j] = dict.get(ix);
//                    }
//                }
//                map.put(i, ints);
//            } else {
//                if (!columnIndexList.contains(i)) {
//                    int length = wrap1.getInt();
//                    wrap1.position(wrap1.position() + length);
//                    continue;
//                }
//                int[] ints = new int[valueSize];
//                // not use map
//                int length = wrap1.getInt();
//                byte[] bytes2 = new byte[length];
//                wrap1.get(bytes2, 0, bytes2.length);
//                long[] longs = decompress2WithoutZstd(bytes2, valueSize);
//                ints[0] = (int) longs[0];
//                for (int j = 1; j < longs.length; j++) {
//                    ints[j] = (int) (ints[j - 1] + longs[j]);
//                }
//                map.put(i, ints);
//            }
//        }
//        return map;
    }

    public static byte[] compressOrigin(int[] ints, int valueSize) {
        List<ByteBuffer> arrayList = new ArrayList<>();
        List<Integer> notUseDictArray = new ArrayList<>();
        int start = 0;
        int length = ints.length;
        int totalLength = 0;
        byte[] compressType = new byte[5];
        int batchIndex = 0;
        preProcess(ints, valueSize);
        while (start < length) {
            int count = 0;
            Map<Integer, Integer> map = new HashMap<>();
            Map<Integer, Integer> invMap = new HashMap<>();
            boolean isUseMap = true;
            for (int index = start + 1; index < start + valueSize; index++) {
                if (isUseMap && !map.containsKey(ints[index])) {
                    map.put(ints[index], count);
                    invMap.put(count, ints[start]);
                    count++;
                }
                if (map.size() > 1) {
                    isUseMap = false;
                    break;
                }
            }
            if (isUseMap) {
                setBit(batchIndex, compressType);
                // write dict
                byte dictSize = (byte) map.size();
                if (dictSize > 16) dictSize = 64;
                else if (dictSize > 4) dictSize = 16;
//                else if (dictSize > 4) dictSize = 8;
                else if (dictSize > 2) dictSize = 4;
                int bitSize = valueSize;
                if (dictSize == 64) bitSize *= 8;
                if (dictSize == 16) bitSize *= 4;
                if (dictSize == 8) bitSize *= 3;
                if (dictSize == 4) bitSize *= 2;
                if (dictSize == 1) bitSize = 0;
                ByteBuffer allocate = ByteBuffer.allocate(4 + 1 + dictSize * 4 + UpperBoundByte(bitSize));
                allocate.putInt(ints[start]);
                // put dictSize
                allocate.put(dictSize);
                // put dict
                for (int i = 0; i < dictSize; i++) {
                    boolean exist = false;
                    for (Integer integer : map.keySet()) {
                        if (map.get(integer) == i) {
                            allocate.putInt(integer);
                            exist = true;
                            break;
                        }
                    }
                    if (!exist) allocate.putInt(0);
                }
                // put index value
                if (dictSize != 0) {
                    if (dictSize == 2) {
                        byte[] bitSet = new byte[UpperBoundByte(bitSize)];
                        for (int j = start + 1; j < start + valueSize; j++) {
                            Integer i = map.get(ints[j]);
                            if (i == 1) {
                                setBit(j - start, bitSet);
                            }
                        }
                        allocate.put(bitSet);
                    } else if (dictSize == 4) {
                        byte[] bitSet = new byte[UpperBoundByte(bitSize)];
                        for (int j = start + 1; j < start + valueSize; j++) {
                            Integer i = map.get(ints[j]);
                            setTwoBit(bitSet, j - start, i);
                        }
                        allocate.put(bitSet);
                    } else if (dictSize == 16) {
                        byte[] bitSet = new byte[UpperBoundByte(bitSize)];
                        for (int j = start + 1; j < start + valueSize; j++) {
                            Integer i = map.get(ints[j]);
                            setFourBit(bitSet, j - start, i);
                        }
                        allocate.put(bitSet);
                    } else if (dictSize == 64) {
                        byte[] bitSet = new byte[UpperBoundByte(bitSize)];
                        for (int j = start + 1; j < start + valueSize; j++) {
                            int i = map.get(ints[j]);
                            bitSet[j - start] = (byte) i;
                        }
                        allocate.put(bitSet);
                    }
                }
                totalLength += allocate.array().length;
                arrayList.add(allocate);
            } else {
                long[] longs2 = new long[valueSize];
                for (int j = start; j < start + valueSize; j++) {
                    longs2[j - start] = ints[j];
                }
                byte[] bytes = compress2WithoutZstd(longs2);
                ByteBuffer allocate = ByteBuffer.allocate(4 + bytes.length);
                allocate.putInt(bytes.length);
                allocate.put(bytes);
                totalLength += allocate.array().length;
                arrayList.add(allocate);
            }
            batchIndex++;
            start += valueSize;
        }

        // compressType 5B
        //
        ByteBuffer allocate = ByteBuffer.allocate(5 + totalLength);
        allocate.put(compressType);
        for (ByteBuffer byteBuffer : arrayList) {
            allocate.put(byteBuffer.array());
        }
        byte[] compress = Zstd.compress(allocate.array(), 2);
//        byte[] compress = allocate.array();
        ByteBuffer buffer = ByteBuffer.allocate(4 + compress.length);
        buffer.putInt(allocate.array().length);
        buffer.put(compress);
        return buffer.array();
    }

    public static int[] decompressOriginBySingle(byte[] bytes, int valueSize, int index) {
        ByteBuffer wrap = ByteBuffer.wrap(bytes);
        int anInt = wrap.getInt();
        byte[] bytes1 = new byte[bytes.length - 4];
        wrap.get(bytes1, 0, bytes1.length);
        byte[] decompress = Zstd.decompress(bytes1, anInt);
        ByteBuffer wrap1 = ByteBuffer.wrap(decompress);
        byte[] compressType = new byte[5];
        int[] result = new int[valueSize * 40];
        wrap1.get(compressType, 0, compressType.length);
        Set<Integer> dependencies = dependencyFinder.findDependencies(index);
        for (int i = 0; i < 40; i++) {
            boolean bit = getBit(i, compressType);
            if (bit) {
                // use map
                int firstInt = wrap1.getInt();
                result[i * valueSize] = firstInt;
                byte dictSize = wrap1.get();
                int bitSize = valueSize;
                if (!dependencies.contains(i)) {
                    int offset1 = 0;
                    switch (dictSize) {
                        case 1:
                            offset1 += 4;
                            break;
                        case 2:
                            offset1 += 2 * 4 + UpperBoundByte(bitSize);
                            break;
                        case 4:
                            offset1 += 4 * 4 + UpperBoundByte(bitSize * 2);
                            break;
                        case 16:
                            offset1 += 16 * 4 + UpperBoundByte(bitSize * 4);
                            break;
                        case 64:
                            offset1 += 64 * 4 + UpperBoundByte(bitSize * 8);
                            break;
                    }
                    wrap.position(wrap.position() + offset1);
                    continue;
                }
                List<Integer> dict = new ArrayList<>();
                for (int j = 0; j < dictSize; j++) {
                    int anInt1 = wrap1.getInt();
                    dict.add(anInt1);
                }
                if (dictSize == 1) {
                    for (int j = 1; j < valueSize; j++) {
                        result[i * valueSize + j] = dict.get(0);
                    }
                } else if (dictSize == 2) {
                    byte[] indexBit = new byte[UpperBoundByte(bitSize)];
                    wrap1.get(indexBit, 0, indexBit.length);
                    for (int j = 1; j < valueSize; j++) {
                        boolean bit1 = getBit(j, indexBit);
                        result[i * valueSize + j] = bit1 ? dict.get(1) : dict.get(0);
                    }
                } else if (dictSize == 4) {
                    byte[] indexBit = new byte[UpperBoundByte(bitSize * 2)];
                    wrap1.get(indexBit, 0, indexBit.length);
                    for (int j = 1; j < valueSize; j++) {
                        int ix = getTwoBit(indexBit, j);
                        result[i * valueSize + j] = dict.get(ix);
                    }
                } else if (dictSize == 16) {
                    byte[] indexBit = new byte[UpperBoundByte(bitSize * 4)];
                    wrap1.get(indexBit, 0, indexBit.length);
                    for (int j = 1; j < valueSize; j++) {
                        int ix = getFourBit(indexBit, j);
                        result[i * valueSize + j] = dict.get(ix);
                    }
                } else if (dictSize == 64) {
                    byte[] indexBit = new byte[UpperBoundByte(bitSize * 8)];
                    wrap1.get(indexBit, 0, indexBit.length);
                    for (int j = 1; j < valueSize; j++) {
                        int ix = indexBit[j];
                        result[i * valueSize + j] = dict.get(ix);
                    }
                }
            } else {
                // not use map
                int length = wrap1.getInt();
                if (!dependencies.contains(i)) {
                    wrap1.position(wrap1.position() + length);
                    continue;
                }
                byte[] bytes2 = new byte[length];
                wrap1.get(bytes2, 0, bytes2.length);
                long[] longs = decompress2WithoutZstd(bytes2, valueSize);
                result[i * valueSize] = (int) longs[0];
                for (int j = 0; j < longs.length; j++) {
                    result[i * valueSize + j] = (int) (longs[j]);
                }
            }
        }
        recoverProcessBySingple(result, valueSize,index,dependencies);
        return result;
    }

    public static int[] decompressOrigin(byte[] bytes, int valueSize) {
        ByteBuffer wrap = ByteBuffer.wrap(bytes);
        int anInt = wrap.getInt();
        byte[] bytes1 = new byte[bytes.length - 4];
        wrap.get(bytes1, 0, bytes1.length);
//        byte[] decompress = bytes1;
        byte[] decompress = Zstd.decompress(bytes1, anInt);
        ByteBuffer wrap1 = ByteBuffer.wrap(decompress);
        byte[] compressType = new byte[5];
        int[] result = new int[valueSize * 40];
        wrap1.get(compressType, 0, compressType.length);
        for (int i = 0; i < 40; i++) {
            boolean bit = getBit(i, compressType);
            if (bit) {
                // use map
                int firstInt = wrap1.getInt();
                result[i * valueSize] = firstInt;
                byte dictSize = wrap1.get();
                List<Integer> dict = new ArrayList<>();
                for (int j = 0; j < dictSize; j++) {
                    int anInt1 = wrap1.getInt();
                    dict.add(anInt1);
                }
                int bitSize = valueSize;
                if (dictSize == 1) {
                    for (int j = 1; j < valueSize; j++) {
                        result[i * valueSize + j] = dict.get(0);
                    }
                } else if (dictSize == 2) {
                    byte[] indexBit = new byte[UpperBoundByte(bitSize)];
                    wrap1.get(indexBit, 0, indexBit.length);
                    for (int j = 1; j < valueSize; j++) {
                        boolean bit1 = getBit(j, indexBit);
                        result[i * valueSize + j] = bit1 ? dict.get(1) : dict.get(0);
                    }
                } else if (dictSize == 4) {
                    byte[] indexBit = new byte[UpperBoundByte(bitSize * 2)];
                    wrap1.get(indexBit, 0, indexBit.length);
                    for (int j = 1; j < valueSize; j++) {
                        int ix = getTwoBit(indexBit, j);
                        result[i * valueSize + j] = dict.get(ix);
                    }
                } else if (dictSize == 16) {
                    byte[] indexBit = new byte[UpperBoundByte(bitSize * 4)];
                    wrap1.get(indexBit, 0, indexBit.length);
                    for (int j = 1; j < valueSize; j++) {
                        int ix = getFourBit(indexBit, j);
                        result[i * valueSize + j] = dict.get(ix);
                    }
                } else if (dictSize == 64) {
                    byte[] indexBit = new byte[UpperBoundByte(bitSize * 8)];
                    wrap1.get(indexBit, 0, indexBit.length);
                    for (int j = 1; j < valueSize; j++) {
                        int ix = indexBit[j];
                        result[i * valueSize + j] = dict.get(ix);
                    }
                }
            } else {
                // not use map
                int length = wrap1.getInt();
                byte[] bytes2 = new byte[length];
                wrap1.get(bytes2, 0, bytes2.length);
                long[] longs = decompress2WithoutZstd(bytes2, valueSize);
                result[i * valueSize] = (int) longs[0];
                for (int j = 0; j < longs.length; j++) {
                    result[i * valueSize + j] = (int) (longs[j]);
                }
            }
        }
        recoveryProcess(result, valueSize);
        return result;
    }


    public static int[] decompress4V2(byte[] bytes, int valueSize, IntCompressResult intCompressResult) {
        try {
            ByteBuffer wrap = ByteBuffer.wrap(bytes);
//        int anInt = wrap.getInt();
//        byte[] bytes1 = new byte[bytes.length - 4];
//        wrap.get(bytes1, 0, bytes1.length);
//        byte[] decompress = bytes1;
            int[] result = new int[valueSize * 40];
            final byte[] compressType = intCompressResult.compressType;
            final short i1 = intCompressResult.splitLength[0];
            final short i2 = intCompressResult.splitLength[1];
            byte[] bytes1 = new byte[i1];
            ArrayUtils.copy(bytes, 0, bytes1, 0, i1);
            byte[] decompress = Zstd.decompress(bytes1, intCompressResult.beforeCompressLength[0]);
            ByteBuffer wrap1 = ByteBuffer.wrap(decompress);

            for (Integer i : split1) {
                boolean bit = getBit(i, compressType);
                if (bit) {
                    int firstInt = wrap1.getInt();
                    result[i * valueSize] = firstInt;
                    // use map
                    byte dictSize = wrap1.get();
                    List<Integer> dict = new ArrayList<>();
                    for (int j = 0; j < dictSize; j++) {
                        int anInt1 = wrap1.getInt();
                        dict.add(anInt1);
                    }
                    int bitSize = valueSize;
                    if (dictSize == 1) {
                        for (int j = 1; j < valueSize; j++) {
                            result[i * valueSize + j] = dict.get(0);
                        }
                    } else if (dictSize == 2) {
                        byte[] indexBit = new byte[UpperBoundByte(bitSize)];
                        wrap1.get(indexBit, 0, indexBit.length);
                        for (int j = 1; j < valueSize; j++) {
                            boolean bit1 = getBit(j, indexBit);
                            result[i * valueSize + j] = bit1 ? dict.get(1) : dict.get(0);
                        }
                    } else if (dictSize == 4) {
                        byte[] indexBit = new byte[UpperBoundByte(bitSize * 2)];
                        wrap1.get(indexBit, 0, indexBit.length);
                        for (int j = 1; j < valueSize; j++) {
                            int ix = getTwoBit(indexBit, j);
                            result[i * valueSize + j] = dict.get(ix);
                        }
                    } else if (dictSize == 16) {
                        byte[] indexBit = new byte[UpperBoundByte(bitSize * 4)];
                        wrap1.get(indexBit, 0, indexBit.length);
                        for (int j = 1; j < valueSize; j++) {
                            int ix = getFourBit(indexBit, j);
                            result[i * valueSize + j] = dict.get(ix);
                        }
                    } else if (dictSize == 64) {
                        byte[] indexBit = new byte[UpperBoundByte(bitSize * 8)];
                        wrap1.get(indexBit, 0, indexBit.length);
                        for (int j = 1; j < valueSize; j++) {
                            int ix = indexBit[j];
                            result[i * valueSize + j] = dict.get(ix);
                        }
                    }
                } else {
                    // not use map
                    int length = wrap1.getInt();
                    byte[] bytes2 = new byte[length];
                    wrap1.get(bytes2, 0, bytes2.length);
                    long[] longs = decompress2WithoutZstd(bytes2, valueSize);
                    result[i * valueSize] = (int) longs[0];
                    for (int j = 0; j < longs.length; j++) {
                        result[i * valueSize + j] = (int) (longs[j]);
                    }
                }
            }
            bytes1 = new byte[i2];
            ArrayUtils.copy(bytes, i1, bytes1, 0, i2);
            decompress = Zstd.decompress(bytes1, intCompressResult.beforeCompressLength[1]);
            wrap1 = ByteBuffer.wrap(decompress);
            for (Integer i : split2) {
                boolean bit = getBit(i, compressType);
                if (bit) {
                    // use map
                    int firstInt = wrap1.getInt();
                    result[i * valueSize] = firstInt;
                    byte dictSize = wrap1.get();
                    List<Integer> dict = new ArrayList<>();
                    for (int j = 0; j < dictSize; j++) {
                        int anInt1 = wrap1.getInt();
                        dict.add(anInt1);
                    }
                    int bitSize = valueSize;
                    if (dictSize == 1) {
                        for (int j = 1; j < valueSize; j++) {
                            result[i * valueSize + j] = dict.get(0);
                        }
                    } else if (dictSize == 2) {
                        byte[] indexBit = new byte[UpperBoundByte(bitSize)];
                        wrap1.get(indexBit, 0, indexBit.length);
                        for (int j = 1; j < valueSize; j++) {
                            boolean bit1 = getBit(j, indexBit);
                            result[i * valueSize + j] = bit1 ? dict.get(1) : dict.get(0);
                        }
                    } else if (dictSize == 4) {
                        byte[] indexBit = new byte[UpperBoundByte(bitSize * 2)];
                        wrap1.get(indexBit, 0, indexBit.length);
                        for (int j = 1; j < valueSize; j++) {
                            int ix = getTwoBit(indexBit, j);
                            result[i * valueSize + j] = dict.get(ix);
                        }
                    } else if (dictSize == 16) {
                        byte[] indexBit = new byte[UpperBoundByte(bitSize * 4)];
                        wrap1.get(indexBit, 0, indexBit.length);
                        for (int j = 1; j < valueSize; j++) {
                            int ix = getFourBit(indexBit, j);
                            result[i * valueSize + j] = dict.get(ix);
                        }
                    } else if (dictSize == 64) {
                        byte[] indexBit = new byte[UpperBoundByte(bitSize * 8)];
                        wrap1.get(indexBit, 0, indexBit.length);
                        for (int j = 1; j < valueSize; j++) {
                            int ix = indexBit[j];
                            result[i * valueSize + j] = dict.get(ix);
                        }
                    }
                } else {
                    // not use map
                    int length = wrap1.getInt();
                    byte[] bytes2 = new byte[length];
                    wrap1.get(bytes2, 0, bytes2.length);
                    long[] longs = decompress2WithoutZstd(bytes2, valueSize);
                    result[i * valueSize] = (int) longs[0];
                    for (int j = 0; j < longs.length; j++) {
                        result[i * valueSize + j] = (int) (longs[j]);
                    }
                }
            }
            recoveryProcess(result, valueSize);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int[] decompress4(byte[] bytes, int valueSize) {
        ByteBuffer wrap = ByteBuffer.wrap(bytes);
        int anInt = wrap.getInt();
        byte[] bytes1 = new byte[bytes.length - 4];
        wrap.get(bytes1, 0, bytes1.length);
//        byte[] decompress = bytes1;
        byte[] decompress = Zstd.decompress(bytes1, anInt);
        ByteBuffer wrap1 = ByteBuffer.wrap(decompress);
        byte[] compressType = new byte[5];
        int[] result = new int[valueSize * 40];
        wrap1.get(compressType, 0, compressType.length);
        for (int i = 0; i < 40; i++) {
            boolean bit = getBit(i, compressType);
            if (bit) {
                // use map
                byte dictSize = wrap1.get();
                List<Integer> dict = new ArrayList<>();
                for (int j = 0; j < dictSize; j++) {
                    int anInt1 = wrap1.getInt();
                    dict.add(anInt1);
                }
                int bitSize = valueSize;
                if (dictSize == 1) {
                    for (int j = 0; j < valueSize; j++) {
                        result[i * valueSize + j] = dict.get(0);
                    }
                } else if (dictSize == 2) {
                    byte[] indexBit = new byte[UpperBoundByte(bitSize)];
                    wrap1.get(indexBit, 0, indexBit.length);
                    for (int j = 0; j < valueSize; j++) {
                        boolean bit1 = getBit(j, indexBit);
                        result[i * valueSize + j] = bit1 ? dict.get(1) : dict.get(0);
                    }
                } else if (dictSize == 4) {
                    byte[] indexBit = new byte[UpperBoundByte(bitSize * 2)];
                    wrap1.get(indexBit, 0, indexBit.length);
                    for (int j = 0; j < valueSize; j++) {
                        int ix = getTwoBit(indexBit, j);
                        result[i * valueSize + j] = dict.get(ix);
                    }
                }
            } else {
                // not use map
                int length = wrap1.getInt();
                byte[] bytes2 = new byte[length];
                wrap1.get(bytes2, 0, bytes2.length);
                long[] longs = decompress2WithoutZstd(bytes2, valueSize);
                result[i * valueSize] = (int) longs[0];
                for (int j = 0; j < longs.length; j++) {
                    result[i * valueSize + j] = (int) (longs[j]);
                }
            }
        }
        recoveryProcess(result, valueSize);
        return result;
    }

    public static byte[] compress2WithoutZstd(long[] ints) {
        try {
            final long[] gapArray = ints;
            for (int i = 0; i < gapArray.length; i++) {
                gapArray[i] = ZigZagUtil.encodeLong(gapArray[i]);
            }
            long[] output = new long[ints.length];
            final int compress = Simple8.compress(gapArray, output);
            ByteBuffer resultBuffer = ByteBuffer.allocate(compress * 8);
            for (int i = 0; i < compress; i++) {
                resultBuffer.putLong(output[i]);
            }
            return resultBuffer.array();
        } catch (Exception e) {
            System.out.println("compress2 error, e" + e);
        }
        return null;
    }

    public static long[] decompress2WithoutZstd(byte[] bytes1, int valueSize) {
        byte[] bytes = bytes1;
        long[] output = new long[bytes.length / 8];
        int position = 0;
        ByteBuffer wrap = ByteBuffer.wrap(bytes);
        for (int i = 0; i < bytes.length; i += 8) {
            final long l = wrap.getLong();
            output[position] = l;
            position++;
        }
        final long[] longs = new long[valueSize];
        Simple8.decompress(output, 0, bytes.length / 8, longs, 0);
        for (int i = 0; i < longs.length; i++) {
            longs[i] = ZigZagUtil.decodeLong(longs[i]);
        }
        return longs;
    }

    public static byte[] compress2(long[] ints) {
        try {
            final long[] gapArray = toGapArray(ints);
            for (int i = 0; i < gapArray.length; i++) {
                gapArray[i] = ZigZagUtil.encodeLong(gapArray[i]);
            }
            long[] output = new long[ints.length];
            final int compress = Simple8.compress(gapArray, output);
            ByteBuffer resultBuffer = ByteBuffer.allocate(compress * 8);
            long[] outputArray = new long[compress];
            System.arraycopy(output, 0, outputArray, 0, compress);
            LongBuffer longBuffer = resultBuffer.asLongBuffer();
            longBuffer.put(outputArray);
//            byte[] result = new byte[compress * 8];
//            int position = 0;
//            for (int i = 0; i < compress; i++) {
//                final long l = output[i];
//                final byte[] bytes = BytesUtil.long2Bytes(l);
//                ArrayUtils.copy(bytes, 0, result, position, 8);
//                position += 8;
//            }
//            return resultBuffer.array();
            return Zstd.compress(resultBuffer.array(), 12);
//            GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
//            return gzipCompress.compress(result);
//            return result;
        } catch (Exception e) {
            System.out.println("compress2 error, e" + e);
        }
        return null;
    }

    public static long[] decompress2(byte[] bytes1, int valueSize) {
//        GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
//        byte[] bytes = gzipCompress.deCompress(bytes1);

        byte[] bytes = Zstd.decompress(bytes1, valueSize * 8);
        long[] output = new long[bytes.length / 8];
        int position = 0;
        ByteBuffer wrap = ByteBuffer.wrap(bytes);
        for (int i = 0; i < bytes.length; i += 8) {
            final long l = wrap.getLong();
            output[position] = l;
            position++;
        }
        final long[] longs = new long[valueSize];
        Simple8.decompress(output, 0, bytes.length / 8, longs, 0);
        for (int i = 0; i < longs.length; i++) {
            longs[i] = ZigZagUtil.decodeLong(longs[i]);
        }
        long aLong = longs[0];
        for (int i = 1; i < longs.length; i++) {
            longs[i] = longs[i] + aLong;
            aLong = longs[i];
        }
        return longs;
    }

    public static byte[] compress(int[] ints) {
//        ints = Simple9Codes.innerEncode(ints);
        ByteBuffer allocate = ByteBuffer.allocate(ints.length * 4);
        for (int i : ints) {
            allocate.putInt(i);
        }
        final byte[] array = allocate.array();
        GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
        return gzipCompress.compress(array);
    }


    public static byte[] compressShort(short[] shorts, int valueSize) {

        BitSet bitSet = SHORT_ARRTY_TYPE_THREAD_LOCAL.get();
        bitSet.clear();
        bitSet.set(15);
        int diffNum = 0;
        for (int i = 0; i < shorts.length / valueSize; i++) {
            boolean flag = true;
            short value = shorts[i * valueSize];
            for (int j = 0; j < valueSize; j++) {
                if (value != shorts[i * valueSize + j]) {
                    flag = false;
                }
            }
            if (!flag) {
                diffNum++;
            } else {
                bitSet.set(i);
            }
        }
        int to = shorts.length / valueSize;
        ByteBuffer allocate = ByteBuffer.allocate(1 + 2 + diffNum * 2 + (to - diffNum) * valueSize * 2);
        allocate.put((byte) (shorts.length / valueSize));
        allocate.put(bitSet.toByteArray());
        for (int i = 0; i < shorts.length / valueSize; i++) {
            if (bitSet.get(i)) {
                allocate.putShort(shorts[i * valueSize]);
            } else {
                for (int j = 0; j < valueSize; j++) {
                    allocate.putShort(shorts[i * valueSize + j]);
                }
            }
        }
        byte[] compress = Zstd.compress(allocate.array(), 6);
        ByteBuffer allocate1 = ByteBuffer.allocate(4 + compress.length);
        allocate1.putInt(allocate.array().length);
        allocate1.put(compress);
        return allocate1.array();
    }

    public static byte[] compressZstd(int[] ints) {
        ByteBuffer allocate = ByteBuffer.allocate(ints.length * 4);
        for (int i : ints) {
            allocate.putInt(i);
        }
        final byte[] array = allocate.array();
        return Zstd.compress(array, 22);
    }

    public static byte[] compress4(int[] ints) {
        ints = Simple9Codes.innerEncode(ints);
        ByteBuffer allocate = ByteBuffer.allocate(ints.length * 4);
        for (int i : ints) {
            allocate.putInt(i);
        }
        final byte[] array = allocate.array();
        return ZstdCompress.compress(array, 12);
    }


    public static int[] decompress(byte[] bytes) {
        GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
        final byte[] bytes1 = gzipCompress.deCompress(bytes);
        final ByteBuffer wrap = ByteBuffer.wrap(bytes1);
        int[] ints = new int[bytes1.length / 4];
        for (int j = 0; j < ints.length; j++) {
            ints[j] = wrap.getInt();
        }
        return ints;
//        return Simple9Codes.decode(ints);
    }

    public static short[] decompressShort(byte[] bytes, int valueSize, int totalLength) {
        final byte[] bytes1 = Zstd.decompress(bytes, totalLength);
        final ByteBuffer wrap = ByteBuffer.wrap(bytes1);
        byte length = wrap.get();
        byte[] bytes2 = new byte[2];
        wrap.get(bytes2, 0, bytes2.length);
        BitSet compressType = BitSet.valueOf(bytes2);
        short[] shorts = new short[length * valueSize];
        for (int i = 0; i < length; i++) {
            if (compressType.get(i)) {
                short value = wrap.getShort();
                for (int j = 0; j < valueSize; j++) {
                    shorts[i * valueSize + j] = value;
                }
            } else {
                for (int j = 0; j < valueSize; j++) {
                    shorts[i * valueSize + j] = wrap.getShort();
                }
            }
        }
        return shorts;
//        return Simple9Codes.decode(ints);
    }

    public static int[] decompressZstd(byte[] bytes, int num) {
        final byte[] bytes1 = Zstd.decompress(bytes, num);
        final ByteBuffer wrap = ByteBuffer.wrap(bytes1);
        int[] ints = new int[bytes1.length / 4];
        for (int j = 0; j < ints.length; j++) {
            ints[j] = wrap.getInt();
        }
        return ints;
    }

    protected static int[] toGapArray(int[] numbers) {
        int prev = numbers[0];
        for (int i = 1; i < numbers.length; i++) {
            int tmp = numbers[i];
            numbers[i] = numbers[i] - prev;
            prev = tmp;
        }
        return numbers;

    }

    protected static long[] toGapArray(long[] numbers) {
        long prev = numbers[0];
        for (int i = 1; i < numbers.length; i++) {
            long tmp = numbers[i];
            numbers[i] = numbers[i] - prev;
            prev = tmp;
        }
        return numbers;

    }
}
