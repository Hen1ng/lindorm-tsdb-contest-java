package com.alibaba.lindorm.contest.util.list.hppc;

import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class AbstractIterator<E> implements Iterator<E> {
    private static final int NOT_CACHED = 0;
    private static final int CACHED = 1;
    private static final int AT_END = 2;

    /** Current iterator state. */
    private int state = NOT_CACHED;

    /** The next element to be returned from {@link #next()} if fetched. */
    private E nextElement;

    /** {@inheritDoc} */
    @Override
    public boolean hasNext() {
        if (state == NOT_CACHED) {
            state = CACHED;
            nextElement = fetch();
        }
        return state == CACHED;
    }

    /** {@inheritDoc} */
    @Override
    public E next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        state = NOT_CACHED;
        return nextElement;
    }

    /** Default implementation throws {@link UnsupportedOperationException}. */
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    /**
     * Fetch next element. The implementation must return {@link #done()} when all elements have been
     * fetched.
     *
     * @return Returns the next value for the iterator or chain-calls {@link #done()}.
     */
    protected abstract E fetch();

    /**
     * Call when done.
     *
     * @return Returns a unique sentinel value to indicate end-of-iteration.
     */
    protected final E done() {
        state = AT_END;
        return null;
    }
}

