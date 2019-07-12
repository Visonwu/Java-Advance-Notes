### 1.zookeeper简介
&emsp;&emsp;zookeeper 是一个开源的分布式协调服务，由雅虎公司创建，是 google chubby 的开源实现。zookeeper 致力于提供一个高性能，高可用且具有严格的顺序访问控制能力（主要是写操作顺序性）的分布式协调服务。高性能使得Zookeeper能够应用于哪些对系统吞吐有明确要求的大型分布式系统。高可用使得分布式下单点问题得到解决；而严格的顺序访问使得客户端基于Zookeeper实现一些复杂的同步语义。


#### 1.1 Zookeeper的特点
&emsp;&emsp;Zookeeper工作在集群中，对集群提供分布式协调服务，它提供的分布式协调服务具有如下的特点：

- **顺序一致性**
从同一个客户端发起的事务请求，最终将会严格按照其发起顺序被应用到zookeeper中
- **原子性**
所有事物请求的处理结果在整个集群中所有机器上的应用情况是一致的，即，要么整个集群中所有机器都成功应用了某一事务，要么都没有应用，一定不会出现集群中部分机器应用了改事务，另外一部分没有应用的情况。
- **单一视图**
无论客户端连接的是哪个zookeeper服务器，其看到的服务端数据模型都是一致的。
- **可靠性**
一旦服务端成功的应用了一个事务，并完成对客户端的响应，那么该事务所引起的服务端状态变更将会一直保留下来，除非有另一个事务又对其进行了改变。
- **实时性**
Zookeeper仅仅保证在一段时间内，客户端最终一定能够从服务器上读取到最新的数据状态。

### 2.zookeeper安装部署
&emsp;&emsp;zookeeper 有两种运行模式：集群模式和单击模式。
&emsp;&emsp;下载zookeeper 安 装 包 ：http://apache.fayea.com/zookeeper/ ；下载完成，通过 tar -zxvf 解压。


#### 2.1 单机安装

    步骤一：解压   tar -zxvf 压缩包开放端口2888、3888 、2181
    步骤二：创建文件夹 进入  	cd zookeeper-3.4.6
    步骤三： copy配置文件            cp zoo_sample.cfg  zoo.cfg    #默认zookeeper是读取zoo.cfg文件，需要把修改原本的文件名
    步骤四：进入启动目录		cd /cd zookeeper-3.4.6/bin   
    步骤五：启动服务器 		./zkServer.sh	
    步骤六： 查看是否启动成功 	netstat -apn | grep 2181
    步骤七：启动客户端		./zkCli.sh
    步骤八：进入客户端后help命令查看命令   		help

#### 2.2 集群安装
&emsp;&emsp;在zookeeper集群中，各个节点总共有三种角色，分别是：leader，follower，observer。参考后面的文章。


&emsp;&emsp;集群模式我们采用模拟 3 台机器来搭建 zookeeper 集群。分别复制安装包到三台机器上并解压，同时 copy 一份zoo.cfg。

**1） 修改配置文件**

    #每个zoo.cfg配置文件 添加下面三个
    server.1=IP1:2888:3888 【2888：访问 zookeeper 的端口；3888：重新选举 leader 的端口】
    server.2=IP2.2888:3888
    server.3=IP3.2888:3888

> server.A=B：C：D：其 中
> -  A 是一个数字，表示这个是第几号服务器； 
> - B 是这个服务器的 ip 地址；
> - C 表示的是这个服务器与集群中的 Leader服务器交换信息的端口； 
> - D 表示的是万一集群中的 Leader服务器挂了，需要一个端口来重新进行选举，选出一个新的 Leader，而这个端口就是用来执行选举时服务器相互通信的端口。如果是伪集群的配置方式，由于 B 都是一样，所以不同的 Zookeeper 实例通信端口号不能一样，所以要给它们分配不同的端口号。在集群模式下，集群中每台机器都需要感知到整个集群是 由 哪 几 台 机 器 组 成 的 在 配 置 文 件 中 ， 按 照 格 式server.id=host:port:port，每一行代表一个机器配置id: 指的是server ID,用来标识该机器在集群中的机器序号

**2. 新建 datadir 目录，设置 myid**

​	在/tmp/zookeeper中新建data目录，然后各自创建myid文件，写入各自的id

```bin
 #myid文件需要放在datadir目录data下，该datadir在zoo.cfg中配置
 // 例如：dataDir=/tmp/zookeeper
 cd zookeeper/data
```
   	 vim myid       #创建文件myid   (文件里面填写)  1   
   	 (这里的这个数字和 server.1=120.78.191.34:2888:3888  server后面的数字是相关的,各自的服务器对应自己的id  )

> &emsp;&emsp;在每台zookeeper机器上，我们都需要在数据目录(dataDir)下创建一个 myid文件，该文件只有一行内容，对应每台机器的 Server ID 数字；比如 server.1 的 myid 文件内容就是1。【必须确保每个服务器的 myid 文件中的数字不同，并且和自己所在机器的 zoo.cfg 中 server.id 的 id 值一致，id 的范围是 1~255】

**3. 启动 zookeeper**

```
  #分别启动三台机器即可
 ./zkServer.sh start
```

#### 2.3 zookeeper shell命令
1. 启动 ZK 服务:
bin/zkServer.sh start
2. 查看 ZK 服务状态:
bin/zkServer.sh status
3. 停止 ZK 服务:
bin/zkServer.sh stop
4. 重启 ZK 服务:
bin/zkServer.sh restart
5. 连接服务器
zkCli.sh -timeout 0 -r -server ip:port

#### 2.4 客户端常用命令
这是基于客户端操作数据的相关命令

```bash
help         #这个可以查看命令使用方式
create path data      create  /bobo   124   创建节点 和数据  （/表示根目录）
get  /vison      			获取存在vison的数据
set /vison  122    		重新设置/vison 的数据
rmr    path			remove  recycle  循环删除目录(可以删除带有子节点)
delete  path			可以删除当前节点，但是他下面有子节点就不能删除，rmr可以删除

ls   /					表示查看根节点(ls  path    可以查看其它节点)
create /vison/wusu	22	创建vison下的子数据wusu
history				表示操作的命令的记录

create -s /path  data   	创建顺序节点  
create -e /path  data	创建临时节点(客户端关闭，当前节点消失)
```

