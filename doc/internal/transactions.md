# 事务上下文与 JDBC 资源

[文档首页](../README.md) / [内部维护](README.md)

## 本页导航

- [事务上下文与提交扩展点](#事务上下文与提交扩展点)
- [JDBC 事务连接资源](#jdbc-事务连接资源)
- [借用与并行批处理](#借用与并行批处理)
- [完成与释放](#完成与释放)

## 事务上下文与提交扩展点

RouteSession 是唯一业务协调入口。TransactionContext 只存状态，由 RouteSession 创建、绑定和解绑；不再存在 TransactionListenerManager 或 Spring PlatformTransactionManager 实现。

上下文保存已登记的 Session 列表（`sessions`，按首次访问顺序，每组一个 master 节点）、一个 Frame 栈、代理状态和诊断日志。每个 Frame 按 Session 保存 JDBC 保存点（`savepoints`）、代理快照、当前范围 rollbackOnly、缓存失效记录及 afterCommitTasks。外层帧对应物理事务。Session 在首次被请求访问时登记（`RouteSession.enlist`）；已有多个嵌套帧时，登记后给除外层外的每层补建保存点，再执行第一条 SQL。不支持事务的 Session（ES）不登记，请求直接执行。最外层按登记顺序逐个 commit，失败或回滚时按相反顺序回滚尚未结束的 Session；不是两阶段提交。

嵌套开始前 flush 外层代理，避免把外层待写数据放到内层保存点之后。内层成功前 flush，再释放保存点并合并缓存/回调；内层回滚恢复已有代理字段与版本、懒加载记录，作废新代理，丢弃本帧失效与回调。字段快照不承担任意可变对象内部修改的深拷贝回滚。

TransactionListener 仅接收最外层的 start/commit/rollback/afterCompletion/afterCommit，不接收保存点事件。业务回调用 TransactionUtil.afterCommit 或 route.afterCommit，随帧归属处理。最外层顺序：提交前监听 → 代理 flush → 资源 commit → 清理代理/解绑 → afterCompletion → 缓存失效 → 成功监听/业务回调。失败继续尝试后续清理，保留首个异常和 suppressed。

JoinTransactionAop 是同时识别两种注解的单一 Advisor，复用 Spring 的注解元数据解析，不注册 TransactionInterceptor。两种注解叠加只创建一个范围；Spring 显式回滚规则优先，无匹配时所有异常回滚。超时取 `@Transactional(timeout/timeoutString)`，其次 `@JoinTransaction.timeout`；propagation、isolation、transactionManager、label、`@JoinTransaction.value` 只打一次 WARN 后忽略，readOnly 静默忽略。Boot 默认 DataSourceTransactionManagerAutoConfiguration 由 jdbc 的 AutoConfigurationImportFilter 排除；显式原生 Spring 事务拦截器冲突时启动报错。完整支持边界见[注解兼容](../relations/transactions.md#spring-事务注解兼容)。

## JDBC 事务连接资源

JdbcTransactionResources 持有一条连接、原自动提交状态、所属线程、物理失效标记及保存点栈。每个 JdbcSession 各自一份；多个组的协调在 RouteSession，不在资源层。同一组只能登记一个节点（master），同组其他节点加入会在目标 SQL 前拒绝。

EntitySession.supportsTransactions 默认 false。JDBC 声明支持并提供保存点操作；不支持保存点的驱动明确拒绝。非 JDBC 会话在事务内不登记、直接执行，不能声明虚假的回滚能力。事务超时由 TransactionContext.remainingMillis 给出，JdbcSession.applyTransactionTimeout 在每条语句前设置查询超时。

SQL 错误分类在持有连接的 JDBC 入口完成。MySQL/H2 普通语句错误不自动标记物理事务；PG 普通错误标记当前保存点范围失效，回滚保存点后可恢复，无保存点时标记物理事务。连接/事务回滚类错误、超时、未知状态保守失效。错误链遍历 cause、nextException、suppressed，保留原始 JDBC 原因。JdbcErrors 仅借助 Spring 错误分类识别重复键，对外抛 ORM 异常。

注意：rollbackToSavepoint 不能先调用要求“资源可继续 SQL”的 initialize/usable 检查，否则 PG 已失败范围会永远无法恢复。不可恢复的物理事务则禁止保存点恢复。

## 借用与并行批处理

显式事务全部 SQL 使用原线程和同一连接，禁止并行借用。batch 只影响 JDBC 批次发送，事务内不创建工作线程；execute-batch-size 继续生效。

事务外普通 JDBC 写请求由 RouteSession 自动包装单连接事务。显式 batch 请求直接执行独立自动提交任务：每个任务借自己的连接，用完关闭；AbstractSession 等待全部已派发任务，合并异常后返回。部分成功不回滚；RouteSession 在 finally 清理该请求缓存。有效连接并发数由 parallel-connections 和 Hikari 容量共同限制，SQLite 不拆组。

beforeQuery/afterRow、beforeWrite/afterWrite 过滤器、编解码与 SQL 日志保留。事务中写入前登记缓存失效，覆盖业务捕获部分执行错误后提交的情况；回滚帧丢弃自身失效记录。事务外 batch 的 afterWrite 仅在全部执行成功后调用，不能当成原子提交通知。

## 完成与释放

最外层提交失败时尝试回滚并释放连接；提交结果未知时不切换 autoCommit=true，避免意外提交。保存点恢复失败使整个物理事务失效。上下文在提交后回调之前解绑，回调中创建的新事务不会被旧事务清理。

诊断开关 yulinlin.data.transaction-log 默认关闭，直接传给 RouteSession。嵌套沿用外层 ID；事务外独立批量显示 none。SQL 执行成功不等于最终提交。

回归入口：

- [H2 事务集成](../../h2/src/test/java/com/yulinlin/jdbc/h2/H2TransactionFailureTest.java)：真实 JDBC 保存点、异常规则、代理恢复、回调、缓存、普通及并行批次。
- [资源错误与保存点](../../jdbc/src/test/java/com/yulinlin/jdbc/session/JdbcTransactionResourcesTest.java)：PG 范围恢复、MySQL/H2 分类、致命错误、连接线程及栈顺序。
- [注解兼容](../../core/src/test/java/com/yulinlin/data/core/aop/JoinTransactionAopTest.java)：单次代理、接口/组合注解、规则覆盖、超时、被忽略的选项和原生拦截器冲突。
- [多数据源事务](../../h2/src/test/java/com/yulinlin/jdbc/h2/H2MultiSourceTransactionTest.java)：多组提交、整体回滚、跨组嵌套保存点、提交中途失败、非事务数据源直接执行。
- [事务超时](../../h2/src/test/java/com/yulinlin/jdbc/h2/H2TransactionTimeoutTest.java)：剩余时间设置、嵌套取最早截止时间、超时回滚。

这些测试不代替真实 MySQL/PG 服务端及生产连接池验证。事务只保护事务型业务 DML，不保护数据库隐式提交的 DDL、外部请求或业务自行取得的连接。
