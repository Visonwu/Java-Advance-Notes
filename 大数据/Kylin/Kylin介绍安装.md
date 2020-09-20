# 1.简介

## 1.1 定义

​		Apache Kylin是一个开源的分布式分析引擎，提供Hadoop/Spark之上的SQL查询接口及多维分析（OLAP）能力以支持超大规模数据，最初由eBay开发并贡献至开源社区。它能在亚秒内查询巨大的Hive表。



## 1.2 架构

![wtbOmQ.png](https://s1.ax1x.com/2020/09/11/wtbOmQ.png)

**1）REST Server**

​		REST Server是一套面向应用程序开发的入口点，旨在实现针对Kylin平台的应用开发工作。 此类应用程序可以提供查询、获取结果、触发cube构建任务、获取元数据以及获取用户权限等等。另外可以通过Restful接口实现SQL查询。

**2）查询引擎（Query Engine）**

​		当cube准备就绪后，查询引擎就能够获取并解析用户查询。它随后会与系统中的其它组件进行交互，从而向用户返回对应的结果。 

**3）路由器（Routing）**

​		在最初设计时曾考虑过将Kylin不能执行的查询引导去Hive中继续执行，但在实践后发现Hive与Kylin的速度差异过大，导致用户无法对查询的速度有一致的期望，很可能大多数查询几秒内就返回结果了，而有些查询则要等几分钟到几十分钟，因此体验非常糟糕。最后这个路由功能在**发行版中默认关闭。**

**4）元数据管理工具（Metadata）**

​		Kylin是一款元数据驱动型应用程序。元数据管理工具是一大关键性组件，用于对保存在Kylin当中的所有元数据进行管理，其中包括最为重要的cube元数据。其它全部组件的正常运作都需以元数据管理工具为基础。 Kylin的元数据存储在hbase中。 

**5）任务引擎（Cube Build Engine）**

​		这套引擎的设计目的在于处理所有离线任务，其中包括shell脚本、Java API以及Map Reduce任务等等。任务引擎对Kylin当中的全部任务加以管理与协调，从而确保每一项任务都能得到切实执行并解决其间出现的故障。

## 1.3 特点

Kylin的主要特点包括支持SQL接口、支持超大规模数据集、亚秒级响应、可伸缩性、高吞吐率、BI工具集成等。

1）**标准SQL接口**：Kylin是以标准的SQL作为对外服务的接口。

2）**支持超大数据集**：Kylin对于大数据的支撑能力可能是目前所有技术中最为领先的。早在2015年eBay的生产环境中就能支持百亿记录的秒级查询，之后在移动的应用场景中又有了千亿记录秒级查询的案例。

3）**亚秒级响应**：Kylin拥有优异的查询响应速度，这点得益于预计算，很多复杂的计算，比如连接、聚合，在离线的预计算过程中就已经完成，这大大降低了查询时刻所需的计算量，提高了响应速度。

4）**可伸缩性和高吞吐率**：单节点Kylin可实现每秒70个查询，还可以搭建Kylin的集群。

5）**BI工具集成**

- Kylin可以与现有的BI工具集成，具体包括如下内容。
- ODBC：与Tableau、Excel、PowerBI等工具集成
- JDBC：与Saiku、BIRT等Java工具集成
- RestAPI：与JavaScript、Web网页集成
- Kylin开发团队还贡献了**Zepplin**的插件，也可以使用Zepplin来访问Kylin服务。



## 1.4 术语

1) **BI**

```

BI:Business Intelligence（商业智能）
​	商业智能通常被理解为将企业中现有的数据转化为知识，帮助企业做出明智的业务经营决策的工具。
​   为了将数据转化为知识，需要利用数据仓库、联机分析处理（OLAP）工具和数据挖掘等技术。
```

2) OLAP

```
OLAP:OLAP（online analytical processing）是一种软件技术，它使分析人员能够迅速、一致、交互地从各个方面观察信息，以达到深入理解数据的目的。从各方面观察信息，也就是从不同的维度分析数据，因此OLAP也成为多维分析。
OLAP也可以分为：
​	ROLAP：(Relational OLAP)基于关系型数据库，不需要预计算
​	MOLAP：(Multidimensional OLAP)基于多维数据集，需要预计算


MOLAP基于多维数据集，一个多维数据集称为一个OLAP Cube
Cube如下小格子，类似于魔方的小格子，不同维度可以有不同的数据。
```



![wUfRdx.png](https://s1.ax1x.com/2020/09/12/wUfRdx.png)



3）**表的分类**

- **实体表：**一般是指一个现实存在的业务对象，比如用户，商品，商家，销售员信息表等等

- **事实表**：

  - **事务性事实表**： 一般指随着业务发生不断产生数据。特点是一旦发生不会再变化；一般比如，交易流水，操作日志，出库入库记录等等。

  - **周期型事实表**，一般指随着业务发生不断产生变化(更新, 新增)的数据。与事务型不同的是，数据会随着业务周期性的推进而变化。

     比如订单，其中订单状态会周期性变化。再比如，请假、贷款申请，随着批复状态在周期性变化。

- **维度表**：一般是指对应一些业务状态，编号的解释表。也可以称之为码表；比如地区表，订单状态，支付方式，审批状态，商品分类等等。



**星型模型：**

![wUolvD.png](https://s1.ax1x.com/2020/09/12/wUolvD.png)

4)**表的模型**

- 星型模型：围绕一个事实表，有不同的维度表或者实体表关联，一般只需要一步join就可以获取相关信息
- 雪花模型：相比星型，这个有点链式的概念，通过一个表查询另外的信息需要多次join才能获取信息
- 星座模型：是星型的多个事实表的构成。



5）**维度和度量**

类似于分组，下面有三个基本维度，组合起来总共的维度就有2的3次方-1个维度。

![1599874954275](C:\Users\vison\AppData\Roaming\Typora\typora-user-images\1599874954275.png)

# 2. 安装

## 2.1  **Kylin依赖环境**

​		安装Kylin前需先部署好Hadoop、Hive、Zookeeper、HBase，并且需要在/etc/profile中配置以下环境变量HADOOP_HOME，HIVE_HOME，HBASE_HOME，记得source使其生效。

## 2.2 Kylin安装

1）下载Kylin安装包

​		下载地址：<http://kylin.apache.org/cn/download/>

2）解压apache-kylin-2.5.1-bin-hbase1x.tar.gz到/opt/module

```shell
[ebusiness1 sorfware]$ tar -zxvf apache-kylin-2.5.1-bin-hbase1x.tar.gz -C /opt/module/
```

注意：启动前检查HADOOP_HOME，HIVE_HOME，HBASE_HOME是否配置完毕

3）启动

（1）启动Kylin之前，需先启动Hadoop（hdfs，yarn，jobhistoryserver）、Zookeeper、Hbase

（2）启动Kylin

```shell
[ebusiness kylin]$ bin/kylin.sh start
```



## 2.3 页面访问

在<http://hostname:7070/kylin>查看Web页面

用户名为：ADMIN，密码为：KYLIN（系统已填）



# 3.Kylin的使用

Apache Kylin™ 令使用者仅需三步，即可实现超大数据集上的亚秒级查询。

- 1）定义数据集上的一个星形或雪花形模型
- 2）在定义的数据表上构建cube

- 3）使用标准 SQL 通过 ODBC、JDBC 或 RESTFUL API 进行查询，仅需亚秒级响应时间即可获得查询结果





//TODO待续