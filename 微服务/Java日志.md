		简单介绍日志的发展历史最早的日志组件是Apache 基金会提供的 Log 4j； log4 j 能够通过配置文件轻松 的实现日志系统的管理和多样化配置所以很快被广泛运用。也是我们接触得比较早和比较多的日志组件。它几乎成了 Java 社区的日志标准。据说Apache 基金会还曾经建议 Sun 引入 Log4j 到 java 的标准库中，但 Sun 拒绝了 。 所以 sun 公司在 java 1.4 版本中，增加了日志库Java Util Logging) 。 其实现基本模仿了Log4j 的实现。在 JUL 出来以前， Log4j 就已经成为一项成熟的技术，使得 Log4j 在选择上占据了一定的优势Apache推出的 JUL 后，有一些项目使用 JUL ，也有一些项目使用 log4j ，这样就造成了开发者的混乱，因为这两个日志组件没有关联，所以要想实现统一管理或者替换就非常困难。

​		这个时候又轮到Apache 出手了，它推出了一个 ApacheCommons Logging 组件， JCL 只是定义了一套日志接口其内部也提供一个 Simple Log 的简单实现 ))，支持运行时动态加载日志组件的实现，也就是说，在你应用代码里，只需调用 Commons Logging 的接口，底层实现可以是Log4j ，也可以是 Java Util Logging由于它很出色的完成了主流日志的兼容，所以基本上在后面很长一段时间，是无敌的存在。连 spring 也都是依赖 JCL进行日志管理但是故事并没有结束原Log4J 的作者，它觉得 Apache Commons Logging 不够优秀，所以他想搞一套更优雅的方案，于是 slf4j 日志体系诞生了，slf 4j 实际上就是一个日志门面接口，它的作用类似于 Commons Loggins 。 并且他还为 slf4j 提供了一个日志的实现 log back 。

​			因此大家可以发现Java 的日志领域被划分为两个大营：**Commons Logging 和 slf4j**；另外，还有一个log4j2 是怎么回事呢？ 因为 slf4j 以及它的实现 l ogback 出来以后，很快就赶超了原本 apache 的log 4j 体系，所以 apache 在 2012 年重写了 log4j 成立了新的项目 Log4j2。

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g4r99dwpiyj20oq0eagmm.jpg)

**总的来说，日志的整个体系分为日志框架和日志系统**

- 日志框架：JCL 、Slf4j  ( 两个框架都兼容所有的日志系统)

- 日志系统：Log 4j 、 Log 4j2 、 Logback 、 JUL 
  

而在我们现在的应用中，绝大部分都是使用`slf4j` 作为门面，然后搭配 `logback `或者`log4j2 `日志系统