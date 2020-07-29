# 1.概念

​		Phoenix 构建在 HBase 之上的开源 SQL 层. 能够让我们使用标准的 JDBC API 去建表, 插入数据和查询 HBase 中的数据, 从而可以避免使用 HBase 的客户端 API.

在我们的应用和 HBase 之间添加了 Phoenix, 并不会降低性能, 而且我们也少写了很多代码.





# 2.特点

- 将 SQl 查询编译为 HBase 扫描
- 确定扫描 Rowkey 的最佳开始和结束位置
- 扫描并行执行
- 将 where 子句推送到服务器端的过滤器
- 通过**协处理器**进行聚合操作
- 完美支持 HBase 二级索引创建
- DML命令以及通过DDL命令创建和操作表和版本化增量更改。
- 容易集成：如Spark，Hive，Pig，Flume和Map Reduce。





# 3.架构

![aed49H.png](https://s1.ax1x.com/2020/07/29/aed49H.png)



# 4.数据模型

hbase和phoenix的对应关系

		hbase				phoenix
		-----				-----
		namespace			database(库)
		table				table（表）
		column family		cf:cq
		column quliafier
		rowkey				主键
		
		在sql中如果建表时，指定的主键是联合主键(由多个列共同作为主键)，
		在hbase中，rowkey就是多个主键共同拼接的结果！


# 5.安装启动

#### **步骤 1: 下载 Phoenix**

http://archive.apache.org/dist/phoenix/

#### **步骤 2: 解压 jar 包**

```shell
tar -zxvf apache-phoenix-4.14.3-HBase-1.4-bin.tar.gz

mv apache-phoenix-4.14.3-HBase-1.4-bin.tar.gz phoenix
```

#### **步骤 3: 复制 jar 包**

复制 HBase 需要用到  jar 包到Hbase的lib目录中

```shell
phoenix-4.14.3-HBase-1.4-client.jar
phoenix-4.14.3-HBase-1.4-server.jar
phoenix-core-4.14.3-HBase-1.4.jar
```

#### **步骤 4: 配置环境变量**

```shell
export PHOENIX_HOME=/opt/module/phoenix
export PHOENIX_CLASSPATH=$PHOENIX_HOME
export PATH=$PATH:$PHOENIX_HOME/bin
```

#### **步骤 5: 启动 hadoop, zookeeper, HBase**

#### **步骤 6: 启动 Phoenix**

```shell
sqlline.py [hostname]:2181
```



# 6.表操作使用

```shell
help   帮助命令操作
1. 显示所有表
!tables

# 或者
!table

2.创建表
	2.1.char类型必须添加长度限制
	2.2.varchar 可以不用长度限制
	2.3.主键映射到 HBase 中会成为 Rowkey. 如果有多个主键(联合主键), 会把多个主键的值拼成 rowkey
	2.4.在 Phoenix 中, 默认会把表名,字段名等自动转换成大写. 如果要使用消息, 需要把他们用双引号括起来.

```



## 6.1.hbase中表不存在

```mysql
#创建表
CREATE TABLE IF NOT EXISTS us_population (
      state CHAR(2) NOT NULL,
      city VARCHAR NOT NULL,
      population BIGINT
      CONSTRAINT my_pk PRIMARY KEY (state, city));
      
#插入记录
upsert into us_population values('NY','NewYork',8143197);
upsert into us_population values('CA','Los Angeles',3844829);
upsert into us_population values('IL','Chicago',2842518);

#说明: upset可以看成是update和insert的结合体.

# 查询
select * from US_POPULATION;
select * from us_population where state='NY';

# 删除记录
delete from us_population where state='NY';

#删除表
drop table us_population;

#退出命令行
! quit
```



## 6.2 hbase表存在

 默认情况下, 直接在 HBase 中创建的表通过 Phoenix 是查不到的.创建表需要对应hbase中的列；创建后在 HBase 中添加的数据在 Phoenix 中也可以查询到

- ①如果这个表只希望执行查询操作，不希望执行修改，那么可以创建View；Phoenix 创建的视图是只读的, 所以只能用来查询, 无法通过视图对数据进行修改等操作.

```mysql
create view  "emp"(
  "empno" varchar primary key, 
   "info"."ename" varchar, 
  "info"."job" varchar, 
  "info"."mgr" varchar, 
  "info"."hiredate" varchar, 
  "info"."sal" varchar, 
  "info"."comm" varchar, 
  "info"."deptno" varchar
)
```



②如果这个表需要增删改查，可以创建表

```mysql
create table  "t3"(
  "id" varchar primary key, 
   "info"."name" varchar, 
  "info"."age" varchar, 
  "info"."gender" varchar
)column_encoded_bytes=0
```





# 7.通过phoenix在hbase创建二级索引

**概念：**在hbase中，查询数据时，一般都会指定rowkey，或指定rowkey的范围！rowkey称为一级索引
		如果查询某个具体的列，hbase在高版本也支持在列上创建索引，在列上创建的索引称为二级索引！之前如果要创建二级索引，需要自己调用HBase的API，写起来很麻烦！
		

如果使用Phoneix，只需要一行`create index 索引名 on 表名(列) SQL`

Phoneix帮助我们创建二级索引！二级索引的目的在想执行查询某些列的数据时，加快效率！

```mysql
# 例子
创建索引： create index idx_age on "t3"("info"."age");
删除索引：  drop index 索引名 on 表名
```



## 7.1 配置使用

给 HBase 配置支持创建二级索引

**配置如下以支持二级索引**：

**步骤 1: 添加如下配置到 HBase 的 Hregionerver 节点的 hbase-site.xml**

```xml
<!-- phoenix regionserver 配置参数 -->
<property>
    <name>hbase.regionserver.wal.codec</name>
    <value>org.apache.hadoop.hbase.regionserver.wal.IndexedWALEditCodec</value>
</property>

<property>
    <name>hbase.region.server.rpc.scheduler.factory.class</name>
    <value>org.apache.hadoop.hbase.ipc.PhoenixRpcSchedulerFactory</value>
<description>Factory to create the Phoenix RPC Scheduler that uses separate queues for index and metadata updates</description>
</property>

<property>
    <name>hbase.rpc.controllerfactory.class</name>
    <value>org.apache.hadoop.hbase.ipc.controller.ServerRpcControllerFactory</value>
    <description>Factory to create the Phoenix RPC Scheduler that uses separate queues for index and metadata updates</description>
</property>
```



**步骤 2: 添加如下配置到 HBase 的 Hmaster 节点的 hbase-site.xml**

```xml
<!-- phoenix master 配置参数 -->
<property>
    <name>hbase.master.loadbalancer.class</name>
    <value>org.apache.phoenix.hbase.index.balancer.IndexLoadBalancer</value>
</property>

<property>
    <name>hbase.coprocessor.master.classes</name>
    <value>org.apache.phoenix.hbase.index.master.IndexMasterObserver</value>
</property>
```

**步骤 3: 测试是否支持**

准备数据:

```mysql
create table user_1(id varchar primary key, name varchar, addr varchar)

upsert into user_1 values ('1', 'zs', 'beijing');
upsert into user_1 values ('2', 'lisi', 'shanghai');
upsert into user_1 values ('3', 'ww', 'sz');
```

默认情况下, 只要 rowkey 支持索引(就是上面的 id);其他字段是不支持索引的:

给name添加索引

```mysql
create index idx_user_1 on user_1(name)

select * from user_1 where name = "ww";
```

这种索引, 对 name 创建的索引, 则查询的时候也必须只查询 name 字段.



**查看索引使用**

	使用explain + select sql查看是否使用了索引
			  在mysql中 type=All，代表全部扫描，没有使用上索引！
			 在phoneix中如果出现了FULL SCAN ，代表没有使用上二级索引，出现了全部列扫描
	
	测试时不能写select *;	  
		  如果出现RANGE SCAN OVER IDX_AGE，代表使用上了某个索引，进行了范围查询！


## 7.2 二级索引的分类
```mysql
在hbase中二级索引分为gloal(全局)和local(本地)的二级索引！
	
不管是全局索引还是本地索引，都是为了加快查询！从作用上说，没有区别！
	
区别在于适合的场景不同：
	
	gloal(全局)索引，（默认）；在创建后，专门在hbase中，生成一个表，讲索引的信息存储在表中！
		适合多读少写的场景！ 每次写操作，不仅要更新数据，还需要更新索引！
		数据表在rs1，索引表在rs2中，每次发送一次put请求，必须先请求rs1，在请求rs2，才能完成更新！
		网络开销很大，加重rs的压力！
	
	local(本地)索引，在创建后，在表中，创建一个列族，在这个列族中保存索引的信息！
		适合多写少读的场景！  索引是以列族的形式在表中存储，索引和数据在一个rs上，此时
		频繁写操作时，只需要请求当前的regionserver!
		
创建全局索引的方法:
	CREATE INDEX my_index ON my_table (my_col)

创建局部索引的方法(相比全局索引多了一个关键字 local):
	CREATE LOCAL INDEX my_index ON my_table (my_index)
```
