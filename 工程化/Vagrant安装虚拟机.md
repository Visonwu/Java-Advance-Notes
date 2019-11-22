采用 vagrant + virtual box 安装虚拟机



## 1. 安装Vagrant

```text
01 访问Vagrant官网
		https://www.vagrantup.com/
		
02 点击Download
		Windows，MacOS，Linux等
		
03 选择对应的版本

04 傻瓜式安装

05 命令行输入vagrant，测试是否安装成功
```



## 2. 安装Virtual box

```text
01 访问VirtualBox官网
	https://www.virtualbox.org/
	
02 选择左侧的“Downloads”

03 选择对应的操作系统版本

04 傻瓜式安装

05 [win10中若出现]安装virtualbox快完成时立即回滚，并提示安装出现严重错误
	(1)打开服务
	(2)找到Device Install Service和Device Setup Manager，然后启动
	(3)再次尝试安装
```



## 3.安装Centos7

```text
01 创建first-docker-centos7文件夹，并进入其中[目录路径不要有中文字符]

02 在此目录下打开cmd，运行vagrant init centos/7
	此时会在当前目录下生成Vagrantfile
	
03 运行vagrant up[注意不要运行，拉取远端的centos7太慢]
		此时会找centos7的镜像，本地有就用本地的，本地没有就会拉取远端的
		
04 准备centos7的box
	(1)选中命令行中提示的链接；比如：
		https://vagrantcloud.com/centos/boxes/7/versions/1905.1/providers/virtualbox.box
	(2)复制到迅雷中下载
	(3)vagrant box add centos/7 D:\迅雷下载\virtualbox.box
	(4)vagrant box list 查看本地的box[这时候可以看到centos/7]
	
05 根据本地的centos7 box创建虚拟机
	vagrant up[打开virtual box，可以发现centos7创建成功]
	
06 vagrant基本操作
	(1)vagrant ssh
		进入刚才创建的centos7中
	(2)vagrant status
		查看centos7的状态
	(3)vagrant halt
		停止centos7
	(4)vagrant destroy
		删除centos7
	(5)vagrant status
		查看当前vagrant创建的虚拟机
	(6)Vagrantfile中也可以写脚本命令，使得centos7更加丰富
		但是要注意，修改了Vagrantfile，要想使正常运行的centos7生效，必须使用vagrant reload
```



这里的我的Vagrantfile配置了网络和相关cpu

```makefile
Vagrant.configure("2") do |config|
	
   config.vm.box = "centos/7"
   # 设置网络，这里是公共网络
   config.vm.network "public_network"

   config.vm.provider "virtualbox" do |vb|
     vb.memory = "2048"
	 vb.name="vison-centos7"
	 vb.cpus=2
   end
end
```



## 4.通过xshell连接

```bash
01 查看centos7相关信息
    vagrant ssh-config
    关注:Hostname Port IdentityFile; #IdentifyFile就是我们root的密码

02 Xshell连接
    IP:127.0.0.1
    port:2222
    用户名:vagrant
    密码:vagrant
    文件:Identityfile指向的路径

03 使用root账户登录
    sudo -i
    vi /etc/ssh/sshd_config
    修改PasswordAuthentication yes
    passwd	修改密码
    systemctl restart sshd
    root vagrant 登录

```

## 5.box 的分发

```text
01 退出虚拟机
	vagrant halt
	
02 打包
	vagrant package --output first-docker-centos7.box
	
03 得到first-docker-centos7.box

04 将first-docker-centos7.box添加到其他的vagrant环境中
	vagrant box add first-docker-centos7 first-docker-centos7.box

05 得到Vagrantfile
	vagrant init first-docker-centos7
	
06 根据Vagrantfile启动虚拟机
	vagrant up [此时可以得到和之前一模一样的环境，但是网络要重新配置]
```

## 6.同步文件

```bash
#Vagrantfile文件中配置该文件
config.vm.synced_folder "D://software//nginx//html", "/usr/local/vison/wm-nginx/html"

#需要安装插件
vagrant plugin install vagrant-vbguest

#如果不用插件，建议uninstall这个插件，不然每次都会去下载插件，极慢
```



## 7.同时启动多台虚拟机

​	这里会启动manager-node，worker01-node，worker02-node三台机器

```bash
boxes = [
    {
        :name => "manager-node",
        :eth1 => "192.168.0.11",
        :mem => "1024",
        :cpu => "1"
    },
    {
        :name => "worker01-node",
        :eth1 => "192.168.0.12",
        :mem => "1024",
        :cpu => "1"
    },
    {
        :name => "worker02-node",
        :eth1 => "192.168.0.13",
        :mem => "1024",
        :cpu => "1"
    }
]

Vagrant.configure(2) do |config|

  config.vm.box = "centos/7"
  
   boxes.each do |opts|
      config.vm.define opts[:name] do |config|
        config.vm.hostname = opts[:name]
        config.vm.provider "vmware_fusion" do |v|
          v.vmx["memsize"] = opts[:mem]
          v.vmx["numvcpus"] = opts[:cpu]
        end

        config.vm.provider "virtualbox" do |v|
          v.customize ["modifyvm", :id, "--memory", opts[:mem]]
		  v.customize ["modifyvm", :id, "--cpus", opts[:cpu]]
		  v.customize ["modifyvm", :id, "--name", opts[:name]]
        end

        config.vm.network :public_network, ip: opts[:eth1]
      end
  end

end
```

