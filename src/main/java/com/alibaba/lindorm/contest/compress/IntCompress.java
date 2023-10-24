package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.compress.intcodec.simple.Simple9Codes;
import com.alibaba.lindorm.contest.compress.intcodec2.integercompression.*;
import com.alibaba.lindorm.contest.file.TSFileService;
import com.alibaba.lindorm.contest.memory.Value;
import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.*;
import com.alibaba.lindorm.contest.util.ZigZagUtil;
import com.github.luben.zstd.Zstd;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.*;

public class IntCompress {
    static Composition codec = new Composition(new NewPFDS9(), new VariableByte());

    static int[] testNumReal = new int[8400];
    static int originLength;
    static int[] testNum2 = new int[]{
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 513, 1025
    };
    public static ThreadLocal<BitSet> SHORT_ARRTY_TYPE_THREAD_LOCAL = ThreadLocal.withInitial(() -> BitSet.valueOf(new byte[2]));

    static long[] testNum4 = new long[1575];

    static {
        Random random = new Random();
        for (int i = 0; i < 1575; i++) {
            testNum4[i] = Math.abs(random.nextInt(30000));
        }
    }

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

            // Find max and min in the temp array
            int max = Arrays.stream(temp).max().orElse(Integer.MIN_VALUE);
            int min = Arrays.stream(temp).min().orElse(Integer.MAX_VALUE);

            // Create a new array with the original elements and space for the random numbers
            int[] newArray = Arrays.copyOf(temp, temp.length + num);

            // Append the random numbers between min and max to the new array
            for (int j = 0; j < num; j++) {
                newArray[temp.length + j] = min + random.nextInt(max - min + 1);
            }

            result[i] = newArray;
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
//        String fileName = "int.txt";  // 替换为你的文件路径
//        try {
//            testNumReal = readIntsFromFile(fileName);
//            System.out.println(testNumReal.length);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
//        int[] data = testNum3.clone();
//        int[][] splitArray = splitArray(data, 35,100*35);
//        for (int[] ints : splitArray) {
//            System.out.println(Arrays.toString(ints));
//        }
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
        long[] longs1 = new long[testNumReal.length];
        for(int i=0;i<longs1.length;i++){
            longs1[i] = testNumReal[i];
        }
        int[] data = testNumReal.clone();
        final byte[] bytes = compress4(data,210);
        long byteLength = bytes.length;
//        final int[] output = new int[data.length];
        final int[] longs = decompress4(bytes, 210);
//        final byte[] bytes1 = compressZstd(data1);
        boolean a = Arrays.equals(longs, testNumReal);
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
        System.out.println(a);
        System.out.println("compress rate : " + 1.0d * byteLength/ (data.length * 4));
    }

    public static final ThreadLocal<long[]> INT_ARRAY_BUFFER = ThreadLocal.withInitial(() -> new long[Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS]);

    public static int UpperBoundByte(int valueSize) {
        return ((valueSize + 7) / 8);
    }
    public static byte[] compress4(int[] ints,int valueSize){
        List<ByteBuffer> arrayList = new ArrayList<>();
        List<Integer> notUseDictArray = new ArrayList<>();
        int start = 0;
        int length = ints.length;
        int totalLength = 0;
        byte[] compressType = new byte[5];
        int batchIndex = 0;
        while (start < length)  {
            int count = 0;
            Map<Integer,Integer> map = new HashMap<>();
            Map<Integer,Integer> invMap = new HashMap<>();
            boolean isUseMap = true;
            for (int index = start; index < start + valueSize; index++) {
                if(isUseMap && !map.containsKey(ints[index])){
                    map.put(ints[index],count);
                    invMap.put(count,ints[start]);
                    count++;
                }
                if(map.size() > 4){
                    isUseMap = false;
                    break;
                }
            }
            if (isUseMap) {
                setBit(batchIndex,compressType);
                // write dict
                byte dictSize = (byte) map.size();
                if (dictSize == 3) dictSize = 4;
                int bitSize = valueSize;
                if (dictSize == 4) bitSize *= 2;
                if(dictSize == 1)bitSize = 0;
                ByteBuffer allocate = ByteBuffer.allocate(1 + dictSize * 4 + UpperBoundByte(bitSize));
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
                        for (int j = start; j < start + valueSize; j++) {
                            Integer i = map.get(ints[j]);
                            if (i == 1) {
                                setBit(j-start,bitSet);
                            }
                        }
                        allocate.put(bitSet);
                    } else if (dictSize == 4) {
                        byte[] bitSet = new byte[UpperBoundByte(bitSize)];
                        for (int j = start; j < start + valueSize; j++) {
                            Integer i = map.get(ints[j]);
                            setTwoBit(bitSet,j - start,i);
                        }
                        allocate.put(bitSet);
                    }
                }
                totalLength+=allocate.array().length;
                arrayList.add(allocate);
            } else {
                long[] longs2 = new long[valueSize];
                for(int j=start;j<start+valueSize;j++){
                    longs2[j-start] = ints[j];
                }
                long[] longs = toGapArray(longs2);
                byte[] bytes = compress2WithoutZstd(longs);
                ByteBuffer allocate = ByteBuffer.allocate(4+bytes.length);
                allocate.putInt(bytes.length);
                allocate.put(bytes);
                totalLength+=allocate.array().length;
                arrayList.add(allocate);
            }
            batchIndex++;
            start+=valueSize;
        }

        // compressType 5B
        //
        ByteBuffer allocate = ByteBuffer.allocate(5 + totalLength);
        allocate.put(compressType);
        for (ByteBuffer byteBuffer : arrayList) {
            allocate.put(byteBuffer.array());
        }
        byte[] compress = ZstdInner.compress(allocate.array(), 3);
//        byte[] compress = allocate.array();
        ByteBuffer buffer = ByteBuffer.allocate(4 + compress.length);
        buffer.putInt(allocate.array().length);
        buffer.put(compress);
        return buffer.array();
    }
    public static Map<Integer, int[]> getByLineNum(ByteBuffer byteBuffer, int valueSize,List<Integer> columnIndexList,int compressLength){
        final Map<Integer, int[]> map = new HashMap<>(columnIndexList.size());
        ByteBuffer wrap = byteBuffer;
        int anInt = wrap.getInt();
        byte[] bytes1 = new byte[compressLength-4];
        wrap.get(bytes1,0,bytes1.length);
        byte[] decompress = Zstd.decompress(bytes1, anInt);
        ByteBuffer wrap1 = ByteBuffer.wrap(decompress);
        byte[] compressType = new byte[5];
        int[] result = new int[valueSize * 40];
        wrap1.get(compressType,0,compressType.length);
        for(int i=0;i<40;i++){
            boolean bit = getBit(i, compressType);
            if(bit){
                if(!columnIndexList.contains(i)){
                    int offset = 0;
                    byte dictSize = wrap1.get();
                    int bitSize = valueSize;
                    switch (dictSize){
                        case 1:
                            offset+=4;
                            break;
                        case 2:
                            offset+=2*4+UpperBoundByte(bitSize);
                            break;
                        case 4:
                            offset+=4*4+UpperBoundByte(bitSize*2);
                    }
                    wrap1.position(wrap1.position()+offset);
                    continue;
                }
                int[] ints =new int[valueSize];
                // use map
                byte dictSize = wrap1.get();
                List<Integer> dict = new ArrayList<>();
                for(int j=0;j<dictSize;j++){
                    int anInt1 = wrap1.getInt();
                    dict.add(anInt1);
                }
                int bitSize = valueSize;
                if(dictSize == 1){
                    for(int j=0;j<valueSize;j++){
                        ints[j] = dict.get(0);
                    }
                }else if(dictSize == 2){
                    byte[] indexBit = new byte[UpperBoundByte(bitSize)];
                    wrap1.get(indexBit,0,indexBit.length);
                    for(int j=0;j<valueSize;j++){
                        boolean bit1 = getBit(j, indexBit);
                        ints[j] = bit1? dict.get(1) : dict.get(0);
                    }
                }else if(dictSize == 4){
                    byte[] indexBit = new byte[UpperBoundByte(bitSize*2)];
                    wrap1.get(indexBit,0,indexBit.length);
                    for(int j=0;j<valueSize;j++){
                        int ix = getTwoBit(indexBit,j);
                        ints[j] = dict.get(ix);
                    }
                }
                map.put(i,ints);
            }else{
                if(!columnIndexList.contains(i)){
                    int length = wrap1.getInt();
                    wrap1.position(wrap1.position()+length);
                    continue;
                }
                int[] ints = new int[valueSize];
                // not use map
                int length = wrap1.getInt();
                byte[] bytes2 = new byte[length];
                wrap1.get(bytes2,0,bytes2.length);
                long[] longs = decompress2WithoutZstd(bytes2, valueSize);
                ints[0] = (int) longs[0];
                for (int j = 1; j < longs.length; j++) {
                    ints[j] = (int) (ints[j-1]+longs[j]);
                }
                map.put(i,ints);
            }
        }
        return map;
    }
    public static int[] decompress4(byte[] bytes,int valueSize){
        ByteBuffer wrap = ByteBuffer.wrap(bytes);
        int anInt = wrap.getInt();
        byte[] bytes1 = new byte[bytes.length-4];
        wrap.get(bytes1,0,bytes1.length);
//        byte[] decompress = bytes1;
        byte[] decompress = Zstd.decompress(bytes1, anInt);
        ByteBuffer wrap1 = ByteBuffer.wrap(decompress);
        byte[] compressType = new byte[5];
        int[] result = new int[valueSize * 40];
        wrap1.get(compressType,0,compressType.length);
        for(int i=0;i<40;i++){
            boolean bit = getBit(i, compressType);
            if(bit){
                // use map
                byte dictSize = wrap1.get();
                List<Integer> dict = new ArrayList<>();
                for(int j=0;j<dictSize;j++){
                    int anInt1 = wrap1.getInt();
                    dict.add(anInt1);
                }
                int bitSize = valueSize;
                if(dictSize == 1){
                    for(int j=0;j<valueSize;j++){
                        result[i*valueSize+j] = dict.get(0);
                    }
                }else if(dictSize == 2){
                    byte[] indexBit = new byte[UpperBoundByte(bitSize)];
                    wrap1.get(indexBit,0,indexBit.length);
                    for(int j=0;j<valueSize;j++){
                        boolean bit1 = getBit(j, indexBit);
                        result[i*valueSize + j] = bit1? dict.get(1) : dict.get(0);
                    }
                }else if(dictSize == 4){
                    byte[] indexBit = new byte[UpperBoundByte(bitSize*2)];
                    wrap1.get(indexBit,0,indexBit.length);
                    for(int j=0;j<valueSize;j++){
                        int ix = getTwoBit(indexBit,j);
                        result[i*valueSize + j] = dict.get(ix);
                    }
                }
            }else{
                // not use map
                int length = wrap1.getInt();
                byte[] bytes2 = new byte[length];
                wrap1.get(bytes2,0,bytes2.length);
                long[] longs = decompress2WithoutZstd(bytes2, valueSize);
                result[i*valueSize] = (int) longs[0];
                for (int j = 1; j < longs.length; j++) {
                    result[i*valueSize+j] = (int) (result[i*valueSize+j-1]+longs[j]);
                }
            }
        }
        return result;
    }
    public static byte[] compress2WithoutZstd(long[] ints) {
        try {
            final long[] gapArray = toGapArray(ints);
            for (int i = 0; i < gapArray.length; i++) {
                gapArray[i] = ZigZagUtil.encodeLong(gapArray[i]);
            }
            long[] output = new long[ints.length];
            final int compress = Simple8.compress(gapArray, output);
            ByteBuffer resultBuffer = ByteBuffer.allocate(compress * 8);
            for (int i = 0; i < compress; i++) {
                resultBuffer.putLong(output[i]);
            }
//            return resultBuffer.array();
//            long[] outputArray = new long[compress];
//            System.arraycopy(output,0,outputArray,0,compress);
//            LongBuffer longBuffer = resultBuffer.asLongBuffer();
//            longBuffer.put(outputArray);
//            byte[] result = new byte[compress * 8];
//            int position = 0;
//            for (int i = 0; i < compress; i++) {
//                final long l = output[i];
//                final byte[] bytes = BytesUtil.long2Bytes(l);
//                ArrayUtils.copy(bytes, 0, result, position, 8);
//                position += 8;
//            }
//            return resultBuffer.array();
            return resultBuffer.array();
//            GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
//            return gzipCompress.compress(result);
//            return result;
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
        long aLong = longs[0];
        for (int i = 1; i < longs.length; i++) {
            longs[i] = longs[i] + aLong;
            aLong = longs[i];
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
            ByteBuffer resultBuffer = ByteBuffer.allocate(compress*8);
            long[] outputArray = new long[compress];
            System.arraycopy(output,0,outputArray,0,compress);
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

        byte[] bytes = Zstd.decompress(bytes1,valueSize*8);
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
        byte[] compress = Zstd.compress(allocate.array(), 12);
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
