# PostgreSQL 接入与方言范围

> 状态：2026-10-05 按解析器注册机制和数据库模块自行创建 Session 更新。本轮未运行测试、编译或打包；此前回归结果不代表本轮验证。真实 PostgreSQL 测试需显式提供测试库，默认跳过。没有 PostgreSQL 性能实测。
> 适用：JDK 25、制品版本 3.0、Spring Boot 3.5。
> 源码：PostgresqlAutoConfiguration、PostgresqlSession、PostgresqlParseManager、SqlParseManager、SqlParamsContext、DataJdbcApplication、JdbcSessionFactory。

PostgreSQL 使用 PostgresqlSession，继承公共 JdbcSession 的连接、事务、批处理与查询执行。Session 设置 PostgresqlParseManager，由它注册 PostgreSQL 专属解析器生成 SQL；Session 只额外处理驱动参数绑定与结果读取，不承担 SQL 拼接，也不使用独立 SqlDialect。postgresql 模块可独立使用，不依赖 mysql 模块或 MySQL 驱动，测试也不引入 mysql；不能把 mysqlSessionFactory 用于 PostgreSQL。

## 1 依赖与单数据源配置

最小依赖只需 postgresql，公共 jdbc、core 和 lang 会传递引入；不需要 mysql 或 starter。通过团队制品仓库或本地发布获取，不假设已经发布到 Maven Central。

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>postgresql</artifactId>
    <version>3.0</version>
</dependency>
```

正常 Spring Boot 自动配置链下无需额外启用注解。已有表与可用的数据库账号是前提，本模块不扫描建表。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/demo?stringtype=unspecified
    username: ${PG_USERNAME}
    password: ${PG_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
```

日期、枚举、BigDecimal、Map、集合和嵌套 Bean 等仍经过现有 JDBC 编码器，部分值编码成字符串。示例设置 `stringtype=unspecified`，让 PostgreSQL 根据目标列或 SQL 上下文推断参数类型，避免将 JSON 字符串直接绑定为 varchar 后写入 jsonb 等类型时报错。它是驱动配置，不是框架自动添加的参数；每个 PostgreSQL 数据源都要分别设置。[pgJDBC 参数说明](https://jdbc.postgresql.org/documentation/use/)

该参数不能解决所有类型推断问题。无明确类型上下文的自定义 SQL 应写显式 CAST，例如 `CAST(#{value} AS jsonb)`；不应假定 UUID、数组、空间类型、原生 enum 等 PostgreSQL 扩展类型已经都有专用编码器。

PostgresqlAutoConfiguration 在模块内部注册 `postgresqlSessionFactory`，并直接用自己的工厂创建 Bean `postgresqlSession`，实际类型为 PostgresqlSession，组名为 `postgresql`；core 自动收集实际的会话对象注册到 RouteSession。公共 DataJdbcApplication 只提供 JDBC 通用组件，不再集中匹配工厂或创建默认 Session。注入时可使用父类型 JdbcSession 配合 `@Qualifier("postgresqlSession")`，多模块共存时不要仅按父类型猜测会话。

- 模块直接调用自己的工厂创建会话，不读取或校验 JDBC URL；引入模块和配置正确的 DataSource 是调用方责任。默认 Bean 名与组名不同，但这不表示自动选择了不同的物理数据源。
- 同时引入 mysql 与 postgresql 时，应显式创建各自会话，防止两边默认注入同一个 @Primary DataSource；示例见下节。
- 多个 DataSource 必须指定 @Primary，或者显式声明每个会话；无主候选时不会任意选库。
- 自定义 Bean 名 `postgresqlSession` 或兼容入口 `jdbcSession` 会使 PostgreSQL 默认创建退让。默认配置不再提供名为 jdbcSession 的会话，旧的按名注入需迁移或自行声明兼容 Bean。
- 会话初始化不要求连接池暴露 URL 获取方法，也不通过打开连接来识别类型；不匹配的驱动或数据库可能到执行 SQL 时才报错，初始化成功不能证明数据库类型兼容。
- 只有 postgresql 一个会话组时可以省略选组参数；多个组并存时显式选 postgresql，除非用户另外注册了旧 primary 组作为默认。

## 2 复用实体和 CRUD

原有 @JoinTable、@JoinField、@JoinMeta、ModelSelectWrapper 等 API 保持不变，主键仍由应用或实体基类生成，不增加数据库自增键回填功能。完整实体与 Service 组织方式见 ORM 专题。

本节示例使用 com.yulinlin.common.model 下的便利门面，需要按需额外引入 common，或者引入已包含 common 的 starter；这不是 postgresql 模块的强制依赖。只使用公共 JdbcSession 与低层 Wrapper 时不需要它们。

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>common</artifactId>
    <version>3.0</version>
</dependency>
```

下面是方法体片段；PgUser 是业务实体占位名，需已经映射到对应表。会话组必须已注册。

```java
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.common.model.ModelDeleteWrapper;

// postgresql 是模块默认组；使用其他自定义组时相应替换。
ModelInsertWrapper.newInstance("postgresql", user).execute();

var users = ModelSelectWrapper.newInstance("postgresql", PgUser.class)
        .eq("status", 1).orderByAsc("id").selectList();

var page = ModelSelectWrapper.newInstance("postgresql", PgUser.class)
        .eq("status", 1).orderByAsc("id").selectPage(1, 20);

PgUser patch = new PgUser();
patch.setId("existing-id");
patch.setName("updated");
ModelUpdateWrapper.newInstance("postgresql", patch).execute();

// 必须提供正确主键或明确条件；没有默认的全表写入保护。
ModelDeleteWrapper.newInstance("postgresql", patch).execute();
```

表名、列名和别名使用 PostgreSQL 双引号；普通 schema 限定名按段引用，例如 `public.pg_user`。大小写必须与实际建表一致：未引用的 PostgreSQL 标识符通常转为小写，驼峰实体字段应明确映射到实际列名，推荐数据库采用小写下划线命名。[PostgreSQL 标识符规则](https://www.postgresql.org/docs/current/sql-syntax-lexical.html)

## 3 与 MySQL 同时使用

MySQL 使用公共 JdbcSession，PostgreSQL 使用其子类 PostgresqlSession，两者沿用相同会话接口和事务实现。给不同数据库选择各自工厂，再指定组名；不要把工厂 Bean 名和路由组名混为一谈。

以下配置类假设应用已定义名为 dataSource 的 MySQL 主 DataSource 和 pgDataSource 的 PostgreSQL DataSource，主库标记 @Primary。显式声明兼容名 jdbcSession，使两个模块的默认创建退让，再用各自工厂绑定正确的数据源和组名。这里 mysqlSession 只是同一个主会话的 Bean 别名，不会重复注册对象。完整 DataSourceProperties 定义方式见多数据源专题；pgDataSource 的 URL 同样需要按编码器使用情况设置 stringtype。

```java
package demo.config;

import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class PgSessionConfig {
    @Bean(name = {"jdbcSession", "mysqlSession"})
    public JdbcSession mysqlSession(
            @Qualifier("mysqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("dataSource") DataSource dataSource) {
        return factory.create(dataSource, "mysql");
    }

    @Bean("postgresqlSession")
    public JdbcSession postgresqlSession(
            @Qualifier("postgresqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("pgDataSource") DataSource dataSource) {
        return factory.create(dataSource, "postgresql");
    }
}
```

返回的会话 Bean 会自动注册，不在 Bean 创建方法中反向注入 RouteSession。业务使用 `ModelSelectWrapper.newInstance("postgresql", PgUser.class).selectList()` 或 `@JoinSession("postgresql")` 选库。factory.create 本身不执行路由注册，也不自动确认手动传入的数据源是否与方言匹配。

Spring @Transactional、框架 @JoinTransaction、懒加载和 setter 懒同步沿用公共实现。多个数据库或多个写连接不是 XA 两阶段提交，不能承诺跨库原子提交；多路由与 Spring 已绑定连接的边界必须按事务专题配置。

## 4 日期与 JSON

| 功能 | PostgreSQL 实现与限制 |
| --- | --- |
| 普通 CRUD、JOIN、IN、聚合 | 使用共用 SQL 解析器；自定义 SQL 表达式不自动翻译 |
| 分页 | LIMIT size OFFSET offset |
| 行锁 | FOR UPDATE；需要实际事务边界 |
| 日期分组 | date_trunc 加 to_char；支持分钟、小时、日、月、季度、年 |
| 数值间隔分组 | 先 CAST 为 numeric，避免整数除法；间隔必须大于 0 |
| 聚合 HAVING | 将所选聚合别名展开为原表达式，如 count(*)，不直接引用输出别名 |
| 分组表达式 | GROUP BY 展开原分组表达式，避免日期分组别名与原列同名时分组粒度错误 |
| 原生布尔列 | 按 JDBC 布尔类型读取，保留 false 与 NULL，不把 PostgreSQL 的 f 当作 true |
| JSON 路径读取 | CAST 为 jsonb 后用 #>> 提取文本，支持 text、json、jsonb 列中的合法 JSON |
| JSON 数字或布尔条件 | 根据条件值生成 numeric 或 boolean CAST；不自动处理脏数据 |
| JSON 局部更新 | 尚未提供自动 jsonb_set 生成，路径赋值会明确拒绝；整字段替换沿用普通更新 |

日期若沿用现有字符串编码并存为 TEXT，应统一固定格式与时区约定，才能依赖字符串范围比较。原生 timestamp/date 列的字符串写入需确认驱动类型推断与格式；日期分组会 CAST 到 timestamp，不提供按业务时区自动转换 timestamptz 的保证。

下面是低层 Wrapper 方法体片段。实体对应的 JSON 属性仍可定义为 Map、List 或普通 Bean，由编码器完成整字段读写；JSON 路径选择的结果是文本，不是自动推断的 Java 数字或对象。

```java
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;

var query = new SelectWrapper<>().table("pg_user");
query.fields().field("id", "id").field("payload->profile->name", "nickname");
query.where().eq("payload->profile->name", "alice")
        .gte("payload->age", 18)
        .eq("payload->enabled", true);
// 同一种对象路径也可用 nested：
query.where().nested("payload", nested -> nested.eq("city", "Shanghai"));

var group = new GroupWrapper<>().table("pg_user");
group.aggregations().day("created_at", "day");
group.metrics().count("*", "totalCount");
group.having().gt("totalCount", 1);
```

这些是 SQL 构造片段，不会自行执行查询；Model Wrapper 的执行方式见 ORM 专题。JSON 路径以 `->` 分段，例如 `payload->items->0->name`。不存在的路径得到 SQL NULL；数字条件遇到不能转换的字符串、非 JSON 文本列遇到非法 JSON 时，数据库会报错，不会静默跳过。JSON 对象与数组整体比较、混合类型 IN、包含查询和 JSON 索引优化不在当前自动适配范围内。[PostgreSQL JSON 操作](https://www.postgresql.org/docs/current/functions-json.html)

日期分组依赖 PostgreSQL 的 [日期函数](https://www.postgresql.org/docs/current/functions-datetime.html)及 [格式化函数](https://www.postgresql.org/docs/current/functions-formatting.html)。季度按所在季度的起始月分组。别名、原始 SQL、表达式中的表名或函数是可信代码，不可由未校验的 HTTP 参数直接拼接。

## 5 大集合写入

普通集合写入复用 JDBC executeBatch。`.batch()` 仍按公共能力判断是否启用多连接，默认最多 4 个连接、每次 executeBatch 256 条。PostgresqlSession 不重写这套连接、事务和批处理流程。

可在 URL 中显式添加 `reWriteBatchedInserts=true`，让 pgJDBC 将兼容批量 INSERT 改写为多值 INSERT；并非框架替你开启，也不是所有 SQL 都能改写。[pgJDBC 参数说明](https://jdbc.postgresql.org/documentation/use/)

多连接不保证比单连接更快。需要单库严格原子事务时配置 parallel-connections 为 1；驱动返回 SUCCESS_NO_INFO 时只统计成功命令，不承诺精确受影响行数。连接池容量、索引、冲突、磁盘与 WAL 都会影响实际性能。

byte[] 参数由驱动绑定；InputStream 在 PostgreSQL 下使用 setBinaryStream，目标通常是 bytea，不沿用 MySQL Blob 绑定。除 PostgreSQL 原生布尔列按布尔类型读取外，现有 ORM 查询结果沿用字符串编码器解码，尚不承诺 bytea 或 PostgreSQL oid 大对象的完整二进制读回；需要时使用专用编码器或 JDBC 读取。

## 6 迁移与验证

新代码使用：

- `com.yulinlin.jdbc.postgresql.PostgresqlSession`
- `com.yulinlin.jdbc.postgresql.PostgresqlParseManager`
- `com.yulinlin.jdbc.postgresql.PostgresqlAutoConfiguration`
- `@Qualifier("postgresqlSessionFactory")`

已移除 PostgreSQL 包内历史 MysqlParseManager、MysqlParseAutoConfig、重复解析器和分页工具，包括空继承的兼容转发类，以及独立的 SqlDialect、PostgresqlDialect。当前 PostgresqlParseManager 继承 SqlParseManager，在 init 中先调用 super.init() 注册公共 CRUD，再注册 PostgreSQL 的 NameParse、PageParse、DateParse、IntervalParse；只有确实不同的 SQL 才单独实现，普通节点不增加重复类。MySQL 同样保留 MysqlParseManager 注册自己的日期和数值区间解析器。

如果外部代码直接引用旧 PostgreSQL 类，必须迁移到上述新入口并重新编译；不要仅替换 JAR。旧 PG 工厂名 mysqlSessionFactory 必须改为 postgresqlSessionFactory；真正的 MySQL 工厂仍叫 mysqlSessionFactory。

业务仍使用原有 Model Wrapper、Request、RouteSession 和 factory.create(dataSource, group)。默认主会话 Bean 从 jdbcSession 改为 postgresqlSession，按名注入必须相应更新；默认组从 primary 改为 postgresql，显式选择旧 primary 的业务也需迁移或自行注册旧组。仅有一个组时仍支持无组参数查询；多个组时应明确选库，Wrapper API 不变。额外数据源仍需显式声明对应 Session Bean，由 core 自动注册到路由。SqlParamsContext 只携带当前 ParseManager、字段解析器与请求内的 SELECT 别名，不引用 Session，也不把别名写回共享的模型映射。在注册表初始化后不再修改的前提下，同一数据库语法的多个会话可以复用一个 ParseManager；不同数据库要使用各自的注册表。

低层解析可调用 session.parseSql(node, params)，也可直接使用 new PostgresqlParseManager().parse(node, params)；两者只生成 SQL，不获取连接或执行查询。不要把公共 SqlParseManager 或 MysqlParseManager 当作 PostgreSQL 解析器。增加数据库适配时继承 SqlParseManager，在 init 中注册确有差异的 IParse 实现；仅有驱动读写差异时再继承 JdbcSession，重写 bindParameter、readColumn 并设置对应解析器。通过工厂指定 JDBC 地址前缀及 Session 构造函数，再由数据库模块自动配置直接创建自己的 Session Bean，无需公共 JDBC 工厂选择逻辑，也无需复制 CRUD 解析器或事务代码。

不需要外部数据库的专项测试：

```powershell
mvn -pl postgresql,sqlite -am `
  -Dtest=PostgresqlSessionTest,PostgresqlAutoConfigurationTest,JdbcSessionTransactionTest,JdbcBatchExecutionTest,SqliteIntegrationTest,SqliteSchemaManagerTest,OrmProxyIntegrationTest `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

上述命令包含临时 SQLite 数据库测试，但不连接外部 MySQL 或 PostgreSQL。真实 PostgreSQL 测试默认跳过，手动启用：

```powershell
$env:PG_TEST_URL = 'jdbc:postgresql://127.0.0.1:5432/test_db'
$env:PG_TEST_USER = 'test_user'
$env:PG_TEST_PASSWORD = '<测试账号密码>'
mvn -pl postgresql -am -Dtest=PostgresqlServerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

PostgresqlServerTest 在一个测试连接中创建临时表；连接关闭后清理，不使用应用的数据库配置，不修改业务表。入口覆盖共用 JdbcSession 的批处理、CRUD、字符串日期与 BigDecimal、jsonb 编解码、JSON 条件、分组 HAVING、分页行锁和回滚；不覆盖真实服务中的连接池并发、跨库提交或 ORM 代理链。只有实际启用并通过后才能声称真实 PostgreSQL 集成测试通过。
