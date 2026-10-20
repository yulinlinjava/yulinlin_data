# 登录、角色与权限注解

[文档首页](../README.md) / [接口安全](README.md)

ApiLogin、ApiRole、ApiPerm、ApiPublic 和默认匿名行为。

## 本页导航

- [鉴权声明注解](#鉴权声明注解)
- [默认匿名安全管理器](#默认匿名安全管理器)

## 鉴权声明注解

`com.yulinlin.security.annotation` 提供以下四个公共注解，与 ApiCrypto、ApiLimit 命名统一。由自动注册的 ApiAuthAspect 统一执行，直接调用业务 ISecurityManager.getUserDetails() 获取当前用户，使用 IUserDetails.hasRole/hasPermission 校验；不自行解析 Token、查询账号表或刷新登录会话。

| 注解 | 声明含义 | 参数 |
| --- | --- | --- |
| `@ApiLogin` | 调用方必须登录 | 无 |
| `@ApiRole("admin")` | 调用方必须登录并拥有指定角色 | 必填 `String[] value`；`any` 默认 false |
| `@ApiPerm("user:query")` | 调用方必须登录并拥有指定权限 | 必填 `String[] value`；`any` 默认 false |
| `@ApiPublic` | 允许匿名访问 | 无 |

四个注解均可放在 Controller 或 Service 的类、接口和公共方法上。解析支持接口声明、实现类、父类、重写方法、泛型桥接和继承的 default 方法。角色和权限默认按大小写精确匹配，不解释通配符或 SpEL，也没有 admin 自动越权；`any=false` 表示全部满足，`any=true` 表示满足任意一个。

通过 Spring Bean 代理调用时生效，例如：

```java
import com.yulinlin.security.annotation.ApiLogin;
import com.yulinlin.security.annotation.ApiPerm;
import com.yulinlin.security.annotation.ApiPublic;
import com.yulinlin.security.annotation.ApiRole;

@ApiLogin
public class UserApi {
    @ApiPublic
    public void login() { }

    @ApiPerm("user:query")
    public void page() { }

    @ApiRole(value = {"admin", "operator"}, any = true)
    @ApiPerm({"user:query", "user:export"})
    public void export() { }
}
```

鉴权规则：

- ApiRole、ApiPerm 本身要求登录，不需要重复添加 ApiLogin。
- 类、父类、接口和方法上的要求叠加，都必须满足；方法上的同类注解不会覆盖类级要求。
- 方法 ApiPublic 可豁免类级 ApiLogin；不能绕过类级角色、权限要求，也不能与方法自身的鉴权要求共存。类上的 ApiPublic 不能与类级鉴权要求共存；但公开类中的方法可用 ApiLogin/ApiRole/ApiPerm 单独收紧访问。
- 空数组、空白编码、编码首尾空白或冲突声明会抛出明确的配置异常，不静默放行。
- 未登录抛出 HTTP 401；登录后缺少角色或权限抛出 HTTP 403。实现使用 ResponseStatusException；搭配 starter 时由现有异常处理器包装为 R，security 不依赖 starter。
- 获取当前用户时发生的数据库、Redis 或业务异常原样传播，不伪装成未登录。ISecurityManager 的身份加载路径不要再次调用受鉴权保护的方法，否则明确报告递归配置错误；应直接使用 Repository、缓存等基础设施。
- ApiPublic 只影响鉴权，不关闭 ApiCrypto、ApiLimit 或 IP 黑名单。

### 默认匿名安全管理器

| 场景或方法 | 默认行为 |
| --- | --- |
| 无业务 ISecurityManager Bean | 自动提供 AnonymousSecurityManager，正常启动 |
| `getUserDetails()`、`getUserDetails(userId)`、`refresh(userId)` | 返回 null，不查库、不生成身份 |
| `logout()` | 清理本模块当前线程上下文，不操作外部登录会话 |
| `login(...)` 两个重载 | 抛出 IllegalStateException，提示未配置业务安全管理器 |
| 无鉴权注解或允许匿名的方法 | 不读取管理器，不影响加密和限流 |
| 默认匿名实现访问受保护方法 | 401，不会因为没有业务实现而放行 |
| 自定义 ISecurityManager Bean | 自动替换默认实现；项目只保留一个安全管理器 |

业务实现只需注册为普通 Bean，不需要额外的启用注解或配置。切面只在受保护的调用中解析管理器，每次调用只获取一次当前用户；注解策略按目标类和方法缓存，不缓存用户与校验结果。通过 ClassValue 保存解析结果，避免使用长期全局 Class/Method Map。

ApiAuthAspect 使用 Spring 原生 Advisor/MethodInterceptor 做方法匹配，而不是叠加多个重复执行的切面；order 为 9990，早于框架同步代理的 9998 和事务的 9999。没有鉴权声明的 Bean 不为此创建代理。当前只支持经 Spring 代理调用的 public、非 final 方法；受保护的 final 类或 final 方法明确拒绝配置。Object 基础方法不作为业务鉴权入口。private/static 方法、手动 new 对象和同类 this 调用不受 AOP 保护，不能将这些调用当成安全边界；异步线程也不会自动继承当前请求身份。Spring Boot 默认启用 AOP，不要设置 spring.aop.auto=false；非 Boot 容器需自行配置 AOP 基础设施。

Admin RBAC 案例已改为公共 ApiLogin、ApiPerm、ApiPublic，移除了本地 RequirePermission、Anonymous 和 RbacInterceptor，避免同一入口再执行两套权限检查。回归用例统一放在 admin/src/test/java；本轮未执行编译、测试或打包。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
