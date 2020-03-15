

客户端生成的ref是一个Proxy代理，而这个proxy本质上是一个动态代理类。

# 1. JavassistProxyFactory.getProxy

```java
public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        return (T) Proxy.getProxy(interfaces)
            .newInstance(new InvokerInvocationHandler(invoker));
}
```



首先 这个invoker实际上是：`MockClusterWrapper(FailoverCluster(directory))`

然后通过InvokerInvocationHandler做了一层包装,所以我们的代理类实际上是:`InvokerInvocationHandler(MockClusterWrapper(FailoverCluster(directory)))`



另外：Proxy.getProxy()生成的代理

​	这个方法里面，会生成一个动态代理的方法，我们通过debug可以看到动态字节码的拼接过程。它代理
了当前这个接口的方法sayHello , 并且方法里面是使用handler.invoke进行调用的。

```java
public java.lang.String sayHello(java.lang.String arg0){
Object[] args = new Object[1];
args[0] = ($w)$1;
Object ret = handler.invoke(this, methods[0], args);
return (java.lang.String)ret;
}
```



# 2. 消费端调用的过程

handler的调用链路为：
InvokerInvocationHandler(MockClusterWrapper(FailoverCluster(directory)))

## 2.1 InvokerInvocationHandler.invoke()

​		这个方法主要判断当前调用的远程方法，如果是tostring、hashcode、equals，就直接返回；否则，调用invoker.invoke,进入到MockClusterWrapper.invoke 方法

```java
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    Class<?>[] parameterTypes = method.getParameterTypes();
    if (method.getDeclaringClass() == Object.class) {
        return method.invoke(invoker, args);
    }
    if ("toString".equals(methodName) && parameterTypes.length == 0) {
        return invoker.toString();
    }
    if ("hashCode".equals(methodName) && parameterTypes.length == 0) {
        return invoker.hashCode();
    }
    if ("equals".equals(methodName) && parameterTypes.length == 1) {
        return invoker.equals(args[0]);
    }
    //createInvocation,参数为目标方法名称和目标方法的参数，看起来似乎是组装一个传输的对象
    return invoker.invoke(createInvocation(method, args)).recreate();
}
```

## 2.2 MockClusterInvoker.invoke()

Mock，在这里面有两个逻辑
1. 是否客户端强制配置了mock调用，那么在这种场景中主要可以用来解决服务端还没开发好的时候直接使用本地数据进行测试
2. 是否出现了异常，如果出现异常则使用配置好的Mock类来实现服务的降级

```java
public Result invoke(Invocation invocation) throws RpcException {
        Result result = null;
		//从url中获得MOCK_KEY对应的value
        String value = directory.getUrl().getMethodParameter(invocation.getMethodName(), Constants.MOCK_KEY, Boolean.FALSE.toString()).trim();
    //如果没有配置mock，则直接传递给下个invoker调用
        if (value.length() == 0 || value.equalsIgnoreCase("false")) {
            //no mock
            result = this.invoker.invoke(invocation);
            //如果强制为本地调用，则执行mockInvoke
        } else if (value.startsWith("force")) {
            if (logger.isWarnEnabled()) {
                logger.warn("force-mock: " + invocation.getMethodName() + " force-mock enabled , url : " + directory.getUrl());
            }
            //force:direct mock
            result = doMockInvoke(invocation, null);
        } else {
            //fail-mock
            try {
                //远程调用服务
                result = this.invoker.invoke(invocation);
            } catch (RpcException e) {
                if (e.isBiz()) {
                    throw e;
                }
                
                if (logger.isWarnEnabled()) {
                    logger.warn("fail-mock: " + invocation.getMethodName() + " fail-mock enabled , url : " + directory.getUrl(), e);
                }
                //如果远程调用出现异常，则使用Mock进行处理
                result = doMockInvoke(invocation, e);
            }
        }
        return result;
    }
```

## 2.3 AbstractClusterInvoker.invoke()

​	下一个invoke，应该进入FailoverClusterInvoke，但是在这里它又用到了模版方法，所以直接进入到父
类的invoke方法中

1. 绑定`attachments`，Dubbo中，可以通过 RpcContext 上的 setAttachment 和 getAttachment 在
    服务消费方和提供方之间进行参数的隐式传递，所以这段代码中会去绑定attachments

  ```java
  RpcContext.getContext().setAttachment("index", "1")
  ```

2. 通过list获得invoker列表，这个列表基本可以猜测到是从directory里面获得的、但是这里面还实现
了服务路由的逻辑，简单来说就是先拿到invoker列表，然后通过router进行服务路由，筛选出符
合路由规则的服务提供者（暂时不细讲，属于另外一个逻辑）
3. `initLoadBalance` 初始化负载均衡机制
4. 执行`doInvoke`



```java
public Result invoke(final Invocation invocation) throws RpcException {
    checkWhetherDestroyed();

    // binding attachments into invocation.
    Map<String, String> contextAttachments = RpcContext.getContext().getAttachments();
    if (contextAttachments != null && contextAttachments.size() != 0) {
        ((RpcInvocation) invocation).addAttachments(contextAttachments);
    }

    List<Invoker<T>> invokers = list(invocation);
    LoadBalance loadbalance = initLoadBalance(invokers, invocation);
    RpcUtils.attachInvocationIdIfAsync(getUrl(), invocation);
    return doInvoke(invocation, invokers, loadbalance);
}
```

### 1) initLoadBalance()

​		不用看这个代码，基本也能猜测到，会从url中获得当前的负载均衡算法，然后使用spi机制来获得负载均衡的扩展点。然后返回一个具体的实现

```java
protected LoadBalance initLoadBalance(List<Invoker<T>> invokers, Invocation invocation) {
    if (CollectionUtils.isNotEmpty(invokers)) {
        return ExtensionLoader.getExtensionLoader(LoadBalance.class)
            .getExtension(invokers
                          .get(0).getUrl()
                          .getMethodParameter(RpcUtils
                                              .getMethodName(invocation),                                                               Constants.LOADBALANCE_KEY,
                                              Constants.DEFAULT_LOADBALANCE));
    } else {
        return ExtensionLoader.getExtensionLoader(LoadBalance.class)
            .getExtension(Constants.DEFAULT_LOADBALANCE);
    }
}
```

### 2) FailoverClusterInvoker.doInvoke

​		这段代码逻辑也很好理解，容错机制，而failover是失败重试，所以这里面应该会实现容错的逻辑

- 获得重试的次数，并且进行循环
- 获得目标服务，并且记录当前已经调用过的目标服务防止下次继续将请求发送过去
- 如果执行成功，则返回结果
- 如果出现异常，判断是否为业务异常，如果是则抛出，否则，进行下一次重试



![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g63uetc9huj20o00c3dlv.jpg)

- 这里的 Invoker 是 Provider 的一个可调用 Service 的抽象， Invoker 封装了 Provider 地址及 Service 接口信息
- Directory 代表多个 Invoker ，可以把它看成 List<Invoker> ，但与 List 不同的是，它的值可能是动态变化的，比如注册中心推送变更
- Cluster 将 Directory 中的多个 Invoker 伪装成一个 Invoker ，对上层透明，伪装过程包含了容错逻辑，调用失败后，重试另一个
- Router 负责从多个 Invoker 中按路由规则选出子集，比如读写分离，应用隔离等
- LoadBalance 负责从多个 Invoker 中选出具体的一个用于本次调用，选的过程包含了负载均衡算法，调用失败后，需要重选

```java
public Result doInvoke(Invocation invocation, final List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
        List<Invoker<T>> copyInvokers = invokers;
        checkInvokers(copyInvokers, invocation);
        String methodName = RpcUtils.getMethodName(invocation);
        int len = getUrl().getMethodParameter(methodName, Constants.RETRIES_KEY, Constants.DEFAULT_RETRIES) + 1;
        if (len <= 0) {
            len = 1;
        }
        // retry loop.
        RpcException le = null; // last exception.
        List<Invoker<T>> invoked = new ArrayList<Invoker<T>>(copyInvokers.size()); // invoked invokers.
        Set<String> providers = new HashSet<String>(len);
        for (int i = 0; i < len; i++) {
            //Reselect before retry to avoid a change of candidate `invokers`.
            //NOTE: if `invokers` changed, then `invoked` also lose accuracy.
            if (i > 0) {
                checkWhetherDestroyed();
                copyInvokers = list(invocation);
                // check again
                checkInvokers(copyInvokers, invocation);
            }
            //通过负载均衡获得目标invoker
            Invoker<T> invoker = select(loadbalance, invocation, copyInvokers, invoked);
            invoked.add(invoker);//记录已经调用过的服务，下次调用会进行过滤
            RpcContext.getContext().setInvokers((List) invoked);
            try {
                //服务调用成功，直接返回结果
                Result result = invoker.invoke(invocation);
                if (le != null && logger.isWarnEnabled()) {
                    .....
                return result;
            } catch (RpcException e) {
                if (e.isBiz()) { // 如果是业务异常，直接抛出不进行重试 
                    throw e;
                }
                le = e;			//记录异常信息，进行下一次循环
            } catch (Throwable e) {
                le = new RpcException(e.getMessage(), e);
            } finally {
                providers.add(invoker.getUrl().getAddress());
            }
        }
        throw new RpcException(le.getCode())
    }
```



## 2.4 FailoverClusterInvoker.select()

​	在调用invoker.invoke之前，会需要通过select选择一个合适的服务进行调用，而这个选择的过程其实
就是负载均衡的实现；

```java
protected Invoker<T> select(LoadBalance loadbalance, Invocation invocation,
                                List<Invoker<T>> invokers,
                            List<Invoker<T>> selected) throws RpcException {

    if (CollectionUtils.isEmpty(invokers)) {
        return null;
    }
    String methodName = invocation == null ? StringUtils.EMPTY 
        : invocation.getMethodName();

    boolean sticky = invokers.get(0).getUrl()
        .getMethodParameter(methodName, 
                            Constants.CLUSTER_STICKY_KEY, Constants.DEFAULT_CLUSTER_STICKY);

    //ignore overloaded method
    if (stickyInvoker != null && !invokers.contains(stickyInvoker)) {
        stickyInvoker = null;
    }
    //ignore concurrency problem
    if (sticky && stickyInvoker != null && (selected == null || !selected.contains(stickyInvoker))) {
        if (availablecheck && stickyInvoker.isAvailable()) {
            return stickyInvoker;
        }
    }

    //调用 doSelect ,获取Invoker
    Invoker<T> invoker = doSelect(loadbalance, invocation, invokers, selected);

    if (sticky) {
        stickyInvoker = invoker;
    }
    return invoker;
}

//doSelect()
private Invoker<T> doSelect(LoadBalance loadbalance, Invocation invocation,
                            List<Invoker<T>> invokers, 
                            List<Invoker<T>> selected) throws RpcException {

    if (CollectionUtils.isEmpty(invokers)) {
        return null;
    }
    if (invokers.size() == 1) {
        return invokers.get(0);
    }
    //负载均衡选择invoker
    Invoker<T> invoker = loadbalance.select(invokers, getUrl(), invocation);

    if ((selected != null && selected.contains(invoker))
        || (!invoker.isAvailable() && getUrl() != null && availablecheck)) {
        try {
            Invoker<T> rinvoker = reselect(loadbalance, invocation, 
                                           invokers, selected, availablecheck);
            if (rinvoker != null) {
                invoker = rinvoker;
            } else {

                int index = invokers.indexOf(invoker);
                try {
                    //Avoid collision
                    invoker = invokers.get((index + 1) % invokers.size());
                } catch (Exception e) {
                    ...
                }
            }
        } catch (Throwable t) {
            ...
        }
    }
    return invoker;
}
```



## 2.5 AbstractLoadBalance.select()负载均衡

​	所有负载均衡实现类均继承自 AbstractLoadBalance，该类实现了 LoadBalance 接口，并封装了一些公共的逻辑。所以在分析负载均衡实现之前，先来看一下 AbstractLoadBalance 的逻辑。首先来看一下负载均衡的入口方法 select，如下：

```java
@Override
public <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) {
    if (CollectionUtils.isEmpty(invokers)) {
        return null;
    }
    //如果invoker只有一个，直接返回
    if (invokers.size() == 1) {
        return invokers.get(0);
    }
    //调用 doSelect 方法进行负载均衡，该方法为抽象方法，由子类实现
    return doSelect(invokers, url, invocation);
}
```

### 1） RandomLoadBalance

```java
public class RandomLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "random";

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Number of invokers
        int length = invokers.size();
        // Every invoker has the same weight?
        boolean sameWeight = true;
        // the weight of every invokers
        int[] weights = new int[length];
        // the first invoker's weight
        int firstWeight = getWeight(invokers.get(0), invocation);
        weights[0] = firstWeight;
        // The sum of weights
        int totalWeight = firstWeight;
        // 下面这个循环有两个作用，第一是计算总权重 totalWeight，
		// 第二是检测每个服务提供者的权重是否相同
        for (int i = 1; i < length; i++) {
            int weight = getWeight(invokers.get(i), invocation);
            // save for later use
            weights[i] = weight;
            // Sum
            totalWeight += weight;
            if (sameWeight && weight != firstWeight) {
                sameWeight = false;
            }
        }
        // 下面的 if 分支主要用于获取随机数，并计算随机数落在哪个区间上
        if (totalWeight > 0 && !sameWeight) {
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on totalWeight.
            int offset = ThreadLocalRandom.current().nextInt(totalWeight);
            // Return a invoker based on the random value.
            
            // 循环让 offset 数减去服务提供者权重值，当 offset 小于0时，返回相应的Invoker。
            // 举例说明一下，我们有 servers = [A, B, C]，weights = [5, 3, 2]，offset= 7。
            // 第一次循环，offset - 5 = 2 > 0，即 offset > 5，
            // 表明其不会落在服务器 A 对应的区间上。
            // 第二次循环，offset - 3 = -1 < 0，即 5 < offset < 8，
            // 表明其会落在服务器 B 对应的区间上
            for (int i = 0; i < length; i++) {
                // 让随机值 offset 减去权重值
                offset -= weights[i];
                if (offset < 0) {
                    // 返回相应的 Invoker
                    return invokers.get(i);
                }
            }
        }
        //如果所有服务提供者权重值相同，此时直接随机返回一个即可
        return invokers.get(ThreadLocalRandom.current().nextInt(length));
    }

}
```

​	通过从RegistryDirectory中获得的invoker是什么呢？这个很重要，因为它决定了接下来的调用过程。
这个时候我们需要去了解这个invoker是在哪里初始化的？



## 2.6 可调用的Invoker初始化过程

在RegistryDirectory中有一个成员属性，保存了服务地方地址对应的invoke信息

```java
private volatile Map<String, Invoker<T>> urlInvokerMap; 
```

**toInvokers**

这个invoker是动态的，基于注册中心的变化而变化的。它的初始化过程的链路是
RegistryDirectory.notify->refreshInvoker->toInvokers 下面的这段代码中

```java
if (invoker == null) { // Not in the cache, refer again
    invoker = new InvokerDelegate<>(protocol.refer(serviceType, url), url,
    providerUrl);
}
```

​	是基于protocol.refer来构建的invoker，并且使用InvokerDelegate进行了委托,在dubboprotocol中，是这样构建invoker的。返回的是一个DubboInvoker对象

```java
@Override
public <T> Invoker<T> refer(Class<T> serviceType, URL url) throws RpcException {
    optimizeSerialization(url);
    // create rpc invoker.
    DubboInvoker<T> invoker = new DubboInvoker<T>(serviceType, url,
    getClients(url), invokers);
    invokers.add(invoker);
    return invoker;
}
```

所以这个invoker应该是：
InvokerDelegate(ProtocolFilterWrapper(ListenerInvokerWrapper(DubboInvoker())
ProtocolFilterWrapper->这个是一个invoker的过滤链路
ListenerInvokerWrapper-> 这里面暂时没做任何的实现
所以我们可以直接看到DubboInvoker这个类里面来





### 1) DubboInvoker

**AbstractInvoker.invoke**

这里面也是对Invocation的attachments进行处理，把attachment加入到Invocation中
这里的attachment，实际上是目标服务的接口信息以及版本信息



**DubboInvoker.doInvoker**

```java
protected Result doInvoke(final Invocation invocation) throws Throwable {
    RpcInvocation inv = (RpcInvocation) invocation;
    final String methodName = RpcUtils.getMethodName(invocation);
    //将目标方法以及版本好作为参数放入到Invocation中
    inv.setAttachment(Constants.PATH_KEY, getUrl().getPath());
    inv.setAttachment(Constants.VERSION_KEY, version);
    //获得客户端连接
    ExchangeClient currentClient;
    if (clients.length == 1) {
        currentClient = clients[0];
    } else {
        currentClient = clients[index.getAndIncrement() % clients.length];
    }
    try {
        boolean isAsync = RpcUtils.isAsync(getUrl(), invocation);
        boolean isAsyncFuture = RpcUtils.isReturnTypeFuture(inv);
        //判断方法是否有返回值
        boolean isOneway = RpcUtils.isOneway(getUrl(), invocation);
        //获得超时时间， 默认是1s
        int timeout = getUrl().getMethodParameter(methodName, Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);
        if (isOneway) {
            boolean isSent = getUrl().getMethodParameter(methodName, Constants.SENT_KEY, false);
            currentClient.send(inv, isSent);
            RpcContext.getContext().setFuture(null);
            return new RpcResult();
        } else if (isAsync) {
            ResponseFuture future = currentClient.request(inv, timeout);
            // For compatibility
            FutureAdapter<Object> futureAdapter = new FutureAdapter<>(future);
            RpcContext.getContext().setFuture(futureAdapter);

            Result result;
            if (isAsyncFuture) {
                // register resultCallback, sometimes we need the async result being processed by the filter chain.
                result = new AsyncRpcResult(futureAdapter, futureAdapter.getResultFuture(), false);
            } else {
                result = new SimpleAsyncRpcResult(futureAdapter, futureAdapter.getResultFuture(), false);
            }
            return result;
        } else {
            RpcContext.getContext().setFuture(null);
            return (Result) currentClient.request(inv, timeout).get();
        }
    } catch (TimeoutException e) {
        throw new RpcException(RpcException.TIMEOUT_EXCEPTION, "Invoke remote method timeout. method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
    } catch (RemotingException e) {
        throw new RpcException(RpcException.NETWORK_EXCEPTION, "Failed to invoke remote method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
    }
}
```



### 1) currentClient.request

currentClient还记得是一个什么对象吗？
它实际是一个ReferenceCountExchangeClient(HeaderExchangeClient())
所以它的调用链路是
`ReferenceCountExchangeClient->HeaderExchangeClient->HeaderExchangeChannel`->(request方法)
最终，把构建好的RpcInvocation，组装到一个Request对象中进行传递



```java
public ResponseFuture request(Object request, int timeout) throws RemotingException {
    if (closed) {
        throw new RemotingException(this.getLocalAddress(), null, "Failed to send request " + request + ", cause: The channel " + this + " is closed!");
    }
    // // 创建请求对象
    Request req = new Request();
    req.setVersion(Version.getProtocolVersion());
    req.setTwoWay(true);
    req.setData(request);
    DefaultFuture future = DefaultFuture.newFuture(channel, req, timeout);
    try {
        channel.send(req);
    } catch (RemotingException e) {
        future.cancel();
        throw e;
    }
    return future;
}
```

channel.send的调用链路
AbstractPeer.send ->AbstractClient.send->NettyChannel.send
通过NioSocketChannel把消息发送出去

ChannelFuture future = channel.writeAndFlush(message);



# 3.服务端收到请求处理

客户端把消息发送出去之后，服务端会收到消息，然后把执行的结果返回到客户端



## 3.1 服务端接收到消息

服务端这边接收消息的处理链路，也比较复杂，我们回到NettServer中创建io的过程.

```java
protected void doOpen() throws Throwable {
        bootstrap = new ServerBootstrap();

        bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("NettyServerBoss", true));
        workerGroup = new NioEventLoopGroup(getUrl().getPositiveParameter(Constants.IO_THREADS_KEY, Constants.DEFAULT_IO_THREADS),
                new DefaultThreadFactory("NettyServerWorker", true));

        final NettyServerHandler nettyServerHandler = new NettyServerHandler(getUrl(), this);
        channels = nettyServerHandler.getChannels();

        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, Boolean.TRUE)
                .childOption(ChannelOption.SO_REUSEADDR, Boolean.TRUE)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        // FIXME: should we use getTimeout()?
                        int idleTimeout = UrlUtils.getIdleTimeout(getUrl());
                        NettyCodecAdapter adapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyServer.this);
                        ch.pipeline()//.addLast("logging",new LoggingHandler(LogLevel.INFO))//for debug
                                .addLast("decoder", adapter.getDecoder())
                                .addLast("encoder", adapter.getEncoder())
                                .addLast("server-idle-handler", new IdleStateHandler(0, 0, idleTimeout, MILLISECONDS))
                                .addLast("handler", nettyServerHandler);
                    }
                });
        // bind
        ChannelFuture channelFuture = bootstrap.bind(getBindAddress());
        channelFuture.syncUninterruptibly();
        channel = channelFuture.channel();
}
```

- handler配置的是`nettyServerHandler`
- `server-idle-handler` 表示心跳处理的机制

```java
  final NettyServerHandler nettyServerHandler = new NettyServerHandler(getUrl(), this);
```

​	Handler与Servlet中的filter很像，通过Handler可以完成通讯报文的解码编码、拦截指定的报文、统一对日志错误进行处理、统一对请求进行计数、控制Handler执行与否.

```java
public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
    try {
        handler.received(channel, msg);
    } finally {
        NettyChannel.removeChannelIfDisconnected(ctx.channel());
    }
}
```

>handler->MultiMessageHandler->HeartbeatHandler->AllChannelHandler->DecodeHandler->
>
>HeaderExchangeHandler->
>最后进入这个方法->DubboProtocol$requestHandler(receive)
>MultiMessageHandler: 复合消息处理
>HeartbeatHandler：心跳消息处理，接收心跳并发送心跳响应
>AllChannelHandler：业务线程转化处理器，把接收到的消息封装成ChannelEventRunnable可执行任
>务给线程池处理
>DecodeHandler:业务解码处理器



## 3.2 服务调用流程

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g63w35y2z3j20ne0o00zx.jpg)



### 1)HeaderExchangeHandler.received

交互层请求响应处理，有三种处理方式
1. handlerRequest，双向请求
2. handler.received 单向请求
3. handleResponse 响应消息

```java
public void received(Channel channel, Object message) throws RemotingException {
    channel.setAttribute(KEY_READ_TIMESTAMP, System.currentTimeMillis());
    final ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
    try {
        if (message instanceof Request) {
            // handle request.
            Request request = (Request) message;
            if (request.isEvent()) {
                handlerEvent(channel, request);
            } else {
                if (request.isTwoWay()) {
                    handleRequest(exchangeChannel, request);
                } else {
                    handler.received(exchangeChannel, request.getData());
                }
            }
        } else if (message instanceof Response) {
            handleResponse(channel, (Response) message);
        } else if (message instanceof String) {
            if (isClientSide(channel)) {
                Exception e = new Exception("Dubbo client can not supported string message: " + message + " in channel: " + channel + ", url: " + channel.getUrl());
                logger.error(e.getMessage(), e);
            } else {
                String echo = handler.telnet(channel, (String) message);
                if (echo != null && echo.length() > 0) {
                    channel.send(echo);
                }
            }
        } else {
            handler.received(exchangeChannel, message);
        }
    } finally {
        HeaderExchangeChannel.removeChannelIfDisconnected(channel);
    }
}
```



### 2) ExchangeHandler.reply()

 接着进入到ExchangeHandler.reply这个方法中(这个方法在DubboProtocol的全局变量中)

- 把message转化为Invocation
- 调用getInvoker获得一个Invoker对象
- 然后通过Result result = invoker.invoke(inv); 进行调用

```java
private ExchangeHandler requestHandler = new ExchangeHandlerAdapter() {

    @Override
    public CompletableFuture<Object> reply(ExchangeChannel channel, Object message) throws RemotingException {

        if (!(message instanceof Invocation)) {
            throw new RemotingException(channel, "Unsupported request: "
                                        + (message == null ? null : (message.getClass().getName() + ": " + message))
                                        + ", channel: consumer: " + channel.getRemoteAddress() + " --> provider: " + channel.getLocalAddress());
        }

        Invocation inv = (Invocation) message;
        Invoker<?> invoker = getInvoker(channel, inv);
        // need to consider backward-compatibility if it's a callback
        if (Boolean.TRUE.toString().equals(inv.getAttachments().get(IS_CALLBACK_SERVICE_INVOKE))) {
            String methodsStr = invoker.getUrl().getParameters().get("methods");
            boolean hasMethod = false;
            if (methodsStr == null || !methodsStr.contains(",")) {
                hasMethod = inv.getMethodName().equals(methodsStr);
            } else {
                String[] methods = methodsStr.split(",");
                for (String method : methods) {
                    if (inv.getMethodName().equals(method)) {
                        hasMethod = true;
                        break;
                    }
                }
            }
            if (!hasMethod) {
                logger.warn(new IllegalStateException("The methodName " + inv.getMethodName()
                                                      + " not found in callback service interface ,invoke will be ignored."
                                                      + " please update the api interface. url is:"
                                                      + invoker.getUrl()) + " ,invocation is :" + inv);
                return null;
            }
        }
        RpcContext rpcContext = RpcContext.getContext();
        rpcContext.setRemoteAddress(channel.getRemoteAddress());
        Result result = invoker.invoke(inv);

        if (result instanceof AsyncRpcResult) {
            return ((AsyncRpcResult) result).getResultFuture().thenApply(r -> (Object) r);

        } else {
            return CompletableFuture.completedFuture(result);
        }
    }
```

### 3) getInvoker

这里面是获得一个invoker的实现
DubboExporter<?> exporter = (DubboExporter<?>) exporterMap.get(serviceKey);
这段代码非常熟悉，exporterMap不就是我们之前在分析服务发布的过程中，保存的Invoker吗？
而key，就是对应的interface:port 。

```java
Invoker<?> getInvoker(Channel channel, Invocation inv) throws RemotingException {
    boolean isCallBackServiceInvoke = false;
    boolean isStubServiceInvoke = false;
    int port = channel.getLocalAddress().getPort();
    String path = inv.getAttachments().get(Constants.PATH_KEY);

    // if it's callback service on client side
    isStubServiceInvoke = Boolean.TRUE.toString().equals(inv.getAttachments().get(Constants.STUB_EVENT_KEY));
    if (isStubServiceInvoke) {
        port = channel.getRemoteAddress().getPort();
    }

    //callback
    isCallBackServiceInvoke = isClientSide(channel) && !isStubServiceInvoke;
    if (isCallBackServiceInvoke) {
        path += "." + inv.getAttachments().get(Constants.CALLBACK_SERVICE_KEY);
        inv.getAttachments().put(IS_CALLBACK_SERVICE_INVOKE, Boolean.TRUE.toString());
    }

    String serviceKey = serviceKey(port, path, inv.getAttachments().get(Constants.VERSION_KEY), inv.getAttachments().get(Constants.GROUP_KEY));
    DubboExporter<?> exporter = (DubboExporter<?>) exporterMap.get(serviceKey);

    if (exporter == null) {
        throw new RemotingException(channel, "Not found exported service: " + serviceKey + " in " + exporterMap.keySet() + ", may be version or group mismatch " +
                                    ", channel: consumer: " + channel.getRemoteAddress() + " --> provider: " + channel.getLocalAddress() + ", message:" + inv);
    }

    return exporter.getInvoker();
}
```

**exporterMap**

```java
protected final Map<String, Exporter<?>> exporterMap = new ConcurrentHashMap<String, Exporter<?>>();
```

在服务发布时，实际上是把invoker包装成了DubboExpoter。然后放入到exporterMap中,如下发布服务的时候：

```java
 public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        URL url = invoker.getUrl();

        // export service.
        String key = serviceKey(url);
        DubboExporter<T> exporter = new DubboExporter<T>(invoker, key, exporterMap);
        exporterMap.put(key, exporter);
			.....

        return exporter;
    }
```

### 4) invoker.invoke(inv)

接着调用invoker； invoke那么再回忆一下，此时的invoker是一个什么呢？
	invoker=ProtocolFilterWrapper(InvokerDelegate(DelegateProviderMetaDataInvoker(AbstractProxy
Invoker)))最后一定会进入到这个代码里面。



### 5) AbstractProxyInvoker

​	在AbstractProxyInvoker里面，doInvoker本质上调用的是wrapper.invokeMethod()

```java
public class JavassistProxyFactory extends AbstractProxyFactory {

    @Override
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        // TODO Wrapper cannot handle this scenario correctly: the classname contains '$'
        final Wrapper wrapper = Wrapper.getWrapper(proxy.getClass().getName().indexOf('$') < 0 ? proxy.getClass() : type);
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            @Override
            protected Object doInvoke(T proxy, String methodName,
                                      Class<?>[] parameterTypes,
                                      Object[] arguments) throws Throwable {
                return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments);
            }
        };
    }

}
```

而Wrapper是一个动态代理类，它的定义是这样的， 最终调用w.sayHello()方法进行处理

```java
public Object invokeMethod(Object o, String n, Class[] p, Object[] v) throws
java.lang.reflect.InvocationTargetException {
    com.gupaoedu.dubbo.practice.ISayHelloService w;
    try {
    	w = ((com.gupaoedu.dubbo.practice.ISayHelloService) $1);
    } catch (Throwable e) {
   	 throw new IllegalArgumentException(e);
    }
    try {
   	 	if ("sayHello".equals($2) && $3.length == 1) {
   			 return ($w) w.sayHello((java.lang.String) $4[0]);
    	}
    } catch (Throwable e) {
    	throw new java.lang.reflect.InvocationTargetException(e);
    }
    throw new org.apache.dubbo.common.bytecode.NoSuchMethodException("Not
   	 found method \"" + $2 + "\" in class
   	 com.gupaoedu.dubbo.practice.ISayHelloService.");
}
```

到此为止，服务端的处理过程就分析完了。