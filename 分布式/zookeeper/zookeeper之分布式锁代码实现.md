
### 1.分布式锁
#### 1.1 通过原生zookeeper封装实现
```java
package com.vison.ws.zookeeper;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class DistributedLock implements Lock, Watcher {

    private ZooKeeper zooKeeper;
    private String ROOT_LOCK="/locks";
    private String WAIT_LOCK;
    private String CURRENT_LOCK;
    private CountDownLatch countDownLatch;


    public DistributedLock() {
        try {
            zooKeeper = new ZooKeeper("127.0.0.1:2181", 3000, this);
            Stat stat = zooKeeper.exists(ROOT_LOCK, false);
            if (stat == null){
                zooKeeper.create(ROOT_LOCK,"0.".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void lock() {
        if (tryLock()){
            System.out.println(Thread.currentThread().getName()+"-->"+CURRENT_LOCK+".获取锁成功");
        }
        try {
            waitForLock(WAIT_LOCK);  //没有获取锁，则等待，知道获取锁
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean waitForLock(String pre) throws KeeperException, InterruptedException {
        Stat stat = zooKeeper.exists(pre, true);//监听当前节点的上一个节点的变化
        if (stat != null){
            System.out.println(Thread.currentThread().getName()+"-->等待锁"+pre+"的释放");
            countDownLatch = new CountDownLatch(1);
            countDownLatch.await();
            System.out.println(Thread.currentThread().getName()+"-->"+CURRENT_LOCK+".获取锁成功");
        }
        return  true;
    }

    public void lockInterruptibly() throws InterruptedException {

    }

    public boolean tryLock() {
        //创建临时有序节点
        try {
           CURRENT_LOCK = zooKeeper.create(ROOT_LOCK+"/", "0".getBytes(),
                   ZooDefs.Ids.OPEN_ACL_UNSAFE,
                   CreateMode.EPHEMERAL_SEQUENTIAL);
            System.out.println(Thread.currentThread().getName()+"-->"+CURRENT_LOCK+".尝试竞争锁");
            List<String>  children = zooKeeper.getChildren(ROOT_LOCK,false);

            SortedSet<String> sortedSet = new TreeSet();
            for (String child : children) {
                sortedSet.add(ROOT_LOCK+"/"+child);
            }
            String first = sortedSet.first();//获取子节点中最小的节点
            SortedSet<String> lessThanMe = sortedSet.headSet(CURRENT_LOCK);//获取小于自己节点的集合
            if (CURRENT_LOCK.equals(first)){  //当前节点锁和集合中最小节点相同，表示当前节点获取锁成功
                return true;
            }
            if (!lessThanMe.isEmpty()){
                WAIT_LOCK = lessThanMe.last();  //获得比当前节点更小的最后一个节点，设置给WAIT_LOCK
            }

        }catch (Exception e){

        }
        return false;
    }

    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }

    public void unlock() {
        System.out.println(Thread.currentThread().getName()+"-->"+CURRENT_LOCK+".释放锁");
        try {
            zooKeeper.delete(CURRENT_LOCK,-1);
            CURRENT_LOCK = null;
            zooKeeper.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    public Condition newCondition() {
        return null;
    }

    public void process(WatchedEvent event) {
        if (this.countDownLatch != null){
            this.countDownLatch.countDown();
        }
    }
}

```

&emsp;测试

```java
public class TestDistributedLock{

    public static void main(String[] args) throws IOException {
        final  CountDownLatch countDownLatch = new CountDownLatch(10);

        for (int i=0;i<10;i++){
            new Thread(new Runnable() {
                public void run() {
                    try {
                        countDownLatch.await();
                        DistributedLock distributedLock = new DistributedLock();
                        distributedLock.lock();
                    }catch ( Exception e){
                       e.printStackTrace();
                    }
                }
            },"Thread - "+i ).start();
            countDownLatch.countDown();
        }

        System.in.read();
        //可以通过在客户端一个一个的删除节点，观看控制台的打印情况
    }
}
```

#### 2.2 使用Curator recipe 实现

&emsp;&emsp;建议参考官网：http://curator.apache.org/    这里还有很多高级应用场景的封装实现

```java

package com.vison.ws.zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.concurrent.CountDownLatch;

public class CuratorLocksTest {

    public static void main(String[] args) throws Exception {
        CuratorFramework curatorFramework = CuratorFrameworkFactory
                .newClient("127.0.0.1:2181", new ExponentialBackoffRetry(3000, 3));
        final InterProcessLock interProcessMutex = new InterProcessMutex(curatorFramework,"/locks");
        curatorFramework.start();
        final CountDownLatch countDownLatch = new CountDownLatch(10);
        for (int i=0;i<10;i++){
            new Thread(new Runnable() {
                public void run() {
                    try {
                        countDownLatch.await();
                        interProcessMutex.acquire();
                        this.doSomething(); //做一些操作
                        interProcessMutex.release();//释放锁
                        System.out.println(Thread.currentThread().getName()+"-->释放锁");
                    }catch ( Exception e){
                        e.printStackTrace();
                    }
                }
                private void doSomething() {
                    System.out.println(Thread.currentThread().getName()+"-->获取锁");
                }
            },"Thread - "+i ).start();
            countDownLatch.countDown();
        }
        System.in.read();
    }
}

```