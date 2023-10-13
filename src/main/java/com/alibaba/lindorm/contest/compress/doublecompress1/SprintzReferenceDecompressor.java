package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class SprintzReferenceDecompressor {

	ByteBuffer input;
	ArrayList<Double> doubleList = new ArrayList<Double>();
	boolean stop = false;
	
	public SprintzReferenceDecompressor (ByteBuffer input) {
		this.input = (ByteBuffer) input.flip();
	}
	
//	Decompresses the given ByteBuffer that has been compressed using 
//	SprintzReferenceCompressor.
	public double[] decompress() {
		ByteBufferBitInput bitInput = new ByteBufferBitInput(input);
		SprintzValueDecompressor d = new SprintzValueDecompressor(bitInput);
		while(bitInput.bb.position() < bitInput.bb.limit()){
			try {
				doubleList.add(Double.longBitsToDouble(d.nextValue()));
			} catch (Exception e) {
				stop = true;
			}
		}
		int len = doubleList.size();
		double[] ret = new double[len];
		for(int i = 0; i < len; i++) {
			ret[i] = doubleList.get(i);
		}
		return ret;
	}
	
}
