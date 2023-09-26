package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.util.ArrayUtils;
import com.alibaba.lindorm.contest.util.AssertUtil;
import com.alibaba.lindorm.contest.util.Pair;
import com.alibaba.lindorm.contest.util.UnsafeUtil;

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

    public static byte[] encode2(double[] values) {
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
            final byte[] byteArray = buffer.toByteArray();
            final long l = Double.doubleToLongBits(values[0]);
            final ByteBuffer allocate = ByteBuffer.allocate(8 + 4 + byteArray.length);
            allocate.putLong(l);
            allocate.putInt(offset);
            allocate.put(byteArray);
            return allocate.array();
        } catch (Exception e) {
            System.out.println("float encode error," + e);
        }
        return null;
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

    public static double[] decode2(byte[] data) {
        final ByteBuffer wrap = ByteBuffer.wrap(data);
        final long aLong = wrap.getLong();
        final int anInt = wrap.getInt();
        byte[] data1 = new byte[data.length - 12];
        ArrayUtils.copy(data, 8 + 4, data1, 0, data1.length);
        final List<Double> decode = decode(aLong, anInt, data1);
        double[] doubles = new double[decode.size()];
        for (int i = 0; i < decode.size(); i++) {
            doubles[i] = decode.get(i);
        }
        return doubles;
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

//        AssertUtil.assertTrue((offset == dataLen));
        return values;
    }

    public static void main(String[] args) {
        double[] values = new double[]{26965.26599263888,26965.25520601932,26965.26247191294,
                26965.25361209828, 26965.258724932894, 26965.25695619905,
                26965.260738665187, 26965.26014345266, 26965.266010121133, 26965.25522351106, 26965.26248999291, 26965.258741836402};
        Pair<Long, Pair<Integer, byte[]>> data = encode(values);
        System.out.println(data.getRight().getLeft()); // 编码后的数据长度，单位 bits
        List<Double> a = decode(data.getLeft(), data.getRight().getLeft(), data.getRight().getRight()); // 解码后的数据
        final GzipCompress gzipCompress = new GzipCompress();
        final ByteBuffer allocate = ByteBuffer.allocate(values.length * 8);
        for (double value : values) {
            allocate.putDouble(value);
        }
        final byte[] compress = gzipCompress.compress(allocate.array());
        final byte[] bytes = gzipCompress.deCompress(compress);
        final boolean equals = ArrayUtils.equals(bytes, 0, bytes.length, allocate.array());

        final byte[] compress1 = ZstdCompress.compress(allocate.array(), 15);
        final byte[] decompress = ZstdCompress.decompress(compress1);
        final boolean equals1 = ArrayUtils.equals(decompress, 0, decompress.length, allocate.array());
        System.out.println(1);
    }
}
