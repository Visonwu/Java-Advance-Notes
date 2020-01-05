# 1.MySQL体系结构

## 1.1 模块详解

![lNpp5D.png](https://s2.ax1x.com/2020/01/02/lNpp5D.png)

```text
上图解析：

1.Connector：接入方支持多种协议

2.Management Serveices&Utilities：系统管理和控制工具，mysqldump、 mysql复制集群、分区管理等

3.Connection Pool连接池：管理缓冲用户连接、用户名、密码、权限校验、线程处理等需要缓存的需求

4.SQL Interface SQL接口：接受用户的SQL命令，并且返回用户需要查询的结果，类似于MVC中的C层。

5.Parser解析器，SQL命令传递到解析器的时候会被解析器验证和解析。解析器是由Lex和YACC实现的

6.Optimizer查询优化器，SQL语句在查询之前会使用查询优化器对查询进行优化

7.Cache和Buffer（高速缓存区）查询缓存，如果查询缓存有命中的查询结果，查询语句就可以直接去查询缓存中取数据

8.pluggable storage Engines 插件式存储引擎。存储引擎是MySql中具体的与文件打交道的子系统

9.file system文件系统，数据、日志（redo，undo）、索引、错误日志、查询记录、慢查询等

```



## 1.2 架构分层

​		总体上，我们可以把MySQL 分成三层，跟客户端对接的**连接层**，真正执行操作的**服务层**，和跟硬件打交道的**存储引擎层**（参考MyBatis：接口、核心、基础)。

![lNCQun.png](https://s2.ax1x.com/2020/01/02/lNCQun.png)

- **连接层**

  ​    我们的客户端要连接到MySQL 服务器3306 端口，必须要跟服务端建立连接，那么
  管理所有的连接，验证客户端的身份和权限，这些功能就在连接层完成。

- **服务层**
        连接层会把SQL 语句交给服务层，这里面又包含一系列的流程：
  比如查询缓存的判断、根据SQL 调用相应的接口，对我们的SQL 语句进行词法和语法的解析（比如关键字怎么识别，别名怎么识别，语法有没有错误等等）。然后就是优化器，MySQL 底层会根据一定的规则对我们的SQL 语句进行优化，最后再交给执行器去执行。
- **存储引擎**
        存储引擎就是我们的数据真正存放的地方，在MySQL 里面支持不同的存储引擎。再往下就是内存或者磁盘。





# 2.存储引擎

​		在关系型数据库里面，数据是放在表Table 里面的我们可以把这个表理解成Excel 电子表格的形式。所以我们的表在存储数据的同时，还要组织数据的存储结构，这个存储结构就是由我们的存储引擎决定的，所以我们也可
以把存储引擎叫做表类型。
​		在MySQL 里面，支持多种存储引擎，他们是可以替换的，所以叫做插件式的存储引擎。为什么要搞这么多存储引擎呢？一种还不够用吗？

## 2.1 查看存储引擎

### 1) 查看表的存储引擎

比如我们数据库里面已经存在的表，我们怎么查看它们的存储引擎呢？

```sql
--
show table status from `weixin`;
```

或者通过DDL 建表语句来查看。
在MySQL 里面，我们创建的每一张表都可以指定它的存储引擎，而不是一个数据库只能使用一个存储引擎。存储引擎的使用是以表为单位的。而且，创建表之后还可以修改存储引擎

### 2) 服务器上物理存储

我们说一张表使用的存储引擎决定我们存储数据的结构，那在服务器上它们是怎么存储的呢？我们先要找到数据库存放数据的路径。

```sql
show variables like 'datadir';
```

> 默认情况下，每个数据库有一个自己文件夹，以weixin数据库为例。
>
> - 任何一个存储引擎都有一个frm 文件，这个是表结构定义文件。
>
> - 不同的存储引擎存放数据的方式不一样，产生的文件也不一样，innodb 是1 个；memory 没有，myisam 是两个。



## 2.2 存储引擎比较

官网：https://dev.mysql.com/doc/refman/5.7/en/storage-engines.html

**常见存储引擎**

> ​		MyISAM 和InnoDB 是我们用得最多的两个存储引擎，在MySQL 5.5 版本之前，默认的存储引擎是MyISAM，它是MySQL 自带的。我们创建表的时候不指定存储引擎，它就会使用MyISAM 作为存储引擎。MyISAM 的前身是ISAM（Indexed Sequential Access Method：利用索引，顺序存取数据的方法）。
> ​		5.5 版本之后默认的存储引擎改成了InnoDB，它是第三方公司为MySQL 开发的。为什么要改呢？最主要的原因还是InnoDB 支持事务，支持行级别的锁，对于业务一致性要求高的场景来说更适合

我们可以用这个命令查看数据库对存储引擎的支持情况

```sql
show engines;
```

### 1） MyISAM

**存储：**一张表用MyISAM引擎有3 个文件：表结构（.frm），数据(.MYD)，索引(.MYI)

**应用范围比较小：**表级锁定限制了读/写的性能，因此在Web 和数据仓库配置中，它通常用于只读或以读为主的工作。
**特点：**

- 支持表级别的锁（插入和更新会锁表）；不支持事务
- 拥有较高的插入（insert）和查询（select）速度
- 存储了表的行数（count 速度更快）
  （怎么快速向数据库插入100 万条数据？我们有一种先用MyISAM 插入数据，然后修改存储引擎为InnoDB 的操作。）


**适合：**只读之类的数据分析的项目。



### 2） InnoDB

官网：https://dev.mysql.com/doc/refman/5.7/en/innodb-storage-engine.html

**存储：**一张表用InnoDB引擎有2 个文件：表结构（.frm），数据+索引(.idb）

```
		mysql 5.7 中的默认存储引擎。InnoDB 是一个事务安全（与ACID 兼容）的MySQL存储引擎，它具有提交、回滚和崩溃恢复功能来保护用户数据。InnoDB 行级锁（不升级为更粗粒度的锁）和Oracle 风格的一致非锁读提高了多用户并发性和性能。InnoDB 将用户数据存储在聚集索引中，以减少基于主键的常见查询的I/O。为了保持数据完整性，InnoDB 还支持外键引用完整性约束。
```

**特点：**

- 支持事务，支持外键，因此数据的完整性、一致性更高。
- 支持行级别的锁和表级别的锁。
- 支持读写并发，写不阻塞读（MVCC）。
- 特殊的索引存放方式，可以减少IO，提升查询效率。
  

**适合：**经常更新的表，存在并发读写或者有事务处理的业务系统。



### 3）Memory

Memory（1 个文件）

 数据都是存储在内存中， 数据都是存储在内存中，IO效率要比其他引擎高很多 效率要比其他引擎高很多；服务重启数据丢失，内存数据表默认只有 服务重启数据丢失，内存数据表默认只有16M

**特点：**

- 支持hash索引，B tree索引，默认hash（查找复杂度0(1)）

- 字段长度都是固定长度varchar(32)=char(32)

- 不支持大数据存储类型字段如 blog，text

- 表级锁

  

**应用场景：**

- 等值查找热度较高数据
- 查询结果内存中的计算，大多数都是采用这种存储引擎
- 作为临时表存储需计算的数据





### 4）CVS存储引擎
  数据存储以用CSV文件

**特点：**
		不能定义没有索引、列定义必须为NOT NULL、不能设置自增列–> 不适用大表或者数据的在线处理
CSV数据的存储用,隔开，可直接编辑CSV文件进行数据的编排–> 数据安全性低
注：编辑之后，要生效使用flush table XXX 命令

**应用场景：**

- 数据的快速导出导入
- 表格直接转换成 表格直接转换成CSV



### 5）Archive存储引擎

  压缩协议进行数据的存储,数据存储为 数据存储为ARZ文件格式 文件格式

**特点：**

- 只支持insert和select两种操作
- 只允许自增ID列建立索引
- 行级锁
- 不支持事务
- 数据占用磁盘少

**应用场景：**

- 日志系统
- 大量的设备数据采集



## 2.3  如何选择存储引擎？
- 如果对数据一致性要求比较高，需要事务支持，可以选择InnoDB。
- 如果数据查询多更新少，对查询性能要求比较高，可以选择MyISAM。
- 如果需要一个用于查询的临时表，可以选择Memory。
- 如果所有的存储引擎都不能满足你的需求，并且技术能力足够，可以根据官网内部
  手册用C 语言开发一个存储引擎：https://dev.mysql.com/doc/internals/en/custom-engine.html



# 3. 通信协议

### 1) 通信类型

​	同步或者异步；一般来说我们连接数据库都是同步连接

### 2)  连接方式

MySQL 既支持短连接，也支持长连接。短连接就是操作完毕以后，马上close 掉。

- 长连接可以保持打开，减少服务端创建和释放连接的消耗，后面的程序访问的时候还可以使用这个连接。一般我们会在连接池中使用长连接。
- 保持长连接会消耗内存。长时间不活动的连接，MySQL 服务器会断开

**线程长连接超时时间：**

```sql
--长连接情况： 默认都是28800 秒，8 小时。
show global variables like 'wait_timeout'; -- 非交互式超时时间，如JDBC 程序
show global variables like 'interactive_timeout'; -- 交互式超时时间，如数据库工具
```

**线程连接情况：**

```sql
-- 查看线程情况
show global status like 'Thread%';

--Threads_cached	0  //  缓存中的线程连接数
--Threads_connected	32  //当前打开的连接数。
--Threads_created	3348 //为处理连接创建的线程数
--Threads_running	1   //非睡眠状态的连接数，通常指并发连接数

-- 每产生一个连接或者一个会话，在服务端就会创建一个线程来处理。反过来，如果要杀死会话，就是Kill 线程

```

**线程当前状态：**

参考：https://dev.mysql.com/doc/refman/5.7/en/show-processlist.html

```sql
show processlist 

 kill {id}
 -- 关闭连接： kill 线程id的方式进行连接的杀掉
```

**一些常见的状态：**
	参考：https://dev.mysql.com/doc/refman/5.7/en/thread-commands.html

```sql
状态含义
Sleep 					线程正在等待客户端，以向它发送一个新语句
Query 					线程正在执行查询或往客户端发送数据
Locked 					该查询被其它查询锁定
Copying to tmp table on disk
		临时结果集合大于tmp_table_size。线程把临时表从存储器内部格式改变为磁盘模式，以节约存储器
Sending data 			线程正在为SELECT 语句处理行，同时正在向客户端发送数据
Sorting for group 		线程正在进行分类，以满足GROUP BY 要求
Sorting for order 		线程正在进行分类，以满足ORDER BY 要求
```

**MySQL最大连接数**

```sql
--在5.7 版本中默认是151 个，最大可以设置成16384（2^14）。
show variables like 'max_connections';
```



注：

```text
show 的参数说明：
1、级别：会话session 级别（默认）；全局global 级别
2、动态修改：set，重启后失效；永久生效，修改配置文件/etc/my.cnf
```



### 3) 通信协议

- 第一种是Unix Socket
  比如我们在Linux 服务器上，如果没有指定-h 参数，它就用socket 方式登录（省略了-S /var/lib/mysql/mysql.sock）

- 如果指定-h 参数，就会用第二种方式，TCP/IP 协议

  我们的编程语言的连接模块都是用TCP 协议连接到MySQL 服务器的， 比如mysql-connector-java-x.x.xx.jar



另外还有命名管道（Named Pipes）和内存共享（Share Memory）的方式，这两种通信方式只能在Windows 上面使用，一般用得比较少。



### 4)  通信方式

- **单工**
  在两台计算机通信的时候，数据的传输是单向的。生活中的类比：遥控器。
- **半双工**
  在两台计算机之间，数据传输是双向的，你可以给我发送，我也可以给你发送，但是在这个通讯连接里面，同一时间只能有一台服务器在发送数据，也就是你要给我发的话，也必须等我发给你完了之后才能给我发。生活中的类比：对讲机。

- **全双工**
  数据的传输是双向的，并且可以同时传输。生活中的类比：打电话。

MySQL 使用了半双工的通信方式



# 4.一条SQL的select流程

![lNiT1g.png](https://s2.ax1x.com/2020/01/02/lNiT1g.png)



​		要么是客户端向服务端发送数据，要么是服务端向客户端发送数据，这两个动作不能同时发生。所以客户端发送SQL 语句给服务端的时候，（在一次连接里面）数据是不能分成小块发送的，不管你的SQL 语句有多大，都是一次性发送。

```text
比如我们用MyBatis 动态SQL 生成了一个批量插入的语句，插入10 万条数据，values后面跟了一长串的内容，或者where 条件in 里面的值太多，会出现问题。

这个时候我们必须要调整MySQL 服务器配置max_allowed_packet 参数的值（默认是4M），把它调大，否则就会报错。
另一方面，对于服务端来说，也是一次性发送所有的数据，不能因为你已经取到了想要的数据就中断操作，这个时候会对网络和内存产生大量消耗。
所以，我们一定要在程序里面避免不带limit 的这种操作，比如一次把所有满足条件的数据全部查出来，一定要先count 一下。如果数据量大的话，可以分批查询。
```



### **1）建立连接**

执行一条查询语句，客户端跟服务端建立连接之后呢？下一步要做什么？

### **2）查询缓存**

​	MySQL 内部自带了一个缓存模块。缓存的作用我们应该很清楚了，把数据以KV 的形式放到内存里面，可以加快数据的查询，默认关闭的；而且mysql8.0中缓冲模块也是删除了的

### **3）语法解析和预处理(Parser&Preprocessor)**

对语句基于SQL 语法进行词法和语法分析和语义的解析。

- **词法分析**：词法分析就是把一个完整的SQL 语句打碎成一个个的单词

- **语法分析**

  ​	第二步就是语法分析，语法分析会对SQL 做一些语法检查，比如单引号有没有闭合，然后根据MySQL 定义的语法规则，根据SQL 语句生成一个数据结构。这个数据结构我们把它叫做解析树（select_lex）

- **预处理器**

  ​	它会检查生成的解析树，解决解析器无法解析的语义。比如，它会检查表和列名是否存在，检查名字和别名，保证没有歧义。预处理之后得到一个新的解析树       



### **4）查询优化与执行计划**

​	**查询优化**

​		查询优化器的目的就是根据解析树生成不同的执行计划（Execution Plan），然后选择一种最优的执行计划，MySQL 里面使用的是基于开销（cost）的优化器，那种执行计划开销最小，就用哪种。

-- 官网：https://dev.mysql.com/doc/refman/5.7/en/server-status-variables.html#statvar_Last_query_cost

```sql
-- 如下方式查看开销
show status like 'Last_query_cost';
```

> MySQL 的优化器能处理哪些优化类型呢？
> 举两个简单的例子：
> 	1、当我们对多张表进行关联查询的时候，以哪个表的数据作为基准表。
> 	2、有多个索引可以使用的时候，选择哪个索引。
> 实际上，对于每一种数据库来说，优化器的模块都是必不可少的，他们通过复杂的算法实现尽可能优化查询效率的目标。如果对于优化器的细节感兴趣，可以看看《数据库查询优化器的艺术-原理解析与SQL性能优化》
>
> 注：但是优化器也不是万能的，并不是再垃圾的SQL 语句都能自动优化，也不是每次都能选择到最优的执行计划，大家在编写SQL 语句的时候还是要注意。



如果我们想知道优化器是怎么工作的，它生成了几种执行计划，每种执行计划的cost是多少，应该怎么做？

官网：https://dev.mysql.com/doc/internals/en/optimizer-tracing.html
首先我们要启用优化器的追踪（默认是关闭）

```sql
SHOW VARIABLES LIKE 'optimizer_trace';
set optimizer_trace='enabled=on';
```

> 注意开启这开关是会消耗性能的，因为它要把优化分析的结果写到表里面，所以不要轻易开启，或者查看完之后关闭它（改成off）。
> 注意：参数分为session 和global 级别。
>
> 接着我们执行一个SQL 语句，优化器会生成执行计划：
>
> - ```select t.tcid from teacher t,teacher_contact tc where t.tcid = tc.tcid;```
>
>    这个时候优化器查询上一次分析的过程已经记录到系统表里面了，我们可以查询
>   ```select * from information_schema.optimizer_trace \G```
>
>   1.QUERY：表示我们的查询语句。
>
>   2.TRACE：表示优化过程的JSON格式文本
>
>   3.MISSING_BYTES_BEYOND_MAX_MEM_SIZE：由于优化过程可能会输出很多，如果超过某个限制时，多余的文本将不会被显示，这个字段展示了被忽略的文本字节数。
>
>   4.INSUFFICIENT_PRIVILEGES：表示是否没有权限查看优化过程，默认值是0，只有某些特殊情况下才会是1；
>
> 上面的TRACE它是一个JSON 类型的数据，主要分成三部分，准备阶段、优化阶段和执行阶段
>
> - .单表查询来说，我们主要关注optimize阶段的"rows_estimation"这个过程，这个过程深入分析了对单表查询的各种执行方案的成本
>
> - 对于多表连接查询来说，我们更多需要关注"considered_execution_plans"这个过程，这个过程里会写明各种不同的连接方式所对应的成本
>
> ```sql
> set optimizer_trace="enabled=off";
> SHOW VARIABLES LIKE 'optimizer_trace';
> ```

**查询执行计划**

优化完之后，得到一个什么东西呢？
		优化器最终会把解析树变成一个查询执行计划，查询执行计划是一个数据结构。当然，这个执行计划是不是一定是最优的执行计划呢？不一定，因为MySQL 也有可能覆盖不到所有的执行计划。

MySQL 提供了一个执行计划的工具。我们在SQL 语句前面加上EXPLAIN，就可以看到执行计划的信息。

```sql
EXPLAIN select name from user where id=1;

-- 注意Explain 的结果也不一定最终执行的方式。
```

执行计划结果官网：<https://dev.mysql.com/doc/refman/5.7/en/explain-output.html>



### 5）执行引擎查询存储引擎

​	得到执行计划以后，SQL 语句是不是终于可以执行了？

问题又来了：

- 从逻辑的角度来说，我们的数据是放在哪里的，或者说放在一个什么结构里面？
- 执行计划在哪里执行？是谁去执行？

​	存储引擎是我们存储数据的形式，那么是谁使用执行计划去操作存储引擎呢？这就是我们的执行引擎，它利用存储引擎提供的相应的API 来完成操作。为什么我们修改了表的存储引擎，操作方式不需要做任何改变？因为不同功能的存储引擎实现的API 是相同的。最后把数据返回给客户端，即使没有结果也要返回。

 [存储引擎介绍见](#存储引擎)



# 5.  一条SQL的update流程

> 查看了查询流程，我们是不是再看看更新流程、插入流程和删除流程？
>
> 在数据库里面，我们说的update 操作其实包括了更新、插入和删除。如果大家有看过MyBatis 的源码，应该知道Executor 里面也只有doQuery()和doUpdate()的方法，没有doDelete()和doInsert()。

## 5.1 更新流程和查询流程不同？

​	基本流程也是一致的，也就是说，它也要经过解析器、优化器的处理，最后交给执行器；区别就在于拿到符合条件的数据之后的操作。

​		首先，InnnoDB 的数据都是放在磁盘上的，InnoDB 操作数据有一个最小的逻辑单位，叫做页(默认16k)（索引页和数据页）。我们对于数据的操作，不是每次都直接操作磁盘，因为磁盘的速度太慢了。InnoDB 使用了一种缓冲池的技术，也就是把磁盘读到的页放到一块内存区域里面。这个内存区域就叫Buffer Pool。
下一次读取相同的页，先判断是不是在缓冲池里面，如果是，就直接读取，不用再次访问磁盘。

​	修改数据的时候，先修改缓冲池里面的页。内存的数据页和磁盘数据不一致的时候，我们把它叫做脏页。InnoDB 里面有专门的后台线程把Buffer Pool 的数据写入到磁盘，每隔一段时间就一次性地把多个修改写入磁盘，这个动作就叫做刷脏。

## 5.2 更新流程

比如，执行如下sql的流程：

```sql
update user set name = 'vison' where id=1;
```

1、事务开始，从内存或磁盘取到这条数据，返回给Server 的执行器；
2、执行器修改这一行数据的值为vison；
3、记录name=ws到undo log；
4、记录name=vison到redo log；
5、调用存储引擎接口，在内存（Buffer Pool）中修改name=vison；
6、事务提交。



详细的信息存储如下：

# 6.  InnoDB内存结构和磁盘结构

![lUZcsU.png](https://s2.ax1x.com/2020/01/03/lUZcsU.png)

## 6.1 内存结构

​	Buffer Pool 主要分为3 个部分： Buffer Pool、Change Buffer、Adaptive Hash Index，另外还有一个（redo）log buffer。

官网：<https://dev.mysql.com/doc/refman/5.7/en/innodb-in-memory-structures.html>

#### 1） Buffer Pool

​		Buffer Pool 缓存的是页面信息，包括数据页、索引页。

查看服务器状态，里面有很多跟Buffer Pool 相关的信息：

```sql
SHOW STATUS LIKE '%innodb_buffer_pool%';
```

Buffer Pool 默认大小是128M（134217728 字节），可以调整。



> 问题：内存的缓冲池写满了怎么办？（Redis 设置的内存满了怎么办？）

> ​	InnoDB 用LRU算法来管理缓冲池（链表实现，不是传统的LRU，分成了young 和old），经过淘汰的
> 数据就是热点数据。内存缓冲区对于提升读写性能有很大的作用。
>
> 思考一个问题：
> 当需要更新一个数据页时，如果数据页在Buffer Pool 中存在，那么就直接更新好了。否则的话就需要从磁盘加载到内存，再对内存的数据页进行操作。也就是说，如果没有命中缓冲池，至少要产生一次磁盘IO，有没有优化的方式呢？



#### 2）Change Buffer 写缓冲

​		如果这个数据页不是唯一索引，不存在数据重复的情况，也就不需要从磁盘加载索引页判断数据是不是重复（唯一性检查）。这种情况下可以先把修改记录在内存的缓冲池中，从而提升更新语句（Insert、Delete、Update）的执行速度，这一块区域就是Change Buffer。

> 5.5 版本之前叫Insert Buffer 插入缓冲，现在也能支持delete 和update。
>
> 最后把Change Buffer 记录到数据页的操作叫做merge。什么时候发生merge？
> 		有几种情况：在访问这个数据页的时候，或者通过后台线程、或者数据库shut down、redo log 写满时触发。
>
> 如果数据库大部分索引都是非唯一索引，并且业务是写多读少，不会在写数据后立刻读取，就可以使用Change Buffer（写缓冲）。写多读少的业务，调大这个值：
>
> ```sql
> SHOW VARIABLES LIKE 'innodb_change_buffer_max_size';
> 
> --值为25 代表Change Buffer 占Buffer Pool 的比例，默认25%。
> ```

#### 3） Adaptive Hash Index

​		索引应该是放在磁盘的，为什么要专门把一种哈希的索引放到内存？

这个是为热点数据做hash-index的，不用全部走三次IO才获取到数据，直接一次性IO即可拿到数据

#### 4）(redo) Log Buffer

​	如果Buffer Pool 里面的脏页还没有刷入磁盘时，数据库宕机或者重启，这些数据丢失。如果写操作写到一半，甚至可能会破坏数据文件导致数据库不可用。
为了避免这个问题，InnoDB 把所有对页面的修改操作专门写入一个日志文件，并且在数据库启动时从这个文件进行恢复操作（实现crash-safe）——用它来实现事务的持久性。

##### i. redo log日志文件

​		这个文件就是磁盘的redo log（叫做重做日志），对应于`/var/lib/mysql/目录下的ib_logfile0 和ib_logfile1`，每个48M。
​		这种日志和磁盘配合的整个过程， 其实就是MySQL 里的WAL 技术（Write-Ahead Logging），它的关键点就是先写日志，再写磁盘。

- redo log 是InnoDB 存储引擎实现的，并不是所有存储引擎都有。
- 不是记录数据页更新之后的状态，而是记录这个页做了什么改动，属于物理日志
- redo log 的大小是固定的，前面的内容会被覆盖，满了会触发刷盘的；
  

##### ii. 相关参数设置

```sql
show variables like 'innodb_log%';

-- 值含义:
-- 	innodb_log_file_size 指定每个文件的大小，默认48M
-- 	innodb_log_files_in_group 指定文件的数量，默认为2
-- 	innodb_log_group_home_dir 指定文件所在路径，相对或绝对。如果不指定，则为datadir 路径。
```



> 问题：同样是写磁盘，为什么不直接写到db file 里面去？为什么先写日志再写磁盘？
>
> 回答：随机IO和顺序IO的区别；写db file属于随机IO效率低；日志是顺序IO效率高
>
> 我们先来了解一下随机I/O 和顺序I/O 的概念。
> 磁盘的最小组成单元是扇区，通常是512 个字节。
> 操作系统和内存打交道，最小的单位是页Page。
> 操作系统和磁盘打交道，读写磁盘，最小的单位是块Block。
>
> - 如果我们所需要的数据是随机分散在不同页的不同扇区中，那么找到相应的数据需要等到磁臂旋转到指定的页，然后盘片寻找到对应的扇区，才能找到我们所需要的一块数据，一次进行此过程直到找完所有数据，这个就是随机IO，读取数据速度较慢。
> - 假设我们已经找到了第一块数据，并且其他所需的数据就在这一块数据后边，那么就不需要重新寻址，可以依次拿到我们所需的数据，这个就叫顺序IO
>
> 刷盘是随机I/O，而记录日志是顺序I/O，顺序I/O 效率更高。因此先把修改写入日志，可以延迟刷盘时机，进而提升系统吞吐。



##### iii. redo log buffer

​		当然redo log 也不是每一次都直接写入磁盘，在Buffer Pool 里面有一块内存区域（Log Buffer）专门用来保存即将要写入日志文件的数据，默认16M，它一样可以节省磁盘IO。

```sql
SHOW VARIABLES LIKE 'innodb_log_buffer_size';
```



##### Iv.刷盘 

​		需要注意：redo log 的内容主要是用于崩溃恢复。磁盘的数据文件，数据来自buffer pool。redo log 写入磁盘，不是写入数据文件。
那么，Log Buffer 什么时候写入log file？
在我们写入数据到磁盘的时候，操作系统本身是有缓存的。flush 就是把操作系统缓冲区写入到磁盘。

log buffer 写入磁盘的时机，由一个参数控制，默认是1。

```sql
SHOW VARIABLES LIKE 'innodb_flush_log_at_trx_commit';

-- 值：0（延迟写）
	含义：log buffer 将每秒一次地写入log file 中，并且log file 的flush 操作同时进行。该模式下，在事务提交的时候，不会主动触发写入磁盘的操作。可能都是1s的数据
-- 值：1（默认，实时写，实时刷）
	含义：每次事务提交时MySQL 都会把log buffer 的数据写入log file，并且刷到磁盘中去。效率最低，安全
-- 值：2（实时写，延迟刷）
	含义：每次事务提交时MySQL 都会把log buffer 的数据写入log file。但是flush 操作并不会同时进行。该模式下，MySQL 会每秒执行一次flush 操作。
	
-- 参考：https://dev.mysql.com/doc/refman/5.7/en/innodb-parameters.html#sysvar_innodb_flush_log_at_trx_commit
```

## 6.2 磁盘结构

​	表空间可以看做是InnoDB 存储引擎逻辑结构的最高层，所有的数据都存放在表空间中。InnoDB 的表空间分为5 大类。

#### 1）系统表空间

在默认情况下InnoDB 存储引擎有一个共享表空间（对应文件/var/lib/mysql/ibdata1），也叫系统表空间。
InnoDB 系统表空间包含`InnoDB 数据字典和双写缓冲区，Change Buffer 和Undo Logs）`，如果没有指定file-per-table，也包含用户创建的表和索引数据。在默认情况下，所有的表共享一个系统表空间，这个文件会越来越大，而且它的空间不会收缩。

- 1、undo 在后面介绍，因为有独立的表空间。
- 2、数据字典：由内部系统表组成，存储表和索引的元数据（定义信息）。
- 3、双写缓冲（InnoDB 的一大特性）

##### i.双写缓冲

​		InnoDB 的页和操作系统的页大小不一致，InnoDB 页大小一般为16K，操作系统页大小为4K，InnoDB 的页写入到磁盘时，一个页需要分4 次写。



![lUTi79.png](https://s2.ax1x.com/2020/01/03/lUTi79.png)



​	如果存储引擎正在写入页的数据到磁盘时发生了宕机，可能出现页只写了一部分的情况，比如只写了4K，就宕机了，这种情况叫做部分写失效（partial page write），可能会导致数据丢失。

```sql
show variables like 'innodb_doublewrite';
```

​		我们不是有redo log 吗？但是有个问题，如果这个页本身已经损坏了，用它来做崩溃恢复是没有意义的。所以在对于应用redo log 之前，需要一个页的副本。如果出现了写入失效，就用页的副本来还原这个页，然后再应用redo log。这个页的副本就是doublewrite，InnoDB 的双写技术。通过它实现了数据页的可靠性。

​	跟redo log 一样，double write 由两部分组成，一部分是内存的double write，一个部分是磁盘上的double write。因为double write 是顺序写入的，不会带来很大的开销。

#### 2） 独占表空间file-per-table tablespaces

​	我们可以让每张表独占一个表空间。这个开关通过innodb_file_per_table 设置，默认开启。

```sql
SHOW VARIABLES LIKE 'innodb_file_per_table';
```

​		开启后，则每张表会开辟一个表空间，这个文件就是数据目录下的ibd 文件（例如:/var/lib/mysql/gupao/user_innodb.ibd），存放表的索引和数据。

​		但是其他类的数据，如回滚（undo）信息，插入缓冲索引页、系统事务信息，二次写缓冲（Double write buffer）等还是存放在原来的共享表空间内。



#### 3) 通用表空间general tablespaces

通用表空间也是一种共享的表空间，跟ibdata1 类似。可以创建一个通用的表空间，用来存储不同数据库的表，数据路径和文件可以自定义。语法

```sql
create tablespace ts2673 add datafile '/var/lib/mysql/ts2673.ibd' file_block_size=16K engine=innodb;

-- 在创建表的时候可以指定表空间，用ALTER 修改表空间可以转移表空间。
create table t2673(id integer) tablespace ts2673;
-- 不同表空间的数据是可以移动的。
-- 删除表空间需要先删除里面的所有表：
drop table t2673;
drop tablespace ts2673;
```

#### 4) 临时表空间temporary tablespaces

​		存储临时表的数据，包括用户创建的临时表，和磁盘的内部临时表。对应数据目录下的ibtmp1 文件。当数据服务器正常关闭时，该表空间被删除，下次重新产生。



#### 5） Redo  Log

​	参考内存结构Redo log buffer的介绍

#### 6） undo log tablespace

官网：https://dev.mysql.com/doc/refman/5.7/en/innodb-undo-tablespaces.html

​	https://dev.mysql.com/doc/refman/5.7/en/innodb-undo-logs.html

- undo log（撤销日志或回滚日志）记录了事务发生之前的数据状态（不包括select）；
  如果修改数据时出现异常，可以用undo log 来实现回滚操作（保持原子性）。

- 在执行undo 的时候，仅仅是将数据从逻辑上恢复至事务之前的状态，而不是从物理页面上操作实现的，属于逻辑格式的日志。
- redo Log 和undo Log 与事务密切相关，统称为事务日志。
  undo Log 的数据默认在系统表空间ibdata1 文件中，因为共享表空间不会自动收缩，也可以单独创建一个undo 表空间。





## 6.3 后台线程

​		后台线程的主要作用是负责刷新内存池中的数据和把修改的数据页刷新到磁盘。后台线程分为：master thread，IO thread，purge thread，page cleaner thread。

- master thread 负责刷新缓存数据到磁盘并协调调度其它后台进程。
- IO thread 分为insert buffer、log、read、write 进程。分别用来处理insert buffer、重做日志、读写请求的IO 回调。
- purge thread 用来回收undo 页。

- page cleaner thread 用来刷新脏页。

  

除了InnoDB 架构中的日志文件，MySQL 的Server 层也有一个日志文件，叫做binlog，它可以被所有的存储引擎使用。



# 7.Server层的日志Binlog

官网：https://dev.mysql.com/doc/refman/5.7/en/binary-log.html

​		binlog 以事件的形式记录了所有的DDL 和DML 语句（因为它记录的是操作而不是数据值，属于逻辑日志），可以用来做主从复制和数据恢复。

​		跟redo log 不一样，它的文件内容是可以追加的，没有固定大小限制。在开启了binlog 功能的情况下，我们可以把binlog 导出成SQL 语句，把所有的操作重放一遍，来实现数据的恢复。
binlog 的另一个功能就是用来实现主从复制，它的原理就是从服务器读取主服务器的binlog，然后执行一遍。

有了这两个日志之后，我们来看一下一条更新语句是怎么执行的：

```sql
-- 例如一条语句：
update teacher set name='彭于晏' where id=1;
```

流程：

1、先查询到这条数据，如果有缓存，也会用到缓存。
2、把name 改成彭于晏，然后调用引擎的API 接口，写入这一行数据到内存，同时记录redo log。这时redo log 进入prepare 状态，然后告诉执行器，执行完成了，可以随时提交。
3、执行器收到通知后记录binlog，然后调用存储引擎接口，设置redo log 为commit状态。
4、更新完成。

![lUby9I.png](https://s2.ax1x.com/2020/01/03/lUby9I.png)

**重点注**：

1、先记录到内存，再写日志文件。

2、记录redo log 分为两个阶段。
3、存储引擎和Server 记录不同的日志。
3、先记录redo，再记录binlog。

（两阶段提交，避免数据不一致）