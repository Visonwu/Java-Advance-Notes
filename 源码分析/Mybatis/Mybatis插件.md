



MyBatis 通过提供插件机制，让我们可以根据自己的需要去增强 MyBatis 的功能。

需要注意的是，如果没有完全理解 MyBatis 的运行原理和插件的工作方式，最好不要使用插件，因为它会改变系底层的工作逻辑，给系统带来很大的影响

# 1.可以代理的四大对象

- MyBatis 允许你在已映射语句执行过程中的某一点进行拦截调用。默认情况下，MyBatis 允许使用插件来拦截的方法调用包括：
  - Executor (update, query, flushStatements, commit, rollback, getTransaction, close, isClosed)
  - ParameterHandler (getParameterObject, setParameters)
  - ResultSetHandler (handleResultSets, handleOutputParameters)
  - StatementHandler (prepare, parameterize, batch, update, query)

可以参考官网：http://www.mybatis.org/mybatis-3/zh/configuration.html#plugins



**原理就是通过责任链模式，层层拦截来办到的。**



# 2.自定义插件编写与注册
（基于 spring-mybatis）运行自定义的插件，需要 3 步，我们以 PageHelper 为例：

- **1、编写自己的插件类**
  - 1）实现 Interceptor 接口；这个是所有的插件必须实现的接口。
  - 2）添加@Intercepts({@Signature()})，指定拦截的对象和方法、方法参数
    方法名称+参数类型，构成了方法的签名，决定了能够拦截到哪个方法。
  - 3）实现接口的 3 个方法

```java
// 用于覆盖被拦截对象的原有方法（在调用代理对象 Plugin 的 invoke()方法时被调用）
Object intercept(Invocation invocation)  throws Throwable;
// target 是被拦截对象，这个方法的作用是给被拦截对象生成一个代理对象，并返回它
Object plugin(Object target);
// 设置参数
void setProperties(Properties properties);
```

- **2、插件注册，在 mybatis-config.xml 中注册插件**

  - ```xml
    <plugins>
        <plugin interceptor ="com.github.pagehelper.PageInterceptor">
        	<property name" ="offsetAsPageNum" value ="true"/> ...
        </ plugin>
    </ plugins>
    ```

- **3、插件登记**
  MyBatis 启 动 时 扫 描` <plugins> `标 签 ， 注 册 到 Configuration 对 象 的InterceptorChain 中。property 里面的参数，会调用 setProperties()方法处理。

# 3. 插件执行流程

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g2lz76aaxzj20jg0b4dlf.jpg)



# 4. 对Executor进行代理

- **Interceptor**： 自定义插件需要实现接口，实现 4 个方法

- **InterceptChain**： 配置的插件解析后会保存在 Configuration 的 InterceptChain 中

- **Plugin** ：用来创建代理对象，包装四大对象

  - ```java
    //Interceptor接口实现的方法通过Plugin.wrap做代理返回
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
    ```

- **Invocation**： 对被代理类进行包装，可以调用 proceed()调用到被拦截的方法

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g2lz8s84b5j20o50fj780.jpg)



# 5.PageHelper原理分析

## 5.1 使用

例如使用分页插件查询十条数据

```java
PageHelper.startPage (pn, 10);
List<Employee> emps = employeeService.getAll();
PageInfo page = new PageInfo(emps, 10);
```

- PageInterceptor 自定义拦截器

- Page 包装分页参数

- PageInfo 包装结果

- PageHelper 工具类







