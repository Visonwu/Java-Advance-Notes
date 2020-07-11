package com.vison.tfidf;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

/**
 * 统计某一篇文章的单词数量
 *
 * @author Vison
 * @date 2020/7/10 11:48
 * @Version 1.0
 */
public class SecondReducer extends Reducer<Text, IntWritable,Text,IntWritable> {

    IntWritable intWritable = new IntWritable(0);
    @Override
    protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
        int sum = 0;
        for (IntWritable value : values) {
            sum+=value.get();
        }
        //每个文章的单词   id   3   -->一片文章总的单词数
        intWritable.set(sum);
        context.write(key,intWritable);
    }
}
