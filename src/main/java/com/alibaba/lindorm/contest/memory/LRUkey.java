package com.alibaba.lindorm.contest.memory;

import com.alibaba.lindorm.contest.structs.ColumnValue;
import com.alibaba.lindorm.contest.structs.Vin;

import java.util.Objects;

public class LRUkey {
    private Vin vin;
    private long offset;

    public LRUkey(Vin vin, long offset) {
        this.vin = vin;
        this.offset = offset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LRUkey lrUkey = (LRUkey) o;
        return offset == lrUkey.offset && Objects.equals(vin, lrUkey.vin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vin, offset);
    }
}
