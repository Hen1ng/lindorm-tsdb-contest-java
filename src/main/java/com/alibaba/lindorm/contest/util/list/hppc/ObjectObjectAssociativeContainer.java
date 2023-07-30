package com.alibaba.lindorm.contest.util.list.hppc;

import java.util.Iterator;

public interface ObjectObjectAssociativeContainer<KType, VType>
        extends Iterable<ObjectObjectCursor<KType, VType>>
{
    /**
     * Returns a cursor over the entries (key-value pairs) in this map. The
     * iterator is implemented as a cursor and it returns <b>the same cursor
     * instance</b> on every call to {@link Iterator#next()}. To read the current
     * key and value use the cursor's public fields. An example is shown below.
     *
     * <pre>
     * for (IntShortCursor c : intShortMap) {
     *   System.out.println(&quot;index=&quot; + c.index + &quot; key=&quot; + c.key + &quot; value=&quot; + c.value);
     * }</pre>
     *
     * <p>
     * The <code>index</code> field inside the cursor gives the internal index
     * inside the container's implementation. The interpretation of this index
     * depends on to the container.
     */
    @Override
    public Iterator<ObjectObjectCursor<KType, VType>> iterator();

    /**
     * Returns <code>true</code> if this container has an association to a value
     * for the given key.
     */
    public boolean containsKey(KType key);

    /**
     * @return Returns the current size (number of assigned keys) in the
     *         container.
     */
    public int size();

    /**
     * @return Return <code>true</code> if this hash map contains no assigned
     *         keys.
     */
    public boolean isEmpty();

}