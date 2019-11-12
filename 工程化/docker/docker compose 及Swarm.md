# 1.Docker Compose

## 1.1 安装方法

**1.可以参考官网 比较慢**

​	官网`：<https://docs.docker.com/compose/>

**2.自安装**

```bash
yum -y install epel-release
yum -y install python-pip
#pip install docker-compose --timeout 100
# pip 安装docker-compose可能报错readTimeOUt，可以通过把超时时间设置大一点
sudo pip --default-timeout=200 install -U docker-compose
```



## 1.2 常用文档介绍

​	 可以参考：<https://docs.docker.com/compose/compose-file/>

官网案例：

### 1.同样的前期准备

​	新建目录，比如composetest

​	进入目录，编写app.py代码

​	创建requirements.txt文件

​	编写Dockerfile

### 2.编写docker-compose.yaml文件

​	默认名称，当然也可以指定，`docker-compose.yaml`

```bash
version: '3'		 			# 表示版本
services:						#一个service表示一个container
  web:		
    build: .
    ports:
      - "5000:5000"				#相当于-p 8080:8080
    networks:
      - app-net

  redis:
    image: "redis:alpine"		#表示使用哪一个image
    networks:
      - app-net
	
networks:						#相当于docker network create app-net
  app-net:
    driver: bridge
```

### 3.启动

```bash
docker-compose up -d
```



### 4.关键字解释

> (1)version: '3'

```
表示docker-compose的版本
```

> (2)services

```
一个service表示一个container
```

> (3)networks

```
相当于docker network create app-net
```

> (4)volumes

```
相当于-v v1:/var/lib/mysql
```

> (5)image

```
表示使用哪个镜像，本地build则用build，远端则用image
```

> (6)ports

```
相当于-p 8080:8080
```

> (7)environment

```
相当于-e 
```



## 1.3 docker-compose常见操作

```bash
(1)查看版本

​	docker-compose version

(2)根据yml创建service

​	docker-compose up

​	指定yaml：docker-compose  up -f xxx.yaml

​	后台运行：docker-compose up

(3)查看启动成功的service

​	docker-compose ps

​	也可以使用docker ps

(4)查看images

​	docker-compose images

(5)停止/启动service

​	docker-compose stop/start 

(6)删除service[同时会删除掉network和volume]

​	docker-compose down

(7)进入到某个service

​	docker-compose exec redis sh
```

## 1.4 scale扩容

(1)修改docker-compose.yaml文件，主要是把web的ports去掉，不然会报错,多个扩容再单机环境下会出现端口冲突。

```bash
#通过--scale即可
docker-compose up --scale web=5 -d  
```



# 2.Docker Swarm

​	参考官网：<https://docs.docker.com/swarm/>

## 2.1 搭建docker Swarm

​	分别启动三个节点

- manger    (192.168.0.11)

- work1      (192.168.0.12)

- work2      (192.168.0.13)

**(1)进入manager**

`	提示`：manager node也可以作为worker node提供服务

```bash
docker swarm init --advertise-addr=192.168.0.11
```

`注意观察日志，拿到worker node加入manager node的信息`

```bash
docker swarm join --token SWMTKN-1-0a5ph4nehwdm9wzcmlbj2ckqqso38pkd238rprzwcoawabxtdq-arcpra6yzltedpafk3qyvv0y3 192.168.0.11:2377
```

**(2)进入两个worker**

```bash
docker swarm join --token SWMTKN-1-0a5ph4nehwdm9wzcmlbj2ckqqso38pkd238rprzwcoawabxtdq-arcpra6yzltedpafk3qyvv0y3 192.168.0.11:2377
```

日志打印

```bash
This node joined a swarm as a worker.
```

**(3)进入到manager node查看集群状态**

```bash
docker node ls
```

**(4)node类型的转换**

​	可以将worker提升成manager，从而保证manager的高可用

```bash
docker node promote worker01-node
docker node promote worker02-node

#降级可以用demote
docker node demote worker01-node
```

## 2.2 Swarm基本操作

### 2.2.1 Service

(1)创建一个tomcat的service

```bash
docker service create --name my-tomcat tomcat
```

(2)查看当前swarm的service

```bash
docker service ls
```

(3)查看service的启动日志

```bash
docker service logs my-tomcat
```

(4)查看service的详情

```bash
docker service inspect my-tomcat
```

(5)查看my-tomcat运行在哪个node上

```bash
docker service ps my-tomcat
```

(6)水平扩展service

​	如果某个node上的my-tomcat挂掉了，这时候会自动扩展

```bash
docker service scale my-tomcat=3
docker service ls
docker service ps my-tomcat
```

(7)删除service

```bash
docker service rm my-tomcat
```



### 2.2.2 多机通信overlay网络

业务场景`：workpress+mysql实现个人博客搭建

> <https://hub.docker.com/_/wordpress?tab=description>

(1)创建一个overlay网络，用于docker swarm中多机通信

```bash
【manager-node】
docker network create -d overlay my-overlay-net

docker network ls[此时worker node查看不到]
```

(2)创建mysql的service

```bash
【manager-node】
# 01-创建service
	docker service create --name mysql --mount type=volume,source=v1,destination=/var/lib/mysql --env MYSQL_ROOT_PASSWORD=examplepass --env MYSQL_DATABASE=db_wordpress --network my-overlay-net mysql:5.6

# 02-查看service
	docker service ls
	docker service ps mysql
```

(3)创建wordpress的service

```bash
# 01-创建service  [注意之所以下面可以通过mysql名字访问，也是因为有DNS解析]
	docker service create --name wordpress --env WORDPRESS_DB_USER=root --env WORDPRESS_DB_PASSWORD=examplepass --env WORDPRESS_DB_HOST=mysql:3306 --env WORDPRESS_DB_NAME=db_wordpress -p 8080:80 --network my-overlay-net wordpress

# 02-查看service
	docker service ls
	docker service ps mysql
	
#03-此时mysql和wordpress的service运行在哪个node上，这时候就能看到my-overlay-net的网络
```

(4)测试

```bash
win浏览器访问ip[manager/worker01/worker02]:8080都能访问成功
```

(5)查看my-overlay-net

```bash
docker network inspect my-overlay-net
```

(6)为什么没有用etcd？docker swarm中有自己的分布式存储机制



## 2.3 Routing Mesh

### 2.3.1 Ingress

> 通过前面的案例我们发现，部署一个wordpress的service，映射到主机的8080端口，这时候通过swarm集群中的任意主机ip:8080都能成功访问，这是因为什么？
>
> `把问题简化`：docker service create --name tomcat  -p 8080:8080 --network my-overlay-net tomcat

(1)记得使用一个自定义的overlay类型的网络

```bash
--network my-overlay-net
```

(2)查看service情况

```bash
docker service ls
docker service ps tomcat
```

(3)访问3台机器的ip:8080测试

```bash
发现都能够访问到tomcat的欢迎页
```

### 2.3.2  Internal

> 之前在实战wordpress+mysql的时候，发现wordpress中可以直接通过mysql名称访问
>
> 这样可以说明两点，第一是其中一定有dns解析，第二是两个service的ip是能够ping通的
>
> `思考`：不妨再创建一个service，也同样使用上述tomcat的overlay网络，然后来实验
>
> docker service create --name whoami -p 8000:8000 --network my-overlay-net -d  jwilder/whoami

(1)查看whoami的情况

```bash
docker service ps whoami
```

(2)在各自容器中互相ping一下彼此，也就是容器间的通信

```bash
#tomcat容器中ping whoami
docker exec -it 9d7d4c2b1b80 ping whoami
64 bytes from bogon (10.0.0.8): icmp_seq=1 ttl=64 time=0.050 ms
64 bytes from bogon (10.0.0.8): icmp_seq=2 ttl=64 time=0.080 ms


#whoami容器中ping tomcat
docker exec -it 5c4fe39e7f60 ping tomcat
64 bytes from bogon (10.0.0.18): icmp_seq=1 ttl=64 time=0.050 ms
64 bytes from bogon (10.0.0.18): icmp_seq=2 ttl=64 time=0.080 ms
```

(3)将whoami进行扩容

```bash
docker service scale whoami=3
docker service ps whoami     #manager,worker01,worker02
```

(4)此时再ping whoami service，并且访问whoami服务

```bash
#ping
docker exec -it 9d7d4c2b1b80 ping whoami
64 bytes from bogon (10.0.0.8): icmp_seq=1 ttl=64 time=0.055 ms
64 bytes from bogon (10.0.0.8): icmp_seq=2 ttl=64 time=0.084 ms

#访问
docker exec -it 9d7d4c2b1b80 curl whoami:8000  [多访问几次]
I'm 09f4158c81ae
I'm aebc574dc990
I'm 7755bc7da921
```

`小结`：通过上述的实验可以发现什么？whoami服务对其他服务暴露的ip是不变的，但是通过whoami名称访问8000端口，确实访问到的是不同的service，。

也就是说whoami service对其他服务提供了一个统一的VIP入口，别的服务访问时会做负载均衡。



## 2.4 Stack

>docker stack deploy：https://docs.docker.com/engine/reference/commandline/stack_deploy/
>
>compose-file：https://docs.docker.com/compose/compose-file/
>
>有没有发现上述部署service很麻烦？要是能够类似于docker-compose.yml文件那种方式一起管理该多少？这就要涉及到docker swarm中的Stack，我们直接通过前面的wordpress+mysql案例看看怎么使用咯。

(1)新建service.yml文件

```yml
version: '3'

services:

  wordpress:
    image: wordpress
    ports:
      - 8080:80
    environment:
      WORDPRESS_DB_HOST: db
      WORDPRESS_DB_USER: exampleuser
      WORDPRESS_DB_PASSWORD: examplepass
      WORDPRESS_DB_NAME: exampledb
    networks:
      - ol-net
    volumes:
      - wordpress:/var/www/html
    deploy:
      mode: replicated
      replicas: 3
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
      update_config:
        parallelism: 1
        delay: 10s

  db:
    image: mysql:5.7
    environment:
      MYSQL_DATABASE: exampledb
      MYSQL_USER: exampleuser
      MYSQL_PASSWORD: examplepass
      MYSQL_RANDOM_ROOT_PASSWORD: '1'
    volumes:
      - db:/var/lib/mysql
    networks:
      - ol-net
    deploy:
      mode: global
      placement:
        constraints:
          - node.role == manager

volumes:
  wordpress:
  db:

networks:
  ol-net:
    driver: overlay
```

(2)根据service.yml创建service

```bash
docker statck deploy -c service.yml my-service
```

(3)常见操作

```bash
01-查看stack具体信息
	docker stack ls
	NAME                SERVICES            ORCHESTRATOR
	my-service          2                   Swarm
	
02-查看具体的service
	docker stack services my-service
	
ID                  NAME                   MODE                REPLICAS            IMAGE               PORTS
icraimlesu61        my-service_db          global              1/1                 mysql:5.7           
iud2g140za5c        my-service_wordpress   replicated          3/3                 wordpress:latest    *:8080->80/tcp

03-查看某个service
	docker service inspect my-service-db
	
"Endpoint": {
            "Spec": {
                "Mode": "vip"
            },
            "VirtualIPs": [
                {
                    "NetworkID": "kz1reu3yxxpwp1lvnrraw0uq6",
                    "Addr": "10.0.1.5/24"
                }
            ]
        }
```

(4)访问测试

​	win浏览器ip[manager,worker01,worker02]:8080