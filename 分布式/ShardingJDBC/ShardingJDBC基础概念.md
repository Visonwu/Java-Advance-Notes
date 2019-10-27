

参考：<https://shardingsphere.apache.org/document/current/cn/quick-start/sharding-jdbc-quick-start/>

数据源选择的解决方案层次： 

DAO：AbstractRoutingDataSource 

ORM：MyBatis 插件 

JDBC：Sharding-JDBC 

Proxy：Mycat、Sharding-Proxy 

Server：特定数据库或者版本

## 1.发展历史

​			它是从当当网的内部架构 ddframe 里面的一个分库分表的模块脱胎出来的，用来解 决当当的分库分表的问题，把跟业务相关的敏感的代码剥离后，就得到了 Sharding- JDBC。它是一个工作在客户端的分库分表的解决方案。 

DubboX，Elastic-job 也是当当开源出来的产品。

- 2018 年 5 月，因为增加了 Proxy 的版本和 Sharding-Sidecar（尚未发布）， Sharding-JDBC 更名为 Sharding Sphere，从一个客户端的组件变成了一个套件。 

- 2018 年 11 月，Sharding-Sphere 正式进入 Apache 基金会孵化器，这也是对 Sharding-Sphere 的质量和影响力的认可。不过现在还没有毕业（名字带 incubator）， 一般我们用的还是 io.shardingsphere 的包。

因为更名后和捐献给 Apache 之后的 groupId 都不一样，在引入依赖的时候千万要 注意。主体功能是相同的，但是在某些类的用法上有些差异，如果要升级的话 import 要 全部修改，有些类和方法也要修改。



## 2.基本特性

​		定位为轻量级 Java 框架，在 Java 的 JDBC 层提供的额外服务。 它使用客户端直连数据 库，以 jar 包形式提供服务，无需额外部署和依赖，可理解为增强版的 JDBC 驱动，完 全兼容 JDBC 和各种 ORM 框架。



​		在 Sharding-Sphere 里面同样提供了代理 Proxy 的版本，跟 Mycat 的作用是一样的。Sharding-Sidecar 是一个 Kubernetes 的云原生数据库代理，正在开发中。

![KDlfds.png](https://s2.ax1x.com/2019/10/26/KDlfds.png)

### 2.1 功能

​	分库分表后的几大问题：跨库关联查询、分布式事务、排序翻页计算、全局主键。

#### 1）数据分片

1、分库& 分表
2、读写分离
https://shardingsphere.apache.org/document/current/cn/features/read-write-split/
3、分片策略定制化
4、无中心化分布式主键（包括UUID、雪花、LEAF）
https://shardingsphere.apache.org/document/current/cn/features/sharding/other-features/key-generator/



#### 2） 分布式事务

https://shardingsphere.apache.org/document/current/cn/features/transaction/
1、标准化事务接口
2、XA 强一致事务
3、柔性事务



#### 3）核心概念

https://shardingsphere.apache.org/document/current/cn/features/sharding/concept/sql/
逻辑表、

真实表、

分片键、

数据节点

动态表（类似Mycat的分片表）

广播表（类似Mycat中的全局表）

绑定表（类似Mycat中的ER表）

#### 4）使用规范

不支持的SQL：
https://shardingsphere.apache.org/document/current/cn/features/sharding/use-norms/sql/
分页的说明：
https://shardingsphere.apache.org/document/current/cn/features/sharding/use-norms/pagination/

