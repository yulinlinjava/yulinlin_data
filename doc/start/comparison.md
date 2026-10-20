# 技术选型与开发成本对比

[文档首页](../README.md) / [快速开始](README.md)

以下为已有项目能力对比，评分不是运行性能或通用排名；并非本次重新验证外部产品。

## 开发一个完整系统的成本对比

本节比较的不是“最终能不能写出来”，而是交付同一项能力时，业务开发者还需要引入什么、学习什么、编写什么，以及以后维护什么。能够手写 SQL 或再接入一个框架，不等于当前框架已经低成本支持该能力。

能力按以下口径分类：

| 类型 | 判断标准 | 开发成本 |
| --- | --- | --- |
| 内置能力 | 不增加依赖，直接使用现有实体和 API | 最低 |
| 简单扩展 | 按需增加模块和配置，继续使用原实体、Wrapper、Repository 和 Session 模型 | 低 |
| 复杂扩展 | 增加依赖后还要学习新的注解、查询 API、DSL 和生命周期 | 中高 |
| 自行实现 | 手写 SQL、适配器、缓存规则、队列或其他基础设施 | 高 |

下面以一个需要 MySQL、PostgreSQL/Elasticsearch 全文检索、H2/SQLite 本地数据、查询缓存、高频延迟写入和播放热度聚合的视频网站为例。视频文件和播放流量应走对象存储/CDN，不属于 ORM 对比范围。

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
| 多种缓存同时使用 | 中：配置 CacheManager 及选择规则 | 中：ORM Provider 与业务 CacheManager 分别配置 | 低：缓存模块共存，配置 default；ORM 统一默认，客户端可按名称选择 |
| 更新后的缓存失效 | 低但粗：默认让当前 Mapper namespace 缓存空间全部失效 | 中：实体缓存和查询缓存规则不同 | 低：单字段等值结构按条件值失效；出现多种或复杂结构后升级为表 namespace 整体失效 |
| 多数据源、列表与 Count 缓存隔离 | 中高：业务缓存需自行设计 Key 和失效关系 | 中高：需区分 persistence unit、Region 与查询缓存 | 低：数据源、实体、SQL、参数和请求类型统一进入缓存元数据 |
| 跨请求延迟批量写入 | 高：需自行开发队列、定时任务、分组、满量刷新和失败处理 | 高：事务 Flush 不是跨请求队列，仍需自行开发 | 低：`.queue()` 固化 INode，按 Session/类型/实体分组，按时间或 `flush-size` 刷新 |
| 自动计数聚合（例如多次 `+1` 合成 `+N`） | 高：业务自行维护计数器、调度、失败恢复和批量 SQL | 高：事务 Flush 不跨请求，仍需业务实现聚合任务 | 低：`EntityCounter` 支持按主键聚合更新、按非数字维度聚合插入，按时间或阈值刷新 |
| 自动建表、索引与结构校验 | 高：通常维护 DDL 或接入迁移工具 | 低到中：Hibernate Schema 能力 | 低：Schema 扫描、`@JoinIndex` 和 CREATE/VALIDATE 安全子集 |
| 慢操作聚合和查看页面 | 中高：另接监控组件 | 中高：另接 Statistics/Micrometer 与页面 | 低：引入 `monitor`，使用内置只读页面 |
| 框架内跨数据源轻量增量同步 | 高：需自行监听变化、适配目标写入和异步队列，或接入同步工具 | 中高：关系库到 ES 可接 Hibernate Search；通用跨库同步仍需另做 | 低：引入 `data-sync`，配置 source/target/entities，提交后重放框架写请求；有界内存队列、失败暂停 |
| MySQL/PostgreSQL 人工全量补同步与续传 | 高：自行分页、目标写入、保存进度和恢复 | 高：通用关系库间补同步仍需自行实现；索引重建不等于跨库迁移 | 低：`sync(source,target,class)` 自动读取主键进度，`restart(...)` 从头覆盖；业务停写，内部暂停相关增量消费 |
| 崩溃可恢复的可靠同步（含关系库到 ES） | 高：另接 CDC，或实现事务 Outbox、消费与重试 | 中高：ORM 实体到 ES 可接 Hibernate Search outbox-polling，仍需额外依赖、配置和运维 | 高：当前轻量队列不保证可靠交付，仍需 CDC 或事务 Outbox 等额外机制；不是已经内置的能力 |
| 多节点共享缓存 | 需 Redis/广播等外部能力 | 需 Redis/广播等外部能力 | 内置 `cache-redis`；关闭其 Caffeine L1 后所有节点直接读取 Redis |

JPA 的关联、懒加载和脏检查是内置能力，应当获得明显加分；但 Hibernate Search 不是 JPA 查询语法的延续。接入后仍需学习 `@Indexed`、`@FullTextField`、`SearchSession`、Predicate/Projection/Highlight DSL、Analyzer 和索引生命周期，因此属于复杂扩展，而不是“引入即用”的简单扩展。yulinlin-data 的数据库、搜索和缓存也是按需模块，但模块继续复用原实体和查询 API，所以按简单扩展计算。

同步成本要区分“已有功能”和“可靠性保障”：`data-sync` 已提供跨 group 的轻量增量入口，目标使用自身解析器，因此不应再把所有同步都写成必须自行实现 Outbox/MQ。ES 作为增量目标时仍受其节点解析、字段映射和写入语义限制，不能据此宣称所有 SQL 条件均已兼容；当前人工全量只支持 MySQL/PostgreSQL，不包含 ES。以上同步实现尚未完成真实服务端组合验证。若要求源提交与事件持久化原子完成、崩溃恢复、可靠重试，则属于另外一档成本；Hibernate Search 的 [outbox-polling](https://docs.jboss.org/hibernate/stable/search/reference/en-US/html_single/#coordination-outbox-polling) 是这类 ORM 索引同步扩展，不能与普通提交后异步通知混为一谈。

### 开发效率参考分

分数只表示完成上述系统所需的业务开发与集成工作量；不表示运行性能、社区规模或任何框架“做不到”。分数越高，代表新增能力时需要学习的新模型、胶水代码和长期维护规则越少。

| 开发效率维度 | 权重 | MyBatis-Plus + 自行扩展 | JPA/Hibernate + Hibernate Search | yulinlin-data |
| --- | ---: | ---: | ---: | ---: |
| 多数据源统一访问 | 25 | 8 | 11 | 25 |
| 全文索引、搜索和高亮 | 18 | 4 | 8 | 16 |
| 查询缓存与失效管理 | 17 | 6 | 8 | 16 |
| 关联、懒加载和自动更新 | 15 | 5 | 14 | 13 |
| CRUD、动态查询和分页 | 10 | 9 | 9 | 10 |
| 跨请求延迟写入与计数聚合 | 8 | 2 | 2 | 8 |
| 建表、索引和 Mapping | 4 | 1 | 4 | 4 |
| 日志和慢操作监控 | 3 | 1 | 1 | 3 |
| **开发效率总分** | **100** | **36** | **57** | **95** |

这个分数针对“多数据库 + 搜索 + 缓存 + 延迟写入”的复杂新系统。若只是单 MySQL CRUD，MyBatis-Plus 和 JPA 的开发效率会明显提高；若项目以复杂领域对象图为核心，JPA 的关系与实体生命周期模型更完整；若项目要逐条控制历史 SQL，MyBatis-Plus 更直接。

从快速交付上述视频网站的角度，推荐先用 MySQL 承载用户、会员、订单和支付，用 PostgreSQL + pg_jieba 承载视频内容与常用全文检索，用 Caffeine 做单机查询加速；确实需要模糊纠错、搜索建议、复杂评分、水平扩展、向量或语义检索时，再引入 Elasticsearch。yulinlin-data 能保持 `match()`、`highlight()` 等业务调用不变，并已提供 `data-sync` 轻量增量同步和 MySQL/PostgreSQL 人工全量续传；只有要求崩溃可恢复等可靠交付保障时，才需要进一步接入 CDC 或事务 Outbox 等机制。不要把“有轻量同步”表述成“已有可靠数据库复制”。

能力边界必须明确：yulinlin-data 的跨库提交是本地事务协调，不是 XA/2PC；自动 Schema 默认不改列类型，MySQL/PostgreSQL/H2 可在人工确认并备份后显式开启 `alter-column-type`，不负责字段改名、删列和跨列数据搬迁；自动更新要求有效路由事务，回滚不会恢复 Java 对象的内存值；原始 SQL 不会自动翻译为其他方言；本地缓存不自动解决多应用节点一致性。

对比参考了 [MyBatis-Plus 官方文档](https://baomidou.com/)、[Spring Data JPA 文档](https://docs.spring.io/spring-data/jpa/reference/)、[Hibernate ORM User Guide](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html)、[Hibernate Search Reference](https://docs.jboss.org/hibernate/stable/search/reference/en-US/html_single/)、[PostgreSQL 全文检索文档](https://www.postgresql.org/docs/current/textsearch.html)和 [Elasticsearch Query DSL](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)；yulinlin-data 一列以本仓库当前实现和后续专题为准。
