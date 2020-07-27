# 一、Hbase存储在HDFS中的结构

1.默认有两张系统表
		hbase:meta:  保存的是用户的表和region的对应信息
		hbase:namespace:  保存的是用户自己创建的namespace的信息
		
2.hbase中的对象在HDFS的表现形式

下面首个/hbase是我们配置文件中rootdir中配置的。

​		库以目录的形式存放在 /hbase/data中
​		表是以子目录的形式存在在 /hbase/data/库名 中
​		region也是以子目录的形式存在  /hbase/data/库名/表名 中
​		列族也是以子目录的形式存在   /hbase/data/库名/表名/region 中
​		数据以文件的形式存放在   /hbase/data/库名/表名/region/列族 目录中





# 二、Hbase shell的使用



```
> hbase
# 可以查看hbase的使用

# 我们主要查看hbase shell的使用,通过这个进入hbase客户端的命令操作

> hbase shell
```

## 1 注意事项

```shell
1.在hbase shell中不要敲 ;，如果敲了;，需要敲两个 单引号结束！

2. 查看帮助
		help: 查看所有命令的帮助
		help '命令'： 查看某个命令的帮助
		help '组名' ： 查看某组命令的帮助
		
		hbase shell使用ruby编写，不支持中文！
		
		
#比如,shell的命令可以通过分组来查看，比如general的命令如下：
#  Group name: general
#	  Commands: processlist, status, table_help, version, whoami
		
hbase(main):011:0> help 'general'
```



## 2. 相关命令crud

```shell
# help命令可以查看很多支持的命令
COMMAND GROUPS:
  Group name: general
  Commands: processlist, status, table_help, version, whoami

  Group name: ddl
  Commands: alter, alter_async, alter_status, clone_table_schema, create, describe, disable, disable_all, drop, drop_all, enable, enable_all, exists, get_table, is_disabled, is_enabled, list, list_regions, locate_region, show_filters

  Group name: namespace
  Commands: alter_namespace, create_namespace, describe_namespace, drop_namespace, list_namespace, list_namespace_tables

  Group name: dml
  Commands: append, count, delete, deleteall, get, get_counter, get_splits, incr, put, scan, truncate, truncate_preserve


  Group name: configuration
  Commands: update_all_config, update_config

。。。。。

```

**1）namespace和ddl：**

```shell
# 创建vison命名空间
>create_namespace 'vison'

# 从vison命名空间下创建表t1，有f1，f2,f3列簇
>create 'vison:t1', {NAME => 'f1'}, {NAME => 'f2'}, {NAME => 'f3'}
>create 'vison:t1','cf1','cf2'


# 修改表，删除f1列簇
hbase> alter 'vison:t1', NAME => 'f1', METHOD => 'delete'
hbase> alter 'vison:t1', 'delete' => 'f1'

# 修改f2列簇的配置
>alter 'vison:t1', {NAME => 'f2', CONFIGURATION => {'hbase.hstore.blockingStoreFiles' => '10'}}

# 删除表
> disable 'vison:t1'
> drop 'vison:t1'

# 删除命名空间
> drop_namespace 'vison'
```



**2）dml表操作**

```shell
#  Group name: dml
#  Commands: append, count, delete, deleteall, get, get_counter, get_splits, incr, put, scan, truncate, truncate_preserve
  
# 先创建一个表
> create 'nst:t1','cf1','cf2'
#put添加数据，官方案例如下，（'命名空间:表名'，'row-key','列簇:列名','值'）
  hbase> put 'ns1:t1', 'r1', 'c1', 'value'
  hbase> put 'ns1:t1', 'r1', 'c1', 'value'
  hbase> put 'ns1:t1', 'r1', 'c1', 'value', ts1
  hbase> put 'ns1:t1', 'r1', 'c1', 'value', {ATTRIBUTES=>{'mykey'=>'myvalue'}}
  hbase> put 'ns1:t1', 'r1', 'c1', 'value', ts1, {ATTRIBUTES=>{'mykey'=>'myvalue'}}
  hbase> put 'ns1:t1', 'r1', 'c1', 'value', ts1, {VISIBILITY=>'PRIVATE|SECRET'}

# 比如向t1表的插 cell，rowkey为r1，插入列簇cf1,name=jack,age=18,gender=male
> put 'ns1:t1', 'r1','cf1:name', 'jack'
> put 'ns1:t1', 'r1','cf1:age', '18'
> put 'ns1:t1', 'r1','cf1:gender', 'male'

# 查看r1行的数据，返回的时间戳作为版本存入
> get 'ns1:t1','r1'
COLUMN       CELL                                                   
cf1:age    timestamp=1595659303050, value=18                            
cf1:gender timestamp=1595659313031, value=male                                           
cf1:name   timestamp=1595659030766, value=jack 

# scan,查看表的信息，下面那个可以把所有版本的信息查询出来，包括已经删除了的，VERSIONS表示最新的多少条记录
> scan 'ns1:t1'
> scan 'ns1:t1',{RAW => true,VERSIONS => 10}

# 更新数据其实就是相同的列名直接插入数据就是更新了
> put 'ns1:t1', 'r1','cf1:age', '20'

#删除gender cell，本地也是追加操作，
> delete 'ns1:t1','r1','cf1:gender'

```



**3）scan的高级用法**

​	get是查询一条记录，scan是扫描多条记录

```shell
# 来自官方的例子，支持limit，某一列查询，相关限制查询

hbase> scan 'hbase:meta'
hbase> scan 'hbase:meta', {COLUMNS => 'info:regioninfo'}
hbase> scan 'ns1:t1', {COLUMNS => ['c1', 'c2'], LIMIT => 10, STARTROW => 'xyz'}
hbase> scan 't1', {COLUMNS => ['c1', 'c2'], LIMIT => 10, STARTROW => 'xyz'}
hbase> scan 't1', {COLUMNS => 'c1', TIMERANGE => [1303668804000, 1303668904000]}
hbase> scan 't1', {REVERSED => true}
hbase> scan 't1', {ALL_METRICS => true}
hbase> scan 't1', {METRICS => ['RPC_RETRIES', 'ROWS_FILTERED']}
hbase> scan 't1', {ROWPREFIXFILTER => 'row2', FILTER => "
  (QualifierFilter (>=, 'binary:xyz')) AND (TimestampsFilter ( 123, 456))"}
hbase> scan 't1', {FILTER =>
  org.apache.hadoop.hbase.filter.ColumnPaginationFilter.new(1, 0)}
hbase> scan 't1', {CONSISTENCY => 'TIMELINE'}
hbase> scan 't1', {ISOLATION_LEVEL => 'READ_UNCOMMITTED'}
hbase> scan 't1', {MAX_RESULT_SIZE => 123456}
For setting the Operation Attributes
hbase> scan 't1', { COLUMNS => ['c1', 'c2'], ATTRIBUTES => {'mykey' => 'myvalue'}}
hbase> scan 't1', { COLUMNS => ['c1', 'c2'], AUTHORIZATIONS => ['PRIVATE','SECRET']}

```



## 3. hbase 查看HFile

hbase可以通过如下方式查看hdfs的数据存储内容

```shell
hbase org.apache.hadoop.hbase.io.hfile.HFile -e -p -f hdfs://xxx/hbase/xxx
```



# 三、Hbase Java API使用

添加依赖

```xml
  <dependency>
      <groupId>org.apache.hbase</groupId>
      <artifactId>hbase-server</artifactId>
      <version>2.1.9</version>
  </dependency>

  <dependency>
      <groupId>org.apache.hbase</groupId>
      <artifactId>hbase-client</artifactId>
      <version>2.1.9</version>
  </dependency>
```

参考目录下项目





# 四、Hbase-MapReduce

​		**统计的需要：**我们知道HBase的数据都是分布式存储在RegionServer上的，所以对于类似传统关系型数据库的group by操作，扫描器是无能为力的，只有当所有结果都返回到客户端的时候，才能进行统计。这样做一是慢，二是会产生很大的网络开销，所以使用MapReduce在服务器端就进行统计是比较好的方案。

​		**性能的需要：**说白了就是“快”！如果遇到较复杂的场景，在扫描器上添加多个过滤器后，扫描的性能很低；或者当数据量很大的时候扫描器也会执行得很慢，原因是扫描器和过滤器内部实现的机制很复杂，虽然使用者调用简单，但是服务器端的性能就不敢保证了

​	通过HBase的相关JavaAPI，我们可以实现伴随HBase操作的MapReduce过程，比如使用MapReduce将数据从本地文件系统导入到HBase的表中，比如我们从HBase中读取一些原始数据后使用MapReduce做数据分析。





## 1.和MR的配置

- 1.1 Hbase可以做简单的查询，但是无法对查询的结果进行深加工！
  		可以使用MR来进行hbase中数据的深加工！


- 1.2 MR必须持有可以读取HBase中数据的api才可以！
  在MR启动时，在MR程序的类路径下，把读取hbase的jar包加入进去！	

```shell
①使用MR读取hbase，需要哪些jar包？
	通过执行hbase mapredcp查看
②如何让MR在运行时，提前讲这些jar包加入到MR的环境中？
		hadoop jar MRjar包 主类名  参数
		
		hadoop命令一执行，先去读取hadoop-config.sh(hadoop环境的配置脚本，用来配置环境变量)
		，hadoop-config.sh读取hadoop-env.sh(建议将hadoop运行的变量配置在此脚本中)
		
		在hadoop/etc/hadoop/hadoop-env.sh （注意：在for循环之后配），添加hbase的目录
		export HADOOP_CLASSPATH=$HADOOP_CLASSPATH:/opt/module/hbase/lib/*
```

### 1）测试官方案例

```shell
#（我在hbase-server-2.1.9没有找到这个自带案例）

①hadoop jar hbase-server-1.3.1.jar CellCounter t1 /hbasemr/cellcount   #统计t1表中 cell数据

②hadoop jar hbase-server-1.3.1.jar rowcounter t1   #统计t1的row数据

③向hbase中导入数据，需要手动建表，需要把数据上传到HDFS，注意数据中字段的顺序要和-Dimporttsv.columns的顺序一致；(create 't2','info');
   hadoop jar hbase-server-1.3.1.jar importtsv
		-Dimporttsv.columns=HBASE_ROW_KEY,info:name,info:age,info:gender t2 /hbaseimport

HBASE_ROW_KEY： 代表rowkey列	
/hbaseimport : 需要上传的文件数据，通过tab做分隔
在示例程序中添加参数，可以通过在core-site.xml中添加相关的参数！比如zk的配置地址
```



### 2） 手工mapreduce案例1

   MR读取hbase中t1表的部分数据，写入到t2表中

### 3） 手工mapreduce案例2

​	MR读取HDFS中的数据，写入到hbase表中



## 2.Hbase和Hive集成

参考官网：<https://cwiki.apache.org/confluence/display/Hive/HBaseIntegration>

尖叫提示：HBase与Hive的集成在最新的两个版本中无法兼容。所以，只能含着泪勇敢的重新编译：hive-HBase-handler-1.2.2.jar！！

HBase负责存储，Hive负责分析！
hive的本质是使用HQL语句，翻译为MR程序，进行计算和分析！



**环境配置**

- 让hive持有可以读写hbase的jar包
  			在HIVE_HOME/lib/下，讲操作hbase的jar包以软连接的形式，持有！

```shell
# /etc/profile 中配置hbase和hive的home
export HBASE_HOME=/opt/module/HBase
export HIVE_HOME=/opt/module/hive

# 将hbase的jar以软连接到HIVE_HOME的jar包中
ln -s $HBASE_HOME/lib/HBase-common-2.1.9.jar  $HIVE_HOME/lib/HBase-common-2.1.9.jar
ln -s $HBASE_HOME/lib/HBase-server-2.1.9.jar $HIVE_HOME/lib/HBase-server-2.1.9.jar
ln -s $HBASE_HOME/lib/HBase-client-2.1.9.jar $HIVE_HOME/lib/HBase-client-2.1.9.jar
ln -s $HBASE_HOME/lib/HBase-protocol-2.1.9.jar $HIVE_HOME/lib/HBase-protocol-2.1.9.jar
ln -s $HBASE_HOME/lib/HBase-it-2.1.9.jar $HIVE_HOME/lib/HBase-it-2.1.9.jar
ln -s $HBASE_HOME/lib/htrace-core-3.1.0-incubating.jar $HIVE_HOME/lib/htrace-core-3.1.0-incubating.jar
ln -s $HBASE_HOME/lib/HBase-hadoop2-compat-2.1.9.jar $HIVE_HOME/lib/HBase-hadoop2-compat-2.1.9.jar
ln -s $HBASE_HOME/lib/HBase-hadoop-compat-2.1.9.jar $HIVE_HOME/lib/HBase-hadoop-compat-2.1.9.jar
```

---

- hive的hive-site.xml配置zk

  ```xml
  <property>
    <name>hive.zookeeper.quorum</name>
    <value>hadoop-senior01-test.com</value>
    <description>The list of ZooKeeper servers to talk to. This is only needed for read/write locks.</description>
  </property>
  <property>
    <name>hive.zookeeper.client.port</name>
    <value>2181</value>
    <description>The port of ZooKeeper servers to talk to. This is only needed for read/write locks.</description>
  </property>
  ```

  



注意：在hive中建表，这个表需要和hbase中的数据进行映射; 只能创建 non-native table

### 1）数据已经在hbase中

数据已经在hbase中，只需要在hive建表，查询即可,hbase和hive列名匹配

```mysql
create external table hbase_t2(id int,age int,gender string,name string) 
STORED BY 'org.apache.hadoop.hive.hbase.HBaseStorageHandler' 
WITH SERDEPROPERTIES ("hbase.columns.mapping" = ":key,cf1:age,cf1:gender,cf1:name") 
TBLPROPERTIES ("hbase.table.name" = "ns1:t2");

# 不在使用row format 做格式转换；使用store by
# SERDEPROPERTIES 按照 serde 序列化以及相关列映射
# TBLPROPERTIES 表的对应关系
```



### 2） 数据还尚未插入到hbase

 数据还尚未插入到hbase，可以在hive中建表，建表后，在hive中执行数据的导入，将数据导入到hbase，再分析。 表必须是	managed	 non-native table！

```mysql
①建表
	CREATE  TABLE `hbase_emp`(
	  `empno` int, 
	  `ename` string, 
	  `job` string, 
	  `mgr` int, 
	  `hiredate` string, 
	  `sal` double, 
	  `comm` double, 
	  `deptno` int)
	STORED BY 'org.apache.hadoop.hive.hbase.HBaseStorageHandler'
	WITH SERDEPROPERTIES ("hbase.columns.mapping" = ":key,info:ename,info:job,info:mgr,info:hiredate,info:sal,info:comm,info:deptno")
	TBLPROPERTIES ("hbase.table.name" = "emp");
			
②替换hive-hbase-handler.jar

③使用insert向表中导入数据,不能使用load
	insert into table hbase_emp select * from emp
```



### 3）注意事项

```
a) 在建表时，hive中的表字段的类型要和hbase中表列的类型一致,以避免类型转换失败造成数据丢失
b) row format 的作用是指定表在读取数据时，使用什么分隔符来切割数据，只有正确的分隔符，才能正确切分字段
c) 管理表：managed_table
		由hive的表管理数据的生命周期！在hive中，执行droptable时，
		不禁将hive中表的元数据删除，还把表目录中的数据删除
   外部表： external_table
		hive表不负责数据的生命周期！在hive中，执行droptable时，
		只会将hive中表的元数据删除，不把表目录中的数据删除
```



### 4）Hive集成Hbase理论

- **1.Storage Handlers**
  		Storage Handlers是一个扩展模块，帮助hive分析不在hdfs存储的数据！
  		例如数据存储在hbase上，可以使用hive提供的对hbase的Storage Handlers，来读写hbase中的数据！	

	native table: 本地表！ hive无需通过Storage Handlers就能访问的表。
				例如之前创建的表，都是native table！
	non-native table : hive必须通过Storage Handlers才能访问的表！
				例如和hbase继承的表！

- **2.在建表时**
  **创建native表：**
  			[ROW FORMAT row_format] [STORED AS file_format]
  					file_format: ORC|TEXTFILE|SEQUNCEFILE|PARQUET
  					都是hive中支持的文件格式，由hive负责数据的读写！
  	或
  	**创建non-native表:**
  			STORED BY 'storage.handler.class.name' [WITH SERDEPROPERTIES (...)]
  					数据在外部存储，hive通过Storage Handlers来读写数据！

- **3.SERDE:**
  序列化器和反序列化器
  	

```
表中的数据是什么样的格式，就必须使用什么样的SerDe!
	纯文本：  row format delimited ，默认使用LazySimpleSerDe
	JSON格式：  使用JsonSerde
	ORC：    使用读取ORC的SerDe
	Paquet:  使用读取PaquetSerDe

普通的文件数据，以及在建表时，如果不指定serde，默认使用LazySimpleSerDe！


例如： 数据中全部是JSON格式
	{"name":"songsong","friends":["bingbing","lili"]}
	{"name":"songsong1","friends": ["bingbing1" , "lili1"]}

错误写法：
	create table testSerde(
	name string,
	friends array<string>
	)
	row format delimited fields terminated by ','
	collection items terminated by ','
	lines terminated by '\n';

如果指定了row format delimited ,此时默认使用LazySimpleSerDe！
LazySimpleSerDe只能处理有分隔符的普通文本！


创建serde前需要在hive客户端命令下执行,hive-hcatalog-core-1.2.2.jar加到环境变量下
> add jar /usr/local/hive-1.2.2/hcatalog/share/hcatalog/hive-hcatalog-core-1.2.2.jar
现在数据是JSON，格式{},只能用JSONSerDE
	create table testSerde2(
		name string,
		friends array<string>
	)
	ROW FORMAT SERDE 
	'org.apache.hive.hcatalog.data.JsonSerDe' 
	STORED AS TEXTFILE
```
