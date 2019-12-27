## 1.1 Service mesh

​		很多公司借鉴了Proxy模式，推出了Sidecar的产品，比如像14年Netflflix的Prana、15年唯品会的local proxy 其实Sidecar模式和Proxy很类似，但是Sidecar功能更全面，也就是Sidecar功能和侵入式框架的功能对应

​      问题 ：这种Sidecar是为特定的基础设施而设计的，也就是跟公司原有的框架技术绑定在一起，不能成为通用性的产 品，所以还需要继续探索。 



### 1) Service Mesh之Linkerd

​			2016年1月，离开Twitter的基础设施工程师William Morgan和Oliver Gould，在github上发布了Linkerd 0.0.7 版本，从而第一个Service Mesh项目由此诞生。并且Linkerd是基于Twitter的Finagle开源项目，实现了通用 性。 

后面又出现了Service Mesh的第二个项目Envoy，并且在17年都加入了CNCF项目。

![QcV72q.png](https://s2.ax1x.com/2019/12/12/QcV72q.png)

小结 ：Linkerd解决了通用性问题，在Linkerd思想要求所有的流量都走Sidecar，而原来的Sidecar方式可以走可以直 连，这样一来Linkerd就帮业务人员屏蔽了通信细节，也不需要侵入到业务代码中，让业务开发者更加专注于业务本 身。 

问题 ：但是Linkerd的设计思想在传统运维方式中太难部署和维护了，所以就后来没有得到广泛的关注，其实主要的 问题是Linkerd只是实现了数据层面的问题，但没有对其进行很好的管理。 



### 2) Service Mesh 之Istio

​         由Google、IBM和Lyft共同发起的开源项目，17年5月发布0.1 release版本，17年10月发布0.2 release版本， 18年7月发布1.0 release版本。 

​       很明显Istio不仅拥有“数据平面（Data Plane）”，而且还拥有“控制平面（Control Plane），也就是拥有了数据 接管与集中控制能力。



> 官网Why use Istio？ ：https://istio.io/docs/concepts/what-is-istio/#why-use-istio

> 官网 ：https://istio.io/ 

> github ：https://github.com/istio 



**在Istio中到底能解决哪些问题** 

（1）针对HTTP、gRPC、WebSocket等协议的自动负载均衡 

（2）故障的排查、应用的容错、众多路由 

（3）流量控制、全链路安全访问控制与认证 

（4）请求遥测、日志分析以及全链路跟踪 

（5）应用的升级发布、频率限制和配合等 





## 2.Istio

![QcZ8eS.png](https://s2.ax1x.com/2019/12/12/QcZ8eS.png)

### 2.1 Istio架构

![QcZTTe.jpg](https://s2.ax1x.com/2019/12/12/QcZTTe.jpg)

Istio在逻辑上分为数据平面(data plane)和控制平面(control plane)两部分：

- 数据平面：由一系列以sidecar形式部署的智能代理(Envoy)组成。这些代理可以调节和控制微服务及混合器(Mixer)之间的所有网络通信
- 控制平面：负责管理和配置代理来路由流量，除此之外控制平面还配置混合器(Mixer)以执行策略和收集遥测数据

### 2.2 Istio 安装

官网 ：https://istio.io/docs/setup/getting-started/ 



#### 步骤一：Set up your platform 

​	**这里我们使用之前安装好的**Kubernetes1.14版本



#### 步骤二：Download the release 

- \# 方式一：到github上下载 ：https://github.com/istio/istio/releases 

  \# 方式二：macOs or Linux System直接下载，这个地址下载国内下载会很慢，不推荐 `curl -L https://istio.io/downloadIstio | sh - `

  \# 方式三： 使用本地下载好的/istio-1.0.6-linux.tar.gz

- 将当前下载*.tar.gz文件放大k8s的master节点上并且解压

- 然后进入istio目录中

- 将itsioctl添加到环境变量中  `export PATH=$PWD/bin:$PATH `



其中解压的tar.gz包含如下文件：

- Installation YAML fifiles for Kubernetes in install/kubernetes 

- Sample applications in samples/ 

- The istioctl client binary in the bin/ directory. istioctl is used when manually injecting ，Envoy as a sidecar proxy. 

#### 步骤三：Install Istio 

**1.安装kubernates crd**

​	Kubernetes平台对于分布式服务部署的很多重要的模块都有系统性的支持，借助如下一些平台资源可以满足大多数 分布式系统部署和管理的需求。 

​		但是在不同应用业务环境下，对于平台可能有一些特殊的需求，这些需求可以抽象为Kubernetes的扩展资源，而 Kubernetes的CRD(CustomResourceDefifinition)为这样的需求提供了轻量级的机制，保证新的资源的快速注册和使 用。在更老的版本中，TPR(ThirdPartyResource)是与CRD类似的概念，但是在1.9以上的版本中被弃用，而CRD则进 入的beta状态。 Istio就是使用CRD在Kubernetes上建构出一层Service Mesh的实现 。

`istio-1.0.6/install/kubernetes/helm/istio/templates/crds.yaml`

```
kubectl apply -f crds.yaml
```

**2.准备镜像**

> istio-1.0.6/install/kubernetes/istio-demo.yaml 
>
> 上述istio-demo.yaml文件中有很多镜像速度较慢，大家记得下载我的，然后tag，最好保存到自己的镜像仓 
>
> 库，以便日后使用。

(1)从阿里云镜像仓库下载 

​	**记得是所有节点都要下载，因为pod会调度在不同的节点上**,自己记得把这些镜像备份到自己的镜像仓库中

```shell
docker pull registry.cn-hangzhou.aliyuncs.com/istio-k8s/proxy_init:1.0.6 
docker pull registry.cn-hangzhou.aliyuncs.com/istio-k8s/hyperkube:v1.7.6_coreos.0 
docker pull registry.cn-hangzhou.aliyuncs.com/istio-k8s/galley:1.0.6 
docker pull registry.cn-hangzhou.aliyuncs.com/istio-k8s/proxyv2:1.0.6 
docker pull registry.cn-hangzhou.aliyuncs.com/istio-k8s/grafana:5.2.3  
docker pull registry.cn-hangzhou.aliyuncs.com/istio-k8s/mixer:1.0.6 
docker pull registry.cn-hangzhou.aliyuncs.com/istio-k8s/pilot:1.0.6 
docker pull registry.cn-hangzhou.aliyuncs.com/istio-k8s/prometheus:v2.3.1 
docker pull registry.cn-hangzhou.aliyuncs.com/istio-k8s/citadel:1.0.6 
docker pull registry.cn-hangzhou.aliyuncs.com/istio-k8s/servicegraph:1.0.6 
docker pull registry.cn-hangzhou.aliyuncs.com/istio-k8s/sidecar_injector:1.0.6
docker pull registry.cn-hangzhou.aliyuncs.com/istio-k8s/all-in-one:1.5
```



**3.安装istio核心组件**

(1)根据istio-1.0.6/install/kubernetes/istio-demo.yaml创建资源

```shell
kubectl apply -f istio-demo.yaml
```

(2)查看资源

```shell
kubectl get pods -n istio-system 
kubectl get svc -n istio-system # 可以给某个service配置一个ingress规则，访问试试
```



### 2.3 Istio感受

#### 1）手动注入Side car

**(1) 准备一个资源**  first-istio.yaml 

创建资源

```shell
kubectl apply -f first-istio.yaml 
kubectl get pods -> # 注意该pod中容器的数量 
kubectl get svc 
curl 192.168.80.227:8080/dockerfile 
curl 10.105.179.205/dockerfile
```

**(2)删除上述资源，重新创建，使用手动注入sidecar的方式**

```shell
kubectl delete -f first-istio.yaml 

istioctl kube-inject -f first-istio.yaml | kubectl apply -f -
```

**(3)查看资源**

```shell
kubectl get pods -> # 注意该pod中容器的数量 
kubectl get svc 
kubectl describe pod first-istio-cc5d65fc-rzdxx -> # Containers:first-istio & istio-proxy
```

**(4)删除资源**

```shell
istioctl kube-inject -f first-istio.yaml | kubectl delete -f -
```

> 这样一来，就手动给pod中注入了sidecar的container，也就是envoy sidecar;In fact ：**其实这块是先改变了**yaml文件的内容，然后再创建的pod：kubectl get pod pod-name -o yaml

first-istio.yaml如下：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: first-istio
spec:
  selector:
    matchLabels:
      app: first-istio
  replicas: 1
  template:
    metadata:
      labels:
        app: first-istio
    spec:
      containers:
      - name: first-istio
        image: registry.cn-hangzhou.aliyuncs.com/itcrazy2016/test-docker-image:v1.0
        ports:
        - containerPort: 8080  
---
apiVersion: v1
kind: Service
metadata:
  name: first-istio
spec:
  ports:
  - port: 80
    protocol: TCP
    targetPort: 8080
  selector:
    app: first-istio
  type: ClusterIP
```



#### 2） 自动注入Side Car

> 上述每次都进行手动创建肯定不爽咯，一定会有好事之者来做这件事情。 
>
> 这块需要命名空间的支持，比如有一个命名空间为istio-demo，可以让该命名空间下创建的pod都自动注入 sidecar。

(1)创建命名空间

```shell
kubectl create namespace istio-demo
```

(2)给命名空间加上label

```shell
kubectl label namespace istio-demo istio-injection=enabled
```

(3)创建资源

```shell
kubectl apply -f first-istio.yaml -n istio-demo
```

(4)查看资源

```shell
kubectl get pods -n istio-demo 
kubectl describe pod pod-name -n istio-demo 
kubectl get svc -n istio-demo
```

(5)删除资源

```shell
kubectl delete -f first-istio.yaml -n istio-demo
```

### 2.4 感受prometheus和grafana

> 其实istio已经默认帮我们安装好了grafana和prometheus，只是对应的Service是ClusterIP，我们按照之前K8s 的方式配置一下Ingress访问规则即可，但是要提前有Ingress Controller的支持哦

**(1)访问prometheus** 

​	搜索istio-demo.yaml文件 

```yaml
kind: Service 
name: http-prometheus # 主要是为了定位到prometheus的Service
```

**(2)配置prometheus的Ingress规则** 

​		prometheus-ingress.yaml

````yaml
#ingress
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: prometheus-ingress
  namespace: istio-system
spec:
  rules:
  - host: prometheus.istio.itcrazy2016.com
    http:
      paths:
      - path: /
        backend:
          serviceName: prometheus
          servicePort: 9090
````

**(3)访问grafana**

​	istio-demo.yaml文件

```yaml
kind: Service targetPort: 3000
```

**(4)配置grafana的Ingress规则** 

   grafana-ingress.yaml 

```yaml
#ingress
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: grafana-ingress
  namespace: istio-system
spec:
  rules:
  - host: grafana.istio.itcrazy2016.com
    http:
      paths:
      - path: /
        backend:
          serviceName: grafana
          servicePort: 3000
```

(5)根据两个ingress创建资源 

并且访问测试 

​	istio.itcrazy2016.com/prometheus 

​    istio.itcrazy2016.com/grafana 

```shell
kubectl apply -f prometheus-ingress.yaml 
kubectl apply -f grafana-ingress.yaml 
kubectl get ingress -n istio-system
```

