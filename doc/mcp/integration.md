# MCP 快速接入

[文档首页](../README.md) / [MCP](README.md)

业务项目引入 MCP、做一些配置，外部 AI 就能通过 MCP 调用这个应用。先选场景：

| 想让 AI 做什么 | 引入 | 读完本页后再读 |
| --- | --- | --- |
| 调用业务方法（查订单、改状态等） | `mcp` | [业务工具](tools.md) |
| 运维这台服务器：执行命令和 sh 脚本、看进程、看服务器信息 | `mcp` + `mcp-ops` | [AI 运维](ops.md) |
| 读取项目数据（JDBC / ES）、修改数据 | `mcp` + `mcp-data` | [数据工具](data.md) |
| 在服务端做大数据量、跨表跨库的分析 | `mcp` + `mcp-data` + `mcp-script` | [数据工具](data.md#dataanalyzemcp-script) |

三类能力可以同时引入。核心模块 `mcp` 本身不带任何工具，工具都来自你写的 `@McpTool` 或上面的可选模块。

无论哪个场景，都先按顺序完成以下 8 步。前 6 步是代码和配置，后 2 步是运行时操作。

## 前提

- JDK 25、Spring Boot 3.5.16、框架 3.0。不要为了 MCP 升级整个应用。
- 应用已经选好登录模块（JWT 或 Sa-Token，或自定义 `ISecurityManager`），并提供了 `UserStore`。见[安全模块选型](../security/modules.md)。
- 应用已经接入至少一个 ORM 数据源，用于默认存储。没有数据源时，需要自行实现 `McpStore` 和 `McpTokenStore`，见[扩展点](extension.md)。

## 1. 引入依赖

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>mcp</artifactId>
    <version>3.0</version>
</dependency>
```

模块依赖公共的 `security` 和 `starter`，不会选择具体的登录实现。

按场景再加 `mcp-ops`、`mcp-data`、`mcp-script`（同一版本），各自的配置见对应文档。

## 2. 最小配置

```yaml
yulinlin:
  mcp:
    enabled: true              # 只控制模块是否装配
    application-id: my-service # 必填，稳定不变，1–64 位字母、数字、_、-
    group: h2                  # 存放 MCP 自身数据（开关、令牌、审计）的 JDBC 数据源组；只有一个数据源时可省略
```

- `enabled: true` 只表示注册 Bean。还有一个**运行开关**保存在数据库里，新库默认是关闭的，需要在第 7 步打开。
- `application-id` 用来隔离数据，改了就相当于换了一套空数据（令牌、开关、任务都看不到了）。
- 启动时会自动建两张表：`yulinlin_mcp_record`（开关、任务、审计）和 `yulinlin_mcp_token`（令牌）。

全部配置项见[配置](configuration.md#完整配置)。

## 3. 提供权限策略（必须）

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

权限名称完全由业务决定，框架没有默认放行，也没有超级管理员特判。

| 判断 | 作用 |
| --- | --- |
| `canUse(user)` | 账号能否使用 MCP。签发令牌时、每次外部请求、后台任务启动前都会检查令牌**所属账号** |
| `canManage(user)` | 启停 MCP、管理所有人的令牌、为他人签发、设置令牌 `attributes`、查看任务和审计 |
| `canInvoke(user, tool)` | 能否调用某个工具。`tools/list` 过滤和 `tools/call` 执行前都会检查 |

三个判断互相独立：

- `canManage` 不包含 `canUse` 或 `canInvoke`。只有管理权限的账号可以管理令牌、暂停 MCP，但给自己签发令牌仍然需要 `canUse`。
- 一次工具调用的实际权限是 **令牌所属账号当前的 `canInvoke` ∩ 令牌的 scope**。管理员身份也不能越过令牌的 scope。
- `canInvoke` 不代替数据权限。工具内部仍要按业务规则判断能访问哪些数据。

## 4. 账号映射（按需）

默认的 `McpIdentityProvider` 直接调用 `UserStore.load(accountId)`，令牌归属记录的是 `IUserDetails.getUserId()`。

如果用户 ID 可能变化（例如登录 ID 跟着用户名或密码变），需要提供稳定的账号映射：

```java
@Bean
McpIdentityProvider mcpIdentityProvider(AccountService accounts) {
    return new McpIdentityProvider() {
        @Override public String accountId(IUserDetails user) {
            return accounts.stableAccountId(user.getUserId());
        }
        @Override public IUserDetails load(String accountId) {
            return accounts.loadEnabledUser(accountId); // 禁用或不存在时返回 null
        }
    };
}
```

账号被禁用或删除时，`load` 必须返回 null，这样该账号的令牌会立即失效。

## 5. 放行 `/mcp` 入口

`/mcp` 用 Bearer 令牌认证，由 MCP 模块自己完成，不走网页登录。

- 使用框架自带拦截器时，`/mcp` 已经通过 `@ApiPublic` 放行，不需要额外配置。
- 项目自己接入了 Spring Security 过滤器链（自定义 `ISecurityManager`）时，需要在已有配置中加上：

```java
http.authorizeHttpRequests(a -> a.requestMatchers("/mcp").permitAll())
    .csrf(c -> c.ignoringRequestMatchers("/mcp"));
```

注意：

- **只放行 `/mcp`**。`/api/mcp/**` 是管理接口，必须继续要求网页登录和 CSRF 防护。
- 项目自定义的认证 Filter 不能提前消费或拒绝 `/mcp` 的 `Authorization: Bearer` 请求头。

## 6. 注册业务工具

给 Spring Bean 的方法加 `@McpTool`：

```java
@Component
public class OrderTools {
    private final OrderService orders;
    public OrderTools(OrderService orders) { this.orders = orders; }

    @McpTool(name = "order_query", description = "按编号查询当前用户有权查看的订单", readOnly = true)
    public OrderResult query(OrderRequest request) {
        return orders.findVisible(SecurityContext.getUserId(), request.id());
    }
}
```

DTO 写法、上下文参数、Schema 规则等详见[业务工具](tools.md)。

如果希望 AI 直接读取项目数据（JDBC、Elasticsearch）或在服务端做数据分析，可以开启数据工具，配置 `yulinlin.mcp.data.read: ["*"]` 即可，见 [数据工具](data.md)。

## 7. 启用并签发令牌（运行时）

以下请求都需要网页登录身份，写请求必须带 `X-MCP-Action: mcp-management` 请求头：

```http
PUT /api/mcp
X-MCP-Action: mcp-management
Content-Type: application/json

{"enabled": true}
```

```http
POST /api/mcp/credentials
X-MCP-Action: mcp-management
Content-Type: application/json

{"name": "我的 AI 助手", "scope": ["order_query"], "expiresAt": null}
```

响应里的 `data.token` 就是令牌明文，**只返回这一次**。完整接口和参数见[令牌管理](tokens.md#管理接口)。

## 8. 配置外部客户端

客户端连接地址为 `https://你的域名/mcp`，并需要配置请求头：

```http
Authorization: Bearer ylmcp_xxxxxxxxxxxx_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

客户端必须支持自定义 Bearer 请求头。目前不支持 OAuth 自动发现，只支持 OAuth 的客户端暂时无法直接接入。协议细节见[客户端协议](configuration.md#客户端协议)。

## 接入检查清单

- [ ] 依赖版本与应用一致（3.0）
- [ ] 配置了 `enabled`、稳定的 `application-id`；多数据源时配置了 `group`
- [ ] 提供了 `McpAccessPolicy` Bean，三个判断各自独立
- [ ] 用户 ID 不稳定时提供了 `McpIdentityProvider`
- [ ] 只放行了 `/mcp`，`/api/mcp/**` 仍然需要登录和 CSRF
- [ ] 每个工具内部都做了数据权限判断
- [ ] 只引入需要的可选模块；引入 `mcp-ops` 时只在需要的机器上打开命令开关，并按[安全建议](ops.md#安全建议)使用专门的系统账号和运维令牌
- [ ] 运行开关已打开，令牌已签发，客户端已配置
