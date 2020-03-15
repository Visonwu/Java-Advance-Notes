		dubbo主要是一个分布式服务治理解决方案，那么什么是服务治理？服务治理主要是针对大规模服务化以后，服务之间的路由、负载均衡、容错机制、服务降级这些问题的解决方案，而 Dubbo 实现的不仅仅是远程服务通信，并且还解决了服务路由、负载、降级、容错等功能。



# 1.入门例子

​	dubbo默认使用netty来做通信

**依赖**

```xml
<dependency> 
    <groupId>org.apache.dubbo</groupId> 
    <artifactId>dubbo</artifactId> 
    <version>2.7.2</version> 
</dependency>
```

## 1.1  公共Api 

定义接口

```java
public interface DemoService {
    String sayHello(String name);
}
```

## 1.2 服务方实现

​	服务实现

```java
public class DemoServiceImpl implements DemoService {
    public String sayHello(String name) {
        return "Hello " + name;
    }
}
```

  用 Spring 配置声明暴露服务

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:dubbo="http://dubbo.apache.org/schema/dubbo"
    xsi:schemaLocation="http://www.springframework.org/schema/beans        http://www.springframework.org/schema/beans/spring-beans-4.3.xsd        http://dubbo.apache.org/schema/dubbo        http://dubbo.apache.org/schema/dubbo/dubbo.xsd">
 
    <!-- 提供方应用信息，用于计算依赖关系，必须唯一， 在监控系统可以区别不同应用-->
    <dubbo:application name="hello-world-app"  />
 
    <!-- 不使用服务注册中心 -->
    <dubbo:registry address="N/A" />
 
    <!-- 用dubbo协议在20880端口暴露服务 -->
    <dubbo:protocol name="dubbo" port="20880" />
 
    <!-- 声明需要暴露的服务接口 -->
    <dubbo:service interface="org.apache.dubbo.demo.DemoService" ref="demoService" />
 
    <!-- 和本地bean一样实现服务 -->
    <bean id="demoService" class="org.apache.dubbo.demo.provider.DemoServiceImpl" />
</beans>
```

启动服务

```java
 
public class Provider {
    public static void main(String[] args) throws Exception {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] {"application.xml"});
        context.start();
        System.in.read(); // 按任意键退出
    }
}
```



## 1.3 客户端消费

​	application.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:dubbo="http://dubbo.apache.org/schema/dubbo"
    xsi:schemaLocation="http://www.springframework.org/schema/beans        http://www.springframework.org/schema/beans/spring-beans-4.3.xsd        http://dubbo.apache.org/schema/dubbo        http://dubbo.apache.org/schema/dubbo/dubbo.xsd">
 
    <!-- 消费方应用名,唯一，不要与提供方一样 -->
    <dubbo:application name="consumer-of-helloworld-app"  />
 
    <!-- 不使用注册中心 -->
    <dubbo:registry address="N/A" />
 
    <!-- 生成远程服务代理，可以和本地bean一样使用demoService -->
    <!-- 这没有使用注册中心，所以需要自己提供url地址去发现服务-->
    <dubbo:reference id="demoService" interface="org.apache.dubbo.demo.DemoService" 
                    url="dubbo://192.168.13.1:20880/org.apache.dubbo.demo.DemoService"/>
</beans>
```

​	代码调用

```java
public class Consumer {
    public static void main(String[] args) throws Exception {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] {"application.xml"});
        context.start();
        DemoService demoService = (DemoService)context.getBean("demoService"); // 获取远程服务代理
        String hello = demoService.sayHello("world"); // 执行远程方法
        System.out.println( hello ); // 显示调用结果
    }
}
```

# 2.Dubbo启动真相

​		正常情况下，我们会认为服务的发布，需要tomcat、或者jetty这类的容器支持，但是只用Dubbo以后，我们并不需要这样重的服务器去支持，同时也会增加复杂性，和浪费资源。Dubbo提供了几种容器让我们去启动和发布服务。

**容器类型**

- Spring Container：自动加载META-INF/spring 目录下的所有 Spring 配置。

- logback Container：自动装配log back 日志

- Log4j Container：自动配置 log4j 的配置

Dubbo 提供了一个 Main .main 快速启动相应的容器，默认情况下，只会启动 spring 容器



​		默认情况下， spring 容器，本质上，就是加在 spring ioc 容器，然后启动一个 netty 服务实现服务的发布 ，所以并没有特别多的黑科技 ，下面是spring 容器启动的代码

```java
package com.alibaba.dubbo.container; 
public static void main(String[] args) {
     ...
    for (Container container : containers) {
        container.start();
        logger.info("Dubbo " + container.getClass().getSimpleName() + " started!");
    }         
    .... 
}     

//这里的container是SpringContainer
public class SpringContainer implements Container {
    @Override
    public void start() {
        String configPath = ConfigUtils.getProperty(SPRING_CONFIG);
        if (configPath == null || configPath.length() == 0) {
            configPath = DEFAULT_SPRING_CONFIG;
        }
        context = new ClassPathXmlApplicationContext(configPath.split("[,\\s]+"));
        context.start();
    }
}
```

所以上面的启动服务的provider类可以通过如下`Main`启动代替

```java
import com.alibaba.dubbo.container.Main
public class Provider{
    
    public static void main(String[] args){
        Main.main(args);
    }
    
}
```



# 3. 基于zookeeper注册中心

## 3.1 dubbo集成 Zookeeper 的实现原理

dubbo注册的相关信息在zookeeper中节点的信息存储结构如下

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g57vqj5cxxj20ln0cin5u.jpg)

## 3.2 改造上面的服务

需要依赖

```xml
<dependency> 
    <groupId>org.apache.curator</groupId> 
    <artifactId>curator-framework</artifactId> 
    <version>4.0.0</version> 
</dependency>
<dependency> 
    <groupId>org.apache.curator</groupId> 
    <artifactId>curator-recipes</artifactId> 
    <version>4.0.0</version> 
</dependency>
```

修改`application .xml `文件

```xml
<dubbo:registry address="zookeeper://192.168.13.102:2181" /> 
<!--如果是 zookeeper 集群，则配置的方式是-->
<dubbo:registry  address ="zookeeper://ip:host?backup=ip:host,ip:host>
```

客户端改造

```xml
<!-- 使用multicast广播注册中心暴露服务地址 --> 
<dubbo:registry address="zookeeper://192.168.13.102:2181" /> 
<dubbo:reference id="demoService" interface="org.apache.dubbo.demo.DemoService"/>
```

## 3.3 dubbo 每次都要连 zookeeper？
在消费端的配置文件中指定如下路径

```xml
<dubbo:registry id="zookeeper" address="zookeeper://192.168.13.102:2181" 
                file="d:/dubbo-server" />
```

## 3.4 多注册中心支持

​		Dubbo中可以支持多注册中心， 有的时候，客户端需要用调用的远程服务不在同一个注册中心上，那么客户端就需要配置多个注册中心来访问。 演示一个简单的案例

```xml
<!---配置多个注册中心-->
<dubbo:registry address="zookeeper://192.168.13.102:2181" id="registryCenter1"/> <dubbo:registry address="zookeeper://192.168.13.102:2181" id="registryCenter2"/>
```

将服务注册到不同的注册中心通过`registry`设置注册中心的 ID

```xml

<dubbo:service interface="com.vison.practice.LoginService" registry="registryCenter1" ref="demoService" />
```

## 3.4 注册中心的其他支持

1. 当设置 `<dubbo:registry check="false" />` 时，记录失败注册和订阅请求，后台定时重试

2. 可通过 `<dubbo:registry username="admin" password="1234" />` 设置zookeeper 登录信息
3. 可通过 `<dubbo:registry group="dubbo" /> `设置 zookeeper 的根节点， 默认使用 dubbo 作为 dubbo 服务注册的 namespace 

# 4. Dubbo仅仅是一个 RPC 框架?
​		到目前为止，我们了解到了Dubbo 的核心功能， 提供服务注册和服务发现以及基于 Dubbo 协议的远程通信， 我想，大家以后不会仅仅只认为 Dubbo是一个 RPC 框架吧。 Dubbo 从另一个方面来看也可以认为是一个服务治理生态 。从目前已经讲过的内容上可以看到。
- Dubbo 可以支持市面上主流的注册中心

- Dubbo 提供了 Container 的支持，默认提供了 3 种 container 。我们可以自行扩展

- Dubbo 对于 RPC 通信协议的支持，不仅仅是原生的 Dubbo 协议，它还围绕着 rmi 、 hessian 、 http 、 webservice 、 thrift 、 rest



​	有了多协议的支持，使得其他rpc 框架的应用程序可以快速的切入到 dubbo生态中。 同时，对于多协议的支持，使得不同应用场景的服务，可以选择合适的协议来发布服务，并不一定要使用 dubbo 提供的长连接方式。

## 4.1 集成Webservice 协议

​	webservice 是一个短链接并且是基于 http 协议的方式来实现的 rpc 框架

**依赖**

```xml
<dependency>
    <groupId>org.apache.cxf</groupId>
    <artifactId>cxf-rt-frontend-simple</artifactId>
    <version>3.3.2</version> 
</dependency> 
<dependency> 
    <groupId>org.apache.cxf</groupId> 
    <artifactId>cxf-rt-transports-http</artifactId>
    <version>3.3.2</version> 
</dependency> 
<dependency>
    <groupId>org.eclipse.jetty</groupId>
    <artifactId>jetty-server</artifactId> 
    <version>9.4.19.v20190610</version> 
</dependency> 
<dependency> 
    <groupId>org.eclipse.jetty</groupId>
    <artifactId>jetty-servlet</artifactId> 
    <version>9.4.19.v20190610</version>
</dependency>
```

修改`application .xml`

添加多协议支持，一个服务可以发布多种协议的支持，也可以实现不同服务发布不同的协议(这里也可以去掉duboo)

```xml
<!-- 用dubbo协议在20880端口暴露服务 --> 
<dubbo:protocol name="dubbo" port="20880" /> 
<dubbo:protocol name="webservice" port="8080" server="jetty"/> 
<!-- 声明需要暴露的服务接口 --> 
<dubbo:service interface="org.apache.dubbo.demo.DemoService" 
               registry="registryCenter1" ref="demoService"protocol="dubbo,webservice"/>
```

启动服务之后，可以使用：
	http://localhost:8080/org.apache.dubbo.demo.DemoService?wsdl 来获得Webservice 的 wsdl 描述文档



## 4.2 Dubbo对于 REST 协议的支持
​		我在用REST 协议发布服务的时候，遇到 Dubbo 的一个坑， Dubbo 启动时后写地方的服务直接被吞掉了，使得服务启动不了的同时，还看不到错误信息 。（可以把 resteasy client 包暂时不导入 ，看到效果)

​	Dubbo中的 REST （表述性资源转移 支持， 是基于 JAX RS2.0(Java API for RESTful Web Services) 来实现的。
​		REST是一种架构风格，简单来说就是对于 api 接口的约束，基于 URL 定位资源，使用 http 动词（ GET/POST/DELETE 来描述操作.



```tex
JAX-RS 协议说明:
	REST很早就提出来了，在早期开发人员为了实现 REST ，会使用各种工具来实现，比如 Servlets 就经常用来开发 RESTful 的程序。随着 REST 被越来越多的开发人员采用，所以 JCP (Java community 提出了 JAX RS 规范， 并且提供了一种新的基于注解的方式来开发 RESTful 服务。 有了这样的一个规范，使得开发人员不需要关心通讯层的东西，只需要关注资源以 以及数据对象。

JAX-RS 规范的实现有： Apache-CXF 、 Jersey(由 Sun 公司提供的 JAX-RS 的参考实现) 、 RESTEasy (jboss 实现)等 。
而Dubbo 里面实现的 REST 就是基于 Jboss 提供的 RESTEasy 框架来实现的

SpringMVC中的 RESTful 实现我们用得比较多，它也是 JAX-RS 规范的一种实现
```

**依赖**

```xml
<dependency> 
    <groupId>org.jboss.resteasy</groupId>
    <artifactId>resteasy-jaxrs</artifactId>
    <version>3.8.0.Final</version>
</dependency> 
<dependency>
    <groupId>org.jboss.resteasy</groupId> 
    <artifactId>resteasy-client</artifactId>
    <version>3.8.0.Final</version> 
</dependency>
```

**添加新的协议支持**

```xml
<dubbo:protocol name="rest" port="8888" server="jetty"/>
```

**提供新的服务**
@Path("/users")：指定访问 UserService 的 URL 相对路径是 /users ，即http://localhost:8080/users
@Path("/register")：指定访问 registerUser() 方法的 URL 相对路径是 /register再结合上一个@Path 为 UserService 指定的路径，则调用UserService.register() 的完整路径为 http://localhost:8080/users/register
@POST：指定访问 registerUser() 用 HTTP POST 方法
@Consumes({MediaType.APPLICATION_JSON})：指定 registerUser() 接收JSON 格式的数据。 REST 框架会自动将 JSON 数据反序列化为 User 对象

（注解可以放在接口上，客户端需要根据接口的注解来进行解析和调用)

**接口**

```java
@Path("/user") 
public interface UserService { 
    
    @GET 
    @Path("/register/{id}") void register(@PathParam("id") int id); 
}
```

**客户端配置**

```xml

<dubbo:protocol name="rest" port="8888" server="jetty"/>
<dubbo:reference id="userService" interface="com.vison.practice.UserService" protocol="rest"/>
```

**在服务接口获得上下文的方式**

​	方式一：

```java
HttpServletRequest
request=(HttpServletRequest)RpcContext.getContext().getRequest();
```

​	方式二：

```java
@GET 
@Path("/register/{id}") void register(@PathParam("id") int id, @Context HttpServletRequest request);
```



# 5. dubbo监控平台使用

建议参考官网：<https://github.com/apache/dubbo-admin>



# 6.Dubbo终端操作

​		Dubbo 里面提供了一种 基于终端操作的方法来实现服务 治理使用`telnet localhost 20880` 连接到 服务对应的端口.

```tex

ls
ls:显示服务列表
ls -l: 显示服务详细信息列表
ls XxxService:显示服务的方法列表
ls -l XxxService: 显示服务的方法详细信息列表
-----------------------------------

ps
ps:显示服务端口列表
ps-l: 显示服务地址列表
ps 20880:显示端口上的连接信息
ps -l 20880: 显示端口上的连接详细信息
---------------------------------------

cd
cd XxxService:改变缺省服务，当设置了缺省服务，凡是需要输入服务名作为参数的命令，都可以省略服务参数
cd /:取消缺省服务
-----------------------------------------

pwd
pwd:显示当前缺省服务
----------------------------------------

count
count XxxService:统计 1 次服务任意方法的调用情况
count XxxService 10:统计 10 次服务任意方法的调用情况
count XxxService xxxMethod:统计 1 次服务方法的调用情况
count XxxService xxxMethod 10: 统计 10 次服务方法的调用情况
```

