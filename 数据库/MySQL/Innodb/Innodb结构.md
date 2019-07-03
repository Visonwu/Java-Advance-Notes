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





## 2.2 日志文件

