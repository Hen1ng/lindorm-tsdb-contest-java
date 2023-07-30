package com.alibaba.lindorm.contest.util.list;

import java.util.Comparator;

/**
 * Provides a {@code SortedList} which sorts the elements by their
 * natural order. 
 *
 * @author Mark Rhodes
 * @version 1.1
 * @see SortedList
 * @param <T> any {@code Comparable}
 */
public class NaturalSortedList<T extends Comparable<? super T>>
		extends SortedList<T> {

	private static final long serialVersionUID = -8834713008973648930L;

	/**
	 * Constructs a new {@code NaturalSortedList} which sorts elements
	 * according to their <i>natural order</i>.
	 */
	public NaturalSortedList(){
		super(new Comparator<T>(){
			public int compare(T one, T two){
				return one.compareTo(two);
			}
		}); 
	}
}
