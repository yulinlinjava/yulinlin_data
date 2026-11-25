# MCP AI 运维：命令、脚本与进程

[文档首页](../README.md) / [MCP](README.md)

业务项目引入 `mcp-ops`，打开开关并签发令牌后，外部 AI（Claude、Cursor 等）就能通过 MCP 在这台服务器上执行命令和 sh 脚本、跟进后台任务、查看和停止进程、查看服务器信息，帮你排查和运维。

**执行权限等同于应用进程的系统账号，不是沙箱。** 拿到带 `command_execute` 的令牌，就等于拿到了这台机器上这个账号的 shell。开启前请先看完[安全建议](#安全建议)。

## 能做什么

| 开关 | 工具 | 用途 |
| --- | --- | --- |
| `command.enabled` | `command_execute` | 用 shell 执行命令或多行脚本（如 `bash /opt/scripts/deploy.sh`），立即返回后台任务 |
| | `task_list`、`task_get`、`task_output`、`task_cancel` | 查看任务、读取输出、取消任务 |
| `process.enabled` | `process_list`、`process_stop` | 查看进程的 CPU、内存、启动参数；按身份停止进程 |
| `monitor.enabled` | `server_info` | 当前服务器 / 容器的系统、Java、CPU、内存、磁盘、网卡 |

这些工具都通过 `tools/list` 发布了完整的输入输出 Schema 和逐字段说明，连接时的 [instructions](configuration.md#服务说明instructions) 里也会按令牌能用的工具自动加上使用要点，AI 不需要读本文档就能用。

## 接入

前提：已按[快速接入](integration.md)完成核心模块的接入（权限策略、放行 `/mcp`、管理接口可用）。

1. 引入依赖：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>mcp-ops</artifactId>
    <version>3.0</version>
</dependency>
```

2. 打开需要的开关。引入模块后仍需显式打开，默认全部关闭：

```yaml
yulinlin:
  mcp:
    enabled: true
    application-id: order-service
    instance-id: server-01          # 每台服务器必须不同，审计和任务按它区分机器
    command:
      enabled: true
      shell: /bin/bash
      default-timeout-seconds: 3600 # 0 不限时，最大 604800
      max-concurrent: 4             # 本机同时运行的命令数
      max-output-bytes: 8388608     # 每个任务最多保存 8 MiB 输出
      audit-command: true           # 审计记录命令原文（默认开）
    process:
      enabled: true
    monitor:
      enabled: true
    data-directory: ./mcp-data      # 命令输出文件目录
```

3. 在 `McpAccessPolicy.canInvoke` 中给运维账号开放这些工具，签发一个运维专用令牌，建议同时设置 IP 白名单：

```json
POST /api/mcp/credentials
{"name": "运维助手", "scope": ["command_execute", "task_*", "process_*", "server_info"], "allowedIps": ["10.0.0.0/8"]}
```

令牌范围写法见[令牌管理](tokens.md#范围scope)。`task_*` 是前缀通配，等价于 `task_list`、`task_get`、`task_output`、`task_cancel`。

4. 在 AI 客户端里配置这台服务器：地址 `https://<这台服务器>/mcp`，请求头 `Authorization: Bearer ylmcp_...`。

## 多台服务器

**每台服务器单独配置一个 MCP 连接，直连机器，不要走负载均衡。**

- 命令在接到请求的那台机器上执行。连负载均衡地址时，AI 每次调用可能落到不同机器，它不知道自己在操作哪台。
- 任务输出只保存在执行它的机器上，`task_get` / `task_output` / `task_cancel` 必须打到同一台。

在 AI 客户端里按机器命名，例如 `order-01`、`order-02`，各自指向机器的 IP 或内网域名。

令牌有两种管理方式：

| 方式 | 配置 | 特点 |
| --- | --- | --- |
| 各自独立（默认） | MCP 数据存在每台服务器自己的数据源里 | 开箱即用；但每台都要单独签发和撤销令牌，审计分散在各台 |
| 共享一个 MCP 小库（推荐，多台时） | 每台服务器另配一个数据源指向同一个小库，`yulinlin.mcp.group` 指向它，所有服务器用**同一个** `application-id`、**不同的** `instance-id` | 签发一次所有机器可用，撤销一次全部失效（其他实例最多延迟 `token.cache-seconds`）；审计集中，并带 `instance` 字段区分机器。业务库不受影响；这个小库不可用时各机器的 MCP 都不可用 |

```yaml
# 共享 MCP 小库：每台服务器的配置（业务数据源保持原样）
yulinlin:
  mcp:
    application-id: ops          # 所有服务器相同，共享令牌、开关和审计
    instance-id: order-01        # 每台不同
    group: mcp                   # MCP 数据写到名为 mcp 的数据源
# 再声明一个名为 mcp 的数据源，所有服务器指向同一个 MySQL / PostgreSQL 库，见数据源文档
```

数据源的声明方式见[多数据源与默认组](../datasources/routing.md)。

## 后台命令

```json
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"command_execute","arguments":{"command":"date; uptime","cwd":"/tmp","timeoutSeconds":30,"requestId":"inspect-001"}}}
```

- 默认执行器只支持 Linux，通过配置的 shell `-c` 执行，多行脚本、管道、`&&` 都可以用。工作目录必须是已存在的绝对路径，stdin 关闭，没有 PTY：交互式命令（`top`、`vim`、要求输入密码的 `sudo`）用不了，请用非交互写法，例如 `top -b -n 1`、`sudo -n`。
- 调用会立即返回任务，从 `data.id` 获取任务 ID，再用 `task_get` / `task_output` 跟进。HTTP 断开不会取消任务。不需要加 `&` 或 `nohup`；长期运行的服务应交给 systemd。
- `requestId` 在同一应用、同一令牌内唯一。相同编号且参数相同会返回已有任务，参数不同会返回 409 冲突。任务记录先落库再启动进程，多个实例并发提交也不会重复执行。
- 任务提交时和进程启动前都会重新检查令牌和权限。令牌在这期间被撤销、过期或缩小范围，任务会失败，不会启动进程。已经在运行的进程不会因为令牌失效被终止。
- 状态：`queued`、`running`、`cancelling`、`succeeded`、`failed`、`cancelled`、`timed_out`、`unknown`。应用重启后，本实例原来活跃的任务会被标为 `unknown`，不会重跑，也不会按旧 PID 杀进程。
- 输出保存在 `data-directory/application-id/instance-id/output`。超过上限后会继续读取并丢弃，同时标记 `truncated`。`offset` 是字节偏移，要无损拼接请使用 `base64`。
- 非管理员只能看到自己令牌提交的任务。读取输出、取消、删除必须访问任务所在的实例。

| 工具 | `structuredContent.data` |
| --- | --- |
| `command_execute` / `task_get` / `task_cancel` | 任务对象：id、状态、时间、PID、退出码、实例、输出是否截断 |
| `task_list` | 有权访问的任务数组 |
| `task_output` | `text`、`base64`、`nextOffset`、`hasMore`、`truncated` |
| `process_list` | `items` 进程数组，以及 `page`、`totalPages`、`total`、`sampledAt`、`truncated` |
| `process_stop` | `pid`、`state`（requested / exited）、`force` |
| `server_info` | CPU、内存、磁盘数组、网卡数组。只反映当前 JVM 所在的服务器或容器，没有实时带宽 |

## 进程

- `process_stop` 必须原样传入 `process_list` 返回的 `stopIdentity` 作为 `identity`，并且 `confirm: true`。这样可以防止 PID 被复用后误杀其他进程。PID 1、当前进程及其祖先进程受保护。
- 取消任务会尝试终止可见的子进程；脱离进程树的服务需要单独处理。systemd / Docker 管理的服务会被自动拉起，应使用 `systemctl` / `docker` 命令停止。

## 审计

每次调用都会写入调用审计（`/api/mcp/audit?type=call`），运维工具额外记录：

| 工具 | 记录的字段 |
| --- | --- |
| 全部运维工具 | `instance`（`instance-id`，区分是哪台机器） |
| `command_execute` | `command`（命令原文，最多 512 字符）、`cwd`、`timeoutSeconds`、`requestId` |
| `task_get` / `task_output` / `task_cancel` | `taskId` |
| `process_stop` | `pid`、`force` |

命令里不要写明文密码，改用环境变量、配置文件或凭证管理工具。确实不能让命令原文进入审计时，配置 `command.audit-command: false`，审计里只记录命令长度。

业务注册的 `McpAuditTargetProvider` 与这些字段互相追加，不会把它们替换掉，见[扩展点](extension.md#审计mcpauditservice)。

## 管理接口

开启命令工具后，`mcp-ops` 在管理接口前缀下注册任务接口（网页登录，返回 `R` 包装，写请求需要 `X-MCP-Action: mcp-management`）：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/mcp/tasks?page=1` | canManage | 全部任务，每页 30 条 |
| GET | `/api/mcp/tasks/{id}` | canManage | 任务详情 |
| GET | `/api/mcp/tasks/{id}/output?offset=0` | canManage | 按字节偏移读取输出，每次最多 64 KiB |
| POST | `/api/mcp/tasks/{id}/cancel` | canManage + `canInvoke(user,"task_cancel")` | 取消任务 |
| DELETE | `/api/mcp/tasks/{id}` | canManage | 删除已结束的任务及其输出 |

没开命令工具时这些接口不存在（404）。

## 安全建议

- **用专门的系统账号运行应用**，不要用 root。需要提权的操作在 sudoers 里按命令白名单放开，并加 `NOPASSWD`，AI 用 `sudo -n` 调用。
- **令牌单独签发、范围最小**：运维令牌和业务令牌分开；只需要看日志、看状态时，不要给 `command_execute`，只给 `server_info`、`process_list`、`task_*`。
- **加 IP 白名单**（`allowedIps`），并设置过期时间，定期轮换。
- **只在需要的机器上打开开关**：引入了 `mcp-ops` 但没打开 `command.enabled` 的机器不会注册命令工具。
- 让 AI 执行变更前先说明要做什么并等你确认；工具说明里已经要求变更类命令带 `requestId` 防止重复执行。

## 常见问题

**AI 说找不到 `command_execute`**：检查是否引入了 `mcp-ops`、是否打开 `command.enabled`、令牌 scope 和 `canInvoke` 是否包含它。

**`task_get` 说任务不存在**：任务只能在执行它的机器上查看，确认 AI 连接的是同一台机器，没有经过负载均衡。

**命令一直卡住**：多半是在等交互输入（如 `sudo` 要密码、`apt` 等确认）。改用非交互参数（`sudo -n`、`apt-get -y`），并设置 `timeoutSeconds`。
