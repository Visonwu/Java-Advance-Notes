package com.vison.friends;

import com.vison.whether.Whether;
import com.vison.whether.WhetherBootstrap;
import com.vison.whether.WhetherGroupComparator;
import com.vison.whether.WhetherMapper;
import com.vison.whether.WhetherPartition;
import com.vison.whether.WhetherReducer;
import com.vison.whether.WhetherSortComparator;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * 问题：推荐好友分析，有类似的好友给推荐出来  这里只分析了一半;
 * 下面：比如hello hadoop 出现在多个好友列表中，那么他们很可能认识
 *
 * 姓名 好友列表 （第一个名字表示用户，后面表示用户的好友）
 * tom hello hadoop cat
 * world hadoop hello hive
 * cat tom hive
 * hive cat hadoop world hello mr
 * hadoop tom hive world
 * hello tom world hive mr
 *
 * @author Vison
 * @date 2020/7/7 22:12
 * @Version 1.0
 */
public class FriendsBootstrap {

    public static void main(String[] args) throws Exception {

        Configuration configuration = new Configuration(true);
        // configuration.set("fs.defaultFS","hdfs://192.168.109.133:8020");
        Job job= Job.getInstance(configuration,"vison-friends");
        //通过类名打成jar包
        job.setJarByClass(FriendsBootstrap.class);

        //1.输入文件
        FileInputFormat.addInputPath(job,new Path(args[0]));
        //2.编写mapper处理逻辑
        job.setMapperClass(FriendMapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);

        //3.编写reducer逻辑
        job.setReducerClass(FriendReducer.class); //reduce 业务处理
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        //4.输出文件
        Path output = new Path(args[1]);
        if (output.getFileSystem(configuration).exists(output)){
            output.getFileSystem(configuration).delete(output,true);
        }
        FileOutputFormat.setOutputPath(job,output);

        //5.运行
        job.waitForCompletion(true);


    }

}
