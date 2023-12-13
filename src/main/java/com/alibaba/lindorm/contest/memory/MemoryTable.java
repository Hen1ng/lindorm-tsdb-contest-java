package com.alibaba.lindorm.contest.memory;

import com.alibaba.lindorm.contest.file.TSFileService;
import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.index.MapIndex;
import com.alibaba.lindorm.contest.structs.ColumnValue;
import com.alibaba.lindorm.contest.structs.Row;
import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.Pair;
import com.alibaba.lindorm.contest.util.RestartUtil;
import com.alibaba.lindorm.contest.util.SchemaUtil;
import com.github.luben.zstd.Zstd;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.alibaba.lindorm.contest.index.MapIndex.INDEX_ARRAY;

/**
 * key:Vin, value:timestamp
 * 根据vin检索到ts
 *
 * @author hn
 */
public class MemoryTable {

    private final List<Value>[] values;
    private final AtomicInteger atomicIndex = new AtomicInteger(0);
    private final ReentrantReadWriteLock[] spinLockArray;
    private final TSFileService tsFileService;
    private final AtomicLong queryTimeRangeTimes = new AtomicLong(0);


    public MemoryTable(int size, TSFileService tsFileService) {
        this.values = new ArrayList[size];
        this.tsFileService = tsFileService;
        this.spinLockArray = new ReentrantReadWriteLock[60000];
        for (int i = 0; i < 60000; i++) {
            this.spinLockArray[i] = new ReentrantReadWriteLock();
        }
        for (int i = 0; i < size; i++) {
            values[i] = new ArrayList<>(Constants.CACHE_VINS_LINE_NUMS);
        }
    }


    public void put(Row row) {
        Vin vin = row.getVin();
        long ts = row.getTimestamp();
        final byte[] vin1 = vin.getVin();
        final int hash = getStringHash(vin1, 0, vin1.length);
        int lock = hash % spinLockArray.length;
        spinLockArray[lock].writeLock().lock();
        try {
            Integer index = VinDictMap.get(vin);
            if (index == null) {
                index = atomicIndex.getAndIncrement();
                VinDictMap.put(vin, index);
            }
            final List<Value> valueSortedList = values[index];
            valueSortedList.add(new Value(ts, row.getColumns()));
            if (valueSortedList.size() >= Constants.CACHE_VINS_LINE_NUMS) {
                Collections.sort(valueSortedList, (v1, v2) -> Long.compare(v2.getTimestamp(), v1.getTimestamp()));
                tsFileService.write(vin, valueSortedList, Constants.CACHE_VINS_LINE_NUMS, index);
            }
        } finally {
            spinLockArray[lock].writeLock().unlock();
        }
    }

    public int getStringHash(byte[] vin1, int start, int end) {
        int h = 0;
        for (int i = start; i < end; i++) {
            h = 31 * h + vin1[i];
        }
        return Math.abs(h);
    }

    public Row getLastRow(Vin vin, Set<String> requestedColumns) {
        try {
            Integer i = VinDictMap.get(vin);
            if (i == null) {
                return null;
            }
            if (!RestartUtil.IS_FIRST_START) {
                return getFromMemoryTable(vin, requestedColumns, i);
            }
            final Row fromMemoryTable = getFromMemoryTable(vin, requestedColumns, i);
            Row row = null;
            final Pair<Index, Long> last = MapIndex.getLast(i);
            if (last != null && last.getLeft() != null) {
                final Index index = last.getLeft();
                final Long timestamp = last.getRight();
                row = tsFileService.getByIndex(vin, timestamp, index, requestedColumns, i);
            }
            if (row == null) {
                return fromMemoryTable;
            }
            if (fromMemoryTable == null) {
                return row;
            }
            if (row.getTimestamp() > fromMemoryTable.getTimestamp()) {
                return row;
            }
            return fromMemoryTable;
        } catch (Exception e) {
            System.out.println("getLastRowFromMemoryTable e" + e);
        } finally {
//            spinLockArray[lock].readLock().unlock();
        }
        return null;
    }

    public synchronized void print(Map<String, ColumnValue> columns,  Set<String> requestedColumns ) {
        System.out.println("query columns");
        for (String requestedColumn : requestedColumns) {
            System.out.print(requestedColumn + ",");
        }
        System.out.println("result columns");
        for (Map.Entry<String, ColumnValue> entry : columns.entrySet()) {
            System.out.println(entry.getKey() + " :" + entry.getValue());
        }

    }
    public Row getLastRow(Vin vin, int[] queryColumns, Set<String> requestedColumns ) {
        try {
            Integer i = VinDictMap.get(vin);
            if (i == null) {
                return null;
            }
            final List<Value> value1 = values[i];
            Value value = value1.get(0);
            final Map<String, ColumnValue> columns = new HashMap<>(requestedColumns.size());
            int j = 0;
            for (String requestedColumn : requestedColumns) {
                columns.put(requestedColumn, value.getColumnValues()[queryColumns[j]]);
                j++;
            }
            return new Row(vin, value.getTimestamp(), columns);
        } catch (Exception e) {
            e.printStackTrace();;
        }
        return null;
    }

    public Row getFromMemoryTable(Vin vin, Set<String> requestedColumns, int slot) {
        Value value = values[slot].get(0);
        final Map<String, ColumnValue> columns = new HashMap<>(requestedColumns.size());
        for (String requestedColumn : requestedColumns) {
                columns.put(requestedColumn, value.getColumns().get(requestedColumn));
        }
        return new Row(vin, value.getTimestamp(), columns);
    }


    public ArrayList<Row> getTimeRangeRow(Vin vin, long timeLowerBound, long timeUpperBound, Set<String> requestedColumns) {
        long start = System.currentTimeMillis();
        queryTimeRangeTimes.getAndIncrement();
        try {
            Integer i = VinDictMap.get(vin);
            if (i == null) {
                return null;
            }
            if (!RestartUtil.IS_FIRST_START) {
                return getTimeRangeRowFromTsFile(vin, timeLowerBound, timeUpperBound, requestedColumns, i);
            }
            final ArrayList<Row> timeRangeRowFromMemoryTable = getTimeRangeRowFromMemoryTable(vin, timeLowerBound, timeUpperBound, requestedColumns, i);
            final ArrayList<Row> timeRangeRowFromTsFile = getTimeRangeRowFromTsFile(vin, timeLowerBound, timeUpperBound, requestedColumns, i);
            timeRangeRowFromMemoryTable.addAll(timeRangeRowFromTsFile);
            return timeRangeRowFromMemoryTable;
        } finally {
            if (queryTimeRangeTimes.get() % 1000000 == 0) {
                System.out.println("getTimeRangeRow cost: " + (System.currentTimeMillis() - start) + " ms");
            }
        }
    }


    private ArrayList<Row> getTimeRangeRowFromMemoryTable(Vin vin, long timeLowerBound, long timeUpperBound, Set<String> requestedColumns, int slot) {
        ArrayList<Row> result = new ArrayList<>();
        try {
            final List<Value> sortedList = values[slot];
            for (Value value : sortedList) {
                if (value.getTimestamp() >= timeLowerBound && value.getTimestamp() < timeUpperBound) {
                    Map<String, ColumnValue> columns = new HashMap<>(requestedColumns.size());
                    for (String requestedColumn : requestedColumns) {
                        final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(requestedColumn);
                        if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_STRING)) {
                            final ByteBuffer stringValue = value.getColumns().get(requestedColumn).getStringValue();
                            final ByteBuffer allocate = ByteBuffer.allocate(stringValue.capacity());
                            int position = stringValue.position();
                            int limit = stringValue.limit();
                            allocate.put(stringValue);
                            stringValue.limit(limit);
                            stringValue.position(position);
                            columns.put(requestedColumn, new ColumnValue.StringColumn(allocate.flip()));
                        } else {
                            columns.put(requestedColumn, value.getColumns().get(requestedColumn));
                        }
                    }
                    result.add(new Row(vin, value.getTimestamp(), columns));
                }
            }
            return result;
        } catch (Exception e) {
            System.out.println("getTimeRangeRowFromMemoryTable error, e" + e);
        }
        return result;
    }

    private final AtomicLong executeTimeRangeQueryTimes = new AtomicLong(0);

    private ArrayList<Row> getTimeRangeRowFromTsFile(Vin vin, long timeLowerBound, long timeUpperBound, Set<String> requestedColumns, int vinIndex) {
        ArrayList<Row> rowArrayList = new ArrayList<>();
        try {
            final List<Index> indexList = MapIndex.get(vinIndex, timeLowerBound, timeUpperBound);
            for (Index index : indexList) {
                final ArrayList<Row> byIndex = tsFileService.getByIndexV2(vin, timeLowerBound, timeUpperBound, index, requestedColumns, vinIndex);
                if (!byIndex.isEmpty()) {
                    rowArrayList.addAll(byIndex);
                }
            }
            if (executeTimeRangeQueryTimes.getAndIncrement() % 200000 == 0) {
                System.out.println("getTimeRangeRowFromTsFile indexList size " + indexList.size() + "interval:" + (timeUpperBound - timeLowerBound));
            }
        } catch (Exception e) {
            System.out.println("getTimeRangeRowFromTsFile error, e" + e);
        }
        return rowArrayList;
    }

    public void writeToFileBeforeShutdown() {
        long start = System.currentTimeMillis();
        try {
            for (int i = 0; i < values.length; i++) {
                List<Value> valueList = values[i];
                Collections.sort(valueList, (v1, v2) -> Long.compare(v2.getTimestamp(), v1.getTimestamp()));
                if (valueList.size() >= 1) {
                    final Vin vin = new Vin(VinDictMap.get(i));
                    tsFileService.write(vin, valueList, valueList.size(), i);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("writeToFileBeforeShutdown finish cost: " + (System.currentTimeMillis() - start) + " ms");
    }


    public List<Value>[] getValues() {
        return values;
    }

    public void loadLastTsToMemory() {
        try {
            System.out.println("loadLastTsToMemory start");
            long start = System.currentTimeMillis();
            final Set<String> requestedColumns = SchemaUtil.getSchema().getColumnTypeMap().keySet();
            int total = 0;
            for (int i = 0; i < INDEX_ARRAY.length; i++) {
                Pair<Index, Long> pair = MapIndex.getLast(i);
                final Index index = pair.getLeft();
                final Long timestamp = pair.getRight();
                Vin vin = new Vin(VinDictMap.get(i));
                if (index == null) continue;
                ;
                Row row = tsFileService.getByIndex(vin, timestamp, index, requestedColumns, i);
                if (row == null) {
                    throw new RuntimeException("loadLastTsToMemory error, row is null");
                }
                final List<Value> valueSortedList = this.values[i];
                final Value value = new Value(timestamp, row.getColumns());
                ColumnValue[] columnValues = new ColumnValue[60];
                for (Map.Entry<String, ColumnValue> entry : row.getColumns().entrySet()) {
                    final String key = entry.getKey();
                    final ColumnValue value1 = entry.getValue();
                    final int indexByColumn = SchemaUtil.getIndexByColumn(key);
                    columnValues[indexByColumn] = value1;
                }
                value.setColumnValues(columnValues);
                valueSortedList.add(value);
                total += 1;
            }
            System.out.println("loadLastTsToMemory finish cost:" + (System.currentTimeMillis() - start) + " ms" + "total " + total);
        } catch (Exception e) {
            System.out.println("loadLastTsToMemory error, e" + e);
        }
    }

    public static void main(String[] args) {
       String s = "asdfjasdfsdfasdfasdf";
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(s.length());
        final ByteBuffer byteBuffer1 = ByteBuffer.allocate(s.length());
        byteBuffer.put(s.getBytes());
        byteBuffer1.put(s.getBytes());
        final byte[] compress1 = Zstd.compress(byteBuffer1.array(), 3);
        final ByteBuffer compress = Zstd.compress(byteBuffer, 3);
        final ByteBuffer decompress = Zstd.decompress(compress, s.length());
        byte[] bytes = new byte[s.length()];
        decompress.get(bytes);
        final String s1 = new String(bytes);
    }
}
