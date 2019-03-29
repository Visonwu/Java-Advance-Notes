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