### 1.zookeeper核心概念

&emsp;&emsp;ZooKeeper命名空间中的Znode，兼具文件和目录两种特点。既像文件一样维护着数据、元信息、ACL、时间戳等数据结构，又像目录一样可以作为路径标识的一部分。 

```
每个Znode由3部分组成:
    stat：此为状态信息, 描述该Znode的版本, 权限等信息
    data：与该Znode关联的数据
    children：该Znode下的子节点
```

&emsp;&emsp;ZooKeeper虽然可以关联一些数据，但并没有被设计为常规的数据库或者大数据存储，相反的是，它用来管理调度数据，比如分布式应用中的配置文件信息、状态信息、汇集位置等等。这些数据的共同特性就是它们都是很小的数据，通常以KB为大小单位。ZooKeeper的服务器和客户端都被设计为严格检查并限制每个Znode的数据大小一般在kb大小，至多1M，但常规使用中应该远小于此值。如果需要存储大型数据，建议将数据存放在hdfs等文件存储系统

#### 1.1 数据节点分类

 &emsp;&emsp;zookeeper节点
&emsp;&emsp;节点类型有四种，分别是PERSISTENT、PERSISTENT_SEQUENTIAL、EPHEMERAL、EPHEMERAL_SEQUENTIAL，分别为永久节点，永久有序节点，临时节点和临时有序节点。节点的类型在创建时即被确定，并且不能改变。

```
- 同级节点：同级节点是唯一的

- 临时节点：该节点的生命周期依赖于创建它们的会话。一旦会话(Session)结束，临时节点将被自动删除，当然可以也可以手动删除。虽然每个临时的Znode都会绑定到一个客户端会话，但他们对所有的客户端还是可见的。另外，ZooKeeper的临时节点不允许拥有子节点。

- 永久节点：该节点的生命周期不依赖于会话，并且只有在客户端显示执行删除操作的时候，他们才能被删除。

- 有序节点：当创建Znode的时候，用户可以请求在ZooKeeper的路径结尾添加一个递增的计数。这个计数对于此节点的父节点来说是唯一的，它的格式为"%10d"(10位数字，没有数值的数位用0补充，例如"0000000001")。当计数值大于2的32次方-1时，计数器将溢出。
```

#### 1.2 数据节点stat信息

&emsp;&emsp;通过命令get /node可以获取到如下所示信息：

```
[root: 127.0.0.1:2181(CONNECTED) 2] get /node
value            						#当前节点的信息
cZxid = 0x200000007    					#创建的时候生成的事务ID
ctime = Thu Dec 07 16:37:55 CST 2019  	#创建的时候时间戳
mZxid = 0x2400000006   					 #修改（modify）的时候生成的事务ID
mtime = Mon Dec 18 10:57:16 CST 2019 	 #修改（modify）的时候时间戳
pZxid = 0x2400000004						#子节点列表最后修改事务ID，子节点内容修改不影响pZxid
cversion = 4										#类似乐观锁保证原子性 ，子节点版本号
dataVersion = 8									#类似乐观锁保证原子性 ，数据节点版本号
aclVersion = 0										#类似乐观锁保证原子性 ，节点ACL版本号
ephemeralOwner = 0x0						#创建该临时节点的会话sessionID,如果是持久节点，那么就是0。这里是持久化节点
dataLength = 6								 #数据节点内容长度
numChildren = 2								#当前节点的子节点数量
```

#### 1.3 Session会话

&emsp;&emsp;在zookeeper客户端和服务端成功完成链接后，就建立一个会话，Zookeeper会在整个运行期间的生命周期中，会在不同的会话状态中进行切换，这些状态有Connecting,Connected,ReConnecting,ReConnected,Close等。

```
  Session 是指客户端会话，在ZooKeeper 中，一个客户端连接是指客户端和 ZooKeeper 服务器之间的TCP长连接。

       ZooKeeper 对外的服务端口默认是2181，客户端启动时，首先会与服务器建立一个TCP
  连接，从第一次连接建立开始，客户端会话的生命周期也开始了，通过这个连接，客户端能够通
  过心跳检测和服务器保持有效的会话，也能够向 ZooKeeper 服务器发送请求并接受响应，同
  时还能通过该连接接收来自服务器的 Watch 事件通知。

      Session 的 SessionTimeout 值用来设置一个客户端会话的超时时间。当由于服务器
  压力太大、网络故障或是客户端主动断开连接等各种原因导致客户端连接断开时，只要在 
  SessionTimeout 规定的时间内能够重新连接上集群中任意一台服务器，那么之前创建的会话
  仍然有效。
```

#### 1.4 集群角色

&emsp;&emsp;集群角色包含：Leader，Follower ，Observer；一个 ZooKeeper 集群同一时刻只会有一个 Leader，其他都是 Follower 或 Observer。

&emsp;&emsp;zooKeeper 默认只有 Leader 和 Follower 两种角色，没有 Observer 角色。为了使用 Observer 模式，在任何想变成Observer的节点的配置文件中加入:peerType=observer 并在所有 server 的配置文件中，配置成 observer 模式的 server 的那行配置追加 :observer

```
    1.Leader：ZooKeeper 集群的所有机器通过一个 Leader 选举过程来选定一台被称为Leader
         的机器，Leader服务器为客户端提供读写服务。
         作用：1）事务请求的唯一调度和处理者，保证事务处理的顺序性；
                    2）集群内部各服务器的调度者；
    2.Follower：
        作用：1）处理客户端非事务请求-读，转发事务给Leader服务器；
                   2）参与事务Proposal请求的投票（超过半数，Leader才能发起commit通知）
                   3）参与Leader选举投票
    3.Observer 
          作用：  1）处理客户端非事务请求-读，转发事务给Leader服务器；  
               此 Observer 可以在不影响写性能的情况下提升集群的读性能。当写请求到了的时候，会转发给Leader服务器
       
      bin/zkServer.sh status  可以查看当前节点是什么角色
```

**集群组成：**

&emsp;&emsp;通常 zookeeper 是由<font color="red"> 2n+1</font> 台 server 组成，每个 server 都知道彼此的存在。对于 2n+1 台 server，只要有 n+1 台（大多数）server 可用，整个系统保持可用。我们已经了解到，一个 zookeeper 集群如果要对外提供可用的服务，那么集群中必须要有过半的机器正常工作并且彼此之间能够正常通信;

&emsp;&emsp;基于这个特性，如果向搭建一个能够允许 F 台机器down 掉的集群，那么就要部署 2*F+1 台服务器构成的zookeeper 集群。因此 3 台机器构成的 zookeeper 集群，能够在挂掉一台机器后依然正常工作。一个 5 台机器集群的服务，能够对 2 台机器怪调的情况下进行容灾。如果一台由 6 台服务构成的集群，同样只能挂掉 2 台机器。因此，5 台和 6 台在容灾能力上并没有明显优势，反而增加了网络通信负担。系统启动时，集群中的 server 会选举出一台server 为 Leader，其它的就作为 follower（这里先不考虑observer 角色）。

&emsp;&emsp;之所以要满足这样一个等式，是因为一个节点要成为集群中的 leader，需要有超过及群众过半数的节点支持，这个涉及到 leader 选举算法。同时也涉及到事务请求的提交投票。

#### 1.5 Watcher 事件监听器

```
     Watcher 是 ZooKeeper 中一个很重要的特性。ZooKeeper允许用户在指定节点上注册一些 Watcher，并且在一些特定事件触发的时候，
     ZooKeeper 服务端会将事件通知到感兴趣的客户端上去。该机制是 ZooKeeper 实现分布式协调服务的重要特性。
```

#### 1.6 ACL

Zookeeper采用ACL(access Control Lists) 策略来进行权限控制，类似于Unix文件系统的权限控制。Zookeeper定义了如下5中权限控制：

- CREATE:创建子节点权限
- READ：获取节点数据和子节点列表的权限
- WRITE：更新节点的权限
- DELETE：删除子节点的权限
- ADMIN：设置节点ACL的权限
  注意点这里CREATE和DELETE是针对子节点的权限控制。

### 2. Zookeeper数据存储

数据存储在DataTree中，用ConcurrentHashMap存储。

- **事务日志**  ： conf/zoo.cfg 中dataDir目录中。默认是/tmp/zookeeper，最好不要放在tmp目录下，这个是临时目录，会定时清理。一般挂载在某个磁盘下。日志文件的命名规则为log.**，**表示写入该日志的第一个事务的ID，十六进制表示。
- **快照日志**  ：conf/zoo.cfg 中dataDir目录中。zookeeper快照文件的命名规则为snapshot.**，其中**表示zookeeper触发快照的那个瞬间，提交的最后一个事务的ID。
- **运行时日志**： bin/zookeeper.out文件中

参考：
https://blog.csdn.net/piaoslowly/article/details/81625306
https://blog.csdn.net/weijifeng_/article/details/79775738#2