# 1.Hbase高可用

```shell
regionserver由master负责高可用，一个regionserver挂掉，它负责的region会自动分配给其他的regionserver!

需要配置的是master的高可用！需要在conf/backup-masters,列出备用的master!


#默认没有这个目录，里面配置其他master地址，然后重启所有的服务即可，
[ HBase]$ touch conf/backup-masters
```


# 2.Hbase的预分区

​		每一个region维护着startRow与endRowKey，如果加入的数据符合某个region维护的rowKey范围，则该数据交给这个region维护。那么依照这个原则，我们可以将数据所要投放的分区提前大致的规划好，以提高HBase性能。

**目的：**
		通常情况下，每次建表时，默认只有一个region!随着这个region的数据不断增多，region会自动切分！

	自动切分： 讲当前region中的所有的rowkey进行排序，排序后取start-key和stopkey中间的rowkey,
		由这个rowkey一分为二，一分为二后，生成两个region，新增的Region会交给其他的RS负责，
		目的是为了达到负载均衡，但是通常往往会适得其反！
		
	为例避免某些热点Region同时分配到同一个RegionServer，可以在建表时，自己提前根据数据的特征规划region！

**注意：**
		如果使用HexStringSplit算法，随机生成region的边界！
		在插入一条数据时，rowkey必须先采取HexString，转为16进制，再插入到表中！



## 1）手动分区

```mysql
#按照rowkey分成的region 0-1000,1000-2000,...,40000- 

HBase> create 'staff1','info','partition1',SPLITS => ['1000','2000','3000','4000']
```



## 2）16进制算法生成预分区

```mysql
#使用16进制算法生成预分区,根据rowkey的分区会生成0-11111111,11111111-22222222....,dddddddd-eeeeeeee,eeeeeeee- 
create 'staff2','info','partition2',{NUMREGIONS => 15, SPLITALGO => 'HexStringSplit'}
```



## 3）分区规则放在文件中

```mysql
splits.txt:
	aaaa
	bbbb
	cccc
	dddd
		
# 生成的分区为 - aaaa,aaaa-bbbb,....,cccc-dddd
create 'table2','partition2',SPLITS_FILE => 'splits.txt'
```

## 4） Java代码设置分区

```java
//自定义算法，产生一系列Hash散列值存储在二维数组中
byte[][] splitKeys = 某个散列值函数

//创建HBaseAdmin实例
HBaseAdmin hAdmin = new HBaseAdmin(HBaseConfiguration.create());

//创建HTableDescriptor实例
HTableDescriptor tableDesc = new HTableDescriptor(tableName);

//通过HTableDescriptor实例和散列值二维数组创建带有预分区的HBase表
hAdmin.createTable(tableDesc, splitKeys);
```




# 3.Rowkey的设计

​		一条数据的唯一标识就是rowkey，那么这条数据存储于哪个分区，取决于rowkey处于哪个一个预分区的区间内，设计rowkey的主要目的 ，就是让数据均匀的分布于所有的region中，在一定程度上防止数据倾斜。接下来我们就谈一谈rowkey常用的设计方案。

## 3.1 Rowkey长度原则

Rowkey是一个二进制码流，Rowkey的长度被很多开发者建议设计在10-100个字节，不过建议是越短越好，不要超过16个字节。

## 3.2 Rowkey散列原则

​	如果Rowkey是按时间戳的方式递增，不要将时间放在二进制码的前面，建议将Rowkey的高位作为散列字段，由程序循环生成，低位放时间字段，这样将提高数据均衡分布在每个Regionserver实现负载均衡的几率。

## 3.3 Rowkey唯一原则

​	必须在设计Rowkey上保证其唯一性。



## 3.4 案例

```text
例如： 转账的场景
	流水号 转入账户 转出账户 时间 金额 用户	
	流水号适合作为rowkey,讲流水号再拼接字符串，生成完整的rowkey!
		格式：  流水号+时间戳   时间戳+流水号
				流水号+随机数	随机数+流水号
				
		如果流水号在设计时，足够散列，可以使用流水号在前，拼接随机数！
		如果流水号不够散列，可以使用函数计算其散列值，或拼接一个散列的值！
		
举例:如何让一个月的数据，分布到同一个Region！可以取月份的时间，作为计算的参数，
			使用hash运算，讲运算后的字符串，拼接到rowkey前部！
```





# 4.内存分配

​		HBase操作过程中需要大量的内存开销，毕竟Table是可以缓存在内存中的，一般会分配整个可用内存的70%给HBase的Java堆。但是不建议分配非常大的堆内存，因为GC过程持续太久会导致RegionServer处于长期不可用状态，一般16~48G内存就可以了，如果因为框架占用内存过高导致系统内存不足，框架一样会被系统服务拖死。

```shell
在conf/hbase-env.sh 中，编写regionserver进程启动时的JVM参数！
	-Xms : JVM堆的起始值
	-Xmx : JVM堆的最大值
	
export HBASE_HEAPSIZE=1G 或
export HBASE_OPTS="-XX:+UseConcMarkSweepGC"
```

# 5.其他参数的配置

```mysql
1．允许在HDFS的文件中追加内容
hdfs-site.xml、HBase-site.xml
属性：dfs.support.append
解释：开启HDFS追加同步，可以优秀的配合HBase的数据同步和持久化。默认值为true。

2．优化DataNode允许的最大文件打开数
hdfs-site.xml
属性：dfs.datanode.max.transfer.threads
解释：HBase一般都会同一时间操作大量的文件，根据集群的数量和规模以及数据动作，设置为4096或者更高。默认值：4096

3．优化延迟高的数据操作的等待时间
hdfs-site.xml
属性：dfs.image.transfer.timeout
解释：如果对于某一次数据操作来讲，延迟非常高，socket需要等待更长的时间，建议把该值设置为更大的值（默认60000毫秒），以确保socket不会被timeout掉。

4．优化数据的写入效率
mapred-site.xml
属性：
mapreduce.map.output.compress
mapreduce.map.output.compress.codec
解释：开启这两个数据可以大大提高文件的写入效率，减少写入时间。第一个属性值修改为true，第二个属性值修改为：org.apache.hadoop.io.compress.GzipCodec或者其他压缩方式。

5．设置RPC监听数量
HBase-site.xml
属性：HBase.regionserver.handler.count
解释：默认值为30，用于指定RPC监听的数量，可以根据客户端的请求数进行调整，读写请求较多时，增加此值。

6．优化HStore文件大小
HBase-site.xml
属性：HBase.hregion.max.filesize
解释：默认值10737418240（10GB），如果需要运行HBase的MR任务，可以减小此值，因为一个region对应一个map任务，如果单个region过大，会导致map任务执行时间过长。该值的意思就是，如果HFile的大小达到这个数值，则这个region会被切分为两个Hfile。

7．优化HBase客户端缓存
HBase-site.xml
属性：HBase.client.write.buffer
解释：用于指定HBase客户端缓存，增大该值可以减少RPC调用次数，但是会消耗更多内存，反之则反之。一般我们需要设定一定的缓存大小，以达到减少RPC次数的目的。

8．指定scan.next扫描HBase所获取的行数
HBase-site.xml
属性：HBase.client.scanner.caching
解释：用于指定scan.next方法获取的默认行数，值越大，消耗内存越大。

9．flush、compact、split机制
当MemStore达到阈值，将Memstore中的数据Flush进Storefile；compact机制则是把flush出来的小文件合并成大的Storefile文件。split则是当Region达到阈值，会把过大的Region一分为二。
涉及属性：
即：128M就是Memstore的默认阈值
HBase.hregion.memstore.flush.size：134217728
即：这个参数的作用是当单个HRegion内所有的Memstore大小总和超过指定值时，flush该HRegion的所有memstore。RegionServer的flush是通过将请求添加一个队列，模拟生产消费模型来异步处理的。那这里就有一个问题，当队列来不及消费，产生大量积压请求时，可能会导致内存陡增，最坏的情况是触发OOM。
HBase.regionserver.global.memstore.upperLimit：0.4
HBase.regionserver.global.memstore.lowerLimit：0.38
即：当MemStore使用内存总量达到HBase.regionserver.global.memstore.upperLimit指定值时，将会有多个MemStores flush到文件中，MemStore flush 顺序是按照大小降序执行的，直到刷新到MemStore使用内存略小于lowerLimit

```



# 6. HBase在商业项目中的能力

每天：
	1) 消息量：发送和接收的消息数超过60亿
	2) 将近1000亿条数据的读写
	3) 高峰期每秒150万左右操作
	4) 整体读取数据占有约55%，写入占有45%
	5) 超过2PB的数据，涉及冗余共6PB数据
	6) 数据每月大概增长300千兆字节。

# 7.布隆过滤器

​	布隆是个人，发明了布隆算法，基于布隆算法实现的组件，称为布隆过滤器！
这个组件一般是用作过滤！Bloom Filter通过极少的错误换取了存储空间的极大节省。
​	

```
过滤功能：  在海量数据中，用非常高的效率和性能，判断一个数据是否在集合中存在！		
作用： 布隆过滤器只能判断一个数据要么一定在集合中不存在，要么在集合中可能存在！
误判：  布隆过滤器判断数据可能存在，实际扫描后，发现不存在，这种情况有存在的几率！
```

Bloom Filter是一种空间效率很高的随机数据结构，它利用位数组很简洁地表示一个集合，

- 1）然后将我们的ekey通过多个哈希函数散列到不同的位上置为1，后面的数据散列到相同位上不变
- 2）为了查询一个元素，即判断它是否在集合中，同样通过多个哈希函数计算值判定所有的位上是否为1，如果都为1那么可能存在，否则一定不存在
  		
- 3) 注意：		
  	不允许remove元素，因为那样的话会把相应的k个bits位置为0，而其中很有可能有其他元素对应的位。因此remove会引入false negative，这是绝对不被允许的。

	## 7.1 Hbase中的布隆过滤器

		HBase中通过列族设置过滤器。HBase支持两种布隆过滤器：  ROW|ROWCOL
		
	查看布隆过滤器describe taleName ; 有一个BLOOMFILTER关键字来设置	
		
	1）ROW: 布隆过滤器在计算时，使用每行的rowkey作为参数，进行判断！
		
		举例：   		info				info1
		
		info storefile1: (r1,info:age,20) ,(r2,info:age,20) 
		info1 storefile2: (r3,info1:age,20) ,(r4,info1:age,20) 
		
		查询r1时，如果命中，判断storefile2中一定没有r1的数据，在storefile1中可能有！


​		
	2）ROWCOL: 布隆过滤器在计算时，使用每行的rowkey和column一起作为参数，进行判断！
		
		举例：   		info				info1
		
		info storefile1: (r1,info:age,20) ,(r2,info:age,20) 
		info1 storefile2: (r3,info1:age,20) ,(r4,info1:age,20) 
		
		查询rowkey=r1，只查info:age=20 列时，如果命中，判断storefile2中一定没有此数据，
		在storefile1中可能有！


​		
	注意： 旧版本，只有get操作，才会用到布隆过滤器，scan用不到！
				1.x之后，scan也可用用布隆过滤器，稍微起点作用！
				
				启用布隆过滤器后，会占用额外的内存，布隆过滤器通常是在blockcache和memstore中！
				
	举例： 执行  get 't1','r1'
		
			①扫描r1所在region的所有列族的memstore，扫memstore时，先通过布隆过滤器判断r1是否
			存在，如果不存在，就不扫！可能存在，再扫描！
			
			②扫描Storefile时，如果storefile中,r1所在的block已经缓存在blockcache中，直接扫blockcache
			在扫描blockcache时，先使用布隆过滤器判断r1是否存在，如果不存在，就不扫！可能存在，再扫描！
 