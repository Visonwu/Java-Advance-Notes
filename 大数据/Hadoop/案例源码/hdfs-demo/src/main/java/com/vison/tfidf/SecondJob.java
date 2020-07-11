package com.vison.tfidf;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 *
 * 统计每篇文章的字词数量--> id 23
 *
 * @author Vison
 * @date 2020/7/10 10:32
 * @Version 1.0
 */
public class SecondJob {

    public static void main(String[] args) {
        Configuration configuration = new Configuration(true);
        try {
            configuration.set("fs.defaultFS","hdfs://192.168.4.131:9000");
            Job job= Job.getInstance(configuration,"second-job");
            //通过类名打成jar包
            job.setJarByClass(FirstJob.class);

            //1.输入文件 /user/root/tfidf/first/output
            FileInputFormat.addInputPath(job,new Path("/user/root/tfidf/first/output"));

            //2.编写mapper处理逻辑
            job.setMapperClass(SecondMapper.class);
            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(IntWritable.class);


            //3.编写reducer逻辑
            job.setReducerClass(SecondReducer.class); //reduce 业务处理
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(IntWritable.class);

            //4.输出文件
            Path output = new Path("/user/root/tfidf/second/output");
            if (output.getFileSystem(configuration).exists(output)){
                output.getFileSystem(configuration).delete(output,true);
            }
            FileOutputFormat.setOutputPath(job,output);

            //5.运行
            job.waitForCompletion(true);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
