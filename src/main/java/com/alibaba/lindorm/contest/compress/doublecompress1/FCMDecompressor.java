package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class FCMDecompressor {
	
	ByteBuffer byteInput;
	double[] doubleInput;
	double[] output;
	HashMap<ArrayList<Double>, Double> arrayListMap;
	HashMap<Double, Double> doubleMap;
	int count;
	double actual;
	int len;
	int level = 3;
	
	public FCMDecompressor(ByteBuffer byteInput, double[] doubleInput) {
		this(byteInput, doubleInput, 3);
	}

	public FCMDecompressor(ByteBuffer byteInput, double[] doubleInput, int level) {
		if(byteInput != null) {
			this.byteInput = byteInput;
			if(this.byteInput.position() != 0) {
				this.byteInput.flip();
			}
			this.output = new double[byteInput.capacity()/8];
		}
		if(doubleInput != null) {
			this.doubleInput = doubleInput;
			this.len = doubleInput.length;
			this.output = new double[len];
		}
		if(level == 1) {
			this.doubleMap = new HashMap<Double, Double>();
		} else {
			this.arrayListMap = new HashMap<ArrayList<Double>, Double>();
		}
		this.count = 0;
		this.level = level;
	}
	
//	Decompress the given ByteBuffer that has been compressed using FCMCompressor.
	public double[] decompress() {
		while(byteInput.position() < byteInput.capacity()) {
			output[count] = decompressOne();
			update();
			count++;
		}
		return output;
	}
	
//	Decompress one double.
	double decompressOne() {
		actual = byteInput.getDouble();
		Double prediction = predict();
		return Util.xorDoubles(actual, prediction);
	}
	
//	Decompress the given double[] that has already been run through another
//	decompressor (Gorilla or Sprintz). The function name is a misnomer, and
//	should be changed.
	public double[] decompressFromGorilla() {
		for(int i = 0; i < len; i++) {
			actual = doubleInput[i];
			output[i] = decompressOneFromGorilla(actual);
			update();
			count++;
		}
		return output;
	}
	
//	Decompress one double.
	double decompressOneFromGorilla (double input) {
		Double prediction = predict();
		return Util.xorDoubles(input, prediction);
	}
	
//	Gets the n (where n = level) previous doubles decompressed.
	ArrayList<Double> getPrevList() {
		if(count >= level) {
			ArrayList<Double> ret = new ArrayList<Double>();
			for(int i = 1; i <= level; i++) {
				ret.add(Double.valueOf(output[count-i]));
			}
			return ret;
		} else {
			return null;
		}
	}
	
//	Gets the previous double decompressed.
	Double getPrevDouble() {
		if(count >= level) {
			return Double.valueOf(output[count-1]);
		} else {
			return null;
		}
	}
	
//	Predicts the next double.
	Double predict() {
		if(level == 1) {
			if(count >= level) {
				Double ret = doubleMap.get(getPrevDouble());
				if (ret != null) {
					return ret;
				}
			}
		} else {
			if(count >= level) {
				Double ret = arrayListMap.get(getPrevList());
				if (ret != null) {
					return ret;
				}
			}
		}
		return (double) 0;
	}
	
//	Updates the prediction table.
	void update() {
		if(level == 1) {
			Double prev = getPrevDouble();
			if(prev != null) {
				doubleMap.put(prev, output[count]);
			}
		} else {
			ArrayList<Double> prev = getPrevList();
			if(prev != null) {
				arrayListMap.put(prev, output[count]);
			}
		}
	}
	
}
