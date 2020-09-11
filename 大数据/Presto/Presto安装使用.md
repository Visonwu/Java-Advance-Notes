# 1.Presto简介

## 1.1 .Presto介绍

Presto是一个开源的分布式SQL查询引擎，数据量支持GB到PB字节，主要是用来处理秒级查询场景

注意：虽然Presto可以解析SQL，但是它不是一个标准的数据库。不是MySQL、Oracle的替代品，不能用来处理在线事务OLTP



## 1.2.Presto架构

Presto是一个Coordinator和多个Worker组成

![wJKWYq.png](https://s1.ax1x.com/2020/09/10/wJKWYq.png)

## 1.3  Presto优缺点

![wJMV9P.png](https://s1.ax1x.com/2020/09/10/wJMV9P.png)

## 1.4  **Presto、Impala性能比较**



测试结论：Impala性能稍领先于Presto，但是Presto在数据源支持上非常丰富，包括Hive、图数据库、传统关系型数据库、Redis等。





# 2. Presto安装启动

参考：<https://prestodb.io/docs/current/installation/deployment.html>

在三台机器上安装ebusiness1,ebusiness2,ebusiness3

## 2.1 准备

服务端：presto-server-0.196.tar.gz

客户端：presto-cli-0.196-executable.jar

可视化界面：yanagishima-18.0.zip



## 2.2 服务端安装

1)将presto-server-0.196.tar.gz导入/opt/software目录下，并解压到/opt/module目录

```bash
[software]$ tar -zxvf presto-server-0.196.tar.gz -C /opt/module/
```

3）修改名称为presto

```shell
[module]$ mv presto-server-0.196/ presto
```

4）进入到/opt/module/presto目录，并创建存储数据文件夹

```shell
[ presto]$ mkdir data
```

5）进入到/opt/module/presto目录，并创建存储配置文件文件夹

```shell
[ presto]$ mkdir etc
```

6）配置在/opt/module/presto/etc目录下添加jvm.config配置文件

```shell
[ etc]$ vim jvm.config
```

添加如下内容:

```java
-server
-Xmx16G
-XX:+UseG1GC
-XX:G1HeapRegionSize=32M
-XX:+UseGCOverheadLimit
-XX:+ExplicitGCInvokesConcurrent
-XX:+HeapDumpOnOutOfMemoryError
-XX:+ExitOnOutOfMemoryError
```

7）Presto可以支持多个数据源，在Presto里面叫catalog，这里我们配置支持Hive的数据源，配置一个Hive的catalog

```
[ etc]$ mkdir catalog

[ catalog]$ vim hive.properties 
```

添加如下内容

```properties
# hive的连接器是hive-hadoop2
connector.name=hive-hadoop2
#ebusiness1是hive的metastore的地址
hive.metastore.uri=thrift://ebusiness1:9083
```



8）将第一台上的是上的presto分发到其他两台机器

```shell
[ module]$ xsync presto
```

9）分发之后，分别进入三台主机的/opt/module/presto/etc的路径。配置node属性，node id每个节点都不一样。

```shell
[ebusiness1 etc]$vim node.properties
node.environment=production
node.id=ffffffff-ffff-ffff-ffff-ffffffffffff
node.data-dir=/opt/module/presto/data

[ebusiness2 etc]$vim node.properties
node.environment=production
node.id=ffffffff-ffff-ffff-ffff-fffffffffffe
node.data-dir=/opt/module/presto/data

[ebusiness3 etc]$vim node.properties
node.environment=production
node.id=ffffffff-ffff-ffff-ffff-fffffffffffd
node.data-dir=/opt/module/presto/data
```

10）Presto是由一个coordinator节点和多个worker节点组成。在第一台机器ebusiness1上配置成coordinator，在ebusiness2,ebusiness3上配置为worker。

（1）ebusiness1上配置coordinator节点

```shell
[ebusiness1 etc]$ vim config.properties
```

添加内容如下

```properties
coordinator=true
node-scheduler.include-coordinator=false
http-server.http.port=8881
query.max-memory=50GB
discovery-server.enabled=true
discovery.uri=http://ebusiness1:8881
```

（2）ebusiness2、ebusiness3上配置worker节点

```shell
[ebusiness2 etc]$ vim config.properties
```

添加内容如下

```properties
coordinator=false
http-server.http.port=8881
query.max-memory=50GB
discovery.uri=http://ebusiness1:8881
```

```shell
[ebusiness3 etc]$ vim config.properties
```

添加内容如下:

```properties
coordinator=false
http-server.http.port=8881
query.max-memory=50GB
discovery.uri=http://ebusiness1:8881
```

11）在ebusiness1的/opt/module/hive目录下，启动Hive Metastore，用当前用户角色

```shell
[ebusiness1 hive]$
nohup bin/hive --service metastore >/dev/null 2>&1 &
```



12）分别在ebusiness1、ebusiness2、ebusiness3上启动Presto Server

（1）前台启动Presto，控制台显示日志

```shell
[ebusiness1 presto]$ bin/launcher run
[ebusiness2 presto]$ bin/launcher run
[ebusiness3 presto]$ bin/launcher run
```

（2）后台启动Presto

```shell
[ebusiness1 presto]$ bin/launcher start
[ebusiness2 presto]$ bin/launcher start
[ebusiness3 presto]$ bin/launcher start
```

13）日志查看路径/opt/module/presto/data/var/log



## 2.3 Presto命令行Client安装

2）将presto-cli-0.196-executable.jar上传到ebusiness的/opt/module/presto文件夹下

3）修改文件名称

```shell
[ebusiness1 presto]$ mv presto-cli-0.196-executable.jar  prestocli
```

4）增加执行权限

```shell
[ebusiness1 presto]$ chmod +x prestocli
```

5）启动prestocli

```shell
[ebusiness1 presto]$ ./prestocli --server ebusiness1:8881 --catalog hive --schema default
```

6）Presto命令行操作

Presto的命令行操作，相当于Hive命令行操作。每个表必须要加上schema。

例如：

```sql
select * from schema.table limit 100
# hive.库名.表名
select * from hive.gmall.ads_gmv_sum_day limit 1;
```



## 2.4 Presto可视化Client安装

1）将yanagishima-18.0.zip上传到ebusiness1的/opt/module目录

2）解压缩yanagishima

```shell
[ebusiness1 module]$ unzip yanagishima-18.0.zip
cd yanagishima-18.0
```



3）进入到/opt/module/yanagishima-18.0/conf文件夹，编写yanagishima.properties配置

```shell
[ebusiness1 conf]$ vim yanagishima.properties
```

​	添加如下内容

```properties
jetty.port=7080
presto.datasources=my-presto
presto.coordinator.server.my-presto=http://ebusiness1:8881
catalog.my-presto=hive
schema.my-presto=default
sql.query.engines=presto
```

4）在/opt/module/yanagishima-18.0路径下启动yanagishima

```shell
[ebusiness1 yanagishima-18.0]$
nohup bin/yanagishima-start.sh >y.log 2>&1 &
```

5）启动web页面

http://ebusiness1:7080 

看到界面，进行查询了。

6）查看表结构,treeview.



# 3. Presto优化之数据存储

## 3.1 **合理设置分区**

与Hive类似，Presto会根据元数据信息读取分区数据，合理的分区能减少Presto数据读取量，提升查询性能。

## 3.2 **使用列式存储**

Presto对ORC文件读取做了特定优化，因此在Hive中创建Presto使用的表时，建议采用ORC格式存储。相对于Parquet，Presto对ORC支持更好。

## 3.3 **使用压缩**

数据压缩可以减少节点间数据传输对IO带宽压力，对于即席查询需要快速解压，建议采用Snappy压缩。



# 4 Presto优化之查询SQL

## 4.1 **只选择使用的字段**

由于采用列式存储，选择需要的字段可加快字段的读取、减少数据量。避免采用*读取所有字段。

```sql
[GOOD]: SELECT time, user, host FROM tbl
[BAD]:  SELECT * FROM tbl
```

## 4.2 **过滤条件必须加上分区字段** 

对于有分区的表，where语句中优先使用分区字段进行过滤。acct_day是分区字段，visit_time是具体访问时间。

```sql
[GOOD]: SELECT time, user, host FROM tbl where acct_day=20171101
[BAD]:  SELECT * FROM tbl where visit_time=20171101
```

## 4.3 **Group By语句优化**

合理安排Group by语句中字段顺序对性能有一定提升。将Group By语句中字段按照每个字段distinct数据多少进行降序排列。distinct大的在前，小的在后

```sql
[GOOD]: SELECT GROUP BY uid, gender
[BAD]:  SELECT GROUP BY gender, uid
```

## 4.4 **Order By时使用Limit**

Order by需要扫描数据到单个worker节点进行排序，导致单个worker需要大量内存。如果是查询Top N或者Bottom N，使用limit可减少排序计算和内存压力。

```sql
[GOOD]: SELECT * FROM tbl ORDER BY time LIMIT 100
[BAD]:  SELECT * FROM tbl ORDER BY time
```

## 4.5 使用Join语句时将大表放在左边

Presto中join的默认算法是broadcast join，即将join左边的表分割到多个worker，然后将join右边的表数据整个复制一份发送到每个worker进行计算。如果右边的表数据量太大，则可能会报内存溢出错误。

```sql
[GOOD] SELECT ... FROM large_table l join small_table s on l.id = s.id

[BAD] SELECT ... FROM small_table s join large_table l on l.id = s.id
```

# 5.注意事项

## 5.1 **字段名反引用**

避免和关键字冲突：MySQL对字段加反引号**`、**Presto对字段加双引号分割

当然，如果字段名称不是关键字，可以不加这个双引号。

## 5.2 **时间函数**

对于Timestamp，需要进行比较的时候，需要添加Timestamp关键字，而MySQL中对Timestamp可以直接进行比较。

```sql
/*MySQL的写法*/
SELECT t FROM a WHERE t > '2017-01-01 00:00:00'; 

/*Presto中的写法*/
SELECT t FROM a WHERE t > timestamp '2017-01-01 00:00:00';
```

## 5.3  **不支持INSERT OVERWRITE语法**

Presto中不支持insert overwrite语法，只能先delete，然后insert into。

## 5.4 **PARQUET格式**

Presto目前支持Parquet格式，支持查询，但不支持insert。