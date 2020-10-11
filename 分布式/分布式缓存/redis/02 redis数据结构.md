# 1. Redis的特点

- 1>:Redis采用的是基于内存的采用的是**单进程单线程**模型的**KV**数据库

-  2>:基于内存的(信息是在内存中的 访问的速度特别快)

- 3>:数据结构简单(Key-Value)

- 4>:支持数据的持久化(能够将内存中的数据同步到硬盘)

- 5>使用16个db存储，相互隔离，默认用第0个db

## 1.1. 存储结构

​     大家一定对字典类型的数据结构非常熟悉，比如map ，通过key value的方式存储的结构。 redis的全称是remote dictionary server(远程字典服务器)，它以字典结构存储数据，并允许其他应用通过TCP协议读写字典中的内容。数据结构如下：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g1kz1ouhmgj20ku06h74j.jpg)

# 2.Redis 的数据类型

redis的数据结构都是

```c
typdef struct redisObject{
    unsigned type:4;//类型 string,set,list等
    unsigned encoding:4;//编码
    unsigned lru:LRU_BITS //最后一次访问的时间
	int refcount; //表示该键值被引用的数量，即一个键值可被多个键引用
    void * prt; //指向底层数据结构的指针
    //...
}
```

type类型如下，

```bash
redis> type key 
//通过如上命令 可以获取当前key的类型
```



![nigWPe.jpg](https://s2.ax1x.com/2019/09/02/nigWPe.jpg)

编码的底层数据有：

```bash
redis>object encoding key
//通过如上命令可以获取当前key所存在的底层数据结构信息
```



![nigIKI.png](https://s2.ax1x.com/2019/09/02/nigIKI.png)

## 2.1 字符串类型 - String

​      字符串类型是redis中最基本的数据类型，它能存储任何形式的字符串，包括二进制数据，int,float,double类型。你可以用它存储用户的邮箱、json化的对象甚至是图片。一个字符类型键允许存储的最大容量是512M

**应用场景**：普通的数据缓存这种一般就放到这里面  应用场景比较广   因为他就是一个键值对的字符串而已



**内部结构：**在Redis内部，String类型通过 int、SDS(simple dynamic string)作为结构存储，int用来存放整型数据，sds存放字节/字符串和浮点型数据。在C的标准字符串结构下进行了封装，用来提升基本操作的性能，同时也充分利用已有的C的标准库，简化实现逻辑

编码：int（整数）、raw（redisObject+SDS）或embstr（redisObject+embstr编码的SDS，连续内存分配）

- 若是整数值，并且该整数值可以用long类型表示，则编码为int；
- 若是字符串值，并且字符串长度大于32字节，那么将使用SDS保存字符串值，编码为raw；
- 若是字符串值，并且字符串长度小于等于32字节，则编码为embstr。

注意：

- long double类型的浮点数是用字符串保存的，对其操作会先转化为浮点型进行操作再转化为字符串保存。
- 对int型操作（如APPEND一个字符串），会把int转化为raw。
- embstr字符串为只读，若要对其修改会先转化为raw。

注：embstr其实也是sds结构，只是embstr将创建字符串的内存分配有raw的两次降低为一次了，所有数据都放在放在一块连续的内存中，并且释放当前的embstr也只需要调用一次就可以了

##  2.2 列表类型 - List(可以重复的)

​     列表类型(list)可以存储一个有序的字符串列表，常用的操作是向列表两端添加元素或者获得列表的某一个片段。

**应用场景:**博客的关注、博客的评论、博客的积分可以实现消息队列功能

**内部结构** ：之前是ziplist和linkedlist实现的；redis3.2之后，采用的一种叫quicklist的数据结构来存储list，列表的底层都由quicklist实现

在redis3.2之前的情况是：

- 当列表对象保存的字符串元素都小于64字节，并且元素数量小于512个时，使用ziplist；
- 否则使用linkedlist，每个节点保存一个字符串对象。

在redis3.2之后是使用quicklist来保存

- linkedlist和ziplist的结合体



##  2.3 Hash类型 (Java中的Map)

**应用场景**:SSO单点登录



 **内部结构：**    map提供两种结构来存储，一种是字典（包含hashtable）、另一种是前面讲的ziplist，数据量小的时候用ziplist. 

- 哈希对象中保存的所有键值对的键和值的字符串长度都小于64字节，并且键值对数量小于512个，使用ziplist；
- 否则使用hashtable，键和值都为字符串对象。

注：ziplist存储的时候现将压缩键节点推入到压缩列表表尾，然后再将保存的了值得节点推入列表表尾



##   2.4 集合类型 - Set(无序但是不重复)：

​    集合类型中，每个元素都是不同的，也就是不能有重复数据，同时集合类型中的数据是无序的。一个集合类型键可以存储至多2的32次方-1个 。集合类型和列表类型的最大的区别是有序性和唯一性

**应用场景**:：每个用户的关注的人在一个集合中，很容易实现两个人的共同好友

**内部结构:** Set在的底层数据结构以intset或者字典（hashtable）来存储。当set中只包含整数型的元素时，采用intset来存储，否则，采用hashtable存储，但是对于set来说，该hashtable的value值用于为NULL。通过key来存储元素



- 集合对象保存的所有元素都是整数值，并且数量不超过512个时，使用intset；这个数量可以通过`set-max-intset-entries`配置
- 否则使用hashtable，若使用hashtable，每个键都为一个字符串对象，值为NULL。



## 2.5 有序集合 - SortedSet

**应用场景**:最新最热的商品(有序)

**内部结构:**zset类型的数据结构就比较复杂一点，内部是以ziplist或者 字典+跳跃表来实现，这里面最核心的一个结构就是skiplist，也就是跳跃表      		



- 有序结合保存的元素数量小于128个，并且元素成员长度小于64字节，使用ziplist，每个结合元素使用两个压缩列表节点保存，第一个保存成员，第二个保存分数，集合元素按分值从小到大排序；

- 否则使用skiplist编码，底层为zset结构，包含一个字典和一个跳跃表，其中跳跃表按分值进行排序，实现了范围查询；而字典创建了从成员到分数的映射，键为集合元素，值为分数；字典和跳跃表都使用指针指向成员和分数，因此不会造成内存浪费

  - 这种结构如下

  - ```c
    typedef struct zset{
        zskiplist * zsl;//跳跃表
        dict *dict; //字典
    }
    ```

    
    

## 2.6 Geo地理位置存储

​		Geo存储通过一定算法将经纬度（二维）转为一个一维数存储，当要再次通过key取出原来的经纬度会有一定误差，不过一般在我们实际应用过程应该也是能够接受的。

​	geo地址位置存储支持如下命令，包含添加

```xml
GEOADD key latitude longitude  name 	//添加地理位置
GEODIST key  name1 name2 [unit] 		//计算两个元素之间的距离
GEOHASH key name					 //获取元素经纬度坐标经过geohash算法生成的base32编码值
GEOPOS	key name [..]				 //获取集合中任意元素的经纬度坐标，可以一次获取多个
GEORADIUS	key name longitude latitude radius unit		 //根据坐标点查找附近位置的元素 
GEORADIUSBYMEMBER	key name radius unit [...] 			//查找指定元素地点位置的有哪些人
```

​		geo地理位置的底层存储原理，使用的是SortSet数据结构存储。把经纬度通过geoHash算法转换为一个整数值，作为score存储在SortSet中

**Redis Geo使用时的注意事项**
			在一个地图应用中，车的数据、餐馆的数据、人的数据可能会有百万千万条，如果使用 Redis 的 Geo 数据结构，它们将全部放在一个 zset 集合中。**在 Redis 的集群环境中，集合可能会从一个节点迁移到另一个节点，如果单个 key 的数据过大，会对集群的迁移工作造成较大的影响，在集群环境中单个 key 对应的数据量不宜超过 1M，否则会导致集群迁移出现卡顿现象，影响线上服务的正常运行。**

​	所以，这里建议 Geo 的数据**使用单独的 Redis 实例部署，不使用集群环境。**

​	如果数据量过亿甚至更大，就需要对 Geo 数据进行拆分，按国家拆分、按省拆分，按市拆分，在人口特大城市甚至可以按区拆分。这样就可以显著降低单个 zset 集合的大小。(注意：zset集合大小，进行合适地切分)



### GeoHash算法思想

```text
	GeoHash算法将二维的经纬度数据映射到一维的整数，这样所有的元素都将挂载到一条线上，距离靠近的二维坐标映射到一维后的点之间距离会很接近。当我们想要计算附近的人时，首先将目标位置映射到这条线上，然后在这条一维的线上获取附近的点就ok了。

	那这个映射算法具体是怎样的呢？它将整个地球看成一个二维平面，然后划分成了一系列正方形的方格，就好比围棋棋盘。所有的地图元素坐标都将放置于唯一的方格中。方格越小，坐标越精确。然后对这些方格进行整数编码，越是靠近的方格编码越是接近。那如何编码呢？一个最简单的方案就是切蛋糕法。设想一个正方形的蛋糕摆在你面前，二刀下去均分分成四块小正方形，这四个小正方形可以分别标记为 00,01,10,11 四个二进制整数。然后对每一个小正方形继续用二刀法切割一下，这时每个小小正方形就可以使用 4bit 的二进制整数予以表示。然后继续切下去，正方形就会越来越小，二进制整数也会越来越长，精确度就会越来越高
	
	编码之后，每个地图元素的坐标都将变成一个整数，通过这个整数可以还原出元素的坐标，整数越长，还原出来的坐标值的损失程度就越小。对于「附近的人」这个功能而言，损失的一点精确度可以忽略不计。

GeoHash算法会对上述编码的整数继续做一次base32编码(0 ~ 9,a ~ z)变成一个字符串。Redis中经纬度使用52位的整数进行编码，放进zset中，zset的value元素是key，score是GeoHash的52位整数值。在使用Redis进行Geo查询时，其内部对应的操作其实只是zset(skiplist)的操作。通过zset的score进行排序就可以得到坐标附近的其它元素，通过将score还原成坐标值就可以得到元素的原始坐标

总之，Redis中处理这些地理位置坐标点的思想是: 二维平面坐标点 --> 一维整数编码值 --> zset(score为编码值) --> zrangebyrank(获取score相近的元素)、zrangebyscore --> 通过score(整数编码值)反解坐标点 --> 附近点的地理位置坐标。
```



## 2.7 bit位操作 - 大数据

​	主要是用来处理大数据，通过位操作速度更快；

场景：用于数据量上亿的场景下，例如几亿用户系统的签到，去重登录次数统计，某用户是否在线状态等等。

​		想想一下腾讯10亿用户，要几个毫秒内查询到某个用户是否在线，你能怎么做？千万别说给每个用户建立一个key，然后挨个记（你可以算一下需要的内存会很恐怖，而且这种类似的需求很多，腾讯光这个得多花多少钱。。）好吧。这里要用到位操作——使用setbit、getbit、bitcount命令。

### 1）存储原理

​	bit位存储采用的SDS存储，只是字节数组中的一个字节又划分为了位存储而已。动态扩展

### 2）命令操作

​			Redis提供了SETBIT、GETBIT、BITCOUNT、BITOP四个命令用于处理二进制位数组（bit array，又称“位数组”）。BITOP计算出来的任然是个二进制位数组
​			其中，SETBIT命令用于为位数组指定偏移量上的二进制位设置值，位数组的偏移量从0开始计数，而二进制位的值则可以是0或者1：

```bash
# SETBIT key offset value     value只能是1或者0

redis＞ SETBIT bit 0 1  		# 0000 0001￼
(integer) 0￼
redis＞ SETBIT bit 3 1        # 0000 1001￼
(integer) 0￼
redis＞ SETBIT bit 0 0        # 0000 1000￼
(integer) 1
```

GETBIT命令则用于获取位数组指定偏移量上的二进制位的值：

```bash
## GETBIT key offset   

redis＞ GETBIT bit 0			 # 0000 1000￼
(integer) 0￼
redis＞ GETBIT bit 3 		 # 0000 1000￼
(integer) 1￼
```

BITCOUNT命令用于统计位数组里面，值为1的二进制位的数量：

```bash
redis＞ BITCOUNT bit  		 # 0000 1000￼
(integer) 1￼
redis＞ SETBIT bit 0 1        # 0000 1001￼
(integer) 0￼
redis＞ BITCOUNT bit￼
(integer) 2￼
redis＞ SETBIT bit 1 1        # 0000 1011￼
(integer) 0￼
redis＞ BITCOUNT bit￼
(integer) 3
```

最后，BITOP命令既可以对多个位数组进行按位与（and）、按位或（or）、按位异或（xor）运算：

```bash
# BITOP operation destkey key [key ...] 
# operation 可以是 AND 、 OR 、 NOT 、 XOR 这四种操作中的任意一种, NOT只支持一个key

redis＞ SETBIT x 3 1        # x = 0000 1011￼
(integer) 0￼
redis＞ SETBIT x 1 1￼
(integer) 0￼
redis＞ SETBIT x 0 1￼
(integer) 0￼
redis＞ SETBIT y 2 1       # y = 0000 0110￼
(integer) 0￼
redis＞ SETBIT y 1 1￼
(integer) 0￼
redis＞ SETBIT z 2 1       # z = 0000 0101￼
(integer) 0￼
redis＞ SETBIT z 0 1￼
(integer) 0￼
redis＞ BITOP AND and-result x y z       # 0000 0000￼
(integer) 1￼
redis＞ BITOP OR or-result x y z         # 0000 1111￼
(integer) 1￼
redis＞ BITOP XOR xor-result x y z       # 0000 1000￼
(integer) 1
```

也可以对给定的位数组进行取反（not）运算：

```bash
redis＞ SETBIT value 0 1                 # 0000 1001￼
(integer) 0￼
redis＞ SETBIT value 3 1￼
(integer) 0￼
redis＞ BITOP NOT not-value value     	# 1111 0110￼
(integer) 1
```



## 2.8 Sort对集合进行排序

​		Redis的SORT命令可对列表键，集合键或者有序集合进行再次排序，排序不会对原来的集合影响，会重新建立一个数组用来存储排序后的结果，排序后的结果可以通过limit，desc/asc，by类似sq进行相关处理

具体参考官网：<https://redis.io/commands/sort> 



# 3.对象共享

让多个键共享一个值的对象，包含上面各种类型的指针指向这个的对象

- 将数据库键的值指针指向一个现有的值对象

- 将被共享的值对象的应用计数法增一（主要是用来内存回收用的，和Java的可达性分析算法不同）

  

​		目前来说redis会在初始化服务器创建一万个字符串对象，这些对象包含了0-9999的所有整数值，当服务器需要用到这些字符串对象时，就会共享这些对象而不是新创建对象。

```bash
redis> object refcount key
//可以通过这个命令获取当前key被引用的次数
```



# 4.对象空转时长

redisObject结构中还包含了lru属性，记录了对象最后一次被命令程序访问的时间，

```bash
redis> object idletime key
//这个可以答应出该键的空转时长，即没有读写该键
```

这个和服务器的内存回收算法volatile-lru和allkeys-lru有关系



# 5.慢查询日志

Redis提供慢查询日志用来记录执行时间超过给定时间的命令请求，服务器有两个配置选项

```properties
# 选项指定执行时间超过多少微秒（1秒等于1 000 000微秒）的命令请求会被记录到日志上。
slowlog-log-slower-than

# 选项指定服务器最多保存多少条慢查询日志，后来的日志替换之前的日志
slowlog-max-len
```

通过`config`命令可以直接实现配置操作，具体参考官网：<https://redis.io/commands/config-set>

```bash
#设置执行时间操作多少微秒存储数据
redis>config set slowlog-log-slower-than 10000

redis>config get slowlog-log-slower-than 

#设置最长日志条数
redis>config set slowlog-max-len 10000

redis>config get slowlog-max-len
```

通过`slowlog` 管理慢查询日志信息，具体参考官网：<https://redis.io/commands/slowlog>

```bash
#slowlog获取慢查询日志信息
redis>slowlog get 

#slowlog 获取慢查询日志信息长度
redis>slowlog len

#slowlog 清空慢查询日志信息
redis>slowlog reset
```



# 6.监视器

​	客户端通过监视器可以检测服务器现在正在处理的命令，操作有如下几种方式：

```bash
localhsot:1> redis-cli -a 123456 monitor
//展示
1339518083.107412 [0 127.0.0.1:60866] "keys" "*"
1339518087.877697 [0 127.0.0.1:60866] "dbsize"
```



```bash
$ telnet localhost 6379
Trying 127.0.0.1...
Connected to localhost.
Escape character is '^]'.
MONITOR
+OK
+1339518083.107412 [0 127.0.0.1:60866] "keys" "*"
```

**监视器的原理：**

​			监视器服务器的实现，是在每个命令执行完毕后，遍历有监视器列表并向每个监视器发送信息，这样就会造成服务器压力较大，就一台监视器看来，通过redis-benchmark测试至少性能降低一半，具体见官网：<https://redis.io/commands/monitor> 



