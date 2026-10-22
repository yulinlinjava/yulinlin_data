# Admin RBAC 与 JWT 登录案例

[文档首页](../README.md) / [项目实践](README.md)

两表角色权限模型、统一 LoginAuthenticator 认证与无状态 JWT 语义。

## 本页导航

- [Admin 两表 RBAC 与登录案例](#admin-两表-rbac-与登录案例)
- [启动方式与完整配置](#启动方式与完整配置)
- [两表模型与状态枚举](#两表模型与状态枚举)
- [登录、权限和会话生命周期](#登录权限和会话生命周期)
- [业务写法与 HTTP 调用](#业务写法与-http-调用)
- [Service 完整 CRUD 与对外边界](#service-完整-crud-与对外边界)

## Admin 两表 RBAC 与登录案例

源码位于 [admin/rbac](../../admin/src/main/java/com/yulinlin/admin/rbac)，请求示例位于 [admin/rbac.http](../../admin/rbac.http)。案例只有 `rbac_user`、`rbac_role` 两张业务表，使用 JDK 25、MySQL 和纯无状态 `security-jwt`，不保存登录记录，未引入 Spring Security，也不同时引入 Sa-Token。

> 2026-10-21：admin 源码和相关回归用例已迁移到统一 LoginAuthenticator。AdminAuthenticationService 同时实现 LoginAuthenticator<PasswordLogin> 和 UserStore，认证名为 password；登录响应和 JWT 携带 authType。完整扩展接入见[用户模型与认证器](../security/user.md)。本轮仅修改源码和文档，未编译、未执行测试或数据库联调。

### 启动方式与完整配置

在 IDEA 中运行 `AdminApplication`。RBAC 组件和 Repository 默认注册，不需要额外激活 `rbac` profile。案例设置位于 [application.yml](../../admin/src/main/resources/application.yml)，数据库连接继续由自己的 `dev` 或 `prod` 环境配置提供。MySQL 数据库本身应提前创建，框架启动扫描负责创建两张表及索引；如果环境配置覆盖了 `schema-packages` 或 Repository 的 `scan-packages`，应确保其扫描范围包含 RBAC 对应包。

启动环境设置 `ADMIN_RBAC_PASSWORD`，长度为 12–128 字符；可选设置 `ADMIN_RBAC_USERNAME`，默认 `admin`。不设置密码时跳过初始账号创建，接口不会变成免登录。初始账号已存在时，重启不会覆盖其密码、状态或角色。

另需设置 `ADMIN_JWT_SECRET`：至少 32 字节安全随机密钥的 Base64 字符串，不是登录密码或 AES 密钥，也不要使用 `${random.value}`。在可信环境生成后保存到环境变量或密钥管理器，不提交到仓库：

```java
byte[] key = new byte[32];
new java.security.SecureRandom().nextBytes(key);
String secret = java.util.Base64.getEncoder().encodeToString(key);
// 将 secret 安全配置为 ADMIN_JWT_SECRET；不要写入生产日志。
```

```yaml
admin:
  rbac:
    bootstrap-username: ${ADMIN_RBAC_USERNAME:admin}
    bootstrap-password: ${ADMIN_RBAC_PASSWORD:}

yulinlin:
  datasource:
    default-group: mysql
  data:
    auto-update: false
  repository:
    scan-packages:
      - com.yulinlin.admin.rbac.repository
  mysql:
    schema-mode: CREATE
    schema-packages:
      - com.yulinlin.admin.rbac.domain
    log-params: false
  security:
    jwt:
      secret: ${ADMIN_JWT_SECRET}
      issuer: yulinlin-admin
      ttl: 2h
    # 不提供 CryptoCodec，也不配置 crypto.devices，框架不接管接口加解密。
    limit:
      enabled: true
      ip-header: X-Real-IP
      trusted-proxies:
        - 127.0.0.1
        - "::1"
```

RBAC 不使用 `@Profile` 条件，登录接口、Service、Repository 和公共鉴权 AOP 随 admin 正常启动。案例默认的 Schema 扫描只包含 RBAC 实体，不会改动旧 `SysUserEntity` 的表；环境配置如果扩大扫描范围，以实际配置为准。案例使用全局默认 group；如果业务同时使用多个 group，应按[多数据源](../datasources/routing.md)和 [Schema](../datasources/schema.md) 专题添加明确的 `@JoinSession` 路由和对应扫描。

Admin 默认不提供 CryptoCodec，也不配置设备密钥，框架不接管报文加解密；案例 Controller 的 `@ApiCrypto(false)` 此时不会触发正文处理，可以直接发送普通 JSON。要接入框架加密，按[接口加密](../security/crypto.md)配置设备密钥（含 `unknown`）和 CBC IV，或提供[自定义 CryptoCodec](../security/crypto-codec.md)，启用 `crypto.enabled`，并移除对应 Controller 的 `@ApiCrypto(false)`（或改为 true），即可跟随全局严格加密。无注解接口也参与；保留 false 的接口兼容请求并返回明文。全局 `enabled=false` 仍兼容解密，不是完全关闭。文件、监控等接口配置排除路径；报文加密不替代 HTTPS。

登录接口每 IP 每分钟最多 10 次，使用现有 `@ApiLimit`。Nginx 的 `X-Real-IP` 只对 `trusted-proxies` 中的直接代理可信；代理不在本机时修改为实际代理地址。

### 两表模型与状态枚举

| 实体 | 表 | 存储字段 |
| --- | --- | --- |
| `UserEntity` | `rbac_user` | ID、用户名、密码哈希、昵称、状态、`List<String> roleCodes`、审计时间 |
| `RoleEntity` | `rbac_role` | ID、角色编码、名称、`List<String> permissions`、审计时间 |

两个集合都由 JDBC 集合编码器自动保存为 JSON 文本，业务直接调用 getter/setter，不手动 JSON 转换。用户名和角色编码有唯一索引。权限字典在 `Permission` 枚举中维护，没有权限表和两张关系表。内置 `admin` 角色持有字典中的全部权限，管理接口禁止修改或删除它；其他角色的编码创建后不可变。

`UserStatus implements SuperEnum<String>` 为状态的唯一业务定义：

| Java 枚举 | 数据库与接口编码 | 中文说明 | 可登录 |
| --- | --- | --- | --- |
| `ENABLED` | `enabled` | 正常 | 是 |
| `DISABLED` | `disabled` | 停用 | 否 |
| `LOCKED` | `locked` | 锁定 | 否 |

新建用户显式赋值 `ENABLED`。`@JsonValue`/`@JsonCreator` 与 ORM `IEnum` 编码器分别保证 HTTP 和数据库都使用稳定编码；`locked` 是管理员控制的状态，不代表已经实现自动失败锁定。用户响应同时给出 `status` 和 `statusLabel`，`/rbac/users/statuses` 可用于前端下拉选项。

用户实体直接表达集合到角色编码的关联：

```java
@JoinField(textType = TextTypeEnum.text, description = "角色编码集合")
private List<String> roleCodes;

@JoinField(exist = false)
@JoinQuery(primary = "code", value = "roleCodes")
private List<RoleEntity> roles;
```

查询用户时，框架汇总 `roleCodes`，通过 `code IN (...)` 批量查角色并分配。`roles` 只负责关联结果，不重复保存到用户表。`AdminUserDetails.from(user)` 将角色编码、权限编码分别合并成不可变 `Set`，只采纳真实存在的角色。角色表中的权限集合示例为 `["user:query", "user:update"]`。

`UserRoleProjection` 是同一用户表的部分投影，仅选择主键、审计时间和角色集合，保持 `autoSchema=false`。删除普通角色时先按 JSON 文本 LIKE 缩小范围，再通过集合 `contains` 确认，使用 setter 移除对应编码，和删除角色在同一事务提交。这样不会因以后重新创建同名角色而自动恢复旧授权；JSON 集合查询没有关系表索引，这种模型适合角色数量较少的后台。

### 登录、权限和会话生命周期

职责划分如下；只定制业务必须处理的部分，不重复实现 JWT：

| 组件 | 职责 |
| --- | --- |
| `AdminAuthenticationService.authenticate` | 实现 LoginAuthenticator<PasswordLogin>，name 返回 password，requestType 返回 PasswordLogin.class；按 username 校验密码和状态，返回安全用户 DTO |
| `AdminAuthenticationService.load` | 同一 Bean 实现 UserStore；按 userId 查询最新用户及权限，拒绝停用/锁定/已删除用户，不再次校验密码 |
| `AuthController` | 先处理验证码，然后调用 ISecurityManager.login(new PasswordLogin(username, password, deviceType)) |
| `security-jwt` / 公开扩展接口 `JwtTokenCodec` | 提供无状态 Token 登录；需要定制生成与解析时替换 JwtTokenCodec，注销不撤销旧凭证 |
| 框架登录拦截器 / 权限 AOP | Bearer Token 恢复当前用户，请求结束清理；检查登录、角色和权限 |

Controller 中的核心代码（PasswordLogin 从 com.yulinlin.security.auth 导入）：

```java
authentication.validateCode(request.code());
String deviceType = request.deviceType() == null ? "web" : request.deviceType();
return R.ok(security.login(new PasswordLogin(request.username(), request.password(), deviceType)), "登录成功");
```

也可调用 `SecurityUtil.login(new PasswordLogin(username, password, deviceType))`，它与注入管理器等价。业务完成外部认证后可用 `UserIdLogin(verifiedUserId, deviceType)`，但不能向客户端开放任意 ID 登录。登录 JSON 示例：

```json
{"username":"admin","password":"你的管理员密码","deviceType":"web"}
```

`deviceType` 可不传，admin 接口默认 `web`；显式传值只接受 1～32 位字母、数字、下划线或短横线。它是登录设备分类，不是凭证，也不自动读取加密的 Device-Type 请求头。

响应为 `R<LoginResult>`，`data` 包含 `token`、`expiresAt`、`user`、`authType`。authType 为成功认证器的 name。默认 JWT 使用 HS256，只承载用户 ID 和必要登录元数据，不保存密码或角色权限。`AdminAuthenticationService.load(userId)` 每次请求查最新用户及关联角色，生成不可变 `AdminUserDetails`，不是持久化完整用户快照。角色权限变化在下一次请求生效，同一请求期间读取同一线程快照。

本案例密码登录的 authType 固定为 `password`，其他服务端代码可通过 `SecurityUtil.getAuthType()` 读取。Admin 不开放 ID 登录接口，也不接受客户端自报 authType。新架构要求 JWT 包含有效 deviceType/authType；缺少这些字段的旧 Token 需要重新登录。

账号认证和用户加载共用一个业务 Bean，不需要额外注册第二个 UserStore。登录按用户名查询，后续请求始终按 Token 内的稳定用户 ID 查询；修改用户名不会把旧 Token 错当成另一个用户名。原独立 AdminUserStore 已合并，不再保留重复组件。

前端后续请求携带 `Authorization: Bearer <token>`。JWT 按 `yulinlin.security.jwt.ttl` 到期，查询/刷新资料不续期；同一用户可以持有多个 JWT。注销和请求结束都只清理当前线程，客户端应自行删除 Token；再次提交旧 JWT 仍可通过验证，直到过期或用户不允许访问。签名密钥、issuer 等校验配置不变时，重启无需恢复登录记录，未过期 Token 仍可用。后台线程不继承身份。

admin 不声明 `ISecurityManager` Bean，框架自动提供唯一的 JWT 管理器；不再有登录 Store 或按用户撤销 API。需要注销后强制失效、踢人或在线会话管理时，改选 security-sa-token，或实现自己的 ISecurityManager；不能给纯 JWT 加一个 Redis 配置就自动获得有状态能力。选型与扩展见[JWT 与 Sa-Token 对比](../security/modules.md#jwt-与-sa-token-如何选择)和[自定义实现](../security/extension.md#接入外部安全框架)。

密码使用 JDK `PBKDF2WithHmacSHA256`，每次生成独立 16 字节随机盐、600000 次迭代和 256 位校验值；通过 `MessageDigest.isEqual` 比较。参数参考 [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)。数据库只保存版本、迭代数、盐和哈希组成的字符串，用户 VO、`IUserDetails` 以及日志不包含密码哈希。登录查库不加 `@JoinCache`，SQL 参数日志也默认关闭。

用户名不存在和密码错误统一提示“用户名或密码错误”，抛出 `LoginException` 并由案例异常处理器返回 HTTP 401。业务请求可携带 code，但本例未接入验证码服务，非空 code 会明确拒绝；验证码不传给框架，业务先完成验证码检查，再调用 login(new PasswordLogin(...))。

案例复用 security 公共鉴权 AOP：三个 Controller 类使用 `@ApiLogin`，登录方法通过 `@ApiPublic` 放行，其他方法默认需要登录；管理方法使用 `@ApiPerm("user:update")` 等权限声明。不再实现 AdminSecurityManager，也不再自建随机 Token、用户权限快照缓存或登录拦截器。

Permission 枚举仍是业务权限字典，角色集合和用户详情按原有规则加载；公共注解使用字符串编码，也可使用业务定义的 public static final String 常量，不能在注解参数中调用枚举 getCode()。权限精确匹配，无角色名自动越权或通配符解释。未登录/Token 失效为 HTTP 401，缺少权限为 HTTP 403；完整规则和 AOP 边界见[接口安全专题](../security/authorization.md#鉴权声明注解)。Service 仍负责业务事务、内置管理员保护和提交后刷新，不把登录检查加到 ISecurityManager 自己的身份加载路径上。

| 操作 | 无状态 JWT 的行为 |
| --- | --- |
| 修改昵称、替换用户角色集合 | 下次请求由 UserStore 加载新资料；提交后 refresh 同一当前用户时也刷新本线程快照，Token 不变 |
| 修改角色权限、删除角色 | 不遍历在线会话，下次请求重新读取关联权限 |
| 停用、锁定或删除用户 | 后续请求被 UserStore 拒绝；JWT 本身没有撤销 |
| 重新启用用户 | 尚未过期、验签有效的旧 JWT 可以继续使用 |
| 重置密码 | 只改密码；已签发 JWT 仍有效，不会自动让所有设备退出 |
| 注销 | 清理当前线程，客户端删除 Token；服务端不记录黑名单 |
| 写事务回滚 | 不更改数据库状态，不执行提交后资料刷新 |

加载用户或权限失败时请求失败，不使用旧权限兜底。手工修改用户状态、角色或权限后，下一次请求会读到新值；经 Service 或直接改库重置密码都不会撤销 JWT。已经进入业务的请求不会被中途强制停止。删除用户后不要复用其 userId，否则旧 JWT 在有效期内可能被新身份接受。多实例使用一致的签名/校验配置和用户数据，不需要共享 Token 登录表；强制失效需求应选择有状态方案，不能将此案例宣称为在线会话管理。

### 业务写法与 HTTP 调用

Controller 全部使用 `@PostMapping`，参数对象集中在 `RbacRequests`，开启 `@Valid`，返回 `R<E>`；Entity、PO、VO 分开。`UserPage extends PagePo` 用 `@JoinWhere` 解析用户名 LIKE 与状态等值条件；`RolePage` 支持角色编码等值、名称 LIKE。页码、每页条数和排序字段在 Service 校验，每页最多 100 条；HTTP 查询不接受继承自 PagePo 的任意 `and/or` 字段条件，避免访问密码等内部字段。

### Service 完整 CRUD 与对外边界

两个业务 Service 直接实现框架的 `ISuperService`，不再是只提供几个自定义方法的普通类，也不继承已经弃用的 ServiceImpl：

```java
@Service("rbacRoleService")
@Transactional
@RequiredArgsConstructor
public class RoleService implements ISuperService<RoleEntity> {
    // 通用 CRUD + 角色校验；权限由下一次请求的 UserStore 加载
}

@Service("rbacUserService")
@Transactional
@RequiredArgsConstructor
public class UserService implements ISuperService<UserEntity> {
    // 通用 CRUD + 密码、状态、角色分配等业务增强
}
```

服务端可直接调用 `findById`、`findByIdIn`、`all`、`page(PagePo)`、`insertOne/insertList`、`updateOne/updateList`、`save/saveList`、`deleteById/deleteByIdIn`。必须通过注入的 Spring Service Bean 调用，才能建立事务。案例显式声明默认写方法的转发入口，并在批量写方法统一校验；单条和 save 分支也经过相同规则，不能绕过内置管理员角色保护、用户名/角色编码不可变和提交后资料刷新。服务端直接插入 UserEntity 时，passwordHash 必须先通过 `PasswordHash.encode` 生成，不能保存明文。

HTTP 同样提供完整的列表、详情、分页、单条/批量增删改，但保留请求 DTO 与响应 VO，不直接继承 `RESTfulController` 的实体 `put/puts` 接口。原因是用户密码、角色、状态各有独立权限，直接开放 Entity 更新会绕过这些边界。普通无敏感字段的业务仍可按[完整 CRUD 案例](crud-example.md)继承通用 Controller；RBAC 案例通过安全的 DTO 接口展示业务扩展。

批量创建/修改使用 `{"items":[...]}`，批量删除使用 `{"ids":[...]}`，每次 1–100 条；删除 ID 去重，返回实际处理条数。在一个 Service 事务内执行，数据库写入阶段失败会整体回滚；资料刷新回调在提交后执行，不撤销 JWT。批量新增和修改调用 Repository 的批量写入；删除角色先合并全部待删编码，对每个用户的角色集合只修改一次，再批量删除角色，避免同一用户关联多个待删角色时修改互相覆盖。`/users/list` 接收 UserPage，按页返回脱敏列表、最多 100 条，不是无界全表导出。用户普通更新（含批量）只修改昵称，授权、状态和密码仍走对应专用接口。

例如批量创建角色：

```http
POST /rbac/roles/createBatch
Authorization: Bearer <管理员Token>
Content-Type: application/json

{"items":[{"code":"reader","name":"只读人员","permissions":["user:query"]},{"code":"auditor","name":"审核人员","permissions":["user:query","role:query"]}]}
```

成功返回 `R<List<String>>`，data 中的 ID 顺序与 items 一致；批量修改/删除返回 `R<Integer>`，表示处理条数。请求明细与返回处理可参考 [rbac.http](../../admin/rbac.http)。

修改业务由外部 Spring Bean 调用 Service，示例采用：

```java
@Transactional
@JoinSync
public void assignRoles(RbacRequests.AssignRoles request) {
    UserEntity user = requireUser(request.id());
    user.setRoleCodes(validRoles(request.roleCodes()));
    user.setUptTime(DateTime.now());
    TransactionUtil.afterCommit(() -> security.refresh(request.id()));
}
```

必须通过集合 setter 替换；`user.getRoleCodes().add(...)` 不会触发 setter 跟踪。传 `[]` 清空角色或权限集合，不能用 `null` 清空列。`TransactionUtil` 从 `com.yulinlin.data.core.transaction` 导入，只向当前框架事务注册；`@Transactional` 由默认事务适配器接入，最外层实际提交成功、旧框架上下文清理后执行回调，无框架事务直接报错。已替换案例原有的临时 `AfterCommit` 类。Repository 只声明普通查询和 CRUD，不放业务事务、不使用类级缓存覆盖认证查询。提交后的权限刷新通过 `findSnapshotById` 显式 `.autoUpdate(false)` 读取普通快照，不继续使用已经完成的原事务代理。

全部接口前缀为 `/rbac`：

| 路径（全部 POST） | 请求示例或用途 | 所需权限 |
| --- | --- | --- |
| `/auth/login` | `{"username":"admin","password":"你配置的密码","deviceType":"web"}`；deviceType 可省略，code 由业务验证 | 匿名 |
| `/auth/me`、`/auth/refresh`、`/auth/logout` | 当前用户、刷新资料、注销；无需请求体 | 已登录 |
| `/users/page` | `{"pageNumber":1,"pageSize":15,"username":"reader","status":"enabled"}` | `user:query` |
| `/users/list` | 与 `/users/page` 相同条件，只返回当前页的列表 | `user:query` |
| `/users/detail` | `{"id":"用户ID"}` | `user:query` |
| `/users/statuses` | 状态字典；无需请求体 | `user:query` |
| `/users/create` | `{"username":"reader01","password":"Example-Password-2026","nickname":"只读示例","roleCodes":["reader"]}` | `user:create` |
| `/users/createBatch` | items 数组内放置 `/users/create` 请求对象，返回用户 ID 列表 | `user:create` |
| `/users/update` | `{"id":"用户ID","nickname":"新昵称"}` | `user:update` |
| `/users/updateBatch` | `{"items":[{"id":"用户ID","nickname":"新昵称"}]}` | `user:update` |
| `/users/status` | `{"id":"用户ID","status":"disabled"}` | `user:update` |
| `/users/roles` | `{"id":"用户ID","roleCodes":["reader"]}` | `user:assign` |
| `/users/password` | `{"id":"用户ID","password":"Another-Password-2026"}` | `user:password` |
| `/users/delete` | `{"id":"用户ID"}` | `user:delete` |
| `/users/deleteBatch` | `{"ids":["用户ID1","用户ID2"]}`；不能删除当前登录账号 | `user:delete` |
| `/roles/list`、`/roles/permissions` | 角色列表、可分配权限字典；无需请求体 | `role:query` |
| `/roles/page` | `{"pageNumber":1,"pageSize":15,"name":"只读"}`，可选 code、orderList | `role:query` |
| `/roles/detail` | `{"id":"角色ID"}` | `role:query` |
| `/roles/create` | `{"code":"reader","name":"只读人员","permissions":["user:query"]}` | `role:create` |
| `/roles/createBatch` | items 数组内放置 `/roles/create` 请求对象，返回角色 ID 列表 | `role:create` |
| `/roles/update` | `{"id":"角色ID","name":"新名称","permissions":["user:query","role:query"]}` | `role:update` |
| `/roles/updateBatch` | items 数组内放置 `/roles/update` 请求对象 | `role:update` |
| `/roles/delete` | `{"id":"角色ID"}` | `role:delete` |
| `/roles/deleteBatch` | `{"ids":["角色ID1","角色ID2"]}`；同样禁止删除 admin | `role:delete` |

推荐按 [rbac.http](../../admin/rbac.http) 顺序执行：管理员登录 → 创建只读角色 → 创建用户 → 用户登录 → 验证 403 → 修改角色权限 → 用原 Token 验证新增权限 → 停用账号 → 验证原 Token 返回 401。请求文件有演示密码和占位管理员密码，使用前替换；重复创建同名用户或角色会被唯一约束拒绝。

本案例于 2026-10-21 更新为 LoginAuthenticator 统一认证入口，同步迁移相关回归用例源码和 HTTP 请求示例；遵循仓库维护约定，未执行编译、打包、测试或真实 MySQL 联调，不能将提供请求示例视为已经验收通过。

---

下一步：返回[项目实践主题目录](README.md)按需选择相关专题。
