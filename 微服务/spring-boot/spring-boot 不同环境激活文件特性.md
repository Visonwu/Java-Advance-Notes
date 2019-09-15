



## 1.yaml文件

`---`在yaml配置文件中可以用来配置spring.profiles,相比properties可以仅仅在一个文件中就可以搞定，如下所示：

​	下面将Eureka，Zookeeper，Consul的配置都配置在同一个spring.yaml文件中，通过我们的配置spring.profiles.active=eureka(zookeep/consul)就可以实现不同配置切换

```yaml
spring:
  application:
    name: config-client
  cloud:
    zookeeper:
      enabled: false # Zookeeper 服务发现与注册失效（默认）
    consul:
      discovery:
        enabled: false # Consul 服务发现与注册失效（默认）

server:
  port: 0 #随机端口

## 默认 profile 关闭自动特性
eureka:
  client:
    enabled: false # Eureka 服务发现与注册失效（默认）

--- # Profile For Eureka `---` 表示不同的配置文件
spring:
  profiles: eureka
# Eureka 客户端配置
eureka:
  server: # 官方不存在的配置（自定义配置）
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
  profiles: zookeeper
  cloud:
    zookeeper:
      enabled: true
      connectString: 127.0.0.1:2181

--- # Profile For Consul
spring:
  profiles: consul
  cloud:
    consul:
      discovery:
        enabled: true
        ipAddress: 127.0.0.1
        port: 8500

```



## 2. spring.profiles之active和include的区别

```properties
server.port=9000
spring.application.name=spring-profile
version=master-veresion
name = master-name
#spring.profiles.active=test1
#include可以包含多个，属性重复后者覆盖前者，但是无法覆盖active，active属性值优先最高
spring.profiles.include=test3,test2
```

总结：
**多个配置文件中有同一个值**，以下情况获取值的效果：

- 1.启动命令不带--spring.profiles.active参数以application.properties首先启动
  - 按顺序所有文件第一个配置的spring.profiles.active属性中指定的最后一个文件中含有该属性的值为准
    如果所有文件都没有spring.profiles.active，那么以pring.profiles.include配置的最后一个属性文件中的值为准
- 2.启动命令带--spring.profiles.active参数以参数指定的属性文件首先启动
  - 此情况，已命令指定的配置文件中的值为准，其他文件中再配置spring.profiles.active也不会生效，如果不存在值，那么会以pring.profiles.include指定的最后一个文件中的值为准



简要说:

​		优先以第一个active的值为最终值，如果没有会包含include指定文件最后一个为基准：

​			启动命令spring.profiles.active指定文件中的值 > 文件中spring.profiles.active指定的文件列表中最后一次出现的值 > 文件中spring.profiles.include指定的文件列表中最后一次出现的值











