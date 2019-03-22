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

Spring SPI  :

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

  BootstrapApplicationListener 这个类负责监听bootstrap.properties或者bootstrap.yml。



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
```







