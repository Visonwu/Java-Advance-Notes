

​	

![集合框架图](http://ww1.sinaimg.cn/large/b8a27c2fgy1g1aqnhz44uj20zg0daq4a.jpg)

# 1.ArrayList

- 默认初始大小是10，扩容按照1.5 扩容

`int newCapacity = oldCapacity + (oldCapacity >> 1);`

   

- 也可以自定义初始化大小`ArrayList(int initialCapacity)`

    如果给定的大小为9：那么扩容 按照9+9/2=13获取结果



## 1.1 成员变量

```java

private static final int DEFAULT_CAPACITY = 10;//数组默认初始容量
 
private static final Object[] EMPTY_ELEMENTDATA = {};//定义一个空的数组实例以供其他需要用到空数组的地方调用 
 
private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};//定义一个空数组，跟前面的区别就是这个空数组是用来判断ArrayList第一添加数据的时候要扩容多少。默认的构造器情况下返回这个空数组 
 
transient Object[] elementData;//数据存的地方它的容量就是这个数组的长度，同时只要是使用默认构造器（DEFAULTCAPACITY_EMPTY_ELEMENTDATA ）第一次添加数据的时候容量扩容为DEFAULT_CAPACITY = 10 

```



## 1.2 扩容源码

```java
 private void grow(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = elementData.length;
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        // minCapacity is usually close to size, so this is a win:
        elementData = Arrays.copyOf(elementData, newCapacity);
    }

```

# 2. LinkedList

查找某一个节点速度慢，通过遍历查找的

```java
/**
* Returns the (non-null) Node at the specified element index.
*/
Node<E> node(int index) {
    // assert isElementIndex(index);

    if (index < (size >> 1)) {
        Node<E> x = first;
        for (int i = 0; i < index; i++)
            x = x.next;
        return x;
    } else {
        Node<E> x = last;
        for (int i = size - 1; i > index; i--)
            x = x.prev;
        return x;
    }
}
```

# 3. Vector

​     它是ArrayList的安全版本，比较老旧

​       扩容是按照两倍扩容，当然也可以自定义扩容大小 `capacityIncrement`

```java
private void grow(int minCapacity) {
    // overflow-conscious code
    int oldCapacity = elementData.length;
    int newCapacity = oldCapacity + ((capacityIncrement > 0) ?
                                     capacityIncrement : oldCapacity);
    if (newCapacity - minCapacity < 0)
        newCapacity = minCapacity;
    if (newCapacity - MAX_ARRAY_SIZE > 0)
        newCapacity = hugeCapacity(minCapacity);
    elementData = Arrays.copyOf(elementData, newCapacity);
}
```



# 4. Stack 栈

他是Vecor的子类，也是安全操作，但是效率低

