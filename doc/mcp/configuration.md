# MCP 配置与系统工具

[文档首页](../README.md) / [MCP](README.md)

## 完整配置

```yaml
yulinlin:
  mcp:
    enabled: false                 # 默认不装配任何 MCP Bean
    application-id: my-service     # 开启时必填；稳定的应用标识
    instance-id: local             # 同时运行的多个副本必须不同；同一副本重启保持不变
    group: h2                      # 存放 MCP 自身数据的 JDBC 数据源组；单数据源可省略，多数据源必填
    path: /mcp                     # 外部客户端入口
    management-path: /api/mcp      # 网页管理接口前缀
    allow-insecure: true           # 允许 HTTP；false 时非本机请求必须 HTTPS
    allowed-origins: []            # 默认只接受同源 Origin；不带 Origin 的非浏览器请求不受影响
    max-request-bytes: 4194304     # 单次请求上限，默认 4 MiB，范围 1 B～64 MiB
    data-directory: ./mcp-data     # 命令输出文件目录
    retention-days: 30             # 审计保留天数
    instructions:                  # 连接时返回给模型的服务说明；不配时按令牌可用的工具组自动生成，最长 8000 字符
    token:
      max-ttl-days: 0              # 非管理员签发/续期的最长天数，0 不限
      max-per-owner: 50            # 每个账号未撤销令牌的上限，0 不限
      cache-seconds: 10            # 令牌全量内存缓存刷新间隔，0 关闭，最大 300
      max-rotate-grace-seconds: 604800  # 轮换宽限期上限，最大 30 天
    data:                          # 数据工具，需要引入 mcp-data，配置了 read 或 write 才启用，见 data.md
      read: ["*"]                  # 可读的数据源组，"*" 为全部 JDBC / ES 数据源
      write: []                    # 可写的 JDBC 组，开放 data.execute
      max-rows: 1000               # 单次查询返回给 AI 的最大行数，最大 100000
      timeout-seconds: 30          # 单次调用总时长（JDBC 事务超时 / ES 服务端超时），最大 3600
      script-row-budget: 200000    # data.analyze 脚本内所有查询合计最多读取的行数，调大前评估内存
    command:                       # 以下 command / process / monitor 需要引入 mcp-ops，见 ops.md
      enabled: false
      shell: /bin/bash
      max-concurrent: 4            # 每个实例的并发命令数
      max-command-bytes: 65536
      default-timeout-seconds: 3600 # 0 不限时，最大 604800
      max-output-bytes: 8388608    # 每个任务最多保存 8 MiB 输出
      audit-command: true          # 审计记录命令原文（最多 512 字符）；false 时只记录长度
    process:
      enabled: false
    monitor:
      enabled: false
```

- `application-id`、`instance-id` 只允许 1～64 位字母、数字、`_`、`-`，不合法会启动失败。
- `path` 和 `management-path` 不能相同；和已有 Controller 路径冲突时 Spring MVC 会启动报错。
- 部署在反向代理后面时，由应用配置可信代理（如 `server.forward-headers-strategy`），以恢复正确的 scheme、host 和客户端 IP。模块不会自行信任客户端传来的转发头。

### 数据存储

| 表 | 内容 | 隔离 |
| --- | --- | --- |
| `yulinlin_mcp_record` | 运行开关、后台任务、调用审计、管理审计、数据写入（`data.execute` / `data.analyze`）的幂等记录（JSON 记录） | key 前缀为 `application-id` |
| `yulinlin_mcp_token` | 令牌，只存 secret 的 SHA-256 | `application_id` 列 |

两张表都在启动时由 ORM `initializeSchema` 创建，遵循数据源自身的建表或校验策略，不需要额外配置实体扫描。同一应用的多个实例共享令牌、开关和任务元数据，但输出文件和进程控制归属于各自实例。这不是分布式任务调度器。

## 管理接口

接口使用网页登录身份，返回框架的 `R` 包装。令牌相关接口见[令牌管理](tokens.md#管理接口)。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/mcp` | 登录 | 返回 `enabled`、`manager`、`canUse` |
| PUT | `/api/mcp` | canManage | `{"enabled":true}` 启用或暂停 MCP |
| GET | `/api/mcp/tools` | 登录 | 当前账号有权限的工具目录 |
| GET | `/api/mcp/audit?type=call&page=1` | canManage | `call` 为工具调用审计，`manage` 为令牌管理审计；每页 50 条 |

- 所有写请求必须带 `X-MCP-Action: mcp-management`，并通过 Origin 和 HTTPS 校验。这个请求头只是防跨站标识，不是登录凭证。
- MCP 令牌**不能**用来访问管理接口。
- 命令任务的管理接口（`/api/mcp/tasks`）由 `mcp-ops` 提供，见 [AI 运维](ops.md#管理接口)。
- Origin 被拒绝时会记录一条 WARN 日志，包含 `Origin`、`serverName`、`serverPort`、`scheme`，用来排查代理配置。日志不会记录 Authorization。

### 服务说明（instructions）

客户端连接时会先调用 `initialize`，返回里的 `instructions` 会被客户端放进模型上下文，是 AI 最先看到的一段说明，不需要 AI 知道任何额外接口。

- **不配置**：由三部分自动拼成：通用开头（应用名、可用工具数、参数以 `tools/list` 为准）、各工具模块按**当前令牌在 `tools/list` 里能看到的工具**补充的一段、通用结尾（写操作结果不确定时先查询确认）。核心模块不认识任何具体工具，只有引入了的模块、并且令牌能用它的工具时，才会出现对应段落。例如引入 `mcp-data` 且令牌能用 `data.*` 时，会说明先调 `data.sources` 再调 `data.schema`；能用 `data.execute` 时会说明先预演再执行；引入 `mcp-ops` 且令牌能用命令工具时，会说明用 `task_get` 跟进后台任务。业务的 `McpToolProvider` 也可以覆盖 `instructions(visibleTools)` 补充自己的段落，见[显式注册](tools.md#显式注册)。
- **配置了**：原样返回配置的内容，不再自动生成。适合写业务背景，例如：

```yaml
yulinlin:
  mcp:
    instructions: |
      这是订单系统的 MCP 服务。金额单位为分，时间为北京时间。
      查数据先调 data.sources 和 data.schema；改数据先 dryRun 预演并让用户确认。
```

配置后会完全替换自动生成的内容，需要的话把工具使用顺序也写进去。这段文字所有有效令牌都能看到，不要放敏感信息。每个工具的详细用法仍然以 `tools/list` 里的说明为准。

## 客户端协议

入口为 `POST /mcp`，采用无会话的 JSON Streamable HTTP 和 JSON-RPC 2.0。

| 项 | 支持情况 |
| --- | --- |
| 方法 | `initialize`、`notifications/initialized`（返回 202）、`ping`、`tools/list`、`tools/call` |
| 协议版本 | `2025-03-26`、`2025-06-18`、`2025-11-25` |
| 认证 | `Authorization: Bearer <令牌>`，可以通过 [`McpAuthenticator`](extension.md#认证方式mcpauthenticator) 扩展 |
| 不支持 | OAuth 自动发现、GET SSE、旧版 HTTP+SSE、交互式输入、协议级 tasks 扩展、resources、prompts |

请求头：

```http
Authorization: Bearer ylmcp_<keyId>_<secret>
Content-Type: application/json
Accept: application/json, text/event-stream
MCP-Protocol-Version: 2025-06-18
```

示例：

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"example-client","version":"1.0"}}}
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"order_query","arguments":{"id":"order-123"}}}
```

HTTP 状态码：

| 状态 | 含义 |
| --- | --- |
| 200 | 正常，或业务错误（`isError: true`），或 JSON-RPC 参数错误 |
| 400 | JSON 解析失败、请求格式错误、协议版本不支持 |
| 401 | 令牌无效、过期、禁用、撤销，或所属账号失去 `canUse` |
| 403 | MCP 已暂停、Origin 或 HTTPS 校验失败、IP 不在白名单、没有工具权限 |
| 413 / 415 / 406 | 请求过大 / Content-Type 不是 JSON / Accept 不接受 JSON |
| 429 | 超过令牌限流（`rateLimit`）或入口限流 |
| 500 | 审计存储不可用，工具未执行 |

入口还叠加了 `@ApiLimit(count=180, interval="1m", by=IP)`。反向代理可以再加请求大小和速率限制。

## 可选工具模块

核心模块 `mcp` 自己不带任何工具，工具由可选模块提供，引入后再按配置启用：

| 模块 | 工具 | 启用配置 | 说明 |
| --- | --- | --- | --- |
| `mcp-ops` | `command_execute`、`task_*`、`process_*`、`server_info` | `command.enabled`、`process.enabled`、`monitor.enabled` | [AI 运维](ops.md) |
| `mcp-data` | `data.sources`、`data.schema`、`data.query`、`data.execute` | `data.read` / `data.write` | [数据工具](data.md) |
| `mcp-script` | `data.analyze` | 引入即启用（需要 `mcp-data` 已启用） | [数据工具](data.md#dataanalyzemcp-script) |

## 保留与容量

- 每小时清理一次（首次在启动约 1 分钟后），清理过期审计、本实例已结束的任务和输出。
- 调用审计和管理审计保留 `retention-days` 天，并各自收敛到最近约 10,000 条。
- 任务最多约 2,000 条。令牌数量受 `token.max-per-owner` 限制，不会被自动清理；撤销的令牌保留到手动删除。
- 审计默认不保存命令、参数原文、令牌或工具输出。数据工具（`data.*`）例外：会记录数据源组和 SQL / 命令 / 脚本的开头（每项最多 500 字符），不记录参数值。
- 审计状态为 `started` 表示结果未确认，不能据此自动重试写操作。
