# RouteSession 执行链与过滤器

[文档首页](../README.md) / [内部维护](README.md)

查询、立即写入、队列及过滤器的执行边界。

## 本页导航

- [RouteSession 执行链](#routesession-执行链)
- [过滤器边界](#过滤器边界)

## RouteSession 执行链

**同步重放例外入口：** `replayWrite(targetGroup, request)` 仅供内部适配器重放已经完成业务加工的节点。强制明确目标组及 master，不走 before/after、不进入普通延迟队列，因此不重复改表名、补默认值，也不会触发下一层同步。仍由 executeNow 管理事务参与、缓存失效和具体 Session 执行/日志。普通业务 CRUD 不应调用此入口绕过过滤器，目标权限及租户隔离必须由同步配置保证。

**增量通知：** data-sync 的写后过滤器在源事务内保存独立快照，登记 TransactionUtil.afterCommit；最外层成功后才入队。队列后台真正写入和代理提交前 flush 产生的请求同样走此链路。每个请求在目标独立提交，不保留源多请求事务的原子性；失败任务不越过、不自动重试。内存容量按节点计，快照/入队失败显式暂停并报告可能缺口，不能回滚已提交的源数据。SyncTaskStore 内置没有持久化队列恢复或原子源事件记录。

**全量维护例外入口：** DataSyncClient.sync(source,target,class) 通过 RouteSession 选择 master，再借 JdbcSession 独立维护连接，不加入业务事务、不走业务过滤器或代理。FullSyncMapping 校验单表实体与实际单列主键；JdbcFullSyncJob 复用源/目标各自 CoderManager 和 Session 读取/绑定策略。每批目标业务行与 yulinlin_sync_progress 检查点同连接提交，确认成功后调用 RouteSession.invalidateMaintenanceCache 清理目标命名空间。sync 自动续传；restart 重置该任务进度，不清空目标。禁止在业务事务中调用，不能把维护连接能力用于普通 CRUD 绕开 RouteSession。

**全量/增量衔接：** 当前采用轻量方案，日常增量正常运行，全量只由人工在维护窗口调用；空规则也可仅提供人工 API。启动入口 SyncManager.start(groups) 只校验组并调度增量，不接收全量执行器，不扫描实体。启动自动全量开关和相关执行器已移除；人工全量保留独立入口，不自动协调停写。

ManualGuard 对本进程所有写向目标 group 的规则提供维护保护：进入前要求空闲且无积压/暂停，未满足直接拒绝，不等待排空；全量期间停止调度，退出时恢复正常规则调度，但不解除错误暂停。它不会阻止源业务提交或停止其他实例；业务必须先停写、结束在途事务并排空后台写任务及增量，成功核对后再恢复写入。普通全量失败也会释放维护保护，所以调用方必须继续保持停写以便续传；检测冲突时需人工修复，不能自动重放可能重复的 inc/dec。持久化检查点只属于全量任务，不等于增量已具备可靠投递；提交结果不确定时仅更新错误状态，不用内存中的旧游标覆盖数据库进度。

**查询：** `RouteSession.executeList` 应用实体路由、初选 Session、执行 before；过滤器可以改表名或指定新的 group/cluster，路由据此重选。自动更新查询要求活动事务。之后委托具体 Session 的 select/page/group/count，最后执行 after 并在 finally 恢复选库作用域。递归关联查询受路由深度限制。

**查询缓存与代理的位置：** 缓存读取、Key/TTL 处理、并发回源锁位于 `AbstractSession.getCacheValue`，不是 RouteSession。缓存未命中时，Session 执行得到 IDataBuffer，再由本 Session 的 CoderManager 解码；select/group 随后调用 `enhanceQueryResults`。缓存保存代理增强前的实体结果；缓存路径返回前复制对象，再增强关联与代理，不缓存事务绑定的代理。事务内查询和行锁查询绕过全局缓存，CACHE_ONLY 在这些场景拒绝执行。分页由 Session 分别 count 和 select，各自有缓存与实际执行边界。

**多缓存选择边界：** CacheCoreAutoConfiguration 收集 QueryCache Bean，创建唯一 CacheRegistry，并绑定 yulinlin.cache.default。无实现/无默认使用 NoOp；单实现自动选中；多实现必须指定默认，显式名称不存在及重名均报错。三个 Provider 的固定名称是 caffeine/ehcache/redis，无 group 配置。RouteSession、JDBC/ES/Mongo 工厂统一注入 registry.defaultCache，保证查询和失效使用同一实现；请求、Wrapper、JoinCache 不携带缓存选择参数，ORM 写后失效不广播其他实现。

RoutingCacheClient 仅管理缓存选择与只读清单：getCache()、getCache(name)、defaultName()、contains(name)、names()、caches()。每个实现复用一个 DefaultCache（实现 Cache），提供业务读写、泛型回源、批量操作和清理；默认与显式选择同一实现共享缓存对象及加载锁。业务 namespace 与 ORM namespace 始终隔离。Cache.clear(namespace) / clear() 不调用 ORM invalidateAll，也不清其他缓存或 Redis 整库。批量方法默认循环复用 Provider，不保证原子性，不新增线程/队列。无 Provider 时默认返回空缓存，ORM 直查数据库。注册表和客户端不接管 Provider 关闭生命周期；Redis 自带 L1 不作为独立 Provider 注册。公共 TTL 配置由各实现复用，配置只在启动时选择，不提供运行时热切换默认缓存。

CacheClient 不提供 ORM 表/实体清理入口，不依赖 ORM 注解解析或路由桥接。查询读写仍由具体 Session 完成，普通业务写入后的缓存失效仍由 RouteSession 协调事务提交；内部 CacheNamespaceResolver 和分区失效协议保留。需要刷新查询时通过 CacheMode.REFRESH，不使用业务缓存的 clear 操作代替。

**立即写入：** `RouteSession.executeNow` 没有外层事务时自动包装一次框架事务；有外层事务则直接登记参与者，不重复增加业务事务深度。先解析失效范围，再调用 Session.insert/update/delete，执行成功登记待失效范围并调用 after。最外层提交时才完成连接提交和缓存失效。`after()` 是“执行后”，不是“提交后”；需要提交成功通知时用 TransactionUtil.afterCommit。

**延迟写入：** before 在入队前执行并固定所选 Session；queue() 不允许在活动事务内使用，成功接收返回 0，不表示落库成功。WriteQueueManager 根据 Session **实例** + RequestType + fromClass 合并节点；同一 Session 内分组依次执行，不同 Session 受全局并发上限控制。当前活跃队列的节点总数达到 flush-size（默认 1024）可提前唤醒刷新，不是必须每组都满。后台执行回到 RouteSession.executeNow，各组独立事务；不重新执行 before。它不会把多次 +1 自动合为 +N，不保证跨分组的原请求顺序，也不是持久化消息队列。

**批量与事务：** AbstractSession 根据 batch()、执行器和 supportsParallelWrites 决定是否拆执行分组；具体 JDBC Session 再控制 executeBatchSize。executeBatch 是发送批次，不是提交事务。SQLite 禁止多连接写入拆组。业务自行开异步线程不会自动继承 TransactionContext；框架并行写入有自己的资源交接路径，不应直接复制为任意异步事务传播。

**统计聚合插入：** common 的 EntityCounter.insert 不进入 WriteQueueManager，而与原计数更新共用 EntityCounterManager 的定时刷新。RouteSession.resolveWriteGroup 在提交线程固定目标组，CounterInsert 缓存字段元数据，把数字转成不可变数值快照，非数字字段名/toString 构成维度，null 忽略。同组数字合并先完整校验，再原子替换行快照，溢出不能部分累加。刷新切换活动区后才通过 ModelInsertWrapper 触发 insertBefore，避免自动 ID/时间提前成为维度。每个数据源/实体分组批量插入、不主动拆多连接；失败批次保留已生成请求，不回并新窗口。标量编码器位于 core/coder；JdbcScalarTypes 仅提供原子类到基础存储类型映射，各数据库 SchemaManager 仍决定方言。内存窗口与失败队列都不持久化，不能据此承诺自然分钟对齐或 exactly-once。

**模型计数更新：** EntityCounter.update(model) 由 CounterUpdate 的 ClassValue 元数据识别唯一主键和可更新字段，终结时固定主键/数字快照，忽略非数字及零值。CounterNumbers 负责更新/插入共用的不可变数字转换，模型更新与链式更新统一以 BigDecimal 增量进入同一按组/实体/主键聚合区；同一行同步合并，整份新值计算完成后才替换。入队先解析实际数据源组，后台不依赖调用线程选库上下文。执行时通过空 IUpdateWrapper 仅生成 inc/dec 和主键条件，不走实体更新工厂，不触发 updateBefore/default 字段覆盖，再交 RouteSession 完成事务、解析、日志和缓存处理。失败增量恢复到活动区；不能承诺提交结果不确定时恰好一次。

**算术参数边界：** EntityCounter 链式 inc(x) 表示加 x、dec(x) 表示减 x，都接受正负整数、小数及现有原子数字类型。每次调用通过 CounterNumbers.snapshot 固定 BigDecimal 快照，拒绝 null/非有限浮点数/不支持的 Number 类型；Builder 和聚合器均使用 BigDecimal 合并，不再先转 long，dec 在 BigDecimal 上取负。终结时复制增量 Map，抵消为零不生成字段更新。输出参数能精确表示为 long 时保持整数，否则保留 BigDecimal；最终范围、精度由目标数据库列决定。JDBC UpdateFieldsParse 对 inc/dec 的数字使用 IParamsContext.putNumberGetKey，SimpParamsContext 保存原生数字、SqlParamsContext 负责委托，防止 BigDecimal 存储编码变成字符串后无法执行 PostgreSQL 数字加减。普通存储字段仍走原编码器，不改变表结构和数字文本存储约定；目标列必须能进行算术运算。

**事务与 sync 代理：** TransactionListenerManager 的线程上下文统一保存 depth、rollbackOnly、参与 Session、选库绑定、代理修改、缓存失效和 afterCommitTasks。嵌套提交只减深度，内层回滚标记整体 rollback-only。代理 setter 只记录修改，最外层提交前 flush，随后各参与 Session 提交自己的资源。清理旧上下文后才运行成功回调。多库/多连接是本地协调，不是 XA；已经提交的参与者不能因后续参与者失败而被保证回滚。ES/MongoDB 的资源参与标记不等于服务端原子事务。

维护定位：

| 修改需求 | 优先入口 |
| --- | --- |
| 动态表名、请求替换、数据源改路由、返回值过滤 | [RouteSession](../../core/src/main/java/com/yulinlin/data/core/session/RouteSession.java)、filter |
| 默认组、主从标签、节点选择 | [RegisterSession](../../core/src/main/java/com/yulinlin/data/core/session/RegisterSession.java)、loadbalan |
| 嵌套、提交、回滚、成功回调 | [TransactionListenerManager](../../core/src/main/java/com/yulinlin/data/core/transaction/TransactionListenerManager.java)、TransactionContext |
| JDBC 连接借还、批处理扩容、物理提交与释放 | [JdbcTransactionResources](../../jdbc/src/main/java/com/yulinlin/jdbc/session/JdbcTransactionResources.java)、AbstractJdbcSession |
| 查询缓存读取、公共批量、解码与结果增强顺序 | [AbstractSession](../../core/src/main/java/com/yulinlin/data/core/session/AbstractSession.java) |
| 关联查询、懒加载、自动更新字段跟踪 | [EntityProxyFactory](../../core/src/main/java/com/yulinlin/data/core/proxy/EntityProxyFactory.java) |
| 延迟写分组、刷新、重试 | [WriteQueueManager](../../core/src/main/java/com/yulinlin/data/core/queue/WriteQueueManager.java) |
| 数据库语法、驱动读写、时间适配 | 对应模块 ParseManager、Session、CoderManager，不在 RouteSession 加数据库分支 |

## 过滤器边界

查询与写入共用 PreparedRequest，携带最终 Request、Session 和真实入栈次数，不再用 Request.sessionChange 猜测清理次数。before 的新请求必须被采用，继承调用者路由/执行选项，同时检查 group 和 cluster。动态表名改写在 SQL 解析和缓存之前完成，fromClass 不必改变。具体 Session 的 `setFilterManager/getFilterManager` 已移除，第三方工厂也应停止配置这项依赖。

after 的新结果必须返回给调用方，按列表、Page、Integer 校验类型；IRequestFilter 对分页列表分发 selectAfter，仅更换列表时才创建保留 total/extra 的新 Page。缓存命中也执行 after，Session 内部分页调用不重复增强。延迟写 before 在入队前执行，after 在后台实际执行后触发，普通后台后处理异常不重试已执行 Node。完整规则见[过滤器](../orm/filters.md#请求与结果增强过滤器)。

SimpFilterManager 注册时复制并稳定排序，Comparator.comparingInt 避免 order 相减溢出；发布 volatile 数组快照，before/after 各自读取快照，无读锁、不创建迭代器。test 每次执行，不缓存依赖当前请求或用户的匹配结果。注册可与请求并发，但过滤器本身须无请求级共享状态。回归用例统一放在 admin/src/test/java，当前只做静态核对，未执行编译或测试。

---

下一步：返回[内部维护主题目录](README.md)按需选择相关专题。
