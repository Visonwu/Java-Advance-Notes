1.使用jdk，Hbase即可

Hbase下载地址：<https://mirror.bit.edu.cn/apache/hbase/2.1.9/>

详细的Hbase+Hadoop集群搭建参考：[https://www.cnblogs.com/clsn/p/10300487.html#hadoophbase-%E9%9B%86%E7%BE%A4%E6%90%AD%E5%BB%BA](https://www.cnblogs.com/clsn/p/10300487.html#hadoophbase-集群搭建)



  hbase有三种安装方式：单机、伪分布式、完全分布式。
    【单机hbase】:hbase数据库的数据文件存在单一的一台设备上，使用的是该设备的文件系统。
    【伪分布式hbase】:hbase数据库的数据文件存在一台设备构成的hdfs上，数据库也分主从结构。
    【完全分布式hbase】:hbase数据库的数据文件存在多台设备构成的hdfs上，数据库也分主从结构。

下面的安装版本都是使用的hbase-2.1.9

# 一、Hbase单机安装

hbase数据库的数据文件存在单一的一台设备上，使用的是该设备的文件系统。

## 1.解压

```sh
tar -zxvf ....tar.gz
```

## 2.修改文件

​	解压完成之后需要修改conf目录下 hbase-env.sh 和 hbase-site.xml 两个配置文件

### 2.1 修改 hbase-env.sh文件

```sh
# 指定jdk的路径
export JAVA_HOME=/usr/local/jdk1.8

#表示启用hbase自带的zookeeper
export HBASE_MANAGES_ZK=true
```

### 2.2 修改 hbase-site.xml 文件

```xml
<configuration>
  <!-- 指定hbase的根目录，放在本地文件系统-->
 <property>
  <name>hbase.rootdir</name>
  <value>file:///mnt/nvme/hbase-2.1.9</value>
 </property>

 <property>
   <name>fs.defaultFS</name>
   <value>/root/sofaware/hbase-2.1.9/data</value>
 </property>
    
    <!--指定临时目录，可省略 -->
 <property>
    <name>hbase.tmp.dir</name>
    <value>/root/software/hbase-2.0.5/data</value>
 </property>

    
    <!-- 关闭分布式配置-->
  <property>
    <name>hbase.cluster.distributed</name>
    <value>false</value>
  </property>
</configuration>
```

## 3.启动

进入bin目录中，执行./start-hbase.sh就ok了

## 4.开启hbase客户端

```sh
# 启动shell客户端
>./hbase shell

# 创建表
>create 'user','info'

#显示表
> list

# 显示表结构
> describe 'user'

#删除表
> drop 'user'

#设置表为disable
> disable 'user'

```



# 二、伪分布式安装

先安装好Hadoop的namenode-yarn,zk使用hbase自带的。

## 1 修改 hbase-env.sh文件

```properties
# 指定jdk的路径
export JAVA_HOME=/usr/local/jdk1.8

#表示启用hbase自带的zookeeper
export HBASE_MANAGES_ZK=true
```



## 2 修改 hbase-site.xml 文件

```xml
<configuration>
  <!--hdfs 存储地址，配置nameNode地址--->  
  <property>
    <name>hbase.rootdir</name>
    <value>hdfs://hadoop-senior01-test.com:9000/hbase</value>
  </property>
  <!-- zk数据存储地址-->  
  <property>
    <name>hbase.zookeeper.property.dataDir</name>
    <value>/usr/local/hbase-2.1.9-pseudo/zookeeper</value>
  </property>
  <!--开启分布式存储-->  
  <property>
    <name>hbase.cluster.distributed</name>
    <value>true</value>
  </property>
</configuration>
```

vim conf/regionservers；

这里配置默认localhost就ok；不过最好配置当前机器的域名最好



## 3.启动访问

**1)启动**

```shell
>bin/hbase-daemon.sh start zookeeper
>bin/hbase-daemon.sh start master
>bin/hbase-daemon.sh start regionserver

或者 start-hbase.sh
# 启动后使用jsp查看进程
>jps
DataNode
ResourceManager
HQuorumPeer
SecondaryNameNode
HMaster
NodeManager
HRegionServer
NameNode
Jps

# 多了HQuorumPeer，HMaster，HRegionServer
```

**2）访问**

访问web页面访问:

```
http://192.168.199.131:16010
```



说明

```
端口说明：  16000是master进程的RPC端口！
		  16010是master进程的http端口！
		  16020是RegionServer进程的RPC端口！
		  16030是RegionServer进程的http端口！
```

**3）停止**

```shell
> bin/hbase-daemon.sh stop regionserver
> bin/hbase-daemon.sh stop master
> bin/hbase-daemon.sh stop zookeeper

# 或者stop-hbase.sh
```


# 三、分布式安装

和前面不同的是，zk自定义安装，多个regionServer安装，多个机器的时钟同步

## 0.配置时间同步器

提示：如果集群之间的节点时间不同步，会导致regionserver无法启动，抛出ClockOutOfSyncException异常。

**1）同步时间服务**

​	`ntpdate -u ntp4.aliyun.com` 做时间同步，不过这个需要备份，如果阿里云时间同步故障，后果不堪设想。

**2) 时间同步器配置**

可以再hbase-site.xml中配置时间最大差值

属性：hbase.master.maxclockskew设置更大的值

```xml
<property>
        <name>hbase.master.maxclockskew</name>
        <value>180000</value>
        <description>Time difference of regionserver from master</description>
 </property>
```





## 1 配置hbase-env.sh

```shell
export JAVA_HOME=/usr/local/jdk1.8    #Java安装路径
export HBASE_CLASSPATH=/opt/hbase-1.2.7/conf    #HBase类路径
export HBASE_MANAGES_ZK=false    #由HBase负责启动和关闭Zookeeper
```



## 2.配置hbase-site.xml

```xml
<property>
    <name>hbase.rootdir</name>
    <value>hdfs://namenode1:9000/hbase</value>
</property>
<property>
    <name>hbase.cluster.distributed</name>
    <value>true</value>
</property>
<property>
    <name>hbase.zookeeper.quorum</name>
    <value>slave01,slave02,slave03</value>
</property>
<property>
    	<!--hbase在zk的数据存储位置-->
        <name>hbase.zookeeper.property.dataDir</name>
     <value>/usr/local/zookeeper-3.4.10/datas</value>
</property>
```



## 3. 配置conf/regionservers

​	配置三台机器的域名

```text
slave01
slave02
slave03
```



## 4.启动关闭

```
			
2.启动
	①启动hdfs,zookeeper
	②启动hbase
			三台机器都启动regionserver: xcall /usr/lcoal/hbase/bin/hbase-daemon.sh start regionserver
			选择一台启动master: /usr/lcoal/hbase/bin/hbase-daemon.sh start master
			
	③查看：
			jps查看
			访问web界面： 访问master进程所在机器:16010
			
	端口说明：    16000是master进程的RPC端口！
				16010是master进程的http端口！
				16020是RegionServer进程的RPC端口！
				16030是RegionServer进程的http端口！
				
3. 停止
	三台机器都停止regionserver: xcall /usr/lcoal/hbase/bin/hbase-daemon.sh stop regionserver
	选择一台停止master: /usr/lcoal/hbase/bin/hbase-daemon.sh stop master
```









