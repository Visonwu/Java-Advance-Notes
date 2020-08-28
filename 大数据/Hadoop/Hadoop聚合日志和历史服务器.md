

## 1. 聚合日志

### 1.1 概念

**概念：**分布式计算作业的是放到nodeManager中计算，计算结果放到nodeManager本地目录中----（yarn.nodemananger.log-dirs:{yarn.log.dir}/userlogs）就是当前logs目录中;当在分布式计算中，多个nodeMananger这样就不好查看日志信息，所以通过配置将所有的本地日志都放到hdfs服务器上，这个就是就是聚合日志



### 1.2 配置

**在yarn-site.xml相关配置：**

- `yarn.log-aggregation-enable`  , 是否启用聚合日志功能,默认false
- `yarn.log-aggregation.retain-seconds`，聚合后日志在HDFS上保存时间，单位秒
- `yarn.log-aggregation.retain-check-interval-seconds` ， 定时任务检查日志是否超过保存时间的调用时间间隔，值为0/-1,是前者保存时间的1/10.

   **其他配置**

- `yarn.nodemanager.log.retain-seconds`
  
  - 当不启用聚合日志生效，即本地日志的保存时间，默认10800s
- `yarn.nodemanager.remote-app-log-dir`
  
  - 应用程序结束后，日志被转移到hdfs目录(启动聚合日志有效)，即保存在hdfs服务器中那个文件件下
- `yarn.nodemanager.remote-app-log-dir-suffix`
  
  - 远程目录子目录名称(启用日志聚合功能有效)



命令查看聚合日志

```shell
# application_1594957740884_0001 是任务id
yarn logs -applicationId application_1594957740884_0001
```





### 1.3 案例

**1）修改配置**

修改伪分布式下中`yarn-site.xml`的配置文件为：

```xml
<configuration>
    <!--启用聚合日志功能-->
    <property>
       <name>yarn.nodemanager.aux-services</name>
       <value>mapreduce_shuffle</value>
    </property>
    <property>
       <name>yarn.log-aggregation-enable</name>
       <vaule>true></value>
    </property>
        <!--存放多长时间-->
    <property>
      <name>yarn.log-aggregation.retain-seconds</name>
      <value>3600<value>
    </property>
</configuration>
```

**2）同样执行wordcount功能**

```bash
$> hadoop jar share/hadoop/mapreduce/hadoop-mapreduce-examples-2.7.7.jar wordcount file:/usr/local/hadoop/NOTICE.txt  file:/usr/local/hadoop/output
```

   可以查看控制台信息查看日志情况，点击ID或者Tracking UI下进去跟踪日志查看信息：

![upKUPO.png](https://s2.ax1x.com/2019/09/22/upKUPO.png)

**3) 可以通过手动命令查看hdfs日志文件**

```bash
# 通过返回结果逐级往下查看
$> hdfs dfs -ls /
```



## 2. 历史服务器

​	可以查看历史记录等信息

**1）在mapred-size.xml中的配置项**

- mapreduce.jobhistory.address:  jobhistory的rpc访问地址
- mapreduce.jobhistory.webapp.address     ; jobhistory的http访问地址

```xml
	<!--jobhistory rpc地址 -->    
	<property>
       <name>mapreduce.jobhistory.address</name>
       <value>ebusiness:10010</value>
     </property>
	<!--jobhistory http地址 -->   
    <property>
       <name>mapreduce.jobhistory.webapp.address</name>
       <value>ebusiness:19888</value>
     </property>
    <property>
       <name>yarn.log.server.url</name>
       <value>http://ebusiness:19888/jobhistory/logs</value>
     </property>
```



**2）启动服务器**

```bash
$> sbin/mr-jobhistory-daemon.sh  start historyserver
```

**3) WEB_UI**

​	浏览器通过该地址访问：	`http://<主机名>:19888`

**4) 关闭服务器**

```bash
$> sbin/mr-jobhistory-daemon.sh  stop historyserver
```



## 3.Java 内存

```xml
<!--java-heap 设置-->

<property>
	<name>yarn.scheduler.maximum-allocation-mb</name>
	<value>2048</value>
</property>
<property>
  	<name>yarn.scheduler.minimum-allocation-mb</name>
  	<value>2048</value>
</property>
<property>
	<name>yarn.nodemanager.vmem-pmem-ratio</name>
	<value>2.1</value>
</property>
<property>
	<name>mapred.child.java.opts</name>
	<value>-Xmx1024m</value>
</property>
```

