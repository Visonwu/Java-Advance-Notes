package com.vison.whether;


import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

/**
 * @author Vison
 * @date 2020/7/7 11:43
 * @Version 1.0
 */
public class WhetherReducer  extends Reducer<Whether, IntWritable, Text,IntWritable> {

    Text text = new Text();
    IntWritable intWritable = new IntWritable();

    @Override
    protected void reduce(Whether key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {

        int first = 0; //当前月最高温度
        int day = 0;
        // 一个月最高的两天温度
        for (IntWritable value : values) {
            if (first == 0){                        //当前月最高温度的一天
                text.set(getKeyStr(key));
                intWritable.set(key.getTemp());
                context.write(text,intWritable);
                first ++;
                day = key.getDay();
            }
            if (first != 0 && day !=key.getDay()){  //当前月温度排名 第二
                text.set(getKeyStr(key));
                intWritable.set(key.getTemp());
                context.write(text,intWritable);
                break;
            }
        }

    }

    private String getKeyStr(Whether key){
        Integer year = key.getYear();
        Integer month = key.getMonth();
        Integer day = key.getDay();
        return year+":"+month+":"+day;
    }
}
