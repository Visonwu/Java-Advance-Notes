1. 参考：<https://nacos.io/zh-cn/docs/sdk.html>

github下载源码编译安装启动：



启动后访问这个页面可以登陆控制界面，账号密码：nacos

<http://127.0.0.1:8848/nacos/index.html>

# 1.简单使用：

依赖

```xml
<dependency>
    <groupId>com.alibaba.boot</groupId>
    <artifactId>nacos-config-spring-boot-starter</artifactId>
    <version>0.2.1</version>
</dependency>
```

application.properties

```properties
# 本地nacos服务启动
nacos.config.server-addr=127.0.0.1:8848
```

使用

```java
@NacosPropertySource(dataId = "example", groupId = "DEFAULT_GROUP",autoRefreshed = true)
@RestController
public class DemoController {
	
    //可以动态获取nacos上设置的info的信息，这里如果info没有获取到返回的默认'hello vison'值
    @NacosValue(value = "${info:hello vison}", autoRefreshed = true)
    private String info;

    @RequestMapping(value = "/get", method = GET)
    @ResponseBody
    public String get() {
        return info;
    }
}
```



# 2. 源码简单分析

如果我们要实现一个配置中心需要考虑的

````
1.服务器端的配置保存（持久化）
	数据库
2.服务器端提供访问api
	rpc、http（openapi）
3.数据变化之后如何通知到客户端
	zookeeper（session manager）
	push（服务端主动推送到客户端）、pull(客户端主动拉去数据）? -> 长轮训( pull数据量很大会怎么办)
4.客户端如何去获得远程服务的数据（）
5.安全性（）
6.刷盘（本地缓存）->
````

## 2.1 从客户端分析源码

nacos采用pull和'push'结合方式获取配置信息，采用长连接方式（超时）获取数据；

其实Nacos还是采用RESTFul 风格是调用服务的，

通过`sdk`代码来分析实现

```java
public static void main(String[] args) {
    try {
        String serverAddr = "localhost:8848";
        String dataId = "dubbo.properties";
        String group = "DEFAULT_GROUP";
        String namespace ="2b6a1eaa-ba6d-49ca-836a-791d4c38dd72";
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
         properties.put("namespace",namespace); //命名空间做隔离
        //这里入口查看启动哪些线程 更新数据
        ConfigService configService = NacosFactory.createConfigService(properties);
        //怎么样去获取数据（本地，远程，缓存等）
        String content = configService.getConfig(dataId, group, 5000);
        System.out.println(content);
        //监听变化回调
        configService.addListener("dubbo.properties", "DEFAULT_GROUP", new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }
            @Override
            public void receiveConfigInfo(String s) {
                System.out.println("ssss"+s);
            }
        });
        System.in.read();
    } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    }
}
```

### 1）NacosFactory.createConfigService

```java
//1.通过源码可以发现这里是  NacosConfigService
ConfigService configService = NacosFactory.createConfigService(properties);


//2.如下，这里调用NacosConfigService的构造方法
public static ConfigService createConfigService(Properties properties) throws NacosException {
    try {
        Class<?> driverImplClass = Class.forName("com.alibaba.nacos.client.config.NacosConfigService");
        Constructor constructor = driverImplClass.getConstructor(Properties.class);
        ConfigService vendorImpl = (ConfigService)constructor.newInstance(properties);
        return vendorImpl;
    } catch (Throwable e) {
        throw new NacosException(-400, e.getMessage());
    }
}

//3.NacosConfigService的构造方法如下
public NacosConfigService(Properties properties) throws NacosException {
    String encodeTmp = properties.getProperty(PropertyKeyConst.ENCODE);
    if (StringUtils.isBlank(encodeTmp)) {
        encode = Constants.ENCODE;
    } else {
        encode = encodeTmp.trim();
    }
    String namespaceTmp = properties.getProperty(PropertyKeyConst.NAMESPACE);
    if (StringUtils.isBlank(namespaceTmp)) {
        namespace = TenantUtil.getUserTenant();
        properties.put(PropertyKeyConst.NAMESPACE, namespace);
    } else {
        namespace = namespaceTmp;
        properties.put(PropertyKeyConst.NAMESPACE, namespace);
    }
    //这里创建代理连接服务器
    agent = new ServerHttpAgent(properties);
    //
    agent.start();
    //
    worker = new ClientWorker(agent, configFilterChainManager);
}

//4.new ClientWorker创建了不同的线程处理任务
public ClientWorker(final ServerHttpAgent agent, final ConfigFilterChainManager configFilterChainManager) {
    this.agent = agent;
    this.configFilterChainManager = configFilterChainManager;
    executor = Executors.newScheduledThreadPool(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("com.alibaba.nacos.client.Worker." + agent.getName());
            t.setDaemon(true);
            return t;
        }
    });

    executorService = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("com.alibaba.nacos.client.Worker.longPulling" + agent.getName());
            t.setDaemon(true);
            return t;
        }
    });
	
    //定时任务检查配置信息是否变化，以及对应的处理方式
    executor.scheduleWithFixedDelay(new Runnable() {
        public void run() {
            try {
                checkConfigInfo();
            } catch (Throwable e) {
                log.error(agent.getName(), "NACOS-XXXX", "[sub-check] rotate check error", e);
            }
        }
    }, 1L, 10L, TimeUnit.MILLISECONDS);
}

//5.这里分批处理任务，检查配置信息，并更新数据信息
public void checkConfigInfo() {
    // 分任务
    int listenerSize = cacheMap.get().size();
    // 向上取整为批数
    int longingTaskCount = (int)Math.ceil(listenerSize / ParamUtil.getPerTaskConfigSize());
    if (longingTaskCount > currentLongingTaskCount) {
        for (int i = (int)currentLongingTaskCount; i < longingTaskCount; i++) {
            // 要判断任务是否在执行 这块需要好好想想。 任务列表现在是无序的。变化过程可能有问题
            executorService.execute(new LongPullingRunnable(i));
        }
        currentLongingTaskCount = longingTaskCount;
    }
}
```

### 2）ConfigService.getConfig

```java
//同样道理，会调用如下方式
private String getConfigInner(String tenant, String dataId, String group, long timeoutMs) throws NacosException {
    group = null2defaultGroup(group);
    ParamUtils.checkKeyParam(dataId, group);
    ConfigResponse cr = new ConfigResponse();

    cr.setDataId(dataId);
    cr.setTenant(tenant);
    cr.setGroup(group);

    // 优先使用本地配置
    String content = LocalConfigInfoProcessor.getFailover(agent.getName(), dataId, group, tenant);
    if (content != null) {
        log.warn(agent.getName(), "[get-config] get failover ok, dataId={}, group={}, tenant={}, config={}", dataId,
                 group, tenant, ContentUtils.truncateContent(content));
        cr.setContent(content);
        configFilterChainManager.doFilter(null, cr);
        content = cr.getContent();
        return content;
    }

    try {
        //主要也是这里发起http请求获取数据返回
        content = worker.getServerConfig(dataId, group, tenant, timeoutMs);
        cr.setContent(content);
        configFilterChainManager.doFilter(null, cr);
        content = cr.getContent();
        return content;
    } catch (NacosException ioe) {
        if (NacosException.NO_RIGHT == ioe.getErrCode()) {
            throw ioe;
        }
        log.warn("NACOS-0003",
                 LoggerHelper.getErrorCodeStr("NACOS", "NACOS-0003", "环境问题", "get from server error"));
        log.warn(agent.getName(), "[get-config] get from server error, dataId={}, group={}, tenant={}, msg={}",
                 dataId, group, tenant, ioe.toString());
    }

    log.warn(agent.getName(), "[get-config] get snapshot ok, dataId={}, group={}, tenant={}, config={}", dataId,
             group, tenant, ContentUtils.truncateContent(content));
    content = LocalConfigInfoProcessor.getSnapshot(agent.getName(), dataId, group, tenant);
    cr.setContent(content);
    configFilterChainManager.doFilter(null, cr);
    content = cr.getContent();
    return content;
}
```



## 2.2 服务端处理Nacos客户端的请求





## 2.3 综合客户端/服务端解读长轮训源码

​		我们知道客户端会有一个长轮训的任务去检查服务器端的配置是否发生了变化，如果发生了变更，那么客户端会拿到变更的 groupKey 再根据 groupKey 去获取配置项的最新值更新到本地的缓存以及文件中，那么这种每次都靠客户端去请求，那请求的时间间隔设置 多少合适呢？ 

​	如果间隔时间设置的太长的话有可能无法及时获取服务端的变更，如果间隔时间设置的太短的话，那么 频繁的请求对于服务端来说无疑也是一种负担，所以最好的方式是客户端每隔一段长度适中的时间去服 务端请求，而在这期间如果配置发生变更，服务端能够主动将变更后的结果推送给客户端，这样既能保 证客户端能够实时感知到配置的变化，也降低了服务端的压力。 我们来看看nacos设置的间隔时间是多 久。

> **长轮训概念：**
>
> ​	客户端发起一个请求到服务端，服务端收到客户端的请求后，并不会立刻响应给客户端，而是先把这个 请求hold住，然后服务端会在hold住的这段时间检查数据是否有更新，如果有，则响应给客户端，如果 一直没有数据变更，则达到一定的时间（长轮训时间间隔）才返回。 
>
> 长轮训典型的场景有： 扫码登录、扫码支付。



### 1) 客户端长轮训

```java
// ClientWork.LongPullingRunnable.run-> ClientWork.checkUpdateDataIds 
-> ClientWork.checkUpdateConfigStr

 /**
 * 从Server获取值变化了的DataID列表。返回的对象里只有dataId和group是有效的。 保证不返回NULL。
 */
List<String> checkUpdateConfigStr(String probeUpdateString, boolean isInitializingCacheList) {

        List<String> params = Arrays.asList(Constants.PROBE_MODIFY_REQUEST, probeUpdateString);
        long timeout = TimeUnit.SECONDS.toMillis(30L);

        List<String> headers = new ArrayList<String>(2);
        headers.add("Long-Pulling-Timeout");
        headers.add("" + timeout);

        // told server do not hang me up if new initializing cacheData added in
        if (isInitializingCacheList) {
            headers.add("Long-Pulling-Timeout-No-Hangup");
            headers.add("true");
        }

        if (StringUtils.isBlank(probeUpdateString)) {
            return Collections.emptyList();
        }

        try {
          //  这个方法最终会发起http请求，注意这里面有一个 timeout 的属性，默认是30000ms;
          //可以通过configLongPollTimeout进行修改
            HttpResult result = agent.httpPost(Constants.CONFIG_CONTROLLER_PATH + "/listener", headers, params,
                agent.getEncode(), timeout);

            if (HttpURLConnection.HTTP_OK == result.code) {
                setHealthServer(true);
                return parseUpdateDataIdResponse(result.content);
            } else {
                setHealthServer(false);
                if (result.code == HttpURLConnection.HTTP_INTERNAL_ERROR) {
                    log.error("NACOS-0007", LoggerHelper.getErrorCodeStr("Nacos", "Nacos-0007", "环境问题",
                        "[check-update] get changed dataId error"));
                }
                log.error(agent.getName(), "NACOS-XXXX", "[check-update] get changed dataId error, code={}",
                    result.code);
            }
        } catch (IOException e) {
            setHealthServer(false);
            log.error(agent.getName(), "NACOS-XXXX", "[check-update] get changed dataId exception, msg={}",
                e.toString());
        }
        return Collections.emptyList();
}
```



我们可以在nacos的日志目录下 $NACOS_HOME/nacos/logs/config-client-request.log 文件;

```log
2019-08-04 13:22:19,736|0|nohangup|127.0.0.1|polling|1|55|0 2019-08-04 13:22:49,443|29504|timeout|127.0.0.1|polling|1|55 2019-08-04 13:23:18,983|29535|timeout|127.0.0.1|polling|1|55 2019-08-04 13:23:48,493|29501|timeout|127.0.0.1|polling|1|55 2019-08-04 13:24:18,003|29500|timeout|127.0.0.1|polling|1|55 2019-08-04 13:24:47,509|29501|timeout|127.0.0.1|polling|1|55
```

​	可以看到一个现象，在配置没有发生变化的情况下，客户端会等29.5s以上，才请求到服务器端的结 果。然后客户端拿到服务器端的结果之后，在做后续的操作。 

如果在配置变更的情况下，由于客户端基于长轮训的连接保持，所以返回的时间会非常的短，我们可以 做个小实验，在nacos console中频繁修改数据然后再观察一下 config-client-request.log 的变化 



这里初步能够猜测到：长轮训根据md5判断数据是否修改，修改后服务端直接返回数据，否则会等待29.5s后才会返回。

### 2) 服务端处理长轮训

​		分析完客户端之后，随着好奇心的驱使，服务端是如何处理客户端的请求的？那么同样，我们需要思考几个问题? 

- 客户端的长轮训响应时间受到哪些因素的影响 

- 客户端的超时时间为什么要设置30s 

- 客户端发送的请求地址是: /v1/cs/configs/listener 找到服务端对应的方法

**ConfifigController**

​		nacos是使用spring mvc提供的rest api。这里面会调用inner.doPollingConfifig进行处理

```java
@RequestMapping(value = "/listener", method = RequestMethod.POST) public void listener(HttpServletRequest request, HttpServletResponse response)throws ServletException, IOException { request.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", true); String probeModify = request.getParameter("Listening-Configs"); if (StringUtils.isBlank(probeModify)) { throw new IllegalArgumentException("invalid probeModify"); }probeModify = URLDecoder.decode(probeModify, Constants.ENCODE); Map<String, String> clientMd5Map; try {clientMd5Map = MD5Util.getClientMd5Map(probeModify); } catch (Throwable e) { throw new IllegalArgumentException("invalid probeModify"); }// do long-polling inner.doPollingConfig(request, response, clientMd5Map, probeModify.length()); }
```

**doPollingConfifig**

这个方法中，兼容了长轮训和短轮询的逻辑，我们只需要关注长轮训的部分。再次进入到 longPollingService.addLongPollingClient 



**longPollingService.addLongPollingClient**

​	从方法名字上可以推测出，这个方法应该是把客户端的长轮训请求添加到某个任务中去。 获得客户端传递过来的超时时间，并且进行本地计算，提前500ms返回响应，这就能解释为什么 客户端响应超时时间是29.5+了。当然如果 isFixedPolling=true 的情况下，不会提前返回响应 根据客户端请求过来的md5和服务器端对应的group下对应内容的md5进行比较，如果不一致，则 通过 generateResponse 将结果返回 ;如果配置文件没有发生变化，则通过 scheduler.execute 启动了一个定时任务，将客户端的长轮 询请求封装成一个叫 ClientLongPolling 的任务，交给 scheduler 去执行 





### 3) 总结

简单总结一下刚刚分析的整个过程。 

- 客户端发起长轮训请求 

- 服务端收到请求以后，先比较服务端缓存中的数据是否相同，如果不通，则直接返回 
- 如果相同，则通过schedule延迟29.5s之后再执行比较 

- 为了保证当服务端在29.5s之内发生数据变化能够及时通知给客户端，服务端采用事件订阅的方式 来监听服务端本地数据变化的事件，一旦收到事件，则触发DataChangeTask的通知，并且遍历 allStubs队列中的ClientLongPolling,把结果写回到客户端，就完成了一次数据的推送 

- 如果 DataChangeTask 任务完成了数据的 “推送” 之后，ClientLongPolling 中的调度任务又开始执 行了怎么办呢？ 

  ​     很简单，只要在进行 “推送” 操作之前，先将原来等待执行的调度任务取消掉就可以了，这样就防 止了推送操作写完响应数据之后，调度任务又去写响应数据，这时肯定会报错的。所以，在 ClientLongPolling方法中，最开始的一个步骤就是删除订阅事件 



​	   所以总的来说，Nacos采用推+拉的形式，来解决最开始关于长轮训时间间隔的问题。当然，30s这个时 间是可以设置的，而之所以定30s，应该是一个经验值。



# 3.nacos集群

​	使用**raft算法**协议保证数据一致性（和zookeeper一样弱一致性），以及相关的leader选举

raft参考：<http://thesecretlivesofdata.com/raft/>

- leader
- follower
- candidate选举