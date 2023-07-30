package com.alibaba.lindorm.contest.example;

import com.alibaba.lindorm.contest.TSDBEngineImpl;
import com.alibaba.lindorm.contest.structs.LatestQueryRequest;
import com.alibaba.lindorm.contest.structs.Row;
import com.alibaba.lindorm.contest.structs.Vin;
import com.alibaba.lindorm.contest.util.BytesUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QueryTestNew {
    public static void main(String[] args) throws Exception {
        File dataDir = new File("./data_dir");
        if (dataDir.isFile()) {
            throw new IllegalStateException("Clean the directory before we start the demo");
        }
        if (!dataDir.isDirectory()) {
            boolean ret = dataDir.mkdirs();
            if (!ret) {
                throw new IllegalStateException("Cannot create the temp data directory: " + dataDir);
            }
        }
        TSDBEngineImpl tsdbEngineSample = new TSDBEngineImpl(dataDir);
        tsdbEngineSample.connect();
        List<Vin> list = new ArrayList<>();
        list.add(new Vin("5BgQcfR6CbF4li53M".getBytes(StandardCharsets.UTF_8)));
        Set<String> requestedColumns = new HashSet<>();
        requestedColumns.add("5String543210");
        final LatestQueryRequest latestQueryRequest = new LatestQueryRequest("", list, requestedColumns);
        final ArrayList<Row> rows = tsdbEngineSample.executeLatestQuery(latestQueryRequest);
    }
}
