package com.alibaba.lindorm.contest.compress.intcodec.vbyte;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VariableByteCodes {


	private void innerEncode( int num, List<Byte> resultList ){

		int headNum = resultList.size();

		while(true){
			byte n = (byte)( num % 128 );
			resultList.add(headNum, n);
			if( num < 128 ) break;
			num = num >>> 7;
		}

		int lastIndex = resultList.size()-1;
		Byte val = resultList.get( lastIndex );
		val = (byte) ( val.byteValue() - 128 );
		resultList.remove(lastIndex);
		resultList.add(val);

	}


	static int mask8bit = (1 << 8) - 1;
	public byte[] encode (int[] numbers, boolean useArraySort ){


		if(useArraySort)
			Arrays.sort(numbers);

		List<Byte> resultList = new ArrayList<Byte>();
		int beforeNum = 0;
		for( int num : numbers ){
			innerEncode( num - beforeNum, resultList);
			beforeNum = num;
		}
		int listNum = resultList.size();

		byte[] resultArray = new byte[listNum+ 4];
		int num = numbers.length;

		resultArray[0] = (byte)( ( num >> 24 ) & mask8bit );
		resultArray[1] = (byte)( ( num >> 16 ) & mask8bit );
		resultArray[2] = (byte)( ( num >>  8 ) & mask8bit );
		resultArray[3] = (byte)(  num & mask8bit  );

		for( int i = 0 ; i < listNum ; i++ )
			resultArray[ i + 4 ] = resultList.get(i);

		return resultArray;

	}


	public int[] decode ( byte[] encodedValue, boolean useGapList ){

		int dataNum =	( ( encodedValue[0] & mask8bit ) << 24  | ( encodedValue[1] & mask8bit ) << 16 )
																|
						( ( encodedValue[2] & mask8bit ) <<  8  | ( encodedValue[3] & mask8bit ) );

		int[] decode = new int[dataNum];
		int id	= 0;
		int n	= 0;
		for( int i =4 ; i < encodedValue.length ; i++ ){

			if( 0 <= encodedValue[i] )
				n = ( n << 7 ) + encodedValue[i];
			else{
				n = ( n << 7 ) + ( encodedValue[i] + 128 );
				decode[id++] = n;
				n = 0;
			}

		}

		if(useGapList)
			for( int j =1 ; j < dataNum ; j++ )
				decode[j] += decode[j-1];

		return decode;

	}

}