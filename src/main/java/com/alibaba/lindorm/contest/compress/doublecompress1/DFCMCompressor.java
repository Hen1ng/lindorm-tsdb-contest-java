package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.nio.ByteBuffer;

public class DFCMCompressor {
	
	double[] input;
	int level = 3;
	
	public DFCMCompressor(double[] input) {
		this(input, 3);
	}
	
	public DFCMCompressor(double[] input, int level) {
		this.input = input;
		this.level = level;
	}
	
//	Converts the raw double values to differences.
	double[] convert() {
		int len = input.length;
		double[] output = new double[len];
		for(int i = 0; i < len; i++) {
			if(i == 0) {
				output[i] = input[i];
			} else {
				output[i] = input[i] - input[i-1];
			}
		}
		return output;
	}
	
//	Compresses the given double[] using DFCM.
	public ByteBuffer compress() {
		return new FCMCompressor(convert(), level).compress();
	}
	
//	Compresses the given double[] to a new double[] that can then be further
//	compressed by Gorilla or Sprintz. The name is a misnomer and should be changed.
	public double[] compressForGorilla() {
		double[] test = new FCMCompressor(convert(), level).compressForGorilla();
		return test;
	}
	
}
