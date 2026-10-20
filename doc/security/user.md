# 用户模型与安全管理器

[文档首页](../README.md) / [接口安全](README.md)

IUserDetails、UserStore、安全协议与扩展入口。

## 本页导航

- [登录用户模型](#登录用户模型)
- [安全管理器](#安全管理器)
- [常用组件扩展入口](#常用组件扩展入口)
- [加载用户信息](#加载用户信息)

## 登录用户模型

完整业务接入参考 [Admin 两表 RBAC 与无状态 JWT 登录案例](../practice/rbac.md#admin-两表-rbac-与登录案例)：业务认证后用 userId 登录，`AdminUserStore` 加载最新用户与权限。admin 只引入 security-jwt，不保存登录记录，不实现安全管理器或撤销逻辑；本轮源码迁移未编译、未执行测试。

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

业务先校验账号、密码、验证码和状态，再用可信 `userId` 登录。框架不处理账号密码，也不查询指定用户表。使用 JWT 或 Sa-Token 实现时，不需要自己实现整个 `ISecurityManager`。

```java
import com.yulinlin.security.auth.LoginResult;
import com.yulinlin.security.auth.SecurityUtil;

// 业务代码：校验密码、验证码、状态，返回真实用户 ID。
String userId = accountService.authenticate(username, password, code);
LoginResult result = SecurityUtil.login(userId, "web");
// Controller 将 result 包装成项目的 R<LoginResult> 返回。
```

上面的 `accountService` 是业务服务，不是框架组件。不能把前端传入的 userId 直接交给 login，否则会绕过身份认证。失败时业务可抛出 `LoginException`，账号不存在和密码错误应使用相同对外提示；基础设施错误不要伪装成登录成功。

```java
SecurityUtil.login(userId);        // deviceType 默认 "default"
SecurityUtil.login(userId, "web"); // 设备类型由本次登录明确传入
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

`login(String, String)` 现在表示 **userId + deviceType**，不再表示用户名和密码；旧三参数登录也已移除。登录成功返回 `LoginResult(token, expiresAt, user)`。`user` 为不含密码的 `IUserDetails`，不要返回含凭证的 ORM 用户实体。登录设备与加密用的 `Device-Type` 请求头不是自动绑定关系。

`SecurityUtil` 只是静态委托入口，应用单例初始化完成后可用；不要在 Bean 构造器等过早阶段调用。普通业务也可以直接注入唯一的 `ISecurityManager`。不需要设计多管理器选择或运行时切换。

### 常用组件扩展入口

只替换需要定制的组件，其他继续使用当前登录实现的默认行为。各扩展点只注册一个业务 Bean，不需要额外配置“实现类名称”。

| 需要定制的能力 | 扩展点及包 | 怎么接入 |
| --- | --- | --- |
| 安全用户模型 | `auth.IUserDetails` | 业务安全 DTO 实现接口，排除密码等敏感字段 |
| 加载用户、角色、权限 | `auth.UserStore` | 提供 Bean，实现 `load(userId)`；JWT、Sa-Token 都使用它 |
| JWT 生成及验签解析 | `jwt.JwtTokenCodec` | 提供 Bean，同时实现 `generate`、`parse`，默认 Codec 自动退出 |
| 有状态登录、撤销、在线会话 | `auth.ISecurityManager` 或 Sa-Token 原生扩展 | 使用 security-sa-token，或提供自己的管理器；JWT 模块无存储接口 |
| Token 从哪里读取、怎样恢复身份 | `web.LoginInterceptor` | 提供该类型 Bean；框架自动注册，不用再写 MVC 注册器 |
| 整体接入其他安全框架 | `auth.ISecurityManager` + `web.LoginInterceptor` | 管理器委托登录/注销/用户能力；自定义拦截器接入外部身份生命周期，框架自动注册 |
| Sa-Token 原生能力 | `satoken.SaTokenSecurityManager` | 使用 `stpLogic()`；存储和策略沿用 Sa-Token 自身扩展 |
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
