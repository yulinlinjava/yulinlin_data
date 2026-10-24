# 事务上下文与 JDBC 资源

[文档首页](../README.md) / [内部维护](README.md)

统一上下文、代理修改、提交回调和连接释放。

## 本页导航

- [事务上下文与提交扩展点](#事务上下文与提交扩展点)
- [JDBC 事务连接资源](#jdbc-事务连接资源)
- [借用与并行批处理](#借用与并行批处理)
- [完成与释放](#完成与释放)

## 事务上下文与提交扩展点

`TransactionListener` 保留 `startTransaction()`、`commitTransaction()`、`rollbackTransaction()`、`afterCompletion()`，`afterCommit()` 使用默认空实现保持接口兼容。监听器只接收最外层的生命周期通知：嵌套开启、提交不重复调用监听器，内层回滚只标记共享事务 rollback-only，直到最外层结束再通知。扩展监听器无需自行维护嵌套栈。`TransactionListenerManager.afterCommit()` 通知所有固定监听器，一处失败不会跳过后续监听器。

`core.transaction.TransactionContext` 只由 `TransactionListenerManager` 的当前线程存储创建、释放，不再使用 Session 私有的 TransactionScope 或上下文继承体系。RouteSession 及其注解入口复用它，嵌套只增减 `depth`；选定节点、参与 Session、按 Provider 汇总的缓存失效范围、`afterCommitTasks` 和代理 `ProxyChanges` 都在同一个上下文中。第一次创建它的管理器负责本次监听生命周期，后续路由嵌套不重复通知。不同线程隔离；各 JDBC Session 的事务连接资源仍单独持有，统一上下文不代表共用一个物理连接。已移除空转发层 BaseTransactionSession，业务事务转发直接位于 RouteSession，RegisterSession 只提供注册和选库钩子。

管理器最外层提交统一执行：固定监听器的提交前处理 → `ProxyChanges.flush()` → 各参与 Session 的资源提交 → 清理代理、解绑上下文并通知 `afterCompletion()` → 处理缓存失效 → 成功监听和业务回调。代理按来源组和实体类型分批生成部分更新，保留脏字段、主键和乐观锁逻辑；只迁移事务归属，不重写 CGLIB 或更新解析器。代理服务不再作为固定 TransactionListener Bean 重复注册，旧手动生命周期方法仅保留为弃用兼容入口。回滚丢弃脏记录和成功回调。提交资源出现异常时只做基本回滚和清理、向外报告异常，不实现断线确认、重试、补偿或分布式原子提交。

`TransactionUtil.afterCommit(Runnable)` 直接委托当前路由业务上下文的管理器，无业务事务直接报错，不提供独立 Session 或其他 Spring 事务管理器的回调兜底。业务 Runnable 保存在本次上下文，不放入全局 Listener 列表。`@Transactional` 使用 Spring 标准注解拦截器，经 `RouteTransactionManager` 接入 RouteSession，不另外注册拦截同一注解的切面。代理写回、固定提交前监听、连接提交和成功通知都由框架管理器按顺序执行；不注册 Spring 同步回调，不维护 `springStatus`，已经解绑的旧上下文不会清理后来新事务的状态。

清理异常不会跳过已成功提交事务的通知，某个监听器或业务回调失败也不会跳过后续任务；最终传播首个异常，其他异常合并为 suppressed，不重新进入已提交事务的回滚分支。所有显式开启必须配对结束；普通 CRUD 只登记资源，不增加深度。该能力不实现 REQUIRES_NEW、NESTED 或跨线程事务传播。业务用法见[提交成功回调](../relations/transactions.md#提交成功回调)。

数据源实现的 `startTransaction/commitTransaction/rollbackTransaction` 仅负责本 Session 的连接资源；管理器首次登记时调用 start，最外层结束时调用 commit 或 rollback，不能反向调用管理器。`isOpenTransaction/setRollbackOnly/isRollbackOnly` 同样只读写本地资源状态。已删除重复的 `*TransactionResource` 桥接接口，不需要再实现两套同义方法。AbstractSession 的执行包装只记录日志、传播异常并标记自身资源失败，不自动开启或提交业务事务；JDBC 资源缺失的写操作明确报错，不能退化为自动提交。非 JDBC 默认只记录参与和回滚标记，保证事务内绕过缓存，不代表 ES/MongoDB 已具备服务端物理事务或回滚能力。

事务上下文、代理状态和独立连接管理的改动仅做静态核对，未执行编译、测试或打包。JDBC 资源登记仍延迟到真正执行 SQL 时才借连接，批处理连接在原线程初始化后再交给工作线程。

事务诊断开关为 `yulinlin.data.transaction-log`，默认关闭，由 core 自动配置传给 `TransactionListenerManager`。每个 `TransactionContext` 在创建时固定自己的 `TransactionLog` 和进程内事务 ID；JDBC 事务资源只捕获这一日志对象用于关联 ID，不通过它开启或协调业务事务。异步连接日志复用捕获的对象，SQL 执行前在所属线程将 ID 写入本次 `ParseResult`，包括并行解析后的所有批次，不读取工作线程的业务 ThreadLocal。关闭时仍保留 ID，但不读取事务诊断时钟、不输出生命周期日志；不存在框架事务时使用 `none`。日志不能改变提交/回滚决策；框架资源方法返回与 JDBC 物理 commit 分别记录，不把上下文清理、SQL 执行成功或 afterCommit 回调异常误报为数据库提交/回滚结果。该诊断改动仅做静态阅读核对，未编译、运行测试或打包。

`JdbcTransactionResources` 统一使用目标 `DataSource.getConnection()`，不调用 `DataSourceUtils`，不检查或复用 Spring holder。`TransactionAwareDataSourceProxy` 解包后再借连接，避免自管连接被代理重新导向线程绑定连接。具体借还和收尾规则见下节。

`jdbc.transaction.RouteTransactionManager` 只实现 Spring `PlatformTransactionManager` 协议，不继承 `AbstractPlatformTransactionManager`，不建立第二份资源上下文。每个注解调用的状态只保存原框架上下文身份和嵌套深度，用于防止跨线程、重复结束或手动事务未配对。Spring 标准拦截器负责注解解析和异常回滚规则，框架负责物理生命周期。仅接受 REQUIRED、默认隔离/超时和 readOnly=false，其他语义在业务执行前拒绝。DataJdbcApplication 提前注册 `@Primary` 的 `yulinlinTransactionManager`，使 Boot 默认 JDBC 事务管理器退让；通过 ObjectProvider 延迟获取 RouteSession，避免基础设施创建时循环依赖。原生 JdbcTemplate/MyBatis/JPA、其他事务管理器与本框架事务独立；不提供 Spring 同步事件或 JPA/JTA/XA 桥接。

源码入口：core/session、core/loadbalan、core/proxy、core/parse、core/coder；jdbc/session 和 jdbc/sql。缓存公共协议位于 cache-core，ORM 相关解析仍位于 core/cache，三个 Provider 只依赖 cache-core。为保持已有业务源码兼容，cache-core 中的公开类型暂时继续使用 `com.yulinlin.data.core.cache` 包名；模块边界已经独立，不要求调用方修改 import。数据库语法放在 Parse，驱动读写差异放在 Session，不额外引入 SqlDialect 或 Session.sqlXXX 拼接层。

## JDBC 事务连接资源

`JdbcTransactionResources` 由 `AbstractJdbcSession` 按事务持有，替代旧 `ConnectionPool`。真正的全局连接池仍是 Hikari、Druid 等 DataSource。业务只使用 RouteSession / 框架事务入口，不直接调用此资源类。

### 借用与并行批处理

1. 登记 Session 时只创建资源对象；第一次执行 SQL 前，在所属线程同步借入首条连接。首条连接获取失败向调用者传播，不允许本事务继续重试初始化。
2. 框架批处理线程捕获同一个资源对象，不复制业务事务 ThreadLocal。每条连接同一时刻只交给一个执行者；归还后可复用，重复归还或归还外部连接直接报错。
3. 全部已有连接繁忙、且未达到 `parallel-connections` 上限时，用虚拟线程申请额外连接。每个资源对象最多一个正在等待 DataSource 的扩容请求，不为每个等待者启动线程。
4. 等待执行者同时等待“已有连接归还”或“新连接加入”。即使扩容卡在 DataSource.getConnection，归还的连接也能立刻用于下一批，不必等扩容成功。
5. 额外借连接出现 SQLException 时，本事务停止继续扩容并复用已有连接，不因未执行 SQL 的可选扩容失败回滚已有业务。首条连接失败、业务 SQL 失败和物理提交失败仍正常传播；扩容的非 SQL 异常标记事务失败。

默认并行上限仍为 4，SQLite 仍为 1。Hikari 的池容量上限修正在资源类统一计算；其他 DataSource 以配置值为上限。这个值不是预留连接数，也不保证每个事务都能取得这么多连接。

旧模式下，两个事务各持有一条连接，又同时等待从已满的全局池借第二条，可能一直等到获取连接超时：执行完的连接只回到了各自事务，没有回到全局池。现在扩容等待与批处理执行分离，批处理可以用已归还的本事务连接继续执行并提交。它不解决数据库行锁冲突，也不使多连接共享隔离快照。

### 完成与释放

| 状态 | 允许行为 |
| --- | --- |
| ACTIVE | 借还连接、按需扩容、记录 rollback-only |
| FINISHING | 拒绝新借用；允许在用连接归还；不再接收扩容连接 |
| CLOSED | 连接已完成清理；重复 finish 不重复提交，不能重新初始化 |

进入 FINISHING 时唤醒等待者并中断尚未取得连接的扩容线程，但只等待正在执行业务的连接归还，不等待扩容结束。不能把仍在执行 SQL 的连接强行关闭，也不能为了缩短等待而提前提交。

驱动可能忽略中断：扩容线程晚到的连接不加入事务、不执行 SQL，单独回滚空事务、恢复原自动提交状态并关闭。必须给实际 DataSource 配置有限的获取连接超时；此实现不承诺能强制终止不响应中断、又没有超时的驱动调用。晚到连接的释放失败只能记录诊断，不能反向改变已经结束的业务事务。

所有已加入的连接统一逐个提交或回滚，始终尝试释放全部连接。提交失败时尝试回滚该连接及尚未提交的连接，首个异常向外传播，其他异常合并 suppressed；完成失败时不把 autoCommit 改为 true，避免意外提交。连接关闭成功与否不改变“已经提交的其他连接不能保证回滚”的本地协调边界。

已移除无人调用的 `ConnectionUtil` 旧事务入口及其第二套 ThreadLocal，也移除了旧 `ConnectionPool` 类型和 `getJdbcConnection/getConnections/isSpringManaged/clear` 兼容方法。外部若直接依赖这些内部接口，需要迁移；普通 Wrapper、RouteSession、`@Transactional` 和配置用法不变。

实现入口：[JdbcTransactionResources](../../jdbc/src/main/java/com/yulinlin/jdbc/session/JdbcTransactionResources.java)。原文引用的 `admin/JdbcTransactionResourcesTest` 已不在当前仓库中，不能再据此宣称已有回归覆盖；双事务扩容等待、已有连接复用、晚到连接关闭、收尾拒绝借用和提交失败清理仍应作为后续事务测试清单。本次只修正文档引用，未新增或运行事务资源测试。

---

下一步：返回[内部维护主题目录](README.md)按需选择相关专题。
