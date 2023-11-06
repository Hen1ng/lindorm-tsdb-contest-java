package com.alibaba.lindorm.contest.index;

import com.alibaba.lindorm.contest.compress.GzipCompress;
import com.alibaba.lindorm.contest.compress.ZstdInner;
import com.alibaba.lindorm.contest.file.TSFileService;
import com.github.luben.zstd.Zstd;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class AggBucket {
    // 40 int 10 double
    // int : max sum 40 int + 40 double
    // double :  10 double + 10 double
    // all bytes : (40+60*2)*4=640B
    //
    int[] iMax, iMin;
    long[] iSum;
    double[]  dMax, dSum, dMin;

    public AggBucket() {
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
    }

    public byte[] bytes() {
        ByteBuffer allocate = ByteBuffer.allocate(80 * 4 + 70 * 8);
        for (int i = 0; i < 40; i++) {
            allocate.putInt(iMax[i]);
            allocate.putInt(iMin[i]);
            allocate.putLong(iSum[i]);
        }
        for (int i = 0; i < 10; i++) {
            allocate.putDouble(dMax[i]);
            allocate.putDouble(dMin[i]);
            allocate.putDouble(dSum[i]);
        }
//        GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
//        return gzipCompress.compress(allocate.array());
        return allocate.array();
    }

    public static AggBucket uncompress(byte[] bytes) {
        AggBucket aggBucket = new AggBucket();
//        GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
//        byte[] bytes1 = gzipCompress.deCompress(bytes);
        ByteBuffer wrap = ByteBuffer.wrap(bytes);
        for (int i = 0; i < 40; i++) {
            aggBucket.iMax[i] = wrap.getInt();
            aggBucket.iMin[i] = wrap.getInt();
            aggBucket.iSum[i] = wrap.getLong();
        }
        for (int i = 0; i < 10; i++) {
            aggBucket.dMax[i] = wrap.getDouble();
            aggBucket.dMin[i] = wrap.getDouble();
            aggBucket.dSum[i] = wrap.getDouble();
        }
        return aggBucket;
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

    public void updateInt(int value, int index) {
        iSum[index] += value;
        iMax[index] = Math.max(iMax[index], value);
        iMin[index] = Math.min(iMin[index], value);
    }

    public void updateDouble(double value, int index) {
        dSum[index - 40] += value;
        dMax[index - 40] = Math.max(dMax[index - 40], value);
        dMin[index - 40] = Math.min(dMin[index - 40], value);
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
            bucket.iSum[i] = Long.parseLong(iSumParts[i]);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AggBucket aggBucket = (AggBucket) o;
        return Arrays.equals(iMax, aggBucket.iMax) && Arrays.equals(iMin, aggBucket.iMin) && Arrays.equals(iSum, aggBucket.iSum) && Arrays.equals(dMax, aggBucket.dMax) && Arrays.equals(dSum, aggBucket.dSum) && Arrays.equals(dMin, aggBucket.dMin);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(iMax);
        result = 31 * result + Arrays.hashCode(iMin);
        result = 31 * result + Arrays.hashCode(iSum);
        result = 31 * result + Arrays.hashCode(dMax);
        result = 31 * result + Arrays.hashCode(dSum);
        result = 31 * result + Arrays.hashCode(dMin);
        return result;
    }

    public static void main(String[] args) {
        AggBucket aggBucket = new AggBucket();
        aggBucket.updateInt(1, 2);
        aggBucket.updateInt(4, 5);

        String string = aggBucket.toString();
        byte[] bytes = aggBucket.bytes();
        AggBucket uncompress = uncompress(bytes);
        boolean a = uncompress.equals(aggBucket);
        System.out.println(a);
    }
}
