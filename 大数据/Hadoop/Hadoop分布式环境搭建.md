这里的分布式就是：多个进程放到不同机器运行



## 0.解读环境搭建

```text
1.操作系统环境准备:
	1.1 依赖原件ssh，jdk
	1.2 环境配置：java-home，免密钥
	1.3 时间同步（心跳需要解决时间问题）
	1.4 hosts，hostname

2.Hadoop配置:
	2.1 /opt/vison/
	2.2 配置文件修改：java-home （解决之前免密钥，远程执行不加载/etc/profile/环境变量的问题）
	2.3 角色在哪里启动：配置文件需要反映出来
```



**机器规划**

| 机器一        node1 192.168.124.152 | 机器二       node2 192.168.124.153 | 机器三     node3 192.168.124.154 |
| ----------------------------------- | ---------------------------------- | -------------------------------- |
| NameNode                            |                                    |                                  |
| DataNode                            | DataNode                           | DataNode                         |
|                                     |                                    | SecondaryNameNode                |
|                                     | ResourceManager                    |                                  |
| NodeManager                         | NodeManager                        | NodeManager                      |



设置各自的主机名 hostname设置，然后配置各自的hosts

```bash
192.168.124.152 node1
192.168.124.153 node2
192.168.124.154 node3
```

---

---



## 1.NTP时间服务器同步

 同步各个的服务器的时间一致；当然这个也可以不配置，个人认为自己搭建可以不配置。

这里然机器一当成时间服务器，机器2/3保持机器一的时间

### 1.1 三台机器共同操作

1）安装

```bash
$> yum -y install ntp
```



2）设置时钟同步开启

```bash
$> vim /etc/sysconfig/ntpd
SYNC_HWCLOCK=yes
```



### 1.2 设置主时间服务器

这里主服务器用node1

**1) 修改配置**

```bash
$> vim /etc/ntp.conf
```

```sh
# Hosts on local network are less restricted.
# 这个是修改原有的,这里配置的是当前的网段，以及子掩码
restrict 192.168.124.0 mask 255.255.255.0 nomodify notrap

#这个是新增的，打开注释
server 127.127.1.0
fudge  127.127.1.0 stratum 10

```

**2）然后重启服务**

```bash
$> systemctl restart ntpd
```



### 1.3 然后在node2/node3通过手动同步的方式试一下

```bash
$> /usr/sbin/ntpdate node1
```



### 1.4  node2/node3定时同步

分别在node2和node3设置定时器去同步时间

```bash
$> crontab -e

#配置内容，10分钟定时执行同步一次
0-59/10 * * * * /usr/sbin/ntpdate node1
```



## 2. 开始Hadoop分布式配置

同单机模式一样

步骤一：分别解压文件

步骤二：分别添加环境变量HADOOP_HOME

步骤三：分别修改/etc/hadoop/hadoop-env.sh中JAVA_HOME的绝对路径



**步骤四：在node1节点中的配置文件中配置**

`hadoop/etc/hadoop/core-site.xml`

​	注意配置：”hadoop.tmp.dir“：这个是放在linux的tmp临时目录中，很容易被清理掉，nameNode的持久化数据也是放在里面的。很重要

```xml
<!-- 配置hdfs 配置当前机器node1为namenode -->
<configuration>
  <property>
    <name>fs.defaultFS</name>
    <value>hdfs://node1:8020</value>
  </property>
  <property>  <!--设置数据存储目录-->
    <name>hadoop.tmp.dir</name>
    <value>/var/vison/local</value>
  </property>
</configuration>
```

`hadoop/etc/hadoop/hdfs-site.xml`

```xml
<!-- 配置node3为secondearyNameNode -->
<configuration>
 <property>
   <name>dfs.namenode.secondary.http-address</name>
   <value>node3:50090</value>
 </property>
</configuration>
```

`$> vim hadoop/etc/hadoop/slaves` 设置从节点，即设置DataNode和NodeManage节点的位置

#去掉原来的localhost

```xml
node1
node2
node3
```

`hadoop/etc/hadoop/core-site.xml`，这里配置yarn信息

```xml
<configuration>
    <!-- 配置yarn的作业类型mapreduce_shuffle -->
     <property>
       <name>yarn.nodemanager.aux-services</name>
       <value>mapreduce_shuffle</value>
     </property>
    
    <!-- 配置node2为yarn的resourManager -->
     <property>     
      <name>yarn.resourcemanager.hostname</name>
      <value>node2</value>
     </property>
</configuration>
```

`$> cp mapred-site.xml.template  mapred-site.xml`

`$> vim hadoop/etc/hadoop/mapred-site.xml`

```xml
 <!-- 设置yarn 作为环境调度-->
<configuration>
     <property>
       <name>mapreduce.framework.name</name>
       <value>yarn</value>
     </property>
</configuration>
```

配置yarn-site.xml之mapreduce-shuffle

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



**步骤五：将node1节点中的配置文件copy到其他node2/node3**

```bash
# 在当前hadoop/etc/hadoop/ 目录下
scp -r * root@192.168.124.153:/usr/local/hadoop/etc/hadoop
scp -r * root@192.168.124.154:/usr/local/hadoop/etc/hadoop
```



**步骤六：配置各个服务之间免密码输入**

​	避免在启动的时候要不停的输入密码，三个节点都要做如下所示

1）生成密钥匙对

```bash
$> cd /root
$> ssh-keygen -t rsa
```

2）将公钥拷贝，也是需要copy给自己的 

```bash
$> cd .ssh
$> ssh-copy-id node1
$> ssh-copy-id node2
$> ssh-copy-id node3
```



**步骤六：启动**

​	记得先格式化NameNode`hdfs namenode format`

​     在节点node1启动hdfs，会把三个几点的对应hdfs都启动起来，可以通过jps查看

```bash
# hadoop/sbin
$> sh start-dfs.sh
```

​    在节点node2启动yarn，会把三个几点的对应yarn都启动起来，可以通过jps查看

```bash
# hadoop/sbin
$> sh start-yarn.sh
```



**步骤七：浏览器访问**

​	通过node1节点访问`192.168.124.152:50070`

​	通过node2节点访问`192.168.124.153:8088`

如果上面不能访问8088 端口,在/etc/hosts中注释掉localhost到127.0.0.1的解析以及localhost转向当前ip地址

```test

#127.0.0.1	ebusiness1	ebusiness1
#127.0.0.1   localhost localhost.localdomain localhost4 localhost4.localdomain4
#::1         localhost localhost.localdomain localhost6 localhost6.localdomain6
192.168.199.132 localhost
```

