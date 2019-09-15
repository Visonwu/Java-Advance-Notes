tcp滑动窗口地址：<https://media.pearsoncmg.com/aw/ecs_kurose_compnetwork_7/cw/content/interactiveanimations/selective-repeat-protocol/index.html>



参考官网：<https://github.com/alibaba/Sentinel>

## Alibaba sentinel

**限流只是一个最基本的服务治理/服务质量体系要求**

- 流量的切换
- 能不能够针对不同的渠道设置不同的限流策略
- 流量的监控
- 熔断
- 动态限流
- 集群限流
  ....
- 承接过双十一、 实时监控

## Sentinel 怎么用

初始化限流规则
根据限流规则进行限流



## Sentinel限流的思考

- 限流用了什么算法来实现的（滑动窗口）
- 它是怎么实现的（责任链有关系）
- SPI的扩展

```java
ProcessorSlot<Object> chain = lookProcessChain(resourceWrapper);
chain.entry(context, resourceWrapper, null, count, prioritized, args);
```



责任链

```java
ProcessorSlotChain chain = new DefaultProcessorSlotChain();
chain.addLast(new NodeSelectorSlot());
chain.addLast(new ClusterBuilderSlot());
chain.addLast(new LogSlot());
chain.addLast(new StatisticSlot());  //流量数据统计
chain.addLast(new SystemSlot());
chain.addLast(new AuthoritySlot());
chain.addLast(new FlowSlot());      //根据当前流量统计和自己定义的设置做限流
chain.addLast(new DegradeSlot());
```

//数据统计

```java
@Override
public void addPass(int count) {
    WindowWrap<MetricBucket> wrap = data.currentWindow();
    wrap.value().addPass(count);
}
```



## Sentinel集群的限流

这个需要依赖第三方组件TokenServer来实现，服务都通过Token Server获取配置规则（规则又放置在nacos中，为了保证高可用toker server挂了，所以每一个服务有单独从nacos中获取限流规则）

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5vsseytyuj20mu0cudgy.jpg)



