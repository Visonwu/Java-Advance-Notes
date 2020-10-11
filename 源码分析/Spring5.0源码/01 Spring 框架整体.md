

# 1.Spring5 系统架构

​        Spring 总共大约有 20 个模块，由 1300 多个不同的文件构成。而这些组件被分别整合在核心容器（Core
Container）、AOP（Aspect Oriented Programming）和设备支持（Instrumentation）、数据访问及集成（Data Access/Integeration）、Web、报文发送（Messaging）、Test，6 个模块集合中。



AOP 的功能完全集成到了 Spring 事务管理、日志和其他各种特性的上下文中。

- AOP 编程的常用场景有：Authentication（权限认证）、Auto Caching（自动缓存处理）、Error Handling（统一错误处理）、Debugging（调试信息输出）、Logging（日志记录）、Transactions（事务处理）等

**架构图：**

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g1dp798fidj20cy09uabx.jpg)

## 1. Spring核心模块

| 模块名称               | 主要功能                                  |
| ---------------------- | ----------------------------------------- |
| spring-core            | 依赖注入IOC与DI的最基本实现               |
| spring-beans           | Bean工厂与Bean的装配                      |
| spring-context         | 定义基础的Spring的Context上下文即IOC容器  |
| spring-context-support | 对Spring IOC容器的扩展支持，以及IOC子容器 |
| spring-context-indexer | Spring的类管理组件和Classpath扫描         |
| spring-expression      | Spring表达式语言                          |

 

## 2.Spring之切面编程

| 模块名称          | 主要功能                                         |
| ----------------- | ------------------------------------------------ |
| spring-aop        | 面向切面编程的应用模块，整合Asm，CGLIb、JDKProxy |
| spring-aspects    | 集成AspectJ，AOP应用框架                         |
| spring-instrument | 动态Class Loading模块                            |

 

## 3.Spring之数据访问与集成

| 模块名称    | 主要功能                                                     |
| ----------- | ------------------------------------------------------------ |
| spring-jdbc | Spring 提供的JDBC抽象框架的主要实现模块，用于简化Spring JDBC操作 |
| spring-tx   | Spring JDBC事务控制实现模块                                  |
| spring-orm  | 主要集成 Hibernate, Java Persistence API (JPA) 和 Java Data Objects (JDO) |
| spring-oxm  | 将Java对象映射成XML数据，或者将XML数据映射成Java对象         |
| spring-jms  | Java Messaging Service能够发送和接收信息                     |

 


## 4.Spring之Web组件

| 模块名称         | 主要功能                                                     |
| ---------------- | ------------------------------------------------------------ |
| spring-web       | 提供了最基础Web支持，主要建立于核心容器之上，通过Servlet或者Listeners来初始化IOC容器。 |
| spring-webmvc    | 实现了Spring MVC（model-view-Controller）的Web应用。         |
| spring-websocket | 主要是与Web前端的全双工通讯的协议。                          |
| spring-webflux   | 一个新的非堵塞函数式 Reactive Web 框架，可以用来建立异步的，非阻塞，事件驱动的服务 |



 ## 5.Spring之通信报文

| 模块名称         | 主要功能                                                     |
| ---------------- | ------------------------------------------------------------ |
| spring-messaging | 从Spring4开始新加入的一个模块，主要职责是为Spring 框架集成一些基础的报文传送应用。 |

 	

## 6.Spring之集成测试

| 模块名称    | 主要功能               |
| ----------- | ---------------------- |
| spring-test | 主要为测试提供支持的。 |





 ## 7.Spring之集成兼容

| 模块名称             | 主要功能                                                 |
| -------------------- | -------------------------------------------------------- |
| spring-framework-bom | Bill of Materials.解决Spring的不同模块依赖版本不同问题。 |





# 2. Spring 模块依赖关系



![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g1dpq1db4hj20kf0bj0u0.jpg)



# 3.Spring的版本命名规则

## 3.1 语义化版本命名规则

使用 X.Y.Z命名

| 序号 | 格式要求 | 说明                                                         |
| ---- | -------- | ------------------------------------------------------------ |
| X    | 非负整数 | 表示主版本号(Major)，当 API 的兼容性变化时，X 需递增。       |
| Y    | 非负整数 | 表示次版本号(Minor)，当增加功能时(不影响API 的兼容性)，Y 需递增。 |
| Z    | 非负整数 | 表示修订号（Patch），当做 Bug 修复时(不影响 API 的兼容性)，Z 需递增。 |



## 3.2 常用软件修饰词

| 描述方式     | 说明   | 含义                                                        |
| ------------ | ------ | ----------------------------------------------------------- |
| Snapshot     | 快照版 | 尚不不稳定、尚处于开发中的版本                              |
| Alpha        | 内部版 | 严重缺陷基本完成修正并通过复测，但需要完整的功能测试        |
| Beta         | 测试版 | 相对alpha有很大的改进，消除了严重的错误，但还是存在一些缺陷 |
| RC           | 终测版 | Release Candidate（最终测试），即将作为正式版发布。         |
| Demo         | 演示版 | 只集成了正式版部分功能升级，无法升级                        |
| SP           | SP1    | 是service pack的意思表示升级包，相信大家在windows中都见过。 |
| Release      | 稳定版 | 功能相对稳定，可以对外发行，但有时间限制                    |
| Trial        | 试用版 | 试用版，仅对部分用户发行                                    |
| Full Version | 完整版 | 即正式版，已发布。                                          |
| Unregistered | 未注册 | 有功能或时间限制的版本                                      |
| Standard     | 标准版 | 能满足正常使用的功能的版本                                  |
| Lite         | 精简版 | 只含有正式版的核心功能                                      |
| Enhance      | 增强版 | 正式版，功能优化的版本                                      |
| Ultimate     | 旗舰版 | 在标配版本升级体验感更好的版本                              |
| Professional | 专业版 | 针对更高要求功能，专业性更强的使用群体发行的版本            |
| Free         | 自由版 | 自由免费使用的版本                                          |
| Upgrade      | 升级版 | 有功能增强或修复已知bug                                     |
| Restail      | 零售版 | 单独发售                                                    |
| Cardware     | 共享版 | 公用许可证（IOS签证）                                       |
| LTS          | 维护版 | 该版本需要长期维护                                          |

   

## 3.3 Spring版本命名规则

| 描述方式 | 说明     | 含义                                                         |
| -------- | -------- | ------------------------------------------------------------ |
| Snapshot | 快照版   | 尚不不稳定、尚处于开发中的版本                               |
| Release  | 稳定版   | 功能相对稳定，可以对外发行，但有时间限制                     |
| GA       | 正式版   | 代表广泛可用的稳定版(General Availability)                   |
| M        | 里程碑版 | (M是Milestone的意思）具有一些全新的功能或是具有里程碑意义的版本。 |
| RC       | 终测版   | Release Candidate（最终测试），即将作为正式版发布。          |


# 4.Spring源码下载编译

spirng5.0使用gradle管理

步骤一：安装gradle和配置环境

步骤二：github上下载spring源码

步骤三：编译源码,cmd 切到 spring-framework-5.0.2.RELEASE 目录，运行 gradlew.bat

步骤四：使用IDEA导入该项目



可能出现的坑：

```java
报错：C:\Program Files\Java\jdk1.8.0_191' but was: 'C:\Program Files\Java\jdk1.8.0

```

解决办法：

通过%JAVA_HOME%/bin中把tools.jar包改为tools.jar.bak，然后重新用gradle 的refresh就可