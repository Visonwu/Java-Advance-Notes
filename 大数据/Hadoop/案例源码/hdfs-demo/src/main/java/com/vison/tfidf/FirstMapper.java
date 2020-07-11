package com.vison.tfidf;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vison
 * @date 2020/7/10 11:09
 * @Version 1.0
 */
public class FirstMapper extends Mapper<LongWritable, Text,Text, IntWritable> {

    Text text = new Text();
    IntWritable intWritable = new IntWritable(1);

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {


        String lines = value.toString();
        String[] split = lines.split("\t");
        if (split.length == 2){
            String id = split[0];
            String textLine = split[1];
            IKAnalyzer analyzer = new IKAnalyzer();
            analyzer.setUseSmart(true);

            List<String> words = analyzerResult(analyzer, textLine);
            //每个单词 id_词 1
            for (String str : words) {
                text.set(id+"_"+str);
                context.write(text,intWritable);
            }
            //记录文章数 count 1
            text.set("count");
            context.write(text,intWritable);
        }


    }


    public static List<String> analyzerResult(Analyzer analyzer, String keyword) throws IOException {
        List<String> resultData = new ArrayList<>();
        TokenStream tokenStream = analyzer.tokenStream("content",new StringReader(keyword));
        tokenStream.addAttribute(CharTermAttribute.class);
        tokenStream.reset();
        while(tokenStream.incrementToken()){
            CharTermAttribute charTermAttribute = tokenStream.getAttribute(CharTermAttribute.class);
            resultData.add(charTermAttribute.toString().trim());
        }
        return resultData;
    }

    public static void main(String[] args) {
        String line = "这是一个粗糙的栅栏，浪费钱，我想要一堵巨大的墙!”网友Mary说，还附上了“理想”中的边境墙照片";
        List<String> s = beginAnalyzer(line);
        System.out.println(s);

    }

    public static List<String> beginAnalyzer(String line){
        IKAnalyzer analyzer = new IKAnalyzer();

        //使用智能分词
        //ik2012和ik3.0,3.0没有这个方法
        analyzer.setUseSmart(true);  //细分拆分文章
       // analyzer.setUseSmart(false);  //细分拆分文章
        try {
            return analyzerResult(analyzer, line);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


}
