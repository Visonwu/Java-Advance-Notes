1）hadoop本身并不支持lzo压缩，故需要使用twitter提供的hadoop-lzo开源组件。hadoop-lzo需依赖hadoop和lzo进行编译，编译步骤如下。

```

0. 环境准备
maven（下载安装，配置环境变量，修改sitting.xml加阿里云镜像）
gcc-c++
zlib-devel
autoconf
automake
libtool
通过yum安装即可，yum -y install gcc-c++ lzo-devel zlib-devel autoconf automake libtool

1. 下载、安装并编译LZO

wget http://www.oberhumer.com/opensource/lzo/download/lzo-2.10.tar.gz

tar -zxvf lzo-2.10.tar.gz

cd lzo-2.10

./configure -prefix=/usr/local/hadoop/lzo/

make

make install

2. 编译hadoop-lzo源码

2.1 下载hadoop-lzo的源码，下载地址：https://github.com/twitter/hadoop-lzo/archive/master.zip
2.2 解压之后，修改pom.xml
    <hadoop.current.version>2.7.2</hadoop.current.version>
2.3 声明两个临时环境变量
     export C_INCLUDE_PATH=/usr/local/hadoop/lzo/include
     export LIBRARY_PATH=/usr/local/hadoop/lzo/lib 
2.4 编译
    进入hadoop-lzo-master，执行maven编译命令
    mvn package -Dmaven.test.skip=true
2.5 进入target，hadoop-lzo-0.4.21-SNAPSHOT.jar 即编译成功的hadoop-lzo组件
```

2）将编译好后的hadoop-lzo-0.4.20.jar 放入hadoop-2.7.2/share/hadoop/common/

```
[root common]$ pwd
/opt/module/hadoop-2.7.2/share/hadoop/common

[root common]$ ls
hadoop-lzo-0.4.20.jar
```

3）同步hadoop-lzo-0.4.20.jar到集群中其他机器

```
[root common]$ xsync hadoop-lzo-0.4.20.jar
```

4）hadoop中core-site.xml增加配置支持LZO压缩

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
<property>
    <name>io.compression.codecs</name>
    <value>
    org.apache.hadoop.io.compress.GzipCodec,
    org.apache.hadoop.io.compress.DefaultCodec,
    org.apache.hadoop.io.compress.BZip2Codec,
    org.apache.hadoop.io.compress.SnappyCodec,
    com.hadoop.compression.lzo.LzoCodec,
    com.hadoop.compression.lzo.LzopCodec
    </value>
</property>

<property>
    <name>io.compression.codec.lzo.class</name>
    <value>com.hadoop.compression.lzo.LzoCodec</value>
</property>
</configuration>
```

5）同步core-site.xml到其他集群机器

```
[root hadoop]$ xsync core-site.xml
```

6）启动及查看集群

```
[root hadoop-2.7.2]$ sbin/start-dfs.sh

[root hadoop-2.7.2]$ sbin/start-yarn.sh
```

7）创建lzo文件的索引，lzo压缩文件的可切片特性依赖于其索引，故我们需要手动为lzo压缩文件创建索引。若无索引，则lzo文件的切片只有一个。

```
hadoop jar /path/to/your/hadoop-lzo.jar com.hadoop.compression.lzo.DistributedLzoIndexer  big_file.lzo
```



8)测试

（1）hive建表语句

```sql
create table bigtable(id bigint, time bigint, uid string, keyword string, url_rank int, click_num int, click_url string)
row format delimited fields terminated by '\t'
STORED AS
  INPUTFORMAT 'com.hadoop.mapred.DeprecatedLzoTextInputFormat'
  OUTPUTFORMAT 'org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat';
```

（2）向表中导入数据，bigtable.lzo大小为140M

```
load data local inpath '/opt/module/data/bigtable.lzo' into table bigtable;
```

（3）测试（建索引之前），观察map个数（1个）

```sql
select id,count(*) from bigtable group by id limit 10;
```

（4）建索引

```hadoop
hadoop jar /opt/module/hadoop-2.7.2/share/hadoop/common/hadoop-lzo-0.4.20.jar com.hadoop.compression.lzo.DistributedLzoIndexer /user/hive/warehouse/bigtable

# /user/hive/warehouse/bigtable 表示需要建立索引的hdfs索引目录，执行完了每一个文件会多一个.index文件
```

（5）测试（建索引之后），观察map个数（2个）

```sql
select id,count(*) from bigtable group by id limit 10;
```

