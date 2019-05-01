

如下可以基于这个来debug源码

```java
public class TestMyBatis {

    public static void main(String[] args) throws IOException {
        String resource = "mybatis-config.xml";
        InputStream inputStream = Resources. getResourceAsStream (resource);
        SqlSessionFactory sqlSessionFactory = new
                SqlSessionFactoryBuilder().build(inputStream);
        SqlSession session = sqlSessionFactory.openSession();
        try {
            BlogMapper mapper = session.getMapper(BlogMapper. class);
            Blog blog = mapper.selectBlogById(1);
            System. out .println(blog);
        } finally {
            session.close();
        }
    }
}
```



# 1.Mybatis工作流程

- 1) 解析配置文件

- 2) 创建工厂类

- 3) 创建会话

- 4) 会话操作数据库

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g2ju8qug1vj20d20alwen.jpg)

# 2.Mybatis 架构

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g2jubhyuvdj20em08bgm9.jpg)



## 2.1 接口层
​	首先接口层是我们打交道最多的。核心对象是 SqlSession，它是上层应用和 MyBatis打交道的桥梁，SqlSession 上定义了非常多的对数据库的操作方法。接口层在接收到调用请求的时候，会调用核心处理层的相应模块来完成具体的数据库操作。



## 2.2 核心处理层
​	接下来是核心处理层。既然叫核心处理层，也就是跟数据库操作相关的动作都是在这一层完成的。
核心处理层主要做了这几件事：

- 1）把接口中传入的参数解析并且映射成 JDBC 类型；

- 2）解析 xml 文件中的 SQL 语句，包括插入参数，和动态 SQL 的生成；

- 3）执行 SQL 语句；

- 4）处理结果集，并映射成 Java 对象

插件也属于核心层，这是由它的工作方式和拦截的对象决定的



## 2.3 基础支持层
​     最后一个就是基础支持层。基础支持层主要是一些抽取出来的通用的功能（实现复用），用来支持核心处理层的功能。比如数据源、缓存、日志、xml 解析、反射、IO、事务等等这些功能。



# 3.缓存

​        缓存是一般的 ORM 框架都会提供的功能，目的就是提升查询的效率和减少数据库的压力。跟 Hibernate 一样，MyBatis 也有一级缓存和二级缓存，并且预留了集成第三方缓存的接口



## 3.1缓存结构

​     MyBatis 跟缓存相关的类都在 cache 包里面，其中有一个 Cache 接口，只有一个默认的实现类 `PerpetualCache`，它是用 HashMap 实现的。

除此之外，还有很多的装饰器，通过这些装饰器可以额外实现很多的功能：回收策略、日志记录、定时刷新等等。 例如`cache/decorators`包中的`BlockingCache`、`LruCache`、`ScheduledCache`。

但是无论怎么装饰，经过多少层装饰，最后使用的还是基本的实现类（默认`PerpetualCache`）

## 3.2缓存分类

所有的缓存实现类总体上可分为三类：**基本缓存、淘汰算法缓存、装饰器缓存。**

| 缓存实现类           | 描述             | 作用                                                         | 装饰条件                                         |
| -------------------- | ---------------- | ------------------------------------------------------------ | ------------------------------------------------ |
| 基本缓存             | 缓存基本实现类   | 默认是 PerpetualCache，也可以自定义比如RedisCache、EhCache 等，具备基本功能的缓存类 | 无                                               |
| LruCache             | Lru策略缓存      | 当缓存到达上限时候，删除最近最少使用的缓存（Least Recently Use） | eviction="LRU"（默认）                           |
| FifoCache            | FIFO策略缓存     | 当缓存到达上限时候，删除最先入队的缓存                       | eviction="FIFO"                                  |
| SoftCache、WeakCache | 带清理策略的缓存 | 通过 JVM 的软引用和弱引用来实现缓存，当 JVM内存不足时，会自动清理掉这些缓存，基于
SoftReference 和 WeakReference | eviction="SOFT"<br/>eviction="WEAK"              |
| LoggingCache         | 带日志功能的缓存 | 比如：输出缓存命中率                                         | 基本                                             |
| SynchronizedCache    | 同步缓存         | 基于 synchronized 关键字实现，解决并发问题                   | 基本                                             |
| BlockingCache        | 阻塞缓存         | 通过在 get/put 方式中加锁，保证只有一个线程操作缓存，基于 Java 重入锁实现 | blocking=true                                    |
| SerializedCache      | 支持序列化缓存   | 将对象序列化以后存到缓存中，取出时反序列化                   | readOnly=false（默认)                            |
| ScheduledCache       | 定时调度缓存     | 在进行 get/put/remove/getSize 等操作前，判断缓存时间是否超过了设置的最长缓存时间（默认是一小时），如果是则清空缓存--即每隔一段时间清空一次缓存 | flushInterval 不为空                             |
| TransactionalCache   | 事务缓存         | 在二级缓存中使用，可一次存入多个缓存，移除多个缓存           | 在TransactionalCacheManager 中用 Map维护对应关系 |




## 3.3 **一级缓存**

​        一级缓存也叫本地缓存，MyBatis 的一级缓存是在会话（SqlSession）层面进行缓存的。MyBatis 的一级缓存是默认开启的，不需要任何的配置。

首先我们必须去弄清楚一个问题，在 MyBatis 执行的流程里面，涉及到这么多的对象，那么缓存 PerpetualCache 应该放在哪个对象里面去维护？如果要在同一个会话里面共享一级缓存，这个对象肯定是在 SqlSession 里面创建的，作为 SqlSession 的一个属性。

​     `DefaultSqlSession` 里面只有两个属性，`Configuration` 是全局的，所以缓存只可能放在 Executor 里面维护——`SimpleExecutor/ReuseExecutor/BatchExecutor` 的父类`BaseExecutor` 的构造函数中持有了 `PerpetualCache`

![](http://ww1.sinaimg.cn/mw690/b8a27c2fgy1g2lky1tsi2j20gv07eq3j.jpg)

### 1）一级缓存不足

​	使用一级缓存的时候，因为缓存不能跨会话共享，不同的会话之间对于相同的数据可能有不一样的缓存。在有多个会话或者分布式环境下，会存在脏数据的问题。如果要解决这个问题，就要用到二级缓存

### 2） 一级缓存怎么命中？

​	`BaseExecutor`中持有`PerpetualCache`引用，每次通过`CacheKey`去查看PerpetualCache中是否有缓存，如果有的话直接返回当前的值，不用去数据库中获取。

### 3） CacheKey怎么构成？

​	需要查看源码是怎么生成的，它是通过计算CacheKey中的update(object)方法生成不同的属性(这里object可以能是参数，方法名很多属性构成)

### 4）一级缓存默认开启，怎么关闭一级缓存？

​	默认设置的一级缓存是`SESSION`的，把他修改成`STATEMENT`级别就可以了

```
<setting name="localCacheScope" value="STATEMENT"/>
```

## 3.4 **二级缓存**

​	二级缓存是用来解决一级缓存不能跨会话共享的问题的，范围是 `namespace` 级别的，可以被多个 SqlSession 共享（只要是同一个接口里面的相同方法，都可以共享），生命周期和应用同步。

思考一个问题：如果开启了二级缓存，二级缓存应该是工作在一级缓存之前，还是在一级缓存之后呢？二级缓存是在哪里维护的呢？

​	作为一个作用范围更广的缓存，它肯定是在 SqlSession 的外层，否则不可能被多个SqlSession 共享。而一级缓存是在 SqlSession 内部的，所以第一个问题，肯定是工作在一级缓存之前，也就是只有取不到二级缓存的情况下才到一个会话中去取一级缓存。第二个问题，二级缓存放在哪个对象中维护呢？ 要跨会话共享的话，SqlSession 本身和它里面的 BaseExecutor 已经满足不了需求了，那我们应该在 BaseExecutor 之外创建一个对象。



​	实际上 MyBatis 用了一个装饰器的类来维护，就是 CachingExecutor。如果启用了二级缓存，MyBatis 在创建 Executor 对象的时候会对 Executor 进行装饰。

> CachingExecutor 对于查询请求，会判断二级缓存是否有缓存结果，如果有就直接返回，如果没有委派交给真正的查询器 Executor 实现类，比如 SimpleExecutor 来执行查询，再走到一级缓存的流程。最后会把结果缓存起来，并且返回给用户。



![](http://ww1.sinaimg.cn/mw690/b8a27c2fgy1g2lmnowg5qj20kx0a141k.jpg)



### 1) 开启二级缓存

第一步：在 mybatis-config.xml 中配置了（可以不配置，默认是 true）：

```xml
<setting name" ="cacheEnabled" value ="true"/>
<!--只要没有显式地设置 cacheEnabled=false，都会用 CachingExecutor 装饰基本的
执行器。-->                                            
```

第二步：在 Mapper.xml 中配置`<cache/>`标签：

``````xml
<!-- 声明这个 namespace 使用二级缓存 -->
<cache type ="org.apache.ibatis.cache.impl.PerpetualCache"
size ="1024" eviction ="LRU" flushInterval ="120000" readOnly =" false" "/>
<!—最多缓存对象个数，默认 1024-->
<!—回收策略-->
<!—自动刷新时间 ms，未配置时只有调用时刷新-->                    
 <!— 默认是 false（安全），改为 true 可读写时，对象必须支持序列化 -->                    
``````

### 2) cache标签属性讲解

| 属性          | 含义                               | 取值                                                         |
| ------------- | ---------------------------------- | ------------------------------------------------------------ |
| type          | 缓存实现类                         | 需要实现 Cache 接口，默认是 PerpetualCache                   |
| size          | 最多缓存对象个数                   | 默认 1024                                                    |
| eviction      | 回收策略<br/>（缓存淘汰算法）      | LRU – 最近最少使用的：移除最长时间不被使用的对象（默认)<br/>FIFO – 先进先出：按对象进入缓存的顺序来移除它们。
SOFT – 软引用：移除基于垃圾回收器状态和软引用规则的对象。

WEAK – 弱引用：更积极地移除基于垃圾收集器状态和弱引用规则的对象 |
| flushInterval | 定时自动清空缓存间隔               | 自动刷新时间，单位 ms，未配置时只有调用时刷新                |
| readOnly      | 是否只读                           | true：只读缓存；会给所有调用者返回缓存对象的相同实例。因此这些对象不能被修改。这提供了很重要的性能优势。<br/>
false：读写缓存；会返回缓存对象的拷贝（通过序列化），不会共享。这会慢一些，但是安全，因此**默认是 false。**
改为 false 可读写时，对象必须支持序列化。 |
| blocking      | 是否使用可重入锁实现缓存的并发控制 | true，会使用 BlockingCache 对 Cache 进行装饰<br/>默认 false  |





### 3） 关闭某个局部方法的二级缓存？
我们可以在单个 Statement ID 上显式关闭二级缓存（默认是 true）：

```xml
<select id" ="selectBlog" resultMap" ="BaseResultMap" useCache="false"/>
```



### 4）哪些方法缓存，一级更新缓存？

> Mapper.xml 配置了`<cache>`之后，select()会被缓存。update()、delete()、insert()会刷新缓存。

思考：如果 cacheEnabled=true，Mapper.xml 没有配置标签，还有二级缓存吗？还会出现 CachingExecutor 包装对象吗？

只要 cacheEnabled=true 基本执行器就会被装饰。有没有配置`<cache>`，决定了在启动的时候会不会创建这个 mapper 的 Cache 对象，最终会影响到 CachingExecutor 的query 方法里面的判断：

```java
if (cache != null) {...}
```



### 5) 事务不提交，二级缓存 不存在

​	因为二级缓存使用 TransactionalCacheManager（TCM）来管理，最后又调用了TransactionalCache的getObject()、putObject和commit()方法，TransactionalCache里面又持有了真正的 Cache 对象，比如是经过层层装饰的 PerpetualCache。在 putObject 的时候，只是添加到了 entriesToAddOnCommit 里面，只有它的commit()方法被调用的时候才会调用 flushPendingEntries()真正写入缓存。

> 它就是在DefaultSqlSession 调用 commit()的时候被调用的。



### 6) 什么时候开启二级缓存?

​	一级缓存默认是打开的，二级缓存需要配置才可以开启。那么我们必须思考一个问题，在什么情况下才有必要去开启二级缓存？

- a. 因为所有的增删改都会刷新二级缓存，导致二级缓存失效，所以适合在查询为主的应用中使用，比如历史交易、历史订单的查询。否则缓存就失去了意义。
- b. 如果多个 namespace 中有针对于同一个表的操作，比如 Blog 表，如果在一个namespace 中刷新了缓存，另一个 namespace 中没有刷新，就会出现读到脏数据的情况。所以，推荐在一个 Mapper 里面只操作单表的情况使用。



### 7) 让多个 namespace 共享一个二级缓存

​	cache-ref 代表引用别的命名空间的 Cache 配置，两个命名空间的操作使用的是同一个 Cache。在关联的表比较少，或者按照业务可以对表进行分组的时候可以使用。

注意：在这种情况下，多个 Mapper 的操作都会引起缓存刷新，缓存的意义已经不大了。



### 8) 第三方缓存 做 二级缓存

除了 MyBatis 自带的二级缓存之外，我们也可以通过实现 Cache 接口来自定义二级缓存。
MyBatis 官方提供了一些第三方缓存集成方式，比如 ehcache 和 redis



例如redis:

引入pom.xml文件

```xml
< dependency>
    < groupId>org.mybatis.caches</ groupId>
    < artifactId>mybatis-redis</ artifactId>
    < version>1.0.0-beta2</ version>
</dependency>
```

Mapper.xml 配置，type 使用 RedisCache：

```xml
<cache type ="org.mybatis.caches.redis.RedisCache"
eviction" ="FIFO" flushInterval" ="60000" size" ="512" readOnly="false"/> 
```

redis.properties 配置：

```properties
host= localhost
port= 6379
connectionTimeout= 5000
soTimeout= 5000
database=0 0
```

