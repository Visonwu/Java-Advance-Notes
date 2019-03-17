[TOC]

Bean Validation 1.1 JSR-303

# 1.maven依赖

```xml
 <dependency>
     <groupId>org.springframework.boot</groupId>
     <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```



# 2. 常用验证技术

## 2.1 注解方式

步骤一：请求实体

```java
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;

public class User {

    @Max(value = 10000)    //这里添加注解@Max最大值不能操作10000
    private long id;

    @NotNull				//这里添加注解@NotNull不能为null
    private String name;

    private String cardNum;
 	...set,get方法
}
```

步骤二：

```java
@RequestMapping(value = "/web/save2",method= RequestMethod.POST)
public User save2(@Valid @RequestBody User user){ //@Valid注解验证是否有效否则返回400错误
  
    return user;
}

//测试调用的时候，如果id>10000  和 name为null时 会抛出异常

```



## 2.2 JAVA API方式

​	这个方式耦合了业务逻辑

```java
    @RequestMapping(value = "/web/save2,method= RequestMethod.POST)
    public User save2(@RequestBody User user){

        //API调用方式
        Assert.hasText(user.getName(),"用户名不能为null");

        return user;
    }
```

## 2.3 JVM 断言方式，测试中使用

```java
 @RequestMapping(value = "/web/save3",method= RequestMethod.POST)
 public User save3(@RequestBody User user){

    //jvm  ，如果没有使用junit不生效，以便用做测试，如果要在正常的逻辑中使用，需要在vmoptions启动参数添加 -ea
    assert user.getId()  < 1000;   

    return user;
 }
```



## 2.4 使用拦截器，aop方式处理

   `HandlerInterceptor`或者`Filter`做拦截处理 ,或者aop的方式



# 3. 自定义注解验证

* 需求：通过员工的卡号来校验，需要通过前缀和后缀来判断
     * <p>前缀必须用'WS-'<p/>
     * <p>后缀必须是数字'<p/>

## 3.1 步骤一

复制成熟的Bean Validation Annotation的模式("例如@NotNull")

```java
/**
 * 合法的 卡号校验
 * 
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = {ValidCardContraintValidator.class})  //ValidCardContraintValidator这个是后面要实现的逻辑判断类
public @interface ValidCardNum {
	//message可以用来设置返回的结果，这里没有修改用的notNull的信息
    String message() default "{javax.validation.constraints.NotNull.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
```



## 3.2 步骤二

 参考理解需要校验的注解 注解`@Constraint`

```java
@Documented
@Target({ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Constraint {
    Class<? extends ConstraintValidator<?, ?>>[] validatedBy();
}
```

## 3.3 步骤三

实现ConstraintValidator接口，编写逻辑，并把这个验证类放到自定义注解的 @Constraint的属性中

```java
import org.apache.commons.lang3.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Objects;

public class ValidCardContraintValidator implements ConstraintValidator<ValidCardNum,String> {

    @Override
    public void initialize(ValidCardNum constraintAnnotation) {

    }

    /**
     * 需求:通过员工的卡号来校验，需要通过前缀和后缀来判断
     * <p>前缀必须用'WS-'<p/>
     * <p>后缀必须是数字'<p/>
     * @param cardNum
     * @param constraintValidatorContext
     * @return
     */
    @Override
    public boolean isValid(String cardNum, ConstraintValidatorContext constraintValidatorContext) {

        String[] parts = StringUtils.split(cardNum, "-");
        //这里注意parts是否是null,否则有NPE，建议用StringUtils.delimetedListToStringArray()方法
        if (null == parts || parts.length != 2){
            return false;
        };
        String prefix = parts[0];
        String suffix  = parts[1];

        boolean validPrefix = StringUtils.equals("WS", prefix);
        boolean isNumeric = StringUtils.isNumeric(suffix);

        return validPrefix && isNumeric;
    }
}
```

## 3.4 步骤四使用

```java
public class User {

   @Max(value = 10000)
    private long id;

    @NotNull
    private String name;

    @NotNull
    @ValidCardNum    //这里添加注解
    private String cardNum;
    ...setter getter
}




@RequestMapping(value = "/web/save4",method= RequestMethod.POST)
public User save4(@Valid @RequestBody User user){
    //这里会自动验证User注解的有效性
    return user;
}
```



## 3.5 补充点，国际化

 ```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = {ValidCardContraintValidator.class})
public @interface ValidCardNum {
	//在这里的message怎么实现国际化，可以参考hibernate的数据
    //例如修改这个路径
    String message() default "{com.vison.ws.validation.invalid.cardNum.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
 ```



在目录resource中新建文件

   ValidationMessages.properties   ,内容如下：

```properties
com.vison.ws.validation.invalid.cardNum.message=the card must start  with 'WS-' and its suffix must be a number!
```

  然后再在这个目录下新建

​	ValidationMessages_zh_CN.properties ，内容：

```properties
com.vison.ws.validation.invalid.cardNum.message=卡号必须以"WS-"开头，以数字结尾
```



即最终目录是/resource/ ValidationMessages.properties /ValidationMessages_zh_CN.properties;当然这里会生成一个bundle.

最后再通过	%JAVA-HOME%/bin中的 native2ascii 对文件进行转码。然后返回的结果就可以实现语言的国际化。需要深入研究下