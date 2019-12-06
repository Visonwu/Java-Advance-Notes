

# 1.K8S相关概念

参考官网：<https://kubernetes.io/docs/concepts/>

## 1.1 POD

​		那k8s如何操作container呢？从感性的角度来讲，得要有点逼格，k8s不想直接操作 container，因为操作container的事情是docker来做的，k8s中要有自己的最小操作单位，称之为 Pod ；说白了，Pod就是一个或多个Container的组合



## 1.2 ReplicaSet

​	那Pod的维护谁来做呢？那就是ReplicaSet，通过selector来进行管理



## 1.3 Deployment

​	Pod和ReplicaSet的状态如何维护和监测呢？那就是通过Deployment来监控和维护的。



## 1.4 Label

​		不妨把相同或者有关联的Pod分门别类一下，那怎么分门别类呢？通过Label来做到分类



## 1.5 Service

​     具有相同label的service要是能够有个名称就好了，那就是Service



## 1.6 Node

​		上述说了这么多，Pod运行在哪里呢？当然是机器咯，比如一台centos机器，我们把这个机器 称作为Node 



## 1.7 集群节点介绍

​	难道只有一个Node吗？显然不太合适，多台Node共同组成集群才行嘛 ；这个集群要配合完成一些工作，总要有一些组件的支持吧？接下来我们来想想有哪些组件。

官网K8S架构图 https://kubernetes.io/docs/concepts/architecture/cloud-controller/ 

[![MlutYj.md.png](https://s2.ax1x.com/2019/11/11/MlutYj.md.png)](https://imgchr.com/i/MlutYj)

本地搭建大概的架构图：

[![MlmUEQ.md.png](https://s2.ax1x.com/2019/11/11/MlmUEQ.md.png)](https://imgchr.com/i/MlmUEQ)



```text
01-总得要有一个操作集群的客户端，也就是和集群打交道 kubectl 

02-请求肯定是到达Master Node，然后再分配给Worker Node创建Pod之类的 关键是命令通过kubectl过来之后，是不是要认证授权一下？ 

03-请求过来之后，Master Node中谁来接收？ APIServer 

04-API收到请求之后，接下来调用哪个Worker Node创建Pod，Container之类的，得要有调度策略 Scheduler [https://kubernetes.io/docs/concepts/scheduling/kube-scheduler/] 

05-Scheduler通过不同的策略，真正要分发请求到不同的Worker Node上创建内容，具体谁负责？ Controller Manager 

06-Worker Node接收到创建请求之后，具体谁来负责
	 Kubelet服务，最终Kubelet会调用Docker Engine，创建对应的容器[这边是不是也反应出一 点，在Node上需要有Docker Engine，不然怎么创建维护容器？]

07-会不会涉及到域名解析的问题？ DNS 

08-是否需要有监控面板能够监测整个集群的状态？ Dashboard 

09-集群中这些数据如何保存？分布式存储 ETCD 

10-至于像容器的持久化存储，网络等可以连系一下Docker中的内容
```









# 2. k8s安装文档

## 2.1 官网安装

- 最难学习方案：https://github.com/kelseyhightower/kubernetes-the-hard-way

- 在线构建方案：https://labs.play-with-k8s.com/

  - ```bash
    1. Initializes cluster master node: 
    	kubeadm init --apiserver-advertise-address $(hostname -i) 
    
    2. Initialize cluster networking: 
    	kubectl apply -n kube-system -f \ "https://cloud.weave.works/k8s/net?k8s-version=$(kubectl version | base64 |tr -d '\n')" 
    
    3. (Optional) Create an nginx deployment:
    	kubectl apply -f https://raw.githubusercontent.com/kubernetes/website/master/content/en/examples/ application/nginx-app.yaml
    ```

- 单机版搭建：https://github.com/kubernetes/minikube

- 集群构建工具：https://github.com/kubernetes/kubeadm

![1559643850377](//ws3.sinaimg.cn/mw690/b8a27c2fgy1g3par4j1uej211l0nggum.jpg)

## 2.2 Cloud上搭建

​	GitHub ：https://github.com/kubernetes/kops



## 2.3企业级解决方案CoreOS

​	coreos ：https://coreos.com/tectonic/ 



## 2.4 Minikube

K8S单节点，适合在本地学习使用 

官网 ：https://kubernetes.io/docs/setup/learning-environment/minikube/ 

GitHub ：https://github.com/kubernetes/minikube 



## 2.5 kubeadm

本地多节点 

GitHub ：https://github.com/kubernetes/kubeadm 



# 3.CentOS安装minikube

​	需要安装docker；centos不需要虚拟化技术，--vm-driver=none

- kubectl:用来和k8s 进行交互的客户端

- minikube就是包含所有组件的一个单机版k8s
- docker 是k8s运行的容器化基础
- 基于linux内核开发

## 3.1 科学上网安装

先安装kubectl

```bash
# 01 下载 
	curl -LO https://storage.googleapis.com/kubernetes-release/release/`curl -s https://storage.googleapis.com/kubernetes- release/release/stable.txt`/bin/linux/amd64/kubectl

# 02 授权 
   chmod +x ./kubectl 
   
# 03 添加到环境变量 
   sudo mv ./kubectl /usr/local/bin/kubectl 
 
# 04 检查 
  kubectl version
```

**2) 安装Minikube**

```bash
# 安装编译工具
$ yum install -y gcc kernel-devel

# 使用yum安装virtualbox
$ cat <<EOF > /etc/yum.repos.d/virtualbox.repo
[virtualbox]
name=Oracle Linux / RHEL / CentOS-\$releasever / \$basearch - VirtualBox
baseurl=http://download.virtualbox.org/virtualbox/rpm/el/\$releasever/\$basearch
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=https://www.virtualbox.org/download/oracle_vbox.asc
EOF
$ yum install -y VirtualBox-5.2

# 设置virtualbox
$ sudo /sbin/vboxconfig
```

**3) 获取Minikube**

```bash
curl -Lo minikube https://storage.googleapis.com/minikube/releases/v0.28.1/minikube-linux-amd64 && chmod +x minikube && sudo mv minikube /usr/local/bin/
```



## 3.2 不能科学上网安装

先安装docker,需要现有kubectl和minikube安装包

在安装kubectl

```bash
# 01 下载[提前下载好的] 

# 02 授权 
	chmod +x ./kubectl 

# 03 添加到环境变量 
	sudo mv ./kubectl /usr/local/bin/kubectl 
# 04 检查
	kubectl version
```

安装minikube

```bash
# 01 下载[提前下载好] 
	wget https://github.com/kubernetes/minikube/releases/download/v1.5.2/minikube- linux-amd64  

# 02 配置环境变量 
	sudo mv minikube-linux-amd64 minikube && chmod +x minikube && mv minikube /usr/local/bin/ 
	
# 03 检查 
	minikube version
	
# 04 使用minikube创建单节点的k8s	
# 需要科学上网 
	minikube start --vm-driver=none
```



## 3.3 Minikube相关命令

```bash
# 创建K8S 
	minikube start 
# 删除K8S 
	minikube delete 
# 进入到K8S的机器中 
	minikube ssh 
# 查看状态 
	minikube status 
# 进入dashboard 
	minikube dashboard
```



## 3.4 先感受一下Kubernetes 

​	既然已经通过Minikube搭建了单节点的Kubernetes，不妨先感受一些组件的存在以及操作咯

**查看连接信息**

```bash
kubectl config view 
kubectl config get-contexts 
kubectl cluster-info
```

**体验Pod**

(1)创建pod_nginx.yaml

```yaml
apiVersion: v1 
kind: Pod 
metadata: 
  name: nginx 
  labels: 
	app: nginx 
		
spec: 
  containers: 
  - name: nginx 
   	image: nginx 
   	ports: 
   	- containerPort: 80
```

(2)根据pod_nginx.yaml文件创建pod

```bash
kubectl create -f pod_nginx.yaml
```

(3)查看pod

```bash
kubectl get pods 
kubectl get pods -o wide 
kubectl describe pod nginx
```

(4)进入nginx容器

```bash
# kubectl进入 
	kubectl exec -it nginx bash 
	
# 通过docker进入 
	minikube ssh
    docker ps 
    docker exec -it containerid bash
```

(5)访问nginx，端口转发

```bash
# 若在minikube中，直接访问 

# 若在物理主机上，要做端口转发
	kubectl port-forward nginx 8080:80
```

(6)删除pod

```bash
kubectl delete -f pod_nginx.yaml
```



# 4. Window 安装Minikube

kubectl官网 ：https://kubernetes.io/docs/tasks/tools/install-kubectl/#install-kubectl-on-windows

minikube官网 ：https://kubernetes.io/docs/tasks/tools/install-minikube/

选择任意一种虚拟化的方式

```bash
• Hyper-V   (Vmare)
• VirtualBox
```

**安装kubect**

```bash
(1)根据官网步骤 [或] 直接下载: https://storage.googleapis.com/kubernetes- release/release/v1.16.2/bin/windows/amd64/kubectl.exe

(2)配置kubectl.exe所在路径的环境变量，使得cmd窗口可以直接使用kubectl命令

(4)kubectl version检查是否配置成功
```

**安装minikube**

```bash
(1)根据官网步骤 [或] 直接下载: https://github.com/kubernetes/minikube/releases/download/v1.5.2/minikube- windows-amd64.exe

(2)修改minikube-windows-amd64.exe名称为minikube.exe 

(3)配置minikube所在路径的环境变量，使得cmd窗口可以直接使用minikube命令 

(4)minikube version检查是否配置成功
```

**使用minikube创建单节点的k8s**

```bash
# 需要科学上网，不然或报错镜像拉取不到 
	minikube start --vm-driver=virtualbox --registry-mirror=https://registry.docker- cn.com 
	
# 若科学上网的条件下，会出现类似如下日志 
		Done! kubectl is now configured to use "minikube" 
		
# 若没有科学上网的条件下，会出现类似如下日志 
	[ERROR ImagePull]: failed to pull image k8s.gcr.io/kube-controller-manager
```

总结：

​	**其实就是通过minikube**创建一个虚拟机这个虚拟机中安装好了单节点的K8S环境然后通过kubectl进行交互

