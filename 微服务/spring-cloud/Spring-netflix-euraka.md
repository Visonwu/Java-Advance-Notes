

# 1.运行环境

Spring Cloud 系列实战运行环境如下：

​	注意：这里用了Actuator，euraka客户端和服务端分别引入`spring-cloud-starter-eureka`，`spring-cloud-starter-eureka-server`



**容器/框架**

- Spring Cloud Dalston.SR3

- Spring Boot 1.5.x

- Spring Framework 4.3.x

**Java 运行时**

- Java 8

**构建工具**

- Maven 3.5.0

架构图：

![](http://ww1.sinaimg.cn/mw690/b8a27c2fly1g1z3ni3g2yj211b12p76b.jpg)

# 2.Euraka 服务端

如上图：Euraka服务端包含了服务管理，服务注册中心，健康检查，监控功能。	

所以 Euraka做服务发现和注册中心地址；服务端不用注册，因为它本身自带注册中心

```properties

### Eureka Server 应用名称
spring.application.name = spring-cloud-eureka-server
### Eureka Server 服务端口
server.port= 9090
### 取消服务器自我注册
eureka.client.register-with-eureka=false
### 注册中心的服务器，没有必要再去检索服务
eureka.client.fetch-registry = false
## Eureka Server 服务 URL,用于客户端注册
eureka.client.serviceUrl.defaultZone= http://localhost:${server.port}/eureka

```

```java
@SpringBootApplication
@EnableEurekaServer
public class SpringCloudEurekaServerDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringCloudEurekaServerDemoApplication.class, args);
	}
}

```



# 3.Euraka 客户端

## 3.1 服务提供方

 ### 1）启动类

```java
@SpringBootApplication
@EnableDiscoveryClient
public class UserServiceProviderBootstrap {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceProviderBootstrap.class, args);
    }
}

```

 ### 2）Restful提供

```java
 //注意这里有UserService的是具体实现，这里没有贴代码而已

@RestController
public class UserServiceProviderRestApiController {

    @Autowired
    private UserService userService; 

    /**
     * @param user User
     * @return 如果保存成功的话，返回{@link User},否则返回<code>null</code>
     */
    @PostMapping("/user/save")
    public User saveUser(@RequestBody User user) {
        if (userService.save(user)) {
            System.out.println("UserService 服务方：保存用户成功！" + user);
            return user;
        } else {
            return null;
        }
    }

    /**
     * 罗列所有的用户数据
     *
     * @return 所有的用户数据
     */
    @GetMapping("/user/list")
    public Collection<User> list() {
        return userService.findAll();
    }

}

```

 ### 3) application.properties配置

```properties
spring.application.name = user-service-provider

## Eureka 注册中心服务器端口
eureka.server.port = 9090

## 服务提供方端口
server.port = 7070

## Eureka Server 服务 URL,用于客户端注册
eureka.client.serviceUrl.defaultZone=\
  http://localhost:${eureka.server.port}/eureka

## Management 安全失效
management.security.enabled = false

```

## 3.2 服务消费方

 ### 1） 启动类

```java
@SpringBootApplication
@EnableDiscoveryClient
public class UserServiceConsumerBootstrap {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceConsumerBootstrap.class, args);
    }

    @LoadBalanced  //这里有负载均衡的意思
    @Bean
    public RestTemplate restTemplate(){
        return new RestTemplate();
    }

}

```



### 2）消费方调用

```java
@RestController
public class UserRestApiController {

    @Autowired
    private UserService userService;

    /**
     * @param name 请求参数名为“name”的数据
     * @return 如果保存成功的话，返回{@link User},否则返回<code>null</code>
     */
    @PostMapping("/user/save")
    public User saveUser(@RequestParam String name) {
        User user = new User();
        user.setName(name);
        if (userService.save(user)) {
            return user;
        } else {
            return null;
        }
    }

    /**
     * 罗列所有的用户数据
     * @return 所有的用户数据
     */
    @GetMapping("/user/list")
    public Collection<User> list() {
        return userService.findAll();
    }

}

```



### 3）消费方Restful调用

```java
@Service
public class UserServiceProxy implements UserService {
	//这里的user-service-provider 是提供方的应用名称
    private static final String PROVIDER_SERVER_URL_PREFIX = "http://user-service-provider";

    /**
     * 通过 REST API 代理到服务器提供者
     */
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public boolean save(User user) {
        User returnValue =
                restTemplate.postForObject(PROVIDER_SERVER_URL_PREFIX + "/user/save", user, User.class);
        return returnValue != null;
    }

    @Override
    public Collection<User> findAll() {
        return restTemplate.getForObject(PROVIDER_SERVER_URL_PREFIX + "/user/list", Collection.class);
    }
}

```



 ### 4）application.properties配置

```properties
spring.application.name = user-service-consumer

## Eureka 注册中心服务器端口
eureka.server.port = 9090

## 服务消费方端口
server.port = 8080

## Eureka Server 服务 URL,用于客户端注册
eureka.client.serviceUrl.defaultZone=\
  http://localhost:${eureka.server.port}/eureka

## Management 安全失效
management.security.enabled = false

```



# 4.Euraka的高可用

涉及客户端和服务端的的高可用：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g261n3j8y5j20ff0a2dgh.jpg)

## 4.1 Euraka客户端高可用

### 1) 高可用注册中心集群

​	 只需要增加 Eureka 服务器注册URL；如果 Eureka 客户端应用配置多个 Eureka 注册服务器，那么默认情况只有第一台可用的服务器，存在注册信息。如果 第一台可用的 Eureka 服务器 Down 掉了，那么 Eureka 客户端应用将会选择下一台可用的 Eureka 服务器。，

```properties
## Eureka Server 服务 URL,用于客户端注册,逗号分隔
eureka.client.serviceUrl.defaultZone=\
  http://localhost:9090/eureka,http://localhost:9091/eureka
```

#### 配置源码(EurekaClientConfigBean)

​	配置项 `eureka.client.serviceUrl` 实际映射的字段为 `serviceUrl `，它是 Map 类型，

**Key 为自定义**，默认值`defaultZone`，value 是需要配置的Eureka 注册服务器URL。

```java
private Map<String, String> serviceUrl = new HashMap<>();
{
  this.serviceUrl.put(DEFAULT_ZONE, DEFAULT_URL);
}

```

**value 可以是多值字段**，通过`,` 分割，源码如下：

```java
String serviceUrls = this.serviceUrl.get(myZone);
if (serviceUrls == null || serviceUrls.isEmpty()) {
   serviceUrls = this.serviceUrl.get(DEFAULT_ZONE);
}
if (!StringUtils.isEmpty(serviceUrls)) {
   final String[] serviceUrlsSplit = StringUtils.commaDelimitedListToStringArray(serviceUrls);
}

```



### 2）获取注册信息时间间隔

Eureka 客户端需要获取 Eureka 服务器注册信息，这个方便服务调用。

> Eureka 客户端：EurekaClient,关联应用集合：Applications
>
> 单个应用信息：Application，关联多个应用实例
>
> 单个应用实例：InstanceInfo

​	当 Eureka 客户端需要调用具体某个服务时，比如user-service-consumer 调用user-service-provider，user-service-provider实际对应对象是Application,关联了许多应用实例(InstanceInfo)。

​	如果应用user-service-provider的应用实例发生变化时，那么user-service-consumer是需要感知的。比如：user-service-provider机器从10 台降到了5台，那么作为调用方的user-service-consumer需要知道这个变化情况。可是这个变化过程，可能存在一定的延迟，可以通过调整注册信息时间间隔来减少错误。

消费端加上如下配置

```properties
## 调整注册信息的获取周期，默认值：30秒
eureka.client.registryFetchIntervalSeconds = 5
```



### 3）获取信息复制时间间隔

​	具体就是客户端信息的上报到 Eureka 服务器时间。当 Eureka 客户端应用上报的频率越频繁，那么 Eureka 服务器的应用状态管理一致性就越高。

```properties
## 调整客户端应用状态信息上报的周期 
eureka.client.instanceInfoReplicationIntervalSeconds = 5
```

> Eureka 的应用信息获取的方式：拉模式
>
> Eureka 的应用信息上报的方式：推模式



### 4) 实例ID

​	从 **Eureka Server Dashboard** 里面可以看到具体某个应用中的实例信息，比如：

`UP (2) - 192.168.1.103:user-service-provider:7075 , 192.168.1.103:user-service-provider:7078`



其中，它们命名模式：${hostname}:${spring.application.name}:${server.port}

**源码类**：`EurekaInstanceConfigBean`

**客户端配置**

```properties
## Eureka 应用实例的ID
eureka.instance.instanceId = ${spring.application.name}:${server.port}
```



### 5) 服务端点映射

​	这个是从`Eureka Server Dashboard`的status看到服务注册在注册中心的地址信息列表，当你点击这个链接这个地址应该跳转到哪个网址？

**源码位置**：`EurekaInstanceConfigBean`

```java
private String statusPageUrlPath = "/info"; //默认值，
```

**客户端配置：**

```properties
## Eureka 客户端应用实例状态 URL  ；可以修改为这个health
eureka.instance.statusPageUrlPath = /health
```



## 4.2 Euraka服务端高可用

**原理：**构建 Eureka 服务器相互注册

**服务器一**：如下：Eureka Server 1 -> 设置Profile : peer1 -----即application-peer1.properties

```properties
### Eureka Server 应用名称
spring.application.name = spring-cloud-eureka-server
### Eureka Server 服务端口
server.port= 9090
### 服务器注册 另外一个Eureka服务器
eureka.client.register-with-eureka=true
### 注册中心的服务器，检索另外一个Eureka服务器
eureka.client.fetch-registry = true
## Eureka Server 服务 URL,用于客户端注册
## 当前 Eureka 服务器 向 9091（Eureka 服务器） 复制数据
eureka.client.serviceUrl.defaultZone=\
  http://localhost:9091/eureka
```



**服务器二** ：如下：Eureka Server2 -> 设置Profile : peer2 -----即application-peer2.properties

```properties
### Eureka Server 应用名称
spring.application.name = spring-cloud-eureka-server
### Eureka Server 服务端口
server.port= 9091
### 服务器注册 另外一个Eureka服务器9090
eureka.client.register-with-eureka=true
### 注册中心的服务器，检索另外一个Eureka服务器
eureka.client.fetch-registry = true
## Eureka Server 服务 URL,用于客户端注册
## 当前 Eureka 服务器 向 9090（Eureka 服务器） 复制数据
eureka.client.serviceUrl.defaultZone=\
  http://localhost:9090/eureka
```



​	可以在IDEA的启动参数(本地测试写在同一个项目中的两个配置文件)中通过通过`--spring.profiles.active=peer1` 和 `--spring.profiles.active=peer2` 分别激活 Eureka Server 1 和 Eureka Server 2



# 5.eureka 可以替换为 zookeeper和consoul 那么这几个使用有什么差异?

答：

https://www.consul.io/intro/vs/zookeeper.html

https://www.consul.io/intro/vs/eureka.html