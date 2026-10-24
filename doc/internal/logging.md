# Session 日志与监控监听

[文档首页](../README.md) / [内部维护](README.md)

Session 直接打印日志，保留执行监听扩展点。

## 本页导航

- [Session 日志与执行监听](#session-日志与执行监听)

## Session 日志与执行监听

已移除 `LogManager`、`LogPrint` 及其 Bean 注册，不再通过泛型反射识别请求并分发日志。当前调用链：

```text
AbstractSession 完成一次执行并固定耗时
  → 具体 Session.printExecution(event)
      → JDBC: JdbcExecutionLog.print(event)
      → ES: EsExecutionLog.print(event, logEnabled)
      → MongoDB: MongoExecutionLog.print(event, logEnabled)
  → 依次调用可选 SessionExecutionListener.afterExecution(event)
      → monitor 的 MonitorExecutionListener 识别 JDBC SqlNode 并记录
```

`SessionExecution` 与 `SessionExecutionListener` 位于 `com.yulinlin.data.core.session`。事件包含 session、requests、elapsedNanos、affectedRows、error、batch；elapsedMillis() 从同一纳秒值换算。batch 显式区分查询与写执行分组，单条写入或失败批次也属于 batch，不能用 requests.size() > 1 或 affectedRows 是否为 null 推断。affectedRows 只在成功写入时提供，其余为 null；error 为 null 只说明本次执行成功，不说明事务最终提交。txId、来源类和操作类型沿用 ParseResult 元数据。

计时边界保持在现有执行包装处：查询包含 beforeExecute、借连接、执行和读取 IDataBuffer，不含节点解析、实体解码、关联增强及日志/监听器时间；写入以 executeUpdate 分组计时，并行分组可能包含等待执行器调度的时间。一个分组内部可以执行多个 SQL 模板和多次 JDBC executeBatch，不应将它宣传成“每次驱动 executeBatch 的服务端纯 SQL 耗时”。

| 场景 | 通知规则 |
| --- | --- |
| select/count/group 实际访问数据源 | 每次执行一次，成功或失败都通知 |
| 分页 | count、select 分别通知；缓存命中的部分不通知 |
| 批量写入 | 每个 executeUpdate 分组一次，不给每行重复分配整批耗时 |
| 延迟写入 | 入队不通知，后台实际执行时通知 |
| 缓存命中、只解析节点、空写入 | 不产生数据库执行事件 |
| 提交/回滚 | 仍使用 TransactionLog，不冒充查询执行事件 |
| 启动 Schema DDL | 保留 Session 的独立 DDL 日志，不纳入当前执行监听 |

日志格式由 Session 直接委托普通辅助类，无日志 Bean。JDBC 保留各 Session 的 log、log-params、max-param-length、slow-sql-threshold、txId 与批量摘要规则；ES/MongoDB 从自身 SessionProperties 读取 log，失败仍输出异常。不要在工作线程通过 SessionUtil.route().session() 重新猜来源。

JDBC SQL 日志的展示键统一中文（事务ID、数据源、节点、操作、总耗时、操作数量、影响行数、SQL模板数、SQL、参数），内部 ParseResult.transactionId 和监控字段不改名。成功批次按 elapsedNanos 计算平均每条耗时，毫秒三位小数、固定小数点；分母用操作节点数而非影响行数，空批次不计算，失败不展示平均值与影响行数。总耗时沿用整数毫秒显示，低于 1ms 时可显示 0ms，但平均值仍从纳秒换算。参数展示仍受开关和截断限制，批量取首条参数，不改成全量输出。只有实际输出日志时才格式化平均值。SQL 文本采集规则需迁移，监控不依赖日志文本，无需改接口字段。

SQL 日志在同一个日志事件中换行展示摘要、SQL、参数，不拆成三次 logger 调用。SQL 和参数值内部仍处理为单行并保留截断限制，只有框架添加展示换行；关闭 log-params 不输出参数行。JdbcSessionProperties.logSqlPretty 默认 true，单条及批量 SQL 仅在日志副本上调用 SqlLogFormatter，按当前 NameParse.selectAlias 的引用规则省略简单标识符引用，整理字符串外空格，不额外包装/转义。词法保护单引号字符串、非标识符双引号字符串、注释、美元引用和复杂名称；控制字符可见化。未知/异常的引用规则保守保留，不影响执行。false 恢复旧的外层双引号和转义展示；不再提供 logSqlEscape。SqlNode、参数及 JDBC 执行路径不读取此开关；美化结果只用于阅读，不保证可直接执行。采集文本日志的平台需按日志头配置多行合并，monitor 仍读取执行事件，不解析控制台文本。外部 JSON encoder 自身的转义不受该开关控制。

JdbcSessionFactory、ElasticSearchFactory、MongoFactory 通过 ObjectProvider 收集排序后的监听器，在 create 时调用 `setExecutionListeners`。没有实现时为空列表，Session 不依赖 monitor 启动；关闭 SQL 日志不会关闭监控。当前内置 monitor 只消费 SqlNode，ES/MongoDB 不自动成为 SQL 监控数据；其他监听器可自己识别类型。

监听器扩展示例（方法体片段，放在业务或扩展模块的 @Configuration 中）：

```java
@Bean
public com.yulinlin.data.core.session.SessionExecutionListener executionListener() {
    return event -> {
        if (event.error() != null) {
            // 将需要的只读摘要交给自己的诊断系统，不在这里修改请求或提交事务。
        }
    };
}
```

监听器在执行完成线程同步调用，批量时可能并发；实现须线程安全、轻量，不持有请求级共享状态。requests 是只读列表快照，但其 ParseResult 不是深度不可变对象，禁止修改，不长期持有 Session、参数或异常引用；需要异步处理先提取必要摘要。日志辅助类和每个监听器的 RuntimeException 分别隔离，一处失败不跳过后续监听器，也不改变业务执行结果；不吞 JVM 致命 Error。

迁移时删除自定义 LogManager 注入和 setLogManager 调用；原 LogPrint 实现改成 SessionExecutionListener 或 Session 的 printExecution 覆盖。旧 SqlNodeLog Bean 替换不再生效；如需改变打印策略，覆盖 Session 方法。core、jdbc、ES/MongoDB 和 monitor 应一起更新，不能混用旧接口的二进制制品。此次只做静态检查，未编译或运行测试。

---

下一步：返回[内部维护主题目录](README.md)按需选择相关专题。
