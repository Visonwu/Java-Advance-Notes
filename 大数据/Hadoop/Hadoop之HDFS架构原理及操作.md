



## 1.HDFS 相关原理

​	三大组件：NameNode，SecondaryNameNode，DataNode

- **NameNode：**
  - 存储元数据（文件名，创建时间，大小，权限，文件和block块的映射关系）
- **DataNode:**
  - 存储真实的数据信息
- **SecondaryNameNode：**
  - 减轻NameNode的压力，将edits编辑日志文件和fsImage镜像文件进行合并操作，合并之后交给NameNode管理

### 1.1 HDFS架构

![u9hucV.png](https://s2.ax1x.com/2019/09/22/u9hucV.png)



### 1.2 NameNode

#### 1) 数据存储

​	数据存储放在hdfs-site.xml的`dfs.namenode.name.dir`属性配置中，默认`file://${hadoop.tmp.dir}/dfs/name`

在服务器上存储的文件有如下几种：

- fsimage:镜像文件，存储某段时间内内存元数据信息和文件与Block块的映射关系
- edits:编辑日志文件
- seee_txid:操作事务id
- VERSION：存储命名空间ID，集群等信息

#### 2) 多次格式化NameNode问题

​	   hdfs格式化会改变VERSION文件中的clusterID,首次格式化时datanode和namenod会产生相同的clusterID；如果重新格式化，namenode的clusterID改变，就会和datanode的clusterID不一致，如果重新启动或读写hdfs就会挂掉。 



### 1.3 DataNode

#### 1) 数据存储

​    数据存储放在hdfs-site.xml的`dfs.datanode.data.dir`属性配置中，默认`file://${hadoop.tmp.dir}/dfs/data`，

存储类容：数据本身和数据长度，校验和时间戳

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

#### 1）执行流程

[![uCpiuD.png](https://s2.ax1x.com/2019/09/22/uCpiuD.png)](https://imgchr.com/i/uCpiuD)

SNN即SecondaryNameNode；NN即NameNode

[![uCphrD.png](https://s2.ax1x.com/2019/09/22/uCphrD.png)](https://imgchr.com/i/uCphrD)



### 1.5 HDFS数据读写流程

#### 1）HDFS数据写入流程

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

![uCFUnf.png](https://s2.ax1x.com/2019/09/22/uCFUnf.png)

![uCAMJH.png](https://s2.ax1x.com/2019/09/22/uCAMJH.png)

 ### 2.2 dfs 管理命令

![uCEhE8.png](https://s2.ax1x.com/2019/09/22/uCEhE8.png)



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





