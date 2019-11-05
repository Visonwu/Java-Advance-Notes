​	



# 1.基础网络介绍

## 1.1 Linux中的网卡

**1)查看网卡**

```bash
# 如下三种方式：
root>ip link show

root> ls /sys/class/net

root> ip a
```



**2）ip a解读**

```bash
状态：UP/DOWN/UNKOWN等
link/ether：MAC地址
inet：绑定的IP地址
```

**3）配置文件**

```text
在Linux中网卡对应的其实就是文件，所以找到对应的网卡文件即可
比如：cat /etc/sysconfig/network-scripts/ifcfg-eth0
```

**4）给网卡添加地址**

当然，这块可以直接修改ifcfg-*文件，但是我们通过命令添加试试

```text
(1)添加ip地址
	ip addr add 192.168.0.100/24 dev eth0
(2)删除IP地址
	ip addr delete 192.168.0.100/24 dev eth0
```

**5） 网卡重启和关闭**

```text
重启网卡：service network restart / systemctl restart network
启动/关闭某个网卡：ifup/ifdown eth0 or ip link set eth0 up/down
```



## 1.2 network namespace

​	在linux上，网络的隔离是通过network namespace来管理的，不同的network namespace是互相隔离的

```bash
# network namespace的管理

ip netns list #查看
ip netns add ns1 #添加ns1 namespace
ip netns delete ns1 #删除ns1 namespace
```



### 1）namespace实践

(1)创建一个network namespace

```text
ip netns add ns1
```

(2)查看该namespace下网卡的情况

```bash
ip netns exec ns1 ip a
```

(3)启动ns1上的lo网卡

```text
ip netns exec ns1 ifup lo
或者
ip netns exec ns1 ip link set lo up
```

(4)再次查看

```bash
#可以发现state变成了UNKOWN
ip netns exec ns1 ip a
```

(5)再次创建一个network namespace

```text
ip netns add ns2
```

(6)此时想让两个namespace网络连通起来
	veth pair ：Virtual Ethernet Pair，是一个成对的端口，可以实现上述功能

(7)创建一对link，也就是接下来要通过veth pair连接的link

```bash
ip link add veth-ns1 type veth peer name veth-ns2
```

(8)查看link情况

```bash
ip link
# 可以看到新的一对网卡信息
	veth-ns2@veth-ns1
	veth-ns1@veth-ns2
```

(9)将veth-ns1加入ns1中，将veth-ns2加入ns2中

```text
ip link set veth-ns1 netns1
ip link set veth-ns2 netns2
```

(10)查看宿主机和ns1，ns2的link情况

```bash
# 此时会发现当前两个veth移到了ns1和ns2中；
ip link
ip netns exec ns1 ip link
ip netns exec ns2 ip link
```

(11)此时veth-ns1和veth-ns2还没有ip地址，显然通信还缺少点条件

```bash
ip netns exec ns1 ip addr add 192.168.0.11/24 dev veth-ns1
ip netns exec ns2 ip addr add 192.168.0.12/24 dev veth-ns2


```

(12)启动两个网卡

```bash
ip netns exec ns1 ip link set veth-ns1 up
ip netns exec ns2 ip link set veth-ns2 up
```

13)此时再次查看，可以看到状态是up状态，互相ping

```bash
ip netns exec ns1 ping 192.168.0.12
ip netns exec ns2 ping 192.168.0.11
```

最终的图：

[![Kz0C9g.md.png](https://s2.ax1x.com/2019/11/04/Kz0C9g.md.png)](https://imgchr.com/i/Kz0C9g)



### 2） Container中的NS(namespace）

​		按照上面的描述，实际上每个container，都会有自己的network namespace，并且是独立的，我们可以进入
到容器中进行验证

(1)不妨创建两个container看看？

```bash
docker run -d --name tomcat01 -p 8081:8080 tomcat
docker run -d --name tomcat02 -p 8082:8080 tomcat
```

(2)进入到两个容器中，并且查看ip

```bash
docker exec -it tomcat01 ip a
docker exec -it tomcat02 ip a
```

(3)互相ping一下是可以ping通的

```text
值得我们思考的是，此时tomcat01和tomcat02属于两个network namespace，是如何能够ping通的？
有些小伙伴可能会想，不就跟上面的namespace实战一样吗？注意这里并没有veth-pair技术
```



# 2.Docker容器网络介绍

## 2.1 容器网络原理

​	Docker容器网络很好利用了Linux虚拟网络技术，在本地主机和容器内分别创建了虚拟接口，并让他们彼此联通(这一对接口叫做**veth pair**)，然后在通过docker0默认bridge做连接；

容器中的应用是可以访问互联网的，NAT是通过iptables实现的；

如下：

![network](//ws1.sinaimg.cn/mw690/b8a27c2fgy1g3ihgo77fcj20ie0e977k.jpg)

1)通过在宿主机上执行 ip a可以查看到

```text
4: docker0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP group default
    link/ether 02:42:43:7b:1b:bd brd ff:ff:ff:ff:ff:ff
    inet 172.17.0.1/16 brd 172.17.255.255 scope global docker0
    valid_lft forever preferred_lft forever
    inet6 fe80::42:43ff:fe7b:1bbd/64 scope link
    valid_lft forever preferred_lft forever
```

2)查看容器tomcat01的网络：docker exec -it tomcat01 ip a，可以发现

```text
[root@bogon ~]# docker exec -it tomcat01 ip a
1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN group default qlen 1000
    link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
    inet 127.0.0.1/8 scope host lo
    valid_lft forever preferred_lft forever
7: eth0@if8: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP group default
    link/ether 02:42:ac:11:00:02 brd ff:ff:ff:ff:ff:ff link-netnsid 0
    inet 172.17.0.2/16 brd 172.17.255.255 scope global eth0
    valid_lft forever preferred_lft forever
```



(4)在tomcat01中有一个eth0和centos的docker0中有一个veth3是成对的，类似于之前实战中的veth-ns1和veth-ns2，不妨再通过一个命令确认下：brctl

```bash
安装一下：yum install bridge-utils
brctl show
```



**总结：**

在创建一个容器时

- 会创建一对虚拟接口分别放到本地和新容器的命令空间中
- 本地主机一端的虚拟接口接到默认的docker0网桥上，并具有一veth开头的唯一名字，如veth123
- 容器的一端的虚拟接口将放到新的容器中，并修改名字为eth0，这个只在容器中可见
- 从网桥的可用地址段中获取一个空闲的地址分配至容器的eth0（例如172.12.0.1/16）,并配置默认路由网关为docker0网卡的内部接口docker0的ip地址。

然后容器就可以使用自己能看到的eth0虚拟网卡连接其他容器和访问外部网络。

docker0 这种网络连接方法我们称之为Bridge；bridge也是docker中默认的网络模式；

## 2.2 容器网络详细介绍

### 2.2.1 docker中的网络模式

可以在docker运行的时候通过--net参数指定容器的网络配置，可选有bridge、host、container 、none

- --net=bridge  : 默认值，在Docker网桥上为容器创建新的网格栈
- --net= host   :告诉Docker不要将容器网络放到隔离的命令空间中，即不要容器化容器内的网络。辞职使用本地主机的网络，拥有本地主机接口的访问权限。。这个对于本地主机较为危险。需要小心
- --net=container  :让Docker将新建容器的进程放到一个已存在容器的网络栈中，新容器进程有自己的文件系统、进程列表和资源限制，但和已存在的容器共享IP地址和端口等网络资源，亮这个直接通过lo环回接口通信
- --net=none :让Docker将所有新容器放到隔离的网络栈中，但是不进行网络配置，由用户自己配置。完全隔离，目前没有看到应用实践

**1）查看docker中的网络模式**

```shell
# 查看网络模式；默认有 brige，host，null
docker network ls


```

**2）检查网络模式，**

​	查看其他host类似 docker network inspect host

```shell
docker network inspect bridge
# 这里当前brige网络中的信息包含网关，及对应的默认容器的ip信息
```



### 2.2.2 docker用新建的network创建应用

(1)创建一个network，类型为bridge,默认是bridge

```bash
docker network create tomcat-net
或者
docker network create --subnet=172.18.0.0/24 tomcat-net
```

(2) 查看自己创建得network

```bash
docker network ls  #可以查看多了一个tomcat-net网络

docker network inspect tomcat-net # 查看tomcat-net详情信息，包含子网信息和网关信息
```

(3)创建tomcat的容器，指定相应的网络tomcat-net

```bash
# --network 只当对应的网络
docker run -d --name custom-net-tomcat --network tomcat-net tomcat
```

(4)查看custom-net-tomcat的网络信息

```bash
docker exec -it custom-net-tomcat ip a
```

(5)查看网卡信息

```bash
ip a
```

(6)查看网卡接口

```bash
brctl show
```

(7)如果本机默认启动一个tomcat0，会发现通过本机的tomcat0访问自定义网络中的应用无法ping通，原因是他们没有在同一个网络中；

此时如果主机中的tomcat0能够连接tomcat-net网络的化，那么就可以ping通tomcat-net里面的应用了

```bash
# 将tomcat01也设置到tomcat-net网络中来；
# 查看tomcat-net网络，可以发现tomcat01这个容器也在其中
# 再次查看tomcat0的网路，发现tomcat01多了一个网卡信息及对应ip地址
docker network connect tomcat-net tomcat01
```

(8) 此时进入到tomcat01或者custom-net-tomcat中，不仅可以通过ip地址ping通，而且可以通过名字ping
到，这时候因为都连接到了用户自定义的tomcat-net bridge上

```bash
docker exec -it tomcat01 bash

root@12f2>ping custom-net-tomcat
# 这个也是可以ping通的
```



（9）反之，在宿主机中通过容器名 ping 平级的其他应用，是ping不同的。



### 2.2.3 结论

​	相关的网络执行命令以及各个容器的通信

```bash
#1.可以创建一个新的network供自己使用
docker network create [network-name]

#2.查看网络
docker network ls

#3.将contaniner-name应用添加到另外一个网络中，相当于又给当前容器分片一个其他网络段中的ip
docker network connect [network-name] [container-name]

#4.启动容器自定义连接某一个网络
 --network [network-name]
 例如：docker run -d --name tomat-custom --network my-network tomat

#5.在宿主机中即dokcer0网络中，不可以通过容器名 连接其他容器

#6.自定义的网络，单机环境中，可以直接通过容器名连接其他应用，这也是单机环境中各个容器能够通过的一个方法，特别是在不确定容器ip的时候。

```

