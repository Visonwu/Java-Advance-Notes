1.使用jdk，Hbase即可

Hbase下载地址：<https://mirror.bit.edu.cn/apache/hbase/2.1.9/>

详细的Hbase+Hadoop集群搭建参考：[https://www.cnblogs.com/clsn/p/10300487.html#hadoophbase-%E9%9B%86%E7%BE%A4%E6%90%AD%E5%BB%BA](https://www.cnblogs.com/clsn/p/10300487.html#hadoophbase-集群搭建)



# 1.解压

```sh
tar -zxvf ....tar.gz
```



# 2.修改文件

​	解压完成之后需要修改conf目录下 hbase-env.sh 和 hbase-site.xml 两个配置文件

## 2.1 修改 hbase-env.sh文件

```sh
# 指定jdk的路径
export JAVA_HOME=/usr/local/jdk1.8

#表示启用hbase自带的zookeeper
export HBASE_MANAGES_ZK=true
```

## 2.2 修改 hbase-site.xml 文件

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



# 3.启动

进入bin目录中，执行./start-hbase.sh就ok了



# 4.开启hbase客户端

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

