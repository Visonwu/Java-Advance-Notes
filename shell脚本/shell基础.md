# 1. shell是什么？

​	Linux操作系统的核心是kernal（内核）！
​	当应用程序在执行时，需要调用计算机硬件的cpu,内存等资源！
​	程序将指令发送给内核执行！
​	为了防止程序发送一些恶意指令导致损坏内核，在内核和应用程序接口之间，设置一个中间层，称为shell!
​	

	本质上来说：
		shell:   一个可以解释shell规定的语法命令的解释器！
						解释器负责将应用程序发送的指令，进行检查，合法后交给内核解释执行！返回结果！
						
		shell命令：  shell解释器要求的指定语法编写的命令！
		
		shell脚本：  多条shell命令，可以编写在一个文件中，文件中的指令，可以按照顺序执行！
						将这个文件称为shell脚本！

# 2. shell中的解释器

​	使用 $SHELL变量查看当前系统默认的解释器类型！
​	 

	 shell支持多种不同风格的解释器，通过/etc/shells文件查看！
	 bin/sh
	 bin/bash
	 bin/csh
	 ...
	 
	 默认使用 /bin/bash作为shell命令解释器！ echo $SHELL  打印出默认命令解释器
	 
	 在终端中输入： cat /etc/shells 等价于/bin/bash -c 'cat /etc/shells'.
	 默认/bin/bash必须接一个脚本，作为输入；如果是一条命令，需要加-c （command）


​	 
# 3. linux中的常用目录

- /bin:     linux用户常用的命令
  		cd 、echo、pwd
- /sbin（super user bin）: root用户(管理员)使用的常用命令！
  		对整个机器的管理命令！
    		开启网络服务：  service network start


# 4. 命令的执行

```
aa
-bash: aa: command not found ： 表示当前命令不在当前用户的环境变量！

查看环境变量： echo $PATH
```



# 5. 脚本的编写要求

①声明：  #!/bin/bash
②正文：  必须是shell解释器能否解释的命令



# 6. 脚本的执行

​	① bash / sh + 脚本
​			特点： 新开一个bash执行脚本，一旦脚本执行完毕，bash自动关闭！
​	② ./脚本，前提是当前用户对脚本有执行权限，使用当前默认的解释器执行脚本
​			特点： 新开一个bash执行脚本，一旦脚本执行完毕，bash自动关闭！
​    ③ source / .  +脚本  使用当前默认的解释器执行脚本，并不要求当前用户对脚本有执行权限
​			特点： 在当前bash执行脚本,可以获取当前bash中的变量

