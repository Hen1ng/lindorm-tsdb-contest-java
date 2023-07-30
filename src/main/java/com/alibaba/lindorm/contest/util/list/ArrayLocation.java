package com.alibaba.lindorm.contest.util.list;

/**
 * Simple class to define a location within the a patch work array.
 */
class ArrayLocation {
	final int backingListIndex;
	final int subListIndex; //should be -1 in the case that there is no sublist..
	
	ArrayLocation(int backingListIndex, int subListIndex){
		this.backingListIndex = backingListIndex;
		this.subListIndex = subListIndex;
	}
	//location where there is no sub list index..
	ArrayLocation(int backingListIndex){
		this.backingListIndex = backingListIndex;
		this.subListIndex = -1;
	}

	/**
	 * Returns whether or not this location has a sub-list index.
	 */
	public boolean hasSubListIndex(){
		return subListIndex != -1;
	}
	
	@Override
	public String toString(){
		return "ArrayLocation:[" + backingListIndex + ", " + subListIndex + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + backingListIndex;
		result = prime * result + subListIndex;
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ArrayLocation other = (ArrayLocation) obj;
		if (backingListIndex != other.backingListIndex)
			return false;
		if (subListIndex != other.subListIndex)
			return false;
		return true;
	}

}
