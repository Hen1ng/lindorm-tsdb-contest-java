package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.compress.gorilla.*;

import java.nio.ByteBuffer;

public class DoubleCompress {

    public static ByteBuffer encode(double[] values) {
        ByteBufferBitOutput byteBufferBitOutput = new ByteBufferBitOutput();
        final ValueCompressor valueCompressor = new ValueCompressor(byteBufferBitOutput, new LastValuePredictor());
        for (int i = 0; i < values.length; i++) {
            final double value = values[i];
            if (i == 0) {
                valueCompressor.writeFirst(Double.doubleToRawLongBits(value));
            } else {
                valueCompressor.compressValue(Double.doubleToRawLongBits(value));
            }
        }
        byteBufferBitOutput.writeBits(0x0F, 4);
        byteBufferBitOutput.writeBits(0xFFFFFFFF, 32);
        byteBufferBitOutput.skipBit();
        byteBufferBitOutput.flush();
        final ByteBuffer byteBuffer = byteBufferBitOutput.getByteBuffer();
        byteBuffer.flip();
        return byteBuffer.slice();
    }

    public static double[] decode(ByteBuffer byteBuffer, int valueSize) {
        double[] doubles = new double[valueSize];
        final ByteBufferBitInput byteBufferBitInput = new ByteBufferBitInput(byteBuffer);
        final ValueDecompressor valueDecompressor = new ValueDecompressor(byteBufferBitInput, new LastValuePredictor());
        final long l =
                valueDecompressor.readFirst();
        int j = 0;
        doubles[j] = Double.longBitsToDouble(l);
        j++;
        int i = valueSize - 1;
        while (i > 0) {
            final long l1 = valueDecompressor.nextValue();
            final double v1 = Double.longBitsToDouble(l1);
            i--;
            doubles[j] = v1;
            j++;
        }
        return doubles;
    }

    public static void main(String[] args) {
        double[] values = new double[]{26965.26599263888,26965.25520601932,26965.26247191294,
                26965.25361209828, 26965.258724932894, 26965.25695619905,
                26965.260738665187, 26965.26014345266, 26965.266010121133, 26965.25522351106, 26965.26248999291, 26965.258741836402};
        ByteBuffer byteBuffer = encode(values);
        System.out.println(byteBuffer);
        double[] decode = decode(byteBuffer, values.length);
        for (int i = 0; i < decode.length; i++) {
            System.out.println(decode[i]);
        }
    }
}
