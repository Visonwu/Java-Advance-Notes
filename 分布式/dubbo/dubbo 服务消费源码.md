如果要实现服务的消费，来思考一下
1. 生成远程服务的代理
2. 获得目标服务的url地址
3. 实现远程网络通信
4. 实现负载均衡
5. 实现集群容错

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g6316nexv2j20l80bogr4.jpg)

消费端的代码解析是从下面这段代码开始的

```xml
<dubbo:reference id="xxxService" interface="xxx.xxx.Service"/>
```

注解的方式的初始化入口是

```java
ReferenceAnnotationBeanPostProcessor->ReferenceBeanInvocationHandler.init-
>ReferenceConfig.get() 获得一个远程代理类
```

# 1.入口 ReferenceConfig.get(）

```java
public class ReferenceConfig<T> extends AbstractReferenceConfig {
	public synchronized T get() {
        checkAndUpdateSubConfigs();

        if (destroyed) {
            throw new IllegalStateException("The invoker of ReferenceConfig(" + url + ") has already destroyed!");
        }
        if (ref == null) {//如果当前接口的远程代理引用为空，则进行初始化
            init();
        }
        return ref;
    }

    //初始化的过程，和服务发布的过程类似，会有特别多的判断以及参数的组装. 我们只需要关注createProxy，创建代理类的方法。
    private void init() {
         ....
        ref = createProxy(map);
         ....
    }
}
```

# 2.Reference.createProxy()

代码比较长，但是逻辑相对比较清晰
1. 判断是否为本地调用，如果是则使用injvm协议进行调用
2. 判断是否为点对点调用，如果是则把url保存到urls集合中，如果url为1，进入步骤4，如果urls>1，则执行5
3. 如果是配置了注册中心，遍历注册中心，把url添加到urls集合，url为1，进入步骤4，如果urls>1，则执行5
4. 直连构建invoker
5. 构建invokers集合，通过cluster合并多个invoker
6. 最后调用 ProxyFactory 生成代理类

```java
  private T createProxy(Map<String, String> map) {
        if (shouldJvmRefer(map)) {//判断是否是在同一个jvm进程中调用
            URL url = new URL(Constants.LOCAL_PROTOCOL, Constants.LOCALHOST_VALUE, 0, interfaceClass.getName()).addParameters(map);
            invoker = refprotocol.refer(interfaceClass, url);
            if (logger.isInfoEnabled()) {
                logger.info("Using injvm service " + interfaceClass.getName());
            }
        } else {
            //url 如果不为空，说明是点对点通信
            if (url != null && url.length() > 0) { // user specified URL, could be peer-to-peer address, or register center's address.
                String[] us = Constants.SEMICOLON_SPLIT_PATTERN.split(url);
                if (us != null && us.length > 0) {
                    for (String u : us) {
                        URL url = URL.valueOf(u);
                        if (StringUtils.isEmpty(url.getPath())) {
                            url = url.setPath(interfaceName);
                        }
                    // 检测 url 协议是否为 registry，若是，表明用户想使用指定的注册中心
                        if (Constants.REGISTRY_PROTOCOL.equals(url.getProtocol())) {
                            urls.add(url.addParameterAndEncoded(Constants.REFER_KEY, StringUtils.toQueryString(map)));
                        } else {
                  // 合并 url，移除服务提供者的一些配置（这些配置来源于用户配置的 url 属性），
                 // 比如线程池相关配置。并保留服务提供者的部分配置，比如版本，group，时间戳等
                 // 最后将合并后的配置设置为 url 查询字符串中。
                            urls.add(ClusterUtils.mergeUrl(url, map));
                        }
                    }
                }
            } else { // assemble URL from register center's configuration
                checkRegistry(); //校验注册中心的配置以及是否有必要从配置中心组装url
               
		//这里的代码实现和服务端类似，也是根据注册中心配置进行解析得到URL
		//这里的URL肯定也是：registry://ip:port/org.apache.dubbo.service.RegsitryService
                List<URL> us = loadRegistries(false);
                if (CollectionUtils.isNotEmpty(us)) {
                    for (URL u : us) {
                        URL monitorUrl = loadMonitor(u);
                        if (monitorUrl != null) {
                            map.put(Constants.MONITOR_KEY, URL.encode(monitorUrl.toFullString()));
                        }
                        urls.add(u.addParameterAndEncoded(Constants.REFER_KEY, StringUtils.toQueryString(map)));
                    }
                }
                if (urls.isEmpty()) {
                    throw new IllegalStateException("No such any registry to reference " + interfaceName + " on the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion() + ", please config <dubbo:registry address=\"...\" /> to your spring config.");
                }
            }

            if (urls.size() == 1) {
               //如果值配置了一个注册中心或者一个服务提供者，直接使用refprotocol.refer
                invoker = refprotocol.refer(interfaceClass, urls.get(0));
            } else {
                List<Invoker<?>> invokers = new ArrayList<Invoker<?>>();
                URL registryURL = null;
                for (URL url : urls) {
                    invokers.add(refprotocol.refer(interfaceClass, url));
                    if (Constants.REGISTRY_PROTOCOL.equals(url.getProtocol())) {
                        registryURL = url; // use last registry url
                    }
                }
                if (registryURL != null) { // registry url is available
                    // // 使用RegistryAwareCluster
                    URL u = registryURL.addParameter(Constants.CLUSTER_KEY, RegistryAwareCluster.NAME);
                    // 通过Cluster将多个invoker合并
                    //RegistryAwareClusterInvoker(StaticDirectory) -> FailoverClusterInvoker(RegistryDirectory, will execute route) -> Invoker
                    invoker = cluster.join(new StaticDirectory(u, invokers));
                } else { // not a registry url, must be direct invoke.
                    invoker = cluster.join(new StaticDirectory(invokers));
                }
            }
        }

        if (shouldCheck() && !invoker.isAvailable()) { //检查invoker的有效性
           。。。。
        }
        
        // create service proxy
        return (T) proxyFactory.getProxy(invoker);
    }
```

## 2.1.Protocol.refer()

```java
invoker = refprotocol.refer(interfaceClass, urls.get(0));
```

​	这里通过指定的协议来调用refer生成一个invoker对象，invoker前面讲过，它是一个代理对象。那么在
当前的消费端而言，invoker主要用于执行远程调用。这个protocol，又是一个自适应扩展点，它得到的是一个Protocol$Adaptive.

```java
  private static final Protocol refprotocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

```

所以这里和发布源码的Protocol相类似.

### 1）RegistryProtocol.refer()

这里面的代码逻辑比较简单

- 组装注册中心协议的url
- 判断是否配置了group，如果有，则cluster=getMergeableCluster()，构建invoker
- doRefer构建invoker



这段代码中，根据当前的协议url，得到一个指定的扩展点，传递进来的参数中，协议地址为
registry://，所以，我们可以直接定位到RegistryProtocol.refer代码

```java
public class RegistryProtocol implements Protocol { 
	@Override
    @SuppressWarnings("unchecked")
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        //这段代码也很熟悉，就是根据配置的协议，生成注册中心的url: zookeeper://
        url = URLBuilder.from(url)
                .setProtocol(url.getParameter(REGISTRY_KEY, DEFAULT_REGISTRY))
                .removeParameter(REGISTRY_KEY)
                .build();
        Registry registry = registryFactory.getRegistry(url);
        if (RegistryService.class.equals(type)) {
            return proxyFactory.getInvoker((T) registry, type, url);
        }

        // 解析group参数，根据group决定cluster的类型
        // group="a,b" or group="*"
        Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(REFER_KEY));
        String group = qs.get(Constants.GROUP_KEY);
        if (group != null && group.length() > 0) {
            if ((COMMA_SPLIT_PATTERN.split(group)).length > 1 || "*".equals(group)) {
                return doRefer(getMergeableCluster(), registry, type, url);
            }
        }
        return doRefer(cluster, registry, type, url);
    }
}
```

### 2）RegistryProtocol.doRefer()

doRefer里面就稍微复杂一些，涉及到比较多的东西，我们先关注主线

- 构建一个RegistryDirectory
- 构建一个consumer://协议的地址注册到注册中心
- 订阅zookeeper中节点的变化
- 调用cluster.join方法

```java
  private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
      //RegistryDirectory初始化
        RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        directory.setRegistry(registry);
        directory.setProtocol(protocol);
        // all attributes of REFER_KEY
        Map<String, String> parameters = new HashMap<String, String>(directory.getUrl().getParameters());
      //注册consumer://协议的url
        URL subscribeUrl = new URL(CONSUMER_PROTOCOL, parameters.remove(REGISTER_IP_KEY), 0, type.getName(), parameters);
        if (!ANY_VALUE.equals(url.getServiceInterface()) && url.getParameter(REGISTER_KEY, true)) {
            directory.setRegisteredConsumerUrl(getRegisteredConsumerUrl(subscribeUrl, url));
            registry.register(directory.getRegisteredConsumerUrl());
        }
      //订阅事件监听
        directory.buildRouterChain(subscribeUrl);
        directory.subscribe(subscribeUrl.addParameter(CATEGORY_KEY,
                PROVIDERS_CATEGORY + "," + CONFIGURATORS_CATEGORY + "," + ROUTERS_CATEGORY));
		//构建invoker
        Invoker invoker = cluster.join(directory);
        ProviderConsumerRegTable.registerConsumer(invoker, url, subscribeUrl, directory);
        return invoker;
    }
```

#####  Cluster是什么？

我们只关注一下Invoker这个代理类的创建过程,其他的暂且不关心

```java
 Invoker invoker = cluster.join(directory);
```

cluster其实是在RegistryProtocol中通过set方法完成依赖注入的，并且，它还是一个被包装的

```java
public void setCluster(Cluster cluster) {
	this.cluster = cluster;
}
```

`同样Cluster也是一个SPI动态扩展点`

​		在动态适配的类中会基于extName，选择一个合适的扩展点进行适配，由于默认情况下cluster:failover，所以getExtension("failover")理论上应该返回FailOverCluster。但实际上，这里做了包装`MockClusterWrapper（FailOverCluster）`，类似其他的Wrapper包装



所以cluster.join这里实际是通过`MockClusterWrapper（FailOverCluster）`来调用的

```java
public class MockClusterWrapper implements Cluster {
    private Cluster cluster;
    public MockClusterWrapper(Cluster cluster) {
        this.cluster = cluster;
    }
    @Override
    public <T> Invoker<T> join(Directory<T> directory) throws RpcException {
        return new MockClusterInvoker<T>(directory,
                this.cluster.join(directory));
    }
}
```

```java
public class FailoverCluster implements Cluster {
    public final static String NAME = "failover";
    @Override
    public <T> Invoker<T> join(Directory<T> directory) throws RpcException {
        return new FailoverClusterInvoker<T>(directory);
    }
}
```

所以最终主类的invoker实际上是 MockClusterInvoker(FailoverClusterInvoker(RegistryDirectory))

## 2.2 proxyFactory.getProxy

回到`Refercence.createProxy`方法中，最后这里返回用拿到的invoker会调用获得一个动态代理类

```java
// create service proxy
return (T) proxyFactory.getProxy(invoker);
```

而这里的proxyFactory又是一个自适应扩展点，所以会进入下面的方法

```java
public class JavassistProxyFactory extends AbstractProxyFactory {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        return (T) Proxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
    }
    
}
```

### 1) proxy.getProxy()

上面的Proxy使用dubbo自定义的代理动态代码加载

```java
package org.apache.dubbo.common.bytecode;
public abstract class Proxy {
	public static Proxy getProxy(ClassLoader cl, Class<?>... ics) {
	try {
            ccp = ClassGenerator.newInstance(cl);
		.....
            Set<String> worked = new HashSet<>();
            List<Method> methods = new ArrayList<>();

            for (int i = 0; i < ics.length; i++) {
                if (!Modifier.isPublic(ics[i].getModifiers())) {
                    String npkg = ics[i].getPackage().getName();
                    if (pkg == null) {
                        pkg = npkg;
                    } else {
                        if (!pkg.equals(npkg)) {
                            throw new IllegalArgumentException("non-public interfaces from different packages");
                        }
                    }
                }
                ccp.addInterface(ics[i]);

                for (Method method : ics[i].getMethods()) {
                    String desc = ReflectUtils.getDesc(method);
                    if (worked.contains(desc)) {
                        continue;
                    }
                    worked.add(desc);

                    int ix = methods.size();
                    Class<?> rt = method.getReturnType();
                    Class<?>[] pts = method.getParameterTypes();

                    StringBuilder code = new StringBuilder("Object[] args = new Object[").append(pts.length).append("];");
                    for (int j = 0; j < pts.length; j++) {
                        code.append(" args[").append(j).append("] = ($w)$").append(j + 1).append(";");
                    }
                    code.append(" Object ret = handler.invoke(this, methods[").append(ix).append("], args);");
                    if (!Void.TYPE.equals(rt)) {
                        code.append(" return ").append(asArgument(rt, "ret")).append(";");
                    }

                    methods.add(method);
                    //这里的code动态生成代码
                    ccp.addMethod(method.getName(), method.getModifiers(), rt, pts, method.getExceptionTypes(), code.toString());
                }
            }
    }
}
```

这里的ics类包含了`EchoService`和我们自己的接口`IHelloSerivce`，生成的代码如下：

在上面的代码中

```java
ccp.addMethod(method.getName(), method.getModifiers(), rt, pts, method.getExceptionTypes(), code.toString());             
```

这里组装成的代码

```java
public java.lang.String sayHello(java.lang.String arg0){
    Object[] args = new Object[1];
    args[0] = ($w)$1; 
    Object ret = handler.invoke(this, methods[0], args); 
    return (java.lang.String)ret;
}
```

从这个sayHello方法可以看出，我们通过
`@Reference`注入的一个对象实例本质上就是一个动态代理类，通过调用这个类中的方法，会触发
handler.invoke(), 而这个handler就是`InvokerInvocationHandler`

## 2.3 服务注册Protocol.doRefer()

这里的URL注册的是：`consumer://192.168.124.1/com.vison.dubbospringapi.IHelloService?application=dubbo-consumer&category=consumers&check=false&cluster=failfast&default.lazy=false&default.sticky=false&dubbo=2.0.2&interface=com.vison.dubbospringapi.IHelloService&lazy=false&loadbalance=random&methods=sayHello&mock=com.vison.MockHelloService&pid=52680&release=2.7.1&side=consumer&sticky=false&timeout=1000&timestamp=1566092916602`

```java
private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
        RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        directory.setRegistry(registry);
        directory.setProtocol(protocol);
        // all attributes of REFER_KEY
        Map<String, String> parameters = new HashMap<String, String>(directory.getUrl().getParameters());
        URL subscribeUrl = new URL(CONSUMER_PROTOCOL, parameters.remove(REGISTER_IP_KEY), 0, type.getName(), parameters);
        if (!ANY_VALUE.equals(url.getServiceInterface()) && url.getParameter(REGISTER_KEY, true)) {
            directory.setRegisteredConsumerUrl(getRegisteredConsumerUrl(subscribeUrl, url));
            //这里的registry是ZookeeperRegistry，这里创建好对应的节点信息
            registry.register(directory.getRegisteredConsumerUrl());
        }
        directory.buildRouterChain(subscribeUrl);
        directory.subscribe(subscribeUrl.addParameter(CATEGORY_KEY,
                PROVIDERS_CATEGORY + "," + CONFIGURATORS_CATEGORY + "," + ROUTERS_CATEGORY));

        Invoker invoker = cluster.join(directory);
        ProviderConsumerRegTable.registerConsumer(invoker, url, subscribeUrl, directory);
        return invoker;
    }
```

### 1） RegistryDirectory.subscribe() 节点订阅

​		订阅注册中心指定节点的变化，如果发生变化，则通知到RegistryDirectory。Directory其实和服务的注
册以及服务的发现有非常大的关联.

```java
public void subscribe(URL url) {
    setConsumerUrl(url);
    consumerConfigurationListener.addNotifyListener(this);
    serviceConfigurationListener = new ReferenceConfigurationListener(this, url);
    registry.subscribe(url, this);
}
```

这里的registry 是ZookeeperRegistry ，会去监听并获取路径下面的节点。监听的路径是：
`/dubbo/org.vison.dubbo.demo.DemoService/providers`

`/dubbo/org.vison.dubbo.demo.DemoService/configurators`、

`/dubbo/org.vison.dubbo.demo.DemoService/routers `节点下面的子节点变动。



### 2）ZookeeperRegistry.doSubscribe

​	这个方法是订阅，逻辑实现比较多，可以分两段来看，这里的实现把所有Service层发起的订阅以及指定的Service层发起的订阅分开处理。所有Service层类似于监控中心发起的订阅。指定的Service层发起的订阅可以看作是服务消费者的订阅。我们只需要关心指定service层发起的订阅即可

```java
public void doSubscribe(final URL url, final NotifyListener listener) {
    try {
        if (Constants.ANY_VALUE.equals(url.getServiceInterface())) {
            String root = toRootPath();
            ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
            // 如果之前该路径没有添加过listener，则创建一个map来放置listener
            if (listeners == null) {
                zkListeners.putIfAbsent(url, new ConcurrentHashMap<>());
                listeners = zkListeners.get(url);
            }
            ChildListener zkListener = listeners.get(listener);
            // 如果没有添加过对于子节点的listener，则创建,通知服务变化 回调NotifyListener
            if (zkListener == null) {
                listeners.putIfAbsent(listener, (parentPath, currentChilds) -> {
                    for (String child : currentChilds) {
                        child = URL.decode(child);
                        if (!anyServices.contains(child)) {
                            anyServices.add(child);
                            subscribe(url.setPath(child).addParameters(Constants.INTERFACE_KEY, child,
                                                                       Constants.CHECK_KEY, String.valueOf(false)), listener);
                        }
                    }
                });
                zkListener = listeners.get(listener);
            }
            zkClient.create(root, false);
            List<String> services = zkClient.addChildListener(root, zkListener);
            if (CollectionUtils.isNotEmpty(services)) {
                for (String service : services) {
                    service = URL.decode(service);
                    anyServices.add(service);
                    subscribe(url.setPath(service).addParameters(Constants.INTERFACE_KEY, service,
                                                                 Constants.CHECK_KEY, String.valueOf(false)), listener);
                }
            }
        } else {
            List<URL> urls = new ArrayList<>();
            for (String path : toCategoriesPath(url)) {
                ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
                if (listeners == null) {
                    zkListeners.putIfAbsent(url, new ConcurrentHashMap<>());
                    listeners = zkListeners.get(url);
                }
                ChildListener zkListener = listeners.get(listener);
                if (zkListener == null) {
                    listeners.putIfAbsent(listener, (parentPath, currentChilds) -> ZookeeperRegistry.this.notify(url, listener, toUrlsWithEmpty(url, parentPath, currentChilds)));
                    zkListener = listeners.get(listener);
                }
                zkClient.create(path, false);
                
                //添加path节点的当前节点及子节点监听，并且获取子节点信息
				//也就是dubbo://ip:port/...
                List<String> children = zkClient.addChildListener(path, zkListener);
                if (children != null) {
                    urls.addAll(toUrlsWithEmpty(url, path, children));
                }
            }
            notify(url, listener, urls);
        }
    } catch (Throwable e) {
        throw new RpcException("Failed to subscribe " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
    }
}
```

### 3) AbstractRegistry.notify()

这里面会针对每一个category，调用listener.notify进行通知，然后更新本地的缓存文件,

消费端的listener是最开始传递过来的RegistryDirectory，所以这里会触发RegistryDirectory.notify

```java
protected void notify(URL url, NotifyListener listener, List<URL> urls) {
        if (url == null) {
            throw new IllegalArgumentException("notify url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("notify listener == null");
        }
        if ((CollectionUtils.isEmpty(urls))
                && !Constants.ANY_VALUE.equals(url.getServiceInterface())) {
            logger.warn("Ignore empty notify urls for subscribe url " + url);
            return;
        }
        if (logger.isInfoEnabled()) {
            logger.info("Notify urls for subscribe url " + url + ", urls: " + urls);
        }
        // keep every provider's category.
        Map<String, List<URL>> result = new HashMap<>();
        for (URL u : urls) {
            if (UrlUtils.isMatch(url, u)) {
                String category = u.getParameter(Constants.CATEGORY_KEY, Constants.DEFAULT_CATEGORY);
                List<URL> categoryList = result.computeIfAbsent(category, k -> new ArrayList<>());
                categoryList.add(u);
            }
        }
        if (result.size() == 0) {
            return;
        }
        Map<String, List<URL>> categoryNotified = notified.computeIfAbsent(url, u -> new ConcurrentHashMap<>());
        for (Map.Entry<String, List<URL>> entry : result.entrySet()) {
            String category = entry.getKey();
            List<URL> categoryList = entry.getValue();
            categoryNotified.put(category, categoryList);
            listener.notify(categoryList);
            // We will update our cache file after each notification.
            // When our Registry has a subscribe failure due to network jitter, we can return at least the existing cache URL.
            saveProperties(url);
        }
    }
```

### 4) RegistryDirectory.notify()

Invoker的网络连接以及后续的配置变更，都会调用这个notify方法
urls: zk的path数据，这里表示的是dubbo://

```java
public synchronized void notify(List<URL> urls) {
    Map<String, List<URL>> categoryUrls = urls.stream()
        .filter(Objects::nonNull)
        .filter(this::isValidCategory)
        .filter(this::isNotCompatibleFor26x)
        .collect(Collectors.groupingBy(url -> {
            if (UrlUtils.isConfigurator(url)) {
                return CONFIGURATORS_CATEGORY;
            } else if (UrlUtils.isRoute(url)) {
                return ROUTERS_CATEGORY;
            } else if (UrlUtils.isProvider(url)) {
                return PROVIDERS_CATEGORY;
            }
            return "";
        }));

    List<URL> configuratorURLs = categoryUrls.getOrDefault(CONFIGURATORS_CATEGORY, Collections.emptyList());
    this.configurators = Configurator.toConfigurators(configuratorURLs).orElse(this.configurators);
// 如果router 路由节点有变化，则重新将router下的数据生成router
    List<URL> routerURLs = categoryUrls.getOrDefault(ROUTERS_CATEGORY, Collections.emptyList());
    toRouters(routerURLs).ifPresent(this::addRouters);

    // 获得provider URL，然后调用refreshOverrideAndInvoker进行刷新
    List<URL> providerURLs = categoryUrls.getOrDefault(PROVIDERS_CATEGORY, Collections.emptyList());
    refreshOverrideAndInvoker(providerURLs);
}
```

### 5) refreshOverrideAndInvoker()

逐个调用注册中心里面的配置，覆盖原来的url，组成最新的url 放入overrideDirectoryUrl 存储
根据 provider urls，重新刷新Invoker

```java
private void refreshOverrideAndInvoker(List<URL> urls) {
    // mock zookeeper://xxx?mock=return null
    overrideDirectoryUrl();
    refreshInvoker(urls);
}
```

### 6) refreshInvoker()



```java
private void refreshInvoker(List<URL> invokerUrls) {
    Assert.notNull(invokerUrls, "invokerUrls should not be null");

    if (invokerUrls.size() == 1
        && invokerUrls.get(0) != null
        && Constants.EMPTY_PROTOCOL.equals(invokerUrls.get(0).getProtocol())) {
        this.forbidden = true; // Forbid to access
        this.invokers = Collections.emptyList();
        routerChain.setInvokers(this.invokers);
        destroyAllInvokers(); // Close all invokers
    } else {
        this.forbidden = false; // Allow to access
        Map<String, Invoker<T>> oldUrlInvokerMap = this.urlInvokerMap; // local reference
        if (invokerUrls == Collections.<URL>emptyList()) {
            invokerUrls = new ArrayList<>();
        }
        if (invokerUrls.isEmpty() && this.cachedInvokerUrls != null) {
            invokerUrls.addAll(this.cachedInvokerUrls);
        } else {
            this.cachedInvokerUrls = new HashSet<>();
            this.cachedInvokerUrls.addAll(invokerUrls);//Cached invoker urls, convenient for comparison
        }
        if (invokerUrls.isEmpty()) {
            return;
        }
        //根据provider url，生成新的invoker
        Map<String, Invoker<T>> newUrlInvokerMap = toInvokers(invokerUrls);// Translate url list to Invoker map

        /**
             * If the calculation is wrong, it is not processed.
             *
             * 1. The protocol configured by the client is inconsistent with the protocol of the server.
             *    eg: consumer protocol = dubbo, provider only has other protocol services(rest).
             * 2. The registration center is not robust and pushes illegal specification data.
             *
             */
        if (CollectionUtils.isEmptyMap(newUrlInvokerMap)) {
            logger.error(new IllegalStateException("urls to invokers error .invokerUrls.size :" + invokerUrls.size() + ", invoker.size :0. urls :" + invokerUrls
                                                   .toString()));
            return;
        }

        List<Invoker<T>> newInvokers = Collections.unmodifiableList(new ArrayList<>(newUrlInvokerMap.values()));
        // pre-route and build cache, notice that route cache should build on original Invoker list.
        // toMergeMethodInvokerMap() will wrap some invokers having different groups, those wrapped invokers not should be routed.
        routerChain.setInvokers(newInvokers);
        //如果服务配置了分组，则把分组下的provider包装成StaticDirectory,组成一个invoker
		//实际上就是按照group进行合并
        this.invokers = multiGroup ? toMergeInvokerList(newInvokers) : newInvokers;
        this.urlInvokerMap = newUrlInvokerMap;

        try {
            //旧的url 是否在新map里面存在，不存在，就是销毁url对应的Invoker
            destroyUnusedInvokers(oldUrlInvokerMap, newUrlInvokerMap); // Close the unused Invoker
        } catch (Exception e) {
            logger.warn("destroyUnusedInvokers error. ", e);
        }
    }
}
```

### 7) toInvokers()

这个方法中有比较长的判断和处理逻辑，我们只需要关心invoker是什么时候初始化的就行。
这里用到了protocol.refer来构建了一个invoker
invoker = new InvokerDelegate<>(protocol.refer(serviceType, url), url,providerUrl);
构建完成之后，会保存在Map<String, Invoker<T>> urlInvokerMap 这个集合中

```java
private Map<String, Invoker<T>> toInvokers(List<URL> urls) {
    Map<String, Invoker<T>> newUrlInvokerMap = new HashMap<>();
    if (urls == null || urls.isEmpty()) {
        return newUrlInvokerMap;
    }
    Set<String> keys = new HashSet<>();
    String queryProtocols = this.queryMap.get(Constants.PROTOCOL_KEY);
    for (URL providerUrl : urls) {
        // If protocol is configured at the reference side, only the matching protocol is selected
        if (queryProtocols != null && queryProtocols.length() > 0) {
            boolean accept = false;
            String[] acceptProtocols = queryProtocols.split(",");
            for (String acceptProtocol : acceptProtocols) {
                if (providerUrl.getProtocol().equals(acceptProtocol)) {
                    accept = true;
                    break;
                }
            }
            if (!accept) {
                continue;
            }
        }
        if (Constants.EMPTY_PROTOCOL.equals(providerUrl.getProtocol())) {
            continue;
        }
        if (!ExtensionLoader.getExtensionLoader(Protocol.class).hasExtension(providerUrl.getProtocol())) {
            logger.error(new IllegalStateException("Unsupported protocol " + providerUrl.getProtocol() +
                                                   " in notified url: " + providerUrl + " from registry " + getUrl().getAddress() +
                                                   " to consumer " + NetUtils.getLocalHost() + ", supported protocol: " +
                                                   ExtensionLoader.getExtensionLoader(Protocol.class).getSupportedExtensions()));
            continue;
        }
        URL url = mergeUrl(providerUrl);

        String key = url.toFullString(); // The parameter urls are sorted
        if (keys.contains(key)) { // Repeated url
            continue;
        }
        keys.add(key);
        // Cache key is url that does not merge with consumer side parameters, regardless of how the consumer combines parameters, if the server url changes, then refer again
        Map<String, Invoker<T>> localUrlInvokerMap = this.urlInvokerMap; // local reference
        Invoker<T> invoker = localUrlInvokerMap == null ? null : localUrlInvokerMap.get(key);
        if (invoker == null) { // Not in the cache, refer again
            try {
                boolean enabled = true;
                if (url.hasParameter(Constants.DISABLED_KEY)) {
                    enabled = !url.getParameter(Constants.DISABLED_KEY, false);
                } else {
                    enabled = url.getParameter(Constants.ENABLED_KEY, true);
                }
                if (enabled) {
                    invoker = new InvokerDelegate<>(protocol.refer(serviceType, url), url, providerUrl);
                }
            } catch (Throwable t) {
                logger.error("Failed to refer invoker for interface:" + serviceType + ",url:(" + url + ")" + t.getMessage(), t);
            }
            if (invoker != null) { // Put new invoker in cache
                newUrlInvokerMap.put(key, invoker);
            }
        } else {
            newUrlInvokerMap.put(key, invoker);
        }
    }
    keys.clear();
    return newUrlInvokerMap;
}
```

### 8) protocol.refer(serviceType, url)

调用指定的协议来进行远程引用。protocol是一个Protocol$Adaptive类
而真正的实现应该是：
ProtocolFilterWrapper(ProtocolListenerWrapper(QosProtocolWrapper(DubboProtocol.refer)
前面的包装过程，在服务发布的时候已经分析过了，我们直接进入DubboProtocol.refer方法



### 9) DubboProtocol.refer

- 优化序列化

- 构建DubboInvoker

​      在构建DubboInvoker时，会构建一个ExchangeClient，通过getClients(url)方法，这里基本可以猜到到
是服务的通信建立

```java
public <T> Invoker<T> refer(Class<T> serviceType, URL url) throws RpcException {
    optimizeSerialization(url);

    // create rpc invoker.
    DubboInvoker<T> invoker = new DubboInvoker<T>(serviceType, url, getClients(url), invokers);
    invokers.add(invoker);

    return invoker;
}
```

### 10 DubboProtocol.getClients()

这里面是获得客户端连接的方法
判断是否为共享连接，默认是共享同一个连接进行通信
是否配置了多个连接通道 connections，默认只有一个

```java
private ExchangeClient[] getClients(URL url) {
    // whether to share connection

    boolean useShareConnect = false;

    int connections = url.getParameter(Constants.CONNECTIONS_KEY, 0);
    List<ReferenceCountExchangeClient> shareClients = null;
    // if not configured, connection is shared, otherwise, one connection for one service
    if (connections == 0) {
        useShareConnect = true;

        /**
         * The xml configuration should have a higher priority than properties.
         */
        String shareConnectionsStr = url.getParameter(Constants.SHARE_CONNECTIONS_KEY, (String) null);
        connections = Integer.parseInt(StringUtils.isBlank(shareConnectionsStr) ? ConfigUtils.getProperty(Constants.SHARE_CONNECTIONS_KEY,
                Constants.DEFAULT_SHARE_CONNECTIONS) : shareConnectionsStr);
        shareClients = getSharedClient(url, connections);
    }

    ExchangeClient[] clients = new ExchangeClient[connections];
    for (int i = 0; i < clients.length; i++) {
        if (useShareConnect) {
            clients[i] = shareClients.get(i);

        } else {
            clients[i] = initClient(url);
        }
    }

    return clients;
}
```

### 11) getSharedClient()

```java
private List<ReferenceCountExchangeClient> getSharedClient(URL url, int connectNum) {
    String key = url.getAddress();
    List<ReferenceCountExchangeClient> clients = referenceClientMap.get(key);

    if (checkClientCanUse(clients)) {
        batchClientRefIncr(clients);
        return clients;
    }

    locks.putIfAbsent(key, new Object());
    synchronized (locks.get(key)) {
        clients = referenceClientMap.get(key);
        // dubbo check
        if (checkClientCanUse(clients)) {
            batchClientRefIncr(clients);
            return clients;
        }

        // connectNum must be greater than or equal to 1
        connectNum = Math.max(connectNum, 1);

        // If the clients is empty, then the first initialization is
        if (CollectionUtils.isEmpty(clients)) {
            clients = buildReferenceCountExchangeClientList(url, connectNum);
            referenceClientMap.put(key, clients);

        } else {
            for (int i = 0; i < clients.size(); i++) {
                ReferenceCountExchangeClient referenceCountExchangeClient = clients.get(i);
                // If there is a client in the list that is no longer available, create a new one to replace him.
                if (referenceCountExchangeClient == null || referenceCountExchangeClient.isClosed()) {
                    clients.set(i, buildReferenceCountExchangeClient(url));
                    continue;
                }

                referenceCountExchangeClient.incrementAndGetCount();
            }
        }

        /**
             * I understand that the purpose of the remove operation here is to avoid the expired url key
             * always occupying this memory space.
             */
        locks.remove(key);

        return clients;
    }
}
```



### 12) DubboProtocol.initClient()

```java
private ExchangeClient initClient(URL url) {

    // 获得连接类型
    String str = url.getParameter(Constants.CLIENT_KEY, url.getParameter(Constants.SERVER_KEY, Constants.DEFAULT_REMOTING_CLIENT));

    url = url.addParameter(Constants.CODEC_KEY, DubboCodec.NAME);
    //设置心跳时间
    url = url.addParameterIfAbsent(Constants.HEARTBEAT_KEY, String.valueOf(Constants.DEFAULT_HEARTBEAT));

    // BIO is not allowed since it has severe performance issue.
    if (str != null && str.length() > 0 && !ExtensionLoader.getExtensionLoader(Transporter.class).hasExtension(str)) {
        throw new RpcException("Unsupported client type: " + str + "," +
                               " supported client type is " + StringUtils.join(ExtensionLoader.getExtensionLoader(Transporter.class).getSupportedExtensions(), " "));
    }

    ExchangeClient client;
    try {
        // 是否需要延迟创建连接，注意哦，这里的requestHandler是一个适配器
        if (url.getParameter(Constants.LAZY_CONNECT_KEY, false)) {
            client = new LazyConnectExchangeClient(url, requestHandler);

        } else {
            client = Exchangers.connect(url, requestHandler);
        }

    } catch (RemotingException e) {
        throw new RpcException("Fail to create remoting client for service(" + url + "): " + e.getMessage(), e);
    }

    return client;
}
```

### 13) Exchangers.connect()

```java
public static ExchangeClient connect(URL url, ExchangeHandler handler) throws RemotingException {
    if (url == null) {
        throw new IllegalArgumentException("url == null");
    }
    if (handler == null) {
        throw new IllegalArgumentException("handler == null");
    }
    url = url.addParameterIfAbsent(Constants.CODEC_KEY, "exchange");
    return getExchanger(url).connect(url, handler); //这里通过Exchager连接
}
```

### 14） HeaderExchanger.connect()

​	这里和服务发布源码类似，通过nettyServer获取连接

```java
@Override
public ExchangeClient connect(URL url, ExchangeHandler handler) throws RemotingException {
    return new HeaderExchangeClient(Transporters.connect(url, new DecodeHandler(new HeaderExchangeHandler(handler))), true);
}
```

# 3. 总结

​	我们讲到了RegistryProtocol.refer 过程中有一个关键步骤，即在监听到服务提供者url时触发RegistryDirectory.notify() 方法。

​	RegistryDirectory.notify() 方法调用refreshInvoker() 方法将服务提供者urls转换为对应的远程invoker ，最终调用到DubboProtocol.refer() 方法生成对应的DubboInvoker 。

​	DubboInvoker 的构造方法中有一项入参ExchangeClient[] clients ，即对应本文要讲的网络客户端Client 。DubboInvoker就是通过调用client.request() 方法完成网络通信的请求发送和响应接收功能。

​	Client 的具体生成过程就是通过DubboProtocol 的initClient(URL url) 方法创建了一个HeaderExchangeClient 。