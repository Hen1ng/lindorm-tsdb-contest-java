package com.alibaba.lindorm.contest.index;


import com.alibaba.lindorm.contest.compress.GzipCompress;
import com.alibaba.lindorm.contest.file.TSFileService;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class Index {

    private long offset;
    private long maxTimestamp;
    private long minTimestamp;

    private AggBucket aggBucket;

    private int bigIntOffset;




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

    public int getOffsetLine() {
        return offsetLine;
    }

    public long getMaxTimestamp() {
        return maxTimestamp;
    }

    public long getMinTimestamp() {
        return minTimestamp;
    }

    public int getLength() {
        return length;
    }

    private int length;

    private int offsetLine;

    public int getDoubleLine() {
        return doubleLine;
    }

    private int doubleLine;


    public Index(long offset
            , long maxTimestamp
            , long minTimestamp
            , int length
            , int valueSize
            , int offsetLine
            , int doubleLine
            , AggBucket aggBucket
//            , DoubleIndexMap doubleIndexMap
    ) {
        this.offset = offset;
        this.maxTimestamp = maxTimestamp;
        this.minTimestamp = minTimestamp;
        this.length = length;
        this.valueSize = valueSize;
        this.offsetLine = offsetLine;
        this.doubleLine = doubleLine;
        this.aggBucket = aggBucket;
//        this.doubleIndexMap = doubleIndexMap;
    }

    public byte[] bytes(){
        byte[] bytes = aggBucket.bytes();
        ByteBuffer allocate = ByteBuffer.allocate(4+bytes.length + 8 * 3 + 4 * 5);
        allocate.putInt(bytes.length);
        allocate.put(bytes);
        allocate.putLong(offset);
        allocate.putLong(maxTimestamp);
        allocate.putLong(minTimestamp);
        allocate.putInt(bigIntOffset);
        allocate.putInt(valueSize);
        allocate.putInt(length);
        allocate.putInt(offsetLine);
        allocate.putInt(doubleLine);
        GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
        return gzipCompress.compress(allocate.array());
    }
    public static Index uncompress(byte[] bytes){
        GzipCompress gzipCompress = TSFileService.GZIP_COMPRESS_THREAD_LOCAL.get();
        bytes = gzipCompress.deCompress(bytes);
        ByteBuffer wrap = ByteBuffer.wrap(bytes);
        int aggBucketLength = wrap.getInt();
        byte[] aggBytes = new byte[aggBucketLength];
        wrap.get(aggBytes,0,aggBucketLength);
        AggBucket aggBucket = AggBucket.uncompress(aggBytes);
        long offset = wrap.getLong();
        long maxTimeStamp = wrap.getLong();
        long minTimeStamp = wrap.getLong();
        int bigIntOffset = wrap.getInt();
        int valueSize = wrap.getInt();
        int length = wrap.getInt();
        int offsetLine = wrap.getInt();
        int doubleLine = wrap.getInt();
        return new Index(
                offset,
                maxTimeStamp,
                minTimeStamp,
                length,
                valueSize,
                offsetLine,
                doubleLine,
                aggBucket
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
                offsetLine +
                "@" +
                doubleLine +
                "@" +
                aggBucket.toString()
                ;

    }

    public int getBigIntOffset() {
        return bigIntOffset;
    }

    public void setBigIntOffset(int offset) {
        this.bigIntOffset = offset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Index index = (Index) o;
        return offset == index.offset && maxTimestamp == index.maxTimestamp && minTimestamp == index.minTimestamp && bigIntOffset == index.bigIntOffset && valueSize == index.valueSize && length == index.length && offsetLine == index.offsetLine && doubleLine == index.doubleLine && Objects.equals(aggBucket, index.aggBucket);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, maxTimestamp, minTimestamp, aggBucket, bigIntOffset, valueSize, length, offsetLine, doubleLine);
    }

    public static void main(String[] args) {
        Random random = new Random();
        AggBucket aggBucket1 = new AggBucket();
        aggBucket1.updateInt(1,1);
        aggBucket1.updateInt(2,2);
        Index index = new Index(random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                aggBucket1);
        byte[] bytes = index.bytes();
        Index index1 = uncompress(bytes);
        boolean a = index1.equals(index);
        System.out.println(a);

    }

}
