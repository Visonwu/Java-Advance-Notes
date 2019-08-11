1. 参考：<https://nacos.io/zh-cn/docs/sdk.html>

github下载源码编译安装启动：



启动后访问这个页面可以登陆控制界面，账号密码：nacos

<http://127.0.0.1:8848/nacos/index.html>





# 1.简单使用：

依赖

```xml
<dependency>
    <groupId>com.alibaba.boot</groupId>
    <artifactId>nacos-config-spring-boot-starter</artifactId>
    <version>0.2.1</version>
</dependency>
```

application.properties

```properties
# 本地nacos服务启动
nacos.config.server-addr=127.0.0.1:8848
```

使用

```java
@NacosPropertySource(dataId = "example", groupId = "DEFAULT_GROUP",autoRefreshed = true)
@RestController
public class DemoController {
	
    //可以动态获取nacos上设置的info的信息，这里如果info没有获取到返回的默认'hello vison'值
    @NacosValue(value = "${info:hello vison}", autoRefreshed = true)
    private String info;

    @RequestMapping(value = "/get", method = GET)
    @ResponseBody
    public String get() {
        return info;
    }
}
```



# 2. 源码简单分析

如果我们要实现一个配置中心需要考虑的

````
1.服务器端的配置保存（持久化）
	数据库
2.服务器端提供访问api
	rpc、http（openapi）
3.数据变化之后如何通知到客户端
	zookeeper（session manager）
	push（服务端主动推送到客户端）、pull(客户端主动拉去数据）? -> 长轮训( pull数据量很大会怎么办)
4.客户端如何去获得远程服务的数据（）
5.安全性（）
6.刷盘（本地缓存）->
````

nacos采用pull和'push'结合方式获取配置信息，采用长连接方式（超时）获取数据

通过sdk代码来分析实现

```java
public static void main(String[] args) {
    try {
        String serverAddr = "localhost:8848";
        String dataId = "example";
        String group = "DEFAULT_GROUP";
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        //这里入口查看启动哪些线程 更新数据
        ConfigService configService = NacosFactory.createConfigService(properties);
        //怎么样去获取数据（本地，远程，缓存等）
        String content = configService.getConfig(dataId, group, 5000);
        System.out.println(content);
        configService.addListener("example", "DEFAULT_GROUP", new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }
            @Override
            public void receiveConfigInfo(String s) {
                System.out.println("ssss"+s);
            }
        });
        System.in.read();
    } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    }
}
```



# 3.nacos集群

​	使用**raft算法**协议保证数据一致性（和zookeeper一样弱一致性），以及相关的leader选举

raft参考：<http://thesecretlivesofdata.com/raft/>

- leader
- follower
- candidate选举