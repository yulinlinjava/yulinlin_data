# ORM 查询缓存

[文档首页](../README.md) / [缓存](README.md)

cache()、CacheMode、默认 TTL 和查询行为。

## 本页导航

- [启用查询缓存](#启用查询缓存)
- [CacheMode](#cachemode)

## 启用查询缓存

`cache()` 不传参数时使用系统默认值 `yulinlin.cache.ttl`，默认 10 分钟；`cache(Duration)` 只覆盖当前查询：

```java
List<SysUser> users = ModelSelectWrapper
        .newInstance("mysql", SysUser.class)
        .cache() // 使用 yulinlin.cache.ttl
        .where(where -> where.eq("status", 1))
        .selectList();

List<SysUser> shortLived = ModelSelectWrapper
        .newInstance("mysql", SysUser.class)
        .cache(Duration.ofSeconds(30))
        .where(where -> where.eq("status", 1))
        .selectList();
```

自定义 SQL 同样支持缓存：

```java
QueryRequest<SysUser> request = QueryRequest.newInstance(
        "select * from sys_user where status = #{status}",
        Map.of("status", 1),
        SysUser.class
);
request.setSession("mysql");
request.cache(); // 默认 TTL；也可 cache(Duration.ofSeconds(30))
List<SysUser> users = request.selectList();
```

列表、分页内部的 count、group、统计结果和空列表都可以进入缓存。查询异常不会缓存。级联查询、批量预加载和懒加载会继承入口查询的缓存模式与请求级 TTL。

同一条查询使用不同 TTL 时会生成不同物理 Key，互不覆盖。默认 `after-write` 下读取不会续期；Provider 配置为 `after-access` 时，命中会按该条目的 TTL 重新计时。

## CacheMode

`CacheMode`明确控制一次查询是否读缓存、访问数据源和写缓存：

| 模式 | 读缓存 | 未命中访问数据源 | 写缓存 |
| --- | --- | --- | --- |
| `NONE` | 否 | 是 | 否 |
| `CACHE_ONLY` | 是 | 否 | 否 |
| `READ_THROUGH` | 是 | 是 | 是 |
| `REFRESH` | 否 | 是 | 是 |

```java
// cache() 等价于 READ_THROUGH
List<SysUser> cached = ModelSelectWrapper.newInstance("mysql", SysUser.class)
        .cache(CacheMode.READ_THROUGH)
        .selectList();

// 只允许读取已有缓存；未命中抛出 CacheMissException，绝不查询数据库
List<SysUser> offline = ModelSelectWrapper.newInstance("mysql", SysUser.class)
        .cache(CacheMode.CACHE_ONLY)
        .selectList();

// 强制查询数据库并覆盖缓存
List<SysUser> refreshed = ModelSelectWrapper.newInstance("mysql", SysUser.class)
        .cache(CacheMode.REFRESH, Duration.ofMinutes(5))
        .selectList();
```

未调用 `cache(...)` 时为 `NONE`。没有引入缓存 Provider 时，`READ_THROUGH`和`REFRESH`仍能查询数据源，但不会保存结果；`CACHE_ONLY`一定抛出 `CacheMissException`。

---

下一步：返回[缓存主题目录](README.md)按需选择相关专题。
