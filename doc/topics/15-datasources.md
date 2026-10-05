# 多数据源：创建、注册与选择 JDBC 会话

> 状态：2026-10-05 按数据库模块自行注册 Session 和负载均衡默认组更新。本轮未运行测试、编译或打包，未连接真实数据库运行多数据源集成测试。
> 适用：JDK 25、制品版本 3.0、Spring Boot 3.5；本例两个数据源均为 MySQL。
> 源码：`JdbcSessionFactory`、`YulinlinCoreAutoConfig.routeSession`、`DataJdbcApplication`、`MysqlParseAutoConfig`、`RegisterSession`、`JoinSessionAop`、`RouteSession`。

## 1. create 与注册不是同一步

```java
JdbcSession create(DataSource dataSource, String group)
JdbcSession create(DataSourceProperties properties, String group)
```

`group` 是路由使用的会话组名，不是数据库名，也不是 Spring Bean 名。工厂会设置解析器、编码器、缓存、过滤器等依赖并返回会话，但 **create 本身不注册路由**。

Spring 推荐路径：将返回对象声明成 `@Bean`。core 自动配置注入 `List<EntitySession>`，然后执行 `routeSession.registerSession(list)`。因此通常无需自己再 registerSession。

| Spring DataSource Bean | Spring 会话 Bean | 会话组 | 生成方式 |
| --- | --- | --- | --- |
| `dataSource`（@Primary） | `mysqlSession` | `mysql` | MySQL 模块自动配置用自己的工厂创建 |
| `ossDataSource` | `ossSession` | `oss` | 自定义 Bean 调用 factory.create |

`@Primary` 解决 Spring DataSource 注入歧义；group 是框架路由名，二者不同。模块默认组为 mysql、postgresql、sqlite。未指定 group 且只有一个注册组时自动使用该组；多个组时使用 yulinlin.datasource.default-group 或 setDefaultGroup 指定的组，未设置或组不存在时明确报错，不按注册顺序猜测。primary 也是普通组名，没有隐藏的优先级。

### 全局默认会话组

下面设置应用共享负载均衡器的默认组；mysql 必须是已注册的会话组，不是 DataSource Bean 名：

```yaml
yulinlin:
  datasource:
    default-group: mysql
```

也可通过代码设置。以下是业务方法体片段，loadBalance 为容器注入的 `com.yulinlin.data.core.loadbalan.LoadBalance` 单例：

```java
loadBalance.setDefaultGroup("mysql");
String configured = loadBalance.getDefaultGroup(); // 配置值
String effective = loadBalance.defaultGroup();    // 实际默认组，单组时直接使用唯一组
```

配置只影响未显式选组的请求，Model Wrapper 的 group 参数、模型 @JoinSession 和当前 Service 会话上下文仍优先。只有一个组时不要求 default-group，即使配置值与唯一组不同也直接使用唯一组；存在多个节点的同组仍按集群标签和权重选节点，不直接拿第一个节点。

框架默认 LoadBalance Bean 会绑定此配置。若用户自己声明 LoadBalance Bean，应自行注入 LoadBalanceProperties 或调用 setDefaultGroup；自定义实现需要支持默认组接口。运行时修改会影响后续未限定请求，应在没有在途业务事务的配置窗口进行。

注册和移除发布只读快照，读路径不加注册锁。权重必须非负，0 表示不参与选择；权重总和使用 long，随机区间为 [0, total)，避免第一节点偏置。单个可用节点不执行随机选择，但不会绕过主从标签或健康过滤。RandomLoadBalance.ping() 更新健康快照；现有 heartbeat 不启动后台定时任务，不能把这项优化描述成新增自动巡检。

非事务路由不再永久缓存已选节点，后续请求能看到权重或健康变化；事务内仍按组和标签保持节点固定。快照保护集合访问，并不等于动态卸载、关闭底层资源对在途请求完全安全；运行中的 group/cluster 不宜随意修改。

## 2. 完整配置：保留自动 mysql，再新增 oss

前提：已引入 starter + mysql，配置类在应用组件扫描范围内。此例替代原单数据源手工配置；不要再保留另一份同名 DataSource Bean。

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/main_db
    username: ${MAIN_DB_USERNAME}
    password: ${MAIN_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
oss:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/oss_db
    username: ${OSS_DB_USERNAME}
    password: ${OSS_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

```java
package demo.config;

import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class MultiDataSourceConfig {
    @Bean("mainDataSourceProperties")
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mainProperties() {
        return new DataSourceProperties();
    }

    @Bean("dataSource")
    @Primary
    public DataSource mainDataSource(
            @Qualifier("mainDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("ossDataSourceProperties")
    @ConfigurationProperties("oss.datasource")
    public DataSourceProperties ossProperties() {
        return new DataSourceProperties();
    }

    @Bean("ossDataSource")
    public DataSource ossDataSource(
            @Qualifier("ossDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("ossSession")
    public JdbcSession ossSession(@Qualifier("mysqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("ossDataSource") DataSource dataSource) {
        return factory.create(dataSource, "oss");
    }
}
```

为什么主数据源也显式定义：应用新增 DataSource Bean 会影响 Boot 默认数据源的条件装配；不要只定义第二个数据源，却假定主数据源一定仍会自动创建。本例明确提供两个数据源，并用 @Primary 指定 MySQL 模块的 `mysqlSession` 应使用哪一个。

本例已有组 mysql 的自动会话，不要再额外声明另一个同组会话。各模块直接调用自己的工厂，不检查 JDBC URL，也不由公共 JDBC 层选择工厂。两个不同会话对象使用同一组名会作为同组节点注册，不是按组名覆盖。手动覆盖某模块会话时使用对应默认 Bean 名；声明 jdbcSession 则会让 MySQL 和 PostgreSQL 的默认创建都退让。

同时引入 MySQL 与 PostgreSQL 时，默认组名虽然不同，但两边仍可能注入同一个 @Primary DataSource。group 名不会自动选中对应的 DataSource Bean，也不会修正错配的驱动或 SQL。应显式声明由正确工厂和数据源创建的会话；有 @Primary 主库时可将主会话命名为 jdbcSession 禁用双方默认创建，再为其他库注册独立组。完整例子见 PostgreSQL 专题。

与 SQLite 或 PostgreSQL 共存时，工厂必须用 `@Qualifier("mysqlSessionFactory")` 指定；PostgreSQL 使用 `@Qualifier("postgresqlSessionFactory")`。直接注入会话时也应使用实际生效的 Bean 名，例如 `@Qualifier("mysqlSession")`，避免同时引入多个模块或存在多个会话时仅按 JdbcSession 父类型注入产生歧义。手动 create 由调用方正确选择工厂。

工厂应使用 Spring 注入的实例，不能直接 `new JdbcSessionFactory(...)` 后就调用 create，因为其内部依赖需要注入。SQL 差异由对应 ParseManager 注册的解析器处理，JDBC 参数绑定和结果读取差异由 Session 的扩展方法处理；PostgreSQL 工厂创建 PostgresqlSession 并设置 PostgresqlParseManager，MySQL 工厂创建公共 JdbcSession 并设置 MysqlParseManager。不能仅把 MySQL 工厂的数据源地址改为 PostgreSQL/Oracle 就承诺兼容。

## 3. 如何指定使用 oss

### 方式 A：业务方法上的 @JoinSession

```java
package demo.service;

import com.yulinlin.data.core.anno.JoinSession;
import demo.domain.DemoUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class OssUserService {
    @JoinSession("oss")
    @Transactional(rollbackFor = Exception.class)
    public List<DemoUser> findEnabled() {
        return new DemoUser().createSelectWrapper()
                .eq(DemoUser::getStatus, 1).selectList();
    }
}
```

DemoUser 定义在 ORM 专题中，本例假设 oss_db 也有该实体对应的表。调用必须经过 Spring 代理；不能靠同类自调用切换数据源。注解也可放在 Service 类上，方法注解优先于 Service 类注解。

### 方式 B：实体固定会话组

在已有实体类上添加 `@com.yulinlin.data.core.anno.JoinSession("oss")`。RouteSession 在请求未显式指定组时读取模型注解；这不是 DataSource Bean 的 qualifier。

### 方式 C：模型包装器显式指定

```java
import com.yulinlin.common.model.ModelSelectWrapper;
import demo.domain.DemoUser;

// 放在业务方法中；事务边界按下节说明设置
var query = ModelSelectWrapper.newInstance("oss", new DemoUser());
var users = query.selectList();
```

主路径组选择优先级：请求显式组 → 模型 @JoinSession → Service 切面压入的当前组 → 负载均衡器默认组选取（单组自动使用；多组使用 default-group，或要求显式指定）。不要在模型固定 oss 后，假定 Service 上的另一个组一定覆盖它。

## 4. 事务与生命周期限制

- Spring `@Transactional` 已接入框架切面，但 `@JoinSession` 不是 Spring 事务管理器选择器，不会自动创建或切换 PlatformTransactionManager。
- JdbcSession 自己管理事务和连接，不再根据已注册的组数切换连接模式。没有外层事务时，每次 ORM 请求自动完成自己的事务；Session 也能直接执行已准备的 ExecuteRequest/QueryRequest，不依赖 RouteSession。
- RouteSession 在事务中按需加入会话，只结束自己加入的那一层事务；独立 Session 已有的外层事务仍由调用方结束。业务需要合并多个请求时，使用框架事务注解或 route.transaction。
- 检测到同一个 DataSource 已由 Spring 绑定事务连接时，保持调用线程上的单连接执行，由 Spring 完成物理提交/回滚，不再另开并发连接绕过 Spring。
- 跨库提交不是 XA/分布式原子事务；Propagation、隔离、noRollbackFor、异步等语义不能仅凭同名注解推定完全一致。详细边界见事务专题。
- DataSource 单独声明成 Bean 有利于容器管理连接池生命周期。`create(DataSourceProperties, group)` 内部会新建 DataSource；不要默认它等价于单独的、由容器销毁的 DataSource Bean。

## 5. 手动注册与验收

非 Bean 动态会话可以在路由初始化完成后调用：

```java
// factory、route 和 dataSource 均为已配置好的对象
JdbcSession session = factory.create(dataSource, "reporting");
route.registerSession(session);
```

这里 route 类型为 `com.yulinlin.data.core.session.RouteSession`。不要在创建会话 Bean 的方法中反向注入 RouteSession 来注册，可能形成初始化循环。已经作为 EntitySession Bean 自动注册的会话无需再次注册。

当前注册表没有提供完整的并发动态管理保证；不要将这个片段当作运行时随意增加/移除租户库的生产方案。

启动后检查 `SessionUtil.route().loadBalanceList()` 是否包含 mysql、oss。主库查询显式选择 mysql，例如 `ModelSelectWrapper.newInstance("mysql", DemoUser.class).selectList()`。两个库预置不同标记数据，通过代理调用上述 Service 确认选库；分别验证正常提交、异常回滚和跨库失败行为。此文档没有替你执行这些数据库操作。

## 6. 大集合：最多 4 个连接，每次 JDBC batch 默认 256 条

普通集合 execute 本身已经使用 JDBC batch；增加 `.batch()` 才申请多连接并发写入，不需要修改原业务 API。先检查实际 Session 的 `supportsParallelWrites()`，不支持时**不做并发分组**，在调用线程用一个连接处理整批数据。

支持时，将全部解析结果均匀分成最多 x 个大组，每组提交一个任务，在整个任务内持续使用一个连接。同一 SQL 模板复用一个 PreparedStatement，每满 256 条执行一次 `executeBatch()`，最后执行不足 256 条的尾批。比如 10 万条、4 个连接：最多 4 个任务，每组 2.5 万条，而不是提交数百个 128 条任务。

`ExecuteRequest.batchSize` 仍是启用异步执行的**整次请求最小条数**，默认 128；不等于 JDBC batch 大小。整次请求不足阈值时合为一组同步执行；达到阈值后，即使某个工作组不足 128 条也可并发。未调用 `.batch()` 或未提供执行器时不并发分组。解析仍会持有整个输入集合，不是流式导入。

```java
ModelInsertWrapper.newInstance("oss", usersToInsert).batch().execute();
// 更新、删除也保留现有的 .batch() 入口。
```

默认值可省略；用户在应用外围配置：

```yaml
yulinlin:
  datasource:
    jdbc:
      parallel-connections: 4
      execute-batch-size: 256
```

两项均要求正整数，独立控制并发连接数和一次 `executeBatch()` 的行数。`parallel-connections: 1` 表示不并发分组。连接数是**每个 Session、每个框架事务的物理连接上限**，不是整个应用的并发上限，也不是连接池的 maximumPoolSize。连接按需创建，不会每次请求固定打开 4 个；HikariDataSource 下还会限制为池的 maximumPoolSize，实际并行度也受数据量和执行器线程数限制。

可以按会话覆盖，在会话未开启事务时调用：

```java
JdbcSession session = factory.create(dataSource, "reporting");
session.setParallelConnections(2);
session.setExecuteBatchSize(512);
boolean parallelAllowed = session.supportsParallelWrites();
// Spring Bean 或手动注册均沿用前文方式。
```

`supportsParallelWrites()` 是与当前线程事务上下文有关的能力查询，不会为了查询而打开连接；它不表示连接池当前还有多少空闲连接，也不保证数据库性能收益。SQLite 内置会话固定使用 1 个连接，返回 false，不受通用默认值 4 影响；Spring 已绑定事务连接或 Hikari 最大连接数为 1 时也返回 false。上层统一通过这个函数决定是否分组，不另建 SQLite Session 子类。要用多连接批处理，用框架 `@JoinTransaction`/route.transaction，避免同时用 Spring 事务管理器绑定同一数据源。

**执行 JDBC batch 不等于提交事务。**每 256 条只发送一批参数，所有组执行完成后才沿用 Session/RouteSession 的事务提交边界；后续批次失败，单连接事务中之前执行的批次一起回滚。驱动返回 `SUCCESS_NO_INFO` 时按一条成功命令计数，不代表精确受影响行数；`EXECUTE_FAILED` 或 BatchUpdateException 都使请求失败。

批次提交或同步执行中途失败时，框架仍等待**所有已提交任务**结束，再处理回滚和释放连接；不会因为最后一批是同步任务而提前返回。内层回滚或执行失败被业务捕获，也会标记 rollback-only，不能继续把外层 JDBC 事务正常提交。

多连接拥有各自的数据库事务，逐个提交，不是一个原子数据库事务：提交中途失败时，已经提交的连接无法撤销；提交前跨连接查询也不保证读到其他连接的未提交写入。需要单库严格原子性和事务内读己之写，配置 `parallel-connections: 1`。外围连接池需给其他请求预留容量；多个批量事务同时占用连接仍可能等待或超时，不应把并发值等同于无成本加速。未连接真实 MySQL 做吞吐量测试，不承诺 4 倍性能。
