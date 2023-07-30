package com.alibaba.lindorm.contest.index;

import com.alibaba.lindorm.contest.structs.LatestQueryRequest;
import com.alibaba.lindorm.contest.structs.Row;
import com.alibaba.lindorm.contest.structs.TimeRangeQueryRequest;

import java.util.ArrayList;

public interface IndexService {

    boolean buildIndex(Row row);

    ArrayList<Row> executeLatestQuery(LatestQueryRequest pReadReq);

    ArrayList<Row> executeTimeRangeQuery(TimeRangeQueryRequest trReadReq);
}
