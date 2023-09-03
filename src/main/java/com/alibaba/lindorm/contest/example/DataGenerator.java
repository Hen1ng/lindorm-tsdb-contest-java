package com.alibaba.lindorm.contest.example;

import com.alibaba.lindorm.contest.TSDBEngineImpl;
import com.alibaba.lindorm.contest.structs.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataGenerator {

    public static String[] columnsName  = {"QZZS", "ZDWDZXTH", "JYDZ", "FDJGZZS", "QXTZGWD", "ZGWDZXTH", "DW", "QDDJGS", "QQZGND", "ZGDYDCDTDH", "QDDJXH", "ZDDYDCDTDH", "DCDC", "RLDCRLXHL", "DJKZQDY", "DCDTDYZGZ", "ZGDYDCZXTDH", "YXMS", "DWZT", "RLDCDY", "QTGZLB", "QQZGYLCGQDH", "GYDCDCZT", "RLDCDL", "LATITUDE", "DJKZQDL", "QXTZGWDTZDH", "ZDWDTZXH", "KCDCNZZGZDMLB", "CDZT", "QDDJZS", "RLXHL", "QDDJZT", "LONGITUDE", "FDJGZLB", "QDDJGZDMLB", "DCDTDYZDZ", "QQZGNDCGQDH", "LJLC", "ZDWDZ", "QDDJZJ", "ZGBJDJ", "QDDJGZZS", "ZDL", "ZDDYDCZXTDH", "KCDCNZZGZZS", "SOC", "ZDY", "ZGWDTZXH", "QDDJWD", "CS", "RLDCTZGS", "QDDJKZWD", "TZWD", "ZGWDZ", "QTGZZS", "QQZGYL", "TYBJBZ", "CLZT", "FDJZT"};

    public static double[] randomDouble;
    public static Vin[] vins;
    public static int VIN_NUMS = 30000;


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

    public static RowFactory randomRowFactory(){
        RowFactory rowFactory = new RowFactory();
        Random random = new Random();
        rowFactory.vin = vins[random.nextInt(VIN_NUMS)];
        rowFactory.timeStamp = System.currentTimeMillis();
        Map<String, ColumnValue> columns = new HashMap<>();
        for(int i=0;i<45;i++){
            rowFactory.ints[i] = random.nextInt(100);
        }
        for(int i=45;i<54;i++){
            rowFactory.doubles[i-45] = randomDouble[random.nextInt(100)];
        }
        for(int i=54;i<60;i++){
            int length = random.nextInt(25);
            String str = generateRandomString(length);
            rowFactory.byteBuffers[i-54] = str;
        }
        return rowFactory;
    }

    public static Schema getSchema(){
        Map<String, ColumnValue.ColumnType> cols = new HashMap<>();
        for(int i=0;i<45;i++){
            cols.put(columnsName[i], ColumnValue.ColumnType.COLUMN_TYPE_INTEGER );
        }
        for(int i=45;i<54;i++){
            cols.put(columnsName[i], ColumnValue.ColumnType.COLUMN_TYPE_DOUBLE_FLOAT );
        }
        for(int i=54;i<60;i++){
            cols.put(columnsName[i], ColumnValue.ColumnType.COLUMN_TYPE_STRING);
        }
        return new Schema(cols);

    }
    public static LatestQueryRequest randomLastestQueryRequest(){
        Random random = new Random();
        Set<String> requestedColumns = new HashSet<>(Arrays.asList(columnsName));
        ArrayList<Vin> vinList = new ArrayList<>();
        int length = random.nextInt(20);
        for(int i=0;i<20;i++){
            vinList.add(vins[random.nextInt(VIN_NUMS)]);
        }
        return new LatestQueryRequest("test",vinList,requestedColumns);
    }
    public static void main(String[] args) {
        vins = new Vin[VIN_NUMS];
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
            tsdbEngineSample.createTable("test",schema);
            // Save generated data to a file for later comparison
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(dataDir+"/300WrandomRowFactory.dat"))) {
                out.writeInt(3000000);
                int batchSize = 10;
                ArrayList<Row> rows = new ArrayList<>();
                for(int i=0;i<3000000;i++){
                    RowFactory rowFactory = randomRowFactory();
                    out.writeObject(rowFactory);
                    rows.add(rowFactory.GetRow());
                    if(rows.size() == batchSize){
                        executorService.submit(()-> {
                            try {
                                tsdbEngineSample.upsert(new WriteRequest("test", new ArrayList<>(rows)));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            rows.clear();
                        });
                    }
                }
                System.out.println("row insert and  write into 300WrandomRowFactory.dat completed");
            } catch (IOException e) {
                e.printStackTrace();
            }
            tsdbEngineSample.shutdown();
            // Stage2: read
            tsdbEngineSample.connect();
            try(ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(dataDir+"/LatestQueryRequest"))){

            }catch (Exception e){
                e.printStackTrace();
            }
            // Read saved data from file
            // Save generated data to a file for later comparison
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }
}
