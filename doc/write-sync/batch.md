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

只有 .batch()、执行器、请求阈值和 supportsParallelWrites 等条件满足才并发；SQLite 或有效并发数为 1 时不拆组。Hikari 池会自动限制有效并发数，其他连接池应按其容量设置 parallel-connections。H2 可拆组，但多个连接仍受文件锁、索引和写入热点影响。4 是每 Session、每框架事务的上限，不是整个应用并发上限，也不保证 4 倍速度。

executeBatch 不是 commit。所有已提交任务结束后再提交/回滚及释放；失败不能让工作线程继续在已归还连接上执行。解析仍持有完整输入集合，不是流式导入。SUCCESS_NO_INFO 按成功命令计数，不保证精确行数。

多个连接有各自的本地事务，中途提交失败无法撤销已成功提交的连接；跨连接未提交数据也不保证可读。严格单库原子性和事务内读己之写使用 parallel-connections: 1。

---

下一步：返回[批量与同步主题目录](README.md)按需选择相关专题。
