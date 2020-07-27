## 1.Hbase概念

### 1.1 **HBase定义**

HBase是一种分布式、可扩展、支持海量数据存储的NoSQL数据库。

Hbase面向列存储，构建于Hadoop之上，类似于Google的BigTable，提供对10亿级别表数据的快速随机实时读写！



### 1.2 Hbase的特点

Hbase主要特点有：海量存储，列式存储，极易扩展，高并发，稀疏。

**1）海量存储**

​		HBase适合存储PB级别的海量数据，在PB级别的数据以及采用廉价PC存储的情况下，能在几十到百毫秒内返回数据。这与HBase的极易扩展性息息相关。正式因为HBase良好的扩展性，才为海量数据的存储提供了便利。

**2）列式存储**

​		这里的列式存储其实说的是列族存储，HBase是根据列族来存储数据的。列族下面可以有非常多的列，列族在创建表的时候就必须指定。

**3）极易扩展**

​		HBase的扩展性主要体现在两个方面，一个是基于上层处理能力（RegionServer）的扩展，一个是基于存储的扩展（HDFS）。

​		通过横向添加RegionSever的机器，进行水平扩展，提升HBase上层的处理能力，提升Hbsae服务更多Region的能力。

**4）高并发**

​		由于目前大部分使用HBase的架构，都是采用的廉价PC，因此单个IO的延迟其实并不小，一般在几十到上百ms之间。这里说的高并发，主要是在并发的情况下，HBase的单个IO延迟下降并不多。能获得高并发、低延迟的服务。

**5）稀疏**

​		稀疏主要是针对HBase列的灵活性，在列族中，你可以指定任意多的列，在列数据为空的情况下，是不会占用存储空间的。这个和数据库不同，数据库中数据为NULL是占用空间的





### 1.3  Hbase优缺点

#### 1) Hbase的优点

①HDFS有高容错，高扩展的特点，而Hbase基于HDFS实现数据的存储，因此Hbase拥有与生俱来的超强的扩展性和吞吐量。

②HBase采用的是Key/Value的存储方式，这意味着，即便面临海量数据的增长，也几乎不会导致查询性能下降。

③HBase是一个列式数据库，相对于于传统的行式数据库而言。当你的单张表字段很多的时候，可以将相同的列(以regin为单位)存在到不同的服务实例上，分散负载压力。



#### 2) Hbase 缺点

①架构设计复杂，且使用HDFS作为分布式存储，因此只是存储少量数据，它也不会很快。在大数据量时，它慢的不会很明显！

②Hbase不支持表的关联操作，因此数据分析是HBase的弱项。常见的 group by或order by只能通过编写MapReduce来实现！

③Hbase部分支持了ACID



### 1.4 Hbase的总结

​	适合场景：单表超千万，上亿，且高并发！

不适合场景：主要需求是数据分析，比如做报表。数据量规模不大，对实时性要求高！



## 2.Hbase 数据模型

​			逻辑上，HBase的数据模型同关系型数据库很类似，数据存储在一张表中，有行有列。但从HBase的底层物理存储结构（K-V）来看，HBase更像是一个multi-dimensional map。



### 2.1 Name Space

​		命名空间，类似于关系型数据库的database概念，每个命名空间下有多个表。HBase两个自带的命名空间，分别是hbase和default，hbase中存放的是HBase内置的表，default表是用户默认使用的命名空间。

一个表可以自由选择是否有命名空间，如果创建表的时候加上了命名空间后，这个表名字以<Namespace>:<Table>作为区分！

### 2.2 Table

类似于关系型数据库的表概念。不同的是，HBase定义表时只需要声明**列族**即可，数据属性，比如超时时间（TTL），压缩算法（COMPRESSION）等，都在列族的定义中定义，不需要声明具体的列。

这意味着，往HBase写入数据时，字段可以**动态、按需**指定。因此，和关系型数据库相比，HBase能够轻松应对字段变更的场景。

### 2.3 Row

HBase表中的每行数据都由一个RowKey和多个Column（列）组成。一个行包含了多个列，这些列通过列族来分类,行中的数据所属列族只能从该表所定义的列族中选取,不能定义这个表中不存在的列族，否则报错NoSuchColumnFamilyException。

### 2.4 RowKey

Rowkey由用户指定的一串不重复的字符串定义，是一行的唯一标识！数据是按照RowKey的字典顺序存储的，并且查询数据时只能根据RowKey进行检索，所以RowKey的设计十分重要。

如果使用了之前已经定义的RowKey，那么会将之前的数据更新掉！



### 2. 5 Column Family

列族是多个列的集合。一个列族可以动态地灵活定义多个列。表的相关属性大部分都定义在列族上，同一个表里的不同列族可以有完全不同的属性配置，但是同一个列族内的所有列都会有相同的属性。

列族存在的意义是HBase会把相同列族的列尽量放在同一台机器上，所以说，如果想让某几个列被放到一起，你就给他们定义相同的列族。

官方建议一张表的列族定义的越少越好，列族太多会极大程度地降低数据库性能，且目前版本Hbase的架构，容易出BUG。

### 2.6 **Column** Qualifier

Hbase中的列是可以随意定义的，一个行中的列不限名字、不限数量，只限定列族。因此列必须依赖于列族存在！列的名称前必须带着其所属的列族！例如info：name，info：age。

因为HBase中的列全部都是灵活的，可以随便定义的，因此创建表的时候并不需要指定列！列只有在你插入第一条数据的时候才会生成。其他行有没有当前行相同的列是不确定，只有在扫描数据的时候才能得知！



### 2.7 TimeStamp

用于标识数据的不同版本（version）。时间戳默认由系统指定，也可以由用户显式指定。

在读取单元格的数据时，版本号可以省略，如果不指定，Hbase默认会获取最后一个版本的数据返回！

### 2.8 Cell

一个列中可以存储多个版本的数据。而每个版本就称为一个单元格（Cell）。

Cell由{rowkey, column Family：column Qualifier, time Stamp}确定。

Cell中的数据是没有类型的，全部是字节码形式存贮。



### 2.9 Region

​	Region由一个表的若干行组成！在Region中行的排序按照行键（rowkey）字典排序。

Region不能跨RegionSever，一个Region只能由一个RegionServer管理。RegionServer就类似HDFS中的nameserver；且当数据量大的时候，HBase会拆分Region。

Region由RegionServer进程管理。HBase在进行负载均衡的时候，一个Region有可能会从当前RegionServer移动到其他RegionServer上。Region是基于HDFS的，它的所有数据存取操作都是调用了HDFS的客户端接口来实现的。



## 3.Hbase的存储结构

store是 部分行的 相同列簇数据

Region就是表中的若干行数据

Storefile是一个Row Key的数据，可能宝行多个版本的数据。 

![Uxgnje.png](https://s1.ax1x.com/2020/07/25/Uxgnje.png)

![Uxg3Nt.png](https://s1.ax1x.com/2020/07/25/Uxg3Nt.png)



## 4.Hbase架构

### 4.1 基础架构

![Ux2RRf.png](https://s1.ax1x.com/2020/07/25/Ux2RRf.png)

![UxRWkR.png](https://s1.ax1x.com/2020/07/25/UxRWkR.png)



**架构角色：**

**1）Region** **Server**

RegionServer是一个服务，负责多个Region的管理。其实现类为HRegionServer，主要作用如下:

- 对于数据的操作：get, put, delete；

- 对于Region的操作：splitRegion、compactRegion。

- 客户端从ZooKeeper获取RegionServer的地址，从而调用相应的服务，获取数据。

**2）Master**

Master是所有Region Server的管理者，其实现类为HMaster，主要作用如下：

​	对于表的操作：create, delete, alter，这些操作可能需要跨多个ReginServer，因此需要Master来进行协调！

对于RegionServer的操作：分配regions到每个RegionServer，监控每个RegionServer的状态，负载均衡和故障转移。

即使Master进程宕机，集群依然可以执行数据的读写，只是不能进行表的创建和修改等操作！当然Master也不能宕机太久，有很多必要的操作，比如创建表、修改列族配置，以及更重要的分割和合并都需要它的操作。

**3）Zookeeper**

RegionServer非常依赖ZooKeeper服务，ZooKeeper管理了HBase所有RegionServer的信息，包括具体的数据段存放在哪个RegionServer上。

客户端每次与HBase连接，其实都是先与ZooKeeper通信，查询出哪个RegionServer需要连接，然后再连接RegionServer。Zookeeper中记录了读取数据所需要的元数据表

hbase:meata,因此关闭Zookeeper后，客户端是无法实现读操作的！

HBase通过Zookeeper来做master的高可用、RegionServer的监控、元数据的入口以及集群配置的维护等工作。

**4）HDFS**

HDFS为Hbase提供最终的底层数据存储服务，同时为HBase提供高可用的支持。



### 4.2 详细架构

![UxRuTA.png](https://s1.ax1x.com/2020/07/25/UxRuTA.png)

**1）StoreFile**

保存实际数据的物理文件，StoreFile以Hfile的形式存储在HDFS上。每个Store会有一个或多个StoreFile（HFile），数据在每个StoreFile中都是有序的。

**2）MemStore**

写缓存，由于HFile中的数据要求是有序的，所以数据是先存储在MemStore中，排好序后，等到达刷写时机才会刷写到HFile，每次刷写都会形成一个新的HFile。

**3）WAL**

由于数据要经MemStore排序后才能刷写到HFile，但把数据保存在内存中会有很高的概率导致数据丢失，为了解决这个问题，数据会先写在一个叫做Write-Ahead logfile的文件中，然后再写入MemStore中。所以在系统出现故障的时候，数据可以通过这个日志文件重建。

每间隔hbase.regionserver.optionallogflushinterval(默认1s)， HBase会把操作从内存写入WAL。 

一个RegionServer上的所有Region共享一个WAL实例。

WAL的检查间隔由hbase.regionserver.logroll.period定义，默认值为1小时。检查的内容是把当前WAL中的操作跟实际持久化到HDFS上的操作比较，看哪些操作已经被持久化了，被持久化的操作就会被移动到.oldlogs文件夹内（这个文件夹也是在HDFS上的）。一个WAL实例包含有多个WAL文件。WAL文件的最大数量通过hbase.regionserver.maxlogs（默认是32）参数来定义。

**4）BlockCache**

读缓存，每次查询出的数据会缓存在BlockCache中，方便下次查询。



### 4.3 写流程

![UxRcm4.png](https://s1.ax1x.com/2020/07/25/UxRcm4.png)

写流程：

1）Client先访问zookeeper，获取hbase:meta表位于哪个Region Server。

2）访问对应的Region Server，获取**hbase:meta表**，根据读请求的namespace:table/rowkey，查询出目标数据位于哪个Region Server中的哪个Region中。并将该table的region信息以及meta表的位置信息缓存在客户端的meta cache，方便下次访问。

3）与目标Region Server进行通讯；

4）将数据顺序写入（追加）到WAL；

5）将数据写入对应的MemStore，数据会在MemStore进行排序；

6）向客户端发送ack；

7）等达到MemStore的刷写时机后，将数据刷写到HFile。



**微观写流程**

```
①获取到锁
②生成数据的时间戳
③构建WAL对象
④讲数据写入到WAL的buffer中
⑤讲数据写入到memstore中3
⑥将数据从WAL的buffer，sync到磁盘
⑦如果成功，滚动MVCC，客户端可见，写成功
⑧否则，回滚之前已经写入到memstore中的数据，写入失败
```



### 4.4 读流程

![UxWRKS.png](https://s1.ax1x.com/2020/07/25/UxWRKS.png)

**读流程**

1）Client先访问zookeeper，获取hbase:meta表位于哪个Region Server。

2）访问对应的Region Server，获取hbase:meta表，根据读请求的namespace:table/rowkey，查询出目标数据位于哪个Region Server中的哪个Region中。并将该table的region信息以及meta表的位置信息缓存在客户端的meta cache，方便下次访问。

3）与目标Region Server进行通讯；

4）分别在Block Cache（读缓存），MemStore和Store File（HFile）中查询目标数据，并将查到的所有数据进行合并。此处所有数据是指同一条数据的不同版本（time stamp）或者不同的类型（Put/Delete）。

5）将查询到的数据块（Block，HFile数据存储单元，默认大小为64KB）缓存到Block Cache。

6）将合并后的最终结果返回给客户端





### 4.5 MemStore Flush

**MemStore**存在的意义是在写入HDFS前，将其中的数据整理有序。

#### 1) 手工刷新

我们可以通过 手工命令刷新表，我们在建立列名的时候设置了VERSIONS参数，默认1；在我们修改某个列数据时，数据都是存储在MemStore中，在我们flush后就会清掉多余的数据，VERSIONS的数值表示把MemStore中数据刷新到HDFS中需要保存的最新几条记录，如果没有flush前，我们通过scan可以查询到memstore中和hdfs中的数据，如果flush后会把多余的记录删除掉，那么只会剩下VERSIONS个最新的数据刷新到hfile中。

当然已经刷新到HFILE中的数据个数会不断增加，VERSIONS只是控制每次从memstore刷新的个数。

```
flush '表名' 
```



#### 2）MemStore自动刷写时机

- 1.当**某个memstore**的大小达到了**hbase.hregion.memstore.flush.size**（默认值128M），其所在region的所有memstore都会刷写。当memstore的大小达到了（128M*4）如下数据时，会阻止继续往该memstore写数据。

```
hbase.hregion.memstore.flush.size（默认值128M）
乘以  hbase.hregion.memstore.block.multiplier（默认值4）
```



- 2.**当region server**中memstore的总大小达到如下三个数相乘

```
java_heapsize
乘以   hbase.regionserver.global.memstore.size（默认值0.4）
乘以  hbase.regionserver.global.memstore.size.lower.limit（默认值0.95），
```

region会按照其所有memstore的大小顺序（由大到小）依次进行刷写。直到region server中所有memstore的总大小减小到上述值以下。



当region server中memstore的总大小达到下列两个值相乘的结果时，会阻止继续往所有的memstore写数据。

```
java_heapsize 
乘以   hbase.regionserver.global.memstore.size（默认值0.4）
```



- 3.到达**自动刷写的时间**，也会触发memstore flush。自动刷新的时间间隔由该属性进行配置

  ```
  hbase.regionserver.optionalcacheflushinterval（默认1小时）。
  ```

  

- 4.当WAL文件的数量超过**hbase.regionserver.max.logs**，region会按照时间顺序依次进行刷写，直到WAL文件数量减小到**hbase.regionserver.max.log**以下（该属性名已经废弃，现无需手动设置，最大值为32）。



### 4.6 Storefile  Compaction

​		由于Hbase依赖HDFS存储，HDFS只支持追加写。所以，当新增一个单元格的时候，HBase在HDFS上新增一条数据。当修改一个单元格的时候，HBase在HDFS又新增一条数据，只是版本号比之前那个大（或者自定义）。当删除一个单元格的时候，HBase还是新增一条数据！只是这条数据没有value，类型为DELETE，也称为墓碑标记（Tombstone）

HBase每间隔一段时间都会进行一次合并（Compaction），合并的对象为HFile文件。合并分为两种

minor compaction和major compaction。

![aSg141.png](https://s1.ax1x.com/2020/07/25/aSg141.png)

----



在HBase进行major compaction的时候，它会把多个HFile合并成1个HFile，在这个过程中，一旦检测到有被打上墓碑标记的记录，在合并的过程中就忽略这条记录。这样在新产生的HFile中，就没有这条记录了，自然也就相当于被真正地删除了

由于memstore每次刷写都会生成一个新的HFile，且同一个字段的不同版本（timestamp）和不同类型（Put/Delete）有可能会分布在不同的HFile中，因此查询时需要遍历所有的HFile。为了减少HFile的个数，以及清理掉过期和删除的数据，会进行StoreFile Compaction。



Compaction分为两种，分别是Minor Compaction和Major Compaction。

- Minor Compaction会将临近的若干个较小的HFile合并成一个较大的HFile，但**不会**清理过期和删除的数据。
- Major Compaction会将一个Store下的所有的HFile合并成一个大HFile，并且**会**清理掉过期和删除的数据。



参数相关配置参考同目录下flush_compact.xml文件



### 4.7 Region Split

​		默认情况下，每个Table起初只有一个Region，随着数据的不断写入，Region会自动进行拆分。刚拆分时，两个子Region都位于当前的Region Server，但处于负载均衡的考虑，HMaster有可能会将某个Region转移给其他的Region Server。

#### 1）手动命令切分

```
help "split"
```



#### 2）Region Split自动切分

​	1.当1个region中的某个Store下所有StoreFile的总大小超过`hbase.hregion.max.filesize`，该Region就会进行拆分（0.94版本之前）。

   2.0.94版本之后的切分策略

​	切分策略取决于`hbase.regionserver.region.split.policy`的参数配置， 默认使用`IncreasingToUpperBoundRegionSplitPolicy`策略切分region, `getSizeToCheck()`是检查region的大小以判断是否满足切割切割条件。源码分析：

```java
  protected long getSizeToCheck(final int tableRegionsCount) {
    // safety check for 100 to avoid numerical overflow in extreme cases
    return tableRegionsCount == 0 || tableRegionsCount > 100
               ? getDesiredMaxFileSize()
               : Math.min(getDesiredMaxFileSize(),
                          initialSize * tableRegionsCount * tableRegionsCount * tableRegionsCount);
  }

// tableRegionsCount：为当前Region Server中属于该Table的region的个数。
// getDesiredMaxFileSize() 这个值是hbase.hregion.max.filesize参数值，默认为10GB。
// initialSize的初始化比较复杂，由多个参数决定。
```

// 从上面分析需要确定在表的region数量在0-100时，那么initialSize就需要确定了，如下：

```java
  @Override
  protected void configureForRegion(HRegion region) {
    super.configureForRegion(region);
    Configuration conf = getConf();
      //默认hbase.increasing.policy.initial.size 没有在配置文件中指定
    initialSize = conf.getLong("hbase.increasing.policy.initial.size", -1);
    if (initialSize > 0) {
      return;
    }
      // 获取用户表中自定义的memstoreFlushSize大小，默认也为128M
    TableDescriptor desc = region.getTableDescriptor();
    if (desc != null) {
      initialSize = 2 * desc.getMemStoreFlushSize();
    }
      // 判断用户指定的memstoreFlushSize是否合法，如果不合法，则为hbase.hregion.memstore.flush.size，默认为128. 
    if (initialSize <= 0) {
      initialSize = 2 * conf.getLong(HConstants.HREGION_MEMSTORE_FLUSH_SIZE,
                                     TableDescriptorBuilder.DEFAULT_MEMSTORE_FLUSH_SIZE);
    }
  }
```

所以具体的切分策略就是：

```
1)tableRegionsCount在0和100之间，则为initialSize（默认为2*128） *  tableRegionsCount^3,
例如：
	第一次split：1^3 * 256 = 256MB 
	第二次split：2^3 * 256 = 2048MB 
	第三次split：3^3 * 256 = 6912MB 
	第四次split：4^3 * 256 = 16384MB > 10GB，因此取较小的值10GB 
	后面每次split的size都是10GB了。

2)tableRegionsCount超过100个，则超过10GB才会切分region。
```



