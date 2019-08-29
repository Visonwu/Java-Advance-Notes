

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

