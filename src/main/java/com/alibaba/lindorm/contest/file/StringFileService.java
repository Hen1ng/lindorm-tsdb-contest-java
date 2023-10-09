package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.index.Bindex;
import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.Pair;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public class StringFileService {

    private Map<Integer, StringFile> map;
    public Pair<Bindex, int[]>  put(List<ByteBuffer> buffers, int valueSize) {
        int i = 0;
        while (i * valueSize < buffers.size()) {
            for (int j = i * valueSize; j < (i + 1) * valueSize; j++) {
                int columnIndex = i + Constants.FLOAT_NUMS + Constants.INT_NUMS;
                final StringFile stringFile = map.get(columnIndex);
                stringFile.write(buffers.get(j));
            }
        }
        return null;
    }

    public ByteBuffer get(int columnIndex, long offset, int stringOffset, Bindex bindex) {
        return null;
    }
}
