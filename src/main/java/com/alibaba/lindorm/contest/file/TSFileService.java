package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.index.MapIndex;
import com.alibaba.lindorm.contest.memory.Value;
import com.alibaba.lindorm.contest.structs.ColumnValue;
import com.alibaba.lindorm.contest.structs.Row;
import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.SchemaUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;


public class TSFileService {

    private static ThreadLocal<ByteBuffer> TOTAL_INT_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS * 4));
    private static ThreadLocal<ByteBuffer> TOTAL_DOUBLE_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * Constants.INT_NUMS * 8));
    private static ThreadLocal<ByteBuffer> TOTAL_LONG_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * 8));
    private static ThreadLocal<ByteBuffer> TOTAL_STRING_LENGTH_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.CACHE_VINS_LINE_NUMS * Constants.STRING_NUMS * 4));
    private static ThreadLocal<ByteBuffer> INT_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(4));
    private static ThreadLocal<ByteBuffer> DOUBLE_BUFFER = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(8));


    private final TSFile[] tsFiles;
    private final AtomicLong atomicLong = new AtomicLong(0);

    public TSFileService(String file) {
        this.tsFiles = new TSFile[Constants.TS_FILE_NUMS];
        for (int i = 0; i < Constants.TS_FILE_NUMS; i++) {
            long initPosition = (long) i * Constants.TS_FILE_SIZE;
            tsFiles[i] = new TSFile(file, i, initPosition);
//            tsFiles[i].warmTsFile();
        }
    }

    public TSFile getTsFileByVin(Vin vin) {
        final byte[] vin1 = vin.getVin();
        final int hash = getHashCodeByByteArray(vin1);
        int slot = hash % Constants.TS_FILE_NUMS;
        return tsFiles[slot];
    }

    public int getHashCodeByByteArray(byte[] bytes) {
        int h = 0;
        for (int i = 0; i < 17; i++) {
            h = 31 * h + bytes[i];
        }
        return Math.abs(h);
    }

    public ArrayList<Row> getByIndex(Vin vin, long timeLowerBound, long timeUpperBound, Index index, Set<String> requestedColumns) {
        ArrayList<Row> rowArrayList = new ArrayList<>();
        try {
            final long offset = index.getOffset();
            final int valueSize = index.getValueSize();
            final TSFile tsFile = getTsFileByVin(vin);
            final ByteBuffer timestampBuffer = ByteBuffer.allocateDirect(valueSize * 8);
            tsFile.getFromOffsetByFileChannel(timestampBuffer, offset);
            timestampBuffer.flip();
            ByteBuffer stringLengthBuffer = ByteBuffer.allocateDirect(valueSize * Constants.STRING_NUMS * 4);
            tsFile.getFromOffsetByFileChannel(stringLengthBuffer, offset + valueSize * 8 + valueSize * Constants.INT_NUMS * 4 + valueSize * Constants.FLOAT_NUMS * 8);
            stringLengthBuffer.flip();
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
                                final ByteBuffer intBuffer = ByteBuffer.allocate(4);
                                int off = (columnIndex * valueSize + i) * 4;
                                tsFile.getFromOffsetByFileChannel(intBuffer, offset + Constants.CACHE_VINS_LINE_NUMS * 8 + off);
                                intBuffer.flip();
                                columns.put(requestedColumn, new ColumnValue.IntegerColumn(intBuffer.getInt()));
//                                System.out.println("time read int");
                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                            }
                        } else if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT)) {
                            try {
                                final ByteBuffer doubleBuffer = ByteBuffer.allocate(8);
                                int off = valueSize * Constants.INT_NUMS * 4 + ((columnIndex - Constants.INT_NUMS) * Constants.CACHE_VINS_LINE_NUMS + i) * 8;
                                tsFile.getFromOffsetByFileChannel(doubleBuffer, offset + Constants.CACHE_VINS_LINE_NUMS * 8 + off);
                                doubleBuffer.flip();
                                columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubleBuffer.getDouble()));
//                                System.out.println("time read double");
                            } catch (Exception e) {
                                System.out.println("getByIndex time range COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                            }
                        } else {
                            try {
                                int stringNum = columnIndex - 54;
                                int stringPosition = (stringNum * valueSize + i) * 4;
                                int anInt = 0;
                                try {
                                    anInt = stringLengthBuffer.getInt(stringPosition);
                                } catch (Exception e) {
                                    System.out.println("getByIndex time range get string length error, e" + e);
                                }
                                if (anInt != 0) {
                                    int position = 0;
                                    try {
                                        for (int i1 = 0; i1 < stringPosition; i1 += 4) {
                                            position += stringLengthBuffer.getInt(i1);
                                        }
                                    } catch (Exception e) {
                                        System.out.println("getByIndex time range get string offset error, e" + e);
                                    }
                                    final ByteBuffer stringBuffer = ByteBuffer.allocate(anInt);
                                    int off = valueSize * 8 //timestamp
                                            + valueSize * Constants.INT_NUMS * 4 //int
                                            + valueSize * Constants.FLOAT_NUMS * 8 //double
                                            + valueSize * Constants.STRING_NUMS * 4
                                            + position;
                                    tsFile.getFromOffsetByFileChannel(stringBuffer, offset + off);
                                    columns.put(requestedColumn, new ColumnValue.StringColumn(stringBuffer.flip()));
//                                    System.out.println("time read string");
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

    public Row getByIndex(Vin vin, long timestamp, Index index, Set<String> requestedColumns) {
        final long offset = index.getOffset();
        final int valueSize = index.getValueSize();
        final TSFile tsFile = getTsFileByVin(vin);
        final ByteBuffer timestampBuffer = ByteBuffer.allocateDirect(valueSize * 8);
        tsFile.getFromOffsetByFileChannel(timestampBuffer, offset);
        timestampBuffer.flip();
        ByteBuffer stringLengthBuffer = ByteBuffer.allocateDirect(valueSize * Constants.STRING_NUMS * 4);
        tsFile.getFromOffsetByFileChannel(stringLengthBuffer, offset + valueSize * 8 + valueSize * Constants.INT_NUMS * 4 + valueSize * Constants.FLOAT_NUMS * 8);
        stringLengthBuffer.flip();
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
                            final ByteBuffer intBuffer = ByteBuffer.allocate(4);
                            int off = (columnIndex * valueSize + i) * 4;
                            tsFile.getFromOffsetByFileChannel(intBuffer, offset + Constants.CACHE_VINS_LINE_NUMS * 8 + off);
                            intBuffer.flip();
                            columns.put(requestedColumn, new ColumnValue.IntegerColumn(intBuffer.getInt()));
//                            System.out.println("read int");
                        } catch (Exception e) {
                            System.out.println("getByIndex COLUMN_TYPE_INTEGER error, e:" + e + "index:" + index);
                        }
                    } else if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT)) {
                        try {
                            final ByteBuffer doubleBuffer = ByteBuffer.allocate(8);
                            int off = valueSize * Constants.INT_NUMS * 4 + ((columnIndex - Constants.INT_NUMS) * Constants.CACHE_VINS_LINE_NUMS + i) * 8;
                            tsFile.getFromOffsetByFileChannel(doubleBuffer, offset + Constants.CACHE_VINS_LINE_NUMS * 8 + off);
                            doubleBuffer.flip();
                            columns.put(requestedColumn, new ColumnValue.DoubleFloatColumn(doubleBuffer.getDouble()));
//                            System.out.println("read double");
                        } catch (Exception e) {
                            System.out.println("getByIndex COLUMN_TYPE_DOUBLE_FLOAT error, e:" + e + "index:" + index);
                        }
                    } else {
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
                                final ByteBuffer stringBuffer = ByteBuffer.allocate(anInt);
                                int off = valueSize * 8 //timestamp
                                        + valueSize * Constants.INT_NUMS * 4 //int
                                        + valueSize * Constants.FLOAT_NUMS * 8 //double
                                        + valueSize * Constants.STRING_NUMS * 4
                                        + position;
                                tsFile.getFromOffsetByFileChannel(stringBuffer, offset + off);
                                columns.put(requestedColumn, new ColumnValue.StringColumn(stringBuffer.flip()));
//                                System.out.println("read string");
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
    public void write(Vin vin, List<Value> valueList, int lineNum) {
        try {
            final long andIncrement = atomicLong.getAndIncrement();
            if (andIncrement % 1000000 == 0) {
                System.out.println("write times:" + andIncrement);
            }
            TSFile tsFile = getTsFileByVin(vin);
            String[] indexArray = SchemaUtil.getIndexArray();
            //存储int类型，大小为缓存数据行 * 每行多少个int * 4
            final ByteBuffer intBuffer = ByteBuffer.allocateDirect(lineNum * Constants.INT_NUMS * 4);
            //存储double类型，大小为缓存数据行 * 每行多少个double * 8
            final ByteBuffer doubleBuffer = ByteBuffer.allocateDirect(lineNum * Constants.FLOAT_NUMS * 8);
            //存储时间戳，总共有多少行，有多少个时间戳
            final ByteBuffer longBuffer = ByteBuffer.allocateDirect(lineNum * 8);
            //存储每个字符串的长度
            ByteBuffer stringLengthBuffer = ByteBuffer.allocateDirect(lineNum * Constants.STRING_NUMS * 4);
            int totalStringLength = 0;
            long maxTimestamp = Long.MIN_VALUE;
            long minTimestamp = Long.MAX_VALUE;
            List<ByteBuffer> stringList = new ArrayList<>(lineNum * Constants.STRING_NUMS);
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
            int total = lineNum * 8 //timestamp
                    + lineNum * Constants.INT_NUMS * 4 //int
                    + lineNum * Constants.FLOAT_NUMS * 8 //double
                    + lineNum * Constants.STRING_NUMS * 4 //string长度记录
                    + totalStringLength; //string存储string
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(total);
            longBuffer.flip();
            byteBuffer.put(longBuffer.slice());
            intBuffer.flip();
            byteBuffer.put(intBuffer.slice());
            doubleBuffer.flip();
            byteBuffer.put(doubleBuffer.slice());
            stringLengthBuffer.flip();
            byteBuffer.put(stringLengthBuffer);
            for (ByteBuffer buffer : stringList) {
                byteBuffer.put(buffer.slice());
            }
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
