# 自定义 Token 生成与解析

[文档首页](../README.md) / [接口安全](README.md)

替换 JwtTokenCodec，统一生成与解析协议。

前置阅读：[安全模块选型](modules.md)、[用户模型与 UserStore](user.md)。请求恢复与清理见[登录上下文](context.md)，自定义接入见[扩展专题](extension.md)。

## 本页导航

- [自定义 Token 生成与解析](#自定义-token-生成与解析-1)

## 自定义 Token 生成与解析

```java
package com.example.security;

import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.jwt.JwtTokenCodec;
import com.yulinlin.security.jwt.TokenInfo;

// 在业务配置中将此实现注册成唯一 JwtTokenCodec Bean。
public class BusinessTokenCodec implements JwtTokenCodec {
    private final BusinessTokenService tokens;

    public BusinessTokenCodec(BusinessTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public String generate(IUserDetails user, String deviceType) {
        return tokens.issue(user.getUserId(), deviceType);
    }

    @Override
    public TokenInfo parse(String token) {
        return tokens.verifyAndParse(token);
    }
}
```

`BusinessTokenService` 是示意的业务实现，不是框架类；请委托成熟 JWT 库做签发和验签，不要只 Base64 解码。注册方式：`@Bean JwtTokenCodec jwtTokenCodec(BusinessTokenService tokens) { return new BusinessTokenCodec(tokens); }`。

解析结果定义为：

```java
public record TokenInfo(
    String userId,
    String deviceType,
    String tokenId,
    Instant issuedAt,
    Instant expiresAt
) {}
```

- 每次新登录生成唯一 tokenId；保留传入的 userId 和 deviceType，不能更换身份。管理器会回读解析结果核对并设置线程快照，不保存记录，也不查重 tokenId。
- parse 必须完成验签等真实性校验；无效 Token 返回 null 或抛 `LoginException`。外部依赖故障应正常抛出，不能绕过验证。
- 签发时间不能在未来，过期时间必须晚于签发时间且未过期。自定义 Codec 以返回的 expiresAt 决定会话到期，不自动再套一层配置 ttl。
- 兼容旧 Token 未携带 deviceType：解析结果可为 null，不从服务端记录补齐；其他签名、用户、tokenId、有效期校验不放宽。新登录生成的 Token 仍需携带匹配的 deviceType。
- 提供自定义 Codec 后，无需默认 Codec 的 secret/issuer；签名策略由自定义实现负责。它不负责存储、注销、加载用户或设置线程上下文。

如果只想手动生成/解析，可注入 `JwtTokenCodec` 调用上述方法；它不设置线程上下文，也不验证账号密码，只能由可信服务端代码使用，不应封装成接收任意 userId 的公开登录接口。正常登录调用 `SecurityUtil.loginByUsername`，由 UserAuthenticator 验证账号密码，管理器核对签发结果并建立当前线程身份。手动签发的 JWT 满足相同验签、时间和用户检查时也可被接受，不需要服务端登记。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
