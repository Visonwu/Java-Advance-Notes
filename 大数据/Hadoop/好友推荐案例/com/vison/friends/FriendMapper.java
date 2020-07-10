package com.vison.friends;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

/**
 * @author Vison
 * @date 2020/7/7 22:14
 * @Version 1.0
 */
public class FriendMapper extends Mapper<LongWritable, Text, Text, IntWritable> {


    Text text = new Text();
    IntWritable intWritable = new IntWritable();

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        //tom hello hadoop cat
        //思路：将互为好友的value置为0，同时在别人好友列表里置为1

        String str = value.toString();

        String[] names = str.split(" ");

        for (int i = 1; i < names.length; i++) {
            // 先写好友信息 value置为 0
            String comp = comp(names[0], names[i]);
            text.set(comp);
            intWritable.set(0);
            context.write(text, intWritable);

            //同时存储在别人 好友列表中，value记录为1
            for (int j = i + 1; j < names.length; j++) {
                String comp1 = comp(names[i], names[j]);
                text.set(comp1);
                intWritable.set(1);
                context.write(text, intWritable);
            }

        }

    }

    /**
     * 将两个str 按照字典序组合，部分先后顺序，便于map存储相同的key
     */
    private static String comp(String s1, String s2) {

        if (s1.compareTo(s2) < 0) {
            return s1 + ":" + s2;
        }
        return s2 + ":" + s1;

    }
}
