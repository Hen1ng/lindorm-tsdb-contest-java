package com.alibaba.lindorm.contest.example;

import com.alibaba.lindorm.contest.TSDBEngineImpl;
import com.alibaba.lindorm.contest.index.MapIndex;
import com.alibaba.lindorm.contest.structs.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DataGenerator {

    public static String[] columnsName = {"QZZS", "ZDWDZXTH", "JYDZ", "FDJGZZS", "QXTZGWD", "ZGWDZXTH", "DW", "QDDJGS", "QQZGND", "ZGDYDCDTDH", "QDDJXH", "ZDDYDCDTDH", "DCDC", "RLDCRLXHL", "DJKZQDY", "DCDTDYZGZ", "ZGDYDCZXTDH", "YXMS", "DWZT", "RLDCDY", "QTGZLB", "QQZGYLCGQDH", "GYDCDCZT", "RLDCDL", "LATITUDE", "DJKZQDL", "QXTZGWDTZDH", "ZDWDTZXH", "KCDCNZZGZDMLB", "CDZT", "QDDJZS", "RLXHL", "QDDJZT", "LONGITUDE", "FDJGZLB", "QDDJGZDMLB", "DCDTDYZDZ", "QQZGNDCGQDH", "LJLC", "ZDWDZ", "QDDJZJ", "ZGBJDJ", "QDDJGZZS", "ZDL", "ZDDYDCZXTDH", "KCDCNZZGZZS", "SOC", "ZDY", "ZGWDTZXH", "QDDJWD", "CS", "RLDCTZGS", "QDDJKZWD", "TZWD", "ZGWDZ", "QTGZZS", "QQZGYL", "TYBJBZ", "CLZT", "FDJZT"};

    public static int INT_NUM = 40;
    public static int DOUBLE_NUM = 10;
    public static int STRING_NUM = 10;
    public static double[] randomDouble;
    public static Vin[] vins;

    public static long[] timeStamp = new long[36000];
    public static int VIN_NUMS = 5000;


    public static int threadNum = 16;

    private static ExecutorService executorService = Executors.newFixedThreadPool(threadNum);

    public static String generateRandomString(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random rand = new Random();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(rand.nextInt(characters.length())));
        }

        return sb.toString();
    }
    public static void initTimeStamp(long start){
        for(int i=0;i<36000;i++){
            timeStamp[i] = start+i*1000;
        }
    }

    public static RowFactory randomRowFactory() {
        RowFactory rowFactory = new RowFactory();
        Random random = new Random();
        rowFactory.vin = vins[0];
        rowFactory.timeStamp = System.currentTimeMillis();
        Map<String, ColumnValue> columns = new HashMap<>();
        for (int i = 0; i < INT_NUM; i++) {
            rowFactory.ints[i] = random.nextInt();
        }
        for (int i = INT_NUM; i < INT_NUM+DOUBLE_NUM; i++) {
            rowFactory.doubles[i - INT_NUM] = randomDouble[random.nextInt(100)];
        }
        for (int i = INT_NUM+DOUBLE_NUM; i < INT_NUM+DOUBLE_NUM+STRING_NUM; i++) {
            int length = random.nextInt(25);
            String str = generateRandomString(length);
            rowFactory.byteBuffers[i - INT_NUM-DOUBLE_NUM] = str;
        }
        return rowFactory;
    }

    public static Schema getSchema() {
        Map<String, ColumnValue.ColumnType> cols = new HashMap<>();
        for (int i = 0; i < INT_NUM; i++) {
            cols.put(columnsName[i], ColumnValue.ColumnType.COLUMN_TYPE_INTEGER);
        }
        for (int i = INT_NUM; i < INT_NUM+DOUBLE_NUM; i++) {
            cols.put(columnsName[i], ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT);
        }
        for (int i = INT_NUM+DOUBLE_NUM; i < INT_NUM+DOUBLE_NUM+STRING_NUM; i++) {
            cols.put(columnsName[i], ColumnValue.ColumnType.COLUMN_TYPE_STRING);
        }
        return new Schema(cols);

    }

    public static LatestQueryRequest randomLastestQueryRequest() {
        Random random = new Random();
        Set<String> requestedColumns = new HashSet<>(Arrays.asList(columnsName));
        ArrayList<Vin> vinList = new ArrayList<>();
        int length = random.nextInt(20);
        for (int i = 0; i < 20; i++) {
            vinList.add(vins[random.nextInt(VIN_NUMS)]);
        }
        return new LatestQueryRequest("test", vinList, requestedColumns);
    }

    public static TimeRangeDownsampleRequest genTimeRangeDownsampleRequest(){
        Random random = new Random();
        Vin vin = vins[0];
        int start = random.nextInt(36000-3600);
        int randomTime = random.nextInt(1000);
        long timeLower = timeStamp[start] + randomTime;
        long timeUpper = timeStamp[start+3600]+randomTime;
        int i = random.nextInt(4);
        if(i==1) {
            return new TimeRangeDownsampleRequest("test", vin, "QZZS", timeLower, timeUpper, Aggregator.AVG, 10000, new CompareExpression(new ColumnValue.IntegerColumn(random.nextInt()), CompareExpression.CompareOp.GREATER));
        }else if(i==2) {
            return new TimeRangeDownsampleRequest("test", vin, "QZZS", timeLower, timeUpper, Aggregator.MAX, 10000, new CompareExpression(new ColumnValue.IntegerColumn(random.nextInt()), CompareExpression.CompareOp.GREATER));
        }else if(i==3){
            return new TimeRangeDownsampleRequest("test", vin, "QZZS", timeLower, timeUpper, Aggregator.AVG, 10000, new CompareExpression(new ColumnValue.IntegerColumn(random.nextInt()), CompareExpression.CompareOp.EQUAL));
        }
        return new TimeRangeDownsampleRequest("test", vin, "QZZS", timeLower, timeUpper, Aggregator.MAX, 10000, new CompareExpression(new ColumnValue.IntegerColumn(random.nextInt()), CompareExpression.CompareOp.EQUAL));
    }

    public static void main(String[] args) {
        initTimeStamp(16492012515000L);
        vins = new Vin[VIN_NUMS];
        for(int i=0;i<VIN_NUMS;i++){
            vins[i] = new Vin(generateRandomString(100).getBytes());
        }
        randomDouble = new double[100];
        for (int i = 0; i < randomDouble.length; i++) {
            randomDouble[i] = Math.random();
        }
        File dataDir = new File("data_dir_test");

        if (dataDir.isFile()) {
            throw new IllegalStateException("Clean the directory before we start the demo");
        }


        boolean ret = dataDir.mkdirs();
        if (!ret) {
            throw new IllegalStateException("Cannot create the temp data directory: " + dataDir);
        }

        TSDBEngineImpl tsdbEngineSample = new TSDBEngineImpl(dataDir);
        try {
            // Generate random data
            Random random = new Random();
            tsdbEngineSample.connect();
            Schema schema = getSchema();
            tsdbEngineSample.createTable("test", schema);
            // Save generated data to a file for later comparison
            AtomicLong totalTime = new AtomicLong();
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(dataDir + "/300WrandomRowFactory.dat"))) {
//                out.writeInt(3000000);
                int batchSize = 10;
                for (int i = 0; i <3600; i++) {
                    ArrayList<Row> rows = new ArrayList<>();
                    for(int j=0;j<10;j++) {
                        RowFactory rowFactory = randomRowFactory();
//                    out.writeObject(rowFactory);
                        rowFactory.timeStamp = timeStamp[i*10+j];
                        rows.add(rowFactory.GetRow());
                    }
                    if (rows.size() == batchSize) {
                        executorService.execute(() -> {
                            try {
                                long start = System.currentTimeMillis();
                                tsdbEngineSample.write(new WriteRequest("test", new ArrayList<>(rows)));
                                long end = System.currentTimeMillis();
                                totalTime.addAndGet((end - start));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                }
                System.out.println("row insert and  write into 300WrandomRowFactory.dat completed");
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("write Time : " + totalTime.get());
            executorService.shutdown();
            try {
                // 等待线程池终止，但最多等待5秒
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.out.println("Some tasks were not terminated yet!");
                    executorService.shutdownNow();  // 尝试强制关闭所有正在执行的任务
                } else {
                    System.out.println("============write Done===========");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                executorService.shutdownNow();  // 如果当前线程被中断，也尝试强制关闭线程池
            }
            tsdbEngineSample.shutdown();
            TSDBEngineImpl tsdbEngine = new TSDBEngineImpl(dataDir);
            MapIndex.INDEX_MAP.clear();
            tsdbEngine.connect();
            System.out.println("DownSample Quey Begin =============");
            for(int i=0;i<100000;i++){
                TimeRangeDownsampleRequest timeRangeDownsampleRequest = genTimeRangeDownsampleRequest();
                ArrayList<Row> rows = tsdbEngine.executeDownsampleQuery(timeRangeDownsampleRequest);
                ArrayList<Row> ans = tsdbEngine.executeDownsampleQueryByBucket(timeRangeDownsampleRequest);
                if(rows.size() != ans.size()){
                    System.out.println("size not equal");
                    ans = tsdbEngine.executeDownsampleQueryByBucket(timeRangeDownsampleRequest);
                    rows = tsdbEngine.executeDownsampleQuery(timeRangeDownsampleRequest);
                }
                for(int j=0;j<rows.size();j++){
                    Row row = rows.get(j);
                    Row ansRow = ans.get(j);
                    if(row.getTimestamp() != ansRow.getTimestamp()){
                        System.out.println("timeStamp error");
                    }
                    if(!row.getColumns().get("QZZS").equals(ansRow.getColumns().get("QZZS"))){
                        System.out.println("ans wrong expect : "+ ansRow.getColumns().get("QZZS") +" but got : " + row.getColumns().get("QZZS"));
                        ans = tsdbEngine.executeDownsampleQueryByBucket(timeRangeDownsampleRequest);
                        rows = tsdbEngine.executeDownsampleQuery(timeRangeDownsampleRequest);
                    }
                }
                System.out.println("DownSample query "+ i +" all right");
            }
            System.out.println("DownSample Quey end ============= ALL RIGHT");
            tsdbEngine.shutdown();
//            tsdbEngineSample.shutdown();
            // Read saved data from file
            // Save generated data to a file for later comparison
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }
}
