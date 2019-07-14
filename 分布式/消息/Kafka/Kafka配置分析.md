



# 1. 发送端配置

## 1.1 acks

​		acks配置表示producer发送消息到broker上以后的确认值。有三个可选项

- 0：表示producer不需要等待broker的消息确认。这个选项时延最小但同时风险最大（因为当server宕机时，数据将会丢失）。
- 1：表示producer只需要获得kafka集群中的leader节点确认即可，这个选择时延较小同时确保了leader节点确认接收成功。
-  all(-1)：需要ISR中所有的Replica给予接收确认，速度最慢，安全性最高，但是由于ISR可能会缩小到仅包含一个Replica，所以设置参数为all并不能一定避免数据丢失，

## 1.2 batch.size

​		生产者发送多个消息到broker上的同一个分区时，为了减少网络请求带来的性能开销，通过批量的方式来提交消息，可以通过这个参数来控制批量提交的字节数大小，默认大小是16384byte,也就是16kb，意味着当一批消息大小达到指定的batch.size的时候会统一发送

## 1.3 linger.ms

​		Producer默认会把两次发送时间间隔内收集到的所有Requests进行一次聚合然后再发送，以此提高吞吐量，而linger.ms就是为每次发送到broker的请求增加一些delay，以此来聚合更多的Message请求。 这个有点想TCP里面的Nagle算法，在TCP协议的传输中，为了减少大量小数据包的发送，采用了Nagle算法，也就是基于小包的等-停协议。

## 1.4  batch.size和linger.ms

 **batch.size和linger.ms**这两个参数是kafka性能优化的关键参数，很多同学会发现batch.size和linger.ms这两者的作用是一样的，如果两个都配置了，那么怎么工作的呢？实际上，当二者都配置的时候，只要满足其中一个要求，就会发送请求到broker上

## 1.5 max.request.size
​		设置请求的数据的最大字节数，为了防止发生较大的数据包影响到吞吐量，默认值为1MB。



# 2.接收端配置

## 2.1 group.id

​		consumer group是kafka提供的可扩展且具有容错性的消费者机制。既然是一个组，那么组内必然可以有多个消费者或消费者实例(consumer instance)，它们共享一个公共的ID，即group ID。组内的所有消费者协调在一起来消费订阅主题(subscribed topics)的所有分区(partition)。当然，每个分区只能由同一个消费组内的一个consumer来消费.如下图所示，分别有三个消费者，属于两个不同的group，那么对于firstTopic这个topic来说，这两个组的消费者都能同时消费这个topic中的消息，对于此事的架构来说，这个firstTopic就类似于ActiveMQ中的topic概念。如图所示，如果3个消费者都属于同一个group，那么此时firstTopic就是一个Queue的概念

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4xew3abwyj20le09c405.jpg)

## 2.2 enable.auto.commit

​		消费者消费消息以后自动提交，只有当消息提交以后，该消息才不会被再次接收到，还可以配合auto.commit.interval.ms控制自动提交的频率。当然，我们也可以通过consumer.commitSync()的方式实现手动提交



## 2.3 auto.offset.reset

​		这个参数是针对新的groupid中的消费者而言的，当有新groupid的消费者来消费指定的topic时，对于该参数的配置，会有不同的语义

- auto.offset.reset=latest情况下，新的消费者将会从其他消费者最后消费的offset处开始消费Topic下的消息
- auto.offset.reset= earliest情况下，新的消费者会从该topic最早的消息开始消费
- auto.offset.reset=none情况下，新的消费者加入以后，由于之前不存在offset，则会直接抛出异常。



## 2.3 max.poll.records

​		此设置限制每次调用poll返回的消息数，这样可以更容易的预测每次poll间隔要处理的最大值。通过调整此值，可以减少poll间隔。