# MCP 快速接入与扩展

[文档首页](../README.md) / [MCP](README.md)

## 依赖和启动

沿用 JDK 25、Spring Boot 3.5.16 和框架 3.0，不为 MCP 升级整个应用。依赖来自同一份源码构建或团队制品仓库。

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>mcp</artifactId>
    <version>3.0</version>
</dependency>
```

模块依赖公共 `security`、`starter`，不选择 JWT、Sa-Token 等具体认证实现。应用继续选择自己的安全模块并提供 `UserStore`。默认持久化需要一个已经接入的 ORM 数据源；也可以实现 `McpStore`。缺少权限策略、用户加载或存储，启用时启动失败。

```yaml
yulinlin:
  mcp:
    enabled: true
    application-id: my-service
    group: h2  # 已配置的数据源名称；单数据源可省略
```

`enabled` 只控制模块装配。新数据库中的运行开关默认关闭，需要网页登录的管理员通过管理接口启用。详见[配置与管理](configuration.md)。

若使用 Spring Security 原生过滤器链，需要在现有链中允许 `/mcp` 到达 MVC，并只对这个 Bearer 入口忽略浏览器 CSRF 校验；认证由 MCP 入口完成。`@ApiPublic` 不会自动改写原生过滤器规则。例如在已有配置中加入 `requestMatchers("/mcp").permitAll()` 和 `csrf.ignoringRequestMatchers("/mcp")`。不要对 `/api/mcp/**` 放开网页登录或 CSRF 防护。项目自定义的认证 Filter 也不能提前吞掉 MCP 的 Bearer 头。

## 一个权限接口，三个明确的判断

```java
import com.yulinlin.mcp.auth.McpAccessPolicy;
import com.yulinlin.security.auth.IUserDetails;
import org.springframework.context.annotation.Bean;

@Bean
McpAccessPolicy mcpAccessPolicy() {
    return new McpAccessPolicy() {
        @Override public boolean canUse(IUserDetails user) {
            return user.hasPermission("mcp:use");
        }
        @Override public boolean canManage(IUserDetails user) {
            return user.hasPermission("mcp:manage");
        }
        @Override public boolean canInvoke(IUserDetails user, String toolName) {
            return user.hasPermission("mcp:tool:" + toolName);
        }
    };
}
```

权限名称完全由业务约定，框架没有默认放行或超级管理员特判：

| 判断 | 生效位置 |
| --- | --- |
| `canUse` | 创建自己的凭证、每次外部请求校验、工具调用 |
| `canManage` | 网页管理接口、跨凭证任务查看权限 |
| `canInvoke` | 工具列表过滤和实际执行前再次校验；管理页取消任务还需要 `task_cancel` 权限 |

管理权限不会自动赋予工具权限。仅有管理权限的用户可暂停 MCP、撤销凭证、查看管理记录，但不能给自己创建可用凭证或执行命令。新凭证绑定当前管理用户；首版不支持管理员替其他账号创建凭证。

`canInvoke` 只决定是否能调用工具。订单归属、租户、目录范围等数据权限仍由业务服务判断。工具回调在当前已认证用户的 `SecurityContext` 中运行，可继续调用已有 Spring Service 代理；同步调用结束或抛异常后恢复原身份，不创建网页登录会话，也不向其他线程继承身份。

## 显式注册业务工具

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.mcp.tool.McpToolDefinition;
import com.yulinlin.mcp.tool.McpToolProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;

// OrderService 是业务应用已有的服务，查询时检查当前用户的数据权限。
@Bean
McpToolProvider orderTools(ObjectMapper json, OrderService orders) throws Exception {
    var schema = json.readTree("""
        {
          "type": "object",
          "properties": {
            "id": {"type": "string", "minLength": 1, "maxLength": 64}
          },
          "required": ["id"],
          "additionalProperties": false
        }
        """);
    return () -> List.of(new McpToolDefinition(
        "order_query", "按订单编号查询当前用户可以访问的订单", schema, true,
        (context, args) -> orders.findVisible(context.user().getUserId(), args.get("id").asText())
    ));
}
```

`McpCallContext` 包含 `applicationId`、`credentialId`、当前用户、服务端 `requestId` 和 `contextPath`，不传凭证明文。这里的追踪 `requestId` 与 `command_execute` 参数里的幂等编号不同。

工具名重复启动报错。参数通过 JSON Schema Draft 2020-12 校验，支持嵌套、数组、`$defs`/本地 `$ref`、组合条件等；不自动加载远程 Schema。未知字段是否拒绝由 Schema 的 `additionalProperties` 决定，内置工具均为 `false`。`readOnly` 是工具描述，不会代替服务端授权。

可用完整构造器传入 `outputSchema`。为兼容已有客户端，返回值包装为 `structuredContent.data`，因此输出 Schema 应描述完整的 `{ "data": ... }` 对象。输出校验发生在业务执行之后，失败不能表示业务已回滚，客户端不可自动重试写操作。

## 身份与长期凭证

默认 `McpIdentityProvider` 调用 `UserStore.load(accountId)`。账号禁用、删除时，用户加载实现应返回 `null`；每次请求都会重新加载用户并执行权限策略。业务已有用户缓存时，缓存更新/失效仍由业务保证。

如果登录 ID 会随密码变化，需要提供稳定账号到当前身份的映射：

```java
@Bean
McpIdentityProvider mcpIdentityProvider(AccountService accounts) {
    return new McpIdentityProvider() {
        @Override public String accountId(IUserDetails user) {
            return accounts.stableAccountId(user.getUserId());
        }
        @Override public IUserDetails load(String accountId) {
            return accounts.loadEnabledUser(accountId); // 禁用或不存在返回 null
        }
    };
}
```

凭证随机生成，明文只在创建响应返回一次，数据库保存 SHA-256 摘要。`expiresAt` 省略、为 `null` 或 `0` 时永久有效；正数是到期毫秒时间戳，到期拒绝新请求。重启、网页退出及网页会话到期不会改变凭证自身的到期时间，也不会自动续期；撤销、暂停 MCP 或账号失去权限在后续请求生效。凭证不用于网页登录和 `/api/mcp` 管理认证。暂停或到期不会终止已经启动的后台进程，需要明确取消任务。详见[有效期规则和创建示例](configuration.md#凭证有效期)。

## 存储、审计和执行器扩展

| Bean | 业务可替换的行为 |
| --- | --- |
| `McpStore` | 凭证、运行开关、任务、审计的持久化 |
| `McpIdentityProvider` | 稳定账号 ID 和当前用户加载 |
| `McpAccessPolicy` | 使用、管理和工具调用权限；必须提供 |
| `McpToolProvider` | 注册任意业务工具；可有多个 Bean |
| `McpAuditTargetProvider` | 从参数中提取允许展示的目标 ID；默认空对象 |
| `McpTaskOutputResolver` | 本实例任务输出文件路径，包括兼容旧路径 |
| `CommandExecutor` | 本机命令启动策略；返回受任务服务跟踪的 `Process` |
| `McpManagementGuard` | 网页管理写请求的来源及请求头校验 |

`McpStore.create` 必须原子地创建唯一 key，重复必须报错，不能用 `save` 覆盖模拟；任务先成功持久化，再派发进程。跨实例共享凭证时，`get` 必须能看到已提交的撤销，不能读到未失效的本地缓存或延迟副本。默认 ORM 存储关闭查询缓存、从主节点读取，并通过数据库唯一索引保证原子创建。

审计默认不复制命令、密码、Token、文件内容或工具输出。可以只提取非敏感目标字段：

```java
@Bean
McpAuditTargetProvider auditTargets() {
    return (tool, args) -> tool.equals("order_query")
        ? java.util.Map.of("orderId", args.path("id").asText()) : java.util.Map.of();
}
```

每条审计目标最多 16 项，字段名最多 128 字符，值最多 512 字符。审计开始记录失败时不执行工具；操作完成后审计更新失败会返回提示，避免客户端误以为没有执行。
