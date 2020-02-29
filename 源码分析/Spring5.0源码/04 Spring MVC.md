	    Spring 的MVC 是基于Servlet 功能实现的，通过实现Servlet 接口的`DispatcherServlet `来封装其核心功能实现，通过将请求分派给处理程序，同时带有可配置的处理程序映射、视图解析、本地语言、主题解析以及上载文件支持。默认的处理程序是非常简单的Controller 接口，只有一个方法ModelAndView handleRequest(request, response） 。Spring 提供了一个控制器层次结构，可以派生子类。如果应用程序需要处理用户输入表单， 那么可以继承AbstractFormController。如果需要把多页输入处理到一个表单，那么可以继承AbstractWizardFormController。



对SpringMVC 或者其他比较成熟的MVC 框架而言，解决的问题无外乎以下几点。

- 将Web 页面的请求传给服务器。
- 根据不同的请求处理不同的逻辑羊元。
- 返回处理结果数据并跳转至响应的页面



# 1.Spring MVC 简单使用

spring MVC 是基于sevlet实现的，所以需要配置web.xml三要素

- 配置上下文读取的读取信息context-param

- 配置dispatcherServlet

- 配置ContextLoaderListener

  ContextLoaderListener 的作用就是启动Web 容器时，自动装配Application Context 的配置信息;

  他是实现了ServletContextlistener，在监听Servlet的变化

  

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app version="2.5" xmlns="http://java.sun.com/xml/ns/javaee"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://java.sun.com/xml/ns/javaee 
	http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd">

	<!--SPRING配置 -->
	<context-param>
		<param-name>contextConfigLocation</param-name>
		<param-value>classpath:resource/*.xml</param-value>
	</context-param>

	<listener>
		<listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
	</listener>
  
	<!--Spring MVC -->
	<servlet>
		<servlet-name>SpringMvc</servlet-name>
		<servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
		<init-param>
			<param-name>contextConfigLocation</param-name>
			<param-value>classpath:resource/spring-mvc.xml</param-value>
		</init-param>
		<load-on-startup>0</load-on-startup>
	</servlet>

	<servlet-mapping>
		<servlet-name>SpringMvc</servlet-name>
		<url-pattern>/</url-pattern>
	</servlet-mapping>
	
	
</web-app> 
```

## 1.1 分析ContextLoaderListener

​		ServletContext 启动之后会调用ServletContextListener 的contextlnitialized 方法，那么，我们就从这个函数开始进行分析。

```java
/** ContextLoaderListener
 * Initialize the root web application context.
 */
@Override
public void contextInitialized(ServletContextEvent event) {
    initWebApplicationContext(event.getServletContext());
}
```

```java
public WebApplicationContext initWebApplicationContext(ServletContext servletContext) {
    if (servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE) != null) {
        throw new IllegalStateException(
            "Cannot initialize context because there is already a root application context present - " +
            "check whether you have multiple ContextLoader* definitions in your web.xml!");
    }

    Log logger = LogFactory.getLog(ContextLoader.class);
    servletContext.log("Initializing Spring root WebApplicationContext");
    if (logger.isInfoEnabled()) {
        logger.info("Root WebApplicationContext: initialization started");
    }
    long startTime = System.currentTimeMillis();

    try {
        // Store context in local instance variable, to guarantee that
        // it is available on ServletContext shutdown.
        if (this.context == null) {
            //初始化上下文环境，这里的就是ContextLoader.properties配置的实现类
            this.context = createWebApplicationContext(servletContext);
        }
        if (this.context instanceof ConfigurableWebApplicationContext) {
            ConfigurableWebApplicationContext cwac = (ConfigurableWebApplicationContext) this.context;
            if (!cwac.isActive()) {
                // The context has not yet been refreshed -> provide services such as
                // setting the parent context, setting the application context id, etc
                if (cwac.getParent() == null) {
                    // The context instance was injected without an explicit parent ->
                    // determine parent for root web application context, if any.
                    ApplicationContext parent = loadParentContext(servletContext);
                    cwac.setParent(parent);
                }
                configureAndRefreshWebApplicationContext(cwac, servletContext);
            }
        }
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, this.context);

        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        if (ccl == ContextLoader.class.getClassLoader()) {
            currentContext = this.context;
        }
        else if (ccl != null) {
            currentContextPerThread.put(ccl, this.context);
        }
	....
        return context
}
```





```java
//ContextLoaderListener的父类ContextLoader在创建上下文环境时候，有下面这个静态方法
static {
    // Load default strategy implementations from properties file.
    // This is currently strictly internal and not meant to be customized
    // by application developers.
    //所以当前类一定有这个ContextLoader.properties配置文件
    try {
        ClassPathResource resource = new ClassPathResource(DEFAULT_STRATEGIES_PATH, ContextLoader.class);
        defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
    }
    catch (IOException ex) {
        throw new IllegalStateException("Could not load 'ContextLoader.properties': " + ex.getMessage());
    }
}

//ContextLoader.properties配置文件内容，包含了context的实现类
org.springframework.web.context.WebApplicationContext=org.springframework.web.context.support.XmlWebApplicationContext

```



