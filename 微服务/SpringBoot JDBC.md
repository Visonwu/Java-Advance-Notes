[TOC]

# 1.数据源的分类

数据源是数据库l连接的来源，通过DataSource接口来获取

通过类型可以分为以下三类：

- 通用性数据源：javax.sql.DataSource
- 分布式数据源：javax.sql.XADataSource
- 嵌入式数据源：org.springframework.jdbc.datasource.embedded.EmbeddedDatabase



# 2.SpringBoot的数据源

在 springBoot2.0.0中

- 如果使用SpringMVC作为WEB服务，默认情况下使用嵌入式Tomcat

- 如果采用Spring Web Flux，默认情况下使用Netty Web Server(嵌入式)



##  2.1 数据库连接池技术：

### 1) [Apache Commons DBCP](http://commons.apache.org/proper/commons-dbcp/)

- commons-dbcp2
  - 依赖：commons-pool2

- commons-dbcp(老版本)
  - 依赖：commons-pool

### 2)[Tomcat DBCP](http://tomcat.apache.org/tomcat-8.5-doc/jndi-datasource-examples-howto.html)



###  3）DataSourceProperties类

​      这个类可以查看需要配置的数据源信息，如下：这也是application.properties中需要配置四大信息

```java
@ConfigurationProperties(prefix = "spring.datasource")
public class DataSourceProperties implements BeanClassLoaderAware, InitializingBean {

   private ClassLoader classLoader;

   private String name;

   private boolean generateUniqueName;

   private Class<? extends DataSource> type;

   private String driverClassName;

   private String url;

   private String username;

   private String password;
   ...
   }
```

 ### 4）DataSourceConfiguration类

   这个类是看数据库连接池的技术使用，如果没有相应的包会显示红色， 这里会加载DataSourceProperties相关信息获取连接。

```java
abstract class DataSourceConfiguration {

   @SuppressWarnings("unchecked")
   protected static <T> T createDataSource(DataSourceProperties properties,
         Class<? extends DataSource> type) {
      return (T) properties.initializeDataSourceBuilder().type(type).build();
   }

   /**
    * Tomcat Pool DataSource configuration.
    */
   @ConditionalOnClass(org.apache.tomcat.jdbc.pool.DataSource.class)
   @ConditionalOnMissingBean(DataSource.class)
   @ConditionalOnProperty(name = "spring.datasource.type", havingValue = "org.apache.tomcat.jdbc.pool.DataSource", matchIfMissing = true)
   static class Tomcat {

      @Bean
      @ConfigurationProperties(prefix = "spring.datasource.tomcat")
      public org.apache.tomcat.jdbc.pool.DataSource dataSource(
            DataSourceProperties properties) {
         org.apache.tomcat.jdbc.pool.DataSource dataSource = createDataSource(
               properties, org.apache.tomcat.jdbc.pool.DataSource.class);
         DatabaseDriver databaseDriver = DatabaseDriver
               .fromJdbcUrl(properties.determineUrl());
         String validationQuery = databaseDriver.getValidationQuery();
         if (validationQuery != null) {
            dataSource.setTestOnBorrow(true);
            dataSource.setValidationQuery(validationQuery);
         }
         return dataSource;
      }
       ...
       ...
	
   }
```



## 2.2 单数据源

需要导入jdbc包

spring.properties配置,默认实用 HikariDataSource数据源

```properties
spring.datasource.driverClassName=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://localhost:3306/
spring.datasource.username=root
spring.datasource.password=123456
```

## 2.3 多数据源

通过如下配置实现，但是如果是这样的话，需要添加@Primary作为主数据库

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary    //作为主数据源
    public DataSource masterDatasource(){

       return DataSourceBuilder.create().driverClassName("com.mysql.cj.jdbc.Driver")
                .url("jdbc:mysql://localhost:3306/test")
                .username("root")
                .password("123456")
                .build();
    }

    
    @Bean
    public DataSource slaveDatasource(){

        return DataSourceBuilder.create().driverClassName("com.mysql.cj.jdbc.Driver")
                .url("jdbc:mysql://localhost:3306/test")
                .username("root")
                .password("123456")
                .build();
    }
}
```



```java
//如下dataSource自动注入的时masterDatasource
//这里自动注入需要使用 @Qualifier("masterDatasource")用来区分不同的数据源，
//里面的名称和你配置中的方法相同。

@Repository
public class UserRepository {

    private DataSource dataSource;

    private DataSource masterDatasource;

    private DataSource slaveDatasource;

    @Autowired
    public UserRepository(DataSource dataSource,
                          @Qualifier("masterDatasource") DataSource masterDatasource,
                          @Qualifier("slaveDatasource") DataSource slaveDatasource) {
        this.dataSource = dataSource;
        this.masterDatasource = masterDatasource;
        this.slaveDatasource = slaveDatasource;
    }
}
```



# 3. 事务

## 3.1 事务的隔离级别

java.sql.Connection中的隔离级别：

```java
public interface Connection extends Wrapper, AutoCloseable {
    int TRANSACTION_NONE = 0;
    int TRANSACTION_READ_UNCOMMITTED = 1;
    int TRANSACTION_READ_COMMITTED = 2;
    int TRANSACTION_REPEATABLE_READ = 4;
    int TRANSACTION_SERIALIZABLE = 8;
	......
}
```



  Spring 事务实现重用了JDBC的API，可以参考Spring的Isolation类

```java
//这里是编译后的代码，需要看源码可以查看到相关API使用
public enum Isolation {
    DEFAULT(-1),
    READ_UNCOMMITTED(1),
    READ_COMMITTED(2),
    REPEATABLE_READ(4),
    SERIALIZABLE(8);

    private final int value;

    private Isolation(int value) {
        this.value = value;
    }

    public int value() {
        return this.value;
    }
}

```

事务的的定义类信息

```java
public interface TransactionDefinition {
    int PROPAGATION_REQUIRED = 0;
    int PROPAGATION_SUPPORTS = 1;
    int PROPAGATION_MANDATORY = 2;
    int PROPAGATION_REQUIRES_NEW = 3;
    int PROPAGATION_NOT_SUPPORTED = 4;
    int PROPAGATION_NEVER = 5;
    int PROPAGATION_NESTED = 6;
    int ISOLATION_DEFAULT = -1;
    int ISOLATION_READ_UNCOMMITTED = 1;
    int ISOLATION_READ_COMMITTED = 2;
    int ISOLATION_REPEATABLE_READ = 4;
    int ISOLATION_SERIALIZABLE = 8;
    int TIMEOUT_DEFAULT = -1;

    int getPropagationBehavior();

    int getIsolationLevel();

    int getTimeout();

    boolean isReadOnly();

    @Nullable
    String getName();
}
```

## 3.2 事务的传播行为

```java
//建议看jsr中的jts规范 和EJB 规范
public enum Propagation {
    REQUIRED(0),
    SUPPORTS(1),
    MANDATORY(2),
    REQUIRES_NEW(3),
    NOT_SUPPORTED(4),
    NEVER(5),
    NESTED(6);

    private final int value;

    private Propagation(int value) {
        this.value = value;
    }

    public int value() {
        return this.value;
    }
}
```



## 3.3 保护点

**保存点**就是为回退做的

- 保存点的个数没有限制 ，保存点和虚拟机中快照类似

- 保存点是事务中的一点。用于取消部分事务，当结束事务时，会自动的删除该事务中所定义的所有保存点。

- 当执行rollback时，通过指定保存点可以回退到指定的点。

**回退事务的几个重要操作** 

- 1.设置保存点 savepoint a 
- 2.取消保存点a之后事务 rollback to a 
- 3.取消全部事务 rollback

SavePoint  建议看源码

```java
  DefaultTransactionDefinition transactionDefinition = 
      					new DefaultTransactionDefinition();
        //开始事务
  TransactionStatus transaction = platformTransactionManager
        							.getTransaction(transactionDefinition);
  Object savepoint = transaction.createSavepoint();
 

   transaction.releaseSavepoint(savepoint);
        
  transaction.rollbackToSavepoint(savepoint);
```

 

## 3.4注解事务

```java
@Trasactional    //直接放在需要加事务的方法上即可`
//源码是使用TransactionInterceptor 代理拦截处理的`
```

- 可以指定事务rollback的粒度：rollbackFor 和 noRollbackFor
- 可以指定事务管理器：transactionManager



## 3.5 API编程式事务

```java
@Repository
public class UserRepository {


    private final JdbcTemplate jdbcTemplate;

    //默认使用DataSourceTransactionManager 事务管理器
    private final PlatformTransactionManager platformTransactionManager;

    @Autowired
    public UserRepository(JdbcTemplate jdbcTemplate,
                          PlatformTransactionManager platformTransactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.platformTransactionManager = platformTransactionManager;
    }

    public void save(){

        DefaultTransactionDefinition transactionDefinition = 
            new DefaultTransactionDefinition();
        //开始事务
        TransactionStatus transaction = platformTransactionManager
            .getTransaction(transactionDefinition);
        
        jdbcTemplate.execute("INSERT INTO users(name) VALUES(?);", 
                             new PreparedStatementCallback<Boolean>() {
            @Override
            public Boolean doInPreparedStatement(PreparedStatement 
                                                 preparedStatement) 
                								throws SQLException, 
                                 				DataAccessException {
                
                return preparedStatement.executeUpdate() > 0;
            }
        });

        //提交事务
        platformTransactionManager.commit(transaction);

        //回滚事务
        //platformTransactionManager.rollback(transaction);
    }
```





