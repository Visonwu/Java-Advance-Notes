# 1. k8s安装文档

- 最难学习方案：https://github.com/kelseyhightower/kubernetes-the-hard-way
- 单机版搭建：https://github.com/kubernetes/minikube
- 集群构建工具：https://github.com/kubernetes/kubeadm

![1559643850377](//ws3.sinaimg.cn/mw690/b8a27c2fgy1g3par4j1uej211l0nggum.jpg)

# 2.安装minikube

## 2.1 安装kubectl

使用官方镜像,注意需要科学上网

```bash
$ cat <<EOF > /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=https://packages.cloud.google.com/yum/repos/kubernetes-el7-x86_64
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=https://packages.cloud.google.com/yum/doc/yum-key.gpg https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
EOF

$ yum install -y kubectl
```



## 2.2 安装Minikube

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



## 2.3 获取Minikube

```bash
curl -Lo minikube https://storage.googleapis.com/minikube/releases/v0.28.1/minikube-linux-amd64 && chmod +x minikube && sudo mv minikube /usr/local/bin/
```



## 2.4 启动Minikube

```bash
在minikube当前目录执行 

minikube start
```



## 2.5 删除RC pods

```bash
kubectl delete -f [rc配置文件]

kubectl delete pod [pod名称] --grace-period=0 --force
```