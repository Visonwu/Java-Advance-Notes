

# 1.EventLoopGroup 与Reactor

​		一个Netty 程序启动时，至少要指定一个EventLoopGroup(如果使用到的是NIO，通常是指NioEventLoopGroup)，那么，这个NioEventLoopGroup 在Netty 中到底扮演着什么角色呢? 我们知道，Netty是Reactor 模型的一个实现，我们就从Reactor 的线程模型开始。

## 1.1 浅谈Reactor模型

​	Reactor 的线程模型有三种:单线程模型、多线程模型、主从多线程模型。

### 1.1.1 单线程模型

首先来看一下单线程模型：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j0dkxmg0j20ku06741l.jpg)



​	  所谓单线程, 即Acceptor 处理和handler 处理都在同一个线程中处理。这个模型的坏处显而易见：当其中某个Handler阻塞时, 会导致其他所有的Client 的Handler 都得不到执行，并且更严重的是，Handler 的阻塞也会导致整个服务不能接收新的Client 请求(因为Acceptor 也被阻塞了)。因为有这么多的缺陷，因此单线程Reactor 模型应用场景比较少。

### 1.1.2 Reactor 多线程模型

​			那么，什么是多线程模型呢? Reactor 的多线程模型与单线程模型的区别就是Acceptor 是一个单独的线程处理，并且有一组特定的NIO 线程来负责各个客户端连接的IO 操作如下图所示

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j0fjmk6aj20mt06un0v.jpg)

Reactor 多线程模型有如下特点:

- 1、有专门一个线程，即Acceptor 线程用于监听客户端的TCP 连接请求。
- 2、客户端连接的IO 操作都由一个特定的NIO 线程池负责.每个客户端连接都与一个特定的NIO 线程绑定,因此在这个客户端连接中的所有IO 操作都是在同一个线程中完成的。
- 3、客户端连接有很多，但是NIO 线程数是比较少的，因此一个NIO 线程可以同时绑定到多个客户端连接中。
  接下来我们再来看一下Reactor 的主从多线程模型。一般情况下, Reactor 的多线程模式已经可以很好的工作了

### 1.1.3 Reactor 的主从多线程

​        但是我们想象一个这样的场景：如果我们的服务器需要同时处理大量的客户端连接请求或我们需要在客户端连接时，进行一些权限的校验，那么单线程的Acceptor 很有可能就处理不过来，造成了大量的客户端不能连接到服务器。Reactor 的主从多线程模型就是在这样的情况下提出来的，它的特点是：服务器端接收客户端的连接请求不再是一个线程，而是由一个独立的线程池组成。

其线程模型如下图所示：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j0ila8hoj20ix04zmz6.jpg)

​		可以看到，Reactor 的主从多线程模型和Reactor 多线程模型很类似，只不过Reactor 的主从多线程模型的Acceptor使用了线程池来处理大量的客户端请求



## 1.2 EventLoopGroup和Reactor关联

### 1.2.1 单线程模型

​			因此当传入一个group 时，那么bossGroup 和workerGroup 就是同一个NioEventLoopGroup 了。这时，因为bossGroup 和workerGroup 就是同一个NioEventLoopGroup，并且这个NioEventLoopGroup 线程池数量只设置了1个线程，也就是说Netty 中的Acceptor 和后续的所有客户端连接的IO 操作都是在一个线程中处理的。那么对应到Reactor 的线程模型中，我们这样设置NioEventLoopGroup 时，就相当于Reactor 的单线程模型。

```java
EventLoopGroup bossGroup = new NioEventLoopGroup(1);
ServerBootstrap server = new ServerBootstrap();
server.group(bossGroup);

//实际调用的是如下，如果传入的是一个group，那么bossgroup和workgroup使用一个，并且
public ServerBootstrap group(EventLoopGroup group) {
	return group(group, group);
}
```

​		

### 1.2.2 多线程模型

​      我们只需要将bossGroup 的参数就设置为大于1 的数，其实就是Reactor 多线程模型。

```java
EventLoopGroup bossGroup = new NioEventLoopGroup(128);
ServerBootstrap server = new ServerBootstrap();
server.group(bossGroup);
```



### 1.2.3 主从线程模型

​		bossGroup 为主线程，而workerGroup 中的线程是CPU 核心数乘以2，因此对应的到Reactor 线程模型中，我们知道, 这样设置的NioEventLoopGroup 其实就是Reactor 主从多线程模型。

```java
EventLoopGroup bossGroup = new NioEventLoopGroup();
EventLoopGroup workerGroup = new NioEventLoopGroup();
ServerBootstrap b = new ServerBootstrap();
b.group(bossGroup, workerGroup);
```





# 2. EventLoopGroup 的实例化

​	首先，我们先纵览一下EventLoopGroup 的类结构图，如下图所示：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j0tp8ca2j20g60i3dg4.jpg)

**EventLoopGroup 初始化的步骤步骤如下：**

- 1、EventLoopGroup(其实是MultithreadEventExecutorGroup)内部维护一个类为EventExecutor children 数组，其大小是nThreads，这样就初始化了一个线程池。
- 2、如果我们在实例化NioEventLoopGroup 时，如果指定线程池大小，则nThreads 就是指定的值，否则是CPU核数* 2。
- 3、在MultithreadEventExecutorGroup 中会调用newChild()抽象方法来初始化children 数组.
- 4、抽象方法newChild()实际是在NioEventLoopGroup 中实现的，由它返回一个NioEventLoop 实例。
- 5、初始化NioEventLoop 主要属性：
  - provider：在NioEventLoopGroup 构造器中通过SelectorProvider 的provider()方法获取SelectorProvider。
  - selector：在NioEventLoop 构造器中调用selector = provider.openSelector()方法获取Selector 对象。



# 3.任务执行者EventLoop

​	NioEventLoop 继承自SingleThreadEventLoop，而SingleThreadEventLoop 又继承SingleThreadEventExecutor。而SingleThreadEventExecutor 是Netty 中对本地线程的抽象，它内部有一个Thread thread 属性，存储了一个本地Java线程。因此我们可以简单地认为，一个NioEventLoop 其实就是和一个特定的线程绑定，并且在其生命周期内，绑定的线程都不会再改变。

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4j1o2mh23j20le0ipmxm.jpg)

​      NioEventLoop 的类层次结构图还是有些复杂的，不过我们只需要关注几个重要点即可。首先来看NioEventLoop 的继承链：NioEventLoop->SingleThreadEventLoop->SingleThreadEventExecutor->AbstractScheduledEventExecutor。

​         在AbstractScheduledEventExecutor 中, Netty 实现了NioEventLoop 的schedule 功能，即我们可以通过调用一个NioEventLoop 实例的schedule 方法来运行一些定时任务。而在SingleThreadEventLoop 中，又实现了任务队列的功能，通过它，我们可以调用一个NioEventLoop 实例的execute()方法来向任务队列中添加一个task,并由NioEventLoop进行调度执行。

## 3.1 NioEventLoop任务

**通常来说，NioEventLoop 负责执行两个任务：**

- 第一个任务是作为IO 线程，执行与Channel 相关的IO 操作，包括调用Selector 等待就绪的IO 事件、读写数据与数据的处理等；

- 第二个任务是作为任务队列，执行taskQueue 中的任务，例如用户调用eventLoop.schedule 提交的定时任务也是这个线程执行的。



## 3.2 EventLoop和Channel的关联

​	在Netty 中, **每个Channel 都有且仅有一个EventLoop 与之关联**, 它们的关联过程如下：


​		当调用AbstractChannel$AbstractUnsafe.register()方法后，就完成了Channel 和EventLoop的关联。register()方法的具体实现如下：

```java
public final void register(EventLoop eventLoop, final ChannelPromise promise) {
    // 删除条件检查
    //这里赋值
    AbstractChannel.this.eventLoop = eventLoop;
    
    if (eventLoop.inEventLoop()) {
    	register0(promise);
    } else {
        try {
            eventLoop.execute(new Runnable() {
                @Override
                public void run() {
                    register0(promise);
                }
            });
        } catch (Throwable t) {
        // 删除catch 块内容
        }
    }
}
```



## 3.3 EventLoop 的启动

​	NioEventLoop 本身就是一个SingleThreadEventExecutor，因此NioEventLoop 的启动，其实就是NioEventLoop 所绑定的本地Java 线程的启动。

​		按照这个思路，我们只需要找到在哪里调用了SingleThreadEventExecutor 中thread 字段的start()方法就可以知道是在哪里启动的这个线程了。从前面章节的分析中， 其实我们已经清楚： thread.start() 被封装到了SingleThreadEventExecutor.startThread()方法中，来看代码：

```java
private void startThread() {
        if (state == ST_NOT_STARTED) {//第一次进入时才会启动，然后将当前状态设置为ST_STARTED
            if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) {
                doStartThread(); //这里启动
            }
        }
    }
```

​		我们有提到到在注册channel 的过程中， 会在AbstractChannel$AbstractUnsafe 的register()中调用eventLoop.execute()方法，在EventLoop 中进行Channel 注册代码的执行，AbstractChannel$AbstractUnsafe 的register()部分代码如下

```java
public final void register(EventLoop eventLoop, final ChannelPromise promise) {
    // 删除判断
    AbstractChannel.this.eventLoop = eventLoop;
    if (eventLoop.inEventLoop()) {
   		 register0(promise);
    } else {
        try {
            eventLoop.execute(new Runnable() {
            
            @Override
            public void run() {
                register0(promise);
                }
            });
        } catch (Throwable t) {
        	// 删除异常处理代码
        }
    }
}
```

​		

​         很显然，一路从Bootstrap 的bind()方法跟踪到AbstractChannel$AbstractUnsafe 的register()方法，整个代码都是在主线程中运行的，因此上面的eventLoop.inEventLoop()返回为false，于是进入到else 分支，在这个分支中调用了eventLoop.execute()方法，而NioEventLoop 没有实现execute()方法，因此调用的是`SingleThreadEventExecutor` 的execute()方法.

```java
public void execute(Runnable task) {
    // 条件判断
    boolean inEventLoop = inEventLoop();
    if (inEventLoop) {
    	addTask(task);
    } else {
   	 	startThread();
    	addTask(task);
        if (isShutdown() && removeTask(task)) {
             reject();
        }
   		if (!addTaskWakesUp && wakesUpForTask(task)) {
    	wakeup(inEventLoop);
        }
    }
}
```

​		我们已经分析过了， inEventLoop == false ， 因此执行到else 分支， 在这里就调用startThread() 方法来启动SingleThreadEventExecutor 内部关联的Java 本地线程了。



​	总结一句话：**当EventLoop 的execute()第一次被调用时，就会触发startThread()方法的调用，进而导致EventLoop所对应的Java 本地线程启动**。