	首先需要明确，效率与可靠性是无法兼得的，如果要保证每一个环节都成功，势必会对消息的收发效率造成影响。如果是一些业务实时一致性要求不是特别高的场合，可以牺牲一些可靠性来换取效率。

# 一、可靠性投递

如下分为几个步骤保证消息的一致性

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g24q17zb6oj20im08gwff.jpg)

## 1、确保消息发送到RabbitMQ服务器

​	可能因为网络或者Broker的问题导致①失败，而生产者是无法知道消息是否正确发送到Broker的。

有两种解决方案：

- 第一种是Transaction（事务）模式

- 第二种Confirm（确认）模式

### 1.1 事务模式

​	在通过`channel.txSelect`方法开启事务之后，我们便可以发布消息给RabbitMQ了，如果事务提交成功，则消息一定到达了RabbitMQ中然后调用`channel.txCommit()`，如果在事务提交执行之前由于RabbitMQ异常崩溃或者其他原因抛出异常，这个时候我们便可以将其捕获，进而通过执行`channel.txRollback`方法来实现事务回滚。使用事务机制的话会“吸干”RabbitMQ的性能，**一般不建议使用。**

### 1.2 确认模式

​	生产者通过调用`channel.confirmSelect`方法（即Confirm.Select命令）将信道设置为confirm模式。一旦消息被投递到所有匹配的队列之后，RabbitMQ就会发送一个确认（Basic.Ack）给生产者（包含消息的唯一ID），这就使得生产者知晓消息已经正确到达了目的地了

- 可以同步确认
  - 在生产者这边通过调用channel.confirmSelect()方法将信道设置为Confirm 模式，然后发送消息。一旦消息被投递到所有匹配的队列之后，RabbitMQ 就会发送一个确认（Basic.Ack）给生产者，也就是调用channel.waitForConfirms()返回true，这样生产者就知道消息被服务端接收了。
- 可以批量确认
  - 批量确认， 就是在开启Confirm 模式后， 先发送一批消息。只要channel.waitForConfirmsOrDie();方法没有抛出异常，就代表消息都被服务端接收了。
- 可以异步确认
  - 异步确认模式需要添加一个ConfirmListener，并且用一个SortedSet 来维护没有被确认的消息。
    Confirm 模式是在Channel 上开启的，因为RabbitTemplate 对Channel 进行了封装，叫做ConfimCallback。

## 2、确保消息路由到正确的队列

​	可能因为路由关键字错误，或者队列不存在，或者队列名称错误导致②失败。

- 使用`mandatory`参数和`ReturnListener`，可以实现消息无法路由的时候返回给生产者。
- 使用备份交换机（alternate-exchange），无法路由的消息会发送到这个交换机上。

```java
Map<String,Object> arguments = new HashMap<String,Object>();
arguments.put("alternate-exchange","ALTERNATE_EXCHANGE"); // 指定交换机的备份交换机
channel.exchangeDeclare("TEST_EXCHANGE","topic", false, false, false, arguments);
```

## 3、确保消息在队列正确地存储

​	可能因为系统宕机、重启、关闭等等情况导致存储在队列的消息丢失，即③出现问题。

解决方案：

### 3.1 队列持久化

```java
// String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String,
Object> arguments
channel.queueDeclare(QUEUE_NAME, true, false, false, null);
```

### 3.2 交换机持久化

```java
// String exchange, boolean durable
channel.exchangeDeclare("MY_EXCHANGE","true");
```

### 3.3 消息持久化

```java
AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
   .deliveryMode(2)  // 2代表持久化，其他代表瞬态
   .build();
   
channel.basicPublish("", QUEUE_NAME, properties, msg.getBytes());
```

### 3.4 集群，镜像队列，参考下面

​	保证宕机消息

## 4、确保消息从队列正确地投递到消费者

​	如果消费者收到消息后未来得及处理即发生异常，或者处理过程中发生异常，会导致④失败。

### 4.1 使用autoAck 或者Basic.Reject 确认是否消费

​		autoAc'k为true表示收到消息就自动应答

​		为了保证消息从队列可靠地达到消费者，RabbitMQ提供了消息确认机制（message acknowledgement）。消费者在订阅队列时，可以指定`autoAck`参数，当`autoAck`等于false时，RabbitMQ会等待消费者显式地回复确认信号后才从队列中移去消息。

​	如果消息消费失败，也可以调用`Basic.Reject(单条拒绝)`或者`Basic.Nack(批量拒绝)`来拒绝当前消息而不是确认。如果requeue参数设置为true，可以把这条消息重新存入队列，以便发给下一个消费者（当然，只有一个消费者的时候，这种方式可能会出现无限循环重复消费的情况，可以投递到新的队列中，或者只打印异常日志）。



如何设置手动ACK？
SimpleRabbitListenerContainer 或者SimpleRabbitListenerContainerFactory

```java
factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
```

注意这三个值的区别：
NONE：自动ACK

MANUAL： 手动ACK
AUTO：如果方法未抛出异常，则发送ack。

​	当抛出`AmqpRejectAndDontRequeueException` 异常的时候，则消息会被拒绝，且不重新入队。当抛出`ImmediateAcknowledgeAmqpException` 异常，则消费者会发送ACK。其他的异常，则消息会被拒绝，且requeue = true 会重新入队。





### 4.2 消费者回调

​	消费者处理消息以后，可以再发送一条消息给生产者，或者调用生产者的API，告知消息处理完毕。
参考：二代支付中异步通信的回执，多次交互。某提单APP，发送碎屏保消息后，消费者必须回调API

### 4.3 补偿机制

​	对于一定时间没有得到响应的消息，可以设置一个定时重发的机制，但要控制次数，比如最多重发3次，否则会造成消息堆积。

​	参考：ATM存款未得到应答时发送5次确认；ATM取款未得到应答时，发送5次冲正。根据业务表状态做一个重发。



# 二、消息幂等性
服务端是没有这种控制的，只能在消费端控制。

如何避免消息的重复消费？
消息重复可能会有两个原因：

1、生产者的问题，环节①重复发送消息，比如在开启了Confirm模式但未收到确认。
2、环节④出了问题，由于消费者未发送ACK或者其他原因，消息重复投递。

​	对于重复发送的消息，可以对每一条消息生成一个唯一的业务ID，通过日志或者建表来做重复控制。
参考：银行的重账控制环节。



# 三、消息的顺序性
消息的顺序性指的是消费者消费的顺序跟生产者产生消息的顺序是一致的。

如果一个队列只有一个消费者就可以保证消息的顺序性了。

在RabbitMQ中，一个队列有多个消费者时，由于不同的消费者消费消息的速度是不一样的，顺序无法保证。
参考：消息：1、新增门店 2、绑定产品 3、激活门店，这种情况下消息消费顺序不能颠倒。



# 四、高可用架构

## 1、RabbitMQ集群

集群主要用于实现高可用与负载均衡。

RabbitMQ通过/var/lib/rabbitmq/.erlang.cookie来验证身份，需要在所有节点上保持一致。

集群有两种节点类型：

- **一种是磁盘节点**：：将元数据（包括队列名字属性、交换机的类型名字属性、绑定、vhost）放在磁盘中
- **一种是内存节点**：将元数据放在内存中

​		集群中至少需要一个磁盘节点以实现元数据的持久化，未指定类型的情况下，默认为磁盘节点；我们一般把应用连接到内存节点（读写快），磁盘节点用来备份。

<font color='red'>PS：内存节点会将磁盘节点的地址存放在磁盘（不然重启后就没有办法同步数据了）。
如果是持久化的消息，会同时存放在内存和磁盘。</font>



集群通过**25672**端口两两通信，需要开放防火墙的端口。
需要注意的是，RabbitMQ集群无法搭建在广域网上，除非使用federation或者shovel等插件。

集群的配置步骤：
1、配置hosts
2、同步erlang.cookie
3、加入集群

### 1.1 普通集群

​		普通集群模式下，不同的节点之间只会相互同步元数据。

疑问：为什么不直接把队列的内容（消息）在所有节点上复制一份？

​		主要是出于存储和同步数据的网络开销的考虑，如果所有节点都存储相同的数据，就无法达到线性地增加性能和存储容量的目的（堆机器）。

​		假如生产者连接的是节点3，要将消息通过交换机A 路由到队列1，最终消息还是会转发到节点1 上存储，因为队列1 的内容只在节点1 上。同理，如果消费者连接是节点2，要从队列1 上拉取消息，消息会从节点1 转发到节点2。其它节点起到一个路由的作用，类似于指针。
​			普通集群模式不能保证队列的高可用性，因为队列内容不会复制。如果节点失效将导致相关队列不可用，因此我们需要第二种集群模式。



### 1.2 镜像集群

​	第二种集群模式叫做镜像队列。
​			镜像队列模式下，消息内容会在镜像节点间同步，可用性更高。不过也有一定的副作用，系统性能会降低，节点过多的情况下同步的代价比较大。

| 操作方式              | 命令或步骤                                                   |
| --------------------- | ------------------------------------------------------------ |
| rabbitmqctl (Windows) | rabbitmqctl set_policy ha-all "^ha." "{""ha-mode"":""all""}" |
| HTTP API              | PUT /api/policies/%2f/ha-all {"pattern":"^ha.",<br/>"definition":{"ha-mode":"all"}} |
| Web UI                | 1、avigate to Admin > Policies > Add / update a policy<br/>2、Name 输入：mirror_image<br/>3、Pattern 输入：^（代表匹配所有）<br/>4、Definition 点击HA mode，右边输入：all<br/>5、Add policy |

