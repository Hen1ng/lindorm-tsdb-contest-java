package com.alibaba.lindorm.contest.compress.intcodec.variant;

import com.alibaba.lindorm.contest.compress.intcodec.common.AbstractByteRangeCodes;

import java.util.ArrayList;
import java.util.List;

public class VariantCodes extends AbstractByteRangeCodes {

	@Override
	public int[] decode( byte[] encodedValue, boolean useGapList ) {

		int dataNum =	( ( encodedValue[0] & mask8bit ) << 24  | ( encodedValue[1] & mask8bit ) << 16 )
																|
						( ( encodedValue[2] & mask8bit ) <<  8  | ( encodedValue[3] & mask8bit ) );

		int[] decode = new int[dataNum];
		int id	= 0;
		int n	= 0;
		int b = 0;
		for( int i = 4 ; i < encodedValue.length ; i++ ){

			if( encodedValue[i] < 0 ){
				n |= ( ( encodedValue[i] & mask7bit ) << b );
				b += 7;
			}
			else{
				n |= ( ( encodedValue[i] ) << b );
				decode[id++] = n;
				n = 0;
				b = 0;
			}

		}

		if(useGapList)
			for( int i=0 ; i < decode.length ; i++ )
				decode[i] += decode[i-1];

		return decode;

	}


	static int mask8bit = (1 << 8) - 1;
	static int mask7bit = (1 << 7) - 1;
	@Override
	protected byte[] innerEncode( int[] gappedNumbers ) {


		List<Byte> tmpList = new ArrayList<Byte>();
		for( int  i =0 ; i < gappedNumbers.length ; i++ ){

			int num = gappedNumbers[i];

			while( true ){
				int val = num >>> 7 ;
				if( 0 < num ){
					tmpList.add( (byte)( ( num & mask7bit ) | ( 1 << 7 ) ) );
					num = val;
				}
				else{
					tmpList.add( (byte)( num & mask7bit) );
					break;
				}
			}

		}

		int num 	= gappedNumbers.length;
		int dataNum = tmpList.size();

		byte[] resultArray = new byte[ dataNum + 4 ];

		resultArray[0] = (byte)( ( num >> 24 ) & mask8bit );
		resultArray[1] = (byte)( ( num >> 16 ) & mask8bit );
		resultArray[2] = (byte)( ( num >>  8 ) & mask8bit );
		resultArray[3] = (byte)(  num & mask8bit  );

		for( int i = 0 ; i < dataNum ; i++ )
			resultArray[ i + 4 ] = tmpList.get(i);

		return resultArray;

	}


}
