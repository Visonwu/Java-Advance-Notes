参考：http://www.mybatis.org/spring/zh/index.html





# 1.Spring集成Mybatis步骤

**步骤一：导入包mybatis-spring**

```xml
<dependency>
    <groupId>org.mybatis</groupId>
    <artifactId>mybatis-spring</artifactId>
    <version>2.0.0</version>
</dependency>
```

步骤二：然后在 Spring 的 applicationContext.xml 里面配置 SqlSessionFactoryBean，它是用来帮助我们创建会话的，其中还要指定全局配置文件和 mapper 映射器文件的路径

```xml
<bean id" ="sqlSessionFactory" class ="org.mybatis.spring.SqlSessionFactoryBean">
    <property name" ="configLocation" value ="classpath:mybatis-config.xml"></ property>
    <property name" ="mapperLocations" value ="classpath:mapper/*.xml"></ property>
    <property name" ="dataSource" ref ="dataSource"/>
</bean>
```

**步骤三：然后在 applicationContext.xml 配置需要扫描 Mapper 接口的路径**。
​	在 Mybatis 里面有几种方式，

-  第一种是配置一个 MapperScannerConfigurer。

  ```xml
  <bean id" ="mapperScanner" class="org.mybatis.spring.mapper.MapperScannerConfigurer">
  	<property name" ="basePackage" value ="com.ws.vison.crud.dao"/>
  </ bean>
  ```

- 第二种是配置一个`<scan>`标签：

  ```xml
  < mybatis-spring:scan base-package="com.ws.vison.crud.dao"/>
  ```

- 第三种直接用@MapperScan 注解，例如在Springboot中

  ```java
  @SpringBootApplication
  @MapperScan( "com.ws.vison.crud.dao")
  public  class MybaitsApp {
      
   	public static void main(String[] args) {
  		SpringApplication.run(MybaitsApp.class, args);
  	}
  }
  ```



# 2.mybatis-spring原理分析

## 2.1 创建会话工厂

​	Spring 对 MyBatis 的对象进行了管理，但是并不会替换 MyBatis 的核心对象。也就意味着：MyBatis jar 包中的 SqlSessionFactory、SqlSession、MapperProxy 这些都会用到。而 mybatis-spring.jar 里面的类只是做了一些包装或者桥梁的工作。

 `SqlSessionFactoryBean`这个用来创建SqlSessionfactory。

- 它实现了 InitializingBean 接口，所以要实现 afterPropertiesSet()方法，这个方法会在 bean 的属性值设置完的时候被调用。**这里实例化SqlSessionFactory**
- 另外它实现了 FactoryBean 接口，所以它初始化的时候，实际上是调用 getObject()方法，它里面调用的也是 afterPropertiesSet()方法。**这里会返回SqlSessionFacotry.**

###  源码 -- 会话工厂创建流程

`buildSqlSessionFactory`方法生成`DefaultSqlSessionFactory`流程，这里的版本是mybatis-spring 2.0.0版本

- 1）第一步我们定义了一个 Configuration，叫做 targetConfiguration。然后是一些标签属性的检查，接下来调用了 buildSqlSessionFactory()方法

- 2）426 行，判断 Configuration 对象是否已经存在，也就是是否已经解析过。如果已经有对象，就覆盖一下属性。
- 3）433 行，如果 Configuration 不存在，但是配置了 configLocation 属性，就根据mybatis-config.xml 的文件路径，构建一个 xmlConfigBuilder 对象。
- 4）436 行，否则，Configuration 对象不存在，configLocation 路径也没有，只能使用默认属性去构建去给 configurationProperties 赋值。后面就是基于当前 factory 对象里面已有的属性，对 targetConfiguration 对象里面属性的赋值

- 5）在第 498 行，如果 xmlConfigBuilder 不为空，也就是上面的第二种情况，调用了xmlConfigBuilder.parse()去解析配置文件，最终会返回解析好的 Configuration 对象。

- 6）在 第 507 行 ， 如 果 没 有 明 确 指 定 事 务 工 厂 ， 默 认 使 用SpringManagedTransactionFactory。它创建的 SpringManagedTransaction 也有getConnection()和 close()方法

- 7）在 520 行，调用 xmlMapperBuilder.parse()，这个步骤我们之前了解过了，它的作用是把接口和对应的 MapperProxyFactory 注册到 MapperRegistry 中。
- 8）最后调用sqlSessionFactoryBuilder.build() 返 回 了 一 个DefaultSqlSessionFactory。





## 2.2 创建会话SqlSessionTemplate

​	我们现在已经有一个 DefaultSqlSessionFactory，按照编程式的开发过程，我们接下来就会创建一个 SqlSession 的实现类，但是在 Spring 里面，我们不是直接使用DefaultSqlSession 的，而是对它进行了一个封装，这个 SqlSession 的实现类就是SqlSessionTemplate。这个跟 Spring 封装其他的组件是一样的，比如 JdbcTemplate，RedisTemplate 等等，也是 Spring 跟 MyBatis 整合的最关键的一个类。

**为什么不用 DefaultSqlSession？它是线程不安全的，注意看类上的注解**

**为什么SqlSessionTemplate是线程安全的？** 可以参考https://blog.51cto.com/longlongchang/1171019

> 由同一个sqlSessionFactory创建的执行类型相同的sqlSession才会被复用，其他情况下都是创建新的sqlSession。试想一下，即使只有一个SqlSessionTemplate供所有dao使用，所有地方使用的都是同以个sqlSessionFactory，但是由于是不同线程，所以得到的不是同一个sqlSession，因此不会出现线程安全问题。好处是同一个线程同一个事务中sqlSession会被复用，不会每执行一个sql请求，都创建一个SqlSession，这样很浪费资源，因为SqlSession相当于一次数据库连接。

注意看源码`SqlSessionTemplate`中构造方法时通过一个`SqlSessionProxy`引用；还有`SqlSessionInterceptor`内部类



## 2.3 获取SqlSessionTemplate引用

​	我们让 DAO 层的实现类继承 `SqlSessionDaoSupport`，就可以获得`SqlSessionTemplate`，然后在里面封装 `SqlSessionTemplate` 的方法。



## 3. 其他

对象 生命周期

- SqlSessionTemplate： Spring 中 SqlSession 的替代品，是线程安全的，通过代理的方式调用DefaultSqlSession 的方法
- SqlSessionInterceptor：（ 内部类） 代理对象，用来代理 DefaultSqlSession，在 SqlSessionTemplate 中使用
- SqlSessionDaoSupport ：用于获取 SqlSessionTemplate，只要继承它即可
- MapperFactoryBean 注册到 IOC 容器中替换接口类，继承了 SqlSessionDaoSupport 用来获取SqlSessionTemplate，因为注入接口的时候，就会调用它的 getObject()方法
- SqlSessionHolder 控制 SqlSession 和事务



**我们的Spring集成Mybatis有多种方式调用**

