# 1.Jdk中提供的SPI

​			了解Dubbo里面的SPI机制之前，我们先了解下Java提供的SPI（service provider interface）机制，SPI是JDK内置的一种服务提供发现机制。目前市面上有很多框架都是用它来做服务的扩展发现。简单来说，它是一种动态替换发现的机制。举个简单的例子，我们想在运行时动态给它添加实现，你只需要添加一个实现，然后把新的实现描述给JDK知道就行了。大家耳熟能详的如JDBC、日志框架都有用到。

## 1.1 实现SPI需要遵循的标准
我们如何去实现一个标准的SPI发现机制呢？其实很简单，只需要满足以下提交就行了

- 需要在classpath下创建一个目录，该目录命名必须是：META-INF/service
- 在该目录下创建一个properties文件，该文件需要满足以下几个条件
  - 文件名必须是扩展的接口的全路径名称
  - 文件内部描述的是该扩展接口的所有实现类
  -  文件的编码格式是UTF-8
  - 通过java.util.ServiceLoader 的加载机制来发现

例如：

​	我们在连接数据库的时候，一定需要用到java.sql.Driver 这个接口对吧。然后我好奇的去看了下java.sql.Driver 的源码，发现Driver 并没有实现，而是提供了一套标准的api 接口

因为我们在实际应用中用的比较多的是mysql，所以我去mysql 的包里面看到一个如下的目录结构

`META-INF/services/java.sql.Driver`

## 1.2 SPI 的缺点

- JDK 标准的SPI 会一次性加载实例化扩展点的所有实现，什么意思呢？就是如果你在META-INF/service 下的文件里面加了N个实现类，那么JDK 启动的时候都会一次性全部加载。那么如果有的扩展点实现初始化很耗时或者如果有些实现类并没有用到，那么会很浪费资源
- 如果扩展点加载失败，会导致调用方报错，而且这个错误很难定位到是这个原因



# 2.Dubbo优化后的SPI机制

**Dubbo 的SPI 扩展机制，有两个规则**

- 需要在resource 目录下配置META-INF/dubbo 或者META-INF/dubbo/internal 或者META-INF/services，并基于SPI 接口去创建一个文件
- 文件名称和接口名称保持一致，文件内容和SPI 有差异，内容是KEY 对应Value；Dubbo 针对的扩展点非常多，可以针对协议、拦截、集群、路由、负载均衡、序列化、容器… 几乎里面用到的所有功能，都可以实现自己的扩展，我觉得这个是dubbo 比较强大的一点。



例如：

**自定义协议扩展类**

```java
package com.vison.extension;
public class MyProtocol implements Protocol {
    @Override
    public int getDefaultPort() {
        return 3000;
    }
    ...
}
```

在当前资源路径下新建`/META-INF/dubbo/org.apache.dubbo.rpc.Protocol/`

内容：

```properties
myProtocol=com.vison.extension.MyProtocol
```

**测试**

```java
public class TestExtension {

    public static void main(String[] args) {
        Protocol myProtocol = ExtensionLoader.getExtensionLoader(Protocol.class)
                .getExtension("myProtocol"); //通过指定名称获取

        int defaultPort = myProtocol.getDefaultPort();
        System.out.println(defaultPort); //结果为 3000
    }
}
```

这里使用的是duboo提供的一些SPI接口，具体同样是加载配置文件的数据进行返回，部分源码如下：

```java
private T createExtension(String name) {
        Class<?> clazz = getExtensionClasses().get(name);  //这里就是从配置文件中加载类
        if (clazz == null) {
            throw findException(name);
        }
        try {
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            //给配置扩展点的数据进行set注入
            injectExtension(instance);
            //这里是给wrapper缓冲进行set注入
            Set<Class<?>> wrapperClasses = cachedWrapperClasses;
            if (CollectionUtils.isNotEmpty(wrapperClasses)) {
                for (Class<?> wrapperClass : wrapperClasses) {
                    instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));
                }
            }
            return instance;
。。。。
    }

//EXtensionLoader类中的
private Map<String, Class<?>> loadExtensionClasses() {
    cacheDefaultExtensionName();

    Map<String, Class<?>> extensionClasses = new HashMap<>();
    //这里会从/META-INF/dubbo,/META-INF/dubbo/internal,/META-INF/dubbo/internal,"META-INF/services/目录中加载资源
    loadDirectory(extensionClasses, DUBBO_INTERNAL_DIRECTORY, type.getName());
    loadDirectory(extensionClasses, DUBBO_INTERNAL_DIRECTORY, type.getName().replace("org.apache", "com.alibaba"));
    loadDirectory(extensionClasses, DUBBO_DIRECTORY, type.getName());
    loadDirectory(extensionClasses, DUBBO_DIRECTORY, type.getName().replace("org.apache", "com.alibaba"));
    loadDirectory(extensionClasses, SERVICES_DIRECTORY, type.getName());
    loadDirectory(extensionClasses, SERVICES_DIRECTORY, type.getName().replace("org.apache", "com.alibaba"));
    
    return extensionClasses;
}
```



## 2.1 @SPI

​		dubbo提供了@SPI注解表示当前接口是一个可扩展点，所以我们可以通过@SPI注解自定义可扩展接口

例如dubbo自己提供的协议，里面的值表示使用默认的协议实现dubbo.

参考：<https://www.jianshu.com/p/dc616814ce98>

```java
@SPI("dubbo")
public interface Protocol {
    ...
}
```



## 2.2 @Adaptive自适应扩展点

​	什么叫自适应扩展点呢？我们先演示一个例子，在下面这个例子中，我们传入一个Compiler 接口，它会返回一个`AdaptiveCompiler`。这个就叫自适应。

```java
Compiler compiler=ExtensionLoader.getExtensionLoader(Compiler.class)
    .getAdaptiveExtension();
System.out.println(compiler.getClass());
```



​		它是怎么实现的呢？ 我们根据返回的AdaptiveCompiler 这个类，看到这个类上面有一个注解`@Adaptive`;这个就是一个自适应扩展点的标识。它可以修饰在类上，也可以修饰在方法上面。这两者有什么区别呢？

- **放在类上**，说明当前类是一个确定的自适应扩展点的类。

- **放在方法级别**，那么需要生成一个动态字节码，来进行转发。



比如拿Protocol 这个接口来说，它里面定义了export 和refer 两个抽象方法，这两个方法分别带有@Adaptive 的标识，标识是一个自适应方法。

​		我们知道Protocol 是一个通信协议的接口，具体有多种实现，那么这个时候选择哪一种呢？ 取决于我们在使用dubbo 的时候所配置的协议名称。而这里的方法层面的Adaptive 就决定了当前这个方法会采用何种协议来发布服务。

通过源码来解析：

```java
public T getAdaptiveExtension() {
    //获取了自适应实例会缓存起来
    Object instance = cachedAdaptiveInstance.get();
    if (instance == null) {
        if (createAdaptiveInstanceError == null) {
            synchronized (cachedAdaptiveInstance) {
                instance = cachedAdaptiveInstance.get();
                if (instance == null) {
                    try {
                        //这里创建一个自适应实例
                        instance = createAdaptiveExtension();
                        cachedAdaptiveInstance.set(instance);
                    } catch (Throwable t) {
                      。。。
                }
            }
        } else {
          。。。
        }
    }

    return (T) instance;
}
    
    
@SuppressWarnings("unchecked")
private T createAdaptiveExtension() {
    try {
        //创建自适应实例，并做set注入
        return injectExtension((T) getAdaptiveExtensionClass().newInstance());
    } catch (Exception e) {
        throw new IllegalStateException("Can't create adaptive extension " + type + ", cause: " + e.getMessage(), e);
    }
}
//获取自适应类文件
private Class<?> getAdaptiveExtensionClass() {
    //这里会加载配置文件的所有扩展类
    getExtensionClasses();
    if (cachedAdaptiveClass != null) {
        //这里表示自适应当前类是一个Adaptive类，之前如果有就加载了这里直接返回
        return cachedAdaptiveClass;
    }
    //只有方法级别的才会返回这个，通过动态字节码来实现生成的类名：扩展点类名$Adaptive
    return cachedAdaptiveClass = createAdaptiveExtensionClass();
}

private Class<?> createAdaptiveExtensionClass() {
    	//这个code就是通过字符串拼接成的动态代理类（如果这里使用的是Procotol,因为procotol的refer，export方法都是@Adaptive的） Protocol$Adaptive
        String code = new AdaptiveClassCodeGenerator(type, cachedDefaultName).generate();
        ClassLoader classLoader = findClassLoader();
        org.apache.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        return compiler.compile(code, classLoader);
}       
```

```java
package org.apache.dubbo.rpc;
import org.apache.dubbo.common.extension.ExtensionLoader;
public class Protocol$Adaptive implements org.apache.dubbo.rpc.Protocol {
    public void destroy()  {
        throw new UnsupportedOperationException("The method public abstract void org.apache.dubbo.rpc.Protocol.destroy() of interface org.apache.dubbo.rpc.Protocol is not adaptive method!");
    }
    public int getDefaultPort()  {
        throw new UnsupportedOperationException("The method public abstract int org.apache.dubbo.rpc.Protocol.getDefaultPort() of interface org.apache.dubbo.rpc.Protocol is not adaptive method!");
    }
    public org.apache.dubbo.rpc.Invoker refer(java.lang.Class arg0, org.apache.dubbo.common.URL arg1) throws org.apache.dubbo.rpc.RpcException {
        if (arg1 == null) throw new IllegalArgumentException("url == null");
        org.apache.dubbo.common.URL url = arg1;
        
        //这里可以发现如果我们没有配置相关协议，那么默认还是获取的DubboProcotol作为返回（这里的dubbo是@SPI中的值（Protocol）） 
        String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() );
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.Protocol) name from url (" + url.toString() + ") use keys([protocol])");
        org.apache.dubbo.rpc.Protocol extension = (org.apache.dubbo.rpc.Protocol)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension(extName);
        return extension.refer(arg0, arg1);
    }
    public org.apache.dubbo.rpc.Exporter export(org.apache.dubbo.rpc.Invoker arg0) throws org.apache.dubbo.rpc.RpcException {
        if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument == null");
        if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument getUrl() == null");
        org.apache.dubbo.common.URL url = arg0.getUrl();
        
        //这里可以发现如果我们没有配置相关协议，那么默认还是获取的DubboProcotol作为返回
        String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() );
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.Protocol) name from url (" + url.toString() + ") use keys([protocol])");
        org.apache.dubbo.rpc.Protocol extension = (org.apache.dubbo.rpc.Protocol)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension(extName);
        return extension.export(arg0);
    }
}
```

### 1）再次详解set注入方法injectExtension

​	这个是给扩展点做set注入的方法

```java
private T injectExtension(T instance) {
        try {
            //这里的注入加了一个判断是否有objectFactory，否则就不注入
            if (objectFactory != null) {
                for (Method method : instance.getClass().getMethods()) {
                    if (isSetter(method)) {
                     ...
                        Class<?> pt = method.getParameterTypes()[0];
                        if (ReflectUtils.isPrimitives(pt)) {
                            continue;
                        }
                        try {
                            String property = getSetterProperty(method);
                            //这里还通过objectFactory根据参数类型和参数名去获取实例
                            //这里其实是通过spring的IOC容器和dubbo的扩展点
                            //然后注入
                            Object object = objectFactory.getExtension(pt, property);
                            if (object != null) {
                                method.invoke(instance, object);
                            }
           ....
        return instance;
    }
```

**详解步骤一：**

​		我们最开始会创建一个ExtensionLoader对象，这里type如果不是null，当前objectFactory会是`ExtensionFactory`的一个扩展点类即这里objectFacotory获取的是：`AdaptiveExtensionFactory`

```java
 private ExtensionLoader(Class<?> type) {
        this.type = type;
        objectFactory = (type == ExtensionFactory.class ? null : ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension());
}
```

**详解步骤二：**

​		如上最开始的地方`objectFactory.getExtension(pt, property)`

```java
@Adaptive
public class AdaptiveExtensionFactory implements ExtensionFactory {

    private final List<ExtensionFactory> factories;

    public AdaptiveExtensionFactory() {
        ExtensionLoader<ExtensionFactory> loader = ExtensionLoader.getExtensionLoader(ExtensionFactory.class);
        List<ExtensionFactory> list = new ArrayList<ExtensionFactory>();
        for (String name : loader.getSupportedExtensions()) {
            list.add(loader.getExtension(name));
        }
        factories = Collections.unmodifiableList(list);
    }

    //这里ExtensionFactory还有两个实现类SPIExtensionFactory和SpringExtensionFactory，轮训这两个类，根据type和name获取到了实例类就立刻返回
    @Override
    public <T> T getExtension(Class<T> type, String name) {
        for (ExtensionFactory factory : factories) {
            T extension = factory.getExtension(type, name);
            if (extension != null) {
                return extension;
            }
        }
        return null;
    }
}
```



## 2.3 @Activate 自动激活扩展点

​	自动激活扩展点，有点类似我们讲springboot 的时候用到的`@conditional`，根据条件进行自动激活。但是这里设计的初衷是，对于一个类会加载多个扩展点的实现，这个时候可以通过自动激活扩展点进行动态加载， 从而简化配置我们的配置工作@Activate 提供了一些配置来允许我们配置加载条件，比如group 过滤，比如key 过滤。

参考：<https://www.jianshu.com/p/bc523348f519>

### 1) 调用方法

```java
 public static void main(String[] args) {

     ExtensionLoader<Filter> loader=ExtensionLoader.getExtensionLoader(Filter.class);
     URL url=new URL("","",0);
     //url=url.addParameter("cache","cache"); //有和没有的区别
     List<Filter> filters=loader.getActivateExtension(url,new String[]{});
     System.out.println(filters.size()); //数量不同
 }
```

​		如上，我们可以看看org.apache.dubbo.Filter 这个类，它有非常多的实现，比如说CacheFilter，这个缓存过滤器，配置信息如下

```java
//group 表示客户端和和服务端都会加载，value 表示url 中有cache_key 的时候，当然还要order可以根据group获取后进行排序
@Activate(group = {CONSUMER, PROVIDER}, value = CACHE_KEY)
public class CacheFilter implements Filter {
	...
}
```

​		通过上面测试代码，演示关于Filter 的自动激活扩展点的效果。没有添加“url参数”时，list 的结果是10，添加之后list的结果是11. 会自动把cacheFilter 加载进来。



### 2) 结论

- 1.根据loader.getActivateExtension中的`group`和搜索到此类型的实例进行比较，如果`group`能匹配到，就是我们选择的，也就是在此条件下需要激活的。如果我们没有提供group值会把所有@active注解的都加载进来

- 2.@Activate中的value是参数是第二层过滤参数（第一层是通过group），在group校验通过的前提下，如果URL中的参数（k）与值（v）中的参数名同@Activate中的value值一致或者包含，那么才会被选中。相当于加入了value后，条件更为苛刻点，需要URL中有此参数并且，参数必须有值。

- 3.@Activate的order参数对于同一个类型的多个扩展来说，order值越小，优先级越高



### 3) 应用场景

​	 主要用在filter上，有的filter需要在provider边需要加的，有的需要在consumer边需要加的，根据URL中的参数指定，当前的环境是provider还是consumer，运行时决定哪些filter需要被引入执行。