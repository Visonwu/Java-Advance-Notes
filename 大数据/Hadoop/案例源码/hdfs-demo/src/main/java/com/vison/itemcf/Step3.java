package com.vison.itemcf;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.util.Map;

/**
 *
 * 计算物品与物品间的关系---对应同向矩阵中图的数据
 *
 * @author Vison
 * @date 2020/7/11 12:52
 * @Version 1.0
 */
public class Step3 {

    public static void run(Configuration configuration, Map<String, Path> paths) throws IOException, ClassNotFoundException, InterruptedException {

        Job job= Job.getInstance(configuration,"step3");
        //通过类名打成jar包
        job.setJarByClass(Step3.class);

        //1.输入文件
        FileInputFormat.addInputPath(job,paths.get("step3Input"));
        //2.编写mapper处理逻辑
        job.setMapperClass(Step3Mapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);

        //3.编写reducer逻辑
        job.setReducerClass(Step3Reducer.class); //reduce 业务处理
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        //4.输出文件
        Path output = paths.get("step3Output");
        if (output.getFileSystem(configuration).exists(output)){
            output.getFileSystem(configuration).delete(output,true);
        }
        FileOutputFormat.setOutputPath(job,output);

        //5.运行
        job.waitForCompletion(true);
    }

    static class  Step3Mapper extends Mapper<LongWritable,Text,Text,IntWritable> {

        Text keyText = new Text();
        IntWritable valueText = new IntWritable(1);
        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

            //将当前用户的所有物品两两组合，各种情况，这就就类似同向矩阵组合一样，
            // u2319  i642:1,i942:1,i142:1,i612:1,  用户 物品：评分

            //i642:i642  1
            //i642:i942  1
            //i642:i142  1
            //i642:i612  1
            String[] split = value.toString().split("\t");
            String[] items = split[1].split(",");
            for (String item_actionA : items) {
                String itemIdA = item_actionA.split(":")[0];

                for (String item_actionB : items) {
                    String itemIdB = item_actionB.split(":")[0];

                    keyText.set(itemIdA + ":" + itemIdB);
                    context.write(keyText, valueText);
                }
            }
        }
    }

    static class  Step3Reducer extends Reducer<Text, IntWritable,Text,IntWritable> {

        IntWritable intWritable = new IntWritable();
        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            // i442:i642 1  物品:物品 评分
            // i642:i442 1  物品:物品 评分
            // i442:i642 1  物品:物品 评分

            //计算两个物品的评分中和
            int sum =0;
            for (IntWritable value : values) {
                sum+=value.get();
            }
            intWritable.set(sum);
            context.write(key,intWritable);
        }
    }
}
