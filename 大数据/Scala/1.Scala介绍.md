## 一、 概述

### 1.为什么学习Scala

- 1）Scala 新一代内存级大数据计算框架，是大数据的重要内容。
- 2）Spark就是使用Scala编写的。因此**为了更好的学习**Spark, **需要掌握**Scala这门语言。
- 3）Spark的兴起，带动Scala语言的发展！



### 2.Scala发展历史

​		联邦理工学院的马丁·奥德斯基（Martin Odersky）于2001年开始设计Scala。

​		马丁·奥德斯基是编译器及编程的狂热爱好者，长时间的编程之后，希望发明一种语言，能够让写程序这样的基础工作变得高效，简单。所以当接触到JAVA语言后，对JAVA这门便携式，运行在网络，且存在垃圾回收的语言产生了极大的兴趣，所以决定将函数式编程语言的特点融合到JAVA中，由此发明了两种语言（Pizza & Scala）

Pizza和Scala极大地推动了Java编程语言的发展。

- jdk5.0 的泛型，for循环增强，自动类型转换等，都是从Pizza 引入的新特性。

- jdk8.0 的类型推断，Lambda表达式就是从Scala引入的特性。

**Jdk5.0**和Jdk8.0的编辑器就是马丁 奥德斯基写的，因此马丁·奥德斯基一个人的战斗力抵得上一个Java开发团队。



### 3. Scala和Java的关系

一般来说，学Scala的人，都会Java，而Scala是基于Java的，因此我们需要将Scala和Java以及JVM 之间的关系搞清楚。

![wTwySe.png](https://s1.ax1x.com/2020/09/20/wTwySe.png)



```java
public void main(String[] args){

	Predef.println("hello"); // 可以通过Predef调用scala类库
}
```



### 4. Scala语言特点

​		Scala是一门以Java虚拟机（JVM）为运行环境并将**面向对象**和**函数式编程**的最佳特性结合在一起的**静态类型编程语言**。

1）Scala是一门多范式的编程语言，Scala支持**面向对象和函数式编程**。

2）Scala源代码（.scala）会被编译成Java字节码（.class），然后运行于JVM之上，**并可以调用现有的**Java类库，实现两种语言的无缝对接。

3）Scala单作为一门语言来看，非常的**简洁高效。**

4）Scala在设计时，马丁·奥德斯基是参考了Java的设计思想，可以说Scala是源于Java，同时马丁·奥德斯基也加入了自己的思想，将函数式编程语言的特点融合到Java中, 因此，对于学习过Java的同学，只要在学习Scala的过程中，搞清楚Scala和Java相同点和不同点，就可以快速的掌握Scala这门语言。



## 二、安装使用

### 1.插件离线安装步骤

- 1）建议将该插件scala-intellij-bin-2017.2.6.zip文件，放到Scala的安装目录E:\02_software\scala-2.11.8下，方便管理。并配置环境变量
- 2）将scala插件安装到idea



### 2.项目创建

- 步骤1：file->new project -> 选择Maven，创建项目

- 默认下，maven不支持Scala的开发，需要引入Scala框架。

  右键项目点击-> add framework support... ，然后选择Scala

- 右键main目录->创建一个diretory -> 写个名字（比如scala）->右键scala目录->mark directory ->选择source root即可。和java分开，项目中可能java和scala分开





## 三、Scala程序基本结构

### 1.初步结构

```scala
class Hello {

  /*
  定义变量：
  val/var 变量名:变量类型 = 变量值
  */
  val a: Int = 1

  /*
  定义方法：
  def 函数名(参数名:参数类型):返回值类型={方法体}
  */
  def hello(arg: String): Unit = {
    println(arg)
  }
}
```

```scala
object Hello {

  /*
  Scala程序的入口
  */
  def main(args: Array[String]): Unit = {
    println("hello,scala")
  }

  /*
  完全面向对象：scala完全面向对象，故scala去掉了java中非面向对象的元素，如static关键字，void类型
  1.static
  scala无static关键字，由object实现类似静态方法的功能（类名.方法名），object关键字和class的关键字定义方式相同，但作用不同。class关键字和java中的class关键字作用相同，用来定义一个类；object的作用是声明一个单例对象，object后的“类名”可以理解为该单例对象的变量名。
  2.void
  对于无返回值的函数，scala定义其返回值类型为Unit类
  */
}
```



### 2.注意事项

- 1）Scala源文件以“.scala" 为扩展名。

- 2）Scala程序的执行入口是object中的main()函数。

- 3）Scala语言严格区分大小写。

- 4）Scala方法由一条条语句构成，每个语句后不需要分号（Scala语言会在每行后自动加分号）。（至简原则）

- 5）如果在同一行有多条语句，除了最后一条语句不需要分号，其它语句需要分号。





