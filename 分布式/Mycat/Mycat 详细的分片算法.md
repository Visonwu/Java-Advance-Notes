

Mycat值rule.xml

总体上分为连续分片和离散分片，还有一种是连续分片和离散分片的结合，例如先范围后取模。

|        | 连续分片                                               | 离散分片                                                     | 综合类分片                    |
| ------ | ------------------------------------------------------ | ------------------------------------------------------------ | ----------------------------- |
| 优点： | 扩容无需迁移数据，范围条件查询资源消耗小               | 数据分布均匀，并发能力强，不受限分片节点                     |                               |
| 缺点： | 数据热点问题，并发能力受限于分片节点                   | 移植性差，扩容难                                             |                               |
| 代表： | 按日期（天）分片<br/>自定义数字范围分片<br/>自然月分片 | 枚举分片
数字取模分片
字符串数字hash分片
一致性哈希分片
程序指定 | 范围求模分片 
取模范围约束分片 |

# 一、连续分片



## 1. 自定义数字范围分片

​	提前规划好分片字段某个范围属于哪个分片

```xml
<function name="rang-long"
		class="io.mycat.route.function.AutoPartitionByLong">
	<property name="mapFile">autopartition-long.txt</property>
	<property name="defaultNode">0</property> 
</function>
```

defaultNode 超过范围后的默认节点。

此配置非常简单，即预先制定可能的id范围到某个分片

```text
#autopartion-long.txt文件内容
0-500M=0
500M-1000M=1
1000M-1500M=2
或
0-10000000=0
10000001-20000000=1
```


注意： 所有的节点配置都是从0开始，及0代表节点1



## 2.按日期（天）分片

 从开始日期算起，按照天数来分片

```xml
<function name=“sharding-by-date” class=“io.mycat.route.function.PartitionByDate">
	<property name="dateFormat">yyyy-MM-dd</property>    		  <!--日期格式-->
	<property name="sBeginDate">2019-01-01</property>            <!--开始日期-->
	<property name="sPartionDay">10</property> 					<!--每分片天数-->
</function>
```

按日期（自然月）分片： 从开始日期算起，按照自然月来分片

```xml
<function name=“sharding-by-month” class=“io.mycat.route.function.PartitionByMonth">
	<property name="dateFormat">yyyy-MM-dd</property>        	<!--日期格式-->
	<property name="sBeginDate">2019-01-01</property>            <!--开始日期-->
</function>
 #注意：节点个数要大于月份的个数                              
```

注意： 需要提前将分片规划好，建好，否则有可能日期超出实际配置分片数



## 3.按单月小时分片

最小粒度是小时，可以一天最多24个分片，最少1个分片，一个月完后下月从头开始循环。

```xml
<function name="sharding-by-hour" class=“io.mycat.route.function.LatestMonthPartion"> 
	<property name="splitOneDay">24</property> <!-- 将一天的数据拆解成几个分片-->
</function>
```

注意事项：每个月月尾，需要手工清理数据



# 二、离散分片

## 1.枚举分片

​       通过在配置文件中配置可能的枚举id，自己配置分片，本规则适用于特定的场景，比如有些业务需要按照省份或区县来做保存，而全国省份区县固定的

```xml
<function name="hash-int" class=“io.mycat.route.function.PartitionByFileMap">
 	<property name="mapFile">partition-hash-int.txt</property> 
	<property name="type">0</property> 
	<property name="defaultNode">0</property> 
</function> 
```

partition-hash-int.txt 配置：

```text
10000=0  //10000就路由到节点1
10010=1  //10010就路由到节点2
```

mapFile标识配置文件名称
type默认值为0（0表示Integer，非零表示String）
默认节点的作用：枚举分片时，如果碰到不识别的枚举值，就让它路由到默认节点



## 2.十进制取模

十进制求模分片：规则为对分片字段十进制取模运算。数据分布最均匀

```xml
<function name="mod-long" class=“io.mycat.route.function.PartitionByMod"> 
	<!-- how many data nodes  --> 
	<property name="count">3</property> 
</function>
```



## 3. 应用指定分片

应用指定分片：规则为对分片字段进行字符串截取，获取的字符串即指定分片。

```xml
<function name="sharding-by-substring“ class="io.mycat.route.function.PartitionDirectBySubString">
	<property name="startIndex">0</property><!-- zero-based -->
	<property name="size">2</property>
	<property name="partitionCount">8</property>
	<property name="defaultPartition">0</property>
</function>
```

- startIndex 开始截取的位置
- size 截取的长度
- partitionCount 分片数量
- defaultPartition 默认分片



例如 id=05-100000002;
		在此配置中代表根据 id 中从 startIndex=0，开始，截取 size=2 位数字即 05，05 就是获取的分区，如果没传默认分配到 defaultPartition



## 4.截取数字hash分片
此规则是截取字符串中的int数值hash分片

```xml
<function name="sharding-by-stringhash" class=“io.mycat.route.function.PartitionByString"> 
	<property name=length>512</property>		<!-- zero-based --> 
	<property name="count">2</property> 
	<property name="hashSlice">0:2</property>
</function>
```

- length代表字符串hash求模基数，

- count分区数，其中length*count=1024

- hashSlice: hash预算位，即根据子字符串中int值 hash运算
  - 0 代表 str.length(), 
  - -1 代表 str.length()-1，大于0只代表数字自身
  - 可以理解为substring（start，end），start为0则只表示0
    -  例1：值“45abc”，hash预算位0:2 ，取其中45进行计算
       例2：值“aaaabbb2345”，hash预算位-4:0 ，取其中2345进行计算



## 5.一致性Hash分片
​		此规则优点在于扩容时迁移数据量比较少，前提分片节点比较多，虚拟节点分配多些。

虚拟节点少的缺点是会造成数据分布不够均匀
如果实际分片数量比较少，迁移量会比较多

```xml
<function name="murmur" class=“io.mycat.route.function.PartitionByMurmurHash"> 
	<property name="seed">0</property><!-- 创建hash对象的种子，默认0--> 
	<property name="count">2</property><!-- 要分片的数据库节点数量，必须指定，否则没法分片--> 
	<property name="virtualBucketTimes">160</property>
</ function>
```

注意：
一个实际的数据库节点被映射为这么多虚拟节点，默认是160倍，也就是虚拟节点数是物理节点数的160倍

