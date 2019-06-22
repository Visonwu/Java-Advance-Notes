  

​		Ribbon是一个客户端负载平衡器，它为您提供了对HTTP和TCP客户机行为的大量控制。Feign已经使用Ribbon。



通过Spring Cloud Ribbon的封装，我们在微服务架构中使用客户端负载均衡调用非常简单，只需要如下两步：

​        ▪️服务提供者只需要启动多个服务实例并注册到一个注册中心或是多个相关联的服务注册中心。

​        ▪️服务消费者直接通过调用被`@LoadBalanced`注解修饰过的RestTemplate来实现面向服务的接口调用。

----

​	

# 1.RestTemplate

**使用**

```java
@LoadBalanced
RestTemplate restTemplate;

ResponseEntity<User> responseEntity =new RestTemplate()
        .getForEntity("http://USER-SERVER/user/uid={1}",User.class,1);

User body = responseEntity.getBody();
```

​	RestTemplate增加一个LoadBalancerInterceptor，调用Netflix 中的LoadBalander实现，根据 Eureka 客户端应用获取目标应用 IP+Port 信息，轮训的方式调用。



# 2. 源码分析

​		`LoadBalancerAutoConfiguration` 这个类为客户端实现负载均衡做了自动化配置，包括生成`LoadBalancerInterceptor` 这个类持有`LoadBalanceClient`引用，通过`LoadBalancerInterceptor` 对RestTemplate做拦截处理。

​	这里拦截器持有`LoadBalanceClient`(具体实现`RibbonLoadBalanceClient`)引用可以对服务名进行解析获取到host和ip，然后做请求。

## 1）负载均衡客户端 -- LoadBalancerClient

​	所以负载均衡也是通过拦截器对每一个RestTemplate请求做拦截处理。

**实际请求客户端**

- LoadBalancerClient
  - RibbonLoadBalancerClient

**负载均衡上下文**

- LoadBalancerContext
  - RibbonLoadBalancerContext

```java

public interface LoadBalancerClient extends ServiceInstanceChooser {
    <T> T execute(String serviceId, LoadBalancerRequest<T> request) throws IOException;

    <T> T execute(String serviceId, ServiceInstance serviceInstance, LoadBalancerRequest<T> request) throws IOException;

    URI reconstructURI(ServiceInstance instance, URI original); //通过服务名获取host:ip
}
```

	### 2) 负载均衡器 -- ILoadBalancer

​		这里调用过程中最终选择的服务会委托给IRule.choose选择，如下链路调用情况

## 2）负载均衡器 -- ILoadBalancer

- ILoadBalancer
  - BaseLoadBalancer
    - DynamicServerListLoadBalancer
  - ZoneAwareLoadBalancer
  - NoOpLoadBalancer	

```java
RibbonLoadBalancerClient.execute() -----> ILoadBalancer.chooseServer() ---> BaseLoadBalancer.chooseServer() ---->IRule.choose()
```

​	其中`BaseLoadBalancer`做了一些基础的负载均衡，而`ZoneAwareLoadBalancer`，`DynamicServerListLoadBalancer`在基础的负载均衡中做了一些扩展

这里的`ILoadBalancer`提供如下功能：

```java
    void addServers(List<Server> newServers); //向负载均衡中维护的实例中添加服务实例

    Server chooseServer(Object key);//通过某种策略，从负载均衡器中挑选一个具体的实例

    void markServerDown(Server server);//用来表示某个服务实例已经停止服务

    /** @deprecated */
    @Deprecated
    List<Server> getServerList(boolean availableOnly);

    List<Server> getReachableServers();//获取当前正常服务的实例列表

    List<Server> getAllServers();//获取所有的服务实例列表
}
```



## 3）Ping 策略

​	这里有更新server状态的ping，在和Eureka共同使用的时候，依赖Eureka心跳来做Ping。

**PING核心策略接口**

- IPingStrategy

**PING 接口**

- IPing
  - NoOpPing
  - DummyPing
  - PingConstant
  - PingUrl  Discovery Client 实现
- NIWSDiscoveryPing





## 4）负载均衡规则 -- IRule

**核心规则接口**

- IRule

  - 随机规则：RandomRule

  - 最可用规则：BestAvailableRule

  - 轮训规则：RoundRobinRule

  - 重试实现：RetryRule

  - 客户端配置：ClientConfigEnabledRoundRobinRule

  - 可用性过滤规则：AvailabilityFilteringRule

  - RT权重规则：WeightedResponseTimeRule

  - 规避区域规则：ZoneAvoidanceRule



# 3.自动化配置

## 1） 默认配置类

Ribbon中每个接口有多种不同策略实现，所以Spring Cloud Ribbon提供自动化配置如下这些类：

- ```
  IClientConfig 默认使用DefaultClientConfigImpl
  ```

- ```
  IRule 默认使用区域亲和性的 ZoneAvoidanceRule
  ```

- ```
  IPing 默认使用 NoOpPing；这个返回不对服务进行检测，默认所有的服务都是可用的
  ```

- ```
  ServerList 默认使用ConfigurationBasedServerList
  //这个默认可以从配置中加载serverList,如下所示：
  <clientName>.<nameSpace>.listOfServers=<comma delimited hostname:port strings>
  ```

- ```
  ServerListFilter 默认使用ZonePreferenceServerListFilter 该策略有限过滤和请求放同一个区域的服务实例
  ```

- ```
  ILoadBalancer 默认使用ZoneAwareLoadBalancer，具备区域感知能力
  ```

## 2） 修改默认配置

​	比如要修改IPing的规则，直接把需要的规则加入的Bean容器中即可，他会自动替换掉原来的规则

**1）整体设置**

```java
//这个会默认把NoOpPing替换掉
@Configuration
public class MyConfiguration {

    @Bean
    public IPing ribbonPing(IClient config){
        return new PingUrl();
    }
}
```

**2）单个服务设置**

​	更加细粒度对于单个服务的设置，可以通过如下方式

```java
@RibbonClient(name="hello-service",configuration = MyConfiguration.class)
public class HelloConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloConsumerApplication.class, args);
    }

}
```

​		在Camdem中对于这个配置做了一定优化，因为通过上面的注解，单个服务的来做这样的配置会造成代码很难看，所以提供如下方式来实现。

```java
<clientName>.<nameSpace>.<key>=<value>

//clientName服务名
//默认nameSpace是ribbon,
//key是不同的接口默认使用名，这个需要参考org.springframework.cloud.netflix.ribbon.PropertiesFactory
//value是自己定制化的配置类

//例如上面的hello-service配置
hello-service.ribbon.NFLoadBalancerPingClassName=com.netflix.loadbalancer.PingUrl
```

```java
//这个配置类如下
public class PropertiesFactory {

	@Autowired
	private Environment environment;

	private Map<Class, String> classToProperty = new HashMap<>();

	public PropertiesFactory() {
		classToProperty.put(ILoadBalancer.class, "NFLoadBalancerClassName");
		classToProperty.put(IPing.class, "NFLoadBalancerPingClassName");
		classToProperty.put(IRule.class, "NFLoadBalancerRuleClassName");
		classToProperty.put(ServerList.class, "NIWSServerListClassName");
		classToProperty.put(ServerListFilter.class, "NIWSServerListFilterClassName");
	}

	public String getClassName(Class clazz, String name) {
		if (this.classToProperty.containsKey(clazz)) {
			String classNameProperty = this.classToProperty.get(clazz);
			String className = environment
					.getProperty(name + "." + NAMESPACE + "." + classNameProperty);
			return className;
		}
		return null;
	}
	。。。。
}   
```





# 4. 参数配置

​	详细的属性可以参考类`CommonClientConfigKey`

## 1）全局配置

```java
ribbon.<key> = <value>
//通过如上配置。例如ribbon.ConnectTimeout=250
```



## 2）指定客户端配置

```java
<clientName>.ribbon.<key>=<value>

//例如
hello-service.ribbon.listOfServers=localhost:8081,localhost:8082,localhost:8083
```



