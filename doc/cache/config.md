# 缓存选择与公共配置

[文档首页](../README.md) / [缓存](README.md)

可选实现、默认缓存选择、TTL 和启动规则。

## 本页导航

- [公共配置](#公共配置)
- [选择实现](#选择实现)
- [启动规则](#启动规则)

## 公共配置

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

## 选择实现

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

## 启动规则

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

下一步：返回[缓存主题目录](README.md)按需选择相关专题。
