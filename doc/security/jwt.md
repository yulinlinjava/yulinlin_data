# 无状态 JWT 登录

[文档首页](../README.md) / [接口安全](README.md)

最小接入和请求身份恢复；注销不会撤销已发出的 JWT。

前置阅读：[安全模块选型](modules.md)、[用户模型与 UserStore](user.md)。请求恢复与清理见[登录上下文](context.md)，自定义接入见[扩展专题](extension.md)。

## 本页导航

- [JWT 最小接入](#jwt-最小接入)

## JWT 最小接入

引入 `com.yulinlin:security-jwt:3.0`，注册上述 `UserStore`，再配置：

```yaml
yulinlin:
  security:
    jwt:
      secret: ${YULINLIN_JWT_SECRET} # 至少 32 字节随机密钥的 Base64，不是 AES 设备密钥
      issuer: yulinlin
      ttl: 2h
```

默认 `DefaultJwtTokenCodec` 使用 HS256，签发 `sub=userId`、唯一 `jti`、`deviceType`、`iat`、`exp`、`iss`；验签时检查算法、签名和签发者，管理器还检查时间并通过 UserStore 加载允许访问的用户，不查询登录会话记录。`ttl` 默认 2h，允许 1s～365d。JWT 是签名而非加密，不要加入密码等秘密资料。

本实现是**纯无状态 JWT**，没有登录记录、Token 黑名单、按用户撤销或存储容量配置。`LoginSession` 只是当前请求的线程快照，不是持久化会话。保持签名密钥和校验配置稳定即可在应用重启后验证未过期 JWT，前提是 `UserStore` 仍允许该用户访问。

从旧有状态版本迁移时，之前因删除记录而失效、但尚未过期且验签有效的 JWT 可能重新被接受。如果迁移要求所有用户重新登录，需由部署方明确轮换签名密钥或调整验证策略，不能继续依赖已移除的撤销记录。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
