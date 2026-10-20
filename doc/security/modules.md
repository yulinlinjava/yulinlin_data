# 安全模块选型

[文档首页](../README.md) / [接口安全](README.md)

仅需加密限流用 security；登录选择 JWT 或 Sa-Token。

## 本页导航

- [引入模块](#引入模块)
- [JWT 与 Sa-Token 如何选择](#jwt-与-sa-token-如何选择)

## 引入模块

一个项目只选一种登录实现，不同时引入 `security-jwt` 和 `security-sa-token`。它们均依赖公共 `security`，无需重复声明公共模块：

| 业务需要 | 引入的制品 | 业务需要提供 |
| --- | --- | --- |
| 仅加密、限流或公共权限协议 | `security` | 不需要登录实现；默认匿名管理器可启动 |
| 无状态 JWT 登录 | `security-jwt` | `UserStore` Bean、JWT 签名配置；不存登录记录，不提供 Token 撤销 |
| Sa-Token 登录 | `security-sa-token` | `UserStore` Bean；会话策略和存储使用 Sa-Token 自身配置 |
| 已有其他安全框架 | `security` | 适配该框架的 `ISecurityManager` Bean |

下面为仅引入公共模块的示例；需要登录时，将 `artifactId` 换成上表选定的实现即可。

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>security</artifactId>
    <version>3.0</version>
</dependency>
```

模块不依赖 Spring Security，也不要求引入 ORM 或数据库驱动。未配置任何设备密钥时，不注册接口加解密组件，由业务自行处理；登录、权限、限流仍按各自规则工作。配置了任意设备密钥后才注册设备密钥管理器、请求解密 Advice 和响应加密 Advice；此时缺少 `unknown`、其他已声明设备的密钥或 CBC IV 会启动失败，不生成临时密钥。启停和兼容模式见[加密接入](crypto.md#是否接管接口加解密)。

鉴权使用独立的 SecurityAuthAutoConfiguration 注册默认匿名管理器和 ApiAuthAspect，不依赖 MVC 或加密密钥配置，非 Web 项目也可保护 Service。MVC 登录上下文由 SecurityLoginAutoConfiguration 自动注册：有业务 `LoginInterceptor` Bean 就使用它，没有就使用默认实现，无需启用开关。只使用加密、限流的项目不需要提供业务 ISecurityManager；默认匿名管理器不解析 Authorization，受保护方法仍拒绝匿名访问。

### JWT 与 Sa-Token 如何选择

下面比较的是本仓库两种适配器的当前行为，而不是 Sa-Token 的全部可选模式。一个项目只选一个登录模块。

| 能力 | security-jwt | security-sa-token |
| --- | --- | --- |
| 登录状态 | 纯无状态，不保存 Token 记录 | 有状态，依赖 Sa-Token 登录记录 |
| 请求认证 | 验签、时间检查，再按 userId 加载用户 | 检查 Sa-Token 登录及有效期状态，再加载用户 |
| 默认存储 | 无登录存储 | Sa-Token 默认内存；可用其原生共享存储扩展 |
| 注销 | 只清理线程，客户端删除 Token；不能使旧 JWT 失效 | 服务端注销当前 Token，并清理线程 |
| 踢人、在线会话管理 | 不提供 | 使用底层 StpLogic 原生能力；不是公共 ISecurityManager 的方法 |
| 应用重启 | 密钥、issuer 等校验配置不变且用户仍有效时，未过期 JWT 可继续使用 | 内存记录丢失则需重登；共享持久化存储需保证记录仍在且未过期 |
| 修改密码 | 默认不撤销旧 JWT | 仅修改密码也不会自动踢人；业务需在提交后调用原生注销/踢人能力 |
| 停用/删除账号 | UserStore 拒绝加载，阻止后续访问；不等于永久撤销 JWT | UserStore 拒绝加载；适配器处理不存在/不允许登录的用户 |
| 自动续期 | 不提供，refresh 只刷新用户资料 | 活跃超时/续期规则取决于原生 Sa-Token 配置 |
| 业务登录调用 | login(userId, deviceType) | 同左 |

简单无状态认证选 JWT；需要服务端强制失效、设备会话管理选 Sa-Token。JWT 的服务端 logout 不足以防止已经泄漏的 Token 再次使用；重新启用账号后，其仍未过期的旧 JWT 可能再次被接受。删除账号后不要复用旧 userId，否则该身份的旧 JWT 在有效期内也可能被新账号接受。高要求业务应使用有状态方案或自定义凭证版本等校验。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
