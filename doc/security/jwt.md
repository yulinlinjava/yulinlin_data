# 无状态 JWT 登录

[文档首页](../README.md) / [接口安全](README.md)

最小接入和请求身份恢复；注销不会撤销已发出的 JWT。

前置阅读：[安全模块选型](modules.md)、[用户模型与 UserStore](user.md)。请求恢复与清理见[登录上下文](context.md)，自定义接入见[扩展专题](extension.md)。

## 本页导航

- [JWT 最小接入](#jwt-最小接入)

## JWT 最小接入

引入 `com.yulinlin:security-jwt:3.0`，注册 [UserStore 与 LoginAuthenticator](user.md)。统一调用 `SecurityUtil.login(new PasswordLogin(username, password, "web"))` 或 `login(new ApiKeyLogin(apiKey, "server"))`，分别由对应参数类型的业务认证器验证凭证。业务已完成外部身份验证时，也可调用 `login(new UserIdLogin(userId, "web"))`，默认通过 UserStore 检查用户可用性；不能向客户端开放任意 ID 登录。自定义登录方式同样注册 LoginAuthenticator，无需修改 JWT 模块。再配置：

```yaml
yulinlin:
  security:
    jwt:
      secret: ${YULINLIN_JWT_SECRET} # 至少 32 字节随机密钥的 Base64，不是 AES 设备密钥
      issuer: yulinlin
      ttl: 2h
```

默认使用 HS256 签名，Token 包含用户 ID、设备类型、认证来源、唯一凭证标识和有效期，不保存密码或完整用户资料。authType 取成功认证器的 name，不接收客户端自报值。请求必须通过签名、签发者、时间及用户可用性校验。`ttl` 默认 2h，允许 1s～365d。JWT 是签名而非加密，不要加入密码等秘密资料。

登录响应包含 authType，后续使用 `SecurityContext.getAuthType()` 或 `SecurityUtil.getAuthType()` 读取。缺少 deviceType/authType 的旧 Token 不再兼容，需要重新登录；不根据当前请求猜测或补齐认证方式。

本模块提供**纯无状态 JWT**，没有服务端登录记录、Token 黑名单或按用户撤销能力。注销后客户端应删除 Token，但已发出的 Token 仍可能有效。保持签名密钥和校验配置稳定即可在应用重启后使用未过期 JWT，前提是 `UserStore` 仍允许该用户访问。

JWT 默认不参与统一会话管理，不需要额外配置 `yulinlin.security.session.enabled=false`。显式 false 也允许；显式 true 会在启动时提示不支持，不会因为 Sa-Token/Spring 默认开启而要求 JWT 注册会话管理器。

业务 API Key 与 JWT 签名 secret 是不同凭证。密钥登录只在签发时验证 API Key，JWT 不保存原密钥或独立密钥权限；停用/轮换 API Key 不会自动撤销已签发 JWT。后续权限以 UserStore 为准，建议密钥绑定独立服务账号，不能只在认证时临时缩减同一用户的权限。

需要注销立即失效或管理在线登录状态时，选择[有状态登录](sa-token.md)或[自定义安全实现](extension.md)，不要把纯 JWT 的注销当成服务端撤销。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
