Sleuth产生：参考基础：[Google Dapper](dapper.pdf)



​		在大规模的微服务的使用，各个微服务的调用之间也会越来越复杂，几乎在每一个请求都会有复杂的分布式调用链路，所以为了跟踪请求链路及其调用情况，我们可以用Sleuth来做到这一点。

spring-cloud微服务分布：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g2vb8l8ac9j20zu0jedi0.jpg)

# 1.简单使用

​	基本上只需要引入依赖就可以了，不过只能看到日志有一定的变化。

HTTP 收集（HTTP 调用），这里只是简单HTTPriz

**1) 借用前面的项目**

- 一个Eureka-Server注册中心，

- 两个项目（项目一consumer-demo调用 项目二的service-provider），并且这两个项目都注册到Eureka-server上

**2）在两个调用的链路中添加日志打印，简单的打印即可**
**3）分别在后面两个项目添加依赖**

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
```



**4）启动项目，然后发起调用，这里会发现控制台多了如下信息**

```xml
INFO [feign-consumer,ce638951bffb6c99,ce638951bffb6c99,false] 。。。。

INFO [hello-service-provider,ce638951bffb6c99,1b79415e848fdcaa,false] 。。。。
```



这里会发现相比没有添加sleuth依赖多了一些日志[service- provider,f34343dkdk3434343ss,a6344343dejdkfjk,false]

- 第一个值：service-provider表示项目名，即spring-application-name的名字
- 第二个值：f34343dkdk3434343ss表示Spring-cloud-sleuth生成的唯一ID,成为TraceID,用来标识一条请求链路，一个请求只有会一个TraceID和多个SpanID
- 第三个值：a6344343dejdkfjk表示Spring-cloud-sleuth生成的另外一个ID，称为SpanID,表示一个基本的工作单元，如果请求的链路的第一步是当期项目，那么这里为TraceID值
- 第四个值：false，表示是否要将该信息输出到Zipkin等服务中来收集和展示。



# 2.Sleuth原理

## 2.1 抽样收集

​	我们在分析和跟踪日志的时候会产生大量的日志，如果日志过多那么对整个分布式系统也是有一定的影响的，同时保存大量的日志也会存在很大的存储开销。所以Sleuth采用了抽样收集的方式.

Sleuth提供了如下Sampler接口：

```java
public abstract class Sampler {

  public static final Sampler ALWAYS_SAMPLE = new Sampler() {
  };

  public static final Sampler NEVER_SAMPLE = new Sampler() {
  };
  //这个用来返回是否要收集日志，但是即使是false也是表示不仅仅代表该跟踪信息不被输出到远程分析系统，比如zipkin
  public abstract boolean isSampled(long traceId);

  public static Sampler create(float rate) {
    return CountingSampler.create(rate);
  }
}

```

**我们可以自定义抽样比率，通过appllication.xml配置**

```java
spring.sleuth.sampler.rate=1
```

**通过添加bean**

```java
//总是全部选择
@Bean
public Sampler defaultSample(){
    return Sampler.ALWAYS_SAMPLE;
}
```







# 2.Spring Cloud Sleuth 整合

​	通过sleuth给每一个分布式系统增加了跟踪日志，但是每一个服务分别查看日志还是比较麻烦，所以我们可以通过其他整合日志系统，可以更加快捷的查看到日志信息情况。

## 2.1 Spring Cloud Sleuth整合Logstash

​	Logstash是ELK中的日志收集系统，我们可以参考官网做整合：<https://cloud.spring.io/spring-cloud-static/spring-cloud-sleuth/2.1.2.RELEASE/single/spring-cloud-sleuth.html>



但是通过ELK做出来的信息收集不能对调用时间的监控。

## 2.2 Spring Cloud Sleuth整合 [Zipkin](zipkin.io) 

### 2.2.1 Http收集

​	使用简单的Http，收集zipkin日志，并展示在页面上

#### 1） 创建 Spring Cloud Zipkin 服务器

**1）增加 Maven 依赖**

```xml
<!-- Zipkin 服务器依赖 -->
<dependency>
	<groupId>io.zipkin.java</groupId>
	<artifactId>zipkin-server</artifactId>
</dependency>

<!-- Zipkin 服务器UI控制器 -->
<dependency>
	<groupId>io.zipkin.java</groupId>
	<artifactId>zipkin-autoconfigure-ui</artifactId>
	<scope>runtime</scope>
</dependency>
```

**2）配置服务**

```properties
spring.application.name=zipkin-demo-server
server.port=9411
```

**3）激活 Zipkin 服务器**

```java
@SpringBootApplication
@EnableZipkinServer
public class ZipkinServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringCloudZipkinServerDemoApplication.class, args);
	}
}
```

#### 2） 原来的两个调用项目zipkin客户端配置

​			项目一 **consumer-demo**调用 项目二的**service-provider**

**增加依赖**

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-sleuth-zipkin</artifactId>
</dependency>
```

**分别添加zipkin-server服务器的url调用地址**

```properties
spring.zipkin.base-url=http://localhost:9411/
```

#### 3） 调用链路测试

​	调用完了，通过zipkin服务器查看信息`http://localhost:9411/zipkin`可以查看到如下信息：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4lbrvu0f2j20v90gx74x.jpg)

### 2.2.2 消息中间件收集

​		调整`spring-cloud-zipkin-server` 通过 Stream 来收集

#### 1. 调整zipkin 服务器配置

 **Maven 依赖**

```xml
<!-- Zipkin 服务器通过Stream 收集跟踪信息 -->
<dependency>
	<groupId>org.springframework.cloud</groupId>
	<artifactId>spring-cloud-sleuth-zipkin-stream</artifactId>
</dependency>
<!-- 使用 rabbit 作为 Stream 服务器 -->
<dependency>
	<groupId>org.springframework.cloud</groupId>
	<artifactId>spring-cloud-stream-binder-rabbit</artifactId>
</dependency>
```

**激活 Zipkin Stream**

```java
package com.gupao.springcloudzipkinserverdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.sleuth.zipkin.stream.EnableZipkinStreamServer;
import zipkin.server.EnableZipkinServer;

@SpringBootApplication
//@EnableZipkinServer
@EnableZipkinStreamServer
public class SpringCloudZipkinServerDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringCloudZipkinServerDemoApplication.class, args);
	}
}
```



#### 2）调整相关需要需要上报的服务

**增加依赖**

```xml
<!-- 添加 sleuth Stream 收集方式 -->
<dependency>
	<groupId>org.springframework.cloud</groupId>
	<artifactId>spring-cloud-sleuth-stream</artifactId>
</dependency>
<dependency>
	<groupId>org.springframework.cloud</groupId>
	<artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
<!-- rabbitmq Binder -->
<dependency>
	<groupId>org.springframework.cloud</groupId>
	<artifactId>spring-cloud-stream-binder-rabbit</artifactId>
</dependency>
```

**含有http上报的URL配置都注释掉，并增加消息的相关配置**

> ```properties
> ### 增加 ZipKin 服务器地址
> #spring.zipkin.base-url = \
> #  http://${zipkin.server.host}:${zipkin.server.port}/
> 
> #并增加相应消息的配置，这里rabbitmq
> spring.rabbitmq.host=192.168.124.150
> spring.rabbitmq.port=5672
> spring.rabbitmq.username=admin
> spring.rabbitmq.password=admin
> spring.rabbitmq.virtual-host=admin_vhost
> ```



### 2.2.3 zipkin原理

参考：<https://www.cnblogs.com/duanxz/p/7552857.html>

  ​



