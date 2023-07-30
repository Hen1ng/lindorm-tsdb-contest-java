package com.alibaba.lindorm.contest.util.list;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * List to store {@code Alteration}s to the backingList of a {@code PatchWorkArray}, so that elements can be accessed
 * quickly.
 */
class AlterationList extends NaturalSortedList<Alteration> {

	private static final long serialVersionUID = 1L;

	//Cached value of the "performaceHit" of all the Alterations in this list..
	private int totalPerformanceHit;
	
	AlterationList(){
		totalPerformanceHit = 0; //unrequired but for completeness.
	}

	/**
	 * Deletes the alteration covering the given index in the {@code PatchWorkArray}'s
	 * backingList.
	 * 
	 * @param index the index of the backingList for which the Alteration needs to be removed.
	 *        Note that in the case of a {@code Removal}, the index only needs to be covered 
	 * @throws IllegalStateException in the case that an attempt is made to remove an Alteration that
	 *         doesn't exist.
	 */
	void deleteAlterationAtIndex(final int index){
		//get the alteration that is the floor of this one..
		AlterationNode altNode = getNodeCovering(index);
		Alteration alt = (altNode == null) ? null : altNode.getValue();
		if(alt == null || !alt.coversBackingListIndex(index)){
			throw new IllegalStateException("Can not delete alteration covering index: " + index + " as no such " +
					"alteration, exists!");
		}
		//(unfortunately) we need to deal with Additions and Removals in different ways..
		if(alt.getClass() == Addition.class){
			remove(altNode);
		} else { //instance of Removal..
			//if the removal doesn't cover multiple elements remove it.
			if(alt.size() == 1){
				remove(altNode);

			} else {
				if(alt.index + alt.size() -1 == index){ //case that we're removing "from the end of the Removal"
					alt.indexDiff--;
					altNode.updateCachedValues();
				} else if(alt.index == index) { //case that we're removing from the start of the Removal..
					alt.index++;
					alt.indexDiff--;
					altNode.updateCachedValues();
				} else { //case that removing from middle of Removal..
					//we make the Removal to the left smaller then add a new one to the right..
					int oldIndexDiff = alt.indexDiff;
					int leftIndexDiff = index-alt.index;
					alt.indexDiff = leftIndexDiff;
					altNode.updateCachedValues();

					Removal toRight = new Removal(index+1, oldIndexDiff-leftIndexDiff-1);
					add(toRight);
				}
			}
		}
		
	}
	
	/**
	 * Returns the modCount for this {@code AlterationList}.
	 *
	 * @return the modCount for this list.
	 */
	int getModCount(){
		return modCount;
	}

	/**
	 * Obtains the {@code AlterationNode} that contains the {@code Alteration} covering the given index
	 * or <code>null</code> in the case that no such node exists.
	 * <p>
	 * Takes time <i>O(log(size())</i>.
	 * 
	 * @param index the covered index to locate the {@code AlterationNode} for.
	 * @return the {@code AlterationNode} covering the given index, or <code>null</code> if no such node exists.
	 */
	AlterationNode getNodeCovering(int index){
        Node current = getRoot();
        while(current != null){
        	Alteration alt = ((AlterationNode) current).getValue();
        	if(alt.coversBackingListIndex(index)){
        		break; //it's a match!
        	}
        	//compare the index ot see if we need to traverse left of right..
        	if(alt.index < index){ //need to go right..
                current = current.getRightChild();
            } else { //need to go left.
                current = current.getLeftChild();
            }
        }
        return ((AlterationNode) current);
	}
	
	/**
	 * Moves the {@code Removal} at the given index one position to the left and increments its
	 * size by one, this should be used when removing an element to the left of the this {@code Removal}.
	 *
	 * @param index the index of the {@code Removal}.
	 * @throws ClassCastException in the case that the Alteration at the given index is not a {@code Removal}.
	 * @throws NullPointerException in the case that there is no Alteration with the given index.
	 */
	void moveRemovalLeftAndIncrementSize(int index){
		alterRemovalCoveringIndex(index, -1, 1);
	}
	
	/**
	 * Gets the Removal which covers the given index, decreases its size by one and shifts its
	 * index one place to the right.
	 * <p>
	 * This should be used in the case that you wish to add an element at the position
	 * held by the start of the {@code Removal} covering the given index and the {@code Removal}
	 * has size at least two.
	 *
	 * @param index the index of the {@code Removal}.
	 */
	void moveRemovalRightAndDecrementSize(int index){
		alterRemovalCoveringIndex(index, 1, -1);
	}
	
	/**
	 * Gets the {@code Removal} which covers the given index and increments its size by one.
	 * <p>
	 * This should be used in the case that you wish to remove the element to the right of this {@code Removal}.
	 *
	 * @param index the index of the {@code Removal}.
	 */
	void incrementSizeOfRemovalForIndex(int index){
		alterRemovalCoveringIndex(index, 0, 1);
	}

	/**
	 * Gets the Removal which covers the given index and decreases its size by one.
	 * <p>
	 * This should be used in the case that you wish to add an element at the position
	 * held by the end of the {@code Removal} covering this index and the {@code Removal}
	 * has size at least two.
	 *
	 * @param index the index of the {@code Removal}.
	 */
	void decrementSizeOfRemovalForIndex(int index){
		alterRemovalCoveringIndex(index, 0, -1);
	}
	
	//Gets the Removal covering the given index, adds the given changeToIndex to its index
	//and adds the changeToIndexDiff to its indexDiff.  Finally updates the cached values..
	private void alterRemovalCoveringIndex(int index, int changeToIndex, int changeToIndexDiff){
		AlterationNode altNode = getNodeCovering(index);
		Removal rem = (Removal) altNode.getValue();
		rem.index += changeToIndex;
		rem.indexDiff += changeToIndexDiff;
		altNode.updateCachedValues();
	}
	
	/**
	 * Returns an Iterator which begins at the given index in this
	 * {@code AlterationList}.
	 *
	 * @param startIndex the index in this list to start at, for example
	 *        giving 0 is equivalent to calling {@code #iterator()} in a non-empty list.
	 * @return an {@code Iterator} for going through this list quickly.
	 */
	Iterator<Alteration> iterator(int startIndex){
		return new CustomStartingItr(startIndex);
	}

	 //Custom implementation of iterator which provides rapid access
	//to the elements and can be configured with a starting index..
    private class CustomStartingItr implements Iterator<Alteration> {

    	//the next node to show and it's index..
    	private Node nextNode;
		private int nextIndex;

		//the last one returned..
		private Node lastReturned = null;

		private int expectedModCount = modCount; //optimistic concurrent mod check..

		CustomStartingItr(int startIndex){
    		nextIndex = startIndex;
    		nextNode = (startIndex < 0 || startIndex > size() - 1) ? null : findNodeAtIndex(startIndex);
    	}

		@Override
		public boolean hasNext() {
			return nextNode != null;
		}

		@Override
		public Alteration next() {
			checkModCount();

			if(nextNode == null){
				throw new NoSuchElementException();
			}

			//update fields..
			lastReturned = nextNode;
			nextNode = nextNode.successor();
			nextIndex++;

			return lastReturned.getValue();
		}

		@Override
		public void remove() {
			checkModCount();

			if(lastReturned == null){
				throw new IllegalStateException();
			}

			AlterationList.this.remove(lastReturned);
			lastReturned = null;

			//the nextNode could now be incorrect so need to get it again..
			nextIndex--;
			if(nextIndex < size()){ //check that a node with this index actually exists..
				nextNode = findNodeAtIndex(nextIndex);
			} else {
				nextNode = null;
			}

			expectedModCount = modCount;
		}

		private void checkModCount(){
			if(expectedModCount != modCount){
				throw new ConcurrentModificationException();
			}
		}
    }

	/**
	 * Gets the Alteration at the given index and adds the  <code>indexDiffChange</code>
	 * to its <code>indexDiff</code>.
	 * <p>
	 * Note that the <code>indexDiffChange</code> should be negative if you are increasing the size
	 * of an {@code Addition} and positive when increasing the size of a {@code Removal}.
	 *
	 * @param index the index of the {@code Alteration}.
	 * @param indexDiffChange the amount to add to it's indexDiff.
	 * @throws ClassCastException in the case that there is an Removal at the given index.
	 * @throws NullPointerException in the case that there is no Alteration at the given index.
	 */
	void changeIndexDiffForAlterationAtIndex(int index, int indexDiffChange){
		//put in a dummy value then try and find it in the list..
		AlterationNode altNode = (AlterationNode) findFirstNodeWithValue(new Addition(index));
		Alteration alt = altNode.getValue();

		//perform the change ensuring that the cached value is kept sync-ed..
		int previousPerformanceHit = alt.getPerformanceHit();
		alt.indexDiff += indexDiffChange;
		totalPerformanceHit += alt.getPerformanceHit() - previousPerformanceHit;

		altNode.updateCachedValues(); //trigger the values updating up the tree..
	}

	/**
	 * Gets the location of the element at the given index in the backingList that this
	 * {@code AlterationList} is storing the changes to.
	 * <p>
	 * This work in time <i>O(log size())</i>.
	 *
	 * @param index the index in the list about which the {@code Alteration}s in this list refer to.
	 * @return the location of the given element in the referred to list.
	 */
    ArrayLocation getLocationOf(final int index){
    	
    	int mainIndex = index; //the index in the main list that contains the value we want (updated as the search progresses)..
    	int subIndex = -1; //the index of the sub-list that contains the value we want (-1 means no sub-index)..

    	AlterationNode current = (AlterationNode) getRoot();
    	while(current != null){
    		//get the alteration of the node..
	    	Alteration alt = current.getValue();

	    	//the change that the current alteration makes to smaller indices make..
	    	AlterationNode leftChild = (AlterationNode) current.getLeftChild();
	    	int leftSideDiff = (leftChild == null) ? 0 : leftChild.totalIndexDiff();

	    	//what's the index taking into account what happens in the smaller indices..
	    	int adjustedIndex = mainIndex + leftSideDiff;

	    	//this alteration occurs at an index too big to affect us..
	    	if(adjustedIndex < alt.index){
	    		current = leftChild;
	    	} else { //case that this index will affect the index we want to find..

	    		if(alt.indexDiff < 0){ //we may need to return a sub index..
		    		//smallest and largest indices of elements that being stored in sub list...
		        	int smallestIndexInSubArray = alt.index - leftSideDiff;
		        	int largestIndexInSubArray = smallestIndexInSubArray + Math.abs(alt.indexDiff);

		        	//case that we want an element in this location..
		    		if(mainIndex <= largestIndexInSubArray){
		    			subIndex = mainIndex-smallestIndexInSubArray;
		    			mainIndex = alt.index;
		    			break;

		    		} else { //(mainIndex > largestIndexInSubArray)
		    			current = (AlterationNode) current.getRightChild();
		    			mainIndex = adjustedIndex + alt.indexDiff;
		    		}

		     	} else { //alt instanceof Removal
		     		current = (AlterationNode) current.getRightChild();
					mainIndex = adjustedIndex + alt.indexDiff; //need to change what we are looking for..
			 	}
	    	}
    	}

    	return new ArrayLocation(mainIndex, subIndex);
    }
    
    /**
     * Returns the total "performance hit" of the {@code Alteration}s stored in this
     * {@code AlterationList}.
     * <p>
     * This method make use of a cached value and hence takes constant time.
     *
     * @return the total "performance hit" of the {@code Alteration}s stored in this
     * {@code AlterationList}.
     */
    int getTotalPerformanceHit(){
    	return totalPerformanceHit;
    }

    //Overrides the parent method so that cached values are kept sync-ed..
    @Override
    protected void remove(Node node){
    	totalPerformanceHit -= ((AlterationNode) node).getValue().getPerformanceHit();
    	super.remove(node);
    }

	//Override so that the AlterationNode class is used instead of the default Node one
    //and any cached values are correctly sync-ed.
	@Override
	public boolean add(Alteration alt){		
		add(new AlterationNode(alt)); //uses the #add(Node) method of the super class..
		totalPerformanceHit += alt.getPerformanceHit();
		return true;
	}

	//Represents the individual positions in the AlterationList..
	class AlterationNode extends Node {

		//Cached values..
		int totalChildrenIndexDiff = 0; //sum of all children index diffs..

		AlterationNode(Alteration alt){
			super(alt);
		}

		//Ensures that the totalChildrenIndexDiff is correctly set..
		@Override
		protected void updateAdditionalCachedValues(){
			//set the total child index diff to the sum of left and right diffs..
			totalChildrenIndexDiff = 0;
			AlterationNode leftChild = (AlterationNode) getLeftChild();
			totalChildrenIndexDiff += (leftChild == null) ? 0 : leftChild.totalIndexDiff();
			AlterationNode rightChild = (AlterationNode) getRightChild();
			totalChildrenIndexDiff += (rightChild == null) ? 0 : rightChild.totalIndexDiff();
		}

		/**
		 * The difference in the index over the whole sub-tree rooted at this node.
		 * <p>
		 * This method requires only constant time to run.
		 *
		 * @return the diff over the while sub-tree rooted at this node.
		 */
		int totalIndexDiff(){
			return getValue().indexDiff + totalChildrenIndexDiff;
		}

	} //end of the AlterationNode innerClass.

} //end of the AlterationList class.

