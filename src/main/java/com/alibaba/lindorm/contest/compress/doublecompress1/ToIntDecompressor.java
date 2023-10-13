package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ToIntDecompressor {
	
	byte[] inputArray;
	int inputByteLen;
	int inputBitLen;
	int pos = 0;
	ArrayList<Double> doubleList = new ArrayList<Double>();
	
	public ToIntDecompressor(ByteBuffer input) {
		inputByteLen = input.capacity();
		inputBitLen = inputByteLen * 8;
		inputArray = new byte[inputByteLen];
		input.get(inputArray);
		int numExtra = inputArray[inputByteLen-1];
		inputBitLen -= numExtra + 8;
	}

//	Decompresses the given ByteBuffer that has been compressed using 
//	ToIntCompressor.
	public double[] decompress() {
		double min = (double) nextN(64).getDouble();
		while(pos < inputBitLen) {
			double decompressed = decompressOne();
			doubleList.add(decompressed);
		}
		int len = doubleList.size();
		double[] ret = new double[len];
		for(int i = 0; i < len; i++) {
			ret[i] = doubleList.get(i) + min;
		}
		return ret;
	}
	
//	Decompresses one double.
	double decompressOne() {
		double base = (double) nextN(32).getInt();
		byte multFactor = nextN(5).get();
		return base / Math.pow(10, multFactor);
	}
	
//	Gets the current byte index of the input array.
	int currIndex() {
		return (int) Math.floor(pos/8);
	}
	
//	Gets the current bit offset in the current byte of the input array.
	int currOffset() {
		return pos - (currIndex() * 8);
	}
	
//	Gets the the shift amount of the current bit of the current byte in the 
//	input array.
	int currShiftAmount() {
		return 7 - currOffset();
	}
	
//	Gets the next bit of the input array and increments the position in the array.
	int nextBit() {
		byte b = inputArray[currIndex()];
		int shift = currShiftAmount();
		pos++;
		return (b & 1 << shift) >> shift;
	}
	
//	Gets the next n bits of the input array and increments the position in the 
//	array by n.
	ByteBuffer nextN (int n) {
		ByteBuffer ret = ByteBuffer.allocate((int) Math.ceil(n/8.0));
		byte buf = 0;
		int posInBuf = 7;
		for(int i = 0; i < n; i++) {
			if(posInBuf == -1) {
				ret.put(buf);
				buf = 0;
				posInBuf = 7;
			}
			buf |= nextBit() << posInBuf;
			posInBuf--;
		}
		int numLeft = posInBuf + 1;
		buf = (byte) (buf >> numLeft);
		ret.put(buf);
		ret.flip();
		return ret;
	}
	
}
