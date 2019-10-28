# 0. quartz执行原理



​	这个基于xml配置的来说明，这里相当于

```xml
//定义配置定时任务
<bean id="timer" class="com.timer.Timer" />

//定义detail，包含执行方法，具体的job任务
<bean id="timerDetail" class="org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean">
    <property name="targetObject">
        <ref bean="timer" />
    </property>
    <property name="targetMethod">
        <value>run</value>
    </property>
</bean>

//定义trigger 执行定时任务
<bean id="timerTrigger" class="org.springframework.scheduling.quartz.CronTriggerFactoryBean">
    <property name="jobDetail">
        <ref bean="timerDetail" />
    </property>
    <property name="cronExpression">
        <value>0 57/0 * * * ?</value>
    </property>
</bean>

//将定义好的trigger定时任务放在SchedulerFactoryBean中起步加载初始化
<bean class="org.springframework.scheduling.quartz.SchedulerFactoryBean">
        <property name="triggers">
            <list>
                <ref bean="vipendTrigger" />
            </list>
    </property>
</bean>
```



**quartz定时任务类似生产者消费者模型**

- 基于spring的·`afterPropertiesSet()`加载文件quartz，启动整个定时任务
- 读取系统属性以及配置文件quartz.properties中的属性，加载默认的线程池配置等
- 启动一个`QuartzSchedulerThread`主线程负责轮询定时任务，并获取需要的定时任务
- 默认启动**10**后台个`WorkerThread`线程，用来交替执行定时任务，如果线程不够需要等待
- `WorkerThread`线程也是轮询获取定时任务，如果有就执行，由`QuartzSchedulerThread`交给它执行。



​			这里的`MethodInvokingJobDetailFactoryBean`实际上是用QuartzJobBean实现的Job接口定义的一个模板类，然后QuartzJobBean子类MethodInvokingJob持有MethodInvoker应用，每次执行Job的Execute接口的时候就会执行到这个接口，这个的意思是自己指定程序调用的方法；并且groupName默认是DEFAULT



# 1.quartz 启动时序图

​	默认启动的执行定时任务的线程WorkerThread是10个，在quartz.properties中有配置，采用的是SimpleThreadPool线程池来使用

![图](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5smnvg121j22em0ybadu.jpg)



# 2. quartz定时任务执行

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5soamv87tj228w13bjvc.jpg)

## 2.1 JobRunShell 作用

​		JobRunShell 用来为Job 提供安全的运行环境的，执行Job 中所有的作业，捕获运行中的异常，在任务执行完毕的时候更新Trigger 状态等等。
​	

​	JobRunShell 实例是用JobRunShellFactory 为QuartzSchedulerThread 创建的，在调度器决定一个Job 被触发的时候，它从线程池中取出一个线程来执行任务。

# 3. quartz 执行策略？

​	在修改系统时间后，如果定时器中途有多个没有执行到的任务，那么它的执行策略是什么？

## 3.1 xml配置方式

```xml
<bean id="timerTrigger" class="org.springframework.scheduling.quartz.CronTriggerFactoryBean">
    <property name="jobDetail">
        <ref bean="timerDetail" />
    </property>
    //通过misfireInstruction来配置实现在中途没有执行任务的处理策略
    <property name="misfireInstruction">
        <value>1</value>
    </property>
    <property name="cronExpression">
        <value>0 57/0 * * * ?</value>
    </property>
</bean>
```

## 3.2 执行策略

`CronTrigger`，`Trigger`,``中可以查看，当然SimpleTrigger等里面的值不同

```java
//这个表示中途有多少个定时任务没有执行，那么就会执行多少个定时任务
public static final int MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY = -1;

//这个来自于trigger,当使用cronTrigger的时候，是马上执行一次，也是默认策略，然后根据cron执行调用下一次的定时任务
public static final int MISFIRE_INSTRUCTION_SMART_POLICY = 0;

//这个表示过期的任务什么也不做，下次定时任务在执行
public static final int MISFIRE_INSTRUCTION_DO_NOTHING = 2;

```



## 3.3 源码分析

RAMJobStore. acquireNextTriggers()  ---》  applyMisfire()判定是否还有过期没执行的定时任务 

---》CronTriggerImpl. updateAfterMisfire() 是否更新下次任务的执行时间



```java
public List<OperableTrigger> acquireNextTriggers(long noLaterThan, int maxCount, long timeWindow) {
        synchronized (lock) {
            List<OperableTrigger> result = new ArrayList<OperableTrigger>();
            Set<JobKey> acquiredJobKeysForNoConcurrentExec = new HashSet<JobKey>();
            Set<TriggerWrapper> excludedTriggers = new HashSet<TriggerWrapper>();
            long batchEnd = noLaterThan;
            
            // return empty list if store has no triggers.
            if (timeTriggers.size() == 0)
                return result;
            
            while (true) {
                TriggerWrapper tw;

                try {
                    tw = timeTriggers.first();
                    if (tw == null)
                        break;
                    timeTriggers.remove(tw);
                } catch (java.util.NoSuchElementException nsee) {
                    break;
                }

                if (tw.trigger.getNextFireTime() == null) {
                    continue;
                }
				//判定当前定时任务是否过期，更新下次执行时间
                if (applyMisfire(tw)) {
                    if (tw.trigger.getNextFireTime() != null) {
                        timeTriggers.add(tw);
                    }
                    continue;
                }
				//根据当前时间和trigger中的下次任务执行时间做对比，如果当前时间大，那么久返回当前的trigger任务，然后执行它，
                if (tw.getTrigger().getNextFireTime().getTime() > batchEnd) {
                    timeTriggers.add(tw);
                    break;
                }
            }
                JobKey jobKey = tw.trigger.getJobKey();
                JobDetail job = jobsByKey.get(tw.trigger.getJobKey()).jobDetail;
             	....
            return result;
        }
    }
```



```java
//返回true需要循环获取到最近的一次 执行任务时间    
protected boolean applyMisfire(TriggerWrapper tw) {

        long misfireTime = System.currentTimeMillis();
        if (getMisfireThreshold() > 0) {
            misfireTime -= getMisfireThreshold();
        }

        Date tnft = tw.trigger.getNextFireTime();
        
        //这里如果执行策略为MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY 直接返回false了
        if (tnft == null || tnft.getTime() > misfireTime 
                || tw.trigger.getMisfireInstruction() == Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY) { 
            return false; 
        }
		...
        //这里还有多个策略更新当前时间
        tw.trigger.updateAfterMisfire(cal);
		....

        return true;
}
```

```java
//更新下次定时任务执行的时间
public void updateAfterMisfire(org.quartz.Calendar cal) {
    int instr = getMisfireInstruction();
	
    //这个对当前定时任务不更新执行时间
    if(instr == Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY)
        return;

    if (instr == MISFIRE_INSTRUCTION_SMART_POLICY) {
        instr = MISFIRE_INSTRUCTION_FIRE_ONCE_NOW;
    }
	
    //这个表示过期的定时任务不再执行，然后把下次执行的定时任务设置为下一轮的定时时间。
    if (instr == MISFIRE_INSTRUCTION_DO_NOTHING) {
        Date newFireTime = getFireTimeAfter(new Date());
        while (newFireTime != null && cal != null
               && !cal.isTimeIncluded(newFireTime.getTime())) {
            newFireTime = getFireTimeAfter(newFireTime);
        }
        setNextFireTime(newFireTime);
    //这个是在策略设置MISFIRE_INSTRUCTION_SMART_POLICY把下次执行时间设置为当前时间，等几秒轮询的时候就会执行当前设置的时间   
    } else if (instr == MISFIRE_INSTRUCTION_FIRE_ONCE_NOW) {
        setNextFireTime(new Date());
    }
}
```



# 4.调度原理

基于这个来查看执行原理

```java
// SchedulerFactory
SchedulerFactory  factory = new StdSchedulerFactory();

// Scheduler
Scheduler scheduler = factory.getScheduler();

// 绑定关系是1：N
scheduler.scheduleJob(jobDetail, trigger);
scheduler.start();
```

## 4.1 读取配置文件

```java
public Scheduler getScheduler() throws SchedulerException {
    if (cfg == null) {
        // 读取quartz.properties 配置文件
        initialize();
    }
    // 这个类是一个HashMap，用来基于调度器的名称保证调度器的唯一性
    SchedulerRepository schedRep = SchedulerRepository.getInstance();
    Scheduler sched = schedRep.lookup(getSchedulerName());
    // 如果调度器已经存在了
    if (sched != null) {
        // 调度器关闭了，移除
        if (sched.isShutdown()) {
         	schedRep.remove(getSchedulerName());
        } else {
            // 返回调度器
            return sched;
        }
    }
    // 调度器不存在，初始化
    sched = instantiate();
    return sched;
}
```

## 4.2 Scheuler初始化

instantiate()方法中做了初始化的所有工作：

```java
// 存储任务信息的JobStore
JobStore js = null;
// 创建线程池，默认是SimpleThreadPool
ThreadPool tp = null;
// 创建调度器
QuartzScheduler qs = null;
// 连接数据库的连接管理器
DBConnectionManager dbMgr = null;
// 自动生成ID
// 创建线程执行器，默认为DefaultThreadExecutor
ThreadExecutor threadExecutor;


```

### 4.2.1 创建了一个线程池

```java
//创建了一个线程池，默认是配置文件中指定的SimpleThreadPool
//String tpClass = cfg.getStringProperty(PROP_THREAD_POOL_CLASS, SimpleThreadPool.class.getName());
	tp = (ThreadPool) loadHelper.loadClass(tpClass).newInstance();
```

​		SimpleThreadPool 里面维护了三个list，分别存放所有的工作线程、空闲的工作线程和忙碌的工作线程。我们可以把SimpleThreadPool 理解为包工头。

```java
private List<WorkerThread> workers;
private LinkedList<WorkerThread> availWorkers = new LinkedList<WorkerThread>();
private LinkedList<WorkerThread> busyWorkers = new LinkedList<WorkerThread>();
```

​	tp 的runInThread()方法是线程池运行线程的接口方法。参数Runnable 是执行的任务内容。
取出WorkerThread 去执行参数里面的runnable（JobRunShell）

```java
WorkerThread wt = (WorkerThread)availWorkers.removeFirst();
busyWorkers.add(wt);
wt.run(runnable);
```

### 4.2.2 WorkerThread（工人）

WorkerThread 是SimpleThreadPool 的内部类， 用来执行任务。我们把WorkerThread 理解为工人。在WorkerThread 的run 方法中，执行传入的参数runnable任务：



### 4.2.3 创建调度线程（项目经理）

```java
qs = new QuartzScheduler(rsrcs, idleWaitTime, dbFailureRetry);
```

​	   在QuartzScheduler 的构造函数中，创建了QuartzSchedulerThread，我们把它理解为项目经理，它会调用包工头的工人资源，给他们安排任务。
​	  并且创建了线程执行器schedThreadExecutor ， 执行了这个QuartzSchedulerThread，也就是调用了它的run 方法。

```java
// 创建一个线程，resouces 里面有线程名称
this.schedThread = new QuartzSchedulerThread(this, resources);
// 线程执行器
ThreadExecutor schedThreadExecutor = resources.getThreadExecutor();
//执行这个线程，也就是调用了线程的run 方法
schedThreadExecutor.execute(this.schedThread);
```

​	点开QuartzSchedulerThread 类，找到run 方法，这个是Quartz 任务调度的核心方法：



### 4.3 源码总结

​		getScheduler 方法创建线程池ThreadPool，创建调度器QuartzScheduler，创建调度线程QuartzSchedulerThread，调度线程初始处于暂停状态。
scheduleJob 将任务添加到JobStore 中。

​		scheduler.start()方法激活调度器，QuartzSchedulerThread 从timeTrriger 取出待触发的任务， 并包装成TriggerFiredBundle ， 然后由JobRunShellFactory 创建TriggerFiredBundle 的执行线程JobRunShell ， 调度执行通过线程池SimpleThreadPool 去执行JobRunShell，而JobRunShell 执行的就是任务类的execute方法：job.execute(JobExecutionContext context)。



# 5.集群原理

基于数据库，如何实现任务的不重跑不漏跑？
**问题1**：如果任务执行中的资源是“下一个即将触发的任务”，怎么基于数据库实现这个资源的竞争？
**问题2：**怎么对数据的行加锁？

QuartzSchedulerThread 获取下一个即将触发的Trigger

```java
triggers = qsRsrcs.getJobStore().acquireNextTriggers()
```

调用JobStoreSupport 的acquireNextTriggers()方法，
调用JobStoreSupport.executeInNonManagedTXLock()方法

```java
return executeInNonManagedTXLock(lockName.)

//	 获取锁
transOwner = getLockHandler().obtainLock(conn, lockName);
```

最终会发现是通过：

```sql
select * from QRTZ_LOCKS t where t.lock_name='TRIGGER_ACCESS' for update
```



# 6.任务重复执行

​		在我们的演示过程中，有多个调度器，任务没有重复执行，也就是默认会加锁，什么情况下不会上锁呢？



JobStoreSupport 的executeInNonManagedTXLock()方法；如果lockName 为空，则不上锁

```java
if (lockName != null) {
    // If we aren't using db locks, then delay getting DB connection
    // until after acquiring the lock since it isn't needed.
    if (getLockHandler().requiresConnection()) {
    conn = getNonManagedTXConnection();
    }
    transOwner = getLockHandler().obtainLock(conn, lockName);
    }
    if (conn == null) {
    conn = getNonManagedTXConnection();
    }
```

​		而上一步JobStoreSupport 的acquireNextTriggers() 方法， 如果isAcquireTriggersWithinLock()值是false 并且maxCount>1 的话，lockName 赋值为null，否则赋值为LOCK_TRIGGER_ACCESS。这种情况获取Trigger 下默认不加锁。

```java
public List<OperableTrigger> acquireNextTriggers(final long noLaterThan, final int maxCount, final long timeWindow)throws JobPersistenceException {
    String lockName;
    if(isAcquireTriggersWithinLock() || maxCount > 1) {
    	lockName = LOCK_TRIGGER_ACCESS;
    } else {
        lockName = null;
    }
```

acquireTriggersWithinLock 默认是空的：

```java
private boolean acquireTriggersWithinLock = false;
```

maxCount 来自QuartzSchedulerThread：

```java
triggers = qsRsrcs.getJobStore().acquireNextTriggers(
now + idleWaitTime, Math.min(availThreadCount, qsRsrcs.getMaxBatchSize()),
qsRsrcs.getBatchTimeWindow());
```

​		getMaxBatchSize()来自QuartzSchedulerResources，代表Scheduler 一次拉取trigger 的最大数量，默认是1：

```java
private int maxBatchSize = 1;
```

这个值可以通过参数修改:

```java
org.quartz.scheduler.batchTriggerAcquisitionMaxCount=50
```

​		理论上把batchTriggerAcquisitionMaxCount 的值改掉以后，在获取Trigger 的时候就不会再上锁了，但是实际上为什么没有出现频繁的重复执行问题？因为每个调度器的线程持有锁的时间太短了。
QuartzSchedulerThread 的triggersFired()方法：



```java
List<TriggerFiredResult> res = qsRsrcs.getJobStore().triggersFired(triggers);
```



​	调用了JobStoreSupport 的triggersFired()方法，接着又调用了一个triggerFired(Connection conn, OperableTrigger trigger)方法：

​	如果Trigger 的状态不是ACQUIRED，也就是说被其他的线程fire 了，返回空。但是这种乐观锁的检查在高并发下难免会出现ABA 的问题，比如线程A 拿到的时候还是ACQUIRED 状态，但是刚准备执行的时候已经变成了EXECUTING 状态，这个时候就会出现重复执行的问题。

```java
if (!state.equals(STATE_ACQUIRED)) {
	return null;
}
```



**总结:**
	   如果设置的数量>1，并且使用JDBC JobStore（RAMJobStore 不支持分布式，只有一个调度器实例， 所以不加锁） ， 则属性org.quartz.jobStore.acquireTriggersWithinLock 应设置为true。否则不加锁会导致任务重复执行。

```properties
org.quartz.scheduler.batchTriggerAcquisitionMaxCount=1
org.quartz.jobStore.acquireTriggersWithinLock=true
```

