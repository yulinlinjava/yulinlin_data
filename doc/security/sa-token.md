# 有状态 Sa-Token 登录

[文档首页](../README.md) / [接口安全](README.md)

使用原生会话能力；存储、注销与 JWT 的区别。

前置阅读：[安全模块选型](modules.md)、[用户模型与 UserStore](user.md)。请求恢复与清理见[登录上下文](context.md)，自定义接入见[扩展专题](extension.md)。

## 本页导航

- [Sa-Token 接入](#sa-token-接入)

## Sa-Token 接入

选择 `com.yulinlin:security-sa-token:3.0`，不引入 `security-jwt`；仍提供相同的 `UserStore`。业务继续调用 `SecurityUtil.login(userId, deviceType)`、`logout()` 和 `getUserDetails()`，权限注解不用改。

Token 生命周期、设备策略、会话存储由 Sa-Token 负责，配置沿用 `sa-token.*`。默认内存存储不能保证重启保留登录；多实例和重启恢复需要按所用 Sa-Token 版本接入其 Redis 等共享存储，并保证记录仍在、未过期。不通过 JWT 配置增加存储，也不实现 JwtTokenCodec。

需要踢人、在线会话管理等能力时注入 `SaTokenSecurityManager`，调用 `stpLogic()` 获取底层对象。业务重置密码后若要求强制重新登录，应在事务提交后调用对应原生能力；本适配器不会监听账号表变化自动踢人。单独引入项目中的 ORM cache-redis 模块，不会自动替换 Sa-Token 会话存储。

统一 HTTP 入口仍默认读取 `Authorization: Bearer <token>`；适配器本身不读写 Cookie。若业务另外使用原生 `StpUtil` 的 HTTP 入口，需自行对齐其 Token 读取配置，不能假设两套请求头规则自动相同。接口详情以所用版本的 [Sa-Token 登录文档](https://sa-token.com/use/login-auth.html) 为准。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
