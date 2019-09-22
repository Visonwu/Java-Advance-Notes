

## Hadoop伪分布式环境配置

相关的配置信息我们都可以通过官网<https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-common/SingleCluster.html>查看：

![nxcWp4.png](https://s2.ax1x.com/2019/09/21/nxcWp4.png)

实际就是把Yarn,Hadoop等配置放置在同一台机器上

前提：安装Hadoop单机安装的方式将Hadoop环境搭建好



步骤如下：

## 1.配置启动HDFS

### 步骤一：文件配置

**1）在`$HADOOP_HOME/etc/hadoop/core-site.xml`配置**

```xml
<configuration>
    <property>
        <name>fs.defaultFS</name>
        <value>hdfs://hadoop-senior01-test.com:8020</value>
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



**2) 在`$HADOOP_HOME/etc/hadoop/hdfs-site.xml`配置hdfs的副本数，这里配置1即可**

```bash
<configuration>
        <property>
                <name>dfs.replication</name>
                <value>1</value>
        </property>
</configuration>

```



**3) 在`$HADOOP_HOME/etc/hadoop/hadoop-env.sh`修改java绝对路径，否则启动会报错**

```bash
#将JAVA_HOME相对路径改为绝对路径
$> vim /hadoop-env.sh
export JAVA_HOME=/usr/local/jdk1.8.0_211
```



### 步骤二：启动HDFS

**1） 格式化NameNode**

​		清空NameNode目录下的所有数据，生成目录结构，初始化一些信息到文件中

```bash
# $HADOOP_HOME/bin下 可以通过hdfs --help 查看命令 ，我们配置了环境变量可以直接这样启动
$ > sh  hdfs namenode format
```

​	格式化后我们可以再`/tmp/hadoop-root/dfs/name/current`看到生成的相关文件

**2）启动NameNode,DataNode,SecondaryNameNode**

```bash
# $HADOOP_HOME/sbin下 ,一键启动三个节点
$ > sh start-dfs.sh
	#或者分开启动
	sbin/hadoop-daemon.sh start namenode
	sbin/hadoop-daemon.sh start datanode
	sbin/hadoop-daemon.sh start secondarynamenode
	
	
#然后启动会，应该会连续输入多次本机密码，通过jps查看是否所有节点都启动成功
$> jps
```

**3）浏览器访问**

​	然后本地浏览器访问 `${ip}:50070` , 这里的50070是Http协议的端口号，上面自己配置的8020是节点间通信的RPC端口号



**4) 停止节点**

```bash
# $HADOOP_HOME/sbin下 ,一键启动三个节点
$ > sh stop-dfs.sh
```





## 2.配置启动yarn

​	配置文件都在`$HADOOP_HOME/etc/hadoop/`进行操作

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



**2）配置yarn-size.xml文件**

```xml
<configuration>
 <property>
   <name>yarn.nodemanager.aux-services</name>
   <value>mapreduce_shuffle</value>
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

​     然后本地浏览器访问 `${ip}:8088` ，查看信息管理界面，能够查看则表示成功了



**5）停止yarn**

```bash
# $HADOOP_HOME/sbin下 ,一键启动三个节点
$ > sh stop-yarn.sh
```

