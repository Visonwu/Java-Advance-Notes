# 1.CAS锁机制-乐观锁

&emsp;&emsp;在jdk5以前，java基本只有靠synchronized关键字来保证同步，这会导致需要同步的对象被加上独占锁，也就是我们常说的悲观锁。

> 悲观锁存在一些问题，典型的如：
> - 1.在多线程竞争下，加锁和释放锁会导致比较多的上下文切换和调度延时，引起性能问题。
> - 2.一个线程持有锁会导致其他所有需要此锁的线程挂起

&emsp;&emsp;与悲观锁对应的是乐观锁，乐观锁在处理某些场景的时候有更好的表现，所谓乐观锁就是认为在并发场景下大部分时候都不会产生冲突，因此每次读写数据读不加锁而是假设没有冲突去完成某项操作，如果因为冲突失败就重试，直到成功为止。乐观锁用到的机制就是CAS。

# 2.CAS操作机制

&emsp;&emsp;CAS操作包含三个操作数——内存位置（V），预期原值（A）和新值（B）。如果内存位置的值与预期原值相匹配，那么处理器将会自动将该位置值更新为新值，否则，不做任何操作。无论哪种情况，它都会在CAS指令之前返回该位置的值。

> 通过以上定义我们知道CAS其实是有三个步骤的：
> - 1.读取内存中的值
> - 2.将读取的值和预期的值比较
> - 3.如果比较的结果符合预期，则写入新值

&emsp;&emsp;现在的CPU都支持“读-比较-修改”原子操作，也就是一个cpu在执行这个操作的时候，是绝对不会被其他线程中断的，但是多cpu环境就不一定了。可能在cpu1执行完比较操作准备修改的时候，另一块cpu2火速完成了一次“读-比较-修改”操作从而让内存中的值发生变化。而此时cpu1再写入明显就不对了。并且这个场景也并没有违背该命令的原子性。
# 3.使用volatile

&emsp;&emsp;解决以上问题的答案其实也很简单，那就是就是volatile。

&emsp;&emsp;在上面的场景中，解决问题的关键就是volatile的一致性，volitile的写操作是安全的，因为他在写入的时候lock声言会锁住cpu总线导致其他CPU不能访问内存（现在多用缓存一致性协议，处理器嗅探总线上传播的数据来判断自己缓存的值是否过期），所以，写入的时候若其他cpu修改了内存值，那么写入会失败。上面的问题中，由于cpu1的CAS指令执行一半的时候cpu2火速修改了变量的值，因此这就让该变量在所有cpu上的缓存失效，cpu1在进行写入操作的时候，也会发现自己的缓存失效，那么CAS操作就会失败（在java的automicinteger中，会不停的CAS直到成功）。所以即使是在多cpu多线程环境下，CAS机制依然能够保证线程的安全。

# 4.CAS存在的问题

&emsp;&emsp;但是CAS也并非完美的，CAS存在3个问题：
- ABA问题
- 循环时间长的话开销大（也就是说多冲突环境下乐观锁的重试消耗大）
- 以及只能保证一个共享变量的原子操作，本文就不再详细讨论了。

> ABA问题：
> &emsp;&emsp;比如说一个线程one从内存位置V中取出A，这时候另一个线程two也从内存中取出A，并且two进行了一些操作变成了B，然后two又将V位置的数据变成A，这时候线程one进行CAS操作发现内存中仍然是A，然后one操作成功。尽管线程one的CAS操作成功，但是不代表这个过程就是没有问题的。
> <br/>
>
> &emsp;&emsp;部分乐观锁的实现是通过版本号（version）的方式来解决ABA问题，乐观锁每次在执行数据的修改操作时，都会带上一个版本号，一旦版本号和数据的版本号一致就可以执行修改操作并对版本号执行+1操作，否则就执行失败。因为每次操作的版本号都会随之增加，所以不会出现ABA问题，因为版本号只会增加不会减少

# 5.CAS--Unsafe类

&emsp;&emsp;Unsafe类是在sun.misc包下，不属于Java标准。但是很多Java的基础类库，包括一些被广泛使用的高性能开发库都是基于Unsafe类开发的，比如Netty、Hadoop、Kafka等；Unsafe可认为是Java中留下的后门，提供了一些低层次操作，如直接内存访问、线程调度等)。这个类有很多方法。
举例：

```java
//比较并更新值
public final native boolean compareAndSwapObject(Object var1, long var2, Object var4,
Object var5)

//这个是一个native方法，第一个参数为需要改变的对象，第二个为偏移量,第三个参数为期待的值，第四个为更新后的值
//整个方法的作用是如果当前时刻的值等于预期值var4相等，则更新为新的期望值 var5，如果更新成功，则返回true，否则返回false；


//-----------------------------------------------------------
//获取字段的偏移量- 字段在当前类的内存中相对于该类首地址的偏移量
public native long objectFieldOffset(Field var1)

//一个Java对象可以看成是一段内存，每个字段都得按照一定的顺序放在这段内存里，通过这个方法可以准确地告诉你某个字段相对于对象的起始内存地址的字节偏移。
//用于在后面的compareAndSwapObject中，去根据偏移量找到对象在内存中的具体位置

```
----
```c
//compareAndSwapObject这个方法在C++的代码如下：
UNSAFE_ENTRY(jboolean, Unsafe_CompareAndSwapObject(JNIEnv *env, jobject unsafe, jobject
obj, jlong offset, jobject e_h, jobject x_h))
 UnsafeWrapper("Unsafe_CompareAndSwapObject");
 oop x = JNIHandles::resolve(x_h); // 新值
 oop e = JNIHandles::resolve(e_h); // 预期值
 oop p = JNIHandles::resolve(obj);
 HeapWord* addr = (HeapWord *)index_oop_from_field_offset_long(p, offset);// 在内存中的
具体位置
 oop res = oopDesc::atomic_compare_exchange_oop(x, addr, e, true);// 调用了另一个方法，实
际上就是通过cas操作来替换内存中的值是否成功
 jboolean success  = (res == e);  // 如果返回的res等于e，则判定满足compare条件（说明res应该为
内存中的当前值），但实际上会有ABA的问题
 if (success) // success为true时，说明此时已经交换成功（调用的是最底层的cmpxchg指令）
  update_barrier_set((void*)addr, x); // 每次Reference类型数据写操作时，都会产生一个Write
Barrier暂时中断操作，配合垃圾收集器
 return success;
UNSAFE_END
```