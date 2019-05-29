# 1.Docker介绍

## 1.1 Docker作用

​	**Docker**一种容器技术， Docker是基于**Go**语言实现目标：“Build，Ship and Run Any App,Anywhere”；一次封装到处运行。

**docker作用:**解决了运行环境和配置问题，方便做持续集成，有助于整体发布

- 开发与运维的鸿沟
- 减少运维的工作量

## 1.2 Docker和传统虚拟机差异

​	虚拟化技术就是**虚拟了整套环境**

- 缺点：资源占用多、启动慢

具体差异参考参考官网：<https://www.docker.com/resources/what-container>

![dockerVSVirtual](//wx4.sinaimg.cn/mw690/b8a27c2fgy1g3es0agng0j216g0gzn01.jpg)



## 1.3 Docker架构图

![dockerStructure](//wx3.sinaimg.cn/mw690/b8a27c2fgy1g3esijjly0j20ji0a5n1a.jpg)



## 1.4 Docker镜像

​	**Docker镜像是一个联合文件系统**; 拉去 一个image,会自动把相关的依赖都会拉去成功，比如pull 一个tomcat，那么会自动把java，linux系统都会pull下来。

​	另外由于docker使用自身机器的内核，所以只是单纯的拉去一个centos，会发现只有很小的空间。如果是传统的虚拟机那就是把整个内核和系统工具都拉去，所以很大有几个G左右。

![imageDocker](//ws1.sinaimg.cn/mw690/b8a27c2fgy1g3ezu4cqhej20bj07bwi0.jpg)

```bash
1.BootFS 主要包含BootLoader 和Kernel,
	BootLoader主要是引导加载Kernel, 当Boot成功后，Kernel被加载到内存中BootFS就被Umount了。
2.RootFS包含的就是典型 Linux 系统中的/dev、/proc、/bin 等标准目录和文件。
```



## 1.5 Docker 工作原理

![dockerRun](//ws1.sinaimg.cn/mw690/b8a27c2fgy1g3etfg0f17j20jd0dbgni.jpg)



# 2. Docker安装

## 2.1 资源获取

- 英文网：www.docker.com 

- 中文网：www.docker-cn.com

- 仓库地址：hub.docker.com



## 2.2 Docker在linux环境安装

​	可以查看官网的安装方式	

```bash
1.sudo yum install -y yum-utils \
device-mapper-persistent-data \
lvm2

2.sudo yum-config-manager \
--add-repo \
https://download.docker.com/linux/cen
tos/docker-ce.repo

3.sudo yum install docker-ce

4.sudo systemctl start docker

5.验证：docker version
```

```bash
6.安装仓库镜像地址，可以访问：https://cr.console.aliyun.com/cn-hangzhou/instances/mirrors
vi /etc/docker/daemon.json ：这个文件添加镜像地址，没有这个文件，新建一个
{
  "registry-mirrors": ["https://5i7mumsf.mirror.aliyuncs.com"]
}
```

