



## 1. Java Security 技术

Java SE - Java Security

- **加密/解密**----Oracle Java 算法禁运 - Java Security 扩展
  - 古巴、朝鲜、伊朗、叙利亚

- **JVM 安全**

  - Java 安全沙箱
    - 权限 API - Permission
      - 属性权限 - PropertyPermission
      - 运行时权限 - RuntimePermission
        - ClassLoader 使用 - getClassLoader
      - 反射权限 - ReflectPermission
    - 权限配置
      - java.policy - Java Permission 授权文件
      - java.security - Java 安全配置扩展文件
  - Java 身份表示 - java.security.Principal
  - Java 安全异常 - java.lang.SecurityException（运行时异常）

  - Java 安全管理器 -   java.lang.SecurityManager
    - Java 安全校验方法 - checkPermission(java.security.Permission)
  - Java 安全入口控制器 - java.security.AccessController
    - Java 安全校验方法 -   checkPermission(java.security.Permission)
    - Java 鉴权方法 -    doPrivileged(java.security.PrivilegedAction) 以及重载

---

> ClassLoader 安全

- Bootstrap ClassLoader (用户 Java 代码无法获取，null) - 无法re-define Class 结构
- 每个 ClassLoader 会有自己加载的 Classes -findLoadedClass
  java.lang.String 无法被修改
  - System ClassLoader (用户 Java 代码可通过ClassLoader#getSystemClassLoader) - 允许 re-define
    Class 结构（需要 Java Security 授权）
  - App ClassLoader（用户 Java 代码可以自定义ClassLoader）



## 2. Security技术选型

### 2.1  方案一：Servlet Security

**优势**

- 标准支持
  - Java Security 标准
  - Servlet 标准
- 服务端安全支持 
  - Authentication（鉴定）
  - Authorization（授权）

**不足**

- 客户端安全 不支持 
  - CSRF、XSS、JSON等
- 服务端安全 支持受限
  - Session 管理有限
  - 加密困难
- 扩展困难



### 2.2 方案二：Apache Shiro

**优势**

- Web 整合
  - 兼容 Servlet 标准
  - 非 Servlet 框架（Play）
- 服务端安全支持 
  - Authentication（鉴定）
  - Authorization（授权）
  - Session 管理
  - 加密
- 易于扩展

**不足**

- 客户端安全 不支持 
  - CSRF、XSS、JSON等



### 2.3 方案三：Spring Security

**优势**

- 标准支持
  - 兼容 Java Security 标准
  - 兼容 Servlet 标准
- 服务端安全支持 
  - Authentication（鉴定）
  - Authorization（授权）
  - Session 管理
  - 加密
- 客户端安全支持
  - CSRF、CSP、HSTS 等
  - 易于扩展

**不足**

- 客户端安全 不足
  - XSS、JSON、Referer等

### 2.4 Web 客户端 安全功能比较

| **方案**        | **XSS** | **JSON** | **Cache Control** | **Content Type Options** | **HTTP Strict Transport Security (HSTS)** | **HTTP Public Key Pinning (HPKP)** | **X-Frame-Options** | **X-XSS** | **Content Security Policy (CSP)** |
| --------------- | ------- | -------- | ----------------- | ------------------------ | ----------------------------------------- | ---------------------------------- | ------------------- | --------- | --------------------------------- |
| Servlet         | X       | X        | X                 | X                        | X                                         | X                                  | X                   | X         | X                                 |
| Apache Shiro    | X       | X        | X                 | X                        | X                                         | X                                  | X                   | X         | X                                 |
| Spring Security | X       | X        | O                 | O                        | O                                         | O                                  | O                   | O         | O                                 |

### 2.5 Web 服务端 安全功能比较

| **方案**        | **Cross Site Request Forgery (CSRF)** | **Authentication** | **Authorization** | **Cryptography** | **Session Management** | **Referer** | **Domain** |
| --------------- | ------------------------------------- | ------------------ | ----------------- | ---------------- | ---------------------- | ----------- | ---------- |
| Servlet         | X                                     | X                  | X                 | X                | X                      | X           | X          |
| Apache Shiro    | X                                     | O                  | O                 | O                | O                      | X           | X          |
| Spring Security | O                                     | O                  | O                 | O                | O                      | X           | X          |



**选型方案：Spring Security**

再次分析

- 功能完备**，仍需扩展**
  - 页面 XSS 过滤
  - JSON 内容转义（Escape）
  - Referer 域名检测
  - 安全域名检测
- 整合强大**，部分配置**
  - Servlet 容器
  - Spring MVC
  - Spring Boot
- 扩展支持，**不易理解**
  - WebSecurityConfigurer
  - ObjectPostProcessor