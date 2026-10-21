# 有状态 Sa-Token 登录

[文档首页](../README.md) / [接口安全](README.md)

统一登录后由 Sa-Token 管理会话；原生登录接入时需保存可信认证来源。

前置阅读：[安全模块选型](modules.md)、[用户模型与 UserStore](user.md)。请求恢复与清理见[登录上下文](context.md)，自定义接入见[扩展专题](extension.md)。

## 本页导航

- [Sa-Token 接入](#sa-token-接入)
- [保留原来的登录代码](#保留原来的登录代码)
- [请求头与 Cookie](#请求头与-cookie)
- [上下文与扩展边界](#上下文与扩展边界)

## Sa-Token 接入

选择 `com.yulinlin:security-sa-token:3.0`，不引入其他登录模块；提供相同的 `UserStore`。统一使用 `SecurityUtil.login(LoginRequest)`，PasswordLogin、ApiKeyLogin 和自定义参数各自注册 [LoginAuthenticator](user.md)。业务已完成外部身份验证时使用 UserIdLogin，默认通过 UserStore 检查用户可用性。认证成功后共用 Sa-Token 登录记录、logout()、getUserDetails() 和权限注解。

authType 取成功认证器的 name，保存在 **Token 专属会话**，不放在用户共享会话。统一入口每次登录生成独立 Token（不复用旧 Token），避免同一用户、设备使用不同认证方式时覆盖来源。`LoginResult.authType()`、`SecurityContext.getAuthType()` 可读取；缺少 authType 的旧 Token 不兼容。

`UserStore.load(userId)` 用来加载安全用户资料、角色和权限，不负责再次登录。不能返回含密码的用户对象；不存在或不允许继续登录时返回 null。实现里按传入的 userId 查询，**不要再调用 SecurityContext.getUser() / SecurityUtil.getUserDetails() 获取当前用户**，否则形成循环，框架会明确报错。

Token 生命周期、设备策略、会话存储由 Sa-Token 负责，配置沿用 `sa-token.*`。默认内存存储不能保证重启保留登录；多实例和重启恢复需要按所用 Sa-Token 版本接入其 Redis 等共享存储，并保证记录仍在、未过期。不通过 JWT 配置增加存储，也不实现 JwtTokenCodec。

需要踢人、在线会话管理等能力时注入 `SaTokenSecurityManager`，调用 `stpLogic()` 获取底层对象。业务重置密码后若要求强制重新登录，应在事务提交后调用对应原生能力；本适配器不会监听账号表变化自动踢人。单独引入项目中的 ORM cache-redis 模块，不会自动替换 Sa-Token 会话存储。

## 保留原来的登录代码

原生 StpUtil 仍能使用，但新版要求可信认证来源：业务验证身份后，创建独立 Token 并写入其 Token 会话。只调用原生 login 而不记录 authType，框架上下文不会认可该登录。优先使用统一入口，以下是保留原生调用的 HTTP 请求示意：

```java
import cn.dev33.satoken.stp.StpUtil;
import com.yulinlin.security.satoken.SaTokenSecurityManager;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.SecurityUtil;

// userId 来自业务已经验证的账号，不能直接信任客户端提交的 userId。
// verifiedAuthType 取完成认证的服务类 name()，不能从客户端参数直接取得。
StpUtil.login(userId, StpUtil.getStpLogic().createSaLoginParameter()
        .setDeviceType("web").setIsShare(false));
String token = StpUtil.getTokenValue();
StpUtil.getStpLogic().getTokenSessionByToken(token)
        .set(SaTokenSecurityManager.AUTH_TYPE_KEY, verifiedAuthType);

// 首次读取时按 Sa-Token 当前身份加载 UserStore；同一请求复用用户资料。
IUserDetails user = SecurityContext.requireUser();
String currentUserId = SecurityContext.getUserId();
IUserDetails sameUser = SecurityUtil.getUserDetails();

StpUtil.logout();
boolean loggedIn = SecurityContext.isLogin(); // false
IUserDetails anonymous = SecurityContext.getUser(); // null
```

原生会话记录与 authType 都准备好后，同一请求重新登录另一个账号，下一次读取上下文会加载新账号。踢人、注销、Token 失效也会在后续读取时重新检查。authType 必须遵守 [认证名称规则](user.md#自定义认证方式)；保存失败时业务应撤销刚创建的 Token，不返回不完整的登录结果。

统一入口仍然可用：

```java
import com.yulinlin.security.auth.PasswordLogin;
import com.yulinlin.security.auth.ApiKeyLogin;
import com.yulinlin.security.auth.UserIdLogin;
import com.yulinlin.security.auth.LoginResult;

LoginResult result = SecurityUtil.login(new PasswordLogin(username, password, "web"));
String token = StpUtil.getTokenValue(); // 与 result.token() 一致
StpUtil.logout();                      // 能注销这次登录

// 密钥认证 Bean 已配置时，另一种登录方式（不必和密码登录连续调用）
LoginResult partner = SecurityUtil.login(new ApiKeyLogin(apiKey, "server"));

// 另一种独立场景：上游已验证身份，只用可信 userId 建立登录。
LoginResult trusted = SecurityUtil.login(new UserIdLogin(verifiedUserId, "web"));
String authType = trusted.authType(); // 默认 user-id
```

原生 login 按 Sa-Token 配置写 Cookie/响应头；SecurityUtil.login 返回 LoginResult，只同步原生请求存储，不额外写登录 Cookie/响应头。客户端按所选入口接收并保存 Token。

账号密码和密钥是两种换取 Token 的方式，不是每次请求直接传业务密钥；后续仍按原生 Token 规则验证，按 UserStore.load(userId) 加载权限。密钥应绑定专用服务账号，停用业务密钥不会自动注销已换取的 Token，需要立即失效时由业务调用原生撤销能力。完整认证 Bean 和 Controller 示例见[业务密钥认证](user.md#业务密钥认证)。

## 请求头与 Cookie

内置 `SaTokenLoginInterceptor` 优先按 **Sa-Token 自己的规则**读取 Token：名称、前缀、请求参数、Header、Cookie 均由 `sa-token.*` 配置决定，不要求原有客户端统一改成 Authorization。

例如只允许原生请求头携带 Token：

```yaml
sa-token:
  token-name: satoken
  timeout: 86400
  is-read-header: true
  is-read-body: false
  is-read-cookie: false
```

前端后续请求使用 `satoken: <token>`。需要沿用 Cookie 登录时，按业务需要开启 `is-read-cookie`。配置项的完整含义见 [Sa-Token 官方文档](https://sa-token.com/use/login-auth.html)。

兼容既有框架调用：**没有原生凭证时**也接受 `Authorization: Bearer <token>`。校验成功后把 Token 写入 Sa-Token 的本次请求存储，因此 Bearer 请求内调用 StpUtil.getLoginId()/logout() 也能使用相同身份。若原生凭证和 Bearer 同时存在，以原生凭证为准；原生凭证无效或前缀不正确时直接拒绝，不降级切换到另一份身份。

没有凭证时按匿名处理，权限注解决定是否允许访问；携带无效凭证返回 401，包括公开接口。重新登录时移除已失效的旧凭证。

## 上下文与扩展边界

- 自动桥接在 Spring 初始化完成后生效，跟随 SecurityUtil 选定的 ISecurityManager；JWT 和默认匿名实现不启用此桥接。
- SecurityContext 读取时校验 Sa-Token 当前 Token、会话状态及 authType，**只缓存用户资料，不缓存“已经登录”的结论**。Redis 存储会产生相应会话读取开销；同请求、同身份不反复调用 UserStore，主动 refresh 或切换身份除外。
- 请求结束和异步切换清理框架线程快照；Sa-Token 自身的请求上下文仍由其 starter 管理。不会注销会话，也不会跨线程自动继承用户。
- SecurityContext.clear() 不是注销：原生会话仍有效时，再次读取可重新恢复。要注销请使用 StpUtil.logout() 或 SecurityUtil.logout()。
- 自定义 LoginInterceptor Bean 仍优先，框架自动注册，不需要自己编写 WebMvcConfigurer。使用内置 Sa-Token 管理器时，身份来源仍是原生 Sa-Token，不能只设置另一份 SecurityContext 冒充不同账号。
- 当前自动桥接跟随 StpUtil.getStpLogic() 对应的账号体系；其他独立 StpLogic、多账号体系和临时身份切换 switchTo，不在默认桥接范围内，需要定制管理器。
- 非 HTTP 场景没有原生请求上下文时，不会凭空恢复用户；显式框架登录/可信快照仍校验服务端会话，调用方负责 finally 清理。普通异步任务应显式传递必要的用户 ID。

内部只增加可选的 `SecurityContextResolver` 协议，SaTokenSecurityManager 实现它；不通过 AOP 包装 StpUtil 静态方法，也不增加第二份登录记录。自定义外部管理器可按[扩展专题](extension.md#接入外部安全框架)选择是否实现该协议。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
