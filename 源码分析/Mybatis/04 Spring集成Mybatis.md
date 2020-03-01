参考：http://www.mybatis.org/spring/zh/index.html



# 1.Spring集成Mybatis步骤

spring集成mybatis需要映入mybatis和spring的桥梁包---mybatis-spring

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
    <!-- 这是mapper.xml的配置地址-->
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

> 当然， 为了减少重复的代码， 我们通常不会让我们的实现类直接去继承SqlSessionDaoSupport，而是先创建一个BaseDao 继承SqlSessionDaoSupport。在BaseDao 里面封装对数据库的操作，包括selectOne()、selectList()、insert()、delete()这些方法，子类就可以直接调用。
>
> 然后让我们的实现类继承BaseDao 并且实现我们的DAO 层接口，这里就是我们的Mapper 接口。实现类需要加上@Repository 的注解。在实现类的方法里面，我们可以直接调用父类（BaseDao）封装的selectOne()方法，那么它最终会调用sqlSessionTemplate 的selectOne()方法

### 1）代码：

```java
public  class BaseDao extends SqlSessionDaoSupport {
    //使用sqlSessionFactory
    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
        super.setSqlSessionFactory(sqlSessionFactory);
    }

    /**
     * 获取Object对象
     */
    public Object selectOne(String statement) {
        return getSqlSession().selectOne(statement);
    }

    public Object selectOne(String statement, Object parameter) {
        return getSqlSession().selectOne(statement, parameter);
    }
}

//DAO实现
@Repository
public class EmployeeDaoImpl extends BaseDao implements EmployeeMapper {
    /**
     * 暂时只实现了这一个方法
     * @param empId
     * @return
     */
    @Override
    public Employee selectByPrimaryKey(Integer empId) {
         //出现了Statement ID 的硬编码
        Employee emp = (Employee) 
            this.selectOne("com.vison.crud.dao.EmployeeMapper.selectByPrimaryKey",empId);
        return emp;
    }
    
}
```





## 2.4 @Autowired注入Mapper接口真相

​		有没有更好的拿到SqlSessionTemplate 的方法？上面的方法是直接继承SqlSessionDaoSupport，但是还是需要些DAO的实现；但实际我们是通过直接@Autowired接口就可以了；

​	实际我们上面扫描Mapper接口就是把这些放到IOC容器中，这样我们就可以直接通过@Autowired直接获取信息了；

```xml
<!--1-->
< mybatis-spring:scan base-package="com.ws.vison.crud.dao"/>

<!--2.或者下面这样扫描等-->
@MapperScan( "com.ws.vison.crud.dao")

<!--3.-->
<bean id" ="mapperScanner" class="org.mybatis.spring.mapper.MapperScannerConfigurer">
	<property name" ="basePackage" value ="com.ws.vison.crud.dao"/>
</ bean>
```

### 1) 扫描类MapperScannerConfigurer

​	这个扫描通过`MapperScannerConfigurer`实现

```java
public class MapperScannerConfigurer implements BeanDefinitionRegistryPostProcessor, InitializingBean, ApplicationContextAware, BeanNameAware {

  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
    if (this.processPropertyPlaceHolders) {
      processPropertyPlaceHolders();
    }

    ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry);
    scanner.setAddToConfig(this.addToConfig);
    scanner.setAnnotationClass(this.annotationClass);
    scanner.setMarkerInterface(this.markerInterface);
    scanner.setSqlSessionFactory(this.sqlSessionFactory);
    scanner.setSqlSessionTemplate(this.sqlSessionTemplate);
    scanner.setSqlSessionFactoryBeanName(this.sqlSessionFactoryBeanName);
    scanner.setSqlSessionTemplateBeanName(this.sqlSessionTemplateBeanName);
    scanner.setResourceLoader(this.applicationContext);
    scanner.setBeanNameGenerator(this.nameGenerator);
    scanner.registerFilters();
     //重点这个扫描包
    scanner.scan(StringUtils.tokenizeToStringArray(this.basePackage, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS));
  }
}

public class ClassPathBeanDefinitionScanner extends ClassPathScanningCandidateComponentProvider {

    //扫描包实际实现
	public int scan(String... basePackages) {
		int beanCountAtScanStart = this.registry.getBeanDefinitionCount();

        //这个调用子类重写的方法
		doScan(basePackages);

		// Register annotation config processors, if necessary.
		if (this.includeAnnotationConfig) {
			AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
		}

		return (this.registry.getBeanDefinitionCount() - beanCountAtScanStart);
	}

}
public class ClassPathMapperScanner extends ClassPathBeanDefinitionScanner {
  public Set<BeanDefinitionHolder> doScan(String... basePackages) {
    Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);

    if (beanDefinitions.isEmpty()) {
      logger.warn("No MyBatis mapper was found in '" + Arrays.toString(basePackages) + "' package. Please check your configuration.");
    } else {
      processBeanDefinitions(beanDefinitions);
    }

    return beanDefinitions;
  }
  
   //处理BeanDefinition
   private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
    GenericBeanDefinition definition;
    for (BeanDefinitionHolder holder : beanDefinitions) {
      definition = (GenericBeanDefinition) holder.getBeanDefinition();

      if (logger.isDebugEnabled()) {
        logger.debug("Creating MapperFactoryBean with name '" + holder.getBeanName() 
          + "' and '" + definition.getBeanClassName() + "' mapperInterface");
      }

      // the mapper interface is the original class of the bean
      // but, the actual class of the bean is MapperFactoryBean
      definition.getConstructorArgumentValues().addGenericArgumentValue(definition.getBeanClassName()); // issue #59
      //实际Mapper存储的是MapperFactoryBean，这个继承了SqlSessionDaoSupport可以拿到SqlSessionTemplate
      definition.setBeanClass(this.mapperFactoryBean.getClass());
    	
       ........
    }  
}
   
```

### 2）MapperFacotoryBean作为Mapper的工厂类

问题：
	为什么要把注册bean的时候；把BeanClass 修改成MapperFactoryBean，这个类有什么作用？

原因：MapperFactoryBean 继承了SqlSessionDaoSupport ， 可以拿到SqlSessionTemplate。

如下所示：

```java
public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {

  private Class<T> mapperInterface;

  private boolean addToConfig = true;

  public MapperFactoryBean() {
    //intentionally empty 
  }
  
  public MapperFactoryBean(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  @Override
  public T getObject() throws Exception {
    return getSqlSession().getMapper(this.mapperInterface);
  }


  /**
   * Sets the mapper interface of the MyBatis mapper
   * @param mapperInterface class of the interface
   */
  public void setMapperInterface(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * Return the mapper interface of the MyBatis mapper
   * @return class of the interface
   */
  public Class<T> getMapperInterface() {
    return mapperInterface;
  }
```

### 3）真相解密

```java
@Service
public class EmployeeService {
    @Autowired
    EmployeeMapper employeeMapper;

    public List<Employee> getAll() {

        return employeeMapper.selectByMap(null);
    }
	....
}
```

Spring 在启动的时候需要去实例化EmployeeService。
			EmployeeService 依赖了EmployeeMapper 接口（是EmployeeService 的一个属性）。
Spring 会根据Mapper 的名字从BeanFactory 中获取它的BeanDefination，再从BeanDefination 中获取BeanClass ， EmployeeMapper 对应的BeanClass 是MapperFactoryBean（上一步已经分析过）。
接下来就是创建MapperFactoryBean，因为实现了FactoryBean 接口，同样是调用getObject()方法。

最终：

```java
public class MapperFactoryBean <T> extends SqlSessionDaoSupport implements FactoryBean<T>  {
   /**
   * {@inheritDoc}
   */
  @Override
  public T getObject() throws Exception {
      //getSqlSession就是SqlSessionTemplate了；然后完全和我们之前分析的Mybatis的代码结合起来
    return getSqlSession().getMapper(this.mapperInterface);
  }
  //获取流程   
 SqlSessionTemplate -> DefaultSqlSession ->Configuration->MapperRegistry->mapperProxyFactory.newInstance(sqlSession)  ->MapperProxy(最终Mapper的代理类)

}
```



## 3. 其他

对象 生命周期

- SqlSessionTemplate： Spring 中 SqlSession 的替代品，是线程安全的，通过代理的方式调用DefaultSqlSession 的方法
- SqlSessionInterceptor：（ 内部类） 代理对象，用来代理 DefaultSqlSession，在 SqlSessionTemplate 中使用
- SqlSessionDaoSupport ：用于获取 SqlSessionTemplate，只要继承它即可
- MapperFactoryBean 注册到 IOC 容器中替换接口类，继承了 SqlSessionDaoSupport 用来获取SqlSessionTemplate，因为注入接口的时候，就会调用它的 getObject()方法
- SqlSessionHolder 控制 SqlSession 和事务



**我们的Spring集成Mybatis有多种方式调用**





# 3.Spring-Mybatis事务

