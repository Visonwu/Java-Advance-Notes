



## 1.MapReduce的整体执行流程

```text
1.输入文件

2.编写mapper处理逻辑

3.shuffle流程

4.编写reducer逻辑

5.输出文件
```

![ux2HK0.png](https://s2.ax1x.com/2019/10/13/ux2HK0.png)

### 1.1 案例 - WordCount

单词统计的计算流程如下所示：

![ux2XaF.png](https://s2.ax1x.com/2019/10/13/ux2XaF.png)



### 1.2 编写代码

```java
package com.vison.hadoopdemo.mapreduce;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;

/**
 * @author Vison
 * @date 2019/10/13 21:48
 * @Version 1.0
 */
public class WordCount {

  	//static {
    //    System.setProperty("hadoop.home.dir","D:\\software\\hadoop\\2.7.3");
    //}
    
    public static void main(String[] args) throws Exception {

        Configuration configuration = new Configuration();
        Job job= Job.getInstance(configuration,"vison-wordcount");
        //通过类名打成jar包
        job.setJarByClass(WordCount.class);

        //1.输入文件
        input(args[0],job);
        //2.编写mapper处理逻辑
        job.setMapperClass(MyMapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(LongWritable.class);

        //3.shuffle流程(暂不处理)

        //4.编写reducer逻辑
        job.setReducerClass(MyReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(LongWritable.class);

        //5.输出文件
        output(args[1],job);


        //6.运行
        boolean b = job.waitForCompletion(true);
        System.out.println(b);

    }

    private static void input(String path,Job job) throws  Exception{
        FileInputFormat.addInputPath(job,new Path(path));
    }

    private static void output(String path,Job job)throws  Exception{
        FileOutputFormat.setOutputPath(job,new Path(path));
    }

    /**
     * 默认MapReduce使用TextInputFormat 进行切片，并交给Mapper进行处理
     * TextInputFormat：
     *     key:当前行的首字母的索引(第一个为0，第二个为第一行字母的个数，第三行为第一行字母和第二行字母个数的和，后面依次为前面的相加)
     *     value:当前行的数据
     *
     *Mapper类参数：
     *      输入key类型：Long;输入Value类型：String;
     *      输出key类型：String;输出Value类型：Long;
     *
     */
    static class MyMapper extends Mapper<LongWritable,Text, Text,LongWritable>{

        /**
         * 比如要计算这些数据的个数
         * hadoop mapper good
         * good hello welcome
         * batch add beauty
         * terrible thanks
         * a a b b cc cc dd dd d ww
         * 将每行数据才分，拆分输出每个单词和个数
         */
        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            LongWritable longWritable = new LongWritable(1);
            String words = value.toString();
            //将每行数据拆分为单词,以空格为才分
            String[] wordArr = words.split(" ");
            //遍历各个单词
            for (String word : wordArr) {
                //输出格式《单词，1》
                context.write(new Text(word),longWritable);
            }


        }
    }


    /**
     * 全局聚合
     * Reducer参数：
     *      输入key类型：string；输入value类型：long
     *      输出key类型：string；输出value类型：long
     */
    static class MyReducer extends Reducer<Text, LongWritable,Text,LongWritable> {

        /**
         * 将map 输出结果进行全局聚合
         *      key：单词，values:个数[1,1,1,1]就是多个单词的数量
         */
        @Override
        protected void reduce(Text key, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException {

            Long sum = 0L;
            for (LongWritable value : values) {
                //累计单词数量
                sum+= value.get();
            }
            //输出结果
            context.write(key,new LongWritable(sum));
        }
    }
}
```



### 1.3 运行如上程序

​	

#### 1）windows 本地单机运行

​	需要本地配置两个输入和输出 `file:/C:/Users/vison/Desktop/click/words.txt file:/C:/Users/vison/Desktop/click/out`

- 下载hadoop程序包在bin目录下导入文件winutils和hadoop.dll

- 配置HADOOP_HOME环境变量（需要重启机器）或者临时设置环境变量 `System.setProperty("hadoop.home.dir","D:\\software\\hadoop\\hadoop-2.7.7");`
- 添加相同的jar包，修改NativeIO类，将access0调用处直接换成true.



#### 2) 远程调用服务器

​		本地代码直接调用hadoop远程服务器；需要本地配置两个输入和输出 `file:/C:/Users/vison/Desktop/click/words.txt file:/C:/Users/vison/Desktop/click/out`

后者file开头表示是本地文件，否则就是存储在hdfs中。

​	在上面的代码configuration中添加远程dfs服务器地址：

```java
//Configuration configuration = new Configuration();
configuration.set("fs.defaultFS","hdfs://192.168.109.133:8020");
```



#### 3 ） 打成jar包放到服务器运行

```bash
# 这里的hadoop.jar是打包上去的
root~> bin/hadoop jar hadoop-demo.jar com.vison.hadoopdemo.mapreduce.WordCount file:/usr/local/hadoop/NOTICE.txt file:/usr/local/hadoop/output

```



## 2.InputFormat 的API介绍

​     InputFormat主要是对输入文件进行切分，形成多个InputSplit文件，每一个InputSplit对应一个map，创建RecordReader，从InputSplit分片中读取数据共map使用

子类主要分为如下三种：

- DBInputFormat：主要是从数据库中读取数据
- FileInputFormat：文件中读取数据
  - TextInputFormat :文本读取，上面的例子就是使用这个类读取；key是这一行在文件中的 偏移量，value是这一行的内容
  - KeyValueInputFormat:键值对的方式读取；读取普通文件按照行分割，每一行由key和value组成，key和value的分隔符如果没有指定，那么只有key，value为空
  - SequenceInputFormat：序列化文件读取数据，比如图片二进制文件等；从sequence文件中读取，<key,value>键值对存放文件
  - NlineInputFormat:多行变成一个分片；可以将多行划分为一个split作为maptask输入
  - CombineInputFormat：将多个小文件合成一个分片



