package com.alibaba.lindorm.contest.util;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

public class GCUtil {
    /**
     * 打印gc日志
     */
    public static void printGCInfo() {
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean garbageCollectorMXBean : garbageCollectorMXBeans) {
            System.out.println("name:" + garbageCollectorMXBean.getName());
            System.out.println("collection count :" + garbageCollectorMXBean.getCollectionCount());
            System.out.println("collection time :" + garbageCollectorMXBean.getCollectionTime() / 1000 + " s");
        }
    }
}
