package com.vison.tfidf;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Vison
 * @date 2020/7/10 11:57
 * @Version 1.0
 */
public class ThirdReducer extends Reducer<Text, IntWritable, Text, Text> {


    Map<String, Integer> tfWordLineNums = new HashMap<>();   //每一行某个字符的个数  id_单词,num
    Map<String, Integer> tfLineNums = new HashMap<>();       //每一行所有字符的个数  id,nums

    long lineSums = 0;//总的行数

    Text text = new Text();

    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        URI[] cacheFiles = context.getCacheFiles();
        URI cacheFile1 = cacheFiles[0]; //  /user/root/tfidf/first/output
        URI cacheFile2 = cacheFiles[1]; //  /user/root/tfidf/second/output

        //读取第一个文件
        FileSystem fileSystem = FileSystem.get(context.getConfiguration());

        for (int i = 0; i < 3; i++) { // 0-2
            Path path1 = new Path(cacheFile1.getPath() + "/part-r-0000" + i);
            FSDataInputStream open = fileSystem.open(path1);
            DataInputStream d = new DataInputStream(open);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(d));
            String temp = "";
            while ((temp = bufferedReader.readLine()) != null) {  //first -0-2
                String[] split = temp.split("\t");
                String id_word = split[0];
                String id_word_nums = split[1];
                tfWordLineNums.put(id_word, Integer.valueOf(id_word_nums));
            }
        }

        //first -3
        Path path1 = new Path(cacheFile1.getPath() + "/part-r-00003");
        FSDataInputStream open = fileSystem.open(path1);
        DataInputStream d = new DataInputStream(open);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(d));
        String temp = bufferedReader.readLine();
        lineSums = Long.parseLong(temp.split("\t")[1]);


        //second-0
        Path path2 = new Path(cacheFile2.getPath() + "/part-r-00000");
        FSDataInputStream open1 = fileSystem.open(path2);
        DataInputStream d1 = new DataInputStream(open1);
        BufferedReader bufferedReader1 = new BufferedReader(new InputStreamReader(d1));

        while ((temp = bufferedReader1.readLine()) != null) {  //first -0-2
            String[] split = temp.split("\t");
            String id = split[0];
            String id_word_nums = split[1];
            tfLineNums.put(id, Integer.valueOf(id_word_nums));
        }
    }

    @Override
    protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {

        //key-->

        // tf 分子在first-part-r-0000[0-2] 中；tf分母在second-part-r-*中
        // idf 分子在first-part-r-00003 中

        //享有	1
        //享有	1
        //享有	1

        //享有   3
        int sum = 0;  //idf 分母
        for (IntWritable value : values) {
            sum += value.get();
        }

        double idf = (lineSums * 1.0 / sum * 1.0);

        Set<String> ids = tfLineNums.keySet();
        StringBuilder value = new StringBuilder();
        for (String string : ids) {
            Integer tfNums = tfWordLineNums.get(string + "_" + key.toString());
            if (tfNums != null) {
                double tf = tfNums * 1.0 / tfLineNums.get(string) * 1.0;
                double tf_idf = tf * Math.log(idf);
                value.append(string)
                        .append(":")
                        .append(tf_idf).append("\t");   //不同文章的tf_idf值
            }
        }

        //最终写出每一个单词在不同的文章中的tfidf值
        //单词   id:tfidf   id:tfidf   id:tfidf

        //错误	22:0.022715880007376732	34:0.09869658210101616
        //需要	22:0.019497902958899235	24:0.027603772728329257	10:0.04635350514757177
        //面积	23:0.04913471545642608	13:0.034121330178073665	21:0.0307091971602663
        //项目	12:0.03291988945823531
        //颁发	38:0.11110462692154417
        //预告	8:0.16930228864235303
        //首次	24:0.027603772728329257	19:0.023397483550679086	8:0.11698741775339543
        //齐全	22:0.05643409621411767  。。。。
        text.set(value.toString());
        context.write(key,text);

    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        super.cleanup(context);
    }



}
