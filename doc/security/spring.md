# Spring Security 接入

[文档首页](../README.md) / [接口安全](README.md)

已有项目保留登录体系，业务侧统一使用 SecurityUtil、ISecurityManager 和权限注解。

## 本页导航

- [最小接入](#最小接入)
- [当前用户与权限](#当前用户与权限)
- [登录与注销](#登录与注销)
- [查询用户与刷新](#查询用户与刷新)
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

## 使用边界

- 只支持 Servlet 项目，不支持 WebFlux。
- 统一登录应在 HTTP 接口中、响应输出前调用；不用于后台线程凭空建立浏览器会话。
- 项目已有的多因素认证、验证码、访问控制和防跨站请求校验仍必须执行。
- 请求结束清理当前上下文；用户身份不会自动传到普通异步线程。
- 加密、限流、权限注解可以按需使用，不需要把第三方框架类型暴露给业务接口。

---

下一步：[统一登录与认证器](user.md)、[公开扩展接口](extension.md)。
