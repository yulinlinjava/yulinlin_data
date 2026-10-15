# yulinlin-data

面向 Spring Boot 的自定义 ORM 和 Java 工具库。当前版本：JDK 25、Spring Boot 3.5.16、制品 3.0。不是 MyBatis-Plus、JPA 或 Spring Data。

## 它解决什么问题

很多 Spring Boot 项目同时需要“标准 CRUD 少写代码”和“复杂 SQL 仍可控”，但还要自行拼装多数据源路由、批量写入、关联加载、本地数据库、查询缓存和表结构初始化。yulinlin-data 的目标是在一套 Session/Request 模型中统一这些能力：

- 用 Wrapper 或 Repository 完成常见 CRUD，同时保留参数化自定义 SQL 和统计分析入口。
- 内置 group/cluster、`@JoinSession` 和 RouteSession，统一 MySQL、PostgreSQL、SQLite、H2 等会话路由与本地事务协调。
- 通过 `JoinQuery`/`JoinLazy` 表达关联查询，主键关联可批量预加载，减少列表场景的 N+1。
- 通过显式 `@JoinSync` 或 `.autoUpdate()` 在事务内按 setter 跟踪部分更新，不要求所有查询都进入持久化上下文。
- 普通 JDBC batch 之上可选受控多连接拆分；SQLite 等不支持并发写的 Session 会主动退化为单写者。
- 最终一致的高频小写入可显式使用 `.queue()`，由进程内定时队列合并 `INode` 后复用现有 Session 和 JDBC batch。
- 查询缓存是可选模块：可选 Caffeine、Ehcache 或 Redis+Caffeine，Key 包含数据源与查询元数据；条件结构稳定为单字段等值查询时按条件值精确失效，出现多种或复杂条件结构后自动升级为表 namespace 整体失效。
- 数据库优化监控是可选模块：`monitor` 聚合 SQL 平均/最大耗时并给出 P0–P4 优先级，有界保存慢操作与异常，自带登录对话框和只读 Element Plus 页面。
- MySQL、PostgreSQL、SQLite、H2 提供显式开启的 Schema 扫描，Elasticsearch 提供同一入口的索引与基础 Mapping 初始化；只有 `autoSchema=true` 的完整实体才参与结构初始化。
- PostgreSQL 与 Elasticsearch 通过同一套 `match()`、`highlight()` 和实体全文字段声明提供常用全文检索；MySQL、SQLite、H2 保持 LIKE/原字段兼容回退。

## 开发一个完整系统的成本对比

本节比较的不是“最终能不能写出来”，而是交付同一项能力时，业务开发者还需要引入什么、学习什么、编写什么，以及以后维护什么。能够手写 SQL 或再接入一个框架，不等于当前框架已经低成本支持该能力。

能力按以下口径分类：

| 类型 | 判断标准 | 开发成本 |
| --- | --- | --- |
| 内置能力 | 不增加依赖，直接使用现有实体和 API | 最低 |
| 简单扩展 | 按需增加模块和配置，继续使用原实体、Wrapper、Repository 和 Session 模型 | 低 |
| 复杂扩展 | 增加依赖后还要学习新的注解、查询 API、DSL 和生命周期 | 中高 |
| 自行实现 | 手写 SQL、适配器、缓存规则、队列或其他基础设施 | 高 |

下面以一个需要 MySQL、PostgreSQL/Elasticsearch 全文检索、H2/SQLite 本地数据、查询缓存和高频延迟写入的视频网站为例。视频文件和播放流量应走对象存储/CDN，不属于 ORM 对比范围。

### 功能交付成本

| 需要交付的能力 | MyBatis-Plus | Spring Data JPA / Hibernate | yulinlin-data |
| --- | --- | --- | --- |
| 用户、会员、订单 CRUD | 低：实体、Mapper、Service | 低：实体、Repository | 低：实体可直接使用 Model/Service，Repository 可选 |
| 页面动态查询与分页 | 中：查询对象转 Wrapper；分页插件处理 Count | 中：派生方法、Specification/Criteria 与 Pageable | 低：`PagePo + @JoinWhere`，统一分页与 Count |
| 参数化自定义 SQL | 低：Mapper/XML | 中：JPQL/Native Query | 低：`QueryRequest`、`ExecuteRequest` |
| 一对一、一对多关联 | 中高：ResultMap、Join SQL 或业务组装 | 低：实体关系是核心能力 | 低：`@JoinQuery` |
| 懒加载 | 中高：MyBatis 底层可配置，但需 ResultMap、嵌套 Select 和 Session 行为 | 低：内置 Lazy 代理，但要理解持久化上下文和 N+1 | 低：`@JoinQuery + @JoinLazy`，在路由事务内首次访问 getter 时加载 |
| 列表关联批量预加载 | 高：自行收集 ID、查询并分配 | 中：EntityGraph、Batch Fetch、Join Fetch | 低：主键关联可自动按 IN 分批预加载；复杂条件仍可能 N+1 |
| 实体修改自动写回 | 高：显式调用 UPDATE | 低：托管实体默认 dirty checking/flush | 低：默认普通对象，按需使用 `.autoUpdate()` 或 `@JoinSync` 跟踪 setter |
| MySQL、PostgreSQL、H2 访问 | 低：都可通过 JDBC 使用 | 低：使用对应 Dialect | 低：按模块接入，业务 API 不变 |
| SQLite 本地数据库 | 中高：需处理 DDL、WAL 与单写限制 | 中高：需额外 Dialect 与并发约束 | 低：模块处理文件、WAL、Schema 与单写退化 |
| 运行时切换数据源 | 中高：动态数据源扩展或多个 SqlSessionFactory | 高：多个 persistence unit/EntityManagerFactory | 低：group、默认组和 `@JoinSession` |
| PostgreSQL 全文索引、中文匹配和高亮 | 高：学习 `tsvector`、GIN、`ts_headline` 并编写 DDL/Mapper SQL | 高：Native Query；JPA 本身不提供全文 API | 低：配置 pg_jieba，字段声明 `fullText`，继续使用 `match()`/`highlight()` |
| Elasticsearch 查询、Mapping 和高亮 | 高：另接 ES Client/框架，维护 Document、DSL 和结果解析 | 中高：需引入 Hibernate Search 或 Spring Data ES，并学习新注解与 Search DSL | 低：引入 ES 模块，继续使用相同实体、Wrapper、`match()` 和 `highlight()` |
| 高级 ES 同义词、建议、向量与语义搜索 | 高：ES 专项开发 | 中：Hibernate Search/ES 专项配置 | 中高：当前公共 Wrapper 未完整覆盖，必要时使用原生 ES 能力 |
| 查询缓存接入 | 中：配置 MyBatis 二级缓存或 Spring Cache/Provider | 中：配置二级/查询缓存 Provider | 低：按需引入 Caffeine、Ehcache 或 Redis 模块，业务 API 不变 |
| 更新后的缓存失效 | 低但粗：默认让当前 Mapper namespace 缓存空间全部失效 | 中：实体缓存和查询缓存规则不同 | 低：单字段等值结构按条件值失效；出现多种或复杂结构后升级为表 namespace 整体失效 |
| 多数据源、列表与 Count 缓存隔离 | 中高：业务缓存需自行设计 Key 和失效关系 | 中高：需区分 persistence unit、Region 与查询缓存 | 低：数据源、实体、SQL、参数和请求类型统一进入缓存元数据 |
| 跨请求延迟批量写入 | 高：需自行开发队列、定时任务、分组、满量刷新和失败处理 | 高：事务 Flush 不是跨请求队列，仍需自行开发 | 低：`.queue()` 固化 INode，按 Session/类型/实体分组，按时间或 `flush-size` 刷新 |
| 自动业务聚合（例如多次 `+1` 合成 `+N`） | 不支持，业务实现 | 不支持，业务实现 | 不支持，业务决定聚合语义；聚合后的写入仍可入队 |
| 自动建表、索引与结构校验 | 高：通常维护 DDL 或接入迁移工具 | 低到中：Hibernate Schema 能力 | 低：Schema 扫描、`@JoinIndex` 和 CREATE/VALIDATE 安全子集 |
| 慢操作聚合和查看页面 | 中高：另接监控组件 | 中高：另接 Statistics/Micrometer 与页面 | 低：引入 `monitor`，使用内置只读页面 |
| MySQL/PostgreSQL 到 ES 的可靠同步 | 高：Outbox/MQ 自行实现 | 中：Hibernate Search 可降低 ORM 实体索引同步成本 | 高：当前仍需 Outbox/MQ；统一查询 API 不等于跨系统事务 |
| 多节点共享缓存 | 需 Redis/广播等外部能力 | 需 Redis/广播等外部能力 | 内置 `cache-redis`；关闭其 Caffeine L1 后所有节点直接读取 Redis |

JPA 的关联、懒加载和脏检查是内置能力，应当获得明显加分；但 Hibernate Search 不是 JPA 查询语法的延续。接入后仍需学习 `@Indexed`、`@FullTextField`、`SearchSession`、Predicate/Projection/Highlight DSL、Analyzer 和索引生命周期，因此属于复杂扩展，而不是“引入即用”的简单扩展。yulinlin-data 的数据库、搜索和缓存也是按需模块，但模块继续复用原实体和查询 API，所以按简单扩展计算。

### 开发效率参考分

分数只表示完成上述系统所需的业务开发与集成工作量；不表示运行性能、社区规模或任何框架“做不到”。分数越高，代表新增能力时需要学习的新模型、胶水代码和长期维护规则越少。

| 开发效率维度 | 权重 | MyBatis-Plus + 自行扩展 | JPA/Hibernate + Hibernate Search | yulinlin-data |
| --- | ---: | ---: | ---: | ---: |
| 多数据源统一访问 | 25 | 8 | 11 | 25 |
| 全文索引、搜索和高亮 | 18 | 4 | 8 | 16 |
| 查询缓存与失效管理 | 17 | 6 | 8 | 16 |
| 关联、懒加载和自动更新 | 15 | 5 | 14 | 13 |
| CRUD、动态查询和分页 | 10 | 9 | 9 | 10 |
| 跨请求延迟批量写入 | 8 | 2 | 2 | 8 |
| 建表、索引和 Mapping | 4 | 1 | 4 | 4 |
| 日志和慢操作监控 | 3 | 1 | 1 | 3 |
| **开发效率总分** | **100** | **36** | **57** | **95** |

这个分数针对“多数据库 + 搜索 + 缓存 + 延迟写入”的复杂新系统。若只是单 MySQL CRUD，MyBatis-Plus 和 JPA 的开发效率会明显提高；若项目以复杂领域对象图为核心，JPA 的关系与实体生命周期模型更完整；若项目要逐条控制历史 SQL，MyBatis-Plus 更直接。

从快速交付上述视频网站的角度，推荐先用 MySQL 承载用户、会员、订单和支付，用 PostgreSQL + pg_jieba 承载视频内容与常用全文检索，用 Caffeine 做单机查询加速；确实需要模糊纠错、搜索建议、复杂评分、水平扩展、向量或语义检索时，再引入 Elasticsearch。yulinlin-data 能保持 `match()`、`highlight()` 等业务调用不变，但关系库到 ES 的可靠同步仍需独立设计。

能力边界必须明确：yulinlin-data 的跨库提交是本地事务协调，不是 XA/2PC；自动 Schema 不负责字段改名、改类型、删列和数据搬迁；自动更新要求有效路由事务，回滚不会恢复 Java 对象的内存值；原始 SQL 不会自动翻译为其他方言；本地缓存不自动解决多应用节点一致性。

对比参考了 [MyBatis-Plus 官方文档](https://baomidou.com/)、[Spring Data JPA 文档](https://docs.spring.io/spring-data/jpa/reference/)、[Hibernate ORM User Guide](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html)、[Hibernate Search Reference](https://docs.jboss.org/hibernate/stable/search/reference/en-US/html_single/)、[PostgreSQL 全文检索文档](https://www.postgresql.org/docs/current/textsearch.html)和 [Elasticsearch Query DSL](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)；yulinlin-data 一列以本仓库当前实现和后续专题为准。

## 九个专题

| 你要做什么 | 阅读入口 |
| --- | --- |
| 复用 common 实体、PagePo 页面查询、通用 Service，并按推荐分层开发业务 | [Common 业务开发与项目最佳实践](doc/09-项目开发最佳实践.md) |
| 选模块、连接数据库、多数据源与默认 group | [接入与数据源](doc/01-接入与数据源.md) |
| 实体 CRUD、自定义 SQL、统计分析、批量与事务 | [CRUD 与统计分析](doc/02-CRUD与统计分析.md) |
| 用户角色菜单、JoinQuery、懒加载、事务自动更新 | [关联查询与代理](doc/03-关联查询与代理.md) |
| HTTP，以及 lang 的反射、JSON、日期、缓存、并发、事件等全部工具 | [工具类](doc/04-工具类.md) |
| 新数据源模块、架构、排障、验收与历史性能 | [扩展开发与维护](doc/05-扩展开发与维护.md) |
| JSON 接口请求解密、响应加密与浏览器协议 | [接口安全](doc/06-接口安全.md) |
| SQLite/H2 多线程写入结果、选型依据与复现 | [SQLite/H2 性能报告](doc/07-SQLite与H2性能报告.md) |
| Caffeine/Ehcache/Redis 查询缓存、TTL、复杂 SQL 与多数据源 Key | [查询缓存](doc/08-查询缓存.md) |

[文档目录](doc/README.md)提供完整阅读导航。AI 能读取仓库时从 [llms.txt](llms.txt)进入；只能上传一个附件时用 [AI 接入指南](doc/AI接入指南.md)，其中包含七个使用专题。

## 快速选择

- MySQL ORM：starter + mysql。
- 本地 SQLite：starter + sqlite，默认文件 data/local.db、group 为 sqlite，单写连接；适合单文件和可串行写入。
- 本地 H2：starter + h2，默认数据库基路径 data/local、group 为 h2，默认最多 4 个连接；适合多线程小批量写入。SQLite/H2 可在启动时递归扫描配置包，为显式 `autoSchema=true` 的完整实体创建表、文本列说明与 `JoinIndex`；MySQL 也可启用同一机制。字符串主键默认且最多 128 字符。`autoSchema` 默认 false，CRUD 阶段不再根据 `fromClass` 执行 DDL；简要实测见[第一专题](doc/01-接入与数据源.md#sqliteh2-选型)。
- PostgreSQL 可用 `@JoinField(fullText=true)`、`match()` 和 `highlight()` 接入 pg_jieba 中文分词、GIN 索引与原字段高亮；其他 SQL 数据源保持 LIKE/原字段兼容语义。
- PostgreSQL：postgresql；common/starter 按使用门面需要引入。
- HTTP：core；反射、深克隆、JSON、日期、轻量缓存、并发与事件工具：lang。
- 接口报文加密：security；当前提供显式注解启用的 AES-ECB（默认）、AES-GCM 和 AES-CBC JSON 加解密。
- 查询缓存完全可选：`cache-core` 只承载缓存 SPI、`CacheClient` 和 Key/TTL/失效模型，并由 ORM core 或 Provider 传递引入；不引入具体 Provider 时直接访问数据源。低延迟内存缓存引入 `cache-caffeine`，Heap + Disk 持久化缓存引入 `cache-ehcache`，Redis 共享缓存引入 `cache-redis`。三个 Provider 只能选择一个；`cache-redis` 已内置可关闭的 Caffeine L1，不要再引入 `cache-caffeine`。
- 慢数据库监控：引入 `monitor`，访问 `/monitor/`；可通过 `yulinlin.monitor.enabled=false` 完全关闭采集与页面，页面内置账号密码验证对话框，验证结果保存在当前 Session，不依赖安全框架。

完整依赖和配置见第一专题。制品来源由团队提供，不假设已发布 Maven Central；业务不依赖 admin 示例模块。

## 使用约定

写入先确认主键/条件，框架不默认拦截全表操作。多库或多连接是本地事务协调，不是分布式原子提交。多会话组用显式 group 或 yulinlin.datasource.default-group；primary 不自动优先。

每个专题按“入口与示例 → 常见场景 → API/配置 → 使用限制”组织。九份正文是唯一维护源，AI 单文件由使用专题派生，不再维护旧专题、存档或独立重复指南。

框架维护默认只进行代码与文档修改、静态阅读检查，不主动执行编译、打包、测试、Spring YAML 属性绑定测试或 `git diff --check`；用户在当前任务中明确要求后才运行。完整约定见[扩展开发与维护](doc/05-扩展开发与维护.md#ai-使用约定)。

本轮文档核对日期为 2026-10-10；已运行本地库 JMH、JDK 25 全模块测试编译、lang/缓存专项测试，以及启动扫描/MySQL 自动配置/SQLite/H2 Schema 定向测试。维护与验证方式见第五专题。许可证见 [LICENSE](LICENSE)。
