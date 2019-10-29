# 1.Elastic-Job介绍

 **Quartz 的不足：**

- 1、作业只能通过DB 抢占随机负载，无法协调
- 2、任务不能分片——单个任务数据太多了跑不完，消耗线程，负载不均
- 3、作业日志可视化监控、统计



### 1.1 相关背景介绍

**发展历史：**

​		在当当的ddframe 框架中，需要一个任务调度系统（作业系统）；实现的话有两种思路，一个是修改开源产品，一种是基于开源产品搭建（封装），当当选择了后者，最开始这个调度系统叫做dd-job。它是一个无中心化的分布式调度框架。因为数据库缺少分布式协调功能（比如选主），替换为Zookeeper 后，增加了弹性扩容和数据分片的功能。Elastic-Job 是ddframe 中的dd-job 作业模块分离出来的作业框架，基于Quartz和Curator 开发，在2015 年开源。



**轻量级，无中心化解决方案:**
	为什么说是去中心化呢？因为没有统一的调度中心。集群的每个节点都是对等的，节点之间通过注册中心进行分布式协调。E-Job 存在主节点的概念，但是主节点没有调度的功能，而是用于处理一些集中式任务，如分片，清理运行时信息等。

​	Elastic-Job 最开始只有一个elastic-job-core 的项目，在2.X 版本以后主要分为Elastic-Job-Lite 和Elastic-Job-Cloud 两个子项目。其中，Elastic-Job-Lite 定位为轻量级无中心化解决方案， 使用jar 包的形式提供分布式任务的协调服务。而Elastic-Job-Cloud 使用Mesos + Docker 的解决方案，额外提供资源治理、应用分发以及进程隔离等服务（跟Lite 的区别只是部署方式不同，他们使用相同的API，只要开发一次）



### 1.2 功能特性

- **分布式调度协调**：用ZK 实现注册中心
-  **错过执行作业重触发**（Misfire）
- **支持并行调度**（任务分片）
- **作业分片一致性**，保证同一分片在分布式环境中仅一个执行实例
-  **弹性扩容缩容**：将任务拆分为n 个任务项后，各个服务器分别执行各自分配到的任务项。一旦有新的服务器加入集群，或现有服务器下线，elastic-job 将在保留本次任务执行不变的情况下，下次任务开始前触发任务重分片。
- **失效转移failover**：弹性扩容缩容在下次作业运行前重分片，但本次作业执行的过程中，下线的服务器所分配的作业将不会重新被分配。失效转移功能可以在本次作业运行中用空闲服务器抓取孤儿作业分片执行。同样失效转移功能也会牺牲部分性能。
- **支持作业生命周期操作**（Listener）
- **丰富的作业类型**（Simple、DataFlow、Script）
- **Spring 整合以及命名空间提供**
- **运维平台**



### 1.3 项目架构

​			应用在各自的节点执行任务，通过ZK 注册中心协调。节点注册、节点选举、任务分片、监听都在E-Job 的代码中完成。

[![K6bYGV.md.png](https://s2.ax1x.com/2019/10/28/K6bYGV.md.png)](https://imgchr.com/i/K6bYGV)

# 2.Java开发使用

### 步骤一：pom依赖

```xml
<dependency>
    <groupId>com.dangdang</groupId>
    <artifactId>elastic-job-lite-core</artifactId>
    <version>2.1.5</version>
</dependency>
```

### 步骤二：创建Job

​	Job有三种任务类型，如下所示，三种任意一种即可

#### 1） SimpleJob任务类型

​	**SimpleJob:** 简单实现，未经任何封装的类型。需实现SimpleJob 接口。

```java
import com.dangdang.ddframe.job.api.ShardingContext;
import com.dangdang.ddframe.job.api.simple.SimpleJob;

public class MySimpleJob implements SimpleJob {
    //context可以获取到传递的参数和分片数目
    public void execute(ShardingContext context) {
        
        System.out.println(String.format("分片项 ShardingItem: %s | 运行时间: %s | 线程ID: %s | 分片参数: %s ",
                context.getShardingItem(), new SimpleDateFormat("HH:mm:ss")
                                         .format(new Date()),
                Thread.currentThread().getId(), context.getShardingParameter()));
    }
}
```



#### 2） DataFlow任务类型

​		DataFlowJob：Dataflow 类型用于处理数据流，必须实现fetchData()和processData()的方法，一个用来获取数据，一个用来处理获取到的数据。

```java
public class MyDataFlowJob implements DataflowJob<String> {
    public List<String> fetchData(ShardingContext shardingContext) {
        System.out.println("开始获取数据");
        
        // 假装从文件或者数据库中获取到了数据
        Random random = new Random();
        if( random.nextInt() % 3 != 0 ){
            return null;
        }
        return Arrays.asList("qingshan","jack","seven");
    }

    public void processData(ShardingContext shardingContext, List<String> data) {
        for(String val : data){
            // 处理完数据要移除掉，不然就会一直跑,
            System.out.println("开始处理数据："+val);
        }
    }
}
```



#### 3） ScriptJob

​		Script：Script 类型作业意为脚本类型作业，支持shell，python，perl 等所有类型脚本。D 盘下新建1.bat，内容：

```bat
@echo ------【脚本任务】Sharding Context: %*
```



### 步骤三：配置启动

​		配置手册：http://elasticjob.io/docs/elastic-job-lite/02-guide/config-manual/

#### 1）ZK 注册中心配置

​		E-Job 使用ZK 来做分布式协调，所有的配置都会写入到ZK 节点。

#### 2）作业配置

​		（从底层往上层：Core——Type——Lite）

​		作业配置分为3 级，分别是JobCoreConfiguration，JobTypeConfiguration 和LiteJobConfiguration 。LiteJobConfiguration 使用JobTypeConfiguration ，JobTypeConfiguration 使用JobCoreConfiguration，层层嵌套。



- **Core：**配置类-- JobCoreConfiguration： 用于提供作业核心配置信息，如：作业名称、CRON 表达式、分片总数等。
- **Type：**配置类--- JobTypeConfiguration ：有3 个子类分别对应SIMPLE, DATAFLOW 和SCRIPT 类型作业，提供3 种作
  业需要的不同配置，如：DATAFLOW 类型是否流式处理或SCRIPT 类型的命
  令行等。Simple 和DataFlow 需要指定任务类的路径。
- **Root ：**配置类JobRootConfiguration 有2 个子类分别对应Lite 和Cloud 部署类型，提供不同部署类型所需的配置，如：Lite 类型的是否需要覆盖本地配置或Cloud 占用CPU 或Memory数量等。
  可以定义分片策略--http://elasticjob.io/docs/elastic-job-lite/02-guide/job-sharding-strategy/



```java
public class SimpleJobTest {
    // TODO 如果修改了代码，跑之前清空ZK
    public static void main(String[] args) {
       
        //1) ZK注册中心
        CoordinatorRegistryCenter regCenter = new ZookeeperRegistryCenter(
                new ZookeeperConfiguration("localhost:2181", "vison-ejob-standalone"));
        regCenter.init();

        // 数据源，使用DBCP
/*        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("com.mysql.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://localhost:3306/elastic_job_log");
        dataSource.setUsername("root");
        dataSource.setPassword("123456");
        JobEventConfiguration jobEventConfig = new JobEventRdbConfiguration(dataSource);*/

        // 2) 定义作业核心配置
        // TODO 如果修改了代码，跑之前清空ZK
        JobCoreConfiguration coreConfig = JobCoreConfiguration
                .newBuilder("MySimpleJob", "0/2 * * * * ?", 4)
                .shardingItemParameters("0=RDP, 1=CORE, 2=SIMS, 3=ECIF")
                .failover(true)
                .build();
        //3) 定义作业类型
        // 定义SIMPLE类型配置
        SimpleJobConfiguration simpleJobConfig = new SimpleJobConfiguration(coreConfig, MySimpleJob.class.getCanonicalName());
// 定义SCRIPT类型配置
//        ScriptJobConfiguration scriptJobConfig = new ScriptJobConfiguration(scriptJobCoreConfig,"D:/1.bat");
// 定义DATAFLOW类型配置
//        DataflowJobConfiguration dataJobConfig = new DataflowJobConfiguration(dataJobCoreConfig, MyDataFlowJob.class.getCanonicalName(), true);

        
        
        
        // 作业分片策略
        // 基于平均分配算法的分片策略
        String jobShardingStrategyClass = AverageAllocationJobShardingStrategy.class.getCanonicalName();

        //4) 定义Lite作业根配置
        // LiteJobConfiguration simpleJobRootConfig = LiteJobConfiguration.newBuilder(simpleJobConfig).jobShardingStrategyClass(jobShardingStrategyClass).build();
        LiteJobConfiguration simpleJobRootConfig = LiteJobConfiguration.newBuilder(simpleJobConfig).build();

        // 构建Job
        new JobScheduler(regCenter, simpleJobRootConfig).init();
        // new JobScheduler(regCenter, simpleJobRootConfig, jobEventConfig).init();
    }

}

```



# 3.Spring集成ElasticJob

## 3.1 pom依赖：

```xml
<properties>
		<elastic-job.version>2.1.5</elastic-job.version>
</properties>
<dependency>
    <groupId>com.dangdang</groupId>
    <artifactId>elastic-job-lite-core</artifactId>
    <version>${elastic-job.version}</version>
</dependency>
<!-- elastic-job-lite-spring -->
<dependency>
    <groupId>com.dangdang</groupId>
    <artifactId>elastic-job-lite-spring</artifactId>
    <version>${elastic-job.version}</version>
</dependency>
```

## 3.2 application.properties

```properties
server.port=${random.int[10000,19999]}
regCenter.serverList = localhost:2181
regCenter.namespace = vison-ejob-springboot
visonJob.cron = 0/3 * * * * ?
visonJob.shardingTotalCount = 2
visonJob.shardingItemParameters = 0=0,1=1
```



## 3.3 创建任务
创建任务类，加上@Component 注解

```java
@Component
public class SimpleJobDemo implements SimpleJob {
	public void execute(ShardingContext shardingContext) {
        System.out.println(String.format("------Thread ID: %s, %s,任务总片数: %s, " +
        "当前分片项: %s.当前参数: %s," +
        "当前任务名称: %s.当前任务参数%s",
        Thread.currentThread().getId(),
        new SimpleDateFormat("HH:mm:ss").format(new Date()),
            shardingContext.getShardingTotalCount(),
            shardingContext.getShardingItem(),
            shardingContext.getShardingParameter(),
            shardingContext.getJobName(),
            shardingContext.getJobParameter()
            ));
    }
}
```



## 3.4 注册中心配置

​		Bean 的initMethod 属性用来指定Bean 初始化完成之后要执行的方法，用来替代继承InitializingBean 接口，以便在容器启动的时候创建注册中心。

```java
@Configuration
public class ElasticRegCenterConfig {
    
    @Bean(initMethod = "init")
    public ZookeeperRegistryCenter regCenter(
        @Value("${regCenter.serverList}") final String serverList,
        @Value("${regCenter.namespace}") final String namespace) {
        return new ZookeeperRegistryCenter(new ZookeeperConfiguration(serverList, namespace));
    }
}
```



## 3.5 作业三级配置

​	设置其他任务类型和Java单机版类似

Core——Type——Lite
`return LiteJobConfiguration.newBuilder(new SimpleJobConfiguration(JobCoreConfiguration.newBuilder(`

```java
@Configuration
public class ElasticJobConfig {
    
    @Autowired
    private ZookeeperRegistryCenter regCenter;
	
    @Bean(initMethod = "init") //这里是简单任务类型
    public JobScheduler simpleJobScheduler(final SimpleJobDemo simpleJob,
        @Value("${visonJob.cron}") final String cron,
        @Value("${visonJob.shardingTotalCount}") final int shardingTotalCount,
        @Value("${visonJob.shardingItemParameters}") final String shardingItemParameters){
    return new SpringJobScheduler(simpleJob, regCenter,getLiteJobConfiguration(simpleJob.getClass(), cron, shardingTotalCount, shardingItemParameters));
     
   // 配置任务详细信息
    private LiteJobConfiguration getLiteJobConfiguration(final Class<? extends SimpleJob> jobClass,
                        final String cron,
                        final int shardingTotalCount,
                        final String shardingItemParameters,
                        final String jobParameter) {
        return LiteJobConfiguration.newBuilder(new SimpleJobConfiguration(
                JobCoreConfiguration.newBuilder(jobClass.getName(), cron, shardingTotalCount)
                       .shardingItemParameters(shardingItemParameters).jobParameter(jobParameter).build()
                , jobClass.getCanonicalName())
        ).overwrite(true).build();
    }                    
}                                                                                    
```



# 4.分片策略

## 4.1  分片项与分片参数

任务分片，是为了实现把一个任务拆分成多个子任务，在不同的ejob 示例上执行。

​	例如：100W 条数据，在配置文件中指定分成10 个子任务（分片项），这10 个子任务再按照一定的规则分配到5 个实际运行的服务器上执行。除了直接用分片项ShardingItem获取分片任务之外，还可以用item 对应的parameter 获取任务。



```java
//这里配置四个分片，所以参数也要对应四个，在job任务接口中可以获取到相应参数
JobCoreConfiguration coreConfig = JobCoreConfiguration
			.newBuilder("MySimpleJob", "0/2 * * * * ?",4)
    		.shardingItemParameters("0=RDP, 1=CORE, 2=SIMS, 3=ECIF")
    		.build();
```



**注意：**分片个数和分片参数要一一对应。通常把分片项设置得比E-Job 服务器个数大一些，比如3 台服务器，分成9 片，这样如果有服务器宕机，分片还可以相对均匀。



## 4.2 分片策略

​		参考官网：http://elasticjob.io/docs/elastic-job-lite/02-guide/job-sharding-strategy/

分片项如何分配到服务器？这个跟分片策略有关

| 策略                                  | 描述                                                  | 具体规则                                                     |
| ------------------------------------- | ----------------------------------------------------- | ------------------------------------------------------------ |
| AverageAllocationJobShardingStrategy  | 基于平均分配算法的分片策略，也是默认的分片策略。      | 如果分片不能整除，则不能整除的多余分片将依次追加到序号小的服务器。如：<br/> 如果有3 台服务器，分成9 片，则每台服务器分到的分片是： 1=[0,1,2], 2=[3,4,5],3=[6,7,8]<br/><br/> 如果有3 台服务器，分成8 片，则每台服务器分到的分片是：1=[0,1,6], 2=[2,3,7], 3=[4,5]<br/><br/> 如果有3 台服务器，分成10 片，则每台服务器分到的分片是： 1=[0,1,2,9], 2=[3,4,5],3=[6,7,8] |
| OdevitySortByNameJobShardingStrategy  | 根据作业名的哈希值奇偶数决定IP 升降序算法的分片策略。 | 根据作业名的哈希值奇偶数决定IP 升降序算法的分片策略。<br/> 作业名的哈希值为奇数则IP 升序。<br/> 作业名的哈希值为偶数则IP 降序。<br/>用于不同的作业平均分配负载至不同的服务器。 |
| RotateServerByNameJobShardingStrategy | 根据作业名的哈希值对服务器列表进行轮转的分片策略。    |                                                              |
| 自定义分片策略                        |                                                       | 实现JobShardingStrategy 接口并实现sharding 方法，接口方法参数为作业服务器IP 列表和分片策略选项，分片策略选项包括作业名称，分片总数以及分片序列号和个性化参数对照表，可以根据需求定制化自己的分片策略。 |

​		`AverageAllocationJobShardingStrategy` 的缺点是，一旦分片数小于作业服务器数，作业将永远分配至IP 地址靠前的服务器，导致IP 地址靠后的服务器空闲。而`OdevitySortByNameJobShardingStrategy `则可以根据作业名称重新分配服务器负载.

java使用：

```java
String jobShardingStrategyClass = AverageAllocationJobShardingStrategy.class.getCanonicalName();

LiteJobConfiguration simpleJobRootConfig =
                LiteJobConfiguration
                    .newBuilder(simpleJobConfig)
                    .jobShardingStrategyClass(jobShardingStrategyClass)
                    .build();
```

## 4.3 分片方案

获取到分片项shardingItem 之后，怎么对数据进行分片？

### 1） 方案一

​	**对业务主键进行取模**，获取余数等于分片项的数据

举例：获取到的sharding item 是0,1；在SQL 中加入过滤条件：where mod(id, 4) in (1, 2)。

这种方式的缺点：会导致索引失效，查询数据时会全表扫描。
解决方案：在查询条件中在增加一个索引条件进行过滤。



### 2）方案二

​		**在表中增加一个字段**；根据分片数生成一个mod 值。取模的基数要大于机器数。否则在增加机器后，会导致机器空闲。例如取模基数是2，而服务器有5 台，那么有三台服务器永远空闲。而取模基数是10，生成10 个shardingItem，可以分配到5 台服务器。当然，取模基数也可以调整。



### 3）方案三

​	如果从业务层面，可以用ShardingParamter 进行分片。

例如0=RDP, 1=CORE, 2=SIMS, 3=ECIF

```sql
List<users> = SELECT * FROM user WHERE status = 0 AND SYSTEM_ID =
'RDP' limit 0, 100。
```



# 5. ZK 注册中心数据结构

​	一个任务一个二级节点。
这里面有些节点是临时节点，只有任务运行的时候才能看到。
​		注意：修改了任务重新运行任务不生效，是因为ZK 的信息不会更新, 除非把overwrite 修改成true。

```java
LiteJobConfiguration simpleJobRootConfig = LiteJobConfiguration.newBuilder(simpleJobConfig)
                .overwrite(true).build();
```

- 第一级节点-命名空间（zookeeper配置的）

  - 二级节点--任务名称，下面的节点就是记录当前任务的详细信息

    - **config 节点**：JSON 格式存储。

      - 存储任务的配置信息，包含执行类，cron 表达式，分片算法类，分片数量，分片参数等等。
      - config 节点的数据是通过ConfigService 持久化到zookeeper 中去的。默认状态下，如果你修改了Job 的配置比如cron 表达式、分片数量等是不会更新到zookeeper 上去的，除非你在Lite 级别的配置把参数overwrite 修改成true。

    - **instances 节点：**同一个Job 下的elastic-job 的部署实例。

      - 一台机器上可以启动多个Job 实例，也就是Jar 包。instances 的命名是IP+@-@+PID。只有在运行的时候能看到。

    - **leader 节点：**任务实例的主节点信息，通过zookeeper 的主节点选举，选出来的主节点信息。

      - 在elastic job 中，任务的执行可以分布在不同的实例（节点）中，但任务分片等核心控制，需要由主节点完成。因此，任务执行前，需要选举出主节点。下面有三个子节点：
      - election：主节点选举
        - election 下面的instance 节点显示了当前主节点的实例ID：jobInstanceId。
          election 下面的latch 节点也是一个永久节点用于选举时候的实现分布式锁。
      - sharding：分片
        - sharding 节点下面有一个临时节点，necessary，是否需要重新分片的标记。如果
          分片总数变化，或任务实例节点上下线或启用/禁用，以及主节点选举，都会触发设置重
          分片标记，主节点会进行分片计算。
      - failover：失效转移

    - **servers 节点:**

      ​	任务实例的信息，主要是IP 地址，任务实例的IP 地址。跟instances 不同，如果多个任务实例在同一台机器上运行则只会出现一个IP 子节点。可在IP 地址节点写入DISABLED 表示该任务实例禁用。

    - **sharding 节点:**

      ​	任务的分片信息，子节点是分片项序号，从0 开始。分片个数是在任务配置中设置的。分片项序号的子节点存储详细信息。每个分片项下的子节点用于控制和记录分片运行状态。最主要的子节点就是instance。
      instance： 非临时节点	执行该分片项的作业运行实例主键
      running： 临时节点	分片项正在运行的状态；仅配置monitorExecution 时有效
      failover： 临时节点	如果该分片项被失效转移分配给其他作业服务器，则此节点值记录执行此分片的作业服务器IP
      misfire： 非临时节点	是否开启错过任务重新执行
      disabled： 非临时节点	是否禁用此分片项



# 6. 运维平台

## 6.1 下载解压运行

​		git 下载源码https://github.com/elasticjob/elastic-job-lite

​		对elastic-job-lite-console 打包得到安装包；解压缩elastic-job-lite-console-${version}.tar.gz 并执行bin\start.sh（Windows运行.bat）。打开浏览器访问http://localhost:8899/即可访问控制台。

​		8899 为默认端口号，可通过启动脚本输入-p 自定义端口号。默认管理员用户名和密码是root/root（配置文件可以修改）



## 6.2 添加ZK 注册中心

​	第一步，添加注册中心，输入ZK 地址和命名空间，并连接。

​	运维平台和elastic-job-lite 并无直接关系，是通过读取作业注册中心数据展现作业状态，或更新注册中心数据修改全局配置。控制台只能控制作业本身是否运行，但不能控制作业进程的启动，因为控制台和作业本身服务器是完全分离的，控制台并不能控制作业服务器。



## 6.4 事件追踪

​	http://elasticjob.io/docs/elastic-job-lite/02-guide/event-trace/

Elastic-Job 提供了事件追踪功能，可通过事件订阅的方式处理调度过程的重要事件，用于查询、统计和监控。
Elastic-Job-Lite 在配置中提供了JobEventConfiguration，目前支持数据库方式配置。

配置新增一个配置：

```java
BasicDataSource dataSource = new BasicDataSource();
dataSource.setDriverClassName("com.mysql.jdbc.Driver");
dataSource.setUrl("jdbc:mysql://localhost:3306/elastic_job_log");
dataSource.setUsername("root");
dataSource.setPassword("123456");
JobEventConfiguration jobEventConfig = new JobEventRdbConfiguration(dataSource);
…………
new JobScheduler(regCenter, simpleJobRootConfig, jobEventConfig).init();
```

​		事件追踪的event_trace_rdb_url 属性对应库自动创建JOB_EXECUTION_LOG 和JOB_STATUS_TRACE_LOG 两张表以及若干索引。

需要在运维平台中添加数据源信息，并且连接。





# 7.Elastic-job-starter

Git 上有一个现成的实现：https://github.com/TFdream/elasticjob-spring-boot-starter
工程：elasticjob-spring-boot-starter