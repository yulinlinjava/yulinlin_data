# 登录上下文生命周期

[文档首页](../README.md) / [接口安全](README.md)

线程身份恢复、请求结束清理与异步边界。

## 本页导航

- [登录上下文与请求生命周期](#登录上下文与请求生命周期)

## 登录上下文与请求生命周期

```java
LoginResult result = SecurityUtil.login(userId, "web"); // 前提：业务已验证身份
// HTTP 登录接口可返回 R.ok(result)，JSON 字段为 token、expiresAt、user。
IUserDetails user = SecurityContext.requireUser();
String userId = SecurityContext.getUserId();
String token = SecurityContext.getToken();
boolean loggedIn = SecurityContext.isLogin();
```

前端后续请求携带 `Authorization: Bearer <token>`。内置 LoginContextInterceptor 在限流和 Controller 之前清理旧身份，再调用 `ISecurityManager.resolveSession(token)`，验证有效期和返回 Token 一致性，然后设置 ThreadLocal。一个正常请求只解析一次 Token，Controller、Service 和权限 AOP 读取同一用户快照。JWT 和 Sa-Token 实现已提供 resolveSession；自己实现管理器且使用此拦截器时也必须提供该能力。

没有 Authorization 时按匿名处理，是否允许访问由原有权限注解决定；有非法格式、过期或无效 Token 时返回 401（包括公开接口），重新登录时客户端应移除旧 Token。OPTIONS 不解析 Token。只使用默认匿名管理器的项目不接管其他 Authorization 格式。

正常完成、异常完成时通过 afterCompletion 调用 ThreadLocal.remove()；preHandle 自身失败也立即清理。异步 MVC 的 afterConcurrentHandlingStarted 清理原请求线程，后续 MVC redispatch 会重新解析 Token，但 Callable、DeferredResult 或线程池的业务线程不会自动继承身份。不要使用 InheritableThreadLocal。后台任务需要的用户 ID 显式作为参数传递；不能把客户端声明的 userId 直接变成可信身份。

请求结束只清理线程。JWT logout 也只清理当前线程，客户端必须自行删除 Token；随后再次携带该 JWT 仍可通过校验直到过期或用户不允许访问。Sa-Token logout 则注销服务端会话并清理线程。refresh 当前用户时重新验证凭证并加载最新用户，更新当前线程快照，不改变 JWT 的签发时间和有效期；Sa-Token 的活跃超时策略按其自身配置执行。其他请求已加载的快照不会被跨线程强制替换，长请求必要时应主动复核。

非 HTTP 使用后必须显式清理：

```java
try {
    SecurityUtil.login(userId, "job"); // 仅限服务端已验证身份的场景
    // 当前线程业务
} finally {
    SecurityContext.clear(); // 清理当前线程，不改变凭证本身或外部会话状态
}
```

SecurityContext.set/clear 是供可信服务端认证实现和生命周期管理使用的入口。LoginSession/LoginResult 的 toString 不打印 Token 和用户资料；仍不得把完整登录响应、请求头或密码写入日志。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
