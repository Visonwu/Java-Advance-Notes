

## 1.原生JDBC

**直接引入依赖**

```xml
<dependencies>
    <dependency>
        <groupId>io.shardingsphere</groupId>
        <artifactId>sharding-jdbc-core</artifactId>
        <version>3.1.0</version>
    </dependency>
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>commons-dbcp</groupId>
        <artifactId>commons-dbcp</artifactId>
        <version>1.3</version>
    </dependency>
</dependencies>
```

**建表sql**

```sql
CREATE TABLE `order0` (
  `order_id` bigint(32) NOT NULL,
  `user_id` bigint(32) NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `total_price` int(10) NOT NULL,
  PRIMARY KEY (`order_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE `order1` (
  `order_id` bigint(32) NOT NULL,
  `user_id` bigint(32) NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `total_price` int(10) NOT NULL,
  PRIMARY KEY (`order_id`) USING BTREE
) ENGINE=InnoDB
```

**代码测试**

```java
package com.vison;
public class ShardingJDBCTest {

    public static void main(String[] args) throws SQLException {

        // 配置真实数据源
        Map<String, DataSource> dataSourceMap = new HashMap<>();
        // 配置第一个数据源
        BasicDataSource dataSource1 = new BasicDataSource();
        dataSource1.setDriverClassName("com.mysql.jdbc.Driver");
        dataSource1.setUrl("jdbc:mysql://localhost:3306/shard0?serverTimezone=Hongkong");
        dataSource1.setUsername("root");
        dataSource1.setPassword("root");
        dataSourceMap.put("ds0", dataSource1);

        // 配置第二个数据源
        BasicDataSource dataSource2 = new BasicDataSource();
        dataSource2.setDriverClassName("com.mysql.jdbc.Driver");
        dataSource2.setUrl("jdbc:mysql://localhost:3306/shard1?serverTimezone=Hongkong");
        dataSource2.setUsername("root");
        dataSource2.setPassword("root");
        dataSourceMap.put("ds1", dataSource2);

        // 配置Order 表规则
        TableRuleConfiguration orderTableRuleConfig = new TableRuleConfiguration();
        orderTableRuleConfig.setLogicTable("order");
        orderTableRuleConfig.setActualDataNodes("ds${0..1}.order${0..1}");
        // 配置分库+ 分表策略
        orderTableRuleConfig.setDatabaseShardingStrategyConfig(
                new InlineShardingStrategyConfiguration("order_id",
                "ds${order_id % 2}"));
        orderTableRuleConfig.setTableShardingStrategyConfig(
                new InlineShardingStrategyConfiguration("order_id",
                "order${order_id % 2}"));
        // 配置分片规则
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();
        shardingRuleConfig.getTableRuleConfigs().add(orderTableRuleConfig);

        Map<String, Object> map = new HashMap<>();
        // 获取数据源对象
        DataSource dataSource = ShardingDataSourceFactory
                .createDataSource(dataSourceMap, shardingRuleConfig, map, new Properties());


        //insert(dataSource);
        select(dataSource);
    }

	//查询
    private static void select(DataSource dataSource) {
        String sql = "SELECT * from order WHERE order_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, 1);
            System.out.println();
            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    // %2 结果，路由到shard1.order1
                    System.out.println("order_id---------" + rs.getInt(1));
                    System.out.println("user_id---------" + rs.getInt(2));
                    System.out.println("create_time---------" + rs.getTimestamp(3));
                    System.out.println("total_price---------" + rs.getInt(4));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
	//插入，这个是会插入到shard1的order1表中
    public static void insert(DataSource dataSource){

        String sql = "INSERT INTO order(order_id,user_id,total_price) values (1,2,100)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            boolean execute = preparedStatement.execute();
            System.out.println("执行结果："+execute);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

**总结：**

- ShardingRuleConfiguration 可以包含多个TableRuleConfiguration（多张表），也可以设置默认的分库和分表策略。
- 每个TableRuleConfiguration 可以针对表设置ShardingStrategyConfiguration，包括分库分分表策略。
- ShardingStrategyConfiguration 有5 种实现（标准、行内、复合、Hint、无）。
- ShardingDataSourceFactory 利用ShardingRuleConfiguration 创建数据源；有了数据源，就可以走JDBC 的流程了。



## 2.Spring中使用

​		先来总结一下，因为我们要使用Sharding-JDBC 去访问数据库，所以我们不再使用ORM 框架或者容器去定义数据源，而是注入Sharding-JDBC 自定义的数据源，这样才能保证动态选择数据源的实现。

​		第二个，因为Sharding-JDBC 是工作在客户端的，所以我们要在客户端配置分库分表的策略。跟Mycat 不一样的是，**Sharding-JDBC 没有内置各种分片策略和算法**，需要我们通过表达式或者自定义的配置文件实现。我们创建的数据源中包含了分片的策略。

​	总体上，需要配置的就是这两个，数据源和分片策略，当然分片策略又包括分库的策略和分表的策略。

配置的方式是多种多样的。
https://shardingsphere.apache.org/document/current/cn/manual/sharding-jdbc/configuration/config-java/
位置：4.用户手册——4.1 Sharding-JDBC——4.1.2 配置手册



### 2.1 Java-config中配置

参考：<https://github.com/Visonwu/Java-Sound-Code/shard-java-config>



### 2.2 Spring-boot配置

 参考：<https://github.com/Visonwu/Java-Sound-Code/shard-properties>

<https://github.com/Visonwu/Java-Sound-Code/shard-yml>

**properties配置：**

​		第二种是直接使用Spring Boot 的application.properties 来配置，这个要基于starter 模块，org.apache.shardingsphere 的包还没有starter，只有io.shardingsphere的包有starter。

​		把数据源和分库分表策略都配置在properties 文件中。这种方式配置比较简单，但是不能实现复杂的分片策略，不够灵活。



**yml配置：**

​			第三种是使用Spring Boot 的yml 配置（shardingjdbc.yml），也要依赖starter模块。当然我们也可以结合不同的配置方式，比如把分片策略放在JavaConfig 中，数据源配置在yml 中或properties 中。





## 3.分片策略

​	Sharding-JDBC 中的分片策略有两个维度：**分库（数据源分片）策略和分表策略。**

​		分库策略表示数据路由到的物理目标数据源，分表分片策略表示数据被路由到的目标表。分表策略是依赖于分库策略的，也就是说要先分库再分表，当然也可以不分库只分表。

​		跟Mycat 不一样，Sharding-JDBC 没有提供内置的分片算法，而是通过抽象成接口，让开发者自行实现，这样可以根据业务实际情况灵活地实现分片。



### 3.1 分片策略

​			包含分片键和分片算法，分片算法是需要自定义的。可以用于分库，也可以用于分表。

Sharding-JDBC 提供了5 种分片策略，这些策略全部继承自ShardingStrategy：

- ComplexShardingStrategy
- StandardShardingStrategy
- HintShardingStrategy
- InlineShardingStrategy
- NoneShardingStrategy



#### 1） 行表达式分片策略

​		对应InlineShardingStrategy 类。只支持单分片键，提供对 `=`和`IN `操作的支持。行内表达式的配置比较简单。

​         官网：https://shardingsphere.apache.org/document/current/cn/features/sharding/other-features/inline-expression/

```text
例如：
${begin..end}表示范围区间

${[unit1, unit2, unit_x]}表示枚举值

t_user_$->{u_id % 8} 表示t_user 表根据u_id 模8，而分成8 张表，表名称为
t_user_0 到t_user_7。

行表达式中如果出现连续多个${ expression }或$->{ expression }表达式，整个表
达式最终的结果将会根据每个子表达式的结果进行笛卡尔组合。
例如，以下行表达式：
${['db1', 'db2']}_table${1..3}
最终会解析为：
db1_table1, db1_table2, db1_table3,
db2_table1, db2_table2, db2_table3
```



#### 2）标准分片策略

​	对应StandardShardingStrategy 类。
​		标准分片策略只支持单分片键， 提供了提供`PreciseShardingAlgorithm` 和`RangeShardingAlgorithm` 两个分片算法，分别对应于SQL 语句中的 `=, IN 和BETWEEN AND`。

​			如果要使用标准分片策略，必须要实现PreciseShardingAlgorithm，用来处理=和IN 的分片。RangeShardingAlgorithm 是可选的。如果没有实现，SQL 语句会发到所有的数据节点上执行。



#### 3） 复合分片策略
​		比如：根据日期和ID 两个字段分片，每个月3 张表，先根据日期，再根据ID 取模。对应ComplexShardingStrategy 类。可以支持等值查询和范围查询。

复合分片策略支持多分片键，提供了ComplexKeysShardingAlgorithm，分片算法需要自己实现

#### 4） Hint 分片策略
对应HintShardingStrategy。通过Hint 而非SQL 解析的方式分片的策略。有点类似于Mycat 的指定分片注解。
https://shardingsphere.apache.org/document/current/cn/manual/sharding-jdbc/usage/hint/

#### 5） 不分片策略
​		对应NoneShardingStrategy。不分片的策略。



### 3.2 分片算法
​			创建了分片策略之后，需要进一步实现分片算法。Sharding-JDBC 目前提供4 种分片算法。

#### 1） 精确分片算法
​		对应PreciseShardingAlgorithm，用于处理使用单一键作为分片键的=与IN 进行分片的场景。需要配合StandardShardingStrategy 使用

#### 2） 范围分片算法

​		对应RangeShardingAlgorithm，用于处理使用单一键作为分片键的BETWEEN，AND 进行分片的场景。需要配合StandardShardingStrategy 使用。如果不配置范围分片算法，范围查询默认会路由到所有节点。

#### 3） 复合分片算法

​	对应ComplexKeysShardingAlgorithm，用于处理使用多键作为分片键进行分片的场景，包含多个分片键的逻辑较复杂，需要应用开发者自行处理其中的复杂度。需要配合ComplexShardingStrategy 使用。

#### 4） Hint 分片算法
​		对应HintShardingAlgorithm，用于处理使用Hint 行分片的场景。需要配合HintShardingStrategy 使用。
https://shardingsphere.apache.org/document/current/cn/manual/sharding-jdbc/usage/hint/



## 4.分布式事务

### 4.1 事务概述

​		https://shardingsphere.apache.org/document/current/cn/features/transaction/

为什么要有分布式事务？
	XA 模型的不足：需要锁定资源
	柔性事务



### 4.2 两阶段事务XA

 参考：https://github.com/Visonwu/Java-Sound-Code/shard-yml

​					com.vison.TransactionTest

依赖：

```xml
<dependency>
    <groupId>io.shardingsphere</groupId>
    <artifactId>sharding-transaction-2pc-xa</artifactId>
    <version>3.1.0</version>
</dependency>
```

默认是用**atomikos** 实现的

在Service 类，或者方法上加上注解

```java
@ShardingTransactionType(TransactionType.XA)  //其他事务类型：Local、BASE
@Transactional(rollbackFor = Exception.class)

```


模拟在两个节点上操作，id=12673、id=12674 路由到两个节点，第二个节点插入
两个相同对象，发生主键冲突异常，会发生回滚。
XA 实现类：
	XAShardingTransactionManager——XATransactionManager——AtomikosTransactionManager



### 4.3 柔性事务Saga

​	ShardingSphere 的柔性事务已通过第三方SPI 实现Saga 事务，Saga 引擎使用Servicecomb-Saga

参考官方文章：[分布式事务在Sharding-sphere中的实现](https://mp.weixin.qq.com/s?__biz=MzIwMzc4NDY4Mw==&mid=2247486704&idx=1&sn=edc76d838cbed006a107573732ba271b&chksm=96cb6474a1bced623e55db47e2d2fa5b7493eff5730bc3a6d0a5f1c4b08d2cc27fcccd61513e&mpshare=1&scene=1&srcid=1022xTpRYjxUbdUEZPgLpvV6&sharer_sharetime=1571725596151&sharer_shareid=035a2b61dca579f53be4eae95018f9cf&key=07d257d046da7c4bd15ab473b579736eb78a263ceaf74f267306f034ad9d436c49c55f92ae9d7ab24b1fd410a4de62b2ae1c6f968405882d59633162adc18476f01c6db048de3071e6c2549918a20836&ascene=1&uin=MTE5MTQzNjA0MA%3D%3D&devicetype=Windows+7&version=62070152&lang=zh_CN&pass_ticket=Q2%2FOQotkJiA%2F%2FFksN%2B2ivqC0PU2wLceaL8QDpYYehC5L5uxZ69kRygI53NXEXcdU)

```xml
<dependency>
    <groupId>io.shardingsphere</groupId>
    <artifactId>sharding-transaction-2pc-spi</artifactId>
    <version>3.1.0</version>
</dependency>
```



### 4.4 柔性事务Seata

https://github.com/seata/seata
https://github.com/seata/seata-workshop
https://mp.weixin.qq.com/s/xfUGep5XMcIqRTGY3WFpgA
GTS 的社区版本叫Fescar（Fast & Easy Commit And Rollback），Fescar 改名后叫Seata AT（Simple Extensible Autonomous Transaction Architecture）

需要额外部署Seata-server 服务进行分支事务的协调。
官方的demo 中有一个例子：
https://github.com/apache/incubator-shardingsphere-example
incubator-shardingsphere-example-dev\sharding-jdbc-example\transaction
-example\transaction-base-seata-raw-jdbc-example



## 5. 全局ID

[参考官网网址](https://shardingsphere.apache.org/document/current/cn/features/sharding/other-features/key-generator/)

​		在我的GitHub目录中的 shard-yml 中，使用key-generator-column-name 配置，生成了一个18 位的ID。

**Properties 配置**：

```properties
sharding.jdbc.config.sharding.default-key-generator-class-name=
sharding.jdbc.config.sharding.tables.t_order.keyGeneratorColumnName=
sharding.jdbc.config.sharding.tables.t_order.keyGeneratorClassName=
```

**Java config 配置：**

```java
tableRuleConfig.setKeyGeneratorColumnName("order_id");
tableRuleConfig.setKeyGeneratorClass("io.shardingsphere.core.keygen.DefaultKeyGenerator");
```

keyGeneratorColumnName：指定需要生成ID 的列
KeyGenerotorClass：指定生成器类，默认是DefaultKeyGenerator.java，里面使用了雪花算法。

