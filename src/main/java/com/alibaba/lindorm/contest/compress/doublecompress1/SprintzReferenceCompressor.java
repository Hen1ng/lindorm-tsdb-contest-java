package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SprintzReferenceCompressor {
	
	double[] input;
	int len;

	public SprintzReferenceCompressor(double[] input) {
		this.input = input;
		this.len = input.length;
	}
	
//	Compresses the given double[] using the implementation of Sprintz
//	located in gorilla-tsc.
	public ByteBuffer compress() throws IOException {
		
		ByteBufferBitOutput output = new ByteBufferBitOutput();
        SprintzValueCompressor c = new SprintzValueCompressor(output);
        
        for(int i = 0; i < len; i++) {
            c.compressValue(Double.doubleToRawLongBits(input[i]), i == len - 1);
        }
        output.writeBits(0x0F, 4);
        output.writeBits(0xFFFFFFFF, 32);
        output.skipBit();
        output.flush();
        return output.getByteBuffer();
	}
	
}
