



## 1.HDFS 相关原理

HDFS的存储模型：

![N6Yii6.png](https://s1.ax1x.com/2020/06/27/N6Yii6.png)



​	**三大组件：**NameNode，SecondaryNameNode，DataNode

- **NameNode：**
  - 存储元数据（文件名，创建时间，大小，权限，文件和block块的映射关系）
- **DataNode:**
  - 存储真实的数据信息
- **SecondaryNameNode：**
  - 减轻NameNode的压力，将edits编辑日志文件和fsImage镜像文件进行合并操作，合并之后交给NameNode管理

### 1.1 HDFS架构

HDFS采用主从架构模型，先通过NameNode获取数据元信息，然后在根据元信息去某个节点获取具体的信息

![u9hucV.png](https://s2.ax1x.com/2019/09/22/u9hucV.png)

![N6Yvff.png](https://s1.ax1x.com/2020/06/27/N6Yvff.png)

----



### 1.2 NameNode（NN）

注意下面一点，NameNode数据只会存储在内存中，不会像数据库一样会存在磁盘交换，也就是说NameNode的内存是包含所有数据的，如果内存不够了那么NameNode就停止上传文件了。所这里hdfs的瓶颈就在nameNode。



另外下面NameNode中保存块只保存了偏移量，**没有保存block的位置信息**，这个有dataNode通过心跳上报信息来恢复，如果有客户端请求如果发现这个位置上没有数据表示datanode没有启动起来，不存储位置信息的目的就是为了避免给出的位置信息，让客户端多次请求无用的datanode，，另外要同步所有的位置信息时间也是比较上的。这样说明了NameNode的重启的代价还是比较高的

![N6tAkq.png](https://s1.ax1x.com/2020/06/27/N6tAkq.png)

#### 1) 数据存储

​	数据存储放在hdfs-site.xml的`dfs.namenode.name.dir`属性配置中，默认`file://${hadoop.tmp.dir}/dfs/name`

在服务器上持久化存储的文件有如下几种：

- fsimage:镜像文件，存储某段时间内内存元数据信息和文件与Block块的映射关系，位置信息不会存储
- edits:对metadata操作的日志文件
- seee_txid:操作事务id
- VERSION：存储命名空间ID，集群等信息

#### 2) 多次格式化NameNode问题

​	   hdfs格式化会改变VERSION文件中的clusterID,首次格式化时datanode和namenod会产生相同的clusterID；如果重新格式化，namenode的clusterID改变，就会和datanode的clusterID不一致，如果重新启动或读写hdfs就会挂掉。 



### 1.3 DataNode（DN）

​		启动时会向NameNode汇报block信息，同时向NN发送心跳保持联系，如果NN10分钟没有收到心跳就认为其已经lost，然后会copy其上的block到其他的DN。

#### 1) 数据存储

​    数据存储放在hdfs-site.xml的`dfs.datanode.data.dir`属性配置中，默认`file://${hadoop.tmp.dir}/dfs/data`，

存储内容：数据本身和数据长度，校验和时间戳，还有block块的元信息

**文件块（Block）**:基本的存储单元，默认大小是128M，通过`dfs.blocksize`配置，有点类似分片的概念



#### 2）block副本策略

​	默认是3个副本，通过`dfs.replication`配置，就是针对block块的副本，类似es分片

- 第一个block副本发在client所在的node里（如果client不再集群中，那么这个node就是随机选择的，当然系统会会尝试不选择太忙或者太满的ndoe）

- 第二个副本会放在和第一个node节点不同的机架的node中
- 第三个副本和第二个副本在同一个机架，随机放在不同的node中，如果还有更多的副本就随机放在集群node中



#### 4）DataNode和NameNode通信

- DataNode启动后向Namenode注册，注册后周期性（1h)向NameNode上报块信息
  -  BlockReport数据内容：block块和datanode节点的映射（第二映射关系）
- DataNode周期性发送心跳（3s)给NameNode，心跳返回结果带有namenode下发给datanode的命令，如果超过10m，namenode没有收到datanode的心跳则认为不可用



### 1.4 SecondaryNameNode

SecondaryNameNode 第一代的产物，第二代没有了；

不是NN的备份，主要工作是帮助NN合并edits log，减少NN启动时间

#### 1）执行流程

[![uCpiuD.png](https://s2.ax1x.com/2019/09/22/uCpiuD.png)](https://imgchr.com/i/uCpiuD)

SNN即SecondaryNameNode；NN即NameNode

[![uCphrD.png](https://s2.ax1x.com/2019/09/22/uCphrD.png)](https://imgchr.com/i/uCphrD)



### 1.5 HDFS数据读写流程

#### 1）HDFS数据写入流程

**HDFS副本放置策略：**
	1.第一个副本放在上传文件的datanode上
	2.第二个放在和第一个副本不同的机架上
	3.第三个放在和第二个副本相同机架的节点上
	4.其他随机节点（其他副本）

[![uC9ry8.png](https://s2.ax1x.com/2019/09/22/uC9ry8.png)](https://imgchr.com/i/uC9ry8) 

[![uC9RFs.png](https://s2.ax1x.com/2019/09/22/uC9RFs.png)](https://imgchr.com/i/uC9RFs)



#### 2）HDFS数据读取流程

[![uCPZ59.png](https://s2.ax1x.com/2019/09/22/uCPZ59.png)](https://imgchr.com/i/uCPZ59)

[![uCPFDU.png](https://s2.ax1x.com/2019/09/22/uCPFDU.png)](https://imgchr.com/i/uCPFDU)



### 1.6 HDFS安全模式

**安全模式：**即只能查看，不能添加和修改操作

**作用：**namdenode启动后进入安全模式，检查数据库和datanode的完整性

**判定条件：**

![uCidY9.png](https://s2.ax1x.com/2019/09/22/uCidY9.png)

**命令操作：**

`bin/hdfs dfsadmin -safemode <command>`

```bash
command选项：
​	 get:查看当前状态
​    enter：进入安全模式
​    leave：强制退出安全模式
​    wait:等待
```



## 2.HDFS命令行操作

### 2.1 dfs常见命令

​	命令和linux命令类似

​	-D 可以加上我们常用的键值对设置对应的值，比如：

-D dfs.blocksize=1048576   表示将block块设置为1M。

![uCFUnf.png](https://s2.ax1x.com/2019/09/22/uCFUnf.png)

![uCAMJH.png](https://s2.ax1x.com/2019/09/22/uCAMJH.png)

 ### 2.2 dfs 管理命令

![uCEhE8.png](https://s2.ax1x.com/2019/09/22/uCEhE8.png)



### 2.3 HDFS文件权限
	POSIX，和linxu文件权限类似：r,w,x
	
	那一个用户通过hadoop命令创建一个文件，那么这个文件的owner就是该用户


## 3. HDFS的Java操作

​	当前案例在伪分布式下操作

**dfs-site.xml**

```xml
<configuration>
	<property>
		<name>dfs.replication</name>
		<value>1</value>
	</property>
    <!--需要添加这个权限关闭，否则不能访问，当然我可以通过添加用户来访问-->
	<property>
		<name>dfs.permissions.enabled</name>
		<value>false</value>
	</property>
</configuration>

```

**core-site.xml**

```xml
<configuration>
    <property>
    		<name>fs.defaultFS</name>
   		<value>hdfs://192.168.124.158:8020</value>  <!--这里这样配置，其他客户端才能通过192.168.124.158访问-->
    </property>
</configuration>
```

**相关API如下：**

​	只有一部分，可以对照命令相关的API都有

```java
package com.visno.hdfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import sun.awt.geom.AreaOp;

import java.io.IOException;
import java.net.CacheRequest;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Vison
 * @date 2019/9/25 20:04
 * @Version 1.0
 */
public class HdfsDemo {
	
    //这个地址就是nameserver的地址，即core.site.xml中fs.defaultFS的值
    private static final String HDFA_PATH = "hdfs://192.168.124.158:8020";
    private FileSystem  fileSystem= null;


    public void setUp()  {
        Configuration configuration = new Configuration();
        configuration.set("fs.defaultFS","hdfs://192.168.124.158:8020");
        try {
            //URI uri = new URI(HDFA_PATH);
            fileSystem =  FileSystem.get(configuration);
            System.out.println(fileSystem);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean mkdir(String path) throws IOException {
        try {
            return fileSystem.mkdirs(new Path(path));
        }finally {
            fileSystem.close();
        }
    }
    public boolean create(String name) throws Exception{
        try(FSDataOutputStream fos = fileSystem.create(new Path(name))) {
            fos.write("hello world".getBytes());
            fos.flush();
        }finally {
            fileSystem.close();
        }
        return true;
    }

    public void open(String name)throws Exception{
        try(FSDataInputStream fis = fileSystem.open(new Path(name))){
            IOUtils.copyBytes(fis,System.out,1024);
        }finally {
            fileSystem.close();
        }
    }

    public void copyFromLocalFile()throws Exception{
        fileSystem.copyFromLocalFile(new Path("C:\\Users\\vison\\Desktop\\文件\\批处理执行灵活IP.cmd"),
                new Path("/vison/")); //Windows系统和Linux系统都可以，写法不一样
    }


    public static void main(String[] args) throws Exception{
        HdfsDemo hdfsDemo = new HdfsDemo();
        hdfsDemo.setUp();
//        boolean bool = hdfsDemo.mkdir("/vison");
//        System.out.println(bool);
//        boolean bool = hdfsDemo.create("/vison/v1.txt");
//        hdfsDemo.open("/vison/v1.txt");
        hdfsDemo.copyFromLocalFile();
    }
}

```





## 4.HDFS 的优缺点

![N667Pe.png](https://s1.ax1x.com/2020/06/27/N667Pe.png)

![N6cEq0.png](https://s1.ax1x.com/2020/06/27/N6cEq0.png)



