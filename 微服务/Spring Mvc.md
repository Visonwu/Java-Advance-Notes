[TOC]

# 1.基础知识

- Spring Web MVC 配置Bean :WebMvcProperties
- DispatcherServlet自动装配类：DispatcherServletAutoConfiguration
- HandlerMapping： 寻找Request URI 匹配的Handler； Handler是处理的方法，当然这是一种实例

整体流程：Request ->Handler ->处理结果 -> (REST) ->普通文本

```
HandlerMapping -> RequestMappingHandlerMapping(用@RequestMapping+Handler+mapping记忆)
```

# 2.拦截器

```
HandlerInterceptor:拦截器

	处理顺序：preHandler（true）-> Handler: HandlerMethod 反射执行 Method.invoke -> postHandle -> afterCompletion
```

SpringBoot添加拦截器：

```java
//新建一个拦截器
 public class DefaultHandlerInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //拦截前
        return false;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        //拦截后
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //...
    }
} 
```



```java
//spring 启动类继承webMvcConfigureAdapter，重写添加拦截器，把上面新建的拦截器添加进去
@SpringBootApplication
public class SpringMvcApplication extends WebMvcConfigurerAdapter {

    public static void main(String[] args) {
        SpringApplication.run(SpringMvcApplication.class, args);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DefaultHandlerInterceptor());
    }
}
```



# 3.异常处理

以下三种方式都是根据 Servlet 为原型处理的，

- **状态码**
- **异常类型**
- **处理服务路径**



## 3.1 Servlet 异常处理

```xml
在web.xml中配置
<error-page>
    <error-code>404</error-code> //处理状态码
    <!--<exception-type></exception-type> --> //异常类型
    <location>/404.html</location> //处理服务
</error-page>
```



## 3.2 Spring web mvc 异常处理

- 优点：易于理解，尤其是全局异常
- 缺点：很难完全掌握所有的异常

```java
@RestControllerAdvice
public class RestControllerAdviser {

    @ExceptionHandler(ClassNotFoundException.class)
    public Object pageNotFound(HttpStatus httpStatus,
                HttpServletRequest request,
                Throwable throwable){
        HashMap<String, Object> map = new HashMap<>();
        //这里的属性需要查看servlet的规范
        map.put("statusCode",request.getAttribute("javax.servlet.error.status_code"));
        map.put("requestUri",request.getAttribute("javax.servlet.error.request_uri"));

        return map;
    }
}
```

参考：<https://docs.spring.io/spring/docs/5.0.0.RELEASE/spring-framework-reference/web.html#mvc-exceptionhandlers>



## 3.3 SpringBoot异常处理

- 优点：状态码比较固定
- 缺点：页面处理的路径必须固定

```java
package com.vison.springmvc;

import com.vison.springmvc.interceptor.DefaultHandlerInterceptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.server.ErrorPageRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@SpringBootApplication
public class SpringMvcApplication extends WebMvcConfigurerAdapter implements ErrorPageRegistrar {

    public static void main(String[] args) {
        SpringApplication.run(SpringMvcApplication.class, args);
    }

    //这里是添加拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DefaultHandlerInterceptor());
    }

    @Override
    public void registerErrorPages(ErrorPageRegistry registry) {
        //这里的  ErrorPage也是包含状态码，路径，异常类型，和servelt相同
        registry.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND,"/error.html"));
    }
}

@Controller
public class ExceptioController {

    @RequestMapping("error.html")
    public Object error(){
        
        return "error";
    }
}
```



# 4.视图技术

- View接口 

​	render方法：处理页面渲染的逻辑，例如：Velocity2，JSP，Thymeleaf

- ViewResolver

  view ﹢resolver  = 页面﹢解析器 ->resolveViewName 寻找合适/对应View对象

















