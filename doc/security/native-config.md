# 原生配置与统一策略

[文档首页](../README.md) / [接口安全](README.md)

统一业务接口和少量跨实现策略，不重新包装第三方框架的全部配置。项目只选择 `security-sa-token`、`security-jwt` 中的一种，或自定义 `ISecurityManager`。

## 本页导航

- [配置归属](#配置归属)
- [Sa-Token 请求头登录案例](#sa-token-请求头登录案例)
- [冲突与关闭行为](#冲突与关闭行为)
- [扩展点与验证边界](#扩展点与验证边界)

## 配置归属

| 内容 | 配置或扩展入口 |
| --- | --- |
| 是否提供统一会话管理、登录会话数量上限、查询分页 | `yulinlin.security.session.*` |
| 默认 Token 请求头与前缀 | `yulinlin.security.auth.token-header` / `token-prefix`；详见[适用范围](context.md#配置-token-请求头) |
| Sa-Token 有效期、活跃超时、Token 头、Cookie、超限注销方式 | `sa-token.*`、原生 `SaTokenConfig` / `StpLogic` / `SaTokenDao` |
| 当前 JWT 的密钥、签发者、固定有效期 | `yulinlin.security.jwt.*`；完整替换协议使用 `JwtTokenCodec` |

不增加 `yulinlin.security.sa-token.*` 或通用 `csrf-enabled` 去代理原生框架。原生配置保留各自语义，不保证不同框架的超时、会话排序或分布式能力相同。

## Sa-Token 请求头登录案例

引入 `com.yulinlin:security-sa-token:3.0`，先提供 [UserStore 和所需 LoginAuthenticator](user.md)。示例让同一账号最多保留 3 个登录会话：

```yaml
yulinlin:
  security:
    session:
      enabled: true          # 默认开启，可以省略
      max-sessions: 3        # 默认 -1，不限；不是去重后的物理设备数量
      query:
        default-page-size: 20
        max-page-size: 100

sa-token:
  token-name: Authorization
  token-prefix: Bearer
  is-read-header: true
  is-read-cookie: false
  is-read-body: false
  is-concurrent: true
  is-share: false
  timeout: 86400             # 秒：Token 最长 24 小时
  active-timeout: 1800       # 秒：30 分钟没有有效鉴权活动后不可继续访问
  auto-renew: true           # 更新活跃时间，不延长上面的绝对有效期
  overflow-logout-mode: KICKOUT # 原生选项：超限移除最早会话时标记为踢下线
```

数量只维护 `max-sessions`，不再设置 `sa-token.max-login-count`。第四个会话登录时按原生策略处理最早会话；不承诺跨节点并发登录的严格原子上限。

登录仍使用 `SecurityUtil.login(new PasswordLogin(username, password, "web"))`，前端读取业务登录接口返回的 Token，后续只发送一份凭证：

```http
GET /api/user/profile
Authorization: Bearer <token>
```

统一登录不会额外写认证 Cookie。关闭原生 Cookie/参数读取后采用 Header Token，仍需严格设置 CORS、防范 XSS；不要把前后端分离等同于自动安全。如果改为 Cookie 认证，必须同时实现 Cookie 下发、CSRF Token 下发与校验等完整协议，不能只把 `is-read-cookie` 改成 true。

可继续通过原生 Java 配置定制；Sa-Token 官方配置 Bean 不自动退让，业务自定义配置通常通过 `@Primary` 选择。这里使用 `@ConfigurationProperties` 保留 YAML 绑定：

```java
@Bean
@Primary
@ConfigurationProperties(prefix = "sa-token")
SaTokenConfig customSaTokenConfig(SessionManagementProperties sessions) {
    return new SaTokenConfig()
            .setMaxLoginCount(sessions.getMaxSessions())
            .setIsConcurrent(true)
            .setIsShare(false)
            .setTimeout(86400);
}
```

类型分别是 `cn.dev33.satoken.config.SaTokenConfig`、`com.yulinlin.security.session.SessionManagementProperties`。自定义 Bean 或 `StpLogic` 独立配置时，必须显式让 `maxLoginCount` 与统一上限一致；即使业务值恰好等于 Sa-Token 默认的 12，也不会猜测它是“未配置”后覆盖。只有官方自动装配且未配置的默认值可以由统一上限替换。

分布式直接使用 Sa-Token 官方 Redis 插件和连接配置，不另建一套会话存储，详见 [Sa-Token 接入](sa-token.md#会话管理和分布式)。原生配置说明见 [Sa-Token 文档](https://sa-token.com/use/config.html)。

## 冲突与关闭行为

| 情况 | 启动行为 |
| --- | --- |
| 同时启用两个内置登录实现 | 报错，不能用 `@Primary` 混用 |
| Sa-Token YAML 原生上限与统一上限不同 | 报错；相同值允许，但推荐只配统一值 |
| Sa-Token 最终 Java Bean / StpLogic 上限与统一值不同 | 报错，不静默覆盖 |
| Sa-Token 开启统一管理但 `is-concurrent=false` | 报错 |
| Sa-Token 统一登录入口配置 `is-share=true` | 报错；独立 Token 是统一登录的不变量，关闭管理也不放开共享 Token |
| JWT 显式 `session.enabled=true` | 报错；默认不参与统一管理 |

要完全交由原生框架管理数量，可配置：

```yaml
yulinlin:
  security:
    session:
      enabled: false
```

关闭后不提供默认 `LoginSessionManager`，不接管数量上限，仍保留认证、会话原生过期和业务安全配置。Sa-Token 可自行使用原生上限、互斥登录；统一登录仍不允许共享 Token，需要该语义时自定义 `ISecurityManager`。

JWT 继续使用 [JWT 配置](jwt.md) 和 [JwtTokenCodec](token-codec.md)。自定义 Codec 时默认 Codec 退让，不强制提供它的签名密钥；不会额外创建有状态会话。

## 扩展点与验证边界

- `LoginSessionManager` 只替换查询、计数和踢人；不会替换实际认证存储或取消统一数量策略。
- 自定义 `ISecurityManager` 时对应内置适配器整体退让；业务负责完整认证生命周期。
- Sa-Token 仍支持官方 DAO、监听器和策略扩展。
- 启动检查覆盖配置属性和最终 Sa-Token 配置。不要在运行时、单次原生登录参数或自定义 ObjectPostProcessor 中重新覆盖已归属统一策略的字段；任意用户代码的行为不可能由启动检查穷尽证明。

业务调用 `LoginSessionManager.countSessions(userId)`、`kickoutSession(userId, sessionId, reason)`、`kickoutAll(userId, reason)` 不需要知道底层实现。对外 `sessionId` 是管理标识，不是登录凭证。管理接口的授权和审计由业务负责；能力差异和全局列表支持以 `capabilities()` 为准。

---

下一步：[统一会话管理](user.md#会话管理与数量限制)、[自定义安全实现](extension.md)。
