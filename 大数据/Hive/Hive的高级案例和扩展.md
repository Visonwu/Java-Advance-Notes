## 1. 自定义函数

1.先编写自定义的函数

```xml
<dependency>
	<groupId>org.apache.hive</groupId>
	<artifactId>hive-exec</artifactId>
	<version>1.2.1</version>
</dependency>
```

		②自定义UDF函数，继承UDF类
		③提供evaluate()，可以提供多个重载的此方法，但是方法名是固定的
		④evaluate()不能返回void，但是可以返回null!
2.打包
3.安装
		在·`HIVE_HOME/auxlib` 目录下存放jar包！
4.创建函数

​		注意：用户自定义的函数，是有库的范围！指定库下创建的函数，只在当前库有效！库就是hive的数据库

```mysql
create [temporary] function 函数名  as  自定义的函数的全类名
```



5.举例

**例子1：**创建UDF 1对1函数

```java
package com.vison;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.hive.ql.exec.UDF;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * UDF 一对一 输入一，输出一
 *
 * @author Vison
 * @date 2020/9/4 13:57
 * @Version 1.0
 *
 * line 的值
 *  * 1829239293829|{
 *  * 	"cm": {
 *  * 		"ln": "-107.3",
 *  * 		"sv": "V2.7.3",
 *  * 		"os": "8.1.1",
 *  * 		"g": "3VR8AVWU@gmail.com",
 *  * 		"mid": "53",
 *  * 		"nw": "WIFI",
 *  * 		"l": "es",
 *  * 		"vc": "0",
 *  * 		"hw": "1080*1920",
 *  * 		"ar": "MX",
 *  * 		"uid": "53",
 *  * 		"t": "1598518800838",
 *  * 		"la": "-53.8",
 *  * 		"md": "Huawei-15",
 *  * 		"vn": "1.2.5",
 *  * 		"ba": "Huawei",
 *  * 		"sr": "R"
 *  *        },
 *  * 	"ap": "app",
 *  * 	"et": [{
 *  * 		"ett": "1598552161555",
 *  * 		"en": "display",
 *  * 		"kv": {
 *  * 			"goodsid": "16",
 *  * 			"action": "2",
 *  * 			"extend1": "1",
 *  * 			"place": "4",
 *  * 			"category": "34"
 *  *        }
 *  *    },{
 *  * 		"ett": "1598592519226",
 *  * 		"en": "favorites",
 *  * 		"kv": {
 *  * 			"course_id": 5,
 *  * 			"id": 0,
 *  * 			"add_time": "1598577718786",
 *  * 			"userid": 7
 *  *        }
 *  *    }]
 *  * }
 *  * line 格式： 时间戳|json数据
 *  * 1.取ap的值
 *  * 2.取时间值
 *  * 3.取cm中的公共数值
 *
 */
public class MyUDF  extends UDF {

    public String evaluate (final String line,String key) throws JSONException {
        if (StringUtils.isEmpty(line)){
            return "";
        }
        // ts 表示时间
        if (!line.contains(key) && !"ts".equals(key) ){
            return "";
        }
        String[] words = line.split("\\|");
        JSONObject jsonObject = new JSONObject(words[1]);
        if ("ts".equals(key)){
            return words[0].trim();
        }else if ("ap".equals(key)){
            return jsonObject.getString("ap");
        }else if ("et".equals(key)){
            return jsonObject.getString("et");
        }else{
            return jsonObject.getJSONObject("cm").getString(key);
        }
    }

    public static void main(String[] args) throws JSONException {

        String line = "1598598623100|{\"cm\":{\"ln\":\"-107.3\",\"sv\":\"V2.7.3\",\"os\":\"8.1.1\",\"g\":\"3VR8AVWU@gmail.com\",\"mid\":\"53\",\"nw\":\"WIFI\",\"l\":\"es\",\"vc\":\"0\",\"hw\":\"1080*1920\",\"ar\":\"MX\",\"uid\":\"53\",\"t\":\"1598518800838\",\"la\":\"-53.8\",\"md\":\"Huawei-15\",\"vn\":\"1.2.5\",\"ba\":\"Huawei\",\"sr\":\"R\"},\"ap\":\"app\",\"et\":[{\"ett\":\"1598552161555\",\"en\":\"display\",\"kv\":{\"goodsid\":\"16\",\"action\":\"2\",\"extend1\":\"1\",\"place\":\"4\",\"category\":\"34\"}},{\"ett\":\"1598585511276\",\"en\":\"newsdetail\",\"kv\":{\"entry\":\"2\",\"goodsid\":\"17\",\"news_staytime\":\"8\",\"loading_time\":\"36\",\"action\":\"4\",\"showtype\":\"5\",\"category\":\"85\",\"type1\":\"542\"}},{\"ett\":\"1598592852530\",\"en\":\"notification\",\"kv\":{\"ap_time\":\"1598556750942\",\"action\":\"1\",\"type\":\"4\",\"content\":\"\"}},{\"ett\":\"1598558409647\",\"en\":\"active_foreground\",\"kv\":{\"access\":\"\",\"push_id\":\"2\"}},{\"ett\":\"1598593407135\",\"en\":\"error\",\"kv\":{\"errorDetail\":\"java.lang.NullPointerException\\\\n    at cn.lift.appIn.web.AbstractBaseController.validInbound(AbstractBaseController.java:72)\\\\n at cn.lift.dfdf.web.AbstractBaseController.validInbound\",\"errorBrief\":\"at cn.lift.appIn.control.CommandUtil.getInfo(CommandUtil.java:67)\"}},{\"ett\":\"1598592519226\",\"en\":\"favorites\",\"kv\":{\"course_id\":5,\"id\":0,\"add_time\":\"1598577718786\",\"userid\":7}}]}";

        String ap = new MyUDF().evaluate(line, "ts");
        System.out.println(ap);
    }
}

```



**例子2：**创建UDTF 1对多函数

```java
package com.vison;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDTF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 一对多
 *
 * @author Vison
 * @date 2020/9/4 14:19
 * @Version 1.0
 *
 * 	"et": [{
 * 		"ett": "1598552161555",
 * 		"en": "display",
 * 		"kv": {
 * 			"goodsid": "16",
 * 			"action": "2",
 * 			"extend1": "1",
 * 			"place": "4",
 * 			"category": "34"
 *        }
 *    },{
 * 		"ett": "1598592519226",
 * 		"en": "favorites",
 * 		"kv": {
 * 			"course_id": 5,
 * 			"id": 0,
 * 			"add_time": "1598577718786",
 * 			"userid": 7
 *        }
 *    }]
 *
 * 将et数组，转换为多行数据
 * event_name=en
 * event_json=et数组中的一条
 *
 */
public class MyUDTF extends GenericUDTF {

    //在函数运行前调用一次，作用是告诉mapTask;当前函数返回的类型和个数；一边mapTask运行时会将函数返回值进行检查
    //当前函数，应该在initialize声明返回值类型为2列string类型的数据
    @Override
    public StructObjectInspector initialize(ObjectInspector[] argOIs) throws UDFArgumentException {
        //当前返回的两列字段别名；event_name;event_json
        List<String> fileName = new ArrayList<>();
        fileName.add("event_name");
        fileName.add("event_json");

        //当前两列的检查类型,java string 类型
        List<ObjectInspector> fieldOIs = new ArrayList<>();
        fieldOIs.add(PrimitiveObjectInspectorFactory.javaStringObjectInspector);
        fieldOIs.add(PrimitiveObjectInspectorFactory.javaStringObjectInspector);

        return ObjectInspectorFactory.getStandardStructObjectInspector(fileName,fieldOIs);
    }

    /**
     * 执行函数功能，处理数据返回结果；通过forward()返回结果
     * 返回的为2列N行的数据
     * @param args  传入的参数值；这里只有一个参数上面 注释中的数组数据
     * @throws HiveException
     */

    @Override
    public void process(Object[] args) throws HiveException {
        if (args == null || args.length ==0){
            return;
        }
        try {
            JSONArray jsonArray = new JSONArray(args[0].toString());
            if (jsonArray.length() == 0){
                return;
            }
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                try {
                    String[] result = new String[2];
                    result[0] = jsonObject.getString("en"); //第一列数据
                    result[1] = jsonObject.toString();         //第二列数据
                    super.forward(result);
                }catch (Exception ignore){ //避免其中一步影响全局数据
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    //函数调用完成之后处理一些清理和关闭操作
    @Override
    public void close() throws HiveException {

    }
}

```



基于上面打包上传到hive/auxlib/中，然后启动hive

```shell
#创建函数
hive>create function   as 'com.vison.MyUDF';
hive>create function flat_analizer as 'com.vison.MyUDTF';
# 使用函数获取数据
hive>select base_analizer(line,"ts") from ods_event_log limit 1;
hive>select flat_analizer(base_analizer(line,"et")) from ods_event_log limit 1;
```





## 2. snappy压缩

可以通过  `hadoop checknative` 查看本地 支持哪些压缩

前提需要先配置JAVA_HOME,MAVEN_HOME

```shell
1．准备编译环境
[root@hadoop101 software]# yum install svn
[root@hadoop101 software]# yum install autoconf automake libtool cmake
[root@hadoop101 software]# yum install ncurses-devel
[root@hadoop101 software]# yum install openssl-devel
[root@hadoop101 software]# yum install gcc*

2．编译安装snappy
[root@hadoop101 software]# tar -zxvf snappy-1.1.3.tar.gz -C /opt/module/
[root@hadoop101 module]# cd snappy-1.1.3/
[root@hadoop101 snappy-1.1.3]# ./configure
[root@hadoop101 snappy-1.1.3]# make
[root@hadoop101 snappy-1.1.3]# make install
# 查看snappy库文件
[root@hadoop101 snappy-1.1.3]# ls -lh /usr/local/lib |grep snappy

3．编译安装protobuf
[root@hadoop101 software]# tar -zxvf protobuf-2.5.0.tar.gz -C /opt/module/
[root@hadoop101 module]# cd protobuf-2.5.0/
[root@hadoop101 protobuf-2.5.0]# ./configure 
[root@hadoop101 protobuf-2.5.0]#  make 
[root@hadoop101 protobuf-2.5.0]#  make install
# 查看protobuf版本以测试是否安装成功
[root@hadoop101 protobuf-2.5.0]# protoc --version

4．编译hadoop native
[root@hadoop101 software]# tar -zxvf hadoop-2.7.2-src.tar.gz
[root@hadoop101 software]# cd hadoop-2.7.2-src/
[root@hadoop101 software]# mvn clean package -DskipTests -Pdist,native -Dtar -Dsnappy.lib=/usr/local/lib -Dbundle.snappy
执行成功后，/opt/software/hadoop-2.7.2-src/hadoop-dist/target/hadoop-2.7.2.tar.gz即为新生成的支持snappy压缩的二进制安装包。

5.将新生成的hadoop安装包解压，
	然编译后待snappy的  lib/native  替换掉原来hadoop中的native目录的文件
	再次执行 hadoop checknative 就发现支持snappy压缩了 
	
	
```



要在Hadoop中启用压缩，可以配置如下参数（mapred-site.xml文件中）：

| 参数                                              | 默认值                                                       | 阶段        | 建议                                         |
| ------------------------------------------------- | ------------------------------------------------------------ | ----------- | -------------------------------------------- |
| io.compression.codecs   （在core-site.xml中配置） | org.apache.hadoop.io.compress.DefaultCodec, org.apache.hadoop.io.compress.GzipCodec, org.apache.hadoop.io.compress.BZip2Codec,、org.apache.hadoop.io.compress.Lz4Codec | 输入压缩    | Hadoop使用文件扩展名判断是否支持某种编解码器 |
| mapreduce.map.output.compress                     | false                                                        | mapper输出  | 这个参数设为true启用压缩                     |
| mapreduce.map.output.compress.codec               | org.apache.hadoop.io.compress.DefaultCodec                   | mapper输出  | 使用LZO、LZ4或snappy编解码器在此阶段压缩数据 |
| mapreduce.output.fileoutputformat.compress        | false                                                        | reducer输出 | 这个参数设为true启用压缩                     |
| mapreduce.output.fileoutputformat.compress.codec  | org.apache.hadoop.io.compress. DefaultCodec                  | reducer输出 | 使用标准工具或者编解码器，如gzip和bzip2      |
| mapreduce.output.fileoutputformat.compress.type   | RECORD                                                       | reducer输出 | SequenceFile输出使用的压缩类型：NONE和BLOCK  |

**1) 开启map输出阶段压缩**

​	可以减少job中map和Reduce task间数据传输量。具体配置如下：

**案例实操：**

```mysql
1．开启hive中间传输数据压缩功能

hive (default)>set hive.exec.compress.intermediate=true;

2．开启mapreduce中map输出压缩功能

hive (default)>set mapreduce.map.output.compress=true;

3．设置mapreduce中map输出数据的压缩方式

hive (default)>set mapreduce.map.output.compress.codec=

 org.apache.hadoop.io.compress.SnappyCodec;

4．执行查询语句

	hive (default)> select count(ename) name from emp;
```



**1) 开启Reduce输出阶段压缩**

​			当Hive将输出写入到表中时，输出内容同样可以进行压缩。属性hive.exec.compress.output控制着这个功能。用户可能需要保持默认设置文件中的默认值false，这样默认的输出就是非压缩的纯文本文件了。用户可以通过在查询语句或执行脚本中设置这个值为true，来开启输出结果压缩功能。



案例实操：

```shell

1．开启hive最终输出数据压缩功能
hive (default)>set hive.exec.compress.output=true;

2．开启mapreduce最终输出数据压缩
hive (default)>set mapreduce.output.fileoutputformat.compress=true;

3．设置mapreduce最终数据输出压缩方式
hive (default)> set mapreduce.output.fileoutputformat.compress.codec =
 org.apache.hadoop.io.compress.SnappyCodec;
 
4．设置mapreduce最终数据输出压缩为块压缩,这个和数据格式有关，如果是textfile就不用设置了
hive (default)> set mapreduce.output.fileoutputformat.compress.type=BLOCK;


5．测试一下输出结果是否是压缩文件
hive (default)> insert overwrite local directory
 '/opt/module/datas/distribute-result' select * from emp distribute by deptno sort by empno desc;
```





## 3.文件存储格式

Hive支持的存储数的格式主要有：TEXTFILE 、SEQUENCEFILE、ORC、PARQUET。

在大数据中基本上都是基于列式存储数据的。



- TEXTFILE和SEQUENCEFILE的存储格式都是基于**行存储**的；

- ORC和PARQUET是基于**列式存储**的。



​		TEXTFILE默认格式，数据不做压缩，磁盘开销大，数据解析开销大。可结合Gzip、Bzip2使用，但使用Gzip这种方式，hive不会对数据进行切分，从而无法对数据进行并行操作。

SEQUENCEFILE是类似key，value的形式存储，所以也是行存储。



```
**ORC 和 PARQUET对比：**

- ORC：  hive独有，只有在hive中可以使用
  				ORC更优一点！ ORC的压缩比高！
  	
- PARQUET：  clodera公司提供的一个旨在整个hadoop生态系统中设计一个通用的高效的数据格式！
  					PARQUET格式的文件，不仅hive支持，hbase，impala，spark都支持！
- ORC和PARQUET都是行列结合，特定行作为一组，然后对于这一组做列式存储，其中也会有索引等信息便于快速查找。

**总结**
    ①如果表以TEXTFILE为格式存储数据，可以使用load的方式，否则都必须使用insert into
	②压缩比：     ORC>PARQUET>TEXTFILE
	③在查询速度上无明显差别
	④一般使用ORC(内部使用SNAPPY压缩）
	⑤如果使用Parquet(使用LZO压缩)
```



ORC存储方式的压缩：

| Key                      | Default    | Notes                                                        |
| ------------------------ | ---------- | ------------------------------------------------------------ |
| orc.compress             | ZLIB       | high level compression (one of NONE, ZLIB, SNAPPY)           |
| orc.compress.size        | 262,144    | number of bytes in each compression chunk                    |
| orc.stripe.size          | 67,108,864 | number of bytes in each stripe                               |
| orc.row.index.stride     | 10,000     | number of rows between index entries (must be >= 1000)       |
| orc.create.index         | true       | whether to create row indexes                                |
| orc.bloom.filter.columns | ""         | comma separated list of column names for which bloom filter should be created |
| orc.bloom.filter.fpp     | 0.05       | false positive probability for bloom filter (must >0.0 and <1.0) |



## 4.ETL

**ETL**是英文Extract-Transform-Load的缩写，用来描述将数据从来源端经过抽取（extract）、转换（transform）、加载（load）至目的端的过程。

```text
如何进行ETL： 
    ①写shell脚本使用awk,sed这些工具
    ②写java程序进行ETL（我们采用）
    ③使用一些专业的ETL工具，例如kettle
```


```text
-- 比如如下数据，最后两个属于数组不过是通过\t分隔，People & Blogs 也是数组但是通过&分隔，而且&两边还有空格，造成不统一，无法统一load 为hive表形式，所以需要通过ETL把数据转换为目标格式，load进入hive中。

RX24KLBhwMI	lemonette	697	People & Blogs	512	24149	4.22	315	474	t60tW0WevkE	WZgoejVDZlo
```





## 5.综合 案例

### 5.1 表分析

数据结构

**视屏表**

| 字段        | 备注       | 详细描述               |
| ----------- | ---------- | ---------------------- |
| video id    | 视频唯一id | 11位字符串             |
| uploader    | 视频上传者 | 上传视频的用户名String |
| age         | 视频年龄   | 视频在平台上的整数天   |
| category    | 视频类别   | 上传视频指定的视频分类 |
| length      | 视频长度   | 整形数字标识的视频长度 |
| views       | 观看次数   | 视频被浏览的次数       |
| rate        | 视频评分   | 满分5分                |
| ratings     | 流量       | 视频的流量，整型数字   |
| conments    | 评论数     | 一个视频的整数评论数   |
| related ids | 相关视频id | 相关视频的id，最多20个 |

原数据类似：

```
Gm6XszMrw9Y	mrmintel	731	Film & Animation	115	1227020	3.22	2442	542	GlAiXdaUYJw	IrmOYh_L5ic	Gm6XszMrw9Y
YjeAkUKPp-w	McDonaldsSnackWrap	731	Entertainment	10	367386	2.6	3071	357	YjeAkUKPp-w	g6P_iTlLaVQ
```



**用户表：**

| 字段     | 备注         | 字段类型 |
| -------- | ------------ | -------- |
| uploader | 上传者用户名 | string   |
| videos   | 上传视频数   | int      |
| friends  | 朋友数量     | int      |

数据类似

```json
barelypolitical	151	5106
bonk65	89	144
camelcars	26	674
cubskickass34	13	126
boydism08	32	50
```



### 5.2 数据清洗

由上面发现需要数据清洗

```java
package com.vison.gulivideo;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;

/**
 * @author Vison
 * @date 2020/7/17 9:17
 * @Version 1.0
 */
public class GuliVideoMain {

    public static void main(String[] args) throws Exception{
        Configuration configuration = new Configuration(true);
         configuration.set("fs.defaultFS","hdfs://192.168.4.131:9000");
        Job job= Job.getInstance(configuration,"guli-video");
        //通过类名打成jar包
        job.setJarByClass(GuliVideoMain.class);

        //1.输入文件
        FileInputFormat.addInputPath(job,new Path("/user/root/gulivideo"));
        //2.编写mapper处理逻辑
        job.setMapperClass(GuliVideoMapper.class);
        job.setMapOutputKeyClass(String.class);
        job.setMapOutputValueClass(NullWritable.class);
        job.setNumReduceTasks(0);

        //5.输出文件
        Path output = new Path("/user/root/gulivideo-output");
        if (output.getFileSystem(configuration).exists(output)){
            output.getFileSystem(configuration).delete(output,true);
        }
        FileOutputFormat.setOutputPath(job,output);

        //6.运行
        job.waitForCompletion(true);
    }


    /**
     *
     * mapper 做ETL数据清洗
     *
     * @author Vison
     * @date 2020/7/17 9:17
     * @Version 1.0
     */
    public static class GuliVideoMapper extends Mapper<LongWritable, Text,String, NullWritable> {

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            //atfNL0_KAcs	f0xmuld3r	454	Howto & DIY	102	47718	4.84
            // 101	44	tt3W6X8971o	kTfYttriolI	pdyYe7sDlhA
            String str = convertText(value);
            if (null != str){
                context.write(str,NullWritable.get());
            }
        }

        private String convertText(Text value) {
            StringBuilder sb = new StringBuilder();
            String[] tokens = value.toString().split("\t");
            if (tokens.length <9){
                return null;
            }
            tokens[3] = tokens[3].replaceAll(" ","");
            for (int i = 0; i < tokens.length; i++) {
                if (i < 9){
                    if (i == tokens.length -1){
                        sb.append(tokens[i]);
                    }else {
                        sb.append(tokens[i]).append("\t");
                    }
                }else{
                    if (i == tokens.length -1){
                        sb.append(tokens[i]);
                    }else {
                        sb.append(tokens[i]).append("&");
                    }

                }
            }
            return sb.toString();
        }
    }

}

```



### 5.3 建表和数据导入

```mysql
-- 1） 表创建
-- 创建表：gulivideo_ori，gulivideo_user_ori，
-- orc创建表：gulivideo_orc，gulivideo_user_orc

create table gulivideo_ori(videoId string, uploader string, age int, category array<string>, 
    length int, 
    views int, 
    rate float, 
    ratings int, 
    comments int,
    relatedId array<string>)
row format delimited fields terminated by '\t' collection items terminated by '&' stored as textfile;

create table gulivideo_orc(videoId string, uploader string, age int, category array<string>, 
    length int, 
    views int, 
    rate float, 
    ratings int, 
    comments int,
    relatedId array<string>) 
row format delimited fields terminated by '\t' collection items terminated by '&' stored as orc;

create table gulivideo_user_ori(uploader string,videos int,friends int) row format delimited fields terminated by '\t' stored as textfile;
create table gulivideo_user_orc(uploader string,videos int,friends int) row format delimited fields terminated by '\t' stored as orc;



-- 2）数据导入，orc的格式不能通过load导入，所以insert来实现
load data inpath '/user/root/gulivideo-output' into table gulivideo_ori;
insert into gulivideo_orc select * from gulivideo_ori;

load data local inpath '/usr/local/hadoop-2.7.7/data/gulivideo/user.txt' into table gulivideo_user_ori;
insert into gulivideo_user_orc select * from gulivideo_user_ori;
```



### 5.4 需求解决

```
统计硅谷影音视频网站的常规指标，各种TopN指标：
--统计视频观看数Top10
--统计视频类别热度Top10
--统计视频观看数Top20所属类别
--统计视频观看数Top50所关联视频的所属类别Rank
--统计每个类别中的视频热度Top10
--统计每个类别中视频流量Top10
--统计上传视频最多的用户Top10以及他们上传的视频
--统计每个类别视频观看数Top10
```

**视图创建**

```mysql
create view categoryView  as select 语句 
```

**解决：**

```mysql
-- 统计视频观看数Top10
	select * from gulivideo_orc order by views desc limit 10;
	
	
-- 统计视频类别热度Top10
	select category_new,sum(views)as total_views from (
		select views,category_new from gulivideo_orc lateral view explode(category) table_tmp as category_new 
	) t1 
	group by category_new order by total_views desc limit 10;
	
-- 统计出视频观看数最高的20个视频的所属类别以及类别包含Top20视频的个数

	创建视图：
		create view categoryView as
		select  videoid,categoryName,views
		from  gulivideo_ori
		lateral view explode(category) tmp as  categoryName
	
	
	select videoId,category,views from gulivideo_orc order by views desc limit 20;		//t1 获取top20
	select category_new,count(*) from t1 lateral view explode(category) table_tmp as category_new group by category_new ;
		

-- 统计视频观看数Top50所关联视频的所属类别Rank
	
	select videoId,category,views,relatedid from gulivideo_orc  order by views desc limit 50;  //top50  t1
	
	select distinct(related_videoId) from t1 lateral view explode(relatedid) table_tmp as related_videoId; // top50关联的视频id  t2
	
	select a.videoId,a.views, rank() over(order by views desc ) from t2 left join  gulivideo_orc a on t2.related_videoId = a.videoid ;    //最终结果统计

	select a.videoId,a.views, rank() over(order by views desc ) from (select distinct(related_videoId) from (select videoId,category,views,relatedid from gulivideo_orc  order by views desc limit 50)t1 lateral view explode(relatedid) table_tmp as related_videoId)t2 left join  gulivideo_orc a on t2.related_videoId = a.videoid;
	

-- 统计每个类别中的视频热度Top10
	select *,rank() over(partition by categoryName order by view  ) rank from categoryView  //每一个视频在每一个类别中的排名  //t1; categoryView前面创建的视图
	
	select * from t1 where rank <=10;


-- 统计每个类别中视频流量Top10
		select  videoid,categoryName,ratings from  gulivideo_ori lateral view explode(category) tmp as  categoryName;  //t1 按照类别列转行
		
		select *,rank() over(partition by categoryName order by ratings ) rank from t1;   //t2
		select * from t2 where rank <=10;
		
		
-- 统计上传视频最多的用户Top10以及他们上传的视频
	select uploader from gulivideo_user_orc order by videos desc limit 10;   //t1 上传top10
	
	select * from t1 left join gulivideo_orc a on t1.uploader = a.uploader  //

-- 统计每个类别视频观看数Top10

```







## 6.调优

### 6.1 Fetch抓取

​		Fetch抓取是指，Hive中对某些情况的查询可以不必使用MapReduce计算。例如：`SELECT * FROM employees`;在这种情况下，Hive可以简单地读取employee对应的存储目录下的文件，然后输出查询结果到控制台。

​	在hive-default.xml.template文件中hive.fetch.task.conversion默认是more，老版本hive默认是minimal，该属性修改为more以后，在全局查找、字段查找、limit查找等都不走mapreduce。

```xml
<property>
    <name>hive.fetch.task.conversion</name>
    <value>more</value>
    <description>
      Expects one of [none, minimal, more].
      Some select queries can be converted to single FETCH task minimizing latency.
      Currently the query should be single sourced not having any subquery and should not have
      any aggregations or distincts (which incurs RS), lateral views and joins.
      0. none : disable hive.fetch.task.conversion
      1. minimal : SELECT STAR, FILTER on partition columns, LIMIT only
      2. more  : SELECT, FILTER, LIMIT only (support TABLESAMPLE and virtual columns)
    </description>
  </property>
```



### 6.2 严格模式

Hive提供了一个严格模式，可以防止用户执行那些可能意想不到的不好的影响的查询。

​	通过设置属性hive.mapred.mode值为默认是非严格模式nonstrict 。开启严格模式需要修改hive.mapred.mode值为strict，开启严格模式可以禁止3种类型的查询。

```xml
<property>
    <name>hive.mapred.mode</name>
    <value>strict</value>
    <description>
      The mode in which the Hive operations are being performed. 
      In strict mode, some risky queries are not allowed to run. They include:
        Cartesian Product.
        No partition being picked up for a query.
        Comparing bigints and strings.
        Comparing bigints and doubles.
        Orderby without limit.
</description>
</property>
```

- 1) 对于分区表，**除非where语句中含有分区字段过滤条件来限制范围，否则不允许执行**。换句话说，就是用户不允许扫描所有分区。进行这个限制的原因是，通常分区表都拥有非常大的数据集，而且数据增加迅速。没有进行分区限制的查询可能会消耗令人不可接受的巨大资源来处理这个表。

- 2) 对于**使用了order by语句的查询，要求必须使用limit语句**。因为order by为了执行排序过程会将所有的结果数据分发到同一个Reducer中进行处理，强制要求用户增加这个LIMIT语句可以防止Reducer额外执行很长一段时间。

- 3) **限制笛卡尔积的查询**。对关系型数据库非常了解的用户可能期望在执行JOIN查询的时候不使用ON语句而是使用where语句，这样关系数据库的执行优化器就可以高效地将WHERE语句转化成那个ON语句。不幸的是，Hive并不会执行这种优化，因此，如果表足够大，那么这个查询就会出现不可控的情况。



### 6.3 JVM 重用

JVM重用是Hadoop调优参数的内容，其对Hive的性能具有非常大的影响，特别是对于很难避免**小文件的场景或task特别多的场景，这类场景大多数执行时间都很短。**

Hadoop的默认配置通常是使用派生JVM来执行map和Reduce任务的。这时JVM的启动过程可能会造成相当大的开销，尤其是执行的job包含有成百上千task任务的情况。**JVM重用可以使得JVM实例在同一个job中重新使用N次**。N的值可以在Hadoop的mapred-site.xml文件中进行配置。通常在10-20之间，具体多少需要根据具体业务场景测试得出。

```xml
<property>
  <name>mapreduce.job.jvm.numtasks</name>
  <value>10</value>
  <description>How many tasks to run per jvm. If set to -1, there is
  no limit. 
  </description>
</property>
```

​	这个功能的缺点是，开启JVM重用将一直占用使用到的task插槽，以便进行重用，直到任务完成后才能释放。如果某个“不平衡的”job中有某几个reduce task执行的时间要比其他Reduce task消耗的时间多的多的话，那么保留的插槽就会一直空闲着却无法被其他的job使用，直到所有的task都结束了才会释放。



### 6.4 **推测执行**

在分布式集群环境下，因为程序Bug（包括Hadoop本身的bug），负载不均衡或者资源分布不均等原因，会造成同一个作业的多个任务之间运行速度不一致，有些任务的运行速度可能明显慢于其他任务（比如一个作业的某个任务进度只有50%，而其他所有任务已经运行完毕），则这些任务会拖慢作业的整体执行进度。为了避免这种情况发生，Hadoop采用了推测执行（Speculative Execution）机制，它根据一定的法则推测出“拖后腿”的任务，并为这样的任务启动一个备份任务，让该任务与原始任务同时处理同一份数据，并最终选用最先成功运行完成任务的计算结果作为最终结果。

设置开启推测执行参数：Hadoop的mapred-site.xml文件中进行配置

```xml
<property>
  <name>mapreduce.map.speculative</name>
  <value>true</value>
  <description>If true, then multiple instances of some map tasks 
               may be executed in parallel.</description>
</property>

<property>
  <name>mapreduce.reduce.speculative</name>
  <value>true</value>
  <description>If true, then multiple instances of some reduce tasks 
               may be executed in parallel.</description>
</property>
```

不过hive本身也提供了配置项来控制reduce-side的推测执行：

```xml
  <property>
    <name>hive.mapred.reduce.tasks.speculative.execution</name>
    <value>true</value>
    <description>Whether speculative execution for reducers should be turned on. </description>
  </property>
```

​		关于调优这些推测执行变量，还很难给一个具体的建议。如果用户对于运行时的偏差非常敏感的话，那么可以将这些功能关闭掉。如果用户因为输入数据量很大而需要执行长时间的map或者Reduce task的话，那么启动推测执行造成的浪费是非常巨大大。



### 6.5 执行计划 EXPLAIN

1．基本语法

​		EXPLAIN [EXTENDED | DEPENDENCY | AUTHORIZATION] query

2．案例实操

（1）查看下面这条语句的执行计划

```mysql
hive (default)> explain select * from emp;

hive (default)> explain select deptno, avg(sal) avg_sal from emp group by deptno;
```

（2）查看详细执行计划

```mysql
hive (default)> explain extended select * from emp;

hive (default)> explain extended select deptno, avg(sal) avg_sal from emp group by deptno;
```



### 6.6 数据倾斜

####  1）合理设置Map数

**1）通常情况下，作业会通过input的目录产生一个或者多个map任务。**

主要的决定因素有：input的文件总个数，input的文件大小，集群设置的文件块大小。

**2）是不是map数越多越好？**

答案是否定的。如果一个任务有很多小文件（远远小于块大小128m），则每个小文件也会被当做一个块，用一个map任务来完成，而一个map任务启动和初始化的时间远远大于逻辑处理的时间，就会造成很大的资源浪费。而且，同时可执行的map数是受限的。

**3）是不是保证每个map处理接近128m的文件块，就高枕无忧了？**

答案也是不一定。比如有一个127m的文件，正常会用一个map去完成，但这个文件只有一个或者两个小字段，却有几千万的记录，如果map处理的逻辑比较复杂，用一个map任务去做，肯定也比较耗时。

针对上面的问题2和3，我们需要采取两种方式来解决：即减少map数和增加map数；



#### 2）小文件进行合并

​		在map执行前合并小文件，减少map数：CombineHiveInputFormat具有对小文件进行合并的功能（系统默认的格式）。HiveInputFormat没有对小文件合并功能。

set hive.input.format= org.apache.hadoop.hive.ql.io.CombineHiveInputFormat;



#### 3）复杂文件增加Map数

​		当input的文件都很大，任务逻辑复杂，map执行非常慢的时候，可以考虑增加Map数，来使得每个map处理的数据量减少，从而提高任务的执行效率。

增加map的方法为：根据computeSliteSize(Math.max(minSize,Math.min(maxSize,blocksize)))=blocksize=128M公式，调整maxSize最大值。让maxSize最大值低于blocksize就可以增加map的个数。



#### 4）合理设置Reduce数

**1．调整reduce个数方法一**

（1）每个Reduce处理的数据量默认是256MB

​			hive.exec.reducers.bytes.per.reducer=256000000

（2）每个任务最大的reduce数，默认为1009

​			hive.exec.reducers.max=1009

（3）计算reducer数的公式

​			N=min(参数2，总输入数据量/参数1)



**2．调整reduce个数方法二**

在hadoop的mapred-default.xml文件中修改

设置每个job的Reduce个数

​		set mapreduce.job.reduces = 15;



**3．reduce个数并不是越多越好**

1）过多的启动和初始化reduce也会消耗时间和资源；

2）另外，有多少个reduce，就会有多少个输出文件，如果生成了很多个小文件，那么如果这些小文件作为下一个任务的输入，则也会出现小文件过多的问题；

​		在设置reduce个数的时候也需要考虑这两个原则：处理大数据量利用合适的reduce数；使单个reduce任务处理数据量大小要合适；



#### 5） ORC 和Parquet切片

1.ORC是否可以切片？
		ORC不管是否用压缩，都可以切片！
		ORC+Snappy
		

		ORC可以切片！
			如果使用的是TextInputFormat，TextInputFormat根据文件的后缀判断是否是一个压缩格式，只要不是压缩格式，都可切！
				如果是压缩格式，再判断是否使用的是可切的压缩格式类型！
				
		如果表在创建时，使用store as orc,此时这个表的输入格式会使用OrcInputFormat！
			OrcInputFormat.getSplits()方法中，文件是可以切片的，即使使用snappy压缩，也可切！

2.Parquet是否切片？

		Parquet文件不使用LZO压缩，可以切！
		Parquet如果使用了LZO压缩，必须创建index后才可切！
	
		如果表在创建时，使用store as Parquet,此时这个表的输入格式会使用ParquetInputFormat！
				ParquetInputFormat继承了FileInputFormat!
				 并没有重写isSplitable()方法啊，FileInputFormat.isSplitable(){return true};
				 
		Parquet文件格式在切片时，也可以切！
		
		Parquet+LZO格式的文件，在切片时是可以切！但是通常我们还会为此文件创建Index!
				创建索引的目的是，在读入文件时，使用LZO合理的切片策略，而不是默认的切片策略！
				因为如果表的存储为Parquet+LZO，此时表的输入格式已经不能设置为ParquetInputFormat，而需要设置为LZOInputFormat！


### 6.7 表优化

#### 	1）**小表、大表Join**

​		将key相对分散，并且数据量小的表放在join的左边，这样可以有效减少内存溢出错误发生的几率；再进一步，可以使用map join让小的维度表（1000条以下的记录条数）先进内存。在map端完成reduce。

实际测试发现：新版的hive已经对小表JOIN大表和大表JOIN小表进行了优化。小表放在左边和右边已经没有明显区别。



#### 2） 空Key问题

​	因为join的时候，mapreduce是根据on的字段做分区的，如果字段出现大量的null，那么会出现某一个分区大量的null值。然后也存在现象，是否保留该字段有null的数据

```mysql
如果A表中有大量c字段为null的数据。如果不对null值处理，此时，会产生数据倾斜！
	
情形一：A left join B  on A.c = b.c
		
		假如不需要id为null的数据！此时可以将A表中id为null的字段提前过滤，减少MR在执行时，输入的数据量！
		
		解决：	将null值过滤，过滤后再执行Join!
			(select * from A where c is not null)A left join B  on A.c = b.c
			
情形二： A表中c字段为null的数据也需要，不能过滤，如何解决数据倾斜？
			注意：①可以将null替换为一个不影响执行结果的随机值！
				 ②注意转换后类型匹配的问题
				insert overwrite local directory '/home/atguigu/joinresult'
				select n.* from nullidtable n full join ori o on 
				case when n.id is null then -floor(rand()*100) else n.id end = o.id;
```




#### 4） MapJoin

​			如果不指定MapJoin或者不符合MapJoin的条件，那么Hive解析器会将Join操作转换成Common Join，即：在Reduce阶段完成join。容易发生数据倾斜。可以用MapJoin把小表全部加载到内存在map端进行join，避免reducer处理。

开启MapJoin参数设置

（1）设置自动选择Mapjoin

​			set hive.auto.convert.join = true; 默认为true

（2）大表小表的阈值设置（默认25M一下认为是小表）：

​		set hive.mapjoin.smalltable.filesize=25000000;



#### 5）**Group By**

​		默认情况下，Map阶段同一Key数据分发给一个reduce，当一个key数据过大时就倾斜了。

​		并不是所有的聚合操作都需要在Reduce端完成，很多聚合操作都可以先在Map端进行部分聚合，最后在Reduce端得出最终结果。



**开启Map端聚合参数设置**

​	（1）是否在Map端进行聚合，默认为True

​			hive.map.aggr = true

（2）在Map端进行聚合操作的条目数目

​			hive.groupby.mapaggr.checkinterval = 100000

（3）有数据倾斜的时候进行负载均衡（默认是false）

​			hive.groupby.skewindata = true

   

​		 当选项设定为 true，生成的查询计划会有两个MR Job。第一个MR Job中，Map的输出结果会随机分布到Reduce中，每个Reduce做部分聚合操作，并输出结果，这样处理的结果是相同的Group By Key有可能被分发到不同的Reduce中，从而达到负载均衡的目的；第二个MR Job再根据预处理的数据结果按照Group By Key分布到Reduce中（这个过程可以保证相同的Group By Key被分布到同一个Reduce中），最后完成最终的聚合操作。



#### 6） **Count(Distinct) 去重统计**

​				数据量小的时候无所谓，数据量大的情况下，由于COUNT DISTINCT操作需要用一个Reduce Task来完成，这一个Reduce需要处理的数据量太大，就会导致整个Job很难完成，一般COUNT DISTINCT使用先GROUP BY再COUNT的方式替换：

```mysql
select count(distinct id) from bigtable;

-- 采用GROUP by去重id
select count(id) from (select id from bigtable group by id) a;
```

虽然会多用一个Job来完成，但在数据量大的情况下，这个绝对是值得的。



#### 7) 笛卡尔积

​		尽量避免笛卡尔积，join的时候不加on条件，或者无效的on条件，Hive只能使用1个reducer来完成笛卡尔积。



#### 8) 行列过滤

- 列处理：在SELECT中，只拿需要的列，如果有，尽量使用分区过滤，少用SELECT *。

- 行处理：在分区剪裁中，当使用外关联时，如果将副表的过滤条件写在Where后面，那么就会先全表关联，之后再过滤，比如：

**案例实操：**

1．测试先关联两张表，再用where条件过滤

```mysql
hive (default)> select o.id from bigtable b
					join ori o on o.id = b.id
					where o.id <= 10;

Time taken: 34.406 seconds, Fetched: 100 row(s)
```



2．通过子查询后，再关联表

```mysql
hive (default)> select b.id from bigtable b
				join (select id from ori where id <= 10 ) o on b.id = o.id;

Time taken: 30.058 seconds, Fetched: 100 row(s)
```



#### 9） 动态分区

​			关系型数据库中，对分区表Insert数据时候，数据库自动会根据分区字段的值，将数据插入到相应的分区中，Hive中也提供了类似的机制，即动态分区(Dynamic Partition)，只不过，使用Hive的动态分区，需要进行相应的配置。

静态分区：		load data local inpath 'xx' into table xxx partition(分区列名=分区列值)
					在导入数据时，分区的名称已经确定！
					
动态分区：   在导入数据时，根据数据某一列字段的值是什么，就自动创建分区！
		      1）动态分区需要在动态分区非严格模式才能运行！
						set hive.exec.dynamic.partition.mode=nonstrict;
	          2）动态分区只能使用insert方式导入数据
	          3）注意字段的顺序问题，分区列必须位于最后一个字段！		



开启动态分区参数设置：

```mysql
（1）开启动态分区功能（默认true，开启）
		hive.exec.dynamic.partition=true
（2）设置为非严格模式（动态分区的模式，默认strict，表示必须指定至少一个分区为静态分区，nonstrict模式表示允许所有的分区字段都可以使用动态分区。）
		hive.exec.dynamic.partition.mode=nonstrict
（3）在所有执行MR的节点上，最大一共可以创建多少个动态分区。
		hive.exec.max.dynamic.partitions=1000
（4）在每个执行MR的节点上，最大可以创建多少个动态分区。该参数需要根据实际的数据来设定。比如：源数据中包含了一年的数据，即day字段有365个值，那么该参数就需要设置成大于365，如果使用默认值100，则会报错。
		hive.exec.max.dynamic.partitions.pernode=100
（5）整个MR Job中，最大可以创建多少个HDFS文件。
        hive.exec.max.created.files=100000		        
 (6）当有空分区生成时，是否抛出异常。一般不需要设置。
		hive.error.on.empty.partition=false      
 
 
 (7) 建表
 create external table if not exists default.emp2(
empno int,ename string,job string,mgr int,hiredate string, sal double, 
comm double)
partitioned by(deptno int)
row format delimited fields terminated by '\t';

（8）导入数据，下面的查询出来的emp表中最后一个字段需要是分区字段,不然下面的* 需要转换为具体的每一个字段，把分区字段放最后一个
 insert into table emp2 partition(deptno)  select * from emp;
```

当然我们经常使用分区使用的时间分区



































