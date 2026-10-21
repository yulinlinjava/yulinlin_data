# Spring Security 接入

[文档首页](../README.md) / [接口安全](README.md)

已有 Spring Security 项目保留原来的过滤器链与账号认证配置，统一使用框架用户上下文与权限注解；也可通过 loginByUsername 编写 JSON 登录接口。

## 本页导航

- [最小接入](#最小接入)
- [当前用户与权限](#当前用户与权限)
- [自定义用户映射](#自定义用户映射)
- [登录与注销](#登录与注销)
- [账号密码登录配置](#账号密码登录配置)
- [设备类型与自定义会话策略](#设备类型与自定义会话策略)
- [查询用户与刷新](#查询用户与刷新)
- [生命周期与能力边界](#生命周期与能力边界)

## 最小接入

在**已配置 Spring Security 的项目**引入：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>security-spring</artifactId>
    <version>3.0</version>
</dependency>
```

只选一种适配：不要同时引入 `security-jwt`、`security-sa-token`。Spring Security 自己的 JWT Resource Server 属于原有认证体系，不是本项目的 security-jwt，可以继续使用。

该模块依赖公共 security 和 Spring Security core/web API，版本由应用的 Spring Boot BOM 管理。**不创建 SecurityFilterChain、AuthenticationManager、用户表或 Controller**。原有 Spring Security starter/配置继续保留；单独引入本适配器不等于配置好了 Spring 认证。新增账号密码入口默认使用请求属性 + HttpSession 保存上下文，并执行更换 Session ID、轮换 CSRF Token 的会话策略；相应 Spring Bean 可替换，详见下文。

默认无需 UserStore，也无需新增 YAML 开关。内置 SpringLoginInterceptor 不重复解析 Authorization/Cookie，只管理框架快照的请求生命周期。用户自定义 LoginInterceptor Bean 仍然优先。

## 当前用户与权限

原来的登录成功后，直接使用：

```java
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.SecurityUtil;

IUserDetails user = SecurityContext.getUser(); // 未认证或匿名：null
String userId = SecurityUtil.getUserId();
boolean loggedIn = SecurityContext.isLogin();
boolean admin = SecurityUtil.hasRole("ADMIN");
boolean canEdit = SecurityUtil.hasPermission("video:edit");
IUserDetails required = SecurityUtil.requireUser(); // 未登录抛 LoginException
```

默认映射规则：

| Spring 当前身份 | 框架用户 |
| --- | --- |
| null、未认证或 AnonymousAuthenticationToken | 未登录；不会因匿名对象 isAuthenticated=true 而放行 |
| principal 实现本项目 IUserDetails | 复制 userId/username；角色权限仍以 Authentication.authorities 为准 |
| 其他 principal | userId/username 默认使用 Authentication.getName() |
| ROLE_ADMIN | roles 包含 ADMIN；大小写不变 |
| video:edit、SCOPE_video.read 等 authority | permissions 保留完整字符串，不自动去除 SCOPE_ |
| ROLE_ADMIN 等角色 authority | permissions 同样保留原始 authority 字符串 |
| password、credentials、Authentication.details | 不复制到用户 DTO |

`Authentication.getName()` 通常是用户名或认证主体名称，**不保证是数据库主键**。需要真实用户 ID 时按下节配置映射。默认返回独立、只读的用户快照，不直接返回带密码的 Spring UserDetails，也不自动复制自定义属性。

已有注解可直接使用：

```java
@ApiRole("ADMIN")
@ApiPerm("video:edit")
public void editVideo() {
    String operatorId = SecurityContext.getUserId();
    // 业务操作
}
```

这些注解只做框架的精确角色/权限编码校验，不自动解释 Spring 的 RoleHierarchy、SpEL、PermissionEvaluator 或“必须完整认证”等高级规则。需要这些能力继续使用 Spring 原有规则；也可以在 SpringUserMapper 中明确展开权限。`@ApiPublic` 不会绕过 Spring Security 的过滤器授权，原有 CSRF、URL 权限照常生效。

## 自定义用户映射

提供一个 `SpringUserMapper` Bean，默认映射自动退让。示例的 `MyLoginUser` 是**业务现有的 Spring principal**，不是框架类：

```java
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.spring.DefaultSpringUserMapper;
import com.yulinlin.security.spring.SpringUserMapper;
import org.springframework.context.annotation.Bean;

@Bean
public SpringUserMapper springUserMapper() {
    DefaultSpringUserMapper defaults = new DefaultSpringUserMapper();
    return authentication -> {
        MyLoginUser principal = (MyLoginUser) authentication.getPrincipal();
        IUserDetails permissions = defaults.map(authentication);
        return new DefaultSpringUserMapper.UserSnapshot(
                principal.getUserId(),
                principal.getUsername(),
                permissions.getRoles(),
                permissions.getPermissions()
        );
    };
}
```

也可返回业务自己的安全 IUserDetails DTO。不要返回含 getPassword() 的 principal、Authentication 或 credentials；不要把用户 ID 从客户端参数里直接拿来当已认证身份。

每次读取当前用户都会从 Spring 当前 Authentication 重新映射，避免切换账号或原地修改权限后复用旧快照。映射结果不再写入框架 ThreadLocal，因此原生登录过滤器在 MVC 前结束请求也不会遗留框架用户快照。默认只处理内存数据、不查库；自定义 mapper 要查库时自行控制缓存。mapper 返回 null 时框架视为无可用用户，但不会擅自撤销 Spring 会话；异常正常抛出且清理旧快照。mapper 内不能再次读取 SecurityContext / SecurityUtil 当前用户，避免递归。

## 登录与注销

原生表单、已有 JSON 登录或 OAuth2 可以继续使用，原有登录代码不必修改。需要统一调用时，在 MVC Controller 内使用：

```java
// 默认设备类型为 "default"
LoginResult result = SecurityUtil.loginByUsername(username, password);

// 指定本次登录的设备类型；不会自动读取加密模块的 Device-Type 请求头
LoginResult web = SecurityUtil.loginByUsername(username, password, "web");

// 注入 ISecurityManager 调用相同方法，与静态入口使用同一套实现
LoginResult mobile = securityManager.loginByUsername(username, password, "android");
```

以上为三种替代写法，不需要连续登录三次。模块将未认证的账号密码请求交给 **AuthenticationManager Bean**，通过后映射安全用户，执行 SessionAuthenticationStrategy，再用 SecurityContextRepository 保存 Spring 上下文；不是只设置当前线程。默认 Session 模式下 `result.token()` / `expiresAt()` 都为 null，浏览器携带会话 Cookie 恢复身份，不把 Session ID 当 Token 返回。会话过期时间沿用容器/Spring Session 配置。

框架已统一移除 `SecurityUtil.login(userId)` / `login(userId, deviceType)`，只公开 loginByUsername；不是保留旧方法后运行时报错。Spring 使用 AuthenticationManager，JWT/Sa-Token 使用业务 UserAuthenticator，不能互相混用认证组件。

账号密码错误、账号状态不允许登录或会话策略拒绝时抛 LoginException；不透露“账号不存在还是密码错误”。保存身份之前调用认证结果的 CredentialsContainer 清理凭证；自定义 Authentication 清理后 credentials 仍非 null 时拒绝保存，不能把密码带进 Session。认证开始后的映射/存储失败会清理当前身份，并尽力清除已保存的上下文，清理失败保留在异常中，不伪装成功。未配置 AuthenticationManager、没有当前 Servlet 请求/响应、响应已提交会明确报错。不得记录密码或把底层异常堆栈返回前端；业务异常处理器将 LoginException 转为统一失败响应。

原生注销入口继续保留。Spring 清理当前 Authentication 后，下一次 SecurityContext/SecurityUtil 读取立即按匿名处理，不保留旧用户。

MVC 业务中也可使用：

```java
SecurityUtil.logout();
```

这里委托**当前 Spring 包装请求的 HttpServletRequest.logout()**，复用当前过滤器链的 LogoutHandler，不另建注销链。需要启用 Spring 的 Servlet API 集成；没有有效 HTTP 请求、未经过对应包装或调用后当前认证仍存在时会明确报错，不能只清理线程就假装已经注销。仍由业务编写响应；它不代替原 `/logout` 入口的 LogoutSuccessHandler 跳转或 OAuth2/OIDC/SAML 的远端退出流程。自定义复杂退出继续使用原生入口。

Session、Remember-Me 的具体清理由 Spring 的注销配置决定；无状态 JWT/外部访问令牌是否撤销也取决于原有体系，不能认为清理当前请求就撤销所有凭证。参见 [Spring Servlet API 集成](https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/servlet-api.html)及[原生注销流程](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/logout.html)。

## 账号密码登录配置

在已有 Spring Security 配置中，**暴露现有 AuthenticationManager Bean**。如果已经有，不要再注册一个：

```java
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
    return configuration.getAuthenticationManager();
}
```

类型分别来自 `org.springframework.security.authentication.AuthenticationManager`、`org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration`。继续使用业务已有的 UserDetailsService、PasswordEncoder 或 AuthenticationProvider；框架不自己查用户表，不做明文 equals。仅在某个 HttpSecurity 内部创建的 AuthenticationManager 不会自动成为 Bean；多管理器场景明确提供统一入口使用的唯一/@Primary Bean。

Session 登录建议与目标过滤器链显式共用相同组件，在现有链内合并以下配置，**不要额外复制一条覆盖所有请求的新链**：

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http,
        AuthenticationManager authenticationManager,
        SecurityContextRepository repository,
        SessionAuthenticationStrategy sessionStrategy) throws Exception {
    http.authenticationManager(authenticationManager)
        .securityContext(context -> context.securityContextRepository(repository))
        .sessionManagement(session -> session.sessionAuthenticationStrategy(sessionStrategy))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
            .anyRequest().authenticated());
    // 保留原项目的 CSRF、Session、注销和其他认证配置。
    return http.build();
}
```

`SecurityContextRepository` 来自 `org.springframework.security.web.context`，`SessionAuthenticationStrategy` 来自 `org.springframework.security.web.authentication.session`。默认由适配模块提供这两个 Bean；业务提供同类型 Bean 时默认退让。它们不会凭空读取另一条过滤器链的私有配置，多链应用须明确匹配。

Controller 使用项目统一响应包装：

```java
import com.yulinlin.starter.domain.R;
import com.yulinlin.security.auth.LoginResult;
import com.yulinlin.security.auth.SecurityUtil;

@PostMapping("/auth/login")
public R<LoginResult> login(@RequestBody LoginPo po) {
    // 有验证码等业务规则，在此先校验。失败不能继续调用登录。
    return R.newInstance(SecurityUtil.loginByUsername(po.username(), po.password(), po.deviceType()));
}

public record LoginPo(String username, String password, String deviceType) { }
```

三参数写法要求 deviceType 非空；不需要区分设备就用两参数重载。使用项目自己的 R 时调整导入。跨域浏览器客户端还需按原项目配置携带 Cookie，后端 CORS 精确允许受信任来源。

`permitAll()` **不会关闭 CSRF**。Session/Cookie 登录须沿用原来的 CSRF Token 获取与请求头机制，登录接口本身也应受保护；不要为此全局关闭 CSRF。若使用 ApiCrypto，MVC 的 @RequestBody 可以走框架解密，但原生 Spring 登录过滤器不会自动解密它。

Spring 的自定义认证需要显式保存上下文，详见[官方账号密码认证](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passwords/index.html#publish-authentication-manager-bean)和[会话持久化](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html)。

## 设备类型与自定义会话策略

- `loginByUsername(username, password)` 默认设备 `default`；三参数版本透传指定设备，不把设备信息当作密码或验证码。
- AuthenticationManager 收到的 `Authentication.details` 为 `com.yulinlin.security.spring.SpringLoginDetails`，通过 `getDeviceType()` 读取；同时保留 WebAuthenticationDetails 的来源地址/会话信息。
- 常规认证结果保留这份 details；自定义提供者返回自己的 details 时不覆盖。因此需要自定义认证结果承载设备时，由提供者自行保留。它不自动实现“同设备互踢”“多设备隔离”。
- 默认 SecurityContextRepository 为 DelegatingSecurityContextRepository（请求属性 + HttpSession）；自定义仓库必须同时接入恢复身份的过滤器链。NullSecurityContextRepository 等不持久化实现不会带来跨请求登录，接口不会额外签发 Token。
- 默认 SessionAuthenticationStrategy 为更换 Session ID + CSRF Token 轮换。CSRF 存储使用业务 CsrfTokenRepository Bean，未提供时使用 HttpSessionCsrfTokenRepository；使用 Cookie 或自定义 CSRF 存储时，将原来的仓库暴露为 Bean 并在过滤器链中配置同一个实例。
- 业务提供 SessionAuthenticationStrategy Bean 可整体替换默认策略。需要并发会话限制时，使用 Spring 的组合策略，并保留防 Session 固定攻击、会话注册/并发检查和 CSRF 轮换等必要步骤。仅在另一条链里配置 maximumSessions，不代表此 MVC 登录入口自动获得相同限制。
- 统一入口不自动执行原生登录过滤器的 Remember-Me、成功跳转、自定义成功/失败 Handler 或额外 MFA 步骤。依赖这些流程的项目继续使用原生登录入口，或由业务完整接入对应策略；不能用账号密码入口绕过必须的二次认证。
- STATELESS、OAuth2 Resource Server 等原本无状态的链，继续使用原来的凭证签发/校验；不要为了调用此 Session 登录方法强行混入 HttpSession。

## 查询用户与刷新

| API | Spring 适配行为 |
| --- | --- |
| getUserDetails()/requireUser()/hasRole()/hasPermission() | 映射 Spring 当前认证身份，无需 UserStore |
| getToken() | 返回 null；统一适配不假定认证方式，不把密码、Session ID 或其他 credentials 当 Token |
| getUserDetails(userId) | 提供 UserStore 时按 ID 加载；未提供时只支持返回当前同 ID 用户，查询其他人明确报错 |
| refresh() | 清理框架快照并重新映射当前 Authentication，不重新认证、不续期 |
| refresh(userId) | 当前用户同上；其他用户按 UserStore 加载，不切换当前身份 |

改变密码、账号状态或角色后，仍由 Spring 认证体系更新 principal/authorities、重认证或撤销会话。框架 refresh 不会自动调用 UserDetailsService 或改写 Authentication。这里的 UserStore 仅是“按指定 ID 查询用户”的可选目录，不替换 Spring 的 UserDetailsService。

Spring 的 Session/Basic 等方式可能没有接口 Token，因此 LoginSession 允许 token=null；expiresAt=null 表示适配器不声明到期时间，**不是承诺永久登录**，以 Spring 当前认证状态为准。JWT、Sa-Token 的 Token 校验并未取消。

## 生命周期与能力边界

- 身份来源唯一：Spring SecurityContextHolder → SpringUserMapper → 框架 SecurityContext。两个同名 SecurityContext 类型不是同一个类；业务使用本框架类型时导入 `com.yulinlin.security.auth.SecurityContext`。
- 原来的 SecurityFilterChain 负责认证、恢复与清理 Spring 上下文；框架 MVC 包装器只清理自己的 ThreadLocal。SecurityContext.clear() 不会注销 Spring，再次读取仍可恢复有效身份。
- 静态桥接在 Spring 单例初始化完成后生效，跟随选中的 ISecurityManager；自定义 ISecurityManager Bean 会使默认适配退让。
- 默认不复制身份到其他线程。若使用 Spring 的显式安全上下文传递能力，适配器读取传入线程中的 Spring 身份；调用方仍需正确清理业务线程上下文。
- 当前适配基于 Servlet/Spring MVC 和线程上下文，不支持 WebFlux 的 Reactor 安全上下文。
- 加密 Advice、限流和框架权限注解可以继续使用。但 Spring 过滤器先于 MVC Advice，原生登录过滤器不会自动解密 ApiCrypto 密文；登录报文应沿用原认证流程的协议。

本次仅完成源码与文档接入，回归案例位于 admin/src/test；未执行编译、测试或外部认证系统联调。
