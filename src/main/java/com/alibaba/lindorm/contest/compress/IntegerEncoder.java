package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.compress.intcodec.simple.Simple9Codes;
import com.alibaba.lindorm.contest.compress.intcodec2.integercompression.Simple16;
import com.alibaba.lindorm.contest.compress.intcodec2.integercompression.Simple9;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class IntegerEncoder {
    private static final long INT_COMPRESSED_RLE = 2;

    private static final long SIMPLE8B_MAXVALUE = 1L<<28-1;

    private static final long INT_UNCOMPRESSED = 0;

    private static final int INT_COMPRESSED_SIMPLE =1;

    private long prev;
    private boolean rle;
    private int[] values;

    public IntegerEncoder(int sz) {
        this.rle = true;
        this.values = new int[sz];
    }

    public byte[] Bytes() throws Exception {
        if(rle&& values.length>2){
            return encodeRLE();
        }
        for (long value : values) {
            if(value > SIMPLE8B_MAXVALUE){
                return encodeUncompressed();
            }
        }
        return encodePacked();
    }
    public byte[] encodePacked() throws Exception {
        if (values.length == 0) {
            return null;
        }

        // Assuming a Simple8b class exists with a method EncodeAll that takes a portion of the array.
        int[] encoded = Simple9Codes.innerEncode(values);

        ByteBuffer buffer = ByteBuffer.allocate(1 + (encoded.length + 1) * 8)
                .order(ByteOrder.BIG_ENDIAN);

        // 4 high bits of the first byte store the encoding type for the block
        buffer.put((byte) (INT_COMPRESSED_SIMPLE << 4));

        // Write the first value since it's not part of the encoded values
        buffer.putLong(values[0]);

        // Write the encoded values
        for (int v : encoded) {
            buffer.putInt(v);
        }

        return buffer.array();
    }
    public byte[] encodeRLE() throws Exception {
        // Large varints can take up to 10 bytes. We're storing 3 + 1 type byte.
        ByteBuffer buffer = ByteBuffer.allocate(31).order(ByteOrder.BIG_ENDIAN);

        // 4 high bits used for the encoding type
        buffer.put((byte) (INT_COMPRESSED_RLE << 4));

        // The first value
        buffer.putInt(values[0]);

        // The first delta
        buffer.put(putUvarint(values[1]));

        // The number of times the delta is repeated
        buffer.put(putUvarint(values.length - 1));

        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);

        return result;
    }
    public byte[] encodeUncompressed() {
        if (values.length == 0) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.allocate(1 + values.length * 8)
                .order(ByteOrder.BIG_ENDIAN);

        // 4 high bits of the first byte store the encoding type for the block
        buffer.put((byte) (INT_UNCOMPRESSED << 4));

        for (int v : values) {
            buffer.putInt(v);
        }

        return buffer.array();
    }
    // Simulate the PutUvarint behavior. This is a basic function, a full uvarint implementation may differ
    private byte[] putUvarint(int value) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(5); // Adjusted size since int is 4 bytes and max possible size for encoded int is 5 bytes
        while (true) {
            if ((value & ~0x7F) == 0) {
                buffer.put((byte) value);
                break;
            } else {
                buffer.put((byte) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }
}
