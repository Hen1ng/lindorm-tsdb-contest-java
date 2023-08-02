package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.compress.GzipCompress;
import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.index.MapIndex;
import com.alibaba.lindorm.contest.memory.Value;
import com.alibaba.lindorm.contest.structs.ColumnValue;
import com.alibaba.lindorm.contest.structs.Row;
import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.ArrayUtils;
import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.SchemaUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;


public class TSFileService {

    private static final ThreadLocal<ByteBuffer> TOTAL_INT_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS * 4));
    private static final ThreadLocal<ByteBuffer> TOTAL_DOUBLE_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS * 8));
    private static final ThreadLocal<ByteBuffer> TOTAL_LONG_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * 8));
    private static final ThreadLocal<ByteBuffer> TOTAL_STRING_LENGTH_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * Constants.STRING_NUMS * 4));
    private static final ThreadLocal<ByteBuffer> INT_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocate(4));
    private static final ThreadLocal<ByteBuffer> DOUBLE_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocate(8));
    private static final ThreadLocal<ArrayList<ByteBuffer>> STRING_BUFFER_LIST = ThreadLocal.withInitial(() -> new ArrayList<>(Constants.CACHE_VINS_LINE_NUMS * Constants.STRING_NUMS));
    private static final ThreadLocal<ArrayList<Row>> LIST_THREAD_LOCAL = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<GzipCompress> GZIP_COMPRESS_THREAD_LOCAL = ThreadLocal.withInitial(GzipCompress::new);

    private final TSFile[] tsFiles;
    private final AtomicLong atomicLong = new AtomicLong(0);

    public TSFileService(String file, File indexFile) {
        this.tsFiles = new TSFile[Constants.TS_FILE_NUMS];
        for (int i = 0; i < Constants.TS_FILE_NUMS; i++) {
            long initPosition = (long) i * Constants.TS_FILE_SIZE;
            tsFiles[i] = new TSFile(file, i, initPosition);
//            if (RestartUtil.isFirstStart(indexFile)) {
//                tsFiles[i].warmTsFile();
//            }
        }
    }

    public TSFile getTsFileByVin(Vin vin) {
        final byte[] vin1 = vin.getVin();
        final int hash = getHashCodeByByteArray(vin1);
        int slot = hash % Constants.TS_FILE_NUMS;
        return tsFiles[slot];
    }

    public TSFile getTsFileByIndex(int i) {
        return tsFiles[i];
    }

    public int getHashCodeByByteArray(byte[] bytes) {
        int h = 0;
        for (int i = 0; i < 17; i++) {
            h = 31 * h + bytes[i];
        }
        return Math.abs(h);
    }

    public ArrayList<Row> getByIndex(Vin vin, long timeLowerBound, long timeUpperBound, Index index, Set<String> requestedColumns, int j) {
        ArrayList<Row> rowArrayList = LIST_THREAD_LOCAL.get();
        rowArrayList.clear();
        try {
            final long offset = index.getOffset();
            final int valueSize = index.getValueSize();
            final int length = index.getLength();
            int m = j % Constants.TS_FILE_NUMS;
            final TSFile tsFile = getTsFileByIndex(m);
            ByteBuffer timestampBuffer;
            ByteBuffer stringLengthBuffer = null;
            if (valueSize == Constants.CACHE_VINS_LINE_NUMS) {
                timestampBuffer = TOTAL_LONG_BUFFER.get();
                timestampBuffer.clear();
            } else {
                timestampBuffer = ByteBuffer.allocateDirect(valueSize * 8);
            }
            tsFile.getFromOffsetByFileChannel(timestampBuffer, offset);
            timestampBuffer.flip();
            byte[] stringBytes = null;
            int i = 0;//多少行
            while (timestampBuffer.remaining() > 0) {
                final long aLong = timestampBuffer.getLong();
                if (aLong >= timeLowerBound && aLong < timeUpperBound) {
                    Map<String, ColumnValue> columns = new HashMap<>(requestedColumns.size());
                    for (String requestedColumn : requestedColumns) {
                        final int columnIndex = SchemaUtil.getIndexByColumn(requestedColumn); //多少列
                        final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(requestedColumn);
                        if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_INTEGER)) {
                            try {
                                final ByteBuffer intBuffer = INT_BUFFER.get();
                                intBuffer.clear();
                                int off = (columnIndex * valueSize + i) * 4;
                                tsFile.getFromOffsetByFileChannel(intBuffer, offset + valueSize * 8 + off);
                                intBuffer.flip();
                                columns.put(requestedColumn, new ColumnValue.IntegerColumn(intBuffer.getInt()));
                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                            }
                        } else if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT)) {
                            try {
                                final ByteBuffer doubleBuffer = DOUBLE_BUFFER.get();
                                doubleBuffer.clear();
                                int off = valueSize * Constants.INT_NUMS * 4 + ((columnIndex - Constants.INT_NUMS) * valueSize + i) * 8;
                                tsFile.getFromOffsetByFileChannel(doubleBuffer, offset + valueSize * 8 + off);
                                doubleBuffer.flip();
                                columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubleBuffer.getDouble()));
                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                            }
                        } else {
                            if (stringLengthBuffer == null) {
                                if (valueSize == Constants.CACHE_VINS_LINE_NUMS) {
                                    stringLengthBuffer = TOTAL_STRING_LENGTH_BUFFER.get();
                                    stringLengthBuffer.clear();
                                } else {
                                    stringLengthBuffer = ByteBuffer.allocateDirect(valueSize * Constants.STRING_NUMS * 4);
                                }
                                tsFile.getFromOffsetByFileChannel(stringLengthBuffer, offset + valueSize * 8 + valueSize * Constants.INT_NUMS * 4 + valueSize * Constants.FLOAT_NUMS * 8);
                                stringLengthBuffer.flip();
                            }
                            if (stringBytes == null) {
                                int stringLength = length - (valueSize * 8 + valueSize * Constants.INT_NUMS * 4 + valueSize * Constants.FLOAT_NUMS * 8 + valueSize * Constants.STRING_NUMS * 4 );
                                final ByteBuffer stringBuffer = ByteBuffer.allocate(stringLength);
                                tsFile.getFromOffsetByFileChannel(stringBuffer, offset + valueSize * 8 + valueSize * Constants.INT_NUMS * 4 + valueSize * Constants.FLOAT_NUMS * 8 + valueSize * Constants.STRING_NUMS * 4);
                                stringBuffer.flip();
                                GzipCompress gzipCompress = GZIP_COMPRESS_THREAD_LOCAL.get();
                                stringBytes = gzipCompress.deCompress(stringBuffer.array());
                            }
                            try {
                                int stringNum = columnIndex - 54;
                                int stringPosition = (stringNum * valueSize + i) * 4;
                                int anInt = 0;
                                try {
                                    anInt = stringLengthBuffer.getInt(stringPosition);
                                } catch (Exception e) {
                                    System.out.println("getByIndex get string length error, e" + e);
                                }
                                if (anInt != 0) {
                                    int position = 0;
                                    try {
                                        for (int i1 = 0; i1 < stringPosition; i1 += 4) {
                                            position += stringLengthBuffer.getInt(i1);
                                        }
                                    } catch (Exception e) {
                                        System.out.println("getByIndex get string offset error, e" + e);
                                    }
                                    byte[] string = new byte[anInt];
                                    ArrayUtils.copy(stringBytes, position, string, 0, anInt);
                                    columns.put(requestedColumn, new ColumnValue.StringColumn(ByteBuffer.wrap(string)));
                                } else {
                                    columns.put(requestedColumn, new ColumnValue.StringColumn(ByteBuffer.allocate(0)));
                                }
                            } catch (Exception e) {
                                System.out.println("getByIndex time range String error, e:" + e + "index:" + index);
                            }
                        }
                    }
                    rowArrayList.add(new Row(vin, aLong, columns));
                }
                i++;
            }
        } catch (Exception e) {
            System.out.println("getByIndex error, e" + e);
        }
        return rowArrayList;

    }

    public Row getByIndex(Vin vin, long timestamp, Index index, Set<String> requestedColumns, int j) {
        final long offset = index.getOffset();
        final int valueSize = index.getValueSize();
        final int length = index.getLength();
        int m = j % Constants.TS_FILE_NUMS;
        final TSFile tsFile = getTsFileByIndex(m);
        ByteBuffer timestampBuffer;
        ByteBuffer stringLengthBuffer = null;
        if (valueSize == Constants.CACHE_VINS_LINE_NUMS) {
            timestampBuffer = TOTAL_LONG_BUFFER.get();
            timestampBuffer.clear();
        } else {
            timestampBuffer = ByteBuffer.allocateDirect(valueSize * 8);
        }
        tsFile.getFromOffsetByFileChannel(timestampBuffer, offset);
        timestampBuffer.flip();
        byte[] stringBytes = null;
        int i = 0;//多少行
        while (timestampBuffer.remaining() > 0) {
            final long aLong = timestampBuffer.getLong();
            if (aLong == timestamp) {
                Map<String, ColumnValue> columns = new HashMap<>(requestedColumns.size());
                for (String requestedColumn : requestedColumns) {
                    final int columnIndex = SchemaUtil.getIndexByColumn(requestedColumn); //多少列
                    final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(requestedColumn);
                    if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_INTEGER)) {
                        try {
                            final ByteBuffer intBuffer = INT_BUFFER.get();
                            intBuffer.clear();
                            int off = (columnIndex * valueSize + i) * 4;
                            tsFile.getFromOffsetByFileChannel(intBuffer, offset + valueSize * 8 + off);
                            intBuffer.flip();
                            columns.put(requestedColumn, new ColumnValue.IntegerColumn(intBuffer.getInt()));
                        } catch (Exception e) {
                            System.out.println("getByIndex COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                        }
                    } else if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT)) {
                        try {
                            final ByteBuffer doubleBuffer = DOUBLE_BUFFER.get();
                            doubleBuffer.clear();
                            int off = valueSize * Constants.INT_NUMS * 4 + ((columnIndex - Constants.INT_NUMS) * valueSize + i) * 8;
                            tsFile.getFromOffsetByFileChannel(doubleBuffer, offset + valueSize * 8 + off);
                            doubleBuffer.flip();
                            columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubleBuffer.getDouble()));
                        } catch (Exception e) {
                            System.out.println("getByIndex COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                        }
                    } else {
                        try {
                            if (stringLengthBuffer == null) {
                                if (valueSize == Constants.CACHE_VINS_LINE_NUMS) {
                                    stringLengthBuffer = TOTAL_STRING_LENGTH_BUFFER.get();
                                    stringLengthBuffer.clear();
                                } else {
                                    stringLengthBuffer = ByteBuffer.allocateDirect(valueSize * Constants.STRING_NUMS * 4);
                                }
                                tsFile.getFromOffsetByFileChannel(stringLengthBuffer, offset + valueSize * 8 + valueSize * Constants.INT_NUMS * 4 + valueSize * Constants.FLOAT_NUMS * 8);
                                stringLengthBuffer.flip();
                            }
                            if (stringBytes == null) {
                                int stringLength = length - (valueSize * 8 + valueSize * Constants.INT_NUMS * 4 + valueSize * Constants.FLOAT_NUMS * 8 + valueSize * Constants.STRING_NUMS * 4 );
                                final ByteBuffer stringBuffer = ByteBuffer.allocate(stringLength);
                                tsFile.getFromOffsetByFileChannel(stringBuffer, offset + valueSize * 8 + valueSize * Constants.INT_NUMS * 4 + valueSize * Constants.FLOAT_NUMS * 8 + valueSize * Constants.STRING_NUMS * 4);
                                stringBuffer.flip();
                                GzipCompress gzipCompress = GZIP_COMPRESS_THREAD_LOCAL.get();
                                stringBytes = gzipCompress.deCompress(stringBuffer.array());
                            }
                            int stringNum = columnIndex - 54;
                            int stringPosition = (stringNum * valueSize + i) * 4;
                            int anInt = 0;
                            try {
                                anInt = stringLengthBuffer.getInt(stringPosition);
                            } catch (Exception e) {
                                System.out.println("getByIndex get string length error, e" + e);
                            }
                            if (anInt != 0) {
                                int position = 0;
                                try {
                                    for (int i1 = 0; i1 < stringPosition; i1 += 4) {
                                        position += stringLengthBuffer.getInt(i1);
                                    }
                                } catch (Exception e) {
                                    System.out.println("getByIndex get string offset error, e" + e);
                                }
                                byte[] string = new byte[anInt];
                                ArrayUtils.copy(stringBytes, position, string, 0, anInt);
                                columns.put(requestedColumn, new ColumnValue.StringColumn(ByteBuffer.wrap(string)));
                            } else {
                                columns.put(requestedColumn, new ColumnValue.StringColumn(ByteBuffer.allocate(0)));
                            }
                        } catch (Exception e) {
                            System.out.println("getByIndex String error, e:" + e + "index:" + index);
                        }
                    }
                }
                return new Row(vin, aLong, columns);
            }
            i++;
        }
        return null;
    }

    /**
     * @param vin
     * @param valueList
     * @param lineNum
     */
    public void write(Vin vin, List<Value> valueList, int lineNum, int j) {
        try {
            final long andIncrement = atomicLong.getAndIncrement();
            if (andIncrement % 1000000 == 0) {
                System.out.println("write times:" + andIncrement);
            }
            int m = j % Constants.TS_FILE_NUMS;
            TSFile tsFile = getTsFileByIndex(m);
            String[] indexArray = SchemaUtil.getIndexArray();
            ByteBuffer intBuffer;
            ByteBuffer doubleBuffer;
            ByteBuffer longBuffer;
            ByteBuffer stringLengthBuffer;
            List<ByteBuffer> stringList;
            if (lineNum == Constants.CACHE_VINS_LINE_NUMS) {
                intBuffer = TOTAL_INT_BUFFER.get();
                doubleBuffer = TOTAL_DOUBLE_BUFFER.get();
                longBuffer = TOTAL_LONG_BUFFER.get();
                stringLengthBuffer = TOTAL_STRING_LENGTH_BUFFER.get();
                stringList = STRING_BUFFER_LIST.get();
                intBuffer.clear();
                doubleBuffer.clear();
                longBuffer.clear();
                stringLengthBuffer.clear();
                stringList.clear();
            } else {
                //存储int类型，大小为缓存数据行 * 每行多少个int * 4
                intBuffer = ByteBuffer.allocateDirect(lineNum * Constants.INT_NUMS * 4);
                //存储double类型，大小为缓存数据行 * 每行多少个double * 8
                doubleBuffer = ByteBuffer.allocateDirect(lineNum * Constants.FLOAT_NUMS * 8);
                //存储时间戳，总共有多少行，有多少个时间戳
                longBuffer = ByteBuffer.allocateDirect(lineNum * 8);
                //存储每个字符串的长度
                stringLengthBuffer = ByteBuffer.allocateDirect(lineNum * Constants.STRING_NUMS * 4);
                stringList = new ArrayList<>(lineNum * Constants.STRING_NUMS);
            }
            int totalStringLength = 0;
            long maxTimestamp = Long.MIN_VALUE;
            long minTimestamp = Long.MAX_VALUE;
            for (int i = 0; i < indexArray.length; i++) {
                final String key = indexArray[i];
                for (Value value : valueList) {
                    long timestamp = value.getTimestamp();
                    maxTimestamp = Math.max(maxTimestamp, timestamp);
                    minTimestamp = Math.min(minTimestamp, timestamp);
                    if (i == 0) {
                        longBuffer.putLong(value.getTimestamp());
                    }
                    Map<String, ColumnValue> columns = value.getColumns();
                    if (i < 45) {
                        int integerValue = columns.get(key).getIntegerValue();
                        intBuffer.putInt(integerValue);
                    } else if (i < 54) {
                        final double doubleFloatValue = columns.get(key).getDoubleFloatValue();
                        doubleBuffer.putDouble(doubleFloatValue);
                    } else {
                        final ByteBuffer stringValue = columns.get(key).getStringValue();
                        totalStringLength += stringValue.remaining();
                        stringList.add(stringValue);
                        stringLengthBuffer.putInt(stringValue.remaining());
                    }
                }
            }
            byte[] bytes = new byte[totalStringLength];
            int position = 0;
            for (ByteBuffer buffer : stringList) {
                final byte[] array = buffer.array();
                ArrayUtils.copy(array, 0, bytes, position, array.length);
                position += array.length;
            }
            final GzipCompress gzipCompress = GZIP_COMPRESS_THREAD_LOCAL.get();
            final byte[] compress = gzipCompress.compress(bytes);
//            System.out.println("write compress, before length:" + bytes.length + "after length: " + compress.length);
            int total = lineNum * 8 //timestamp
                    + lineNum * Constants.INT_NUMS * 4 //int
                    + lineNum * Constants.FLOAT_NUMS * 8 //double
                    + lineNum * Constants.STRING_NUMS * 4 //string长度记录
                    + compress.length; //string存储string
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(total);
            longBuffer.flip();
            byteBuffer.put(longBuffer.slice());
            intBuffer.flip();
            byteBuffer.put(intBuffer.slice());
            doubleBuffer.flip();
            byteBuffer.put(doubleBuffer.slice());
            stringLengthBuffer.flip();
            byteBuffer.put(stringLengthBuffer);
            byteBuffer.put(compress);
            final long append = tsFile.append(byteBuffer);
            final Index index = new Index(append
                    , maxTimestamp
                    , minTimestamp
                    , total
                    , lineNum);
            MapIndex.put(vin, index);
            valueList.clear();
        } catch (Exception e) {
            System.out.println("write to file error, e" + e.getLocalizedMessage());
        }
    }

    public TSFile[] getTsFiles() {
        return tsFiles;
    }

}
