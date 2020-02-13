ConcurrentHashMap jdk1.8



# 1.ConcurrentHashMap

## 1.1 初始化容器的大小

- 只有一个线程对于容器进行初始化

- 初始化后`sizeCtl`保存下一次`resize`元素的大小，
  - sizeCtl = 0表示还没有初始化
  - sizeCtl = - N 表示有线程在进行扩容(-1除外，表示容器进行初始化)
  - sizeCtl = N 表示下次元素个数超过N个就进行扩容

​	 初始化容器大小采用CAS设计, 先进入的线程通过CAS将`sizeCtl`大小修改为 -1 ，然后初始化大小，后进入就不能操作这个值。

```java
private final Node<K,V>[] initTable() {
    Node<K,V>[] tab; int sc;
    while ((tab = table) == null || tab.length == 0) {
        if ((sc = sizeCtl) < 0)
            Thread.yield(); // 其他线程占用了初始化，就把调度任务放给其他线程
        else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
            try {
                if ((tab = table) == null || tab.length == 0) {
                    int n = (sc > 0) ? sc : DEFAULT_CAPACITY;//默认初始容量为16
                    @SuppressWarnings("unchecked")
                    Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                    table = tab = nt;
                    sc = n - (n >>> 2);  //这里其实 = n*0.75 相同，这里用来做扩容操作，初始化即12的值
                }
            } finally {
                sizeCtl = sc;
            }
            break;
        }
    }
    return tab;
}
```



## 1.2 Put操作添加值

​	这里涉及到两个经典的涉及

1. 高并发下的扩容
2. 如何保证 addCount 的数据安全性以及性能

**PUT步骤：**

1. 先对key求`hash值`，然后`计算下标`

2. 通过由于使用很多CAS处理数据，所以用for循环做自旋操作，成功即退出

3. 如果当前数组为null，则进行`initTable()`初始化,然后进行下一轮循环处理

4. 通过hash值获取数组下标并获取当前bin是否有值，如果没有就通过cas设置值，如果成功就退出，否则进行下一个循环处理，即下一步

5. 通过获取当前bin的`hash=-1`，如果是就帮助进行数据扩容处理`helpTransfer`，再进行下一循环处理

6. 把当前bin的头结点用`synchronized`做加锁操作

7. 通过遍历当前bin下的链表,如果key相同就覆盖原来的值，会记录原来的旧值，否则就添加到到当前链表的最后一个，然后退出当前遍历，继续下一步处理

8. 再次判定当前bin的数量`bincount`是否超过8，如果是就将当前的链表转换为红黑树，退出添加操作自旋操作

   - 如果此时的容器的长度小于`MIN_TREEIFY_CAPACITY（64）`，会做扩容操作

   注：如果红黑树做删除元素的时候，少于`UNTREEIFY_THRESHOLD（6）`的时，会转为链表

9. 判定是否是覆盖值，而不是添加值，如果是覆盖的话直接返回旧值，否则执行下一步操作

10. 采用类似存值一样的操作分桶做size的加一操作，记录当前map中的数据的多少,当然还判断是否需要扩容，如果需要扩容会进行扩容操作



## 1.3 resize扩容操作

concurrentHashMap有三个扩容操作，前两者是对数据存储的数组进行扩容；最后一个是多线程下，多个线程同时进行数据迁移：

- 1.容器的大小操作`threshold(12)`的大小

- 2.链表的数节点数量超过8，本来要转为红黑树；判断当前容器的大小是否超过64，如果没有超过64就需要进行扩容，否则就转换为红黑树

- 3.在hash值为-1的时候，后来的线程需要帮助之前的容器进行扩容`helpTransfer`，

  - 采用多线程共同扩容，每一个线程分配16的bin进行数据迁移（扩容大了会变大的），比如0-15bin --线程一执行，16-31bin --线程二进行数据迁移；迁移和hashMap类似，通过和n进行与操作，为0就放在新数组原位置，否则就放在+n索引位置

    - ```java
      //实际通过如下方式分配bin进行转移，stride即一个线程的转移步长，
      if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
          stride = MIN_TRANSFER_STRIDE; 
      ```

      

# 2. addCount 计数+1操作

​	`addCount(long x, int check)`，计数考虑的情况，这个计数操作思想很有借鉴性。这里采用分片操作减小性能的消耗

- 采用加锁操作，并发高的话性能不佳
- 采用CAS死循环操作，直到成功，这个在高并发下也会消耗大量的CPU
- 分片，分而治之操作，在合并（类似分布式的概念）,



## 2.1 CounterCells解释

​		ConcurrentHashMap是采用 CounterCell 数组来记录元素个数的，像一般的集合记录集合大小，直接定义一个 size 的成员变量即可，当出现改变的时候只要更新这个变量就行。为什么ConcurrentHashMap 要用这种形式来处理呢？
​		问题还是处在并发上，ConcurrentHashMap 是并发集合，如果用一个成员变量来统计元素个数的话，为了保证并发情况下共享变量的的 难全兴，势必会需要通过加锁或者自旋来实现，如果竞争比较激烈的情况下， size 的设置上会出现比较大的冲突反而影响了性能，所以在ConcurrentHashMap 采用了分片的方法来记录大小，

默认初始化CounterCells数组是两个，同样通过`两倍`扩容来扩容

## 2.2 CounterCell相关定义

```java
/**
 * Spinlock (locked via CAS) used when resizing and/or creating CounterCells.
 *  标识当前cell数组是否在初始化或扩容中的CAS标志位
 */
private transient volatile int cellsBusy;

/**
 * Table of counter cells. When non-null, size is a power of 2.
 * counterCells数组，总数值的分值分别存在每个cell中
 */
private transient volatile CounterCell[] counterCells; 

@sun.misc.Contended static final class CounterCell {
        volatile long value;
        CounterCell(long x) { value = x; }
}

//看到这段代码就能够明白了，CounterCell数组的每个元素，都存储一个元素个数，而实际我们调用size方法就是通过这个循环累加来得到的 //又是一个设计精华，大家可以借鉴；
final long sumCount() {
        CounterCell[] as = counterCells; CounterCell a;
        long sum = baseCount;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    sum += a.value;
            }
        }
        return sum;
    }
```

## 2.3 相关操作步骤

步骤：

- 1.判断`counterCells`是否为空，如果为空，就通过cas操作尝试修改全局变量`baseCount`变量，对这个变量进行原子累加操作(做这个操作的意义是：如果在没有竞争的情况下，仍然采用baseCount来记录元素个数)
- 2.如果cas失败说明存在竞争，这个时候不能再采用baseCount来累加，而是通过`CounterCell`来记录
- 3.判定当前counterCells是否为null,
  - 不是的话，通过hash值计算下标获取当前counterCell数组值是否为null
    - 为null的话，初始化一个CounterCell放在countCell数组中
    - 不为null的话，进行下一次循环，然后直接对当前hash下标的值的数据加+1
  - 是的话进行初始化数组大小为2的counter,然后对随机数进行hash求下标，进行赋值

## 2.4 源码详解

源码：

```java
//传递了两个参数，分别是 1 和 binCount( 链表长度)
private final void addCount(long x, int check) {
    //增加元素个数
    CounterCell[] as; long b, s;
    if ((as = counterCells) != null ||
        //1）第一次尝试 CAS增加 成员变量basecount的值，没有并发就非常好，否则执行下面操作
        !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) {
        CounterCell a; long v; int m;
        boolean uncontended = true; //是否冲突标识，默认为没有冲突
        
       // 这里有几个判断 
       //1. 计数表为空则直接调用fullAddCount 
       //2. 从计数表中随机取出一个数组的位置为空，直接调用fullAddCount 
       //3. 通过CAS修改CounterCell随机位置的值，如果修改失败说明出现并发情况（这里又用到了一种巧妙的方法），调用fullAndCount Random在线程并发的时候会有性能问题以及可能会产生相同的随机数,ThreadLocalRandom.getProbe可以解决这个问题，并且性能要比Random高
        if (as == null || (m = as.length - 1) < 0 ||
            (a = as[ThreadLocalRandom.getProbe() & m]) == null ||
            !(uncontended =
              U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {
            //2.第一次CAS执行count操作失败，这里执行添加count操作
            fullAddCount(x, uncontended);
            return;
        }
        if (check <= 1) //链表长度小于等于1，不需要考虑扩容
            return;
        s = sumCount(); //统计ConcurrentHashMap元素个数
    }
    
    //这里检查扩容判断
    if (check >= 0) { //这里的check是binCount，就是当前bin中链表的数量大小
        Node<K,V>[] tab, nt; int n, sc;
        
        // s标识集合大小，如果集合大小大于或等于扩容阈值（默认值的0.75）
        // 并且table不为空并且table的长度小于最大容量
        while (s >= (long)(sc = sizeCtl) && (tab = table) != null &&
               (n = tab.length) < MAXIMUM_CAPACITY) {
            //这里是生成一个唯一的扩容戳，这个是干嘛用的呢？后面分析
            int rs = resizeStamp(n);
            if (sc < 0) { //sc<0，也就是sizeCtl<0，说明已经有别的线程正在扩容了
                //这5个条件只要有一个条件为true，说明当前线程不能帮助进行此次的扩容，直接跳出循环 
                //sc >>> RESIZE_STAMP_SHIFT!=rs 表示比较高RESIZE_STAMP_BITS位生成戳和rs是否相等，相同 
                //sc=rs+1 表示扩容结束 
                //sc==rs+MAX_RESIZERS 表示帮助线程线程已经达到最大值了 
                //nt=nextTable -> 表示扩容已经结束 
                //transferIndex<=0 表示所有的transfer任务都被领取完了，没有剩余的hash桶来给自己自己好这个线程来做transfer
                
                if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
                    sc == rs + MAX_RESIZERS || (nt = nextTable) == null ||
                    transferIndex <= 0)
                    break;
                //当前线程尝试帮助此次扩容，如果成功，则调用transfer
                if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
                    transfer(tab, nt);
            }
            //如果当前没有在扩容，那么 rs 肯定是一个正数，通过 rs <<RESIZE_STAMP_SHIFT 将 sc 设置为一个负数， 2 表示有一个线程在执行扩容
            else if (U.compareAndSwapInt(this, SIZECTL, sc,
                                         (rs << RESIZE_STAMP_SHIFT) + 2))
                transfer(tab, null);
            s = sumCount();   // 重新计数，判断是否需要开启下一轮扩容
        }
    }
}
```

```java
//countCell添加操作
private final void fullAddCount(long x, boolean wasUncontended) {
    int h;
    //获取当前线程的probe的值，如果值为0，则初始化当前线程的probe的值,probe就是随机数
    if ((h = ThreadLocalRandom.getProbe()) == 0) {
        ThreadLocalRandom.localInit();      // force initialization
        h = ThreadLocalRandom.getProbe();
        wasUncontended = true; // 由于重新生成了probe，未冲突标志位设置为true
    }
    boolean collide = false;                // True if last slot nonempty
    for (;;) { //自旋
        CounterCell[] as; CounterCell a; int n; long v;
        //说明counterCells已经被初始化过了
        if ((as = counterCells) != null && (n = as.length) > 0) {
            // 通过该值与当前线程probe求与，获得cells的下标元素，和hash 表获取索引是一样的
            if ((a = as[(n - 1) & h]) == null) {
                //cellsBusy=0表示counterCells不在初始化或者扩容状态下
                if (cellsBusy == 0) {            // Try to attach new Cell
                    //构造一个CounterCell的值，传入元素个数
                    CounterCell r = new CounterCell(x); // Optimistic create
                    if (cellsBusy == 0 &&
                        //通过cas设置cellsBusy标识，防止其他线程来对counterCells并发处理
                        U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                        boolean created = false;
                        try {               // Recheck under lock
                            CounterCell[] rs; int m, j;
                            //将初始化的r对象的元素个数放在对应下标的位置
                            if ((rs = counterCells) != null &&
                                (m = rs.length) > 0 &&
                                rs[j = (m - 1) & h] == null) {
                                rs[j] = r;
                                created = true;
                            }
                        } finally {//恢复标志位
                            cellsBusy = 0;
                        }
                        if (created)//创建成功，退出循环
                            break;
                        continue;   //说明指定cells下标位置的数据不为空，则进行下一次循环
                    }
                }
                collide = false;
            }
            else if (!wasUncontended)       // CAS already known to fail
                wasUncontended = true;      ////设置为未冲突标识，进入下一次自旋
            //由于指定下标位置的cell值不为空，则直接通过cas进行原子累加，如果成功，则直接退出
            else if (U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))
                break;
            //如果已经有其他线程建立了新的counterCells或者CounterCells大于CPU核心数（很巧妙，线程的并发数不会超过cpu核心数）
            else if (counterCells != as || n >= NCPU)
                collide = false;            // 设置当前线程的循环失败不进行扩容
            else if (!collide)////恢复collide状态，标识下次循环会进行扩
                collide = true;
           //进入这个步骤，说明CounterCell数组容量不够，线程竞争较大，所以先设置一个标识表示为正在扩容
            else if (cellsBusy == 0 &&
                     U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                try {
                    if (counterCells == as) {// Expand table unless stale
                        //扩容一倍 2变成4，这个扩容比较简单
                        CounterCell[] rs = new CounterCell[n << 1];
                        for (int i = 0; i < n; ++i)
                            rs[i] = as[i];
                        counterCells = rs;
                    }
                } finally {
                    cellsBusy = 0; //恢复标识
                }
                collide = false;
                continue;        //继续下一次自旋
            }
            h = ThreadLocalRandom.advanceProbe(h);//更新随机数的值
        }
        //cellsBusy=0表示没有在做初始化，通过cas更新cellsbusy的值标注当前线程正在做初始化操作
        else if (cellsBusy == 0 && counterCells == as &&
                 U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
            boolean init = false;
            try {                           // Initialize table
                if (counterCells == as) {
                    CounterCell[] rs = new CounterCell[2]; //初始化容量为2
                    rs[h & 1] = new CounterCell(x);x);//将x也就是元素的个数放在指定的数组下标位置
                    counterCells = rs;//赋值给counterCells
                    init = true;		//设置初始化完成标识
                }
            } finally {
                cellsBusy = 0;//恢复标识
            }
            if (init)
                break;
        }
        //竞争激烈，其它线程占据cell 数组，直接累加在base变量中
        else if (U.compareAndSwapLong(this, BASECOUNT, v = baseCount, v + x))
            break;                          // Fall back on using base
    }
}
```



### 1）resizeStamp操作

```java
static final int resizeStamp(int n) {
    return Integer.numberOfLeadingZeros(n) | (1 << (RESIZE_STAMP_BITS - 1));
}
```

​	`Integer.numberOfLeadingZeros`这个方法是返回无符号整数 n 最高位非 0 位前面的 0 的个数
比如：
​		10 的二进制是 0 000 0000 0000 0000 0000 0000 0000 1010；那么这个方法返回的值就是2 8
根据resizeStamp 的运算逻辑，我们来推演一下，假如 n =16 ，那么 resizeStamp(16)=32796
转化为二进制是
[0000 0000 0000 0000 1000 0000 0001 1100
接着再来看；当第一个线程尝试进行扩容的时候，会执行下面这段代码

```java
U.compareAndSwapInt(this, SIZECTL, sc,(rs << RESIZE_STAMP_SHIFT) + 2
```

rs左移 16 位，相当于原本的二进制低位变成了高位

```plus
 1000 0000 0001 1100 0000 0000 0000 0000 
 +2
 ------------------------------------------------
 1000 0000 0001 1100 0000 0000 0000 0010
```

高16位代表扩容的标记、低16位代表并行扩容的线程数

高RESIZE_STAMP_BITS位表示扩容标记

 低RESIZE_STAMP_SHIFT位 表示并行扩容线程数

**这样来存储有什么好处呢？**

1. 首先在 CHM 中是支持并发扩容的，也就是说如果当前的数组需要进行扩容操作，可以由多个线程来共同负责

2. 可以保证每次扩容都生成唯一的生成戳 每次新的扩容，都有一个不同的 n ，这个生成戳就是根据 n 来计算出来的一个数字， n 不同，这个数字也不同

  

**第一个线程尝试扩容的时候，为什么是 2？**

​       因为 1 表示初始化， 2 表示一个线程在执行扩容，而且对 sizeCtl 的操作都是基于位运算的，所以不会关心它本身的数值是多少，只关心它在二进制上的数值，而 sc + 1 会在低16 位上加 1



# 3.数据迁移transfer

​		数据迁移在**帮助迁移**和**addCount计数**中做数据迁移

​		扩容是ConcurrentHashMap 的精华之一， 扩容操作的核心在于数据的转移，在单线程环境下数据的转移很简单，无非就是把旧数组中的数据迁移到新的数组。但是这在多线程环境下在扩容的时候其他线程也可能正在添加元素，这时又触发了扩容怎么办？ 可能大家想到的第一个解决方案是加互斥锁，把转移过程锁住，虽然是可行的解决方案，但是会带来较大的性能开销。因为互斥锁会导致 所有访问临界区的线程陷入到阻塞状态，持有锁的线程耗时越长，其他竞争线程就会一直被阻塞，导致吞吐量较低。而且还可能导致死锁。
而ConcurrentHashMap 并没有直接加锁，而是采用 CAS 实现无锁的并发同步策略，最精华的部分是它可以利用多线程来进行协同扩容；

​		简单来说，它把Node 数组当作多个线程之间共享的任务队列，然后通过维护一个指针来划分每个线程锁负责的区间，每个线程通过区间逆向遍历来实现扩容，一个已经迁移完的bucket 会被替换为一个 ForwardingNode 节点，标记当前 bucket 已经被其他线程 迁移完了。

接下来分析一下它的源码实现：

- `fwd`这个类是个标识类，用于指向新表用的，其他线程遇到这个类会主动跳过这个类，因
  为这个类要么就是扩容迁移正在进行，要么就是已经完成扩容迁移，也就是这个类要保证线
  程安全，再进行操作。
- `advance` 这个变量是用于提示代码是否进行推进处理，也就是当前桶处理完，处理下一个
  桶的标识
- `finishing` 这个变量用于提示扩容是否结束用的



```java
private final void transfer(Node<K,V>[] tab, Node<K,V>[] nextTab) {
    int n = tab.length, stride;
    //将 (n>>>3相当于 n/8) 然后除以 CPU核心数。如果得到的结果小于 16，那么就使用 16 // 这里的目的是让每个 CPU 处理的桶一样多，避免出现转移任务不均匀的现象，如果桶较少的话，默认一个 CPU（一个线程）处理 16 个桶，也就是长度为16的时候，扩容的时候只会有一个线程来扩容
    if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
        stride = MIN_TRANSFER_STRIDE; // subdivide range
    if (nextTab == null) {            // initiating
        try {
            //新建一个n<<1原始table大小的nextTab,也就是32
            Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n << 1];
            nextTab = nt;//赋值给nextTab
        } catch (Throwable ex) {      // try to cope with OOME
            sizeCtl = Integer.MAX_VALUE;//扩容失败，sizeCtl使用int的最大值
            return;
        }
        nextTable = nextTab;	//更新成员变量
        transferIndex = n;		//更新转移下标，表示转移时的下标
    }
    int nextn = nextTab.length;//新的tab的长度
    
    // 创建一个 fwd 节点，表示一个正在被迁移的Node，并且它的hash值为-1(MOVED)，也就是前面我们在讲putval方法的时候，会有一个判断MOVED的逻辑。它的作用是用来占位，表示原数组中位置i处的节点完成迁移以后，就会在i位置设置一个fwd来告诉其他线程这个位置已经处理过了，具体后续还会在讲
    ForwardingNode<K,V> fwd = new ForwardingNode<K,V>(nextTab);
    
    // 首次推进为 true，如果等于 true，说明需要再次推进一个下标（i--），反之，如果是 false，那么就不能推进下标，需要将当前的下标处理完毕才能继续推进
    boolean advance = true;
    //判断是否已经扩容完成，完成就return，退出循环
    boolean finishing = false; // to ensure sweep before committing nextTab
    
    //通过for自循环处理每个槽位中的链表元素，默认advace为真，通过CAS设置transferIndex属性值，并初始化i和bound值，i指当前处理的槽位序号，bound指需要处理的槽位边界，先处理槽位15的节点
    for (int i = 0, bound = 0;;) {
        Node<K,V> f; int fh;
        // 这个循环使用CAS不断尝试为当前线程分配任务 
        // 直到分配成功或任务队列已经被全部分配完毕 
        // 如果当前线程已经被分配过bucket区域 
        // 那么会通过--i指向下一个待处理bucket然后退出该循环
        while (advance) {
            int nextIndex, nextBound;
            //--i表示下一个待处理的bucket，如果它>=bound,表示当前线程已经分配过bucket区域
            if (--i >= bound || finishing)
                advance = false;
            //表示所有bucket已经被分配完毕
            else if ((nextIndex = transferIndex) <= 0) {
                i = -1;
                advance = false;
            }
            //通过cas来修改TRANSFERINDEX,为当前线程分配任务，处理的节点区间(nextBound,nextIndex)->(0,15)
            else if (U.compareAndSwapInt
                     (this, TRANSFERINDEX, nextIndex,
                      nextBound = (nextIndex > stride ?
                                   nextIndex - stride : 0))) {
                bound = nextBound;//0
                i = nextIndex - 1;//15
                advance = false;
            }
        }
        if (i < 0 || i >= n || i + n >= nextn) {
            int sc;
            if (finishing) {//如果完成了扩容
                nextTable = null;//删除成员变量
                table = nextTab;//更新table数组
                sizeCtl = (n << 1) - (n >>> 1);//更新阈值(32*0.75=24)
                return;
            }
            //如果相等，表示当前为整个扩容操作的 最后一个线程，那么意味着整个扩容操作就结束了；如果不想等，说明还得继续这么做的目的，一方面是防止不同扩容之间出现相同的sizeCtl ，另外一方面，还可以避免sizeCtl 的ABA问题导致的扩容重叠的情况
            
            //// sizeCtl 在迁移前会设置为 (rs << RESIZE_STAMP_SHIFT) + 2 // 然后，每增加一个线程参与迁移就会将 sizeCtl 加 1， // 这里使用 CAS 操作对 sizeCtl 的低16位进行减 1，代表做完了属于自己的任务
            if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {
               // 第一个扩容的线程，执行transfer方法之前，会设置 sizeCtl = (resizeStamp(n) << RESIZE_STAMP_SHIFT) + 2) 后续帮其扩容的线程，执行transfer方法之前，会设置 sizeCtl = sizeCtl+1 每一个退出transfer的方法的线程，退出之前，会设置 sizeCtl = sizeCtl-1 那么最后一个线程退出时：必然有 sc == (resizeStamp(n) << RESIZE_STAMP_SHIFT) + 2)，即 (sc - 2) == resizeStamp(n) << RESIZE_STAMP_SHIFT
                // 如果 sc - 2 不等于标识符左移 16 位。如果他们相等了，说明没有线程在帮助他们扩容了。也就是说，扩容结束了。
                if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT)
                    return; //这里表示所有bin处理完毕，也是最后一个线程处理完毕
                
                // 如果相等，扩容结束了，更新 finising 变量
                finishing = advance = true;
                i = n; // recheck before commit
            }
        }
        else if ((f = tabAt(tab, i)) == null)
            advance = casTabAt(tab, i, null, fwd);
        else if ((fh = f.hash) == MOVED)
            advance = true; // already processed
        else {
            synchronized (f) { //对当前节点bin头结点进行加锁，准备数据迁移
                if (tabAt(tab, i) == f) {	//再做一次校验
                    Node<K,V> ln, hn;   //ln 表示低位， hn 表示高位 接下来这段代码的作用是把链表拆分成两部分， 0 在低位， 1 在高位
                    if (fh >= 0) {
                        //获取n位的值，=0表示还是在原来的位置，=1表示新迁移的+n即可	
                        int runBit = fh & n; 
                        Node<K,V> lastRun = f;
                        //遍历当前bucket的链表，目的是尽量重用Node链表尾部的一部分
                        for (Node<K,V> p = f.next; p != null; p = p.next) {
                            int b = p.hash & n;
                            if (b != runBit) {
                                runBit = b;
                                lastRun = p;
                            }
                        }
                        if (runBit == 0) {
                            ln = lastRun;
                            hn = null;
                        }
                        else {
                            hn = lastRun;
                            ln = null;
                        }
                        //这里遍历当前链表，高位的添加进hn链表，低链表加入ln
                        for (Node<K,V> p = f; p != lastRun; p = p.next) {
                            int ph = p.hash; K pk = p.key; V pv = p.val;
                            if ((ph & n) == 0)
                                ln = new Node<K,V>(ph, pk, pv, ln);
                            else
                                hn = new Node<K,V>(ph, pk, pv, hn);
                        }
                      
                        setTabAt(nextTab, i, ln);//将低位的链表放在 i 位置也就是不动
                        setTabAt(nextTab, i + n, hn);//将高位链表放在 i+n 位置
                        setTabAt(tab, i, fwd);//把旧table 的 hash 桶中放置转发节点，表明此 hash 桶已经被处理
                        advance = true;//表示可以进入下一轮循环做下一个bin的数据转移
                    }
                    //红黑树数据转移
                    else if (f instanceof TreeBin) {
                        TreeBin<K,V> t = (TreeBin<K,V>)f;
                        TreeNode<K,V> lo = null, loTail = null;
                        TreeNode<K,V> hi = null, hiTail = null;
                        int lc = 0, hc = 0;
                        for (Node<K,V> e = t.first; e != null; e = e.next) {
                            int h = e.hash;
                            TreeNode<K,V> p = new TreeNode<K,V>
                                (h, e.key, e.val, null, null);
                            if ((h & n) == 0) {
                                if ((p.prev = loTail) == null)
                                    lo = p;
                                else
                                    loTail.next = p;
                                loTail = p;
                                ++lc;
                            }
                            else {
                                if ((p.prev = hiTail) == null)
                                    hi = p;
                                else
                                    hiTail.next = p;
                                hiTail = p;
                                ++hc;
                            }
                        }
                        ln = (lc <= UNTREEIFY_THRESHOLD) ? untreeify(lo) :
                        (hc != 0) ? new TreeBin<K,V>(lo) : t;
                        hn = (hc <= UNTREEIFY_THRESHOLD) ? untreeify(hi) :
                        (lc != 0) ? new TreeBin<K,V>(hi) : t;
                        setTabAt(nextTab, i, ln);
                        setTabAt(nextTab, i + n, hn);
                        setTabAt(tab, i, fwd);
                        advance = true;
                    }
                }
            }
        }
    }
}
```

## 3.1 高低位原理

​		所以对于高位，直接增加扩容的长度，当下次hash 获取数组位置的时候，可以直接定位到对应的位置。
这个地方又是一个很巧妙的设计，直接通过高低位分类以后，就使得不需要在每次扩容的时候来重新计算 hash ，极大提升了效率。



`ConcurrentHashMap`在做链表迁移时，会用高低位来实现，这里有两个问题要分析一下

### 1) 如何实现高低位链表的区分
假如我们有这样一个队列

![nIB6iQ.png](https://s2.ax1x.com/2019/09/17/nIB6iQ.png)

第14 个槽位插入新节点之后，链表元素个数已经达到了 8 ，且数组长度为 16 ，优先通过扩容来缓解链表过长的问题 ，扩容这块的图解稍后再分析，先分析高低位扩容的原理:

​		假如当前线程正在处理槽位为14 的节点，它是一个链表结构，在代码中，首先定义两个变量节点 l n 和 h n ，实际就是 lowNode 和 HighNode ，分别保存 hash 值的第 x 位为 0 和 不等于0 的节点；通过fn&n 可以把这个链表中的元素分为两类， A 类是 hash 值的第 X 位为 0 B 类是 hash 值的第 x 位为 不等于 0 （至于为什么要这么区分，稍后分析），并且通过 lastRun 记录最后要处理的节点。最终要达到的目的是， A 类的链表保持位置不动， B 类的链表为 14+16( 扩容增加的长度 ))=30
​	我们把14 槽位的链表单独伶出来，我们用蓝色表示 fn&n 0 的节点，假如链表的分类是这样

![nIDjpj.png](https://s2.ax1x.com/2019/09/17/nIyLtO.png)

```java
int runBit = fh & n;
Node<K,V> lastRun = f;
for (Node<K,V> p = f.next; p != null; p = p.next) {
    int b = p.hash & n;
    if (b != runBit) {
        runBit = b;
        lastRun = p;
    }
}
if (runBit == 0) {
    ln = lastRun;
    hn = null;
}
else {
    hn = lastRun;
    ln = null;
}
```

​		通过上面这段代码遍历，会记录runBit 以及 lastRun ，按照上面这个结构，那么 run Bit 应该是橙色节点 lastRun 应该是第 6 个节点接着，再通过这段代码进行遍历，生成ln 链以及 hn 链

```java
for (Node<K,V> p = f; p != lastRun; p = p.next) {
    int ph = p.hash; K pk = p.key; V pv = p.val;
    if ((ph & n) == 0)
        ln = new Node<K,V>(ph, pk, pv, ln);
    else
        hn = new Node<K,V>(ph, pk, pv, hn);
}
```

​		接着，通过CAS 操作，把 hn 链放在 i +n 也就是 1 4 16 的位置， ln 链保持 原来的位置不动。并且设置当前节点为 f wd ，表示已经被当前线程迁移完了

```java
setTabAt(nextTab, i, ln);
setTabAt(nextTab, i + n, hn);
setTabAt(tab, i, fwd);
advance = true;
```

迁移完成以后的数据分布如下:

![nIcePO.png](https://s2.ax1x.com/2019/09/17/nIcePO.png)





# 4.帮助迁移

​		put方法第三阶段；如果对应的节点存在，判断这个节点的hash 是不是等于 MOVED( 1)1)，说明当前节点是
`ForwardingNode `节点，

​	意味着有其他线程正在进行扩容，那么当前现在直接帮助它进行扩容，因此调用`helpTransfer`

```java
final Node<K,V>[] helpTransfer(Node<K,V>[] tab, Node<K,V> f) {
    Node<K,V>[] nextTab; int sc;
    // 判断此时是否仍然在执行扩容,nextTab=null的时候说明扩容已经结束了
    if (tab != null && (f instanceof ForwardingNode) &&
        (nextTab = ((ForwardingNode<K,V>)f).nextTable) != null) {
        int rs = resizeStamp(tab.length);//生成扩容戳
         //说明扩容还未完成的情况下不断循环来尝试将当前线程加入到扩容操作中
        while (nextTab == nextTable && table == tab &&
               (sc = sizeCtl) < 0) {
            //下面部分的整个代码表示扩容结束，直接退出循环
            //transferIndex<=0表示所有的Node都已经分配了线程
            //sc=rs+MAX_RESIZERS 表示扩容线程数达到最大扩容线程数 
            //sc >>> RESIZE_STAMP_SHIFT !=rs， 如果在同一轮扩容中，那么sc无符号右移比较高位和rs的值，那么应该是相等的。如果不相等，说明扩容结束了 //sc==rs+1 表示扩容结束
            if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
                sc == rs + MAX_RESIZERS || transferIndex <= 0)
                break;//跳出循环
            if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1)) {{//在低16位上增加扩容线程数
                transfer(tab, nextTab);//帮助扩容
                break;
            }
        }
        return nextTab;
    }
    return table;//返回新的数组
}
```





# 4. jdk1.7和jdk1.8 的ConcurrentHashMap比较

|          | jdk1.7 的ConcurrentHashMap                                   | jdk1.8 的ConcurrentHashMap |
| -------- | ------------------------------------------------------------ | -------------------------- |
| 数据结构 | 数组 Segment(默认是16，所以最多支持16个线程读写)；Segment 内部是由 数组+链表 | 数组+链表+红黑树           |
| 加锁     | 分段锁，锁Segment                                            | 锁的是容器的一个bin        |
| 加锁方式 | 使用Entry继承 ReentrantLock加锁                              | 使用synchronize锁一个bin   |
| 扩容     | segment 数组不能扩容，扩容是 segment 数组某个位置内部的数组 HashEntry\[] 进行扩容，扩容后，容量为原来的 2 倍。 | 外部的数组进行扩容         |



# 5.HashMap和ConcurrentHashMap区别

|           | HashMap                           | ConcurrentHashMap      |
| --------- | --------------------------------- | ---------------------- |
| key,value | 允许一个key为null,多个value为null | key, value都不能为null |
| ....      |                                   |                        |
|           |                                   |                        |

