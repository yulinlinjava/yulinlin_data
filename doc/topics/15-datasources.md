# 多数据源：创建、注册与选择 JDBC 会话

> 状态：2026-10-04 按当前源码核对，未连接真实数据库运行多数据源集成测试。
> 适用：JDK 25、制品版本 3.0、Spring Boot 3.5；本例两个数据源均为 MySQL。
> 源码：`JdbcSessionFactory`、`YulinlinCoreAutoConfig.routeSession`、`MysqlParseAutoConfig`、`RegisterSession`、`JoinSessionAop`、`RouteSession`。

## 1. create 与注册不是同一步

```java
JdbcSession create(DataSource dataSource, String group)
JdbcSession create(DataSourceProperties properties, String group)
```

`group` 是路由使用的会话组名，不是数据库名，也不是 Spring Bean 名。工厂会设置解析器、编码器、缓存、过滤器等依赖并返回会话，但 **create 本身不注册路由**。

Spring 推荐路径：将返回对象声明成 `@Bean`。core 自动配置注入 `List<EntitySession>`，然后执行 `routeSession.registerSession(list)`。因此通常无需自己再 registerSession。

| Spring DataSource Bean | Spring 会话 Bean | 会话组 | 生成方式 |
| --- | --- | --- | --- |
| `dataSource`（@Primary） | `jdbcSession` | `primary` | MySQL 自动配置创建 |
| `ossDataSource` | `ossSession` | `oss` | 自定义 Bean 调用 factory.create |

`@Primary` 解决 Spring 注入歧义；字符串 `primary` 是框架默认路由组。这两个概念不同。

## 2. 完整配置：保留自动 primary，再新增 oss

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

为什么主数据源也显式定义：应用新增 DataSource Bean 会影响 Boot 默认数据源的条件装配；不要只定义第二个数据源，却假定主数据源一定仍会自动创建。本例明确提供两个数据源，并用 @Primary 指定自动 `jdbcSession` 应注入哪一个。

不要额外声明 `factory.create(mainDataSource, "primary")` 的会话 Bean：当前 MySQL 自动配置按 Bean 名 `jdbcSession` 条件创建主会话。两个不同会话对象使用同一组名会作为同组节点注册，不是按组名覆盖。与 SQLite 共存时，工厂必须用 `@Qualifier("mysqlSessionFactory")` 指定，不能把 SQLite 解析器用于 MySQL。

工厂应使用 Spring 注入的实例，不能直接 `new JdbcSessionFactory(...)` 后就调用 create，因为其内部依赖需要注入。当前 MySQL 工厂使用 MySQL 解析器，不能用它直接承诺连接 PostgreSQL/Oracle 后 SQL 方言也正确。

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

主路径组选择优先级：请求显式组 → 模型 @JoinSession → Service 切面压入的当前组 → 默认 primary。不要在模型固定 oss 后，假定 Service 上的另一个组一定覆盖它。

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

启动后检查 `SessionUtil.route().loadBalanceList()` 是否包含 primary、oss。两个库预置不同标记数据，通过代理调用上述 Service 确认选库；分别验证正常提交、异常回滚和跨库失败行为。此文档没有替你执行这些数据库操作。

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
