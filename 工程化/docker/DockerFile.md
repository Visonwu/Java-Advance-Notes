**Dockerfile**

​	

​	除了通过在原有的镜像加以定制化，然后通过commit,push到镜像仓库，生成自定义的镜像，还可以通过Dockerfile快速构建自定义的镜像。



# 1.学习方式：

- 1.进入镜像仓库[https://hub.docker.com](https://hub.docker.com/)

- 2.搜索一个镜像(例如tomcat)
- 3.然后通过选择不同的版本都dockerfile学习研究



# 2. Dockerfile相关命令

​	**注意点：**

- **关键字都是大写**
- Dockerfile单独放在一个文件夹中

构建好了通过 docker build <选项> dockerfile路径 

**-t <tag> :** 表示生成镜像的标签



| 命令                                          | 说明                                                         |
| --------------------------------------------- | ------------------------------------------------------------ |
| FROM <iamge> 或者<image>:<tag>                | 基础镜像，当前镜像的是基于哪一个镜像                         |
| MAINTAINER <name>                             | 镜像的维护者和邮箱地址                                       |
| RUN <command> 或者 [executable,param1,param2] | 镜像构建时需要运行的命令                                     |
| WORKDIR                                       | 容器创建后，默认在哪个目录；为后续的RUN,CMD,ENTRYPOINT指令配置工作目录；如果<br>WORKDIR /a  ; WORKDIR b ;WORKDIR c  ;RUN pwd 最终目录为/a/b/c |
| EXPOSE <port> [<port> ...]                    | 当前容器对外的端口                                           |
| ENV <key> <value>                             | 用来构建镜像是设置环境变量                                   |
| ADD <src> <dest>                              | 将宿主的目录的文件copy进镜像;并且ADD命令会自动解压压缩包     |
| COPY <src> <dest>                             | 将宿主的目录的文件copy进镜像                                 |
| VOLUME                                        | 创建一个从本地主机或者其他容器挂载的挂载点-容器数据卷，用来保存和持久化 |
| CMD  [executable,param1,param2]               | docker容器启动运行命令：多条cmd命令只有最后一条生效<br/>**CMD命令会被docker run 之后的参数替换** |
| ENTRYPOINT                                    | 指定容器启动过程中需要运行的命令<br/>多条cmd命令只有最后一条生效<br/>**ENTRYPOINT会把docker run 命令的参数追加到后面**: |
| ONBUILD  [INSTRUCTION]                        | 配置当所创建的镜像作为其他新创建镜像的基础镜像，所执行的操作指令<br/>       在一个镜像中用 ONBUILD修饰了相关命令，然后把这个dockerFile保存为一个镜像，当其他镜像通过FROM 把这个镜像作为基础镜像，会自动自行ONBUILD修饰的命令 |



# 3.使用Dockerfile部署项目

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
#启动程序
ENTRYPOINT java -jar wmled.jar
```

