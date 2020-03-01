debug按照这个来参考：

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



**第一步，我们通过建造者模式创建一个工厂类，配置文件的解析就是在这一步完成的，包括 mybatis-config.xml 和 Mapper.xml 适配器文件。**

> ​	问题：解析的时候怎么解析的，做了什么，产生了什么对象，结果存放到了哪里。解析的结果决定着我们后面有什么对象可以使用，和到哪里去取。

**第二步，通过 SqlSessionFactory 创建一个 SqlSession。**

> ​	问题：SqlSession 是用来操作数据库的，返回了什么实现类，除了 SqlSession，还创建了什么对象，创建了什么环境？

**第三步，获得一个 Mapper 对象。**

> ​	问题：Mapper 是一个接口，没有实现类，是不能被实例化的，那获取到的这个Mapper 对象是什么对象？为什么要从 SqlSession 里面去获取？为什么传进去一个接口，然后还要用接口类型来接收？

**第四步，调用接口方法。**

> ​	问题：我们的接口没有创建实现类，为什么可以调用它的方法？那它调用的是什么方法？它又是根据什么找到我们要执行的 SQL 的？也就是接口方法怎么和 XML 映射器里面的 StatementID 关联起来的？

> 此外，我们的方法参数是怎么转换成 SQL 参数的？获取到的结果集是怎么转换成对象的？



# 1.配置文件解析

```java
SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
```

​		首先我们要清楚的 是配 置 解 析 的 过 程 全 部 只 解 析 了 两 种 文 件 ；所有解析的都存储在Configuration类中

- 一 个 是`mybatis-config.xml` 全局配置文件

- 另外就是可能有很多个的 `Mapper.xml `文件，也包括在 Mapper 接口类上面定义的`注解`



**BaseBuilder** 

- XMLConfigBuilder 是抽象类 BaseBuilder 的一个子类，专门用来解析全局配置文
  件，针对不同的构建目标还有其他的一些子类，比如：

- XMLMapperBuilder：解析 Mapper 映射器

- XMLStatementBuilder：解析增删改查标签

![](http://ww1.sinaimg.cn/mw690/b8a27c2fgy1g2lp6p8fpxj20eo03ta9x.jpg)

```
配置文件和注解主要是以下流程解析然后存储在Configuration中
XMLConfigBuilder.parse（） -> XmlMapperBuilder.parse（） -> XmlStatementBuilder.parse（） -> MapperAnnotationBuilder 
```

**具体解析时序图：**

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g2lvk1ydtzj21mh0ll40i.jpg)





​	在这一步，我们主要完成了 config 配置文件、Mapper 文件、Mapper 接口上的注解的解析。我们得到了一个最重要的对象 Configuration，这里面存放了全部的配置信息，它在属性里面还有各种各样的容器。

最后，返回了一个 DefaultSqlSessionFactory，里面持有了 Configuration 的实例

```java
//配置类Configuration包含了所有的配置信息；
public class Configuration {

  protected Environment environment; //mysql相关环境，包含事务，jdbc类型等；

  protected boolean safeRowBoundsEnabled;
  protected boolean safeResultHandlerEnabled = true;
  protected boolean mapUnderscoreToCamelCase;
  protected boolean aggressiveLazyLoading;
  protected boolean multipleResultSetsEnabled = true;
  protected boolean useGeneratedKeys;   //
  protected boolean useColumnLabel = true;
  protected boolean cacheEnabled = true;   //缓存是否开启
  protected boolean callSettersOnNulls;
  protected boolean useActualParamName = true;
  protected boolean returnInstanceForEmptyRow;

  protected String logPrefix;
  protected Class <? extends Log> logImpl;
  protected Class <? extends VFS> vfsImpl;
  protected LocalCacheScope localCacheScope = LocalCacheScope.SESSION;
  protected JdbcType jdbcTypeForNull = JdbcType.OTHER;
  protected Set<String> lazyLoadTriggerMethods = new HashSet<String>(Arrays.asList(new String[] { "equals", "clone", "hashCode", "toString" }));
  protected Integer defaultStatementTimeout;
  protected Integer defaultFetchSize;
  protected ExecutorType defaultExecutorType = ExecutorType.SIMPLE;
  protected AutoMappingBehavior autoMappingBehavior = AutoMappingBehavior.PARTIAL;
  protected AutoMappingUnknownColumnBehavior autoMappingUnknownColumnBehavior = AutoMappingUnknownColumnBehavior.NONE;

  protected Properties variables = new Properties();
  protected ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
  protected ObjectFactory objectFactory = new DefaultObjectFactory();  //
  protected ObjectWrapperFactory objectWrapperFactory = new DefaultObjectWrapperFactory();

  protected boolean lazyLoadingEnabled = false;
  protected ProxyFactory proxyFactory = new JavassistProxyFactory(); // #224 Using internal Javassist instead of OGNL

  protected String databaseId;
  /**
   * Configuration factory class.
   * Used to create Configuration for loading deserialized unread properties.
   *
   * @see <a href='https://code.google.com/p/mybatis/issues/detail?id=300'>Issue 300 (google code)</a>
   */
  protected Class<?> configurationFactory;
  //Mapperr的注册器
  protected final MapperRegistry mapperRegistry = new MapperRegistry(this);  
  //所有的拦截器存放位置
  protected final InterceptorChain interceptorChain = new InterceptorChain();
  //java类型和jdbc类型的转换器
  protected final TypeHandlerRegistry typeHandlerRegistry = new TypeHandlerRegistry();
  //type的别名
  protected final TypeAliasRegistry typeAliasRegistry = new TypeAliasRegistry();
  protected final LanguageDriverRegistry languageRegistry = new LanguageDriverRegistry();
  //
  protected final Map<String, MappedStatement> mappedStatements = new StrictMap<MappedStatement>("Mapped Statements collection");
  protected final Map<String, Cache> caches = new StrictMap<Cache>("Caches collection");
  protected final Map<String, ResultMap> resultMaps = new StrictMap<ResultMap>("Result Maps collection");
  protected final Map<String, ParameterMap> parameterMaps = new StrictMap<ParameterMap>("Parameter Maps collection");
  protected final Map<String, KeyGenerator> keyGenerators = new StrictMap<KeyGenerator>("Key Generators collection");

  protected final Set<String> loadedResources = new HashSet<String>();
  protected final Map<String, XNode> sqlFragments = new StrictMap<XNode>("XML fragments parsed from previous mappers");

  protected final Collection<XMLStatementBuilder> incompleteStatements = new LinkedList<XMLStatementBuilder>();
  protected final Collection<CacheRefResolver> incompleteCacheRefs = new LinkedList<CacheRefResolver>();
  protected final Collection<ResultMapResolver> incompleteResultMaps = new LinkedList<ResultMapResolver>();
  protected final Collection<MethodResolver> incompleteMethods = new LinkedList<MethodResolver>();
    
}
```





# 2.会话创建过程

```java
 SqlSession session = sqlSessionFactory.openSession();

包含：
  1.创建事务
  2.创建Executor执行器
```


​	DefaultSqlSessionFactory —— openSessionFromDataSource()这个会话里面，需要包含一个 Executor 用来执行 SQL。Executor 又要指定事务类型和执行器的类型。

所以我们会先从 Configuration 里面拿到 Enviroment，Enviroment 里面就有事务工厂。

## 2.1 创建 Transaction

​	如果配置的是 JDBC，则会使用 Connection 对象的 commit()、rollback()、close()管理事务。	

​	如果配置成 MANAGED，会把事务交给容器来管理，比如 JBOSS，Weblogic。因为我们跑的是本地程序，如果配置成 MANAGE 不会有任何事务。

​	如 果 是 Spring + MyBatis ， 则 没 有 必 要 配 置 ， 因 为 我 们 会 直 接 在applicationContext.xml 里面配置数据源和事务管理器，覆盖 MyBatis 的配置。



## 2.2  创建 Executor

​	我们知道，Executor 的基本类型有三种：SIMPLE、BATCH、REUSE，默认是 SIMPLE（settingsElement()读取默认值），他们都继承了抽象类 BaseExecutor（模板方法）。



问题**：三种类型的区别（通过 update()方法对比）？**

- SimpleExecutor：每执行一次 update 或 select，就开启一个 Statement 对象，用完立刻关闭 Statement 对象。

- ReuseExecutor：执行 update 或 select，以 sql 作为 key 查找 Statement 对象，存在就使用，不存在就创建，用完后，不关闭 Statement 对象，而是放置于 Map 内，供下一次使用。简言之，就是重复使用 Statement 对象。

- BatchExecutor：执行 update（没有 select，JDBC 批处理不支持 select），将所有 sql 都添加到批处理中（addBatch()），等待统一执行（executeBatch()），它缓存了多个 Statement 对象，每个 Statement 对象都是 addBatch()完毕后，等待逐一执行executeBatch()批处理。与 JDBC 批处理相同。



​	如果配置了 cacheEnabled=ture，会用装饰器模式对 executor 进行包装：new CachingExecutor(executor)。

包装完毕后，会执行:

```java
//创建executor
public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
    executorType = executorType == null ? defaultExecutorType : executorType;
    executorType = executorType == null ? ExecutorType.SIMPLE : executorType;
    Executor executor;
    if (ExecutorType.BATCH == executorType) {
        executor = new BatchExecutor(this, transaction);
    } else if (ExecutorType.REUSE == executorType) {
        executor = new ReuseExecutor(this, transaction);
    } else {
        executor = new SimpleExecutor(this, transaction);
    }
    if (cacheEnabled) {
        executor = new CachingExecutor(executor); //这个表示二级缓存
    }
    //这里对executor做拦截处理；也是对Executor做代理处理；
    executor = (Executor) interceptorChain.pluginAll(executor);
    return executor;
}

```

## 2.3 最终返回

最终返回 DefaultSqlSession，属性包括 Configuration、Executor 对象。

总结：创建会话的过程，我们获得了一个 DefaultSqlSession，里面包含了一个Executor，它是 SQL 的执行者。



## 2.4 时序图

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g2lvxtpepmj210y0s8zm9.jpg)

# 3.获取Mapper对象

​	现在我们已经有一个 DefaultSqlSession 了，必须找到 Mapper.xml 里面定义的Statement ID，才能执行对应的 SQL 语句。

**找到 Statement ID 有两种方式：**

## 3.1 直接调用 session 的方法

- **一种是直接调用 session 的方法**，在参数里面传入Statement ID，这种方式属于硬编码，我们没办法知道有多少处调用，修改起来也很麻烦。另一个问题是如果参数传入错误，在编译阶段也是不会报错的，不利于预先发现问题。

  - ```java
    Blog blog = (Blog) session.selectOne("com.gupaoedu.mapper.BlogMapper.selectBlogById", 1);
    ```


## 3.2 通过定义Mapper接口

​	由于我们的接口名称跟 Mapper.xml 的 namespace 是对应的，接口的方法跟statement ID 也都是对应的，所以根据方法就能找到对应的要执行的 SQL

```java
BlogMapper mapper = session.getMapper(BlogMapper. class)
```

我们知道，在解析 mapper 标签和 Mapper.xml 的时候已经把接口类型和类型对应的 MapperProxyFactory 放到了一个 Map 中。获取 Mapper 代理对象，实际上是从Map 中获取对应的工厂类后，调用以下方法创建对象：

```java
MapperProxyFactory.newInstance()
```

最后返回代理对象：

```java
return (T) Proxy. newProxyInstance ( mapperInterface.getClassLoader(), new Class[]
{ e mapperInterface }, mapperProxy);
```

​	

​	**我们只需要根据接口类型+方法的名称(不需要被代理的实现类)，就可以找到Statement ID了，而唯一要做的一件事情也是这件，所以不需要实现类。在MapperProxy里面直接执行逻辑（也就是执行 SQL）就可以。**



## 3.3 获取代理对象时序图

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g2lwew5ytnj212x0s3dha.jpg)

## 3.4 总结

​	获得 Mapper 对象的过程，实质上是获取了一个 MapperProxy 的代理对象。MapperProxy 中有 sqlSession、mapperInterface、methodCache



# 4.执行SQL

​	 由于所有的 Mapper 都是 MapperProxy 代理对象，所以任意的方法都是执行MapperProxy 的 invoke()方法

**MapperProxy.invoke()源码**

```java
@Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      } else if (isDefaultMethod(method)) {
        return invokeDefaultMethod(proxy, method, args);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
    final MapperMethod mapperMethod = cachedMapperMethod(method);
    return mapperMethod.execute(sqlSession, args);
  }
```



## 4.1 首先判断是否需要去执行 SQL，还是直接执行方法

```java
try {
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args); //Object的方法
      } else if (isDefaultMethod(method)) {
        return invokeDefaultMethod(proxy, method, args); //
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }

//Object 本身的方法和 Java 8 中接口的默认方法不需要去执行 SQL。

//如java8的默认方法
public  interface IService {
     default String getName(){
     	return "Vison";
    }
}
```



## 4.2 获取缓存
这里加入缓存是为了提升 MapperMethod 的获取速度：

```java
// 获取缓存，保存了方法签名和接口方法的关系
final MapperMethod mapperMethod = cachedMapperMethod(method);
```



## 4.3 MapperMethod.execute

```java
mapperMethod.execute( sqlSession, args)
    
//具体的方法
  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    switch (command.getType()) {
      case INSERT: {
      Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
        if (method.returnsVoid() && method.hasResultHandler()) {
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) {
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) {
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {
          result = executeForCursor(sqlSession, args);
        } else {
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
        }
        break;
      case FLUSH:
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName() 
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }   
```

## 4.4 时序图

这里用了selectOne做的执行sql，具体时序图

注：

```java
执行sql的才 在创建三大对象handler；
StatementHandler； //负责执行sql的处理，同时也用plugin做了拦截器包装
ParameterHandler;  //负责执行参数 的处理，同时也用plugin做了拦截器包装
ResultSetHanler;   //负责sql执行后的 结果处理，同时也用plugin做了拦截器包装

当然plugin除了对于上面三大天王 做了拦截；在创建Sqlsession的时候对Executor做了包装
```



![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g2lxd6td81j21my12s781.jpg)

# 5.使用到的设计模式

![3glZY8.png](https://s2.ax1x.com/2020/03/01/3glZY8.png)

