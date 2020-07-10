package com.vison.whether;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Partitioner;

/**
 * @author Vison
 * @date 2020/7/7 16:32
 * @Version 1.0
 */
public class WhetherPartition extends Partitioner<Whether, IntWritable> {

    @Override
    public int getPartition(Whether whether, IntWritable intWritable, int numPartitions) {
        return whether.hashCode() % numPartitions;
    }
}
