package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.util.AssertUtil;
import com.alibaba.lindorm.contest.util.Pair;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class FloatCompress {
    static class Block {

        int leadingZero;
        int tailingZero;
        int blockSize;
        long value;

        public void setLeadingZero(int leadingZero) {
            this.leadingZero = leadingZero;
        }

        public void setTailingZero(int tailingZero) {
            this.tailingZero = tailingZero;
        }

        public void setBlockSize(int blockSize) {
            this.blockSize = blockSize;
        }

        public void setValue(long value) {
            this.value = value;
        }

        public int getLeadingZero() {
            return leadingZero;
        }

        public int getTailingZero() {
            return tailingZero;
        }

        public int getBlockSize() {
            return blockSize;
        }

        public long getValue() {
            return value;
        }

        boolean valueOf(int i) {
            AssertUtil.assertTrue(i < blockSize);
            return ((value >>> (blockSize - 1 - i)) & 0x1) > 0;
        }

        boolean fallInSameBlock(Block block) {
            return block != null && block.leadingZero == leadingZero && block.tailingZero == tailingZero;
        }
    }

    static Block calcBlock(double x, double y) {
        long a = Double.doubleToRawLongBits(x);
        long b = Double.doubleToRawLongBits(y);
        long xor = a ^ b;

        Block block = new Block();
        block.setLeadingZero(Long.numberOfLeadingZeros(xor));
        block.setTailingZero(Long.numberOfTrailingZeros(xor));
        block.setValue(xor >>> block.getTailingZero());
        block.setBlockSize(block.getValue() == 0 ? 0 : 64 - block.getLeadingZero() - block.getTailingZero());
        return block;
    }

    public static Pair encode(double[] values) {
        try {
            int offset = 0;
            BitSet buffer = new BitSet();

            boolean ctrlBit;
            double previous = values[0];
            Block prevBlock = null;
            for (int n = 1; n < values.length; n++) {
                Block block = calcBlock(previous, values[n]);
                if (block.getValue() == 0) {
                    buffer.clear(offset++);
                } else {
                    buffer.set(offset++);
                    buffer.set(offset++, ctrlBit = !block.fallInSameBlock(prevBlock));
                    if (ctrlBit) {
                        int leadingZero = block.getLeadingZero();
                        int blockSize = block.getBlockSize();
                        AssertUtil.assertTrue(leadingZero < (1 << 6));
                        AssertUtil.assertTrue((blockSize < (1 << 7)));
                        for (int i = 5; i > 0; i--) {
                            buffer.set(offset++, ((leadingZero >> (i - 1)) & 0x1) > 0);
                        }
                        for (int i = 6; i > 0; i--) {
                            buffer.set(offset++, ((blockSize >> (i - 1)) & 0x1) > 0);
                        }
                    }
                    for (int i = 0; i < block.getBlockSize(); i++) {
                        buffer.set(offset++, block.valueOf(i));
                    }
                }
                previous = values[n];
                prevBlock = block;
            }

            return Pair.of(Double.doubleToLongBits(values[0]), Pair.of(offset, buffer.toByteArray()));
        } catch (Exception e) {
            System.out.println("float encode error," + e);
        }
        return null;
    }

    public static List<Double> decode(long previous, int dataLen, byte[] data) {
        BitSet buffer = BitSet.valueOf(data);

        List<Double> values = new ArrayList<>();
        values.add(Double.longBitsToDouble(previous));

        int offset = 0;
        Block blockMeta = null;
        double p = values.get(0);
        while (offset < dataLen) {
            if (!buffer.get(offset++)) {
                values.add(p);
            } else {
                boolean ctrlBit = buffer.get(offset++);
                if (ctrlBit) {
                    int leadingZero = 0;
                    int blockSize = 0;
                    for (int i = 0; i < 5; i++) {
                        leadingZero = (leadingZero << 1) | (buffer.get(offset++) ? 0x1 : 0x0);
                    }
                    for (int i = 0; i < 6; i++) {
                        blockSize = (blockSize << 1) | (buffer.get(offset++) ? 0x1 : 0x0);
                    }
                    blockMeta = new Block();
                    blockMeta.setLeadingZero(leadingZero);
                    blockMeta.setBlockSize(blockSize);
                    blockMeta.setTailingZero(64 - leadingZero - blockSize);
                }
                AssertUtil.notNull(blockMeta);
                long value = 0;
                for (int i = 0; i < blockMeta.getBlockSize(); i++) {
                    value = (value << 1) | (buffer.get(offset++) ? 0x1 : 0x0);
                }
                previous ^= (value << blockMeta.getTailingZero());
                p = Double.longBitsToDouble(previous);
                values.add(p);

            }
        }

        AssertUtil.assertTrue((offset == dataLen));
        return values;
    }

    public static void main(String[] args) {
        double[] values = new double[]{121.19,117.19,64.19,60.19,75.19,18.19,124.19,52.19,67.19,82.19,78.19,101.19,97.19,112.19,59.19,55.19,70.19,93.19,89.19,104.19,100.19,47.19,62.19,58.19,81.19,96.19,92.19,107.19,54.19,50.19,141.19,69.19,84.19,99.19,27.19,42.19,133.19,129.19,76.19,72.19,87.19,34.19,30.19,121.19,79.19,

                82.18,71.18,10.18,106.18,95.18,23.18,108.18,47.18,36.18,132.18,139.18,60.18,67.18,145.18,84.18,73.18,80.18,90.18,97.18,104.18,93.18,32.18,110.18,117.18,38.18,45.18,123.18,130.18,69.18,58.18,154.18,75.18,82.18,71.18,99.18,88.18,95.18,102.18,23.18,119.18,108.18,47.18,36.18,132.18,67.18,

                46.1,72.1,114.1,140.1,92.1,70.1,48.1,74.1,26.1,142.1,94.1,120.1,72.1,98.1,50.1,76.1,28.1,54.1,96.1,122.1,74.1,100.1,52.1,78.1,30.1,56.1,8.1,124.1,76.1,102.1,54.1,80.1,32.1,58.1,10.1,36.1,152.1,104.1,130.1,82.1,108.1,60.1,86.1,38.1,132.1,

                125.11,94.11,144.11,113.11,82.11,90.11,109.11,78.11,47.11,97.11,66.11,35.11,85.11,54.11,12.11,62.11,31.11,83.11,133.11,102.11,152.11,121.11,90.11,140.11,109.11,67.11,117.11,86.11,55.11,105.11,74.11,43.11,93.11,62.11,31.11,81.11,39.11,8.11,141.11,110.11,79.11,129.11,98.11,67.11,136.11,

                88.14,74.14,73.14,73.14,59.14,58.14,125.14,43.14,43.14,110.14,110.14,28.14,109.14,95.14,13.14,94.14,80.14,12.14,79.14,79.14,146.14,64.14,64.14,131.14,63.14,49.14,130.14,116.14,34.14,115.14,101.14,33.14,100.14,100.14,18.14,85.14,85.14,71.14,84.14,70.14,70.14,69.14,55.14,55.14,121.14,

                89.17,84.17,75.17,70.17,65.17,68.17,122.17,49.17,44.17,120.17,115.17,25.17,101.17,96.17,23.17,99.17,94.17,4.17,80.17,75.17,151.17,78.17,56.17,132.17,59.17,54.17,130.17,125.17,35.17,111.17,106.17,33.17,109.17,104.17,14.17,90.17,85.17,80.17,88.17,66.17,61.17,69.17,64.17,59.17,121.17,

                102.12,78.12,135.12,123.12,27.12,72.12,105.12,93.12,69.12,126.12,114.12,18.12,75.12,63.12,39.12,96.12,84.12,60.12,117.12,105.12,90.12,66.12,54.12,111.12,87.12,75.12,132.12,108.12,96.12,81.12,57.12,45.12,102.12,78.12,66.12,123.12,99.12,87.12,72.12,48.12,36.12,93.12,69.12,57.12,159.12,

                96.16,96.16,119.16,119.16,61.16,84.16,107.16,107.16,49.16,130.16,72.16,72.16,95.16,95.16,37.16,118.16,60.16,60.16,83.16,83.16,106.16,106.16,48.16,129.16,71.16,71.16,94.16,94.16,36.16,117.16,59.16,59.16,82.16,82.16,24.16,105.16,121.16,47.16,144.16,70.16,86.16,93.16,109.16,35.16,155.16,

                129.15,87.15,141.15,114.15,72.15,99.15,111.15,84.15,42.15,96.15,69.15,27.15,81.15,54.15,12.15,66.15,39.15,80.15,134.15,107.15,146.15,119.15,92.15,131.15,104.15,77.15,116.15,89.15,62.15,101.15,74.15,47.15,86.15,59.15,32.15,71.15,44.15,17.15,139.15,112.15,85.15,124.15,97.15,70.15,136.15};
        Pair<Long, Pair<Integer, byte[]>> data = encode(values);
        System.out.println(data.getRight().getLeft()); // 编码后的数据长度，单位 bits
        List<Double> a = decode(data.getLeft(), data.getRight().getLeft(), data.getRight().getRight()); // 解码后的数据
        final GzipCompress gzipCompress = new GzipCompress();
        final ByteBuffer allocate = ByteBuffer.allocate(values.length * 8);
        for (double value : values) {
            allocate.putDouble(value);
        }
        final byte[] array = allocate.array();
        final byte[] compress = gzipCompress.compress(array);
        final byte[] bytes = gzipCompress.deCompress(compress);
        System.out.println(1);
    }
}
