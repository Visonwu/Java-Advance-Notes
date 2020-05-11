# nacos集群

Nacos支持集群模式，很显然。 
	而一旦涉及到集群，就涉及到主从，那么nacos是一种什么样的机制来实现的集群呢？ nacos的集群类似于zookeeper， 它分为leader角色和follower角色， 那么从这个角色的名字可以看出来，这个集群存在选举的机制。 因为如果自己不具备选举功能，角色的命名可能就是master/slave了， 当然这只是我基于这么多组件的命名的一个猜测

## 1. 选举算法

​	使用**raft算法**协议保证数据一致性（和zookeeper一样弱一致性），以及相关的leader选举

raft参考：<http://thesecretlivesofdata.com/raft/>

- leader
- follower
- candidate选举

**选举分为两个节点**

- 服务启动的时候 
- leader挂了的时候

​		所有节点启动的时候，都是follower状态。 如果在一段时间内如果没有收到leader的心跳（可能是没有 leader，也可能是leader挂了），那么follower会变成Candidate。然后发起选举，选举之前，会增加 term，这个term和zookeeper中的epoch的道理是一样的。 

- follower会投自己一票，并且给其他节点发送票据vote，等到其他节点回复 
- 在这个过程中，可能出现几种情况 收到过半的票数通过，则成为leader 被告知其他节点已经成为leader，则自己切换为follower 一段时间内没有收到过半的投票，则重新发起选举 
- **约束条件在任一term中，单个节点最多只能投一票**

**选举的几种情况**

- 第一种情况，赢得选举之后，leader会给所有节点发送消息，避免其他节点触发新的选举 
- 第二种情况，比如有三个节点A B C。A B同时发起选举，而A的选举消息先到达C，C给A投了一 票，当B的消息到达C时，已经不能满足上面提到的第一个约束，即C不会给B投票，而A和B显然都 不会给对方投票。A胜出之后，会给B,C发心跳消息，节点B发现节点A的term不低于自己的term， 知道有已经有Leader了，于是转换成follower 
- 第三种情况， 没有任何节点获得majority投票，可能是平票的情况。加入总共有四个节点 （A/B/C/D），Node C、Node D同时成为了candidate，但Node A投了NodeD一票，NodeB投 了Node C一票，这就出现了平票 split vote的情况。这个时候大家都在等啊等，直到超时后重新发 起选举。如果出现平票的情况，那么就延长了系统不可用的时间,因此raft引入了randomized election timeouts来尽量避免平票情况



**源码分析：**

Server节点数据获取是通过ServerListManager从文件和内存数据读取以及比较来的

```java
public class ServerListManager{    
	@PostConstruct
    public void init() {
        GlobalExecutor.registerServerListUpdater(new ServerListUpdater());
        GlobalExecutor.registerServerStatusReporter(new ServerStatusReporter(), 2000);
    }
}
```

下面是Raft核心算法

```java
public class RaftCore {
    
        @PostConstruct
    public void init() throws Exception {

        Loggers.RAFT.info("initializing Raft sub-system");

        executor.submit(notifier);

        long start = System.currentTimeMillis();

        raftStore.loadDatums(notifier, datums);
			
        setTerm(NumberUtils.toLong(raftStore.loadMeta().getProperty("term"), 0L));
		....
		//这里是一个定时任务负责master的选举
        GlobalExecutor.registerMasterElection(new MasterElection());
        //这个负责定时心跳处理，服务数据，待分析 todo
        GlobalExecutor.registerHeartbeat(new HeartBeat());

        Loggers.RAFT.info("timer started: leader timeout ms: {}, heart-beat timeout ms: {}",
            GlobalExecutor.LEADER_TIMEOUT_MS, GlobalExecutor.HEARTBEAT_INTERVAL_MS);
    }
    
}



```

**发起选举**

```java
 //Master的选举
 public class MasterElection implements Runnable {
        @Override
        public void run() {
            try {
				//表示peers状态好了，这个是通过listerner监控获取的
                if (!peers.isReady()) {
                    return;
                }

                RaftPeer local = peers.local();
                local.leaderDueMs -= GlobalExecutor.TICK_PERIOD_MS;

                if (local.leaderDueMs > 0) {
                    return;
                }

                // reset timeout
                local.resetLeaderDue();
                local.resetHeartbeatDue();
				//发起选举
                sendVote();
            } catch (Exception e) {
                Loggers.RAFT.warn("[RAFT] error while master election {}", e);
            }

        }

        public void sendVote() {

            RaftPeer local = peers.get(NetUtils.localServer());
            Loggers.RAFT.info("leader timeout, start voting,leader: {}, term: {}",
                JSON.toJSONString(getLeader()), local.term);

            peers.reset();
			//本地的term增加一
            local.term.incrementAndGet();
            //先投给自己
            local.voteFor = local.ip;
            //更新自己的状态
            local.state = RaftPeer.State.CANDIDATE;

            Map<String, String> params = new HashMap<>(1);
            params.put("vote", JSON.toJSONString(local));
            //发给除了自己所有其他服务节点
            for (final String server : peers.allServersWithoutMySelf()) {
                final String url = buildURL(server, API_VOTE);
                try {
                    //会请求RaftController的raft/vote接口做 处理；
                    HttpClient.asyncHttpPost(url, null, params, new AsyncCompletionHandler<Integer>() {
                        @Override
                        public Integer onCompleted(Response response) throws Exception {
                            if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                                Loggers.RAFT.error("NACOS-RAFT vote failed: {}, url: {}", response.getResponseBody(), url);
                                return 1;
                            }

                            RaftPeer peer = JSON.parseObject(response.getResponseBody(), RaftPeer.class);

                            Loggers.RAFT.info("received approve from peer: {}", JSON.toJSONString(peer));
							//拿到投票结果，选举最终的leader
                            peers.decideLeader(peer);

                            return 0;
                        }
                    });
                } catch (Exception e) {
                    Loggers.RAFT.warn("error while sending vote to server: {}", server);
                }
            }
        }
    }
}
```

```java
//Raft节点集
public class RaftPeerSet {
      //投票选出leader
      public RaftPeer decideLeader(RaftPeer candidate) {
        peers.put(candidate.ip, candidate);

        SortedBag ips = new TreeBag();
        int maxApproveCount = 0;
        String maxApprovePeer = null;
        for (RaftPeer peer : peers.values()) {
            if (StringUtils.isEmpty(peer.voteFor)) {
                continue;
            }

            ips.add(peer.voteFor);
            if (ips.getCount(peer.voteFor) > maxApproveCount) {
                maxApproveCount = ips.getCount(peer.voteFor);
                maxApprovePeer = peer.voteFor;
            }
        }
		//当投票数量 总投票节点 过半，将该节点设置为leader
        if (maxApproveCount >= majorityCount()) {
            RaftPeer peer = peers.get(maxApprovePeer);
            peer.state = RaftPeer.State.LEADER;
		
            if (!Objects.equals(leader, peer)) {
                leader = peer;
                //发布事件，leader已经选举完成了
                applicationContext.publishEvent(new LeaderElectFinishedEvent(this, leader));
                Loggers.RAFT.info("{} has become the LEADER", leader.ip);
            }
        }

        return leader;
    }
    
}
```



**RaftPeer**收到投票做处理

```java
public synchronized RaftPeer receivedVote(RaftPeer remote) {
        if (!peers.contains(remote)) {
            throw new IllegalStateException("can not find peer: " + remote.ip);
        }

        RaftPeer local = peers.get(NetUtils.localServer());
    	//如果当前的term大于远程的term，返回本地的给远端作为投票返回
        if (remote.term.get() <= local.term.get()) {
            String msg = "received illegitimate vote" +
                ", voter-term:" + remote.term + ", votee-term:" + local.term;

            Loggers.RAFT.info(msg);
            if (StringUtils.isEmpty(local.voteFor)) {
                local.voteFor = local.ip;
            }

            return local;
        }
		//第一个收到就返回先收到的投票
        local.resetLeaderDue();

        local.state = RaftPeer.State.FOLLOWER;
        local.voteFor = remote.ip;
        local.term.set(remote.term.get());

        Loggers.RAFT.info("vote {} as leader, term: {}", remote.ip, remote.term);

        return local;
    }

```





## 2.数据处理

对于事务操作，请求会转发给leader
非事务操作上，可以任意一个节点来处理
下面这段代码摘自 RaftCore ， 在发布内容的时候，做了两个事情 

- 如果当前的节点不是leader，则转发给leader节点处理 
- 如果是，则向所有节点发送onPublish



RaftCore

```java
    public void signalPublish(String key, Record value) throws Exception {

        if (!isLeader()) {
            JSONObject params = new JSONObject();
            params.put("key", key);
            params.put("value", value);
            Map<String, String> parameters = new HashMap<>(1);
            parameters.put("key", key);

            raftProxy.proxyPostLarge(getLeader().ip, API_PUB, params.toJSONString(), parameters);
            return;
        }

        try {
            OPERATE_LOCK.lock();
            long start = System.currentTimeMillis();
            final Datum datum = new Datum();
            datum.key = key;
            datum.value = value;
            if (getDatum(key) == null) {
                datum.timestamp.set(1L);
            } else {
                datum.timestamp.set(getDatum(key).timestamp.incrementAndGet());
            }

            JSONObject json = new JSONObject();
            json.put("datum", datum);
            json.put("source", peers.local());

            onPublish(datum, peers.local());

            final String content = JSON.toJSONString(json);
            //过半策略
            final CountDownLatch latch = new CountDownLatch(peers.majorityCount());
            for (final String server : peers.allServersIncludeMyself()) {
                if (isLeader(server)) {
                    latch.countDown();
                    continue;
                }
                final String url = buildURL(server, API_ON_PUB);
                HttpClient.asyncHttpPostLarge(url, Arrays.asList("key=" + key), content, new AsyncCompletionHandler<Integer>() {
                    @Override
                    public Integer onCompleted(Response response) throws Exception {
                        if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                            Loggers.RAFT.warn("[RAFT] failed to publish data to peer, datumId={}, peer={}, http code={}",
                                datum.key, server, response.getStatusCode());
                            return 1;
                        }
                        latch.countDown();
                        return 0;
                    }

                    @Override
                    public STATE onContentWriteCompleted() {
                        return STATE.CONTINUE;
                    }
                });

            }

            if (!latch.await(UtilsAndCommons.RAFT_PUBLISH_TIMEOUT, TimeUnit.MILLISECONDS)) {
                // only majority servers return success can we consider this update success
                Loggers.RAFT.error("data publish failed, caused failed to notify majority, key={}", key);
                throw new IllegalStateException("data publish failed, caused failed to notify majority, key=" + key);
            }

            long end = System.currentTimeMillis();
            Loggers.RAFT.info("signalPublish cost {} ms, key: {}", (end - start), key);
        } finally {
            OPERATE_LOCK.unlock();
        }
    }
```



