# 一、MapReduce架构

## 1.MapReduce的整体执行流程

```text
1.输入文件

2.编写mapper处理逻辑

3.shuffle流程

4.编写reducer逻辑

5.输出文件
```

**简单图示：**

![ux2HK0.png](https://s2.ax1x.com/2019/10/13/ux2HK0.png)

**详细图示：**

![NOShZj.png](https://s1.ax1x.com/2020/07/03/NOShZj.png)

上面的两个不同的框代表不同的进程处理，一般放在不同的服务器上便于进程一直处理。

注意：

- 上面最开始block分成（合成）切片（1:1,1:N,N:1），然后切片和map的映射是1:1

- 每一个map做了对于map映射生成一个中间集（key,value,partion）然后进行排序，每一小块就是内部有序外部无序状态

  - 由于map这个步骤可以做许多优化，比如map在切分一小块排序放在缓存区上，然后通过合并这一块存储在磁盘上
  - 有可能map完成后有大量的数据，可能会造成shuffle拉取数据量过大，可以先在map进程中先做一次reduce操作，将重复的数据合并一次减少数据传输量

- shuffle过程将排序后的数据分别将属于不同的partion拉取到不同的reduce节点上，然后通过遍历拉取到的数据进行reduce。相同 的key一组，调用一次reduce方法迭代这一组数据进行计算

  

```
map:
   读懂数据
   映射为K,V模型
   并行分布式
   计算向数据移动

reduce：
   数据全量或者分量加工
   reduce可以包含不同的key
   相同的key汇聚到一个reduce中
   相同的key调用一次reduce方法
	 -排序实现key的汇聚
	 
reduce强依赖map的排序，map是什么排序，reduce就必须通过相同的排序做聚合

K,V使用自定义的数据类型：
   作为参数传递，节省开发程度，提高程序自由度
   Writable序列化：使能分布式程序数据交互
   Comparable比较强：实现具体排序（字典序，数值序等）
---------------------------------------------------

block:split:map:reduce:partion比例：
	block:split
		1:1
		N:1
		1:N
	split:map
		1:1
	map:reduce
		N:1
		N:N
		1:1
		1:N
	group(key):partion 
		1:1
		N:1
		N:N
		1:N 错误 违背规则
	partion -> outputfile



```



## 2. 运行架构

### 2.1 Hadoop  1.x版本架构

![NOVbND.png](https://s1.ax1x.com/2020/07/03/NOVbND.png)

```text
MapReduce 1.0版本角色:
- JobTracker
	●核心，主节点,单点
	●调度所有的作业
	●监控整个集群的资源负载
- TaskTracker
	●从节点，自身节点资源管理
	●和JobTracker心跳， 汇报资源，获取Task
- Client
	●作业为单位
	●规划作业计算分布
	●提交作业资源到HDFS
	●最终提交作业到JobTracker
	
弊端:
	● JobTracker: 负载过重，单点故障
	●资源管理与计算调度强耦合，其他计算框架需要重复实现资源管理
	●不同框架对资源不能全局管理

client将作业（jar包和相关计算配置等）提交到HDFS中，然后各自的TaskTracker获取task
```



### 2.2 Hadoop  2.x版本架构

使用yarn做来资源管理解决1.x的问题。

![NXi0fA.png](https://s1.ax1x.com/2020/07/03/NXi0fA.png)



步骤1：用户将应用程序提交到 ResourceManager 上；

步骤2：ResourceManager 为应用程序 ApplicationMaster 申请资源，并与某个 NodeManager 通信启动第一个 Container，以启动ApplicationMaster；

步骤3：ApplicationMaster 与 ResourceManager 注册进行通信，为内部要执行的任务申请资源，一旦得到资源后，将于 NodeManager 通信，以启动对应的 Task；

步骤4：所有任务运行完成后，ApplicationMaster 向 ResourceManager 注销，整个应用程序运行结束。



上图有两个Client，那么会启动两个Application master做任务调度container，注意上面不同的颜色表示不同的任务。

```text
●mapreduce 2.x版本运行在 On YARN
- YARN: 解耦资源与计算
	●ResourceManager
		-主节点，核心
		-集群节点资源管理
	●NodeManager
		-与RM汇报资源
		-管理Container生命周期
		-计算框架中的角色都以Container表示
	●Container: [节点NM, CPU,MEM,I/O大小， 启动命令]
		-默认NodeManager启动线程监控Container大小， 超出申请资源额度，kill
		-支持Linux内核的Cgroup
- MR(MapReduce):
	●MR-ApplicationMaster- Container
		-作业为单位， 避免单点故障，负载到不同的节点,挂掉在重新启动一个，不同作业有不同的AM
		-创建Task需要和RM申请资源 (Container)
	●Task-Container
- Client:
	●RM-Client: 请求资源创建AM
	●AM-Client: 与AM交互
```





# 二、案例



### 1.1 案例 - WordCount

单词统计的计算流程如下所示：

![NOSHzT.png](https://s1.ax1x.com/2020/07/03/NOSHzT.png)



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



