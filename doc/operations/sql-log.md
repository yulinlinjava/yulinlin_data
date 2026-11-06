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
- `log-sql-pretty` 默认 `true`，对打印出来的 SQL 做人类可读美化：不添加外层引号和额外转义，省略简单标识符的引用符，整理字符串外的多余空格及逗号、括号间距。设为 `false` 恢复原来的带引号、转义展示。普通、批量、慢查询、失败日志均生效；只处理日志副本，不修改执行 SQL、参数或编解码。只保留此开关，旧的 `log-sql-escape` 配置应移除。
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

控制台默认开启可读 SQL，例如使用 H2：

```yaml
yulinlin:
  h2: # 按实际模块替换为 mysql / postgresql / sqlite
    log: true
    log-sql-pretty: true # 默认 true；false 恢复原来的转义展示
```

```text
# log-sql-pretty: true（默认）
SQL=insert into user_info (date, upt_time, id, crt_time) values (?, ?, ?, ?)
# log-sql-pretty: false
SQL="insert into \"user_info\" ( \"date\" , \"upt_time\" , \"id\" , \"crt_time\" ) values ( ? , ? , ? , ? )"
```

标识符引用规则来自当前 Session 的 SQL 解析器，例如 MySQL 的反引号、H2/PostgreSQL 的双引号。仅省略字母/下划线起始、后续为字母/数字/下划线/美元符号的简单名称引用；带空格、点号或转义引用符的复杂名称仍保留。字符串、JSON、注释、美元引用内容不会被当成标识符删引号；未知引用规则保守保留。美化后的 SQL 仅用于阅读，省略引用后可能改变关键字或大小写语义，不能替代原始可执行 SQL。

批量仍使用 `SQL=[...]` 展示去重后的受限模板列表。两个模式都会保留日志单行化、控制字符处理和长度截断；美化时字符串和注释中的换行等控制字符用可见的 `\n`、`\r`、`\t` 或 Unicode 转义表示，普通内容及空格保留。该开关不会开启成功日志或参数日志，不会将参数值拼进 SQL，也不会改变外部 JSON 日志编码器自身的转义行为。框架格式化的是 logger 消息，因此控制台和订阅该 logger 的文件日志会看到同一展示格式；不改变日志 appender 配置。

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

日志统一使用中文事件描述。最外层 ORM 事务与所有嵌套保存点共用一个事务 ID；SQL 日志显示 `事务ID`，生命周期日志显示 `txId`。每笔事务只绑定一个数据源、一条连接。事务外查询及独立 batch 显示 none，不把多个独立自动提交伪装成一个事务。

事务日志包含嵌套深度 depth、group 和 rollbackOnly；JDBC 资源日志包含 group、cluster、owner=orm、savepoints 和 rollbackOnly。elapsedMs 从最外层开启起计算，包含业务时间，与 SQL 执行耗时不同。日志不打印连接密码。

| 事件 | 如何理解 |
| --- | --- |
| JDBC保存点已创建 | 内层后续修改可以回滚到此处 |
| 嵌套事务成功，等待外层提交 | 保存点已释放，物理事务尚未提交 |
| JDBC保存点回滚成功 | 内层 SQL 已撤销，资源有效时外层可继续 |
| JDBC连接提交成功 | 单条事务连接的 commit 已正常返回 |
| JDBC连接回滚成功 | 物理事务已回滚，之前的 SQL 成功日志不代表数据保留 |
| JDBC事务完成失败 | 提交、回滚或清理异常，需要查看原始原因；不能盲目重试 |
| 事务提交完成 | ORM 物理提交阶段成功，后续通知可能仍抛异常 |

例如：

```text
事务开启 txId=1 elapsedMs=0 depth=1 group=unbound rollbackOnly=false
SQL批量执行成功 事务ID=1 数据源=mysql 节点=master 操作=insert 总耗时=8ms 平均每条耗时=8.000ms 操作数量=1 影响行数=1 SQL模板数=1
SQL=[insert into pay_order (id) values (?)]
参数=[order-1]
JDBC连接提交成功 txId=1 elapsedMs=31 group=mysql cluster=master owner=orm savepoints=0 rollbackOnly=false
```

只看到 SQL 成功或内层成功，不能推断最外层已提交。提交后回调失败也不会撤销已提交数据。没有 ORM 事务的 batch 是独立自动提交，整体失败可能已有成功数据。

关闭开关时不读取事务诊断时钟、不输出生命周期日志，但活动事务仍有轻量 ID 供 SQL 日志关联。手动构造 RouteSession 时可调用 setTransactionLog(true)，默认自动配置读取上述 YAML。

连接由 ORM 直接向 DataSource 取得和管理；不复用 Spring 绑定连接。Transactional 是注解兼容入口，不再注册原生事务管理器；JdbcTemplate/MyBatis/JPA 不参与此事务。详见[Spring 事务注解兼容](../relations/transactions.md#spring-事务注解兼容)。

---

下一步：返回[监控与排障主题目录](README.md)按需选择相关专题。
