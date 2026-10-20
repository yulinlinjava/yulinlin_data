# 项目开发规范

[文档首页](../README.md) / [项目实践](README.md)

分层、事务、缓存、日志、批量和上线前检查。

## 本页导航

- [核心规则](#核心规则)
- [推荐目录结构](#推荐目录结构)
- [事务与自动更新](#事务与自动更新)
- [推荐写法](#推荐写法)
- [缓存规范](#缓存规范)
- [日志规范](#日志规范)
- [多数据源规范](#多数据源规范)
- [批量写入规范](#批量写入规范)
- [Schema 管理规范](#schema-管理规范)
- [常见反例](#常见反例)
- [上线前评审清单](#上线前评审清单)

## 核心规则

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

## 推荐目录结构

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

## 事务与自动更新

### 推荐写法

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
- 默认只对 RuntimeException/Error 回滚，受检异常也需回滚时写 `@Transactional(rollbackFor = Exception.class)`。目前只支持 REQUIRED 及默认隔离/超时、非只读语义，完整边界见[事务注解兼容](../relations/transactions.md#spring-事务注解兼容)。
- 必须通过 Spring Bean 从外部调用 public 方法；`this.changeProfile(...)` 式自调用会绕过 Spring AOP。
- 不要将代理 Entity 传给异步任务、虚拟线程或事务外的 Controller。它只能在创建它的原线程和原事务中 setter。
- 业务失败让异常离开事务方法。如果必须捕获，应继续抛出或明确设置 rollback-only，不要吞掉后继提交。
- 只读方法不加 `@JoinSync`。全局 `yulinlin.data.auto-update=true` 适合对边界非常一致的项目；一般项目优先显式方法注解。
- Repository 方法可使用 `@JoinSync` 只增强特定查询，但仍需外层 Service 活动事务。推荐默认放在 Service，避免事务和更新意图分散。

自动更新跳过 null，`setX(null)` 不表示把列清为 SQL NULL。如果业务必须清空列，使用参数化自定义 SQL；不要利用字符串拼接绕过参数绑定。

## 缓存规范

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

## 日志规范

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

## 多数据源规范

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

## 批量写入规范

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

## Schema 管理规范

- 生产项目默认使用版本化 DDL 迁移；框架 `CREATE` 模式更适合本地数据库、新项目和可控表。
- 只有拥有全部持久化列的 Entity 设置 `autoSchema=true`。投影 DTO、Join 模型和统计模型保持 false。
- 自动 Schema 可创建表、缺失普通列和声明索引，不会安全推断字段改名、类型变更、删列、外键或数据转换。
- 先用 `EntitySession.createTableSql(Entity.class)` 审查 DDL，再在目标环境启用自动初始化。所有执行的 Schema SQL 都应在日志中可见。

## 常见反例

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

## 上线前评审清单

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

下一步：返回[项目实践主题目录](README.md)按需选择相关专题。
