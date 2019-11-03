# 1.Container 深入分析

## 1.1 container反制作Image

​		其实可以理解为container只是基于image之后的layer而已，也就是可以通过docker run image创建出一个container出来。既然container是基于image之上的，想想是否能够由一个container反推出image呢？

​		肯定是可以的，比如通过docker run运行起一个container出来，这时候对container对一些修改，然后再生成一个新的image，这时候image的由来就不仅仅只能通过Dockerfile咯。



```text
(1)拉取一个centos image
	docker pull centos
(2)根据centos镜像创建出一个container
	docker run -d -it --name my-centos centos
(3)进入my-centos容器中
	docker exec -it my-centos bash
(4)输入vim命令
	bash: vim: command not found
(5)我们要做的是
	对该container进行修改，也就是安装一下vim命令，然后将其生成一个新的centos
(6)在centos的container中安装vim
	yum install -y vim
(7)退出容器，将其生成一个新的centos，名称为"vim-centos-image"
	docker commit my-centos vim-centos-image
(8)查看镜像列表，并且基于"vim-centos-image"创建新的容器
	docker run -d -it --name my-vim-centos vim-centos-image
(9)进入到my-vim-centos容器中，检查vim命令是否存在
	docker exec -it my-vim-centos bash
	vim
```

​	结论：可以通过docker commit命令基于一个container重新生成一个image，但是一般得到image的方式不建议这么做，不然image怎么来的就全然不知咯。



## 1.2 container资源限制

​	如果不对container的资源做限制，它就会无限制地使用物理机的资源，这样显然是不合适的。
查看资源情况：docker stats



### 1) 内存限制

```bash
--memory Memory limit;
如果不设置 --memory-swap，其大小和memory一样
命令：
docker run -d --memory 100M --name tomcat1 tomcat
```



### 2) CPU限制

```text
--cpu-shares 权重 表示权重
docker run -d --cpu-shares 10 --name tomcat2 tomcat
```



### 3) 图像化监控

使用weaveworks 来使用https://github.com/weaveworks/scope

```bash
# 拉取并启动
    sudo curl -L git.io/scope -o /usr/local/bin/scope
    sudo chmod a+x /usr/local/bin/scope
    scope launch [ip]
    
# 停止scope
scope stop
# 同时监控两台机器，在两台机器中分别执行如下命令
scope launch ip1 ip2
```



## 1.3 docker container 常见操作

```text
(1)根据镜像创建容器
	docker run -d --name -p 9090:8080 my-tomcat tomcat
(2)查看运行中的container
	docker ps
(3)查看所有的container[包含退出的]
	docker ps -a
(4)删除container
    docker rm containerid
    docker rm -f $(docker ps -a) 删除所有container
(5)进入到一个container中
	docker exec -it container bash
(6)根据container生成image
	docker commit [src-image-name] [tar-image-name]
 (7)查看某个container的日志
	docker logs container
(8)查看容器资源使用情况
	docker stats
(9)查看容器详情信息
	docker inspect container
(10)停止/启动容器
	docker stop/start container   
```



## 1.4 Container底层

​	Container是一种轻量级的虚拟化技术，不用模拟硬件创建虚拟机。
​	Docker是基于Linux Kernel的Namespace、CGroups、UnionFileSystem等技术封装成的一种自定义容器格式，从而提供一套虚拟运行环境。

```text
Namespace：用来做隔离的，比如pid[进程]、net[网络]、mnt[挂载点]等
CGroups: Controller Groups用来做资源限制，比如内存和CPU等
Union file systems：用来做image和container分层
```

