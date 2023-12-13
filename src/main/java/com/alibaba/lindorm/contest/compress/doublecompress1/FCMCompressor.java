package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class FCMCompressor {
	
	double[] input;
	int len;
	HashMap<ArrayList<Double>, Double> arrayListMap;
	HashMap<Double, Double> doubleMap;
	int count = 0;
	double matches = 0;
	double perfectMatches = 0;
	int level = 1;
//	double[] bitReportArray = new double[64];
//	boolean bitReport = true;
//	int numInBitReport = 0;
	
	public FCMCompressor(double[] input) {
		this(input, 3);
	}
	
	public FCMCompressor(double[] input, int level) {
		this.input = input;
		this.len = input.length;
		if(level == 1) {
			this.doubleMap = new HashMap<Double, Double>();
		} else {
			this.arrayListMap = new HashMap<ArrayList<Double>, Double>();
		}
		this.count = 0;
		this.level = level;
	}
	
	public ByteBuffer compress() {
		ByteBuffer ret = ByteBuffer.allocate(8 * input.length);
		for(double d : input) {
//			if(d == 0.527090441) {
//				System.out.println("Hit");
//			}
			ret.put(compressOne(d));
			update();
			count++;
		}
//		System.out.println(Math.round(10000.0 * matches / count) / 100.0 + "," +
//				Math.round(10000.0 * perfectMatches / count) / 100.0);
//		if(numInBitReport > 0) {
//			bitReportArray = Util.normalizedBitReport(numInBitReport, bitReportArray);
//			Util.printBitReport(bitReportArray);
//		}
		return ret;
	}
	
	byte[] compressOne(double d) {
		Double prediction = predict();
		byte[] ret;
		if(prediction == null) {
			ret = Util.toByteArray(d);
		} else {
//			matches++;
//			if(d == prediction) {
//				perfectMatches++;
//			}
			ret = Util.xorByteArrays(Util.toByteArray(d), Util.toByteArray(prediction));
		}
//		Util.addToBitReport(Util.toDouble(ret), bitReportArray);
//		numInBitReport++;
		return ret;
	}
	
	public double[] compressForGorilla() {
		int len = input.length;
		double[] ret = new double[len];
		for(int i = 0; i < len; i++) {
			ret[i] = compressOneForGorilla(input[i]);
			update();
			count++;
		}
		return ret;
	}
	
	double compressOneForGorilla(double d) {
		Double prediction = predict();
		if(prediction == null) {
			return d;
		} else {
			return Util.xorDoubles(d, prediction);
		}
	}
	
	ArrayList<Double> getPrevList() {
		if(count >= level) {
			ArrayList<Double> ret = new ArrayList<Double>();
			for(int i = 1; i <= level; i++) {
				ret.add(Double.valueOf(input[count-i]));
			}
			return ret;
		} else {
			return null;
		}
	}
	
	Double getPrevDouble() {
		if(count >= level) {
			return Double.valueOf(input[count-1]);
		} else {
			return null;
		}
	}
	
	Double predict() {
		if(level == 1) {
			if(count >= level) {
				return doubleMap.get(getPrevDouble());
			} else {
				return null;
			}
		} else {
			if(count >= level) {
				return arrayListMap.get(getPrevList());
			} else {
				return null;
			}
		}
	}
	
	Double actual() {
		if(count < len) {
			return input[count];
		} else {
			return null;
		}
	}
	
	void update() {
		if(level == 1) {
			Double prev = getPrevDouble();
			Double actual = actual();
			if(prev != null && actual != null) {
				doubleMap.put(prev, actual);
			}
		} else {
			ArrayList<Double> prev = getPrevList();
			Double actual = actual();
			if(prev != null && actual != null) {
				arrayListMap.put(prev, actual);
			}
		}
	}
	
}
