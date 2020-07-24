# 1.Agent原理

![U7IYNT.png](https://s1.ax1x.com/2020/07/22/U7IYNT.png)

**重要组件：**

**1）ChannelSelector**

​		ChannelSelector的作用就是选出Event将要被发往哪个Channel。其共有两种类型，分别是Replicating（复制）和Multiplexing（多路复用）。

- ReplicatingSelector会将同一个Event发往所有的Channel

- Multiplexing会根据相应的原则，将不同的Event发往不同的Channel

**2）SinkProcessor**

SinkProcessor共有三种类型，分别是DefaultSinkProcessor、LoadBalancingSinkProcessor和FailoverSinkProcessor

DefaultSinkProcessor对应的是单个的Sink，LoadBalancingSinkProcessor和FailoverSinkProcessor对应的是Sink Group，LoadBalancingSinkProcessor可以实现负载均衡的功能，FailoverSinkProcessor可以实现故障转移的功能。



# 2. Flume的拓扑结构

## 2.1 简单串联

​		这种模式是将多个flume顺序连接起来了，从最初的source开始到最终sink传送的目的存储系统。此模式不建议桥接过多的flume数量， flume数量过多不仅会影响传输速率，而且一旦传输过程中某个节点flume宕机，会影响整个传输系统。

![U7ok24.png](https://s1.ax1x.com/2020/07/22/U7ok24.png)

​	如果AgentA需要将Event对象发送到其他的agent进程中！AgentA的sink，必须为AvroSink,其他的agent在接收时，必须选择AvroSource！

```protobuf
常用组件
①avrosource：  监听一个avro的端口，从另一个avro客户端接受event!
	
	必须配置：
	type	–	The component type name, needs to be avro
	bind	–	hostname or IP address to listen on
	port	–	Port # to bind to
	
②avrosink： 将event转为avro格式的event，发送给指定的主机和端口
	必须配置：
	type	–	The component type name, needs to be avro.
	hostname	–	The hostname or IP address to bind to.
	port	–	The port # to listen on.
```

案例一：  在hadoop101，agent1:   netcatsource---memorychannel--arvosink
		             ----> hadoop102，agent2:    avrosource----memorychannel--loggersink

agent1的sink是agent2的source。

```properties
#agent1
#a1是agent的名称，a1中定义了一个叫r1的source，如果有多个，使用空格间隔
a1.sources = r1
a1.sinks = k1
a1.channels = c1
#组名名.属性名=属性值
a1.sources.r1.type=netcat
a1.sources.r1.bind=hadoop101
a1.sources.r1.port=44444

#定义sink
a1.sinks.k1.type=avro
a1.sinks.k1.hostname=hadoop102
a1.sinks.k1.port=33333
#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1

--------------------------------------------------------
#agent2
#a1是agent的名称，a1中定义了一个叫r1的source，如果有多个，使用空格间隔
a1.sources = r1
a1.sinks = k1
a1.channels = c1
#组名名.属性名=属性值
a1.sources.r1.type=avro
a1.sources.r1.bind=hadoop102
a1.sources.r1.port=33333

#定义sink
a1.sinks.k1.type=logger

#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1
```

## 2.2 ChannelSelector

​		Flume支持将事件流向一个或者多个目的地。这种模式可以将相同数据复制到多个channel中，或者将不同数据分发到不同的channel中，sink可以选择传送到不同的目的地。



### 1） 复制 Replicating Channel Selector

比如案例：使用Flume-1监控文件变动，Flume-1将变动内容传递给Flume-2，Flume-2负责存储到HDFS。同时Flume-1将变动内容传递给Flume-3，Flume-3负责输出到Local FileSystem。

![U7TPeI.png](https://s1.ax1x.com/2020/07/22/U7TPeI.png)



Channel Selector

1.Replicating Channel Selector
		复制的channel选择器，也是默认的选择器！当一个source使用此选择器选择多个channel时，
		source会将event在每个channel都复制一份！

		optional(可选的)channel: 向可选的channel写入event时，即便发生异常，也会忽略！

2. File Roll Sink
		存储event到本地文件系统！
	
	```
		必需配置：
		type	–	The component type name, needs to be file_roll.
		sink.directory	–	The directory where files will be stored
	```
	
3. 案例解析

      ```
      (execsource----memory channel1----avrosink1)------(arvosource----memory channel----loggersink)
      		   ----memory channel2----avrosink2)------(arvosource----memory channel----filerollsink)
      ```

      这里使用loggersink代替hdfs;使用了三台机器hadoop101，hadoop102，hadoop103

```properties
 # agent1:  在hadoop102
#a1是agent的名称，a1中定义了一个叫r1的source，如果有多个，使用空格间隔
a1.sources = r1
a1.sinks = k1 k2
a1.channels = c1 c2 
#组名名.属性名=属性值
a1.sources.r1.type=exec
a1.sources.r1.command=tail -f /usr/local/flume/test
#声明r1的channel选择器
a1.sources.r1.selector.type = replicating

#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

a1.channels.c2.type=memory
a1.channels.c2.capacity=1000

##定义sink
a1.sinks.k1.type=avro
a1.sinks.k1.hostname=hadoop101
a1.sinks.k1.port=33333

a1.sinks.k2.type=avro
a1.sinks.k2.hostname=hadoop103
a1.sinks.k2.port=33333

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1 c2
a1.sinks.k1.channel=c1
a1.sinks.k2.channel=c2

-------------------------------------------------------
# logger输出文件

a1.sources = r1
a1.sinks = k1
a1.channels = c1
#组名名.属性名=属性值
a1.sources.r1.type=avro
a1.sources.r1.bind=hadoop101
a1.sources.r1.port=33333


#定义sink
a1.sinks.k1.type=logger

#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1

-------------------------------------------------------
#写到本地文件file_roll

a1.sources = r1
a1.sinks = k1
a1.channels = c1
#组名名.属性名=属性值
a1.sources.r1.type=avro
a1.sources.r1.bind=hadoop103
a1.sources.r1.port=33333

#定义sink
a1.sinks.k1.type=file_roll
a1.sinks.k1.sink.directory=/usr/local/flume/test


#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1
```



### 2）多路复用 Multiplexing Channel Selector

这个可以根据event请 求头中的配置信息，然后发送到不同的channel中。需要使用到Multiplexing Channel Selector

官网例子：

```properties
#Multiplexing Channel Selector根据evnet header中属性，参考用户自己配置的映射信息，将event发送到指定的channel!

a1.sources = r1
a1.channels = c1 c2 c3 c4
a1.sources.r1.selector.type = multiplexing
a1.sources.r1.selector.header = state
a1.sources.r1.selector.mapping.CZ = c1
a1.sources.r1.selector.mapping.US = c2 c3
a1.sources.r1.selector.default = c4

#r1中每个event根据header中key为state的值，进行选择：
# 	如果state=CZ,这类event发送到c1，
#	如果state=US,这类event发送到c2，c3,
# 	如果state=其他，发送到c4
```

比如设置：



```properties
#agent1:  在hadoop102
#a1是agent的名称，a1中定义了一个叫r1的source，如果有多个，使用空格间隔
a1.sources = r1
a1.sinks = k1 k2
a1.channels = c1 c2 
#组名名.属性名=属性值
a1.sources.r1.type=exec
a1.sources.r1.command=tail -f /usr/local/flume/test
#声明r1的channel选择器
a1.sources.r1.selector.type = multiplexing
a1.sources.r1.selector.header = state
# state = CZ 选择c1 channel，US选择 c2
a1.sources.r1.selector.mapping.CZ = c1   
a1.sources.r1.selector.mapping.US = c2

#使用拦截器为event加上某个header，这里写死了拦截器，type为static，state=CZ,所以这里会全部发往c1
a1.sources.r1.interceptors = i1
a1.sources.r1.interceptors.i1.type = static
a1.sources.r1.interceptors.i1.key = state
a1.sources.r1.interceptors.i1.value = CZ


#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

a1.channels.c2.type=memory
a1.channels.c2.capacity=1000

##定义sink
a1.sinks.k1.type=avro
a1.sinks.k1.hostname=hadoop101
a1.sinks.k1.port=33333

a1.sinks.k2.type=avro
a1.sinks.k2.hostname=hadoop103
a1.sinks.k2.port=33333

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1 c2
a1.sinks.k1.channel=c1
a1.sinks.k2.channel=c2

-------------------------------------------------------
#avro-logger1.conf

a1.sources = r1
a1.sinks = k1
a1.channels = c1
#组名名.属性名=属性值
a1.sources.r1.type=avro
a1.sources.r1.bind=hadoop101
a1.sources.r1.port=33333


#定义sink
a1.sinks.k1.type=logger

#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1

-------------------------------------------------------
#avro-logger2.conf

a1.sources = r1
a1.sinks = k1
a1.channels = c1
#组名名.属性名=属性值
a1.sources.r1.type=avro
a1.sources.r1.bind=hadoop103
a1.sources.r1.port=33333

#定义sink
a1.sinks.k1.type=logger


#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1
```



## 2.3 SinkProcessor 

​		Flume支持使用将多个sink逻辑上分到一个sink组，sink组配合不同的SinkProcessor可以实现负载均衡和错误恢复的功能。

SinkProcessor有三个：Deault，Failover和Loadbalance。

### 1）Default Sink Processor
​		如果agent中，只有一个sink，默认就使用Default Sink Processor，这个sink processor是不强制用户，
​		将sink组成一个组！
​		如果有多个sink，多个sink对接一个channel，不能选择Default Sink Processor



### 2）Failover Sink Processor
​		Failover Sink Processor维护了一个多个sink的有优先级的列表，按照优先级保证，至少有一个sink是可以干活的！
​		如果根据优先级发现，优先级高的sink故障了，故障的sink会被转移到一个故障的池中冷却！
​		在冷却时，故障的sink也会不管尝试发送event，一旦发送成功，此时会将故障的sink再移动到存活的池中！

**必需配置：**

```

	sinks 		– Space-separated list of sinks that are participating in the group 
	processor.type 			default The component type name, needs to be failover 
	processor.priority.<sinkName> 		– Priority value. <sinkName> must be one of the sink instances associated with the current sink group A higher priority value Sink gets activated earlier. A larger absolute value indicates higher priority 
```

案例：  
		agent1:   execsource--memorychannel----avrosink1--------agent2: avroSource---memorychannel----loggersink
											                                 ----avrosink2--------agent3: avroSource---memorychannel----loggersink

avrosink1的优先级高，优先被Failover Sink Processor选中，此时只有agent2可以输出event！
一旦 agent2挂掉，此时avrosink1故障，由Failover Sink Processor选择剩下的avrosink2干活！									

 配置：

```properties
-----------------------hadoop102--agent1------------------
#a1是agent的名称，a1中定义了一个叫r1的source，如果有多个，使用空格间隔
a1.sources = r1
a1.sinks = k1 k2
a1.channels = c1

a1.sinkgroups = g1
a1.sinkgroups.g1.sinks = k1 k2
a1.sinkgroups.g1.processor.type = failover
a1.sinkgroups.g1.processor.priority.k1=100
a1.sinkgroups.g1.processor.priority.k2=90

#组名名.属性名=属性值
a1.sources.r1.type=exec
a1.sources.r1.command=tail -f /usr/local/flume/test
#声明r1的channel选择器
a1.sources.r1.selector.type = replicating

#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

##定义sink
a1.sinks.k1.type=avro
a1.sinks.k1.hostname=hadoop101
a1.sinks.k1.port=33333

a1.sinks.k2.type=avro
a1.sinks.k2.hostname=hadoop103
a1.sinks.k2.port=33333

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1
a1.sinks.k2.channel=c1

----------------------hadoop101----agent2------------------
a1.sources = r1
a1.sinks = k1
a1.channels = c1
#组名名.属性名=属性值
a1.sources.r1.type=avro
a1.sources.r1.bind=hadoop101
a1.sources.r1.port=33333


#定义sink
a1.sinks.k1.type=logger

#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1

----------------------hadoop103----agent3------------------
a1.sources = r1
a1.sinks = k1
a1.channels = c1
#组名名.属性名=属性值
a1.sources.r1.type=avro
a1.sources.r1.bind=hadoop103
a1.sources.r1.port=33333


#定义sink
a1.sinks.k1.type=logger

#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1
```



### 3) Load balancing Sink Processor
	负载均衡的sink processor! Load balancing Sink Processor维持了sink组中active状态的sink!
		使用round_robin 或 random 算法，来分散sink组中存活的sink之间的负载！
		
	必需配置：
		processor.sinks   – Space-separated list of sinks that are participating in the group 
		processor.type 		default The component type name, needs to be load_balance 


案例配置：

```properties
# 将上面failover的sinkgroups修改为如下所示
a1.sinkgroups = g1
a1.sinkgroups.g1.sinks = k1 k2
#a1.sinkgroups.g1.processor.type = failover
#a1.sinkgroups.g1.processor.priority.k1=100
#a1.sinkgroups.g1.processor.priority.k2=90
a1.sinkgroups.g1.processor.sinks=k1 k2
a1.sinkgroups.g1.processor.type = load_balance
```



## 2.4 聚合

![UHkXz4.png](https://s1.ax1x.com/2020/07/22/UHkXz4.png)

案例需求：
	hadoop102上的Flume-1监控文件/opt/module/group.log，
	hadoop103上的Flume-2监控某一个端口的数据流，
	Flume-1与Flume-2将数据发送给hadoop104上的Flume-3，Flume-3将最终数据打印到控制台。



案例配置：

```properties
#------hadoop102 flume1-exec-avro.conf--------	
# Name the components on this agent
a1.sources = r1
a1.sinks = k1
a1.channels = c1

# Describe/configure the source
a1.sources.r1.type = exec
a1.sources.r1.command = tail -F /opt/module/group.log
a1.sources.r1.shell = /bin/bash -c

# Describe the sink
a1.sinks.k1.type = avro
a1.sinks.k1.hostname = hadoop104
a1.sinks.k1.port = 4141

# Describe the channel
a1.channels.c1.type = memory
a1.channels.c1.capacity = 10000
a1.channels.c1.transactionCapacity = 1000

# Bind the source and sink to the channel
a1.sources.r1.channels = c1
a1.sinks.k1.channel = c1

#------hadoop103 netcat-avro.conf--------
# Name the components on this agent
a2.sources = r1
a2.sinks = k1
a2.channels = c1

# Describe/configure the source
a2.sources.r1.type = netcat
a2.sources.r1.bind = hadoop103
a2.sources.r1.port = 44444

# Describe the sink
a2.sinks.k1.type = avro
a2.sinks.k1.hostname = hadoop104
a2.sinks.k1.port = 4141

# Use a channel which buffers events in memory
a2.channels.c1.type = memory
a2.channels.c1.capacity = 10000
a2.channels.c1.transactionCapacity = 1000

# Bind the source and sink to the channel
a2.sources.r1.channels = c1
a2.sinks.k1.channel = c1



#------hadoop104 avro-logger.conf  上面两个都往当前的source中写入--------
# Name the components on this agent
a3.sources = r1
a3.sinks = k1
a3.channels = c1

# Describe/configure the source
a3.sources.r1.type = avro
a3.sources.r1.bind = hadoop104
a3.sources.r1.port = 4141

# Describe the sink
# Describe the sink
a3.sinks.k1.type = logger

# Describe the channel
a3.channels.c1.type = memory
a3.channels.c1.capacity = 10000
a3.channels.c1.transactionCapacity = 1000

# Bind the source and sink to the channel
a3.sources.r1.channels = c1
a3.sinks.k1.channel = c1
```



# 3. 事务

![UHEH8U.png](https://s1.ax1x.com/2020/07/22/UHEH8U.png)
### 1）数量关系

- batchSize:  每个Source和Sink都可以配置一个batchSize的参数。
  	这个参数代表一次性到channel中put|take 多少个event!
    	batchSize <=  transactionCapacity


- transactionCapacity： putList和takeList的初始值！

- capacity： channel中存储event的容量大小！
  		transactionCapacity <=  capacity


### 2） 概念

- putList:  source在向channel放入数据时的缓冲区！
  putList在初始化时，需要根据一个固定的size初始化，这个size在channel中设置！
  在channel中，这个size由参数transactionCapacity决定！

- put事务流程：source将封装好的event，先放入到putList中，放入完成后，
  	一次性commit(),这批event就可以写入到channel!
    	写入完成后，清空putList，开始下一批数据的写入！
    	假如一批event中的某些event在放入putList时，发生了异常，此时要执行rollback(),rollback()直接清空putList。



- takeList: sink在向channel拉取数据时的缓冲区！	
- take事务流程：  sink不断从channel中拉取event，每拉取一个event，这个event会先放入takeList中！
  		当一个batchSize的event全部拉取到takeList中之后，此时由sink执行写出处理！
    		假如在写出过程中，发送了异常，此时执行回滚！将takeList中所有的event全部回滚到channel!
    		反之，如果写出没有异常，执行commit(),清空takeList！



注意：这里的事务只是souce放到putList,以及takeList到Sink的事务处理，实际PutList到Channel和channel到takelist是完全没问题的。





# 4.自定义Source

1)  自定义类，继承AbstractSource，实现Configurable和PollableSource接口
2) 实现process()方法
	返回Status对象！
			READY：  一旦成功封装了1个或多个event，放入到channel!
			BACKOFF:  如果没有封装event或放入到channel失败！
			process()被PollableSourceRunner线程循环调用！

3) 从configure()中获取配置文件中配置的参数值
		context.getxxx("参数名","默认值")

4) 将该类打成jar包放入flume/lib下

```xml
<dependency>
	<groupId>org.apache.flume</groupId>
	<artifactId>flume-ng-core</artifactId>
	<version>1.7.0</version>
</dependency>
```

```java
package com.vison.flume.custom;

import java.util.ArrayList;
import java.util.List;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.PollableSource;
import org.apache.flume.channel.ChannelProcessor;
import org.apache.flume.conf.Configurable;
import org.apache.flume.event.SimpleEvent;
import org.apache.flume.source.AbstractSource;

/*
 * 具体的调用逻辑参考
 *
 * 使用flume接收数据，并给每条数据添加前缀，输出到控制台。前缀可从flume配置文件中配置
 */
public class MySource  extends AbstractSource implements Configurable, PollableSource {

	private String prefix;
	// 最核心方法，在process()中，创建Event，将event放入channel
	// Status{ READY, BACKOFF}
	// READY: source成功第封装了event，存入到channel，返回READY
	// BACKOFF： source无法封装了event，无法存入到channel，返回BACKOFF
	// process()方法会被Source所在的线程循环调用！
	@Override
	public Status process() throws EventDeliveryException {
		
		Status status=Status.READY;
		
		//封装event
		List<Event> datas=new ArrayList<>();
		
		for (int i = 0; i < 10; i++) {
			
			SimpleEvent e = new SimpleEvent();
			
			//向body中封装数据
			e.setBody((prefix+"hello"+i).getBytes());
			
			datas.add(e);
			
		}
		
		//将数据放入channel
		// 获取当前source对象对应的channelprocessor
		try {
			
			Thread.sleep(5000);
			
			ChannelProcessor cp = getChannelProcessor();
			
			cp.processEventBatch(datas);
			
		} catch (Exception e) {
			
			status=Status.BACKOFF;
			
			e.printStackTrace();
		}
		
		return status;
	}

	// 当source没有数据可封装时，会让source所在的线程先休息一会，休息的时间，由以下值*计数器系数
	@Override
	public long getBackOffSleepIncrement() {
		return 2000;
	}

	@Override
	public long getMaxBackOffSleepInterval() {
		return 5000;
	}

	// 从配置中来读取信息
	@Override
	public void configure(Context context) {
		
		//从配置文件中读取key为prefix的属性值，如果没有配置，提供默认值my-source:
		prefix=context.getString("prefix", "my-source:");
		
	}

}

```

配置：

```properties
a1.sources = r1
a1.sinks = k1
a1.channels = c1
#组名名.属性名=属性值
a1.sources.r1.type=com.vison.flume.custom.MySource
a1.sources.r1.prefix=my-source:

#定义sink
a1.sinks.k1.type=logger

#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1
```



# 5.自定义Interceptor

和Source流程一直，相关的实现和配置如下

```java
package com.vison.flume.custom;

import java.util.List;
import java.util.Map;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;

// 为每个event的header中添加key-value:  time=时间戳
public class MyInterceptor implements Interceptor{

	//初始化
	@Override
	public void initialize() {
		
	}

	//拦截处理方法
	// 为每个event的header中添加key-value:  time=时间戳
	@Override
	public Event intercept(Event event) {
		
		Map<String, String> headers = event.getHeaders();
		
		headers.put("time", System.currentTimeMillis()+"");
		
		return event;
	}

	//拦截处理方法
	@Override
	public List<Event> intercept(List<Event> events) {
		
		for (Event event : events) {
			intercept(event);
		}
		
		return events;
	}

	// 结合时调用的方法
	@Override
	public void close() {
		
	}
	
	//额外提供一个内部的Builder，因为Flume在创建拦截器对象时，固定调用Builder来获取
	public static class Builder implements Interceptor.Builder{

		// 读取配置文件中的参数
		@Override
		public void configure(Context context) {
			
		}

		//返回一个当前的拦截器对象
		@Override
		public Interceptor build() {
			return new MyInterceptor();
		}
		
		
	}

}

```



配置：

```properties
#agent 
a1.sources = r1
a1.sinks = k1
a1.channels = c1

# 定义source
a1.sources.r1.type = netcat
a1.sources.r1.bind = localhost
a1.sources.r1.port = 44444
a1.sources.r1.interceptors = i1
# 这里的拦截器需要类名和$Builder的名称
a1.sources.r1.interceptors.i1.type = com.vison.flume.interceptor.MyInterceptor$Builder

#定义sink
a1.sinks.k1.type=logger

#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1

```



# 6.自定义Sink

和上面类似

```java
package com.vison.flume.custom;

import org.apache.flume.Channel;
import org.apache.flume.ChannelException;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.Transaction;
import org.apache.flume.conf.Configurable;
import org.apache.flume.sink.AbstractSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 从配置文件中读取一个后缀，将event的内容读取后，拼接后缀进行输出
public class MySink extends AbstractSink implements Configurable {
	
	private String suffix;
	
	private Logger logger=LoggerFactory.getLogger(MySink.class);

	//核心方法：处理sink逻辑
	// Status.ready:  成功传输了一个或多个event
	// Status.backoff:  从channel中无法获取数据
	@Override
	public Status process() throws EventDeliveryException {
		
		Status status=Status.READY;
		
		//获取当前sink对接的channel
		Channel c = getChannel();
		
		//声明Event，用来接收chanel中的event
		Event e=null;
		
		Transaction transaction = c.getTransaction();
		try {
			//获取take事务对象
			
			//开启事务
			transaction.begin();
			
			//如果channel中，没有可用的event，此时e会是null
			e=c.take();
			
			if (e==null) {
				
				status=Status.BACKOFF;
				
			}else {
				
				//取到数据后，执行拼接后缀进行输出
				logger.info(new String(e.getBody())+suffix);
			}
			//提交事务
			transaction.commit();
			
		} catch (ChannelException e1) {
			
			//回滚事务
			transaction.rollback();
			
			status=Status.BACKOFF;
			
			e1.printStackTrace();
		}finally {
		
			//关闭事务对象
			transaction.close();
			
		}
		
		return status;
	}

	//从配置中读取配置的参数
	@Override
	public void configure(Context context) {
		
		suffix=context.getString("suffix", ":hi");
	}

}

```

配置：

```properties
#agent 
a1.sources = r1
a1.sinks = k1
a1.channels = c1

# 定义source
a1.sources.r1.type = netcat
a1.sources.r1.bind = localhost
a1.sources.r1.port = 44444

#定义sink
a1.sinks.k1.type = com.vison.MySink
a1.sinks.k1.suffix = :vison

#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1
```



# 7.监控Flume

如何实现监控
	在使用flume期间，我们需要监控什么？
		channel当前的容量是多少？
		channel当前已经使用了多少容量？
		source向channel中put成功了多少个event?
		sink从channel中take成功了多少个event?
	

## 1）使用JMX

```
J2EE定义了14种技术规范！
	JDBC： java连接数据库的技术规范！
	Servlet:  所有javaweb写的程序，都最终使用Servlet完成请求的接受和响应
	JMX(java monitor extension)： java的监控扩展模块
		JMX可以帮助我们实时监控一个java进程中需要了解的参数，可以实时修改java进程中某个对象的参数！
					
		①MBean(monitor bean): 监控的参数封装的Bean
		②JMX的monitor服务，这个服务可以在程序希望获取到MBean参数时，来请求服务，请求后服务帮我们
					对MBean的参数进行读写！
					
					flume已经提供了基于JMX的服务实现，如果我们希望使用，只需要启动此服务即可！
		③客户端，客户端帮我们来向JMX服务发送请求，显示服务返回的Mbean的结果
```



## 2) 客户端监控

```shell
①使用JCONSOLE程序查看
		在flume的conf/env.sh文件中，配置
		export JAVA_OPTS=”-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=5445 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false”

②使用web浏览器向JMX服务发请求查看
		使用JSON Reporting
		bin/flume-ng agent --conf-file example.conf --name a1 -Dflume.monitoring.type=http -Dflume.monitoring.port=34545


③使用第三方框架，例如Ganglia; 不过这个几乎不用，太难用和不好看了
		可视化MBean：  内置一个可以处理Http请求的服务器(PHP)
							ganglia-web(选集群的一台机器安装)
		数据库：  需要把采集到的MBean的信息写入到数据库，在查询时，从数据库查询，返回结果
							ganglia-gmetad(选集群的一台机器)负责将每台机器ganglia-gmond收集的数据汇总，汇总后存入数据库rrdtool
		收集数据的服务：  需要在每个机器都部署一个收集本台机器上运行的进程的指标的服务，此服务
						将收集的指标数据汇总到数据库中，由PHP程序来查询
							ganglia-gmond(需要采集哪个机器的指标，就在哪个机器安装)负责监控MBean，采集数据
							
		
@4开源方案：  ①需要有一个可以请求JMX服务的框架  JMXTrans
			②需要有一个数据库(时序数据库最佳)，数据来存储采集到的信息  Influxdb
			③可视化框架来显示指标   Graffna
```



