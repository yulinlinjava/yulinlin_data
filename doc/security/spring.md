# Spring Security 接入

[文档首页](../README.md) / [接口安全](README.md)

已有项目保留登录体系，业务侧统一使用 SecurityUtil、ISecurityManager 和权限注解。

## 本页导航

- [最小接入](#最小接入)
- [当前用户与权限](#当前用户与权限)
- [登录与注销](#登录与注销)
- [查询用户与刷新](#查询用户与刷新)
- [会话管理接入](#会话管理接入)
- [分布式 Spring Session](#分布式-spring-session)
- [使用边界](#使用边界)

## 最小接入

已有 Spring Security 的 Servlet 项目引入 `com.yulinlin:security-spring:3.0`，不要同时引入 security-jwt 或 security-sa-token。

本模块用于接入已有认证体系，不替项目创建用户表、登录接口或完整认证配置。读取当前已登录用户不要求额外提供密码认证器。

**统一 login 入口需要项目先完成认证适配，仅引入模块或只声明 LoginAuthenticator，不保证登录入口已经可用。** 已有登录接口可以继续保留。本文只说明业务公开 API，不展开第三方认证配置和内部实现。

## 当前用户与权限

```java
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.SecurityUtil;

IUserDetails user = SecurityUtil.getUserDetails(); // 匿名为 null
IUserDetails required = SecurityUtil.requireUser(); // 未登录抛 LoginException
String userId = SecurityUtil.getUserId();
String authType = SecurityUtil.getAuthType();

boolean admin = SecurityUtil.hasRole("ADMIN");
boolean canEdit = SecurityUtil.hasPermission("video:edit");
```

业务用户 ID、角色和权限编码必须与当前项目的身份模型对应，不能默认把用户名当作数据库 ID。需要完整自定义身份适配时，使用[公开安全管理器接口](extension.md#接入外部安全框架)。

`@ApiLogin`、`@ApiRole`、`@ApiPerm` 可以继续使用。`@ApiPublic` 只豁免本框架相应的登录要求，不能绕过项目已有的访问控制或请求安全校验。

## 登录与注销

完成项目认证适配后，调用方式与其他模块相同：

```java
import com.yulinlin.security.auth.*;

LoginResult result = SecurityUtil.login(
        new PasswordLogin(username, password, "web"));

// 以下为其他场景，不需要连续调用
SecurityUtil.login(new ApiKeyLogin(apiKey, "server"));
SecurityUtil.login(new UserIdLogin(verifiedUserId, "web"));
```

账号密码和密钥分别提供对应 `LoginAuthenticator<T>`；可信 ID 登录需要 `UserStore` 或自定义同类型认证器。认证示例见[用户模型与安全管理器](user.md)。

默认会话登录返回的 `token`、`expiresAt` 可以为 null；不代表未登录，也不能把会话标识当成 Token 返回。客户端继续遵守项目已有的 Cookie、凭证和请求安全约定。

`authType` 来自成功认证器的 name；已有外部登录没有提供可信来源时为 null，不推测为 password。

```java
SecurityUtil.logout();
```

统一注销需要项目支持对应的注销能力，未完成接入时明确报错，应使用项目原有注销入口。不能用 `SecurityContext.clear()` 冒充注销。

## 查询用户与刷新

| API | 行为 |
| --- | --- |
| getUserDetails() | 获取当前用户，匿名返回 null |
| getUserDetails(userId) | 只查询用户，不切换身份；查询其他用户需要 UserStore |
| refresh() | 重新读取当前身份，不重新登录、不承诺重查业务数据库 |
| refresh(userId) | 当前用户按 refresh 处理，其他用户只查询 |
| getToken() | 默认会话登录返回 null |
| getAuthType() | 返回可信认证来源，没有该信息时返回 null |

业务权限修改后是否立即生效，取决于项目采用的用户资料刷新策略；不要把 refresh 当成所有请求权限的强制刷新。

## 会话管理接入

[统一会话管理](user.md#会话管理与数量限制)默认开启，数量默认不限。即使没有写 `session.enabled`，也必须在业务已有的有状态过滤器链构建前显式接入：

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http, SpringSessionSupport sessions) throws Exception {
    // 保留项目已有的认证、授权、CSRF、Remember-Me 等安全配置。
    // ...
    sessions.configure(http); // 放在最后，之后不要再覆盖会话策略、注册表或上下文存储
    return http.build();
}
```

`SpringSessionSupport` 位于 `com.yulinlin.security.spring`。它配置原生数量限制、注册表和失效检查，并将该链生成的原生 `SessionAuthenticationStrategy` 提供给统一 MVC 登录入口，避免原生登录与 `SecurityUtil.login` 使用不同策略。默认开启但未接入会启动失败。

升级已有项目时应完成上述接入，或显式设置 `yulinlin.security.session.enabled=false` 保留原有登录与会话策略。关闭时不提供 SpringSessionSupport，不需要调用 configure，也不会关闭原生 Session 或删除业务原有的会话配置。

默认适配一个有状态过滤器链；其他无状态链不接入该组件。需要多个独立 Session 认证体系时使用自定义实现，不重复调用 configure。开启时不要另外注册 SessionAuthenticationStrategy Bean；需要调整 fixation/CSRF 等，在 configure 之前定制该链原生配置。

原生定制案例见[Spring Cookie 登录](native-config.md#spring-cookie-登录案例)。使用 `sessionConcurrency(...)` 定制超限拒绝登录、`expiredUrl` 或失效响应，这些选项不会被统一接入覆盖。数量只配置 `max-sessions`，在接入前或之后再设置 `maximumSessions` / `SessionLimit` 都会启动报错（包括相同数值）。注册表和上下文仓库必须与注入的 Bean 相同，`STATELESS` / `NEVER`、接入后关闭会话或上下文配置也会报错；完整规则及版本兼容边界见[冲突说明](native-config.md#冲突与关闭行为)。

本地默认使用 `SessionRegistryImpl`，同时提供 `HttpSessionEventPublisher` 清理已销毁会话。Boot 会注册 Listener Bean；非 Boot Servlet 部署需按容器规则注册监听器。原生注销、过期事件必须正常送达，不能把注册表当成独立认证数据库。

默认 `SpringLoginSessionManager` 支持按用户查询、计数、指定/全部踢人，本地注册表还支持全局分页。它通过原生 expireNow 标记会话，后续请求由原生过滤器拒绝；不能直接删注册表记录冒充踢人。

原生 SessionRegistry 不提供 deviceType/deviceId/创建时间/到期时间，默认这些字段为 null，不声明 DEVICE_TYPE 能力。需要设备查询等附加元数据时，注册自定义 LoginSessionManager，并与实际 Session 生命周期保持一致。

## 分布式 Spring Session

引入并配置项目所需的 Spring Session 官方存储实现，再提供对应原生注册表，例如已有带用户索引的仓库时：

```java
@Bean
<S extends org.springframework.session.Session>
org.springframework.security.core.session.SessionRegistry sessionRegistry(
        org.springframework.session.FindByIndexNameSessionRepository<S> repository) {
    return new org.springframework.session.security.SpringSessionBackedSessionRegistry<>(repository);
}
```

框架自身不依赖 Spring Session/Redis，也不替项目配置连接。检测到 SessionRepository 时不会静默创建本地注册表；缺少配套 SessionRegistry 会启动失败。仅提供任意 SessionRepository 不代表具备用户索引能力。

默认 principal 映射规则：IUserDetails 使用 userId，UserDetails 使用 username，Principal 使用 name，String 直接作为 ID。对于分布式反查，默认使用 userId 作为原生索引名；如果业务 ID 与原生 principal 名称不一致（包括非标准 principal 的 getName/toString 规则），必须提供 SpringSessionPrincipalResolver，分别实现 userId(principal) 与 principal(userId)。自定义 SpringUserMapper 且使用默认管理器时也必须显式提供该映射器。

Spring Session 原生注册表不支持 getAllPrincipals，因此默认分布式适配不声明 GLOBAL_QUERY：按用户查询和踢人可用，全局查询会明确报不支持。需要跨节点全局列表时提供自定义 LoginSessionManager，通过可靠的查询索引实现；不能只枚举当前节点或扫描猜测的 Redis 私有结构。

## 使用边界

- 只支持 Servlet 项目，不支持 WebFlux。
- 统一登录应在 HTTP 接口中、响应输出前调用；不用于后台线程凭空建立浏览器会话。
- 项目已有的多因素认证、验证码、访问控制和防跨站请求校验仍必须执行。
- 请求结束清理当前上下文；用户身份不会自动传到普通异步线程。
- 加密、限流、权限注解可以按需使用，不需要把第三方框架类型暴露给业务接口。

---

下一步：[统一登录与认证器](user.md)、[公开扩展接口](extension.md)。
