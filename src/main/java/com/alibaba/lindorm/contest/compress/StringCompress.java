package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.util.StaticsUtil;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdDictTrainer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
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
    public static byte[] dictionary = "SUCCESS".getBytes();

    public static DeflaterCompress deflaterCompress = new DeflaterCompress();

    public static volatile ZstdDictCompress zstdDictCompress;

    public static ZstdDictDecompress zstdDictDecompress;


    public static ZstdDictTrainer zstdDictTrainer = new ZstdDictTrainer(256 * 1024 * 1024, 1024*1024 * 1024);

    public static AtomicLong COMPRESS_COUNT = new AtomicLong(0);


    public static byte USING_DIRECTORY = 1;

    public static byte NOT_USING_DIRECTORY = 2;

    public static boolean USING_MAP_COMPRESS = true;

    public static boolean NOT_USING_MAP_COMPRESS = false;

    public static int UpperBoundByte(int valueSize){
        return ((valueSize+7)/8);
    }

    public static byte[] compress1(List<ByteBuffer> stringList,int valueSize){
        int length = stringList.size();
        int start = 0;
        int total = 0;
        ArrayList<byte[]> arrayList = new ArrayList<>();
        BitSet compressBitSet = BitSet.valueOf(new byte[2]);
        compressBitSet.set(15);
        int index = 0;
        while (start < length){
            List<ByteBuffer> byteBuffers = stringList.subList(start, start + valueSize);
            Map<String,Integer> set = new HashMap<>();
            int count = 0;
            int totalLength = 0;
            boolean isUseMap = true;
            for (ByteBuffer byteBuffer : byteBuffers) {
                totalLength += byteBuffer.remaining();
                if(!set.containsKey(new String(byteBuffer.array()))){
                    set.put(new String(byteBuffer.array()),count);
                    count++;
                }
                if(set.size()>2){
                    isUseMap = false;
                }
            }
            if(isUseMap){
                // putDict
                StaticsUtil.MAP_COMPRESS_TIME.addAndGet(1);
                int dictLength = 0;
                for (String bytes : set.keySet()) {
                    dictLength +=bytes.length();
                }
                ByteBuffer compress = ByteBuffer.allocate(dictLength + 2*2 + UpperBoundByte(valueSize));
                compressBitSet.set(index);
                for(int i=0;i<2;i++){
                    boolean isExist = false;
                    for (String bytes : set.keySet()) {
                        if(set.get(bytes) == i) {
                            compress.putShort((short) bytes.length());
                            compress.put(bytes.getBytes());
                            isExist = true;
                            break;
                        }
                    }
                    if(!isExist)compress.putShort((short) 0);
                }
                // 写字典 id -> string
                // 写string -> id
                BitSet bitSet = new BitSet(UpperBoundByte(valueSize));
                for(int i=0;i<byteBuffers.size();i++){
                    Integer i1 = set.get(new String(byteBuffers.get(i).array()));
                    if(i1==1){
                        bitSet.set(i);
                    }
                }
                byte[] byteArray = bitSet.toByteArray();
                compress.put(byteArray);
                total += compress.array().length;
                arrayList.add(compress.array());
            }else{
                ByteBuffer allocate = ByteBuffer.allocate( totalLength);
                for (ByteBuffer byteBuffer : byteBuffers) {
                    allocate.put(byteBuffer.array());
                }
                total += allocate.array().length;
                arrayList.add(allocate.array());
            }
            index++;
            start+=valueSize;
        }
        ByteBuffer allocate = ByteBuffer.allocate(2+total);
//        System.out.println(Arrays.toString(compressBitSet.toString().getBytes()));
        allocate.put(compressBitSet.toByteArray());
        for (byte[] aByte : arrayList) {
            allocate.put(aByte);
        }
        byte[] compress = Zstd.compress(allocate.array(),12);
//        compress = gzipCompress.compress(compress);
        ByteBuffer res = ByteBuffer.allocate(4+compress.length);
        res.putInt(2+total);
        res.put(compress);
        return res.array();
    }

    public static ArrayList<ByteBuffer> decompress1(byte[] bytes,ByteBuffer stringLengthBuffer,int valueSize,int totalLength){
//        bytes = gzipCompress.deCompress(bytes);
        stringLengthBuffer.flip();
        byte[] decompress1 = Zstd.decompress(bytes, totalLength);
        ByteBuffer wrap = ByteBuffer.wrap(decompress1);
        ArrayList<ByteBuffer> byteBuffers = new ArrayList<>();
        byte[] compressType = new byte[2];
        wrap.get(compressType,0,2);
        BitSet compressTypeBitSet = BitSet.valueOf(compressType);
        int index = 0;
        while (wrap.hasRemaining()){
            boolean b = compressTypeBitSet.get(index);
            if(b == USING_MAP_COMPRESS){
                ArrayList<byte[]> arrayList = new ArrayList<>();
                for(int i=0;i<2;i++){
                    short anInt1 = wrap.getShort();
                    byte[] dict = new byte[anInt1];
                    wrap.get(dict,0,dict.length);
                    arrayList.add(dict);
                }
                byte[] values = new byte[UpperBoundByte(valueSize)];
                wrap.get(values,0,UpperBoundByte(valueSize));
                BitSet bitSet = BitSet.valueOf(values);
                for(int i=0;i<valueSize;i++){
                    stringLengthBuffer.getShort();
//                    byteBuffers.add(ByteBuffer.wrap(arrayList.get(0)));
                    boolean bi = bitSet.get(i);
                    if(bi){
                        byte[] bytes1 = arrayList.get(1);
                        byteBuffers.add(ByteBuffer.wrap(bytes1));
                    }else{
                        byte[] bytes1 = arrayList.get(0);
                        byteBuffers.add(ByteBuffer.wrap(bytes1));
                    }
                }
            }else{
                for(int i=0;i<valueSize;i++){
                    int size = stringLengthBuffer.getShort();
                    byte[] bytes1 = new byte[size];
                    wrap.get(bytes1,0,size);
                    ByteBuffer wrap1 = ByteBuffer.wrap(bytes1);
                    byteBuffers.add(wrap1);
                }
            }
            index++;
        }
       return byteBuffers;
    }


    public static byte[] compress(byte[] bytes) throws IOException {

        return Zstd.compress(bytes,12);
    }

    public static byte[] deCompress(byte[] bytes, int valueSize) {
            return Zstd.decompress(bytes,valueSize);
    }

    static {
        dataString1 = new String[]{"e", "\\", "a", "a", "8", "O", "C", "y", "m", "W", "K", " &", "#", "e", "\\", "4", "\\", "'", "[", "q", "m", "G", "C", "\\", "i", "K", "i", "'", "q", "'", "i", "O", "&", "m", "#", "m", "a", "a", "q", "u", "8", "S", "0", "O", "'", "m", ".", "u", "S", "u", "S", "e", "S", "4", "G", ".", "K", "\\", "u", "W", "y", "u", "e", "&", "K", "-", ".", "S", "i", "&", "8", "&", "C", "0", "&", "'", "-", "0", "4", "i", "y", "0", "O", "8", "m", ".", "m", "a", "'", "4", "G", "[", "i", "'", "C", "8", "-", "C", ".", "#", "y", "\\", "O", "S", "-", "4", ".", "q", "i", "W", "8", "W", "-", "q", "a", "i", "e", "a", "a", "\\", "#", "8", "K", "e", "-", "i", "e", "a", "u", "G", "0", "a", "W", "a", "0", "0", "y", "K", "m", "i", "u", "m", "[", ".", "a", "a", "G", "i", "'", "4", "u", "e", "u", "q", "u", "#", "C", "i", "4", "-", "-1",
                "0", "0", "1", "0", "0", "0", "0", "1", "-1", "0", "-1", "1", "1", "0", "0", "-1", "-1", "1", "0", "0", "1", "1", "0", "1", "0", "-1", "-1", "-1", "-1", "0", "-1", "0", "0", "-1", "0", "-1", "1", "-1", "1", "1", "0", "1", "1", "-1", "-1", "-1", "0", "1", "0", "0", "-1", "1", "0", "0", "0", "-1", "1", "1", "1", "0", "-1", "-1", "-1", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "1", "-1", "0", "-1", "0", "-1", "-1", "0", "1", "1", "-1", "1", "1", "-1", "-1", "0", "-1", "1", "-1", "0", "-1", "-1", "-1", "0", "-1", "-1", "1", "0", "1", "1", "-1", "1", "1", "1", "-1", "1", "0", "1", "0", "1", "0", "0", "-1", "1", "1", "-1", "-1", "0", "-1", "1", "1", "0", "-1", "1", "0", "1", "-1", "-1", "1", "0", "1", "1", "1", "1", "0", "0", "-1", "0", "1", "-1", "0", "-1", "-1", "1", "0", "1", "-1", "1", "1", "0", "0", "-1", "1", "1", "-1"};
        dataString0 = new String[160];
        dataString0 = Arrays.copyOf(dataString1,160);
        String[] dataStringTmp = new String[160];
        System.arraycopy(dataString1,160,dataStringTmp,0,160);
        dataString1 = dataStringTmp;
        // 数据字符串
        String data23 = "\"u4S00a&'a\\.#GS.\\0ummeS\\aqWm&GC&&.i8[\",\"u4S00a&'a\\.#GS.\\0ummeS\\aqWm&GC&&.i8[\",\"u4S00a&'a\\.#GS.\\0ummeS\\aqWm&GC&&.i8[\",\"44&#qyeG0e'uS'4[&yKaqq8[4S8m&mu\\.84miO8\"\n" +
                "\"&a4\\O[.ei.404mSqSu4qqWG\\4u&0[q#aqm\",\"&S0ae#K&-'qG'Wa..8[.\\#qe4-aSyq\",\"['Kq4[&G0yKaS'\\#OuK0uC44'CCK&8\",\"q#umKmSm0K&\\mW\\#KC&ii-8.8CS8Ku\",\"e\\SK#4Sm#4\\4'e.\\i[S[Ka4i0e80'aGC4GOG\",\"a4'8qu4S-O[uG8.ymyWO8W-mqyy4-O\",\"\\8[S&8[OG-mGqG-Km8mamOuGu0K&CuyS\\y\",\"y\\OWmueGi.O[e.K['WyGe8Kmu8yq'K\",\"0.mO0mGKaae#C-G#4#yeG.-W8-'G..\",\"S[W&8a-muCSOeiSa0mG4uGK4\\K4Gi&O4&4&&\",\"\\maCCS\\[Oy#'aWu4&&4KK.qC#88[KO\",\"-aK4K-\\080miq[GG#q#.#GGKS[GKiuaaymG[\",\"e-0'-qqK4\\4[-[#emaymS-m'i#a'G#yiW4&[\\W#i\",\"G8i0CSa4'm-iW'S&q#0\\G-W-KG#uGym\",\"eW.#&'uSi[8u8Sm#O8-#aaeC'&S\\e88eiKa4'\",\"8[WaeyWeG[O&u8GiC&'8[-'aO-OyG&#\",\"0'ia4&O4q'&i'#80G8#qmeWqGOimWW4CCaq\",\"'8a&W.O&-0myym-.iC.GKq-u&KKi[4'\",\"\\i[.Sy#\\aCO.#qaq.yWSei.[-S0qOW4S[e\",\"i-WmKC08G--auC\\y#[WWSaaq[-aOO#-#a['4u\",\"S##\\yGWS4Geyam48u00.q.[4uCCS#SuaW-e\",\"4q8Oymu-O\\\\\\S.Km4Kaa4eSe['q#'0&.uOi\",\"[uGe&[.i0'mu[e.#&S.iy-Gie88K4G.#4\\G0mS\",\"mm0m'Ky8-#&G'8&y4q0O4\\.G88iSS0y\\[iu0Oyme\",\"y[#.[-\\-80a0G-#'8S#a4yeCKCSa&ieymGSa\\0\",\"8W#O-u'-\\G[uq\\ymi&e'&'O88-#&.O-mem[u&.\",\"'-[u8\\u&0\\m-0u'8O-'G8OeqaeiaKSWyq\",\"8yiu&.W\\GC\\4eW#-KKaS[0G4'[q#8[4i4'i\",\"[COGK4'4&S0mqKm0yaqK\\-0y\\K&8[Cmue&qO\",\"qqyiS''G.-OC'#y\\uayKq4qG8aO8.i-uWu8.W\\-u\",\"S#0eum[m8-#GK[GC4\\K&'K0mSmGiSme\",\"\\qiG4iKq-\\SGayW4&\\a04a..8y0WGK&O\\mi\\\",\"\\0S0CCyWKu-i.Kq-mG#m44e8uy'aiy0m'ym0.8\\\",\"\\K#&4C\\-#GWiCa.m4C'.SCmei'-aq&&qS#4qCKyS\",\"&-emqyy'[4&-meG8K'8qq0.[-Gy[ueaWSae#C\",\"&auqyaeuSm-aeiWG'Ou[&Oim4u8K4Sa\",\"GK'eWG8iaiG8\\OWm88#8&a[euqamSa4e4&44mi\",\"..CKG--a4S[4Su4.a4Ci'mW4yWamG[-#8ieS'\",\"KSWmu-e-O'yGOKO'Gq#a\\S\\#ym8.8G4CCimKS\\\",\"u-yy&G-C'aOu&WeW-\\eaCy#4CGamSu88KWu[q\",\"Ca#C\\#Oa\\\\.-88SuaG-&miuGK&OeOC-y4-Sa#4\",\"qqK04qOaa8iiaq0K#OWSOC4SCe4..i4'4\",\"8W-SGqCe[OS&e0yyW[q\\u'q[8iG.Gq\",\"\\'&y-\\aqW\\Wue\\0G&m8[a44e-m[[GWyau4CW-G\",\"#a8.84eum-CW4#ee#8&iOy0#\\yO&4[.ym#&0eSy\",\".\\KSO'SyW&0[-aGmeu8Ka'eOGqGS-.\",\"u'GOKK\\[4SOuuq[e\\G[&'mu\\0[-\\q0&&W-OGyG\",\".4-yK8W-a#eu8u-G'\\4.i0.[48[OS'iO0#4&\\u\",\"u8#qeK&&CSy0\\WmmSC8-0CmOO\\O0KSOWC\",\"-Oya\\GOS\\aC&##u8mO88i.[e'[-yWu&q##48G0C0\",\"uKiy.u'u#KO-e-y#SOC#qiq[4Se.[-y#\\q\",\"-#Ka\\KayeOymu0u-\\W88'q&'.yyO-8yK\",\"i0'8.#[SG#OK.8OS'-.K-qm-#'.&-um'4q\",\"#uO0qm#muemSKq\\y#&'#mW#0[qGWuS\\'Ciy\",\"m8WqimuqGWGiWeG[4CW&'i4\\&\\40K0#qCW\",\"e8ay\\..[imqGua&W-ye4&&4\\4mWmGyqWS[iOW8u0\",\".WK0.yK0COeWKC\\yqym4em-.K&umee[W.\\0\",\"y4iSKqm4uS8.8m[m\\\\m\\'O4GyO\\CW4.'Cm'\",\"a4Oi8y-y&yWu.#u4O-mWqe[4\\#\\.8KKG88i\",\".'Oqa\\#-q#i8yy[i'myuS&4'&WiqSe4a'q[KKei-\",\"-4'&WqK&8qy0Ke&8.''#O'0y4e-Gm\\Gi8WGS\",\"'Ce0OmaOWeW-Kq#COuSyOC8uG#4\\[Km'.u\",\"mm0'au[G4&GW-.W#SKC[i&i&OK#ye&iu&e80'[-\",\".K8im0K0WCOSK0e8#&eq0-#\\K8y#a&#&i0\",\"#\\G888##C&qCO\\[-i-mC&'aCCOG.y8-0i\\iO\",\"u#iKqmmSO8uq[ia&a#COG48iKum[8umei\\&\\8O\",\"[008mq'Sy[C.aiW-OW8K-y'SyC.eW-a8\",\"'e#CuqKKm8...K['#CC4W\\yW\\aOaSSyW-'SaiW\",\"KCGK-4-[.[#C8O[C4SS\\aO[SmG4qq##Oa.#&u&G#\",\"S-ee\\4yC[8GC4&#aCymiq4qWqei##.K\\mqS\",\"Sye84#m-\\m[WCOeeK-#ue4[G&i-qKWqae\",\"CmCe#'C\\qKq\\W&yG8W'C'SSe84\\a-uae8&\",\"SSae.yWWS&S#emO-88yaOii0yiS##OC4\\-\",\"i0.Wq#K&Gi&-'8\\aOqu&-OuG[G\\Smmeq0#q\",\"Waem8#'uS0.K4W-m'q[G00m8Ce0W\\-\\O0m\",\"&W8-a-C#yK\\am8i&uSO8[4u8OieSGW\",\"G[8u'SS\\qOWyKueu.8Oy0y.Gqa\\\\'[0\",\"yKmaq8ima88#-a&a\\m[-\\-[-m#uO#&S''\",\"a[S4\\&&-i\\..i0mSOS[\\mKSe&.88yW'Gy\",\"C'&W\\8S[OS0KG.8-m8[ma#COq8\\yCWe&4\",\"qOGW8ym.#'0[[WCKm-\\Gy[G[qGK.WSW\\.'[K\",\"K[eqK#iy\\ia\\.ue8u&8y'qiuS&'CqW#Saqm\",\"C&yWue[Wmim-q#a[\\.meGySKaaq[8#q&\",\"#a-yG..Ceu-&GCW-iC\\[KCi..yCy4y-#S[uGy'C&\",\"\\SeyOS#\\yiWm[4C'S'.WaOKK\\eK.\\4\\W'0qWGu8[\",\"qm-K4&ym-8[8eqyWqOuqK\\44S'8y-0mm.[\",\"8'4C-C#q4O-Ci0.KOKOaq'&.K.Kyq8e\",\"G#&yiaa0&-uy&i#KmWqO-O[\\OO.CyaC80\",\"iiW-\\y4\\#8884Cy4G[4'WK\\4Ce8\\&Km\",\"aq\\aOS0'G.y\\0#G.GiWa8WqW4i0'4eWmS\",\"[q&q-SeW#i\\a'O0e-[S.8y.qOS'aeS#O#08-\",\"8m0y#aS\\yy.iu#0##K&G##q[u[&G[OamWC.KC8&\",\"Sm[K\\y-C[O'4Gm88\\a0u[..88-y&u[SyW\\S#Oy##\",\"KyS&'uS[K#S-0euKC&0#SmKe.#a''-yiGC'4a&.[\",\"WuO'0#qayy[C'W0a0W00.\\a&#&'0[4ei8uOS-eKG\",\"euqa&CK-CaqOueOm8eGeiCK'aOqGm4&8Km0\",\"#[C[G-aqOCOqu'&S[y4eiC\\W00i\\imi4\",\"Oi0iayi-#4-\\[e.W#4u8G-O&u4-KS0ai\\#\",\"ae\\Ku&aa\\mK\\K0'G8[m8iy-mm8am-Gia'#SW.S4\",\"44#&4qm'ei\\KOGaqau&iyKS#&aamiqy8KeGK&O\",\"q8&\\0yKqeqa&m.i[G0qm8mu'0Sqae\\m\",\"uum8Ky.i80S#&Cq8yW-0[[''q[qOGmC&iG'CaWq\",\"Wq[SeCy-a&W#WO\\C\\S0#&.qKK[iG.qOu#W\",\"Ga&Wqyi\\O-[GyW.e8#q4\\.4#'K[KS8WGm[#u\",\"m-mS.0[8O'qu'mWeeGi#&O8e8Kaumu'i.G\",\"'&8\\COu0KmS-.W8.84[-0.G[Sm&We#\",\"[We-SOiquS\\O0'G[Saeq'a'qKq'iO-mq0W.#\",\"y#Ce-'iqG#a&GaOaqWu'u-CCCSiO.0.ya\",\"Ky[W#\\.W'Sy-u[u'i.\\80q[qyumu8#y.'\",\"yWqGyK#miO-COC0[0KO-4G.yK#[ueK\\qyiu-C&m\",\"WWeW4KqCO0KO-eG'O#K&qi&GuiqC'[eeSqaa#0.q\",\"''C4W'eia&'.i&-0Gy8m8WK0aOa8#-G&&'\",\"&Sm&#qGi0S\\euqOWiCSaGuqK\\KWeiq[\\CS[\",\"[.iWS\\myKOmS4W\\e'ay[8&u#&&WWW&i&Gq[-m'.\",\"-aqy[4&-a&-&-mG\\-[S'S#uS'Gq\\y.K#O[G\",\"&a'G0uW0eSm\\88uaC'y[emq8-eO8a.i#4&Si80i\",\"&K\\0#O.'8KO4e-\\q#K.K4&#G'e-mq[G\",\"4qCq0#\\[#-mS0W.-&y'i880eOS'\\...''&y#qqy\",\"O#8y\\mWi44C&8S\\#&8i&qemGKmaamyaS['O[\",\"Kqm[4ueu4SymG-\\[GyKO0&uGWS\\#4qC\\Suq0\",\"miu'[-#-q0'SO#mKma\\-iO.ey[-0eOC\",\"-a[C'0&y8-KaSyK[0CuGKW'\\a8iOaOuG\\'S-u\",\"W.im04eKOKaay.iSq[aeCu&-uuOG4S\",\"yi\\[40m.i4\\#euCqy[uyWa..e4qy4u-q0ya\",\"-yKiKiaGW4'G.[u-u\\m8WqC.8O..#uCW\\WOG[Sm\",\"&0\\[S4&W00q\\i##0#0.&GOSi#OG&Ky8.KSe&'G\",\".KKqe#aO8ya4eeiKq&-Ca\\'e-#\\m4eqy\",\"ii.a.iq0Wmy[S4eeueGCOya0W-y8C0\",\"C'O&-\\#\\y\\\\.88C\\aWSG8u\\#ueWuS\\uue\",\"&W\\aO0[8.ayayim&O8K#CK00.m44'ae80K[Kaae\",\"yCe-O'W--WS4K0y['&K0meuK#4[CyO\\Oeim-my\",\"&CW0yime4q4eu'K4Ku-iO0i[uC4\\eG-\",\"C-Sm#\\[GO#O8#Cqi-e8mOiqy['ei#O\\W'y.eiCey\",\"CeCC'iaKCqaK\\yy#aq40i&aKWG[0yySG0OG#.mK\",\"aiy[\\0KW.S\\GCOqe[C-SaKq[Cay.8\\W4iC\",\"0KmC&KeWu'[.-y[eG\\[-[4C'a'4\\GWWWiOWC\",\"\\y8.y#\\&WqauW'4&KmmW#4#-CaCqG8KS[8iS&-\",\"[#a0W.#C8i\\mu&iyWK8q0y08-y#'44eOS8S\\y&\",\"Kmeiu'qO[ySSiim#qa\\0KSamS0#\\4q#[\",\"\\ue8u.ia8'G[-#a4u'8i#&4&[uG&-K4#qaau4W\",\"0&'aaa04a&iG[eCqO&'eW'\\'GK\\q\\m&Gui\\\",\"#W0.qO'G'G&-ymi0#.\\OyymyCu--\\mCCO\",\"&..8uaeGK\\8C[u'&WyyCSu[G#\\eiCO'4q00[\",\"&4qS\\\\CeGq8SyWOGKSOGKqq'GqO4eS\\mm\",\"iS0-.&&-0SW8a[m8GSWG#0-am-mqqyWme#4Oy\",\"yWSmK'GSuGi.OG[ue0y'&8.#8#S8''WOW\",\"8qmi\\4muK&.S'i\\y[4mi.#m-0C#OSKW\\.'ae\",\"-\\yG-a&Gy4u#Gu[S\\Gy8.i-CC.W.q'C#yq\",\".imSaquSK&uuSiS0eu-.0\\ey[eq'Ce.8uC\\\",\"-iK4qOW'W'q&4W0W-ye[#0[iya#&#OOGu\\[-\",\".y.y.'[a\\#y4G&Gu'C&W8W.88W8\\aaGq\",\"'\\#&GO'&Wq#44#iy''KC4\\'8ae['ySO0\",\"..4\\'qG.00G'W&GGK&.&-&GKOG#uW44\",\"OC.&'OS[4.iWG[Si.OSe#\\'G..4.O8[y.SK-G\",\"eKaa\\.8uO8[qWS\\&4q#-eq8&4Gi'-m'&GK'88a\",\"[q[.8y[aO-qS&iOq0y4C'i#i'Gy&O#CS4'Ki\",\"G0KmyWm\\OG.eCKCO&aeKqKq#4'mm''-qi&mSOqe.\",\"..iaWCq&.C.##CCuS8WuqKS4u\\S\\-aKG\\#W.\",\"0G8yuu.aW&8'CSK&q'C0iqemu&&iK&e4uiWGuSWS\\['8yy44\\ySO8.OOu['0WO##q[--00.yCm-iG8S0C4e-00y'&0#q-&a&\\a\\#\",\"KG'K[SyOi'4OO'e#G.0O-\\[.i#y'-0W&u8eiGiSyiSyS[W444yKC#.y\\miuiCS'#m4u'a&'4K'CO#K4yK8yCCW'CS'0e&8C[0-0C\",\"i&-0ieq#4[&-0m'ei'ueuuWa8OGGC4aCOm#O.44K0OK&&u'm&u#0'4eyW-4-S-#-e0Ky#4aaaC4\\0[WuiaKS'OW\\CKOK&q0['S\\e\"\n" +
                "\"Wu&##'eKC-\\a\\WOK0Ga['KGq8a4yCqm[SyeK[K#0WG-C.#WGa-e84q[K.-OW0e8uqCqaCy-emmqCqS#Kqu'Ki'&'u-&S.Wy#4'ai\",\"qia\\ye.OimWuO'Cm4'aOy0-&.W'qui\\G4ee.8m'4'mCaS-yq-\\4[e#K0m'\\8yKia\\-OOeWKO-#uKC&W8'qme[0W4SOW4i8qWWOCq\",\".\\K[e-mu8yOWiW\\0aa'.mS[i#.iaO8yi\\0.KOGCOu0-m.OiaCK#W0u&8[Gy\\K0.#G\\qq0'&00y04-mGmqK0q4.'#q#Cq&[00['SS\",\"[e8W-ymuuS0[\\eK&#Wi#Oy-u4yyGKO8q\\GC\\u-'[K\\y-m[SyW''Cq-#aq0qW#4Sy#e.WeWqm0C4Kmqy#mme-\\WieCS&u4-0[\\0'S\",\"G-iG[\\y0CC.O&8.#&.#\\#u.yuS\\.yOi#O\\q0meGC.Oqa\\4OWSSmyqCeima4iqmqaeq&'0[i0GaueG['i&-qG-a-a-Kq#a8y4q#SK\",\"8KaqK\\eiO8[0mGu[&GC\\q#[Sy-aWG80Gmy4K88a\\euy--0yG0.i0\\me8['u4e''8iK--OimuGyS.&i#u\\K\\Oi-\\.8i884SuCW-W'\",\"44eu-OyO4[mSa.[yWuOuiG0yyC\\..[.8qCGGKS-.q&uS\\K.[qy.aC8.G.W'OeS040SOOO[yy-'O8.'[KWG[4.[&i#\\WW-8'0um\\'\",\"'#CmuGaSW\\&'y--muKOS.8.8#aWmOKWeWm#&KCCeK8W&.#&WO\\#\\y08G-8-aO8e.0\\emqKq8SKeaeW4&ii''q4S8i[G\\y[Sy4''q\",\"y0#\\CuOu&q4iOiS\\q[WSye0&'4u#mq&Sa4qyKm-q8SqyiOq'GCySO&y-aquC'#K\\K&.W\\[eK0W[8-G0OC&C-O-\\WW'&mC-C\\0yeu\",\"uCqa.mi'.0K0..8SGO-yOuGO&\\yqO\\i\\e8&[&m-O4Cm\\yWGuG'-WSy0Cq#4&qO'-O4.q#0C\\e\\Cei\\Wi'0OC#\\eiKG[4Cu-.Kq[q\",\"0q0CeaW8q\\yCm[--C\\\\[Ce.WWe.qm8C\\.mu-&.[0\\mG.Kyuey'Gmiaqq[uSeiSyyyS\\0.0qqmai#Kme-mu[q.4O.[Ou\\G&4a'iK8\",\"KW&4OK[Sm&CSq[m'&#OaWeCem'4yKm0.u4q-0&W&'\\eGWKKSa-0'4&8K.O8mCOuam'u8K0\\KaOqa-u8-GWCKKe-#iyiGm0C-m\\OW\",\"mKyy.m8#OO'CS''4GK&&KSm'O0C0K#O'.[&WeOWe'SCSe0K40.a\\WKCSe0Wa4-KW'&aqa4uqO-mK#Ce44aeKW4\\KmKC4S..y#me-\",\"K#0KKmO04#-O4S0meGK04#uO..i8ii.'GKOmqWSOGim&aCG&u'&'#\\y[W4W.Ki'a.4eC-y\\mi-OyOyyW4aK#eWuei0SS&-am-GKS\",\"O0#m#'&#\\#0.q8-WSOiiOeimSaeeKqa&'GGeKyaOmm'qGKaCS0iW&i-\\O4-q-0yG-S'-WGGKayKOCi..OSauS\\ayOGuGWGyKe..-\",\"G8y#y.8y#-OC\\#iOWG\\4&&#CKi#S0.'qaaeu'80Gey4[Sy8yi\\.e.iOu[mu&GO'8[G8-'CGCGWyeuaCqKK&..[Sa8K-y#8au[[.[\",\"yKqOqq8KCO[S8mu[#am-O\\.G-4a\\\\euSe'G.\\4G\\yOWmGG[q'KO'8uO'G\\0W#OuCS.mS[WKC8CqaW#am-Oe4mS[O-aiKaW-q08u0\",\"8yaimSG.Wqm48qOe0.#G8-m.[#qWS.'GieGieu#qyWyy#uyu[-mm'eiaaSOGSGW4[#iO\\uS#8WaeOCCG[e.yimSuyS\\CqGGK\\-um\",\"\\.#[eWu&\\8'u4CC#CW#Ki#qC\\a4uSmi0y[Gm''e-Sae\\8S\\[G[[\\#aaaCq[OCeyqqGqaeGu-W-0C4GOm'-mGmGqW4uOOqua.&.q\\\",\"[\\\\0'a&0mu&SKmeW[#&[uua&yWG[&'W\\--SaC\\Ka\\0'O[48uC.mGuGWq04Sy[4eKa4\\y0S08W480GqO'e#C#W-0['K4C0[CSe'4S\",\"KG[mG-O\\.&0i\\.[auWqOu&8q\\mueKae[SOWuu4##qqW.#\\q-u-SOWuq[4mW8y.y[ei-'&.#Wu.K8KWe-#qS4S8OSqe&8miae-O&u\",\"qKWqKe4qi-yO-eK0Gqm.CeWui\\y&8-ya\\\\-WK#&G.'[&8iWO'OWy#KC[\\q0.WGuyWmW0K.K4a4ii\\WW&\\SK[i'qW[e08&OS[0&&\\\",\"'C&[04'&C0\\a\\8qm-\\&Cu0mS#\\#yWa0&ii8imS.#&&.&W8uC0Giae4muS0[[ea&8[S8\\OGy0W&'-#\\&4SOWue8.ae8KmWuK#mSCC\",\"4a\\8Sai\\qOm-C-m-y4C\\8WuOO.WGuWG0KG'Wm-uqyW4y.eWa4S&aK&['#WOSmqyGi..#&-W4'u&[GCqa\\q4O8&SWim0#-[OSm.\\4\",\"O#\\mO\\m\\K8W\\m\\#a#OuGG4e-#aCia\\iaCmyqO444OGO[0eWyOC&0CyC4uWWeaG&&#uyG0KC&u'&m'e-yy[O'..uK4Sm'S8Kuq.\\8\",\"y.a'CiO[i&[OWuGW4SiuymuCey#-O'[mOq0G.88u0Ce\\qOe8'\\e00'GWyKSW'44S8W#K#Ce4CG4#yKmay.miWu-CCae8Kq-eG0Ke\",\"OOy'[4&.\\q0[aWm8aWq'\\yW&.e-S#['.\\'WWeKaC\\0WueWSW8OS0[K'e-yKOiaO&W'i0.i&S\\[0Km8KaWCWu4O8C4uWeiCim[O[e\",\"4S4SeymyyG#-mK0a\\#-y'SqK'4-OC&OiC&e.u#0q[4S'a04CKOOKa\\GaS&[4yyySOqmiay##4K0'#iaO''4&y[88yK&uS0000'q\\\",\"WGO-mm0OS80W\\##&[#\\[-\\GS#uSaaWiiuuW'uW4\\ia#a0CS#C\\aaqe.\\#yy'qC4KyeSSyy0a#WG0O'W0maa4W0eW#Sm'WiKqiy&G\",\"yO&&\\Geuq8KaG[-&u004.8qyWG.OWmG8-0#ue0O-mK'WuK0.#[&WqSO444\\8e#44a&\\-W-u#a.Ou4&.\\C#mKmC.OSyuqi'G#-W#C\",\"uGySyCeyC&q0.y.SG0qO8ymimS4\\K[eCm0W[.&eimS#Wy.Su4Cee-m[8Sm''&'\\eW4-'q[ie8[aaOiK&G\\'[Ou0-.Kae.\\i#['eq\",\"u&8y.WS4'i'&GK48&e\\au\\u-ySW-0#\\aq#0W8\\#Se#mWe0.umu'uOG8WOmeGKKeyuCi'4##Ce4&8uaC.4S#OGCO8S8.ymG-#Ga-C\",\"80\\\\y.i\\u8u&#aKa-ai[iaCqmmK-uqee&#e#aeimWG.8'[-8y4'CCSe0O0.&Wu-8Ka\\eSmG\\4\\0\\i\\8O\\y.C#.4Oi.KOue0Kq'GS\",\"WWuy[SSa'S\\aWeGWa4[eu[m.[0\\4ySCKqOSu08u'W\\aWS\\u&8.G4S-u[e-\\0'Sm.4Oeem4\\im08me[-OiyqSmiui[q.q4480y['C\",\"WqK\\'uyCaaSuOi.uWmK#-#yy8O#&8'C&i-8i4eu..Sy&-i#'KW-O\\-\\GWiu4aa\\#eqK0C4yyWiC&'SCS8mi\\.Smi0Ca.mS&iKG4C\",\"#mi&qaCqW.S\\[O.ayG\\W-#aqyK4'WO''\\&e0[&'q0'8#4y4q#m-y-Wua4''S#&u&-Gy\\[\\.e08\\8.#GaCGW'0&CmSi#qaiquOW&8\",\"&mSiaaOimG.[Oue[4CS..K#&C&4'[yi0GiSa4#uqOuuWOm'a0aW'aCq-a&WS\\u#W\\\\G8&44.C##&&'eWOi04aa#W[Gum8qmKCSu4\",\"&8#y\\a#\\W-K0KK\\G.\\\\O'qe[&G#&m-'q0i0'qWq[O[e#4[[W40[imamy84e[\\Gu-y[[G.\\8.ai\\.iCmu0#&WyW-\\8W0'C.&-yae0\",\"a.O.KaOKO\\m\\mWS.K0u\\GWC&G04S.8.W8W8-\\m8[8iyaO'OCy-Gim8ieKy[[q0i0Kqeq44u8&4'G-a.[ui-\\4[u4C[0KO4--G'q0\",\"iq\\aWG8iK&ia48'i4e.4&G[S\\.S['&im\\-'..[#Gq.[\\C''a'uGO-CCW-0q.K\\08'C\\C[ey[a&mGum8'eC8W#a&uCCO0.W-4Gq&S\",\"WG0yy[8iWWu&Kqay\\0m'S0-4-yu&OW'-\\0yOqyC&-#&WSC&ym&0uK\\#\\m-Wem'iu['88&8yKO-qm0q0SaSm0.mS0\\8Cq[e-\\KKq'\",\"O#.qaKWmGCqKa0ymmWG&[4G.#CK&[4[y.\\uee8S#yC&q#[--W#&GyGWqimy8iq&.OyW48W8-'&0O&S&&\\i&8ym''u.[S#m..\\a\\\\\",\"88'KOWuye&8Oa\\auG[y#4\\uq[GO4K.0\\S4&SSa4&0eCq0\\e88.Km-\\a'O8ai#O4Ke0[Sa\\aW4004[SCeyW-'CyuKmqq#0.COGWC[\",\"Sq[8Cy#C8iaq0#S'CG'.eWq['SeW8yymK#qi&mi'WWu4-u0[-e8\\m4eKWSy[mK\\[u'W#y8i8iO-aC4SWq0K.&G.W[iyiSK\\C..\\e\",\"C404i0mGe4#S&a-0u'yy4qu0KK.yyKa-yG-'.8[i.-aW[m[u4\\&.O[#qG40iSeO#CCq-i8Cuqy#GKWO4iS[uW.Kqq#0#qKO-a-&u\",\"a8Wme4WSmG0O4qy'Seaq.[Ka4e[8uaeeu-C\\.0'SOa0a\\[0eKm&e88#4ueKCCSa4ee-8ieS'aaS.m-K[GG4iaaC8iK\\GKm'K'O[4\",\"&G.KeC&OW0&O48uuW0-[m[m.8\\OuWGKC[me#0O--Kai'Ce-S[a'...C4'-OS0#mymaaOSG&.[qW4e0eGG.GWqO[WqO.[-40#8y#4\",\"0KC\\#e.[[G.-yW0aia[8qaC0SieGO'qK0eC&&mO-eqqm-.uqe''.myi\\#aiWS'qCm\\[y.u&ue.['#q-4aO8'yW0u#\\amSm8S#Oa\\\",\"0qO#W0i0KCO.i#8i&0yWmuaq.S[eauee.#e4e-muiC#\\&a4'yK['Oi.8[e8[S0i8[Su#-W-\\SuCqyqWWyGK'[e8#Wi[O[uiS&..K\",\"00y.4\\mCuymaO-#mimS0uGG.i#iCCu00mqaC4S#SaiO8'G8GqiOay--#ay-OOaa&Ky.eGW80#4CeS0u#KWqKe8e-yiSCiOSeGK#\\\",\"m\\0mW4uG0C.-q08uSu[WWO'eOS\\uyi0y#.8&8K\\&yi\\Km8KGKeieW\\y[8K0m[uqq&CC.Cyy#u-0KWqiy-m'C4KGqmuSay-q\\y'W'\",\"\\&i-4'S0\\0m4'8OGWSmyK\\&'&m0W[8O-\\qu'mi\\e[#0W8[K'W-Wa\\.''&i\\a#0GyWmie'C4'8mW-O#Si4qiC&.O\\q-##4q[G[q#q\",\"eK4y[GGu&'[m-q\\mCOi0&-\\#\\K.\\#'q-0imG'O-yWKC\\.G.aO4S\\.[yC\\&'u4\\KqWW&8eu'GW[W4K\\.#ai..-0-ii4qa'&.KS#Cq\",\"'[#C&G\\y\\[eCSe-#-a#qWG-a-iuGOaae0W&#S\\0##4.i8KqyO-0[-\\Kq--0GC&G&a48.e&&[i0WWaC#C\\.&'O&'eGuaO'eK'88Oi\",\"KKq.W44'4a\\aqaqKK.uS-O-a8Gm'K#SK&[..-CeumSq#C'Ka4Kmua4\\.8u&qiSW44eG84q0e[WC&GuSS-q88uKqO[84GmiO0\\eue\",\"&#0#4Se8[C&&GCCmGKCe8[.i\\.[SWSmKuCO'-mWGK\\K-O8yKWKy[4-S[CC4S0W\\WO'#eCqOG'88.ieaqa[eWS0aCyCa4q4yqK.-i\",\"e-y4eK-#&GG44myaS0C&.m-SC8Si0ey-0m-C-.eOeumS..ei-OO#0#qKmOee.#G\\OCC&eq'&4m#Cq8uW000[yG&\\8y[Wu-[\\uq[G\",\"--4-CSOGym8O'#uSe'CK-Ca&'''C#[#uG8.ym'-ay---Oue&OK&8.y.WGaC[q#\\aGKy.a4myGWi#88uKy4#m&'SSOq\\GSS[&-OiO\",\"&i4C8i'\\#0-C8yy.W[OiiOue-&#.WS'\\qqyGi#'\\[0ymeW'S\\8ye.#S\\OC.iWu\\e8.ea&[8\\u'i-\\..8yeu44SS#G-me.#Ga&\\.y\",\"mGiOqK4Kqe.uGeqi04ime4qK8yK#4C8.GW-aW8[\\0S4ymCq88G-O-'ei.8-CKO'mWS[KO80a0e.-miSu0\\.0WKaOO4C..iS&W'Ge\",\"yeuyiGuWeGuO-qm&eG8mS0[GKGuu\\KO-8ueO..iGi[y-0ySG#Saame&u&4KWSqO[-Wm8qi[e.i-[GW'4Wqe-mS.WS8eW8#iWW['G\",\"'C&e8OSOm4Sq&..miWSymO#\\S4CeWqKiaeiW4S488[W0W-eOyy&'y.[&[u\\a8&iOSaS#ym..SeKC8O0eKueyiW4mGy#\\m#8ieqSO\",\"C0[---4ueqmmG0eiCOue4S\\aOC\\eSqaeG00-C&-O.&8-0[y#4SS[e.8S-\\i\\8GO\\#a#0CW...W-0'm#[-.#.[yO#8WGy.08i'C8y\",\"SeW-C\\'G[4\\WK\\[8#SC&-4qOG8.&&iiO-\\W.Ke#Wy0m#\\'#m'e'[Ca8aCqau[qym0Ki&0\\8y.aqyu[.[eCii'S\\#.&[8u#KqKq8y\",\"a\\yW[G-0.W'[-iO44[S\\K\\[uOO&qm8OG-\\q[G8q-m-me..[Sy[W'\\iOiii#.\\[uyWi0Kam8WS44&e88iC4e8'Sme'&'u.[\\'ui0i\",\"4C&G-K00-#aq.i.8euyyK.W4CW0[auCW'#mK#[iS-[ae[a-my0KaC[\\0[OGueK0O[8uS[-'[4C'&#8yyiSuuO8.WqyqOW&'0-Ca4\",\"y-\\a\\[4e8.8q'K[-CaSS0mO8GueGWS0Ke''W-&GOK.-\\4-S8iuG[meW-ym4#'yq[4O[G8\\mi#00uS&KO8KWei&8&&uy#S44C.'e4\",\"qeWiO[O'S#[W4Oy&uqiCe#a4q0uWumaCy.0ySKqGKa4qqmO['-mG-\\#KC.eO8[Cm'eyW#Wqmiyy#ye84&0yey48W-&&u4&8imC4\\\",\"yWu&W[SKGWeW0&m\\'.CW-CG8uy#SqK#qKqGK0COCamKaW'm-&aW&4i'4&i&'i#'W#[\\.W4[.qW'4em--CSa\\G#'&-#KS04GK&'8\\\",\"[Ou-y'G\\KaWqyOSSaS\\8iCOS-CK.OW'C&-uiCyeK#-e-...SuqOS.e-a\\[WSay8y'40uuS#mS[0aq#4emG-ymi.Ca4u&8iOuG'#C\",\"iCS&GmW.##\\W'&#-y8i&.&8q#[SG[8&''&Wa\\#O.yS0-aKi.8yW'mKeaaq&'qG-CuCm\\S-iKi&4O.GOGi0Wy.#qKma-\\.GuS00C'\",\"[84&4'&qa4q'am\\8[GO\\S-y#CCWS&\\4Sm-#4'meaOuKmm0#mG'OGWG#4a&.#-S4\\ia.SSyyu&m#0\\m88ia&[.['G#G-mO-CC[&W-\",\"OCm8['4[\\.\\a\\8COe\\Kq[#KS\\0S[qmGSy#4e#eWum'mSOWy8y&.u4uSamKO'y-8-8[eiq&084[m.[&m#S'4Sa\\y04qyO&-\\GSym-\",\"C&uyC'm\\aOi40W-aW\\Ou4yC--yqCGuqCym8.y'&8Cm-#mm'Sy'Sq\\[4CeOie.84G0SWKqy.#q0#COq\\\\G-KO84Ki\\-8\\&iC\\88y#\",\"iy['8OW--y08W8WqKq4W\\\\K-SC8y[.[eCW#\\WGKyO#0i8G-\\0myG-K04yyS.euO'8KG-#yi8aKay&[i#\\#0#OG0G[e&K-muS#\\8-\",\"m0[G0##.#Cu'Guei0G4#qWmS8uS8.y\\8Omq0eWqy#4q.G.K.'&y'u8Oy\\&Gim#u[em'mO&iC-a&0SCW-00Ce&4&-4GK8aqGW.aSe\",\"40Cm[\\CuWWG'qaq\\S[O8'SO'q'[uqq--q8WeiK--4GuO-0m-q\\eyKOK8ai#KeayyuO&#OaO'.8\\S#O'eGqu4Ke..iCGW-##W&SW.\",\"-08&W..\\4&8qS0mGeqaC&88q-qyOC.uuWq#aOGWG['CC#y0O[[Gu-a4Guu.qyO-KmW#'KO8O#ey\\q\\''eu-'\\e#0\\-KGeK#K&que\",\"88G8W\\#'W-y4&.iu&Gaae.S0u\\'eGWG4\\aeKOqSOSy4a-mGmi\\ii&'C'eGK&uqy&m'C40a0[Ki\\uyS#y4.\\u8[Om#&uyi0yi0.[\\\",\".qWuCSGeu-ye-W&[&'e-CWW.uaa&0'[&u'yG#au4\\.WS[8.OCOGOiC4W-GOW4#CqCO'&i#&KmmSKiCe'[Cm-\\#-qWymGO\\uS'C4K\",\"q0uCe'&GK.#iGuSiK\\[m.\\yGW..i8.aGyq0e'04q.COiW-aGWCe\\.yWiCK&Sm8iSu8\\a['44qq'i.[\\0Weu[a''a&S#CCyWa4O#&\",\"8SOu\\ye48.[am#O#S8C'..8Weu-4#mS4[C4qy\\K44\\y#0#CqC#.yW#aK[C&iS8.GW-.#G.Wm\\-W&SK4K8Ku&-SmuGyi'q\\\\yC''G\",\"S&.Ka4qK0WS.CC.8#ae#O0yiaS'ei#i0qyi\\.m0K'G['4&.Ou'yq&y[m-...S#Cu&uq0KGim-#'a&i&qe#\\OW-.-8W&\\a&\\yqei4\",\"88u.i#'WG.uG-4&-i4mK4#0W'ya&'4qKq04eeOSSu4\\[SyK\\.S#qaC[8CyOC8imS4qa&8#-OGq\\me-aqCSym4a[-.&uyy\\4G8'W4\",\"WC.iy.SuC.W\\aq#K-mKeOm-&WmyeyaOau[#&4GuqaiKO4GKaauSaKa.'G8iC844u&yGu.Se-&G.4ae[eeuS-Gqi&-\\4q#qi\\i4'i\",\"O8-aOWGymC'We0.yeW4OuqGiOqae8iGe\\muGS&.0SmKi[\\\\.Kqum[S-8\\0['4\\y.'4-qCGO&u&G'4GaC'[i\\8&Wi#qmS0yy.mu4e\",\"#O'.&888[8'Smimy&u-CK#aaS80iCa4W[qeS-iO'#\\ma&qyyOy&\\WGmuW0u44#y\\CCeC-C#\\m#aqW[u&S#0&q-&mmii88Kq#0[Sa\",\"\\ym'WSyWuW-\\0C#e.W--m8e&-yyu-KO4[eGGu[aK-Cq40K\\.8-O\\WKC&mG#O8#CeSOu&8qaqm'a8im8m'i&'u0[8KymWe8u4#O&u\",\"G'emim-a\\[.i.#-[W-#\\#-G88Cq#8S[a&S&iqam.0W\\#&Cq&q.'q#4C\\-O-eKaaaOS[K&meu'\\.O8WG4m0iyey[[au0.y0K.O'Ce\",\"4''CO#y4u.0uCOa'KW8[y.mCS#WGW'aC.0iCm[.mqu#GO'SCSC[m-m'eG'[qiyq\\0memy[4\\4q.88'#yKaGyqqu0#[#4&#i\\a&'G\",\"aCqKGum.-miCq#a0Ci'u&.G.ye-#C\\Ca-CK'a\\aaOCCC.##K'Gu&C-KS'CyWqO-maem8yi0uCO[Cm4CeWG4''&meuie'\\'&i8i[e\",\"[\\aWWSOKGCe[Cm8WS#&iO8m-#a\\Wyy-m-\\G8mSWG8.8mi.#Ku-meGuuS[0me.S0m.K0u#[.0K-G8SGOC4.[Oum-O.O'-y[SqG\\W8\",\"qO8\\0O#''0qOWe8[SO&miK0qO'4#.u0.4CG0y.uSae8qm'-e&a8y#W[iWS'#8ie'eGm'0Sa.CSaum0KW0&iSSuGSKai0&CamS00G\",\"\\K4eu'[i-m.W\\Wa\\&u&a0m4\\&.WG\\eG.'u\\OSyuOW\\e#iW'&m-4\\y##q\\[yKm8a4\\\\mm[[8S[uGKe.8W\\&S4'SyqmC-W0#e&&G[0\",\"[K-mKW4\\[\\C8qe\\0y&WSm00ee8-#44e.imaKi[8WC[iuGCC#eK.[\\m'#8GK&OW[44'O4iaW.#4\\WeuG..W4mG#i#4\\0WG8-y#&iG\",\"SaSWaOuy.q0[4'qGO.-Smae84-#8m0KS4.OG&'S#0'#\\u'C&4.4iaOmG\\.CC'Gi.e-G.Wi&\\yCa'.[-W8[[S'mGu--WW.q0['Su'\",\"-y&-8SC\\uSSi-eKKCCKW.e4\\y[K&WaCKm#yyKmK4#ae&'W-eOyWy0#[G#y4Sm\\aq[Wuei0WeC0y'-Oa4'qi\\0m8W#Ce\\'qKC\\q4m\",\"Guei\\.88KaWi-0GuyW&Wm..yq\\4S[GK\\q#'e8#Omei0C880y##e'4\\#ue4mWOaq8O'C8eaGuG#'.Gi#0m4\\Wm'm8mCWaOa\\yyC-'\",\"K-\\\\yK-00KaeKGa44'G.C88[-u4Sm40W8e-'Oi&mGKmmSWOW0m-0[88aOaCW4S8Oi\\K\\.&0mi&&S0\\.i#GGu0qWeS0eKiWK\\myy\\\",\".['auSey0Sm.i.W#.y-ayK[e4#--iCOKCOSK'-#.imuSue.#q\\Sm'q#[4&qO-##aq[WS&yaq--mSyW[0GyuKm08y--ya[SKGOaqy\",\"0K&Ge'qqqO4'#C4i\\8KOOWWeue--K04eGGGy#&a.KO-C4'yW4&u-0.8qam-OKq&uyGqy8a8iW[KaS&WiSOyi0[eime-KSaGWC.8u\",\"CGaq#WS\\Weu'SOC&SqqmGuGWOe##a4&i.W-Sa4SCqSm-4W[S--OeKu'e'CymSa\\CmG&-0&[m#-aa-\\'KqeCi##y.#OuGai0#.yCu\",\"#q'8[SC..8.08WKSyKGe'u-[.4O&.i[uK[mqy-\\&S#-m4.KS0C\\0S#&WmC&m88[WW-CSq-0mi[G#[\\&C&i'\\WO[[.8-iqm&uW4\\0\",\"S.C44-S&W-C\\\\m.'uS#0-m[q#44aq8u.uG44ea[a-m[yieu&i[.#Ou8eyCm'-[m-a\\eu#&'#\\.#'Cy0-aCGaC[uG8'm8[C0y[W4\\\",\"#'[0.iOuSaqO'WiKyCauq8..WG-\\imWaq-Oeae040#&KKSaOeqqa4iC0iO8K0.yiSO.G4&-8yK0[8.-G8.[eeyq[#i8WyCC4qaK'\",\"qyuSm8Cy&G08W'-yi#ue.yiCOKW\\SuOSyWK8\\OCaS&&e4aeKOm'mS0mCe-[aWuG[8GKC.4q4qGKmyy.qWa\\mG[4qeKq[0KSyu4i\\\",\"eO'qOmCy\\0#KCa\\O-uK-ySSOG##-aaymyC&mi\\G.8e8#-.O88im'imWuKa&8yy\\Ka.48Ou44qKO'4C\\&S&'q4K-W4&e\\O'S.yyi\\\",\"qaauGiqqOGKaq[8-#8eWqOaCCOq0#m0[4G[W8aK\\i'Gq.m#iiGieWq.0W&Wum8iW&qOG[-O'8C&i[8G.q8'.q-C&8\\C48Ku-a4y#\",\"e#Ge'eGu4C'.KC0#qie8u40KO'OaWKqe.i8Ca..8aeKOuqeWmWu#OS0WeW[i848.&G'G000yeCS'\\iC&.[-ime0[&-K&S\\SyKSa&\",\"\\#8uy4KK.OWi&e--eKa4u-'SyC-8.yG'&qO..--[4&8W'uGia\\m8Waqmea\\uG\\K[Oamiiym'C4S0CS8Ku[eiy.Ka\\Ki..aq&.&4e\",\"#8SCqmy&W44qe&-O..[0y-0#e84a&u84a'C0[y[#\\y[SaaaqO'W[eiim4K\\maeaiCG8eiWiyOOGu-q#&G4C&aWCe.8\\#iuyK4ii&\",\"W8COGK0qi-0#qq\\.#ai\\KSGW'4m[m4m&aSy\\.O-eW'4\\0yaqq'\\m8WS0Wuq8uiu8y#iqWyS4W-\\0m'KOOuWy&'WO#aC0Sm0SC\\-G\",\"q#yumiG''Cy#0WC'W#O##aK[-Km\\aqK44q#&[m-eGa4WW.'iSWuCaC#.G[\\4qySqC0e8.'&.y[WWmSyy#y-C&'.\\KK&iaOC&yOq.\",\"0&G.S[.\\W4SK&m-&KW8-W.K4.0-8&.#0#[-8CuSm.Ka\\0[-Wi''.aaK[0S\\aKyum&q8i8#qW.8C\\yW48#aq\\a#a4C\\4C-ame#Giy\",\"i&ieuO&80GyWe88O'4\\y8[G-[yeae.K0\\&'eGu'u&Ge.[44Sa.#'4a4GGaO-yme0'u.0'Oe0[eKayu&8-GC.OS[#GOK\\0K\\mu&GS\",\"imSyG8qaq#0ya-C4Ci0[8a\\S.yK'u[8y'\\KCeGmeiae.4-ie.\\miy8auCO0uG4uu4'y0-&8yuKm8S#uCe.imG0#0\\#'i&\\mm&yi4\",\"K'S\\u'i'u8ymWWKuG.i.y'&qS#WuaKyu4&mu\\[i-0.q[m8'eGu-'u4C08yWG.#.We8ia'8O'-04&u'aeaO-\\0-u-y#4yWm-O#WG-\",\"-\\[.WG&qmSa.4S\\0G-#4-CCSOG4-q#CGW'\\G'iae\\G44#GK\\eWKO8uC#4CSyGm&.K0a[e..KKqWm&a.O&O&'##aKmuu4SG8yy.uW\",\"q'C.yO#\\mG.GC-\\#.KaCqO[S0m'e844&&ueWuK0mK4CW-O4CaieamyW-'qq4&WGuSW40WC\\y48#[y#aOu-G4O'8.uOSO.SqG.[OW\",\"Sy[4Sa'\\#Oe#K#0y&#e8##mqCeKGi\\4eWuG8m8y4WiSOi-8COi'OW#S0.WG80yCm8[GK'G[\\8&qW##q88#['SCS'-00\\WS\\.&8.y\",\"0qaa0-.Gq8iuOy[#qa[-O-iae\\muS\\Wum''&'4q0-u.O8uymS\\&K4u[aaWqymy8y[#e0mOumC4[..[\\mG-OWii.G\\O8'CCKOuW.-\",\"\\my[u[#e.ym8u.W'GW4'''''yOemi'i#&8G.ue8'0yC'u[[i8'.uOSi\\'#iKuWGGy#4ieqKqS0O.y.#O4KSWu.quOyi\\K[44\\[4-\",\"4yeWyuGe4Sa'au&eOWKm0ymii[-e88iSyyOiyK\\88mGau-eSK\\-u.#&#e-C4CO8CCq4Sy\\'eCS[#iOiGqmim-\\OWeCWu4q.a4ay#\",\"0O0ueCuO4Se0G[88[a&WmOSi#80&&84K0W\\CWm.W'#0\\.W\\yWy0.[G#0COu-q[S8&q\\OuSa44#aOaK4.[8WaC--S\\au84q\\0..0e\",\"iCeWmy.4iO\\We##OCym4.#8u&8.i\\#.[8SSy4a\\04e[0qeK\\&-#\\ymu4qO8S0mSWqaKyi'C4&ueiiGKq0Wq[#C\\i--eGSiC8iC-.\",\"&0a\\#GuWqmmyaeW[-OKqe0Wq['S'iSSmOCWGyWKOqeiGm0..&aO-KeC0ye0.q#0#\\SCCqWOi#&i0.yeqO0&&8G.y8.ym.iOue8'4\",\"\\ym#4C[ueaKSO4SS[&W&eWq#0aCO-0y.&0--#[8'#K&\\0Wy8-#i-CqOySa&qyO8KmWe0'.#m8u'44#S0K\\y.#OS-#Oa#8mSa4m''\",\"8i4OOi'.-&a448&&488uy'e[yy00WCWiC\\a.WmCKOGWK.yW4-0[4KaC4SKSeWG-m8#qO'eiqaueG[.'e8W-.[.K4q-.#4S8&yO#\\\",\"W#S#iueCS\\.8ui04eG00G8q#aG8.KOGW[Ky'y8CC\\mqi&[i0e8CmC4e.y400\\8im\\iCeuSa4uqOuKq[eG.C44WGiS8KO''Syiue8\",\"4SuSimq\\CKq[-0#yiO0a&CKW&[OiC\\qC&iaC['.#aOCqGW8-CK#GiGe-meWGW'q8[4yK0O[4C4#00O'&'#&.Om-#0K\\8WSSa&S\\C\",\"WS#y'C-mmeCyq'[Sa'..Syy.yWu8W-iOS4G-O-mu'qGu-0Kq0WS#ymaqy.\\8OuuiO'i.0y4u'y[S-0\\0#&8WiaaCeG[WCa[O.K'-\",\"8.8[GiSO.0m\\8C4.K&[aSGGK\\mi\\&\\qKqyK0GW\\'u-Wq8['&SGK-aa8KCS'4O0O.eKa4&q&i4400['SOG-KOeOOWCaK\\\\yWa#'eW\",\"iuSa0&uS[e#4[y#04\\-O#aqW0aOG&#-u[O-8uG8WWGW-m#SCu&u4&qKO&Sa\\48.uiCmaOa\\iK8qOG.yGGK#''Wy\\8[.CKa0WCKWe\",\"-yO.W-44S-00#O4SOa.Ge8G.GuC&#C[S-CC48y&'ami[qKu0aau#K0K\\eGKiOi.mye&OO[euuKKeWuua4yeuK0qG0WyK4&emueqO\",\"'0CaK4y4&-W4qyyS.0a8\\\\Seem88[.[KGO.y.&uq-ymm4qOS#0u-yeWKaaa\\#Cyae-KSKq0WC\\\\y#q'SmSu&W.W.OmGmuqOq0e&W\",\"8O[8'Gi[eS#OS'eq0[CK&C\\'GuG.[i#GW8WaOOuS-\\KymGaey&i&qmqSi#Cq0iOi[4qaau'&&u[iq#CC..K&8W48S#0K00iimCG-\",\"4\\qyqqGKqK0-4&W#mi[4qW&-['Cy8eGC&8'K..[G8y0i0G.aS.40yCuSmy.-WeW&WK.KmW8aa\\i40.i'e-#K&aqOOGm.a4[-ii\\K\",\"&\\u-\\mKWGmqmei00-#eOa&qyWu&8i4a#-Gqa-G.W44#aOeWK-8-\\K-S[-080m-CeKOGCOaGa8Su&-8W'Gu4[W[4\\K&O-0q8Wm#qy\",\"8.u-\\4&iquSSq#OS[4[.CO-\\\\\\.iy['0\\aOqC&WOW&.80'&GK.8[4-#qqWimm8W-a\\i\\yWGSa&iG#0\\mCmGaO-84e-a#CSKGS04C\",\"'Si#8u-#W4-a-e&eeOi'04eeK4SWG0euSi&'eOuG88'qaO'Ky[uW#WSa-#mS'SK[m'0#myO.i0\\y#iiS0C.yWS#Seu&G.8q4CKa4\",\"S#&8[i0OOu\\O80WOC8eCC.CKG\\Km'04q#Ce84yau'ymCW'qy#4G.G.[q&uy'u0.KeK04#C[-\\0KGe'0iG4\\ayy&Cey#O4ueq8W\\.\",\"WmmG\\u#O'&GW-uG4mKO&WSOS&ii\\-\\uSO0a-#y[S-y&'[i[-eSaaOiG[u8'eW#8G0W4uGu&G8iuWSi\\04i\\[44qOm.y\\C88[G0.8\",\"W-GqWi.m\\e-muWO-aqeuOOGeK[e.W'qa[8aO.e#C-&W\\We.444[-#\\&OuS.yK&.OW\\mWK&W#\\aKi4W-C4KW4WeuSu#aq'i4&eaq\\\",\"O8&'4.K\\iGu\\.[0W.qmu&y.mqa&Wu#[aCme..\\&ae0&.8uKOWe.K\\eGCOa[0C-CGG&e'i&KOKmq&G-Ca[e'S--eG8GK\\.4O-0G&'\",\"4#8[em00&8uq[G[88#OGGG0[#'iOKG[&-KiiOO[eyuG.[#4[ee-aS-.4eWCa&u-44q#'iua'8iO.CCW4WSOu\\mm-#OS-m-\\8WSe&\",\"O'CuW[GC'4i#\\iC#aCC.0KC'4W&u.[[&u'\\0WS8m40eWqOy&ymq\\mGau.#qaaC-8&G[#a..mKq4e4[aCyKO0q0#yK.8'q[.i084&\",\"m4OeOu.O-S''CuyGSeOWaGKOGqm&u'40Wq#aW-'4m'#4eK0OCyK\\eSm'&8-uWeCGa[0W&[uq-uOiyyO#umW--8u-Oa[S\\O0\\0mqe\",\"&yCq8KO0'4'\\Oi'OuG.KqmuK\\y-#-SyW'CCa&'WSy#\\.[.u-mW.WW#Ki0\\''q-[S'[8-#Ca4We.iOGy0\\4\\4C0WaCqqCu4mOq[GK\",\"0y0Cyi&ae-\\K0W0.K.u#C[auCuCWq\\&uS\\yC\\WSy'Gu-.8u-OW\\K0muWSm-O.[4S\\qiGme..qKSO-8.yWuimeWq[eW4q4WOW\\4ea\",\"&am.4me8K0.[C&G8Wa'.#-m-#&y8SO8eei0G-#'q#SWGC\\qeG8#We[aWme-CyOWS&WSOiaO-q.-GiK\\Cm8.'.e.GCS0O[y[G[CCG\",\"#eCOaim[ee[y0#WmW40q8mi-SyO#&'&\\.uGe&[[4qO8&aK[.q#yO\\#im&.iu[K.u-OS&u0G&8uGWKCqSKK4-[[GKu44e\\i8#qKy.\",\"&m-00KaeiOSm'&-uaOmqKaWWS0Caa4'[-##4aCiaem\\&WW4GK&iy##&\\K.'eiaeC&WS..O8WmO'C\\\\yKmOSiWeaG\\KqeSKa#4&#e\",\"uSm\\0i#Sm[uqC['.0m8'CmiSaqu&'immuS[C4SK0mmK0qyG&u-y&Wi-Oyi'Sm4qqq0m&-G\\4O\\KC.W-y[8S#O8yG[OOay[u.-\\e\\\",\"[0qmW4CC.W.[S'C4-C\\8i\\[\\80#yymu4eKG4G8[eC\\&S[u##G8u['Geuiy#O8m'#C'8yK-i-S#C.S[S0Wm#qKG0m-O4OS0004m..\",\"4Gai&'ei.8\\#mi\\4q.u&WG\\uK0\\WS#4G\\'[e#\\-C4Cu4CCWW\\4CWO\\.&uy8u-\\u.-aKO'qO'yy8&WuCe-GmCq&.8S#K0m&.4'4#[\",\"#&0['&G.Wu8yyK&CGmuy-iG[8m&S.Gie-&aamKC484e'uq'q4Ki\\m'[[04qW-#K8OSi[.4WC0W&0mC0C\\[e#8\\a\\#4'OO[4iW.#\\\",\"Cmy#qWK[Sq8GiC&iiG#i-88&yii8[O#u0#.SGi0[yeO\\eG[O'a8#euqS8OC[SO&#q8.mSO8WeiCS&W4[W4Sqy0q'4.48\\aeu00[u\"";
        dataString23 = data23.split(",");

        dataString2 = Arrays.copyOf(dataString23,160);
        System.arraycopy(dataString23,160,dataStringTmp,0,160);
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
    public static void main(String[] args) throws IOException {
        List<ByteBuffer> stringList = new ArrayList<>();
        ByteBuffer stringLengthBuffer = ByteBuffer.allocate(2*160*9);
        int totalLength = 0;
        for (String[] datum : data) {
            System.out.println(datum.length);
            for (String string : datum) {
                totalLength+=string.length();
                stringLengthBuffer.putShort((short) string.length());
                stringList.add(ByteBuffer.wrap(string.getBytes()));
            }
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(totalLength);

        byte[] bytes = byteBuffer.array();
        byte[] compress = compress1(stringList,160);
        ByteBuffer wrap = ByteBuffer.wrap(compress);
        int tl = wrap.getInt();
        byte[] bytes1 = new byte[compress.length-4];
        wrap.get(bytes1);
        List<ByteBuffer> decompressedList = decompress1(bytes1,stringLengthBuffer,160,tl);
        for (int i = 0; i < stringList.size(); i++) {
            ByteBuffer original = stringList.get(i);
            ByteBuffer decompressed = decompressedList.get(i);
            boolean eq = Arrays.equals(original.array(),decompressed.array());
            if(!eq){
                System.out.println("not equal");
            }
        }
        short[] shorts = new short[160*9];
        stringLengthBuffer.flip();
        for(int i=0;i<shorts.length;i++){
            shorts[i] = stringLengthBuffer.getShort();
        }
        byte[] bytes2 = IntCompress.compressShort(shorts);
        System.out.println("compress rate : " + (1.0*compress.length+bytes2.length)/totalLength);

        System.out.println(Zstd.defaultCompressionLevel());
        // 使用compress1函数压缩
//        byte[] compressedData = compress1(stringList, valueSize);
//
//        // 使用decompress1函数解压
//        ArrayList<ByteBuffer> decompressedData = decompress1(compressedData, valueSize);

        // 校验数据
    }
}
