# MCP 配置、管理和任务

[文档首页](../README.md) / [MCP](README.md)

## 完整配置

```yaml
yulinlin:
  mcp:
    enabled: false                 # 默认不注册接口、清理任务、命令执行器
    application-id: my-service     # 开启时必填；稳定的应用标识
    instance-id: local             # 同时运行的副本必须不同；同一副本重启保持稳定
    group: h2                      # ORM 数据源名称；单数据源可省略
    path: /mcp
    management-path: /api/mcp
    allow-insecure: true           # 允许 HTTP；false 时远程请求必须 HTTPS
    allowed-origins: []            # 默认仅接受同源 Origin；非浏览器可不带 Origin
    max-request-bytes: 4194304      # 默认 4 MiB，可配置范围 1 B～64 MiB
    data-directory: ./mcp-data
    retention-days: 30
    command:
      enabled: false
      shell: /bin/bash
      max-concurrent: 4            # 每个实例的并发命令数量
      max-command-bytes: 65536
      default-timeout-seconds: 3600 # 0 不限时，最大 604800 秒
      max-output-bytes: 8388608    # 每个任务最多保存 8 MiB 输出
    process:
      enabled: false
    monitor:
      enabled: false
```

`application-id`、`instance-id` 只允许 1～64 位字母、数字、下划线、短横线。路径可配置但不能相同；与现有 Controller 重复映射时 Spring MVC 启动报错。反向代理 HTTPS 由应用配置可信代理并恢复正确的 Servlet scheme/host；模块不自行信任客户端的转发头。

默认表为 `yulinlin_mcp_record`，启动调用 ORM `initializeSchema`，不需要手工添加模块实体扫描路径；仍服从数据源的 schema 创建/校验策略。多数据源必须设置 `group`。自定义 `McpStore` 后默认 ORM 存储退出，`group` 由自定义实现自行处理。

数据 key、kind 按 `application-id` 隔离。不要随意更换应用标识，否则会读到另一份空配置；同一应用的各实例共享凭证、运行开关和任务元数据，输出文件及进程控制归所属实例。该设计不是分布式任务调度器。

## 网页管理接口

管理接口通过现有安全模块的网页登录身份和 `canManage` 校验，返回框架 `R` 包装。以下为默认路径：

| 方法 | 路径 | 请求或用途 |
| --- | --- | --- |
| GET | `/api/mcp` | enabled、available、凭证摘要列表 |
| PUT | `/api/mcp` | `{"enabled":true}`，启用或暂停 |
| POST | `/api/mcp/credentials` | `{"name":"local-ai","expiresAt":null}`，给当前用户创建凭证，可指定到期时间 |
| DELETE | `/api/mcp/credentials/{id}` | 撤销凭证 |
| GET | `/api/mcp/tasks?page=1` | 全部凭证的任务分页，每页 30 条 |
| GET | `/api/mcp/tasks/{id}` | 任务详情 |
| GET | `/api/mcp/tasks/{id}/output?offset=0` | 按字节偏移读取输出，最多 64 KiB |
| POST | `/api/mcp/tasks/{id}/cancel` | 还需 `canInvoke(user,"task_cancel")` |
| DELETE | `/api/mcp/tasks/{id}` | 删除已结束任务和本地输出 |
| GET | `/api/mcp/audit?page=1` | 调用记录，每页 50 条 |

管理写请求必须带 `X-MCP-Action: mcp-management`，并通过 Origin 和 HTTP/HTTPS 校验。固定请求头不是登录凭证。所有管理响应禁用缓存。命令功能关闭时任务接口返回 404。

创建凭证返回的 `data.token` 只显示一次，列表中不返回 token 或 digest。新安装可以先启用运行开关、创建凭证，再把 token 填到外部客户端的 `Authorization` 中。

### 凭证有效期

`expiresAt` 使用 Unix 毫秒时间戳，不是有效秒数，也不按最后活跃时间续期：

- 省略、`null` 或 `0`：永久有效；保存和响应统一为 `0`。
- 正数：绝对到期时间，当前时间达到该值（`now >= expiresAt`）即失效。
- 创建时传负数、已到期或恰好等于当前时间的时间戳：返回 HTTP 400，不保存凭证。

永久凭证：

```json
{"name":"local-ai","expiresAt":0}
```

有期限凭证（示例为 2030-01-01 00:00:00 UTC；实际使用时填写未来的到期时间）：

```json
{"name":"temporary-ai","expiresAt":1893456000000}
```

创建响应的 `data.credential.expiresAt` 和管理列表的 `credentials[].expiresAt` 返回实际到期时间，过期后 `active=false`。到期会保留凭证记录，不需要定时清理才能失效；后续外部请求返回 HTTP 401，后台命令在启动进程前再次检查。已经启动的进程不会因凭证到期被强行终止，管理员仍可通过网页登录管理和取消任务。永久凭证也可以手动撤销。

Java 调用为 `credentials.create(user, name, expiresAt)`，原 `create(user, name)` 仍创建永久凭证。数据库中旧记录缺少 `expiresAt` 或为 `null`/`0` 时仍为永久；所有正数，包括历史记录，都会执行到期校验。历史迁移注意事项见[网盘迁移约定](netdisk.md#复用原表和旧凭证)。

## 外部客户端接入

支持无需会话 ID 的 JSON Streamable HTTP：`POST /mcp`，JSON-RPC 2.0，支持 `initialize`、`notifications/initialized`、`ping`、`tools/list`、`tools/call`。支持协议版本 `2025-03-26`、`2025-06-18`、`2025-11-25`，初始化返回协商版本。

请求头：

```http
Authorization: Bearer <创建后获得的凭证>
Content-Type: application/json
Accept: application/json, text/event-stream
MCP-Protocol-Version: 2025-06-18
```

初始化例子：

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"example-client","version":"1.0"}}}
```

调用例子：

```json
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"order_query","arguments":{"id":"order-123"}}}
```

成功响应含 `result.structuredContent.data`，`result.content` 同时提供 JSON 文本。协议错误返回 JSON-RPC error，业务执行失败返回 `isError:true`；认证拒绝使用 HTTP 401/403。初始化通知返回 202。

首版不提供 OAuth、GET SSE、旧 HTTP+SSE、交互式输入、MCP 协议级 tasks 扩展；后台任务通过普通工具公开。客户端必须支持自定义 Bearer 请求头。不要将仅支持 OAuth 自动发现的客户端视为已经兼容。

传输入口使用现有 `@ApiLimit(count=180, interval="1m", by=IP)`，实际 IP 识别及限流行为沿用安全模块。业务的全局异常处理应保持正确 HTTP 状态；反向代理可再设置请求大小及速率限制。

## 可选系统工具

| 开关 | 工具 |
| --- | --- |
| `monitor.enabled` | `server_info` |
| `command.enabled` | `command_execute`、`task_list`、`task_get`、`task_output`、`task_cancel` |
| `process.enabled` | `process_list`、`process_stop` |

以上工具会通过 `tools/list` 发布完整 `inputSchema` 和 `outputSchema`：参数及返回字段均包含 `description`，包括嵌套对象/数组、单位、空值含义、状态枚举和参数默认值。AI 无需访问项目文档即可读取这些定义。自定义工具的写法见[工具字段说明](integration.md#ai-如何获取字段说明)。

| 工具 | `structuredContent.data` 的结构 |
| --- | --- |
| `server_info` | 系统状态对象，含 CPU、内存、磁盘数组、网卡数组；不可用扩展指标可省略 |
| `command_execute` / `task_get` / `task_cancel` | 任务对象，含任务 ID、状态、时间、PID、退出码、实例归属及输出截断标识 |
| `task_list` | 有权访问的任务对象数组 |
| `task_output` | `text`、`base64`、`nextOffset`、`hasMore`、`truncated` |
| `process_list` | `items` 进程数组及 `page`、`totalPages`、`total`、`sampledAt`、`truncated` |
| `process_stop` | `pid`、`state`（requested/exited）、`force` |

完整逐字段说明以运行时发布的 Schema 为准；这些定义同时用于返回结果校验。返回错误时检查 `isError` 和 `content`，不能假定存在成功结果中的 `data`。

默认命令执行器仅支持 Linux，普通业务工具可在 Windows 使用；命令通过配置的 shell `-c` 执行，工作目录必须是已有绝对路径，stdin 关闭，不提供 PTY。执行权限等于应用进程权限，不是文件沙箱，也不限制为业务网盘目录。

```json
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"command_execute","arguments":{"command":"date; uptime","cwd":"/tmp","timeoutSeconds":30,"requestId":"inspect-001"}}}
```

立即从 `result.structuredContent.data.id` 获取任务 ID，随后用 `task_get` / `task_output` 查询。HTTP 断开不会取消任务。`requestId` 在当前应用和凭证内唯一；同编号、同规范化命令/目录/超时复用已存在任务，不同参数返回冲突。唯一记录先落库，再启动进程，跨实例并发提交也不能重复派发。删除或过期清理任务记录后该编号不再具备去重能力。

状态为 `queued`、`running`、`cancelling`、`succeeded`、`failed`、`cancelled`、`timed_out`、`unknown`。重启只将本实例旧的活跃任务标为 `unknown`，不重跑、不使用旧 PID 杀进程，也不保证 OS 子进程在容器重启后仍存活。输出默认放在 `data-directory/application-id/instance-id/output`。

超过输出上限仍继续读取并丢弃多余字节，避免管道塞满。输出文件写失败也继续排空并设置 `truncated`。`offset` / `nextOffset` 是字节偏移；跨页 UTF-8 字符可能拆开，要无损合并请拼接解码后的 `base64` 字节再解码文本。

非管理用户只能通过工具查看本凭证的任务；管理用户查看他人任务仍需对应工具调用权限。列表先按全局任务页读取再按凭证过滤，页面可能不足 30 条或为空。外部读取输出、取消、删除必须访问任务所属实例；模块不会把另一实例的 PID 当作本机进程操作。

`process_stop` 必须传进程列表返回的 `stopIdentity` 作为 `identity`，以及 `confirm:true`；以精确启动时刻防止 PID 重用，保护 PID 1、当前进程及祖先进程。取消任务会尝试终止可见子进程，脱离进程树的服务需单独管理。

`server_info` 提供当前 JVM 所处服务器/容器的 OS、Java、CPU、内存、根文件系统容量和网卡地址；不提供远程宿主机数据或网卡实时带宽。进程列表部分 Linux `/proc`/`ps` 指标在其他系统可能为空。

## 保留和容量

每小时清理过期审计、已结束本实例任务及其输出；首次清理约在启动一分钟后。审计保留 `retention-days` 天，并收敛到最近 10,000 条；不是每次调用的硬上限。任务和凭证各最多接纳约 2,000 条，跨实例边界并发可能短暂超限。凭证不会因为审计保留期而自动删除或失效；撤销记录仍保留。`started` 审计意味着结果未确认，不能据此自动重试写操作。

任务命令和输出属于敏感运维数据，管理读取受 `canManage` 保护；审计默认不保存原始参数。模块的本地并发限制、输出文件和保留策略不等于分布式资源配额。
