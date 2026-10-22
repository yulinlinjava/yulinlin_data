# yulinlin-data AI 使用指南

---

> 自动生成；正文来源为 topics.json 中 ai=true 的小专题。不要手工编辑本文件。

---

适用 JDK 25、Spring Boot 3.5.16、制品 3.0。用于无法读取仓库时的单附件接入；有仓库访问能力时先读 llms.txt，按任务读取小专题即可。内部架构、监控与完整性能报告按需另提供。

---

代码片段不等于已完成编译或全部数据库集成验证。历史测试结果只代表原日期和环境，本次文档导出不编译、不运行测试。业务必须替换示例表、账号、地址和密钥。

---

## 主题导航

- **快速开始**：[模块选择与版本](#ai-start-modules) · [技术选型与开发成本对比](#ai-start-comparison)
- **数据源**：[MySQL 接入](#ai-datasources-mysql) · [SQLite 接入](#ai-datasources-sqlite) · [自动建表、列类型与索引](#ai-datasources-schema) · [H2 接入](#ai-datasources-h2) · [本地数据库选型](#ai-datasources-local-choice) · [PostgreSQL 接入](#ai-datasources-postgresql) · [Elasticsearch 接入](#ai-datasources-elasticsearch) · [多数据源与默认组](#ai-datasources-routing) · [数据源配置速查](#ai-datasources-config)
- **ORM 开发**：[实体映射与最小模型](#ai-orm-entity) · [插入、更新与删除](#ai-orm-crud) · [查询条件、分页与结果](#ai-orm-query) · [全文检索与高亮](#ai-orm-search) · [SQL JOIN](#ai-orm-join) · [自定义 SQL](#ai-orm-raw-sql) · [统计分析](#ai-orm-analytics) · [请求与结果过滤器](#ai-orm-filters) · [Repository 接口](#ai-orm-repository)
- **关联与事务**：[事务与提交后回调](#ai-relations-transactions) · [关联查询](#ai-relations-query) · [懒加载与批量预加载](#ai-relations-lazy) · [代理自动更新](#ai-relations-auto-update)
- **批量与同步**：[增量同步](#ai-write-sync-incremental) · [人工全量同步与续传](#ai-write-sync-full) · [批量与多连接写入](#ai-write-sync-batch) · [EntityCounter 聚合插入与更新](#ai-write-sync-counter) · [延迟写队列](#ai-write-sync-queue)
- **缓存**：[缓存选择与公共配置](#ai-cache-config) · [Caffeine 内存缓存](#ai-cache-caffeine) · [Ehcache 持久化缓存](#ai-cache-ehcache) · [Redis 与可选本地缓存](#ai-cache-redis) · [缓存序列化](#ai-cache-serialization) · [ORM 查询缓存](#ai-cache-query) · [CacheClient 业务缓存](#ai-cache-client) · [缓存 Key、失效与一致性](#ai-cache-invalidation)
- **接口安全**：[安全模块选型](#ai-security-modules) · [接口加密与前端对接](#ai-security-crypto) · [自定义接口加解密](#ai-security-crypto-codec) · [限流与 IP 黑名单](#ai-security-rate-limit) · [用户模型与安全管理器](#ai-security-user) · [无状态 JWT 登录](#ai-security-jwt) · [自定义 Token 生成与解析](#ai-security-token-codec) · [自定义安全实现与拦截器](#ai-security-extension) · [有状态 Sa-Token 登录](#ai-security-sa-token) · [Spring Security 接入](#ai-security-spring) · [登录上下文生命周期](#ai-security-context) · [登录、角色与权限注解](#ai-security-authorization)
- **常用工具**：[lang 定位与类型目录](#ai-tools-catalog) · [HTTP 请求、上传与下载](#ai-tools-http) · [HTTP 执行器规范与替换](#ai-tools-http-executor) · [反射复制与深克隆](#ai-tools-reflection) · [JSON 转换](#ai-tools-json) · [日期、数字与字符串工具](#ai-tools-value) · [轻量缓存与资源池](#ai-tools-local-cache) · [线程、调度与事件](#ai-tools-concurrency) · [Lambda、泛型与包扫描](#ai-tools-types) · [序列化、元组与扩展 SPI](#ai-tools-serialization)
- **项目实践**：[项目开发规范](#ai-practice-conventions) · [公共实体与页面参数](#ai-practice-models) · [通用 Service 与树服务](#ai-practice-service) · [完整 CRUD 分层案例](#ai-practice-crud-example) · [Admin RBAC 与 JWT 登录案例](#ai-practice-rbac)

---

<a id="ai-start-modules"></a>
<!-- source: doc/start/modules.md -->
<a id="ai-start-modules-模块选择与版本"></a>
## 模块选择与版本

[文档首页](README.md) / [快速开始](start/README.md)

先确定需要引入的模块；业务不依赖 admin 示例模块。

<a id="ai-start-modules-本页导航"></a>
### 本页导航

- [模块选择](#ai-start-modules-模块选择)

<a id="ai-start-modules-模块选择"></a>
### 模块选择

| 使用场景 | 依赖 |
| --- | --- |
| Spring Boot 和 MySQL ORM | `com.yulinlin:starter:3.0` + `com.yulinlin:mysql:3.0` |
| Spring Boot 和 SQLite ORM | starter + sqlite |
| Spring Boot 和 H2 本地 ORM | starter + h2 |
| Spring Boot 和 PostgreSQL ORM | postgresql；需要便利模型门面时再加 common 或 starter |
| HTTP 工具 | core，starter 已传递引入 |
| 反射、深克隆、JSON | lang |
| JSON 接口请求/响应加密、限流、权限协议 | security；Web 能力需要 Spring MVC，独立于 ORM 数据库模块 |
| 用户登录与请求身份恢复 | security-jwt（无状态）或 security-sa-token（有状态）二选一，自动依赖 security；业务提供 UserStore，见[安全选型与扩展](#ai-security-modules-jwt-与-sa-token-如何选择) |
| 慢数据库操作与内置管理页面 | monitor；依赖公共 JDBC 执行入口和 Spring MVC |
| 增量同步 / MySQL、PostgreSQL 全量续传 | data-sync + 源/目标数据库模块；默认关闭，依赖公共 jdbc，不自带数据库驱动和 Web 页面 |
| 缓存公共协议与 `CacheClient` | cache-core；由 core 和具体缓存 Provider 传递引入，业务通常不需要单独声明 |
| 可选查询缓存 | cache-caffeine、cache-ehcache、cache-redis 可共存；单实现自动选择，多个实现用 yulinlin.cache.default 指定 ORM 默认缓存，客户端可指定名称 |
| 实体基类、Model Wrapper、树和 ID | common |
| 示例或基准 | admin，不作为业务依赖 |

依赖应来自团队制品仓库或同一份源码的发布产物，不假设已发布 Maven Central。同组模块保持相同版本和构建来源。Lombok 示例需要业务项目提供 Lombok 与注解处理；不使用它时手写 getter/setter。

core 同时包含 ORM 自动配置，不是独立的纯 HTTP starter。只使用公共 JDBC Session 时，数据库模块不强制依赖 common/starter。

---

下一步：返回[快速开始主题目录](start/README.md)按需选择相关专题。

---

<a id="ai-start-comparison"></a>
<!-- source: doc/start/comparison.md -->
<a id="ai-start-comparison-技术选型与开发成本对比"></a>
## 技术选型与开发成本对比

[文档首页](README.md) / [快速开始](start/README.md)

以下为已有项目能力对比，评分不是运行性能或通用排名；并非本次重新验证外部产品。

<a id="ai-start-comparison-开发一个完整系统的成本对比"></a>
### 开发一个完整系统的成本对比

本节比较的不是“最终能不能写出来”，而是交付同一项能力时，业务开发者还需要引入什么、学习什么、编写什么，以及以后维护什么。能够手写 SQL 或再接入一个框架，不等于当前框架已经低成本支持该能力。

能力按以下口径分类：

| 类型 | 判断标准 | 开发成本 |
| --- | --- | --- |
| 内置能力 | 不增加依赖，直接使用现有实体和 API | 最低 |
| 简单扩展 | 按需增加模块和配置，继续使用原实体、Wrapper、Repository 和 Session 模型 | 低 |
| 复杂扩展 | 增加依赖后还要学习新的注解、查询 API、DSL 和生命周期 | 中高 |
| 自行实现 | 手写 SQL、适配器、缓存规则、队列或其他基础设施 | 高 |

下面以一个需要 MySQL、PostgreSQL/Elasticsearch 全文检索、H2/SQLite 本地数据、查询缓存、高频延迟写入和播放热度聚合的视频网站为例。视频文件和播放流量应走对象存储/CDN，不属于 ORM 对比范围。

<a id="ai-start-comparison-功能交付成本"></a>
#### 功能交付成本

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

<a id="ai-start-comparison-开发效率参考分"></a>
#### 开发效率参考分

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

---

<a id="ai-datasources-mysql"></a>
<!-- source: doc/datasources/mysql.md -->
<a id="ai-datasources-mysql-mysql-接入"></a>
## MySQL 接入

[文档首页](README.md) / [数据源](datasources/README.md)

配置 MySQL 数据源和默认会话。

共同规则：[自动建表与索引](#ai-datasources-schema) · [多数据源与默认组](#ai-datasources-routing) · [SQL 与事务日志](operations/sql-log.md)。

<a id="ai-datasources-mysql-本页导航"></a>
### 本页导航

- [MySQL 接入](#ai-datasources-mysql-mysql-接入-1)

<a id="ai-datasources-mysql-mysql-接入-1"></a>
### MySQL 接入

加入依赖：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>starter</artifactId>
    <version>3.0</version>
</dependency>
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>mysql</artifactId>
    <version>3.0</version>
</dependency>
```

配置自己的数据库账号。下面的地址和环境变量是示例，不应直接用于生产：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

yulinlin:
  mysql:
    log: true
    log-params: false
    max-param-length: 256
    slow-sql-threshold: 1s
    map-underscore-to-camel-case: true
    parallel-connections: 4
    execute-batch-size: 256
    schema-mode: CREATE
    schema-packages:
      - demo.domain
```

正常 Boot 自动配置链下无需框架启用注解。mysql 模块创建 `mysqlSessionFactory` 和 `mysqlSession`，默认 group 为 `mysql`。为兼容已有生产项目，`schema-mode` 默认 `NONE`；不需要框架管理结构时删除整个 `yulinlin.mysql` 段并由迁移脚本准备表。设为 `CREATE` 后，启动时递归扫描 `schema-packages`，创建缺失的表、普通列和声明索引，并校验已有结构；`VALIDATE` 只校验，缺失直接阻止启动。完整实体与 CRUD 见 [ORM 开发](orm/README.md)。

---

下一步：返回[数据源主题目录](datasources/README.md)按需选择相关专题。

---

<a id="ai-datasources-sqlite"></a>
<!-- source: doc/datasources/sqlite.md -->
<a id="ai-datasources-sqlite-sqlite-接入"></a>
## SQLite 接入

[文档首页](README.md) / [数据源](datasources/README.md)

单文件本地数据库、WAL 和单写约束。

共同规则：[自动建表与索引](#ai-datasources-schema) · [多数据源与默认组](#ai-datasources-routing) · [SQL 与事务日志](operations/sql-log.md)。

<a id="ai-datasources-sqlite-本页导航"></a>
### 本页导航

- [SQLite 接入](#ai-datasources-sqlite-sqlite-接入-1)

<a id="ai-datasources-sqlite-sqlite-接入-1"></a>
### SQLite 接入

仅加入 starter + sqlite，无需数据库服务器、账号或密码：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>starter</artifactId>
    <version>3.0</version>
</dependency>
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>sqlite</artifactId>
    <version>3.0</version>
</dependency>
```

引入模块即启用，不提供额外的模块级开关。未配置时使用进程工作目录下的 `data/local.db`，默认 group 为 `sqlite`。可按需覆盖：

```yaml
yulinlin:
  sqlite:
    file: data/app.db
    group: sqlite
    log: true
    map-underscore-to-camel-case: true
    execute-batch-size: 256
    busy-timeout: 5000
    synchronous: NORMAL
    schema-mode: CREATE
    schema-packages:
      - demo.domain
```

启动时创建父目录与文件，启用 WAL、外键约束和单连接池。SQLite 的 `schema-mode` 默认 `CREATE`：递归扫描 `schema-packages` 并创建缺失结构；`VALIDATE` 只校验；`NONE` 完全跳过扫描。file 仅接受本地路径，不接受 JDBC URL、内存数据库或 file URI；空路径会报错。文件应放在可写的本地持久化目录，而不是 classpath 或网络共享盘。

---

下一步：返回[数据源主题目录](datasources/README.md)按需选择相关专题。

---

<a id="ai-datasources-schema"></a>
<!-- source: doc/datasources/schema.md -->
<a id="ai-datasources-schema-自动建表列类型与索引"></a>
## 自动建表、列类型与索引

[文档首页](README.md) / [数据源](datasources/README.md)

显式声明结构拥有者，再配置扫描；CRUD 不触发建表。

<a id="ai-datasources-schema-本页导航"></a>
### 本页导航

- [启动扫描与表结构拥有者](#ai-datasources-schema-启动扫描与表结构拥有者)
- [文本列、长度与说明](#ai-datasources-schema-文本列长度与说明)
- [显式允许修改列类型](#ai-datasources-schema-显式允许修改列类型)
- [表结构错误排查](#ai-datasources-schema-表结构错误排查)
- [预览建表 SQL](#ai-datasources-schema-预览建表-sql)
- [Schema 边界](#ai-datasources-schema-schema-边界)
- [MySQL/PostgreSQL/H2/SQLite 自动索引](#ai-datasources-schema-mysqlpostgresqlh2sqlite-自动索引)

<a id="ai-datasources-schema-启动扫描与表结构拥有者"></a>
### 启动扫描与表结构拥有者

MySQL、PostgreSQL、SQLite、H2 和 Elasticsearch 共用 core 中相同的扫描规则。每个配置包都会递归扫描子包，但只处理显式声明 `@JoinTable(..., autoSchema = true)` 的具体普通结构类。`autoSchema` 默认 `false`；普通查询实体、DTO、接口、抽象类、JOIN 模型和统计模型不参与。未配置扫描包时也不扫描任何类。

包配置支持通配符：`*` 只匹配一级包，`**` 匹配零到任意多级包；匹配到的包仍会递归扫描子包。建议在 YAML 中加引号。例如 `"com.example.*.*.local"` 会匹配 `com.example` 与 `local` 之间恰好两级的目录，而 `"com.example.**.local"` 允许中间为任意层级。只允许完整包段使用通配符，`jdbc*`、`***` 和空包段会在启动时报配置错误。多个配置项命中的同一个类会自动去重。

```yaml
yulinlin:
  mysql:
    schema-mode: CREATE
    schema-packages:
      - "com.example.*.*.local"
      - "com.example.shared.entity"
```

同一个物理表只允许一个完整实体明确负责结构。轻量 DTO、不同字段视图和统计模型保持默认值即可：

```java
@JoinTable(value = "ai_demo_user", autoSchema = true)
public class DemoUser { /* 完整持久化字段 */ }

@JoinTable("ai_demo_user") // autoSchema 默认 false，只做 ORM 映射
public class DemoUserSummary { /* 查询投影 */ }
```

扫描结果若发现两个 `autoSchema=true` 的类映射同一表，应用启动失败并列出冲突类，避免“小实体先创建残缺表”。建表沿用字段别名、下划线映射、继承字段、单主键和 `JoinIndex` 元信息；static、transient、非持久化和关联查询字段不生成列。

<a id="ai-datasources-schema-文本列长度与说明"></a>
### 文本列、长度与说明

完整结构实体可以通过 `JoinField` 明确普通文本长度、大文本类型和列用途：

```java
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.TextTypeEnum;

@JoinField(textLength = 120, description = "视频标题")
private String title;

@JoinField(textType = TextTypeEnum.text, description = "视频正文")
private String content;
```

`textType` 默认是 `auto`。正数 `textLength` 会将文本列解析为定长上限的 VARCHAR；`textType=text` 表示大文本，不能再同时设置 `textLength`。自动 Schema 的映射如下：

| 声明 | MySQL | PostgreSQL | H2 | SQLite |
| --- | --- | --- | --- | --- |
| 默认普通文本 | `VARCHAR(255)` | `VARCHAR(255)` | `CHARACTER VARYING` | `TEXT` |
| `textLength = n` | `VARCHAR(n)` | `VARCHAR(n)` | `CHARACTER VARYING(n)` | 声明为 `VARCHAR(n)`，仍采用 TEXT affinity |
| `textType = text` | `LONGTEXT` | `TEXT` | `CHARACTER LARGE OBJECT`/CLOB | `TEXT` |

自动 Schema 会识别框架支持的时间类型：

| Java 类型 | MySQL | PostgreSQL | H2 | SQLite |
| --- | --- | --- | --- | --- |
| `java.util.Date`、框架 `DateTime`、`LocalDateTime` | `DATETIME` | `TIMESTAMP WITHOUT TIME ZONE` | `TIMESTAMP` | `TEXT` |
| `LocalDate` | `DATE` | `DATE` | `DATE` | `TEXT` |
| `LocalTime` | `TIME` | `TIME WITHOUT TIME ZONE` | `TIME` | `TEXT` |

各数据库模块自己的 `coder` 包提供 `MysqlCoderManager`、`PostgresqlCoderManager`、`H2CoderManager`、`SqliteCoderManager`，均继承 `JdbcCoderManager`，内部注册本模块五种时间类型的编码器。具体时间规则仍分别定义在本模块的 `*TemporalCoders` 中；公共转换复用 `TemporalConversions`，空值/接口适配复用基类。工厂直接使用对应管理器，不传编码器列表，也不注入全局管理器；普通业务无需新增配置。不同数据库的管理器与编码规则独立，修改 H2 编码器不会修改其他模块。

- MySQL、PostgreSQL、H2 各自的编码器当前都使用原生时间参数：框架 `DateTime` 编码为其内部 `LocalDateTime`，`LocalDateTime` 原样传给驱动，不再转为 `java.sql.Timestamp`。分别对应 `DATETIME`、`TIMESTAMP WITHOUT TIME ZONE` 和不带时区的 `TIMESTAMP`，不依赖 VARCHAR 的隐式转换；规则目前相同，但实现分别维护。
- 这三个模块中，`java.util.Date` 仍编码为 `Timestamp`，`LocalDate` / `LocalTime` 仍编码为 `java.sql.Date` / `java.sql.Time`，保留原有行为；`Time` 编码仍是秒级精度。
- SQLite 自己的编码器直接输出 TEXT：`DateTime` / `LocalDateTime` / `Date` 为 `yyyy-MM-dd HH:mm:ss`，`LocalDate` 为 `yyyy-MM-dd`，`LocalTime` 为 `HH:mm:ss`。格式器也定义在 SQLite 模块，保留旧数据的空格分隔、秒级精度；写入和查询条件使用相同规则，日期范围比较不混用带 `T` 的格式。原始参数绑定中的时间对象也调用 SQLite 自己的编码器。
- JDBC 解码支持 `LocalDateTime`、旧驱动返回的 `Timestamp`，以及空格或 `T` 分隔、可带 1～9 位小数秒的日期时间文本。文本直接解析为本地时间，不经 `Timestamp` 中转；MySQL/PostgreSQL/H2 实际保存的小数秒精度仍由列定义与驱动决定，SQLite 保持秒级存储。
- core 的 `DateTimeCoder`、JSON 展示，以及 MongoDB/Elasticsearch 的原有时间规则不变。扩展数据库时定义自己的 `CoderManager` 并在内部注册编码器，自行声明工厂时例如传入 `new H2CoderManager()`，参见 [编解码扩展](internal/jdbc.md#编解码)。`SqlParseManager` 仍只负责 SQL 解析，字段编解码沿用 `ICoderManager`。

`DateTime` / `LocalDateTime` 表示无时区的日期时间，这次修复避免它们因为被转成 `Timestamp` 而触发驱动的时间点换算。它不会修改 JVM 或数据库时区；`DateTime.now()` 仍取 JVM 本地时间。`java.util.Date` 表示时间点，保留原有时区处理；手工建成 MySQL `TIMESTAMP`、PostgreSQL/H2 带时区列的字段仍有数据库自身的时区语义，不能据此认为所有列都不再换算。历史上已经写偏的值不会自动修复，不要直接对整表统一加减 8 小时。升级时同步更新 core、jdbc 及项目使用的 mysql/postgresql/h2/sqlite 模块，避免新旧注册方法、工厂和编码器混用。

时间字段不接受 `textType` 或 `textLength`；这两个选项只用于真正按文本编解码的字段。

字符串主键未指定长度（`textLength=0`）时统一创建为 `VARCHAR(32)`；显式指定正整数 `textLength` 时按注解生成，不再限制为最多 128。长度单位是字符，不是 bit；例如下面表示最多 64 个字符的字符串主键。数据库自身的列长度、主键索引字节数等限制仍然适用，大文本仍禁止作为主键或框架自动索引。

```java
@JoinField(textLength = 64)
@JoinMeta(primaryKey = true)
private String id;
```

MySQL/PostgreSQL/H2 会约束 VARCHAR 长度，SQLite 只保留 `VARCHAR(n)` 声明，不强制字符长度。默认值变更仅影响新建列，不自动缩短已有主键；未显式指定长度时，不校验已有列长度。显式指定长度时按容量兼容：已有列长度大于等于要求即可通过，例如实体要求 32、旧列为 128，无需改表；实体要求 64、旧列只有 32，默认报错，可手动扩容或显式开启下文的 `alter-column-type`。此规则同时适用于字符串主键和普通 VARCHAR 字段；主键约束缺失仍报错，默认不自动修改已有类型。

`description` 最多 1024 个字符。新建表或补充新列时，MySQL 写入列 `COMMENT`，PostgreSQL/H2 执行 `COMMENT ON COLUMN`；SQLite 没有原生列注释，因此只接受该元数据但不落库。已有列不会因为 description 变化而执行 ALTER，注释也不参与兼容性校验。

这些选项只服务于启动 Schema 描述，不改变运行期 SQL 编解码器，也不会在 Java 端截断或校验字符串。MySQL/PostgreSQL/H2 对显式 VARCHAR 长度只检查已有列容量是否足够，实际可写长度由数据库列决定；SQLite 不执行或校验 VARCHAR 长度限制，确需强制长度应使用业务校验或自行添加 CHECK 约束。

SQLite 的整数/布尔值对应 INTEGER，浮点数对应 REAL，其余默认 TEXT。日期、枚举、BigDecimal、JSON 对象等使用 TEXT；字符串与数字仍由 JDBC 编解码器恢复 Java 字段。日期范围依赖统一、可按字典序排序的固定格式与时区；TEXT BigDecimal 的字符串比较不等于数值比较，数值范围或统计需要适当列类型或显式 SQL CAST。

自动 Schema 默认不会修改已有列类型。升级后，如果时间字段原来由自动建表创建成 VARCHAR/TEXT，会报告类型不兼容；可以先通过数据库迁移脚本转换，或在人工确认格式与数据可转换后按下文显式启用改列。SQLite 的时间仍使用 TEXT，不需要该类型迁移。

启动完成后，CRUD 和自定义 SQL 均不再检查或修改 Schema。`BaseRequest.fromClass` 为 null、Object.class 或任意类型都不会触发 DDL；它原有的映射与路由语义不变。自定义 SQL 涉及的表若不在扫描实体中，应由迁移脚本或其他初始化流程创建。

<a id="ai-datasources-schema-显式允许修改列类型"></a>
### 显式允许修改列类型

仅在用户已人工确认现有数据可转换、且已备份时启用。开关默认 `false`，各数据源独立配置，不改变其他数据源：

```yaml
yulinlin:
  mysql:
    schema-mode: CREATE
    schema-packages:
      - "com.example.domain"
    alter-column-type: true
  postgresql:
    alter-column-type: false
  h2:
    alter-column-type: false
```

只处理扫描中 `@JoinTable(autoSchema=true)` 的结构拥有者，启动阶段执行；`schema-mode=NONE` 不操作，`VALIDATE` 即使开启此开关也只校验。不扫描业务数据提前判断转换是否安全，失败由数据库报告；该开关不是忽略校验。

| 现有结构与实体目标 | 开关开启后的 CREATE 行为 |
| --- | --- |
| 列存储类型不同 | 按实体当前 Schema 映射生成改类型 DDL，执行后重新读取结构校验 |
| 同为字符串，显式要求 64，已有 32 | 扩容至 64 |
| 同为字符串，要求 32，已有 128 | 保留 128，不缩短 |
| 未显式指定长度 | 不因默认长度变化而修改旧列长度 |
| 主键缺失或主键标记不一致 | 仍报错，不自动增删主键 |
| SQLite 类型 affinity 不一致 | 明确提示不支持自动改类型，需手动重建表并迁移数据 |

数据库边界：

- MySQL 使用 `MODIFY COLUMN`，读取并保留已有非空、普通字面量默认值、注释，以及仍为文本时的字符集/排序规则；不以注解中的新 description 覆盖已有注释。要求连接启用严格 SQL 模式。含表达式默认值、ON UPDATE、不可见、UNSIGNED/ZEROFILL、ENUM/SET 等复杂属性的待修改列，保守拒绝自动修改。
- PostgreSQL 使用 `ALTER COLUMN TYPE ... USING CAST(...)`；H2 使用 `ALTER COLUMN ... SET DATA TYPE ...`。不主动删除默认值、注释、主键或索引来促成转换；默认值、约束、视图或依赖对象不能适配新类型时停止启动，请手动迁移。
- 三个支持的数据库均拒绝自动修改自增/标识列、生成列、引用外键或被外键引用的列。其他索引与约束交给数据库原生改列语句保留，数据库拒绝时不会自动删约束重试。
- 目标类型沿用现有实体映射，不新增 `columnDefinition` 或任意 `USING` 表达式配置；例如 String 改为 Long 才对应字符串转 BIGINT，BigDecimal 仍遵循各模块既有文本存储规则。

转换可能改变数据含义，例如 `"001"` 变成 `1`、小数转整数、时间解析、类型转换后唯一键冲突。需人工确认值域、格式、精度和唯一性，框架不保证无损。不执行同类字符串的长度缩短，但其他类型转换仍可能有精度/值域变化。

所有执行的 DDL 由 Session 先打印完整 SQL；改列失败的异常包含中文说明及对应 SQL，启动中止。大表可能锁表或重写数据，多列/多表修改不保证整体回滚，已经完成的 DDL 可能保留；修复后重新启动会读取实际结构，不做自动补偿。建议在维护窗口执行，完成迁移后关闭此开关。

<a id="ai-datasources-schema-表结构错误排查"></a>
### 表结构错误排查

MySQL、PostgreSQL、H2、SQLite 的结构冲突使用中文提示，列出数据库类型、表/列、原因、实体要求、数据库现有结构及处理方式，不再直接输出内部 `Column[...]` 对象。例如：

```text
数据库表结构检查失败
数据库类型：mysql
对象：orders.amount
原因：字段存储类型不同
实体要求：BIGINT，非主键
数据库现有：VARCHAR(255)，非主键
处理方式：自动改列未开启。请手动迁移；或确认数据可转换并备份后，
设置 yulinlin.mysql.alter-column-type: true，并确保
yulinlin.mysql.schema-mode: CREATE。修改类型可能锁表或改变数据精度
```

| 提示原因 | 处理方式 |
| --- | --- |
| 类型不同，或显式字符串容量不足 | 先核对实体是否正确。确需迁移时，备份并人工确认数据可转换，再手动改列或使用支持数据库的 `CREATE + alter-column-type=true` |
| 当前为 `VALIDATE` | 只检查，不执行 DDL；开启改列开关也不会修改。手动迁移，或明确切换 `CREATE` |
| 主键标记/字段集合不一致，缺失主键列 | 核对 `@JoinMeta(primaryKey=true)`；检查空值、重复值和外键后手动迁移。改列开关不负责增删主键 |
| 索引定义冲突 | 核对字段顺序、唯一性及索引形式；手动迁移，不会自动删除或替换已有索引 |
| SQLite 列类型不兼容 | 备份后手动重建表并迁移数据；改列开关不能解除 SQLite 的实现限制 |
| 自增、生成列、外键或特殊列属性 | 提示具体受限原因；使用迁移脚本处理，不会为了转换而删除约束 |
| 缺失表、普通列或索引 | `VALIDATE` 下手动补齐或切换 `CREATE`；`CREATE` 下仍缺失时检查实际数据库、权限及前面的 DDL 日志 |
| 同一张表存在多个自动建表实体 | 同一表只保留一个完整实体的 `autoSchema=true`；DTO 不开启；测试实体放在测试源码且位于业务扫描包之外 |
| DDL 执行失败 | 查看失败 SQL 与数据库原始错误，核对值域、格式、唯一性、默认值、约束和权限；检查此前 DDL 是否已生效再恢复 |

较长的已有 VARCHAR 不视为冲突：要求 32、已有 128 时继续使用，不缩短、不报错。提示中的类型是框架归一化后的存储类型；精确原始定义以数据库元数据/建表语句为准。`数据库类型` 不是自定义数据源 group，定位连接请结合 Session 的 `[group][schema]` 日志。

框架说明使用中文，数据库驱动原始错误原样保留，异常链及 SQLState/错误码也保留。结构 SQL 失败会中止初始化，但多条 DDL **不保证整体回滚**；不能将“重启失败”理解为“数据库完全没改动”。

<a id="ai-datasources-schema-预览建表-sql"></a>
### 预览建表 SQL

`EntitySession` 统一提供 `createTableSql(Class<?>)`。它只根据当前 Session 的数据库方言和字段命名设置生成 SQL，不获取连接、不执行 DDL；返回列表依次包含建表、列注释和声明索引。只有显式 `autoSchema=true` 的完整简单表实体会生成 DDL，投影、统计模型、null 和 Object.class 返回空列表：

```java
@Resource(name = "mysqlSession")
private EntitySession mysqlSession;

List<String> ddl = mysqlSession.createTableSql(DemoUser.class);
ddl.forEach(System.out::println);
```

`initializeSchema(Collection<Class<?>>)` 才执行初始化。MySQL、PostgreSQL、H2、SQLite 和 Elasticsearch 的启动扫描调用该 Session 接口；JDBC Session 自己管理连接和 Statement，并在执行每条语句前以 INFO 输出 `[group][schema]` 和完整 DDL。预览和实际初始化共用相同的结构描述函数。`schema-mode=NONE` 会跳过自动初始化，但不妨碍手动调用 `createTableSql` 预览。

<a id="ai-datasources-schema-schema-边界"></a>
### Schema 边界

这不是完整的版本迁移工具：`CREATE` 会创建缺失表、缺失普通列和缺失的声明索引；新增列通过 `ALTER TABLE ... ADD COLUMN` 执行。MySQL/PostgreSQL/H2 只有显式开启 `alter-column-type` 才允许上述受限的类型修改和扩容。它不会自动增加或修改主键，也不删除旧列/旧索引或添加外键、复合主键。框架无法识别字段改名：改名会被视为“增加新列”，旧列和旧数据仍保留，但数据不会自动搬到新列，因此改名必须使用业务迁移脚本。所有实际执行的 DDL 都会先以 INFO 日志打印，并且发生在会话 Bean 初始化阶段，不进入后续业务事务。`createTableSql` 只预览新建结构，不读取现有元数据，因此不是改列迁移计划。

SQLite 使用继承 JdbcSession 的 SqliteSession，复用公共 CRUD；不需要 DataSource Bean 或 sqliteTransactionManager。SqliteDatabase 持有并关闭内部连接池。`supportsParallelWrites()` 返回 false，多连接批处理不会拆组；WAL 也不能让同一文件同时拥有多个写事务。

SQLite 不支持框架生成的 FOR UPDATE、日期分组和数值间隔分组，当前解析器明确拒绝；需用自定义 SQL 实现数据库专用功能。运行中不要单独删除 -wal/-shm 文件，也不要仅复制主文件作为可靠备份。NORMAL 偏向性能，FULL 更重视持久性；这不是业务吞吐量承诺。

<a id="ai-datasources-schema-mysqlpostgresqlh2sqlite-自动索引"></a>
### MySQL/PostgreSQL/H2/SQLite 自动索引

在具体表实体上重复使用 `@JoinIndex`，fields 填 Java 属性名，顺序就是联合索引的列顺序。字段先经过 JoinField 和下划线映射，再生成数据库索引：

```java
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinIndex;
import com.yulinlin.data.core.anno.JoinTable;
import java.util.Date;

@JoinTable(value = "video_comment", autoSchema = true)
@JoinIndex(fields = {"videoId", "crtTime"})
@JoinIndex(fields = {"videoId", "userId"}, unique = true)
public class VideoComment {
    @JoinField(name = "video_id") private String videoId;
    @JoinField(name = "user_id") private String userId;
    @JoinField(name = "crt_time") private Date crtTime;
}
```

上例自动生成普通索引 `idx_video_comment_video_id_crt_time` 和唯一索引 `uk_video_comment_video_id_user_id`。用户不填写索引名；名称由类型前缀、表名和映射后的列名组成，统一小写，超过 63 个字符时截断并追加稳定的 8 位哈希。

同一有序字段组合不能重复声明；如果同时声明普通和唯一索引，只保留唯一索引。字段为空、重复、不存在、exist=false、关联字段或函数映射字段会在启动初始化时报错。索引注解只读取结构拥有者具体类上的声明，不继承父类索引；fields 仍可引用父类持久化字段。

已有同名索引会校验字段顺序、唯一性以及是否为普通升序列索引；不兼容时要求手动迁移。删除或修改注解不会自动删除旧索引，修改字段组合会创建新名称。MySQL/PostgreSQL/H2 使用独立启动连接；SQLite 在内部数据库会话初始化时执行。

---

下一步：返回[数据源主题目录](datasources/README.md)按需选择相关专题。

---

<a id="ai-datasources-h2"></a>
<!-- source: doc/datasources/h2.md -->
<a id="ai-datasources-h2-h2-接入"></a>
## H2 接入

[文档首页](README.md) / [数据源](datasources/README.md)

配置本地 H2 和连接并发。

共同规则：[自动建表与索引](#ai-datasources-schema) · [多数据源与默认组](#ai-datasources-routing) · [SQL 与事务日志](operations/sql-log.md)。

<a id="ai-datasources-h2-本页导航"></a>
### 本页导航

- [H2 接入](#ai-datasources-h2-h2-接入-1)

<a id="ai-datasources-h2-h2-接入-1"></a>
### H2 接入

H2 适合纯 Java 的本地文件存储，不需要数据库服务器或本机原生库。加入 starter + h2：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>starter</artifactId>
    <version>3.0</version>
</dependency>
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>h2</artifactId>
    <version>3.0</version>
</dependency>
```

引入即启用，默认数据库基路径为 `data/local`，H2 实际生成 `data/local.mv.db`；默认 group 是 `h2`。最小配置只需覆盖文件名：

```yaml
yulinlin:
  h2:
    file: data/app
```

完整配置及默认值：

```yaml
yulinlin:
  h2:
    file: data/local
    group: h2
    username: sa
    password: ""
    mode: MYSQL
    log: true
    map-underscore-to-camel-case: true
    parallel-connections: 4
    connection-timeout: 10s
    lock-timeout: 5s
    execute-batch-size: 256
    schema-mode: CREATE
    schema-packages:
      - demo.domain
    auto-server: false
```

file 是本地数据库基路径，不写 `.mv.db` 后缀，也不接受 JDBC URL 或内存 URL。mode 当前只接受 MYSQL，以复用框架普通 CRUD/分页语法；原始 SQL 仍应按 H2 实际能力编写。auto-server 仅用于确实需要多个 JVM 同时打开一个文件的场景，单进程保持 false。

模块创建 `h2SessionFactory`、`h2Session` 和 group `h2`。`H2Database` 持有连接池，但 DataSource 不注册成 Spring Bean，也不创建独立 TransactionManager。RouteSession、框架事务注解和显式 group 的用法与其他 JdbcSession 相同：

```java
var users = ModelSelectWrapper.newInstance("h2", DemoUser.class).selectList();
ModelInsertWrapper.newInstance("h2", user).execute();
```

H2 在 Session Bean 初始化时扫描配置包。schema-mode 的含义是：CREATE 创建缺失表、普通列和声明的索引并校验，VALIDATE 只校验且缺失时报错，NONE 完全跳过。自动增量只执行安全的 `ADD COLUMN`：不会自动增加或变更主键，不改已有列类型，也不删除旧列或旧索引。字段改名会被视为新增列，旧数据不会自动搬迁，必须使用版本迁移脚本。

普通字段默认映射为 BOOLEAN、INTEGER、BIGINT、DOUBLE PRECISION 或 CHARACTER VARYING；日期时间字段使用上文列出的 DATE、TIME、TIMESTAMP，`DateTime` / `LocalDateTime` 直接按无时区时间绑定。枚举、BigDecimal/BigInteger、byte[] 与 JSON 对象继续经过公共编码器存为文本；文本数字范围和统计需要显式 CAST。JSON 路径条件当前不自动生成，使用可信的 H2 自定义 SQL。

H2 的 DDL 会自动提交，因此启动创建表或索引使用独立直连连接，不会提交业务事务连接；相应地，这些结构变更也不随业务回滚。普通 CRUD、SELECT FOR UPDATE、日期/区间统计使用 H2 专用解析器。

H2 默认允许一个框架事务最多使用 4 个连接，并按 256 行调用一次 executeBatch；这适合本地批量吞吐，但多连接提交仍不是单连接原子事务。严格原子性或事务内读己之写时设置 parallel-connections: 1。connection-timeout 是取连接等待时间，lock-timeout 是数据库锁等待时间，都不是 SQL 查询执行超时。

---

下一步：返回[数据源主题目录](datasources/README.md)按需选择相关专题。

---

<a id="ai-datasources-local-choice"></a>
<!-- source: doc/datasources/local-choice.md -->
<a id="ai-datasources-local-choice-本地数据库选型"></a>
## 本地数据库选型

[文档首页](README.md) / [数据源](datasources/README.md)

先看简要取舍，完整数据与环境见性能报告。

<a id="ai-datasources-local-choice-本页导航"></a>
### 本页导航

- [SQLite/H2 选型](#ai-datasources-local-choice-sqliteh2-选型)

<a id="ai-datasources-local-choice-sqliteh2-选型"></a>
### SQLite/H2 选型

4 个业务线程、每次最多 128 行、总计 10 万行的 JMH 实测中，H2 为 89,401 行/秒，SQLite 为 45,456 行/秒；SQLite 的累计内存分配更低。多线程小批量且吞吐优先选 H2，单文件和写入可排队选 SQLite。测试口径、完整指标、限制和复现命令见[本地数据库性能报告](operations/local-benchmark.md)。

---

下一步：返回[数据源主题目录](datasources/README.md)按需选择相关专题。

---

<a id="ai-datasources-postgresql"></a>
<!-- source: doc/datasources/postgresql.md -->
<a id="ai-datasources-postgresql-postgresql-接入"></a>
## PostgreSQL 接入

[文档首页](README.md) / [数据源](datasources/README.md)

接入 PostgreSQL 与中文全文检索前置配置。

共同规则：[自动建表与索引](#ai-datasources-schema) · [多数据源与默认组](#ai-datasources-routing) · [SQL 与事务日志](operations/sql-log.md)。

<a id="ai-datasources-postgresql-本页导航"></a>
### 本页导航

- [PostgreSQL 接入](#ai-datasources-postgresql-postgresql-接入-1)
- [PostgreSQL 中文全文检索](#ai-datasources-postgresql-postgresql-中文全文检索)

<a id="ai-datasources-postgresql-postgresql-接入-1"></a>
### PostgreSQL 接入

最小依赖只需 postgresql，公共 jdbc/core/lang 会传递引入；不依赖 mysql 模块或 MySQL 驱动：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>postgresql</artifactId>
    <version>3.0</version>
</dependency>
```

需要 Model Wrapper 时再加入 common 或 starter。示例配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/demo?stringtype=unspecified
    username: ${PG_USERNAME}
    password: ${PG_PASSWORD}
    driver-class-name: org.postgresql.Driver

yulinlin:
  postgresql:
    log: true
    map-underscore-to-camel-case: true
    parallel-connections: 4
    execute-batch-size: 256
    schema-mode: CREATE
    schema-packages:
      - demo.domain
    full-text:
      index-config: jiebacfg
      query-config: jiebaqry
      max-words: 40
      min-words: 15
    highlight:
      start-tag: __HL_START__
      end-tag: __HL_END__
      max-fragments: 2
      fragment-delimiter: "..."
```

模块创建 `postgresqlSessionFactory` 和 `postgresqlSession`，默认 group 为 `postgresql`，实际会话类型为 PostgresqlSession。`schema-mode` 默认 `NONE`；设为 `CREATE` 后会扫描实体并自动创建缺失表、普通列和声明索引，`VALIDATE` 只校验。`@JoinTable` 可使用普通表名，也可使用 `schema.table`，两段都会独立引用。

现有编码器可能把枚举、BigDecimal 或对象编码为字符串；已支持的日期时间类型由 JDBC 编码器以对应类型绑定，`DateTime` / `LocalDateTime` 不依赖 `stringtype=unspecified`。该参数让驱动按目标列或 SQL 上下文推断字符串类型；它不是框架自动添加的参数，也不解决所有类型问题。无明确类型上下文时可在可信 SQL 中显式 CAST，例如 `CAST(#{payload} AS jsonb)`。不要推定原生 enum、UUID、数组、空间类型已有完整专用编解码支持。

标识符采用双引号，schema 限定名按段引用；实际列名应与模型映射一致。普通 CRUD 和实体可复用，原始 MySQL SQL、反引号、函数与 DDL 不会被自动翻译。自动 Schema 不负责序列、自增键、外键、分区表或数据迁移。

JSON 读取支持合法 JSON 的路径，例如 `payload->profile->name`；数字/布尔条件按比较值生成 CAST。路径部分更新尚未自动生成 jsonb_set，应整字段替换或写专用 SQL。非法 JSON 和不能转换的值会报错，不是静默跳过。

JDBC 参数统一使用 `setObject`，业务参数不应直接携带 InputStream、bytea/oid 等二进制对象；需要保存时应先由明确的编解码策略转成文本，或把文件放到对象存储后只保存地址。原生布尔列保留 false/NULL；除布尔列外结果大多沿用字符串解码，不能推定 timestamptz 等扩展类型完整往返已验证。

<a id="ai-datasources-postgresql-postgresql-中文全文检索"></a>
#### PostgreSQL 中文全文检索

全文检索是 PostgreSQL 模块的可选能力，中文分词依赖数据库服务器已安装 `pg_jieba`。框架不会下载或安装 PostgreSQL 扩展二进制；运维先按服务器版本安装插件，再由有权限的账号执行：

```sql
CREATE EXTENSION IF NOT EXISTS pg_jieba;
```

默认索引配置是 `jiebacfg`，查询配置是 `jiebaqry`。只有结构拥有者实体上显式声明 `@JoinField(fullText = true)` 的文本列才生成 GIN 表达式索引。`schema-mode=CREATE` 会创建缺失索引，`VALIDATE` 只校验；初始化遇到配置不存在时会直接说明需要安装/启用 pg_jieba。修改 index-config 会使用新的索引名称创建新索引，旧索引不会自动删除。

`index-config` 同时用于 `to_tsvector`、GIN 索引和 `ts_headline`，不能随意设置为不同分词配置；`query-config` 用于 `websearch_to_tsquery`。`max-words/min-words` 是 PostgreSQL 专属摘要长度；高亮标签、片段数和分隔符来自所有 EntitySession 共用的 `highlight` 配置，并作为 JDBC 参数绑定，不拼接用户输入。默认标记不是 HTML，前端可安全地按 `__HL_START__` / `__HL_END__` 转义后渲染；若改成 `<mark>`，仍须遵循项目的 XSS 输出策略。查询用法见[全文匹配与高亮](#ai-orm-search-全文匹配与高亮)。

MySQL、SQLite、H2 当前不会创建全文索引：`fullText=true` 被忽略，`match()` 退化为包含式 LIKE，`highlight()` 返回原字段。这保证同一业务调用可以运行，但不代表大表查询具有全文检索性能。

---

下一步：返回[数据源主题目录](datasources/README.md)按需选择相关专题。

---

<a id="ai-datasources-elasticsearch"></a>
<!-- source: doc/datasources/elasticsearch.md -->
<a id="ai-datasources-elasticsearch-elasticsearch-接入"></a>
## Elasticsearch 接入

[文档首页](README.md) / [数据源](datasources/README.md)

索引与 Mapping 初始化、配置和能力边界。

<a id="ai-datasources-elasticsearch-本页导航"></a>
### 本页导航

- [Elasticsearch 结构初始化](#ai-datasources-elasticsearch-elasticsearch-结构初始化)

<a id="ai-datasources-elasticsearch-elasticsearch-结构初始化"></a>
### Elasticsearch 结构初始化

Elasticsearch 与 JDBC 模块复用 core 的实体扫描和 Schema 配置。默认 `schema-mode=NONE`，不会访问或修改索引；启用时配置：

```yaml
yulinlin:
  elasticsearch:
    url: http://localhost:9200
    log: true
    map-underscore-to-camel-case: true
    schema-mode: CREATE
    schema-packages:
      - com.example.search.entity
    highlight:
      start-tag: "<mark>"
      end-tag: "</mark>"
      max-fragments: 2
      fragment-delimiter: "..."
```

模块使用 Elasticsearch Java Client `9.5.4` 和其默认 `Rest5Client`。`CREATE` 会创建缺失索引和基础 Mapping，并给已有索引补充缺失字段；`VALIDATE` 只检查索引、字段和字段类型，不修改结构。普通字符串映射为 `keyword`，`@JoinField(fullText = true)` 或 `textType = text` 映射为 `text`；复杂 JSON 对象保留 Elasticsearch 动态映射。已存在字段类型不一致时不会强制修改，而是要求手工迁移索引。查询调用 `.highlight(field)` 后使用公共标签与片段数，并按 `fragment-delimiter` 合并返回的多个片段到原字段。

---

下一步：返回[数据源主题目录](datasources/README.md)按需选择相关专题。

---

<a id="ai-datasources-routing"></a>
<!-- source: doc/datasources/routing.md -->
<a id="ai-datasources-routing-多数据源与默认组"></a>
## 多数据源与默认组

[文档首页](README.md) / [数据源](datasources/README.md)

注册多个数据源，使用 group 或 JoinSession 选择路由。

<a id="ai-datasources-routing-本页导航"></a>
### 本页导航

- [多数据源注册](#ai-datasources-routing-多数据源注册)
- [两个 MySQL 数据源](#ai-datasources-routing-两个-mysql-数据源)
- [MySQL 与 PostgreSQL 共存](#ai-datasources-routing-mysql-与-postgresql-共存)
- [默认会话组](#ai-datasources-routing-默认会话组)

<a id="ai-datasources-routing-多数据源注册"></a>
### 多数据源注册

三个名字不要混用：

| 名称 | 作用 |
| --- | --- |
| DataSource Bean 名 | Spring 注入具体连接池 |
| Session Bean 名 | 容器管理会话对象 |
| group | 框架运行请求时选库 |

工厂 `create(dataSource, group)` 返回已配置的 Session，并继承该工厂所属模块的配置，但不注册路由。把它声明为 EntitySession Bean，core 就会收集到 RouteSession；无需在 Bean 创建方法中反向注入 RouteSession。某个同类 Session 需要独立参数时，可调用 `create(dataSource, group, properties)` 显式覆盖。

<a id="ai-datasources-routing-两个-mysql-数据源"></a>
#### 两个 MySQL 数据源

下面是完整配置类。应用提供 spring.datasource 和 oss.datasource 两套 DataSourceProperties 配置；main 数据源标记 @Primary，使模块默认 mysqlSession 使用它：

```java
package demo.config;

import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class MultiDataSourceConfig {
    @Bean("mainProperties")
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mainProperties() { return new DataSourceProperties(); }

    @Bean("dataSource")
    @Primary
    public DataSource mainDataSource(@Qualifier("mainProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("ossProperties")
    @ConfigurationProperties("oss.datasource")
    public DataSourceProperties ossProperties() { return new DataSourceProperties(); }

    @Bean("ossDataSource")
    public DataSource ossDataSource(@Qualifier("ossProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("ossSession")
    public JdbcSession ossSession(@Qualifier("mysqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("ossDataSource") DataSource dataSource) {
        return factory.create(dataSource, "oss");
    }
}
```

Boot 可能因为额外的 DataSource Bean 不再创建原默认源，因此例子明确声明两个池，而不是只声明第二个源。

<a id="ai-datasources-routing-mysql-与-postgresql-共存"></a>
#### MySQL 与 PostgreSQL 共存

模块不检查 JDBC URL。两边自动配置可能注入同一个 @Primary DataSource；默认 group 不同不代表已绑定不同物理库。

以下完整配置类假设 MySQL 的 dataSource 与 PostgreSQL 的 pgDataSource 已正确创建。主会话增加兼容名 jdbcSession，让双方默认会话创建退让；别名 mysqlSession 指向同一个对象：

```java
package demo.config;

import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class MixedSessionConfig {
    @Bean(name = {"jdbcSession", "mysqlSession"})
    public JdbcSession mysqlSession(@Qualifier("mysqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("dataSource") DataSource dataSource) {
        return factory.create(dataSource, "mysql");
    }

    @Bean("postgresqlSession")
    public JdbcSession postgresqlSession(@Qualifier("postgresqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("pgDataSource") DataSource dataSource) {
        return factory.create(dataSource, "postgresql");
    }
}
```

MySQL 与 SQLite 共存时，保留原主库配置即可，SQLite 使用内部池和独立 sqlite 组。多个主 DataSource 没有唯一候选时，全部会话显式创建，不任意猜测。

使用容器管理的 JdbcSessionFactory：`create(...)` 返回前已初始化编码器、解析器、代理服务、日志、配置、group 和缓存；未引入缓存实现时使用空缓存，不影响启动。RouteSession 注册时只登记节点，不再补充或覆盖 Session 配置；过滤器只由 RouteSession 执行。不要把手动 `new JdbcSessionFactory(...)` 当成已完成容器注入的工厂。`create(DataSourceProperties, group)` 会新建池，资源所有者仍须负责关闭，不自动等价于独立 DataSource Bean。

<a id="ai-datasources-routing-默认会话组"></a>
### 默认会话组

只注册一个 group 时，无组参数请求自动使用它；有多个 group 时设置应用共享 LoadBalance 的默认组：

```yaml
yulinlin:
  datasource:
    default-group: mysql
```

代码设置的业务方法体片段，loadBalance 为注入的 `com.yulinlin.data.core.loadbalan.LoadBalance`：

```java
loadBalance.setDefaultGroup("mysql");
String configured = loadBalance.getDefaultGroup();
String effective = loadBalance.defaultGroup();
```

getDefaultGroup 返回配置值，defaultGroup 返回实际默认组；单组时配置并非必需。多组未配置或默认组不存在会报错。primary 是普通组名，不自动优先。

路由优先级为：请求显式 group → 方法 `@JoinSession` → Bean 类或 Repository 接口 `@JoinSession` → 模型 `@JoinSession` → 当前普通会话上下文 → 负载均衡默认组。选择方式举例，DemoUser 定义见[最小实体](#ai-orm-entity)：

```java
ModelSelectWrapper.newInstance("oss", DemoUser.class).selectList();
```

也可在 Spring Service、Repository 接口或其方法上使用 `com.yulinlin.data.core.anno.JoinSession("oss")`，必须通过 Spring Bean 调用。方法注解覆盖类或接口注解，内层调用结束后恢复外层路由；框架默认 LoadBalance Bean 会绑定 YAML，自定义 Bean 应自行配置。

单个可用节点直接返回，但仍检查 cluster、正权重和健康状态；同组多节点继续按权重选择。健康缓存不会把离线默认库悄悄切到另一个组。运行时修改默认组或移除资源应协调在途事务。

---

下一步：返回[数据源主题目录](datasources/README.md)按需选择相关专题。

---

<a id="ai-datasources-config"></a>
<!-- source: doc/datasources/config.md -->
<a id="ai-datasources-config-数据源配置速查"></a>
## 数据源配置速查

[文档首页](README.md) / [数据源](datasources/README.md)

查询配置入口与接入限制，完整示例见各数据库页面。

<a id="ai-datasources-config-本页导航"></a>
### 本页导航

- [配置速查](#ai-datasources-config-配置速查)
- [接入边界与排障](#ai-datasources-config-接入边界与排障)

<a id="ai-datasources-config-配置速查"></a>
### 配置速查

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| yulinlin.datasource.default-group | 未设置 | 单组自动；多组指定默认 |
| yulinlin.data.transaction-log | false | 全局事务诊断；记录嵌套、数据源连接提交/回滚以及 Spring 最终结果，独立于 SQL 日志 |
| yulinlin.monitor.enabled | true | 是否启用数据库操作采集及内置监控接口和页面；关闭后不注册相关 Bean |
| yulinlin.monitor.threshold | 1s | 慢操作明细保留阈值，同时作为 P1–P4 优先级的计算基准；异常始终保留 |
| yulinlin.monitor.max-records | 1000 | 慢操作明细和 SQL 聚合组各自的内存上限；超出后淘汰最早或最久未访问的数据 |
| yulinlin.monitor.username | admin | 内置监控对话框账号 |
| yulinlin.monitor.password | 空 | 内置监控对话框密码；必须显式配置，推荐使用环境变量 |
| yulinlin.`<module>`.log | false | 当前模块 SQL 成功日志；module 为 mysql、postgresql、sqlite 或 h2 |
| yulinlin.`<module>`.map-underscore-to-camel-case | true | 当前模块下划线与驼峰映射 |
| yulinlin.`<module>`.parallel-connections | 4 | 当前模块每框架事务连接上限；SQLite 最终固定为 1 |
| yulinlin.`<module>`.execute-batch-size | 256 | 当前模块一次 executeBatch 的行数，不是 commit |
| yulinlin.mysql.schema-mode | NONE | CREATE、VALIDATE 或 NONE；默认不改生产结构 |
| yulinlin.mysql.schema-packages | 空 | MySQL 启动递归扫描包列表 |
| yulinlin.postgresql.schema-mode | NONE | CREATE、VALIDATE 或 NONE；默认不改生产结构 |
| yulinlin.postgresql.schema-packages | 空 | PostgreSQL 启动递归扫描包列表 |
| yulinlin.postgresql.full-text.index-config | jiebacfg | PostgreSQL 建索引、匹配和高亮使用的分词配置 |
| yulinlin.postgresql.full-text.query-config | jiebaqry | PostgreSQL 查询分词配置 |
| yulinlin.postgresql.full-text.max-words/min-words | 40 / 15 | PostgreSQL ts_headline 摘要长度 |
| yulinlin.`<module>`.highlight.start-tag/end-tag | `__HL_START__` / `__HL_END__` | PostgreSQL/Elasticsearch 返回到原字段中的高亮边界 |
| yulinlin.`<module>`.highlight.max-fragments | 2 | 最大高亮片段数，必须大于 0 |
| yulinlin.`<module>`.highlight.fragment-delimiter | `...` | 返回多个片段时的合并文本 |
| yulinlin.sqlite.file | data/local.db | 进程工作目录下的路径 |
| yulinlin.sqlite.group | sqlite | 本地会话组 |
| yulinlin.sqlite.busy-timeout | 5000 ms | 等锁，不是查询超时 |
| yulinlin.sqlite.synchronous | NORMAL | 可选 FULL |
| yulinlin.sqlite.schema-mode | CREATE | CREATE、VALIDATE 或 NONE |
| yulinlin.sqlite.schema-packages | 空 | SQLite 启动递归扫描包列表 |
| yulinlin.h2.file | data/local | 数据库基路径，实际文件追加 .mv.db |
| yulinlin.h2.group | h2 | H2 会话组 |
| yulinlin.h2.username/password | sa / 空 | 内部文件库凭据 |
| yulinlin.h2.mode | MYSQL | 当前唯一支持的兼容模式 |
| yulinlin.h2.connection-timeout | 10s | 连接池取连接等待时间 |
| yulinlin.h2.lock-timeout | 5s | H2 锁等待时间 |
| yulinlin.h2.schema-mode | CREATE | CREATE、VALIDATE 或 NONE |
| yulinlin.h2.schema-packages | 空 | H2 启动递归扫描包列表 |
| yulinlin.h2.auto-server | false | 是否允许多 JVM 打开同一文件 |
| yulinlin.elasticsearch.log | false | Elasticsearch 成功请求日志 |
| yulinlin.elasticsearch.url | http://localhost:9200 | Elasticsearch 服务地址 |
| yulinlin.elasticsearch.map-underscore-to-camel-case | true | Elasticsearch 实体字段命名转换 |
| yulinlin.elasticsearch.schema-mode | NONE | CREATE、VALIDATE 或 NONE |
| yulinlin.elasticsearch.schema-packages | 空 | Elasticsearch 启动递归扫描实体包 |

<a id="ai-datasources-config-接入边界与排障"></a>
### 接入边界与排障

- 单数据源也必须等待容器初始化，不在静态初始化块里查询。所有模块只有配置扫描包并启用相应模式时才处理结构；SQLite/H2 默认模式为 CREATE，MySQL/PostgreSQL/Elasticsearch 默认为 NONE。未扫描的结构由业务迁移工具准备。
- 不把独立库无意注册为同组节点；同组是负载均衡，不是覆盖注册。
- 多库事务与多连接事务不是 XA，详细规则见 [ORM 开发](#ai-relations-transactions-事务使用)。
- 未发现会话：检查数据库模块、自动配置 imports、DataSource 候选、Session Bean 和 group。
- 表或字段不存在：核对实际建表、JoinField 映射和实体基类继承字段。
- PostgreSQL 报 MySQL 函数错误：检查是否使用了 mysqlSessionFactory 或原始 MySQL SQL。
- SQLite 原生库在 JDK 25 下可能提示 native-access 警告；部署时按实际环境配置 JVM 原生访问权限，不把警告当成数据库初始化成功的证明。
- H2 表已存在但字段报不兼容：框架不会迁移旧结构，手动迁移后重启或重建 Session；不要直接删除正在使用的 .mv.db 文件。

内部 Session 创建与扩展规则见 [内部维护](internal/README.md)。

---

下一步：返回[数据源主题目录](datasources/README.md)按需选择相关专题。

---

<a id="ai-orm-entity"></a>
<!-- source: doc/orm/entity.md -->
<a id="ai-orm-entity-实体映射与最小模型"></a>
## 实体映射与最小模型

[文档首页](README.md) / [ORM 开发](orm/README.md)

定义 DemoUser，后续 CRUD 示例沿用这个模型。

<a id="ai-orm-entity-本页导航"></a>
### 本页导航

- [最小实体和配套表](#ai-orm-entity-最小实体和配套表)

<a id="ai-orm-entity-最小实体和配套表"></a>
### 最小实体和配套表

以下完整类放在 `demo/domain/DemoUser.java`，提供无参构造和 getter/setter。IdEntity 提供 String 主键及插入前的应用侧 ID 生成。

```java
package demo.domain;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;

@JoinTable(value = "ai_demo_user", autoSchema = true)
public class DemoUser extends IdEntity<DemoUser> {
    @JoinField(name = "user_name")
    @JoinWhere
    private String username;

    @JoinField
    @JoinWhere
    private Integer status;

    public DemoUser() { }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
```

MySQL/PostgreSQL/SQLite/H2 若已按[Schema 专题](#ai-datasources-schema)配置启动扫描，DemoUser 由应用启动阶段创建/校验；否则必须提前准备。下列 DDL 只在自己的示例数据库执行，不把它当作生产迁移脚本：

```sql
CREATE TABLE ai_demo_user (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    user_name VARCHAR(100),
    status INT
);
```

SuperEntity 在 IdEntity 上增加 crtTime、uptTime 和填充逻辑，使用它时表必须有对应列。非持久化属性显式用 `@JoinField(exist = false)`，不能依赖“没有注解就一定忽略”。

| 注解 | 用途 |
| --- | --- |
| JoinTable | 表或 SQL JOIN 映射；autoSchema 默认 false，只有完整结构实体明确设为 true 才参与启动 Schema |
| JoinField(name = "...") | Java 属性与列名映射 |
| JoinField(textLength = 120) | 自动 Schema 的 VARCHAR 字符上限；字符串主键默认 32，显式长度按注解，仍受数据库限制 |
| JoinField(textType = TextTypeEnum.text) | 自动 Schema 使用大文本；不能作为主键或框架声明索引 |
| JoinField(description = "...") | 列用途说明；MySQL/PostgreSQL/H2 新表或新列写入注释，SQLite 不落库 |
| JoinField(fullText = true) | PostgreSQL 为该文本列维护 pg_jieba GIN 全文索引；其他数据库忽略 |
| JoinField(exist = false) | 排除非数据库列 |
| JoinField(update = false) | 排除更新字段 |
| JoinMeta(primaryKey = true) | 主键元信息；不是 JoinPrimary |
| JoinIndex(fields = {...}) | MySQL/PostgreSQL/H2/SQLite 启动维护普通或唯一联合索引；字段写 Java 属性名 |
| JoinWhere | 对象属性有值时参与条件 |
| JoinField(version = true) | 版本字段；具体支持路径按代理与实际更新实现核对 |

自定义主键建议同时声明 JoinField、JoinMeta、JoinWhere。重命名主键列时要检查删除、部分更新等实际映射，不仅查看 SELECT SQL。

---

下一步：返回[ORM 开发主题目录](orm/README.md)按需选择相关专题。

---

<a id="ai-orm-crud"></a>
<!-- source: doc/orm/crud.md -->
<a id="ai-orm-crud-插入更新与删除"></a>
## 插入、更新与删除

[文档首页](README.md) / [ORM 开发](orm/README.md)

完整 CRUD Service，以及写入和查询缓存的交互。

示例使用的基础模型见[实体映射与最小模型](#ai-orm-entity)；数据库配置见[数据源](datasources/README.md)。

<a id="ai-orm-crud-本页导航"></a>
### 本页导航

- [CRUD 完整服务](#ai-orm-crud-crud-完整服务)
- [新增用法](#ai-orm-crud-新增用法)
- [更新用法](#ai-orm-crud-更新用法)
- [删除用法](#ai-orm-crud-删除用法)
- [写操作与查询缓存](#ai-orm-crud-写操作与查询缓存)

<a id="ai-orm-crud-crud-完整服务"></a>
### CRUD 完整服务

下面完整 Service 使用 mysql 组。对应表已存在、容器已初始化；通过 Spring 代理调用带事务的方法：

```java
package demo.service;

import demo.domain.DemoUser;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.common.model.ModelDeleteWrapper;
import com.yulinlin.data.lang.util.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoUserService {
    public DemoUser findById(String id) {
        requireId(id);
        return ModelSelectWrapper.newInstance("mysql", DemoUser.class)
                .eq("id", id).selectOne();
    }

    public Page<DemoUser> page(int page, int size) {
        if (page < 1 || size < 1 || size > 200) throw new IllegalArgumentException("Invalid page");
        return ModelSelectWrapper.newInstance("mysql", DemoUser.class)
                .eq("status", 1).orderByDesc("id").selectPage(page, size);
    }

    @Transactional
    public String create(String username) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username is required");
        DemoUser user = new DemoUser();
        user.setUsername(username);
        user.setStatus(1);
        ModelInsertWrapper.newInstance("mysql", user).execute();
        return user.getId();
    }

    @Transactional
    public int changeStatus(String id, int status) {
        requireId(id);
        DemoUser patch = new DemoUser();
        patch.setId(id);
        patch.setStatus(status);
        return ModelUpdateWrapper.newInstance("mysql", patch).execute();
    }

    @Transactional
    public int delete(String id) {
        requireId(id);
        return ModelDeleteWrapper.newInstance("mysql", DemoUser.class).eq("id", id).execute();
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
    }
}
```

实体也提供 `createSelectWrapper()`、`createInsertWrapper()`、`createUpdateWrapper()`、`createDeleteWrapper()`。不指定组的入口遵循全局默认组选取；多组时不要靠注册顺序。

<a id="ai-orm-crud-新增用法"></a>
#### 新增用法

以下新增、更新和删除代码均为业务方法体片段，`List` 使用 `java.util.List`，实体沿用上文 DemoUser。

单条新增直接传实体；IdEntity 会在插入前生成 ID，execute 返回数据库报告的影响行数：

```java
DemoUser user = new DemoUser();
user.setUsername("alice");
user.setStatus(1);

int affected = ModelInsertWrapper.newInstance("mysql", user).execute();
String generatedId = user.getId();
```

同一批数据传集合，默认复用 JDBC batch；是否使用多连接由调用方显式决定：

```java
List<DemoUser> users = List.of(firstUser, secondUser);
int affected = ModelInsertWrapper.newInstance("mysql", users).execute();
```

大批量才考虑 `.batch()`；连接数量、提交大小和原子性限制见[批量与多连接写入](#ai-write-sync-batch-批量与多连接写入)。

<a id="ai-orm-crud-更新用法"></a>
#### 更新用法

对象更新适合“按主键修改非 null 字段”。主键生成 WHERE，其他非 null、可更新字段生成 SET：

```java
DemoUser patch = new DemoUser();
patch.setId(id);
patch.setStatus(2);

int affected = ModelUpdateWrapper.newInstance("mysql", patch).execute();
if (affected != 1) throw new IllegalStateException("User does not exist: " + id);
```

需要明确控制 SET 和 WHERE 时，以实体类型创建 Wrapper：

```java
int affected = ModelUpdateWrapper.newInstance("mysql", DemoUser.class)
        .field("status", 2)
        .eq("id", id)
        .execute();
```

数值字段支持原子增减；下面只是语法示例，status 必须确实适合做数值累加：

```java
int affected = ModelUpdateWrapper.newInstance("mysql", DemoUser.class)
        .inc("status", 1)
        .eq("id", id)
        .execute();

int restored = ModelUpdateWrapper.newInstance("mysql", DemoUser.class)
        .dec("status", 1)
        .eq("id", id)
        .execute();
```

集合更新会为每个对象建立更新节点。每个对象都应携带完整非空主键，只设置真正需要修改的字段：

```java
DemoUser first = new DemoUser();
first.setId("user-1");
first.setStatus(1);

DemoUser second = new DemoUser();
second.setId("user-2");
second.setStatus(2);

int affected = ModelUpdateWrapper.newInstance("mysql", List.of(first, second)).execute();
```

普通对象更新、自动更新代理和显式 `.field(name, null)` 都跳过 null；它们不能把数据库列清成 NULL。需要清空字段时使用参数化的[自定义写入](#ai-orm-raw-sql-自定义写入)。`@JoinField(update = false)` 字段不参与对象更新；version 字段按当前值加入条件并递增，仍应检查影响行数判断并发冲突。

<a id="ai-orm-crud-删除用法"></a>
#### 删除用法

按实体主键删除时，只设置主键即可：

```java
DemoUser key = new DemoUser();
key.setId(id);
int affected = ModelDeleteWrapper.newInstance("mysql", key).execute();
```

按条件删除可以直接使用属性名或 Lambda 条件：

```java
int affected = ModelDeleteWrapper.newInstance("mysql", DemoUser.class)
        .eq("id", id)
        .execute();
```

按主键集合删除前必须处理空集合：

```java
public int deleteByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) return 0;
    return ModelDeleteWrapper.newInstance("mysql", DemoUser.class)
            .in("id", ids)
            .execute();
}
```

也可以传入只包含主键的实体集合，生成多条删除节点：

```java
DemoUser first = new DemoUser();
first.setId("user-1");
DemoUser second = new DemoUser();
second.setId("user-2");

int affected = ModelDeleteWrapper.newInstance("mysql", List.of(first, second)).execute();
```

框架不默认拦截全表写入。`ModelDeleteWrapper.newInstance("mysql", DemoUser.class).execute()` 没有 WHERE，带 SET 但没有 WHERE 的更新也可能影响整表；业务入口必须先校验主键、集合和条件。execute 返回影响行数，单条更新/删除通常应检查是否为 1。

<a id="ai-orm-crud-写操作与查询缓存"></a>
#### 写操作与查询缓存

新增、更新和删除在数据库提交成功后，默认清理本次写入涉及的查询缓存 namespace。全局默认值如下：

```yaml
yulinlin:
  cache:
    clear-on-write: true
```

单次写操作可以覆盖全局配置；`clearCache`只控制 ORM 查询缓存，不会删除通过 `CacheClient` 保存的业务缓存：

```java
int affected = ModelUpdateWrapper.newInstance("mysql", patch)
        .clearCache(false) // 本次写入不清理查询缓存；未调用时继承全局配置
        .execute();
```

写 Wrapper 只提供 `clearCache(boolean)`，不再提供写操作版 `cache(...)`、`invalidate(...)` 或 `invalidateAll()`。一般业务保留默认 `true`；只有明确接受数据在 TTL 内短暂陈旧时才关闭。回滚或执行失败不清理缓存。`clear-on-write` 属于可选缓存 Provider；未引入 `cache-caffeine`、`cache-ehcache` 或 `cache-redis` 时不会启用该配置，写路径也不会解析失效 namespace。详细规则见[查询缓存专题](#ai-cache-invalidation-失效和一致性)。

---

下一步：返回[ORM 开发主题目录](orm/README.md)按需选择相关专题。

---

<a id="ai-orm-query"></a>
<!-- source: doc/orm/query.md -->
<a id="ai-orm-query-查询条件分页与结果"></a>
## 查询条件、分页与结果

[文档首页](README.md) / [ORM 开发](orm/README.md)

使用 Wrapper 与查询条件对象构建页面查询。

示例使用的基础模型见[实体映射与最小模型](#ai-orm-entity)；数据库配置见[数据源](datasources/README.md)。

<a id="ai-orm-query-本页导航"></a>
### 本页导航

- [查询条件与结果组织](#ai-orm-query-查询条件与结果组织)
- [查询条件对象](#ai-orm-query-查询条件对象)

<a id="ai-orm-query-查询条件与结果组织"></a>
### 查询条件与结果组织

以下为业务方法体片段，所用 DemoUser 已在上文定义：

```java
var query = ModelSelectWrapper.newInstance("mysql", DemoUser.class)
        .eq(DemoUser::getStatus, 1)
        .like(DemoUser::getUsername, "alice")
        .in("id", java.util.List.of("1", "2"))
        .orderByAsc("username");
var users = query.selectList();
```

| 入口 | 行为 |
| --- | --- |
| eq / ne / gt / gte / lt / lte | 比较条件，可用属性字符串或 Lambda |
| like / likeRight | 模糊条件 |
| match | 全文匹配；PostgreSQL 的 fullText 字段使用分词索引，其他 SQL 数据源退化为包含式 LIKE |
| in | 集合条件 |
| between | 范围；字符串字段与列类型的排序规则需要一致 |
| isNull | SQL NULL 条件 |
| and / not / where | 条件组合；按实际 Wrapper 接口使用 |
| selectOne | 无结果返回 null；取第一条，不自动保证唯一 |
| selectList / count | 列表 / 计数 |
| selectPage(page, size) | 数据库分页，调用前校验页码和大小 |
| selectByMap("id") | Java 端索引，重复键由后项覆盖 |
| selectByGroup("status") | Java 端组织结果，不是 SQL GROUP BY |

<a id="ai-orm-query-查询条件对象"></a>
#### 查询条件对象

业务页面查询优先定义 `XxxPo extends PagePo`，在子类业务字段上声明 `@JoinWhere`，一次复用条件、分页与排序。完整的 `PayChannelPo`、Controller 和 `ISuperService.page(po)`示例见[Common 业务开发：页面查询对象](#ai-practice-models-页面查询对象pagepo--joinwhere)。本节只解释对象条件如何转换为 WHERE，也适用于不分页的独立查询 DTO。

直接把实体作为查询条件：

```java
DemoUser filter = new DemoUser();
filter.setStatus(1); // 因为字段有 JoinWhere，参与条件。
var users = ModelSelectWrapper.newInstance("mysql", filter).selectList();
```

实体类型和查询参数也可以分开。推荐 Controller 接收独立查询 DTO，再把它作为第三个参数传入：

```java
package demo.query;

import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinWhere;
import lombok.Data;

import java.util.List;

@Data
public class UserQuery {
    @JoinWhere(name = "id", condition = ConditionEnum.in)
    private List<String> ids;

    @JoinWhere(name = "username", condition = ConditionEnum.like)
    private String keyword;

    @JoinWhere(name = "status", condition = ConditionEnum.between)
    private List<Integer> statusRange;
}
```

```java
UserQuery condition = new UserQuery();
condition.setIds(List.of("user-1", "user-2"));
condition.setKeyword("alice");
condition.setStatusRange(List.of(1, 3));

List<DemoUser> users = ModelSelectWrapper
        .newInstance("mysql", DemoUser.class, condition)
        .orderByDesc("crtTime")
        .selectList();
```

这里 `DemoUser.class` 决定查询表、返回字段和结果类型，`condition` 只负责生成 WHERE。查询 DTO 不需要 `@JoinTable`，但必须提供可读取的 getter。`@JoinWhere.name` 写目标实体的 Java 属性名；省略时使用查询 DTO 自己的属性名。`condition` 可使用 `eq/ne/gt/gte/lt/lte/like/likeRight/match/in/between/isNull`。

只有带 `@JoinWhere` 的属性才会参与查询。`null`、空字符串和空集合会被忽略；`between` 传两个元素的集合，`in` 传集合。`isNull` 条件需要一个非 null 的启用字段，例如 Boolean 值为 true 时生成目标列的 `IS NULL`，值为 null 时不生成该条件。生成对象条件后仍可继续调用 `.eq()`、`.orderByDesc()`、`.page()` 等链式方法追加条件和排序。

`JoinWhere.and()` 默认为 `true`。所有 `and=true` 的字段进入同一个 AND 分组；`and=false` 的字段进入外层 OR 分组。混合 AND/OR 时应先确认期望的括号语义，复杂嵌套条件优先使用 Wrapper 的 `.where(...)`、`.and(...)`、`.or(...)`，不要只靠查询 DTO 猜测组合顺序。

不是所有非 null 属性都自动成为 WHERE 条件。字符串条件优先使用 Java 属性名，使 JoinField 映射生效。没有默认的全表写入保护；UPDATE/DELETE 前业务必须验证主键或条件。

---

下一步：返回[ORM 开发主题目录](orm/README.md)按需选择相关专题。

---

<a id="ai-orm-search"></a>
<!-- source: doc/orm/search.md -->
<a id="ai-orm-search-全文检索与高亮"></a>
## 全文检索与高亮

[文档首页](README.md) / [ORM 开发](orm/README.md)

统一 match/highlight API、中文分词和数据库差异。

示例使用的基础模型见[实体映射与最小模型](#ai-orm-entity)；数据库配置见[数据源](datasources/README.md)。

<a id="ai-orm-search-本页导航"></a>
### 本页导航

- [全文匹配与高亮](#ai-orm-search-全文匹配与高亮)

<a id="ai-orm-search-全文匹配与高亮"></a>
### 全文匹配与高亮

PostgreSQL 中文全文检索先在完整表实体上标记需要建立索引的文本字段：

```java
@JoinTable(value = "video", autoSchema = true)
public class Video {
    @JoinMeta(primaryKey = true)
    private String id;

    @JoinField(name = "title", fullText = true, textLength = 200)
    private String title;

    @JoinField(name = "content", fullText = true, textType = TextTypeEnum.text)
    private String content;

    // getter/setter
}
```

启动扫描在 PostgreSQL 上为 title、content 分别生成可单字段命中的 GIN 表达式索引。查询时 `match` 指定检索字段，`highlight` 把高亮后的摘要直接写回同名结果字段，不需要额外的 titleHighlight DTO 属性：

```java
List<Video> videos = ModelSelectWrapper.newInstance("postgresql", Video.class)
        .match(Video::getTitle, "Java 性能")
        .highlight(Video::getTitle)
        .orderByDesc("id")
        .selectList();
```

也可以同时检索多个字段：

```java
List<Video> videos = ModelSelectWrapper.newInstance("postgresql", Video.class)
        .or(or -> or.match(Video::getTitle, keyword)
                    .match(Video::getContent, keyword))
        .fieldMeta(fields -> fields
                .field("title")
                .field("content")
                .highlight("title")
                .highlight("content"))
        .selectList();
```

只有同一查询中实际出现原生 `match(field, ...)` 的字段才调用 `ts_headline`。单独写 `highlight()`、字段未声明 `fullText=true`、原始 SQL查询，都会返回普通原字段。计数只解析 WHERE，不生成高亮表达式。公共高亮标记与片段设置在 `yulinlin.postgresql.highlight`，PostgreSQL 专属摘要长度在 `yulinlin.postgresql.full-text`；pg_jieba 的安装和完整 YAML 见[PostgreSQL 中文全文检索](#ai-datasources-postgresql-postgresql-中文全文检索)。

MySQL、SQLite、H2 当前把 `match()` 解释为 `%关键词%` LIKE，并忽略 `highlight()`；这只是兼容回退，大表检索应改用 PostgreSQL 原生全文能力或 Elasticsearch。Elasticsearch 的 `match()` 使用原生 MatchQuery；MongoDB 保持现有正则兼容语义。

---

下一步：返回[ORM 开发主题目录](orm/README.md)按需选择相关专题。

---

<a id="ai-orm-join"></a>
<!-- source: doc/orm/join.md -->
<a id="ai-orm-join-sql-join"></a>
## SQL JOIN

[文档首页](README.md) / [ORM 开发](orm/README.md)

SQL 联表查询；与应用侧 JoinQuery 关联加载区分。

示例使用的基础模型见[实体映射与最小模型](#ai-orm-entity)；数据库配置见[数据源](datasources/README.md)。

<a id="ai-orm-join-本页导航"></a>
### 本页导航

- [SQL JOIN](#ai-orm-join-sql-join-1)

<a id="ai-orm-join-sql-join-1"></a>
### SQL JOIN

SQL JOIN 与[应用侧 JoinQuery](#ai-relations-query) 不是同一功能。以下完整投影类假设 sys_user、sys_dept 表存在，列名与示例一致；使用 Lombok 生成访问方法：

```java
package demo.dto;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import lombok.Data;

@Data
@JoinTable(left = "sys_user a", right = "sys_dept b", on = "a.sys_dept_id = b.id")
public class UserDepartmentView {
    @JoinField(name = "a.id")
    private String id;
    @JoinField(name = "a.username")
    private String username;
    @JoinField(name = "b.dept_name")
    private String departmentName;
}
```

方法体查询：`ModelSelectWrapper.newInstance("mysql", UserDepartmentView.class).eq("id", id).selectList()`。更多表可用 JoinTableList。表表达式、别名和 ON 是可信代码，不能直接拼接 HTTP 输入；不能复用旧文档的 JoinPrimary、lambda().eq 或 getSql 调用来猜测当前 API。

---

下一步：返回[ORM 开发主题目录](orm/README.md)按需选择相关专题。

---

<a id="ai-orm-raw-sql"></a>
<!-- source: doc/orm/raw-sql.md -->
<a id="ai-orm-raw-sql-自定义-sql"></a>
## 自定义 SQL

[文档首页](README.md) / [ORM 开发](orm/README.md)

QueryRequest 和 ExecuteRequest 的参数化原始 SQL。

示例使用的基础模型见[实体映射与最小模型](#ai-orm-entity)；数据库配置见[数据源](datasources/README.md)。

<a id="ai-orm-raw-sql-本页导航"></a>
### 本页导航

- [自定义 SQL 执行](#ai-orm-raw-sql-自定义-sql-执行)
- [查询列表与单条](#ai-orm-raw-sql-查询列表与单条)
- [自定义写入](#ai-orm-raw-sql-自定义写入)
- [占位符与安全](#ai-orm-raw-sql-占位符与安全)

<a id="ai-orm-raw-sql-自定义-sql-执行"></a>
### 自定义 SQL 执行

SQL 文本由调用方提供，框架不会跨数据库翻译它。所有 Request 的便利执行方法仍走 RouteSession，需要初始化好的会话。

<a id="ai-orm-raw-sql-查询列表与单条"></a>
#### 查询列表与单条

完整 imports 加方法体片段：

```java
import com.yulinlin.data.core.request.QueryRequest;
import demo.domain.DemoUser;
import java.util.Map;

// MySQL 专用命令，不是 SQLite/H2/PostgreSQL 通用 SQL。
var tablesRequest = QueryRequest.newInstance("show tables", Map.of(), Map.class);
tablesRequest.setSession("mysql");
var tables = tablesRequest.selectList();

// SELECT 列别名与返回对象属性一致；只投影需要的列。
var userRequest = QueryRequest.newInstance(
        "select id, user_name as username, status from ai_demo_user where id=#{id}",
        Map.of("id", "existing-id"), DemoUser.class);
userRequest.setSession("mysql");
DemoUser user = userRequest.selectOne();
```

QueryRequest.newInstance(sql, params, clazz) 的 Map.class 返回按列标签组织的行 Map；对象结果按字段映射解码。selectOne 不检查“恰好一条”。多组时用 setSession 指定组；这是 void setter，不是可继续 selectList 的链式返回。

自定义 SQL 不触发建表，`setFromClass` 也不再承担 Schema 初始化。MySQL/PostgreSQL/SQLite/H2 的结构只由启动扫描处理；原始 SQL 涉及但没有扫描实体的表，应由迁移脚本创建。`entityClass` 仅决定查询结果如何解码。

原始 CommandNode 不自动补分页或计数 SQL。需要时在可信 SQL 中明确写 LIMIT/OFFSET 或 COUNT，并用 selectList/selectOne 读取；不要把包装器分页能力直接套到任意原始命令。

<a id="ai-orm-raw-sql-自定义写入"></a>
#### 自定义写入

```java
import com.yulinlin.data.core.request.ExecuteRequest;
import java.util.Map;

var update = ExecuteRequest.newInstance(
        "update ai_demo_user set status=#{status} where id=#{id}",
        Map.of("status", 1, "id", "existing-id"));
update.setSession("mysql");
Integer affected = update.execute();
```

此入口走通用 update 执行路径，命令 ParseType 同为 update；不根据 SQL 首词猜测 INSERT/DELETE 类型。适用于后端支持的写语句，不作为返回查询结果的入口。DDL、驱动批量改写、受影响行数等行为仍由目标数据库/驱动决定。

Request 可以复用事务上下文，但可变请求对象不能跨线程共享。原始查询 SQL 可以参与缓存，Key 会包含 SQL 与带类型参数。引入缓存 Provider 后，自定义写 SQL 默认继承 `yulinlin.cache.clear-on-write`；开启时因为无法可靠推导目标表，会在提交成功后清理全部 ORM 查询缓存，也可调用 `update.clearCache(false)` 关闭本次清理。未引入 Provider 时没有查询缓存，因此也不执行这项全量失效判定。

<a id="ai-orm-raw-sql-占位符与安全"></a>
#### 占位符与安全

```text
SQL 文本：where id=#{id}
参数 Map：{"id": 7}
SQL Node：where id=?，绑定列表：[7]
```

正则匹配 SQL 文本中的占位符，截取内部的裸键从 Map 取值；不要把参数键写成 "#{id}"。多个或重复占位符按 SQL 出现顺序绑定，Map 的迭代顺序不决定 JDBC 参数顺序。

`#{value}` 用于 PreparedStatement 绑定；`${identifier}` 是直接文本替换，仅用于业务预先校验的可信表名、列名或表达式，不用于用户数据。WHERE 必须明确限制写入目标。

当前编码缓冲区不承诺 null 参数值可正常 put：Map.of 本身也不接受 null。查询 NULL 用 IS NULL，清列可用固定的 SET column=NULL SQL；需要可空动态参数应先核对并验证对应编码路径。缺少键可能被绑定成 null，不当作可靠的参数校验。

---

下一步：返回[ORM 开发主题目录](orm/README.md)按需选择相关专题。

---

<a id="ai-orm-analytics"></a>
<!-- source: doc/orm/analytics.md -->
<a id="ai-orm-analytics-统计分析"></a>
## 统计分析

[文档首页](README.md) / [ORM 开发](orm/README.md)

指标、分组、日期筛选和统计结果模型。

示例使用的基础模型见[实体映射与最小模型](#ai-orm-entity)；数据库配置见[数据源](datasources/README.md)。

<a id="ai-orm-analytics-本页导航"></a>
### 本页导航

- [统计分析](#ai-orm-analytics-统计分析-1)
- [注解模型](#ai-orm-analytics-注解模型)
- [分组求和与日期筛选](#ai-orm-analytics-分组求和与日期筛选)
- [指标与维度速查](#ai-orm-analytics-指标与维度速查)

<a id="ai-orm-analytics-统计分析-1"></a>
### 统计分析

<a id="ai-orm-analytics-注解模型"></a>
#### 注解模型

下面保留 MetricsTable 的业务结构。name 用于分组，metrics 用于 SUM；dateStr 映射 crt_time，默认不作为分组输出。

```java
package demo.statistics;

import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinAggregations;
import com.yulinlin.data.core.anno.JoinMetrics;
import com.yulinlin.data.core.anno.MetricsEnum;
import lombok.Data;

@Data
@JoinTable("sta_metrics")
public class MetricsTable {
    @JoinAggregations
    private String name;

    @JoinField(name = "crt_time")
    private String dateStr;

    @JoinMetrics(MetricsEnum.sum)
    private int metrics;

    public MetricsTable() { }
    public MetricsTable(String name) { this.name = name; }
}
```

仅用于自己的示例数据库的配套表：

```sql
CREATE TABLE sta_metrics (
    name VARCHAR(100),
    crt_time VARCHAR(19),
    metrics INT
);
```

大范围 SUM/COUNT 推荐用 Long/long 或 BigDecimal 等适当结果类型，避免 int 溢出、截断；AVG 也不应假定整数结果。编码器的 Java 类型和目标数据库返回类型一起验证。

<a id="ai-orm-analytics-分组求和与日期筛选"></a>
#### 分组求和与日期筛选

```java
import com.yulinlin.common.model.ModelGroupWrapper;
import demo.statistics.MetricsTable;

var totals = ModelGroupWrapper.newInstance("mysql", MetricsTable.class)
        .gte("dateStr", "2026-10-01 00:00:00")
        .lt("dateStr", "2026-11-01 00:00:00")
        .having(h -> h.gt("metrics", 100))
        .orderByDesc("metrics")
        .selectList();
```

逻辑上是按 name 分组、SUM(metrics)，WHERE 先限定原始行的 crt_time，HAVING 再过滤聚合结果。属性到列、别名、分页和 HAVING 的具体 SQL 由数据库解析器处理，不强行用 MySQL 写法描述全部数据库。

new MetricsTable("foo") 只赋值，不自动生成 WHERE，因为 name 没有 JoinWhere。筛选用 `.eq("name", "foo")`；要让对象值参与条件则明确加 JoinWhere。

<a id="ai-orm-analytics-指标与维度速查"></a>
#### 指标与维度速查

| 类型 | 支持入口 |
| --- | --- |
| JoinMetrics | MetricsEnum.count、distinctCount、sum、avg、min、max |
| JoinAggregations | field、minute、hour、day、month、quarter、year、interval |
| 数字区间 | JoinAggregations(value = AggregationsEnum.interval, interval = 10) |
| ModelGroupWrapper | selectList、selectOne、page(...).selectPage、having、orderByAsc/Desc |
| 结果组织 | selectByMap / selectByGroup 是 Java 端索引或分组，不替代 SQL 聚合 |

要在 name 外增加日期维度，可在支持的数据库上补一个分组表达式：

```java
var daily = ModelGroupWrapper.newInstance("mysql", MetricsTable.class)
        .apply(w -> w.aggregations().day("dateStr", "dateStr"))
        .orderByAsc("dateStr")
        .selectList();
```

也可在 dateStr 字段增加 `@JoinAggregations(AggregationsEnum.day)`，并保留 JoinField 映射。日期维度生成分组格式字符串；没有该表达式时 dateStr 不自动出现在统计结果里。

MySQL/PostgreSQL/H2 使用各自日期与区间解析器；SQLite 当前不支持这两个自动分组入口，需写 strftime 等专用 SQL。TEXT 日期须统一格式与时区；TEXT 数字的比较、排序和聚合不能直接当作原生数值列。自定义函数和表达式始终按目标库语法编写。

---

下一步：返回[ORM 开发主题目录](orm/README.md)按需选择相关专题。

---

<a id="ai-orm-filters"></a>
<!-- source: doc/orm/filters.md -->
<a id="ai-orm-filters-请求与结果过滤器"></a>
## 请求与结果过滤器

[文档首页](README.md) / [ORM 开发](orm/README.md)

通过 RouteSession 增强修改表名、请求和结果。

示例使用的基础模型见[实体映射与最小模型](#ai-orm-entity)；数据库配置见[数据源](datasources/README.md)。

<a id="ai-orm-filters-本页导航"></a>
### 本页导航

- [请求与结果增强（过滤器）](#ai-orm-filters-请求与结果增强过滤器)

<a id="ai-orm-filters-请求与结果增强过滤器"></a>
### 请求与结果增强（过滤器）

实现 `IFilter` 或 `IRequestFilter` 并注册成 Spring Bean，即可自动加入过滤链。`order()` 从小到大执行，同序保留注册顺序。过滤器是共享实例，不要将当前用户、请求或分表名存入实例字段。

增强统一由 RouteSession 执行，具体 Session 和 JdbcSessionFactory 不再注入过滤器管理器：

```text
RouteSession.before → 最终 Request/数据源 → Session 解析、缓存/数据库、解码/代理 → RouteSession.after
```

`before` 在 SQL 解析和缓存 Key 构造之前执行，可以原地修改节点，也可以返回新的同类 Request，用于动态表名、租户条件或数据源选择。查询仍须返回 QueryRequest，写入仍须返回 ExecuteRequest，允许将删除转换为逻辑更新，不能返回 null。以下为配置方法片段，DemoUser 是本文示例实体，固定后缀仅演示可信的业务分表规则：

```java
@Bean
public com.yulinlin.data.core.filter.IRequestFilter userTableFilter() {
    return new com.yulinlin.data.core.filter.IRequestFilter() {
        @Override
        public QueryRequest selectBefore(String group, QueryRequest<?> request) {
            if (request.getFromClass() != DemoUser.class) return request;
            var node = request.getWrapper();
            if (node instanceof com.yulinlin.data.core.wrapper.ICountWrapper count) {
                node = count.getWrapper();
            }
            if (node instanceof com.yulinlin.data.core.wrapper.impl.AbstractSelectWrapper<?, ?, ?, ?> select) {
                select.table("demo_user_202610");
            }
            return request;
        }
    };
}
```

表名必须来自服务端可信规则，不能直接拼接客户端输入。改表名不改变 `fromClass`，它仍负责实体映射和编解码。插入、更新、删除、聚合同样修改各自的节点，批量请求须处理全部节点。替换查询节点优先使用 `request.copy(newWrapper)`，保留模型、fromClass、参数、路由、缓存和代理选项。最终请求改变 group 或 master/slave 时重新选库；后续 SQL、Key 和失效范围使用最终请求。

| 入口 | `IFilter.after` 收到/返回 | `IRequestFilter` 默认分发 |
| --- | --- | --- |
| select、group | List | 调用一次 selectAfter |
| page | Page | 对 Page.list 调用一次 selectAfter；替换列表时保留 total、extra |
| count | Integer | 保持计数不变；需要增强时覆盖普通 IFilter.after |
| insert、update、delete | Integer | 调用 updateAfter |

`after` 的新结果会真正返回给调用者；普通 IFilter 可以替换整个 Page。返回值须符合入口类型，计数和影响行数不能为 null；不要伪造乐观锁校验依赖的影响行数。分页内部 count/select 不重复增强。缓存命中同样执行 after，基础缓存不会保存后处理结果或事务代理；分页过滤当前页列表不重算数据库 total。

延迟队列入队前执行一次 before 并保存增强后节点，后台刷新不再次改表名或选库；after 只在实际执行后调用。后台写后过滤器的 RuntimeException 只记录中文日志，保留实际执行结果，不因此重试已经执行的 Node。普通写入后处理失败则向外传播，并标记路由事务回滚。

after 不代表事务已经提交，提交成功通知使用 `TransactionUtil.afterCommit`。路由作用域按实际入栈次数清理，复用 Request 或过滤器异常不会通过旧 `sessionChange` 标记误弹外层路由。

---

下一步：返回[ORM 开发主题目录](orm/README.md)按需选择相关专题。

---

<a id="ai-orm-repository"></a>
<!-- source: doc/orm/repository.md -->
<a id="ai-orm-repository-repository-接口"></a>
## Repository 接口

[文档首页](README.md) / [ORM 开发](orm/README.md)

方法查询、缓存注解、多数据源与 Service 职责。

示例使用的基础模型见[实体映射与最小模型](#ai-orm-entity)；数据库配置见[数据源](datasources/README.md)。

<a id="ai-orm-repository-本页导航"></a>
### 本页导航

- [Repository 接口](#ai-orm-repository-repository-接口-1)
- [Service 与 Repository 的职责边界](#ai-orm-repository-service-与-repository-的职责边界)

<a id="ai-orm-repository-repository-接口-1"></a>
### Repository 接口

Repository 根据方法名生成普通 Wrapper 查询。引入模块后自动启用，不再需要扫描注解：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>repository</artifactId>
    <version>3.0</version>
</dependency>
```

未配置扫描路径时，默认从 `@SpringBootApplication`所在根包递归查找。需要限制范围或扫描多个业务模块时，在配置文件指定：

```yaml
yulinlin:
  repository:
    scan-packages:
      - "com.example.*.*.local"       # 每个 * 匹配一层
      - "com.example.**.repository"  # ** 跨任意层级
      - "${APP_REPOSITORY_PACKAGE:com.example.repository}"
```

扫描使用类元数据，不会加载路径下所有普通类。普通包名递归扫描；路径段支持 Ant 风格通配符：`*`匹配一级包，`**`匹配任意层级，`?`匹配单个字符。YAML 中包含 `*`的路径必须加引号。配置缺失时使用应用根包，而配置存在时只扫描列出的路径。

Repository 默认 Bean 名是接口简单类名首字母小写。同名接口会明确报错，不会静默覆盖；使用 `@JoinRepository("localUserRepository")`指定唯一名称。重复或重叠扫描路径会自动去重。`JoinRepository`、`JoinCache` 和 `JoinSession` 均从 `com.yulinlin.data.core.anno` 导入；Repository 模块只提供扫描与代理实现。

Repository 接口和方法都可以使用 `@JoinSession`切换数据源，方法注解优先于接口注解：

```java
@JoinRepository
@JoinSession("mysql")
public interface DemoUserRepository extends BaseRepository<DemoUser> {

    // 使用 mysql/master
    DemoUser findByUsernameEq(String username);

    // 仅此方法切换到 postgresql/master，返回后恢复 mysql
    @JoinSession("postgresql")
    List<DemoUser> findByStatusEq(Integer status);

    // 指定同组从库
    @JoinSession(value = "mysql", cluster = JoinCluster.slave)
    List<DemoUser> findByTypeEq(Integer type);
}
```

Repository 必须从 Spring 容器注入后调用，不能自行创建代理。显式 Request group 仍高于注解；最终选中的 group 和 cluster 会进入查询缓存 Key，因此不同数据源不会共用查询缓存。

<a id="ai-orm-repository-service-与-repository-的职责边界"></a>
#### Service 与 Repository 的职责边界

推荐把一个完整的业务事务放在 Service 层：Service 方法使用 `@Transactional`（或框架 `@JoinTransaction`）定义事务边界，需要 setter 自动回写时再同时添加 `@JoinSync`。Repository 主要声明查询方法、`@JoinCache` 缓存策略，以及确实需要的 `@JoinSession` 数据源路由。

```java
@JoinRepository
public interface DemoUserRepository extends BaseRepository<DemoUser> {

    @JoinCache
    DemoUser findByIdEq(String id);
}

@Service
public class DemoUserService {
    private final DemoUserRepository repository;

    public DemoUserService(DemoUserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @JoinSync
    public void rename(String id, String name) {
        DemoUser user = repository.findByIdEq(id);
        if (user == null) throw new IllegalStateException("user not found");
        user.setName(name); // 事务提交前生成部分更新
    }
}
```

`@JoinSync` 只开启查询结果的自动更新策略，不会开启事务。它可用在 Repository 查询方法上，用于精确指定某个查询返回代理对象；但 setter 仍必须发生在外层 Service 持有的原事务内。一般业务更推荐在 Service 统一使用 `@Transactional + @JoinSync`，避免把事务和更新意图分散到多个 Repository。不要只在 Repository 查询方法上开启事务后，在方法返回之外再修改实体；此时原始事务已经结束。只读查询不需要 `@JoinSync`，显式 Wrapper 查询仍可在事务内使用 `.autoUpdate()` 覆盖单次行为。

`@JoinCache` 可以标记 Repository 接口，为其中全部抽象查询方法提供默认缓存配置；方法级注解优先，可覆盖接口配置或用 `CacheMode.NONE` 关闭单个查询。父接口上的类级和方法级配置均可继承：

```java
@JoinRepository
@JoinCache(ttl = 10, unit = TimeUnit.MINUTES)
public interface DemoUserRepository extends BaseRepository<DemoUser> {

    // 继承接口配置：READ_THROUGH，缓存 10 分钟
    DemoUser findByUsernameEq(String username);

    // 明确关闭该查询的缓存
    @JoinCache(mode = CacheMode.NONE)
    List<DemoUser> findByStatusEq(Integer status);

    // 方法配置完整覆盖接口配置：强制查库，TTL 改为 30 秒
    @JoinCache(mode = CacheMode.REFRESH, ttl = 30, unit = TimeUnit.SECONDS)
    DemoUser findByIdEq(String id);

    // 额外把角色表版本纳入 Key，角色表更新后该查询也失效
    @JoinCache(namespaces = "sys_role")
    List<DemoUser> findAll();
}
```

方法级注解是完整替换而不是逐项合并：上例 `REFRESH` 方法不会继承接口的 10 分钟 TTL。`ttl = -1`是默认值，表示使用 `yulinlin.cache.ttl`；显式 TTL 必须为正数。默认 `yulinlin.cache.ttl-in-key=false`，不同 TTL 的相同查询会复用 Key；设为 `true`才按 TTL 隔离。`CACHE_ONLY`只读已有缓存，未命中抛出 `CacheMissException`。未引入缓存 Provider 时，`READ_THROUGH`和`REFRESH`安全退化为查询数据源但不保存，`CACHE_ONLY`仍然报未命中。

接口级配置只影响查询，写方法不会继承它。写方法直接添加 `@JoinCache`、TTL 非法或 namespace 为空，会在 Repository 代理创建阶段失败；已引入缓存 Provider 时，Repository 的 insert/update/delete 按 `yulinlin.cache.clear-on-write` 决定是否在提交成功后清理解析出的表缓存。需要单次覆盖时使用写 Wrapper 或 `ExecuteRequest.clearCache(boolean)`。基础对象在关联代理处理前进入缓存，懒加载和批量预加载会按关联实体单独查询并继承入口缓存模式，因此普通懒加载关系不需要额外声明关联表 namespace；`namespaces` 主要用于无法从查询节点识别全部依赖的自定义 SQL 或非懒加载聚合。

---

下一步：返回[ORM 开发主题目录](orm/README.md)按需选择相关专题。

---

<a id="ai-relations-transactions"></a>
<!-- source: doc/relations/transactions.md -->
<a id="ai-relations-transactions-事务与提交后回调"></a>
## 事务与提交后回调

[文档首页](README.md) / [关联与事务](relations/README.md)

路由事务、Spring 注解兼容和 afterCommit。

<a id="ai-relations-transactions-本页导航"></a>
### 本页导航

- [事务使用](#ai-relations-transactions-事务使用)
- [框架路由事务](#ai-relations-transactions-框架路由事务)
- [Spring 事务注解兼容](#ai-relations-transactions-spring-事务注解兼容)
- [排查事务是否提交](#ai-relations-transactions-排查事务是否提交)
- [提交成功回调](#ai-relations-transactions-提交成功回调)
- [路由是唯一业务入口](#ai-relations-transactions-路由是唯一业务入口)

<a id="ai-relations-transactions-事务使用"></a>
### 事务使用

<a id="ai-relations-transactions-框架路由事务"></a>
#### 框架路由事务

以下完整 Service 方法片段假设 mysql/sqlite 两组及业务表均已准备：

```java
import com.yulinlin.data.core.anno.JoinTransaction;

@JoinTransaction
public void saveBusinessData() {
    ModelInsertWrapper.newInstance("mysql", mysqlUsers).batch().execute();
    ModelInsertWrapper.newInstance("sqlite", localUsers).execute();
}
```

放在 Spring 管理的 Service public 方法上，通过代理调用。也可使用回调：

```java
SessionUtil.route().transaction(() -> {
    ModelInsertWrapper.newInstance("mysql", usersToInsert).execute();
    return null;
});
```

`RouteSession` 是业务 CRUD 和事务的唯一入口。没有外层事务时，一次写请求由 RouteSession 自动开始并结束事务；普通查询不主动开启事务，多个独立请求不自动合成一个业务事务。RouteSession 定义边界并登记参与者，`TransactionListenerManager` 统一维护当前线程的业务上下文，各 Session 只管理自己的连接资源。

同一线程内，路由方法和事务注解共用一份 `TransactionContext`，具体 Session 不创建上下文，也不反向调用事务管理器。只有路由显式开启事务才增加 `depth`，普通 CRUD 和 Session 资源开启只登记参与者，不增加深度；同一个 Session 的资源在本事务中只开启一次。内层提交只减少深度，不提交连接、不刷新代理、不执行提交后回调；内层回滚标记整个事务 rollback-only。最外层统一写回代理、提交/回滚参与者、清理状态和通知回调。不同线程互相隔离，嵌套不模拟独立提交或保存点。

<a id="ai-relations-transactions-spring-事务注解兼容"></a>
#### Spring 事务注解兼容

继续使用 `org.springframework.transaction.annotation.Transactional`。JDBC 模块自动注册默认优先的 `yulinlinTransactionManager`，Spring 标准注解拦截器解析注解后，由这个轻量适配器调用 RouteSession。它不创建 Spring JDBC 事务、不绑定 Spring 连接、不注册 Spring 同步回调。原有重复的 `SpringTransactionAop` 已移除，默认 Boot JDBC 事务管理器自动配置会退让。

```java
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Exception.class)
public void createOrder() throws Exception {
    // 在 Service 中通过 Repository / Wrapper 执行业务；所有数据源加入同一路由事务。
}
```

- 支持默认 `REQUIRED`：内层加入同一业务上下文，最外层才提交；内层回滚会标记整个事务 rollback-only。
- `rollbackFor`、`noRollbackFor` 及其类名规则由 Spring 标准拦截器解析。默认对 `RuntimeException` / `Error` 回滚，对受检异常默认提交；业务需要受检异常也回滚时使用上例。已经发生 SQL 失败等导致 rollback-only 的事务不能被 `noRollbackFor` 强行提交。
- 目前仅支持默认隔离级别、默认超时和 `readOnly=false`。非默认传播方式（包括 `REQUIRES_NEW` / `NESTED`）、隔离级别、超时或 `readOnly=true` 会在进入业务方法前明确报错，不静默假装支持。
- 必须从 Spring 代理外部调用；不要叠加 `@JoinTransaction` 和 `@Transactional`，也不要认为业务异步线程会继承事务。框架 `.batch()` 由内部显式传递连接池，不等于自动传播业务线程上下文。
- 数据源仍使用原来的 Spring Bean 配置，但连接由框架独立取得和管理。原生 JdbcTemplate、MyBatis、JPA 不会加入此事务；不能把混合使用当成统一提交。
- 若项目另外指定了默认事务管理器、有其他 `@Primary` 管理器，或使用 `TransactionManagementConfigurer`，请明确使用 `@Transactional(transactionManager = "yulinlinTransactionManager", rollbackFor = Exception.class)`。显式指定其他管理器不进入本适配器，框架写请求会按自己的边界独立提交。
- 不提供 Spring `TransactionSynchronizationManager` / `@TransactionalEventListener` 的同步生命周期。提交后业务使用 `TransactionUtil.afterCommit`；手动 `TransactionTemplate` 只有选择本适配器时才进入框架事务。

迁移时更新配套的 core、jdbc 及所用数据库模块，移除业务手动注册的旧 `SpringTransactionAop`。不要同时保留另一套拦截相同 `@Transactional` 方法的自定义事务切面。

<a id="ai-relations-transactions-排查事务是否提交"></a>
#### 排查事务是否提交

开启 `yulinlin.data.transaction-log: true`（默认关闭），按 SQL 日志里的同一个 `txId` 查看开启、嵌套深度、数据源参与和连接的最终处理。SQL 日志中的 `affected=1` 只表示 JDBC 执行影响一行，不代表事务已经提交；事务日志关闭时 SQL 仍保留 ID，没有框架事务的查询显示 `txId=none`。连接均由框架持有，最终查看每个连接的 `JDBC连接提交成功` 或回滚日志。`数据源事务提交方法已返回`、`框架事务上下文已清理` 都不是物理提交凭证，多库/多连接要逐一核对，不能凭一个资源成功推断全部成功。配置、字段和日志事件详见[事务诊断日志](operations/sql-log.md#事务诊断日志)。

<a id="ai-relations-transactions-提交成功回调"></a>
#### 提交成功回调

业务需要在数据库提交成功后刷新权限、清理业务缓存或发送进程内通知时，使用 `com.yulinlin.data.core.transaction.TransactionUtil.afterCommit(Runnable)`：

```java
@Transactional
@JoinSync
public void changeNickname(String id, String nickname) {
    UserEntity user = userRepository.findByIdEq(id);
    user.setNickname(nickname);
    TransactionUtil.afterCommit(() -> security.refresh(id));
}
```

上例是方法片段，`userRepository` 为业务仓库，`security` 为注入的 `ISecurityManager`。刷新指定用户资料；若是当前用户，则更新本线程快照，不续期或撤销 JWT。实际业务还应保留输入校验。无状态 JWT 案例每次请求重新加载权限，单纯修改角色无需遍历刷新全部会话。`@Transactional` 经 `yulinlinTransactionManager` 接入路由事务；`@JoinTransaction` 和 `SessionUtil.route().transaction(...)` 也使用同一入口。`TransactionUtil` 只向当前框架事务注册，不为其他事务管理器提供回调兜底。

| 场景 | 行为 |
| --- | --- |
| 框架业务事务成功提交 | 先清理旧代理状态并解绑上下文，处理相关缓存失效，再通知固定 `TransactionListener.afterCommit()`，最后按注册顺序同步执行业务回调 |
| 路由事务嵌套、同时访问多个数据源 | 共用当前线程的上下文，等最外层完成后每个回调执行一次 |
| 回滚、rollback-only 或提交失败 | 不触发提交成功通知，丢弃业务回调 |
| `@Transactional` 使用框架适配器 | 与路由事务完全一致，不等待其他 Spring 事务 |
| 仅有其他 Spring 管理器的事务、没有框架事务上下文 | 拒绝注册，提示先开启框架事务 |
| 仅手动开启具体 Session 的连接资源 | 不构成业务事务，不能注册回调；必须通过 RouteSession 开启 |
| 没有活动的框架事务 | 抛出 `IllegalStateException`，不静默立即执行 |
| 清理、监听器或某个回调失败 | 仍尝试后续清理与回调，最终抛出首个异常，其他异常保留为 suppressed；已提交的数据不再回滚 |

回调在当前线程执行，不是异步任务，也不是 `.queue()`。管理器先清理原事务的代理状态、解绑上下文，再发出 `afterCompletion()` 和成功通知；回调中新开的框架事务使用新上下文，不会被原事务的收尾逻辑误清理。不要在回调里修改原事务代理；普通读取显式 `.autoUpdate(false)`，多步写业务用新的框架事务边界。RBAC 案例的 `findSnapshotById` 使用普通查询快照，避免继承外层 `@JoinSync` 的代理策略。

回调开始前，框架 JDBC 连接已完成提交与释放。因此回调中再次调用 `SessionUtil.route().transaction(...)` 会建立新的框架事务；这只是旧事务结束后的新事务，不代表支持活动事务内的 `REQUIRES_NEW`。若另外包在原生 Spring 事务中，两者完全独立，本回调不会等待该原生事务，也不能保证它提交成功。

提交后回调失败不代表数据库写入失败，调用方不能盲目重试原始写入。它是进程内通知，应用在提交后异常退出可能来不及执行；需要可靠投递的外部消息仍由业务持久化任务或 Outbox 处理。

具体 Session 的 `startTransaction/commitTransaction/rollbackTransaction` 是管理器调用的资源生命周期，不再转发管理器，也不维护业务嵌套深度。业务代码不得手动调用它们，否则会绕过统一的代理写回、跨数据源协调、缓存失效和回调；JDBC 无活动资源的写操作会报错。

<a id="ai-relations-transactions-路由是唯一业务入口"></a>
#### 路由是唯一业务入口

以下为方法体片段，两个 Request 已构造，并明确指定各自的数据源组：

```java
insertRequest.setSession("mysql");
updateRequest.setSession("sqlite");
RouteSession route = SessionUtil.route();
route.transaction(() -> {
    route.insert(insertRequest);
    route.update(updateRequest);
    return null;
});
```

`route.isOpenTransaction()` 表示当前线程是否存在业务事务；具体 Session 的同名方法只表示自身资源是否已开启，不等于新的业务事务。业务事务必须在开启它的同一线程结束。

嵌套是共享外层事务的计数，不是保存点。执行失败/嵌套回滚标记 rollback-only，即使业务捕获异常，外层不能继续正常提交。跨库和多连接只是本地事务协调，不是分布式原子提交。

---

下一步：返回[关联与事务主题目录](relations/README.md)按需选择相关专题。

---

<a id="ai-relations-query"></a>
<!-- source: doc/relations/query.md -->
<a id="ai-relations-query-关联查询"></a>
## 关联查询

[文档首页](README.md) / [关联与事务](relations/README.md)

JoinQuery 级联、动态值、固定值和复杂查询条件。

<a id="ai-relations-query-本页导航"></a>
### 本页导航

- [行为速查](#ai-relations-query-行为速查)
- [用户角色菜单级联](#ai-relations-query-用户角色菜单级联)
- [动态值和固定值](#ai-relations-query-动态值和固定值)
- [复杂查询条件](#ai-relations-query-复杂查询条件)

<a id="ai-relations-query-行为速查"></a>
### 行为速查

| 配置/注解 | 加载 | 修改 |
| --- | --- | --- |
| JoinQuery | 查询结果增强时立即查询关联 | 普通对象，不因此自动写库 |
| JoinQuery + JoinLazy | 框架业务事务内首次访问 getter 时加载 | 仅延迟读取；关联选库仍需要路由 |
| 查询 `.autoUpdate()` | 主对象与关联对象使用同一层代理 | setter 的非 null 修改在提交前写回 |
| 方法/类 `@JoinSync` | 作用域内所有查询等同 `.autoUpdate()` | 必须同时存在框架业务事务 |
| `yulinlin.data.auto-update=true` | 全局查询默认自动更新 | 可由单次 `.autoUpdate(false)` 覆盖 |

ORM 查询得到的模型统一由 EntityProxyFactory 增强。一个实体最多只有一层代理；同一拦截器处理 JoinLazy getter 与持久化 setter，不再叠加 LazyProxy/SyncProxy。自己 new 出来的 DTO 仍需调用 RouteSession 的兼容代理入口。

<a id="ai-relations-query-用户角色菜单级联"></a>
### 用户角色菜单级联

以下完整 DTO 放在 demo.dto.RouterDetails。引用的三个业务实体必须由应用提供，不依赖 admin 模块；每个目标实体需有表映射、无参构造、getter/setter 和明确类型的 ID。

| 实体 | 示例契约 |
| --- | --- |
| demo.domain.SysUserEntity | String id、username、nickname；List<String> sysRoleIds |
| demo.domain.SysRoleEntity | String id；List<String> sysMenuIds |
| demo.domain.SysMenuEntity | String id；菜单显示属性 |

sysRoleIds/sysMenuIds 的元素类型必须与目标 id 一致，字段与表中 JSON/集合存储匹配。Lombok Data 需要业务项目配置注解处理。

```java
package demo.dto;

import com.yulinlin.data.core.anno.JoinQuery;
import demo.domain.SysUserEntity;
import demo.domain.SysRoleEntity;
import demo.domain.SysMenuEntity;
import lombok.Data;
import java.util.List;

@Data
public class RouterDetails {
    private String username;
    private String loginType;

    @JoinQuery(primary = "username", value = "${username}")
    private SysUserEntity user;

    @JoinQuery(primary = "id", value = "${user.sysRoleIds}")
    private List<SysRoleEntity> roles;

    @JoinQuery(primary = "id", value = "${roles.sysMenuIds}")
    private List<SysMenuEntity> menus;

    public RouterDetails() { }
    public RouterDetails(String username, String loginType) {
        this.username = username;
        this.loginType = loginType;
    }
}
```

业务方法体片段，username/loginType 已由调用方提供：

```java
import com.yulinlin.data.core.session.SessionUtil;
import demo.dto.RouterDetails;

RouterDetails details = SessionUtil.callable("mysql", () ->
        SessionUtil.route().getLazyProxy(new RouterDetails(username, loginType)));
```

这个类没有 JoinLazy，入口会立即尝试加载 user、roles、menus。getLazyProxy 的名称不代表所有字段都延迟。

即时关联按反射字段顺序处理，不是依赖拓扑排序。示例按 user → roles → menus 放置，但复杂图不能仅靠反射顺序保证正确，必要时分步查询或采用下面的懒字段方案。路由的默认递归深度保护是 6，不等于循环关联可以安全展开。

<a id="ai-relations-query-动态值和固定值"></a>
#### 动态值和固定值

| JoinQuery.value | 含义 |
| --- | --- |
| "${username}" | 当前对象 username |
| "${user.sysRoleIds}" | 当前对象 user 的角色 ID |
| "${roles.sysMenuIds}" | 遍历 roles 汇集菜单 ID |
| "username" | 固定字符串，不是当前属性值 |

动态取值必须带 ${...}，不能按其他 ORM 的属性名规则猜测。primary 是目标匹配属性，默认 id，不要求一定是物理主键。

放在数据库实体里的关系属性加 `@JoinField(exist = false)`，避免被当作列。纯组装 DTO 可以没有 JoinTable，但目标实体仍需完整映射和实际表。

List/Set 的元素类型必须可推断，不使用原始 List、List<?> 或不明确泛型。无匹配时单对象为 null，集合为空；单对象匹配键应保证唯一，否则取第一条。

<a id="ai-relations-query-复杂查询条件"></a>
#### 复杂查询条件

`wheres` 用于根据当前父对象的多个属性构造关联查询。下面的对象会查询指定视频下、状态可见、创建时间较新且正文包含关键词的前 20 条评论：

```java
package demo.dto;

import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinOrder;
import com.yulinlin.data.core.anno.JoinQuery;
import com.yulinlin.data.core.anno.JoinWhere;
import demo.domain.CommentEntity;
import lombok.Data;

import java.util.List;

@Data
public class VideoCommentDetails {
    private String videoId;
    private List<Integer> visibleStatuses;
    private String keyword;
    private String createdAfter;
    private String createdBefore;

    @JoinQuery(
        wheres = {
            @JoinWhere(name = "videoId", value = "${videoId}"),
            @JoinWhere(
                name = "status",
                value = "${visibleStatuses}",
                condition = ConditionEnum.in
            ),
            @JoinWhere(
                name = "content",
                value = "${keyword}",
                condition = ConditionEnum.like
            ),
            @JoinWhere(
                name = "crtTime",
                value = "${createdAfter}",
                condition = ConditionEnum.gte
            ),
            @JoinWhere(
                name = "crtTime",
                value = "${createdBefore}",
                condition = ConditionEnum.lte
            )
        },
        order = @JoinOrder(name = "crtTime", asc = false),
        size = 20
    )
    private List<CommentEntity> comments;
}
```

创建条件对象并执行关联增强：

```java
VideoCommentDetails details = new VideoCommentDetails();
details.setVideoId("video-1001");
details.setVisibleStatuses(List.of(1, 2));
details.setKeyword("Java");
details.setCreatedAfter("2026-10-01 00:00:00");
details.setCreatedBefore("2026-11-01 00:00:00");

VideoCommentDetails result = SessionUtil.callable("mysql", () ->
        SessionUtil.route().getLazyProxy(details));

List<CommentEntity> comments = result.getComments();
```

该示例等价于以下逻辑条件：

```sql
video_id = ?
AND status IN (?, ?)
AND content LIKE ?
AND crt_time >= ?
AND crt_time <= ?
ORDER BY crt_time DESC
LIMIT 20
```

`name` 写目标实体的 Java 属性名，字段到数据库列名的转换仍由 `@JoinField` 和具体数据源解析器完成。`value = "${...}"` 从当前父对象取值；不带 `${}` 的值会作为固定字符串，例如 `@JoinWhere(name = "type", value = "PUBLIC")`。

值为 `null`、空字符串或空集合的条件会被忽略；如果所有动态条件都没有值，框架返回空关联结果，不会退化成无条件全表查询。`ConditionEnum.in` 的值必须是集合或数组属性，其元素会展开并去重。

当前 `wheres` 中的条件统一按 AND 使用，并且每个父对象单独构造查询。因此列表场景可能产生 N+1，不享受普通 `primary/value` 分支的批量 IN 分配。`JoinWhere.and()` 当前没有接入代理查询；`between` 也不应在 `wheres` 中使用，需要范围条件时分别写 `gte` 和 `lte` 两个父对象属性。

关联计数使用 `model` 指定被统计的实体，字段使用 `Long`：

```java
@JoinQuery(
    model = CommentEntity.class,
    wheres = {
        @JoinWhere(name = "videoId", value = "${videoId}"),
        @JoinWhere(name = "status", value = "${visibleStatuses}", condition = ConditionEnum.in)
    }
)
private Long visibleCommentCount;
```

这里的 `model` 会进入 `count()` 分支，不是用来替代 `List<CommentEntity>` 泛型声明的通用参数。

---

下一步：返回[关联与事务主题目录](relations/README.md)按需选择相关专题。

---

<a id="ai-relations-lazy"></a>
<!-- source: doc/relations/lazy.md -->
<a id="ai-relations-lazy-懒加载与批量预加载"></a>
## 懒加载与批量预加载

[文档首页](README.md) / [关联与事务](relations/README.md)

首次访问触发加载；主键关系按 IN 批量预加载。

<a id="ai-relations-lazy-本页导航"></a>
### 本页导航

- [懒加载](#ai-relations-lazy-懒加载)
- [列表批量预加载](#ai-relations-lazy-列表批量预加载)

<a id="ai-relations-lazy-懒加载"></a>
### 懒加载

在上面的三个关联字段上各加 JoinLazy；其余类结构不变：

```java
import com.yulinlin.data.core.anno.JoinLazy;

@JoinLazy
@JoinQuery(primary = "username", value = "${username}")
private SysUserEntity user;

@JoinLazy
@JoinQuery(primary = "id", value = "${user.sysRoleIds}")
private List<SysRoleEntity> roles;

@JoinLazy
@JoinQuery(primary = "id", value = "${roles.sysMenuIds}")
private List<SysMenuEntity> menus;
```

在创建它的同一线程、同一框架业务事务中读取所需字段：

```java
SessionUtil.route().transaction(() ->
        SessionUtil.callable("mysql", () -> {
            RouterDetails details = SessionUtil.route()
                    .getLazyProxy(new RouterDetails(username, loginType));
            var user = details.getUser();
            var roles = details.getRoles();
            var menus = details.getMenus();
            // 在事务内将所需字段复制到普通响应 DTO。
            return null;
        }));
```

也可在 Spring Service public 方法上用 JoinTransaction，经代理调用。业务事务统一通过 RouteSession 开启，事务注解也接入同一入口。具体 JdbcSession 只处理自己的连接资源，不再反向调用管理器或独立创建业务上下文；关联查询与代理写回均通过 RouteSession 解析来源组和执行。

- 没有框架业务事务时，JoinLazy 字段会被跳过，不自动改成立即查询。
- getter 才触发加载，直接访问字段或原始对象不是等价调用。
- 未加载字段在事务外、其他线程或新事务读取会报错；已加载数据可在事务后读取。
- 不直接把未加载代理交给 Controller JSON 序列化或异步任务。
- 代理类不能是 final/record；需要无参构造，相关 getter/setter 可被 CGLIB 覆盖。
- 一次成功加载包括 null/空结果，不反复查询；失败不标记完成，可重试；显式 setter 值不被后续批量赋值覆盖。
- 循环懒加载会报错，仍应避免循环图。

<a id="ai-relations-lazy-列表批量预加载"></a>
### 列表批量预加载

普通关联会汇集同批父对象的关联键，去重后按 IN 分批查询，再按目标属性建立索引分配。默认 batchSize 为 512，表示每批去重键数，不是结果行数或事务提交大小。

同一次 selectList 的懒代理共享上下文，首次访问某个关联 getter 时为同批对象加载该字段，不逐个父对象发查询。手动 DTO 列表一次性传入：

```java
SessionUtil.route().transaction(() ->
        SessionUtil.callable("mysql", () -> {
            var details = SessionUtil.route().getLazyProxy(
                    java.util.List.of(new RouterDetails("alice", "password"),
                                      new RouterDetails("bob", "password")));
            details.getFirst().getUser(); // 汇集 alice/bob 查询并分配。
            details.get(1).getUser();     // 这一批已加载，不再单独查 bob。
            return null;
        }));
```

这段使用的是加过 JoinLazy 的 RouterDetails。不要逐个 getLazyProxy(dto)，否则不是同一批。不同查询结果不自动合并。

`@JoinQuery(value = "${ids}", batchSize = 256)` 可调每批 IN 键上限。关联集合不保证输入 ID 顺序；复杂 wheres/model 分支仍可能 N+1，不把全部关联规则都描述成自动 IN。

---

下一步：返回[关联与事务主题目录](relations/README.md)按需选择相关专题。

---

<a id="ai-relations-auto-update"></a>
<!-- source: doc/relations/auto-update.md -->
<a id="ai-relations-auto-update-代理自动更新"></a>
## 代理自动更新

[文档首页](README.md) / [关联与事务](relations/README.md)

事务内 setter 跟踪、代理生命周期及限制。

<a id="ai-relations-auto-update-本页导航"></a>
### 本页导航

- [自动更新](#ai-relations-auto-update-自动更新)
- [会话与代理边界](#ai-relations-auto-update-会话与代理边界)
- [修改跟踪的限制](#ai-relations-auto-update-修改跟踪的限制)
- [常见问题](#ai-relations-auto-update-常见问题)

<a id="ai-relations-auto-update-自动更新"></a>
### 自动更新

自动更新捕获事务内的 setter 修改，在提交前统一更新数据库，不是每次 setter 立即执行 SQL，也不是后台线程写入。现有 `ProxyChanges` 按需保存在管理器的统一 `TransactionContext` 中，代理工厂不再维护独立事务 ThreadLocal；主对象和懒加载出的关联代理共用这份记录。单次查询开启：

```java
SessionUtil.route().transaction(() ->
        SessionUtil.callable("mysql", () -> {
            DemoUser user = ModelSelectWrapper.newInstance("mysql", DemoUser.class)
                    .autoUpdate()
                    .eq("id", id)
                    .selectOne();
            if (user == null) throw new IllegalStateException("user not found");
            user.setStatus(1);
            return null;
        }));
```

没有框架业务事务时，`.autoUpdate()` 会在执行 SQL 前提示“请开启事务”。setter 只保存脏字段的最终非 null 值，不为每次修改创建 Runnable；最外层提交前按数据源和实体类型批量写回，保留主键校验和乐观锁。内层提交不写回，回滚丢弃修改记录；回滚不会还原 Java 对象的内存值。这些更新不能放进 `afterCommitTasks`。

全局开启：

```yaml
yulinlin:
  data:
    auto-update: true
```

全局开启后，可更新实体的查询都必须处于事务中。单次查询可用 `.autoUpdate(false)` 明确返回普通对象。

方法或类级入口：

```java
@Transactional
@JoinSync
public void rename(String id, String name) {
    DemoUser user = ModelSelectWrapper.newInstance("mysql", DemoUser.class)
            .eq("id", id)
            .selectOne();
    user.setName(name);
}
```

`@JoinSync` 只开启当前调用作用域的自动更新，不创建事务；需配合 `@Transactional`、`@JoinTransaction` 或 `RouteSession.transaction(...)`。它不再允许标在字段上。

在 Spring 项目中，推荐把 `@Transactional` 和 `@JoinSync` 一起放在 Service 的 public 业务方法上，让查询、setter 修改与提交共享同一个边界。Repository 保持查询声明，通常只配置 `@JoinCache` 和必要的 `@JoinSession`。Repository 方法也可用 `@JoinSync` 精确指定该查询返回代理，但后续 setter 必须仍位于外层 Service 的活动事务内；不建议把它当作默认分层方式。代理对象不能在创建它的事务结束后继续自动更新。只读 Service 不需要 `@JoinSync`。Repository 完整示例见[ORM 开发](#ai-orm-repository-service-与-repository-的职责边界)。

主对象的关联查询继承相同策略。懒字段首次 getter 得到的实体或集合元素也是同一层代理，因此在原事务内调用关联实体 setter 同样会写回对应表。

通常直接使用 `.autoUpdate()` 或 `@JoinSync`，不再手动调用 `getSyncProxy`；旧公开方法只用于已有代码迁移。

<a id="ai-relations-auto-update-会话与代理边界"></a>
### 会话与代理边界

字段可以指定独立会话，下面是已有 DTO 中的字段片段：

```java
import com.yulinlin.data.core.anno.JoinSession;

@JoinSession("sqlite")
@JoinQuery(primary = "id", value = "${localUserId}")
private LocalUserEntity localUser;
```

LocalUserEntity 与 localUserId 由业务提供。未指定时关联上下文保留创建时的来源组；后续 getter 即使进入其他会话栈，也使用原组。同步更新也按原组执行。

| 参数 | 当前范围 |
| --- | --- |
| order | 普通关联排序；跨批 Java 合并与数据库 collation 不一定一致 |
| batchSize | 普通 IN 的键数上限 |
| wheres | 按父对象构造复杂条件，可能逐对象查 |
| size | 正值只在 wheres/model 分支分页，不作用于普通 primary/value 分支 |
| model | 当前进入 count 分支，不是替代集合泛型的通用参数 |

<a id="ai-relations-auto-update-修改跟踪的限制"></a>
#### 修改跟踪的限制

- 目标必须是可更新实体，有完整非 null 主键；创建/提交校验主键，禁止代理改主键。
- 只捕获真正持久化属性的单参数 setter；普通方法、直接写字段、修改原对象不自动同步。
- null 跳过：setX(null) 不清列，并取消此前该字段的待同步值。
- 集合 add、Map.put、嵌套对象原地变化不自动跟踪，需要调用持久化属性 setter 或显式更新。
- 未调用的 setter 不产生部分更新，未修改的 0/false/初始化值不会被全部覆盖到数据库；设回原值不会产生更新。
- setter 捕获对象引用，不是深快照；setter 后原地修改同一对象可能改变最终编码值。
- 支持的 Integer/int、Long/long 版本字段按原始版本条件递增；写入数不匹配视为乐观锁失败，不承诺全部 Wrapper 都具备相同版本机制。
- 代理绑定原始线程与统一业务事务上下文，结束后不能继续 setter 修改。缓存保存未增强数据，读取时复制并创建当前查询代理，不能复用旧事务代理。

同步写回统一发生在框架物理提交前，不依赖 Spring 的提交回调。`@Transactional` 只适配到同一路由事务，目前 `readOnly=true` 会在进入业务方法前被拒绝；普通只读查询无需声明该属性。高级传播与分布式一致性仍按 [ORM 开发](#ai-relations-transactions-事务使用)的边界理解。

<a id="ai-relations-auto-update-常见问题"></a>
### 常见问题

| 问题 | 检查 |
| --- | --- |
| 关联没有数据 | ${...}、源属性、泛型、键类型、目标表与会话 |
| 懒字段一直为 null | 是否经过代理入口、配置路由并处于框架业务事务中，是否访问 getter |
| 提示原始事务不一致 | 是否跨线程、事务外读取或把旧代理带进新事务 |
| 列表查询仍很多 | 是否同批入口，是否使用复杂 wheres/model 分支 |
| setter 后没更新 | 是否同步代理、原始事务、非 null 值与可靠主键 |
| 缓存命中复制失败 | 模型是否满足深克隆边界，是否包含流/连接等资源 |

实现职责与验证范围在 [内部维护](internal/README.md)，不要把历史测试记录解释成当前全部业务示例已运行。

---

下一步：返回[关联与事务主题目录](relations/README.md)按需选择相关专题。

---

<a id="ai-write-sync-incremental"></a>
<!-- source: doc/write-sync/incremental.md -->
<a id="ai-write-sync-incremental-增量同步"></a>
## 增量同步

[文档首页](README.md) / [批量与同步](write-sync/README.md)

轻量异步同步、实体规则、状态恢复及一致性限制。

补齐历史数据请阅读[人工全量与主键续传](#ai-write-sync-full)，按要求业务停写后操作。

<a id="ai-write-sync-incremental-本页导航"></a>
### 本页导航

- [数据源增量同步](#ai-write-sync-incremental-数据源增量同步)
- [实体通配符](#ai-write-sync-incremental-实体通配符)
- [执行语义](#ai-write-sync-incremental-执行语义)
- [状态与人工恢复](#ai-write-sync-incremental-状态与人工恢复)
- [一致性与第一版边界](#ai-write-sync-incremental-一致性与第一版边界)

<a id="ai-write-sync-incremental-数据源增量同步"></a>
### 数据源增量同步

`data-sync` 是可选模块。增量模式把经过框架 RouteSession 的写请求，在源事务提交成功后异步重放到目标组；复用 `INode`，不把 MySQL SQL 原文发送给 PostgreSQL。另提供 MySQL/PostgreSQL 全量 API，按主键分页复制，并在目标库保存检查点。

当前采用**轻量同步：日常增量正常运行，全量只在人工维护时调用**。启动只校验数据源组并启动增量消费，不扫描实体或执行全量。内部暂停的是增量消费者，不是业务接口或数据库写权限；全量前必须由业务停写，不能把暂停消费当作不停写同步方案。

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>data-sync</artifactId>
    <version>3.0</version>
</dependency>
```

先按[多数据源注册](#ai-datasources-routing)配置 MySQL、PostgreSQL 两个数据源。`source` / `target` 是框架注册的 **group**，不是 Spring DataSource Bean 名；规则内未注册组会阻止同步模块启动。业务表需通过原有 Schema 配置或迁移脚本准备；全量 API 只自动创建自身的进度表。

```yaml
yulinlin:
  sync:
    enabled: true                  # 默认 false；不引入模块或关闭时不注册同步过滤器
    capacity: 10000                # 每条规则最多保留的写节点数，不是字节数
    full-batch-size: 1000           # 全量 API 每批读取/提交条数，1～10000；与增量 capacity 无关
    rules:
      - name: video-search         # 可省略，默认 mysql->postgresql
        source: mysql
        target: postgresql
        entities:
          - com.example.video.VideoEntity
          - com.example.video.CommentEntity
```

<a id="ai-write-sync-incremental-实体通配符"></a>
#### 实体通配符

`entities` 按写请求 `fromClass.getName()` 匹配并按类缓存结果；增量模式不扫描实体包。表达式是 Java 完整类名，不是表名：

| 表达式 | 含义 |
| --- | --- |
| `com.example.video.VideoEntity` | 指定类 |
| `com.example.video.*` | 当前包内的类，不跨包 |
| `com.example.video.**` | 当前包及子包内的类 |
| `com.example.*.entity.**` | 中间一级包通配，entity 下递归 |
| `com.example.video.*Entity` | 当前包下以 Entity 结尾的类 |

`**` 必须独占一个包段；多个表达式命中同一类只生成一个规则任务。不同规则可以分别转发到不同目标。规则名称不能重复，同一 source/target 应合并在一条规则内；entities 为空、源目标相同、capacity 非正数均启动报错。实体来自继承父类、DTO 或业务投影时，匹配的仍是本次请求的实际 fromClass，不会自动改成其父类。

<a id="ai-write-sync-incremental-执行语义"></a>
#### 执行语义

1. 业务通过 Model/Request 正常写入，无须改每个调用点。
2. RouteSession 的 `before` 已确定最终源组、表名和节点；实际写后过滤器生成独立快照，不保留业务 root。事务回滚会丢弃提交回调。
3. 最外层提交成功后立即尝试入队，不等待凑批。每条规则只有一个顺序消费者，不同规则可并发。
4. 目标通过 `RouteSession.replayWrite` 直接重放已准备节点：保留源 ID/字段值，跳过业务 before/after 过滤器，不再次补 ID/默认时间，不递归产生同步任务。目标事务、缓存失效和 SQL 日志仍然生效。

目标的租户隔离、改表名等业务过滤器也不会执行，因此只向明确授权、结构映射兼容的目标组同步。两个数据源应使用相容的字段映射与命名规则，不能借目标过滤器重新改写源表名。目标缓存失效默认跟随其配置，请求显式 clearCache 值会保留。

| 写入类型 | 第一版行为 |
| --- | --- |
| 插入 | 重放相同 ID/值，不是 upsert；目标有重复主键时正常报错并暂停 |
| 任意条件更新/删除 | 重放相同条件与字段节点，包括 inc/dec；影响行数为 0 也可以重放 |
| 批量操作 | 一个任务携带原请求的节点集合与 batch 选项；容量按节点数计 |
| `.queue()` | 排队时不捕获，后台实际执行、事务提交后才入同步队列 |
| EntityCounter | 捕获聚合后实际执行的写请求，不捕获每一次 submit |
| 懒同步代理 | 代理在提交阶段实际刷出的框架写请求也登记同步任务 |
| 自定义 SQL / fromClass=null 或 Object.class | 不自动同步 |
| 混入原始 Expression、第三方自定义节点 | 不静默复制：快照失败，规则暂停并报告可能缺口 |

节点结构由模块独立复制，普通字段值复用 lang 深克隆；无需实体实现 Serializable。日期、集合、Map、原子数字等沿用深克隆能力。无法深克隆的自定义值（例如当前不支持的 record 或没有适用构造器的 Bean）会报告失败，不会共享可变引用冒充快照。同步目标不执行对象生命周期钩子，节点中的源字段值才是同步内容。

<a id="ai-write-sync-incremental-状态与人工恢复"></a>
#### 状态与人工恢复

通过注入 `com.yulinlin.sync.SyncManager` 查看状态，不提供管理页面或 HTTP 管理端点：

```java
// 方法体片段，syncManager 由 Spring 注入。
List<SyncStatus> statuses = syncManager.statuses();
```

状态包含规则、源/目标、STARTING/RUNNING/FULL_SYNC/PAUSED/CLOSED、待处理任务数/节点数、完成任务数、拒绝请求数、失败任务 ID、最近错误及 `requiresRepair`。RUNNING 表示规则启用，不代表此刻正在执行 SQL；没有任务时不会创建轮询线程。该接口展示增量规则，不列出进度表中的全部全量任务。

- 目标执行失败或任务确认失败：保留队列头，暂停规则，不自动重试、不让后续任务越过。其他规则不受影响。
- 暂停期间仍可接收新任务，达到容量后拒绝并记录缺口。执行中和失败头任务也占容量；单个请求超过容量同样拒绝。
- 源提交后的队列满、存储失败不会回滚源事务，也不使业务“自动重试插入”；模块输出中文 ERROR 并更新状态。快照失败也会暂停并标记可能缺口，即便之后源事务回滚，该告警也不会自动解除。
- `requiresRepair=false` **不表示目标一定没提交**。执行报错也可能需要人工确认，尤其是 inc/dec 和提交结果不确定的情况。

人工确认数据已修复、重试不会重复执行后才调用：

```java
syncManager.resumeAfterRepair("video-search", false); // 保留并重试头任务
// 二选一：确认失败头任务已在目标提交，或已经人工补齐其影响：
syncManager.resumeAfterRepair("video-search", true);  // 丢弃该失败头任务，继续后续任务
```

`true` 只允许存在失败任务 ID 时使用，不会把“队列满”的正常头任务随意丢弃。调用表示用户已经核对并修复全部缺口，不是自动修复或自动对账。暂停不自动冻结源库；修复期间宜暂停相关源写入。

<a id="ai-write-sync-incremental-一致性与第一版边界"></a>
#### 一致性与第一版边界

- 这叫**请求级单向增量同步**，不是 CDC/数据库复制。只捕获 RouteSession 写入，不捕获数据库客户端、触发器、原生 JDBC 或其他应用直接改库。
- 增量本身不补历史；可在停写窗口用全量 API 准备初始数据。相同条件在不同库可能命中不同记录，受初始数据、同步延迟、比较规则、排序和时间表达式影响。
- 每条规则仅保证任务入队顺序，不保证源端并发事务内部的执行顺序。一个源事务中的多次请求是多个目标事务，不保证跨请求原子性；目标再次被其他业务修改也可能产生差异。
- 内置存储是进程内有界队列；源提交到入队之间、进程崩溃和关闭时都可能丢任务，没有持久化历史、exactly-once 或跨库回滚保证。capacity 限制节点数，不限制单个字段字节数，也不覆盖提交前事务持有的快照。
- 关闭只停止接收/调度，不强行打断正在执行的目标事务；剩余内存任务记录警告，不承诺排空。重新启动不会自动恢复失败状态与积压，需人工对账。
- 不做双向冲突处理，也不做级联转发：A→B 的重放不会触发 B→C。需要多个目标时直接配置 A→B、A→C。
- 全量是按实体主键复制/覆盖，不删除目标额外记录，不是完整数据库备份；只提供人工 API，不提供启动自动全量、创建时间筛选、在线一致性快照或自动对账。全量检查点不等于增量队列具备持久化可靠性。

新增模块本轮仅完成代码和静态核对，回归测试源码位于 admin 测试目录，按要求未编译、未运行测试；尚未验证真实 MySQL/PostgreSQL 服务端组合。

---

下一步：返回[批量与同步主题目录](write-sync/README.md)按需选择相关专题。

---

<a id="ai-write-sync-full"></a>
<!-- source: doc/write-sync/full.md -->
<a id="ai-write-sync-full-人工全量同步与续传"></a>
## 人工全量同步与续传

[文档首页](README.md) / [批量与同步](write-sync/README.md)

全量由人工调用，基于主键进度续传；操作前必须业务停写。

前置配置与失败恢复见[增量同步](#ai-write-sync-incremental)。这里是人工全量，不是业务持续写入期间的无缝快照同步。

<a id="ai-write-sync-full-本页导航"></a>
### 本页导航

- [全量 API：自动读取主键进度](#ai-write-sync-full-全量-api自动读取主键进度)
- [人工全量流程：业务停写，内部暂停增量](#ai-write-sync-full-人工全量流程业务停写内部暂停增量)

<a id="ai-write-sync-full-全量-api自动读取主键进度"></a>
### 全量 API：自动读取主键进度

完成下面的停写、排空步骤后，注入 `com.yulinlin.sync.DataSyncClient` 调用；无需自己查询最后一条记录或记录主键。人工 API 不要求实体命中增量 `entities`，调用方显式传入实体类，并自行确认权限和迁移范围：

```java
import com.yulinlin.sync.DataSyncClient;
import com.yulinlin.sync.SyncResult;
import org.springframework.stereotype.Service;

@Service
public class VideoSyncService {
    private final DataSyncClient dataSyncClient;

    public VideoSyncService(DataSyncClient dataSyncClient) {
        this.dataSyncClient = dataSyncClient;
    }

    // 前提：已停写、在途事务结束、相关增量队列排空。
    // 不加 @Transactional；人工触发后台维护任务，方法阻塞到完成或失败。
    public SyncResult syncVideos() {
        return dataSyncClient.sync("mysql", "postgresql", VideoEntity.class);
    }

    // 明确开启新一轮：从头复制，但不清空目标表。
    public SyncResult restartVideos() {
        return dataSyncClient.restart("mysql", "postgresql", VideoEntity.class);
    }
}
```

仅用人工 API 时可以不配置增量规则：

```yaml
yulinlin:
  sync:
    enabled: true
    full-batch-size: 1000
    rules: []
```

| 调用 / 状态 | 行为 |
| --- | --- |
| 首次 `sync(source, target, class)` | 主键升序，从第一批开始 |
| 中断或失败后再次 `sync(...)` | 保持停写且源数据未变化时，从 `主键 > 最后成功主键` 继续，不使用 OFFSET；若已恢复写入，先核对是否需 `restart` |
| 已完成后再次 `sync(...)` | 返回已有完成结果，本次复制数为 0；不会自动追加源库新增数据 |
| `restart(...)` | 新任务 ID、清零该任务进度，从头按主键新增或覆盖；不删除目标额外数据 |
| 映射、主键结构或实际连接指纹变化 | 拒绝沿用旧进度，核对后使用 `restart` |
| 检测到全量期间源写入 | 暂停相关增量规则，全量进度标记 `RESTART_REQUIRED`；停写、人工修复并排空规则后 `restart`，不能直接续传 |

`SyncResult` 返回 `taskId/source/target/entity/status/lastPrimaryKey/completedRows/rowsThisRun/batchesThisRun/elapsedMillis`。`completedRows` 是本轮累计复制的源行数，`rowsThisRun` 是这次调用确认提交的源行数，不是数据库 UPDATE 的 changed-rows；覆盖相同值也计入复制数。失败会抛异常，并保留可恢复的检查点。

**主键与字段要求：**

- 使用完整单表 `@JoinTable` 实体，主键声明 `@JoinMeta(primaryKey = true)`，且与两端实际单列主键一致；不要求实体 `autoSchema=true`。
- 主键支持 `String`、byte/short/int/long 及其包装类、`BigInteger`；不支持复合主键、浮点主键、UUID Java 类型。主键不能为空，需保持稳定；字符串按源数据库排序，不执行数字“+1”。
- 复制实体声明的持久化字段，包括 `null`、`update=false`、ID 和时间；跳过 `exist=false`、计算字段、关联加载字段。使用源 CoderManager 解码、目标 CoderManager 编码，复用对应 Session 的值读取/绑定策略。
- 拒绝聚合投影和 JoinTableList 联表实体。不经过业务过滤器、代理、自动补时间或增量回放；不能依赖租户过滤器限制迁移范围，也不支持动态分表。应由有权限的维护代码调用，模块不公开 HTTP 管理接口。
- 仅支持 MySQL/PostgreSQL。目标 MySQL 业务表与进度表必须是 InnoDB；触发器、生成列、自增序列、额外唯一约束、字段范围和排序规则需人工确认。复制显式主键不会自动调整 PostgreSQL sequence；序列型主键后续插入前应自行校准。

**进度、事务和日志：**

进度存入目标连接默认库/schema 的 `yulinlin_sync_progress`，任务按源 group + 目标 group + 实体类区分；每个任务只保存最新一轮进度，不保存所有历史轮次。需要该表的建表和读写权限，业务表需 SELECT/INSERT/UPDATE 权限。每批数据和检查点使用同一目标连接提交，失败回滚本批；已提交批次不回滚。目标按主键区分 INSERT/UPDATE，不使用 MySQL 任意唯一键冲突覆盖。每批提交后主动清理目标实体缓存。

运行全量时持有源、目标各一个维护连接；目标事务按批提交，不使用多连接批处理。相同任务通过目标库会话锁防止重复运行。进程异常退出后可续传，连接断开后的锁释放以数据库为准；持久化 RUNNING 不代表仍有工作线程。不要同时用不同 group 别名/不同实体启动对同一物理表的全量任务。

日志示例（数值仅示意）：

```text
全量同步开始 源=mysql 目标=postgresql 实体=com.example.VideoEntity 任务=... 每批=1000 恢复主键=2000 累计完成=2000
全量同步进度 源=mysql 目标=postgresql 实体=com.example.VideoEntity 本批=1000 本次完成=1000 累计完成=3000 最后成功主键=3000 本批耗时=120ms
全量同步完成 源=mysql 目标=postgresql 实体=com.example.VideoEntity 本次完成=1000 累计完成=3000 批次=1 总耗时=135ms
```

不预先 COUNT 全表，所以不提供百分比。异常日志提示排查和续传；提交确认异常时，以目标进度表实际值为准，不用旧内存游标覆盖已提交进度。

<a id="ai-write-sync-full-人工全量流程业务停写内部暂停增量"></a>
### 人工全量流程：业务停写，内部暂停增量

按下面的顺序操作，无需业务再写一套暂停/恢复增量消费者的代码：

1. **业务停写**：关闭相关源/目标业务写入口，覆盖所有应用实例、定时任务及其他程序的写入。先停止产生新任务，再让 EntityCounter、`.queue()` 和在途事务处理完；不能只暂停 HTTP 接口。
2. **排空增量**：保持增量消费正常运行，等待提交后的同步任务完成。用 `syncManager.statuses()` 核对写向本次目标组的规则无积压、无错误。不要先关闭整个同步模块，否则不能排空。
3. **人工调用**：在业务事务之外调用 `sync(source, target, class)`，需要重新覆盖全部历史数据时调用 `restart(...)`。API 会再次检查规则是否空闲；尚在执行、积压、已暂停或被其他全量占用时直接报错，不自动等待、不丢弃任务。状态计数只是观察入口，最终以 API 检查为准。
4. **内部暂停**：检查通过后，本进程所有写向该目标 group 的增量规则进入维护保护，不再调度消费；不只是当前实体或当前源组。随后逐批复制，业务行与检查点同批提交。
5. **完成并恢复**：API 正常返回时释放内部维护保护，未处于错误暂停状态的规则恢复正常调度。核对结果、日志和规则状态后，再由业务恢复写入。正常全量完成不需要调用 `resumeAfterRepair`。

**内部暂停不等于业务停写。** 不会拦截用户请求、不撤销源事务，也不会停止其他应用实例、原生 SQL 或数据库客户端的修改。若其他源组的规则也写向这个目标 group，同样需要协调它们的停写。目标读取未被禁止，但全量按批提交，期间可能看到部分新、部分旧的数据，不能把它当作整表原子切换。

| 情况 | 内部处理 | 业务操作 |
| --- | --- | --- |
| 全量正常完成 | 释放维护保护，正常规则恢复调度 | 核对后恢复写入 |
| 普通读取/写入失败，没有源写入冲突 | 回滚未提交批次，保留已提交进度；退出时释放维护保护，正常规则可再次调度 | 保持停写，排查后续传；不能因为消费者已恢复就认定全量成功 |
| 全量期间检测到匹配规则的源写入或提交 | 标记冲突、暂停相关增量规则；全量任务处理中检测到冲突会中止并记录需要重做 | 保持停写，人工核对、修复和处理积压，再 `restart`；不要盲目重放 inc/dec |
| 进程退出 | 全量检查点已落在目标库；内存增量任务和运行状态不保证保留 | 重启后先确认数据及缺口，不把“队列空了”当作已同步成功 |

失败恢复期间也需保持停写；如果期间已恢复写入，旧游标之前的数据可能变化，需要核对并重新全量。全量不会删除目标额外记录，漏同步的删除仍需人工清理。正常匹配规则之外、其他进程或原生 SQL 的修改可能检测不到，因此这**不是不停机快照与 CDC 无缝衔接**。

启动自动全量及其扩展入口已移除；升级时删除旧配置中的 `rules[].full`，不再填写 true/false。`full-batch-size` 仍保留，用于人工全量 API 每批读取/提交数量，不是自动全量开关。

---

下一步：返回[批量与同步主题目录](write-sync/README.md)按需选择相关专题。

---

<a id="ai-write-sync-batch"></a>
<!-- source: doc/write-sync/batch.md -->
<a id="ai-write-sync-batch-批量与多连接写入"></a>
## 批量与多连接写入

[文档首页](README.md) / [批量与同步](write-sync/README.md)

批次大小、连接并发和事务提交是不同概念。

<a id="ai-write-sync-batch-本页导航"></a>
### 本页导航

- [批量与多连接写入](#ai-write-sync-batch-批量与多连接写入-1)

<a id="ai-write-sync-batch-批量与多连接写入-1"></a>
### 批量与多连接写入

方法体片段，usersToInsert 为已准备好的 DemoUser 集合：

```java
ModelInsertWrapper.newInstance("mysql", usersToInsert).execute();         // 普通 JDBC batch
ModelInsertWrapper.newInstance("mysql", usersToInsert).batch().execute(); // 申请多连接路径
```

```yaml
yulinlin:
  mysql:
    parallel-connections: 4
    execute-batch-size: 256
  postgresql:
    parallel-connections: 2
    execute-batch-size: 128
  h2:
    parallel-connections: 4
    execute-batch-size: 256
  sqlite:
    execute-batch-size: 256
```

这些数都必须是正整数，并按数据库模块独立生效。MySQL、PostgreSQL 与 H2 默认最多 4 个连接；调用 `.batch()` 时，把整批数据均匀分成最多 4 个大组，一组一个任务/连接。同 SQL 复用 PreparedStatement，每满配置行数执行一次 executeBatch，尾批也执行。SQLite 固定单写连接，但仍可独立设置 execute-batch-size。

只有 .batch()、执行器、请求阈值和 supportsParallelWrites 等条件满足才并发；SQLite 或有效并发数为 1 时不拆组。Hikari 池会自动限制有效并发数，其他连接池应按其容量设置 parallel-connections。H2 可拆组，但多个连接仍受文件锁、索引和写入热点影响。4 是每 Session、每框架事务的上限，不是整个应用并发上限，也不保证 4 倍速度。

executeBatch 不是 commit。所有已提交任务结束后再提交/回滚及释放；失败不能让工作线程继续在已归还连接上执行。解析仍持有完整输入集合，不是流式导入。SUCCESS_NO_INFO 按成功命令计数，不保证精确行数。

多个连接有各自的本地事务，中途提交失败无法撤销已成功提交的连接；跨连接未提交数据也不保证可读。严格单库原子性和事务内读己之写使用 parallel-connections: 1。

---

下一步：返回[批量与同步主题目录](write-sync/README.md)按需选择相关专题。

---

<a id="ai-write-sync-counter"></a>
<!-- source: doc/write-sync/counter.md -->
<a id="ai-write-sync-counter-entitycounter-聚合插入与更新"></a>
## EntityCounter 聚合插入与更新

[文档首页](README.md) / [批量与同步](write-sync/README.md)

点击量自增、模型增量和按非数字维度聚合插入。

<a id="ai-write-sync-counter-本页导航"></a>
### 本页导航

- [实体计数器](#ai-write-sync-counter-实体计数器)
- [通过模型提交更新增量](#ai-write-sync-counter-通过模型提交更新增量)
- [按维度聚合插入统计数据](#ai-write-sync-counter-按维度聚合插入统计数据)

<a id="ai-write-sync-counter-实体计数器"></a>
### 实体计数器

`common` 的 `EntityCounter` 面向播放量、点击量、热度等数值累加。`update(...)` 表示按主键更新已有记录，`insert(entity)` 表示按维度聚合插入新记录；`execute()` 立即执行，`submit()` 交给后台聚合。旧 `newInstance(...)` 已移除，调用方统一改为 `update(...)`。一次更新 Builder 只描述一行，但同一行可以同时修改多个字段：

```java
import com.yulinlin.common.model.EntityCounter;

EntityCounter.update("mysql", VideoEntity.class, videoId)
        .inc(VideoEntity::getViewCount, 1)
        .inc(VideoEntity::getHotValue, 5)
        .dec(VideoEntity::getRemainingQuota, 1)
        .submit();
```

多行由业务代码正常循环，不提供容易混淆的链式 `.id(...)` 批量语法：

```java
for (VideoStat stat : stats) {
    EntityCounter.update("mysql", VideoEntity.class, stat.videoId())
            .inc(VideoEntity::getViewCount, stat.views())
            .inc(VideoEntity::getHotValue, stat.hotValue())
            .submit();
}
```

不传数据源时复用 RouteSession 的默认路由规则；单组自动选择，多组使用 `yulinlin.datasource.default-group`，实体上的 `@JoinSession` 仍可参与路由：

```java
EntityCounter.update(VideoEntity.class, videoId)
        .inc(VideoEntity::getViewCount)
        .submit();
```

终结方法有两种，Builder 终结后不能再次执行或提交：

| 方法 | 行为 | 返回值 | 事务 |
| --- | --- | --- | --- |
| `execute()` | 立即生成 `column = column + ?` 原子更新 | 数据库影响行数 | 加入当前路由事务，包括适配后的 `@Transactional` |
| `submit()` | 合并进进程内计数区，定时或达到阈值后批量写入 | `void` | 必须在事务外调用，后台使用独立事务 |

聚合键是“调用线程解析出的数据源组 + 实体类型 + 主键”，字段增量保存在该行下面。默认选库和显式指定相同组时可以合并，模型更新与链式更新也共用聚合区。因此相同视频的一百次 `viewCount + 1` 最终可以变成一次 `view_count = view_count + 100`；同一行的 `viewCount`、`hotValue` 等字段也会进入一条 UPDATE。刷新时再按数据源和实体合成批量请求，底层继续使用现有 SQL 解析、JDBC batch、缓存失效和日志能力。

```yaml
yulinlin:
  data:
    counter:
      flush-interval: 1m
      flush-size: 1024
      clear-cache: false
```

`flush-size` 统计待刷新的更新行、插入维度组及待重试插入行总数，不是调用次数；达到阈值立即异步刷新。刷新采用稳定快照，新提交进入下一轮；更新分组失败时把增量合回活动区，插入分组失败则保留原批次等待重试。Spring 正常关闭会尝试最后刷新。可以注入 `EntityCounterManager` 调用 `flush()` 手动刷新或用 `pendingRows()` 查看待处理行数（不包括正在执行的首次刷新快照）。

上述按主键更新模式要求字段是可更新的数值属性，实体必须有且只有框架可识别的主键；`@JoinField(exist=false)`、`@JoinField(update=false)` 和主键字段不能作为更新计数器。`inc(field, x)` 表示加上 x，`dec(field, x)` 表示减去 x，两者都支持正负整数、小数，零不变；`dec(field, -0.5)` 等价于 `inc(field, 0.5)`。不传数量默认 1。

数量支持 `Byte/Short/Integer/Long/Float/Double`、`BigInteger/BigDecimal`、`AtomicInteger/AtomicLong/LongAdder/DoubleAdder`，不接受 null、NaN、无穷大或未支持的自定义 Number 类型。链式调用时立即读取数字快照，之后修改原子类不会改变已记录增量。内部统一使用 BigDecimal 精确聚合，不再受 long 范围限制；正负增量抵消为零时不生成该字段更新。浮点数已有的运算误差不会自动修复，精确小数建议 `new BigDecimal("0.1")`。最终写入仍受数据库列类型、范围和精度约束，不会自动修改表结构。

```java
EntityCounter.update("mysql", VideoEntity.class, videoId)
        .inc(VideoEntity::getHeat, new BigDecimal("0.75"))
        .dec(VideoEntity::getHeat, new BigDecimal("-0.25"))
        .submit(); // 本次净增 1；改用 execute() 可立即执行
```

默认 `clear-cache=false`，避免浏览量高频更新反复清理查询缓存；业务确实要立即影响缓存查询时可单次调用 `.clearCache(true)`，或修改全局默认值。

这是内存聚合，进程异常退出可能丢失最近一个刷新周期的数据，不用于余额、库存和必须审计的数据；这些场景使用 `execute()`，或接入具有持久化确认能力的 Redis/MQ。数据库最终执行仍是原子加减，多实例同时刷新不会互相覆盖，但每个实例各自持有尚未刷新的本地增量。

<a id="ai-write-sync-counter-通过模型提交更新增量"></a>
#### 通过模型提交更新增量

只需要设置主键和本次的数字增量，不必逐个调用 `inc/dec`；原来的链式 API 仍保留。

```java
VideoEntity delta = new VideoEntity();
delta.setId(videoId);
delta.setViewCount(10L);       // 数据库原值 + 10，不是设置为 10
delta.setHotValue(5L);        // 同一行的另一个计数
delta.setRemainingQuota(-1L); // 数据库原值 - 1

EntityCounter.update("mysql", delta).submit();
// 替代选择：EntityCounter.update(delta).submit(); // 默认选库
// 替代选择：int affected = EntityCounter.update(delta).clearCache(true).execute();
```

字段规则：

| 字段 | 模型更新行为 |
| --- | --- |
| `@JoinMeta(primaryKey=true)` | 必须恰好一个且值非空，仅生成主键条件；即使是数字也不参与累加，允许 `update=false` |
| 可更新数字字段 | 正值增加、负值减少，null 和 0 忽略；支持多个字段 |
| 字符串、时间、枚举、布尔等非数字字段 | 忽略，不作为条件或覆盖赋值 |
| `@JoinField(exist=false/update=false)`、静态、transient、合成字段 | 不参与增量更新 |

支持 byte/short/int/long/float/double 及包装类、BigInteger、BigDecimal、AtomicInteger、AtomicLong、LongAdder、DoubleAdder。原子类读取当前值或 sum，不修改/清零调用对象；NaN、无穷和不支持的 Number 子类拒绝提交。数字增量在内存中用 BigDecimal 合并，避免大整数或小数被转成 long 截断；不承诺数据库浮点列的十进制精度。

注意：

- 必须使用新建的“增量模型”，不要将查回的完整实体直接传入，否则所有非零可更新数字（包括状态码、排序值、默认值）都会成为增量。此接口不是普通 `update(entity)`。
- 在 `execute/submit` 时读取快照，随后修改实体或原子类不会改变已提交数据；同一对象重复提交仍会重复累计。不要并发修改同一个模型来期待多字段整体原子快照。
- 仅生成数字加减和主键条件，不调用实体 `updateBefore`，不自动更新时间或执行版本号乐观锁逻辑。无需通过无参构造器复制实体。
- 所有增量为空/零时 `execute()` 返回 0，`submit()` 不入队；同一窗口相反增量抵消后不执行 SQL。主键不存在则更新 0 行，不会自动插入。
- SQL 更新字段必须是支持算术运算的数据库数字列。BigDecimal/BigInteger 自动建表仍沿用原存储规则；如果列为 TEXT/VARCHAR，尤其 PostgreSQL，需要业务先迁移为合适的 NUMERIC 列，本功能不自动改列。最终数值范围/精度由数据库决定；列值为 NULL 时也不自动补零。
- 模型和链式计数更新共享刷新间隔、阈值、缓存策略和失败恢复机制；`execute()` 可加入当前事务，`submit()` 必须在事务外调用。进程内聚合不是可靠消息队列。

<a id="ai-write-sync-counter-按维度聚合插入统计数据"></a>
#### 按维度聚合插入统计数据

`EntityCounter.insert(entity)` 不要求数据库中已有记录。它读取提交对象：全部非空数字字段分别求和，全部非空非数字字段的 `toString()` 作为维度；时间也作为维度，不截断为分钟。`null` 忽略，`0` 仍是有效数字，`false` 是维度。`@JoinWhere` 只影响查询条件，不控制这里的分组。

```java
import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.common.model.EntityCounter;
import com.yulinlin.data.core.anno.JoinTable;
import lombok.Data;
import java.util.concurrent.atomic.DoubleAdder;

@Data
@JoinTable("sta_metrics")
public class StaMetricsEntity extends SuperEntity<StaMetricsEntity> {
    private String name;
    private String channel;
    private String deviceType;
    private String osName;
    private DoubleAdder metrics = new DoubleAdder();
}
```

业务每次构造一份新增贡献即可：

```java
StaMetricsEntity stat = new StaMetricsEntity();
stat.setName("播放量");
stat.setChannel("渠道A");
stat.setDeviceType("web");
stat.setOsName("Windows");
stat.getMetrics().add(1.5);

EntityCounter.insert("mysql", stat).submit();       // 默认一分钟聚合插入
// 不指定数据源：EntityCounter.insert(stat).submit();
// 立即插入：int affected = EntityCounter.insert(stat).execute();
// 单次覆盖缓存策略：EntityCounter.insert(stat).clearCache(true).submit();
```

三个入口示例是替代选择，不应把同一份贡献重复提交。`submit()` 返回 void，不代表已落库；活动事务中拒绝 submit，`execute()` 则立即插入并加入当前事务。Builder 只能终结一次。

分组键为“调用线程解析出的数据源组 + 实体类型 + 非数字字段名/字符串值集合”，不是简单拼接字符串。相同维度的 `metrics=1.5` 和 `metrics=2.5` 合成一条 `metrics=4.0` 的新记录；维度不同则分别插入。下一轮继续生成新记录，不查询旧记录、不做 upsert。省略数据源时复用实体 `@JoinSession`、当前注解选库作用域和默认组规则，提交时固定组名，避免后台线程丢失调用方选库上下文；路由过滤器仍在实际执行时运行。

| 数字类型 | 插入聚合规则 |
| --- | --- |
| byte/Byte、short/Short、int/Integer、long/Long | 同名字段相加，超过原类型范围时拒绝本次提交，不部分更新其他字段 |
| float/Float、double/Double | 同名字段相加，拒绝 NaN/无穷及结果溢出；不承诺十进制精度 |
| BigInteger、BigDecimal | 内存中精确相加，不转成 double；数据库列容量仍由表结构决定 |
| AtomicInteger、AtomicLong | 提交时读取数值快照，累加后仍按原字段类型还原 |
| LongAdder、DoubleAdder | 提交时读取 sum 快照，不保留或清零调用者的累加器 |

一行可以有多个数值字段，数字的正负值和零都允许。内部用不可变精确值求和，浮点字段在还原时转回原类型。`Boolean/boolean/AtomicBoolean` 是维度；AtomicReference、原子数组、LongAccumulator/DoubleAccumulator 以及其他自定义 Number 暂不支持，直接报错，不静默取 long 导致截断。声明为 Number/Object 的字段如果实际数字类型在提交间变化，也会拒绝合并；推荐声明具体数字类型。

补充约束：

- 实体须有 `@JoinTable`、可用的无参构造器和可写字段。静态、transient、合成字段与 `@JoinField(exist=false)` 不参与；`update=false` 不影响插入。
- null 不是通配符：`channel=null` 与 `channel=A` 不同组；数字字段的 null 不增加维度，也不贡献数值。
- `submit/execute` 时复制维度值并读取数字快照，之后修改原实体不影响已提交数据。复杂维度对象需支持框架 deepClone；推荐字符串、枚举、布尔和时间等简单类型，并保证 toString 稳定。同一对象重复提交会重复累计；不要并发修改它的多个字段来期待整体原子快照，Adder.sum 本身也不保证并发下的严格原子快照。
- 不预填主键、创建时间。它们为 null 时不进入分组，真正构造插入请求时由 `insertBefore` 补齐。如果业务已经赋值，非数字 ID/时间也会成为维度，数字 ID 则与其他数字一样累加；框架不特殊排除，所以统计模型不应提前设置数字主键。
- 维度以对象实际 toString 为准。例如 DateTime 默认字符串到秒，LocalDateTime 可含小数秒；不同字符串必定分组，不自动统一精度、时区或大小写。
- 刷新间隔复用上面的 `yulinlin.data.counter` 配置，默认 1m，达到 1024 个待处理组可提前刷新。它不是自然分钟窗口；需要分钟报表时，业务自行设置统一分钟字符串维度。
- 每个数据源/实体的插入分组使用 JDBC 批处理，但不主动拆多连接；仍通过标准 RouteSession、编解码器、日志和缓存失效路径执行。与按主键更新模式的聚合区隔离，不混成同一种请求。
- 失败插入保留已构造的请求和生成主键，下一轮重试，不和新窗口重新求和；不会重新生成 ID 重复插入。但不保证 exactly-once：提交结果不确定、过滤器或回调异常、缺少唯一约束等仍需业务处理。失败组不会因其他组成功而丢弃；持续失败会积压，flush-size 不是容量上限。

MySQL/PostgreSQL/H2/SQLite 自动建表已把原子数字和 Adder 映射为对应数字类型，AtomicBoolean 对应布尔存储；Byte/Short、AtomicInteger/AtomicLong/AtomicBoolean 的标量编解码也已注册。旧表如果把这些字段建成了 TEXT，不自动做破坏性改列，请按业务数据手动迁移。BigDecimal/BigInteger 延续各数据库模块原有存储规则。

---

下一步：返回[批量与同步主题目录](write-sync/README.md)按需选择相关专题。

---

<a id="ai-write-sync-queue"></a>
<!-- source: doc/write-sync/queue.md -->
<a id="ai-write-sync-queue-延迟写队列"></a>
## 延迟写队列

[文档首页](README.md) / [批量与同步](write-sync/README.md)

请求快照、分组、定时或阈值刷新及失败边界。

<a id="ai-write-sync-queue-本页导航"></a>
### 本页导航

- [延迟写队列](#ai-write-sync-queue-延迟写队列-1)

<a id="ai-write-sync-queue-延迟写队列-1"></a>
### 延迟写队列

适合任意插入、更新和删除的延后批量执行。它只合并请求节点，不会把相同主键的重复数值增量先求和；播放量、点击量、热度等高频计数优先使用 [EntityCounter.submit()](#ai-write-sync-counter)。三种普通写操作使用同一个入口：

```java
ModelInsertWrapper.newInstance("mysql", users).queue().execute();
ModelUpdateWrapper.newInstance("mysql", user).queue().execute();
ModelDeleteWrapper.newInstance("mysql", User.class)
        .eq(User::getId, id)
        .queue()
        .execute();
```

`execute()` 在节点成功进入内存队列后立即返回 `0`，不代表数据库影响了零行，也不返回自增主键。Wrapper 创建时已经把对象当时的字段值生成到 `INode`；入队后重新给实体字段赋值不会改变这次写入，Node 中原本保存的 List、Map、Date 等可变值引用则不要再原地修改。普通请求不调用 `.queue()`，仍按原方式立即执行。

```yaml
yulinlin:
  data:
    queue:
      flush-interval: 1m
      flush-size: 1024
      concurrency: 4
      retry-count: 3
```

`flush-size` 按当前活动队列的 Node 总数计算，不按 Wrapper 调用次数，也不按单个分组计算。总数达到阈值时会立即异步刷新当前整个队列快照，不必等待 `flush-interval`；例如多个分组各有数百个 Node，合计达到默认的 `1024` 也会立即刷新。刷新快照再按实际 `EntitySession` 实例、请求类型和 `fromClass` 统一分组；该阈值不拆分请求，也不改变事务边界。

不同 `EntitySession` 最多按 `concurrency` 并发刷新，同一 `EntitySession` 内的分组仍依次执行；单个分组调用过 `.batch()` 时，仍由该数据源的 `parallel-connections` 决定组内多连接。因此 `concurrency=4` 是同时处理的 Session 上限，不是全局物理连接上限。某组失败只重试该组，不阻塞其他 Session；写入成功后才按 `clearCache` 规则清理查询缓存。队列合并同组请求时，任一请求显式 `clearCache(true)` 就清理；全部显式为 `false` 才不清理；其余组合继续继承全局配置。

这是进程内队列，应用异常退出可能丢失尚未刷新数据，不替代消息队列。队列没有硬容量上限；如果持续生产速度高于数据库写入速度，待执行和待重试 Node 会继续占用内存，应在业务入口限流并监控积压。`.queue()` 面向零散小写入的定时聚合，不是大数据导入器；大集合直接在业务异步任务中调用 `.batch().execute()`。`.queue()` 必须在事务外调用，因为延迟任务不能加入调用线程的原事务；依赖严格先后关系、自增 ID、实时影响行数或强一致读取的写操作继续同步执行。应用正常关闭时框架会尝试最后刷新一次；数据库失败按 `retry-count` 在后续刷新中重试，超过次数后记录错误并丢弃。

队列不会为每个入队对象打印日志。开启 `com.yulinlin.data.core.queue.WriteQueueManager=DEBUG` 后，每个非空刷新快照输出一条摘要，包括触发方式、Session/分组/Node 数、影响行数、耗时和剩余积压；失败、重试和最终丢弃仍分别使用 WARN/ERROR。

---

下一步：返回[批量与同步主题目录](write-sync/README.md)按需选择相关专题。

---

<a id="ai-cache-config"></a>
<!-- source: doc/cache/config.md -->
<a id="ai-cache-config-缓存选择与公共配置"></a>
## 缓存选择与公共配置

[文档首页](README.md) / [缓存](cache/README.md)

可选实现、默认缓存选择、TTL 和启动规则。

<a id="ai-cache-config-本页导航"></a>
### 本页导航

- [公共配置](#ai-cache-config-公共配置)
- [选择实现](#ai-cache-config-选择实现)
- [启动规则](#ai-cache-config-启动规则)

<a id="ai-cache-config-公共配置"></a>
### 公共配置

```yaml
yulinlin:
  cache:
    ttl: 10m
    table-ttl:
      sys_user: 30m
      video: 5m
      "mysql.sys_config": 1h
    ttl-in-key: false
    clear-on-write: true
```

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `default` | 不指定 | 单实现自动选中；多个实现共存时必须指定 `caffeine`、`ehcache` 或 `redis` |
| `ttl` | `10m` | 查询缓存默认有效期 |
| `table-ttl` | `{}` | 按物理表名覆盖 ORM 查询 TTL；也可写 `group.table` 精确区分数据源，所有值必须大于 `0` |
| `ttl-in-key` | `false` | TTL 是否参与 ORM 查询缓存 Key；不影响 `CacheClient` 业务缓存 |
| `clear-on-write` | `true` | 成功写入后是否默认清理相关 ORM 查询缓存 |

`yulinlin.cache.clear-on-write`默认 `true`，控制写操作未显式选择时是否在提交成功后清理默认缓存中的相关 ORM 查询缓存。它不控制查询是否启用缓存，也不会清理 `CacheClient` 业务缓存或广播到其他缓存实现。`QueryCacheProperties` 由 cache-core 自动配置注册；未引入 Provider 且未显式指定 default 时使用空缓存，写路径不执行 namespace 解析和失效处理。

ORM 查询 TTL 的优先级为：请求 `.cache(Duration)` 或 Repository 方法 `@JoinCache(ttl=...)`、`group.table`、普通表名、全局 `ttl`。JOIN、`JoinTableList` 或显式声明多个缓存 namespace 时，每张表先按该顺序得到 TTL，最终使用最短值；未配置的表以全局 TTL 参与比较。自定义 SQL 无法识别资源时使用全局 TTL，调用 `cacheNamespaces("table_name")` 后可以参与表级规则。

`table-ttl` 只调整已经显式启用的 ORM 查询缓存时间，不控制是否缓存。业务不需要缓存时不调用 `.cache()`，或者使用 `.cache(false)` / `CacheMode.NONE`；表级 TTL 和全局 TTL 都必须大于 `0`。这套规则不影响 `CacheClient` 业务缓存，也不改变写入后的 namespace 失效逻辑。表名按框架解析后的物理资源名匹配，不写实体类名。

普通 `sys_user` 规则对所有数据源组中的同名表生效；`mysql.sys_user` 表示“group 为 mysql、物理表为 sys_user”的精确规则，不会修改 SQL 表名。两者同时存在时，mysql 组使用精确规则，其他组继续使用普通表规则。一般项目只写物理表名，确实存在跨数据源同名表且 TTL 不同时再加 group。

默认关闭时，只要查询条件、数据源和 namespace 版本相同，不同 TTL 的查询就会复用同一个缓存条目。已有条目被命中时不会修改其过期时间；`REFRESH`或未命中重新写入时，才使用本次请求的 TTL。这样可以减少重复数据并提高缓存复用率。

需要让不同 TTL 的相同查询完全隔离时开启：

```yaml
yulinlin:
  cache:
    ttl-in-key: true
```

开启后，解析出的全局、表级或请求级 TTL 会参与物理 Key，同一查询使用不同 TTL 时会保存为不同条目。namespace 名称和版本始终参与 Key，不受此开关影响。

<a id="ai-cache-config-选择实现"></a>
### 选择实现

三个实现可以同时引入，固定名称为 `caffeine`、`ehcache`、`redis`，不用再配置各模块的 group。它们都会传递引入 `cache-core`，业务不需要重复声明。示例（需引入对应模块，并按 Redis 专题配置连接）：

```yaml
yulinlin:
  cache:
    default: redis
    ttl: 10m
    caffeine:
      maximum-size: 10000
    ehcache:
      directory: data/cache
    redis:
      key-prefix: dev
```

- 没有实现且省略 default：照常启动，ORM 无缓存，客户端未命中。
- 只有一个已注册实现：无需配置 default，兼容原有用法。
- 多个实现：必须明确指定 default，禁止按 Bean 顺序随机挑选。
- 显式 default 或客户端指定的缓存名称不存在：明确报错，不回退。缓存名称重复也报错。判断依据是实际注册的 Provider，不是只看 YAML 配置块；例如没有 RedisConnectionFactory 时不会注册 Redis Provider。
- ORM 的 `.cache()`、CacheMode、TTL、`@JoinCache` 用法不变，只使用默认实现，不增加缓存名参数；配置默认缓存并不会自动让所有查询启用缓存。
- 客户端可指定其他实现，缓存内容各自独立，不自动复制，不按多个 Provider 依次查找，也不广播清理。

公共 ttl 等配置由这些实现复用；每个实现的容量、持久化、序列化等配置继续独立。选择在启动时固定，修改默认值需要重启。`table-ttl` 中的 `group.table` 仍指**数据源 group**，不是缓存名称。

`cache-redis` 自带的 Caffeine L1 属于 `redis`，不依赖、不注册 `cache-caffeine` Provider。只引入 Redis 模块仍然算一个缓存实现；同时引入 cache-caffeine 后，独立的 caffeine 与 redis 内部 L1 也不会共享数据。

<a id="ai-cache-config-启动规则"></a>
### 启动规则

| 项目依赖 | 启动结果 |
| --- | --- |
| 不引入缓存模块 | 正常启动；`cacheClient.getCache().available()`为 false，缓存优先查询直接访问数据源 |
| 只引入 `cache-caffeine` | 启用内存缓存 |
| 只引入 `cache-ehcache` | 启用 Heap + Disk 缓存 |
| 只引入 `cache-redis` | 启用 Redis L2；默认同时启用模块内置 Caffeine L1 |
| 多个实现且已配置 `yulinlin.cache.default` | 正常启动；ORM 仅使用默认实现，客户端可显式选择 |
| 多个实现但未配置 default | 启动报错，要求指定默认缓存 |
| 显式指定未注册名称 / 名称重复 | 报错，不静默回退 |

第一种情况下不会创建 `QueryCacheProperties`，也不会因 `clearCache(true)` 解析表 namespace。这不是“空缓存执行一遍失效”，而是写路径直接跳过整套缓存失效逻辑。

Session 的缓存由对应工厂在 `create(...)` 内初始化，`RouteSession.registerSession` 只登记已配置完成的节点，不再注入或覆盖缓存。JDBC、Elasticsearch、MongoDB 工厂与 RouteSession 使用容器中的同一个 QueryCache：Session 负责查询缓存，路由负责协调写后失效；没有 Provider 时统一使用 NoOpQueryCache。

第三方数据源继承 `AbstractSession` 时，由工厂调用其 `setQueryCache`；该初始化方法不属于 `EntitySession` 执行接口。直接实现 `EntitySession` 时，由自有工厂完成缓存集成，不要求额外实现缓存 setter。工厂和路由必须复用同一个缓存 Bean，不要各自创建 Provider，否则查询与失效可能操作不同的缓存。

---

下一步：返回[缓存主题目录](cache/README.md)按需选择相关专题。

---

<a id="ai-cache-caffeine"></a>
<!-- source: doc/cache/caffeine.md -->
<a id="ai-cache-caffeine-caffeine-内存缓存"></a>
## Caffeine 内存缓存

[文档首页](README.md) / [缓存](cache/README.md)

引入依赖、容量、淘汰和统计配置。

<a id="ai-cache-caffeine-本页导航"></a>
### 本页导航

- [Caffeine 内存缓存](#ai-cache-caffeine-caffeine-内存缓存-1)

<a id="ai-cache-caffeine-caffeine-内存缓存-1"></a>
### Caffeine 内存缓存

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>cache-caffeine</artifactId>
    <version>3.0</version>
</dependency>
```

调用链为 `Caffeine → 数据源`。它适合追求最低本地延迟、允许应用重启后缓存消失的服务。

```yaml
yulinlin:
  cache:
    ttl: 10m
    ttl-in-key: false
    caffeine:
      initial-capacity: 128
      maximum-size: 10000
      expiration-policy: after-write
      use-system-scheduler: false
      executor: common-pool
      record-stats: false
```

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `initial-capacity` | `16` | Caffeine 内部表的初始容量；只影响扩容次数，不会一次性创建全部缓存对象 |
| `maximum-size` | `10000` | 最大条目数；ORM 查询缓存与 `CacheClient` 业务缓存共用该容量 |
| `expiration-policy` | `after-write` | `after-write` 写入后固定 TTL；`after-access` 每次命中后按该条目的 TTL 续期 |
| `use-system-scheduler` | `false` | 使用 JDK 系统调度器更及时地触发过期维护；关闭时由正常读写维护，过期项仍不会被命中 |
| `executor` | `common-pool` | `common-pool` 异步执行维护任务；`direct` 在调用线程执行，少一次调度但可能增加请求延迟 |
| `record-stats` | `false` | 是否记录命中率、淘汰数和加载统计；需要观测时显式开启，默认关闭以减少热点路径计数开销 |

`expiration-policy`同时作用于 ORM 查询缓存和业务缓存。请求级 `cache(Duration)` 或 `Cache.set(..., Duration)` 仍决定各条目的 TTL，策略只决定读取命中后是否重新开始计时。大多数接口服务建议保留 `after-write + common-pool`；会话类热点数据才考虑 `after-access`。

需要监控时先配置 `record-stats: true`，再直接注入具体实现：

```java
@Resource
private CaffeineQueryCache caffeineQueryCache;

long entries = caffeineQueryCache.estimatedSize();
CacheStats stats = caffeineQueryCache.stats();
caffeineQueryCache.cleanUp(); // 主动执行待处理的过期和容量淘汰维护
```

---

下一步：返回[缓存主题目录](cache/README.md)按需选择相关专题。

---

<a id="ai-cache-ehcache"></a>
<!-- source: doc/cache/ehcache.md -->
<a id="ai-cache-ehcache-ehcache-持久化缓存"></a>
## Ehcache 持久化缓存

[文档首页](README.md) / [缓存](cache/README.md)

内存与磁盘容量、持久化及配置边界。

<a id="ai-cache-ehcache-本页导航"></a>
### 本页导航

- [Ehcache 持久化缓存](#ai-cache-ehcache-ehcache-持久化缓存-1)

<a id="ai-cache-ehcache-ehcache-持久化缓存-1"></a>
### Ehcache 持久化缓存

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>cache-ehcache</artifactId>
    <version>3.0</version>
</dependency>
```

调用链为 `Ehcache Heap → Ehcache Disk → 数据源`。Ehcache 自己管理内存热点层和磁盘层，不需要再引入 Caffeine。

```yaml
yulinlin:
  cache:
    ttl: 10m
    ttl-in-key: false
    ehcache:
      directory: ./data/cache
      heap-entries: 10000
      offheap-size-mb: 0
      disk-size-mb: 1024
      persistent: true
      expiration-policy: after-write
      disk-threads: 2
      serializer: json
      maximum-entry-size-mb: 16
      record-statistics: false
```

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `directory` | `data/cache` | 磁盘缓存目录，同一时间只能由一个 CacheManager 占用 |
| `heap-entries` | `10000` | JVM 堆内热点条目数，ORM 查询与 `CacheClient` 共用 |
| `offheap-size-mb` | `0` | JVM 直接内存中的可选中间层；`0` 表示关闭。可降低大缓存对 GC 的影响，速度通常快于磁盘，但会增加序列化、内存复制和进程总内存占用 |
| `disk-size-mb` | `1024` | 整个 Ehcache 磁盘层的容量上限，不是单条缓存上限；空间满后淘汰旧条目。Ehcache 磁盘资源池要求设置有限容量，不提供无限增长模式 |
| `persistent` | `true` | 是否在正常关闭并重启后保留磁盘缓存 |
| `expiration-policy` | `after-write` | `after-write` 写入后固定 TTL；`after-access` 每次命中后按该条目的 TTL 续期 |
| `disk-threads` | `2` | Ehcache 磁盘层固定工作线程数；本地 SSD 通常使用 `2` 到 `4` |
| `serializer` | `json` | Value 格式：`json`、`smile` 或 `kryo` |
| `maximum-entry-size-mb` | `16` | 单条序列化结果上限，超出后记录并跳过，防止一个大列表挤占缓存 |
| `record-statistics` | `false` | 是否记录框架层命中、未命中、写入、超大条目和序列化失败计数；需要观测时显式开启 |

正常关闭时框架调用 `close()`。即使 `persistent: true`，缓存仍应被视为可丢失数据；缓存损坏或反序列化失败会删除对应项并回源查询。缓存量不大时保持 `offheap-size-mb: 0` 即可；只有缓存较大或 GC 压力明显时才建议从 `256` 或 `512` MB 开始启用。容器内存预算必须同时包含 Heap、Off-Heap、线程栈和 JVM 自身开销。

Ehcache 的业务 Value 统一保存为带序列化格式和 TTL 的原始 `byte[]`，Heap、Off-Heap、Disk 不再额外保存一个 Java 包装对象。格式信息随条目保存，因此切换 `json`、`smile` 或 `kryo` 后仍可读取尚未过期的其他格式条目。

需要监控时先配置 `record-statistics: true`，再直接注入具体实现读取和重置统计：

```java
@Resource
private EhcacheQueryCache ehcacheQueryCache;

EhcacheQueryCache.Stats stats = ehcacheQueryCache.stats();
ehcacheQueryCache.resetStatistics();
```

---

下一步：返回[缓存主题目录](cache/README.md)按需选择相关专题。

---

<a id="ai-cache-redis"></a>
<!-- source: doc/cache/redis.md -->
<a id="ai-cache-redis-redis-与可选本地缓存"></a>
## Redis 与可选本地缓存

[文档首页](README.md) / [缓存](cache/README.md)

Redis 连接、L1 开关、同步与异步写入。

<a id="ai-cache-redis-本页导航"></a>
### 本页导航

- [Redis 共享缓存与可选 Caffeine L1](#ai-cache-redis-redis-共享缓存与可选-caffeine-l1)

<a id="ai-cache-redis-redis-共享缓存与可选-caffeine-l1"></a>
### Redis 共享缓存与可选 Caffeine L1

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>cache-redis</artifactId>
    <version>3.0</version>
</dependency>
```

模块使用 Spring Boot 默认的 Lettuce 和 `RedisConnectionFactory`，不需要业务代码创建 `RedisClient`。默认调用链为 `Caffeine L1 → Redis L2 → 数据源`；关闭 L1 后为 `Redis → 数据源`。

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379

yulinlin:
  cache:
    ttl: 10m
    ttl-in-key: false
    clear-on-write: true
    redis:
      key-prefix: video:prod
      write-mode: async
      batch-size: 128
      serializer: smile
      maximum-entry-size-mb: 16
      record-statistics: false
      caffeine:
        enabled: true
        maximum-size: 10000
```

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `key-prefix` | `dev` | 隔离不同系统、环境和版本的 Redis Key；默认用于本地开发，生产环境建议显式设置为“项目名:环境” |
| `write-mode` | `async` | `async` 先写 L1，再由虚拟线程写 Redis；`sync` 在当前线程完成 Redis 写入 |
| `batch-size` | `128` | 一次 Redis Pipeline 最多提交多少条；收到第一条任务就立即提交当前已有任务，不会等队列凑满 128 条 |
| `serializer` | `smile` | Redis Value 格式：`smile`、`json` 或 `kryo`；JSON 系列使用 Jackson Blackbird |
| `maximum-entry-size-mb` | `16` | 单条序列化结果上限；超出后跳过缓存，避免大列表占满 Redis 和异步队列 |
| `record-statistics` | `false` | 是否记录 L1/Redis 命中、未命中、写入失败和异步丢弃统计；默认关闭热点计数 |
| `caffeine.enabled` | `true` | 是否启用进程内 L1；关闭后每次缓存读取和版本判断都访问 Redis |
| `caffeine.maximum-size` | `10000` | L1 最大条目数，ORM 查询与 `CacheClient` 共用 |

`async`只用于写入普通缓存值。删除、namespace 失效、单条件分区失效和清空操作始终同步写 Redis，避免旧缓存继续可达。后台线程取到第一条任务后立即 `drainTo` 当时已经存在的任务，并使用 Pipeline 批量发送；`batch-size`是单批上限，不是等待阈值。异步队列满时仅跳过该条 Redis L2 写入，查询仍可回源，不做重试。

`caffeine.enabled=true` 适合单实例或允许一个 L1 TTL 内短暂不一致的业务。每个节点的 L1 和本地版本快照互不广播，其他节点更新数据后，本节点可能继续命中旧 L1，直到条目过期。需要多个实例都立即依据 Redis 版本读取时设置为 `false`；本模块不引入 Pub/Sub、分布式锁或强一致协议。

L1 与 Redis 使用同一个解析后的 TTL，但 Redis 命中后重新放入 L1 会从当前时间重新计算该 TTL，因此 L1 最晚可能比 Redis 条目多存活一个 TTL。单机加速场景可以接受；需要严格以 Redis 过期时间为准时关闭 L1。

Redis 中保存的是 Smile/JSON/Kryo 原始字节，不做 Base64。缓存值带有版本、序列化格式和 TTL 信封，因此修改 `serializer` 后仍能读取未过期的旧格式条目。使用 `smile` 或 `json` 时，实体和业务缓存对象必须符合 Jackson 反序列化要求。

---

下一步：返回[缓存主题目录](cache/README.md)按需选择相关专题。

---

<a id="ai-cache-serialization"></a>
<!-- source: doc/cache/serialization.md -->
<a id="ai-cache-serialization-缓存序列化"></a>
## 缓存序列化

[文档首页](README.md) / [缓存](cache/README.md)

格式选择、兼容性与失败处理。

<a id="ai-cache-serialization-本页导航"></a>
### 本页导航

- [序列化格式选择](#ai-cache-serialization-序列化格式选择)

<a id="ai-cache-serialization-序列化格式选择"></a>
### 序列化格式选择

| 格式 | 适合场景 | 特点 |
| --- | --- | --- |
| `json` | 兼容性、可排障性优先 | 默认用于 Ehcache；文本可读，类升级和跨版本排障最直观 |
| `smile` | 性能、体积与兼容性的平衡 | Redis 默认；二进制 JSON，仍复用项目 Jackson 配置 |
| `kryo` | 复杂对象、循环引用、希望使用成熟二进制格式 | Kryo 5 二进制对象图；无需预注册业务类，实例通过线程安全对象池复用，适配虚拟线程 |

Kryo 本身不是线程安全的，框架使用线程安全对象池复用实例，不会在每个虚拟线程中创建一份 `ThreadLocal`。Kryo 会在缓存字节中携带 Java 类型信息，框架无法预先注册全部业务实体，因此只应用于可信缓存存储；不要让用户提交的任意字节直接进入 Redis 或 Ehcache 磁盘目录。读取失败时，框架会删除损坏条目并回源数据源。

`kryo` 更适合可丢弃、短生命周期缓存，不应把缓存字节当作长期持久化协议。实体字段结构或 Kryo 版本发生较大变化时，旧条目可能无法读取，但框架会删除该条目并重新查询数据源。它不经过 Jackson，因此 Jackson 自定义序列化器不会作用于 Kryo 格式。

Kryo 不需要额外 JVM 参数，启用方式如下：

```yaml
yulinlin:
  cache:
    ehcache:
      serializer: kryo
    # 使用 cache-redis 时改为：
    # redis:
    #   serializer: kryo
```

需要观测时开启统计并注入具体实现：

```java
@Resource
private RedisQueryCache redisQueryCache;

RedisQueryCache.Stats stats = redisQueryCache.stats();
long localEntries = redisQueryCache.estimatedLocalSize();
redisQueryCache.resetStatistics();
```

---

下一步：返回[缓存主题目录](cache/README.md)按需选择相关专题。

---

<a id="ai-cache-query"></a>
<!-- source: doc/cache/query.md -->
<a id="ai-cache-query-orm-查询缓存"></a>
## ORM 查询缓存

[文档首页](README.md) / [缓存](cache/README.md)

cache()、CacheMode、默认 TTL 和查询行为。

<a id="ai-cache-query-本页导航"></a>
### 本页导航

- [启用查询缓存](#ai-cache-query-启用查询缓存)
- [CacheMode](#ai-cache-query-cachemode)

<a id="ai-cache-query-启用查询缓存"></a>
### 启用查询缓存

`cache()` 不传参数时使用系统默认值 `yulinlin.cache.ttl`，默认 10 分钟；`cache(Duration)` 只覆盖当前查询：

```java
List<SysUser> users = ModelSelectWrapper
        .newInstance("mysql", SysUser.class)
        .cache() // 使用 yulinlin.cache.ttl
        .where(where -> where.eq("status", 1))
        .selectList();

List<SysUser> shortLived = ModelSelectWrapper
        .newInstance("mysql", SysUser.class)
        .cache(Duration.ofSeconds(30))
        .where(where -> where.eq("status", 1))
        .selectList();
```

自定义 SQL 同样支持缓存：

```java
QueryRequest<SysUser> request = QueryRequest.newInstance(
        "select * from sys_user where status = #{status}",
        Map.of("status", 1),
        SysUser.class
);
request.setSession("mysql");
request.cache(); // 默认 TTL；也可 cache(Duration.ofSeconds(30))
List<SysUser> users = request.selectList();
```

列表、分页内部的 count、group、统计结果和空列表都可以进入缓存。查询异常不会缓存。级联查询、批量预加载和懒加载会继承入口查询的缓存模式与请求级 TTL。

同一条查询使用不同 TTL 时会生成不同物理 Key，互不覆盖。默认 `after-write` 下读取不会续期；Provider 配置为 `after-access` 时，命中会按该条目的 TTL 重新计时。

<a id="ai-cache-query-cachemode"></a>
### CacheMode

`CacheMode`明确控制一次查询是否读缓存、访问数据源和写缓存：

| 模式 | 读缓存 | 未命中访问数据源 | 写缓存 |
| --- | --- | --- | --- |
| `NONE` | 否 | 是 | 否 |
| `CACHE_ONLY` | 是 | 否 | 否 |
| `READ_THROUGH` | 是 | 是 | 是 |
| `REFRESH` | 否 | 是 | 是 |

```java
// cache() 等价于 READ_THROUGH
List<SysUser> cached = ModelSelectWrapper.newInstance("mysql", SysUser.class)
        .cache(CacheMode.READ_THROUGH)
        .selectList();

// 只允许读取已有缓存；未命中抛出 CacheMissException，绝不查询数据库
List<SysUser> offline = ModelSelectWrapper.newInstance("mysql", SysUser.class)
        .cache(CacheMode.CACHE_ONLY)
        .selectList();

// 强制查询数据库并覆盖缓存
List<SysUser> refreshed = ModelSelectWrapper.newInstance("mysql", SysUser.class)
        .cache(CacheMode.REFRESH, Duration.ofMinutes(5))
        .selectList();
```

未调用 `cache(...)` 时为 `NONE`。没有引入缓存 Provider 时，`READ_THROUGH`和`REFRESH`仍能查询数据源，但不会保存结果；`CACHE_ONLY`一定抛出 `CacheMissException`。

---

下一步：返回[缓存主题目录](cache/README.md)按需选择相关专题。

---

<a id="ai-cache-client"></a>
<!-- source: doc/cache/client.md -->
<a id="ai-cache-client-cacheclient-业务缓存"></a>
## CacheClient 业务缓存

[文档首页](README.md) / [缓存](cache/README.md)

选择缓存对象，读写、回源、批量和清理。

<a id="ai-cache-client-本页导航"></a>
### 本页导航

- [业务缓存：CacheClient 管理，Cache 操作](#ai-cache-client-业务缓存cacheclient-管理cache-操作)

<a id="ai-cache-client-业务缓存cacheclient-管理cache-操作"></a>
### 业务缓存：CacheClient 管理，Cache 操作

框架始终暴露一个可注入的 `CacheClient` Bean，仅负责选择、查看缓存。通过它获取 `Cache` 对象后进行业务数据读写。两个接口位于 `com.yulinlin.data.core.cache`，不要与 Spring 的同名 Cache 接口混用。业务缓存与 ORM 使用独立的 Key 和版本域，数据库写操作不会误删验证码、接口结果等业务缓存。

```java
@Resource
private CacheClient cacheClient;

Cache cache = cacheClient.getCache();
cache.set("user", "10001", user); // 使用系统默认 TTL
cache.set("user", "10002", user, Duration.ofMinutes(30));

SysUser cachedUser = cache.get("user", "10001", SysUser.class);
boolean exists = cache.exists("user", "10001");
cache.remove("user", "10001");
cache.clear("user"); // 整个业务命名空间立即失效
cache.clear();       // 只清理当前实现的业务缓存，不清理 ORM 查询缓存
```

选择指定实现，不改变全局默认缓存：

```java
Cache local = cacheClient.getCache("caffeine");
Cache redis = cacheClient.getCache("redis");
local.set("user", "10001", user);
redis.set("user", "10001", user, Duration.ofMinutes(5));

SysUser loaded = redis.getOrLoad(
        "user", "10003", SysUser.class, Duration.ofMinutes(5),
        () -> loadUser("10003")); // loadUser 是业务自行提供的方法

String defaultName = cacheClient.defaultName();
boolean hasRedis = cacheClient.contains("redis");
Set<String> names = cacheClient.names();
Map<String, Cache> caches = cacheClient.caches(); // 只读视图
```

单实现不必配置默认值；多实现必须配置 `yulinlin.cache.default`。每个实现复用一个 `Cache` 对象及其加载锁，默认选择与显式选择同一实现得到同一个对象。名称不存在直接报错；`contains` 对不存在、null 或空白名称返回 false。不提供运行时注册、删除或切换默认值。

`Cache.name()` / `available()` 描述当前缓存；`clear(namespace)` 仅清当前实现的对应业务命名空间，`clear()` 仅清当前实现的全部业务缓存。都不会广播到其他实现，不执行 Redis FLUSHDB，也不清 ORM 缓存。

API 迁移：旧 `cacheClient.get/set/...` 改成 `cacheClient.getCache().get/set/...`；旧 `named(name)` 改成 `getCache(name)`；旧命名空间 `invalidate(namespace)` 改成缓存对象上的 `clear(namespace)`。不再保留管理对象上的数据操作方法，避免职责混淆。

集合等泛型数据使用 Jackson `TypeReference`：

```java
List<SysUser> users = cache.get(
        "user-list", "enabled",
        new TypeReference<List<SysUser>>() {}
);
```

`getOrLoad`支持 Class 和 TypeReference，均有默认 TTL 和指定 TTL 两种重载。单进程同一缓存对象内使用分段锁和二次检查，不保证跨进程只回源一次；加载结果为 null 时不写入缓存，加载异常向调用方传递：

```java
SysUser loadedUser = cache.getOrLoad(
        "user", userId, SysUser.class, Duration.ofMinutes(10),
        () -> userService.findById(userId)
);

List<SysUser> users = cache.getOrLoad(
        "user-list", "enabled", new TypeReference<List<SysUser>>() {},
        () -> loadEnabledUsers());

cache.setAll("user", Map.of("10001", user1, "10002", user2));
cache.setAll("user", Map.of("10003", user3), Duration.ofMinutes(5));
Map<String, SysUser> hits = cache.getAll(
        "user", List.of("10001", "10002"), SysUser.class);
cache.removeAll("user", List.of("10001", "10002"));
```

批量操作当前复用逐条 API，不新增线程或队列，不保证原子性；中途失败可能已有部分生效。`getAll` 只返回命中的条目。Redis 原有异步写入模式继续有效，批量方法返回不等于所有 Redis 写入均已完成。

业务缓存不接受空 namespace、空 key 或 `set(..., null)`。不传 TTL 或传 null 使用配置默认 TTL。不引入任何缓存实现时，ORM 正常启动并直接访问数据库；`getCache()` 返回空缓存，`available()` 为 false，读取未命中，写入/清理无操作，`getOrLoad` 正常执行加载函数。此时 `defaultName()` 返回 null，`names()` / `caches()` 为空；显式 `getCache("redis")` 仍报错。

`CacheClient` 不提供按表、实体或全部 ORM 缓存的手动清理入口，也不解析数据源和实体注解。ORM 写入后的失效由 `clear-on-write` / `clearCache(true)` 控制；需要刷新某条查询时使用 `.cache(CacheMode.REFRESH)`。绕过框架直接修改数据库不会自动触发 ORM 缓存失效，需要等待 TTL 或针对受影响查询刷新。业务缓存的 `Cache.clear(...)` 不能用于清理 ORM 查询缓存。

---

下一步：返回[缓存主题目录](cache/README.md)按需选择相关专题。

---

<a id="ai-cache-invalidation"></a>
<!-- source: doc/cache/invalidation.md -->
<a id="ai-cache-invalidation-缓存-key失效与一致性"></a>
## 缓存 Key、失效与一致性

[文档首页](README.md) / [缓存](cache/README.md)

条件分区失效、表命名空间、多数据源隔离及并发边界。

<a id="ai-cache-invalidation-本页导航"></a>
### 本页导航

- [Key 规则](#ai-cache-invalidation-key-规则)
- [失效和一致性](#ai-cache-invalidation-失效和一致性)
- [单条件自动分区](#ai-cache-invalidation-单条件自动分区)
- [并发与对象边界](#ai-cache-invalidation-并发与对象边界)

<a id="ai-cache-invalidation-key-规则"></a>
### Key 规则

Key 在 `core` 中统一生成，不依赖某一种数据库最终 SQL：

```text
MurmurHash3-x64-128(
    版本
  + 路由后的 group/cluster
  + Session 类型
  + entityClass/fromClass
  + 查询类型
  + 完整 INode 元数据
  + 实际 TTL
  + 全局版本与依赖表版本
)
```

元数据直接流入128位哈希器，不构建完整中间字符串。`CacheKey` 在内存中只保存两个 `long`；Caffeine 直接使用该对象作为 Key，Ehcache/Redis 持久化或日志输出时才按需生成 `v2:` 开头的32位十六进制文本。实体反射字段通过 `ClassValue` 缓存，不会在每次查询时重新扫描、排序。

`CommandNode` 的 SQL、参数名、参数类型和值都属于 INode 元数据。因此相同 SQL 的不同参数不会共用结果；MySQL、PostgreSQL、SQLite、H2、MongoDB、Elasticsearch 以及第三方 `AbstractSession` 实现都使用同一规则。Map 参数按键排序，参数插入顺序不会造成无意义的 Key 差异。

缓存命中时不会访问数据源，因此数据库模块的 `log=true` 不会打印 SQL。排障时可将 `com.yulinlin.data.cache.QueryCache` 调整为 DEBUG，查看 `HIT`、`MISS`、`REFRESH`、等待后命中以及事务/行锁绕过等事件；日志只记录哈希 Key，不记录缓存值。需要确认物理 SQL 时使用 `CacheMode.REFRESH` 或暂时关闭该次查询缓存。

分页内部的 `count` 使用独立的逻辑 Key：普通查询只保留 `from/join + where`，因此不同页码、页大小、排序、返回字段、高亮和行锁会复用同一个总数；聚合查询还会保留 `aggregations + metrics + having`，避免不同分组统计误命中。普通列表 Key 仍然包含分页和排序。未知的第三方 Wrapper 不做字段裁剪，继续使用完整节点生成 Key，以正确性优先。

数据源 group 必须进入 Key。同一个实体和条件在 `mysql`、`pgsql` 或其他 group 中不会串缓存；同一个逻辑 group 下的负载均衡节点可以共享逻辑 Key。

<a id="ai-cache-invalidation-失效和一致性"></a>
### 失效和一致性

缓存始终受 TTL 约束。引入缓存 Provider 后，标准 `insert/update/delete` 未指定策略时继承 `yulinlin.cache.clear-on-write`，默认在提交成功后清理相关查询缓存：

```java
int affected = ModelUpdateWrapper
        .newInstance("mysql", patch)
        .execute();

int deleted = ModelDeleteWrapper
        .newInstance("mysql", SysUser.class)
        .eq("status", 0)
        .clearCache(false) // 明确接受 TTL 窗口内的旧结果时，关闭本次清理
        .execute();
```

写操作只提供一个请求级 API：

| API | 行为 |
| --- | --- |
| 不调用 `clearCache(...)` | 继承 Provider 配置 `yulinlin.cache.clear-on-write` |
| `clearCache(true)` | 本次写入提交成功后清理相关查询缓存，覆盖全局 `false` |
| `clearCache(false)` | 本次写入不清理查询缓存，覆盖全局 `true` |

<a id="ai-cache-invalidation-单条件自动分区"></a>
#### 单条件自动分区

分区失效是缓存 Provider 的内置能力，始终自动开启，不需要配置，也没有额外 API。只有已经调用 `cache(...)` 或使用 `@JoinCache` 的查询才会进入缓存；它不会让普通查询自动启用缓存。

当同一数据源、同一物理表的缓存查询始终使用同一个单字段等值条件时，框架会按条件值划分缓存：

```java
List<SysUser> alice = ModelSelectWrapper.newInstance("mysql", SysUser.class)
        .eq("username", "Alice")
        .cache()
        .selectList();
```

例如当前结构是 `username = ?`，更新或删除条件同样为 `username = 'Alice'` 时，提交后只让 `Alice` 分区失效，`Bob` 分区仍可命中。普通列表、分页、Count、聚合等缓存仍按表失效，不会因为分区优化而保留旧结果。

最小识别规则如下：

- 查询必须是单表、非行锁，并且有效条件只有一个 `Eq`；框架工厂产生的空 `And/Or` 包装会被忽略。
- 条件字段只识别普通列名或当前表已验证的别名前缀；JSON 路径、函数、引号字段和未知前缀直接按整表处理。
- 字段名先走实体的 `JoinField`/驼峰映射，条件值先走当前 Session 的 Coder；映射或编码结果无法安全识别时同样按整表处理。
- 更新、删除使用相同字段的单个 `Eq` 时精准失效对应值。
- 更新同时修改该条件字段、插入、自定义 SQL、多条件或无法解析的写入，会让该表当前全部分区失效。
- 如果缓存查询又使用第二个等值字段，例如先有 `username = ?`、后来又有 `id = ?`，该物理表自动升级为普通整表失效模式。
- 状态按 `Session 类型 + group + 物理表` 隔离；同表对应多个实体类时共享同一判断。

分区 Key 仍保留完整查询信息，因此不同分页、排序、投影和 TTL 不会错误命中；分区版本只负责让同一个条件值下的这些条目一起失效。没有引入缓存模块，或第三方 Provider 未实现分区能力时，会安全回退为现有整表失效。

实体查询和写入优先从过滤器增强后的 `INode`、From/Join/Store 提取实际表名，节点无法识别表时才回退到 `@JoinTable`、`@JoinTableList`。动态分表不会同时混入注解中的原始逻辑表，Key、表级 TTL 和失效范围使用最终物理表；显式 `cacheNamespaces(...)` 始终追加。原始 SQL 不做表名猜测，有动态表或隐藏依赖时须显式声明实际命名空间。自定义 SQL 没有实体或节点表信息时，启用清理会退化为清理全部 ORM 查询缓存并输出警告：

```java
ExecuteRequest<Object> write = ExecuteRequest.newInstance(
        "update sys_user set status=#{status} where id=#{id}",
        Map.of("status", 1, "id", 7)
);
write.setSession("mysql");
int affected = write.clearCache(true).execute();
```

自定义查询若没有带 `@JoinTable` 的 `fromClass`，可用 `cacheNamespaces("sys_user", "sys_role")` 声明依赖表。这样任一表失效时，该复杂 SQL、JOIN 或聚合结果都会同时失效。

延迟写队列在真正写入成功后才清理，不在入队时清理。相同 `Session + 操作类型 + fromClass` 的请求合并时使用三态规则：任一请求显式 `true`，合并组就是 `true`；全部显式 `false`，合并组才是 `false`；包含未指定且没有 `true` 时保持未指定，执行时继承全局配置。

失效采用“命名空间版本号”，不会遍历百万级 Key：普通查询 Key 携带 `Session 类型 + group + 表名` 的版本快照；单条件分区查询携带表策略版本和对应条件值版本。写入成功后只递增相关版本，旧 Key 立即不可达，随后按自身 TTL 自然回收。Ehcache 的普通 namespace 版本会持久化；分区条目在进程重启后使用新的运行期版本，不会重新命中重启前的旧分区结果。Redis 会把普通版本和分区版本都保存在共享 Redis 元数据中；关闭 L1 时，各节点每次都读取共享版本。

事务规则：

- 普通单次写入在底层 Session 提交成功后按 `clearCache` 策略清理。
- `RouteSession` 多数据源事务会合并重复表名，只在最外层提交成功后统一清理。
- 多数据源提交中途失败、结果无法确定时会保守清理待处理缓存；最多多一次回源，不保留可能已提交数据的旧缓存。
- Spring 事务在 `afterCommit` 阶段清理；回滚或提交失败不清理。
- 活动事务内的查询直接访问数据源，不读取也不写入全局查询缓存，防止未提交结果在回滚后残留；事务内使用 `CACHE_ONLY` 会直接报告缓存不可用。
- `lock()` 行锁查询始终访问数据库，不会因为 `cache()` 命中而跳过加锁。
- 查询回源期间若并发发生更新，旧查询只能写入旧版本 Key，不能重新污染新版本缓存。

即使有主动失效，也应把 TTL 设置为业务可接受的最大兜底陈旧时间。事务外要求绝对强一致的查询使用 `CacheMode.NONE`。

<a id="ai-cache-invalidation-并发与对象边界"></a>
### 并发与对象边界

同一个 Key 回源时使用分段锁和二次检查，降低缓存到期瞬间的重复查询。缓存保存查询增强前的数据；返回给业务前会复制模型并创建当前调用需要的统一实体代理，旧事务代理不会被保存复用。

Ehcache 默认使用 JSON 字节保存结果，Redis 默认使用 Smile 字节保存结果；选择 JSON/Smile 时实体应符合 Jackson 的反序列化要求。连接、流、Statement、ResultSet 等资源对象不能作为缓存查询结果。

---

下一步：返回[缓存主题目录](cache/README.md)按需选择相关专题。

---

<a id="ai-security-modules"></a>
<!-- source: doc/security/modules.md -->
<a id="ai-security-modules-安全模块选型"></a>
## 安全模块选型

[文档首页](README.md) / [接口安全](security/README.md)

按业务需要选择模块，登录和鉴权统一使用框架公开 API。

<a id="ai-security-modules-本页导航"></a>
### 本页导航

- [引入模块](#ai-security-modules-引入模块)
- [JWT 与 Sa-Token 如何选择](#ai-security-modules-jwt-与-sa-token-如何选择)

<a id="ai-security-modules-引入模块"></a>
### 引入模块

一个项目只选一种登录实现，不混用 `security-jwt`、`security-sa-token`、`security-spring`。这些模块已经包含公共 `security` 的能力。

| 业务需要 | 引入的制品 | 接入要求 |
| --- | --- | --- |
| 仅加密、限流或权限注解 | `security` | 不要求实现登录；未登录用户不能访问受保护方法 |
| 无状态 Token 登录 | `security-jwt` | 配置签名密钥，提供 UserStore 和所需 LoginAuthenticator |
| 有状态 Token 登录 | `security-sa-token` | 提供 UserStore 和所需 LoginAuthenticator |
| 复用已有 Spring Security 登录 | `security-spring` | 项目已有可用认证配置；统一登录需完成项目认证适配 |
| 完全自定义登录实现 | `security` | 提供 ISecurityManager，按需提供 LoginInterceptor |

仅使用公共能力时：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>security</artifactId>
    <version>3.0</version>
</dependency>
```

需要登录时，将 artifactId 换成对应模块即可。公共安全能力不要求引入 ORM 或数据库驱动。

公开使用规则：

- 登录统一调用 `SecurityUtil.login(LoginRequest)`，也可注入 `ISecurityManager`。
- 密码、密钥、自定义认证分别提供 `LoginAuthenticator<T>` Bean；`UserStore` 按用户 ID 加载用户。
- 可信业务按 ID 登录使用 `UserIdLogin`，不能直接接受客户端任意用户 ID。
- 自定义 `LoginInterceptor` Bean 会自动替换默认拦截器，不需要额外启用开关或重复注册。
- 没有自定义 CryptoCodec 且不配置设备密钥时，不处理接口加解密；登录、权限和限流仍可单独使用。自定义实现不要求 AES 密钥，详见[加密扩展](#ai-security-crypto-codec)。
- 存在 Codec 时无注解接口也参与全局加解密；ApiCrypto(false) 仅使该接口兼容请求、返回明文。要完全跳过，配置 exclude-paths，见[规则表](#ai-security-crypto-接口注解)。

<a id="ai-security-modules-jwt-与-sa-token-如何选择"></a>
### JWT 与 Sa-Token 如何选择

| 能力 | security-jwt | security-sa-token |
| --- | --- | --- |
| 登录状态 | 无状态，不保存登录记录 | 有状态，保存登录记录 |
| 登录调用 | `login(PasswordLogin / ApiKeyLogin / UserIdLogin / 自定义参数)` | 相同 |
| 登录结果 | Token、有效期、用户信息、authType | 相同；不设置固定到期时间时 expiresAt 可为空 |
| 注销 | 清理当前身份，客户端删除 Token；旧 Token 本身不会因此失效 | 注销当前 Token，后续不能继续使用该 Token |
| 应用重启 | 密钥和校验配置不变、用户有效且 Token 未过期时仍可用 | 默认内存登录记录不跨重启保留；重启恢复需要持久化方案 |
| 停用或删除用户 | UserStore 拒绝加载后阻止后续访问 | 相同 |
| 修改密码或业务密钥 | 不自动撤销已签发 Token | 不自动注销既有登录 |
| 刷新资料 | `refresh()`，不重新签发 Token | `refresh()`，不等于重新登录 |
| 多实例部署 | 各实例使用一致的签名配置和用户数据 | 各实例需要共享有效的登录状态 |

只需要简单 Token 登录可选 JWT；需要注销后使当前 Token 失效可选有状态登录。统一 API 不承诺批量踢人、查询在线用户或自动续期。

JWT 用户重新启用后，其尚未过期的旧 Token 可能继续使用。删除用户后不要复用旧 userId。需要更严格的凭证撤销规则，使用有状态方案或[自定义管理器](#ai-security-extension-自定义有状态登录)。

---

下一步：[用户模型与登录接口](#ai-security-user)。

---

<a id="ai-security-crypto"></a>
<!-- source: doc/security/crypto.md -->
<a id="ai-security-crypto-接口加密与前端对接"></a>
## 接口加密与前端对接

[文档首页](README.md) / [接口安全](security/README.md)

完整配置、多设备、CBC/GCM、明密文兼容和安全边界。

<a id="ai-security-crypto-本页导航"></a>
### 本页导航

- [是否接管接口加解密](#ai-security-crypto-是否接管接口加解密)
- [完整主配置](#ai-security-crypto-完整主配置)
- [配置项与默认值](#ai-security-crypto-配置项与默认值)
- [接口注解](#ai-security-crypto-接口注解)
- [响应加密规则](#ai-security-crypto-响应加密规则)
- [默认 CBC 前端对接](#ai-security-crypto-默认-cbc-前端对接)
- [多设备选择](#ai-security-crypto-多设备选择)
- [明文排除路径](#ai-security-crypto-明文排除路径)
- [可选 AES-GCM](#ai-security-crypto-可选-aes-gcm)
- [手动使用](#ai-security-crypto-手动使用)
- [安全边界与限制](#ai-security-crypto-安全边界与限制)

<a id="ai-security-crypto-是否接管接口加解密"></a>
### 是否接管接口加解密

**没有自定义 CryptoCodec，也没有配置设备密钥时，框架不做接口加密解密。** JWT 签名密钥不属于设备密钥。提供自定义 CryptoCodec Bean 后，不要求配置 AES 密钥，完整示例见[自定义接口加解密](#ai-security-crypto-codec)。

- 自定义 CryptoCodec 优先，默认 AES 不与其叠加。下列设备密钥规则适用于没有自定义 Codec 的默认接入。
- `devices` 未配置、为空，或所有设备的 `key` 都为 null / 空白：不启用默认 AES。
- 没有 Codec 时，即使标注 `@ApiCrypto`，框架也不会读取、解密或改写正文，不附加加密响应头；请求和响应按正常接口协议处理，业务可自行实现加解密。
- 不接管不等于“任意密文都能直接进入 Controller”。业务未提供解密逻辑时，JSON 转换器仍可能拒绝密文。正常 HTTP 大小限制仍由 Web 容器和业务配置负责。
- 配置了任意一个非空设备密钥，就注册默认 AES Codec；此时 `devices.unknown`、所有已声明设备的 key、CBC IV 和算法配置必须合法，错误配置启动时报错。
- `advice-enabled` 默认 `true`。设置为 `false` 时，只停用 `CryptoRequestBodyAdvice` 和 `CryptoResponseBodyAdvice`，请求和响应不再由这两个 Advice 处理；默认或自定义 CryptoCodec 仍保留，可注入并手动调用，设备配置校验也不会跳过。
- Advice 启用且存在默认或自定义 Codec 时，无注解的业务接口也参与：全局 `enabled=true` 要求密文请求、加密响应；`@ApiCrypto(false)` 是允许明文请求、返回明文的例外。
- Advice 启用且存在默认或自定义 Codec 时，`enabled=false` 仍为兼容模式：请求可明文或密文，响应明文；它不是完全关闭。
- 登录、权限、限流不受设备密钥有无影响。业务手工创建的加密组件由业务自己管理；这里控制的是框架自动配置。配置在启动时判断，修改后需重启。

没有自定义 Codec 且密钥全部缺失时按“不接管”处理，部署时应核对环境变量和配置来源；需要强制加密的项目应确保 Codec 已接入，不能只依赖 `@ApiCrypto` 注解。

<a id="ai-security-crypto-完整主配置"></a>
### 完整主配置

下面为默认 AES 的完整多设备配置，展示设备请求头、未知设备兜底、明文路径、请求大小以及 Web、Android、iOS 独立密钥。自定义 CryptoCodec 无需填写 devices，其他公共配置仍生效：

```yaml
yulinlin:
  security:
    crypto:
      # MVC 自动加解密总开关；false 只停用两个 Advice，保留 CryptoCodec
      advice-enabled: true

      # 无注解接口也参与；true：全局严格加密；false：兼容解密、响应明文
      enabled: true

      # 客户端通过该请求头选择设备密钥
      device-header: Device-Type

      # 请求和响应都跳过加解密
      # 自定义该列表会替换默认值，因此需要保留 /monitor/**
      exclude-paths:
        - /monitor/**
        - /actuator/health
        - /public/**
        - /upload/**
        - /download/**

      # 同时限制收到的密文和解密后的 JSON
      max-request-size: 1MB

      devices:
        web:
          algorithm: AES/CBC/PKCS5Padding
          key: ${YULINLIN_SECURITY_WEB_KEY}
          iv: ${YULINLIN_SECURITY_WEB_IV}

        android:
          algorithm: AES/CBC/PKCS5Padding
          key: ${YULINLIN_SECURITY_ANDROID_KEY}
          iv: ${YULINLIN_SECURITY_ANDROID_IV}

        ios:
          algorithm: AES/CBC/PKCS5Padding
          key: ${YULINLIN_SECURITY_IOS_KEY}
          iv: ${YULINLIN_SECURITY_IOS_IV}

        # 固定保留名：请求头缺失、为空或未匹配任何设备时统一使用
        unknown:
          algorithm: AES/CBC/PKCS5Padding
          key: ${YULINLIN_SECURITY_UNKNOWN_KEY}
          iv: ${YULINLIN_SECURITY_UNKNOWN_IV}

    limit:
      enabled: true
      # 本机最多保留多少个“接口 + IP/设备 ID”计数器
      maximum-size: 50000
      ip-header: X-Real-IP
      device-id-header: Device-Id
      # 只有 TCP 连接来源属于这里时，框架才信任 ip-header
      trusted-proxies:
        - 127.0.0.1
        - "::1"
```

环境变量示例：

```properties
YULINLIN_SECURITY_WEB_KEY=MIGfMA0GCSqGSIb3
YULINLIN_SECURITY_WEB_IV=5efd3f6060e20765

YULINLIN_SECURITY_ANDROID_KEY=1234567890abcdef
YULINLIN_SECURITY_ANDROID_IV=abcdef1234567890

YULINLIN_SECURITY_IOS_KEY=fedcba0987654321
YULINLIN_SECURITY_IOS_IV=0123456789abcdef

YULINLIN_SECURITY_UNKNOWN_KEY=abcdef1234567890
YULINLIN_SECURITY_UNKNOWN_IV=1234567890abcdef
```

示例值仅用于说明长度。生产密钥必须由环境变量或密钥管理系统提供，不能提交到仓库。

<a id="ai-security-crypto-配置项与默认值"></a>
### 配置项与默认值

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `advice-enabled` | `true` | 是否注册请求解密和响应加密 Advice；false 不影响 Codec、鉴权或限流，修改后需重启 |
| `enabled` | `true` | Advice 启用且存在 Codec 时全局参与，包括无注解接口；true 严格加密，false 兼容解密、响应明文；无 Codec 不接管 |
| `device-header` | `Device-Type` | 客户端设备类型请求头；同时回写到加密响应 |
| `exclude-paths` | `/monitor/**` | 同时跳过请求解密和响应加密的路径模式，如 `/public/**` |
| `max-request-size` | `1MB` | 密文与解密后正文的大小上限 |
| `devices.<name>.algorithm` | `AES/CBC/PKCS5Padding` | 当前设备的 AES 算法 |
| `devices.<name>.key` | 无 | 必填；CBC 接受 16、24 或 32 字节 |
| `devices.<name>.iv` | 无 | CBC 必填且必须是 16 字节 |
| `devices.<name>.key-encoding` | `UTF8` | 默认字符密钥不需要填写；GCM 示例使用 `BASE64` |
| `devices.<name>.iv-encoding` | `UTF8` | 默认字符 IV 不需要填写 |

默认 AES 配置中的设备名会去除首尾空白并转换为小写，只允许小写字母、数字、下划线和短横线，最大长度为 32。HTTP 请求未携带设备头、设备头为空、格式异常或设备名未配置时，统一使用保留设备 `devices.unknown`。默认 AES 配置任意设备密钥后，`devices.unknown` 也是必填项。algorithm、key、iv 只在各设备下配置，不设置外层默认设备或密钥。自定义 Codec 自行选择设备密钥，详见[扩展协议](#ai-security-crypto-codec)。

CBC 参数必须满足：

```text
algorithm = AES/CBC/PKCS5Padding
key       = 16、24 或 32 字节
iv        = 16 字节
encoding  = UTF-8
cipher    = 标准 Base64
padding   = PKCS5/PKCS7
```

建议 key 和 IV 使用 ASCII 字符，避免中文 UTF-8 字符占多个字节。Java 的 `PKCS5Padding` 与常见 JavaScript AES 实现的 `Pkcs7` 在 AES 分组下兼容。

<a id="ai-security-crypto-接口注解"></a>
### 接口注解

以下加解密规则以 `advice-enabled=true` 为前提。配置设备密钥或提供自定义 CryptoCodec 后，全局 `enabled` 决定默认行为，**无注解接口也参与加解密**。不需要每个 Controller 都标注 `@ApiCrypto`；注解用于声明例外，可放在类或具体方法上，方法配置优先于类配置：

| 全局配置 | 接口注解 | 请求 | 响应 |
| --- | --- | --- | --- |
| `advice-enabled=false` | 任意注解或无注解 | 框架 Advice 不处理 | 框架 Advice 不处理 |
| 无自定义 Codec，也无设备密钥 | 任意注解或无注解 | 框架不处理 | 框架不处理 |
| 有 Codec，`enabled=true` | 无注解 / `@ApiCrypto` / `@ApiCrypto(true)` | 要求整个 JSON 加密后的文本密文，默认 AES 为 Base64 | 加密 |
| 有 Codec，`enabled=true` | `@ApiCrypto(false)` | 普通 JSON 原样读取；非 JSON body 仍尝试解密 | 明文 |
| 有 Codec，`enabled=false` | 任意注解或无注解 | 普通 JSON 或整个 body 密文都支持 | 明文 |
| 任意值 | 命中 `exclude-paths` | security 完全不处理 | 明文 |

Advice 启用且存在 Codec 时，`enabled=false` 是兼容模式，仍能解密尚未升级的旧客户端请求；此时 `@ApiCrypto(true)` 不再拒绝明文请求，响应也不会加密。要保留 Codec 但完全停用框架的 MVC 自动加解密，设置 `advice-enabled=false`；只排除部分接口时使用 `exclude-paths`。`@ApiCrypto(true)` 不能重新开启已停用的 Advice，业务自行注册的处理逻辑仍由业务管理。

```yaml
yulinlin:
  security:
    crypto:
      advice-enabled: false
```

关闭后请同步调整客户端或由业务接管报文处理；框架不会再自动解开旧客户端发送的密文。该开关不改变 HTTPS、鉴权和限流配置。

类标注 `@ApiCrypto(false)` 时，该类未单独标注的方法也采用兼容请求、明文响应；方法可用 `@ApiCrypto(true)` 恢复全局严格行为，但不能覆盖全局 `enabled=false`。排除路径优先于所有注解。

**升级注意：去掉注解不再表示跳过处理。** 已配置密钥或自定义 Codec 的项目，开启全局加密后，原本无注解的接口也会要求密文并加密返回。迁移阶段可先设置 `enabled=false`，或对明确需要明文返回的接口添加 `@ApiCrypto(false)`；文件、监控及第三方回调等不走该协议的路径要加入排除列表。

业务 JSON 接口推荐统一使用 `@PostMapping + @RequestBody`。接口加密只处理 body，不加密 query 参数和 path 变量。

```java
package demo.api;

import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.starter.domain.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    // 无注解：跟随全局配置；enabled=true 时请求和返回都加密。
    @PostMapping("/api/user/detail")
    public R<UserVO> detail(@RequestBody UserDetailPo request) {
        return R.ok(userService.detail(request.getId()));
    }

    @ApiCrypto(false)
    @PostMapping("/api/user/public-detail")
    public R<UserVO> publicDetail(@RequestBody UserDetailPo request) {
        return R.ok(userService.detail(request.getId()));
    }
}
```

`@ApiCrypto(false)` 是兼容模式，不是完全关闭请求处理。body 使用 UTF-8：忽略首尾空白后，以 `{` 或 `[` 开头的内容按明文 JSON 处理；其他内容交给 Codec 解密，默认 AES 接收 Base64。因此兼容明文请求应使用 JSON 对象或数组。解密失败返回 400，不会再次降级为明文。

<a id="ai-security-crypto-响应加密规则"></a>
#### 响应加密规则

- 普通 Java 对象：整个对象序列化后加密，响应 JSON 是密文字符串，默认 AES 使用 Base64。
- `R<E>`：只加密非 null 的 `data`，保留 `code`、`msg`、`ok` 和 `timestamp`，并设置 `crypt=true`。
- 返回值或 `R.data` 是 `String` 时，直接加密该字符串的 UTF-8 字节，不先增加 JSON 双引号；其他类型仍先序列化为 JSON。
- `R.data == null`：不生成无意义密文，`crypt` 保持 `false`。
- `ResponseVo<E>`：仅保留废弃兼容，行为与 `R<E>` 相同。

因此客户端必须按接口约定选择解密方法：字符串使用 `decryptText()`，对象、集合、数字和布尔值使用 `decryptJson()`。密文自身不能可靠判断原始 Java 类型。

`R<E>` 加密响应示例：

```json
{
  "code": 200,
  "msg": "操作成功",
  "ok": true,
  "crypt": true,
  "data": "mFbzvANmZJZQj/3nB4zKpA==",
  "timestamp": 1792070400000
}
```

<a id="ai-security-crypto-默认-cbc-前端对接"></a>
### 默认 CBC 前端对接

请求仍使用 `Content-Type: application/json`。推荐 body 直接发送未经 JSON 双引号包裹的标准 Base64 密文；为兼容浏览器请求链路，服务端也接受 Axios 添加一层 JSON 双引号、`+` 被表单处理转换为空格，以及密文中混入回车、换行或制表符的情况：

```http
POST /api/user/detail HTTP/1.1
Content-Type: application/json
Device-Type: web

N4H0Qf4H0m4YqQPG4Q3XWg==
```

以下 Axios 实际报文也能兼容，服务端会先移除最外层双引号再进行 Base64 解码：

```text
"N4H0Qf4H0m4YqQPG4Q3XWg=="
```

默认 AES 的加密响应会返回实际设备和算法；自定义 Codec 不自动发送算法头：

```http
Device-Type: web
X-Yulinlin-Crypto: AES/CBC/PKCS5Padding
```

下面使用浏览器原生 Web Crypto API，与默认 CBC、UTF-8 key/IV 和标准 Base64 对齐。Web Crypto 的 AES-CBC 使用 PKCS7 填充，与服务端 `PKCS5Padding` 兼容：

```javascript
const encoder = new TextEncoder();
const decoder = new TextDecoder();

function bytesToBase64(bytes) {
  let binary = "";
  for (const value of new Uint8Array(bytes)) binary += String.fromCharCode(value);
  return btoa(binary);
}

function base64ToBytes(value) {
  return Uint8Array.from(atob(value), char => char.charCodeAt(0));
}

async function importCbcKey(keyText) {
  const bytes = encoder.encode(keyText);
  if (![16, 24, 32].includes(bytes.length)) throw new Error("AES key length is invalid");
  return crypto.subtle.importKey("raw", bytes, {name: "AES-CBC"}, false,
      ["encrypt", "decrypt"]);
}

function cbcIv(ivText) {
  const bytes = encoder.encode(ivText);
  if (bytes.length !== 16) throw new Error("AES-CBC IV must contain 16 bytes");
  return bytes;
}

async function encryptJson(value, keyText, ivText) {
  const key = await importCbcKey(keyText);
  const encrypted = await crypto.subtle.encrypt(
      {name: "AES-CBC", iv: cbcIv(ivText)},
      key,
      encoder.encode(JSON.stringify(value)));
  return bytesToBase64(encrypted);
}

async function decryptText(ciphertext, keyText, ivText) {
  const key = await importCbcKey(keyText);
  const plaintext = await crypto.subtle.decrypt(
      {name: "AES-CBC", iv: cbcIv(ivText)},
      key,
      base64ToBytes(ciphertext.trim()));
  return decoder.decode(plaintext);
}

async function decryptJson(ciphertext, keyText, ivText) {
  return JSON.parse(await decryptText(ciphertext, keyText, ivText));
}

const deviceType = "web";
const key = "MIGfMA0GCSqGSIb3";
const iv = "5efd3f6060e20765";
const body = await encryptJson({id: "U1001"}, key, iv);

const response = await fetch("/api/user/detail", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "Device-Type": deviceType
  },
  body
});

const responseBody = await response.json();
const result = responseBody?.crypt === true
    ? {...responseBody, data: await decryptJson(responseBody.data, key, iv)}
    : typeof responseBody === "string"
        ? await decryptText(responseBody, key, iv)
        : responseBody;
```

上例接口返回的是 `R<UserVO>`，所以 `data` 使用 `decryptJson()`；如果接口返回 `R<String>`，对应代码应改为：

```javascript
const result = responseBody?.crypt === true
    ? {...responseBody, data: await decryptText(responseBody.data, key, iv)}
    : responseBody;
```

跨域调用时，业务 CORS 配置必须允许请求头 `Device-Type`，并暴露响应头 `Device-Type` 和 `X-Yulinlin-Crypto`。密钥不得直接硬编码在公开源码中；上述浏览器代码只用于说明协议对接。

<a id="ai-security-crypto-多设备选择"></a>
### 多设备选择

默认 AES 由请求头决定当前使用哪组设备密钥；自定义实现按[公开协议](#ai-security-crypto-codec-保留的配置与行为)接收设备名：

```http
Device-Type: web
Device-Type: android
Device-Type: ios
```

| 请求头 | 最终设备 |
| --- | --- |
| 缺失或空值 | `unknown` |
| `web`、`android` 等已配置名称 | 对应设备 |
| 未配置的名称 | `unknown` |
| 不符合设备名格式的值 | `unknown` |

设备名匹配不区分大小写并忽略首尾空白。响应回写实际解析后的设备类型，客户端应优先读取响应 `Device-Type`，再选择对应密钥解密。设备请求头只负责选密钥，不是身份认证信息，不能仅凭该值判断调用方可信。

<a id="ai-security-crypto-明文排除路径"></a>
### 明文排除路径

`exclude-paths` 是服务端控制的完整排除列表，支持如下路径模式。命中后，即使接口存在 `@ApiCrypto(true)`，请求体也不解密，响应体也不加密：

```yaml
yulinlin:
  security:
    crypto:
      exclude-paths:
        - /monitor/**
        - /actuator/health
        - /public/**
```

默认列表只有 `/monitor/**`。配置新列表会替换默认列表，因此使用监控模块时必须继续保留它。不要提供由客户端控制的“跳过加密”请求头，否则业务接口可能被绕过。

文件上传、文件下载、视频流、SSE、WebSocket、静态资源、健康检查及不使用此加密协议的第三方回调，应将实际路径加入排除列表。无注解不再跳过处理；`@ApiCrypto(false)` 也不是文件接口的完整排除方式，因为它仍会尝试解密非 JSON 正文。

<a id="ai-security-crypto-可选-aes-gcm"></a>
### 可选 AES-GCM

支付、订单等需要密文完整性校验的设备可以显式切换到 GCM：

```yaml
yulinlin:
  security:
    crypto:
      devices:
        web:
          key: ${YULINLIN_SECURITY_WEB_KEY}
          iv: ${YULINLIN_SECURITY_WEB_IV}
        ios:
          algorithm: AES/GCM/NoPadding
          key-encoding: BASE64
          key: ${YULINLIN_SECURITY_IOS_KEY}
        unknown:
          key: ${YULINLIN_SECURITY_UNKNOWN_KEY}
          iv: ${YULINLIN_SECURITY_UNKNOWN_IV}
```

GCM 要求 Base64 解码后恰好为 32 字节的密钥，不允许配置固定 `iv`。框架每次生成 12 字节随机 IV，Base64 解码后的报文结构为：

```text
12字节随机IV + 密文 + 16字节认证标签
```

GCM 使用以下 UTF-8 内容作为 AAD，客户端必须完全一致：

```text
1\n<规范化设备类型>\nA256GCM
```

例如 web 设备的 AAD 是 `1\nweb\nA256GCM`。密文、设备名、IV、认证标签或密钥不匹配都会导致解密失败；这是 GCM 相比默认固定 IV CBC 的主要安全优势。

<a id="ai-security-crypto-手动使用"></a>
### 手动使用

只需要手动调用时可设置 `advice-enabled=false`；该开关不会移除或替换已注册的 Codec。

存在默认或自定义加密实现时，统一注入公开的 `CryptoCodec`，与接口加解密使用同一个 Bean：

```java
import com.yulinlin.security.crypto.CryptoCodec;
import com.yulinlin.security.crypto.CryptoContext;

public class SecureValueService {
    private final CryptoCodec cryptoCodec;

    public SecureValueService(CryptoCodec cryptoCodec) {
        this.cryptoCodec = cryptoCodec;
    }

    public String encrypt(String deviceType, String value) {
        return cryptoCodec.encrypt(CryptoContext.of(deviceType), value);
    }

    public String decrypt(String deviceType, String ciphertext) {
        return cryptoCodec.decrypt(CryptoContext.of(deviceType), ciphertext);
    }
}
```

手动调用时传入规范设备名，并自行控制大小、处理异常。`CryptoContext.of` 使用空请求头；需要协议头时可显式构造 CryptoContext。没有自定义 Codec 且未配置设备密钥时不能直接注入 CryptoCodec。自定义协议、Bean 示例和响应约定见[自定义接口加解密](#ai-security-crypto-codec)。

<a id="ai-security-crypto-安全边界与限制"></a>
### 安全边界与限制

- 当前只支持 Spring MVC，不支持 WebFlux。
- 生产环境仍必须使用 HTTPS；接口加密不能替代 TLS。
- CBC 使用固定 IV，不能可靠发现密文篡改；高敏感业务选择 GCM。
- 浏览器必须持有共享 AES 密钥，因此网页脚本、浏览器扩展和自动化浏览器最终都能读取它。该能力只能提高普通采集脚本的成本，不能单独承担防爬虫职责。
- AES 不是密码哈希，用户密码必须使用专用密码哈希算法保存。
- 接口加密只适合 JSON 请求和 JSON/字符串响应，不适合 multipart、文件流、视频流、SSE 或 WebSocket。
- 日志不得记录密钥、IV、解密后的敏感正文或完整密文。
- 当前没有实现时间戳、nonce 防重放和密钥轮换，业务不能假设已经具备这些能力。
- 当前限流状态只存于单个 Java 进程；多实例部署时每个实例独立计数，不提供分布式强一致限流。
- 设备 ID 可以伪造，IP 也可能由 NAT 共享；限流用于控制接口频率，不能单独判断用户一定是爬虫。

接入时应使用真实客户端核对设备密钥、请求格式、响应类型及明密文兼容约定。

---

下一步：返回[接口安全主题目录](security/README.md)按需选择相关专题。

---

<a id="ai-security-crypto-codec"></a>
<!-- source: doc/security/crypto-codec.md -->
<a id="ai-security-crypto-codec-自定义接口加解密"></a>
## 自定义接口加解密

[文档首页](README.md) / [接口安全](security/README.md)

提供一个 CryptoCodec Bean，即可替换接口加密和解密，无需修改 Controller，也无需配置框架的 AES 密钥。

<a id="ai-security-crypto-codec-公开接口"></a>
### 公开接口

```java
package com.yulinlin.security.crypto;

public interface CryptoCodec {
    String encrypt(CryptoContext context, String plaintext);
    String decrypt(CryptoContext context, String ciphertext);
}
```

encrypt 和 decrypt 都处理字符串。对象由框架先转 JSON；R 响应只处理 data，其他字段保持不变。Codec 不负责 JSON 序列化、登录、权限或响应包装。

CryptoContext 提供框架规范化后的 `deviceType()` 和原请求头的只读副本 `requestHeaders()`。自定义实现可以根据 `X-Key-Id`、租户、Token 或协议版本选择密钥，但不能修改 Web 请求头。响应加密阶段的 `requestHeaders()` 仍然是原请求头，不是响应头。

<a id="ai-security-crypto-codec-注册业务实现"></a>
### 注册业务实现

放在业务配置类中，下面 BusinessCryptoService 是业务自己的加解密服务，不是框架类：

```java
import com.yulinlin.security.crypto.CryptoCodec;
import com.yulinlin.security.crypto.CryptoContext;
import org.springframework.context.annotation.Bean;

@Bean
public CryptoCodec cryptoCodec(BusinessCryptoService service) {
    return new CryptoCodec() {
        @Override
        public String encrypt(CryptoContext context, String plaintext) {
            String keyId = context.requestHeaders().getFirst("X-Key-Id");
            return service.encrypt(context.deviceType(), keyId, plaintext);
        }

        @Override
        public String decrypt(CryptoContext context, String ciphertext) {
            String keyId = context.requestHeaders().getFirst("X-Key-Id");
            return service.decrypt(context.deviceType(), keyId, ciphertext);
        }
    };
}
```

业务服务负责安全算法、密钥管理和密文格式，不能把 Base64 编码当作加密。实现必须线程安全，失败抛 CryptoException，不返回原始明文兜底。

| 场景 | 选择 |
| --- | --- |
| 有自定义 CryptoCodec Bean | 使用自定义实现，不要求配置 devices、key 或 iv |
| 无自定义 Bean，有设备密钥 | 使用默认多设备 AES |
| 无自定义 Bean，也无设备密钥 | 请求和响应均不做加解密 |

通常只提供一个 CryptoCodec Bean；多个时必须用 @Primary 明确选择。设备密钥配置不会将自定义实现与默认实现叠加使用。

<a id="ai-security-crypto-codec-保留的配置与行为"></a>
### 保留的配置与行为

```yaml
yulinlin:
  security:
    crypto:
      advice-enabled: true # 默认开启；false 只停用两个 MVC Advice，保留 Codec
      enabled: true # 无注解接口也默认要求密文请求、加密响应
      device-header: Device-Type
      exclude-paths:
        - /monitor/**
      max-request-size: 1MB
```

`advice-enabled=false` 时不注册框架的请求解密和响应加密 Advice，保留自定义 CryptoCodec，可继续注入和手动调用；不影响鉴权、限流，修改配置后需重启。注解不能覆盖该开关，业务自行注册的处理逻辑仍由业务管理。

`advice-enabled=true` 时，自定义实现与默认 AES 使用相同的全局规则：无需注解，enabled=true 时默认要求密文请求、加密响应。@ApiCrypto(false) 是请求兼容明文/密文、响应明文的例外；方法设置优先于类设置。enabled=false 时，所有接口（无注解或任意注解）都兼容明密文请求、返回明文，不是禁用解密。排除路径始终请求和响应都不处理，文件、监控及不走该协议的回调接口应配置排除，详见[规则表](#ai-security-crypto-接口注解)。

`context.deviceType()` 已去除首尾空白并转换为小写，只接受 1～32 位字母、数字、下划线或短横线。请求头缺失、空白或格式非法时传入 unknown；其他合法设备名原样传给自定义实现，不根据 YAML 设备列表过滤。业务决定未知设备使用哪组密钥或拒绝请求，设备类型不是身份凭证。

<a id="ai-security-crypto-codec-密文协议约定"></a>
### 密文协议约定

- 本接口支持 UTF-8 文本密文，不接收二进制报文或 JSON 密文对象。
- 兼容模式把去除首尾空白后以 `{`、`[` 开头的正文当作明文 JSON；严格模式拒绝此类明文。其他正文才交给 decrypt，因此 encrypt 不能返回以 `{`、`[` 开头的内容。
- decrypt 接收到的是原始正文字符串，包括空白、换行、引号和加号；框架不会提前改成 Base64、去引号或将空格替换为加号。这些兼容规则由自己的协议决定。
- encrypt 必须返回非空且非纯空白的密文；decrypt 不得返回 null。解密失败返回 HTTP 400，不再降级成明文；加密失败不返回未加密数据。
- 收到的正文和解密结果都受 max-request-size 限制；自定义实现还应避免无界分配内存。
- 响应仍使用 JSON 字符串，R.data 为密文时 crypt=true。客户端按接口原始类型解密为字符串或 JSON。
- 自定义实现的响应回写规范化的设备名，不自动发送 X-Yulinlin-Crypto 算法头；该头只用于默认 AES 协议。自定义客户端按双方约定识别密文，不依赖 AES 专属响应头。

请求和响应使用同一个 Codec。只改变一个方向的格式时，也必须保证另一个方向与客户端协议兼容。

<a id="ai-security-crypto-codec-手动调用"></a>
### 手动调用

注入 CryptoCodec 即可，与接口自动加解密使用相同的 Bean：

```java
CryptoContext context = CryptoContext.of("web");
String encrypted = cryptoCodec.encrypt(context, json);
String decrypted = cryptoCodec.decrypt(context, encrypted);
```

手动调用时由调用方提供规范设备名、控制大小并处理异常；`CryptoContext.of` 使用空请求头。如果手动调用也需要协议头，可以显式构造 `new CryptoContext(deviceType, headers)`。手动调用不会自动应用 @ApiCrypto、路径排除、JSON 序列化或响应包装。

---

下一步：[默认加密配置与前端对接](#ai-security-crypto)。

---

<a id="ai-security-rate-limit"></a>
<!-- source: doc/security/rate-limit.md -->
<a id="ai-security-rate-limit-限流与-ip-黑名单"></a>
## 限流与 IP 黑名单

[文档首页](README.md) / [接口安全](security/README.md)

注解限流、业务黑名单和 Nginx 可信真实 IP。

<a id="ai-security-rate-limit-本页导航"></a>
### 本页导航

- [接口限流](#ai-security-rate-limit-接口限流)
- [IP 黑名单](#ai-security-rate-limit-ip-黑名单)
- [Nginx 真实 IP](#ai-security-rate-limit-nginx-真实-ip)

<a id="ai-security-rate-limit-接口限流"></a>
### 接口限流

`@ApiLimit` 可以放在 Controller 类或方法上，方法配置覆盖类配置。默认同一客户端对同一接口每分钟最多调用 60 次，并按 IP 隔离：

```java
import com.yulinlin.security.annotation.ApiLimit;
import com.yulinlin.security.limit.LimitBy;

@RestController
public class VideoController {

    // 等价于 count=60、interval="1m"、by=LimitBy.IP
    @ApiLimit
    @PostMapping("/video/list")
    public R<?> list(@RequestBody VideoListPo request) {
        return R.ok(videoService.list(request));
    }

    // 设备 ID 缺失或非法时自动回退到真实 IP
    @ApiLimit(count = 20, interval = "10s", by = LimitBy.DEVICE)
    @PostMapping("/video/like")
    public R<?> like(@RequestBody VideoLikePo request) {
        return R.ok(videoService.like(request));
    }
}
```

interval 支持 `500ms`、`10s`、`1m`、`1h` 等带单位的时长。没有 `@ApiLimit` 的接口不参与限流；`OPTIONS` 请求直接放行。超出限制时返回 HTTP `429`、`Retry-After` 响应头和未加密的统一 JSON：

```json
{
  "data": null,
  "code": 429,
  "crypt": false,
  "ok": false,
  "msg": "请求过于频繁，请稍后再试",
  "timestamp": 1792070400000
}
```

各接口按 IP 或设备 ID 独立计数，不活跃记录随 interval 到期淘汰。maximum-size 限制本机保留的计数器数量；达到容量上限而被提前淘汰的客户端再次访问时会重新计数。多实例各自计数，不提供全局统一额度。

<a id="ai-security-rate-limit-ip-黑名单"></a>
#### IP 黑名单

IP 黑名单由业务实现 `IpBlacklist` 接口，自行选择数据存储。接口只接收已经完成可信代理检查并规范化的真实客户端 IP：

```java
import com.yulinlin.security.limit.IpBlacklist;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfiguration {

    @Bean
    public IpBlacklist ipBlacklist(IpBlacklistService service) {
        return service::isBlocked;
    }
}
```

一个简单的本地实现：

```java
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IpBlacklistService {
    private final Set<String> blockedIps = ConcurrentHashMap.newKeySet();

    public boolean isBlocked(String ip) {
        return blockedIps.contains(ip);
    }

    public void block(String ip) {
        blockedIps.add(ip);
    }

    public void unblock(String ip) {
        blockedIps.remove(ip);
    }
}
```

没有业务 Bean 时，框架注册默认空实现并放行所有 IP。命中黑名单时不进入 Controller，直接返回 HTTP `403` 和未加密的统一 JSON，`msg` 为 `当前 IP 已被禁止访问`。黑名单检查对所有 Controller 生效，不要求存在 `@ApiLimit`。

`isBlocked` 会在每次业务请求进入 Controller 前执行，应使用快速查询，避免同步慢 SQL。可使用业务缓存，或将数据库黑名单定时加载到本地。拉黑、解除、过期时间、CIDR 规则和管理接口都属于业务实现；框架只负责可靠传入真实 IP。实现抛出的异常不会被静默当作放行，避免安全检查在故障时无声失效。

<a id="ai-security-rate-limit-nginx-真实-ip"></a>
#### Nginx 真实 IP

后端由本机 Nginx 代理时推荐：

```nginx
location /api/ {
    if ($request_method = OPTIONS) {
        return 204;
    }

    rewrite ^/api/(.*)$ /$1 break;
    proxy_pass http://127.0.0.1:9091;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

框架先检查 Java 服务看到的 TCP 来源地址。只有来源匹配 `trusted-proxies` 时才读取 `X-Real-IP`；否则忽略所有转发 IP，使用连接来源地址。缺失、非法或包含多个值的 `X-Real-IP` 也会回退连接地址，避免客户端伪造。`trusted-proxies` 支持单个 IPv4、IPv6 和 CIDR，例如容器网络可以明确增加 `172.16.0.0/12`，不要无条件信任所有地址。Java 服务端口也应只允许可信 Nginx 访问。

如果 Nginx 前面还有 CDN 或负载均衡，必须先正确配置 Nginx `real_ip` 模块，使 `$remote_addr` 恢复为真实客户端 IP，再转发给 Java。框架不会取 `X-Forwarded-For` 的第一个值。

`LimitBy.DEVICE` 读取 `Device-Id`，接受最长 128 个字符的字母、数字、点、下划线、冒号和短横线。设备 ID 由客户端提供，能够被修改或轮换，因此视频列表、详情和防采集接口应优先按 IP；设备维度适合点赞、收藏等正常设备行为的额度隔离，不能替代登录鉴权。

限流配置默认值：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否处理 `@ApiLimit` |
| `maximum-size` | `50000` | 本机同时保留的计数器上限，不是单接口请求次数 |
| `ip-header` | `X-Real-IP` | 仅从可信代理连接读取的客户端 IP 请求头 |
| `device-id-header` | `Device-Id` | `LimitBy.DEVICE` 使用的请求头 |
| `trusted-proxies` | `127.0.0.1`、`::1` | 可以提供真实 IP 请求头的代理地址或 CIDR |

---

下一步：返回[接口安全主题目录](security/README.md)按需选择相关专题。

---

<a id="ai-security-user"></a>
<!-- source: doc/security/user.md -->
<a id="ai-security-user-用户模型与安全管理器"></a>
## 用户模型与安全管理器

[文档首页](README.md) / [接口安全](security/README.md)

统一 `login(LoginRequest)`，通过 `LoginAuthenticator<T>` 扩展认证方式，用认证器 `name()` 记录本次登录的 `authType`。

<a id="ai-security-user-本页导航"></a>
### 本页导航

- [登录用户模型](#ai-security-user-登录用户模型)
- [安全管理器](#ai-security-user-安全管理器)
- [业务账号认证](#ai-security-user-业务账号认证)
- [业务密钥认证](#ai-security-user-业务密钥认证)
- [根据用户 ID 登录](#ai-security-user-根据用户-id-登录)
- [自定义认证方式](#ai-security-user-自定义认证方式)
- [一个业务组件实现两个接口](#ai-security-user-一个业务组件实现两个接口)
- [常用组件扩展入口](#ai-security-user-常用组件扩展入口)
- [加载用户信息](#ai-security-user-加载用户信息)

<a id="ai-security-user-登录用户模型"></a>
### 登录用户模型

`com.yulinlin.security.auth.IUserDetails` 由业务安全 DTO 实现：

```java
public interface IUserDetails {
    String getUserId();
    String getUsername();
    Set<String> getRoles();
    Set<String> getPermissions();
    Object getAttribute(String name);
    // 接口还自带 hasRole(role)、hasPermission(permission)。
}
```

角色/权限使用 Set，执行精确且区分大小写的判断，不解释通配符；集合或检查参数为 null 时返回 false。未知 attribute 返回 null。用户模型不得包含密码、密码哈希、API Key、Token 等凭证；`authType` 属于某次登录，不放进用户模型。

完整项目案例见 [Admin RBAC](#ai-practice-rbac)：同一业务 Bean 实现 LoginAuthenticator<PasswordLogin> 与 UserStore，Controller 使用 login(new PasswordLogin(...))。

<a id="ai-security-user-安全管理器"></a>
### 安全管理器

```java
import com.yulinlin.security.auth.*;

// 以下是不同场景的替代写法，不需连续登录。
LoginResult result = SecurityUtil.login(new PasswordLogin(username, password, "web"));
LoginResult partner = SecurityUtil.login(new ApiKeyLogin(apiKey, "server"));
// 仅限服务端已完成可信外部验证的场景，详见下文。
LoginResult trusted = SecurityUtil.login(new UserIdLogin(verifiedUserId, "web"));
```

也可注入 `ISecurityManager` 调用 `login(request)`。PasswordLogin、ApiKeyLogin、UserIdLogin 省略/null 设备时默认 `default`，空白或首尾含空白的设备被拒绝。密码/密钥不 trim。业务验证码、外部授权回调验证应在相应业务认证流程完成；设备不是身份凭证，不自动读取加密用 Device-Type 请求头。

`LoginResult(token, expiresAt, user, authType)` 返回安全用户及登录信息。JWT/Sa-Token 返回 Token；Spring 默认 Session 模式 token/expiresAt 为 null。`authType` 取自实际成功认证器的 name，不是客户端参数，也不是 Java 类名或 Bean 名。

```java
String type = SecurityContext.getAuthType(); // 例如 password、api-key、sms、user-id
String same = SecurityUtil.getAuthType();
IUserDetails current = SecurityUtil.getUserDetails(); // 匿名为 null
IUserDetails required = SecurityUtil.requireUser();  // 匿名抛 LoginException
String userId = SecurityUtil.getUserId();
SecurityUtil.hasRole("admin");
SecurityUtil.hasPermission("video:update");
SecurityUtil.refresh();       // 刷新用户，不改变登录方式，不重新签发 Token
SecurityUtil.getUserDetails(otherUserId); // 只查询用户，不切换身份
SecurityUtil.logout();
```

authType 在后续请求中保持为本次登录的认证来源。JWT/Sa-Token 缺少 authType 的凭证会被拒绝，需要重新登录；不推测默认认证方式。已有外部登录没有提供可信来源时返回 null，不代表通过了 password 认证。

静态调用与注入 ISecurityManager 使用同一个安全实现；应用初始化完成后再调用，不在 Bean 构造器中调用。只选择一个登录模块，不叠加 JWT/Sa-Token/Spring。仅引入公共 security 时默认匿名，不提供登录能力。

<a id="ai-security-user-业务账号认证"></a>
### 业务账号认证

统一 SPI：

```java
public interface LoginAuthenticator<T extends LoginRequest> {
    String name();
    Class<T> requestType();
    IUserDetails authenticate(T request);
}
```

业务声明 Bean，框架启动收集并检查冲突。按**精确参数类**查找，不根据继承顺序/Bean 优先级猜测；同一 requestType 和同一 name 都不能重复。name 限 1～64 位字母、数字、点、下划线、冒号或短横线，并以字母或数字开头。启动时固定注册名称，运行中不要修改它。

密码认证示例（AccountService 是业务服务，不是框架类）：

```java
import com.yulinlin.security.auth.*;
import org.springframework.stereotype.Component;

@Component
public class PasswordAuthenticator implements LoginAuthenticator<PasswordLogin> {
    private final AccountService accounts;
    public PasswordAuthenticator(AccountService accounts) { this.accounts = accounts; }
    @Override public String name() { return "password"; }
    @Override public Class<PasswordLogin> requestType() { return PasswordLogin.class; }
    @Override public IUserDetails authenticate(PasswordLogin request) {
        // 按 username 查询，验证密码哈希、账号状态，再返回不含凭证的 DTO。
        return accounts.authenticate(request.username(), request.password());
    }
}
```

框架不内置用户表、明文密码比较或密码规则。认证拒绝时返回 null 或抛 LoginException，账号不存在与密码错误应使用相同提示；存储/网络故障正常抛出。无匹配认证器明确报错，不回退到 user-id。认证器不能递归调用 login，也不能只查到用户就跳过凭证验证。密码认证器和密钥认证器都需要业务提供，没有注册仍可启动并恢复有效的已有凭证。

<a id="ai-security-user-业务密钥认证"></a>
### 业务密钥认证

```java
@Component
public class PartnerAuthenticator implements LoginAuthenticator<ApiKeyLogin> {
    private final PartnerService partners;
    public PartnerAuthenticator(PartnerService partners) { this.partners = partners; }
    @Override public String name() { return "api-key"; }
    @Override public Class<ApiKeyLogin> requestType() { return ApiKeyLogin.class; }
    @Override public IUserDetails authenticate(ApiKeyLogin request) {
        return partners.authenticateApiKey(request.apiKey());
    }
}
```

PartnerService 是业务服务，负责验证密钥摘要、有效期、停用状态和绑定账号状态。框架拒绝空白及超过 8192 字符的密钥。密钥与 AES/JWT 签名密钥分开，使用高强度随机值、摘要存储、HTTPS、限流，不放 URL 或日志。浏览器写死共享密钥不构成可靠用户身份。参见 [OWASP REST 安全建议](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html)。

**密钥登录是验证一次后换取 Token/会话，不是每次请求传 X-Api-Key。**停用密钥不自动撤销已经发出的凭证。JWT/Sa-Token 后续通过 UserStore.load(userId) 恢复权限，推荐绑定独立服务账号；不能只在认证时临时缩减权限，后续却加载更大的用户权限。需要每把密钥独立权限/即时撤销时，业务扩展 Token/会话恢复校验，不由 authType 自动实现。

HTTP 使用明确参数类，不允许客户端指定任意 Java 类型。两个接口共用统一入口：

```java
import com.yulinlin.security.annotation.*;
import com.yulinlin.security.auth.*;
import com.yulinlin.starter.domain.R;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@ApiLogin
public class AuthController {
    @ApiPublic
    @ApiLimit(count = 10, interval = "1m")
    @PostMapping("/password")
    public R<LoginResult> password(@RequestBody PasswordLogin request) {
        return R.ok(SecurityUtil.login(request), "登录成功");
    }
    @ApiPublic
    @ApiLimit(count = 10, interval = "1m")
    @PostMapping("/api-key")
    public R<LoginResult> apiKey(@RequestBody ApiKeyLogin request) {
        return R.ok(SecurityUtil.login(request), "登录成功");
    }
    @PostMapping("/me")
    public R<IUserDetails> me() { return R.ok(SecurityUtil.requireUser()); }
    @PostMapping("/logout")
    public R<Void> logout() {
        SecurityUtil.logout();
        return R.ok(null, "注销成功");
    }
}
```

示例使用项目 starter 的 R，业务将 LoginException 统一转为 HTTP 401；项目已有的访问控制、防跨站请求校验及报文加密规则仍需遵守。密钥接口 JSON 为 `{"apiKey":"业务密钥","deviceType":"server"}`，后续使用返回的 Token。登录前移除失效旧 Token，公开接口携带无效凭证也会被拒绝。

<a id="ai-security-user-根据用户-id-登录"></a>
### 根据用户 ID 登录

存在唯一/Primary UserStore 且业务没有注册 UserIdLogin 认证器时，默认支持 ID 登录，authType 为 `user-id`，按 ID 加载用户并检查返回 ID 一致；UserStore 应拒绝禁用/删除用户。自定义 LoginAuthenticator<UserIdLogin> Bean 可替换该行为。

```java
// verifiedUserId 必须来自服务端已经验证的授权结果，不能直接来自客户端参数。
String verifiedUserId = externalLogin.verifyCallback(callback);
LoginResult result = SecurityUtil.login(new UserIdLogin(verifiedUserId, "web"));
```

**用户 ID 不是身份凭证**。默认实现只检查用户可用性，不负责外部回调验签、验证码或管理员代登录权限。禁止公开 `/login/user-id` 接口直接接收任意用户 ID；不要把 UserIdLogin 加入公开 JSON 多态类型白名单。需要对外短信/第三方登录时，定义对应 LoginRequest，在自己的认证器内完成验证，才能准确记录 sms/oauth 等 authType，而非笼统的 user-id。

<a id="ai-security-user-自定义认证方式"></a>
### 自定义认证方式

```java
public record SmsLogin(String phone, String code, String deviceType) implements LoginRequest {
    public SmsLogin { if (deviceType == null) deviceType = "default"; }
    @Override public String toString() { return "SmsLogin[凭证已隐藏]"; }
}

@Component
public class SmsAuthenticator implements LoginAuthenticator<SmsLogin> {
    private final AccountService accounts;
    public SmsAuthenticator(AccountService accounts) { this.accounts = accounts; }
    @Override public String name() { return "sms"; }
    @Override public Class<SmsLogin> requestType() { return SmsLogin.class; }
    @Override public IUserDetails authenticate(SmsLogin request) {
        // 业务还应校验验证码有效期、使用次数、账号状态和防重放。
        return accounts.authenticateSms(request.phone(), request.code());
    }
}

// 在已经配置对应认证器的业务中调用
SecurityUtil.login(new SmsLogin(phone, code, "web"));
String type = SecurityContext.getAuthType(); // sms
```

不新增枚举值、不修改管理器分支。不同密码认证体系可定义不同参数类和 name；同一 ApiKeyLogin 类型不能通过注册多个 Bean 随机选择。需要区分认证器内部的多个密钥账户，用用户 ID/业务元数据表达，name 表示认证方式，不是每把密钥的 ID。

内置参数类 toString 隐藏凭证，但显式 JSON 序列化仍包含请求字段；自定义参数也应隐藏敏感信息，不记录完整登录请求/响应，不把 LoginRequest 放入会话或用户 DTO。

<a id="ai-security-user-一个业务组件实现两个接口"></a>
### 一个业务组件实现两个接口

可以让业务类同时实现 `LoginAuthenticator<PasswordLogin>` 和 `UserStore`，分别实现 name、requestType、authenticate(PasswordLogin)、load(userId)。

认证时根据凭证验证用户，后续请求按稳定 userId 恢复，不把用户名当 ID。一个类不能同时实现同一个泛型接口的两个不同类型实例；多种登录方式分别提供认证器 Bean，可以复用同一个 AccountService。

<a id="ai-security-user-常用组件扩展入口"></a>
### 常用组件扩展入口

| 能力 | 扩展点 | 接入方式 |
| --- | --- | --- |
| 安全用户模型 | IUserDetails | 不含凭证的业务 DTO |
| 任意认证方式 | LoginAuthenticator<T> + LoginRequest | 提供 Bean，精确类型分发，name 作为 authType |
| 用户资料与权限 | UserStore | 按稳定用户 ID 加载；可缓存但业务负责失效 |
| JWT 生成/验签 | JwtTokenCodec | generate(user, deviceType, authType) 与 parse 成对替换 |
| Token 读取 | LoginInterceptor | Bean 自动替换并注册，默认生命周期清理仍在 |
| 整体安全实现 | ISecurityManager | 实现 login(LoginRequest)、恢复、注销等；可适配外部上下文 |

登录公共协议位于 com.yulinlin.security.auth，JwtTokenCodec 位于 com.yulinlin.security.jwt，LoginInterceptor 位于 com.yulinlin.security.web。认证名称来自验证流程，不因 HTTP URL、类名或 Bean 名变化而变化。authType 不代替角色权限，也不自动实现多因素认证。

<a id="ai-security-user-加载用户信息"></a>
### 加载用户信息

```java
@Bean
public UserStore userStore(AccountService accounts) {
    return userId -> accounts.loadSecurityUser(userId);
}
```

UserStore.load 不验证密码、不签发 Token，不递归读取 SecurityContext。不存在/禁用用户返回 null 或抛 LoginException；基础设施错误正常抛出，不能返回默认用户，返回 ID 必须与查询 ID 相同。

JWT/Sa-Token 认证器返回的安全用户直接用于当前登录，下一次请求才按 ID 重新加载；请求内复用快照。业务缓存需要自行处理角色/状态变更，refresh 不会替业务清理缓存。已有 Spring 登录体系采用其用户资料加载规则，不承诺自动重查业务数据库。

---

下一步：返回[接口安全主题目录](security/README.md)按需选择相关专题。

---

<a id="ai-security-jwt"></a>
<!-- source: doc/security/jwt.md -->
<a id="ai-security-jwt-无状态-jwt-登录"></a>
## 无状态 JWT 登录

[文档首页](README.md) / [接口安全](security/README.md)

最小接入和请求身份恢复；注销不会撤销已发出的 JWT。

前置阅读：[安全模块选型](#ai-security-modules)、[用户模型与 UserStore](#ai-security-user)。请求恢复与清理见[登录上下文](#ai-security-context)，自定义接入见[扩展专题](#ai-security-extension)。

<a id="ai-security-jwt-本页导航"></a>
### 本页导航

- [JWT 最小接入](#ai-security-jwt-jwt-最小接入)

<a id="ai-security-jwt-jwt-最小接入"></a>
### JWT 最小接入

引入 `com.yulinlin:security-jwt:3.0`，注册 [UserStore 与 LoginAuthenticator](#ai-security-user)。统一调用 `SecurityUtil.login(new PasswordLogin(username, password, "web"))` 或 `login(new ApiKeyLogin(apiKey, "server"))`，分别由对应参数类型的业务认证器验证凭证。业务已完成外部身份验证时，也可调用 `login(new UserIdLogin(userId, "web"))`，默认通过 UserStore 检查用户可用性；不能向客户端开放任意 ID 登录。自定义登录方式同样注册 LoginAuthenticator，无需修改 JWT 模块。再配置：

```yaml
yulinlin:
  security:
    jwt:
      secret: ${YULINLIN_JWT_SECRET} # 至少 32 字节随机密钥的 Base64，不是 AES 设备密钥
      issuer: yulinlin
      ttl: 2h
```

默认使用 HS256 签名，Token 包含用户 ID、设备类型、认证来源、唯一凭证标识和有效期，不保存密码或完整用户资料。authType 取成功认证器的 name，不接收客户端自报值。请求必须通过签名、签发者、时间及用户可用性校验。`ttl` 默认 2h，允许 1s～365d。JWT 是签名而非加密，不要加入密码等秘密资料。

登录响应包含 authType，后续使用 `SecurityContext.getAuthType()` 或 `SecurityUtil.getAuthType()` 读取。缺少 deviceType/authType 的旧 Token 不再兼容，需要重新登录；不根据当前请求猜测或补齐认证方式。

本模块提供**纯无状态 JWT**，没有服务端登录记录、Token 黑名单或按用户撤销能力。注销后客户端应删除 Token，但已发出的 Token 仍可能有效。保持签名密钥和校验配置稳定即可在应用重启后使用未过期 JWT，前提是 `UserStore` 仍允许该用户访问。

业务 API Key 与 JWT 签名 secret 是不同凭证。密钥登录只在签发时验证 API Key，JWT 不保存原密钥或独立密钥权限；停用/轮换 API Key 不会自动撤销已签发 JWT。后续权限以 UserStore 为准，建议密钥绑定独立服务账号，不能只在认证时临时缩减同一用户的权限。

需要注销立即失效或管理在线登录状态时，选择[有状态登录](#ai-security-sa-token)或[自定义安全实现](#ai-security-extension)，不要把纯 JWT 的注销当成服务端撤销。

---

下一步：返回[接口安全主题目录](security/README.md)按需选择相关专题。

---

<a id="ai-security-token-codec"></a>
<!-- source: doc/security/token-codec.md -->
<a id="ai-security-token-codec-自定义-token-生成与解析"></a>
## 自定义 Token 生成与解析

[文档首页](README.md) / [接口安全](security/README.md)

替换 JwtTokenCodec，统一生成与解析协议。

前置阅读：[安全模块选型](#ai-security-modules)、[用户模型与 UserStore](#ai-security-user)。请求恢复与清理见[登录上下文](#ai-security-context)，自定义接入见[扩展专题](#ai-security-extension)。

<a id="ai-security-token-codec-本页导航"></a>
### 本页导航

- [自定义 Token 生成与解析](#ai-security-token-codec-自定义-token-生成与解析-1)

<a id="ai-security-token-codec-自定义-token-生成与解析-1"></a>
### 自定义 Token 生成与解析

```java
package com.example.security;

import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.jwt.JwtTokenCodec;
import com.yulinlin.security.jwt.TokenInfo;

// 在业务配置中将此实现注册成唯一 JwtTokenCodec Bean。
public class BusinessTokenCodec implements JwtTokenCodec {
    private final BusinessTokenService tokens;

    public BusinessTokenCodec(BusinessTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public String generate(IUserDetails user, String deviceType, String authType) {
        return tokens.issue(user.getUserId(), deviceType, authType);
    }

    @Override
    public TokenInfo parse(String token) {
        return tokens.verifyAndParse(token);
    }
}
```

`BusinessTokenService` 是示意的业务实现，不是框架类；请委托成熟 JWT 库做签发和验签，不要只 Base64 解码。注册方式：`@Bean JwtTokenCodec jwtTokenCodec(BusinessTokenService tokens) { return new BusinessTokenCodec(tokens); }`。

解析结果定义为：

```java
public record TokenInfo(
    String userId,
    String deviceType,
    String authType,
    String tokenId,
    Instant issuedAt,
    Instant expiresAt
) {}
```

- 每次新登录生成唯一 tokenId；解析结果必须与生成时的 userId、deviceType 和 authType 一致，不能更换身份或认证来源。authType 来自成功认证的 LoginAuthenticator.name()，必须受签名保护。唯一标识不代表框架会保存登录记录或提供撤销功能。
- parse 必须完成验签等真实性校验；无效 Token 返回 null 或抛 `LoginException`。外部依赖故障应正常抛出，不能绕过验证。
- 签发时间不能在未来，过期时间必须晚于签发时间且未过期。自定义 Codec 以返回的 expiresAt 决定会话到期，不自动再套一层配置 ttl。
- 不兼容旧 Token：deviceType、authType 必须存在且有效；缺少时拒绝，不默认补成 password 或 user-id。authType 为 1～64 位字母、数字、点、下划线、冒号或短横线，且以字母或数字开头。
- 提供自定义 Codec 后，无需默认 Codec 的 secret/issuer；签名策略由自定义实现负责。它不负责存储、注销、加载用户或设置线程上下文。

如果只想手动生成/解析，可注入 `JwtTokenCodec` 调用上述方法；它不设置请求上下文，也不验证账号密码或业务密钥，只能由可信服务端代码使用，不应封装成接收任意 userId 的公开登录接口。正常登录调用 `SecurityUtil.login(LoginRequest)`，通过对应认证器验证后获得登录结果和当前身份。业务已完成外部验证时可使用 `UserIdLogin`，见[可信 ID 登录](#ai-security-user-根据用户-id-登录)。手动签发的 JWT 满足相同验签、时间、认证信息和用户检查时也可被接受，不需要服务端登记。

---

下一步：返回[接口安全主题目录](security/README.md)按需选择相关专题。

---

<a id="ai-security-extension"></a>
<!-- source: doc/security/extension.md -->
<a id="ai-security-extension-自定义安全实现与拦截器"></a>
## 自定义安全实现与拦截器

[文档首页](README.md) / [接口安全](security/README.md)

按需要替换公开接口，不需要了解默认实现的内部类或第三方调用链。

<a id="ai-security-extension-本页导航"></a>
### 本页导航

- [选择扩展点](#ai-security-extension-选择扩展点)
- [自定义有状态登录](#ai-security-extension-自定义有状态登录)
- [自定义登录拦截器](#ai-security-extension-自定义登录拦截器)
- [接入外部安全框架](#ai-security-extension-接入外部安全框架)

<a id="ai-security-extension-选择扩展点"></a>
### 选择扩展点

| 需求 | 公开扩展点 | 注册方式 |
| --- | --- | --- |
| 账号密码、业务密钥或其他认证方式 | LoginRequest + LoginAuthenticator<T> | 为每种请求类型提供认证器 Bean，name 记录为 authType |
| 加载用户资料、角色与权限 | UserStore | 提供按 userId 加载安全用户的 Bean |
| 替换 JWT 生成和解析 | JwtTokenCodec | 提供一个 Bean，生成和解析成对替换 |
| 替换接口请求解密和响应加密 | CryptoCodec | 提供一个 Bean，无需框架 AES 密钥，见[接口加解密扩展](#ai-security-crypto-codec) |
| 自定义凭证读取与请求身份恢复 | LoginInterceptor | 提供一个 Bean，自动替换并注册 |
| 完整自定义登录或接入已有认证体系 | ISecurityManager | 提供一个 Bean，按需要配套 LoginInterceptor |

认证示例见[用户模型](#ai-security-user)，Token 协议见[自定义 Token](#ai-security-token-codec)。只需要修改一项能力时，不必替换整个安全管理器。

<a id="ai-security-extension-自定义有状态登录"></a>
### 自定义有状态登录

需要注销后立即失效等服务端登录状态时，可以选择 [security-sa-token](#ai-security-sa-token)，或实现自己的 ISecurityManager。security-jwt 是无状态实现，不能通过增加缓存配置自动变成有状态登录。

自定义有状态实现需要明确以下约定：

- 登录成功前保存可靠的登录记录，明确每个 Token 与登录记录的对应关系。
- 请求时验证凭证、有效期、登录记录和用户状态；存储故障不能当作验证成功。
- 注销时撤销对应登录记录，并清理当前请求身份。按用户撤销、重置密码失效等能力由业务自行定义。
- 多实例使用共享状态；需要跨重启恢复时还要配置持久化。丢失登录记录不能自动降级为无状态放行。
- 明确请求身份恢复和清理的责任，避免上一次请求的身份影响下一次请求。

如果只改变签名算法、密钥来源或 Token 格式，替换 JwtTokenCodec 即可；它不负责登录记录或注销撤销。

<a id="ai-security-extension-自定义登录拦截器"></a>
### 自定义登录拦截器

业务提供 LoginInterceptor Bean，框架就使用它；没有自定义 Bean 时使用默认实现。无需手动重复注册。Bean 返回类型应声明为 LoginInterceptor 或其具体实现；通常只提供一个，存在多个时用 @Primary 明确选择。

下面示例用于 security-jwt，或完全使用 SecurityContext 管理请求身份的自定义安全实现：客户端通过 X-Token 发送原始凭证，调用公开的 resolveSession 验证后恢复上下文。

```java
import com.yulinlin.security.auth.*;
import com.yulinlin.security.web.LoginInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;

@Bean
public LoginInterceptor loginInterceptor(ISecurityManager manager) {
    return new LoginInterceptor() {
        @Override
        public boolean preHandle(HttpServletRequest request,
                                 HttpServletResponse response, Object handler) throws Exception {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
            String token = request.getHeader("X-Token");
            if (token == null) return true; // 匿名请求，是否允许访问由权限声明决定
            if (token.isBlank() || token.length() > 8192
                    || token.chars().anyMatch(Character::isWhitespace)) {
                response.sendError(401, "登录凭证无效");
                return false;
            }
            LoginSession session;
            try {
                session = manager.resolveSession(token);
            } catch (LoginException error) {
                response.sendError(401, "登录已失效，请重新登录");
                return false;
            }
            if (session == null || session.expired() || !token.equals(session.token())) {
                response.sendError(401, "登录已失效，请重新登录");
                return false;
            }
            SecurityContext.set(session);
            return true;
        }
    };
}
```

这是替换整个请求身份恢复行为，不只是新增一个请求头。manager.resolveSession 必须完成真实凭证验证；此示例不能直接替换 Sa-Token、Spring 或其他同时维护外部身份的请求处理。它们应保留模块默认凭证约定，或通过完整自定义接入统一管理两侧身份。不要将客户端传入的 userId 直接写入上下文。

框架负责自动注册和清理 SecurityContext，包括拒绝请求、异常、请求完成及异步切换。自定义实现若还维护其他请求状态，必须自行清理；基础设施异常正常传播，不能伪装成验证成功。

不需要启用开关，也不要重复注册同一个拦截器。只替换 Token 算法时，应使用 JwtTokenCodec，不必修改拦截器。

<a id="ai-security-extension-接入外部安全框架"></a>
### 接入外部安全框架

已有 [Sa-Token](#ai-security-sa-token) 或 [Spring Security](#ai-security-spring) 项目可选择对应模块。其他认证体系只需引入公共 security，提供 ISecurityManager；如果已有请求身份恢复机制，再提供 LoginInterceptor 对接已验证身份。不要同时引入多个登录模块。

业务侧继续使用 SecurityUtil、ISecurityManager 和 ApiLogin/ApiRole/ApiPerm，不依赖具体登录实现。需要实现的公开行为如下：

| 方法 | 接入约定 |
| --- | --- |
| login(LoginRequest) | 按对应认证方式验证凭证，返回 LoginResult；未支持的方式明确拒绝，不降级为 ID 登录 |
| resolveSession(token) | 使用默认 Token 拦截器时必须实现：验证凭证并恢复 LoginSession，不依赖当前请求身份 |
| getUserDetails() | 返回当前已验证用户，匿名返回 null |
| getToken() / getAuthType() | 返回当前 Token 和可信认证来源；不适用或来源未知时为 null，不编造 |
| logout() | 按自身登录协议注销，并清理请求身份；无状态 JWT 不能宣称已撤销旧 Token |
| getUserDetails(userId) | 查询指定用户，不切换当前身份 |
| refresh() / refresh(userId) | 刷新用户资料，当前用户时更新当前请求身份；不切换用户，不隐式续期 |

注册形式如下，BusinessSecurityManager、BusinessIdentityService 均为业务自己实现的类，不是框架提供的类：

```java
@Bean
public ISecurityManager securityManager(BusinessIdentityService identities) {
    return new BusinessSecurityManager(identities);
}
```

使用框架上下文时，公开模型为：

```java
LoginSession session = new LoginSession(token, expiresAt, safeUser, authType);
SecurityContext.set(session);
LoginResult result = LoginResult.of(session);
```

以上代码只用于已经完成认证的实现内部，不能包装成接收任意 userId 的公开登录接口。safeUser 不得携带密码、API Key 等凭证。authType 来自成功认证器的 name；已有外部身份没有可信来源时可以为 null，不猜测为 password。JWT/Sa-Token 凭证要求有效的认证来源，不能随意省略。

resolveSession 验证失败可返回 null 或抛 LoginException；login 失败应抛异常，不返回 null 冒充成功。使用默认 Token 拦截器时，恢复结果中的 Token 必须与请求 Token 一致。

已有外部上下文时，可由管理器直接读取其当前已验证身份；若业务还要读取 SecurityContext，自定义 LoginInterceptor 应同步设置它。自定义请求状态由业务负责在成功、拒绝、异常及异步切换时清理。不要仅清理 SecurityContext 就宣称已退出外部登录。

---

下一步：返回[接口安全主题目录](security/README.md)按需选择相关专题。

---

<a id="ai-security-sa-token"></a>
<!-- source: doc/security/sa-token.md -->
<a id="ai-security-sa-token-有状态-sa-token-登录"></a>
## 有状态 Sa-Token 登录

[文档首页](README.md) / [接口安全](security/README.md)

使用统一登录 API；注销当前 Token 后，该 Token 不再有效。

<a id="ai-security-sa-token-本页导航"></a>
### 本页导航

- [最小接入](#ai-security-sa-token-最小接入)
- [登录与请求](#ai-security-sa-token-登录与请求)
- [当前用户与注销](#ai-security-sa-token-当前用户与注销)
- [使用边界](#ai-security-sa-token-使用边界)

<a id="ai-security-sa-token-最小接入"></a>
### 最小接入

引入 `com.yulinlin:security-sa-token:3.0`，不要同时引入其他登录模块。提供：

- `UserStore`：按用户 ID 加载安全用户、角色和权限。
- `LoginAuthenticator<T>`：验证对应登录参数；只提供业务实际需要的认证方式。

完整 Bean 和 Controller 示例见[用户模型与安全管理器](#ai-security-user)。验证码、账号状态、密码及业务密钥规则由业务认证器校验。

<a id="ai-security-sa-token-登录与请求"></a>
### 登录与请求

```java
import com.yulinlin.security.auth.*;

LoginResult result = SecurityUtil.login(
        new PasswordLogin(username, password, "web"));

String token = result.token();
String authType = result.authType();
IUserDetails user = result.user();
```

其他登录方式使用相同入口，按场景选择，不需要连续调用：

```java
SecurityUtil.login(new ApiKeyLogin(apiKey, "server"));

// 仅供服务端已经完成可信身份验证的业务使用
SecurityUtil.login(new UserIdLogin(verifiedUserId, "web"));
```

登录接口返回 `R<LoginResult>`，客户端保存 Token；统一登录入口不会额外替客户端写登录 Cookie。

采用 Bearer 调用时，后续请求只携带这一份凭证：

```http
POST /video/list
Authorization: Bearer <token>
Content-Type: application/json

{}
```

已有项目使用其他凭证头或 Cookie 时应统一客户端约定，不要同时携带多份身份凭证。普通接入保留模块默认处理即可；需要完全自定义请求认证时，按[公开扩展协议](#ai-security-extension-接入外部安全框架)完整接入，不能直接套用仅设置框架上下文的 JWT 拦截器示例。

每次统一登录返回独立 Token，authType 取成功认证器的 name。旧凭证缺少必要认证信息时需要重新登录；不要假设绕过统一入口产生的凭证可以直接接入。

<a id="ai-security-sa-token-当前用户与注销"></a>
### 当前用户与注销

```java
IUserDetails user = SecurityUtil.requireUser();
String userId = SecurityUtil.getUserId();
String authType = SecurityUtil.getAuthType();

boolean permitted = SecurityUtil.hasPermission("video:edit");
SecurityUtil.refresh(); // 刷新用户资料，不切换账号

SecurityUtil.logout();  // 注销当前 Token
```

也可通过 `SecurityContext` 读取当前身份，使用 `@ApiLogin`、`@ApiRole`、`@ApiPerm` 声明权限。不要把第三方登录 API 混入普通业务代码。

客户端在注销成功后删除本地 Token。请求结束自动清理当前上下文，但不会注销登录；`SecurityContext.clear()` 也不等于注销。

<a id="ai-security-sa-token-使用边界"></a>
### 使用边界

- 默认登录记录保存在内存，应用重启后需要重新登录；重启恢复和多实例登录需要项目配置持久化、共享登录状态。
- 引入 ORM 的 cache-redis 模块，不等于登录状态已经持久化。
- `UserStore` 应拒绝不存在、禁用或不允许继续访问的用户。加载失败不能返回旧用户兜底。
- 修改密码、停用业务密钥不会自动注销已经建立的登录，不要把这类操作等同于注销全部设备。
- 公开统一 API 不提供在线会话列表、批量踢人或按用户注销全部 Token。
- 用户身份不自动传递到新线程，异步任务显式传入必要的业务信息。
- 复杂登录策略通过[公开扩展接口](#ai-security-extension)接入，普通业务继续调用同一套 API。

---

下一步：[登录上下文](#ai-security-context)、[权限注解](#ai-security-authorization)。

---

<a id="ai-security-spring"></a>
<!-- source: doc/security/spring.md -->
<a id="ai-security-spring-spring-security-接入"></a>
## Spring Security 接入

[文档首页](README.md) / [接口安全](security/README.md)

已有项目保留登录体系，业务侧统一使用 SecurityUtil、ISecurityManager 和权限注解。

<a id="ai-security-spring-本页导航"></a>
### 本页导航

- [最小接入](#ai-security-spring-最小接入)
- [当前用户与权限](#ai-security-spring-当前用户与权限)
- [登录与注销](#ai-security-spring-登录与注销)
- [查询用户与刷新](#ai-security-spring-查询用户与刷新)
- [使用边界](#ai-security-spring-使用边界)

<a id="ai-security-spring-最小接入"></a>
### 最小接入

已有 Spring Security 的 Servlet 项目引入 `com.yulinlin:security-spring:3.0`，不要同时引入 security-jwt 或 security-sa-token。

本模块用于接入已有认证体系，不替项目创建用户表、登录接口或完整认证配置。读取当前已登录用户不要求额外提供密码认证器。

**统一 login 入口需要项目先完成认证适配，仅引入模块或只声明 LoginAuthenticator，不保证登录入口已经可用。** 已有登录接口可以继续保留。本文只说明业务公开 API，不展开第三方认证配置和内部实现。

<a id="ai-security-spring-当前用户与权限"></a>
### 当前用户与权限

```java
import com.yulinlin.security.auth.IUserDetails;
import com.yulinlin.security.auth.SecurityContext;
import com.yulinlin.security.auth.SecurityUtil;

IUserDetails user = SecurityUtil.getUserDetails(); // 匿名为 null
IUserDetails required = SecurityUtil.requireUser(); // 未登录抛 LoginException
String userId = SecurityUtil.getUserId();
String authType = SecurityUtil.getAuthType();

boolean admin = SecurityUtil.hasRole("ADMIN");
boolean canEdit = SecurityUtil.hasPermission("video:edit");
```

业务用户 ID、角色和权限编码必须与当前项目的身份模型对应，不能默认把用户名当作数据库 ID。需要完整自定义身份适配时，使用[公开安全管理器接口](#ai-security-extension-接入外部安全框架)。

`@ApiLogin`、`@ApiRole`、`@ApiPerm` 可以继续使用。`@ApiPublic` 只豁免本框架相应的登录要求，不能绕过项目已有的访问控制或请求安全校验。

<a id="ai-security-spring-登录与注销"></a>
### 登录与注销

完成项目认证适配后，调用方式与其他模块相同：

```java
import com.yulinlin.security.auth.*;

LoginResult result = SecurityUtil.login(
        new PasswordLogin(username, password, "web"));

// 以下为其他场景，不需要连续调用
SecurityUtil.login(new ApiKeyLogin(apiKey, "server"));
SecurityUtil.login(new UserIdLogin(verifiedUserId, "web"));
```

账号密码和密钥分别提供对应 `LoginAuthenticator<T>`；可信 ID 登录需要 `UserStore` 或自定义同类型认证器。认证示例见[用户模型与安全管理器](#ai-security-user)。

默认会话登录返回的 `token`、`expiresAt` 可以为 null；不代表未登录，也不能把会话标识当成 Token 返回。客户端继续遵守项目已有的 Cookie、凭证和请求安全约定。

`authType` 来自成功认证器的 name；已有外部登录没有提供可信来源时为 null，不推测为 password。

```java
SecurityUtil.logout();
```

统一注销需要项目支持对应的注销能力，未完成接入时明确报错，应使用项目原有注销入口。不能用 `SecurityContext.clear()` 冒充注销。

<a id="ai-security-spring-查询用户与刷新"></a>
### 查询用户与刷新

| API | 行为 |
| --- | --- |
| getUserDetails() | 获取当前用户，匿名返回 null |
| getUserDetails(userId) | 只查询用户，不切换身份；查询其他用户需要 UserStore |
| refresh() | 重新读取当前身份，不重新登录、不承诺重查业务数据库 |
| refresh(userId) | 当前用户按 refresh 处理，其他用户只查询 |
| getToken() | 默认会话登录返回 null |
| getAuthType() | 返回可信认证来源，没有该信息时返回 null |

业务权限修改后是否立即生效，取决于项目采用的用户资料刷新策略；不要把 refresh 当成所有请求权限的强制刷新。

<a id="ai-security-spring-使用边界"></a>
### 使用边界

- 只支持 Servlet 项目，不支持 WebFlux。
- 统一登录应在 HTTP 接口中、响应输出前调用；不用于后台线程凭空建立浏览器会话。
- 项目已有的多因素认证、验证码、访问控制和防跨站请求校验仍必须执行。
- 请求结束清理当前上下文；用户身份不会自动传到普通异步线程。
- 加密、限流、权限注解可以按需使用，不需要把第三方框架类型暴露给业务接口。

---

下一步：[统一登录与认证器](#ai-security-user)、[公开扩展接口](#ai-security-extension)。

---

<a id="ai-security-context"></a>
<!-- source: doc/security/context.md -->
<a id="ai-security-context-登录上下文生命周期"></a>
## 登录上下文生命周期

[文档首页](README.md) / [接口安全](security/README.md)

业务只读取当前用户、Token 和认证来源；请求结束由框架清理上下文。

<a id="ai-security-context-本页导航"></a>
### 本页导航

- [登录上下文与请求生命周期](#ai-security-context-登录上下文与请求生命周期)
- [刷新与注销](#ai-security-context-刷新与注销)
- [异步与后台任务](#ai-security-context-异步与后台任务)

<a id="ai-security-context-登录上下文与请求生命周期"></a>
### 登录上下文与请求生命周期

```java
import com.yulinlin.security.auth.*;

LoginResult result = SecurityUtil.login(new PasswordLogin(username, password, "web"));
// HTTP 登录接口可返回 R<LoginResult>，包含 token、expiresAt、user、authType。

IUserDetails user = SecurityContext.requireUser();
String userId = SecurityContext.getUserId();
String token = SecurityContext.getToken();
String authType = SecurityContext.getAuthType();
boolean loggedIn = SecurityContext.isLogin();
```

也可全部使用 SecurityUtil，或者注入 ISecurityManager。未登录时 getUser/getUserDetails、getUserId、getToken、getAuthType 返回 null，requireUser 抛 LoginException。会话型登录可能没有可返回的 Token，判断登录状态使用 isLogin，而不是判断 token 是否为空。

JWT 客户端在后续请求携带 `Authorization: Bearer <token>`，其他登录模块按对应专题的客户端约定携带凭证。

请求行为：

- 有有效凭证：恢复当前用户及可信 authType。
- 没有凭证：按匿名访问，由权限注解决定是否允许。
- 凭证格式错误、过期或无效：默认 Token 接入返回 401，即使目标是公开接口；重新登录前移除失效旧凭证。
- 正常返回、异常、拒绝请求和异步请求切换时，自动清理本模块当前上下文。
- OPTIONS 不解析登录 Token。仅使用公共 security 的匿名模式不接管其他认证格式。

自定义拦截器和外部登录体系需要遵守同样的身份隔离要求，见[扩展接口](#ai-security-extension)。

<a id="ai-security-context-刷新与注销"></a>
### 刷新与注销

```java
SecurityUtil.refresh();                 // 刷新当前用户资料
SecurityUtil.getUserDetails(otherId);   // 查询其他用户，不切换登录
SecurityUtil.logout();                  // 按选定登录模块执行注销
```

- JWT：logout 不撤销已签发 Token，客户端必须自行删除；refresh 不改变 Token 有效期。
- 有状态登录：logout 注销当前 Token；请求结束不等于注销。
- 已有认证体系：注销和刷新遵循其已接入的公开能力，没有可信 authType 时可返回 null。

`SecurityContext.clear()` 只清理当前上下文，不使 Token 或其他登录状态失效。有有效凭证的下一次请求仍可恢复身份。

用户资料或角色变化不会强制改写所有正在处理的请求，业务需要时应主动复核；自定义 UserStore 缓存的刷新和失效仍由业务负责。

<a id="ai-security-context-异步与后台任务"></a>
### 异步与后台任务

普通新线程、线程池任务不会自动继承当前登录身份。通常把所需的 userId 等业务参数显式传入，不在后台重新登录。

确实需要非 HTTP 登录的 JWT 场景：

```java
try {
    SecurityUtil.login(new PasswordLogin(serviceUsername, servicePassword, "job"));
    // 执行当前线程业务
} finally {
    SecurityContext.clear();
}
```

SecurityContext.set/clear 是供可信服务端扩展使用的公开方法，不能将客户端直接提交的用户对象设置为登录身份。不要记录完整 Token、密码、API Key 或登录响应。

---

下一步：[权限注解](#ai-security-authorization)、[自定义实现](#ai-security-extension)。

---

<a id="ai-security-authorization"></a>
<!-- source: doc/security/authorization.md -->
<a id="ai-security-authorization-登录角色与权限注解"></a>
## 登录、角色与权限注解

[文档首页](README.md) / [接口安全](security/README.md)

ApiLogin、ApiRole、ApiPerm、ApiPublic 和默认匿名行为。

<a id="ai-security-authorization-本页导航"></a>
### 本页导航

- [鉴权声明注解](#ai-security-authorization-鉴权声明注解)
- [默认匿名安全管理器](#ai-security-authorization-默认匿名安全管理器)

<a id="ai-security-authorization-鉴权声明注解"></a>
### 鉴权声明注解

`com.yulinlin.security.annotation` 提供以下四个公共注解，与 ApiCrypto、ApiLimit 命名统一。权限判断以 ISecurityManager 提供的当前用户、角色和权限为准；注解不负责登录、查询账号表或刷新用户资料。

| 注解 | 声明含义 | 参数 |
| --- | --- | --- |
| `@ApiLogin` | 调用方必须登录 | 无 |
| `@ApiRole("admin")` | 调用方必须登录并拥有指定角色 | 必填 `String[] value`；`any` 默认 false |
| `@ApiPerm("user:query")` | 调用方必须登录并拥有指定权限 | 必填 `String[] value`；`any` 默认 false |
| `@ApiPublic` | 允许匿名访问 | 无 |

四个注解均可放在 Controller 或 Service 的类、接口和公共方法上。解析支持接口声明、实现类、父类、重写方法、泛型桥接和继承的 default 方法。角色和权限默认按大小写精确匹配，不解释通配符或 SpEL，也没有 admin 自动越权；`any=false` 表示全部满足，`any=true` 表示满足任意一个。

通过容器管理的 Bean 调用时生效，例如：

```java
import com.yulinlin.security.annotation.ApiLogin;
import com.yulinlin.security.annotation.ApiPerm;
import com.yulinlin.security.annotation.ApiPublic;
import com.yulinlin.security.annotation.ApiRole;

@ApiLogin
public class UserApi {
    @ApiPublic
    public void login() { }

    @ApiPerm("user:query")
    public void page() { }

    @ApiRole(value = {"admin", "operator"}, any = true)
    @ApiPerm({"user:query", "user:export"})
    public void export() { }
}
```

鉴权规则：

- ApiRole、ApiPerm 本身要求登录，不需要重复添加 ApiLogin。
- 类、父类、接口和方法上的要求叠加，都必须满足；方法上的同类注解不会覆盖类级要求。
- 方法 ApiPublic 可豁免类级 ApiLogin；不能绕过类级角色、权限要求，也不能与方法自身的鉴权要求共存。类上的 ApiPublic 不能与类级鉴权要求共存；但公开类中的方法可用 ApiLogin/ApiRole/ApiPerm 单独收紧访问。
- 空数组、空白编码、编码首尾空白或冲突声明会抛出明确的配置异常，不静默放行。
- 未登录返回 HTTP 401；登录后缺少角色或权限返回 HTTP 403。搭配 starter 时错误响应包装为 R；自定义异常处理器应保留对应状态，不把拒绝访问改成成功。
- 获取当前用户时发生的数据库、Redis 或业务异常原样传播，不伪装成未登录。ISecurityManager 的身份加载路径不要再次调用受鉴权保护的方法，否则明确报告递归配置错误；应直接使用 Repository、缓存等基础设施。
- ApiPublic 只影响鉴权，不关闭全局接口加解密、ApiLimit 或 IP 黑名单。公开登录接口也遵守加密配置，需要例外时使用 ApiCrypto(false) 或排除路径。

<a id="ai-security-authorization-默认匿名安全管理器"></a>
#### 默认匿名安全管理器

| 场景或方法 | 默认行为 |
| --- | --- |
| 只引入公共 security，未提供 ISecurityManager Bean | 默认匿名，正常启动 |
| `getUserDetails()`、`getUserDetails(userId)`、`refresh(userId)` | 返回 null，不查库、不生成身份 |
| `logout()` | 清理本模块当前线程上下文，不操作外部登录会话 |
| `login(LoginRequest)` | 默认抛 UnsupportedOperationException；匿名管理器不提供账号密码、密钥或 ID 登录 |
| 无鉴权注解或允许匿名的方法 | 不读取管理器，不影响加密和限流 |
| 默认匿名实现访问受保护方法 | 401，不会因为没有业务实现而放行 |
| 自定义 ISecurityManager Bean | 自动替换默认实现；项目只保留一个安全管理器 |

业务实现只需注册为普通 Bean，不需要额外的启用注解或配置。

使用边界：注解适用于经容器管理的 Bean 调用的 public、非 final 方法，不能用于 final 类。private/static 方法、手动 new 对象和同类 this 调用不在保护范围内，不能将它们当作安全入口；异步线程也不会自动继承当前请求身份。项目不能关闭方法拦截能力。

完整 Controller、Service 和权限声明案例见 [Admin RBAC](#ai-practice-rbac)。

---

下一步：返回[接口安全主题目录](security/README.md)按需选择相关专题。

---

<a id="ai-tools-catalog"></a>
<!-- source: doc/tools/catalog.md -->
<a id="ai-tools-catalog-lang-定位与类型目录"></a>
## lang 定位与类型目录

[文档首页](README.md) / [常用工具](tools/README.md)

先查工具所在模块和公开类型，再阅读对应专题。

<a id="ai-tools-catalog-本页导航"></a>
### 本页导航

- [lang 模块定位](#ai-tools-catalog-lang-模块定位)
- [lang 公开类型目录](#ai-tools-catalog-lang-公开类型目录)
- [常见问题](#ai-tools-catalog-常见问题)

<a id="ai-tools-catalog-lang-模块定位"></a>
### lang 模块定位

只使用基础工具时可以直接引入：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>lang</artifactId>
    <version>3.0</version>
</dependency>
```

`lang` 不是零依赖 JAR：JSON API 使用 Jackson，注解、泛型扫描与 AOP 扩展使用 Spring/AspectJ。若项目已经引入完整框架，无需重复声明。

本页把 API 分为三类：

- 业务工具：`JsonUtil`、`ReflectionUtil`、`DateTime`、`Page`、`StringUtil` 等，可直接调用。
- 轻量基础设施：`ExpiryMap`、`SmartCache`、`ResourcePool`、事件和线程工具，调用方需要管理生命周期与并发边界。
- 框架扩展 SPI：AOP、JSON 格式器、代理和安全响应接口，只有扩展框架时才实现或直接调用。

<a id="ai-tools-catalog-lang-公开类型目录"></a>
### lang 公开类型目录

下面按包列出当前全部公开类型，便于外部 AI 判断准确入口。未列为首选的兼容类型也没有删除。

| 包 | 公开类型 | 定位 |
| --- | --- | --- |
| `lang.aop` | BaseAnnotationBeanPostProcessor、AnnotationAdvisor | Spring AOP 扩展 SPI |
| `lang.cache` | ExpiryMap、ResourcePool | JVM 轻量过期缓存、资源池 |
| `lang.enums` | IEnum | 枚举 label/value 契约 |
| `lang.event` | AbstractEventPublishManager、EventPublishManager、IEvent、IEventHandler | JVM 内同步/异步事件 |
| `lang.json` | JsonUtil、YulinlinSimpModule、DateTimeJsonFormat、DateTimeStrJsonSerializer、MoneyJsonFormat、CoinJsonFormat、LongAdderJsonFormat、DoubleAdderJsonFormat、EnumFormat、EnumSerializer | Jackson 转换与扩展 |
| `lang.lambda` | LambdaPropertyFunction、LambdaUtils | 可序列化属性方法引用解析 |
| `lang.lazy` | SmartCache | 首次同步、过期异步刷新的单值缓存 |
| `lang.reflection` | ReflectionUtil、GenericUtil、AnnotationUtil、SerializableUtil、MethodHandlesUtil、ProxyUtil、ReflectAsmUtil | 反射、泛型、注解、序列化和代理 |
| `lang.security` | CryptoDataResponse | security 模块响应加密 SPI |
| `lang.tuple` | Triple、MutablePair、MutableTriple | 轻量位置容器 |
| `lang.util` | DateTime、Page、StringUtil、RandomUtil、Snowflake、WeightedSelector、SegmentLock、ThreadUtil、ListString、CodeObject、Currency、Money、Coin、ClassUtil、ScanUtils、BaseGeneric、GenericClass、GenericField、GenericMethod | 通用值、并发、扫描和兼容泛型工具 |

`DeepCopies`、`ValueCopies`、`HandleAccess` 是包内实现，不是外部 API。

<a id="ai-tools-catalog-常见问题"></a>
### 常见问题

| 问题 | 优先检查 |
| --- | --- |
| ReflectionUtil.property 的 NoSuchMethodError | 编译与运行时 lang/core 是否同一产物 |
| DTO 复制字段缺失 | 名称、可写属性、null 跳过及嵌套类型是否相容 |
| 克隆列表后共享引用不同 | 整体克隆还是每元素独立调用 |
| SmartCache 过期仍拿到旧值 | 这是 stale-while-revalidate；首次加载才阻塞 |
| ExpiryMap 实例不再使用 | 调用 shutdown 取消该实例清理任务 |
| ResourcePool 后续 acquire 卡住 | 检查每次借出是否恰好 release 一次 |
| Lambda 解析失败 | 参数是否为 LambdaPropertyFunction/其他可序列化 Lambda |
| DateTime 时间戳偏移 | JVM 默认时区是否符合业务协议 |
| Java 反序列化是否能处理请求数据 | 不要反序列化不可信输入 |
| HTTP 超时配置没生效 | Spring 客户端、静态客户端、手动构造和单次覆盖分别核对 |
| 下载成功但 body 为空 | downloadTo 内容在文件中 |
| 404 检查返回 false | 不代表存在，需区分网络/状态异常 |

本文按当前源码和 JDK 25 行为核对；HTTP 地址、文件路径及业务类型是示例，需要替换。工具类是进程内能力，不自动提供分布式一致性、持久化、重试或安全随机。

---

下一步：返回[常用工具主题目录](tools/README.md)按需选择相关专题。

---

<a id="ai-tools-http"></a>
<!-- source: doc/tools/http.md -->
<a id="ai-tools-http-http-请求上传与下载"></a>
## HTTP 请求、上传与下载

[文档首页](README.md) / [常用工具](tools/README.md)

JSON、表单、文件、超时设置和 404 检查。

需要替换底层 HTTP 库或统一扩展请求行为，见[HTTP 执行器规范与替换](#ai-tools-http-executor)。

<a id="ai-tools-http-本页导航"></a>
### 本页导航

- [HTTP 请求](#ai-tools-http-http-请求)
- [上传与下载](#ai-tools-http-上传与下载)
- [超时与错误处理](#ai-tools-http-超时与错误处理)
- [判断是否为 404](#ai-tools-http-判断是否为-404)
- [自定义客户端与生命周期](#ai-tools-http-自定义客户端与生命周期)

<a id="ai-tools-http-http-请求"></a>
### HTTP 请求

公共类型在 `com.yulinlin.data.core.http`：HttpRequestClient、HttpRequest、HttpUtil、HttpResponse、HttpFile、HttpRequestException。上层调用同步阻塞；默认由 RestClientHttpExecutor 使用 Spring RestClient + JDK HttpClient 执行，用户提供 HttpExecutor Bean 即可替换。

下面是完整 Service。复用客户端，每次调用创建新的请求对象；地址是示例，需替换为自己的服务：

```java
package demo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yulinlin.data.core.http.HttpRequestClient;
import com.yulinlin.data.core.http.HttpResponse;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Map;

@Service
public class RemoteApiService {
    private final HttpRequestClient client;

    public RemoteApiService(HttpRequestClient client) { this.client = client; }

    public Map<String, Object> createOrder(String token, String productId) {
        HttpResponse response = client.post("https://api.example.com/orders")
                .bearerToken(token)
                .json(Map.of("productId", productId, "quantity", 1))
                .timeout(Duration.ofSeconds(5))
                .execute();
        return response.bodyAs(new TypeReference<Map<String, Object>>() {});
    }
}
```

以下为业务方法体片段，采用静态入口：

```java
import com.yulinlin.data.core.http.HttpUtil;
import com.yulinlin.data.core.http.HttpResponse;
import java.util.Map;

HttpResponse response = HttpUtil.get("https://api.example.com/users")
        .query("page", 1).query("size", 20)
        .header("X-Request-Id", "demo")
        .execute();

String text = response.getBodyAsString();
int status = response.getStatus();

HttpUtil.post("https://api.example.com/forms")
        .form(Map.of("name", "alice", "enabled", true))
        .execute();
```

| 能力 | 入口 |
| --- | --- |
| HTTP 方法 | get/head/post/put/patch/delete；其他方法 request(HttpMethod, url) |
| 查询与头 | query(name, value)、header(name, value)、headers(HttpHeaders) |
| 认证 | basicAuth(user, password)、bearerToken(token) |
| JSON | json(Object) |
| 普通表单 | form(name, value)、form(Map<String,Object>) |
| 其他 body | body(Object, MediaType) |
| 响应 | getStatus、getStatusCode、getHeaders、getBody、getBodyAsString、bodyAs |
| 只检查状态 | 请求的 status() 返回状态码，不读取正文；4xx/5xx 也直接返回 |

query 的 null 值会忽略。同一个请求只使用一种 body 类型，不能混合 json 与 form；请求构造器是可变对象，不跨线程共享。

`query(name, value)` 接收**未编码的原始值**，例如 `a+b` 会编码成 `a%2Bb`。不要先手工 URL 编码参数。URL 本身已有的 `%2F`、`%2B`、重复参数顺序和转义大小写会保留，不重复编码或重排签名参数；原始空格和中文会编码。签名 URL 不应再随意追加参数，否则仍可能使签名失效。

<a id="ai-tools-http-上传与下载"></a>
### 上传与下载

```java
import com.yulinlin.data.core.http.HttpFile;
import com.yulinlin.data.core.http.HttpUtil;
import java.nio.file.Path;
import java.time.Duration;

// multipart 普通字段和文件一起上传，不使用 form() 混入。
HttpUtil.post("https://api.example.com/files")
        .multipart("category", "invoice")
        .file("file", HttpFile.of(Path.of("upload/invoice.pdf")))
        .execute();

// 大文件流式落盘。
HttpUtil.get("https://api.example.com/export")
        .timeout(Duration.ofSeconds(60))
        .downloadTo(Path.of("download/export.zip"));
```

HttpFile 支持 of(Path)、of(filename, byte[], MediaType)、of(filename, InputStream, MediaType)。输入流上传使用未知长度，不提前读取流计算大小；服务器需要支持未知长度的上传。输入流不保证可重放，不重复使用已消费的原流。业务提供的流建议使用 try-with-resources，即使请求构造失败也能关闭。

`execute()` 会把响应 body 放入内存，默认最多 16MB；大文件使用 `downloadTo()` 流式落盘，不受内存响应上限限制。

下载会创建父目录，先写同目录临时文件，读取完成且 Content-Length（若提供）符合后才替换目标文件。优先原子替换；文件系统不支持时退回普通移动，不承诺断电原子性。读取失败会清理临时文件并保留旧文件；清理失败作为 suppressed 异常保留。只接受有正文的完整 2xx 响应，拒绝 3xx、4xx/5xx、HEAD、204/205 和 206/Content-Range，避免把跳转页面或分段文件覆盖成完整文件。返回响应 body 为空，内容已写磁盘。业务需控制磁盘空间并校验目标路径，不能直接信任远端路径。

<a id="ai-tools-http-超时与错误处理"></a>
### 超时与错误处理

Spring 注入客户端默认 10 秒，配置如下：

```yaml
yulinlin:
  http:
    timeout: 10s
    max-response-size: 16MB
    max-error-size: 64KB
```

| 配置 | 默认 | 含义 |
| --- | --- | --- |
| timeout | 10s | 默认响应超时；框架自建连接池也用它设置连接超时 |
| max-response-size | 16MB | execute() 响应正文上限，超过抛出 HttpRequestException，不返回残缺数据 |
| max-error-size | 64KB | 失败响应正文保留上限，超过截断；isResponseBodyTruncated() 可判断 |

单次 `timeout(Duration)` 覆盖**响应等待与读取超时**，至少 1ms，不重建连接池、不修改其他并发请求的超时；连接超时保留客户端创建时的设置。它不是覆盖参数序列化、文件写盘和全部业务处理的严格总截止时间。没有独立的链式 connectTimeout/readTimeout 方法；高级连接配置通过自定义底层 HttpClient 提供。

正文大小配置必须大于 0 且小于 2GB；普通响应即使没有 Content-Length 也会限制实际读取量。错误正文只保留前 N 字节，截断后可能不是完整 JSON。状态检查和截断处理会关闭正文流，不为了复用连接继续消费整个大文件，必要时由底层重建该条连接。业务自定义拦截器如果提前缓冲正文，其自身内存使用不在这个限制内。

Spring 单例初始化完成后，`HttpUtil.getClient()` 与注入的默认 HttpRequestClient 是**同一个实例**，自动使用相同的执行器、ObjectMapper 和上述配置，不必手工 setClient。初始化回调（如 `@PostConstruct`）中的业务请直接注入客户端，不依赖静态绑定完成时机。

`HttpUtil.withTimeout(duration)` 和 `client.withTimeout(duration)` 返回轻量视图，只改变默认响应超时，继续使用同一个执行器，不创建连接池，也不绕过自定义实现。关闭视图不关闭共享执行器。

无 Spring 时才延迟创建内置默认客户端，也可使用 `HttpUtil.setClient(client)` 手工替换。Spring 已绑定时不能用 setClient 改成另一个客户端，防止静态调用和注入分离；请注册 HttpExecutor 或 HttpRequestClient Bean。替换手工客户端不自动关闭旧实例。Spring 关闭后静态入口明确报错，不静默回退到另一套执行器；新的 Spring 上下文重新绑定或显式 setClient 后可继续使用。

<a id="ai-tools-http-判断是否为-404"></a>
#### 判断是否为 404

```java
boolean missing = HttpUtil.isNotFound("https://api.example.com/resource");
```

优先 HEAD；仅遇到 405/501（不支持 HEAD）回退 GET，两条路径均不读取正文。只有明确 HTTP 404 返回 true。超时、网络错误和其他状态返回 false，所以 false 不证明存在或健康；非法 URL 等调用参数错误仍抛出异常。少数服务的 HEAD/GET 状态不一致，业务可明确使用 GET 的 status() 检查。

带认证的检查或需要区分其他失败时，使用 status()：

```java
int status = HttpUtil.get("https://api.example.com/resource")
        .bearerToken(token)
        .timeout(Duration.ofSeconds(3))
        .status();
if (status == 404) {
    // 明确不存在；网络失败会抛异常，不伪装成 404。
}
```

`execute()` 的普通 4xx/5xx、网络调用与部分 JSON 解码失败包装为 HttpRequestException；status() 则直接返回 4xx/5xx。参数校验和非法 URI 不保证都包装。204 空响应不当作 JSON 解析。不默认自动重试，不默认跟随重定向，调用仍是同步方式。

<a id="ai-tools-http-自定义客户端与生命周期"></a>
### 自定义客户端与生命周期

默认执行器复用一个 JDK HttpClient 的连接池；每次请求只创建轻量超时适配器，请求体阻塞写入使用虚拟线程。Spring 管理的 HttpExecutor Bean 在销毁时关闭自己创建的连接池，注入它的客户端不重复关闭。手动通过 HttpRequestClient.create/withTimeout(builder, mapper, timeout) 创建的客户端拥有默认执行器，不再使用时调用 close()。

保留简单调用：

```java
// 使用当前选中的执行器（包括 Spring 自定义实现），只改变默认响应超时。
try (HttpRequestClient client = HttpUtil.withTimeout(Duration.ofSeconds(10))) {
    client.get("https://api.example.com/a").timeout(Duration.ofSeconds(2)).execute();
    client.get("https://api.example.com/b").timeout(Duration.ofSeconds(5)).execute();
    // 默认实现中两次请求使用同一个连接池；关闭此视图不会关闭共享执行器。
}
```

需要自定义代理、客户端证书、SSL 或连接超时，可以声明自己的 HttpExecutor Bean，默认执行器自动退让，静态与注入入口一起生效。示例方法放入业务的 `@Configuration` 类：

```java
@Bean(destroyMethod = "close")
public java.net.http.HttpClient remoteTransport() {
    return java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            // .proxy(proxySelector)
            // .sslContext(sslContext)
            .build();
}

@Bean
public HttpExecutor projectHttpExecutor(
        RestClient.Builder builder, ObjectMapper mapper,
        HttpRequestProperties properties,
        @Qualifier("remoteTransport") java.net.http.HttpClient transport) {
    return RestClientHttpExecutor.create(builder, mapper, properties, transport);
}
```

所需导入：`org.springframework.context.annotation.Bean`、`org.springframework.beans.factory.annotation.Qualifier`、`org.springframework.web.client.RestClient`、`com.fasterxml.jackson.databind.ObjectMapper`、`com.yulinlin.data.core.http.HttpRequestProperties`、`com.yulinlin.data.core.http.HttpExecutor`、`com.yulinlin.data.core.http.RestClientHttpExecutor` 和 Duration。外部提供的 transport 由业务/容器管理，执行器不会关闭它。

`RestClientHttpExecutor.create(...)`、`HttpRequestClient.create(...)` 和静态 `HttpRequestClient.withTimeout(builder, mapper, timeout)` 明确创建默认实现并安装框架 JDK 请求工厂，不查找 Spring 的自定义执行器；保留 builder 的消息转换器、默认请求头、拦截器等，但**不沿用另一种 requestFactory 的网络设置**。日常业务使用注入的客户端或 HttpUtil；不要通过这些工厂绕过已选定的执行器。

已配置好的 Apache/其他 RestClient 可使用 `new RestClientHttpExecutor(restClient, mapper)` 注册为 Bean，或保留旧写法 `new HttpRequestClient(restClient, mapper)`（第三个参数可提供正文大小限制）。它们完整保留原工厂，但超时必须在原客户端配置。再使用请求 `.timeout(...)` 或超时视图会明确报错，不会悄悄替换代理或证书。

默认执行器内部使用 exchange() 统一处理状态和大小限制，不执行 RestClient 的 defaultStatusHandler；需要该扩展语义的业务直接使用原 RestClient，或在取得工具响应/异常后自行处理。完全更换底层实现的协议、示例和资源归属见[HTTP 执行器规范与替换](#ai-tools-http-executor)。

用户提供 URL 时校验协议、目的地址与内网访问策略，防止 SSRF；不要无差别记录 token、cookie、密码及错误响应体。

---

下一步：返回[常用工具主题目录](tools/README.md)按需选择相关专题。

---

<a id="ai-tools-http-executor"></a>
<!-- source: doc/tools/http-executor.md -->
<a id="ai-tools-http-executor-http-执行器规范与替换"></a>
## HTTP 执行器规范与替换

[文档首页](README.md) / [常用工具](tools/README.md) / [HTTP 基础用法](#ai-tools-http)

统一调用方式，默认使用现有 RestClient 实现；业务方只替换 HttpExecutor，不重写链式工具类。类型均位于 `com.yulinlin.data.core.http`，第一版继续复用 Spring 的 HttpMethod、HttpHeaders、MediaType，不额外增加一套同义类型。

<a id="ai-tools-http-executor-本页导航"></a>
### 本页导航

- [组件边界](#ai-tools-http-executor-组件边界)
- [实现协议](#ai-tools-http-executor-实现协议)
- [Spring 自动替换](#ai-tools-http-executor-spring-自动替换)
- [请求与响应适配](#ai-tools-http-executor-请求与响应适配)
- [非 Spring 与生命周期](#ai-tools-http-executor-非-spring-与生命周期)
- [维护检查清单](#ai-tools-http-executor-维护检查清单)

<a id="ai-tools-http-executor-组件边界"></a>
### 组件边界

```text
HttpUtil ──────────┐
                  ↓
注入 HttpRequestClient → HttpRequest → HttpRequestData → HttpExecutor
                                                        ├─ 默认 RestClientHttpExecutor
                                                        └─ 用户实现
```

| 组件 | 职责 |
| --- | --- |
| HttpRequestClient | 合并默认配置、创建请求、统一 404 策略及响应 ObjectMapper；不直接访问网络 |
| HttpRequest | 组装 query、header、JSON、form、multipart、timeout；不跨线程共享 |
| HttpRequestData | 只读请求快照；不依赖 RestClient，也不暴露其请求对象 |
| HttpExecutor | 真正执行网络请求，负责连接复用、流、超时、大小限制和下载安全 |
| RestClientHttpExecutor | 默认适配器，承接已有优化；支持直接包装外部 RestClient |
| HttpAutoConfiguration / HttpClientBinding | 默认 Bean 退让、选择实现、绑定和解除静态入口 |

暂不增加执行器路由、插件注册中心、自动重试或多套 HTTP 客户端配置组。单个应用选择一个默认执行器即可。

<a id="ai-tools-http-executor-实现协议"></a>
### 实现协议

```java
public interface HttpExecutor extends AutoCloseable {
    HttpResponse execute(HttpRequestData request);
    int status(HttpRequestData request);
    HttpResponse download(HttpRequestData request, Path destination);
    default void close() { }
}
```

| 方法/行为 | 要求 |
| --- | --- |
| execute | 只在正文上限内读入内存；4xx/5xx 抛 HttpRequestException，错误正文按错误上限截断 |
| status | 返回状态码，包括 4xx/5xx；不读取完整正文；网络失败抛异常，不当作 404 |
| download | 流式写同目录临时文件，完整成功才替换目标；失败清理临时文件；返回空响应正文 |
| 超时 | 遵守请求快照中的响应超时，不能影响其他并发请求；不支持时明确报错，不静默忽略 |
| 资源 | 单例线程安全、复用连接池；关闭响应流，不为了复用连接强制读完大错误正文 |
| 重试/跳转 | 默认不自动重试、不自动跟随重定向；不要重复提交 POST 或一次性文件流 |
| close | 只释放实现自己拥有的资源；允许重复关闭，不关闭外部共享客户端 |

`download` 必须拒绝 HEAD、无正文状态、3xx/4xx/5xx、206 或 Content-Range 等非完整文件响应；提供 Content-Length 时校验读取量。优先原子替换，不支持时可退回完整下载后的普通移动，不承诺断电安全。不要把 download/status 默认实现成 execute，否则会把大正文读入内存。

`HttpRequestClient` 会对普通请求返回的错误状态、超大正文做防御性检查，但这不能替代执行器在读取流时限制内存，也不能撤销一个错误执行器已经覆盖的文件。第三方实现需要遵守协议。

404 判断始终在 HttpRequestClient：先 HEAD，405/501 才回退 GET；不要求每个执行器各写一套 isNotFound。具体默认超时和大小配置见[HTTP 基础用法](#ai-tools-http-超时与错误处理)。

<a id="ai-tools-http-executor-spring-自动替换"></a>
### Spring 自动替换

业务提供一个 Bean 即可，Bean 名字不作要求：

```java
@Bean
public HttpExecutor projectHttpExecutor() {
    return new MyHttpExecutor(); // 业务实现三个执行方法，遵守上面的协议
}
```

没有自定义 Bean 时自动注册 RestClientHttpExecutor；有自定义 HttpExecutor 时默认实现不创建。自动创建的 HttpRequestClient 注入这个执行器。存在多个实现时必须指定 `@Primary`，否则启动失败并提示原因。若业务直接提供 HttpRequestClient Bean，则以该客户端为准；多个客户端同样需要 @Primary。

Spring 单例初始化完成后，两种入口保持一致：

```java
@Service
public class RemoteService {
    private final HttpRequestClient client;
    public RemoteService(HttpRequestClient client) { this.client = client; }

    public void invoke(String url) {
        client.get(url).timeout(Duration.ofSeconds(3)).execute();
        HttpUtil.get(url).timeout(Duration.ofSeconds(3)).execute();
        // client == HttpUtil.getClient()，两次走同一个执行器。
        HttpUtil.withTimeout(Duration.ofSeconds(5)).get(url).execute();
        // 仍走同一个执行器，只改变该视图的默认响应超时。
    }
}
```

`@Bean`、`@Service` 分别导入 Spring 的对应注解，Duration 导入 java.time.Duration。业务初始化回调中直接注入客户端，不依赖静态入口的绑定时机。Spring 激活绑定后，setClient 不允许换成另一个实例；应用关闭后静态调用报错，不静默切回默认实现。

如果只是统一请求头、代理或 SSL，无须重新实现协议：

```java
@Bean
public HttpExecutor projectHttpExecutor(
        RestClient.Builder builder, ObjectMapper mapper, HttpRequestProperties properties) {
    return RestClientHttpExecutor.create(
            builder.clone().defaultHeader("X-Client", "video-service"), mapper, properties);
}
```

所需导入：`org.springframework.web.client.RestClient`、`com.fasterxml.jackson.databind.ObjectMapper` 和本包的 HttpExecutor、RestClientHttpExecutor、HttpRequestProperties。传入自定义 JDK HttpClient 的完整 SSL/代理配置案例见[自定义客户端与生命周期](#ai-tools-http-自定义客户端与生命周期)。

真正更换网络库时实现 HttpExecutor 三个方法即可；无需继承默认实现。需要委托默认实现增强时，可以在自己的执行器中持有 RestClientHttpExecutor，并将 execute/status/download 委托给它；自建的委托实例也要在 close 中关闭。

<a id="ai-tools-http-executor-请求与响应适配"></a>
### 请求与响应适配

HttpRequestData 通过 getter 读取：

| 信息 | API / 说明 |
| --- | --- |
| 方法和 URL | getMethod()、getUri()；URL 已编码，不再转义或重排 query，可能使用客户端的相对地址 |
| 请求头 | getHeaders()；深复制列表并只读，不允许直接修改快照 |
| 正文 | getBodyKind()：NONE / JSON / FORM / MULTIPART / RAW；getBody()、getContentType() |
| 普通表单 | getForm()，保留同名多值 |
| multipart | getMultipart()，值为 String 或 HttpFile，保留同名多个文件 |
| 超时 | getTimeout() 是有效 Duration；isTimeoutOverridden() 表示用户显式覆盖（含超时视图） |
| 大小上限 | getMaxResponseSize()、getMaxErrorSize()，单位均为字节 |

`HttpRequest.toRequestData()` 只生成快照，不执行请求。快照复制头、表单与 multipart 容器；正文对象、字节数组、文件内容和输入流不默认深度克隆。提交期间不要并发修改正文；输入流文件只能消费一次。

HttpFile 提供公开适配入口：

```java
String filename = file.getFilename();
MediaType type = file.getContentType(); // 可以为 null
long length = file.getContentLength(); // 未知为 -1，不会提前消费输入流
boolean repeatable = file.isRepeatable();
try (InputStream input = file.openStream()) {
    // 交给底层网络库发送；不要为计算长度先读完。
}
// Spring 适配器也可直接使用 file.getResource()。
```

自定义执行器创建响应和异常：

```java
// status、headers、body 来自底层网络库；body 必须已按上限读取。
return HttpResponse.of(status, headers, body);
```

```java
throw new HttpRequestException(
        request.getMethod().name(), request.getUri().toString(),
        status, errorBody, truncated, cause);
// 网络失败的 status 可为 null；不要把网络错误伪装成 HTTP 404。
```

`HttpResponse.of(...)` 防御性复制外部正文和头；客户端返回响应前关联自己的 ObjectMapper，因此 `bodyAs(Class)` / `bodyAs(TypeReference)` 在静态、注入和自定义执行器路径上使用一致的应用配置。必要时直接调用执行器也可用 `of(status, headers, body, mapper)` 指定 mapper。

直接包装外部 RestClient 时保留其网络配置；默认 timeout 不强制覆盖它，显式 timeout/超时视图会报错，因为框架不能安全修改第三方工厂。其他自定义执行器也不得静默忽略调用方显式提出但不支持的能力。

<a id="ai-tools-http-executor-非-spring-与生命周期"></a>
### 非 Spring 与生命周期

```java
HttpExecutor executor = new MyHttpExecutor();
HttpRequestClient client = new HttpRequestClient(executor);
HttpUtil.setClient(client);
// 所有静态请求走 executor；用完后由其所有者关闭 executor。
```

需要 JSON 定制及全局默认值时使用 `new HttpRequestClient(executor, objectMapper, properties)`。

| 创建方式 | 谁关闭执行器 |
| --- | --- |
| HttpExecutor Bean + 自动 HttpRequestClient | Spring 关闭执行器 Bean 一次；客户端不重复关闭 |
| new HttpRequestClient(executor, ...) | 外部提供者负责；client.close() 不关闭共享执行器 |
| HttpRequestClient.create / 静态 withTimeout(builder, mapper, duration) | 创建的是默认执行器，client.close() 关闭它；这类显式工厂不查找 Spring Bean |
| client.withTimeout(duration) / HttpUtil.withTimeout(duration) | 轻量视图不拥有执行器，close 不影响原客户端 |
| 外部传入的 JDK HttpClient 或 RestClient | 外部所有者负责，默认执行器不会关闭它 |

静态入口适用于单个主应用上下文。第二个上下文不能抢占已有绑定，会明确报错；多上下文隔离应用应在额外上下文排除 HttpAutoConfiguration，自行注册并注入 HttpRequestClient，仅主上下文管理静态入口。关闭一个未取得绑定的上下文不会清除主上下文的绑定。

<a id="ai-tools-http-executor-维护检查清单"></a>
### 维护检查清单

- 新增底层实现只实现 HttpExecutor；不要让 HttpRequest 再依赖 RestClient 或 SDK 对象。
- 不在每次请求中创建连接池，也不按无限多超时值缓存客户端。
- 测试普通请求、错误截断、未知长度上传、状态检查、下载失败保护、并发超时和 close 所有权。
- 测试自定义 Bean 退让、@Primary、静态/注入一致、关闭后解绑；初始化阶段优先注入。
- 测试源码放在 admin/src/test。新增测试不代表已执行；遵守用户关于不编译、不运行测试和不运行 YAML 属性绑定测试的要求。

---

下一步：[HTTP 基础用法](#ai-tools-http) / [工具目录](tools/README.md)。

---

<a id="ai-tools-reflection"></a>
<!-- source: doc/tools/reflection.md -->
<a id="ai-tools-reflection-反射复制与深克隆"></a>
## 反射复制与深克隆

[文档首页](README.md) / [常用工具](tools/README.md)

属性复制、嵌套对象和深度克隆的行为边界。

<a id="ai-tools-reflection-本页导航"></a>
### 本页导航

- [反射复制与深克隆](#ai-tools-reflection-反射复制与深克隆-1)

<a id="ai-tools-reflection-反射复制与深克隆-1"></a>
### 反射复制与深克隆

以下完整普通 Bean 不依赖 ORM；作为本节和 JSON 示例模型：

```java
package demo.tools;

import java.util.ArrayList;
import java.util.List;

public class ToolUser {
    private String name;
    private List<String> tags = new ArrayList<>();
    public ToolUser() { }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
```

业务方法体片段：

```java
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import demo.tools.ToolUser;

ToolUser user = ReflectionUtil.newInstance(ToolUser.class);
ReflectionUtil.invokeSetter(user, "name", "alice");
Object name = ReflectionUtil.invokeGetter(user, "name");

ReflectionUtil.PropertyAccess access = ReflectionUtil.property(ToolUser.class, "name");
access.set(user, "bob");
Object value = access.get(user);

ToolUser deep = ReflectionUtil.deepClone(user);
ToolUser target = new ToolUser();
ReflectionUtil.copyProperties(user, target);
```

| API | 语义 |
| --- | --- |
| newInstance(Class) | 可访问无参构造 |
| invokeGetter/invokeSetter | 名称和嵌套路径；不推定中间对象自动创建 |
| invokeMethod(bean, name, args...) | 根据名称和参数找方法；歧义报错，变长参数传声明数组 |
| property(Class, name) | 单一属性访问器，可缓存；不是嵌套路径解析器 |
| clone(value) | 浅复制 |
| deepClone(value) / clone(value, true) | 对象图深复制，null 返回 null |
| copyProperties(source, target) | 同名属性深复制到已有目标；跳过缺少属性及源 null |

深克隆覆盖支持的 Bean、集合、Map 键值、数组和可变值，保留对象图的循环与共享引用。每次调用使用独立身份跟踪上下文，不把 DeepCopies 改成跨请求共享单例。

整体 deepClone(list) 保留跨元素共享关系；逐条 deepClone 不保留跨调用共享关系。这两种方式不能仅凭速度互换。

使用限制：

- Bean 需要无参构造和可写属性；record、不可写 final 字段、线程/流/连接不作为通用复制目标。
- 不自动把嵌套实体转换为另一种 DTO，也不自动转换集合泛型。
- 不可变值可以复用；包装集合可能变成普通可变集合，不保证保留不可修改/同步包装语义。
- copyProperties 失败可能已部分修改目标，没有回滚；null 跳过不等于 SQL NULL 更新。
- 私有成员访问仍受 Java 模块 opens 限制，不绕过任意访问控制。
- 当前基于 MethodHandle，ReflectAsmUtil 是弃用兼容入口；Kryo 不是 lang 的克隆后端。
- 性能实测必须看相同模型、粒度和环境；历史 JMH 数据在 [内部维护](operations/clone-benchmark.md#历史克隆基准)，不作当前通用排名。

---

下一步：返回[常用工具主题目录](tools/README.md)按需选择相关专题。

---

<a id="ai-tools-json"></a>
<!-- source: doc/tools/json.md -->
<a id="ai-tools-json-json-转换"></a>
## JSON 转换

[文档首页](README.md) / [常用工具](tools/README.md)

常用 JSON 转换方式与泛型类型。

<a id="ai-tools-json-本页导航"></a>
### 本页导航

- [JSON 转换](#ai-tools-json-json-转换-1)

<a id="ai-tools-json-json-转换-1"></a>
### JSON 转换

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.yulinlin.data.lang.json.JsonUtil;
import demo.tools.ToolUser;
import java.util.List;

String json = JsonUtil.toJson(new ToolUser());
ToolUser user = JsonUtil.parseJson(json, ToolUser.class);
List<ToolUser> users = JsonUtil.parseJson("[]", new TypeReference<List<ToolUser>>() {});
```

JsonUtil.to(source, Target.class) 经 JSON 做类型转换，受 Jackson 注解/配置影响，不是对象图克隆，不保持任意循环与对象身份。

`JsonUtil` 独立模式有自有 mapper；starter 会用 Spring `ObjectMapper` 替换它。`setMapper` 是进程级全局设置，应在启动阶段完成，不能在并发请求中来回切换。`HttpUtil` 静态客户端的 mapper 是另一套，不自动与 `JsonUtil` 同步。

`YulinlinSimpModule` 自动支持以下值：

| Java 类型 | JSON 形式 |
| --- | --- |
| DateTime | `yyyy-MM-dd HH:mm:ss` 字符串 |
| Money、Coin | JSON 数字 |
| LongAdder、DoubleAdder | 当前累计值 |

手动创建 Jackson mapper 时这样注册：

```java
ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new YulinlinSimpModule());
JsonUtil.setMapper(mapper);
```

`DateTimeStrJsonSerializer` 输出“刚刚、N 分钟前”等相对文本；`EnumSerializer` 输出实现 `IEnum` 的 label。它们没有加入 `YulinlinSimpModule`，必须通过 Jackson 注解或模块显式注册。`EnumFormat` 也是扩展构件，不会自动接管全部枚举。

`JsonUtil.clone` 是保留的 JSON 往返接口，只适合 Jackson 可序列化模型；异构集合、循环引用和对象身份不可靠。通用深克隆优先使用 `ReflectionUtil.deepClone`。

---

下一步：返回[常用工具主题目录](tools/README.md)按需选择相关专题。

---

<a id="ai-tools-value"></a>
<!-- source: doc/tools/value.md -->
<a id="ai-tools-value-日期数字与字符串工具"></a>
## 日期、数字与字符串工具

[文档首页](README.md) / [常用工具](tools/README.md)

日期、金额、分页、字符串、随机数和 ID。

<a id="ai-tools-value-本页导航"></a>
### 本页导航

- [日期、金额与分页](#ai-tools-value-日期金额与分页)
- [DateTime](#ai-tools-value-datetime)
- [Money、Coin 与 CodeObject](#ai-tools-value-moneycoin-与-codeobject)
- [Page](#ai-tools-value-page)
- [ListString](#ai-tools-value-liststring)
- [字符串、随机数与 ID](#ai-tools-value-字符串随机数与-id)
- [WeightedSelector](#ai-tools-value-weightedselector)

<a id="ai-tools-value-日期金额与分页"></a>
### 日期、金额与分页

<a id="ai-tools-value-datetime"></a>
#### DateTime

`DateTime` 是基于 `LocalDateTime` 的不可变包装：`plus*`、`with*`、`beginOf*`、`endOf*` 都返回新对象，不修改原值。

```java
DateTime now = DateTime.now();
DateTime tomorrow = now.plusDays(1);
DateTime dayStart = now.beginOfDay();
DateTime dayEnd = now.endOfDay();

DateTime a = DateTime.parse("2026-10-10 12:30:00");
DateTime b = DateTime.parse("20261010123000");
DateTime c = DateTime.parse("2026/10/10", "yyyy/MM/dd");
long millis = a.getTime();
Date legacy = a.toDate();
```

默认解析支持 `yyyy-MM-dd HH:mm:ss`、`yyyyMMddHHmmss`、日期/月/年简写，以及 10 位秒时间戳和 13 位毫秒时间戳。时间戳和 `Date` 转换使用 JVM 默认时区；跨时区协议优先直接使用 `Instant`、`OffsetDateTime`。`isDate` 只是快速形态判断，不是严格日期校验。

<a id="ai-tools-value-moneycoin-与-codeobject"></a>
#### Money、Coin 与 CodeObject

```java
Money price = Money.of(12.34);
long stored = price.encode();       // 1234
Money decoded = new Money();
decoded.decode(stored);             // 12.34

Coin coin = Coin.of(8.5);
long coinStored = coin.encode();    // 85
```

`Money` 固定两位缩放，`Coin` 固定一位缩放；两者都继承 `Currency<Double>` 并实现 `CodeObject<Long>`。由于内部是 `Double`，不适合要求严格十进制精度、超大金额或复杂舍入的财务场景，这些场景使用 `BigDecimal`。

`CodeObject<T>` 是数据库值编解码约定：`encode` 输出持久化值，`decode` 把持久化值写回当前对象。

<a id="ai-tools-value-page"></a>
#### Page

```java
Page<User> page = Page.of(users, total);
Page<UserDto> dtoPage = page.map(this::toDto);
Page<User> active = page.filter(User::isActive);
List<User> second = Page.page(users, 2, 20);
page.putValue("traceId", traceId);
```

`Page.page` 是从 1 开始的内存分页，不发 SQL，要求 `pageNumber >= 1`、`pageSize > 0`。`map` 遇到 null 会从返回 Page 的 total 中扣除；`filter` 保留原 total，适合“当前页后处理”，不表示重新统计数据库总数。`shuffle`、`random` 返回新 Page，不修改原 list；`randomOne` 不接受空列表。

<a id="ai-tools-value-liststring"></a>
#### ListString

```java
ListString<Long> ids = new ListString<>();
ids.decode("10,20,30", Long.class);
String value = ids.encode();

ListString<String> tags = ListString.ofString("java,jdk25");
```

默认分隔符是进程级静态字段 `ListString.splitStr`。支持 String、Integer、Long、Double、Byte、Float；内容本身包含分隔符时不能无损往返，也不处理转义。业务优先保持默认逗号，避免运行期间修改全局分隔符。

<a id="ai-tools-value-字符串随机数与-id"></a>
### 字符串、随机数与 ID

```java
String field = StringUtil.columnToJava("user_name"); // userName
String column = StringUtil.javaToColumn("userName"); // user_name
String text = StringUtil.removeLine(" a\n b\t");     // ab
String rendered = StringUtil.renderString("hi ${name}", Map.of("name", "Lin"));

int value = RandomUtil.randomInt(10); // [0, 10)
char tokenChar = RandomUtil.randomChar();

Snowflake snowflake = new Snowflake(1, 2);
long id = snowflake.nextId();
```

- `StringUtil.isNull` 只判断 null 或空串，不等价于 `isBlank`；`removeLine` 会移除 ASCII 空白。
- `renderString` 是简单 `${key}` 替换，不是表达式引擎；值不能为 null。
- `RandomUtil` 基于 `ThreadLocalRandom`，适合抽样和非安全随机，不能生成密码、token、IV 或密钥。
- `Snowflake` 的 workerId 和 dataCenterId 均为 0～31；同一时间范围内的不同进程必须使用不同组合。`nextId` 是同步线程安全的，时钟大幅回拨会拒绝生成。

<a id="ai-tools-value-weightedselector"></a>
#### WeightedSelector

```java
WeightedSelector<String> selector = new WeightedSelector<>();
selector.add("A", 1);
selector.add("B", 3);
String selected = selector.select();
```

权重小于等于 0 会忽略；没有正权重项时 `select` 抛异常。添加操作同步并发布到并发有序表，热点选择路径不使用共享 `Random`，支持并发读取；`list` 返回不可修改视图。

---

下一步：返回[常用工具主题目录](tools/README.md)按需选择相关专题。

---

<a id="ai-tools-local-cache"></a>
<!-- source: doc/tools/local-cache.md -->
<a id="ai-tools-local-cache-轻量缓存与资源池"></a>
## 轻量缓存与资源池

[文档首页](README.md) / [常用工具](tools/README.md)

ExpiryMap、SmartCache 与 ResourcePool，不等同于 ORM 缓存。

<a id="ai-tools-local-cache-本页导航"></a>
### 本页导航

- [轻量缓存与资源池](#ai-tools-local-cache-轻量缓存与资源池-1)
- [ExpiryMap](#ai-tools-local-cache-expirymap)
- [SmartCache](#ai-tools-local-cache-smartcache)
- [ResourcePool](#ai-tools-local-cache-resourcepool)

<a id="ai-tools-local-cache-轻量缓存与资源池-1"></a>
### 轻量缓存与资源池

这些类不替代 [ORM 查询缓存](#ai-cache-query)。它们只管理当前 JVM 内的普通对象。

<a id="ai-tools-local-cache-expirymap"></a>
#### ExpiryMap

```java
try {
    ExpiryMap<String, User> cache = new ExpiryMap<>(
            30, TimeUnit.SECONDS, 20_000, 5,
            expired -> expired.forEach(this::afterRemoval));

    User user = cache.get("u:1", key -> loadUser(key));
    cache.invalidate("u:1");
    cache.shutdown();
} catch (RuntimeException error) {
    // loader 异常原样传播
}
```

TTL 是写入后过期；`randomTtl` 单位为秒，会在基础 TTL 上随机增加 0～N 秒。`get(key, loader)` 对同一 key 使用 `ConcurrentHashMap.compute`，避免同一时刻重复加载。容量是轻量近似上限，不提供 Caffeine 的精确淘汰算法。清理任务使用全局守护调度器；每个实例使用完都调用 `shutdown`，移除回调在框架线程池异步执行。key、value、loader 返回值均不要使用 null。

<a id="ai-tools-local-cache-smartcache"></a>
#### SmartCache

`SmartCache` 是轻量级 stale-while-revalidate 缓存。首次访问同步加载；TTL 内直接返回缓存；TTL 到期后的第一个请求只提交一次虚拟线程刷新，并立即返回旧值，其他并发请求不会重复刷新。

```java
try (SmartCache<List<User>> cache = SmartCache.of(this::loadUsers, 60)) {
    List<User> users = cache.get();
    cache.refresh();       // 强制同步刷新
    cache.refreshAsync();  // 合并并发刷新请求
}
```

默认 `autoRefresh=false`，没有访问就不会刷新，适合“缓存 60 秒，过期后的下一次访问刷新一次”的业务。需要即使无人访问也定时更新时，显式使用：

```java
SmartCache<List<User>> cache = SmartCache.of(this::loadUsers, 60, true);
```

`autoRefresh=true` 按 TTL 周期在虚拟线程刷新。实例不再使用时应调用 `close()`；关闭后的实例不能继续 `get/refresh`。刷新成功后重新计算完整 TTL；刷新失败会记录日志并保留旧值；supplier 返回 `null` 也不会覆盖旧值，两种情况都会在最多 5 秒后允许访问触发重试，避免连续请求形成刷新风暴。

带 key 的工厂用于进程内共享：

```java
SmartCache<User> cache = SmartCache.of("user:" + userId, () -> loadUser(userId), 60);
```

`SmartCache.of(key, ...)` 会复用同一实例，同 key 第一次创建时的值类型、supplier、TTL 和 `autoRefresh` 配置生效，因此不要让不同业务或不同类型共用一个 key。带 key 实例调用 `close()` 后会从全局注册表移除，再次调用 `of(key, ...)` 会创建新实例；`clearAllCache()` 会原子移除并关闭当前全部带 key 实例。`clear()` 与后台刷新互斥，清空后下一次 `get()` 会同步加载。

<a id="ai-tools-local-cache-resourcepool"></a>
#### ResourcePool

```java
ResourcePool<Client> pool = new ResourcePool<>(8, Client::new);
Client client = pool.acquire();
try {
    client.call();
} finally {
    pool.release(client); // 每次 acquire 必须且只能 release 一次
}
pool.close(); // 关闭当时空闲的资源
```

继承 `ResourcePool` 并覆盖 `protected close(T)` 可定义资源关闭动作。工厂异常不会泄漏许可证。该类不跟踪借出对象，重复 release 会破坏容量约束；`close` 也不会等待正在使用的对象，复杂连接池优先使用成熟池实现。

---

下一步：返回[常用工具主题目录](tools/README.md)按需选择相关专题。

---

<a id="ai-tools-concurrency"></a>
<!-- source: doc/tools/concurrency.md -->
<a id="ai-tools-concurrency-线程调度与事件"></a>
## 线程、调度与事件

[文档首页](README.md) / [常用工具](tools/README.md)

锁、虚拟线程、延迟调度和进程内事件。

<a id="ai-tools-concurrency-本页导航"></a>
### 本页导航

- [并发与调度](#ai-tools-concurrency-并发与调度)
- [SegmentLock](#ai-tools-concurrency-segmentlock)
- [ThreadUtil](#ai-tools-concurrency-threadutil)
- [进程内事件](#ai-tools-concurrency-进程内事件)

<a id="ai-tools-concurrency-并发与调度"></a>
### 并发与调度

<a id="ai-tools-concurrency-segmentlock"></a>
#### SegmentLock

```java
SegmentLock locks = new SegmentLock(64);
locks.execute(userId, () -> updateUser(userId));
Order order = locks.executeWithReturn(orderId, () -> loadOrder(orderId));
```

相同 key 一定进入同一段，不同 key 可能碰撞到同一锁。段数创建后固定；构造参数必须大于 0。它适合短临界区，不提供跨进程锁。

<a id="ai-tools-concurrency-threadutil"></a>
#### ThreadUtil

```java
Future<Result> ioFuture = ThreadUtil.submit(this::blockingHttpOrJdbcCall);
Future<Result> explicitIo = ThreadUtil.submitVirtual(this::blockingHttpOrJdbcCall);
Future<Result> cpuFuture = ThreadUtil.submitCpu(this::calculate);
ThreadUtil.submit(this::refresh, 500);       // 延迟约 500 ms 执行一次
ScheduledFuture<?> task = ThreadUtil.schedule(this::blockingPoll, 1_000);
ScheduledFuture<?> cpuTask = ThreadUtil.scheduleCpu(this::calculate, 1_000);

ThreadUtil.set("trace", traceId);
try {
    useTrace(ThreadUtil.get("trace"));
} finally {
    ThreadUtil.clear();
}
```

`ThreadUtil.submit` 默认使用 JDK 25 一任务一虚拟线程；`submitVirtual` 是语义更明确的同义入口，适合 HTTP、文件、网络、等待锁等阻塞任务。CPU 密集计算或必须由线程池限制并发的任务使用 `submitCpu`，其平台线程数按可用处理器数量固定。虚拟线程不会让单次计算更快，也不会自动限制数据库连接或远端接口并发，因此 ORM 批量写入仍使用 Session 的连接并发配置。

`schedule` 与 `scheduleVirtual` 都由轻量调度器触发、在虚拟线程执行，并在上一次尚未完成时跳过重叠执行；CPU 定时任务使用 `scheduleCpu`。`submitVirtual(task, delay)` 是一次性延迟虚拟任务。`startThread` 使用虚拟线程循环执行，回调返回 `false`、线程被中断时停止。ThreadLocal 数据必须在 finally 中 clear。Spring 应用有复杂隔离、监控或优雅停机要求时仍可注入自己的 `TaskExecutor`/`TaskScheduler`。

<a id="ai-tools-concurrency-进程内事件"></a>
### 进程内事件

```java
final class UserCreated { }

final class UserCreatedHandler implements IEventHandler<UserCreated> {
    public void handle(UserCreated event) { /* ... */ }
}

EventPublishManager.instance().register(new UserCreatedHandler());
EventPublishManager.instance().publish(new UserCreated());
EventPublishManager.instance().asyncPublish(new UserCreated());
```

`IEvent` 提供 `publish`/`asyncPublish` 默认方法。处理器按事件的精确运行时 Class 分组，不向父类或接口传播；重复 register 会重复执行。注册表支持并发读写，适合启动期间注册、运行期间高频发布。同步发布的异常直接中断后续处理器；异步发布通过 `ThreadUtil` 使用虚拟线程，不返回 Future，调用方无法等待，也不会自动汇总错误。需要可靠投递、持久化、重试或跨进程通信时使用消息队列。

自定义隔离实例可以继承 `AbstractEventPublishManager<MyManager>`；全局默认实例是 `EventPublishManager.instance()`。

---

下一步：返回[常用工具主题目录](tools/README.md)按需选择相关专题。

---

<a id="ai-tools-types"></a>
<!-- source: doc/tools/types.md -->
<a id="ai-tools-types-lambda泛型与包扫描"></a>
## Lambda、泛型与包扫描

[文档首页](README.md) / [常用工具](tools/README.md)

方法引用、泛型解析、注解和包扫描。

<a id="ai-tools-types-本页导航"></a>
### 本页导航

- [Lambda、泛型与包扫描](#ai-tools-types-lambda泛型与包扫描-1)
- [LambdaUtils](#ai-tools-types-lambdautils)
- [GenericUtil](#ai-tools-types-genericutil)
- [AnnotationUtil 与 ScanUtils](#ai-tools-types-annotationutil-与-scanutils)

<a id="ai-tools-types-lambda泛型与包扫描-1"></a>
### Lambda、泛型与包扫描

<a id="ai-tools-types-lambdautils"></a>
#### LambdaUtils

```java
LambdaPropertyFunction<User> property = User::getUsername;
String method = LambdaUtils.lambdaMethodName(property);          // getUsername
Field field = LambdaUtils.lambdaMethodNameToField(property);     // username
Class<User> owner = LambdaUtils.lambdaMethodNameToClassName(property);
```

参数必须是可序列化 Lambda，通常使用 `LambdaPropertyFunction<T>`。不要传普通 `Function`、匿名内部类或任意对象。实现只缓存 lambda class 的 `writeReplace` 访问器，不缓存捕获参数，因此不会把首个捕获对象长期保留。

<a id="ai-tools-types-genericutil"></a>
#### GenericUtil

```java
Class<?> entityType = GenericUtil.forClass(UserRepositoryImpl.class, BaseRepository.class, 0);
Class<?> elementType = GenericUtil.forField(User.class.getDeclaredField("roles"), 0);
Class<?> returnType = GenericUtil.forMethodReturnType(method, 0);
Class<?> parameterType = GenericUtil.forMethodParameter(method, 0, 0);
```

解析不到具体类型时返回 null。`GenericUtil` 只在首次调用时进入 Spring `ResolvableType`，之后按源/声明类使用 `ClassValue` 缓存类、字段、方法参数和返回值的最终类型；解析失败的 null 也会缓存。类卸载时其缓存可一起回收，测试或动态类替换可调用 `clearCache(Class<?>)` 或 `clearCache()`。

`BaseGeneric`、`GenericClass`、`GenericField`、`GenericMethod` 是旧的链式泛型模型，仍为兼容保留；新外部代码优先用 `GenericUtil`。`GenericClass.newInstance` 同样使用 `ClassValue` 缓存，不阻止类加载器卸载。

<a id="ai-tools-types-annotationutil-与-scanutils"></a>
#### AnnotationUtil 与 ScanUtils

```java
JoinTable table = AnnotationUtil.findAnnotation(User.class, JoinTable.class);
Marker marker = AnnotationUtil.findAnnotation(method, Marker.class, 0); // 第 0 个参数上的注解

Set<Class<?>> services = ScanUtils.scanSubclass(
        "com.example.**.service", BaseService.class, true);
```

`AnnotationUtil` 使用 Spring 合成注解规则；AspectJ 重载用于切面内部。`ScanUtils.scanClass(path, false)` 返回具体类，传 true 返回接口或抽象类；扫描会读取 class 元数据并加载匹配 Class，但不会初始化静态代码。通配路径依赖 Spring resource pattern，库/应用边界上优先传明确根包，避免扫描整个 classpath。

---

下一步：返回[常用工具主题目录](tools/README.md)按需选择相关专题。

---

<a id="ai-tools-serialization"></a>
<!-- source: doc/tools/serialization.md -->
<a id="ai-tools-serialization-序列化元组与扩展-spi"></a>
## 序列化、元组与扩展 SPI

[文档首页](README.md) / [常用工具](tools/README.md)

lang 序列化和框架扩展工具；缓存序列化见缓存专题。

<a id="ai-tools-serialization-本页导航"></a>
### 本页导航

- [序列化、元组与框架扩展](#ai-tools-serialization-序列化元组与框架扩展)
- [Java 序列化](#ai-tools-serialization-java-序列化)
- [元组](#ai-tools-serialization-元组)
- [框架扩展 SPI](#ai-tools-serialization-框架扩展-spi)

<a id="ai-tools-serialization-序列化元组与框架扩展"></a>
### 序列化、元组与框架扩展

<a id="ai-tools-serialization-java-序列化"></a>
#### Java 序列化

```java
byte[] bytes = SerializableUtil.serialize(value);
Object restored = SerializableUtil.unSerialize(bytes);
SerializableType copy = SerializableUtil.clone(value);
```

整个对象图必须实现 `Serializable`。Java 原生序列化通常比专用编码慢、体积大，且反序列化不可信字节存在安全风险；只处理本进程生成且可信的数据，不作为 HTTP、消息队列或跨语言协议。普通克隆使用 `ReflectionUtil.deepClone`。

<a id="ai-tools-serialization-元组"></a>
#### 元组

```java
MutablePair<String, Integer> pair = MutablePair.of("count", 3);
String left = pair.getLeft();

MutableTriple<String, Integer, Boolean> triple = MutableTriple.of("task", 1, true);
```

这些类是轻量位置容器，没有值语义的 equals/hashCode，也没有公开修改器。跨层 API、缓存 key 或领域模型优先定义 record/明确 DTO。

<a id="ai-tools-serialization-框架扩展-spi"></a>
#### 框架扩展 SPI

| 类型 | 用途 | 外部使用建议 |
| --- | --- | --- |
| BaseAnnotationBeanPostProcessor | 基于类/方法注解创建 Spring Advisor | 仅扩展框架 AOP；实现 getAnnotation 和 invoke |
| MethodHandlesUtil | privateLookupIn 和父接口 default/special 方法句柄 | 代理框架内部使用；受 JPMS opens 限制 |
| ProxyUtil | Spring CGLIB 子类代理 | JDK 25 优先接口/JDK Proxy；final 类和模块边界受限 |
| ReflectAsmUtil | 旧反射入口 | 已弃用，转用 ReflectionUtil；clear 是兼容空操作 |
| DateTimeJsonFormat、MoneyJsonFormat、CoinJsonFormat | Jackson serializer/deserializer 工厂 | 通常直接注册 YulinlinSimpModule |
| LongAdderJsonFormat、DoubleAdderJsonFormat | Adder JSON 数值格式 | 通常直接注册 YulinlinSimpModule |
| DateTimeStrJsonSerializer、EnumSerializer、EnumFormat | 可选展示/枚举格式 | 必须显式配置，不会由默认模块自动启用 |
| IEnum | label/value 枚举约定 | ORM EnumCoder 会优先持久化 value |
| CryptoDataResponse | 响应 data 可被安全模块替换的契约 | starter 的 R 已实现；普通项目按需实现 |

---

下一步：返回[常用工具主题目录](tools/README.md)按需选择相关专题。

---

<a id="ai-practice-conventions"></a>
<!-- source: doc/practice/conventions.md -->
<a id="ai-practice-conventions-项目开发规范"></a>
## 项目开发规范

[文档首页](README.md) / [项目实践](practice/README.md)

分层、事务、缓存、日志、批量和上线前检查。

<a id="ai-practice-conventions-本页导航"></a>
### 本页导航

- [核心规则](#ai-practice-conventions-核心规则)
- [推荐目录结构](#ai-practice-conventions-推荐目录结构)
- [事务与自动更新](#ai-practice-conventions-事务与自动更新)
- [推荐写法](#ai-practice-conventions-推荐写法)
- [缓存规范](#ai-practice-conventions-缓存规范)
- [日志规范](#ai-practice-conventions-日志规范)
- [多数据源规范](#ai-practice-conventions-多数据源规范)
- [批量写入规范](#ai-practice-conventions-批量写入规范)
- [Schema 管理规范](#ai-practice-conventions-schema-管理规范)
- [常见反例](#ai-practice-conventions-常见反例)
- [上线前评审清单](#ai-practice-conventions-上线前评审清单)

<a id="ai-practice-conventions-核心规则"></a>
### 核心规则

| 层次 | 应该负责 | 不应负责 |
| --- | --- | --- |
| Controller | HTTP 参数校验、身份与响应 DTO | 组装 Wrapper、开启事务、返回可变持久化实体 |
| Service | 业务规则、事务边界、自动更新作用域、结果组装 | 依赖 Controller 或吞掉异常 |
| Repository | 声明式查询和标准 CRUD，可选查询缓存与数据源路由 | 业务事务、跨多个 Repository 的流程 |
| Entity | 表映射、字段与索引元数据 | Controller 响应协议和与表无关的业务状态 |
| DTO/VO | 请求、响应、JoinQuery 投影或统计结果 | 拥有表结构；不要与完整 Entity 同时 `autoSchema=true` |

默认决策：

1. 事务标在 Spring 管理的 Service public 方法上。
2. 需要 setter 自动回写时，在同一个 Service 方法上组合 `@Transactional + @JoinSync`。
3. Repository 默认不承担业务事务；缓存只标记查询方法。
4. 一般查询返回 DTO；需要修改的代理 Entity 不逃离原始线程和事务。
5. 多数据源时明确 group；不依赖 Bean 注册顺序或 `primary` 名称。
6. 查询缓存是可丢失的加速层，不是事务一致性或业务状态存储。
7. 写入必须校验主键或条件，并检查影响行数；不假设“调用没报错”等于写入成功。
8. 同一物理表只有一个完整 Entity 拥有自动 Schema；字段改名、改类型和数据搬迁使用版本化脚本。
9. 默认开启驼峰转下划线映射；标准映射不要重复填写 `@JoinField.name`。

例如下面的属性会自动映射到数据库列 `base_pushes`：

```java
@JoinField(description = "基础推荐量")
private long basePushes;
```

`basePushes -> base_pushes`、`tenantId -> tenant_id`、`crtTime -> crt_time` 都属于标准映射。只有物理列名无法由属性名按该规则得到时，才显式填写 `name`，例如属性 `username` 对应历史列 `user_name`；如果关闭某个数据源的 `map-underscore-to-camel-case`，则以该数据源的实际映射规则为准。

<a id="ai-practice-conventions-推荐目录结构"></a>
### 推荐目录结构

按业务域组织代码，不要把整个项目的 Controller、Service、Entity 分成三个巨型目录：

```text
com.example.app
├── user
│   ├── api
│   │   ├── UserController.java
│   │   └── UserResponse.java
│   ├── domain
│   │   └── UserEntity.java
│   ├── repository
│   │   └── UserRepository.java
│   └── service
│       └── UserService.java
├── video
│   └── ...
└── shared
    ├── config
    └── error
```

Repository 扫描和 Schema 扫描是两件事：`yulinlin.repository.scan-packages` 找接口，`yulinlin.<module>.schema-packages` 找显式 `autoSchema=true` 的完整实体。两者都可按业务包递归扫描。

<a id="ai-practice-conventions-事务与自动更新"></a>
### 事务与自动更新

<a id="ai-practice-conventions-推荐写法"></a>
#### 推荐写法

```java
@Transactional
@JoinSync
public void changeProfile(String id, String username) {
    UserEntity user = repository.findByIdEq(id);
    if (user == null) throw new IllegalArgumentException("user not found");
    user.setUsername(username);
}
```

- `@Transactional` 经默认的 `yulinlinTransactionManager` 委托 RouteSession；连接由框架独立提交，不复用 Spring JDBC 事务。`@JoinSync` 只决定作用域内查询是否返回自动更新代理。
- 默认只对 RuntimeException/Error 回滚，受检异常也需回滚时写 `@Transactional(rollbackFor = Exception.class)`。目前只支持 REQUIRED 及默认隔离/超时、非只读语义，完整边界见[事务注解兼容](#ai-relations-transactions-spring-事务注解兼容)。
- 必须通过 Spring Bean 从外部调用 public 方法；`this.changeProfile(...)` 式自调用会绕过 Spring AOP。
- 不要将代理 Entity 传给异步任务、虚拟线程或事务外的 Controller。它只能在创建它的原线程和原事务中 setter。
- 业务失败让异常离开事务方法。如果必须捕获，应继续抛出或明确设置 rollback-only，不要吞掉后继提交。
- 只读方法不加 `@JoinSync`。全局 `yulinlin.data.auto-update=true` 适合对边界非常一致的项目；一般项目优先显式方法注解。
- Repository 方法可使用 `@JoinSync` 只增强特定查询，但仍需外层 Service 活动事务。推荐默认放在 Service，避免事务和更新意图分散。

自动更新跳过 null，`setX(null)` 不表示把列清为 SQL NULL。如果业务必须清空列，使用参数化自定义 SQL；不要利用字符串拼接绕过参数绑定。

<a id="ai-practice-conventions-缓存规范"></a>
### 缓存规范

```java
@JoinCache
UserEntity findByUsernameEq(String username);
```

- 只缓存价值明确、读多写少且可接受 TTL 窗口的查询。不是每个 Repository 方法都应该加缓存。
- 引入缓存 Provider 后，`yulinlin.cache.clear-on-write`默认 `true`；标准 insert/update/delete 在事务提交成功后按表 namespace 清理缓存，回滚不清理。
- 写 Wrapper 和 `ExecuteRequest` 只使用 `clearCache(boolean)` 覆盖单次行为。一般保持默认；只有明确接受 TTL 窗口内旧结果时才使用 `clearCache(false)`。
- 自定义查询或跨表投影无法完整推导依赖时，通过 `@JoinCache.namespaces` 或查询请求的 `cacheNamespaces(...)` 补充表命名空间；自定义写 SQL 开启清理时会退化为清理全部 ORM 查询缓存。
- 事务内必须读到最新数据的查询使用 `CacheMode.NONE`；强制回源并重写缓存使用 `CacheMode.REFRESH`。
- 未引入缓存 Provider 时，普通查询安全回源数据库，`QueryCacheProperties` 不会启用，写路径也不解析 namespace 或执行失效。一个应用只选 Caffeine、Ehcache 或 Redis 之一；`cache-redis` 已自带可选 Caffeine L1，不要再重复引入 `cache-caffeine`。
- 默认 TTL 放在 `yulinlin.cache.ttl`，默认不让 TTL 参与 Key。监控统计默认关闭，只在确实需要观测时开启。

<a id="ai-practice-conventions-日志规范"></a>
### 日志规范

面向开发和运维人员的日志描述统一使用中文。SQL 执行日志的指标也使用中文，如 `数据源`、`节点`、`总耗时`、`平均每条耗时`、`参数`；类名、SQL、异常类型以及数据库标识值保持原样。业务日志和事务生命周期日志可保留既有结构化键，避免不必要的接口变化；日志采集规则需按实际格式配置。

推荐写法：

```java
log.info("订单创建成功 orderId={} userId={}", orderId, userId);
log.warn("缓存条目超过大小限制，已跳过 key={} sizeBytes={} limitBytes={}",
        key, actualBytes, limitBytes);
log.error("支付回调处理失败 orderId={} channel={}", orderId, channel, error);
```

统一规则：

- 描述动作和结果，例如“SQL执行成功”“延迟写队列刷新完成”“控制器发生未处理异常”，不要只写“失败”或一串对象值。
- 使用 `{}`占位符记录上下文，不使用字符串拼接；字段键保持稳定，便于日志平台查询。
- 捕获异常时把 `Throwable`作为最后一个参数传入，保留完整堆栈；不要只打印 `error.getMessage()`。
- `DEBUG`用于批次、缓存命中等高频诊断；`INFO`用于重要生命周期和明确成功事件；`WARN`用于可恢复、已降级或将重试的问题；`ERROR`用于本次操作失败、数据被丢弃或必须人工处理的问题。
- 同一异常只在能够补充业务上下文或最终处理它的层记录一次，随后继续抛出的中间层不要重复打印相同堆栈。
- 不记录密码、密钥、令牌、完整身份证号、银行卡号和未脱敏请求体。SQL参数日志也必须遵守长度限制与脱敏策略。
- 日志文字修改不应改变监控数据结构。数据库监控使用独立记录对象，`elapsedMs`等字段含义保持稳定。

SQL 日志在同一事件内分行展示，方便控制台阅读：

```text
SQL慢查询 事务ID=none 数据源=mysql 节点=master 操作=select 总耗时=800ms
SQL="select ..."
参数=[...]
```

参数行只在 log-params 开启时输出。批量成功时增加平均每条耗时（分组总耗时 ÷ 操作数量，三位小数），失败不展示平均值；参数标签统一为“参数”，批量只打印受限参数摘要。并行各组耗时不能相加当作请求耗时。不要依赖整段日志文本进行业务控制；程序判断应使用异常、返回值或监控接口。采集日志文本时配置多行合并，内置 monitor 不依赖日志文本格式。

<a id="ai-practice-conventions-多数据源规范"></a>
### 多数据源规范

只有一个 group 时可省略指定；同时注册多个 group 时设定默认组：

```yaml
yulinlin:
  datasource:
    default-group: mysql
```

一个 Repository 始终属于同一数据源时，可在接口标记：

```java
@JoinRepository
@JoinSession("postgresql")
public interface SearchRepository extends BaseRepository<SearchEntity> {
    // 默认使用 postgresql
}
```

只有个别方法需要切换时使用方法级 `@JoinSession`。代码中显式 group 的 Request 优先级更高。不要在一条原始 SQL 中假设框架会自动翻译 MySQL、PostgreSQL、SQLite 和 H2 方言。

跨多个 group 的业务可使用 Service 层 `@JoinTransaction` 或 `RouteSession.transaction(...)` 统一收集参与会话。这是多个本地事务的协调提交，不是 2PC/XA；对严格原子性业务使用消息、补偿、幂等或专用分布式事务方案。

<a id="ai-practice-conventions-批量写入规范"></a>
### 批量写入规范

```java
@Transactional
public int importUsers(List<UserEntity> users) {
    if (users == null || users.isEmpty()) return 0;
    return ModelInsertWrapper.newInstance("mysql", users)
            .batch()
            .execute();
}
```

- 普通集合写入先使用 JDBC batch；数据足够大、数据库与连接池有余量时，才启用 `.batch()` 多连接拆分。
- `parallel-connections` 控制一个框架事务的最大物理连接数，`execute-batch-size` 控制一次 `executeBatch()` 的行数，都应结合连接池和数据库实测。
- SQLite 单文件只有一个写者，Session 会拒绝多连接拆组；不要用增加写线程的方式解决 SQLite 锁竞争。
- 业务上可以每批 128 个对象提交给上层，底层仍由 `execute-batch-size` 限制 JDBC 批大小。不为每 128 条数据创建一个新线程或新事务。
- 大文本、JSON 和二进制内容应限制单条大小；不把不受控的 InputStream 或超大对象混入普通批次。

允许一分钟左右最终一致的点赞、浏览统计等小批写入，可使用 `.queue().execute()` 交给全局 `yulinlin.data.queue` 合并；当前活动队列的 Node 总数达到 `flush-size`（默认 `1024`）会立即异步刷新，即使这些 Node 分布在多个不同分组中。它返回 `0` 只表示入队成功，不能放在事务内，也不适合依赖自增 ID、实际影响行数或严格操作顺序的业务。进程内队列没有硬容量上限；持续生产快于写入时会继续占用内存，应限流并监控积压。它也不是持久化消息系统，关键数据仍使用同步事务或可靠消息；大数据导入直接在业务异步任务中调用 `.batch()`，不通过延迟队列拆事务。

<a id="ai-practice-conventions-schema-管理规范"></a>
### Schema 管理规范

- 生产项目默认使用版本化 DDL 迁移；框架 `CREATE` 模式更适合本地数据库、新项目和可控表。
- 只有拥有全部持久化列的 Entity 设置 `autoSchema=true`。投影 DTO、Join 模型和统计模型保持 false。
- 自动 Schema 可创建表、缺失普通列和声明索引，不会安全推断字段改名、类型变更、删列、外键或数据转换。
- 先用 `EntitySession.createTableSql(Entity.class)` 审查 DDL，再在目标环境启用自动初始化。所有执行的 Schema SQL 都应在日志中可见。

<a id="ai-practice-conventions-常见反例"></a>
### 常见反例

| 反例 | 问题 | 推荐替代 |
| --- | --- | --- |
| Controller 直接调 Wrapper | 协议层与数据层耦合，无法统一事务 | Controller 只调 Service |
| 在 Repository 方法开事务，返回后再 setter | Repository 返回时事务可能已结束 | Service 上组合 `@Transactional + @JoinSync` |
| 实现 `ISuperService` 后不声明事务 | 接口默认方法只封装 CRUD，不会自动开启事务 | 在具体 Service 业务入口上使用 `@Transactional` |
| 把 `save()` 当成无竞态 UPSERT | 实际是先查后分组写入，并发请求可同时判断为不存在 | 已知操作类型时使用 `insert/update`，并依赖唯一索引保底 |
| 将 `ITreeService.deleteById*` 当作普通单行删除 | 该默认实现会级联删除后代节点 | 事务内执行，接口命名和二次确认明确告知级联语义 |
| 全局开启自动更新，却大量无事务查询 | 需要代理的查询会明确报“请开启事务” | 默认关闭，在写业务方法显式 `@JoinSync` |
| 将 `@JoinCache` 加到写方法 | 缓存注解是查询策略，不是写入清理开关 | 保留全局 `clear-on-write`，或在写 Wrapper 上调用 `clearCache(boolean)` |
| 在写 Wrapper 上调用旧 `cache/invalidate` API | 写入不缓存结果，旧命名容易混淆查询策略 | 统一使用 `clearCache(boolean)` |
| 多 group 不配默认值，也不显式路由 | 框架无法判断业务数据库 | 配置 `default-group` 或使用 group/`@JoinSession` |
| 在事务中启动 `CompletableFuture` 修改同一实体 | ThreadLocal、连接和代理不自动跨线程 | 同一事务同步完成；异步任务自建业务边界 |
| 日志只写英文短语、拼接对象或只打印异常消息 | 难以直接阅读、无法稳定检索且丢失堆栈 | 中文描述 + 稳定字段键 + `{}`占位符，异常放最后一个参数 |
| 用 null 期望清空数据库列 | 普通更新和自动更新都跳过 null | 参数化自定义 SQL |
| 多个不完整模型共同拥有一张表的 Schema | 可创建残缺表或发生拥有者冲突 | 只让一个完整 Entity `autoSchema=true` |
| 把跨库本地协调当作分布式原子事务 | 某库提交后另一库仍可失败 | 设计幂等、补偿或专用分布式方案 |

<a id="ai-practice-conventions-上线前评审清单"></a>
### 上线前评审清单

- [ ] 所有写 Service 都有清晰的事务边界，通过 Spring Bean 外部调用。
- [ ] 使用 `@JoinSync` 的查询、setter 和提交处在同一线程与事务。
- [ ] 实体基类与物理表列一致；未重复声明继承的主键、审计时间或树字段。
- [ ] 实体具有无参构造、完整主键映射和可预期的列名；非列字段明确 `exist=false`。
- [ ] 更新和删除校验主键/条件及影响行数，已考虑 null 跳过语义。
- [ ] 缓存仅用于查询，TTL、namespace、`clear-on-write` 默认值和单次覆盖策略已明确，应用未同时引入两个 Provider。
- [ ] 多数据源项目设置了默认 group 或显式路由，原始 SQL 已按目标方言审查。
- [ ] 业务日志使用中文描述和稳定字段键，异常保留完整堆栈，敏感信息已经脱敏。
- [ ] 多连接批处理已在真实驱动、连接池和数据库上压测；SQLite 未强行并发写入。
- [ ] 每张自动表只有一个 Schema 拥有者，生产字段改名和类型变更都有可回滚迁移脚本。
- [ ] 测试至少覆盖成功提交、异常回滚、缓存未命中/失效、批量失败和多数据源路由。

如果项目不使用 Repository，保留相同的分层和事务规则，在 Service 中使用 Model Wrapper 即可。Repository 是声明式入口，不是使用事务、缓存或自动更新的强制前提。

---

下一步：返回[项目实践主题目录](practice/README.md)按需选择相关专题。

---

<a id="ai-practice-models"></a>
<!-- source: doc/practice/models.md -->
<a id="ai-practice-models-公共实体与页面参数"></a>
## 公共实体与页面参数

[文档首页](README.md) / [项目实践](practice/README.md)

PagePo、JoinWhere、R、IdEntity、SuperEntity 和 TreeEntity。

<a id="ai-practice-models-本页导航"></a>
### 本页导航

- [公共业务模型](#ai-practice-models-公共业务模型)
- [页面查询对象：`PagePo + @JoinWhere`](#ai-practice-models-页面查询对象pagepo--joinwhere)
- [统一 Controller 响应：`R<E>`](#ai-practice-models-统一-controller-响应r)
- [基础实体选择](#ai-practice-models-基础实体选择)
- [`IdEntity`](#ai-practice-models-identity)
- [`SuperEntity`](#ai-practice-models-superentity)
- [`TreeEntity`](#ai-practice-models-treeentity)
- [自定义项目基类](#ai-practice-models-自定义项目基类)

<a id="ai-practice-models-公共业务模型"></a>
### 公共业务模型

框架把常见的页面查询、分页结果和 Web 响应模型集中提供给业务项目，避免每个模块重复声明 `pageNumber`、`pageSize`、排序、ID 和列表等字段。它们是对外公共 API，不是 ORM 内部临时对象。

| 类型 | 所属模块 | 用途 |
| --- | --- | --- |
| `PagePo` | `common` | 页面查询入参基类，统一分页、排序和运行时追加条件 |
| `OrderItem` | `common` | 一个排序字段及升降序 |
| `PageVo<E>` | `common` | 兼容已有业务的分页响应类型，继承 `Page<E>` |
| `Page<E>` | `lang` | ORM 和 Service 共用的分页结果，包含当前页列表、总数和扩展值 |
| `IdPo` | `starter` | Web 接口的单个字符串 ID 入参 |
| `ListPo<E>` | `starter` | Web 接口的通用列表入参 |
| `R<E>` / `ResponseVo<E>` | `starter` | 标准 Web 响应体；新代码使用 `R`，`ResponseVo`仅保留废弃兼容写法 |

这些类型按使用的门面模块引入。只使用 ORM 的项目可直接使用 `PagePo`和 `Page`，不必为了 `IdPo`或 `R`引入 Web Controller。它们也不要求实现 Java `Serializable`，HTTP JSON 仍由 Spring/Jackson 处理。

<a id="ai-practice-models-页面查询对象pagepo--joinwhere"></a>
#### 页面查询对象：`PagePo + @JoinWhere`

页面列表查询推荐为每个业务页面定义一个很薄的 `XxxPo extends PagePo`。父类统一携带分页和排序，子类只声明本页面的业务条件：

```java
package com.example.app.pay.api;

import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinWhere;
import com.yulinlin.data.lang.util.DateTime;
import lombok.Data;

import java.util.List;

@Data
public class PayChannelPo extends PagePo {

    @JoinWhere(condition = ConditionEnum.eq)
    private String platformId;

    @JoinWhere(condition = ConditionEnum.eq)
    private String status;

    @JoinWhere(condition = ConditionEnum.between)
    private List<DateTime> crtTime;
}
```

Controller 只接收业务查询对象，Service 复用 `ISuperService.page(PagePo)`：

```java
@PostMapping("/page")
public R<Page<PayChannelEntity>> page(@RequestBody PayChannelPo po) {
    return R.newInstance(payChannelService.page(po));
}
```

请求可以同时携带业务条件、分页和排序：

```json
{
  "pageNumber": 1,
  "pageSize": 20,
  "platformId": "web",
  "status": "enabled",
  "crtTime": ["2026-10-01 00:00:00", "2026-10-15 23:59:59"],
  "orderList": [
    { "name": "crtTime", "asc": false }
  ]
}
```

`ISuperService.page(po)`的核心过程是先把查询对象交给 Wrapper，再应用分页和排序（接口实现还会合并 `PagePo.and/or`中的运行时条件）：

```java
Page<PayChannelEntity> result = ModelSelectWrapper
        .newInstance(PayChannelEntity.class, po)
        .page(po.getPageNumber(), po.getPageSize())
        .orderBy(order -> {
            if (po.getOrderList() != null) {
                for (OrderItem item : po.getOrderList()) {
                    order.orderBy(item.getName(), item.isAsc());
                }
            }
        })
        .selectPage();
```

查询对象解析遵循以下规则：

- 只有带 `@JoinWhere` 且实际提供值的子类字段生成 WHERE；`pageNumber`、`pageSize`和 `orderList`没有该注解，不会成为查询条件。
- `@JoinWhere.name`省略时使用查询对象的 Java 属性名；查询属性与实体属性不同时显式填写实体属性名。
- `between`传入按“起始、结束”排列的两个值，`in`传集合；字段类型继续使用框架现有 SQL 编解码规则。
- `PagePo.and(...)`和 `or(...)`用于运行时才知道字段或操作符的补充条件，不是普通页面查询的必写步骤。
- `orderList.name`应使用实体 Java 属性名。直接接收前端排序时，业务层应限制允许排序的属性，避免任意字段参与查询。
- 复杂嵌套 AND/OR、子查询或数据库专用表达式继续使用 Wrapper，不要把所有 SQL 逻辑塞进 PO。

不通过 `ISuperService`时，也可以直接把同一个查询对象传给指定数据源：

```java
Page<PayChannelEntity> result = ModelSelectWrapper
        .newInstance("mysql", PayChannelEntity.class, po)
        .page(po.getPageNumber(), po.getPageSize())
        .selectPage();
```

`PayChannelEntity.class`决定表、返回字段和结果类型，`PayChannelPo`只描述 WHERE、分页和排序输入。查询 PO 不需要 `@JoinTable`，也不应开启自动 Schema。`@JoinWhere`支持的操作符、空值和 AND/OR 精确语义见[查询条件对象](#ai-orm-query-查询条件对象)。

<a id="ai-practice-models-统一-controller-响应r"></a>
#### 统一 Controller 响应：`R<E>`

普通 JSON Controller 统一使用 `@PostMapping + @RequestBody`并显式返回 `R<E>`。查询、详情、创建、修改和删除都把参数放进一个 JSON 请求体；配置设备密钥或自定义 CryptoCodec 后，无注解接口也使用全局加解密规则，`@ApiCrypto(false)` 用于兼容明密文请求、保持响应明文。框架不会自动把任意返回对象包装为 R。

```java
@PostMapping("/page")
public R<Page<PayChannelEntity>> page(@RequestBody PayChannelPo po) {
    return R.ok(payChannelService.page(po));
}

@PostMapping("/delete")
public R<Void> delete(@RequestBody IdPo po) {
    payChannelService.delete(po.getId());
    return R.ok();
}
```

标准工厂方法：

| 方法 | 结果 |
| --- | --- |
| `R.ok()` | 成功且没有响应数据 |
| `R.ok(data)` | 成功并返回数据 |
| `R.ok(data, message)` | 成功并返回数据和提示 |
| `R.fail(message)` | 默认 `500`失败响应 |
| `R.fail(code, message)` | 指定错误码的失败响应 |

成功响应默认 `code=200`、`ok=true`；失败工厂始终设置 `ok=false`。每个新响应都会写入当前 `timestamp`，`crypt`默认 false。旧的 `R.newInstance(...)`和 `ResponseVo<E>`暂时保留二进制/源码兼容，但均已废弃，新业务不要继续使用。

`starter`默认注册最低优先级的异常处理器：参数绑定或 JSON 格式错误返回 `400`；`ApiException`携带 `400–599`标准 HTTP 错误码时使用该状态，其他数值按 `400`处理；`ResponseStatusException`保留 HTTP 状态；未识别异常记录完整服务端日志并返回 `500 + 系统繁忙，请稍后重试`。错误 HTTP 状态和 `R.code`保持一致；业务项目可以提供更高优先级的 `@RestControllerAdvice`覆盖细分规则。不要把 SQL、文件路径、密钥或异常堆栈写进客户端 `msg`。

接口按全局规则需要加密且返回 `R`时，只替换并加密 `data`，随后设置 `crypt=true`；`code`、`ok`、`msg`和 `timestamp`保持明文。`data == null`时跳过加密并保持 `crypt=false`，错误提示因此无需客户端先解密。详细协议见[接口安全](security/README.md)。

业务 JSON 接口不要用 `@GetMapping`、`@PutMapping`、`@PatchMapping`或 `@DeleteMapping`承载加密参数，也不要把敏感条件放在 query/path 中。监控页面、静态资源、健康检查、文件下载、图片/视频/音频流、SSE、重定向以及直接写 `HttpServletResponse`的接口不受这条约束；流式响应不使用 `R`包装，应配置 `crypto.exclude-paths` 跳过 JSON 加解密流程，不能仅靠不写注解规避。

<a id="ai-practice-models-基础实体选择"></a>
### 基础实体选择

`common` 模块提供了几个可复用实体。它们不只是字段集合，还实现了 `AbstractModel` 的 CRUD 便捷入口和写入前生命周期，因此应按表结构选择，不要为了少写字段而继承。

| 类型 | 持久化字段 | 自动行为 | 适用场景 |
| --- | --- | --- | --- |
| 普通 POJO | 自行定义 | 无 | 数据库自增主键、`Long`/UUID 主键、投影结果或非标准表 |
| `IdEntity<E>` | `String id` | 插入前在 `id` 为 null 或空串时生成 Snowflake 字符串 ID | 大多数由应用生成字符串主键的业务表 |
| `SuperEntity<E>` | `id`、`crtTime`、`uptTime` | 包含 `IdEntity` 行为，插入时补齐空的创建/更新时间 | 同时具有主键和审计时间列的表 |
| `TreeEntity<E>` | `id`、`crtTime`、`uptTime`、`parentId`、`parentPath` | 默认根节点 ID 为 `"0"`，`selectTree()` 可按 `ITreeNode` 组装 `children` | 菜单、部门和分类等树表 |
| `EntityCounter` | 不持有表字段 | 按主键立即/聚合自增更新；或按非数字维度聚合数字字段后批量插入统计记录 | 播放量、点击量、热度和分渠道统计；它是写入门面，不作为实体父类 |

<a id="ai-practice-models-identity"></a>
#### `IdEntity`

```java
@JoinTable(value = "sys_user", autoSchema = true)
public class UserEntity extends IdEntity<UserEntity> {
    @JoinField(textLength = 64, description = "登录用户名")
    private String username;

    public UserEntity() { }
    // getter/setter
}
```

`id` 已声明为主键和默认条件字段，子类不要再次定义同名字段。插入前的 `insertBefore()` 会保留业务已设置的 ID，仅在未设置时调用 `generateId()`。需要其他应用侧 ID 策略时可在子类重写 `generateId()`；如果主键由数据库自增、序列或触发器产生，应改用普通 POJO 并显式定义主键。

`nextIncrId()` 会用表中当前最大 ID 初始化进程内计数器，之后在本进程递增。它不是数据库自增 ID，也不保证多实例唯一；分布式业务保持默认 Snowflake 策略或接入统一 ID 服务。

`IncrementEntity` 的动态表名/列名方式仅为旧代码保留，已经废弃。已有视频记录使用 `EntityCounter.update(VideoEntity.class, videoId).inc(VideoEntity::getViewCount).submit()`；统计明细使用 `EntityCounter.insert(stat).submit()`，非数字字段作为维度、数字字段分别累加，默认一分钟批量插入。不要提前填充统计实体的 ID/时间，以免它们意外成为不同维度。立即写入、类型范围、聚合规则、配置和数据丢失边界见[实体计数器](#ai-write-sync-counter-实体计数器)。

多字段增量也可以新建模型，仅设置主键和本次增量后调用 `EntityCounter.update("mysql", delta).submit()`，或 `EntityCounter.update(delta).execute()` 立即写入。数字正值增加、负值减少，非数字字段忽略，主键只作条件。不要传入查回的完整实体：它不是覆盖更新，已有的非零数字都会再次累加。模型更新不触发 `updateBefore`、自动更新时间或版本校验，完整用例见 [通过模型提交更新增量](#ai-write-sync-counter-通过模型提交更新增量)。

<a id="ai-practice-models-superentity"></a>
#### `SuperEntity`

```java
@JoinTable(value = "video_comment", autoSchema = true)
public class CommentEntity extends SuperEntity<CommentEntity> {
    @JoinField(description = "视频 ID")
    private String videoId;

    @JoinField(textType = TextTypeEnum.text, description = "评论内容")
    private String content;

    public CommentEntity() { }
    // getter/setter
}
```

`crtTime` 和 `uptTime` 的 Java 类型都是框架 `DateTime`，按驼峰转下划线规则对应 `crt_time` 和 `upt_time`。只有物理表确实具有这两列时才继承 `SuperEntity`；自动 Schema 也会把继承字段当作真实列处理，并在 MySQL、PostgreSQL、H2 中创建原生时间列，在 SQLite 中创建固定格式 TEXT 列。

当前 `updateBefore()` 只在 `uptTime == null` 时填入当前时间。如果业务要求每次更新都刷新时间，应在写入前显式 `setUptTime(DateTime.now())`，或在项目自定义父类中重写 `updateBefore()`，不要把“非空时保留原值”误解为无条件自动刷新。

<a id="ai-practice-models-treeentity"></a>
#### `TreeEntity`

```java
@JoinTable(value = "sys_menu", autoSchema = true)
public class MenuEntity extends TreeEntity<MenuEntity> {
    @JoinField(textLength = 64, description = "菜单名称")
    private String name;

    public MenuEntity() { }
    // getter/setter
}
```

`children` 已标记 `@JoinField(exist = false)`，只用于内存树结构，不会成为表列。`parentId` 和 `parentPath` 是真实列；新节点没有指定父节点时使用全局根值 `"0"`。如果项目需要其他根值，应在启动配置阶段统一设置，不要在请求中反复修改这个静态值。

`ModelSelectWrapper.selectTree()` 会将查到的 `ITreeNode` 列表在内存中组装为树；普通 `selectList()` 仍返回扁平列表。`findChildren()` 和 `findParent()` 是使用默认 group 的便捷方法；在多数据源项目中，优先在 Service/Repository 明确路由后查询，避免树节点自身隐式选库。

<a id="ai-practice-models-自定义项目基类"></a>
#### 自定义项目基类

只有多张表真正共享同一列语义时才增加项目基类，例如所有业务表都具有租户 ID：

```java
public abstract class TenantEntity<E extends TenantEntity<E>> extends SuperEntity<E> {
    @JoinField(textLength = 64, description = "租户 ID")
    private String tenantId;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
```

实体的泛型参数始终传具体类，例如 `UserEntity extends IdEntity<UserEntity>`。不要在子类重复声明 `id`、`crtTime`、`uptTime`、`parentId` 或 `parentPath`；重名字段会让反射映射、Schema 比对和更新条件变得不确定。与表无关的计算属性仍必须显式使用 `@JoinField(exist = false)`。

---

下一步：返回[项目实践主题目录](practice/README.md)按需选择相关专题。

---

<a id="ai-practice-service"></a>
<!-- source: doc/practice/service.md -->
<a id="ai-practice-service-通用-service-与树服务"></a>
## 通用 Service 与树服务

[文档首页](README.md) / [项目实践](practice/README.md)

复用 ISuperService/ITreeService，区分 Repository 职责。

<a id="ai-practice-service-本页导航"></a>
### 本页导航

- [通用 Service 接口](#ai-practice-service-通用-service-接口)
- [`ISuperService` 用法](#ai-practice-service-isuperservice-用法)
- [`ITreeService` 用法](#ai-practice-service-itreeservice-用法)
- [与 Repository 如何选择](#ai-practice-service-与-repository-如何选择)

<a id="ai-practice-service-通用-service-接口"></a>
### 通用 Service 接口

`common` 模块通过接口默认方法提供无状态 CRUD 和树服务能力。新项目直接 `implements ISuperService<E>` 或 `implements ITreeService<E>`，不再占用业务 Service 的类继承位。`impl.ServiceImpl/TreeImpl` 与 `impl2` 只作为已废弃兼容层。

| 接口/实现 | 用途 | 关键能力 |
| --- | --- | --- |
| `ISuperService<E>` | 通用 CRUD 契约和默认实现 | 按 ID 查询、新增、更新、删除、保存、分页 |
| `ServiceImpl<E>` | 已废弃兼容类 | 保留旧 `extends ServiceImpl` 写法和 `dataGroup()` 扩展点 |
| `ITreeService<E>` | 树业务契约和默认实现 | 维护父路径、移动子树、删除后代、内存组树 |
| `TreeImpl<E>` | 已废弃兼容类 | 保留旧 `extends TreeImpl` 写法 |

<a id="ai-practice-service-isuperservice-用法"></a>
#### `ISuperService` 用法

```java
package com.example.app.user.service;

import com.example.app.user.domain.UserEntity;
import com.yulinlin.common.service.ISuperService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserCrudService implements ISuperService<UserEntity> {

    @Transactional(readOnly = true)
    public UserEntity detail(String id) {
        return findById(id);
    }

    @Transactional
    public int create(UserEntity user) {
        return insertOne(user);
    }

    @Transactional
    public int change(UserEntity user) {
        return updateOne(user);
    }
}
```

Service 接口默认方法不会注册 Spring Bean，也不会自动开启事务。具体实现类仍必须使用 `@Service`，并由业务入口定义事务边界。实现这个接口不等于自动获得事务、缓存或数据源路由。

常用方法语义：

| 方法 | 行为 |
| --- | --- |
| `findById(id)` / `findByIdIn(ids)` | 按实体主键查询 |
| `all()` / `page(PagePo)` | 查询全部或动态分页 |
| `insertOne/list` | 明确新增，会执行实体 `insertBefore()` |
| `updateOne/list` | 明确按主键更新，会执行 `updateBefore()` |
| `deleteById/IdIn` | 明确按主键删除 |
| `save/saveList` | 先查询主键是否存在，再将对象分为 insert/update 两组 |
| `newInstance()` | 通过反射创建实体实例 |

`save()` 不是数据库原生 UPSERT：它会先查后写，有额外往返，且并发时仍要依赖唯一索引处理竞态。业务明确知道是新增还是更新时，直接调用 `insert*` 或 `update*` 性能更好，语义也更清楚。

`page(PagePo)`会解析业务子类上的 `@JoinWhere`字段，再应用 `PagePo`自带的动态条件、分页和排序；完整页面写法见[公共业务模型](#ai-practice-models-页面查询对象pagepo--joinwhere)。不要把前端传入的排序字段名无限制透传；Controller/Service 应使用允许列表转换为可排序的实体属性。

<a id="ai-practice-service-itreeservice-用法"></a>
#### `ITreeService` 用法

```java
package com.example.app.menu.service;

import com.example.app.menu.domain.MenuEntity;
import com.yulinlin.common.service.ITreeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MenuService implements ITreeService<MenuEntity> {

    @Transactional(readOnly = true)
    public List<MenuEntity> tree(MenuQuery query) {
        return getTree(query);
    }

    @Transactional
    public int saveMenu(MenuEntity menu) {
        return save(menu);
    }

    @Transactional
    public int removeMenu(String id) {
        return deleteById(id);
    }
}
```

`ITreeService` 继承全部通用 CRUD，并增加以下约定：

1. `save()` 会根据 `parentPath` 最后一项推导 `parentId`，空路径表示根节点。
2. `updateOne()` 在父节点变化时重新计算后代节点的 `parentPath`。
3. `deleteByIdIn()` 会同时删除 `parentPath` 匹配的后代节点，这是级联删除，调用前必须确认业务意图。
4. `getTree(query)` 先查询扁平数据，再在内存中组树；实体实现 `ISortValue` 时按 `sortValue` 递归排序。

树移动和级联删除会修改多行，必须在事务中执行。接口会拒绝 `parentPath` 包含自身 ID 的移动，防止节点移入自己的后代形成环。大树的 `getTree()` 会一次性加载数据，后台菜单等小树可直接使用；数万节点的树应改用按层/按父 ID 分页查询。

<a id="ai-practice-service-与-repository-如何选择"></a>
#### 与 Repository 如何选择

- 简单后台 CRUD、树维护，且始终使用默认数据源时，可实现 `ISuperService` 或 `ITreeService`。
- 需要声明式查询、方法级 `@JoinCache`、`@JoinSession` 或希望数据访问边界更清晰时，优先 Repository + 业务 Service。
- 一个业务域选择一个主入口。不要在同一个 Service 中既实现通用接口、又为同一批 CRUD 注入 Repository，造成两套规则和缓存策略。
- 这两个接口默认方法创建的 Wrapper 没有显式 group，依赖全局默认数据源。多数据源业务优先使用可明确声明 group 的 Repository 或 Wrapper。

---

下一步：返回[项目实践主题目录](practice/README.md)按需选择相关专题。

---

<a id="ai-practice-crud-example"></a>
<!-- source: doc/practice/crud-example.md -->
<a id="ai-practice-crud-example-完整-crud-分层案例"></a>
## 完整 CRUD 分层案例

[文档首页](README.md) / [项目实践](practice/README.md)

实体、Repository、DTO、Service 和 Controller 配套使用。

<a id="ai-practice-crud-example-本页导航"></a>
### 本页导航

- [完整业务案例](#ai-practice-crud-example-完整业务案例)
- [1. 完整实体](#ai-practice-crud-example-1-完整实体)
- [2. Repository](#ai-practice-crud-example-2-repository)
- [3. 响应 DTO](#ai-practice-crud-example-3-响应-dto)
- [4. Service](#ai-practice-crud-example-4-service)
- [5. Controller](#ai-practice-crud-example-5-controller)

<a id="ai-practice-crud-example-完整业务案例"></a>
### 完整业务案例

下面用“用户查询、创建、改名和删除”展示推荐分层。案例假设已引入 starter、mysql 和 repository；查询缓存模块仍是可选的。

<a id="ai-practice-crud-example-1-完整实体"></a>
#### 1. 完整实体

```java
package com.example.app.user.domain;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinIndex;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;

@JoinTable(value = "sys_user", autoSchema = true)
@JoinIndex(fields = "username", unique = true)
public class UserEntity extends IdEntity<UserEntity> {
    @JoinField(name = "user_name", textLength = 64, description = "登录用户名")
    @JoinWhere
    private String username;

    @JoinField(description = "状态：1启用，0停用")
    @JoinWhere
    private Integer status;

    public UserEntity() { }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
```

Entity 保持非 final，提供无参构造和标准 getter/setter，以便编解码和代理。上例假设框架拥有该表全部列，因此明确设置 `autoSchema=true`；配套扫描配置如下：

这里的 `username` 并不能按驼峰规则转换成 `user_name`，所以示例中的 `@JoinField(name = "user_name")` 是必要的；如果 Java 属性本身命名为 `userName`，则应省略 `name`。

```yaml
yulinlin:
  repository:
    scan-packages:
      - "com.example.app.**.repository"
  mysql:
    schema-mode: CREATE
    schema-packages:
      - "com.example.app.**.domain"
```

如果生产表由 Flyway、Liquibase 或发布脚本管理，保留 `autoSchema=false` 默认值，不配置 `CREATE` 扫描，并在迁移脚本中创建同等的唯一索引。

<a id="ai-practice-crud-example-2-repository"></a>
#### 2. Repository

```java
package com.example.app.user.repository;

import com.example.app.user.domain.UserEntity;
import com.yulinlin.data.core.anno.JoinCache;
import com.yulinlin.data.core.anno.JoinRepository;
import com.yulinlin.repository.dao.BaseRepository;

@JoinRepository
public interface UserRepository extends BaseRepository<UserEntity> {

    @JoinCache
    UserEntity findByUsernameEq(String username);
}
```

`BaseRepository` 已提供按 ID 查询、全量查询、新增、更新和删除。方法级 `@JoinCache` 让缓存意图可见；如果希望接口中全部查询默认缓存，也可标在接口上，但方法级更容易评审。不要在 insert/update/delete 方法上使用 `@JoinCache`。

<a id="ai-practice-crud-example-3-响应-dto"></a>
#### 3. 响应 DTO

```java
package com.example.app.user.api;

import com.example.app.user.domain.UserEntity;

public record UserResponse(String id, String username, Integer status) {
    public static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getStatus());
    }
}
```

在 Service 内完成 Entity 到 DTO 的转换，可以避免 Controller 触发懒加载，也不会把事务绑定代理暴露给 JSON 序列化层。

<a id="ai-practice-crud-example-4-service"></a>
#### 4. Service

```java
package com.example.app.user.service;

import com.example.app.user.api.UserResponse;
import com.example.app.user.domain.UserEntity;
import com.example.app.user.repository.UserRepository;
import com.yulinlin.data.core.anno.JoinSync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public UserResponse findByUsername(String username) {
        UserEntity user = repository.findByUsernameEq(requireText(username, "username"));
        if (user == null) throw new IllegalArgumentException("user not found");
        return UserResponse.from(user);
    }

    @Transactional
    public String create(String username) {
        UserEntity user = new UserEntity();
        user.setUsername(requireText(username, "username"));
        user.setStatus(1);
        int affected = repository.insert(user);
        if (affected != 1) throw new IllegalStateException("user insert failed");
        return user.getId();
    }

    @Transactional
    @JoinSync
    public void rename(String id, String username) {
        UserEntity user = repository.findByIdEq(requireText(id, "id"));
        if (user == null) throw new IllegalArgumentException("user not found");
        user.setUsername(requireText(username, "username"));
        // 无需手动 update；提交前按主键生成部分更新。
    }

    @Transactional
    public void delete(String id) {
        int affected = repository.deleteByIdEq(requireText(id, "id"));
        if (affected != 1) throw new IllegalArgumentException("user not found");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
```

创建和删除是显式写入，只需事务。`rename` 依赖 setter 跟踪，因此同时使用 `@Transactional` 和 `@JoinSync`。查询、setter 与提交全部在同一个 Service 调用内完成。

<a id="ai-practice-crud-example-5-controller"></a>
#### 5. Controller

```java
package com.example.app.user.api;

import com.example.app.user.service.UserService;
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.starter.domain.IdPo;
import com.yulinlin.starter.domain.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@ApiCrypto
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping("/by-username")
    public R<UserResponse> findByUsername(@Valid @RequestBody FindUserRequest request) {
        return R.ok(service.findByUsername(request.username()));
    }

    @PostMapping("/create")
    public R<String> create(@Valid @RequestBody CreateUserRequest request) {
        return R.ok(service.create(request.username()), "创建成功");
    }

    @PostMapping("/rename")
    public R<Void> rename(@Valid @RequestBody RenameUserRequest request) {
        service.rename(request.id(), request.username());
        return R.ok();
    }

    @PostMapping("/delete")
    public R<Void> delete(@RequestBody IdPo request) {
        service.delete(request.getId());
        return R.ok();
    }

    public record FindUserRequest(@NotBlank String username) { }
    public record CreateUserRequest(@NotBlank @Size(max = 64) String username) { }
    public record RenameUserRequest(
            @NotBlank String id,
            @NotBlank @Size(max = 64) String username) { }
}
```

Controller 只处理 POST JSON 协议、校验和 DTO，不直接调用 Wrapper 或持有事务。查询和删除同样定义小型请求 DTO，不把参数拆到 URL。真实项目应将异常统一转换为约定的错误响应，不在每个 Controller 重复 try/catch。

---

下一步：返回[项目实践主题目录](practice/README.md)按需选择相关专题。

---

<a id="ai-practice-rbac"></a>
<!-- source: doc/practice/rbac.md -->
<a id="ai-practice-rbac-admin-rbac-与-jwt-登录案例"></a>
## Admin RBAC 与 JWT 登录案例

[文档首页](README.md) / [项目实践](practice/README.md)

两表角色权限模型、统一 LoginAuthenticator 认证与无状态 JWT 语义。

<a id="ai-practice-rbac-本页导航"></a>
### 本页导航

- [Admin 两表 RBAC 与登录案例](#ai-practice-rbac-admin-两表-rbac-与登录案例)
- [启动方式与完整配置](#ai-practice-rbac-启动方式与完整配置)
- [两表模型与状态枚举](#ai-practice-rbac-两表模型与状态枚举)
- [登录、权限和会话生命周期](#ai-practice-rbac-登录权限和会话生命周期)
- [业务写法与 HTTP 调用](#ai-practice-rbac-业务写法与-http-调用)
- [Service 完整 CRUD 与对外边界](#ai-practice-rbac-service-完整-crud-与对外边界)

<a id="ai-practice-rbac-admin-两表-rbac-与登录案例"></a>
### Admin 两表 RBAC 与登录案例

源码位于 [admin/rbac](../admin/src/main/java/com/yulinlin/admin/rbac)，请求示例位于 [admin/rbac.http](../admin/rbac.http)。案例只有 `rbac_user`、`rbac_role` 两张业务表，使用 JDK 25、MySQL 和纯无状态 `security-jwt`，不保存登录记录，未引入 Spring Security，也不同时引入 Sa-Token。

> 2026-10-21：admin 源码和相关回归用例已迁移到统一 LoginAuthenticator。AdminAuthenticationService 同时实现 LoginAuthenticator<PasswordLogin> 和 UserStore，认证名为 password；登录响应和 JWT 携带 authType。完整扩展接入见[用户模型与认证器](#ai-security-user)。本轮仅修改源码和文档，未编译、未执行测试或数据库联调。

<a id="ai-practice-rbac-启动方式与完整配置"></a>
#### 启动方式与完整配置

在 IDEA 中运行 `AdminApplication`。RBAC 组件和 Repository 默认注册，不需要额外激活 `rbac` profile。案例设置位于 [application.yml](../admin/src/main/resources/application.yml)，数据库连接继续由自己的 `dev` 或 `prod` 环境配置提供。MySQL 数据库本身应提前创建，框架启动扫描负责创建两张表及索引；如果环境配置覆盖了 `schema-packages` 或 Repository 的 `scan-packages`，应确保其扫描范围包含 RBAC 对应包。

启动环境设置 `ADMIN_RBAC_PASSWORD`，长度为 12–128 字符；可选设置 `ADMIN_RBAC_USERNAME`，默认 `admin`。不设置密码时跳过初始账号创建，接口不会变成免登录。初始账号已存在时，重启不会覆盖其密码、状态或角色。

另需设置 `ADMIN_JWT_SECRET`：至少 32 字节安全随机密钥的 Base64 字符串，不是登录密码或 AES 密钥，也不要使用 `${random.value}`。在可信环境生成后保存到环境变量或密钥管理器，不提交到仓库：

```java
byte[] key = new byte[32];
new java.security.SecureRandom().nextBytes(key);
String secret = java.util.Base64.getEncoder().encodeToString(key);
// 将 secret 安全配置为 ADMIN_JWT_SECRET；不要写入生产日志。
```

```yaml
admin:
  rbac:
    bootstrap-username: ${ADMIN_RBAC_USERNAME:admin}
    bootstrap-password: ${ADMIN_RBAC_PASSWORD:}

yulinlin:
  datasource:
    default-group: mysql
  data:
    auto-update: false
  repository:
    scan-packages:
      - com.yulinlin.admin.rbac.repository
  mysql:
    schema-mode: CREATE
    schema-packages:
      - com.yulinlin.admin.rbac.domain
    log-params: false
  security:
    jwt:
      secret: ${ADMIN_JWT_SECRET}
      issuer: yulinlin-admin
      ttl: 2h
    # 不提供 CryptoCodec，也不配置 crypto.devices，框架不接管接口加解密。
    limit:
      enabled: true
      ip-header: X-Real-IP
      trusted-proxies:
        - 127.0.0.1
        - "::1"
```

RBAC 不使用 `@Profile` 条件，登录接口、Service、Repository 和公共鉴权 AOP 随 admin 正常启动。案例默认的 Schema 扫描只包含 RBAC 实体，不会改动旧 `SysUserEntity` 的表；环境配置如果扩大扫描范围，以实际配置为准。案例使用全局默认 group；如果业务同时使用多个 group，应按[多数据源](#ai-datasources-routing)和 [Schema](#ai-datasources-schema) 专题添加明确的 `@JoinSession` 路由和对应扫描。

Admin 默认不提供 CryptoCodec，也不配置设备密钥，框架不接管报文加解密；案例 Controller 的 `@ApiCrypto(false)` 此时不会触发正文处理，可以直接发送普通 JSON。要接入框架加密，按[接口加密](#ai-security-crypto)配置设备密钥（含 `unknown`）和 CBC IV，或提供[自定义 CryptoCodec](#ai-security-crypto-codec)，保持 `crypto.advice-enabled=true`（默认）并启用 `crypto.enabled`，再移除对应 Controller 的 `@ApiCrypto(false)`（或改为 true），即可跟随全局严格加密。无注解接口也参与；保留 false 的接口兼容请求并返回明文。全局 `enabled=false` 仍兼容解密，不是完全关闭；`advice-enabled=false` 才会停用两个 MVC Advice，同时保留 Codec 供手动调用。文件、监控等接口配置排除路径；报文加密不替代 HTTPS。

登录接口每 IP 每分钟最多 10 次，使用现有 `@ApiLimit`。Nginx 的 `X-Real-IP` 只对 `trusted-proxies` 中的直接代理可信；代理不在本机时修改为实际代理地址。

<a id="ai-practice-rbac-两表模型与状态枚举"></a>
#### 两表模型与状态枚举

| 实体 | 表 | 存储字段 |
| --- | --- | --- |
| `UserEntity` | `rbac_user` | ID、用户名、密码哈希、昵称、状态、`List<String> roleCodes`、审计时间 |
| `RoleEntity` | `rbac_role` | ID、角色编码、名称、`List<String> permissions`、审计时间 |

两个集合都由 JDBC 集合编码器自动保存为 JSON 文本，业务直接调用 getter/setter，不手动 JSON 转换。用户名和角色编码有唯一索引。权限字典在 `Permission` 枚举中维护，没有权限表和两张关系表。内置 `admin` 角色持有字典中的全部权限，管理接口禁止修改或删除它；其他角色的编码创建后不可变。

`UserStatus implements SuperEnum<String>` 为状态的唯一业务定义：

| Java 枚举 | 数据库与接口编码 | 中文说明 | 可登录 |
| --- | --- | --- | --- |
| `ENABLED` | `enabled` | 正常 | 是 |
| `DISABLED` | `disabled` | 停用 | 否 |
| `LOCKED` | `locked` | 锁定 | 否 |

新建用户显式赋值 `ENABLED`。`@JsonValue`/`@JsonCreator` 与 ORM `IEnum` 编码器分别保证 HTTP 和数据库都使用稳定编码；`locked` 是管理员控制的状态，不代表已经实现自动失败锁定。用户响应同时给出 `status` 和 `statusLabel`，`/rbac/users/statuses` 可用于前端下拉选项。

用户实体直接表达集合到角色编码的关联：

```java
@JoinField(textType = TextTypeEnum.text, description = "角色编码集合")
private List<String> roleCodes;

@JoinField(exist = false)
@JoinQuery(primary = "code", value = "roleCodes")
private List<RoleEntity> roles;
```

查询用户时，框架汇总 `roleCodes`，通过 `code IN (...)` 批量查角色并分配。`roles` 只负责关联结果，不重复保存到用户表。`AdminUserDetails.from(user)` 将角色编码、权限编码分别合并成不可变 `Set`，只采纳真实存在的角色。角色表中的权限集合示例为 `["user:query", "user:update"]`。

`UserRoleProjection` 是同一用户表的部分投影，仅选择主键、审计时间和角色集合，保持 `autoSchema=false`。删除普通角色时先按 JSON 文本 LIKE 缩小范围，再通过集合 `contains` 确认，使用 setter 移除对应编码，和删除角色在同一事务提交。这样不会因以后重新创建同名角色而自动恢复旧授权；JSON 集合查询没有关系表索引，这种模型适合角色数量较少的后台。

<a id="ai-practice-rbac-登录权限和会话生命周期"></a>
#### 登录、权限和会话生命周期

职责划分如下；只定制业务必须处理的部分，不重复实现 JWT：

| 组件 | 职责 |
| --- | --- |
| `AdminAuthenticationService.authenticate` | 实现 LoginAuthenticator<PasswordLogin>，name 返回 password，requestType 返回 PasswordLogin.class；按 username 校验密码和状态，返回安全用户 DTO |
| `AdminAuthenticationService.load` | 同一 Bean 实现 UserStore；按 userId 查询最新用户及权限，拒绝停用/锁定/已删除用户，不再次校验密码 |
| `AuthController` | 先处理验证码，然后调用 ISecurityManager.login(new PasswordLogin(username, password, deviceType)) |
| `security-jwt` / 公开扩展接口 `JwtTokenCodec` | 提供无状态 Token 登录；需要定制生成与解析时替换 JwtTokenCodec，注销不撤销旧凭证 |
| 框架登录拦截器 / 权限 AOP | Bearer Token 恢复当前用户，请求结束清理；检查登录、角色和权限 |

Controller 中的核心代码（PasswordLogin 从 com.yulinlin.security.auth 导入）：

```java
authentication.validateCode(request.code());
String deviceType = request.deviceType() == null ? "web" : request.deviceType();
return R.ok(security.login(new PasswordLogin(request.username(), request.password(), deviceType)), "登录成功");
```

也可调用 `SecurityUtil.login(new PasswordLogin(username, password, deviceType))`，它与注入管理器等价。业务完成外部认证后可用 `UserIdLogin(verifiedUserId, deviceType)`，但不能向客户端开放任意 ID 登录。登录 JSON 示例：

```json
{"username":"admin","password":"你的管理员密码","deviceType":"web"}
```

`deviceType` 可不传，admin 接口默认 `web`；显式传值只接受 1～32 位字母、数字、下划线或短横线。它是登录设备分类，不是凭证，也不自动读取加密的 Device-Type 请求头。

响应为 `R<LoginResult>`，`data` 包含 `token`、`expiresAt`、`user`、`authType`。authType 为成功认证器的 name。默认 JWT 使用 HS256，只承载用户 ID 和必要登录元数据，不保存密码或角色权限。`AdminAuthenticationService.load(userId)` 每次请求查最新用户及关联角色，生成不可变 `AdminUserDetails`，不是持久化完整用户快照。角色权限变化在下一次请求生效，同一请求期间读取同一线程快照。

本案例密码登录的 authType 固定为 `password`，其他服务端代码可通过 `SecurityUtil.getAuthType()` 读取。Admin 不开放 ID 登录接口，也不接受客户端自报 authType。新架构要求 JWT 包含有效 deviceType/authType；缺少这些字段的旧 Token 需要重新登录。

账号认证和用户加载共用一个业务 Bean，不需要额外注册第二个 UserStore。登录按用户名查询，后续请求始终按 Token 内的稳定用户 ID 查询；修改用户名不会把旧 Token 错当成另一个用户名。原独立 AdminUserStore 已合并，不再保留重复组件。

前端后续请求携带 `Authorization: Bearer <token>`。JWT 按 `yulinlin.security.jwt.ttl` 到期，查询/刷新资料不续期；同一用户可以持有多个 JWT。注销和请求结束都只清理当前线程，客户端应自行删除 Token；再次提交旧 JWT 仍可通过验证，直到过期或用户不允许访问。签名密钥、issuer 等校验配置不变时，重启无需恢复登录记录，未过期 Token 仍可用。后台线程不继承身份。

admin 不声明 `ISecurityManager` Bean，框架自动提供唯一的 JWT 管理器；不再有登录 Store 或按用户撤销 API。需要注销后强制失效、踢人或在线会话管理时，改选 security-sa-token，或实现自己的 ISecurityManager；不能给纯 JWT 加一个 Redis 配置就自动获得有状态能力。选型与扩展见[JWT 与 Sa-Token 对比](#ai-security-modules-jwt-与-sa-token-如何选择)和[自定义实现](#ai-security-extension-接入外部安全框架)。

密码使用 JDK `PBKDF2WithHmacSHA256`，每次生成独立 16 字节随机盐、600000 次迭代和 256 位校验值；通过 `MessageDigest.isEqual` 比较。参数参考 [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)。数据库只保存版本、迭代数、盐和哈希组成的字符串，用户 VO、`IUserDetails` 以及日志不包含密码哈希。登录查库不加 `@JoinCache`，SQL 参数日志也默认关闭。

用户名不存在和密码错误统一提示“用户名或密码错误”，抛出 `LoginException` 并由案例异常处理器返回 HTTP 401。业务请求可携带 code，但本例未接入验证码服务，非空 code 会明确拒绝；验证码不传给框架，业务先完成验证码检查，再调用 login(new PasswordLogin(...))。

案例复用 security 公共鉴权 AOP：三个 Controller 类使用 `@ApiLogin`，登录方法通过 `@ApiPublic` 放行，其他方法默认需要登录；管理方法使用 `@ApiPerm("user:update")` 等权限声明。不再实现 AdminSecurityManager，也不再自建随机 Token、用户权限快照缓存或登录拦截器。

Permission 枚举仍是业务权限字典，角色集合和用户详情按原有规则加载；公共注解使用字符串编码，也可使用业务定义的 public static final String 常量，不能在注解参数中调用枚举 getCode()。权限精确匹配，无角色名自动越权或通配符解释。未登录/Token 失效为 HTTP 401，缺少权限为 HTTP 403；完整规则和 AOP 边界见[接口安全专题](#ai-security-authorization-鉴权声明注解)。Service 仍负责业务事务、内置管理员保护和提交后刷新，不把登录检查加到 ISecurityManager 自己的身份加载路径上。

| 操作 | 无状态 JWT 的行为 |
| --- | --- |
| 修改昵称、替换用户角色集合 | 下次请求由 UserStore 加载新资料；提交后 refresh 同一当前用户时也刷新本线程快照，Token 不变 |
| 修改角色权限、删除角色 | 不遍历在线会话，下次请求重新读取关联权限 |
| 停用、锁定或删除用户 | 后续请求被 UserStore 拒绝；JWT 本身没有撤销 |
| 重新启用用户 | 尚未过期、验签有效的旧 JWT 可以继续使用 |
| 重置密码 | 只改密码；已签发 JWT 仍有效，不会自动让所有设备退出 |
| 注销 | 清理当前线程，客户端删除 Token；服务端不记录黑名单 |
| 写事务回滚 | 不更改数据库状态，不执行提交后资料刷新 |

加载用户或权限失败时请求失败，不使用旧权限兜底。手工修改用户状态、角色或权限后，下一次请求会读到新值；经 Service 或直接改库重置密码都不会撤销 JWT。已经进入业务的请求不会被中途强制停止。删除用户后不要复用其 userId，否则旧 JWT 在有效期内可能被新身份接受。多实例使用一致的签名/校验配置和用户数据，不需要共享 Token 登录表；强制失效需求应选择有状态方案，不能将此案例宣称为在线会话管理。

<a id="ai-practice-rbac-业务写法与-http-调用"></a>
#### 业务写法与 HTTP 调用

Controller 全部使用 `@PostMapping`，参数对象集中在 `RbacRequests`，开启 `@Valid`，返回 `R<E>`；Entity、PO、VO 分开。`UserPage extends PagePo` 用 `@JoinWhere` 解析用户名 LIKE 与状态等值条件；`RolePage` 支持角色编码等值、名称 LIKE。页码、每页条数和排序字段在 Service 校验，每页最多 100 条；HTTP 查询不接受继承自 PagePo 的任意 `and/or` 字段条件，避免访问密码等内部字段。

<a id="ai-practice-rbac-service-完整-crud-与对外边界"></a>
#### Service 完整 CRUD 与对外边界

两个业务 Service 直接实现框架的 `ISuperService`，不再是只提供几个自定义方法的普通类，也不继承已经弃用的 ServiceImpl：

```java
@Service("rbacRoleService")
@Transactional
@RequiredArgsConstructor
public class RoleService implements ISuperService<RoleEntity> {
    // 通用 CRUD + 角色校验；权限由下一次请求的 UserStore 加载
}

@Service("rbacUserService")
@Transactional
@RequiredArgsConstructor
public class UserService implements ISuperService<UserEntity> {
    // 通用 CRUD + 密码、状态、角色分配等业务增强
}
```

服务端可直接调用 `findById`、`findByIdIn`、`all`、`page(PagePo)`、`insertOne/insertList`、`updateOne/updateList`、`save/saveList`、`deleteById/deleteByIdIn`。必须通过注入的 Spring Service Bean 调用，才能建立事务。案例显式声明默认写方法的转发入口，并在批量写方法统一校验；单条和 save 分支也经过相同规则，不能绕过内置管理员角色保护、用户名/角色编码不可变和提交后资料刷新。服务端直接插入 UserEntity 时，passwordHash 必须先通过 `PasswordHash.encode` 生成，不能保存明文。

HTTP 同样提供完整的列表、详情、分页、单条/批量增删改，但保留请求 DTO 与响应 VO，不直接继承 `RESTfulController` 的实体 `put/puts` 接口。原因是用户密码、角色、状态各有独立权限，直接开放 Entity 更新会绕过这些边界。普通无敏感字段的业务仍可按[完整 CRUD 案例](#ai-practice-crud-example)继承通用 Controller；RBAC 案例通过安全的 DTO 接口展示业务扩展。

批量创建/修改使用 `{"items":[...]}`，批量删除使用 `{"ids":[...]}`，每次 1–100 条；删除 ID 去重，返回实际处理条数。在一个 Service 事务内执行，数据库写入阶段失败会整体回滚；资料刷新回调在提交后执行，不撤销 JWT。批量新增和修改调用 Repository 的批量写入；删除角色先合并全部待删编码，对每个用户的角色集合只修改一次，再批量删除角色，避免同一用户关联多个待删角色时修改互相覆盖。`/users/list` 接收 UserPage，按页返回脱敏列表、最多 100 条，不是无界全表导出。用户普通更新（含批量）只修改昵称，授权、状态和密码仍走对应专用接口。

例如批量创建角色：

```http
POST /rbac/roles/createBatch
Authorization: Bearer <管理员Token>
Content-Type: application/json

{"items":[{"code":"reader","name":"只读人员","permissions":["user:query"]},{"code":"auditor","name":"审核人员","permissions":["user:query","role:query"]}]}
```

成功返回 `R<List<String>>`，data 中的 ID 顺序与 items 一致；批量修改/删除返回 `R<Integer>`，表示处理条数。请求明细与返回处理可参考 [rbac.http](../admin/rbac.http)。

修改业务由外部 Spring Bean 调用 Service，示例采用：

```java
@Transactional
@JoinSync
public void assignRoles(RbacRequests.AssignRoles request) {
    UserEntity user = requireUser(request.id());
    user.setRoleCodes(validRoles(request.roleCodes()));
    user.setUptTime(DateTime.now());
    TransactionUtil.afterCommit(() -> security.refresh(request.id()));
}
```

必须通过集合 setter 替换；`user.getRoleCodes().add(...)` 不会触发 setter 跟踪。传 `[]` 清空角色或权限集合，不能用 `null` 清空列。`TransactionUtil` 从 `com.yulinlin.data.core.transaction` 导入，只向当前框架事务注册；`@Transactional` 由默认事务适配器接入，最外层实际提交成功、旧框架上下文清理后执行回调，无框架事务直接报错。已替换案例原有的临时 `AfterCommit` 类。Repository 只声明普通查询和 CRUD，不放业务事务、不使用类级缓存覆盖认证查询。提交后的权限刷新通过 `findSnapshotById` 显式 `.autoUpdate(false)` 读取普通快照，不继续使用已经完成的原事务代理。

全部接口前缀为 `/rbac`：

| 路径（全部 POST） | 请求示例或用途 | 所需权限 |
| --- | --- | --- |
| `/auth/login` | `{"username":"admin","password":"你配置的密码","deviceType":"web"}`；deviceType 可省略，code 由业务验证 | 匿名 |
| `/auth/me`、`/auth/refresh`、`/auth/logout` | 当前用户、刷新资料、注销；无需请求体 | 已登录 |
| `/users/page` | `{"pageNumber":1,"pageSize":15,"username":"reader","status":"enabled"}` | `user:query` |
| `/users/list` | 与 `/users/page` 相同条件，只返回当前页的列表 | `user:query` |
| `/users/detail` | `{"id":"用户ID"}` | `user:query` |
| `/users/statuses` | 状态字典；无需请求体 | `user:query` |
| `/users/create` | `{"username":"reader01","password":"Example-Password-2026","nickname":"只读示例","roleCodes":["reader"]}` | `user:create` |
| `/users/createBatch` | items 数组内放置 `/users/create` 请求对象，返回用户 ID 列表 | `user:create` |
| `/users/update` | `{"id":"用户ID","nickname":"新昵称"}` | `user:update` |
| `/users/updateBatch` | `{"items":[{"id":"用户ID","nickname":"新昵称"}]}` | `user:update` |
| `/users/status` | `{"id":"用户ID","status":"disabled"}` | `user:update` |
| `/users/roles` | `{"id":"用户ID","roleCodes":["reader"]}` | `user:assign` |
| `/users/password` | `{"id":"用户ID","password":"Another-Password-2026"}` | `user:password` |
| `/users/delete` | `{"id":"用户ID"}` | `user:delete` |
| `/users/deleteBatch` | `{"ids":["用户ID1","用户ID2"]}`；不能删除当前登录账号 | `user:delete` |
| `/roles/list`、`/roles/permissions` | 角色列表、可分配权限字典；无需请求体 | `role:query` |
| `/roles/page` | `{"pageNumber":1,"pageSize":15,"name":"只读"}`，可选 code、orderList | `role:query` |
| `/roles/detail` | `{"id":"角色ID"}` | `role:query` |
| `/roles/create` | `{"code":"reader","name":"只读人员","permissions":["user:query"]}` | `role:create` |
| `/roles/createBatch` | items 数组内放置 `/roles/create` 请求对象，返回角色 ID 列表 | `role:create` |
| `/roles/update` | `{"id":"角色ID","name":"新名称","permissions":["user:query","role:query"]}` | `role:update` |
| `/roles/updateBatch` | items 数组内放置 `/roles/update` 请求对象 | `role:update` |
| `/roles/delete` | `{"id":"角色ID"}` | `role:delete` |
| `/roles/deleteBatch` | `{"ids":["角色ID1","角色ID2"]}`；同样禁止删除 admin | `role:delete` |

推荐按 [rbac.http](../admin/rbac.http) 顺序执行：管理员登录 → 创建只读角色 → 创建用户 → 用户登录 → 验证 403 → 修改角色权限 → 用原 Token 验证新增权限 → 停用账号 → 验证原 Token 返回 401。请求文件有演示密码和占位管理员密码，使用前替换；重复创建同名用户或角色会被唯一约束拒绝。

本案例于 2026-10-21 更新为 LoginAuthenticator 统一认证入口，同步迁移相关回归用例源码和 HTTP 请求示例；遵循仓库维护约定，未执行编译、打包、测试或真实 MySQL 联调，不能将提供请求示例视为已经验收通过。

---

下一步：返回[项目实践主题目录](practice/README.md)按需选择相关专题。
