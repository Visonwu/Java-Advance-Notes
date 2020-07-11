package com.vison.itemcf;

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
import java.util.Map;

/**
 * 对原来的数据做去重处理
 *
 * @author Vison
 * @date 2020/7/11 12:52
 * @Version 1.0
 */
public class Step1 {

    public static void run(Configuration configuration, Map<String, Path> paths) throws IOException, ClassNotFoundException, InterruptedException {

        Job job= Job.getInstance(configuration,"step1");
        //通过类名打成jar包
        job.setJarByClass(Step1.class);

        //1.输入文件
        FileInputFormat.addInputPath(job,paths.get("step1Input"));
        //2.编写mapper处理逻辑
        job.setMapperClass(Step1Mapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(NullWritable.class);

        //3.编写reducer逻辑
        job.setReducerClass(Step1Reducer.class); //reduce 业务处理

        //4.输出文件
        Path output = paths.get("step1Output");
        if (output.getFileSystem(configuration).exists(output)){
            output.getFileSystem(configuration).delete(output,true);
        }
        FileOutputFormat.setOutputPath(job,output);

        //5.运行
        job.waitForCompletion(true);
    }

    static class  Step1Mapper extends Mapper<LongWritable,Text,Text,NullWritable> {

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            if (key.get() !=0){ //直接输出
                context.write(value,NullWritable.get());
            }
        }
    }

    static class  Step1Reducer extends Reducer<Text,IntWritable,Text,NullWritable> {

        @Override
        protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            //只执行一个key
            context.write(key,NullWritable.get());
        }
    }
}
