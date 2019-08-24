# 1.背景

## 1.1 数据库性能瓶颈主要原因

1，数据库连接

2，表数据量大（空间存储的问题）

​	索引命中不了 全表的扫描

 	命中索引

​	 硬盘级索引，它是存储在硬盘里面  IO操作

3，硬件资源限制（QPS\TPS）

## 1.2 数据性能优化方案

1，sql优化

2，缓存

3，建好索引

**4，读写分离**

**5，分库分表**

### 1）读写分离

1，数据库连接

2，硬件资源限制（QPS\TPS）

​		区别读、写多数据源方式进行数据的存储和加载；数据的存储（增删改）一般指定写数据源，数据的读取查询指定读数据源 (读写分离会基于主从复制)



主从有分为如下几种：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g578ctj439j20oy0ctta9.jpg)



**主从复制，延迟是怎么产生的？**

1，当master  tps高于slave的sql线程所能承受的范围

2，网络原因

3，磁盘读写耗时



**判断延迟？**

1，show  slave status \G;  sends_behind_master  0

2,  mk-heartbeat  timestamp 进行时间戳的判断



**我们怎么解决延迟问题？**

1，配置更高的硬件资源

2，把IOthread  改变成 多线程的方式

​	mysql5.6  库进行多线程的方式

​	 GTID进行多线程的方式

3， 应用程序自己去判断（mycat有这么方案）

### 2）分库分表

**垂直拆分**

​	1，数据库连接

​	2，硬件资源限制（QPS\TPS）

**水平拆分**

​	1，表数据量大的问题 存储空间也解决了

​	1，数据库连接

​	2，硬件资源限制（QPS\TPS）

​	

# 2.Mysql配置主从

​	主从复制原理是从服务器从主服务器中通过二进制日志文件拉去日志进行备份，如下图：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g6b5248xwkj20g607i3zy.jpg)

## 2.1 Master操作

- 1.进入mysql，创建主从复制的用户及授权

  ```mysql
  # 创建master用户，密码是123456
  CREATE USER 'master'@'%' IDENTIFIED BY '123456'; 
  
  # 授予master用户 REPLICATION SLAVE权限
  GRANT REPLICATION SLAVE ON *.* TO 'master'@'%' IDENTIFIED BY '123456' WITH GRANT OPTION;
  ```

- 2.指定服务ID，开启binlog日志记录，在`/etc/my.cnf`（配置文件即可）中加入 

  ```mysql
  #配置二进制文件名称
  log-bin=mysql-bin
  #配置唯一的ServerId
  server-id=1     
  #要同步的mytest数据库,要同步多个数据库，就多加几个replicate-db-db=数据库名 
  binlog-do-db=mytest 
  #binlog-ignore-db=mysql //要忽略的数据库
  sync_binlog=1
  ```

- 3.重启msyql，然后 通过`SHOW MASTER STATUS;`查看Master db状态,如下表示成功

  ```mysql
  mysql> mysql> show master status;
  +------------------+----------+--------------+------------------+-------------------+
  | File             | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set |
  +------------------+----------+--------------+------------------+-------------------+
  | mysql-bin.000001 |      400 | syn-test     |                  |                   |
  +------------------+----------+--------------+------------------+-------------------+
  ```

  

## 2.2 Slave操作

- 1.指定服务器ID，指定同步的binlog存储位置，在`/etc/my.cnf`中加入

```mysql
#唯一的ServerId
server-id=2
# 中继日志配置
relay-log=slave-relay-bin
relay-log-index=slave-relay-bin.index
read_only=1
# 可以指定要复制的库,在master端不指定binlog-do-db，在slave端用replication-do-db来过滤
replicate_do_db=mytest
#replicate-ignore-db=mysql #忽略的库
```

- 2.重启服务器 `service mysql restart`

- 3.接入slave的mysql服务，并配置

```mysql
#1.关闭slave节点
mysql> stop slave;

#2.关联master节点信息
mysql> change master to
master_host='192.168.124.132',
master_user='master',
master_password='123456',
master_port=3306,
master_log_file='mysql-bin.000001',
master_log_pos=154;

#3.启动slave节点
mysql> start slave;
```

- 4.查看状态 `show slave status \G;` 相关信息如下所示

```mysql
mysql> show slave status \G;
*************************** 1. row ***************************
               Slave_IO_State: Waiting for master to send event
                  Master_Host: 192.168.124.132
                  Master_User: master
                  Master_Port: 3306
                Connect_Retry: 60
              Master_Log_File: mysql-bin.000001
          Read_Master_Log_Pos: 649
               Relay_Log_File: slave-relay-bin.000002
                Relay_Log_Pos: 815
        Relay_Master_Log_File: mysql-bin.000001
             Slave_IO_Running: Yes
            Slave_SQL_Running: Yes
              Replicate_Do_DB: syn-test
 		Seconds_Behind_Master: 0   # 表示成功
 		  		  Master_UUID: ab9bfe9c-a90c-11e9-8a32-000c29b8e97f
             Master_Info_File: /usr/local/mysql/data/master.info
                    SQL_Delay: 0
          SQL_Remaining_Delay: NULL
      Slave_SQL_Running_State: Slave has read all relay log; waiting for more updates
           Master_Retry_Count: 86400
           。。。。
```

最后在主服务中的mytest库中，分别CRUD表及相关信息在从表都可以查看到同步的内容。



# 3.Mycat

​		Mycat 是开源的分布式数据库中间件，基于阿里的cobar的开源框架之上。它处于数据库服务与应用服务之间。它是进行数据处理与整合的中间服务。

- 一个彻底开源的，面向企业应用开发的大数据库集群

-  支持事务、ACID、可以替代MySQL的加强版数据库

- 一个融合内存缓存技术、NoSQL技术、HDFS大数据的新型SQL Server

![](http://ww1.sinaimg.cn/mw690/b8a27c2fgy1g578ld5y96j20lw0dxdh1.jpg)

## 3.1 Mycat相关概念

基于这张图理解下面的相关概念

![](http://ww1.sinaimg.cn/mw690/b8a27c2fgy1g578sfctr6j20q30f1wl1.jpg)





- **逻辑库：**  db_user， db_store（上面两个黄色的库，逻辑上的库不是物理上的库）

- **逻辑表**

  - **分片表**： 用户表（上面的用户表是放在两个物理机做的分片处理，分片规则可以自定义）

  - **全局表**：数字字典表（冗余表，数字字典表表示数据一般都不会变，每一个物理机都会存储一模一样的数据，比如：用户等级）

  - **ER表**： 用户地址表（由于用户分片，不同的用户放在不同的物理机，那么相对应的用户地址表也存储在同一个物理机上，这样根据用户join查询地址也不用跨库查询了）

  - **非分片表**：  门店表，店员表（这两个基于主从复制，分两个机器存储，不是分片表）

- **分片规则：**   比如用户  userID%2

- **节点**  ：
  - **分片节点**：数据切分后，一个大表被分到不同的分片数据库上面，每个表分片所在的数据库就是分片节点（dataNode）
  - **节点主机**：数据切分后，每个分片节点（dataNode）不一定都会独占一台机器，同一机器上面可以有多个分片数据库，这样一个或多个分片节点（dataNode）所在的机器就是节点主机（dataHost）,为了规避单节点主机并发数限制，尽量将读写压力高的分片节点（dataNode）均衡的放在不同的节点主机（dataHost）。

