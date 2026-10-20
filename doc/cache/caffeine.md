# Caffeine 内存缓存

[文档首页](../README.md) / [缓存](README.md)

引入依赖、容量、淘汰和统计配置。

## 本页导航

- [Caffeine 内存缓存](#caffeine-内存缓存-1)

## Caffeine 内存缓存

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

下一步：返回[缓存主题目录](README.md)按需选择相关专题。
