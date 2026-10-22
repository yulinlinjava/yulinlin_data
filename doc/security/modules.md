# 安全模块选型

[文档首页](../README.md) / [接口安全](README.md)

按业务需要选择模块，登录和鉴权统一使用框架公开 API。

## 本页导航

- [引入模块](#引入模块)
- [JWT 与 Sa-Token 如何选择](#jwt-与-sa-token-如何选择)

## 引入模块

一个项目只选一种登录实现，不混用 `security-jwt`、`security-sa-token`、`security-spring`。这些模块已经包含公共 `security` 的能力。

| 业务需要 | 引入的制品 | 接入要求 |
| --- | --- | --- |
| 仅加密、限流或权限注解 | `security` | 不要求实现登录；未登录用户不能访问受保护方法 |
| 无状态 Token 登录 | `security-jwt` | 配置签名密钥，提供 UserStore 和所需 LoginAuthenticator |
| 有状态 Token 登录 | `security-sa-token` | 提供 UserStore 和所需 LoginAuthenticator |
| 复用已有 Spring Security 登录 | `security-spring` | 项目已有可用认证配置；统一登录需完成项目认证适配 |
| 完全自定义登录实现 | `security` | 提供 ISecurityManager，按需提供 LoginInterceptor |

仅使用公共能力时：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>security</artifactId>
    <version>3.0</version>
</dependency>
```

需要登录时，将 artifactId 换成对应模块即可。公共安全能力不要求引入 ORM 或数据库驱动。

公开使用规则：

- 登录统一调用 `SecurityUtil.login(LoginRequest)`，也可注入 `ISecurityManager`。
- 密码、密钥、自定义认证分别提供 `LoginAuthenticator<T>` Bean；`UserStore` 按用户 ID 加载用户。
- 可信业务按 ID 登录使用 `UserIdLogin`，不能直接接受客户端任意用户 ID。
- 自定义 `LoginInterceptor` Bean 会自动替换默认拦截器，不需要额外启用开关或重复注册。
- 没有自定义 CryptoCodec 且不配置设备密钥时，不处理接口加解密；登录、权限和限流仍可单独使用。自定义实现不要求 AES 密钥，详见[加密扩展](crypto-codec.md)。
- 存在 Codec 时无注解接口也参与全局加解密；ApiCrypto(false) 仅使该接口兼容请求、返回明文。要完全跳过，配置 exclude-paths，见[规则表](crypto.md#接口注解)。

## JWT 与 Sa-Token 如何选择

| 能力 | security-jwt | security-sa-token |
| --- | --- | --- |
| 登录状态 | 无状态，不保存登录记录 | 有状态，保存登录记录 |
| 登录调用 | `login(PasswordLogin / ApiKeyLogin / UserIdLogin / 自定义参数)` | 相同 |
| 登录结果 | Token、有效期、用户信息、authType | 相同；不设置固定到期时间时 expiresAt 可为空 |
| 注销 | 清理当前身份，客户端删除 Token；旧 Token 本身不会因此失效 | 注销当前 Token，后续不能继续使用该 Token |
| 应用重启 | 密钥和校验配置不变、用户有效且 Token 未过期时仍可用 | 默认内存登录记录不跨重启保留；重启恢复需要持久化方案 |
| 停用或删除用户 | UserStore 拒绝加载后阻止后续访问 | 相同 |
| 修改密码或业务密钥 | 不自动撤销已签发 Token | 不自动注销既有登录 |
| 刷新资料 | `refresh()`，不重新签发 Token | `refresh()`，不等于重新登录 |
| 多实例部署 | 各实例使用一致的签名配置和用户数据 | 各实例需要共享有效的登录状态 |

只需要简单 Token 登录可选 JWT；需要注销后使当前 Token 失效可选有状态登录。统一 API 不承诺批量踢人、查询在线用户或自动续期。

JWT 用户重新启用后，其尚未过期的旧 Token 可能继续使用。删除用户后不要复用旧 userId。需要更严格的凭证撤销规则，使用有状态方案或[自定义管理器](extension.md#自定义有状态登录)。

---

下一步：[用户模型与登录接口](user.md)。
