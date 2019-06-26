		**spring-cloud-config**是Spring-Cloud团队全新的一个项目，用来对分布式系统的基础设施和微服务提供集中化配置处理。

例子：可以参考zuul整合案例

# 1.Spring-Cloud配置相关信息

## 1.1 Environment仓储

{application}:配置使用客户端名称

{profile}:客户端spring.profiles.active配置

{label}:服务端配置文件版本标识

**这个是配置文件的信息** 

- {application}-{profile}.properties    

- label是git的分支

## 1.2 SpringCloud分布式配置

 **git实现：**
 - **服务端配置**
     ​	spring.cloud.config.server.git.uri
       ​  spring.cloud.config.server.git.*

- **客户端**
  ​	spring.cloud.config.uri
  ​	spring.cloud.config.name
  ​	spring.cloud.config.profile
  ​	spring.cloud.config.label



# 2. Spring-Cloud服务端配置

​		这里从git获取配置信息，其实是通过**git clone**获取配置中心的配置，有时候即使git服务器挂了，**config-server**也可以从自己本地返回配置信息

前提：新建springcloud-config-server项目，引入`config-server，actuator和web`

**步骤一：**在Configuration Class(可以在主程序上run方法类)上标记`@EnableConfigServer`

**步骤二：**配置文件目录基于（git），也可以基于SVN等，

​			这里的文件放在（D:/workspace/spring-cloud/config，这里是有.git目录中）

> ​	1.vison.properties （默认环境），跟着代码仓库
>
> ​	2.vison-dev.properties （profile="dev"） 开发环境		
>
> ​	3.vison-dev.properties （profile="test"） 测试环境
>
> ​	4.vison-dev.properties （profile="prod"） 生产环境

​	

**步骤三：**服务端配置版本仓库(这里用的本地仓库)application.properties中配置

```properties
spring.cloud.config.server.git.uri =file:///D:/workspace/spring-cloud/config
```

完整配置：

```properties
spring.application.name = config-server
sever-port=9090
spring.cloud.config.server.git.uri =file:///D:/workspace/spring-cloud/config
#配置仓库的相对路径搜索地址，可以配置多个
#spring.cloud.config.server.git.search-paths=file1

###全局关闭Actuator安全
# management.security.enable=false
## 细粒度开放 Actuator Endpoints ;sensitive 关注铭感性，安全
endpoints.env.sensitive = false

spring.cloud.config.server.git.username=username
spring.cloud.config.server.git.password=password
```



**步骤四：**通过访问localhost:9090/vison-default.properties  可以获取到相应配置的内容



# 3. Spring-Cloud客户端配置

几乎和服务端类似



前提：新建springcloud-config-client项目，引入`config-client，actuator和web`

**步骤一：**在Configuration Class(可以在主程序上run方法类)上标记`@EnableConfigClient`，新版本直接导入依赖即可，不用注解了

**步骤二：**application.properties中配置

```properties
spring.application.name = config-client
sever-port=8080
###全局关闭Actuator安全
# management.security.enable=false
## 细粒度开放 Actuator Endpoints ;sensitive 关注铭感性，安全
endpoints.env.sensitive = false
```

**步骤三：**同时在`application.properties`同目录下新建bootstrap.properties文件。然后配置

```properties
#bootstrap上下文配置

#相当于配置了 uri/{application}-{profile}.properties;
#配置服务器uri,根路径+端口即可
spring.cloud.config.uri = http://localhost:9090/
#配置客户端应用名称{application},就是配置文件的其实名称，比如vison.properties中的vison
spring.cloud.config.name = vison
#profile 是激活的配置
spring.cloud.config.profile = dev
#label指的时git在分支中的名称，如果是master就是master了
spring.cloud.config.label = master
```

**步骤四：**访问`localhost:8080/env`查看获取到的配置信息

注意：当服务端的配置文件修改后，服务端`localhost:9090/env`可以实时查看到最新信息，

但是客户端如果不重启；`localhost:8080/env`获取到的信息还是原始的。

可以通过`localhost:8080/refresh`刷新一下，这里需要在application.properties配置中添加

```properties
endpoints.refresh.sensitive = false
```



# 4. 动态配置属性Bean

- @RefreshScope  ： 一般用在开关，阈值，文案等内容刷新

- RefreshEndpoint

- ContextRefresher

```java
//在服务端的配置文件内容修改后，客户端如果不重启，怎么让代码中的值动态修改，这里需要添加@RefreshScope表示当前refresh的范围。

//同样需要调用  localhost:8080/refresh  刷新一下值，然后再次访问localhost:8080/my-name 可以获取新值

@RefreshScope
@Controller
public class EndPointController {

    @Value("${my.name}")   //上面的vison-prod.properties中配置的是  my.name=123
    private String myName; 
    @Autowired
    private Environment environment; //也是可以根据这个获取配置属性

    @RequestMapping("/my-name")
    public String getName(){
        return  myName;
    }

}
```



```java

//通过ContextRefresher 定时刷新服务端的配置信息;类似zookeeper的推拉机制，通过watcher更新配置信息

@SpringBootApplication
public class SpringcloudConfigApplication {

    private final ContextRefresher contextRefresher;

    private final Environment environment;

    @Autowired
    public SpringcloudConfigApplication(ContextRefresher contextRefresher,
                                        Environment environment) {
        this.contextRefresher = contextRefresher;
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringcloudConfigApplication.class, args);
    }

    /**
     * 定时任务，5秒钟刷新一次，初始延迟3秒刷新 配置
     */
    @Scheduled(fixedRate = 5*1000 , initialDelay = 3*1000)
    public void autoRefresh(){
        Set<String> updateNames = contextRefresher.refresh();//如果服务端有配置更新了，这里返回配置的属性名
        updateNames.forEach(property->{
                 System.out.printf("[Thread-%s] key:%s , vlaue:%s",
                    Thread.currentThread().getName(),
                    property,
                    environment.getProperty(property));
        });
    }

}
```



# 4 健康指标

- 端点URI : /health    
-  实现类：`HealthEndpoint`

- 健康指示器`HealthIndicator`

比例关系：HealthEndpoint：HealthIndicator = 1：N

意义：可以任意地输出业务健康，系统健康

----

如果需要（通过Actuator）观察健康指标的相关信息，需要在客户和服务端配置中application.proerties配置

```properties
endpoints.health.sensitive = false
```

通过访问`localhost:808/health`观察健康状态

---



**自定义健康指示器：**

**步骤一：**

```java
public class MyHealthIndicator extends AbstractHealthIndicator {

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        builder.withDetail("MyHealthIndicator","Day Day up");
    }
}
```

**步骤二：**暴露bean

```java
@Bean
MyHealthIndicator myHealthIndicator(){
    return new MyHealthIndicator();
}
```

**步骤三：**关闭安全控制，配置 

```properties
management.security.enabled=false
```







