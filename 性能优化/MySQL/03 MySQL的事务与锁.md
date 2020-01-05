# 1.事务

## 1.1 事务特性

​     ACID--原子性，一致性，隔离性，持久性

| 事务特性              | 解释                                                         |
| --------------------- | ------------------------------------------------------------ |
| 原子性（Atomicity）   | 最小的工作单元，整个工作单元要么一起提交成功，要么全部失败回滚 |
| 一致性（Consistency） | 事务中操作的数据及状态改变是一致的，即写入资料的结果必须完全符合预设的规则， 不会因为出现系统意外等原因导致状态的不一致，和原子性区别：原子性保证操作是原子的，一致性保证数据是对的，不会出现两次操作都执行了，结果数据不一致这种情况。 |
| 隔离性（Isolation）   | 一个事务所操作的数据在提交之前，对其他事务的可见性设定（一般设定为不可见） |
| 持久性（Durability）  | 事务所做的修改就会永久保存，不会因为系统意外导致数据的丢失   |

## 1.2 事务下中并发带来的问题

| 更新丢失   | 两个事务对同一条数据修改，后者事务覆盖前者                   | ---更新            |
| ---------- | ------------------------------------------------------------ | ------------------ |
| 脏读       | 一个事务对一条数据正在修改并未提交，另外一个事务读取到了这个未提交的数据 | 读取数据一致性问题 |
| 不可重复读 | 一个事务在读取某些数据后，再次重复读取数据，发现数据变了，其他事务对某些数据做了更新 |                    |
| 幻读/虚读  | 一个事务在重新读取数据时，发现其他事务插入了数据，数据变多了 |                    |

## 1.3 事务隔离级别

​      其实解决上面并发带来的问题，可以在事务读取数据前对数据进行加锁，但是这样会降低效率，**加锁其实就是变并发为串行**，事务的隔离级别越严格，副作用越小，但是付出的代价也是比较大的，所以根据不同应用选择不同的隔离级别，例如某些应用对于不可重复读和幻读并不是很敏感，可能更关心并发的能力。

| 读数据和允许的副作用        | 读数据一致性                             | 脏读 | 不可重复读 | 幻读                           |
| --------------------------- | ---------------------------------------- | ---- | ---------- | ------------------------------ |
| 隔离级别                    |                                          |      |            |                                |
| 未提交读(Read uncommitted)  | 最低级别，只能保证不读取物理上损坏的数据 | 是   | 是         | 是                             |
| 已提交读（Read committed）  | 语句级                                   | 否   | 是         | 是                             |
| 可重复读（Repeatable read） | 事务级                                   | 否   | 否         | 是（innodb引擎是解决了幻读的） |
| 可序列化（Serializable）    | 最高级别，事务级                         | 否   | 否         | 否                             |

注：Oracle只提供Read commited 和Serializable 和自定义的Read only 级别，Mysql支持四种。



# 2.解决读一致性问题

​		如果要解决读一致性的问题，保证一个事务中前后两次读取数据结果一致，实现事务隔离，应该怎么做？我们有哪一些方法呢？你的思路是什么样的呢？总体上来说，我们有两大类的方案。

## 2.1 基于锁的并发控制LBCC

   后面锁的实现也是为了达到这个任务；

​		第一种，我既然要保证前后两次读取数据一致，那么我读取数据的时候，锁定我要操作的数据，不允许其他的事务修改就行了。这种方案我们叫做基于锁的并发控制Lock Based Concurrency Control（LBCC）。
​	如果仅仅是基于锁来实现事务隔离，一个事务读取的时候不允许其他时候修改，那就意味着不支持并发的读写操作，而我们的大多数应用都是读多写少的，这样会极大地影响操作数据的效率。



## 2.2多版本控制MVCC

所以我们还有另一种解决方案，如果要让一个事务前后两次读取的数据保持一致，

​		那么我们可以在修改数据的时候给它建立一个备份或者叫快照，后面再来读取这个快照就行了。这种方案我们叫做多版本的并发控制Multi Version Concurrency Control（MVCC）。
​		MVCC 的核心思想是： 我可以查到在我这个事务开始之前已经存在的数据，即使它在后面被修改或者删除了。在我这个事务之后新增的数据，我是查不到的。



> **MVCC解释：**并发访问(读或写)数据库时，对正在事务内处理的数据做多版本的管理。以达到用来避免写操作的堵塞，从而引发读操作的并发问题。（这个是没有加锁的读-快照读。具体见后面）。
>
> InnoDB存储引擎在每行记录上存有三个默认字段： **DB_TRX_ID、DB_ROLL_PTR、*****DB_ROW_ID；***
>
> - **DB_TRX_ID：这个指的是插入的版本号**
> - **DB_ROLL_PTR：这个指的是删除的版本号**
> - ***DB_ROW_ID：这个指的是行自增的id***
>
> 这里和MVCC相关就前两个。

**问题：**

​           例子一：在两个事务中，一个事务在修改数据（用了X锁锁住数据），但是另外一个事务仍然能够获取同一数据。但是数据仍然是之前的数据。

​           例子二：两个事务中，事务一先读取数据，然后事务二更新同一个数据，事务一再次读取数据仍然获取的是原数据。

​          ![img](https://img-blog.csdnimg.cn/20181211231234413.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### 2.2.1 MVCC的插入流程

​         每次插入数据的时候会给 DB_TRX_ID 添加一个版本号（可以看成一个事务的ID），如图：

![img](https://img-blog.csdnimg.cn/20181211233120415.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### 2.2.2 MVCC 的删除流程

​       每次插入数据的时候会给 DB_ROLL_PTR 添加一个版本号（可以看成一个事务的ID），如图：

![img](https://img-blog.csdnimg.cn/20181211233041969.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### 2.2.3 MVCC 的更新流程

​      修改操作是先做命中数据行的copy,将原行数据的删除版本号设置为当前事务ID,如图：

![img](https://img-blog.csdnimg.cn/20181211233510238.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### 2.2.4 MVCC 的查询流程

规则： 1）查询操作是查找数据行DB_TRX_ID版本早于当前事务版本的数据行；

​            2）查找删除版本号DB_ROLL_PT要么是null，要么大于当前事务版本号的记录。以免后来的事务删除了不能获取数据

![img](https://img-blog.csdnimg.cn/20181211233605137.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)



### 2.2.5 更新在查询仍然有问题

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

### 2.2.6  Undo Log

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



## 2.3.查询方式

### 2.3.1 快照读

​          SQL 读取的 数据 是快照版本，也就是历史版本 ， 普通的SELECT 就是快照读；innodb 快照读，数据的读取将由 cache( 原本数据) + undo( 事务修改前的数据)  两部分组成（即前面的MVCC和undo log组成）

### 2.3.2 当前读

​          SQL 读取的 数据 是最新版本 。通过锁机制来保证读取的数据无法通过其他事务进行修改；UPDATE 、DELETE 、INSERT 、SELECT … LOCK IN SHARE MODE 、SELECT … FOR UPDATE 都是当前读。



# 3.MyISAM引擎的锁

两种引擎的锁介绍

- MyISAM的锁	MyISAM存储引擎采用的是表级锁，
- InnoDB的锁	InnoDB既支持表级锁，有支持行锁，默认情况下是行锁；InnoDB还支持事务特性

## 3.1.具体锁的特性

- 表级锁	开销小，加锁快，不会出现死锁，锁定粒度大，发生锁的冲突概率高，开发度最低，并发度低

- 行级锁	开销大，加锁慢，会出现死锁，锁定粒度小，发生锁的冲突概率低，开发度高，并发度一般

  

  ​    总结：两种锁各有优缺点。表级锁更适合用于查询为主，只有少量按索引更新数据的应用，例如Web应用。而行级锁则更适合有大量按照索引条件并发更新少量不同数据，同时又有并发处理查询的引用。如一些在线事务处理的系统。

## 3.2.MyISAM表锁介绍
### 1） 锁分类

MyISAM的表级锁有两种模式：表共享读锁、表独占锁，他们的兼容性如下表格所示

​	当前锁模式/请求模式	none	读锁	写锁

- 读锁（当前模式）	兼容	兼容	不兼容

- 写锁（当前模式）	兼容	不兼容	不兼容

  

  从上可以看出，MyISAM的表读操作不会阻塞其他用户对同一表的读操作，但是会阻塞对同一表的写操作；而写操作则会阻塞其他用户对同一表的读和写操作。写操作和读操作之间，以及写操作之间是串行的。

### 2） 如何加表锁

     1. 默认情况下MyISAM在执行Select语句(查询)时，自动会加表读锁；而在执行update,delete,insert语句时，会自动给涉及到的表加上写锁，这个过程用户完全不用做任何干预。
    
    2. 显示加表锁。例如在执行sql语句前 lock table user write (给user表加表写锁)，用处一般是为了模拟事务存在，例如有一个订单表，还有一个订单明细表，为了检查这两个表的金额是否相符，那么就需要执行如下

```sql
select sum(total) from orders;
select sum(subtotal) from order_detail;

--这两个表如果不给其加上锁，有可能某一个表会添加修改数据，造成数据读取不一致。所以需要在执行这两句前执行

--lock tables orders read local,order_detail read local;  //这里的local表示允许在表尾并发插入
.....
--unlock tables;  //解锁
```



### 3） MyISAM加锁的注意事项

    1、显示加锁，如上文中如果加了某一种锁，那么只能执行相应的操作，加了读锁，只能读取select,不能update,delete,insert.反之，隐式锁同理。

   2、 执行了local table 后，当前会话只能访问当前加锁的表，不能访问其他表，否则报错。

   3、对当前的表显示加锁后，如果当前表用了别名，用了多次也会报错,隐式不会有这样的情况。如下：

```sql
lock  table actor read;

selelct a.name b.name from actor a, actor b where a.name = b.name and a.name="vison"; //报错

需要修改加锁条件为：lock table actor as a read,actor as b read;
```

   4、MyISAM总是一次获取SQL语句所需要的全部锁，这也是MyISAM不会出现死锁的原因。

3.4 MyISAM 并发插入

        总体而言，MyISAM表的读和写是串行的，但是在一定条件下也是支持查询和插入并发进行。这个存储引擎又给系统变量concurrent_insert专门用于控制并发插入的行为。

concurrent_insert的值	并发插入可行性
concurrent_insert=0	不允许并发插入
concurrent_insert=1	如果MyISAM表没有空洞(即表中间没有删除行)，则允许插入在表尾，(通过optimize table 可以去除空洞,自行百度)
concurrent_insert=2	无论有没有空洞，都允许在表尾并发插入数据
3.5 MyISAM锁的调度

问题：如果一个读请求和一个写请求同时到达，那么优先让哪一个进程的请求获取到锁呢？

      答案：写进程获取，就算是读请求先到达进入排队，也会先让写请求插队，这是由于MySQL默认认为写请求比读请求重要，这也是MyISAM表不太适合有大量更新操作和查询操作的原因，大量的更新操作会让查询很难获取到锁。不过可以通过启动参数调节
    
    set low_priority_updates  = 1 表示降低更新优先级,当然还有 insert,update，delete 。当前也可以设置max_write_lock_count设置一个合适的值，当表的写锁到达一个值后，就将写锁优先级降低。
# 3.Innodb引擎的锁

https://dev.mysql.com/doc/refman/5.7/en/innodb-locking.html

​		官网把锁分成了8 类。所以我们把前面的两个行级别的锁（Shared and Exclusive Locks），和两个表级别的锁（Intention Locks）称为锁的基本模式；后面三个Record Locks、Gap Locks、Next-Key Locks，我们把它们叫做锁的算法，也就是分别在什么情况下锁定什么范围。



## 3.1 锁的粒度

​		**InnoDB 里面既有行级别的锁，又有表级别的锁**，我们先来分析一下这两种锁定粒度的一些差异。
表锁，顾名思义，是锁住一张表；行锁就是锁住表里面的一行数据。锁定粒度，表锁肯定是大于行锁的。

- 那么加锁效率，表锁应该是大于行锁还是小于行锁呢？
  - 大于。为什么？表锁只需要直接锁住这张表就行了，而行锁，还需要在表里面去检索这一行数据，所以表锁的加锁效率更高。

- 第二个冲突的概率？表锁的冲突概率比行锁大，还是小？
- 大于，因为当我们锁住一张表的时候，其他任何一个事务都不能操作这张表。但是我们锁住了表里面的一行数据的时候，其他的事务还可以来操作表里面的其他没有被锁定的行，所以表锁的冲突概率更大。
- 表锁的冲突概率更大，所以并发性能更低，这里并发性能就是小于。
  



## 3.1 锁的基本类型

### 1）行锁（共享锁和排他锁）

​      行锁分为共享锁和排他锁。InnoDB的行锁是通过给索引上的*索引项加锁*来实现的。只有通过索引条件进行数据检索，InnoDB才使用行级锁，否则**InnoDB将使用表锁**（锁住索引的所有记录）。

**i: 共享锁（Share Lock）**

​     共享锁又称**读锁**，是读取操作创建的锁。其他用户可以并发读取数据，但任何事务都不能对数据进行修改（获取数据上的排他锁），直到已释放所有共享锁。如果事务T对数据A加上共享锁后，则其他事务只能对A再加共享锁，不能加排他锁。获得共享锁的事务只能读数据，不能修改数据**。**

​    **用途：**主要是用来确认记录是否存在，并确保没有人对这个记录进行update,delete操作，如果当前事务需要对该记录进行修改那么请用select ...for update获得排他锁，否则容易造成死锁。

​           **语法为**： *select \* from table **lock in share mode***

**ii: 排他锁（eXclusive lock）**   

​      又称为写锁，简称X锁，排他锁不能与其他锁并存，如一个事务获取了一个数据行的排他锁，其他事务就不能再获取该行的锁（共享锁、排他锁），只有该获取了排他锁的事务是可以对数据行进行读取和修改，（其他事务要读取数据可来自于快照）

​           **语法为**： update，delete,insert 自动加了排他锁

​                          *select \* from table **for update*** 

 另外：为了允许行锁和表锁的共同存在，实现多粒度机制，InnoDB还有两种内部使用的意向锁



### 2）意向锁-表锁级别

​      意向锁是属于表锁级别的的。分为**意向共享锁和意向排它锁**；InnoDB自动加的，不需用户干预



- 也就是说，当我们给一行数据加上共享锁之前，数据库会自动在这张表上面加一个意向共享锁。
- 当我们给一行数据加上排他锁之前，数据库会自动在这张表上面加一个意向排他锁。

反过来说：
如果一张表上面至少有一个意向共享锁，说明有其他的事务给其中的某些数据行加上了共享锁。
如果一张表上面至少有一个意向排他锁，说明有其他的事务给其中的某些数据行加上了排他锁。



​		那么这两个表级别的锁存在的意义是什么呢？第一个，我们有了表级别的锁，在InnoDB 里面就可以支持更多粒度的锁。它的第二个作用，我们想一下，如果说没有意向锁的话，当我们准备给一张表加上表锁的时候，我们首先要做什么？是不是必须先要去判断有没其他的事务锁定了其中了某些行？如果有的话，肯定不能加上表锁。那么这个时候我们就要去扫描整张表才能确定能不能成功加上一个表锁，如果数据量特别大，比如有上千万的数据的时候，加表锁的效率是不是很低？但是我们引入了意向锁之后就不一样了。我只要判断这张表上面有没有意向锁，如果有，就直接返回失败。如果没有，就可以加锁成功。

**意义：**当事务想去进行锁表时，可以先判断意向锁是否存在，存在时则可快速返回该表不能启用表锁。说白了就是这张表如果存在已经被行锁锁住了，就不能加表锁了。



### 2.3 表锁

​      InnoDB的行锁是通过给索引上的*索引项加锁*来实现的。只有通过索引条件进行数据检索，InnoDB才使用行级锁，否则**InnoDB将使用表锁**（锁住索引的所有记录）。

​    也可以通过命令 这样加锁   lock tables xx read/write；（和myisam设置表锁一样）



### 2.4 自增锁

​       针对自增列自增长的一个特殊的表级别锁，当当前的id自增后，但是在事务回滚后，这个id已经被使用过了，就会在表中出现id中间消失的状态。
​        可以通过 命令查询  show variables like 'innodb_autoinc_lock_mode';
​        默认取值1 ，代表连续，事务未提交ID

**自增长和锁：**
		默认会有一个自增长计数器的表，通过select MAX(auto_inc_col) from t for update,当完成sql的插入后就释放当前锁，不是当前事务执行完毕；并发插入效率较低，一般需要等待前一个插入完成，特别是大批量插入



## 3.2 行锁的算法

​		这里主要是讲解行锁的算法；其实**我们的锁也主要是针对索引来实现的，如果查找当前数据没有匹配到索引，那么是锁住整个表的。**

```text
1、为什么表里面没有索引的时候，锁住一行数据会导致锁表？或者说，如果锁住的是索引，一张表没有索引怎么办？
所以，一张表有没有可能没有索引？
答：
	1）如果我们定义了主键(PRIMARY KEY)，那么InnoDB 会选择主键作为聚集索引。
	2）如果没有显式定义主键，则InnoDB 会选择第一个不包含有NULL 值的唯一索引作为主键索引。
	3）如果也没有这样的唯一索引，则InnoDB 会选择内置6 字节长的ROWID 作为隐藏的聚集索引，它会随着行记录的写入而主键递增。

	所以，为什么锁表，是因为查询没有使用索引，会进行全表扫描，然后把每一个隐藏的聚集索引都锁住了。

2、为什么通过唯一索引给数据行加锁，主键索引也会被锁住？
	大家还记得在InnoDB 里面，当我们使用辅助索引的时候，它是怎么检索数据的吗？辅助索引的叶子节点存储的是什么内容？
在辅助索引里面，索引存储的是二级索引和主键的值。比如name=4，存储的是name的索引和主键id 的值4。
而主键索引里面除了索引之外，还存储了完整的数据。所以我们通过辅助索引锁定一行数据的时候，它跟我们检索数据的步骤是一样的，会通过主键值找到主键索引，然后也锁定。
```

| 锁类型               | 说明                                                         |
| -------------------- | ------------------------------------------------------------ |
| Record Lock 记录锁   | 对索引项加锁                                                 |
| Gap Lock 间隙锁      | 对索引项之间的“间隙”、第一条记录前的间隙或者最后一条记录后的间隙加锁 |
| Next-key Lock 临键锁 | 前两种的组合，对记录及其前面的间隙加锁   锁住记录+区间（左开右闭） |

### 1）Record Lock 记录锁

​         当条件为精确匹配时就会退化为记录锁

![img](https://img-blog.csdnimg.cn/20181211000729769.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)



### 2）Next-key Lock 临键锁

​          当sql执行按照索引进行数据的检索时,查询条件为范围查找（between and、<、>等）并有数据命中则此时SQL语句加上的锁为Next-key locks， 锁住索引的记录+区间。否则就是表锁哦。

临键锁也是解决了RR（Repeatable read）可重复读幻读的问题，原因是当前事务在这个区间做了查询就把当前区间给锁住了，其他事务就不能插入数据，也就不会出现幻读的情况了。

![img](https://img-blog.csdnimg.cn/20181210235748258.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### 3.2 Gap Lock 间隙锁

​         当查询的记录不存在，临键锁就会退化为间隙锁。

![img](https://img-blog.csdnimg.cn/20181211000527452.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)



**总结上面的并发问题：**

 更新丢失：通过对当前修改线程加排他锁，其他线程就无法修改当前记录；

  脏读：通过对修改记录的事务线程 加排他锁实现，当前线程加了排他锁，其他线程就不能查询当前的记录；

 不可重复读：多次查询的事务线程，对这个线程加上共享锁，这样其他线程想修改这条记录是无法修改的；

 幻读：通过加上Next-key锁，对于多次查询的线程有了临检索就不会让新的数据插入到这个区间来。

## 



# 4. 死锁

## 4.1 锁的释放与阻塞
回顾：锁什么时候释放？
	事务结束（commit，rollback）；客户端连接断开。

​		如果一个事务一直未释放锁，其他事务会被阻塞多久？会不会永远等待下去？如果是，在并发访问比较高的情况下，如果大量事务因无法立即获得所需的锁而挂起，会占用大量计算机资源，造成严重性能问题，甚至拖跨数据库。

```sql
-- MySQL 有一个参数来控制获取锁的等待时间，默认是50 秒。
show VARIABLES like 'innodb_lock_wait_timeout';
```



## 4.2 死锁发生和检测

为什么可以直接检测到呢？是因为死锁的发生需要满足一定的条件，所以在发生死锁时，InnoDB 一般都能通过算法（wait-for graph）自动检测到。

那么死锁需要满足什么条件？死锁的产生条件：
因为锁本身是互斥的

（1）同一时刻只能有一个事务持有这把锁，

（2）其他的事务需要在这个事务释放锁之后才能获取锁，而不可以强行剥夺，

（3）当多个事务形成等待环路的时候，即发生死锁。

## 4.3 查看锁信息

```sql
-- SHOW STATUS 命令中，包括了一些行锁的信息：
show status like 'innodb_row_lock_%';

-- Innodb_row_lock_current_waits：当前正在等待锁定的数量；
-- Innodb_row_lock_time ：从系统启动到现在锁定的总时间长度，单位ms；
-- Innodb_row_lock_time_avg ：每次等待所花平均时间；
-- Innodb_row_lock_time_max：从系统启动到现在等待最长的一次所花的时间；
-- Innodb_row_lock_waits ：从系统启动到现在总共等待的次数。
```



```sql
-- SHOW 命令是一个概要信息。InnoDB 还提供了三张表来分析事务与锁的情况：
select * from information_schema.INNODB_TRX; -- 当前运行的所有事务，还有具体的语句
select * from information_schema.INNODB_LOCKS; -- 当前出现的锁
select * from information_schema.INNODB_LOCK_WAITS; -- 锁等待的对应关系
```

找出持有锁的事务之后呢？
		如果一个事务长时间持有锁不释放， 可以kill 事务对应的线程ID ， 也就是INNODB_TRX 表中的trx_mysql_thread_id，例如执行kill 4，kill 7，kill 8。



​		当然，死锁的问题不能每次都靠kill 线程来解决，这是治标不治本的行为。我们应该尽量在应用端，也就是在编码的过程中避免。



## 4.4 死锁的避免

1、在程序中，操作多张表时，尽量以相同的顺序来访问（避免形成等待环路）；
2、批量操作单张表数据的时候，先对数据进行排序（避免形成等待环路）；
3、申请足够级别的锁，如果要操作数据，就申请排它锁；
4、尽量使用索引访问数据，避免没有where 条件的操作，避免锁表；
5、如果可以，大事务化成小事务；
6、使用等值查询而不是范围查询查询数据，命中记录，避免间隙锁对并发的影响。