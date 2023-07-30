package com.alibaba.lindorm.contest.util;


import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeUtil {
    public static final Unsafe UNSAFE;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static Unsafe getUnsafe() {
        return UNSAFE;
    }



    public static void main(String[] args) {
        getUnsafe();
    }

}