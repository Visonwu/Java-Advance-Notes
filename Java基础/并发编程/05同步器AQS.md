
&emsp;&emsp;AQS(AbstractQueuedSynchronizer),AbstractQueuedSynchronizer提供了一个FIFO队列，可以看做是一个用来实现锁以及其他需要同步功能的框架。这里简称该类为AQS。AQS的使用依靠继承来完成，子类通过继承自AQS并实现所需的方法来管理同步状态。例如常见的`ReentrantLock`，`CountDownLatch`等

# 1.功能分类

&emsp;&emsp;从使用上来说，AQS的功能可以分为两种：**独占和共享**。
- 独占锁模式下，每次只能有一个线程持有锁，ReentrantLock就是以独占方式实现的互斥锁
- 共享锁模式下，允许多个线程同时获取锁，并发访问共享资源，比如ReentrantReadWriteLock中的ReadLock，Semaphore，CountDownLatch等

> &emsp;&emsp;很显然，独占锁是一种悲观保守的加锁策略，它限制了读/读冲突，如果某个只读线程获取锁，则其他读线程都只能等待，这种情况下就限制了不必要的并发性，因为读操作并不会影响数据的一致性。共享锁则是一种乐观锁，它放宽了加锁策略，允许多个执行读操作的线程同时访问共享资源。类似数据库中的独占和共享，下片会分享ReentrantReadWriteLock的用法。

# 2.内部实现

## 2.1 锁队列实现

&emsp;&emsp;同步器依赖内部的同步队列（一个FIFO双向队列）来完成同步状态的管理，当前线程获取同步状态失败时，同步器会将当前线程以及等待状态等信息构造成为一个节（Node）并将其加入同步队列，同时会阻塞当前线程，当同步状态释放时，会把首节点中的线程唤醒，使其再次尝试获取同步状态。

**Node节点的属性**如下：

```java
static final class Node {
	  int waitStatus; //表示节点的状态，包含cancelled（取消）；condition 表示节点在等待condition
	也就是在condition队列中
	  Node prev; //前继节点
	  Node next; //后继节点
	  Node nextWaiter; //存储在condition队列中的后继节点
	  Thread thread; //当前线程
}
```
**AQS的数据结构**
&emsp;&emsp;AQS类底层的数据结构是使用双向链表，是队列的一种实现。包括一个head节点和一个tail节点，分别表示头结点和尾节点，其中头结点不存储Thread，仅保存next结点的引用。如下所示：
![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5vxzv86agj20i4054mxi.jpg)

**调用CAS实现原子操作**
&emsp;&emsp;当一个线程成功地获取了同步状态（或者锁），其他线程将无法获取到同步状态，转而被构造成为节点并加入到同步队列中，而这个加入队列的过程必须要保证线程安全，因此同步器提供了一个基于CAS的设置尾节点的方法compareAndSetTail(Node expect,Nodeupdate)，它需要传递当前线程“认为”的尾节点和当前节点，只有设置成功后，当前节点才正式与之前的尾节点建立关联。
![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5vy046k7kj20he073dhy.jpg)

&emsp;&emsp;同步队列遵循FIFO，首节点是获取同步状态成功的节点，首节点的线程在释放同步状态时，将会唤醒后继节点，而后继节点将会在获取同步状态成功时将自己设置为首节点。
![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5vy09w00dj20il054q3s.jpg)

&emsp;&emsp;设置首节点是通过获取同步状态成功的线程来完成的，由于只有一个线程能够成功获取到同步状态，因此设置头节点的方法并不需要使用CAS来保证，它只需要将首节点设置成为原首节点的后继节点并断开原首节点的next引用即可。

## 2.2 等待队列

AQS使用含有`ConditionObject`内部类，用来保存等待和唤醒操作，类似Synchronized关键字，

例如：ReentrantLock中通过newCondition()获取这个condition，

- 通过condition.await()方法就会释放当前锁并且防止在ConditionObject链表队列中，

- 通过`condition.signal()`方法会将ConditionObject链表队列中的第一个数据放入当上面的同步队列中争抢锁。

- condition.signalAll()则是通过循环的方式将所有ConditionObject链表队列放入同步队里俄中

# 3.AQS使用方法

## 3.1 继承

&emsp;&emsp;同步器组件的设计是基于模板方法模式的，使用者继承AbstractQueuedSynchronizer并重写指定的方法。（这些重写方法很简单，无非是对于共享资源state的获取和释放）例如：在ReentrantLock类中定义静态内部类实现继承AQS类，来重写模板方法:

```java
public class ReentrantLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572414699L;
 
    private final Sync sync;
 
    abstract static class Sync extends AbstractQueuedSynchronizer {
    ...
    }
...

```

## 3.2 修改同步状态的方法

&emsp;&emsp;重写同步器指定方法需要使用下面三个方法用来修改同步状态重写同步器指定方法需要使用下面三个方法用来修改同步状态:
state 为0表示没有获取到锁，state>1表示获取锁的次数，这个也是重入锁的记录次数。
```java
getState();    // 获取当前同步状态
setState();    //设置当前同步状态
compareAndSetState(); //使用 CAS设置当前状态，该方法能够保证状态设置的原子性
```

## 3.3 同步器可以重写的方法

子类可以重写AQS提供的方法）如下：
![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5vy0gc6fnj20o207jdif.jpg)

## 3.4 调用AQS提供的方法 

现自定义同步组件，可以调用AQS提供的实现方法，如下：
![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5vy0npmauj20ob0egte3.jpg)

## 3.5 设计思想

&emsp;&emsp;对于使用者来讲，我们无需关心获取资源失败，线程排队，线程阻塞/唤醒等一系列复杂的实现，这些都在AQS中为我们处理好了。我们只需要负责好自己的那个环节就好，也就是获取/释放共享资源state的姿势。很经典的模板方法设计模式的应用，AQS为我们定义好顶级逻辑的骨架，并提取出公用的线程入队列/出队列，阻塞/唤醒等一系列复杂逻辑的实现，将部分简单的可由使用者决定的操作逻辑延迟到子类中去实现即可。

总结：我们自定义同步器组件:

- 1.只需要聚合一个继承了AQS的子类，

- 2.子类可以重写AQS相关的方法

- 3.自定义同步器组件可以调用AQS的模板方法，(这些模板方法实际是会调用你重写的方法)，您调用AQS模板方法就可以实现同步的相关功能。