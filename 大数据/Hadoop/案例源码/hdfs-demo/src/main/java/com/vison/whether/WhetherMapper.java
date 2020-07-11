package com.vison.whether;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @author Vison
 * @date 2020/7/7 11:43
 * @Version 1.0
 */
public class WhetherMapper extends Mapper<LongWritable,Text,Whether, IntWritable> {


    Whether whether = new Whether(); //这里不存在全局数据异常，write的时候会序列化到缓冲区
    IntWritable intWritable = new IntWritable();
    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        // 1999-01-03 12:09:12   34c
        // 1999-01-03 12:09:12   38c
        // 1999-01-03 12:09:12   36c
        // 1999-01-02 12:09:12   37c
        // 2012-01-03 12:09:12   39c
        // 2012-01-03 11:09:12   41c
        // 2012-01-03 13:09:12   34c
        // 2002-01-04 12:09:12   40c

        String str = value.toString();
        String dateStr = str.split(" {3}")[0].split(" ")[0];
        LocalDate localDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        whether.setYear(localDate.getYear());
        whether.setMonth(localDate.getMonthValue());
        whether.setDay(localDate.getDayOfMonth());

        String s1 = str.split(" {3}")[1];
        String temp = s1.substring(0,s1.length() -1);
        whether.setTemp(Integer.valueOf(temp));

        //key是whether；value是温度
        intWritable.set(Integer.parseInt(temp));

        context.write(whether,intWritable);
    }

}
