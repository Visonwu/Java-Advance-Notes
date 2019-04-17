# 1.需要选择rabbitMQ的依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>

```

# 2.配置类

```java
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {

    // 两个交换机
    @Bean("topicExchange")
    public TopicExchange getTopicExchange(){
        return new TopicExchange("TOPIC_EXCHANGE");
    }

    @Bean("fanoutExchange")
    public FanoutExchange getFanoutExchange(){
        return  new FanoutExchange("FANOUT_EXCHANGE");
    }

    // 三个队列
    @Bean("firstQueue")
    public Queue getFirstQueue(){
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-message-ttl",6000);
        Queue queue = new Queue("FIRST_QUEUE", false, false, true, args);
        return queue;
    }

    @Bean("secondQueue")
    public Queue getSecondQueue(){
        return new Queue("SECOND_QUEUE");
    }

    @Bean("thirdQueue")
    public Queue getThirdQueue(){
        return new Queue("THIRD_QUEUE");
    }

    // 两个绑定
    @Bean
    public Binding bindSecond(@Qualifier("secondQueue") Queue queue,@Qualifier("topicExchange") TopicExchange exchange){
        return BindingBuilder.bind(queue).to(exchange).with("#.vison.#");
    }

    @Bean
    public Binding bindThird(@Qualifier("thirdQueue") Queue queue,@Qualifier("fanoutExchange") FanoutExchange exchange){
        return BindingBuilder.bind(queue).to(exchange);
    }

}

```



# 3.生产者

```java
@Component
public class MyProvider {

    @Autowired
    AmqpTemplate amqpTemplate;

    public void send(){
        // 发送4条消息
        amqpTemplate.convertAndSend("","FIRST_QUEUE","-------- a direct msg");

        amqpTemplate.convertAndSend("TOPIC_EXCHANGE","shanghai.gupao.teacher","-------- a topic msg : shanghai.gupao.teacher");
        amqpTemplate.convertAndSend("TOPIC_EXCHANGE","changsha.gupao.student","-------- a topic msg : changsha.gupao.student");

        amqpTemplate.convertAndSend("FANOUT_EXCHANGE","","-------- a fanout msg");
    }
```

# 4. application.xml配置相关的连接

```properties

```



# 5. 消费者

# 5.1 消费者1

```java
@Component
@RabbitListener(queues = "FIRST_QUEUE")
public class FirstConsumer {

    @RabbitHandler
    public void process(String msg){
        System.out.println(" first queue received msg : " + msg);
    }
}
```

## 5.2 消费者2

```java
@Component
@RabbitListener(queues = "SECOND_QUEUE")
public class SecondConsumer {

    @RabbitHandler
    public void process(String msg){
        System.out.println(" second queue received msg : " + msg);
    }
}
```

# 5.3 消费者3

```java
@Component
@RabbitListener(queues = "THIRD_QUEUE")
public class ThirdConsumer {

    @RabbitHandler
    public void process(String msg){
        System.out.println(" third queue received msg : " + msg);
    }
}
```

