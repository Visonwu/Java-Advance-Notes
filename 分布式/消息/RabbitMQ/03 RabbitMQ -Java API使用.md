# 1.pom依赖

创建Maven工程，pom.xml引入依赖

```xml
<dependency>
    <groupId>com.rabbitmq</groupId>
    <artifactId>amqp-client</artifactId>
    <version>4.1.0</version>
</dependency>
```

# 2.创建连接

```java
ConnectionFactory factory = new ConnectionFactory();
factory.setHost(IP_ADDRESS);
factory.setPort(PORT);
factory.setVirtualHost(virtualHost); //虚拟消息服务器host
factory.setUsername("root");
factory.setPassword("root");
Connection connection = factory.newConnection(); //创建连接
Channel channel = connection.createChannel();//创建信道
```

或者

```java
ConnectionFactory factory = new ConnectionFactory();
factory.setUri("amqp://username:password@ipAddress:portNumber/virtualHost");
Connection connection = factory.newConnection(); //创建连接
Channel channel = connection.createChannel();//创建信道
```



# 2 .生产者

```java
//创建一个type="direct" 、持久化，非自动删除的交换器
//exchange, exchange_type, durable, autodelete, internal, Map<String, Object> arguments
channel.exchangeDeclare(EXCHANGE_NAME,"direct",true,false,null);

//创建一个持久化，非排他的，非自动删除的队列
// String queue, boolean durable, boolean exclusive, boolean autoDelete,Map<String, Object> arguments
channel.queueDeclare(QUEUE_NAME,true,false,false,null);

//将交换器与队列通过路由绑定
channel.queueBind(QUEUE_NAME,EXCHANGE_NAME,ROUTING_KEY);

//发布
//String exchange, String routingKey, BasicProperties props, byte[] bod
channel.basicPublish(EXCHANGE_NAME, QUEUE_NAME, null, msg.getBytes());
channel.close();
conn.close();

```

# 3.消费者

## 3.1 推(Push)模式

​	可以通过持续订阅的方式来消费，使用到的类Consumer,DefaultConsumer，接受消息一般通过实现Consumer接口或者继承DefaultConsumer类，当调用Consumer相关的API方法时，不同的订阅需要制定不同的消费者标签（consumerTag）来区分彼此，在同一个channel中的消费者也需要通过唯一的消费标签用来做区分，关键消费代码如下：

```java
  boolean autoAck = false;
channel.basicQos(64);//设置客户端最多接受违背ack的信息的个数
Consumer consumer = new DefaultConsumer(channel){
    
    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        
        System.out.println("recieve message :"+new String(body));
        try {
            TimeUnit.SECONDS.sleep(1);  //休眠一秒在返回确定信息
        }catch (InterruptedException e){
            e.printStackTrace();
        }
        channel.basicAck(envelope.getDeliveryTag(),false);
        
    }
};
//这里的自动应答被关闭，通过手动确认收到消息后在做应答，这个可以有效的防止消息不必要的丢失。
channel.basicConsume(QUEUE_NAME,autoAck,"myConsumerTag",consumer); 
```



## 3.2拉模式

​	拉模式的消费方式，通过channel.basicGet方法可以单条地获取消息，其返回值是GetResponse，Channel类的basicGet方法没有其他重载方法，



# 4.相关参数信息

## 4.1 声明交换器方法参数

```java
参数详细说明如下所述：

exchange:交换器的名称。

type:交换器的类型，常见的如fanout、direct、topic，header 详情看上一章。

durable:设置是否持久化。durable设置为true表示持久化，反之是非持久化。持久化可以将交换器存盘，在服务器重启的时候不会丢失相关信息。

autoDelete:设置是否自动删除。autoDelete设置为true则表示自动删除。自动删除的前提是至少有一个队列或者交换器与这个交换器绑定，之后所有与这个交换器绑定的队列或者交换器都与此解绑。注意不能错误地把这个参数理解为:"当与此交换器连接的客户端都断开时，RabbitMQ会自动删除本交换器"。

internal:设置是否是内置的。如果设置为true，则表示是内置的交换器，客户端程序无法直接发送消息到这个交换器中，只能通过交换器路由到交换器这种方式。

argument:其他一些结构化参数，比如alternate-exchange(有alternate­exchange的详情后面介绍)。

```

## 4.2 声明队列方法的参数

```java
boolean durable：是否持久化，代表队列在服务器重启后是否还存在。
boolean exclusive：是否排他性队列。排他性队列只能在声明它的Connection中使用，连接断开时自动删除。
boolean autoDelete：是否自动删除。如果为true，至少有一个消费者连接到这个队列，之后所有与这个队列连接
的消费者都断开时，队列会自动删除。
Map<String, Object> arguments：队列的其他属性，例如x-message-ttl、x-expires、x-max-length、x-max-length-bytes、x-dead-letter-exchange、x-dead-letter-routing-key、x-max-priority
```



## 4.3 发布消息参数

```java
exchange:交换器的名称，指明消息需要发送到哪个交换器中。如果设置为空字符串，则消息会被发送到RabbitMQ默认的交换器中。

routingKey:路由键，交换器根据路由键将消息存储到相应的队列之中。

BasicProperties:消息的基本属性集，其包含14个属性成员，分别有contentType、contentEncoding、headers(Map<String，Object>)、deliveryMode、priority、correlationld、replyTo、expiration、messageld、timestamp、type、userld、appld、clusterld.
    
byte[] body:消息体(payload)，真正需要发送的消息。

mandatory和immediate的详细内容后面讲解
```

**消息属性BasicProperties**消息的全部属性有14个，以下列举了一些主要的参数：

- Map<String,Object> headers :消息的其他自定义参数

- Integer deliveryMode :2持久化，其他：瞬态

- Integer priority :消息的优先级

- String correlationId :关联ID，方便RPC相应与请求关联

- String replyTo:回调队列

- String expiration TTL:消息过期时间，单位毫秒



## 4.4消费消息参数

```java
queue:队列的名称；

autoAck:设置是否自动确认。建议设成false，即不自动确认,默认不设置也是false；

consumerTag:消者标签，用来区分多个消费者，不设置默认''；

noLocal:设置为true则表示不能将同一个Connection中生产者发送的消息传送给这个Connection中的消费者，默认是false；

exclusive:设置是否排他,默认是false；

arguments:设置消费者的其他参数,默认是null；

callback:设置消费者的回调函数。用来处理RabbitMQ推送过来的消息，比如DefaultConsumer，使用时需要客户端重写(override )其中的方法。
```

# 5. 消息应答

​	为了保证消息从队列可靠地达到消费者，RabbitMQ提供了消息确认机制(message acknowledgement )。消费者在订阅队列时，可以指定autoAck参数，当autoAck等于false时，RabbitMQ会等待消费者显式地回复确认信号后才从内存(或者磁盘)中移去消息(实质上是先打上删除标记，之后再删除)。当autoAck等于true时，RabbitMQ会自把发送出去的消息置为确认，然后从内存(或者磁盘)中删除，而不管消费者是否真正地消费到了这些消息。

​             采用消息确认机制后，只要设置autoAck参数为false，消费者就有足够的时间处理消息(任务)，不用担心处理消息过程中消费者进程挂掉后消息丢失的问题，因为RabbitMQ会一直等待持有消息直到消费者显式调用Basic.Ack命令为止。当autoAck参数置为false，对于RabbitMQ服务端而言，队列中的消息分成了两个部分:一部分是等待投递给消费者的消息:一部分是己经投递给消费者，但是还没有收到消费者确认信号的消息。如果RabbitMQ一直没有收到消费者的确认信号，并且消费此消息的消费者己经断开连接，则RabbitMQ会安排该消息重新进入队列，等待投递给下一个消费者，当然也有可能还是原来的那个消费者**。**          

```java
boolean autoAck = false;  
channel.basicQos(64);//设置客户端最多接受违背ack的信息的个数
Consumer consumer = new DefaultConsumer(channel){
    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        
        System.out.println("recieve message :"+new String(body));
        try {
            TimeUnit.SECONDS.sleep(1);  //休眠一秒在返回确定信息
        }catch (InterruptedException e){
            e.printStackTrace();
        }
        //这里在消费信息后手动确认已经获取到信息了，然后Broker好删除消息
        channel.basicAck(envelope.getDeliveryTag(),false);
    }
};
//这里设置的自动应答会false
channel.basicConsume(QUEUE_NAME,autoAck,"myConsumerTag",consumer);
```

