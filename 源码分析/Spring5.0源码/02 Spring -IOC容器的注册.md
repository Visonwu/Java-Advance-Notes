

# 1.xml解析

##   1.1 默认 xml解析

​		主要解析 import,bean,alias标签

- 解析这些标签后封装到GenericBeanDefinition中

  > 另外还有RootBeanDefinition和ChildBeanDefinition作为bean在内存中对于bean的定义

- 将该beanDefinition注册到 DefaultListableBeanFactory中的属性 beanDefinitionMap

  > BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());

  ## 1.2 自定义xml解析

​	自定义xml标签，比如spring自身扩展的aop,tx.driven等。通过如下步骤扩展：

- 创建一个需要扩展的组件。
- 定义一个XSD 文件描述组件内容
- 创建一个文件，实现BeanDefinitionParser 接口，用来解析XSD 文件中的定义和组件定义。
- 创建一个Handler 文件，扩展自NamespaceHandlerSupport ，目的是将组件注册到Spring容器。
- 编写Spring.handlers 和Spring.schemas 文件。



# 2. Bean的加载

1.IOC容器的注册就是把bean静态信息存储在BeanDefinition中

笔记是：初始化得是ClassPathXmlApplicationContext(xml配置)



步骤：

1、初始化的入口在容器实现中的 refresh()调用来完成。
2、对 Bean 定义载入 IOC 容器使用BeanDefinitionReader的方法loadBeanDefinition()

3、使用DefaultBeanDefinitionDocumentReader对xml中定义的bean进行解析

 - 这里包含委派BeanDefinitionParserDelegate对Element的解析并抽象为BeanDefinition
 - 并且把对BeanDefinition注册到DefaultListableFactoryBean中

- 最后发送注册完成的事件通知



具体看文件内容。