package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.util.AssertUtil;
import com.alibaba.lindorm.contest.util.Pair;

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

    static Pair encode(double[] values) {
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
    }

    static List<Double> decode(Pair<Long, Pair<Integer, byte[]>> data) {

        long previous = data.getLeft();
        int dataLen = data.getRight().getLeft();
        BitSet buffer = BitSet.valueOf(data.getRight().getRight());

        List<Double> values = new ArrayList<>();
        values.add(Double.longBitsToDouble(previous));

        int offset = 0;
        Block blockMeta = null;
        while (offset < dataLen) {
            if (!buffer.get(offset++)) {
                values.add(0d);
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
                values.add(Double.longBitsToDouble(previous));
            }
        }

        AssertUtil.assertTrue((offset == dataLen));
        return values;
    }

    public static void main(String[] args) {
        double[] values = new double[]{15.5, 14.0625, 3.25, 8.625, 13.1, 0, 25.5};
        Pair<Long, Pair<Integer, byte[]>> data = encode(values);
        System.out.println(data.getRight().getLeft()); // 编码后的数据长度，单位 bits
        System.out.println(decode(data)); // 解码后的数据
    }
}
