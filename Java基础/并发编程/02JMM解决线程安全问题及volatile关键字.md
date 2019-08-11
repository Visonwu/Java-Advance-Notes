# 1. JMM解决原子性、可见性、有序性的问题

​		在Java中提供了一系列和并发处理相关的关键字，比如volatile、Synchronized、final、juc(java.util.concurrent)等，这些就是Java内存模型封装了底层的实现后提供给开发人员使用的关键字，在开发多线程代码的时候，我们可以直接使用synchronized等关键词来控制并发，使得我们不需要关心底层的编译器优化、缓存一致性的问题了，所以在Java内存模型中，除了定义了一套规范，还提供了开放的指令在底层进行封装后，提供给开发人员使用。

## 1.1 原子性保障

&emsp;&emsp;在java中提供了两个高级的字节码指令`monitorenter`和`monitorexit`，在Java中对应的**Synchronized**来保证代码块内的操作是原子的。

## 1.2 可见性

&emsp;&emsp;Java中的volatile关键字提供了一个功能，那就是被其修饰的变量在被修改后可以立即同步到主内存，被其修饰的变量在每次是用之前都从主内存刷新。因此，可以使用volatile来保证多线程操作时变量的可见性。

&emsp;&emsp;除了**volatile，Java中的synchronized和final**两个关键字也可以实现可见性.

## 1.3 有序性

&emsp;&emsp;在Java中，可以使用**synchronized和volatile**来保证多线程之间操作的有序性。实现方式有所区别：`volatile`关键字会禁止指令重排。synchronized关键字保证同一时刻只允许一条线程操作。

# 2. volatile如何保证可见性

**指令查看方法：**
步骤一：下载hsdis工具 ，https://sourceforge.net/projects/fcml/files/fcml-1.1.1/hsdis-1.1.1-win32-amd64.zip/download

步骤二：解压后存放到jre目录的server路径下

步骤三：然后跑main函数，跑main函数之前，加入如下虚拟机参数：
-server -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly -
XX:CompileCommand=compileonly,*App.getInstance（替换成实际运行的代码）

&emsp;&emsp;volatile变量修饰的共享变量，在进行写操作的时候会多出一个lock前缀的汇编指令，这个指令会触发总线锁或者缓存锁，通过缓存一致性协议来解决可见性问题

## 1.保证可见性

- .将当前处理器缓存行的数据写回到系统内存
- 这个写回的内存操作会使其他CPU里缓存了该内存地址的数据无效。

&emsp;如下：当线程A写入主内存后，让线程B本地内存x值无效，让线程B重新去主内存读取数据
![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5vuzrutzyj20fn0d2tan.jpg)

## 2. volatile防止指令重排序	

&emsp;&emsp;指令重排的目的是为了最大化的提高CPU利用率以及性能，CPU的乱序执行优化在单核时代并不影响正确性，但是在多核时代的多线程能够在不同的核心上实现真正的并行，一旦线程之间共享数据，就可能会出现一些不可预料的问题。

​		指令重排序必须要遵循的原则是，不影响代码执行的最终结果，编译器和处理器不会改变存在数据依赖关系的两个操作的执行顺序，(这里所说的数据依赖性仅仅是针对单个处理器中执行的指令和单个线程中执行的操作.)这个语义，实际上就是as-if-serial语义，不管怎么重排序，单线程程序的执行结果不会改变，编译器、处理器都必须遵守as-if-serial语义

### 2.1 内存屏障

​       内存屏障需要两个问题，一个是编译器的优化乱序和CPU的执行乱序，我们可以分别使用优化屏障和内存屏障这两个机制来解决。

**1）CPU层面的乱序优化**
&emsp;&emsp;CPU的乱序执行，本质还是，由于在多CPU的机器上，每个CPU都存在cache，当一个特定数据第一次被特定一个CPU获取时，由于在该CPU缓存中不存在，就会从内存中去获取，被加载到CPU高速缓存中后就能从缓存中快速访问。当某个CPU进行写操作时，它必须确保其他的CPU已经将这个数据从他们的缓存中移除，这样才能让其他CPU安全的修改数据。显然，存在多个cache时，我们必须通过一个cache一致性协议来避免数据不一致的问题，而这个通讯的过程就可能导致乱序访问的问题，也就是运行时的内存乱序访问。

CPU层面的内存屏障是在x86的cpu中，实现了相应的内存屏障：
		写屏障(store barrier)、读屏障(load barrier)和全屏障(Full Barrier)，主要的作用是 防止指令之间的重排序、 保证数据的可见性。细节略。

**2)编译器层面指令重排序**
&emsp;JMM通过如下内存屏障指令来禁止特定类型的处理器重排序，volatile就包含有这个指令，分类如下所示：
![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5vv52xhugj20nv08gn3x.jpg)

&emsp;&emsp;在编译器层面，通过volatile关键字，取消编译器层面的缓存和重排序。保证编译程序时在优化屏障之前的指令不会在优化屏障之后执行。这就保证了编译时期的优化不会影响到实际代码逻辑顺序。
如果硬件架构本身已经保证了内存可见性，那么volatile就是一个空标记，不会插入相关语义的内存屏障。如果硬件架构本身不进行处理器重排序，有更强的重排序语义，那么volatile就是一个空标记，不会插入相关语义的内存屏障。

&emsp;在volatile字段读或者写的语句中，它前面的数据必定发生在当前语句之前，前面的语句可以发生重排序；同理，在volatile字段操作之后也同样必须发生在这个语句之后，后面的不会管他们的重排序情况。

例子：
```java
int a,b,c ;
 
volatile int v1=1;
 
volatile int v2 =2;
 
void readAndWrite(){
   a =1;       //1
   b=2;        //2
   c=3;        //3
   int i =v1;   //4 第一个volatile读
   int j =v2;    //5 第二个volatile读
   a = i+j;      // 6 普通写
   b = i+j;       //7
   c = i+j;       //8
   v1=i+1;       //9 第一个volatile写
   v2=j* 2;      //10 第二个volatile写
}
```
如上所示：1,2,3可能发生重排序，但是在4的时候，一定会保证1,2,3都是执行了的。同理5,6,都是volatile字段操作都会保证前面的都是执行了的不会让一块发生重排序；同理6,7,8可能发生重排序；

## 3. volatile原子性问题

volatile不能保证数据的原子性问题,   可以在java编译后 通过javap -c Demo.class，去查看字节码指令

例子一：
```java
//我们通过下面一个例子，对一个通过volatile修饰的值进行递增
public class Demo {
  volatile int i;
  public void incr(){
    i++; //这里有三个步骤，不能保证这个操作的原子性，会分为三个步骤：1.读取volatile变量的值到local；2.增加变量的值；3.把local的值写回让其他线程可见
 }
  public static void main(String[] args) {
    new Thread(()->{
		new Demo().incr();
	});
	int j =i; //读操作，这个可以保证前后指令的排序问题 ，具有原子性概念
	i =3;  //写操作，对其他内存是可见的，具有原子性概念
 }
}
```
例子二：
使用volatile实现单例模式
```java
Pubulic Class Singleton {
 
    private volatile static Singleton instance; //1 这里用volatile申明
 
    public static Singleton getInstance() {
        if (null == instance) {
            synchronized (Singleton.class) {
                if (null == instance) {
                    instance = new Singleton ();  //2 这里有三个操作，可能会被重排序
                }
            }
        }
        return instance;
    }

```

如上所示，单例的引用采用volatile申明，就是为了避免2处的重排序

> 问题解析：
>           instance = new Singleton() 实际是由下面三步完成的: 
> -    memory=allocate();        &emsp;&emsp;      1. //分配对象内存空间   
> -  ctorInstance(memory); &emsp;&emsp;  2.//初始化对象 
> -  instance=memory;       &emsp;&emsp;  3.//设置instance指向刚分配的内存
> <br/>
> 上面2,3可能会被重排序，如果重排序后，当A执行了线程执行到了3(2还没有执行)，B线程获取这个单例，判断不为空(已经有指向内存空间了)，但实际上并没有初始化，这里访问的也就是没有初始化的对象。如下图两个线程访问顺序：
> ![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5vv5g1xe3j20o906cgo5.jpg)
> 解决办法：
> &emsp;&emsp;   1>: 禁止重排序  所以通过把instance用volatile申明，那么当前就会禁止重排序就会避免这个问题
> &emsp;&emsp;  2>: 通过类初始化完成，即饿汉式单例模式（类加载使这个重排序对外隐藏）

## 4.volatile使用条件

您只能在有限的一些情形下使用 volatile 变量替代锁。要使 volatile 变量提供理想的线程安全，必须同时满足下面两个条件：

- 对变量的写操作不依赖于当前值，例如自增环境下不行 i++;
- 该变量没有包含在具有其他变量的不变式中
## 5.volatile应用场景

1. volatile特别适合于状态标记量 
          例如： volatile boolean flag = true;
2. 双重检查锁定-单例模式使用，如上所示
3. 其他场景可以参考：https://www.ibm.com/developerworks/cn/java/j-jtp06197.html     

## 6.Java内存模型(JMM)实现的 final重排序规则

​	参考：<https://www.cnblogs.com/jerrice/p/7278661.html>

![](http://ww1.sinaimg.cn/large/b8a27c2fgy1g5vv5qzva5j20p806qgmw.jpg)




​    