package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.nio.ByteBuffer;

public class DFCMDecompressor {

	ByteBuffer byteInput;
	double[] doubleInput;
	int level = 3;
	
	public DFCMDecompressor (ByteBuffer byteInput, double[] doubleInput) {
		this(byteInput,doubleInput,3);
	}
	
	public DFCMDecompressor (ByteBuffer byteInput, double[] doubleInput, int level) {
		this.byteInput = byteInput;
		this.doubleInput = doubleInput;
		this.level = level;
	}
	
//	Converts the differences back into raw values.
	double[] convertBack (double[] input) {
		double prev = 0;
		int len = input.length;
		double[] ret = new double[len];
		for(int i = 0; i < len; i++) {
			ret[i] = input[i] + prev;
			prev = ret[i];
		}
		
		return ret;
	}
	
//	Decompresses the given ByteBuffer that has been compressed using 
//	DFCMCompressor.
	public double[] decompress() {
		double[] decompressed = new FCMDecompressor(byteInput,null,level).decompress();
		return convertBack(decompressed);
	}
	
//	Decompresses the given double[] that has been output by another 
//	decompressor (Gorilla or Sprintz). The name is a misnomer and 
//	should be changed.
	public double[] decompressFromGorilla() {
		double[] decompressed = new FCMDecompressor(null,doubleInput,level).decompressFromGorilla();
		return convertBack(decompressed);
	}
	
}
