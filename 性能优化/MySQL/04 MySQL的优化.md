# 1.架构优化

## 1.1 添加缓存

## 1.2 采用主从复制

## 1.3 分库分表

## 1.4 其他集群的高可用方案

传统的HAProxy + keepalived 的方案，基于主从复制。

传统的HAProxy + keepalived 的方案，基于主从复制。

传统的HAProxy + keepalived 的方案，基于主从复制。

MMM（Master-Master replication manager for MySQL），一种多主的高可用架构，是一个日本人开发的，像美团这样的公司早期也有大量使用MMM

MySQL 5.7.17 版本推出的InnoDB Cluster，也叫MySQL Group Replicatioin（MGR），这个套件里面包括了mysql shell 和mysql-route



# 2.MySQL服务端优化

## 2.1 选择对应的存储引擎



# 3. MySQL客户端优化

## 3.1 连接池优化

```mysql
-- （1）修改配置参数增加可用连接数，修改max_connections 的大小：
show variables like 'max_connections'; -- 修改最大连接数，当有多个应用连接的时候

-- （2）或者，或者及时释放不活动的连接。交互式和非交互式的客户端的默认超时时间都是28800 秒，8 小时，我们可以把这个值调小。
show global variables like 'wait_timeout'; -- 及时释放不活动的连接，注意不要释放连接池还在使用的连接

-- (3)配置不同的连接池

```



# 4. 对于开发人员的优化

建议参考阿里的SQL规范来；

参考官网：https://dev.mysql.com/doc/refman/5.7/en/optimization.html

## 4.1 建库建表

数据 原则：使用可以正确存储数据的最小数据类型。


- 字符类型变长的建议用varchar，固定的用char
- 非空字段尽量定义成NOT NULL，提供默认值，或者使用特殊值、空串代替null
- 不要用外键、触发器、视图
- 不要用数据库存储图片（比如base64 编码）或者大文件；
- 将不常用的字段拆分出去，避免列数过多和数据量过大。
- 索引的创建参考索引最大使用
- 有时可以增加冗余列避免连表查询

## 4.2 SQL的编写

- **1.尽量采用批量操作**，避免和mysql服务端过多的交互；批量删除，更新，添加

- **2.优化查询**

  2.1.避免全表扫描

  2.2.避免索引失效

  2.3.避免排序，不能避免，尽量选择索引排序

  2.4.避免查询不必要的字段

  2.5.避免临时表的创建，删除

- **3.优化排序 order by**

  避免fileSort排序

- **4.优化分组 group by**

   默认情况下group by 对字段分组的时候，会排序。这和在查询order by 的情况类似。

  ```sql
  -- 1. 如果在在分组的时候不需要排序，最好关掉排序命令：order by null。例如：
  explain select name sum(money) from user group by name order by null;
  ```

- **5.优化嵌套查询**

  某些子查询可以通过join来代替。理由：join不需要在内存中创建一个临时表来存储数据。

  ```mysql
  explain select * from customer where customer_id not in (select customer_id from payment) ;
  
  -- 上面的语句用下面的语句代替
   explain select * from customer a left join payment b on a.customer_id=b.customer where b.customer_id is null;
  
  ```

- **优化分页查询**

  ```mysql
  -- 常见的分页查询，查询到“limit 2000,20”;时候就会出现先查询前面2200个，然后抛弃前面2000个，造成查询和排序代价非常大。优化方式如下：
  --   1.在索引上完成排序分页的操作。根据主键关联回原表查询所需要的其他内容。例如：
      explain select a.last_name , a.first_name from user a inner join (select id from user order by id limit 2000,20) b on a.id=b.id;
  
  -- 2.把limit查询转换成某一个位置查询。可以通过把上一页的最后一条记录记下来;例如：
  	select * from payment order by rental_id desc limit 2000,20;    //这样效率非常低下
  
  --  如上面是通过 rental_id 降序来排列的 ，那么我们在查询 limit 1800,20时候，记录下2000位置的rental_id,加入这里的rental_id的值，假设这里的值是“5000” ，那么sql语句就可以转换成如下：
  
   select * from payment where rental_id < 5000 order by rental_id desc limit 10;
  
  ```

  

- **使用SQL提示**

  ```mysql
  -- 1. use index  这个表示希望sql去参考的索引，就可以让mysql不在考虑其他可用的索引了
      explain select count(*) from user user index(idx_user_id);
  
  -- 2.ingore index 只是单纯的希望mysql忽略一个索引，或者多个。例如：
       explain select count(*) from rental ignore index(idx_rental_date)
  
  -- 3.force index 强制mysql使用一个索引
  explain select * from user  use index (idx_fk_inventory_id) where inventory_id >1;
  
  ```

  

## 4.3 慢SQL的检测

 **打开慢日志开关**：为开启慢查询日志是有代价的（跟bin log、optimizer-trace 一样），所以它默认是关闭的

```sql
--回话级别查看，修改
show variables like '%slow_query%';

-- my.cnf 修改
slow_query_log = ON
long_query_time=2
slow_query_log_file =/var/lib/mysql/localhost-slow.log
```



**慢日志分析**

mysqldumpslow

https://dev.mysql.com/doc/refman/5.7/en/mysqldumpslow.html
MySQL 提供了mysqldumpslow 的工具，在MySQL 的bin 目录下。



SHOW PROFILE
https://dev.mysql.com/doc/refman/5.7/en/show-profile.html
SHOW PROFILE 是谷歌高级架构师Jeremy Cole 贡献给MySQL 社区的，可以查看SQL 语句执行的时候使用的资源，比如CPU、IO 的消耗情况



## 4.4 慢SQL的优化

使用`Explain` 关键字查看执行计划来优化SQL











