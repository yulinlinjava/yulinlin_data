# Ehcache 持久化缓存

[文档首页](../README.md) / [缓存](README.md)

内存与磁盘容量、持久化及配置边界。

## 本页导航

- [Ehcache 持久化缓存](#ehcache-持久化缓存-1)

## Ehcache 持久化缓存

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

下一步：返回[缓存主题目录](README.md)按需选择相关专题。
