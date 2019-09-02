# 1.字符串SDS

SDS = 'Simple Dynamic String' ；底层结构，有点类似Java中的ArrayList。

```c
struct sdshdr{
    int len; //等于sds所保存字符串的长度，等于buf数组中已使用的自己数量
    
    int free;//等于buf数组中未使用的字节数量
    
    char buf[]；//字节数据用于保存字符串
        //（数组末尾使用c语言的‘\0‘结尾’，主要是为了符合c语言的写法，方便调用c语言的库函数）
}
```

 **1.空间预分配**

	1、如果对SDS进行修改后字符串长度（len值）小于1MB，则额外分配len大小相同的空间（free值）；
	2、若SDS修改后len大于1MB，则额外分配1MB的空间（free值）。

**2.惰性空间释放**

```对SDS字符串进行缩短操作，并不会重新分配内存回收缩短的字节，而是使用free属性将这些字节的数量记录起来。```

**3.二进制安全**

**4.兼容部分c字符串函数**



# 2. 链表linkedlist

这个和Java中的linkedList类似

链表节点结构如下：

```c
typedef struct listNode{
    struct listNode * prev;//前置节点
    Struct listNode * next;//后置节点
    void * vlaue;//节点的值
}listNode;

```

不过一般用链表来表示

```c
typedef struct list{
    listNode * head; //头结点指针
    listNode * tail;//尾节点指针
    unsigned long len;//链表长度计数
    //下面三个是为了实现多态链表所需要的类型特定函数
    void *(*dup)(void *ptr);
    void *(*free)(void *ptr);
    int (*match)(void *ptr,void *key);
}list;
```



# 3. 字典

字典，又称符号表，关联数组或者映射是一个用来保存键值对的抽象数据结构。

​		redis的字典使用**哈希表**作为底层实现，一个哈希表里面可以有多个**哈希表节点**，而每一个哈希表节点就保存了**字典的键值对**

## 3.1 哈希表 hashtable

​		哈希表的数据结构， 这个其实很像Java中的HashMap的结构，不过现在Java8中的HashMap中在hash冲突的情况下如果多了会变成红黑树。

```c
typedef struct dictht{
    dictEntry **table;//哈希表数组
    unsigned long size;//哈希表大小
    unsigned long sizemask;//哈希表掩码，计算索引值，总是等于size-1,这里是为了在读取key的时候，不用总是size-1，减少计算
    unsigned long used;//该哈希表中已经有的节点的数量
}
```



## 3.2 哈希表节点

哈希表节点结构如下：

```c
typedef struct dictEntry{
    void *key;//键
    //值，可以是一个指针，或者uint64_t，由或者int64_t的整数
    union{
        void *val; 
        uint64_tu64;
        int64_ts64;
    }v;
    struct dicEntry *next;//指向下一个哈希表节点，解决hash冲突的问题（两个key的hash值相同的时候）
}dictEntry;
```

​		哈希冲突：使用链地址法解决键冲突，为了速度考虑，总是将新节点添加到链表的表头位置。

## 3.3 字典

redis中的字典有dict.h/dic结构：

```c
typedef struct dict{
    
    dictType *type;//类型特定函数，dictType保存了特定类型键值对的函数，
    
    void *privdata;//保存需要传给特定函数的可选参数
    
    dictht ht[2];//包含两个数组的哈希表，字典只是用ht[0]，ht[1]只是在扩容(rehash)的时候临时使用
    
    int rehashids;//记录rehash的进度，如果当前没有rehash则值为-1
}dict;


/*  字典类型特定函数 */
typedef struct dictType {
    unsigned int (*hashFunction)(const void *key); 
    // 计算哈希值的函数
    void *(*keyDup)(void *privdata, const void *key);  
    // 复制键的函数
    void *(*valDup)(void *privdata, const void *obj);               
    // 复制值的函数
    int (*keyCompare)(void *privdata, const void *key1, const void *key2);   
    // 对比键的函数
    void (*keyDestructor)(void *privdata, void *key);   
    // 销毁键的函数
    void (*valDestructor)(void *privdata, void *obj);       
    // 销毁值的函数
} dictType;
```

redis使用MurmurHash2算法来计算键的哈希值，然后让哈希值和sizeMask(数组大小)做与（&）操作获取索引值



## 3.4 rehash

​	就是扩容或者缩减容的时候，随着键值对的变化，为了让哈希表的负载因子（键值对/链表大小）保持一定大小，需要对哈希表做rehash。

- **1.分配空间**，保证分配后的数据是2的幂次方
  -  **扩容操作**，ht[1]的大小为ht[0].used的大小的两倍，然后往后走的第一个2的幂次方(和hashMap类似)
  - **收缩操作**，ht[1]的大小为ht[0].used的大小的往后走的第一个2的幂次方(和hashMap类似)，

- 2.将保存在h[0]所有键值对全部重新计算哈希值和索引值到h[1]哈希表位置上
- 3.ht[0]全部键值对都迁移完毕后，将ht[1]设置为ht[0],然后新创建一个空的哈希表ht[1]为下次rehash做准备



负载因子：load_factor = ht[0].used / ht[0].size

**1.哈希表的扩展：** 主要是减少内存的开销，因为下面两者都会fork一个子进程操作数据

- 服务器没执行BGSAVE（子进程rdbsave操作）或BGREWRITEAOF（重写aof文件）命令，并且负载因子大于等于1.

- 服务器正执行BGSAVE或BGREWRITEAOF命令，并且负载因子大于等于5.

  

**2.哈希表的收缩：**当哈希表的负载因子小于0.1时，会自动收缩。



**渐进式rehash：**
			若是键值对数量太大，那么若一次性rehash可能会导致服务器在一段时间内停止服务。因此可以采用分多次渐进式地将ht[0]的键值对慢慢地rehash到ht[1]中。
步骤：

1. 为ht[1]分配空间，让字典同时拥有ht[0]和ht[1]两个哈希表；
2. 将rehashidx设为0，表示rehash正式开始；
3. 在rehash期间，每次对字典进行添加、删除、查找或者更新操作时，除了执行指定操作外，还会顺带将ht[0]在rehashidx索引上的所有键值对rehash到ht[1]中，然后将rehashidx加1；
4. 当ht[0]中所有的键值对全部rehash到ht[1]后，将rehashidx设为-1，rehash完成。

在rehash期间，对字典进行增删查找操作时需要在ht[0]和ht[1]上操作。如查找要现在ht[0]查找，查不到再到ht[1]查找；如增加键值对，一律在ht[1]中增加，这样保证ht[0]中的键值对数量只减少不增加



# 4. 跳跃表zskiplist

​		在跳跃表中，节点是按分数从小到大排序的。各个节点保存的成员对象必须是唯一的，但不同节点的分数可以相同，分数相同的节点按照成员对象在字典序中的大小来排序。主要作为有序集合底层实现。

跳跃表通过在没有给节点中位置多个指向其他节点的指针，从而可以达到快速访问的目的.

结构如下：

```c
/* ZSETs use a specialized version of Skiplists */
/*  跳跃表节点 */
typedef struct zskiplistNode {
    robj *obj;                         // 成员对象 ,这里可以是sds值的指针
    double score;                      // 分值
    struct zskiplistNode *backward;    // 后退指针
    struct zskiplistLevel {            // 层
        struct zskiplistNode *forward; // 前进指针
        unsigned int span;             // 跨度
    } level[];
} zskiplistNode;

/* 跳跃表 */
typedef struct zskiplist {
    struct zskiplistNode *header, *tail; // 表头节点和表尾节点
    unsigned long length;                // 表中节点的数量
    int level;                           // 表中层数最大的节点的层数
} zskiplist;
```



参考下图:

[![nidVF1.jpg](https://s2.ax1x.com/2019/09/02/nidVF1.jpg)](https://imgchr.com/i/nidVF1)



- **level层数**：层数越多，指向其他节点越多，访问速度越快；每添加一个值得时候，level实在1-32中（根据幂次定律）随机生成一个
- **前进指针：**每层都有指向表位的前进指针，如上虚线表示表头到表位的方向
- **跨度**：用于记录两个节点的距离，两个节点跨度越大，相距越远，指向null的跨度为0；跨度实际用来计算排位，将沿途所有的跨度相加就为目标节点在跳跃表的排位
- **后退指针**（BW =backward）：用来表示从表尾向表头访问节点，但是后退指针和前进指针不同，后退指针每次只能后退一步

- **分值和成员**：score是一个double类型浮点数，分值由小到大排序；节点对象是一个指针指向一个字符串对象，而字符串对象保存着SDS值



# 5. 整数集合intset

​	当一个集合只包含整数值元素，并且这个集合的元素不多的时候，redis就会使用整数集合作为集合键的底层实现。

结构如下：

```c
typedef struct intset {
    uint32_t encoding;   // 编码方式，编码不同，下面的数组保存的数值也不同
    uint32_t length;     // 集合包含的元素数量
    int8_t contents[];   // 保存元素的数组，从小到大排序，无重复
} intset;

//encoding的编码有几种
1.INTSET_ENC_INT16  contents就是 int_16类型的数据
2.INTSET_ENC_INT32 contents就是 int_32类型的数据
2.INTSET_ENC_INT64 contents就是 int_64类型的数据
```



升级：若要添加一个新元素到整数集合中，并且新元素类型比整数集合元素的类型长，则整数集合需要先升级，才能将新元素放进集合中。

步骤：

1. 根据新元素的类型，扩展整数集合底层数组（contents）的空间大小，并为新元素分配空间；
2. 将底层数组现有的所有元素都转换成与新元素相同的类型，并有序地放到正确的位置上；
3. 将新元素添加到底层数组中。
   因为引发升级的新元素的长度总是比整数集合现有的所有元素的长度都大，所以该元素要么就大于所有现有元素，要不就小于现有所有元素，因此新元素要不就放在最开头要不就放在最末尾。

不支持降级！

# 6.压缩列表（ziplist）

​		压缩列表是由一系列特殊编码的**连续内存块组成的顺序型数据结构**；可以减少内存碎片，并且访问直接顺序访问，速度快。但是如果添加数据都需要重新分配连续内存，所以一般数据量大的时候就不能使用这个数据结构了。

​		当hash键只包含少量的键值对，并且每一个键值对的是小的整数值，或者长度比较短的时候就是用ziplist压缩列表来做底层存储。

**压缩列表组成**：

![niDq5d.jpg](https://s2.ax1x.com/2019/09/02/niDq5d.jpg)

![nirSr8.png](https://s2.ax1x.com/2019/09/02/nirSr8.png)



**每一个entry节点**的的内容如下所示:

![nir5Js.jpg](https://s2.ax1x.com/2019/09/02/nir5Js.jpg)

previous_entry_length：记录了压缩列表中前一个节点的长度（1或5字节）：

- 若前一节点长度小于254字节，那么previous_entry_length长度为1字节；
- 若前一节点长度大于等于254字节，那么previous_entry_length长度为5字节，其中第一字节为0xFE，后四个字节为长度。

encoding：记录了节点content属性所保存的数据类型以及长度；

content：保存节点的值，可以是一个字节数组或者整数。



# 7. quickList

​		每个节点使用ziplist来保存数据；quicklist里面保存着一个一个小的ziplist。其实就是linkedlist和ziplist的结合。quicklist中的每个节点ziplist都能够存储多个数据元素。

但是为啥不直接用ziplist喃。本身ziplist就是一个双向链表。并且额外的信息还特别的少。

​				其实原因很简单。根据ziplist来看，ziplist在我们程序里面来看将会是一块连续的内存块。它使用内存偏移来保存next从而节约了next指针。这样造成了我们每一次的删除插入操作都会进行remalloc,从而分配一块新的内存块。当我们的ziplist特别大的时候。没有这么大空闲的内存块给我们的时候。操作系统其实会抽象出一块连续的内存块给我。在底层来说他其实是一个链表链接成为的内存。不过在我们程序使用来说。他还是一块连续的内存。这样的话会造成内存碎片，并且在操作的时候因为内存不连续等原因造成效率问题。或者因为转移到大内存块等进行数据迁移。从而损失性能。

所以quicklist是对ziplist进行一次封装，使用小块的ziplist来既保证了少使用内存，也保证了性能。

当然quicklist支持使用压缩算法对节点进行压缩

## 7.1 数据结构

如图：

[![ni67QI.png](https://s2.ax1x.com/2019/09/02/ni67QI.png)](https://imgchr.com/i/ni67QI)

数据结构：

```c
typedef struct quicklistNode {
    struct quicklistNode *prev;  // 指向上一个ziplist节点
    struct quicklistNode *next;  // 指向下一个ziplist节点
    unsigned char *zl;           // 数据指针，如果没有被压缩，就指向ziplist结构，反之指向quicklistLZF结构 
    unsigned int sz;             // 表示指向ziplist结构的总长度(内存占用长度)
    unsigned int count : 16;     // 表示ziplist中的数据项个数
    unsigned int encoding : 2;   // 编码方式，1--ziplist，2--quicklistLZF
    unsigned int container : 2;  // 预留字段，存放数据的方式，1--NONE，2--ziplist
    unsigned int recompress : 1; // 解压标记，当查看一个被压缩的数据时，需要暂时解压，标记此参数为1，之后再重新进行压缩
    unsigned int attempted_compress : 1; // 测试相关
    unsigned int extra : 10; // 扩展字段，暂时没用
} quicklistNode;


typedef struct quicklistLZF {
    unsigned int sz; /* LZF size in bytes*/
    char compressed[];
} quicklistLZF;


typedef struct quicklist {
    quicklistNode *head;        // 指向quicklist的头部
    quicklistNode *tail;        // 指向quicklist的尾部
    unsigned long count;        // 列表中所有数据项的个数总和
    unsigned int len;           // quicklist节点的个数，即ziplist的个数
    int fill : 16;              // ziplist大小限定，由list-max-ziplist-size给定
    unsigned int compress : 16; // 节点压缩深度设置，由list-compress-depth给定
} quicklist;
```



## 7.2 压缩深度

quicklist 默认的压缩深度是 0，也就是不压缩。压缩的实际深度由配置参数list-compress-depth决定。

　　为了支持快速的 push/pop 操作，quicklist 的首尾两个 ziplist 不压缩，此时深度就是 1。

　　如果深度为 2，就表示 quicklist 的首尾第一个 ziplist 以及首尾第二个 ziplist 都不压缩。



## 7.3 zipList 长度

　　quicklist 内部默认单个 ziplist 长度为 8k 字节，超出了这个字节数，就会新起一个 ziplist。

　　ziplist 的长度由配置参数 `list-max-ziplist-size `决定。