		`Spring-cloud- Hystrix` 是基于开源框架netflix的`Hystrix` 实现的，Hystrix具备服务降级，服务熔断，线程和信号隔离，请求缓存，请求合并以及服务监控等功能。



# 1.Hystrix的简单使用

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



# 2. Hystrix接口和注解的使用

​    	上面Hystrix使用注解的方式实现，当然我们还可以通过继承 `HystrixCommand`或者`HystrixObservableCommand`实现。



**编程模型**

- toObservable()   =懒加载，响应式编程

- observe()     =热加载，响应式编程，通过**toObservable().subscribe(subject)**实现
- queue()     =异步；  通过**toObservable().toBlocking().toFuture();**实现
- execute()   =同步；通过**queue().get()**实现

## 2.1 创建请求

### 1）使用编程式

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



### 2）注解实现

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



## 2.2 异常处理（传播和获取）

​		在`HystrixCommand`实现的run方法中抛出异常时，除了HystrixBadRequestException之外，其他异常都会触发服务降级处理。所以当需要在抛出异常的时候不执行降级处理，通过以下方式实现。



### 1）注解实现

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



### 2）编程式

​	通过`getExecutionException()`方法可以获取对应的异常，然后进行处理





















