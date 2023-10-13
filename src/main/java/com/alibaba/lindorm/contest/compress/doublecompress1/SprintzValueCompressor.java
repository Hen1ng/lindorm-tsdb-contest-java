package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.io.IOException;

/**
 * ValueCompressor for the Gorilla encoding format. Supply with long presentation of the value,
 * in case of doubles use Double.doubleToRawLongBits(value)
 *
 * @author Michael Burman
 */
public class SprintzValueCompressor {

    private Predictor predictor;
    private BitOutput out;
    
	double prev = 0;
	double[] input;
	byte blockSize = 2;
	long[] block = new long[blockSize];
	int posInBlock = 0;
	short numZeroBlocks = 0;

    public SprintzValueCompressor(BitOutput out) {
        this(out, new LastValuePredictor());
    }

    public SprintzValueCompressor(BitOutput out, Predictor predictor) {
        this.out = out;
        this.predictor = predictor;
    }

    public void compressValue(long value, boolean flushing) throws IOException {
    	if(posInBlock == blockSize) {
			posInBlock = 0;
			compressBlock(false);
		}
		block[posInBlock] = value ^ predictor.predict();
		predictor.update(value);
		posInBlock++;
		if(flushing) {
			compressBlock(true);
		}
    }
    
//	Compresses the current block of doubles.
	void compressBlock (boolean flushing) throws IOException {
		long b = block[0];
		for(int i = 1; i < blockSize; i++) {
			b |= block[i];
		}
		int nBits = bitLeadingZeroesIgnoringSign(b);
		if(nBits == 0 && getNBit(b,64) == 0) {
			numZeroBlocks++;
			if(flushing) {
				out.writeBits(0, 7);
				out.writeBits(numZeroBlocks, 16);
			}
		} else {
			if(numZeroBlocks > 0) {
				out.writeBits(0, 7);
				out.writeBits(numZeroBlocks, 16);
				numZeroBlocks = 0;
			}
			out.writeBits(nBits, 7);
			int numToAdd = (posInBlock == 0) ? blockSize : posInBlock;
			for(int i = 0; i < numToAdd; i++) {
				addErrAsNBits(block[i], nBits);
			}
		}
		block = new long[blockSize];
	}
	
//	Adds all of the errors in the current block to the buffer using the 
//	appropriate amount of bits.
	void addErrAsNBits(long err, int nBits) {
		out.writeBits(getNBit(err,63),1);
		out.writeBits(err, nBits);
	}

	private int bitLeadingZeroesIgnoringSign(long b) {
//		return 63;
	    for(int i = 62; i >= 0; i--) {
	    	if(getNBit(b, i) == 1) {
	    		return i + 1;
	    	}
	    }
		return 0;
    }
	
	private long getNBit(long value, int n) {
		return (value >> n) & 1;
	}
}
