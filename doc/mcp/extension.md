# MCP 扩展点

[文档首页](../README.md) / [MCP](README.md)

所有默认实现都带有 `@ConditionalOnMissingBean`。注册同类型的 Bean 就会替换默认实现，不需要继承框架的具体类，也不需要开启 Bean 覆盖。各扩展点互相独立，可以只替换其中一个。

## 一览

| Bean | 用途 | 默认实现 |
| --- | --- | --- |
| `McpAccessPolicy` | 使用、管理、工具调用权限 | **无，必须提供** |
| `McpIdentityProvider` | 稳定账号 ID 与当前用户加载 | `UserStore::load` |
| `McpToolProvider` | 显式注册工具，可以有多个 | 无（见[显式注册](tools.md#显式注册)） |
| `McpAuthenticator` | 增加认证方式，可以有多个 | `TokenAuthenticator` |
| `McpTokenStore` | 令牌持久化 | `OrmMcpTokenStore` |
| `McpStore` | 运行开关、任务、审计的持久化 | `OrmMcpStore` |
| `McpAuditService` | 调用审计与管理审计 | `McpAudit` |
| `McpAuditTargetProvider` | 从参数中挑出允许写入审计的目标字段 | `DataAuditTargets::of`：只处理 `data.*` 工具（数据源组、SQL / 命令开头），其他工具返回空 |
| `McpTaskOutputResolver` | 任务输出文件路径 | `data-directory/application-id/instance-id/output/{id}.log` |
| `CommandExecutor` | 本机命令启动方式 | `LocalCommandExecutor` |
| `McpManagementGuard` | 管理写请求的来源和请求头校验 | Origin / HTTPS 校验 + `X-MCP-Action` |
| `McpLifecycle` | 可选功能参与启动校验（`initialize`）和每小时清理（`clean`），可以有多个 | 数据工具使用 |
| `McpDataService` | 数据工具的执行引擎，见 [数据工具](data.md) | 配置了 `yulinlin.mcp.data.read` 或 `write` 时注册 |

要完全不用 ORM 数据源，需要同时提供 `McpStore` 和 `McpTokenStore`。

## 监听令牌事件

不需要替换任何组件就可以接收令牌事件。每次签发、修改、轮换、禁用、启用、撤销、删除都会发布 `McpTokenEvent`：

```java
@EventListener
public void onToken(McpTokenEvent event) {
    // event.action()   ISSUE / UPDATE / ROTATE / DISABLE / ENABLE / REVOKE / DELETE
    // event.token()    操作后的令牌信息（McpTokenView，不含 hash）
    // event.actorId()  操作人账号；event.ip() 来源 IP
    // event.changes()  变化字段 {字段: {from, to}}
}
```

事件同步发布。监听器抛出的异常只记录日志，不会回滚已完成的操作。

## 认证方式：`McpAuthenticator`

用于接入 OAuth、SSO、网关签名等方式。多个认证器按 `@Order` 依次尝试，第一个返回非 null 的结果生效。

```java
@Component
public class GatewayAuthenticator implements McpAuthenticator {
    public String type() { return "gateway"; }

    public McpPrincipal authenticate(HttpServletRequest request) {
        String user = verifiedGatewayUser(request);   // 业务自己校验网关签名
        if (user == null) return null;                // 不是这种方式，交给下一个认证器
        return new McpPrincipal("gateway", "gw-" + user, "网关调用", user,
                List.of("@readonly"), Map.of("tenant", tenantOf(user)));
    }
}
```

| 约定 | 说明 |
| --- | --- |
| 返回 null | 请求中没有本认证器能识别的凭证 |
| 抛 `McpException(401)` | 能识别，但凭证无效 |
| `type()` | 全局唯一，并且与返回的 `McpPrincipal.type()` 一致，否则启动或认证失败 |
| `refresh(principal)` | 后台任务启动进程前调用，用于重新确认凭证。默认原样返回；支持撤销的方式应当覆盖，失效时返回 null |

不论使用哪种认证器，框架都会统一执行：运行开关、`McpIdentityProvider.load`、`canUse`、scope 和 `canInvoke` 校验。

`McpPrincipal` 的字段：`type`、`credentialId`（任务归属和审计使用）、`name`、`accountId`、`scope`（[写法](tokens.md#范围scope)）、`attributes`。

## 令牌存储：`McpTokenStore`

| 方法 | 约定 |
| --- | --- |
| `initialize()` | 启动时调用，可以在这里建表 |
| `findById` / `findByKeyId` | 按索引查找当前应用的数据，找不到返回 null |
| `list(query)` / `count(query)` | 按 `McpTokenQuery` 的 ownerId、status、keyword、分页过滤。`ACTIVE` 和 `EXPIRED` 要用 `query.now()` 结合 `expiresAt` 判断 |
| `countUnrevoked(ownerId)` | 统计未撤销的数量 |
| `insert(token)` | `keyId` 冲突时抛异常 |
| `update(token, expectedVersion)` | 乐观锁：只有库中 version 等于 expectedVersion 时才写入，返回是否成功。不能覆盖 `lastUsedAt` / `lastUsedIp` |
| `markUsed(id, at, ip)` | 只更新使用信息。要单调递增，并且跨实例每分钟最多写一次 |
| `delete(id)` | 物理删除 |

必须按 `application-id` 隔离数据。不能只靠单 JVM 锁保证并发安全，因为可能有多个实例。

## 通用存储：`McpStore`

保存运行开关、任务和审计，是一个按 key 存取的 JSON 存储。

- `create(key, kind, value)` 必须**原子**地创建唯一 key，key 已存在时必须报错，不能用 `save` 覆盖来模拟。任务防重复提交依赖这一点。
- `get` 必须能读到其他实例已提交的变更，不能返回过期的本地缓存。默认实现关闭了查询缓存，并且从主库读取。
- `list(kind, page, size)` 按更新时间倒序返回。

## 审计：`McpAuditService`

```java
public final class BusinessMcpAudit implements McpAuditService {
    private final BusinessAuditRepository repository;
    public BusinessMcpAudit(BusinessAuditRepository repository) { this.repository = repository; }

    @Override
    public McpAudit.Entry begin(McpPrincipal principal, String tool, JsonNode args) {
        var entry = new McpAudit.Entry(McpStore.id(), principal.credentialId(), principal.name(),
                principal.accountId(), tool, Map.of(), System.currentTimeMillis(), null, "started");
        repository.save(entry);          // 保存失败要抛异常，框架就不会执行工具
        return entry;
    }
    @Override
    public void finish(McpAudit.Entry entry, boolean success) {
        repository.save(new McpAudit.Entry(entry.id(), entry.credentialId(), entry.clientName(),
                entry.userId(), entry.tool(), entry.target(), entry.createdAt(),
                System.currentTimeMillis(), success ? "succeeded" : "failed"));
    }
    @Override public List<McpAudit.Entry> list(int page) { return repository.pageCalls(page, 50); }

    // 令牌管理审计，可选；默认是空实现
    @Override public void recordManagement(McpAudit.ManagementEntry entry) { repository.saveManagement(entry); }
    @Override public List<McpAudit.ManagementEntry> listManagement(int page) { return repository.pageManagement(page, 50); }

    @Override public void clean() { repository.cleanExpired(); }
}
```

- `begin` 必须返回非 null，并且和 `finish` 使用同一个 id。`begin` 失败时工具不会执行；`finish` 失败时会提示“操作已完成，但审计保存失败”，客户端不应因此重试。
- 自定义审计会收到原始参数，脱敏由实现负责。
- 异步写日志时，要说明返回前是可靠入队还是只放进内存。队列满时应抛异常，不能静默丢弃。
- `clean()` 由模块每小时调用一次。外部日志平台自己管理保留期的，可以实现成空方法。

只想往默认审计里增加可展示的目标字段，用 `McpAuditTargetProvider` 即可。替换后默认的数据工具字段不再记录，需要的话在实现里调用 `DataAuditTargets.of(tool, args)` 合并：

```java
@Bean
McpAuditTargetProvider auditTargets() {
    return (tool, args) -> tool.equals("order_query")
        ? Map.of("orderId", args.path("id").asText()) : Map.of();
}
```

每条审计最多 16 个目标字段；字段名最多 128 字符，值最多 512 字符。不要放入密码、令牌或文件内容。

## 其他

- **`McpTaskOutputResolver`**：参数是已经校验过的 32 位十六进制任务 ID，返回输出文件路径。可以用来兼容旧的输出目录。
- **`CommandExecutor`**：`start(command, directory)` 返回一个受任务服务跟踪的 `Process`。
- **`McpManagementGuard`**：覆盖时应当先调用 `McpHttpGuard.check(request)`，再校验自己的请求头。不能只校验固定请求头而忽略 Origin。
