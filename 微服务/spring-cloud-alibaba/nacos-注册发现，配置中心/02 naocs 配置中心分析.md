# 1. 配置中心源码简单分析

如果我们要实现一个配置中心需要考虑的

```
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
```

nacos默认使用derby做配置数据存储，放在用户目录下；当然可以配置mysql做数据存储；

**理解：**

- nacos采用pull和'push'结合方式获取配置信息，采用长连接方式（超时）获取数据；
  - 客户端长轮询，服务器如果配置数据没有更改会hang住这个请求29.5s;如果期间数据发生变化会直接返回数据
  - 如果是集群条件下；客户端会选一个服务器连接，如果有一个失败了，会更新下一个服务器重新发起请求

- 服务器配置中心数据更改了
  - 数据发送时，先保存持久化的mysql数据，然后通过触发ConfigDataChangeEvent事件，
  - 然后通过遍历所有的集群服务器节点ip信息,然后分别通过线程异步发送给所有服务器（CommunicaionController处理）当前数据更改了;
  - 服务器拿到数据后会更新每一个服务器的本地日志缓存，以及内存中每一个dataId对应的md5值；最后还会触发LocalDataChangeEvent事件，通过hang住的客户端请求返回数据 



## 1.1 从客户端分析源码

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
    //这里创建代理连接服务器，这里用了装饰设计模式，通过Metric-prometheus来统计rest 请求时长
    agent = new MetricsHttpAgent(new ServerHttpAgent(properties));
    //这个其实是针对endpoint设置才会起作用，这里简单说一下；
    //通过nameServie命名服务获取serverList
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

    // 优先使用本地配置，这里是读取本地快照文件
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

    public HttpResult httpGet(String path, List<String> headers, List<String> paramValues, String encoding,
                              long readTimeoutMs) throws IOException {
        final long endTime = System.currentTimeMillis() + readTimeoutMs;

        boolean isSSL = false;

        do {
            try {
                List<String> newHeaders = getSpasHeaders(paramValues);
                if (headers != null) {
                    newHeaders.addAll(headers);
                }
                //这里发起请求，注意这里服务器的url由来
                HttpResult result = HttpSimpleClient.httpGet(
                    getUrl(serverListMgr.getCurrentServerAddr(), path, isSSL), newHeaders, paramValues, encoding,
                    readTimeoutMs, isSSL);
                if (result.code == HttpURLConnection.HTTP_INTERNAL_ERROR
                    || result.code == HttpURLConnection.HTTP_BAD_GATEWAY
                    || result.code == HttpURLConnection.HTTP_UNAVAILABLE) {
                    log.error("NACOS ConnectException", "currentServerAddr:{}. httpCode:",
                        new Object[] {serverListMgr.getCurrentServerAddr(), result.code});
                } else {
                    return result;
                }
            } catch (ConnectException ce) {
                //这里表示请求异常，会换一个服务器做请求
    			serverListMgr.refreshCurrentServerAddr();            
               .....
    }
```

```java
public ServerListManager{
    ...
	public String getCurrentServerAddr() {
        //这里是通过list的选取的一个；当然这个是通过权重排序了的来获取url的；
        if (StringUtils.isBlank(currentServerAddr)) {
            currentServerAddr = iterator().next();
        }
        return currentServerAddr;
    }
    //这个就是在当前的url中请求失败后会change一个服务请求
    public void refreshCurrentServerAddr() {
        currentServerAddr = iterator().next();
    }
}
```



## 1.2 服务端处理Nacos客户端的请求

如下所示，服务端是通过restful获取请求：

```java
@RestController
@RequestMapping("/v1/cs/configs")
public class ConfigController {

   @GetMapping
    public void getConfig(HttpServletRequest request, HttpServletResponse response,
                          @RequestParam("dataId") String dataId, @RequestParam("group") String group,
                          @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY)
                              String tenant,
                          @RequestParam(value = "tag", required = false) String tag)
        throws IOException, ServletException, NacosException {
        // check params
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        ParamUtils.checkParam(tag);

        final String clientIp = RequestUtil.getRemoteIp(request);
        inner.doGetConfig(request, response, dataId, group, tenant, tag, clientIp);
    }
...
}


//inner.doGetConfig
 /**
     * 同步配置获取接口
     */
    public String doGetConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,String tenant, String tag, String clientIp) throws IOException, ServletException {
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String autoTag = request.getHeader("Vipserver-Tag");
        String requestIpApp = RequestUtil.getAppName(request);
        //这里加读锁 尝试加锁，最多10次 零表示没有数据，失败。正数表示成功，负数表示有写锁导致加锁失败。
        int lockResult = tryConfigReadLock(groupKey);

        final String requestIp = RequestUtil.getRemoteIp(request);
        boolean isBeta = false;
        if (lockResult > 0) {
            FileInputStream fis = null;
            try {
                String md5 = Constants.NULL;
                long lastModified = 0L;
                CacheItem cacheItem = ConfigService.getContentCache(groupKey);
                if (cacheItem != null) {
                    if (cacheItem.isBeta()) {
                        if (cacheItem.getIps4Beta().contains(clientIp)) {
                            isBeta = true;
                        }
                    }
                }
                File file = null;
                ConfigInfoBase configInfoBase = null;
                PrintWriter out = null;
                if (isBeta) {
                    md5 = cacheItem.getMd54Beta();
                    lastModified = cacheItem.getLastModifiedTs4Beta();
                    if (STANDALONE_MODE && !PropertyUtil.isStandaloneUseMysql()) {
                        configInfoBase = persistService.findConfigInfo4Beta(dataId, group, tenant);
                    } else {
                        file = DiskUtil.targetBetaFile(dataId, group, tenant);
                    }
                    response.setHeader("isBeta", "true");
                } else {
                    if (StringUtils.isBlank(tag)) {
                        if (isUseTag(cacheItem, autoTag)) {
                            if (cacheItem != null) {
                                if (cacheItem.tagMd5 != null) {
                                    md5 = cacheItem.tagMd5.get(autoTag);
                                }
                                if (cacheItem.tagLastModifiedTs != null) {
                                    lastModified = cacheItem.tagLastModifiedTs.get(autoTag);
                                }
                            }
                            if (STANDALONE_MODE && !PropertyUtil.isStandaloneUseMysql()) {
                                //通过持久化数据库获取
                                configInfoBase = persistService.findConfigInfo4Tag(dataId, group, tenant, autoTag);
                            } else {
                                //本地磁盘文件获取;例如：
                                //C:\Users\vison\nacos\data\tenant-config-data\b1092a4a-3b8d-4e33-8874-55cee3839c1f\DEFAULT_GROUP\dubbo.properties
                                file = DiskUtil.targetTagFile(dataId, group, tenant, autoTag);
                            }

                            response.setHeader("Vipserver-Tag",
                                URLEncoder.encode(autoTag, StandardCharsets.UTF_8.displayName()));
                        } else {
                            md5 = cacheItem.getMd5();
                            lastModified = cacheItem.getLastModifiedTs();
                            if (STANDALONE_MODE && !PropertyUtil.isStandaloneUseMysql()) {
                                configInfoBase = persistService.findConfigInfo(dataId, group, tenant);
                            } else {
                                file = DiskUtil.targetFile(dataId, group, tenant);
                            }
                            if (configInfoBase == null && fileNotExist(file)) {
                                // FIXME CacheItem
                                // 不存在了无法简单的计算推送delayed，这里简单的记做-1
                                ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1,
                                    ConfigTraceService.PULL_EVENT_NOTFOUND, -1, requestIp);

                                // pullLog.info("[client-get] clientIp={}, {},
                                // no data",
                                // new Object[]{clientIp, groupKey});

                                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                                response.getWriter().println("config data not exist");
                                return HttpServletResponse.SC_NOT_FOUND + "";
                            }
                        }
                    } else {
                        if (cacheItem != null) {
                            if (cacheItem.tagMd5 != null) {
                                md5 = cacheItem.tagMd5.get(tag);
                            }
                            if (cacheItem.tagLastModifiedTs != null) {
                                Long lm = cacheItem.tagLastModifiedTs.get(tag);
                                if (lm != null) {
                                    lastModified = lm;
                                }
                            }
                        }
                        if (STANDALONE_MODE && !PropertyUtil.isStandaloneUseMysql()) {
                            configInfoBase = persistService.findConfigInfo4Tag(dataId, group, tenant, tag);
                        } else {
                            file = DiskUtil.targetTagFile(dataId, group, tenant, tag);
                        }
                        if (configInfoBase == null && fileNotExist(file)) {
                            // FIXME CacheItem
                            // 不存在了无法简单的计算推送delayed，这里简单的记做-1
                            ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1,
                                ConfigTraceService.PULL_EVENT_NOTFOUND,
                                -1, requestIp);

                            // pullLog.info("[client-get] clientIp={}, {},
                            // no data",
                            // new Object[]{clientIp, groupKey});

                            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                            response.getWriter().println("config data not exist");
                            return HttpServletResponse.SC_NOT_FOUND + "";
                        }
                    }
                }

                response.setHeader(Constants.CONTENT_MD5, md5);
                /**
                 *  禁用缓存
                 */
                response.setHeader("Pragma", "no-cache");
                response.setDateHeader("Expires", 0);
                response.setHeader("Cache-Control", "no-cache,no-store");
                if (STANDALONE_MODE && !PropertyUtil.isStandaloneUseMysql()) {
                    response.setDateHeader("Last-Modified", lastModified);
                } else {
                    fis = new FileInputStream(file);
                    response.setDateHeader("Last-Modified", file.lastModified());
                }

                if (STANDALONE_MODE && !PropertyUtil.isStandaloneUseMysql()) {
                    out = response.getWriter();
                    out.print(configInfoBase.getContent());
                    out.flush();
                    out.close();
                } else {
                    fis.getChannel().transferTo(0L, fis.getChannel().size(),
                        Channels.newChannel(response.getOutputStream()));
                }

                LogUtil.pullCheckLog.warn("{}|{}|{}|{}", groupKey, requestIp, md5, TimeUtils.getCurrentTimeStr());

                final long delayed = System.currentTimeMillis() - lastModified;

                // TODO distinguish pull-get && push-get
                // 否则无法直接把delayed作为推送延时的依据，因为主动get请求的delayed值都很大
                ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, lastModified,
                    ConfigTraceService.PULL_EVENT_OK, delayed,
                    requestIp);

            } finally {
                releaseConfigReadLock(groupKey);
                if (null != fis) {
                    fis.close();
                }
            }
        } else if (lockResult == 0) {

            // FIXME CacheItem 不存在了无法简单的计算推送delayed，这里简单的记做-1
            ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1,
                ConfigTraceService.PULL_EVENT_NOTFOUND, -1, requestIp);

            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().println("config data not exist");
            return HttpServletResponse.SC_NOT_FOUND + "";

        } else {

            pullLog.info("[client-get] clientIp={}, {}, get data during dump", clientIp, groupKey);

            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.getWriter().println("requested file is being modified, please try later.");
            return HttpServletResponse.SC_CONFLICT + "";

        }

        return HttpServletResponse.SC_OK + "";
    }

```





## 1.3 综合客户端/服务端解读长轮训源码

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
 
 * @param probeUpdateString: 这里值客户端本地缓存的数据，通过组装为string做探测；通过分隔号区分不同的数据，其中包含了配置的md5信息
   例如值：wm-weimiao-redis.propertiesDEFAULT_GROUPaf56c97225efa306fa17f2797061023bb1092a4a-3b8d-4e33-8874-55cee3839c1fwm-weimiao-payment.propertiesDEFAULT_GROUP803be2e961132d422e2dae230d433cf1b1092a4a-3b8d-4e33-8874-55cee3839c1fwm-weimiao-mutil-config.propertiesDEFAULT_GROUP3b75b51509525a82e97422bbd8a43a99b1092a4a-3b8d-4e33-8874-55cee3839c1fwm-weimiao-jdbc.propertiesDEFAULT_GROUP503a67b52af54d49ffc99c6b3a571d34b1092a4a-3b8d-4e33-8874-55cee3839c1fwm-weimiao-third-party.propertiesDEFAULT_GROUP1467d25a4e03a9e7fa37933bda2d5d1ab1092a4a-3b8d-4e33-8874-55cee3839c1fdubbo.propertiesDEFAULT_GROUP03b1346ccdbf9a218590699ce559bb08b1092a4a-3b8d-4e33-8874-55cee3839c1f
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
		//如果本地没有换成配置，那么直接返回
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
                //如果有返回结果表示有更新那么就会更新我们本地缓存的信息
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

**ConfigController**

​		nacos是使用spring mvc提供的rest api。这里面会调用inner.doPollingConfig进行处理

```java

public class ConfigControler{
/**
     * 比较MD5
     */
    @PostMapping("/listener")
    public void listener(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        request.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", true);
        String probeModify = request.getParameter("Listening-Configs");
        if (StringUtils.isBlank(probeModify)) {
            throw new IllegalArgumentException("invalid probeModify");
        }

        probeModify = URLDecoder.decode(probeModify, Constants.ENCODE);

        Map<String, String> clientMd5Map;
        try {
            clientMd5Map = MD5Util.getClientMd5Map(probeModify);
        } catch (Throwable e) {
            throw new IllegalArgumentException("invalid probeModify");
        }

        // do long-polling
        inner.doPollingConfig(request, response, clientMd5Map, probeModify.length());
    }
}
```

**doPollingConfifig**

这个方法中，兼容了长轮训和短轮询的逻辑，我们只需要关注长轮训的部分。再次进入到 longPollingService.addLongPollingClient 



**longPollingService.addLongPollingClient**

- 从方法名字上可以推测出，这个方法应该是把客户端的长轮训请求添加到某个任务中去。 获得客户端传递过来的超时时间，并且进行本地计算，提前500ms返回响应，这就能解释为什么 客户端响应超时时间是29.5+了。当然如果 isFixedPolling=true 的情况下，不会提前返回响应

- 根据客户端请求过来的md5和服务器端对应的group下对应内容的md5进行比较，如果不一致，则 通过 generateResponse 将结果返回 ;

- 如果配置文件没有发生变化，则通过 scheduler.execute 启动了一个定时任务，将客户端的长轮 询请求封装成一个叫 ClientLongPolling 的任务，交给 scheduler 去执行 

```java
public void addLongPollingClient(HttpServletRequest req, HttpServletResponse rsp, Map<String, String> clientMd5Map,
                                 int probeRequestSize) {

    String str = req.getHeader(LongPollingService.LONG_POLLING_HEADER);
    String noHangUpFlag = req.getHeader(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER);
    String appName = req.getHeader(RequestUtil.CLIENT_APPNAME_HEADER);
    String tag = req.getHeader("Vipserver-Tag");
    int delayTime = SwitchService.getSwitchInteger(SwitchService.FIXED_DELAY_TIME, 500);
    /**
         * 提前500ms返回响应，为避免客户端超时 @qiaoyi.dingqy 2013.10.22改动  add delay time for LoadBalance
         */
    long timeout = Math.max(10000, Long.parseLong(str) - delayTime);
    if (isFixedPolling()) {
        timeout = Math.max(10000, getFixedPollingInterval());
        // do nothing but set fix polling timeout
    } else {
        long start = System.currentTimeMillis();
        List<String> changedGroups = MD5Util.compareMd5(req, rsp, clientMd5Map);
        if (changedGroups.size() > 0) {
            generateResponse(req, rsp, changedGroups);
            LogUtil.clientLog.info("{}|{}|{}|{}|{}|{}|{}",
                                   System.currentTimeMillis() - start, "instant", RequestUtil.getRemoteIp(req), "polling",
                                   clientMd5Map.size(), probeRequestSize, changedGroups.size());
            return;
        } else if (noHangUpFlag != null && noHangUpFlag.equalsIgnoreCase(TRUE_STR)) {
            LogUtil.clientLog.info("{}|{}|{}|{}|{}|{}|{}", System.currentTimeMillis() - start, "nohangup",
                                   RequestUtil.getRemoteIp(req), "polling", clientMd5Map.size(), probeRequestSize,
                                   changedGroups.size());
            return;
        }
    }
    String ip = RequestUtil.getRemoteIp(req);
    // 一定要由HTTP线程调用，否则离开后容器会立即发送响应
    final AsyncContext asyncContext = req.startAsync();
    // AsyncContext.setTimeout()的超时时间不准，所以只能自己控制
    asyncContext.setTimeout(0L);

    scheduler.execute(
        new ClientLongPolling(asyncContext, clientMd5Map, ip, probeRequestSize, timeout, appName, tag));
}
```

**ClientLongPolling**

​	在run方法中，通过scheduler.schedule实现了 一个定时任务，它的delay时间正好是前面计算的29.5s。在这个任务中，会通过MD5Util.compareMd5 来进行计算

​	那另外一个，当数据发生变化以后，肯定不能等到29.5s之后才通知呀，那怎么办呢？我们发现有一个 allSubs 的东西，它似乎和发布订阅有关系。那是不是有可能当前的clientLongPolling订阅了数据变化 的事件呢?

最终通过ClientLongPolling来异步定时任务触发：

```java
class ClientLongPolling implements Runnable {

    @Override
    public void run() {
        asyncTimeoutFuture = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    getRetainIps().put(ClientLongPolling.this.ip, System.currentTimeMillis());
                    /**
                         * 删除订阅关系
                         */
                    allSubs.remove(ClientLongPolling.this);

                    if (isFixedPolling()) {
                        LogUtil.clientLog.info("{}|{}|{}|{}|{}|{}",
                                               (System.currentTimeMillis() - createTime),
                                               "fix", RequestUtil.getRemoteIp((HttpServletRequest)asyncContext.getRequest()),
                                               "polling",
                                               clientMd5Map.size(), probeRequestSize);
                        List<String> changedGroups = MD5Util.compareMd5(
                            (HttpServletRequest)asyncContext.getRequest(),
                            (HttpServletResponse)asyncContext.getResponse(), clientMd5Map);
                        if (changedGroups.size() > 0) {
                            sendResponse(changedGroups);
                        } else {
                            sendResponse(null);
                        }
                    } else {
                        LogUtil.clientLog.info("{}|{}|{}|{}|{}|{}",
                                               (System.currentTimeMillis() - createTime),
                                               "timeout", RequestUtil.getRemoteIp((HttpServletRequest)asyncContext.getRequest()),
                                               "polling",
                                               clientMd5Map.size(), probeRequestSize);
                        sendResponse(null);
                    }
                } catch (Throwable t) {
                    LogUtil.defaultLog.error("long polling error:" + t.getMessage(), t.getCause());
                }

            }

        }, timeoutTime, TimeUnit.MILLISECONDS);

        allSubs.add(this);
    }
```

**allSubs**

​	allSubs是一个队列，队列里面放了ClientLongPolling这个对象。这个队列似乎和配置变更有某种关联 关系

那这个时候，我的第一想法是，先去看一下当前这个类的类图，发现LongPollingService集成了 AbstractEventListener，事件监听？果然没猜错。

```java
static public abstract class AbstractEventListener {

    public AbstractEventListener() {
        /**
             * automatic register 新建的时候，把当前存储起来，会存储在一个Entry的列表中
             */
        EventDispatcher.addEventListener(this);
    }

    /**
         * 感兴趣的事件列表
         *
         * @return event list
         */
    abstract public List<Class<? extends Event>> interest();

    /**
         * 处理事件
         *
         * @param event event
         */
    abstract public void onEvent(Event event);
}
```

**LongPollingService.onEvent** 

```java
@Override
public void onEvent(Event event) {
    if (isFixedPolling()) {
        // ignore
    } else {
        if (event instanceof LocalDataChangeEvent) {
            LocalDataChangeEvent evt = (LocalDataChangeEvent)event;
            scheduler.execute(new DataChangeTask(evt.groupKey, evt.isBeta, evt.betaIps));
        }
    }
}
```

**DataChangeTask.run**

​		从名字可以看出来，这个是数据变化的任务，最让人兴奋的应该是，它里面有一个循环迭代器，从 allSubs里面获得ClientLongPolling 最后通过clientSub.sendResponse把数据返回到客户端。所以，这也就能够理解为何数据变化能够实 时触发更新了

```java
 class DataChangeTask implements Runnable {
        @Override
        public void run() {
            try {
                ConfigService.getContentBetaMd5(groupKey);
                for (Iterator<ClientLongPolling> iter = allSubs.iterator(); iter.hasNext(); ) {
                    ClientLongPolling clientSub = iter.next();
                    if (clientSub.clientMd5Map.containsKey(groupKey)) {
                        // 如果beta发布且不在beta列表直接跳过
                        if (isBeta && !betaIps.contains(clientSub.ip)) {
                            continue;
                        }

                        // 如果tag发布且不在tag列表直接跳过
                        if (StringUtils.isNotBlank(tag) && !tag.equals(clientSub.tag)) {
                            continue;
                        }

                        getRetainIps().put(clientSub.ip, System.currentTimeMillis());
                        iter.remove(); // 删除订阅关系
                        LogUtil.clientLog.info("{}|{}|{}|{}|{}|{}|{}",
                            (System.currentTimeMillis() - changeTime),
                            "in-advance",
                            RequestUtil.getRemoteIp((HttpServletRequest)clientSub.asyncContext.getRequest()),
                            "polling",
                            clientSub.clientMd5Map.size(), clientSub.probeRequestSize, groupKey);
                        clientSub.sendResponse(Arrays.asList(groupKey));
                    }
                }
            } catch (Throwable t) {
                LogUtil.defaultLog.error("data change error:" + t.getMessage(), t.getCause());
            }
        }
 }
```

### 3) 服务端的数据变化

​		那么接下来还有一个疑问是，数据变化之后是如何触发事件的呢？ 所以我们定位到数据变化的请求类 中，在ConﬁgController这个类中，找到POST请求的方法 找到配置变更的位置， 发现数据持久化之后，会通过EventDispatcher进行事件发布 EventDispatcher.fireEvent 但是这个事件似乎不是我们所关心的事件，原因是这里发布的事件是 ConfigDataChangeEvent , 而LongPollingService感兴趣的事件是 LocalDataChangeEvent

```java
  @PostMapping
public Boolean publishConfig(HttpServletRequest request, HttpServletResponse response, ....)
    throws NacosException {
    .....省略

        if (AggrWhitelist.isAggrDataId(dataId)) {
            log.warn("[aggr-conflict] {} attemp to publish single data, {}, {}",
                     RequestUtil.getRemoteIp(request), dataId, group);
            throw new NacosException(NacosException.NO_RIGHT, "dataId:" + dataId + " is aggr");
        }

    final Timestamp time = TimeUtils.getCurrentTime();
    String betaIps = request.getHeader("betaIps");
    ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
    if (StringUtils.isBlank(betaIps)) {
        if (StringUtils.isBlank(tag)) {
            persistService.insertOrUpdate(srcIp, srcUser, configInfo, time, configAdvanceInfo, false);
            EventDispatcher.fireEvent(new ConfigDataChangeEvent(false, dataId, group, tenant, time.getTime()));
        } else {
            persistService.insertOrUpdateTag(configInfo, tag, srcIp, srcUser, time, false);
            EventDispatcher.fireEvent(new ConfigDataChangeEvent(false, dataId, group, tenant, tag, time.getTime()));
        }
    } else { // beta publish
        persistService.insertOrUpdateBeta(configInfo, betaIps, srcIp, srcUser, time, false);
        //更新的时候，触发数据变化
        EventDispatcher.fireEvent(new ConfigDataChangeEvent(true, dataId, group, tenant, time.getTime()));
    }
    ConfigTraceService.logPersistenceEvent(dataId, group, tenant, requestIpApp, time.getTime(),
                                           LOCAL_IP, ConfigTraceService.PERSISTENCE_EVENT_PUB, content);

    return true;
}
```

​		后来我发现，在Nacos中有一个DumpService，它会定时把变更后的数据dump到磁盘上， DumpService在spring启动之后，会调用init方法启动几个dump任务。然后在任务执行结束之后，会 触发一个LocalDataChangeEvent 的事件

```java
@PostConstruct
public void init() {
    LogUtil.defaultLog.warn("DumpService start");
    DumpProcessor processor = new DumpProcessor(this);
    DumpAllProcessor dumpAllProcessor = new DumpAllProcessor(this);
    DumpAllBetaProcessor dumpAllBetaProcessor = new DumpAllBetaProcessor(this);
    DumpAllTagProcessor dumpAllTagProcessor = new DumpAllTagProcessor(this);
	。。。。
         try {
            dumpConfigInfo(dumpAllProcessor);
         ....
         }
}


private void dumpConfigInfo(DumpAllProcessor dumpAllProcessor)
    throws IOException {
    int timeStep = 6;
    Boolean isAllDump = true;
    // initial dump all
    FileInputStream fis = null;
    Timestamp heartheatLastStamp = null;
    try {
        if (isQuickStart()) {
            File heartbeatFile = DiskUtil.heartBeatFile();
            if (heartbeatFile.exists()) {
                fis = new FileInputStream(heartbeatFile);
                String heartheatTempLast = IoUtils.toString(fis, Constants.ENCODE);
                heartheatLastStamp = Timestamp.valueOf(heartheatTempLast);
                if (TimeUtils.getCurrentTime().getTime()
                    - heartheatLastStamp.getTime() < timeStep * 60 * 60 * 1000) {
                    isAllDump = false;
                }
            }
        }
        if (isAllDump) {
            LogUtil.defaultLog.info("start clear all config-info.");
            DiskUtil.clearAll();
            dumpAllProcessor.process(DumpAllTask.TASK_ID, new DumpAllTask());
        } else {
            Timestamp beforeTimeStamp = getBeforeStamp(heartheatLastStamp,
                                                       timeStep);
            DumpChangeProcessor dumpChangeProcessor = new DumpChangeProcessor(
                this, beforeTimeStamp, TimeUtils.getCurrentTime());
            dumpChangeProcessor.process(DumpChangeTask.TASK_ID, new DumpChangeTask());
            Runnable checkMd5Task = () -> {
                LogUtil.defaultLog.error("start checkMd5Task");
                List<String> diffList = ConfigService.checkMd5();
                for (String groupKey : diffList) {
                    String[] dg = GroupKey.parseKey(groupKey);
                    String dataId = dg[0];
                    String group = dg[1];
                    String tenant = dg[2];
                    //这里比较服务端日志缓存和数据库的数据是否不同
                    ConfigInfoWrapper configInfo = persistService.queryConfigInfo(dataId, group, tenant);
                    ConfigService.dumpChange(dataId, group, tenant, configInfo.getContent(),
                                             configInfo.getLastModified());
                }
                LogUtil.defaultLog.error("end checkMd5Task");
            };
            //这里有一个定时文件定时检查md5是否更新，否则就处罚LocalDataChange事件
            TimerTaskService.scheduleWithFixedDelay(checkMd5Task, 0, 12,
                                                    TimeUnit.HOURS);
        }
    } catch (IOException e) {
        LogUtil.fatalLog.error("dump config fail" + e.getMessage());
        throw e;
    } finally {
        if (null != fis) {
            try {
                fis.close();
            } catch (IOException e) {
                LogUtil.defaultLog.warn("close file failed");
            }
        }
    }
}
```



### 4) 总结

简单总结一下刚刚分析的整个过程。 

- 客户端发起长轮训请求 

- 服务端收到请求以后，先比较服务端缓存中的数据是否相同，如果不通，则直接返回 

- 如果相同，则通过schedule延迟29.5s之后再执行比较 

- 为了保证当服务端在29.5s之内发生数据变化能够及时通知给客户端，服务端采用事件订阅的方式 来监听服务端本地数据变化的事件，一旦收到事件，则触发DataChangeTask的通知，并且遍历 allStubs队列中的ClientLongPolling,把结果写回到客户端，就完成了一次数据的推送 

- 如果 DataChangeTask 任务完成了数据的 “推送” 之后，ClientLongPolling 中的调度任务又开始执 行了怎么办呢？ 

  ​     很简单，只要在进行 “推送” 操作之前，先将原来等待执行的调度任务取消掉就可以了，这样就防 止了推送操作写完响应数据之后，调度任务又去写响应数据，这时肯定会报错的。所以，在 ClientLongPolling方法中，最开始的一个步骤就是删除订阅事件 



​	   所以总的来说，Nacos采用推+拉的形式，来解决最开始关于长轮训时间间隔的问题。当然，30s这个时 间是可以设置的，而之所以定30s，应该是一个经验值。



## 1.4 服务端数据主动更新引起的数据变化

举例：**CofigController**我们后台跟新配置中心的数据

```java
    @PostMapping
    public Boolean publishConfig(HttpServletRequest request, HttpServletResponse response,  ...{
		...
			//这会在config_info更新记录和history_config_info存储一条历史记录
            persistService.insertOrUpdateBeta(configInfo, betaIps, srcIp, srcUser, time, false);
        	//主要是触发监听器 AsyncNotifyService的使用
            EventDispatcher.fireEvent(new ConfigDataChangeEvent(true, dataId, group, tenant, time.getTime()));
        。。。
        ConfigTraceService.logPersistenceEvent(dataId, group, tenant, requestIpApp, time.getTime(),
            LOCAL_IP, ConfigTraceService.PERSISTENCE_EVENT_PUB, content);

        return true;
    }
```

```java
@Service
public class AsyncNotifyService extends AbstractEventListener {   
    ....
	public void onEvent(Event event) {

        // 并发产生 ConfigDataChangeEvent
        if (event instanceof ConfigDataChangeEvent) {
            ConfigDataChangeEvent evt = (ConfigDataChangeEvent) event;
            long dumpTs = evt.lastModifiedTs;
            String dataId = evt.dataId;
            String group = evt.group;
            String tenant = evt.tenant;
            String tag = evt.tag;
            List<?> ipList = serverListService.getServerList();

            // 其实这里任何类型队列都可以
            Queue<NotifySingleTask> queue = new LinkedList<NotifySingleTask>();
            for (int i = 0; i < ipList.size(); i++) {
                //注意NotifySingleTask中url的封装，这里会调用//v1/cs/communication/dataChange
                queue.add(new NotifySingleTask(dataId, group, tenant, tag, dumpTs, (String) ipList.get(i), evt.isBeta));
            }
            //这里异步执行AysncTask的任务；这里也是处理上面队列中的任务；
            EXECUTOR.execute(new AsyncTask(httpclient, queue));
        }
    }
}
```

```java
class AsyncTask implements Runnable {

    public AsyncTask(CloseableHttpAsyncClient httpclient, Queue<NotifySingleTask> queue) {
        this.httpclient = httpclient;
        this.queue = queue;
    }

    @Override
    public void run() {
        executeAsyncInvoke();
    }

    private void executeAsyncInvoke() {
        //遍历发送不同服务器的通知；通知消息更改了
        while (!queue.isEmpty()) {
            NotifySingleTask task = queue.poll();
            String targetIp = task.getTargetIP();
            if (serverListService.getServerList().contains(
                targetIp)) {
                // 启动健康检查且有不监控的ip则直接把放到通知队列，否则通知
                if (serverListService.isHealthCheck()
                    && ServerListService.getServerListUnhealth().contains(targetIp)) {
                    // target ip 不健康，则放入通知列表中
                    ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                                                      task.getLastModified(),
                                                      LOCAL_IP, ConfigTraceService.NOTIFY_EVENT_UNHEALTH, 0, task.target);
                    // get delay time and set fail count to the task
                    asyncTaskExecute(task);
                } else {
                    HttpGet request = new HttpGet(task.url);
                    request.setHeader(NotifyService.NOTIFY_HEADER_LAST_MODIFIED,
                                      String.valueOf(task.getLastModified()));
                    request.setHeader(NotifyService.NOTIFY_HEADER_OP_HANDLE_IP, LOCAL_IP);
                    if (task.isBeta) {
                        request.setHeader("isBeta", "true");
                    }
                    //发送通知 调用//v1/cs/communication/dataChange
                    httpclient.execute(request, new AsyncNotifyCallBack(httpclient, task));
                }
            }
        }
    }
```

**CommunicationController**

通过上面知道，如果配置中心数据变化，会在这个控制器中处理，而且上面会分别发送给不同的服务器中；

```java
/**
 * 通知配置信息改变
 */
@GetMapping("/dataChange")
public Boolean notifyConfigInfo(HttpServletRequest request,
                                @RequestParam("dataId") String dataId, @RequestParam("group") String group,
                                @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY)
                                String tenant,
                                @RequestParam(value = "tag", required = false) String tag) {
    dataId = dataId.trim();
    group = group.trim();
    String lastModified = request.getHeader(NotifyService.NOTIFY_HEADER_LAST_MODIFIED);
    long lastModifiedTs = StringUtils.isEmpty(lastModified) ? -1 : Long.parseLong(lastModified);
    String handleIp = request.getHeader(NotifyService.NOTIFY_HEADER_OP_HANDLE_IP);
    String isBetaStr = request.getHeader("isBeta");
    if (StringUtils.isNotBlank(isBetaStr) && trueStr.equals(isBetaStr)) {
        dumpService.dump(dataId, group, tenant, lastModifiedTs, handleIp, true);
    } else {
        //然后会执行这个dump操作
        dumpService.dump(dataId, group, tenant, tag, lastModifiedTs, handleIp);
    }
    return true;
}
```

然后DumpService

```java
 public void dump(String dataId, String group, String tenant, String tag, long lastModified, String handleIp,
                     boolean isBeta) {
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
     	//这里就会在TaskManager中把新建的dumpTask用Map存储起来

        dumpTaskMgr.addTask(groupKey, new DumpTask(groupKey, tag, lastModified, handleIp, isBeta));
    }
```

上面添加task流程就结束了；说明有一个后台线程不停的从这个队列中获取任务处理，所以我们全局查询tasks的使用；最终发现，在创建TaskManager的时候启动了一个precessingThread去处理该队列

```java
  public TaskManager(String name) {
        this.name = name;
        if (null != name && name.length() > 0) {
            //创建了这个后台线程；
            this.processingThread = new Thread(new ProcessRunnable(), name);
        } else {
            this.processingThread = new Thread(new ProcessRunnable());
        }
      	//设置为后台线程
        this.processingThread.setDaemon(true);
        this.closed.set(false);
      	//开启任务了
        this.processingThread.start();
    }
```

**继续看ProcessRunnable**

```java
    class ProcessRunnable implements Runnable {

        @Override
        public void run() {
            while (!TaskManager.this.closed.get()) {
                try {
                    //这里核心业务，休眠100毫秒继续处理
                    Thread.sleep(100);
                    TaskManager.this.process();
                } catch (Throwable e) {
                }
            }

        }

    }


protected void process() {
        for (Map.Entry<String, AbstractTask> entry : this.tasks.entrySet()) {
            AbstractTask task = null;
            this.lock.lock();
            try {
                // 获取任务
                task = entry.getValue();
                if (null != task) {
                    if (!task.shouldProcess()) {
                        // 任务当前不需要被执行，直接跳过
                        continue;
                    }
                    // 先将任务从任务Map中删除
                    this.tasks.remove(entry.getKey());
                    MetricsMonitor.getDumpTaskMonitor().set(tasks.size());
                }
            } finally {
                this.lock.unlock();
            }

            if (null != task) {
                // 获取任务处理器
                TaskProcessor processor = this.taskProcessors.get(entry.getKey());
                if (null == processor) {
                    // 如果没有根据任务类型设置的处理器，使用默认处理器
                    processor = this.getDefaultTaskProcessor();
                }
                if (null != processor) {
                    boolean result = false;
                    try {
                        // 处理任务;这里是核心业务处理，默认使用DumpProcessor
                        result = processor.process(entry.getKey(), task);
                    } catch (Throwable t) {
                        log.error("task_fail", "处理task失败", t);
                    }
                    if (!result) {
                        // 任务处理失败，设置最后处理时间
                        task.setLastProcessTime(System.currentTimeMillis());

                        // 将任务重新加入到任务Map中
                        this.addTask(entry.getKey(), task);
                    }
                }
            }
        }

        if (tasks.isEmpty()) {
            this.lock.lock();
            try {
                this.notEmpty.signalAll();
            } finally {
                this.lock.unlock();
            }
        }
    }
```

所以又到了**DumpProcessor**处理该任务

```java
@Override
public boolean process(String taskType, AbstractTask task) {
    
    ...
    ConfigInfo cf = dumpService.persistService.findConfigInfo(dataId, group, tenant);
    if (null != cf) {
    result = ConfigService.dump(dataId, group, tenant, cf.getContent(), lastModified);

}
```

最后又回到了**ConfigService.dump**

```java
/**
  * 保存配置文件，并缓存md5.
  */
static public boolean dump(String dataId, String group, String tenant, String content, long lastModifiedTs) {
    String groupKey = GroupKey2.getKey(dataId, group, tenant);
    makeSure(groupKey);
    final int lockResult = tryWriteLock(groupKey);
    assert (lockResult != 0);

    if (lockResult < 0) {
        dumpLog.warn("[dump-error] write lock failed. {}", groupKey);
        return false;
    }

    try {
        final String md5 = MD5.getInstance().getMD5String(content);
        //相同的话就不修改了
        if (md5.equals(ConfigService.getContentMd5(groupKey))) {
            dumpLog.warn(
                "[dump-ignore] ignore to save cache file. groupKey={}, md5={}, lastModifiedOld={}, "
                + "lastModifiedNew={}",
                groupKey, md5, ConfigService.getLastModifiedTs(groupKey), lastModifiedTs);
        } else if (!STANDALONE_MODE || PropertyUtil.isStandaloneUseMysql()) {
            //这里就是我们本地的配置\nacos\data\tenant-config-data\b1092a4a-3b8d-4e33-8874-55cee3839c1f\DEFAULT_GROUP\dubbo.properties
            DiskUtil.saveToDisk(dataId, group, tenant, content);
        }
        //更新内存数据和发送本地数据更新事件，对于客户端长轮训可以返回数据了
        updateMd5(groupKey, md5, lastModifiedTs);
        return true;
    } catch (IOException ioe) {
        dumpLog.error("[dump-exception] save disk error. " + groupKey + ", " + ioe.toString(), ioe);
        if (ioe.getMessage() != null) {
            String errMsg = ioe.getMessage();
            if (NO_SPACE_CN.equals(errMsg) || NO_SPACE_EN.equals(errMsg) || errMsg.contains(DISK_QUATA_CN)
                || errMsg.contains(DISK_QUATA_EN)) {
                // 磁盘写满保护代码
                fatalLog.error("磁盘满自杀退出", ioe);
                System.exit(0);
            }
        }
        return false;
    } finally {
        releaseWriteLock(groupKey);
    }
}

```

```java
public static void updateMd5(String groupKey, String md5, long lastModifiedTs) {
    //缓存更新
    CacheItem cache = makeSure(groupKey);
    if (cache.md5 == null || !cache.md5.equals(md5)) {
        cache.md5 = md5;
        cache.lastModifiedTs = lastModifiedTs;
        //发送本地数据更新事件，对于客户端长轮训可以返回数据了
        EventDispatcher.fireEvent(new LocalDataChangeEvent(groupKey));
    }
}
```

