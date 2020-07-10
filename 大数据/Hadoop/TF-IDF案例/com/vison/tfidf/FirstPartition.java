package com.vison.tfidf;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.lib.partition.HashPartitioner;

/**
 * @author Vison
 * @date 2020/7/10 11:30
 * @Version 1.0
 */
public class FirstPartition extends HashPartitioner<Text, IntWritable> {


    @Override
    public int getPartition(Text key, IntWritable value, int numReduceTasks) {
        //总共4个分区，将记录统计放到最后一个分区中
        if (key.toString().contains("count")){
            return 3;
        }
        //其他的放到其他分区去
        return super.getPartition(key, value, (numReduceTasks-1));
    }
}
