# Admin ORM 事务人工验证

直接运行 [AdminOrmTransactionTest](java/com/yulinlin/admin/jdbc/AdminOrmTransactionTest.java)。

这是使用真实内存 H2 的 JUnit 5 测试，不使用 `@SpringBootTest`，不启动 admin，也不读取业务数据源配置。每个方法使用独立临时数据库，结束后释放连接；已有 admin 数据库不受影响。不需要配置 Redis、MySQL、PG 或 JWT 密钥。

## IDEA 运行

1. 用项目配置的 JDK（当前 Java 25）导入整个 Maven 工程，等待依赖和模块加载完成。
2. 打开 `AdminOrmTransactionTest`，点击类旁边绿色按钮运行全部 14 项，或点击方法旁边按钮单独运行。
3. 查看中文场景名称和断言结果。每项结束时控制台打印“数据库最终数据”，事务日志会显示保存点与连接提交/回滚。
4. 重复键、模拟资源失效、检查约束失败是测试主动制造的异常；JUnit 全绿才表示验证通过，不要仅凭控制台出现异常栈判断测试失败。

调试时可以在 `route.transaction(...)` 的回调、`route.setRollbackOnly()`、代理 setter 和 `counters.flush()` 上打断点。

## 场景与预期

| 编号 | 验证内容 | 数据库预期 |
| --- | --- | --- |
| 01 | 正常事务，事务内读未提交隔离 | 提交后保留 a、b；只用一条写连接 |
| 02 | 外层 rollback-only | 空表；提交失败，成功回调不执行 |
| 03 | 内层 rollback-only | 只保留 outer-before、outer-after |
| 04 | 注入 SQLState 40001，业务捕获异常 | 空表；物理资源失效不能强行提交 |
| 05 | H2 当前范围内捕获普通重复键 | 保留 a、b；不是所有 SQL 错误都标记整体回滚 |
| 06 | 内层成功，外层失败 | 空表 |
| 07 | 内层自动更新回滚 | user 的 name=final、version=2；内层新代理失效 |
| 08 | 普通 List 插入失败 | 只保留之前已提交的 existing |
| 09 | 事务内 batch 失败 | 空表；使用一条写连接 |
| 10 | 事务外 batch 部分失败 | 保留 a、b、existing；本用例使用三条写连接 |
| 11 | Spring Transactional 注解兼容 | 受检异常也回滚，空表 |
| 12 | 计数器 execute 嵌套回滚 | video.views=1，内层 +2 撤销 |
| 13 | 计数器后台刷新失败后重试 | 失败时 a、b 均为 0；修正后各为 1 |
| 14 | 事务内手动 flush | 拒绝且不取走缓冲；事务外刷新后 views=1 |

04 使用注入的 JDBC 错误验证框架判断，并不证明真实数据库死锁行为；05、10 验证的是 H2，不能据此推断 PG 的失败恢复或所有驱动的批次执行顺序。

命令行从仓库根目录运行同一组：

```shell
mvn -pl admin -am -Dtest=AdminOrmTransactionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

只运行这个类，不要用 `AdminApplicationTests` 代替；后者会启动完整 admin 应用。
