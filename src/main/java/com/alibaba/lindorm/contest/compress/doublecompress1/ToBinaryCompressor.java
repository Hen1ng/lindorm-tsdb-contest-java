package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.nio.ByteBuffer;

public class ToBinaryCompressor {
	
	double[] input;
	
	public ToBinaryCompressor (double[] input) {
		this.input = input;
	}
	
	//Compresses the given double[] by converting it to it from ASCII representation to binary representation
	public ByteBuffer compress() {
		ByteBuffer ret = ByteBuffer.allocate(8 * input.length);
		for(double d : input) {
			//Util.toDoubleFast(Util.toByteArray(d))
			ret.put(Util.toByteArray(d));
		}
		return ret;
	}

}
