# yulinlin-data

面向 Spring Boot 的自定义 ORM 和 Java 工具库。当前版本：JDK 25、Spring Boot 3.5.16、制品 3.0。不是 MyBatis-Plus、JPA 或 Spring Data。

## 它解决什么问题

很多 Spring Boot 项目同时需要“标准 CRUD 少写代码”和“复杂 SQL 仍可控”，但还要自行拼装多数据源路由、批量写入、关联加载、本地数据库、查询缓存和表结构初始化。yulinlin-data 的目标是在一套 Session/Request 模型中统一这些能力：

- 用 Wrapper 或 Repository 完成常见 CRUD，同时保留参数化自定义 SQL 和统计分析入口。
- 内置 group/cluster、`@JoinSession` 和 RouteSession，统一 MySQL、PostgreSQL、SQLite、H2 等会话路由与本地事务协调。
- 通过 `JoinQuery`/`JoinLazy` 表达关联查询，主键关联可批量预加载，减少列表场景的 N+1。
- 通过显式 `@JoinSync` 或 `.autoUpdate()` 在事务内按 setter 跟踪部分更新，不要求所有查询都进入持久化上下文。
- 普通 JDBC batch 之上可选受控多连接拆分；SQLite 等不支持并发写的 Session 会主动退化为单写者。
- 查询缓存是可选模块：可选 Caffeine 或 Ehcache，Key 包含数据源与查询元数据，标准写入提交后按表 namespace 失效。
- MySQL、PostgreSQL、SQLite、H2 提供显式开启的 Schema 扫描；只有 `autoSchema=true` 的完整实体才可创建缺失表、普通列和声明索引。

## 与 JPA、MyBatis 的选型对比

这里的 JPA 指 Jakarta Persistence（以 Hibernate 常见实现为例），MyBatis 指原生 MyBatis。三者的抽象目标不同，表中的“更适合”不代表绝对性能胜负。

| 维度 | JPA / Hibernate | MyBatis | yulinlin-data |
| --- | --- | --- | --- |
| 编程模型 | 领域实体 + 持久化上下文，面向对象生命周期 | SQL 优先，Mapper 负责参数和结果映射 | Request/Wrapper + 可选 Repository，不维持全局持久化上下文 |
| CRUD 代码量 | 标准实体 CRUD 很少，复杂查询使用 JPQL、Criteria 或原生 SQL | 通常需声明 Mapper SQL/注解，控制明确 | 常见 CRUD 用 Wrapper/Repository，复杂场景可返回自定义 SQL |
| SQL 控制力 | 生成 SQL 为主，可使用原生 SQL；需理解 flush 和抓取策略 | 最高，适合报表、历史 SQL 和数据库特性 | 普通语法由解析器生成，也允许原始 SQL；原始 SQL 不自动跨库翻译 |
| 实体修改跟踪 | 托管实体默认参与 dirty checking/flush | 通常显式调用 UPDATE | 默认普通对象；只有显式 `@JoinSync`/`.autoUpdate()` 时在原事务内跟踪 setter |
| 关联与懒加载 | 实体关系、lazy/eager 与 EntityGraph 体系成熟，需持续治理抓取计划 | ResultMap、嵌套查询和懒加载均由 Mapper 显式组织 | `JoinQuery`/`JoinLazy` 注解映射，主键列表关联支持 IN 批量预加载 |
| 多数据源 | 可配置多个 persistence unit/EntityManagerFactory，应用需管理选择与事务 | 可配置多个 SqlSessionFactory 或集成动态数据源 | group/cluster、默认组、`@JoinSession` 和 RouteSession 是核心模型 |
| 批量写入 | Hibernate 可配 JDBC batching，大批量要管理 flush/clear 和持久化上下文 | `ExecutorType.BATCH` 提供直接批处理 | JDBC batch + `execute-batch-size`，支持的 Session 可再按 `parallel-connections` 并行拆分 |
| 查询缓存 | 持久化上下文是一级缓存，实现可配二级/查询缓存 | Session 本地缓存 + 可选 Mapper 二级缓存 | 默认无 Provider；可选 Caffeine/Ehcache，缓存 Key 纳入路由与查询元数据 |
| Schema | 规范/实现提供 Schema generation，正式迁移仍常用专用工具 | 通常交给外部迁移脚本 | 显式 `autoSchema=true` + CREATE/VALIDATE，只处理安全子集，不是完整迁移工具 |
| 标准与生态 | Jakarta 标准，实现和生态最成熟 | 成熟、文档与集成广泛 | 项目自有抽象，生态与社区规模更小，适合团队可控的工程体系 |

选型建议：

- 优先 JPA：强领域模型、复杂实体生命周期、团队已熟悉 persistence context 与抓取调优。
- 优先 MyBatis：SQL 就是主要设计产物、大量数据库专用查询、历史 SQL 迁移，或需要逐条审核 SQL。
- 优先 yulinlin-data：同时需要轻量 CRUD、统一多数据源路由、本地 SQLite/H2、可控代理、批量写入和可选缓存，且团队愿意统一遵循本项目规范。

能力边界也必须明确：yulinlin-data 的跨库提交是本地事务协调，不是 XA/2PC；自动 Schema 不负责字段改名、改类型、删列和数据搬迁；原始 SQL 不会自动翻译为其他方言；复杂领域对象图不目标为完整取代 JPA，以 SQL 为中心的超复杂报表也可能更适合 MyBatis。

对比参考了 [Jakarta Persistence 规范](https://jakarta.ee/specifications/persistence/3.2/)、[Hibernate ORM User Guide](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html) 和 [MyBatis 官方文档](https://mybatis.org/mybatis-3/)；yulinlin-data 一列以本仓库当前实现和后续专题为准。

## 九个专题

| 你要做什么 | 阅读入口 |
| --- | --- |
| 按推荐分层开发新业务，查看完整项目案例和反例 | [项目开发最佳实践](doc/09-项目开发最佳实践.md) |
| 选模块、连接数据库、多数据源与默认 group | [接入与数据源](doc/01-接入与数据源.md) |
| 实体 CRUD、自定义 SQL、统计分析、批量与事务 | [CRUD 与统计分析](doc/02-CRUD与统计分析.md) |
| 用户角色菜单、JoinQuery、懒加载、事务自动更新 | [关联查询与代理](doc/03-关联查询与代理.md) |
| HTTP，以及 lang 的反射、JSON、日期、缓存、并发、事件等全部工具 | [工具类](doc/04-工具类.md) |
| 新数据源模块、架构、排障、验收与历史性能 | [扩展开发与维护](doc/05-扩展开发与维护.md) |
| JSON 接口请求解密、响应加密与浏览器协议 | [接口安全](doc/06-接口安全.md) |
| SQLite/H2 多线程写入结果、选型依据与复现 | [SQLite/H2 性能报告](doc/07-SQLite与H2性能报告.md) |
| Caffeine/Ehcache 查询缓存、TTL、复杂 SQL 与多数据源 Key | [查询缓存](doc/08-查询缓存.md) |

[文档目录](doc/README.md)提供完整阅读导航。AI 能读取仓库时从 [llms.txt](llms.txt)进入；只能上传一个附件时用 [AI 接入指南](doc/AI接入指南.md)，其中包含七个使用专题。

## 快速选择

- MySQL ORM：starter + mysql。
- 本地 SQLite：starter + sqlite，默认文件 data/local.db、group 为 sqlite，单写连接；适合单文件和可串行写入。
- 本地 H2：starter + h2，默认数据库基路径 data/local、group 为 h2，默认最多 4 个连接；适合多线程小批量写入。SQLite/H2 可在启动时递归扫描配置包，为显式 `autoSchema=true` 的完整实体创建表、文本列说明与 `JoinIndex`；MySQL 也可启用同一机制。字符串主键默认且最多 128 字符。`autoSchema` 默认 false，CRUD 阶段不再根据 `fromClass` 执行 DDL；简要实测见[第一专题](doc/01-接入与数据源.md#sqliteh2-选型)。
- PostgreSQL 可用 `@JoinField(fullText=true)`、`match()` 和 `highlight()` 接入 pg_jieba 中文分词、GIN 索引与原字段高亮；其他 SQL 数据源保持 LIKE/原字段兼容语义。
- PostgreSQL：postgresql；common/starter 按使用门面需要引入。
- HTTP：core；反射、深克隆、JSON、日期、轻量缓存、并发与事件工具：lang。
- 接口报文加密：security；当前提供显式注解启用的 AES-256-GCM JSON 加解密。
- 查询缓存完全可选：不引入缓存模块时直接访问数据源；低延迟内存缓存引入 `cache-caffeine`，Heap + Disk 持久化缓存引入 `cache-ehcache`，两者不能同时引入。

完整依赖和配置见第一专题。制品来源由团队提供，不假设已发布 Maven Central；业务不依赖 admin 示例模块。

## 使用约定

写入先确认主键/条件，框架不默认拦截全表操作。多库或多连接是本地事务协调，不是分布式原子提交。多会话组用显式 group 或 yulinlin.datasource.default-group；primary 不自动优先。

每个专题按“入口与示例 → 常见场景 → API/配置 → 使用限制”组织。九份正文是唯一维护源，AI 单文件由使用专题派生，不再维护旧专题、存档或独立重复指南。

本轮文档核对日期为 2026-10-10；已运行本地库 JMH、JDK 25 全模块测试编译、lang/缓存专项测试，以及启动扫描/MySQL 自动配置/SQLite/H2 Schema 定向测试。维护与验证方式见第五专题。许可证见 [LICENSE](LICENSE)。
