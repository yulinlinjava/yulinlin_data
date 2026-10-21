# 自定义安全实现与拦截器

[文档首页](../README.md) / [接口安全](README.md)

有 LoginInterceptor Bean 就使用用户实现，否则自动提供默认实现。

## 本页导航

- [自定义有状态登录](#自定义有状态登录)
- [自定义登录拦截器](#自定义登录拦截器)
- [接入外部安全框架](#接入外部安全框架)

## 自定义有状态登录

`security-jwt` 不再提供登录存储或撤销 SPI。旧的 `JwtSessionStore`、`JwtSession`、`CaffeineJwtSessionStore`、`getByUserId/removeByUserId` 及 `jwt.maximum-size` 已移除，不能继续复制旧 Redis Store 示例。

需要在线会话、注销后立即失效、踢人或多设备管理时，优先选择 `security-sa-token`；需要自己的规则时，提供唯一的 `ISecurityManager` Bean（见[自定义与外部框架接入](#接入外部安全框架)），不要给无状态 JWT 默认链路偷偷增加存储。

业务自定义有状态实现至少负责：

- 登录成功前可靠写入服务端记录；明确是一条 Token 对应一个登录，还是按设备复用。
- 请求时验证凭证及记录有效性，再加载允许登录的用户；存储故障不能当作验证成功。
- 注销时同步撤销记录；按用户撤销、重置密码失效等由业务定义，不属于公共接口的默认能力。
- 多实例使用共享存储；若用 Redis，需考虑 TTL、持久化、故障及副本读一致性。登录记录丢失应拒绝登录，不能自动退回纯 JWT 放行。
- 明确请求上下文的恢复与清理归属，不让旧线程身份泄漏到下一请求。

如果自定义逻辑只是签名算法、密钥来源或 Token 格式，仍使用 `JwtTokenCodec`，无需实现整套管理器。若引入了撤销表或登录存储，就已经是有状态方案，文档和调用方都应明确这一点。

## 自定义登录拦截器

JWT 或自定义管理器仅需改变 Token 请求头时，继承默认实现，保留它的验证与上下文处理。Sa-Token 应优先修改其 `sa-token.token-name/token-prefix` 等原生配置，内置适配器会自动使用，无需另写拦截器。

```java
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.web.LoginContextInterceptor;
import com.yulinlin.security.web.LoginInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;

@Bean
public LoginInterceptor loginInterceptor(ISecurityManager manager) {
    return new LoginContextInterceptor(() -> manager) {
        @Override
        protected String resolveToken(HttpServletRequest request) {
            return request.getHeader("X-Token"); // 示例：直接携带 Token，不带 Bearer 前缀
        }
    };
}
```

框架发现业务 `LoginInterceptor` Bean 后使用它，否则使用默认实现；框架负责 MVC 注册和清理包装，无需手动 `addInterceptors`，避免重复执行。Bean 返回类型应声明为 `LoginInterceptor` 或具体实现，不要只暴露为普通 `HandlerInterceptor`。通常只提供一个；若确需多个 Bean，必须用 `@Primary` 明确选择。

旧的 `yulinlin.security.auth.enabled` 已移除，配置为 false 也不会阻止自动注册，请删除该配置。以前通过它接入外部框架的项目，应改为提供自定义 `LoginInterceptor` Bean。

完全定制时也可直接实现 `LoginInterceptor`，必须自己完成可信身份恢复：使用框架上下文时设置 `SecurityContext`；使用外部上下文时配合自定义 `ISecurityManager` 读取身份。不能只从请求里读出 userId 就认为已登录。框架包装器负责请求开始、拒绝请求、异常、完成及异步切换时清理 **SecurityContext**，不替自定义实现补做验签或清理外部框架的上下文。Token 算法变化优先替换 `JwtTokenCodec`，不必改拦截器。

## 接入外部安全框架

自定义实现不必同时引入两个登录模块，也不必改公共权限 AOP。分三种情况：

| 定制范围 | 实现/配置 | 请求身份由谁维护 |
| --- | --- | --- |
| 只改变 JWT 算法、密钥、声明格式 | security-jwt + 一个 JwtTokenCodec Bean | 框架默认拦截器和管理器 |
| 自己实现完整登录协议，继续使用框架拦截器 | security + 一个 ISecurityManager Bean | 你的 resolveSession 校验凭证，默认拦截器设置/清理 SecurityContext |
| 已有安全框架自带请求拦截与上下文 | security + 一个 ISecurityManager 适配 Bean + 一个 LoginInterceptor Bean | 自定义拦截器接入外部身份生命周期，管理器读取其当前用户 |

框架不要求自定义实现一定使用 UserStore；JWT/Sa-Token 用它恢复用户，使用 UserAuthenticator 验证账号密码；security-spring 只在按 ID 查询其他用户时可选使用 UserStore。自定义管理器可继承 `AbstractUserSecurityManager`，通过构造器传入 UserStore、UserAuthenticator，复用账号认证、加载和刷新，实现 protected createLogin(IUserDetails, deviceType)、logout、resolveSession；createLogin 只供内部已认证流程调用，不应扩大为 public。它不会替你实现存储、签名或撤销规则。

继续使用框架上下文时，各方法的要求是：

| 方法 | 实现要求 |
| --- | --- |
| loginByUsername(username, password, deviceType) | 可选覆盖：认证账号密码与状态，再建立登录；两参数默认方法自动使用设备 default；未覆盖时明确抛 UnsupportedOperationException |
| resolveSession(token) | 不依赖当前线程身份，从可信凭证恢复 LoginSession；检查签名/会话、有效期和用户状态，不能只解码或信任客户端 userId |
| logout() | 无状态仅清理上下文；有状态先执行自己的撤销，finally 清理上下文；不要声称 JWT 清理等于撤销 |
| getUserDetails(userId) | 读取指定用户，不切换当前身份 |
| refresh(userId) | 重载用户；为当前用户时重新校验凭证并更新快照，不把其他用户切换成当前用户，不隐式续期 |

resolveSession 校验失败返回 null 或抛 LoginException；loginByUsername 失败应抛异常，不能返回 null 假装登录成功。基础设施异常正常传播。LoginResult/LoginSession 中的 user 必须是安全 DTO，不能含密码等凭证；使用框架拦截器时返回的 Token 要与请求 Token 一致。自定义有状态实现的可靠存储约定见[自定义有状态登录](#自定义有状态登录)。

已经提供的 [Sa-Token](sa-token.md) 或 [Spring Security](spring.md) 适配按对应专题接入，不必自己重写管理器。其他外部框架：只引入公共 `security`，实现一个 `ISecurityManager` Bean；再提供一个 `LoginInterceptor` Bean，接入该框架请求身份的恢复与清理。不支持的统一 API 必须明确报错，不能凭 userId 绕过外部认证或只清理线程冒充注销。

如果还希望业务直接读取 `SecurityContext`，可让 **同一个 ISecurityManager 实现类**额外实现 `SecurityContextResolver`：实现 `resolveContext(LoginSession cached)`，每次按外部当前身份校验，身份未变可复用 cached.user()，已注销返回 null，基础设施故障正常抛出。Spring 选中的管理器自动接入，无需额外注册 Resolver Bean；解析过程和 UserStore 不得递归读取 SecurityContext。内置 Sa-Token 使用这个桥接，因此无需修改原来的 StpUtil.login/logout 代码。

不需要再实现适配器接口，也不强制使用 SecurityContext；覆盖 getUserDetails()、getToken() 从外部框架读取当前身份，此时 resolveSession 可以保留默认实现。实现 logout、getUserDetails(userId)、refresh(userId)，以及需要的当前用户刷新语义；账号密码入口按需覆盖 loginByUsername。旧 login(userId) 已从协议和工具类移除，不再实现或调用它。

下面是注册形式，放在业务 `@Configuration` 中。`ExternalAuth` 代表**业务自己封装的外部框架服务，不是本项目提供的类**；其中方法需按上表的安全和生命周期约定实现：

```java
import com.yulinlin.security.auth.ISecurityManager;
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.LoginResult;
import org.springframework.context.annotation.Bean;

@Bean
public ISecurityManager securityManager(ExternalAuth auth) {
    return new ISecurityManager() {
        @Override public LoginResult loginByUsername(String username, String password, String deviceType) {
            return auth.loginByUsername(username, password, deviceType);
        }
        @Override public void logout() { auth.logout(); }
        @Override public IUserDetails getUserDetails() { return auth.currentUser(); }
        @Override public String getToken() { return auth.currentToken(); }
        @Override public IUserDetails getUserDetails(String userId) {
            return auth.loadUser(userId);
        }
        @Override public IUserDetails refresh(String userId) {
            return auth.refreshUser(userId); // 当前用户时更新外部框架快照，不切换身份
        }
    };
}
```

配套的拦截器示例（同样放在业务配置类中；`restoreRequest`、`clearRequest` 是业务封装的方法）：

```java
import com.yulinlin.security.web.LoginInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Bean
public LoginInterceptor externalLoginInterceptor(ExternalAuth auth) {
    return new LoginInterceptor() {
        @Override
        public boolean preHandle(HttpServletRequest request,
                                 HttpServletResponse response, Object handler) throws Exception {
            boolean accepted = false;
            try {
                // 校验凭证并恢复外部框架身份；拒绝时写入响应并返回 false。
                accepted = auth.restoreRequest(request, response);
                return accepted;
            } finally {
                // preHandle 返回 false 或抛异常时，不会收到自己的 afterCompletion。
                if (!accepted) auth.clearRequest();
            }
        }

        @Override
        public void afterCompletion(HttpServletRequest request,
                                    HttpServletResponse response, Object handler, Exception ex) {
            auth.clearRequest();
        }

        @Override
        public void afterConcurrentHandlingStarted(HttpServletRequest request,
                                                  HttpServletResponse response, Object handler) {
            auth.clearRequest();
        }
    };
}
```

外部身份恢复必须验证真实凭证，不得信任客户端直接传入的用户 ID。若外部框架已有 Filter 完整管理请求身份，Filter 应先于 MVC 执行并在 finally 清理；自定义 LoginInterceptor 对接其已验证上下文即可，不要重复解析或重复注册同一拦截器。异步业务线程的身份传递需遵循外部框架自己的规则。

ApiLogin/ApiRole/ApiPerm 的 AOP 仍调用你的 ISecurityManager，加密和限流不受影响。框架包装器只兜底清理 SecurityContext，外部上下文仍由上述适配或外部 Filter 负责。若不提供自定义 LoginInterceptor，则启用默认实现，此时管理器必须实现 resolveSession，不能沿用上面省略该方法的示例。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
