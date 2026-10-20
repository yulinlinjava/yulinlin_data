# 缓存序列化

[文档首页](../README.md) / [缓存](README.md)

格式选择、兼容性与失败处理。

## 本页导航

- [序列化格式选择](#序列化格式选择)

## 序列化格式选择

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

下一步：返回[缓存主题目录](README.md)按需选择相关专题。
