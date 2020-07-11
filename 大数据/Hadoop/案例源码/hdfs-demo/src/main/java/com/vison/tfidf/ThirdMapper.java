package com.vison.tfidf;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import java.io.IOException;
import java.net.URI;

/**
 * @author Vison
 * @date 2020/7/10 11:57
 * @Version 1.0
 */
public class ThirdMapper extends Mapper<LongWritable,Text, Text, IntWritable> {

    Text text = new Text();
    IntWritable intWritable = new IntWritable(1);
    @Override
    protected void map(LongWritable key,Text value, Context context) throws IOException, InterruptedException {
//排除最后一个文件-->  part-r-00003  ,_SUCCESS的总数计算
        String name = ((FileSplit) context.getInputSplit()).getPath().getName();
        if (name.contains("part-r-00003") || name.contains("SUCCESS")){
            System.out.println("file-execlude------->"+name);
            return;
        }

        //计算单词 在文件中所有的包含数  3
        // 9_享有	8
        // 4_享有	2

        //享有    1   一条记录返回一条数据表示在某一篇文章包含，在累计就是在所有文章中的占比数
        //享有    1
        String[] split = value.toString().split("\t");
        if (split.length == 2){
            String[] words = split[0].split("_");
            String word = words[1];

            text.set(word);
            context.write(text,intWritable);
        }

    }
}
