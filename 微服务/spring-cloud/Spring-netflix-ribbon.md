  通过Spring Cloud Ribbon的封装，我们在微服务架构中使用客户端负载均衡调用非常简单，只需要如下两步：

​        ▪️服务提供者只需要启动多个服务实例并注册到一个注册中心或是多个相关联的服务注册中心。

​        ▪️服务消费者直接通过调用被`@LoadBalanced`注解修饰过的RestTemplate来实现面向服务的接口调用。

----

​	RestTemplate增加一个LoadBalancerInterceptor，调用Netflix 中的LoadBalander实现，根据 Eureka 客户端应用获取目标应用 IP+Port 信息，轮训的方式调用。



**实际请求客户端**

- LoadBalancerClient
  - RibbonLoadBalancerClient

 

**负载均衡上下文**

- LoadBalancerContext
  - RibbonLoadBalancerContext





**负载均衡器**

-  ILoadBalancer
  - BaseLoadBalancer
    - DynamicServerListLoadBalancer
   - ZoneAwareLoadBalancer
  - NoOpLoadBalancer





**负载均衡规则**

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





PING 策略

**核心策略接口**

- IPingStrategy



**PING 接口**

- IPing

  - NoOpPing

  - DummyPing

  - PingConstant

  - PingUrl  Discovery Client 实现

##### 

- NIWSDiscoveryPing