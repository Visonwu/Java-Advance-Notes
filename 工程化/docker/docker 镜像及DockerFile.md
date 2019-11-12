**Dockerfile**

​		除了通过在原有的镜像加以定制化，然后通过commit,push到镜像仓库，生成自定义的镜像，还可以通过Dockerfile快速构建自定义的镜像。



# 1.DockerFile 

## 1.1 学习方式：

- 1.进入镜像仓库[https://hub.docker.com](https://hub.docker.com/)

- 2.搜索一个镜像(例如tomcat)
- 3.然后通过选择不同的版本都dockerfile学习研究

- 4.官方的dockerFile的image地址：https://github.com/docker-library

## 1.2. Dockerfile相关命令

​	**注意点：**

- **关键字都是大写**
- Dockerfile单独放在一个文件夹中

```bash
构建好了通过 docker build <选项> dockerfile路径 

-t <tag> :表示生成镜像的标签
```



| 命令                                          | 说明                                                         |
| --------------------------------------------- | ------------------------------------------------------------ |
| FROM <iamge> 或者<image>:<tag>                | 基础镜像，当前镜像的是基于哪一个镜像                         |
| MAINTAINER <name>                             | 镜像的维护者和邮箱地址                                       |
| RUN <command> 或者 [executable,param1,param2] | 镜像构建时需要运行的命令,在镜像内部执行一些命令，比如安装软件，配置环境等，换行可以使用""<br />比如：RUN groupadd -r mysql && useradd -r -g mysql mysql |
| WORKDIR                                       | 容器创建后，默认在哪个目录；为后续的RUN,CMD,ENTRYPOINT指令配置工作目录；如果<br>WORKDIR /a  ; WORKDIR b ;WORKDIR c  ;RUN pwd 最终目录为/a/b/c |
| EXPOSE <port> [<port> ...]                    | 指定镜像要暴露的端口，启动镜像时，可以使用-p将该端口映射给宿主机 |
| ENV <key> <value>                             | 用来构建镜像是设置环境变量；可以通过docker run --e key=value修改，后面可以直接使用${MYSQL_MA JOR}，比如：<br />ENV MYSQL_MAJOR 5.7 |
| LABEL                                         | 设置镜像标签<br />比如：LABEL email="visonws@163.com"        |
| ADD <src> <dest>                              | 将宿主的目录的文件copy进镜像;并且ADD命令会自动解压压缩包     |
| COPY <src> <dest>                             | 将宿主的目录的文件copy进镜像                                 |
| VOLUME                                        | 创建一个从本地主机或者其他容器挂载的挂载点-容器数据卷，用来保存和持久化；<br/>比如：VOLUME /var/lib/mysql  表示容器中该目录存储数据，并且我们宿主机有对应的目录和该目录挂载，通过docker volumn ls查看具体信息 |
| CMD  [executable,param1,param2]               | docker容器启动运行命令：多条cmd命令只有最后一条生效<br/>**CMD命令会被docker run 之后的参数替换**<br />CMD ["java","-jar","dockerfile-image.jar"] |
| ENTRYPOINT                                    | 指定容器启动过程中需要运行的命令<br/>多条cmd命令只有最后一条生效<br/>**ENTRYPOINT会把docker run 命令的参数追加到后面**:<br />比如：ENTRYPOINT ["docker-entrypoint.sh"] |
| ONBUILD  [INSTRUCTION]                        | 配置当所创建的镜像作为其他新创建镜像的基础镜像，所执行的操作指令<br/>       在一个镜像中用 ONBUILD修饰了相关命令，然后把这个dockerFile保存为一个镜像，当其他镜像通过FROM 把这个镜像作为基础镜像，会自动自行ONBUILD修饰的命令 |

## 1.3 使用Dockerfile部署项目

在当前目录创建Dockerfile文件，然后粘贴如下命令

```bash
#基础镜像
FROM java:8
#维护者
MAINTAINER vison
#容器挂载目录
VOLUME /usr/local/wmled
#当前Dockerfile下的jar包拷贝进镜像中
COPY demo-0.0.1-SNAPSHOT.jar wmled.jar
#开放端口
EXPOSE 80 8080 9092 6379 8085
#启动程序 或者CMD ["java","-jar","wmled.jar"]
ENTRYPOINT java -jar wmled.jar

```

通过`docker build -t my-led .`就可以创建好当前的镜像了 。



# 2.镜像仓库

## 2.1 官方仓库docker hub

官方网址：`hub.docker.com`

```text
(1)在docker机器上登录
		docker login
(2)输入用户名和密码
(3)docker push visonws2019/test-docker-image
	 [注意镜像名称要和docker id一致，不然push不成功]
(4)给image重命名，并删除掉原来的
	docker tag test-docker-image visonws2019/test-docker-image
    docker rmi -f test-docker-image
(5)再次推送，刷新hub.docker.com后台，发现成功
(6)别人下载，并且运行
    docker pull visonws2019/test-docker-image
    docker run -d --name user01 -p 6661:8080 visonws2019/test-docker-image
```



## 2.2 阿里云docker hub

>阿里云docker仓库：https://cr.console.aliyun.com/cn-hangzhou/instances/repositories
>参考手册：https://cr.console.aliyun.com/repository/cn-hangzhou/dreamit/image-repo/details



```text
(1)在阿里云的容器镜像服务中创建命名空间，比如visonws

(2)登录到阿里云docker仓库
	sudo docker login --username=635379261@qq.com registry.cnhangzhou.aliyuncs.com

(3)输入密码

(4)给image打tag，tag命名：阿里云/仓库名/image对象：版本
	sudo docker tag [ImageId] registry.cn-hangzhou.aliyuncs.com/visonws/testdocker-image:v1.0

(5)推送镜像到docker阿里云仓库
	sudo docker push registry.cn-hangzhou.aliyuncs.com/visonws/test-dockerimage:v1.0

(6)别人下载，并且运行
	docker pull registry.cn-hangzhou.aliyuncs.com/visonws/test-dockerimage:v1.0
	docker run -d --name user01 -p 6661:8080 registry.cnhangzhou.aliyuncs.com/visonws/test-docker-image:v1.0
```



## 2.3 自己搭建Docker Harbor

```text
(1)访问github上的harbor项目
	https://github.com/goharbor/harbor
(2)下载版本，比如1.7.1
	https://github.com/goharbor/harbor/releases
(3)找一台安装了docker-compose[这个后面的课程会讲解]，上传并解压
	tar -zxvf xxx.tar.gz
(4)进入到harbor目录
    修改harbor.cfg文件，主要是ip地址的修改成当前机器的ip地址
    同时也可以看到Harbor的密码，默认是Harbor12345;账号：admin
(5)安装harbor，需要一些时间
	sh install.sh 
(6)浏览器访问，比如192.168.199.151，输入用户名和密码即可 默认是Harbor12345;账号：admin
```

如果通过http访问，并且登陆的话会报错

```text
Error response from daemon: Get 192.168.199.151/v2: dial tcp 192.168.199.151:80: connect: connection refused
```

需要修改如下文件，并重启docker

```bash
#1.找到文件
find / -name docker.service -type f

##2.修改配置文件， 增加  --insecure-registry=192.168.199.151 选项
[root@master01 docker]# cat /etc/systemd/system/docker.service

[Service]
Environment="PATH=/opt/kube/bin:/bin:/sbin:/usr/bin:/usr/sbin"
#这里后面添加
ExecStart=/opt/kube/bin/dockerd --insecure-registry=192.168.199.151

##3.重新启动服务
systemctl daemon-reload
systemctl restart docker

## 4.然后在重新启动harbor
docker-compose down
./prepare
docker-compose up –d
```



