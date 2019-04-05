ConcurrentHashMap jdk1.8



# 1.和HashMap差异

和HashMap的源码相似，只是在某些线程不安全的地方，做了对应的处理，hash值没有做处理

## 1.1 初始化容器的大小

​        只有一个线程对于容器进行初始化

​	 初始化容器大小采用CAS设计, 先进入的线程通过CAS将`sizeCtl`大小修改为 -1 ，然后初始化大小，后进入就不能操作这个值。

```java
while（）{
if ((sc = sizeCtl) < 0)
    Thread.yield(); // lost initialization race; just spin
else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
	。。。。
}
```



## 1.2 添加值

​	先通过`Unsafe类`以及下标志，将主内存最新的值获取出来

### 1） 原先容器中没有值

​		通过CAS将value设置到当前的bin中

### 2）原先容器中是链表或者红黑树

​	通过 `synchronized` 对当前的`bin`加锁，保证最小的加锁范围，只有数组中下标的一个值



## 1.3 resize扩容

有三个扩容操作：

- 容器的大小操作`threshold(12)`的大小，

- 在hash值为-1的时候，后来线程需要帮助扩容`helpTransfer`
- 链表的数节点数量超过8，本来要转为红黑树；判断当前容器的大小是否超过64，如果没有超过64就需要进行扩容，否则就转换为红黑树

采用多线程共同扩容，每一个线程分配16的bin进行数据迁移

```
if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
    stride = MIN_TRANSFER_STRIDE; 
```



# 2. jdk1.7和jdk1.8 的ConcurrentHashMap比较

|          | jdk1.7 的ConcurrentHashMap                                   | jdk1.8 的ConcurrentHashMap |
| -------- | ------------------------------------------------------------ | -------------------------- |
| 数据结构 | 数组 Segment(默认是16，所以最多支持16个线程读写)；Segment 内部是由 数组+链表 | 数组+链表+红黑树           |
| 加锁     | 分段锁，锁Segment                                            | 锁的是容器的一个bin        |
| 加锁方式 | 使用继承 ReentrantLock加锁                                   | 使用synchronize锁一个bin   |
| 扩容     | segment 数组不能扩容，扩容是 segment 数组某个位置内部的数组 HashEntry\[] 进行扩容，扩容后，容量为原来的 2 倍。 | 外部的数组进行扩容         |

