# 1.索引

​	Mongodb的索引采用的B-Tree树作为存储的

参考：<https://docs.mongodb.com/manual/indexes/index.html>

## 1.1 索引操作

```bash
db.collection.createIndex( <key and index type specification>, <options> )

例如：
db.inventory.createIndex({name:-1})
```





## 1.2、索引使用需要注意的地方 

- 1)创建索引的时候注意 1 是正序创建索引-1 是倒序创建索引 

- 2)索引的创建在提高查询性能的同事会影响插入的性能 对于经常查询少插入的文档可以考虑用索引 

- 3)符合索引要注意索引的先后顺序 

- 4)每个键全建立索引不一定就能提高性能呢 索引不是万能的 

- 5)在做排序工作的时候如果是超大数据量也可以考虑加上索引 用来提高排序的性能