# MCP 接入

[文档首页](../README.md)

`com.yulinlin:mcp:3.0` 让外部 AI 客户端（Claude、Cursor 等）通过 MCP 协议调用业务应用里的工具。模块与业务应用共用 Spring MVC 端口，复用应用已有的登录和用户体系，不包含页面、文件服务或独立账号体系。

## 按任务阅读

| 要做的事 | 阅读 |
| --- | --- |
| 第一次把 MCP 接入一个 Spring Boot 应用 | [快速接入](integration.md)（必读，按步骤做） |
| 把业务方法暴露成 AI 可调用的工具 | [业务工具](tools.md) |
| 让 AI 读取项目数据（JDBC / ES）、修改数据、在服务端做复杂分析 | [数据工具](data.md) |
| 签发、修改、轮换、撤销令牌，限制令牌能做什么 | [令牌管理](tokens.md) |
| 查配置项、管理接口、客户端协议、命令和进程工具 | [配置与系统工具](configuration.md) |
| 替换存储、审计、认证方式等默认实现 | [扩展点](extension.md) |

## 一次请求是怎么走的

```
外部 AI 客户端
  │  POST /mcp   Authorization: Bearer ylmcp_{keyId}_{secret}
  ▼
McpTransport ── McpHttpGuard（Origin / HTTPS）
  │
  ▼
McpAuthentication ── 依次调用 McpAuthenticator（默认 TokenAuthenticator）
  │                   → McpPrincipal（凭证 id、所属账号、scope、attributes）
  │                   → McpIdentityProvider.load(账号) → McpAccessPolicy.canUse
  ▼
McpToolRegistry ── 令牌 scope ∩ McpAccessPolicy.canInvoke → 校验 inputSchema
  │
  ▼
审计开始 → 以所属账号身份（SecurityContext）执行工具 → 审计结束 → 校验 outputSchema
```

## 给 AI 的硬性规则

接入时必须遵守：

- **必须**由业务提供 `McpAccessPolicy` Bean。没有它模块会启动失败，框架不会默认放行。
- **必须**在工具内部继续做数据权限判断，例如订单归属、租户、目录范围。`canInvoke` 只决定能不能调用这个工具。
- **只使用**本目录文档和源码里确认存在的类、方法和配置，不要凭名称臆造 API。
- **不要**把 `/api/mcp/**` 管理接口改成公开访问，也不要对它关闭 CSRF。只有 `/mcp` 是 Bearer 入口。
- **不要**在工具返回值外面再包一层 `R`，框架会自动把返回值放进 `structuredContent.data`。
- **不要**在日志、审计、异常信息里输出令牌明文。
- 默认只修改源码和文档，**不执行**编译、打包和测试，除非任务明确要求。
