package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.compress.fpc.FpcCompressor;
import com.alibaba.lindorm.contest.compress.doublecompress1.*;
import com.alibaba.lindorm.contest.compress.gorilla.*;
import com.alibaba.lindorm.contest.compress.gorilla.ByteBufferBitInput;
import com.alibaba.lindorm.contest.compress.gorilla.ByteBufferBitOutput;
import com.alibaba.lindorm.contest.util.Constants;
import com.github.luben.zstd.Zstd;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

public class DoubleCompress {

    public static boolean[] doubleDeltaFlag = new boolean[10];

    public static boolean[] corillaListFlag = new boolean[10];
    public static ArrayList<Integer> corillaList = new ArrayList<>();

    public static final ThreadLocal<double[]> TOTAL_THREAD_LOCAL = ThreadLocal.withInitial(() -> new double[Constants.FLOAT_NUMS * Constants.CACHE_VINS_LINE_NUMS]);

    static {
        doubleDeltaFlag[0] = true;
        doubleDeltaFlag[1] = true;
        doubleDeltaFlag[2] = true;
        doubleDeltaFlag[5] = true;
        doubleDeltaFlag[8] = true;

        corillaListFlag[3] = true;
        corillaListFlag[4] = true;
        corillaListFlag[6] = true;
        corillaListFlag[7] = true;

        corillaList.add(3);
//        corillaList.add(4);
        corillaList.add(6);
        corillaList.add(4);
        corillaList.add(7);
//        toLongCompress.add(3);
//        toLongCompress.add(4);
//        toLongCompress.add(7);
//        toLongCompress.add(9);
    }

    public static ByteBuffer encode3(double[] values, int start, int end) {
        FpcCompressor fpc = new FpcCompressor();
        ByteBuffer buffer = ByteBuffer.allocate((end - start) * 8 + 100);
        fpc.compress(buffer, values, start, end);
        buffer.flip();
        return buffer.slice();
    }

    public static double[] decode3(ByteBuffer byteBuffer, int valueSize) {
        FpcCompressor decompressor = new FpcCompressor();
        double[] dest = new double[valueSize];
        decompressor.decompress(byteBuffer, dest);
        return dest;
    }


    public static double[] decodeFromLong(ByteBuffer byteBuffer, int valueSize) {
        double firstDouble = byteBuffer.getDouble();
        double secondDouble = byteBuffer.getDouble();
        byte[] bytes = new byte[byteBuffer.array().length - 8 * 2 - 4];
        int anInt = byteBuffer.getInt();
        byteBuffer.get(bytes, 0, bytes.length);
        long[] longs = IntCompress.decompress2(bytes, anInt);
        double[] doubles = new double[valueSize];
        doubles[0] = firstDouble;
        doubles[1] = secondDouble;
        for (int i = 3; i < doubles.length; i++) {
            doubles[i] = 1.0 * longs[i] / 100000000000L;
        }
        DoubleDeltaDecompress(doubles, 0, valueSize);
        return doubles;
    }

    public static Map<Integer, double[]> getByLineNum(ByteBuffer byteBuffer, int valueSize, List<Integer> columnIndexList) {
        final Map<Integer, double[]> map = new HashMap<>(columnIndexList.size());
        final int compressLength = byteBuffer.getInt();
        byte[] array1 = new byte[byteBuffer.capacity() - 4];
        byteBuffer.get(array1);
        final byte[] decompress = Zstd.decompress(array1, compressLength);
        final ByteBuffer wrap = ByteBuffer.wrap(decompress);
        int total = 0;
        int line = 40;
        int count = 0;
        while (wrap.hasRemaining()) {
            final short aShort = wrap.getShort();
            total += 2;
            if (columnIndexList.contains(line)) {
                byte[] array = new byte[aShort];
                wrap.position(total);
                wrap.get(array);
                double[] doubles = decode3(ByteBuffer.wrap(array), valueSize);
                if (!doubleDeltaFlag[count]) {
                    DoubleDeltaDecompress(doubles, 0, doubles.length);
                }
                map.put(line, doubles);
            }
            count++;
            total += aShort;
            line++;
            wrap.position(total);
        }
        return map;
    }

    public static void DoubleDeltaCompress(double[] value, int start, int end) {
        int idx = start / (end - start);
        for (int i = end - 1; i >= start + 1; i--) {
            value[i] -= value[i - 1];
        }
        for (int i = end - 1; i >= start + 2; i--) {
            value[i] -= value[i - 1];
        }
        for (int i = end - 1; i >= start + 3; i--) {
            value[i] -= value[i - 1];
        }
    }

    public static void DoubleDeltaDecompress(double[] value, int start, int end) {
        for (int i = start + 3; i < end; i++) {
            value[i] += value[i - 1];
        }
        for (int i = start + 2; i < end; i++) {
            value[i] += value[i - 1];
        }
        for (int i = start + 1; i < end; i++) {
            value[i] += value[i - 1];
        }
    }

    public static ByteBuffer encodeCorilla(double[] values, int start, int end) {
        ByteBufferBitOutput output = new ByteBufferBitOutput();
        com.alibaba.lindorm.contest.compress.gorilla.Compressor compressor = new com.alibaba.lindorm.contest.compress.gorilla.Compressor(output);
        for (int i = start; i < end; i++) {
            compressor.addValue(values[i]);
        }
        compressor.close();
        ByteBuffer byteBuffer = output.getByteBuffer();
        int position = byteBuffer.position();
        byte[] bytes = new byte[position];
        byteBuffer.flip();
        byteBuffer.get(bytes);
        return ByteBuffer.wrap(bytes);
    }

    public static double[] decodeCorilla(ByteBuffer byteBuffer, int valueSize) {
        ByteBufferBitInput input = new ByteBufferBitInput(byteBuffer);
        Decompressor d = new Decompressor(input);
        return d.getValues(valueSize);
    }

    public static double P3 = 10000;
    public static double P4 = 155.68460000003;

    public static double P6 = -100000;
    public static double P7 = -35.044;

    public static void DeltaCompress(double[] value, int start, int end, double[] target) {
        double[] doubles = new double[end - start];
        double p = 0;
        int idx = start / (end - start);
        if (idx == 3) p = P3;
        else if (idx == 4) p = P4;
        else if (idx == 6) p = P6;
        else if (idx == 7) p = P7;
        for (int i = start; i < end - 1; i++) {
            double v = value[i + 1] - value[i];
            doubles[i - start] = v - p * target[i - start];
        }
        for (int i = start; i < end - 1; i++) {
            value[i] = doubles[i - start];
        }
    }

    public static double[] preProcess(double[] values, int valueSize) {
        int index = 6;
        int start = 9 * valueSize;
        int end = 10 * valueSize;
        double[] doubles = new double[valueSize - 1];
        for (int i = start; i < end - 1; i++) {
            doubles[i - start] = (values[i + 1] - values[i]);
        }
//        subColumnds(values, valueSize, 3, 20166);
//        addTwoColumnds(values, valueSize, firstIndex, secondIndex);
//        addTwoColumnds(values, valueSize, 4, 8);
//        addTwoColumnds(values, valueSize, 6, 8);
//        addTwoColumnds(values, valueSize, 7, 8);

        return doubles;
    }

    public static void recoverProcess(double[] values, int valueSize) {
        int index = 9;
        int start = index * valueSize;
        int end = (index + 1) * valueSize;
        double[] doubles = new double[valueSize - 1];
        for (int i = start; i < end - 1; i++) {
            doubles[i - start] = values[i + 1] - values[i];
        }
        for (int i = 0; i < 10; i++) {
            if (corillaListFlag[i]) {
                start = i * valueSize;
                end = (i + 1) * valueSize;
                double p = i == 3 ? 10000 : -100000;
                if (i == 3) p = P3;
                if (i == 4) p = P4;
                if (i == 6) p = P6;
                if (i == 7) p = P7;
                for (int j = end - 2; j >= start; j--) {
                    values[j] = values[j + 1] - (values[j] + doubles[j - start] * p);
                }
            }
        }
    }

    public static doubleCompressResult encode2(double[] values, int valueSize) throws IOException {
        double[] doubles = preProcess(values, valueSize);
        List<ByteBuffer> doubleDeltaBuffers = new ArrayList<>();
        List<ByteBuffer> corillaBuffers = new ArrayList<>();

        final int count = values.length / valueSize;
        int total = 0;
        for (int i = 0; i < count; i++) {
            ByteBuffer encode = null;
            if (doubleDeltaFlag[i]) {
                DoubleDeltaCompress(values, i * valueSize, (i + 1) * valueSize);
                for(int j=i*valueSize;j<(i+1)*valueSize;j++){
                    long longBits = Double.doubleToLongBits(values[j]);
                    String binaryString = Long.toBinaryString(longBits);
                    System.out.printf("%.15f : %s\n",values[j],binaryString);
                }
                System.out.println();
                encode = encode3(values, i * valueSize, (i + 1) * valueSize);
                doubleDeltaBuffers.add(encode);
            } else if (corillaListFlag[i]) {
                DeltaCompress(values, i * valueSize, (i + 1) * valueSize, doubles);
                encode = encodeCorilla(values, i * valueSize, (i + 1) * valueSize);
                corillaBuffers.add(encode);
            } else {
                encode = encodeCorilla(values, i * valueSize, (i + 1) * valueSize);
                corillaBuffers.add(encode);
            }
            total += encode.capacity();
        }
        ByteBuffer doubleDeltaBytes;
        ByteBuffer corillaBytes;
        // compress doubleDeltaBuffers
        {
            int length = 0;
            for (ByteBuffer doubleDeltaBuffer : doubleDeltaBuffers) {
                length += doubleDeltaBuffer.array().length;
            }
            ByteBuffer allocate = ByteBuffer.allocate(length + doubleDeltaBuffers.size() * 2);
            for (ByteBuffer doubleDeltaBuffer : doubleDeltaBuffers) {
                allocate.putShort((short) doubleDeltaBuffer.array().length);
                allocate.put(doubleDeltaBuffer.array());
            }
            byte[] compress = Zstd.compress(allocate.array(), 3);
            doubleDeltaBytes = ByteBuffer.allocate(compress.length + 4);
            doubleDeltaBytes.putInt(allocate.array().length);
            doubleDeltaBytes.put(compress);
        }
        {
            int length = 0;
            for (ByteBuffer corillaBuffer : corillaBuffers) {
                length = length + corillaBuffer.array().length;
            }
            ByteBuffer allocate = ByteBuffer.allocate(length + corillaBuffers.size() * 2);
            for (ByteBuffer buffer : corillaBuffers) {
                allocate.putShort((short) buffer.array().length);
                allocate.put(buffer.array());
            }
            byte[] compress = Zstd.compress(allocate.array(), 6);
            corillaBytes = ByteBuffer.allocate(compress.length + 4);
            corillaBytes.putInt(allocate.array().length);
            corillaBytes.put(compress);
        }
        ByteBuffer allocate = ByteBuffer.allocate(doubleDeltaBytes.array().length + corillaBytes.array().length);
        allocate.put(doubleDeltaBytes.array());
        allocate.put(corillaBytes.array());

        ByteBuffer headerBuffer = ByteBuffer.allocate(12);
        headerBuffer.putInt(doubleDeltaBytes.array().length);
        headerBuffer.putInt(corillaBytes.array().length);

        return new doubleCompressResult(allocate.array(), headerBuffer.array());
    }

    // 单列解压
    public static double[] decodeByIndex(ByteBuffer byteBuffer, int doubleNum, int valueSize, byte[] header, int index) {
        ByteBuffer headerWrap = ByteBuffer.wrap(header);
        int doubleDeltaBytesLength = headerWrap.getInt();
        int corillaBytesLength = headerWrap.getInt();

        int length = 0;
        if (doubleDeltaFlag[index]) {
            length = doubleDeltaBytesLength;
        } else {
            length = corillaBytesLength;
        }
        int totalLength = byteBuffer.getInt();
        byte[] bytes = new byte[length - 4];
        byteBuffer.get(bytes, 0, bytes.length);
        byte[] decompress = Zstd.decompress(bytes, totalLength);
        ByteBuffer wrap = ByteBuffer.wrap(decompress);
        double[] doubles = new double[valueSize * 10];
        for (int i = 0; i < 10; i++) {
            double[] decode = null;
            if (doubleDeltaFlag[i]) {
                if (!doubleDeltaFlag[index]) {
                    continue;
                }
                final int anInt = wrap.getShort();
                if (i != index) {
                    wrap.position(wrap.position() + anInt);
                    continue;
                }
                byte[] array = new byte[anInt];
                wrap.get(array);
                decode = decode3(ByteBuffer.wrap(array), valueSize);
                DoubleDeltaDecompress(decode, 0, decode.length);
            } else {
                if (doubleDeltaFlag[index]) {
                    continue;
                }
                if (corillaListFlag[i]) {
                    final int anInt = wrap.getShort();
                    byte[] array = new byte[anInt];
                    if (i != index) {
                        wrap.position(wrap.position() + anInt);
                        continue;
                    }
                    wrap.get(array);
                    decode = decodeCorilla(ByteBuffer.wrap(array), valueSize);
                } else {
                    final int anInt = wrap.getShort();
                    byte[] array = new byte[anInt];
                    wrap.get(array);
                    decode = decodeCorilla(ByteBuffer.wrap(array), valueSize);
                }
            }
            System.arraycopy(decode, 0, doubles, i * valueSize, decode.length);
        }
        recoverProcess(doubles, valueSize);
        double[] result = new double[valueSize];
        System.arraycopy(doubles, index * valueSize, result, 0, valueSize);
        return result;
    }

    public static double[] decode2ByColumns(ByteBuffer byteBuffer, int doubleNum, int valueSize, byte[] header, Set<Integer> columns) throws IOException {

        boolean isContainDoubleDelta = false;
        boolean isContainCorilla = false;
        for (Integer column : columns) {
            if (doubleDeltaFlag[column]) isContainDoubleDelta = true;
            else {
                isContainCorilla = true;
            }
        }
        ByteBuffer doubleDeltaBytes = null;
        ByteBuffer corillaBytes = null;
        double[] doubles;
        doubles = new double[doubleNum];
        ByteBuffer headerWrap = ByteBuffer.wrap(header);
        int doubleDeltaBytesLength = headerWrap.getInt();
        int corillaBytesLength = headerWrap.getInt();
        int totalLength = byteBuffer.getInt();
        byte[] bytes = new byte[doubleDeltaBytesLength - 4];
        byteBuffer.get(bytes, 0, bytes.length);
        if (isContainDoubleDelta) {
            byte[] decompress1 = Zstd.decompress(bytes, totalLength);
            doubleDeltaBytes = ByteBuffer.wrap(decompress1);
        }
        totalLength = byteBuffer.getInt();
        byte[] bytes1 = new byte[corillaBytesLength - 4];
        byteBuffer.get(bytes1, 0, bytes1.length);
        if(isContainCorilla){
            byte[] decompress2 = Zstd.decompress(bytes1, totalLength);
            corillaBytes = ByteBuffer.wrap(decompress2);
        }
        int start = 0;
        int count = 0;
        while ((doubleDeltaBytes != null && doubleDeltaBytes.hasRemaining()) || (corillaBytes != null && corillaBytes.hasRemaining())) {
            double[] decode = null;
            if (doubleDeltaFlag[count]) {
                if(!isContainDoubleDelta){
                    start += valueSize;
                    count++;
                    continue;
                }
                final int anInt = doubleDeltaBytes.getShort();
                if (!columns.contains(count)) {
                    doubleDeltaBytes.position(doubleDeltaBytes.position() + anInt);
                    start += valueSize;
                    count++;
                    continue;
                }
                byte[] array = new byte[anInt];
                doubleDeltaBytes.get(array);
                decode = decode3(ByteBuffer.wrap(array), valueSize);
                DoubleDeltaDecompress(decode, 0, decode.length);
            } else if (corillaList.contains(count)) {
                if(!isContainCorilla){
                    start += valueSize;
                    count++;
                    continue;
                }
                final int anInt = corillaBytes.getShort();
                if (!columns.contains(count)) {
                    corillaBytes.position(corillaBytes.position() + anInt);
                    start += valueSize;
                    count++;
                    continue;
                }
                byte[] array = new byte[anInt];
                corillaBytes.get(array);
                decode = decodeCorilla(ByteBuffer.wrap(array), valueSize);
            } else {
                if(!isContainCorilla){
                    start += valueSize;
                    count++;
                    continue;
                }
                final int anInt = corillaBytes.getShort();
                byte[] array = new byte[anInt];
                corillaBytes.get(array);
                decode = decodeCorilla(ByteBuffer.wrap(array), valueSize);
            }
            System.arraycopy(decode, 0, doubles, start, decode.length);
            start += valueSize;
            count++;
        }
        recoverProcess(doubles, valueSize);
        return doubles;
    }

    // 解压全部
    public static double[] decode2(ByteBuffer byteBuffer, int doubleNum, int valueSize, byte[] header) throws IOException {
        double[] doubles;
        doubles = new double[doubleNum];
        ByteBuffer headerWrap = ByteBuffer.wrap(header);
        int doubleDeltaBytesLength = headerWrap.getInt();
        int corillaBytesLength = headerWrap.getInt();
        int totalLength = byteBuffer.getInt();
        byte[] bytes = new byte[doubleDeltaBytesLength - 4];
        byteBuffer.get(bytes, 0, bytes.length);
        byte[] decompress1 = Zstd.decompress(bytes, totalLength);
        ByteBuffer doubleDeltaBytes = ByteBuffer.wrap(decompress1);

        totalLength = byteBuffer.getInt();
        byte[] bytes1 = new byte[corillaBytesLength - 4];
        byteBuffer.get(bytes1, 0, bytes1.length);
        byte[] decompress2 = Zstd.decompress(bytes1, totalLength);
        ByteBuffer corillaBytes = ByteBuffer.wrap(decompress2);

        int start = 0;
        int count = 0;
        while (doubleDeltaBytes.hasRemaining() || corillaBytes.hasRemaining()) {
            double[] decode = null;
            if (doubleDeltaFlag[count]) {
                final int anInt = doubleDeltaBytes.getShort();
                byte[] array = new byte[anInt];
                doubleDeltaBytes.get(array);
                decode = decode3(ByteBuffer.wrap(array), valueSize);
                DoubleDeltaDecompress(decode, 0, decode.length);
            } else if (corillaList.contains(count)) {
                final int anInt = corillaBytes.getShort();
                byte[] array = new byte[anInt];
                corillaBytes.get(array);
                decode = decodeCorilla(ByteBuffer.wrap(array), valueSize);
            } else {
                final int anInt = corillaBytes.getShort();
                byte[] array = new byte[anInt];
                corillaBytes.get(array);
                decode = decodeCorilla(ByteBuffer.wrap(array), valueSize);
            }
            System.arraycopy(decode, 0, doubles, start, decode.length);
            start += decode.length;
            count++;
        }
        recoverProcess(doubles, valueSize);
        return doubles;
    }

    public static void main(String[] args) throws IOException {
        double[] values = new double[]{
                261542.7413603405, 261535.9541321445, 261529.16890449845, 261522.38567745395, 261515.60445106262, 261508.82522537635, 261502.04800044652, 261495.27277632477, 261488.49955306278, 261481.72833071218, 261474.95910932432, 261468.191888951, 261461.42666964355, 261454.66345145367, 261447.9022344327, 261441.14301863225, 261434.38580410375, 261427.63059089863, 261420.87737906835, 261414.12616866443, 261407.3769597381, 261400.6297523409, 261393.8845465242, 261387.14134233928, 261380.40013983764, 261373.66093907045, 261366.92374008917, 261360.18854294508, 261353.4553476894, 261346.72415437346, 261339.9949630485, 261333.26777376572, 261326.54258657648, 261319.8194015319, 261313.0982186832, 261306.37903808156, 261299.66185977816, 261292.94668382418, 261286.23351027071, 261279.52233916888, 261272.81317056983, 261266.10600452463, 261259.40084108437, 261252.69768030004, 261245.99652222282, 261239.2973669037, 261232.60021439358, 261225.9050647436, 261219.21191800467, 261212.52077422786, 261205.83163346403, 261199.1444957641, 261192.4593611791, 261185.77622975985, 261179.09510155726, 261172.41597662232, 261165.7388550057, 261159.06373675837, 261152.39062193118, 261145.71951057488, 261139.05040274037, 261132.38329847832, 261125.71819783957, 261119.05510087486, 261112.39400763495, 261105.73491817055, 261099.07783253235, 261092.42275077116, 261085.76967293746, 261079.1185990821, 261072.46952925564, 261065.82246350875, 261059.17740189208, 261052.53434445616, 261045.89329125162, 261039.25424232899, 261032.6171977389, 261025.98215753183, 261019.34912175837, 261012.71809046902, 261006.08906371426, 260999.4620415445, 260992.83702401037, 260986.21401116217, 260979.59300305042, 260972.9739997256, 260966.35700123792, 260959.74200763798, 260953.129018976, 260946.51803530246, 260939.90905666765, 260933.30208312185, 260926.69711471547, 260920.09415149875, 260913.49319352204, 260906.8942408355, 260900.29729348945, 260893.70235153416, 260887.1094150198, 260880.51848399662, 260873.9295585148, 260867.34263862448, 260860.75772437587, 260854.17481581902, 260847.5939130042, 260841.01501598145, 260834.43812480092, 260827.8632395126, 260821.2903601667, 260814.71948681312, 260808.150619502, 260801.58375828335, 260795.01890320715, 260788.45605432347, 260781.8952116822, 260775.3363753333, 260768.77954532683, 260762.22472171258, 260755.67190454056, 260749.1210938607, 260742.5722897228, 260736.02549217676, 260729.48070127243, 260722.9379170597, 260716.39713958837, 260709.85836890823, 260703.32160506904, 260696.78684812074, 260690.25409811293, 260683.72335509537, 260677.1946191179, 260670.66789023014, 260664.14316848188, 260657.62045392272, 260651.09974660235, 260644.5810465705, 260638.06435387675, 260631.54966857075, 260625.03699070215, 260618.52632032038, 260612.01765747525, 260605.5110022162, 260599.0063545928, 260592.50371465454, 260586.003082451, 260579.50445803173, 260573.0078414461, 260566.51323274366, 260560.02063197386, 260553.53003918618, -2339172.772724608, -2339104.9004426477, -2339037.048166187, -2338969.2158957417, -2338901.403631829, -2338833.6113749663, -2338765.8391256677, -2338698.08688445, -2338630.3546518306, -2338562.642428324, -2338494.950214446, -2338427.2780107125, -2338359.6258176384, -2338291.993635739, -2338224.3814655296, -2338156.7893075254, -2338089.2171622403, -2338021.665030189, -2337954.1329118866, -2337886.620807847, -2337819.1287185834, -2337751.6566446116, -2337684.2045864444, -2337616.772544596, -2337549.360519579, -2337481.9685119074, -2337414.5965220947, -2337347.2445506533, -2337279.9125980963, -2337212.600664937, -2337145.3087516874, -2337078.0368588604, -2337010.7849869677, -2336943.553136522, -2336876.3413080345, -2336809.1495020185, -2336741.9777189847, -2336674.8259594445, -2336607.69422391, -2336540.582512892, -2336473.490826901, -2336406.419166449, -2336339.3675320465, -2336272.3359242035, -2336205.324343431, -2336138.3327902392, -2336071.3612651387, -2336004.4097686387, -2335937.4783012494, -2335870.5668634814, -2335803.6754558426, -2335736.804078844, -2335669.9527329938, -2335603.121418801, -2335536.3101367755, -2335469.518887426, -2335402.7476712596, -2335335.996488787, -2335269.2653405145, -2335202.5542269517, -2335135.8631486064, -2335069.1921059857, -2335002.541099598, -2334935.9101299513, -2334869.2991975523, -2334802.708302908, -2334736.1374465264, -2334669.5866289143, -2334603.0558505775, -2334536.545112024, -2334470.0544137596, -2334403.5837562904, -2334337.1331401234, -2334270.7025657645, -2334204.292033719, -2334137.9015444927, -2334071.5310985916, -2334005.180696521, -2333938.8503387864, -2333872.540025893, -2333806.249758345, -2333739.979536648, -2333673.7293613064, -2333607.4992328244, -2333541.2891517067, -2333475.0991184586, -2333408.929133582, -2333342.7791975825, -2333276.6493109628, -2333210.5394742275, -2333144.449687879, -2333078.3799524214, -2333012.3302683574, -2332946.3006361905, -2332880.291056423, -2332814.301529558, -2332748.3320560977, -2332682.3826365443, -2332616.4532714006, -2332550.5439611687, -2332484.6547063505, -2332418.785507447, -2332352.9363649613, -2332287.107279393, -2332221.2982512447, -2332155.5092810174, -2332089.7403692114, -2332023.991516329, -2331958.2627228694, -2331892.553989334, -2331826.865316223, -2331761.1967040366, -2331695.548153274, -2331629.9196644374, -2331564.311238025, -2331498.7228745357, -2331433.154574471, -2331367.6063383287, -2331302.0781666087, -2331236.57005981, -2331171.082018431, -2331105.6140429704, -2331040.166133927, -2330974.7382918, -2330909.3305170867, -2330843.942810285, -2330778.5751718935, -2330713.22760241, -2330647.900102332, -2330582.5926721566, -2330517.3053123816, -2330452.038023504, -2330386.7908060215, -2330321.56366043, -2330256.3565872265, -2330191.1695869076, -2330126.0026599704, -2330060.8558069104, -2329995.729028224, -2329930.6223244066, -2329865.535695955, -2329800.4691433646, -2329735.4226671304, -2329670.396267748, -2329605.389945713, -2329540.40370152, -2329475.4375356636, -2329410.4914486394, -2329345.5654409416, -2329280.659513064, 20166.326087987112, 20166.319300758918, 20166.31251553127, 20166.305732304227, 20166.298951077835, 20166.29217185215, 20166.28539462722, 20166.278619403096, 20166.271846179836, 20166.265074957486, 20166.258305736097, 20166.251538515724, 20166.244773296417, 20166.238010078225, 20166.231248861204, 20166.224489645407, 20166.217732430876, 20166.21097721767, 20166.20422400584, 20166.197472795437, 20166.19072358651, 20166.183976379114, 20166.177231173297, 20166.170487969113, 20166.16374676661, 20166.157007565842, 20166.150270366863, 20166.14353516972, 20166.136801974462, 20166.130070781146, 20166.12334158982, 20166.11661440054, 20166.10988921335, 20166.103166028304, 20166.096444845454, 20166.089725664853, 20166.083008486552, 20166.076293310598, 20166.069580137042, 20166.062868965942, 20166.056159797343, 20166.049452631298, 20166.042747467858, 20166.03604430707, 20166.029343148995, 20166.022643993678, 20166.015946841166, 20166.009251691517, 20166.002558544777, 20165.995867401, 20165.989178260235, 20165.982491122537, 20165.975805987953, 20165.96912285653, 20165.96244172833, 20165.955762603397, 20165.949085481778, 20165.942410363532, 20165.935737248703, 20165.92906613735, 20165.922397029513, 20165.91572992525, 20165.909064824613, 20165.902401727646, 20165.89574063441, 20165.889081544945, 20165.882424459305, 20165.875769377544, 20165.86911629971, 20165.862465225855, 20165.85581615603, 20165.84916909028, 20165.842524028663, 20165.83588097123, 20165.829239918025, 20165.8226008691, 20165.815963824512, 20165.809328784304, 20165.80269574853, 20165.79606471724, 20165.789435690487, 20165.78280866832, 20165.77618365078, 20165.769560637935, 20165.76293962982, 20165.756320626497, 20165.74970362801, 20165.743088634412, 20165.73647564575, 20165.729864662077, 20165.72325568344, 20165.716648709895, 20165.71004374149, 20165.70344077827, 20165.696839820295, 20165.690240867607, 20165.683643920263, 20165.67704897831, 20165.670456041793, 20165.663865110768, 20165.657276185288, 20165.650689265396, 20165.644104351148, 20165.637521442593, 20165.630940539777, 20165.624361642753, 20165.617784751572, 20165.611209866285, 20165.60463698694, 20165.598066113587, 20165.591497246274, 20165.584930385055, 20165.57836552998, 20165.571802681097, 20165.565241838456, 20165.558683002106, 20165.5521261721, 20165.545571348484, 20165.53901853131, 20165.532467720634, 20165.525918916497, 20165.51937211895, 20165.512827328046, 20165.506284543833, 20165.499743766362, 20165.49320499568, 20165.486668231842, 20165.48013347489, 20165.473600724887, 20165.467069981867, 20165.46054124589, 20165.454014517003, 20165.447489795253, 20165.440967080696, 20165.434446373376, 20165.427927673343, 20165.42141098065, 20165.414896295344, 20165.408383617476, 20165.40187294709, 20165.395364284246, 20165.38885762899, 20165.382352981367, 20165.37585034143, 20165.369349709224, 20165.362851084803, 20165.35635446822, 20165.349859859518, 20165.343367258745, 20165.33687666596, 5373016.158294049, 5373114.671098222, 5372601.409531063, 5372551.526492445, 5372130.631897357, 5372081.616075404, 5372215.92885103, 5372201.558228856, 5372300.630340398, 5372323.214599987, 5372421.727333839, 5372444.206711049, 5373134.268186634, 5373190.702601103, 5373141.686712977, 5373275.999520638, 5372725.765099728, 5372824.855253342, 5372810.484633404, 5372909.286078603, 5372931.563581858, 5372439.086951565, 5372460.8050791295, 5372595.406564781, 5372583.32648789, 5372605.621967063, 5372704.423481332, 5372727.296381053, 5372826.079850883, 5372809.182950072, 5373499.26253825, 5372913.246053629, 5373082.763470863, 5373033.747582632, 5373203.860466437, 5373191.7983627515, 5373214.653288135, 5373313.454731882, 5373336.038991575, 5372879.073691057, 5372793.04998403, 5372962.585379323, 5372913.569557476, 5373083.682373315, 5373034.648509164, 5372519.147254489, 5372618.23857102, 5372640.822760462, 5373366.395636093, 5373317.379747999, 5373487.781339532, 5373400.944441683, 5373571.327988228, 5373522.023390264, 5373692.424983802, 5373643.697837849, 5373667.416639741, 5373246.541247672, 5373197.236649539, 5372794.837606955, 5372744.937542806, 5372913.101634158, 5372826.842154002, 5372997.2437478425, 5372984.009855034, 5373674.378153241, 5373696.962344678, 5373684.611599966, 5373854.995058334, 5373806.014199629, 5373975.549592756, 5373926.533773078, 5374060.82850212, 5373473.657245794, 5372981.469325467, 5373004.053583059, 5373102.83698502, 5373122.895030258, 5373110.832929224, 5373280.368393071, 5373231.33445836, 5373365.647302302, 5373388.231491569, 5374041.356202152, 5374064.2110585645, 5374163.01257073, 5374185.308052005, 5374284.109564176, 5373733.875074947, 5373756.746952003, 5373855.548395855, 5373877.843945445, 5373385.349270821, 5373405.984736467, 5373467.831302562, 5373490.415526009, 5373588.910285837, 5373575.694437373, 5373745.22989905, 5373696.179049841, 5373755.410340325, 5374445.489861826, 5373895.273485228, 5373994.363638977, 5374016.929854357, 5374114.287747983, 5374065.27192827, 5374234.807323562, 5374185.773457278, 5373762.6594046205, 5373713.353780115, 5373772.892851314, 5373871.96503114, 5373894.5492203105, 5374029.150774347, 5373979.846173983, 5373540.474207298, 5373490.59218865, 5374252.271856363, 5374202.967258268, 5374373.350805497, 5374361.288704496, 5374348.361634548, 5374518.7631577, 5374469.440583551, 5374637.604604899, 5374587.722655062, 5374129.891224549, 5374080.752475331, 5374250.865290619, 5382829.659052879, 5382928.749208765, 5382988.269211243, 5382939.253321144, 5383109.366208538, 5383060.350352585, 5383821.145779706, 5383772.129959895, 5383869.487853617, 5383892.0721133435, 5383990.855512619, 5384013.728482473, 5384110.292426256, 5384169.541761213, 5384120.507828637, 5383125.964577753, 5383076.948689618, 5383211.550243557, 5383233.827678391, 5383295.67431293, 153228.5241464057, 153230.05783905694, 153222.06714687907, 153221.29054478768, 153214.73786411984, 153213.97476325638, 153216.06580633123, 153215.84207787472, 153217.38447808038, 153217.7360802224, 153219.2697717789, 153219.61974106383, 153230.36293554402, 153231.24153246827, 153230.4784305746, 153232.56947414816, 153224.0031715756, 153225.54585266855, 153225.3221242469, 153226.8603105944, 153227.2071370127, 153219.54003429308, 153219.87815209336, 153221.97368993866, 153221.78562174478, 153222.13272802046, 153223.67091544328, 153224.02701126767, 153225.5649177664, 153225.301859042, 153236.04533550734, 153226.92196130718, 153229.5610864367, 153228.79798454142, 153231.44638016843, 153231.2585917897, 153231.6144077813, 153233.1525941062, 153233.50419624988, 153226.3899502474, 153225.0506936055, 153227.6900986257, 153226.9269977639, 153229.57539233277, 153228.8120105801, 153220.78644991675, 153222.3291491145, 153222.68075016447, 153233.97680245579, 153233.21370056263, 153235.86659092436, 153234.51467415367, 153237.16728358273, 153236.3996869215, 153239.05257731443, 153238.29397069174, 153238.66323591024, 153232.11085420387, 153231.34325754, 153225.0785241415, 153224.3016569888, 153226.91971291843, 153225.576785652, 153228.22967604964, 153228.0236447188, 153238.77161595473, 153239.12321703573, 153238.9309349607, 153241.583543017, 153240.82098647748, 153243.46039146394, 153242.69729063593, 153244.78805275323, 153235.64670053596, 153227.9840925859, 153228.33569469684, 153229.8736001389, 153230.18587301386, 153229.99808467642, 153232.63749076388, 153231.87410791274, 153233.9651520511, 153234.31675309836, 153244.48489903007, 153244.84071394792, 153246.37890133803, 153246.72600764644, 153248.2641950366, 153239.69789140043, 153240.0539713035, 153241.59215763002, 153241.93926500203, 153234.27188135992, 153234.5931437814, 153235.5559995718, 153235.90760115115, 153237.44101287975, 153237.2352624716, 153239.87466852527, 153239.1110223414, 153240.03316231805, 153250.77663774535, 153242.21061609493, 153243.75329718998, 153244.10461841148, 153245.6203308841, 153244.8572300555, 153247.4966350757, 153246.73325328977, 153240.14601908557, 153239.3784064427, 153240.30533809107, 153241.8477393598, 153242.19934040552, 153244.2948793155, 153243.5272826169, 153236.68693772846, 153235.91035151642, 153247.76853095603, 153247.00093429277, 153249.65354373245, 153249.4657553955, 153249.2645008241, 153251.91739012126, 153251.1495135985, 153253.76756843828, 153252.99098329755, 153245.86325298485, 153245.09823833322, 153247.7466328935, 153381.30524042947, 153382.84792155778, 153383.7745563356, 153383.0114544112, 153385.6598500941, 153384.8967487013, 153396.74116187662, 153395.97806104654, 153397.49377352063, 153397.84537566482, 153399.38328106512, 153399.73937798134, 153401.24272987756, 153402.1651507789, 153401.40176796092, 153385.91826114128, 153385.1551592475, 153387.25069815593, 153387.59752350903, 153388.560380365, -18759.216970325448, -18758.538247505847, -18757.85972474124, -18757.18140203679, -18756.50327939766, -18755.82535682903, -18755.14763433605, -18754.470111923874, -18753.792789597675, -18753.115667362614, -18752.43874522383, -18751.762023186497, -18751.085501255755, -18750.409179436763, -18749.73305773467, -18749.05713615462, -18748.381414701773, -18747.70589338126, -18747.030572198233, -18746.355451157837, -18745.680530265206, -18745.00580952549, -18744.331288943817, -18743.656968525327, -18742.98284827516, -18742.308928198447, -18741.635208300315, -18740.961688585903, -18740.288369060334, -18739.61524972874, -18738.942330596245, -18738.269611667973, -18737.597092949047, -18736.92477444459, -18736.25265615972, -18735.580738099557, -18734.909020269217, -18734.237502673815, -18733.56618531847, -18732.895068208287, -18732.22415134838, -18731.55343474386, -18730.88291839983, -18730.212602321404, -18729.542486513677, -18728.872570981763, -18728.202855730757, -18727.533340765756, -18726.864026091866, -18726.194911714185, -18725.525997637797, -18724.85728386781, -18724.188770409306, -18723.520457267383, -18722.852344447125, -18722.18443195363, -18721.516719791965, -18720.849207967236, -18720.181896484515, -18719.514785348885, -18718.847874565432, -18718.181164139227, -18717.514654075356, -18716.848344378883, -18716.182235054894, -18715.516326108453, -18714.850617544635, -18714.18510936851, -18713.519801585146, -18712.85469419961, -18712.189787216965, -18711.525080642277, -18710.860574480605, -18710.196268737014, -18709.53216341656, -18708.868258524297, -18708.204554065287, -18707.541050044583, -18706.877746467235, -18706.2146433383, -18705.55174066282, -18704.88903844585, -18704.226536692433, -18703.564235407615, -18702.90213459644, -18702.240234263954, -18701.57853441519, -18700.917035055194, -18700.255736189, -18699.594637821643, -18698.93373995816, -18698.273042603585, -18697.612545762946, -18696.952249441274, -18696.2921536436, -18695.63225837495, -18694.972563640345, -18694.313069444815, -18693.65377579338, -18692.99468269106, -18692.33579014288, -18691.677098153843, -18691.018606728983, -18690.360315873302, -18689.70222559182, -18689.044335889543, -18688.386646771487, -18687.72915824266, -18687.071870308064, -18686.414782972708, -18685.757896241597, -18685.101210119734, -18684.444724612116, -18683.788439723743, -18683.132355459617, -18682.476471824728, -18681.82078882408, -18681.16530646266, -18680.510024745457, -18679.854943677467, -18679.200063263677, -18678.545383509074, -18677.890904418644, -18677.236625997368, -18676.582548250237, -18675.92867118222, -18675.274994798303, -18674.62151910347, -18673.96824410269, -18673.315169800935, -18672.662296203187, -18672.00962331441, -18671.357151139586, -18670.704879683668, -18670.052808951634, -18669.40093894845, -18668.749269679072, -18668.097801148473, -18667.44653336161, -18666.795466323438, -18666.144600038922, -18665.493934513015, -18664.843469750675, -18664.193205756852, -18663.543142536502, -18662.89328009457, -18662.24361843601, -18661.594157565763, -18660.944897488786, -18660.295838210015, 5.143527040061779E7, 5.1434285272576064E7, 5.143941788824766E7, 5.1439916718633845E7, 5.1444125664584726E7, 5.144461582280425E7, 5.144327269504799E7, 5.144341640126973E7, 5.144242568015431E7, 5.144219983755843E7, 5.144121471021989E7, 5.1440989916447796E7, 5.143408930169194E7, 5.143352495754727E7, 5.143401511642853E7, 5.143267198835192E7, 5.1438174332561016E7, 5.1437183431024864E7, 5.143732713722425E7, 5.1436339122772254E7, 5.143611634773972E7, 5.144104111404264E7, 5.144082393276699E7, 5.1439477917910464E7, 5.1439598718679376E7, 5.143937576388765E7, 5.143838774874497E7, 5.1438159019747764E7, 5.143717118504945E7, 5.143734015405756E7, 5.143043935817579E7, 5.143629952302201E7, 5.143460434884966E7, 5.143509450773197E7, 5.1433393378893904E7, 5.143351399993077E7, 5.143328545067693E7, 5.143229743623948E7, 5.143207159364255E7, 5.1436641246647716E7, 5.1437501483717985E7, 5.1435806129765056E7, 5.143629628798354E7, 5.1434595159825146E7, 5.1435085498466656E7, 5.144024051101339E7, 5.143924959784809E7, 5.143902375595367E7, 5.143176802719736E7, 5.14322581860783E7, 5.143055417016297E7, 5.1431422539141454E7, 5.142971870367601E7, 5.143021174965564E7, 5.142850773372026E7, 5.142899500517981E7, 5.142875781716088E7, 5.143296657108156E7, 5.143345961706289E7, 5.143748360748873E7, 5.1437982608130224E7, 5.1436300967216715E7, 5.143716356201828E7, 5.143545954607986E7, 5.143559188500795E7, 5.142868820202587E7, 5.142846236011151E7, 5.1428585867558636E7, 5.142688203297495E7, 5.1427371841561995E7, 5.142567648763073E7, 5.142616664582751E7, 5.142482369853709E7, 5.143069541110036E7, 5.143561729030361E7, 5.1435391447727695E7, 5.143440361370809E7, 5.143420303325572E7, 5.143432365426605E7, 5.143262829962759E7, 5.143311863897468E7, 5.143177551053527E7, 5.1431549668642595E7, 5.1425018421536766E7, 5.142478987297265E7, 5.142380185785098E7, 5.142357890303823E7, 5.142259088791653E7, 5.142809323280881E7, 5.142786451403826E7, 5.142687649959975E7, 5.142665354410384E7, 5.143157849085008E7, 5.143137213619362E7, 5.1430753670532666E7, 5.143052782829821E7, 5.1429542880699925E7, 5.142967503918455E7, 5.142797968456779E7, 5.142847019305987E7, 5.1427877880155034E7, 5.142097708494003E7, 5.142647924870601E7, 5.142548834716852E7, 5.142526268501472E7, 5.142428910607845E7, 5.1424779264275596E7, 5.142308391032267E7, 5.142357424898552E7, 5.142780538951208E7, 5.1428298445757136E7, 5.142770305504514E7, 5.14267123332469E7, 5.142648649135519E7, 5.142514047581482E7, 5.142563352181846E7, 5.1430027241485305E7, 5.1430526061671786E7, 5.142290926499465E7, 5.142340231097562E7, 5.142169847550332E7, 5.142181909651333E7, 5.142194836721281E7, 5.142024435198128E7, 5.142073757772277E7, 5.1419055937509306E7, 5.141955475700767E7, 5.142413307131279E7, 5.142462445880498E7, 5.142292333065209E7, 5.13371353930295E7, 5.133614449147064E7, 5.1335549291445866E7, 5.133603945034684E7, 5.1334338321472906E7, 5.133482848003243E7, 5.132722052576124E7, 5.132771068395934E7, 5.132673710502211E7, 5.132651126242486E7, 5.132552342843209E7, 5.132529469873357E7, 5.1324329059295736E7, 5.132373656594616E7, 5.132422690527192E7, 5.133417233778076E7, 5.13346624966621E7, 5.133331648112271E7, 5.133309370677439E7, 5.1332475240428984E7, 9774.413338033399, 9774.068109762455, 9775.866783598407, 9776.041593718943, 9777.516576737968, 9777.68834778442, 9777.217662093517, 9777.268022501861, 9776.920834194174, 9776.841689914874, 9776.496461890361, 9776.417685160864, 9773.999433725823, 9773.801664963765, 9773.973436242113, 9773.502750438944, 9775.430991943582, 9775.083740409253, 9775.134100809766, 9774.787861025208, 9774.709791742807, 9776.435626846003, 9776.359517839763, 9775.887820393444, 9775.930153814901, 9775.852021537687, 9775.505781511085, 9775.425625721307, 9775.079448929631, 9775.138662428832, 9772.720347520024, 9774.773983688732, 9774.179926851773, 9774.351698130493, 9773.75555454048, 9773.797824976637, 9773.717732176123, 9773.37149239666, 9773.292348116991, 9774.893737316126, 9775.195198795029, 9774.601078955766, 9774.772850001851, 9774.176706650022, 9774.348540923553, 9776.155063520433, 9775.807807910785, 9775.728663877304, 9773.185966291943, 9773.357737570177, 9772.760582232811, 9773.064893457633, 9772.467801357121, 9772.640584390221, 9772.043429045827, 9772.21418845611, 9772.13106828676, 9773.605984010723, 9773.77876704442, 9775.18893424925, 9775.363804034056, 9774.774489792324, 9775.076777514583, 9774.479622169129, 9774.525999023084, 9772.106672358846, 9772.027528318376, 9772.070810268146, 9771.473718476638, 9771.645366997886, 9771.051247166213, 9771.223018204691, 9770.752395756235, 9772.810078706905, 9774.534902054898, 9774.455757782593, 9774.109581228762, 9774.039289815033, 9774.081560241895, 9773.48744016239, 9773.659274683188, 9773.188588752879, 9773.10944472001, 9770.820634484246, 9770.740541925436, 9770.394301906199, 9770.31616962162, 9769.969929602366, 9771.898171346418, 9771.818019140464, 9771.471779360632, 9771.39364683665, 9773.119545174402, 9773.04723024859, 9772.830495142367, 9772.751350989724, 9772.40618595338, 9772.452499572735, 9771.858379500836, 9772.0302732968, 9771.82270316243, 9769.404388487283, 9771.332566757432, 9770.985315222639, 9770.906234177455, 9770.565053175033, 9770.73682421364, 9770.142704374375, 9770.314538655384, 9771.797299541515, 9771.970086172032, 9771.76143745092, 9771.414248903944, 9771.33510487141, 9770.863407185447, 9771.036190226961, 9772.575925347011, 9772.75073189316, 9770.081501665625, 9770.254284699193, 9769.657192596283, 9769.699463023033, 9769.744764646955, 9769.147609549218, 9769.320455578067, 9768.731141581658, 9768.905947886667, 9770.510372351755, 9770.682574184515, 9770.086430834617, 9740.022905974158, 9739.67565443187, 9739.467072535186, 9739.638843820449, 9739.042700217864, 9739.214471383464, 9736.548339888666, 9736.72011092761, 9736.37892992485, 9736.299785645066, 9735.953609100641, 9735.87345306509, 9735.535054380496, 9735.32742101107, 9735.499255524392, 9738.984532892788, 9739.156304171167, 9738.684606485542, 9738.606537442916, 9738.389802096835, 2.3741803186107885E7, 2.3741124463288285E7, 2.3740445940523677E7, 2.3739767617819227E7, 2.37390894951801E7, 2.373841157261147E7, 2.3737733850118484E7, 2.373705632770631E7, 2.3736379005380113E7, 2.373570188314505E7, 2.373502496100627E7, 2.3734348238968935E7, 2.373367171703819E7, 2.37329953952192E7, 2.3732319273517106E7, 2.373164335193706E7, 2.373096763048421E7, 2.3730292109163698E7, 2.3729616787980672E7, 2.3728941666940276E7, 2.3728266746047642E7, 2.3727592025307927E7, 2.3726917504726253E7, 2.3726243184307765E7, 2.3725569064057596E7, 2.3724895143980883E7, 2.3724221424082752E7, 2.372354790436834E7, 2.372287458484277E7, 2.372220146551118E7, 2.3721528546378683E7, 2.372085582745041E7, 2.3720183308731485E7, 2.3719510990227025E7, 2.3718838871942155E7, 2.3718166953881994E7, 2.3717495236051653E7, 2.3716823718456253E7, 2.3716152401100907E7, 2.3715481283990726E7, 2.3714810367130816E7, 2.3714139650526296E7, 2.371346913418227E7, 2.3712798818103842E7, 2.3712128702296115E7, 2.37114587867642E7, 2.3710789071513195E7, 2.3710119556548197E7, 2.3709450241874304E7, 2.3708781127496623E7, 2.3708112213420235E7, 2.3707443499650247E7, 2.3706774986191746E7, 2.370610667304982E7, 2.3705438560229562E7, 2.3704770647736065E7, 2.3704102935574405E7, 2.3703435423749674E7, 2.370276811226695E7, 2.3702101001131326E7, 2.370143409034787E7, 2.3700767379921667E7, 2.370010086985779E7, 2.369943456016132E7, 2.369876845083733E7, 2.369810254189089E7, 2.3697436833327074E7, 2.3696771325150948E7, 2.3696106017367583E7, 2.3695440909982048E7, 2.3694776002999403E7, 2.3694111296424713E7, 2.369344679026304E7, 2.369278248451945E7, 2.3692118379198994E7, 2.3691454474306732E7, 2.3690790769847725E7, 2.369012726582702E7, 2.3689463962249674E7, 2.3688800859120738E7, 2.3688137956445258E7, 2.3687475254228286E7, 2.368681275247487E7, 2.3686150451190054E7, 2.368548835037888E7, 2.3684826450046394E7, 2.3684164750197627E7, 2.368350325083763E7, 2.3682841951971438E7, 2.3682180853604082E7, 2.3681519955740597E7, 2.368085925838602E7, 2.3680198761545382E7, 2.367953846522371E7, 2.3678878369426038E7, 2.3678218474157386E7, 2.3677558779422782E7, 2.3676899285227254E7, 2.3676239991575815E7, 2.3675580898473497E7, 2.3674922005925316E7, 2.3674263313936282E7, 2.367360482251142E7, 2.367294653165574E7, 2.3672288441374257E7, 2.367163055167198E7, 2.3670972862553924E7, 2.36703153740251E7, 2.36696580860905E7, 2.3669000998755146E7, 2.3668344112024035E7, 2.3667687425902173E7, 2.3667030940394554E7, 2.3666374655506182E7, 2.3665718571242053E7, 2.3665062687607165E7, 2.3664407004606515E7, 2.3663751522245094E7, 2.3663096240527894E7, 2.3662441159459904E7, 2.3661786279046115E7, 2.366113159929151E7, 2.366047712020108E7, 2.3659822841779806E7, 2.3659168764032673E7, 2.3658514886964656E7, 2.3657861210580744E7, 2.365720773488591E7, 2.3656554459885124E7, 2.3655901385583375E7, 2.3655248511985626E7, 2.3654595839096848E7, 2.365394336692202E7, 2.3653291095466107E7, 2.3652639024734072E7, 2.3651987154730886E7, 2.365133548546151E7, 2.365068401693091E7, 2.3650032749144047E7, 2.3649381682105877E7, 2.364873081582136E7, 2.3648080150295455E7, 2.3647429685533114E7, 2.364677942153929E7, 2.364612935831894E7, 2.3645479495877005E7, 2.3644829834218446E7, 2.3644180373348203E7, 2.3643531113271225E7, 2.364288205399245E7, 9965.279885366968, 9965.289736647386, 9965.23841049067, 9965.233422186808, 9965.1913327273, 9965.186431145105, 9965.199862422667, 9965.19842536045, 9965.208332571603, 9965.210590997562, 9965.220442270947, 9965.222690208668, 9965.291696356227, 9965.297339797675, 9965.29243820886, 9965.305869489628, 9965.250846047536, 9965.260755062898, 9965.259318000904, 9965.269198145425, 9965.27142589575, 9965.22217823272, 9965.224350045477, 9965.237810194041, 9965.236602186353, 9965.23883173427, 9965.248711885697, 9965.250999175669, 9965.260877522653, 9965.259187832571, 9965.328195791388, 9965.269594142927, 9965.28654588465, 9965.281644295826, 9965.298655584207, 9965.297449373838, 9965.299734866378, 9965.309615010752, 9965.311873436722, 9965.266176906669, 9965.257574535966, 9965.274528075495, 9965.269626493311, 9965.286637774896, 9965.28173438848, 9965.230184263013, 9965.240093394666, 9965.24235181361, 9965.314909101173, 9965.310007512364, 9965.327047671517, 9965.318363981733, 9965.335402336386, 9965.33047187659, 9965.347512035944, 9965.342639321349, 9965.345011201538, 9965.302923662332, 9965.297993202517, 9965.25775329826, 9965.252763291845, 9965.26957970098, 9965.260953752964, 9965.277993912348, 9965.276670523068, 9965.345707352888, 9965.34796577203, 9965.34673069756, 9965.363769043397, 9965.358870957527, 9965.37582449684, 9965.370922914872, 9965.384352387775, 9965.325635262143, 9965.276416470111, 9965.27867489587, 9965.288553236065, 9965.29055904059, 9965.289352830487, 9965.306306376871, 9965.3014029834, 9965.314834267794, 9965.31709268672, 9965.38240515778, 9965.38469064342, 9965.394570794637, 9965.396800342764, 9965.406680493981, 9965.35165704506, 9965.353944232764, 9965.36382437715, 9965.366053932108, 9965.316804464646, 9965.31886801121, 9965.32505266782, 9965.327311090165, 9965.337160566147, 9965.3358389813, 9965.352792527468, 9965.347887442547, 9965.353810571596, 9965.422818523746, 9965.367796886087, 9965.377705901461, 9965.379962522999, 9965.389698312361, 9965.38479673039, 9965.40175026992, 9965.396846883292, 9965.354535478025, 9965.349604915575, 9965.355558822695, 9965.365466040677, 9965.367724459595, 9965.381184614998, 9965.376254154962, 9965.332316958293, 9965.327328756428, 9965.4034967232, 9965.39856626339, 9965.415604618112, 9965.414398408013, 9965.413105701018, 9965.430145853334, 9965.42521359592, 9965.442029998054, 9965.43704180307, 9965.391258660018, 9965.386344785096, 9965.403356066625, 9966.261235442851, 9966.27114445844, 9966.277096458687, 9966.272194869678, 9966.289206158417, 9966.284304572822, 9966.360384115535, 9966.355482533552, 9966.365218322926, 9966.367476748897, 9966.377355088825, 9966.379642385811, 9966.38929878019, 9966.395223713685, 9966.390320320428, 9966.29086599534, 9966.285964406525, 9966.29942456192, 9966.301652305403, 9966.307836968857};
        double[] data = values.clone();
        double avg = 0;
        int index = 0;
        for (int i = 0; i < values.length; i += 150) {
            for (int j = i; j < i + 150; j++) {
                System.out.printf("%f,", values[j]);
            }
            System.out.println("");
        }
//        System.out.println("==================");
//        for (int i = 0; i < values.length; i += 150) {
//            if (doubleDelta.contains(index)) {
//                DoubleDeltaCompress(values, i, i + 150);
//            }
//            index++;
//        }
//        for (int i = 0; i < values.length; i += 150) {
//            for (int j = i; j < i + 150; j++) {
//                System.out.printf("%f,", values[j]);
//            }
//            System.out.println("");
//        }
        doubleCompressResult doubleCompressResult = encode2(values, 150);
        byte[] array = doubleCompressResult.getData();
        byte[] header = doubleCompressResult.getHeader();
        //        byte[] array1 = encode2(values, 150);
        final ToIntCompressor sprintzCompressor = new ToIntCompressor(values);
        final ByteBuffer compress2 = sprintzCompressor.compress();
        final ToIntDecompressor toIntDecompressor = new ToIntDecompressor(compress2);
        final double[] decompress = toIntDecompressor.decompress();
        final boolean equals = Arrays.equals(values, decompress);
        final byte[] compress = Zstd.compress(compress2.array(), 25);
        final double[] decode1 = decode2(ByteBuffer.wrap(array), 1500, 150, header);
        Set<Integer> columns = new HashSet<>();
        columns.clear();
        ;
        columns.add(4);
        columns.add(3);
        columns.add(2);
        columns.add(6);
        double[] doubles1 = decode2ByColumns(ByteBuffer.wrap(array), 1500, 150, header, columns);
//        for(int i=0;i<10;i++){
//            ByteBuffer wrap = ByteBuffer.wrap(header);
//            ByteBuffer wrap1 = ByteBuffer.wrap(array);
//            int doubleDeltaOffset = wrap.getInt();
//            int corrilaLength = wrap.getInt();
//            ByteBuffer byteBuffer = null;
//            if(doubleDeltaFlag[i]){
//                byteBuffer = wrap1;
//            }else{
//                wrap1.position(wrap1.position()+doubleDeltaOffset);
//                byte[] bytes = new byte[corrilaLength];
//                wrap1.get(bytes,0,bytes.length);
//                byteBuffer = ByteBuffer.wrap(bytes);
//            }
//            double[] doubles = decodeByIndex(byteBuffer, 150, 150, header,i);
//            for(int j=0;j<150;j++){
//                if(doubles[j]!=data[i*150+j]){
//                    System.out.println(i + "->" + doubles[i] + " : " + data[i]);
//                }
//            }
//        }
        for (Integer column : columns) {
            for (int j = 0; j < 150; j++) {
                if (doubles1[column * 150 + j] != data[column * 150 + j]) {
                    System.out.println("not euqal");
                }
            }
        }
        System.out.println(Arrays.equals(decode1, data));
        System.out.println("compress rate : " + 1.0 * array.length / (values.length * 8));
//        final ByteBuffer encode = encode(values, 0, 150);
//        final double[] decode1 = decode(encode, 150);
//
//        final byte[] array = byteBuffer.array();
//        final GzipCompress gzipCompress = new GzipCompress();
//        final byte[] compress = gzipCompress.compress(array);
//        byteBuffer.flip();
        final ByteBuffer byteBuffer = ByteBuffer.wrap(array);
        List<Integer> lists = new ArrayList<>();
        lists.add(41);
        lists.add(42);
        lists.add(43);
        lists.add(44);
        lists.add(45);
        lists.add(46);
        lists.add(49);
//        final Map<Integer, double[]> byLineNum = getByLineNum(byteBuffer, 150, lists);
//        final double[] decode1 = decode2(ByteBuffer.wrap(array), 1500, 150);
//        final boolean equals1 = Arrays.equals(values, decode);
//
//        final byte[] compress2 = ZstdCompress.compress(array, 25);
//        System.out.println(byteBuffer);
//        double[] decode = decode(byteBuffer, values.length);
//        for (int i = 0; i < decode.length; i++) {
//            System.out.println(decode[i]);
//        }
        final ByteBuffer allocate = ByteBuffer.allocate(values.length * 8);
        for (double value : values) {
            allocate.putDouble(value);
        }
        final byte[] compress1 = Zstd.compress(allocate.array(), 12);
//        final GzipCompress gzipCompress = new GzipCompress();
//        final byte[] compress = gzipCompress.compress(compress1);
    }
}
