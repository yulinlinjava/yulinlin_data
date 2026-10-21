# 用户模型与安全管理器

[文档首页](../README.md) / [接口安全](README.md)

IUserDetails、UserAuthenticator、UserStore、安全协议与扩展入口。

## 本页导航

- [登录用户模型](#登录用户模型)
- [安全管理器](#安全管理器)
- [业务账号认证](#业务账号认证)
- [一个业务组件实现两个接口](#一个业务组件实现两个接口)
- [常用组件扩展入口](#常用组件扩展入口)
- [加载用户信息](#加载用户信息)

## 登录用户模型

完整业务接入参考 [Admin 两表 RBAC 与无状态 JWT 登录案例](../practice/rbac.md#admin-两表-rbac-与登录案例)：统一调用 loginByUsername，`AdminAuthenticationService` 一个 Bean 同时实现 UserAuthenticator、UserStore，分别负责账号认证和按 ID 加载用户。admin 只引入 security-jwt，不保存登录记录，不实现安全管理器或撤销逻辑；本轮源码迁移未编译、未执行测试。

登录用户模型只约束最小身份、角色和权限协议，不规定用户表、登录流程、密码校验、Token，以及权限数据如何存储、加载。业务用户模型按需实现 `IUserDetails`：

```java
import com.yulinlin.security.auth.IUserDetails;

import java.util.Set;

public class LoginUser implements IUserDetails {
    private String userId;
    private String username;
    private Set<String> roles;
    private Set<String> permissions;
    private String tenantId;

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public Object getAttribute(String name) {
        return switch (name) {
            case "tenantId" -> tenantId;
            default -> null;
        };
    }
}
```

`roles` 和 `permissions` 使用 `Set` 表达唯一集合。接口自带 `hasRole(role)` 和 `hasPermission(permission)`，执行精确且区分大小写的集合判断；参数、角色集合或权限集合为 `null` 时返回 `false`。框架不自动增加 `ROLE_` 前缀，也不解释通配符。

```java
if (loginUser.hasRole("admin")) {
    // 当前用户具有 admin 角色
}

if (loginUser.hasPermission("video:update")) {
    // 当前用户可以修改视频
}
```

`getAttribute(name)` 只提供按名称读取，不暴露可修改的元数据 Map，也不强制业务使用 Map 存储。未知属性返回 `null`。

该模型不应包含密码、密码哈希、AES 密钥、Token、签发时间、过期时间、IP 或设备 ID。密码和 Token 属于认证凭证，IP 和设备属于单次登录或请求上下文，不属于用户本身。

### 安全管理器

统一公开账号密码登录入口 `loginByUsername`。**原来的 login(userId)、login(userId, deviceType) 已移除**，不是废弃后仍可调用，也不能改名成别的公开 ID 登录方法绕过认证。框架内部仍使用认证结果中的真实 userId 建立 JWT/Sa-Token 登录。

JWT / Sa-Token 通过业务 `UserAuthenticator` 验证账号密码并返回安全用户；[security-spring](spring.md) 继续通过 `AuthenticationManager`，不使用这个业务 SPI。三个实现都可以使用同样的调用形式，不规定用户表或密码存储规则：

```java
import com.yulinlin.security.auth.LoginResult;
import com.yulinlin.security.auth.SecurityUtil;

// 有验证码时业务先校验，账号密码校验由所选实现委托认证组件。
LoginResult result = SecurityUtil.loginByUsername(username, password, "web");
// Controller 将 result 包装成项目的 R<LoginResult> 返回。
```

账号不存在和密码错误应使用相同对外提示，认证失败抛 `LoginException`；基础设施错误不要伪装成登录成功。不要把用户名直接当作 userId，也不能只查询到用户就视为密码校验通过。

```java
SecurityUtil.loginByUsername(username, password);        // deviceType 默认 "default"
SecurityUtil.loginByUsername(username, password, "web"); // 设备类型由本次登录明确传入
SecurityUtil.getUserDetails();    // 当前用户，未登录为 null
SecurityUtil.requireUser();       // 当前用户，未登录抛 LoginException
SecurityUtil.getUserDetails(userId); // 读取指定用户，不切换当前身份
SecurityUtil.getToken();
SecurityUtil.hasRole("admin");
SecurityUtil.hasPermission("video:update");
SecurityUtil.refresh();           // 重新加载当前用户
SecurityUtil.refresh(userId);     // 指定用户；不是当前用户时仅加载并返回
SecurityUtil.logout();            // JWT：清理线程；Sa-Token：注销当前会话并清理线程
SecurityUtil.manager();           // 获取底层 ISecurityManager，也可以直接注入它
```

注入的 ISecurityManager 同样提供两个 loginByUsername 重载，与静态入口一致。Spring 配置见[账号密码登录配置](spring.md#账号密码登录配置)。JWT/Sa-Token 未提供 UserAuthenticator 时仍可启动和恢复既有凭证，但调用账号密码登录会明确报错，不回退到按用户名/ID 直接签发。默认匿名管理器不支持登录，抛 UnsupportedOperationException。

登录成功返回 `LoginResult(token, expiresAt, user)`。`user` 为不含密码的 `IUserDetails`，不要返回含凭证的 ORM 用户实体；Spring 默认 Session 登录的 token/expiresAt 为 null。登录设备与加密用的 `Device-Type` 请求头不是自动绑定关系。

`SecurityUtil` 只是静态委托入口，应用单例初始化完成后可用；不要在 Bean 构造器等过早阶段调用。普通业务也可以直接注入唯一的 `ISecurityManager`。不需要设计多管理器选择或运行时切换。

### 业务账号认证

JWT / Sa-Token 使用一个 `UserAuthenticator` Bean，Spring Security 不需要它：

```java
import com.yulinlin.security.auth.UserAuthenticator;

@Bean
public UserAuthenticator userAuthenticator(AccountService accounts) {
    return (username, password) -> accounts.authenticate(username, password);
}
```

`AccountService` 是业务服务。这里的 authenticate 必须验证密码哈希及用户状态，返回包含真实 userId、角色、权限的 `IUserDetails` 安全 DTO，不含密码/密码哈希。返回 null 或抛 LoginException 表示拒绝；数据服务故障正常抛出。不能返回未经验证的“根据 username 查到的对象”，不能在认证实现内再次调用 loginByUsername。

认证结果直接用于本次登录，内部不为了同一结果再调用一次 UserStore。后续请求恢复身份仍通过 UserStore.load(userId) 重新加载状态和权限。两个接口可以由同一个业务组件实现，但各自职责不能混淆；验证码在 Controller/业务入口先校验，不扩展为框架统一密码规则。

旧代码迁移：将原 Controller 里的账号密码验证抽到 UserAuthenticator，Controller 改调 loginByUsername。不要继续保留旧 login 的覆盖方法；自定义 ISecurityManager 按需实现三参数 loginByUsername。Sa-Token 的原生 StpUtil.login/logout 不属于被移除的框架 API，已有原生接入可以不改。

### 一个业务组件实现两个接口

两个接口不要求两个实现类。推荐小型项目提供一个业务组件；**与上述独立 @Bean 注册方式二选一，不要重复注册**：

```java
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.UserAuthenticator;
import com.yulinlin.security.auth.UserStore;
import org.springframework.stereotype.Service;

@Service
public class AccountSecurityService implements UserAuthenticator, UserStore {
    private final AccountService accounts;

    public AccountSecurityService(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public IUserDetails authenticate(String username, String password) {
        // 业务按用户名查账号，验证密码哈希和状态，返回安全 DTO。
        return accounts.authenticate(username, password);
    }

    @Override
    public IUserDetails load(String userId) {
        // 业务按稳定 ID 加载最新资料和权限，拒绝已删除/停用用户；不再比较密码。
        return accounts.loadSecurityUser(userId);
    }
}
```

AccountService 仍是业务服务，两个示例方法按上述契约实现，不是框架提供的类。框架通过不同接口注入同一个 Bean：登录使用 username/password，恢复身份使用已验证 Token 中的 userId。用户名修改后仍按原 ID 找到该用户；不要因此把旧 Token 的 ID 当成新用户名查询。

完整实现见 [AdminAuthenticationService](../../admin/src/main/java/com/yulinlin/admin/rbac/auth/AdminAuthenticationService.java)。本例仅整合业务组件，不改变 Spring Security 的 AuthenticationManager 接入。

### 常用组件扩展入口

只替换需要定制的组件，其他继续使用当前登录实现的默认行为。各扩展点只注册一个业务 Bean，不需要额外配置“实现类名称”。

| 需要定制的能力 | 扩展点及包 | 怎么接入 |
| --- | --- | --- |
| 安全用户模型 | `auth.IUserDetails` | 业务安全 DTO 实现接口，排除密码等敏感字段 |
| JWT / Sa-Token 账号密码认证 | `auth.UserAuthenticator` | 提供 Bean，验证账号密码和状态后返回安全 IUserDetails；不自行签发 Token |
| 加载用户、角色、权限 | `auth.UserStore` | 提供 Bean，实现 `load(userId)`；JWT、Sa-Token 都使用它 |
| JWT 生成及验签解析 | `jwt.JwtTokenCodec` | 提供 Bean，同时实现 `generate`、`parse`，默认 Codec 自动退出 |
| 有状态登录、撤销、在线会话 | `auth.ISecurityManager` 或 Sa-Token 原生扩展 | 使用 security-sa-token，或提供自己的管理器；JWT 模块无存储接口 |
| Token 从哪里读取、怎样恢复身份 | `web.LoginInterceptor` | 提供该类型 Bean；框架自动注册，不用再写 MVC 注册器 |
| 整体接入其他安全框架 | `auth.ISecurityManager` + `web.LoginInterceptor` | 管理器委托登录/注销/用户能力；自定义拦截器接入外部身份生命周期，框架自动注册 |
| Sa-Token 原生能力 | `satoken.SaTokenSecurityManager` | 使用 `stpLogic()`；存储和策略沿用 Sa-Token 自身扩展 |
| Spring principal 与业务用户 ID、角色权限映射 | `spring.SpringUserMapper` | security-spring 自动桥接已有认证；提供 mapper Bean 替换默认安全映射 |
| Spring 账号密码登录 | Spring `AuthenticationManager` Bean | loginByUsername 委托现有认证；SecurityContextRepository、SessionAuthenticationStrategy Bean 可替换并与过滤器链共用 |
| IP 黑名单 | `limit.IpBlacklist` | 实现并注册 Bean，见 [IP 黑名单](rate-limit.md#ip-黑名单) |

表中包名前缀均为 `com.yulinlin.security`。加密设备密钥、限流参数优先使用本专题配置和注解，不必为普通配置需求替换整个安全管理器。

### 加载用户信息

```java
import com.yulinlin.security.auth.UserStore;
import org.springframework.context.annotation.Bean;

@Bean
public UserStore userStore(AccountService accounts) {
    return userId -> accounts.loadSecurityUser(userId);
}
```

`AccountService.loadSecurityUser` 为业务自定义方法，返回实现 `IUserDetails` 的安全 DTO。只按 userId 加载，不在这里再次验证密码或验证码。用户不存在、已停用或不允许登录时返回 `null` 或抛 `LoginException`；数据库等故障直接抛出，不能返回一个默认用户。返回对象的 userId 必须与查询 ID 一致。

JWT 中不保存完整用户资料。每次正常请求恢复身份时，验签并检查有效期后重新调用 `UserStore`，不查询登录记录；同一请求后续读取线程快照，不重复加载。业务可在 `UserStore` 内加缓存，但必须自行处理角色、状态变更的失效，`refresh()` 不会替你清理业务缓存。“无状态”指不保存 Token 登录状态，不表示完全不查用户数据库。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
