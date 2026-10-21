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
    public String generate(IUserDetails user, String deviceType, String authType) {
        return tokens.issue(user.getUserId(), deviceType, authType);
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
    String authType,
    String tokenId,
    Instant issuedAt,
    Instant expiresAt
) {}
```

- 每次新登录生成唯一 tokenId；解析结果必须与生成时的 userId、deviceType 和 authType 一致，不能更换身份或认证来源。authType 来自成功认证的 LoginAuthenticator.name()，必须受签名保护。唯一标识不代表框架会保存登录记录或提供撤销功能。
- parse 必须完成验签等真实性校验；无效 Token 返回 null 或抛 `LoginException`。外部依赖故障应正常抛出，不能绕过验证。
- 签发时间不能在未来，过期时间必须晚于签发时间且未过期。自定义 Codec 以返回的 expiresAt 决定会话到期，不自动再套一层配置 ttl。
- 不兼容旧 Token：deviceType、authType 必须存在且有效；缺少时拒绝，不默认补成 password 或 user-id。authType 为 1～64 位字母、数字、点、下划线、冒号或短横线，且以字母或数字开头。
- 提供自定义 Codec 后，无需默认 Codec 的 secret/issuer；签名策略由自定义实现负责。它不负责存储、注销、加载用户或设置线程上下文。

如果只想手动生成/解析，可注入 `JwtTokenCodec` 调用上述方法；它不设置请求上下文，也不验证账号密码或业务密钥，只能由可信服务端代码使用，不应封装成接收任意 userId 的公开登录接口。正常登录调用 `SecurityUtil.login(LoginRequest)`，通过对应认证器验证后获得登录结果和当前身份。业务已完成外部验证时可使用 `UserIdLogin`，见[可信 ID 登录](user.md#根据用户-id-登录)。手动签发的 JWT 满足相同验签、时间、认证信息和用户检查时也可被接受，不需要服务端登记。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
