# 接口安全

[返回文档首页](../README.md)

按需接入登录、权限、加密、限流；JWT、Sa-Token 或现有 Spring Security 选择一种适配。本主题只介绍框架公开 API、配置、使用案例和必要边界，不展开第三方框架调用及内部实现。

## 按需阅读

| 小专题 | 解决什么问题 |
| --- | --- |
| [安全模块选型](modules.md) | 仅需加密限流用 security；选择一种登录实现或现有认证适配。 |
| [接口加密与前端对接](crypto.md) | 全局加解密、ApiCrypto(false) 例外、多设备、前端协议和排除路径。 |
| [自定义接口加解密](crypto-codec.md) | 提供 CryptoCodec Bean 替换算法与密文格式，不要求配置 AES 密钥。 |
| [限流与 IP 黑名单](rate-limit.md) | 注解限流、业务黑名单和 Nginx 可信真实 IP。 |
| [用户模型与安全管理器](user.md) | 统一 LoginAuthenticator，密码、密钥、可信 ID 与自定义登录；name 记录 authType。 |
| [会话管理与数量限制](user.md#会话管理与数量限制) | 会话分页、计数、踢人、自定义管理器与分布式使用边界。 |
| [无状态 JWT 登录](jwt.md) | 最小接入和请求身份恢复；注销不会撤销已发出的 JWT。 |
| [自定义 Token 生成与解析](token-codec.md) | 替换 JwtTokenCodec，统一生成与解析协议。 |
| [自定义安全实现与拦截器](extension.md) | 有 LoginInterceptor Bean 就使用用户实现，否则自动提供默认实现。 |
| [有状态 Sa-Token 登录](sa-token.md) | 统一登录和注销、客户端凭证、重启与多实例使用边界。 |
| [Spring Security 接入](spring.md) | 已有登录体系使用统一用户、权限和登录 API 的约定。 |
| [登录上下文生命周期](context.md) | 线程身份恢复、请求结束清理与异步边界。 |
| [登录、角色与权限注解](authorization.md) | ApiLogin、ApiRole、ApiPerm、ApiPublic 和默认匿名行为。 |

## 建议顺序

- 登录：选型 → [用户模型](user.md) → [JWT](jwt.md)、[Sa-Token](sa-token.md) 或 [Spring Security](spring.md) → [请求上下文](context.md) → [权限注解](authorization.md)。
- 只需要加密或限流：直接阅读[加密](crypto.md)或[限流](rate-limit.md)，不必接入登录实现。
- 替换行为：按需求阅读[接口加解密](crypto-codec.md)、[Token 编解码](token-codec.md)或[管理器与拦截器扩展](extension.md)。

只维护小专题正文；本页只提供导航。给 AI 使用时先提供 [llms.txt](../../llms.txt)，再按任务加载相关页面。
