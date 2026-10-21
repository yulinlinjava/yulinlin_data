# 登录上下文生命周期

[文档首页](../README.md) / [接口安全](README.md)

线程身份恢复、请求结束清理与异步边界。

## 本页导航

- [登录上下文与请求生命周期](#登录上下文与请求生命周期)

## 登录上下文与请求生命周期

```java
LoginResult result = SecurityUtil.loginByUsername(username, password, "web");
// HTTP 登录接口可返回 R.ok(result)，JSON 字段为 token、expiresAt、user。
IUserDetails user = SecurityContext.requireUser();
String userId = SecurityContext.getUserId();
String token = SecurityContext.getToken();
boolean loggedIn = SecurityContext.isLogin();
```

JWT 前端后续请求携带 `Authorization: Bearer <token>`。内置 LoginContextInterceptor 在限流和 Controller 之前清理旧身份，再调用 `ISecurityManager.resolveSession(token)`，验证有效期和返回 Token 一致性，然后设置 ThreadLocal。JWT 在正常请求中只验签一次，Controller、Service 和权限 AOP 读取同一用户快照。自己实现管理器且使用此拦截器时也必须提供 resolveSession。

**Sa-Token 接入不同**：自动选择 SaTokenLoginInterceptor，优先沿用 `sa-token.*` 的原生 Token 读取规则，没有原生凭证时兼容 Bearer。SecurityContext 读取时重新核对当前原生身份及会话状态，同一请求复用用户资料；因此直接 StpUtil.login/logout、重新登录另一个账号或踢人后，后续读取不会继续使用旧身份。详见[Sa-Token 接入](sa-token.md)。

**Spring Security 接入**：请求恢复继续由原有过滤器链负责，security-spring 读取 SecurityContextHolder 的已认证身份并映射为安全用户；匿名和未认证对象不放行。原生登录、注销、切换身份后自动跟随，也支持 loginByUsername 委托 AuthenticationManager 并保存 Session 身份，默认不需要 UserStore。它可以没有 Token，getToken() 返回 null；身份有效期由 Spring 管理。见 [Spring 接入](spring.md)。

未携带对应实现支持的凭证时按匿名处理，是否允许访问由原有权限注解决定；有非法格式、过期或无效 Token 时返回 401（包括公开接口），重新登录时客户端应移除旧 Token。OPTIONS 不解析 Token。只使用默认匿名管理器的项目不接管其他 Authorization 格式。

正常完成、异常完成时通过 afterCompletion 调用 ThreadLocal.remove()；preHandle 自身失败也立即清理。异步 MVC 的 afterConcurrentHandlingStarted 清理原请求线程，后续 MVC redispatch 会重新解析 Token，但 Callable、DeferredResult 或线程池的业务线程不会自动继承身份。不要使用 InheritableThreadLocal。后台任务需要的用户 ID 显式作为参数传递；不能把客户端声明的 userId 直接变成可信身份。

请求结束只清理线程。JWT logout 也只清理当前线程，客户端必须自行删除 Token；随后再次携带该 JWT 仍可通过校验直到过期或用户不允许访问。Sa-Token logout 则注销服务端会话并清理线程。refresh 当前用户时重新验证凭证并加载最新用户，更新当前线程快照，不改变 JWT 的签发时间和有效期；Sa-Token 的活跃超时策略按其自身配置执行。其他请求已加载的快照不会被跨线程强制替换，长请求必要时应主动复核。

JWT / Sa-Token 非 HTTP 使用后必须显式清理；Spring 账号密码入口要求当前 Servlet 请求/响应。后台任务通常直接传业务 userId，不必额外登录：

```java
try {
    SecurityUtil.loginByUsername(serviceUsername, servicePassword, "job"); // 确实需要登录时也必须认证
    // 当前线程业务
} finally {
    SecurityContext.clear(); // 清理当前线程，不改变凭证本身或外部会话状态
}
```

SecurityContext.set/clear 是供可信服务端认证实现和生命周期管理使用的入口。clear 只删除框架快照：使用 Sa-Token 时，只要原生会话仍有效，再次读取可自动恢复，不等于退出登录。原生注销请调用 StpUtil.logout() 或 SecurityUtil.logout()。外部身份桥接不会信任客户端直接提交的用户资料。

LoginSession/LoginResult 的 toString 不打印 Token 和用户资料；仍不得把完整登录响应、请求头或密码写入日志。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
