# 登录上下文生命周期

[文档首页](../README.md) / [接口安全](README.md)

业务只读取当前用户、Token 和认证来源；请求结束由框架清理上下文。

## 本页导航

- [登录上下文与请求生命周期](#登录上下文与请求生命周期)
- [刷新与注销](#刷新与注销)
- [异步与后台任务](#异步与后台任务)

## 登录上下文与请求生命周期

```java
import com.yulinlin.security.auth.*;

LoginResult result = SecurityUtil.login(new PasswordLogin(username, password, "web"));
// HTTP 登录接口可返回 R<LoginResult>，包含 token、expiresAt、user、authType。

IUserDetails user = SecurityContext.requireUser();
String userId = SecurityContext.getUserId();
String token = SecurityContext.getToken();
String authType = SecurityContext.getAuthType();
boolean loggedIn = SecurityContext.isLogin();
```

也可全部使用 SecurityUtil，或者注入 ISecurityManager。未登录时 getUser/getUserDetails、getUserId、getToken、getAuthType 返回 null，requireUser 抛 LoginException。会话型登录可能没有可返回的 Token，判断登录状态使用 isLogin，而不是判断 token 是否为空。

JWT 客户端在后续请求携带 `Authorization: Bearer <token>`，其他登录模块按对应专题的客户端约定携带凭证。

请求行为：

- 有有效凭证：恢复当前用户及可信 authType。
- 没有凭证：按匿名访问，由权限注解决定是否允许。
- 凭证格式错误、过期或无效：默认 Token 接入返回 401，即使目标是公开接口；重新登录前移除失效旧凭证。
- 正常返回、异常、拒绝请求和异步请求切换时，自动清理本模块当前上下文。
- OPTIONS 不解析登录 Token。仅使用公共 security 的匿名模式不接管其他认证格式。

自定义拦截器和外部登录体系需要遵守同样的身份隔离要求，见[扩展接口](extension.md)。

## 刷新与注销

```java
SecurityUtil.refresh();                 // 刷新当前用户资料
SecurityUtil.getUserDetails(otherId);   // 查询其他用户，不切换登录
SecurityUtil.logout();                  // 按选定登录模块执行注销
```

- JWT：logout 不撤销已签发 Token，客户端必须自行删除；refresh 不改变 Token 有效期。
- 有状态登录：logout 注销当前 Token；请求结束不等于注销。
- 已有认证体系：注销和刷新遵循其已接入的公开能力，没有可信 authType 时可返回 null。

`SecurityContext.clear()` 只清理当前上下文，不使 Token 或其他登录状态失效。有有效凭证的下一次请求仍可恢复身份。

用户资料或角色变化不会强制改写所有正在处理的请求，业务需要时应主动复核；自定义 UserStore 缓存的刷新和失效仍由业务负责。

## 异步与后台任务

普通新线程、线程池任务不会自动继承当前登录身份。通常把所需的 userId 等业务参数显式传入，不在后台重新登录。

确实需要非 HTTP 登录的 JWT 场景：

```java
try {
    SecurityUtil.login(new PasswordLogin(serviceUsername, servicePassword, "job"));
    // 执行当前线程业务
} finally {
    SecurityContext.clear();
}
```

SecurityContext.set/clear 是供可信服务端扩展使用的公开方法，不能将客户端直接提交的用户对象设置为登录身份。不要记录完整 Token、密码、API Key 或登录响应。

---

下一步：[权限注解](authorization.md)、[自定义实现](extension.md)。
