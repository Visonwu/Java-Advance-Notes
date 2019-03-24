# 1.Spring的Java配置方式

- 1、@Configuration 作用于类上，相当于一个xml配置文件；
- 2、@Bean 作用于方法上，相当于xml配置中的<bean/>；
- 3、@Configuration表示当前类是一个配置文件
- 4、@ComponentScan用来扫描你添加了注解的包
- 5、@PropertySource可以指定读取的配置文件，通过@Value注解获取值
- 6、@ImportResource(value={"xxx.xml"}):表示的意思是导入外部的xml配置文件
- 7、@Import 引入另外一个Configuration配置文件，可以获取其他配置文件获取的bean
- 8、@ConfigurationProperties和EnableConfigurationProperties，前者表示将properties的文件自动组装到Java 对象的属性上，后者表示将@ConfigurationProperties组装好的对象作为bean注入到IOC容器中(这样前者就不用加@Component)

**例子：**

```java
@Configuration //通过该注解来表明该类是一个Spring的配置，相当于一个xml文件
@ComponentScan(basePackages = "com.vison.springboot.javaconfig") //配置扫描包
public class SpringConfig {
    @Bean // 通过该注解来表明是一个Bean对象，相当于xml中的<bean>
    public UserDAO userDAO(){
        return new UserDAO(); // 直接new对象做演示
    }
}

//实例化Spring容器，类似ClassPathXmlApplicationContext
public class Main {
    
    public static void main(String[] args) {
        // 通过Java配置来实例化Spring容器
        AnnotationConfigApplicationContext context = new 	          AnnotationConfigApplicationContext(SpringConfig.class);
        
        // 在Spring容器中获取Bean对象
        UserDAO userDAO = context.getBean(UserDAO.class);
        
        // 销毁该容器
        context.destroy();
    }
 
}
```



# 2. SpringBoot条件注解

   建议看源码，还可以根据源码 自定义这些注解。

- 添加注解 @Conditional ，重新实现Condition接口

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g1auhd9i01j211g0940wm.jpg)

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g1auhtg35fj21gk0s0qfb.jpg)



# 3.Web MVC自动装配

## 3.1 Servlet 3.0 + 自动装配

ServletContainerInitializer（onStartUp方法） --> ServletContextListener(contextInitialized方法)

```java
//1. 当Servlet容器启动的时候，就把ServletContext注入进去
public interface ServletContainerInitializer {
    void onStartup(Set<Class<?>> var1, ServletContext var2) throws ServletException;
}

//2. 当上面的容器启动后，ServletContext被注入，就触发contextInitialized事件。
public interface ServletContextListener extends EventListener {

    public default void contextInitialized(ServletContextEvent sce) {
    }
    public default void contextDestroyed(ServletContextEvent sce) {
    }
}
   
```

## 3.2 Spring Web 自动装配

SpringWeb中用`SpringServletContainerInitializer`实现了ServletContainerInitializer了接口。

- @HanldeTypes表示 选择关心的类以及派生的类进行加载，默认会扫描`WEB-INF/classes`，`WEB-INF/lib`，中的类。


> 那么`@HandlesTypes(WebApplicationInitializer.class) `表示如下：

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g1bvmacqnvj20n70aojro.jpg)

```java
@HandlesTypes(WebApplicationInitializer.class)
public class SpringServletContainerInitializer implements ServletContainerInitializer {

    @Override
    public void onStartup(@Nullable Set<Class<?>> webAppInitializerClasses, ServletContext servletContext)
        throws ServletException {

        List<WebApplicationInitializer> initializers = new LinkedList<>();

        if (webAppInitializerClasses != null) {
            for (Class<?> waiClass : webAppInitializerClasses) {
                // Be defensive: Some servlet containers provide us with invalid classes,
                // no matter what @HandlesTypes says...
                //判定当前加载回来的数据不是接口，不是抽象类，并且实现了WebApplicationInitializer接口，再回执行这里面的数据，然而上面的三个都是抽象类，所以我们可以自己 实现这个类，从而实现自定义的Spring Web MVC装配
                if (!waiClass.isInterface() && !Modifier.isAbstract(waiClass.getModifiers()) &&
                    WebApplicationInitializer.class.isAssignableFrom(waiClass)) {
                    try {
                        initializers.add((WebApplicationInitializer)
                                         ReflectionUtils.accessibleConstructor(waiClass).newInstance());
                    }
                    catch (Throwable ex) {
                        throw new ServletException("Failed to instantiate WebApplicationInitializer class", ex);
                    }
                }
            }
        }

。。。。

}
```



## 3.2  实现Spring Web MVC自动装配

根据上面的解释，需要自己实现接口 WebApplicationInitializer的抽象类

**1.新建一个maven工程，引入依赖**

```xml
 <dependency>
     <groupId>javax.servlet</groupId>
     <artifactId>javax.servlet-api</artifactId>
     <version>3.0.1</version>
     <scope>provided</scope>
</dependency>

<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webmvc</artifactId>
    <version>5.1.3.RELEASE</version>
</dependency>
```

**2.新建controller**

```java
@RestController
public class IndexController {

    @GetMapping("/auto/config/webmvc")
    public String index(){

        return "hello";
    }
}
```

**3.定义配置类做扫描**

```java
@Configuration
@ComponentScan(basePackages = "com.vison") //扫描注解的类
public class SpringWebMvcConfiguration {
}
```

**4.新建启动类继承AbstractAnnotationConfigDispatcherServletInitializer**

```java
public class AutoConfigDispatcherServletInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {
    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class[0];
    }

    //添加ServletConfig配置
    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class[]{SpringWebMvcConfiguration.class};
    }

    //mapping映射
    @Override
    protected String[] getServletMappings() {
        return new String[]{"/*"};
    }
}
```

**5.然后再利用tomcat-plugin插件(通过pom.xml配置)，用jar启动起来**

​      再然后通过localhost:8080就可以访问定义的controller了。达到实现SpringWebMVC的自动装配



# 4. spring-boot 启动方式：

spring-webmvc 和spring-webflux 不能同时使用

## 4.1 配置

> 报错： springboot 打包报错   ...jar包没有主清单属性？**
>
> jar规范中，有一个MANIFEST.INFO,里面有一个Main-Class的属性，
>
> API:`java.util.jar.Manifest#getAttributes`

```xml
<!-- 需要添加这个编译插件  Package as an executable jar -->
//这个需要放在web模块层
	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
                //这里配置main主函数类路径
                <configuration>
                    <mainClass>com.vison.demo.SpringBootDemo</mainClass>
                </configuration>
			</plugin>
		</plugins>
	</build>


```

注意点：当使用的依赖和插件时，如果版本是milestone的时候，需要添加

```xml
//放在父工程的pom.xml中  
<repositories>
        <repository>
            <id>spring-milestone</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/libs-milestone</url>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
    </repositories>


<pluginRepositories>
	<pluginRepository>
		<id>spring-snapshots</id>
		<url>https://repo.spring.io/snapshot</url>
	</pluginRepository>
	<pluginRepository>
		<id>spring-milestones</id>
		<url>https://repo.spring.io/milestone</url>
	</pluginRepository>
</pluginRepositories>
```



## 4.2. 使用jar  和war包启动

### 4.2.1 jar 打包

**1）jar打包后的 解压后 包含文件BOOT-INF,META-INF,org**

`命令： mvn -dMaven.test.skip -U clean packeage`

```xml
BOOT-INF/lib:所有的依赖文件,在springboot 1.4以后才有这个文件
META-INF/MANIFEST-MF:元信息，包含主类和启动类；
 
例如：
	- Main-Class:org.springframework.boot.loader.Jarlauncher 
    - Start-Class:com.vison.demo.SpringBootDemo

```



### 4.2.2 war打包

1> 修改web模块的<packaging>war<packaging>

2> 创建`webapp/WEB-INF`目录 相对于（`src/main`目录）

3>然后再webapp/WEB-INF目录下新建一个web.xml文件

> 注意：步骤2和3是为了绕过war插件的限制

或者2,3步骤使用如下方式：

```xml
<plugin>
    <artifactId>maven-war-plugin</artifactId>
    <configuration>
        <failOnMissingWebXml>false</failOnMissingWebXml>
    </configuration>
</plugin>
```



4> 打包 `命令： mvn -DMaven.test.skip -U clean packeage` 

5> 启动

- 方式一：`命令：java -jar xxx.1.0.0-SNAPSHOT.war`

- 方式二：和上面jar包使用类似，这个需要解压打包文件，进入解压后的根目录执行

  ​	`命令： java org.springframework.boot.loader.Warlauncher`

## 4.3 Maven启动

​    在目录下执行 `命令：mvn spring-boot:run`



# 5.SpringFactoriesLoader装载器

类似于JVM中的加载器，BootstrapCalssLoader、ExeClassLoader、AppClassLoader

> 加载器除了了加载类还可以加载资源；

- `SpringFactoriesLoader`会把当前classpath路径下的`META-INF/spring.factories`全部装载进入IOC容器，所有可以查看下配置文件的类信息，可以发现包含很多重要的类信息(包含EnableAutoconfiguration ,用来开启自动配置的功能)。



```java
public final class SpringFactoriesLoader {

	//资源文件
	public static final String FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories";


	private static final Log logger = LogFactory.getLog(SpringFactoriesLoader.class);

	private static final Map<ClassLoader, MultiValueMap<String, String>> cache = new ConcurrentReferenceHashMap<>();


	private SpringFactoriesLoader() {
	}


	
	public static <T> List<T> loadFactories(Class<T> factoryClass, @Nullable ClassLoader classLoader) {
		Assert.notNull(factoryClass, "'factoryClass' must not be null");
		ClassLoader classLoaderToUse = classLoader;
		if (classLoaderToUse == null) {
			classLoaderToUse = SpringFactoriesLoader.class.getClassLoader();
		}
		List<String> factoryNames = loadFactoryNames(factoryClass, classLoaderToUse);
		if (logger.isTraceEnabled()) {
			logger.trace("Loaded [" + factoryClass.getName() + "] names: " + factoryNames);
		}
		List<T> result = new ArrayList<>(factoryNames.size());
		for (String factoryName : factoryNames) {
			result.add(instantiateFactory(factoryName, factoryClass, classLoaderToUse));
		}
		AnnotationAwareOrderComparator.sort(result);
		return result;
	}

	
	public static List<String> loadFactoryNames(Class<?> factoryClass, @Nullable ClassLoader classLoader) {
		String factoryClassName = factoryClass.getName();
		return loadSpringFactories(classLoader).getOrDefault(factoryClassName, Collections.emptyList());
	}

	private static Map<String, List<String>> loadSpringFactories(@Nullable ClassLoader classLoader) {
		MultiValueMap<String, String> result = cache.get(classLoader);
		if (result != null) {
			return result;
		}

		try {
            //这里会把当前classpath路径下的META-INF/spring.factories全部装载进入IOC容器
			Enumeration<URL> urls = (classLoader != null ?
					classLoader.getResources(FACTORIES_RESOURCE_LOCATION) :
					ClassLoader.getSystemResources(FACTORIES_RESOURCE_LOCATION));
			result = new LinkedMultiValueMap<>();
			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				UrlResource resource = new UrlResource(url);
				Properties properties = PropertiesLoaderUtils.loadProperties(resource);
				for (Map.Entry<?, ?> entry : properties.entrySet()) {
					String factoryClassName = ((String) entry.getKey()).trim();
					for (String factoryName : StringUtils.commaDelimitedListToStringArray((String) entry.getValue())) {
						result.add(factoryClassName, factoryName.trim());
					}
				}
			}
			cache.put(classLoader, result);
			return result;
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Unable to load factories from location [" +
					FACTORIES_RESOURCE_LOCATION + "]", ex);
		}
	}
	...
}
```



#  6.Spring容器的事件监听

   可以参考Spring-cloud.client中的笔记

怎么为Spring-Boot添加监听器：

- 通过SpringApplication.addListeners或者SpringApplication.setListeners添加自定义自定义监听器

- 由于SpringApplication初始化会在spring.factories中获取ApplicationListener实现类 ，那么我们可以在在自己的META-INF/spring.factories中新增配置

  ```properties
  org.springframework.context.ApplicationListener = com.vison.ws.listener.xxxListener
  ```

# 7.SpringBoot自动装配原理

查看入口启动类

```java

@SpringBootApplication   
public class EffectiveJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EffectiveJavaApplication.class, args);
    }

}
```

## 7.1`@SpringBootApplication`

```
@SpringBootConfiguration   //表示是@Configuration的配置
@EnableAutoConfiguration   //主要是@Import(AutoConfigurationImportSelector.class)
@ComponentScan				//启动扫描组件
```

`AutoConfigurationImportSelector`会将所有的@Configuration配置都加载到容器中

```java
//如下 ，这个类会使用SpringFactoriesLoader得到大量的配置类,这里会具使用
//selectImports方法的调用会在AbstraceApplicationContext.refresh方法中
@Override
public String[] selectImports(AnnotationMetadata annotationMetadata) {
    if (!isEnabled(annotationMetadata)) {
        return NO_IMPORTS;
    }
    AutoConfigurationMetadata autoConfigurationMetadata = AutoConfigurationMetadataLoader
        .loadMetadata(this.beanClassLoader);
    AutoConfigurationEntry autoConfigurationEntry = getAutoConfigurationEntry(
        autoConfigurationMetadata, annotationMetadata);
    return StringUtils.toStringArray(autoConfigurationEntry.getConfigurations());
}
```



## 7.2 SpringApplication的初始化

总共分为两个步骤：

- 1.初始化一个`SpringApplication`对象
- 2.执行run方法

### 1）初始化SpringApplication对象

​      初始化对象的时候主要是找到`spring.factories`文件中配置的`ApplicationContextInitializer`和`ApplicationListener`两个接口的实现类名称，以便后期构造相应的实例。

- `ApplicationContextInitializer`在`configurableApplicationContext`做`refresh`之前对`configurableApplicationContext`（提供了对ApplicationContext的设置能力）做进一步的设置和处理。


### 2) SpringBoot的启动流程 run方法

​	流程很长，但是本质就是在Spring容器启动的基础上做了了大量的扩展

1.通过`SpringFactoriesLoader`查找并加载所有的`SpringApplicationRunListeners`:应用开始启动

2.创建并配置当前应用将要使用的Environment（包含当前程序需要运行的环境，抽象了两个方面的内容：配置文件（profile） 、属性（properties））

3.启动输出Banner涂鸦

4.根据是否是Web项目，来创建不同的`ApplicationContext`容器

5.创建一系列的`FailureAnalyzer`

6.初始化`ApplicationContext`

- 将准备好的Environment设置给ApplicationContext
- 遍历调用所有的`ApplicationContextInitializer`的initialize方法对创建好的ApplicationContext进一步处理
- 调用`SpringApplicationRunListener`的contextPrepared方法通知所有的监听者，ApplicationContext已经准备完毕
- 将所有的bean加载进容器中
- 调用`SpringApplicationRunListener`的contextLoaded方法，通知所有的监听者，ApplicationContext已经装载完毕 

7.调用`ApplicationContext`的refresh方法，完成IOC容器的最后一道工序

- 获取到所有的BeanFactoryPostProcessor来对容器做一些额外的操作.（允许对注册到容器的BeanDefinition 做额外的操作）
  - 会有三个` BeanFactoryPostProcessor`实现类
  - 这里包含调用类型为`BeanDefinitionRegistryPostProcessor`的:` ConfigurationClassPostProcessor` :这个用来处理`@Configuartion。@ComponentScan，@Import,@PropertySource,@ImportReSource,@bean`

8.查找当前context中是否有`commandLineRunner`和`ApplicationRunner`,如果有，遍历执行

9.执行`SpringAppllicationRunlistener`的finnished方法