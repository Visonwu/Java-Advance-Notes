

# 1.启动

基于这个来查看源码：

```java
new JobScheduler(regCenter, simpleJobRootConfig).init();
```



```java
/**
 * 初始化作业.
 */
public void init() {
        
        LiteJobConfiguration liteJobConfigFromRegCenter = schedulerFacade.updateJobConfiguration(liteJobConfig);
  //设置分片数
   JobRegistry.getInstance()
      .setCurrentShardingTotalCount(liteJobConfigFromRegCenter
                                    		.getJobName(),                                                					 liteJobConfigFromRegCenter
                                    		.getTypeConfig()
                                    		.getCoreConfig()
                                    		.getShardingTotalCount());  
    //构建任务，创建调度器
    JobScheduleController jobScheduleController = new JobScheduleController(
                createScheduler(),
        createJobDetail(liteJobConfigFromRegCenter.getTypeConfig()
                        .getJobClass()),liteJobConfigFromRegCenter.getJobName());
    //注册服务
    JobRegistry.getInstance()
               .registerJob(liteJobConfigFromRegCenter
                                  .getJobName(), jobScheduleController, regCenter);
    //添加任务信息进行选举
    schedulerFacade.registerStartUpInfo(!liteJobConfigFromRegCenter.isDisabled());

	//启动调度器
      jobScheduleController.scheduleJob(liteJobConfigFromRegCenter
                                               .getTypeConfig()
                                               .getCoreConfig().getCron());
}
```

## 1.1 任务添加选举

```java
/**
 * 注册作业启动信息.
 * 
 * @param enabled 作业是否启用
 */
public void registerStartUpInfo(final boolean enabled) {
    listenerManager.startAllListeners();//启动所有监听器
    leaderService.electLeader(); //选举节点
    serverService.persistOnline(enabled);//服务信息持久化（ZK持久化）
    instanceService.persistOnline();	 //实例信息持久化（zk)
    shardingService.setReshardingFlag(); //重新分片
    monitorService.listen();			 //监控信息监听器
    if (!reconcileService.isRunning()) { //自我诊断修复
        reconcileService.startAsync();
    }
}


/**
* 选举主节点.
*/
public void electLeader() {
    log.debug("Elect a new leader now.");
    jobNodeStorage.executeInLeader(LeaderNode.LATCH, 
                                   new LeaderElectionExecutionCallback());
    log.debug("Leader election completed.");
}
```



# 2.任务执行和分片原理

## 2.1 任务执行流程

init 方法构建任务中：

```java
private JobDetail createJobDetail(final String jobClass) {
    
    //这里通过构建的 LiteJob，实现了quartz的job接口
    JobDetail result = JobBuilder.newJob(LiteJob.class)
        .withIdentity(liteJobConfig.getJobName())
        .build();
    
    result.getJobDataMap().put(JOB_FACADE_DATA_MAP_KEY, jobFacade);
    Optional<ElasticJob> elasticJobInstance = createElasticJobInstance();
    
    if (elasticJobInstance.isPresent()) {
        result.getJobDataMap().put(ELASTIC_JOB_DATA_MAP_KEY, elasticJobInstance.get());
    } else if (!jobClass.equals(ScriptJob.class.getCanonicalName())) {
        try {
            result.getJobDataMap()
                .put(ELASTIC_JOB_DATA_MAP_KEY, Class.forName(jobClass).newInstance());
        } catch (final ReflectiveOperationException ex) {
            throw new JobConfigurationException("Elastic-Job:
                                        Job class '%s' can not initialize.", jobClass);
        }
    }
    return result;
}
```

所以查看LiteJob类

```java
public final class LiteJob implements Job {    
    @Setter
    private ElasticJob elasticJob;   
    @Setter
    private JobFacade jobFacade;
    
    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        //使用jobExecutor来选择对应的执行器进行调用
        JobExecutorFactory.getJobExecutor(elasticJob, jobFacade).execute();
    }
}

public final class JobExecutorFactory {
    
    /**
     * 获取作业执行器.
     *
     * @param elasticJob 分布式弹性作业
     * @param jobFacade 作业内部服务门面服务
     * @return 作业执行器
     */
    @SuppressWarnings("unchecked")
    public static AbstractElasticJobExecutor getJobExecutor(final ElasticJob elasticJob, 				final JobFacade jobFacade) {
        if (null == elasticJob) {
            return new ScriptJobExecutor(jobFacade);
        }
        if (elasticJob instanceof SimpleJob) {
            return new SimpleJobExecutor((SimpleJob) elasticJob, jobFacade);
        }
        if (elasticJob instanceof DataflowJob) {
            return new DataflowJobExecutor((DataflowJob) elasticJob, jobFacade);
        }
        throw new JobConfigurationException("Cannot support job type '%s'", 	
                                            elasticJob.getClass().getCanonicalName());
    }
}
```

上面的execute方法使用了模板设计模式，模板类`AbstractElasticJobExecutor`

```java
/**
 * 执行作业.
 */
public final void execute() {
    try {
        jobFacade.checkJobExecutionEnvironment();
    } catch (final JobExecutionEnvironmentException cause) {
        jobExceptionHandler.handleException(jobName, cause);
    }
    ShardingContexts shardingContexts = jobFacade.getShardingContexts();
    if (shardingContexts.isAllowSendJobEvent()) {
        jobFacade.postJobStatusTraceEvent(shardingContexts.getTaskId(), 
                                          State.TASK_STAGING, String.format("Job '%s' 
                                             execute begin.", jobName));
    }
    if (jobFacade.misfireIfRunning(shardingContexts
                                   .getShardingItemParameters().keySet())) {
        if (shardingContexts.isAllowSendJobEvent()) {
            jobFacade.postJobStatusTraceEvent(shardingContexts.getTaskId(), 	                              State.TASK_FINISHED, String.format(
                "Previous job '%s' - shardingItems '%s' is still running, "+
                		"misfired job will start after previous job completed.", jobName, 
                shardingContexts.getShardingItemParameters().keySet()));
        }
        return;
    }
    try {
        jobFacade.beforeJobExecuted(shardingContexts);
        //CHECKSTYLE:OFF
    } catch (final Throwable cause) {
        //CHECKSTYLE:ON
        jobExceptionHandler.handleException(jobName, cause);
    }
   //重点这个执行方法                                                                       
    execute(shardingContexts, JobExecutionEvent.ExecutionSource.NORMAL_TRIGGER);
    while (jobFacade.isExecuteMisfired(shardingContexts
                                       .getShardingItemParameters()
                                       .keySet())) {
        jobFacade.clearMisfire(shardingContexts.getShardingItemParameters().keySet());
        execute(shardingContexts, JobExecutionEvent.ExecutionSource.MISFIRE);
    }
    jobFacade.failoverIfNecessary();
    try {
        jobFacade.afterJobExecuted(shardingContexts);
        //CHECKSTYLE:OFF
    } catch (final Throwable cause) {
        //CHECKSTYLE:ON
        jobExceptionHandler.handleException(jobName, cause);
    }
}
```

最终调用process方法：

```java
private void process(final ShardingContexts shardingContexts, final JobExecutionEvent.ExecutionSource executionSource) {
    Collection<Integer> items = shardingContexts.getShardingItemParameters().keySet();
    //只有一个分片时，直接执行
    if (1 == items.size()) {
        int item = shardingContexts.getShardingItemParameters()
            						.keySet().iterator().next();
        JobExecutionEvent jobExecutionEvent =  new JobExecutionEvent(
            			shardingContexts.getTaskId(), jobName, executionSource, item);
        process(shardingContexts, item, jobExecutionEvent);
        return;
    }
    // 本节点遍历执行相应的分片信息
    final CountDownLatch latch = new CountDownLatch(items.size());
    for (final int each : items) {
        final JobExecutionEvent jobExecutionEvent = new JobExecutionEvent(shardingContexts.getTaskId(), jobName, executionSource, each);
        if (executorService.isShutdown()) {
            return;
        }
        executorService.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    //这里最好调用到对应的
                    process(shardingContexts, each, jobExecutionEvent);
                } finally {
                    latch.countDown();
                }
            }
        });
    }
    try {
        // 等待所有的分片项任务执行完毕
        latch.await();
    } catch (final InterruptedException ex) {
        Thread.currentThread().interrupt();
    }
}
```

最后在调用不同的任务类型执行对应的任务类型的process方法

```java
protected abstract void process(ShardingContext shardingContext);
```



## 2.2 每个实例怎么获取到对应的分片内容

在抽象类`AbstractElasticJobExecutor`的execute方法中

```java
 public final void execute() {
        try {
            jobFacade.checkJobExecutionEnvironment();
        } catch (final JobExecutionEnvironmentException cause) {
            jobExceptionHandler.handleException(jobName, cause);
        }
     	//这里就从zk中 拿到了当前实例的需要处理的分片内容，可以一次debug获取相应的调用信息
        ShardingContexts shardingContexts = jobFacade.getShardingContexts();
     ...
 }
```



# 3. 失效转移

​		所谓失效转移，就是在执行任务的过程中发生异常时，这个分片任务可以在其他节点再次执行

```java
// 设置失效转移 ,failover设置为true
JobCoreConfiguration coreConfig = JobCoreConfiguration.newBuilder("MySimpleJob", "0/2 * * * * ?",4).shardingItemParameters("0=RDP, 1=CORE, 2=SIMS, 3=ECIF").failover(true).build();
```

​		`FailoverListenerManager` 监听的是zk 的instance 节点删除事件。如果任务配置了failover 等于true，其中某个instance 与zk 失去联系或被删除，并且失效的节点又不是本身，就会触发失效转移逻辑。

​			Job 的失效转移监听来源于`FailoverListenerManager` 中内部类`JobCrashedJobListener` 的dataChanged 方法。当节点任务失效时会调用JobCrashedJobListener 监听器，此监听器会根据实例id获取所有的分片，然后调用FailoverService 的setCrashedFailoverFlag 方法，将每个分片id 写到`/jobName/leader/failover/items `下，例如原来的实例负责1、2 分片项，那么items 节点就会写入1、2，代表这两个分片项需要失效转移。

```java
class JobCrashedJobListener extends AbstractJobListener {

    @Override
    protected void dataChanged(final String path, final Type eventType, 
                               final String data) {
        if (isFailoverEnabled() && Type.NODE_REMOVED == eventType 
            		&& instanceNode.isInstancePath(path)) {
            String jobInstanceId = path.substring(instanceNode
                                                  .getInstanceFullPath().length() + 1);
            if (jobInstanceId.equals(JobRegistry.getInstance()
                                     .getJobInstance(jobName).getJobInstanceId())) {
                return;
            }
            List<Integer> failoverItems = failoverService
                								.getFailoverItems(jobInstanceId);
            if (!failoverItems.isEmpty()) {
                for (int each : failoverItems) {
                    //设置失效分片项的标记
                    failoverService.setCrashedFailoverFlag(each);
                    failoverService.failoverIfNecessary();
                }
            } else {
                for (int each : shardingService.getShardingItems(jobInstanceId)) {
                    failoverService.setCrashedFailoverFlag(each);
                    failoverService.failoverIfNecessary();
                }
            }
        }
    }
}
```

​		然后接下来调用FailoverService 的failoverIfNessary 方法，首先判断是否需要失败转移，如果可以需要则只需作业失败转移。

```java
public void failoverIfNecessary() {
    if (needFailover()) {
    jobNodeStorage.executeInLeader(FailoverNode.LATCH, 
                                   new FailoverLeaderExecutionCallback());
    }
}

//条件一：${JOB_NAME}/leader/failover/items/${ITEM_ID} 有失效转移的作业分片项。
//条件二：当前作业不在运行中。
private boolean needFailover() {
    return jobNodeStorage.isJobNodeExisted(FailoverNode.ITEMS_ROOT)
    	 && !jobNodeStorage.getJobNodeChildrenKeys(FailoverNode.ITEMS_ROOT).isEmpty()
   		 && !JobRegistry.getInstance().isJobRunning(jobName);
}


//1、再次判断是否需要失效转移；
//2、从注册中心获得一个`${JOB_NAME}/leader/failover/items/${ITEM_ID}` 作业分片项；
//3、在注册中心节点`${JOB_NAME}/sharding/${ITEM_ID}/failover` 注册作业分片项为当前作业节点；
//4、然后移除任务转移分片项；
//5、最后调用执行，提交任务。

class FailoverLeaderExecutionCallback implements LeaderExecutionCallback {
        
    @Override
    public void execute() {
        // 判断是否需要失效转移
        if (JobRegistry.getInstance().isShutdown(jobName) || !needFailover()) {
            return;
        }
        // 从${JOB_NAME}/leader/failover/items/${ITEM_ID}获得一个分片项
        int crashedItem = Integer.parseInt(jobNodeStorage
                                         .getJobNodeChildrenKeys(FailoverNode.ITEMS_ROOT)
                                           .get(0));
        log.debug("Failover job '{}' begin, crashed item '{}'", jobName, crashedItem);
        
        // 在注册中心节点`${JOB_NAME}/sharding/${ITEM_ID}/failover`注册作业分片项为当前作业节点
        jobNodeStorage.fillEphemeralJobNode(FailoverNode
                                            	 .getExecutionFailoverNode(crashedItem), 											JobRegistry
                                                 .getInstance()
                                                 .getJobInstance(jobName)
                                                 .getJobInstanceId());
        // 移除任务转移分片项
        jobNodeStorage.removeJobNodeIfExisted(FailoverNode.getItemsNode(crashedItem));
        // TODO 不应使用triggerJob, 而是使用executor统一调度
        JobScheduleController jobScheduleController = JobRegistry.getInstance()
            							.getJobScheduleController(jobName);
        if (null != jobScheduleController) {
            // 提交任务
            jobScheduleController.triggerJob();
        }
    }
}
```

