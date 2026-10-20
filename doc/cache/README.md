# 缓存

[返回文档首页](../README.md)

按需选择实现，区分 ORM 查询缓存和业务缓存。

## 按需阅读

| 小专题 | 解决什么问题 |
| --- | --- |
| [缓存选择与公共配置](config.md) | 可选实现、默认缓存选择、TTL 和启动规则。 |
| [Caffeine 内存缓存](caffeine.md) | 引入依赖、容量、淘汰和统计配置。 |
| [Ehcache 持久化缓存](ehcache.md) | 内存与磁盘容量、持久化及配置边界。 |
| [Redis 与可选本地缓存](redis.md) | Redis 连接、L1 开关、同步与异步写入。 |
| [缓存序列化](serialization.md) | 格式选择、兼容性与失败处理。 |
| [ORM 查询缓存](query.md) | cache()、CacheMode、默认 TTL 和查询行为。 |
| [CacheClient 业务缓存](client.md) | 选择缓存对象，读写、回源、批量和清理。 |
| [缓存 Key、失效与一致性](invalidation.md) | 条件分区失效、表命名空间、多数据源隔离及并发边界。 |

## 建议顺序

[公共配置](config.md) → 按需选择 [Caffeine](caffeine.md)、[Ehcache](ehcache.md) 或 [Redis](redis.md) → [查询 API](query.md) → [失效与一致性](invalidation.md)。只用业务缓存 API 时读 [CacheClient](client.md)，不必先学 ORM 缓存。

只维护小专题正文；本页只提供导航。给 AI 使用时先提供 [llms.txt](../../llms.txt)，再按任务加载相关页面。
