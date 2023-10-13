package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SprintzCompressor {
	
	double prev = 0;
	double[] input;
	byte blockSize;
	double[] block;
	int posInBlock = 0;
	byte buf = 0;
	byte posInBuf = 7;
	ByteArrayOutputStream output = new ByteArrayOutputStream();
	short numZeroBlocks = 0;
	double[] bitReportArray = new double[64];
	boolean bitReport = true;
	int numInBitReport = 0;
	int numCompressed=0;
	
	public SprintzCompressor (double[] input) {
		this(input, 8);
	}
	
	public SprintzCompressor (double[] input, int blockSize) {
		this.input = input;
		this.blockSize = (byte) blockSize;
		block = new double[blockSize];
	}
	
//	Compresses the given double[] using Sprintz[].
	public ByteBuffer compress() throws IOException {
		output.write(Util.toByteArray(input.length));
		for(double d : input) {
			compressOne(d);
		}
		flush();
		return ByteBuffer.wrap(output.toByteArray());
	}
	
//	Compresses one double.
	void compressOne(double d) throws IOException {
		if(posInBlock == blockSize) {
			posInBlock = 0;
			compressBlock(false);
		}
		block[posInBlock] = err(d);
		prev = d;
		posInBlock++;
	}
	
//	Compresses the current block of doubles.
	void compressBlock (boolean flushing) throws IOException {
		byte[][] bs = new byte[blockSize][8]; 
		byte[] b = Util.toByteArray(block[0]);
		bs[0] = b;
		for(int i = 1; i < blockSize; i++) {
			bs[i] = Util.toByteArray(block[i]);
			b = Util.orByteArrays(b, bs[i]);
		}
		byte nBits = (byte) (64 - Util.bitLeadingZeroesIgnoringSign(b));
		if(nBits == 0 && b[0] == 0) {
			numZeroBlocks++;
			if(flushing) {
				addByte((byte) 0, 7);
				addBytes(Util.toByteArray(numZeroBlocks));
			}
		} else {
			if(numZeroBlocks > 0) {
				addByte((byte) 0, 7);
				addBytes(Util.toByteArray(numZeroBlocks));
				numZeroBlocks = 0;
			}
			addByte((byte) (nBits+1), 7);
			int numToAdd = (posInBlock == 0) ? blockSize : posInBlock;
			for(int i = 0; i < numToAdd; i++) {
				addErrAsNBits(bs[i], nBits);
			}
		}
		block = new double[blockSize];
		numCompressed++;
	}
	
//	Flushes the buffer.
	void flush () throws IOException {
		compressBlock(true);
		int numToAdd = posInBuf + 1;
		if(numToAdd == blockSize) {
			numToAdd = 0;
		}
		output.write(buf);
		output.write(numToAdd);
	}
	
//	Adds the given bit to the buffer.
	void addBit (char c) {
		if(posInBuf == -1) {
			output.write(buf);
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
	
//	Adds all 8 bits of all of the bytes in the given byte[] to the buffer.
	void addBytes (byte[] bs) {
		for(byte b : bs) {
			addByte(b);
		}
	}
	
//	Adds all of the errors in the current block to the buffer using the 
//	appropriate amount of bits.
	void addErrAsNBits(byte[] err, byte nBits) {
		int signBit = ((err[0] & (1 << 7)) >> 7);
		addBit((signBit == 0) ? '0' : '1');
		int numBytes = (int) Math.ceil(nBits / 8.0);
		int numHangBits = nBits - ((numBytes - 1) * 8);
		boolean first = true;
		for(int i = (8 - numBytes); i < 8; i++) {
			if(first) {
				addByte(err[i], numHangBits);
				first = false;
			} else {
				addByte(err[i]);
			}
		}
	}
	
//	Calculates and returns the error between the prediction and the actual double.
//	The delta method of the Sprintz paper simply takes the difference, but I have
//	modified it to instead take the xor which leads to greater compression.
	double err (double d) {
		//return d - predict(d);
		return Util.xorDoubles(d, predict(d));
	}
//	Predicts the next double. This implementation simply predicts that the 
//	next double will be equal to the last double.
	double predict (double d) {
		return prev;
	}

}
