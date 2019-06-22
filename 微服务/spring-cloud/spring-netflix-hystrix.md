		`Spring-cloud- Hystrix` 是基于开源框架netflix的`Hystrix` 实现的，Hystrix具备服务降级，服务熔断，线程和信号隔离，请求缓存，请求合并以及服务监控等功能。

参考：<https://github.com/Netflix/Hystrix/wiki/How-To-Use>



# 一、Hystrix 使用

##  1.Hystrix的简单使用



​	使用  `@HystrixCommand`默认远程服务不可用，或者调用超时都会触发失败方法的调用。超时默认是2000ms



**步骤一:**引入依赖

```xml
 <dependency>
     <groupId>org.springframework.cloud</groupId>
     <artifactId>spring-cloud-starter-hystrix</artifactId>
</dependency>
```

**步骤二：**启动类加入注解`@EnableCircuitBreaker`

```java
@EnableCircuitBreaker
@EnableDiscoveryClient
@SpringBootApplication
public class HelloConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloConsumerApplication.class, args);
    }

    @LoadBalanced
    @Bean
    public RestTemplate restTemplate(){
        return new RestTemplate();
    }
}
```

**步骤三：**在需要做服务处理的地方加入` @HystrixCommand`

```java
@Service
public class HelloServiceProxy implements HelloService {

    @Autowired
    private RestTemplate restTemplate;

    //这里的user-service-provider 是提供方的应用名称
    private static final String PROVIDER_SERVER_URL_PREFIX = "http://hello-service-provider";

    @HystrixCommand(fallbackMethod = "hellofallback")
    @Override
    public String sayHello(String name) {
        String entity = restTemplate.getForObject(PROVIDER_SERVER_URL_PREFIX
                                                  +"/demo?name={1}", String.class, name);
        return "consumed: "+entity;
    }
	//这里就是上面处理失败的，重新调用的方法，这里的方法名和参数必须相同才行
    private String hellofallback(String name){
        return "failed to fetch"+name;
    }
}
```

## 2. Hystrix接口和注解的使用

​    	上面Hystrix使用注解的方式实现，当然我们还可以通过继承 `HystrixCommand`或者`HystrixObservableCommand`实现。



**编程模型**

- toObservable()   =懒加载，响应式编程

- observe()     =热加载，响应式编程，通过**toObservable().subscribe(subject)**实现
- queue()     =异步；  通过**toObservable().toBlocking().toFuture();**实现
- execute()   =同步；通过**queue().get()**实现

### 2.1 创建请求

#### 1）使用编程式

a. 继承HystrixCommand实现

```java
public class HelloCommandHystrix extends HystrixCommand<String> {

    private String name;
    private RestTemplate restTemplate;
    private static final String PROVIDER_SERVER_URL_PREFIX = "http://hello-service-provider";

    public HelloCommandHystrix(String name, RestTemplate restTemplate) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("myGroup"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("myKey")));
        this.name = name;
        this.restTemplate = restTemplate;
    }

    @Override
    protected String run() throws Exception {
        System.out.println("调用了 hystrix");
        String entity = restTemplate.getForObject(PROVIDER_SERVER_URL_PREFIX+
                                                  "/demo?name={1}", String.class, name);
        return "运行成功"+entity;
    }
    
    //降级调用
    @Override
    protected String getFallback() {
        return "调用失败";
    }
}    
```

b. 服务调用

```java
@Service(value = "helloServiceHystrix")
public class HelloServiceHystrix implements HelloService  {

    @Autowired
    RestTemplate restTemplate;

    @Override
    public String sayHello(String name) {
        
        //响应式编程
        Observable<String> toObservable = new HelloCommandHystrix(name, restTemplate)
                .toObservable();//懒加载默认，需要所有订阅者订阅后 才会执行任务(HelloCommandHystrix 中的run方法)

        //toObservable().subscribe(subject)
        Observable<String> observe = new HelloCommandHystrix(name, restTemplate)
                .observe(); //热加载模式，每次被订阅时候都会重放它的行为(HelloCommandHystrix 中的run方法)

        toObservable.subscribe(new MyObserver()); //这里添加了 MyObserver订阅，HelloCommandHystrix中的run方法才会执行
        observe.subscribe(new MyObserver());

        //异步操作  toObservable().toBlocking().toFuture();
        Future<String> queue = new HelloCommandHystrix(name, restTemplate).queue();
        //同步操作  queue().get()
        return new HelloCommandHystrix(name,restTemplate).execute();
    }

    class MyObserver implements Observer<String>{

        @Override
        public void onCompleted() {
            System.out.println("completed");
        }

        @Override
        public void onError(Throwable e) {
            System.err.println("error"+e.getCause());
        }

        @Override
        public void onNext(String str) {
            //这里的str是上面run返回执行的结果
            System.out.println("result:"+str);
        }
    }
}

```



​		上面的`HystrixCommand`具备Observe()和toObservable()，它返回的Observable只能反射一次数据。所以还提供了另外一个`HystrixObservableCommand`可以发射多次`Observable`

```java
public class HelloAbservableCommandHystrix extends HystrixObservableCommand<String> {

    private String name;
    private RestTemplate restTemplate;
    private static final String PROVIDER_SERVER_URL_PREFIX = "http://hello-service-provider";

    public HelloAbservableCommandHystrix(String name,RestTemplate restTemplate) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("myGroup")));
        this.name=name;
        this.restTemplate=restTemplate;
    }

    @Override
    protected Observable<String> construct() {
        return Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> observer) {
                try {
                    if(!observer.isUnsubscribed()) {
                        String entity = restTemplate
                            .getForObject(PROVIDER_SERVER_URL_PREFIX + "/demo?name={1}",
                                String.class, name);
                        
                        observer.onNext(entity);
                        observer.onCompleted();
                    }
                }catch (Exception e){
                    observer.onError(e);
                }
            }
        });
    }

    //降级调用
    @Override
    protected Observable<String> resumeWithFallback() {
        return super.resumeWithFallback();
    }
}
```

#### 2）注解实现

```java
@Service(value = "helloServiceProxy")
public class HelloServiceProxy implements HelloService {

    @Autowired
    private RestTemplate restTemplate;

    //这里的user-service-provider 是提供方的应用名称
    private static final String PROVIDER_SERVER_URL_PREFIX = "http://hello-service-provider";

    //同步
    @HystrixCommand(fallbackMethod = "hellofallback") //hellofallback容错的时候调用
    @Override
    public String sayHello(String name) {
        String entity = restTemplate.getForObject(PROVIDER_SERVER_URL_PREFIX
                                                  +"/demo?name={1}", String.class, name);
        return entity;
    }
    
    //降级调用
    private String hellofallback(String name){
        return "failed to fetch"+name;
    }   
    
    //异步操作
    @HystrixCommand
    public Future<String> sayHelloAsync(final String name){
        return new AsyncResult<String>() {
            @Override
            public String invoke() {
                return restTemplate.getForObject(PROVIDER_SERVER_URL_PREFIX
                                                 +"/demo?name={1}",String.class, name);
            }
        };
    }
    
    //响应式编程， ObservableExecutionMode.LAZY表示懒加载，EAGER表示热加载
    @HystrixCommand(observableExecutionMode = ObservableExecutionMode.LAZY)
    public Observable<String> sayHelloObserable(final String name){
        return Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> observer) {
                try {
                    if(!observer.isUnsubscribed()) {
                        String entity = restTemplate
                            .getForObject(PROVIDER_SERVER_URL_PREFIX + "/demo?name={1}",
                                String.class, name);
                        observer.onNext(entity);
                        observer.onCompleted();
                    }
                }catch (Exception e){
                    observer.onError(e);
                }
            }
        });
    }
}    
```

### 2.2 异常处理（传播和获取）

​		在`HystrixCommand`实现的run方法中抛出异常时，除了HystrixBadRequestException之外，其他异常都会触发服务降级处理。所以当需要在抛出异常的时候不执行降级处理，通过以下方式实现。

#### 1）注解实现

```java
//这里的restTemplate抛出异常BadRequestException，他会包装在HystrixBadRequestException中抛出，不会触发后面的fallback逻辑

//传播
@HystrixCommand(ignoreExceptions = BadRequestException.class) 
public String sayHelloExcep(String name) {
    String entity = restTemplate.getForObject(PROVIDER_SERVER_URL_PREFIX
                                              +"/demo?name={1}", String.class, name);
    return entity;
}

//获取
//降级调用 通过在降级方法中增加Throwable e参数可以实现异常的获取
private String hellofallback(String name，Throwable e){
    return "failed to fetch"+name;
}
```

#### 2）编程式

​	通过`getExecutionException()`方法可以获取对应的异常，然后进行处理

### 2.3 命令名称，分组及线程池划分

​		Hystrix会根据命令组来组织和统计命令的告警，仪表盘等信息，并且默认情况下线程的划分也是根据命令分组来实现的，相同的组名使用同一个线程池。

#### 1） 编程式

​		通过编程的方式--继承实现的Hystrix，通过构造函数，使用静态类Setter可以设置

```java
 public HelloCommandHystrix(String name, RestTemplate restTemplate) {
     
     	//这里的线程池组和命令名称都是可选的。
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("myGroup"))
                .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("myThreads"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("myKey")
     
        this.name = name;
        this.restTemplate = restTemplate;
    }
```

#### 2） 注解式

​	如下，注解中的commandKey，groupKey,threadPooKey分别实现，默认情况下，

groupKey是当前注解方法的类名；commandKey是当前注解的方法名；

```java
@HystrixCommand(commandKey = "hello",groupKey = "myGroup",threadPoolKey = "myThreads") 
public String sayHelloGroup(String name) {
    String entity = restTemplate.getForObject(PROVIDER_SERVER_URL_PREFIX
                                              +"/demo?name={1}", String.class, name);
    return entity;
}
```

### 2.4 请求缓存-----有问题

​	我们可以对当前的请求做一定的缓存，降低高并发下的线程消耗，以及请求时间的响应。

请求缓存会在run方法和contruct方法执行之前调用

#### 1）编程式

​		**开启请求缓存**：通过重载 `HystrixCommand` 或者 `HystrixObservableCommand` 的 `getCacheKey()`方法实现

```java
public class HelloCommandHystrix extends HystrixCommand<String> {

    private String name;
    private RestTemplate restTemplate;
    private static final String PROVIDER_SERVER_URL_PREFIX = "http://hello-service-provider";

    public HelloCommandHystrix(String name, RestTemplate restTemplate) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("myGroup"))
                .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("myThread"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("myKey")
                ));
        this.name = name;
        this.restTemplate = restTemplate;
    }

    @Override
    protected String run() throws Exception {
        String entity = restTemplate.getForObject(PROVIDER_SERVER_URL_PREFIX
                                                  +"/demo?name={1}", String.class, name);
        return entity;
    }

    @Override
    protected String getCacheKey() {
        return name;
    }
```

注：上面这个也许会报错

**Request caching is not available. Maybe you need to initialize the HystrixRequestContext?**

​		通过在每次调用这个方法前加上**HystrixRequestContext.initializeContext();** ，当然这样也是会出问题的，缓存没有起作用。



**清除缓存**：HystrixRequestCache.clear()来实现

```java
package com.vison.hystrix;

import com.netflix.hystrix.*;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategyDefault;
import org.springframework.web.client.RestTemplate;

import javax.naming.Name;

/**
 * @author vison
 * @date 2019/6/18 16:37
 * @Version 1.0
 */
public class HelloCommandHystrix extends HystrixCommand<String> {

    private String name;
    private RestTemplate restTemplate;
    private static final String PROVIDER_SERVER_URL_PREFIX = "http://hello-service-provider";

    private static final HystrixCommandKey key = HystrixCommandKey.Factory
        				.asKey("myKey");

    public HelloCommandHystrix(String name, RestTemplate restTemplate) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("myGroup"))
                .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("myThread"))
                .andCommandKey(key));
        this.name = name;
        this.restTemplate = restTemplate;
    }


    @Override
    protected String run() throws Exception {
        System.out.println("调用了 hystrix");
        String entity = restTemplate.getForObject(PROVIDER_SERVER_URL_PREFIX+"/demo?name={1}", String.class, name);
        return "运行成功"+entity;
    }

    @Override
    protected String getCacheKey() {
        System.out.println("执行了嘛cache");
        return String.valueOf(name);
    }
	
    //通过这个方法来清除当前commandKey中的缓存，在其他更新的命令中调用这个房发即可
    public static void clearCache(String name){
        HystrixRequestCache.getInstance(key, HystrixConcurrencyStrategyDefault
                                        .getInstance())
                						.clear(name);
    }

    @Override
    protected String getFallback() {
        return "调用失败";
    }
}
```



#### 2） 注解式

```JAVA
@Service(value = "helloServiceProxy")
public class HelloServiceProxy implements HelloService {

    @Autowired
    private RestTemplate restTemplate;

    //这里的user-service-provider 是提供方的应用名称
    private static final String PROVIDER_SERVER_URL_PREFIX = "http://hello-service-provider";

    //添加缓存
    @CacheResult  //默认使用当前所有的参数作为 cache的 key
    @HystrixCommand(fallbackMethod = "hellofallback") //hellofallback容错的时候调用
    @Override
    public String sayHello(String name) {
        String entity = restTemplate.getForObject(PROVIDER_SERVER_URL_PREFIX+"/demo?name={1}", String.class, name);
        return entity;
    }

    @CacheResult
    @HystrixCommand(fallbackMethod = "hellofallback") //hellofallback容错的时候调用
    public String sayHelloKey(@CacheKey("id") User user) {  //这里的key优先级比上面的cacheResult中定义的method低
        String entity = restTemplate.getForObject(PROVIDER_SERVER_URL_PREFIX+"/demo?name={1}", String.class, user.getId());
        return entity;
    }

    //移除缓存
    @CacheRemove(commandKey = "sayHello")
    @HystrixCommand(fallbackMethod = "hellofallback") //hellofallback容错的时候调用
    public void saveHello(User user) {
        return restTemplate.postForObject(PROVIDER_SERVER_URL_PREFIX+"/user", User.class, user);
    }
```

### 2.5 请求合并

通过HystrixCollapse实现，具体

### 2.6 属性使用

可以参考：<https://www.cnblogs.com/duan2/p/9302431.html>



当然属性的使用可以通过@HystrixCommand里面的参数设置，也可以

## 3. Hystrix - DashBoard

## 4. Turbine监控集群

​	还可以和MQ联合使用



# 二、Hystrix 原理

参考：<https://github.com/Netflix/Hystrix/wiki/How-it-Works> 



## 1. 请求流程

如下这个图展示了一个请求在调用了依赖服务后可能发生的各种情况。

![hystrix-command-flow-chart](//ws3.sinaimg.cn/mw690/b8a27c2fgy1g46u816fh0j21240ijwgy.jpg)

上面的9个步骤分别表示：

1. [Construct a `HystrixCommand` or `HystrixObservableCommand` Object](https://github.com/Netflix/Hystrix/wiki/How-it-Works#flow1)
2. [Execute the Command](https://github.com/Netflix/Hystrix/wiki/How-it-Works#flow2)
3. [Is the Response Cached?](https://github.com/Netflix/Hystrix/wiki/How-it-Works#flow3)
4. [Is the Circuit Open?](https://github.com/Netflix/Hystrix/wiki/How-it-Works#flow4)
5. [Is the Thread Pool/Queue/Semaphore Full?](https://github.com/Netflix/Hystrix/wiki/How-it-Works#flow5)
6. [`HystrixObservableCommand.construct()` or `HystrixCommand.run()`](https://github.com/Netflix/Hystrix/wiki/How-it-Works#flow6)
7. [Calculate Circuit Health](https://github.com/Netflix/Hystrix/wiki/How-it-Works#flow7)
8. [Get the Fallback](https://github.com/Netflix/Hystrix/wiki/How-it-Works#flow8)
9. [Return the Successful Response](https://github.com/Netflix/Hystrix/wiki/How-it-Works#flow9)

注意两点：

**1.命令执行**

执行命令的时候，都是依赖`toObservable()`方法，

```java
K             value   = command.execute();      //queue().get()
Future<K>     fValue  = command.queue();		//toObservable().toBlocking().toFuture()
Observable<K> ohValue = command.observe();    //热 toObservable().subscribe(subject)
Observable<K> ocValue = command.toObservable();    //冷加载

//热加载是没有添加监听事件也会执行方法，而冷加载是只有在observable添加了监听事件才会执行方法
```

**2.触发fallback时机**

- 当前命令处于'熔断/短路'的状态，断路器是打开的情况下
- 当前命令的线程池，请求队列或者信号量被占满
- HystrixCommand.run()或者HystrixObservableCommand.construct()执行抛出异常的情况下



## 2.断路器原理

如下图，展示了`HystrixCommand`和`HystrixObservableCommand`和[`HystrixCircuitBreaker`](http://netflix.github.io/Hystrix/javadoc/index.html?com/netflix/hystrix/HystrixCircuitBreaker.html) 的交互图。

![circuit-breaker-1280](//wx2.sinaimg.cn/mw690/b8a27c2fgy1g48i1bwr8qj20zk0sxtdd.jpg)

```java
public interface HystrixCircuitBreaker {
    boolean allowRequest();  //每个请求都要通过他来判断是否运行通过

    boolean isOpen();//返回断路器是否打开，打开的话，请求是不允许通过的

    void markSuccess();//闭合断路器
}
```

- 1.当前的服务请求到达了一个临界值：`HystrixCommandProperties.circuitBreakerRequestVolumeThreshold()`

- 2.当前的错误百分比超过了错误百分比的临界值：`HystrixCommandProperties.circuitBreakerErrorThresholdPercentage()`

- 3.然后断路器就会从关闭状态转化为开启

- 4.当断路器开启后，它会对当前服务的所有请求进行短路

- 5.当过了一段时间（`HystrixCommandProperties.circuitBreakerSleepWindowInMilliseconds()`)窗口滑动时间，下一次的请求来临时，这是
  状态是'半开'状态,如果当前请求是失败的，那么当前窗口滑动时间断路器再次开启，如果成功的话那么状态就关闭，重复步骤1



相关联的一些默认值可以查看`HystrixCommandProperties类`

## 3. 依赖隔离

![soa-5-isolation-focused-640](//wx4.sinaimg.cn/mw690/b8a27c2fgy1g48j52f0qrj20hs0dwwgx.jpg)

"bulkhead pattern"舱壁模式，通过该模式使每一个依赖服务创建一个单独的线程池，这样某个依赖服务出现高延迟也不会影响其他服务

- 使用线程池实现依赖隔离

- 使用信号量控制依赖服务的并发量，不过信号量不能设置超时和异步处理，有两处支持信号量

  - Hystrix的fallback降级处理的时候，总是使用Tomcat线程池
  - 如果把属性`execution.isolation.strategy` 设置为 `SEMAPHORE`，也会使用信号量而不是线程隔离

  注：如果采用信号量隔离，当当前线程出现等待状态，那么父线程将一直被阻塞，直到底层网络调用超时。

## 4.请求合并

