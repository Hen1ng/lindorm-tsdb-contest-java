package com.alibaba.lindorm.contest.compress.intcodec.common;

import java.util.Arrays;

public abstract class AbstractByteRangeCodes {

	public byte[] encode( int[] numbers, boolean useArraySorte, boolean useGapList){

		if(useArraySorte)
			Arrays.sort( numbers );


		if(useGapList)
			numbers = toGapArray( numbers );

		return innerEncode(numbers);

	}

	protected static int[] toGapArray( int[] numbers ){

		int prev  = numbers[0];

		for( int i =1 ; i < numbers.length ; i++ ){
			int tmp = numbers[i];
			numbers[i] = numbers[i] - prev;
			prev = tmp;
		}
		return numbers;

	}

	protected abstract byte[] innerEncode( int[] gappedNumbers );

	public  abstract int[] decode ( byte[] encodedValue , boolean useGapList );

}
