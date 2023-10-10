package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.index.Bindex;
import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.SchemaUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StringFileService {

    private Map<Integer, StringFile> map = new ConcurrentHashMap<>(10);

    public void addColumn(Integer columnIndex, String filePath, int totalCacheLines) {
        map.put(columnIndex, new StringFile(filePath, SchemaUtil.getIndexArray()[columnIndex], totalCacheLines));
    }

    public void put(List<ByteBuffer> buffers, int valueSize, boolean flushNow, Index index) {
        //那一列
        int i = 0;
        int[] stringOffset = new int[Constants.STRING_NUMS];
        long[] fileOffset = new long[Constants.STRING_NUMS];
        int[] totalLength = new int[Constants.STRING_NUMS];
        final Bindex bindex = new Bindex(totalLength, fileOffset);
        index.setBindex(bindex);
        index.setStringOffset(stringOffset);
        while (i * valueSize < buffers.size()) {
            int start = i * valueSize;
            int end = (i + 1) * valueSize;
            int columnIndex = i + Constants.INT_NUMS + Constants.FLOAT_NUMS;
            final StringFile stringFile = map.get(columnIndex);
            final StringFile.WriteResult write = stringFile.write(buffers, start, end, flushNow, i, index);
            fileOffset[i] = write.fileOffset;
            totalLength[i] = write.totalLength;
            stringOffset[i] = write.batchSize;
            i++;
        }

    }

    public byte[] get(int columnIndex, int stringOffset, Bindex bindex) {
        final StringFile stringFile = map.get(columnIndex);
//        final ByteBuffer allocate = ByteBuffer.allocate(bindex.totalLength);
//        stringFile.getFromOffsetByFileChannel(allocate, bindex.fileOffset);
//        final byte[] array = allocate.array();
//        //todo decompress
//        byte[] bytes = array;
        //getStringLengthArray
        return null;
    }
}
