## 1. 事务

### **1.1 事务特性**

​     ACID--原子性，一致性，隔离性，持久性

| 事务特性              | 解释                                                         |
| --------------------- | ------------------------------------------------------------ |
| 原子性（Atomicity）   | 最小的工作单元，整个工作单元要么一起提交成功，要么全部失败回滚 |
| 一致性（Consistency） | 事务中操作的数据及状态改变是一致的，即写入资料的结果必须完全符合预设的规则， 不会因为出现系统意外等原因导致状态的不一致，和原子性区别：原子性保证操作是原子的，一致性保证数据是对的，不会出现两次操作都执行了，结果数据不一致这种情况。 |
| 隔离性（Isolation）   | 一个事务所操作的数据在提交之前，对其他事务的可见性设定（一般设定为不可见） |
| 持久性（Durability）  | 事务所做的修改就会永久保存，不会因为系统意外导致数据的丢失   |

### **1.2 事务下中 并发带来的问题**

| 更新丢失   | 两个事务对同一条数据修改，后者事务覆盖前者                   | ---更新            |
| ---------- | ------------------------------------------------------------ | ------------------ |
| 脏读       | 一个事务对一条数据正在修改并未提交，另外一个事务读取到了这个未提交的数据 | 读取数据一致性问题 |
| 不可重复读 | 一个事务在读取某些数据后，再次重复读取数据，发现数据变了，其他事务对某些数据做了更新 |                    |
| 幻读/虚读  | 一个事务在重新读取数据时，发现其他事务插入了数据，数据变多了 |                    |

### **1.3 事务隔离级别**

​      其实解决上面并发带来的问题，可以在事务读取数据前对数据进行加锁，但是这样会降低效率，**加锁其实就是变并发为串行**，事务的隔离级别越严格，副作用越小，但是付出的代价也是比较大的，所以根据不同应用选择不同的隔离级别，例如某些应用对于不可重复读和幻读并不是很敏感，可能更关心并发的能力。

| 读数据和允许的副作用        | 读数据一致性                             | 脏读 | 不可重复读 | 幻读                           |
| --------------------------- | ---------------------------------------- | ---- | ---------- | ------------------------------ |
| 隔离级别                    |                                          |      |            |                                |
| 未提交读(Read uncommitted)  | 最低级别，只能保证不读取物理上损坏的数据 | 是   | 是         | 是                             |
| 已提交读（Read committed）  | 语句级                                   | 否   | 是         | 是                             |
| 可重复读（Repeatable read） | 事务级                                   | 否   | 否         | 是（innodb引擎是解决了幻读的） |
| 可序列化（Serializable）    | 最高级别，事务级                         | 否   | 否         | 否                             |

注：Oracle只提供Read commited 和Serializable 和自定义的Read only 级别，Mysql支持四种。



### 1.4 XA事务

​		参考分布式事务-JTA



## 2. InnoDB 锁

​    默认InnoDB隔离级别是RR（repeatable read）由于innodb锁的特性，是解决了幻读的问题的。



### **2.1 行锁（共享锁和排他锁）**

​      行锁分为共享锁和排他锁。InnoDB的行锁是通过给索引上的*索引项加锁*来实现的。只有通过索引条件进行数据检索，InnoDB才使用行级锁，否则**InnoDB将使用表锁**（锁住索引的所有记录）。

**2.1.1 共享锁（Share Lock）**

​     共享锁又称**读锁**，是读取操作创建的锁。其他用户可以并发读取数据，但任何事务都不能对数据进行修改（获取数据上的排他锁），直到已释放所有共享锁。如果事务T对数据A加上共享锁后，则其他事务只能对A再加共享锁，不能加排他锁。获得共享锁的事务只能读数据，不能修改数据**。**

​    **用途：**主要是用来确认记录是否存在，并确保没有人对这个记录进行update,delete操作，如果当前事务需要对该记录进行修改那么请用select ...for update获得排他锁，否则容易造成死锁。

​           **语法为**： *select \* from table **lock in share mode***

**2.1.2 排他锁（eXclusive lock）**   

​      又称为写锁，简称X锁，排他锁不能与其他锁并存，如一个事务获取了一个数据行的排他锁，其他事务就不能再获取该行的锁（共享锁、排他锁），只有该获取了排他锁的事务是可以对数据行进行读取和修改，（其他事务要读取数据可来自于快照）

​           **语法为**： update，delete,insert 自动加了排他锁

​                          *select \* from table **for update*** 

 另外：为了允许行锁和表锁的共同存在，实现多粒度机制，InnoDB还有两种内部使用的意向锁



### 2.2 意向锁-表锁级别

​      意向锁是属于表锁级别的的。InnoDB自动加的，不需用户干预

| 意向共享锁(IS) | 事务打算给数据行加行共享锁，必须先取得该表的IS锁，意向共享锁之间是可以相互兼容的 |
| -------------- | ------------------------------------------------------------ |
| 意向排他锁(IX) | 事务打算给数据行加行排他锁，必须先取得该表的IX锁，意向排它锁之间是可以相互兼容的 |

**意义：**当事务想去进行锁表时，可以先判断意向锁是否存在，存在时则可快速返回该表不能启用表锁。说白了就是这张表如果存在已经被行锁锁住了，就不能加表锁了。



### 2.3 表锁

​      InnoDB的行锁是通过给索引上的*索引项加锁*来实现的。只有通过索引条件进行数据检索，InnoDB才使用行级锁，否则**InnoDB将使用表锁**（锁住索引的所有记录）。

​    也可以通过命令 这样加锁   lock tables xx read/write；（和myisam设置表锁一样）



### 2.4 自增锁

​       针对自增列自增长的一个特殊的表级别锁，当当前的id自增后，但是在事务回滚后，这个id已经被使用过了，就会在表中出现id中间消失的状态。
​        可以通过 命令查询  show variables like 'innodb_autoinc_lock_mode';
​        默认取值1 ，代表连续，事务未提交ID



## 3.  InnoDB锁的实现方式

​    InnoDB是通过给索引上的索引项加锁实现的，如果没有索引，InnoDB将通过隐藏的聚簇索引来对记录加锁；如果查询没有使用到索引都是所全表的。

| 锁类型               | 说明                                                         |
| -------------------- | ------------------------------------------------------------ |
| Record Lock 记录锁   | 对索引项加锁                                                 |
| Gap Lock 间隙锁      | 对索引项之间的“间隙”、第一条记录前的间隙或者最后一条记录后的间隙加锁 |
| Next-key Lock 临键锁 | 前两种的组合，对记录及其前面的间隙加锁   锁住记录+区间（左开右闭） |



### 3.1 Next-key Lock 临键锁，InooDB行锁默认算法

​          当sql执行按照索引进行数据的检索时,查询条件为范围查找（between and、<、>等）并有数据命中则此时SQL语句加上的锁为Next-key locks， 锁住索引的记录+区间。否则就是表锁哦。

临键锁也是解决了RR（Repeatable read）可重复读幻读的问题，原因是当前事务在这个区间做了查询就把当前区间给锁住了，其他事务就不能插入数据，也就不会出现幻读的情况了。

![img](https://img-blog.csdnimg.cn/20181210235748258.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### 3.2 Gap Lock 间隙锁

​         当查询的记录不存在，临键锁就会退化为间隙锁。

![img](https://img-blog.csdnimg.cn/20181211000527452.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### 3.3 Record Lock 记录锁

​         当条件为精确匹配时就会退化为记录锁

![img](https://img-blog.csdnimg.cn/20181211000729769.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)



**总结上面的并发问题：**

 更新丢失：通过对当前修改线程加排他锁，其他线程就无法修改当前记录；

  脏读：通过对修改记录的事务线程 加排他锁实现，当前线程加了排他锁，其他线程就不能查询当前的记录；

 不可重复读：多次查询的事务线程，对这个线程加上共享锁，这样其他线程想修改这条记录是无法修改的；

 幻读：通过加上Next-key锁，对于多次查询的线程有了临检索就不会让新的数据插入到这个区间来。

## 4.阻塞

`innodb_lock_wait_timeout`属于动态属性，表示锁的超时时间，默认是50秒

`innodb_rollback_on_timeout`属于静态属性，只能配置。

**如果使用MySQL 5.6：**

- `innodb_rollback_on_timeout=off`的情况下，会回滚最后的造成锁等待的语句，事务没有自动结束.但是这样会造成**数据的不一致，破坏了事务的原子性**。

- `innodb_rollback_on_timeout=on`的情况下，整个事务回滚后会自动创建一个事务。

**如果使用MySQL 5.7：**

- `innodb_rollback_on_timeout=off`的情况下和5.6版本是一样的。

- `innodb_rollback_on_timeout=on`的情况下，整个事务已经自动回滚，不会再自动创建事务。



​		所以不管是5.6的版本还是5.7的版本，innodb_rollback_on_timeout最好设置成ON，这样可以避免破坏事务原子性，保证数据一致性。唯一的区别是在5.7版本下需要自己手动开启一个事务



## 5. 死锁

​       多个并发事务（2个或者以上）；
​       每个事务都持有锁（或者是已经在等待锁）;
​       每个事务都需要再继续持有锁；
​       事务之间产生加锁的循环等待，形成死锁。

   **避免死锁：**

​            1）类似的业务逻辑以固定的顺序访问表和行。  （）重点考虑）
​            2）大事务拆小。大事务更倾向于死锁，如果业务允许，将大事务拆小。
​            3）在同一个事务中，尽可能做到一次锁定所需要的所有资源，减少死锁概率。
​            4）降低隔离级别，如果业务允许，将隔离级别调低也是较好的选择
​            5）为表添加合理的索引。可以看到如果不走索引将会为表的每一行记录添加上锁（或者说是表锁，也是重点考虑）

**发生死锁：**

​	一旦发生死锁，Innodb存储引擎会马上回滚但钱死锁的这个事务

## 6. MVCC 多版本并发控制

> **MVCC解释：**并发访问(读或写)数据库时，对正在事务内处理的数据做多版本的管理。以达到用来避免写操作的堵塞，从而引发读操作的并发问题。（这个是没有加锁的读-快照读。具体见后面）。
>
> InnoDB存储引擎在每行记录上存有三个默认字段： **DB_TRX_ID、DB_ROLL_PTR、*****DB_ROW_ID；***
>
> -  **DB_TRX_ID：这个指的是插入的版本号**
> - **DB_ROLL_PTR：这个指的是删除的版本号**
> - ***DB_ROW_ID：这个指的是行自增的id***
>
> 这里和MVCC相关就前两个。

**问题：**

​           例子一：在两个事务中，一个事务在修改数据（用了X锁锁住数据），但是另外一个事务仍然能够获取同一数据。但是数据仍然是之前的数据。

​           例子二：两个事务中，事务一先读取数据，然后事务二更新同一个数据，事务一再次读取数据仍然获取的是原数据。

​          ![img](https://img-blog.csdnimg.cn/20181211231234413.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### 6.1 MVCC的插入流程

​         每次插入数据的时候会给 DB_TRX_ID 添加一个版本号（可以看成一个事务的ID），如图：

![img](https://img-blog.csdnimg.cn/20181211233120415.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### 6.2 MVCC 的删除流程

​       每次插入数据的时候会给 DB_ROLL_PTR 添加一个版本号（可以看成一个事务的ID），如图：

![img](https://img-blog.csdnimg.cn/20181211233041969.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### 6.3 MVCC 的更新流程

​      修改操作是先做命中数据行的copy,将原行数据的删除版本号设置为当前事务ID,如图：

![img](https://img-blog.csdnimg.cn/20181211233510238.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### 6.4 MVCC 的查询流程

规则： 1）查询操作是查找数据行DB_TRX_ID版本早于当前事务版本的数据行；

​            2）查找删除版本号DB_ROLL_PT要么是null，要么大于当前事务版本号的记录。以免后来的事务删除了不能获取数据

![img](https://img-blog.csdnimg.cn/20181211233605137.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)



### 6.5 回溯问题解决，更新在查询仍然有问题

> ​          1）方案一：按照1,2,3,4,5执行如下两个事务重复查询，可以成功查询出来结果，
>
> ​              insert into teacher(name,age) value ('seven',18) ;
> ​              insert into teacher(name,age) value ('qing',20) ;
>
> ​              tx1:
> ​                     begin;  ----------1
> ​                           select * from users ; ----------2
>
> ​                           select * from users ; ----------5
> ​                     commit;
>
> ​              tx2:
>
> ​                     begin; ----------3
> ​                             update teacher set age =28 where id =1; ----------4
> ​                    commit;

![img](https://img-blog.csdnimg.cn/2018121123463357.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

> 2）方案二：按照1,2,3,4执行如下两个事务先更新和查询，感觉似乎不对。
>
> ​              insert into teacher(name,age) value ('seven',18) ;
> ​              insert into teacher(name,age) value ('qing',20) ;
>
> ​              tx1:
>
> ​                       begin; ----------1
> ​                             update teacher set age =28 where id =1; ----------2
> ​                    commit;
>
> ​              tx2:
> ​                     begin;  ----------3
> ​                           select * from users ; ----------4
> ​                     commit;

![img](https://img-blog.csdnimg.cn/20181211235137293.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

## 7.Undo Log

​        Undo Log数据存储在共享表空间中。 

Undo Log：
               undo 意为取消，以撤销操作为目的，返回指定某个状态的操作
               undo log 指事务开始之前， 在操作任何数据之前, 首先将 需操作的 数据备份到一个地方 (Undo Log)

**UndoLog 是为了实现事务的原子性而出现的产物**
Undo Log 实现事务 原子性 ：
         事务处理过程中 如果出现了错误或者用户执行了 ROLLBACK 语句,Mysql 可以利用Undo Log 中的备份将数据恢复到事务开始之前的状态。
**UndoLog 在Mysql innodb 存储引擎中用来实现多版本并发控制**
Undo log 实现多版本并发控制：
       事务未提交之前，Undo 保存了未提交之前的版本数据，Undo中的数据可作为数据旧版本快照供其他并发事务进行快照读

![img](https://img-blog.csdnimg.cn/20181211235524896.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

## 8.查询结论

###  8.1 快照读：

​          SQL 读取的 数据 是快照版本，也就是历史版本 ， 普通的SELECT 就是快照读；innodb 快照读，数据的读取将由 cache( 原本数据) + undo( 事务修改前的数据)  两部分组成（即前面的MVCC和undo log组成）

###  8.2 当前读：

​          SQL 读取的 数据 是最新版本 。通过锁机制来保证读取的数据无法通过其他事务进行修改；UPDATE 、DELETE 、INSERT 、SELECT … LOCK IN SHARE MODE 、SELECT … FOR UPDATE 都是当前读。



## 9. Redo log 

**Redo Log  概念：**
   1)  Redo ，顾名思义就 是 重做。以恢复操作为目的，重现操作 ；
   2)  Redo log 指事务中 操作 的 任何数据,将最新的 数据备份到一个地方 (Redo Log)


Redo log 的持久：
    不是随着事务的提交才写入的，而是在事务的执行过程中，便开始写入redo  中。具体的落盘策略可以进行配置
**RedoLog 是为了实现事务的持久性而出现的产物**


Redo Log 实现事务 持久性 ：
        防止在发生故障的时间点，尚有脏页未写入磁盘，在重启mysql 服务的时候，根据redo log 进行重做，从而达到事务的 未入磁盘数据进行持久化这一特性。



如图：

![img](https://img-blog.csdnimg.cn/20181212080658259.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

**Redo Log  配置：**

![img](https://img-blog.csdnimg.cn/2018121208084577.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

取值1的时候，是每次事务提交都把数据提交到redo buffer中，然后在把数据刷到系统的OS cache缓存总。最后在刷新到硬盘上。这个最安全。

注：OS cache指的是系统级别的。