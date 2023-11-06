package com.alibaba.lindorm.contest.util;//package com.alibaba.lindorm.contest.util;
//
//import java.lang.reflect.Field;
//import java.lang.reflect.Method;
//import java.nio.Buffer;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.security.AccessController;
//import java.security.PrivilegedAction;
//
//public class InvokeUtil {
//
//    public static void clean(final ByteBuffer buffer) {
//        if (buffer == null || !buffer.isDirect() || buffer.capacity() == 0) {
//            return;
//        }
//        invoke(invoke(viewed(buffer), "cleaner"), "clean");
//    }
//
//    private static Object invoke(final Object target, final String methodName, final Class<?>... args) {
//        return AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
//            try {
//                Method method = method(target, methodName, args);
//                method.setAccessible(true);
//                return method.invoke(target);
//            } catch (Exception e) {
//                throw new IllegalStateException(e);
//            }
//        });
//    }
//
//    private static Method method(Object target, String methodName, Class<?>[] args)
//            throws NoSuchMethodException {
//        try {
//            return target.getClass().getMethod(methodName, args);
//        } catch (NoSuchMethodException e) {
//            return target.getClass().getDeclaredMethod(methodName, args);
//        }
//    }
//
//    private static ByteBuffer viewed(ByteBuffer buffer) {
//        String methodName = "viewedBuffer";
//        Method[] methods = buffer.getClass().getMethods();
//        for (int i = 0; i < methods.length; i++) {
//            if (methods[i].getName().equals("attachment")) {
//                methodName = "attachment";
//                break;
//            }
//        }
//
//        ByteBuffer viewedBuffer = (ByteBuffer) invoke(buffer, methodName);
//        if (viewedBuffer == null) {
//            return buffer;
//        } else {
//            return viewed(viewedBuffer);
//        }
//    }
//
//    private static Field addr;
//    private static Field capacity;
//
//    static {
//        try {
//            addr = Buffer.class.getDeclaredField("address");
//            addr.setAccessible(true);
//            capacity = Buffer.class.getDeclaredField("capacity");
//            capacity.setAccessible(true);
//        } catch (NoSuchFieldException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public static ByteBuffer newFastByteBuffer(int cap) {
//        long address = UnsafeUtil.getUnsafe().allocateMemory(cap);
//        ByteBuffer bb = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
//        try {
//            addr.setLong(bb, address);
//            capacity.setInt(bb, cap);
//        } catch (IllegalAccessException e) {
//            return null;
//        }
//        bb.clear();
//        return bb;
//    }
//}
