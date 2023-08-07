package com.alibaba.lindorm.contest.compress.intcodec.simple;

import com.alibaba.lindorm.contest.compress.intcodec.common.AbstractIntRangeCodes;

import java.util.ArrayList;
import java.util.List;

public class Simple9Codes {


	private static int bitLength[]	=	{ 1, 2, 3, 4, 5, 7, 9, 14, 28 };

	private static int codeNum[]	=	{ 28, 14, 9, 7, 5, 4, 3, 2, 1 };

	public static int[] innerEncode ( int[] numbers ){

		List<Integer> resultList = new ArrayList<Integer>();

		int currentPos = 0;
		while( currentPos < numbers.length ){

			for( int selector = 0 ; selector < 9 ; selector++ ){

				int res = 0;
				int compressedNum = codeNum[selector];
				if( numbers.length <= currentPos + compressedNum -1 )
					continue;
				int b = bitLength[selector];
				int max = 1 << b;
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

		int resultNum	  = resultList.size();
		int[] resultArray = new int[ resultNum + 1 ];
		resultArray[0] = numbers.length;
		for( int i = 0 ; i < resultNum ; i++ )
			resultArray[ i+1 ] = resultList.get(i);
		return resultArray;

	}


	//@Override
	public static int[] decode( int[] encodedValue) {

		int dataNum = encodedValue[0];
		int[] resultArray = new int[dataNum];

		int currentPos = 0;
		for( int i = 1 ; i < encodedValue.length ; i++ ){

			int val = encodedValue[i];
			int header	= val >>> 28;

			switch(header){

				case 0 : { //code num : 28, bitwidth : 1
					resultArray[currentPos++] = ( val << 4  ) >>> 31 ;
					resultArray[currentPos++] = ( val << 5  ) >>> 31 ;
					resultArray[currentPos++] = ( val << 6  ) >>> 31 ;
					resultArray[currentPos++] = ( val << 7  ) >>> 31 ;
					resultArray[currentPos++] = ( val << 8  ) >>> 31 ;
					resultArray[currentPos++] = ( val << 9  ) >>> 31 ;
					resultArray[currentPos++] = ( val << 10 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 11 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 12 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 13 ) >>> 31 ; //10
					resultArray[currentPos++] = ( val << 14 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 15 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 16 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 17 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 18 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 19 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 20 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 21 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 22 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 23 ) >>> 31 ;	//20
					resultArray[currentPos++] = ( val << 24 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 25 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 26 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 27 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 28 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 29 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 30 ) >>> 31 ;
					resultArray[currentPos++] = ( val << 31 ) >>> 31 ;
					break;
				}
				case 1 : { //code num : 14, bitwidth : 2
					resultArray[currentPos++] = ( val << 4  ) >>> 30 ;
					resultArray[currentPos++] = ( val << 6  ) >>> 30 ;
					resultArray[currentPos++] = ( val << 8  ) >>> 30 ;
					resultArray[currentPos++] = ( val << 10 ) >>> 30 ;
					resultArray[currentPos++] = ( val << 12 ) >>> 30 ;
					resultArray[currentPos++] = ( val << 14 ) >>> 30 ;
					resultArray[currentPos++] = ( val << 16 ) >>> 30 ;
					resultArray[currentPos++] = ( val << 18 ) >>> 30 ;
					resultArray[currentPos++] = ( val << 20 ) >>> 30 ;
					resultArray[currentPos++] = ( val << 22 ) >>> 30 ; //10
					resultArray[currentPos++] = ( val << 24 ) >>> 30 ;
					resultArray[currentPos++] = ( val << 26 ) >>> 30 ;
					resultArray[currentPos++] = ( val << 28 ) >>> 30 ;
					resultArray[currentPos++] = ( val << 30 ) >>> 30 ;
					break;
				}
				case 2 : { //code num : 9, bitwidth : 3
					resultArray[currentPos++] = ( val << 5  ) >>> 29 ;
					resultArray[currentPos++] = ( val << 8  ) >>> 29 ;
					resultArray[currentPos++] = ( val << 11 ) >>> 29 ;
					resultArray[currentPos++] = ( val << 14 ) >>> 29 ;
					resultArray[currentPos++] = ( val << 17 ) >>> 29 ;
					resultArray[currentPos++] = ( val << 20 ) >>> 29 ;
					resultArray[currentPos++] = ( val << 23 ) >>> 29 ;
					resultArray[currentPos++] = ( val << 26 ) >>> 29 ;
					resultArray[currentPos++] = ( val << 29 ) >>> 29 ;
					break;
				}
				case 3 : { //code num : 7, bitwidth : 4
					resultArray[currentPos++] = ( val << 4  ) >>> 28 ;
					resultArray[currentPos++] = ( val << 8  ) >>> 28 ;
					resultArray[currentPos++] = ( val << 12 ) >>> 28 ;
					resultArray[currentPos++] = ( val << 16 ) >>> 28 ;
					resultArray[currentPos++] = ( val << 20 ) >>> 28 ;
					resultArray[currentPos++] = ( val << 24 ) >>> 28 ;
					resultArray[currentPos++] = ( val << 28 ) >>> 28 ;
					break;
				}
				case 4 : { //code num : 5, bitwidth : 5
					resultArray[currentPos++] = ( val << 7  ) >>> 27 ;
					resultArray[currentPos++] = ( val << 12 ) >>> 27 ;
					resultArray[currentPos++] = ( val << 17 ) >>> 27 ;
					resultArray[currentPos++] = ( val << 22 ) >>> 27 ;
					resultArray[currentPos++] = ( val << 27 ) >>> 27 ;
					break;
				}
				case 5 : { //code num : 4, bitwidth : 7
					resultArray[currentPos++] = ( val << 4  ) >>> 25 ;
					resultArray[currentPos++] = ( val << 11 ) >>> 25 ;
					resultArray[currentPos++] = ( val << 18 ) >>> 25 ;
					resultArray[currentPos++] = ( val << 25 ) >>> 25 ;
					break;
				}
				case 6 : { //code num : 3, bitwidth : 9
					resultArray[currentPos++] = ( val << 5  ) >>> 23 ;
					resultArray[currentPos++] = ( val << 14 ) >>> 23 ;
					resultArray[currentPos++] = ( val << 23 ) >>> 23 ;
					break;
				}
				case 7 : { //code num : 2, bitwidth : 14
					resultArray[currentPos++] = ( val << 4  ) >>> 18 ;
					resultArray[currentPos++] = ( val << 18 ) >>> 18 ;
					break;
				}
				case 8 : { //code num : 2, bitwidth : 14
					resultArray[currentPos++] = ( val << 4 ) >>> 4;
					break;
				}
			}

		}
		return resultArray;

	}


}
