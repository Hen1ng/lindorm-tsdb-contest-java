package com.alibaba.lindorm.contest.memory;

import com.alibaba.lindorm.contest.structs.ColumnValue;

import java.util.Map;

public class Value {

    public Value(long timestamp, Map<String, ColumnValue> columns) {
        this.timestamp = timestamp;
        this.columns = columns;
    }

    private final long timestamp;

    private final Map<String, ColumnValue> columns;

    public long getTimestamp() {
        return timestamp;
    }

    public Map<String, ColumnValue> getColumns() {
        return columns;
    }

    @Override
    public boolean equals(Object o) {
        Value value = (Value) o;
        return timestamp == value.timestamp ;
    }


}
