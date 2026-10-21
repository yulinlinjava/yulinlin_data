# Spring Security 接入

[文档首页](../README.md) / [接口安全](README.md)

保留原生过滤器链；统一 login(LoginRequest) 通过 AuthenticationManager 调用 LoginAuthenticator，保存本次认证器 name 为 authType。

## 本页导航

- [最小接入](#最小接入)
- [当前用户与权限](#当前用户与权限)
- [自定义用户映射](#自定义用户映射)
- [登录与注销](#登录与注销)
- [账号密码登录配置](#账号密码登录配置)
- [密钥登录配置](#密钥登录配置)
- [设备类型与自定义会话策略](#设备类型与自定义会话策略)
- [查询用户与刷新](#查询用户与刷新)
- [生命周期与能力边界](#生命周期与能力边界)

## 最小接入

在已有 Spring Security 的 Servlet 项目引入 `com.yulinlin:security-spring:3.0`，不要同时引入 security-jwt/security-sa-token。Spring 自己的 OAuth2 Resource Server 属于原有认证体系，不是本仓库的 security-jwt。

模块依赖 Spring Security core/web API，版本由应用 BOM 管理。不创建 AuthenticationManager、SecurityFilterChain、用户表或 Controller；自动提供 SpringUserMapper、可替换的上下文存储/会话策略以及 LoginAuthenticatorAuthenticationProvider Bean，但**不会擅自把 Provider 加入已有 Manager**。

仅桥接原生登录不需要 UserStore 或 LoginAuthenticator；需要统一 login(LoginRequest) 时，按下文注册认证器，并将桥接 Provider 加入实际使用的 AuthenticationManager。未配置认证器/Provider 会拒绝，不回退到其他登录方式。

## 当前用户与权限

```java
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.SecurityUtil;

IUserDetails user = SecurityContext.getUser();
String userId = SecurityUtil.getUserId();
String authType = SecurityUtil.getAuthType();
boolean admin = SecurityUtil.hasRole("ADMIN");
boolean canEdit = SecurityUtil.hasPermission("video:edit");
```

默认 mapper 忽略 null、未认证和匿名 Authentication。principal 实现安全 IUserDetails 时使用其 userId/username，否则使用 Authentication.getName()（不一定是数据库 ID）。ROLE_ADMIN 转成角色 ADMIN；权限保留 authorities 原字符串，包括 ROLE_、SCOPE_ 前缀。不复制密码、credentials、details 到用户 DTO。

统一登录的 authType 来自已选认证器 name，保存在 SpringLoginDetails，后续从同一 Authentication 恢复。已有原生表单/OAuth2/Basic 登录如果没有可信的 SpringLoginDetails，authType 为 null，**不猜测 password，也不使用请求参数补齐**。这是外部身份桥接，不是对旧框架 Token 的兼容。

@ApiLogin、@ApiRole、@ApiPerm 继续使用；不自动解释 RoleHierarchy、SpEL 或 PermissionEvaluator。@ApiPublic 不能绕过 Spring 原生 URL 授权和 CSRF。

## 自定义用户映射

提供 `com.yulinlin.security.spring.SpringUserMapper` Bean 替换默认映射。返回不含凭证的 IUserDetails，按原生认证结果取得真实业务 ID，不能从请求参数取身份。

```java
@Bean
public SpringUserMapper springUserMapper() {
    DefaultSpringUserMapper defaults = new DefaultSpringUserMapper();
    return authentication -> {
        // MyLoginUser 是业务已有 principal；实际项目须兼容所有正在使用的 principal 类型。
        MyLoginUser principal = (MyLoginUser) authentication.getPrincipal();
        IUserDetails permissions = defaults.map(authentication);
        return new DefaultSpringUserMapper.UserSnapshot(
                principal.getUserId(), principal.getUsername(),
                permissions.getRoles(), permissions.getPermissions());
    };
}
```

统一桥接 Provider 返回的 principal 是 LoginAuthenticator 返回的安全 IUserDetails，默认 mapper 可直接处理。若原项目自定义 mapper 只接受一种 Spring UserDetails，需增加此分支，不能直接复制上述强转示例。mapper 不递归读取当前 SecurityContext。

## 登录与注销

```java
SecurityUtil.login(new PasswordLogin(username, password, "web"));
SecurityUtil.login(new ApiKeyLogin(apiKey, "server"));
// 仅限服务端已完成可信验证；需 UserStore 或自定义 UserIdLogin 认证器。
SecurityUtil.login(new UserIdLogin(verifiedUserId, "web"));
```

以上是替代调用，不需连续登录。统一链路为：参数类型查注册表 → LoginRequestAuthenticationToken → AuthenticationManager → LoginAuthenticatorAuthenticationProvider → 对应 LoginAuthenticator → 原生会话策略/存储。

成功返回 `LoginResult(null, null, safeUser, authType)`，默认建立 Session，浏览器继续携带 Cookie，不把 Session ID 冒充 Token。请求必须处于有响应的 Servlet 上下文，且响应未提交。认证结果必须是可设置详情的 AbstractAuthenticationToken、已认证且非匿名；保存前清除 credentials，仍含凭证则拒绝。认证失败清理线程和已保存的本次上下文，底层存储失败正常抛出，不伪装成功。

注销可继续走原入口；MVC 中 `SecurityUtil.logout()` 委托当前 Spring 包装请求的 HttpServletRequest.logout()，复用现有 LogoutHandler。没有 Servlet API 集成会明确报错，不用清理线程冒充注销。不代替 LogoutSuccessHandler、OIDC/SAML 远端退出流程。参见 [Spring Servlet API](https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/servlet-api.html)及[注销流程](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/logout.html)。

## 账号密码登录配置

1. 注册 [LoginAuthenticator<PasswordLogin>](user.md#业务账号认证)。可以继续复用业务已有密码验证服务，不新增明文比较。
2. 把自动提供的 LoginAuthenticatorAuthenticationProvider 加到**统一入口实际注入的 AuthenticationManager Bean**。
3. 与过滤器链共用同一 Manager、SecurityContextRepository、SessionAuthenticationStrategy。

只使用统一认证器的新项目，Manager 可以这样定义：

```java
import com.yulinlin.security.spring.LoginAuthenticatorAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;

@Bean
public AuthenticationManager authenticationManager(LoginAuthenticatorAuthenticationProvider provider) {
    return new ProviderManager(java.util.List.of(provider));
}
```

已有项目不要直接覆盖原 Manager：将此 Provider 合并到原来的 ProviderManager，同时保留原有密码/其他认证 Provider、事件发布、父 Manager 及必要策略。仅声明一个 Provider Bean，并不保证另一个已经构建的 Manager 会自动发现它。

如果需要复用现有 DaoAuthenticationProvider 等密码验证，可在 LoginAuthenticator<PasswordLogin> 中调用**特定原生密码 Provider**的 authenticate(UsernamePasswordAuthenticationToken.unauthenticated(...))，检查其认证结果并通过 SpringUserMapper 转成安全用户。不要递归调用 SecurityUtil.login，也不要让包含统一桥接 Provider 的同一个 Manager 相互递归。原生密码 Provider 不能直接处理 LoginRequestAuthenticationToken。

在已有过滤器链中合并以下配置，不另建覆盖全部请求的链：

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http,
        AuthenticationManager manager, SecurityContextRepository repository,
        SessionAuthenticationStrategy strategy) throws Exception {
    http.authenticationManager(manager)
        .securityContext(context -> context.securityContextRepository(repository))
        .sessionManagement(session -> session.sessionAuthenticationStrategy(strategy))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/auth/password", "/auth/api-key").permitAll()
            .anyRequest().authenticated());
    // 保留原有 CSRF、Session、注销和其他认证配置。
    return http.build();
}
```

Controller 示例见[统一登录接口](user.md#业务密钥认证)。`permitAll()` 不关闭 CSRF，Session/Cookie 登录仍需获取 CSRF Token 并携带请求头，不要全局关闭。原生登录过滤器先于 MVC，不会自动解密 ApiCrypto 请求。参考 [Spring 自定义认证](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passwords/index.html#publish-authentication-manager-bean)及[会话管理](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html)。

## 密钥登录配置

注册 [LoginAuthenticator<ApiKeyLogin>](user.md#业务密钥认证)，同一个桥接 Provider 自动处理，不再需要专门的 ApiKeyAuthenticationToken/ApiKeyAuthenticator。短信、第三方回调等自定义 LoginRequest 同理。

```java
LoginResult result = SecurityUtil.login(new ApiKeyLogin(apiKey, "server"));
String type = result.authType(); // 取该认证器的 name，例如 api-key
```

原始密钥只参与验证，不写入 principal/details。停用密钥不自动撤销已建立的 Session，立即失效由原生会话体系处理。基于 ID 的登录本身不验证凭证，绝不能把 UserIdLogin 作为公开免验证登录入口。

## 设备类型与自定义会话策略

- 内置参数类设备默认 default；所有自定义参数需要返回非空、不含首尾空白的设备类型。
- 认证请求及结果 details 使用 SpringLoginDetails，getDeviceType()/getAuthType() 提供元数据。提供者的其他详情放在 getProviderDetails()，不得包含原始凭证。使用共享 Session 序列化时，业务 principal/详情必须符合其存储要求。
- 默认 SecurityContextRepository 为请求属性 + HttpSession 的 DelegatingSecurityContextRepository。自定义同类型 Bean 可替换，须与恢复请求的过滤器链共用；NullSecurityContextRepository 不会产生跨请求会话。
- 默认 SessionAuthenticationStrategy 执行 Session ID 更换和 CSRF 轮换；CSRF 仓库使用业务 Bean，未提供时使用 HttpSessionCsrfTokenRepository。自定义策略保留必要防护。
- 同设备互踢、并发会话限制、Remember-Me、成功/失败 Handler、MFA 不会自动从另一条链复制。依赖这些流程的业务保留原入口，或完整接入策略，不能用新登录路径绕过必需的多因素认证。
- STATELESS/OAuth2 Resource Server 继续使用原有凭证体系，不为调用此 Session 登录入口强行混入 HttpSession。

## 查询用户与刷新

getUserDetails()/requireUser()/hasRole()/hasPermission() 映射当前 Authentication；getAuthType() 读取其可信登录详情。getToken() 为 null，不泄露密码、Session ID 或 credentials。

getUserDetails(userId) 有 UserStore 时按 ID 查询，否则只能读取当前同 ID 用户；查询其他用户明确报错。refresh() 重新映射当前认证，不自动重查 UserDetailsService、不重认证、不修改 authType 或续期。refresh(otherId) 只查询，不切换身份。

## 生命周期与能力边界

Spring 原生过滤器恢复/清理自己的上下文，框架 MVC 包装器清理自己的线程快照。Spring 适配不在 ThreadLocal 重复缓存用户；原生过滤器在 MVC 前结束请求也不会遗留框架身份。SecurityContext.clear() 不是注销，原生身份有效时再次读取仍恢复。

后台线程不自动继承身份；按原生体系显式传递和清理。本适配只支持 Servlet，不支持 WebFlux Reactor 上下文。静态桥接跟随选定的 ISecurityManager，业务自行替换管理器时需承担对应协议。

旧账号认证 SPI 与旧 loginByUsername 已移除，只有 login(LoginRequest)。admin 内相关 Spring 适配回归用例已按统一入口更新，admin 业务仍只接入 JWT。本轮仅源码与文档静态检查，未编译、未运行测试。
