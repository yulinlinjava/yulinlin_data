# MCP 令牌管理

[文档首页](../README.md) / [MCP](README.md)

外部 AI 客户端通过令牌调用 MCP。模块提供签发、修改、轮换、禁用、启用、撤销、删除，并支持工具范围、业务属性、IP 白名单和限流。认证方式可扩展，静态令牌只是默认实现。

## 令牌格式与存储

```
ylmcp_{keyId}_{secret}
       │       └─ 32 字节随机数（base64url，43 位），只保存 SHA-256
       └─ 12 位小写字母数字，明文保存，唯一索引
```

认证时先按 `keyId` 查找，再对 `secret` 计算 SHA-256，用常量时间比较。`keyId` 也用于界面展示，列表中显示为 `ylmcp_{keyId}_****`，令牌泄露时可以据此定位。明文只在签发和轮换的响应里出现一次。

默认表为 `yulinlin_mcp_token`，启动时由 ORM 自动建表，数据按 `application-id` 隔离。旧版保存在 `yulinlin_mcp_record` 中的凭证（`kind=credential`）不再读取，升级后需要重新签发。旧记录可以手动删除。

## 状态

| 状态 | 说明 | 可执行操作 |
| --- | --- | --- |
| `ACTIVE` | 正常可用 | 修改、轮换、禁用、撤销 |
| `DISABLED` | 暂停，认证返回 401 | 启用、修改、轮换、撤销 |
| `EXPIRED` | 计算状态：`ACTIVE` 且已过期，不入库 | 修改 `expiresAt` 即可续期，也可撤销、删除 |
| `REVOKED` | 已撤销，不可恢复 | 只能删除 |

只有 `REVOKED` 和 `EXPIRED` 的令牌可以物理删除。

## 配置

```yaml
yulinlin:
  mcp:
    token:
      max-ttl-days: 0            # 非管理员签发/续期的最长有效天数；0 不限（允许永久）
      max-per-owner: 50          # 每个账号未撤销令牌上限；0 不限
      cache-seconds: 10          # 令牌全量内存缓存的刷新间隔；0 关闭，最大 300
      max-rotate-grace-seconds: 604800  # 轮换宽限期上限，最大 30 天
```

令牌数量通常很少，模块会把整张令牌表加载到内存，认证时不查库，不存在的 `keyId` 也不会打到数据库。每隔 `cache-seconds` 秒重新加载一次，本实例的修改会让下一次认证立即重新加载。多实例部署时，其他实例最多延迟 `cache-seconds` 才能看到禁用或撤销。需要立即生效时可以设为 0，代价是每次请求都按 `keyId` 查库。

## 权限规则

| 角色 | 能做什么 |
| --- | --- |
| 已登录用户 | 查看、签发、修改、轮换、禁用、启用、撤销、删除**自己的**令牌 |
| `canManage` | 管理所有人的令牌；为其他账号签发；设置 `attributes`；不受 `max-ttl-days` 限制 |

- 令牌所属账号必须通过 `canUse`，签发才会成功。
- 范围只能收窄：签发和修改时，范围里写明的工具名必须存在，并且所属账号要有 `canInvoke` 权限。每次调用时还会再次校验账号当前权限。
- 访问不属于自己的令牌时返回 404，不暴露令牌是否存在。

## 范围（scope）

| 写法 | 含义 |
| --- | --- |
| `*` 或省略 | 继承所属账号的全部工具权限 |
| `echo` | 精确工具名，与 `GET /api/mcp/tools` 返回的 `name` 完全一致 |
| `file.*` | 工具名前缀通配 |
| `@readonly` | 所有 `readOnly=true` 的工具 |
| `resource:...`、`prompt:...` | 预留，当前不参与判断 |

最终权限为 **所属账号当前权限 ∩ 范围**。`*` 不能和其他项混用，范围不能为空。`tools/list` 和 `tools/call` 都会按范围过滤，后台命令在提交和启动进程前也会重新检查。

**保存和返回的值就是上表的规范写法**，传什么读回什么。输入时也可以写带前缀的 `tool:echo`、`tool:file.*`，保存时会去掉 `tool:` 前缀，重复项会被去重。前端可以直接拿 `scope` 和 `GET /api/mcp/tools` 的 `name` 比较，不需要做任何转换：

- `["*"]`：全部工具
- 每一项都能在工具目录里找到：可以用勾选框编辑
- 包含 `*` 结尾的前缀、`@readonly` 或 `resource:` / `prompt:`：属于高级写法，勾选框无法完整表达

## 业务属性（attributes）

`attributes` 是令牌上的键值对。框架只负责存储和透传，不解释其含义，由业务工具自行读取并限制数据范围：

```java
@McpTool(name = "file.list", description = "列出目录", readOnly = true)
public List<FileItem> list(ListRequest request, McpCallContext context) {
    String root = context.attribute("file.root");   // 例如 /项目A
    return files.list(root, request.path());
}
```

也可以在调用链深处通过 `McpCallContext.current()` 获取上下文。只有 `canManage` 可以设置或修改 `attributes`，防止普通用户给自己放宽数据范围。键名限 1–64 位字母、数字或 `_.:-`，值最多 1024 字符，最多 32 项。

## IP 白名单与限流

- `allowedIps`：单个 IP 或 CIDR，支持 IPv4 和 IPv6，最多 32 项。空列表表示不限制。IP 取自 `request.getRemoteAddr()`，部署在反向代理后面时需要配置 `server.forward-headers-strategy`。
- `rateLimit`：每分钟最多调用次数，0 表示不限，按单实例计数。超出时返回 429。

## 轮换

轮换会重新生成 `secret`，`keyId` 保持不变。`graceSeconds` 内旧令牌仍然可用，方便客户端平滑切换；传 0 表示旧令牌立即失效。撤销会让宽限期内的旧令牌一起失效。

## 管理接口

默认前缀为 `/api/mcp`，使用网页登录身份，返回 `R` 包装。写请求必须带 `X-MCP-Action: mcp-management`，并通过 Origin 和 HTTPS 校验。令牌相关响应都设置了 `Cache-Control: no-store`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/mcp` | `enabled`、`manager`、`canUse` |
| PUT | `/api/mcp` | `{"enabled":true}` 启用或暂停，要求 `canManage` |
| GET | `/api/mcp/tools` | 当前账号有权限的工具（`name`、`description`、`readOnly`），供选择范围 |
| GET | `/api/mcp/credentials?owner=&status=&keyword=&page=1&size=20` | 分页列表。`keyword` 模糊匹配名称或精确匹配 keyId；非管理员只能查自己的 |
| GET | `/api/mcp/credentials/{id}` | 详情 |
| POST | `/api/mcp/credentials` | 签发，返回 `{credential, token}`，`token` 只出现这一次 |
| PATCH | `/api/mcp/credentials/{id}` | 修改；字段为 null 表示不改 |
| POST | `/api/mcp/credentials/{id}/rotate` | `{"graceSeconds":3600}`，返回新 `token` |
| POST | `/api/mcp/credentials/{id}/disable` | 禁用 |
| POST | `/api/mcp/credentials/{id}/enable` | 启用 |
| POST | `/api/mcp/credentials/{id}/revoke` | 撤销，不可恢复 |
| DELETE | `/api/mcp/credentials/{id}` | 物理删除，只限已撤销或已过期 |
| GET | `/api/mcp/credentials/{id}/usage` | 该令牌最近 50 条工具调用（扫描最近 1000 条审计） |
| GET | `/api/mcp/audit?type=manage&page=1` | 管理审计；`type=call`（默认）为工具调用审计，要求 `canManage` |

签发：

```json
POST /api/mcp/credentials
{
  "name": "数据分析",
  "remark": "给 BI 助手用",
  "ownerId": null,
  "expiresAt": 1798732800000,
  "scope": ["@readonly", "order_query"],
  "attributes": {"tenant": "t_001"},
  "allowedIps": ["10.0.0.0/8"],
  "rateLimit": 120
}
```

`ownerId` 为空表示自己，只有管理员可以填写其他账号。`expiresAt` 为 null 或 0 表示永久，正数为毫秒时间戳，必须晚于当前时间。

修改：

```json
PATCH /api/mcp/credentials/{id}
{"expiresAt": 0, "allowedIps": []}
```

`expiresAt: 0` 改为永久，`allowedIps: []` 取消 IP 限制，`remark: ""` 清空备注。至少要提供一个字段。内容没有变化的修改不会产生事件和审计。

返回的令牌信息（`McpTokenView`）不包含任何 hash：

```json
{
  "id": "...", "keyId": "k3x9...", "display": "ylmcp_k3x9..._****",
  "name": "数据分析", "remark": "", "ownerId": "u1", "issuerId": "admin",
  "status": "ACTIVE", "expiresAt": 0, "scope": ["@readonly"],
  "attributes": {}, "allowedIps": [], "rateLimit": 0,
  "createdAt": 0, "updatedAt": 0, "rotatedAt": 0, "previousExpiresAt": 0,
  "lastUsedAt": 0, "lastUsedIp": ""
}
```

## 事件与管理审计

每次签发、修改、轮换、禁用、启用、撤销、删除都会发布 Spring 事件 `McpTokenEvent`，内容包括动作、操作后的令牌、操作人、IP、变化字段（`{字段: {from, to}}`）和时间。事件里不包含 token 或 hash。

默认的 `McpTokenAuditListener` 会把事件写入管理审计，可以通过 `/api/mcp/audit?type=manage` 查看，保留周期与调用审计相同。业务也可以自己监听这个事件，用来发通知、同步到其他系统等：

```java
@EventListener
public void onToken(McpTokenEvent event) {
    if (event.action() == McpTokenEvent.Action.ISSUE) notify(event.token().ownerId(), event.token().name());
}
```

事件处理失败只记录日志，不会回滚已完成的操作。

## Java 调用

```java
@Autowired McpTokenService tokens;

var actor = new McpTokenService.Actor(SecurityUtil.requireUser(), request.getRemoteAddr());
var issued = tokens.issue(actor, new McpTokenService.IssueRequest(
        "nightly-job", null, null, null, List.of("@readonly"), null, null, null));
String token = issued.token();          // 只返回这一次
tokens.rotate(actor, issued.credential().id(), 3600);
tokens.revoke(actor, issued.credential().id());
```

`Actor` 必须来自服务端认证，不能直接使用客户端提交的用户 ID。

## 扩展

- 换存储：实现 `McpTokenStore`，见[令牌存储](extension.md#令牌存储mcptokenstore)。
- 增加 OAuth、SSO、网关签名等认证方式：实现 `McpAuthenticator`，见[认证方式](extension.md#认证方式mcpauthenticator)。
- 签发、撤销时发通知或同步：监听 `McpTokenEvent`，见[监听令牌事件](extension.md#监听令牌事件)。
