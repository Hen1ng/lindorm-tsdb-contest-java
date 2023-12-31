package com.alibaba.lindorm.contest.index;


import com.alibaba.lindorm.contest.compress.IntCompress;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public class Index {

    private long offset;
    private long maxTimestamp;
    private long minTimestamp;

    public long getIntOffset() {
        return intOffset;
    }

    public void setIntOffset(long intOffset) {
        this.intOffset = intOffset;
    }

    private long intOffset;

    private int intLength;
    private int doubleLength;

    private long previousTimeStamp;

    private byte[] timeStampBytes;

    private byte[] doubleHeader;

    public IntCompress.IntCompressResult getIntCompressResult() {
        return intCompressResult;
    }

    public void setIntCompressResult(IntCompress.IntCompressResult intCompressResult) {
        this.intCompressResult = intCompressResult;
    }

    private IntCompress.IntCompressResult intCompressResult;

    public byte[] getDoubleHeader() {
        return doubleHeader;
    }

    public void setDoubleHeader(byte[] doubleHeader) {
        this.doubleHeader = doubleHeader;
    }

    public int getIntLength() {
        return intLength;
    }

    public void setIntLength(int intLength) {
        this.intLength = intLength;
    }

    public int getDoubleLength() {
        return doubleLength;
    }

    public void setDoubleLength(int doubleLength) {
        this.doubleLength = doubleLength;
    }

    public void setAggBucket(AggBucket aggBucket) {
        this.aggBucket = aggBucket;
    }

    private AggBucket aggBucket;

    public int getValueSize() {
        return valueSize;
    }

    private int valueSize;

    public AggBucket getAggBucket() {
        return aggBucket;
    }

    public long getOffset() {
        return offset;
    }

    public long getMaxTimestamp() {
        return maxTimestamp;
    }

    public long getPreviousTimeStamp() {
        return previousTimeStamp;
    }

    public void setPreviousTimeStamp(long previousTimeStamp) {
        this.previousTimeStamp = previousTimeStamp;
    }

    public byte[] getTimeStampBytes() {
        return timeStampBytes;
    }

    public void setTimeStampBytes(byte[] timeStampBytes) {
        this.timeStampBytes = timeStampBytes;
    }

    public long getMinTimestamp() {
        return minTimestamp;
    }

    public int getLength() {
        return length;
    }

    private int length;

    public long getBigStringOffset() {
        return bigStringOffset;
    }

    private long bigStringOffset;

    public int getBigStringLength() {
        return bigStringLength;
    }

    public void setBigStringLength(int bigStringLength) {
        this.bigStringLength = bigStringLength;
    }

    private int bigStringLength;


    public Index(long offset
            , long intOffset
            , long maxTimestamp
            , long minTimestamp
            , int length
            , int valueSize
            , AggBucket aggBucket
            , int intLength
            , int doubleLength
            , long previousTimeStamp
            , byte[] timeStampBytes
            , long bigStringOffset
            , byte[] doubleHeader
            , IntCompress.IntCompressResult intCompressResult
            , int bigStringLength
    ) {
        this.offset = offset;
        this.intOffset = intOffset;
        this.maxTimestamp = maxTimestamp;
        this.minTimestamp = minTimestamp;
        this.length = length;
        this.valueSize = valueSize;
        this.aggBucket = aggBucket;
        this.intLength = intLength;
        this.doubleLength = doubleLength;
        this.previousTimeStamp = previousTimeStamp;
        this.timeStampBytes = timeStampBytes;
        this.bigStringOffset = bigStringOffset;
        this.doubleHeader = doubleHeader;
        this.intCompressResult = intCompressResult;
        this.bigStringLength = bigStringLength;
    }

    public byte[] bytes() {

        byte[] bytes = new byte[0];
        if (aggBucket != null) {
            bytes = aggBucket.bytes();
        }
//        final byte[] bytes1 = intCompressResult.bytes();
        ByteBuffer allocate = ByteBuffer.allocate(4 + bytes.length + 8 * 4 + 4 * 6 + 8 + 8 + 2 + timeStampBytes.length + 2 + doubleHeader.length );
        allocate.putInt(bytes.length);
        allocate.put(bytes);
        allocate.putLong(offset);
        allocate.putLong(intOffset);
        allocate.putLong(maxTimestamp);
        allocate.putLong(minTimestamp);
        allocate.putInt(valueSize);
        allocate.putInt(length);
        allocate.putInt(intLength);
        allocate.putInt(doubleLength);
        allocate.putInt(bigStringLength);
        allocate.putLong(bigStringOffset);
        allocate.putLong(previousTimeStamp);
        allocate.putShort((short) timeStampBytes.length);
        allocate.put(timeStampBytes);
        allocate.putShort((short) doubleHeader.length);
        allocate.put(doubleHeader);
        return allocate.array();
//        GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
//        return gzipCompress.compress(allocate.array());
    }

    public static Index uncompress(byte[] bytes) {
//        GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
//        bytes = gzipCompress.deCompress(bytes);
        ByteBuffer wrap = ByteBuffer.wrap(bytes);
        int aggBucketLength = wrap.getInt();
        AggBucket aggBucket;
        if (aggBucketLength == 0) {
            aggBucket = null;
        } else {
            byte[] aggBytes = new byte[aggBucketLength];
            wrap.get(aggBytes, 0, aggBucketLength);
            aggBucket = AggBucket.uncompress(aggBytes);
        }
        long offset = wrap.getLong();
        long intOffset = wrap.getLong();
        long maxTimeStamp = wrap.getLong();
        long minTimeStamp = wrap.getLong();
        int valueSize = wrap.getInt();
        int length = wrap.getInt();
        int intLength = wrap.getInt();
        int doubleLength = wrap.getInt();
        int bigStringLength = wrap.getInt();
        final long bigStringOffset = wrap.getLong();
        long previousTimeStamp = wrap.getLong();
        short timeStampBytesLength = wrap.getShort();
        byte[] bytes1 = new byte[timeStampBytesLength];
        wrap.get(bytes1, 0, bytes1.length);
        short doubleHeaderLength = wrap.getShort();
        byte[] doubleHeader = new byte[doubleHeaderLength];
        wrap.get(doubleHeader, 0, doubleHeader.length);

        return new Index(
                offset,
                intOffset,
                maxTimeStamp,
                minTimeStamp,
                length,
                valueSize,
                aggBucket,
                intLength,
                doubleLength,
                previousTimeStamp,
                bytes1,
                bigStringOffset,
                doubleHeader,
                null,
                bigStringLength
        );
    }

    @Override
    public String toString() {
        return offset +
                "@" +
                maxTimestamp +
                "@" +
                minTimestamp +
                "@" +
                length +
                "@" +
                valueSize +
                "@" +
                aggBucket.toString()
                ;

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Index index = (Index) o;
        return offset == index.offset && maxTimestamp == index.maxTimestamp && minTimestamp == index.minTimestamp && intLength == index.intLength && doubleLength == index.doubleLength && previousTimeStamp == index.previousTimeStamp && valueSize == index.valueSize && length == index.length && Arrays.equals(timeStampBytes, index.timeStampBytes) && Objects.equals(aggBucket, index.aggBucket);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, maxTimestamp, minTimestamp, aggBucket, valueSize, length);
    }

    public static void main(String[] args) {
//        Random random = new Random();
//        AggBucket aggBucket1 = new AggBucket();
//        aggBucket1.updateInt(1, 1);
//        aggBucket1.updateInt(2, 2);
//        Index index = new Index(random.nextLong(),
//                random.nextLong(),
//                random.nextLong(),
//                random.nextInt(),
//                random.nextInt(),
//                aggBucket1,
//                random.nextInt(),
//                random.nextInt(),
//                random.nextLong(),
//                new byte[0]);
//        byte[] bytes = index.bytes();
//        Index index1 = uncompress(bytes);
//        boolean a = index1.equals(index);
//        System.out.println(a);

    }

}
