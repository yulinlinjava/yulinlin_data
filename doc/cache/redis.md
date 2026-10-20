# Redis 与可选本地缓存

[文档首页](../README.md) / [缓存](README.md)

Redis 连接、L1 开关、同步与异步写入。

## 本页导航

- [Redis 共享缓存与可选 Caffeine L1](#redis-共享缓存与可选-caffeine-l1)

## Redis 共享缓存与可选 Caffeine L1

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

下一步：返回[缓存主题目录](README.md)按需选择相关专题。
