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



### 1) 执行Rebalance 以及管理 consumer 

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
  sh kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group test --offsets
  ```
  



# 5. 消息的存储

## 5.1 消息的保存路径
​		消息发送端发送消息到broker 上以后，消息是如何持久化的呢？那么接下来去分析下消息的存储。

​		首先我们需要了解的是，kafka 是使用日志文件的方式来保存生产者和发送者的消息，每条消息都有一个 offset 值来表示它在分区中的偏移量。 Kafka 中存储的一般都是海量的消息数 据，为了避免日志文件过大， Log 并不是直接对应在一个磁盘上的日志文件，而是对应磁盘上的一个目录，这个目录的明明规则是topic_name>_<partition_id>

​		比如创建一个名为firstTopic 的 topic ，其中有 3 个 partition那么在 kafka 的数据目录（ （/tmp/kafka log ）中就有 3 个目录， firstTopic 0~3

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4zsk09nqxj20mf0avaba.jpg)



## 5.2 多个分区在集群中的分配
如果我们对于一个topic ，在集群中创建多个 partition ，那么 partition 是如何分布的呢？

- 1.将所有 N Broker 和 待分配的 i 个 Partition 排序

- 2.将第 i 个 Partition 分配到第 (i mod  Broker ）

  ​	

  上了解到这里的时候，大家再结合前面讲的消息分发策略，就应该能明白消息发送到 broker 上，消息会保存到哪个分区中，并且消费端应该消费哪些分区的数据了。

## 5.3 消息写入的性能

​			我们现在大部分企业仍然用的是机械结构的磁盘，如果把消息以随机的方式写入到磁盘，那么磁盘首先要做的就是寻址，也就是定位到数据所在的物理地址，在磁盘上就要找到对应的柱面、磁头以及对应的扇区；这个过程相对内存来说会消耗大量时间，为了规避随机读写带来的时间消耗， kafka 采用顺序写的方式存储数据。即使是这样，但是频繁的 I/O 操作仍然会造成磁盘的性能瓶颈，所以 kafka还有一个性能策略

### 1)零拷贝

​		消息从发送到落地保存，broker 维护的消息日志本身就是文件目录，每个文件都是二进制保存，生产者和消费者使用相同的格式来处理 。在消费者获取消息时，服务器先从硬盘读取数据到内存，然后把内存中的数据原封 不动 的通过 socket 发送给消费者。虽然这个操作描述起来很简单，但实际上经历了很多步骤 ；如下：



![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4zsk5fj9yj20jt0f6jsp.jpg)



- 1.操作系统将数据从磁盘读入到内核空间的页缓存
- 2.应用程序将数据从内核空间读入到用户空间缓存中
- 3.应用程序将数据写回到内核空间到 socket 缓存中
- 4.操作系统将数据从 socket 缓冲区复制到网卡缓冲区，以便将数据经网络发出



​		这个过程涉及到4 次上下文切换以及 4 次数据复制，并且有两次复制操作是由CPU 完成。但是这个过程中，数据完全没有进行变化，仅仅是从磁盘复制到网卡缓冲区。

​		通过“零拷贝”技术，可以去掉这些没必要的数据复制操作，同时也会减少上下文切换次数。现代的 unix 操作系统提供一个优化的代码路径，用于将数据从页缓存传输到 socket

​		在 Linux 中，是通过 sendfile 系统调用来完成的。 Java 提供了访问这个系统调用的方法： FileChannel.transferTo API使用

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4zskqhhogj20jl0exwfo.jpg)



​			使用sendfile ，只需要一次拷贝就行，允许操作系统 将数据直接从页缓存发送到网络上。所以在这个优化的路径中，只有最后一步将数据拷贝到网卡缓存中是需要的。

### 2) 页缓存

​	页缓存是操作系统实现的一种主要的磁盘缓存，但凡设计到缓存的，基本都是为了提升i/o性能，所以页缓存是用来减少磁盘I/O操作的。

磁盘高速缓存有两个重要因素：

- 第一，访问磁盘的速度要远低于访问内存的速度，若从处理器L1和L2高速缓存访问则速度更快。
- 第二，数据一旦被访问，就很有可能短时间内再次访问。正是由于基于访问内存比磁盘快的多，所以磁盘的内存缓存将给系统存储性能带来质的飞越。

​	当 一 个进程准备读取磁盘上的文件内容时， 操作系统会先查看待读取的数据所在的页(page)是否在页缓存(pagecache)中，如果存在（命中）则直接返回数据， 从而避免了对物理磁盘的I/0操作；如果没有命中， 则操作系统会向磁盘发起读取请求并将读取的数据页存入页缓存， 之后再将数据返回给进程。
同样，如果 一 个进程需要将数据写入磁盘， 那么操作系统也会检测数据对应的页是否在页缓存中，如果不存在， 则会先在页缓存中添加相应的页， 最后将数据写入对应的页。 被修改过后的页也就变成了脏页， 操作系统会在合适的时间把脏页中的数据写入磁盘， 以保持数据的 一 致性

​	Kafka中大量使用了页缓存， 这是Kafka实现高吞吐的重要因素之 一 。 虽然消息都是先被写入页缓存，
然后由操作系统负责具体的刷盘任务的， 但在Kafka中同样提供了同步刷盘及间断性强制刷盘(fsync),
可以通过 `log.flush.interval.messages` 和 `log.flush.interval.ms` 参数来控制。

​	同步刷盘能够保证消息的可靠性，避免因为宕机导致页缓存数据还未完成同步时造成的数据丢失。但是
实际使用上，我们没必要去考虑这样的因素以及这种问题带来的损失，消息可靠性可以由多副本来解
决，同步刷盘会带来性能的影响。 刷盘的操作由操作系统去完成即可

# 6.消息的文件存储机制

​	前面我们知道了一个topic 的多个 partition 在物理磁盘上的保存路径，那么我们再来分析日志的存储方式。通过如下命令找到对应 partition 下的日志内容 `/tmp/kafka-logs/foo-0`

```bash
00000000000000000000.index  00000000000000000000.log  00000000000000000000.timeindex  leader-epoch-checkpoint
```

​		

​		kafka是通过分段的方式将 Log 分为多个 LogSegment；LogSegment 是一个逻辑上的概念，一个 LogSegment 对应磁盘上的一个日志文件和一个索引文件，其中日志文件是用来记录消息的。索引文件是用来保存消息的索引。

## 6.1 Log Segment

​		假设kafka 以 partition 为最小存储单位，那么我们可 以想象当 kafka producer 不断发送消息，必然会引起 partition文件的无线扩张，这样对于消息文件的维护以及被消费的消息的清理带来非常大的挑战，所以 kafka 以 segment 为单位又把 partition 进行细分。每个 partition 相当于一个巨型文件被平均分配到多个大小相等的 segment 数据文件中（每个 segment 文件中的消息不一定相等），这种特性方便已经被消费的消息的清理，提高磁盘的利用率。

➢<font color="red"> log.segment.bytes=107370 </font>设置分段大小 默认是1gb ，我们把这个值调小以后，可以看到日志分段的效果
⚫ 抽取其中 3 个分段来进行分析

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g50uh4rnqxj20qy0a0my2.jpg)

​		segment file 由 2 大部分组成，分别为 index file 和 data file ，此 2 个文件一一对应，成对出现，后缀 ". 和 “.分别表示为 segment 索引文件、数据文件

​		segment文件命名规则： partition 全局的第一个 segment；从 0 开始，后续每个 segment 文件名为上一个 segment文件最后一条消息的 offset 值 进行递增 。数值最大为 64 位long 大小， 20 位数字字符长度，没有数字用 0 填充。



### 6.1.1 查看segment 文件命名规则
➢ 通过下面这条命令可以看到 kafka 消息日志的内容

```bash
sh kafka-run-class.sh  kafka.tools.DumpLogSegments --files /tmp/kafka-logs/foo-0/00000000000000000000.log --print-data-log
```

输出结果为:

```xml
offset: 5376 CreateTime: 1563072502465 keysize: 2 valuesize: 2 sequence: -1 headerKeys: [] key: 99 payload: 99
```

​	第一个log 文件的最后一个 offset 为 5376, 所以下一个segment 的文件命名为 00000000000000005376.log 。对应的 index 为 00000000000000005376.index

### 6.1.2 segment 中 index 和 log 的对应关系
​		从所有分段中，找一个分段进行分析为了提高查找消息的性能，为每一个日志文件添加2 个索引索引文件： OffsetIndex 和 TimeIndex ，分别对应` *.index`以及 `*.timeindex` ，TimeIndex 索引文件格式：它是映射时间戳和相对 offset。通过如下方式获取索引的内容

```bash
sh kafka-run-class.sh  kafka.tools.DumpLogSegments --files /tmp/kafka-logs/foo-0/00000000000000000000.index --print-data-log
```

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g50uonl2qfj20r90hk42o.jpg)

​		如图所示，index 中存储了索引以及物理偏移量。 log 存储了消息的内容。索引文件的元数据执行对应数据文件中message 的物理偏移地址。举个简单的案例来说，以[4053,80899] 为例，在 log 文件中，对应的是第 4053 条记录，物理偏移量（ position ）为 80899. position 是ByteBuffer 的指针位置。



### 6.1.3 在partition 中通过 offset查找message
**查找的算法是**：

1. 根据 offset 的值，查找 segment 段中的 index 索引文件。由于索引文件命名是以上一个文件的最后一个
offset 进行命名的，所以，使用二分查找算法能够根据offset 快速定位到指定的索引文件。

2. 找到索引文件后，根据 offset 进行定位，找到索引文件中的符合范围的索引。（ kafka 采用稀疏索引的方式来提高查找性能）
3. 得到 position 以后，再到对应的 log 文件中，从 position出开始查找 offset 对应的消息，将每条消息的 offset 与目标 offset 进行比较，直到找到消息



### 6.1.4 Log文件的消息内容分析
​		前面我们通过kafka 提供的命令，可以查看二进制的日志文件信息，一条消息，会包含很多的字段。

```json
 offset: 99 CreateTime: 1563072502465 keysize: 2 valuesize: 2 sequence: -1 headerKeys: [] key: 99 payload: 99
//offset表示偏移量
keysize和valuesize表示key和value的大小
payload:表示消息体
```



## 6.2 日志的清除策略以及压缩策略

### 6.2.1 日志清除策略

​		前面提到过，日志的分段存储，一方面能够减少单个文件内容的大小，另一方面方便kafka 进行日志清理。日志的清理策略有两个
1. 根据消息的保留时间，当消息在 kafka 中保存的时间超过了指定的时间，就会触发清理过程
2. 根据 topic 存储的数据大小，当 topic 所占的日志文件大小大于一定的阀值，则可以开始删除最旧的消息。 kafka会启动一个后台线程，定期检查是否存在可以删除的消息。

```bash
通过log.retention.bytes 和log.retention.hours 这两个参数来设置，当其中任意一个达到要求，都会执行删除 。默认的保留时间是：7天
```



### 6.2.2 日志压缩策略

​		Kafka 还提供了“日志压缩（ Log Compaction ）”功能，通过这个功能可以有效的减少日志文件的大小，缓解磁盘紧张的情况，在很多实际场景中，消息的 key 和 value 的值之间的对应关系是不断变化的，就像数据库 中的数据会不断被修改一样，消费者只关心 key 对应的最新的 value 。 因此，我们可以开启 kafka 的日志压缩功能，服务端 会在后台启动启动 Cleaner 线程池，定期将相同的 key 进行合并，只保留最新的 value 值。日志的压缩原理是

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g50uya07kjj20ru0c076d.jpg)

## 6.3 partition的高可用副本机制
​		我们已经知道Kafka 的每个 topic 都可以分为多个 Partition并且多个 partition 会均匀分布在集群的各个节点下。虽然这种方式能够有效的对数据进行分片，但是对于每个partition 来说，都是单点的，当其中一个 partition 不可用的时候，那么这部分消息就没办法消费。所以 kafka 为了提高partition 的可靠性而提供了副本的概念（Replica)通过副本机制来实现冗余备份。

​		每个分区可以有多个副本，并且在副本集合中会存在一个leader 的副本，<font color="red">**所有的读写请求都是由 leader 副本来进行处理**</font>。剩余的其他副本都做为 follower 副本， follower 副本会从 leader 副本同步消息日志。这个有点类似zookeeper 中 leader 和 follower 的概念，但是具体的实现方式还是有比较大的差异。所以我们可以认为，副本集会存在一主多从的关系。

​		一般情况下，同一个分区的多个副本会被均匀分配到集群中的不同 broker 上，当 leader 副本所在的 broker 出现故障后，可以重新选举新的 leader 副本继续对外提供服务。通过这样的副本机制来提高 kafka 集群的可用性。

### 6.3.1 副本分配算法
将所有N Broker 和待分配的 i 个 Partition 排序
将第i 个 Partition 分配到第 (i mod n)个 Broker 上
将第i 个 Partition 的第 j 个副本分配到第 ((i + j) mod n)个Broker 上

**创建一个带副本机制的topic**

​	通过下面的命令去创建带 2 个副本的 topic (sendTopic)

```bash
sh kafka-topics.sh --create --zookeeper 192.168.124.159:2181 --replication-factor 2 --partitions 3 --topic sendTopic
```

然后我们可以在tmp/kafka-logs 路径下看到对应 topic 的副本信息了。

➢ 针对sendTopic 这个 topic 的 3 个分区对应的 2 个副本；如何知道那个各个分区中对应的leader 是谁呢？

​	在zookeeper 服务器上，通过如下命令去获取对应分区的信息 比如下面这个是获取 sendTopic 第1个分区的状态信息。

```bash
[zk: localhost:2181(CONNECTED) 2] get /brokers/topics/sendTopic/partitions/1/state

{"controller_epoch":6,"leader":3,"version":1,"leader_epoch":0,"isr":[3,1]}

leader：表示当前分区的 leader是那个broker-id，其他节点就是follower
```

​		Kafka提供了数据复制算法保证，如果 leader 发生故障或挂掉，一个新 leader 被选举并被接受客户端的消息成功写入。 Kafka 确保从同步副本列表中选举一个副本为 leader

​		leader 负责维护和跟踪 ISR(in Sync replicas 副本同步队列 中所有 follower 滞后的状态。当 producer 发送一条消息到 broker 后， leader 写入消息并复制到所有 follower 。消息提交之后才被成功复制到所有的同步副本。



### 6.3.2 副本数据同步

​		需要注意的是，大家不要把zookeeper 的 leader 和follower 的同步机制和 kafka 副本的同步机制搞混了。虽然从思想层面来说是一样的，但是原理层面的实现是完全不同的。

#### 1）kafka 副本机制中的几个概念

副本根据角色的不同可分为 3 类：

- **leader副本**：响应 clients 端读写请求的副本
- **follower副本**：被动地备份 leader 副本中的数据，不能响应 clients 端读写请求。
- **ISR副本** ：包含了 leader 副本和所有与 leader 副本保持同步的 follower 副本；如何判定是否与 leader 同步后面会提到 每个 Kafka 副本对象都有两个重要的属性： **LEO 和HW** 。注意是所有的副本，而不只是 leader 副本。后文详讲



**LEO：**即日志末端位移 (log end offset)，记录了该副本底层日志 (log) 中下一条消息的位移值。注意是下一条消息！也就是说，如果 LEO=10 ，那么表示该副本保存了 10 条消息，位移值范围是 [0,9] 。另外， leader LEO 和 follower LEO 的更新是有区别的。

**HW：**即上面提到的水位值。对于同一个副本对象而言，其HW 值不会大于 LEO 值。小于等于 HW 值的所有消息都被认为是 已备份 的（ replicated ）。同理 leader 副本和follower 副本的 HW 更新是有区别的



#### 2）副本协同机制

​		刚刚提到了，消息的读写操作都只会由leader 节点来接收和处理。 follower 副本只负责同步数据以及当 leader 副本所在的 broker 挂了以后，会从 follower 副本中选取新的leader 。ISR最靠前的作为leader。



​		写请求首先由Leader 副本处理，之后 follower 副本会从leader 上**拉取**写入的消息，这个过程会有一定的延迟，导致 follower 副本中保存的消息略少于 leader 副本，但是只要没有超出阈值都可以容忍。但是如果一个 follower 副本出现异常，比如宕机、网络断开等原因长时间没有同步到消息，那这个时候， leader 就会把它踢出去； kafka 通过 ISR集合来维护一个分区副本信息。



**ISR**： 表示目前“可用且消息量与 leader 相差不多的副本集合，这是整个副本集合的一个子集”。怎么去理解可用和相差不多这两个词呢？具体来说， ISR 集合中的副本必须满足两个条件：

1. 副本所在节点必须维持着与 zookeeper 的连接

2. 副本最后一条消息的 offset 与 leader 副本的最后一条消息的 offset 之间的差值不能超过指定的阈值
    （replica.lag.time.max.ms）
    `replica.lag.time.max.ms`；如果该 follower 在此时间间隔内一直没有追上过 leader 的所有消息，则该 follower 就会被剔除 isr 列表

  

➢ ISR 数据保存在 Zookeeper 的/brokers/topics/<topic>/partitions/<partitionId>/state 节点中

```bash
比如上面看到的：
[zk: localhost:2181(CONNECTED) 2] get /brokers/topics/sendTopic/partitions/1/state

{"controller_epoch":6,"leader":3,"version":1,"leader_epoch":0,"isr":[3,1]}
//isr表示当前broker-id=3和1副本集合可以
```



**HW&LEO**

​	  关于follower 副本同步的过程中，还有两个关键的概念，HW ( 和 LEO (Log End Offset). 这两个参数跟ISR 集合紧密关联。 HW 标记了一个特殊的 offset ，<font color="red">当消费者处理消息的时候，只能拉去到 HW 之前的消息</font> HW之后的消息对消费者来说是不可见的。也就是说，取partition 对应 ISR 中最小的 LEO 作 为 HW consumer 最多只能消费到 HW 所在的位置。每个 replica 都有 HWleader 和 follower 各自维护更新自己的 HW 的状态。一条消息只有被 ISR 里的所有 Follower 都从 Leader 复制过去才会被认为已提交。这样就避免了部分数据被写进了Leader ，还没来得及被任何 Follower 复制就宕机了，而造成数据丢失（ Consumer 无法消费这些数据）。而对于Producer 而言，它可以选择是否等待消息 commit ，这可以通过 acks 来设置。这种机制确保了只要 ISR 有一个或以上的 Follower ，一条被 commit 的消息就不会丢失。

#### 3) 数据的同步过程
数据的同步过程；它需要解决
1. 怎么传播消息

2. 在向消息发送端返回 ack 之前需要保证多少个 Replica已经接收到这个消息

  

**数据的处理过程是：**

- Producer在发布消息到某个 Partition 时，先通过ZooKeeper找到该 Partition 的 Leader 【 get/brokers/ topic>topic>/partitions/2/state 】，

- 然后无论该Topic 的 Replication Factor 为多少（也即该 Partition 有多少个 Replica Producer 只将该消息发送到该 Partition 的Leader 。 Leader 会将该消息写入其本地 Log 。每个 Follower都从 Leader pull 数据。这种方式上， Follower 存储的数据顺序与 Leader 保持一致。

- Follower 在收到该消息并写入其Log 后，向 Leader 发送 ACK 。一旦 Leader 收到了 ISR 中的所有 Replica 的 ACK ，该消息就被认为已经 commit 了，Leader 将增加 HW HighWatermark 并且向 Producer 发送ACK。

##### 初始状态

​		初始状态下，leader 和 follower 的 HW 和 LEO 都是0；leader 副本会保存 remote LEO ，表示所有 follower LEO也会被初始化为 0 ，这个时候 producer 没有发送消息。follower 会不断地个 leader 发送 FETCH 请求 ，但是因为没有数据，这个请求会被 leader 寄存，当在指定的时间之后会强制 完成请求 ，这个时间配置是replica.fetch.wait.max.ms)，如果在指定时间内 producer有消息发送过来，那么 kafka 会唤醒 fetch 请求，让 leader继续处理。

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g50wpestl7j20kr08v3ys.jpg)

这里会分两种情况：

- 第一种是leader 处理完 producer 请求之后， follower 发送一个 fetch 请求过来；

- 第二种是follower 阻塞在 leader 指定时间之内， leader 副本收到producer 的请求。

这两种情况下处理方式是不一样的。先来看第一种情况。

##### 方式一：follower的 fetch 请求是当 leader 处理消息以后执行的

步骤一：生产者发送一条消息
➢ leader 处理完 producer 请求之后， follower 发送一个fetch 请求过来 。状态图如下

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g50x10e9i2j20n00be74r.jpg)



步骤二：leader副本收到请求以后，会做几件事情

1. 把消息追加到log文件，同时更新 leader 副本的 LEO
2. 尝试更新 leader HW 值。这个时候由于 follower 副本还没有 发送 fetch 请求，那么 leader 的 remote LEO 仍然是 0 。 leader会比较自己的 LEO 以及 remote LEO 的值发现最小值是 0 ，与 HW 的值相同，所以不会更新HW。

**follower fetch 消息**

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g50x30rzwhj20jd0crdgd.jpg)

步骤三：follower发送 fetch 请求， leader 副本的处理逻辑是
1. 读取 log 数据、更新 remote LEO=0( follower 还没有写入这条消息，这个值是根据 follower 的 fetch 请求中的offset 来确定的

2. 尝试更新 HW ，因为这个时候 LEO 和 remoteLEO 还是不一致，所以仍然是 HW=0

3. 把消息内容和当前分区的 HW 值发送给 follower 副本follower副本收到 response 以后

  - 将消息写入到本地 log ，同时更新 follower 的 LEO

  - 更新 follower HW 本地的 LEO 和 leader 返回的 H W进行比较取小的值，所以仍然是 0
    第一次交互结束以后，HW 仍然还是 0 ，这个值会在下一次follower 发起 fetch 请求时 被更新

![](http://ww1.sinaimg.cn/mw690/b8a27c2fgy1g50x9biobdj20kc0k8gmn.jpg)

步骤四：follower发第二次 fetch 请求， leader 收到请求以后
1. 读取 log 数据
2. 更新 remote LEO 1 因为这次 fetch 携带的 offset 是1
3. 更新当前分区的 HW ，这个时候 leader LEO 和 remoteLEO 都是 1 ，所以 HW 的值也更新为 1
4. 把数据和当前分区的 HW 值返回给 follower 副本 ，这个时候如果没有数据，则返回为空



follower副本收到 response 以后
1. 如果有数据则写本地日志，并且更新 LEO
2. 更新 follower 的 HW 值到目前为止，数据的同步就完成了，意味着消费端能够消费 offset=0 这条消息



##### 方式二：follower的fetch请求是直接从 阻塞过程中触发
​		前面说过，由于leader 副本暂时没有数据过来，所以follower 的 fetch 会被阻塞，直到等待超时或者 leader 接收到新的数据。当 leader 收到请求以后会唤醒处于阻塞的fetch 请求。 处理过程基本上和前面说的一致
1. leader 将消息写入本地日志，更新 Leader 的 LEO
2. 唤醒 follower 的 fetch 请求
3. 更新 HW



​		kafka使用 HW 和 LEO 的方式来实现副本数据的同步，本身是一个好的设计，但是在这个地方会存在一个数据丢失的问题，当然这个丢失只出现在特定的背景下。我们回想一下， HW 的值是在新的一轮 FETCH 中才会被更新。我们分析下这个过程为什么会出现数据丢失



#### 4）数据丢失的问题

前提：
		min.insync.replicas=1 的时候。 设定 ISR 中的最小副本数是多少，默认值为 1 当且仅当 acks 参数设置为 1表示 需要所有副本确认） 时，此参数才生效 表达的含义是，至少需要多少个副本同步 才能表示消息是提交的
所以，当min.insync.replicas=1 的时候一旦消息被写入leader 端 log 即被认为是 已提交 ””，而延迟一轮 FETCH RPC 更新 HW 值的设计使得 follower HW值是异步延迟更新的，倘若在这个过程中 leader 发生变更，那么成为新 leader 的 follower 的 HW 值就有可能是过期的，使得 clients 端认为是成功提交的消息被删除。

![](http://ww1.sinaimg.cn/mw690/b8a27c2fgy1g50xis8fkuj20mp0e5ju3.jpg)

**数据丢失的解决方案**

​		在kafka 0.11.0.0 版本以后，提供了一个新的解决方案， 使用 leader epoch 来解决这个问题 leader epoch 实际上是一对之 (epoch,offset) , epoch 表示 leader 的版本号，从 0开始，当 leader 变更过 1 次时 epoch 就会 +1 ，而 offset 则对应于该 epoch 版本的 leader 写入第一条消息的位移 。

​	比如说(0,0) ; (1,50); 表示第一个 leader 从 offset= 0 开始写消息，一共写了50条，第二个 leader 版本号是 1 ，从 50 条处开始写消息。 这个信息保存在对应分区的本地磁盘文件中，
文件名为： `tmp/kafka-log/leader-epoch-checkpoint` 

​		leader broker中会保存这样的一个缓存，并定期地写入到一个 checkpoint 文件中。当leader 写 log 时它会尝试更新整个缓存 如果这个leader 首次写消息，则会在缓存中增加一个条目；否则就不做更新。而每次副本重新成为 leader 时会查询这部分缓存，获取出对应 leader 版本的 offset

![](http://ww1.sinaimg.cn/mw690/b8a27c2fgy1g50xkypv1kj20lq0dvaci.jpg)

如何处理所有的Replica 不工作的情况
		在ISR 中至少有一个 follower 时， Kafka 可以确保已经commit 的数据不丢失，但如果某个 Partition 的所有Replica 都宕机了，就无法保证数据不丢失了

1. 等待 ISR 中的任一个 Replica“ 活 过来，并且选它作为Leader

2. 选择第一个“活”过来的 Replica （不一定是 ISR 中的）作为 Leader

这就需要在可用性和一致性当中作出一个简单的折衷。
如果一定要等待ISR 中的 Replica“ 活 过来，那不可用的时间就可能会相对较长。而且如果 ISR 中的所有 Replica 都无法 活 过来了，或者数据 都丢失了，这个 Partition 将永远不可用。
选择第一个活 过来的 Replica 作为 Leader ，而这个Replica 不是 ISR 中的 Replica ，那即使它并不保证已经包含了所有已 commit 的消息，它也会成为 Leader 而作为consumer 的数据源（前文有说明，所有读写都由 Leader完成）。 在我们讲的版本中，使用的是第一种策略。

#### 5) ISR 的设计原理

​			在所有的分布式存储中，冗余备份是一种常见的设计方式，而常用的模式有同步复制和异步复制，按照 kafka 这个副本模型来说如果采用同步复制，那么需要要求 所有能工作的 Follower 副本都复制完，这条消息才会被认为提交成功，一旦有一个follower 副本出现故障，就会导致 HW 无法完成递增，消息就无法提交，消费者就获取不到消息。这种情况下，故障的Follower 副本会拖慢整个系统的性能，设置导致系统不可用
如果采用异步复制leader 副本收到生产者推送的消息后，就认为次消息提交成功。 follower 副本则异步从 leader 副本同步。这种设计虽然避免了同步复制的问题，但是假设所有follower 副本的同步速度都比较慢他们保存的消息量远远落后于 leader 副本。 而此时 leader 副本所在的 broker 突然宕机，则会重新选举新的 leader 副本，而新的 leader 副本中没有原来leader 副本的消息。这就出现了消息的丢失。

​	kafka权衡了同步和异步的两种策略，采用 ISR 集合，巧妙解决了两种方案的缺陷：当 follower 副本延迟过高， leader 副本则会把该 follower 副本剔除 ISR 集合，消息依然可以快速提交。当 leader 副本所在的 broker 突然宕机，会优先将 ISR 集合中follower 副本选举为 leader ，新 leader 副本包含了 HW 之前的全部消息，这样 就避免了消息的丢失。





# 7.leader的选举

1. KafkaController会监听ZooKeeper的/brokers/ids节点路径，一旦发现有broker挂了，执行下面的逻辑。这里暂时先不考虑KafkaController所在broker挂了的情况，KafkaController挂了，各个broker会重新leader选举出新的KafkaController
2. leader副本在该broker上的分区就要重新进行leader选举，目前的选举策略是
  - a) 优先从isr列表中选出第一个作为leader副本，这个叫优先副本，理想情况下有限副本就是该分区的leader副本
  - b) 如果isr列表为空，则查看该topic的`unclean.leader.election.enable`配置。
    `unclean.leader.election.enable`：为true则代表允许选用非isr列表的副本作为leader，那么此时就意味着数据可能丢失，为false的话，则表示不允许，直接抛出NoReplicaOnlineException异常，造成leader副本选举失败。
  - c) 如果上述配置为true，则从其他副本中选出一个作为leader副本，并且isr列表只包含该leader
    副本。一旦选举成功，则将选举后的leader和isr和其他副本信息写入到该分区的对应的zk路径上。