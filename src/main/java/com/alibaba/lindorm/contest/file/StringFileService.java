package com.alibaba.lindorm.contest.file;

import com.alibaba.lindorm.contest.compress.IntCompress;
import com.alibaba.lindorm.contest.compress.StringCompress;
import com.alibaba.lindorm.contest.index.Bindex;
import com.alibaba.lindorm.contest.index.BindexFactory;
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
        map.put(columnIndex, new StringFile(filePath, totalCacheLines));
    }

    /**
     * @param buffers   所有列的170，总共170 * 10
     * @param valueSize
     * @param flushNow
     * @param index
     */
    public void put(List<ByteBuffer> buffers, int valueSize, boolean flushNow, Index index) {
        //那一列
        int i = 0;
        while (i * valueSize < buffers.size()) {
            int start = i * valueSize;
            int end = (i + 1) * valueSize;
            int columnIndex = i + Constants.INT_NUMS + Constants.FLOAT_NUMS;
            final StringFile stringFile = map.get(columnIndex);
            stringFile.write(buffers, start, end, flushNow, i, index);
            i++;
        }

    }

    public List<ByteBuffer> get(int columnIndex, Index index) {
        final StringFile stringFile = map.get(columnIndex);
        final Bindex bindex = BindexFactory.getByPosition(index.getBindexIndex()[columnIndex - Constants.INT_NUMS - Constants.FLOAT_NUMS]);
        final int totalLength = bindex.totalLength;
        final long fileOffset = bindex.fileOffset;
        final int[] stringOffsets = index.getStringOffset();
        int arrayIndex = columnIndex - Constants.INT_NUMS - Constants.FLOAT_NUMS;
        final int stringOffset = stringOffsets[arrayIndex];
        //get from memory
        if (totalLength == -1) {
            final List<ByteBuffer> fromBuffer = stringFile.getFromBuffer(stringOffset, index.getValueSize());
            if (!fromBuffer.isEmpty()) {
                return fromBuffer;
            }
        }
        //get from file
        final ByteBuffer allocate = ByteBuffer.allocate(totalLength);
        stringFile.getFromOffsetByFileChannel(allocate, fileOffset);
        allocate.flip();
        final short shortLength = allocate.getShort();
        final byte[] bytes = new byte[shortLength];

        allocate.get(bytes);
//        IntCompress.decompressShort()
//        final int stringLength = allocate.getInt();
//        final byte[] bytes1 = new byte[stringLength];
//
//        StringCompress.decompress1(bytes1, );
        return null;
    }
}
