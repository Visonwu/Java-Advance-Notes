package com.vison.whether;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * 计算每个月气温最高的2天
 *
 *  1999-01-03 12:09:12   34c
 *  1999-01-03 12:09:12   38c
 *  1999-01-03 12:09:12   36c
 *  1999-01-02 12:09:12   37c
 *  2012-01-03 12:09:12   39c
 *  2012-01-03 11:09:12   41c
 *  2012-01-03 13:09:12   34c
 *  2002-01-04 12:09:12   40c
 *
 *
 *  1999:1:2	37
 *  1999:1:3	38
 *  2002:1:4	40
 *  2012:1:3	41
 *
 * @author Vison
 * @date 2020/7/7 11:42
 * @Version 1.0
 */
public class WhetherBootstrap {

    public static void main(String[] args) throws Exception {

        Configuration configuration = new Configuration(true);
       // configuration.set("fs.defaultFS","hdfs://192.168.109.133:8020");
        Job job= Job.getInstance(configuration,"vison-wordcount");
        //通过类名打成jar包
        job.setJarByClass(WhetherBootstrap.class);

        //1.输入文件
        FileInputFormat.addInputPath(job,new Path(args[0]));
        //2.编写mapper处理逻辑
        job.setMapperClass(WhetherMapper.class);
        job.setMapOutputKeyClass(Whether.class);
        job.setMapOutputValueClass(IntWritable.class);

        job.setSortComparatorClass(WhetherSortComparator.class);
        job.setPartitionerClass(WhetherPartition.class);

        //3.编写reducer逻辑
        job.setGroupingComparatorClass(WhetherGroupComparator.class); //分组排序
        job.setReducerClass(WhetherReducer.class); //reduce 业务处理
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        //5.输出文件
        Path output = new Path(args[1]);
        if (output.getFileSystem(configuration).exists(output)){
            output.getFileSystem(configuration).delete(output,true);
        }
        FileOutputFormat.setOutputPath(job,output);

        //6.运行
        job.waitForCompletion(true);


    }

}
