# 1. flume之kafka通道

流程图：

![dwn19U.png](https://s1.ax1x.com/2020/08/23/dwn19U.png)

	KafkaChannel:
	优点： 基于kafka的副本功能，提供了高可用性！event被存储在kafka中！
			即便agent挂掉或broker挂掉，依然可以让sink从channel中读取数据！
			
	应用场景：
		①KafkaChannel和sink和source一起使用，单纯作为channel。
		②KafkaChannel+拦截器+Source，只要Source把数据写入到kafka就完成
		③KafkaChannel+sink，使用flume将kafka中的数据写入到其他的目的地，例如hdfs!
		
		为例在上述场景工作，KafkaChannel可以配置生产者和消费者的参数！
		
	配置参数：
		①在channel层面的参数，例如channel的类型，channel的容量等，需要和之前一样，在channel层面配置，例 
		    如：a1.channel.k1.type
		②和kafka集群相关的参数，需要在channel层面配置后，再加上kafka.
			例如： a1.channels.k1.kafka.topic ： 向哪个主题发送数据
					a1.channels.k1.kafka.bootstrap.servers： 集群地址
		③和Produer和Consumer相关的参数，需要加上produer和consumer的前缀：
			例如：a1.channels.k1.kafka.producer.acks=all
				  a1.channels.k1.kafka.consumer.group.id=vison
	
	必须的配置：
		type=org.apache.flume.channel.kafka.KafkaChannel
		kafka.bootstrap.servers=
		可选：
		kafka.topic： 生成到哪个主题
		parseAsFlumeEvent=true(默认)： 
				如果parseAsFlumeEvent=true，kafkaChannel会把数据以flume中Event的结构作为参考，
				把event中的header+body放入ProducerRecord的value中！
				
				如果parseAsFlumeEvent=false，kafkaChannel会把数据以flume中Event的结构作为参考，
				把event中body放入ProducerRecord的value中！
				
		a1.channels.k1.kafka.producer.acks=0


source 一个r1，使用两个kafka的channel c1,c2

```properties
a1.sources=r1
a1.channels=c1 c2

# configure source
a1.sources.r1.type = TAILDIR
#日志采集数据位置 JSON数据存储
a1.sources.r1.positionFile = /opt/module/flume/test/log_position.json
a1.sources.r1.filegroups = f1
##读取/tmp/logs/app-yyyy-mm-dd.log ^代表以xxx开头$代表以什么结尾 .代表匹配任意字符
#+代表匹配任意位置
a1.sources.r1.filegroups.f1 = /tmp/logs/^app.+.log$
a1.sources.r1.fileHeader = true


#interceptor
a1.sources.r1.interceptors =  i1 i2
a1.sources.r1.interceptors.i1.type = com.vison.flume.interceptor.LogETLInterceptor$Builder
a1.sources.r1.interceptors.i2.type = com.vison.flume.interceptor.LogTypeInterceptor$Builder

a1.sources.r1.selector.type = multiplexing
a1.sources.r1.selector.header = topic
a1.sources.r1.selector.mapping.topic_start = c1
a1.sources.r1.selector.mapping.topic_event = c2

# configure channel
a1.channels.c1.type = org.apache.flume.channel.kafka.KafkaChannel
a1.channels.c1.kafka.bootstrap.servers = ebusiness1:9092,ebusiness2:9092,ebusiness3:9092
a1.channels.c1.kafka.topic = topic_start
#表示是否将event的head放入producerRecord中，true表示放入，否则不放入
a1.channels.c1.parseAsFlumeEvent = false  
# 这里没有设置消费组
#a1.channels.c1.kafka.consumer.group.id = flume-consumer

a1.channels.c2.type = org.apache.flume.channel.kafka.KafkaChannel
a1.channels.c2.kafka.bootstrap.servers = ebusiness1:9092,ebusiness2:9092,ebusiness3:9092
a1.channels.c2.kafka.topic = topic_event
a1.channels.c2.parseAsFlumeEvent = false
#a1.channels.c2.kafka.consumer.group.id = flume-consumer

#连接组件 同一个source可以对接多个channel，一个sink只能从一个channel拿数据！
a1.sources.r1.channels=c1 c2
```



拦截器：

```java
package com.vison.flume.interceptor;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

//过滤时间戳不合法和Json数据不完整的日志
public class LogETLInterceptor implements Interceptor {

    @Override
    public void initialize() {

    }

    @Override
    public Event intercept(Event event) {

        // 1 获取数据
        byte[] body = event.getBody();
        String log = new String(body, Charset.forName("UTF-8"));

        // 2 判断数据类型并向Header中赋值
        if (log.contains("start")) {
            if (LogUtils.validateStart(log)){
                return event;
            }
        }else {
            if (LogUtils.validateEvent(log)){
                return event;
            }
        }

        // 3 返回校验结果
        return null;
    }

    @Override
    public List<Event> intercept(List<Event> events) {

        ArrayList<Event> interceptors = new ArrayList<>();

        for (Event event : events) {
            Event intercept1 = intercept(event);

            if (intercept1 != null){
                interceptors.add(intercept1);
            }
        }

        return interceptors;
    }

    @Override
    public void close() {

    }

    public static class Builder implements Interceptor.Builder{

        @Override
        public Interceptor build() {
            return new LogETLInterceptor();
        }

        @Override
        public void configure(Context context) {

        }
    }
}

// Flume日志过滤工具类
package com.vison.flume.interceptor;
import org.apache.commons.lang.math.NumberUtils;

public class LogUtils {

    public static boolean validateEvent(String log) {
        // 服务器时间 | json
        // 1549696569054 | {"cm":{"ln":"-89.2","sv":"V2.0.4","os":"8.2.0","g":"M67B4QYU@gmail.com","nw":"4G","l":"en","vc":"18","hw":"1080*1920","ar":"MX","uid":"u8678","t":"1549679122062","la":"-27.4","md":"sumsung-12","vn":"1.1.3","ba":"Sumsung","sr":"Y"},"ap":"weather","et":[]}

        // 1 切割
        String[] logContents = log.split("\\|");

        // 2 校验
        if(logContents.length != 2){
            return false;
        }

        //3 校验服务器时间
        if (logContents[0].length()!=13 || !NumberUtils.isDigits(logContents[0])){
            return false;
        }

        // 4 校验json
        if (!logContents[1].trim().startsWith("{") || !logContents[1].trim().endsWith("}")){
            return false;
        }

        return true;
    }

    public static boolean validateStart(String log) {
 // {"action":"1","ar":"MX","ba":"HTC","detail":"542","en":"start","entry":"2","extend1":"","g":"S3HQ7LKM@gmail.com","hw":"640*960","l":"en","la":"-43.4","ln":"-98.3","loading_time":"10","md":"HTC-5","mid":"993","nw":"WIFI","open_ad_type":"1","os":"8.2.1","sr":"D","sv":"V2.9.0","t":"1559551922019","uid":"993","vc":"0","vn":"1.1.5"}

        if (log == null){
            return false;
        }

        // 校验json
        if (!log.trim().startsWith("{") || !log.trim().endsWith("}")){
            return false;
        }

        return true;
    }
}

// Flume日志类型区分拦截器LogTypeInterceptor
////日志类型区分拦截器主要用于，将启动日志和事件日志区分开来，方便发往Kafka的不同Topic
package com.vison.flume.interceptor;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LogTypeInterceptor implements Interceptor {
    @Override
    public void initialize() {

    }

    @Override
    public Event intercept(Event event) {

        // 区分日志类型：   body  header
        // 1 获取body数据
        byte[] body = event.getBody();
        String log = new String(body, Charset.forName("UTF-8"));

        // 2 获取header
        Map<String, String> headers = event.getHeaders();

        // 3 判断数据类型并向Header中赋值
        if (log.contains("start")) {
            headers.put("topic","topic_start"); //让数据好发往topic_start中
        }else {
            headers.put("topic","topic_event");//让数据好发往topic_event中
        }

        return event;
    }

    @Override
    public List<Event> intercept(List<Event> events) {

        ArrayList<Event> interceptors = new ArrayList<>();

        for (Event event : events) {
            Event intercept1 = intercept(event);

            interceptors.add(intercept1);
        }

        return interceptors;
    }

    @Override
    public void close() {

    }

    public static class Builder implements  Interceptor.Builder{

        @Override
        public Interceptor build() {
            return new LogTypeInterceptor();
        }

        @Override
        public void configure(Context context) {

        }
    }
}
```



# 2.flume之kafka的source

```
①kafkaSource：kafkaSource就是kafka的一个消费者线程，可以从指定的主题中读取数据！
	如果希望提供消费的速率，可以配置多个kafkaSource，这些source组成同一个组！
	
	kafkaSource在工作时，会检查event的header中有没有timestamp属性，如果没有，
	kafkaSource会自动为event添加timestamp=当前kafkaSource所在机器的时间！
	
	kafkaSource启动一个消费者，消费者在消费时，默认从分区的最后一个位置消费！

必须的配置：
type=org.apache.flume.source.kafka.KafkaSource
kafka.bootstrap.servers=ebusiness1:9092,ebusiness2:9092,ebusiness3:9092
kafka.topics=消费的主题
kafka.topics.regex=使用正则表达式匹配主题

可选的配置：
kafka.consumer.group.id=消费者所在的组id
batchSize=一次put多少数据，小于10000
batchDurationMillis=每次put时间隔时间
useFlumeEventFormat= 如果为true，kafkaSource中的ConsumerRecord包含了header+body数据，那么构造
			event的数据，会把header和body构造给event。false（默认）只有body数据和之前kafka的channel中parseAsFlumeEvent相对应

和kafkaConsumer相关的属性：kafka.consumer=consumer的属性名
	例如：kafka.consumer.auto.offset.reset
```

