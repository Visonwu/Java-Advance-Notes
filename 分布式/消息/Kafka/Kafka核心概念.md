# 1.Topic和Partition

## 1.1 Topic
​		在kafka 中， topic 是一个存储消息的逻辑概念，可以认为是一个消息集合。每条消息发送到 kafka 集群的消息都有一个类别。物理上来说，不同的 topic 的消息是分开存储的，每个topic 可以有多个生产者向它发送消息，也可以有多个消费者去消费其中的消息。



## 1.2 Partition

​		每个topic 可以划分多个分区（每个 Topic 至少有一个分区），同一 topic 下的不同分区包含的消息是不同的。每个消息在被添加到分区时，都会被分配一个 offset （称之为偏移量），它是消息在此分区中的唯一编号， kafka 通过 offset保证消息在分区内的顺序， offset 的顺序不跨分区，即 kafka只保证在同一个分区内的消息是有序的。



​		每一条消息发送到 broker 时，会根据 partition 的规则选择存储到哪一个 partition 。如果 partition 规则设置合理，那么所有的消息会均匀的分布在不同的 partition 中，这样就有点类似数据库的分库分表的概念，把数据做了**分片处理**。



## 1.3 Topic&Partition 的存储
​		Partition是以文件的形式存储在文件系统中，比如创建一个名为firstTopic的topic，其中有3个partition ，那么在kafka的数据目录 /tmp/kafka-logs 中就有3个目录，firstTopic-0~ 3 命名规则是 

`<topic_ name>-<partition_id>`

​		如上面的三个分区就会命名为firstTopic-0，firstTopic-1，firstTopic-2，如果同时是三个broker集群，那么这三个Partition会散布在三个机器上；

如果是四个broker，其中一个broker是没有改topic分区数据的；

如果只有两broker,其中一个broker有两个Partition数据

```bash
 bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --replication-factor 3 --partitions 1 --topic my-replicated-topic

```



# 2.关于消息分发
## 2.1 kafka消息分发策略

​		消息是kafka 中最基本的数据单元，在 kafka 中，一条消息由 key 、 value 两部分构成，在发送一条消息时，我们可以指定这个 key ，那么 producer 会**根据 key 和 partition 机制**来判断当前这条消息应该发送并存储到哪个 partition 中。我们可以根据需要进行扩展 producer 的 partition 机制。



## 2.2 消息默认的分发机制

默认情况下，kafka 采用的是 hash 取模的分区算法。

​		如果Key 为 null ，则会随机分配一个分区 。这个随机是在这个参数 ”metadata.max.age. 的时间范围内随机选择一 个。对于这个时间段内，如果 key 为 null ，则只会发送到唯一的分区。这个值默认情况下是 10 分钟更新一次。

​		关于Metadata ，简单理解就是Topic/Partition 和 broker 的映射关系，每一个 topic 的每一个 partition ，需要知道对应的 broker 列表是什么， leader是谁、 follower 是谁。这些信息都是存储在 Metadata 这个类里面。



**生产者指定自定义的分发策略**

```java
//自定义策略
public class MyPartition implements Partitioner {

    private Random random = new Random();

    @Override
    public int partition(String topic, Object key, byte[] keyBytes,
                         Object value, byte[] valueBytes, Cluster cluster) {
        List<PartitionInfo> partitionInfos = cluster.partitionsForTopic(topic);
        int partition = 0;
        if (key == null){
            partition = random.nextInt(partitionInfos.size());//随机分区
        }else {
            partition=Math.abs(key.hashCode()%partitionInfos.size());
        }
        return partition; //指定分区值
    }

    @Override
    public void close() {

    }

    @Override
    public void configure(Map<String, ?> configs) {

    }
}
```

**发送数据，添加自定义分区策略**

```java
public class KafkaProducerDemo {

    public static void main(String[] args) {
         Properties props = new Properties();
        props.put("bootstrap.servers", "192.168.124.160:9092");
        props.put("acks", "all");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        //自定义分区策略
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG,"com.vison.MyPartition");
        
        Producer<String, String> producer = new KafkaProducer<>(props);
        for (int i = 0; i < 100; i++) {
            producer.send(new ProducerRecord<String, String>("foo", Integer.toString(i), Integer.toString(i)));
        }
        producer.close();
    }
}
```



# 3. 消息的消费原理

​		在实际生产过程中，每个topic 都会有多个 partitions ，多个 partitions 的好处在于，一方面能够对 broker 上的数据进行分片有效减少了消息的容量从而提升 io 性能。另外一方面，为了提高消费端的消费能力，一般会通过多个consumer 去消费同一个 topic ，也就是消费端的负载均衡机制，也就是我们接下来要了解的，在多个partition 以及多个 consumer 的情况下，消费者是如何消费消息的

​		同时，kafka 存在 consumer group的概念，也就是 group.id 一样 的 consumer ，这些consumer 属于一个 consumer group ，组内的所有消费者协调在一起来消费订阅主题的所有分区。当然每一个分区只能由同一个消费组内的 consumer 来消费，那么同一个consumer group 里面的 consumer 是怎么去分配该消费哪个分区里的数据的呢？



​		 如下图所示， 3 个分区， 3 个消费者，这3 个消费者会分别消费 test 这个topic 的 3 个分区，也就是每个 consumer 消费一个partition

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4z7345yfyj20mz09etak.jpg)

​	

## 3.1 消费端 指定分区消费

​		通过下面的代码，就可以消费指定该topic 下的 1 号分区

```java
//consumer.subscribe(Arrays.asList("foo", "bar"));
TopicPartition partition = new TopicPartition("foo",1);
consumer.assign(Arrays.asList(partition));
```



## 3.2 分区分配策略

​		同一个group 中的消费者对于一个topic 中的多个 partition ，存在一定的分区分配策略。在kafka 中，存在两种分区分配策略，一种是 Range( 默认）、另一种还是 RoundRobin （轮询）。 通过`partition.assignment.strategy` 这个参数来设置



### 1) Range strategy 范围分区

​		Range策略是对每个主题而言的，首先对同一个主题里面的分区按照序号进行排序，并对消费者按照字母顺序进行排序。 假设我们有10个分区，3 个消费者 ，排完序的分区将会是 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 ；消费者线程排完序将会是C1-0, C2-0, C3-0 。然后将 partitions 的个数除于消费者线程的总数来决定每个消费者线程消费几个分区。如果除不尽，那么前面几个消费者线程将会多消费一个分区。在我们的例子里面，我们有10个分区， 3个消费者线程， 10 /3 = 3 ，而且除不尽，那么消费者线程 C1-0 将会多消费一个分区，所以最后分区分配

```bash
##消费结果
C1-0 将消费 0, 1, 2, 3 分区
C2-0 将消费 4, 5, 6 分区
C3-0 将消费 7, 8, 9 分区
```

**Range strategy 的一个很明显的弊端:**

​		通过上面了解到，一个Topic如果分区和消费者除不尽，有部分消费者会多消费一个；那么当有多个Topic的时候，排在消费者前的就会消费许多个分区。



### 2) RoundRobin strategy 轮询分区
​		轮询分区策略是把**所有partition** 和**所有 consumer 线程**都列出来，然后按照 hashcode 进行排序。最后通过轮询算法分配 partition 给消费线程。如果所有 consumer 实例的订阅是相同的 ，那么 partition 会均匀分布。

​	如果同一个消费组内所有的消费者的订阅信息都是相同的，那么RoundRobinAssignor策略的分区分配会是均匀的。举例，假设消费组中有2个消费者C0和C1，都订阅了主题t0和t1，并且每个主题都有3个分区，那么所订阅的所有分区可以标识为：t0p0、t0p1、t0p2、t1p0、t1p1、t1p2。最终的分配结果为：

```bash
消费者C0：t0p0、t0p2、t1p1
消费者C1：t0p1、t1p0、t1p2
```

​	如果同一个消费组内的消费者所订阅的信息是不相同的，那么在执行分区分配的时候就不是完全的轮询分配，有可能会导致分区分配的不均匀。如果某个消费者没有订阅消费组内的某个topic，那么在分配分区的时候此消费者将分配不到这个topic的任何分区。

​	举例，假设消费组内有3个消费者C0、C1和C2，它们共订阅了3个主题：t0、t1、t2，这3个主题分别有1、2、3个分区，即整个消费组订阅了t0p0、t1p0、t1p1、t2p0、t2p1、t2p2这6个分区。具体而言，消费者C0订阅的是主题t0，消费者C1订阅的是主题t0和t1，消费者C2订阅的是主题t0、t1和t2，那么最终的分配结果为：

```bash
消费者C0：t0p0
消费者C1：t1p0
消费者C2：t1p1、t2p0、t2p1、t2p2
```

**使用轮询分区策略必须满足两个条件:**

1. 每个主题的消费者实例具有相同数量的流
2. 每个消费者订阅的主题必须是相同的



## 3.3 分区分配触发条件

当出现以下几种情况时，kafka 会进行一次分区分配操作；也就是 kafka consumer 的 rebalance
- 同一个 consumer-group 内新增了消费者

- 消费者离开当前所属的 consumer-group ，比如主动停机或者宕机

- Topic 新增了分区（也就是分区数量发生了变化）



​		kafka-consuemr 的 rebalance 机制规定了一个 consumer-group 下的所有 consumer 如何达成一致来分配订阅 topic的每个分区。而具体如何执行分区策略，就是前面提到过的两种内置的分区策略。而 kafka 对于分配策略这块，提供了可插拔的实现方式， 也就是说，除了这两种之外，我们还可以创建自己的分配机制。



### 1) 谁来执行Rebalance 以及管理 consumer 的 group 呢？

​		Kafka提供了一个角色： coordinator 来执行对于 consumer-group 的管理 ;当 consumer-group 的第一个 consumer 启动的时候，它会去和 kafka server 确定谁是它们组的 coordinator 。之后该 group 内的所有成员都会和该 coordinator 进行协调通信。

### 2）如何确定coordinator
​			consumer-group如何确定自己的 coordinator 是谁呢？ 消费者向 kafka 集群中的任意一个 broker 发送一个`GroupCoordinatorRequest` 请求，服务端会返回 一个负载最小的 broker 节点的 id ，并将该 broker 设置为coordinator

### 3) JoinGroup的过程
​		在rebalance 之前，需要保证 coordinator 是已经确定好了的，整个 rebalance 的过程分为两个步骤， Join 和 Sync-join: 表示加入到 consumer group 中，

**第一步：Join**

​			在这一步中，所有的成员都会向 coordinator 发送 joinGroup 的请求。一旦所有成员都发送了join Group 请求，那么 coordinator 会选择一个 consumer 担任 leader 角色，并把组成员信息和订阅信息发送消费者

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4z9jk5ljlj20ni09f0u5.jpg)

- protocol_metadata: 序列化后的消费者的订阅信息
- leader_id 消费组中的消费者， coordinator 会选择一个作为 leader ，对应的就是 member _id
- member_metadata 对应消费者的订阅信息
- members:consumer group 中全部的消费者的订阅信息
- generation_id 年代信息，类似于之前讲解 zookeeper 的时候的 epoch 是一样的，对于每一轮 rebalance,
  generation _id 都会递增。主要用来保护 consumer group 。隔离无效的 offset 提交。也就是上一轮的 consumer 成员无法提交 offset 到新的 consumer group 中。

**第二步：sysn Join**

​		完成分区分配之后，就进入了Synchronizing Group State;阶段，主要逻辑是向GroupCoordinator 发送SyncGroupRequest 请求，并且处理 SyncGroupResponse响应，简单来说，就是 leader 将消费者对应的 partition 分配方案同步给 consumer group 中的所有 consumer.

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4z9negy51j20ny085gmu.jpg)



​			每个消费者都会向coordinator 发送 syncgroup 请求，不过只有 leader 节点会发送分配方案，其他消费者只是打打酱油而已。当 leader 把方案发给 coordinator 以后，coordinator 会把结果设置到 S yncGroupResponse 中。这样所有成员都知道自己应该消费哪个分区。



➢ **consumer group 的分区分配方案是在客户端执行的**！Kafka 将 这个权利下放给客户端主要是因为这样做可以
有更好的灵活性



# 4. Offset

​			前面在讲解partition 的时候，提到过 offset 每个 topic可以划分多个分区（每个Topic 至少有一个分区），同一topic 下的不同分区包含的消息是不同的。每个消息在被添加到分区时，都会被分配一个 offset （称之为偏移量），它是消息在此分区中的唯一编号， kafka 通过 offset 保证消息在分区内的顺序， offset 的顺序不跨分区，即 kafka 只保证在同一个分区内的消息是有序的 对于应用层的消费来说，每次消费一个消息并且提交以后，会保存当前消费到的最近的一个 offset 。那么 offset 保存在哪里（指的是消费消费后的）？



## 4.1 消费者消费的Offset维护

​		在kafka 中，提供了一个 _ __consumer_offsets-*的一个topic ，把 offset 信息写入到这个 topic 中。___consumer_offsets 按保存了每个 consumer group某一时刻提交的 offset 信息。 __consumer_offsets 默认**有50**个分区。



例如我们案例，有如下这个group-id

```java
properties.put(ConsumerConfig.GROUP_ID_CONFIG,"test");
```

- 1.获取分区值

  ```java
  Math.abs(“test”.hashCode())%groupMetadataTopicPartitionCount ; 
  由于默认情况下group的 MetadataTopicPartitionCount 有50个分区，
  
  计算得到的结果为 48 , 意味着当前的 consumer _group 的位移信息保存在 ___consumer_offsets-48 分区
  ```

  

- 2.通过命令查看当前consumer-group 的相关offset信息

  ```bash
  sh kafka-simple-consumer-shell.sh --topic __consumer_offsets --partition 48 --broker-list 192.168.124.159:9092,192.168.124.160:9092,192.168.124.161:9092 --formatter
  "kafka.coordinator.group.GroupMetadataManager\$OffsetsMessageFormatter"
  ```

  