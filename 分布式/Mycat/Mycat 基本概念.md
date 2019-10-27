

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

​	2，数据库连接

​	3，硬件资源限制（QPS\TPS）

​	



## 1.3 多数据源/读写数据源的解决方案

我们先要分析一下SQL 执行经过的流程：

```text
DAO——Mapper（ORM）——JDBC——代理——数据库服务
```

### 1）客户端DAO 层

​	第一个就是在我们的客户端的代码，比如DAO 层，在我们连接到某一个数据源之前，我们先根据配置的分片规则，判断需要连接到哪些节点，再建立连接。

Spring 中提供了一个抽象类AbstractRoutingDataSource，可以实现数据源的动态切换



在DAO 层实现的优势：不需要依赖ORM 框架，即使替换了ORM 框架也不受影响。实现简单（不需要解析SQL 和路由规则），可以灵活地定制。

缺点：不能复用，不能跨语言。



### 2）ORM 框架层

​		第二个是在框架层，比如我们用MyBatis 连接数据库，也可以指定数据源。我们可以基于MyBatis 插件的拦截机制（拦截query 和update 方法），实现数据源的选择。

例如：https://github.com/colddew/shardbatis
https://docs.jboss.org/hibernate/stable/shards/reference/en/html_single/



### 3）驱动层

​		不管是MyBatis 还是Hibernate，还是Spring 的JdbcTemplate，本质上都是对JDBC的封装，所以第三层就是驱动层。比如Sharding-JDBC，就是对JDBC 的对象进行了封装。JDBC 的核心对象：

DataSource：数据源

Connection：数据库连接
Statement：语句对象
ResultSet：结果集

那我们只要对这几个对象进行封装或者拦截或者代理，就可以实现分片的操作。



### 4）代理层

前面三种都是在客户端实现的，也就是说不同的项目都要做同样的改动，不同的编程语言也有不同的实现，所以我们能不能把这种选择数据源和实现路由的逻辑提取出来，做成一个公共的服务给所有的客户端使用呢？

这个就是第四层，代理层。比如Mycat 和Sharding-Proxy，都是属于这一层。





# 2.Mycat

参考：	<https://github.com/MyCATApache/Mycat-Server>

​		Mycat 是开源的分布式数据库中间件，基于阿里的cobar的开源框架之上。它处于数据库服务与应用服务之间。它是进行数据处理与整合的中间服务。

- 一个彻底开源的，面向企业应用开发的大数据库集群

-  支持事务、ACID、可以替代MySQL的加强版数据库

- 一个融合内存缓存技术、NoSQL技术、HDFS大数据的新型SQL Server

![](http://ww1.sinaimg.cn/mw690/b8a27c2fgy1g578ld5y96j20lw0dxdh1.jpg)

架构图：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g6br47oeozj20e107udke.jpg)

## 2.1 Mycat 目录介绍

- bin 程序目录，存放了 window 版本和 linux 版本可执行文件./mycat {start|restart|stop|status…}
- conf 目录下存放配置文件，
  - server.xml 是 Mycat 服务器参数调整和用户授权的配置文件
  - schema.xml 是逻辑库定义和表
  - rule.xml 是分片规则的配置文件，分片规则的具体一些参数信息单独存放为文件，也在这个目录下
  - log4j2.xml配置logs目录日志输出规则
  - wrapper.conf JVM相关参数调整

- lib 目录下主要存放 mycat 依赖的一些 jar 文件
- logs目录日志存放日志文件

## 2.2 Mycat相关概念

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



# 3.Mycat配置文件介绍

## 3.1 schema.xml配置

### 1. \<schema>配置

​	schema 标签用于定义 MyCat 实例中的逻辑库，如

```xml
<!--逻辑库db_store-->
<schema name="db_store" checkSQLschema="false" sqlMaxLimit="100">
    <table name="store" dataNode="db_store_dataNode" primaryKey="storeID"/>
    <table name="employee" dataNode="db_store_dataNode" primaryKey="employeeID"/>
</schema>

<!--逻辑库db_user-->
<schema name="db_user" checkSQLschema="false" sqlMaxLimit="100">
    <table name="data_dictionary" type="global"          dataNode="db_user_dataNode1,db_user_dataNode2" primaryKey="dataDictionaryID"/>
    <table name="users" dataNode="db_user_dataNode$1-2"  rule="mod-userID-long"    primaryKey="userID">
        <childTable name="user_address"  joinKey="userID" parentKey="userID"    primaryKey="addressID"/>
    </table>
</schema>
```

#### 1）schema配置

1、 name属性

​		配置逻辑库的名字（即数据库实例名）；

2、 dataNode属性

​		用于配置该逻辑库默认的分片。没有通过table标签配置的表，就会走到默认的分片上。这里注意没有配置在table标签的表，用工具查看是无法显示的，但是可以正常使用。

3、 checkSQLschema属性

​		boolean类型。

> 当前端执行【select *from USERDB.tf_user;】时（表名前指定了mycat逻辑库名），两种取值：

> true：mycat会把语句转换为【select * from tf_user;】

> false：会报错

4、 `sqlMaxLimit`属性

​	相当于sql的所有结果集中，加上【limit N】。如果sql本身已经指定limit，则以sql指定的为准。

​	作用：防止将mycat内存撑爆了

#### 2）\<table>配置

​	table标签为schema标签的子标签；table标签用于定义Mycat的逻辑表，以及逻辑表的分片规则

1、 `name`属性

​	逻辑表的表名，同一个schema表名必须唯一。

2、`dataNode`属性

​	定义这个逻辑表所属的 dataNode，用英文逗号间隔，如：dataNode="dn1,dn2"

如果dataNode过多，可以使用如下的方法减少配置

` dataNode="db_user_dataNode$1-2"` //$1-2表示1到2所有的

3、rule属性

​		该属性用于指定逻辑表要使用的规则名字，规则名字在 rule.xml 中定义，必须与 tableRule 标签中 name 属性属性值一一对应。

4、 ruleRequired属性

​	该属性用于指定表是否绑定分片规则，如果配置为 true，但没有配置具体 rule 的话 ，程序会报错

5、 primaryKey属性

​		指定该逻辑表对应真实表的主键。MyCat会缓存主键（通过primaryKey属性配置）和具体 dataNode的信息。当分片规则使用非主键进行分片时，那么在使用主键进行查询时，MyCat就会通过缓存先确定记录在哪个dataNode上，然后再在该dataNode上执行查询；

​		`关于Mycat的主键缓存，其机制是：当根据主键查询的SQL语句第一次执行时，Mycat会对其结果进行分析，确定该主键在哪个分片上，并进行该主键到分片ID的缓存。通过连接MyCAT的9066管理端口，执行show@@cache，可以显示当前缓存的使用情况。可在sql执行前后的2个时间点执行show @@cache，通过结果信息中的LAST_PUT和LAST_ACCESS列，判断相应表的缓存是否有被更新过。`

​	这个其实建议能不用主键则不用主键，可以用**唯一键**代替,因为mycat会对该唯一键做缓存，下次查询就直接去相应分片节点查询，就不用再走所有节点进行查询了。



6、 type属性

​	该属性定义了逻辑表的类型，目前逻辑表只有“全局表”和”普通表”两种类型。对应的配置：

- 全局表：global。

- 普通表：不指定该值为 global 的所有表。

7、 autoIncrement属性

​		这个使用在分布式全局ID使用的

8、subTables属性

分表配置，mycat1.6之后开始支持，但dataNode 在分表条件下只能配置一个。

9、 `needAddLimit`属性

​	与schema标签的sqlMaxLimit配合使用，如果needAddLimit值为false，则语句不会加上limit



#### 3）\<childTable>配置

​	childTable 标签用于定义 E-R 分片的子表。通过标签上的属性与父表进行关联。

1、name属性

​		定义子表的表名

2、joinKey属性

​		插入子表的时候会使用这个列的值查找父表存储的数据节点。

3、parentKey属性

​		该属性指定的值一般为与父表建立关联关系的列名。Mycat首先获取 joinkey 的值，再通过 parentKey 属性指定的列名产生查询语句，通过执行该语句得到父表存储在哪个分片上。从而确定子表存储的位置。

4、primaryKey属性

​		同table标签描述。

5、 needAddLimit 属性

​		同table标签描述。





### 2. \<dataNode>配置

​	节点配置

```xml
<!-- 节点配置 -->
<!-- db_store -->
<dataNode name="db_store_dataNode" dataHost="db_storeHOST" database="db_store" />

<!-- db_user -->
<dataNode name="db_user_dataNode1" dataHost="db_userHOST1" database="db_user" />
<dataNode name="db_user_dataNode2" dataHost="db_userHOST2" database="db_user" />
```

1、name 属性

​	指定分片的名字

2、dataHost 属性

​	定义该分片属于哪个**数据库实例**，会使用下面\<dataHost>的name值然后和真正的物理数据库做关联

3、database 属性

​	定义该分片属于哪个**具体数据库实例上的具体库**（即对应mysql中实际的DB)



### 3. \<dataHost>配置

​	节点主机配置；定义后端的物理数据库主机信息

```xml
<dataHost name="db_storeHOST" maxCon="1000" minCon="10" balance="1"
     writeType="0" dbType="mysql" dbDriver="native" switchType="1"  slaveThreshold="100">
    <heartbeat>select user()</heartbeat>
    <!-- can have multi write hosts -->
    <writeHost host="hostM1" url="192.168.8.137:3306" user="root"  password="123456">
        <!-- can have multi read hosts -->
        <readHost host="hostS1" url="192.168.8.101:3306" user="root" password="123456" />
    </writeHost>

</dataHost>
```

#### 1）dataHost属性配置

1、`name`  属性：指定dataHost的名字

2、`maxCon` 属性

​		指定每个读写实例连接池的最大连接。也就是说，标签内嵌套的`writeHost`、 `readHost` 标签都会使用这个属性的值来实例化出连接池的最大连接数。

3、`minCon` 属性

​	指定每个读写实例连接池的最小连接，初始化连接池的大小

4、`balance` 属性

负载均衡类型：

- balance="0", 不开启读写分离机制，所有读操作都发送到当前可用的`writeHost` 上。

- balance="1"，全部的 readHost 与 stand by writeHost 参与 select 语句的负载均衡，简单的说，当双主双模式(M1->S1，M2->S2，并且 M1 与 M2 互为主备)，正常情况下，M2,S1,S2 都参与 select 语句的负载衡。

- balance="2"，所有读操作都随机的在 writeHost、 readhost 上分发。

- balance="3"，所有读请求随机的分发到 wiriterHost 对应的 readhost 执行，writerHost 不负担读压力，意 balance=3 只在 1.4 及其以后版本有，1.3 没有。

5、`writeType` 属性

（1）writeType="0", 所有写操作发送到配置的第一个 writeHost，第一个挂了切到还生存的第二个writeHost，重新启动后以切换后的为准，切换记录在配置文件中:dnindex.properties.

（2）writeType="1"，所有写操作都随机的发送到配置的 writeHost（多个writeHost），1.5 以后废弃不推荐。

6、` dbType` 属性

​	指定后端连接的数据库类型，目前支持二进制的 mysql 协议，还有其他使用 JDBC 连接的数据库。例如：mongodb、 oracle、 spark 等。

7、` dbDriver` 属性

​		指定连接后端数据库使用的 Driver，目前可选的值有 native 和 JDBC。使用native 的话，因为这个值执行的是二进制的 mysql 协议，所以可以使用 mysql 和 maridb。其他类型的数据库则需要使用 JDBC 驱动来支持。

9、`switchType` 属性

​		表示主从切换的策略

- -1 表示主机挂了不自动切换

- 1 默认值，主机挂了自动切换

- 2 基于 MySQL 主从同步的状态决定是否切换；如果从服务器延时没有操作slaveThreshold这个值才会切换

  ```xml
  //使用这个属性需要配置心跳为这个
  <heartbeat>show slave status</heartbeat> 
  ```

- 3 基于 MySQL galary cluster 的切换机制（适合集群）（1.4.1）；

  ```xml
  //心跳语句为 
  <heartbeat> show status like ‘wsrep%’</heartbeat> 
  ```

10、`slaveThreshold`属性

​		单位：秒；这个是基于上面`switchType=2`的时候通过心跳检查机制发现，通过发现`Seconds_Behind_Master`的值是否大于`slaveThreshold`的值（这是主从延迟的时长）就会切换

```mysql
mysql> show slave status \G;
*************************** 1. row ***************************
               Slave_IO_State: Waiting for master to send event
                  Master_Host: 192.168.124.132
                  Master_User: master
 		Seconds_Behind_Master: 0   #slaveThreshold 表示这个值，如果操作这个延时，就会主从切换
```

9、` tempReadHostAvailable` 属性

​	如果配置了这个属性 writeHost 下面的 readHost 仍旧可用，默认 0 可配置（0、 1）。

 

#### 2）heartbeat标签

​		这个标签内指明用于和后端数据库进行心跳检查的语句。例如,MYSQL可以使用`select user()`，Oracle可以使用`select 1 from dual`等。当前这个可以和上面的`switchType`和`slaveThreshold`配合使用

​		这个标签还有一个`connectionInitSql`属性，主要是当使用Oracla数据库时，需要执行的初始化SQL语句就这个放到这里面来。例如：`alter session set nls_date_format='yyyy-mm-dd hh24:mi:ss'`



#### 3）writeHost 、readHost 标签

1、`host `属性

​	用于标识不同实例，一般 `writeHost `我们使用M1，`readHost` 我们用S1。

2、 `url` 属性

后端实例连接地址，如果是使用 native 的 dbDriver，则一般为 address:port 这种形式。用 JDBC 或其他的dbDriver，则需要特殊指定。当使用 JDBC 时则可以这么写：jdbc:mysql://localhost:3306/。

3、user 属性

​	后端存储实例需要的用户名字

4、password 属性

​		后端存储实例需要的密码，这里再生产环境一般使用加密的秘密，加密方式可以通过如下方式：

在mycat的lib目录使用下面的这个加密进行加密，如果密码配置下面这个需要在配置属性`usingDecrypt=1` 

```bash
//1:hostM1:123456
//	1:表示连接后端数据库加密，0表示应用连接mycat数据库加密
//	hostM1:表示上面host属性的值
//	123456:表示真实的密码
[root@localhost lib]# java -cp Mycat-server-1.6.7.1-release.jar io.mycat.util.DecryptUtil 1:hostM1:123456
JQndRMi10UkbJM6iGZh1xc9P+X9bYC9GXHhuacGEqLCBSDvo8FHUOBRlDBqxFgbcXCsBp/oZ0h6lTUZ/DsNWVA==
```

5、`weight` 属性

​		权重 配置在 readhost 中作为读节点的权重（1.4 以后）

7、 `usingDecrypt` 属性

​		是否对密码加密默认 0 否 如需要开启配置 1，同时使用加密程序对密码加密，如上所示password加密



## 3.2 rule.xml配置

​		rule.xml里面就定义了我们对表进行拆分所涉及到的规则定义。我们可以灵活的对表使用不同的分片[算法](http://lib.csdn.net/base/datastructure)，或者对表使用相同的算法但具体的参数不同。这个文件里面主要有tableRule和function这两个标签。在具体使用过程中可以按照需求添加tableRule和function。

**tableRule标签**：这个标签定义表规则

```xml
<tableRule name="mod-userID-long">
    <rule>
        <columns>userID</columns>
        <algorithm>mod-long</algorithm>
    </rule>
</tableRule>
```

- name 属性指定唯一的名字，用于标识不同的表规则。供schema.xml中分片规则选用

- 内嵌的rule标签则指定对物理表中的哪一列进行拆分和使用什么路由算法。

- columns 内指定要拆分的列名字。

- algorithm 使用function标签中的name属性。连接表规则和具体路由算法。

当然，多个表规则可以连接到同一个路由算法上。标签内使用。让逻辑表使用这个规则进行分片。



**function标签**

```xml
<function name="mod-long" class="io.mycat.route.function.PartitionByMod">
    <!-- how many data nodes 取模算法，取模数量，-->
    <property name="count">2</property>
</function>
```

- name 指定算法的名字,供tableRule标签中algorithm选用
- class 制定路由算法具体的类名字。
- property 为具体算法需要用到的一些属性。



## 3.3 server.xml配置

```bash
#登录mycat的管理客户端
root@localhost>mysql -uroot -P9066 -p123456 -h192.168.8.151

//执行命令表示重新加载修改的mycat配置文件
reload @@config_all;
```

重要的信息如下：

```xml
system
    sequnceHandlerType  //和分布式全局有关
    Processors			//处理mycat的进程数时候多少，不设置会根据CPU核数进行配置
    processorExecutor	//进程里面的线程数是多少
    serverPort			//mycat的进程端口
    managerPort			//mycat的管理端口
    firewall			//对机器的访问 做白名单和黑名单限制
    user				//给用户添加不同的数据库权限
        benchmark		//限流，表示当前用户 的连接数，超过这个连接数 就等待或者禁止连接
        usingDecrypt	//同样做加密操作
        privileges		//优先级配置，如下所示：
```





详细的配置如下所示：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- - - Licensed under the Apache License, Version 2.0 (the "License"); 
	- you may not use this file except in compliance with the License. - You 
	may obtain a copy of the License at - - http://www.apache.org/licenses/LICENSE-2.0 
	- - Unless required by applicable law or agreed to in writing, software - 
	distributed under the License is distributed on an "AS IS" BASIS, - WITHOUT 
	WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. - See the 
	License for the specific language governing permissions and - limitations 
	under the License. -->
<!DOCTYPE mycat:server SYSTEM "server.dtd">
<mycat:server xmlns:mycat="http://io.mycat/">
	<system>
	<property name="useSqlStat">0</property>  <!-- 1为开启实时统计、0为关闭 -->
	<property name="useGlobleTableCheck">0</property>  <!-- 1为开启全加班一致性检测、0为关闭 -->

		<property name="sequnceHandlerType">2</property>
      <!--  <property name="useCompression">1</property>--> <!--1为开启mysql压缩协议-->
        <!--  <property name="fakeMySQLVersion">5.6.20</property>--> <!--设置模拟的MySQL版本号-->
	<!-- <property name="processorBufferChunk">40960</property> -->
	<!-- 
	<property name="processors">1</property> 
	<property name="processorExecutor">32</property> 
	 -->
		<!--默认为type 0: DirectByteBufferPool | type 1 ByteBufferArena-->
		<property name="processorBufferPoolType">0</property>
		<!--默认是65535 64K 用于sql解析时最大文本长度 -->
		<!--<property name="maxStringLiteralLength">65535</property>-->
		<!--<property name="sequnceHandlerType">0</property>-->
		<!--<property name="backSocketNoDelay">1</property>-->
		<!--<property name="frontSocketNoDelay">1</property>-->
		<!--<property name="processorExecutor">16</property>-->
		<!--
			<property name="serverPort">8066</property> <property name="managerPort">9066</property> 
			<property name="idleTimeout">300000</property> <property name="bindIp">0.0.0.0</property> 
			<property name="frontWriteQueueSize">4096</property> <property name="processors">32</property> -->
		<!--分布式事务开关，0为不过滤分布式事务，1为过滤分布式事务（如果分布式事务内只涉及全局表，则不过滤），2为不过滤分布式事务,但是记录分布式事务日志-->
		<property name="handleDistributedTransactions">0</property>
		
			<!--
			off heap for merge/order/group/limit      1开启   0关闭
		-->
		<property name="useOffHeapForMerge">1</property>

		<!--
			单位为m
		-->
		<property name="memoryPageSize">1m</property>

		<!--
			单位为k
		-->
		<property name="spillsFileBufferSize">1k</property>

		<property name="useStreamOutput">0</property>

		<!--
			单位为m
		-->
		<property name="systemReserveMemorySize">384m</property>


		<!--是否采用zookeeper协调切换  -->
		<property name="useZKSwitch">true</property>


	</system>
	
	<!-- 全局SQL防火墙设置 -->
	<!-- 
	<firewall> 
	   <whitehost>
	      <host host="127.0.0.1" user="mycat"/>
	      <host host="127.0.0.2" user="mycat"/>
	   </whitehost>
       <blacklist check="false">
       </blacklist>
	</firewall>
	-->
	
	<user name="root">
		<property name="password">123456</property>
        <!-- 数据库 权限设置 -->
		<property name="schemas">db_store,db_user</property>
		<!--<property name="benchmark">1000</property>-->
		<!-- 表级 DML 权限设置  -->
   <!-- （insert,update,select,delete）IUSD 下面的dml分别是这四个权限，1表示拥有，0表示没权限 -->
		<!-- 		
		<privileges check="false">
			<schema name="db_user" dml="0110" >
				<table name="users" dml="1111"></table>  IUSD
				<table name="useraddres" dml="1110"></table>
			</schema>
		</privileges>		
		 -->
	</user>

</mycat:server>

```



## 3.4 ZK配置

<https://www.cnblogs.com/leeSmall/p/9551038.html>

​		Mycat 也支持ZK 配置（ 用于管理配置和生成全局ID ） ， 执行bin 目录下`init_zk_data.sh`,会自动将zkconf 下的所有配置文件上传到ZK（先拷贝到这个目录)

```text
cd /usr/local/soft/mycat/conf
cp *.txt *.xml *.properties zkconf/
cd /usr/local/soft/mycat/bin
./init_zk_data.sh
```



```text
启用ZK 配置：
mycat/conf/myid.properties


loadZk=true
zkURL=127.0.0.1:2181
clusterId=010
myid=01001
clusterSize=1
clusterNodes=mycat_gp_01
#server booster ; booster install on db same server,will reset all minCon to 2
type=server
boosterDataHosts=dataHost1
```

**注意**：如果执行`init_zk_data.sh` 脚本报错的话，代表未写入成功，此时不要启用ZK配置并重启，否则本地文件会被覆盖。

启动时如果loadzk=true 启动时，会自动从zk 下载配置文件覆盖本地配置。
在这种情况下如果修改配置，需要先修改conf 目录的配置，copy 到zkconf，再执行上传



## 3.5 MyCat命令使用

进入mycat/bin 目录（注意要先启动物理数据库）：

```bash
#操作命令

启动		 ./mycat start
停止		 ./mycat stop
重启		 ./mycat restart
查看状态	./mycat status
前台运行	./mycat console

#连接：这里必须要写ip，本地也要用ip，8066表示mycat的连接端口，catmall表示逻辑库
mysql -uroot -p123456 -h 192.168.8.151 -P8066 catmall
```



# 4.Mycat全局ID

​		Mycat 全局序列实现方式主要有4 种：本地文件方式、数据库方式、本地时间戳算法、ZK。也可以自定义业务序列。

注意获取全局ID 的前缀都是：`MYCATSEQ_`

## 4.1 文件方式

配置文件server.xml sequnceHandlerType 值：

```xml
##	0 文件; 1 数据库; 2 本地时间戳; 3 ZK
<property name="sequnceHandlerType">0</property>
```

文件方式，配置conf/sequence_conf.properties

```properties
CUSTOMER.HISIDS=
CUSTOMER.MINID=10000001
CUSTOMER.MAXID=20000000
CUSTOMER.CURID=10000001
```



**语法：**

```bash
select next value for MYCATSEQ_CUSTOMER

INSERT INTO `customer` (`id`, `name`) VALUES (next value for MYCATSEQ_CUSTOMER, 'vison');
```



**优点**：本地加载，读取速度较快。
**缺点**：当Mycat 重新发布后，配置文件中的sequence 需要替换。Mycat 不能做集群部署。



## 4.2 数据库方式

```properties
<property name="sequnceHandlerType">1</property>
```

配置： `sequence_db_conf.properties`
把这张表创建在节点dn1

```properties
#sequence stored in datanode
GLOBAL=dn1
CUSTOMER=dn1
```

在dn1数据库节点上创建MYCAT_SEQUENCE 表：

```sql
DROP TABLE IF EXISTS MYCAT_SEQUENCE;
CREATE TABLE MYCAT_SEQUENCE (
name VARCHAR(50) NOT NULL,
current_value INT NOT NULL,
increment INT NOT NULL DEFAULT 1,
remark varchar(100),
PRIMARY KEY(name)) ENGINE=InnoDB;
```

注：可以在schema.xml 配置文件中配置这张表，供外部访问。

```xml
<table name="mycat_sequence" dataNode="dn1" autoIncrement="true" primaryKey="id"></table>
```

创建存储过程——获取当前sequence 的值:

```sql
DROP FUNCTION IF EXISTS `mycat_seq_currval`;
DELIMITER ;;
CREATE DEFINER=`root`@`%` FUNCTION `mycat_seq_currval`(seq_name VARCHAR(50)) RETURNS varchar(64)
CHARSET latin1
DETERMINISTIC
BEGIN
DECLARE retval VARCHAR(64);
SET retval="-999999999,null";
SELECT concat(CAST(current_value AS CHAR),",",CAST(increment AS CHAR) ) INTO retval FROM
MYCAT_SEQUENCE WHERE name = seq_name;
RETURN retval ;
END
;;
DELIMITER ;
```

创建存储过程，获取下一个sequence

```sql
DROP FUNCTION IF EXISTS `mycat_seq_nextval`;
DELIMITER ;;
CREATE DEFINER=`root`@`%` FUNCTION `mycat_seq_nextval`(seq_name VARCHAR(50)) RETURNS varchar(64)
CHARSET latin1
DETERMINISTIC
BEGIN
UPDATE MYCAT_SEQUENCE
SET current_value = current_value + increment WHERE name = seq_name;
RETURN mycat_seq_currval(seq_name);
END
;;
DELIMITER ;
```

创建存储过程，设置sequence

```sql
DROP FUNCTION IF EXISTS `mycat_seq_setval`;
DELIMITER ;;
CREATE DEFINER=`root`@`%` FUNCTION `mycat_seq_setval`(seq_name VARCHAR(50), value INTEGER)
RETURNS varchar(64) CHARSET latin1
DETERMINISTIC
BEGIN
UPDATE MYCAT_SEQUENCE
SET current_value = value
WHERE name = seq_name;
RETURN mycat_seq_currval(seq_name);
END
;;
DELIMITER ;
```



插入记录

```sql
INSERT INTO MYCAT_SEQUENCE(name,current_value,increment,remark) VALUES ('GLOBAL', 1, 100,'');
INSERT INTO MYCAT_SEQUENCE(name,current_value,increment,remark) VALUES ('ORDERS', 1, 100,'订单表使
用');
```



## 4.3 本地时间戳方式
​	ID= 64 位二进制(42(毫秒)+5(机器ID)+5(业务编码)+12(重复累加) ，长度为18 位

```xml
<property name="sequnceHandlerType">2</property>
```

配置文件sequence_time_conf.properties

```properties
#sequence depend on TIME
WORKID=01
DATAACENTERID=01
```

验证：`select next value for MYCATSEQ_GLOBAL`



## 4.4 ZK方式

修改conf/myid.properties
		设置loadZk=true（启动时会从ZK 加载配置，一定要注意备份配置文件，并且先用bin/init_zk_data.sh,把配置文件写入到ZK）

```properties
<property name="sequnceHandlerType">3</property>
```

配置文件：sequence_distributed_conf.properties

```properties
# 代表使用zk
INSTANCEID=ZK
# 与myid.properties 中的CLUSTERID 设置的值相同
CLUSTERID=010
```

复制配置文件

```bash
cd /usr/local/soft/mycat/conf
cp *.txt *.xml *.properties zkconf/
chown -R zkconf/
cd /usr/local/soft/mycat/bin
./init_zk_data.sh
```

验证：`select next value for MYCATSEQ_GLOBAL`


## 4.5 全局ID的 使用
​		在schema.xml 的table 标签上配置autoIncrement="true"，不需要获取和指定序列的情况下，就可以使用全局ID 了。



# 5.Mycat的监控和日志

## 5.1 命令行监控

​		连接到管理端口9066，注意必须要带IP

```bash
mysql -uroot -h127.0.0.1 -p123456 -P9066
```

全部命令：

```bash
mysql>show @@help;

命令						作用
show @@server 			查看服务器状态，包括占用内存等
show @@database 		查看数据库
show @@datanode 		查看数据节点
show @@datasource 		查看数据源
show @@connection		该命令用于获取Mycat 的前端连接状态，即应用与mycat 的连接
show @@backend 			查看后端连接状态
show @@cache			查看缓存使用情况
SQLRouteCache：sql 		路由缓存。
TableID2DataNodeCache	 缓存表主键与分片对应关系。
ER_SQL2PARENTID 		 缓存ER 分片中子表与父表关系
reload @@config			 重新加载基本配置，使用这个命令时mycat服务不可用
show @@sysparam 		参看参数
show @@sql.high 		执行频率高的SQL
show @@sql.slow			慢SQL;设置慢SQL 的命令：reload @@sqlslow=5 ;
```

## 5.2 命令行监控mycatweb 监控

https://github.com/MyCATApache/Mycat-download/tree/master/mycat-web-1.0

Mycat-eye 是mycat 提供的一个监控工具，它依赖于ZK。
本地必须要运行一个ZK，必须先启动ZK。
参考：https://gper.club/articles/7e7e7f7ff7g59gc3g64

下载mycat-web

```bash
cd /usr/local/soft
wget http://dl.mycat.io/mycat-web-1.0/Mycat-web-1.0-SNAPSHOT-20170102153329-linux.tar.gz
tar -xzvf Mycat-web-1.0-SNAPSHOT-20170102153329-linux.tar.gz
```

启动mycat-web

```bash
cd mycat-web
nohup ./start.sh &
```

访问端口8082：http://192.168.8.151:8082/mycat/

mycat server.xml 配置

```xml
<!-- 1 为开启实时统计、0 为关闭-->
<property name="useSqlStat">1</property>
```

## 5.3 日志


​	log4j 的level 配置要改成debug



	**wrapper 日志**：mycat 启动，停止，添加为服务等都会记录到此日志文件，如果系统环境配置错误或缺少配置时，导致Mycat 无法启动，可以通过查看wrapper.log 定位具体错误原因。



​	**mycat.log** 为mycat 主要日志文件，记录了启动时分配的相关buffer 信息，数据源连接信息，连接池，动态类加载信息等等。在conf/log4j2.xml 文件中进行相关配置，如保留个数，大小，字符集，日志文件大小等。





# 6. Mycat 高可用

​	目前Mycat 没有实现对多Mycat 集群的支持，可以暂时使用HAProxy 来做负载
思路：HAProxy 对Mycat 进行负载。Keepalived 实现VIP。

**方式一：**

![K3EeAO.png](https://s2.ax1x.com/2019/10/22/K3EeAO.png)



**方式二：**

![K3EM3d.png](https://s2.ax1x.com/2019/10/22/K3EM3d.png)



# 7. Mycat的注解

​	Mycat 作为一个中间件，有很多自身不支持的SQL 语句，比如存储过程，但是这些语句在实际的数据库节点上是可以执行的。有没有办法让Mycat 做一层透明的代理转发，直接找到目标数据节点去执行这些SQL 语句呢？

​	那我们必须要有一种方式告诉Mycat 应该在哪个节点上执行。这个就是Mycat 的注解。我们在需要执行的SQL 语句前面加上一段代码，帮助Mycat 找到我们的目标节点。



##  7.1 注解的用法

**注解的形式是:**
		`/*!mycat: sql=注解SQL 语句*/`
**注解的使用方式是:**
	`/*!mycat: sql=注解SQL 语句*/ 真正执行的SQL`

使用时将= 号后的"注解SQL 语句" 替换为需要的SQL 语句即可。
使用注解有一些限制，或者注意的地方：

```bash
原始SQL:注解SQL

select:
	如果需要确定分片，则使用能确定分片的注解，比如/*!mycat: sql=select * from users where
user_id=1*/
	如果要在所有分片上执行则可以不加能确定分片的条件

insert:
	使用insert 的表作为注解SQL，必须能确定到某个分片;原始SQL 插入的字段必须包括分片字段
	非分片表（只在某个节点上）：必须能确定到某个分片

delete: 使用delete 的表作为注解SQL

update: 使用update 的表作为注解SQL
```

​		使用注解并不额外增加MyCat 的执行时间；从解析复杂度以及性能考虑，注解SQL 应尽量简单，因为它只是用来做路由的。



## 7.2 注解的例子

**1) 创建表或存储过程**

创建表或存储过程  customer.id=1 全部路由到节点dn1

```bash
-- 存储过程
/*!mycat: sql=select * from customer where id =1 */ CREATE PROCEDURE test_proc() BEGIN END ;
-- 表
/*!mycat: sql=select * from customer where id =1 */ CREATE TABLE test2(id INT);
```



**2) 特殊语句自定义分片**

Mycat 本身不支持insert select，通过注解支持

```sql
/*!mycat: sql=select * from customer where id =1 */ INSERT INTO test2(id) SELECT id FROM order_detail;
```



**3) 多表ShareJoin**

```sql
/*!mycat:catlet=io.mycat.catlets.ShareJoin */
select a.order_id,b.goods_id from order_info a, order_detail b where a.order_id = b.order_id;
```



**4) 读写分离**

​		读写分离: 配置Mycat 读写分离后，默认查询都会从读节点获取数据，但是有些场景需要获取实时数据，如果从读节点获取数据可能因延时而无法实现实时，Mycat 支持通过注解/*balance*/ 来强制从写节点（write host）查询数据。

```sql
/*balance*/ select a.* from customer a where a.id=6666;
```

**5)读写分离数据库选择**

```sql
/*!mycat: db_type=master */ select * from customer;
/*!mycat: db_type=slave */ select * from customer;
/*#mycat: db_type=master */ select * from customer;
/*#mycat: db_type=slave */ select * from customer;

注解支持的'! '不被mysql 单库兼容
注解支持的'#'不被MyBatis 兼容
随着Mycat 的开发，更多的新功能正在加入。
```



## 7.3 注解原理

​	Mycat 在执行SQL 之前会先解析SQL 语句，在获得分片信息后再到对应的物理节点上执行。如果SQL 语句无法解析，则不能被执行。如果语句中有注解，则会先解析注解的内容获得分片信息，再把真正需要执行的SQL 语句发送对对应的物理节点上。

​		所以我们在使用主机的时候，应该清楚地知道目标SQL 应该在哪个节点上执行，注解的SQL 也指向这个分片，这样才能使用。如果注解没有使用正确的条件，会导致原始SQL 被发送到所有的节点上执行，造成数据错误



# 8.离线扩容

​		当我们规划了数据分片，而数据已经超过了单个节点的存储上线，或者需要下线节点的时候，就需要对数据重新分片。



## 8.1 Mycat自带工具

**准备工作：**

1、mycat 所在环境安装mysql 客户端程序。
2、mycat 的lib 目录下添加mysql 的jdbc 驱动包。
3、对扩容缩容的表所有节点数据进行备份，以便迁移失败后的数据恢复。

**修改动作：**

编辑newSchema.xml 和 newRule.xml
配置conf/migrateTables.properties
修改bin/dataMigrate.sh，执行dataMigrate.sh

注意前方坑位【坑坑坑】：
1，一旦执行数据是不可逆的
2，只能支持分片表的扩缩容  
3，分片规则必须一致，只能节点扩或者缩



**案例：**

```text
这里使用的取模分片表sharding-by-mod
迁移前数据:
    dn0 3,6
    dn1 1,4
    dn3 2,5
迁移后数据:
    dn0 2,4,6
    dn1 1,3,5
```

- 1、复制schema.xml、rule.xml 并重命名为newSchema.xml、newRule.xml 放于conf 目录下。
- 2、修改newSchema.xml 和newRule.xml 配置文件为扩容缩容后的mycat配置参数（表的节点数、数据源、路由规则）。
  - 注意：只有节点变化的表才会进行迁移。仅分片配置变化不会迁移。

```xml
#newSchema.xml
<table name="sharding_by_mod" dataNode="dn1,dn2,dn3" rule="qs-sharding-by-mod" />
改为：
<table name="sharding_by_mod" dataNode="dn1,dn2" rule="qs-sharding-by-mod" />


#newRule.xml 修改count 个数
<function name="qs-sharding-by-mod-long" class="io.mycat.route.function.PartitionByMod">
	<property name="count">2</property>
</function>
```

- 3、修改conf 目录下的migrateTables.properties 配置文件，告诉工具哪些表；需要进行扩容或缩容,没有出现在此配置文件的schema 表不会进行数据迁移，格式：

  - 1）不迁移的表，不要修改dn 个数，否则会报错。

  - 2）ER 表，因为只有主表有分片规则，子表不会迁移。

  - ```properties
    text_db=sharding-by-mod
    ```

- 4、dataMigrate.sh 中这个必须要配置
  通过命令"find / -name mysqldump" 查找mysqldump 路径为
  "/usr/bin/mysqldump"，指定#mysql bin 路径为"/usr/bin/"

  - ```properties
    #mysql bin 路径
    RUN_CMD="$RUN_CMD -mysqlBin= /usr/bin/"
    ```

- 5、停止mycat 服务

- 6、执行执行bin/ dataMigrate.sh 脚本
          注意：必须要配置Java 环境变量，不能用openjdk

- 7 、脚本执行完成， 如果最后的数据迁移验证通过， 就可以将之前的newSchema.xml 和newRule.xml 替换之前的schema.xml 和rule.xml 文件，并重启mycat 即可。



注意事项：
1）保证分片表迁移数据前后路由规则一致（取模——取模）。
2）保证分片表迁移数据前后分片字段一致。
3）全局表将被忽略。
4）不要将非分片表配置到migrateTables.properties 文件中。
5）暂时只支持分片表使用MySQL 作为数据源的扩容缩容。
		migrate 限制比较多，还可以使用mysqldump。



## 8.2 MysqlDump方式

​		系统第一次上线，把单张表迁移到Mycat，也可以用mysqldump。

**mysql导出：**

```bash
root >mysqldump -uroot -p123456 -h127.0.0.1 -P3306 -c -t --skip-extended-insert gpcat > mysql-1017.sql

-c 代表带列名
-t 代表只要数据，不要建表语句
--skip-extended-insert 代表生成多行insert（mycat childtable 不支持多行插入

//其他导入方式：
load data local infile '/mycat/customer.txt' into table customer;
source sql '/mycat/customer.sql';
```

**Mycat 导入：**

```bash
root >mysql -uroot -p123456 -h127.0.0.1 -P8066 catmall < mysql-1017.sql
```



# 9. 调试入口

**debug 方式启动main 方法**
	`Mycat-Server-1.6.5-RELEASE\src\main\java\io\mycat\MycatStartup.java`
**连接入口：**
	`io.mycat.net.NIOAcceptor#accept`
**SQL 入口：**
	`io.mycat.server.ServerQueryHandler#query`