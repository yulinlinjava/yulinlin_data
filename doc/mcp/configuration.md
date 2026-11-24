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
    token:
      max-ttl-days: 0              # 非管理员签发/续期的最长天数，0 不限
      max-per-owner: 50            # 每个账号未撤销令牌的上限，0 不限
      cache-seconds: 10            # 令牌全量内存缓存刷新间隔，0 关闭，最大 300
      max-rotate-grace-seconds: 604800  # 轮换宽限期上限，最大 30 天
    data:                          # 数据工具，配置了 read 或 write 才启用，见 data.md
      read: ["*"]                  # 可读的数据源组，"*" 为全部 JDBC / ES 数据源
      write: []                    # 可写的 JDBC 组，开放 data.execute
      max-rows: 1000               # 单次查询返回给 AI 的最大行数，最大 100000
      timeout-seconds: 30          # 单次调用总时长（JDBC 事务超时 / ES 服务端超时），最大 3600
      script-row-budget: 200000    # data.analyze 脚本内所有查询合计最多读取的行数，调大前评估内存
    command:
      enabled: false
      shell: /bin/bash
      max-concurrent: 4            # 每个实例的并发命令数
      max-command-bytes: 65536
      default-timeout-seconds: 3600 # 0 不限时，最大 604800
      max-output-bytes: 8388608    # 每个任务最多保存 8 MiB 输出
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
| GET | `/api/mcp/tasks?page=1` | canManage | 全部任务，每页 30 条 |
| GET | `/api/mcp/tasks/{id}` | canManage | 任务详情 |
| GET | `/api/mcp/tasks/{id}/output?offset=0` | canManage | 按字节偏移读取输出，每次最多 64 KiB |
| POST | `/api/mcp/tasks/{id}/cancel` | canManage + `canInvoke(user,"task_cancel")` | 取消任务 |
| DELETE | `/api/mcp/tasks/{id}` | canManage | 删除已结束的任务及其输出 |
| GET | `/api/mcp/audit?type=call&page=1` | canManage | `call` 为工具调用审计，`manage` 为令牌管理审计；每页 50 条 |

- 所有写请求必须带 `X-MCP-Action: mcp-management`，并通过 Origin 和 HTTPS 校验。这个请求头只是防跨站标识，不是登录凭证。
- MCP 令牌**不能**用来访问管理接口。
- 命令功能关闭时，任务接口返回 404。
- Origin 被拒绝时会记录一条 WARN 日志，包含 `Origin`、`serverName`、`serverPort`、`scheme`，用来排查代理配置。日志不会记录 Authorization。

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

## 可选系统工具

数据工具（`data.sources`、`data.schema`、`data.query`、`data.execute`、`data.analyze`）单独说明，见[数据工具](data.md)。

以下系统工具默认全部关闭。开启前要确认风险：**命令工具的执行权限等同于应用进程**，不是沙箱。

| 开关 | 工具 |
| --- | --- |
| `monitor.enabled` | `server_info` |
| `command.enabled` | `command_execute`、`task_list`、`task_get`、`task_output`、`task_cancel` |
| `process.enabled` | `process_list`、`process_stop` |

这些工具都通过 `tools/list` 发布了完整的输入输出 Schema 和逐字段说明，AI 不需要读文档就能使用。它们和业务工具一样受令牌 scope 与 `canInvoke` 控制。建议只给专门的运维令牌授予 `command_execute`。

| 工具 | `structuredContent.data` |
| --- | --- |
| `server_info` | CPU、内存、磁盘数组、网卡数组。只反映当前 JVM 所在的服务器或容器，没有实时带宽 |
| `command_execute` / `task_get` / `task_cancel` | 任务对象：id、状态、时间、PID、退出码、实例、输出是否截断 |
| `task_list` | 有权访问的任务数组 |
| `task_output` | `text`、`base64`、`nextOffset`、`hasMore`、`truncated` |
| `process_list` | `items` 进程数组，以及 `page`、`totalPages`、`total`、`sampledAt`、`truncated` |
| `process_stop` | `pid`、`state`（requested / exited）、`force` |

### 后台命令

```json
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"command_execute","arguments":{"command":"date; uptime","cwd":"/tmp","timeoutSeconds":30,"requestId":"inspect-001"}}}
```

- 默认执行器只支持 Linux，通过配置的 shell `-c` 执行。工作目录必须是已存在的绝对路径，stdin 关闭，没有 PTY。
- 调用会立即返回任务，从 `data.id` 获取任务 ID，再用 `task_get` / `task_output` 跟进。HTTP 断开不会取消任务。
- `requestId` 在同一应用、同一令牌内唯一。相同编号且参数相同会返回已有任务，参数不同会返回 409 冲突。任务记录先落库再启动进程，多个实例并发提交也不会重复执行。
- 任务提交时和进程启动前都会重新检查令牌和权限。令牌在这期间被撤销、过期或缩小范围，任务会失败，不会启动进程。已经在运行的进程不会因为令牌失效被终止。
- 状态：`queued`、`running`、`cancelling`、`succeeded`、`failed`、`cancelled`、`timed_out`、`unknown`。应用重启后，本实例原来活跃的任务会被标为 `unknown`，不会重跑，也不会按旧 PID 杀进程。
- 输出保存在 `data-directory/application-id/instance-id/output`。超过上限后会继续读取并丢弃，同时标记 `truncated`。`offset` 是字节偏移，要无损拼接请使用 `base64`。
- 非管理员只能看到自己令牌提交的任务。读取输出、取消、删除必须访问任务所在的实例。

### 进程

- `process_stop` 必须原样传入 `process_list` 返回的 `stopIdentity` 作为 `identity`，并且 `confirm: true`。这样可以防止 PID 被复用后误杀其他进程。PID 1、当前进程及其祖先进程受保护。
- 取消任务会尝试终止可见的子进程；脱离进程树的服务需要单独处理。

## 保留与容量

- 每小时清理一次（首次在启动约 1 分钟后），清理过期审计、本实例已结束的任务和输出。
- 调用审计和管理审计保留 `retention-days` 天，并各自收敛到最近约 10,000 条。
- 任务最多约 2,000 条。令牌数量受 `token.max-per-owner` 限制，不会被自动清理；撤销的令牌保留到手动删除。
- 审计默认不保存命令、参数原文、令牌或工具输出。数据工具（`data.*`）例外：会记录数据源组和 SQL / 命令 / 脚本的开头（每项最多 500 字符），不记录参数值。
- 审计状态为 `started` 表示结果未确认，不能据此自动重试写操作。
