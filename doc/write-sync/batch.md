# 批量与多连接写入

[文档首页](../README.md) / [批量与同步](README.md)

批次大小、连接并发和事务提交是不同概念。

## 本页导航

- [批量与多连接写入](#批量与多连接写入-1)

## 批量与多连接写入

方法体片段，usersToInsert 为已准备好的 DemoUser 集合：

```java
ModelInsertWrapper.newInstance("mysql", usersToInsert).execute();         // 普通 JDBC batch
ModelInsertWrapper.newInstance("mysql", usersToInsert).batch().execute(); // 申请多连接路径
```

```yaml
yulinlin:
  mysql:
    parallel-connections: 4
    execute-batch-size: 256
  postgresql:
    parallel-connections: 2
    execute-batch-size: 128
  h2:
    parallel-connections: 4
    execute-batch-size: 256
  sqlite:
    execute-batch-size: 256
```

这些数都必须是正整数，并按数据库模块独立生效。MySQL、PostgreSQL 与 H2 默认最多 4 个连接；调用 `.batch()` 时，把整批数据均匀分成最多 4 个大组，一组一个任务/连接。同 SQL 复用 PreparedStatement，每满配置行数执行一次 executeBatch，尾批也执行。SQLite 固定单写连接，但仍可独立设置 execute-batch-size。

只有事务外 batch、执行器和 supportsParallelWrites 等条件满足才并发；SQLite 或有效并发数为 1 时不拆组。Hikari 池限制有效并发数，其他连接池应按容量设置 parallel-connections。这个值是单次请求的连接并发上限，不是应用全局上限，也不保证线性提速。

事务外 batch 使用独立自动提交连接，等待全部已派发任务完成后返回。任一任务失败则抛出首个异常，其余放 suppressed；已成功写入保留，相关缓存仍会失效。不能在任务还运行时归还连接，也不能把整体抛错理解成全部回滚。解析仍持有完整集合，不是流式导入。SUCCESS_NO_INFO 按成功命令计数，不保证精确行数。

需要整批原子性时使用显式事务：

```java
SessionUtil.route().transaction(() -> {
    ModelInsertWrapper.newInstance("mysql", usersToInsert).batch().execute();
    return null;
});
```

事务内自动固定单连接，batch 不拆线程，但 execute-batch-size 仍控制 JDBC 发送批次。全部 SQL 成功后外层提交；异常逃出范围则回滚。普通非 batch 写请求在没有外层事务时也会自动包单连接事务。仅设置 parallel-connections: 1 不会改变事务外 batch 的独立自动提交语义。

启用 data-sync 增量同步的源实体，batch 必须像上例一样包进显式事务；框架会在 before 阶段拒绝不满足条件的请求，避免部分成功后错误重放整批。

---

下一步：返回[批量与同步主题目录](README.md)按需选择相关专题。
