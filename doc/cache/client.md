# CacheClient 业务缓存

[文档首页](../README.md) / [缓存](README.md)

选择缓存对象，读写、回源、批量和清理。

## 本页导航

- [业务缓存：CacheClient 管理，Cache 操作](#业务缓存cacheclient-管理cache-操作)

## 业务缓存：CacheClient 管理，Cache 操作

框架始终暴露一个可注入的 `CacheClient` Bean，仅负责选择、查看缓存。通过它获取 `Cache` 对象后进行业务数据读写。两个接口位于 `com.yulinlin.data.core.cache`，不要与 Spring 的同名 Cache 接口混用。业务缓存与 ORM 使用独立的 Key 和版本域，数据库写操作不会误删验证码、接口结果等业务缓存。

```java
@Resource
private CacheClient cacheClient;

Cache cache = cacheClient.getCache();
cache.set("user", "10001", user); // 使用系统默认 TTL
cache.set("user", "10002", user, Duration.ofMinutes(30));

SysUser cachedUser = cache.get("user", "10001", SysUser.class);
boolean exists = cache.exists("user", "10001");
cache.remove("user", "10001");
cache.clear("user"); // 整个业务命名空间立即失效
cache.clear();       // 只清理当前实现的业务缓存，不清理 ORM 查询缓存
```

选择指定实现，不改变全局默认缓存：

```java
Cache local = cacheClient.getCache("caffeine");
Cache redis = cacheClient.getCache("redis");
local.set("user", "10001", user);
redis.set("user", "10001", user, Duration.ofMinutes(5));

SysUser loaded = redis.getOrLoad(
        "user", "10003", SysUser.class, Duration.ofMinutes(5),
        () -> loadUser("10003")); // loadUser 是业务自行提供的方法

String defaultName = cacheClient.defaultName();
boolean hasRedis = cacheClient.contains("redis");
Set<String> names = cacheClient.names();
Map<String, Cache> caches = cacheClient.caches(); // 只读视图
```

单实现不必配置默认值；多实现必须配置 `yulinlin.cache.default`。每个实现复用一个 `Cache` 对象及其加载锁，默认选择与显式选择同一实现得到同一个对象。名称不存在直接报错；`contains` 对不存在、null 或空白名称返回 false。不提供运行时注册、删除或切换默认值。

`Cache.name()` / `available()` 描述当前缓存；`clear(namespace)` 仅清当前实现的对应业务命名空间，`clear()` 仅清当前实现的全部业务缓存。都不会广播到其他实现，不执行 Redis FLUSHDB，也不清 ORM 缓存。

API 迁移：旧 `cacheClient.get/set/...` 改成 `cacheClient.getCache().get/set/...`；旧 `named(name)` 改成 `getCache(name)`；旧命名空间 `invalidate(namespace)` 改成缓存对象上的 `clear(namespace)`。不再保留管理对象上的数据操作方法，避免职责混淆。

集合等泛型数据使用 Jackson `TypeReference`：

```java
List<SysUser> users = cache.get(
        "user-list", "enabled",
        new TypeReference<List<SysUser>>() {}
);
```

`getOrLoad`支持 Class 和 TypeReference，均有默认 TTL 和指定 TTL 两种重载。单进程同一缓存对象内使用分段锁和二次检查，不保证跨进程只回源一次；加载结果为 null 时不写入缓存，加载异常向调用方传递：

```java
SysUser loadedUser = cache.getOrLoad(
        "user", userId, SysUser.class, Duration.ofMinutes(10),
        () -> userService.findById(userId)
);

List<SysUser> users = cache.getOrLoad(
        "user-list", "enabled", new TypeReference<List<SysUser>>() {},
        () -> loadEnabledUsers());

cache.setAll("user", Map.of("10001", user1, "10002", user2));
cache.setAll("user", Map.of("10003", user3), Duration.ofMinutes(5));
Map<String, SysUser> hits = cache.getAll(
        "user", List.of("10001", "10002"), SysUser.class);
cache.removeAll("user", List.of("10001", "10002"));
```

批量操作当前复用逐条 API，不新增线程或队列，不保证原子性；中途失败可能已有部分生效。`getAll` 只返回命中的条目。Redis 原有异步写入模式继续有效，批量方法返回不等于所有 Redis 写入均已完成。

业务缓存不接受空 namespace、空 key 或 `set(..., null)`。不传 TTL 或传 null 使用配置默认 TTL。不引入任何缓存实现时，ORM 正常启动并直接访问数据库；`getCache()` 返回空缓存，`available()` 为 false，读取未命中，写入/清理无操作，`getOrLoad` 正常执行加载函数。此时 `defaultName()` 返回 null，`names()` / `caches()` 为空；显式 `getCache("redis")` 仍报错。

`CacheClient` 不提供按表、实体或全部 ORM 缓存的手动清理入口，也不解析数据源和实体注解。ORM 写入后的失效由 `clear-on-write` / `clearCache(true)` 控制；需要刷新某条查询时使用 `.cache(CacheMode.REFRESH)`。绕过框架直接修改数据库不会自动触发 ORM 缓存失效，需要等待 TTL 或针对受影响查询刷新。业务缓存的 `Cache.clear(...)` 不能用于清理 ORM 查询缓存。

---

下一步：返回[缓存主题目录](README.md)按需选择相关专题。
