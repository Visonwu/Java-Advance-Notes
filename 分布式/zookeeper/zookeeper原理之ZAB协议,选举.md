

## 1. ZAB协议
&emsp;&emsp;ZAB（Zookeeper Atomic Broadcast） 协议是为分布式协调服务 ZooKeeper 专门设计的一种支持崩溃恢复的原子广播协议。在 ZooKeeper 中，主要依赖 ZAB 协议来实现分布式数据一致性，基于该协议，ZooKeeper 实现了一种主备模式的系统架构来保持集群中各个副本之间的数据一致性。通过单一的主进程来接收并处理客户端的所有事务请求。

ZAB协议包含两种基本模式：**崩溃恢复和消息广播**


&emsp;&emsp;当整个集群在启动时，或者当 leader 节点出现网络中断、崩溃等情况时，ZAB 协议就会进入恢复模式并选举产生新的 Leader，当 leader 服务器选举出来后，并且集群中有过半的机器和该 leader 节点完成数据同步后（同步指的是数据同步，用来保证集群中过半的机器能够和 leader 服务器的数据状态保持一致），ZAB 协议就会退出恢复模式。

&emsp;&emsp;当集群中已经有过半的 Follower 节点完成了和 Leader 状态同步以后，那么整个集群就进入了消息广播模式。这个时候，在 Leader 节点正常工作时，启动一台新的服务器加入到集群，那这个服务器会直接进入数据恢复模式，和leader 节点进行数据同步。同步完成后即可正常对外提供非事务请求的处理。

### 1.1 消息广播的实现原理
消息广播的过程实际上是一个简化版本的二阶段提交过程

1. leader 接收到消息请求后，将消息赋予一个全局唯一的64 位自增 id，叫：zxid，通过 zxid 的大小比较既可以实现因果有序这个特征
2. leader 为每个 follower 准备了一个 FIFO 队列（通过 TCP协议来实现，以实现了全局有序这一个特点）将带有 zxid的消息作为一个提案（proposal）分发给所有的 follower
3. 当 follower 接收到 proposal，先把 proposal 写到磁盘，写入成功以后再向 leader 回复一个 ack
4. 当 leader 接收到合法数量（超过半数节点）的 ACK 后，leader 就会向这些 follower 发送 commit 命令，同时会在本地执行该消息
5. 当 follower 收到消息的 commit 命令以后，会提交该消息


<font color="red">&emsp;&emsp; leader 的投票过程，不需要 Observer 的 ack，也就是Observer 不需要参与投票过程，但是 Observer 必须要同步 Leader 的数据从而在处理请求的时候保证数据的一致性</font>
![在这里插入图片描述](https://img-blog.csdnimg.cn/20190216215846544.png)
### 1.2 崩溃恢复
&emsp;&emsp;ZAB 协议的这个基于原子广播协议的消息广播过程，在正常情况下是没有任何问题的，但是**一旦 Leader 节点崩溃，或者由于网络问题导致 Leader 服务器失去了过半Follower 节点的联系**（leader 失去与过半 follower 节点联系，可能是 leader 节点和 follower 节点之间产生了网络分区，那么此时的 leader 不再是合法的 leader 了），那么就会进入到崩溃恢复模式。在 ZAB 协议中，为了保证程序的正确运行，整个恢复过程结束后需要选举出一个新的Leader。

为了使 leader 挂了后系统能正常工作，需要解决以下**两个问题**:

- <font color="red">1. 已经被处理的消息不能丢失</font>
当 leader 收到合法数量 follower 的 ACKs 后，就向各个 follower 广播 COMMIT 命令，同时也会在本地执行 COMMIT 并向连接的客户端返回「成功」。但是如果在各个 follower 在收到 COMMIT 命令前 leader就挂了，导致剩下的服务器并没有执行都这条消息。

如图：
![在这里插入图片描述](https://img-blog.csdnimg.cn/20190216220529633.png)
&emsp;&emsp;leader 对事务消息发起 commit 操作，但是该消息在follower1 上执行了，但是 follower2 还没有收到 commit，就已经挂了，而实际上客户端已经收到该事务消息处理成功的回执了。所以在 zab 协议下需要保证所有机器都要执行这个事务消息。


- <font color="red"> 2. 被丢弃的消息不能再次出现</font>
&emsp;&emsp;当 leader 接收到消息请求生成 proposal 后就挂了，其他 follower 并没有收到此 proposal，因此经过恢复模式重新选了 leader 后，这条消息是被跳过的。 此时，之前挂了的 leader 重新启动并注册成了 follower，他保留了被跳过消息的 proposal 状态，与整个系统的状态是不一致的，需要将其删除。

&emsp;&emsp;ZAB 协议需要满足上面两种情况，就必须要设计**一个leader 选举算法**：能够确保已经被 leader 提交的事务Proposal能够提交、同时丢弃已经被跳过的事务Proposal。针对这个要求：

 &emsp;&emsp;<font color="red">如果 leader 选举算法能够保证新选举出来的 Leader 服务器拥有集群中所有机器最高编号（ZXID 最大）的事务Proposal，那么就可以保证这个新选举出来的 Leader 一定具有已经提交的提案。</font>因为所有提案被 COMMIT 之前必须有超过半数的 follower ACK，即必须有超过半数节点的服务器的事务日志上有该提案的 proposal，因此，只要有合法数量的节点正常工作，就必然有一个节点保存了所有被 COMMIT 消息的 proposal 状态

&emsp;&emsp;另外一个，zxid 是 64 位，高 32 位是 epoch 编号，每经过一次 Leader 选举产生一个新的 leader，新的 leader 会将epoch 号+1，低 32 位是消息计数器，每接收到一条消息这个值+1，新 leader 选举后这个值重置为 0.这样设计的好处在于老的leader挂了以后重启，它不会被选举为leader，因此此时它的 zxid 肯定小于当前新的 leader。当老的leader 作为 follower 接入新的 leader 后，新的 leader 会让它将所有的拥有旧的 epoch 号的未被 COMMIT 的proposal 清除。



> **关于 ZXID** 
> &emsp;&emsp;zxid，也就是事务 id，为了保证事务的顺序一致性，zookeeper 采用了递增的事 务 id 号（zxid）来标识事务。所有的提议（proposal）都在被提出的时候加上了 zxid。实现中 zxid 是一个 64 位的数字，它高32 位是 epoch（ZAB 协议通过 epoch 编号来区分 Leader 周期变化的策略）用来标识 leader关系是否改变，每次一个 leader 被选出来，它都会有一个新的epoch=（原来的 epoch+1），标识当前属于那个 leader 的统治时期。低 32 位用于递增计数。

    epoch 的变化可以做一个简单的实验:
    1. 启动一个 zookeeper 集群。
    2. 在/tmp/zookeeper/VERSION-2 路径下会看到一个currentEpoch 文件。文件中显示的是当前的 epoch
    3. 把 leader 节点停机，这个时候在看 currentEpoch 会有变化。 随着每次选举新的 leader，epoch 都会发生变化


## 2. 选举

&emsp;&emsp;Leader 选举会分两个过程：启动的时候的 leader 选举、 leader 崩溃的时候的的选举。

服务器状态：

- LOOKING:寻找Leader状态。当服务器进入这个状态，表明当前集群中没有leader，因此需要进入leader选举流程；
- FOLLOWING：跟随者状态。表明当前服务器角色是Follower；
- LEADING：领导者状态。表明当前服务器角色是Leader；
- OBSERVING：领导者状态。表明当前服务器角色是Observer


### 2.1 启动时的leader选举

&emsp;&emsp;每个节点启动的时候状态都是 LOOKING，处于观望状态，接下来就开始进行选主流程
进行 Leader 选举，至少需要两台机器，我们选取 3 台机器组成的服务器集群为例。在集
群初始化阶段，当有一台服务器 Server1 启动时，它本身是无法进行和完成 Leader 选举，当第二台服务器 Server2 启动时，这个时候两台机器可以相互通信，每台机器都试图找到 Leader，于是进入 Leader 选举过程。选举过程如下：

- (1) 每个 Server 发出一个投票。由于是初始情况，Server1和 Server2 都会将自己作为 Leader 服务器来进行投票，每次投票会包含所推举的服务器的myid和ZXID、epoch，使用(myid, ZXID,epoch)来表示，此时 Server1的投票为(1, 0)，Server2 的投票为(2, 0)，然后各自将这个投票发给集群中其他机器。
- (2) 接受来自各个服务器的投票。集群的每个服务器收到投票后，首先判断该投票的有效性，如检查是否是本轮投票（epoch）、是否来自LOOKING状态的服务器。
- (3) 处理投票。针对每一个投票，服务器都需要将别人的投票和自己的投票进行 PK，PK 规则如下
     - i. 优先检查 ZXID。ZXID 比较大的服务器优先作为Leader

     - ii. 如果 ZXID 相同，那么就比较 myid。myid 较大的服务器作为 Leader 服务器。
     

>   &emsp;&emsp;对于 Server1 而言，它的投票是(1, 0)，接收 Server2的投票为(2, 0)，首先会比较两者的ZXID，均为 0，再比较 myid，此时 Server2 的 myid 最大，于是更新自己的投票为(2,0)，然后重新投票，对于Server2而言，它不需要更新自己的投票，只是再次向集群中所有机器发出上一次投票信息即可。

- (4) 统计投票。每次投票后，服务器都会统计投票信息，判断是否已经有过半机器接受到相同的投票信息，对于 Server1、Server2 而言，都统计出集群中已经有两台机器接受了(2, 0)的投票信息，此时便认为已经选出了 Leader。
- (5) 改变服务器状态。一旦确定了 Leader，每个服务器就会更新自己的状态，如果是 Follower，那么就变更为FOLLOWING，如果是 Leader，就变更为 LEADING。

### 2.1 服务运行期间的Leader选举
&emsp;&emsp;当集群中的 leader 服务器出现宕机或者不可用的情况时，那么整个集群将无法对外提供服务，而是进入新一轮的Leader 选举，服务器运行期间的 Leader 选举和启动时期的 Leader 选举基本过程是一致的。

- (1) 变更状态。Leader 挂后，余下的非 Observer 服务器都会将自己的服务器状态变更为 LOOKING，然后开始进入 Leader 选举过程。
- (2) 每个 Server 会发出一个投票。在运行期间，每个服务器上的 ZXID 可能不同，此时假定 Server1 的 ZXID 为123，Server3的ZXID为122；在第一轮投票中，Server1
和 Server3 都会投自己，产生投票(1, 123)，(3, 122)，然后各自将投票发送给集群中所有机器。接收来自各个服务器的投票。与启动时过程相同。
- (3) 处理投票。与启动时过程相同，此时，Server1 将会成为 Leader。
- (4) 统计投票。与启动时过程相同。
- (5) 改变服务器的状态。与启动时过程相同


## 3. Leader选举源码

### 3.1 FastLeaderElection 选举过程

其实在这个投票过程中就涉及到几个类：
- FastLeaderElection：FastLeaderElection实现了Election接口，实现各服务器之间基于 TCP 协议进行选举
- Notification：内部类，Notification 表示收到的选举投票信息（其他服务器发来的选举投票信息），其包含了被选举者的 id、zxid、选举周期等信息
- ToSend：ToSend表示发送给其他服务器的选举投票信息，也包含了被选举者的 id、zxid、选举周期等信息
- Messenger ： Messenger 包 含 了 WorkerReceiver 和WorkerSender 两个内部类；
- WorkerReceiver实现了 Runnable 接口，是选票接收器。其会不断地从 QuorumCnxManager 中获取其他服务器发来的选举消息，并将其转换成一个选票，然后保存到recvqueue 中
- WorkerSender 也实现了 Runnable 接口，为选票发送器，其会不断地从 sendqueue 中获取待发送的选票，并将其传递到底层 QuorumCnxManager 中

### 3.2 从QuorumPeerMain类开始
```java
public static void main(String[] args) {
        QuorumPeerMain main = new QuorumPeerMain();

        try {
            main.initializeAndRun(args); //这个初始化
        } catch (IllegalArgumentException var3) {
            LOG.error("Invalid arguments, exiting abnormally", var3);
            LOG.info("Usage: QuorumPeerMain configfile");
            System.err.println("Usage: QuorumPeerMain configfile");
            System.exit(2);
        } catch (ConfigException var4) {
            LOG.error("Invalid config, exiting abnormally", var4);
            System.err.println("Invalid config, exiting abnormally");
            System.exit(2);
        } catch (Exception var5) {
            LOG.error("Unexpected exception, exiting abnormally", var5);
            System.exit(1);
        }

        LOG.info("Exiting normally");
        System.exit(0);
    }


 protected void initializeAndRun(String[] args) throws ConfigException, IOException {
        QuorumPeerConfig config = new QuorumPeerConfig();
        if (args.length == 1) {
            config.parse(args[0]);
        }

        DatadirCleanupManager purgeMgr = new DatadirCleanupManager(config.getDataDir(), config.getDataLogDir(), config.getSnapRetainCount(), config.getPurgeInterval());
        purgeMgr.start();
        
        //判断是否是standalone模式还是集群模式
        if (args.length == 1 && config.servers.size() > 0) {
            this.runFromConfig(config); //集群模式
        } else {
            LOG.warn("Either no config or no quorum defined in config, running  in standalone mode");
            ZooKeeperServerMain.main(args);
        }

    }

public void runFromConfig(QuorumPeerConfig config) throws IOException {
        try {
            ManagedUtil.registerLog4jMBeans();
        } catch (JMException var4) {
            LOG.warn("Unable to register log4j JMX control", var4);
        }

        LOG.info("Starting quorum peer");

        try {
        //为客户端提供读写的server,也就是2181这个端口的访问能力
            ServerCnxnFactory cnxnFactory = ServerCnxnFactory.createFactory();
            cnxnFactory.configure(config.getClientPortAddress(), config.getMaxClientCnxns());
            //设置各种参数
            this.quorumPeer = new QuorumPeer(config.getServers(), new File(config.getDataDir()), new File(config.getDataLogDir()), config.getElectionAlg(), config.getServerId(), config.getTickTime(), config.getInitLimit(), config.getSyncLimit(), config.getQuorumListenOnAllIPs(), cnxnFactory, config.getQuorumVerifier());
            this.quorumPeer.setClientPortAddress(config.getClientPortAddress());
            this.quorumPeer.setMinSessionTimeout(config.getMinSessionTimeout());
            this.quorumPeer.setMaxSessionTimeout(config.getMaxSessionTimeout());
            this.quorumPeer.setZKDatabase(new ZKDatabase(this.quorumPeer.getTxnFactory()));
            this.quorumPeer.setLearnerType(config.getPeerType());
            this.quorumPeer.setSyncEnabled(config.getSyncEnabled());
            this.quorumPeer.setQuorumSaslEnabled(config.quorumEnableSasl);
            if (this.quorumPeer.isQuorumSaslAuthEnabled()) {
                this.quorumPeer.setQuorumServerSaslRequired(config.quorumServerRequireSasl);
                this.quorumPeer.setQuorumLearnerSaslRequired(config.quorumLearnerRequireSasl);
                this.quorumPeer.setQuorumServicePrincipal(config.quorumServicePrincipal);
                this.quorumPeer.setQuorumServerLoginContext(config.quorumServerLoginContext);
                this.quorumPeer.setQuorumLearnerLoginContext(config.quorumLearnerLoginContext);
            }

            this.quorumPeer.setQuorumCnxnThreadsSize(config.quorumCnxnThreadsSize);
            this.quorumPeer.initialize();
            //启动主线程
            this.quorumPeer.start();
            this.quorumPeer.join();
        } catch (InterruptedException var3) {
            LOG.warn("Quorum Peer interrupted", var3);
        }

    }
```

### 3.3 调用QuorumPeer.start方法

```java
	public synchronized void start() {
        this.loadDataBase(); //恢复DB
        this.cnxnFactory.start();
        this.startLeaderElection();//选举初始化
        super.start();
    }


```
**恢复DB**
```java
	//恢复DB
	private void loadDataBase() {
        File updating = new File(this.getTxnFactory().getSnapDir(), "updatingEpoch");

        try {
            this.zkDb.loadDataBase();//从本地文件恢复DB
            //从最新的zxid恢复epoch变量，zxid64位，前32位是epoch值，后32位是zxid值
            long lastProcessedZxid = this.zkDb.getDataTree().lastProcessedZxid;
            long epochOfZxid = ZxidUtils.getEpochFromZxid(lastProcessedZxid);

            try {
                //都群当前的epoch值
                this.currentEpoch = this.readLongFromFile("currentEpoch");
                if (epochOfZxid > this.currentEpoch && updating.exists()) {
                    LOG.info("{} found. The server was terminated after taking a snapshot but before updating current epoch. Setting current epoch to {}.", "updatingEpoch", epochOfZxid);
                    this.setCurrentEpoch(epochOfZxid);
                    if (!updating.delete()) {
                        throw new IOException("Failed to delete " + updating.toString());
                    }
                }
            } catch (FileNotFoundException var8) {
                this.currentEpoch = epochOfZxid;
                LOG.info("currentEpoch not found! Creating with a reasonable default of {}. This should only happen when you are upgrading your installation", this.currentEpoch);
                this.writeLongToFile("currentEpoch", this.currentEpoch);
            }

            if (epochOfZxid > this.currentEpoch) {
                throw new IOException("The current epoch, " + ZxidUtils.zxidToString(this.currentEpoch) + ", is older than the last zxid, " + lastProcessedZxid);
            } else {
                try {
                    this.acceptedEpoch = this.readLongFromFile("acceptedEpoch");
                } catch (FileNotFoundException var7) {
                    this.acceptedEpoch = epochOfZxid;
                    LOG.info("acceptedEpoch not found! Creating with a reasonable default of {}. This should only happen when you are upgrading your installation", this.acceptedEpoch);
                    this.writeLongToFile("acceptedEpoch", this.acceptedEpoch);
                }

                if (this.acceptedEpoch < this.currentEpoch) {
                    throw new IOException("The accepted epoch, " + ZxidUtils.zxidToString(this.acceptedEpoch) + " is less than the current epoch, " + ZxidUtils.zxidToString(this.currentEpoch));
                }
            }
        } catch (IOException var9) {
            LOG.error("Unable to load database on disk", var9);
            throw new RuntimeException("Unable to run quorum server ", var9);
        }
    }


```



**选举初始化**

```java
	//选举初始化
    public synchronized void startLeaderElection() {
        try {
        	//投票给自己
            this.currentVote = new Vote(this.myid, this.getLastLoggedZxid(), this.getCurrentEpoch());
        } catch (IOException var4) {
            RuntimeException re = new RuntimeException(var4.getMessage());
            re.setStackTrace(var4.getStackTrace());
            throw re;
        }

        Iterator i$ = this.getView().values().iterator();

        while(i$.hasNext()) {
            QuorumPeer.QuorumServer p = (QuorumPeer.QuorumServer)i$.next();
            if (p.id == this.myid) {
                this.myQuorumAddr = p.addr;
                break;
            }
        }

        if (this.myQuorumAddr == null) {
            throw new RuntimeException("My id " + this.myid + " not in the peer list");
        } else {
            if (this.electionType == 0) {
                try {
                    this.udpSocket = new DatagramSocket(this.myQuorumAddr.getPort());
                    this.responder = new QuorumPeer.ResponderThread();
                    this.responder.start();
                } catch (SocketException var3) {
                    throw new RuntimeException(var3);
                }
            }
			//根据配置获取选举算法。
            this.electionAlg = this.createElectionAlgorithm(this.electionType);
        }
    }

//配置选举算法，选举算法有 3 种，可以通过在 zoo.cfg 里面进行配置，默认是 fast 选举
  protected Election createElectionAlgorithm(int electionAlgorithm) {
        Election le = null;
        switch(electionAlgorithm) {
        case 0:
            le = new LeaderElection(this);
            break;
        case 1:
            le = new AuthFastLeaderElection(this);
            break;
        case 2:
            le = new AuthFastLeaderElection(this, true);
            break;
        case 3:
            this.qcm = this.createCnxnManager();
            Listener listener = this.qcm.listener;
            if (listener != null) {
            	//启动已绑定端口的选举线程，等待集群中其他j机器链接，
            	//基于TCP的选举算法
                listener.start();
                le = new FastLeaderElection(this, this.qcm);
            } else {
                LOG.error("Null listener when initializing cnx manager");
            }
            break;
        default:
            assert false;
        }

        return (Election)le;
    }


  //继续看 FastLeaderElection 的初始化动作，主要初始化了业务层的发送队列和接收队列
	public FastLeaderElection(QuorumPeer self, QuorumCnxManager manager){
        this.stop = false;
        this.manager = manager;
        starter(self, manager);
    }
     //starter方法
	 private void starter(QuorumPeer self, QuorumCnxManager manager) {
        this.self = self;
        proposedLeader = -1;
        proposedZxid = -1;
		//业务层发送队列,业务对象ToSend
        sendqueue = new LinkedBlockingQueue<ToSend>();
        //业务层接受队列，业务对象Notification
        recvqueue = new LinkedBlockingQueue<Notification>();
        this.messenger = new Messenger(manager);
    }
    //再看上面new Messenger(manager)的构造方法
     Messenger(QuorumCnxManager manager) {
			//以后台运行启动发送线程，将消息发送给IO负责类QuorumCnxManager 
            this.ws = new WorkerSender(manager); //内部类

            Thread t = new Thread(this.ws,
                    "WorkerSender[myid=" + self.getId() + "]");
            t.setDaemon(true);
            t.start();
			//以后台运行启动接受线程，从IO负责类QuorumCnxManager 接受消息
            this.wr = new WorkerReceiver(manager);//内部类

            t = new Thread(this.wr,
                    "WorkerReceiver[myid=" + self.getId() + "]");
            t.setDaemon(true);
            t.start();
        }

```

**然后再回到 QuorumPeer.java。 FastLeaderElection 初始化完成以后，调用 super.start()，最终运行 QuorumPeer 的run 方法**

```java
public void run() {
        setName("QuorumPeer" + "[myid=" + getId() + "]" +
                cnxnFactory.getLocalAddress());

        LOG.debug("Starting quorum peer");
        try {
        	//此处通过JMX来监控一些属性
            jmxQuorumBean = new QuorumBean(this);
            MBeanRegistry.getInstance().register(jmxQuorumBean, null);
            for(QuorumServer s: getView().values()){
                ZKMBeanInfo p;
                if (getId() == s.id) {
                    p = jmxLocalPeerBean = new LocalPeerBean(this);
                    try {
                        MBeanRegistry.getInstance().register(p, jmxQuorumBean);
                    } catch (Exception e) {
                        LOG.warn("Failed to register with JMX", e);
                        jmxLocalPeerBean = null;
                    }
                } else {
                    p = new RemotePeerBean(s);
                    try {
                        MBeanRegistry.getInstance().register(p, jmxQuorumBean);
                    } catch (Exception e) {
                        LOG.warn("Failed to register with JMX", e);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to register with JMX", e);
            jmxQuorumBean = null;
        }

        try {
            /*
             * Main loop 重要代码在这里
             */
            while (running) { 
                switch (getPeerState()) {//判断当前节点状态
                case LOOKING:  //如果是LOOKING，则进入选举流程
                    LOG.info("LOOKING");

                    if (Boolean.getBoolean("readonlymode.enabled")) {
                        LOG.info("Attempting to start ReadOnlyZooKeeperServer");

                        // Create read-only server but don't start it immediately
                        final ReadOnlyZooKeeperServer roZk = new ReadOnlyZooKeeperServer(
                                logFactory, this,
                                new ZooKeeperServer.BasicDataTreeBuilder(),
                                this.zkDb);
    
                        // Instead of starting roZk immediately, wait some grace
                        // period before we decide we're partitioned.
                        //
                        // Thread is used here because otherwise it would require
                        // changes in each of election strategy classes which is
                        // unnecessary code coupling.
                        Thread roZkMgr = new Thread() {
                            public void run() {
                                try {
                                    // lower-bound grace period to 2 secs
                                    sleep(Math.max(2000, tickTime));
                                    if (ServerState.LOOKING.equals(getPeerState())) {
                                        roZk.startup();
                                    }
                                } catch (InterruptedException e) {
                                    LOG.info("Interrupted while attempting to start ReadOnlyZooKeeperServer, not started");
                                } catch (Exception e) {
                                    LOG.error("FAILED to start ReadOnlyZooKeeperServer", e);
                                }
                            }
                        };
                        try {
                            roZkMgr.start();
                            setBCVote(null);
                            setCurrentVote(makeLEStrategy().lookForLeader());
                        } catch (Exception e) {
                            LOG.warn("Unexpected exception",e);
                            setPeerState(ServerState.LOOKING);
                        } finally {
                            // If the thread is in the the grace period, interrupt
                            // to come out of waiting.
                            roZkMgr.interrupt();
                            roZk.shutdown();
                        }
                    } else {
                        try {
                            setBCVote(null);
							//调用setCurrentVote(makeLEStrategy().lookForLeader());，最终根据策略应该运行 FastLeaderElection 中的选举算法													
                            //此处通过策略模式来决定当前用哪个选举算法来进行领导选举
                            setCurrentVote(makeLEStrategy().lookForLeader());
                        } catch (Exception e) {
                            LOG.warn("Unexpected exception", e);
                            setPeerState(ServerState.LOOKING);
                        }
                    }
                    break;
               
               ......




//FastLeaderElection 的  LOOKFORLEADER 开始选举
public Vote lookForLeader() throws InterruptedException {
        try {
            self.jmxLeaderElectionBean = new LeaderElectionBean();
            MBeanRegistry.getInstance().register(
                    self.jmxLeaderElectionBean, self.jmxLocalPeerBean);
        } catch (Exception e) {
            LOG.warn("Failed to register with JMX", e);
            self.jmxLeaderElectionBean = null;
        }
        if (self.start_fle == 0) {
           self.start_fle = System.currentTimeMillis();
        }
        try {
        	//收到的投票结果
            HashMap<Long, Vote> recvset = new HashMap<Long, Vote>();
			//存储选举结果
            HashMap<Long, Vote> outofelection = new HashMap<Long, Vote>();

            int notTimeout = finalizeWait;

            synchronized(this){
                logicalclock++; //增加逻辑时钟
                //更新自己的zxid,epoch,myid
                updateProposal(getInitId(), getInitLastLoggedZxid(), getPeerEpoch());
            }

            LOG.info("New election. My id =  " + self.getId() +
                    ", proposed zxid=0x" + Long.toHexString(proposedZxid));
            sendNotifications();//发送通知，包括发送给自己

            /*
             * Loop in which we exchange notifications until we find a leader
             */

            while ((self.getPeerState() == ServerState.LOOKING) &&
                    (!stop)){ //主循环，知道选举出leader
                /*
                 * Remove next notification from queue, times out after 2 times
                 * the termination time
                 * 从IO线程里拿到投票消息，自己的投票也在这里
                 */
                Notification n = recvqueue.poll(notTimeout,
                        TimeUnit.MILLISECONDS);

                /*
                 * Sends more notifications if haven't received enough.
                 * Otherwise processes new notification.
                 */
                if(n == null){
                	//如果通知为null，继续发送，直到选出leader为止
                    if(manager.haveDelivered()){
                        sendNotifications();
                    } else {
                    	//消息还没有发送出去，可能是server还没有启动，尝试在连接
                        manager.connectAll();
                    }

                    /*
                     * Exponential backoff 延长超时时间 *2
                     */
                    int tmpTimeOut = notTimeout*2;
                    notTimeout = (tmpTimeOut < maxNotificationInterval?
                            tmpTimeOut : maxNotificationInterval);
                    LOG.info("Notification time out: " + notTimeout);
                }
              
                else if(self.getVotingView().containsKey(n.sid)) {
                    /*
                     * Only proceed if the vote comes from a replica in the
                     * voting view.
                     * 收到了投票消息，判断是否属于当前这个集群内
                     */
                    switch (n.state) {
                    case LOOKING:
                        // If notification > current, replace and send messages out
                        //判断接受到的节点epoch是否大于logicalclock
                        if (n.electionEpoch > logicalclock) {
                            logicalclock = n.electionEpoch;
                            recvset.clear();//清空接收队列
                            //检查收到的这个消息是否胜出一次性比较epoch，zxid，myid
                            if(totalOrderPredicate(n.leader, n.zxid, n.peerEpoch,
                                    getInitId(), getInitLastLoggedZxid(), getPeerEpoch())) {				
                                //胜出后，改投对方的票
                                updateProposal(n.leader, n.zxid, n.peerEpoch);
                            } else {//否则票据不变
                                updateProposal(getInitId(),
                                        getInitLastLoggedZxid(),
                                        getPeerEpoch());
                            }
                            sendNotifications();//继续广播消息，让其他节点知道我现在的投票					
                        //如果收到的消息的epoch小于当前的的epoch，则忽略当前的消息    
                        } else if (n.electionEpoch < logicalclock) {
                            if(LOG.isDebugEnabled()){
                                LOG.debug("Notification election epoch is smaller than logicalclock. n.electionEpoch = 0x"
                                        + Long.toHexString(n.electionEpoch)
                                        + ", logicalclock=0x" + Long.toHexString(logicalclock));
                            }
                            break;
                         //如果epoch相同的话，机继续比较zxid和myi，如果胜出，则更新自己的投票，发出广播   
                        } else if (totalOrderPredicate(n.leader, n.zxid, n.peerEpoch,
                                proposedLeader, proposedZxid, proposedEpoch)) {
                            updateProposal(n.leader, n.zxid, n.peerEpoch);
                            sendNotifications();
                        }

                        if(LOG.isDebugEnabled()){
                            LOG.debug("Adding vote: from=" + n.sid +
                                    ", proposed leader=" + n.leader +
                                    ", proposed zxid=0x" + Long.toHexString(n.zxid) +
                                    ", proposed election epoch=0x" + Long.toHexString(n.electionEpoch));
                        }
						//添加到本地投票集合，用来做选举判断							
                        recvset.put(n.sid, new Vote(n.leader, n.zxid, n.electionEpoch, n.peerEpoch));
						//判断选举是否结束，默认算法是超过半数server同意
                        if (termPredicate(recvset,
                                new Vote(proposedLeader, proposedZxid,
                                        logicalclock, proposedEpoch))) {

                            // Verify if there is any change in the proposed leader
                          //一直等新的notification到达，直到超时
                            while((n = recvqueue.poll(finalizeWait,
                                    TimeUnit.MILLISECONDS)) != null){
                                if(totalOrderPredicate(n.leader, n.zxid, n.peerEpoch,
                                        proposedLeader, proposedZxid, proposedEpoch)){
                                    recvqueue.put(n);
                                    break;
                                }
                            }

                            /*
                             * This predicate is true once we don't read any new
                             * relevant message from the reception queue
                             */
                             //确定leader
                            if (n == null) {
                            	//修改状态是LEADING或者FOLLOWING
                                self.setPeerState((proposedLeader == self.getId()) ?		
                                        ServerState.LEADING: learningState());
								//返回最终投票结果
                                Vote endVote = new Vote(proposedLeader,
                                                        proposedZxid,
                                                        logicalclock,
                                                        proposedEpoch);
                                leaveInstance(endVote);
                                return endVote;
                            }
                        }
                        break;
                   //如果收到的选举状态不是LOOKING，比如这台机器刚加入一个已经正在运行的zk集群时
                   case OBSERVING:	//OBSERVING不参与投票
                        LOG.debug("Notification from observer: " + n.sid);
                        break;
                    case FOLLOWING://这两种也需要参与投票
                    case LEADING:
                        /*
                         * Consider all notifications from the same epoch
                         * together.
                         * 判断epoch是否相同
                         */
                        if(n.electionEpoch == logicalclock){
                        	//加入到本机的投票集合
                            recvset.put(n.sid, new Vote(n.leader,
                                                          n.zxid,
                                                          n.electionEpoch,
                                                          n.peerEpoch));
                           //投票是否结束，如果结束，再确认leader是否有效，如果结束，修改自己的状态并返回投票结果
                            if(ooePredicate(recvset, outofelection, n)) {
                                self.setPeerState((n.leader == self.getId()) ?
                                        ServerState.LEADING: learningState());

                                Vote endVote = new Vote(n.leader, 
                                        n.zxid, 
                                        n.electionEpoch, 
                                        n.peerEpoch);
                                leaveInstance(endVote);
                                return endVote;
                            }
                        }

                        /*
                         * Before joining an established ensemble, verify
                         * a majority is following the same leader.
                         */
                        outofelection.put(n.sid, new Vote(n.version,
                                                            n.leader,
                                                            n.zxid,
                                                            n.electionEpoch,
                                                            n.peerEpoch,
                                                            n.state));
           
                        if(ooePredicate(outofelection, outofelection, n)) {
                            synchronized(this){
                                logicalclock = n.electionEpoch;
                                self.setPeerState((n.leader == self.getId()) ?
                                        ServerState.LEADING: learningState());
                            }
                            Vote endVote = new Vote(n.leader,
                                                    n.zxid,
                                                    n.electionEpoch,
                                                    n.peerEpoch);
                            leaveInstance(endVote);
                            return endVote;
                        }
                        break;
                    default:
                        LOG.warn("Notification state unrecognized: {} (n.state), {} (n.sid)",
                                n.state, n.sid);
                        break;
                    }
                } else {
                    LOG.warn("Ignoring notification from non-cluster member " + n.sid);
                }
            }
            return null;
        } finally {
            try {
                if(self.jmxLeaderElectionBean != null){
                    MBeanRegistry.getInstance().unregister(
                            self.jmxLeaderElectionBean);
                }
            } catch (Exception e) {
                LOG.warn("Failed to unregister with JMX", e);
            }
            self.jmxLeaderElectionBean = null;
            LOG.debug("Number of connection processing threads: {}",
                    manager.getConnectionThreadCount());
        }
    }




	/**
     * Check if a pair (server id, zxid) succeeds our
     * current vote.
     *
     * @param id    Server identifier
     * @param zxid  Last zxid observed by the issuer of this vote
     */
    protected boolean totalOrderPredicate(long newId, long newZxid, long newEpoch, long curId, long curZxid, long curEpoch) {
        LOG.debug("id: " + newId + ", proposed id: " + curId + ", zxid: 0x" +
                Long.toHexString(newZxid) + ", proposed zxid: 0x" + Long.toHexString(curZxid));
        if(self.getQuorumVerifier().getWeight(newId) == 0){
            return false;
        }
        
        /*
         * We return true if one of the following three cases hold:
         * 1- New epoch is higher
         * 2- New epoch is the same as current epoch, but new zxid is higher
         * 3- New epoch is the same as current epoch, new zxid is the same
         *  as current zxid, but server id is higher.
         * 1.判断消息里的epoch是不是比当前的大，如果大则消息id对应的服务器就是leader
         * 2.如果epoch相等则判断zxid，如果消息里的zxid大，则消息中id对应的服务器就死leader
         * 3.如果前面两个都相等就比较服务器id，如果大，则其就是leader
         */
        
        return ((newEpoch > curEpoch) || 
                ((newEpoch == curEpoch) &&
                ((newZxid > curZxid) || ((newZxid == curZxid) && (newId > curId)))));
    }




/**
     * Termination predicate. Given a set of votes, determines if
     * have sufficient to declare the end of the election round.
     *
     *  @param votes    Set of votes
     *  @param l        Identifier of the vote received last
     *  @param zxid     zxid of the the vote received last
     */
    protected boolean termPredicate(
            HashMap<Long, Vote> votes,
            Vote vote) {

        HashSet<Long> set = new HashSet<Long>();

        /*
         * First make the views consistent. Sometimes peers will have
         * different zxids for a server depending on timing.
         * 遍历已经接受到的投票集合，把等于当前的投票项放入set中
         */
        for (Map.Entry<Long,Vote> entry : votes.entrySet()) {
            if (vote.equals(entry.getValue())){
                set.add(entry.getKey());
            }
        }
		//统计集合，看集合投某个id的票数是否超过一半
        return self.getQuorumVerifier().containsQuorum(set);
    }
```

### 3.4 消息如何广播
**FastLeaderElection中的sendNotification方法**
```java
/**
 * Send notifications to all peers upon a change in our vote
*/
private void sendNotifications() {
    for (QuorumServer server : self.getVotingView().values()) {
        long sid = server.id;

        ToSend notmsg = new ToSend(ToSend.mType.notification,
                                   proposedLeader,
                                   proposedZxid,
                                   logicalclock,
                                   QuorumPeer.ServerState.LOOKING,
                                   sid,
                                   proposedEpoch);
        if(LOG.isDebugEnabled()){
            LOG.debug("Sending Notification: " + proposedLeader + " (n.leader), 0x"  +
                      Long.toHexString(proposedZxid) + " (n.zxid), 0x" + Long.toHexString(logicalclock)  +
                      " (n.round), " + sid + " (recipient), " + self.getId() +
                      " (myid), 0x" + Long.toHexString(proposedEpoch) + " (n.peerEpoch)");
        }
        sendqueue.offer(notmsg);//添加到发送队列，这个队列会被workerSender消费
    }
}

```

**FastLeaderElection内部类 WorkerSender**

```java
class WorkerSender extends ZooKeeperThread {
    volatile boolean stop;
    QuorumCnxManager manager;

    WorkerSender(QuorumCnxManager manager){
        super("WorkerSender");
        this.stop = false;
        this.manager = manager;
    }

    public void run() {
        while (!stop) {
            try {
                //从发送队列中取出消息实体
                ToSend m = sendqueue.poll(3000, TimeUnit.MILLISECONDS);
                if(m == null) continue;
                //发送消息
                process(m);
            } catch (InterruptedException e) {
                break;
            }
        }
        LOG.info("WorkerSender is down");
    }

    /**
             * Called by run() once there is a new message to send.
             *
             * @param m     message to send
             */
    void process(ToSend m) {
        ByteBuffer requestBuffer = buildMsg(m.state.ordinal(), 
                                            m.leader,
                                            m.zxid, 
                                            m.electionEpoch, 
                                            m.peerEpoch);
        manager.toSend(m.sid, requestBuffer);
    }
}



/**
     * Processes invoke this message to queue a message to send. Currently, 
     * only leader election uses it.
     */
public void toSend(Long sid, ByteBuffer b) {
    /*
         * If sending message to myself, then simply enqueue it (loopback).
         */
    if (this.mySid == sid) {//如果是自己，就不走网络发送，直接添加到本地接受队列
        b.position(0);
        addToRecvQueue(new Message(b.duplicate(), sid));
        /*
             * Otherwise send to the corresponding thread to send.
             */
    } else {
        /*
              * Start a new connection if doesn't have one already.
              * 发送给别的节点，判断是否发送过
              */

        if (!queueSendMap.containsKey(sid)) {
            ArrayBlockingQueue<ByteBuffer> bq = new ArrayBlockingQueue<ByteBuffer>(
                SEND_CAPACITY);
            queueSendMap.put(sid, bq);
            addToSendQueue(bq, b);

        } else {
            ArrayBlockingQueue<ByteBuffer> bq = queueSendMap.get(sid);
            if(bq != null){
                addToSendQueue(bq, b);
            } else {
                LOG.error("No queue for server " + sid);
            }
        }
        //真正的发送了逻辑
        connectOne(sid);

    }
}
```