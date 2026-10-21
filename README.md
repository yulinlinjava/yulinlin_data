# yulinlin-data

面向 Spring Boot 的 ORM 与 Java 工具库。当前版本：JDK 25、Spring Boot 3.5.16、制品 3.0。

用同一套实体、Wrapper、Repository 和 Session 模型，完成多数据源 CRUD、关联加载、查询缓存、全文检索、批量与延迟写入。不是 MyBatis-Plus、JPA 或 Spring Data；请使用本项目文档里的包名和 API。

## 从这里开始

**[文档首页：按任务找答案](doc/README.md)** · [模块与依赖](doc/start/modules.md) · [MySQL 接入](doc/datasources/mysql.md) · [第一个 CRUD](doc/orm/crud.md) · [完整业务案例](doc/practice/crud-example.md)

业务依赖来自团队制品仓库或同一份源码的发布产物，不假设已发布 Maven Central。不要把 admin 示例模块作为业务依赖。

## 核心能力

- **统一数据访问**：MySQL、PostgreSQL、SQLite、H2、Elasticsearch；按 group 路由，保留参数化自定义 SQL。
- **减少业务代码**：PagePo 查询条件对象、通用 Service、Repository、JoinQuery 关联、懒加载与批量预加载。
- **事务内自动更新**：通过 `.autoUpdate()` 或 `@JoinSync` 跟踪 setter，在路由事务提交前写回。
- **可选缓存**：Caffeine、Ehcache、Redis 可共存；统一 CacheMode、TTL、多数据源 Key 与条件分区失效。
- **高频写入**：受控多连接批量、`.queue()` 延迟执行、EntityCounter 聚合自增和按维度聚合插入。
- **搜索与结构初始化**：显式自动建表/索引，PostgreSQL 与 ES 的 `match()`、`highlight()`。
- **配套能力**：轻量数据同步、慢操作监控、接口加密限流、无状态 JWT、有状态 Sa-Token 或现有 Spring Security 接入；统一 loginByUsername 账号密码登录，业务认证可替换。

技术选型看[功能、开发成本与评分说明](doc/start/comparison.md)。比较的是交付同一业务能力需要的工作量，不是运行性能或通用排名。

## 按主题阅读

每个主题只有导航；每篇小专题完成一个使用任务，不需要通读整个框架。

| 大主题 | 常用入口 |
| --- | --- |
| [快速开始](doc/start/README.md) | [模块选择](doc/start/modules.md)、[技术选型](doc/start/comparison.md) |
| [数据源](doc/datasources/README.md) | MySQL、PostgreSQL、SQLite、H2、ES、[多数据源](doc/datasources/routing.md)、[自动建表与索引](doc/datasources/schema.md) |
| [ORM 开发](doc/orm/README.md) | [CRUD](doc/orm/crud.md)、[查询条件](doc/orm/query.md)、[自定义 SQL](doc/orm/raw-sql.md)、[统计](doc/orm/analytics.md)、[Repository](doc/orm/repository.md) |
| [关联与事务](doc/relations/README.md) | [关联查询](doc/relations/query.md)、[懒加载](doc/relations/lazy.md)、[自动更新](doc/relations/auto-update.md)、[事务](doc/relations/transactions.md) |
| [批量与同步](doc/write-sync/README.md) | [批量](doc/write-sync/batch.md)、[延迟队列](doc/write-sync/queue.md)、[EntityCounter](doc/write-sync/counter.md)、增量/全量同步 |
| [缓存](doc/cache/README.md) | [ORM 缓存](doc/cache/query.md)、[失效规则](doc/cache/invalidation.md)、Caffeine/Ehcache/Redis、[CacheClient](doc/cache/client.md) |
| [接口安全](doc/security/README.md) | [JWT](doc/security/jwt.md)、[Sa-Token](doc/security/sa-token.md)、[Spring Security](doc/security/spring.md)、[权限](doc/security/authorization.md)、[加密](doc/security/crypto.md)、[限流](doc/security/rate-limit.md) |
| [常用工具](doc/tools/README.md) | [HTTP](doc/tools/http.md)、[反射与克隆](doc/tools/reflection.md)、JSON、日期、线程与调度 |
| [项目实践](doc/practice/README.md) | [公共模型](doc/practice/models.md)、[通用 Service](doc/practice/service.md)、[完整 CRUD](doc/practice/crud-example.md)、[RBAC 登录](doc/practice/rbac.md) |
| [监控与排障](doc/operations/README.md) | [监控页面](doc/operations/monitor.md)、[SQL/事务日志](doc/operations/sql-log.md)、[SQLite/H2 性能报告](doc/operations/local-benchmark.md) |
| [内部维护](doc/internal/README.md) | [组件边界](doc/internal/architecture.md)、[执行链](doc/internal/execution.md)、[事务资源](doc/internal/transactions.md)、新数据源接入 |

## 使用边界

- 多库/多连接事务是本地协调，不是 XA/2PC；写操作先确认主键与条件，框架不默认阻止全表操作。
- 自动 Schema 需显式声明结构拥有者；改列类型需人工确认并开启配置，不负责字段改名和数据搬迁。
- data-sync 是轻量同步，不是 CDC。人工全量要求业务停写并排空队列；内存增量队列不保证崩溃后可靠交付。
- JWT 是纯无状态：注销不撤销已签发 Token；需要服务端会话管理时选择 Sa-Token 或自定义实现。
- 示例不是所有数据库服务端组合的集成验证。性能数据仅适用于报告写明的环境和模型。

## 给 AI 与维护者

能读取仓库：从 [llms.txt](llms.txt) 按任务选择小专题，不要一次加载全部正文。

只能上传一个附件：使用自动生成的 [AI 接入指南](doc/AI接入指南.md)。内部框架维护另读[核心组件](doc/internal/architecture.md)和[维护约定](doc/internal/maintenance.md)。

小专题是唯一正文维护源，主题页只做导航，AI 指南自动派生。默认不执行编译、打包、测试、Spring YAML 属性绑定测试或 `git diff --check`，除非用户在当前任务明确要求。

许可证：[LICENSE](LICENSE)。
