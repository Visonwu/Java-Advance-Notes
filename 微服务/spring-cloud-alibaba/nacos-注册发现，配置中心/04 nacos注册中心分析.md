**Nacos注册中心**

可以从官网例子入手

```java
 public static void main(String[] args) throws NacosException {

        Properties properties = new Properties();
        properties.setProperty("serverAddr", "127.0.0.1");
        properties.setProperty("namespace", "public");
		
        NamingService naming = NamingFactory.createNamingService(properties);

        naming.registerInstance("nacos.test.3", "11.11.11.11", 8888, "TEST1");
      
        naming.registerInstance("nacos.test.3", "2.2.2.2", 9999, "DEFAULT");

        System.out.println(naming.getAllInstances("nacos.test.3"));

        naming.deregisterInstance("nacos.test.3", "2.2.2.2", 9999, "DEFAULT");

        System.out.println(naming.getAllInstances("nacos.test.3"));

        naming.subscribe("nacos.test.3", new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent)event).getServiceName());
                System.out.println(((NamingEvent)event).getInstances());
            }
        });
 }
```

注意，nacos的存储采用

> We introduce a 'service --> cluster --> instance' model, in which service stores a list of clusters,
>
> service 中包含多个cluster，cluster包含多个instance

# 1.获取NamingService

> 同注册服务一样，都是随机选择一个服务获取实例，如果有一个获取失败，切换下一个实例获取信息

```java
//分析这一块；注册服务
Properties properties = new Properties();
properties.setProperty("serverAddr", "127.0.0.1");
properties.setProperty("namespace", "public");

NamingService naming = NamingFactory.createNamingService(properties);
```

最终会通过反射创建NacosNamingService,然后调用如下代码；

```java

private void init(Properties properties) {
    namespace = InitUtils.initNamespaceForNaming(properties);
    //通过获取集群 server列表，如果endpoint存在，则使用endpoint获取列表，取消自定义的服务列表
    initServerAddr(properties);
    InitUtils.initWebRootContext();
    //启动本地缓存目录文件
    initCacheDir();
    //本地naming.log日志文件
    initLogName(properties);
    //有一个后台处理线程 --事件分发器，变化的服务会添加到服务变化队列中，不停的获取服务分别通知监听该服务的监听器
    //通过阻塞队列实现，阻塞时间是5分钟；com.alibaba.nacos.naming.client.listener 线程名
    eventDispatcher = new EventDispatcher();
    
    //定时任务 30s执行一次；根据endpoint获取更新后server列表，endpoint为空不用执行
    //com.alibaba.nacos.client.naming.serverlist.updater 线程名
    serverProxy = new NamingProxy(namespace, endpoint, serverList);
    serverProxy.setProperties(properties);
    
    //注册服务的时候 做非持久的服务定时检查更新
    //后台线程池 com.alibaba.nacos.naming.beat.sender
    beatReactor = new BeatReactor(serverProxy, initClientBeatThreadCount(properties));
    
    //主要是分为两个：
    //1.failover服务 的持久化处理（故障转移）
    //2.使用 DatagramSocket() 监听服务端的服务变化(这里是服务端推送)，
    //如果有变化会更新本地磁盘和本地服务列表；以及触发服务变化事件EventDispatcher会处理
    hostReactor = new HostReactor(eventDispatcher, serverProxy, cacheDir, isLoadCacheAtStart(properties),
                                  initPollingThreadCount(properties));
}
```



# 2.注册和销毁实例

注册实例

## 2.1 客户端注册操作

```java
public class NacosNamingService{ 	
	@Override
    public void registerInstance(String serviceName, String groupName, Instance instance) throws NacosException {
		//这里是非持久的心跳处理；不然这个步骤就不用走了
        if (instance.isEphemeral()) {
            BeatInfo beatInfo = new BeatInfo();
            beatInfo.setServiceName(NamingUtils.getGroupedName(serviceName, groupName));
            beatInfo.setIp(instance.getIp());
            beatInfo.setPort(instance.getPort());
            beatInfo.setCluster(instance.getClusterName());
            beatInfo.setWeight(instance.getWeight());
            beatInfo.setMetadata(instance.getMetadata());
            beatInfo.setScheduled(false);
            beatInfo.setPeriod(instance.getInstanceHeartBeatInterval());

            beatReactor.addBeatInfo(NamingUtils.getGroupedName(serviceName, groupName), beatInfo);
        }

        serverProxy.registerService(NamingUtils.getGroupedName(serviceName, groupName), groupName, instance);
    }
	....

}
```

注意一点，发送注册实例的时候会**随机发送给某一个服务节点的**

```java
   public String reqAPI(String api, Map<String, String> params, String body, List<String> servers, String method) throws NacosException {

        ....

        NacosException exception = new NacosException();

        if (servers != null && !servers.isEmpty()) {

            Random random = new Random(System.currentTimeMillis());
            int index = random.nextInt(servers.size());

            for (int i = 0; i < servers.size(); i++) {
                String server = servers.get(index);
                try {
                    return callServer(api, params, body, server, method);
                } catch (NacosException e) {
                    exception = e;
                    if (NAMING_LOGGER.isDebugEnabled()) {
                        NAMING_LOGGER.debug("request {} failed.", server, e);
                    }
                }
                index = (index + 1) % servers.size();
            }
        }

        if (StringUtils.isNotBlank(nacosDomain)) {
            for (int i = 0; i < UtilAndComs.REQUEST_DOMAIN_RETRY_COUNT; i++) {
                try {
                    return callServer(api, params, body, nacosDomain);
                } catch (NacosException e) {
                    exception = e;
                    if (NAMING_LOGGER.isDebugEnabled()) {
                        NAMING_LOGGER.debug("request {} failed.", nacosDomain, e);
                    }
                }
            }
        }
		....

    }
```

## 2.2 服务端注册操作

服务端会调用**InstanceController** #register

```java
// 注册实例分为如下两种：
//1.非持久化
//2.持久化
public void registerInstance(String namespaceId, String serviceName, Instance instance) throws NacosException {
    
    //如果当前服务为创建，需要先创建服务，这里如果创建服务会触发服务创建的事件
    createEmptyService(namespaceId, serviceName, instance.isEphemeral());

    Service service = getService(namespaceId, serviceName);

    if (service == null) {
        throw new NacosException(NacosException.INVALID_PARAM,
                                 "service not found, namespace: " + namespaceId + ", service: " + serviceName);
    }
    //这里添加实例，这里添加实例
    addInstance(namespaceId, serviceName, instance.isEphemeral(), instance);
}
```

### 1）服务端非持久化Distro操作

​	先总结：非持久化操作主要是通过DistroConsistencyServiceImpl实现：

> 1. 这个直接在内存更新值
> 2. 会对于这个key添加一个Task
> 3. 服务端对于监听这个key的监听器发送通知

上面我们在创建Service或者是Instance都是类似的操作

```java
public class DistroConsistencyServiceImpl	{
     @Autowired
    private DataStore dataStore;
    
	@Override
    public void put(String key, Record value) throws NacosException {
        onPut(key, value);
        taskDispatcher.addTask(key); //这里会添加这个key对应的任务
    }

	//添加操作
	public void onPut(String key, Record value) {
        if (KeyBuilder.matchEphemeralInstanceListKey(key)) {
            Datum<Instances> datum = new Datum<>();
            datum.value = (Instances) value;
            datum.key = key;
            datum.timestamp.incrementAndGet();
            //这里就是直接存储在内存中
            dataStore.put(key, datum);
        }

        if (!listeners.containsKey(key)) {
            return;
        }
		//对于监听当前key监听器 发送通知
        notifier.addTask(key, ApplyAction.CHANGE);
    }
}
```



### 2）服务端持久化Raft操作

先总结：非持久化操作主要是通过RaftConsistencyServiceImpl实现

> 1.如果不是leader转发给leader操作
>
> 2.leader拿到任务加锁然后本地执行缓存
>
> 3.发送变更信息给所有follower节点，如果超过一半节点成功就返回成功

```java
public class  RaftConsistencyServiceImpl{
	@Override
    public void put(String key, Record value) throws NacosException {
        try {
            //依赖raftCore实现发布；这里做持久化肯定会存储在磁盘中
            raftCore.signalPublish(key, value);
        } catch (Exception e) {
            Loggers.RAFT.error("Raft put failed.", e);
            throw new NacosException(NacosException.SERVER_ERROR, "Raft put failed, key:" + key + ", value:" + value, e);
        }
    }
}
```

这里就是具体的实现

```java
public void signalPublish(String key, Record value) throws Exception {
	
    //如果当前不是leader，那么转发给leader做处理
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
        OPERATE_LOCK.lock(); //加锁
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
		//这里会更新 本地磁盘缓存
        onPublish(datum, peers.local());

        final String content = JSON.toJSONString(json);
        //过半策略，这里使用countDown来实现的；
        final CountDownLatch latch = new CountDownLatch(peers.majorityCount());
        for (final String server : peers.allServersIncludeMyself()) {
            if (isLeader(server)) {
                latch.countDown();
                continue;
            }
            //发送给其他节点，等待他们刷盘更新
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
		//等待策略，等待超时抛出异常
        if (!latch.await(UtilsAndCommons.RAFT_PUBLISH_TIMEOUT, TimeUnit.MILLISECONDS)) {
            // only majority servers return success can we consider this update success
            Loggers.RAFT.error("data publish failed, caused failed to notify majority, key={}", key);
            throw new IllegalStateException("data publish failed, caused failed to notify majority, key=" + key);
        }
		//过半策略写入成功后，返回结果
        long end = System.currentTimeMillis();
        Loggers.RAFT.info("signalPublish cost {} ms, key: {}", (end - start), key);
    } finally {
        OPERATE_LOCK.unlock();
    }
}
```



# 3.获取实例

```java
//分析这个接口即可
naming.getAllInstances("nacos.test.3");
```

```java
//会调用到NacosNamingService 服务如下方法
@Override
public List<Instance> getAllInstances(String serviceName, String groupName, List<String> clusters, boolean subscribe) throws NacosException {

    ServiceInfo serviceInfo;
    if (subscribe) {
        //
        serviceInfo = hostReactor.getServiceInfo(NamingUtils.getGroupedName(serviceName, groupName), StringUtils.join(clusters, ","));
    } else {
        //这个表示直接从服务器端获取实例信息
        serviceInfo = hostReactor.getServiceInfoDirectlyFromServer(NamingUtils.getGroupedName(serviceName, groupName), StringUtils.join(clusters, ","));
    }
    List<Instance> list;
    if (serviceInfo == null || CollectionUtils.isEmpty(list = serviceInfo.getHosts())) {
        return new ArrayList<Instance>();
    }
    return list;
}
```

```java
//如果开启了subscribe模式；那么通过 HostReactor中获取    
public ServiceInfo getServiceInfo(final String serviceName, final String clusters) {

        NAMING_LOGGER.debug("failover-mode: " + failoverReactor.isFailoverSwitch());
        String key = ServiceInfo.getKey(serviceName, clusters);
    	//如果开启了故障转移功能；；默认是没有的
        if (failoverReactor.isFailoverSwitch()) {
            return failoverReactor.getService(key);
        }
		//从内存中获取
        ServiceInfo serviceObj = getServiceInfo0(serviceName, clusters);

    	//这里
        if (null == serviceObj) {
            serviceObj = new ServiceInfo(serviceName, clusters);
			//当前map中放一个空对象
            serviceInfoMap.put(serviceObj.getKey(), serviceObj);
			//表示当前服务正在更新中 
            updatingMap.put(serviceName, new Object());
            //这个表示会从远程服务器拉取 实例信息
            updateServiceNow(serviceName, clusters);
            updatingMap.remove(serviceName);
		//如果获取当前的服务正在从远处服务更新的处理
        } else if (updatingMap.containsKey(serviceName)) {

            if (UPDATE_HOLD_INTERVAL > 0) {
                //hold住一段时间，等待之前线程请求服务器获取结果返回
                // hold a moment waiting for update finish
                synchronized (serviceObj) {
                    try {
                        serviceObj.wait(UPDATE_HOLD_INTERVAL);
                    } catch (InterruptedException e) {
                        NAMING_LOGGER.error("[getServiceInfo] serviceName:" + serviceName + ", clusters:" + clusters, e);
                    }
                }
            }
        }
		
    	//这个表示如果上面等待了一段时间还是没有，那么会继续从服务器更新信息
        scheduleUpdateIfAbsent(serviceName, clusters);

        return serviceInfoMap.get(serviceObj.getKey());
    }
```



# 4.BeatReactor的使用-非持久化的更新

​	上面我们看到我们注册服务实例如果是非持久化会添加一个addBeatInfo，那么这个beatInfo是干什么的呢；下面我们娓娓道来：

> 1.默认给的是5秒回发送一个心跳检测实例的变化，当然这个5s是通过服务器来确定
>
> 2.如果服务端数据发送变化会通过udp把相关数据发送给客户端；客户端通过比对返回数据

## 4.1 客户端处理

```java
public void addBeatInfo(String serviceName, BeatInfo beatInfo) {
    NAMING_LOGGER.info("[BEAT] adding beat: {} to beat map.", beatInfo);
    String key = buildKey(serviceName, beatInfo.getIp(), beatInfo.getPort());
    BeatInfo existBeat = null;
    //fix #1733
    if ((existBeat = dom2Beat.remove(key)) != null) {
        existBeat.setStopped(true);
    }
    dom2Beat.put(key, beatInfo);
    //执行当前的BeatTask任务
    executorService.schedule(new BeatTask(beatInfo), beatInfo.getPeriod(), TimeUnit.MILLISECONDS);
    MetricsMonitor.getDom2BeatSizeMonitor().set(dom2Beat.size());
}
```

具体细节如下：

```java
class BeatTask implements Runnable {

    BeatInfo beatInfo;

    public BeatTask(BeatInfo beatInfo) {
        this.beatInfo = beatInfo;
    }

    @Override
    public void run() {
        if (beatInfo.isStopped()) {
            return;
        }
        //下一次定时任务的延时处理
        long nextTime = beatInfo.getPeriod();
        try {
            //服务请求处理
            JSONObject result = serverProxy.sendBeat(beatInfo, BeatReactor.this.lightBeatEnabled);
            //返回下一次客户端心跳间隔时间
            long interval = result.getIntValue("clientBeatInterval");
            boolean lightBeatEnabled = false;
            if (result.containsKey(CommonParams.LIGHT_BEAT_ENABLED)) {
                lightBeatEnabled = result.getBooleanValue(CommonParams.LIGHT_BEAT_ENABLED);
            }
            BeatReactor.this.lightBeatEnabled = lightBeatEnabled;
            if (interval > 0) {
                nextTime = interval;
            }
            int code = NamingResponseCode.OK;
            if (result.containsKey(CommonParams.CODE)) {
                code = result.getIntValue(CommonParams.CODE);
            }
            //如果服务端返回未找到，那么就发起一个注册实例
            if (code == NamingResponseCode.RESOURCE_NOT_FOUND) {
                Instance instance = new Instance();
                instance.setPort(beatInfo.getPort());
                instance.setIp(beatInfo.getIp());
                instance.setWeight(beatInfo.getWeight());
                instance.setMetadata(beatInfo.getMetadata());
                instance.setClusterName(beatInfo.getCluster());
                instance.setServiceName(beatInfo.getServiceName());
                instance.setInstanceId(instance.getInstanceId());
                instance.setEphemeral(true);
                try {
                    serverProxy.registerService(beatInfo.getServiceName(),
                                                NamingUtils.getGroupName(beatInfo.getServiceName()), instance);
                } catch (Exception ignore) {
                }
            }
        } catch (NacosException ne) {
            NAMING_LOGGER.error("[CLIENT-BEAT] failed to send beat: {}, code: {}, msg: {}",
                                JSON.toJSONString(beatInfo), ne.getErrCode(), ne.getErrMsg());

        }
        //下一次延时心跳执行
        executorService.schedule(new BeatTask(beatInfo), nextTime, TimeUnit.MILLISECONDS);
    }
```

udpSocket用来接收服务端Instance变化请求

- 我们在获取instance列表的时候会把本地host和udp的port发送给服务端；

```java

public class HostReactor{
//使用 DatagramSocket() 监听服务端的服务变化，如果有变化会更新本地磁盘和本地服务列表；并产生恢复
 this.pushReceiver = new PushReceiver(this);

}

public class PushReciver{
    
    
      @Override
    public void run() {
        while (true) {
            try {
                // byte[] is initialized with 0 full filled by default
                byte[] buffer = new byte[UDP_MSS];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                udpSocket.receive(packet);

                String json = new String(IoUtils.tryDecompress(packet.getData()), "UTF-8").trim();
                NAMING_LOGGER.info("received push data: " + json + " from " + packet.getAddress().toString());

                PushPacket pushPacket = JSON.parseObject(json, PushPacket.class);
                String ack;
                if ("dom".equals(pushPacket.type) || "service".equals(pushPacket.type)) {
                    //最终本地处理变化信息，刷新缓存已经更新磁盘
                    hostReactor.processServiceJSON(pushPacket.data);

                    // send ack to server
                    ack = "{\"type\": \"push-ack\""
                        + ", \"lastRefTime\":\"" + pushPacket.lastRefTime
                        + "\", \"data\":" + "\"\"}";
                } else if ("dump".equals(pushPacket.type)) {
                    // dump data to server
                    ack = "{\"type\": \"dump-ack\""
                        + ", \"lastRefTime\": \"" + pushPacket.lastRefTime
                        + "\", \"data\":" + "\""
                        + StringUtils.escapeJavaScript(JSON.toJSONString(hostReactor.getServiceInfoMap()))
                        + "\"}";
                } else {
                    // do nothing send ack only
                    ack = "{\"type\": \"unknown-ack\""
                        + ", \"lastRefTime\":\"" + pushPacket.lastRefTime
                        + "\", \"data\":" + "\"\"}";
                }
				//回复客户端收到的信息
                udpSocket.send(new DatagramPacket(ack.getBytes(Charset.forName("UTF-8")),
                    ack.getBytes(Charset.forName("UTF-8")).length, packet.getSocketAddress()));
            } catch (Exception e) {
                NAMING_LOGGER.error("[NA] error while receiving push data", e);
            }
        }
    }

    public static class PushPacket {
        public String type;
        public long lastRefTime;
        public String data;
    }
}
```



## 4.2 服务端的处理

**InstanceController**处理这个任务

```java
	@CanDistro
    @PutMapping("/beat")
    public JSONObject beat(HttpServletRequest request) throws Exception {

        JSONObject result = new JSONObject();
		。。。

        Instance instance = serviceManager.getInstance(namespaceId, serviceName, clusterName, ip, port);
		//实例和客户端发送的实例没有收到，会返回这个信息
        if (instance == null) {
            if (clientBeat == null) {
                result.put(CommonParams.CODE, NamingResponseCode.RESOURCE_NOT_FOUND);
                return result;
            }
            instance = new Instance();
            instance.setPort(clientBeat.getPort());
            instance.setIp(clientBeat.getIp());
            instance.setWeight(clientBeat.getWeight());
            instance.setMetadata(clientBeat.getMetadata());
            instance.setClusterName(clusterName);
            instance.setServiceName(serviceName);
            instance.setInstanceId(instance.getInstanceId());
            instance.setEphemeral(clientBeat.isEphemeral());

            serviceManager.registerInstance(namespaceId, serviceName, instance);
        }

        Service service = serviceManager.getService(namespaceId, serviceName);

        if (service == null) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "service not found: " + serviceName + "@" + namespaceId);
        }
        if (clientBeat == null) {
            clientBeat = new RsInfo();
            clientBeat.setIp(ip);
            clientBeat.setPort(port);
            clientBeat.setCluster(clusterName);
        }
        //这里 重点处理客户端的心跳请求
        service.processClientBeat(clientBeat);
		//返回下次心跳时间
        result.put(CommonParams.CODE, NamingResponseCode.OK);
        result.put("clientBeatInterval", instance.getInstanceHeartBeatInterval());
        result.put(SwitchEntry.LIGHT_BEAT_ENABLED, switchDomain.isLightBeatEnabled());
        return result;
    }
	//ClientBeanProcessor 这里构建了这个
	public void processClientBeat(final RsInfo rsInfo) {
        ClientBeatProcessor clientBeatProcessor = new ClientBeatProcessor();
        clientBeatProcessor.setService(this);
        clientBeatProcessor.setRsInfo(rsInfo);
        HealthCheckReactor.scheduleNow(clientBeatProcessor);
    }
```

```java
/**
 * Thread to update ephemeral instance triggered by client beat
 *
 * @author nkorange
 */
public class ClientBeatProcessor implements Runnable {
    public static final long CLIENT_BEAT_TIMEOUT = TimeUnit.SECONDS.toMillis(15);
   

    @Override
    public void run() {
        Service service = this.service;
        if (Loggers.EVT_LOG.isDebugEnabled()) {
            Loggers.EVT_LOG.debug("[CLIENT-BEAT] processing beat: {}", rsInfo.toString());
        }

        String ip = rsInfo.getIp();
        String clusterName = rsInfo.getCluster();
        int port = rsInfo.getPort();
        Cluster cluster = service.getClusterMap().get(clusterName);
        //由于我们只有实例非持久化才会处理，所以这里获取的非持久化的信息
        List<Instance> instances = cluster.allIPs(true);

        for (Instance instance : instances) {
            if (instance.getIp().equals(ip) && instance.getPort() == port) {
                if (Loggers.EVT_LOG.isDebugEnabled()) {
                    Loggers.EVT_LOG.debug("[CLIENT-BEAT] refresh beat: {}", rsInfo.toString());
                }
                //更新最新一次心跳时间
                instance.setLastBeat(System.currentTimeMillis());
                if (!instance.isMarked()) {
                    if (!instance.isHealthy()) {
                        //如果之前不是健康的，修改状态
                        instance.setHealthy(true);
                        Loggers.EVT_LOG.info("service: {} {POS} {IP-ENABLED} valid: {}:{}@{}, region: {}, msg: client beat ok",
                            cluster.getService().getName(), ip, port, cluster.getName(), UtilsAndCommons.LOCALHOST_SITE);
                        //这里通过pushService发起服务更改事件
                        getPushService().serviceChanged(service);
                    }
                }
            }
        }
    }
}

```

**服务更改事件变化**如下：

```java
public class PushService{
    
    @Override
    public void onApplicationEvent(ServiceChangeEvent event) {
        Service service = event.getService();
        String serviceName = service.getName();
        String namespaceId = service.getNamespaceId();

        Future future = udpSender.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    Loggers.PUSH.info(serviceName + " is changed, add it to push queue.");
                    ConcurrentMap<String, PushClient> clients = clientMap.get(UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName));
                    if (MapUtils.isEmpty(clients)) {
                        return;
                    }

                    Map<String, Object> cache = new HashMap<>(16);
                    long lastRefTime = System.nanoTime();
                    for (PushClient client : clients.values()) {
                        if (client.zombie()) {
                            Loggers.PUSH.debug("client is zombie: " + client.toString());
                            clients.remove(client.toString());
                            Loggers.PUSH.debug("client is zombie: " + client.toString());
                            continue;
                        }
。。。                 
						//会组装相关实例的信息发送给客户端
                        udpPush(ackEntry);
                    }
                } catch (Exception e) {
      				。。。
            }
        }, 1000, TimeUnit.MILLISECONDS);

        futureMap.put(UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName), future);

    }
}
```



# 5. FailOverReactor的使用-故障转移

## 5.1 先看什么时候开启这个功能

> 默认是从本地

```java
public class FailOverReactor{
    
    public void init() {
        //定时 把failoverdir的文件 读取出来放在内存中 --功能是否开启故障转移功能
        executorService.scheduleWithFixedDelay(new SwitchRefresher(), 0L, 5000L, TimeUnit.MILLISECONDS);
        //根据不同的服务，把服务往failoverDir文件 写，30min一次
        executorService.scheduleWithFixedDelay(new DiskFileWriter(), 30, DAY_PERIOD_MINUTES, TimeUnit.MINUTES);

        // backup file on startup if failover directory is empty.
        executorService.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    File cacheDir = new File(failoverDir);

                    if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                        throw new IllegalStateException("failed to create cache dir: " + failoverDir);
                    }

                    File[] files = cacheDir.listFiles();
                    if (files == null || files.length <= 0) {
                        new DiskFileWriter().run();
                    }
                } catch (Throwable e) {
                    NAMING_LOGGER.error("[NA] failed to backup file on startup.", e);
                }

            }
        }, 10000L, TimeUnit.MILLISECONDS);
    }
}

//这个线程判定是否开启这个功能；
class SwitchRefresher implements Runnable{
    
    long lastModifiedMillis = 0L;

        @Override
        public void run() {
            try {
                //00-00---000-VIPSRV_FAILOVER_SWITCH-000---00-00这个文件是否存在
                File switchFile = new File(failoverDir + UtilAndComs.FAILOVER_SWITCH);
                if (!switchFile.exists()) {
                    switchParams.put("failover-mode", "false");
                    NAMING_LOGGER.debug("failover switch is not found, " + switchFile.getName());
                    return;
                }

                long modified = switchFile.lastModified();

                if (lastModifiedMillis < modified) {
                    lastModifiedMillis = modified;
                    String failover = ConcurrentDiskUtil.getFileContent(failoverDir + UtilAndComs.FAILOVER_SWITCH,
                        Charset.defaultCharset().toString());
                    if (!StringUtils.isEmpty(failover)) {
                        List<String> lines = Arrays.asList(failover.split(DiskCache.getLineSeparator()));

                        for (String line : lines) {
                            String line1 = line.trim();
                            //当这个文件的第一行为1才表示开启
                            if ("1".equals(line1)) {
                                switchParams.put("failover-mode", "true");
                                NAMING_LOGGER.info("failover-mode is on");
                                //开启后即可扫描磁盘文件存储在内存中
                                new FailoverFileReader().run();
                            } else if ("0".equals(line1)) {
                                switchParams.put("failover-mode", "false");
                                NAMING_LOGGER.info("failover-mode is off");
                            }
                        }
                    } else {
                        switchParams.put("failover-mode", "false");
                    }
                }

            } catch (Throwable e) {
                NAMING_LOGGER.error("[NA] failed to read failover switch.", e);
            }
        }
}

//顺带看一下DiskFileWriter的实现
class DiskFileWriter extends TimerTask {
    @Override
    public void run() {
        //这个是把本地servie内存中的数据ServicInfo获取出来，然后排序部分信息写磁盘
        Map<String, ServiceInfo> map = hostReactor.getServiceInfoMap();
        for (Map.Entry<String, ServiceInfo> entry : map.entrySet()) {
            ServiceInfo serviceInfo = entry.getValue();
            if (StringUtils.equals(serviceInfo.getKey(), UtilAndComs.ALL_IPS) || StringUtils.equals(
                serviceInfo.getName(), UtilAndComs.ENV_LIST_KEY)
                || StringUtils.equals(serviceInfo.getName(), "00-00---000-ENV_CONFIGS-000---00-00")
                || StringUtils.equals(serviceInfo.getName(), "vipclient.properties")
                || StringUtils.equals(serviceInfo.getName(), "00-00---000-ALL_HOSTS-000---00-00")) {
                continue;
            }
			//写本地磁盘缓存
            DiskCache.write(serviceInfo, failoverDir);
        }
    }
}
```



## 5.2 回看FailOverReactor的使用

```java
//我们获取 
public ServiceInfo getServiceInfo(final String serviceName, final String clusters) {

        NAMING_LOGGER.debug("failover-mode: " + failoverReactor.isFailoverSwitch());
        String key = ServiceInfo.getKey(serviceName, clusters);
        if (failoverReactor.isFailoverSwitch()) {
            //开启故障转移功能那么通过这个这里获取服务实例
            return failoverReactor.getService(key);
        }
    	..
        //通过本地内存或者远程服务器获取服务实例
            
}

//通过如下发现，直接通过内存缓存的ServiceMap获取，如果没有的话，直接构造一个空对象返回，不会远程访问服务
public ServiceInfo getService(String key) {
    ServiceInfo serviceInfo = serviceMap.get(key);

    if (serviceInfo == null) {
        serviceInfo = new ServiceInfo();
        serviceInfo.setName(key);
    }

    return serviceInfo;
}
```

​		从上面我们发现直接通过ServiceMap中获取，那么猜想serviceMap肯定要定时从服务器获取更新，不然serviceMap会缓存一些老数据

最后我们发现在SwitchRefresher启动获取本地failover是否开启的时候，如果开启了，会启动一个FailoverFileReader线程，如下：



```java
 class FailoverFileReader implements Runnable {

        @Override
        public void run() {
            Map<String, ServiceInfo> domMap = new HashMap<String, ServiceInfo>(16);

            BufferedReader reader = null;
            try {
				//其实很简单，就是通过本地缓存获取实例数据，然后放入内存中
                File cacheDir = new File(failoverDir);
               
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }

                    if (file.getName().equals(UtilAndComs.FAILOVER_SWITCH)) {
                        continue;
                    }

                    ServiceInfo dom = new ServiceInfo(file.getName());

                    try {
                        String dataString = ConcurrentDiskUtil.getFileContent(file,
                            Charset.defaultCharset().toString());
                        reader = new BufferedReader(new StringReader(dataString));

                        ...
                    if (!CollectionUtils.isEmpty(dom.getHosts())) {
                        domMap.put(dom.getKey(), dom);
                    }
                }
            } catch (Exception e) {
                NAMING_LOGGER.error("[NA] failed to read cache file", e);
            }

            if (domMap.size() > 0) {
                serviceMap = domMap;
            }
        }
    }
```



