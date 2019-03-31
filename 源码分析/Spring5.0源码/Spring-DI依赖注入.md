

**DI依赖注入**是通过IOC保存的类信息实例化的过程，包含了：

- Bean的实例化以及Bean生命周期前后事件监听变化（`BeanPostProcessor`、 `InitializingBean`）；

  - 调用Bean构造方法实例化
  - 给Bean填充属性，包含自动注入
  - 如果有`BeanPostProcessor`，调用`postProcessBeforeInitialization`方法
  - 如果有`init-method`或者实现了`InitializingBean`(比`init-method`先调用)接口，调用相应的方法
  - 最后在调用`BeanPostProcessor`的`postProcessAfterInitialization`方法

  注：看源码`AbstractAutowireCapableBeanFactory`的`initiabizeBean`方法

- 生成的单例Bean被包装为`BeanWrapper`
- 单例的`BeanWrapper`会被缓存在一个Map中，下次直接取，原型的类型不会缓存下来



**依赖注入发生的时间**
​       当 Spring IOC 容器完成了 Bean 定义资源的定位、载入和解析注册以后，IOC 容器中已经管理类 Bean定义的相关数据，但是此时 IOC 容器还没有对所管理的 Bean 进行依赖注入，依赖注入在以下两种情况发生：

- 1)、用户第一次调用 getBean()方法时，IOC 容器触发依赖注入。
- 2)、当用户在配置文件中将<bean>元素配置了 lazy-init=false 属性，即让容器在解析注册 Bean 定义
  时进行预实例化，触发依赖注入

