# 轻量缓存与资源池

[文档首页](../README.md) / [常用工具](README.md)

ExpiryMap、SmartCache 与 ResourcePool，不等同于 ORM 缓存。

## 本页导航

- [轻量缓存与资源池](#轻量缓存与资源池-1)
- [ExpiryMap](#expirymap)
- [SmartCache](#smartcache)
- [ResourcePool](#resourcepool)

## 轻量缓存与资源池

这些类不替代 [ORM 查询缓存](../cache/query.md)。它们只管理当前 JVM 内的普通对象。

### ExpiryMap

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

### SmartCache

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

### ResourcePool

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

下一步：返回[常用工具主题目录](README.md)按需选择相关专题。
