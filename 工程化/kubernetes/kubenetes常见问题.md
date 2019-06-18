### 1.pod状态显示**ContainerCreating**

​	解决方案：

```bash
# 查看当前pod的信息
- kubectl describe pod [pod名称]   

## 看到报错：(open /etc/docker/certs.d/registry.access.redhat.com/redhat-ca.crt: no such file or directory)"

# 解决方法：

- yum install -y *rhsm*

- wget http://mirror.centos.org/centos/7/os/x86_64/Packages/python-rhsm-certificates-1.19.10-1.el7_4.x86_64.rpm

- rpm2cpio python-rhsm-certificates-1.19.10-1.el7_4.x86_64.rpm | cpio -iv --to-stdout ./etc/rhsm/ca/redhat-uep.pem | tee /etc/rhsm/ca/redhat-uep.pem

# 然后在用docker拉取这个地址
- docker pull registry.access.redhat.com/rhel7/pod-infrastructure:latest
```

