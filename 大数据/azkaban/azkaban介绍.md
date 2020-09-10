## 1.Azkaban的是什么

Azkaban是由Linkedin公司推出的一个批量工作流任务调度器，用于在一个工作流内以一个特定的顺序运行一组工作和流程。Azkaban使用job配置文件建立任务之间的依赖关系，并提供一个易于使用的web用户界面维护和跟踪你的工作流。

## 2. Azkaban 的优点

- 提供功能清晰，简单易用的Web UI界面
- 提供job配置文件快速建立任务和任务之间的依赖关系
- 提供模块化和可插拔的插件机制，原生支持command、Java、Hive、Pig、Hadoop
- 基于Java开发，代码结构清晰，易于二次开发



## 3. 安装部署

### 1）准备

1) 将Azkaban Web服务器、Azkaban执行服务器、Azkaban的sql执行脚本及MySQL安装包拷贝到hadoop102虚拟机/opt/software目录下

```
a) azkaban-web-server-2.5.0.tar.gz
b) azkaban-executor-server-2.5.0.tar.gz
c) azkaban-sql-script-2.5.0.tar.gz
d) mysql-libs.zip   （包含）
```

​	选择**Mysql**作为Azkaban数据库，因为Azkaban建立了一些Mysql连接增强功能，以方便Azkaban设置，并增强服务可靠性。



### 2）安装Azkaban

1) 在/opt/module/目录下创建azkaban目录

​		[ module]$ mkdir azkaban

2) 解压azkaban-web-server-2.5.0.tar.gz、azkaban-executor-server-2.5.0.tar.gz、azkaban-sql-script-2.5.0.tar.gz到/opt/module/azkaban目录下

```shell
[ software]$ tar -zxvf azkaban-web-server-2.5.0.tar.gz -C /opt/module/azkaban/
[ software]$ tar -zxvf azkaban-executor-server-2.5.0.tar.gz -C /opt/module/azkaban/
[ software]$ tar -zxvf azkaban-sql-script-2.5.0.tar.gz -C /opt/module/azkaban/
```

3) 对解压后的文件重新命名

```shell
[ azkaban]$ mv azkaban-web-2.5.0/ server
[ azkaban]$ mv azkaban-executor-2.5.0/ executor
```

4) azkaban脚本导入

​	进入mysql，创建azkaban数据库，并将解压的脚本导入到azkaban数据库。

```shell
[ azkaban]$ mysql -uroot -proot

mysql> create database azkaban;
mysql> use azkaban;
mysql> source /opt/module/azkaban/azkaban-2.5.0/create-all-sql-2.5.0.sql
```



### 3)生成密钥库

Keytool：是java数据证书的管理工具，使用户能够管理自己的公/私钥对及相关证书。

- -keystore：指定密钥库的名称及位置（产生的各类信息将不在.keystore文件中）

- -genkey：在用户主目录中创建一个默认文件".keystore" 

- -alias：对我们生成的.keystore进行指认别名；如果没有默认是mykey

- -keyalg：指定密钥的算法 RSA/DSA 默认是DSA

1）生成 keystore的密码及相应信息的密钥库

```shell
[ azkaban]$ keytool -keystore keystore -alias jetty -genkey -keyalg RSA
输入密钥库口令:  
再次输入新口令: 
您的名字与姓氏是什么?
  [Unknown]:  
您的组织单位名称是什么?
  [Unknown]:  
您的组织名称是什么?
  [Unknown]:  
您所在的城市或区域名称是什么?
  [Unknown]:  
您所在的省/市/自治区名称是什么?
  [Unknown]:  
该单位的双字母国家/地区代码是什么?
  [Unknown]:  
CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown是否正确?
  [否]:  y

输入 <jetty> 的密钥口令
        (如果和密钥库口令相同, 按回车):  
再次输入新口令:
```

注意：

密钥库的密码至少必须6个字符，可以是纯数字或者字母或者数字和字母的组合等等

密钥库的密码最好和<jetty> 的密钥相同，方便记忆

2）将keystore 拷贝到 azkaban web服务器根目录中

```
[ azkaban]$ mv keystore /opt/module/azkaban/server/
```

### 4) **时间同步配置**

先配置好服务器节点上的时区;

​	如果在/usr/share/zoneinfo/这个目录下不存在时区配置文件Asia/Shanghai，就要用 tzselect 生成。

### 5) 配置文件

#### 5.1) Web服务器配置

1) 进入azkaban web服务器安装目录 conf目录，打开azkaban.properties文件

```shell
[ conf]$ pwd
/opt/module/azkaban/server/conf
[ conf]$ vim azkaban.properties
```

2) 按照如下配置修改azkaban.properties文件

```properties
#Azkaban Personalization Settings
#服务器UI名称,用于服务器上方显示的名字
azkaban.name=Test
#描述
azkaban.label=My Local Azkaban
#UI颜色
azkaban.color=#FF3601
azkaban.default.servlet.path=/index
#默认web server存放web文件的目录
web.resource.dir=/opt/module/azkaban/server/web/
#默认时区,已改为亚洲/上海 默认为美国
default.timezone.id=Asia/Shanghai

#Azkaban UserManager class
user.manager.class=azkaban.user.XmlUserManager
#用户权限管理默认类（绝对路径）
user.manager.xml.file=/opt/module/azkaban/server/conf/azkaban-users.xml

#Loader for projects
#global配置文件所在位置（绝对路径）
executor.global.properties=/opt/module/azkaban/executor/conf/global.properties
azkaban.project.dir=projects

#数据库类型
database.type=mysql
#端口号
mysql.port=3306
#数据库连接IP
mysql.host=hadoop102
#数据库实例名
mysql.database=azkaban
#数据库用户名
mysql.user=root
#数据库密码
mysql.password=000000
#最大连接数
mysql.numconnections=100

# Velocity dev mode
velocity.dev.mode=false

# Azkaban Jetty server properties.
# Jetty服务器属性.
#最大线程数
jetty.maxThreads=25
#Jetty SSL端口
jetty.ssl.port=8443
#Jetty端口
jetty.port=8081
#SSL文件名（绝对路径）
jetty.keystore=/opt/module/azkaban/server/keystore
#SSL文件密码
jetty.password=000000
#Jetty主密码与keystore文件相同
jetty.keypassword=000000
#SSL文件名（绝对路径）
jetty.truststore=/opt/module/azkaban/server/keystore
#SSL文件密码
jetty.trustpassword=000000

# Azkaban Executor settings
executor.port=12321

# mail settings
mail.sender=
mail.host=
job.failure.email=
job.success.email=

lockdown.create.projects=false

cache.directory=cache
```

 3）web服务器用户配置

在azkaban web服务器安装目录 conf目录，按照如下配置修改azkaban-users.xml 文件，增加管理员用户。

```xml
[ conf]$ vim azkaban-users.xml
<azkaban-users>
	<user username="azkaban" password="azkaban" roles="admin" groups="azkaban" />
	<user username="metrics" password="metrics" roles="metrics"/>
    <!--增加admin用户-->
	<user username="admin" password="admin" roles="admin" />
	<role name="admin" permissions="ADMIN" />
	<role name="metrics" permissions="METRICS"/>
</azkaban-users>
```

#### 5.2）执行服务器配置

1）进入执行服务器安装目录conf，打开azkaban.properties

```shell
[ conf]$ pwd
/opt/module/azkaban/executor/conf
[ conf]$ vim azkaban.properties
```



2）进入执行服务器安装目录conf，打开azkaban.properties

```properties
#Azkaban
#时区
default.timezone.id=Asia/Shanghai

# Azkaban JobTypes Plugins
#jobtype 插件所在位置
azkaban.jobtype.plugin.dir=plugins/jobtypes

#Loader for projects
executor.global.properties=/opt/module/azkaban/executor/conf/global.properties
azkaban.project.dir=projects

database.type=mysql
mysql.port=3306
mysql.host=hadoop102
mysql.database=azkaban
mysql.user=root
mysql.password=000000
mysql.numconnections=100

# Azkaban Executor settings
#最大线程数
executor.maxThreads=50
#端口号(如修改,请与web服务中一致)
executor.port=12321
#线程数
executor.flow.threads=30
```

### 6) **启动**executor服务器

在executor服务器目录下执行启动命令

```shell
[ executor]$ pwd
/opt/module/azkaban/executor
[ executor]$ bin/azkaban-executor-start.sh
```

### 7) **启动web服务器**

在azkaban web服务器目录下执行启动命令

```shell
[ server]$ pwd
/opt/module/azkaban/server
[ server]$ bin/azkaban-web-start.sh
```



**注意：**

先执行executor，再执行web，避免Web Server会因为找不到执行器启动失败。

jps查看进程

```shell
[ server]$ jps
3601 AzkabanExecutorServer
5880 Jps
3661 AzkabanWebServer
```

启动完成后，在浏览器(建议使用谷歌浏览器)中输入**https://服务器IP地址:8443**，即可访问azkaban服务了。

在登录中输入刚才在azkaban-users.xml文件中新添加的户用名及密码，点击 login。





## 4.Azkaban实战

### 4.1 单一job案例

1）创建job描述文件

```shell
[ jobs]$ vim first.job
#first.job
type=command
command=echo 'this is my first job'
```

2) 将job资源文件打包成zip文件

```shell
[ jobs]$ zip first.zip first.job
  adding: first.job (deflated 15%)
[ jobs]$ ll
总用量 8
-rw-rw-r--. 1 atguigu atguigu  60 10月 18 17:42 first.job
-rw-rw-r--. 1 atguigu atguigu 219 10月 18 17:43 first.zip
```



**注意：**

目前，Azkaban上传的工作流文件只支持xxx.zip文件。zip应包含xxx.job运行作业所需的文件和任何文件（文件名后缀必须以.job结尾，否则无法识别）。作业名称在项目中必须是唯一的。



3）执行文件

```
a.通过网页进入azkaban的web管理平台
b.首先创建project
c.上传job的zip包
d.启动执行该job
e.启动执行该job
```



### 4.2 多job工作流依赖案例

创建有依赖关系的多个job描述; first->second,third->last

第一个job：first.job

```properties
type=command
command=mkdir -p /opt/module/azkaban/test
```

第二个job：second.job

```properties
type=command
dependencies=first
command=touch /opt/module/azkaban/test/1.txt
```

第三个job：third.job

```properties
type=command
dependencies=first
command=touch /opt/module/azkaban/test/2.txt
```

最后一个job：last.job

```properties
type=command
dependencies=second,third
command=bash -c "echo 111 >> /opt/module/azkaban/test/1.txt && echo 222 >> /opt/module/azkaban/test/2.txt "
```



### 4.3 java 任务

#### 4.3.1）将java程序打成jar包



#### 4.3.2 ）编写job文件 testJava.job

```properties
type=javaprocess
java.class=com.vison.AppMain ${gapTime} ${nums}
classpath=/opt/soft/logger-collector-0.0.1.jar
```

- javaprocess表示java任务
- java.class 表示main方法入口类 ${gapTime}和${nums} 表示要传入的参数
- classpath表示当前jar在azkaban服务器的地址，如果有相关依赖，需要都包含进去比如用路径 /opt/soft/*

#### 4.3.3 ）将job文件打成zip包



#### 4.3.4） web上传执行

通过azkaban的web管理平台创建project并上传job压缩包，启动执行该job

可以通过flow parameter添加参数