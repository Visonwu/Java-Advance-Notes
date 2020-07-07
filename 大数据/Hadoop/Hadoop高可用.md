## 1.Hadoop版本对比

1.x的Hadoop只有mapreduce和hdfs，JobTracker的作用是资源管理和任务的调用，短板较大（不支持spark等框架）

2.x的Hadoop有mapreduce，hdfs，yarn（资源管理，替代JobTracker）
	解决1.x单点故障和内存受限问题

- 单点故障：通过主备NameNode解决	
- 内存受限：
  - HDFS Federation联邦
  - 水平扩展，支持多个NameNode
    			每个NameNode分管一部分目录
    			所有NameNode共享所有DataNode存储资源

   

3. 相比1.x架构改变，使用方式不变
   		
   	

---------------------------------
## 2.高可用解决方案

### 1.1 zk做NameNode主备

主备使用zk做两个NameNode主节点选举，如果为master那么作为主；

两个NameNode同步元数据:
	1.客户端上次元数据，主NameNode的操作日志记录到nfs（网络文件系统，nfs保证过半机制即可返回成功），返回成功
	2.从NameNode可以从nfs中获取操作日志同步元数据
	3.dataNode的块位置自动上报到两个主从NameNode中
	![NH7w9I.png](https://s1.ax1x.com/2020/07/02/NH7w9I.png)

### 1.2 namenode federation 联邦

​	类似分片机制，多个nameNode分别处理各自的文件，但是都从存储到相同的dataNode
​	通过存储到hdfs的不同目录隔离不同的nameNode

![NH7Djf.png](https://s1.ax1x.com/2020/07/02/NH7Djf.png)





## 3.搭建

官网参考：<https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-hdfs/HDFSHighAvailabilityWithQJM.html>

