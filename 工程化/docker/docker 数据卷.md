# 1.数据卷和数据卷容器

​		通过  **-v** 可以是实现把宿主机目录挂载到容器目录中；	这个是针对数据做持久化处理的，当容器内的数据发生变化，相对应的主机内容也会发生变化，和DockerFile中VOLUME类似，主机会自己生成对应的目录地址

## 1.1 Volume

​	通过自定义容器卷名称来对容器内的数据进行持久化。

```bash
#1.查看volume
	docker volume ls

#2.具体查看某个volume的详细情况，路径等
	docker volume inspect [volume-name]

#3.使用自定义的名称的数据卷容器名
	docker volume create my-volume
	# 使用自己创建的my-volume
	docker run -it -v my-volume:/var/lib/mysql --name my-mysql mysql

```

## 1.2 Bind Mouting

​	通过限制绝对路径来绑定容器内的数据，当容器或者主机随便一方修改数据，那么数据内容两边都会同步，特别是在开发中尤为有效

```cmd
命令：`docker run -it -v 宿主机绝对路径(或者数据卷的名称)：容器内路径 镜像名` 

例子：docker run -it -v /tmp/webapps:/usr/local/webapps -p 8090:8080 tomcat

//目录不存在会自动创建目录，默认docker挂载的数据卷默认权限是读写的(rw);
如果要改为只读，通过添加(ro)实现
即：docker run -it -v /src/webapp:/opt/webapp:ro centos
```

​		我们的宿主机中的`/tmp/webapps`就可以实时修改数据，那么webapps就可以呈现不同的数据，那么我们通过vagrant在绑定当前数据机的目录就可以实时的修改数据，让容器不用重新开启就可以实现实时的数据更新。



## 1.3 数据卷容器

​	即运行一个docker容器作为其他容器的数据卷；通过 **--volumes-from**命令实现。挂载多个数据卷，通过多个**--volumes-from**实现。另外数据卷自身并不一定需要保持在运行状态。挂载的容器如果被删除了，数据卷并不会被删除，只有删除最后一个挂载它的容器显示使用**docker rm -v** 删除指定关联的容器。

```cmd
例子：
步骤一：新建一个数据卷容器n0,供其他容器挂载使用
	docker run -it -v /dbdata --name n0 centos

步骤二：新建另外两个容器来挂载该容器
	docker run -it --volumes-from n0 --name n1 centos
	docker run -it --volumes-from n0 --name n2 centos

上面中n1,n2 挂载了n0 /dbdata目录，所以任意一个容器修改dbdata中的文件，其他容器都能看到。

```

