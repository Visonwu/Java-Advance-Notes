# 1.Hbase高可用

```shell
regionserver由master负责高可用，一个regionserver挂掉，它负责的region会自动分配给其他的regionserver!

需要配置的是master的高可用！需要在conf/backup-masters,列出备用的master!


#默认没有这个目录，里面配置其他master地址，然后重启所有的服务即可，
[ HBase]$ touch conf/backup-masters
```




# 2.Hbase的预分区





# 3.Rowkey的设计



# 4.内存分配



# 5.其他参数的配置



# 6.布隆过滤器



 