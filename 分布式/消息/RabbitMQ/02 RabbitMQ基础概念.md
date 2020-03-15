# 1.工作模式

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g22c97n9rxj20pu08mjs2.jpg)



# 2. 名称解释

| 概念        | 解释                                                         |
|--|--|
| Broker      | 即RabbitMQ的实体服务器。提供一种传输服务，维护一条从生产者到消费者的传输线路，
保证消息数据能按照指定的方式传输。 |
| Exchange    | 消息交换机。指定消息按照什么规则路由到哪个队列Queue。        |
| Queue       | 消息队列。消息的载体，每条消息都会被投送到一个或多个队列中。 |
| Binding     | 绑定。作用就是将Exchange和Queue按照某种路由规则绑定起来。    |
| Routing Key | 路由关键字。Exchange根据Routing Key进行消息投递。定义绑定时指定的关键字称为Binding Key。 |
| Vhost       | 虚拟主机。一个Broker可以有多个虚拟主机，用作不同用户的权限分离。一个虚拟主机持有
一组Exchange、Queue和Binding。 |
| Producer    | 消息生产者。主要将消息投递到对应的Exchange上面。一般是独立的程序 |
| Consumer    | 消息消费者。消息的接收者，一般是独立的程序。                 |
| Connection  | Producer 和 Consumer 与Broker之间的TCP长连接。               |
| Channel     | 消息通道，也称信道。在客户端的每个连接里可以建立多个Channel，每个Channel代表一
个会话任务。在RabbitMQ Java Client API中，channel上定义了大量的编程接口 |



# 3.四种主要的交换机

## 3.1 Direct Exchange 直连交换机

**定义**：直连类型的交换机与一个队列绑定时，需要指定一个明确的`binding key`。

**路由规则**：发送消息到直连类型的交换机时，只有`routing key`跟`binding key`完全匹配时，绑定的队列才能收到消息。

例如如下：

```java
// 发布一条消息；只有队列1能收到消息
channel.basicPublish("MY_DIRECT_EXCHANGE", "key1", null, msg.getBytes());
```



![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g22ckpotjej20ip0753z8.jpg)

## 3.2 Topic Exchange 主题交换机

**定义**：主题类型的交换机与一个队列绑定时，可以指定按模式匹配的`routing key`。
对于bindKey 通配符有两个:

- *****代表匹配一个单词

- **#**代表匹配零个或者多个单词。单词与单词之间用 `. `隔开；如“com.wusu.vison”、“com.rabbit.client”。

**路由规则**：发送消息到主题类型的交换机时，`routing key`符合`binding key`的模式时，绑定的队列才能收到消息。

例如：

```java
// 只有队列1能收到消息
channel.basicPublish("MY_TOPIC_EXCHANGE", "sh.abc", null, msg.getBytes()); 
// 队列2和队列3能收到消息
channel.basicPublish("MY_TOPIC_EXCHANGE", "bi.book", null, msg.getBytes()); 
// 只有队列4能收到消息
channel.basicPublish("MY_TOPIC_EXCHANGE", "abc.def.food", null, msg.getBytes());
```

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g22cvgoec5j20iy08gdgu.jpg)

## 3.3 Fanout Exchange 广播交换机

**定义**：广播类型的交换机与一个队列绑定时，不需要指定binding key。
**路由规则**：当消息发送到广播类型的交换机时，不需要指定routing key，所有与之绑定的队列都能收到消息。
例如：

```java
// 3个队列都会收到消息
channel.basicPublish("MY_FANOUT_EXCHANGE", "", null, msg.getBytes());
```

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g22czgmuhjj20ff066q3j.jpg)

## 3.4.headers

​     heads类型的交换器不依赖于路由键的匹配规则来路由消息，而是根据发送的消息内容中headers属性进行匹配。在绑定队列和交换器时制定一组键值对，当发送消息到交换器时，RabbitMQ会获取到该消息的headers(也是一个键值对的形式)，对比其中的键值对是否完全配队列和交换器绑定时指定的键值对，如果完全匹配则消息会路由到该队列，否则不会路由到该队列。headers类型的交换器性能会很差，而且也不实用，基本上不会看到它的存在。

