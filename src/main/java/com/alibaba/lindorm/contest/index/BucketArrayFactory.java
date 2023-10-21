package com.alibaba.lindorm.contest.index;

import java.util.concurrent.atomic.AtomicInteger;

public class BucketArrayFactory {
    private final AggBucket[] aggBucketArray;

    private final AtomicInteger position = new AtomicInteger(0);

    public BucketArrayFactory(int bucketCount) {
        this.aggBucketArray = new AggBucket[bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            aggBucketArray[i] = new AggBucket();
        }
    }

    public AggBucket getAggBucket() {
        try {
            return aggBucketArray[position.getAndIncrement()];
        } catch (Exception e) {
            System.out.println("get aggBucket error position:" + position.get());
            System.exit(-1);
        }
        return null;
    }


}
