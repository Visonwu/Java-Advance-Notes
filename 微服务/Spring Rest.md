==如下实例都是依赖于SpringBoot来写扩展的，其他SpringMVC是要用不同的接口来实现==。


[TOC]

# 1.Rest基础概念


GET,PUT,DELETE具有幂等性，POST没有幂等性。服务器解决幂等性问题

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g0r1rnpr3oj20vg0eh0ul.jpg)

参考网址：https://en.wikipedia.org/wiki/Representational_state_transfer


- 客户端请求头中的accept：表示客户端能够接受的媒体类型，一般浏览器text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8。
    - q表示权重
- 请求头中的content-type：表示客户端实体的媒体类型

可以用postman改变请求头的内容 


# 2.Rest On Spring Web MVC

## 2.1服务端核心接口
- 定义相关
    - @Controller
    - @RestController
- 映射相关
    - @RequestMapping
    - @GetMapping , @PostMapping...
    - @PathVariable
- 方法相关
    - HttpMethod

## 2.2 案例
```java
@RestController
public class PersonController {
    
    @GetMapping("/person/{id}")
    public Person person(@PathVariable Long id,
                         @RequestParam(required = false) String name){ 
                         //required = false表示非必须
        Person person = new Person();
        person.setId(id);
        person.setName(name);
        return person;
    }
}

//通过访问http://localhost:8080/person/1?name=vison 可以得到结果：{"id":1,"name":"vison"}

```

```xml
<!--然而在依赖包中添加了如下--> 
 <dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-xml</artifactId>
    <!--<version>2.0.0-RC2</version>-->
</dependency>

//返回值就变成了
<Person>
    <id>1</id>
    <name>vison</name>
</Person>
    
```
```java
//理由就是源码中的WebMvcConfiguration中的有判断下面的类是否存在,可以在在网站 https://search.maven.org 根据类查询maven仓库地址

static {
    ClassLoader classLoader = WebMvcConfigurationSupport.class.getClassLoader();
    romePresent = ClassUtils.isPresent("com.rometools.rome.feed.WireFeed", classLoader);
    jaxb2Present = ClassUtils.isPresent("javax.xml.bind.Binder", classLoader);
    jackson2Present = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader) &&
        ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);


    jackson2XmlPresent = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.xml.XmlMapper", classLoader);


    jackson2SmilePresent = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.smile.SmileFactory", classLoader);
    jackson2CborPresent = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.cbor.CBORFactory", classLoader);
    gsonPresent = ClassUtils.isPresent("com.google.gson.Gson", classLoader);
    jsonbPresent = ClassUtils.isPresent("javax.json.bind.Jsonb", classLoader);
}
    
```
<br/>

- 总结：application/json,SpringBoot默认使用Jackson2序列化方式，其中媒体类型为application/json,处理类是MappingJackson2HttpMessageConverter,这个类提供两类方法：

    - 读read*：通过Http请求内容转换为对应的Bean
    - 写write*：通过Bean序列化成对应文本内容作为响应内容

- 为什么默认返回JSON文件，后来加了XML的类包，就变成了XML返回内容呢？

    - 回答： SpringBoot应用默认没有XML处理器实现，最后采用轮训的方式逐一尝试canWrite(POJO)，如果返回true,说明可以序列化该对象，那么Jackson2恰好能处理。

- 当Accept没有指定时，返回结果为什么还是JSON呢？
    -回答：这个依赖于messageConverters的插入顺序，JSON默认顺序靠前。而且没有指定Accept，会用默认的messageConverters。 




## 2.3 源码分析

查找路径：
@EnableWebMVC -> DelegatingWebMvcConfiguration -> WebMvcConfigurationSupport  -> MappingJackson2HttpMessageConverter ->AbstractJackson2HttpMessageConverter  ->RequestResponseBodyMethodProcessor ->AbstractMessageConverterMethodProcessor

参考：AbstractMessageConverterMethodProcessor类中下面的这个方法

```java
protected <T> void writeWithMessageConverters(@Nullable T value, MethodParameter returnType,
			ServletServerHttpRequest inputMessage, ServletServerHttpResponse outputMessage)
			throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {

		Object body;
		Class<?> valueType;
		Type targetType;

		if (value instanceof CharSequence) {
			body = value.toString();
			valueType = String.class;
			targetType = String.class;
		}
		else {
			body = value;
			valueType = getReturnValueType(body, returnType);
			targetType = GenericTypeResolver.resolveType(getGenericType(returnType), returnType.getContainingClass());
		}

		if (isResourceType(value, returnType)) {
			outputMessage.getHeaders().set(HttpHeaders.ACCEPT_RANGES, "bytes");
			if (value != null && inputMessage.getHeaders().getFirst(HttpHeaders.RANGE) != null &&
					outputMessage.getServletResponse().getStatus() == 200) {
				Resource resource = (Resource) value;
				try {
					List<HttpRange> httpRanges = inputMessage.getHeaders().getRange();
					outputMessage.getServletResponse().setStatus(HttpStatus.PARTIAL_CONTENT.value());
					body = HttpRange.toResourceRegions(httpRanges, resource);
					valueType = body.getClass();
					targetType = RESOURCE_REGION_LIST_TYPE;
				}
				catch (IllegalArgumentException ex) {
					outputMessage.getHeaders().set(HttpHeaders.CONTENT_RANGE, "bytes */" + resource.contentLength());
					outputMessage.getServletResponse().setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
				}
			}
		}

		MediaType selectedMediaType = null;
		MediaType contentType = outputMessage.getHeaders().getContentType();
		if (contentType != null && contentType.isConcrete()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Found 'Content-Type:" + contentType + "' in response");
			}
			selectedMediaType = contentType;
		}
		else {
			HttpServletRequest request = inputMessage.getServletRequest();
			List<MediaType> acceptableTypes = getAcceptableMediaTypes(request);
			List<MediaType> producibleTypes = getProducibleMediaTypes(request, valueType, targetType);

			if (body != null && producibleTypes.isEmpty()) {
				throw new HttpMessageNotWritableException(
						"No converter found for return value of type: " + valueType);
			}
			List<MediaType> mediaTypesToUse = new ArrayList<>();
			for (MediaType requestedType : acceptableTypes) {
				for (MediaType producibleType : producibleTypes) {
					if (requestedType.isCompatibleWith(producibleType)) {
						mediaTypesToUse.add(getMostSpecificMediaType(requestedType, producibleType));
					}
				}
			}
			if (mediaTypesToUse.isEmpty()) {
				if (body != null) {
					throw new HttpMediaTypeNotAcceptableException(producibleTypes);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("No match for " + acceptableTypes + ", supported: " + producibleTypes);
				}
				return;
			}

			MediaType.sortBySpecificityAndQuality(mediaTypesToUse);

			for (MediaType mediaType : mediaTypesToUse) {
				if (mediaType.isConcrete()) {
					selectedMediaType = mediaType;
					break;
				}
				else if (mediaType.equals(MediaType.ALL) || mediaType.equals(MEDIA_TYPE_APPLICATION)) {
					selectedMediaType = MediaType.APPLICATION_OCTET_STREAM;
					break;
				}
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Using '" + selectedMediaType + "', given " +
						acceptableTypes + " and supported " + producibleTypes);
			}
		}

		if (selectedMediaType != null) {
			selectedMediaType = selectedMediaType.removeQualityValue();
			for (HttpMessageConverter<?> converter : this.messageConverters) {
				GenericHttpMessageConverter genericConverter = (converter instanceof GenericHttpMessageConverter ?
						(GenericHttpMessageConverter<?>) converter : null);
				if (genericConverter != null ?
						((GenericHttpMessageConverter) converter).canWrite(targetType, valueType, selectedMediaType) :
						converter.canWrite(valueType, selectedMediaType)) {
					body = getAdvice().beforeBodyWrite(body, returnType, selectedMediaType,
							(Class<? extends HttpMessageConverter<?>>) converter.getClass(),
							inputMessage, outputMessage);
					if (body != null) {
						Object theBody = body;
						LogFormatUtils.traceDebug(logger, traceOn ->
								"Writing [" + LogFormatUtils.formatValue(theBody, traceOn) + "]");
						addContentDispositionHeader(inputMessage, outputMessage);
						if (genericConverter != null) {
							genericConverter.write(body, targetType, selectedMediaType, outputMessage);
						}
						else {
							((HttpMessageConverter) converter).write(body, selectedMediaType, outputMessage);
						}
					}
					else {
						if (logger.isDebugEnabled()) {
							logger.debug("Nothing to write: null body");
						}
					}
					return;
				}
			}
		}

		if (body != null) {
			throw new HttpMediaTypeNotAcceptableException(this.allSupportedMediaTypes);
		}
	}

```

## 2.4 定制化消息转换器

下面两个定制化消息转换器公用代码做调试
```java
@RestController
public class PersonController {

    @GetMapping("/person/{id}")
    public Person person(@PathVariable Long id,
                         @RequestParam(required = false) String name){  //required = false表示非必须
        Person person = new Person();
        person.setId(id);
        person.setName(name);
        return person;
    }
}
```

### 2.4.1 实现WebMvcConfigurer接口

```java
package com.vison.restful.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.set(0,new MappingJackson2HttpMessageConverter()); //可以设置消息转换的前后顺序
      // converters.add(new MappingJackson2HttpMessageConverter()); //增加消息转换器。具体参考WebMvcConfigurer接口，也可以实现下面这个类
      //可以查看AbstractMessageConverterMethodProcessor怎么调用这些消息转换器的
    }
}
```

### 2.4.2 继承 WebMvcConfigurationSupport 重写模板方法

```java
package com.vison.restful.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebMvcConfig extends WebMvcConfigurationSupport {

    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
      converters.add(new MappingJackson2HttpMessageConverter());  //可以查看AbstractMessageConverterMethodProcessor怎么调用这些消息转换器的
    }

    //
    //    protected void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
    //    }

    /** 利用的模板模式，参考父类的这个方法WebMvcConfigurationSupport
        protected final List<HttpMessageConverter<?>> getMessageConverters() {
            if (this.messageConverters == null) {
                this.messageConverters = new ArrayList<>();
                configureMessageConverters(this.messageConverters);
                if (this.messageConverters.isEmpty()) {
                    addDefaultHttpMessageConverters(this.messageConverters);
                }
                extendMessageConverters(this.messageConverters);
            }
            return this.messageConverters;
        }
     */
}

```

## 2.5 扩展自描述消息 实现JSON和Properties相互转化

扩展自描述消息：application/properties+person

步骤一：继承AbstractHttpMessageConverter编写自定义转换器

```java
package com.vison.restful.http.message;

import com.vison.restful.domain.Person;
import jdk.internal.org.objectweb.asm.tree.LocalVariableAnnotationNode;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Properties;

public class PropertiesPersonHttpMessageConverter extends AbstractHttpMessageConverter<Person> {

    public PropertiesPersonHttpMessageConverter(){
        super(MediaType.valueOf("application/properties+person"));
        setDefaultCharset(Charset.forName("UTF-8"));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return clazz.isAssignableFrom(Person.class);
    }

    /**
     * 将请求中的内容Properties转换为Person对象
     * @param clazz
     * @param httpInputMessage
     * @return
     * @throws IOException
     * @throws HttpMessageNotReadableException
     */
    @Override
    protected Person readInternal(Class<? extends Person> clazz, HttpInputMessage httpInputMessage) throws IOException, HttpMessageNotReadableException {

        InputStream body = httpInputMessage.getBody();
        //将强求中的内容转换为Properties
        Properties properties = new Properties();
        properties.load(new InputStreamReader(body,getDefaultCharset()));

        //将properties内容转换为Person字段中
        Person person = new Person();
        person.setName(properties.getProperty("person.name"));
        person.setId(Long.valueOf(properties.getProperty("person.id")));
        return person;
    }

    /**
     * 将内容写出去
     * @param person
     * @param httpOutputMessage
     * @throws IOException
     * @throws HttpMessageNotWritableException
     */
    @Override
    protected void writeInternal(Person person, HttpOutputMessage httpOutputMessage) throws IOException, HttpMessageNotWritableException {

        OutputStream body = httpOutputMessage.getBody();
        //将强求中的内容转换为Properties
        Properties properties = new Properties();
        properties.setProperty("person.id",String.valueOf(person.getId()));
        properties.setProperty("person.name",person.getName());

        properties.store(new OutputStreamWriter(body,getDefaultCharset()),"Written by web server");
    }
}

```

步骤二：把新增的消息转换器添加到配置中去

```java
package com.vison.restful.config;

import com.vison.restful.http.message.PropertiesPersonHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
      converters.add(new PropertiesPersonHttpMessageConverter());
    }
}


```

步骤三：编写控制器，控制请求和返回的类型

```java
package com.vison.restful.controller;

import com.vison.restful.domain.Person;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
public class PersonController {

    @PostMapping(value = "/person/json/to/properties",
                 consumes = MediaType.APPLICATION_JSON_UTF8_VALUE,   //对应请求类型Content-type
                 produces = "application/properties+person"          //响应类型对应Accept
    )
    public Person personJsonToPerson(@RequestBody Person person){ //required = false表示非必须
         return person;
    }

    @PostMapping(value = "/person/properties/to/json",
            consumes = "application/properties+person" ,            //请求类型
            produces =MediaType.APPLICATION_JSON_UTF8_VALUE         //响应类型
    )
    public Person personPersonToJson(@RequestBody Person person){ //required = false表示非必须
        return person;
    }
}
```

//注意点，上面限制了请求类型和响应类型。如果请求地址对了，但是媒体类型不匹配就会出现媒体类型不匹配415错误。

这里可以使用postman模拟修改请求类型和能够支持的类型

```
访问"/person/json/to/properties"  

需要设置请求头：
        Accept=application/json;charset=UTF-8     //Accept这是客户端能够支持的类型
        Content-Type=application/properties+person  //Content-Type这是请求体的类型
请求体：
    {"id":1,"name":"vison"}
------------------------------------    
    
访问"/person/properties/to/json"  

需要设置请求头：
        Accept=application/properties+person
        Content-Type=application/json;charset=UTF-8
请求体：
    person.id=1
    person.name=vison
```

# 3.Spring RestTemplate

**1) HTTP消息装换器：HttpMessageConvertor**

- 自定义实现

- 编码问题

- 切换序列化/反序列化协议

**2) HTTP Client 适配工厂：ClientHttpRequestFactory**

这个方面主要考虑大家的使用 HttpClient 偏好：

- Spring 实现
  - SimpleClientHttpRequestFactory
- HttpClient


​	- HttpComponentsClientHttpRequestFactory

- OkHttp

  - OkHttp3ClientHttpRequestFactory

  - OkHttpClientHttpRequestFactory

举例说明：

```java
//切换HTTP 通讯实现，提升性能s
RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory()); // HTTP Client
```

**3) HTTP 请求拦截器：ClientHttpRequestInterceptor**

加深RestTemplate 拦截过程的

