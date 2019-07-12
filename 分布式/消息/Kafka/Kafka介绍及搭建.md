# 1. Kafka介绍

​		Kafka是一款分布式消息发布和订阅系统，具有高性能、高吞吐量的特点而被广泛应用与大数据传输场景。它是由LinkedIn公司开发，使用Scala语言编写，之后成为Apache基金会的一个顶级项目。kafka提供了类似JMS的特性，但是在设计和实现上是完全不同的，而且他也不是JMS规范的实现。

## 1.1 背景

​		kafka作为一个消息系统，早起设计的目的是用作LinkedIn的活动流（Activity Stream）和运营数据处理管道（Pipeline）。活动流数据是所有的网站对用户的使用情况做分析的时候要用到的最常规的部分,活动数据包括页面的访问量（PV）、被查看内容方面的信息以及搜索内容。这种数据通常的处理方式是先把各种活动以日志的形式写入某种文件，然后周期性的对这些文件进行统计分析。运营数据指的是服务器的性能数据（CPU、IO使用率、请求时间、服务日志等）。



## 1.2 应用场景

​	   由于kafka具有更好的**吞吐量、内置分区、冗余及容错性**的优点(kafka每秒可以处理几十万消息)，让kafka成为了一个很好的大规模消息处理应用的解决方案。所以在企业级应用长，主要会应用于如下几个方面：

- **行为跟踪**：kafka可以用于跟踪用户浏览页面、搜索及其他行为。通过发布-订阅模式实时记录到对应的topic中，通过后端大数据平台接入处理分析，并做更进一步的实时处理和监控

- **日志收集**：日志收集方面，有很多比较优秀的产品，比如 Apache Flume ，很多公司使用kafka 代理日志聚合。日志聚合表示从服务器上收集日志文件，然后放到一 个集中的平台（文件服务器）进行处理。在实际应用开发中，我们应用程序的 log 都会输出到本地的磁盘上，排查问题的话通过 linux 命令来搞定，如果应用程序组成了负载均衡集群，并且集群的机器有几十台以上，那么想通过日志快速定位到问题，就是很麻烦的事情了。所以一般都会做一个日志统一收集平台管理 log 日志用来快速查询重要应用的问题。所以很多公司的套路都是把应用日志几种到 kafka 上，然后分别导入到 es 和 hdfs 上，用来做实时检索分析和离线统计数据备份等。而另一方面， kafka 本身又提供了很好的 api 来集成日志并且做日志收集。

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4tzg6r8p1j20qk0jln6s.jpg)

## 1.3 Kafka架构

​		一个典型的kafka 集群包含若干 Producer （可以是应用节点产生的消息，也可以是通过Flume 收集日志产生的事件），若干个 Broker kafka 支持水平扩展）、若干个 ConsumerGroup ，以及一个 zookeeper 集群。 kafka 通过 zookeeper 管理集群配置及服务协同。Producer使用 push 模式将消息发布到 broker consumer 通过监听使用 pull 模式从broker 订阅并消费消息 。
​		多个broker 协同工作， producer 和 consumer 部署在各个业务逻辑 中。三者通过zookeeper 管理协调请求和转发。这样就组成了一个高性能的分布式消息发布和订阅系统 。

​	图上有一个细节是和其他mq中间件不同的点，**producer 发送消息到broker的过程是push，而consumer从broker消费消息的过程是pull，主动去拉数据**。而不是broker把数据主动发送给consumer

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4tzkqgm2aj20q80dpdjl.jpg)

# 2.安装使用

## 2.1 单机安装启动

```bash
下载地址：https://www.apache.org/dyn/closer.cgi?path=/kafka/2.2.0/kafka_2.12-2.2.0.tgz

1. tar -zxvf解压安装包

2. 需要先启动zookeeper，如果没有搭建zookeeper环境，可以直接运行kafka内嵌的zookeeper
启动命令： bin/zookeeper-server-start.sh config/zookeeper.properties&

3. 进入kafka目录，运行 bin/kafka-server-start.sh｛-daemon} config/server.properties &

4. 进入kafka目录，运行bin/kafka-server-stop.sh config/server.properties
```

## 2.2 目录介绍

```bash
1. /bin 操作kafka的可执行脚本
2. /config 配置文件
3. /libs 依赖库目录
4. /logs 日志数据目录
```



## 2.3 集群配置

```bash
1. 修改server.properties,不同服务器设置各自的id,每一个服务器的id不能重复
	broker.id=1
2. 修改server.properties 修改成本机IP,表示基于下面这个连接，否则是localhost
advertised.listeners=PLAINTEXT://192.168.11.153:9092

3.注册各个服务器的zookeeper地址地址
zookeeper.connect=server1:2181,server2:2181

4.分别启动三个kafka
bin/kafka-server-start.sh -daemon config/server.properties &

当Kafka broker启动时，它会在ZK上注册自己的IP和端口号，客户端就通过这个IP和端口号来连接
```

**安装问题**

jdk版本尽量高一点，否则kafka启动会报错