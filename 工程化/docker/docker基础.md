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



## 1.4 Docker 工作原理

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

