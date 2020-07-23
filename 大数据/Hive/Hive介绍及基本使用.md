# 1.Hive介绍

## 1.1 Hive是一个数据仓库软件
Hive可以使用SQL来促进对已经存在在分布式设备中的数据进行读，写和管理等操作！
	Hive在使用时，需要对已经存储的数据进行结构的投影(映射)
	Hive提供了一个命令行和JDBC的方式，让用户可以连接到hive！
	

```
注意：Hive只能分析结构化的数据！
	  Hive在Hadoop之上，使用hive的前提是先要安装Hadoop
```



## 1.2 Hive的特点

①Hive并不是一个关系型数据库
    ②不是基于OLTP(在线事务处理)设计
			OLTP设计的软件： 
					侧重点在事务的处理，和在线访问。一般RDMS都是基于OLTP设计
    ③Hive无法做到实时查询，不支持行级别更新(update,delete)
	

---

④Hive要分析的数据存储在HDFS，hive为数据创建的表结构(schema)，存储在RDMS
⑤Hive基于OLAP(在线分析处理)设计
		OLAP设计的软件：
				侧重点在数据的分析上，不追求分析的效率！
⑥Hive使用类SQL，称为HQL对数据进行分析
⑦Hive容易使用，可扩展，有弹性





## 1.3 Hive 本质

**Hive底层本质**就是MapReduce

- Hive处理的数据存储在HDFS中
- Hive分析数据底层实现是MapReduce
- 执行程序运行在yarn上





## 1.4 Hive优缺点

**优点：**

- 提供类SQL，能够快速上手
- 避免手写MapReduce，减少开发成本
- Hive执行延时较高，使用于数据分析，对实时性要求不高的场合
- 有时在于处理大数据，小数据没有优势



**缺点：**

- Hive的HQL表达能力有限；迭代算法无法表达，数据挖解方面不擅长
- Hive的效率比较低；自动生成MapReduce作业，通常不够智能化;Hive调优比较困难，粒度较粗



## 1.5 Hive的架构

如下事Hive的架构图，Meta Store存储的是数据的表结构图，客户的HQL会通过解析器-》编译器-》优化器-》执行器转换为MapReduce执行-》然后存储在HDFS中，最后结果输出。

![U1IfpR.png](https://s1.ax1x.com/2020/07/12/U1IfpR.png)



## 1.5 Hive和关系型数据比较

- 对于Hive有专门的SQL查询语言HQL
- 数据存储。Hive的数据都是存储在HDFS中；而数据库可以将数据存储在块设备上或者本地文件系统中
- 数据更新。Hive主要作为数据仓库设计的，所以读多写少，因此Hive的数据在加载就确定好了，而数据库则需要经常crud。
- 索引。Hive没有索引，基于mapreduce的并行计算处理
- 执行。Hive基于MapReduce来实现，而数据库通常有自己的执行引擎
- 执行延迟。Hive查询时是基于MapReduce框架，需要全表扫描。数据库数据量少的时候延迟低，
- 扩展性。Hive的扩展性和Hadoop一样，数据库由于ACID语义限制，扩展性有限





# 2.Hive安装和使用

Hive的安装
       ①保证有JAVA_HOME,HADOOP_HOME
       ②将bin配置到PATH中，在环境变量中提供HIVE_HOME

下载linux的Hive包，然后解压安装，配置环境变量HIVE_HOME及bin目录



## 2.1 Hive-1.x 版本

### 1) 初步使用

```sql
使用hive需要先清除 hdfs数据,否则报错执行不成功
1.删除 dfs数据存储目录
2.然后格式化 hdfs namenode -format
3.然后启动start-all.sh

-- 简单使用，先执行
~> hive
~> show databases
~> use default

~> create table person(name varchar(100),age int);
~> insert into person values('zhangshan',3);
~> select * from person;


// 文件存储在 /user/hive/warehouse/person下 （person就是我们创建的表）
```



1.Hive要分析的数据是存储在HDFS上
			hive中的库的位置，在hdfs上就是一个目录！
			hive中的表的位置，在hdfs上也是一个目录，在所在的库目录下创建了一个子目录！
			hive中的数据，是存在在表目录中的文件！
			

2. 在hive中，存储的数据必须是结构化的数据，而且这个数据的格式要和表的属性紧密相关！
   	表在创建时，有分隔符属性，这个分隔符属性，代表在执行MR程序时，使用哪个分隔符去分割每行中的字段！


   ```
   hive中默认字段的分隔符： ctrl+A, 进入编辑模式，ctrl+V 再ctrl+A 
   
   展示为： tom^A30  
   
   // ^A(ctrl+A)用于分隔字段（列），在创建表的时候可以通过八进制编码‘\001’表示
   // ^B用来分隔ARRAY或者STRUCT元素，或者Map中的键值对之间的分隔，在创建表的时候可以通过八进制编码‘\002’表示
   // ^C用于分隔Map键值对，在创建表的时候可以通过八进制编码‘\003’表示
   ```

3. hive中的元数据(schema)存储在关系型数据库

   默认存储在derby中！

   ```
   derby是使用Java语言编写的一个微型，常用于内嵌在Java中的数据库！
   derby同一个数据库的实例文件不支持多个客户端同时访问！
   ```

4. 将hive的元数据的存储设置存储在Mysql中！
   Mysql支持多用户同时访问一个库的信息！
   
```
   -1.在hive/conf中新建hive-site.xml配置文件
   -2.在hive/lib添加mysql-connector连接包
   -3.在自己的mysql数据库创建hive-metadata数据库，记得字符集必须有latin1
   -4.启动hive客户端执行命令再试试
   
   
   注意事项： ①metastore库的字符集必须是latin1
   		   ②5.5mysql，改 binlog_format=mixed | row
   						默认为statement
   	mysql的配置文件： /etc/my.cnf
   
   ①安装MySQL
   		卸载时： 使用rpm -e卸载后，需要删除 /var/lib/mysql目录！
   		检查：
  
  元数据的结构存储在Mysql中的位置:
       表的信息都存储在tbls表中，通过db_id和dbs表中的库进行外键约束！
	   库的信息都存储在dbs表中！
	   字段信息存在在column_v2表中，通过CD_ID和表的主键进行外键约束！
```

hive-site.xml的配置内容		

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
  <!-- WARNING!!! This file is auto generated for documentation purposes ONLY! -->
  <!-- WARNING!!! Any changes you make to this file will be ignored by Hive.   -->
  <!-- WARNING!!! You must make your changes in hive-site.xml instead.         -->
  <!-- Hive Execution Parameters -->
  
	<property>
		<name>javax.jdo.option.ConnectionURL</name>
		<value>jdbc:mysql://hive-mysql:3306/hivemetadata?createDatabaseIfNotExist=true</value>
	</property>
  
	<property>
		<name>javax.jdo.option.ConnectionDriverName</name>
		<value>com.mysql.jdbc.Driver</value>
		<description>Driver class name for a JDBC metastore</description>
	</property>
	
	<property>
		<name>javax.jdo.option.ConnectionUserName</name>
		<value>root</value>
	</property>
    <property>
		<name>javax.jdo.option.ConnectionPassword</name>
		<value>root</value>
	</property>
  </configuration>

```



### 2）使用服务端和beeline登录

​     上面我们通过的命令行的方式访问；如果我们要使用类似jdbc的登录连接，那么就需要服务端-客户端来访问

```shell
-服务端 bin目录下执行
~> hiveserver2

-客户端 bin目录下的beeline
~> beeline

- 连接hive服务端，上面默认使用的10000端口，hive2表示使用的是hive2而不是mysql，然后进行其他操作
beeline> !connect 'jdbc:hive2://192.168.0.131:10000'
```



### 3）Java连接使用

同理需要上面hiveserver2 服务端开启

i: 引入hive-jdbc包

```xml
<!-- hive-jdbc -->
<dependency>
    <groupId>org.apache.hive</groupId>
    <artifactId>hive-jdbc</artifactId>
    <version>1.2.1</version>
</dependency>
```

ii: 代码

```java
package com.vison.hive;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class HiveJdbc {

    static Connection connection;
    static{
        try {
             Class.forName("org.apache.hive.jdbc.HiveDriver");
             connection = DriverManager.getConnection("jdbc:hive2://192.168.0.131:10000", "root", "");

        }catch (Exception  e){
            e.printStackTrace();
        }
    }

    public static void select () throws SQLException {

        String sql = "select * from person";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        ResultSet resultSet = preparedStatement.executeQuery();
        while (resultSet.next()){
            System.out.println(resultSet.getString("name") + ":"+
                    resultSet.getInt("age"));
        }
    }

    public static void main(String[] args)throws Exception {
        select();
    }

}
```



# 3.Hive常见属性配置

- 数据库仓库位置

  - ```xml
    //默认是在 /user/hive/warehouse,通过hive-site.xml配置
    
      <property>
        <name>hive.metastore.warehouse.dir</name>
        <value>/user/hive/warehouse</value>
        <description>location of default database for the warehouse</description>
      </property>
    
    ```

- 日志存储位置修改hive-log4j.properties

  - ```properties
    # 默认在/tmp/${user.name} 下
    hive.log.dir = /user/local/hive-1.2.1/logs
    ```

- hive的查询输出显示列名，进入hive cli后：

  - `set hive.cli.print.header=true;`



# 4.hive常用的交互参数

```shell
hive --help //展示下面的使用
#定义一个变量，在hive启动后，可以使用${变量名}引用变量	
 -d,--define <key=value>          Variable subsitution to apply to hive 
                                  commands. e.g. -d A=B or --define A=B
    --database <databasename>     Specify the database to use   指定使用哪个库
    
  # 指定命令行获取的一条引号引起来的sql，执行完返回结果后退出cli!  
 -e <quoted-query-string>         SQL from command line
 # 执行一个文件中的sql语句！执行完返回结果后退出cli!
 -f <filename>                    SQL from files
 -H,--help                        Print help information
    --hiveconf <property=value>   Use value for given property 在cli运行之前，定义一对属性！
    --hivevar <key=value>         Variable subsitution to apply to hive 和-d作用类似
                                  commands. e.g. --hivevar A=B
 -i <filename>                    Initialization SQL file 先初始化一个sql文件，之后不退出cli
 -S,--silent                      Silent mode in interactive shell  不打印和结果无关的信息
 -v,--verbose                     Verbose mode (echo executed SQL to the  console)


# hive在运行时，先读取 hadoop的全部8个配置文件，读取之后，再读取hive-default.xml;;;再读取hive-site.xml， 如果使用--hiveconf，可以定义一组属性，这个属性会覆盖之前读到的参数的值！
```



在hive中操作hdfs和linux的命令

```shell
# hdfs;直接在前面加dfs
hive > dfs -ls /

#linux；直接前面加！
hive > !ls /
```





# 5.Hive的数据类型

hive的实现是java，所以基本上参考java就可以了

## 5.1 数据类型介绍

**1）基本类型**

| Hive数据类型 | Java数据类型 | 长度     | 例子       |
| ------------ | ------------ | -------- | ---------- |
| TINYINT      | byte         | 1        | 1          |
| SMARTINT     | short        | 2        | 20         |
| INT          | int          | 4        | 200        |
| BIGINT       | long         | 8        | 1000       |
| Boolean      | boolean      |          | True,false |
| FLOAT        | float        | 4        |            |
| Double       | double       | 8        |            |
| STRING       | string       | 字符     |            |
| TIMESTAMP    |              | 时间戳   |            |
| BINARY       |              | 字节数组 |            |

上面的STRING相当于数据库的varchar，只不过没有限制大小



**2）集合数据类型**

- STRUCT:类似c语言，通过.访问元素内容;类似java中的实体对象
- MAP:可以通过key访问数据
- ARRAY: 可以通过下标访问数据



MAP和STRUCT的区别；map的key是可以变的。而STRUCT的属性是固定的。

比如建表内容

```json
{
	"name":"zhangshan",
	"friends":["bingbing","lili"],		#array
	"children":{						#map
		"xiaohong":16,
		"xiaoming":22,
	}
	address:{
		"street":"beijing hutong",	  #struct
		"city":"beijing"
	}
}

# 创建sql
create table people (name string,friends array<string>,children map<string,int>,
adress struct<street:string,city:string>)
row format delimited fields terminated by ','   #设置字段的分隔符
collection items  terminated by '_'			# array元素之间的分隔符，map中entry的分隔符以及
											# struct属性之间的分隔符都是需要一致的
map keys terminated by ':'					# 设置map中key和value的分隔符
lines terminated by '\n'					# 行分隔符
```



```shell
# 可以通过dfs和hive的 load命令将数据上传到hdfs中

songsong,bingbing_lili,xiao song:18_xiaoxiao song:19,hui long guan_beijing
yangyang,caicai_susu,xiao yang:18_xiaoxiao yang:19,chao yang_beijing

# load 的使用，如下
hive > load  data local inpath 'hdfs-path' into table person(表名)

# 访问,map,array,struct的数据获取方式对应
hive> select friends[1],children[xiaohong],address.street from person where name ='zhangshan' 
```



## 5.2 类型转换

类型数据转换类似于Java中的数据转换，TINYINT会自动转换为INT，但是hive不会反向转换，除非使用CAST操作，否则会报错

- 任何整数类型都可以往更大的整性转--》bigint
- 所有整性，float，string都可以隐形转换为double 
- 整性也是可以转换为float的
- boolean不可以转换为其他类型



使用**CAST强制转**换

​	CAST("1" AS INT) 将字符串"1"转换为整性1，如果转换失败就是NULL





# 6. Hive的DDL操作

## 6.1 Hive的库操作

```shell
一、库的常见操作

1.增
  CREATE (DATABASE|SCHEMA) [IF NOT EXISTS] database_name
  [COMMENT database_comment]  // 库的注释说明
  [LOCATION hdfs_path]        // 库在hdfs上的路径
  [WITH DBPROPERTIES (property_name=property_value, ...)]; // 库的属性

  
  create database  if not exists mydb2 
  comment 'this is my db' 
  location 'hdfs://hadoop101:9000/mydb2' 
  with dbproperties('ownner'='jack','tel'='12345','department'='IT');
2.删
		drop database 库名： 只能删除空库
		drop database 库名 cascade： 删除非空库



3.改
		use 库名： 切换库
		
		dbproperties: alter database mydb2 set dbproperties('ownner'='tom','empid'='10001');
				同名的属性值会覆盖，之前没有的属性会新增

4.查
		show databases: 查看当前所有的库
		show tables in database: 查看库中所有的表
		desc database 库名： 查看库的描述信息
		desc database extended 库名： 查看库的详细描述信息
```



## 6.2 Hive的表操作

**1.增**

```shell
CREATE [EXTERNAL] TABLE [IF NOT EXISTS] table_name 
[(col_name data_type [COMMENT col_comment], ...)]   //表中的字段信息
[COMMENT table_comment] //表的注释

[PARTITIONED BY (col_name data_type [COMMENT col_comment], ...)]    // 创建分区表 
[CLUSTERED BY (col_name, col_name, ...) 							//分桶表
[SORTED BY (col_name [ASC|DESC], ...)] INTO num_buckets BUCKETS]   //分桶后排序

[ROW FORMAT row_format]  // 表中数据每行的格式，定义数据字段的分隔符，集合元素的分隔符等

[STORED AS file_format] //表中的数据要以哪种文件格式来存储，默认为TEXTFILE（文本文件）
					可以设置为SequnceFile或 Paquret,ORC等
[LOCATION hdfs_path]  //表在hdfs上的位置


其他建表：
			只复制表结构：  create table 表名 like  表名1   //包含分区表
			执行查询语句，将查询语句查询的结果(包含你返回的分区列)，按照顺序作为新表的普通列：create table 表名  as select 语句    //不能创建分区表！
			
```

	①建表时，不带EXTERNAL，创建的表是一个MANAGED_TABLE(管理表，内部表)
		建表时，带EXTERNAL，创建的表是一个外部表！
	
	外部表和内部表的区别是： 
			内部表(管理表)在执行删除操作时，会将表的元数据(schema)和表位置的数据一起删除！
			外部表在执行删除表操作时，只删除表的元数据(schema)
			
	在企业中，创建的都是外部表！在hive中表是廉价的，数据是珍贵的！
			
	建表语句执行时： 
			hive会在hdfs生成表的路径；
			hive还会向MySQl的metastore库中掺入两条表的信息(元数据)
			
	管理表和外部表之间的转换：
		将表改为外部表：	alter table p1 set tblproperties('EXTERNAL'='TRUE');
				
		将表改为管理表：	alter table p1 set tblproperties('EXTERNAL'='FALSE');
		
		注意：在hive中语句中不区分大小写，但是在参数中严格区分大小写！

**2.删**

```
	drop table 表名：删除表
	truncate table 表名：清空管理表，不删除表
```

**3.改**

```
改表的属性：  alter table 表名 set tblproperties(属性名=属性值)
		
对列进行调整：
	改列名或列类型： alter table 表名 change [column] 旧列名 新列名 新列类型 [comment 新列的注释]  					[FIRST|AFTER column_name] //调整列的顺序
								 
     添加列和重置列：ALTER TABLE table_name ADD|REPLACE COLUMNS (col_name data_type [COMMENT col_comment], ...) 

```

**4.查**

```
		desc  表名： 查看表的描述
		desc formatted 表名： 查看表的详细描述
```



## 6.3 Hive建表语句的其他属性

### 6.3.1 分区

`[PARTITIONED BY (col_name data_type [COMMENT col_comment], ...)] `



**1.分区表**

​	在建表时，指定了`PARTITIONED BY` ，这个表称为分区表

**2.分区概念**

MR：  在MapTask输出key-value时，为每个key-value计算一个区号，同一个分区的数据，会被同一个reduceTask处理
			这个分区的数据，最终生成一个结果文件！
			通过分区，将MapTask输出的key-value经过reduce后，分散到多个不同的结果文件中！

​	

Hive:  将表中的数据，分散到表目录下的多个子目录(分区目录)中



**3.分区意义**

​	分区的目的是为了就数据，分散到多个子目录中，在执行查询时，可以只选择查询某些子目录中的数据，加快查询效率！

	- 只有分区表才有子目录(分区目录)
	-  分区目录的名称由两部分确定：  分区列列名=分区列列值

- 将输入导入到指定的分区之后，数据会附加上分区列的信息！
- 分区的最终目的是在查询时，使用分区列进行过滤！	



**4.创建分区表**

```mysql
-- 创建分区area
create external table if not exists default.deptpart1(
deptno int,
dname string,
loc int
)
PARTITIONED BY(area string)
row format delimited fields terminated by '\t';



-- 多级分区表，有多个分区字段 area,province
create external table if not exists default.deptpart2(
deptno int,
dname string,
loc int)
PARTITIONED BY(area string,province string)
row format delimited fields terminated by '\t';


-- 指定localtion--文件在hdfs的存储位置
create external table if not exists default.deptpart3(
deptno int,
dname string,
loc int)
PARTITIONED BY(area string)
row format delimited fields terminated by '\t'
location 'hdfs://hadoop101:9000/deptpart3';



① alter table 表名 add partition(分区字段名=分区字段值) ;
			a)在hdfs上生成分区路径
			b)在mysql中metastore.partitions表中生成分区的元数据
② 直接使用load命令向分区加载数据，如果分区不存在，load时自动帮我们生成分区


-- 修复分区，元数据删除了，通过msck 可以修复分区
③ 如果数据已经按照规范的格式，上传到了HDFS，可以使用修复分区命令自动生成分区的元数据
		msck repair table 表名;	
        
        
注意事项：
	①如果表是个分区表，在导入数据时，必须指定向哪个分区目录导入数据
	②如果表是多级分区表，在导入数据时，数据必须位于最后一级分区的目录        
```



使用比如导入数据

```shell
# 将本地department1.txt 导入表deptpart1中
hive-1.2.2> hive -d t=/usr/local/hive-1.2.2/department1.txt
hive> load data local inpath '${t}' into table deptpart1 partition (area='sichuan');
```



**6.分区的查询**
		`show partitions 表名`
		

### 6.3.2 分桶

​	分桶是用表字段和分区不同。

```shell
[CLUSTERED BY (col_name, col_name, ...) 
		分桶的字段，是从表的普通字段中来取
[SORTED BY (col_name [ASC|DESC], ...)] INTO num_buckets BUCKETS] 
```



**1.分桶表**

​	建表时指定了CLUSTERED BY，这个表称为分桶表！

​	分桶：  和MR中分区是一个概念！ 把数据分散到多个文件中！

**2.分桶的意义**

​	分桶本质上也是为了分散数据！在分桶后，可以结合hive提供的抽样查询，只查询指定桶的数据

**3.分桶排序**

​		在分桶时，也可以指定将每个桶的数据根据一定的规则来排序；如果需要排序，那么可以在CLUSTERED BY后跟SORTED BY



**4.分桶表操作**

```mysql
-- 创建分桶表
create table stu_buck(id int, name string)
clustered by(id) 
SORTED BY (id desc)
into 4 buckets
row format delimited fields terminated by '\t';

-- 由于分桶表需要排序，所以需要通过map-reduce执行一次 所以创建一个 临时表
create table stu_buck_tmp(id int, name string)
row format delimited fields terminated by '\t';



-- 导入数据
	向分桶表导入数据时，必须运行MR程序，才能实现分桶操作！
	load的方式，只是执行put操作，无法满足分桶表导入数据！
	必须执行insert into 
		insert into 表名 values(),(),(),()
		insert into 表名 select 语句
		
	
	导入数据之前：
			需要打开强制分桶开关： set hive.enforce.bucketing=true;
			需要打开强制排序开关： set hive.enforce.sorting=true;
	
	
-- 先load 数据进入stu_buck_tmp 表中，然后通过insert .. select 操作完成   
load data local inpath '/usr/local/buck.txt' into table stu_back;
insert into table stu_buck select * from stu_buck_tmp
```



# 7.Hive的DML操作

## 7.1 DML导入

**1.load** : 作用将数据直接加载到表目录中

```mysql
语法：  load  data [LOCAL] inpath 'xx' [OVERWRITE] into table 表名 [PARTITION (partcol1=val1, partcol2=val2 ...)]
			LOCAL:  如果导入的文件在本地文件系统，需要加上LOCAL，使用put将本地上传到hdfs
					不加LOCAL默认导入的文件是在hdfs，使用mv将源文件移动到目标目录
			OVERWRITE：表示覆盖原来文件中的内容

例子： load data local inpath '/usr/local/hive-1.2.2/department1.txt' overwrite into table deptpart1 partition(area='beijing');
```



2. **insert：** insert方式运行MR程序，通过程序将数据输出到表目录！
		在某些场景，必须使用insert方式来导入数据：
				①向分桶表插入数据
				②如果指定表中的数据，不是以纯文本形式存储，需要使用insert方式导入
	
	```mysql
	语法： insert into|overwrite table 表名 select xxx | values(),(),() 
				insert into: 向表中追加新的数据
				insert overwrite： 先清空表中所有的数据，再向表中添加新的数据
	
	特殊情况： 多插入模式(从一张源表查询，向多个目标表插入)
				from 源表
				insert xxxx  目标表  select xxx
				insert xxxx  目标表  select xxx
				insert xxxx  目标表  select xxx
	
    举例： from deptpart2
           insert into table deptpart1 partition(area='huaxi') select deptno,dname,loc
	       insert into table deptpart1 partition(area='huaxinan') select deptno,dname,loc 
	```
	
	
	
	
	
3. **location:** 在建表时，指定表的location为数据存放的目录

4. **import :**  不仅可以导入数据还可以顺便导入元数据(表结构)。Import只能导入export输出的内容！
	
	```mysql
	  IMPORT [[EXTERNAL] TABLE 表名(新表或已经存在的表) [PARTITION (part_column="value"[, ...])]]
	  FROM 'source_path'
	  [LOCATION 'import_target_path']
					①如果向一个新表中导入数据，hive会根据要导入表的元数据自动创建表
					②如果向一个已经存在的表导入数据，在导入之前会先检查表的结构和属性是否一致
							只有在表的结构和属性一致时，才会执行导入
					③不管表是否为空，要导入的分区必须是不存在的
	
	例子：
	  import external table importtable1  from '/export1'
	```
	
	

## 7.2 DML之导出

1. **insert :**  将一条sql运算的结果，插入到指定的路径
		
	```mysql
    语法： insert overwrite [local] directory '/opt/module/datas/export/student'
			   row format xxxx
	           select * from student;
	```
	
	
	
2. **export ：**  既能导出数据，还可以导出元数据(表结构)！
   		  export会在hdfs的导出目录中，生成数据和元数据！
   		  导出的元数据是和RDMS无关！ 
   		  如果是分区表，可以选择将分区表的部分分区进行导出！
   		  

   	语法：  export table 表名 [partiton(分区信息) ] to 'hdfspath'



## 7.3 排序
		Hive的本质是MR，MR中如何排序的！
				全排序：  结果只有一个(只有一个分区)，所有的数据整体有序！
				部分排序：  结果有多个(有多个分区)，每个分区内部有序！
				二次排序：  在排序时，比较的条件有多个！
				
				排序： 在reduce之前就已经排好序了，排序是shuffle阶段的主要工作！
				
		排序？ 
		分区：使用Partitioner来进行分区！
					当reduceTaskNum>1，设置用户自己定义的分区器，如果没有使用HashParitioner!
					HashParitioner只根据key的hashcode来分区！

- **1）ORDER BY col_list** ：  全排序！  order by会对所给的全部数据进行全局排序，并且只会“叫醒”一个reducer干活
- **2）SORT BY col_list** ： 部分排序！ 设置reduceTaskNum>1。 只写sort by是随机分区！
  						如果希望自定定义使用哪个字段分区，需要使用DISTRIBUTE BY
- **3）DISTRIBUTE BY  col_list**：指定按照哪个字段分区！结合sort by 使用！
- **4）CLUSTER BY col_list**  ： 如果分区的字段和排序的字段一致，可以简写为CLUSTER BY 
  							DISTRIBUTE BY sal sort by sal asc  等价于  CLUSTER BY  sal

						要求： CLUSTER BY  后不能写排序方式，只能按照asc排序！

------------------------------------------------------------
例子：
```mysql
	insert overwrite local directory '/home/atguigu/sortby'
	ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t'
	select * from emp DISTRIBUTE BY deptno sort by sal desc ;

	insert overwrite local directory '/home/atguigu/sortby'
	ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t'
	select * from emp where mgr is not null CLUSTER BY  mgr ;
```



# 8.函数

## 8.1.查看函数
​		函数有库的概念，系统提供的除外，系统提供的函数可以在任意库使用！

- 查看当前库所有的函数：show functions;

- 查看函数的使用： desc function 函数名

- 查看函数的详细使用： desc function extended 函数名

  

## 8.2.函数的分类

**函数的来源：**  

- ①系统函数，自带的，直接使用即可
- ②用户自定义的函数：
  - a)遵守hive函数类的要求，自定义一个函数类
  - b)打包函数，放入到hive的lib目录下，或在HIVE_HOME/auxlib；auxlib用来存放hive可以加载的第三方jar包的目录
  - c)创建一个函数，让这个函数和之前编写的类关联函数有库的概念
  - d)使用函数

**函数按照特征分：**

-  ①UDF：  用户定义的函数。 一进一出。 输入单个参数，返回单个结果！cast('a' as int) 返回 null
-  ②UDTF:  用户定义的表生成函数。 一进多出。传入一个参数(集合类型)，返回一个结果集！
-  ③UDAF： 用户定义的聚集函数。 多进一出。 传入一列多行的数据，返回一个结果(一列一行) count,avg,sum





## 8.2 常用函数

```java
常用日期函数
		hive默认解析的日期必须是： 2019-11-24 08:09:10
            
unix_timestamp:返回当前或指定时间的时间戳	
from_unixtime：将时间戳转为日期格式
current_date：当前日期
current_timestamp：当前的日期加时间
* to_date：抽取日期部分
year：获取年
month：获取月
day：获取日
hour：获取时
minute：获取分
second：获取秒
weekofyear：当前时间是一年中的第几周
dayofmonth：当前时间是一个月中的第几天
* months_between： 两个日期间的月份，前-后
* add_months：日期加减月
* datediff：两个日期相差的天数，前-后
* date_add：日期加天数
* date_sub：日期减天数
* last_day：日期的当月的最后一天

date_format格式化日期   date_format( 2019-11-24 08:09:10,'yyyy-MM') mn

*常用取整函数
round： 四舍五入
ceil：  向上取整
floor： 向下取整

常用字符串操作函数
upper： 转大写
lower： 转小写
length： 长度
* trim：  前后去空格
lpad： 向左补齐，到指定长度
rpad：  向右补齐，到指定长度
* regexp_replace： SELECT regexp_replace('100-200', '(\d+)', 'num')='num-num
	使用正则表达式匹配目标字符串，匹配成功后替换！ 第一个表示要被替换的，第二个表示正则，第三个表示匹配后替换为这个数据

集合操作
size： 集合（map和list）中元素的个数
map_keys： 返回map中的key
map_values: 返回map中的value
* array_contains: 判断array中是否包含某个元素
sort_array： 将array中的元素排序
```







​	




​		
​		
​		
​		
​		
​		
​		
​		