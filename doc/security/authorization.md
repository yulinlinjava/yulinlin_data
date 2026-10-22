# 登录、角色与权限注解

[文档首页](../README.md) / [接口安全](README.md)

ApiLogin、ApiRole、ApiPerm、ApiPublic 和默认匿名行为。

## 本页导航

- [鉴权声明注解](#鉴权声明注解)
- [默认匿名安全管理器](#默认匿名安全管理器)

## 鉴权声明注解

`com.yulinlin.security.annotation` 提供以下四个公共注解，与 ApiCrypto、ApiLimit 命名统一。权限判断以 ISecurityManager 提供的当前用户、角色和权限为准；注解不负责登录、查询账号表或刷新用户资料。

| 注解 | 声明含义 | 参数 |
| --- | --- | --- |
| `@ApiLogin` | 调用方必须登录 | 无 |
| `@ApiRole("admin")` | 调用方必须登录并拥有指定角色 | 必填 `String[] value`；`any` 默认 false |
| `@ApiPerm("user:query")` | 调用方必须登录并拥有指定权限 | 必填 `String[] value`；`any` 默认 false |
| `@ApiPublic` | 允许匿名访问 | 无 |

四个注解均可放在 Controller 或 Service 的类、接口和公共方法上。解析支持接口声明、实现类、父类、重写方法、泛型桥接和继承的 default 方法。角色和权限默认按大小写精确匹配，不解释通配符或 SpEL，也没有 admin 自动越权；`any=false` 表示全部满足，`any=true` 表示满足任意一个。

通过容器管理的 Bean 调用时生效，例如：

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
- 未登录返回 HTTP 401；登录后缺少角色或权限返回 HTTP 403。搭配 starter 时错误响应包装为 R；自定义异常处理器应保留对应状态，不把拒绝访问改成成功。
- 获取当前用户时发生的数据库、Redis 或业务异常原样传播，不伪装成未登录。ISecurityManager 的身份加载路径不要再次调用受鉴权保护的方法，否则明确报告递归配置错误；应直接使用 Repository、缓存等基础设施。
- ApiPublic 只影响鉴权，不关闭全局接口加解密、ApiLimit 或 IP 黑名单。公开登录接口也遵守加密配置，需要例外时使用 ApiCrypto(false) 或排除路径。

### 默认匿名安全管理器

| 场景或方法 | 默认行为 |
| --- | --- |
| 只引入公共 security，未提供 ISecurityManager Bean | 默认匿名，正常启动 |
| `getUserDetails()`、`getUserDetails(userId)`、`refresh(userId)` | 返回 null，不查库、不生成身份 |
| `logout()` | 清理本模块当前线程上下文，不操作外部登录会话 |
| `login(LoginRequest)` | 默认抛 UnsupportedOperationException；匿名管理器不提供账号密码、密钥或 ID 登录 |
| 无鉴权注解或允许匿名的方法 | 不读取管理器，不影响加密和限流 |
| 默认匿名实现访问受保护方法 | 401，不会因为没有业务实现而放行 |
| 自定义 ISecurityManager Bean | 自动替换默认实现；项目只保留一个安全管理器 |

业务实现只需注册为普通 Bean，不需要额外的启用注解或配置。

使用边界：注解适用于经容器管理的 Bean 调用的 public、非 final 方法，不能用于 final 类。private/static 方法、手动 new 对象和同类 this 调用不在保护范围内，不能将它们当作安全入口；异步线程也不会自动继承当前请求身份。项目不能关闭方法拦截能力。

完整 Controller、Service 和权限声明案例见 [Admin RBAC](../practice/rbac.md)。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
