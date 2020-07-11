package com.vison.itemcf;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 统计用户 所有商品的评分信息
 *
 * @author Vison
 * @date 2020/7/11 12:52
 * @Version 1.0
 */
public class Step2 {


    public static void run(Configuration configuration, Map<String, Path> paths) throws IOException, ClassNotFoundException, InterruptedException {

        Job job= Job.getInstance(configuration,"step2");
        //通过类名打成jar包
        job.setJarByClass(Step2.class);

        //1.输入文件
        FileInputFormat.addInputPath(job,paths.get("step2Input"));
        //2.编写mapper处理逻辑
        job.setMapperClass(Step2Mapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);

        //3.编写reducer逻辑
        job.setReducerClass(Step2Reducer.class); //reduce 业务处理
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        //4.输出文件
        Path output = paths.get("step2Output");
        if (output.getFileSystem(configuration).exists(output)){
            output.getFileSystem(configuration).delete(output,true);
        }
        FileOutputFormat.setOutputPath(job,output);

        //5.运行
        job.waitForCompletion(true);
    }

    static class  Step2Mapper extends Mapper<LongWritable,Text,Text,Text> {

        Text keyText = new Text();
        Text valueText = new Text();
        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

            if (value == null || StringUtils.isEmpty(value.toString())){
                return;
            }
            // value --->  i642,u2319,click,2014/9/28 21:35
//            System.out.println("values====>"+value.toString());
            String[] split = value.toString().split(",");
            String itemId = split[0];
            String userId = split[1];
            int actionValue = StartUp.RATE.get(split[2]);

            keyText.set(userId);
            valueText.set(itemId+":"+actionValue);

            // u2319  i642:1  用户 物品：评分
            context.write(keyText,valueText);
        }
    }

    static class  Step2Reducer extends Reducer<Text, Text,Text,Text> {

        Text text = new Text();
        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            //u2319    用户
            // i642:1  物品：评分
            // i442:3  物品：评分
            // i512:1  物品：评分

            Map<String,Integer> map = new HashMap<>();

            for (Text value : values) {
                String[] split = value.toString().split(":");
                String itemId = split[0];
                int actionId = Integer.parseInt(split[1]);
                map.put(itemId,map.get(itemId) == null ? actionId : actionId+map.get(itemId));
            }

            StringBuilder  sb = new StringBuilder();
            Set<Map.Entry<String, Integer>> entries = map.entrySet();
            for (Map.Entry<String, Integer> entry : entries) {
                sb.append(entry.getKey())
                        .append(":")
                        .append(entry.getValue())
                        .append(",");
            }
            //u2319  i642:1,i442:3,i512:1
            text.set(sb.toString());
            context.write(key,text);
        }
    }
}
