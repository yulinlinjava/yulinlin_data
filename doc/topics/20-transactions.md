# Spring 事务兼容与边界

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`jdbc/src/main/java/com/yulinlin/jdbc/aop/SpringTransactionAop.java`；`jdbc/src/main/java/com/yulinlin/jdbc/session/ConnectionUtil.java`。若与实际安装版本冲突，以该版本源码为准。

框架已接入 Spring `org.springframework.transaction.annotation.Transactional`，不要求业务只能使用 `@JoinTransaction`。Spring Boot 常规单数据源、同步 JDBC 场景优先按上例使用 Spring 注解，并保证事务管理器管理同一个 DataSource。

源码依据：jdbc 自动配置 `DataJdbcApplication` 注册 `SpringTransactionAop`，识别方法或类上的 Spring `@Transactional`，同步调用 route 的 start/commit/rollback；同步 JDBC 调用经 `ConnectionUtil` 获取和释放连接，在其单路由分支使用 `DataSourceUtils`，参与 Spring 绑定的连接事务。

需要 Spring 代理生效；`new Service()` 或同对象内部直接调用不能当作有效代理事务示例。不要同时叠加两种事务注解作为默认用法。

兼容边界：当前框架切面本身不解析 propagation、isolation、rollbackFor 等注解属性；Spring 事务拦截器会负责其自身事务属性，但不能据此保证框架自管连接路径与 Spring 的所有语义一致。`ConnectionUtil` 在 `loadBalanceList().size() > 1` 时使用自有连接池，异步更新也走自有连接路径。多路由、异步、REQUIRES_NEW/嵌套事务、noRollbackFor 等场景需另做集成验证，不承诺自动跨数据源原子提交。

另外保留框架注解 `com.yulinlin.data.core.anno.JoinTransaction`。其 `String[] value()` 当前没有被切面读取，不要依靠该参数选择事务会话。两种注解不是可以无条件互换的全部事务语义实现。

## 标准业务方法写法

```java
import org.springframework.transaction.annotation.Transactional;

// 放在 Spring 管理的 Service public 方法上，并通过代理调用。
@Transactional(rollbackFor = Exception.class)
public void saveBusinessData() {
    // 在这里执行本次业务需要的 ORM 操作
}
```

本专题核对了注解与连接接入路径，没有执行事务回滚集成测试。验收应至少覆盖正常提交、异常回滚、同类自调用，以及项目实际使用的传播行为。
