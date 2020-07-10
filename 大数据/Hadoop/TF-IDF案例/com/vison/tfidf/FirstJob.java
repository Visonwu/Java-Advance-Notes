package com.vison.tfidf;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;


/**
 * 1.统计总的篇幅数量        -->  count 23
 * 2.统计每篇文章 每个词的个数-->  id_词  3
 *
 * @author Vison
 * @date 2020/7/10 10:31
 * @Version 1.0
 */
public class FirstJob {

    public static void main(String[] args) throws  Exception {

        Configuration configuration = new Configuration(true);
        try {
            configuration.set("fs.defaultFS","hdfs://192.168.4.131:9000");
            Job job= Job.getInstance(configuration,"first-job");
            //通过类名打成jar包
            job.setJarByClass(FirstJob.class);

            //1.输入文件
            FileInputFormat.addInputPath(job,new Path("/user/root/tfidf/input/"));

            //2.编写mapper处理逻辑
            job.setMapperClass(FirstMapper.class);
            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(IntWritable.class);
            job.setPartitionerClass(FirstPartition.class);
            job.setNumReduceTasks(4); //设置四个reducertask即有4个分区

            //3.编写reducer逻辑
            job.setReducerClass(FirstReducer.class); //reduce 业务处理
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(IntWritable.class);

            //4.输出文件
            Path output = new Path("/user/root/tfidf/first/output");
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
