# SQL 与事务日志

[文档首页](../README.md) / [监控与排障](README.md)

查看 SQL、参数、批量耗时和 txId，排查实际提交。

## 本页导航

- [JDBC 执行日志](#jdbc-执行日志)
- [事务诊断日志](#事务诊断日志)

## JDBC 执行日志

MySQL、PostgreSQL、SQLite 和 H2 都继承同一组日志配置，前缀替换为对应模块名即可：

- `log` 默认 `false`，只控制真正访问数据库后的成功日志；缓存命中没有执行 SQL，因此不会伪造 SQL 日志。
- `log-params` 默认 `false`。开启后打印绑定参数，但长值最多保留 `max-param-length` 个字符；字节数组和输入流只记录类型、长度等摘要。
- `max-param-length` 默认 `256`，必须为正整数。
- 日期时间参数（`LocalDateTime`、框架 `DateTime`、`Date`/`Timestamp`）在日志中统一显示为 `yyyy-MM-dd HH:mm:ss`，不显示 `T` 或小数秒；只格式化日志，不改变实际绑定对象、数据库精度或时区规则。`Date` 按 JVM 本地时区展示，纯日期/时间类型和普通字符串保持原有格式。
- `slow-sql-threshold` 默认 `0`（关闭）。配置为 `1s` 后，框架侧耗时达到 1 秒的执行以 `WARN` 输出，即使 `log=false` 也会记录。
- SQL 异常始终以单条 `ERROR` 输出，包含实际耗时并携带原始堆栈。批量写入按执行组打印一条摘要；摘要列出最多 5 种 SQL 模板及模板总数，不再为每一行打印 INFO。

SQL 日志指标使用中文：`事务ID`、`数据源`、`节点`、`操作`、`总耗时`、`操作数量`、`影响行数`、`SQL模板数`、`SQL`、`参数`。成功批次（含慢批次）额外展示 `平均每条耗时`，按原始纳秒耗时 ÷ 操作数量换算为毫秒，保留三位小数，是每条操作的分摊值而非逐条实测。失败批次不展示平均耗时和影响行数。驱动不提供精确影响行数时，框架可能按成功命令计数，不能把该值一律视为精确修改行数。

```text
SQL批量执行成功 事务ID=12 数据源=mysql 节点=master 操作=insert 总耗时=240ms 平均每条耗时=0.240ms 操作数量=1000 影响行数=1000 SQL模板数=1
SQL=["insert into ..."]
参数=[...]
SQL批量执行失败 事务ID=12 数据源=mysql 节点=master 操作=insert 总耗时=240ms 操作数量=1000 SQL模板数=1
SQL=["insert into ..."]
参数=[...]
```

普通、批量、慢操作和失败 SQL 日志统一分行：首行摘要，下一行 SQL，开启 log-params 时再输出一行参数；仍是一次 logger 调用，不拆为多个日志事件。参数只在 log-params 开启时输出；批量仍仅取首个请求的参数，最多 20 项并限制每项长度，标签统一为“参数”，不代表打印整个批次所有值。并行拆组时各组单独计时，不能将各组耗时相加当作请求总耗时；此处不额外输出请求汇总日志。

耗时是应用侧观察值，可能包含获取连接、JDBC 驱动、参数绑定和结果读取，并不等同于数据库服务器内部的纯执行时间。写入成功日志在 JDBC 执行返回后打印，不代表外层事务已经提交；事务结果应查看下面的事务诊断日志。查询缓存的 `HIT/MISS/REFRESH/BYPASS` 使用 `com.yulinlin.data.cache.QueryCache` 的 DEBUG 日志；延迟写队列刷新摘要使用 `com.yulinlin.data.core.queue.WriteQueueManager` 的 DEBUG 日志。需要排查缓存时可临时提高对应 logger 级别，或者使用 `.cache(CacheMode.REFRESH)` 强制访问数据库。

## 事务诊断日志

怀疑“SQL 返回影响行数，但数据未提交”时，临时开启全局开关。此配置对全部数据源生效，独立于 `yulinlin.mysql.log` 等 SQL 开关，默认 `false`：

```yaml
yulinlin:
  data:
    transaction-log: true

# 项目根日志级别高于 INFO 时，需要同时放开此 logger。
logging:
  level:
    com.yulinlin.data.core.transaction.TransactionLog: INFO
```

日志统一使用中文事件描述。同一个最外层框架事务使用相同事务 ID，SQL 日志以 `事务ID` 展示，事务生命周期日志仍使用 `txId`；嵌套事务、多个数据源及 JDBC 批处理工作线程共享该 ID。ID 只用于当前进程内关联，不是订单 ID，也不是跨服务链路 ID。普通查询没有框架事务时显示 `事务ID=none`。事务日志的 `elapsedMs` 从最外层框架事务开启时计算，包含事务内业务耗时，与 SQL 日志的 `总耗时` 计时范围不同。

框架层记录开启、嵌套深度 `depth`、参与者数量 `participants`、只回滚标记 `rollbackOnly`、资源提交/回滚和上下文清理。JDBC 层额外记录 `group`、`cluster`、DataSource 实例标识、连接标识、连接数量、原始/当前自动提交状态和提交归属 `owner`。不额外借连接、查询数据库元数据或打印账号密码；连接标识是当前 JDBC 连接对象的标识，不是数据库服务器的连接编号。

| 事件 | 如何理解 |
| --- | --- |
| `内层事务提交返回，等待最外层提交` | 只减少嵌套深度，尚未提交连接 |
| `JDBC连接开始提交` | 即将调用这个连接的 `commit()` |
| `JDBC连接提交成功` | 这个连接的 `commit()` 已正常返回；多连接/多库仍需逐一核对其他资源 |
| `JDBC连接回滚成功` | 对应连接已回滚，之前打印 SQL 成功不代表数据最终保留 |
| `框架事务资源处理完成` | 所有参与 Session 的提交方法均已正常返回；JDBC 物理结果仍按连接核对 |
| `框架事务上下文已清理` | 只代表线程状态清理，不代表数据库提交成功 |
| `事务提交后回调开始` | 框架资源提交成功且旧上下文已清理，开始处理进程内通知 |

例如，下列是同一笔单连接事务中实际提交的关键日志片段，省略了其他生命周期行：

```text
事务开启 txId=1 elapsedMs=0 depth=1 participants=0 rollbackOnly=false
SQL批量执行成功 事务ID=1 数据源=mysql 节点=master 操作=insert 总耗时=8ms 平均每条耗时=8.000ms 操作数量=1 影响行数=1 SQL模板数=1
SQL=["insert into `pay_order` (`id`) values (?)"]
参数=[order-1]
JDBC连接开始提交 txId=1 elapsedMs=28 group=mysql cluster=master dataSource=HikariDataSource@6f1 connection=7ab owner=framework connections=1 rollbackOnly=false
JDBC连接提交成功 txId=1 elapsedMs=31 group=mysql cluster=master dataSource=HikariDataSource@6f1 connection=7ab owner=framework connections=1 rollbackOnly=false
```

排查时按 `txId` 找完整过程：只看到嵌套结束则检查最外层是否收尾；每个 JDBC 连接都应找到最终提交或回滚结果；出现提交失败、回滚失败或连接释放失败会记录 ERROR 与原始异常。只有 SQL 成功而没有最终结果时，不应直接下结论认为已提交或已回滚。ES/MongoDB 当前没有对应的物理事务，框架资源方法返回也不能证明其具备数据库回滚能力。

关闭开关时不采集事务诊断计时、不输出生命周期日志，但仍生成轻量事务 ID，供 SQL 日志关联。SQL 普通、慢操作、异常和批量日志都包含 `事务ID`，是否输出仍遵守原有 SQL 日志配置；批处理 ID 在提交工作线程前捕获，不依赖工作线程的事务 ThreadLocal。手动声明 `TransactionListenerManager` Bean 的项目需自行调用 `setTransactionLog(true)`，默认自动配置会读取上述 YAML。

JDBC 事务连接统一标记 `owner=framework`：直接向 DataSource 借连接，关闭自动提交，最终由框架提交/回滚、恢复连接状态并释放。不再复用 Spring 线程绑定连接，也不等待 Spring 的提交通知。DataSource 仍可由 Spring Boot、Druid 或 Hikari 配置和注入；`@Transactional` 仅通过注解适配器委托 RouteSession，不再交给原生 JDBC 事务管理器。多资源仍是多个本地事务，不提供跨库原子提交保证；原生 JdbcTemplate/MyBatis/JPA 与框架写入不共用事务。注解支持范围及多事务管理器选取见[Spring 事务注解兼容](../relations/transactions.md#spring-事务注解兼容)。

---

下一步：返回[监控与排障主题目录](README.md)按需选择相关专题。
