# 1. 官网

- **1.1 JDK8**

​	官网:https://docs.oracle.com/javase/8/

The relation of JDK/JRE/JVM
	Reference -> Developer Guides -> 定位到:https://docs.oracle.com/javase/8/docs/index.html

![lPeo2n.png](https://s2.ax1x.com/2019/12/24/lPeo2n.png)



# 2. 源文件到类文件

## 2.1 **源码**

```java
class Person{
    private String name;
    private int age;
    private static String address;
    private final static String hobby="Programming";
    
    public void say(){
    	System.out.println("person say...");
    }
    
    public int calc(int op1,int op2){
   	 return op1+op2;
    }
    
}
```

> 编译: javac Person.java ---> Person.class



## 2.2 编译过程

> Person.java -> 词法分析器 -> tokens流 -> 语法分析器 -> 语法树/抽象语法树 -> 语义分析器
> -> 注解抽象语法树 -> 字节码生成器 -> Person.class文件



## 2.3 类文件(Class文件)

官网：The class File Format :https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html

class文件的16进制文件如下：

```texg
cafe babe 0000 0034 0027 0a00 0600 1809
0019 001a 0800 1b0a 001c 001d 0700 1e07
001f 0100 046e 616d 6501 0012 4c6a 6176
612f 6c61 6e67 2f53 7472 696e 673b 0100
0361 6765 0100 0149 0100 0761 6464 7265
......
```



具体内容分别对应如下所示：

```jvm
//文件格式：
ClassFile {
	u4 magic; // 魔法数字，表明当前文件是.class文件，固定0xCAFEBABE
	u2 minor_version; // 分别为Class文件的副版本和主版本
	u2 major_version;
	u2 constant_pool_count; // 常量池计数
	cp_info constant_pool[constant_pool_count-1]; //
	u2 access_flags; // 类访问标识
	u2 this_class; // 当前类
	u2 super_class; // 父类
	u2 interfaces_count; // 实现的接口数
	u2 interfaces[interfaces_count]; // 实现接口信息
	u2 fields_count; // 字段数量
	field_info fields[fields_count]; // 包含的字段信息
	u2 methods_count; // 方法数量
	method_info methods[methods_count]; // 包含的方法信息
	u2 attributes_count; // 属性数量
	attribute_info attributes[attributes_count]; // 各种属性
}
```



# 3.类文件到虚拟机(类加载机制)

**加载流程如下：**

![lPuxPg.png](https://s2.ax1x.com/2019/12/24/lPuxPg.png)

## 3.1 装载(Load)

​	查找和导入class文件

```text
(1)通过一个类的全限定名获取定义此类的二进制字节流
(2)将这个字节流所代表的静态存储结构转化为方法区的运行时数据结构
(3)在Java堆中生成一个代表这个类的java.lang.Class对象，作为对方法区中这些数据的访问入口
```



## 3.2 链接(Link)

### 3.2.1 验证(Verify)

​	保证被加载类的正确性

- 文件格式验证
  元数据验证
  字节码验证
  符号引用验证

### 3.2.2 准备(Prepare)

​	为类的静态变量分配内存，并将其初始化为默认值；这些内存都将在方法区中进行分配

```text
1)这里的赋值指的是赋值null,0,等。比如：
	public static int a = 11;在准备阶段只是给a赋值为0，而赋值11是需要初始化阶段才会发生的。

2）但是如果是final修饰的常量则会直接赋值：
	public static final  int b = 12;这里直接赋值b为12。
```

### 3.2.3 解析(Resolve)

​	把类中的符号引用转换为直接引用；（比如String s ="aaa",转化为 s的地址指向“aaa”的地址）



## 3.3 初始化(Initialize)

​	对类的**静态变量，静态代码块**执行初始化操作

- 初始化阶段是执行类构造器方法（<clinit>）的过程。类构造器方法是由编译器自动收集类中的所有类变量的赋值动作和静态语句块（static块）中的语句合并产生的。这里会初始化准备阶段static修饰的变量。

- 当初始化一个类的时候，如果发现其父类还没有进行过初始化，则需要先初始化其父类的初始化；虚拟机会保证一个类的构造器方法在多线程环境中被正确加锁和同步

  ****

**初始化发生时机：**

**1）主动对类进行引用，才会触发对类的初始化**

有且只有如下5种情况才会对类进行初始化

- 遇到new，getstatic、putstatic或者invokestatic这4条字节码指令时，如果类没有进行初始化，那么会触发初始化。
        **常见的场景：**new关键字实例化对象；读取或者设置一个类的静态字段（final修饰的除外，以及在编译的期间就将结果放入常量池的的静态字段除外）；以及调用一个类的静态方法。

- 使用java.lang.reflect包的方法对类进行反射调用的时候，如果类没有进行初始化，也会先触发类的初始化
- 类初始化一个类的时候，如果发现父类还没有进行初始化，则需要先触发父类的初始化
- 当虚拟机启动的时候，用户需要制定一个执行的主类（main方法这个类），虚拟机会先初始化这个类
- 当使用jdk1.7的动态语言支持时，如果一个java.lang.invoke.MethodHandle实例最后解析结REF_getStatic、REF_putStatic、REF_invokeStatic的方法句柄，并且这个方法句柄所对应的类没有进行过初始化，那么需要先触发初始化
  

**2）被动引用不会触发初始化**

- 通过子类引用父类静态字段，不会导致子类初始化

- 通过数组定义引用类，不会触发此类的初始化
- 常量在编译阶段会存入调用类的常量池中，本质上并没有直接引用到定义常量的类，因此不会触发定义常量的类的初始化



# 4.类装载器ClassLoader

> 在装载(Load)阶段，其中第(1)步:通过类的全限定名获取其定义的二进制字节流，需要借助类装载
> 器完成，顾名思义，就是用来装载Class文件的。
>
> - 通过一个类的全限定名获取定义此类的二进制字节流



## 4.1 分类

- **1）Bootstrap ClassLoader** 负责加载$JAVA_HOME中 jre/lib/rt.jar 里所有的class或Xbootclassoath选项指定的jar包。由C++实现，不是ClassLoader子类。
- **2）Extension ClassLoader** 负责加载java平台中扩展功能的一些jar包，包括$JAVA_HOME中
  jre/lib/*.jar 或 -Djava.ext.dirs指定目录下的jar包。
- **3）App ClassLoader** 负责加载classpath中指定的jar包及 Djava.class.path 所指定目录下的类和
  jar包。
- **4）Custom ClassLoader** 通过java.lang.ClassLoader的子类自定义加载class，属于应用程序根据
  自身需要自定义的ClassLoader，如tomcat、jboss都会根据j2ee规范自行实现ClassLoader。



## 4.2 加载原则

![lP17H1.png](https://s2.ax1x.com/2019/12/24/lP17H1.png)

​	检查某个类是否已经加载：顺序是自底向上，从Custom ClassLoader到BootStrap ClassLoader逐层检
查，只要某个Classloader已加载，就视为已加载此类，保证此类只所有ClassLoader加载一次。

**加载的顺序：**加载的顺序是自顶向下，也就是由上层来逐层尝试加载此类。



### 4.2.1 双亲委派机制

> 定义：如果一个类加载器在接到加载类的请求时，它首先不会自己尝试去加载这个类，而是把
> 这个请求任务委托给父类加载器去完成，依次递归，如果父类加载器可以完成类加载任务，就
> 成功返回；只有父类加载器无法完成此加载任务时，才自己去加载。
>
> 优势：Java类随着加载它的类加载器一起具备了一种带有优先级的层次关系。比如，Java中的
> Object类，它存放在rt.jar之中,无论哪一个类加载器要加载这个类，最终都是委派给处于模型
> 最顶端的启动类加载器进行加载，因此Object在各种类加载环境中都是同一个类。如果不采用
> 双亲委派模型，那么由各个类加载器自己取加载的话，那么系统中会存在多种不同的Object
> 类。
> 破坏：可以继承ClassLoader类，然后重写其中的loadClass方法，其他方式大家可以自己了解
> 拓展一下。



**ClassLoader的源码：**

```java
//如下逻辑是，先通过findClass查找该类是否已经加载了，如果没有加载再通过父类的laodClass去加载，
//如果父类加载失败了，在调用自身的findClass去加载。
//当然如果父类为null,说明此时父类是BootStrap,所以调用native方法findBootstrapClassOrNull来加载   
protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException{
    synchronized (getClassLoadingLock(name)) {
        // First, check if the class has already been loaded
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            long t0 = System.nanoTime();
            try {
                if (parent != null) {
                    c = parent.loadClass(name, false);
                } else {
                    c = findBootstrapClassOrNull(name);
                }
            } catch (ClassNotFoundException e) {
                // ClassNotFoundException thrown if class not found
                // from the non-null parent class loader
            }

            if (c == null) {
                // If still not found, then invoke findClass in order
                // to find the class.
                long t1 = System.nanoTime();
                c = findClass(name);

                // this is the defining class loader; record the stats
                sun.misc.PerfCounter.getParentDelegationTime().addTime(t1 - t0);
                sun.misc.PerfCounter.getFindClassTime().addElapsedTimeFrom(t1);
                sun.misc.PerfCounter.getFindClasses().increment();
            }
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }
}
```



### 4.2.2 Class.forname()与ClassLoader.loadClass()

```text
1) Class.forname():是一个静态方法,最常用的是Class.forname(String className);根据传入的类的全限定名返回一个Class对象.该方法在将Class文件加载到内存的同时,会执行类的初始化.
　　如: Class.forName("com.vison.User");

2) ClassLoader.loadClass():这是一个实例方法,需要一个ClassLoader对象来调用该方法,该方法将Class文件加载到内存时,并不会执行类的初始化,直到这个类第一次使用时才进行初始化.该方法因为需要得到一个ClassLoader对象,所以可以根据需要指定使用哪个类加载器.

  如:ClassLoader classLoader=user.getClass().getClassLoader();
	 Class<Admin> class = classLoader.loadClass("com.vison.Admin");
```



# 5.运行时数据区(Run-Time Data Areas)

> 在装载阶段的第(2),(3)步可以发现有运行时数据，堆，方法区等名词
>
> (2)将这个字节流所代表的静态存储结构转化为方法区的运行时数据结构
>
> (3)在Java堆中生成一个代表这个类的java.lang.Class对象，作为对方法区中这些数据的访问入口
>
> ​	说白了就是类文件被类装载器装载进来之后，类中的内容(比如变量，常量，方法，对象等这些数
> 据得要有个去处，也就是要存储起来，存储的位置肯定是在JVM中有对应的空间)



## 5.1 官网概括
​	官网： https://docs.oracle.com/javase/specs/jvms/se8/html/index.html

**图解：**

　　![lPGouQ.png](https://s2.ax1x.com/2019/12/24/lPGouQ.png)



## 5.2 常规理解

### 5.2.1 方法区

- 方法区是各个线程共享的内存区域，在虚拟机启动时创建。用于存储已被虚拟机加载的类信息、常量、静态变量、即时编译器编译后的代码等数据。

- 虽然Java虚拟机规范把方法区描述为堆的一个逻辑部分，但是它却又一个别名叫做Non-Heap(非堆)，目
  的是与Java堆区分开来。当方法区无法满足内存分配需求时，将抛出OutOfMemoryError异常。



> 此时回看装载阶段的第2步：(2)将这个字节流所代表的静态存储结构转化为方法区的运行时数据结构
> 如果这时候把从Class文件到装载的第(1)和(2)步合并起来理解的话，可以画个图

![lPJdVs.png](https://s2.ax1x.com/2019/12/24/lPJdVs.png)

> **值得说明的**
> (1)方法区在JDK 8中就是Metaspace，在JDK6或7中就是Perm Space
>
> (2)Run-Time Constant Pool
> 		Class文件中除了有类的版本、字段、方法、接口等描述信息外，还有一项信息就是常量池，用于存放编译时期生成的各种字面量和符号引用，这部分内容将在类加载后进入方法区的运行时常量池中存放。



### 5.2.2 堆

​	Java堆是Java虚拟机所管理内存中最大的一块，在虚拟机启动时创建，被所有线程共享。
Java对象实例以及数组都在堆上分配。

> 此时回看装载阶段的第3步：(3)在Java堆中生成一个代表这个类的java.lang.Class对象，作为对方
> 法区中这些数据的访问入口

![lPaa4A.png](https://s2.ax1x.com/2019/12/24/lPaa4A.png)

### 5.2.3 Java虚拟机栈

> 经过上面的分析，类加载机制的装载过程已经完成，后续的链接，初始化也会相应的生效。
> 假如目前的阶段是初始化完成了，后续做啥呢？肯定是Use使用咯，不用的话这样折腾来折腾去
> 有什么意义？那怎样才能被使用到？换句话说里面内容怎样才能被执行？比如通过主函数main调
> 用其他方法，这种方式实际上是main线程执行之后调用的方法，即要想使用里面的各种内容，得
> 要以线程为单位，执行相应的方法才行。
> 那一个线程执行的状态如何维护？一个线程可以执行多少个方法？这样的关系怎么维护呢？

​		虚拟机栈是一个线程执行的区域，保存着一个线程中方法的调用状态。换句话说，一个Java线程的运行
状态，由一个虚拟机栈来保存，所以虚拟机栈肯定是线程私有的，独有的，随着线程的创建而创建。

- 每一个被线程执行的方法，为该栈中的栈帧，即每个方法对应一个栈帧。

- 调用一个方法，就会向栈中压入一个栈帧；一个方法调用完成，就会把该栈帧从栈中弹出。

**栈帧包含内容**：

> - **局部变量表**：表示当前栈帧（方法）中的变量，默认第0个是this（非静态方法）
> - **操作数栈**：也是一个栈，用来操作当前栈帧中的数据运算。
> - **动态链接**：表示动态决定引用的地址，比如我们经常用的多态
> - **出口**：表示方法的出口地址



![lPa03t.png](https://s2.ax1x.com/2019/12/24/lPa03t.png)





### 5.2.4 Pc计数器

> 我们都知道一个JVM进程中有多个线程在执行，而线程中的内容是否能够拥有执行权，是根据CPU调度来的。
>
> 假如线程A正在执行到某个地方，突然失去了CPU的执行权，切换到线程B了，然后当线程A再获得CPU执行权的时候，怎么能继续执行呢？这就是需要在线程中维护一个变量，记录线程执行到的位置。

> ​	程序计数器占用的内存空间很小，由于Java虚拟机的多线程是通过线程轮流切换，并分配处理器执行时间的方式来实现的，在任意时刻，一个处理器只会执行一条线程中的指令。因此，为了线程切换后能够恢复到正确的执行位置，每条线程需要有一个独立的程序计数器(线程私有)。
>
> 如果线程正在执行Java方法，则计数器记录的是正在执行的虚拟机字节码指令的地址；
>
> 如果正在执行的是Native方法，则这个计数器为空。



### 5.2.5 Native Method Stacks(本地方法栈)
​	如果当前线程执行的方法是Native类型的，这些方法就会在本地方法栈中执行。

​		而本地方法栈为native方法服务，我们在看源码的时候经常了一看到用native关键字修饰的方法，这种方法的实现是用c/c++实现的，我们在平时是看不到他的源码实现的。

