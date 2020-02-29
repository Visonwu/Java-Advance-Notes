# 1.JDBC原生使用

```java
@Test
public void testJdbc() throws IOException {
    Connection conn = null;
    Statement stmt = null;
    Blog blog = new Blog();

    try {
        // 注册 JDBC 驱动
        Class.forName("com.mysql.jdbc.Driver");

        // 打开连接
        conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/test-mybatis", "root", "123456");

        // 执行查询
        stmt = conn.createStatement();
        String sql = "SELECT bid, name, author_id FROM blog";
        ResultSet rs = stmt.executeQuery(sql);

        // 获取结果集
        while (rs.next()) {
            Integer bid = rs.getInt("bid");
            String name = rs.getString("name");
            Integer authorId = rs.getInt("author_id");
            blog.setAuthorId(authorId);
            blog.setBid(bid);
            blog.setName(name);
        }
        System.out.println(blog);

        rs.close();
        stmt.close();
        conn.close();
    } catch (SQLException se) {
        se.printStackTrace();
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
       ....
    }
}
```

# 2.TypeHandler 

​		由于Java 类型和数据库的JDBC 类型不是一一对应的（比如String 与varchar），所以我们把Java 对象转换为数据库的值，和把数据库的值转换成Java 对象，需要经过一定的转换，这两个方向的转换就要用TypeHandler。
​		

​		有的同学可能会有疑问，我没有做任何的配置，为什么实体类对象里面的一个String属性，可以保存成数据库里面的varchar 字段，或者保存成char 字段？

​		这是因为MyBatis 已经内置了很多TypeHandler（在type 包下），它们全部全部注册在TypeHandlerRegistry 中，他们都继承了抽象类BaseTypeHandler，泛型就是要处理的Java 数据类型,比如：BooleanTypeHandler，IntegerTypeHandler，DoubleTypeHandler等；



​		当我们做数据类型转换的时候，就会自动调用对应的TypeHandler 的方法如果我们需要自定义一些类型转换规则，或者要在处理类型的时候做一些特殊的动作，就可以编写自己的TypeHandler，跟系统自定义的TypeHandler 一样，继承抽象类BaseTypeHandler<T>。有4 个抽象方法必须实现，我们把它分成两类：

- set 方法从Java 类型转换成JDBC 类型的

- get 方法是从JDBC 类型转换成Java 类型的。

```java
从Java 类型到JDBC 类型
	setNonNullParameter：设置非空参数

从JDBC 类型到Java 类型
	getNullableResult：获取空结果集（根据列名），一般都是调用这个
	getNullableResult：获取空结果集（根据下标值）
	getNullableResult：存储过程用的
```

使用：

## 2.1 继承BaseTypeHandler

```java
public class MyTypeHandler extends BaseTypeHandler<String> {
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter,
    JdbcType jdbcType)
    throws SQLException {
    // 设置String 类型的参数的时候调用，Java 类型到JDBC 类型
    System.out.println("---------------setNonNullParameter1："+parameter);
    ps.setString(i, parameter);
    }
    
    ..
}       
```



## 2.2 配置

```xml
<typeHandlers>
	<typeHandler handler="com.gupaoedu.type.MyTypeHandler"></typeHandler>
</typeHandlers>
```



## 2.3 使用

```xml
# 插入值
<insert id="insertBlog" parameterType="com.gupaoedu.domain.Blog">
   insert into blog (bid, name, author_id)
	values (#{bid,jdbcType=INTEGER},
	#{name,jdbcType=VARCHAR,typeHandler=com.vison.type.MyTypeHandler},
	#{authorId,jdbcType=INTEGER})
</insert>

#返回值：
<result column="name" property="name" jdbcType="VARCHAR"
typeHandler="com.vison.type.MyTypeHandler"/>
```

