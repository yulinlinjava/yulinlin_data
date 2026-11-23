# 现有网盘的 MCP 迁移约定

[文档首页](../README.md) / [MCP](README.md)

通用模块已经提供扩展入口，但引入依赖不会自动改写另一个仓库的代码、数据库或输出目录。现有 `D:/java/ftp` 网盘不能同时保留旧 `/mcp` Controller 又启用新 Controller。以下是迁移时需要完成的适配，不表示网盘实例已经切换或完成运行验证。

## 保留现有权限与业务服务

网盘提供 `McpAccessPolicy`：三个判断均要求现有超级管理员角色。不要直接使用“已登录”作为 MCP 权限。`McpIdentityProvider` 将旧凭证中的登录 ID 转换成稳定账号 key，再从当前账号记录恢复用户；必须检查账号启用状态和超级管理员角色。密码变化不应丢失长期凭证关联。

文件、上传、分享工具通过 `McpToolProvider` 注册，回调调用原服务，保留授权目录、内部目录保护、版本校验、2 MiB 分片和原指纹算法。模块不复制这套文件实现。

需要保留的业务工具名为：`file_entries`、`file_list`、`file_read_text`、`file_write_text`、`file_create_text`、`file_tail`、`file_download`、`folder_create`、`folder_ensure`、`file_rename`、`file_move`、`file_delete`、`archive_extract`、`upload_status`、`upload_chunk`、`upload_merge`、`upload_cancel`、`share_create`、`share_list`、`share_revoke`。

通用命令、任务、进程工具沿用原名称与 `structuredContent.data`。若旧 `server_info` 依赖网盘实时网络采样的完整结果，关闭通用 `monitor.enabled`，由网盘 Provider 注册同名工具复用原返回结构；通用版本只提供基础系统信息。重名工具会启动报错。

## 复用原表和旧凭证

提供 `McpStore`，继续访问原 `netdisk_mcp_record` 表及原 key/kind，不增加新应用前缀。这个适配只供该网盘应用使用，不能拿同一命名空间给多个应用共享。默认 `OrmMcpStore` 自动退出，避免创建一套空的凭证库。

保留 `credential-<SHA-256>` key 和原凭证字段。模块接受原 token 的摘要，不要求 token 前缀为 `ylmcp_`；已有 `ndmcp_` token 无需重新签发，但必须通过当前有效期和账号校验。`sessionId` 仍作为历史元数据，`expiresAt` 则统一按新规则处理：缺失、`null`、`0` 为永久，正数为实际到期时间。撤销状态原样保留，不得重置。新 `create` 方法必须通过表的唯一索引原子插入，不能转调旧的覆盖保存方法。

旧网盘曾忽略正数 `expiresAt`；升级前需要核对这类数据。只有明确确认原本为永久授权的旧记录，才由业务迁移将该字段设为 `0`，保留其摘要和撤销状态；模块不会自动清除正数到期时间，避免把真正已过期的凭证重新激活。

任务 JSON 新增可空的 `instanceId`、`runId`；旧记录缺省 `instanceId` 只归默认 `local` 实例。迁移时保持 `instance-id: local`，不要将旧任务任意归属到多台机器。账号映射和原表适配必须先验证，再开放外部请求。

## 复用输出、请求头和过滤器

提供 `McpTaskOutputResolver` 指向旧内部目录，回调只接收校验过的 32 位十六进制任务 ID：

```java
@Bean
McpTaskOutputResolver mcpOutputs(StoragePathResolver paths) {
    return id -> paths.internal().resolve("mcp-output").resolve(id + ".log");
}
```

管理页面保持 `/api/mcp` 和框架 `R` 包装。为了保留旧前端的 `X-Netdisk-Action: mcp-management`，覆盖 `McpManagementGuard`，先调用 `McpHttpGuard.check(request)`，再验证旧请求头。不能只校验固定请求头而忽略 Origin。

迁移 `CookieRequestFilter` 时，让 `/mcp` 保留原始 Bearer 请求头，由通用传输入口认证；不要再创建旧内部登录桥接，也不要把该头用于 `/api/**` 网页入口。清理旧 `McpLoginAuthenticator` 前同时处理 `AuthService` 中的旧认证类型判断，确保历史桥接 token 不能访问网页。

只启用一套 Controller、清理器和任务执行器。旧页面可继续使用，但必须验证创建、暂停、撤销、任务输出、取消、审计等交互。网盘原有 Shell/TOTP 启用门槛若仍需保留，应在业务权限/管理适配中明确实现，通用模块不写死这一门槛。

## 切换和回退

先备份原数据库和内部输出目录，停止接收新命令，等待可等待的任务完成；保留运行开关和凭证摘要，部署适配后核对已撤销凭证拒绝、未撤销凭证有效、旧输出可读及文件权限边界。任务状态 `unknown` 不表示进程已停止。

复用原表时无需搬迁凭证数据，但通用任务 JSON 多出字段；回退旧程序前，应确认旧 ObjectMapper 忽略未知字段，或在停服状态下只移除新增任务元数据字段。不要恢复旧数据库快照覆盖新撤销记录，否则会重新激活已撤销凭证。旧 token 校验、表适配、网页过滤器和文件工具集成需在网盘仓库单独验证。
