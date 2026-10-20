# 事务与提交后回调

[文档首页](../README.md) / [关联与事务](README.md)

路由事务、Spring 注解兼容和 afterCommit。

## 本页导航

- [事务使用](#事务使用)
- [框架路由事务](#框架路由事务)
- [Spring 事务注解兼容](#spring-事务注解兼容)
- [排查事务是否提交](#排查事务是否提交)
- [提交成功回调](#提交成功回调)
- [路由是唯一业务入口](#路由是唯一业务入口)

## 事务使用

### 框架路由事务

以下完整 Service 方法片段假设 mysql/sqlite 两组及业务表均已准备：

```java
import com.yulinlin.data.core.anno.JoinTransaction;

@JoinTransaction
public void saveBusinessData() {
    ModelInsertWrapper.newInstance("mysql", mysqlUsers).batch().execute();
    ModelInsertWrapper.newInstance("sqlite", localUsers).execute();
}
```

放在 Spring 管理的 Service public 方法上，通过代理调用。也可使用回调：

```java
SessionUtil.route().transaction(() -> {
    ModelInsertWrapper.newInstance("mysql", usersToInsert).execute();
    return null;
});
```

`RouteSession` 是业务 CRUD 和事务的唯一入口。没有外层事务时，一次写请求由 RouteSession 自动开始并结束事务；普通查询不主动开启事务，多个独立请求不自动合成一个业务事务。RouteSession 定义边界并登记参与者，`TransactionListenerManager` 统一维护当前线程的业务上下文，各 Session 只管理自己的连接资源。

同一线程内，路由方法和事务注解共用一份 `TransactionContext`，具体 Session 不创建上下文，也不反向调用事务管理器。只有路由显式开启事务才增加 `depth`，普通 CRUD 和 Session 资源开启只登记参与者，不增加深度；同一个 Session 的资源在本事务中只开启一次。内层提交只减少深度，不提交连接、不刷新代理、不执行提交后回调；内层回滚标记整个事务 rollback-only。最外层统一写回代理、提交/回滚参与者、清理状态和通知回调。不同线程互相隔离，嵌套不模拟独立提交或保存点。

### Spring 事务注解兼容

继续使用 `org.springframework.transaction.annotation.Transactional`。JDBC 模块自动注册默认优先的 `yulinlinTransactionManager`，Spring 标准注解拦截器解析注解后，由这个轻量适配器调用 RouteSession。它不创建 Spring JDBC 事务、不绑定 Spring 连接、不注册 Spring 同步回调。原有重复的 `SpringTransactionAop` 已移除，默认 Boot JDBC 事务管理器自动配置会退让。

```java
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Exception.class)
public void createOrder() throws Exception {
    // 在 Service 中通过 Repository / Wrapper 执行业务；所有数据源加入同一路由事务。
}
```

- 支持默认 `REQUIRED`：内层加入同一业务上下文，最外层才提交；内层回滚会标记整个事务 rollback-only。
- `rollbackFor`、`noRollbackFor` 及其类名规则由 Spring 标准拦截器解析。默认对 `RuntimeException` / `Error` 回滚，对受检异常默认提交；业务需要受检异常也回滚时使用上例。已经发生 SQL 失败等导致 rollback-only 的事务不能被 `noRollbackFor` 强行提交。
- 目前仅支持默认隔离级别、默认超时和 `readOnly=false`。非默认传播方式（包括 `REQUIRES_NEW` / `NESTED`）、隔离级别、超时或 `readOnly=true` 会在进入业务方法前明确报错，不静默假装支持。
- 必须从 Spring 代理外部调用；不要叠加 `@JoinTransaction` 和 `@Transactional`，也不要认为业务异步线程会继承事务。框架 `.batch()` 由内部显式传递连接池，不等于自动传播业务线程上下文。
- 数据源仍使用原来的 Spring Bean 配置，但连接由框架独立取得和管理。原生 JdbcTemplate、MyBatis、JPA 不会加入此事务；不能把混合使用当成统一提交。
- 若项目另外指定了默认事务管理器、有其他 `@Primary` 管理器，或使用 `TransactionManagementConfigurer`，请明确使用 `@Transactional(transactionManager = "yulinlinTransactionManager", rollbackFor = Exception.class)`。显式指定其他管理器不进入本适配器，框架写请求会按自己的边界独立提交。
- 不提供 Spring `TransactionSynchronizationManager` / `@TransactionalEventListener` 的同步生命周期。提交后业务使用 `TransactionUtil.afterCommit`；手动 `TransactionTemplate` 只有选择本适配器时才进入框架事务。

迁移时更新配套的 core、jdbc 及所用数据库模块，移除业务手动注册的旧 `SpringTransactionAop`。不要同时保留另一套拦截相同 `@Transactional` 方法的自定义事务切面。

### 排查事务是否提交

开启 `yulinlin.data.transaction-log: true`（默认关闭），按 SQL 日志里的同一个 `txId` 查看开启、嵌套深度、数据源参与和连接的最终处理。SQL 日志中的 `affected=1` 只表示 JDBC 执行影响一行，不代表事务已经提交；事务日志关闭时 SQL 仍保留 ID，没有框架事务的查询显示 `txId=none`。连接均由框架持有，最终查看每个连接的 `JDBC连接提交成功` 或回滚日志。`数据源事务提交方法已返回`、`框架事务上下文已清理` 都不是物理提交凭证，多库/多连接要逐一核对，不能凭一个资源成功推断全部成功。配置、字段和日志事件详见[事务诊断日志](../operations/sql-log.md#事务诊断日志)。

### 提交成功回调

业务需要在数据库提交成功后刷新权限、清理业务缓存或发送进程内通知时，使用 `com.yulinlin.data.core.transaction.TransactionUtil.afterCommit(Runnable)`：

```java
@Transactional
@JoinSync
public void changeNickname(String id, String nickname) {
    UserEntity user = userRepository.findByIdEq(id);
    user.setNickname(nickname);
    TransactionUtil.afterCommit(() -> security.refresh(id));
}
```

上例是方法片段，`userRepository` 为业务仓库，`security` 为注入的 `ISecurityManager`。刷新指定用户资料；若是当前用户，则更新本线程快照，不续期或撤销 JWT。实际业务还应保留输入校验。无状态 JWT 案例每次请求重新加载权限，单纯修改角色无需遍历刷新全部会话。`@Transactional` 经 `yulinlinTransactionManager` 接入路由事务；`@JoinTransaction` 和 `SessionUtil.route().transaction(...)` 也使用同一入口。`TransactionUtil` 只向当前框架事务注册，不为其他事务管理器提供回调兜底。

| 场景 | 行为 |
| --- | --- |
| 框架业务事务成功提交 | 先清理旧代理状态并解绑上下文，处理相关缓存失效，再通知固定 `TransactionListener.afterCommit()`，最后按注册顺序同步执行业务回调 |
| 路由事务嵌套、同时访问多个数据源 | 共用当前线程的上下文，等最外层完成后每个回调执行一次 |
| 回滚、rollback-only 或提交失败 | 不触发提交成功通知，丢弃业务回调 |
| `@Transactional` 使用框架适配器 | 与路由事务完全一致，不等待其他 Spring 事务 |
| 仅有其他 Spring 管理器的事务、没有框架事务上下文 | 拒绝注册，提示先开启框架事务 |
| 仅手动开启具体 Session 的连接资源 | 不构成业务事务，不能注册回调；必须通过 RouteSession 开启 |
| 没有活动的框架事务 | 抛出 `IllegalStateException`，不静默立即执行 |
| 清理、监听器或某个回调失败 | 仍尝试后续清理与回调，最终抛出首个异常，其他异常保留为 suppressed；已提交的数据不再回滚 |

回调在当前线程执行，不是异步任务，也不是 `.queue()`。管理器先清理原事务的代理状态、解绑上下文，再发出 `afterCompletion()` 和成功通知；回调中新开的框架事务使用新上下文，不会被原事务的收尾逻辑误清理。不要在回调里修改原事务代理；普通读取显式 `.autoUpdate(false)`，多步写业务用新的框架事务边界。RBAC 案例的 `findSnapshotById` 使用普通查询快照，避免继承外层 `@JoinSync` 的代理策略。

回调开始前，框架 JDBC 连接已完成提交与释放。因此回调中再次调用 `SessionUtil.route().transaction(...)` 会建立新的框架事务；这只是旧事务结束后的新事务，不代表支持活动事务内的 `REQUIRES_NEW`。若另外包在原生 Spring 事务中，两者完全独立，本回调不会等待该原生事务，也不能保证它提交成功。

提交后回调失败不代表数据库写入失败，调用方不能盲目重试原始写入。它是进程内通知，应用在提交后异常退出可能来不及执行；需要可靠投递的外部消息仍由业务持久化任务或 Outbox 处理。

具体 Session 的 `startTransaction/commitTransaction/rollbackTransaction` 是管理器调用的资源生命周期，不再转发管理器，也不维护业务嵌套深度。业务代码不得手动调用它们，否则会绕过统一的代理写回、跨数据源协调、缓存失效和回调；JDBC 无活动资源的写操作会报错。

### 路由是唯一业务入口

以下为方法体片段，两个 Request 已构造，并明确指定各自的数据源组：

```java
insertRequest.setSession("mysql");
updateRequest.setSession("sqlite");
RouteSession route = SessionUtil.route();
route.transaction(() -> {
    route.insert(insertRequest);
    route.update(updateRequest);
    return null;
});
```

`route.isOpenTransaction()` 表示当前线程是否存在业务事务；具体 Session 的同名方法只表示自身资源是否已开启，不等于新的业务事务。业务事务必须在开启它的同一线程结束。

嵌套是共享外层事务的计数，不是保存点。执行失败/嵌套回滚标记 rollback-only，即使业务捕获异常，外层不能继续正常提交。跨库和多连接只是本地事务协调，不是分布式原子提交。

---

下一步：返回[关联与事务主题目录](README.md)按需选择相关专题。
