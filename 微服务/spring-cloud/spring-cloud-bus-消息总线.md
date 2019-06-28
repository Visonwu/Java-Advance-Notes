**Spring-cloud-bus的原理**还是依赖Spring-cloud-stream做的消息推送。

​			在微服务中，我们可以通过轻量级的消息代理构建一个公用的消息主题让所有的微服务实例都连接上来，所有连接上的实例监听由消息代理发送的消息，这个就称为消息总线。在总线上的实例可以广播一些消息让其他实例都知道的消息，例如配置变更或者其他一些管理的操作等。

​		我们这里可以使用Spring-cloud-bus来对spring-cloud-config的配置变化做消息广播推送。架构图如下：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4ehyheedrj20ol0czwfi.jpg)

## 1.使用RabbitMQ推送消息

​	这里依赖Config的相关项目，上面的Service A...表示需要从Cofig配置中心拉取配置

### 1.1 例子

步骤一：在每个服务实例中引入amqp依赖

```xml
<dependency>
	<groupId>org.springframework.cloud</groupId>
	<artifactId>spring-cloud-starter-bus-amqp</artifactId>
</dependency>
```

步骤二：在配置服务其中加入如下信息

```properties
##RabbitMQ
spring.rabbitmq.host=192.168.124.145
spring.rabbitmq.port=5672
spring.rabbitmq.username=admin
spring.rabbitmq.password=admin
#如果对于当前用户配置了virtual_host要配置下面这个，否则一直报错unexpected connection driver error occured
spring.rabbitmq.virtual-host=admin_vhost
#当然还有其他配置，可以自己定制化

#这里记得设置 actuator的访问权限 springboot2.0改版，和之前不同，这里把所有的api接口都开放出来了
management.endpoints.web.exposure.include=*
```

步骤三：修改配置中心配置文件的内容

​	记得主类上需要添加`@RefreshScope`注解

步骤四：对Config Server发起调用 更新

```bash
#发起调用，更新信息
POST  /bus/refresh  【springboot 1.0版本用这个】

POST /actuator/bus_refresh   【springboot 2.0改版为这个】
```

### 1.2 刷新指定范围

```bash
#发起调用，更新信息
POST  /bus/refresh?destination=customers:9000

//destination指定需要刷新的配置服务器。默认名称是通过${spring.cloud.client.hostname}:${spring.application.name}:${spring.application.instance_id:${server.port}}规则生成的。

当然这里的配置符合Spring的类 PathMatcher接口。
```



## 2.使用Kafka推送消息

类似的方法引入,这里导入**Kafka**

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bus-kafka</artifactId>
</dependency>
```

