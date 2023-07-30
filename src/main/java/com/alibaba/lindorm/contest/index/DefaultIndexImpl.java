package com.alibaba.lindorm.contest.index;

import com.alibaba.lindorm.contest.structs.LatestQueryRequest;
import com.alibaba.lindorm.contest.structs.Row;
import com.alibaba.lindorm.contest.structs.TimeRangeQueryRequest;
import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.list.SortedList;
import com.alibaba.lindorm.contest.util.list.hppc.ObjectObjectCursor;
import com.alibaba.lindorm.contest.util.list.hppc.ObjectObjectHashMap;

import java.util.ArrayList;

/**
 * 默认的索引实现
 * @author hn
 */
public class DefaultIndexImpl implements IndexService {

    private ObjectObjectHashMap<Vin, SortedList<Long>> map;

    public DefaultIndexImpl(int size) {
        map = new ObjectObjectHashMap<>(size);
        final Object[] keys = map.getKeys();
        for (int i = 0; i < keys.length; i++) {
            keys[i] = new Vin(new byte[17]);
        }
        final Object[] values = map.getValues();
        for (int i = 0; i < values.length; i++) {
            values[i] = new SortedList<Long>((l1, l2) -> -1 * l1.compareTo(l2));
        }

    }


    @Override
    public boolean buildIndex(Row row) {
        final long timestamp = row.getTimestamp();
        final Vin vin = row.getVin();
        synchronized (vin) {

        }
        final SortedList<Long> sortedList = map.get(vin);
        sortedList.add(timestamp);
        return false;
    }

    @Override
    public ArrayList<Row> executeLatestQuery(LatestQueryRequest pReadReq) {
        return null;
    }

    @Override
    public ArrayList<Row> executeTimeRangeQuery(TimeRangeQueryRequest trReadReq) {
        return null;
    }
}
