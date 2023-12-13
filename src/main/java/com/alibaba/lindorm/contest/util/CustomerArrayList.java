package com.alibaba.lindorm.contest.util;

import com.alibaba.lindorm.contest.memory.Value;

import java.util.*;

public class CustomerArrayList implements List<Value> {

    public Value[] getValues() {
        return values;
    }

    private Value[] values;

    private int position;

    private int size;

    public CustomerArrayList(int size) {
        this.size = size;
        this.values = new Value[size];
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public boolean isEmpty() {
        return position > 0;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @Override
    public Iterator<Value> iterator() {
        return new Iterator<Value>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i != size;
            }

            @Override
            public Value next() {
                return values[i++];
            }
        };
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(Value value) {
        values[position++] = value;
        return true;
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends Value> c) {
        return false;
    }

    @Override
    public boolean addAll(int index, Collection<? extends Value> c) {
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {
        this.values = new Value[size];
        this.position = 0;
    }

    @Override
    public Value get(int index) {
        return null;
    }

    @Override
    public Value set(int index, Value element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, Value element) {

    }

    @Override
    public Value remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        return 0;
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<Value> listIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<Value> listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Value> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    public static void main(String[] args) {
        List<Value> list = new CustomerArrayList(6);
        list.add(new Value(1L, new HashMap<>(1)));
        list.add(new Value(5L, new HashMap<>(1)));
        list.add(new Value(2L, new HashMap<>(1)));
        list.add(new Value(1L, new HashMap<>(1)));
        list.add(new Value(10L, new HashMap<>(1)));
        list.add(new Value(7, new HashMap<>(1)));
        for (Value value : list) {
            System.out.println(value.getTimestamp());
        }
        list.sort((v1, v2) -> (int) (v2.getTimestamp() - v1.getTimestamp()));
        for (Value value : list) {
            System.out.println(value.getTimestamp());
        }
    }
}
