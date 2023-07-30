package com.alibaba.lindorm.contest.util.list.hppc;

public interface ObjectObjectMap<KType, VType> extends ObjectObjectAssociativeContainer<KType, VType> {
    /**
     * @return Returns the value associated with the given key or the default
     *         value for the value type, if the key is not associated with any
     *         value.  For numeric value types, this default value is 0, for
     *         object types it is {@code null}.
     */
    public VType get(KType key);

    /**
     * @return Returns the value associated with the given key or the provided
     *         default value if the key is not associated with any value.
     */
    public VType getOrDefault(KType key, VType defaultValue);

    /**
     * Place a given key and value in the container.
     *
     * @return The value previously stored under the given key in the map is
     *         returned.
     */
    public VType put(KType key, VType value);

    /**
     * If the specified key is not already associated with a value, associates it with the given
     * value.
     *
     * @return {@code true} if {@code key} did not exist and {@code value} was placed in
     * the map, {@code false} otherwise.
     */
    public default boolean putIfAbsent(KType key, VType value) {
        int keyIndex = indexOf(key);
        if (indexExists(keyIndex)) {
            return false;
        } else {
            indexInsert(keyIndex, key, value);
            return true;
        }
    }

    /**
     * Puts all keys from another container to this map, replacing the values of
     * existing keys, if such keys are present.
     *
     * @return Returns the number of keys added to the map as a result of this
     *         call (not previously present in the map). Values of existing keys
     *         are overwritten.
     */
    public int putAll(ObjectObjectAssociativeContainer<? extends KType, ? extends VType> container);

    /**
     * Puts all keys from an iterable cursor to this map, replacing the values of
     * existing keys, if such keys are present.
     *
     * @return Returns the number of keys added to the map as a result of this
     *         call (not previously present in the map). Values of existing keys
     *         are overwritten.
     */
    public int putAll(Iterable<? extends ObjectObjectCursor<? extends KType, ? extends VType>> iterable);





    /**
     * Remove all values at the given key. The default value for the key type is
     * returned if the value does not exist in the map.
     */
    public VType remove(KType key);

    /**
     * Compares the specified object with this set for equality. Returns
     * {@code true} if and only if the specified object is also a
     * {@link ObjectObjectMap} and both objects contains exactly the same key-value
     * pairs.
     */
    public boolean equals(Object obj);

    /**
     * @return A hash code of elements stored in the map. The hash code is defined
     *         as a sum of hash codes of keys and values stored within the set).
     *         Because sum is commutative, this ensures that different order of
     *         elements in a set does not affect the hash code.
     */
    public int hashCode();

    /**
     * Returns a logical "index" of a given key that can be used to speed up
     * follow-up value setters or getters in certain scenarios (conditional
     * logic).
     *
     * The semantics of "indexes" are not strictly defined. Indexes may (and
     * typically won't be) contiguous.
     *
     * The index is valid only between map modifications (it will not be affected
     * by read-only operations like iteration or value retrievals).
     *
     * @see #indexExists
     * @see #indexGet
     * @see #indexInsert
     * @see #indexReplace
     *
     * @param key
     *          The key to locate in the map.
     * @return A non-negative value of the logical "index" of the key in the map
     *         or a negative value if the key did not exist.
     */
    public int indexOf(KType key);

    /**
     * @see #indexOf
     *
     * @param index
     *          The index of a given key, as returned from {@link #indexOf}.
     * @return Returns <code>true</code> if the index corresponds to an existing
     *         key or false otherwise. This is equivalent to checking whether the
     *         index is a positive value (existing keys) or a negative value
     *         (non-existing keys).
     */
    public boolean indexExists(int index);

    /**
     * Returns the value associated with an existing key.
     *
     * @see #indexOf
     *
     * @param index
     *          The index of an existing key.
     * @return Returns the value currently associated with the key.
     * @throws AssertionError
     *           If assertions are enabled and the index does not correspond to an
     *           existing key.
     */
    public VType indexGet(int index);

    /**
     * Replaces the value associated with an existing key and returns any previous
     * value stored for that key.
     *
     * @see #indexOf
     *
     * @param index
     *          The index of an existing key.
     * @return Returns the previous value associated with the key.
     * @throws AssertionError
     *           If assertions are enabled and the index does not correspond to an
     *           existing key.
     */
    public VType indexReplace(int index, VType newValue);

    /**
     * Inserts a key-value pair for a key that is not present in the map. This
     * method may help in avoiding double recalculation of the key's hash.
     *
     * @see #indexOf
     *
     * @param index
     *          The index of a previously non-existing key, as returned from
     *          {@link #indexOf}.
     * @throws AssertionError
     *           If assertions are enabled and the index corresponds to an
     *           existing key.
     */
    public void indexInsert(int index, KType key, VType value);

    /**
     * Removes a key-value pair at an index previously acquired from {@link #indexOf}.
     *
     * @see #indexOf
     *
     * @param index
     *          The index of the key to remove, as returned from {@link #indexOf}.
     * @return Returns the previous value associated with the key.
     * @throws AssertionError
     *           If assertions are enabled and the index does not correspond to an
     *           existing key.
     */
    public VType indexRemove(int index);

    /**
     * Clear all keys and values in the container.
     *
     * @see #release()
     */
    public void clear();

    /**
     * Removes all elements from the collection and additionally releases any
     * internal buffers. Typically, if the object is to be reused, a simple
     * {@link #clear()} should be a better alternative since it'll avoid
     * reallocation.
     *
     * @see #clear()
     */
    public void release();

    /**
     * Visually depict the distribution of keys.
     *
     * @param characters
     *          The number of characters to "squeeze" the entire buffer into.
     * @return Returns a sequence of characters where '.' depicts an empty
     *         fragment of the internal buffer and 'X' depicts full or nearly full
     *         capacity within the buffer's range and anything between 1 and 9 is between.
     */
    public String visualizeKeyDistribution(int characters);
}
