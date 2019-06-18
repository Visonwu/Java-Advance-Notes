**ss免费网站参考**：[https://github.com/Alvin9999/new-pac/wiki/ss%E5%85%8D%E8%B4%B9%E8%B4%A6%E5%8F%B7](https://github.com/Alvin9999/new-pac/wiki/ss免费账号)



**步骤一：**保证本地主机能够使用ss进行外网访问，并**开启局域网可以访问**

​		我这里用的ss，所以端口统一都是1080；

**步骤二：**然后主机上使用ipconfig获取当前的网络ip，一般我们用的wifi，查看wifi下的IP地址

​		例如我这里是：**192.168.4.161**

**步骤三：**通过shell连接虚拟机，打开隐藏文件 `.bash_profile`，然后在文件未添加如下代码：

```bash
# proxy_addr表示代理的地址，port表示端口
export http_proxy=”http://proxy_addr:port”
export https_proxy="http://proxy_addr:port"
export ftp_proxy="http://proxy_addr:port"

#所以这里我们配置的是
export http_proxy=”http://192.168.6.92:1080”
export https_proxy="http://192.168.6.92:1080"
export ftp_proxy="http://192.168.6.92:1080"
```

**步骤四：**然后`source .bash_profile`，使当前修改的配置文件生效



最后就可以在vmware中就通过命令下载外网的安装包，进行有效的上网了。