package com.alibaba.lindorm.contest.compress.intcodec.pfor;

import com.alibaba.lindorm.contest.compress.intcodec.common.AbstractIntRangeCodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NewPFDCodes extends AbstractIntRangeCodes {

	static final double  exceptionThresholdRate = 0.1;

	static final double  exceptionRate = 0.05;

	private static final int limitDataNum = 128;

	private S9 s9 = this.new S9();

	private static int[] basicMask = new int[]{
		0,
		1,
		( 1 << 2 )  -1,
		( 1 << 3 )  -1,
		( 1 << 4 )  -1,
		( 1 << 5 )  -1,
		( 1 << 6 )  -1,
		( 1 << 7 )  -1,
		( 1 << 8 )  -1,
		( 1 << 9 )  -1,
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
			int b				=	params[0];	// numFrameBits
			int exceptionNum	=	params[1];

			int[] exceptionList	= new int[exceptionNum];

			boolean lastFlag = false;
			if(term == bufferBlock-1)
				lastFlag = true;

			int[] frameData	= compress( length, b, offset, numbers, exceptionList, lastFlag );

			frames[term] = frameData;

		}
		return toBitFrame( dataNum, frames );

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
	  * @param bitFrame
	  * @param offset	// offset for dataNum
	  * @param exceptionCode
	  * @param numbers
	  * @param exception
	  * @param lastFlag
	  * @return
	  */
	private int[] compress( int length, int bitFrame, int offset, int[] numbers, int[] exception, boolean lastFlag ){

		int pre		=  0;
		int max		=  1 << bitFrame;
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

		if( exception.length == 0 )
			// firstExceptionPos = 0 represent no exception
			return transformToFrame( bitFrame, 0, code, exception, null, lastFlag );

		// loop2: create offset and upper-bit value list for patch .
		int[] exceptionOffset	=	new int[ exception.length - 1 ];

		int cur = miss[ 0 ];
		exception[0] = code[cur] >> bitFrame ;
		code[cur] = code[cur] & basicMask[bitFrame];
		pre	= cur ;

		for( int i = 1 ; i < j ; i++ ){
			cur = miss[ i ];
			exception[i] = code[cur] >> bitFrame;
			code[cur] = code[cur] & basicMask[bitFrame];
			exceptionOffset[i-1] = cur - pre - 1;
			pre	= cur ;
		}

		int firstExceptionPos = miss[0] + 1;
		//System.out.println("firstExceptionPos : "+firstExceptionPos);
		return transformToFrame( bitFrame, firstExceptionPos, code, exception, exceptionOffset, lastFlag );

	}


	private static int headerSize = 1;



	/**
	 * exception and normal value is compressed to bit value.
	 *
	 * @param b
	 * @param firstExceptionPos : miss[0]+1 or 0
	 * @param code
	 * @param exception
	 * @return
	 */
	private int[] transformToFrame( int b, int firstExceptionPos, int[] code, int[] exception, int[] exceptionOffset, boolean lastFlag ){

		int exceptionIntOffset = headerSize + ( code.length * b + 31 ) / 32;

		if( firstExceptionPos == 0 ){

			int[] frame	= new int[exceptionIntOffset];
			frame[0] = makeHeader( b, firstExceptionPos, code, 0, 0, lastFlag );
			for( int i = 0 ; i < code.length ; i++ ){
				int val = code[i];
				encodeCompressedValue( i, val, b, frame );	// normal encoding
			}
			return frame;

		}


		//make exception region
		int exceptionOffsetNum = exceptionOffset.length;
		int exceptionNum = exception.length;

		int[] exceptionDatum = new int[ exceptionOffsetNum + exceptionNum ];
		for( int i = 0 ; i < exceptionNum ; i++ )
			exceptionDatum[ 2 * i ] = exception[i];
		for( int i = 0 ; i < exceptionOffsetNum ; i++ )
			exceptionDatum[ 2 * i + 1 ] = exceptionOffset[i];

		int[] compressedExceptionDatum = s9.encodeWithoutDataNumHeader( exceptionDatum );

		int exceptionRange = compressedExceptionDatum.length ;
		int intDataSize	= exceptionIntOffset + compressedExceptionDatum.length ;

		int[] frame	= new int[intDataSize];


		// 1: make header
		frame[0] = makeHeader( b, firstExceptionPos, code, exceptionNum, exceptionRange, lastFlag );

		// 2: make encoded value
		for( int i = 0 ; i < code.length ; i++ ){
			int val = code[i];
			encodeCompressedValue( i, val, b, frame );	// normal encoding
		}

		// 3: make exception value
		encodeExceptionValues( exceptionIntOffset, compressedExceptionDatum, frame );
		return frame;

	}



	/**
	 * encode exception values
	 *
	 * @param exceptionIntOffset : ( header + compressed code ) int-length
	 * @param compressedExceptionOffsets
	 * @param compressedExceptionValues
	 * @param frame
	 */
	private void encodeExceptionValues( int exceptionIntOffset, int[] compressedExceptionDatum, int[] frame ){

		int offset = exceptionIntOffset;
		for( int i = 0 ; i < compressedExceptionDatum.length ; i++ )
			frame[offset++] = compressedExceptionDatum[i];

	}



	/**
	 * encode normal value
	 *
	 * @param i
	 * @param val
	 * @param b
	 * @param frame
	 */
	private void encodeCompressedValue( int i, int val, int b, int[] frame ){

		int _val = val;
		int totalBitCount = b * i;
		int intPos	 = totalBitCount >>> 5;
		int firstBit = totalBitCount % 32;
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
	 *
	 * 7bit : dataNum - 1
	 * 8bit : first exceptionPos
	 * 5bit : numFramebit -1
	 * 11bit : exception byte range
	 * 1bit : has next frame or not
	 *
	 * @param b
	 * @param exceptionCode
	 * @param firstExceptionPos
	 * @param code
	 * @param exception
	 * @return
	 */
	private int makeHeader( int b, int firstExceptionPos, int[] code, int exceptionNum,
							int exceptionIntRange, boolean lastFlag ){

		int dataNum   = code.length -1 ;
		int lastOrNot = lastFlag ? 1 : 0;
        return dataNum << 25
        		| firstExceptionPos << 17
        		| ( b - 1 ) << 12
        		| exceptionIntRange << 1
        		| lastOrNot;

	}


	/**
	 * calculate optimized bit number of frame
	 * it is estimated by prediction of 10% exception
	 *
	 * @param numbers
	 * @param offset
	 * @param length : data length of this "For"
	 * @return 2 value int
	 * 			( bitFrame, exceptionNum )
	 */
	private int[] optimizedCompressBits( int[] numbers, int offset, int length ){

		int[] copy = new int[length];
		System.arraycopy(numbers, offset, copy , 0, length);
		Arrays.sort(copy);

		int maxValue	=	copy[ length - 1 ];
		if ( maxValue <= 1 ) return new int[]{ 1, 0 }; // bitFrame, exceptionNum, exceptionCode :
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
					return rebuild( copy, bestFrameBits,  length - i - 1 );
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
		return rebuild( copy, bestFrameBits,  bestExceptions );
	}



	private int[] rebuild( int[] copy, int bestFrameBits, int bestExceptions ){

		if( bestExceptions <= limitDataNum * exceptionThresholdRate  )
			return new int[]{bestFrameBits, bestExceptions};

		int length = copy.length;
		int maxValue	=	copy[ length - 1 ];
		if ( maxValue <= 1 ) return new int[]{ 1, 0 }; // bitFrame, exceptionNum, exceptionCode :

		int searchPos	=	(int) Math.floor( length * ( 1- exceptionRate ) );
		searchPos = searchPos == 0 ? 1 : searchPos;

		int currentVal	=	copy[ searchPos - 1 ];

		int i   = 1;
		int max = 0 ;
		for( ; i < 32 ; i++ ){
			max = basicMask[i];
			if( currentVal <= max )
				break;
		}
		int candidateBit = i;

		// search exception num
		for( int j = 0 ; j < length ; j++ ){
			if( max < copy[j] )
				return new int[]{ candidateBit, length - j };
		}

		return new int[]{ candidateBit, 0 };

	}


	@Override
	public int[] decode ( int[] encodedValue, boolean useGapList  ){
		int totalDataNum = encodedValue[0];
		return  fastDecodeFrame( 1, encodedValue , 0, new int[totalDataNum], useGapList );
	}


	private int[] fastDecodeFrame( int headerPos, int[] encodedValue, int decodeOffset, int[] decode, boolean useGapList ){


		/****************************************************************
		 * decode header value
		 * header component is as follow
		 *
		 * 7bit : dataNum - 1
		 * 8bit : first exceptionPos
		 * 5bit : numFramebit -1
		 * 11bit : exception byte range
		 * 1bit : has next frame or not
		 *
		 *****************************************************************/

		int headerValue = encodedValue[headerPos];

		int dataNum 			= 	( headerValue >>> 25 ) + 1 ;
		int firstExceptionPos   =	( headerValue << 7 ) >>> 24 ; 			// miss[0] + 1 or 0
		int numFrameBit			=	( ( headerValue << 15) >>> 27 ) + 1 ;		// 1 < numFramebit < 32
		int exceptionIntRange	=	( headerValue << 20 ) >>> 21 ;
		int lastFlag			=	( headerValue << 31 ) >>> 31 ;

		/***************************************************************/

		// first loop
		int encodeOffset = headerPos + headerSize ;

		int intOffsetForExceptionRange;
		switch( numFrameBit ){
			case 1  : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor1Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 2  : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor2Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 3  : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor3Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 4  : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor4Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 5  : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor5Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 6  : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor6Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 7  : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor7Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 8  : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor8Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 9  : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor9Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 10 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor10Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 11 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor11Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 12 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor12Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 13 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor13Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 14 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor14Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 15 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor15Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 16 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor16Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 17 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor17Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 18 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor18Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 19 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor19Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 20 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor20Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 21 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor21Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 22 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor22Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 23 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor23Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 24 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor24Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 25 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor25Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 26 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor26Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 27 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor27Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 28 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor28Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 29 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor29Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 30 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor30Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 31 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor31Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 32 : intOffsetForExceptionRange = PForDecompress.fastDeCompressFor32Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			default : throw new RuntimeException("numFramBit is too high ! " + numFrameBit);
		}



		//exception loop
		if( firstExceptionPos != 0 )
			s9.decode( encodedValue, intOffsetForExceptionRange, exceptionIntRange, decode,  firstExceptionPos - 1 );


		if( lastFlag == 0 ){
			int nextFrameIntPos = intOffsetForExceptionRange + exceptionIntRange;
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


	class S9 {

		private int bitLength[]	=	{ 1, 2, 3, 4, 5, 7, 9, 14, 28 };

		private int codeNum[]	=	{ 28, 14, 9, 7, 5, 4, 3, 2, 1 };


		public int[] encodeWithoutDataNumHeader( int numbers[] ){

			List<Integer> resultList = new ArrayList<Integer>();

			int currentPos = 0;
			while( currentPos < numbers.length ){

				for( int selector = 0 ; selector < 9 ; selector++ ){

					int res = 0;
					int compressedNum = codeNum[selector];
					if( numbers.length <= currentPos + compressedNum -1 )
						continue;
					int b = bitLength[selector];
					int max = 1 << b ;
					int i = 0;
					for( ; i < compressedNum ; i++ )
						if( max <= numbers[currentPos + i] )
							break;
						else
							res = ( res << b ) + numbers[currentPos + i];

					if( i == compressedNum ) {
						res |= selector << 28;
						resultList.add(res);
						currentPos += compressedNum;
						break;
					}

				}

			}

			int resultNum = resultList.size();
			int[] resultArray = new int[ resultNum ];
			for( int i = 0 ; i < resultNum ; i++ )
				resultArray[i] = resultList.get(i);
			return resultArray;

		}



		public void decode( int encodedValue[], int offset, int length, int[] decode, int firstExceptionPos ){

			int correntPos = firstExceptionPos;
			int head = 0;
			for( int i = 0 ; i < length ; i++ ){

				int val = encodedValue[ offset + i ] ;
				int header = ( val >>> 28 ) + head;

				switch( header ){

					case 0 : { //code num : 28, bitwidth : 1
						decode[ correntPos ]  = ( val << 4  ) >>> 31 ;
						correntPos += ( ( val << 5  ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 6  ) >>> 31 ;
						correntPos += ( ( val << 7  ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 8  ) >>> 31 ;
						correntPos += ( ( val << 9  ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 10 ) >>> 31 ;
						correntPos += ( ( val << 11 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 12 ) >>> 31 ;
						correntPos += ( ( val << 13 ) >>> 31 ) + 1 ; //10
						decode[ correntPos ]  = ( val << 14 ) >>> 31 ;
						correntPos += ( ( val << 15 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 16 ) >>> 31 ;
						correntPos += ( ( val << 17 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 18 ) >>> 31 ;
						correntPos += ( ( val << 19 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 20 ) >>> 31 ;
						correntPos += ( ( val << 21 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 22 ) >>> 31 ;
						correntPos += ( ( val << 23 ) >>> 31 ) + 1 ; //20
						decode[ correntPos ]  = ( val << 24 ) >>> 31 ;
						correntPos += ( ( val << 25 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 26 ) >>> 31 ;
						correntPos += ( ( val << 27 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 28 ) >>> 31 ;
						correntPos += ( ( val << 29 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 30 ) >>> 31 ;
						correntPos += ( ( val << 31 ) >>> 31 ) + 1 ;
						head = 0;
						break;
					}
					case 1 : { //code num : 14, bitwidth : 2
						decode[ correntPos ]  = ( val << 4  ) >>> 30 ;
						correntPos += ( ( val << 6  ) >>> 30 ) + 1 ;
						decode[ correntPos ]  = ( val << 8  ) >>> 30 ;
						correntPos += ( ( val << 10 ) >>> 30 ) + 1 ;
						decode[ correntPos ]  = ( val << 12 ) >>> 30 ;
						correntPos += ( ( val << 14 ) >>> 30 ) + 1 ;
						decode[ correntPos ]  = ( val << 16 ) >>> 30 ;
						correntPos += ( ( val << 18 ) >>> 30 ) + 1 ;
						decode[ correntPos ]  = ( val << 20 ) >>> 30 ;
						correntPos += ( ( val << 22 ) >>> 30 ) + 1 ; //10
						decode[ correntPos ]  = ( val << 24 ) >>> 30 ;
						correntPos += ( ( val << 26 ) >>> 30 ) + 1 ;
						decode[ correntPos ]  = ( val << 28 ) >>> 30 ;
						correntPos += ( ( val << 30 ) >>> 30 ) + 1 ;
						head = 0;
						break;
					}
					case 2 : { //code num : 9, bitwidth : 3
						decode[ correntPos ] = ( val << 5  ) >>> 29 ;
						correntPos += ( ( val << 8  ) >>> 29 ) + 1 ;
						decode[ correntPos ] = ( val << 11 ) >>> 29 ;
						correntPos += ( ( val << 14 ) >>> 29 ) + 1 ;
						decode[ correntPos ] = ( val << 17 ) >>> 29 ;
						correntPos += ( ( val << 20 ) >>> 29 ) + 1 ;
						decode[ correntPos ] = ( val << 23 ) >>> 29 ;
						correntPos += ( ( val << 26 ) >>> 29 ) + 1 ;
						decode[ correntPos ] = ( val << 29 ) >>> 29 ;
						head = 16;
						break;
					}
					case 3 : { //code num : 7, bitwidth : 4
						decode[ correntPos ] = ( val << 4  ) >>> 28 ;
						correntPos += ( ( val << 8  ) >>> 28 ) + 1 ;
						decode[ correntPos ] = ( val << 12 ) >>> 28 ;
						correntPos += ( ( val << 16 ) >>> 28 ) + 1 ;
						decode[ correntPos ] = ( val << 20 ) >>> 28 ;
						correntPos += ( ( val << 24 ) >>> 28 ) + 1 ;
						decode[ correntPos ] = ( val << 28 ) >>> 28 ;
						head = 16;
						break;
					}
					case 4 : { //code num : 5, bitwidth : 5
						decode[ correntPos ] = ( val << 7  ) >>> 27 ;
						correntPos += ( ( val << 12 ) >>> 27 ) + 1 ;
						decode[ correntPos ] = ( val << 17 ) >>> 27 ;
						correntPos += ( ( val << 22 ) >>> 27 ) + 1 ;
						decode[ correntPos ] = ( val << 27 ) >>> 27 ;
						head = 16;
						break;
					}
					case 5 : { //code num : 4, bitwidth : 7
						decode[ correntPos ] = ( val << 4  ) >>> 25 ;
						correntPos += ( ( val << 11 ) >>> 25 ) + 1 ;
						decode[ correntPos ] = ( val << 18 ) >>> 25 ;
						correntPos += ( ( val << 25 ) >>> 25 ) + 1 ;
						head = 0;
						break;
					}
					case 6 : { //code num : 3, bitwidth : 9
						decode[ correntPos ] = ( val << 5  ) >>> 23 ;
						correntPos += ( ( val << 14 ) >>> 23 ) + 1 ;
						decode[ correntPos ] = ( val << 23 ) >>> 23 ;
						head = 16;
						break;
					}
					case 7 : { //code num : 2, bitwidth : 14
						decode[ correntPos ] = ( val << 4  ) >>> 18 ;
						correntPos += ( ( val << 18 ) >>> 18 ) +1 ;
						head = 0;
						break;
					}
					case 8 : { //code num : 1, bitwidth : 28
						decode[ correntPos ] = ( val << 4 ) >>> 4;
						head = 16;
						break;
					}

					case 16 : { //code num : 28, bitwidth : 1
						correntPos += ( val << 4  ) >>> 31 ;
						decode[ correntPos ] = ( val << 5  ) >>> 31 ;
						correntPos += ( val << 6  ) >>> 31 ;
						decode[ correntPos ] = ( val << 7  ) >>> 31 ;
						correntPos += ( val << 8  ) >>> 31 ;
						decode[ correntPos ] = ( val << 9  ) >>> 31 ;
						correntPos += ( val << 10 ) >>> 31 ;
						decode[ correntPos ] = ( val << 11 ) >>> 31 ;
						correntPos += ( val << 12 ) >>> 31 ;
						decode[ correntPos ] = ( val << 13 ) >>> 31 ; //10
						correntPos += ( val << 14 ) >>> 31 ;
						decode[ correntPos ] = ( val << 15 ) >>> 31 ;
						correntPos += ( val << 16 ) >>> 31 ;
						decode[ correntPos ] = ( val << 17 ) >>> 31 ;
						correntPos += ( val << 18 ) >>> 31 ;
						decode[ correntPos ] = ( val << 19 ) >>> 31 ;
						correntPos += ( val << 20 ) >>> 31 ;
						decode[ correntPos ] = ( val << 21 ) >>> 31 ;
						correntPos += ( val << 22 ) >>> 31 ;
						decode[ correntPos ] = ( val << 23 ) >>> 31 ; //20
						correntPos += ( val << 24 ) >>> 31 ;
						decode[ correntPos ] = ( val << 25 ) >>> 31 ;
						correntPos += ( val << 26 ) >>> 31 ;
						decode[ correntPos ] = ( val << 27 ) >>> 31 ;
						correntPos += ( val << 28 ) >>> 31 ;
						decode[ correntPos ] = ( val << 29 ) >>> 31 ;
						correntPos += ( val << 30 ) >>> 31 ;
						decode[ correntPos ] = ( val << 31 ) >>> 31 ;
						head = 16;
						break;
					}
					case 17 : { //code num : 14, bitwidth : 2
						correntPos += ( val << 4  ) >>> 30 ;
						decode[ correntPos ] = ( val << 6  ) >>> 30 ;
						correntPos += ( val << 8  ) >>> 30 ;
						decode[ correntPos ] = ( val << 10 ) >>> 30 ;
						correntPos += ( val << 12 ) >>> 30 ;
						decode[ correntPos ] = ( val << 14 ) >>> 30 ;
						correntPos += ( val << 16 ) >>> 30 ;
						decode[ correntPos ] = ( val << 18 ) >>> 30 ;
						correntPos += ( val << 20 ) >>> 30 ;
						decode[ correntPos ] = ( val << 22 ) >>> 30 ; //10
						correntPos += ( val << 24 ) >>> 30 ;
						decode[ correntPos ] = ( val << 26 ) >>> 30 ;
						correntPos += ( val << 28 ) >>> 30 ;
						decode[ correntPos ] = ( val << 30 ) >>> 30 ;
						head = 16;
						break;
					}
					case 18 : { //code num : 9, bitwidth : 3
						correntPos += ( val << 5  ) >>> 29 ;
						decode[ correntPos ] = ( val << 8  ) >>> 29 ;
						correntPos += ( val << 11 ) >>> 29 ;
						decode[ correntPos ] = ( val << 14 ) >>> 29 ;
						correntPos += ( val << 17 ) >>> 29 ;
						decode[ correntPos ] = ( val << 20 ) >>> 29 ;
						correntPos += ( val << 23 ) >>> 29 ;
						decode[ correntPos ] = ( val << 26 ) >>> 29 ;
						correntPos += ( val << 29 ) >>> 29 ;
						head = 0;
						break;
					}
					case 19 : { //code num : 7, bitwidth : 4
						correntPos += ( val << 4  ) >>> 28 ;
						decode[ correntPos ] = ( val << 8  ) >>> 28 ;
						correntPos += ( val << 12 ) >>> 28 ;
						decode[ correntPos ] = ( val << 16 ) >>> 28 ;
						correntPos += ( val << 20 ) >>> 28 ;
						decode[ correntPos ] = ( val << 24 ) >>> 28 ;
						correntPos += ( val << 28 ) >>> 28 ;
						head = 0;
						break;
					}
					case 20 : { //code num : 5, bitwidth : 5
						correntPos += ( val << 7  ) >>> 27 ;
						decode[ correntPos ] = ( val << 12 ) >>> 27 ;
						correntPos += ( val << 17 ) >>> 27 ;
						decode[ correntPos ] = ( val << 22 ) >>> 27 ;
						correntPos += ( val << 27 ) >>> 27 ;
						head = 0;
						break;
					}
					case 21 : { //code num : 4, bitwidth : 7
						correntPos += ( val << 4  ) >>> 25 ;
						decode[ correntPos ] = ( val << 11 ) >>> 25 ;
						correntPos += ( val << 18 ) >>> 25 ;
						decode[ correntPos ] = ( val << 25 ) >>> 25 ;
						head = 16;
						break;
					}
					case 22 : { //code num : 3, bitwidth : 9
						correntPos += ( val << 5  ) >>> 23 ;
						decode[ correntPos ] = ( val << 14 ) >>> 23 ;
						correntPos += ( val << 23 ) >>> 23 ;
						head = 0;
						break;
					}
					case 23 : { //code num : 2, bitwidth : 14
						correntPos += ( val << 4  ) >>> 18 ;
						decode[ correntPos ] = ( val << 18 ) >>> 18 ;
						head = 16;
						break;
					}
					case 24 : { //code num : 1, bitwidth : 28
						correntPos += ( val << 4 ) >>> 4;
						head = 0;
						break;
					}
					default : throw new IllegalArgumentException();

				}
			}
		}

	}

}