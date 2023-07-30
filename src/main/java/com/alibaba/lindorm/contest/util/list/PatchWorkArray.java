package com.alibaba.lindorm.contest.util.list;

import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * List implementation which is backed by an ArrayList and performs
 * lazy insertions and removals
 * <p>
 * This implementation is not synchronised so if you need
 * multi-threaded access then consider wrapping
 * it using the {@link Collections#synchronizedList} method.
 * <p>
 * The iterators this list provides are <i>fail-fast</i>, so any structural
 * modification, other than through the iterator itself, cause it to throw a
 * {@link ConcurrentModificationException}.
 *
 * @author Mark Rhodes
 * @version 1.0
 * @see List
 * @see Collection
 * @see ArrayList
 * @param <T> The type of element that this list should store.
 */
public class PatchWorkArray<T> extends AbstractList<T> implements Serializable {

       private static final long serialVersionUID = -268305845431177099L;

       //the value to use when the value has been removed..
       private final static Object NO_VALUE = new Object(){
    	   @Override
    	   public String toString(){
    		   return "NO_VALUE";
    	   }
       };

       //The type of values that can occur at an individual index in the backing list..
       private enum ElementType {
           NORMAL, NO_VALUE, SUB_LIST
       };

       private List<Object> backingList; //the ArrayList that backs this list..
       private final AlterationList alterations; //the Alterations that have been made to the backing list..

       //cached values to improve performance..
       private int size; //number of actual elements in the list.

       /**
        * Creates a new {@code PatchWorkArray} backed by an ArrayList with an initial
        * capacity of ten.
        */
       public PatchWorkArray() {
           backingList = new ArrayList<Object>();
           alterations = new AlterationList();
       }

       /**
        * Creates a new {@code PatchWorkArray} backed by an ArrayList with the given
        * initial capacity.
        */
       public PatchWorkArray(int initialCapacity) {
               backingList = new ArrayList<Object>(initialCapacity);
               alterations = new AlterationList();
       }

       /**
        * Obtains the element at the given index in this {@code PatchWorkArray}.  Note that
        * indices are numbered from zero through to {@code #size()-1}.
        * <p>
        * This implementation works in time <i>O(log(d))</i>, where <i>d</i> is the number
        * of alterations in the list.
        *
        * @return the elements at the given index in this {@code PatchWorkArray}.
        */
       @SuppressWarnings("unchecked")
       @Override
       public T get(int index) {
           if(index < 0 || index >= size()){
	           throw new IllegalArgumentException(index + " is not valid index.");
	       }
           ArrayLocation loc = alterations.getLocationOf(index);
           return (loc.hasSubListIndex())
                       ? ((List<T>) backingList.get(loc.backingListIndex)).get(loc.subListIndex)
                       : (T) backingList.get(loc.backingListIndex);
       }

       /**
        * Returns the number of elements in this {@code PatchWorkArray}.
        *
        * @return the number of elements in this {@code PatchWorkArray}.
        */
       @Override
       public int size() {
               return size;
       }

       /**
        * Returns the size of the alterations list.
        *
        * @return the size of the alterations list.
        */
       int getAlterationsSize(){
    	   return alterations.size();
       }

       /**
        * Returns the size of the list backing this {@code PatchWorkArray}.
        *
        * @return the size of the list backing this {@code PatchWorkArray}.
        */
       int backingListSize(){
    	   return backingList.size();
       }

       /**
        * Adds the given object to the end of this {@code PatchWorkArray}. This implementation
        * forbids <code>null</code> values from being added, an {@code IllegalArgumentException}
        * is thrown in the case that this is attempted.
        *
        * @param obj the object to add to the list.
        * @return <code>true</code> if the operation to insert the element was successful.
        * @throws IllegalArgumentException in the case that the given element is <code>null</code>.
        */
       @Override
       public boolean add(T obj){
           if(obj == null){
                   throw new IllegalArgumentException("null can not be added to a PatchWorkArray.");
           }
           size++;
           modCount++;
           return backingList.add(obj);
       }

       /**
        * The performance hit caused by the {@code Alteration}s to the backingList of this
        * {@code PatchWorkArray}.  The time it takes to perform the operations: get, remove, add is the log
        * of this value.
        * 
        * @return the effective hit in performance caused by the alterations to the backingList of this
        * {@code PatchWorkArray}.
        */
       public int getPerformanceHit(){
    	   return alterations.getTotalPerformanceHit();
       }
       /**
        * Returns an iterator which allows the list to be iterated over in
        * <i>O(a + n)</i>, where <i>a</i> is the number of alterations and
        * <i>n</i> is the number of elements in this {@code PatchWorkArray}.
        *
        * @return an {@code Iterator} for iterating elements of this list.
        */
       @Override
       public Iterator<T> iterator(){
    	   return new Itr();
       }

       //Custom implementation of iterator which provides rapid access to the elements..
       private class Itr implements Iterator<T> {

    	   int nextIndex = 0; //next index of this list to return..
    	   int lastReturnedIndex = -1; //-1 indicates no element valid index was just returned..

    	   int nextBLIndex  = 0;

    	   //Current position in sublist if any..
    	   SortedList<T>.Node lastSubListNode = null;

    	   int expectedModCount = modCount; //best-effort basis for checking the modCount..

			@Override
			public boolean hasNext() {
				return nextIndex < size;
			}

			@SuppressWarnings("unchecked")
			@Override
			public T next() {
				checkModCount();

				if(!hasNext()){
					throw new NoSuchElementException();
				}

				//reference to the element we'll be returning..
				T next = null;

				//case that we are going through a sublist..
				if(lastSubListNode != null){
					lastSubListNode = lastSubListNode.successor();
					if(lastSubListNode != null){
						next = lastSubListNode.getValue();
					} else {
						nextBLIndex++;
					}
				}

				if(next == null) { //see if the next index is valid..
					switch(getTypeAtBackingListIndex(nextBLIndex)){

						case NO_VALUE: //its a removal - figure out how far to skip forward..
							 nextBLIndex += alterations.getNodeCovering(nextBLIndex).getValue().indexDiff;
							 //next index should always be ok (removals should be merged together)..
							 switch(getTypeAtBackingListIndex(nextBLIndex)){
							 	case NO_VALUE:
							 		throw new IllegalStateException("The alterations of this list are out of sych!");
							 	case NORMAL:
							 		next = (T) backingList.get(nextBLIndex++);
							 		break;
							 	case SUB_LIST:
							 		lastSubListNode = ((UnsortedList<T>) backingList.get(nextBLIndex)).findNodeAtIndex(0);
							 		next = lastSubListNode.getValue();
							 }
							 break;

						case NORMAL:
							next = (T) backingList.get(nextBLIndex++);
							break;

						case SUB_LIST:
							lastSubListNode = ((UnsortedList<T>) backingList.get(nextBLIndex)).findNodeAtIndex(0);
							next = lastSubListNode.getValue();
					}
				}
				lastReturnedIndex = nextIndex++;
				return next;
			}

			@SuppressWarnings("unchecked")
			@Override
			public void remove() {
				checkModCount();

				if(lastReturnedIndex < 0){
					throw new IllegalStateException();
				}

				//perform the removal..
				PatchWorkArray.this.remove(lastReturnedIndex);
				lastReturnedIndex = -1;

				//now fix the position vars..
				nextIndex--;
				if(nextIndex == size){
					lastSubListNode = null;
				} else {
					ArrayLocation loc = alterations.getLocationOf(nextIndex);
					nextBLIndex = loc.backingListIndex;
					lastSubListNode = ((UnsortedList<T>) backingList.get(loc.backingListIndex)).findNodeAtIndex(loc.subListIndex);
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
        * Testing method only - verifies that the alterations List and the backing list
        * are in sync.
        * 
        * @return whether or not the backing list and the alterations are in sync.
        */
       @SuppressWarnings("unchecked")
       boolean __checkBackingListAndAlterationsInSync__(){
    	   ListIterator<Object> backItr = backingList.listIterator();
    	   Iterator<Alteration> altItr = alterations.iterator();
    	   while(backItr.hasNext()){
    		   Object next = backItr.next();
    		   switch(getType(next)){
    		   		//Removal case..
    		   		case NO_VALUE:
    		   			if(!altItr.hasNext()) return false;
    		   			Alteration alt = altItr.next();
    		   			if(!(alt.indexDiff > 0 && alt.index == backItr.previousIndex())) return false;
    		   			
    		   			//check whole block of Removals is present in backingList..
    		   			for(int i = 0; i < alt.indexDiff -1; i++){
    		   				if(!(backItr.hasNext() && getType(backItr.next()) == ElementType.NO_VALUE)) return false;
    		   			}
    		   			break;
    		   		case SUB_LIST:
    		   			if(!altItr.hasNext()) return false;
    		   			
    		   			alt = altItr.next();
    		   			if(!(alt.indexDiff < 0 && alt.index == backItr.previousIndex())) return false;
    		   			List<T> subList = ((List<T>) next);
    		   			if(subList.size()-1 != alt.size()) return false;
    		   }
    	   }
    	   return true;
       }

       /**
        * Inserts the specified element at the specified position in this list. Shifts the element currently
        * at that position (if any) and any subsequent elements to the right (adds one to their indices).
        * <p>
        * If the given index is the current size of the list, the {@code #add(Object)} method is invoked, otherwise
        * the element is lazily added and the number of alterations is incremented.
        *
        * @param index at which the element should be added.
        * @param obj the object to by inserted.
        * @throws IllegalArgumentException in the case that the given element is null.
        * @throws IndexOutOfBoundsException in the case that (0 <= index <= size()) does not hold.
        */
       @Override
       @SuppressWarnings("unchecked")
       public void add(int index, T obj){
           if(obj == null){
               throw new IllegalArgumentException("null can not be added to a PatchWorkArray.");
           }
           if(index < 0 || index > size()){
               throw new IndexOutOfBoundsException(index + " is not a valid index for inserting an element, must be between 0 and size() inclusive.");
           }
           //case that the elements should just be added to the end of the list..
           if(index == size()){
               add(obj); //will increment modCount..
           } else {
               //Get the location of where we'd like to store the value
               //if it's in a sub-list add to that list, otherwise make a new sublist..
               ArrayLocation loc = alterations.getLocationOf(index);
               int blIndex = loc.backingListIndex;
               if(loc.hasSubListIndex()){
                   int slIndex = loc.subListIndex;
                   //case that we're adding to the front of the sublist, not the front of the list and there is a free space to the left..
                   if(blIndex > 0 && slIndex == 0 && getTypeAtBackingListIndex(blIndex-1) == ElementType.NO_VALUE){
                       backingList.set(blIndex-1, obj);
                       alterations.deleteAlterationAtIndex(blIndex-1);

                   } else {
                       List<T> subList = (List<T>) backingList.get(blIndex);

                       //case that there is a removal to the right and not to the end of the list..
                       if(blIndex < backingList.size()-1 && getTypeAtBackingListIndex(blIndex+1) == ElementType.NO_VALUE){
                           //move the last thing currently in the sublist to empty space add just insert this
                           //new element into the list..
                           T lastInSubList = subList.remove(subList.size()-1);
                           subList.add(slIndex, obj);
                           backingList.set(blIndex+1, lastInSubList);
                           alterations.deleteAlterationAtIndex(blIndex+1);

                       } else { //just going to add to the sublist as normal..
                           subList.add(slIndex, obj);

                           //update the alterations list accordingly..
                           alterations.changeIndexDiffForAlterationAtIndex(blIndex, -1);
                       }
                   }
               } else { //not adding to a sub-list..
                   //case that we're not adding to the front and there is a removal to the left..
                   if(blIndex > 0 && getTypeAtBackingListIndex(blIndex-1) == ElementType.NO_VALUE){
                       backingList.set(blIndex-1, obj);
                       alterations.deleteAlterationAtIndex(blIndex-1);

                   //case that there is a removal to the right..
                   } else if(blIndex < backingList.size()-1 && getTypeAtBackingListIndex(blIndex+1) == ElementType.NO_VALUE){
                       //move what is in required index up one..
                       backingList.set(blIndex+1, backingList.get(blIndex));
                       backingList.set(blIndex, obj);
                       alterations.deleteAlterationAtIndex(blIndex+1);

                   } else { //case that a new sublist will need to be created..

                       //create a new sublist and replace what's at the current index in the backing list with it..
                       List<T> subList = new SubList(); //(allows log time removal/insertions)
                       subList.add(obj);
                       subList.add((T) backingList.get(blIndex));
                       backingList.set(blIndex, subList);

                       //update the alterations list accordingly..
                       alterations.add(new Addition(blIndex));
                   }
               }
               size++;
               modCount++;
           }
       }

       /**
        * Pushes all the alterations in this {@code PatchWorkArray} to the backing list,
        * leaving the number of alterations as zero.
        * <p>
        * This method performs structural changes to the list and hence increments the
        * {@code modCount} of the list.
        */
       public void fix(){
    	   //the new backing list and the next index to add to..
           List<Object> fixedList = new ArrayList<Object>(size());

           Iterator<Alteration> itr = alterations.iterator();
           int blIndex = 0; //index into backing list..
           while(itr.hasNext()){
               Alteration alt = itr.next();
               int altIndex = alt.index;

	           //copy the elements from blIndex to altIndex-1..
	           for(; blIndex < altIndex; blIndex++){
	        	   fixedList.add(backingList.get(blIndex));
	           }

               if(alt.indexDiff > 0){  //alt instanceof Removal..
            	 blIndex += alt.size();
               } else { //alt instanceof Addition..
            	   @SuppressWarnings("unchecked")
                   Iterator<Object> subListItr = ((List<Object>) backingList.get(altIndex)).iterator();
                   while(subListItr.hasNext()){
                       fixedList.add(subListItr.next());
                   }
                   blIndex++;
               }
           }
           //copy in the final elements..
           for(int size=backingList.size(); blIndex < size; blIndex++){
        	   fixedList.add(backingList.get(blIndex));
           }

           backingList = fixedList;
           alterations.clear();
           modCount++;
       }

       /**
        * Removes all elements and alterations from this {@code PatchWorkArray}.
        */
       @Override
       public void clear(){
           backingList.clear();
           alterations.clear();
           size = 0;
           modCount++;
       }

       /**
        * Returns the element type of the element in the given index of the backing list.
        *
        * @param index the index of the backing list to check the element type of.
        * @return the {@code ElementType} of the element at the given index in the backing list.
        * @throws NoSuchElementException in the case that the index does not exist in the backing list.
        */
       private ElementType getTypeAtBackingListIndex(int index){
           return getType(backingList.get(index));
       }

       /**
        * Returns the element type of the given object, which should be something from the
        * backing list.
        *
        * @param obj the object to check the element type of.
        * @return the {@code ElementType} of the given object.
        */
       private ElementType getType(Object obj){
           return (obj == NO_VALUE)
                   ? ElementType.NO_VALUE
                   : (obj instanceof PatchWorkArray.SubList)
                           ? ElementType.SUB_LIST
                           : ElementType.NORMAL;
       }
  
       /**
	    * Removes and returns the element at the given index in this {@code PatchWorkArray}.
	    * <p>
	    * This method can increase or decrease the number of alterations, depending on the element
	    * on which it is called.
	    *
	    * @param index the index of the element to remove.
	    * @return the element which was removed from the list.
	    * @throws IllegalArgumentException in the case that the index is not a valid index.
	    */
       @SuppressWarnings("unchecked")
       public T remove(int index){
    	   //quick check that the index is valid..
           if(index < 0 || index > size()){
               throw new IndexOutOfBoundsException("Index must be between 0 and size()-1.");
           }

           T valueRemoved = null; // the value which will be removed.
           ArrayLocation loc = alterations.getLocationOf(index);
           int blIndex = loc.backingListIndex;
           if(loc.hasSubListIndex()){ //need to treat differently if removing from a sub-list..
                   List<T> subList = (List<T>) backingList.get(blIndex);
                   valueRemoved = subList.remove(loc.subListIndex);

                   if(subList.size() == 1){ //case that trigger alteration needs to be removed..
                       backingList.set(blIndex, subList.get(0));
                       alterations.deleteAlterationAtIndex(blIndex);
                   } else { //case that alteration will remain..
                	   alterations.changeIndexDiffForAlterationAtIndex(blIndex, 1);
                   }

           } else { //case where there is a regular value at the required index..
               int backingListLastIndex = backingList.size()-1;

               if(blIndex == backingListLastIndex){
            	   //this is the last element of the backing list, just remove it from there..
                   backingList.remove(backingListLastIndex);
               } else { //removal is not from the end..
            	   boolean removalToRight = (getTypeAtBackingListIndex(blIndex+1) == ElementType.NO_VALUE);
            	   if(blIndex > 0 && getTypeAtBackingListIndex(blIndex-1) == ElementType.NO_VALUE){

            		   if(removalToRight){ //case that there is a removal to both sides (merge Removals)..
            			   AlterationList.Node rightRemoval = alterations.getNodeCovering(blIndex+1);
            			   int rightRemovalSize = rightRemoval.getValue().size(); //need to do this first so it doesn't change..
            			   //get rid of the right hand removal and make the left one larger to cover both..
            			   int leftHandRemovalIndex = rightRemoval.predecessor().getValue().index;
            			   alterations.remove(rightRemoval);
            			   alterations.changeIndexDiffForAlterationAtIndex(leftHandRemovalIndex, rightRemovalSize + 1);

            		   } else { //case that there is just a removal to the left..
            			   alterations.incrementSizeOfRemovalForIndex(blIndex-1);
            		   }

            	   } else if(removalToRight){ //case that there's a Removal to the right but not the left..
            		   alterations.moveRemovalLeftAndIncrementSize(blIndex+1);

            	   } else { //otherwise just make a new Removal..
            		   alterations.add(new Removal(loc.backingListIndex));
            	   }
                   valueRemoved = (T) backingList.set(blIndex, NO_VALUE);
               }
           }
           size--;
           modCount++;
           return valueRemoved;
       }

       //Class that defines sub-lists..
       private class SubList extends UnsortedList<T> {
    	   private static final long serialVersionUID = 308040562609962654L;
       };

} //end of the PatchWorkArray class.

