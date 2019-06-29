**Channel**

​	在 Netty 中，Channel 是一个 Socket 的抽象，它为用户提供了关于 Socket 状态(是否是连接还是断开)以及对 Socket的读写等操作。每当 Netty 建立了一个连接后, 都创建一个对应的 Channel 实例。
​	除了 TCP 协议以外，Netty 还支持很多其他的连接协议, 并且每种协议还有 NIO(非阻塞 IO)和 OIO(Old-IO, 即传统的阻塞 IO)版本的区别。不同协议不同的阻塞类型的连接都有不同的 Channel 类型与之对应下面是一些常用的 Channel。

| 类名                   | 解释                                                         |
| ---------------------- | ------------------------------------------------------------ |
| NioSocketChannel       | 异步非阻塞的客户端Tcp Socket连接                             |
| NioServerSocketChannel | 异步非阻塞的服务端Tcp Socket连接                             |
| NioDatagramChannel     | 异步非阻塞UDP连接                                            |
| NioSctpChannel         | 异步客户端Sctp(Stream control Transmission Protocol,流控制传输协议)连接 |
| NioSctpServerChannel   | 异步服务端Sctp连接                                           |
| OioSocketChannel       | 同步阻塞的客户端Tcp Socket连接                               |
| OioServerSocketChannel | 同步阻塞的服务端Tcp Socket连接                               |
| OioDatagramChannel     | 同步阻塞UDP连接                                              |
| OioSctpChannel         | 同步客户端Sctp连接                                           |
| OioSctpServerChannel   | 同步服务端Sctp连接                                           |



# 1.netty的客户端

​	Bootstrap 是 Netty 提供的一个便利的工厂类, 我们可以通过它来完成 Netty 的客户端或服务器端的 Netty 初始化。下面我先来看一个例子, 从客户端和服务器端分别分析一下 Netty 的程序是如何启动的。首先，让我们从客户端的代码片段开始：

```java
public class NettyClient {

    public static void main(String[] args) {
        EventLoopGroup workGroup = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
							ChannelPipeline p = socketChannel.pipeline();
                            p.addLast(new StringDecoder());
                            p.addLast(new StringEncoder());
                        }
                    });
            //发起同步连接
            ChannelFuture sync = bootstrap.bind("127.0.0.1", 9000).sync();

            sync.channel().closeFuture().sync();
        }catch (Exception e){
            e.printStackTrace();
            //关闭资源
            workGroup.shutdownGracefully();
        }
    }
}
```



从上面的客户端代码虽然简单, 但是却展示了 Netty 客户端初始化时所需的所有内容：

- 1、EventLoopGroup：不论是服务器端还是客户端, 都必须指定 EventLoopGroup。在这个例子中, 指定了
  NioEventLoopGroup, 表示一个 NIO 的 EventLoopGroup。

- 2、ChannelType: 指定 Channel 的类型。 因为是客户端，因此使用了 NioSocketChannel。
- 3、Handler: 设置处理数据的 Handler。



## 1.1 BootStrap的初始化 - connect方法带来的影响

​		如下所示Bootsrap的时序图，初始化的时候创建Channel，这里的channel是上面我们自己通过class定义的，然后通过`ReflectiveChannelFactory`反射创建的。

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4ie3c9rk5j20ix050gm2.jpg)

```java
final ChannelFuture initAndRegister() {
        Channel channel = null;
   		...删除无关代码
            channel = channelFactory.newChannel(); //这里利用反射创建channel；这里是NioSocketChannel
            init(channel);

        ChannelFuture regFuture = config().group().register(channel);
      ...删除无关代码
    
    return regFuture;
```

### 1） 新建NioSocketChannel对象

​	**总结：**当我们新建一个`NioSocketChannel`做了如下工作：

- 调用`NioSocketChannel.newSocket`打开一个新的NIOSocketChannel
- AbstractChannel(Channel parent)中需要初始化的属性
  - id:每一个channel都需要一个唯一的id，这里可以看到id的生成方式
  - parent：这里属性值为null
  - unsafe：实例化一个Unsafe对象，
  - pipeLine:是一个DefaultChannelPipeline(this)对象
- AbstractNioChannel中的属性
  - ch:赋值NioSocketChannel
  - readInterestOp设置为读事件SelectionKey.OP_READ
  - ch:ch.configureBlocking(false)配置为非阻塞模式
- NioSocketChannel中的属性
  - config ：new NioSocketChannelConfig(this, socket.socket());新建配置类

```java
public NioSocketChannel() {
    this(DEFAULT_SELECTOR_PROVIDER);
}

//1.继续看
public NioSocketChannel(SelectorProvider provider) {
    this(newSocket(provider));
}

	//1.1这里newSocket方法会创建一个SockeChannel
    private static SocketChannel newSocket(SelectorProvider provider) {
        try {
            return provider.openSocketChannel();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }
//2.然后不停往super父类查看直到 AbstractChannel
//当然从中途传递过来的parent为null.
protected AbstractChannel(Channel parent) {
    this.parent = parent;
    id = newId();
    unsafe = newUnsafe();
    pipeline = newChannelPipeline(); //这里创建DefaultChannelPipeline
}

```

### 2）Unsafe对象的初始化

NioSocketChannel重写了newUnsafe方法，当然我们Unsafe提供了对底层网络的操作。如下所示：

```java
@Override
protected AbstractNioUnsafe newUnsafe() {
    return new NioSocketChannelUnsafe();
}

```



### 3）Pipeline的初始化

​	当一个Channel被创建，就会自动创建一个PipeLine。

这里维护了一个`AbstractChannelHandlerContext`双向列表，他们持有NioSocketChannel引用；

实例对象如下：

```java
/**
*这里的channel就是NioSocketChannel
*/
protected DefaultChannelPipeline(Channel channel) {
    this.channel = ObjectUtil.checkNotNull(channel, "channel");
    succeededFuture = new SucceededChannelFuture(channel, null);
    voidPromise =  new VoidChannelPromise(channel, true);

    tail = new TailContext(this);//尾结点
    head = new HeadContext(this);//头结点

    head.next = tail;//默认头结点连接尾结点
    tail.prev = head;
}
```

#### 3.1) HeadContext

​		HeadContext和TailContext的inbound和outbound的值设置是反的

```java
HeadContext(DefaultChannelPipeline pipeline) {
    super(pipeline, null, HEAD_NAME, false, true);//这里调用了父类`AbstractChannelHandlerContext`,传递的inbound=false,outbound=true
    unsafe = pipeline.channel().unsafe();
    setAddComplete();
}
```

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4if4656txj214m0a4q3c.jpg)

#### 3.2) TailContext

```java
TailContext(DefaultChannelPipeline pipeline) {
    super(pipeline, null, TAIL_NAME, true, false);//这里调用了父类`AbstractChannelHandlerContext`,传递的inbound=true,outbound=false
    setAddComplete();
}
```

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4if4bpmu5j21490avq39.jpg)



## 1.2 NioEventLoopGroup初始化

**总结：**NioEventLoopGroup初始化做的如下事：

- 1、EventLoopGroup(其实是MultithreadEventExecutorGroup)内部维护一个类型为EventExecutor children 数组,其大小是nThreads,这样就构成了一个线程池。
- 2、如果我们在实例化NioEventLoopGroup 时，如果指定线程池大小，则nThreads 就是指定的值，反之是处理器核心数* 2。
- 3、MultithreadEventExecutorGroup 中会调用newChild()抽象方法来初始化children 数组。
- 4、抽象方法newChild()是在NioEventLoopGroup 中实现的，它返回一个NioEventLoop 实例。
- 5、NioEventLoop 属性赋值：
  - provider：在NioEventLoopGroup 构造器中通过SelectorProvider.provider()获取一个SelectorProvider。
  - selector：在NioEventLoop 构造器中通过调用通过provider.openSelector()方法获取一个selector 对象。



**该类的结构图：**这里其实就是一个`ScheduledExecutorService`

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4ifd9l5xmj20j70iz3yv.jpg)

当我们使用 `EventLoopGroup workGroup = new NioEventLoopGroup();`这里做的事。

### 1）默认创建两倍CPU核数线程数

```java
//当我们没有传递线程数时，默认创建的两倍的cpu核心的线程数。
protected MultithreadEventLoopGroup(int nThreads, Executor executor, Object... args) {
     super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, executor, args);
 }
```

### 2）根据线程数 初始化多个EventExecutor

​		这里`EventExecutor`可以看成就是定时器`ScheduledExecutorService`外加其他一些功能；调用到`MultithreadEventExecutorGroup`的构造器，

- 创建并维护一个EventExecutor数组，数量是线程数
- 根据线程数是否是2的幂次，提供不同的选择器

如下所示主要功能代码：

```java
protected MultithreadEventExecutorGroup(int nThreads, Executor executor,
       EventExecutorChooserFactory chooserFactory, Object... args) {
    children = new EventExecutor[nThreads];
    for (int i = 0; i < nThreads; i ++) {
        children[i] = newChild(executor, args);
    }            
    chooser = chooserFactory.newChooser(children); //这里是为了提供选择的效率，每次需要一个EventExecutor，直接调用这个的next方法返回一个即可。
    ...省掉不相关代码
}   

	//当然这里的newChooser就是根据当前线程是否是2的幂次方返回不同的选择器，当然不同的选择器只是在每次next的时候使用不同的EventExecutor
	public EventExecutorChooser newChooser(EventExecutor[] executors) {
        if (isPowerOfTwo(executors.length)) {
            return new PowerOfTwoEventExecutorChooser(executors);
            
            
        } else {
            return new GenericEventExecutorChooser(executors);
        }
    }
		
	//这两种的不同之处在于他们在选择EventExecutor的算法不同，这里只是让是2的幂次方采用 与的算法，这样比采用取模的效率更高而已。
		//PowerOfTwoEventExecutorChooser	2的幂次方 PowerOfTwoEventExecutorChooser
		public EventExecutor next() {
            return executors[idx.getAndIncrement() & executors.length - 1];
        }
		//GenericEventExecutorChooser
		public EventExecutor next() {
            return executors[Math.abs(idx.getAndIncrement() % executors.length)];
		}	
```



## 1.3 Channel注册到Selector上

总结一下Channel 的注册过程：

- 1、首先在AbstractBootstrap 的initAndRegister()方法中, 通过group().register(channel)，调用
  MultithreadEventLoopGroup 的register()方法

- 2、在MultithreadEventLoopGroup 的register()中，调用next()方法获取一个可用的SingleThreadEventLoop, 然后调用它的register()方法。

- 3、在SingleThreadEventLoop 的register()方法中，调用channel.unsafe().register(this, promise)方法来获取channel 的unsafe()底层操作对象，然后调用unsafe 的register()。

- 4、在AbstractUnsafe 的register()方法中, 调用register0()方法注册Channel 对象。

- 5、在AbstractUnsafe 的register0()方法中，调用AbstractNioChannel 的doRegister()方法。

- 6、AbstractNioChannel 的doRegister()方法通过javaChannel().register(eventLoop().selector, 0, this)将Channel对应的Java NIO 的SocketChannel 注册到一个eventLoop 的selector 中，并且将当前Channel 作为attachment 与SocketChannel 关联。

  

​        总的来说，Channel 注册过程所做的工作就是将Channel 与对应的EventLoop 关联，因此这也体现了，在Netty中，**每个Channel 都会关联一个特定的EventLoop**，并且这个Channel 中的所有IO 操作都是在这个EventLoop 中执行的；当关联好Channel 和EventLoop 后，会继续调用底层Java NIO 的SocketChannel 对象的register()方法，将底层Java NIO 的SocketChannel 注册到指定的selector 中。通过这两步，就完成了Netty 对Channel 的注册过程。

---



​	在前面的分析中，我们提到Channel 会在Bootstrap 的initAndRegister()中进行初始化，但是这个方法还会将初始化好的Channe 注册到NioEventLoop 的selector 中；接下来我们来分析一下Channel 注册的过程。

```java
final ChannelFuture initAndRegister() {
        Channel channel = null;
    
            channel = channelFactory.newChannel();
            init(channel);

        ChannelFuture regFuture = config().group().register(channel);
}   
```

​	  当Channel 初始化后，紧接着会调用`group().register()`方法来向`selector 注册Channel`。我们继续跟踪的话，会发现其调用链如下：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4igdazcf0j20rx03ogma.jpg)

AbstractChannel$AbstractUnsafe.register()方法中如下：

```java
public final void register(EventLoop eventLoop, final ChannelPromise promise) {
    
     AbstractChannel.this.eventLoop = eventLoop;
     register0(promise);
}
```

​	首先，将eventLoop 赋值给Channel 的eventLoop 属性，而我们知道这个eventLoop 对象其实是MultithreadEventLoopGroup 的next()方法获取的，根据我们前面的分析，我们可以确定next()方法返回的eventLoop对象是NioEventLoop 实例。register()方法接着调用了register0()方法：

```java
private void register0(ChannelPromise promise) {
	// 省略了非关键代码
    boolean firstRegistration = neverRegistered;
    doRegister();
    neverRegistered = false;
    registered = true;
    
    pipeline.invokeHandlerAddedIfNeeded();
    safeSetSuccess(promise);
    
    pipeline.fireChannelRegistered();
    
    if (isActive()) {
        if (firstRegistration) {
        pipeline.fireChannelActive();
        }
    }
}
```

register0()方法又调用了AbstractNioChannel 的doRegister()方法：

```java
protected void doRegister() throws Exception {
	// 省略了错误处理的代码
	selectionKey = javaChannel().register(eventLoop().selector, 0, this);
}
```

​		看到javaChannel()这个方法在前面我们已经知道了，它返回的是一个Java NIO 的SocketChannel 对象，这里我们将这个SocketChannel 注册到与eventLoop 关联的selector 上了。



## 1.4 Pipeline添加 Handler的过程

​	Netty 有一个强大和灵活之处就是基于Pipeline 的自定义handler 机制。基于此，我们可以像添加插件一样自由组合各种各样的handler 来完成业务逻辑。例如我们需要处理HTTP 数据，那么就可以在pipeline 前添加一个针对HTTP编、解码的Handler，然后接着添加我们自己的业务逻辑的handler，这样网络上的数据流就向通过一个管道一样, 从不同的handler 中流过并进行编、解码，最终在到达我们自定义的handler 中。



​	