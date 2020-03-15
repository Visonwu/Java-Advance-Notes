

参考：<https://blog.csdn.net/u012117723/article/details/80734653>

猜想：

- 加载配置
- 发布服务

spring提供了可扩展的xml配置，dubbo扩展了`dubbo.xsd`,相关的命名空间处理器是`DubboNamespaceHandler`

```java
public class DubboNamespaceHandler extends NamespaceHandlerSupport {

    static {
        Version.checkDuplicate(DubboNamespaceHandler.class);
    }

    @Override
    public void init() {
        registerBeanDefinitionParser("application", new DubboBeanDefinitionParser(ApplicationConfig.class, true));
        registerBeanDefinitionParser("module", new DubboBeanDefinitionParser(ModuleConfig.class, true));
        registerBeanDefinitionParser("registry", new DubboBeanDefinitionParser(RegistryConfig.class, true));
        registerBeanDefinitionParser("config-center", new DubboBeanDefinitionParser(ConfigCenterBean.class, true));
        registerBeanDefinitionParser("metadata-report", new DubboBeanDefinitionParser(MetadataReportConfig.class, true));
        registerBeanDefinitionParser("monitor", new DubboBeanDefinitionParser(MonitorConfig.class, true));
        registerBeanDefinitionParser("provider", new DubboBeanDefinitionParser(ProviderConfig.class, true));
        registerBeanDefinitionParser("consumer", new DubboBeanDefinitionParser(ConsumerConfig.class, true));
        //对于服务发布我们关注这个
        registerBeanDefinitionParser("protocol", new DubboBeanDefinitionParser(ProtocolConfig.class, true));
        registerBeanDefinitionParser("service", new DubboBeanDefinitionParser(ServiceBean.class, true));
        registerBeanDefinitionParser("reference", new DubboBeanDefinitionParser(ReferenceBean.class, false));
        registerBeanDefinitionParser("annotation", new AnnotationBeanDefinitionParser());
    }
}
```



## 1.入口

`ServiceBean`利用实现`ApplicationListener`接口在容器初始化发布dubbo服务

```java
@Override
public void onApplicationEvent(ContextRefreshedEvent event) {
    if (!isExported() && !isUnexported()) {
        if (logger.isInfoEnabled()) {
            logger.info("The service ready on spring started. service: " + getInterface());
        }
        export();
    }
}
```

ServiceBean.export()  -->ServiceConfig.export() -->doExport() -->doExportUrls()->

## 2. ServiceConfig.doExportUrls()

1. 加载所有配置的注册中心地址
2. 遍历所有配置的协议，protocols
3. 针对每种协议发布一个对应协议的服务

```java
private void doExportUrls() {
    //加载所有配置的注册中心的地址，组装成一个URL 	    //(registry://ip:port/org.apache.dubbo.registry.RegistryService的东西)
    List<URL> registryURLs = loadRegistries(true);
    for (ProtocolConfig protocolConfig : protocols) {
        String pathKey = URL.buildKey(getContextPath(protocolConfig).map(p -> p + "/" + path).orElse(path), group, version);
        ProviderModel providerModel = new ProviderModel(pathKey, ref, interfaceClass);
        ApplicationModel.initProviderModel(pathKey, providerModel);
        //
        doExportUrlsFor1Protocol(protocolConfig, registryURLs);
    }
}
```

## 3.ServiceConfig.doExportUrlsFor1Protocol()

发布指定协议的服务，我们以Dubbo服务为例，由于代码太多，就不全部贴出来

1. 前面的一大串if else代码，是为了把当前服务下所配置的<dubbo:method>参数进行解析，保存到map集合中
2. 获得当前服务需要暴露的ip和端口
3. 把解析到的所有数据，组装成一个URL,大概应该是：dubbo://192.168.13.1:20881/com.gupaoedu.dubbo.practice.ISayHelloService

```java
private void doExportUrlsFor1Protocol(ProtocolConfig protocolConfig, List<URL> registryURLs) {
        String name = protocolConfig.getName();
        if (StringUtils.isEmpty(name)) {
            name = Constants.DUBBO;
        }
    ...
    // export service
        String host = this.findConfigedHosts(protocolConfig, registryURLs, map);
        Integer port = this.findConfigedPorts(protocolConfig, name, map);
        URL url = new URL(name, host, port, getContextPath(protocolConfig).map(p -> p + "/" + path).orElse(path), map);
    
    //如果scope!="none"则发布服务，默认scope为null。如果scope不为none，判断是否为local或remote，从而发布Local服务或Remote服务，默认两个都会发布
   String scope = url.getParameter(Constants.SCOPE_KEY);
        。。。。
     //这个是获取代理服务Invoker，使用代理生成       
     Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, url);
    DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);
	//这里表示发布远程服务
    Exporter<?> exporter = protocol.export(wrapperInvoker);
    exporters.add(exporter);
                }
                /**
                 * @since 2.7.0
                 * ServiceData Store 元数据存储
                 */
                MetadataReportService metadataReportService = null;
                if ((metadataReportService = getMetadataReportService()) != null) {
                    metadataReportService.publishProvider(url);
                }
            }
        }
        this.urls.add(url);
}
```

### 3.1 Invoker

服务的发布分三个阶段：

- 第一个阶段会创造一个invoker
- 第二个阶段会把经历过一系列处理的invoker（各种包装），在DubboProtocol中保存到exporterMap中
- 第三个阶段把dubbo协议的url地址注册到注册中心上

`Invoker`是Dubbo领域模型中非常重要的一个概念, 和ExtensionLoader的重要性是一样的，如果Invoker没有搞懂，那么不算是看懂了Dubbo的源码。这段代码是还没有分析过的。以这个作为入口来分析我们前面export出去的invoker到底是啥东西.

```java
Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(Constants.EXPORT_KEY, url.toFullString()));
```

首先上面的proxyFacotry也是通过SPI的方式获取,然后动态生成的字节码组成的

```java
import org.apache.dubbo.common.extension.ExtensionLoader;
public class ProxyFactory$Adaptive implements org.apache.dubbo.rpc.ProxyFactory {
    public java.lang.Object getProxy(org.apache.dubbo.rpc.Invoker arg0) throws org.apache.dubbo.rpc.RpcException {
        if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument == null");
        if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument getUrl() == null");
        org.apache.dubbo.common.URL url = arg0.getUrl();
        String extName = url.getParameter("proxy", "javassist");
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.ProxyFactory) name from url (" + url.toString() + ") use keys([proxy])");
        org.apache.dubbo.rpc.ProxyFactory extension = (org.apache.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.ProxyFactory.class).getExtension(extName);
        return extension.getProxy(arg0);
    }
    public java.lang.Object getProxy(org.apache.dubbo.rpc.Invoker arg0, boolean arg1) throws org.apache.dubbo.rpc.RpcException {
        if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument == null");
        if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument getUrl() == null");
        org.apache.dubbo.common.URL url = arg0.getUrl();
        String extName = url.getParameter("proxy", "javassist");
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.ProxyFactory) name from url (" + url.toString() + ") use keys([proxy])");
        org.apache.dubbo.rpc.ProxyFactory extension = (org.apache.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.ProxyFactory.class).getExtension(extName);
        return extension.getProxy(arg0, arg1);
    }
    public org.apache.dubbo.rpc.Invoker getInvoker(java.lang.Object arg0, java.lang.Class arg1, org.apache.dubbo.common.URL arg2) throws org.apache.dubbo.rpc.RpcException {
        if (arg2 == null) throw new IllegalArgumentException("url == null");
        org.apache.dubbo.common.URL url = arg2;
        String extName = url.getParameter("proxy", "javassist");
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.ProxyFactory) name from url (" + url.toString() + ") use keys([proxy])");
        org.apache.dubbo.rpc.ProxyFactory extension = (org.apache.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.ProxyFactory.class).getExtension(extName);
        return extension.getInvoker(arg0, arg1, arg2);
    }
}
```

这里默认调用的`JavassistProxyFactory`

```java
public class JavassistProxyFactory extends AbstractProxyFactory {
...
    @Override
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        // TODO Wrapper cannot handle this scenario correctly: the classname contains '$'
        final Wrapper wrapper = Wrapper.getWrapper(proxy.getClass().getName().indexOf('$') < 0 ? proxy.getClass() : type);
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            @Override
            protected Object doInvoke(T proxy, String methodName,
                                      Class<?>[] parameterTypes,
                                      Object[] arguments) throws Throwable {
                //建议通过断点调试下，最终就是调用的wrappter的invoke方法，这里的wrapper也是通过代理动态生成的代码。
                return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments);
            }
        };
    }
}
```

比如我自己发布的代码有一个方法，这里invokeMethod方法如下：

```java
public class Wrapper1 {

    public void setPropertyValue(Object o, String n, Object v) {
        com.vison.dubbospringapi.IHelloService w;
        try{
            w = ((com.vison.dubbospringapi.IHelloService)$1);
        }catch(Throwable e){
            throw new IllegalArgumentException(e);
        }
        throw new org.apache.dubbo.common.bytecode.NoSuchPropertyException("Not found property \""+$2+"\" field or setter method in class com.vison.dubbospringapi.IHelloService.");
    }
	
	//这里最终还是调用自己IHelloService实现类的方法sayHello，做了一个代理
    public Object invokeMethod(Object o, String n, Class[] p, Object[] v)
            throws java.lang.reflect.InvocationTargetException{
        com.vison.dubbospringapi.IHelloService w;
        try{
            w = ((com.vison.dubbospringapi.IHelloService)$1);
        }catch(Throwable e){
            throw new IllegalArgumentException(e);
        }
        try{
            if( "sayHello".equals( $2 )  &&  $3.length == 1 ) {
            return ($w)w.sayHello((java.lang.String)$4[0]);
        } catch(Throwable e) {
            throw new java.lang.reflect.InvocationTargetException(e);
        }
       throw new org.apache.dubbo.common.bytecode.
             NoSuchMethodException("Not found method \""+$2+"\" in class com.vison.dubbospringapi.IHelloService.");
    }
}
```

​		所以，简单总结一下Invoke本质上应该是一个代理，经过层层包装最终进行了发布。当消费者发起请求的时候，会获得这个invoker进行调用。最终发布出去的invoker, 也不是单纯的一个代理，也是经过多层包装



## 4.protocol.export

上面的protocol是通过如下方式获取的

```java
private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

```

通过上面的方式获取到的protocol是通过javaassit字节码重新生成的，如下所示：

这个动态代理类，会根据`url中配置的protocol name`来实现对应协议的适配

```java
package org.apache.dubbo.rpc;
import org.apache.dubbo.common.extension.ExtensionLoader;

public class Protocol$Adaptive implements Protocol {
    public void destroy()  {
        throw new UnsupportedOperationException("The method public abstract void org.apache.dubbo.rpc.Protocol.destroy() of interface org.apache.dubbo.rpc.Protocol is not adaptive method!");
    }
    public int getDefaultPort()  {
        throw new UnsupportedOperationException("The method public abstract int org.apache.dubbo.rpc.Protocol.getDefaultPort() of interface org.apache.dubbo.rpc.Protocol is not adaptive method!");
    }
    public Exporter export(Invoker arg0) throws RpcException {
        if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument == null");
        if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument getUrl() == null");
        org.apache.dubbo.common.URL url = arg0.getUrl();
        String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() );
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.Protocol) name from url (" + url.toString() + ") use keys([protocol])");
        Protocol extension = (Protocol)ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(extName);
        return extension.export(arg0);
    }
    public Invoker refer(Class arg0, org.apache.dubbo.common.URL arg1) throws RpcException {
        if (arg1 == null) throw new IllegalArgumentException("url == null");
        org.apache.dubbo.common.URL url = arg1;
        String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() );
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.Protocol) name from url (" + url.toString() + ") use keys([protocol])");
        Protocol extension = (Protocol)ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(extName);
        return extension.refer(arg0, arg1);
    }
}
```



​		那么在当前的场景中，protocol会是调用谁呢？目前发布的invoker(URL)，实际上是一个registry://协议，所以Protocol$Adaptive，会通过getExtension(extName)得到一个RegistryProtocol(当让这里有一个wrapper包装了RegistryProtocol不过，包装器对RegistryProtocol都直接通过了)，如下

```java
public class ProtocolFilterWrapper implements Protocol {
    。。。。
    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        //这里表示如果当前是Registry协议直接调用protocol.export（）方法
        if (Constants.REGISTRY_PROTOCOL.equals(invoker.getUrl().getProtocol())) {
            return protocol.export(invoker);
        }
        return protocol.export(buildInvokerChain(invoker, Constants.SERVICE_FILTER_KEY, Constants.PROVIDER));
    }
    。。。。
}
```

所以重点看RegistryProtocol

## 5.RegistryProtocol

很明显，这个RegistryProtocol是用来实现服务注册的；这里面会有很多处理逻辑
• 实现对应协议的服务发布
• 实现服务注册
• 订阅服务重写

```java
public class RegistryProtocol implements Protocol {
	@Override
    public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        URL registryUrl = getRegistryUrl(originInvoker);
        // url to export locally
        URL providerUrl = getProviderUrl(originInvoker);

        //订阅override数据。在admin控制台可以针对服务进行治理，比如修改权重，修改路由机制等，当注册中心有此服务的覆盖配置注册进来时，推送消息给提供者，重新暴露服务
        final URL overrideSubscribeUrl = getSubscribedOverrideUrl(providerUrl);
        final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl, originInvoker);
        overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);

        providerUrl = overrideUrlWithConfig(providerUrl, overrideSubscribeListener);
        //这里通过具体的协议发布服务
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker, providerUrl);

        // 根据invoker中的url获取Registry实例: zookeeperRegistry
        final Registry registry = getRegistry(originInvoker);
        final URL registeredProviderUrl = getRegisteredProviderUrl(providerUrl, registryUrl);
        ProviderInvokerWrapper<T> providerInvokerWrapper = ProviderConsumerRegTable.registerProvider(originInvoker,
                registryUrl, registeredProviderUrl);
        boolean register = registeredProviderUrl.getParameter("register", true);
        if (register) {{//是否配置了注册中心，如果是， 则需要注册
            register(registryUrl, registeredProviderUrl);
            providerInvokerWrapper.setReg(true);
        }
		////设置注册中心的订阅
        // Deprecated! Subscribe to override rules in 2.6.x or before.
        registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);

        exporter.setRegisterUrl(registeredProviderUrl);
        exporter.setSubscribeUrl(overrideSubscribeUrl);
        //保证每次export都返回一个新的exporter实例
        //Ensure that a new exporter instance is returned every time export
        return new DestroyableExporter<>(exporter);
    }
}
```

### 5.1 doLocalExport() 发布服务

先通过doLocalExport来暴露一个服务，本质上应该是启动一个通信服务,主要的步骤是将本地ip和20880端口打开，进行监听originInvoker: 应该是registry://ip:port/com.alibaba.dubbo.registry.RegistryService

key: 从originInvoker中获得发布协议的url: dubbo://ip:port/...

bounds: 一个prviderUrl服务export之后，缓存到 bounds中，所以一个providerUrl只会对应一个exporter

computeIfAbsent就相当于, java8的语法

```java
private <T> ExporterChangeableWrapper<T> doLocalExport(final Invoker<T> originInvoker, URL providerUrl) {
    String key = getCacheKey(originInvoker);

    return (ExporterChangeableWrapper<T>) bounds.computeIfAbsent(key, s -> {
        Invoker<?> invokerDelegete = new InvokerDelegate<>(originInvoker, providerUrl);
        return new ExporterChangeableWrapper<>((Exporter<T>) protocol.export(invokerDelegete), originInvoker); //重点是这里的protocl.export
    });
}
```

#### 1) DubboProtocol.export

​		基于动态代理的适配，很自然的就过渡到了DubboProtocol这个协议类中，但是实际上是DubboProtocol吗？
​	这里并不是获得一个单纯的DubboProtocol扩展点，而是会通过Wrapper对Protocol进行装饰，装饰器分别为: ProtocolFilterWrapper/QosProtocolWrapper/ProtocolListenerWrapper//DubboProtocol

这需要自己去看`SPI`中wrapper对于getExTension的SPI节点，如果配置文件中有wrapper会做包装。



> 我们可以在dubbo的配置文件中找到三个Wrapper

- QosprotocolWrapper， 如果当前配置了注册中心，则会启动一个Qos server.qos是dubbo的在线运维命令，dubbo2.5.8新版本重构了telnet模块，提供了新的telnet命令支持，新版本的telnet端口与dubbo协议的端口是不同的端口，默认为22222
- ProtocolFilterWrapper，对invoker进行filter的包装，实现请求的过滤
- ProtocolListenerWrapper， 用于服务export时候插入监听机制，暂未实现



当然这里的ProtocolFilterWrapper会使用责任链来对请求做处理

```java
private static <T> Invoker<T> buildInvokerChain(final Invoker<T> invoker, String key, String group) {
        Invoker<T> last = invoker;
    //通过激活点获取拦截器做处理
        List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class).getActivateExtension(invoker.getUrl(), key, group);
        if (!filters.isEmpty()) {
            for (int i = filters.size() - 1; i >= 0; i--) {
               ....
            }
        }
        return last;
    }
```



最终调用DubboProtocol.export

```java
@Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        URL url = invoker.getUrl();

 //获取服务标识，理解成服务坐标也行。由服务组名，服务名，服务版本号以及端口组成。比如   //${group}/com.vison.practice.dubbo.ISayHelloService:${version}:20880
        String key = serviceKey(url);
        DubboExporter<T> exporter = new DubboExporter<T>(invoker, key, exporterMap);
        exporterMap.put(key, exporter);

        //export an stub service for dispatching event
        Boolean isStubSupportEvent = url.getParameter(Constants.STUB_EVENT_KEY, Constants.DEFAULT_STUB_EVENT);
        Boolean isCallbackservice = url.getParameter(Constants.IS_CALLBACK_SERVICE, false);
        if (isStubSupportEvent && !isCallbackservice) {
            String stubServiceMethods = url.getParameter(Constants.STUB_EVENT_METHODS_KEY);
            if (stubServiceMethods == null || stubServiceMethods.length() == 0) {
                if (logger.isWarnEnabled()) {
                    logger.warn(new IllegalStateException("consumer [" + url.getParameter(Constants.INTERFACE_KEY) +
                            "], has set stubproxy support event ,but no stub methods founded."));
                }

            } else {
                stubServiceMethodsMap.put(url.getServiceKey(), stubServiceMethods);
            }
        }
		//启动服务
        openServer(url);
        optimizeSerialization(url);

        return exporter;
    }
```

```java
private void openServer(URL url) {
    // find server.
    String key = url.getAddress();
    //client can export a service which's only for server to invoke
    boolean isServer = url.getParameter(Constants.IS_SERVER_KEY, true);
    if (isServer) {
        ExchangeServer server = serverMap.get(key);
        if (server == null) {
            synchronized (this) {
                server = serverMap.get(key);
                if (server == null) {
                    //创建一个一个服务
                    serverMap.put(key, createServer(url));
                }
            }
        } else {
            // server supports reset, use together with override
            server.reset(url);
        }
    }
}
```

```java
 private ExchangeServer createServer(URL url) {
        .....

        ExchangeServer server;
        try {
            //绑定服务
            server = Exchangers.bind(url, requestHandler);
        } catch (RemotingException e) {
            throw new RpcException("Fail to start server(url: " + url + ") " + e.getMessage(), e);
        }
...
        return server;
    }
```

```java
public static ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        url = url.addParameterIfAbsent(Constants.CODEC_KEY, "exchange");
    	//同样的代理这里通过获取@SPI的Exchanger最终会调用HeadExchanger
        return getExchanger(url).bind(url, handler);
    }
```

#### 2) HeaderExchanger

```java
public class HeaderExchanger implements Exchanger {
    public ExchangeServer bind(URL url, ExchangeHandler handler) throws
        RemotingException {
            return new HeaderExchangeServer(Transporters.bind(url, new DecodeHandler(new HeaderExchangeHandler(handler))));
    }
}
```

#### 3) Transporters

```java
public static Server bind(URL url, ChannelHandler... handlers) throws RemotingException {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handlers == null || handlers.length == 0) {
            throw new IllegalArgumentException("handlers == null");
        }
        ChannelHandler handler;
        if (handlers.length == 1) {
            handler = handlers[0];
        } else {
            handler = new ChannelHandlerDispatcher(handlers);
        }
        return getTransporter().bind(url, handler);
}
```

```java
//然后调用netty4的服务
public class NettyTransporter implements Transporter {  
    
    @Override
    public Server bind(URL url, ChannelHandler listener) throws RemotingException {
        return new NettyServer(url, listener);
    }
}
```

#### 4) NettyServer.doOpen()通过netty开放服务

```java
 @Override
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

### 5.2 服务注册

在RegistyProtol.export()中除了发布服务还有注册服务到注册中心上



#### 1) getRegistry()

1. 把url转化为对应配置的注册中心的具体协议
2. 根据具体协议，从registryFactory中获得指定的注册中心实现,同样这里的registryFactory也是通过SPI获取

#### 2) ZookeeperRegistryFactory

```java
public class ZookeeperRegistryFactory extends AbstractRegistryFactory {	
	@Override
    public Registry createRegistry(URL url) {
        return new ZookeeperRegistry(url, zookeeperTransporter);
    }
}
```

#### 3) ZookeeperRegistry

```java

public class ZookeeperRegistry extends FailbackRegistry {
	public ZookeeperRegistry(URL url, ZookeeperTransporter zookeeperTransporter) {
        super(url);
        if (url.isAnyHost()) {
            throw new IllegalStateException("registry address == null");
        }
        //获取group
        String group = url.getParameter(Constants.GROUP_KEY, DEFAULT_ROOT);
        if (!group.startsWith(Constants.PATH_SEPARATOR)) {
            group = Constants.PATH_SEPARATOR + group;
        }
        this.root = group;
        //这里创建zk连接
        zkClient = zookeeperTransporter.connect(url);
        //监听变化
        zkClient.addStateListener(state -> {
            if (state == StateListener.RECONNECTED) {
                try {
                    recover();
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        });
    }
}
```

#### 4) RegistryProtocol.export()

​	继续往下分析，会调用registry.register去讲dubbo://的协议地址注册到zookeeper上
这个方法会调用FailbackRegistry类中的register. 为什么呢？因为ZookeeperRegistry这个类中并没有register这个方法，但是他的父类FailbackRegistry中存在register方法，而这个类又重写了AbstractRegistry类中的register方法。所以我们可以直接定位大FailbackRegistry这个类中的register方法中

```java
if (register) {
    //主类注册
    register(registryUrl, registeredProviderUrl);
    providerInvokerWrapper.setReg(true);
}
```

#### 5) FailbackRegistry.register()

FailbackRegistry，从名字上来看，是一个失败重试机制

- 调用父类的register方法，讲当前url添加到缓存集合中

- 调用doRegister方法，这个方法很明显，是一个抽象方法，会由ZookeeperRegistry子类实现。

```java
public abstract class FailbackRegistry extends AbstractRegistry {
	@Override
    public void register(URL url) {
        super.register(url);
        removeFailedRegistered(url);
        removeFailedUnregistered(url);
        try {
            // 调用子类实现真正的服务注册，把url注册到zk上
            doRegister(url);
        } catch (Exception e) {
            Throwable t = e;

            // If the startup detection is opened, the Exception is thrown directly.
            boolean check = getUrl().getParameter(Constants.CHECK_KEY, true)
                    && url.getParameter(Constants.CHECK_KEY, true)
                    && !Constants.CONSUMER_PROTOCOL.equals(url.getProtocol());
            boolean skipFailback = t instanceof SkipFailbackWrapperException;
            if (check || skipFailback) {
                if (skipFailback) {
                    t = t.getCause();
                }
                throw new IllegalStateException("Failed to register " + url + " to registry " + getUrl().getAddress() + ", cause: " + t.getMessage(), t);
            } else {
                logger.error("Failed to register " + url + ", waiting for retry, cause: " + t.getMessage(), t);
            }

            // Record a failed registration request to a failed list, retry regularly
            addFailedRegistered(url);
        }
    }
}
```

#### 6) ZookeeperRegistry.doRegister()

​	最终这里注册当前url

```java
public class ZookeeperRegistry extends FailbackRegistry {
	@Override
    public void doRegister(URL url) {
        try {
            zkClient.create(toUrlPath(url), url.getParameter(Constants.DYNAMIC_KEY, true));
        } catch (Throwable e) {
            throw new RpcException("Failed to register " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }
}
```

