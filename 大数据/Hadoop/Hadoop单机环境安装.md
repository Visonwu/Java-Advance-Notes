****



## Hadoop的单机环境安装步骤

**Hadoop前提需要安装好Jdk（建议1.8以上）**

**1) 官网下载：tar.gz安装包** 

​	<https://www.apache.org/dyn/closer.cgi/hadoop/common/hadoop-2.7.7/hadoop-2.7.7.tar.gz>

​		例如下载：hadoop-2.7.7.tar.gz  



**2) 解压**

```bash
tar -zxvf hadoop-2.7.7.tar.gz  
```

​	解压出来的文件目录中 etc/hadoop是主要的配置文件放置处

**3) 移动到/usr/local**

​	移动到那个目录下看自己的个人习惯

```bash
mv hadoop-2.7.7 /usr/local

```



**4）进入/urs/local重新命名hadoop**

```bash
mv hadoop-2.7.7 hadoop
```



**5）配置hadoop环境变量**

```bash
$>vim /etc/profile

export HADOOP_HOME=/usr/local/hadoop
export PATH=$PATH:$HADOOP_HOME/bin  

$> source /etc/profile
```

​	当前我这里/etc/profile最终的结果展示为这样

```properties
export JAVA_HOME=/home/jdk1.8.0_211
export HADOOP_HOME=/usr/local/hadoop
export PATH=$PATH:$JAVA_HOME/bin:$HADOOP_HOME/bin:$HADOOP_HOME/sbin
export CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar
```



**6）进入/urs/local/hadoop执行自带的案例**

```bash
# 在Hadoop路径下创建input目录
$> mkdir input
# 将hadoop的配置拷贝到刚创建的input目录下
$> cp etc/hadoop/*.xml input
# 对input路径下的文件执行Hadoop自带示例中的MapReduce程序，并将输出写入到output目录中
$> bin/hadoop jar share/hadoop/mapreduce/hadoop-mapreduce-examples-2.7.7.jar grep input/ output/ 'dfs[a-z.]+' 
$> cat output/*

```

单机环境配置基本成功了，也可以查看到对应的案例了

