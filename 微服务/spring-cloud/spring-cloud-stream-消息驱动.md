RabbitMQ：AMQP、JMS 规范

Kafka : 相对松散的消息队列协议

《企业整合模式》： [Enterprise Integration Patterns](http://www.eaipatterns.com/)



## [Spring Cloud Stream](https://cloud.spring.io/spring-cloud-stream/)

​		Spring-cloud-stream是一个用来为微服务应用构建消息驱动能力的框架，它的本质就是整合了SpringBoot和Spring-Integration，实现了一套轻量级的消息驱动的微服务框架。

**引入了三个核心概念：**

- **发布-订阅**
- **消息组**
- **分区**

参考官网：<https://cloud.spring.io/spring-cloud-static/Greenwich.SR2/single/spring-cloud.html#spring-cloud-stream-overview-introducing>	

## 一 、概念

### 1.1 基础概念

- Source：来源，近义词：Producer、Publisher

- Sink：接收器，近义词：Consumer、Subscriber

- Processor：对于上流而言是 Sink，对于下流而言是 Source，本质继承了**Source和Sink**

> Reactive Streams : 
>
> - Publisher
> - Subscriber
> - Processor

​		`Spring-cloud -stream`采用`Binder`把应用和消息中间件连接起来。并用`input`输入通道和`output`输出通道把应用和`Binder`连接起来。使引用和消息中间件完全解耦。目前我们的`Spring-cloud -stream`中的`Binder`只实现了`Kafka`和`RabbitMQ`的实现。如果我们需要其他的可以自己实现。

`Spring-cloud -stream`应用模型图如下：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4fshidqfcj20ad08mglu.jpg)

### 1.2 核心概念

#### 1）绑定器

​	上面的Binder完美 的实现了应用程序和消息中间件之间的隔离。通过暴露统一的Channel；使得各个应用程序不用考虑各种不同的消息中间件的实现。目前的Spring-Cloud-Stream实现的产品只有Kafka和RabbitMQ

#### 2） 发布订阅模式

​		Spring-cloud-Stream的消息通信遵循发布-订阅模式，当一条消息被投递到消息中间件之后，它会通过共享的`topic`主题进行广播，消息消费者在订阅的主题中收到它并触发自身的业务逻辑处理。当然这里的`Topic`主题是Spring-cloud-Stream中的一个抽象，不同的实现对应的产品也是不同的

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4fu8jd6toj20au0bdglv.jpg)

**Topic分别对应：**

- 这里的topic 对应   --------RabbitMQ的Exchange
- 这里的topic对应    -------- Kafka的topic



​		相对于点对点的通信，Spring-cloud-stream采用发布-订阅可以有效降低消息生产者和消费者之间的耦合，当需要对同一类的消息增加一种处理方式，只需要增加对当前Topic的订阅即可。而不需要做其他事情。

```properties
#例如这里对input的通道设置主题名（raw-sensor-data）
#spring.cloud.stream.bindings.{消息通道}.destination={主题名}
spring.cloud.stream.bindings.input.destination=raw-sensor-data
```



#### 3）消费组

​		虽然上面的发布订阅将生产者和消费者进行了解耦，但是在微服务中的高可用和负载均衡，有时候多台机器只消费一个，所以引入了消费组的概念（来源于Kafka的的消费组），在当前的同一个消费组，只会有一个实例真正消费到数据。我们可以通过如下设置。如果没有设置，会为每一个应用分配一个独立的匿名消费组。

```properties
#设置消费组
spring.cloud.stream.bindings.<channelName>.group=vison
```



![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4fu90cvq9j20cn0bgwer.jpg)

#### 4） 消息分区

​	    通过消息分组可保证同一个组内只有一个实例消费（轮训消费），但是某种情况下，我们需要让带有某些特征的应用消费。(比如做统计功能，让某一台机器单独做统计功能)那么这里就出现了消息分区的概念。我们可以通过生成者为每一个消息添加一个特征ID，然后让特定的应用去消费这个消息完成统计任务。





## 二、RabbitMQ的案例

### 步骤一：引入依赖

```xml
<dependency>
     <groupId>org.springframework.cloud</groupId>
     <artifactId>spring-cloud-starter-stream-rabbit</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-test-support</artifactId>
    <scope>test</scope>
</dependency>
```

### 步骤二：spring.properties配置

```properties
spring.application.name=stream-hello
server.port=8089
#RabbitMQ配置
spring.rabbitmq.host=192.168.124.149
spring.rabbitmq.port=5672
spring.rabbitmq.username=admin
spring.rabbitmq.password=admin
#这里注意配置了virtualhost不要忘了这里的配置
spring.rabbitmq.virtual-host=admin_vhost

#这里使用的默认通道input和output通道
#设置输出和输入主题是raw-sensor-data
spring.cloud.stream.bindings.input.destination=raw-sensor-data
#设置消息消费分组是vison
spring.cloud.stream.bindings.input.group=vison
spring.cloud.stream.bindings.output.destination=raw-sensor-data

#这里把actuator的端点信息都暴露出来
management.endpoints.web.exposure.include=*
```

### 步骤三：编写消费者类

```java
@EnableBinding(Sink.class,Source.class)
public class MessageReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageReceiver.class);

    @StreamListener(Sink.INPUT)
    public void receive(Object payload){
        LOGGER.info("sink-input received :"+payload);
    }
}
```

### 步骤四：启动应用程序

### 步骤五：在RabbitMQ管理界面发送消息

​		通过如下所示可以再**队列里或者交换器**中发送一条消息，在你的控制台马上就会收到一条消息。

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4ft85bz9nj20vb0l074y.jpg)

### 步骤六：代码里生产消息

​	   如下通过调用/msg接口发送消息，这里发送主题是配置中的主题topic（raw-sensor-data)。当前程序开启不同的端口启动多个应用。那么通过消息分组会发现多个实例消息只会论轮着消费。

```java
@RestController
@SpringBootApplication
public class StreamDemoApplication {

    @Autowired
    private Source source;

    public static void main(String[] args) {
        SpringApplication.run(StreamDemoApplication.class, args);

    }
    //发送消息
    @RequestMapping("/msg")
    public void sendMsg(@RequestParam("message")String message){
        send(message);
    }

    public void send(String message){
        boolean hello = source.output()
                .send(MessageBuilder.withPayload(message).build());
        System.out.println("send result:" + hello);
    }
}
```





## 三、Kafka案例

### 3.1. 原生kafka使用



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

### 3.2 kafka 集成spring-boot消费

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

### 3.3. Spring Cloud Stream Binder : [Kafka](http://kafka.apache.org/)

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
spring.kafka.consumer.keyDeserializer
=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.valueDeserializer =org.apache.kafka.common.serialization.StringDeserializer

## 定义 Spring Cloud Stream Source 消息去向
### 针对 Kafka 而言，基本模式下
# spring.cloud.stream.bindings.${channel-name}.destination = ${kafka.topic}
spring.cloud.stream.bindings.output.destination = ${kafka.topic}
spring.cloud.stream.bindings.vison.destination = test

spring.cloud.stream.bindings.input.destination = ${kafka.topic}
```

#### 3.3.1  发送消息

##### 1）定义标准消息发送源

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

##### 2）自定义标准消息发送源

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

#### 3.3.2 监听消息

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



## 四、使用详解

### 4.1 @EnableBinding 开启绑定

​		当前注解使用`@Import({ BindingBeansRegistrar.class, BinderFactoryConfiguration.class })`

- BindingBeansRegistrar负责将带有@Input和@Ouput注解的类添加进bean

- BinderFactoryConfiguration用来加载消息中间件相关的配置信息，它会从META-INF/spring.binders中加载对消息中间件相关的配置文件。比如RabbitMq的

  ```properties
  rabbit:\
  org.springframework.cloud.stream.binder.rabbit.config.RabbitServiceAutoConfiguration
  ```



### 4.2 @Input和@Output

​		我们可以在接口中可以通过`@Input`和`@Output`注解来定义消息通道。然后通过`@EnableBinding`来绑定通道。`Sink`和`Source`是Spring-cloud-stream的默认实现。

​		另外还有一个`Processor`,就是简单的继承了这两个接口。如下所示包含两者的功能。

```java
public interface Processor extends Source, Sink {

}
```



**结论：**我们的这两个注解的主要功能就是为了生成不同的通道。@Output输出通道需要返回`MessageChannel`。而@Input输入通道目的产生了一个`SubscribableChannel`该接口继承`MessageChannel`,维护订阅者方法

​	和上面的案例类似，我们可以通过直接注入`MessageChannel`来使用，但是如果有多个MessageChannel,需要使用`@Qullifier`来指定不同的通道，这个值是@Output中的值。



### 4.3 使用Spring-Integeration

​		由于Spring-cloud-stream是基于Spring-integration实现的，所以我们也可以使用Spring-Integeration相应的API。

**spring-integeration案例**

```java
@EnableBinding(Sink.class)
public class SinkReceiver {

    //使用spring-integration原生 消费，通道是input，主题也是input
    @ServiceActivator(inputChannel = Sink.INPUT)
    public void receive(Object payload){
        System.out.println("收到消息："+payload);
    }

}
```

```java
@Component
public class SourceSender {

    //1.这里将输出通道绑定到input通道上，这里和消费者的通道构成一对消费者和生产者。
    //2.这里将当前时间作为消息返回
    //3.这里使用@Poller 每隔2秒返回当前时间
    @Bean
    @InboundChannelAdapter(value =  Sink.INPUT,poller = @Poller(fixedRate = "2000"))
    public MessageSource<String> timeMessageSource(){
        System.out.println("发送消息了");
        return ()->new GenericMessage<>("{\"name\":\"vison\",\"age\":\"23\"}");
    }

    //我们可以对指定通道的消息做数据转换处理,这里是Date日志
	@Transformer(inputChannel = Sink.INPUT,outputChannel =  Sink.INPUT)
    public Object transfer(Date message){
        return   new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(message);
    }
}
```

### 4.4 消息转换

​	如果使用spring-cloud-stream的消息转换，@StreamListener自带消息转换功能。我们如果发送的是一个json文件，消费时直接用相应的对象接受，并且在`application.properties`中添加相应的`content-type`值。

而Spring-integration本身的` @ServiceActivator`没有自动转换功能，需要使用`@Transformer`自己转换

```java
@StreamListener(Sink.INPUT)
public void receive(User user){
    LOGGER.info("sink-input received :"+user);
}

```

```properties
#设置input通道的content-type类型为application/json
spring.cloud.stream.bindings.input.content-type=application/json
```



### 4.5 消息反馈@SendTo

​	有时候我们在消费了某个消息后，需要反馈一个消息给对方。这时候用`@SendTo`注解返回指定内容的输出通道。

​	比如这里两个应用aap1,和app2。注意的是APP1的消费的topic和app2接收的topic相同。

**APP1负责接收完成处理后回复**

```java
@EnableBinding(Processor.class)
public class App1 {

    @StreamListener(Processor.INPUT)
    @SendTo(Processor.OUTPUT)
    public Object input(Object payload){
        System.out.println("app1 received："+payload);
        return "app1 retured："+payload;
    }

}
```

这里配置的properties

```properties
spring.application.name=stream-consumer
server.port=8090

spring.rabbitmq.host=192.168.124.149
spring.rabbitmq.port=5672
spring.rabbitmq.username=admin
spring.rabbitmq.password=admin
spring.rabbitmq.virtual-host=admin_vhost

spring.cloud.stream.bindings.input.destination=input
spring.cloud.stream.bindings.output.destination=output

management.endpoints.web.exposure.include=*
```



**APP2负责发送，然后接收消费者的确认消息**

```java
@EnableBinding(Processor.class)
public class App2 {

    @Bean
    @InboundChannelAdapter(value = Processor.OUTPUT,poller = @Poller(fixedDelay = "2000"))
    public MessageSource<Date> send(){
        return ()->new GenericMessage<>(new Date());
    }

    @StreamListener(Processor.INPUT)
    public void receive(Object payload){
        System.out.println("app2 received: "+payload);
    }

}
```

```properties
spring.application.name=stream-producer
server.port=8091

spring.rabbitmq.host=192.168.124.149
spring.rabbitmq.port=5672
spring.rabbitmq.username=admin
spring.rabbitmq.password=admin
spring.rabbitmq.virtual-host=admin_vhost
#这里的进出通道的 topic和和消费者的恰好相反
spring.cloud.stream.bindings.input.destination=output
spring.cloud.stream.bindings.output.destination=input

management.endpoints.web.exposure.include=*
```



### 4.6 响应式编程

​	Spring-cloud-stream还支持使用RxJava的响应式编程来处理消息的输入和输出；这里导入包：

之前可以使用导入的`spring-cloud-stream-rxjava`中的`@EnableRxJavaProcessor`,`RxJavaProcessor`来实现响应式编程，不过现在这个已经被`@Deprecated`,推荐使用`@StreamListener`来完成类似的功能。

// TODO待补充



### 4.7 消费组和消息分区

####	1）消息分组

​		保证的是同一个主题 那么监听这个主题的实例在同一个组，只有一个应用实例能够收到消息，能够达到多个实例轮次消费，做到负载均衡的概念。如下设置的topic--input，分组vison

​	这里只需要设置**消费者的输入通道和分组即可**。

```properties
# 输入通道的topic设置的是input
spring.cloud.stream.bindings.input.destination=input
# 这里的input通道 消费分组也是vison
spring.cloud.stream.bindings.input.group=vison
```

#### 2） 消费分区

​	为了让在某个消费组的应用 具备某一种特征的消息只能够被同一个实例消费，引入消费分区的概念。所以需要我们对消息发送以及**消息者**两处做处理。

​	发送的消息是被封装为`GenericMessage`  //TODO待分析具体分区规则

​	如下的默认规则会通过 `hashcode(partition-key-expression)  % partition-count` 返回需要选择的分区信息，



​		**生产者配置：**

```properties
#指定了分区键的表达式规则，我们可以根据实际的输出消息规则SpEL来生成合适的分区键
spring.cloud.stream.bindings.output.producer.partition-key-expression=5
#该参数制定了消息分区的数量
spring.cloud.stream.bindings.output.producer.partition-count=3
```

​	 **消费者配置：**

```properties
spring.cloud.stream.bindings.input.group=vison
#开启分区功能
spring.cloud.stream.bindings.input.consumer.partitioned=true
#指定当前消费者的总实例数量
spring.cloud.stream.instance-count=3
#当前实例的索引号
spring.cloud.stream.instance-index=0
```

​	      这里消费者可以看到，消费者的配置有3个消费实例，索引号为0；总实例数量为3,表示可以有三个实例，配置0,1,2三种情况。

**分区路由原理解析**

​	然后我们看一下上面的分区后消息的传递规则，看源码：

​		通过全局搜索发现`ProducerProperties`类中引用了`partitionKeyExpression`引用。所以再然后我们发现`PartitionHandler`持有当前`ProducerProperties`引用。

```java
public int determinePartition(Message<?> message) {
    Object key = extractKey(message);

    int partition;
    //默认我们没有设置这个
    if (this.producerProperties.getPartitionSelectorExpression() != null) {
        partition = this.producerProperties.getPartitionSelectorExpression()
            .getValue(this.evaluationContext, key, Integer.class);
    }
    else {
        //上面我们设置的走这条线，这里的默认策略是返回 partition-key-expression 的hashcode值
        partition = this.partitionSelectorStrategy.selectPartition(key,
                                                                   this.partitionCount);
    }
    // 然后通过上面的hashcode值对 partition-count分区数取模返回。
    return Math.abs(partition % this.partitionCount);
}
```

​		然后这个的返回值通过`MessageConverterConfigurer.PartitioningInterceptor.preSend`放到返回消息的消息头中返回。

```java
@Override
public Message<?> preSend(Message<?> message, MessageChannel channel) {
    
    //这里开启了这里，从这里返回消息
    if (!message.getHeaders().containsKey(BinderHeaders.PARTITION_OVERRIDE)) {
        int partition = this.partitionHandler.determinePartition(message);
        return MessageConverterConfigurer.this.messageBuilderFactory
            .fromMessage(message)
            .setHeader(BinderHeaders.PARTITION_HEADER, partition).build();
    }
    else {
        return MessageConverterConfigurer.this.messageBuilderFactory
            .fromMessage(message)
            .setHeader(BinderHeaders.PARTITION_HEADER,
                       message.getHeaders()
                       .get(BinderHeaders.PARTITION_OVERRIDE))
            .removeHeader(BinderHeaders.PARTITION_OVERRIDE).build();
    }
}
```

​	  这里返回到 会发现在`AbstractMessageChannel.ChannelInterceptorList.preSend`通过遍历拦截器实现的,当然最后还是对调用到`RabbitTemplate.doSend方法`去。

```java
@Nullable
public Message<?> preSend(Message<?> messageArg, MessageChannel channel,
                          Deque<ChannelInterceptor> interceptorStack) {

    Message<?> message = messageArg;
    if (this.size > 0) {
        for (ChannelInterceptor interceptor : this.interceptors) {
            Message<?> previous = message;
            message = interceptor.preSend(message, channel);
            if (message == null) {
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug(interceptor.getClass().getSimpleName()
                                      + " returned null from preSend, i.e. precluding the send.");
                }
                afterSendCompletion(previous, channel, false, null, interceptorStack);
                return null;
            }
            interceptorStack.add(interceptor);
        }
    }
    return message;
}
```



### 4.8 消息类型content-type

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4idc39y5oj20ve08omy2.jpg)

```properties
#spring.cloud.stream.bindings.{channelName}.content-type={value}
spring.cloud.stream.bindings.input.content-type=application/json
```

**Spring-cloud-Stream自带下面的消息类型转换器：**

- Json和POJO的类型转换
- Json与org.springframework.tuple.Tuple的转换
- Object和byte[]的互相转换
- String和byte[]的相互转换
- Object向纯文本转换：Object需要实现toString方法

**MIME类型**

​		上面的content-type采用了MediaType类型。比如

- application/json、text/plain;charset=UTF-8

- application/x-java-Object;type=java.util.Map表示传输的是一个Map对象

- application/x-java-Object;type=com.vison.User表示传输的是一个User对象

- application/x-spring-tuple表示传输的是一个Spring的Tuple对象

  ​	我们一般转换在信息发出的时候做转换，如果我们需要自定义相关的消息转化器，可以通过`org.springframework.messaging.converter.MessageConverter`接口实现自定义转换器。



### 4.9 Binder绑定器

参考：<https://cloud.spring.io/spring-cloud-static/Greenwich.SR2/single/spring-cloud.html#spring-cloud-stream-overview-binders>

​	我们的RabbitMQ和Kafka分别有实现的Binder接口。`Binder`接口是spring-cloud-stream提供的SPI,我们自己要可以实现自己的其他消息实现。

**一个典型Binder绑定器需要包含下面几个内容:**

- 一个实现Binder接口的类

- 一个Spring的加载配置类，用来创建连接消息中间件基础结构使用的实例

- 一个或者多个能够在classpath的META-INF/spring.binders路径线找到绑定器 定义文件。比如我们能够在spring-cloud-starter-stream-rabbit中找到该文件，这里存储了自动化配置的路径

  - ```properties
    rabbit:\
    org.springframework.cloud.stream.binder.rabbit.config.RabbitServiceAutoConfiguration
    
    ```




#### 1） 多绑定器使用

​		我们同时导入了多个消息中间件使用，比如kafka，rabbitMq，我们需要对不同的消息中间件做定制化使用.

**1.1).设置默认绑定器，不同的消息中间件**

```properties
#当然这里的rabbit,kafka分别是META-INF/spring.binders中的标识符
spring.cloud.stream.default-binder=rabbit
##设置input通道的绑定器为kafka
spring.cloud.stream.bindings.input.binder=kafka

```

**1.2).同一个消息中间件，多个实例**

- 1.当显示的配置会自动禁用默认的绑定器配置
- 2.spring.cloud.stream.binders.<configurationName>自定义配置绑定器设置
- 3.spring.cloud.stream.binders.<configurationName>.type=rabbit #配置当前绑定器的类型，这个来自META-INF/spring.binders文件
- 4.spring.cloud.stream.binders.rabbit1.environment 用来设置绑定器的相关属性
- 5.spring.cloud.stream.binders.rabbit1.inherit-environment 当前绑定器是否继承当前应用的环境配置，默认true
- 6.spring.cloud.stream.binders.rabbit1.default-candidate=用来设置当前绑定器配置为默认绑定器的候选项，默认为true,如果需要不影响默认配置，可以设置为false

```properties
#当我们连接两个不同的消息中间件实例，input通道使用的rabbit1，output通道使用的是rabbit2
spring.cloud.stream.bindings.input.binder=rabbit1
spring.cloud.stream.bindings.output.binder=rabbit2

#rabbitmq 实例1
spring.cloud.stream.binders.rabbit1.type=rabbit
spring.cloud.stream.binders.rabbit1.environment.spring.rabbitmq.host=192.168.124.149
spring.cloud.stream.binders.rabbit1.environment.spring.rabbitmq.port=5672
spring.cloud.stream.binders.rabbit1.environment.spring.rabbitmq.username=admin
spring.cloud.stream.binders.rabbit1.environment.spring.rabbitmq.password=admin
spring.cloud.stream.binders.rabbit1.default-candidate=
spring.cloud.stream.binders.rabbit1.inherit-environment=true

#rabbitmq 实例2
spring.cloud.stream.binders.rabbit2.type=rabbit
spring.cloud.stream.binders.rabbit2.environment.spring.rabbitmq.host=192.168.124.150
spring.cloud.stream.binders.rabbit2.environment.spring.rabbitmq.port=5672
spring.cloud.stream.binders.rabbit2.environment.spring.rabbitmq.username=admin
spring.cloud.stream.binders.rabbit2.environment.spring.rabbitmq.password=admin
```



### 4.10 RabbitMQ和Kafka各自对应的特殊配置

**rabbimq：**参考官网   <https://cloud.spring.io/spring-cloud-static/spring-cloud-stream-binder-rabbit/2.2.0.RELEASE/spring-cloud-stream-binder-rabbit.html>

**Kafka**：参考官网<https://cloud.spring.io/spring-cloud-static/spring-cloud-stream/2.2.0.RELEASE/home.html>

## 5.疑难杂症

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