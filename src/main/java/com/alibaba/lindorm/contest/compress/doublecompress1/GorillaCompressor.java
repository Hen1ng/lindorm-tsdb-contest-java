package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class GorillaCompressor {
	
	double prev = 0.0;
	byte prevLeading = 9;
	byte prevTrailing = 9;
	boolean first = true;
	double[] input;
	byte buf = 0;
	byte posInBuf = 7;
	boolean frontCoding;
	ByteArrayOutputStream ret = new ByteArrayOutputStream();
	long totalConvertTime = 0;
	
	public GorillaCompressor (double[] input, boolean frontCoding) {
		this.input = input;
		this.frontCoding = frontCoding;
	}
	
//	Compresses the given double[] using Gorilla.
	public ByteBuffer compress() throws IOException {
		long startTime = System.nanoTime(); 
		for(double d : input) {
			compressOne(d);
		}
		flush();
		long endTime = System.nanoTime();
		long time = endTime - startTime;
//		System.out.println(100 * (double) totalConvertTime / time);
		return ByteBuffer.wrap(ret.toByteArray());
	}
	
//	Compresses one double.
	void compressOne(double d) throws IOException {
		double xored = Util.xorDoubles(d, prev);
		long convertStartTime = System.nanoTime();
		byte[] arr = Util.toByteArray(xored);
		long convertEndTime = System.nanoTime();
		totalConvertTime += convertEndTime - convertStartTime;
		if(first == true) {
			addBytes(Util.toByteArray(d));
			first = false;
			prev = d;
		} else {
			if (Double.compare(xored, 0) == 0) {
				addBit('0');
			} else {
				addBit('1');
				byte leading = (byte) leadingZeroes(arr);
				byte trailing = (byte) trailingZeroes(arr);
				if(frontCoding) {
					if(leading >= prevLeading && trailing >= prevTrailing) {
						addBit('0');
						byte[] meaningful = meaningful(arr, prevLeading, prevTrailing);
						addBytes(meaningful);
						prev = d;
					} else {
						addBit('1');
						addByte(leading, 5);
						byte[] meaningful = meaningful(arr, leading, trailing);
						byte meaningfulLen = (byte) meaningful.length;
						addByte(meaningfulLen, 6);
						addBytes(meaningful);
						setPrev(d, leading, trailing);
					}
				} else {
					addByte(leading, 5);
					byte[] meaningful = meaningful(arr, leading, trailing);
					byte meaningfulLen = (byte) meaningful.length;
					addByte(meaningfulLen, 6);
					addBytes(meaningful);
					setPrev(d, leading, trailing);
				}
			}
		}
	}
	
//	Sets the previous double and number of leading and trailing zeroes.
	void setPrev (double d, byte leading, byte trailing) {
		prev = d;
		prevLeading = leading;
		prevTrailing = trailing;
	}
	
//	Gets the number of trailing (to the left of the meaningful) zero bytes
//	in the given byte[].
	int trailingZeroes (byte[] bs) {
		int len = bs.length;
		int ret = 0;
		for(int i = 0; i < len; i++) {
			if(bs[i] == 0) {
				ret++;
			} else {
				return ret;
			}
		}
		return ret;
	}
	
//	Gets the number of leading (to the right of the meaningful) zero bytes
//	in the given byte[].
	int leadingZeroes (byte[] bs) {
		int len = bs.length - 1;
		int ret = 0;
		for(int i = len; i >= 0; i--) {
			if(bs[i] == 0) {
				ret++;
			} else {
				return ret;
			}
		}
		return ret;
	}
	
//	Gets the meaningful bytes of the given byte[].
	byte[] meaningful (byte[] input, int leading, int trailing) {
		return Arrays.copyOfRange(input, trailing, input.length - leading);
	}
	
//	Flushes the buffer.
	void flush () {
		int numToAdd = posInBuf + 1;
		if(numToAdd == 8) {
			numToAdd = 0;
		}
		ret.write(buf);
		ret.write(numToAdd);
	}
	
//	Adds the given bit to the buffer.
	void addBit (char c) {
		if(posInBuf == -1) {
			ret.write(buf);
			buf = 0;
			posInBuf = 7;
		}
		if(c == '1') {
			buf |= 1 << posInBuf;
		}
		posInBuf--;
	}
	
//	Adds all 8 bits of the given byte to the buffer.
	void addByte (byte b) {
		addByte(b, 8);
	}
	
//	Adds the n least significant bits of the current byte to the buffer.
	void addByte (byte b, int n) {
		n--;
		for(int i = n; i >= 0; i--) {
			int bit = ((b & (1 << i)) >> i);
			char asChar = (bit == 0) ? '0' : '1';
			addBit(asChar);
		}
	}
	
//	Adds all 8 bits of all of the bytes in the byte[] to the buffer.
	void addBytes (byte[] bs) {
		for(byte b : bs) {
			addByte(b);
		}
	}
}
