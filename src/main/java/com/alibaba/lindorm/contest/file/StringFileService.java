package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.compress.IntCompress;
import com.alibaba.lindorm.contest.compress.StringCompress;
import com.alibaba.lindorm.contest.index.Bindex;
import com.alibaba.lindorm.contest.index.Index;
import com.alibaba.lindorm.contest.util.Constants;
import com.alibaba.lindorm.contest.util.SchemaUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StringFileService {

    private Map<Integer, StringFile> map = new ConcurrentHashMap<>(10);

    public void addColumn(Integer columnIndex, String filePath, int totalCacheLines) {
        map.put(columnIndex, new StringFile(filePath, SchemaUtil.getIndexArray()[columnIndex], totalCacheLines));
    }

    /**
     *
     * @param buffers 所有列的170，总共170 * 10
     * @param valueSize
     * @param flushNow
     * @param index
     */
    public void put(List<ByteBuffer> buffers, int valueSize, boolean flushNow, Index index) {
        //那一列
        int i = 0;
//        int[] stringOffset = new int[Constants.STRING_NUMS];
//        long[] fileOffset = new long[Constants.STRING_NUMS];
//        int[] totalLength = new int[Constants.STRING_NUMS];
//        final Bindex bindex = getBindex();
//        index.setBindex(bindex);
//        index.setStringOffset(stringOffset);
        while (i * valueSize < buffers.size()) {
            int start = i * valueSize;
            int end = (i + 1) * valueSize;
            int columnIndex = i + Constants.INT_NUMS + Constants.FLOAT_NUMS;
            final StringFile stringFile = map.get(columnIndex);
            final StringFile.WriteResult write = stringFile.write(buffers, start, end, flushNow, i, index, bindex);
//            fileOffset[i] = write.fileOffset;
//            totalLength[i] = write.totalLength;
//            stringOffset[i] = write.batchSize;
            i++;
        }

    }

    public ArrayList<ByteBuffer> get(int columnIndex, Index index) {
        final StringFile stringFile = map.get(columnIndex);
        final Bindex bindex = index.getBindex();
        final int[] totalLengths = bindex.totalLength;
        final long[] fileOffsets = bindex.fileOffset;
        final int[] stringOffsets = index.getStringOffset();
        int arrayIndex = columnIndex - Constants.INT_NUMS - Constants.FLOAT_NUMS;
        final long fileOffset = fileOffsets[arrayIndex];
        final int stringOffset = stringOffsets[arrayIndex];
        final int totalLength = totalLengths[arrayIndex];
        final ByteBuffer allocate = ByteBuffer.allocate(totalLength);
        stringFile.getFromOffsetByFileChannel(allocate, fileOffset);
        allocate.flip();
        final short shortLength = allocate.getShort();
        final byte[] bytes = new byte[shortLength];
        //todo get from memory
        allocate.get(bytes);
        IntCompress.decompressShort()
        final int stringLength = allocate.getInt();
        final byte[] bytes1 = new byte[stringLength];

        StringCompress.decompress1(bytes1, );
        return null;
    }
}
