package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.nio.ByteBuffer;

/**
 * Value decompressor for Gorilla encoded values
 *
 * @author Michael Burman
 */
public class SprintzValueDecompressor {
    private final BitInput in;
    private final Predictor predictor;
    ByteBuffer inputBB;
    int inputByteLen;
	int inputBitLen;
	int pos = 0;
	int blockSize = 2;
	int leftInBlock = 0;
	long zeroesLeft = 0;
	int nBits;
	long ret;

    public SprintzValueDecompressor(BitInput input) {
        this(input, new LastValuePredictor());
    }

    public SprintzValueDecompressor(BitInput input, Predictor predictor) {
        this.in = input;
        this.predictor = predictor;
        this.inputBB = ((ByteBufferBitInput) input).getByteBuffer();
        this.inputByteLen = inputBB.capacity();
		this.inputBitLen = inputByteLen * 8;
    }

    public long nextValue() {
    	if(leftInBlock > 0) {
    		long xor = getLong(1) << 63;
    		xor |= getLong(nBits);
    		leftInBlock--;
    		ret = xor ^ predictor.predict();
    		predictor.update(ret);
    		return ret;
    	} else if(zeroesLeft > 0) {
    		zeroesLeft--;
    		return predictor.predict();
    	} else {
    		nBits = (int) getLong(7);
    		if(nBits == 0) {
    			long numZeroBlocks = getLong(16);
    			zeroesLeft = numZeroBlocks * blockSize;
    		} else {
    			leftInBlock = blockSize;
    		}
    		return nextValue();
    	}
    }
    
    public long getLong(int bits) {
    	pos += bits;
    	return in.getLong(bits);
    }
}
