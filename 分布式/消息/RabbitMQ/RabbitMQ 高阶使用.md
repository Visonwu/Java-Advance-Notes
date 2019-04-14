# 1、TTL（Time To Live）过期时间

## 1.1 设置消息的TTL

​	两种方式来设置消息的TTL，通过队列和单独的每条消息来设置，两种都设置的话，那么以过期时间较小的哪个为准。消息一旦超过设置的TTL值时，就会变成“死信（dead message）”

**1）通过队列属性设置消息过期时间：**

```java
Map<String,Object> ars = new HashMap<>();
ars.put("x-message-ttl",1000);
channel.queueDeclare("queueName",true,false,false,ars);
```

**2)  对消息进行单独设置TTL**

```java
AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
   .deliveryMode(2) // 持久化消息
   .contentEncoding("UTF-8")
   .expiration("10000") // TTL
   .build();
channel.basicPublish("", "TEST_TTL_QUEUE", properties, msg.getBytes());
```



## 1.2 队列TTL

​	队列的过期时间决定了在没有任何消费者以后，队列可以存活多久。

​	用于表示过期时间的x-expires参数以毫秒为单位，井且服从和x-message-ttl一样的约束条件，不过不能设置为0。比如该参数设置为1000，则表示该队列如果在1秒钟之内未使用则会被删除。

```java
Map<String,Object> ars = new HashMap<>();
ars.put("x-expires",1000); //单位ms,
channel.queueDeclare("queueDemo",true,false,false,ars);
```



# 2. 死信队列

​	DLX，全称为Dead-Letter-Exchange，可以称之为死信交换器，也有人称之为死信邮箱。当消息在一个队列中变成死信( dead message )后，它能被重新被发送到另一个交换器中，这个交换器就是DLX，绑定DLX的队列就称之为死信队列。

>  消息变成死信一般是由于以下几种情况:
>
> - 消息被拒绝(Basic.Reject/Basic.Nack)，并且设置requeue参数为false
> -  消息过期;
> - 队列达到最大长度。

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g22j4t3q1gj20h008x0t0.jpg)

```java
Map<String,Object> arguments = new HashMap<String,Object>();
arguments.put("x-dead-letter-exchange","DLX_EXCHANGE");
// 指定了这个队列的死信交换机
channel.queueDeclare("TEST_DLX_QUEUE", false, false, false, arguments);
// 声明死信交换机
channel.exchangeDeclare("DLX_EXCHANGE","topic", false, false, false, null);
// 声明死信队列
channel.queueDeclare("DLX_QUEUE", false, false, false, null);
// 绑定
channel.queueBind("DLX_QUEUE","DLX_EXCHANGE","#");
```

​	对于RabbitMQ来说，DLX是一个非常有用的特性。它可以处理异常情况下，消息不能够被消费者正确消费(消费者调用了Basic.Nack或者Basic.Reject)而被置入死信队列中的情况，后续分析程序可以通过消费这个死信队列中的内容来分析当时所遇到的异常情况，进而可以改善和优化系统



# 3. 延迟队列

​	RabbitMQ本身不支持延迟队列。可以使用TTL结合DLX的方式来实现消息的延迟投递，即把DLX跟某个队列绑定，到了指定时间，消息过期后，就会从DLX路由到这个队列，消费者可以从这个队列取走消息。

另一种方式是使用rabbitmq-delayed-message-exchange插件。
当然，将需要发送的信息保存在数据库，使用任务调度系统扫描然后发送也是可以实现的。



```java
延迟队列的使用场景有很多，比如:

	在订单系统中，一个用户下单之后通常有30分钟的时间进行支付，如果3 0分钟内没有支付成功，那么这个订单将进行异常处理，这时就可以使用延迟队列来处理这些订单了。
	
	用户希望通过手机远程遥控家里的智能设备在指定的时间进行工作。这时候就可以将用户指令发送到延迟队列，当指令设定的时间到了再将指令推送到智能设备。
```

# 4. 消息路由失败处理

  **消息路由失败的处理方式有以下两种：**

- 1） `mandatory`和`immediate`是**channel.basicPublish**方法中的两个参数，它们都有当消息传递过程中不可达目的地时将消息返回给生产者的功能。

- 2）RabbitMQ提供的备份交换器(Altemate  Exchange )可以将未能被交换器路由的消息(没有绑定队列或者没有匹配的绑定〉存储起来，而不用返回给客户端。

## 4.1  mandatory 参数

mandatory默认是false

​	**当mandatory参数设为true时，交换器无法根据自身的类型和路由键找到一个符合条件的队列**，那么RabbitMQ会调用Basic.Return命令将消息返回给生产者。当mandatory参数设置为false时，出现上述情形，则消息直接被丢弃(这是默认情况下)。那么生产者如何获取到没有被正确路由到合适队列的消息呢?这时候可以通过调用channel.addReturnListener来添加ReturnListener监听器实现。代码如下：

```java

channel.addReturnListener(new ReturnListener() {
    @Override
    public void handleReturn(int replyCode, String replyText, String exchange, String routeKey, AMQP.BasicProperties basicProperties, byte[] bytes) throws IOException {
        
        String text = new String(bytes);
        System.out.println(“返回的消息是：”+text);
        
    }
});
```

## 4.2 immediate参数

 	**immediate设为true时，如果交换器在将消息路由到队列时发现队列上并不存在任何消费者**，那么这条消息将不会存入队列中。当与路由键匹配的所有队列都没有消费者时，该消息会通过Basic.Return返回至生产者。



​       **RabbitMQ3. 0版本开始去掉了对immediate参数的支持，**对此RabbitMQ官方解释是:immediate参数会影响镜像队列的性能，增加了代码复杂性，所以一般用过期时间和死信队列来实现。后面介绍



总结：

​         概括来说，mandatory参数告诉服务器至少将该消息路由到一个队列中，否则将消息返回给生产者。immediate参数告诉服务器，如果该消息关联的队列上有消费者，则立刻投递:如果所有匹配的队列上都没有消费者，则直接将消息返还给生产者，不用将消息存入队列而等待消费者了。



## 4.3 备份交换器

​	备份交换器，英文名称为AlternateExchange，简称AE，或者更直白地称之为"备胎交换器"。生产者在发送消息的时候如果不设置mandatory参数，那么消息在未被路由的情况下将会丢失:如果设置了mandatory参数，那么需要添加ReturnListener的编程逻辑，生产者的代码将变得复杂。如果既不想复杂化生产者的编程逻辑，又不想消息丢失，那么可以使用备份交换器，这样可以将未被路由的消息存储在RabbitMQ中，在需要的时候去处理这些消息。

​        可以通过在声明交换器(调用channel.exchangeDeclare方法)的时候添加alternate-exchange参数来实现，也可以通过策略(Policy,后面介绍)的方式实现。如果两者同时使用，则前者的优先级更高，会覆盖掉Policy的设置.

代码如下：

```java
Map<String,Object> ars = new HashMap<>();
ars.put("alternate-exchange","myAe");
//这个ars就是把myAE交换器作为normalExchange的备用交换器
channel.exchangeDeclare("normalExchange","direct",true,false,ars);
channel.exchangeDeclare("myAe","fanout",true,false,null);

channel.queueDeclare("normalQueue",true,true,false,null);
channel.queueBind("normalQueue","normalExchange","normalKey");

channel.queueDeclare("unroutedQueue",true,false,false,null);
channel.queueBind("unroutedQueue","myAe","");
```

​	上面的代码中声明了两个交换器normalExchange和myAe，分别绑定了normalQueue和unroutedQueue这两个队列，同时将myAe设置为normalExchange的备份交换器。注意myAe的交换器类型为fanout(它会把所有发送到该交换器的消息路由到所有与该交换器绑定的队列中，不用管routeKey的事),这里的备用交换器如果设置其他类型，同样需要遵循路由规则，没有路由到消息也会丢失。

​       如果此时发送一条消息到normalExchange上，当路由键等于"normalKey"的时候，消息能正确路由到normalQueue这个队列中。如果路由键设为其他值，比如" errorKey,即消息不能被正确地路由到与normalExchange绑定的任何队列上，此时就会发送给myAe，进而发送到unroutedQueue这个队列。



```java
备份交换器的情况：

如果设置的备份交换器不存在，客户端和RabbitMQ服务端都不会有异常出现，此时消息会失
如果备份交换器没有绑定任何队列，客户端和RabbitMQ服务端都不会有异常出现，此时消息会丢失
如果备份交换器没有任何匹配的队列，客户端和RabbitMQ服务端都不会有异常出现，此时消息会丢失
如果备份交换器和mandatory参数一起使用，那么mandatory参数无效
```



# 5.RPC

​	RabbitMQ实现RPC的原理：服务端处理消息后，把响应消息发送到一个响应队列，客户端再从响应队列取到结果。

​	其中的问题：Client收到消息后，怎么知道应答消息是回复哪一条消息的？所以必须有一个唯一ID来关联，就是correlationId。

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g22jmo47gfj20g3051t91.jpg)

