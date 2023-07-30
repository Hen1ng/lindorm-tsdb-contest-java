/**
 * Contains the Alteration class and its subclasses; Addition and Removal.
 */
package com.alibaba.lindorm.contest.util.list;

/**
 * Represents the alterations to the backingList of a {@code PatchWorkArray}.
 * <p>
 * These come in two flavors: {@code Addition}s and {@code Removal}. {@code Addition}s cover
 * a single index and represents the fact that the containing PatchWorkArray has a sublist
 * at that index in its backingList.  {@code Removal}s cover a continuous block of indices in the 
 * {@code PatchWorkArray}'s backingList and represent that those indices do not contain valid
 * elements.
 */
abstract class Alteration implements Comparable<Alteration> {

	int index; //the index that this alteration occurs..
	int indexDiff; //the difference that this alteration makes to the index..

	Alteration(int index, int indexDiff){
		this.index = index;
		this.indexDiff = indexDiff;
	}

	//Compares elements on their index only..
	@Override
	public int compareTo(Alteration other) {
		return index-other.index;
	}
	
	/**
	 * Returns the impact on performance which the existence of this
	 * Alteration will have on a containing {@code PatchWorkArray}.
	 * <p>
	 * Let <i>t</i> be the sum of the values returned by calling this method on all
	 * {@code Alteration}s in a {@code PatchWorkArray}, the time it takes to find an
	 * element in that list by index should be <i>O(log(t))</i>. 
	 * 
	 * @return an indication of the performance hit a {@code PatchWorkArray}
	 *         would have if it included this {@code Alteration}. 
	 */
	abstract int getPerformanceHit();

	/**
	 * Returns whether or not this {@code Alteration} covers the given index 
	 * in the {@code PatchWorkArray}'s backingList.
	 *
	 * @param index the index of thebackingList to check.
	 * @return <code>true</code> in the case that this {@code Alteration} covers the given
	 *         index of the backingList.
	 */
	abstract boolean coversBackingListIndex(int index);
	
	//Simply returns the index..
	@Override
	public int hashCode() {
		return index;
	}

	/**
	 * Returns the number of elements that this alteration affects,
	 * which is calculated as being the absolute size of the {@code #indexDiff}.
	 * 
	 * @return the absolute size of the {@code #indexDiff}.
	 */
	public int size(){
		return Math.abs(indexDiff);
	}

	//Works solely on the index..
	@Override
	public boolean equals(Object obj) {
		boolean same = false;
		if(obj instanceof Alteration){
			same = ((Alteration) obj).index == index;
		}
		return same;
	}
	
	@Override
	public String toString(){
		return "[" + index + "," + indexDiff + "]";  
	}
}

/**
 * Represents an alteration to a patch work array, where elements have been
 * added to the array at a specific index.
 */
class Addition extends Alteration {
	Addition(int index){
		super(index, -1);
	}
	void addElement(){
		indexDiff -= 1;
	}
	void removeElement(){
		indexDiff += 1;
	}
	//each element in an Addition slows the containing PatchWorkArray down..
	int getPerformanceHit(){
		return size();
	}
	boolean coversBackingListIndex(int index){
		return this.index == index;
	}
}

/**
 * Represents a consecutive block of elements being removed from the list.
 * <p>
 * The index of the removal is the smallest index in the block of consecutive removals.
 */
class Removal extends Alteration {
	
	/**
	 * Creates a new Removal of size 1 at the given index.
	 *
	 * @param index the index for this Removal.
	 */
	Removal(int index){
		super(index, 1);
	}
	
	/**
	 * Creates a new Removal of the given size at the given index.
	 *
	 * @param index the index for this Removal.
	 * @param indexDiff the number of elements that have been removed that this Removal represents.
	 */
	Removal(int index, int indexDiff){
		super(index, indexDiff);
	}
	
	/**
	 * This should only be called if the element to in position index-1 is a normal
	 * element that is due to be removed.
	 */
	void removeElementToLeft(){
		index--; //move the index to the left..
		indexDiff += 1;
	}
	void removeElementToRight(){
		indexDiff += 1;
	}
	//the effect of this removal on the containing PatchWorkArray should be constant..
	int getPerformanceHit(){
		return 1;
	}
	boolean coversBackingListIndex(int index){
		return this.index <= index && this.index + indexDiff > index;  
	}
}
