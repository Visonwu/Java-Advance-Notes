# 1. Tomcat 容器初识

> 官网：https://tomcat.apache.org

我们理解中：Tomcat可以称为Web容器或者Servlet容器

## 1.1 假想Tomcat构建

```java
class Tomcat{
    
    List list=new ArrayList();
    //使用tcp/ip表示web容器
    ServerSocket server=new ServerSocket(8080);
    Socket socket=server.accept();
    
    // 把请求和响应都封装在业务代码中的servlet
    list.add(servlets); // 只要把业务代码中一个个servlets添加到tomcat中即可
    
}
```

**作为Web容器；**即服务器使用HTTP来监听，用socket来监听链接请求

![lnww90.png](https://s2.ax1x.com/2019/12/29/lnww90.png)

**作为Servlet容器；**即能够装下一个个的Servlet

```java
public interface Servlet {
    void init(ServletConfig config) throws ServletException;
    ServletConfig getServletConfig();
    
    void service(ServletRequest req, ServletResponse res）throws ServletException, IOException;
    String getServletInfo();
                 
    void destroy();
}
```

**所以 获得的信息**

- （1）tomcat需要支持**servlet规范**
  			`tomcat/lib/servlet-api.jar`
- （2）**web容器**
  	        希望tomcat源码中也有`new ServerSocket(8080)`的代码，也可以是NIO
- （3）**servlet容器**
               希望tomcat源码中也有list.add(servlets)的代码



## 1.2 Tomcat产品

### 1）目录

（1）bin：主要用来存放命令，.bat是windows下，.sh是Linux下
（2）conf：主要用来存放tomcat的一些配置文件
（3）lib：存放tomcat依赖的一些jar包
（4）logs：存放tomcat在运行时产生的日志文件
（5）temp：存放运行时产生的临时文件
（6）webapps：存放应用程序
（7）work：存放tomcat运行时编译后的文件，比如JSP编译后的文件

### 2）源码下载调试

> Tomcat版本：Tomcat8.0.11
> 各个版本下载地址：https://archive.apache.org/dist/tomcat

(1)根据上面的链接下载对应的tomcat源码
(2)创建pom.xml文件

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
http://maven.apache.org/xsd/maven-4.0.0.xsd">
    
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.apache.tomcat</groupId>
    <artifactId>Tomcat8.0</artifactId>
    <name>Tomcat8.0</name>
    <version>8.0</version>
    
    <build>
    	<finalName>Tomcat8.0</finalName>
        <sourceDirectory>java</sourceDirectory>
		<testSourceDirectory>test</testSourceDirectory>
        <resources>
            <resource>
            <directory>java</directory>
            </resource>
        </resources>
        <testResources>
            <testResource>
            <directory>test</directory>
            </testResource>
        </testResources>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>2.3</version>
                <configuration>
                    <encoding>UTF-8</encoding>
                    <source>1.8</source>
                    <target>1.8</target>
                </configuration>
            </plugin>
        </plugins>
    </build>
    
    <dependencies>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.12</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.easymock</groupId>
            <artifactId>easymock</artifactId>
            <version>3.4</version>
        </dependency>
        <dependency>
            <groupId>ant</groupId>
            <artifactId>ant</artifactId>
            <version>1.7.0</version>
        </dependency>
        <dependency>
            <groupId>wsdl4j</groupId>
            <artifactId>wsdl4j</artifactId>
            <version>1.6.2</version>
        </dependency>
        <dependency>
            <groupId>javax.xml</groupId>
            <artifactId>jaxrpc</artifactId>
            <version>1.1</version>
        </dependency>

    	<dependency>
            <groupId>org.eclipse.jdt.core.compiler</groupId>
            <artifactId>ecj</artifactId>
            <version>4.5.1</version>
        </dependency>
	</dependencies>
</project>
```

(3)将源码导入到idea中；创建Application，名称为local-tomcat，并且填写相关信息

```properties
name:gp-tomcat
Main class:org.apache.catalina.startup.Bootstrap
VM options:-Dcatalina.home="apache-tomcat-8.0.11"
```

(4)在当前源码目录创建apache-tomcat-8.0.11文件夹，并且将一些文件拷贝到该目录下
		比如bin conf lib logs temp webapps work
(5)启动，发现报错，找不到CookieFilter，直接删除
(6)打开浏览器访问:localhost:8080



# 2.Tomcat架构

**架构图**<--->**conf/server.xml**<--->**源码** 三者有一一对应的关系

![lnwh36.png](https://s2.ax1x.com/2019/12/29/lnwh36.png)

 ## 2.1 **各个组件含义** 

​	官网 ：https://tomcat.apache.org/tomcat-8.0-doc/architecture/overview.html

### 1） **Server**

````
In the Tomcat world, a Server represents the whole container. Tomcat provides a default implementation of the Server interface which is rarely customized by users.
````

### 2）**Service**

```java
A Service is an intermediate component which lives inside a Server and ties one or more Connectors to exactly one Engine. The Service element is rarely customized by users, as the default implementation is simple and sufficient: Service interface.Engine
```

### 3）**Connector**

```java
A Connector handles communications with the client. There are multiple connectors available with Tomcat. These include the HTTP connector which is used for most HTTP traffic, especially when running Tomcat as a standalone server, and the AJP connector which implements the AJP protocol used when connecting Tomcat to a web server such as Apache HTTPD server. Creating a customized connector is a significant effort.
```

### 4）**Contaniner**

- **Engine**

  ```java
  An Engine represents request processing pipeline for a specific Service. As a Service may have multiple Connectors, the Engine receives and processes all requests from these connectors, handing the response back to the appropriate connector for transmission to the client. The Engine interface may be implemented to supply custom Engines, though this is uncommon. Note that the Engine may be used for Tomcat server clustering via the jvmRoute parameter. Read the Clustering documentation for more information.
  ```

- **Host**

  ```java
  A Host is an association of a network name, e.g. www.yourcompany.com, to the Tomcat server. An Engine may contain multiple hosts, and the Host element also supports network aliases such as yourcompany.com and abc.yourcompany.com. Users rarely create custom Hosts because the StandardHost implementation provides significant additional functionality.
  ```

- **Context**

  ```java
  A Context represents a web application. A Host may contain multiple contexts, each with a unique path. The Context interface may be implemented to create custom Contexts, but this is rarely the case because the StandardContext provides significant additional functionality.
  ```

- **Wrapper**

  ```java
  这个就是Servlet的包装器类
  ```



## 2.2 核心组件

> Connector：主要负责处理Socket连接，以及Request与Response的转化
>
> Container：包括Engine、Host、Context和Wrapper，主要负责内部的处理以及Servlet的管理

### 1) Connector

> 设计思想 ：**高内聚、低耦合** 
>
> EndPoint：提供字节流给Processor 
>
> Processor：提供Tomcat Request对象给Adapter 
>
> Adapter：适配request转换并提供ServletRequest给容器

![lnBqpt.png](https://s2.ax1x.com/2019/12/29/lnBqpt.png)

**2.2.1 EndPoint** 

​		监听通信端口，是对传输层的抽象，用来实现 TCP/IP 协议的。 

> 对应的抽象类为AbstractEndPoint，有很多实现类，比如NioEndPoint，JIoEndPoint等。在其中有两个组件，一个 是Acceptor，另外一个是SocketProcessor。 
>
> Acceptor用于监听Socket连接请求，SocketProcessor用于处理接收到的Socket请求



**2.2.2 Processor** 

​	Processor是用于实现HTTP协议的，也就是说Processor是针对应用层协议的抽象。 

>  Processor接受来自EndPoint的Socket，然后解析成Tomcat Request和Tomcat Response对象，最后通过Adapter 提交给容器。 
>
> 对应的抽象类为AbstractProcessor，有很多实现类，比如AjpProcessor、Http11Processor等。 

**2.2.3 Adpater** 

​	ProtocolHandler接口负责解析请求并生成 Tomcat Request 类；需要把这个 Request 对象转换成ServletRequest。 

>  Tomcat 引入CoyoteAdapter，这是**适配器模式**的经典运用，连接器调用 CoyoteAdapter 的 sevice 方法，传入的是 Tomcat Request 对象，CoyoteAdapter 负责将 Tomcat Request 转成 ServletRequest，再调用容器的 service 方法。



### 2) Container

> 通过Connector之后，我们已经能够获得对应的Servlet



## 2.3 服务器启动流程图

> 官网：<https://tomcat.apache.org/tomcat-8.0-doc/architecture/startup/serverStartup.pdf>



## 2.4 请求处理流程图

> 官网：<https://tomcat.apache.org/tomcat-8.0-doc/architecture/requestProcess/request-process.png>



# 3 .Tomcat源码

## 3.1 自定义类加载器

> WebAppClassLoader，打破了双亲委派模型：先自己尝试去加载这个类，找不到再委托给父类加载器。 
>
> 通过复写findClass和loadClass实现。
>
> 参考：<https://www.jianshu.com/p/269f60fa481e>

![l8LsFP.png](https://s2.ax1x.com/2020/01/01/l8LsFP.png)

```text
 1. BootstrapClassLoader  : 系统类加载器
 2. ExtClassLoader        : 扩展类加载器
 3. AppClassLoader        : 普通类加载器
 
 #下面是 这几个 Classloader 是 Tomcat 对老版本的兼容
 4. commonLoader      : Tomcat 通用类加载器, 加载的资源可被 Tomcat 和 所有的 Web 应用程序共同获取
 5. catalinaLoader    : Tomcat 类加载器, 加载的资源只能被 Tomcat 获取(但 所有 WebappClassLoader 不能获取到 catalinaLoader 加载的类)
 6. sharedLoader      : Tomcat 各个Context的父加载器, 这个类是所有 WebappClassLoader 的父类, sharedLoader 所加载的类将被所有的 WebappClassLoader 共享获取

 这个版本 (Tomcat 8.x.x) 中, 默认情况下 commonLoader = catalinaLoader = sharedLoader
 (PS: 为什么这样设计, 主要这样这样设计 ClassLoader 的层级后, WebAppClassLoader 就能直接访问 tomcat 的公共资源, 若需要tomcat 有些资源不让 WebappClassLoader 加载, 则直接在 ${catalina.base}/conf/catalina.properties 中的 server.loader 配置一下 加载路径就可以了)
 

参考：https://www.jianshu.com/p/269f60fa481e
```



## **3.2 Session管理** 

> Context中的Manager对象，查看Manager中的方法，可以发现有跟Session相关的。 
>
> Session 的核心原理是通过 Filter 拦截 Servlet 请求，将标准的 ServletRequest 包装一下，换成 Spring 的 Request 对象，这样当我们调用 Request 对象的 getSession 方法时，Spring 在背后为我们创建和管理 Session。



## 3.3 服务器启动源码

- **BootStrap**

  ​	BootStrap是Tomcat的入口类

  ​	bootstrap.init() 和 创建Catalina

- **Catalina**

  ​	解析server.xml文件 

  ​	创建Server组件，并且调用其init和start方法 

- **Lifecycle**

  ​	用于管理各个组件的生命周期 

  ​	init、start、stop、destroy 

  ​	LifecycleBase实现了Lifecycle，利用的是模板设计模式 

![lGPE2n.png](https://s2.ax1x.com/2020/01/01/lGPE2n.png)

- **Server**

  ​	管理Service组件，并且调用其init和start方法 

- **Service**

    管理连接器和Engine

### 3.3.1 初始化流程

![lGFFcn.png](https://s2.ax1x.com/2020/01/01/lGFFcn.png)



### 3.3.2 组件启动调用流程

![lGFtAO.png](https://s2.ax1x.com/2020/01/01/lGFtAO.png)



**关注到上述图解中的**ContainerBase.startInternal()**方法**

```java
// Start our child containers, if any 
Container children[] = findChildren(); 
List<Future<Void>> results = new ArrayList<>(); 
for (int i = 0; i < children.length; i++) { 
    // 这句代码就是会调用ContainerBase下的一个个子容器的call方法  	
    results.add(startStopExecutor.submit(new StartChild(children[i]))); 
}
```

**查看**new StartChild**要执行的**call方法

```java
private static class StartChild implements Callable<Void> {

    private Container child;

    public StartChild(Container child) {
        this.child = child;
    }

    @Override
    public Void call() throws LifecycleException {
        child.start();
        return null;
    }
}
```

![lGFOC4.png](https://s2.ax1x.com/2020/01/01/lGFOC4.png)

**StandardHost将一个个web项目部署起来**

![lGnJtP.png](https://s2.ax1x.com/2020/01/01/lGnJtP.png)

```java
// Deploy XML descriptors from configBase
deployDescriptors(configBase, configBase.list()); 
// Deploy WARs 
deployWARs(appBase, filteredAppPaths); 
// Deploy expanded folders
deployDirectories(appBase, filteredAppPaths);
```

**StandardContext.startInternal()解析web.xml和添加wrapper**

![lGudC6.png](https://s2.ax1x.com/2020/01/01/lGudC6.png)

```java
ContextConfig.webConfig()的step9解析到servlets包装成wrapper对象 

StandardContext.startInternal()->最终会调用 if (!loadOnStartup(findChildren())) 
```



