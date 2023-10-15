package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.util.Constants;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;

import java.util.Arrays;

public class ZstdInner {

    public static ThreadLocal<ZstdCompressCtx> ZSTD_COMPRESS_THREAD_LOCAL = ThreadLocal.withInitial(ZstdCompressCtx::new);
    public static ThreadLocal<ZstdDecompressCtx> ZSTD_DECOMPRESS_THREAD_LOCAL = ThreadLocal.withInitial(ZstdDecompressCtx::new);
    public static ThreadLocal<byte[]> BYTE_THREAD_LOCAL = ThreadLocal.withInitial(() -> new byte[Constants.INT_NUMS * Constants.CACHE_VINS_LINE_NUMS * 8]);
    public static byte[] compress(byte[] var0, int level) {
        final ZstdCompressCtx zstdCompressCtx = ZSTD_COMPRESS_THREAD_LOCAL.get();
        zstdCompressCtx.setLevel(level);
        byte[] var4 = BYTE_THREAD_LOCAL.get();
        Arrays.fill(var4, (byte) 0);
        int var5 = zstdCompressCtx.compressByteArray(var4, 0, var4.length, var0, 0, var0.length);
        return Arrays.copyOfRange(var4, 0, var5);
    }

    public static byte[] decompress(byte[] var0, int var1) {
        ZstdDecompressCtx var2 = ZSTD_DECOMPRESS_THREAD_LOCAL.get();
        return var2.decompress(var0, var1);

    }
}
