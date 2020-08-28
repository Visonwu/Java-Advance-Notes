ebussiness 1-3 台机器同步目录数据的脚本编写

```bash
#!/bin/bash
#验证参数
if(($#!=1))
then
        echo 请传入要分发的单个文件!
        exit;
fi

#获取分发文件的绝对路径
dirpath=$(cd -P `dirname $1`; pwd)
filename=$(basename $1)

echo "要分发的文件路径是:$dirpath/$filename"

#获取当前的用户名
user=$(whoami)
#分发,前提是集群中的机器都有当前分发文件所在的父目录
for((i=1;i<=3;i++))
do
        echo -----------------------ebusiness$i---------------------
        rsync -rvlt $dirpath/$filename $user@ebusiness$i:$dirpath
done

```

