官网：<https://about.gitlab.com/install/#centos-7>



## 1.说明

​	安装gitlab的机器至少要有4G的内存，因为gitlab比较消耗内存

## 2.必要的依赖

```shell
sudo yum install -y curl policycoreutils-python openssh-server 
sudo systemctl enable sshd 
sudo systemctl start sshd 
sudo firewall-cmd --permanent --add-service=http 
sudo systemctl reload firewalld
```



## 3.**如果想要发送邮件，就跑**如下类容

```shell
sudo yum install postfix 
sudo systemctl enable postfix 
sudo systemctl start postfix
```



## 4.**添加gitlab的仓库地址**

​	curl https://packages.gitlab.com/install/repositories/gitlab/gitlab-ee/script.rpm.sh  | sudo bash

注意：这个下载仓库可能速度会很慢，此时可以用国内的仓库地址

```text
新建文件 /etc/yum.repos.d/gitlab-ce.repo 内容为 
[gitlab-ce] 
name=Gitlab CE Repository 
baseurl=https://mirrors.tuna.tsinghua.edu.cn/gitlab-ce/yum/el$releasever/ 
gpgcheck=0 
enabled=1
```



## 5.**设置gitlab的域名和安装gitlab**

```shell
sudo EXTERNAL_URL="https://gitlab.visonws.com" yum install -y gitlab-ee 
如果用的是国内仓库地址，则执行以下命令，其实区别就是ee和ce版 
sudo EXTERNAL_URL="https://gitlab.visonws.com" yum install -y gitlab-ce
```

此时要么买一个域名，要么在本地的hosts文件中设置一下 

```text
安装gitlab服务器的ip地址 gitlab.itcrazy2016.com

假如不想设置域名，可以直接安装 yum install -y gitlab-ee
```



## 6.**重新configure**

如果没有成功，可以运行gitlab-ctl reconfigure 



## 7.**查看gitlab运行的情况**

​		gitlab-ctl status可以看到运行gitlab服务所需要的进程 



## 8.访问

​		浏览器输入gitlab.visonws.com，此时需要修改root账号的密码



## 9.**配置已经安装好的gitlab**

​		vim /etc/gitlab/gitlab.rb 

​		修改完成之后一定要gitlab-ctl reconfigure