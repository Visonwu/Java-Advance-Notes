​         我们在进行数据传送的时候需要用到缓冲区，常用的就是NIO的Buffer,实际上7种类型（除了Boolean）都有相关的实现，但是NIO编程常用的还是ByteBuffer。但是ByteBuffer有position，limit，capacity位置，每次读写还需要flip和clear处理相对比较麻烦，所以Netty自己使用ByteBuf来替换NIO的缓冲。



## 1.ByteBuf的原理

ByteBuf通过两个指针来协助缓冲区的读写操作，读操作用readerIndex，写操作用writeIndex；



​         readerIndex和writerIndex的取值一开始都是0，随着数据的写入writerIndex会增加,读取数据会使readerIndex增加，但是它不会超过writerIndex.在读取之后，0~readerIndex的就被视为discard的，调用discardReadBytes方法，可以释放这部分空间，它的作用类似ByteBuffer的compact方法。
ReaderIndex和writerIndex 之间的数据是可读取的，等价于ByteBuffer position和limit之间的数据。WriterIndex 和capacity之间的空间是可写的，等价于ByteBuffer limit 和capacity之间的可用空间。

​      由于写操作不修改readerIndex指针，读操作不修改writerIndex指针，因此读写之间不再需要调整位置指针，这极大地简化了缓冲区的读写操作，避免了由于遗漏或者不熟悉flip()操作导致的功能异常。

具体操作流程如下图：

![YbkucQ.png](https://s1.ax1x.com/2020/05/21/YbkucQ.png)



## 2.ByteBuf的功能

### 2.1 顺序读read操作

​	byteBuf的read操作ByteBuffer的get操作，有许多可以查看相关api。

比如readBytes()



### 2.2 顺序写write操作

  	byteBuf的write操作ByteBuffer的put操作，有许多可以查看相关api。

比如writeBytes()



### 2.3 readerIndex和writerIndex

​         Netty提供了两个指针变量用于支持顺序读取和写入操作:readerIndex用于标识读取索引，writerIndex 用于标识写入索引。两个位置指针将ByteBuf缓冲区分割成三个区域.

​      调用ByteBuf 的read操作时，从readerIndex处开始读取。readerIndex 到writerIndex之间的空间为可读的字节缓冲区;从writerIndex到cappacitcity 之间为可写的字节缓冲区; 0到readerIndex之间是已经读取过的缓冲区，可以调用discardReadBytes操作来重用这部分空间，以节约内存，防止ByteBuf的动态扩张。这在私有协议栈消息解码的时候非常有用，因为TCP底层可能粘包，几百个整包消息被TCP粘包后作为一个整包发送，这样，通过discardReadBytes操作可以重用之前已经解码过的缓冲区，这样就可以防止接收缓冲区因为容量不足导致的扩张。

### 2.4 Discardable bytes

​	清理掉已经读取到的缓冲区。

### 2.5 ReadableBytes 和WritableBytes

​	可以查看可读字节以及可写的字节数

### 2.6 Clear，SkipBytes操作

 clear 会将readerIndex和writerIndex全部设置为0,内容不会清除

SkipBytes跳过不需要读取的字节或者字节数组。

### 2.7 Mark和Reset操作

​	如果某个时候我们需要对于某一个操作做回滚，那么mark和reset就是这样的实现的，他们只是改变索引位置，并不会  改变缓冲区内容。通过mark操作将当前指针备份到mark中，当调用reset操作恢复备份的mark值。

比如：

```
markReaderIndex
resetReaderIndex

markWriterIndex
resetWriterIndex
```



### 2.8 查找操作

我们可以通过某一个字符定位他的位置做到定位操作。

比如indexOf,bytesBefore,forEachByte等。



### 2.9 Derived  buffers 新建视图

类似数据库视图，可以创建byteBuf的视图或者复制byteBuf

- 1)duplicate:返回当前ByteBuf的复制对象，复制的只是指针，内容还是原来的内容，如果原来的内容变了，之后的byteBuf获得的内容也是变化后的。
- 2）copy：完全复制新的ByteBuf，内容和索引都是完全互不影响
- 3）slice：返回当前可读缓冲区即readerIndex到writerIndex，返回后的ByteBuf和原来的共享内容，但是指针各种维护



### 2.10 转换为标准的ByteBuffer

​	将byteBuf转换为对应的ByteBuffer



### 2.11 随机读写（set/get）

​	除了顺序的read和write外，也支持随机读写，不过不同的地方就是顺序读写可以做到自动扩容，随机读写会对索引和长度进行校验，不会动态扩展缓冲区，所以需要注意字节长度，否则会抛出越界的错误。





## 3.ByteBuf的辅助类



### 3.1 ByteBufHolder

​		ByteBufHolder是ByteBuf的容器，在Netty中，它非常有用，例如HTTP协议的请求消息和应答消息都可以携带消息体，这个消息体在NIOByteBuffer中就是个ByteBuffer对象，在Netty中就是ByteBuf对象。由于不同的协议消息体可以包含不同的协议字段和功能，因此，需要对ByteBuf进行包装和抽象，不同的子类可以有不同的实现。



​		为了满足这些定制化的需求，Netty 抽象出了ByteBufHolder 对象，它包含了一个ByteBuf,另外还提供了一些其他实用的方法，使用者继承ByteBufHolder接口后可以按需封装自己的实现。



### 3.2 ByteBufferAllocator

​		ByteBufAllocator是字节缓冲区分配器，按照Netty的缓冲区实现不同，共有两种不同的分配器:基于内存池的字节缓冲区分配器和普通的字节缓冲区分配器。

- UnpooledByteBufAllocator
- PooledByteBufAllocator

他们分别可以创建堆内缓冲和非堆内缓冲





### 3.3 CompositeByteBuf

​		CompositeByteBuf允许将多个ByteBuf的实例组装到一起，形成-一个统- -的视图，有点类似于数据库将多个表的字段组装到一起统一用视图展示。



### 3.4 ByteBufUtil

ByteBufUtil是一个非常有用的工具类，它提供了一系列静态方法用于操作ByteBuf对象.

比较有用的比如encodeString,decodeString,hexDump等方法。



## 4. 内存分配和回收

从内存分配的角度看，ByteBuf 可以分为两类：

​		(1)堆内存(HeapByteBuf) 字节缓冲区:特点是内存的分配和回收速度快，可以被
JVM自动回收;缺点就是如果进行Socket 的I/O 读写，需要额外做一次内存复制，将堆
内存对应的缓冲区复制到内核Channel中，性能会有一定程度的下降。

​		(2)直接内存( DirectByteBuf) 字节缓冲区:非堆内存，它在堆外进行内存分配，相
比于堆内存，它的分配和回收速度会慢--些，但是将它写入或者从SocketChannel中读取
时，由于少了一次内存复制，速度比堆内存快。

​		正是因为各有利弊，所以Netty提供了多种ByteBuf供开发者使用，经验表明，ByteBuf
的最佳实践是在I/O通信线程的读写缓冲区使用DirectByteBuf,后端业务消息的编解码模
块使用HeapByteBuf,这样组合可以达到性能最优。



从内存回收角度看，ByteBuf也分为两类:基于对象池的ByteBuf和普通ByteBuf。两
者的主要区别就是基于对象池的ByteBuf可以重用ByteBuf对象，它自己维护了一个内存
池，可以循环利用创建的ByteBuf,提升内存的使用效率，降低由于高负载导致的频繁GC。
测试表明使用内存池后的Netty在高负载、大并发的冲击下内存和GC更加平稳。
尽管推荐使用基于内存池的ByteBuf,但是内存池的管理和维护更加复杂，使用起来
也需要更加谨慎，因此，Netty提供了灵活的策略供使用者来做选择。