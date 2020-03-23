# 1.服务端

```java
public static void nettyStart() {
    new Thread() {
        @Override
        public void run() {
            EventLoopGroup boosGroup = new NioEventLoopGroup();
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(boosGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_RCVBUF, 1024 * 1024)
                    .option(ChannelOption.SO_BACKLOG,2048)
                    //这里绑定handler处理器
                    .childHandler(new ServerInitializer())
                    .childOption(ChannelOption.TCP_NODELAY,true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
                // 服务器绑定端口监听
                ChannelFuture channelFuture = bootstrap.bind(PORT).sync();
                logger.info("----netty服务已经启动,端口：" + PORT + "----------");
                // 监听服务器关闭监听
                channelFuture.channel().closeFuture().sync();
            } catch (Exception e) {
                logger.error("--- netty服务异常 ---", e);
            } finally {
                boosGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }

        }
    }.start();
}

public class ServerInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    public void initChannel(SocketChannel arg0){
        //ChannelPipeline 可以理解为消息传送通道 通道一旦建立 持续存在
        ChannelPipeline channelPipeline = arg0.pipeline();
        //为通道添加功能
        ByteBuf buf = Unpooled.copiedBuffer("}".getBytes());//自定义拆包字符
        channelPipeline.addLast("delimiter",
                new DelimiterBasedFrameDecoder(1024,false,buf));
        //字符串解码  编码
        channelPipeline.addLast("decoder", new StringDecoder());
        channelPipeline.addLast("encoder",new ByteArrayEncoder());
        //添加自主逻辑

        channelPipeline.addLast(SpringUtil.getBean(ServerHandler.class));
    }
}

//ServerHandler自己定义需要处理相关的信息
```



# 2.客户端

```java
public class NettyClient {

    /*IP地址*/
    private static final String HOST = "127.0.0.1";
    /*端口号*/
    private static final int PORT1 = 9091;

    public static void main(String[] args) throws Exception {

        EventLoopGroup workGroup = new NioEventLoopGroup();
        try {
            byte[] bab5BBS = BinaryTransferUtils.hex2Bytes("BAB5BB");
            Bootstrap b = new Bootstrap();//客户端
            ByteBuf buf = Unpooled.copiedBuffer(bab5BBS);
            b.group(workGroup)
                    .channel(NioSocketChannel.class)//客户端 -->NioSocketChannel
                    .option(ChannelOption.SO_KEEPALIVE, true)

                    .handler(new ChannelInitializer<SocketChannel>() {//handler
                        @Override
                        protected void initChannel(SocketChannel sc) throws Exception {
                            sc.pipeline()
                              .addLast("delimiter",new DelimiterBasedFrameDecoder( 100000000,false,buf))
                              .addLast("decoder", new ByteArrayDecoder())
                                    .addLast("encoder", new StringEncoder())
                                    .addLast(new ClientHandler());
                        }
                    });
            //创建异步连接 可添加多个端口
            ChannelFuture cf1 = b.connect(HOST, PORT1).sync();

            cf1.channel().closeFuture().sync();
        } finally {
            workGroup.shutdownGracefully();
        }
    }
}

//处理器
public class ClientHandler extends SimpleChannelInboundHandler<Object> {

    private static final String ledId = "123";

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String send = "{'type':'add','appid':'" + ledId + "'}";
        byte[] data = send.getBytes();
        ByteBuf firstMessage = Unpooled.buffer();
        firstMessage.writeBytes(data);
        ctx.writeAndFlush(firstMessage);
        System.out.println("客户端发送消息:" + send);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx,Object msg) throws Exception {
            System.out.println("接收到客户端 发送消息:"+msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // Close the connection when an exception is raised.
        cause.printStackTrace();
        ctx.close();
    }
}
```

