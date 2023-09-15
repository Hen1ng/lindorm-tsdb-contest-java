package com.alibaba.lindorm.contest.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

public class LindormBlockingQueue<E> implements BlockingQueue<E> {

    public interface SpinPolicy {

        /**
         * Do some spin work to avoid the producer/consumer thread fallen to unpark
         *
         * @param tryCount How many times the queue's length has been checked.
         * @return Whether spin work has been done.
         * If false is returned, the caller will perform a blocking wait.
         */
        boolean doSpin(int tryCount);
    }

    /**
     * Do a composite waiting strategy including SPIN, YIELD
     */
    public final static SpinPolicy LITE_SPIN_POLICY = new LiteSpinPolicy();

    /**
     * Don't do any spin work.
     */
    public final static SpinPolicy NO_SPIN_POLICY = new NoSpinPolicy();

    private static class LiteSpinPolicy implements SpinPolicy {
        private final static long PARK_NANOS = TimeUnit.MICROSECONDS.toNanos(100);

        public boolean doSpin(int tryCount) {
            if (shouldYield(tryCount)) {
                Thread.yield();
                return true;
            } else if (shouldSleep(tryCount)) {
                LockSupport.parkNanos(PARK_NANOS);
                return true;
            }
            return false;
        }
    }

    private static class NoSpinPolicy implements SpinPolicy {
        @Override public boolean doSpin(int tryCount) {
            return false;
        }
    }

    final private static int DEFAULT_LENGTH = 8;

    /**
     * Hub is internal structure for storing elements and handling conflicts
     *
     * <p>
     * hub.code   ->  The hub's status
     * seq         ->  The consumer/producer acquired number
     * asize       ->  The embedded array's size.
     * <p>
     * <p>
     * Hub's status meaning
     * When hub.sequence == ~(seq - asize),
     * The hub is ready for producer to write element at seq.
     * After the hub has been written, the hub.sequence will
     * turn from ~(seq - asize) to seq
     * <p>
     * When hub.sequence == seq
     * The hub is ready for consumer to fetch element at seq
     * After the hub has been read, the hub.sequence will turn
     * from seq to ~seq.
     * <p>
     * Thus, the hub.sequence would always have different value to avoid conflicts.
     */
    private static class Hub extends Sequence {
        Hub(long initial) {
            super(initial);
        }

        volatile Object e;

        /**
         * Fetch object at sequence
         * @return the object at sequence, must be not null
         */
        Object fetchObject(long seq) {
            int loop = 0;
            while (!readyForRead(seq)) {
                // Waiting too long for seq.
                // maybe the producer is slower than consumer, retry later.
                if (((++loop) & 0xff) == 0) {
                    Thread.yield();
                } else {
                    doSpin();
                }
            }

            Object obj;
            // Read maybe fast than write. Spin at here.
            while ((obj = e) == null) {
            }
            e = null;
            finishRead(seq);
            return obj;
        }

        void setObject(long seq, long asize, Object obj) {
            int loop = 0;
            while (!readyForWrite(seq, asize)) {
                // Waiting too long for seq.
                // maybe the consumer is slower than producer, retry later.
                if (((++loop) & 0xff) == 0) {
                    Thread.yield();
                } else {
                    doSpin();
                }
            }

            e = obj;
            finishWrite(seq);
        }

        boolean readyForRead(long seq) {
            long current = guessEqualGet(seq);
            return (seq == current);
        }

        boolean readyForWrite(long seq, long asize) {
            long prev = ~(seq - asize);
            long current = guessEqualGet(prev);
            return (prev == current);
        }

        void finishRead(long seq) {
            setVolatile(~seq);
        }

        void finishWrite(long seq) {
            setVolatile(seq);
        }
    }

    final private int index(long seq) {
        return (int) (seq & mask);
    }

    final private int size;
    final private Hub[] hubs;
    private Sequence head = new Sequence(); // consumer pointer
    private Sequence tail = new Sequence(); // producer pointer
    final private QueuedSynchronizer consumerParkingQueue;
    final private QueuedSynchronizer producerParkingQueue;
    final private SpinPolicy spinPolicy;
    /**
     * The aligned size of embedded array. Aligned size must be power of 2
     */
    final private long asize;
    /**
     * Fast operator for locating the hub by a sequence number.
     */
    final private long mask;

    private static class QueuedSynchronizer {
        private ThreadLocal<Parker> threadParker = ThreadLocal.withInitial(() -> {
            return new Parker(Thread.currentThread());
        });

        static private class Parker {
            public Parker(Thread thread) {
                this.thread = thread;
            }

            final Thread thread;

            /**
             * When epoch is even number, this thread is not waiting anyone
             * When epoch is odd number, this thread is waiting in parkingQueue
             * When (state % 2 == 1), the parker is inside parkingQueue
             */
            Sequence epoch = new Sequence(0);

            final static int MAX_CACHE = 4;
            final ParkerAndEpoch[] cache = new ParkerAndEpoch[MAX_CACHE];

            ParkerAndEpoch advanceEpochForPark() {
                long myEpoch = epoch.incrementAndGet();
                assert myEpoch % 2 != 0 : "myEpoch=" + myEpoch;
                int idx = IntStream.range(0, cache.length).filter(i -> cache[i] != null).findFirst().orElse(-1);
                ParkerAndEpoch pae;
                if (idx >= 0) {
                    pae = cache[idx];
                    cache[idx] = null;
                } else {
                    pae = new ParkerAndEpoch();
                }
                pae.reset(this, myEpoch);
                return pae;
            }

            boolean advanceEpochForUnpark(long expectEpoch, boolean hasMorePossibilityToSucceed) {
                assert expectEpoch % 2 != 0;
                if (!hasMorePossibilityToSucceed && epoch.getRelaxed() >= expectEpoch + 1) {
                    return false;
                }
                return epoch.compareAndSet(expectEpoch, expectEpoch + 1);
            }


            boolean cacheParkerAndEpochObject(ParkerAndEpoch obj) {
                int idx = IntStream.range(0, cache.length).filter(i -> cache[i] == null).findFirst().orElse(-1);
                if (idx >= 0) {
                    cache[idx] = obj;
                    return true;
                } else {
                    return false;
                }
            }
        }

        final static private class ParkerAndEpoch {
            volatile Parker parker;
            volatile long epoch;

            private ParkerAndEpoch() {
            }

            void reset(Parker parker, long epoch) {
                this.parker = parker;
                this.epoch = epoch;
            }

            /**
             * Only used when removing ParkerAndEpoch from queue.
             * @param obj
             * @return true if equal
             */
            @Override public boolean equals(Object obj) {
                return this == obj;
            }
        }

        ConcurrentLinkedQueue<ParkerAndEpoch> queue = new ConcurrentLinkedQueue<>();

        void signal() {
            ParkerAndEpoch parkerAndEpoch;
            while ((parkerAndEpoch = queue.poll()) != null) {
                assert parkerAndEpoch.parker != null && parkerAndEpoch.epoch >= 0;
                Parker parker = parkerAndEpoch.parker;
                long epoch = parkerAndEpoch.epoch;
                if (parker.advanceEpochForUnpark(epoch, true)) {
                    LockSupport.unpark(parker.thread);
                    return;
                }
            }
        }

        void await() {
            await(false, 0);
        }

        void await(long nanos) {
            await(true, nanos);
        }

        private void await(boolean timeWait, long nanos) {
            Parker parker = threadParker.get();
            ParkerAndEpoch pae = parker.advanceEpochForPark();
            queue.add(pae);
            try {
                if (timeWait) {
                    LockSupport.parkNanos(this, nanos);
                } else {
                    LockSupport.park(this);
                }
            } finally {
                if (parker.advanceEpochForUnpark(pae.epoch, false)) {
                    // If advance epoch succeed, means that this thread is woke by timeout or interrupt
                    if (queue.remove(pae)) {
                        // If removed successfully, we can sure that the signal thread never touch this PAE object.
                        // To make gc more friendly, we keep the pae in the parker's cache
                        parker.cacheParkerAndEpochObject(pae);
                    } else {
                        // If remove pae failed, means that the signal thread may see a middle-state pae, we should not
                        // reset the pae and put it into cache cache.
                    }
                } else {
                    // If advance epoch failed, means that the signal thread already finished to change the epoch, we can cache
                    // the pae object to reuse
                    parker.cacheParkerAndEpochObject(pae);
                }
            }
        }
    }

    public LindormBlockingQueue() {
        this(DEFAULT_LENGTH);
    }

    public LindormBlockingQueue(int size) {
        this(size, NO_SPIN_POLICY);
    }

    public LindormBlockingQueue(int size, SpinPolicy spinPolicy) {
        if (size <= 0)
            throw new IllegalArgumentException();
        this.size = size;
        asize = 1l << (Integer.SIZE - Integer.numberOfLeadingZeros(size - 1));
        if (asize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("size is too large");
        }
        mask = asize - 1;
        hubs = new Hub[(int) asize];
        // Initialize the hub's sequence.
        for (int i = 0; i < hubs.length; ++i) {
            hubs[i] = new Hub(0);
            hubs[i].finishRead(i - asize);
        }
        consumerParkingQueue = new QueuedSynchronizer();
        producerParkingQueue = new QueuedSynchronizer();
        this.spinPolicy = spinPolicy;
    }

    @SuppressWarnings("unchecked") @Override public E poll() {
        long h;
        long cachedTail = tail.getRelaxed();
        while (true) {
            h = head.getVolatile();
            // If the cached tail value has already been larger than head,
            // we don't need to read tail's value from memory again
            cachedTail = (h < cachedTail) ? cachedTail : tail.getVolatile();
            if (h < cachedTail) {
                if (head.compareAndSet(h, h + 1)) {
                    break;
                } else {
                    // CAS head failed, retry
                    continue;
                }
            }
            // no available element
            return null;
        }

        try {
            long seq = h + 1;
            int index = index(seq);
            return (E) hubs[index].fetchObject(seq);
        } finally {
            producerParkingQueue.signal();
        }
    }

    @Override public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E ret = null;
        checkDurationValid(unit.toNanos(timeout));
        long start = System.nanoTime();
        long end = unit.toNanos(timeout) + start;
        long now;
        int cnt = 0;
        do {
            if ((ret = poll()) != null)
                return ret;
            cnt += 1;
            if (spinPolicy.doSpin(cnt)) {
                doSpin();
            } else {
                now = System.nanoTime();
                consumerParkingQueue.await(end - now);
            }
            checkInterrupted();
            now = System.nanoTime();
        } while (now < end);
        return null;
    }

    @Override public E element() {
        E ret = peek();
        if (ret == null)
            throw new NoSuchElementException();
        return ret;
    }

    @SuppressWarnings("unchecked") @Override public E peek() {
        Object ret;
        while (true) {
            long h = head.getVolatile() + 1;
            long t = tail.guessLargerGet(h);
            if (h > t)
                return null;
            int index = (int) (h & mask);
            if (hubs[index].guessEqualGet(h) == h && (ret = hubs[index].e) != null)
                return (E) ret;
        }
    }

    @Override public int size() {
        return (int) (tail.getRelaxed() - head.getRelaxed());
    }

    @Override public boolean isEmpty() {
        return size == remainingCapacity();
    }

    @Override public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override public void clear() {
        while (poll() != null)
            ;
    }

    static private void checkNotNull(Object e) {
        if (e == null)
            throw new NullPointerException();
    }

    static private boolean checkInterrupted() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return false;
    }

    static private void checkDurationValid(long nanos) {
        if (nanos == Long.MIN_VALUE) {
            throw new IllegalArgumentException("Waiting time is too long");
        }
    }

    final private static int YIELD_MASK = ~(0x3);
    final private static int SLEEP_MASK = ~(0x7);

    // Sleep after try 8 times
    static private boolean shouldSleep(int c) {
        return (c & SLEEP_MASK) == 0;
    }

    // Yield after try 4 times
    static private boolean shouldYield(int c) {
        return (c & YIELD_MASK) == 0;
    }

    @Override public boolean add(E e) {
        return offer(e);
    }

    @Override public boolean offer(E e) {
        checkNotNull(e);

        long t;
        long cacheHead = head.getRelaxed();
        while (true) {
            t = tail.getVolatile();
            // If the cached head value tells that the space is enough.
            // we don't need to read head's value from memory again
            cacheHead = (t - cacheHead >= size) ? head.getVolatile() : cacheHead;
            if (t - cacheHead >= size) {
                return false;
            } else {
                if (tail.compareAndSet(t, t + 1)) {
                    break;
                } else {
                    continue;
                    // CAS tail failed, retry
                }
            }
        }

        try {
            long seq = t + 1;
            int index = index(seq);
            hubs[index].setObject(seq, asize, e);
            return true;
        } finally {
            consumerParkingQueue.signal();
        }
    }


    @Override public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        checkDurationValid(unit.toNanos(timeout));
        long start = System.nanoTime();
        long end = unit.toNanos(timeout) + start;
        long now;
        int cnt = 0;
        do {
            if (offer(e))
                return true;
            cnt += 1;
            if (spinPolicy.doSpin(cnt)) {
                doSpin();
            } else {
                now = System.nanoTime();
                producerParkingQueue.await(end - now);
            }
            checkInterrupted();
            now = System.nanoTime();
        } while (now < end);
        return false;
    }

    @Override public void put(E e) throws InterruptedException {
        int cnt = 0;
        do {
            if (offer(e))
                return;
            cnt += 1;
            if (spinPolicy.doSpin(cnt)) {
                doSpin();
            } else {
                producerParkingQueue.await();
            }
            checkInterrupted();
        } while (true);
    }

    @Override public E take() throws InterruptedException {
        int cnt = 0;
        do {
            E ret = poll();
            if (ret != null)
                return ret;
            cnt += 1;
            if (spinPolicy.doSpin(cnt)) {
                doSpin();
            } else {
                consumerParkingQueue.await();
            }
            checkInterrupted();
        } while (true);
    }

    @Override public int remainingCapacity() {
        int remain = size - size();
        if (remain < 0)
            remain = 0;
        if (remain > size)
            remain = size;
        return remain;
    }

    @Override public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override public E remove() {
        E ret = poll();
        if (ret == null) {
            throw new NoSuchElementException();
        }
        return ret;
    }

    @Override public boolean contains(Object o) {
        long start = head.getVolatile();
        long end = tail.getVolatile();
        for (long i = start; i < end; ++i) {
            int index = (int) (i & mask);
            if (hubs[index].guessEqualGet(i) == i) {
                // not be consumed
                if (hubs[index].e == o) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override public int drainTo(Collection<? super E> c) {
        E e = null;
        int cnt = 0;
        while ((e = poll()) != null) {
            c.add(e);
            cnt += 1;
        }
        return cnt;
    }

    @Override public int drainTo(Collection<? super E> c, int maxElements) {
        E e = null;
        int cnt = 0;
        while ((--maxElements) >= 0 && (e = poll()) != null) {
            c.add(e);
            cnt += 1;
        }
        return cnt;
    }

    /**
     * A long value container like AtomicLong,
     * with which padding bytes around it to keep a separated cache line.
     *
     * For performance purpose, Sequence provides multi level consistency read ability
     */
    @SuppressWarnings("restriction") private static class Sequence {
        @SuppressWarnings("unused")
        /**
         * Preceding cache-line padding
         */ protected long b0, b1, b2, b3, b4, b5, b6;
        protected long value;
        @SuppressWarnings("unused")
        /**
         * Following cache-line padding
         */ protected long f0, f1, f2, f3, f4, f5, f6;

        static final long INITIAL_VALUE = -1L;
        private static final long VALUE_OFFSET;

        static {
            try {
                VALUE_OFFSET = UnsafeUtil.unsafe.objectFieldOffset(Sequence.class.getDeclaredField("value"));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Sequence() {
            this(INITIAL_VALUE);
        }

        public Sequence(final long initialValue) {
            UnsafeUtil.unsafe.putOrderedLong(this, VALUE_OFFSET, initialValue);
        }

        /**
         * The value may be stale.
         *
         * @return
         */
        public long getRelaxed() {
            return value;
        }

        public long getVolatile() {
            return UnsafeUtil.unsafe.getLongVolatile(this, VALUE_OFFSET);
        }

        /**
         * Try to read the stale value from local-cache.
         * If the stale value is equal to guessed value, return it.
         * Otherwise re-read it from memory
         *
         * @param guessedValue
         * @return
         */
        long guessEqualGet(long guessedValue) {
            if (getRelaxed() == guessedValue)
                return guessedValue;
            return getVolatile();
        }

        /**
         * Try to read the stale value from local-cache.
         * If the stale value is larger than guessed value, then return it.
         * Otherwise re-read it from memory
         *
         * @param guessedValue
         * @return
         */
        long guessLargerGet(long guessedValue) {
            long ret;
            if ((ret = getRelaxed()) > guessedValue)
                return ret;
            return getVolatile();
        }

        public void setVolatile(final long value) {
            UnsafeUtil.getUnsafe().putLongVolatile(this, VALUE_OFFSET, value);
        }

        /**
         * Compare and set the value when the current is equal to expected
         *
         * @param value
         * @param expected
         * @return true if the current value has been changed to value
         */
        public boolean compareAndSet(final long expected, final long value) {
            return UnsafeUtil.getUnsafe().compareAndSwapLong(this, VALUE_OFFSET, expected, value);
        }

        public long incrementAndGet() {
            return addAndGet(1);
        }

        public long addAndGet(final long increment) {
            long currentValue;
            long newValue;

            do {
                currentValue = getVolatile();
                newValue = currentValue + increment;
            } while (!compareAndSet(currentValue, newValue));

            return newValue;
        }

        @Override public String toString() {
            return Long.toString(getVolatile());
        }
    }

    final private static void doSpin() {
        for (int i = 0; i < 100; ++i);
    }
}