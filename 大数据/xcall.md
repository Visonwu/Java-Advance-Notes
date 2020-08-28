ebussiness 1-3 台机器执行相同的命令的脚本编写

```bash
 
#!/bin/bash
#验证参数
if(($#==0))
then
        echo 请传入要执行的命令!
        exit;
fi

echo "要执行的命令是:$@"

#批量执行
for((i=1;i<=3;i++))
do
        echo -----------------------ebusiness$i---------------------
        ssh  ebusiness$i $@
done
```

