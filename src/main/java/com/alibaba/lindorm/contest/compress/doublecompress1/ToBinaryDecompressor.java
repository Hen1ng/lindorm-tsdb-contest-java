package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.nio.ByteBuffer;

public class ToBinaryDecompressor {
	
	ByteBuffer input;
	double[] output;
	int count = 0;
	
	public ToBinaryDecompressor (ByteBuffer input) {
		this.input = (ByteBuffer) input.flip();
		this.output = new double[input.capacity()/8];
	}
	
	// Decompresses the given ByteBuffer that has been compressed using the ToBinaryCompressor
	public double[] decompress() {
		while(input.position() < input.capacity()) {
			output[count] = input.getDouble();
			count++;
		}
		return output;
	}

}
