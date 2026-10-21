# 自定义安全实现与拦截器

[文档首页](../README.md) / [接口安全](README.md)

按需要替换公开接口，不需要了解默认实现的内部类或第三方调用链。

## 本页导航

- [选择扩展点](#选择扩展点)
- [自定义有状态登录](#自定义有状态登录)
- [自定义登录拦截器](#自定义登录拦截器)
- [接入外部安全框架](#接入外部安全框架)

## 选择扩展点

| 需求 | 公开扩展点 | 注册方式 |
| --- | --- | --- |
| 账号密码、业务密钥或其他认证方式 | LoginRequest + LoginAuthenticator<T> | 为每种请求类型提供认证器 Bean，name 记录为 authType |
| 加载用户资料、角色与权限 | UserStore | 提供按 userId 加载安全用户的 Bean |
| 替换 JWT 生成和解析 | JwtTokenCodec | 提供一个 Bean，生成和解析成对替换 |
| 替换接口请求解密和响应加密 | CryptoCodec | 提供一个 Bean，无需框架 AES 密钥，见[接口加解密扩展](crypto-codec.md) |
| 自定义凭证读取与请求身份恢复 | LoginInterceptor | 提供一个 Bean，自动替换并注册 |
| 完整自定义登录或接入已有认证体系 | ISecurityManager | 提供一个 Bean，按需要配套 LoginInterceptor |

认证示例见[用户模型](user.md)，Token 协议见[自定义 Token](token-codec.md)。只需要修改一项能力时，不必替换整个安全管理器。

## 自定义有状态登录

需要注销后立即失效等服务端登录状态时，可以选择 [security-sa-token](sa-token.md)，或实现自己的 ISecurityManager。security-jwt 是无状态实现，不能通过增加缓存配置自动变成有状态登录。

自定义有状态实现需要明确以下约定：

- 登录成功前保存可靠的登录记录，明确每个 Token 与登录记录的对应关系。
- 请求时验证凭证、有效期、登录记录和用户状态；存储故障不能当作验证成功。
- 注销时撤销对应登录记录，并清理当前请求身份。按用户撤销、重置密码失效等能力由业务自行定义。
- 多实例使用共享状态；需要跨重启恢复时还要配置持久化。丢失登录记录不能自动降级为无状态放行。
- 明确请求身份恢复和清理的责任，避免上一次请求的身份影响下一次请求。

如果只改变签名算法、密钥来源或 Token 格式，替换 JwtTokenCodec 即可；它不负责登录记录或注销撤销。

## 自定义登录拦截器

业务提供 LoginInterceptor Bean，框架就使用它；没有自定义 Bean 时使用默认实现。无需手动重复注册。Bean 返回类型应声明为 LoginInterceptor 或其具体实现；通常只提供一个，存在多个时用 @Primary 明确选择。

下面示例用于 security-jwt，或完全使用 SecurityContext 管理请求身份的自定义安全实现：客户端通过 X-Token 发送原始凭证，调用公开的 resolveSession 验证后恢复上下文。

```java
import com.yulinlin.security.auth.*;
import com.yulinlin.security.web.LoginInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;

@Bean
public LoginInterceptor loginInterceptor(ISecurityManager manager) {
    return new LoginInterceptor() {
        @Override
        public boolean preHandle(HttpServletRequest request,
                                 HttpServletResponse response, Object handler) throws Exception {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
            String token = request.getHeader("X-Token");
            if (token == null) return true; // 匿名请求，是否允许访问由权限声明决定
            if (token.isBlank() || token.length() > 8192
                    || token.chars().anyMatch(Character::isWhitespace)) {
                response.sendError(401, "登录凭证无效");
                return false;
            }
            LoginSession session;
            try {
                session = manager.resolveSession(token);
            } catch (LoginException error) {
                response.sendError(401, "登录已失效，请重新登录");
                return false;
            }
            if (session == null || session.expired() || !token.equals(session.token())) {
                response.sendError(401, "登录已失效，请重新登录");
                return false;
            }
            SecurityContext.set(session);
            return true;
        }
    };
}
```

这是替换整个请求身份恢复行为，不只是新增一个请求头。manager.resolveSession 必须完成真实凭证验证；此示例不能直接替换 Sa-Token、Spring 或其他同时维护外部身份的请求处理。它们应保留模块默认凭证约定，或通过完整自定义接入统一管理两侧身份。不要将客户端传入的 userId 直接写入上下文。

框架负责自动注册和清理 SecurityContext，包括拒绝请求、异常、请求完成及异步切换。自定义实现若还维护其他请求状态，必须自行清理；基础设施异常正常传播，不能伪装成验证成功。

不需要启用开关，也不要重复注册同一个拦截器。只替换 Token 算法时，应使用 JwtTokenCodec，不必修改拦截器。

## 接入外部安全框架

已有 [Sa-Token](sa-token.md) 或 [Spring Security](spring.md) 项目可选择对应模块。其他认证体系只需引入公共 security，提供 ISecurityManager；如果已有请求身份恢复机制，再提供 LoginInterceptor 对接已验证身份。不要同时引入多个登录模块。

业务侧继续使用 SecurityUtil、ISecurityManager 和 ApiLogin/ApiRole/ApiPerm，不依赖具体登录实现。需要实现的公开行为如下：

| 方法 | 接入约定 |
| --- | --- |
| login(LoginRequest) | 按对应认证方式验证凭证，返回 LoginResult；未支持的方式明确拒绝，不降级为 ID 登录 |
| resolveSession(token) | 使用默认 Token 拦截器时必须实现：验证凭证并恢复 LoginSession，不依赖当前请求身份 |
| getUserDetails() | 返回当前已验证用户，匿名返回 null |
| getToken() / getAuthType() | 返回当前 Token 和可信认证来源；不适用或来源未知时为 null，不编造 |
| logout() | 按自身登录协议注销，并清理请求身份；无状态 JWT 不能宣称已撤销旧 Token |
| getUserDetails(userId) | 查询指定用户，不切换当前身份 |
| refresh() / refresh(userId) | 刷新用户资料，当前用户时更新当前请求身份；不切换用户，不隐式续期 |

注册形式如下，BusinessSecurityManager、BusinessIdentityService 均为业务自己实现的类，不是框架提供的类：

```java
@Bean
public ISecurityManager securityManager(BusinessIdentityService identities) {
    return new BusinessSecurityManager(identities);
}
```

使用框架上下文时，公开模型为：

```java
LoginSession session = new LoginSession(token, expiresAt, safeUser, authType);
SecurityContext.set(session);
LoginResult result = LoginResult.of(session);
```

以上代码只用于已经完成认证的实现内部，不能包装成接收任意 userId 的公开登录接口。safeUser 不得携带密码、API Key 等凭证。authType 来自成功认证器的 name；已有外部身份没有可信来源时可以为 null，不猜测为 password。JWT/Sa-Token 凭证要求有效的认证来源，不能随意省略。

resolveSession 验证失败可返回 null 或抛 LoginException；login 失败应抛异常，不返回 null 冒充成功。使用默认 Token 拦截器时，恢复结果中的 Token 必须与请求 Token 一致。

已有外部上下文时，可由管理器直接读取其当前已验证身份；若业务还要读取 SecurityContext，自定义 LoginInterceptor 应同步设置它。自定义请求状态由业务负责在成功、拒绝、异常及异步切换时清理。不要仅清理 SecurityContext 就宣称已退出外部登录。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
