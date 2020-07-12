

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
hive > load  data local 'inputpath' 'hdfs-path' table person

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



## 6.2 Hive的管理表和外部表



## 6.3 Hive建表语句的其他属性









# 3. WordCount

如何用hive实现wordcount?

```
源文件：
hadoop	hive	hadoop
hadoop	hive
...

将源文件转为结构化的数据
hadoop	1
hive	1
hadoop	1
...
```



①建表
		表需要根据存储的数据的结构创建！
		表要映射到数据的结构上
		create table a(word string,totalCount int) 
	
②写HQL： 
		select  word,sum(totalCount)
		from a 
		group by word
		



​	




​		
​		
​		
​		
​		
​		
​		
​		