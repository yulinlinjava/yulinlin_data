# 缓存 Key、失效与一致性

[文档首页](../README.md) / [缓存](README.md)

条件分区失效、表命名空间、多数据源隔离及并发边界。

## 本页导航

- [Key 规则](#key-规则)
- [失效和一致性](#失效和一致性)
- [单条件自动分区](#单条件自动分区)
- [并发与对象边界](#并发与对象边界)

## Key 规则

Key 在 `core` 中统一生成，不依赖某一种数据库最终 SQL：

```text
MurmurHash3-x64-128(
    版本
  + 路由后的 group/cluster
  + Session 类型
  + entityClass/fromClass
  + 查询类型
  + 完整 INode 元数据
  + 实际 TTL
  + 全局版本与依赖表版本
)
```

元数据直接流入128位哈希器，不构建完整中间字符串。`CacheKey` 在内存中只保存两个 `long`；Caffeine 直接使用该对象作为 Key，Ehcache/Redis 持久化或日志输出时才按需生成 `v2:` 开头的32位十六进制文本。实体反射字段通过 `ClassValue` 缓存，不会在每次查询时重新扫描、排序。

`CommandNode` 的 SQL、参数名、参数类型和值都属于 INode 元数据。因此相同 SQL 的不同参数不会共用结果；MySQL、PostgreSQL、SQLite、H2、MongoDB、Elasticsearch 以及第三方 `AbstractSession` 实现都使用同一规则。Map 参数按键排序，参数插入顺序不会造成无意义的 Key 差异。

缓存命中时不会访问数据源，因此数据库模块的 `log=true` 不会打印 SQL。排障时可将 `com.yulinlin.data.cache.QueryCache` 调整为 DEBUG，查看 `HIT`、`MISS`、`REFRESH`、等待后命中以及事务/行锁绕过等事件；日志只记录哈希 Key，不记录缓存值。需要确认物理 SQL 时使用 `CacheMode.REFRESH` 或暂时关闭该次查询缓存。

分页内部的 `count` 使用独立的逻辑 Key：普通查询只保留 `from/join + where`，因此不同页码、页大小、排序、返回字段、高亮和行锁会复用同一个总数；聚合查询还会保留 `aggregations + metrics + having`，避免不同分组统计误命中。普通列表 Key 仍然包含分页和排序。未知的第三方 Wrapper 不做字段裁剪，继续使用完整节点生成 Key，以正确性优先。

数据源 group 必须进入 Key。同一个实体和条件在 `mysql`、`pgsql` 或其他 group 中不会串缓存；同一个逻辑 group 下的负载均衡节点可以共享逻辑 Key。

## 失效和一致性

缓存始终受 TTL 约束。引入缓存 Provider 后，标准 `insert/update/delete` 未指定策略时继承 `yulinlin.cache.clear-on-write`，默认在提交成功后清理相关查询缓存：

```java
int affected = ModelUpdateWrapper
        .newInstance("mysql", patch)
        .execute();

int deleted = ModelDeleteWrapper
        .newInstance("mysql", SysUser.class)
        .eq("status", 0)
        .clearCache(false) // 明确接受 TTL 窗口内的旧结果时，关闭本次清理
        .execute();
```

写操作只提供一个请求级 API：

| API | 行为 |
| --- | --- |
| 不调用 `clearCache(...)` | 继承 Provider 配置 `yulinlin.cache.clear-on-write` |
| `clearCache(true)` | 本次写入提交成功后清理相关查询缓存，覆盖全局 `false` |
| `clearCache(false)` | 本次写入不清理查询缓存，覆盖全局 `true` |

### 单条件自动分区

分区失效是缓存 Provider 的内置能力，始终自动开启，不需要配置，也没有额外 API。只有已经调用 `cache(...)` 或使用 `@JoinCache` 的查询才会进入缓存；它不会让普通查询自动启用缓存。

当同一数据源、同一物理表的缓存查询始终使用同一个单字段等值条件时，框架会按条件值划分缓存：

```java
List<SysUser> alice = ModelSelectWrapper.newInstance("mysql", SysUser.class)
        .eq("username", "Alice")
        .cache()
        .selectList();
```

例如当前结构是 `username = ?`，更新或删除条件同样为 `username = 'Alice'` 时，提交后只让 `Alice` 分区失效，`Bob` 分区仍可命中。普通列表、分页、Count、聚合等缓存仍按表失效，不会因为分区优化而保留旧结果。

最小识别规则如下：

- 查询必须是单表、非行锁，并且有效条件只有一个 `Eq`；框架工厂产生的空 `And/Or` 包装会被忽略。
- 条件字段只识别普通列名或当前表已验证的别名前缀；JSON 路径、函数、引号字段和未知前缀直接按整表处理。
- 字段名先走实体的 `JoinField`/驼峰映射，条件值先走当前 Session 的 Coder；映射或编码结果无法安全识别时同样按整表处理。
- 更新、删除使用相同字段的单个 `Eq` 时精准失效对应值。
- 更新同时修改该条件字段、插入、自定义 SQL、多条件或无法解析的写入，会让该表当前全部分区失效。
- 如果缓存查询又使用第二个等值字段，例如先有 `username = ?`、后来又有 `id = ?`，该物理表自动升级为普通整表失效模式。
- 状态按 `Session 类型 + group + 物理表` 隔离；同表对应多个实体类时共享同一判断。

分区 Key 仍保留完整查询信息，因此不同分页、排序、投影和 TTL 不会错误命中；分区版本只负责让同一个条件值下的这些条目一起失效。没有引入缓存模块，或第三方 Provider 未实现分区能力时，会安全回退为现有整表失效。

实体查询和写入优先从过滤器增强后的 `INode`、From/Join/Store 提取实际表名，节点无法识别表时才回退到 `@JoinTable`、`@JoinTableList`。动态分表不会同时混入注解中的原始逻辑表，Key、表级 TTL 和失效范围使用最终物理表；显式 `cacheNamespaces(...)` 始终追加。原始 SQL 不做表名猜测，有动态表或隐藏依赖时须显式声明实际命名空间。自定义 SQL 没有实体或节点表信息时，启用清理会退化为清理全部 ORM 查询缓存并输出警告：

```java
ExecuteRequest<Object> write = ExecuteRequest.newInstance(
        "update sys_user set status=#{status} where id=#{id}",
        Map.of("status", 1, "id", 7)
);
write.setSession("mysql");
int affected = write.clearCache(true).execute();
```

自定义查询若没有带 `@JoinTable` 的 `fromClass`，可用 `cacheNamespaces("sys_user", "sys_role")` 声明依赖表。这样任一表失效时，该复杂 SQL、JOIN 或聚合结果都会同时失效。

延迟写队列在真正写入成功后才清理，不在入队时清理。相同 `Session + 操作类型 + fromClass` 的请求合并时使用三态规则：任一请求显式 `true`，合并组就是 `true`；全部显式 `false`，合并组才是 `false`；包含未指定且没有 `true` 时保持未指定，执行时继承全局配置。

失效采用“命名空间版本号”，不会遍历百万级 Key：普通查询 Key 携带 `Session 类型 + group + 表名` 的版本快照；单条件分区查询携带表策略版本和对应条件值版本。写入成功后只递增相关版本，旧 Key 立即不可达，随后按自身 TTL 自然回收。Ehcache 的普通 namespace 版本会持久化；分区条目在进程重启后使用新的运行期版本，不会重新命中重启前的旧分区结果。Redis 会把普通版本和分区版本都保存在共享 Redis 元数据中；关闭 L1 时，各节点每次都读取共享版本。

事务规则：

- 普通单次写入在底层 Session 提交成功后按 `clearCache` 策略清理。
- `RouteSession` 多数据源事务会合并重复表名，只在最外层提交成功后统一清理。
- 多数据源提交中途失败、结果无法确定时会保守清理待处理缓存；最多多一次回源，不保留可能已提交数据的旧缓存。
- Spring 事务在 `afterCommit` 阶段清理；回滚或提交失败不清理。
- 活动事务内的查询直接访问数据源，不读取也不写入全局查询缓存，防止未提交结果在回滚后残留；事务内使用 `CACHE_ONLY` 会直接报告缓存不可用。
- `lock()` 行锁查询始终访问数据库，不会因为 `cache()` 命中而跳过加锁。
- 查询回源期间若并发发生更新，旧查询只能写入旧版本 Key，不能重新污染新版本缓存。

即使有主动失效，也应把 TTL 设置为业务可接受的最大兜底陈旧时间。事务外要求绝对强一致的查询使用 `CacheMode.NONE`。

## 并发与对象边界

同一个 Key 回源时使用分段锁和二次检查，降低缓存到期瞬间的重复查询。缓存保存查询增强前的数据；返回给业务前会复制模型并创建当前调用需要的统一实体代理，旧事务代理不会被保存复用。

Ehcache 默认使用 JSON 字节保存结果，Redis 默认使用 Smile 字节保存结果；选择 JSON/Smile 时实体应符合 Jackson 的反序列化要求。连接、流、Statement、ResultSet 等资源对象不能作为缓存查询结果。

---

下一步：返回[缓存主题目录](README.md)按需选择相关专题。
