RabbitMQ：AMQP、JMS 规范

Kafka : 相对松散的消息队列协议

《企业整合模式》： [Enterprise Integration Patterns](http://www.eaipatterns.com/)



## [Spring Cloud Stream](https://cloud.spring.io/spring-cloud-stream/)

## 1. 基本概念

- Source：来源，近义词：Producer、Publisher

- Sink：接收器，近义词：Consumer、Subscriber

- Processor：对于上流而言是 Sink，对于下流而言是 Source

> Reactive Streams : 
>
> - Publisher
> - Subscriber
> - Processor



## 2. 原生kafka使用



 **kafka原生api生产 消息**

```java
public class KafkaProducerDemo {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        //初始化配置
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers","localhost:9092");
        properties.setProperty("key.serializer", StringSerializer.class.getName());
        properties.setProperty("value.serializer", StringSerializer.class.getName());
        KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(properties);
        //创建消息
        String topic = "vison";
        Integer partition =0;
        Long stamp = System.currentTimeMillis();
        String key ="massage-key";
        String value="vison.com";
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, partition, stamp, key, value);
        //发送消息
        Future<RecordMetadata> send = kafkaProducer.send(producerRecord);
        RecordMetadata recordMetadata = send.get();

    }
}
```

**kafka 集成spring-boot消费**

```java
@Component
public class KafkaConsumerListener {

    @KafkaListener(topics = "${kafka.topic}")
    public void onMessage(String message){
        System.out.println("kafka消息消费1："+message);
    }

    @KafkaListener(topics = "vison")
    public void onMyMessage(String message){
        System.out.println("kafka消息消费-vison："+message);
    }
}
```





## 3. Spring Cloud Stream Binder : [Kafka](http://kafka.apache.org/)

启动 Zookeeper

启动 Kafka



> 消息大致分为两个部分：
>
> - 消息头（Headers）
>
> - 消息体（Body/Payload）

application.properties配置如下：

```properties
## 定义应用的名称
spring.application.name = spring-cloud-stream-kafka
## 配置 Web 服务端口
server.port = 8080

## 配置需要的kafka 主题
kafka.topic = vison

## Spring Kafka 配置信息
spring.kafka.bootstrapServers = localhost:9092

#  基于spring-boot -kafka配置
### Kafka 生产者配置
# spring.kafka.producer.bootstrapServers = localhost:9092
#spring.kafka.producer.keySerializer =org.apache.kafka.common.serialization.StringSerializer
#spring.kafka.producer.valueSerializer =org.apache.kafka.common.serialization.StringSerializer
### Kafka 消费者配置
spring.kafka.consumer.groupId = vison-1
spring.kafka.consumer.keyDeserializer =org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.valueDeserializer =org.apache.kafka.common.serialization.StringDeserializer

## 定义 Spring Cloud Stream Source 消息去向
### 针对 Kafka 而言，基本模式下
# spring.cloud.stream.bindings.${channel-name}.destination = ${kafka.topic}
spring.cloud.stream.bindings.output.destination = ${kafka.topic}
spring.cloud.stream.bindings.vison.destination = test

spring.cloud.stream.bindings.input.destination = ${kafka.topic}
```



### 3.1 发送消息

#### 1）定义标准消息发送源

```java
@Component
@EnableBinding({Source.class})
public class MessageProducerBean {

    @Autowired
    @Qualifier(Source.OUTPUT) //bean的名称
    private MessageChannel messageChannel;
    @Autowired
    private Source source;

    /**
     * 使用默认的通道output 发送数据 ，两种方式
     * @param message
     */
    public void sendMessage(String message){
       //messageChannel.send(MessageBuilder.withPayload(message).build());
        source.output().send(MessageBuilder.withPayload(message).build());
    }
```

#### 2）自定义标准消息发送源

```java
/**
 * 自定义消息源
 */
public interface MessageSource {

    String OUTPUT = "vison";

    @Output(OUTPUT)
    MessageChannel vison();

}
```



```java
@Component
@EnableBinding({MessageSource.class})
public class MessageProducerBean {

    /**
     * 使用自定义的消息通道
     */
    @Autowired
    @Qualifier(MessageSource.OUTPUT) //bean的名称
    private MessageChannel visonMessageChannel;
    @Autowired
    private MessageSource visonMessageSource;

    public void sendVisonMessage(String message){
        //visonMessageChannel.send(MessageBuilder.withPayload(message).build());
        visonMessageSource.vison().send(MessageBuilder.withPayload(message).build());
    }
```



### 3.2 监听消息

```java
package com.vison.ws.kafka.stream.consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@EnableBinding({Sink.class})
public class MessageConsumerBean {

    @Autowired
    private Sink sink;  //注入Sink  ,其实也可以和Source一样自定义

    @Autowired
    @Qualifier(Sink.INPUT)
    private SubscribableChannel subscribableChannel;

    /**
     * 方式一：字段注入完成后回调
     */
    @PostConstruct
    public void init(){
        //实现异步回调
        subscribableChannel.subscribe((message)->{
            System.out.println("subscribe : " + message.getPayload());
        });
    }

    //方式二：通过 @ServiceActivator 实现消息消费
    @ServiceActivator(inputChannel = Sink.INPUT)
    public void onMessage(Object message){
        System.out.println("@ServiceActivator : " + message);
    }

    //方式三：通过@StreamListener 实现消息消费
    @StreamListener(Sink.INPUT)
    public void onMessage(String message){
        System.out.println("@StreamListener : " + message);
    }

}

```



## 4.疑难杂症

- `@EnableBinding` 有什么用？

  答：`@EnableBinding` 将 `Source`、`Sink` 以及 `Processor` 提升成相应的代理

- @Autorwired Source source这种写法是默认用官方的实现？

  答：是官方的实现

- 这么多消息框架 各自优点是什么 怎么选取

  答：RabbitMQ：AMQP、JMS 规范

  Kafka : 相对松散的消息队列协议

  ActiveMQ：AMQP、JMS 规范

  ​	AMQP v1.0 support
  ​	MQTT v3.1 support allowing for connections in an IoT environment.

  https://content.pivotal.io/rabbitmq/understanding-when-to-use-rabbitmq-or-apache-kafka

- 如果中间件如果有问题怎么办，我们只管用，不用维护吗。现在遇到的很多问题不是使用，而是维护，中间件一有问题，消息堵塞或丢失都傻眼了，都只有一键重启

  答：消息中间件无法保证不丢消息，多数高一致性的消息背后还是有持久化的。

- @EnableBinder， @EnableZuulProxy，@EnableDiscoverClient这些注解都是通过特定BeanPostProcessor实现的吗？

  答：不完全对，主要处理接口在`@Import`:

  - `ImportSelector` 实现类
  - `ImportBeanDefinitionRegistrar` 实现类
  - `@Configuration` 标注类
  - `BeanPostProcessor` 实现类

- 我对流式处理还是懵懵的 到底啥是流式处理 怎样才能称为流式处理 一般应用在什么场景？

  答：Stream 处理简单地说，异步处理，消息是一种处理方式。

  提交申请，机器生成，对于高密度提交任务，多数场景采用异步处理，Stream、Event-Driven。举例说明：审核流程，鉴别黄图。

- 如果是大量消息 怎么快速消费 用多线程吗？

  答：确实是使用多线程，不过不一定奏效。依赖于处理具体内容，比如：一个线程使用了

  25% CPU，四个线程就将CPU 耗尽。因此，并发 100个处理，实际上，还是 4个线程在处理。I/O 密集型、CPU 密集型。

- 如果是大量消息 怎么快速消费 用多线程吗

  答：大多数是多线程，其实也单线程，流式非阻塞。

- 购物车的价格计算可以使用流式计算来处理么？能说下思路么？有没有什么高性能的方式推荐？

  答：当商品添加到购物车的时候，就可以开始计算了。