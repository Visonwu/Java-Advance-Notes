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

