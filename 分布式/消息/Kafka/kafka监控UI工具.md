Kafka监控工具有kafka-tools,Kafka-Manager,Kafka-monitor



# 1.Kafka-tools

在windows使用的软件；这个工具直接下载一个.exe文件就可以直接使用，通过连接zk可以获取kafka的信息





# 2.Kafka-Manager

下载 Kafka-Manager 安装包.zip，然后解压

1. 修改配置文件conf/application.conf中zoopkeeper的ip:port数据

```properties
kafka-manager.zkhosts="ebusiness1:2181,ebusiness2:2181,ebusiness3:2181"
kafka-manager.zkhosts=${?ZK_HOSTS}
```

2. 启动脚本

```shell
bin/kafka-manager >start.log 2>&1 &
```



3. 访问9000端口

   ​	192.168.199.132:9000

