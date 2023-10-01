package com.alibaba.lindorm.contest.example;

import com.alibaba.lindorm.contest.structs.ColumnValue;
import com.alibaba.lindorm.contest.structs.Row;
import com.alibaba.lindorm.contest.structs.Vin;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RowFactory  implements Serializable{
        public String[] columnsName  = {"QZZS", "ZDWDZXTH", "JYDZ", "FDJGZZS", "QXTZGWD", "ZGWDZXTH", "DW", "QDDJGS", "QQZGND", "ZGDYDCDTDH", "QDDJXH", "ZDDYDCDTDH", "DCDC", "RLDCRLXHL", "DJKZQDY", "DCDTDYZGZ", "ZGDYDCZXTDH", "YXMS", "DWZT", "RLDCDY", "QTGZLB", "QQZGYLCGQDH", "GYDCDCZT", "RLDCDL", "LATITUDE", "DJKZQDL", "QXTZGWDTZDH", "ZDWDTZXH", "KCDCNZZGZDMLB", "CDZT", "QDDJZS", "RLXHL", "QDDJZT", "LONGITUDE", "FDJGZLB", "QDDJGZDMLB", "DCDTDYZDZ", "QQZGNDCGQDH", "LJLC", "ZDWDZ", "QDDJZJ", "ZGBJDJ", "QDDJGZZS", "ZDL", "ZDDYDCZXTDH", "KCDCNZZGZZS", "SOC", "ZDY", "ZGWDTZXH", "QDDJWD", "CS", "RLDCTZGS", "QDDJKZWD", "TZWD", "ZGWDZ", "QTGZZS", "QQZGYL", "TYBJBZ", "CLZT", "FDJZT"};

        public int[] ints = new int[40];

        public double[] doubles = new double[10];
        public String[] byteBuffers = new String[10];

        public Vin vin;
        public long timeStamp;
        Random random = new Random();

        RowFactory(){}
        Row GetRow(){
            Map<String, ColumnValue> columns = new HashMap<>();
            for(int i=0;i<40;i++){
                columns.put(columnsName[i],new ColumnValue.IntegerColumn(ints[i]));
            }
            for(int i=40;i<50;i++){
                columns.put(columnsName[i],new ColumnValue.DoubleFloatColumn(doubles[i-40]));
            }
            for(int i=50;i<60;i++){
                int length = random.nextInt(25);
                ByteBuffer byteBuffer = ByteBuffer.wrap(byteBuffers[i-50].getBytes());
                columns.put(columnsName[i],new ColumnValue.StringColumn(byteBuffer));
            }
            return new Row(vin,timeStamp,columns);
        }
}
