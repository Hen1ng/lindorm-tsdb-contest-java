package com.alibaba.lindorm.contest.index;

import java.util.ArrayList;
import java.util.List;

public class BigBucket {

    List<Index> indexList;
    private long maxTimestamp;
    private long minTimestamp;
    int[] iMax, iMin;
    long[] iSum;
    double[] dMax, dSum, dMin;
    int valueSize = 0;

    public BigBucket() {
        indexList = new ArrayList<>();
        iMax = new int[40];
        iSum = new long[40];
        iMin = new int[40];
        dMax = new double[10];
        dSum = new double[10];
        dMin = new double[10];
        for (int i = 0; i < 40; i++) {
            if (i < 10) {
                dMax[i] = -Double.MAX_VALUE;
                dMin[i] = Double.MAX_VALUE;
                dSum[i] = 0;
            }
            iMax[i] = Integer.MIN_VALUE;
            iSum[i] = 0;
            iMin[i] = Integer.MAX_VALUE;
        }
        minTimestamp = -1;
        maxTimestamp = -1;
    }

    public List<Index> getIndexList() {
        return indexList;
    }

    public void setIndexList(List<Index> indexList) {
        this.indexList = indexList;
    }

    public long getMaxTimestamp() {
        return maxTimestamp;
    }

    public void setMaxTimestamp(long maxTimestamp) {
        this.maxTimestamp = maxTimestamp;
    }

    public long getMinTimestamp() {
        return minTimestamp;
    }

    public void setMinTimestamp(long minTimestamp) {
        this.minTimestamp = minTimestamp;
    }

    public int getIndexSize() {
        return indexList.size();
    }

    public void addBucket(Index index) {
        valueSize += index.getValueSize();
        indexList.add(index);
        if(minTimestamp == -1){
            minTimestamp = index.getMinTimestamp();
        }else{
            minTimestamp = Math.min(minTimestamp,index.getMinTimestamp());
        }
        if(maxTimestamp == -1){
            maxTimestamp = index.getMaxTimestamp();
        }else {
            maxTimestamp = Math.max(maxTimestamp, index.getMaxTimestamp());
        }
        for (int i = 0; i < 40; i++) {
            if (i < 10) {
                dMax[i] = Math.max(dMax[i], index.getAggBucket().getdMax(i + 40));
                dMin[i] = Math.min(dMin[i], index.getAggBucket().getdMin(i + 40));
                dSum[i] += index.getAggBucket().getdSum(i + 40);
            }
            iMax[i] = Math.max(iMax[i], index.getAggBucket().getiMax(i));
            iSum[i] += index.getAggBucket().getiSum(i);
            iMin[i] = Math.max(iMin[i], index.getAggBucket().getiMin(i));
        }
    }

    public int getValueSize() {
        return valueSize;
    }

    public void setValueSize(int valueSize) {
        this.valueSize = valueSize;
    }

    public int getiMin(int index) {
        return iMin[index];
    }

    public double getdMin(int index) {
        return dMin[index - 40];
    }

    public int getiMax(int index) {
        return iMax[index];
    }

    public double getdMax(int index) {
        return dMax[index - 40];
    }

    public long getiSum(int index) {
        return iSum[index];
    }

    public double getdSum(int index) {
        return dSum[index - 40];
    }
}
