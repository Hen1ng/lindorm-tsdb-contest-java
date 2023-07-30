package com.alibaba.lindorm.contest.compress;

public interface Compress {

    /**
     * 数据压缩
     * @param data
     * @return
     */
    default byte[] compress(byte[] data) {
        return data;
    }

    /**
     * 数据恢复
     * @param data
     * @return
     */
    default byte[] deCompress(byte[] data) {
        return data;
    }

}
