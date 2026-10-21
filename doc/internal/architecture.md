# 核心组件与职责

[文档首页](../README.md) / [内部维护](README.md)

core 组件、协议和业务边界，修改框架前先读。

## 本页导航

- [架构与职责](#架构与职责)

## 架构与职责

core 是统一的数据访问与执行协调层。业务入口是 `RouteSession`，不是各数据库 Session；但不能把 RouteSession 理解为所有功能的具体实现。以下描述对应当前源码，供维护者和修改框架的 AI 定位职责。

```text
业务 → Model / Wrapper → INode → QueryRequest / ExecuteRequest
  → RouteSession：初选节点 → before → 按最终请求调整路由
  → EntitySession / AbstractSession：公共执行流程
      → ParseManager + 编码 Buffer → SQL / SDK 请求
      → 查询缓存命中，或调用数据库执行器 → 解码为实体
      → 查询结果关联加载、代理增强
  → RouteSession：after → 返回

写入事务由 RouteSession → TransactionListenerManager 协调；
实际连接、SQL / DSL 和驱动行为由数据库模块负责。
```

| 层 | 职责 | 不应承担 |
| --- | --- | --- |
| Model/Wrapper/IWrapperFactory | 链式 API、实体与条件对象转操作节点 | 获取连接、执行数据库操作 |
| anno/node/alias/util | 表、字段、条件、关联、排序和聚合的通用元数据与节点 | 绑定数据库专属语法 |
| BaseRequest/QueryRequest/ExecuteRequest | 携带节点、fromClass、结果类型、group、缓存、批量和队列选项；便利方法委托 SessionUtil.route() | 独立管理连接或物理事务 |
| ParseManager/IParse | 节点注册、生成 SQL/SDK 请求 | 借连接或提交事务 |
| CoderManager/IDataBuffer | Java 与后端值转换 | 选库和连接池治理 |
| EntitySession | 数据源执行、资源事务及 Schema 协议 | 承诺所有后端都支持相同事务、并发或建表能力 |
| AbstractSession | 公共查询/分页/批量流程、查询缓存、解码、结果增强、计时和监听通知 | 执行路由过滤器、独立开启业务事务、反向调用事务管理器 |
| 具体 Session | SQL/SDK 执行、参数绑定、读取、本数据源连接资源和日志格式 | 复制没有差异的公共 CRUD 流程 |
| SessionFactory | 创建 Session，初始化缓存、编码器、解析器、代理服务、可选执行监听器、配置和 group | 默认承担路由注册或建表；注册日志管理器 |
| RouteSession | 唯一业务 CRUD 入口、过滤器、会话选择、事务协调、队列接入与写后缓存失效决策 | 持有另一份事务上下文、翻译方言、执行物理 commit 或实现 XA |
| IFilterManager/SimpFilterManager | 注册、排序、匹配、顺序执行过滤器 | 选择数据源、执行 SQL 或管理事务 |
| TransactionListenerManager | 统一业务上下文、嵌套深度、代理写回、资源完成与回调 | 直接操作 JDBC Connection 或实现 XA |
| JdbcTransactionResources（jdbc 模块） | 单个 Session 事务内的连接借还、按需扩容、物理提交/回滚与释放 | 全局连接池、业务事务嵌套、路由或 Spring 连接归属判断 |
| RegisterSession/LoadBalance | Session 注册、选库作用域、默认组、cluster 和权重选择 | 数据源 URL 校验、执行 CRUD 或保证事务一致性 |
| EntityProxyService/EntityProxyFactory | 关联加载、同步代理生成与脏字段记录 | 再维护一套事务 ThreadLocal 或自行提交连接 |
| WriteQueueManager | 延迟接收节点、触发刷新、分组和重试调度 | 持久化消息、分布式队列、自动推断业务聚合语义 |
| EntityCounterManager / CounterUpdate / CounterInsert（common） | 链式或模型按主键聚合更新，或按非数字维度聚合插入；提交快照、数字求和、定时刷新与失败批次保留 | 分布式统计、严格分钟窗口、exactly-once；不能绕过 RouteSession |
| cache-core | 缓存 SPI、CacheRegistry 注册/默认选择、CacheClient 命名路由、Key/TTL 与 namespace/partition 模型 | 解析 INode、Wrapper 或数据库表注解；自动串联多个 Provider |
| core/cache | ORM 查询 Key 构造、表依赖解析和写后失效决策 | 实现 Caffeine、Ehcache 或 Redis 存储 |
| core/http：HttpRequestClient / HttpRequest / HttpRequestData | 统一调用入口、请求快照、默认配置和 404 策略；HttpUtil 绑定 Spring 选定的客户端 | 直接实现网络调用、绕过用户 HttpExecutor Bean |
| HttpExecutor / RestClientHttpExecutor | 可替换的 HTTP 执行协议及默认适配；连接复用、流式 I/O、超时和资源释放；规范见[HTTP 执行器](../tools/http-executor.md) | ORM 事务、Session 路由；默认开启重试 |
| SchemaEntityScanner | 包扫描和结构拥有者校验 | 生成各数据库 DDL、在每次 CRUD 前建表 |
| SchemaManager / SchemaColumnAlter（jdbc） | 数据库元数据与 DDL；显式开启 alter-column-type 且 CREATE 时受限改类型/扩容，执行委托所属 Session | 自动确认业务数据可转换、删约束促成转换、跨表原子迁移或数据补偿 |
| SchemaErrors（jdbc） | 统一中文结构诊断、原因和处理建议；包装失败 DDL，保留驱动异常链及错误码 | 决定兼容性、执行 SQL、自动修复结构或吞掉异常 |
| SyncRequestFilter / SyncManager（data-sync） | 匹配源组和实体、保存节点快照、afterCommit 入队、顺序消费、失败暂停及全量维护保护 | CDC、源事务 Outbox、自动对账、分布式一致性 |
| DataSyncClient / JdbcFullSyncJob（data-sync） | MySQL/PostgreSQL 单表主键分页复制、目标本地检查点续传、中文进度 | 普通业务 CRUD、在线快照、目标额外行删除、可靠增量交付 |
| SessionExecution/SessionExecutionListener | 单次数据源执行结果、可选监控扩展点 | 判断事务提交成功、实现监控页面或反向修改请求 |
| AOP/YulinlinCoreAutoConfig | 注解入口与组件装配 | 实现订单、权限等业务规则 |
| ISecurityManager / SecurityUtil / SecurityContext（security） | 唯一登录管理器、静态委托入口与线程身份；统一 loginByUsername(username, password, deviceType)，resolveSession 恢复 LoginSession；公开 login(userId) 已移除 | 仅凭用户 ID 建立登录、公共层自行比较密码/验证码、强制账号表结构、多实现同时登录、跨线程继承身份 |
| UserAuthenticator / AbstractUserSecurityManager（security） | 业务 Bean 校验账号密码及状态，返回安全 IUserDetails；JWT/Sa-Token 共用认证模板，通过 protected createLogin 使用真实 userId 建立登录 | UserStore 代替密码校验、公开 createLogin、将 username 当 ID、认证实现递归调用登录 API、保存密码到上下文 |
| UserStore（security） | 业务 Bean 按已验证 Token/会话内的稳定 userId 加载安全用户、角色权限，缺失/禁用时拒绝访问；可与 UserAuthenticator 共用同一 Bean（admin 即此方式） | 把 userId 当 username 查询、重新验证登录密码、生成 Token、维护登录记录、把凭证带入用户 DTO |
| JwtSecurityManager / JwtTokenCodec（security-jwt） | 无状态签发、验签、时间检查，再按 userId 调用 UserStore；logout 仅清理线程，Codec 可由业务 Bean 替换 | 保存登录记录、Token 黑名单、主动撤销、把客户端删除 Token 当成服务端失效 |
| SecurityContextResolver（security） | 可选的外部身份桥接；仅启用选定管理器的协议，读取时校验身份，失败清理旧快照；cacheContext 控制是否保留线程快照（Spring 不保留，Sa-Token 默认保留） | 新建登录记录、信任请求中的用户 ID、解析时递归读取 SecurityContext |
| SaTokenSecurityManager / SaTokenLoginInterceptor（security-sa-token） | 委托 Sa-Token 会话与原生 Token 规则；桥接 StpUtil.login/logout，读取时检查当前身份、同请求复用 UserStore 资料，兼容既有 Bearer 调用 | 自动监听业务改密、把默认内存当成持久化存储、混用两套登录管理器；默认不桥接其他独立 StpLogic/switchTo |
| SpringSecurityManager / SpringUserMapper / SpringLoginInterceptor（security-spring） | 桥接 Authentication；loginByUsername 委托 AuthenticationManager Bean，SpringLoginDetails 传设备，SessionAuthenticationStrategy + SecurityContextRepository 保存 Session 身份；logout 委托当前 Spring Servlet 包装请求 | 另建用户表/FilterChain/Token、默认支持按 ID 登录、把 credentials/Session ID 当 Token、自动复制链内私有策略/MFA/Remember-Me；原生过滤器链负责恢复和清理 Spring 上下文 |
| LoginInterceptor / LoginContextInterceptor / ApiAuthAspect（security） | 自定义 LoginInterceptor Bean 优先，否则自动提供默认实现，无启用开关；MVC 包装器清理 SecurityContext，AOP 通过 ISecurityManager 校验登录/角色/权限 | 替自定义拦截器验证凭证、清理外部框架上下文、替代会话存储；外部身份生命周期由适配实现负责 |
| core/http | 独立 HTTP 客户端工具 | 参与 ORM 路由、事务或数据库监听链 |

业务 CRUD 必须经过 RouteSession，Model Wrapper、Request 便利方法和事务切面均接入这一入口。具体 Session 是执行器和资源参与者，不作为独立业务事务入口；建表 SQL 预览、启动 Schema 初始化和解析器专项用例仍可以直接使用其底层能力。

启动 Schema 的 `alter-column-type` 默认关闭，放在 JdbcSessionProperties，由各数据库配置前缀独立绑定。MySQL/PostgreSQL/H2 在初始化时读取实际元数据、检查主键/生成列/外键等限制，再生成改列 SQL；Session 负责连接、DDL 日志和异常传播。VALIDATE 永远不修改，SQLite 不提供自动改类型。默认值和索引无法由数据库安全保留时停止启动，不自动删除重建；多条 DDL 不保证整体回滚，具体限制与人工备份要求见[改列说明](../datasources/schema.md#显式允许修改列类型)。

结构诊断按本次 SchemaMode 与改列开关给出建议：VALIDATE 只校验，主键/索引冲突须人工处理，SQLite 不支持自动改类型。Session 保留 DDL 日志职责，SchemaErrors 只负责诊断文本。不要将驱动原始错误强行翻译或丢弃，也不要将 DDL 失败描述为“已全部回滚”；使用规则见[表结构错误排查](../datasources/schema.md#表结构错误排查)。

---

下一步：返回[内部维护主题目录](README.md)按需选择相关专题。
