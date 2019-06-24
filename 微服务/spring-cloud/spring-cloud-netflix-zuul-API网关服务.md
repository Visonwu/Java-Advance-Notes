**Zuul功能：**

- 动态路由
- 监控
- 安全过滤等

**Zuul介绍**：

​		Zuul实现了`HttpServet`，一般情况下，Zuul被内嵌到Spring的分发机制中，这样SpringMVC可以控制路由，这种情况情况下Spring对于请求有缓存处理，但是有时候我们不需要做缓存处理，那么就需要把使用`ZuulServlet`安装到Spring分发器之外了（不是使用`DispatcherServlet`）;这个后面过滤器会介绍`ServletDetectionFilter`。



​       Zuul为了在各个Filter中传递数据，把信息内容都存储在`RequestContext`中，这里数据存储在`ThreadLocal`中,并且`RequestContext`继承了`ConcurrentHashMap`.



Zuul是所有请求的入口，通过在Zuul过滤转发，路由等可以实现不同的请求到不同的服务器，类似F5,Nginx。

​      具体参考：<https://cloud.spring.io/spring-cloud-static/spring-cloud-netflix/2.1.2.RELEASE/single/spring-cloud-netflix.html#_router_and_filter_zuul>

# 1. Zuul的依赖

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-zuul</artifactId>
</dependency>


# 这个依赖出了默认的zuul-core之外，还包含了
spring-boot-start-actuator
spring-boot-start-web
spring-cloud-start-netflix-hystrix
spring-cloud-start-netflix-ribbon
```

# 2. 路由相关设置

​		如果使用Zuul，那么直接导入上面的依赖包，然后在启动主类中添加`@EnableZuulProxy`；当然这里还有一个注解`@EnableZuulServer`；这两者的区别就是过滤器不同，@EnableZuulProxy包含了所有`@EnableZuulServer`的过滤器，具体见官网介绍。

​	这里不包含服务的注册和发现，但是包含hystrix，ribbon，actuator等。

**路径匹配说明：**

| 通配符 | 说明                           |                                                     |
| ------ | ------------------------------ | --------------------------------------------------- |
| ？     | 匹配任意单个字符               | /user-serivice/?      ->     /user-service/a        |
| *      | 匹配任意多个字符               | /user-serivice/*      ->     /user-service/abc      |
| **     | 匹配任意多个字符，支持多个目录 | /user-serivice/**      ->     /user-service/abc/def |



## 2.1 没有服务发现（Eureka情况下）

​	所以如果没有包含加入eureka，那么添加路由规则，需要自定义url地址，当然也可以依赖ribbon做到负载均衡

```properties
spring.application.name=zuul-gateway
server.port=5555

#实例配置
zuul.routes.feign-consumer.path=/feign-consumer/**
## 这里需要自己定义url地址。
zuul.routes.feign-consumer.url=http://localhost:8091


#多实例配置
zuul.routes.feign-consumer.path=/feign-consumer/**
zuul.routes.feign-consumer.serviceId=feign-consumer
#这里关闭eureka的服务治理框架的协助
ribbon.eureka.enable=false 
feign-consumer.ribbon.listOfServers=http://localhost:8081,=http://localhost:8082
```



## 2.2 使用Eureka联合使用

​	默认情况下Zuul和Eureka联合使用的时候，可以直接通过当前服务名可以直接路由到服务对应的接口信息，所以即使下面的配置没有写，也可以通过这个方式做路由。

这里访问   http://localhost:5555//feign-consumer/hello  会被路由到http://feign-consumer/hello服务上

```properties
spring.application.name=zuul-gateway
server.port=5555
## Eureka 注册中心服务器端口
eureka.server.port = 9090
## Eureka Server 服务 URL,用于客户端注册
eureka.client.serviceUrl.defaultZone=http://localhost:${eureka.server.port}/eureka

# Zuul和Eureka默认使用下面的方式做路由。
zuul.routes.feign-consumer.path=/feign-consumer/**
zuul.routes.feign-consumer.service-id=feign-consumer
#zuul.routes.feign-consumer=/feign-consumer/**  这个和上面两句等效
```

**Zuul提供服务路由的默认规则，简化配置**

```properties
zuul.routes.<serverId>.path=<serverId>/**
zuul.routes.<serverId>.service-id=<serverId>
```

## 2.3 多版本下自定义路由规则

​	一般的情况下我们都是通过服务名做路由规则，但是如果一个服务有多个版本，那么每次修改就会很麻烦，比如`userservice-v1,userservice-v2,userservice-v3`等，那么默认生成的路径映射就是` /userservice-v1`这种形式，比较单一，所以为了优化提供了下面这种让这个“-”，做一定的拆分，可以变成这样`/v1/userservice`

这样通过带有版本号的表示就更好的管理服务了。

创建只需要通过如下方式做到即可,通过正则表达式做自定义路由，

```java

//这个自我测试，貌似没有生效
@Bean
public PatternServiceRouteMapper patternServiceRouteMapper(){
    return new PatternServiceRouteMapper(
        "(?<name>^.+)-(?<version>v.+$)",  //匹配服务名是否符合
        "${version}/${name}"			  //重新生成自定义的路由路径
    );
}
```

## 2.4 多个路由都匹配处理

​	  需要注意的是：如果存在多个路由规则，当前服务都能匹配，当服务匹配到一个服务就马上拿出来使用了。例如：

```properties
zuul.routes.feign-consumer.path=/feign-consumer/**
zuul.routes.feign-consumer.service-id=feign-consumer

zuul.routes.feign-consumere-ext.path=/feign-consumer/ext/**
zuul.routes.feign-consumer-ext.service-id=feign-consumer-ext
```

​	如上所示，当匹配`/feign-consumer-ext`服务时，有可能会被`/feign-consumer`处理掉，而且properties是 无序的，所以这里无法保证。

​	**处理方法：**

- 通过yaml文件处理，yaml文件可以按照顺序处理，把ext写在前面

  

## 2.5 忽略路由 ignored

​		有时候我们不希望某些接口被路由，可以通过zuul.ignored-patterns实现。例如：

```properties
# 这里就会对所有路由地址中包含hello做过滤，这些都不会被路由，注意这里是所有的路由情况
zuul.ignored-patterns=/**hello/**
# 当然可以对某些服务做忽略路由
zuul.ignored-services=hello-server
```



## 2.6 路由前缀prefix

为了便于对全局的路由添加前缀，我们提供了zuul.prefix

```properties
# 如下所示，但是这个目前看来还有冲突，当我们服务的名前缀也是api，也许就会出现冲突报错
zuul.prefix=api
```



## 2.7 本地跳转 forward

我们可以让zuul实现本地的跳转，不需要转到其他服务上，通过forward实现，如下

```properties
# 方式一：实现其他服务的跳转
zuul.routes.feign-consumer.path=/feign-consumer/**
zuul.routes.feign-consumer.service-id=feign-consumer
        
# 方式二：实现本地服务的跳转        
zuul.routes.feign-consumer1.path=/feign-consumer1/**
zuul.routes.feign-consumer1.url=forward:/local
```

​		如上，但我们访问`/ feign-consumer1/hello` 后就会转到本地的路径访问  即 `/local/hello`访问；当然前提是我们zuul本地也是有这个接口实现的。



## 2.8 Cookie和头信息

​		Zuul默认情况下会过滤掉Http请求头信息中的一些敏感信息，防止被路由的下游的服务器中。默认的敏感信息通过zuul.sensitiveHeaders参数定义。包括Cookie，Set-Cookie，Authorization三个属性。所以在web开发中，如果使用权限的Spring-Security,shiro安全框架，那么我们便不能使用登录和鉴权等。解决办法如下：

### 1） 全局设置

​	这里是全局设置所有敏感信息都不过滤，不建议这样做

```properties
#这样置空，不推荐，破坏了原有的默认方式
zuul.sensitive-headers=
```

### 2）局部设置

​	比较推荐这种方法，以免对敏感信息泄露

```properties
#方式一：将指定路由的敏感信息置为空
zuul.routes.feign-consumer.sensitive-headers=

#方式二：对指定路由开启自定义敏感头
zuul.routes.feign-consumer.custom-sensitive-headers=true
```

## 2.9 重定向问题

​		当我们能够获取到Cookie后，也能够实现web登录了，但是我们登录成功后需要跳转到某些URL缺失WEB应用的实例地址，而不是网关的路由地址。那么怎么做呢？

​	一般我们登陆成功后请求结果码是302，请求响应头的信息是Location指向了具体的服务实例的地址信息，而请求头信息中的Host也指向了具体服务实例的IP和Port。

通过如下方式可以再路由转发前为请求头设置Host头信息以标识服务端请求地址。

```properties
zuul.add-host-header=true
```



# 3.过滤器

​	  过滤可以做到权限控制和安全校验等功能。我们这里如果需要对客户端的请求做过滤，只需要实现`ZuulFilter`抽象类的四个抽象方法，然后将当前的过滤作为bean添加到spring容器中即可。

## 3.1 自定义过滤器案例

```java
public class AccessFilter extends ZuulFilter {

    private static final Logger logger = LoggerFactory.getLogger(AccessFilter.class);

    //过滤类型，它决定过滤在哪个阶段发生 ，这里为pre表示请求路由之前发生
    //pre 请求路有前被调用哦
    //routing 在路由请求时被调用
    //post 在routing和error过滤器之后被调用
    //error 在处理请求时发生错误时调用
    @Override
    public String filterType() {
        return "pre";
    }

    //过滤器的执行顺序 //越小越好
    @Override
    public int filterOrder() {
        return 0;
    }

    //判断当前是否需要过滤
    @Override
    public boolean shouldFilter() {
        
        return true;
    }

    //过滤器具体的执行逻辑，可以通过
    // currentContext.setSendZuulResponse(false);过滤该请求，不对其做路由请求,然后可以通过responseStatusCode,或者body对返回类容做编辑
    //
    @Override
    public Object run() throws ZuulException {
        RequestContext currentContext = RequestContext.getCurrentContext();
        HttpServletRequest request = currentContext.getRequest();

        String actoken = request.getParameter("actoken");
        if (StringUtils.isEmpty(actoken)){
            logger.warn("error ,no actoken");
            currentContext.setSendZuulResponse(false);
            currentContext.setResponseStatusCode(401);
            return null;
        }
        logger.info("请求成功");
        return null;
    }
}
```

## 3.2 请求生命周期

​	如下，这是从Zuul的官网wiki中关于生命周期的图解。这也是zuul1.*版本的内容， zuul2.*版本采用的是netty的客户端过滤处理的。

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4bdlzrakrj20qo0k0gm2.jpg)

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4bdp4urnkj217x0i1kax.jpg)



## 3.3  Spring-cloud-Zuul核心过滤器

​		所有的过滤逻辑可以参考`ZuulServletFilter`类。这个类实现了`Servlet`接口。源码处理：

```java
//如下所示：相关的pre、route、post、erro处理都有响应处理。

@Override
public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
    try {
        init((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
        try {
            preRouting();
        } catch (ZuulException e) {
            error(e);
            postRouting();
            return;
        }

        // Only forward onto to the chain if a zuul response is not being sent
        if (!RequestContext.getCurrentContext().sendZuulResponse()) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        try {
            routing();
        } catch (ZuulException e) {
            error(e);
            postRouting();
            return;
        }
        try {
            postRouting();
        } catch (ZuulException e) {
            error(e);
            return;
        }
    } catch (Throwable e) {
        error(new ZuulException(e, 500, "UNCAUGHT_EXCEPTION_FROM_FILTER_" + e.getClass().getName()));
    } finally {
        RequestContext.getCurrentContext().unset();
    }
}
```



在Spring-cloud-Zuul自带了许多过滤器。如下所示：

![](http://ww1.sinaimg.cn/mw690/b8a27c2fgy1g4bdy6v85yj20e00hhmxn.jpg)



### 1）pre

| 过滤器                 | order值(优先级) | 说明                                                         |
| ---------------------- | --------------- | ------------------------------------------------------------ |
| ServletDetectionFilter | -3              | 判断当前请求是否是`DispatcherServlet`请求。<br/><br/> - 通过/zuul/*路径访问的是通过`ZuulServlet`请求的，而不是`DispatcherServlet`请求。通过`ZuulServelt`请求一般是用来处理大文件上传等大数 据处理的情况。<br/><br/>- 当然`/zuul/` 路径也是可以修改的，通过`zuul.servletPath`参数来修改 |
| Servlet30WrapperFilter | -2              | 第二个过滤器，对所有请求都生效，主要是包装`HttpServletRequest`为`Servlet30RequestWrapper`这里包装的作用是处理Servlet不同版本的兼容性 |
| FormBodyWrapperFilter  | -1              | 第三个过滤器，仅仅对`application/x-www-form-urlencoded`和`multipart/form-data`以及当前是`DispatcherServlet`请求生效；主要是处理form表单数据以及编码封装请求 |
| DebugFilter            | 1               | 可以通过上下文是否携带`zuul.debug.request`和`zuul.debug.parameter`的值是true,那么过滤器可以通过这个激活debug功能便于定位问题 |
| PreDecorationFilter    | 5               | 判断当前的请求是不包含forward.to和serverId,(包含表示当前请求已经处理过了),才会用当前过滤器处理<br/>这个过滤器主要是对于下游服务设置不同的请求消息。当然可以通过zuul.addProxyHeaders关闭请求的添加信息 |

### 2）route

| 过滤器                  | order值（优先级） | 说明                                                         |
| ----------------------- | ----------------- | ------------------------------------------------------------ |
| RibbonRoutingFilter     | 10                | 只对上下文中含有`serviceId`做处理，这个是路由的核心，这里主要是使用**Ribbon，Hystrix**和可插拔的`Httpclient`发送请求<br/>默认使用`Apache的Httpclient`；如要是使用如：<br/>`OkHttpClient`。需要加入`com.squareup.okhttp3:okhttp`依赖包，然后添加ribbon.okhttp.enabled=true<br/>使用NetflixRibbon的httpclient：直接配置ribbon.restclient.enabled=true |
| SimpleHostRoutingFilter | 100               | 这个只对请求上下文中存在routeHost参数进行处理。意思就是我们的路由规则是通过url配置的(实际物理地址)，而不是通过serverId配置的。另外这个请求处理没有通过HystrixCommand保证，所以是没有线程隔离和断路器的保护的。 |
| SendForwardFilter       | 500               | 只对上下文中存在`forward.to`参数进行处理。即用来处理本地路由跳转配置 |

### 3）post

| 过滤器             | 优先级Order值 | 说明                       |
| ------------------ | ------------- | -------------------------- |
| SendResponseFilter | 1000          | 主要是讲代理返回的结果返回 |

### 4）error

| 过滤器          | 优先级Order值 | 说明                                                         |
| --------------- | ------------- | ------------------------------------------------------------ |
| SendErrorFilter | 0             | post的第一个过滤；这是对于之前的请求请求中设置是否有错误异常，`ctx.getThrowable()`这个可以获取异常不为null,做路由跳转。默认跳转到`/error`。当然可以通过server.error.path配置 |

## 3.4 禁用系统自带过滤器

```properties
#方式：zuul.<SimpleClassName>.<filterType>.disable=true ;例如：
zuul.SendResponseFilter.post.disable=true
```



# 4.自带Hystrix和Ribbon支持

​	zuul依赖默认包含Hystrix和Ribbon。所以Zuul天生拥有线程隔离和断路器的自我保护功能，以及服务调用的客户端负载功能。

不过通过path+url的关系映射时，路由转发不会采用HystrixCommand来包装。

所以一般都是通过Path+serverId来实现。当然下面这个不同版本也许不同

```properties
#路由转发HystrixCommand的超时时间
hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds=2000
#路由转发创建请求连接的超时时间，当这个时间小于 上者 时，会重试
ribbon.ConnectTimeout=2000
#路由转发创建请求 处理请求的时间
ribbon.ReadTimeout=2000

#关闭重试机制，这是全局设置
ribbon.retryable=false
#这是针对局部服务设置重试机制
zuul.routes.feign-consumer.retryable=false

```

# 5.动态路由

​	支持不同的配置文件即可。所以我们可以通过Spring-cloud-config来配置不同的zuul.properties文件。具体可以参考zuul的案例和config的使用说明。



# 6.动态加载过滤器

​	上面的配置可以通过配置文件动态获取，但是过滤器属于类文件却不能这样做，不过zuul提供Groovy过滤来实现。`FilterLoader`类。

**步骤一：配置自定义两个变量**

```properties
zuul.filter.root=filter
zuul.filter.interval=5
```

**步骤二：新增配置类**

```java
@ConfigurationProperties("zuul.filter")
public class FilterConfiguration {
    
    private String root;//根路径
    private Integer interval;//间隔时间
    ...setter,getter
}    
```

**步骤三：主类注入当前配置类，加载器注入bean**

​	这里配置的过滤器就是在当前主类的  `filter/pre`，`filter/post`目录中，这个加载器会间隔5秒加载一次这两个目录中的数据，而且一旦加载不会卸载。只有修改shouldfilter方法来关闭。这里也只是支持Groovy加载。

```java
@EnableConfigurationProperties(FilterConfiguration.class)
@EnableDiscoveryClient
@EnableZuulProxy
@SpringBootApplication
public class ZuulGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZuulGatewayApplication.class, args);
    }

    @Bean
    public FilterLoader filterLoader(FilterConfiguration filterConfiguration){
        FilterLoader instance = FilterLoader.getInstance();
        instance.setCompiler(new GroovyCompiler());
        try{
            FilterFileManager.setFilenameFilter(new GroovyFileFilter());
            FilterFileManager.init(filterConfiguration.getInterval(),
                    filterConfiguration.getRoot()+"/pre",
                    filterConfiguration.getRoot()+"/post");
        }catch (Exception e){
            throw  new RuntimeException(e);
        }
        return  instance;
    }
}  
```



