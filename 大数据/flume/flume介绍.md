# 一、Flume的核心概念

Flume组成架构：



![U42fvF.png](https://s1.ax1x.com/2020/07/20/U42fvF.png)

- 1.source ：  用户需要根据自己的数据源的类型，选择合适的source对象！
- 2.sink：    用户需要根据自己的数据存 储的目的地的类型，选择合适的sink对象！
- 3.Interceptors:  在source将event放入到channel之前，调用拦截器对event进行拦截和处理！
- 4.Channel Selectors:  当一个source对接多个channel时，由 Channel Selectors选取channel将event存
- 5.sink processor:  当多个sink从一个channel取数据时，为了保证数据的顺序，由sink processor从多个sink中挑选一个sink，由这个sink干活！



### **1.1 Agent**

​		Agent是一个JVM进程，它以事件的形式将数据从源头送至目的。

​		Agent主要有3个部分组成，Source、Channel、Sink。

### **1.2 Source**

​		Source是负责接收数据到Flume Agent的组件。Source组件可以处理各种类型、各种格式的日志数据，包括avro、thrift、exec、jms、spooling directory、netcat、sequence generator、syslog、http、legacy。

### **1.3 **Sink

​		Sink不断地轮询Channel中的事件且批量地移除它们，并将这些事件批量写入到存储或索引系统、或者被发送到另一个Flume Agent。

Sink组件目的地包括hdfs、logger、avro、thrift、ipc、file、HBase、solr、自定义。

### **1.4 **Channel

​		Channel是位于Source和Sink之间的缓冲区。因此，Channel允许Source和Sink运作在不同的速率上。Channel是线程安全的，可以同时处理几个Source的写入操作和几个Sink的读取操作。

Flume自带两种Channel：Memory Channel和File Channel。

Memory Channel是内存中的队列。Memory Channel在不需要关心数据丢失的情景下适用。如果需要关心数据丢失，那么Memory Channel就不应该使用，因为程序死亡、机器宕机或者重启都会导致数据丢失。

File Channel将所有事件写到磁盘。因此在程序关闭或机器宕机的情况下不会丢失数据。

### **1.5 Event**

​		传输单元，Flume数据传输的基本单元，以Event的形式将数据从源头送至目的地。Event由**Header**和**Body**两部分组成，Header用来存放该event的一些属性，为K-V结构，Body用来存放该条数据，形式为字节数组。

### **1.**6 Interceptors

​	  在Flume中允许使用拦截器对传输中的event进行拦截和处理！拦截器必须实现org.apache.flume.interceptor.Interceptor接口。拦截器可以根据开发者的设定修改甚至删除event！Flume同时支持拦截器链，即由多个拦截器组合而成！通过指定拦截器链中拦截器的顺序，event将按照顺序依次被拦截器进行处理！

### **1**.7 Channel Selectors

​	   Channel Selectors用于source组件将event传输给多个channel的场景。常用的有replicating（默认）和multiplexing两种类型。replicating负责将event复制到多个channel，而multiplexing则根据event的属性和配置的参数进行匹配，匹配成功则发送到指定的channel!

### **1**.8 Sink Processors

​	    用户可以将多个sink组成一个整体（sink组），Sink Processors可用于提供组内的所有sink的负载平衡功能，或在时间故障的情况下实现从一个sink到另一个sink的故障转移



 

# 二、Flume的安装

参考：<http://flume.apache.org/FlumeUserGuide.html>

下载：http://archive.apache.org/dist/flume/

```
1.版本区别
		0.9之前称为flume og
		0.9之后为flume ng
		
		目前都使用flume ng!
		
		1.7之前，没有taildirsource，1.7及之后有taildirsource
2.安装Flume
		①保证有JAVA_HOME
		②解压即可
		
3.使用Flume
	启动agent:    flume-ng  agent  -n agent的名称  -f agent配置文件  -c 其他配置文件所在的目录 -Dproperty=value
		
4.如何编写agent的配置文件
		agent的配置文件的本质是一个Properties文件！格式为 属性名=属性值
		
		在配置文件中需要编写：
		①定义当前配置文件中agent的名称，再定义source,sink,channel它们的别名
		②指定source和channel和sink等组件的类型
		③指定source和channel和sink等组件的配置，配置参数名和值都需要参考flume到官方用户手册
		④指定source和channel的对应关系，以及sink和channel的对应关系。连接组件！
```



# 三、案例

## 1. 案例一 netcat - logger

使用Flume监听一个端口，收集该端口数据，并打印到控制台。使用netcat-tcp来模拟发送数据

```shell
1.安装netcat工具
	[root@localhost software]$ sudo yum install -y nc

2.判断44444端口是否被占用
	[root@localhost flume-telnet]$ sudo netstat -tunlp | grep 44444

3.创建Flume Agent配置文件flume-netcat-logger.conf,一个业务一个配置来启动
	在flume目录下创建job文件夹并进入job文件夹。
	[root@localhost flume]$ mkdir job
	[root@localhost flume]$ cd job/
	在job文件夹下创建Flume Agent配置文件flume-netcat-logger.conf。
	[root@localhost job]$ vim flume-netcat-logger.conf
	在flume-netcat-logger.conf文件中添加如下内容
```

```properties
# agent 组件名称定义a1
a1.sources = r1
a1.sinks = k1
a1.channels = c1

#配置source来源信息
a1.sources.r1.type = netcat
a1.sources.r1.bind = localhost
a1.sources.r1.port = 44444

#sink类型
a1.sinks.k1.type = logger

#设置channel，这里使用的memory
a1.channels.c1.type = memory
a1.channels.c1.capacity = 10000
a1.channels.c1.transactionCapacity = 1000

#将source和sink绑定到channel上
a1.sources.r1.channels = c1
a1.sinks.k1.channel = c1

# 参考官网：http://flume.apache.org/FlumeUserGuide.html
```

```bash
4.启动flume监听 a1 是我们文件定义的组件名称
[root@localhost flume]$ bin/flume-ng agent -c conf/ -n a1 –f job/flume-netcat-logger.conf -Dflume.root.logger=INFO,console

参数说明：
	--conf/-c：表示配置文件存储在conf/目录
	--name/-n：表示给agent起名为a1
	--conf-file/-f：flume本次启动读取的配置文件是在job文件夹下的flume-telnet.conf文件。
	-Dflume.root.logger=INFO,console ：-D表示flume运行时动态修改flume.root.logger参数属性值，并将控制台日志打印级别设置为INFO级别。日志级别包括:log、info、warn、error。
	
5.向本机4444 发送信息，然后查看flume控制台输出
	[root@localhost ~]$ nc localhost 44444
```



## 2. 案例二 Eexec - HDFS

使用exec作为输入，HDFS作为sink，安装启动;exec 通过监控数据输出来获取数据，支持tail,cat等命令

参考官网：<http://flume.apache.org/releases/content/1.9.0/FlumeUserGuide.html#hdfs-sink>

**配置文件**

```properties
#1) agent 组件名称定义，这里的agent 是a1，启动尤为注意
a1.sources = r1
a1.sinks = k1
a1.channels = c1

#2)配置source来源信息，exec命令
a1.sources.r1.type = exec
a1.sources.r1.command = tail -f /tmp/root/hive.log

#3)sink类型 hdfs 
a1.sinks.k1.type = hdfs
a1.sinks.k1.hdfs.path = /flume/events/%y-%m-%d/%H%M/%S
#文件前缀
a1.sinks.k1.hdfs.filePrefix = flume-hdfs-

# 是否使用时间上的舍弃，按照舍弃的方式做
#下面分别是时间间隔大小和单位，比如10s，那么上面使用的大小就是按照10位倍数舍弃创建文件
a1.sinks.k1.hdfs.round = true
a1.sinks.k1.hdfs.roundValue = 10
a1.sinks.k1.hdfs.roundUnit = second

#当使用了转移字符比如[%y]如果在event的头中没有时间戳，需要使用本地时间戳
a1.sinks.k1.hdfs.useLocalTimeStamp = true

#hdfs 滚动创建新的文件方式，下面都是或的形式；设置为0表示禁用这个策略
#分别是三种：时间间隔（秒），写入文件大小（字节），events的数量
a1.sinks.k1.hdfs.rollInterval=30
a1.sinks.k1.hdfs.rollSize=268,435,456
a1.sinks.k1.hdfs.rollCount=10

#4)设置channel，这里使用的memory
a1.channels.c1.type = memory
a1.channels.c1.capacity = 10000
a1.channels.c1.transactionCapacity = 1000

#5)将source和sink绑定到channel上
a1.sources.r1.channels = c1
a1.sinks.k1.channel = c1
```



```
Execsouce的缺点
	execsource和异步的source一样，无法在source向channel中放入event故障时，及时通知客户端，暂停生成数据！容易造成数据丢失！
		
 解决方案： 需要在发生故障时，及时通知客户端！
		  如果客户端无法暂停，必须有一个数据的缓存机制！
					
 如果希望数据有强的可靠性保证，可以考虑使用SpoolingDirSource或TailDirSource或自己写Source自己控制！
```



## 3.案例三 SpoolingDirSource- HDFS

简介：
	SpoolingDirSource指定本地磁盘的一个目录为"Spooling(自动收集)"的目录！这个source可以读取目录中
	新增的文件，将文件的内容封装为event!

	SpoolingDirSource在读取一整个文件到channel之后，它会采取策略，要么删除文件(是否可以删除取决于配置)，要么对文件进程一个完成状态的重命名，这样可以保证source持续监控新的文件！
	
	SpoolingDirSource和execsource不同，SpoolingDirSource是可靠的！即使flume被杀死或重启，依然不丢数据！但是为了保证这个特性，付出的代价是，一旦flume发现以下情况，flume就会报错，停止！
			①一个文件已经被放入目录，在采集文件时，不能被修改
			②文件的名在放入目录后又被重新使用（出现了重名的文件）
			
	要求： 必须已经封闭的文件才能放入到SpoolingDirSource，在同一个SpoolingDirSource中都不能出现重名的文件！
```shell
使用：必需配置：
	type	–	The component type name, needs to be spooldir.
	spoolDir	–	The directory from which to read files from.
```

**配置文件：**

```properties
#a1是agent的名称，a1中定义了一个叫r1的source，如果有多个，使用空格间隔
a1.sources = r1
a1.sinks = k1
a1.channels = c1
#组名名.属性名=属性值
a1.sources.r1.type=spooldir
a1.sources.r1.spoolDir=/usr/local/flume/data

#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

#定义sink
a1.sinks.k1.type = hdfs
#一旦路径中含有基于时间的转义序列，要求event的header中必须有timestamp=时间戳，如果没有需要将useLocalTimeStamp = true
a1.sinks.k1.hdfs.path = hdfs://192.168.4.131:9000/flume/%Y%m%d/%H/%M
#上传文件的前缀
a1.sinks.k1.hdfs.filePrefix = logs-

#以下三个和目录的滚动相关，目录一旦设置了时间转义序列，基于时间戳滚动
#是否将时间戳向下舍
a1.sinks.k1.hdfs.round = true
#多少时间单位创建一个新的文件夹
a1.sinks.k1.hdfs.roundValue = 1
#重新定义时间单位
a1.sinks.k1.hdfs.roundUnit = minute

#是否使用本地时间戳
a1.sinks.k1.hdfs.useLocalTimeStamp = true
#积攒多少个Event才flush到HDFS一次
a1.sinks.k1.hdfs.batchSize = 100

#以下三个和文件的滚动相关，以下三个参数是或的关系！以下三个参数如果值为0都代表禁用！
#60秒滚动生成一个新的文件
a1.sinks.k1.hdfs.rollInterval = 30
#设置每个文件到128M时滚动
a1.sinks.k1.hdfs.rollSize = 134217700
#每写多少个event滚动一次
a1.sinks.k1.hdfs.rollCount = 0
#以不压缩的文本形式保存数据
a1.sinks.k1.hdfs.fileType=DataStream 


#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1
```



## 4. 案例四 TailDirSource - logger

**简介：**

- Taildir Source 可以读取多个文件最新追加写入的内容！

- Taildir Source是可靠的，即使flume出现了故障或挂掉。Taildir Source在工作时，会将读取文件的最后的位置记录在一个json文件中，一旦agent重启，会从之前已经记录的位置，继续执行tail操作！

  ​	

flume ng 1.7版本后提供！

**常见问题：** TailDirSource采集的文件，不能随意重命名！如果日志在正在写入时，名称为 xxxx.tmp，写入完成后，滚动，改名为xxx.log，此时一旦匹配规则可以匹配上述名称，就会发生数据的重复采集！



**注意：**

- Json文件中，位置是可以修改，修改后，Taildir Source会从修改的位置进行tail操作！如果JSON文件丢失了，此时会重新从每个文件的第一行，重新读取，这会造成数据的重复！

- Taildir Source目前只能读文本文件！

```shell
必需配置：
	channels	–	 
	type	–	The component type name, needs to be TAILDIR.
	filegroups	–	Space-separated list of file groups. Each file group indicates a set of files to be tailed.
	filegroups.<filegroupName>	–	Absolute path of the file group. Regular expression (and not file system patterns) can be used for filename only.
```
**配置文件：**

```properties
#使用TailDirSource和logger sink
#a1是agent的名称，a1中定义了一个叫r1的source，如果有多个，使用空格间隔
a1.sources = r1
a1.sinks = k1
a1.channels = c1
#组名名.属性名=属性值
a1.sources.r1.type=TAILDIR
a1.sources.r1.filegroups=f1 f2
a1.sources.r1.filegroups.f1=/usr/local/flume/hi
a1.sources.r1.filegroups.f2=/usr/local/flume/test

#定义sink
a1.sinks.k1.type=logger
a1.sinks.k1.maxBytesToLog=100

#定义chanel
a1.channels.c1.type=memory
a1.channels.c1.capacity=1000

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1
a1.sinks.k1.channel=c1
```



## 5.FileChannel

fileChannel:  channel中的event是存储在文件中！比memorychannel可靠，但是效率略低！
必须的配置：

- type=file
- checkpointDir=checkpoint线程(负责检查文件中哪些event已经被sink消费了，将这些event的文件删除)保存数据的目录！
- useDualCheckpoints=false 是否启动双检查点，如果启动后，会再启动一个备用的checkpoint线程！
  			如果改为true，还需要设置backupCheckpointDir(备用的checkpoint线程的工作目录)
- dataDirs=在哪些目录下保存event，默认为~/.flume/file-channel/data，可以是逗号分割的多个目录！

可选配置

- keep-alive ：表示一次put操作支持的时间，超过该时间就报错，本次put操作回滚



