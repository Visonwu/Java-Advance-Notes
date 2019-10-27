

## 1. 工作流程

**内核剖析:**https://shardingsphere.apache.org/document/current/cn/features/sharding/principle/

Sharding-JDBC 的原理总结起来很简单：
	SQL 解析=> 执行器优化=> SQL 路由=> SQL 改写=> SQL 执行=> 结果归并。



### 1.1 SQL 解析

​		SQL 解析主要是词法和语法的解析。目前常见的SQL 解析器主要有fdb，jsqlparser和Druid。Sharding-JDBC1.4.x 之前的版本使用Druid 作为SQL 解析器。从1.5.x 版本开始，Sharding-JDBC 采用完全自研的SQL 解析引擎。

### 1.2 SQL 路由

​		SQL 路由是根据分片规则配置以及解析上下文中的分片条件，将SQL 定位至真正的数据源。它又分为直接路由、简单路由和笛卡尔积路由。

​	直接路由，使用Hint 方式。

​	Binding 表是指使用同样的分片键和分片规则的一组表， 也就是说任何情况下，Binding 表的分片结果应与主表一致。例如：order 表和order_item 表，都根据order_id分片，结果应是order_1 与order_item_1 成对出现。这样的关联查询和单表查询复杂度和性能相当。如果分片条件不是等于，而是BETWEEN 或IN，则路由结果不一定落入单库（表），因此一条逻辑SQL 最终可能拆分为多条SQL 语句。

​	笛卡尔积查询最为复杂，因为无法根据Binding 关系定位分片规则的一致性，所以非Binding 表的关联查询需要拆解为笛卡尔积组合执行。查询性能较低，而且数据库连接数较高，需谨慎使用。



### 1.3 SQL 改写

​	例如：将逻辑表名称改成真实表名称，优化分页查询等

### 1.4 SQL 执行

​	因为可能链接到多个真实数据源， Sharding -JDBC 将采用多线程并发执行SQL。

### 1.5 结果归并

​	例如数据的组装、分页、排序等等。



## 2.实现原理

​		如果说带Sharding 的类要替换JDBC 的对象，那么一定要找到创建和调用他们的地方。ShardingDataSource 我们不说了，系统启动的时候就创建好了。
问题就在于， 我们是什么时候用ShardingDataSource 获取一个ShardingConnection 的？

我们以整合了MyBatis 的项目为例。MyBatis 封装了JDBC 的核心对象， 那么在MyBatis 操作JDBC 四大对象的时候，就要替换成Sharding-JDBC 的四大对象。

没有看过MyBatis 源码的同学一定要去看看。我们的查询方法最终会走到SimpleExecutor 的doQuery()方法，这个是我们的前提知识，那我们直接在doQuery()打断点。

doQuery()方法里面调用了prepareStatement()创建连接

```java
private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    Connection connection = getConnection(statementLog);
    stmt = handler.prepare(connection, transaction.getTimeout());
    handler.parameterize(stmt);
    return stmt;
}
```

它经过以下两个方法，返回了一个ShardingConnection。
	DataSourceUtil.fetchConnection()
	Connection con = dataSource.getConnection();
基于这个ShardingConnection，最终得到一个ShardingStatement

```java
stmt = handler.prepare(connection, transaction.getTimeout());
```

接下来

```java
return handler.query(stmt, resultHandler);
```

再调用了的ShardingStatement 的execute()

```java
public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    ps.execute();
    return resultSetHandler.handleResultSets(ps);
}
```

最终调用的是ShardingPreparedStatement 的execute 方法。

```java
public boolean execute() throws SQLException {
    try {
        clearPrevious();
        sqlRoute();
        initPreparedStatementExecutor();
        return preparedStatementExecutor.execute();
    } finally {
        refreshTableMetaData(connection.getShardingContext(), routeResult.getSqlStatement());
        clearBatch();
    }
}
```

SQL 的解析路由就是在这一步完成的。





## 3.Sharding-Proxy相关介绍

下载地址：
https://github.com/sharding-sphere/sharding-sphere-doc/raw/master/dist/sharding-proxy-3.0.0.tar.gz

- lib 目录就是sharding-proxy 核心代码，以及依赖的JAR 包；
- bin 目录就是存放启停脚本的；
- conf 目录就是存放所有配置文件，包括sharding-proxy 服务的配置文件、数据源以及sharding 规则配置文件和项目日志配置文件。
- Linux 运行start.sh 启动（windows 用start.bat），默认端口3307
- 需要的自定义分表算法，只需要将它编译成class 文件，然后放到conf 目录下，也可以打成jar 包放在lib 目录下。



## 4.和MyCat对比

```text
			Sharding-JDBC				 Mycat
工作层面		JDBC协议							MySQL 协议/JDBC 协议
运行方式		Jar 包，客户端					  独立服务，服务端
开发方式		代码/配置改动						 连接地址（数据源）
运维方式		无								 管理独立服务，运维成本高
性能			多线程并发按操作，性能高		 	   独立服务+网络开销，存在性能损失风险
功能范围		协议层面						   包括分布式事务、数据迁移等
适用操作		OLTP 							  OLTP+OLAP
支持数据库		基于JDBC 协议的数据库				MySQL 和其他支持JDBC 协议的数据库
支持语言		Java 项目中使用						支持JDBC 协议的语言
```



​		从易用性和功能完善的角度来说，Mycat 似乎比Sharding-JDBC 要好，因为有现成的分片规则，也提供了4 种ID 生成方式，通过注解可以支持高级功能，比如跨库关联查询。

建议：小型项目，分片规则简单的项目可以用Sharding-JDBC。大型项目，可以用Mycat。