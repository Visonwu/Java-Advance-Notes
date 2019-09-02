



RocketMQ可以支持同一个消费组的广播消费以及负载消费，比kafka多一个广播消息



# 1.RocketMQ的事务消息模型

​		RocketMQ和其他消息中间件最大的一个区别是支持了事务消息，这也是分布式事务里面的基于消息的最终一致性方案

## 1.1 RocketMQ消息的事务架构设计

1. 生产者执行本地事务，修改订单支付状态，并且提交事务
2. 生产者发送事务消息到broker上，消息发送到broker上在没有确认之前，消息对于consumer是不
可见状态
3. 生产者确认事务消息，使得发送到broker上的事务消息对于消费者可见
4. 消费者获取到消息进行消费，消费完之后执行ack进行确认
5. 这里可能会存在一个问题，生产者本地事务成功后，发送事务确认消息到broker上失败了怎么办？这个时候意味着消费者无法正常消费到这个消息。所以RocketMQ提供了消息回查机制，如果事务消息一直处于中间状态，broker会发起重试去查询broker上这个事务的处理状态。一旦发现事务处理成功，则把当前这条消息设置为可见。

![ninD81.png](https://s2.ax1x.com/2019/09/02/ninD81.png)



## 1.2 RocketMQ 事务代码实现

通过一个下单以后扣减库存的数据一致性场景来演示RocketMQ的分布式事务特性

**TransactionProducer**

```java
public class TransactionProducer {
    public static void main(String[] args) throws MQClientException,
    UnsupportedEncodingException, InterruptedException {
        
        TransactionMQProducer transactionProducer=new 		 
            						TransactionMQProducer("tx_producer_group");
        
        transactionProducer.setNamesrvAddr("192.168.13.102:9876");
        ExecutorService executorService= Executors.newFixedThreadPool(10);
        
        //自定义线程池，用于异步执行事务操作
        transactionProducer.setExecutorService(executorService);
        //自定义监听器
        transactionProducer.setTransactionListener(new TransactionListenerLocal());
        transactionProducer.start();
        
        for(int i=0;i<20;i++) {
            String orderId= UUID.randomUUID().toString();
            String body="{'operation':'doOrder','orderId':'"+orderId+"'}";
            Message message = new Message("pay_tx_topic", "TagA",orderId,
            body.getBytes(RemotingHelper.DEFAULT_CHARSET));
            transactionProducer.sendMessageInTransaction(message,
            orderId+"&"+i);
            Thread.sleep(1000);
        }
    }
}
```

**TransactionListenerLocal**

```java
public class TransactionListenerLocal implements TransactionListener {
    
    private static final Map<String,Boolean> results=new ConcurrentHashMap<>();
    
    //执行本地事务  这里的arg是上面传递过来的参数
    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        System.out.println(":执行本地事务："+arg.toString());
        String orderId=arg.toString();
        boolean rs=saveOrder(orderId);//模拟数据入库操作
        return rs?
        LocalTransactionState.COMMIT_MESSAGE:LocalTransactionState.UNKNOW;
        // 这个返回状态表示告诉broker这个事务消息是否被确认，允许给到consumer进行消费
        // LocalTransactionState.ROLLBACK_MESSAGE 回滚
        //LocalTransactionState.UNKNOW 未知
    }
    
    //提供事务执行状态的回查方法，提供给broker回调
    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        String orderId=msg.getKeys();
        System.out.println("执行事务执行状态的回查，orderId:"+orderId);
        boolean rs=Boolean.TRUE.equals(results.get(orderId));
        System.out.println("回调："+rs);
        return rs?LocalTransactionState.COMMIT_MESSAGE:
        LocalTransactionState.ROLLBACK_MESSAGE;
    }
    
    private boolean saveOrder(String orderId){
        //如果订单取模等于0，表示成功,否则表示失败
        boolean success=Math.abs(Objects.hash(orderId))%2==0;
        results.put(orderId,success);
        return success;
    }
}
```

**TransactionConsumer**

```java
public class TransactionConsumer {
    public static void main(String[] args) throws MQClientException, IOException{
        
        DefaultMQPushConsumer defaultMQPushConsumer=new
       							 DefaultMQPushConsumer("tx_consumer_group");
        defaultMQPushConsumer.setNamesrvAddr("192.168.11.162:9876");
        defaultMQPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_O
        FFSET);
        defaultMQPushConsumer.subscribe("pay_tx_topic","*");
        
        
        defaultMQPushConsumer.registerMessageListener((MessageListenerConcurrently)
        (msgs, context) -> {
            msgs.stream().forEach(messageExt -> {
            try {
                String orderId=messageExt.getKeys();
                String body=new String(messageExt.getBody(),
                RemotingHelper.DEFAULT_CHARSET);
                System.out.println("收到消息:"+body+"，开始扣减库
                存："+orderId);
            } catch (UnsupportedEncodingException e) {
            		e.printStackTrace();
            }
        });
                
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
                
        defaultMQPushConsumer.start();
        System.in.read();
    }
}
```

## 1.3 RocketMQ事务消息的三种状态

1. ROLLBACK_MESSAGE：回滚事务

2. COMMIT_MESSAGE： 提交事务

3. UNKNOW： broker会定时的回查Producer消息状态，直到彻底成功或失败。

  

当executeLocalTransaction方法返回ROLLBACK_MESSAGE时，表示直接回滚事务，

当返回COMMIT_MESSAGE提交事务

当返回UNKNOW时，Broker会在一段时间之后回查checkLocalTransaction，根据checkLocalTransaction返回状态执行事务的操作（回滚或提交），

如示例中，当返回ROLLBACK_MESSAGE时消费者不会收到消息，且不会调用回查函数，当返回COMMIT_MESSAGE时事务提交，消费者收到消息，当返回UNKNOW时，在一段时间之后调用回查函数，并根据status判断返回提交或回滚状态，返回提交状态的消息将会被消费者消费，所以此时消费者可以消费部分消息



# 2. 消息的存储和发送

​			由于分布式消息队列对于可靠性的要求比较高，所以需要保证生产者将消息发送到broker之后，保证消息是不出现丢失的，因此消息队列就少不了对于可靠性存储的要求。



## 2.1 MQ消息存储选择

从主流的几种MQ消息队列采用的存储方式来看，主要会有三种
1. 分布式KV存储，比如ActiveMQ中采用的levelDB、Redis， 这种存储方式对于消息读写能力要求不
高的情况下可以使用
2. 文件系统存储，常见的比如kafka、RocketMQ、RabbitMQ都是采用消息刷盘到所部署的机器上的
文件系统来做持久化，这种方案适合对于有高吞吐量要求的消息中间件，因为消息刷盘是一种高效
率，高可靠、高性能的持久化方式，除非磁盘出现故障，否则一般是不会出现无法持久化的问题
3. 关系型数据库，比如ActiveMQ可以采用mysql作为消息存储，关系型数据库在单表数据量达到千
万级的情况下IO性能会出现瓶颈，所以ActiveMQ并不适合于高吞吐量的消息队列场景。
总的来说，对于存储效率，文件系统要优于分布式KV存储，分布式KV存储要优于关系型数据库



## 2.2 消息的存储结构

RocketMQ就是采用文件系统的方式来存储消息，消息的存储是由ConsumeQueue和CommitLog配合完成的。

CommitLog是消息真正的物理存储文件。ConsumeQueue是消息的逻辑队列，有点类似于数据库的索引文件，里面存储的是指向CommitLog文件中消息存储的地址。每个Topic下的每个Message Queue都会对应一个ConsumeQueue文件，文件的地址是：${store_home}/consumequeue/${topicNmae}/${queueId}/${filename}, 默认路径: /root/store在rocketMQ的文件存储目录下。。



我们只需要关心Commitlog、Consumequeue、Index

### 1）CommitLog
CommitLog是用来存放消息的物理文件，每个broker上的commitLog本当前机器上的所有consumerQueue共享，不做任何的区分。
CommitLog中的文件默认大小为1G，可以动态配置； 当一个文件写满以后，会生成一个新的commitlog文件。所有的Topic数据是顺序写入在CommitLog文件中的。
文件名的长度为20位，左边补0，剩余未起始偏移量，比如
00000000000000000000 表示第一个文件， 文件大小为102410241024，当第一个文件写满之后，生成第二个文件000000000001073741824 表示第二个文件，起始偏移量为1073741824



### 2） ConsumeQueue

​		consumeQueue表示消息消费的逻辑队列，这里面包含MessageQueue，在commitlog中的其实物理位置偏移量offset，消息实体内容的大小和Message Tag的hash值。对于实际物理存储来说，consumeQueue对应每个topic和queueid下的文件，每个consumeQueue类型的文件也是有大小，每个文件默认大小约为600W个字节，如果文件满了后会也会生成一个新的文件



### 3）IndexFile
​	索引文件，如果一个消息包含Key值的话，会使用IndexFile存储消息索引。Index索引文件提供了对CommitLog进行数据检索，提供了一种通过key或者时间区间来查找CommitLog中的消息的方法。在物理存储中，文件名是以创建的时间戳明明，固定的单个IndexFile大小大概为400M，一个IndexFile可以保存2000W个索引



### 4）abort

​		broker在启动的时候会创建一个空的名为abort的文件，并在shutdown时将其删除，用于标识进程是否正常退出，如果不正常退出,会在启动时做故障恢复



## 2.3 消息存储的整体结构

![niMgbT.png](https://s2.ax1x.com/2019/09/02/niMgbT.png)

​	RocketMQ的消息存储采用的是混合型的存储结构，也就是Broker单个实例下的所有队列公用一个日志数据文件CommitLog。这个是和Kafka又一个不同之处。

为什么不采用kafka的设计，针对不同的partition存储一个独立的物理文件呢？这是因为在kafka的设计中，一kafka中Topic的Partition数量过多，队列文件会过多，那么会给磁盘的IO读写造成比较大的压力，也就造成了性瓶颈。所以RocketMQ进行了优化，消息主题统一存储在CommitLog中。

当然，这种设计并不是银弹，它也有它的优缺点

**优点在于**：由于消息主题都是通过CommitLog来进行读写，ConsumerQueue中只存储很少的数据，所以队列更加轻量化。对于磁盘的访问是串行化从而避免了磁盘的竞争

**缺点在于**：消息写入磁盘虽然是基于顺序写，但是读的过程确是随机的。读取一条消息会先读ConsumeQueue，再读CommitLog，会降低消息读的效率。



## 2.4 消息发送到消息接收的整体流程

1. Producer将消息发送到Broker后，Broker会采用同步或者异步的方式把消息写入到CommitLog。
RocketMQ所有的消息都会存放在CommitLog中，为了保证消息存储不发生混乱，对CommitLog写之前会加锁，同时也可以使得消息能够被顺序写入到CommitLog，只要消息被持久化到磁盘文件CommitLog，那么就可以保证Producer发送的消息不会丢失。

[![niQ9Mt.md.png](https://s2.ax1x.com/2019/09/02/niQ9Mt.md.png)](https://imgchr.com/i/niQ9Mt)

2. commitLog持久化后，会把里面的消息Dispatch到对应的Consume Queue上，Consume Queue相当于kafka中的partition，是一个逻辑队列，存储了这个Queue在CommiLog中的起始offset，log大小和MessageTag的hashCode。

[![niQAIg.png](https://s2.ax1x.com/2019/09/02/niQAIg.png)](https://imgchr.com/i/niQAIg)

3. 当消费者进行消息消费时，会先读取consumerQueue , 逻辑消费队列ConsumeQueue保存了指定Topic下的队列消息在CommitLog中的起始物理偏移量Offset，消息大小、和消息Tag的HashCode值

[![niQeRs.png](https://s2.ax1x.com/2019/09/02/niQeRs.png)](https://imgchr.com/i/niQeRs)

4. 直接从consumequeue中读取消息是没有数据的，真正的消息主体在commitlog中，所以还需要从commitlog中读取消息

![niQJJJ.png](https://s2.ax1x.com/2019/09/02/niQJJJ.png)



# 3. 什么时候清理物理消息文件？

那消息文件到底删不删，什么时候删？

消息存储在CommitLog之后，的确是会被清理的，但是这个清理只会在以下任一条件成立才会批量删除消息文件（CommitLog）：
1. 消息文件过期（默认72小时），且到达清理时点（默认是凌晨4点），删除过期文件。
2. 消息文件过期（默认72小时），且磁盘空间达到了水位线（默认75%），删除过期文件。
3. 磁盘已经达到必须释放的上限（85%水位线）的时候，则开始批量清理文件（无论是否过期），直到空间充足。
注：若磁盘空间达到危险水位线（默认90%），出于保护自身的目的，broker会拒绝写入服务。