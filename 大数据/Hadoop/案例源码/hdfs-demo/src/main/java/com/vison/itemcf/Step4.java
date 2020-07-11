package com.vison.itemcf;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 *       i101 i102 i103 i104           U8721            结果
 *  i101  1    2     1   1              2               r1
 *  i102  2    2     3   12      X      3               r2
 *  i103  1    3     8   4              4               r3
 *  i104  1    12    4   3              1               r4
 *
 *
 *  矩阵乘法根据（行*列) n-m  * m*p  ==> n*p
 *  我们计算出来的矩阵是对称矩阵，所以
 *  乘法是行乘以列，每一个行和列表的下标 对应乘上然后相加就是某一个下标的值。
 *
 *  r1 = 1*2+2*3+1*4+1*1
 *
 * 可以把所有的相乘起来，然后在相加起来。
 *
 * @author Vison
 * @date 2020/7/11 12:52
 * @Version 1.0
 */
public class Step4 {

    public static void run(Configuration configuration, Map<String, Path> paths) throws IOException, ClassNotFoundException, InterruptedException {

        Job job= Job.getInstance(configuration,"step4");
        //通过类名打成jar包
        job.setJarByClass(Step4.class);

        //1.输入文件
        FileInputFormat.setInputPaths(job,
                paths.get("step4Input1"), paths.get("step4Input2"));

        //2.编写mapper处理逻辑
        job.setMapperClass(Step4Mapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);

        //3.编写reducer逻辑
        job.setReducerClass(Step4Reducer.class); //reduce 业务处理
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        //4.输出文件
        Path output = paths.get("step4Output");
        if (output.getFileSystem(configuration).exists(output)){
            output.getFileSystem(configuration).delete(output,true);
        }
        FileOutputFormat.setOutputPath(job,output);

        //5.运行
        job.waitForCompletion(true);
    }

    static class  Step4Mapper extends Mapper<LongWritable,Text,Text,Text> {


        private  String fileName; //A同向矩阵，B用户得分举证
        @Override
        protected void setup(Context context) throws IOException, InterruptedException {

            FileSplit fileSplit = (FileSplit) context.getInputSplit();
            fileName = fileSplit.getPath().getParent().getName(); //判断读数据文件夹来判定数据
        }

        Text keyText = new Text();
        Text valueText = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

            String[] tokens = Pattern.compile("[\t,]").split(value.toString());
            if ("step2".equals(fileName)){   //用户评分向量 u1232 i232:1,i1231:2
                String userId = tokens[0];
                System.out.println("step2=====>"+value.toString());
                for (int i = 1; i < tokens.length; i++) {
                    String[] item_actions = tokens[i].split(":");
                    String itemId = item_actions[0];     //i232
                    String action_num = item_actions[1]; //1

                    //用户评分向量 u1232 i232:1,i1231:2

                    //i232   B:u1232,1
                    //i1231  B:u1232,2

                    keyText.set(itemId);
                    valueText.set("B:"+userId+","+action_num);
                    context.write(keyText,valueText);   // i232  B:u1212,1
                }

            }else if ("step3".equals(fileName)){ //同向矩阵 i323:i212 12
                System.out.println("step3=====>"+value.toString());
                String item_relations = tokens[1];
                String[] items = tokens[0].split(":");
                String itemId1 = items[0];
                String itemId2 = items[1];

                // i323  A:i212,12
                // i212  A:i323,12   a:b->b:a 有相同的值，所以都会取到,是对称的数据在step3map时输出的

                // i323  A:i112,11
                // i112  A:i323,11

                keyText.set(itemId1);  // i323  A:1212,12
                valueText.set("A:"+itemId2+","+item_relations);
                context.write(keyText,valueText);
            }

        }
    }

    static class  Step4Reducer extends Reducer<Text, Text,Text,Text> {

        Text keyText = new Text();
        Text valueText = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {

            // i323
              //A:i212,12  物品id，物品和物品的关联关系
              //A:i112,11
              //B:u1232,1  用户id,用户和物品的关联关系
              //B:u1272,1

            Map<String,Integer> itemsRelations = new HashMap<>(); // itemId: num;和其他物品的itemid 和关系
            Map<String,Integer> userActions = new HashMap<>();    // userId:num // 用户id和 当前物品的关系

            for (Text value : values) {
                String str = value.toString();
                if (str.startsWith("A:")){
                    //A:i212,12
                    String[] split = Pattern.compile("[\t,]").split(str.substring(2));
                    itemsRelations.put(split[0],Integer.valueOf(split[1]));
                }else if (str.startsWith("B:")){
                    //B:u1272,1
                    String[] split = Pattern.compile("[\t,]").split(str.substring(2));
                    userActions.put(split[0],Integer.valueOf(split[1]));
                }
            }

            int result = 0;

            //将两个变量分别遍历 相乘；；；；这里是比较关键，需要自己多理解
            Iterator<String> iterator = itemsRelations.keySet().iterator();
            while (iterator.hasNext()){
                String itemId = iterator.next(); //itemId
                Integer itemRelation = itemsRelations.get(itemId);

                Iterator<String> userActionsIt = userActions.keySet().iterator();
                while (userActionsIt.hasNext()){
                    String userId = userActionsIt.next(); //userId
                    Integer userAction = userActions.get(userId);

                    //这里相当于计算多个用户id对于
                    result = itemRelation * userAction;

                    keyText.set(userId);
                    valueText.set(itemId+":"+result);
                    context.write(keyText,valueText);

                }

            }


        }
    }
}
