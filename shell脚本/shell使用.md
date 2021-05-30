# 1.变量

## 1.1 基本操作

```
# “=”前后不能有空格，空格表示bash命令执行，会找不到命令报错的
增：  变量名=变量值
删：  unset 变量名
改：  变量名=变量值
查：  echo  $变量名
		查看当前bash所有定义的变量：  set
```



## 1.2.关键字

### 1）特殊关键字
​		**readonly :** 用来修饰一个只读(不能修改，删除)变量！ readonly aa=12
​		**export:**    导出！将一个变量提升为全局变量！
​						局部变量： 默认变量只在定义变量的bash中有效！
​									如果希望在bash-a访问bash-b中定义的变量！
​										要求： ①bash-b不能关闭
​				     							   ②让bash-b将变量提升为全局变量，才能访问到！



```
注意： ①变量赋值时，值全部以字符串存在，无法进行运算！
	  ②赋值的值中有空格，需要使用引号引起来
			单引号： 不能识别$等特殊字符,不能脱义，用于纯字符串输出
			双引号：  可以脱义$,就是能够解析带有$的变量
	  ③``,作用是将引号中的命令执行的结果赋值给变量；例如 echo `pwd`  
			`命令` 等价于 $(命令) echo $(pwd)
```



## 1.3 变量的生命周期

​		在第一次新增时产生
​		变量在执行unset时，撤销，失效！
​		关闭当前bash，所有定义的变量也不会存在！

## 1.4.特殊变量

```
$?:  上一条命令的返回值！在bash中，如果返回值为0，代表上一条命令执行成功！
$#:  参数个数
$*:  参数列表。 在使用 "$*"时，将整个参数列表作为一个元素！
$@:	 参数列表
$0-n: $0:脚本名
		$1-$n: 第n个参数
		获取第10以上的参数，${n}
```



# 2. 运算符

```
1）基本语法
（1）“$((运算式))”或“$[运算式]”
（2）expr  + , - , \*,  /,  %    加，减，乘，除，取余

注意：expr运算符间要有空格; 不支持括号等
*号需要转义为\*，否则会被视为通配符；
运算指的都是整数的运算，浮点运算需要借助其他的命令！


2）例子
root>num=$((1+1))
root>echo $num
2
root>num=$[(1+4)*1+1]
root>echo $num
5

-----
root> expr 1 \* 1
2
#计算 （1+1)*2-1
root> expr `expr 1 + 1` \* 2 - 1
3
```



# 3.条件判断

## 3.1  基本语法

```
[ condition ]（注意condition前后要有空格）
注意：条件非空即为true，[ vison ]返回true，[] 返回false。
	另外前后的判断都要有空格，不然会返回true
```



## 3.常用判断

```
（1）两个整数之间比较
	-lt 小于（less than）			-le 小于等于（less equal）
	-eq 等于（equal）				-gt 大于（greater than）
    -ge 大于等于（greater equal）	   -ne 不等于（Not equal）

	[ 1 -eq 2 ]
（2） 字符串比较
	[ -z STRING ] 如果STRING的长度为零则返回为真，即空是真
	[ -n STRING ] 如果STRING的长度非零则返回为真，即非空是真
	[ STRING1 ]　 如果字符串不为空则返回为真,与-n类似
	[ STRING1 == STRING2 ] 如果两个字符串相同则返回为真
	[ STRING1 != STRING2 ] 如果字符串不相同则返回为真
	[ STRING1 < STRING2 ] 如果 “STRING1”字典排序在“STRING2”前面则返回为真。
	[ STRING1 > STRING2 ] 如果 “STRING1”字典排序在“STRING2”后面则返回为真
	[ STRING1 = STRING2 ]  记得等号恰好要有空格，否则会当成字符串，则会返回true
	
	root> [ 1 = 2 ]  
	root> echo $?
	1		

（2）按照文件权限进行判断，表示的时候当前用户的权限
	-r 有读的权限（read）			-w 有写的权限（write）
	-x 有执行的权限（execute）
	
	root> [ -r second.sh ]    # 判断当前用户对second.sh是否有读权限
	
（3）按照文件类型进行判断
	-f 文件存在并且是一个常规的文件（file）
	-e 文件存在（existence）		-d 文件存在并是一个目录（directory）
	-s 文件存在且不为空          -L 文件存在且是一个链接(link)
    
    root> [ -e second.sh ]    #second.sh是否存在
    
 (4) 逻辑判断
	[ ! EXPR ] 逻辑非，如果 EXPR 是false则返回为真。
	[ EXPR1 -a EXPR2 ] 逻辑与，如果 EXPR1 and EXPR2 全真则返回为真。
	[ EXPR1 -o EXPR2 ] 逻辑或，如果 EXPR1 或者 EXPR2 为真则返回为真。
	[ ] || [ ] 用OR来合并两个条件
	[ ] && [ ] 用AND来合并两个条件
```





# 4.流程控制

## 4.1 if

```shell
1．基本语法
if [ 条件判断式 ] 
  then 
		程序
elif 条件判断式
	then 程序..
else 程序..

fi

或

if [ 条件判断式 ] ; then 
		程序.. 
elif [条件判断式] ; then 
	程序..
else 程序
fi

注意事项：
（1）[ 条件判断式 ]，中括号和条件判断式之间必须有空格
（2）if后要有空格

上面的[]  条件判断也可以使用c语言风格 (())，而且也没有空格的限制

2)例子
if [ $1 == start ]
then
	echo 2
fi

if [ $1 == 2 ]
then
	echo 2
fi

或者
if (($1 == 2))
then
	echo 2
fi
```

## 4.2 case

```
1．基本语法
case $变量名 in 
  "值1"） 
    如果变量的值等于值1，则执行程序1 
    ;; 
  "值2"） 
    如果变量的值等于值2，则执行程序2 
    ;; 
  …省略其他分支… 
  *） 
    如果变量的值都不是以上的值，则执行此程序 
    ;; 
esac
注意事项：
1)case行尾必须为单词“in”，每一个模式匹配必须以右括号“）”结束。
2)双分号“;;”表示命令序列结束，相当于java中的break。
3）最后的“*）”表示默认模式，相当于java中的default。

2.例子
#!/bin/bash

case $1 in
	1)
		echo 1	;;
	2)
		echo 2	;;
	*)
		echo default	;;
esac

```



## 4.3 for

```
1．基本语法1
	for (( 初始值;循环控制条件;变量变化 )) 
  do 
    程序 
  done
  
或
for (( 初始值;循环控制条件;变量变化 )); do 程序; done

2.例子
	sum=0
	for ((i=0;i<10;i++))
	do
		sum=$[$sum+$i]
	done
```



## 4.4 foreach

```shell
for 变量 in 值1 值2 值3… 
  do 
    程序 
  done
  
或
for 变量 in 1 2 3; do 程序; done

或
for 变量 in {1..3}; do 程序; done
```

$* 和$@区别

```shell
for i in "$@"
do
	echo 带@引号$i
done	

for i in "$*"
do
	echo 带\*引号$i		#只要一个整体参数输出
done

# 区别 在没有双引号包含的时候，两个作用相同
	   如果加了双引号，那么"$*" 会被当成一个整体输出

```



## 4.5  while

```
1．基本语法
while [ 条件判断式 ] 
  do 
    程序
  done
或
while((表达式))
do
	程序
done

2.例子
#!/bin/bash
sum =0
i =0
while(($i<100))
do
	sum =$[$i+$sum]
	let i++   #或者i=$[$i+1]
done
```



# 5. read读取控制台输入

```
1．基本语法
	read(选项)(参数)
	选项：
		-p：指定读取值时的提示符；
		-t：指定读取值时等待的时间（秒）。
 	参数
		变量：指定读取值的变量名
	
	
2.例子

#!/bin/bash
read -t 7 -p "请输入名字:" NAME		#等待7s，如果没有-t就会一直等下去;没有-p就没有提示
echo $NAME

```



# 6.函数

## 6.1 系统函数之 dirname

dirname 文件绝对路径		

功能描述：从给定的包含绝对路径的文件名中去除最后一个"/"及后面的字符，返回前面的信息

```
root> dirname /opt/module
/opt
root> dirname /opt/module/
/opt
root> dirname ./test.sh
.
# .表示当前目录 ..表示上一级目录
```



## 6.2 系统函数之basename

```
1. basename基本语法
	basename [string / pathname] [suffix]  	

功能描述：basename命令会删掉所有的前缀包括最后一个（‘/’）字符，然后将字符串显示出来。

选项：
	suffix为后缀，如果suffix被指定了，basename会将pathname或string中的suffix去掉。
	
2.例子

root> basename /opt/module
module
root> basename /opt/module/test.sh
test.sh
root> basename ./test.sh
test.sh

root> basename ./test.sh .sh
test
```



## 6.3 自定义函数

```
1．基本语法 
function  funname[()]
{
	Action;
	[return int;]
}

#上面 () 可以省略，参数要传入的话，直接传入即可

# 注意事项：
（1）必须在调用函数地方之前，先声明函数，shell脚本是逐行运行。不会像其它语言一样先编译。
（2）函数返回值，只能通过$?系统变量获得，可以显示加：return返回，
	如果不加，将以最后一条命令运行结果，作为返回值。return后跟数值n(0-255)；0表示执行成功
	
2.例子

#!/bin/bash
# 计算两个数相加
function sum(){
	s=0
	s=$[ $1 + $2  ]
	return $s
}

read -p '请输入第一个数字：' n1;
read -p '请输入第二个数字：' n2;

sum $n1 $n2
echo $?
```



# 7.工具

## 7.1 wc 计算文件数字

wc命令用来计算数字。利用wc指令我们可以计算文件的Byte数、字数或是列数，若不指定文件名称，或是所给予的文件名为“-”，则wc指令会从标准输入设备读取数据。

```
1. 基本用法
	wc [选项参数] filename
	
	选项参数	功能
	-l	统计文件行数
	-w	统计文件的单词数
	-m	统计文件的字符数
	-c 	统计文件的字节数

2. 例子

root> wc -w test.sh
root> wc -l test.sh
```



## 7.2 cut 剪切数据

cut的工作就是“剪”，具体的说就是在文件中负责剪切数据用的。cut 命令从文件的每一行剪切字节、字符和字段并将这些字节、字符和字段输出。

```
1.基本用法
	cut [选项参数]  filename 

说明：默认分隔符是制表符

选项参数	功能
-f	f为fileds，列号，提取第几列
-d	d为Descriptor分隔符，按照指定分隔符分割列

2.例子
# 以：分隔取第一列
[root@0725pc ~]# echo $PATH | cut -d ':' -f 1

#取2,3列
[root@0725pc ~]# echo $PATH | cut -d ':' -f 2,3

#取2到5列
[root@0725pc ~]# echo $PATH | cut -d ':' -f 2-5

#取1到末尾列
[root@0725pc ~]# echo $PATH | cut -d ':' -f 1-

```



## 7.3 sed行处理

sed是一种流编辑器，它一次处理一行内容。处理时，把当前处理的行存储在临时缓冲区中，称为“模式空间”，接着用sed命令处理缓冲区中的内容，处理完成后，把缓冲区的内容送往屏幕。接着处理下一行，这样不断重复，直到文件末尾。文件内容并没有改变，除非你使用重定向存储输出。

```
1. 基本用法
	sed [选项参数]  ‘command’  filename


	选项参数	功能
	-e	直接在指令列模式上进行sed的动作编辑。

	命令功能描述
	
	命令	功能描述
	a 	新增，a的后面可以接字串，在下一行出现
	d	删除
	s	查找并替换 

2.例子

#将“mei nv”这个单词插入到sed.txt第二行下，打印;2表示第二行，a表示添加
root> sed '2a mei nv' sed.txt 


#删除sed.txt文件所有包含wo的行 /wo/ 匹配包含wo的行 d表示删除
root> sed '/wo/d' sed.txt

#删除sed.txt第二行
root> sed '2d' sed.txt

#删除sed.txt最后一行
root> sed '$d' sed.txt 

#删除sed.txt 2到最后一行
root> sed '2,$d' sed.txt

#将sed.txt文件中wo替换为ni;  注意：‘g’表示global，全部替换，不加g只会替换第一个匹配到的字符
root> sed 's/wo/ni/g' se.txt

#将sed.txt文件中的第二行删除并将wo替换为ni， 
roo> sed -e '2d' -e 's/wo/ni/g' sed.txt 
```



## 7.4 sort排序

sort命令是在Linux里非常有用，它将文件进行排序，并将排序结果标准输出。默认情况以第一个字符串的字典顺序来排序！

```
1. 基本语法
	sort(选项)(参数)

	选项	说明
	-n	依照数值的大小排序
	-r	以相反的顺序来排序
	-t	设置排序时所用的分隔字符，默认使用TAB
	-k	指定需要排序的列
	-u	u为unique的缩写，即如果出现相同的数据，只出现一行
	
	参数：指定待排序的文件列表

2.案例
	# 将/etc/passwd用：分割，然后取第三列排序，按照数字大小排列
	root> sort -t : -k 3 -n /etc/passwd
```



## 7.5 awk 操作

一个强大的文本分析工具，把文件逐行的读入，以空格为默认分隔符将每行切片，切开的部分再进行分析处理。

可以相当于一门语言了

awk内置关键字：print（打印输出）

```
1.基本用法
	awk [选项参数] ‘pattern1{action1}  pattern2{action2}...’ filename
	pattern：表示AWK在数据中查找的内容，就是匹配模式
	action：在找到匹配内容时所执行的一系列命令

	选项参数	功能
	-F	指定输入文件折分隔符；默认TAB
	-v	赋值一个用户定义变量

2.案例

#1)搜索passwd文件以root关键字开头的所有行，并输出该行的第7列。
root> awk -F: '/^root/{print $7}' passwd 
/bin/bash

#2)搜索passwd文件以root关键字开头的所有行，并输出该行的第1列和第7列，中间以“，”号分割。
root> awk -F: '/^root/{print $1","$7}' passwd 
root,/bin/bash

注意：只有匹配了patter的行才会执行action

#3)只显示/etc/passwd的第一列和第七列，以逗号分割，且在所有行前面添加列名vison-b在最后一行添加"vison-e"。
root> awk -F : 'BEGIN{print "vison-b"} {print $1","$7} END{print "vison-e"}' /etc/passwd
vison-b
root,/bin/bash
...
vison-e
注意：BEGIN 在所有数据读取行之前执行；END 在所有数据执行之后执行。

#4)将passwd文件中的用户id(第三列)增加数值1并输出; -v赋值变量
root> awk -v i=1 -F: '{print $3+i}' passwd
```



```
3.awk的内置变量
	变量	说明
	FILENAME	文件名
	NR	已读的记录数（行号）
	NF	（切割后列的个数）


例子：

# 1）统计passwd文件名，每行的行号，每行的列数
root> awk -F: '{print "filename:"  FILENAME ", linenumber:" NR  ",columns:" NF}' passwd 
filename:passwd, linenumber:1,columns:7
filename:passwd, linenumber:2,columns:7
filename:passwd, linenumber:3,columns:7

#（2）切割IP
root> ifconfig eth0 | grep "inet addr" | awk -F: '{print $2}' | awk -F " " '{print $1}' 
192.168.1.102

#（3）查询sed.txt中空行所在的行号；^表示开始$表示结尾，中间没有数据表示空
[atguigu@hadoop102 datas]$ awk '/^$/{print NR}' sed.txt 
5
```



# 8.面试脚本

```
1)Linux常用命令
	参考答案：find、df、tar、ps、top、netstat等。（尽量说一些高级命令)
2)Linux查看内存、磁盘存储、io 读写、端口占用、进程等命令
	答案：
	1、查看内存：top
	2、查看磁盘存储情况：df -h
	3、查看磁盘IO读写情况：iotop（需要安装一下：yum install iotop）、iotop -o（直接查看输出比较高的磁盘读写程序）
	4、查看端口占用情况：netstat -tunlp | grep 端口号
		t:表示查看tcp；u表示udp，n表示域名；p表示。。
	5、查看进程：ps aux；
	
3）使用Linux命令查询file1中空行所在的行号
	awk '/^$/{print NR}' test.txt

4)有文件chengji.txt内容如下，计算第二列和
	张三 40
	李四 100
	周五 110
	
	awk -v sum=0 -F " " '{sum=sum+$2} END {print sum}' 
5) Shell脚本里如何检查一个文件是否存在？如果不存在该如何处理？
	#!/bin/bash
	if [ -f test.txt ]
	then
		echo  文件存在
	else
		echo 文件不存在
	fi
6)用shell写一个脚本，对文本中无序的一列数字排序
	sort -n test.txt

7)请用shell脚本写出查找当前文件夹（/home）下所有的文本文件内容中包含有字符”shen”的文件名称
	 grep -r "shen" /home | cut -d ":" -f	 1
```



# 9.命令执行

```shell
# bash -c command 执行多条命令
bash -c "echo 111 >> /opt/module/azkaban/test/1.txt && echo 222 >> /opt/module/azkaban/test/2.txt "


bash/sh source/. exec

bash 或 sh 或 shell script 执行时，另起一个子shell，其继承父shell的环境变量，其子shelll的变量执行完后不影响父shell。

source xx.sh 或者 .xx.sh  
	source 和 . 不启用新的shell，在当前shell中执行，设定的局部变量在执行完命令后仍然有效。

exec 是用被执行的命令行替换掉当前的shell进程，且exec命令后的其他命令将不再执行。
```



