
### 1. 原生Zookeeper API使用
```java
public class ZookeeperClient {

    private ZooKeeper zooKeeper;

    private String connectStr = "198.12.18.1:2181";

    private int session_time_out =5000;

    @Test
    public  void zooTest() {
        try {           
            doCreate();
            Stat stat = doGet();
            doUpdate(stat);
            doDelete(stat);
            close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	 private void open(){
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        try {
            //这里的Watcher是默认watcher
            zooKeeper = new ZooKeeper(connectStr, session_time_out, new Watcher() {
                public void process(WatchedEvent watchedEvent) {
                    System.out.println(watchedEvent.getState());
                    if (watchedEvent.getState() == Event.KeeperState.SyncConnected) {
                        countDownLatch.countDown();
                    }
                }
            });
            countDownLatch.wait();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    
    private void doCreate() {
        try {
            String result = zooKeeper.create("/vison", "100".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.println("create result: "+result);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private Stat doGet(){
        Stat stat = new Stat();
        try {
            byte[] data = zooKeeper.getData("/vison", null, stat);
            System.out.println("get: "+ new String(data));
        }catch (Exception e){
            e.printStackTrace();
        }
        return stat;
    }

    private void doUpdate(Stat stat){
        try {
            zooKeeper.setData("/vison","1".getBytes(),stat.getVersion());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void doDelete(Stat stat){
        try {
            zooKeeper.delete("/vison",stat.getVersion());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void close(){
        try {
            zooKeeper.close();
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }
}
```

### 2. 事件机制
&emsp;&emsp;Watcher 监听机制是 Zookeeper 中非常重要的特性，我们基于 zookeeper 上创建的节点，可以对这些节点绑定监听事件，比如可以监听节点数据变更、节点删除、子节点状态变更等事件，通过这个事件机制，可以基于 zookeeper实现分布式锁、集群管理等功能watcher 特性：当数据发生变化的时候， zookeeper 会产生一个 watcher 事件，并且会发送到客户端。但是客户端只会收到一次通知。如果后续这个节点再次发生变化，那么之前设置 watcher 的客户端不会再次收到消息。（watcher 是一次性的操作）。 可以通过循环监听去达到永久监听效果。

#### 2.1 如何注册事件机制
&emsp;&emsp;通过这三个操作绑定事件 ：getData，exists，getChildren

#### 2.2 如何触发事件
&emsp;&emsp;事务类型的操作，都会触发监听事件：create，delete，setData

#### 2.3 watcher 事件类型
&emsp;&emsp;通过事件函数中的 WatchedEvent 的getType可以获取到如下的事件类型。

- None (-1), 客户端链接状态发生变化的时候，会收到 none 的事件
- NodeCreated (1), 创建节点的事件。 比如 zk-persis-vison
- NodeDeleted (2), 删除节点的事件
- NodeDataChanged (3), 节点数据发生变更
- NodeChildrenChanged (4); 子节点被创建、被删除会发生事件触发

#### 2.4 不同的事务操作产生不同的事件

| 事务操作                     | /zk-persis-ws                         | /zk-persis-ws/child                    |
| ---------------------------- | ------------------------------------- | -------------------------------------- |
| create(/zk-persis-ws)        | NodeCreated (exists、getData监听)     | 无                                     |
| delete(/zk-persis-ws)        | NodeDeleted (exists、getData监听)     | 无                                     |
| setData(/zk-persis-ws)       | NodeDataChanged (exists、getData监听) | 无                                     |
| create(/zk-persis-ws/child)  | NodeChildrenChanged (getChildren监听) | NodeCreated (exists、getData监听)      |
| delete(/zk-persis-ws/child)  | NodeChildrenChanged (getChildren监听) | NodeDeleted (exists、getData监听)      |
| setData(/zk-persis-ws/child) |                                       | NodeDataChanged  (exists、getData监听) |


#### 2.5 事件机制的 API使用

```java
	private void watchExist(){
        open();
        exist();//下面的执行创建的时候会触发这个Watcher事件
        doCreate();
    }

    private Stat exist(){
        try {
           // Stat stat = zooKeeper.exists("/vison", true);使用boolean 默认用new Zookeeper中的的事件监听（全局事件）
            Stat stat = zooKeeper.exists("/vison", new Watcher() {
                public void process(WatchedEvent event) {
                    //doSomething
                    System.out.println(event.getType()+","+event.getPath());
                    //如果还需要监听，那么这里需要在使用getData，exists，getChildren方法
                }
            });
            return stat;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
```

### 3. Curator的使用
&emsp;&emsp;Curator是Netflix公司开源的一个Zookeeper客户端，与Zookeeper提供的原生客户端相比，Curator的抽象层次更高，简化了Zookeeper客户端的开发量。Curator采用流式风格API。

#### 3.1 Curator的CRUD

```java
	public static void main(String[] args){

        //连接
        CuratorFramework curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(CONNECT_ADDRESS)
                .sessionTimeoutMs(4000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .namespace("/curator")
                .build();
        // ExponentialBackoffRetry表示重试机制,这里有多种重试机制
        //namespace表示命名空间，可以起到隔离作用

        //这里启动，启动后才能做CRUD
        curatorFramework.start();

        //创建节点 /curator/vison/node1
        curatorFramework.create()
                .creatingParentsIfNeeded()//在父节点没有的情况下，创建父节点
                .withMode(CreateMode.PERSISTENT)
                .forPath("/vison/node1", "10".getBytes());

        //删除节点
        curatorFramework.delete()
                .deletingChildrenIfNeeded()
                .forPath("/vison/node1");

        //查询节点
        Stat stat = new Stat();
        curatorFramework.getData().storingStatIn(stat).forPath("/vison/node1");

        //更新节点信息，这里根据节点版本号更新数据，否则更新失败，类似乐观锁
        curatorFramework.setData().withVersion(stat.getVersion()).forPath("/vison/node1", "200".getBytes());

        //关闭
        curatorFramework.close();

    }
```

#### 3.2 Curator的事件监听使用
Curator提供了三种Watcher(Cache)来监听结点的变化：

- Path Cache：监视一个路径下**1）孩子结点的创建、2）删除，3）以及结点数据的更新**。产生的事件会传递给注册的PathChildrenCacheListener。
- Node Cache：监视一个结点的**1）创建、2）更新**，并将结点的数据缓存在本地。产生的事件会传递给注册的NodeCacheListener。
- Tree Cache：Path Cache和Node Cache的“合体”，监视路径下的创建、更新、删除事件，并缓存路径下所有孩子结点的数据。
```java
/**
 * 给当前节点的创建和更新添加监听事件
 * 
 */
public static void addWatchWithNodeCache(CuratorFramework curatorFramework,String path) throws Exception {

    final NodeCache nodeCache = new NodeCache(curatorFramework,path,false);
    NodeCacheListener nodeCacheListener = new NodeCacheListener() {
        public void nodeChanged() throws Exception {
            
            //1.监听当前节点被删除了
            ChildData currentData1 = nodeCache.getCurrentData();
            if (currentData1 ==null){
                System.out.println("节点被删除了:");
                return;
            }
            
            //2.监听当前节点数据变化
            System.out.println("data changed :"+ nodeCache.getCurrentData().getPath());
        }
    };

    nodeCache.getListenable().addListener(nodeCacheListener);
    nodeCache.start(false);
}

/**
 * 子节点的创建，删除，更新的监听事件
 * @param curatorFramework
 * @param path
 * @throws Exception
 */
public static void addWatchWithPathChildrenCache(CuratorFramework curatorFramework,String path) throws Exception {
    PathChildrenCache pathChildrenCache = new PathChildrenCache(curatorFramework, path, true);
    PathChildrenCacheListener pathChildrenCacheListener = new PathChildrenCacheListener() {
        public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent pathChildrenCacheEvent) throws Exception {
            System.out.println("Receive Event ; "+ pathChildrenCacheEvent.getType());
        }
    };
    pathChildrenCache.getListenable().addListener(pathChildrenCacheListener);
    pathChildrenCache.start(PathChildrenCache.StartMode.NORMAL);
}

/**
 * 综合监听事件，包含当前节点和子节点的变化
 * @param curatorFramework
 * @param path
 * @throws Exception
 */
public static void addWatchWithTreeCache(CuratorFramework curatorFramework,String path) throws Exception {
    TreeCache treeCache = new TreeCache(curatorFramework, path);
    TreeCacheListener treeCacheListener = new TreeCacheListener() {
        public void childEvent(CuratorFramework client, TreeCacheEvent event) throws Exception {
            System.out.println(event.getType() + ":" + event.getData().getPath());
        }
    };

    treeCache.getListenable().addListener(treeCacheListener);
    treeCache.start();
}
```