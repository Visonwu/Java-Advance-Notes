目前注册中心可以有：

**Zookeeper，Eureka，Consul，Etcd，Nacos等**



**Eureka拥有** 

- 服务管理，

- 服务注册中心，

- 健康检查，

- 监控功能，

- 以及服务发现等功能，



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
### Eureka Server 服务端口 ，设置为0表示自己生成一个随机端口
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



# 5.Eureka服务治理机制

## 5.1 服务提供者

#### 1）服务注册

​	服务提供者在启动的时候会通过REST请求将自己注册到Eureka Server中，并且会携带自己的元信息，Eureka Server收到信息后，会把元信息存储到一个双层Map中，第一个key为服务名，第二key为实例名。

```properties
eureka.client.register-with-eureka=ture
#服务注册的时候需要确认这个是开启的，默认开启； false是不会注册的
```

#### 2）服务同步

​	在Eureka高可用的时候，两个不同的服务提供者分别注册在一个Eureka server集群中，Eureka server会把各自获取的服务提供者，相互交换信息。所以通过任何一台Eureka Server注册中心都可以获取到服务提供者。

#### 3）服务续约

​	在注册完服务后，服务提供者会维护一个心跳来持续告诉Eureka Server:"我还活着"，防止Eureka Sever将当前提供者从服务列表中剔除。

```properties
eureka.instance.lease-renewal-interval-in-secondes=30
#定义服务续约任务的调用间隔时间，默认 30s

eureka.instance.lease-expiration-duration-in-seconds=90
#参数用于定义服务失效的时间，默认90s
```

## 5.2 服务消费者

#### 1）获取服务

​	消费者每隔一定时间会向Eureka Sever服务端获取服务列表。

```properties
eureka.client.fetch-registry=true
#保证这个是true，才能保证支持获取服务，默认是true

eureka.client.registry-fetch-interval-seconds=30
#希望修改缓存清单的更新时间，默认是30s
```

#### 2） 服务调用

​	服务消费者获取到服务清单和元信息后，可以根据自己的需要选择调用哪一个服务，在Ribbon中采用轮询进行调用，实现负载均衡。

  	对于访问的实例，Eureka有Region和Zone的概念，一个Region中可以包含多个Zone，每一个服务客户端需要被注册在Zone中，所以每个客户端对应一个Region和Zone，所以我们可以通过Region设置区域的概念，如果在当前区域访问不到，在访问其他区域，实现物理上的地区划分访问。

#### 3) 服务下线

​	在服务面临关闭的时候，会触发一个下线的REST请求给Eureka Server,Eureka Server收到请求后，将当前服务状态置为DOWN,并把这台服务下线广播出去。



## 5.3 服务注册中心

#### 1） 失效剔除

​	有时候服务实例不一定会正常下线，可能是由于内存溢出，网络故障等原因导致服务不能正常工作。为了剔除这些实例，Eureka Server在启动开始会创建一个定时任务，默认每隔一段时间（**默认60s**）将当前清单中超时（**90s**)没有续约的服务剔除掉。

#### 2）自我保护

​		在我们本地调试过程中，也许会出现一些**EMEGENCY**警告信息，这些触发了Eureka Server的保护机制。Eureka Server对于注册到注册中心的实例，统计心跳的比例在15分钟之内是否低于85%。如果低于的化，Eureka Server会将这些实例注册信息保护起来，让这些实例尽量不过期，尽可能保护这些注册信息。如果在保护期间，消费端拿到这些实例，就有可能出现问题，所以客户端需要有容错机制，比如请求重试，断路器等。

```properties
eureka.server.enable-self-preservation=false
#线下调试的时候通过这个参数关闭保护机制，确保不可用的实例被剔除掉
```



# 6.源码分析

![image](//ws2.sinaimg.cn/mw690/b8a27c2fgy1g3d9xb1nqhj20tk0b30vt.jpg)



这里`EurekaDiscoveryClient`和接口`DiscoveryClient`是属于`spring-cloud-netflix-eureka` ，EurekaDiscoveryClient依赖netflix的`EurekaClient`接口，`EurekaClient`接口的实现是`DiscoveryClient`类



所以主要是使用了netflix的`DiscoeryClient`类,它的作用：

- 向Eureka server注册服务实例
- 向Eureka server 服务续约
- 当服务关闭时，向Eureka server取消续约
- 查询Eureka server的服务实例列表



## 6.1 Eureka server 的url列表配置

通过查找`eureka.client.serverUrl.defaultZone`发现@link到了`com.netflix.discovery.endpoint.EndpointUtils`中,所以

```java
/**
     * Get the list of all eureka service urls from properties file for the eureka client to talk to.
     *
     * @param clientConfig the clientConfig to use
     * @param instanceZone The zone in which the client resides
     * @param preferSameZone true if we have to prefer the same zone as the client, false otherwise
     * @return an (ordered) map of zone -> list of urls mappings, with the preferred zone first in iteration order
     */
    public static Map<String, List<String>> getServiceUrlsMapFromConfig(EurekaClientConfig clientConfig, String instanceZone, boolean preferSameZone) {
        Map<String, List<String>> orderedUrls = new LinkedHashMap<>();
        String region = getRegion(clientConfig);
        String[] availZones = clientConfig.getAvailabilityZones(clientConfig.getRegion());
        if (availZones == null || availZones.length == 0) {
            availZones = new String[1];
            availZones[0] = DEFAULT_ZONE;
        }
       ......
        int myZoneOffset = getZoneOffset(instanceZone, preferSameZone, availZones);

        String zone = availZones[myZoneOffset];
        List<String> serviceUrls = clientConfig.getEurekaServerServiceUrls(zone);
        if (serviceUrls != null) {
            orderedUrls.put(zone, serviceUrls);
        }
           .......
        return orderedUrls;
    }
```



#### 1）Region, Zone

- 从上面看依次加载 了Region和Zone，其中`GetRegion`函数获取到的是一个Region，所以要给微服务应用**只能属于一个Region**，如果不配置默认是**default**。记得自己看源码

  ```properties
  eureka.client.region = default
  # 可以通过region配置region的值，默认为default
  ```

- `getAvailabilityZones`函数可得没有为Region配置Zone的时候，默认采用defaultZone。这个也是`eureka.client.serviceUrl.defaultZone`的由来；并且由此可以判定Region和zone是一对多的关系

  ```properties
  eureka.client.availability-zones=
  #通过这个来设置zone的属性，多个zone用逗号隔开
  ```

#### 2）serviceUrl

​	在加载完了Region和zone后才开始获取serviceUrl，可以发现这里是通过EurekaClientConfigBean来获取这个serviceUrl的，并且多个serviceUrl是通过逗号隔开的

​	当我们在微服务中使用ribbon实现服务调用时，对于zone的设置可以在负载均衡时实现区域亲和性，Ribbon的默认策略会优先访问同一个Zone中的服务，只有当zone的集群中没有可用实例，才会访问其他Zone中的实例。通过这个可以有效设计出区域故障的容错集群。



## 6.2 服务注册

​	`com.netflix.discovery.DiscoveryClient`中的构造函数中调用了 `initScheduledTasks`方法，初始化定时任务，这里包含

- `InstanceInfoReplicator ` 定时任务（看run方法）这个通过Rest负责注册实例，发起注册的信息时`InstanceInfo`对象



## 6.3 服务发现和续约

initScheduledTasks 方法中还有两个定时任务分别时服务发现和续约。

这服务续约和服务注册 定时任务位于通一个if条件中，就只有在服务提供注册才能实现续约功能`if (clientConfig.shouldRegisterWithEureka()) {`



## 6.4 服务注册中心的处理

​	通过上面发现所有的交互都是通过REST请求发起的，那么服务注册中心的请求处理都放在com.netflix.eureka.resources包下。

那么对于服务注册中心的`AppicationResource`类处理添加服务实例的，源码：

```java
@POST
@Consumes({"application/json", "application/xml"})
public Response addInstance(InstanceInfo info,
                            @HeaderParam(PeerEurekaNode.HEADER_REPLICATION) String isReplication) {
    .....
    // validate that the instanceinfo contains all the necessary required fields
    if (isBlank(info.getId())) {
        return Response.status(400).entity("Missing instanceId").build();
    } else if (isBlank(info.getHostName())) {
     .....

    // handle cases where clients may be registering with bad DataCenterInfo with missing data
    DataCenterInfo dataCenterInfo = info.getDataCenterInfo();
    ....

    registry.register(info, "true".equals(isReplication));
    return Response.status(204).build();  // 204 to be backwards compatible
}
```

​	通过一系列的验证后，通过InstanceRegistry把当前实例注册起来，并通过publishEvent函数将注册时间传播出去，最后调用`AbstractInstanceRegistry`父类中的注册实现将InstanceInfo元信息存储在concurrentHashMap中。这里是双层的Map

```java
// AbstractInstanceRegistry
private final ConcurrentHashMap<String, Map<String, Lease<InstanceInfo>>> registry
            = new ConcurrentHashMap<String, Map<String, Lease<InstanceInfo>>>();
```

第一个key是InstanceInfo的 appName，第二个key是InstanceInfo的InstanceId



最后服务端的请求和接受类似。



# 7.Eureka配置

 Eureka配置:	主要分为**客户端和服务端的配置**

- 客户端配置查看：`EurekaClientConfigBean`
- 服务端配置查看：`EurekaServerConfigBean`



大部分我们都是使用客户端配置，所以这里详解客户端配置，**客户端配置分为两类：**

	- 服务注册配置：包含服务注册中心地址，服务获取的间隔时间，可用区域等
	- 服务实例配置：包含实例名称，Ip,端口号，健康检查路径等



​	为了注册中心的安全考虑，很多时候我们会为服务注册中心加入安全校验，这个时候在配置ServiceUrl时，我们需要加入安全校验，例如`http://<username>:<password>@localhost:6079/eureka`。这里username为安全用户校验名，password为用户密码。



## 7.1 客户端服务注册相关配置

Eureka客户端相关配置,这里都是以EurekaClientConfigBean做参考，这里都以eureka.client开头：

| 参数名                                        | 说明                                                         | 默认值 |
| --------------------------------------------- | ------------------------------------------------------------ | ------ |
| enabled                                       | 启用Eureka客户端                                             | true   |
| registryFetchIntervalSeconds                  | 从Eureka服务端获取注册信息的间隔时间，单位：秒               | 30     |
| instanceInfoReplicationIntervalSeconds        | 更新实例信息的变化到Eureka服务端的间隔时间，单位：秒         | 30     |
| initialInstanceInfoReplicationIntervalSeconds | 初始化实例信息到Eureka服务端的间隔时间，单位：秒             | 40     |
| eurekaServiceUrlPollIntervalSeconds           | 轮询Eureka服务端地址更改的间隔时间，单位为秒，当我们与Spring-cloud-config配置，动态刷新Eureka的serviceURL地址时需要关注该参数 | 300    |
| eurekaServerReadTimeoutSeconds                | 读取Eureka Server信息超时间，单位为秒                        | 8      |
| eurekaServerConnectTimeoutSeconds             | 连接Eureka Server的超时时间，单位为秒                        | 5      |
| eurekaServerTotalConnectionsPerHost           | 从Eureka客户端到所有的Eureka服务端的连接总数                 | 200    |
| erekaConnectionIdleTimeoutSeconds             | Eureka服务端连接的空闲关闭时间，单位为秒                     | 30     |
| heartbeatExecutorThreadPoolSize               | 心跳连接池的初始化线程数                                     | 2      |
| heartbeatExecutorExponentialBackOffBound      | 心跳超市重试延迟时间的最大乘数值                             | 10     |
| cacheRefreshExecutorThreadPoolSize            | 缓存刷新线程池的初始化线程数                                 | 2      |
| cacheRefreshExecutorExponentialBackOffBound   | 缓存刷新重试延迟时间的最大乘数值                             | 10     |
| useDnsForFetchingServiceUrls                  | 使用DNS来获取Eureka服务端的serviceUrl                        | false  |
| registerWithEureka                            | 是否要将自身的实例信息注册到Eureka服务端                     | true   |
| preferSameZoneEureka                          | 是否偏好使用处于相同Zone的Eureka服务端                       | true   |
| filterOnlyUpInstanceces                       | 获取实例时是否过滤，仅保留UP状态的实例                       | true   |
| fetchRegistry                                 | 是否从Eureka服务端获取注册信息                               | true   |



## 7.2 客户端服务实例相关配置

​	这里的实例信息主要参考`EurekaInstanceConfigBean`获取更加详细的内容

**元数据**：注册自己时，包含自身信息的对象，包含服务名称，实例名称，ip，端口等

​	我们通过`eureka.instance.<property>=<value>`对元数据进行配置

​	

#### 1）实例名配置

​	 实例名即InstanceInfo中的instanceId参数，它是区分同一服务中不同实例的唯一标识；原生的Eureka中，实例名采用主机名作为默认值，这样在同一主机上就不能启动多个相同的服务实例。所以针对同一主机中启动多个实例，对实例名做了扩展。如下：

```properties
${spring.cloud.client.hostname}:${spring.application.name}:${spring.application.instance_id:${server.port}}
```

当然可以通过不同的端口实现，这里使用random函数

```properties
eureka.instance.instaceId=${spring.application.name}:${random.int}
```

#### 2）端点配置

​	在InstanceInfo中有一些url配置信息，如:homePageUrl,statusPageUrl,healthCheckUrl；他们分别代表了应用主页的URL，状态页的URL，健康检查的URL；后两者默认是使用 了Spring-boot-actuator模块提供的/info端点和/health端点。默认不需要做修改，有时候需要做一定的服务名前缀，例如：

```properties
management.context-path=/hello

eureka.instance.statusPageUrl=${management.context-path}/info
eureka.instance.healthCheckUrl=${management.context-path}/health
```

​	上面都是使用了相对路径进行配置，而且默认都是http。所以还可以使用绝对路径

```properties
eureka.instance.statusPageUrl=https://${eureka.instance.hostname}/info
eureka.instance.healthCheckUrl=${eureka.instance.hostname}/health
eureka.instance.HomePageUrl=${eureka.instance.hostname}/
```



#### 3）健康检查

​	默认情况下eureka的服务不是交给spring-boot-actuator实现的。依靠客户端的心跳机制来实现的。可以引入这个模块配置

```properties
eureka.client.healthcheck.enabled=ture 
# spring-boot-actuator 做健康检查
```



#### 4）其他配置

| 参数名                           | 说明                                                         | 默认值 |
| -------------------------------- | ------------------------------------------------------------ | ------ |
| preferIpAddress                  | 是否优先使用Ip地址作为主机的标识                             | false  |
| leaseRenewallIntervalInSeconds   | Eureka客端向服务端发送心跳的时间间隔，单位为秒               | 30     |
| leaseExpirationDurationInSeconds | Eureka服务端在收到最后一次心跳之后等待的时间上限，单位为秒。超过该时间之后服务端会将该服务实例从服务清单剔除，从而进制服务调用请求发送到该实例上 | 90     |
| nonSecurePort                    | 非安全的通信端口号                                           | 80     |
| securePort                       | 安全的通信端口号                                             | 443    |
| nonSecurePortEnabled             | 是否启用非安全的通信端口号                                   | true   |
| securePortEnabled                | 是否启用安全的通信端口号                                     |        |
| appName                          | 服务名，默认取spring.application.name得配置值，如果没有去unknown |        |
| hostname                         | 主机名，不配置得时候将根据操作系统得主机名来获取             |        |



# 8.Eureka 跨平台支持

​	eureka使用http 得rest接口通信

## 8.1 通信协议

​	默认使用Jersey和Xstream配合JSON作为server和client之间的通信协议。

**Xstream**是将Xml或者Json和对象序列化的反序列的java类库

**Jersey**是JAX-RS的参考实现

![image](//ws1.sinaimg.cn/mw690/b8a27c2fgy1g3ddl8kzmuj21970ojql8.jpg)



# 9. Eureka 可以替换为 zookeeper和consoul 那么这几个使用有什么差异?

答：

https://www.consul.io/intro/vs/zookeeper.html

https://www.consul.io/intro/vs/eureka.html



# 10 spring-cloud-eureka集成第三方注册中心

​		主要是通过`org.springframework.cloud.client.discovery.DiscoveryClient`抽象完成不同的服务注册，

这里可以使用eureka，zookeeper，consul

依赖配置：pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>spring-cloud-all</artifactId>
        <groupId>com.gupaoedu</groupId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>spring-config-client</artifactId>

    <dependencies>
        <!-- Eureka 服务发现与注册客户端依赖 -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        
        <!-- Zookeeper 服务发现与注册客户端依赖 -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-zookeeper-discovery</artifactId>
        </dependency>
        
        <!-- Consul 服务发现与注册客户端依赖 -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-consul-discovery</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>

</project>
```



​			`spring.yaml`文件配置，如下通过配置将eureka，consul，zookeeper服务注册发现都关闭，然后通过profile自定义开启，避免冲突

```yaml
spring:
  application:
    name: config-client
  cloud:
    zookeeper:
      enabled: false # Zookeeper 服务发现与注册失效，这里关闭它，
    consul:
      discovery:
        enabled: false # Consul 服务发现与注册失效，这里关闭它

server:
  port: 0 #随机端口

## 默认 profile 关闭自动特性 设置eureka注册发现用来关闭避免影响其他服务注册信息
eureka:
  client:
    enabled: false # Eureka 服务发现与注册失效（默认）



--- # Profile For Eureka
spring:
  profiles: eureka
# Eureka 客户端配置
eureka:
  server: # 官方不存在的配置（自定义配置）,这是服务器地址配置
    host: 127.0.0.1
    port: 12345
  client:
    enabled: true
    serviceUrl:
      defaultZone: http://${eureka.server.host}:${eureka.server.port}/eureka
    registryFetchIntervalSeconds: 5 # 5 秒轮训一次
  instance:
    instanceId: ${spring.application.name}:${server.port}

--- # Profile For Zookeeper
spring:
  profiles: zookeeper # spring-cloud-zookeeper可以通过ZookeeperProperties查看具体配置
  cloud:
    zookeeper:
      enabled: true    #开启注册发现功能
      connectString: 127.0.0.1:2181

--- # Profile For Consul
spring:
  profiles: consul
  cloud:
    consul:
      discovery:
        enabled: true  #开启注册发现功能
        ipAddress: 127.0.0.1
        port: 8500

```

