# 文档中心

[返回项目首页](../README.md)

按“**大主题 → 小专题 → 使用案例与边界**”阅读。主题页只做导航，具体 API、配置和案例在独立小专题中维护。

## 我现在要做什么

| 任务 | 直接阅读 |
| --- | --- |
| 第一次接入项目 | [模块选择](start/modules.md) → [MySQL](datasources/mysql.md) / [SQLite](datasources/sqlite.md) / [H2](datasources/h2.md) → [最小实体](orm/entity.md) → [CRUD](orm/crud.md) |
| 写业务页面查询 | [PagePo 与公共模型](practice/models.md) → [查询条件与分页](orm/query.md) |
| 新增、修改、删除数据 | [CRUD 完整用法](orm/crud.md)；分层写法看[完整业务案例](practice/crud-example.md) |
| 写原生 SQL、统计报表或全文搜索 | [自定义 SQL](orm/raw-sql.md) · [统计分析](orm/analytics.md) · [全文检索与高亮](orm/search.md) |
| 接入多个数据库、自动创建表 | [多数据源与默认组](datasources/routing.md) · [自动建表与索引](datasources/schema.md) |
| 查询关联数据、懒加载、自动更新 | [关联查询](relations/query.md) → [懒加载与预加载](relations/lazy.md) → [事务](relations/transactions.md) → [自动更新](relations/auto-update.md) |
| 提升查询效率 | [启用 ORM 缓存](cache/query.md) → [选择缓存与 TTL](cache/config.md) → [Key 和失效规则](cache/invalidation.md) |
| 点击量聚合、延迟批量落库 | [EntityCounter](write-sync/counter.md) · [延迟写队列](write-sync/queue.md) · [多连接批量](write-sync/batch.md) |
| 数据源同步与历史补齐 | [增量同步](write-sync/incremental.md) → [人工全量与续传](write-sync/full.md) |
| 接入登录、权限或接口加密 | [模块选型](security/modules.md) → [JWT](security/jwt.md) / [Sa-Token](security/sa-token.md)；按需读[权限](security/authorization.md)、[加密](security/crypto.md)、[限流](security/rate-limit.md) |
| 替换 Token 或接入外部安全框架 | [Token 编解码](security/token-codec.md) · [自定义管理器与拦截器](security/extension.md) |
| 查工具类使用方式 | [HTTP](tools/http.md) · [反射复制与深克隆](tools/reflection.md) · [lang 类型目录](tools/catalog.md) |
| 找最佳实践案例 | [完整 CRUD](practice/crud-example.md) · [Admin RBAC 与 JWT](practice/rbac.md) · [开发规范](practice/conventions.md) |
| 排查慢 SQL、事务或选本地库 | [日志](operations/sql-log.md) · [监控页面](operations/monitor.md) · [SQLite/H2 性能报告](operations/local-benchmark.md) |
| 修改框架内部实现 | [组件职责](internal/architecture.md) → [执行链](internal/execution.md) → [维护约定](internal/maintenance.md) |

## 全部大主题

| 大主题 | 内容 |
| --- | --- |
| [快速开始](start/README.md) | 模块、版本、快速接入与技术选型 |
| [数据源](datasources/README.md) | 各数据库配置、多数据源、结构初始化 |
| [ORM 开发](orm/README.md) | 实体、CRUD、查询、SQL、统计、Repository |
| [关联与事务](relations/README.md) | JoinQuery、懒加载、预加载、自动更新与事务 |
| [批量与同步](write-sync/README.md) | 批量、队列、计数聚合、全量与增量同步 |
| [缓存](cache/README.md) | 默认缓存、实现配置、查询缓存、业务 API 与失效 |
| [接口安全](security/README.md) | 登录、权限、加密、限流、自定义扩展 |
| [常用工具](tools/README.md) | HTTP、反射、JSON、日期、并发、事件等 |
| [项目实践](practice/README.md) | 公共模型、Service、CRUD 与 RBAC 案例 |
| [监控与排障](operations/README.md) | 慢操作页面、日志、问题排查和历史性能报告 |
| [内部维护](internal/README.md) | 组件职责、执行流程、事务资源与数据源扩展 |

## 给 AI 阅读

- 有仓库读取能力：从 [llms.txt](../llms.txt) 定位任务，只加载相关小专题及其前置链接。
- 只能提供一个附件：[AI 接入指南](AI接入指南.md) 自动汇总业务专题；监控、性能报告、内部实现按需另外提供。
- 不要同时上传全集和相同小专题。正文以小专题为准，AI 合集不手工编辑。

## 维护规则

小专题是唯一正文源；旧的十篇大专题已拆分移除，不保留平行版本。文件清单与 AI 导出范围由 [topics.json](topics.json) 管理，新增页面同时更新所属主题导航与清单。

文档导出使用 `./doc/build-ai-docs.ps1`，只生成文档并检查相对链接、章节锚点和代码围栏，不编译 Java、不执行测试。详见[维护约定](internal/maintenance.md)。

历史性能和测试结果保留原日期与环境，不代表本次文档迁移重新执行过验证。目录内的 admin.sql、code.sql、quartz.sql 是历史脚本，不是当前迁移方案，不自动执行。
