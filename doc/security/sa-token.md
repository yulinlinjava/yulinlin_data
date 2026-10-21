# 有状态 Sa-Token 登录

[文档首页](../README.md) / [接口安全](README.md)

使用统一登录 API；注销当前 Token 后，该 Token 不再有效。

## 本页导航

- [最小接入](#最小接入)
- [登录与请求](#登录与请求)
- [当前用户与注销](#当前用户与注销)
- [使用边界](#使用边界)

## 最小接入

引入 `com.yulinlin:security-sa-token:3.0`，不要同时引入其他登录模块。提供：

- `UserStore`：按用户 ID 加载安全用户、角色和权限。
- `LoginAuthenticator<T>`：验证对应登录参数；只提供业务实际需要的认证方式。

完整 Bean 和 Controller 示例见[用户模型与安全管理器](user.md)。验证码、账号状态、密码及业务密钥规则由业务认证器校验。

## 登录与请求

```java
import com.yulinlin.security.auth.*;

LoginResult result = SecurityUtil.login(
        new PasswordLogin(username, password, "web"));

String token = result.token();
String authType = result.authType();
IUserDetails user = result.user();
```

其他登录方式使用相同入口，按场景选择，不需要连续调用：

```java
SecurityUtil.login(new ApiKeyLogin(apiKey, "server"));

// 仅供服务端已经完成可信身份验证的业务使用
SecurityUtil.login(new UserIdLogin(verifiedUserId, "web"));
```

登录接口返回 `R<LoginResult>`，客户端保存 Token；统一登录入口不会额外替客户端写登录 Cookie。

采用 Bearer 调用时，后续请求只携带这一份凭证：

```http
POST /video/list
Authorization: Bearer <token>
Content-Type: application/json

{}
```

已有项目使用其他凭证头或 Cookie 时应统一客户端约定，不要同时携带多份身份凭证。普通接入保留模块默认处理即可；需要完全自定义请求认证时，按[公开扩展协议](extension.md#接入外部安全框架)完整接入，不能直接套用仅设置框架上下文的 JWT 拦截器示例。

每次统一登录返回独立 Token，authType 取成功认证器的 name。旧凭证缺少必要认证信息时需要重新登录；不要假设绕过统一入口产生的凭证可以直接接入。

## 当前用户与注销

```java
IUserDetails user = SecurityUtil.requireUser();
String userId = SecurityUtil.getUserId();
String authType = SecurityUtil.getAuthType();

boolean permitted = SecurityUtil.hasPermission("video:edit");
SecurityUtil.refresh(); // 刷新用户资料，不切换账号

SecurityUtil.logout();  // 注销当前 Token
```

也可通过 `SecurityContext` 读取当前身份，使用 `@ApiLogin`、`@ApiRole`、`@ApiPerm` 声明权限。不要把第三方登录 API 混入普通业务代码。

客户端在注销成功后删除本地 Token。请求结束自动清理当前上下文，但不会注销登录；`SecurityContext.clear()` 也不等于注销。

## 使用边界

- 默认登录记录保存在内存，应用重启后需要重新登录；重启恢复和多实例登录需要项目配置持久化、共享登录状态。
- 引入 ORM 的 cache-redis 模块，不等于登录状态已经持久化。
- `UserStore` 应拒绝不存在、禁用或不允许继续访问的用户。加载失败不能返回旧用户兜底。
- 修改密码、停用业务密钥不会自动注销已经建立的登录，不要把这类操作等同于注销全部设备。
- 公开统一 API 不提供在线会话列表、批量踢人或按用户注销全部 Token。
- 用户身份不自动传递到新线程，异步任务显式传入必要的业务信息。
- 复杂登录策略通过[公开扩展接口](extension.md)接入，普通业务继续调用同一套 API。

---

下一步：[登录上下文](context.md)、[权限注解](authorization.md)。
