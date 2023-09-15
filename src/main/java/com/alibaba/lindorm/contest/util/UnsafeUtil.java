package com.alibaba.lindorm.contest.util;


import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeUtil {
    public static final Unsafe unsafe;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static Unsafe getUnsafe() {
        return unsafe;
    }



    public static void main(String[] args) {
        getUnsafe();
    }

}