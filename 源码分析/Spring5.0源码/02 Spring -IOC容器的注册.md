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
            //获取当前Bean所有依赖Bean的名称,这边的依赖表示的是被依赖项必须先于当前的bean的加载；
            //xml配置是：depend-on;不同于ref（表示bean的属性了）；
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



## 2.2 Bean的创建#createBean

**流程：**

```text
1. 如果是单例则需要首先清除缓存
2. 实例化bean, 将BeanDefinition 转换为BeanWrapper 
 转换是一个复杂的过程，但是我们可以尝试概括大致的功能，如下所示
	-- 如果存在工厂方法则使用工厂方法进行初始化。
	-- 一个类有多个构造函数，每个构造函数都有不同的参数，所以需要根据参数锁定构造函数并进行初始化。
	-- 如果既不存在工厂方法也不存在带有参数的构造函数，则使用默认的构造函数进行bean 的实例化
3. MergedBeanDefinitionPostProcessor 的应用
	bean合并后的处理， Autowired 注解正是通过此方法实现诸如类型的预解析。
4. 属性的依赖处理
   在Spring 中会有循环依赖的情况，例如，当A 中含有B 的属性，而B 中又含有A 的属性时就会构成一个循环依赖，此时如果A 和B 都是单例，那么在Spring 中的处理方式就是当创建B 的时候，涉及自动注入A 的步骤时，并不是直接去再次创建A ，而是通过放入缓存中的ObjectFactory 来创建实例，这样就解决了循环依赖的问题。
5. 属性填充；将所有属性填充至bean 的实例中
6. 循环依赖检查
	之前有提到过，在Spring 中解决循环依赖只对单例有效，而对于prototype 的bean, Spring没有好的解决办法，唯一要做的就是抛出异常。在这个步骤里面会检测已经加载的bean 是否已经出现了依赖循环，并判断是再需要抛出异常。
7. 注册DisposableBean 
	如果配置了destroy-method ，这里需要注册以便于在销毁时候调用。
8. 完成创建井返回。
	可以看到上面的步骤非常的繁琐，每一步骤都使用了大量的代码来完成其功能，最复杂也是最难以理解的当属循环依赖的处理，在真正进入doCreateBean 前我们有必要先了解下循环依赖。
```



```java
//真正创建Bean的方法
protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args)
    throws BeanCreationException {

    // Instantiate the bean.
    //封装被创建的Bean对象
    BeanWrapper instanceWrapper = null;
    if (mbd.isSingleton()) {
        instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
    }
    if (instanceWrapper == null) {
        instanceWrapper = createBeanInstance(beanName, mbd, args);
    }
    final Object bean = instanceWrapper.getWrappedInstance();
    //获取实例化对象的类型
    Class<?> beanType = instanceWrapper.getWrappedClass();
    if (beanType != NullBean.class) {
        mbd.resolvedTargetType = beanType;
    }

    // Allow post-processors to modify the merged bean definition.
    //调用PostProcessor后置处理器
    synchronized (mbd.postProcessingLock) {
        if (!mbd.postProcessed) {
            try {
                applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
            }
            catch (Throwable ex) {
                throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                                "Post-processing of merged bean definition failed", ex);
            }
            mbd.postProcessed = true;
        }
    }

    // Eagerly cache singletons to be able to resolve circular references
    // even when triggered by lifecycle interfaces like BeanFactoryAware.
    //向容器中缓存单例模式的Bean对象，以防循环引用
    boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
                                      isSingletonCurrentlyInCreation(beanName));
    if (earlySingletonExposure) {
        if (logger.isDebugEnabled()) {
            logger.debug("Eagerly caching bean '" + beanName +
                         "' to allow for resolving potential circular references");
        }
        //这里是一个匿名内部类，为了防止循环引用，尽早持有对象的引用
        addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
    }

    // Initialize the bean instance.
    //Bean对象的初始化，依赖注入在此触发
    //这个exposedObject在初始化完成之后返回作为依赖注入完成后的Bean
    Object exposedObject = bean;
    try {
        //将Bean实例对象封装，并且Bean定义中配置的属性值赋值给实例对象
        populateBean(beanName, mbd, instanceWrapper);
        //初始化Bean对象
        exposedObject = initializeBean(beanName, exposedObject, mbd);
    }
    catch (Throwable ex) {
        if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
            throw (BeanCreationException) ex;
        }
        else {
            throw new BeanCreationException(
                mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
        }
    }

    if (earlySingletonExposure) {
        //获取指定名称的已注册的单例模式Bean对象
        Object earlySingletonReference = getSingleton(beanName, false);
        if (earlySingletonReference != null) {
            //根据名称获取的已注册的Bean和正在实例化的Bean是同一个
            if (exposedObject == bean) {
                //当前实例化的Bean初始化完成
                exposedObject = earlySingletonReference;
            }
            //当前Bean依赖其他Bean，并且当发生循环引用时不允许新创建实例对象
            else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
                String[] dependentBeans = getDependentBeans(beanName);
                Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
                //获取当前Bean所依赖的其他Bean
                for (String dependentBean : dependentBeans) {
                    //对依赖Bean进行类型检查
                    if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
                        actualDependentBeans.add(dependentBean);
                    }
                }
                if (!actualDependentBeans.isEmpty()) {
                    throw new BeanCurrentlyInCreationException(beanName,
                }
            }
        }
    }

    // Register bean as disposable.
    //注册完成依赖注入的Bean
    try {
        registerDisposableBeanIfNecessary(beanName, bean, mbd);
    }
    catch (BeanDefinitionValidationException ex) {
        throw new BeanCreationException(
            mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
    }

    return exposedObject;
}
```

### 1）属性注入

​		在了解循环依赖的时候， 我们曾经反复提到了populateBean 这个函数， 也多少了解了这个
函数的主要功能就是属性填充，那么究竟是如何实现填充的呢？

```java
public abstract class AbstractAutowireCapableBeanFactory{}	
//将Bean属性设置到生成的实例对象上
	protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
		if (bw == null) {
			if (mbd.hasPropertyValues()) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
			}
			else {
				// Skip property population phase for null instance.
				return;
			}
		}

		// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
		// state of the bean before properties are set. This can be used, for example,
		// to support styles of field injection.
		boolean continueWithPropertyPopulation = true;
		//给InstantiationAwareBeanPostProcessor 最后一次机会在属性设置前来改变bean
		//如:可以用来支持属性注入的类型
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
						//返回值为是否继续填充bean
						continueWithPropertyPopulation = false;
						break;
					}
				}
			}
		}
		// 如果后处理器发出停止填充命令则终止后续的执行
		if (!continueWithPropertyPopulation) {
			return;
		}
		//获取容器在解析Bean定义资源时为BeanDefiniton中设置的属性值
		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

		//对依赖注入处理，首先处理autowiring自动装配的依赖注入
		if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME ||
				mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);

			// Add property values based on autowire by name if applicable.
			//根据Bean名称进行autowiring自动装配处理
			if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME) {
				autowireByName(beanName, mbd, bw, newPvs);
			}

			// Add property values based on autowire by type if applicable.
			//根据Bean类型进行autowiring自动装配处理
			if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
				autowireByType(beanName, mbd, bw, newPvs);
			}

			pvs = newPvs;
		}

		// 后处理然已经初始化
		//对非autowiring的属性进行依赖注入处理
		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
		// 需要依赖检查
		boolean needsDepCheck = (mbd.getDependencyCheck() != RootBeanDefinition.DEPENDENCY_CHECK_NONE);

		if (hasInstAwareBpps || needsDepCheck) {
			if (pvs == null) {
				pvs = mbd.getPropertyValues();
			}
			PropertyDescriptor[] filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			if (hasInstAwareBpps) {
				for (BeanPostProcessor bp : getBeanPostProcessors()) {
					if (bp instanceof InstantiationAwareBeanPostProcessor) {
						InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
						// 对所有需要依赖检查的属性进行后处理
						pvs = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
						if (pvs == null) {
							return;
						}
					}
				}
			}
			if (needsDepCheck) {
				checkDependencies(beanName, mbd, filteredPds, pvs);
			}
		}

		if (pvs != null) {
			//对属性进行注入
			applyPropertyValues(beanName, mbd, bw, pvs);
		}
	}
```



### 2）初始化Bean

​	大家应该记得在bean 配置时bean 中有一个init-method 的属性，这个属性的作用是在bean实例化前调用init-method 指定的方法来根据用户业务进行相应的实例化。我们现在就已经进入这个方法了，首先看一下这个方法的执行位置， Spring 中程序已经执行过bean 的实例化，并且进行了属性的填充，而就在这时将会调用用户设定的初始化方法。



```java
public abstract class AbstractAutowireCapableBeanFactory{}	
	//初始容器创建的Bean实例对象，为其添加BeanPostProcessor后置处理器
	protected Object initializeBean(final String beanName, final Object bean, @Nullable RootBeanDefinition mbd) {
		//JDK的安全机制验证权限
		if (System.getSecurityManager() != null) {
			//实现PrivilegedAction接口的匿名内部类
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				invokeAwareMethods(beanName, bean);
				return null;
			}, getAccessControlContext());
		}
		else {
			//为Bean实例对象包装相关属性，如名称，类加载器，所属容器等信息
            //对特殊的bean 处理： BeanNameAware 、BeanClassLoaderAware 、BeanFactoryAware
			invokeAwareMethods(beanName, bean);
		}

		Object wrappedBean = bean;
		//对BeanPostProcessor后置处理器的postProcessBeforeInitialization
		//回调方法的调用，为Bean实例初始化前做一些处理
		if (mbd == null || !mbd.isSynthetic()) {
			wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
		}

		//调用Bean实例对象初始化的方法，这个初始化方法是在Spring Bean定义配置
		//文件中通过init-method属性指定的
        //init-methond方法和InitializingBean 方法这里执行，但是只能执行一个；后者拥有就执行后者；afterPropertiesSet不兼容
		try {
			invokeInitMethods(beanName, wrappedBean, mbd);
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					(mbd != null ? mbd.getResourceDescription() : null),
					beanName, "Invocation of init method failed", ex);
		}
		//对BeanPostProcessor后置处理器的postProcessAfterInitialization
		//回调方法的调用，为Bean实例初始化之后做一些处理
		if (mbd == null || !mbd.isSynthetic()) {
			wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
		}

		return wrappedBean;
	}

```

​		客户定制的初始化方法除了我们熟知的使用配置init-method 外，还有使自定义的bean 实现InitializingBean 接口，并在afterPropertiesSet 中实现自己的初始化业务逻辑。
​		init- method 与afterPropertiesSet 都是在初始化bean 时执行，执行顺序是afterPropertiesSet先执行，而init-method 后执行。

```java
protected void invokeInitMethods(String beanName, final Object bean, @Nullable RootBeanDefinition mbd)
    throws Throwable {

    boolean isInitializingBean = (bean instanceof InitializingBean);
    if (isInitializingBean && (mbd == null || !mbd.isExternallyManagedInitMethod("afterPropertiesSet"))) {
        if (logger.isDebugEnabled()) {
            logger.debug("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
        }
        if (System.getSecurityManager() != null) {
            try {
                AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
                    ((InitializingBean) bean).afterPropertiesSet();
                    return null;
                }, getAccessControlContext());
            }
            catch (PrivilegedActionException pae) {
                throw pae.getException();
            }
        }
        else {
            //先执行InitializingBean接口的初始化
            ((InitializingBean) bean).afterPropertiesSet();
        }
    }

    if (mbd != null && bean.getClass() != NullBean.class) {
        String initMethodName = mbd.getInitMethodName();
        //这里应该和afterPropertiesSet不兼容，前面实现了InitializingBean接口不在执行这个
        if (StringUtils.hasLength(initMethodName) &&
            !(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
            !mbd.isExternallyManagedInitMethod(initMethodName)) {
            //再 执行init 方法
            invokeCustomInitMethod(beanName, bean, mbd);
        }
    }
}
```

### 3）注册DisposableBean

```java
protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
		AccessControlContext acc = (System.getSecurityManager() != null ? getAccessControlContext() : null);
		if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
			if (mbd.isSingleton()) {
				// Register a DisposableBean implementation that performs all destruction
				// work for the given bean: DestructionAwareBeanPostProcessors,
				// DisposableBean interface, custom destroy method.
                //单例的销毁适配注册，会存储到一个Map中，在销毁时调用
				registerDisposableBean(beanName,
						new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), acc));
			}
			else {
				// A bean with a custom scope...
				Scope scope = this.scopes.get(mbd.getScope());
				if (scope == null) {
					throw new IllegalStateException("No Scope registered for scope name '" + mbd.getScope() + "'");
				}
				scope.registerDestructionCallback(beanName,
						new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), acc));
			}
		}
	}
```





## 2.3 其他问题

### 1）循环依赖问题

**先说结论：**

- prototype不支持循环引用，抛出BeanCurrentylyInCreationException异常表示循环依赖

- 单例无法解决通过构造器注入构成的循环依赖，抛出BeanCurrentylyInCreationException异常表示循环依赖

- spring支持 单例作用域的bean的setter循环依赖，可以通过`allowCircularReferences`关闭单例循环依赖

  通过Spring容器提前暴露刚完成构造器注入但未完成其他步骤（如setter注入）的bean来完成的。通过提前暴露一个单例工厂方法，从而使其他bean能够引用到该bean；只能解决单例作用域的bean循环依赖。
  具体步骤：
    （1）Spring容器创建单例 a bean，首先根据无参构造器创建bean，并暴露一个“ObjectFactory”，用于返回一个提前暴露一个创建中的bean，并将 “a”标识符放到“当前创建bean池”，然后进行setter注入b;
    （2）Spring容器创建单例 b bean，首先根据无参构造器创建bean，并暴露一个“ObjectFactory”，用于返回一个提前暴露一个创建中的bean，并将 “b”标识符放到“当前创建bean池”，然后进行setter注入c;
    （3）Spring容器创建单例 c bean，首先根据无参构造器创建bean，并暴露一个“ObjectFactory”，用于返回一个提前暴露一个创建中的bean，并将 “c”标识符放到“当前创建bean池”，然后进行setter注入a;进行注入"a"时，由于提前暴露了a的“ObjectFactory”工厂，从而使用它返回提前暴露一个创建中的bean。
    （4）最后在依赖注入“b”和“a”，完成setter注入

#### i:解决循环依赖的容器

```java
public class DefaultSingletonBeanRegistry{
    /** Cache of singleton objects: bean name --> bean instance  */
    //用于存放完全初始化好的 bean，从该缓存中取出的 bean 可以直接使用
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

    /** Cache of singleton factories: bean name --> ObjectFactory */
    //存放 bean 工厂对象，用于解决循环依赖
    private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

    /** Cache of early singleton objects: bean name --> bean instance */
    //存放原始的 bean 对象（尚未填充属性），用于解决循环依赖
    private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);
}
```

#### ii: 获取循环依赖引用

​	获取bean的时候，可以从`singletonFactories`容器中获取提早暴露出来的引用，解决setter单例的循环依赖

```java
//该方法逻辑比较简单:
// 1.首先从 singletonObjects 缓存中获取 bean 实例。
// 2. 若未命中，再去 earlySingletonObjects 缓存中获取原始 bean 实例。
// 3. 如果仍未命中，则从 singletonFactory 缓存中获取 ObjectFactory 对象，然后再调用 getObject 方法获取原始 bean 实例的应用，也就是早期引用。
// 4.获取成功后，将该实例放入 earlySingletonObjects 缓存中，并将 ObjectFactory 对象从 singletonFactories 移除。

protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		Object singletonObject = this.singletonObjects.get(beanName);
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			synchronized (this.singletonObjects) {
                //用来解决循环依赖
				singletonObject = this.earlySingletonObjects.get(beanName);
				if (singletonObject == null && allowEarlyReference) {
					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
					if (singletonFactory != null) {
                        //用来解决循环依赖，这里只是获取了提前暴露出来的对象引用
						singletonObject = singletonFactory.getObject();
						this.earlySingletonObjects.put(beanName, singletonObject);
						this.singletonFactories.remove(beanName);
					}
				}
			}
		}
		return singletonObject;
	}
```



#### iii: bean创建成功后清除暴露对象引用

```java
//类AbstractBeanFactory；创建bean
protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
			@Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {
    
    //.....
    //1. 这里使用了一个匿名内部类，创建Bean实例对象，并且注册给所依赖的对象
    sharedInstance = getSingleton(beanName, () -> {
        try {
            //创建一个指定Bean实例对象，如果有父级继承，则合并子类和父类的定义
            return createBean(beanName, mbd, args);
        }
        catch (BeansException ex) {
            //显式地从容器单例模式Bean缓存中清除实例对象
            destroySingleton(beanName);
            throw ex;
        }
    });
    
   // .....
}


//类 DefaultSingletonBeanRegistry
public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
    Assert.notNull(beanName, "Bean name must not be null");
    synchronized (this.singletonObjects) {
        Object singletonObject = this.singletonObjects.get(beanName);
        if (singletonObject == null) {
           // ...
            beforeSingletonCreation(beanName);
            boolean newSingleton = false;
            boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
            if (recordSuppressedExceptions) {
                this.suppressedExceptions = new LinkedHashSet<>();
            }
            try {
                //这里就是上面匿名函数的调用 createBean();
                singletonObject = singletonFactory.getObject();
                newSingleton = true;
            }
            catch (IllegalStateException ex) {
              //...
            }
            finally {
                if (recordSuppressedExceptions) {
                    this.suppressedExceptions = null;
                }
                afterSingletonCreation(beanName);
            }
            if (newSingleton) {
                //如果是新创建，这里对早起暴露单例容器做数据清除和单例的存储
                addSingleton(beanName, singletonObject);
            }
        }
        return singletonObject;
    }
    //清除提早暴露的容器缓存，加上单例的缓存
    protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.put(beanName, singletonObject);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.add(beanName);
		}
	}
}
```

#### iv：创建bean时暴露自身引用

createBean()->doCreateBean()->addSingletonFactory（）暴露引用

```text
//流程：
1. 创建原始 bean 实例 → createBeanInstance(beanName, mbd, args)
2. 添加原始对象工厂对象到 singletonFactories 缓存中 
        → addSingletonFactory(beanName, new ObjectFactory<Object>{...})
3. 填充属性，解析依赖 → populateBean(beanName, mbd, instanceWrapper)
	populateBean 用于向 beanA 这个原始对象中填充属性，当它检测到 beanA 依赖于 beanB 时，会首先去实例化beanB。beanB在此方法处也会解析自己的依赖，当它检测到 beanA 这个依赖，于是调用BeanFactry.getBean("beanA") 这个方法，从容器中的singletonFactories获取 beanA；从而解决循环依赖
```

```java
protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final Object[] args)
        throws BeanCreationException {

    BeanWrapper instanceWrapper = null;

    // ......

    // 创建 bean 对象，并将 bean 对象包裹在 BeanWrapper 对象中返回
    // 根据指定bean 使用对应的策略创建新的实例，如:工厂方法、构造函数注入、简单初始化
    instanceWrapper = createBeanInstance(beanName, mbd, args);
    
    // 从 BeanWrapper 对象中获取 bean 对象，这里的 bean 指向的是一个原始的对象
    final Object bean = (instanceWrapper != null ? instanceWrapper.getWrappedInstance() : null);

    /*
     * earlySingletonExposure 用于表示是否”提前暴露“原始对象的引用，用于解决循环依赖。
     * 对于单例 bean，该变量一般为 true。更详细的解释可以参考我之前的文章
     */ 
    boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
            isSingletonCurrentlyInCreation(beanName));
    if (earlySingletonExposure) {
        // ☆ 添加 bean 工厂对象到 singletonFactories 缓存中
        addSingletonFactory(beanName, new ObjectFactory<Object>() {
            @Override
            public Object getObject() throws BeansException {
                /* 
                 * 获取原始对象的早期引用，在 getEarlyBeanReference 方法中，会执行 AOP 
                 * 相关逻辑。若 bean 未被 AOP 拦截，getEarlyBeanReference 原样返回 
                 * bean，所以大家可以把 
                 *      return getEarlyBeanReference(beanName, mbd, bean) 
                 * 等价于：
                 *      return bean;
                 */
                return getEarlyBeanReference(beanName, mbd, bean);
            }
        });
    }

    Object exposedObject = bean;

    // ......
    
    // ☆ 填充属性，解析依赖
    populateBean(beanName, mbd, instanceWrapper);

    // ......

    // 返回 bean 实例
    return exposedObject;
}

protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
    synchronized (this.singletonObjects) {
        if (!this.singletonObjects.containsKey(beanName)) {
            // 将 singletonFactory 添加到 singletonFactories 缓存中
            this.singletonFactories.put(beanName, singletonFactory);

            // 从其他缓存中移除相关记录，即使没有
            this.earlySingletonObjects.remove(beanName);
            this.registeredSingletons.add(beanName);
        }
    }
}
```



### 2） XXXAware注入

​	我们的几个XXX Aware接口的注入是在初始化`initializeBean`的时候调用，不是在属性注入的时候调用

在我们new XmlBeanFactory的时候,默认调用了如下构造器，注册了一个默认不注入的依赖接口，

```java
public abstract class AbstractAutowireCapableBeanFactory{
    private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();
	private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();
	/**
	 * Create a new AbstractAutowireCapableBeanFactory.
	 * 构造器默认忽略如下几个接口的注入
	 */
	public AbstractAutowireCapableBeanFactory() {
		super();
		ignoreDependencyInterface(BeanNameAware.class);
		ignoreDependencyInterface(BeanFactoryAware.class);
		ignoreDependencyInterface(BeanClassLoaderAware.class);
	}
    
    // 这个方法会在bean填充属性populateBean的时候调用，排除这几个xxxAware接口的注入；
    // 前提是使用上面的接口
    protected boolean isExcludedFromDependencyCheck(PropertyDescriptor pd) {
		return (AutowireUtils.isExcludedFromDependencyCheck(pd) ||
				this.ignoredDependencyTypes.contains(pd.getPropertyType()) ||
				AutowireUtils.isSetterDefinedInInterface(pd, this.ignoredDependencyInterfaces));
	}
}
```

```java
//这个实在Bean的initializeBean的时候再次调用的。
private void invokeAwareMethods(final String beanName, final Object bean) {
    if (bean instanceof Aware) {
        if (bean instanceof BeanNameAware) {
            ((BeanNameAware) bean).setBeanName(beanName);
        }
        if (bean instanceof BeanClassLoaderAware) {
            ClassLoader bcl = getBeanClassLoader();
            if (bcl != null) {
                ((BeanClassLoaderAware) bean).setBeanClassLoader(bcl);
            }
        }
        if (bean instanceof BeanFactoryAware) {
            ((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
        }
    }
}
```



# 3. 容器的扩展

new ClassPathXmlApplicationContext() -> refresh()

```java
@Override
public void refresh() throws BeansException, IllegalStateException {
    synchronized (this.startupShutdownMonitor) {
        // Prepare this context for refreshing.
        //调用容器准备刷新的方法，获取容器的当时时间，同时给容器设置同步标识
        prepareRefresh();

        //初始化BeanFactory，并进行XML 文件读取;那么在这一步骤中将会复用BeanFactory 中的配置文件读取解析及其他功能，这一步之后，
        //ClassPathXmlApplicationContext 实际上就已经包含了BeanFactory所提供的功能，也就是可以进行bean 的提取等基础操作了。
        ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

        // Prepare the bean factory for use in this context.
        //为BeanFactory配置容器特性，例如类加载器、事件处理器等
        //	1.@Qualifier 与＠Autowired 应该是大家非常熟悉的注解，那么这两个注解正是在这一步骤中增加的支持。
        //  2.相关的XXX Aware也在注入依赖中被排除，从而在初始化中注入
        prepareBeanFactory(beanFactory);

        try {
            // Allows post-processing of the bean factory in context subclasses.
            //为容器的某些子类指定特殊的BeanPost事件处理器；比如支持web的增加环境配置；
            postProcessBeanFactory(beanFactory);

            // Invoke factory processors registered as beans in the context.
            //调用所有注册的BeanFactoryPostProcessor的Bean
            invokeBeanFactoryPostProcessors(beanFactory);

            // Register bean processors that intercept bean creation.
            //为BeanFactory注册BeanPost事件处理器.这里只是注册(通过getBean获取beanPost并进行IOC存储)
            // 真正的调用是在getBean 时候；BeanPostProcessor是Bean后置处理器，用于监听容器触发的事件
            registerBeanPostProcessors(beanFactory);

            // Initialize message source for this context.
            //初始化信息源，和国际化相关.
            initMessageSource();

            // Initialize event multicaster for this context.
            //初始化容器事件传播器.
            initApplicationEventMulticaster();

            // Initialize other special beans in specific context subclasses.
            //调用子类的某些特殊Bean初始化方法
            onRefresh();

            // Check for listener beans and register them.
            //为事件传播器注册事件监听器.
            registerListeners();

            // Instantiate all remaining (non-lazy-init) singletons.
            //初始化所有剩余的单例Bean（ 非惰性的）
            finishBeanFactoryInitialization(beanFactory);

            // Last step: publish corresponding event.
            //初始化容器的生命周期事件处理器，并发布容器的生命周期事件
            finishRefresh();
        }

        catch (BeansException ex) {
            if (logger.isWarnEnabled()) {
                logger.warn("Exception encountered during context initialization - " +
                            "cancelling refresh attempt: " + ex);
            }

            // Destroy already created singletons to avoid dangling resources.
            //销毁已创建的Bean
            destroyBeans();

            // Reset 'active' flag.
            //取消refresh操作，重置容器的同步标识.
            cancelRefresh(ex);

            // Propagate exception to caller.
            throw ex;
        }

        finally {
            // Reset common introspection caches in Spring's core, since we
            // might not ever need metadata for singleton beans anymore...
            resetCommonCaches();
        }
    }
}
```



