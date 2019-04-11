

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

