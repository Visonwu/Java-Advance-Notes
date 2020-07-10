package com.vison.friends;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

/**
 * @author Vison
 * @date 2020/7/7 22:14
 * @Version 1.0
 */
public class FriendReducer extends Reducer<Text, IntWritable, Text, IntWritable> {


    IntWritable intWritable = new IntWritable();

    @Override
    protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {

        //这里的数据会类似这种
        // cat:hello 0
        // cat:hello 1
        // cat:hello 1

        //如果包含为0，那么表示两个都是好友了，那么久直接排除，否则就把1累加
        int sum = 0;
        for (IntWritable value : values) {
            if (value.get() == 0){
                return;
            }
            sum += value.get();
        }
        //设置这两个 推荐的可能性，越大表示好友可能越高
        intWritable.set(sum);
        context.write(key,intWritable);

    }
}
