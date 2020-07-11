package com.vison.tfidf;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

/**
 * @author Vison
 * @date 2020/7/10 11:09
 * @Version 1.0
 */
public class FirstReducer extends Reducer<Text, IntWritable,Text,IntWritable> {

    IntWritable intWritable = new IntWritable();
    @Override
    protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {

        int sum = 0;
        for (IntWritable value : values) {
            sum+=value.get();
        }
        //每个文章的单词   id_我们   3   -->一片文章总的单词数
        //总的文章条数    count   11    -->

        //?单词出现在文档的个数
        intWritable.set(sum);
        context.write(key,intWritable);
    }
}
