# 1. Istio组件介绍

> Components官网 ：https://istio.io/docs/ops/deployment/architecture/#components
>
> Istio的架构图如下：
>
> ![QcZTTe.jpg](https://s2.ax1x.com/2019/12/12/QcZTTe.jpg)

## 1.1 Envoy

Proxy在Istio架构中必须要有

Envoy是由Lyft开发并开源，使用C++编写的高性能代理，负责在服务网格中服务的进出流量。 

> 官网 ：https://www.envoyproxy.io/
>
> github ：https://github.com/envoyproxy/envoy

**Features** 

- Dynamic service discovery 

- Load balancing 

- TLS termination 

- HTTP/2 and gRPC proxies 

- Circuit breakers 

- Health checks 

- Staged rollouts with %-based traffiffiffic split 

- Fault injection 

- Rich metrics 

**为什么选择Envoy?**

对于Sidecar/Proxy其实不仅仅可以选择Envoy，还可以用Linkerd、Nginx和NginMesh等。 

像Nginx作为分布式架构中比较广泛使用的网关，Istio默认却没有选择，是因为Nginx没有Envoy优秀的 

配置扩展，Envoy可以实时配置。



## 1.2 Mixer

> **Mixer**在Istio架构中不是必须的

> 官网 ：https://istio.io/docs/ops/deployment/architecture/#mixer

**作用：**

- 为集群执行访问控制，哪些用户可以访问哪些服务，包括白名单检查、ACL检查等 
- 策略管理，比如某个服务最多只能接收多少流量请求 
- 遥测报告上报，比如从Envoy中收集数据[请求数据、使用时间、使用的协议等]，通过Adpater上 
- 报给Promethues、Heapster等



## 1.3 **Pilot**

> **Pilot**在Istio**架构中必须要有** 
>
> 官网 ：https://istio.io/docs/ops/deployment/architecture/#pilot

**作用：**

- Pilot为Envoy sidecar提供了服务发现功能，为智能路由提供了流量管理能力(比如A/B测试、金丝雀发布等)。 

- Pilot本身不做服务注册，它会提供一个接口，对接已有的服务注册系统，比如Eureka，Etcd等。 

- Pilot对配置的格式做了抽象，整理成能够符合Envoy数据层的API。 

```text
(1)Polit定了一个抽象模型，从特定平台细节中解耦，用于对接外部的不同平台 
(2)Envoy API负责和Envoy的通讯，主要是发送服务发现信息和流量控制规则给Envoy 
(3)Platform Adapter是Pilot抽象模型的实现版本，用于对接外部的不同平台
```



## 1.4 Galley

> **Galle在**Istio架构中不是必须的
>
> 官网 ：https://istio.io/docs/ops/deployment/architecture/#galley 

**作用：**

- 主要负责istio配置的校验、各种配置之间统筹，为istio提供配置管理服务。 

- 通过kubernetes的webhook机制对pilot和mixer的配置进行验证。





## 1.5 Citadel

> **Citadel**在**Istio**架构中不是必须的
>
> 官网 ：https://istio.io/docs/ops/deployment/architecture/#citadel 

在有一些场景中，对于安全要求是非常高的，比如支付，所以Citadel就是用来保证安全的。





# 2. 案例BookInfo

> 官网 ：https://istio.io/docs/examples/bookinfo/

