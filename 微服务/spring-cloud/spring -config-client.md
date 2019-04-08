[TOC]

# 1.Java 监听模式

## 1.1 发布 订阅模式 -推模式

- java.util.Observable 发布者
- java.util.Observer 监听者

发布者：订阅者 = 1：N 或者N:M

> 拉模式：比如迭代器iterator,通过循环，next方法主动去获取

```java
public class TestObservable {

    public static void main(String[] args) {
        testPublishSubScribe();

    }

    /**
     * 发布/订阅模式
     */
    private static void testPublishSubScribe() {
        //Observable 发布者
        //Observer 监听者
        MyObservable observable = new MyObservable();//发布者

        observable.addObserver(new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                System.out.println(arg);
            }
        });

        //事件变化
        observable.setChanged();
        // 发布者通知，订阅者是被动感知（属于推的模式）
        observable.notifyObservers("hello world");
        
    }

    static class MyObservable extends Observable {
        @Override
        public synchronized void setChanged() {
            super.setChanged();
        }
    }
}
```



## 1.2 事件 监听模式

- java.util.EventObject 事件对象
- java.util.EventListener 事件监听器 （标记性接口）



# 2. Spring中的事件 监听 

- ApplicationEvent  事件
- ApplicationListener 监听



API使用：

```java
package com.vison.ws.obersable;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class TestSpringEvent {

    public static void main(String[] args) {

        AnnotationConfigApplicationContext applicationContext = new 	AnnotationConfigApplicationContext();

        //添加事件监听器
        applicationContext.addApplicationListener(
            new ApplicationListener<MyApplicationEvent>() { 
                //这里使用了MyApplicationEvent，表示只监听这个事件
                //获取监听事件
                @Override
                public void onApplicationEvent(MyApplicationEvent applicationEvent) {
                    System.out.println("获取到事件源"+applicationEvent.getSource());
                }
            });
        applicationContext.refresh();
        //这个结果会被打印处理
        applicationContext.publishEvent(new MyApplicationEvent("Hello World")); 
		//这个结果会被打印出来
        applicationContext.publishEvent(new MyApplicationEvent("1222")); 
        //这个结果不会被打印，因为没有添加关于这个事件的监听器
        applicationContext.publishEvent("6666"); 
    }

    //自定义事件
    static class MyApplicationEvent extends ApplicationEvent{

        public MyApplicationEvent(Object source) {
            super(source); //source是事件源
        }
    }
}
```



# 3. SpringBoot 的事件 监听

- 事件

  - `ApplicationEnvironmentPreparedEvent`

  - ` ApplicationPreparedEvent`

  - `ApplicationStartingEvent`

  - `ApplicationReadyEvent`

  - `ApplicationFailedEvent`

## 3.1 ConfigFileApplicationListener

`ConfigFileApplicationListener` 管理配置文件，例如application.properties，application-{profile}.properties以及application.yaml。

而`ConfigFileApplicationListener`的文件信息可以在spring-boot 的classpath下的/META-INF/spring.factories文件中可以看到： 

java SPI 是 java.util.ServiceLoader

Spring 的 SPI  :

```properties
# Application Listeners
org.springframework.context.ApplicationListener=\
org.springframework.boot.ClearCachesApplicationListener,\
org.springframework.boot.builder.ParentContextCloserApplicationListener,\
org.springframework.boot.context.FileEncodingApplicationListener,\
org.springframework.boot.context.config.AnsiOutputApplicationListener,\
org.springframework.boot.context.config.ConfigFileApplicationListener,\
org.springframework.boot.context.config.DelegatingApplicationListener,\
org.springframework.boot.context.logging.ClasspathLoggingApplicationListener,\
org.springframework.boot.context.logging.LoggingApplicationListener,\
org.springframework.boot.liquibase.LiquibaseServiceLocatorApplicationListener
```

 

# 4. SpringCloud 事件监听

​    全局搜索spring.factories，可以查找到这个文件，从而看到事件如下：

```properties
# AutoConfiguration
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration,\
org.springframework.cloud.autoconfigure.LifecycleMvcEndpointAutoConfiguration,\
org.springframework.cloud.autoconfigure.RefreshAutoConfiguration,\
org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration,\
org.springframework.cloud.autoconfigure.WritableEnvironmentEndpointAutoConfiguration
# Application Listeners
org.springframework.context.ApplicationListener=\
org.springframework.cloud.bootstrap.BootstrapApplicationListener,\
org.springframework.cloud.bootstrap.LoggingSystemShutdownListener,\
org.springframework.cloud.context.restart.RestartListener
# Bootstrap components
org.springframework.cloud.bootstrap.BootstrapConfiguration=\
org.springframework.cloud.bootstrap.config.PropertySourceBootstrapConfiguration,\
org.springframework.cloud.bootstrap.encrypt.EncryptionBootstrapConfiguration,\
org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration,\
org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
```

## 4.1 BootstrapApplicationListener

```xml
建议把这个包导入，可以监听一些信息
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

//然后在application.properties中加入 配置,这里是关闭授权
management.security.enabled=false

//这个可以看到你当前服务的所有上下文bean
然后访问localhost:8080/beans

//获取当前spring-cloud的运行环境信息
localhost:8080/env
....
```

## 1）BootstrapApplicationListener优先级

> ` BootstrapApplicationListener`加载的优先级是优于`ConfigFileApplicationListener`的。
>
> 所以spring.cloud的内部bootstrap配置名，通过application.propertis配置是无效的，
>
> 当然可以通过命令启动来配置
>
> 原因在于：`BootstrapApplicationListener`的order（6）值小于`ConfigFileApplicationListener`（order=11），可以参考源码



  ## 2）BootstrapApplicationListener 作用

- 这个类负责加载bootstrap.properties或者bootstrap.yml

- 负责初始化BootStrap ApplicationContext ID ='bootstrap'

  - ```
    ConfigurableApplicationContext context =builder.run();
    ```

> Boostrap是一个根Spring上下文，parent=null;
>
> 这里有点类似 Java中的ClassLoader加载器，



## 3）Bootstrap 的配置属性

- Bootstrap配置文件路径：spring.cloud.bootstrap.location

- 覆盖远程属性：spring.cloud.config.allowOverride

- 自定义Bootstrap配置：@BootstrapConfiguration

- 自定义Bootstrap配置属性源：PropertySourceLocator

  - 实现`PropertySourceLocator`接口,并作配置配放到IOC容器中，实现这个接口

  - 重新在当前的资源路径下添加/META-INF/spring.factories定义

    `org.springframework.cloud.bootstrap.BootstrapConfiguration= com.xxx.自定义的类全路径`



## 4) Environment

`ENv端点`：EnvironmentEndPoint

`Environment`关联多个带名称的`PropertySource`,Environment允许同名，优先级高胜出

内部实现

```java
public class MutablePropertySources implements PropertySources {

	private final List<PropertySource<?>> propertySourceList = new CopyOnWriteArrayList<>();
    ...
}

//propertySourceList  FIFO
//MutablePropertySources提供addFirst和addLast提高优先级
```



SpringFramework启动的时候调用,源码调用过程如下：

```java
AbstractApplication	refresh() ->prepareRefresh() -> initPropertySources

->最后调用到AbstractRefreshableWebApplicationContext 的initPropertySources()
@Override
protected void initPropertySources() {
		ConfigurableEnvironment env = getEnvironment();
		if (env instanceof ConfigurableWebEnvironment) {
			((ConfigurableWebEnvironment) env).initPropertySources(this.servletContext, this.servletConfig);
		}
}
```

`Environment`有两种实现方式：

- 普通类型`StandardEnvironment`
- Web类型`StandardServletEnvironment`

`Environment`关联着一个`PropertySource`实例；`PropertySources`关联着多个`PropertySource`，并且有优先级;

比较常用的PropertySource：

`SystemEnvironmentPropertySource`

