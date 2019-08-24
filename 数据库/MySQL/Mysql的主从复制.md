# 1.Mysql配置主从

​	主从复制原理是从服务器从主服务器中通过二进制日志文件拉去日志进行备份，如下图：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g6b5248xwkj20g607i3zy.jpg)

## 1.1 Master操作

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

  

## 1.2 Slave操作

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