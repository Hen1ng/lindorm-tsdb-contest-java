package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.nio.ByteBuffer;

public class GorillaReferenceCompressor {
	
	double[] input;
	int len;

	public GorillaReferenceCompressor(double[] input) {
		this.input = input;
		this.len = input.length;
	}
	
//	Compresses the given double[] using the reference implementation of Gorilla
//	located in gorilla-tsc.
	public ByteBuffer compress() {
		
		ByteBufferBitOutput output = new ByteBufferBitOutput();
        ValueCompressor c = new ValueCompressor(output);
        
        c.writeFirst(Double.doubleToRawLongBits(input[0]));
        for(int i = 1; i < len; i++) {
            c.compressValue(Double.doubleToRawLongBits(input[i]));
        }
        output.writeBits(0x0F, 4);
        output.writeBits(0xFFFFFFFF, 32);
        output.skipBit();
        output.flush();
        return output.getByteBuffer();
	}
	
}
