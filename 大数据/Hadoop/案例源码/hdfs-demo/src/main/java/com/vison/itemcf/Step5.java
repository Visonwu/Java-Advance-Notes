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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 将第四部上的矩阵乘 结果相加就是某一个商品的 推荐分数
 *
 * @author Vison
 * @date 2020/7/11 12:52
 * @Version 1.0
 */
public class Step5 {


    public static void run(Configuration configuration, Map<String, Path> paths) throws IOException, ClassNotFoundException, InterruptedException {

        Job job= Job.getInstance(configuration,"step5");
        //通过类名打成jar包
        job.setJarByClass(Step5.class);

        //1.输入文件
        FileInputFormat.addInputPath(job,paths.get("step5Input"));
        //2.编写mapper处理逻辑
        job.setMapperClass(Step5Mapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);

        //3.编写reducer逻辑
        job.setReducerClass(Step5Reducer.class); //reduce 业务处理
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        //4.输出文件
        Path output = paths.get("step5Output");
        if (output.getFileSystem(configuration).exists(output)){
            output.getFileSystem(configuration).delete(output,true);
        }
        FileOutputFormat.setOutputPath(job,output);

        //5.运行
        job.waitForCompletion(true);
    }

    static class  Step5Mapper extends Mapper<LongWritable,Text,Text,Text> {

        Text keyText = new Text();
        Text valueText = new Text();
        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

            // 原封不动输出

            // u2319  i642:1  用户 物品：评分
            // u2319  i642:12  用户 物品：评分
            // u2319  i642:3  用户 物品：评分
            String[] tokens = Pattern.compile("[\t:]").split(value.toString());
            String userId = tokens[0];
            String itemId = tokens[1];
            String nums = tokens[2];

            keyText.set(userId);
            valueText.set(itemId+":"+nums);
            context.write(keyText,valueText);

        }
    }

    static class  Step5Reducer extends Reducer<Text, Text,Text,Text> {

        Text valueText = new Text();
        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {

            // 求用户  每一个商品的的和
            // u2319  i642:1  用户 物品：评分
            // u2319  i642:12  用户 物品：评分
            // u2319  i642:3  用户 物品：评分

            Map<String,Integer> map = new HashMap<>();
            for (Text value : values) {
                String[] tokens = value.toString().split(":");
                String itemId = tokens[0];
                Integer scores = Integer.valueOf(tokens[1]);

                map.merge(itemId, scores, Integer::sum);
            }

            StringBuilder sb = new StringBuilder();
            map.forEach((itemId,scores) ->{
                sb.append(itemId).append(":")
                        .append(scores)
                        .append(",");
            });

            // u2319  i612:90,i142:77,i610:89,

            valueText.set(sb.toString());
            context.write(key,valueText);
        }
    }
}
