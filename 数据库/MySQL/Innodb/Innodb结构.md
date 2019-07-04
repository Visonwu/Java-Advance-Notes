# 一、Innodb体系结构

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4mf80uw7aj20j709sdhz.jpg)

解析：

- 1.包含多个后台线程
  - 负责刷新缓存池中的数据，保证缓存最新的数据
  - 将修改的数据文件刷新到硬盘上
  - 同时将异常情况的数据库恢复到正常情况
- 2.一个内存池
  - 包含进程/线程需要的数据
  - 缓存数据
  - 重做日志。。。
- 3.文件系统

## 1.1. 后台线程

​        Innodb工作机制是**单进程多线程**，默认情况下（mysql5.7版本）有10个IO线程，一个master线程，一个锁监控线程，一个错误监控线程。

比如所示：可以看到10个IO线程包含，4个读，4个写，一个日志线程，一个插入缓存线程。

可以通过配置文件my.cnf来修改

```java
innodb_write_io_threads = 4
innodb_read_io_threads = 4
```

```bash
mysql> show engine innodb status;
.......
FILE I/O
--------
I/O thread 0 state: waiting for completed aio requests (insert buffer thread)
I/O thread 1 state: waiting for completed aio requests (log thread)
I/O thread 2 state: waiting for completed aio requests (read thread)
I/O thread 3 state: waiting for completed aio requests (read thread)
I/O thread 4 state: waiting for completed aio requests (read thread)
I/O thread 5 state: waiting for completed aio requests (read thread)
I/O thread 6 state: waiting for completed aio requests (write thread)
I/O thread 7 state: waiting for completed aio requests (write thread)
I/O thread 8 state: waiting for completed aio requests (write thread)
I/O thread 9 state: waiting for completed aio requests (write thread)
......

```

## 1.2. Innodb内存分布

​	Inndb内存分布主要是包含日志缓冲和缓冲池，以及额外日志缓冲；

- **日志缓冲**主要是存**重做日志缓冲**，innoDB存储引擎首先将重做日志信息放入这个缓冲区，然后按照一定的频率将其刷入重做日志文件中。重做日志缓冲区一般不需要很多，只要保证每秒产生的事务量在这个缓冲大小之内即可。可以通过innodb_log_buffer_size参数设置大小
- **缓冲池**是占用大部分内存，如下，通过innodb_buffer_pool_size设置；Innodb存储引擎总是按照页（16k）读取文件到到缓冲池，然后按照LRU（最少使用）来保存缓冲数据

- **额外的内存池**:在innoDB存储引擎中，对内存的管理是通过一种称为内存堆(heap)的方式进行。在对一些数据结构本身的内存进行分配时，需要从额外的内存池中进行申请，当该区域的内存不够时，需要从缓冲池中申请。例如L:分配了缓冲池，但是每个缓冲池中的帧缓冲(frame buffer)还有对应的缓冲控制对象(buffer control block)，这些记录了一些诸如LRU、锁、等待等信息，而这个对象的内存就需要从额外内存池中申请。因此在申请了很大的缓冲池是也要考虑相应增加这个值。

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4mlrmdv4aj20fx08wmxa.jpg)

```bash
mysql> show engine innodb status \G;
....
----------------------
BUFFER POOL AND MEMORY
----------------------
Total large memory allocated 137428992
Dictionary memory allocated 140621
Buffer pool size   8192
Free buffers       7874
Database pages     318
Old database pages 0
Modified db pages  0
Pending reads      0
Pending writes: LRU 0, flush list 0, single page 0
Pages made young 0, not young 0
0.00 youngs/s, 0.00 non-youngs/s
Pages read 283, created 35, written 57
0.00 reads/s, 0.00 creates/s, 0.00 writes/s
No buffer pool page gets since the last printout
Pages read ahead 0.00/s, evicted without access 0.00/s, Random read ahead 0.00/s
LRU len: 318, unzip_LRU len: 0
I/O sum[0]:cur[0], unzip sum[0]:cur[0]
....
```

​       解读上面的BUFFER POOL AND MEMORY;上面的命令可以看到最近时间innodb的状态信息，这里的buffer展示的有多少缓冲帧（page，页），一帧是16k，所以上面的buffer pool size = 8192*16/1024=128M;同理其他类似；

- Database pages 表示当前已经使用的缓冲页数
- Modified db pages 表示脏页，也就是缓冲数据被修改过，还没有刷新到磁盘去



## 1.3 关键特性

​      Innodb存储引擎的关键特性包含插入缓冲，两次写，以及自适应哈希索引，带来更好的性能和可靠性。

### 1.3.1 插入缓冲

​		Innodb提供插入缓冲来提供数据插入的性能，当我们只有插入自动增长的主键时候，可以直接顺序插入在数据页上。但是如果我们插入的数据有非聚集索引那么就需要随机访问数据页进行访问（B+树特性），所以我们提供了插入缓冲，当我们需要插入的时候先判断缓冲区中 该非聚集索引页是否在，如果存在就插入到当前非聚集索引页中，否则就放入一个插入缓冲中，然后按照一定频率执行插入缓冲和非聚集索引页的合并操作。

​	插入缓冲默认最大可以占用1/2的缓冲区大小。

**插入缓冲的条件：**

- 当前表的中的索引是辅助索引
- 索引不是唯一索引

```bash
mysql> show engine innodb status \G;
....
-------------------------------------
INSERT BUFFER AND ADAPTIVE HASH INDEX
-------------------------------------
Ibuf: size 1, free list len 0, seg size 2, 0 merges
merged operations:
 insert 0, delete mark 0, delete 0
discarded operations:
 insert 0, delete mark 0, delete 0
```

​     如上：**size**表示已经合并的记录页的数量**；free list len** 表示空闲列表的长度；**seg list**表示当前插入缓冲的大小2*16k，**merges**表示合并的次数

### 1.3.2 两次写

​		两次写是用来保证数据的可靠性；默认是开启的，如果要关闭通过skip_innodb_doublewrite可以禁用两次写功能，如果禁用了就有可能会出现数据丢失。

​	在多从服务器的时候，可以考虑启用该参数，但是主服务器记得不要启用

**两次写**的步骤：

- 刷新脏页到磁盘前，先将脏页拷贝到DoubleWrite Buffer（2M）中
- 在分两次将DoubleWrite Buffer写入到共享表空间（两个区---128页，64页为1m为一个区）的磁盘上（顺序写很快）
- 再然后将DoubleWrite Buffer数据写入到磁盘的数据文件
- 如果刷新脏页的失败可以通过共享表空间的数据做恢复

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4mobkdt2nj20gk0abjs5.jpg)

查看两次写状态

```bash
mysql> show global status like 'innodb_dblwr%' \G;
*************************** 1. row ***************************
Variable_name: Innodb_dblwr_pages_written  //两次写入页数
        Value: 5103
*************************** 2. row ***************************
Variable_name: Innodb_dblwr_writes		  //两次写次数
        Value: 867
2 rows in set (0.00 sec)
```



### 1.3.3 自适应哈希索引

​	哈希是一种快速查找的方法，innodb会自动为某些页数据进行hash索引创建，用来提高查询速度，当然哈希索引只能用来查询等值查询`select * from user where name ='vison' `;

 `innodb_adaptive_hash_index`参数设置是否开启自适应哈希索引。

如下：

```bash
mysql> show engine innodb status\G;

-------------------------------------
INSERT BUFFER AND ADAPTIVE HASH INDEX
-------------------------------------
Ibuf: size 1, free list len 47, seg size 49, 4 merges
merged operations:
 insert 3, delete mark 1, delete 0
discarded operations:
 insert 0, delete mark 0, delete 0
Hash table size 276707, node heap has 14 buffer(s)
0.00 hash searches/s, 0.00 non-hash searches/s
```



# 二、文件

## 2.1 参数文件

​	用来制定初始化参数，这些参数定义了某种内存结构的大小等设置。我们配置文件就是参数文件

**数据库的参数可以看为是一个键值对；**

我们可以通过`show variables like` 来过滤相关的参数名

```bash
比如：show varibales like 'innodb_buffer%'
```

当然参数也分为静态和动态参数，以及全局设置和会话设置

## 2.2 日志文件

用来记录Mysql实例对某种条件做出响应时写出的文件，比如错误文件，二进制文件，查询文件等

### 1）错误日志

​	错误日志记录了mysql启动，运行，关闭时候的错误信息，可以通过该文件查看相关信息。

​	比如查看日志如下：

```bash
mysql> show variables like 'log_error'
+---------------+---------------------+
| Variable_name | Value               |
+---------------+---------------------+
| log_error     | /var/log/mysqld.log |
+---------------+---------------------+

```

### 2）慢查询日志

​	慢查询日志对于SQL优化可以带来许多好的帮助，默认输出到文件中，如果要修改为表，也是可以修改的，自己网上找资料，对于性能还是有影响的。

- 是否开启慢查询`slow_query_log`
- 慢查询日志文件`slow_query_log_file`
- 慢查询写日志条件
  - 查询超过多长时间：`long_query_time`
  - 查询没有使用索引：`log_queries_not_using_indexes`

- 借助 `mysqldumpslow --help`提高分析慢查询日志效率





​     默认将运行时间**超过10s的sql**记录下来，该阀值通过`long_query_time`开启。

**默认该日志是不开启的**，需要自己手动开启。

```bash
mysql> show variables like '%long%';
+----------------------------------------------------------+-----------+
| Variable_name                                            | Value     |
+----------------------------------------------------------+-----------+
| long_query_time                                          | 10.000000 |


mysql> show variables like '%slow%';
+---------------------------+-----------------------------------+
| Variable_name             | Value                             |
+---------------------------+-----------------------------------+
| slow_query_log            | ON                                |   //是否开启
| slow_query_log_file       | /var/lib/mysql/localhost-slow.log |	//慢查询日志文件
+---------------------------+-----------------------------------+

mysql> show variables like 'log_queries%';
+-------------------------------+-------+
| Variable_name                 | Value |
+-------------------------------+-------+
| log_queries_not_using_indexes | OFF   |
+-------------------------------+-------+
```



```properties
#配置通过my.cnf配置
slow_query_log=ON
slow_query_log_file=/var/lib/mysql/remotejob-01-slow.log
long_query_time=1
log_queries_not_using_indexes=ON
```

### 3）查询日志

​	比较鸡肋，记录所有对数据库请求的信息,默认关闭，对性能影响很大

```bash
mysql> show variables like '%general_log%';
+------------------+------------------------------+
| Variable_name    | Value                        |
+------------------+------------------------------+
| general_log      | OFF                          |
| general_log_file | /var/lib/mysql/localhost.log |
+------------------+------------------------------+
```

### 4) 二进制日志

​	默认关闭，除了Select或者show的操作，用记录数据库更改操作和执行时间等，二进制日志的主要作用：

- 恢复数据，可以按照某个时间点进行数据恢复
- 复制数据，对于主从等数据可以进行数据复制

**4.1) 相关的配置**

可以通过`log_bin[=name]`,开启二进制日志功能。默认存储当前数据库所在目录`datadir`

```bash
//这里会开启二进制日志功能，二进制日志文件为mysql_bin.000001 
//还有mysql_bin.index 这个存储二进制日志文件的索引
log_bin=mysql_bin

```

-  **max_binlog_size** : 默认大小1073741824，记录单个二进制文件大小，如果超过该文件大小产生新的二进制日志并后缀名+1
-  **sync_binlog**：默认值为 1 ，表示每次事务都会刷新到磁盘上
- **binlog_cache_size** ：默认值为32768 ；基于会话，当开启一个线程开启一个事务操作就会自定分配一个这样的缓存，当事务的记录大于该设定值后，会将缓冲文件写入临时文件；通过**show global status**;查看
  - **Binlog_cache_disk_use**           //查看硬盘使用次数 
  -  **Binlog_cache_use**                //查看缓冲使用次数

- **binlog_format**：设置binlog存储的不同格式

  - **statement**：表示记录逻辑sql，但是对于uuid(),rand()会导致生成的随机数不同
  - **row：**不再简单记录sql，还会包含表的行更改记录。这个相比上面的数据一致性更高，但是会造成大量的日志文件。

  - **mixed：**自定识别写入日志，如果存在动态函数会用row格式记录信息

- **expire_logs_days**：日志自动清除时长



**4.2) 查看二进制日志**

​	由于该日志以二进制的形式存在，需要借助其他工具查看

通过`mysqlbinlog`工具查看

```bash
# 可以mysqlbinlog --help看具体使用方法 比如，
> mysqlbinlog mysql_bin.000001
#对于row记录的信息上面的估计也不清楚，可以通过-v来详细查看
>mysqlbinlog -v mysql_bin.000001
```



## 2.3 socket文件

  当用unix域套接字方式进行连接时需要的文件

```bash
mysql> show variables like 'socket' \G;
*************************** 1. row ***************************
Variable_name: socket
        Value: /var/lib/mysql/mysql.sock

```



## 2.4 Pid文件

​	mysql实例的进程ID文件

```bash
mysql> show variables like 'pid_file'\G;
*************************** 1. row ***************************
Variable_name: pid_file
        Value: /var/run/mysqld/mysqld.pid

```



## 2.5 Mysql表结构文件

​	用来存放mysql表结构的文件,用`.frm`结尾表示，如果新建了一个视图也会新建一个结构文件，可以直接用cat命令打开显示。

## 2.6 Innodb存储引擎文件

   存储引擎存储真正的数据和索引等数据。

### 1）表空间文件

​	默认情况下会初始化一个大小10M,名称为ibdata1的文件，这个文件就是默认的表空间文件，可以通过

```bash
mysql> show variables like 'innodb_data_file_path';
+-----------------------+------------------------+
| Variable_name         | Value                  |
+-----------------------+------------------------+
| innodb_data_file_path | ibdata1:12M:autoextend | //表示自动增长大小
+-----------------------+------------------------+
```

​		通过设置下面的参数，每一个基于innodb引擎的表产生一个单独的表空间，文件名为表名.ibd，这样我们的表空间就不用全部放到默认的表空间了。

```bash
mysql> show variables like 'innodb_file_per_table';
+-----------------------+-------+
| Variable_name         | Value |
+-----------------------+-------+
| innodb_file_per_table | ON    |
+-----------------------+-------+
```

注意点**：当前的idb文件只是记录当前表的数据，索引，插入缓冲信息**，其他信息还是存储在默认的表空间中。

### 2）重做日志文件

​	默认情况下会有两个文件 `ib_logfile0,ib_logfile1`,这两个作为重做日志文件，在某些主机或者实例失败后，可以重新执行，保证数据的完整性

原理

```bash
日志文件组：
  1.每个Innodb存储引擎至少有1个重做日志文件组，每个组至少包含2个重做日志文件(ib_logfile0,ib_logfile1).
  2.可以通过设置多个镜像日志组(mirrored log groups),将不同组放到不同磁盘，提高重做日志的高可用性。
  3.日志组中的文件大小是一致的，以循环的方式运行。文件1写满时，切换到文件2，文件2写满时，再次切换到文件1.
```



Redo log 的持久：
    不是随着事务的提交才写入的，而是在事务的执行过程中，便开始写入redo  中。具体的落盘策略可以进行配置
**RedoLog 是为了实现事务的持久性而出现的产物**

```bash
mysql> show variables like 'innodb%log%';
+----------------------------------+------------+
| Variable_name                    | Value      |
+----------------------------------+------------+
| innodb_log_buffer_size           | 16777216   |//重做日志的缓存大小，默认16M
| innodb_log_file_size             | 50331648   |//重做日志文件大小，默认48M
| innodb_log_files_in_group        | 2          |//日志文件组中重做日志文件的数量，默认2 如：ib_logfile1和ib_logfile2
| innodb_log_group_home_dir        | ./         |//配置指定的目录存储
+----------------------------------+------------+
```

`innodb_flush_log_at_trx_commit`设置：

```bash
  innodb_flush_log_at_trx_commit=1，表示在每次事务提交的时候，都把log buffer刷到文件系统中(os buffer)去，并且调用文件系统的“flush”操作将缓存刷新到磁盘上去。
    
  innodb_flush_log_at_trx_commit=0，表示每隔一秒把log buffer刷到文件系统中(os buffer)去，并且调用文件系统的“flush”操作将缓存刷新到磁盘上去。也就是说一秒之前的日志都保存在日志缓冲区，也就是内存上，如果机器宕掉，可能丢失1秒的事务数据。
       
  innodb_flush_log_at_trx_commit=2，表示在每次事务提交的时候会把log buffer刷到文件系统中(os buffer)去，但是每隔一秒调用文件系统(os buffer)的“flush”操作将缓存刷新到磁盘上去。如果只是MySQL数据库挂掉了，由于文件系统没有问题，那么对应的事务数据并没有丢失。只有在数据库所在的主机操作系统损坏或者突然掉电的情况下，数据库的事务数据可能丢失1秒之类的事务数据。这样的好处，减少了事务数据丢失的概率，而对底层硬件的IO要求也没有那么高(log buffer写到文件系统中，一般只是从log buffer的内存转移的文件系统的内存缓存中，对底层IO没有压力)。MySQL 5.6.6以后，这个“1秒”的刷新还可以用innodb_flush_log_at_timeout 来控制刷新间隔。
       结合上面几个参数的描述，我相信多数企业采用mysql innodb存储引擎都是为了充分保证数据的一致性，所以，innodb_flush_log_at_trx_commit这个参数一般都是 1，这样的话，innodb_flush_log_at_timeout 的设置对其就不起作用。innodb_flush_log_at_timeout 的设置只针对 innodb_flush_log_at_trx_commit为0/2 起作用，所以，此参数可暂时不做研究
```



## 2.7 重做日志和二进制日志的差别

​     **1.类别**

​     二进制日志：记录MySQL**数据库相关的日志记录**，包括InnoDB，MyISAM等其它存储引擎的日志。

​     重做日志：只记录InnoDB存储引擎**本身的事务日志**。

​     **2.内容**

​     二进制日志：记录事务的具体操作内容，**是逻辑日志**。

​     重做日志：记录每个页的更改的**物理情况**。

​     **3.时间**

​     二进制日志：只在事务提交完成后进行写入，**只写磁盘一次**，不论这时事务量多大。

​     重做日志：在事务进行中，就**不断有重做日志条**目(redo entry)写入重做日志文件。