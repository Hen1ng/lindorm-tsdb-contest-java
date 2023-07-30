//package com.alibaba.lindorm.contest.util;
//
//
//import java.lang.invoke.CallSite;
//import java.lang.invoke.LambdaMetafactory;
//import java.lang.invoke.MethodHandle;
//import java.lang.invoke.MethodHandles.Lookup;
//import java.lang.invoke.MethodType;
//import java.lang.reflect.Field;
//import java.lang.reflect.Method;
//import sun.misc.Unsafe;
//
//public class _JDKAccess {
//  // CHECKSTYLE.OFF:TypeName
//  public static final int JAVA_VERSION;
//  public static final boolean OPEN_J9;
//  public static final Unsafe UNSAFE;
//  public static final Class<?> _INNER_UNSAFE_CLASS;
//  public static final Object _INNER_UNSAFE;
//
//  static {
//    String property = System.getProperty("java.specification.version");
//    if (property.startsWith("1.")) {
//      property = property.substring(2);
//    }
//    String jmvName = System.getProperty("java.vm.name", "");
//    OPEN_J9 = jmvName.contains("OpenJ9");
//    JAVA_VERSION = Integer.parseInt(property);
//
//    Unsafe unsafe;
//    try {
//      Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
//      unsafeField.setAccessible(true);
//      unsafe = (Unsafe) unsafeField.get(null);
//    } catch (Throwable cause) {
//      throw new UnsupportedOperationException("Unsafe is not supported in this platform.");
//    }
//    UNSAFE = unsafe;
//    if (JAVA_VERSION >= 11) {
//      try {
//        Field theInternalUnsafeField = Unsafe.class.getDeclaredField("theInternalUnsafe");
//        theInternalUnsafeField.setAccessible(true);
//        _INNER_UNSAFE = theInternalUnsafeField.get(null);
//        _INNER_UNSAFE_CLASS = _INNER_UNSAFE.getClass();
//      } catch (Exception e) {
//        throw new RuntimeException(e);
//      }
//    } else {
//      _INNER_UNSAFE_CLASS = null;
//      _INNER_UNSAFE = null;
//    }
//  }
//
//  public static void main(String[] args) {
//    final Unsafe unsafe = _JDKAccess.UNSAFE;
//  }
//
//
//
//}