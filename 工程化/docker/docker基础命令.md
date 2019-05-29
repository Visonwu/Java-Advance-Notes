



# 常用命令图

![Docker基础命令](//ws4.sinaimg.cn/large/b8a27c2fgy1g3eu3br3k4j22032f1x0v.jpg)

# 1.帮助命令

| docker命令     | 说明       | 备注 |
| -------------- | ---------- | ---- |
| docker version | docker版本 |      |
| docker info    | docker信息 |      |
| docker --help  | docker帮助 |      |



# 2. 镜像命令

​	类似母体，一个镜像可以用来生成不同的实例

| docker命令                                              | 说明                                                         | 例子                                                         |
| ------------------------------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| docker images                                           | -a 列出所有的镜像；<br/>-q 只显示镜像的ID;<br/>--digest 显示摘要信息；<br/>--no-trunc 显示没有去掉的信息 |                                                              |
| docker search 镜像名                                    | 搜索仓库中的镜像;<br/>-s 数字 ： 大于stars 的镜像;<br/>      | docker search -s 30 nginx  <br>: 表示获取start 大于30的nginx |
| docker pull  镜像名                                     | 获取仓库的镜像，本地没有就从仓库中下载                       | docker pull tomcat                                           |
| docker rmi 镜像id                                       | docker rmi -f 镜像名 : 删除单个镜像；<br/>docker rmi 镜像名2：tag1 镜像名2：tag2  : 删除多个镜像;<br/>docker rmi -f $(docker images -q)  : 删除所有的镜像 |                                                              |
| docker commit -m="" -a="作者" 容器ID 目标镜像名：标签名 | 用已存在的容器副本生成新的镜像                               |                                                              |



# 3.容器命令

​	表示每一个承载体，可以用来运行每一个镜像实体

| 介绍                                 | 命令                                                         | 例子                                                         |
| ------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 新建并启动                           | docker run [optioins] IMAGE [command] [ARG...]<br/>options <br/>      --name 为容器指定一个新的名称<br/>     -d 后台运行容器<br/>     -i 交互式运行<br/>     -t启动一个伪终端<br/>    -p 指定端口映射<br/>    -P 随机端口映射 | docker run -i -t --name mytomcat tomcat   ：运行tomcat ,没有指定端口外网无法访问<br/><br/>docker run -i -t  -p:6787:8080  tomcat   ：外网可以通过6787访问tomcat,8080是容器的端口 |
| 列出所有的运行容器                   | docke ps [options]<br/><br/>options  <br/>   --a 列出所有正在运行的和历史运行过的<br/>  -l 显示最近创建的容器<br/>  -n 最近n个创建的容器<br/>   -q 只显示容器id |                                                              |
| 退出容器                             | exit :退出并停止容器<br/>ctrl+p+q :退出不停止容器            |                                                              |
| 启动容器                             | docker start 容器id /容器名称                                |                                                              |
| 重启容器                             | docker restart 容器id /容器名称                              |                                                              |
| 后台运行容器                         | docker run -d 容器id                                         |                                                              |
| 删除容器                             | 删除多个容器:<br/>     docker rm -f $(docker ps -a -q)<br/><br/>    docker ps -a -q \| xargs docker rm <br/><br/>删除单个容器：<br/>docker rm 容器id |                                                              |
| 强制停止容器                         | docker kill 容器id /容器名称                                 |                                                              |
| 查看日志                             | docker logs -f -t --tail 容器id                              |                                                              |
| 查看容器内的进程                     | docker top 容器id                                            |                                                              |
| 查看容器详细信息                     | docker inspect 容器id                                        |                                                              |
| 进入正在运行的容器，并以前台方式运行 | docker exec -it 容器ID bashshell   : <br/>          产生新的进程，一般生产环境都用这个,这里记得使用-i 交互模式，不然进入模板半天都加载不出目录来  bashshell 通常是/bin/bash<br/><br/>docker attach 容器id  ：进入容器，不产生新的进程 | docker  exect  -it  容器ID /bin/bash                         |
| 从容器内拷贝文件到主机中             | docker cp 容器id：容器内路径  主机路径                       |                                                              |



# 4.数据卷和数据卷容器

## 4.1 数据卷挂载

​	通过  **-v** 可以是实现把宿主机目录挂载到容器目录中

```cmd
命令：`docker run -it -v 宿主机绝对路径：容器内路径 镜像名` 

例子：docker run -it -v /src/webapp:/opt/webapp centos

//目录不存在会自动创建目录，默认docker挂载的数据卷默认权限是读写的(rw);
如果要改为只读，通过添加(ro)实现
即：docker run -it -v /src/webapp:/opt/webapp:ro centos

```



## 4.2 数据卷容器

​	即运行一个docker容器作为其他容器的数据卷；通过 **--volumes-from**命令实现。挂载多个数据卷，通过多个**--volumes-from**实现。另外数据卷自身并不一定需要保持在运行状态。挂载的容器如果被删除了，数据卷并不会被删除，只有删除最后一个挂载它的容器显示使用**docker rm -v** 删除指定关联的容器。

```cmd
例子：
步骤一：新建一个数据卷容器no,供其他容器挂载使用
	docker run -it -v /dbdata --name n0 centos

步骤二：新建另外两个容器来挂载该容器
	docker run -it --volumes-from n0 --name n1 centos
	docker run -it --volumes-from n0 --name n2 centos

上面中n1,n2 挂载了n0 /dbdata目录，所以任意一个容器修改dbdata中的文件，其他容器都能看到。当然

```







