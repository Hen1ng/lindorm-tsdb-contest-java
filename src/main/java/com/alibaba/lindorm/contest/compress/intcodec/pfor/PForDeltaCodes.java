package com.alibaba.lindorm.contest.compress.intcodec.pfor;

import com.alibaba.lindorm.contest.compress.intcodec.common.AbstractIntRangeCodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PForDeltaCodes extends AbstractIntRangeCodes {

	private static final int limitDataNum = 128;


	private static int[] basicMask = new int[]{
		0,
		1,
		( 1 << 2 ) -1,
		( 1 << 3 ) -1,
		( 1 << 4 ) -1,
		( 1 << 5 ) -1,
		( 1 << 6 ) -1,
		( 1 << 7 ) -1,
		( 1 << 8 ) -1,
		( 1 << 9 ) -1,
		( 1 << 10 ) -1,
		( 1 << 11 ) -1,
		( 1 << 12 ) -1,
		( 1 << 13 ) -1,
		( 1 << 14 ) -1,
		( 1 << 15 ) -1,
		( 1 << 16 ) -1,
		( 1 << 17 ) -1,
		( 1 << 18 ) -1,
		( 1 << 19 ) -1,
		( 1 << 20 ) -1,
		( 1 << 21 ) -1,
		( 1 << 22 ) -1,
		( 1 << 23 ) -1,
		( 1 << 24 ) -1,
		( 1 << 25 ) -1,
		( 1 << 26 ) -1,
		( 1 << 27 ) -1,
		( 1 << 28 ) -1,
		( 1 << 29 ) -1,
		( 1 << 30 ) -1,
		( 1 << 31 ) -1,
		( 1 << 32 ) -1

	};


	/**
	 * encode Integer Array to Compressed IntegerArray
	 *
	 * @param numbers
	 */
	@Override
	protected int[] innerEncode( int[] numbers ){


		int dataNum		=	numbers.length;
		int bufferBlock =	( dataNum + limitDataNum - 1 ) / limitDataNum;


		Object[] frames = new Object[ bufferBlock ];
		for( int offset = 0, term = 0 ; term < bufferBlock ; offset += limitDataNum , term++ ){

			int diff	=	dataNum - offset;
			int length	=	diff <= limitDataNum ? diff : limitDataNum;

			int[] params = optimizedCompressBits( numbers, offset, length );
			int b				=	params[0]; // numFrameBits - 1
			int exceptionNum	=	params[1];
			int exceptionCode	=	params[2];


			int[] exceptionList	= new int[exceptionNum];

			boolean lastFlag = false;
			if(term == bufferBlock-1)
				lastFlag = true;

			int[] frameData	= compress( length, b, offset, exceptionCode, numbers, exceptionList, lastFlag );

			frames[term] = frameData;

		}
		return toBitFrame( dataNum, frames);

	}


	/**
	 * combinate frames
	 *
	 * @param frames
	 * @return
	 */
	private int[] toBitFrame( int dataNum, Object[] frames ){
		int[] prev = new int[]{dataNum};
		for( int i = 0 ; i < frames.length ; i++ ){
			int[] frame = ( (int[])frames[i] );
			int[] dest	= new int[ prev.length + frame.length];
			System.arraycopy(prev, 0, dest, 0, prev.length);
			System.arraycopy(frame, 0, dest, prev.length, frame.length);
			prev = dest;
		}
		return prev;
	}


	 /**
	  * compress integer numbers into frame
	  *
	  * @param length
	  * @param b
	  * @param offset
	  * @param exceptionCode
	  * @param numbers
	  * @param exception
	  * @return
	  */
	private int[] compress( int length, int b, int offset, int exceptionCode, int[] numbers, int[] exception, boolean lastFlag ){

		int pre		=  0;
		int max		=  1 << b;
		int[] code	=  new int[length];
		int[] miss	=  new int[length];

		// loop1: find exception
		int j = 0;
		for( int i = 0 ; i < length ; i++ ){
			int val = numbers[ i + offset ];
			code[i] = val;
			miss[j] = i;
			if( max <= val ) j++;
		}

		// loop2: create patchlist
		if( 0 < exception.length ){

			int cur = miss[ 0 ];
			exception[0] = code[cur];
			pre = cur;

			for( int i = 1 ; i < j ; i++ ){
				cur = miss[ i ];
				exception[i] = code[cur];
				code[pre] = cur - pre - 1;
				pre	= cur;
			}

			code[ miss[j-1] ] = 0;


			// check whther gap exception is over Max of numFramBit or not.
			for( int i = 0 ; i < j-1 ; i++ ){

				if( max  <= code[ miss[i] ] ){
					int nextMax = 1 <<  b + 1;
					int exceptionNum = overMaxNum( exception, nextMax );
					return compress ( length, b + 1, offset, exceptionCode, numbers, new int[exceptionNum], lastFlag );
				}

			}

		}
		int firstExceptionPos = j > 0 ? miss[0] + 1 : 0;

		// firstExceptionPos = 0 represent no exception
		return transformToBit( b, exceptionCode, firstExceptionPos, code, exception, lastFlag );

	}


	public int overMaxNum( int[] exception, int max){

		int result = 0;
		for( int i = 0 ; i < exception.length ; i++ )
			if( max <= exception[i] )
				result++;
		return result;

	}


	public int[] overMaxException( int[] exception, int max ){

		List<Integer> list = new ArrayList<Integer>();
		for( int i = 0 ; i < exception.length ; i++ )
			if( max <= exception[i] )
				list.add(exception[i]);

		int[] newException = new int[ list.size() ];

		int i = 0;
		for( int exceptionVal : list)
			newException[i++] = exceptionVal;

		return newException;

	}


	private static int headerSize = 1;


	/**
	 * exception and normal value is compressed to bit value.
	 *
	 * @param b
	 * @param exceptionCode  : 0 byte , 1 short , 2 int
	 * @param firstExceptionPos : miss[0]+1 or 0
	 * @param code
	 * @param exception
	 * @return
	 */
	private int[] transformToBit( int b, int exceptionCode, int firstExceptionPos, int[] code, int[] exception, boolean lastFlag ){


		int intOffset	= headerSize + ( code.length * b + 31 ) / 32;
		int intDataSize	= intOffset + ( ( 1 << exceptionCode ) * exception.length + 3) / 4;

		int[] frame	= new int[intDataSize];

		// 1: make header
		frame[0] = makeHeader( b, exceptionCode, firstExceptionPos, code, exception, lastFlag);

		// 2: make encoded value
		for( int i = 0 ; i < code.length ; i++ ){
			int val = code[i];
			encodeCompressedValue( i, val, b, frame);	// normal encoding
		}

		// 3: make exception value
		encodeExceptionValues( intOffset, exceptionCode, exception, frame );
		return frame;

	}


	/**
	 * encode exception values
	 * @param intOffset : ( header + compressed code ) int-length
	 * @param exceptionCode : 0 byte , 1 short , 2 int
	 * @param exception
	 * @param frame
	 */
	private void encodeExceptionValues( int intOffset, int exceptionCode, int[] exception, int[] frame ){

		int exceptionSize = exception.length;
		if ( exceptionSize == 0 )
			return;

		int _intOffset = intOffset;

		switch ( exceptionCode ) {
			case 0: { // 1 byte exceptions
				int i = 0;
				for(; i < exceptionSize - 4 ; i += 4 )
					frame[_intOffset++] = exception[i]	   		 |
										  exception[i+1]  << 8	 |
										  exception[i+2]  << 16  |
										  exception[i+3]  << 24;
				int mask = basicMask[8];
				for( int j = 0 ; i+j < exceptionSize ; j++ ){
					int bitPos = 8 * j;
		            frame[_intOffset] = frame[_intOffset]
		                                      & ~( mask << bitPos )
		                                      | exception[i+j]  << bitPos;
				}
				break;
			}
			case 1: { // 2 byte exceptions
				int i = 0;
				for(; i < exceptionSize - 2 ; i += 2 )
					frame[_intOffset++] = exception[i]	   |
										  exception[i+1] << 16;
				int mask = basicMask[16];
				for( int j = 0 ; i+j < exceptionSize ; j++ ){
					int bitPos = 16 * j;
		            frame[_intOffset] = frame[_intOffset]
		                                      & ~( mask << bitPos )
		                                      | exception[i+j]  << bitPos;
				}
				break;
			}
			case 2 : { // 4 byte exceptions
				for( int i = 0 ; i < exceptionSize  ; i++ )
					frame[_intOffset++] = exception[i];
				break;
			}
			default :{
				throw new IllegalArgumentException("Illeagal exceptionCode");
			}
		}
	}


	/**
	 * encode normal value
	 *
	 * @param i
	 * @param val
	 * @param b
	 * @param frame
	 */
	public void encodeCompressedValue( int i, int val, int b, int[] frame ){

		int _val = val;
		int totalBitCount = b * i;
		int intPos	 = totalBitCount >>> 5;
		int firstBit = totalBitCount  % 32;
		int endBit = firstBit + b;

		int baseMask = basicMask[b];
		int mask 	 = 0;

		mask = ~( baseMask << firstBit );
		_val = val << firstBit;

		frame[ intPos + headerSize ] = frame[ intPos + headerSize ]
		                                      & mask
		                                      | _val;

		// over bit-width of integer
		if( 32 < endBit ){
			int shiftBit = b - ( endBit - 32 );
			mask = ~( baseMask >>> shiftBit );
			_val = val >>> shiftBit;
			frame[ intPos + headerSize + 1] = frame[ intPos + headerSize + 1]
			                                         & mask
			                                         | _val;
		}


	}


	/**
	 * Header is consist of 1 byte and the construction is as follow
	 * 8bit : dataNum ( max : 255 )
	 * 7bit	: exceptionNum
	 * 9bit : first exceptionPos
	 * 5bit : numFramebit -1
	 * 2bit : exception code : 00 byte, 01 short, 10 int, 11 unuse
	 * 1bit : has next frame or not
	 *
	 * @param b
	 * @param exceptionCode
	 * @param firstExceptionPos
	 * @param code
	 * @param exception
	 * @return
	 */
	private int makeHeader( int b, int exceptionCode, int firstExceptionPos, int[] code, int[] exception , boolean lastFlag ){

		int dataNum = code.length;
		int exceptionNum = exception.length;
		int lastOrNot = 0;
		if( lastFlag )
			lastOrNot = 1;

        return ( dataNum << 24 | exceptionNum << 17 )
        						|
        		( firstExceptionPos << 8 | ( b - 1 ) << 3 )
        						|
        		( exceptionCode << 1 | lastOrNot );

	}


	/**
	 * calculate optimized bit number of frame
	 *
	 * @param numbers
	 * @param offset
	 * @param length : data length of this "For"
	 * @return 3 value int
	 * 			( bitFrame, exceptionNum, exceptionCode )
	 */
	public int[] optimizedCompressBits( int[] numbers, int offset, int length ){

		int[] copy = new int[length];
		System.arraycopy(numbers, offset, copy , 0, length);
		Arrays.sort(copy);

		int maxValue	=	copy[ length - 1 ];
		if ( maxValue <= 1 ) return new int[]{ 1, 0, 0 }; // bitFrame, exceptionNum, exceptionCode :
		int exceptionCode = ( maxValue < ( 1 << 8 ) ) ? 0 : (maxValue < (1 << 16 )) ? 1 : 2;
		int bytesPerException = 1 << exceptionCode;
		int frameBits	=	1;
		int bytesForFrame = (length * frameBits + 7 ) / 8; // cut up byte

		// initially assume all inputs are exceptions.
		int totalBytes		=	bytesForFrame + length * bytesPerException; // excluding the header.
		int bestBytes		=	totalBytes;
		int bestFrameBits	=	frameBits;
		int bestExceptions	=	length;

		for (int i = 0; i < length; i++) {
			// determine frameBits so that copy[i] is no more exception
			while ( copy[i] >= (1 << frameBits) ) {
				if ( frameBits == 30 ) { // no point to increase further.
					return new int[]{ bestFrameBits, length - i - 1, exceptionCode };
				}
				++frameBits;
				// increase bytesForFrame and totalBytes to correspond to frameBits
				int newBytesForFrame = (length * frameBits + 7 ) / 8;
				totalBytes += newBytesForFrame - bytesForFrame;
				bytesForFrame = newBytesForFrame;
			}
			totalBytes -= bytesPerException; // no more need to store copy[i] as exception
			if ( totalBytes <= bestBytes ) { // <= : prefer fewer exceptions at higher number of frame bits.
				bestBytes		=	totalBytes;
				bestFrameBits	=	frameBits;
				bestExceptions	=	length - i - 1;
			}

		}
		return new int[]{ bestFrameBits,  bestExceptions, exceptionCode };

	}


	@Override
	public int[] decode ( int[] encodedValue, boolean useGapList  ){
		int totalDataNum = encodedValue[0];
		return  fastDecodeFrame( 1, encodedValue , 0, new int[totalDataNum], useGapList );
	}


	private static int[] fastDecodeFrame( int headerPos, int[] encodedValue, int decodeOffset, int[] decode, boolean useGapList ){


		/****************************************************************
		 * decode header value
		 * header component is as follow
		 *
		 * 8bit : dataNum
		 * 7bit	: exceptionNum
		 * 9bit : first exceptionPos
		 * 5bit : numFramebit -1
		 * 2bit : exception code : 00 byte, 01 short, 10 int 11 unuse
		 * 1bit : has next frame or not
		 *
		 *****************************************************************/

		int headerValue = encodedValue[headerPos];

		int dataNum = headerValue >>> 24;

		int exceptionNum        =	( headerValue <<  8 ) >>> 25;
		int firstExceptionPos   =	( headerValue << 15 ) >>> 23; 			// miss[0] + 1 or 0
		int numFrameBit			=	( (headerValue << 24) >>> 27 ) + 1;		// 1 < numFramebit < 32

		int exceptionCode		=	( headerValue << 29 ) >>> 30;
		int bitShiftPerException	=	exceptionCode + 3  ;				// 2^(bitShiftPerException) = bit per exception

		int lastFlag			=	( headerValue << 31 ) >>> 31;

		/***************************************************************/

		// first loop
		int encodeOffset = headerPos + headerSize ;

		int IntOffsetForException;
		switch(numFrameBit){
			case 1  : IntOffsetForException = PForDecompress.fastDeCompressFor1Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 2  : IntOffsetForException = PForDecompress.fastDeCompressFor2Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 3  : IntOffsetForException = PForDecompress.fastDeCompressFor3Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 4  : IntOffsetForException = PForDecompress.fastDeCompressFor4Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 5  : IntOffsetForException = PForDecompress.fastDeCompressFor5Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 6  : IntOffsetForException = PForDecompress.fastDeCompressFor6Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 7  : IntOffsetForException = PForDecompress.fastDeCompressFor7Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 8  : IntOffsetForException = PForDecompress.fastDeCompressFor8Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 9  : IntOffsetForException = PForDecompress.fastDeCompressFor9Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 10 : IntOffsetForException = PForDecompress.fastDeCompressFor10Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 11 : IntOffsetForException = PForDecompress.fastDeCompressFor11Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 12 : IntOffsetForException = PForDecompress.fastDeCompressFor12Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 13 : IntOffsetForException = PForDecompress.fastDeCompressFor13Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 14 : IntOffsetForException = PForDecompress.fastDeCompressFor14Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 15 : IntOffsetForException = PForDecompress.fastDeCompressFor15Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 16 : IntOffsetForException = PForDecompress.fastDeCompressFor16Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 17 : IntOffsetForException = PForDecompress.fastDeCompressFor17Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 18 : IntOffsetForException = PForDecompress.fastDeCompressFor18Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 19 : IntOffsetForException = PForDecompress.fastDeCompressFor19Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 20 : IntOffsetForException = PForDecompress.fastDeCompressFor20Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 21 : IntOffsetForException = PForDecompress.fastDeCompressFor21Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 22 : IntOffsetForException = PForDecompress.fastDeCompressFor22Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 23 : IntOffsetForException = PForDecompress.fastDeCompressFor23Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 24 : IntOffsetForException = PForDecompress.fastDeCompressFor24Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 25 : IntOffsetForException = PForDecompress.fastDeCompressFor25Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 26 : IntOffsetForException = PForDecompress.fastDeCompressFor26Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 27 : IntOffsetForException = PForDecompress.fastDeCompressFor27Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 28 : IntOffsetForException = PForDecompress.fastDeCompressFor28Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 29 : IntOffsetForException = PForDecompress.fastDeCompressFor29Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 30 : IntOffsetForException = PForDecompress.fastDeCompressFor30Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 31 : IntOffsetForException = PForDecompress.fastDeCompressFor31Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 32 : IntOffsetForException = PForDecompress.fastDeCompressFor32Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			default : throw new RuntimeException("numFramBit is too high ! " + numFrameBit);
		}


		// exception loop
		if( firstExceptionPos != 0 ){

			int currentPos	= firstExceptionPos - 1;
			int diffToNext	= decode[ decodeOffset + currentPos ];
			int mask		= basicMask[ 1 << bitShiftPerException ];
			decode[ decodeOffset + currentPos ] = encodedValue[ IntOffsetForException ] & mask;
			int patchNum = 1;
			while( patchNum < exceptionNum ){
				int nextPos = currentPos + diffToNext + 1;
				diffToNext	= decode[decodeOffset + nextPos];
				currentPos  = nextPos;
				decode[decodeOffset + nextPos] = decodeException( patchNum, mask, bitShiftPerException, IntOffsetForException, encodedValue );
				patchNum++;
			}

		}


		if( lastFlag == 0 ){
			int nextFrameIntPos = IntOffsetForException + ( ( ( exceptionNum << bitShiftPerException )  + 31 ) >> 5 );
			decodeOffset += dataNum;
			return fastDecodeFrame( nextFrameIntPos, encodedValue, decodeOffset, decode, useGapList );
		}
		else{

			if(useGapList)
				for(int i = 1 ; i < decode.length ; i++ )
					decode[i] += decode[i-1];
			return decode;

		}

	}


	private static int decodeException( int patchNum, int mask, int bitShiftPerException, int IntOffsetForException, int[] encodedValue ){

		int bitLength	=	patchNum << bitShiftPerException;
		int intPos		=	bitLength >> 5;
		int firstBitPos	=	bitLength % 32;
		return ( encodedValue[intPos + IntOffsetForException] >> firstBitPos ) & mask;

	}

}