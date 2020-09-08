# 1. Sqoop简介

​		Sqoop是一款开源的工具，主要用于在Hadoop(Hive)与传统的数据库(mysql、oracle...)之间进行数据的传递，可以将一个关系型数据库（例如 ： MySQL ,Oracle ,Postgres等）中的数据导进到Hadoop的HDFS中，也可以将HDFS的数据导进到关系型数据库中。

​		Sqoop项目开始于2009年，最早是作为Hadoop的一个第三方模块存在，后来为了让使用者能够快速部署，也为了让开发人员能够更快速的迭代开发，Sqoop独立成为一个[Apache](https://baike.baidu.com/item/Apache/6265)项目。

Sqoop2的最新版本是1.99.7。请注意，2与1不兼容，且特征不完整，它并不打算用于生产部署。

# 2. Sqoop原理

将导入或导出命令翻译成mapreduce程序来实现。

再翻译出的mapreduce中主要是对inputformat和outputformat进行定制。



# 3.安装

安装Sqoop的前提是已经具备Java和Hadoop的环境。

## **3.1 下载并解压配置**

1) 下载地址：<http://mirrors.hust.edu.cn/apache/sqoop/1.4.6/>

2) 上传安装包 sqoop-1.4.7.bin__hadoop-2.6.0到虚拟机中

3) 解压sqoop安装包到指定目录，如：

​	`$ tar -zxvf /usr/local/sqoop-1.4.7.bin__hadoop-2.6.0 -C /usr/local/`

4)配置环境变量

```shell
可以在/etc/profile中配置，导出为全局变量
或cp sqoop-env-template.sh sqoop-env.sh在sqoop-env.sh配置
				
export HADOOP_COMMON_HOME=/usr/local/hadoop-2.7.7
export HADOOP_MAPRED_HOME=/usr/local/hadoop-2.7.7
export HBASE_HOME=/usr/local/hbase-1.4.13-pseudo
export HIVE_HOME=/usr/local/hive-1.2.2
export ZOOCFGDIR=/usr/local/zookeeper-3.4.10/conf
```

5).将连接mysql的驱动，拷贝到sqoop的lib目录
			mysql-connector-java-5.1.39.jar
6).测试

```shell
> sqoop list-databases --connect jdbc:mysql://192.168.4.157:3306/ --username root --password root
# 返回数据库列表
information_schema
ds0
ds1
gpmall
hivemetadata
league
moss
myshiro
```



# 4.数据导入

## **4.1 RDBMS到HDFS**

例子：

```shell
bin/sqoop import -Dorg.apache.sqoop.splitter.allow_text_splitter=true \
--connect jdbc:mysql://192.168.4.157:3306/mydb \
--username root \
--password root \
--table t_emp \
--target-dir /t_emp \
--delete-target-dir \
--fields-terminated-by "\t" \
--num-mappers 2 \
--split-by id \
--columns age,name \
--where 'id >= 5 and id <= 10'

---------------------------
bin/sqoop import \
--connect jdbc:mysql://192.168.4.157:3306/mydb \
--username root \
--password root \
--query "select * from t_emp where \$CONDITIONS and id >=2" \
--target-dir /t_emp \
--delete-target-dir \
--fields-terminated-by "\t" \
--num-mappers 2 \
--split-by id
```

解释：

```shell
// 代表在shell窗口中换行
bin/sqoop import \
// 连接的url
--connect jdbc:mysql://192.168.4.157:3306/mydb \
// 用户名
--username root \
// 密码
--password 123456 \
// 要导哪个表的数据
--table staff \
// 将数据导入到hdfs的哪个路径
--target-dir /company \
// 如果目标目录存在就删除
--delete-target-dir \
// 导入到hdfs上时，mysql中的字段使用\t作为分隔符
--fields-terminated-by "\t" \
// 设置几个MapTask来运行
--num-mappers 2 \
// 基于ID列，将数据切分为2片，只有在--num-mappers>1时才需要指定,选的列最好不要有null值，否则null
// 是无法被导入的！尽量选取主键列，数字列
--split-by id
// 只导入id和name 列;提示：columns中如果涉及到多列，用逗号分隔，分隔时不要添加空格
--columns id,name \
// 只导入复合过滤条件的行
--where 'id >= 10 and id <= 20' \
// 执行查询的SQL，将查询的数据进行导入，如果使用了--query,不加再用--table,--where,--columns
// 只要使用--query ，必须添加$CONDITONS,这个条件会被Sqoop自动替换为一些表达式
--query "SQL" ' and $CONDITIONS;'


提示：must contain '$CONDITIONS' in WHERE clause.
如果query后使用的是双引号，则$CONDITIONS前必须加转移符，防止shell识别为自己的变量。
```



## 4.2 RDBMS到Hive

​	Sqoop导入到hive，先将数据导入到HDFS，再将HDFS的数据，load到hive表中！

​	导入到hdfs的默认的临时目录是/user/用户名/表名

​	拷贝 hive lib 包下 hive-exec-1.2.2.jar; hive-common-1.2.2.jar 至 sqoop 的lib包下,可能导入到hive失败

举例：

```shell
$ bin/sqoop import \
--connect jdbc:mysql://192.168.4.157:3306/weixin \
--username root \
--password root \
--table paste \
--num-mappers 1 \
//导入到hive
--hive-import \
//导入到hive表中字段的分隔符
--fields-terminated-by "\t" \
// 是否以insert overwrite方式覆盖导入数据
--hive-overwrite \
// 要导入的hive表的名称，会自动帮助我们建表。建议还是在hive中手动建表，需要注意和mysql表的数据类型匹配
--hive-table paste

-----------------------------

> bin/sqoop import \
--connect jdbc:mysql://192.168.4.157:3306/weixin \
--username root \
--password root \
--table paste \
--num-mappers 1 \
--hive-import \
--fields-terminated-by "\t" \
--hive-overwrite \
--hive-table paste



#shell
import_data() {
/opt/module/sqoop/bin/sqoop import \
--connect jdbc:mysql://hadoop102:3306/$db_name \
--username root \
--password 000000 \
--target-dir /origin_data/$db_name/db/$1/$db_date \
--delete-target-dir \
--num-mappers 1 \
--fields-terminated-by "\t" \
--query "$2"' and $CONDITIONS;'
}

import_sku_info(){
  import_data "sku_info" "select 
id, spu_id, price, sku_name, sku_desc, weight, tm_id,
category3_id, create_time
  from sku_info where 1=1"
}
```



## 4.3  RDBMS到Hbase

	在执行导入时，sqoop是可以帮我们自动建表，在使用低版本的hbase时和sqoop没有匹配上，建表会失败！
	建议手动建表！
```shell
bin/sqoop import \
--connect jdbc:mysql://192.168.4.157:3306/weixin \
--username root \
--password root \
--table prize \
//如果表不存在，hbase自动建表
--hbase-create-table \
// 导入的表名
--hbase-table "prize" \
// mysql的哪一列作为rowkey
--hbase-row-key "id" \
//导入的列族名
--column-family "info" \
--num-mappers 1 \
--split-by id	
		
---------------------------
		
bin/sqoop import \
--connect jdbc:mysql://192.168.4.157:3306/weixin \
--username root \
--password root \
--table prize \
--hbase-create-table \
--hbase-table "prize" \
--hbase-row-key "id" \
--column-family "info" \
--num-mappers 1 
```



# 5.数据导出

​		在Sqoop中，“导出”概念指：从大数据集群（HDFS，HIVE）向非大数据集群（RDBMS）中传输数据，叫做：导出，即使用export关键字。

```shell
# 案例hdfs export 到mysql
 bin/sqoop export \
--connect jdbc:mysql://192.168.4.157:3306/company \
--username root \
--password 123456 \
//要导出的mysql的表名
--table staff2 \
--num-mappers 1 \
//导出的数据在hdfs上的路径
--export-dir /company \
// 导出时，基于哪一列判断数据重复
--update-key 
// 导出的数据的分隔符
--input-fields-terminated-by "\t"

--------------------------------
sqoop export \
--connect 'jdbc:mysql://192.168.4.157:3306/test?useUnicode=true&characterEncoding=utf-8' \
--username root \
--password root \
--table customseat \
--num-mappers 1 \
--export-dir /user/root/sqoop/ \
--update-key id \
--update-mode  allowinsert \
--input-fields-terminated-by "\t"


# 提示：Mysql中如果表不存在，不会自动创建
#注意上面配置的字符编码要和数据库相同
在mysql中，执行插入时，如果对于某些唯一列，出现了重复的数据，那么会报错Duplicate Key！
此时，对于重复的列，如果希望指定更新其他列的操作，那么可以使用以下写法：
INSERT INTO t_emp2 VALUE(6,'jack',30,3,100001) ON DUPLICATE KEY UPDATE
NAME=VALUES(NAME),age=VALUES(age),deptid=VALUES(deptid),empno=VALUES(empno);


	在执行export导出时，默认的导出语句适用于向一个新的空表导数据的场景！每一行要导出的记录，都会
转换为Insert语句执行查询，此时如果说触犯了表的某些约束，例如主键唯一约束，此时Insert失败，
Job失败！

	如果要导入的表已经有数据了，此时可以指定--update-key参数，通过此参数，可以讲导入的数据，使用
updata语句进行导入，此时，只会更新重复的数据，不重复的数据是无法导入的！

	如果希望遇到重复的数据，就更新，不重复的数据就新增导入，可以使用--update-key，结合
--update-mode(默认为updateonly)=allowinsert。
```



# 6.脚本打包

```shell
使用opt格式的文件打包sqoop命令，然后执行

1) 创建一个.opt文件
$ mkdir opt
$ touch opt/job_mysql2hdfs.opt

2) 编写sqoop脚本
$ vim opt/job_mysql2hdfs.opt

import --connect \
jdbc:mysql://192.168.4.157:3306/company \
--username root \
--password 123456 \
--table staff \
--target-dir /company3 \
--delete-target-dir \
--num-mappers 1 \
--fields-terminated-by "\t" \
--split-by id

3) 执行该脚本
$ bin/sqoop --options-file opt/job_mysql2hdfs.opt
```

