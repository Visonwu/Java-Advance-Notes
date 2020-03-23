# 1. 初探ChannelPipeline 构造

​		在Netty 中每个Channel 都有且仅有一个ChannelPipeline 与之对应，它们的组成关系如下：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4iw9a5xcxj20s103zwgd.jpg)

​		通过上图我们可以看到， 一个Channel 包含了一个ChannelPipeline ， 而ChannelPipeline 中又维护了一个由ChannelHandlerContext 组成的双向链表。这个链表的头是HeadContext，链表的尾是TailContext，并且每个
ChannelHandlerContext 中又关联着一个ChannelHandler。

在前我们已经知道了一个Channel 的初始化的基本过程，下面我们再回顾一下。下面的代码是
AbstractChannel 构造器：

```java
protected AbstractChannel(Channel parent, ChannelId id) {
    this.parent = parent;
    this.id = id;
    unsafe = newUnsafe();
    pipeline = newChannelPipeline();
}
```



​		AbstractChannel 有一个pipeline 字段，在构造器中会初始化它为DefaultChannelPipeline 的实例。这里的代码就印证了一点：每个Channel 都有一个ChannelPipeline。接着我们跟踪一下DefaultChannelPipeline 的初始化过程，首先进入到DefaultChannelPipeline 构造器中：

```java
protected DefaultChannelPipeline(Channel channel) {
    this.channel = ObjectUtil.checkNotNull(channel, "channel");
    succeededFuture = new SucceededChannelFuture(channel, null);
    voidPromise =  new VoidChannelPromise(channel, true);

    tail = new TailContext(this);
    head = new HeadContext(this);

    head.next = tail;
    tail.prev = head;
}
```

​		在DefaultChannelPipeline 构造器中， 首先将与之关联的Channel 保存到字段channel 中。然后实例化两个
ChannelHandlerContext：一个是HeadContext 实例head，另一个是TailContext 实例tail。接着将head 和tail 互相指向， 构成一个双向链表。

​		特别注意的是：我们在开始的示意图中head 和tail 并没有包含ChannelHandler，这是因为HeadContext 和TailContext继承于AbstractChannelHandlerContext 的同时也实现了ChannelHandler 接口了，因此它们有Context 和Handler的双重属性。		



# 2. HeadContext 和TailContext

​		HeadContext 实现了ChannelInboundHandler，而tail 实现了
ChannelOutboundHandler 接口，并且它们都实现了ChannelHandlerContext 接口, 因此可以说head 和tail 即是一个ChannelHandler，又是一个ChannelHandlerContext。接着看HeadContext 构造器中的代码：

```java
HeadContext(DefaultChannelPipeline pipeline) {
    super(pipeline, null, HEAD_NAME, false, true);
    unsafe = pipeline.channel().unsafe();
    setAddComplete();
}
```

​		它调用了父类AbstractChannelHandlerContext 的构造器，并传入参数inbound = false，outbound = true。而TailContext 的构造器与HeadContext 正好相反，它调用了父类AbstractChannelHandlerContext 的构造器，并传入参数inbound = true，outbound = false。也就是说header 是一个OutBoundHandler，而tail 是一个InboundHandler。



# 3. ChannelInitializer 的添加

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j5z9kl8jj20mu0aw74g.jpg)

​	前面我们已经分析过Channel 的组成，其中我们了解到，最开始的时候ChannelPipeline 中含有两个
ChannelHandlerContext（同时也是ChannelHandler），但是这个Pipeline 并不能实现什么特殊的功能，因为我们还没有给它添加自定义的ChannelHandler。通常来说，我们在初始化Bootstrap，会添加我们自定义的ChannelHandler，就以我们具体的客户端启动代码片段来举例：

```java
Bootstrap bootstrap = new Bootstrap();
bootstrap.group(group)
    .channel(NioSocketChannel.class)
    .option(ChannelOption.SO_KEEPALIVE, true)
    .handler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
        	ChannelPipeline pipeline = ch.pipeline();
      	  pipeline.addLast(new ChatClientHandler(nickName));
        }
    });
	
```

​		ChannelInitializer 实现了ChannelHandler，那么它是在什么时候添加到ChannelPipeline 中的呢？通过代码跟踪，我们发现它是在Bootstrap 的init()方法中添加到ChannelPipeline 中的，其代码如下：

```java
void init(Channel channel) throws Exception {
    ChannelPipeline p = channel.pipeline();
    p.addLast(config.handler());
    //略去N 句代码
}
```

​			从上面的代码可见，将handler()返回的ChannelHandler 添加到Pipeline 中，而handler()返回的其实就是我们在初始化Bootstrap 时通过handler()方法设置的ChannelInitializer 实例，因此这里就是将ChannelInitializer 插入到了Pipeline的末端。

```java
public final ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
    final AbstractChannelHandlerContext newCtx;
    
    synchronized (this) {
        checkMultiplicity(handler);
        //这里将handler封装为context
        newCtx = newContext(group, filterName(name, handler), handler);
        addLast0(newCtx);
    }
}        

//这里将context添加到tail前面一个位置
private void addLast0(AbstractChannelHandlerContext newCtx) {
    AbstractChannelHandlerContext prev = tail.prev;
    newCtx.prev = prev;
    newCtx.next = tail;
    prev.next = newCtx;
    tail.prev = newCtx;
}
```

​		从前面的类图中可以清楚地看到，ChannelInitializer 仅仅实现了ChannelInboundHandler 接口，因此这里实例化的DefaultChannelHandlerContext 的inbound = true，outbound = false。
​		兜了一圈，不就是inbound 和outbound 两个字段嘛，为什么需要这么大费周折地分析一番？其实这两个字段关系到pipeline 的事件的流向与分类，因此是十分关键的，

```java
至此， 我们暂时先记住一个结论：ChannelInitializer 所对应的DefaultChannelHandlerContext 的
inbound =true，outbound = false。
```



# 4.自定义ChannelHandler 的添加过程

​		前面我们已经分析了ChannelInitializer 是如何插入到Pipeline 中的，接下来就来探讨ChannelInitializer 在哪里被调用，ChannelInitializer 的作用以及我们自定义的ChannelHandler 是如何插入到Pipeline 中的。



**先简单了解Channel 的注册过程：**

- 1、首先在AbstractBootstrap 的initAndRegister()中，通过group().register(channel)，调用
  MultithreadEventLoopGroup 的register()方法。
- 2、在MultithreadEventLoopGroup 的register()中调用next()获取一个可用的SingleThreadEventLoop，然后调用它的register()方法。
- 3、在SingleThreadEventLoop 的register()方法中，通过channel.unsafe().register(this, promise)方法获取channel的unsafe()底层IO 操作对象，然后调用它的register()。
- 4、在AbstractUnsafe 的register()方法中，调用register0()方法注册Channel 对象。
- 5、在AbstractUnsafe 的register0()方法中，调用AbstractNioChannel 的doRegister()方法。
- 6、AbstractNioChannel 的doRegister()方法调用javaChannel().register(eventLoop().selector, 0, this)将Channel对应的Java NIO 的SockerChannel 对象注册到一个eventLoop 的Selector 中，并且将当前Channel 作为attachment。

​		

​       而我们自定义ChannelHandler 的添加过程，发生在AbstractUnsafe 的register0()方法中，在这个方法中调用了pipeline.fireChannelRegistered()方法，其代码实现如下：

```java
public final ChannelPipeline fireChannelRegistered() {
    AbstractChannelHandlerContext.invokeChannelRegistered(head);
    return this;
}
```

再看AbstractChannelHandlerContext 的invokeChannelRegistered()方法：

```java
static void invokeChannelRegistered(final AbstractChannelHandlerContext next) {
    EventExecutor executor = next.executor();
    if (executor.inEventLoop()) {
  	  next.invokeChannelRegistered();
    } else {
        executor.execute(new Runnable() {
            @Override
            public void run() {
            next.invokeChannelRegistered();
            }
        });
    }
}
```

​		很显然，这个代码会从head 开始遍历Pipeline 的双向链表，然后找到第一个属性inbound 为true 的
ChannelHandlerContext 实例。想起来了没？我们在前面分析ChannelInitializer 时，花了大量的篇幅来分析了inbound和outbound 属性，现在这里就用上了。回想一下，ChannelInitializer 实现ChannelInboudHandler，因此它所对应的ChannelHandlerContext 的inbound 属性就是true，因此这里返回就是ChannelInitializer 实例所对应的ChannelHandlerContext 对象，如下图所示：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j68thgmqj20p308n0w8.jpg)



当获取到inbound 的Context 后，就调用它的invokeChannelRegistered()方法：

```java
private void invokeChannelRegistered() {
    if (invokeHandler()) {
        try {
        	((ChannelInboundHandler) handler()).channelRegistered(this);
        } catch (Throwable t) {
        	notifyHandlerException(t);
        }
     } else {
       	 fireChannelRegistered();
    }
}
```

​		我们已经知道，每个ChannelHandler 都和一个ChannelHandlerContext 关联，我们可以通过ChannelHandlerContext获取到对应的ChannelHandler。因此很明显，这里handler()返回的对象其实就是一开始我们实例化的ChannelInitializer 对象，并接着调用了ChannelInitializer 的channelRegistered()方法。看到这里, 应该会觉得有点眼熟了。ChannelInitializer 的channelRegistered()这个方法我们在一开始的时候已经接触到了，但是我们并没有深入地分析这个方法的调用过程。看代码：

```java
public final void channelRegistered(ChannelHandlerContext ctx) throws Exception {
    if (initChannel(ctx)) {
    	  ctx.pipeline().fireChannelRegistered();
    } else {
   		 ctx.fireChannelRegistered();
    }
}

private boolean initChannel(ChannelHandlerContext ctx) throws Exception {
    if (initMap.putIfAbsent(ctx, Boolean.TRUE) == null) { // Guard against
        try {
        	initChannel((C) ctx.channel());
        } catch (Throwable cause) {
       	 	exceptionCaught(ctx, cause);
        } finally {
        	remove(ctx);
    	}
        return true;
	}
	return false;
}
```

​		initChannel((C) ctx.channel())这个方法我们也很熟悉，它就是我们在初始化Bootstrap 时，调用handler 方法传入的匿名内部类所实现的方法：

```java
.handler(new ChannelInitializer<SocketChannel>() {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new ChatClientHandler(nickName));
    }
});
```

​	因此，当调用这个方法之后, 我们自定义的ChannelHandler 就插入到了Pipeline，此时Pipeline 的状态如下图所示：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j6e8jh92j20s504c0v3.jpg)



​		当添加完成自定义的ChannelHandler 后，在finally 代码块会删除自定义的ChannelInitializer，也就是remove(ctx)最终调用ctx.pipeline().remove(this)，因此最后的Pipeline 的状态如下：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j6efu0vhj20s003vgnf.jpg)



## 4.1 ChannelHandler命令

```java
ChannelPipeline addLast(String name, ChannelHandler handler);
//第一个参数指定添加的handler 的名字(更准确地说是ChannelHandlerContext
```



​		第一个参数指定添加的handler 的名字(更准确地说是ChannelHandlerContext 的名字，说成handler 的名字更便于理解)。那么handler 的名字有什么用呢？如果我们不设置name，那么handler 默认的名字是怎样呢?带着这些疑问，我们依旧还是去源码中找到答案。还是以addLast()方法为例：

```java
public final ChannelPipeline addLast(String name, ChannelHandler handler) {
	return addLast(null, name, handler);
}
//然后调用
public final ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
    final AbstractChannelHandlerContext newCtx;
    synchronized (this) {
        checkMultiplicity(handler);
        newCtx = newContext(group, filterName(name, handler), handler);
        addLast0(newCtx);
        // 略去N 句代码
    }
    return this;
}
```

​		第一个参数被设置为null，我们不用关心它。第二参数就是这个handler 的名字。看代码可知，在添加一个handler之前，需要调用checkMultiplicity()方法来确定新添加的handler 名字是否与已添加的handler 名字重复。



## 4.2 ChannelHandler的默认命令

​	如果我们调用的是如下的addLast()方法：

```java
ChannelPipeline addLast(ChannelHandler... handlers);
```

那么Netty 就会调用generateName()方法为新添加的handler 自动生成一个默认的名字：

```java
private String filterName(String name, ChannelHandler handler) {
    if (name == null) {
    	return generateName(handler);
    }
    checkDuplicateName(name);
    return name;
}
private String generateName(ChannelHandler handler) {
    
    Map<Class<?>, String> cache = nameCaches.get();
    Class<?> handlerType = handler.getClass();
    String name = cache.get(handlerType);
    if (name == null) {
        name = generateName0(handlerType);
        cache.put(handlerType, name);
    }
    // 此处省略N 行代码
    return name;
}
```

​	而generateName()方法会接着调用generateName0()方法来实际生成一个新的handler 名字：

```java
private static String generateName0(Class<?> handlerType) {
	return StringUtil.simpleClassName(handlerType) + "#0";
}
```

​		默认命名的规则很简单，就是用反射获取handler 的simpleName 加上"#0"，因此我们自定义ChatClientHandler 的名字就是"ChatClientHandler#0"。



# 5.PipeLine的事件传播机制

​	前面章节中，我们已经知道AbstractChannelHandlerContext 中有inbound 和outbound 两个boolean 变量，分别用于标识Context 所对应的handler 的类型，即：

- 1、inbound 为true 是,表示其对应的ChannelHandler 是ChannelInboundHandler 的子类。

- 2、outbound 为true 时，表示对应的ChannelHandler 是ChannelOutboundHandler 的子类。
  这里大家肯定还有很多疑惑，不知道这两个字段到底有什么作用? 这还要从ChannelPipeline 的事件传播类型说起。

  

​       Netty 中的传播事件可以分为两种：**Inbound 事件和Outbound 事件**。如下是从Netty 官网针对这两个事件的说明：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j6lllkzpj20sh0l074y.jpg)



从上图可以看出，inbound 事件和outbound 事件的流向是不一样的:

- **inbound 事件的流行是从下至上**，并且inbound 的传递方式是通过调用相应的ChannelHandlerContext.fireIN_EVT()方法
  - 例如：ChannelHandlerContext的fireChannelRegistered()调用会发送一个ChannelRegistered 的inbound 给下一个ChannelHandlerContext
- **outbound刚好相反，是从上到下**。，而outbound 方法的的传递方式是通过调用ChannelHandlerContext.OUT_EVT()方法。
  - 例如：ChannelHandlerContext 的bind()方法调用时会发送一个bind 的outbound 事件给下一个ChannelHandlerContext。



## 5.1 Outbound 事件传播

​	**outbound** 类似于主动触发（发起请求的事件）;

​		Outbound 事件都是请求事件(request event)，即请求某件事情的发生，然后通过Outbound 事件进行通知。Outbound 事件的**传播方向是tail -> customContext -> head。**

ChannelOutboundHandler方法有：

```java
public interface ChannelOutboundHandler extends ChannelHandler {
	void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception;
    void connect(
    ChannelHandlerContext ctx, SocketAddress remoteAddress,
    SocketAddress localAddress, ChannelPromise promise) throws Exception;
    void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;
    void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;
    void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;
    void read(ChannelHandlerContext ctx) throws Exception;
    void flush(ChannelHandlerContext ctx) throws Exception;
}
```

```java
public class MyOutboundHandler extends ChannelOutboundHandlerAdapter {
    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) 
        throws Exception {
    System.out.println("客户端关闭");
    ctx.close(promise);
}
```



### 5.1.1连接事件 案例讲解

​	我们接下来以connect 事件为例，分析一下Outbound 事件的传播机制。

​		首先，当用户调用了Bootstrap 的connect()方法时，就会触发一个Connect 请求事件，此调用会触发如下调用链：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j758b9pnj20dz068wex.jpg)

继续跟踪，我们就发现AbstractChannel 的connect()其实由调用了DefaultChannelPipeline 的connect()方法：

```java
public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
		return pipeline.connect(remoteAddress, promise);
}
```

而pipeline.connect()方法的实现如下：

```java
public final ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
	return tail.connect(remoteAddress, promise);
}
```

可以看到，当outbound 事件(这里是connect 事件)传递到Pipeline 后，它其实是以tail 为起点开始传播的。
而tail.connect()其实调用的是AbstractChannelHandlerContext 的connect()方法：

```java
public ChannelFuture connect(
    final SocketAddress remoteAddress,
    final SocketAddress localAddress, final ChannelPromise promise) {
    //此处省略N 句
    final AbstractChannelHandlerContext next = findContextOutbound();
    EventExecutor executor = next.executor();
    next.invokeConnect(remoteAddress, localAddress, promise);
    //此处省略N 句
    return promise;
}
```

​	findContextOutbound()方法顾名思义，它的作用是以当前Context 为起点，向Pipeline 中的Context 双向链表的前端寻找第一个outbound 属性为true 的Context（即关联ChannelOutboundHandler 的Context），然后返回。
findContextOutbound()方法代码实现如下：

```java
private AbstractChannelHandlerContext findContextOutbound() {
    AbstractChannelHandlerContext ctx = this;
    do {
    	ctx = ctx.prev;
    } while (!ctx.outbound);
    return ctx;
}
```

​		当我们找到了一个outbound 的Context 后，就调用它的invokeConnect()方法，这个方法中会调用Context 其关联的ChannelHandler 的connect()方法：

```java
private void invokeConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
    if (invokeHandler()) {
        try {
        ((ChannelOutboundHandler) handler()).connect(this, remoteAddress, localAddress, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    } else {
    	connect(remoteAddress, localAddress, promise);
    }
}
```

​		如果用户没有重写ChannelHandler 的connect()方法，那么会调用ChannelOutboundHandlerAdapter 的connect()实现：

```java
public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
SocketAddress localAddress, ChannelPromise promise) throws Exception {
    
		ctx.connect(remoteAddress, localAddress, promise);
}
```

​		我们看到，ChannelOutboundHandlerAdapter 的connect()仅仅调用了ctx.connect()，而这个调用又回到了：Context.connect -> Connect.findContextOutbound -> next.invokeConnect -> handler.connect -> Context.connect这样的循环中，直到connect 事件传递到DefaultChannelPipeline 的双向链表的头节点，即head 中。为什么会传递到head 中呢？回想一下，head 实现了ChannelOutboundHandler，因此它的outbound 属性是true。因为head 本身既是一个ChannelHandlerContext，又实现了ChannelOutboundHandler 接口，因此当connect()消息传递到head 后，会将消息转递到对应的ChannelHandler 中处理，而head 的handler()方法返回的就是head 本身：

​	因此最终connect()事件是在head 中被处理。head 的connect()事件处理逻辑如下：

```java
public void connect(ChannelHandlerContext ctx,SocketAddress remoteAddress, SocketAddress localAddress,ChannelPromise promise) throws Exception {
    //最终调用这里
	unsafe.connect(remoteAddress, localAddress, promise);
}
```

到这里, 整个connect()请求事件就结束了。下图中描述了整个connect()请求事件的处理过程：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j7dq0nq9j20wg067acs.jpg)







## 5.2 Inbound 事件传播

​	**inbound** 类似于是事件回调（响应请求的事件），

Inbound 的特点是它传播方向是**head -> customContext -> tail。**

​	ChannelInboundHandler方法有:

```java
public interface ChannelInboundHandler extends ChannelHandler {
    void channelRegistered(ChannelHandlerContext ctx) throws Exception;
    void channelUnregistered(ChannelHandlerContext ctx) throws Exception;
    void channelActive(ChannelHandlerContext ctx) throws Exception;
    void channelInactive(ChannelHandlerContext ctx) throws Exception;
    void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception;
    void channelReadComplete(ChannelHandlerContext ctx) throws Exception;
    void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception;
    void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception;
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;
}
```

### 5.2.1 使用fireXXX方法继续传播事件

​	注意，如果我们捕获了一个事件，并且想让这个事件继续传递下去，那么需要调用Context 对应的传播方法**fireXXX**。

​		如下面的示例代码：MyInboundHandler 收到了一个channelActive 事件，它在处理后，如果希望将事件继续传播下去那么需要接着调用ctx.fireChannelActive()方法

```java
public class MyInboundHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    System.out.println("连接成功");
    ctx.fireChannelActive();
    }
}
```



### 5.1.2 激活事件 案例讲解

Inbound 事件和Outbound 事件的处理过程是类似的，只是传播方向不同。
   Inbound 事件是一个通知事件,即某件事已经发生了,然后通过Inbound 事件进行通知。Inbound 通常发生在Channel的状态的改变或IO 事件就绪。

Inbound 的特点是它传播方向是head -> customContext -> tail。

​	上面我们分析了connect()这个Outbound 事件,那么接着分析connect()事件后会发生什么Inbound 事件，并最终找到Outbound 和Inbound 事件之间的联系。当connect()这个Outbound 传播到unsafe 后，其实是在AbstractNioUnsafe的connect()方法中进行处理的：

```java
public final void connect(final SocketAddress remoteAddress,
	final SocketAddress localAddress, final ChannelPromise promise) {
    
    if (doConnect(remoteAddress, localAddress)) {
   		 fulfillConnectPromise(promise, wasActive);
    } else {
   		 ...
    }   
}
```

​	在AbstractNioUnsafe 的connect()方法中，首先调用doConnect()方法进行实际上的Socket 连接，当连接上后会调用fulfillConnectPromise()方法：

```java
private void fulfillConnectPromise(ChannelPromise promise, boolean wasActive) {
    if (!wasActive && active) {
  		  pipeline().fireChannelActive();
    }
}
```

​	我们看到,在fulfillConnectPromise()中，会通过调用pipeline().fireChannelActive()方法将通道激活的消息(即Socket 连接成功)发送出去。而这里，当调用pipeline.fireXXX 后，就是Inbound 事件的起点。因此当调用
pipeline().fireChannelActive()后，就产生了一个ChannelActive Inbound 事件，我们就从这里开始看看这个Inbound事件是怎么传播的？

```java
public final ChannelPipeline fireChannelActive() {
    AbstractChannelHandlerContext.invokeChannelActive(head);
    return this;
}
```

​	果然, 在fireChannelActive()方法中，调用的是head.invokeChannelActive()，因此可以证明Inbound 事件在Pipeline中传输的起点是head。那么,在head.invokeChannelActive()中又做了什么呢？

```java
static void invokeChannelActive(final AbstractChannelHandlerContext next) {
    EventExecutor executor = next.executor();
    if (executor.inEventLoop()) {
    		next.invokeChannelActive();
    } else {
    	executor.execute(new Runnable() {
            @Override
            public void run() {
            next.invokeChannelActive();
            }
        });
    }
}
```

上面的代码应该很熟悉了。回想一下在Outbound 事件(例如connect()事件)的传输过程中时，我们也有类似的操作：

- 1、首先调用findContextInbound()，从Pipeline 的双向链表中中找到第一个属性inbound 为true 的Context，然后将其返回。

- 2、调用Context 的invokeChannelActive()方法，invokeChannelActive()方法源码如下：

  - ```java
     private void invokeChannelActive() {
            if (invokeHandler()) {
                try {
                    ((ChannelInboundHandler) handler()).channelActive(this);
                } catch (Throwable t) {
                    notifyHandlerException(t);
                }
            } else {
                fireChannelActive();
            }
        }
    ```

    ​     这个方法和Outbound 的对应方法(如：invokeConnect()方法)如出一辙。与Outbound 一样，如果用户没有重写channelActive() 方法，那就会调用ChannelInboundHandlerAdapter 的channelActive()方法：

​        

​		同样地, 在ChannelInboundHandlerAdapter 的channelActive()中，仅仅调用了ctx.fireChannelActive()方法，因此就会进入Context.fireChannelActive() -> Connect.findContextInbound() ->nextContext.invokeChannelActive() ->nextHandler.channelActive() -> nextContext.fireChannelActive()这样的循环中。同理，tail 本身既实现了ChannelInboundHandler 接口，又实现了ChannelHandlerContext 接口，因此当channelActive()消息传递到tail 后，会将消息转递到对应的ChannelHandler 中处理，而tail 的handler()返回的就是tail 本身：

​		TailContext 的channelActive()方法是空的。如果大家自行查看TailContext 的Inbound 处理方法时就会发现，它们的实现都是空的。可见，如果是Inbound,当用户没有实现自定义的处理器时，那么默认是不处理的。下图描述了Inbound事件的传输过程：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j7uxi332j20wv04e76p.jpg)



## 5.3 事件总结

### 5.3.1 Outbound 事件总结
1、Outbound 事件是请求事件（由connect()发起一个请求，并最终由unsafe 处理这个请求）。

2、Outbound 事件的发起者是Channel。

3、Outbound 事件的处理者是unsafe。

4、Outbound 事件在Pipeline 中的**传输方向是tail -> head**，这里指ContextHandler

5、在ChannelHandler 中处理事件时，如果这个Handler 不是最后一个Handler，则需要调用ctx 的方法（如：
ctx.connect()方法)将此事件继续传播下去。如果不这样做，那么此事件的传播会提前终止。

6、Outbound 事件流：Context.OUT_EVT() -> Connect.findContextOutbound() -> nextContext.invokeOUT_EVT()-> nextHandler.OUT_EVT() -> nextContext.OUT_EVT()



### 5.3.2 Inbound 事件总结
1、Inbound 事件是通知事件，当某件事情已经就绪后，通知上层。

2、Inbound 事件发起者是unsafe。

3、Inbound 事件的处理者是Channel，如果用户没有实现自定义的处理方法，那么Inbound 事件默认的处理者是
TailContext，并且其处理方法是空实现。

4、Inbound 事件在Pipeline 中传输方向是head -> tail。

5、在ChannelHandler 中处理事件时，如果这个Handler 不是最后一个Handler，则需要调用ctx.fireIN_EVT()事
件（如：ctx.fireChannelActive()方法）将此事件继续传播下去。如果不这样做，那么此事件的传播会提前终止。

6、Outbound 事件流：Context.fireIN_EVT() -> Connect.findContextInbound() -> nextContext.invokeIN_EVT() ->nextHandler.IN_EVT() -> nextContext.fireIN_EVT().outbound 和inbound 事件设计上十分相似，并且Context 与Handler 直接的调用关系也容易混淆，因此我们在阅读这里的源码时，需要特别的注意。



# 6.ChannelHandler的其它

## 6.1 ChannelHandlerContext

​		每个ChannelHandler 被添加到ChannelPipeline 后，都会创建一个ChannelHandlerContext 并与之创建的
ChannelHandler 关联绑定。ChannelHandlerContext 允许ChannelHandler 与其他的ChannelHandler 实现进行交互。
ChannelHandlerContext 不会改变添加到其中的ChannelHandler，因此它是安全的。下图描述了
ChannelHandlerContext、ChannelHandler、ChannelPipeline 的关系：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j81n8ijjj20l408mwjv.jpg)



## 6.2 Channel 的生命周期

​		Netty 有一个简单但强大的状态模型，并完美映射到ChannelInboundHandler 的各个方法。下面是Channel 生命周期中四个不同的状态：

**状态描述**

- **channelUnregistered()** :Channel已创建，还未注册到一个EventLoop上
- **channelRegistered()**: Channel已经注册到一个EventLoop上
- **channelActive()** :Channel是活跃状态（连接到某个远端），可以收发数据
- **channelInactive()**: Channel未连接到远端



一个Channel 正常的生命周期如下图所示。随着状态发生变化相应的事件产生。这些事件被转发到ChannelPipeline中的ChannelHandler 来触发相应的操作。

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j83p973sj20du06yq45.jpg)



## 6.3 ChannelHandler 常用的API

先看一下Netty 中整个Handler 体系的类关系图：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j86rdxyrj20te0al74h.jpg)

​		

​		Netty 定义了良好的类型层次结构来表示不同的处理程序类型，所有的类型的父类是ChannelHandler。ChannelHandler提供了在其生命周期内添加或从ChannelPipeline 中删除的方法。

**状态描述**

- **handlerAdded()**: ChannelHandler添加到实际上下文中准备处理事件
- **handlerRemoved():** 将ChannelHandler从实际上下文中删除，不再处理事件
- **exceptionCaught()** :处理抛出的异常



​       Netty 还提供了一个实现了ChannelHandler 的抽象类ChannelHandlerAdapter。ChannelHandlerAdapter 实现了父类的所有方法，基本上就是传递事件到ChannelPipeline 中的下一个ChannelHandler 直到结束。我们也可以直接继承于ChannelHandlerAdapter，然后重写里面的方法。

### 6.3.1 ChannelInboundHandler

​	ChannelInboundHandler 提供了一些方法再接收数据或Channel 状态改变时被调用。下面是**ChannelInboundHandler的一些方法：**

- channelRegistered(): ChannelHandlerContext的Channel被注册到EventLoop
- channelUnregistered(): ChannelHandlerContext的Channel从EventLoop中注销
- channelActive(): ChannelHandlerContext的Channel已激活

- channelInactive(): ChannelHanderContxt的Channel结束生命周期
- channelRead(): 从当前Channel的对端读取消息
- channelReadComplete(): 消息读取完成后执行
- userEventTriggered(): 一个用户事件被触发
- channelWritabilityChanged(): 改变通道的可写状态，可以使用Channel.isWritable()检查
- exceptionCaught(): 重写父类ChannelHandler的方法，处理异常



​		Netty 提供了一个实现了ChannelInboundHandler 接口并继承ChannelHandlerAdapter 的类：
ChannelInboundHandlerAdapter。ChannelInboundHandlerAdapter 实现了ChannelInboundHandler 的所有方法，作用就是处理消息并将消息转发到ChannelPipeline 中的下一个ChannelHandler。

`ChannelInboundHandlerAdapter`的channelRead() 方法处理完消息**后不会自动释放消息**， 若想自动释放收到的消息， 可以使用SimpleChannelInboundHandler，看下面的代码：

```java
public class UnreleaseHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    //手动释放消息
    ReferenceCountUtil.release(msg);
    }
}
```

SimpleChannelInboundHandler 会自动释放消息：

```java
public class ReleaseHandler extends SimpleChannelInboundHandler<Object> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    //不需要手动释放
    }
}
```

ChannelInitializer 用来初始化ChannelHandler，将自定义的各种ChannelHandler 添加到ChannelPipeline 中。

