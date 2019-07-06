

## 1.索引概念    

 索引是为了加速对表中数据行的检索而创建的一种分散存储的数据结构

**特点：**

- 1.索引能极大的减少存储引擎需要扫描的数据量
- 2.索引可以把随机IO变成顺序IO
- 3.索引可以帮助我们在进行 分组、 排序等操作时，避免使用临时表

## 2.MYSQL的储存结构B+Tree的由来

​    背景知识点：Mysql的的每一个磁盘块（页、节点）都是固定大小的（16k），这个也是可以设置的

### **2.1. 二叉树Binary search Tree**

​        每一个磁盘块存储一个关键字（索引），如图所示：

![img](https://img-blog.csdnimg.cn/20181209100652276.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### **2.2.平衡二叉树 Balanced binary search tree**

![img](https://img-blog.csdnimg.cn/20181209100901847.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)



> **前面两个二叉树可以总结如下：**
>
> **1) 它太深了**
>
> ​       数据处的（高）深度决定着他的IO 操作次数，IO 操作耗时大,如上图如果要找到索引3需要经过三次IO。
> **2) 它太小了**
> ​        每一个磁盘块 （节点/ 页） 保存的数据量太小 了（一个节点只有一个关键字和相应的数据，信息太少）
> ​        没有很好的利用操作磁盘IO 的数据 交换 特性（一个磁盘16k，估计只用了1k，多次IO后获取到的无用信息太多）
> ​       也没有利用好磁盘IO 的预 读能力（操作系统定义的空间局部性原理 ），从而带来频繁的IO。（在你读取当前节点时，它认为你要读取接下来的节点，帮你提前读）

### 2.3.多路平衡查找树 B-Tree

​       这里多路指的一个磁盘块（节点）有n个子节点，那么当前节点就保存了n-1个关键字的数据。下面这个图就是三路，一个节点保存了两个关键字的信息和三个节点的引用。

 这样相比二叉树，每一个节点的信息也就可以存储更多的信息了，如图：

![img](https://img-blog.csdnimg.cn/20181209102947372.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### 2.4.加强版多路平衡查找树 B+Tree

​    相比B-Tree树，做了如下改变。磁盘块不在保存关键字的信息（索引对应数据的地址引用）,而只是保存在叶子节点上。并且采用左闭合的方式。

![img](https://img-blog.csdnimg.cn/20181209103412552.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

> **两个B树 对比总结：**
>
> - B+Tree采用左闭右开的关键字搜索区间
> - B+Tree非叶节点不保存数据相关信息，只保存关键字和子节点的引用
> - B+Tree关键字对应的数据保存在叶子节点上
> - B+Tree叶子节点上的数据时顺序排序的，并且相邻接点具有顺序引用的关系

### 2.5. 选择 B+Tree 的优点

> - B+ 树是B- 树的变种（PLUS 版）多路绝对平衡查找树，他拥有B- 树的优势
> - B+ 树扫库、表能力更强
> - B+ 树的磁盘读写能力更强
> - B+树的排序能力更强
> - B+ 树的查询效率更加稳定（仁者见仁、智者见智，有可能B-Tree第一次就命中了，直接返回，而B+Tree需要找到叶节点，所以查找效率不一定比B-Tree更高）

## 3.MYSQL 的B+Tree表现形式

​     mysql数据存储位置存储在本地硬盘上

​          1.通过 在msyql的命令行中输入 show variable like 'datadir'  找到数据存储位置

​          2.在重新打开新的命令行窗口进入找到的地址，如var/lib/data/

​          3.进入你的某一个库中，可以查看到一张表中分别对应的文件，这个MySQL不同版本不同。这里是5.5.

​              innodb引擎包含两个文件：xxx.frm, xxx.ibd  

​              myisam引擎包含文件：xxx.frm .xxx.MYD,xxx.MYI

​                      xxx.frm是表的定义文件，是每一个引擎都会用的；xxx.idb是索引的数据

​                     xxx.MYD,是myisam保存数据的文件，xxx.MYI是myisam保存索引的文件

### 3.1 Myisam引擎的B+Tree表现形式

​        myisam 的数据和索引是分开存储的。

​        叶节点存储的是关键字对应的是数据引用地址，而且不同关键字（索引）分别对应的各自引用地址，互不关联。如下图：

   **1）一个索引对应的B+Tree的表现形式**

![img](https://img-blog.csdnimg.cn/20181209105134296.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

**2）多个索引对应的B+Tree的表现形式**

## ![img](https://img-blog.csdnimg.cn/20181209105313907.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

### 3.2 InnoDB引擎的B+Tree表现形式

​          InnoDB的数据和索引是存储在一起的，数据都是以主键为索引来组织存储的。如果没有设置主键，innodb会默认生成一个隐式主键索引。

**1）主键为索引的B+Tree的表现形式，叶节点是数据信息**

​     **如下聚集索引就是叶子节点保存着数据的信息，这里的聚集索引就是主键。**

   ![img](https://img-blog.csdnimg.cn/20181209111515485.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

**2） 其他辅助索引的B+Tree的表现形式**

​       这里辅助索引，也称为二级索引，叶节点储存的信息是主键的信息。如图

![img](https://img-blog.csdnimg.cn/20181209111740106.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MDc5Mjg3OA==,size_16,color_FFFFFF,t_70)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)



### 3.3  InnoDB 和 Myisam 引擎的B+Tree 树 比对

- ​    1. InnoDB 辅助索引不存储数据的信息（或者索引）就是为了避免数据地址发生迁移的时候不会跟着修改辅助索引的叶节点信息。这也是Myisam做查询比较快，而InnoDB在综合更好。
- ​    2.InnoDB是一个文件IBD,Myisam是两个文件MYI，MYD
- ​    3.InnoDB索引和数据存储在一个文件，Myisam的索引和数据时存储在两个文件中



## 4.索引补充点

### 4.1列的离散型

​        计算方式：count（distinct col ） : count (col)

 **结论：离散型越好，在节点的选择就越好**

### 4.2 最左匹配原则

​     对索引中的关键字进行计算（比对），一定是从左往右且不可跳过。

### 4.3 联合索引

​	联合索引在B+Tree中的存储规则如下，

`where和order by`的字段可以是联合索引使用。因为通过第一个字段查询后的数据就是根据第二个字段做顺序排序的，这样就不会增加额外的排序操作了。

​	![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4oss1y2dnj20f205cq30.jpg)

**单列索引**
         节点中关键字[name],单列索引是特殊的联合索引
**联合索引**
          节点中关键字[name,phoneNum]

**联合索引列选择原则：**
       1 ，经常用的列优先 【 【 最左匹配原则 】
       2 ，选择性（离散度）高的列 优先 【 离散度高原则 】
       3 ，宽度小的列 优先 【 最少空间原则 】



### 4.4 覆盖索引

​     如果查询列可通过索引节点中的关键字直接返回则该索引称之为覆盖索引。节点处已经找到关键字的信息了，就可以直接返回当前节点处关键字的信息了，这也是为什么我们要减少"select * " 的使用，因为有可能会使用到覆盖索引。
​     覆盖索引优点：

​            可减少数据库IO，将随机IO变为顺序IO，可提高查询性能

## 5.总结：

### 5.1 InnoDB 引擎 怎么 选择索引

**1.一个磁盘块固定大小：**

​       索引的数据类型应该越小越好，这样单个节点储存的关键点就多，树的深度就地，IO次数少。特别是主键索引

**2.列的离散型**

​       列离散型越好，在每一个节点的选择性越好，越利于数据的查找，当离散型到了一个程度就是全表扫描

**3.最左匹配原则**

​     做联合索引的时候一定把常用的索引放前面，离散型好的放前面，索引长度小的放前面



### **5.2 平时SQL索引的使用失效**

- 1.索引的数据长度越少越好
- 2.索引并不是越多越好，一定选择最合适的
- 3.匹配到前缀可用到索引 like "12345%", like "%1232" 、like “%1232%”用不到索引。

```tex
实际上like "12345%"也不一定会用到索引，如果这个字段中所有的数据前面的数据都包含了12345那么，当索引扫描到B+Tree根节点的时候，并不知往哪个岔路走，所以最终还是全表扫描，所以这个不一定
```

- 4.Where 中的 not in 和<>操作不能使用到索引  ，也是在B+Tree节点无法选择
- 5.匹配范围值，order by,group by 可以使用到索引
- 6.多用指定到查询，少使用到select *  ,因为可以使用到覆盖索引
- 7.联合索引无法如果不是按照索引最左索引开始查找，无法使用索引
- 8.联合索引中精确匹配最左前列并范围匹配另外一个可以使用索引
- 9.联合索引中如果查询中有某一个列的范围查询，则其右边的所有列都无法使用索引。