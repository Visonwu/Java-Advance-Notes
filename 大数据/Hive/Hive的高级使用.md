## 	1. NVL函数

NVL函数为null值设置默认设置,

```mysql
-- 格式 ,如果string1是null，那么返回replace_with的值，否则返回string1
NVl(string1,replace_with)

-- 第一个是变量名
hive> select nvl(null,100);
```





## 2.开启本地模式

如果数据量少的时候开启本地模式执行效率更快。

```sql
-- 注意下面的只有在reducetask的数量是1才生效

-- 开启本地MR
set hive.exec.mode.local.auto=true;

-- 设置本地local最大的MR数据量，超过这个数据量就转换为yarn上执行
set hive.exec.mode.local.auto.inputbytes.max 

-- 设置本地local最大处理的文件数，超过就在yarn上执行
set hive.exec.mode.local.auto.input.files.max
```

> 客户端输出查看列名 set hive.cli.print.header=true

## 3.字符串拼接函数

```mysql
concat 可以把多个字符串拼接起来
concat_ws:通过某一个分隔符将字符或者数组拼接起来
COLLECT_SET(col)：函数只接受基本数据类型，它的主要作用是将某字段的值进行去重汇总，产生array类型字段



格式：
concat_ws(分隔符, [字符串或者字符串的数组]+)  -> 返回通过分隔符分隔的字符串
例如：
  > SELECT concat_ws('.', 'www', array('facebook', 'com')) FROM src LIMIT 1;
  'www.facebook.com'
  
  >  select concat_ws (',','www','sss','com');
   'www.sss.com'
   
   
```



 ## 4. CASE WHEN

判断句子，根据例子来详细使用

统计每个部门男女各多少

```text
wusu	12	男	110
zhangshan	13	男	110
xiaoling	100	女	110
zhouwu	32	男	110
zhous	11	男	111
zhengj	32	女	111
wuji	11	女	111
zhoss	87	男	111
zisk	33	女	110
zkk	20	女	113
```



```mysql
-- 建表
create table person (name string,age int,sex string,deptid string) row format delimited  field terminated '\t'

-- 查询- 原始查询
select NVL(t1.deptid,t2.deptid),NVL(t1.maleCount,0),NVL(t2.femaleCount,0) from 
(select  p1.deptid,count(*) as maleCount from person p1 where p1.sex ='男' group by p1.deptid) t1
 full outer join 
(select  p2.deptid,count(*) as femaleCount from person p2 where p2.sex ='女' group by p2.deptid) t2
on t1.deptid =t2.deptid

-- 新版查询 case when 判断
select 
  deptid, 
  sum(case sex when '男' then 1 else 0 end)  maleCount,
  sum(case sex when '女' then 1 else 0 end)  femaleCount
from person  group by deptid;
```



## 5. 行转列

相关函数说明,多行变一行

- CONCAT(string A/col, string B/col…)：返回输入字符串连接后的结果，支持任意个输入字符串;

- CONCAT_WS(separator, str1, str2,...)：它是一个特殊形式的 CONCAT()。第一个参数剩余参数间的分隔符。分隔符可以是与剩余参数一样的字符串。如果分隔符是 NULL，返回值也将为 NULL。这个函数会跳过分隔符参数后的任何 NULL 和空字符串。分隔符将被加到被连接的字符串之间;

- COLLECT_SET(col)：函数只接受基本数据类型，它的主要作用是将某字段的值进行去重汇总，产生array类型字段。



问题： 获取相同星座和血型的用户?

数据：

```
孙悟空	白羊座	A
大海	射手座	A
宋宋	白羊座	B
猪八戒	白羊座	A
凤姐	射手座	A
```



```mysql
-- 建表和导入数据
create table constellation (name string,constell string,bloodtype string) row format delimited fields terminated by '\t';
load data local inpath '/usr/local/hive-1.2.2/hive-data/constellation.txt' overwrite into table constellation;

-- 查询 
select t1.base,concat_ws("|",collect_set(t1.name)) from (
select c1.name,concat(c1.constell,"星座和血型",c1.bloodtype) base from constellation c1) t1 
group by t1.base 
```

```mysql
-- collect_set的使用，对于user_id去重，然后通过concat_ws用|将用户id拼接起来
select  
    mid_id,
    concat_ws('|', collect_set(user_id)) user_id,
from dwd_start_log  where dt='2019-12-14'
group by mid_id;
```





## 6. 列转行

函数解释：

- SPLIT(string,expr): 将string根据expr字符进行分割，分割为数组array
- EXPLODE(col)：将hive一列中复杂的array或者map结构拆分成多行。

- LATERAL VIEW
  用法：LATERAL VIEW udtf(expression) tableAlias AS columnAlias
  解释：用于和split, explode等UDTF（一对多）一起使用，它能够将一列数据拆成多行数据，在此基础上可以对拆分后的数据进行聚合。



```text
数据：
《疑犯追踪》	悬疑,动作,科幻,剧情
《Lie to me》	悬疑,警匪,动作,心理,剧情
《战狼2》	战争,动作,灾难
```



```mysql
-- 建表和导入数据：
hive>create table movie_info (movie string,category string) row format delimited fields terminated by '\t';
load data local inpath '/usr/local/hive-1.2.2/hive-data/coloumn_row.txt' overwrite into table coloumn_row;


-- 数据查询 as 后面接列名，有多个列的话，用逗号分隔
hive>select  movie, category_name
from  movie_info lateral view split(category,",") table_tmp as category_name;

《疑犯追踪》	悬疑,
《疑犯追踪》 动作
《疑犯追踪》 科幻
《疑犯追踪》 剧情
《Lie to me》	悬疑
《Lie to me》 警匪
.....
```



## 7. 窗口函数

参考：https://cwiki.apache.org/confluence/display/Hive/LanguageManual+WindowingAndAnalytics

如果是分组，select后只能写分组后的字段，窗口函数则不同。

窗口函数是在指定的窗口，对每一条记录都执行一次函数



窗口函数= 窗口+函数

 **OVER()：**指定分析函数工作的数据窗口大小，这个数据窗口大小可能会随着行的变而变化

### 7.1 函数

函数： 运行的函数！仅仅支持以下函数：

```mysql
Windowing functions：
	LEAD:
LEAD (scalar_expression [,offset] [,default])： 返回当前行以下N行的指定列的列值！
												如果找不到，就采用默认值
LAG:
   LAG (scalar_expression [,offset] [,default])： 返回当前行以上N行的指定列的列值！
												如果找不到，就采用默认值
FIRST_VALUE:
	FIRST_VALUE(列名,[false(默认)])：  返回当前窗口指定列的第一个值，
						第二个参数如果为true,代表加入第一个值为null，跳过空值，继续寻找！
LAST_VALUE:
	LAST_VALUE(列名,[false(默认)])：  返回当前窗口指定列的最后一个值，
						第二个参数如果为true,代表加入第一个值为null，跳过空值，继续寻找！


统计类的函数(一般都需要结合over使用)： min,max,avg,sum,count
排名分析：  
	RANK
	ROW_NUMBER
	DENSE_RANK
	CUME_DIST   -- 从排序后的第一行到当前值之间数据 占整个数据集的百分比！
	PERCENT_RANK
	NTILE
	
	
注意：不是所有的函数在运行都是可以通过改变窗口的大小，来控制计算的数据集的范围！
	所有的排名函数和LAG,LEAD，支持使用over()，但是在over()中不能定义 window_clause
			
格式：   
	函数   over( partition by 字段  order by 字段  window_clause )	
```



### 7.2 **窗口**

函数运行时计算的数据集的范围

```mysql
 CURRENT ROW：当前行
 n PRECEDING：往前n行数据
 n FOLLOWING：往后n行数据
 UNBOUNDED：起点，UNBOUNDED PRECEDING 表示从前面的起点， UNBOUNDED FOLLOWING表示到后面的终点


窗口的大小可以通过windows_clause来指定：
(rows | range) between (unbounded | [num]) preceding and ([num] preceding | current row | (unbounded | [num]) following)
(rows | range) between current row and (current row | (unbounded | [num]) following)
(rows | range) between [num] following and (unbounded | [num]) following
					
特殊情况： 
	①在over()中既没有出现windows_clause，也没有出现order by，窗口默认为rows between UNBOUNDED  PRECEDING and UNBOUNDED  FOLLOWING
	②在over()中(没有出现windows_clause)，指定了order by，窗口默认为rows between UNBOUNDED  PRECEDING and CURRENT ROW
					
窗口函数和分组有什么区别？
	①如果是分组操作，select后只能写分组后的字段
	②如果是窗口函数，窗口函数是在指定的窗口内，对每条记录都执行一次函数
    ③如果是分组操作，有去重效果，而partition不去重！
    
    
  
```



### 7.3 举例：

```mysql
-- 举例窗口函数
over(order by id range between 1 preceding and 1 following) 		id升序后计算前一行，当前行和后面一行的数据
 over(order by id range between CURRENT ROW AND  1 following)		id升序后计算当前行和后面一行的数据
 over(partition by name order by id)								根据name分组计算，按照id升序，计算name分组的数据
 over(partition by name ,substring(datestr,1,7))					根据name分组计算，在按照datestr的1-7个字符串分组，计算分组的数据
```

数据：

```text
数据：
jack,2017-01-01,10
tony,2017-01-02,15
jack,2017-02-03,23
tony,2017-01-04,29
jack,2017-01-05,46
jack,2017-04-06,42
tony,2017-01-07,50
jack,2017-01-08,55
mart,2017-04-08,62
mart,2017-04-09,68
neil,2017-05-10,12
mart,2017-04-11,75
neil,2017-06-12,80
mart,2017-04-13,94
```

问题：

```mysql
-- （1）查询在2017年4月份购买过的顾客及总人数
 select name,count(*) over() from business where substring(datestr,1,7) = '2017-04'  group by name;

-- （2）查询顾客的购买明细及用户月购买总额	
select name,datestr,cost,sum(cost) over(partition by name, month(datestr)) sum1  from business ;

-- （3）上述的场景,要将cost按照日期进行累加
select name,datestr,cost, 
sum(cost) over() as sample1,--所有行相加 
sum(cost) over(partition by name) as sample2,--按name分组，组内数据相加 
sum(cost) over(partition by name order by datestr) as sample3,--按name分组，组内数据累加 
sum(cost) over(partition by name order by datestr rows between UNBOUNDED PRECEDING and current row ) as sample4 ,--和sample3一样,由起点到当前行的聚合 
sum(cost) over(partition by name order by datestr rows between 1 PRECEDING and current row) as sample5, --当前行和前面一行做聚合 
sum(cost) over(partition by name order by datestr rows between 1 PRECEDING AND 1 FOLLOWING ) as sample6,--当前行和前边一行及后面一行 
sum(cost) over(partition by name order by datestr rows between current row and UNBOUNDED FOLLOWING ) as sample7 --当前行及后面所有行 
from business;


-- （4）查询顾客上次的购买时间
select name,datestr,cost, 
lag(datestr,1,'1900-01-01') over(partition by name order by datestr ) as time1,    -- 根据用户，日期排名后，获取当前上次消费时间，如果没有默认为1900-01-01
lag(datestr,2) over (partition by name order by datestr) as time2 				   -- 根据用户，日期排名后，获取上次的上次消费时间
from business;


-- （5）查询前20%时间的订单信息 
-- 不精确计算
select * from (
    select name,datestr,cost, ntile(5) over(order by datestr) sorted from business   -- 根据日期排序，然后分为5组（编号是从1开始直到五），获取第一组的数据就是前20%了
) t  where sorted = 1;

-- 精确计算
 select * from
 (select name,orderdate,cost,cume_dist() over(order by orderdate ) cdnum
 from  business) tmp
 where cdnum<=0.2
 
 
-- 注意6,7例子， 没有限制窗口大小，默认是最开始行到当前行，所以使用LAST_VALUE 需要改写窗口大小
 
-- (6) 查询顾客的购买明细及顾客本月最后一次购买的时间
 select name,datestr,cost,LAST_VALUE(datestr,true) over(partition by name,substring(datestr,1,7) order by datestr rows between CURRENT  row and UNBOUNDED  FOLLOWING) 
 from business 
 
 
-- (7) 查询顾客的购买明细及顾客本月第一次购买的时间
 select name,datestr,cost,FIRST_VALUE(datestr,true) over(partition by name,substring(datestr,1,7) order by datestr ) 
 from business
```





## 8. Rank

注意：排名函数可以跟over()，但是不能定义window_clause.

在计算名次前，需要先排序！



**函数说明：**

- RANK: 允许并列，一旦有并列跳号！ 
- ROW_NUMBER: 行号！ 连续的，每个号之间差1！
- DENSE_RANK： 允许并列，一旦有并列不跳号！
- CUME_DIST：  从排序后的第一行到当前值之间数据 占整个数据集的百分比！
- PERCENT_RANK：  rank-1/ 总数据量-1  ；有点去掉一个最高值或者最低值的感觉
- NTILE(x):  将数据集均分到X个组中，返回每条记录所在的组号



举例说明：

```
孙悟空	语文	87
孙悟空	数学	95
孙悟空	英语	68
大海	语文	94
大海	数学	56
大海	英语	84
宋宋	语文	64
宋宋	数学	86
宋宋	英语	84
婷婷	语文	65
婷婷	数学	85
婷婷	英语	78
```

计算每一门学科的排名

```mysql
-- 创建表和数据导入
create table score(
name string,
subject string, 
score int) 
row format delimited fields terminated by "\t";

load data local inpath '/opt/module/datas/score.txt' into table score;


-- 查询
select name,subject,score,
rank() over(partition by subject order by score desc) rp,
dense_rank() over(partition by subject order by score desc) drp,
row_number() over(partition by subject order by score desc) rmp
from score;

name    subject score   rp      drp     rmp
孙悟空  数学    95      1       1       1
宋宋    数学    86      2       2       2
婷婷    数学    85      3       3       3
大海    数学    56      4       4       4
宋宋    英语    84      1       1       1
大海    英语    84      1       1       2
婷婷    英语    78      3       2       3
孙悟空  英语    68      4       3       4
大海    语文    94      1       1       1
孙悟空  语文    87      2       2       2
婷婷    语文    65      3       3       3
宋宋    语文    64      4       4       4
```



案例二

```mysql
 score.name  | score.subject  | score.score

-- 按照科目进行排名
select *,rank() over(partition by subject order by score desc)
from score

-- 给每个学生的总分进行排名
-- 输出4条记录
select name,sumscore,rank()  over( order by sumscore desc)
from
(select name,sum(score) sumscore from  score group by  name) 
tmp

-- 求每个学生的成绩明细及给每个学生的总分和总分排名
select *,DENSE_RANK() over(order by tmp.sumscore desc)
from
(select *,sum(score) over(partition by name)  sumscore
from score) tmp


-- 只查询每个科目的成绩的前2名
select * from
(select *,rank() over(partition by subject order by score desc) rn
from score) tmp
where rn<=2

-- 查询学生成绩明细，并显示当前科目最高分
select *,max(score) over(partition by subject)
from score

或
select *,FIRST_VALUE(score) over(partition by subject order by score desc)
from score



-- 查询学生成绩，并显示当前科目最低分
select *,min(score) over(partition by subject) from score

或
select *,FIRST_VALUE(score) over(partition by subject order by score )
from score
```



