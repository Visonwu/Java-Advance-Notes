# 1.xml解析

步骤：

1、初始化的入口在容器实现中的 refresh()调用来完成。
2、对 Bean 定义载入 IOC 容器使用BeanDefinitionReader的方法loadBeanDefinition()

3、使用DefaultBeanDefinitionDocumentReader对xml中定义的bean进行解析

- 这里包含委派BeanDefinitionParserDelegate对Element的解析并抽象为BeanDefinition

- 并且把对BeanDefinition注册到DefaultListableFactoryBean中

- 最后发送注册完成的事件通知

  

##   1.1 默认 xml解析

​		主要解析 import,bean,alias标签

- 1）解析这些标签后封装到GenericBeanDefinition中

  > 另外还有RootBeanDefinition和ChildBeanDefinition作为bean在内存中对于bean的定义

- 2）将该beanDefinition注册到 DefaultListableBeanFactory中的属性 beanDefinitionMap

  > BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());

  

## 1.2 自定义xml解析

​	自定义xml标签，比如spring自身扩展的aop,tx.driven等。通过如下步骤扩展：

- 创建一个需要扩展的组件。
- 定义一个XSD 文件描述组件内容
- 创建一个文件，实现BeanDefinitionParser 接口，用来解析XSD 文件中的定义和组件定义。
- 创建一个Handler 文件，扩展自NamespaceHandlerSupport ，目的是将组件注册到Spring容器。
- 编写Spring.handlers 和Spring.schemas 文件。



# 2. Bean的加载

## 2.1 Bean加载的总体流程#getBean

从`AbstractBeanFactory#getBean`开始看起

主要 流程：

- 1).转换对应beanName

  - 包含alias和&factoryBean的转换

- 2) 尝试从缓存中加载单例

  - 有一个单例map缓存单例，有的话直接返回，

  - bean 的实例化：当然返回的时候如果是获取 FactoryBean产生的对象需要转换

- 3)原型模式的依赖检查

  - 如果原型模式存在循环依赖会报错，spring只处理单例模式的循环依赖

- 4)检则parentBeanFactory

  - 如果缓存没有数据，并且当前有父工厂那么使用父工厂加载该bean

- 5) 将存储XML 配置文件的GernericBeanDefinition 转换为RootBean Definition

  - 如果父类不为空要合并父类的属性；这里全部都会转换为RootBeanDefinition

- 6)寻找依赖

  - 如果有依赖会先加载依赖getBean

- 7)针对不同的scope 进行bean 的创建

- 8)类型转换

  - 常对该方法的调用参数requiredType 是为空的，但是可能会存在这样的情况，返回的bean 其实是个String ，但是requiredType 却传人Integer类型，那么这时候本步骤就会起作用了，它的功能是将返回的bean 转换为requiredType 所指定的类型。当然， String 转换为Integer 是最简单的一种转换，在Spri ng 中提供了各种各样的转换器，用户也可以自己扩展转换器来满足需求。

**注：**

​	上面所有bean返回前都会检查一下是否属于FactoryBean，如果是需要判定是否是获取工厂对象还是工厂创建的对象。

重点关注方法：

- `getSingleton`                           //初始化获取从缓存单例

- `getObjectForBeanInstance`    // 主要是完成FactoryBean的相关处理

- ```
  getSingleton(beanName, () -> {  //创建bean前后做了相关处理
  	createBean() //创建bean
  }
  ```

```java
//真正实现向IOC容器获取Bean的功能，也是触发依赖注入功能的地方
protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
                          @Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {

    //根据指定的名称获取被管理Bean的名称，剥离指定名称中对容器的相关依赖
    //如果指定的是别名，将别名转换为规范的Bean名称
    final String beanName = transformedBeanName(name);
    Object bean;

    // Eagerly check singleton cache for manually registered singletons.
    //先从缓存中取是否已经有被创建过的单态类型的Bean
    //对于单例模式的Bean整个IOC容器中只创建一次，不需要重复创建
    Object sharedInstance = getSingleton(beanName);
    //IOC容器创建单例模式Bean实例对象
    if (sharedInstance != null && args == null) {
        if (logger.isDebugEnabled()) {
            //如果指定名称的Bean在容器中已有单例模式的Bean被创建
            //直接返回已经创建的Bean
            if (isSingletonCurrentlyInCreation(beanName)) {
                logger.debug("Returning eagerly cached instance of singleton bean '" + beanName +
                             "' that is not fully initialized yet - a consequence of a circular reference");
            }
            else {
                logger.debug("Returning cached instance of singleton bean '" + beanName + "'");
            }
        }
        //获取给定Bean的实例对象，主要是完成FactoryBean的相关处理
        //注意：BeanFactory是管理容器中Bean的工厂，而FactoryBean是
        //创建创建对象的工厂Bean，两者之间有区别
        bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
    }

    else {
        // Fail if we're already creating this bean instance:
        // We're assumably within a circular reference.
        // 只有在单例模式下才会解决循环依赖问题，如果是原型模式，存在A中有B的属性，B中有A的属性；
        // 当A还没有创建完就创建B，那么就会出现下面的循环依赖异常
        //缓存没有正在创建的单例模式Bean
        //缓存中已经有已经创建的原型模式Bean
        //但是由于循环引用的问题导致实例化对象失败
        if (isPrototypeCurrentlyInCreation(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName);
        }

        // Check if bean definition exists in this factory.
        //对IOC容器中是否存在指定名称的BeanDefinition进行检查，首先检查是否
        //能在当前的BeanFactory中获取的所需要的Bean，如果不能则委托当前容器
        //的父级容器去查找，如果还是找不到则沿着容器的继承体系向父级容器查找
        BeanFactory parentBeanFactory = getParentBeanFactory();
        //当前容器的父级容器存在，且当前容器中不存在指定名称的Bean
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // Not found -> check parent.
            //解析指定Bean名称的原始名称
            String nameToLookup = originalBeanName(name);
            if (parentBeanFactory instanceof AbstractBeanFactory) {
                return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
                    nameToLookup, requiredType, args, typeCheckOnly);
            }
            else if (args != null) {
                // Delegation to parent with explicit args.
                //委派父级容器根据指定名称和显式的参数查找
                return (T) parentBeanFactory.getBean(nameToLookup, args);
            }
            else {
                // No args -> delegate to standard getBean method.
                //委派父级容器根据指定名称和类型查找
                return parentBeanFactory.getBean(nameToLookup, requiredType);
            }
        }

        //创建的Bean是否需要进行类型验证，一般不需要
        if (!typeCheckOnly) {
            //向容器标记指定的Bean已经被创建
            markBeanAsCreated(beanName);
        }

        try {
            //根据指定Bean名称获取其父级的Bean定义
            //主要解决Bean继承时子类合并父类公共属性问题
            final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
            checkMergedBeanDefinition(mbd, beanName, args);

            // Guarantee initialization of beans that the current bean depends on.
            //获取当前Bean所有依赖Bean的名称
            String[] dependsOn = mbd.getDependsOn();
            //如果当前Bean有依赖Bean
            if (dependsOn != null) {
                for (String dep : dependsOn) {
                    if (isDependent(beanName, dep)) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                                        "Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
                    }
                    //递归调用getBean方法，获取当前Bean的依赖Bean
                    registerDependentBean(dep, beanName);
                    //把被依赖Bean注册给当前依赖的Bean
                    getBean(dep);
                }
            }

            // Create bean instance.
            //创建单例模式Bean的实例对象
            if (mbd.isSingleton()) {
                //这里使用了一个匿名内部类，创建Bean实例对象，并且注册给所依赖的对象
                sharedInstance = getSingleton(beanName, () -> {
                    try {
                        //创建一个指定Bean实例对象，如果有父级继承，则合并子类和父类的定义
                        return createBean(beanName, mbd, args);
                    }
                    catch (BeansException ex) {
                        // Explicitly remove instance from singleton cache: It might have been put there
                        // eagerly by the creation process, to allow for circular reference resolution.
                        // Also remove any beans that received a temporary reference to the bean.
                        //显式地从容器单例模式Bean缓存中清除实例对象
                        destroySingleton(beanName);
                        throw ex;
                    }
                });
                //获取给定Bean的实例对象
                bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
            }

            //IOC容器创建原型模式Bean实例对象
            else if (mbd.isPrototype()) {
                // It's a prototype -> create a new instance.
                //原型模式(Prototype)是每次都会创建一个新的对象
                Object prototypeInstance = null;
                try {
                    //回调beforePrototypeCreation方法，默认的功能是注册当前创建的原型对象
                    beforePrototypeCreation(beanName);
                    //创建指定Bean对象实例
                    prototypeInstance = createBean(beanName, mbd, args);
                }
                finally {
                    //回调afterPrototypeCreation方法，默认的功能告诉IOC容器指定Bean的原型对象不再创建
                    afterPrototypeCreation(beanName);
                }
                //获取给定Bean的实例对象
                bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
            }

            //要创建的Bean既不是单例模式，也不是原型模式，则根据Bean定义资源中
            //配置的生命周期范围，选择实例化Bean的合适方法，这种在Web应用程序中
            //比较常用，如：request、session、application等生命周期
            else {
                String scopeName = mbd.getScope();
                final Scope scope = this.scopes.get(scopeName);
                //Bean定义资源中没有配置生命周期范围，则Bean定义不合法
                if (scope == null) {
                    throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
                }
                try {
                    //这里又使用了一个匿名内部类，获取一个指定生命周期范围的实例
                    Object scopedInstance = scope.get(beanName, () -> {
                        beforePrototypeCreation(beanName);
                        try {
                            return createBean(beanName, mbd, args);
                        }
                        finally {
                            afterPrototypeCreation(beanName);
                        }
                    });
                    //获取给定Bean的实例对象
                    bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
                }
                catch (IllegalStateException ex) {
                    throw new BeanCreationException(beanName,
                                                    "Scope '" + scopeName + "' is not active for the current thread; consider " +
                                                    "defining a scoped proxy for this bean if you intend to refer to it from a singleton",
                                                    ex);
                }
            }
        }
        catch (BeansException ex) {
            cleanupAfterBeanCreationFailure(beanName);
            throw ex;
        }
    }

    // Check if required type matches the type of the actual bean instance.
    //对创建的Bean实例对象进行类型检查
    if (requiredType != null && !requiredType.isInstance(bean)) {
        try {
            T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
            if (convertedBean == null) {
                throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
            }
            return convertedBean;
        }
        catch (TypeMismatchException ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to convert bean '" + name + "' to required type '" +
                             ClassUtils.getQualifiedName(requiredType) + "'", ex);
            }
            throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
        }
    }
    return (T) bean;
}
```

### 1）循环依赖问题



## 2.2 Bean的创建#createBean





具体看文件内容。