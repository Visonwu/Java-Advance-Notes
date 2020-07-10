package com.vison.tfidf;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.SplitLocationInfo;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import java.io.IOException;

/**
 * 拆分 id_单词
 *
 * @author Vison
 * @date 2020/7/10 11:48
 * @Version 1.0
 */
public class SecondMapper extends Mapper<LongWritable, Text,Text, IntWritable> {

    Text text = new Text();
    IntWritable intWritable = new IntWritable(0);
    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

        //排除最后一个文件-->  part-r-00003  ,_SUCCESS的总数计算
        String name = ((FileSplit) context.getInputSplit()).getPath().getName();
        if (name.contains("part-r-00003") || name.contains("SUCCESS")){
            System.out.println("file-execlude------->"+name);
            return;
        }


        //计算每一篇文章总的个数 id_单词  3
        String[] split = value.toString().split("\t");
        if (split.length == 2){
            String[] words = split[0].split("_");
            String id = words[0];

            String num = split[1];
            text.set(id);
            intWritable.set(Integer.parseInt(num));
            context.write(text,intWritable);
        }
    }
}
