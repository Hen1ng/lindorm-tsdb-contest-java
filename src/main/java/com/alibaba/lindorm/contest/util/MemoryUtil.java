package com.alibaba.lindorm.contest.util;


import java.lang.management.*;
import java.util.List;

/**
 * @author hening
 */
public class MemoryUtil {

    public static void printMemory() {
        printJVMHeapMemory();
        printRuntimeMemory();
        printGCInfo();
        printMemory2();
    }

    public static void printFreeMemory() {
        long free = Runtime.getRuntime().freeMemory() / 1024 / 1024;
        long total = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        long max = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        System.out.printf("freeMemory:%s M, totalMemory:%s, maxMemory:%s%n", free, total, max);
    }

    public static void main(String[] args) {
        printMemory2();
    }

    public static void printMemory2() {
        System.out.println("=======================printMemory2============================ ");
        printFreeMemory();
        System.out.println("getDirectBufferPoolMBean: " + getDirectBufferPoolMBean().getMemoryUsed() / 1024.0 + " M");
    }


    public static BufferPoolMXBean getDirectBufferPoolMBean() {
        return ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
                .stream()
                .filter(e -> e.getName().equals("direct"))
                .findFirst().get();
    }

    public static void printJVMHeapMemory() {
        System.out.println("=======================MemoryMXBean============================ ");
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage usage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemoryUsage = memoryBean.getNonHeapMemoryUsage();
        System.out.println("INIT HEAP: " +  byteToM(usage.getInit())+ " M");
        System.out.println("MAX HEAP: " + byteToM(usage.getMax())+ " M");
        System.out.println("USE HEAP: " + byteToM(usage.getUsed())+ " M");

//        System.out.println("INIT NON HEAP: " + byteToM(nonHeapMemoryUsage.getInit())+ " M");
//        System.out.println("MAX NON HEAP: " + byteToM(nonHeapMemoryUsage.getMax())+ " M");
//        System.out.println("USE NON HEAP: " + byteToM(nonHeapMemoryUsage.getUsed())+ " M");


    }

    public static void printRuntimeMemory() {
        System.out.println("=======================Runtime============================ ");
        int i = (int) Runtime.getRuntime().totalMemory() / 1024 / 1024;
        System.out.println("total memory is " + i + " M");
        int j = (int) Runtime.getRuntime().freeMemory() / 1024 / 1024;
        System.out.println("free memory is " + j + " M");
        System.out.println("max memory is " + Runtime.getRuntime().maxMemory() / 1024 / 1024);
    }



    public static void printSystemMemory() {
        System.out.println("=======================OperatingSystemMXBean============================ ");
        OperatingSystemMXBean osm = ManagementFactory.getOperatingSystemMXBean();
        System.out.println("osm.getArch() " + osm.getArch());
        System.out.println("osm.getAvailableProcessors() " + osm.getAvailableProcessors());
        System.out.println("osm.getName() " + osm.getName());
        System.out.println("osm.getVersion() " + osm.getVersion());

    }

    public static void printThreadInfo() {
        System.out.println("=======================ThreadMXBean============================ ");
        ThreadMXBean tm = ManagementFactory.getThreadMXBean();
        System.out.println("getThreadCount " + tm.getThreadCount());
        System.out.println("getPeakThreadCount " + tm.getPeakThreadCount());
        System.out.println("getCurrentThreadCpuTime " + tm.getCurrentThreadCpuTime());
        System.out.println("getDaemonThreadCount " + tm.getDaemonThreadCount());
        System.out.println("getCurrentThreadUserTime " + tm.getCurrentThreadUserTime());

    }

    public static void printGCInfo() {
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean garbageCollectorMXBean : garbageCollectorMXBeans) {
            System.out.println("name:" + garbageCollectorMXBean.getName());
            System.out.println("collection count :" + garbageCollectorMXBean.getCollectionCount());
            System.out.println("collection time :" + garbageCollectorMXBean.getCollectionTime());
        }
    }

    public static long byteToM(long b) {
        return b / 1024 / 1024;
    }
}
