# 1. 常用的性能调优参数

![](http://ww1.sinaimg.cn/mw690/b8a27c2fgy1g63wnw3l9nj20ne0lm433.jpg)

# 2. 各个参数的作用

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g63woxdco0j20n306qjrm.jpg)

1、当consumer发起一个请求时，首先经过active limit(参数actives）进行方法级别的限制，其实现方式为CHM中存放计数器(AtomicInteger)，请求时加1，请求完成（包括异常）减1,如果超过actives则等待有其他请求完成后重试或者超时后失败；

2、从多个连接(connections）中选择一个连接发送数据，对于默认的netty实现来说，由于可以复用连接，默认一个连接就可以。不过如果你在压测，且只有一个consumer,一个provider，此时适当的加大connections确实能够增强网络传输能力。但线上业务由于有多个consumer多个provider，因此不建议增加connections参数；

3、连接到达provider时（如dubbo的初次连接），首先会判断总连接数是否超限（acceps），超过限制连接将被拒绝；

4、连接成功后，具体的请求交给io thread处理。io threads虽然是处理数据的读写，但io部分为异步，更多的消耗的是cpu，因此iothreads默认cpu个数+1是比较合理的设置，不建议调整此参数;

5、数据读取并反序列化以后，交给业务线程池处理，默认情况下线程池为fixed，且排队队列为0(queues)，这种情况下，最大并发等于业务线程池大小(threads)，如果希望有请求的堆积能力，可以调整queues参数。如果希望快速失败由其他节点处理（官方推荐方式），则不修改queues，只调整threads;

6、execute limit（参数executes）是方法级别的并发限制，原理与actives类似，只是少了等待的过程，即受限后立即失败

