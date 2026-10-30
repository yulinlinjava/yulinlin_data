# 登录上下文生命周期

[文档首页](../README.md) / [接口安全](README.md)

业务只读取当前用户、Token 和认证来源；请求结束由框架清理上下文。

## 本页导航

- [登录上下文与请求生命周期](#登录上下文与请求生命周期)
- [配置 Token 请求头](#配置-token-请求头)
- [公开接口的可选登录](#公开接口的可选登录)
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

JWT 客户端默认在后续请求携带 `Authorization: Bearer <token>`，请求头名称和前缀可以通过[配置](#配置-token-请求头)修改。其他登录模块按对应专题的客户端约定携带凭证。

请求行为：

- 有有效凭证：恢复当前用户及可信 authType。
- 没有凭证：按匿名访问，由权限注解决定是否允许。
- 凭证格式错误、过期或无效：公开接口按匿名继续；要求登录、角色或权限的接口返回 401。
- 公开接口包括 `@ApiPublic` 最终生效的接口，以及没有鉴权要求的接口。类上公开但方法要求登录、角色或权限的，仍按受保护接口处理。
- 数据库、Redis 等故障正常抛出，不当作无效 Token 静默放行。
- 正常返回、异常、拒绝请求和异步请求切换时，自动清理本模块当前上下文。
- OPTIONS 不解析登录 Token。仅使用公共 security 的匿名模式不接管其他认证格式。
- MVC 托管的静态资源直接放行，不解析 Token；即使携带无效或过期 Token，也不会因此返回登录错误。请求开始、结束和异步切换仍清理框架上下文。

自定义拦截器和外部登录体系需要遵守同样的身份隔离要求，见[扩展接口](extension.md)。

静态资源按实际资源处理器识别，包括 MVC 资源映射和默认 Servlet 的静态资源转发，无需添加路径白名单。Controller 返回文件，或接口路径以 `.js`、`.html` 等结尾，仍按业务接口鉴权；不属于自动放行的静态资源。资源是否存在、缓存与响应状态由资源服务正常处理。本模块的放行不会覆盖网关或外部认证体系在请求到达 MVC 前的拦截规则。

## 配置 Token 请求头

只更换请求头名称或前缀，不需要编写拦截器。例如客户端直接发送 `token: <token>`：

```yaml
yulinlin:
  security:
    auth:
      token-header: token
      token-prefix: ""  # 必须写空字符串：直接传原始 Token，不带 Bearer
```

```http
token: 用户的Token
```

| 配置 | 默认值 | 含义 |
| --- | --- | --- |
| `yulinlin.security.auth.token-header` | `Authorization` | 请求头名称，也可设置为 `token`、`X-Token` 等 |
| `yulinlin.security.auth.token-prefix` | `Bearer` | 凭证前缀；设为 `""` 表示无前缀 |

保留默认值时发送 `Authorization: Bearer <token>`；配置 `token-header: X-Auth`、`token-prefix: Token` 时发送 `X-Auth: Token <token>`。前缀后面的空格由框架处理，配置值不要带空格。请求头名称和前缀按大小写不敏感匹配，Token 本身不改写大小写。

默认拦截器只读取选定请求头，不自动兼容其他请求头、同名 Cookie 或 URL 参数。没有该请求头视为未携带 Token；存在但为空、前缀不符、含内部空白或超长时视为无效凭证，继续遵循[公开接口可选登录规则](#公开接口的可选登录)。请求头值最多 8192 个字符。

配置在应用初始化时读取，修改后重启生效，不提供运行时热更新。请求头名称不能为空，不能包含空白或冒号；非空前缀必须是一个合法、不含空白的标记，错误配置会在启动时提示。

适用范围：

- `security-jwt` 和使用默认 Token 拦截器的自定义安全管理器直接使用这组配置。
- `security-sa-token` 优先使用 `sa-token.*` 配置的凭证；没有原生凭证时才读取上述备用请求头。原生凭证无效时不尝试另一份身份。通常只维护原生约定，见[配置案例](native-config.md#sa-token-请求头登录案例)。
- `security-spring` 沿用项目自己的认证入口配置，此配置不会修改外部认证规则。
- 自定义 `LoginInterceptor` Bean 仍优先，由其决定如何读取凭证；代码中写死的请求头不会被 YAML 自动替换。只需要改请求头时，可移除自定义拦截器，使用默认实现与这组配置。

## 公开接口的可选登录

例如视频列表既允许游客访问，也需要为已登录用户展示个性化结果：

```java
@ApiPublic
@PostMapping("/video/list")
public R<List<VideoVO>> list(@RequestBody VideoQuery query) {
    String userId = SecurityContext.getUserId(); // 游客为 null，有效 Token 对应当前用户。
    return R.newInstance(videoService.list(query, userId));
}
```

这是 Controller 方法片段，`VideoQuery`、`VideoVO` 和 `videoService` 由业务定义。

| 请求携带的凭证 | 公开接口的行为 |
| --- | --- |
| 没有 Token | 匿名执行，当前用户为 null |
| 有效 Token | 验证后恢复身份，可读取用户、Token 与 authType |
| 过期、格式错误或无效 Token | 清理本次身份，匿名执行；不会注销其他有效登录 |

公开接口应使用 `getUserId()` / `getUser()` 判断身份，调用 `requireUser()` 仍会主动拒绝匿名。若随后调用受保护的 Service 方法，该方法依然检查登录、角色和权限。

自定义 `LoginInterceptor` 不要在发现 `@ApiPublic` 时提前返回，否则有效 Token 也没有机会恢复身份。读取 Token 后照常验证，凭证无效抛 `LoginException`，由框架统一决定匿名继续还是返回 401，不要提前写入错误响应。项目自身的过滤器、外部认证体系或网关若提前拒绝请求，本模块不会覆盖其规则。

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
