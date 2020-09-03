

# 1.Tez介绍

Tez是一个Hive的运行引擎，性能优于MR。为什么优于MR呢？看下图。

![wC3mbq.png](https://s1.ax1x.com/2020/09/03/wC3mbq.png)

​		用Hive直接编写MR程序，假设有四个有依赖关系的MR作业，上图中，绿色是Reduce Task，云状表示写屏蔽，需要将中间结果持久化写到HDFS。

​		Tez可以将多个有依赖的作业转换为一个作业，这样只需写一次HDFS，且中间节点较少，从而大大提升作业的计算性能。

# 2.安装使用

这里 使用hive配置tez引擎做计算

## 1）下载tez的依赖包

​	<http://tez.apache.org>

## 2）解压操作

```
1.拷贝apache-tez-0.9.1-bin.tar.gz到ebusiness1机器的/opt/software目录
2.将apache-tez-0.9.1-bin.tar.gz上传到HDFS的/tez目录下
	root> hadoop fs -mkdir /tez
	hadoop fs -put /opt/software/apache-tez-0.9.1-bin.tar.gz/ /tez
3.解压
	 tar -zxvf apache-tez-0.9.1-bin.tar.gz -C /opt/module
     mv apache-tez-0.9.1-bin/ tez-0.9.1
```



## 3）配置

### 3.1) 配置 hive/conf/tez.site.xml

进入到Hive的配置目录：/opt/module/hive/conf，下面创建一个tez-site.xml文件;配置如下内容	

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <?xml-stylesheet type="text/xsl" href="configuration.xsl"?>

    <configuration>
    <!--配置tez安装包在hdfs的位置，便于tez运算的时候可以拉取到对应的包-->    
    <property>
        <name>tez.lib.uris</name>
        <value>${fs.defaultFS}/tez/apache-tez-0.9.1-bin.tar.gz</value>
    </property>

    <property>
         <name>tez.use.cluster.hadoop-libs</name>
         <value>true</value>
    </property>

    <property>
         <name>tez.history.logging.service.class</name>        		<value>org.apache.tez.dag.history.logging.ats.ATSHistoryLoggingService</value>
    </property>

    </configuration>
```

### 3.2) 配置 hive/conf/hive-env.sh

​	在hive-env.sh文件中添加tez环境变量配置和依赖包环境变量配置

```shell
# Set HADOOP_HOME to point to a specific hadoop install directory
export HADOOP_HOME=/opt/module/hadoop-2.7.2

# Hive Configuration Directory can be controlled by:
export HIVE_CONF_DIR=/opt/module/hive/conf

# Folder containing extra libraries required for hive compilation/execution can be controlled by:

export TEZ_HOME=/opt/module/tez-0.9.1    #是你的tez的解压目录
export TEZ_JARS=""
# 两个for循环构建TEZ的jar包全局变量
for jar in `ls $TEZ_HOME |grep jar`; do
    export TEZ_JARS=$TEZ_JARS:$TEZ_HOME/$jar
done

for jar in `ls $TEZ_HOME/lib`; do
    export TEZ_JARS=$TEZ_JARS:$TEZ_HOME/lib/$jar
done

# 需要用HIVE_AUX_JARS_PATH导出这里jar包
export HIVE_AUX_JARS_PATH=/opt/module/hadoop-2.7.2/share/hadoop/common/hadoop-lzo-0.4.20.jar$TEZ_JARS
```



### 3.3）配置hive.site.xml

​	在hive-site.xml文件中添加如下配置，更改hive计算引擎及元数据检查

```xml
<!--修改计算引擎为tez-->
<property>
    <name>hive.execution.engine</name>
    <value>tez</value>
</property>

<!--关闭元数据检查，避免hive和tez版本不同报异常错误-->
<property>
    <name>hive.metastore.schema.verification</name>
    <value>false</value>
</property>    
```

### 3.4）配置hadoop的yarn-site.xml

关掉虚拟内存检查，修改yarn-site.xml，修改后一定要分发，并重新启动hadoop集群。

解决运行Tez时检查到用过多内存而被NodeManager杀死进程问题：

```xml
<property>
	<name>yarn.nodemanager.vmem-check-enabled</name>
	<value>false</value>
</property>
```



## 4）测试

```bash
1）启动Hive
[ hive]$ bin/hive

2）创建表
hive (default)> create table student( id int, name string);

3）向表中插入数据
hive (default)> insert into student values(1,"zhangsan");

4）如果没有报错就表示成功了
hive (default)> select * from student;
1       zhangsan
```



