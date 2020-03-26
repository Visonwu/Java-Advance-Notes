1. 参考：<https://nacos.io/zh-cn/docs/sdk.html>

github下载源码编译安装启动：



启动后访问这个页面可以登陆控制界面，账号密码：nacos

<http://127.0.0.1:8848/nacos/index.html>

# 1.简单使用

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


