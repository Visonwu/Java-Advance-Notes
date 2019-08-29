# 1. RocketMQ的发展历史

​		RocketMq是一个由阿里巴巴开源的消息中间件， 2012年开源，2017年成为apache顶级项目。
它的核心设计借鉴了Kafka，所以我们在了解RocketMQ的时候，会发现很多和kafka相同的特性。同时
呢，Rocket在某些功能上和kafka又有较大的差异，RocktMQ特性:

1. 支持集群模型、负载均衡、水平扩展能力
2. 亿级别消息堆积能力
3. 采用零拷贝的原理，顺序写盘，随机读
4. 底层通信框架采用Netty NIO
5. NameServer代替Zookeeper，实现服务寻址和服务协调
6. 消息失败重试机制、消息可查询
7. 强调集群无单点，可扩展，任意一点高可用，水平可扩展
8. 经过多次双十一的考验



# 2. RocketMQ的架构及角色

架构图如下：

![mLzp4S.png](https://s2.ax1x.com/2019/08/29/mLzp4S.png)



​	集群本身没有什么特殊之处，和kafka的整体架构类似，其中zookeeper替换成了NameServer。

> 在rocketmq的早版本（2.x）的时候，是没有namesrv组件的，用的是zookeeper做分布式协调
> 和服务发现，但是后期阿里数据根据实际业务需求进行改进和优化，自组研发了轻量级的
> namesrv,用于注册Client服务与Broker的请求路由工作，namesrv上不做任何消息的位置存储，
> 频繁操作zookeeper的位置存储数据会影响整体集群性能



RocketMQ由四部分组成

- 1）`Name Server` 可集群部署，节点之间无任何信息同步。提供轻量级的服务发现和路由
- 2）`Broker`(消息中转角色，负责存储消息，转发消息) 部署相对复杂，Broker 分为Master 与Slave，一个Master 可以对应多个Slave，但是一个Slave 只能对应一个Master，Master 与Slave 的对应关系通过指定相同的BrokerName，不同的BrokerId来定 义，BrokerId为0 表示Master，非0 表示Slave。Master 也可以部署多个。
- 3）`Producer`，生产者，拥有相同 Producer Group 的 Producer 组成一个集群， 与Name Server 集群中的其中一个节点（随机选择）建立长连接，定期从Name Server 取Topic 路由信息，并向提供Topic服务的Master 建立长连接，且定时向Master 发送心跳。Producer 完全无状态，可集群部署。
- 4）`Consumer`，消费者，接收消息进行消费的实例，拥有相同 Consumer Group 的 Consumer 组成一个集群，与Name Server 集群中的其中一个节点（随机选择）建立长连接，定期从Name Server 取Topic 路由信息，并向提供Topic 服务的Master、Slave 建立长连接，且定时向Master、Slave 发送心跳。Consumer既可以从Master 订阅消息，也可以从Slave 订阅消息，订阅规则由Broker 配置决定。要使用rocketmq，至少需要启动两个进程，nameserver、broker，前者是各种topic注册中心，后者是真正的broker。



# 3. 安装

## 3.1 单机安装

1. 下载rocketmq的安装文件: http://rocketmq.apache.org

2. 【unzip rocketmq-all-4.5.0-bin-release.zip】 解压压缩包

3. 启动NameServer

   -   进入到bin目录下，运行namesrv，启动NameServer ->ps: `nohup command &` 表示在后台运行

   ```bash
   nohup sh mqnamesrv &
   ```

   - 默认情况下，nameserver监听的是9876端口。

   - 【tail -f ~/logs/rocketmqlogs/namesrv.log】 查看启动日志

4.启动Broker

`nohup sh bin/mqbroker -n ${namesrvIp}:9876 -c /conf/broker.conf & `

[-c可以指定broker.conf配置文件]。默认情况下会加载conf/broker.conf

1. 【`nohup sh mqbroker -n localhost:9876 &`】 启动broker，其中-n表示指定当前broker对应的命
名服务地址： 默认情况下，broker监听的是10911端口。
2. 输入 tail -f ~/logs/rocketmqlogs/broker.log 查看日志
3. 如果 tail -f ~/logs/rocketmqlogs/broker.log 提示找不到文件，则打开当前目录下的 nohup.out
日志文件查看，出现如下日志表示启动失败，提示内存无法分配

## 3.1 内存分配不足

​		这是因为bin 目录下启动 nameserv 与 broker 的 runbroker.sh 和 runserver.sh 文件中默认分配的内存太大，rocketmq比较耗内存，所以默认分配的内存比较大，而系统实际内存却太小导致启动失败，
通常像虚拟机上安装的 CentOS 服务器内存可能是没有高的，只能调小。实际中应该根据服务器内存情况，配置一个合适的值。

解决办法：

​	修改runbroker.sh和runbroker.sh

```xml
JAVA_OPT="${JAVA_OPT} -server -Xms1g -Xmx1g -Xmn512g"
Xms 是指设定程序启动时占用内存大小。一般来讲，大点，程序会启动的快一点，但是也可能会导致机器暂时
间变慢。
Xmx 是指设定程序运行期间最大可占用的内存大小。如果程序运行需要占用更多的内存，超出了这个设置值，
就会抛出OutOfMemory异常。
xmn 年轻代的heap大小，一般设置为Xmx的3、4分之一
```

然后重启服务

## 3.2 停止服务

`sh bin/mqshutdown broker` //停止 brokersh
`bin/mqshutdown namesrv` //停止 nameserver
停止服务的时候需要注意，要先停止broker，其次停止nameserver。



# 4.broker.conf文件

```xml

默认情况下，启动broker会加载conf/broker.conf文件，这个文件里面就是一些常规的配置信息
namesrvAddr //nameserver地址
brokerClusterName //Cluster名称，如果集群机器数比较多，可以分成多个cluster，每个cluster提供给不同的业务场景使用

brokerName //broker名称，如果配置主从模式，master和slave需要配置相同的名称来表名关系
brokerId=0 //在主从模式中，一个master broker可以有多个slave，0表示master，大于0表示不同slave的id
brokerRole=SYNC_MASTER/ASYNC_MASTER/SLAVE ; 同步表示slave和master消息同步完成后再返回信息给客户端
autoCreateTopicEnable = true ; topic不存在的情况下自动创建

```

# 5.API基本使用

建议参考官网文档：<http://rocketmq.apache.org/docs/quick-start/>

## 5.1 依赖

​	建议你的依赖包和你下载的RocketMQ客户端版本尽量一致，否则容易出现问题

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-client</artifactId>
    <version>4.5.2</version>
</dependency>
```

## 5.2 生产者

​		 可以同步发送，也可以异步发送，同步发送的时候发送的时候获取结果，这样就阻塞得到结果，异步就采用注册监听，通过注册监听获取异步结果信息

```java
public class ProducerDemo {

    public static void main(String[] args) throws MQClientException, UnsupportedEncodingException {
        DefaultMQProducer producer = new DefaultMQProducer("vison-rocket-mq" );
        
        //从nameServer获取broker信息
        producer.setNamesrvAddr("192.168.124.134:9876");
        producer.start();
        for (int i = 0; i < 100; i++) {
            Message msg = new Message("vison",
                    "TagA" ,
                    ("Hello RocketMQ " + i).getBytes(RemotingHelper.DEFAULT_CHARSET) /* Message body */
            );
            SendResult sendResult = null;
            try {
                //这里是同步监听结果，不然后就在send后面添加listener监听结果
                sendResult = producer.send(msg);
            } catch (RemotingException e) {
                e.printStackTrace();
            } catch (MQBrokerException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.printf("%s%n", sendResult);
        }

    }
}
```

SendResult中，有一个sendStatus状态，表示消息的发送状态。一共有四种状态
1. `FLUSH_DISK_TIMEOUT `： 表示没有在规定时间内完成刷盘（需要Broker 的刷盘策Ill创立设置成SYNC_FLUSH 才会报这个错误） 。
2. `FLUSH_SLAVE_TIMEOUT` ：表示在主备方式下，并且Broker 被设置成SYNC_MASTER 方式，没有在设定时间内完成主从同步。
3. `SLAVE_NOT_AVAILABLE` ： 这个状态产生的场景和FLUSH_SLAVE_TIMEOUT 类似， 表示在主备方式下，并且Broker 被设置成SYNC_MASTER ，但是没有找到被配置成Slave 的Broker 。
4. `SEND OK` ：表示发送成功，发送成功的具体含义，比如消息是否已经被存储到磁盘？消息是否被同步到了Slave 上？消息在Slave 上是否被写入磁盘？需要结合所配置的刷盘策略、主从策略来定。这个状态还可以简单理解为，没有发生上面列出的三个问题状态就是SEND OK



## 5.3 消费者

​	consumerGroup：位于同一个consumerGroup中的consumer实例和producerGroup中的各个produer实例承担的角色类似；同一个group中可以配置多个consumer，可以提高消费端的并发消费能力以及容灾

​	和kafka一样，多个consumer会对消息做负载均衡，意味着同一个topic下的不同messageQueue会分发给同一个group中的不同consumer。同时，如果我们希望消息能够达到广播的目的，那么只需要把consumer加入到不同的group就行。

​		RocketMQ提供了两种消息消费模型，一种是pull主动拉去，另一种是push，被动接收。但实际上RocketMQ都是pull模式，只是push在pull模式上做了一层封装，也就是pull到消息以后触发业务消费者注册到这里的callback. RocketMQ是基于长轮训来实现消息的pull。

nameServer的地址：name server地址，用于获取broker、topic信息

```java
public class ConsumerDemo {

    public static void main(String[] args) throws MQClientException, IOException {
		//消费者的组名，这个和kafka是一样,这里需要注意的是
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(
                "vison-rocket-mq");
        
        //设置Consumer第一次启动是从队列头部开始消费还是队列尾部开始消费
		//如果非第一次启动，那么按照上次消费的位置继续消费
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
       
        //指定NameServer地址，多个地址以 ; 隔开
        consumer.setNamesrvAddr("192.168.124.134:9876");
        
        //订阅PushTopic下Tag为push的消息
        consumer.subscribe("vison","TagA");//*表示不过滤，可以通过tag来过滤，比如:”tagA”

        
        //这里有两种监听，MessageListenerConcurrently以及MessageListenerOrderly
        //前者是普通监听，后者是顺序监听。这块在后续说明
        consumer.registerMessageListener(new MessageListenerConcurrently() {

            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs,
                                                            ConsumeConcurrentlyContext context) {
                System.out.printf("%s Receive New Messages: %s %n", Thread.currentThread().getName(), msgs);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();
        System.out.printf("Consumer Started.%n");
    }
}
```

# 6.RocketMQ控制台安装

​		启动好服务以后，总得有一个可视化界面来看看我们配置的节点情况吧。rocket官方提供了一个可视化
控制台，大家可以在这个地址下载https://github.com/apache/rocketmq-externals
​	这个是rocketmq的扩展，里面不仅包含控制台的扩展，也包含对大数据flume、hbase等组件的对接和扩展。



## 6.1 下载源码包

​		https://github.com/apache/rocketmq-externals/archive/master.zip

## 6.2 解压并修改配置

​			cd /${rocketmq-externals-home}/rocket-console/

- 修改application.properties文件

- 配置namesrvAddr地址，指向目标服务的ip和端口:
  `rocketmq.config.namesrvAddr=192.168.13.162:9876`

  

## 6.3 运行

1. cd /${rocketmq-externals-home}/rocket-console/
2. `mvn spring-boot:run`



## 6.4 通过控制台创建消息

​	要能够发送和接收消息，需要先创建Topic，这里的Topic和kafka的topic的概念是一样的进入到控制台，选择topic。

- readQueueNums和writeQueueNums分别表示读队列数和写队列数
  - writeQueueNums表示producer发送到的MessageQueue的队列个数
  - readQueueNums表示Consumer读取消息的MessageQueue队列个数，其实类似于kafka的分区的概念这两个值需要相等，在集群模式下如果不相等，假如说writeQueueNums=6,readQueueNums=3, 那
    么每个broker上会有3个queue的消息是无法消费的。

- pem:表示队列的权限 ，2表示W（Write）,4表示R（Read）,6表示RW



# 7.消息支持的模式

## 7.1 NormalProducer（普通）
**消息同步发送**
		普通消息的发送和接收在前面已经演示过了，在上面的案例中是基于同步消息发送模式。也就是说消息发送出去后，producer会等到broker回应后才能继续发送下一个消息。



**消息异步发送**
		异步发送是指发送方发出数据后，不等接收方发回响应，接着发送下个数据包的通讯方式。 MQ 的异步发送，需要用户实现异步发送回调接口（SendCallback）。消息发送方在发送了一条消息后，不需要等待服务器响应即可返回，进行第二条消息发送。发送方通过回调接口接收服务器响应，并对响应结果进行处理。



**OneWay**
		单向（Oneway）发送特点为发送方只负责发送消息，不等待服务器回应且没有回调函数触发，即只发送请求不等待应答.效率最高。



## 7.2 OrderProducer（顺序）

​		前面我们学习kafka的时候有说到，消息可以通过自定义分区策略来失效消息的顺序发送，实现原理就是把同一类消息都发送到相同的分区上。
​		在RocketMQ中，是基于多个Message Queue来实现类似于kafka的分区效果。如果一个Topic 要发送和接收的数据量非常大， 需要能支持增加并行处理的机器来提高处理速度，这时候一个Topic 可以根据需求设置一个或多个Message Queue。Topic 有了多个Message Queue 后，消息可以并行地向各个Message Queue 发送，消费者也可以并行地从多个Message Queue 读取消息并消费。要了解RocketMQ消息的顺序消费，还得对RocketMQ的整体架构有一定的了解



# 8. RocketMQ消息发送及消费的基本原理

​		这是一个比较宏观的部署架构图，rocketmq天然支持高可用，它可以支持多主多从的部署架构，这也是和kafka最大的区别之一

​		原因是RocketMQ中并没有master选举功能，所以通过配置多个master节点来保证rocketMQ的高可用。和所有的集群角色定位一样，master节点负责接受事务请求、slave节点只负责接收读请求，并且接收master同步过来的数据和slave保持一直。当master挂了以后，如果当前rocketmq是一主多从，就意味着无法接受发送端的消息，但是消费者仍然能够继续消费。

​		所以配置多个主节点后，可以保证当其中一个master节点挂了，另外一个master节点仍然能够对外提供消息发送服务。

​		当存在多个主节点时，一条消息只会发送到其中一个主节点，rocketmq对于多个master节点的消息发送，会做负载均衡，使得消息可以平衡的发送到多个master节点上。

​		一个消费者可以同时消费多个master节点上的消息，在下面这个架构图中，两个master节点恰好可以平均分发到两个消费者上，如果此时只有一个消费者，那么这个消费者会消费两个master节点的数据。由于每个master可以配置多个slave，所以如果其中一个master挂了，消息仍然可以被消费者从slave节点消费到。可以完美的实现rocketmq消息的高可用。如图：

![mLxyhF.png](https://s2.ax1x.com/2019/08/29/mLxyhF.png)



​	接下来，站在topic的角度来看看消息是如何分发和处理的，假设有两个master节点的集群，创建了一个TestTopic，并且对这个topic创建了两个队列，也就是分区，（每一个master都有两个队列），然后生产者发送消息的时候通过路由选择发送到broker-A或者broker-B上，然后再次路由到某一个队列；这里表示有两次分发情况。



消费者定义了两个分组，分组的概念也是和kafka一样，通过分组可以实现消息的广播。

如图：

[![mLxotO.png](https://s2.ax1x.com/2019/08/29/mLxotO.png)](https://imgchr.com/i/mLxotO)



## 8.1 集群支持

RocketMQ天生对集群的支持非常友好
**1）单Master**
		优点：除了配置简单没什么优点
		缺点：不可靠，该机器重启或宕机，将导致整个服务不可用

**2）多Master**
		优点：配置简单，性能最高
		缺点：可能会有少量消息丢失（配置相关），单台机器重启或宕机期间，该机器下未被消费的消息在机器恢复前不可订阅，影响消息实时性

**3）多Master多Slave，集群采用异步复制方式**, 

​		每个Master配一个Slave，有多对Master-Slave，主备有短暂消息延迟，毫秒级
​		优点：性能同多Master几乎一样，实时性高，主备间切换对应用透明，不需人工干预
​		缺点：Master宕机或磁盘损坏时会有少量消息丢失

**4）多Master多Slave，集群采用同步双写方式**

​		每个Master配一个Slave，有多对Master-Slave，主备都写成功，向应用返回成功

 		优点：服务可用性与数据可用性非常高
		缺点：性能比异步集群略低，当前版本主宕备不能自动切换为主



​		需要注意的是：在RocketMQ里面，1台机器只能要么是Master，要么是Slave。这个在初始的机器配置里面，就定死了。不会像kafka那样存在master动态选举的功能。其中Master的broker id = 0，Slave的broker id > 0。

​		有点类似于mysql的主从概念，master挂了以后，slave仍然可以提供读服务，但是由于有多主的存在，当一个master挂了以后，可以写到其他的master上。



## 8.2 消息发送到topic多个MessageQueue
1. 创建一个队列，设置2个写队列以及2个读队列，如果读和写队列不一致，会存在消息无法消费到的问题

2. 构建生产者和消费者:参考上面写的生产者消费者代码
3. 消费者数量控制对于队列的消费情况
a) 如果消费队列为2，启动一个消费者，那么这个消费者会消费者两个队列，
b) 如果两个消费者消费这个队列，那么意味着消息会均衡分摊到这两个消费者中
c）如果消费者数大于readQueueNumbs，那么会有一些消费者消费不到消息，浪费资源

## 8.3消息的顺序消费

​		首先，需要保证顺序的消息要发送到同一个messagequeue中；其次，一个messagequeue只能被一个
消费者消费，这点是由消息队列的分配机制来保证的；最后，一个消费者内部对一个mq的消费要保证
是有序的。
我们要做到生产者 - messagequeue - 消费者之间是一对一对一的关系。



## 8.4 自定义消息发送规则

通过自定义发送策略来实现消息只发送到同一个队列

​	因为一个Topic 会有多个Message Queue ，如果使用Producer 的默认配置，这个Producer 会轮流向各个Message Queue 发送消息。Consumer 在消费消息的时候，会根据负载均衡策略，消费被分配到的Message Queue

如果不经过特定的设置，某条消息被发往哪个Message Queue ，被哪个Consumer 消费是未知的
如果业务需要我们把消息发送到指定的Message Queue 里，比如把同一类型的消息都发往相同的
Message Queue。那是不是可以实现顺序消息的功能呢？

和kafka一样，rocketMQ也提供了消息路由的功能，我们可以自定义消息分发策略，可以实现
MessageQueueSelector，来实现自己的消息分发策略

```java
SendResult sendResult=producer.send(msg, new MessageQueueSelector() {
    
    @Override
    public MessageQueue select(List<MessageQueue> list, Message message, Object o) {
        int key=o.hashCode();
        int size = list.size();
        int index = key%size;
        return list.get(index);// list.get(0);
    }
},1); //这里表示队列下标，这个值会传给上面的Object o参数
```



## 8.5 如何保证消息消费顺序呢？
​		通过分区规则可以实现同类消息在rocketmq上的顺序存储。但是对于消费端来说，如何保证消费的顺序？

我们前面写的消息消费代码使用的是MessageListenerConcurrently并发监听，也就是基于多个线程并行来消费消息。这个无法保证消息消费的顺序。

RocketMQ中提供了MessageListenerOrderly 一个类来实现顺序消费，

```java
consumer.subscribe("store_topic_test","*");
    consumer.registerMessageListener((MessageListenerOrderly) (list,
    	consumeOrderlyContext) -> {
        
        list.stream().forEach(messageExt -> System.out.println(new
        String(messageExt.getBody())));
        return ConsumeOrderlyStatus.SUCCESS;
});
```

**顺序消费会带来一些问题:**

1. 遇到消息失败的消息，无法跳过，当前队列消费暂停
2. 降低了消息处理的性能



## 8.6 消费端的负载均衡
​		和kafka一样，消费端也会针对Message Queue做负载均衡，使得每个消费者能够合理的消费多个分区的消息。

**1)   消费端会通过`RebalanceService`线程，10秒钟做一次基于topic下的所有队列负载**

- 消费端遍历自己的所有topic，依次调rebalanceByTopic
- 根据topic获取此topic下的所有queue
- 选择一台broker获取基于group的所有消费端（有心跳向所有broker注册客户端信息）
- 选择队列分配策略实例AllocateMessageQueueStrategy执行分配算法



**2)   什么时候触发负载均衡?**

- 消费者启动之后
- 消费者数量发生变更
- 每10秒会触发检查一次rebalance



**3)  分配算法**

RocketMQ提供了6中分区的分配算法，

（AllocateMessageQueueAveragely）平均分配算法（默认）
（AllocateMessageQueueAveragelyByCircle）环状分配消息队列
（AllocateMessageQueueByConfig）按照配置来分配队列： 根据用户指定的配置来进行负载
（AllocateMessageQueueByMachineRoom）按照指定机房来配置队列
（AllocateMachineRoomNearby）按照就近机房来配置队列：
（AllocateMessageQueueConsistentHash）一致性hash，根据消费者的cid进行



## 8.7 消息的的可靠性原则

​	在实际使用RocketMQ的时候我们并不能保证每次发送的消息都刚好能被消费者一次性正常消费成功，可能会存在需要多次消费才能成功或者一直消费失败的情况，那作为发送者该做如何处理呢？



​		RocketMQ提供了ack机制，以保证消息能够被正常消费。发送者为了保证消息肯定消费成功，只有使用方明确表示消费成功，RocketMQ才会认为消息消费成功。中途断电，抛出异常等都不会认为成功

```java
consumer.registerMessageListener((MessageListenerConcurrently) (list,
    consumeOrderlyContext) -> {
    
    list.stream().forEach(messageExt -> System.out.println(new
    String(messageExt.getBody())));
    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
});
```

​		所有消费者在设置监听的时候会提供一个回调，业务实现消费回调的时候，当回调方法中返回
`ConsumeConcurrentlyStatus.CONSUME_SUCCESS`，RocketMQ才会认为这批消息（默认是1条）是消费完成的。如果这时候消息消费失败，例如数据库异常，余额不足扣款失败等一切业务认为消息需要重试的场景，只要返回ConsumeConcurrentlyStatus.RECONSUME_LATER，RocketMQ就会认为这批消息消费失败了



## 8.8 消息的衰减重试
​		为了保证消息肯定至少被消费一次，RocketMQ会把这批消息重新发回到broker，在延迟的某个时间点（默认是10秒，业务可设置）后，再次投递到这个ConsumerGroup。而如果一直这样重复消费都持续失败到一定次数（默认16次），就会投递到DLQ死信队列。应用可以监控死信队列来做人工干预可以修改broker-a.conf文件

`messageDelayLevel = 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h`



## 8.9 重试消息的处理机制
​		一般情况下我们在实际生产中是不需要重试16次，这样既浪费时间又浪费性能，理论上当尝试重复次数
达到我们想要的结果时如果还是消费失败，那么我们需要将对应的消息进行记录，并且结束重复尝试

```java
consumer.registerMessageListener((MessageListenerConcurrently) (list,
consumeOrderlyContext) -> {
    for (MessageExt messageExt : list) {
        if(messageExt.getReconsumeTimes()==3) { //重试3次后
            //可以将对应的数据保存到数据库，以便人工干预
            System.out.println(messageExt.getMsgId()+","+messageExt.getBody());
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
    }
    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
});
```

