

# Spring Cloud Feign 服务调用

注意：Hystrix 可以是服务端实现，也可以是客户端实现，类似于 AOP 封装：正常逻辑、容错处理。

**申明式 Web 服务客户端：Feign**

申明式：接口声明、Annotation 驱动

Web 服务：HTTP 的方式作为通讯协议

客户端：用于服务调用的存根

Feign：原生并不是 Spring Web MVC的实现，基于JAX-RS（Java REST 规范）实现。Spring Cloud 封装了Feign ，使其支持 Spring Web MVC。`RestTemplate`、`HttpMessageConverter`

> `RestTemplate `以及 Spring Web MVC 可以显示地自定义 `HttpMessageConverter `实现。



假设，有一个Java 接口 `PersonService`, Feign 可以将其声明它是以 HTTP 方式调用的。

# 一、Feign  使用

​	Feign定义的接口使用`@FeignClient`注解，并且接口方法支持**继承**(使用RequestMapping,（GetMapping....）接口继承后仍然生效)。

​	

## 1.注册中心（Eureka Server）

**服务发现和注册**

a. 应用名称：spring-cloud-eureka-server

b. 服务端口：12345

application.properties:

```properties
spring.application.name = spring-cloud-eureka-server
## Eureka 服务器端口
server.port =12345

### 取消服务器自我注册
eureka.client.register-with-eureka=false
### 注册中心的服务器，没有必要再去检索服务
eureka.client.fetch-registry = false

management.security.enabled = false
```

## 2. Feign 声明接口（契约）

定义一种 Java 强类型接口

###  模块：person-api

​     注意接口中，必须指明是GET还是POST参数，另外方法中的参数也必须用`@RequestParam,@RequestBody,@PathVariable`等修饰，并且value值不能为null,Feign就是根据这个value值来寻找的（和WebMvc不同），不然报错405

```java
@FeignClient(value = "person-service") // 服务提供方应用的名称
public interface PersonService {

    /**
     * 保存
     *
     * @param person {@link Person}
     * @return 如果成功，<code>true</code>
     */
    @PostMapping(value = "/person/save")
    boolean save(@RequestBody Person person); 

    /**
     * 查找所有的服务
     *
     * @return
     */
    @GetMapping(value = "/person/find/all")
    Collection<Person> findAll();

}
```

## 3. Feign 客户（服务消费）端

​	这里用了接口的继承特性，不用在Controller层再次写RequestMapping等url映射。

**调用Feign 申明接口**

应用名称：person-client

 依赖：person-api

**创建客户端 Controller**

```java
@RestController
public class PersonClientController implements PersonService {

    private final PersonService personService;

    @Autowired
    public PersonClientController(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public boolean save(@RequestBody Person person) {
        return personService.save(person);
    }

    @Override
    public Collection<Person> findAll() {
        return personService.findAll();
    }
}

```

**创建启动类**

```java
@SpringBootApplication
@EnableEurekaClient
@EnableFeignClients(clients = PersonService.class)
public class PersonClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonClientApplication.class,args);
    }
}
```

**配置 application.properties**

```properties
spring.application.name = person-client

server.port = 8080

## Eureka Server 服务 URL,用于客户端注册
eureka.client.serviceUrl.defaultZone=\
  http://localhost:12345/eureka

management.security.enabled = false
```

## 4. Feign 服务（服务提供）端

**不一定强制实现 Feign 申明接口**

应用名称：person-service

依赖：person-api

**创建 `PersonServiceController**`

```java
@RestController
public class PersonServiceProviderController {

    private final Map<Long, Person> persons = new ConcurrentHashMap<>();

    /**
     * 保存
     *
     * @param person {@link Person}
     * @return 如果成功，<code>true</code>
     */
    @PostMapping(value = "/person/save")
    public boolean savePerson(@RequestBody Person person) {
        return persons.put(person.getId(), person) == null;
    }

    /**
     * 查找所有的服务
     *
     * @return
     */
    @GetMapping(value = "/person/find/all")
    public Collection<Person> findAllPersons() {
        return persons.values();
    }


}

```



**创建服务端应用**

```java
@SpringBootApplication
@EnableEurekaClient
public class PersonServiceProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonServiceProviderApplication.class,args);
    }

}
```

**配置 application.properties**

```properties
## 提供方的应用名称需要和 @FeignClient 声明对应
spring.application.name = person-service
## 提供方端口 9090
server.port = 9090
## Eureka Server 服务 URL,用于客户端注册
eureka.client.serviceUrl.defaultZone=\
  http://localhost:12345/eureka
## 关闭管理安全
management.security.enabled = false
```



> Feign 客户（服务消费）端、Feign 服务（服务提供）端 以及 Feign 声明接口（契约） 存放在同一个工程目录。



## 5. 调用顺序

PostMan -> person-client -> person-service

person-api 定义了 @FeignClients(value="person-service") , person-service 实际是一个服务器提供方的应用名称。

person-client 和 person-service 两个应用注册到了Eureka Server

person-client 可以感知 person-service 应用存在的，并且 Spring Cloud 帮助解析 `PersonService` 中声明的应用名称：“person-service”，因此 person-client 在调用 ``PersonService` `服务时，实际就路由到 person-service 的 URL

# 二、整合 Netflix Ribbon

官方参考文档：http://cloud.spring.io/spring-cloud-static/Dalston.SR4/single/spring-cloud.html#spring-cloud-ribbon

## 1. 关闭 Eureka 注册

不关闭Eureka会出现错误；

**调整 person-client 关闭 Eureka（根据前面介绍的，这里关闭的是Eureka对于服务实例的管理）**

```properties
ribbon.eureka.enabled = false
```

或者完全取消 Eureka 注册

```java
//@EnableEurekaClient //注释 @EnableEurekaClient
```



## 2. 定义服务 ribbon 的服务列表

（服务名称：person-service）

```properties
person-service.ribbon.listOfServers = http://localhost:9090,http://localhost:9090,http://localhost:9090
```

## 3. 自定义 Ribbon 的规则

### 3.1 接口和 Netflix 内部实现

* IRule
  * 随机规则：RandomRule
  * 最可用规则：BestAvailableRule
  * 轮训规则：RoundRobinRule
  * 重试实现：RetryRule
  * 客户端配置：ClientConfigEnabledRoundRobinRule
  * 可用性过滤规则：AvailabilityFilteringRule
  * RT权重规则：WeightedResponseTimeRule
  * 规避区域规则：ZoneAvoidanceRule

### 3.2 实现 IRule

```java
public class FirstServerForeverRule extends AbstractLoadBalancerRule {

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {

    }

    @Override
    public Server choose(Object key) {
        
        ILoadBalancer loadBalancer = getLoadBalancer();

        List<Server> allServers = loadBalancer.getAllServers();

        return allServers.get(0);
    }
}

```

### 3.3 暴露自定义实现为 Spring Bean

```java
@Bean
public FirstServerForeverRule firstServerForeverRule(){
  return new FirstServerForeverRule();
}
```

### 3.4 激活这个配置

```java
@SpringBootApplication
//@EnableEurekaClient
@EnableFeignClients(clients = PersonService.class)
@RibbonClient(value = "person-service", configuration = PersonClientApplication.class)
public class PersonClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonClientApplication.class, args);
    }

    @Bean
    public FirstServerForeverRule firstServerForeverRule() {
        return new FirstServerForeverRule();
    }
}
```

### 3.4 检验结果

通过调试可知：

```java
ILoadBalancer loadBalancer = getLoadBalancer();

// 返回三个配置 Server，即：
// person-service.ribbon.listOfServers = \
// http://localhost:9090,http://localhost:9090,http://localhost:9090
List<Server> allServers = loadBalancer.getAllServers();

return allServers.get(0);
```



## 4. 其他配置

​	比如一些ribbon的连接，调用超时，重试机制，都可以参考ribbon相关的配置；

通过<client>.ribbon.<key>=<value>配置即可。同样如果有超时设置，必须让Hystrix的超时时间大于这个时间，否则hystrix已经熔断了，那么这个时间就没有意义了。



# 三、整合 Netflix Hystrix

## 1. 服务降级案例使用

### 步骤1. 调整 Feign 接口

```java
@FeignClient(value = "person-service",fallback = PersonServiceFallback.class) // 服务提供方应用的名称
public interface PersonService {

    /**
     * 保存
     * @param person {@link Person}
     * @return 如果成功，<code>true</code>
     */
    @PostMapping(value = "/person/save")
    boolean save(@RequestBody Person person);

    /**
     * 查找所有的服务
     * @return
     */
    @GetMapping(value = "/person/find/all")
    Collection<Person> findAll();

}

```

### 步骤2. 添加 Fallback 实现

```java
public class PersonServiceFallback implements PersonService {

    @Override
    public boolean save(Person person) {
        return false;
    }

    @Override
    public Collection<Person> findAll() {
        return Collections.emptyList();
    }
}

```

### 步骤3. 调整客户端（激活Hystrix）

```java
@SpringBootApplication
@EnableEurekaClient
@EnableFeignClients(clients = PersonService.class)
@EnableHystrix
//@RibbonClient(value = "person-service", configuration = PersonClientApplication.class)
public class PersonClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonClientApplication.class, args);
    }

    @Bean
    public FirstServerForeverRule firstServerForeverRule() {
        return new FirstServerForeverRule();
    }
}
```



## 24 其他相关配置

### 2.1 全局配置

```properties
# 在配置hystrix，需要保证hystrix已经开始，没有开启其他配置不生效
feign.hystrix.enable=true

#设置全局超时时间
hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds=50
#这个可以关闭熔断功能
hystrix.command.default.execution.timeout.enable=false 
```

### 2.2 单个服务客户端配置

#### 1）关闭hystrix

步骤一：构建一个配置类

```java
@Configuration
public class DisableHystrixConfiguration {

    @Bean
    @Scope("prototype")
    public Feign.Builder feignBuilder(){
        return Feign.builder();
    }
}
```

步骤二：将配置类配置在`@FeignClient`的`configuration`中

```java
@FeignClient(value = "HELLO-SERVICE-PROVIDER",configuration = DisableHystrixConfiguration.class)
public interface HelloService {

    @GetMapping("/demo")
    String hello(@RequestParam("name") String name);
}
```



### 2.3 指定命令配置方式实现

​	指定命令就是单个方法，接口的调用，这里相比单个客户端更加细粒度了。

```properties
#这里hello表示Feign客户端中的方法名作为表示，如果方法名相同共同下面，所以对于一些方法名相同，需要自己注意，这里的意思就是hello方法
hystrix.command.hello.execution.isolation.thread.timeoutInMilliseconds=50

#当然其他配置通过hystrix.command.<commandKey>作为前缀，后面不同的属性可以具体参考其Hystrix
```



# 四、 请求压缩

​	Feign支持对请求和响应进行Gzip压缩，以减少通信中的性能损耗。默认是关闭的

```properties

feign.compression.request.enabled=true
feign.compression.response.enabled=true
feign.compression.request.mime-types=text/xml,application/xml,application/json,
#这是默认值，请求最小需要达到这个值才开启压缩功能
feign.compression.request.min-request-size=2048
```



 # 五、日志配置

​	**Feign提供的日志级别如下：**

- NONE:不记录日志信息
- BASIC：仅记录请求方法，URL以及相应状态码和执行时间
- HEADERS：记录出了BASIC级别信息之外，还会记录请求头和响应头信息
- FULL：记录所有的请求和响应的明细，包括头信息，请求体，元数据等

​	

​		Spring-cloud对于@FeignClient注解修饰的客户端，会为每一个客户端都创建一个feign.logger实例，我们可以通过该日志对象的DEBUG模式帮助我们分析Fegin的请求细节。通过logger.level.<FeignClient>指定需要开启的客户端日志.如下：

## 步骤一：配置添加

​	当然光配置这个日志功能还不能生效，默认客户端配置的还是NONE

```properties
logger.level.com.vison.feign.HelloService=DEBUG
```

## 步骤二：应用添加Bean

**方式一：应用主类中添加**

```java
@EnableDiscoveryClient
@EnableFeignClients(clients = HelloService.class)
@SpringBootApplication
public class FeignConsumerApplication {

    @Bean
    Logger.Level feignLoggerLevel(){
        return Logger.Level.FULL;
    }

    public static void main(String[] args) {
        SpringApplication.run(FeignConsumerApplication.class, args);
    }

}
```

**方式二：通过配置类实现**

​	新增配置类

```java
@Configuration
public class FullLoggerConfiguration {
    @Bean
    Logger.Level feignLoggerLevel(){
        return Logger.Level.FULL;
    }
}
```

将配置类添加进@FeignClient的配置中去

```java
@FeignClient(value = "HELLO-SERVICE-PROVIDER",configuration = FullLoggerConfiguration.class)
public interface HelloService {

    @GetMapping("/demo")
    String hello(@RequestParam("name") String name);
}
```









