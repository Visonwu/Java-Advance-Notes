# 1.Druid简介

## 1.1 Druid概念

	是一个快速的列式分布式的支持实时分析的数据存储系统。在处理PB级数据，毫秒级，数据实时处理方面
	比传统的OLAP系统有了显著的性能提升；这个和阿里巴巴的开源项目连接池Druid是不相关的。
## 1.2 Druid 特点

![wtiitS.png](https://s1.ax1x.com/2020/09/11/wtiitS.png)

## 1.3 Druid 应用场景

	适用于清洗好的记录实时录入，但不需要更新操作
	适用于支持宽表，不用join操作（换句话说就是单表）
	适用于可以总结出基础的统计指标，用一个字段表示
	适用于实时性要求高的场景
	适用于对数据质量的敏感度不高的场景
## 1.4 Druid对比类似框架

![wtin00.png](https://s1.ax1x.com/2020/09/11/wtin00.png)



# 2.框架原理

![wtiJXR.png](https://s1.ax1x.com/2020/09/11/wtiJXR.png)

# 3. 数据结构

​	与Druid架构相辅相成的是其基于DataSource与Segment的数据结构，它们共同成就了Druid的高性能优势。

数据必须有三类：时间列，维度列和指标列。

![wtidAK.png](https://s1.ax1x.com/2020/09/11/wtidAK.png)



# 4. 安装单机使用

使用imply来安装druid，这个包含了druid并提供了更多的功能。

参考：<https://.imply.io/>



imply集成了Druid，提供了Druid从部署到配置到各种可视化工具的完整的解决方案，imply有点类似于Cloudera Manager

1）将imply-2.7.10.tar.gz上传到ebusiness1的/opt/software目录下，并解压

```shell
[ebusiness1 software]$ tar -zxvf imply-2.7.10.tar.gz -C /opt/module
```

2）修改/opt/module/imply-2.7.10名称为/opt/module/imply

```shell
[ebusiness1 module]$ mv imply-2.7.10/ imply
```

3）修改配置文件

（1）修改Druid的ZK配置

```shell
[ebusiness1 _common]$ vi /opt/module/imply/conf/druid/_common/common.runtime.properties
#修改如下内容
druid.zk.service.host=ebusiness1:2181,ebusiness2:2181,ebusiness3:2181
```

（2）修改启动命令参数，使其不校验不启动内置ZK

```shell
[ebusiness1 supervise]$  vim /opt/module/imply/conf/supervise/quickstart.conf
#修改如下内容

:verify bin/verify-java
\#:verify bin/verify-default-ports
\#:verify bin/verify-version-check
:kill-timeout 10

\#!p10 zk bin/run-zk conf-quickstart
```

4）启动

（1）启动Zookeeper

```shell
[ebusiness1 imply]$ zk.sh start
```

（2）启动imply

```shell
[ebusiness1 imply]$ bin/supervise  -c conf/supervise/quickstart.conf
```

说明：每启动一个服务均会打印出一条日志。可以通过/opt/module/imply/var/sv/查看服务启动时的日志信息

3)停止服务

按Ctrl + c中断监督进程，如果想中断服务后进行干净的启动，请删除/opt/module/imply/var/目录。

