package com.alibaba.lindorm.contest.index;

import java.nio.ByteBuffer;

public class AggBucket {
    // 40 int 10 double
    // int : max sum 40 int + 40 double
    // double :  10 double + 10 double
    // all bytes : (40+60*2)*4=640B
    //
    int[] iMax,iMin;
    double[] iSum,dMax,dSum,dMin;
    public AggBucket(){
        iMax = new int[40];
        iSum = new double[40];
        iMin = new int[40];
        dMax = new double[10];
        dSum = new double[10];
        dMin = new double[10];
        for(int i=0;i<40;i++){
            if(i<10){
                dMax[i] = -Double.MAX_VALUE;
                dMin[i] = Double.MAX_VALUE;
                dSum[i]=0;
            }
            iMax[i] = Integer.MIN_VALUE;
            iSum[i] = 0;
            iMin[i] = Integer.MAX_VALUE;
        }
    }

    public int getiMin(int index){return iMin[index];}
    public double getdMin(int index){return dMin[index-40];}
    public int getiMax(int index) {
        return iMax[index];
    }
    public double getdMax(int index){
        return dMax[index-40];
    }
    public double getiSum(int index){
        return iSum[index];
    }
    public  double getdSum(int index){
        return dSum[index-40];
    }

    public void updateInt(int value, int index){
        iSum[index] += value;
        iMax[index] = Math.max(iMax[index],value);
        iMin[index] = Math.min(iMin[index],value);
    }
    public void updateDouble(double value,int index){
        dSum[index-40] += value;
        dMax[index-40] = Math.max(dMax[index-40],value);
        dMin[index-40] = Math.min(dMin[index-40],value);
    }
    // 转换为 ByteBuffer
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Convert iMax array to string
        for (int i : iMax) {
            sb.append(i).append("|");
        }

        sb.append(",");
        // Convert iMax array to string
        for (int i : iMin) {
            sb.append(i).append("|");
        }

        sb.append(",");

        // Convert iSum array to string
        for (double d : iSum) {
            sb.append(d).append("|");
        }

        sb.append(",");

        // Convert dMax array to string
        for (double d : dMax) {
            sb.append(d).append("|");
        }

        sb.append(",");

        // Convert dSum array to string
        for (double d : dSum) {
            sb.append(d).append("|");
        }
        sb.append(",");
        // Convert dSum array to string
        for (double d : dMin) {
            sb.append(d).append("|");
        }

        return sb.toString();
    }

    // 从 ByteBuffer 中读取并构建对象
    public static AggBucket fromString(String str) {
        AggBucket bucket = new AggBucket();
        String[] parts = str.split(",");

        // Parse iMax array
        String[] iMaxParts = parts[0].split("\\|");
        for (int i = 0; i < iMaxParts.length; i++) {
            bucket.iMax[i] = Integer.parseInt(iMaxParts[i]);
        }
        // Parse iMax array
        String[] iMinParts = parts[1].split("\\|");
        for (int i = 0; i < iMinParts.length; i++) {
            bucket.iMin[i] = Integer.parseInt(iMaxParts[i]);
        }

        // Parse iSum array
        String[] iSumParts = parts[2].split("\\|");
        for (int i = 0; i < iSumParts.length; i++) {
            bucket.iSum[i] = Double.parseDouble(iSumParts[i]);
        }

        // Parse dMax array
        String[] dMaxParts = parts[3].split("\\|");
        for (int i = 0; i < dMaxParts.length; i++) {
            bucket.dMax[i] = Double.parseDouble(dMaxParts[i]);
        }

        // Parse dSum array
        String[] dSumParts = parts[4].split("\\|");
        for (int i = 0; i < dSumParts.length; i++) {
            bucket.dSum[i] = Double.parseDouble(dSumParts[i]);
        }
        // Parse dSum array
        String[] dMinParts = parts[5].split("\\|");
        for (int i = 0; i < dMinParts.length; i++) {
            bucket.dMin[i] = Double.parseDouble(dSumParts[i]);
        }
        return bucket;
    }

    public static void main(String[] args) {
        AggBucket aggBucket = new AggBucket();
        aggBucket.updateInt(1,2);
        String string = aggBucket.toString();
        System.out.println(aggBucket.toString());
        AggBucket aggBucket1 = AggBucket.fromString(string);
        System.out.println(aggBucket1);
    }
}
