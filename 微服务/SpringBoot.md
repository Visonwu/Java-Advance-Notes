

spring-webmvc 和spring-webflux 不能同时使用

# 1. spring-boot 启动方式：

## 1.1 配置

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



## 1.2. 使用jar  和war包启动



### 1.2.1 jar 打包

**1）jar打包后的 解压后 包含文件BOOT-INF,META-INF,org**

`命令： mvn -dMaven.test.skip -U clean packeage`

```xml
BOOT-INF/lib:所有的依赖文件,在springboot 1.4以后才有这个文件
META-INF/MANIFEST-MF:元信息，包含主类和启动类；
 
例如：
	- Main-Class:org.springframework.boot.loader.Jarlauncher 
    - Start-Class:com.vison.demo.SpringBootDemo

```



### 1.2.2 war打包

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



4> 打包

`命令： mvn -dMaven.test.skip -U clean packeage` 

5> 启动

- 方式一：

​		 `命令：java -jar xxx.1.0.0-SNAPSHOT.war`

- 方式二：

    和上面jar包使用类似，这个需要解压打包文件，进入解压后的根目录执行

  ​	`命令： java org.springframework.boot.loader.Warlauncher`



## 1.3 Maven启动

在目录下执行 `命令：mvn spring-boot:run`

