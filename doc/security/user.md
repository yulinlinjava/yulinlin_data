# 用户模型与安全管理器

[文档首页](../README.md) / [接口安全](README.md)

统一 `login(LoginRequest)`，通过 `LoginAuthenticator<T>` 扩展认证方式，用认证器 `name()` 记录本次登录的 `authType`。

## 本页导航

- [登录用户模型](#登录用户模型)
- [安全管理器](#安全管理器)
- [业务账号认证](#业务账号认证)
- [业务密钥认证](#业务密钥认证)
- [根据用户 ID 登录](#根据用户-id-登录)
- [自定义认证方式](#自定义认证方式)
- [一个业务组件实现两个接口](#一个业务组件实现两个接口)
- [常用组件扩展入口](#常用组件扩展入口)
- [加载用户信息](#加载用户信息)

## 登录用户模型

`com.yulinlin.security.auth.IUserDetails` 由业务安全 DTO 实现：

```java
public interface IUserDetails {
    String getUserId();
    String getUsername();
    Set<String> getRoles();
    Set<String> getPermissions();
    Object getAttribute(String name);
    // 接口还自带 hasRole(role)、hasPermission(permission)。
}
```

角色/权限使用 Set，执行精确且区分大小写的判断，不解释通配符；集合或检查参数为 null 时返回 false。未知 attribute 返回 null。用户模型不得包含密码、密码哈希、API Key、Token 等凭证；`authType` 属于某次登录，不放进用户模型。

> [Admin RBAC 案例](../practice/rbac.md)已按本 SPI 更新：同一业务 Bean 实现 LoginAuthenticator<PasswordLogin> 与 UserStore，Controller 使用 login(new PasswordLogin(...))。旧认证接口已移除；源码和相关用例已迁移，但本轮未编译、未执行测试。

## 安全管理器

```java
import com.yulinlin.security.auth.*;

// 以下是不同场景的替代写法，不需连续登录。
LoginResult result = SecurityUtil.login(new PasswordLogin(username, password, "web"));
LoginResult partner = SecurityUtil.login(new ApiKeyLogin(apiKey, "server"));
// 仅限服务端已完成可信外部验证的场景，详见下文。
LoginResult trusted = SecurityUtil.login(new UserIdLogin(verifiedUserId, "web"));
```

也可注入 `ISecurityManager` 调用 `login(request)`。PasswordLogin、ApiKeyLogin、UserIdLogin 省略/null 设备时默认 `default`，空白或首尾含空白的设备被拒绝。密码/密钥不 trim。业务验证码、外部授权回调验证应在相应业务认证流程完成；设备不是身份凭证，不自动读取加密用 Device-Type 请求头。

`LoginResult(token, expiresAt, user, authType)` 返回安全用户及登录信息。JWT/Sa-Token 返回 Token；Spring 默认 Session 模式 token/expiresAt 为 null。`authType` 取自实际成功认证器的 name，不是客户端参数，也不是 Java 类名或 Bean 名。

```java
String type = SecurityContext.getAuthType(); // 例如 password、api-key、sms、user-id
String same = SecurityUtil.getAuthType();
IUserDetails current = SecurityUtil.getUserDetails(); // 匿名为 null
IUserDetails required = SecurityUtil.requireUser();  // 匿名抛 LoginException
String userId = SecurityUtil.getUserId();
SecurityUtil.hasRole("admin");
SecurityUtil.hasPermission("video:update");
SecurityUtil.refresh();       // 刷新用户，不改变登录方式，不重新签发 Token
SecurityUtil.getUserDetails(otherUserId); // 只查询用户，不切换身份
SecurityUtil.logout();
```

JWT 将 authType 放入签名声明；Sa-Token 存在对应 Token-Session；Spring 统一登录保存到认证详情。后续请求恢复该字段。JWT/Sa-Token 缺少 authType 的旧凭证直接拒绝，需要重新登录；不伪造默认认证方式。原生外部 Spring 身份未提供这项信息时返回 null，不代表通过了 password 认证。

框架静态入口只委托 Spring 选定的管理器，单例初始化完成后可用，不在 Bean 构造器中调用。只选择一个登录模块，不叠加 JWT/Sa-Token/Spring。匿名管理器不支持登录。

## 业务账号认证

统一 SPI：

```java
public interface LoginAuthenticator<T extends LoginRequest> {
    String name();
    Class<T> requestType();
    IUserDetails authenticate(T request);
}
```

业务声明 Bean，框架启动收集并检查冲突。按**精确参数类**查找，不根据继承顺序/Bean 优先级猜测；同一 requestType 和同一 name 都不能重复。name 限 1～64 位字母、数字、点、下划线、冒号或短横线，并以字母或数字开头。启动时固定注册名称，运行中不要修改它。

密码认证示例（AccountService 是业务服务，不是框架类）：

```java
import com.yulinlin.security.auth.*;
import org.springframework.stereotype.Component;

@Component
public class PasswordAuthenticator implements LoginAuthenticator<PasswordLogin> {
    private final AccountService accounts;
    public PasswordAuthenticator(AccountService accounts) { this.accounts = accounts; }
    @Override public String name() { return "password"; }
    @Override public Class<PasswordLogin> requestType() { return PasswordLogin.class; }
    @Override public IUserDetails authenticate(PasswordLogin request) {
        // 按 username 查询，验证密码哈希、账号状态，再返回不含凭证的 DTO。
        return accounts.authenticate(request.username(), request.password());
    }
}
```

框架不内置用户表、明文密码比较或密码规则。认证拒绝时返回 null 或抛 LoginException，账号不存在与密码错误应使用相同提示；存储/网络故障正常抛出。无匹配认证器明确报错，不回退到 user-id。认证器不能递归调用 login，也不能只查到用户就跳过凭证验证。密码认证器和密钥认证器都需要业务提供，没有注册仍可启动并恢复有效的已有凭证。

## 业务密钥认证

```java
@Component
public class PartnerAuthenticator implements LoginAuthenticator<ApiKeyLogin> {
    private final PartnerService partners;
    public PartnerAuthenticator(PartnerService partners) { this.partners = partners; }
    @Override public String name() { return "api-key"; }
    @Override public Class<ApiKeyLogin> requestType() { return ApiKeyLogin.class; }
    @Override public IUserDetails authenticate(ApiKeyLogin request) {
        return partners.authenticateApiKey(request.apiKey());
    }
}
```

PartnerService 是业务服务，负责验证密钥摘要、有效期、停用状态和绑定账号状态。框架拒绝空白及超过 8192 字符的密钥。密钥与 AES/JWT 签名密钥分开，使用高强度随机值、摘要存储、HTTPS、限流，不放 URL 或日志。浏览器写死共享密钥不构成可靠用户身份。参见 [OWASP REST 安全建议](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html)。

**密钥登录是验证一次后换取 Token/会话，不是每次请求传 X-Api-Key。**停用密钥不自动撤销已经发出的凭证。JWT/Sa-Token 后续通过 UserStore.load(userId) 恢复权限，推荐绑定独立服务账号；不能只在认证时临时缩减权限，后续却加载更大的用户权限。需要每把密钥独立权限/即时撤销时，业务扩展 Token/会话恢复校验，不由 authType 自动实现。

HTTP 使用明确参数类，不允许客户端指定任意 Java 类型。两个接口共用统一入口：

```java
import com.yulinlin.security.annotation.*;
import com.yulinlin.security.auth.*;
import com.yulinlin.starter.domain.R;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@ApiLogin
public class AuthController {
    @ApiPublic
    @ApiLimit(count = 10, interval = "1m")
    @PostMapping("/password")
    public R<LoginResult> password(@RequestBody PasswordLogin request) {
        return R.ok(SecurityUtil.login(request), "登录成功");
    }
    @ApiPublic
    @ApiLimit(count = 10, interval = "1m")
    @PostMapping("/api-key")
    public R<LoginResult> apiKey(@RequestBody ApiKeyLogin request) {
        return R.ok(SecurityUtil.login(request), "登录成功");
    }
    @PostMapping("/me")
    public R<IUserDetails> me() { return R.ok(SecurityUtil.requireUser()); }
    @PostMapping("/logout")
    public R<Void> logout() {
        SecurityUtil.logout();
        return R.ok(null, "注销成功");
    }
}
```

示例使用项目 starter 的 R，业务将 LoginException 统一转为 HTTP 401；Spring 原生 URL 放行/CSRF 以及启用的报文加密规则仍需遵守。密钥接口 JSON 为 `{"apiKey":"业务密钥","deviceType":"server"}`，后续使用返回的 Token。登录前移除失效旧 Token，默认拦截器对公开接口携带无效凭证也会拒绝。

## 根据用户 ID 登录

存在唯一/Primary UserStore 且业务没有注册 UserIdLogin 认证器时，自动提供默认 `UserIdLoginAuthenticator`，name 为 `user-id`，按 ID 加载用户并检查返回 ID 一致；UserStore 应拒绝禁用/删除用户。自定义 LoginAuthenticator<UserIdLogin> Bean 可替换默认行为，不需要重复注册默认类。

```java
// verifiedUserId 必须来自服务端已经验证的授权结果，不能直接来自客户端参数。
String verifiedUserId = externalLogin.verifyCallback(callback);
LoginResult result = SecurityUtil.login(new UserIdLogin(verifiedUserId, "web"));
```

**用户 ID 不是身份凭证**。默认实现只检查用户可用性，不负责外部回调验签、验证码或管理员代登录权限。禁止公开 `/login/user-id` 接口直接接收任意用户 ID；不要把 UserIdLogin 加入公开 JSON 多态类型白名单。需要对外短信/第三方登录时，定义对应 LoginRequest，在自己的认证器内完成验证，才能准确记录 sms/oauth 等 authType，而非笼统的 user-id。

## 自定义认证方式

```java
public record SmsLogin(String phone, String code, String deviceType) implements LoginRequest {
    public SmsLogin { if (deviceType == null) deviceType = "default"; }
    @Override public String toString() { return "SmsLogin[凭证已隐藏]"; }
}

@Component
public class SmsAuthenticator implements LoginAuthenticator<SmsLogin> {
    private final AccountService accounts;
    public SmsAuthenticator(AccountService accounts) { this.accounts = accounts; }
    @Override public String name() { return "sms"; }
    @Override public Class<SmsLogin> requestType() { return SmsLogin.class; }
    @Override public IUserDetails authenticate(SmsLogin request) {
        // 业务还应校验验证码有效期、使用次数、账号状态和防重放。
        return accounts.authenticateSms(request.phone(), request.code());
    }
}

// 在已经配置对应认证器的业务中调用
SecurityUtil.login(new SmsLogin(phone, code, "web"));
String type = SecurityContext.getAuthType(); // sms
```

不新增枚举值、不修改管理器分支。不同密码认证体系可定义不同参数类和 name；同一 ApiKeyLogin 类型不能通过注册多个 Bean 随机选择。需要区分认证器内部的多个密钥账户，用用户 ID/业务元数据表达，name 表示认证方式，不是每把密钥的 ID。

内置参数类 toString 隐藏凭证，但显式 JSON 序列化仍包含请求字段；自定义参数也应隐藏敏感信息，不记录完整登录请求/响应，不把 LoginRequest 放入会话或用户 DTO。

## 一个业务组件实现两个接口

可以让业务类同时实现 `LoginAuthenticator<PasswordLogin>` 和 `UserStore`，分别实现 name、requestType、authenticate(PasswordLogin)、load(userId)。不再使用旧 UserAuthenticator / ApiKeyAuthenticator，也不再调用 loginByUsername。

认证时根据凭证验证用户，后续请求按稳定 userId 恢复，不把用户名当 ID。一个类不能同时实现同一个泛型接口的两个不同类型实例；多种登录方式分别提供认证器 Bean，可以复用同一个 AccountService。

## 常用组件扩展入口

| 能力 | 扩展点 | 接入方式 |
| --- | --- | --- |
| 安全用户模型 | IUserDetails | 不含凭证的业务 DTO |
| 任意认证方式 | LoginAuthenticator<T> + LoginRequest | 提供 Bean，精确类型分发，name 作为 authType |
| 用户资料与权限 | UserStore | 按稳定用户 ID 加载；可缓存但业务负责失效 |
| JWT 生成/验签 | JwtTokenCodec | generate(user, deviceType, authType) 与 parse 成对替换 |
| Token 读取 | LoginInterceptor | Bean 自动替换并注册，默认生命周期清理仍在 |
| Spring 认证链 | LoginAuthenticatorAuthenticationProvider | 将桥接 Provider 加入业务 AuthenticationManager，详见 [Spring 接入](spring.md) |
| 整体安全实现 | ISecurityManager | 实现 login(LoginRequest)、恢复、注销等；可适配外部上下文 |

包名前缀为 com.yulinlin.security，公共协议位于 auth；JWT/Sa-Token/Spring 扩展分别在 jwt/satoken/spring。认证名称来自验证流程，不因 HTTP URL、类名或 Bean 名变化而变化。authType 不代替角色权限，也不自动实现 MFA。

## 加载用户信息

```java
@Bean
public UserStore userStore(AccountService accounts) {
    return userId -> accounts.loadSecurityUser(userId);
}
```

UserStore.load 不验证密码、不签发 Token，不递归读取 SecurityContext。不存在/禁用用户返回 null 或抛 LoginException；基础设施错误正常抛出，不能返回默认用户，返回 ID 必须与查询 ID 相同。

JWT/Sa-Token 认证器返回的安全用户直接用于当前登录，下一次请求才按 ID 重新加载；请求内复用快照。业务缓存需要自行处理角色/状态变更，refresh 不会替业务清理缓存。Spring 默认映射原生 Authentication，不自动按 UserStore 重新查库。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
