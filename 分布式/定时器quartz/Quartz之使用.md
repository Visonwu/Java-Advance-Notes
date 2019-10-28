## 1.Quartz相关的介绍

​		Quartz是一个特性丰富的，开源的任务调度库，它几乎可以嵌入所有的Java程序，从很小的独立应用程序到大型商业系统。

​	Quartz可以用来创建成百上千的简单的或者复杂的任务，这些任务可以用来执行任何程序可以做的事情。Quartz拥有很多企业级的特性，包括支持JTA事务和集群。

相关任务调度工具：

```text
层次				举例						特点

操作系统		Linux crontab			只能执行简单脚本或者命令
			  Windows 计划任务			只能执行简单脚本或者命令

数据库			 MySQL、Oracle 			可以操作数据。不能执行Java 代码

工具			 Kettle 				 可以操作数据，执行脚本。没有集中配置

开发语言	    JDK Timer、ScheduledThreadPool Timer：单线程
JDK1.5 之后：ScheduledThreadPool（Cache、Fiexed、Single）:没有集中配置，日程管理不够灵活

容器			 Spring Task、@Scheduled   不支持集群

分布式框架	   XXL-JOB，Elastic-Job

```

**@Scheduled**的原理：也是用JUC 的ScheduledExecutorService 实现的@Scheduled(cron = “0 15 10 15 * ?”)

- 1、ScheduledAnnotationBeanPostProcessor 的postProcessAfterInitialization 方法将@Scheduled 的方法包装为指定的task添加到ScheduledTaskRegistrar 中
- 2、ScheduledAnnotationBeanPostProcessor 会监听Spring 的容器初始化事件， 在Spring 容器初始化完成后进行TaskScheduler 实现类实例的查找，若发现有SchedulingConfigurer 的实现类实例，则跳过3
- 3、查找TaskScheduler 实现类实例默认是通过类型查找，若有多个实现则会查找名字为"taskScheduler"的实现Bean，若没有找到则在ScheduledTaskRegistrar 调度任务的时候会创建一个newSingleThreadScheduledExecutor ， 将TaskScheduler 的实现类实例设置到ScheduledTaskRegistrar 属性中
- 4、ScheduledTaskRegistrar 的scheduleTasks 方法触发任务调度
- 5、真正调度任务的类是TaskScheduler 实现类中的ScheduledExecutorService，由J.U.C 提供



## 2. Quartz的使用

```text
#使用：一下几部
1、依赖
2、配置：		quartz.properties
3、调度器:		scheduler 负责任务调度
4、任务类：		job-jobdetail:详细任务   通过group+name唯一标识任务
5、触发器：		trigger：执行的时间点	   通过group+name标识触发器的唯一性
```

```xml
<dependency>
<groupId>org.quartz-scheduler</groupId>
<artifactId>quartz</artifactId>
<version>2.3.0</version>
</dependency>
```

### 2.1 默认配置文件

​		org.quartz.core 包下，有一个默认的配置文件，quartz.properties。当我们没有定义一个同名的配置文件的时候，就会使用默认配置文件里面的配置

```properties
org.quartz.scheduler.instanceName: DefaultQuartzScheduler
org.quartz.scheduler.rmi.export: false
org.quartz.scheduler.rmi.proxy: false
org.quartz.scheduler.wrapJobExecutionInUserTransaction: false
org.quartz.threadPool.class: org.quartz.simpl.SimpleThreadPool
org.quartz.threadPool.threadCount: 10
org.quartz.threadPool.threadPriority: 5
org.quartz.threadPool.threadsInheritContextClassLoaderOfInitializingThread: true
org.quartz.jobStore.misfireThreshold: 60000
org.quartz.jobStore.class: org.quartz.simpl.RAMJobStore
```



### 2.2 Java本地使用

#### 1）创建Job

​		实现唯一的方法execute()，方法中的代码就是任务执行的内容。此处仅输出字符串

```java
public class MyJob implements Job {
    public void execute(JobExecutionContext context) throws JobExecutionException {
    System.out.println("假发在哪里买的");
    }
}
```

在测试类main()方法中，把Job 进一步包装成JobDetail。
		必须要指定JobName 和groupName，两个合起来是唯一标识符。可以携带KV 的数据（JobDataMap），用于扩展属性，在运行的时候可以从context获取到。

```java
JobDetail jobDetail = JobBuilder.newJob(MyJob1.class)
.withIdentity("job1", "group1")
.usingJobData("vison","明天会更好")
.usingJobData("moon",5.21F)
.build();
```



#### 2）创建trigger

​		在测试类main()方法中，基于SimpleTrigger 定义了一个每2 秒钟运行一次、不断重复的Trigger：

```java
Trigger trigger = TriggerBuilder.newTrigger()
    .withIdentity("trigger1", "group1")
    .startNow()
    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
    .withIntervalInSeconds(2)
    .repeatForever())
    .build();
```

#### 3）创建Scheduler

​	在测试类main()方法中，通过Factory 获取调度器的实例，把JobDetail 和Trigger绑定，注册到容器中。Scheduler 先启动后启动无所谓，只要有Trigger 到达触发条件，就会执行任务。

注意这里，调度器一定是单例的。

```java
SchedulerFactory factory = new StdSchedulerFactory();
Scheduler scheduler = factory.getScheduler();
scheduler.scheduleJob(jobDetail, trigger);
scheduler.start();
```

#### 4）汇总测试

**Scheduler**

```java
public class MyScheduler {
	public static void main(String[] args) throws SchedulerException {

		// JobDetail；job的详细包装
		JobDetail jobDetail = JobBuilder.newJob(MyJob1.class)
				.withIdentity("job1", "group1") //唯一任务表示，name+group
				.usingJobData("vison","只为更好的你") //job参数信息
				.usingJobData("moon",5.21F)			//job参数信息
				.build();		

		// Trigger
		Trigger trigger = TriggerBuilder.newTrigger()
				.withIdentity("trigger1", "group1") //唯一触发器表示，name+group
				.startNow()
				.withSchedule(SimpleScheduleBuilder.simpleSchedule()
						.withIntervalInSeconds(2)
						.repeatForever())
				.build();

		// SchedulerFactory
		SchedulerFactory  factory = new StdSchedulerFactory();

		// Scheduler
		Scheduler scheduler = factory.getScheduler();

		// 绑定关系是1：N
		scheduler.scheduleJob(jobDetail, trigger);
		scheduler.start();	
	}
}
```

**Job任务**

```java
public class MyJob1 implements Job {

	public void execute(JobExecutionContext context) throws JobExecutionException {
		Date date = new Date();
        SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		JobDataMap dataMap = context.getJobDetail().getJobDataMap();
		System.out.println( " " + sf.format(date) + " 任务1执行了，" + dataMap.getString("vison"));
		
	}

}
```



### 2.3 体系结构

![KsykJx.png](https://s2.ax1x.com/2019/10/27/KsykJx.png)

#### 1）JobDetail

我们创建一个实现Job 接口的类，使用JobBuilder 包装成JobDetail，它可以携带KV 的数据。

#### 2) Trigger

​		定义任务的触发规律，Trigger，使用TriggerBuilder 来构建。JobDetail 跟Trigger 是1:N 的关系。

Trigger 接口在Quartz 有4 个继承的子接口：

- SimpelTrigger:简单触发器，固定时刻或者间隔，毫秒

- CalendarIntervalTrigger：基于日历的触发器，支持非固定时间的处罚，比如一个月28,29,30

- DailyTimeIntervalTrigger：基于日期的触发器，支持某天的时间段

- CronTrigger：基于cron表达式的触发器，常用

  

#### 3）基于Calendar的排除策略

​	如果要在触发器的基础上，排除一些时间区间不执行任务，就要用到Quartz 的Calendar 类（注意不是JDK 的Calendar）。可以按年、月、周、日、特定日期、Cron表达式排除。

- 调用Trigger 的modifiedByCalendar() 添加到触发器中， 

- 并且调用调度器的addCalendar()方法注册排除规则。



**Calendar的相关类实现有如下类：**

- BaseCalendar :为高级的Calendar 实现了基本的功能，实现了org.quartz.Calendar 接口
- AnnualCalendar: 排除年中一天或多天
- CronCalendar 日历的这种实现排除了由给定的CronExpression 表达的时间集合。例如，
  您可以使用此日历使用表达式“* * 0-7,18-23？* *”每天排除所有营业时间（上午8 点至下午5 点）。如果CronTrigger 具有给定的cron 表达式并且与具有相同表达式的CronCalendar 相关联，则日历将排除触发器包含的所有时间，并且它们将彼此抵消。
- DailyCalendar 您可以使用此日历来排除营业时间（上午8 点- 5 点）每天。每个DailyCalendar 仅允许指定单个时间范围，并且该时间范围可能不会跨越每日边界（即，您不能指定从上午8 点至凌晨5 点的时间范围）。如果属性invertTimeRange 为false（默认），则时间范围定义触发器不允许触发的时间范围。如果invertTimeRange 为true，则时间范围被反转- 也就是排除在定义的时间范围之外的所有时间。
- HolidayCalendar 特别的用于从Trigger 中排除节假日
- MonthlyCalendar 排除月份中的指定数天，例如，可用于排除每月的最后一天
- WeeklyCalendar 排除星期中的任意周几，例如，可用于排除周末，默认周六和周日



#### 4）Scheduler

​			调度器，是Quartz 的指挥官，由StdSchedulerFactory 产生。它是单例的。并且是Quartz 中最重要的API，默认是实现类是StdScheduler，里面包含了一个
QuartzScheduler。QuartzScheduler 里面又包含了一个QuartzSchedulerThread。



Scheduler 中的方法主要分为三大类：

- 1）操作调度器本身，例如调度器的启动start()、调度器的关闭shutdown()。
- 2）操作Trigger，例如pauseTriggers()、resumeTrigger()。
- 3）操作Job，例如scheduleJob()、unscheduleJob()、rescheduleJob()，deleteJob()等

这些方法非常重要，可以实现任务的动态调度。



#### 5）Listener

​		我们有这么一种需求，在每个任务运行结束之后发送通知给运维管理员。那是不是要在每个任务的最后添加一行代码呢？这种方式对原来的代码造成了入侵，不利于维护。
如果代码不是写在任务代码的最后一行，怎么知道任务执行完了呢？或者说，怎么监测到任务的生命周期呢？

观察者模式：定义对象间一种一对多的依赖关系，使得每当一个对象改变状态，则所有依赖它的对象都会得到通知并自动更新。

**工具类**：`ListenerManager`，用于添加、获取、移除监听器
**工具类**：`Matcher`，主要是基于groupName 和keyName 进行匹配。

Quartz 中提供了三种Listener，监听Scheduler 的，监听Trigger 的，监听Job 的。
	只需要创建类实现相应的接口，并在Scheduler 上注册Listener，便可实现对核心对象的监听。

- JobListener
- TriggerListener
- SchedulerListener



通过如下方式给对应的任务加上监听器

```java
// 创建并注册一个全局的Job Listener
scheduler.getListenerManager()
    .addJobListener(new MyJobListener(), EverythingMatcher.allJobs());
//MyjobListener实现监听器接口
```



#### 6）JobStore

​	问题：最多可以运行多少个任务（磁盘、内存、线程数）
Jobstore 用来存储任务和触发器相关的信息，例如所有任务的名称、数量、状态等等。Quartz 中有两种存储任务的方式，一种在在内存，一种是在数据库。



**1. RAMJobStore**

​		Quartz 默认的JobStore 是RAMJobstore，也就是把任务和触发器信息运行的信息存储在内存中，用到了HashMap、TreeSet、HashSet 等等数据结构。
​		如果程序崩溃或重启，所有存储在内存中的数据都会丢失。所以我们需要把这些数据持久化到磁盘。



**2. JDBCJobStore**

​	JDBCJobStore 可以通过JDBC 接口，将任务运行数据保存在数据库中。

JDBC 的实现方式有两种，JobStoreSupport 类的两个子类：

- JobStoreTX：在独立的程序中使用，自己管理事务，不参与外部事务。
- JobStoreCMT：(Container Managed Transactions (CMT)，如果需要容器管理事务时，使用它。

使用JDBCJobSotre 时，需要配置数据库信息：

quartz.properties中配置

```properties
org.quartz.jobStore.class:org.quartz.impl.jdbcjobstore.JobStoreTX
org.quartz.jobStore.driverDelegateClass:org.quartz.impl.jdbcjobstore.StdJDBCDelegate
# 使用quartz.properties，不使用默认配置
org.quartz.jobStore.useProperties:true
#数据库中quartz 表的表名前缀
org.quartz.jobStore.tablePrefix:QRTZ_
org.quartz.jobStore.dataSource:myDS
#配置数据源
org.quartz.dataSource.myDS.driver:com.mysql.jdbc.Driver
org.quartz.dataSource.myDS.URL:jdbc:mysql://localhost:3306/gupao?useUnicode=true&characterEncoding=utf8
org.quartz.dataSource.myDS.user:root
org.quartz.dataSource.myDS.password:123456
org.quartz.dataSource.myDS.validationQuery=select 0 from dual
```

问题来了？需要建什么表？表里面有什么字段？字段类型和长度是什么？
在官网的Downloads 链接中，提供了11 张表的建表语句：
			quartz-2.2.3-distribution\quartz-2.2.3\docs\dbTables
2.3 的版本在这个路径下：src\org\quartz\impl\jdbcjobstore



**表名与作用：**

```text
表名									作用
QRTZ_BLOB_TRIGGERS 					Trigger 作为Blob 类型存储
QRTZ_CALENDARS 						存储Quartz 的Calendar 信息
QRTZ_CRON_TRIGGERS 					存储CronTrigger，包括Cron 表达式和时区信息
QRTZ_FIRED_TRIGGERS 				存储与已触发的Trigger 相关的状态信息，以及相关Job 的执行信息
QRTZ_JOB_DETAILS 					存储每一个已配置的Job 的详细信息
QRTZ_LOCKS 							存储程序的悲观锁的信息
QRTZ_PAUSED_TRIGGER_GRPS 			存储已暂停的Trigger 组的信息
QRTZ_SCHEDULER_STATE 				存储少量的有关Scheduler 的状态信息，和别的Scheduler 实例
QRTZ_SIMPLE_TRIGGERS 				存储SimpleTrigger 的信息，包括重复次数、间隔、以及已触的次数
QRTZ_SIMPROP_TRIGGERS 				存储CalendarIntervalTrigger 和DailyTimeIntervalTrigger 两种类型的触发器
QRTZ_TRIGGERS 						存储已配置的Trigger 的信息
```



## 3.Quartz和Spring的集成

Spring-quartz 工程
Spring 在spring-context-support.jar 中直接提供了对Quartz 的支持。



### 3.1 定义job

这里的MyJob1是实现了org.quartz.Job接口的实现类

```xml
<bean name="myJob1" class="org.springframework.scheduling.quartz.JobDetailFactoryBean">
    <property name="name" value="my_job_1"/>
    <property name="group" value="my_group"/>
    <property name="jobClass" value="com.vison.quartz.MyJob1"/>
    <property name="durability" value="true"/>
</bean>
```



### 3.2 定义Trigger

```xml
<bean name="simpleTrigger" class="org.springframework.scheduling.quartz.SimpleTriggerFactoryBean">
    <property name="name" value="my_trigger_1"/>
    <property name="group" value="my_group"/>
    <property name="jobDetail" ref="myJob1"/>
    <property name="startDelay" value="1000"/>
    <property name="repeatInterval" value="5000"/>
    <property name="repeatCount" value="2"/>
</bean>
```

### 3.3 定义Scheduler

```xml
<bean name="scheduler" class="org.springframework.scheduling.quartz.SchedulerFactoryBean">
    <property name="triggers">
        <list>
            <ref bean="simpleTrigger"/>
            <ref bean="cronTrigger"/>
        </list>
    </property>
</bean>
```



### 3.4 注解方式实现

​	同样注解的方式也可以实现

```java
@Configuration
public class QuartzConfig {
    
    @Bean
    public JobDetail printTimeJobDetail(){
        
        return JobBuilder.newJob(MyJob1.class)
            .withIdentity("Job")
            .usingJobData("vison", "职位更好的你")
            .storeDurably()
            .build();
    }
    
    @Bean
    public Trigger printTimeJobTrigger() {
        
        CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder
            .cronSchedule("0/5 * * * * ?");
        
        return TriggerBuilder.newTrigger()
            .forJob(printTimeJobDetail())
            .withIdentity("quartzTaskService")
            .withSchedule(cronScheduleBuilder)
            .build();
    }
}    
```



## 4.动态调度

​		传统的Spring 方式集成，由于任务信息全部配置在xml 文件中，如果需要操作任务或者修改任务运行频率，只能重新编译、打包、部署、重启，如果有紧急问题需要处理，会浪费很多的时间。
​	有没有可以动态调度任务的方法？比如停止一个Job？启动一个Job？修改Job 的触发频率？

读取配置文件、写入配置文件、重启Scheduler 或重启应用明显是不可取的。
对于这种频繁变更并且需要实时生效的配置信息，我们可以放到哪里？
ZK、Redis、DB tables。并且，我们可以提供一个界面，实现对数据表的轻松操作。



**具体相关配置案例参考**:<https://github.com/Visonwu/Java-Sound-Code/springboot-quatz>里面去掉这个集群的相关配置，如下所示几个配置



**相关注意点：**

- 通过实现`CommandLineRunner`或者`ApplicationRunner`接口可以再springboot启动的时候被调用到，通过实现这个接口可以再springboot启动的时候，从数据中拉取数据并创建定时任务并启动定时任务执行。

- 自定义JobFactory用来实现从数据库获取Job数据，否则在job中的@Autowired无法完成注入；

  - 具体看AdaptableJobFactory,MyJobFactory，还有InitStartSchedule中

    - ```java
      // 如果不设置JobFactory，Service注入到Job会报空指针
      scheduler.setJobFactory(myJobFactory);
      ```

另外还需要自己建表用来存储自己的job数据：

```sql
CREATE database quartz;
-- 这个表参考JobDetailImpl
CREATE TABLE `sys_job` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `job_name` varchar(512) NOT NULL COMMENT '任务名称',
  `job_group` varchar(512) NOT NULL COMMENT '任务组名',
  `job_cron` varchar(512) NOT NULL COMMENT '时间表达式',
  `job_class_path` varchar(1024) NOT NULL COMMENT '类路径,全类型',
  `job_data_map` varchar(1024) DEFAULT NULL COMMENT '传递map参数',
  `job_status` int(2) NOT NULL COMMENT '状态:1启用 0停用',
  `job_describe` varchar(1024) DEFAULT NULL COMMENT '任务功能描述',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8;

-- 插入3条数据，3个任务
-- 注意第三条，是一个发送邮件的任务，需要改成你自己的QQ和授权码。不知道什么是授权码的自己百度。
INSERT INTO `sys_job` (`id`, `job_name`, `job_group`, `job_cron`, `job_class_path`, `job_data_map`, `job_status`, `job_describe`) VALUES (22, 'test', 'test', '*/20 * * * * ?', 'TestTask1', NULL, 1, 'a job a');
INSERT INTO `sys_job` (`id`, `job_name`, `job_group`, `job_cron`, `job_class_path`, `job_data_map`, `job_status`, `job_describe`) VALUES (23, 'test2', 'test', '*/30 * * * * ?', 'TestTask2', NULL, 1, 'another job');
INSERT INTO `sys_job` (`id`, `job_name`, `job_group`, `job_cron`, `job_class_path`, `job_data_map`, `job_status`, `job_describe`) VALUES (24, 'test3', 'mail', '*/10 * * * * ?', 'TestTask3', '{\"data\":{\"loginAccount\":\"改成你的QQ邮箱\",\"loginAuthCode\":\"改成你的邮箱授权码\",\"sender\":\"改成你的QQ邮箱\",\"emailContent\":\"&nbsp;&nbsp;&nbsp;&nbsp;你好，我是蒋介石的私生子，我在台湾有2000亿新台币冻结了。我现在在古交，又回不了台湾。所以没办法，只要你给我转1000块钱帮我解冻我的账号，我在台湾有我自己的部队。要是你今天帮了我，等我回到台湾给你留一个三军统帅的位置，另外再给你200亿人民币，我建行账号158158745745110蒋宽。这是我女秘书的账号，打了钱通知我，我给你安排专机接你来台。\",\"emailContentType\":\"text/html;charset=utf-8\",\"emailSubject\":\"十万火急\",\"recipients\":\"改成你要的收件人邮箱，可以有多个，英文逗号隔开\"}}', 1, 'fdsafdfds');

```





## 5.集群调度

具体相关配置参考:<https://github.com/Visonwu/Java-Sound-Code/springboot-quatz

**为什么需要集群？**

1、防止单点故障，减少对业务的影响
2、减少节点的压力，例如在10 点要触发1000 个任务，如果有10 个节点，则每个节点之需要执行100 个任务



**集群需要需要解决的问题？**

1、任务重跑，因为节点部署的内容是一样的，到10 点的时候，每个节点都会执行相同的操作，引起数据混乱。比如跑批，绝对不能执行多次。
2、任务漏跑，假如任务是平均分配的，本来应该在某个节点上执行的任务，因为节点故障，一直没有得到执行。
3、水平集群需要注意时间同步问题
4、Quartz 使用的是随机的负载均衡算法，不能指定节点执行



​	所以必须要有一种共享数据或者通信的机制。在分布式系统的不同节点中，我们可以采用什么样的方式，实现数据共享？两两通信，或者基于分布式的服务，实现数据共享。
例如：ZK、Redis、DB。

​	在Quartz 中，提供了一种简单的方式，基于数据库共享任务执行信息。也就是说；一个节点执行任务的时候，会操作数据库，其他的节点查询数据库，便可以感知到了（for update 加锁实现）



**集群的配置：**

quartz.properties中添加如下配置：

```properties

#如果使用集群，instanceId必须唯一，设置成AUTO,集群项目自己生成唯一id
org.quartz.scheduler.instanceId = AUTO
#是否使用集群
org.quartz.jobStore.isClustered = true
```

并且还需要添加上**数据库持久化信息**以及**数据源信息**

意思就是: 在上面的动态调度的基础上加上这些配置就可以了









