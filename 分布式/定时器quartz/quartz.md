

**quartz执行原理：**

​	这个基于xml配置的来说明

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



# 1.quartz 启动时序图

​	默认启动的执行定时任务的线程WorkerThread是10个，在quartz.properties中有配置，采用的是SimpleThreadPool线程池来使用

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5smnvg121j22em0ybadu.jpg)



# 2. quartz定时任务执行

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5soamv87tj228w13bjvc.jpg)

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

`CronTrigger`，`Trigger`,``中可以查看

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

