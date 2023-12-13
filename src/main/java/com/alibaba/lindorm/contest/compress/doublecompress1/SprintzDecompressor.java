package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;

public class SprintzDecompressor {
	
	ByteBuffer input;
	byte[] inputArray;
	int inputByteLen;
	int inputBitLen;
	int pos = 0;
	double prev = 0;
	byte blockSize;
	byte[] block;
	ArrayList<Double> doubleList = new ArrayList<Double>();
	int numBlocksDecompressed = 0;
	
	public SprintzDecompressor (ByteBuffer input) {
		this(input, 8);
	}
	
	public SprintzDecompressor (ByteBuffer input, int blockSize) {
		this.input = input;
		this.blockSize = (byte) blockSize;
		this.inputByteLen = input.capacity();
		this.inputBitLen = inputByteLen * 8;
		this.inputArray = new byte[inputByteLen];
		input.get(inputArray);
		int numExtra = inputArray[inputByteLen-1];
		inputBitLen -= numExtra + 8;
		block = new byte[8];
	}
	
//	Decompresses the given ByteBuffer that has been compressed using 
//	SprintzCompressor.
	public double[] decompress() {
		
		int targetLen = nextN(32).getInt();
		while(pos < inputBitLen && doubleList.size() < targetLen) {
			byte nBits = nextN(7).get();
			if(nBits == 0) {
				short numZeroBlocks = nextN(16).getShort();
				for(int i = 0; i < numZeroBlocks * blockSize; i++) {
					doubleList.add(prev);
				}
			} else {
				numBlocksDecompressed++;
				decompressBlock(nBits);
			}
		}
		
		int len = doubleList.size();
		double[] ret = new double[targetLen];
		for(int i = 0; i < targetLen; i++) {
			ret[i] = doubleList.get(i);
		}
		return ret;
		
	}
	
	//Decompresses a block of doubles.
	void decompressBlock (int nBits) {
		
		int numToGet = Math.min(blockSize, (inputBitLen - pos) / nBits);
		for(int i = 0; i < numToGet; i++) {
			byte[] arr = new byte[8];
			arr[0] |= nextN(1).get() << 7;
			int numBytes = (int) Math.floor((nBits - 1) / 8.0);
			int numHangBits = (nBits - 1) - (numBytes * 8);
			arr[7 - numBytes] |= nextN(numHangBits).get();
			for(int j = 0; j < numBytes; j++) {
				arr[8 - numBytes + j] = nextN(8).get();
			}
			double ret = Util.toDouble(arr);
			//ret += prev;
			ret = Util.xorDoubles(ret, prev);
			prev = ret;
			doubleList.add(ret);
		}
		
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
		if(n == 0) {
			ByteBuffer ret = ByteBuffer.allocate(8);
			return ret;
		}
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
		if(numLeft != 0) {
			buf &= (byte) Math.pow(2, (8-numLeft)) - 1;
		}
		ret.put(buf);
		ret.flip();
		return ret;
	}

}
