# 延迟写队列

[文档首页](../README.md) / [批量与同步](README.md)

请求快照、分组、定时或阈值刷新及失败边界。

## 本页导航

- [延迟写队列](#延迟写队列-1)

## 延迟写队列

适合任意插入、更新和删除的延后批量执行。它只合并请求节点，不会把相同主键的重复数值增量先求和；播放量、点击量、热度等高频计数优先使用 [EntityCounter.submit()](counter.md)。三种普通写操作使用同一个入口：

```java
ModelInsertWrapper.newInstance("mysql", users).queue().execute();
ModelUpdateWrapper.newInstance("mysql", user).queue().execute();
ModelDeleteWrapper.newInstance("mysql", User.class)
        .eq(User::getId, id)
        .queue()
        .execute();
```

`execute()` 在节点成功进入内存队列后立即返回 `0`，不代表数据库影响了零行，也不返回自增主键。Wrapper 创建时已经把对象当时的字段值生成到 `INode`；入队后重新给实体字段赋值不会改变这次写入，Node 中原本保存的 List、Map、Date 等可变值引用则不要再原地修改。普通请求不调用 `.queue()`，仍按原方式立即执行。

```yaml
yulinlin:
  data:
    queue:
      flush-interval: 1m
      flush-size: 1024
      concurrency: 4
      retry-count: 3
```

`flush-size` 按当前活动队列的 Node 总数计算，不按 Wrapper 调用次数，也不按单个分组计算。总数达到阈值时会立即异步刷新当前整个队列快照，不必等待 `flush-interval`；例如多个分组各有数百个 Node，合计达到默认的 `1024` 也会立即刷新。刷新快照再按实际 `EntitySession` 实例、请求类型和 `fromClass` 统一分组；该阈值不拆分请求，也不改变事务边界。

不同 `EntitySession` 最多按 `concurrency` 并发刷新，同一 `EntitySession` 内的分组仍依次执行；单个分组调用过 `.batch()` 时，仍由该数据源的 `parallel-connections` 决定组内多连接。因此 `concurrency=4` 是同时处理的 Session 上限，不是全局物理连接上限。某组失败只重试该组，不阻塞其他 Session；写入成功后才按 `clearCache` 规则清理查询缓存。队列合并同组请求时，任一请求显式 `clearCache(true)` 就清理；全部显式为 `false` 才不清理；其余组合继续继承全局配置。

这是进程内队列，应用异常退出可能丢失尚未刷新数据，不替代消息队列。队列没有硬容量上限；如果持续生产速度高于数据库写入速度，待执行和待重试 Node 会继续占用内存，应在业务入口限流并监控积压。`.queue()` 面向零散小写入的定时聚合，不是大数据导入器；大集合直接在业务异步任务中调用 `.batch().execute()`。`.queue()` 必须在事务外调用，因为延迟任务不能加入调用线程的原事务；依赖严格先后关系、自增 ID、实时影响行数或强一致读取的写操作继续同步执行。应用正常关闭时框架会尝试最后刷新一次；数据库失败按 `retry-count` 在后续刷新中重试，超过次数后记录错误并丢弃。

队列不会为每个入队对象打印日志。开启 `com.yulinlin.data.core.queue.WriteQueueManager=DEBUG` 后，每个非空刷新快照输出一条摘要，包括触发方式、Session/分组/Node 数、影响行数、耗时和剩余积压；失败、重试和最终丢弃仍分别使用 WARN/ERROR。

---

下一步：返回[批量与同步主题目录](README.md)按需选择相关专题。
