package com.vison.tfidf;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.net.URI;

/**
 *
 *  tf-idf = wordtextnum / textnum * log(lines / wordline)
 *
 * @author Vison
 * @date 2020/7/10 10:32
 * @Version 1.0
 */
public class ThirdJob {

    public static void main(String[] args) {

        Configuration configuration = new Configuration(true);
        try {
            configuration.set("fs.defaultFS","hdfs://192.168.4.131:9000");
            Job job= Job.getInstance(configuration,"third-job");
            //通过类名打成jar包
            job.setJarByClass(FirstJob.class);

            //1.输入文件
            FileInputFormat.addInputPath(job,new Path("/user/root/tfidf/first/output"));
            URI uri1 = new URI("/user/root/tfidf/first/output");
            URI uri2 = new URI("/user/root/tfidf/second/output");
            job.setCacheFiles(new URI[]{uri1,uri2});

            //2.编写mapper处理逻辑
            job.setMapperClass(ThirdMapper.class);
            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(IntWritable.class);


            //3.编写reducer逻辑
            job.setReducerClass(ThirdReducer.class); //reduce 业务处理
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(Text.class);

            //4.输出文件
            Path output = new Path("/user/root/tfidf/third/output");
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
