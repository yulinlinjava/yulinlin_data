# JDBC 事务：独立 Session、路由协调与 Spring 接入

> 基线：2026-10-04 事务实现；2026-10-05 按命名会话组更新示例 / JDK 25 / 制品版本 3.0 / Spring Boot 3.5。本轮未运行测试、编译或打包，历史验证结果不代表本次变更已验证。
> 源码：core 的 AbstractSession、RegisterSession、RouteSession；jdbc 的 AbstractJdbcSession、ConnectionPool、SpringTransactionAop。

## 推荐选择

- 多数据源或多连接批处理：使用框架 `@JoinTransaction` 或 `SessionUtil.route().transaction(...)`。
- 已有 Spring 管理的单数据源业务：继续使用 Spring `@Transactional`；检测到同一 DataSource 的绑定连接时，框架复用它，在原线程顺序执行，由 Spring 完成物理事务。
- 严格单库原子性：`parallel-connections: 1`，不要把多连接批处理当作单个数据库事务。
- SQLite：使用通用 JdbcSession、单连接，默认组 sqlite；无需注册 DataSource 或 sqliteTransactionManager Bean。

不要默认同时叠加两种事务注解。代理注解需要 Spring 管理的对象并经代理调用，同类自调用或 `new Service()` 不会生效。

## 框架事务示例

```java
import com.yulinlin.data.core.anno.JoinTransaction;

@JoinTransaction
public void saveBusinessData() {
    ModelInsertWrapper.newInstance("mysql", mysqlUsers).batch().execute();
    ModelInsertWrapper.newInstance("sqlite", localUsers).execute();
    // 任何未捕获的异常都触发回滚协调。
}
```

也可以直接使用回调：

```java
SessionUtil.route().transaction(() -> {
    ModelInsertWrapper.newInstance("oss", usersToInsert).batch().execute();
    return null;
});
```

单次 CRUD 请求没有外层事务时也会自动开始、完成自己的事务；多个独立请求不会自动组成一个业务事务。

RouteSession 按实际访问加入参与者，每个 Session 只加入一次；最终正常结束逐个提交，异常逐个回滚。状态按路由实例、会话实例及调用线程隔离，不再由静态计数或已注册组数决定。路由只结束自己加入的事务层，不代替业务关闭一个预先独立开启的外层 Session 事务。

## 独立 Session

配置好的 JdbcSession 可以不通过路由直接执行已构造的请求：

```java
// 方法体片段，所在方法声明 throws Exception。
// jdbcSession 是工厂已配置的对象；insertRequest 是已构造的 ExecuteRequest。
jdbcSession.startTransaction();
try {
    jdbcSession.insert(insertRequest);
    jdbcSession.update(updateRequest);
    jdbcSession.commitTransaction();
} catch (Exception | Error error) {
    try { jdbcSession.rollbackTransaction(); }
    catch (Exception | Error cleanup) { if (cleanup != error) error.addSuppressed(cleanup); }
    throw error;
}
```

也可以直接 `jdbcSession.insert(insertRequest)`，让这一请求自动管理事务。事务必须在开启它的同一线程结束。这里的独立能力指请求执行不依赖全局 RouteSession；工厂仍需注入编码器、解析器等组件，现有 Model Wrapper 的构造/execute 快捷入口仍依赖框架路由。

## 并发批处理与异常

默认并发连接上限为 4，外围配置为 `yulinlin.datasource.jdbc.parallel-connections`，按会话覆盖用 `session.setParallelConnections(n)`；完整配置和 `.batch()` 示例见多数据源专题。

支持并发写入时，上层分成最多连接上限数量的大组，一组一个任务/连接；`supportsParallelWrites()` 为 false 时不分组。底层每次 JDBC batch 默认 256 条，可用 `yulinlin.datasource.jdbc.execute-batch-size` 或 `session.setExecuteBatchSize(n)` 调整。`executeBatch()` 仅执行语句，不 commit，不缩小业务事务的回滚范围。

未绑定 Spring 事务时，异步任务明确捕获所属 Session 的连接上下文，不依赖工作线程的 ThreadLocal。每个物理连接互斥使用；等待所有已提交任务结束后，才提交、回滚或回收连接。同步/异步混合批次累加所有结果，不能因为同步批次已产生结果就提前返回。失败和连接清理异常向调用方传播，次要异常保留为 suppressed。

嵌套回滚，以及已有事务中的 JDBC 请求执行失败，会标记 rollback-only。即使业务捕获异常，外层也不能继续正常提交；外层结束实际回滚并抛出异常。嵌套是共享外层事务的计数，不是保存点回滚。

## Spring 注解：支持范围与限制

```java
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Exception.class)
public void saveOneDatabase() {
    ModelInsertWrapper.newInstance("mysql", usersToInsert).execute();
}
```

jdbc 自动配置注册 SpringTransactionAop，识别类/方法的 Spring注解，开启和结束框架路由事务。若存在 Spring 原生事务管理器，其事务拦截器仍负责自己的属性。框架借用 DataSourceUtils 的绑定连接，不跨工作线程使用，也不自行提交/关闭 Spring 拥有的连接。执行失败会立即标记对应的 Spring ConnectionHolder 为 rollback-only，避免业务捕获异常后原生 Spring 先提交。

仅有 SQLite 时没有原生 JDBC 事务管理器也能使用这个注解，由框架切面管理会话；这不代表实现了 Spring 的全部事务语义。

框架切面本身不解析 propagation、isolation、rollbackFor、noRollbackFor 等属性；`@JoinTransaction.value()` 也不用于选择参与数据源。框架嵌套计数不等价于 REQUIRES_NEW、NESTED 或保存点；异步业务线程不会自动继承事务。高级传播、同步代理自动更新、Spring 与非 Spring 管理的多个库交叉参与，仍需针对实际代理顺序和业务做集成验证，不承诺透明兼容。

## 关联代理与事务

`@JoinLazy` 和 `@JoinSync` 使用的是 `SessionUtil.route()` 的事务状态，不是任意独立 JDBC 连接是否关闭 autoCommit。创建代理及访问延迟字段、调用待同步 setter 时保持同一线程的路由事务，完整用法见 `12-relations.md`。

监听器仍收到嵌套层回调，但 SyncProxyFactory 只在路由最外层提交时写回 setter 记录，内层结束不清理跟踪；实际 Session 完成后调用 `afterCompletion()` 释放上下文。回滚丢弃记录，不还原 Java 对象。

同步代理加入原生 Spring 事务时注册 `beforeCommit` 钩子，在 Spring 物理提交前写回，复用绑定连接；只读事务禁止同步 setter。代理必须在原始线程、原始路由事务内使用，原始 Spring 事务结束后也不能继续修改旧同步代理。已验证普通注解及 TransactionTemplate，不将 REQUIRES_NEW/保存点语义套到路由嵌套上。集合原地修改、null 清列、未代理对象修改不自动写回，完整边界见关联专题。

## 原子性边界

多数据源和多物理连接都是**本地事务的协调**，不是 XA/分布式原子事务。所有任务成功只是进入提交阶段，不能保证所有连接一起提交成功；中途提交失败时尝试回滚后续连接并清理全部资源，但已经成功提交的数据无法撤销。

同一 Session 使用多个连接时，提交前的跨连接查询也不保证读到其他连接的未提交写入。原子业务使用单连接；需要跨库强一致性应另选分布式事务或设计补偿，不要依据同名注解推定保证。

## 已验证与未验证

专项测试覆盖：Session 独立事务与实例隔离、默认 4/配置 2 连接限流、10 万条均匀四组、PreparedStatement 复用、256 条分批及尾批、能力关闭时不分组、后续批次失败回滚、同步/异步混合结果、失败任务排空、拒绝提交、嵌套 rollback-only、连接提交/回滚/关闭失败清理、获取连接等待不阻塞其他借用者归还、真实 Spring AOP 顺序及绑定连接复用；SQLite 真实临时文件 CRUD、512 行批次和整批失败回滚、多会话协调及编解码/建表。

代理专项测试还覆盖批量懒加载、空结果、来源数据源、setter 一次执行、默认值/null、主键和版本保护、内层提交后继续修改、缓存命中创建新代理、Spring beforeCommit 和回滚。JDBC 并发/错误注入测试使用模拟连接；SQLite 使用真实本地文件。未连接外部 MySQL，也未执行业务吞吐量或高级 Spring 传播行为基准。

```shell
mvn -pl sqlite -am -Dtest=OrmProxyIntegrationTest,JdbcSessionTransactionTest,JdbcBatchExecutionTest,SqliteIntegrationTest,SqliteSchemaManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```
