## 1.资源调度器

![NXi0fA.png](https://s1.ax1x.com/2020/07/03/NXi0fA.png)

不同版本的hadoop的默认不同，`apache`默认Capacity, `CDH版本`使用Fair Scheduler具体看配置

- **FIFO Scheduler：**
  - 按照应用的顺序排成一个先进先出的队列，这个比较好理解
- **Capacity Scheduler：**
  - 预先划分几个队列，每个队列按照FIFO或者DRF方式分配资源
- **Fair Scheduler：**
  - 动态划分或者预先划分几个队列，每个队列按照Fair或者FIFO或者DRF方式分配资源

**DRF算法：**主资源公平算法，按照不同的资源进行分配，有两种，比如CPU资源，内存资源

![uQo9W4.png](https://s2.ax1x.com/2019/09/28/uQo9W4.png)

- 单用户应用会在一个队列中，在第二任务来了分配一点资源给第二个任务执行

- 多用户是，第二个队列中分配资源，如果第三个任务来了，会在第二个队列中在分配一半资源给第三个任务



```text
yarn.site.xml中的配置有：
yarn.resourcemanager.scheduler.class:	org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.CapacityScheduler
```



### 1.1  Capacity Scheduler配置

**在capacity-scheduler.xml中配置**

```text
#根root队列拆分为prod，dev两个环境的队列（正式和开发）
yarn.scheduler.capacity.root.queues:prod,dev
#dev队列在拆分为eng，science两个子队列
yarn.scheduler.capacity.root.dev.queues:eng,science
#生产环境使用的资源40%
yarn.scheduler.capacity.root.prod.capacity:40 
#开发环境使用的资源60%
yarn.scheduler.capacity.root.dev.capacity:60 
#开发最大使用75%
yarn.scheduler.capacity.root.dev.maximum-capacity:75 

#开发环境dev两个子队列各占50%
yarn.scheduler.capacity.root.dev.eng.capacity:50 
yarn.scheduler.capacity.root.dev.science.capacity:50 
```

如下：

```xml
<configuration>
  <property>
    <name>yarn.scheduler.capacity.root.queues</name>
    <value>prod,dev</value> 
  </property>
   <property>
    <name>yarn.scheduler.capacity.root.dev.queues</name>
    <value>eng,science</value> 
  </property>
  <property>
    <name>yarn.scheduler.capacity.root.prod.capacity</name>
    <value>40</value> 
  </property>
  <property>
    <name>yarn.scheduler.capacity.root.dev.capacity</name>
    <value>60</value> 
  </property>
  <property>
    <name>yarn.scheduler.capacity.root.dev.maximum-capacity</name>
    <value>75</value> 
  </property>
  <property>
    <name>yarn.scheduler.capacity.root.dev.eng.capacity</name>
    <value>50</value> 
  </property>
  <property>
    <name>yarn.scheduler.capacity.root.dev.science.capacity</name>
    <value>50</value> 
  </property>
</configuration>
```



**yarn-site.xml中的配置：**

```xml
yarn.resourcemanager.scheduler.class:	org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.CapacityScheduler
```



​		然后重新启动yarn节点，通过<http://192.168.124.158:8088/>查看控制台信息，注意scheduler可以查看到我们自定义的节点信息，默认是default队列

执行一个计算任务，比如如下统计字数，再次查看控制台情况

```bash
sh bin/hadoop jar share/hadoop/mapreduce/hadoop-mapreduce-examples-2.7.7.jar wordcount   -Dmapreduce.job.queuename=eng  file:/usr/local/hadoop/LICENSE.txt file:/usr/local/hadoop/output

# -Dmapreduce.job.queuename=eng 表示由eng队列执行
```



### 1.2 Fair Scheduler配置

​	新建文件fair-scheduler.xml配置，注意一点就是之前的yarn-site.xml和capacity-scheduler.xml配置都还是默认情况下

```xml
<?xml version="1.0" encoding="UTF-8"?>
<allocations>
	<!--队列调度方式-->
	<defaultQueueSchedulingPolicy>fair</defaultQueueSchedulingPolicy>
	<queue name="prod">
		<!--权重划分作为公平调度依据，若是动态划分则权重都为1-->
		<weight>40</weight>
		<!--当前队列的调度方式-->
		<schedulingPolicy>fifo</schedulingPolicy>
	</queue>
	<queue name="dev">
		<weight>60</weight>
		<queue name="eng"/>
		<queue name="science"/>
	</queue>
	
	<!--基于下面的规则将不同的应用作业放到不同的队列中，
			默认的话是基于用户的名自己新建一个队列执行-->
	<queuePlacementPolicy>
		<rule name="specified" create="false" /> <!--应用自己指定队列，比如启动应用自己用-D启动应用-->
		<rule name="primaryGroup" create="false" /> <!--根据当前机器得用户名建立一个队列-->
		<rule name="default" create="dev.eng" /> <!--前两者不满足， 自己指定队列-->
	</queuePlacementPolicy>
</allocations>
```

然后重启yarn，在执行一个任务查看控制台具体情况



## 2. 资源隔离

资源隔离分为：CPU隔离和内存隔离

Yarn支持两种实现：

- DefaultContainerExecutor：不支持CPU资源隔离
- LinuxContainerExecutor：这个使用Cgroup的方式支持CPU隔离（建议不用Cgroup隔离内存，否则当内存大于预定义的上限值的时候，使用Cgroup进行内存隔离会强制杀死进程）

两者内存的隔离都是使用**线程监控**的方式实现。



### 2.1 内存隔离（线程监控）

MonitoringThread 线程每隔一段时间扫描正在运行的Container进程

应用程序配置参数：

​	mapreduce.map.memory.mb：MapReduceMapTask需要使用的内存量（单位：MB)

NodeManager配置：

​	yarn.nodemanager.pmem-check-enabled:NodeManager是否启用物理内存监控，默认true

   yarn.nodemanager.mem-check-enabled:NodeManager是否启用虚拟内存监控，默认true

​    yarn.nodemanager.mem-pmem-ratio:NodeManager虚拟内存和物理内存的比例，默认2:1

​    yarn.nodemanager.resource.memory-mb:NodeManager最多可以使用多少物理内存，默认8G



### 2.2 CPU隔离（Cgroup)

cgroup以组为单位隔离资源，同一个组可以使用的资源相同，这里的配置是在cgroup程序目录中配置

​	在cgroup的 CPU目录创建分组，yarn默认使用hadoop-yarn组作为最上层，任务运行的yarn会为每个container在hadoop-yarn里面创建一个组

yarn使用cgroup的两种方式来控制CPU资源：

- 严格按照核数隔离资源：可使用核数=cpu.cfs_quota_us/cpu.cfs_period_us，根据任务申请的core数计算cpu.cfs_period_us
- 按照比例隔离资源：按照每一个分组cpu.shares的比率来分配cpu，比如A,B,C三个分组，cpu.shares分别为1024 1024 2048,那么他们的比率就是1:1:2

注：创建完分组后只需要将限制的进程id写入tasks文件即可，如若需要解除限制，在tasks文件中删除该进程id即可。



相关配置参考：<https://www.jianshu.com/p/e283ab7e2530>