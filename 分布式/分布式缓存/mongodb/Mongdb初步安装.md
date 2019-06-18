# 1.下载

```bash
#下载
wget https://fastdl.mongodb.org/linux/mongodb-linux-x86_64-3.0.6.tgz

#解压
tar -zxvf mongodb-linux-x86_64-3.0.6.tgz
#移动
mv  mongodb-linux-x86_64-3.0.6/ /usr/local/mongodb

#设置环境变量
export PATH=/usr/local/mongodb/bin:$PATH
```



# 2. 启动

```bash
##在/usr/local/mongodb下

#创建数据库目录
mkdir -p /data/db

#创建日志
mkdir logs

#配置文件
vim mongodb.conf
	#然后添加如下配置
	#数据地址
	dbpath=/usr/local/mongoDB/mongodbserver/data
	logpath=/usr/local/mongoDB/logs/mongodb.log
	#端口
	port=27017
	# 后台fork子进程运行
	fork=true
	# 所有ip都可以访问
	bind_ip=0.0.0.0
	
# 启动
mongod --config mongodb.conf
```

