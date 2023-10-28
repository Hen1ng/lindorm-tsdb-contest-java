package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.util.StaticsUtil;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdDictTrainer;
//import net.jpountz.lz4.LZ4Compressor;
//import net.jpountz.lz4.LZ4Factory;
//import org.tukaani.xz.delta.DeltaEncoder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class StringCompress {
    public static GzipCompress gzipCompress = new GzipCompress();
    public static String[] dataString0;
    public static String[] dataString1;

    public static String[] dataString2;

    public static String[] dataString3;

    public static String[] dataString23;

    public static String[] dataString4;

    public static String[] dataString5;

    public static String[] dataString6;

    public static String[] dataString7;
    public static String[] dataString8;

    public static ArrayList<String[]> data;

    public static ThreadLocal<BitSet> BIT_SET_THREAD_LOCAL = ThreadLocal.withInitial(() -> BitSet.valueOf(new byte[2]));

    public static byte[] dictionary = "SUCCESS".getBytes();

    public static DeflaterCompress deflaterCompress = new DeflaterCompress();

    public static volatile ZstdDictCompress zstdDictCompress;

    public static ZstdDictDecompress zstdDictDecompress;


    public static ZstdDictTrainer zstdDictTrainer = new ZstdDictTrainer(256 * 1024 * 1024, 1024 * 1024 * 1024);

    public static AtomicLong COMPRESS_COUNT = new AtomicLong(0);


    public static byte USING_DIRECTORY = 1;

    public static byte NOT_USING_DIRECTORY = 2;

    public static boolean USING_MAP_COMPRESS = true;

    public static boolean NOT_USING_MAP_COMPRESS = false;

    public static int UpperBoundByte(int valueSize) {
        return ((valueSize + 7) / 8);
    }

    public static void setBit(int index, byte[] bytes) {
        int byteIndex = index / 8; // 每个字节有8位
        int bitIndex = index % 8;

        // 创建一个字节，其中只有所需的那一位被设置为1
        byte mask = (byte) (1 << (7 - bitIndex)); // 7 - bitIndex是因为我们从左边的最高位开始计数

        bytes[byteIndex] |= mask; // 使用OR操作来设置所需的那一位
    }


    public static void setTwoBit(byte[] values, int index, int value) {
        if (index < 0 || index > (values.length * 8) - 2) {
            throw new IllegalArgumentException("Index out of range.");
        }
        if (value < 0 || value > 3) {
            throw new IllegalArgumentException("Value must be between 0 and 3 (inclusive).");
        }

        int byteIndex = index / 4; // 2 bits represent a value. So, 4 values per byte.
        int bitIndex = (index % 4) * 2;

        // Clear the two bits at the position
        values[byteIndex] &= ~(3 << bitIndex);  // 3 in binary is 11. So, this clears the two bits at the position.

        // Set the two bits with the value
        values[byteIndex] |= (value << bitIndex);
    }

    public static int getTwoBit(byte[] values, int index) {
        if (index < 0 || index > (values.length * 8) - 2) {
            throw new IllegalArgumentException("Index out of range.");
        }

        int byteIndex = index / 4; // 2 bits represent a value. So, 4 values per byte.
        int bitIndex = (index % 4) * 2;

        // Extract the two bits at the position
        return (values[byteIndex] >> bitIndex) & 3; // 3 in binary is 11. This will mask the two bits we are interested in.
    }

    public static int hashCode(byte[] value) {
        int h = 0;
        for (byte v : value) {
            h = 31 * h + (v & 0xff);
        }
        return h;
    }

    public static class CountAndLength {
        int count;
        byte[] bytes;

        public CountAndLength (int count, byte[] bytes) {
            this.count = count;
            this.bytes = bytes;
        }
    }

    public static CompressResult compress1(ByteBuffer[] stringList, int valueSize) {
        ArrayList<Short> stringlength = new ArrayList<>(stringList.length);
        int length = stringList.length;
        int start = 0;
        int total = 0;
        ArrayList<byte[]> arrayList = new ArrayList<>(stringList.length);
        BitSet compressBitSet = BIT_SET_THREAD_LOCAL.get();
        compressBitSet.clear();
        compressBitSet.set(15);
        int index = 0;
        while (start < length) {
            IntObjectMap<CountAndLength> set = new IntObjectHashMap<>(8);
            int count = 0;
            int totalLength = 0;
            boolean isUseMap = true;
            for (int i = start; i < start + valueSize; i++) {
                final ByteBuffer byteBuffer = stringList[i];
                totalLength += byteBuffer.remaining();
                final byte[] array = byteBuffer.array();
                final int hashCode = hashCode(array);
                if (isUseMap && !set.containsKey(hashCode)) {
                    set.put(hashCode, new CountAndLength(count, array));
                    count++;
                }
                if (set.size() > 4) {
                    isUseMap = false;
                }
            }
            if (!isUseMap) {
                ByteBuffer allocate = ByteBuffer.allocate(totalLength);
                for (int i = start; i < start + valueSize; i++) {
                    final ByteBuffer byteBuffer = stringList[i];
                    stringlength.add((short) byteBuffer.remaining());
                    allocate.put(byteBuffer.array());
                }
                total += allocate.array().length;
                arrayList.add(allocate.array());
            } else {
// putDict
                // dictSize 1B
                // length - string
                // length - string
                // --- string ---
                // indexToLength
                // if dictSize == 0 => indexToLength = 0
                // if dictSize == 1 => indexToLength = (valueSize+7)/8
                // if dictSize == 4 => indexToLength = (valueSize*2+7)/8

                compressBitSet.set(index);
                StaticsUtil.MAP_COMPRESS_TIME.addAndGet(1);
                int dictSize = set.size();
                if (dictSize == 3) dictSize = 4;
                int dictLength = 0;
                for (ObjectCursor<CountAndLength> value : set.values()) {
                    dictLength += value.value.bytes.length;
                }
//                for (CountAndLength countAndLength : set.values()) {
//                    dictLength += countAndLength.bytes.length;
//                }
                int BitSize = valueSize;
                if (dictSize == 1) BitSize = 0;
                if (dictSize == 4) BitSize *= 2;
                ByteBuffer compress = ByteBuffer.allocate(1+dictLength + 2 * dictSize + UpperBoundByte(BitSize));
                compress.put((byte) dictSize);
                for (int i = 0; i < dictSize; i++) {
                    boolean isExist = false;
                    for (IntCursor key : set.keys()) {
                        int hashCode = key.value;
                        final CountAndLength countAndLength = set.get(hashCode);
                        if (countAndLength.count == i) {
                            compress.putShort((short) countAndLength.bytes.length);
                            compress.put(countAndLength.bytes);
                            isExist = true;
                            break;
                        }
                    }
                    if (!isExist) compress.putShort((short) 0);
                }
// 写字典 id -> string
// 写string -> id
                if (dictSize != 0) {
                    if (dictSize == 2) {
                        BitSet bitSet = BitSet.valueOf(new byte[UpperBoundByte(BitSize)]);
                        int index1 = 0;
                        for (int i = start; i < start + valueSize; i++) {
                            bitSet.set(index1);
                            index1++;
                        }
                        compress.put(bitSet.toByteArray());
                    } else if (dictLength == 4) {
                        byte[] bitSet = new byte[UpperBoundByte(BitSize)];
                        int index1 = 0;
                        for (int i = start; i < start + valueSize; i++) {
                            final ByteBuffer byteBuffer = stringList[i];
                            CountAndLength countAndLength = set.get(hashCode(byteBuffer.array()));

                            setTwoBit(bitSet, index1, countAndLength.count);
                            index1++;
                        }
                        compress.put(bitSet);

                    }
                }
                total += compress.array().length;
                arrayList.add(compress.array());

            }
            index++;
            start += valueSize;
        }
        short[] shorts = new short[stringlength.size()];
        for (int i = 0; i < shorts.length; i++) {
            shorts[i] = stringlength.get(i);
        }
        ByteBuffer allocate = ByteBuffer.allocate(2 + total);
        allocate.put(compressBitSet.toByteArray());
        for (byte[] aByte : arrayList) {
            allocate.put(aByte);
        }
        byte[] compress = Zstd.compress(allocate.array(), 3);
        ByteBuffer res = ByteBuffer.allocate(4 + compress.length);
        res.putInt(2 + total);
        res.put(compress);
        return new CompressResult(res.array(), shorts);
    }


    public static ArrayList<ByteBuffer> decompress1(byte[] bytes, ByteBuffer stringLengthBuffer, int valueSize, int totalLength) {
//        bytes = gzipCompress.deCompress(bytes);
        stringLengthBuffer.flip();
        byte[] decompress1 = Zstd.decompress(bytes, totalLength);
        ByteBuffer wrap = ByteBuffer.wrap(decompress1);
        ArrayList<ByteBuffer> byteBuffers = new ArrayList<>();
        byte[] compressType = new byte[2];
        wrap.get(compressType, 0, 2);
        BitSet compressTypeBitSet = BitSet.valueOf(compressType);
        int index = 0;
        while (wrap.hasRemaining()) {
            boolean b = compressTypeBitSet.get(index);
            if (b == USING_MAP_COMPRESS) {
                ArrayList<byte[]> arrayList = new ArrayList<>();
                byte dictSize = wrap.get();
                for (int i = 0; i < dictSize; i++) {
                    short anInt1 = wrap.getShort();
                    byte[] dict = new byte[anInt1];
                    wrap.get(dict, 0, dict.length);
                    arrayList.add(dict);
                }
                if (dictSize == 1) {
                    // no index
                    for (int i = 0; i < valueSize; i++) {
                        byteBuffers.add(ByteBuffer.wrap(arrayList.get(0)));
                    }
                } else if (dictSize == 2) {
                    byte[] values = new byte[UpperBoundByte(valueSize)];
                    wrap.get(values, 0, UpperBoundByte(valueSize));
                    BitSet bitSet = BitSet.valueOf(values);
                    for (int i = 0; i < valueSize; i++) {
//                    stringLengthBuffer.getShort();
//                    byteBuffers.add(ByteBuffer.wrap(arrayList.get(0)));
                        boolean bi = bitSet.get(i);
                        if (bi) {
                            byte[] bytes1 = arrayList.get(1);
                            byteBuffers.add(ByteBuffer.wrap(bytes1));
                        } else {
                            byte[] bytes1 = arrayList.get(0);
                            byteBuffers.add(ByteBuffer.wrap(bytes1));
                        }
                    }
                } else if (dictSize == 4) {
                    byte[] values = new byte[UpperBoundByte(valueSize * 2)];
                    wrap.get(values, 0, UpperBoundByte(valueSize * 2));
                    for (int i = 0; i < valueSize; i++) {
//                    stringLengthBuffer.getShort();
//                    byteBuffers.add(ByteBuffer.wrap(arrayList.get(0)));
                        int value = getTwoBit(values, i);
                        byteBuffers.add(ByteBuffer.wrap(arrayList.get(value)));
                    }
                }
            } else {
                for (int i = 0; i < valueSize; i++) {
                    int size = stringLengthBuffer.getShort();
                    byte[] bytes1 = new byte[size];
                    wrap.get(bytes1, 0, size);
                    ByteBuffer wrap1 = ByteBuffer.wrap(bytes1);
                    byteBuffers.add(wrap1);
                }
            }
            index++;
        }
        return byteBuffers;
    }


    public static byte[] compress(byte[] bytes) throws IOException {

        return Zstd.compress(bytes, 12);
    }

    public static byte[] deCompress(byte[] bytes, int valueSize) {
        return Zstd.decompress(bytes, valueSize);
    }

    static {
        dataString1 = new String[]{"e", "\\", "a", "a", "8", "O", "C", "y", "m", "W", "K", " &", "#", "e", "\\", "4", "\\", "'", "[", "q", "m", "G", "C", "\\", "i", "K", "i", "'", "q", "'", "i", "O", "&", "m", "#", "m", "a", "a", "q", "u", "8", "S", "0", "O", "'", "m", ".", "u", "S", "u", "S", "e", "S", "4", "G", ".", "K", "\\", "u", "W", "y", "u", "e", "&", "K", "-", ".", "S", "i", "&", "8", "&", "C", "0", "&", "'", "-", "0", "4", "i", "y", "0", "O", "8", "m", ".", "m", "a", "'", "4", "G", "[", "i", "'", "C", "8", "-", "C", ".", "#", "y", "\\", "O", "S", "-", "4", ".", "q", "i", "W", "8", "W", "-", "q", "a", "i", "e", "a", "a", "\\", "#", "8", "K", "e", "-", "i", "e", "a", "u", "G", "0", "a", "W", "a", "0", "0", "y", "K", "m", "i", "u", "m", "[", ".", "a", "a", "G", "i", "'", "4", "u", "e", "u", "q", "u", "#", "C", "i", "4", "-", "-1",
                "0", "0", "1", "0", "0", "0", "0", "1", "-1", "0", "-1", "1", "1", "0", "0", "-1", "-1", "1", "0", "0", "1", "1", "0", "1", "0", "-1", "-1", "-1", "-1", "0", "-1", "0", "0", "-1", "0", "-1", "1", "-1", "1", "1", "0", "1", "1", "-1", "-1", "-1", "0", "1", "0", "0", "-1", "1", "0", "0", "0", "-1", "1", "1", "1", "0", "-1", "-1", "-1", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "1", "-1", "0", "-1", "0", "-1", "-1", "0", "1", "1", "-1", "1", "1", "-1", "-1", "0", "-1", "1", "-1", "0", "-1", "-1", "-1", "0", "-1", "-1", "1", "0", "1", "1", "-1", "1", "1", "1", "-1", "1", "0", "1", "0", "1", "0", "0", "-1", "1", "1", "-1", "-1", "0", "-1", "1", "1", "0", "-1", "1", "0", "1", "-1", "-1", "1", "0", "1", "1", "1", "1", "0", "0", "-1", "0", "1", "-1", "0", "-1", "-1", "1", "0", "1", "-1", "1", "1", "0", "0", "-1", "1", "1", "-1"};
        dataString0 = new String[160];
        dataString0 = Arrays.copyOf(dataString1, 160);
        String[] dataStringTmp = new String[160];
        System.arraycopy(dataString1, 160, dataStringTmp, 0, 160);
        dataString1 = dataStringTmp;
        // 数据字符串
        String data23 = "\"u4S00a&'a\\.#GS.\\0ummeS\\aqWm&GC&&.i8[\",\"u4S00a&'a\\.#GS.\\0ummeS\\aqWm&GC&&.i8[\",\"u4S00a&'a\\.#GS.\\0ummeS\\aqWm&GC&&.i8[\",\"44&#qyeG0e'uS'4[&yKaqq8[4S8m&mu\\.84miO8\"\n" +
                "\"&a4\\O[.ei.404mSqSu4qqWG\\4u&0[q#aqm\",\"&S0ae#K&-'qG'Wa..8[.\\#qe4-aSyq\",\"['Kq4[&G0yKaS'\\#OuK0uC44'CCK&8\",\"q#umKmSm0K&\\mW\\#KC&ii-8.8CS8Ku\",\"e\\SK#4Sm#4\\4'e.\\i[S[Ka4i0e80'aGC4GOG\",\"a4'8qu4S-O[uG8.ymyWO8W-mqyy4-O\",\"\\8[S&8[OG-mGqG-Km8mamOuGu0K&CuyS\\y\",\"y\\OWmueGi.O[e.K['WyGe8Kmu8yq'K\",\"0.mO0mGKaae#C-G#4#yeG.-W8-'G..\",\"S[W&8a-muCSOeiSa0mG4uGK4\\K4Gi&O4&4&&\",\"\\maCCS\\[Oy#'aWu4&&4KK.qC#88[KO\",\"-aK4K-\\080miq[GG#q#.#GGKS[GKiuaaymG[\",\"e-0'-qqK4\\4[-[#emaymS-m'i#a'G#yiW4&[\\W#i\",\"G8i0CSa4'm-iW'S&q#0\\G-W-KG#uGym\",\"eW.#&'uSi[8u8Sm#O8-#aaeC'&S\\e88eiKa4'\",\"8[WaeyWeG[O&u8GiC&'8[-'aO-OyG&#\",\"0'ia4&O4q'&i'#80G8#qmeWqGOimWW4CCaq\",\"'8a&W.O&-0myym-.iC.GKq-u&KKi[4'\",\"\\i[.Sy#\\aCO.#qaq.yWSei.[-S0qOW4S[e\",\"i-WmKC08G--auC\\y#[WWSaaq[-aOO#-#a['4u\",\"S##\\yGWS4Geyam48u00.q.[4uCCS#SuaW-e\",\"4q8Oymu-O\\\\\\S.Km4Kaa4eSe['q#'0&.uOi\",\"[uGe&[.i0'mu[e.#&S.iy-Gie88K4G.#4\\G0mS\",\"mm0m'Ky8-#&G'8&y4q0O4\\.G88iSS0y\\[iu0Oyme\",\"y[#.[-\\-80a0G-#'8S#a4yeCKCSa&ieymGSa\\0\",\"8W#O-u'-\\G[uq\\ymi&e'&'O88-#&.O-mem[u&.\",\"'-[u8\\u&0\\m-0u'8O-'G8OeqaeiaKSWyq\",\"8yiu&.W\\GC\\4eW#-KKaS[0G4'[q#8[4i4'i\",\"[COGK4'4&S0mqKm0yaqK\\-0y\\K&8[Cmue&qO\",\"qqyiS''G.-OC'#y\\uayKq4qG8aO8.i-uWu8.W\\-u\",\"S#0eum[m8-#GK[GC4\\K&'K0mSmGiSme\",\"\\qiG4iKq-\\SGayW4&\\a04a..8y0WGK&O\\mi\\\",\"\\0S0CCyWKu-i.Kq-mG#m44e8uy'aiy0m'ym0.8\\\",\"\\K#&4C\\-#GWiCa.m4C'.SCmei'-aq&&qS#4qCKyS\",\"&-emqyy'[4&-meG8K'8qq0.[-Gy[ueaWSae#C\",\"&auqyaeuSm-aeiWG'Ou[&Oim4u8K4Sa\",\"GK'eWG8iaiG8\\OWm88#8&a[euqamSa4e4&44mi\",\"..CKG--a4S[4Su4.a4Ci'mW4yWamG[-#8ieS'\",\"KSWmu-e-O'yGOKO'Gq#a\\S\\#ym8.8G4CCimKS\\\",\"u-yy&G-C'aOu&WeW-\\eaCy#4CGamSu88KWu[q\",\"Ca#C\\#Oa\\\\.-88SuaG-&miuGK&OeOC-y4-Sa#4\",\"qqK04qOaa8iiaq0K#OWSOC4SCe4..i4'4\",\"8W-SGqCe[OS&e0yyW[q\\u'q[8iG.Gq\",\"\\'&y-\\aqW\\Wue\\0G&m8[a44e-m[[GWyau4CW-G\",\"#a8.84eum-CW4#ee#8&iOy0#\\yO&4[.ym#&0eSy\",\".\\KSO'SyW&0[-aGmeu8Ka'eOGqGS-.\",\"u'GOKK\\[4SOuuq[e\\G[&'mu\\0[-\\q0&&W-OGyG\",\".4-yK8W-a#eu8u-G'\\4.i0.[48[OS'iO0#4&\\u\",\"u8#qeK&&CSy0\\WmmSC8-0CmOO\\O0KSOWC\",\"-Oya\\GOS\\aC&##u8mO88i.[e'[-yWu&q##48G0C0\",\"uKiy.u'u#KO-e-y#SOC#qiq[4Se.[-y#\\q\",\"-#Ka\\KayeOymu0u-\\W88'q&'.yyO-8yK\",\"i0'8.#[SG#OK.8OS'-.K-qm-#'.&-um'4q\",\"#uO0qm#muemSKq\\y#&'#mW#0[qGWuS\\'Ciy\",\"m8WqimuqGWGiWeG[4CW&'i4\\&\\40K0#qCW\",\"e8ay\\..[imqGua&W-ye4&&4\\4mWmGyqWS[iOW8u0\",\".WK0.yK0COeWKC\\yqym4em-.K&umee[W.\\0\",\"y4iSKqm4uS8.8m[m\\\\m\\'O4GyO\\CW4.'Cm'\",\"a4Oi8y-y&yWu.#u4O-mWqe[4\\#\\.8KKG88i\",\".'Oqa\\#-q#i8yy[i'myuS&4'&WiqSe4a'q[KKei-\",\"-4'&WqK&8qy0Ke&8.''#O'0y4e-Gm\\Gi8WGS\",\"'Ce0OmaOWeW-Kq#COuSyOC8uG#4\\[Km'.u\",\"mm0'au[G4&GW-.W#SKC[i&i&OK#ye&iu&e80'[-\",\".K8im0K0WCOSK0e8#&eq0-#\\K8y#a&#&i0\",\"#\\G888##C&qCO\\[-i-mC&'aCCOG.y8-0i\\iO\",\"u#iKqmmSO8uq[ia&a#COG48iKum[8umei\\&\\8O\",\"[008mq'Sy[C.aiW-OW8K-y'SyC.eW-a8\",\"'e#CuqKKm8...K['#CC4W\\yW\\aOaSSyW-'SaiW\",\"KCGK-4-[.[#C8O[C4SS\\aO[SmG4qq##Oa.#&u&G#\",\"S-ee\\4yC[8GC4&#aCymiq4qWqei##.K\\mqS\",\"Sye84#m-\\m[WCOeeK-#ue4[G&i-qKWqae\",\"CmCe#'C\\qKq\\W&yG8W'C'SSe84\\a-uae8&\",\"SSae.yWWS&S#emO-88yaOii0yiS##OC4\\-\",\"i0.Wq#K&Gi&-'8\\aOqu&-OuG[G\\Smmeq0#q\",\"Waem8#'uS0.K4W-m'q[G00m8Ce0W\\-\\O0m\",\"&W8-a-C#yK\\am8i&uSO8[4u8OieSGW\",\"G[8u'SS\\qOWyKueu.8Oy0y.Gqa\\\\'[0\",\"yKmaq8ima88#-a&a\\m[-\\-[-m#uO#&S''\",\"a[S4\\&&-i\\..i0mSOS[\\mKSe&.88yW'Gy\",\"C'&W\\8S[OS0KG.8-m8[ma#COq8\\yCWe&4\",\"qOGW8ym.#'0[[WCKm-\\Gy[G[qGK.WSW\\.'[K\",\"K[eqK#iy\\ia\\.ue8u&8y'qiuS&'CqW#Saqm\",\"C&yWue[Wmim-q#a[\\.meGySKaaq[8#q&\",\"#a-yG..Ceu-&GCW-iC\\[KCi..yCy4y-#S[uGy'C&\",\"\\SeyOS#\\yiWm[4C'S'.WaOKK\\eK.\\4\\W'0qWGu8[\",\"qm-K4&ym-8[8eqyWqOuqK\\44S'8y-0mm.[\",\"8'4C-C#q4O-Ci0.KOKOaq'&.K.Kyq8e\",\"G#&yiaa0&-uy&i#KmWqO-O[\\OO.CyaC80\",\"iiW-\\y4\\#8884Cy4G[4'WK\\4Ce8\\&Km\",\"aq\\aOS0'G.y\\0#G.GiWa8WqW4i0'4eWmS\",\"[q&q-SeW#i\\a'O0e-[S.8y.qOS'aeS#O#08-\",\"8m0y#aS\\yy.iu#0##K&G##q[u[&G[OamWC.KC8&\",\"Sm[K\\y-C[O'4Gm88\\a0u[..88-y&u[SyW\\S#Oy##\",\"KyS&'uS[K#S-0euKC&0#SmKe.#a''-yiGC'4a&.[\",\"WuO'0#qayy[C'W0a0W00.\\a&#&'0[4ei8uOS-eKG\",\"euqa&CK-CaqOueOm8eGeiCK'aOqGm4&8Km0\",\"#[C[G-aqOCOqu'&S[y4eiC\\W00i\\imi4\",\"Oi0iayi-#4-\\[e.W#4u8G-O&u4-KS0ai\\#\",\"ae\\Ku&aa\\mK\\K0'G8[m8iy-mm8am-Gia'#SW.S4\",\"44#&4qm'ei\\KOGaqau&iyKS#&aamiqy8KeGK&O\",\"q8&\\0yKqeqa&m.i[G0qm8mu'0Sqae\\m\",\"uum8Ky.i80S#&Cq8yW-0[[''q[qOGmC&iG'CaWq\",\"Wq[SeCy-a&W#WO\\C\\S0#&.qKK[iG.qOu#W\",\"Ga&Wqyi\\O-[GyW.e8#q4\\.4#'K[KS8WGm[#u\",\"m-mS.0[8O'qu'mWeeGi#&O8e8Kaumu'i.G\",\"'&8\\COu0KmS-.W8.84[-0.G[Sm&We#\",\"[We-SOiquS\\O0'G[Saeq'a'qKq'iO-mq0W.#\",\"y#Ce-'iqG#a&GaOaqWu'u-CCCSiO.0.ya\",\"Ky[W#\\.W'Sy-u[u'i.\\80q[qyumu8#y.'\",\"yWqGyK#miO-COC0[0KO-4G.yK#[ueK\\qyiu-C&m\",\"WWeW4KqCO0KO-eG'O#K&qi&GuiqC'[eeSqaa#0.q\",\"''C4W'eia&'.i&-0Gy8m8WK0aOa8#-G&&'\",\"&Sm&#qGi0S\\euqOWiCSaGuqK\\KWeiq[\\CS[\",\"[.iWS\\myKOmS4W\\e'ay[8&u#&&WWW&i&Gq[-m'.\",\"-aqy[4&-a&-&-mG\\-[S'S#uS'Gq\\y.K#O[G\",\"&a'G0uW0eSm\\88uaC'y[emq8-eO8a.i#4&Si80i\",\"&K\\0#O.'8KO4e-\\q#K.K4&#G'e-mq[G\",\"4qCq0#\\[#-mS0W.-&y'i880eOS'\\...''&y#qqy\",\"O#8y\\mWi44C&8S\\#&8i&qemGKmaamyaS['O[\",\"Kqm[4ueu4SymG-\\[GyKO0&uGWS\\#4qC\\Suq0\",\"miu'[-#-q0'SO#mKma\\-iO.ey[-0eOC\",\"-a[C'0&y8-KaSyK[0CuGKW'\\a8iOaOuG\\'S-u\",\"W.im04eKOKaay.iSq[aeCu&-uuOG4S\",\"yi\\[40m.i4\\#euCqy[uyWa..e4qy4u-q0ya\",\"-yKiKiaGW4'G.[u-u\\m8WqC.8O..#uCW\\WOG[Sm\",\"&0\\[S4&W00q\\i##0#0.&GOSi#OG&Ky8.KSe&'G\",\".KKqe#aO8ya4eeiKq&-Ca\\'e-#\\m4eqy\",\"ii.a.iq0Wmy[S4eeueGCOya0W-y8C0\",\"C'O&-\\#\\y\\\\.88C\\aWSG8u\\#ueWuS\\uue\",\"&W\\aO0[8.ayayim&O8K#CK00.m44'ae80K[Kaae\",\"yCe-O'W--WS4K0y['&K0meuK#4[CyO\\Oeim-my\",\"&CW0yime4q4eu'K4Ku-iO0i[uC4\\eG-\",\"C-Sm#\\[GO#O8#Cqi-e8mOiqy['ei#O\\W'y.eiCey\",\"CeCC'iaKCqaK\\yy#aq40i&aKWG[0yySG0OG#.mK\",\"aiy[\\0KW.S\\GCOqe[C-SaKq[Cay.8\\W4iC\",\"0KmC&KeWu'[.-y[eG\\[-[4C'a'4\\GWWWiOWC\",\"\\y8.y#\\&WqauW'4&KmmW#4#-CaCqG8KS[8iS&-\",\"[#a0W.#C8i\\mu&iyWK8q0y08-y#'44eOS8S\\y&\",\"Kmeiu'qO[ySSiim#qa\\0KSamS0#\\4q#[\",\"\\ue8u.ia8'G[-#a4u'8i#&4&[uG&-K4#qaau4W\",\"0&'aaa04a&iG[eCqO&'eW'\\'GK\\q\\m&Gui\\\",\"#W0.qO'G'G&-ymi0#.\\OyymyCu--\\mCCO\",\"&..8uaeGK\\8C[u'&WyyCSu[G#\\eiCO'4q00[\",\"&4qS\\\\CeGq8SyWOGKSOGKqq'GqO4eS\\mm\",\"iS0-.&&-0SW8a[m8GSWG#0-am-mqqyWme#4Oy\",\"yWSmK'GSuGi.OG[ue0y'&8.#8#S8''WOW\",\"8qmi\\4muK&.S'i\\y[4mi.#m-0C#OSKW\\.'ae\",\"-\\yG-a&Gy4u#Gu[S\\Gy8.i-CC.W.q'C#yq\",\".imSaquSK&uuSiS0eu-.0\\ey[eq'Ce.8uC\\\",\"-iK4qOW'W'q&4W0W-ye[#0[iya#&#OOGu\\[-\",\".y.y.'[a\\#y4G&Gu'C&W8W.88W8\\aaGq\",\"'\\#&GO'&Wq#44#iy''KC4\\'8ae['ySO0\",\"..4\\'qG.00G'W&GGK&.&-&GKOG#uW44\",\"OC.&'OS[4.iWG[Si.OSe#\\'G..4.O8[y.SK-G\",\"eKaa\\.8uO8[qWS\\&4q#-eq8&4Gi'-m'&GK'88a\",\"[q[.8y[aO-qS&iOq0y4C'i#i'Gy&O#CS4'Ki\",\"G0KmyWm\\OG.eCKCO&aeKqKq#4'mm''-qi&mSOqe.\",\"..iaWCq&.C.##CCuS8WuqKS4u\\S\\-aKG\\#W.\",\"0G8yuu.aW&8'CSK&q'C0iqemu&&iK&e4uiWGuSWS\\['8yy44\\ySO8.OOu['0WO##q[--00.yCm-iG8S0C4e-00y'&0#q-&a&\\a\\#\",\"KG'K[SyOi'4OO'e#G.0O-\\[.i#y'-0W&u8eiGiSyiSyS[W444yKC#.y\\miuiCS'#m4u'a&'4K'CO#K4yK8yCCW'CS'0e&8C[0-0C\",\"i&-0ieq#4[&-0m'ei'ueuuWa8OGGC4aCOm#O.44K0OK&&u'm&u#0'4eyW-4-S-#-e0Ky#4aaaC4\\0[WuiaKS'OW\\CKOK&q0['S\\e\"\n" +
                "\"Wu&##'eKC-\\a\\WOK0Ga['KGq8a4yCqm[SyeK[K#0WG-C.#WGa-e84q[K.-OW0e8uqCqaCy-emmqCqS#Kqu'Ki'&'u-&S.Wy#4'ai\",\"qia\\ye.OimWuO'Cm4'aOy0-&.W'qui\\G4ee.8m'4'mCaS-yq-\\4[e#K0m'\\8yKia\\-OOeWKO-#uKC&W8'qme[0W4SOW4i8qWWOCq\",\".\\K[e-mu8yOWiW\\0aa'.mS[i#.iaO8yi\\0.KOGCOu0-m.OiaCK#W0u&8[Gy\\K0.#G\\qq0'&00y04-mGmqK0q4.'#q#Cq&[00['SS\",\"[e8W-ymuuS0[\\eK&#Wi#Oy-u4yyGKO8q\\GC\\u-'[K\\y-m[SyW''Cq-#aq0qW#4Sy#e.WeWqm0C4Kmqy#mme-\\WieCS&u4-0[\\0'S\",\"G-iG[\\y0CC.O&8.#&.#\\#u.yuS\\.yOi#O\\q0meGC.Oqa\\4OWSSmyqCeima4iqmqaeq&'0[i0GaueG['i&-qG-a-a-Kq#a8y4q#SK\",\"8KaqK\\eiO8[0mGu[&GC\\q#[Sy-aWG80Gmy4K88a\\euy--0yG0.i0\\me8['u4e''8iK--OimuGyS.&i#u\\K\\Oi-\\.8i884SuCW-W'\",\"44eu-OyO4[mSa.[yWuOuiG0yyC\\..[.8qCGGKS-.q&uS\\K.[qy.aC8.G.W'OeS040SOOO[yy-'O8.'[KWG[4.[&i#\\WW-8'0um\\'\",\"'#CmuGaSW\\&'y--muKOS.8.8#aWmOKWeWm#&KCCeK8W&.#&WO\\#\\y08G-8-aO8e.0\\emqKq8SKeaeW4&ii''q4S8i[G\\y[Sy4''q\",\"y0#\\CuOu&q4iOiS\\q[WSye0&'4u#mq&Sa4qyKm-q8SqyiOq'GCySO&y-aquC'#K\\K&.W\\[eK0W[8-G0OC&C-O-\\WW'&mC-C\\0yeu\",\"uCqa.mi'.0K0..8SGO-yOuGO&\\yqO\\i\\e8&[&m-O4Cm\\yWGuG'-WSy0Cq#4&qO'-O4.q#0C\\e\\Cei\\Wi'0OC#\\eiKG[4Cu-.Kq[q\",\"0q0CeaW8q\\yCm[--C\\\\[Ce.WWe.qm8C\\.mu-&.[0\\mG.Kyuey'Gmiaqq[uSeiSyyyS\\0.0qqmai#Kme-mu[q.4O.[Ou\\G&4a'iK8\",\"KW&4OK[Sm&CSq[m'&#OaWeCem'4yKm0.u4q-0&W&'\\eGWKKSa-0'4&8K.O8mCOuam'u8K0\\KaOqa-u8-GWCKKe-#iyiGm0C-m\\OW\",\"mKyy.m8#OO'CS''4GK&&KSm'O0C0K#O'.[&WeOWe'SCSe0K40.a\\WKCSe0Wa4-KW'&aqa4uqO-mK#Ce44aeKW4\\KmKC4S..y#me-\",\"K#0KKmO04#-O4S0meGK04#uO..i8ii.'GKOmqWSOGim&aCG&u'&'#\\y[W4W.Ki'a.4eC-y\\mi-OyOyyW4aK#eWuei0SS&-am-GKS\",\"O0#m#'&#\\#0.q8-WSOiiOeimSaeeKqa&'GGeKyaOmm'qGKaCS0iW&i-\\O4-q-0yG-S'-WGGKayKOCi..OSauS\\ayOGuGWGyKe..-\",\"G8y#y.8y#-OC\\#iOWG\\4&&#CKi#S0.'qaaeu'80Gey4[Sy8yi\\.e.iOu[mu&GO'8[G8-'CGCGWyeuaCqKK&..[Sa8K-y#8au[[.[\",\"yKqOqq8KCO[S8mu[#am-O\\.G-4a\\\\euSe'G.\\4G\\yOWmGG[q'KO'8uO'G\\0W#OuCS.mS[WKC8CqaW#am-Oe4mS[O-aiKaW-q08u0\",\"8yaimSG.Wqm48qOe0.#G8-m.[#qWS.'GieGieu#qyWyy#uyu[-mm'eiaaSOGSGW4[#iO\\uS#8WaeOCCG[e.yimSuyS\\CqGGK\\-um\",\"\\.#[eWu&\\8'u4CC#CW#Ki#qC\\a4uSmi0y[Gm''e-Sae\\8S\\[G[[\\#aaaCq[OCeyqqGqaeGu-W-0C4GOm'-mGmGqW4uOOqua.&.q\\\",\"[\\\\0'a&0mu&SKmeW[#&[uua&yWG[&'W\\--SaC\\Ka\\0'O[48uC.mGuGWq04Sy[4eKa4\\y0S08W480GqO'e#C#W-0['K4C0[CSe'4S\",\"KG[mG-O\\.&0i\\.[auWqOu&8q\\mueKae[SOWuu4##qqW.#\\q-u-SOWuq[4mW8y.y[ei-'&.#Wu.K8KWe-#qS4S8OSqe&8miae-O&u\",\"qKWqKe4qi-yO-eK0Gqm.CeWui\\y&8-ya\\\\-WK#&G.'[&8iWO'OWy#KC[\\q0.WGuyWmW0K.K4a4ii\\WW&\\SK[i'qW[e08&OS[0&&\\\",\"'C&[04'&C0\\a\\8qm-\\&Cu0mS#\\#yWa0&ii8imS.#&&.&W8uC0Giae4muS0[[ea&8[S8\\OGy0W&'-#\\&4SOWue8.ae8KmWuK#mSCC\",\"4a\\8Sai\\qOm-C-m-y4C\\8WuOO.WGuWG0KG'Wm-uqyW4y.eWa4S&aK&['#WOSmqyGi..#&-W4'u&[GCqa\\q4O8&SWim0#-[OSm.\\4\",\"O#\\mO\\m\\K8W\\m\\#a#OuGG4e-#aCia\\iaCmyqO444OGO[0eWyOC&0CyC4uWWeaG&&#uyG0KC&u'&m'e-yy[O'..uK4Sm'S8Kuq.\\8\",\"y.a'CiO[i&[OWuGW4SiuymuCey#-O'[mOq0G.88u0Ce\\qOe8'\\e00'GWyKSW'44S8W#K#Ce4CG4#yKmay.miWu-CCae8Kq-eG0Ke\",\"OOy'[4&.\\q0[aWm8aWq'\\yW&.e-S#['.\\'WWeKaC\\0WueWSW8OS0[K'e-yKOiaO&W'i0.i&S\\[0Km8KaWCWu4O8C4uWeiCim[O[e\",\"4S4SeymyyG#-mK0a\\#-y'SqK'4-OC&OiC&e.u#0q[4S'a04CKOOKa\\GaS&[4yyySOqmiay##4K0'#iaO''4&y[88yK&uS0000'q\\\",\"WGO-mm0OS80W\\##&[#\\[-\\GS#uSaaWiiuuW'uW4\\ia#a0CS#C\\aaqe.\\#yy'qC4KyeSSyy0a#WG0O'W0maa4W0eW#Sm'WiKqiy&G\",\"yO&&\\Geuq8KaG[-&u004.8qyWG.OWmG8-0#ue0O-mK'WuK0.#[&WqSO444\\8e#44a&\\-W-u#a.Ou4&.\\C#mKmC.OSyuqi'G#-W#C\",\"uGySyCeyC&q0.y.SG0qO8ymimS4\\K[eCm0W[.&eimS#Wy.Su4Cee-m[8Sm''&'\\eW4-'q[ie8[aaOiK&G\\'[Ou0-.Kae.\\i#['eq\",\"u&8y.WS4'i'&GK48&e\\au\\u-ySW-0#\\aq#0W8\\#Se#mWe0.umu'uOG8WOmeGKKeyuCi'4##Ce4&8uaC.4S#OGCO8S8.ymG-#Ga-C\",\"80\\\\y.i\\u8u&#aKa-ai[iaCqmmK-uqee&#e#aeimWG.8'[-8y4'CCSe0O0.&Wu-8Ka\\eSmG\\4\\0\\i\\8O\\y.C#.4Oi.KOue0Kq'GS\",\"WWuy[SSa'S\\aWeGWa4[eu[m.[0\\4ySCKqOSu08u'W\\aWS\\u&8.G4S-u[e-\\0'Sm.4Oeem4\\im08me[-OiyqSmiui[q.q4480y['C\",\"WqK\\'uyCaaSuOi.uWmK#-#yy8O#&8'C&i-8i4eu..Sy&-i#'KW-O\\-\\GWiu4aa\\#eqK0C4yyWiC&'SCS8mi\\.Smi0Ca.mS&iKG4C\",\"#mi&qaCqW.S\\[O.ayG\\W-#aqyK4'WO''\\&e0[&'q0'8#4y4q#m-y-Wua4''S#&u&-Gy\\[\\.e08\\8.#GaCGW'0&CmSi#qaiquOW&8\",\"&mSiaaOimG.[Oue[4CS..K#&C&4'[yi0GiSa4#uqOuuWOm'a0aW'aCq-a&WS\\u#W\\\\G8&44.C##&&'eWOi04aa#W[Gum8qmKCSu4\",\"&8#y\\a#\\W-K0KK\\G.\\\\O'qe[&G#&m-'q0i0'qWq[O[e#4[[W40[imamy84e[\\Gu-y[[G.\\8.ai\\.iCmu0#&WyW-\\8W0'C.&-yae0\",\"a.O.KaOKO\\m\\mWS.K0u\\GWC&G04S.8.W8W8-\\m8[8iyaO'OCy-Gim8ieKy[[q0i0Kqeq44u8&4'G-a.[ui-\\4[u4C[0KO4--G'q0\",\"iq\\aWG8iK&ia48'i4e.4&G[S\\.S['&im\\-'..[#Gq.[\\C''a'uGO-CCW-0q.K\\08'C\\C[ey[a&mGum8'eC8W#a&uCCO0.W-4Gq&S\",\"WG0yy[8iWWu&Kqay\\0m'S0-4-yu&OW'-\\0yOqyC&-#&WSC&ym&0uK\\#\\m-Wem'iu['88&8yKO-qm0q0SaSm0.mS0\\8Cq[e-\\KKq'\",\"O#.qaKWmGCqKa0ymmWG&[4G.#CK&[4[y.\\uee8S#yC&q#[--W#&GyGWqimy8iq&.OyW48W8-'&0O&S&&\\i&8ym''u.[S#m..\\a\\\\\",\"88'KOWuye&8Oa\\auG[y#4\\uq[GO4K.0\\S4&SSa4&0eCq0\\e88.Km-\\a'O8ai#O4Ke0[Sa\\aW4004[SCeyW-'CyuKmqq#0.COGWC[\",\"Sq[8Cy#C8iaq0#S'CG'.eWq['SeW8yymK#qi&mi'WWu4-u0[-e8\\m4eKWSy[mK\\[u'W#y8i8iO-aC4SWq0K.&G.W[iyiSK\\C..\\e\",\"C404i0mGe4#S&a-0u'yy4qu0KK.yyKa-yG-'.8[i.-aW[m[u4\\&.O[#qG40iSeO#CCq-i8Cuqy#GKWO4iS[uW.Kqq#0#qKO-a-&u\",\"a8Wme4WSmG0O4qy'Seaq.[Ka4e[8uaeeu-C\\.0'SOa0a\\[0eKm&e88#4ueKCCSa4ee-8ieS'aaS.m-K[GG4iaaC8iK\\GKm'K'O[4\",\"&G.KeC&OW0&O48uuW0-[m[m.8\\OuWGKC[me#0O--Kai'Ce-S[a'...C4'-OS0#mymaaOSG&.[qW4e0eGG.GWqO[WqO.[-40#8y#4\",\"0KC\\#e.[[G.-yW0aia[8qaC0SieGO'qK0eC&&mO-eqqm-.uqe''.myi\\#aiWS'qCm\\[y.u&ue.['#q-4aO8'yW0u#\\amSm8S#Oa\\\",\"0qO#W0i0KCO.i#8i&0yWmuaq.S[eauee.#e4e-muiC#\\&a4'yK['Oi.8[e8[S0i8[Su#-W-\\SuCqyqWWyGK'[e8#Wi[O[uiS&..K\",\"00y.4\\mCuymaO-#mimS0uGG.i#iCCu00mqaC4S#SaiO8'G8GqiOay--#ay-OOaa&Ky.eGW80#4CeS0u#KWqKe8e-yiSCiOSeGK#\\\",\"m\\0mW4uG0C.-q08uSu[WWO'eOS\\uyi0y#.8&8K\\&yi\\Km8KGKeieW\\y[8K0m[uqq&CC.Cyy#u-0KWqiy-m'C4KGqmuSay-q\\y'W'\",\"\\&i-4'S0\\0m4'8OGWSmyK\\&'&m0W[8O-\\qu'mi\\e[#0W8[K'W-Wa\\.''&i\\a#0GyWmie'C4'8mW-O#Si4qiC&.O\\q-##4q[G[q#q\",\"eK4y[GGu&'[m-q\\mCOi0&-\\#\\K.\\#'q-0imG'O-yWKC\\.G.aO4S\\.[yC\\&'u4\\KqWW&8eu'GW[W4K\\.#ai..-0-ii4qa'&.KS#Cq\",\"'[#C&G\\y\\[eCSe-#-a#qWG-a-iuGOaae0W&#S\\0##4.i8KqyO-0[-\\Kq--0GC&G&a48.e&&[i0WWaC#C\\.&'O&'eGuaO'eK'88Oi\",\"KKq.W44'4a\\aqaqKK.uS-O-a8Gm'K#SK&[..-CeumSq#C'Ka4Kmua4\\.8u&qiSW44eG84q0e[WC&GuSS-q88uKqO[84GmiO0\\eue\",\"&#0#4Se8[C&&GCCmGKCe8[.i\\.[SWSmKuCO'-mWGK\\K-O8yKWKy[4-S[CC4S0W\\WO'#eCqOG'88.ieaqa[eWS0aCyCa4q4yqK.-i\",\"e-y4eK-#&GG44myaS0C&.m-SC8Si0ey-0m-C-.eOeumS..ei-OO#0#qKmOee.#G\\OCC&eq'&4m#Cq8uW000[yG&\\8y[Wu-[\\uq[G\",\"--4-CSOGym8O'#uSe'CK-Ca&'''C#[#uG8.ym'-ay---Oue&OK&8.y.WGaC[q#\\aGKy.a4myGWi#88uKy4#m&'SSOq\\GSS[&-OiO\",\"&i4C8i'\\#0-C8yy.W[OiiOue-&#.WS'\\qqyGi#'\\[0ymeW'S\\8ye.#S\\OC.iWu\\e8.ea&[8\\u'i-\\..8yeu44SS#G-me.#Ga&\\.y\",\"mGiOqK4Kqe.uGeqi04ime4qK8yK#4C8.GW-aW8[\\0S4ymCq88G-O-'ei.8-CKO'mWS[KO80a0e.-miSu0\\.0WKaOO4C..iS&W'Ge\",\"yeuyiGuWeGuO-qm&eG8mS0[GKGuu\\KO-8ueO..iGi[y-0ySG#Saame&u&4KWSqO[-Wm8qi[e.i-[GW'4Wqe-mS.WS8eW8#iWW['G\",\"'C&e8OSOm4Sq&..miWSymO#\\S4CeWqKiaeiW4S488[W0W-eOyy&'y.[&[u\\a8&iOSaS#ym..SeKC8O0eKueyiW4mGy#\\m#8ieqSO\",\"C0[---4ueqmmG0eiCOue4S\\aOC\\eSqaeG00-C&-O.&8-0[y#4SS[e.8S-\\i\\8GO\\#a#0CW...W-0'm#[-.#.[yO#8WGy.08i'C8y\",\"SeW-C\\'G[4\\WK\\[8#SC&-4qOG8.&&iiO-\\W.Ke#Wy0m#\\'#m'e'[Ca8aCqau[qym0Ki&0\\8y.aqyu[.[eCii'S\\#.&[8u#KqKq8y\",\"a\\yW[G-0.W'[-iO44[S\\K\\[uOO&qm8OG-\\q[G8q-m-me..[Sy[W'\\iOiii#.\\[uyWi0Kam8WS44&e88iC4e8'Sme'&'u.[\\'ui0i\",\"4C&G-K00-#aq.i.8euyyK.W4CW0[auCW'#mK#[iS-[ae[a-my0KaC[\\0[OGueK0O[8uS[-'[4C'&#8yyiSuuO8.WqyqOW&'0-Ca4\",\"y-\\a\\[4e8.8q'K[-CaSS0mO8GueGWS0Ke''W-&GOK.-\\4-S8iuG[meW-ym4#'yq[4O[G8\\mi#00uS&KO8KWei&8&&uy#S44C.'e4\",\"qeWiO[O'S#[W4Oy&uqiCe#a4q0uWumaCy.0ySKqGKa4qqmO['-mG-\\#KC.eO8[Cm'eyW#Wqmiyy#ye84&0yey48W-&&u4&8imC4\\\",\"yWu&W[SKGWeW0&m\\'.CW-CG8uy#SqK#qKqGK0COCamKaW'm-&aW&4i'4&i&'i#'W#[\\.W4[.qW'4em--CSa\\G#'&-#KS04GK&'8\\\",\"[Ou-y'G\\KaWqyOSSaS\\8iCOS-CK.OW'C&-uiCyeK#-e-...SuqOS.e-a\\[WSay8y'40uuS#mS[0aq#4emG-ymi.Ca4u&8iOuG'#C\",\"iCS&GmW.##\\W'&#-y8i&.&8q#[SG[8&''&Wa\\#O.yS0-aKi.8yW'mKeaaq&'qG-CuCm\\S-iKi&4O.GOGi0Wy.#qKma-\\.GuS00C'\",\"[84&4'&qa4q'am\\8[GO\\S-y#CCWS&\\4Sm-#4'meaOuKmm0#mG'OGWG#4a&.#-S4\\ia.SSyyu&m#0\\m88ia&[.['G#G-mO-CC[&W-\",\"OCm8['4[\\.\\a\\8COe\\Kq[#KS\\0S[qmGSy#4e#eWum'mSOWy8y&.u4uSamKO'y-8-8[eiq&084[m.[&m#S'4Sa\\y04qyO&-\\GSym-\",\"C&uyC'm\\aOi40W-aW\\Ou4yC--yqCGuqCym8.y'&8Cm-#mm'Sy'Sq\\[4CeOie.84G0SWKqy.#q0#COq\\\\G-KO84Ki\\-8\\&iC\\88y#\",\"iy['8OW--y08W8WqKq4W\\\\K-SC8y[.[eCW#\\WGKyO#0i8G-\\0myG-K04yyS.euO'8KG-#yi8aKay&[i#\\#0#OG0G[e&K-muS#\\8-\",\"m0[G0##.#Cu'Guei0G4#qWmS8uS8.y\\8Omq0eWqy#4q.G.K.'&y'u8Oy\\&Gim#u[em'mO&iC-a&0SCW-00Ce&4&-4GK8aqGW.aSe\",\"40Cm[\\CuWWG'qaq\\S[O8'SO'q'[uqq--q8WeiK--4GuO-0m-q\\eyKOK8ai#KeayyuO&#OaO'.8\\S#O'eGqu4Ke..iCGW-##W&SW.\",\"-08&W..\\4&8qS0mGeqaC&88q-qyOC.uuWq#aOGWG['CC#y0O[[Gu-a4Guu.qyO-KmW#'KO8O#ey\\q\\''eu-'\\e#0\\-KGeK#K&que\",\"88G8W\\#'W-y4&.iu&Gaae.S0u\\'eGWG4\\aeKOqSOSy4a-mGmi\\ii&'C'eGK&uqy&m'C40a0[Ki\\uyS#y4.\\u8[Om#&uyi0yi0.[\\\",\".qWuCSGeu-ye-W&[&'e-CWW.uaa&0'[&u'yG#au4\\.WS[8.OCOGOiC4W-GOW4#CqCO'&i#&KmmSKiCe'[Cm-\\#-qWymGO\\uS'C4K\",\"q0uCe'&GK.#iGuSiK\\[m.\\yGW..i8.aGyq0e'04q.COiW-aGWCe\\.yWiCK&Sm8iSu8\\a['44qq'i.[\\0Weu[a''a&S#CCyWa4O#&\",\"8SOu\\ye48.[am#O#S8C'..8Weu-4#mS4[C4qy\\K44\\y#0#CqC#.yW#aK[C&iS8.GW-.#G.Wm\\-W&SK4K8Ku&-SmuGyi'q\\\\yC''G\",\"S&.Ka4qK0WS.CC.8#ae#O0yiaS'ei#i0qyi\\.m0K'G['4&.Ou'yq&y[m-...S#Cu&uq0KGim-#'a&i&qe#\\OW-.-8W&\\a&\\yqei4\",\"88u.i#'WG.uG-4&-i4mK4#0W'ya&'4qKq04eeOSSu4\\[SyK\\.S#qaC[8CyOC8imS4qa&8#-OGq\\me-aqCSym4a[-.&uyy\\4G8'W4\",\"WC.iy.SuC.W\\aq#K-mKeOm-&WmyeyaOau[#&4GuqaiKO4GKaauSaKa.'G8iC844u&yGu.Se-&G.4ae[eeuS-Gqi&-\\4q#qi\\i4'i\",\"O8-aOWGymC'We0.yeW4OuqGiOqae8iGe\\muGS&.0SmKi[\\\\.Kqum[S-8\\0['4\\y.'4-qCGO&u&G'4GaC'[i\\8&Wi#qmS0yy.mu4e\",\"#O'.&888[8'Smimy&u-CK#aaS80iCa4W[qeS-iO'#\\ma&qyyOy&\\WGmuW0u44#y\\CCeC-C#\\m#aqW[u&S#0&q-&mmii88Kq#0[Sa\",\"\\ym'WSyWuW-\\0C#e.W--m8e&-yyu-KO4[eGGu[aK-Cq40K\\.8-O\\WKC&mG#O8#CeSOu&8qaqm'a8im8m'i&'u0[8KymWe8u4#O&u\",\"G'emim-a\\[.i.#-[W-#\\#-G88Cq#8S[a&S&iqam.0W\\#&Cq&q.'q#4C\\-O-eKaaaOS[K&meu'\\.O8WG4m0iyey[[au0.y0K.O'Ce\",\"4''CO#y4u.0uCOa'KW8[y.mCS#WGW'aC.0iCm[.mqu#GO'SCSC[m-m'eG'[qiyq\\0memy[4\\4q.88'#yKaGyqqu0#[#4&#i\\a&'G\",\"aCqKGum.-miCq#a0Ci'u&.G.ye-#C\\Ca-CK'a\\aaOCCC.##K'Gu&C-KS'CyWqO-maem8yi0uCO[Cm4CeWG4''&meuie'\\'&i8i[e\",\"[\\aWWSOKGCe[Cm8WS#&iO8m-#a\\Wyy-m-\\G8mSWG8.8mi.#Ku-meGuuS[0me.S0m.K0u#[.0K-G8SGOC4.[Oum-O.O'-y[SqG\\W8\",\"qO8\\0O#''0qOWe8[SO&miK0qO'4#.u0.4CG0y.uSae8qm'-e&a8y#W[iWS'#8ie'eGm'0Sa.CSaum0KW0&iSSuGSKai0&CamS00G\",\"\\K4eu'[i-m.W\\Wa\\&u&a0m4\\&.WG\\eG.'u\\OSyuOW\\e#iW'&m-4\\y##q\\[yKm8a4\\\\mm[[8S[uGKe.8W\\&S4'SyqmC-W0#e&&G[0\",\"[K-mKW4\\[\\C8qe\\0y&WSm00ee8-#44e.imaKi[8WC[iuGCC#eK.[\\m'#8GK&OW[44'O4iaW.#4\\WeuG..W4mG#i#4\\0WG8-y#&iG\",\"SaSWaOuy.q0[4'qGO.-Smae84-#8m0KS4.OG&'S#0'#\\u'C&4.4iaOmG\\.CC'Gi.e-G.Wi&\\yCa'.[-W8[[S'mGu--WW.q0['Su'\",\"-y&-8SC\\uSSi-eKKCCKW.e4\\y[K&WaCKm#yyKmK4#ae&'W-eOyWy0#[G#y4Sm\\aq[Wuei0WeC0y'-Oa4'qi\\0m8W#Ce\\'qKC\\q4m\",\"Guei\\.88KaWi-0GuyW&Wm..yq\\4S[GK\\q#'e8#Omei0C880y##e'4\\#ue4mWOaq8O'C8eaGuG#'.Gi#0m4\\Wm'm8mCWaOa\\yyC-'\",\"K-\\\\yK-00KaeKGa44'G.C88[-u4Sm40W8e-'Oi&mGKmmSWOW0m-0[88aOaCW4S8Oi\\K\\.&0mi&&S0\\.i#GGu0qWeS0eKiWK\\myy\\\",\".['auSey0Sm.i.W#.y-ayK[e4#--iCOKCOSK'-#.imuSue.#q\\Sm'q#[4&qO-##aq[WS&yaq--mSyW[0GyuKm08y--ya[SKGOaqy\",\"0K&Ge'qqqO4'#C4i\\8KOOWWeue--K04eGGGy#&a.KO-C4'yW4&u-0.8qam-OKq&uyGqy8a8iW[KaS&WiSOyi0[eime-KSaGWC.8u\",\"CGaq#WS\\Weu'SOC&SqqmGuGWOe##a4&i.W-Sa4SCqSm-4W[S--OeKu'e'CymSa\\CmG&-0&[m#-aa-\\'KqeCi##y.#OuGai0#.yCu\",\"#q'8[SC..8.08WKSyKGe'u-[.4O&.i[uK[mqy-\\&S#-m4.KS0C\\0S#&WmC&m88[WW-CSq-0mi[G#[\\&C&i'\\WO[[.8-iqm&uW4\\0\",\"S.C44-S&W-C\\\\m.'uS#0-m[q#44aq8u.uG44ea[a-m[yieu&i[.#Ou8eyCm'-[m-a\\eu#&'#\\.#'Cy0-aCGaC[uG8'm8[C0y[W4\\\",\"#'[0.iOuSaqO'WiKyCauq8..WG-\\imWaq-Oeae040#&KKSaOeqqa4iC0iO8K0.yiSO.G4&-8yK0[8.-G8.[eeyq[#i8WyCC4qaK'\",\"qyuSm8Cy&G08W'-yi#ue.yiCOKW\\SuOSyWK8\\OCaS&&e4aeKOm'mS0mCe-[aWuG[8GKC.4q4qGKmyy.qWa\\mG[4qeKq[0KSyu4i\\\",\"eO'qOmCy\\0#KCa\\O-uK-ySSOG##-aaymyC&mi\\G.8e8#-.O88im'imWuKa&8yy\\Ka.48Ou44qKO'4C\\&S&'q4K-W4&e\\O'S.yyi\\\",\"qaauGiqqOGKaq[8-#8eWqOaCCOq0#m0[4G[W8aK\\i'Gq.m#iiGieWq.0W&Wum8iW&qOG[-O'8C&i[8G.q8'.q-C&8\\C48Ku-a4y#\",\"e#Ge'eGu4C'.KC0#qie8u40KO'OaWKqe.i8Ca..8aeKOuqeWmWu#OS0WeW[i848.&G'G000yeCS'\\iC&.[-ime0[&-K&S\\SyKSa&\",\"\\#8uy4KK.OWi&e--eKa4u-'SyC-8.yG'&qO..--[4&8W'uGia\\m8Waqmea\\uG\\K[Oamiiym'C4S0CS8Ku[eiy.Ka\\Ki..aq&.&4e\",\"#8SCqmy&W44qe&-O..[0y-0#e84a&u84a'C0[y[#\\y[SaaaqO'W[eiim4K\\maeaiCG8eiWiyOOGu-q#&G4C&aWCe.8\\#iuyK4ii&\",\"W8COGK0qi-0#qq\\.#ai\\KSGW'4m[m4m&aSy\\.O-eW'4\\0yaqq'\\m8WS0Wuq8uiu8y#iqWyS4W-\\0m'KOOuWy&'WO#aC0Sm0SC\\-G\",\"q#yumiG''Cy#0WC'W#O##aK[-Km\\aqK44q#&[m-eGa4WW.'iSWuCaC#.G[\\4qySqC0e8.'&.y[WWmSyy#y-C&'.\\KK&iaOC&yOq.\",\"0&G.S[.\\W4SK&m-&KW8-W.K4.0-8&.#0#[-8CuSm.Ka\\0[-Wi''.aaK[0S\\aKyum&q8i8#qW.8C\\yW48#aq\\a#a4C\\4C-ame#Giy\",\"i&ieuO&80GyWe88O'4\\y8[G-[yeae.K0\\&'eGu'u&Ge.[44Sa.#'4a4GGaO-yme0'u.0'Oe0[eKayu&8-GC.OS[#GOK\\0K\\mu&GS\",\"imSyG8qaq#0ya-C4Ci0[8a\\S.yK'u[8y'\\KCeGmeiae.4-ie.\\miy8auCO0uG4uu4'y0-&8yuKm8S#uCe.imG0#0\\#'i&\\mm&yi4\",\"K'S\\u'i'u8ymWWKuG.i.y'&qS#WuaKyu4&mu\\[i-0.q[m8'eGu-'u4C08yWG.#.We8ia'8O'-04&u'aeaO-\\0-u-y#4yWm-O#WG-\",\"-\\[.WG&qmSa.4S\\0G-#4-CCSOG4-q#CGW'\\G'iae\\G44#GK\\eWKO8uC#4CSyGm&.K0a[e..KKqWm&a.O&O&'##aKmuu4SG8yy.uW\",\"q'C.yO#\\mG.GC-\\#.KaCqO[S0m'e844&&ueWuK0mK4CW-O4CaieamyW-'qq4&WGuSW40WC\\y48#[y#aOu-G4O'8.uOSO.SqG.[OW\",\"Sy[4Sa'\\#Oe#K#0y&#e8##mqCeKGi\\4eWuG8m8y4WiSOi-8COi'OW#S0.WG80yCm8[GK'G[\\8&qW##q88#['SCS'-00\\WS\\.&8.y\",\"0qaa0-.Gq8iuOy[#qa[-O-iae\\muS\\Wum''&'4q0-u.O8uymS\\&K4u[aaWqymy8y[#e0mOumC4[..[\\mG-OWii.G\\O8'CCKOuW.-\",\"\\my[u[#e.ym8u.W'GW4'''''yOemi'i#&8G.ue8'0yC'u[[i8'.uOSi\\'#iKuWGGy#4ieqKqS0O.y.#O4KSWu.quOyi\\K[44\\[4-\",\"4yeWyuGe4Sa'au&eOWKm0ymii[-e88iSyyOiyK\\88mGau-eSK\\-u.#&#e-C4CO8CCq4Sy\\'eCS[#iOiGqmim-\\OWeCWu4q.a4ay#\",\"0O0ueCuO4Se0G[88[a&WmOSi#80&&84K0W\\CWm.W'#0\\.W\\yWy0.[G#0COu-q[S8&q\\OuSa44#aOaK4.[8WaC--S\\au84q\\0..0e\",\"iCeWmy.4iO\\We##OCym4.#8u&8.i\\#.[8SSy4a\\04e[0qeK\\&-#\\ymu4qO8S0mSWqaKyi'C4&ueiiGKq0Wq[#C\\i--eGSiC8iC-.\",\"&0a\\#GuWqmmyaeW[-OKqe0Wq['S'iSSmOCWGyWKOqeiGm0..&aO-KeC0ye0.q#0#\\SCCqWOi#&i0.yeqO0&&8G.y8.ym.iOue8'4\",\"\\ym#4C[ueaKSO4SS[&W&eWq#0aCO-0y.&0--#[8'#K&\\0Wy8-#i-CqOySa&qyO8KmWe0'.#m8u'44#S0K\\y.#OS-#Oa#8mSa4m''\",\"8i4OOi'.-&a448&&488uy'e[yy00WCWiC\\a.WmCKOGWK.yW4-0[4KaC4SKSeWG-m8#qO'eiqaueG[.'e8W-.[.K4q-.#4S8&yO#\\\",\"W#S#iueCS\\.8ui04eG00G8q#aG8.KOGW[Ky'y8CC\\mqi&[i0e8CmC4e.y400\\8im\\iCeuSa4uqOuKq[eG.C44WGiS8KO''Syiue8\",\"4SuSimq\\CKq[-0#yiO0a&CKW&[OiC\\qC&iaC['.#aOCqGW8-CK#GiGe-meWGW'q8[4yK0O[4C4#00O'&'#&.Om-#0K\\8WSSa&S\\C\",\"WS#y'C-mmeCyq'[Sa'..Syy.yWu8W-iOS4G-O-mu'qGu-0Kq0WS#ymaqy.\\8OuuiO'i.0y4u'y[S-0\\0#&8WiaaCeG[WCa[O.K'-\",\"8.8[GiSO.0m\\8C4.K&[aSGGK\\mi\\&\\qKqyK0GW\\'u-Wq8['&SGK-aa8KCS'4O0O.eKa4&q&i4400['SOG-KOeOOWCaK\\\\yWa#'eW\",\"iuSa0&uS[e#4[y#04\\-O#aqW0aOG&#-u[O-8uG8WWGW-m#SCu&u4&qKO&Sa\\48.uiCmaOa\\iK8qOG.yGGK#''Wy\\8[.CKa0WCKWe\",\"-yO.W-44S-00#O4SOa.Ge8G.GuC&#C[S-CC48y&'ami[qKu0aau#K0K\\eGKiOi.mye&OO[euuKKeWuua4yeuK0qG0WyK4&emueqO\",\"'0CaK4y4&-W4qyyS.0a8\\\\Seem88[.[KGO.y.&uq-ymm4qOS#0u-yeWKaaa\\#Cyae-KSKq0WC\\\\y#q'SmSu&W.W.OmGmuqOq0e&W\",\"8O[8'Gi[eS#OS'eq0[CK&C\\'GuG.[i#GW8WaOOuS-\\KymGaey&i&qmqSi#Cq0iOi[4qaau'&&u[iq#CC..K&8W48S#0K00iimCG-\",\"4\\qyqqGKqK0-4&W#mi[4qW&-['Cy8eGC&8'K..[G8y0i0G.aS.40yCuSmy.-WeW&WK.KmW8aa\\i40.i'e-#K&aqOOGm.a4[-ii\\K\",\"&\\u-\\mKWGmqmei00-#eOa&qyWu&8i4a#-Gqa-G.W44#aOeWK-8-\\K-S[-080m-CeKOGCOaGa8Su&-8W'Gu4[W[4\\K&O-0q8Wm#qy\",\"8.u-\\4&iquSSq#OS[4[.CO-\\\\\\.iy['0\\aOqC&WOW&.80'&GK.8[4-#qqWimm8W-a\\i\\yWGSa&iG#0\\mCmGaO-84e-a#CSKGS04C\",\"'Si#8u-#W4-a-e&eeOi'04eeK4SWG0euSi&'eOuG88'qaO'Ky[uW#WSa-#mS'SK[m'0#myO.i0\\y#iiS0C.yWS#Seu&G.8q4CKa4\",\"S#&8[i0OOu\\O80WOC8eCC.CKG\\Km'04q#Ce84yau'ymCW'qy#4G.G.[q&uy'u0.KeK04#C[-\\0KGe'0iG4\\ayy&Cey#O4ueq8W\\.\",\"WmmG\\u#O'&GW-uG4mKO&WSOS&ii\\-\\uSO0a-#y[S-y&'[i[-eSaaOiG[u8'eW#8G0W4uGu&G8iuWSi\\04i\\[44qOm.y\\C88[G0.8\",\"W-GqWi.m\\e-muWO-aqeuOOGeK[e.W'qa[8aO.e#C-&W\\We.444[-#\\&OuS.yK&.OW\\mWK&W#\\aKi4W-C4KW4WeuSu#aq'i4&eaq\\\",\"O8&'4.K\\iGu\\.[0W.qmu&y.mqa&Wu#[aCme..\\&ae0&.8uKOWe.K\\eGCOa[0C-CGG&e'i&KOKmq&G-Ca[e'S--eG8GK\\.4O-0G&'\",\"4#8[em00&8uq[G[88#OGGG0[#'iOKG[&-KiiOO[eyuG.[#4[ee-aS-.4eWCa&u-44q#'iua'8iO.CCW4WSOu\\mm-#OS-m-\\8WSe&\",\"O'CuW[GC'4i#\\iC#aCC.0KC'4W&u.[[&u'\\0WS8m40eWqOy&ymq\\mGau.#qaaC-8&G[#a..mKq4e4[aCyKO0q0#yK.8'q[.i084&\",\"m4OeOu.O-S''CuyGSeOWaGKOGqm&u'40Wq#aW-'4m'#4eK0OCyK\\eSm'&8-uWeCGa[0W&[uq-uOiyyO#umW--8u-Oa[S\\O0\\0mqe\",\"&yCq8KO0'4'\\Oi'OuG.KqmuK\\y-#-SyW'CCa&'WSy#\\.[.u-mW.WW#Ki0\\''q-[S'[8-#Ca4We.iOGy0\\4\\4C0WaCqqCu4mOq[GK\",\"0y0Cyi&ae-\\K0W0.K.u#C[auCuCWq\\&uS\\yC\\WSy'Gu-.8u-OW\\K0muWSm-O.[4S\\qiGme..qKSO-8.yWuimeWq[eW4q4WOW\\4ea\",\"&am.4me8K0.[C&G8Wa'.#-m-#&y8SO8eei0G-#'q#SWGC\\qeG8#We[aWme-CyOWS&WSOiaO-q.-GiK\\Cm8.'.e.GCS0O[y[G[CCG\",\"#eCOaim[ee[y0#WmW40q8mi-SyO#&'&\\.uGe&[[4qO8&aK[.q#yO\\#im&.iu[K.u-OS&u0G&8uGWKCqSKK4-[[GKu44e\\i8#qKy.\",\"&m-00KaeiOSm'&-uaOmqKaWWS0Caa4'[-##4aCiaem\\&WW4GK&iy##&\\K.'eiaeC&WS..O8WmO'C\\\\yKmOSiWeaG\\KqeSKa#4&#e\",\"uSm\\0i#Sm[uqC['.0m8'CmiSaqu&'immuS[C4SK0mmK0qyG&u-y&Wi-Oyi'Sm4qqq0m&-G\\4O\\KC.W-y[8S#O8yG[OOay[u.-\\e\\\",\"[0qmW4CC.W.[S'C4-C\\8i\\[\\80#yymu4eKG4G8[eC\\&S[u##G8u['Geuiy#O8m'#C'8yK-i-S#C.S[S0Wm#qKG0m-O4OS0004m..\",\"4Gai&'ei.8\\#mi\\4q.u&WG\\uK0\\WS#4G\\'[e#\\-C4Cu4CCWW\\4CWO\\.&uy8u-\\u.-aKO'qO'yy8&WuCe-GmCq&.8S#K0m&.4'4#[\",\"#&0['&G.Wu8yyK&CGmuy-iG[8m&S.Gie-&aamKC484e'uq'q4Ki\\m'[[04qW-#K8OSi[.4WC0W&0mC0C\\[e#8\\a\\#4'OO[4iW.#\\\",\"Cmy#qWK[Sq8GiC&iiG#i-88&yii8[O#u0#.SGi0[yeO\\eG[O'a8#euqS8OC[SO&#q8.mSO8WeiCS&W4[W4Sqy0q'4.48\\aeu00[u\"";
        dataString23 = data23.split(",");

        dataString2 = Arrays.copyOf(dataString23, 160);
        System.arraycopy(dataString23, 160, dataStringTmp, 0, 160);
        dataString3 = dataStringTmp;
        String data4 = "\"x\",\"x\",\"x\",\"x\"," +
                "\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\",\"x\"";

        dataString4 = data4.split(",");

        String data5 = "\"u-#\\[&uCGeO#aK&#u#q4uG0i\\yK0Ky\",\"u-#\\[&uCGeO#aK&#u#q4uG0i\\yK0Ky\",\"e-WaOaW&'uK-myK8KCOSGeG'&uSK-8\",\"&qKq#0\\aOmi0yey0my88GKe.C.0[\\m\",\"&8GCCOC#m88G0O-G4#\\OS0\\mCW[&C#\",\"[W4S\\0uCSa\\0m[8ii#OeK&C#4em8uq\"\n" +
                "\"4\\aOi\\KWSmu\\-O8WeG-0yuGeGeCSOq\",\"eSymKGKWeGGq0.&#&\\WCaOCqyGS[.W\",\"au#K\\.W\\#4&-a0&8-&0'yKSu\\0i\\y.\",\"\\[e-aK&KyW4G[y0--#q'C\\GO'CuaGq\",\"ye8y[.W&Om'[#Cqu0aC[8Wiu'KiO.G\",\"0m'q[\\y-G&Wy.#miyqCWW['4Gi&4O'\",\"SaCS'&#W0eKO#ay[uqKi[ui#0#Oqm#\",\"\\&-40uGu-Oq'8\\aOKu8\\0K\\KK.SiOK\",\"u4qG8W-.a&e8Kqm&Gu&\\\\uSSq0q8mq\",\"e.Gue'iG.ei[O&SO-SCe8uya-a[qK8\",\"G[\\\\0u'0yCyiq[y0K&4G.88\\i8WeK-\",\"e#qaO8#\\O0m-CK'iim[4q\\qK4SCmi4\",\"iaC&Caaa#a'C\\0mSW-.maueimWyWK#\",\"0iCCCSG0W8ui04OK0#&8C'8yeq'K0S\",\"'[-e-#G#mC4['e0-&-a[a4'COmuG\\0\",\"\\#4#qa4yGe'.Gi-a4C0.u.8'S-KOSS\",\"iKCOieSe#.#a\\888G\\0O8\\#yWW[#S8\",\"SCSq.ia\\.WW[8eKS\\eqOGKW#'40C&O\",\"e-O&.\\#&-eG\\m\\q.y\\eS-'qmWuO..W\",\"[[m-a0\\S'ieue.&WK.aaOueq&.S88C\",\"m&GOqmqemOuG00W8[u4G-&iOGK'&WW\",\"yOSyiW-qaCS00mSCC.&'-#qKie&[u4\",\"imS&e.K&uWCu[ue.&uiK'-0&GiWaaK\",\"'K4W'4#m'44-um#e8y.y[Cqy#-'yOO\",\"i\\\\WO#Oy#SGq#OS4e\\e.aOS#40a.#G\",\"['8i8GK0iuSmK&'.'euCe8#G0#a'\\y\",\"q-eK&8[CCW'\\04e#\\SC\\GeCOGC#Sae\",\"S\\GGS\\&WaWqGe&m#yGO8i[#uqOqGW8\",\"\\-\\i\\[C0me.G84COK'ee-O8e-0[GK'\",\"\\qy\\ae&'&.yi4C.4-K&e-eq&'aqym-\",\"\\aSS\\e-qeWai.'&.[G.O8qym-u4yu#\",\"&.[\\OaqC&-uG-KmSe.#8-a8'S8iiya\",\"&qOSW&W4K\\y0#aCq0Sy&'\\uuCW'88O\",\"G0#Gui0SGy[8WGC.C#&m'OWm'SyKWW\",\"KmimeW#KWuCqmmKG8iG0i0u#.yyK[-\",\"KiC\\'W.qGi&Gi&uCauCSeG0-KO'u#C\",\"8Keaai#yy&'uK\\['OGi'S#[#a8y.W&\",\"C4'40OGKu4O-\\0ye8K[uCWGOWS#CS[\",\"q-qC\\'GKG[0i8iGu''0.4&C0O-Oua4\",\"im0u4'-aqq.&#SeiqCuGK-\\'G[4uKm\",\"\\iWaS4S0\\eO-#uGqKq#Cqe&mSO8iKS\",\"#4\\y'G.4S.-Wy4[OGm-a4##-00#a8G\",\".equ#8K8\\'S[O'mWyy#CqG&We'4&[S\",\"8im&im--WuG-\\W4OWK\\uiaG.ymemuW\",\".-aa8Ka&GOW-\\00q0G8\\#a8'C.i#W\\\",\"u[''Cm8m8u&0WO'.m-##&qyWmqy[Oa\",\"u4eCmiGOuqu&G4ae-'#0yKWm40eWa&\",\"ua\\08.[4em'-#meim'G4GW\\'&-C-C&\",\"-C4&mm'8K4&m\\SaqW0ymi4\\ai0i#[4\",\"Wq#K8O&\\#\\'Kq0-\\0ya\\.ey\\Ku-a[q\",\"#[uC\\\\4i0u4Sei8iG-.4C.K8W'q-#O\",\"0y&'G\\m0##[i&.m8[-0uiWC.O4\\[OW\",\"e[-00#\\-OC8G\\GWGO&iW'4C.COGKKu\",\".#4C8aC.84WWe888K&qiua'eWee.i0\",\"y-\\u8'e008m.Ce4.W'qGiCCO.q#000\",\"Ouu[Ga#8iOOu44aOiyqOGS.#0am-#G\",\".Wu'y44&i\\08'q4S04Cm84&aO['OWa\",\"--#euS\\#aG&0e.We4..q4-#GC-44q[\",\"'S[\\#\\S[Oua-eWS#i#i&4&-CeOO#\\'\",\"m&G8y.&&.'yWO\\Cim\\GC#4u[mm.Wi#\",\".a\\KKC\\W\\e'S4S[eGuii&8[.iKiWS#\",\"mSmK'K488'8\\iu44&[q-G-mKaqquC4\",\"uC\\mO\\e\\-K#qSa-m8qG'WeGqi..i#q\",\"[&G.['[\\qa-.8aC4i0#\\.me0.e-Ca&\",\"'uS4''CGS[OW4C4&GGGi#CK4a&yyWa\",\"[SmmeG#-\\a4CCG4#y.WG4\\.0yiOOu8\",\"S.[-mGq#q[[\\[8S[.Cq0GSC4\\-'.&'\",\"S\\yKCO4&-CCWWG[Oe[&#ue.O\\[4OOe\",\"C&iG.u-yW08\\q8e&Ca.-W'qmGG#y[q\",\"SiuGuaOS['.y#eu4\\#C'eW-8.K&.&K\",\"i&&yOO\\m#[u-008Ki8yuK\\'OWi#Cq8\",\"Wq[\\'OKq[&\\.yO0.0u\\[qO[&OGK-'&\",\"&#OWyWui4mGa-a\\mOWS0ae'&mKC&[e\",\"-aO.4u.yWqaye0[44mS&&#iOCC#m.-\",\"ya'&\\K0WG[m#O'W[W4\\#e8.\\[OS#q#\",\"aayGmSuqO4OW&S'CiW\\GC[qmOyS'CS\",\"Ci.y0K.uG8eKaCOq-#\\4qmaW\\KmWGe\",\"44m['aeGeie[SOi--y'[\\#e'\\i8u0O\",\"Ka['iOa8u[SO4m[eOu#&We-CqSq0uS\",\"C'S[S-&'Sy4G[4-8Wa&.W#4S#Cai#u\",\"#q0a4#\\#KKyCauC4&G'Ca&ue800W8u\",\"\\8[0muq[qyame.i&mKaaq\\WS0G8u'm\",\"q&0#CS&Wm[C8#ieG[SyiaCC#quSWyi\",\"8iK4ee40.q#CCS&ui\\SSGG\\eiy8Wu4\",\"G\\W0-&'.8.#yKaSu-auG.\\..m&-0CW\",\"i#&WmaW[e[m8[ueaa\\8K#[0#OGSm-'\",\"aG8&muSC##&Ou4m-000Sy.C4C[Kq80\",\"[GW'euW'e[G00GG\\O\\W\\[m8ymuqyiO\",\"8&-a.&Ky4aOi\\eGWGOG[\\mC''0a4\\K\",\"SC4m0a##qqKqaSaeWS4#a[i&GWiay-\",\"K\\ySq..u&\\.-u.a-Wu4q80imiO[q.&\",\"W[88yOiKqaC\\0OG[-a4emCm[[Sq[\\m\",\"S[K&aeC&8&ia\\.uWCiKW#&ia#qa4qm\",\"#OimeWS0-S'q\\[WCS&8W#qa4yC'mmi\",\"O#GK#aaqe-yOe.&'G8y0Wu0[''e8Wi\",\"a8imSS'.-CCOeS#qCCq0ymSu[.y.[\\\",\"euSSC'e\\KyGK8y-08y-a\\[q-O&yKmm\",\"qyW4yaC0KG'&-\\\\8aeue[0Gay-ayi[\",\"u[G.iaOSa&.yK8.e'a[Sa#ea\\0ayKi\",\"W-4-CeqqG'a#qG8#WW4q'K\\Sim'qa#\",\"G4WyO00[G.&''O&OC&uieK&-4m88W4\",\"mKGuuC&eGi8u0eC\\y[m4'C-mGm[Sqq\",\"'GOqaqmWC\\.-qOOGCi\\y&[S'q\\aGi8\",\"[#yi&q00muGOu[mumei8iOeyi'&GS&\",\"#\\W-e800#OS&aGuK[0yK[8aKau'#aW\",\"KO4yKqOS[u&-\\qaC&O'm&4.y..KS#8\",\"ym.8.m4iOq#CiuG8uOSy--iGiO8SW'\",\"Wmy[\\mi#-CCOOKm&iqOuG.\\O'Ka0.-\",\"GiiG-8WeGSKW&80Ka&#4[.i8#q['&&\",\"&i'eK'[S'8GeOiu'&GWS.8\\S0mGCmm\",\"[m\\[q4euC44SyO8\\0SC&[qG-OSG-0#\",\"uq.0iGuq'Sy&OSm#O\\W[im'04iamCS\",\"&q#8y.O.Ku4\\CaaK.KCCuaC&S-y'eS\",\"&0i\\.q\\\\a0'4y#80G\\a\\uqKOe-4KuG\",\"eGWSyOGue.4S-O&qK&.0[iymmuq#a'\",\"OCO00\\aeW--CC[8WKmmuGS[Oi\\[yq-\",\"[G'0\\..qWu&#0m88a&O'&4GO8um.8#\",\"m#O8iW4&W&[ei4'u-e'yy\\im.04[iK\",\"u4444C88a.\\0#qq8u-y[0.e.#.G#eK\",\"W0C\\[G.G-mSa'C\\\\KCeWSi\\\\'.y4iO\",\"y#8mCCeGO--yymiaS\\yq#Oie&'aW8&\",\"-\\q[8['&OG['4&a4\\Gq0#4aeG&-u&q\",\"&&im&Gu''&8\\C4eKG4a8W\\4qK&qaOu\",\"Ka4'COSKaO'4#K\\-[-[u4CemSO#Kia\",\"i#&&u[iW\\Cq[mW[O\\WK-4#m8uiW'Gi\",\"CWuee4qy4eG.\\0i#8aWy[i0-'G-eW[\",\"qmiC#CC4Cq&aSa'mi#O4S[#88\\\\\\.W\",\"y'[i#8Oqm#.4eSeum-aeCS'SKGi0CK\",\"&'C\\.[eOWGi4\\[qaey[a4Ou''eOmiC\",\"C.#\\.4&&-O'uG8.SOi#4eW\\G.8CG&K\",\"&8i4q['Gu'S.WqeW888SyqmSui8[&u\",\"a#e00CC'C8GG.G.OSG[Kq[C'a&Wu#[\",\"0a'4amW'0iC.Oq4Oa'\\#aeaa#8\\m[e\",\"C\\OyWO-m\\GSuqyKm44qaCeK\\aC0O[4\",\"[C-\\-#4#0yGm\\8\\8q\\#8&##&SaW&80\",\"KCy['88KqO.S&a'W[e'S0'mu4C.mim\",\"\\K[KS#aKaiy[Oe-a\\.#a\\4&[W.q0['\",\"0'#C#&SaGS0Ge.iai-..m-aaem#OGi\",\"#mG#Oq[&yWu-'S\\KGO''\\m[Ga.44'i\",\"q0&.'&.CCemCS0#0q&C-88.OK4CGGK\",\"&u.-m4uO#GmSSOu&eWSya4\\ae'#OiO\",\"iiGWuS8q'8a88&'Sa.0[COSiyWKOuu\",\"ymyO88y\\0W0.i#44#eCK'Wi-Ga&'K\\\",\"8G'[mG44&SOS0a88eiq0m0[\\ye.#WG\",\"-ee8e&uC4-##0m4CWKC00W'Ka#GuuO\",\".#G-y'#\\&'#umay.y#[O&C&GW-aq-0\",\"-#q'\\qaC\\i8&yO''O&iC\\aWq.&WaqK\",\"KO&088&.-\\&qa8#40G-ay.8&G[SmeW\",\"'S'e4qK#\\'44y4\\i0KOu-C4&mG8qCa\",\"K0K4q'y''&['qimqe-au.qeSm8WS0m\",\"O'&S4q.-.m0Waqy'4SWW\\C4Oi#Ou'q\",\"Sau&0#04-KCqq[80[8&yueG[C8'q[8\",\"[-4#Ga&K-.8eKOu0u&8-iWKqe8iaSu\",\"GqqOWye[GiOe.CiyKSiCGy\\-C8...O\",\".0\\&uei#CSO#G8iem#0mG[q#'4Cm[.\"";
        dataString5 = data5.split(",");
        String data6 = "\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\"," +
                "\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\",\"RIGHT\"";
        dataString6 = data6.split(",");
        String data7 = "\"OuCS\\['4qGue#&i\",\"OuCS\\['4qGue#&i\",\"yum4eWu4&Wq0Kq&\",\"K4Ou&imCya\\'..4\",\"Ki.'G.aa..O#[-m\",\"eWuW'i'4CC8GKqa\",\"[C4S#K8KCOaue-a\",\"#'C&a\\8KO8m.[\\i\",\"88CaSSu&WSi0#Si\"\n" +
                "\"Wy8[4Sa#i[.#Wq'\",\"'Sy\\Oeueya#q.uW\",\"-0W-\\..iqeCeu4S\",\"mOGi.y.[KG&-K'4\",\"WqKu4&eW4&.yG-G\",\"Oe-.#4Sy[eKOiiS\",\"yK.yiOG8-'\\qm8[\",\"ayS'4&4\\i4eOO&4\",\"ym-4eqKqy\\'0aCy\",\"CO'GGW#Ci\\#im'S\",\"uW'''a4\\G.aOyW-\",\"0yK8[u4a.4K44.'\",\"WmuC-i\\0qG#C4a0\",\"&['S#0&GW[eG00a\",\"#&i-04#&GKC4GK&\",\"yue-0KKe4'mu[-W\",\"SyqKeim-&.K0&\\W\",\"-&.eu8OG.&a#[S\\\",\"SOi\\#4eS[4y'ye[\",\"CmiGiSiee[i0imK\",\"0[u0.mKa&'Km'ee\",\"CCSmeu#0W-m..G[\",\"SGy##\\i\\'W[Si8y\",\"[u8O-&W4m[#8y.K\",\"mC..WKa[[[.#&8S\",\"WuS#'GaOW'&#GW8\",\"W4\\'40O-0yeOCuC\",\"WOiiS0eS\\[-O8yi\",\"KKOSeiOq0iamSCS\",\"K4ei0[u'uqeGKGi\",\"aaC.[4[-qm4a-y8\",\"40#&8e.#GWi.[e&\",\"eW'SWeuSq.W#G80\",\"\\[8444K0ie#0i-4\",\".eWuq.4#4'um0'4\",\"[u-'SO4#qmGOGa#\",\"C0q[8OSCaS&i.KK\",\"WWm4imq\\#G-0Km#\",\"G4S\\.\\u'Cy0\\.Wq\",\"4S-[Cq8.#-[q#[S\",\"\\W&-#ueiGW#00O.\",\"4u44#'#eq&\\00S'\",\"\\yWWG8'aeWW'u-y\",\"Oe8'q44&4S0ieWG\",\"\\O'qySiS\\OymKe[\",\"a&uG&uqWuSWS0[-\",\"&4Cay.a&Wq#&\\S0\",\"Gy['SK\\KKi.[&aa\",\"-#GW..KOWaqOO\\S\",\"yy[4qumiyqO#0yC\",\"4mu'yi0[S'C\\C0a\",\"'uS[#aC\\KW'C0..\",\"88[O.W.W'&-0\\WG\",\"4.yW\\#\\e'&Ga4i.\",\"OuC8[a0a[8W'CC\\\",\"m'O'&KqmyW-m&O[\",\"-&.yCSae-u4\\m-8\",\"qOSaOK0[#Gy[CKq\",\"G'&aWS\\.S-Ou-m.\",\"\\&'&eKCq4#eW&'m\",\"SqKaOOWqa\\0C'Gi\",\"m8i8WO08Cm-C\\8.\",\"e'&&i\\.i#\\.80y.\",\"mK\\Kq\\Oaa#48W0[\",\"#C\\a'.Ce4qi\\u#q\",\".q#Ka&SmG\\Ou\\0K\",\"mW[.yW#-u-Ce.K0\",\"CqG\\e.0OWmam[SO\",\"q4OS..iS8eu&WG'\",\"KmSmCeSKOOmGS'8\",\"aOe0u&8mGS-4CSq\",\"SOWGSSyKqm'em[\\\",\"8O\\.qa'Sy'-COKy\",\".W0\\q'8Wq.K&#u-\",\"[e&\\.iC8\\.Kq&-O\",\"eOOW#.yWem[-Ceq\",\"WGiOi\\a-C0KmiWm\",\"G4q48umau#e8ym8\",\"WiO4&C\\mam-S&\\O\",\"Kq4&'aO[.miaKaK\",\"CWa8i0CO-Se8aKi\",\"aCmqKyq[S[S4i'[\",\"&mGm&W-m\\m'aW0K\",\"u-yG&&&4WaWu'W'\",\"S-0Wi&u-\\mmGyy#\",\"CqK40yi0OCuO0.m\",\"#&u&qWKaaSq.yK-\",\"eC\\iue8i0&&mS\\G\",\"qyyy\\W-#aCi8[-#\",\"#yaGe00eSeO-m\\0\",\"G\\#&8eq\\4-#W0&\\\",\"imKaCiySO8eu&\\i\",\"8i#&iaqy4q8u&[e\",\"y8ii'aCqu0m&Gqm\",\"[ymu\\iaOu8yie-u\",\"\\y.0#WmG[4&ei0C\",\"quu[G0OSqu-e\\#a\",\"aem\\eiymqyi#4Gi\",\"G[.[yKO'qKO0y.8\",\"0-eu4#K[mq&m\\G-\",\"Sm\\m-yy\\.Wm-'&S\",\"'CmKiqyOi&yiyy0\",\"e\\u\\a#m-8WWmmi-\",\"'00#08\\KySe8-0#\",\"q0\\O'uGa448-#CS\",\"0W#.K&uGq-q\\a0'\",\"KW.iaOW-&.mKma0\",\"S0SO-#CWmSK[W-a\",\"O40q#O'S&-ei#KS\",\"K4C#\\S#[uWKua'G\",\"Ka#S0ymq[\\#..4a\",\"y-mi\\Wei\\yK[SGi\",\"iCe4q.#GGimia&a\",\"e-Wq'euSGWWSyea\",\"-mey#4\\eG4q[G.y\",\"Oe88u['.[[uGKiW\",\"qa'S\\O88qOyGquu\",\"Smy&'[C8yime.S\\\",\"OC-O#'qey8q#C8-\",\"Kq#&-\\'-C4Oua.[\",\"4OuWG.q#[Cy.KCu\",\"&mGG['G[#qWqKOq\",\"..[8i#\\0O'mC0SO\",\"K0#'&[a'meWGq'y\",\"'GO#Cqme.a&.&KK\",\"K''S0'C&Gu\\Km&W\",\".KCS0#a44&#a40C\",\".i#8uGq8euy&-i[\",\"8m8qqK0-m.m#8#C\",\"-OWue8uuK.iCmi.\",\"WCe\\0.eO#8y0Oq&\",\"S&[S[-\\0K0mS00u\",\"4&\\OW&G#aC&[O'y\",\"W[OaWuyy[K4qm.0\",\"uG&'Cy&Cq-G#&C\\\",\"G0.CS#WeiKam4Ku\",\"KaG0W[u4m''8&Se\",\"K80Kqm'&Wu'[q-0\",\"&WKmya'S&W-aGi#\",\"S0\\S#&W&.[GC-4.\",\"C-WOq\\CS0-u[['a\",\"OS8y8['4O8Seye.\",\"4m.KCOKqmuS0['4\",\"Om-WS##q#KOiW-#\",\"4\\Gqyqay4&W.#0S\",\"0'W88#ia#u...Wu\",\"4aau-OWuC44y\\aS\",\"i'Giu#8i-OG\\yi4\",\"#O[Gqu['4#iWO&a\",\"SuuC.Wa#4[O[iG0\",\"a4uSm-CmqKuK8uO\",\"qaSG[mGamGuee0O\"";
        dataString7 = data7.split(",");
        String data8 = "\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\"," +
                "\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\",\"SUCCESS\"";
        dataString8 = data8.split(",");
        data = new ArrayList<>();
        data.add(dataString0);
        data.add(dataString1);
        data.add(dataString2);
        data.add(dataString3);
        data.add(dataString4);
        data.add(dataString5);
        data.add(dataString6);
        data.add(dataString7);
        data.add(dataString8);
    }

    //    public static void main(String[] args) {
//        System.out.println(dataString1.length);
//        String string = "124124124";
//        ByteBuffer allocate = ByteBuffer.allocate(string.length());
//        allocate.put(string.getBytes());
//        byte[] compress = compress(allocate.array());
//        byte[] bytes = deCompress(compress, string.length());
//        System.out.println(new String(bytes));
//    }
    public static void saveDirectory(File file) throws IOException {
        RandomAccessFile fileChanel = new RandomAccessFile(file, "rw");
        fileChanel.write(dictionary);
        fileChanel.close();
    }

    public static void loadDirectoryFromFile(File file) throws IOException {
        RandomAccessFile fileChanel = new RandomAccessFile(file, "rw");
        dictionary = new byte[(int) file.length()];
        int read = fileChanel.read(dictionary);
        zstdDictDecompress = new ZstdDictDecompress(dictionary);
        fileChanel.close();
    }

    private static byte[] combine(List<ByteBuffer> buffers) {
        int totalLength = buffers.stream().mapToInt(ByteBuffer::remaining).sum();
        ByteBuffer combined = ByteBuffer.allocate(totalLength);
        for (ByteBuffer buffer : buffers) {
            combined.put(buffer.array());
        }
        return combined.array();
    }

    public static void compressSingle(int index, List<ByteBuffer> stringList) {

    }

    public static void main(String[] args) throws IOException {
        List<ByteBuffer> stringList = new ArrayList<>();
        String[] array = new String[]{"6", "/", "c", "k", "E", "E", "6", "U", "Q", "=", "]", "]", "s", "I", "/", "M", "]", "E", "(", "Q", "Q", "Q", "Y", "(", "(", "%", "s", "s", "g", "]", "o", "(", "(", "E", "Q", "!", "2", "=", "=", "s", "w", "%", "]", "I", "6", "U", ",", "%", "/", "6", "I", "c", "g", "o", "]", "M", "Q", "E", "A", "I", "Y", "]", "!", "6", "A", "!", "s", "c", "%", "g", ";", "(", "U", "]", "Y", "%", "E", "k", "]", "!", "]", "!", "(", "Q", "%", "k", "!", ";", ";", "M", "Y", "Y", "]", "]", "Y", "c", ",", "k", "6", "U", "Y", ",", "c", "!", "6", "I", "]", "6", "Y", "E", "s", "E", "2", "2", "U", "M", "A", "Q", "%", "]", ";", "U", "Q", "=", "o", "Q", "%", "c", "!", ",", "E", "I", "U", "M", "6", "(", "s", "A", "(", "Y", "I", ";", "(", "Q", "U", "E", "Q", "k", "6", "2", "6", "w", "/", "U", "%", "E", "Q", ";", "Q", "k", "2", "(", "w", "U", "=", "=", "(", "s", "U", "=", "g", "]", "A", "U", "Q", ";", "w", ",", "o", "g", "o", "o", "I", "s", "k", "2", "A", "g", "/", "s", "U", "k", "!", "Q", "/", "c", "%", "%", "6", "c", "]", "]", "(", "U", ",", ",", "/", "E", "=", ",", ";", "E", "M", "(", "Q", "M", "g", "!", "M", "%", "!", "k", "U", ";", "E", "s", "U", "!", "2", "I", "-1", "1", "0", "0", "0", "1", "1", "-1", "0", "0", "0", "1", "1", "1", "-1", "-1", "-1", "0", "0", "-1", "-1", "1", "0", "-1", "-1", "0", "1", "-1", "-1", "0", "-1", "1", "-1", "-1", "1", "0", "-1", "-1", "1", "1", "0", "-1", "0", "-1", "0", "1", "0", "1", "0", "1", "0", "0", "-1", "1", "0", "0", "0", "1", "1", "1", "1", "0", "0", "0", "0", "0", "1", "-1", "1", "0", "0", "1", "0", "0", "-1", "-1", "-1", "-1", "0", "-1", "1", "1", "0", "-1", "-1", "1", "-1", "-1", "0", "1", "1", "-1", "1", "-1", "1", "0", "1", "1", "0", "0", "-1", "0", "-1", "0", "-1", "0", "-1", "1", "-1", "-1", "0", "1", "0", "0", "1", "1", "1", "-1", "1", "-1", "-1", "-1", "-1", "0", "-1", "0", "0", "1", "1", "-1", "1", "-1", "0", "-1", "-1", "1", "1", "-1", "1", "0", "-1", "0", "0", "0", "0", "0", "1", "1", "0", "1", "-1", "0", "0", "1", "-1", "1", "-1", "-1", "1", "1", "-1", "0", "1", "0", "1", "1", "0", "0", "-1", "-1", "-1", "0", "0", "0", "-1", "0", "-1", "0", "0", "-1", "0", "1", "-1", "0", "-1", "-1", "0", "1", "0", "0", "1", "1", "0", "-1", "-1", "0", "-1", "0", "0", "1", "0", "0", "1", "0", "0", "0", "-1", "1", "0", "0", "-1", "-1", "-1", "1", "-1", "-1", "0", "0", "0", "0", "0", "-1", "1", "-1", "-1", "-1", "0", "-1", "1", "-1", "IU!=sk!cscQE,;AY,],!ks;E6]2%gE]!g(E(]", "%;E]%(6A=!%k%!cwcA!g!(Q2;/%M/]Q;,;,MAU", "(s=2s;Y6!cI/6!MA;!(Ywc(gAsAs;M", "]M!kYIE%%E/s(Y%YQ;c,MM6%6sI]gg(;U;%2Y", "ggQQ(YMM]!;%U6EA%,Ag;M/g=,E26/6]I(oIMMM", "gkc6;I6(owc6;(,%Y26MgkUMk62;AEg(cAsYA;UI", "IUskQMwE,/=kM=gU6k2cM](I]2,M/I]skYU!", "w/o]26As6Q(sog(Q;g2(Y]gIU!M]kA!wwoA", "sgEUI,%kwUoQAUIQU2Iso2==;g;2(6,Q(", "YI;/6YgsIMocsE%M=YAsM(s!,2Us=!EYAk", "c!sQQcY=2,=ks%o]%AQ=ockw%s;csMA(Q", "2sU2YQY%;I=,sQ!UkAU]kQ;;UQ]M/k", ",MIg!Y%]=IE%/QMkYMAUIo=YogIUYA;w;M]=", "kMAwE,%I2wA=/Q6M];UI,QEEU/,Yg%Yc/MswY", "%6(ooo(/g2/oA(UYo/IQ]%cYgg2(go", "o(I;2U6QkUk/s=M,A(w]s6Y]2/!=g],U", "2g/6w!gA(AYwkc6IssIAsoss=YIgMU!(,(cQk", "gY=!k!M;(2g,w2=/%2I=;=o=2IwYookg(g6AY(6", ",]M(2wo%oEQ;oI]Mw=!QckwU,6wUIgs2Y]cU", "sg2I,AkEk!Ec%;U%2s%!gQ;,EoEMw,]gEg/Q=/", "sM/Qc=UwMg(k,A;cM%//sIU]Q2EA62", "scEY2!,]osIMkckkgEEoQsAskQU!=Y;QogsQ2kkU", "%Y!kE(wIAosIQUYs!s=YQ=E%M,oE]=/(2,(M", "U,(6IIgs,c=Uo2sk,,]gQ/62]kIg6%AU2]A,c!,", "UM6,sIU/QsEE]Y!6g=gQEMM!wYEskMU/", ";gkM!(IsAA=gs=c,%Yo;wQ2(]k!AcY2,6", ",I2ooQAY26;,Y=MM2U!II%;w!E!U,kc,o2s=", ",;;]w;AQ;g;(YYUgY,%MEo!QQ26;%=,", "==wc%YoEE]MkM(EMM((;ckkw(gA;!!(;gM!(", "226/g]E=I2w666(],wY;62(w2c]!I66Q6/", ";QcI(;Ykwo;Ukk!%!k6/,=o!]Q(Is=Uwo;=gMw=", "UMkYA6M]6/%!(E,s%Aw;I6E]!s26o/,kMck", "U=/Uw6o!,/A6Ic]MAwA=I%/scQQQoQQEU,%", "g]AAw=Ig](M,M/g;o;,kwA(,I,Is,MM;oo", "s(]AgoQEY2k%A6%Ucsco6gAsUU;6A,QE%/26", "M%,2Q(;YoUMYkM=,;2E!EA,;(U!s=2", "EAEU!2c]%!I]AIcg%/;k6Y]E2o2wMss];MoE", "/sIIs;Ms6oM;kwQo]o;o2,2w6=kUIQY,o", "/Us;UkkwY/UU=6EI,U=A/Ewow(ggYEI%YQIY", ",]A=!,A]E2,woEU!/]6%AQI/wg%!/sI6c/k]%A/U", "//g]M((/(EkkMgswQMw!,/YQMY=Yc,]gY2o;Q", ";,/YA(2,M62;=Ys2g!oUUwc],(((Qk%YY2U!6", "2(QsIIUQkck(,,!UIY]/EA=,k=/;M(sws%wM,/%", "ko;U,M6!goc=/Y!,Y,o%Q]MMw%YQ,M]E]2Ek!", "Ik=%2w2MIs6UQE,6Y2;6]Y]I%I](cMQ2;66/Y2cI", "!6Y,Mk=Ig,;AI;Is2UUUk;g6M=ksI26%", "k/YIAk/EQUY;IUw]Q(s2=/6!sQQo==!I6M/", "QE(g;I//;,%%%/UYo=!(!%Y%%=kUYQ;=,k=", "%EUUE]2;o(,cgMMkg;]/wEQs,c%w(;g", "=ksMMY(MY2w!(s;22sI%IEo!oskw6sgIs%kUsg%=", "k,IQM,]2Usc!/ssUA2,2=((g(o%!sw/E!", "(wwsg/Qs(!%(6sgg]oIc]g(Qo(E6MkY2=oA", "=Qo26%]U,wcggEoEgc/(6/;sQQA(QE(s", "E%w/=kM]!E%k62%A=A,,Y%]IQgs,//w2,AIckA", "2wk!k(Qs6;!,I6!(w26okYksI;Uc/(o6ks=IU", "oI6/k,A(QYoAUk/(wA%cE6QgYc=Ug=6Y2g", "6/I!QEo=E2(Q26/Uos!A2,]E2w2;=k!oMc]", "go,E/c2A2I!2%22,Q!wsA=wA6E%]sU62", "c2];/kI;=2]=c%gsk!U/w,AU!Ek;I!", "k/%sYoUY(o(AgEg6AgI%IUE/==wQQ(", "Ao]cMk%]gEoc=6kwAoIU/6YA6As!gso,=I(", "2=Iowccg(6;sgIcQcc/s(wck=QI6gYsI]],/", "MoUUYQ2Uw2];gsYgkY;Qc===YoA;=YQ/s!/", "IU2]oAs!c6%6M;%IoEoQEUU6Y;IUw]Q;wsA6sIg", "cUQgcssg,M!cE!%E%!Y]Q%6EA%//E6]", "M]kk!,;6AYI=M2YM6%Y];gokYUAIsUwMY", ",],,oM!2!sYwg]w]26%/QYc,A6Eg6YEEskkM", "(Y,(EM]k/]c/Yk!g=62YI((sIA(YgQw2I2!gEAIE", ";UEM6E%],=cMMYwQs6I,YE6]IUU,o(%Y26/6,IU", "=I%U=I22wQc!A=;wIUEw;IA=,]=w=/gE", "6MUc2,(cc6M;/YgkEcA(EQ2/6M((o,MQ;,wo", "U;Q(%o=;%YI/AI(6o/AUQQ;YAU!6]MwUQYQ", "!(Qog!,2%;wcwcw=,%,o(IMU=/Q]!2", "2gc6E]%w]=w22]oE=w]ocY=IM,gc%ssg(", "AY]gcA!ggow!(;%6MMYQokk/gw6sg/oo", ";Uw,wI6wAgQc]!k!Qs=,cw]MMAk(;%Yk/", "gAg;k/]g(6QIwIk=;IY]o/62o2;]/EYUkI(c]", "]oY/o((2]kkk;s,/sE%!g]Q/6%Y%/s6sc;;", "2IU,]U]UcA6w,/,%,;wY(U/g/;]k/(U=k//", "M6cUo((o2%/ks//%/;EU6%Yo,cM/,M=", "c]I;(oU=w6w]YMwcQ=!,A%AI2!c2;IY2kk", "M2(MYM/E26;cc!Y!;Q=UEgIQA(cYYA", "Ukk!IwQsgE=//sw%s]!oQ(Mg6o2kYAA%Yo", "sE]AA%]=c!kkYA;ckw%=UYUUQkwcMo,(EI", "QsU%]2,QE,AoE;!;=]EkYYcYoc(/6U!U,kAYA", "]Qo(E(kY((;(,c/o(Q!U,]YEoQwE6%k!", "M]//c%U]U6%YA=6%gssUU=(sg=,MsMMkoo2YQ;w", "g6A!U%Y]oEYMMAU=(EQs,IAwU,IEM%I", "6ko/E%/6!ggwkw/EQs!2=IgE;IEQQMo,;;Uc]", "o=]YY];6Ao]cA(;UM2cAUQsg6I(%AU;2;6oE%%gs", "A=%kY/wAsQ6EYoEQoQ%U!gQEQo%kIsU", "AE2=6MwsU;6o/EMkkQ(/6UwYwM!,o;MooYgk!", "2wI/k6kUI!oUMkwQ/%wQEQ!EYQEUkEwEEMY2;wU/", "2wUkI6QQU=,cko(UA,o;,(A==YQ(2IsA=w", "%wc;!M/%Y,],!!!A=YE/IsUMQ(M%]kY,%A(]gwEM", "(2g=,%/]o;c/gY!6%AY%/;(s!6M(26A%6=k/", "!;cMc!gs;!M!(6k2wQoQQ!wIUY262w=]2w(Ig6", "]=Qo%gow2ooQM;kIY2go2o(c!!2I(M6E6!I", "I];6AsI]UAw(!!/kM];=%cY=Mc2w%A=MwA6k/w6", "wQ(EAs;IUIQYgQs6/!=osA,!,2U=I6", "A6s,sQ;]AMU/,E!;,g((;ksUIk!g(;A;A(,Ak(", "!MQ;gU!2AUw%%kkMQ(EMw6UUU=QEUko;2/UU=c", "(Mkw]Y2c,Aw]%ocYMo%AUQ6!]Aswo2U!s", "MAgo6AI(c/wg%;gEwA,YU(Q;!,UQE(Q(YIM!!6", "I(gIEAs26kg//6swc]I!,IQsc]QE!w;sY/k!k!M!", "kEo(=gwI6AEs!/(M/E%6/Y(,Io]Qw2E", "c/YQM/Q(;]Ao2k/Q2Es/w=k;]Qo]k!okYo;", "I;]E2Usk2gEkA(MIY,Q/UY](=%,2IgsIgIE;2%", "AYwg%wgc(EY%%%Y=AwcYYQE=/!Ug;!", "g]%oEc/Y]=sQI/=/26sIQE2scI2IQYswQ,!,kkw", "I(Uk!Yc%M6QE(6!A]UI/ggs](6sskk!kwoQ!", "g;/s=Y]E(w!osI]Is,Ao,/%Y]A%U6!=", "E;ogwcQw2gEQ/IsI!Q;Ig2gQggEw]!/IkgM==I", "(Qk=MQQA;,!ks]2w]c]!s(,c]YosI;;", "w!2(22k!/k=kc%MM%gskEk!(s2s!=wgs%oMA(o;", "ogA]AM]cwQ!oIU=Yoo/MA,c]2!;A]=", "cMw2kAA!w2c=gsIo(2sUEMsIA,QE%2;/,", "s;g2sI2Ugg!]ksc%Ac%k=YYQ%w,/cEQsk", "Q],Ag2g]Qss!]wYsoEM;===Agg!gckIw", "2/MIs2;Q=w]U%/%Ag(Egw%EMo%s,Y!UAYY,M", "66Asw%k;w/(%]M2/ks;2Q/wgoook/%/Q]AQ", "!sk,,QosUsQogI/,]/U/(((6=IsYk(", "s6QU,/,=%MsIIUQs,/((%EwAA,22A,", "Y66AEYQskY(YEk!k!g2cwY%%,%=!g,%kQ2Y%g=I", ";!M]E26(;sIwoQc!E,==(I;!EYAUAM", "6]o/I%2I(==2AM]k!(o2s,!]wggE]EI6s6%M", "QsA(!oo=w((]AwIo(%6];gck/sM22(6=YQ(", "(E6UI%2UUM!22s;]QEE](I(cY=,owYYEkA", "MEU%w62;Ms6/U/;wc!o=;2;%A6Y6(E,U/I%(2", "kkg6M6=!s=]](!,Iks;6ss=s=M%k!;,A,%]Y]", "ggE%gwQA(6(6c;U%=MskEgsk6;=g%Y%,//Q", "k/6%%U=Ac!(IYQckI26kwkE]o!=2UY%26oY", "w,QIc]!c/6(=ME2U(2MUM!s,o;YY2]6YA6", "2=]Q6;Y(]A(gA2=A,Q=Is%]Q%w]A!6/(UYg]", "IUkA26YwoQQwwQ2(c/,QQ]ccg(6(Es,2!U", "UkYQ(((sU=YI,/g2I(sUIYsUQo]2Y(sI=", ",,M/A(,2==YEYwM=,%wMI6M]IMAMkwo", "c%k;IQkY66M/wQQAQQ(2,6kEo2wMMYoUk2Io(ckI", "U,cAY2(,A=//gEAQEUk26M]gU6%Q]QEwYkk6o", "AAwQo;gAoM]QM=kwc6U,Q6so2o(YYc2=/,;=", "k;66g,%=Q6Y2IYcA%]6UI;U(2gQQE,];6", "6!w2U=QswQ6]IEMMwcMgo26;w]QoogYMwMU%wwE", "UM2gUU!UQQQ!E!o2E;!M(cQ,6A(Y;=!=E;w", "so%!U=QE/g,]!kQoQocE%c%UYoE/YA%!ogE", "!(c=](E=MkYc(6Q;Ig/2/Ek(2,kI]2(A/c/!", "g2UcwIcY2w%(EsU/(2=];U;(!s]];Yoo", "sM/!EM,UAswsQc/;gQUE%/k]]=U/]ss]", "]E2E/wQQcA,]]w]E/I%2g/c=]kg(EMY/E2YQ", "IAkE%QowgswI]Qg!Q6%%w2!!==wQ%U!", "EoIg;6s,A;UY2,6UYwQ]cckMMkYQU,=o(/6M(", "IE]6YAcooQY;I6/g26AwQ2w]Ek2I(/;(;cYMII", "M=oM6Eo2Uk!YM]=AM]oMY22%gc,Q,]MsU", "o2Uw%=k!c%%IUYcAM!M!kY,A,(62Qk", "wAs=U,c!6IU2!6IQ,g(Iw6gc!(g]AsUs,2,", "Qc6sog;w!2k!sgAoY;=UY(E%wMcA,sE]", "gMcgI2]M;g(c]]!sc!gYM;gooQUA(,%=A", "6Mg//Mo!U=(=wsgAUgg(M!2gs%YUs=/k]]QYA=I", "6Mskw/g66,Y(U=oE=%/sc/6/Q=!Ew],g;", "sMwAww/2/wsQEAI,E],,wQ2AE,//UQo(,6", "]kMwo!w!;,I(]EA!,coQ2w!gs=!6Mos2Ac", "(;2]QM!6QMgA!sI]Awck/gA;A=I6M((Uw]c%(2Ak", "U=ccow(!o;IgEcIs2I,=k/=YQ,=,2,2/%A6AoQw", "/kU(6,!cQwQ,wcEwQ(c%ww!c]oI%UUc/=EIA(Akc", "ww22=!EE/ooo,]=]wAkIAAcAAooEMU!%!MMYQo;=", "/UMY(%cY(6,YUUwk66MM;ss=%%MwgQoA%A(", "Yos!(M(kws;=IEY2=!6UoMQcM6%!M%,c", "UoIM!I6w6o6%6;(Y(2,26](E/2=]6g2", ",=Yk!;6(U6]oM]ckIII6],=(cQE]cQE%IgEw]", "!U=oc6AY2=%g6]gMI(!E]o]%Uw%!2]!E", "Y6(cY(EAA,%MMcA]ME(,og%;;6Y]]cokI/o", "=Y6E]ogQk!E,%U=Q(;%22c!=Uw!(sg", "2QIsc!===;IEM%w,%U/2](M!QU;UQQc", "Q(YcUYs(s,EkYAg]wM!kw2U;o]ok%Y6/6s", "wgckYEYAAMY]sYEc/I]UoQEA6/=/%M%!", "s!Isc6;EoY]E(EkM]g,Y,c/%Ig%YsY2Ik", "gsE/2!woc;2Is!E(sQ=(6M(2U((QU(;,]YU;2o(", "/Q;!,/gQc!os]E%!Y2Q%gc2(;Uo/s,22kA%M2=6Q", "!Y%/A;!ggs6E%M/cUIwA;Ic%gEskQ;;]o(;U", ";!E=AYoEM%6];!,%2s(Q;/6622c/%U", "=gg!kUIg/];]sA!(,/226Q(]=YAYk!6]%s!", ";!I%kgM=%w]g6g%k;2(]g;ww/=wA/s!%", ";!U]Ig((]I6cEkwos=!A!sAowQIkcw6!AIcMw", "k;AsA2MsAgwos;!,gk(Q(g6IY%wIgUIs", ",Y2w%Y=s!,E!/62;E%M!Y(Qc!g(kE!(26/", "](Q%YQoU,/Eow(sYgM62A2s=%wM;cI62wQcI", "E%!(/(EsM;,c(IE,!(Mwsw=/s2skI6]", "c6o(IIU!EQ/EM;IoYYA(Q6owM=IYIgo/w%YoE", "=Y;6Y;wQc!c%kk=gUo/(AI;kIgoAgQswsUYIA(", "%kkY(/sgUwoss],6I,(=U!=sQMgQIY,];ss=,", ",U!Mkg]Qc2YAoIIUw(;s/AI(;gc6EAc]%]w", "!(]A,=Yk%%I=ooo(s=YY6/Q(so;(;//=w", "]E%ME],UAYcEAgY]cI2(%;IIM!(2kg(UU;Q!", "Mk]cAgsMkIQookQ6=/]%]%ko%=wc](g]E%!,", "s2M;=%,%Yc,/o//E6!6%,I!oMIc=w2Y;", "oc(Q=woE(w!sQk;c%!;U;,o=;,M]](", "(gg;,o%(QYQQo,=kI(IEogE;6IU;6gM;,", "QQQYwU2gM,QQ2=IU(EooEg(Y62w]%IE]o,", "QY2=E=6A=YMo,=!;AI!QkU=/sgk62!", "I%(E]cYQw6Q2QoIsUIoIU!;=kg==IA6/", "(;6%oY6MM!g/Eo]Q(E/go%Aso2]]k!Ag6;Q6/((,", "2M]UYQM/ws]M]=Q%wU=/wk/EUgsggkEY", "c/=wo;]cYgsgg]]2/g(AI%A;AY2QoEw6k;s/", "UsAsEcUMsw(g;o!EAkk%ggc66ogUc!MIY", "ws%(=Y;ss26/w]E,QE(,(EkQ2gI%6g,MM;cM", "!gM]A6%oMw(wA2(kwAEk2!2Qo%=/;/EM", "!ok%%YcogsY2wwQE2Awk/IQQ/;I(!/EQ/II2", "%=!gYUk!(IQMEYI]QQEoQQ(k;(IY2U=2ckYA", "gEYAQg6M=IQ2o%(2,;=gMAM2=Egc26", "Y,]M=c,QwMg(=w(E]I(E!2=M]Y]M;,/", "k!UAoAU;M!UYwcM]==w2!ww%I;,!ksIs66/U", "6kw2go]s/Uw/6YIU,o2U=IEQw]o(sYokEs", "g=Y6=%s!6(/6YcoEUEw;%/AU=c2gEkM", "2sg6YU;Q!k%oYw=kY2wQ;2UIEAEsAs/Eo%k;]", ",%csI=g]os;/gE/(2IUw]]%wYooQco%gE", "sg!IIoc/,(2QE!I(wAE%(2w6gsM/EcAA,EII/w", "oQsEMIgYQ==2=;g;=gQg6Y]EYYggI6E%2kY;IQ", "=/oEc2===Q,Ec,=Y,Aw2EgAs!EYM]Ao6", "M,wo%/Is2g6sU!sgEAgcEoEsoAsQ%/]%c;/6(/", "o(2cIo;!sUM/gAQ2Y;YEocEU=MM]I]w", "Q(c2AkAQ(]%;MM!(o!sY]AM;(EogMws,]E%k", "MEoo=owsQwosAsgs%IM]E%2]E=,ggIgk/s/g", "];!;cM/Mcw%!MEA,AI;skk2Ug;=,]!gQY2;IU,c", "w]css2UU!sUA;M,2o%w(QE!6s=(E]c!;Qc", "62;2UkocAwk!(M/]M!gQkIg2=E==c/E", "g/sYQk;]2wk6Ik;g/skIkQ%,o2w62QAscM]A%/6A", ",ckAs]%/,%YQ/swo=oAwcg!gscY;AAY]6o%", "w]Q2]cwcYo(=YwU!Q((YAUkI]2sA,o", "M;6Uc%w],]A(E;gQs,McIw]QswA,]]2=", "(QcMw/=%Uc!gU]A2cEY;!U(;c;6/Q;I%sIE]", "k,oMMIo=AMw;kEU=kY%oE!E/(IEk=Q,=k=,w]26E", "%w],](Ek%;2Y;;%MkYkYo;gQ!gsUk(oY(((%Yw;k]6o6IEsc%YAYIE]%A=U;c!E,;]oY!wM!;Y2I!QQIs(A=IgI(2IQokggAYIc]", "ggUYIM]IEYME!!Q!6/],%c6%,sE(cA((s;=,/oU6I;I%!QwcYQMUg;,s!AQMkA!]%6c%;EMIsEUw=]kQggY!,YAIMkY]!!QI6AQQ", "YI/A]k(%s;gsc!]oUI,M]A,sEME/,]sEAUk,c/IckY;6/osE!%MwMMM%2IYws!Ew%k;k,/k,!M(E,IU%((;,,;=6A(cMwQs%](;I", "62]wscY=ws!]%Y%Mg2s=Qk=]!M/skgg((;okwIUIEY]s/!ws62s!Qs/;w!Eg=c%!%E=22=%g(((EU6/AIg;,Y%6M=;sU/2I!g;=w", "M=6oMsgUIY2I;6(oE6Q,U!os]o,I!/,%6(2;!6w=wYYcUA6!woI/]owM;w(M](s!6QA]gEgY%;ggs,Y!%sw2w!s%(Q,g;EoEM;%o", "]AsEY2]]!k%]A(,2%AwAk=!Yo]s!((I6AA6g/k(c]6MIUE2g2,%sgQ2==Y]QMYU]k6/sAggk=!]QM,cgsoE%YEkEQYQQ/2gIE;!%", "o!==k6QMc]UE==UIw=(;Qk,U;=Y(Q=oUIY,6E%!((];E2Yc]c/IksYo%ks;!!/2UYs/]/k]Q]=2E6(EM/%!,2gYgQ(E=,]c!!s,;", "coE/w],Ao(QMkgs;g((62kwUY;c6o%/k,o%A2Q(c%]g;=UUkwMkQoMYwAA6%/UwM(sM!(s,,2oUs%=YkYcko(sMUkk%]=I;AUMYA", "/=U6c2,s(I]kwU=Ek%Ygs]/,,AUI=gw(c,U;]2g=AYQU=%=I6MA6Q;w;]Y(o%M]w]==I,AM%/U(Ac=]Eog/s(MU,%I,/gMA]c]]6", "EkgkA(%];AAwoE%]MwQgQc==/=kY]!6MYk6w%/o,g/kYc(Esg!,%]%!;gQ(;2koAQ2MQE(A6;E/YcIc(sIMY6s;=cYwo!UM2sk/6", "IMI2k!(;k=UEo%2AE/6!(AAI(MU=w]26ooAs(c=!=!](s%Y/QcIk%Mkc%!2%U%wAckwgMkw=/,gsg6A6EgI2EskwIYEAI]Qk]M!s", "I,!As!s=Mw,ooQ!,%M!%o%gcY!QscYg];Y]!s2=UQ!YwIU]g=%kk!I;sg;w=wMUk,oUk6owE]AQYM=IU6==(]QEAAUkMUsIEIEg2", "%2/(E(I,;wQ,IQMYok;,MMM!sA/]c%skoMA(E2s,2oIQQkk!62U;/IUk/,!]22gE6%c]]M,M%owY;Ik2,EEoA!AI];/s%M2/2UE;", ";oQ,/2IQkkMQIQ6]Q2!=YoUQYs/EY%];wM6s=gAgo%=;,=wco2wEw](=/6wY;=%;w(/o(o]E=csUQI(c]Q(sA,;2!/%%YQ]UQsUk", "U=,A==/62Qk=!(,/(!YEQ/s!YAskYocYc/Q]cY6%ccM=M/]2swc=YosAI;=kg;(ssk%M,;IQ6A%/kM!;gk;6k%6(2]sg;gsEoEoY", "UUY2w%]Y6=ws2=]I;Q]%w;%/6sA2YAwI(QEkMMEMg!E%so;MQo(%w=A=](Mk;IAQ2,6s6o==QYE]!2Eo,cMU,/U,=k!Q;E,c/Ic%", "Iwo(;;A,Ao!;gcgw=EYowMIEA(/];,/6s(Q/IQwQ(sEAQM6wQUIsg!,]6/6%kYUog6(2Icw2w(=!(Mg;U=k/6o(66g;I;Y!kk/so", "]%/I(;6/AQ(262w]EAY!,gE,62]E62M,cgg==/Q!2YA=A,/]]oQo%%,%sAk!g2MY%6s,A=woIQw;Esgwkcs!A;AgQ22kY%/;,MI;", "Ac2QwQI=!sokkIAA],];gIM6/]]]MUUQ=]QQEwA!6M]EYgg;6o;=I2QcYYsg/%Ek!ggc!UM6s(wo6/;6Mk;g2gUoUo;!MIY!(=Mc", "/w(g2UE/6Yc!!;,o(;EY!ogg==,(AIo,2gM/g6QMYsEAooYc,]=;!c//(!/E(EQI%EY=/AY]I;U/c/,gMcYI;gskM=w(]s;s,c%!", "Y2o2!c%E,IQ(EAE;cMoM=g!/U=IgkcEkI6=,,=/g(YQk],s,I(c=/sk=s=gYcY]EUE%//EYoAY!E/UsIw/,k%Ags,]6Y((%oUwc%", "/sU!wE2,!gg6gcY/!cU2UQQEo!kc]/sEMg6MA(,oskwI!%]%!]wI6;I6oEIEQEYw,c=];,wo6MMM!(6MYk=gY]Q,McAA=wc(/62/", "g%]=//QQ=cEcMU/gA;/MUU,]Qo(k;wA6A,s,M/Y!]6w22Mk!k,];UIgQckkYYk,%kM/=wEY%Ek/UkAYAs;UEk=;kwo6k(2YQ]2EY", "A!,(cc%AQ;,%k26/Y6c,U!=%;E/Mko2I]]%QAE(=(62UIs=;kMU]%gggks=Uw=EswA]EA]g2Ak/gsggw%kYAQ;wQ(cc%AgUkQ]sI", "]2=6]2%g/gcY=Y!U!Uw;,!2=A(,/o]=]2];w=U=,UUQYw,IU,%Y(I2]/w=gE/Eo!(s=YY26cAsk]M!s;]=QkIQ6o!E6sE,M!w6Y/", "wwAkE/2A=o,%2=Q=;w(E]%(;;EAgU/;=EY;E6AU=AAk=(cMoowc(AE%!QwcQ%Q,U%M6o2wQQE2IkM,I=,2sc2w6=IY6As/s%%Agc", "%Y(]=!Us!U22U=]A(6]wM/gI%YA]MYE=M26EwkIsc,6%g%,cAU2g6A(sIEkAw]2;o;g/6;oc=!66YQ(g6AYAo226wsk](s,6UEU,", "AggY;kUk/,c/UY,,o6;AIM]2U=w!swwco/s=kwIc!,EswUwk6(EUow/,%/(,//A%gI(c],%/wccY;6],,cYQ!]I!o2Q/EkM]2wQE", "2/]%I(=M]%kEI((]c;I(gIAI=A;w%!g(EMkAE(wY2,AY2]=I],/wgcc;!sEQQ,Q(6wQAEw!6]o,Iwc=cco;I;,Yo%;c,Mg;!gQE%", "I(=!%gY;;;,]26s%Y,%(wA,I6wQcMg,EE/EY]QUgUggI]U=6(//Y2k2UME=/I;o%sc%AIEw(]!Q,E;ckksEMY6=I%6g((cEQMcMQ", "!6sgMk(((c2%gkk2A==]/gE=EkI%!w=kM;wck;6E,%Iow](o/MAwo=o]U=UkwcEwQcYc2(wQIQ=w2A/;s==QE],wc(g,s%6sw%kU", "]2A!,]6,o]MEoEIgE/ME/;,/%M6/6Mw/!cYoo(UUkkI]sI(M=w2I=kwow2/oEMMc(oQUYcgQo=,A=,!(E/sUoQQ,sMoQw2;Akkoc", "A/2s;]I(Q]MA(cA];,QwMMoEg!g=s;(s(,oMQ(Q(;2UUYc%c=g2==/E%]!(;%gs],A=ggo!%];66skk(gwo22wM]IEowQkI]wcYc", "]2Q/;QcoI6Y2=/g((ckY]Y,gMo//M]%(Mow%2AIk%]McEMM,],Qo2s=I(wE=;sU=/cY=sI2,Q/6]2]cc%I/(Q;(E%cw=;(Ek/6=!", "/,cM%=!/gQ=Iw6oI6;sc!(QEY%U/EI(sM/Q2=,=2koIc/I;/=A]6cA(Mw](2wo2=%;%wwc!(M;2s;=(sgEk,,Y2%/=;UY];=;w==", "(;Y%k/k6!IksgM=IUAUk,Ykc=%A/]c%w%EocIgswIswE(skYEkkYQg%I%cQE%Ec=w%AEk6o,;%Ag(6=/I!cEwM/2=ws6!gIAsgI6", "kQU6E=!UwYggwIQ,;!g/wwcQ6Isc;6UooMc];gcwc;w/IEo!IE]sEI2w;UYcQMEc2UQ!Ek6!2Mo!6YUA2Q(=UI6UooM2,w2/]%w!", "E,YU]k6AockkUw;cc]gc6ssI!c%AM;]=];w,2,cI]6,;QwQY]oY(s];=oU=Qkw6s,oQ2Ic](U2!g%Y]/cME,c/=E=Y2w;wc]226Q", "E!I2o(EEg]so(6(=Ys/occ]AA/!]2(!2wQ=U]Y6/,;EIEsw%E/(oY]QUUkg!Q;I%AM(6c2I,E%I]EgYYc%]2E%A=;woYM!!M;Y=/", "%c;,(2U,];g;YE,Y2YwoE%YkAA(Qc6!UA/YI/Uw2UgI6/62=%M%wgcgcM;IUQ/2YI,!AgIUIU]Y2=62wog;oQ6E%!;UMUwgY/EAw", "Eow/gM/6As=(Ig6kgkMkYw%2Q(]EUIo,w22]oEAcQQMk]YQAEw=/I;cc(=/(s!;!!2];!;Mk]Q;w2]U((%E!M/,IsIg6,26UUoc/", "w!o!,/=2,U%Y(Y6Q!IE,kUs///IYUYcM=2Is;UUQ!Q]2=6%,Y]EY(s6Y,2sEo;sk==/g6/UE!;(ocA,QU/,]/g2EIQc]Ec]6!Q/]", "IU6;cc%Y6;=/;,kI/wcM,//goc2wQsUkQ%!I66YYU,o/(E%]oc6M26;6o2/A]%!wkkIgAU,A;6/w6cAwws!]6(2oEo6,c(c,Q!2M", "Q;gs26A(2QoQ,Yk=o6E2UYcYA,o=/]osk2sg,/6%skoo,2Q;w%oo!E;wwgs%A]U/;!/IEwMsE(6;,%sYk/AI!=Y!,M,;w%ck=%]!", "%A/Mw;=U;gE%]EIg%Agg;wcU(2QkU](Qo6UU=w]c2gQEI6%Mg/(wE/6%Ac!;=6oc=As,,YM/EY2QA,Y=](]kY;k26ok/A6wMkIgA", "c=%ggEQQ2=2,E;=gss!,oo!(Qc%/]2,2w(Q!o!(c%QsUwQQk62k,MMsEAs,cM2%oAs/w/QE6gs/6Ags=!!k!s;62/kk]E/I2%YA(", "Qo%U,E6M/=wk(U!Ag;IcM!==w!g,Aw/w;MMwQEAMkI=6QE!2A=(IkIkQUM=%6Ak]%2;,]EsQQQY%A(EYk]AQQAQI(QUAE%!(,kAc", "wU,(Y2sgM=M,!/,/(U]6%M%](c%]2;s!skwEg%MsME;gg(oME/2=IQ(6],Ykck%A(gw%w%w=/=Q6,2=,]c!oQ2skAc!ooo;(g=IU", "UU!6/g=/!6gwcM]/w2cM]2gEMw(2=;IEwcE6=skMU=,/sU6Qk;g,EY]E]AUUYEYQs]%ME%6%,;,!;cQM/A62%!,Y!c222kMMIM%Y", "oAIYg(/Ug;IE%sEQ(EY2M2E=s]%c!sIwQ%YQ;%YQ,s]ooYw]%YAwM=/YsQ=!6EAIQ(=c;I!o;%I!26YQ2cQEw,/wUU2wIY,62]%!", ";kY2gogwcg%;Is6I;AYc]c,s=I(cw!As,w!,%6]A2,;w2UIU,U%Mc6oQAM;gEs,UIookwY;=IEw2,]E%o]Q/AQ/]Es/E;/c!s/Y!", "kMM;%skAAYMM2sU,Q]Y;QE,26/,M;Y];go%626UU(;cs!kc,]M/(%6M/QwoM]M,o%o!w6/ck(!wQE/UUoc]%I6=/2%6YcQs66/]%", "2sEAA,goQYo%Q(2swoo6!!gEU!;kU(ggU6U=U!A6wwk=IU]Y]2];wQ(2cE]ckgAAEkkM!YMAE2;og/]w%A(Q]cow6/A]%EQUgsok", "!QM!Q(6,(6](c2ooMMk=o/cUUA=%cMYQsA=YIU;6wE%(,o=,IQMMQMoM;%U/!EYk/2]U;=A,Ik6Q]QYUI(oEo(6%occ%!6AsQUkY", ",MAI(/kAo(Io(6!6]A=c%wAEMkkIcsQUIs!E(6%M!ko%E,2QMwI==wQ(/AMA2=Mo;U=/(cMEw%,2]cMY]gE,!gUs=,U%;/g]6c/(", "Uk=!(2UM/]AU;k/6M/;;I;6s2w]Akw,M]g6/E2!2!(2/UI(%];gc/EY!%6MY!cI(sY//Is%/sI!6MA=gUkM,A%YcQU/((w]%YY%/", "/%Y=kYI;]Q;!/6/I(;]o6scQgQs!]Y/c!cAk/Y6IIQ2(,6;Ugs;6g!Y%kYYc%Yg;],cI=k=Qs!kY!=YAg(oM!U(=!Ug]%Uw(g(s%", "]EkQ6w=Ikw==!22IgIMg;gMM!/Esw,IQQ;g(wQ/g=IooM;g6Ikkg%,A]YIE(k/c/]M;U!oIAwgsk=A%2wQE%26ocI/w%M;gM2EQw", "I(Q26(c/EQYc/%Ug%I!]A6;6%Y%wMk62gc]Y/g(kUA==%koYEI;gU%/U%s]IU=YQ/I]g;Y2;AIsEYAA]I!g6g6,=Y((QEgA=YwcA", ";o;;sIo6Ac;UcEUU;(Y2MsUk]cA=,s%!%;Iw%M]AscIY;I;c]gM=2MYk=!gwIUIss,c,,Ag=o,]YA/sowQ!ok!;Eoc6MYc%Y;AAY", "gEc%gEI,26A!(6Y!;]/,cE%M!UwQk6Q=gIsQ26QU6MMoc/]2gM2U!(6AIQ%A/sg!=6g2s=%MI26U;=o2sYk!sAU]/cgEwQQ]%!6/", ",/Y];ww%AUc]cIQ;6%2g=,sw]!/Mk/UwY]I,/Y=,2Ag!AY%wo;=Y(2/EMA(o2oQYwUwMgo(U=;/II(sk]6ogIEU/!g6/!QM2;Ico", "(;!6skwo(Q/kcs/,%!g;gg/,2I;!]/(]Q!MA(6A,%M%M;MUQQ](;sQU!gwgM62!]6]2wAgkwYIAsY2sY;6!/,Yc2gEAM/UY6%Ag6", "o!(/=,M(YUM]I;ow(cEEIs!(2k/AAA((Usoo;2;YAoEwkMMY]A%/%6k6Y2=ws!;w%=,QEM!kAUQ,c%=wcM%YgQgUM/6AUg,(=6Y;", "=!6(!M]oQA=w%!osEI%AUM=QE,2(I6o,=AEkkUk,!]M(62QEAUgQ]s!kMk(sk6YckY]A(Q/U2=kooME/sgc!s,U(/;I%!;AAo2go", "(cA=EoYA=MgcI2/Aw]%%U(Ew2%EowUY%w%]2k,%!AY=Ag;=w!cogggE!IMMgs]!=;/YkMI,E(/g;!QgU,k,=UI2oIs!/]=Q/o,=w", "%ckg=6;!sgwQc]!%sE;]U=sgE],]k/6sQk/,c/Ao(o;cY%E//c]AAw6o]626k2wo!c]Ys!2c6!I6]YAwQsQ=w=6]QUAkk=I;I6oc", "Y%kQ/6gsU%%sUkk,M((M/cIEMUIEk;YQ62k2c,s/6k!%AwQMYA6,2Q;Yg6k;UwsQ(=wk2!,!(c2=kMw/6I]6UkwQU,2!cMUsg,,2", "w!UYA/I,Q!%6IY!;=EY=o2=/]%YAsscMA6M262E]wc%Uo]]I%!cY(6A6Mk2,oc6%;Y]sQwo]]ggYw=]Y](=g6,]2c]6/(A]UUM==", "2Y;sQ2=!(E%;w=Ek/sU!,gQ,/g]cA/IsU/6Yc;wwAY2%;]Iwgk;c6;Uk2AA,/gI;2Yggw]Ag=,/Ec%]!YIs(soIs/QcM,(o!]!6s", "M2!%wo/kYUkk,YU/,%Q6,%(k!6=Ys,%(2,kk!;;(E;;ocMwwA(k=gsAcY=%;Eg/IUA=!E%M/wQ(]2/Mwo2k=gY6A(,,!%wc,o(6A", "Ag6;IIc/wMgs!IsU(!QIg%g!E%A/E]YI%Y;(,6/c];6%!,%,gs]oQ6kY%;;,626A%AwUk=UQM=!%!cs,%EYcw!U%MAEoosEYMMw!", "c,6]%;2!w(,!6c!wkMkcI626]sg6ock]UY2Uc(%sA(2,(2skkMMI%IQ;;!Mk=kM//Ygo=kAw%;2=s=kkQ2Q,EUk;!Uc//g(c2gg=", "IwsE/UIEI!I=Y]2sM,cc6=/UQok=(6U,Qo%]Eo=Eg(!//6AIk!g;Y(;Ek2MMk=oco;!!s2oo6%Igs=%/g;;AsAUEM!Eooo;E((s=", "6Acs!UEo2cI;o;oUcY%EsIAkkQwYkMQco!!cUg/%;w(=!=c!Us==,!E,AQ/sQ26Y/gcE/%wM(6w=wM;2UU=kYA(k=]cA6wocYc6w", "w!Mg;c]E=,2w=!YYgE/IgUcYQU%Y,o]/wY=/]%o,MkU(6Mg6wA=Y%E%2%AM/=Ywc/sQocA6Qo!,AU6o!I=k;=!I((w/6g6]w;6w%", "]cw2(sgoAUo2sIk!ggo%s!=%s=U6Q(]==I6/Y%=oo]=]2s=MwQ2Q%A/k6%YEgsQQ2MI!6c=Y!g6c!]ocQ(Qo(;6YgAYoQ=I(6oEY", "6E%k=/M!IM=EAsI]=QEkkY6k!,2Uc6,gA;EYs,26=w%AUI(]/2U6Yk(skM/66g(AI(EEU%gQUY]s;k];==A%YA/,c2IU(%g(;,AQ", "Ik!6U%g2Yo(QE/I2Y2M/I(2sckQQQs=k=/Mow!/%YQIEk;g2g=%=2Yc!cA/AscUkwo!Q;QEoI!(%swc6=Y(sUUkw626AQUk=!gEk", "sIss=/M=kc!Eo/M222UIw/%A/wcEM]k(sYo,oQgQ2wws!wI!g(6/QU,AMYw2]w!%Y;MQssIM=wUc]I,kYQ]c%6%=wMIQoAgIk//s", "IcYcMIo;(UIU,M!;g,AIEMQU6;6I,=]Q=ksUYQ6sUcQ66wAogg(6/226AY;IU=cg6E(%/EIEQ]%YQc/AkYw]o((=,%YIMMgEAwkY", "((,ksg6M!U2wM!/YUo/IIEY2E/gEc%M=Y!UI;w/;!6ksIc(w=//gY;A/(YU==kUU62Ek=IU,,=AwIUkkgcQQo(/%!UI2/]Uo2,ME", "AAAIc;kA2s,sIs!2=Y]cUQ2skIs;2%22=oAAIQM=g,]wooI(ss6=!]sgI/IEk2gs;s=/(A%6,ckw=YQM==ocYAQcwo!,%Y2,/YAQ", "/Uc/,,g;kY=(,AEQ%,;!k=!6UEA=Q2w62Iw=k]2/Yo(((w];UEYE/Aoc2Yoo;s6Q,QAEggM]!2,gsYUAI%=;]Ao/oIQc%IIQos2k", "=,!]UwoY]=/IA;!(M/,/2ws!swIE!,/Isk%U/gQUY=!%EA;o,]Igow]YIQI6ocQ(kA=6c]AQ%]=oswc%sIU],M!sIUc=IcUk!E,w", "6sE;//(6A6c/EcMc,oAI/Y%Qs!]k!%MY=w;wo;=(6;%w2kgc]k6o]M,UsI!;YA%Y]%AgY(2E!s6ocEAs]%Q6%Q!(!;U/IsIU(6U=", "scok!,%,c,](!=g2!;=IYUIEkcYsw]%/]ocg%kgwM/s!,/II(;o%Yoo6McA2I2,AEsk/,]k2c!ooE((=Qo,I,o]soQ];]o;cgA//", "MIQ=oIs,!s!gIA,!,cgg/gQIYo/!Qo!E]Q((c]U/EIEso2A(((cMsw=MUg6AU!YIQcsk/EA6/6I;MUU,M(s,%6M%!%c!w2U]kU6/", "M]Ek/,s%sI(;UwMsgE]cMgwQ,2,w,]Q=o;,YYcY!g=sw2EAI2ggE]EoE6!;Y=sIM/,o;wUMg/%g!gY62YQk(6s=M2c6o];MM;AM2", "UYc!sgkA=cY!!(E,cA6%YoIs!2IUE,sQo62]/IE]Y=IQQAAs=ww%AI2s;(osY;6!Eg6Agk,E6/gw,Ac/((Y(!!ME!kckwYg6Q]ck", "gY;=ssQI%EE/,o(EEo;IA(6QUIE;]s==!o,2U=QAQMQU]s;,2s6k(]A=,/6;MwU,kw(;YMow]2Eo;MY(c,,2(oM/wE;wE!UU==,o", "ggs,A6QAc((=,E]/%2,M!sM!A6AA6E%cMYUgIIQkkMo/c/s2U;IYA=gYA6AY2Is(cAc,(QosA,oUw26gE!I(AMsco%!IQ2/o;%EY", ",MY!(]Eo;Y]%=k!E2MM;,%]Q2!,Ao(YscMMY2QEgcMk;IEE]%6cAs2kU!]]gg;==c2os!(k];ggEoM2%M=%!UUIg%2wE/YcYIIs,", "IM!wc]!Yc!6!gosI;gEEYQ;,](gk6=U2gw2g6ksUAMgw%M]2w;=!((g;w2YAI6oQ;kcYAgAsg,/6I/EoUYo;=YQI=MA,(kME]okw", "g/s2E6s=g=Y26!koMw,]/;!YU/cUEY]wMAsI(Q2gkMMow(/%kg(!%AM;I6s6%AM/]=!](cU!6A%2w(s!k;]/ws;=I;cM%ok;=/s2", "YEwU2,s,!(%scYkgE/o2c2,E%]cYgg22;=YUU%]Ag%s(!Ys%oQ((,MM;gE%I(6MYoE]cI6/!(EUc!A2o=62w2QcAgwc!Q2sgQ;2g", "Qgsk!;%AMYY;o6YQ]o(;U,M,/(sM6!k%]ws;EAkkwcks,66YUYAs,2%E/Ys(YcoQocgE=I%!wAIs,,Q/A(QI]o2!U;!oQQ2s=MIM", "6/6AIAIEkc]kI;kwo%wc6],o%;s%w],s;!=QoogsgQcU;c%](I=k]%Y/U=wk!U,]=;%6s6]ogQ(UUI,%I(;YY/cw!gg;]o6/U6=I", "o2gE,]2,co,/6!MYcYU!EA%,Qw62(%kAUAggwQ;,Q2M%,/IUU(YQo;oc,As;gkYM,6YU/wA%=];(6skMAMM%(QEkk=M]Y6YoQ666", "2s,c,MkQcwo(QQ6g2I/2=/k=/=Y2Mgk((gY//IE%gI/]6/(sc=Y]UY!]s2=6o%gE%!;%A26AAM,;;IU=Q(g!wo!Y=,]A(c]oQ2/w", "g=I6]kk,=As6;Ek(Ys,sU=I6MEA]wE2(/(===/(!,2soE]22]cc/=262]o]U,]2EggII]%Q;6/%k!MUUk/;,/c%w6ooU;6w(EAgE", "Q2gc%%;!==I,kkYAgQ,A];!6YcgkYYQ(]/,QU!62A,]=c(,oo=/6AA!g6g6YcAQ!QEY;Ywo2,gQ]c6,%wEY(sUYw!ck=I;AUMsQE", "YoAIUs=kQoIg!oQMc];%Y%==;U=cs2=Y;I6=ccME!%EEYA%/6wMAAsksAEMU(;k=2Y%!I(=g6]/66Uck=IcggIM;6I,=s6I/6!!Y", "(cw]AUcMY],%!;UsMMkMYcgQook=Is(6=IAsIAwQ2UkQc%Y,M]I2kIQ,6MM;Y!;(2,o=!I6M(/,6MQos,%k=k==/Q%26YAIswA%!", "oUwU/,M!o/(sI66!6YYk/ggEggUk%!sg=/YsI;w(6ock]Q!]Q;%]UIAg6sk%!UY/g=Y%]o!EkA=2wE%Yk=I(E(;U/,ckQck6g/!g", ";gEQQAQQooc]6/s]oQ;g2=,gMIQwAc6,Qcw(w;w%w!(E/w]wA%M6Qs]6Y%AgsoE%kg((ow!sIcUY]AMQ]AEkwYg6UkE]]AoYM(Q;", "Io%2gskMM%/IYkMEscI]AgAc;!(6okQ/=o(sE%!w!,wc/Uw%!g,2/=(cY2QUUw!6oM!!]AcckoEo6M!w%Ac/%wIE%As6]!A6]6Q2", "ogQcw%]skIQEw(]wo66Mk=c;],/IMUUwEI(]A,/,o%/gM(;62,EYIgg/g=,,%gUU]]IQo]E/,A(6wg!,2s!kk=kc=!=kQAE;%,A%", "6%MsIQAkAs!,!%/!;IsMo%U,c;Y],kM]wMMEoYAswQIU,!IU2=/c]2w/M/,Y2,Q%]6=2AIo,cMAI,/Q2,/IYwMgcM/(s(E/s;,2/", "]2;A/wssI!;!E/w]sEI=g2(Eg2s%,/UY%,Y;=(gk;]%,AME,;,;s!MQgEk=]%=(Y6/(w2gM]];UYow%Q]csk2wMMIA2Ygw!s2%/E", "%UkwEsw=,UoY%6koQsY]!EI/=]=/ok//,o;s!!Q/I,Es(A(kg;6=IEMsY](%QIIgEU!(k=%]=!Q6%EAIgggoI(%;gIQcQwM!EY2g", "]6oEQsgMAk=I2IAwIgQcY!;!;UEAkkk2(o(Q(YM]Ec=/AAo;UMko2(w26s%/c];Ig=]s,/o/%k/=wQwMIYQ!%%6/,]6k]6!!Ec]k", "!gE(;w!Ek,c!,IswAog=k]w2YA,cEkAwIg](Uc!g/,];Mcs,AEoA/M(Ask!=Y6%/U2E;c/Y;!sQ]sQQ;U=Mk/=IME]YYA2s(66(2", "k6AUg!kIM=IEo]ckc%cYwcYo;((/MEsEYgk;62I(UQc/A6%]%(2gc2g%%k6]wAU(QE=ocM/k;!U!sIc2k]=!MAoQg!k/6/(/%M,%", "c](;w=((U/,E/%M]E(I/,=];w=wc]!Ig/o]=Q=%AA%I,U!62;Yk;AE]Qkkw%M=k,YcgE(QoUgsEEgcA6M!(s,A!,s,YMwMgE]c/c", "UwQY,6Uk(E=I(UwME]o];s6/6;Ug;w,;,w/=E]%=w]A2QkY==U/gAkk!6A6;U6/I]A,o!6%k2ocUI;2Y/YQo],o(oYI6k/MM!IE2", "=2MA(UU((QoQQs=cIA=I,kIUEoUk(cs]6AI(gQY%YEMY/gM(6=;/gUUgQ,I]c]I]%=Mk/Eo6/6%Y,Q/g=,MwoQ/IQIUA6MAg,o!E", "/gwA]2w22,IggsQ2;%EY]w%2(Q/(g(sgI6g(;wsgQAs2goQ6(;woEY%w2,IUYQo=!QEk(=gUsU]c]6s!;YwAY/AUggwIcEgEQkc(", "wck/%wA,/gE;=w/g(c2(Mg/MkAAMgY!k/YUYA,2M;g6YQ,wo=!s/!Icc!U%w;,c]2,%26Q(;Uko%]s6A,M/;g]oMA/%Iw!]/66E/", ",o2g]=kYEkY%!/oo!;U,A/UYs,=%ck=o=YII==Y]]oYE/I6U!6!;;=]/(]!UQMYo;!;cQ6scMk!;I(2E!;IMM=gUoQ;Q6Uk/s!2c", "/IQ;;IE/(];,wMc]%;gQUwMssI(;c%AEkAEYc%/k=IwMYkMYg6]wI;U,I=,c]=QogQ/g;]E6,Esw](;wQ]6Y6k,AM;UAc6U(%;AE", "c,A62kI]cgoIcI/IQ!!]IQI(]2=E2ssA2,ggEA]sEY]EMY](;w=II!%!,o(QA,]EAkIAYoE]2,c!!c!U!s!;sI%!s]/6g;!UYc(%", "/I6s2so;wAE2EU;gk!,6(cMME2sI(I;/sgA]UQswQcUY%(,!]U2,o;MEkYAUkEIwMgE](YY;gwQQ]c!E=MM=!IgQI6UI/gE;U,Es", ";==M/skA6MQ(Akk/A((;Aw;]/,]ckIc/o2M!(csc6,26UM/6MUIM;g,;6,Q!U((QMQ/gQQ2/;2UwQ,/Y6YoAgAgIE/gM]o//kMI2", "!]c//w]]MggQkQQY,6M!IgU=I(E]E]!]MYIUkE/sgs!A]%!UQw=Mw!sMA;QMA%=2kwsU!gg!U,oEM=ks%6;M(2UI(Mk/AU,](U,k", "/cEkc,=cA!U=w]A/]QEQw6]/A%k!E(!U;62Iw%gc/!Uoo/Eo%(2,IoUw],c(6ocQk=k!k2g%=%ook=IUkA(!,/kMEYwAscoI6M;c", "=IQQE=IQ(6;Uww=2IMwAU(swcMcIgs,k=Q6oc=Y2k=o;(]kIc%YA=cQoU;Ec!ow(UoQgE,]QYsQ/As=,o]A2Us6cA=Q%U6A/;;,U", "YU=6c,=2cA==YsE%gc,AIg,o2cY,%/]sIA/(6=Y]Ek2gE],M=,%AAkwEog]%];,QA,s==gIkkM=/gg;A],E/YE]A/===22%Y]Q2E", "sU!M;]=/,g(6Q/;!sIE!,Ag]E]oM=(wI!IoA]%(w%=!2Mc/sw2g(Mk=w22=!(]2w!!kswk!U]%Qs(E/Qc26kkwAYY%YwEMggs/Y2", "QA!(g]cE%!/g%kIw%;UUw;/E]6(Q%;wo6%Agk==k2MQoI;U;!M=/cYMEMkMwc;UYsg!(QsA,!!k=%AAsws!gE];cM(QIw%s=oEMM", "M=U]%Q!,AUQ]/;,2Mk=/I(=w!kM](Yc=w/;,Eo(;A(Y%w((M!Uk=I%E/2kEY;66U(s,kY//cU!!]g=YcMoAI(cskIYg%=w/6YQs=", ";%wMIocIkYQ2,Qc//A=YA=,/s;M,YYcQEoM]A6Agg2Ug6%M;MM!Yoc/o6A6Uksk,o%,cUsI;=,A(QIc!cc!,k!UIg6Igo;gg,M==", "c!6U!g;!UUQcIE2IIA2IQIIgskoE6A,MY6%M;%M,!UYA,E=]2I6o;s=wM(k/EMY(];kkI(2g;MsUo(6wwM6IwU!,cYMIE(M=I;!c", ",YcoAk(]IoQ%w2woYo/wwMc2(Q;6%gA6(YUIAcEE%cMU!EE%,=,g(Q2%2,=YocA,YQE/2I%A]AkA,k6=,ccg=%sk/ow=Uw%YooU/", "%!AMw](E!Eo;6Qcsskk;UksokMwYI6wQ,UU!6%!IsU]k=(c%M==/M;!6kY;k/Io,kkg(;cY;g6]swY!s,Us!/(sw!]6;gIgo=Ukw", "AA%oM/M]c!w2E/UQ/;=IM=I6UIQ,2sUwgM/26%koQ;%(;k!=cw2E]=YU!;,oA!g!(2kkk2o(6EQs!/2Us/QoE%A;=]EM/k2w=,Y]", "%!c!,/o!E!wYUwM!kM]AMEc/M6;(2wQQoE2A,%gMwIM]!(6]Q6o(]!66%Mo;!E]U,%;!6%scE]%/gocU,wM!(U!=/(;ss6g=,AU]", "IQAcc!E6oUYssQ;o6o,c/EAQswA(QYQ=I2=!cw,c!((cs=wwg%Ig,IEAg2;g(Eo/66YAsIwQAwo/((kYY/wMA(AU=w;]/6oc%AU/", "A!s/s=/2=k!scE%;,sAQ!!csY]Ew;;6k=kY2]Q%YQck/M]o/wU;Qs!YcQ2U,,](oA;=cUMM]Q(/sgMI2A=s,I(=/UUEA2g;,AEY%", "gcM2=YAI!%Yk==Yk6EkIg;IA6=IE2QEkw,((g%kw2=Y(gEAs=,A/A=/6ckkUA=E(IM2/sY;!M,M6oYIcA;oM%c(Ek=,k2gEIQQ;=", ";g=E%oI;/UwwEYQoEY=I/2!;6Ag==,o(;=%6Uk2MwEEg6EYII,c6EwoM]A,Yo,/UIc6Q=6%/,w=Q]AU=w2c6M!E6]%MsgQ;!;%k=", "MMMAoc!A(E(g(E]]]%2,sA=cAgUUsU]A,M,!,Qo=Y!UgAgc,AQ,2]U6%;wYAUkAs!Ugs]U%MI%w=(o!AoUw2MYE]EE,2Y%Yo6!,]", "]o((ooE2/EoE%!2Q,c]A=A6g!UI;Iw/!2;!A,=o/(2A]Q=kIoA%]QI=/%wIMQ;E6Y%E,]ggc2/YY%2k/=A/6c]M(%;%;A/,gAgo2", "Y;;=oc!MU,6g6k;26As(E%;62I,(2%cYMg(gE/6g;!cYQ,6sI66,(;I=s=Iw%A(s]6oEYY2=k/;6%Msg;oI;c;6%Y2Uk,AYE]%62", "cUsUUMY;,/ww%6;E/(oQccA;6o%oEcgowcM6/,2]2IcE;((/2,QY]w,]2%gA(IQEUcsAYQ!]!A(Y/,UAQ=kIM!AU(22sEY,II,Y;", "]E!o;2w6kkM/As,]I%/%,sg;%M;6,/QcY;w/!!(,,/]2wYIQMII2cMIAI;s=k/owEwAgQUw2=!AU2=oQ(Qc,;2Mw%M26QkMs6w,]", "YooI/6o2=U,M]cM(!o!s(wA/;ck(;6U%s2Y%M==cEQoMs!,,cYwQM]6I2;/E==6I6Y!U!c!Yc/U26/UwU!(UYAMg!!s%o]2UMQY]", "6g(c6;kkYo6U=wA(2g;ck!s,;Ekk=]]]22/MQA,c=gg26(ckkA,]c=wEs;]osgA(6kwAAsoUYQIEM]YsUo=Iok2UsA!gs6AgsUk6", "ocAcI!IE2U,2wQUYgE;2]]]=]c]ws,/(U=I6YIQgoso/;=/=,Ms=,E!6MY((w2Y6%wAUUw]c/o2w%,2QE,k,2(QQEkYI!,c!wQc6", "!E/(Y]]2=(6sY,g,o,6%6A%YQEo=/Ikcc/UIQwM]Q,/2w=,,6ksIgw];%Yc(!;QsQA6wIIUE2I(]E2oEMooU]o%wk=k=k/,//==w", "oUc(sUww!EwkE6/,(EQ!U]M/IEsowMs6oc/I62oo,ws2U6,==!,g,=,Aooog;=kM6=IAQcY;gcYYQ2skkg6YY(262g%k!,k]o](,", "(/EYA/I!c/IsI]wocYE]oA(]kwY=/A%g(=,,(oc!(w]wA(cYcM6Qc/g!E6soE=g=oE;U//I,AIgg;U;Igsg2/!M//k=g2,As=/6=", "UE!IIc((Y2]c;YQo2I2Yo=kM//wIUYgM6]oI%(wQQMw]AEYYwMA=/Y(/E](Y%M=6s2QUMwc,6,AY];((Y;2/I=,/%(A=Q]QQ6MoE", "ccI,oo!Eo!s=g6IEY(,w];wo%/!6E6=gs2=!2!2s,M/6o%YI;2csM2s]2,c(QYgEYA,QwAYsA(Ak%6%2ssc/M%Y;=]!E]UY!6/(]", "ws=;=%YEsQwEog%co2/,ocU]A66g/66%QggEI,Mw],Qs6]QEs%YoU/62wQgw%=!,A6EUQIsM,AU]c2gggE;;oUc=MI!Y2owwo;oM", "]2s(c=UU/IQ!w]kgsIwMQowAs!kg=Ic!Y,sAA22Q,=6o2QgA,IY;A6c!!Ag2g(%]A((EU,2QE((E%wE2Ugsk];g;I(6QcY,cUU(;", "/2w!66I(c!QQssUoks!6cI(sw,oAwwA/Y];g/Q6%%%cg=!UgE6!,MM;YU!Y;I;UI=gg;I(QsIEg%M!EY;!YQ6o=s,sg(%M;!6k;%", "M2Iw;6%%o=w/;=2sMM2g6!=kUcAk%Aw,2!U2c!go/%//o(c=]EQE/2],UMwk!s]c!2U=/g6YYs/c6w6YMMM!Is;Uc2kIUM]Qk;=Q", "Y2M/;Qs!UkEk%A=IUYk=A%(MIo2EYQ;6s6AIQs]A,IAs/(QskU/6(/IU!];I6MMQo!/wwcwQU!E,M,!s=!E%w/II;,g]!]%wMo,2", "6]2,=;QEM=gMwE%kY%EEs,AswcA/Q2UQYcAw,ooE!2wY/g%kY%,s,6M=6%As%%AooQs]!EYoc=Qc%s!!2sYo6EQcEU!kE2I6QoE(", "kg(/kgE%/AsUgs=A;,6Y2(Qc(cM/QsgI,];wc=k(EA/6sQI,o(QoEo(QQ%wYE6sgQ;!=Y;Qs6A!=(w;%E(2]Qk/g;6(,/IUY!Ek=", "A/s%=Q/(!(U%AcIgsgk!%w/!UoMAgI;]/AUwM!gAoo;,o]Q=/o/g=A2k/MA;;k/Q!(E!,oc!QQgkE!IQ/(cc2,]ggI%!=6YE=kYE", "EA!QAoEk/ko26c(kgQ6oA,]o;I/U/,E]gE=wQU,!;=M]QkMkkM/I%wg6;UI%/U6]kwsUM=%!UYEsQ((%!Q,AoAI;,Qs==]Aw22/w", "c/(AQEY/UcAI;]w%M/%wE/sMEIsk;,/2,MAg%IoQ=s!s==MkgII%MQ;6]2g%k/2wAIE]QQ(2UU](2/Msggs/s(w%Y2UA];=Is];s", "Ew2wM,!6AUg(;U!/wEcA,;I,(,cck;Qo/As6Yc,%c%k6=,g]Qs;wcA]gM,wY;%/2cco6/(cUM!EA]skc%M;cskI!gQMUo2,kQ;cI", "E;=IM6/s(gcc(E/QMI=Is!6oQ]EQQow;6YQE%=Y2===E%!o%2=!6E!U;%cIooMs2UIUUU=Y%wQ,cYQ%Q/(];oAcoo(Yc,Es/,U6Q", "]EYkE2]Eoc(,2;s/,%Yc!kIQcwMs!U;%,]Eo/Eo;(=w%kMww;=!2o;Y2Mko2,oo6%Y6MkEcsEIgEg=k/(%YoE%Uw/YccM;2wUMQE", "%/%=Ek]]cUY==]c//g/U;6/;g!,sg;62gg((YI(=;=w6=6]Y;6]!6M!k//IsUooI,%A(s%6Ew%=Y6Yc%kMMY2,kYckQ6YE=!;wAw", "cw/]!]Usk!]Ac]UA/Q]s;]c]YQ(Q6A/s2/UUcQo(2c/6YI;]EQM]/(!Q%2];=6AEk]U(wAgcMkA((2U%M=!E%YI/c!;EQQcY2A]6", ";=,%s//I=w]6Ic%%cc,I((;c,]o6EQQ/6/2woI,/U!gQs(A2g(=YMEc(A,gsE;!6wQUwQ%6Q;2gIsU/gso%]sgw%!A/UAk=YA=Ic", "2A=QUI%Y6YQ2!Uw;I2;Q6%],YQ%kwUA%Q6=Q!EAA]oEkkw2k;,c/MAU!E26!(I62Qo;k;Y](c/kEY];!(wQ,csI,]AwAU(ocoA6M", "I6Y;!;Q;EsUYI%!=EsoQQQ2=U%UAU;E//M=!6YcY;IgoYU=c==/,=Yw6]=kk/sQQoc2;skooMkY=o,EI62kgME!s,w](kI(AQ2QY", "=,ooo(M]A=cEUAU%]k]Y]A!csg(Qs/,];sEowcYAg%w]k,,/]%66=,,2=]go2k/(6(!Uc!(o;Yc;!o]A%M=]!sUIw;o2%cog2sgs", "2=swsY(I=%!goY(;2gcI(o,M!s]((]cY=%c%;!(s%ko%Q!;M2MY=6(ow%Y,Q%/A2Mk6o;IcgA2wU;Mk26;6Mc2A((s/,c,]gAMck", "/MY;!]kM!M/YoEY]Q(kMYAo]]AE;wY;wI!%Y(w]A/s=(6EMw6!/==IkI]=E=I(;,o]woo6]6Ug=k=I,A]%/=%!YoA6A6M=E,(,k%", "MIUkw;QwY(Ac2!(s=o/6w!,%Y/I=/(s=kY,]A=2E]]QEUwEc!6A/M,;QIQ;w,(g,/UU//o(EoE2gE];U,wU,QM!EkkoQo2o=cI(,", "(6g=2s%YYY]MwEoYoA62!A(;,%(EwI;QIA2,]QQkw]2AAY=o;%Y=c,]2w;=I]UIAsw]]!kAY/g;k]sU=6wM2As=,I((o(]k=!,%k", "c%;!,kEo2gE/kMM;!gMoUgs]kYw;UEs%M((c6;IEkMUwM2=,%EM==!;=ssc/E]26U]]cUIYw%/As%goEwM](g6Io2A6/,E%YAYck", "!MUU,(I/,2(gA!I2s;IEUw=(6=g(s,MA/I2M=wUEYg!EIsw=U,UME]I(oQ,%%!s!w!2A/oQ2,g(2%(EYYk(kwkYQw](Q%!/I;EUY", "2wwI(%coUo2goA!6Y!(cw%,/A(;E2k,%/sks;kIgggY%A==k=Q(/(/Ig(2;6c2s;U/6;s]],%MYAM]QoY]sc!=%(/gAsw=6]A2I;", "wMYM(Ag;=kY%2go/UA,%koMIccAgc6/2UY]M;%/M,U6]]UYIcM(Qc6=k;UIc/sEA!]2UM]wY,k/!;w!/=IwgYQ(wM],;U%6/E6s!", "!M!YcA/]I!EwAkw2=,]o%QQAA!/;U!,Y/IQ,,,]AsU2(s2gkkkY]2=sUQwgwAUk,YIQMcoM;k/;Mk=M/Ek!AAUMkUw/,A%cE=Mk/", ";gQ;,=6A=,,IoEk=!=I;IE=U2,]ok,!gg2s/EM=EUcggko2//UAw]%Y];AUs6gsgEsks](g2wI6g2sY/262Uw6ogQ!I(;sM!w;6k", "%%(,I(cAs=Q;,6c(,]cYoQ6o%AI;=kgQ;/A=I;IM,=k2s=(Q6gE!6o%c;E%,2=c/g(swAs26U,YQ(sEMY2w2=!Q(!%6IEUc/!6Uw", "s,6Ms!I2Q]QIs(6M!k=QE]I,(Qc!U=IQUQQ;gUkM=]QMI==k2U,!(2I/6MwY%]%Q]Q%;QEs]2,Ao;Qocs%A6,!UM2=g(,]2;!,/M", "k;];6/YA,sgw%I(=]Qck=,/kw=wQM6o;sU!Mgc=M!,Aw%,;g6wo(;s,M2!kwY,Q]gg=IsY]gc!sMIos;/sw6QoQcQ;Q]k!2%]2=(", "==EQc2o(]Ek/I;=co!;6g;EIQc/E]UQ],%/!c(,IQ=(2E%UYsEUk,]Q];(,%26/sAo=Qok=sk/MIw(!;!A,,o6;6o/!g6(s2A!=,", "o%gEsk;kkYo,gkwUk]o6E6gw]Asgk;UkQ,/EYM!6U2U6s/%Qks;g(kswM/]%=Q=g/2UwQ!Y2UY]]]M!c/62gY(s;!,Akw=w]o/6U", "U]%wM6]ocY]]o]IU/6,!k,/EU6kwM/w%2sg(6sA;U/IEQ!wI=go2QgwIgkY6(cMY/Ek!ggEMkwYw=/k(2UMYw];k/EA!Us=kYwM]", "%w]Y(AUYYQ!UkI=I]QggcYY;,A6/I%E%M]!c(,Qsos,IUw!,;,oIEY%MwMgsYQE!6w/((Qws!;UkAc(ck]U%]sggwoo6(Q;,=](E", "c,c/2Q(sw2gcko2s=,%Mw!6;wIUY,MA!,2IYogg=/MM=/;,ME/c;=%/;,kA(%!2IE62Yk2%wc!cgg%cs/gYc/E;Uogk!U!AM6,w]", "sgEk/go2=]%/!gY%6g(6(2YUQ;=,2ggI(;;sUwQ6(=w]M!6A,6((kkIg6!==,/c/sI%/,og;/c/Q(g]!;6==Qco6AU!EwQQ(UU,;", "s]c%,%MgswoIkk;UM!QoQ/AA(c]I;sIo2%kQ(sUk=6o%M]oc(6EoscYsw2/gcoo6AIQ;=/EgkMwU(gMU62ks,o(ws(M/YwA%%=6o", "/(2cQ,2Ig;6sk/MswI=2/6]AQ262%c](!!U;%YUQ/cU(g;sg((2Qo(cMkAA=UY,(/%/,gI;=EQE!,6M=!6%AgQc2YUkcMk!];Q2o", "Us,oQQIMAk=MMkEQ;IgI,sE,,oQ6;sYQA=%!Q%AU!,o/IkAY%2,c,YA6Akw]EIQwk!UUA(kMI((Q!6/6=AQsYQ=,%A6(E];w]Ios", "kww22=,M/]2!k,=//Q/s((Uc!2kw!U%(sQ/QMwco!oAUwskM!(2QoE/(;gc/]/EoQcA%Ek%IIMIE],AUE(/EMk%6Q%wU%%c/EU%I", "ws6!;%wo,=2k/==IIQ(2IE,!k=A6s=6%M,AQ2;ocAgE%kwUI]%/g;U=AwYUcg((/2(;=s!,YQ;]U/E;gYo,EY,;(/6A=Q2,%(Yco", "wA(,/c]IEMkI;=!(;g]Eos/kwA%/6!g(kYQ!,o!E!;I2,wYA=I6gwMMk!EY%%oc]2,AcYUk/o!ocQ,MYwQEssUwcQ;kcg!sQwQwY", "o;,cUwsY(UowMo=gkgEwYIU,oA]o]%,]M/=wM;,6]Mo;6o2//]Mw2(]%E//Y!/=YA6,6/6AAUUws;]Is=MMUUQ2MA6MI(A;EQAsg", "Y6wM=(]U,Y(6Ao]EIQo,(/QEs=;62k2,;;;o!/22w2,=I;Ic2;,2c]%w]Y!=/2kkI/6Eok;gg6oc;%]]%IcEY];;wU%!2II6YQc=", ",ocss!gg(g/6==;o]6/]A=oQYA=]kY6M6;IIU;I;2(cM,A2(sE!AscsQIQk]sYY]Q2=Y]%kw=c/=,MkgEskIIUQ(IMkw,Mg6A!s%", "I%/,=kgkg,EAc]AQ2s,oM/QcE(s=6(YUI;6UgQ62kA(U=Ek!6skUwsU;;Qg;QsEg]EA=]oYQ2,2g,E!(U!U,wMw=k=kkA=6]IQIM", "AIQE/w%U%kQA]oks;wA2k(s(!I!Agk%w=/IMQ!Mk;/kQc,cM=/6;U,A6Ac!(6%,wUcYY2s%A(c]A=/,=wUQQE/(AwUkYQw,sUgMo", "2I;QQsYA%Q(s6A(Igc,=IcA26A/U!Uw%!;QIkIs]s,gU,A]gEg,k%E(2s,6Aco6,U6%kYMQEwIQ=As,]k!]QU%so!(2%sU=;g%w!", "Qwc/,A,wUkQQw2s/]/U/sI(2s,M(,M6Ak6(;2wgo%%kc%ggA%]owcM(Ek!QsUU]Uc%%YM/;Eos,IEIQcg(=;=Q=!;UQs/(II(g;,", "QEAMI(ww2gwws!;(s/M/cg62ck/Y%M6EwI=YQs(QoIUQ/;6QU2I(ckk,cg%,QE=wQ%o2EQ2YYoY6U!6(cAgk%E!I6%QgoQc/k,cM", "UYA(s%((Aw26%Y=A62U2g%,w,//;6UkQ%k/=,2]6g!22U=!2IUs,]MYc%];wI!Ms,M66oA=IwcI,2g=I;I6ME%wEYA,(sg(6ocw=", "]U%MkAAg;w2=k%(cY2/,cY2%]YkI6gwocMc]]AUgY6U=2,AQsgQ!s2,gQc!(c/IQYg6,M/II,Iks%2M=/,c]MkAUQ6o(%/EQ]E/=", "EkckQwoY(As/sw((QUIs%]/Y;(QsIIA(],!MUwQ]wEUoc(M!/IAAAIUkA,IwgUg2s;!;]QcMU!/k/kocYo(E=;,%!6w]!w%UIYQk", "Q]!M=U%/,/6(6c]%MUMcA,M]MkYQ2s!g;6MQsIA6Isk%gAs==s2,o(,M%EI,MU;o6]M/I(oA,cwEk=!6%k=!QEMEUI6Ag%Qs!Uw/", "MA]A%IgAUII62Y=IYA(,]gU2Ag(kw/Q/2sks,s]cg%Y!wE/(cY=o;A2!6A6/6kMw(]EE6]6gc]gk(/YgA(2I6Y;AogsckQMsUwgw", "M/%(Q,]Eo6kAUc2skcM((wQ6]ws]IY%AUU26QA=!6;A(2MYQ,gAQQ2I]/k%!gc/I;wAs!IU==/kk]2gk]Ysgocwg22wo%6oso6=c", ",,w(s%kYs/MIUw=YoAM;,]!UIU,/(6AsM%YMY=;%Y2Io];Uo(sYMIkw2s(6A!k==/IE/;Ms;=!(cowkI!E,6c!kc%MYQsI;I6c%c", "AQsEccA,!g26QEMssU!k;Y;I2I(wgoc,2c2gog/UIgEgQ;,M=YA%%YE]UIs6!6s%(2w%g6MgE!Ac6Qo=,!,ckA%kE6gEwcA;wcA]", "/w]gcIwgQ6%k%!=6]/,2IAM(kMQ(IQ2osE=;!;A66M,!,M2;U,=I/A;(6oU=;s%U!E/!6/2wcAs=oE;I6o!;YgAoo(,((;o%MI,Y", ",6IQgcA6/!U=(;gEM(6Uw=cQ2(!MMg62]k/M6kkck!;,w=,6o;E]o2QAQg6Qo(cMIgoE%s/;!E=66;wcA%II=22=kkEM2AUk,I;6", "2oEc!wQ;EE6Y/,wMk/MQI(QE%Y2s;%QUgEggEk%wU(sgcIc/,A(,;=%oosw!%]Ag6IM2/,,2owskcIoo6!Uk6cA6MkYQA;wQ]AIc", "skM]Is2AkIE]Q!6U,Mw;,]UEsU=w(Mo2A;]oQs!Y%]cEYEQ;2g%c/s2QAIQ6AUQ,(Q(%/Uoo!o2wQQg=]%;(w6A%,]s!]A(2(Ic!", "U,(oc=kEAIYscAQQo2%ssAU6]gQ6]A/E!MgkA;(Q=Y%kkc;6Y,%Y6A=(gs=cockQ!=ocAMkskw62;,Y;YYA62U2%A6Y(w%YcU!2Q", "w,sA,EUYA%Mk=Mk6(=IMQY2c=/(]Q!U=YEog,2YskUwo2IAAgccEMk]%Y!skYc=o!/;o!6]!22!!,%Y,]%!sEwcco;2,Y/MogEM/", "sUE]QIQA/Y]MwsUgEgc%,/(/IcYMk=I/!sM2;QQ]U!E;M;2!AYM/=EQYAQ!U6M6!ggIY2wwoscgswQ]6IMEw%ooso;A6oYAUwk6o", "sg]c!gsUYkM;IEAI;gg6oI(6kkMAEkIEw2E;s2],%k/,,]Ugo2!QAIU6A62,/s2k/%Q](=gk]E2=,!kksUo%!]c2E%;6AIYc]c!c", "c2sE]=%2sgs,]MIQ(MM6UcA(wcIkEc/(oc26]Y]ocwQccMMEw,6/=EsM;=,E%k%wIwQs]cIg(=%/c(w2c;kIEoA6!(o6A;c;kk,c", "M(g%oEIk=kwE%MM%cIwE%6w%]YM2gM6]s,kYk6UkMMwUsosI]/o;wMg,MI6cc%2=Q2o;cU/AE,g=c6%w,]%s,Y2kg6QIk/g(sAs(", "]oIwkEk,kkw]EkE,oEAwoo;gs=]/g;2gA]%wM6;Is;,2/=oMA=oEwAogQQME/,I66%UQY6cYcQEoM=owkk!AIEYc=YQo(U/(E6o(", "AsAM]gIgQc!!,s!cM]Q!gEAsww2!E%]%Eo2gY(s/(%//IcM!,s%wsQcU,M(kY6M]og6A,U!M!/]cE6(QQ],owM6wQ,AI6Y=csgMU", "cc6AU!;kgcQQUwUYgQ,M;sAU;==6M2!Q%k=2IQA;]M!(,%M];6%(]/wIA=kU]%gIgM22wM6Qkg;I(k!Y2!Ec2A,w=AgQcMoQUIM%", "sg=s!I;,c%MMA;g;=62;/UQ2wQE%EA;!c%EY2;==26%Q(M;6(]gk/gA=YQU2,,Y]Uko!sk=Y;QoEEI(]%2g(c!Mk!oc6]wcAEwo2", "k6sY;sc=c;IAQ]%Q6coEAs,cgkw(,;!2;Is=k;6==E%kIYI(I6=s]I2!o,oA!c]6]QEcM2A%2oc,(AQo,==wAsgccUw2kMw//;2g", ";kEYg2I;=A,kgE,!%w;2I,Uk=2,Q];w!I=I(kwQ/o,=YQQ;,;;k%62%oEAQ;=k=/6co/]U(2!%I2,=;;6A]6=U,U!(;oss6(!s!s", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "z", "I!QYQ]2MYsIE2=g,oE/oAgcM(2M/kA", "%Uko/Uk!%]s!,2=6!E%YAs2!2%Ywc=", "s=2;;6Qccs]YA2skY%=cMQQo/UYQ,I", "]cQ]=k=!gUQsM;UIkIg]c]E=EUskk2", "gw!(cAE=sMI%oYkkI/E/g]!oUwo]!U", "gA=,okYoU/U6Y!%Y(6!;w/sU=,]oEA", "=kM]ooo%]%A!g%]EA26,cAQQ/E(wQE", "w2,2]Is2/6ws=/Y]kY6w%A(Q6Mwko=", "swkw6!sU2!6Q,]o]ogM!(c,;2I%]=2", "YY!%EA/o%2gc]!U=Q2;!2sE(6E(c]s", "c]/s%(Qs,kAk]6,wI(UAEQ=EM,%Aw,", "2IA;=sQkwYA,]IQ;((YEA;c/ssk!cg", ",c2=,A6w%kwA6IsUsQE/Y2,6]Is(2w", "Y2gYc!6(,Ms=6IM=U,Y]kEcMsAs=kw", "%=Y;M;!,M(Qo,!A,=c/,coo6s=]ck!", "o,26]wY]Q!cY]%sE,wAEIg!,AAUgk=", "c!(I,M/wkQcw(,Ms]!MsI2EA,AsEQQ", "goc/IMEAk(Y,;,2=I6MAUw];%!U=sk", ",QsgA/6!UU,;=A6w;A%,6Y,26IgEMc", "s!I!sccAcM=cI=Ak=!(2wEccQ;owA;", "sc(s%Y/6(wwk2s!]g(cQIws,oE2YkY", "s6YA]M]wUI]/(,;,%=I6g6/A=(E,]g", "%oQ]2Uo(6EkIkMEcEw]Q6wQ=k!/2;E", "UkYI6k/2AsAUIU/,2/;/6]E!YMsEk!", "UcM!;k/I=I=;UQQc%A!,U]Y(,]oQoI", ";!;%,U]26cAg]%=(,2sIM;%]Y]UYUU", ",/IQMssUU=I,s%s==/%]Yo2EIgUE/g", ",U!2U6sM==I(sQAQs/(;,2=YoEQo((", "wMQE/Ag%!c;!g!k=gw=I6Y=EQ=Yo%s", "2s]A(2ws%(%6AYYw2%2==QQEAE!,M2", ";g=!Q6Qg2;Ig(QQYE2!;kwA(/s2s!(", "Uc;A/I;=/os!Mw%2IEAIYgQ,=,]Qs,", ",Ms=UIg6Ao(6c,6=,AEAY2kA%(AAsM", "gcg2UYAQs,;,g;]A=I/2M%Qcg!sQ/,", "sI6c(;,A;(2%,/U;!!g6=,MAsw%QEQ", "MEoE%U=,U!;Y(EoEYgIoU%6/QwUQ]Y", "(;kwIE,=gM]M,A=;IQ,2=MYMAQMUQo", "/Iok;6E2/E;Q(ow/gs,gsI%EEYI(MM", "/k/s(]26;%/UQYk(2Y](2sIw,g;E2A", "IQUYI!s=!(Mw=wAg6;!sQ;gg,=/,co", "/2]2!U!,!U2kgM/gk;AokMwYYAg=g;", ";k(AYgg;(Ig;QQ/M%os/!!%,6U2cU6", "2,wU6kM]Q(2(2]QEc2;QUo,c=kwoQA", "kEk=6oYgMEU=6QQ(s/(s6%YU,cI%/I", "I%2c]/U=%IkUkw%csgUkcMYQMk!2UI", "!wE!!]%EMkIA2=o2wYY/AEs%kkIQMk", "koEkY];A=!cQc]cwkwwg/]((;(AMA(", ";UYI2k;Iw!sAIEA,=]%w]2!=MYI(2M", "%,%wc2UAU,]c%Es,AI;QM(2AgE/U=M", "=%/okA!=;(%!Mk!M=!M6/(A(],,U!%", "kY2sk!EY]IU!6k/;,6/g/sQoQ;/Uws", "(]cUE%I2k]s(Ak];U6M,cU;YAgo;;6", "=6,;EQ(QAMUg%!,%AUc!w]cAosk2UA", "E;cAU];w2UskAUUkQ(/]%oYQoIcscU", "2]E/IUI2/6o,cYQo;g!6AMwAgsE]c%", "oY]A=!(%=%6]ocEoQ((UUgoo!(g(k(", "6ooMoggs!(wcwY(;=w%(sIYM%Y]o]g", "gE%g!(U!UYo2IUIEk%A!Qw,IEgYkwQ", "csgsw]AAo(E=!6]c(%YQM=M2I6,%M6", "Y2UUw;MUk;w]%w]Q,kM6/,cgUYUA,%", "A(6E!]6wMUgcQY;6,6M/2U!IE2Q,ko", "2Mo;,(,ck=Is%A=]!gc!,kosU(s;kg", "MEAwwsUQ2((Q%kEQ(c,,swU;w;Yo]U", "=kI2]c!gIIssg=Us=IsIUI6%wss(AI", "ckwIA,kcA2oc/2UoIo2E6oEM/Q=wIo", "MQ;M,!=26%]IgUEwAso(g,Asw=Y6wc", ",c%!]o2Y2,cw%(c!w!(;6/oc/,oE!g", "(o%g2o(UE2U/scQQQk6cYs;Ag22=kM", ";kYo;g6wA/U/gQc]]kM]%(E,U=((s%", "=/U=UkU/2sU!,%!gc/I%U=/;62gUAU", "gcAEA!w/I=;;6Q],/g;!U;AgEoccsQ", "UUwU/;%Ag%]/,%YQ=QE/6;26M=UQ;I", "!IwQEM]/g6%c;,cs2s/g,=Y2U%%k%k", "c!=,c266sYo2ws,oQ%;gs/,Qk!EA(o", "A%6=Ac2QME%wM=UQg;2IEY=g(YQck,", ";kc!UkY6s=,2U2;Uk!MMs!/Uk2,c,!", "gQAs=%(Qk=,I;A;6Y]2(E]E!]E%kcA", "A(E%MU!/sA2kYY%=]=(ow%2gEc=/co", "cY%wkw(;Ickw2;%Y2,Ac,,ko!s!,cA", "M==wMU!k,;Qk];(Y6IIM=o!w6Ew=/I", "cQ26Q;Ms2=%]s(cMkA%MQ2/QAM]],E", "MsY%wo;AU=Ic!2EUY,]/UUgY/UA=2=", ",%;/6/I2/U%/6kcY]E%gg6Yo(;MI2w", "s,gcYQ(s,]2kss!M(A(%!/s2oMUAQk", "QIAQkEM]!k(%/=c%QEIc%M%6A(2=!Q", "A6,U2UcU!UI(2I(//,%MY%wMAsg2!w", "]Q(%%QMw]=sk,%MY%!wMk!;A(Y(!w,", "g=gM(QQwUgcMgsA6/IUkk=/Es!soQ!", "6A,%cQQccwYw(o(%k!%g/wsMcko%U,", "o/6A=2I26EEc,!!;ggg(!EEoEkc/EQ", "%/UMw%%!YskEsUkA=,(M],oMoQYI]%", "AU=YEoo2]6Yosws,(,=Q=II6,%U(6]", "2]oA=IcQ%]6ggQcA6sA,U;IMwso(oA", "c]A]gI,M]/Mc(gY;,MsIk6M;U]A26E", "%M=s,o;k;!E,E2QkQ2I;YgsUoUw/;6", "((]ksQ;wggUY%QQcI(26o(QAI,wc62", "!Uwo%M/2w];wMY;M;,s,6kI;6AM;6s", "]M!;M=g6,;6Qg=Q(sgkg(cQkIMMs=I", "Ic!,Y,Aw]c%(E2(Ug;,A;Q!;kE]g(w", "w6/6/,IE]Y,Y%I/c6%]6Iog(6E(gM2", "A=/wQs=w62//2wQ%2Y=wgY;2gMUEw]", "!c!6Ew2/s!%AIQ;=k=IEMgs26YA2Yg", "(2;/YAUMAc%MIg=Igs((!;((Y2cUsk", "MQ]QEcA%Io%gI=]oQ(/c!s2M=!EAI%", "=,]k2ck//AY/6Y/6!EMokw2A%o%2%(", "kU,UU=oE/Q=sEEY=s=(ko/Qcg;!%AY", "coEs!%I%wc(%wQ(Aw=wQMw=/YsMko6", "Igg6]wkUUw=!U!s(s/UQ!/Y]UQs]Mc", "Aoc=//YM!gcAIgEs,%gc%;Q;!/(E,6", "gcU;2(;Us/!Qc;2=wkw]6(AA%kMsUU", "I,%],AUk(=,EMYQ!UYM;wU;,QIQQo6", "ggs,gAE%kMo%]A6sMME6kM]6Yc/(!s", "EU,IU(Is,w=Q6%/(EU,]wc(Ys=2U;6", "(g;k!sI!wkok](Iggg;2IsgkYA/QM]", "wAIU]EcgEA%k!gs=Ikw2U/=];;Q,]s", "o!goYo(M26oocM2II6c;QIo,A/%Y;(", "Qcc;Ic(62(U=%Yo/Mgw/U]EQMw%2(Y", "sU];QkU;Mwo](k=Y,g(2MMwYM/swgA", "QQo2(E/w=Ik!UoE2=IQ=/wUI(IU;gg", "22s!QE=]%/EUI;Uk%wIY]oQU]Qcs26", "6=gU,Qc]2owAUEII(!,g6M,o]Q/Icw", "!I;!ss6o]I,o%A(EUcY;,6;%Ukc=o%", "s=w=s%]sg2!,cMwcoc=!;sII/!M](;", "YwMccAI2c%wkYQQ,EY6,MMM=gQgUk;", "EA622EYowI]====U/M]A,wc(cAY(EI", "6Q,%gQgEkYA2,(6,;wsU===,,=EoEA", "QIgUI;g(2Uw],ooYMs!Eg,os!Uw]6A", "s,MwgQg;]oo2=k!!k=IE,w;kwk(MAU", "M,AcUIUA(Ik/o;!6wosAgQ2=/II;=A", "k%AIkIA6k/EMMc%((!,kIg,AU%/,%M", "gwkQE/Iwk=ws!=AYQ;w2U,EsE6gE(g", "Yo]cMw%!,MwIs==,cg!2MYQ,]/6MYg", "wkwk%22/;=wIgwIEMgQ/cY;cAs==6I", "2/6(;6Qoscwg,U2k2,]AI2YYM/kY%2", "Ik;cAIQ6g6,w;=IowQ/,6A%ksUQcIo", ",%E(cU!o]Yc,2;]McwwMY/;2o;!]2A", ",k6%/UM/oYc;so(62sAE/UY,g%kwo(", "c;;6gs2,/=EY;Iwk!U=gkUwM]EUwQg", "Uk=2wE!;6/QY%!g]/YoUw]Yos,Y%;M", "AQcsM6/!U2EQgo;g!kYM6g;wA;2=2Y", "kUMIE!6s=Ic2cQ=kI;!MY(6]A=AAIQ", "6]c;(Y,o26Y]c!s=;gQ/(QE/,o%/sc", "U2=I(w2;Is,!/2,M/I%;IQoc(c2=I(", "sEUM(Y,%;wM]EcwY!6g=;;]2wQow2w", "!,=kkUw((]ccMYwAcYcg2sw]A!,6EY", "g(A(UkU,,/sE/YA=Mg](U,c]IUkk,U", "s2(/co];6I%sk,(%%UYwEM=,YYEw;o", "AUI6w/IMIcM]Uo6%6](gw]o;Y]E2=I", "IQ;6/sg6MI%IUI]Uk!(6McI(Ukg%(Q", "E(2IoIk;66MYw]M;sAU(sQwUkMI%Y;", "=,6I=cUYU6cQc/(;=!E%gc,,cMM6=,", "M/,o;g6/]AoYg(2!g;s;%cA=(((%/=", "o(A/MYcgIQsIoQ=!6oQo%/gI6gQ]Ug", "wQ/Ys!,6/Y/oEY%]2k=]MgskIgEkEo", ";6M,M==6c(c!M/g/s,]/%sc=,%]Y/o", "g2=IgE(==wwcU(Q2w%kQ2EswA(EY=;", "6c]%wog6M/w=;k]koYkw2YAo;QI(wE", "gc/],%/c/kcEo%,oQ(c!s]EgokU2A=", "scQ2,/;/EM!Q/soE/E/M];AIc!==Y/", "]AsYMMo6=k](Uwgg2gsI(k=o;YUQQ!", "(UI2oo22=2Y]Ekow,Ag2o,M/MY6QQ%", ",M=EM/!gU6Ag/,o2wM/%AMU6o!6sg;", "/%Ag;!2M=/,,;,kgk=gsMk=kY;6/YQ", "wMI;UMwAE;6oo(2wQEo]Qo%I/QM2Qc", "/!6]QQ,Uk=MYo]c,A!Q;gg;;MQwUkM", "/EM/Qo!U2I=Ic!EMQ%!/E]okk,Y,Q!", "UEo%,kY6/EY%A=Y,/g/gwA;M!E6k!c", ",MEM,6Y%]=Eog(=,cMMYc=,]%so!gM", "!!2QAIsU,/s6A(]wcw%wQcY=6//,6=", "Y=YE=Uw!sk6/g,g!g==ME,M/2IIkEY", "w%MgY;/]Q]=,IM2]MI(g(;I;s/,2wc", "26o,%M%sog];ggc(IYcgcsk(ow%(UM", "QI((sAkokk=!(6]!;Q%2MQ6/]2/IsU", "www]=gc!62cM]Qk]6];/EEQIEAgw(,", "6]o,AI=AU%(EM!;=Uk/ckQk=U=Y=wg", "g=Y%]M%YIggI]cko],]!=];!sU2%YA", "/g!Ms%/]I]6sUwUgsgUswQ%]2wM=w;", "!oU%/62c/IkEIE(]%]Asgw%=sgQIU]", "E]kkYA6A(;kMY2%kw!=IgME%AEAw(Q", "=!]M=wAQEcIM]sQo2Q6g=;;,,]k=26", ";]oc==E(6MEgA/UUYg=E!EIE!kUYc%", "E]A2g=wosYkcYccY]A%(A6Mw,(sIgs", "kUU,/EE26w%%]wQE%2=,,U(QwQg6kQ", ",oIY/A%ock=!6YI%/sQo%62k=I2II6", "]Iwcwsg;A%wo;!/,%;!gQc;;MYwogE", "(E;UwUwo(g]cM%k(;wQ%I!Ug;EQ,Mo", "cw,g6kMg!6QEg=o/scE!6g]EkYs=]c", "=o!,=6oM,],A(Q2Qo6c!;=2sU=MYkM", "%AE]c%kcMM6s](%QcM=%!k,AooE%MU", ",!Qo==(]I(cA=AoE;=,!o%U]2=];Iw", "!I626YQgg;]=IU,o]A2c=]o];Qoc,,", "]U,oc2];6%UE,/;!!]6!;(gQk/c]o2", "]AgE/=kIcY,%=cwQQQ;sco=wMkg];A", "s(s6gQ]!QsM/=;(%A%!sk==wkkAg%k", "o6Ysg/6%kM2skc!M,%,/UI];2!!k;%", "(!]ss;6%=%,c=]2,cwMwE,c/E!E%!c", ";6wAUwUQ(kIQw%oEM=s6UU;6(;Uk(E", "Q%IYcYk!o%;o2%cA,]%,AIUg;=I;6s", "IE/6k(Q]2=,2!goco]sAkYc;=Ig6Mw", "sg]c]AkI(MY//g6AM=c/EoMA]Ekk26", "226=wsE,2IE/U%wk;/]QM/kMsIcEkg", "co2YM6EM;=!6%(6/6Y=sYoM//]]A6A", "UIU,2(M=YMw6YgQo,2os!,o%EQ;(g6", "wIUUUA=2Y(kY;skEk==M,s=YAI6/!2", "k!62/I6YEMw=,UY,;(,2(Y%Y]Q6w,,", "!(;c/AUk/,c2;2w%wEAc2w2Y!6sc%,", "%/;==wcgkY,M/Qow!,I66;Qs2gsI6c", "U,Ec%=YI%Y,2I6k/2,]Y2%k!UgEA62", "YY6%g(M]22/(QoY%U]=w]cUUY]k!I;", "kAAc]cMA(]MY;IswQ]Ag]kI=gss,o%", "6%Q;(;EoEw%/]QoE266/MwcY,o/2wU", "gMEIgQk6/,;6s,,oo=AIEM/2UEMEI6", "2I]I=w=]cAsoso2Us6A,gc6QQcoQE%", ",;=,6Y/wUII/%w(ow]Yoc%MEw;/%g!", "swQ!g;U,AUgQ/2oo;(Is,Q,%sU!wI/", "2g/gkk/U=/AoQ=]AQYUY=/YMw];EM2", "w2,6AE%so6M;!]2,oEAUU,MAIg=!;w", "MYcQ/%]2Uwkso2/Q/(!UUccA]2c%(,", "oII(g;I6k!EY%s=/s,2w(QQ2Uo!kM=", ";I=EY]sMkcs;gEQ2=owQc%k/Qg/EQs", "M,I;g;o2=M6sUY]2I]Q(UoA,ck(Ek;", "]U;6Ao;=IMs!gwgE,M,!%Y%2(66(;6", "wc=,QE/QcI/AYE%M=(A!gs=%;Y22EY", "gs!Es]6M6Mc!M((!g%!,A=s!Ug6gg,", "g2/]%]=w,M26cc!Qs!%]AE]c];g;6M", ",6;cQ26,A;QQ6YcYQ6E%sU=o;E=oEw", "wQ!EY(oM;;w=soAUk==QQI=QY;ckMk", "MUMwAQow]2((/=M]]/QUY!/Y;/Ys;=", "(g=o,%%k](o6o(gM!I2I]I;/%sQw,M", "kY,okkg(62%Q(wA6(2(6UkQgQ!o,]M", "k6", "Qc", ",(", "c/", "=s", "=w", "Y6", "Mk", "Is", "AU", "(=", "(E", "kY", "AY", "Q(", ";;", "(s", "=!", "!/", "Is", ",/", ",o", "Q!", "!g", "wY", "6s", "!U", "!c", "/I", "Eo", "gc", "w/", "!I", "=/", "I;", "o]", "UM", "%E", "%6", "k/", "%Y", "6g", "(;", "]A", "kw", "M(", "]k", "6Q", "QQ", "Yw", "Ag", "UI", "Y2", "g]", "EI", ";U", "Ik", "wA", "(%", "Ak", "cA", "E,", "oA", "Y6", "(6", "o/", "k/", "U!", "66", "/U", "=Y", "wc", "ME", "(s", "c!", "6g", "wM", "cA", "(U", "o(", "(/", "o%", "!!", "I;", "sE", "c2", "2/", "=(", "=w", ";,", "Q,", "QQ", "EI", "(I", "QI", ",%", "]c", "2,", "k/", "/2", "Q(", "MY", "UY", "oM", "Y;", "]Q", "(k", "Yc", "c!", "=/", "k;", "wc", "gc", "U2", "M=", ";s", "sY", ",c", "6/", "Ek", "I(", "ME", "I(", "A(", "g=", "IM", "sE", ",Q", "oQ", "]w", "=s", "Ak", "/g", "E,", "kg", "!!", "!g", "(]", "!g", "cM", "]c", "==", "wY", ",A", "M;", "w%", ",Y", "cQ", "kM", "gA", "Y;", "o,", ";%", "MM", "6o", "=Y", "IY", "=Y", ",/", "2w", "Uc", "!,", "%w", "/I", "%6", "%A", "wA", "kI", "M6", "A(", "/!", "(2", "s;", "/s", "I=", "=E", "oc", "Mk", "g=", "/6", "6=", "g=", "]c", "kk", "2E", "UA", "(s", "Yk", ";w", "!6", "M;", "2Q", "2w", ",%", ";o", "U6", "62", "s!", "Y]", ",c", "EY", "(k", "!E", "/E", "]s", "]A", ";,", "wQ", "AU", "]w", "=!", "=I", "EE", "!]", "Is", "Ec", "/k", "2g", ";;", "6;", "o;", "2Q", "M/", "=%", "=Y", "!o", "MM", "oc", "Uc", "AU", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "RIGHT", "ckgoUI]AIg%!]%M", "I,%(cc=/Y%YQ6,o", "MwsUUo%E]g6E/UY", "UQgQ]6U/;==Yk=]", "%!AIg=ckc%og]Q;", "%%M/(s=QEAA/w2g", "cYcQ(wMc!cgQ(gs", ";c/sQQQ;=,cYg;;", "]!%]!s;=MYM=s(U", "s/AE,=wQYQMIY2]", "wA2=EA%g(/gQkYA", "w=;,]%%/gMgAYA=", "2QsM/IEkYM2gE%Y", "(c!26s;gE%YoEA(", "Iw2UcM,ww6wU626", "=IswQ(=%A/IEY6Y", "wkI/YUwY,E=2cA(", "%26c/,22,gEA2]I", "o;=!;gE/;I%wg6/", "]k/A=YA2]%2I6%6", "]Q==Eg!,2YcQAYc", "]6o;;,kY;!6(c]w", ",2gQ6cMgQsQ%IE!", "oYo/!6!;!gg]gME", "oQcAUs!!sw2!s==", "k!UEYcYQQEgMY6o", "2M/gc%Q=E!%A;6Y", "2,AsYoQ%6kok;I6", "Q]g,2I(2U;wQ(2Q", "w6Q;I!UUYgU/YQ;", "YU]Ago%IMs%]cI=", "oQU;2Q2k=cYck2g", "o]=MYQE,kcY/%]/", "%Q!6Yg/(2=!A(=s", "]=w6IM62%6Igs;]", "g((,(2gwEY!;c!,", "/E%]/]sk;%6(s6o", "s=(%Uo2;=s!wQg2", "6Y2==IA,%cE]oQQ", "2;ko/sQkggs2U2g", "6cQs%c,wU=IQ(E(", "kYI;o2(s2!]w%IE", "wI]k!6!%AgIkAsw", "((%Mw!=Iw6Ao;==", "cosg;U(kY!;]=2g", ";!,AA,/6=/o6A%U", "(2,%2,o26/=w%(I", "k,o/s6o!g//g6w6", "IIE]6!(o;=sIMwY", "Qo2(%=,k%gUc!Qc", "(/s=%(c]!w]cEQE", "MA6kIwg;,%/kYcs", "QgYU,Yc(k%AMM2%", "/EgEkI2kM=/Q/]A", "wA,cM2g;=,,A%;=", "=/QEMsQc6cM6],k", "]2(c(oEgggc=,Qk", "%(E!AA(/EM,,6]%", "!s!wAIY2/6ko,/6", "(ckY]Mk=,sc6/2s", ",sw,AIEkw=MIo;w", "w]sUYAsE,!oY/6o", "g(;]]%((M6k=/c!", "ck/sQYIIs!//E%]", "!Y]/EQIEk;,I!,]", "g;Uc/sg;Q26oEM!", "2QEAQw]]Mw=2MkI", "MoE!swc=o;]E;,w", "kYo(,2Ekk]A(E==", "QMkMk6(A/U]csgc", "AQ;I;s,Ask!w;Is", "o,]kcM/o;26E66E", ";=AUIUY];,UI2AI", "wkM/6k;,c]U,Uk%", ",owMEYA(wsUck%]", "kY6AY6=,ck%IsUw", "%;;wMwc(U!%%2sw", "UsIEQ2,]c2IQwQg", "w/E]o(c(sE;2A=g", "gwM]Q2,M(swQk=k", "wQswgM!UM!Us;!I", "g6oE]!o2E!oIIU!", "ooUc!Ug;=IgEEcI", "]I!62Ycg(%IQ;Yc", "k=;g%]koUMYUw%=", "UgYk6cA=U=okAAk", "g;IEE/!k!k/;6gs", "%w!c=/%YEI=(Ek6", "A%/E6YoE]YE2QUk", "=M!;M!g;QskI62c", ",Mkc]wM/IU;!;MQ", ",,Mo,wM;!,;U;oY", "wA(EM;A(Y%M](I=", "w];Q!;6%!AsIc/;", ",]M=/woM%/kA2U=", "MsQ%=YoY;,]EM==", "E,](EU!;6%!ck;w", "U]AUcEE,(sM=E%w", "cQAYoQYk!EUk2Uk", ";g2w2Qg6!]%;MAE", ",w2AU%gYQQ(E]2=", "EcAwIE]]c/UggIw", "McU2o=(%kEU(6/o", "g;;U,kYcscUM6%s", "cIQ%6YIA=oEEEQ(", "(,YkkEM6=(2Y2w;", "w2,=%wgcgEYUUIk", "cU!wQ(=IEY2QscY", ",26M2UwAUI=g6/!", "%QkU6]o=c]Q=A=,", "2IEQ/=sM2!%!!;=", "%U=Yk=c2,A,UY6/", "/UYMYA6U(Y2=;6E", "/UUo%A6/g/,Qk!%", ";%Mk;]A,o2gQ,/Y", "=k!(2wc%/U,U%EI", "!Q6,MYQ,M6AoMQU", "],;Ug6(swk,sQQo", "kQ(sI]wYswQcsU!", "w2wAU]g%YAk]6w]", "Aw!kY/AoMQcgsw%", ";=,Aw%EQ!wAU/sk", "]w]M==kg;QQ%A(c", "swc66=g;]2c;wI=", "Y%ws6]=Qg!62g%o", "];/E!/(6,]g,s!/", "k=!k/MEgM=cssUU", "M,c]!/(swQ,,UQc", "gI;6YQ(o2!;EMwc", "Eo;/%;/,,]ks!,g", "%!%gIg6Y,!c/,%6", "(2Qgc(//(%c%;%o", ";Y]%Ek]A%!coEo%", "wMw=,o%QcEcM6M,", "cYU6;Q%,;,%22%%", "oo,I62IQ!]=%A=s", "2YwE2ckA/]=!;UY", "!EUw!%A==k!EoA2", "oYM6]],sQAwE/2M", ",;6=Qow/E;k=(gw", "(,c/I(;gs!=,AI2", "AA6UIg6Q/U;s%2/", "ocM/I(A(sU%c!,A", "](kcIg6c%k(s2,2", ";I]oocUg2o=I!Q2", "%s;IY6sw(A/kwQ6", "]cI26!k(Q!UY=]k", "U,MwAU6%sE(ssg/", "c;UwcAEUw!U%sAs", "/(s/(;IsQ,(;U((", "cIw/MYs]EU=wA;k", "6M/(,2EA!2,;E!,", "Is;2c6AIs(/%]I2", ";;2o=(s,=](,c;U", "kgcYQEU,]6IckEM", "%cMM!]c!6Y2Isk=", "]QQEA!E,w]coocs", "AQ2QYw!;=M=k]6%", "]Qg6YUo]%AQ=!kU", "U%=oQ,M,6/sksoM", "/,/s(!];6QE6ccU", "oMMIQU,IE,6MwAU", "6o;!,s]A6A%A2]Q", ";]/Uk,U2osMU]!,", "skw;g/s=U!s;](I", "s(c2g!,=Mwoo%2k", "o((E/s=UIs;gYo;", "2],cY%=c!k!U(!o", "EksgEQQ=(]/M/!s", "swo,]2UYcM/(E]M", "Qoc!oMw%Ao2AgE,", "wg(YE,/U/I6!E/I", "k=II==IQ,/oQQYs", ";!]Q]o%YQ;=skIQ", "]A(YE;g2Eck!k2!", "%woE;,M]s,]%Y,Q", "6UAc==wos%MYso]", "E2kE2%]E=!;!gwk", "YAoo2IE2o(;swUg", "QkQcME/EoEoskk=", "YA(6ME2gQ%kM/E]", "YA;s!;,Qc];Iw,I", "(UkY2]2QQkUUYow", "22/o2=/QM/2c;Q%", "U=A6]AEskc2Uo2E", "/(Uk]2UQ2IsI!6Q", "!!Yk!skIUUw!E%U", "Q2AY]%]%(%AgcI,", "I%,;gwIEw%MYk!g", "2kg(MEQo(6=6gsU", ";=wswg%IQ(6ogMA", "U,Y(6kk(QcA!6;!", "g%!,2EI!]]AUU,2", "]s=wk/k/Ag(EU=k", "I6o=!gE2,A,Y=,c", "MkQ=wM;c6c%Igs,", "kg];Y(((2/%=,6U", "ko/o6g=//c!UA6=", "c(2w%]o%M!A,I/,", "MUQgQ==!2%EEwM/", "wcwM]%2wMwk((62", "w2soQoc%%kQMMk/", "o=kY6A!kIAcMwM=", ";=kkk=g;Ig;E2kQ", "Ekws2;;]oAc26];", "EsU62IsM=w=,2Uc", "IMUM]EAI,]%(!IU", "%I,6(;=!Y]%,gY;", "s/!EkAk%MQEkog;", "E%;6Qk!o2o(;2AY", "AogUIMcQoYUEY=,", "%],/k/=,==w/Q]%", "==Q/](g%]o/U;U,", "oEMY!gwY;woEMok", "]!g%!/swk=]=!UU", "=Uc!%6w=6Ag,o%s", "QcY!E]/U/Us!IsI", "g/6g2=YQEY;YMUE", "==/I!Mg,,Y!EMY2", "k=M,2IQ%,E/w(w=", "gI/UkMM;6%MYsQ6", "U,Uw;!o!s%YcEoM", ";QMYUM!(]!(6=!g", "A6A,=IE%cAIck!k", "%c2Q(IgYEAI/A,c", "2gU6U!;wks==;QI", ";;A,oA]%%sco;g6", "g,c]E/Mk!;Ykw%s", "/UM(Y=/M!g,MM!M", "(//(%6EgQ;UwQ26", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS"};
        ByteBuffer stringLengthBuffer = ByteBuffer.allocate(2 * 230 * 10);
        int totalLength = 0;
        List<ByteBuffer>[] byteBufferList = new List[10];
//        for (String[] datum : data) {
//            System.out.println(datum.length);
//            for (String string : datum) {
//                totalLength+=string.length();
//                stringList.add(ByteBuffer.wrap(string.getBytes()));
//            }
//        }
//        ByteBuffer byteBuffer = ByteBuffer.allocate(totalLength);
//        for (String[] datum : data) {
//            for (String string : datum) {
//                byteBuffer.put(string.getBytes());
//            }
//        }
        for (String s : array) {
            final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            stringList.add(ByteBuffer.wrap(bytes));
        }
        for (int i = 0; i < 10; i++) {
            byteBufferList[i] = stringList.subList(i * 230, (i + 1) * 230);
            int length = 0;
            for (ByteBuffer byteBuffer : byteBufferList[i]) {
                System.out.println(new String(byteBuffer.array()));
                length += byteBuffer.remaining();
            }
            System.out.println(length);
        }
        // single compress column 2
        List<ByteBuffer> byteBuffers1 = stringList.subList(0, 230);
        int length1 = 0;
        for (ByteBuffer byteBuffer : byteBuffers1) {
            length1 += byteBuffer.remaining();
        }
        ByteBuffer allocate1 = ByteBuffer.allocate(length1);
        for (ByteBuffer byteBuffer : byteBuffers1) {
            allocate1.put(byteBuffer.array());
        }
        byte[] compress1 = Zstd.compress(allocate1.array(), 3);
//        // single compress column 2
//        List<ByteBuffer> byteBuffers2 = stringList.subList(230, 460);
//        int length2 = 0;
//        for (ByteBuffer byteBuffer : byteBuffers2) {
//            length2 += byteBuffer.remaining();
//        }
//        ByteBuffer allocate2 = ByteBuffer.allocate(length2);
//        for (ByteBuffer byteBuffer : byteBuffers2) {
//            allocate2.put(byteBuffer.array());
//        }
//        byte[] compress2 = Zstd.compress(allocate2.array(), 3);
        // single compress column 3
        List<ByteBuffer> byteBuffers3 = stringList.subList(2 * 230, 3 * 230);
        int length3 = 0;
        for (ByteBuffer byteBuffer : byteBuffers3) {
            length3 += byteBuffer.remaining();
        }
        ByteBuffer allocate3 = ByteBuffer.allocate(length3);
        for (ByteBuffer byteBuffer : byteBuffers3) {
            allocate3.put(byteBuffer.array());
        }
//        LZ4Factory factory = LZ4Factory.fastestJavaInstance();
//        LZ4Compressor lz4Compressor = factory.highCompressor(6);
//        byte[] compress3 = Zstd.compress(allocate3.array());
//        compress3 = Zstd.compress(compress3,3);
        // single compress column 4
        List<ByteBuffer> byteBuffers4 = stringList.subList(690, 690 + 230);
        int length4 = 0;
        for (ByteBuffer byteBuffer : byteBuffers4) {
            length4 += byteBuffer.remaining();
        }
        ByteBuffer allocate4 = ByteBuffer.allocate(length4);
        for (ByteBuffer byteBuffer : byteBuffers4) {
            allocate4.put(byteBuffer.array());
        }
        byte[] compress4 = Zstd.compress(allocate4.array(), 3);

        // single compress column 4
        List<ByteBuffer> byteBuffers6 = stringList.subList(230 * 5, 230 * 6);
        int length6 = 0;
        for (ByteBuffer byteBuffer : byteBuffers6) {
            length6 += byteBuffer.remaining();
        }
        ByteBuffer allocate6 = ByteBuffer.allocate(length6);
        for (ByteBuffer byteBuffer : byteBuffers6) {
            allocate6.put(byteBuffer.array());
        }
        byte[] compress6 = Zstd.compress(allocate6.array(), 3);


//        // single compress column 4
//        List<ByteBuffer> byteBuffers10 = stringList.subList(230 * 9, 230 * 10);
//        int length10 = 0;
//        for (ByteBuffer byteBuffer : byteBuffers10) {
//            length10 += byteBuffer.remaining();
//        }
//        ByteBuffer allocate10 = ByteBuffer.allocate(length10);
//        for (ByteBuffer byteBuffer : byteBuffers10) {
//            allocate10.put(byteBuffer.array());
//        }
//        byte[] compress10 = Zstd.compress(allocate10.array(), 3);


        int[][] segmentsToRemove = {
//                {230 * 9, 230 * 10},
//                {230 * 5, 230 * 6},
//                {230 * 3, 230 * 4},
//                {230 * 2, 230 * 3},
//                {230 * 1, 230 * 2},
        };
        for (int i = 0; i < segmentsToRemove.length; i++) {
            stringList.subList(segmentsToRemove[i][0], segmentsToRemove[i][1]).clear();
        }
        for (ByteBuffer byteBuffer : stringList) {
            totalLength += byteBuffer.remaining();
        }
        for (int i = 0; i < 10 - 4; i++) {
            byteBufferList[i] = stringList.subList(i * 230, (i + 1) * 230);
            int length = 0;
            for (ByteBuffer byteBuffer : byteBufferList[i]) {
                length += byteBuffer.remaining();
            }
            System.out.println(length);
        }
        final ByteBuffer allocate = ByteBuffer.allocate(totalLength);
        short[] shorts = null;
        CompressResult compressResult = compress1(null, 230);
        byte[] compress = compressResult.compressedData;
        shorts = compressResult.stringLengthArray;
        byte[] bytes2 = IntCompress.compressShort(shorts, 230);
        System.out.println("compress1 rate single : " + (1.0 * compress1.length) / (length1));
//        System.out.println("compress3 rate single : " + (1.0 * compress3.length) / (length3));
        System.out.println("compress4 rate single : " + (1.0 * compress4.length) / (length4));
        System.out.println("compress6 rate single : " + (1.0 * compress6.length) / (length6));
//        System.out.println("compress10 rate single : " + (1.0 * compress10.length) / (length10));
        System.out.println("compressAll rate : " + (1.0 * compress.length + bytes2.length) / (totalLength));
//        System.out.println("compress rate : " + (1.0 * compress.length + compress6.length + compress3.length + compress4.length + bytes2.length) / (totalLength + length6 + length3 + length4));
//        ByteBuffer wrap = ByteBuffer.wrap(compress);
//        int tl = wrap.getInt();
//        byte[] bytes1 = new byte[compress.length-4];
//        wrap.get(bytes1);
//        for (short aShort : shorts) {
//            stringLengthBuffer.putShort(aShort);
//        }
//        List<ByteBuffer> decompressedList = decompress1(bytes1,stringLengthBuffer,160,tl);
//        for (int i = 0; i < stringList.size(); i++) {
//            ByteBuffer original = stringList.get(i);
//            ByteBuffer decompressed = decompressedList.get(i);
//            boolean eq = Arrays.equals(original.array(),decompressed.array());
//            if(!eq){
//                System.out.println("not equal");
//            }
//        }
//        ByteBuffer wrap1 = ByteBuffer.wrap(bytes2);
//        int tl1 = wrap1.getInt();
//        byte[] bytes = new byte[bytes2.length-4];
//        wrap1.get(bytes,0,bytes.length);
//        short[] shorts1 = IntCompress.decompressShort(bytes, 160, tl1);
//        System.out.println(Arrays.equals(shorts1,shorts));
//        System.out.println(bytes2.length+"B");
//        System.out.println("compress rate : " + (1.0*compress.length+bytes2.length)/totalLength );
//        System.out.println("brotli compress rate : " + (1.0*compress1.length+bytes2.length)/bytes.length +"use time : " + (brotli-start));

        System.out.println(Zstd.defaultCompressionLevel());
        // 使用compress1函数压缩
//        byte[] compressedData = compress1(stringList, valueSize);
//
//        // 使用decompress1函数解压
//        ArrayList<ByteBuffer> decompressedData = decompress1(compressedData, valueSize);

        // 校验数据
    }
}
