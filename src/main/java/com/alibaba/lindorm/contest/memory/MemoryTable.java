package com.alibaba.lindorm.contest.memory;

import com.alibaba.lindorm.contest.file.TSFileService;
import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.index.MapIndex;
import com.alibaba.lindorm.contest.structs.ColumnValue;
import com.alibaba.lindorm.contest.structs.Row;
import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.*;
import com.alibaba.lindorm.contest.util.list.SortedList;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.alibaba.lindorm.contest.index.MapIndex.INDEX_ARRAY;

/**
 * key:Vin, value:timestamp
 * 根据vin检索到ts
 *
 * @author hn
 */
public class MemoryTable {

    public final ExecutorService fixThreadPool;
    private final Lock  bufferValuesLock;

    private final Condition hasFreeBuffer;


    private final ConcurrentHashMap<Vin, Queue<Integer>> vinToBufferIndex;

    private final Queue<Integer> freeList;
    private final List<Value>[] bufferValues;
    private final List<Value>[] values;

    private final int size;
    private final AtomicInteger atomicIndex = new AtomicInteger(0);
    private final ReentrantReadWriteLock[] spinLockArray;
    private final TSFileService tsFileService;
    private final AtomicLong queryLastTimes = new AtomicLong(0);
    private final AtomicLong queryTimeRangeTimes = new AtomicLong(0);


    public MemoryTable(int size, TSFileService tsFileService) {
        this.size = size;
        this.values = new SortedList[size];
        this.bufferValues = new SortedList[Constants.TOTAL_BUFFER_NUMS];
        this.tsFileService = tsFileService;
        this.spinLockArray = new ReentrantReadWriteLock[60000];
        for(int i=0;i<60000;i++){
            this.spinLockArray[i] = new ReentrantReadWriteLock();
        }
        this.bufferValuesLock = new ReentrantLock();
        this.hasFreeBuffer = this.bufferValuesLock.newCondition();
        this.freeList = new LinkedList<>();
        this.fixThreadPool = Executors.newFixedThreadPool(8);
        this.vinToBufferIndex = new ConcurrentHashMap<>();
        for (int i = 0; i < size; i++) {
            values[i] = new SortedList<>((v1, v2) -> (int) (v2.getTimestamp() - v1.getTimestamp()));
        }
        for(int i=0;i<Constants.TOTAL_BUFFER_NUMS;i++){
            bufferValues[i] = new SortedList<>((v1, v2) -> (int) (v2.getTimestamp() - v1.getTimestamp()));
            freeList.add(i);
        }
    }

    public void asyncPut(Row row){
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
            List<Value> valueSortedList = values[index];
            valueSortedList.add(new Value(ts, row.getColumns()));
            if (valueSortedList.size() >= Constants.CACHE_VINS_LINE_NUMS) {
                int bufferIndex = getFreeBufferIndex(vin);
                // maybe used copy will be speed up
                assert(bufferValues[bufferIndex].isEmpty());
                values[index] = bufferValues[bufferIndex];
                bufferValues[bufferIndex] = valueSortedList;
                Integer finalIndex = index;
                Integer finalBufferIndex = bufferIndex;
                fixThreadPool.execute( () -> {
                    tsFileService.write(vin,bufferValues[finalBufferIndex],Constants.CACHE_VINS_LINE_NUMS, finalIndex, false);
                    freeBufferByIndex(vin,finalBufferIndex);
                });
            }
        } finally {
            spinLockArray[lock].writeLock().unlock();
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
                tsFileService.write(vin, valueSortedList, Constants.CACHE_VINS_LINE_NUMS, index, false);
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
//        final byte[] vin1 = vin.getVin();
//        final int keyHash = getStringHash(vin1, 0, vin1.length);
//        int lock = keyHash % spinLockArray.length;
//        spinLockArray[lock].readLock().lock();
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

    public Row getFromMemoryTable(Vin vin, Set<String> requestedColumns, int slot) {
        long start = System.currentTimeMillis();
        queryLastTimes.getAndIncrement();
        long totalStringLength = 0;
        try {
            final int size = values[slot].size();
            Value value;
            if (size == 0) {
                try {
                    bufferValuesLock.lock();
                    if (!vinToBufferIndex.containsKey(vin)) {
                        return null;
                    }
                    Queue<Integer> indexs = vinToBufferIndex.get(vin);
                    int bufferIndex = indexs.peek();
                    value = bufferValues[bufferIndex].get(0);
                } finally {
                    bufferValuesLock.unlock();
                }
            } else {
                value = values[slot].get(0);
            }
            Map<String, ColumnValue> columns = new HashMap<>(requestedColumns.size());
            for (String requestedColumn : requestedColumns) {
                final ColumnValue.ColumnType columnType = SchemaUtil.getSchema().getColumnTypeMap().get(requestedColumn);
                if (columnType.equals(ColumnValue.ColumnType.COLUMN_TYPE_STRING)) {
                    final ByteBuffer stringValue = value.getColumns().get(requestedColumn).getStringValue();
                    final ByteBuffer allocate = ByteBuffer.allocate(stringValue.capacity());
                    totalStringLength += stringValue.capacity();
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
            return new Row(vin, value.getTimestamp(), columns);
        } finally {
            if (queryLastTimes.get() % 300000000 == 0) {
                System.out.println("getLast cost: " + (System.currentTimeMillis() - start) + " ms" + " totalStringLength: " + totalStringLength);
            }
        }
    }

    public ArrayList<Row> getTimeRangeRow(Vin vin, long timeLowerBound, long timeUpperBound, Set<String> requestedColumns) {
        long start = System.currentTimeMillis();
        queryTimeRangeTimes.getAndIncrement();
//        final byte[] vin1 = vin.getVin();
//        final int keyHash = getStringHash(vin1, 0, vin1.length);
//        int lock = keyHash % spinLockArray.length;
//        spinLockArray[lock].readLock().lock();
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
            if (queryTimeRangeTimes.get() % 500000 == 0) {
                System.out.println("getTimeRangeRow cost: " + (System.currentTimeMillis() - start) + " ms");
            }
//            spinLockArray[lock].readLock().unlock();
        }
    }

    public ArrayList<Row> getTimeRangeRowForQueryTest(Vin vin, long timeLowerBound, long timeUpperBound, Set<String> requestedColumns) {
        final byte[] vin1 = vin.getVin();
        final int keyHash = getStringHash(vin1, 0, vin1.length);
        int slot = keyHash % size;
        spinLockArray[slot].readLock().lock();
        try {
            Integer i = VinDictMap.get(vin);
            if (i == null) {
                return null;
            }
            final Set<Index> v2 = MapIndex.getV2(i, timeLowerBound, timeUpperBound);
            ArrayList<Row> rowArrayList = new ArrayList<>();
            if (v2 == null) {
                return rowArrayList;
            }
            for (Index index : v2) {
                final Integer integer = VinDictMap.get(vin);
                final ArrayList<Row> byIndex = tsFileService.getByIndexV2(vin, timeLowerBound, timeUpperBound, index, requestedColumns, integer);
                if (!byIndex.isEmpty()) {
                    rowArrayList.addAll(byIndex);
                }

            }
            return rowArrayList;
        } finally {
            spinLockArray[slot].readLock().lock();
        }
    }

    private int getFreeBufferIndex(Vin vin){
        this.bufferValuesLock.lock();
        try{
            while (freeList.isEmpty()){
                hasFreeBuffer.await();
            }
            StaticsUtil.MAX_IDLE_BUFFER = Math.min(StaticsUtil.MAX_IDLE_BUFFER,freeList.size());
            int i = freeList.poll();
            if(vinToBufferIndex.containsKey(vin)) {
                Queue<Integer> integers = vinToBufferIndex.get(vin);
                integers.add(i);
            }else{
                LinkedList<Integer> integers = new LinkedList<>();
                integers.add(i);
                vinToBufferIndex.put(vin,integers);
            }
            return i;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            bufferValuesLock.unlock();
        }
    }
    private void freeBufferByIndex(Vin vin,int index){
        this.bufferValuesLock.lock();
        try{
            freeList.add(index);
            bufferValues[index].clear();
            vinToBufferIndex.remove(vin);
            hasFreeBuffer.signal();
        }finally {
            bufferValuesLock.unlock();
        }
    }


    private ArrayList<Row> getTimeRangeRowFromMemoryTable(Vin vin, long timeLowerBound, long timeUpperBound, Set<String> requestedColumns, int slot) {
        ArrayList<Row> result = new ArrayList<>();
        List<Value> valueList = new ArrayList<>();
        try {
            final List<Value> sortedList = values[slot];
            valueList.addAll(sortedList);
            this.bufferValuesLock.lock();
            if(vinToBufferIndex.containsKey(vin)){
                Queue<Integer> indexs = vinToBufferIndex.get(vin);
                for (Integer index : indexs) {
                    valueList.addAll(bufferValues[index]);
                }
            }
            this.bufferValuesLock.unlock();
            for (Value value : valueList) {
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

    private ArrayList<Row> getTimeRangeRowFromTsFile(Vin vin, long timeLowerBound, long timeUpperBound, Set<String> requestedColumns, int vinIndex) {
        ArrayList<Row> rowArrayList = new ArrayList<>();
        try {
            final List<Index> indexList = MapIndex.get(vinIndex, timeLowerBound, timeUpperBound);
            for (Index index : indexList) {
                final Integer integer = VinDictMap.get(vin);
                final ArrayList<Row> byIndex = tsFileService.getByIndexV2(vin, timeLowerBound, timeUpperBound, index, requestedColumns, integer);
                if (!byIndex.isEmpty()) {
                    rowArrayList.addAll(byIndex);
                }
            }
        } catch (Exception e) {
            System.out.println("getTimeRangeRowFromTsFile error, e" + e);
        }
        return rowArrayList;
    }

    public void writeToFileBeforeShutdown() {
        try {
            for (int i = 0; i < values.length; i++) {
                List<Value> valueList = values[i];
                if (valueList.size() >= 1) {
                    final Vin vin = new Vin(VinDictMap.get(i));
                    tsFileService.write(vin, valueList, valueList.size(), i, true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void writeToFileBeforeShutdownMultiThread() {
        try {
            int threadNum = 10;
            final ExecutorService executorService = Executors.newFixedThreadPool(threadNum);
            List<List<List<Value>>> listList = new ArrayList<>();
            for (int i = 0; i < threadNum; i++) {
                listList.add(new ArrayList<>(Constants.CACHE_VINS_LINE_NUMS));
            }
            int i = 0;
            for (int i1 = 0; i1 < values.length; i1++) {
                int mod = i1 % threadNum;
                listList.get(mod).add(values[i1]);
            }
            List<Future<Void>> futures = new ArrayList<>(threadNum);
            for (List<List<Value>> sortedLists : listList) {
                final Future<Void> submit = executorService.submit(() -> {
                    for (List<Value> valueList : sortedLists) {
                        if (!valueList.isEmpty()) {
                            final Vin vin = new Vin(VinDictMap.get(i));
                            tsFileService.write(vin, valueList, valueList.size(), i, true);
                        }
                    }
                    return null;
                });
                futures.add(submit);
            }
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Value>[] getValues() {
        return values;
    }

    public void loadLastTsToMemory() {
        try {
            System.out.println("loadLastTsToMemory start");
            long start = System.currentTimeMillis();
            final Set<String> requestedColumns = SchemaUtil.getSchema().getColumnTypeMap().keySet();
            for (int i = 0; i < INDEX_ARRAY.length; i++) {
                Pair<Index, Long> pair = MapIndex.getLast(i);
                final Index index = pair.getLeft();
                final Long timestamp = pair.getRight();
                Vin vin = new Vin(VinDictMap.get(i));
                if(index==null)continue;;
                Row row = tsFileService.getByIndex(vin, timestamp, index, requestedColumns, i);
                if (row == null) {
                    throw new RuntimeException("loadLastTsToMemory error, row is null");
                }
                final List<Value> valueSortedList = this.values[i];
                final Value value = new Value(timestamp, row.getColumns());
                valueSortedList.add(value);
            }
//            System.out.println("loadLastTsToMemory finish cost:" + (System.currentTimeMillis() - start) + " ms");
//            start = System.currentTimeMillis();
//            ExecutorService executorService = Executors.newFixedThreadPool(200);
//            final CountDownLatch countDownLatch = new CountDownLatch(MapIndex.INDEX_MAP.size());
//            for (Vin vin : MapIndex.INDEX_MAP.keySet()) {
//                executorService.submit(() -> {
//                    final List<Index> indexList = MapIndex.getByVin(vin);
//                    for (Index index : indexList) {
//                        final Integer integer = VinDictMap.get(vin);
//                        final ByteBuffer timestampList = tsFileService.getTimestampList(index, integer);
//                        final int valueSize = index.getValueSize();
//                        List<Long> timestamps = new ArrayList<>(valueSize);
//                        for (int i = 0; i < valueSize; i++) {
//                            timestamps.add(timestampList.getLong());
//                        }
//                        index.setTimestampList(timestamps);
//                    }
//                    countDownLatch.countDown();
//                });
//            }
//            countDownLatch.await();
//            System.out.println("load timestamp to memory finish cost:" + (System.currentTimeMillis() - start) + " ms");
        } catch (Exception e) {
            System.out.println("loadLastTsToMemory error, e" + e);
        }
    }

    public static void main(String[] args) {
        double maxDouble = -Double.MAX_VALUE;
        System.out.println(maxDouble>0);
    }
}
