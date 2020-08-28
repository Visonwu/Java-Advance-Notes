```netstat -natlp```

## 0.解读环境搭建

```text
1.操作系统环境准备:
	1.1 依赖原件ssh，jdk
	1.2 环境配置：java-home，免密钥 
	1.3 时间同步（心跳需要解决时间问题），伪分布式不用，
	1.4 hosts，hostname

2.Hadoop配置:
	2.1 /opt/vison/
	2.2 配置文件修改：java-home （解决之前免密钥，远程执行不加载/etc/profile/环境变量的问题）
	2.3 角色在哪里启动：配置文件需要反映出来（nameNode，dataNode，SecondaryNameNode）
```

从上面来看伪分布式是所有实例都是部署在同一个节点上的，所以上面的时间同步是不需要了，需要配置系统环境：java-home，免密钥，host，hostname。



# 一、Hadoop伪分布式环境配置

相关的配置信息我们都可以通过官网<https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-common/SingleCluster.html>查看：

![nxcWp4.png](https://s2.ax1x.com/2019/09/21/nxcWp4.png)

实际就是把Yarn,Hadoop等配置放置在同一台机器上

前提：安装Hadoop单机安装的方式将Hadoop环境搭建好



步骤如下：

## 1.配置启动HDFS

**JAVA_HOME,HADOOP_HOME配置.**

免密钥配置：

```sh
  $ ssh-keygen -t rsa -P '' -f ~/.ssh/id_rsa
  $ cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
  $ chmod 0600 ~/.ssh/authorized_keys
```



### 步骤一：文件配置

**1）在`$HADOOP_HOME/etc/hadoop/core-site.xml`配置**

注意配置：”hadoop.tmp.dir“：这个是放在linux的tmp临时目录中，很容易被清理掉

```xml
<configuration>
    <property>
        <name>fs.defaultFS</name> <!--决定nameNode在哪个位置-->
        <value>hdfs://hadoop-senior01-test.com:8020</value>
    </property>
     <property>
    	<name>hadoop.tmp.dir</name>
    	<value>/var/vison/local</value>
    </property>
</configuration>
```

注意这里配置分布式不要用localhost代替，这里的`hadoop-senior01-test.com`是当前机器得主机名

 ```bash
# 我们可以通过hostname 直接设置，这个名字只是临时生效，如果要在下次生效需要修改配置文件
$> hostname hadoop-senior01-test.com
$> hostname   # 查看当前的hostname

#永久性修改 ,修改为如下配置，然后服务设置后需要重新启动才能生效
$> vim /etc/sysconfig/network
HOSTNAME = hadoop-senior01-test.com

 ```

另外这里需要配置Ip和主机名的地址

```bash
$> vim /etc/hosts
127.0.0.1 hadoop-senior01-test.com
```



**2) 在`$HADOOP_HOME/etc/hadoop/hdfs-site.xml`配置hdfs的副本数，这里配置1即可，及SecondaryNameNode**

​		原因：伪分布式下只能设置为1，不然会一直提示异常，副本不能放在同一个节点上

```xml
<configuration>
        <property>
                <name>dfs.replication</name>
                <value>1</value>
        </property>
        <property>		 <!--设置secondary地址-->
   			<name>dfs.namenode.secondary.http-address</name>
   			<value>hadoop-senior01-test.com:50090</value> 
		 </property>
</configuration>


```



**3) 在`$HADOOP_HOME/etc/hadoop/hadoop-env.sh`修改java绝对路径，否则启动会报错**

```bash
#将JAVA_HOME相对路径改为绝对路径
$> vim /hadoop-env.sh
export JAVA_HOME=/usr/local/jdk1.8.0_211
```



**4) vim hadoop/etc/hadoop/slaves` 设置从节点，即设置DataNode节点的位置**

#去掉原来的localhost

```
hadoop-senior01-test.com
```



### 步骤二：启动HDFS

**1） 格式化NameNode**

​		清空NameNode目录下的所有数据，生成目录结构，初始化一些信息到文件中；（格式化，清空所有元数据文件，然后新建一个fsimage文件和edits文件，集群文件，只有在第一次执行这个命令，后面服务重启不能在执行这个命令）

```bash
# $HADOOP_HOME/bin下 可以通过hdfs --help 查看命令 ，我们配置了环境变量可以直接这样启动
$ > sh  hdfs namenode -format
# 或者
$> hadoop namenode -format
# 成功后目录创建成功 。。。。has been successfully formatted.
```

​	格式化后我们可以再`/var/vison/local/dfs/name/current`看到生成的相关文件

**2）启动NameNode,DataNode,SecondaryNameNode**

```bash
# $HADOOP_HOME/sbin下 ,一键启动三个节点
$ > sh start-dfs.sh
	#或者分开启动
	sbin/hadoop-daemon.sh start namenode
	sbin/hadoop-daemon.sh start datanode
	sbin/hadoop-daemon.sh start secondarynamenode
	
# 会有如下日志生成，表示我们可以通过如下地址查看日志情况，另外日志是通过.log查看，不是通过.out看日志文件
#hadoop-senior01-test.com: starting namenode, logging to /usr/local/hadoop-2.7.7/logs/hadoop-root-namenode-hadoop-senior01-test.com.out
#hadoop-senior01-test.com: starting datanode, logging to /usr/local/hadoop-2.7.7/logs/hadoop-root-datanode-hadoop-senior01-test.com.out




#然后启动会，应该会连续输入多次本机密码，通过jps查看是否所有节点都启动成功
$> jps

 # 创建/usr/root目录,客户端告诉hdfs是什么用户就是什么用户，这里默认是root，所以我们这里创建root目录
$> hdfs dfs -mkdir -p /user/root  

#上传文件
$> hdfs dfs -put test.txt   

#按照1M大小切分block块上传文件
$> hdfs dfs -D dfs.blocksize=1048576 -put test.txt   
```

**3）浏览器访问**

​	然后本地浏览器访问 `${ip}:50070` , 这里的50070是Http协议的端口号，上面自己配置的8020是节点间通信的RPC端口号,查看hdfs的管理页面 

可以通过Utiities/Browes the file system 查看hdfs的文件信息

**4) 停止节点**

```bash
# $HADOOP_HOME/sbin下 ,一键启动三个节点
$ > sh stop-dfs.sh
```



**注：**

​	hdfs默认按照block块大小拆分，默认180M分为1个块，不会区分文件某个字符切割成两半了，所以这些问题需要留给计算框架自己处理这些问题。





## 2.配置启动yarn

​	配置文件都在`$HADOOP_HOME/etc/hadoop/`进行操作，yarn是用来做计算的资源管理。

**1）配置mapreduce**

​	当前目录只提供了模板文件,复制一个新的配置文件,默认是local，这里设置使用yarn

```bash
$> cp mapred-site.xml.template mapred-site.xml
$> vim mapred-site.xml
```

```xml
<configuration>
  <property>
    <name>mapreduce.framework.name</name>
    <value>yarn</value>
 </property>
</configuration>
```



**2）配置yarn-site.xml文件**

```xml
<configuration>
 <property>
   <name>yarn.nodemanager.aux-services</name>
   <value>mapreduce_shuffle</value>
 </property>
    
 <property>  
    <name>yarn.resourcemanager.address</name>  
    <value>hadoopMaster:8032</value>  
</property> 
<property>
    <name>yarn.resourcemanager.scheduler.address</name>  
    <value>hadoopMaster:8030</value>  
</property>
<property>
    <name>yarn.resourcemanager.resource-tracker.address</name>  
    <value>hadoopMaster:8031</value>  
</property>
    
</configuration>
```



**3）启动yarn**

```bash
# $HADOOP_HOME/sbin下 ,一键启动两个个节点
$ > sh start-yarn.sh
	#或者分开启动
	sbin/hadoop-daemon.sh start resourcemanager
	sbin/hadoop-daemon.sh start nodemanager
```



**4) 访问浏览器**

​     然后本地浏览器访问 `${ip}:8088` ，查看yarn信息管理界面，能够查看则表示成功了



**5）停止yarn**

```bash
# $HADOOP_HOME/sbin下 ,一键启动三个节点
$ > sh stop-yarn.sh
```



# 二、mapreduce案例

```bash
#查看有多少案例
$> sh bin/hadoop jar share/hadoop/mapreduce/hadoop-mapreduce-examples-2.7.7.jar

#使用例子执行本地文件中wordcount功能
$> sh bin/hadoop jar share/hadoop/mapreduce/hadoop-mapreduce-examples-2.7.7.jar wordcount file:/usr/local/hadoop/LICENSE.txt file:/usr/local/hadoop/output
```



- 这里要用file来加载和输出（output），和单机不同，不然加载的就是服务器hdfs中的文件，然后观察yarn控制台页面的变化，当然这个输出目录不能存在
- 然后观察output输出的结果统计

