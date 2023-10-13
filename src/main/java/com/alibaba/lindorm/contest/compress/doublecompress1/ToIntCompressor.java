package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

public class ToIntCompressor {

	double[] input;
	byte buf = 0;
	byte posInBuf = 7;
	ByteArrayOutputStream ret = new ByteArrayOutputStream();
	
	public ToIntCompressor (double[] input) {
		this.input = input;
	}
	
//	Compresses the given double[] lossily by converting it into the 
//	nearest integer of maximum precision.
	public ByteBuffer compress() throws IOException {
		double min = Double.POSITIVE_INFINITY; 
		for(double d : input) {
			if(d < min) {
				min = d;
			}
		}
		int len = input.length;
		for(int i = 0; i < len; i++) {
			input[i] -= min;
		}
		addBytes(Util.toByteArray(min));
		for(double d : input) {
			compressOne(d);
		}
		flush();
		return ByteBuffer.wrap(ret.toByteArray());
	}
	
//	Compresses one double.
	void compressOne (double d) throws IOException {
		double fPart = d % 1;
		double iPart = Math.abs(d - fPart);
		fPart = Math.abs(fPart);
		int iLen = 0;
		int fLen = 0;
		if(iPart != 0) {
			iLen += (int)(Math.log10(iPart)+1);
		}
		if(fPart != 0) {
			fLen = 9 - iLen;
		}
		int len = iLen + fLen;
		int divBy = 0;
		int integer;
		byte multFactor = 0;
		if (len >= 10 && iLen < 10) {
			fLen = 9 - iLen;
		} else if (iLen >= 10) {
			divBy = iLen - 9;
		}
		if(divBy == 0) {
			integer = (int) Math.round(d * Math.pow(10,fLen));
			multFactor = (byte) fLen;
		} else {
			integer = (int) Math.round(d / Math.pow(10,divBy));
		}
		addBytes(Util.toByteArray(integer));
		addByte(multFactor,5);
	}
	
	//Flushes the buffer.
	void flush () {
		int numToAdd = posInBuf + 1;
		if(numToAdd == 8) {
			numToAdd = 0;
		}
		ret.write(buf);
		ret.write(numToAdd);
	}
	
	//Adds the given bit to the buffer.
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
	
//	Adds the n least significant bits of the given byte to the buffer.
	void addByte (byte b, int n) {
		n--;
		for(int i = n; i >= 0; i--) {
			int bit = ((b & (1 << i)) >> i);
			char asChar = (bit == 0) ? '0' : '1';
			addBit(asChar);
		}
	}
	
//	Adds all 8 bits of all the bytes in the given byte[] to the buffer.
	void addBytes (byte[] bs) {
		for(byte b : bs) {
			addByte(b);
		}
	}
	
}
