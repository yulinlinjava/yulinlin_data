# 事务与提交后回调

[文档首页](../README.md) / [关联与事务](README.md)

ORM 自己管理事务；回调和两种注解都进入同一个 RouteSession。

## 本页导航

- [事务使用](#事务使用)
- [框架路由事务](#框架路由事务)
- [Spring 事务注解兼容](#spring-事务注解兼容)
- [SQL 失败与业务捕获](#sql-失败与业务捕获)
- [排查事务是否提交](#排查事务是否提交)
- [提交成功回调](#提交成功回调)
- [路由是唯一业务入口](#路由是唯一业务入口)
- [迁移说明](#迁移说明)

## 事务使用

### 框架路由事务

统一使用 `SessionUtil.route().transaction(callable)`。成功返回提交，异常离开回调则回滚并原样抛出，受检异常也默认回滚。回调可返回业务结果，不需要结果时返回 null。

```java
SessionUtil.route().transaction(() -> {
    ModelInsertWrapper.newInstance("mysql", order).execute();
    ModelInsertWrapper.newInstance("mysql", details).execute();
    return null;
});
```

也可以在 Spring 管理的 public Service 方法上标注 `@JoinTransaction`，通过代理调用。普通方法自调用不经过代理，需要嵌套边界时显式调用 transaction 或另一个代理 Bean。

| 当前状态 | transaction 的行为 |
| --- | --- |
| 没有事务 | 建立外层事务，使用一条 JDBC 连接，最终 commit/rollback |
| 已有事务 | 在同一连接建立保存点；成功释放保存点，失败回滚到该保存点 |
| 内层成功，外层随后失败 | 内层修改也随外层全部回滚 |
| 内层失败，外层捕获 | 内层修改已撤销；数据库资源仍有效时，外层可以继续 |
| 内层失败，外层不捕获 | 异常继续向外传播，各层依次回滚 |
| 驱动不支持保存点 | 明确报错，不降级成共享回滚标记或独立提交 |

一次事务只能使用一个数据源组和一个节点；第一次访问选择 master，之后固定。跨组访问在执行目标 SQL 前拒绝。禁止将非事务表、DDL 隐式提交或直接操作独立 JDBC 连接视为受本事务保护。

嵌套开始前刷新外层代理修改，再创建保存点并记录代理状态。内层成功前刷新内层代理；内层回滚会恢复已有代理的字段、乐观锁版本和懒加载状态，并丢弃内层回调和缓存失效记录。内层新建的代理在回滚后不能再使用，请重新查询。快照针对 ORM 跟踪的字段赋值，不承诺撤销业务对象内集合、Date 等可变对象的任意原地修改。最外层结束后的代理不能继续自动写回。

### Spring 事务注解兼容

可以保留 `org.springframework.transaction.annotation.Transactional`。统一的 `JoinTransactionAop` 同时识别它和 `@JoinTransaction`，调用 ORM transaction；没有自定义 Spring 事务管理器，也没有第二套连接上下文。

```java
import org.springframework.transaction.annotation.Transactional;

@Transactional
public void createOrder() throws Exception {
    // Repository / Wrapper 在同一个 ORM 事务内执行。
}
```

**兼容的是注解入口，不是 Spring 完整事务语义。**

- 注解默认值 REQUIRED 表示使用 ORM 自动嵌套保存点规则，不是 Spring REQUIRED 的共享回滚规则。不要再写 `propagation=NESTED` 或 REQUIRES_NEW。
- 支持 rollbackFor、noRollbackFor 以及两个 ClassName 属性。最具体的匹配规则优先；无匹配时所有异常默认回滚，包括受检异常。noRollbackFor 不会清除数据库资源失效状态。
- 类、接口、方法和组合注解按 Spring 注解解析规则识别；方法声明覆盖类声明。同一方法同时有两种注解，只建立一个范围，使用 Transactional 的显式回滚规则。
- 非默认 propagation、isolation、timeout、timeoutString、readOnly=true、transactionManager/value、label 明确拒绝。普通单例 Bean 在创建代理时发现不支持的配置会启动失败；懒加载 Bean 在首次创建代理时检查。
- JDBC 模块阻止 Boot 自动创建默认 DataSourceTransactionManager；显式启用 `@EnableTransactionManagement` 或注册原生 TransactionInterceptor/事务 Advisor 会与 ORM 切面冲突并启动失败。
- 不使用 Spring 连接绑定、TransactionTemplate、TransactionSynchronizationManager 或 TransactionalEventListener。JdbcTemplate、MyBatis、JPA 不会加入 ORM 事务；业务异步线程也不继承它。
- 不接受 `@JoinTransaction({"mysql", "other"})` 数据源列表，一次事务只能选择一个组。

### SQL 失败与业务捕获

可预期的失败操作放进内层 transaction，**在内层范围外捕获**，MySQL、H2、PG 可以使用同一种业务写法：

```java
import com.yulinlin.data.core.exception.DuplicateDataException;

var route = SessionUtil.route();
route.transaction(() -> {
    users.updateProfile();
    try {
        route.transaction(() -> {
            users.insertOptionalRecord();
            return null;
        });
    } catch (DuplicateDataException error) {
        // 内层已回滚；业务允许记录已存在。
    }
    audit.insertRecord();
    return null;
});
```

例中 users/audit 是同一数据源的业务仓库。JDBC 主键/唯一约束冲突抛 `DuplicateDataException`，其他 JDBC 执行错误抛 `OrmException`；前者继承后者，原始 SQLException 保留在 cause 中，可读 SQLState 和错误码。业务回调自身异常保留原类型。

不会给每条 SQL 偷加保存点。MySQL/H2 某些普通语句错误在当前范围内捕获后仍能继续，但不能把这一点推广到所有数据库。PG 发生普通 SQL 错误后，当前范围不能继续执行或提交，必须回滚当前保存点；没有保存点时只能回滚外层事务。即使在 PG 内层吞掉 SQL 异常，内层提交也会失败并尝试回滚保存点。

死锁、连接失效、事务回滚类 SQLState、JDBC 超时、MySQL/MariaDB 锁等待超时以及无法确认连接状态的错误保守标记物理事务失效；catch、保存点或 noRollbackFor 都不能保证恢复。框架不自动重试。

无外层事务时，普通 JDBC 写请求自动包一层事务；`.batch()` 走独立自动提交路径，等待全部已派发任务结束，任一失败就抛异常，已成功数据保留并清理相关缓存。事务内的 `.batch()` 自动使用本事务单连接 JDBC batch，不并行、不独立提交。详见[批量写入](../write-sync/batch.md)。

当前 MongoDB、ES 保留普通 CRUD 路径，不提供 ORM 物理事务；在显式事务内访问会在写入前拒绝，不能用空实现假装已回滚。

### 排查事务是否提交

开启 `yulinlin.data.transaction-log: true`，默认关闭。嵌套保存点沿用外层事务 ID；SQL 成功或内层成功不等于物理提交，以最外层 JDBC commit/rollback 结果为准。独立批量写入和事务外查询显示 `事务ID=none`，不会伪造整体事务 ID。详见[事务诊断日志](../operations/sql-log.md#事务诊断日志)。

### 提交成功回调

```java
@Transactional
@JoinSync
public void changeNickname(String id, String nickname) {
    UserEntity user = userRepository.findByIdEq(id);
    user.setNickname(nickname);
    TransactionUtil.afterCommit(() -> security.refresh(id));
}
```

userRepository 和 security 是业务注入的仓库、权限服务。也可调用 `route.afterCommit(action)`。

- 回调登记在当前范围。内层成功后合并进父层，保持注册顺序；内层回滚时丢弃，最外层实际提交后才执行一次。
- 最外层提交顺序：提交前监听 → 代理 flush → JDBC commit/释放 → 清理代理与上下文 → afterCompletion → 缓存失效 → 固定 afterCommit 监听 → 业务回调。
- 回滚、失效或提交失败不发成功通知。提交结果不明确时仍尝试清理缓存。
- 没有活动 ORM 事务时注册回调会报错；具体 Session 的资源事务不等于 ORM 业务事务。
- 清理或某个回调失败仍尝试后续任务，最后抛首个异常，其余放 suppressed。已经提交的数据不会因此回滚，不能盲目重试原始写入。
- 回调同步运行在当前线程，可以开启新的 ORM 事务；这是旧事务完成后的新事务，不是 REQUIRES_NEW。不要修改旧代理。
- 这是进程内通知，不是可靠消息投递；需要可靠投递时由业务使用持久化任务/Outbox。

### 路由是唯一业务入口

Wrapper、Repository、注解和 callback 都通过 RouteSession。具体 Session 的 startTransaction/commitTransaction/rollbackTransaction、保存点方法是底层资源接口，不用于业务手动组合。RouteSession 保留手动生命周期方法以兼容已有调用，必须同线程、按栈配对；推荐统一迁移到 transaction。

同一线程不得混用不同 RouteSession 的事务。一次事务不可跨组、跨节点或跨线程；不提供分布式事务、XA 或自动重试。

### 迁移说明

本次属于事务语义变更，需要一起升级 core、jdbc 和数据库模块：

1. 移除业务配置里的 yulinlinTransactionManager、RouteTransactionManager、TransactionListenerManager、EnableTransactionManagement 和额外事务拦截器。
2. 删除 Transactional 中的 transactionManager 等非默认选项；注解默认对受检异常也回滚。明确需要提交某类业务异常时写 noRollbackFor。
3. 将捕获 Spring DuplicateKeyException/DataAccessException 的业务处理改成 ORM DuplicateDataException/OrmException。
4. 原来依赖“内层失败使外层全部回滚”的业务，继续向外抛异常，或显式标记外层 rollback-only。现在捕获内层异常后可继续。
5. 原来跨库事务必须拆成独立业务步骤；对原子性有要求时由业务重新设计。
6. 需要整批成功或全回滚时，把 batch 包进 transaction；仅把 parallel-connections 设为 1 不会使事务外 batch 变成原子事务。
7. 自定义 Session 只有真正实现物理事务时才能声明 supportsTransactions；需要嵌套还必须实现保存点。MongoDB/ES 普通 CRUD 不受此限制。

---

下一步：返回[关联与事务主题目录](README.md)。
