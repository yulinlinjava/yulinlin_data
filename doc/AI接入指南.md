# yulinlin-data：外部 AI 单文件接入指南

---

> 自动生成，请勿直接编辑。维护源为 doc/topics/，生成命令：./doc/build-ai-docs.ps1。

---

用途：上传一个文件给外部 AI。已包含当前全部专题；无需再上传相同专题、旧案例或性能报告。制品版本 3.0；JDK 25；Spring Boot 3.5。示例的验证范围见各专题。

---

阅读顺序：上下文 → 按任务阅读 ORM/事务、HTTP、反射或其他工具 → 排障与交付检查。示例地址、表名、账号均为占位，执行写操作前必须按业务确认。

---

<!-- source: doc/topics/00-context.md -->
## 项目上下文与生成代码约定

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`pom.xml`；各模块 `pom.xml`。若与实际安装版本冲突，以该版本源码为准。

1. 这是自定义 ORM，不是 MyBatis-Plus、JPA 或 Spring Data。不要生成 `BaseMapper`、`LambdaQueryWrapper`、`@Entity` 等其他框架 API 来替代本文接口。
2. 优先使用本文明确列出的入口、包名和签名。未列出的高级功能应查当前源码，不根据其他框架同名方法猜测。
3. 主键用 `@JoinMeta(primaryKey = true)`；不要沿用旧文档中的 `@JoinPrimary`。
4. 先确认表结构、实体基类、主键、筛选条件及会话，再生成写入代码。不要生成无条件更新或删除，也不要假设框架有全表写入拦截。
5. ORM 需要 Spring 上下文初始化完成、可用数据库会话与已存在的表；不要在静态初始化块中查询数据库。SQLite 可显式开启实体扫描创建缺失表（见 SQLite 专题）；其他路径不要假设自动建表。
6. 深克隆与 Bean 到 DTO 的类型映射不是同一能力。HTTP 的“不是 404”与“请求成功”也不是同一含义。
7. 编码之前确认实际依赖包含接口；如果缺少类或方法，先核对版本和运行时类路径，不用反射或异常吞掉掩盖版本错配。

### 模块与入口

| 需求 | 模块 | 准确入口 |
| --- | --- | --- |
| Spring Boot + MySQL ORM | starter + mysql | `com.yulinlin.common.domain.IdEntity` / `SuperEntity` |
| Spring Boot + SQLite ORM | starter + sqlite | `yulinlin.sqlite.file`，沿用相同实体与 Wrapper |
| 实体映射 | core | `com.yulinlin.data.core.anno.JoinTable`、`JoinField`、`JoinMeta`、`JoinWhere` |
| 数据库分页结果 | lang | `com.yulinlin.data.lang.util.Page`，不是 Spring Data Page |
| HTTP | core | `com.yulinlin.data.core.http.HttpRequestClient`、`HttpUtil` |
| HTTP 响应/异常/文件 | core | 同包下 `HttpResponse`、`HttpRequestException`、`HttpFile` |
| 反射与深克隆 | lang | `com.yulinlin.data.lang.reflection.ReflectionUtil` |
| JSON | lang | `com.yulinlin.data.lang.json.JsonUtil` |
| 字符串、日期 | lang | `com.yulinlin.data.lang.util.StringUtil`、`DateTime` |
| 树与 ID | common | `com.yulinlin.common.util.TreeUtil`、`SnowflakeUtil` |
| Web 响应包装 | starter | `com.yulinlin.starter.domain.R` |

MySQL 是本文完整示例路径。仓库存在其他数据库模块，不代表本文示例与事务行为已在所有数据库验证。admin 是示例/测试宿主，不是业务接入所需依赖。

---

<!-- source: doc/topics/10-orm.md -->
## ORM 接入与 CRUD

多个数据库的配置、`JdbcSessionFactory.create` 注册及选库，见同目录 `15-datasources.md`（单文件指南已包含该专题）。

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`common/.../domain/IdEntity.java`、`SuperEntity.java`；`core/.../model/`；`mysql/.../MysqlParseAutoConfig.java`。若与实际安装版本冲突，以该版本源码为准。

#### 3.1 ORM 项目

使用 JDK 25，业务项目采用 Spring Boot 3.5 系列依赖管理：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>starter</artifactId>
    <version>3.0</version>
</dependency>
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>mysql</artifactId>
    <version>3.0</version>
</dependency>
```

不要假设这些制品已发布 Maven Central；应使用团队制品仓库，或先从同一份源码安装相关模块。core/lang 等同组模块应保持同一构建来源。

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
yulinlin:
  http:
    timeout: 10s
```

当前 core、starter、mysql 等模块提供 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。在正常 Boot 自动配置链中无需额外的框架启用注解。MySQL 自动配置依赖 DataSource，并创建会话名 `primary`、Bean 名 `jdbcSession`。仅引入 starter 不会创建 MySQL 数据库会话。

只需反射/JSON 时可依赖 `com.yulinlin:lang:3.0`。只需 HTTP 时可依赖 `com.yulinlin:core:3.0`；但 core 在 Boot 中还包含 ORM 相关自动配置，不是一个专门拆分的纯 HTTP starter。项目已引入 starter 时无需重复声明 core。

#### 3.2 最小实体：避免隐式时间字段

下面以 `IdEntity` 为基类，仅继承 String 类型 id 和主键生成逻辑。一个 public 类放在对应的独立 `.java` 文件中。

```java
package demo.domain;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;

@JoinTable("ai_demo_user")
public class DemoUser extends IdEntity<DemoUser> {
    @JoinField(name = "user_name")
    @JoinWhere
    private String username;

    @JoinField
    @JoinWhere
    private Integer status;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
```

匹配上述模型的示例建表 SQL（仅在自己的演示库执行）：

```sql
CREATE TABLE ai_demo_user (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_name VARCHAR(100),
    status INT
);
```

`SuperEntity<T>` 在 `IdEntity<T>` 上增加 `crtTime`、`uptTime` 及插入/更新前填充逻辑；选择它时要让表结构与这些实际映射字段一致，不能直接套用上面的三列表。

注解规则：

- `@JoinTable` 标记表，`@JoinField(name = "...")` 指定列，`@JoinField(exist = false)` 排除字段。
- 不要假设没有 `@JoinField` 的字段一定会被忽略；当前工厂会处理一些未注解字段。非持久化字段应显式排除。
- 自定义主键建议同时标记 `@JoinField`、`@JoinMeta(primaryKey = true)`、`@JoinWhere`，不要仅依赖一个主键注解覆盖所有工厂行为。
- `@JoinWhere` 参与按对象构造条件；显式 `.eq(...)` 等链式条件可直接使用。
- 主键属性名与数据库列名不一致时，必须额外检查删除等工厂的映射行为，本文不承诺所有路径都正确处理重命名主键。

### CRUD 服务示例

```java
package demo.service;

import demo.domain.DemoUser;
import org.springframework.transaction.annotation.Transactional;
import com.yulinlin.data.lang.util.Page;
import org.springframework.stereotype.Service;

@Service
public class DemoUserService {
    public DemoUser findById(String id) {
        requireId(id);
        return new DemoUser().createSelectWrapper()
                .eq(DemoUser::getId, id).selectOne();
    }

    public Page<DemoUser> page(int pageNumber, int pageSize) {
        if (pageNumber < 1 || pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("Invalid page parameters");
        }
        return new DemoUser().createSelectWrapper()
                .eq(DemoUser::getStatus, 1)
                .orderByDesc(DemoUser::getId)
                .selectPage(pageNumber, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public String create(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        DemoUser user = new DemoUser();
        user.setUsername(username);
        user.setStatus(1);
        user.createInsertWrapper().execute();
        return user.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(String id, int status) {
        requireId(id);
        DemoUser patch = new DemoUser();
        patch.setId(id);
        patch.setStatus(status);
        return patch.createUpdateWrapper().execute();
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteById(String id) {
        requireId(id);
        DemoUser target = new DemoUser();
        target.setId(id);
        return target.createDeleteWrapper().execute();
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
    }
}
```

方法约定：

| API | 语义 |
| --- | --- |
| `createSelectWrapper().selectList()` | 返回 `List<E>` |
| `selectOne()` | 按第 1 页、每页 1 条查询，不是“多条即抛异常” |
| `selectPage(1, 20)` | 页码从 1 开始，返回框架 `Page<E>` |
| `Page.getList()` / `getTotal()` | 数据列表 / 总数；当前 total 是 int |
| `count()` / `exist()` | 计数 / 存在判断 |
| `createInsertWrapper().execute()` | 执行插入；IdEntity 未设置 id 时生成字符串 ID |
| `createUpdateWrapper().execute()` | 根据模型构造更新；当前工厂跳过 null 字段 |
| `createDeleteWrapper().execute()` | 根据模型主键构造删除；业务侧先校验主键 |

条件可用 `eq/ne/gt/gte/lt/lte/like/likeRight/between/in/nin/isNull`。不要把其他库的 `ge/le/notIn` 名称直接搬过来。复杂嵌套条件先确认 `ModelConditionWrapper` 和 `IConditionWrapper` 的实际签名。

将 patch 字段设为 null 不代表会生成 `SET column = NULL`；清空字段需求必须核查底层字段构造器生成的 SQL。不要把整个 HTTP 请求 DTO 不加限制地复制进更新实体，防止越权更新 id 或敏感字段。

事务说明请同时读取 `20-transactions.md`；上面的服务使用 Spring `@Transactional`。多表、级联等历史案例不属于已核验的完整接入示例。

---

<!-- source: doc/topics/15-datasources.md -->
## 多数据源：创建、注册与选择 JDBC 会话

> 状态：2026-10-04 按当前源码核对，未连接真实数据库运行多数据源集成测试。
> 适用：JDK 25、制品版本 3.0、Spring Boot 3.5；本例两个数据源均为 MySQL。
> 源码：`JdbcSessionFactory`、`YulinlinCoreAutoConfig.routeSession`、`MysqlParseAutoConfig`、`RegisterSession`、`JoinSessionAop`、`RouteSession`。

### 1. create 与注册不是同一步

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

### 2. 完整配置：保留自动 primary，再新增 oss

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

### 3. 如何指定使用 oss

#### 方式 A：业务方法上的 @JoinSession

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

#### 方式 B：实体固定会话组

在已有实体类上添加 `@com.yulinlin.data.core.anno.JoinSession("oss")`。RouteSession 在请求未显式指定组时读取模型注解；这不是 DataSource Bean 的 qualifier。

#### 方式 C：模型包装器显式指定

```java
import com.yulinlin.common.model.ModelSelectWrapper;
import demo.domain.DemoUser;

// 放在业务方法中；事务边界按下节说明设置
var query = ModelSelectWrapper.newInstance("oss", new DemoUser());
var users = query.selectList();
```

主路径组选择优先级：请求显式组 → 模型 @JoinSession → Service 切面压入的当前组 → 默认 primary。不要在模型固定 oss 后，假定 Service 上的另一个组一定覆盖它。

### 4. 事务与生命周期限制

- Spring `@Transactional` 已接入框架切面，但 `@JoinSession` 不是 Spring 事务管理器选择器，不会自动创建或切换 PlatformTransactionManager。
- JdbcSession 自己管理事务和连接，不再根据已注册的组数切换连接模式。没有外层事务时，每次 ORM 请求自动完成自己的事务；Session 也能直接执行已准备的 ExecuteRequest/QueryRequest，不依赖 RouteSession。
- RouteSession 在事务中按需加入会话，只结束自己加入的那一层事务；独立 Session 已有的外层事务仍由调用方结束。业务需要合并多个请求时，使用框架事务注解或 route.transaction。
- 检测到同一个 DataSource 已由 Spring 绑定事务连接时，保持调用线程上的单连接执行，由 Spring 完成物理提交/回滚，不再另开并发连接绕过 Spring。
- 跨库提交不是 XA/分布式原子事务；Propagation、隔离、noRollbackFor、异步等语义不能仅凭同名注解推定完全一致。详细边界见事务专题。
- DataSource 单独声明成 Bean 有利于容器管理连接池生命周期。`create(DataSourceProperties, group)` 内部会新建 DataSource；不要默认它等价于单独的、由容器销毁的 DataSource Bean。

### 5. 手动注册与验收

非 Bean 动态会话可以在路由初始化完成后调用：

```java
// factory、route 和 dataSource 均为已配置好的对象
JdbcSession session = factory.create(dataSource, "reporting");
route.registerSession(session);
```

这里 route 类型为 `com.yulinlin.data.core.session.RouteSession`。不要在创建会话 Bean 的方法中反向注入 RouteSession 来注册，可能形成初始化循环。已经作为 EntitySession Bean 自动注册的会话无需再次注册。

当前注册表没有提供完整的并发动态管理保证；不要将这个片段当作运行时随意增加/移除租户库的生产方案。

启动后检查 `SessionUtil.route().loadBalanceList()` 是否包含 primary、oss。两个库预置不同标记数据，通过代理调用上述 Service 确认选库；分别验证正常提交、异常回滚和跨库失败行为。此文档没有替你执行这些数据库操作。

### 6. 大集合：最多 4 个连接，每次 JDBC batch 默认 256 条

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

---

<!-- source: doc/topics/16-sqlite.md -->
## SQLite：本地文件数据库

> 适用：JDK 25 / 制品版本 3.0。实现位于 `sqlite/`；通用 SQL 解析位于 `jdbc/.../sql/`。
> SQLite 与 MySQL 复用普通 CRUD 解析器和现有 Model Wrapper API，不需要另一套实体或 DAO。

### 最小接入

Spring Boot 项目添加依赖；已有 starter 时不用重复添加。

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>starter</artifactId>
    <version>3.0</version>
</dependency>
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>sqlite</artifactId>
    <version>3.0</version>
</dependency>
```

仅使用 SQLite 时不需要 mysql 模块、数据库服务器、用户名或密码：

```yaml
yulinlin:
  sqlite:
    file: data/local.db
```

相对路径基于进程工作目录，不是 classpath。启动时创建父目录与数据库文件，并启用 WAL；默认不创建业务表，开启下文实体扫描后可以自动建表。生产环境建议使用持久化目录的绝对路径。未配置 `file` 时不启用本模块。只接受文件路径，不接受 JDBC URL、内存数据库或 `file:` URI。

默认会话组是 `local`，请求时使用 `newInstance("local", ...)`。框架未指定会话时仍默认选择 `primary`；仅使用 SQLite 且希望省略会话参数时，可显式配置 `group: primary`。实体映射和 CRUD 按 ORM 专题使用；将 MySQL 建表语句换成 SQLite DDL，不要照搬 `ENGINE`、`AUTO_INCREMENT` 等 MySQL 专用语法。

### CRUD 完全沿用现有用法

#### 可选：扫描实体自动建表

```yaml
yulinlin:
  sqlite:
    file: data/local.db
    schema:
      enabled: true
      packages:
        - com.example.local.entity
```

自动扫描指定包及子包中带 `@JoinTable("表名")` 的具体实体，在 SQLite 会话创建前完成建表。不实例化实体，不注册成 Spring Bean；仅操作模块内置的 SQLite 连接池，不操作 MySQL。默认关闭；开启却未指定包、没有扫描到物理表实体时启动失败，避免配置错误被忽略。

复用现有继承字段、`@JoinField(name=...)`、`@JoinMeta(primaryKey=true)` 和 JDBC 驼峰列名配置。跳过静态/transient 字段、`exist=false`、计算字段以及 JOIN 查询模型。建议扫描专用实体包，不要混入查询投影 DTO。表名必须是普通名称，不支持别名、SQL 表达式或限定名称；不自动创建索引、外键或复合主键。主键值继续按原框架方式提供，不新增生成主键回填机制。

| 实体字段类型 | 自动建表列类型 |
| --- | --- |
| byte/short/int/long 及包装类、boolean/Boolean | `INTEGER` |
| float/double 及包装类 | `REAL` |
| 日期时间、枚举、BigDecimal、BigInteger | `TEXT` |
| String、字符、JSON 对象、Map、集合 | `TEXT` |
| byte[] | `TEXT`，现有 ByteArrayCoder 输出 Base64 |

编解码继续使用现有 SQL 编解码器，不新增转换层。自定义编码器必须与上述存储约定兼容；建表不保证任何任意 Java 类型都可以被现有编码器正确读写。

`BigDecimal` 存 TEXT 可保留现有字符串编码精度，但数据库直接排序、范围比较是文本语义，**不等于数值大小比较**。日期统一 `yyyy-MM-dd HH:mm:ss` 且时区一致时可做文本范围查询；当前 DateCoder 只有秒精度。

也可以注入管理器手动执行（方法体片段）：

```java
// 注入 com.yulinlin.jdbc.sqlite.SqliteSchemaManager schemaManager
var result = schemaManager.scanAndCreate("com.example.local.entity");
// 或只处理明确指定的实体：
schemaManager.createTables(SysUserVo.class, LocalConfig.class);
// result.entities() / result.created() / result.existing()
```

手动入口即使自动扫描关闭也可使用；应在业务事务外、没有并发迁移时调用。每批 DDL 使用一个事务，失败全部回滚。不存在的表创建，已有表检查列类型亲和性和主键；缺列、类型不兼容或同表映射冲突会报错，不自动补列、删表或改数据。额外普通列允许保留，但额外列自身的 NOT NULL、CHECK、触发器等约束仍由数据库执行，本工具不是完整迁移/约束验证器。

以下是方法体片段，假设 `SysUserVo` 是现有框架实体、对应业务表已创建：

```java
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelInsertWrapper;

// SQLite 默认会话组为 local。
var users = ModelSelectWrapper.newInstance("local", SysUserVo.class).selectList();

// 批量插入：一次传入集合，内部使用事务和 JDBC batch。
ModelInsertWrapper.newInstance("local", usersToInsert).execute();
```

条件、排序、分页、更新和删除继续使用原 Wrapper，不要自行拼接用户输入。批量插入要使用待插入的新数据，不要将查询结果直接重复插入。框架不自动阻止无条件更新或删除。

### 与 MySQL 同时使用

保留 mysql 模块与原 `spring.datasource`，SQLite 默认使用独立的 `local` 组，以下 `group: local` 可省略：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/app
    username: app
    password: ${DB_PASSWORD}
yulinlin:
  sqlite:
    file: data/local.db
    group: local
```

```java
// 第一个参数就是数据源会话组，与 oss 的选择方式相同。
var localUsers = ModelSelectWrapper.newInstance("local", SysUserVo.class).selectList();
var mysqlUsers = ModelSelectWrapper.newInstance("primary", SysUserVo.class).selectList();
```

`spring.datasource.hikari` 仍用于主库。SQLite 使用模块内置的连接池，不继承 MySQL URL 或连接池设置，也不注册 `DataSource` Bean。连接池由 `SqliteDatabase` 创建并在应用关闭时释放；业务无需声明或注入 SQLite DataSource。自定义主 DataSource Bean 时，多主库按 Spring 的规则选择 `@Primary`；SQLite 组保持 `local` 即可，不会参与 DataSource Bean 的选择。不要将不同数据库注册在同一个组下做随机路由。

### 默认值与性能取舍

| 配置/行为 | 默认值 | 含义 |
| --- | --- | --- |
| `file` | 必填 | 本地数据库文件路径 |
| `group` | `local` | Wrapper 第一个参数指定的会话组 |
| `busy-timeout` | `5000` | 等待 SQLite 锁的毫秒数，不是查询超时 |
| `synchronous` | `NORMAL` | 可选 `FULL`；不提供关闭同步的默认方案 |
| 日志模式 | WAL | 启动时启用并验证，失败则启动失败 |
| 外键 | ON | 每个物理连接启用外键检查 |
| 连接池 | 1 个连接 | 本模块池内操作串行，减少写连接争抢 |

```yaml
yulinlin:
  sqlite:
    file: data/local.db
    busy-timeout: 5000
    synchronous: FULL
```

WAL 不代表多个写事务可以同时执行。单连接方案偏向简单可靠的本地写入，不是最大化读并发；长事务会占用唯一连接。连接池等待上限为 `max(1000, busy-timeout + 1000)` 毫秒，超时抛异常，不会无限等待。

`NORMAL` 是性能与持久性的折中，断电或操作系统崩溃时最近已提交的数据可能丢失；更重视持久性使用 `FULL`。批量写入优先一次提交集合，不要每行单独开事务。没有针对你的业务负载跑性能基准，因此不承诺吞吐量。

### 事务

不写事务注解也能执行 CRUD；这不等于禁用 SQLite 事务。单次 ORM 请求内部提交，批量失败回滚，多个独立调用不会自动合并成一个业务事务。

SQLite 直接参与框架的会话事务，不自动注册 `sqliteTransactionManager`，也无需业务注册。框架会记录事务中使用的会话，在正常结束时逐个提交、异常时逐个回滚。

```java
// 放在 Spring 管理的 Service public 方法上，通过代理调用。
@com.yulinlin.data.core.anno.JoinTransaction
public void saveLocal() {
    ModelInsertWrapper.newInstance("local", usersToInsert).execute();
    // 同一事务内继续执行其他框架 ORM 操作
}
```

也可以使用框架已兼容的 Spring `@Transactional` 注解，或直接调用 `SessionUtil.route().transaction(() -> { ... })`。仅有 SQLite 时，不需要为此额外创建 Spring JDBC 事务管理器，注解由框架事务切面管理会话。与 MySQL 共存时，Spring 自身的事务拦截器可能仍管理主库；跨框架会话的事务边界推荐使用 `@JoinTransaction`。

SQLite 直接使用通用 `JdbcSession` 和 `JdbcSessionFactory`，不另建 SqliteSession 子类；只配置 SQLite 解析器和单连接上限。各 JdbcSession 都保持自己的连接与事务状态，SQLite 的 `.batch()` 仍在同一连接同步执行。多数据源事务逐个提交，并非分布式原子提交；中途提交失败不能保证其他已经提交的数据回滚。`REQUIRES_NEW`、保存点、`noRollbackFor` 等 Spring 高级事务属性不属于框架切面的完整语义。

SQLite 的 `session.supportsParallelWrites()` 返回 false：整批请求不做并发分组，不投递写入工作线程，减少 SQLite 单一写入者限制下的锁竞争。底层仍默认每 256 条执行一次 JDBC batch，并复用同一个连接和同一 SQL 模板的 PreparedStatement；这不是每 256 条 commit。可通过 `yulinlin.datasource.jdbc.execute-batch-size` 调整批次大小，不改变事务边界；`parallel-connections` 的通用配置不覆盖 SQLite 的单连接限制。

### 方言与运维边界

- 普通 CRUD、条件、排序和分页复用 JDBC 通用解析；MySQL 原解析器类名保留兼容入口。
- SQLite 不支持 `SELECT FOR UPDATE`，底层 `SelectWrapper.lock()` 解析时明确报错，不静默忽略锁；这不是 ModelSelectWrapper 的方法。
- MySQL 的日期/时间间隔分组不直接复用；SQLite 当前对此明确报不支持。自定义 SQL、函数、JSON 和复杂 JOIN 的语义需要按 SQLite 验证，不保证所有 MySQL SQL 等价。
- WAL 文件需要可写的本地目录，不要把数据库放在网络共享盘。运行期间不要单独删除 `-wal`、`-shm` 或只复制主文件作为可靠备份。
- JDK 25 的 SQLite 原生库可能提示 native-access 警告，启动时可添加 `--enable-native-access=ALL-UNNAMED`。

### 验证范围

集成测试位置：`sqlite/src/test/java/com/yulinlin/jdbc/sqlite/SqliteIntegrationTest.java`。覆盖真实临时文件、WAL/同步/外键设置、CRUD、分页、批量失败回滚、Spring 注解的框架回滚、多会话提交与回滚、重开文件，以及 MySQL/SQLite 独立会话路由。共存测试不连接外部 MySQL，不等于 MySQL 服务器回归测试。

```shell
mvn -pl sqlite -am -Dtest=JdbcSessionTransactionTest,JdbcBatchExecutionTest,SqliteIntegrationTest,SqliteSchemaManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

---

<!-- source: doc/topics/20-transactions.md -->
## JDBC 事务：独立 Session、路由协调与 Spring 接入

> 基线：2026-10-04 / JDK 25 / 制品版本 3.0 / Spring Boot 3.5。
> 源码：core 的 AbstractSession、RegisterSession、RouteSession；jdbc 的 AbstractJdbcSession、ConnectionPool、SpringTransactionAop。

### 推荐选择

- 多数据源或多连接批处理：使用框架 `@JoinTransaction` 或 `SessionUtil.route().transaction(...)`。
- 已有 Spring 管理的单数据源业务：继续使用 Spring `@Transactional`；检测到同一 DataSource 的绑定连接时，框架复用它，在原线程顺序执行，由 Spring 完成物理事务。
- 严格单库原子性：`parallel-connections: 1`，不要把多连接批处理当作单个数据库事务。
- SQLite：使用通用 JdbcSession、单连接，默认组 local；无需注册 DataSource 或 sqliteTransactionManager Bean。

不要默认同时叠加两种事务注解。代理注解需要 Spring 管理的对象并经代理调用，同类自调用或 `new Service()` 不会生效。

### 框架事务示例

```java
import com.yulinlin.data.core.anno.JoinTransaction;

@JoinTransaction
public void saveBusinessData() {
    ModelInsertWrapper.newInstance("primary", mysqlUsers).batch().execute();
    ModelInsertWrapper.newInstance("local", localUsers).execute();
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

### 独立 Session

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

### 并发批处理与异常

默认并发连接上限为 4，外围配置为 `yulinlin.datasource.jdbc.parallel-connections`，按会话覆盖用 `session.setParallelConnections(n)`；完整配置和 `.batch()` 示例见多数据源专题。

支持并发写入时，上层分成最多连接上限数量的大组，一组一个任务/连接；`supportsParallelWrites()` 为 false 时不分组。底层每次 JDBC batch 默认 256 条，可用 `yulinlin.datasource.jdbc.execute-batch-size` 或 `session.setExecuteBatchSize(n)` 调整。`executeBatch()` 仅执行语句，不 commit，不缩小业务事务的回滚范围。

未绑定 Spring 事务时，异步任务明确捕获所属 Session 的连接上下文，不依赖工作线程的 ThreadLocal。每个物理连接互斥使用；等待所有已提交任务结束后，才提交、回滚或回收连接。同步/异步混合批次累加所有结果，不能因为同步批次已产生结果就提前返回。失败和连接清理异常向调用方传播，次要异常保留为 suppressed。

嵌套回滚，以及已有事务中的 JDBC 请求执行失败，会标记 rollback-only。即使业务捕获异常，外层也不能继续正常提交；外层结束实际回滚并抛出异常。嵌套是共享外层事务的计数，不是保存点回滚。

### Spring 注解：支持范围与限制

```java
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Exception.class)
public void saveOneDatabase() {
    ModelInsertWrapper.newInstance("primary", usersToInsert).execute();
}
```

jdbc 自动配置注册 SpringTransactionAop，识别类/方法的 Spring注解，开启和结束框架路由事务。若存在 Spring 原生事务管理器，其事务拦截器仍负责自己的属性。框架借用 DataSourceUtils 的绑定连接，不跨工作线程使用，也不自行提交/关闭 Spring 拥有的连接。执行失败会立即标记对应的 Spring ConnectionHolder 为 rollback-only，避免业务捕获异常后原生 Spring 先提交。

仅有 SQLite 时没有原生 JDBC 事务管理器也能使用这个注解，由框架切面管理会话；这不代表实现了 Spring 的全部事务语义。

框架切面本身不解析 propagation、isolation、rollbackFor、noRollbackFor 等属性；`@JoinTransaction.value()` 也不用于选择参与数据源。框架嵌套计数不等价于 REQUIRES_NEW、NESTED 或保存点；异步业务线程不会自动继承事务。高级传播、同步代理自动更新、Spring 与非 Spring 管理的多个库交叉参与，仍需针对实际代理顺序和业务做集成验证，不承诺透明兼容。

### 原子性边界

多数据源和多物理连接都是**本地事务的协调**，不是 XA/分布式原子事务。所有任务成功只是进入提交阶段，不能保证所有连接一起提交成功；中途提交失败时尝试回滚后续连接并清理全部资源，但已经成功提交的数据无法撤销。

同一 Session 使用多个连接时，提交前的跨连接查询也不保证读到其他连接的未提交写入。原子业务使用单连接；需要跨库强一致性应另选分布式事务或设计补偿，不要依据同名注解推定保证。

### 已验证与未验证

专项测试覆盖：Session 独立事务与实例隔离、默认 4/配置 2 连接限流、10 万条均匀四组、PreparedStatement 复用、256 条分批及尾批、能力关闭时不分组、后续批次失败回滚、同步/异步混合结果、失败任务排空、拒绝提交、嵌套 rollback-only、连接提交/回滚/关闭失败清理、获取连接等待不阻塞其他借用者归还、真实 Spring AOP 顺序及绑定连接复用；SQLite 真实临时文件 CRUD、512 行批次和整批失败回滚、多会话协调及编解码/建表。

JDBC 并发/错误注入测试使用模拟连接；SQLite 使用真实本地文件。未连接外部 MySQL，也未执行业务吞吐量或高级 Spring 传播行为基准。

```shell
mvn -pl sqlite -am -Dtest=JdbcSessionTransactionTest,JdbcBatchExecutionTest,SqliteIntegrationTest,SqliteSchemaManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

---

<!-- source: doc/topics/30-http.md -->
## HTTP 请求、上传与下载

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`core/src/main/java/com/yulinlin/data/core/http/`。若与实际安装版本冲突，以该版本源码为准。

#### 5.1 入口、完整包名和同步调用

```java
package demo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yulinlin.data.core.http.HttpRequestClient;
import com.yulinlin.data.core.http.HttpResponse;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Map;

@Service
public class RemoteApiService {
    private final HttpRequestClient client;

    public RemoteApiService(HttpRequestClient client) {
        this.client = client;
    }

    public Map<String, Object> createOrder(String token, String productId) {
        HttpResponse response = client.post("https://api.example.com/orders")
                .bearerToken(token)
                .json(Map.of("productId", productId, "quantity", 1))
                .timeout(Duration.ofSeconds(5))
                .execute();
        return response.bodyAs(new TypeReference<Map<String, Object>>() {});
    }
}
```

所有调用是同步阻塞的。复用 `HttpRequestClient`；每次创建新的 HttpRequest 构造器，不跨线程共享可变请求对象。

#### 5.2 常用调用片段

以下片段使用这些 imports，放在业务方法中执行；url、token、Path 等由业务提供：

```java
import com.yulinlin.data.core.http.HttpUtil;
import com.yulinlin.data.core.http.HttpFile;
import com.yulinlin.data.core.http.HttpResponse;
import com.yulinlin.data.core.http.HttpRequestException;
import java.nio.file.Path;
import java.time.Duration;
```

```java
// GET 参数；query 的 null 值被忽略
HttpResponse response = HttpUtil.get("https://api.example.com/users")
        .query("page", 1).query("size", 20)
        .header("X-Request-Id", "demo-request")
        .execute();
String text = response.getBodyAsString();
int status = response.getStatus();

// application/x-www-form-urlencoded
HttpUtil.post("https://api.example.com/forms")
        .form("name", "alice").form("enabled", true).execute();

// multipart/form-data：普通表单项与文件一起上传
HttpUtil.post("https://api.example.com/files")
        .multipart("category", "invoice")
        .file("file", HttpFile.of(Path.of("upload/invoice.pdf")))
        .execute();

// 流式下载；路径应由业务校验，不能直接信任远端传入的文件路径
HttpUtil.get("https://api.example.com/export")
        .timeout(Duration.ofSeconds(60))
        .downloadTo(Path.of("download/export.zip"));
```

常用签名：

- `get/post/put/patch/delete(String)`，其他方法用 `request(org.springframework.http.HttpMethod, String)`。
- `json(Object)`、`form(String,Object)`、`form(Map<String,Object>)`、`multipart(String,Object)`、`file(String,HttpFile)`。
- 文本/XML/二进制用 `body(Object, org.springframework.http.MediaType)`。
- `basicAuth(String,String)`、`bearerToken(String)`、`header(String,String)`。
- `HttpFile.of(Path)`、`HttpFile.of(String, byte[], MediaType)`、`HttpFile.of(String, InputStream, MediaType)`。
- 响应：`getStatus()` 返回 int；`getStatusCode()` 返回 Spring HttpStatusCode；`getBody()` 返回 byte[]；`bodyAs(Class<T>)` 或 Jackson `TypeReference<T>` 解析 JSON。

同一个请求只允许一种 body 类型。不要混用 `.json()` 与 `.form()`；文件上传的普通字段用 `.multipart()`，不要用 `.form()`。输入流上传不可直接重复使用原流。

#### 5.3 超时和复用

- Spring 自动配置客户端默认 10 秒，可由 `yulinlin.http.timeout` 配置；单次 `.timeout(Duration)` 覆盖默认值，必须为正数。
- 静态 HttpUtil 初始默认也为 10 秒，但不会自动读取 Spring 配置。`HttpUtil.withTimeout(Duration)` 返回新客户端，不会更改静态默认客户端。
- `HttpUtil.setClient(client)` 可替换全局默认客户端；若需要，只在初始化阶段明确设置，不建议请求期间动态切换。
- `new HttpRequestClient(restClient, mapper)` 直接使用传入客户端，并不自动添加 10 秒配置。
- 当前工厂把同一 Duration 设置到 JDK HttpClient 的 connectTimeout 与 Spring JdkClientHttpRequestFactory 的 readTimeout。不要将其描述成独立的 `.connectTimeout()` / `.readTimeout()` 公共链式 API，也不要保证它是覆盖所有网络与落盘阶段的严格总截止时间。
- 单次 `.timeout()` 当前会构造新的底层 HttpClient；大量同超时请求应优先复用预配置的客户端，减少重复建连接池的机会。

#### 5.4 404、异常与下载边界

```java
boolean explicitlyMissing = HttpUtil.isNotFound("https://api.example.com/resource");
```

该方法实际执行 GET，不是 HEAD；只有捕获到 HTTP 404 返回 true。超时、网络错误及其他状态返回 false，因此 false 不代表资源存在或服务健康。成功响应会按普通请求读取内容，不适合探测超大文件；带认证和特殊头的检查应自行构造请求并捕获状态。

```java
try {
    HttpUtil.get("https://api.example.com/resource").execute();
} catch (HttpRequestException e) {
    Integer statusCode = e.getStatusCode(); // 网络错误可能是 null
    if (Integer.valueOf(404).equals(statusCode)) {
        // 业务处理资源不存在
    } else {
        throw e; // 不把其他失败伪装成成功
    }
}
```

普通 4xx/5xx 与网络调用失败包装为 HttpRequestException；JSON 解码失败也可能包装成该异常。参数校验、非法 URI 等其他异常不保证都被包装。不要解析空的 204 响应为 JSON。

`execute()` 将响应读入内存。大文件用 `downloadTo(Path)`，它会创建父目录并覆盖同名文件；下载失败可能留下部分文件，不保证原子替换。下载返回的 HttpResponse body 为空，内容在磁盘。默认不要假设自动重试、自动跟随重定向或异步能力存在。

对于用户提供的 URL，业务层要校验协议和目的地址，防止 SSRF；Token、Cookie、密码、响应错误体不要无差别记录。

---

<!-- source: doc/topics/40-reflection.md -->
## 反射与深复制

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`lang/src/main/java/com/yulinlin/data/lang/reflection/`。若与实际安装版本冲突，以该版本源码为准。

下例的 `demo.domain.DemoUser` 定义在 `10-orm.md`；只使用反射工具时可换成自己的有无参构造器、可写属性的普通 Bean，不需要启动 ORM。

```java
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import demo.domain.DemoUser;

DemoUser user = ReflectionUtil.newInstance(DemoUser.class);
ReflectionUtil.invokeSetter(user, "username", "alice");
Object name = ReflectionUtil.invokeGetter(user, "username");

ReflectionUtil.PropertyAccess access = ReflectionUtil.property(DemoUser.class, "username");
access.set(user, "bob");
Object value = access.get(user);

DemoUser clone = ReflectionUtil.deepClone(user);
DemoUser target = new DemoUser();
ReflectionUtil.copyProperties(user, target);
```

| API | 当前语义 |
| --- | --- |
| `newInstance(Class<E>)` | 需要可访问的无参构造器 |
| `invokeGetter` / `invokeSetter` | 按名字访问，支持嵌套路径；中间对象不要假设会自动创建 |
| `invokeMethod(obj, name, args...)` | 按方法名和参数解析；歧义报错，变长参数需传声明的数组 |
| `property(Class, String)` | 单一属性访问器，可缓存复用，不是嵌套路径解析器 |
| `clone(value)` | 浅复制，不能用于保证嵌套对象独立 |
| `deepClone(value)` / `clone(value, true)` | 对象图深复制；null 输入返回 null |
| `copyProperties(source, target)` | 同名属性深复制到已有目标；跳过源缺少的属性及源 null 值 |

深复制包括 Bean、集合元素、Map 键值、数组及支持的可变值类型；支持范围内保留循环与共享引用。每次 deepClone 都有独立身份跟踪上下文，这是防止不同操作污染，不应直接改成共享单例。

重要边界：

- Bean 需要无参构造器及可写属性；record、不可写 final 字段、线程/流/连接等资源对象不作为通用克隆目标。
- 不自动把源嵌套实体转为不同类型 DTO，也不自动转换集合泛型。
- 不可变值可复用；集合包装器可能变为标准可变集合，不保证保留不可修改或同步包装语义。有序集合复用比较器。
- copyProperties 失败时目标可能已部分修改，不提供回滚。源 null 跳过语义也不等于数据库“设为 NULL”。
- 私有成员访问仍受 Java 模块 opens 限制；不是能绕过任意访问控制。
- 当前基于 MethodHandle；ReflectAsmUtil 是弃用兼容入口，新代码不要使用。Kryo 仅出现在 admin 性能对比中，不是 lang 的深克隆后端。
- 整体 deepClone(list) 保留跨元素共享引用；逐条克隆不能保留跨调用共享关系。不要仅为速度交换这两种语义。

---

<!-- source: doc/topics/50-utilities.md -->
## JSON 与其他工具

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：`lang/src/main/java/com/yulinlin/data/lang/json/JsonUtil.java`；`lang/.../util/`；`common/.../util/`。若与实际安装版本冲突，以该版本源码为准。

下例的 `demo.domain.DemoUser` 定义在 `10-orm.md`，也可换成自己的普通 Bean。

#### JSON

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.yulinlin.data.lang.json.JsonUtil;
import demo.domain.DemoUser;
import java.util.List;

String json = JsonUtil.toJson(new DemoUser());
DemoUser user = JsonUtil.parseJson(json, DemoUser.class);
List<DemoUser> users = JsonUtil.parseJson("[]", new TypeReference<List<DemoUser>>() {});
```

`JsonUtil.to(source, Target.class)` 经 JSON 转换，受 Jackson 注解/配置影响，不是对象图克隆。不要依赖它保留循环引用与对象身份。JsonUtil 默认有自有 mapper；starter 会将它设置为 Spring ObjectMapper，不能把默认独立模式配置当作所有环境的配置。HttpUtil 静态默认 mapper 又是独立配置，不会自动和 JsonUtil 同步。

#### 常用入口和注意点

| 类 | 使用示例 / 签名 | 边界 |
| --- | --- | --- |
| `StringUtil` | `isNull(text)`、`isNotNull(text)` | 只判断 null/空串，不等于 isBlank；纯空格不是空串 |
| `StringUtil` | `javaToColumn(name)`、`columnToJava(name)` | 字符串转换工具，不代表所有 ORM 路径自动采用此规则 |
| `DateTime` | `DateTime.now()`、`DateTime.parse(text, "yyyy-MM-dd HH:mm:ss")` | 框架自有日期类型；不要当作不可变 java.time 类型 |
| `Page` | `Page.of(list)`、`Page.page(list, 1, 20)` | 后者是内存分页，不执行 SQL；调用前校验正数页码/页大小 |
| `SnowflakeUtil` | `nextIdStr()`、`nextId()` | 默认节点配置含随机因素，多实例唯一性需显式规划，不能承诺随机节点绝不冲突 |
| `TreeUtil` | `buildTree(List<E>)`，E 实现 `com.yulinlin.common.domain.ITreeNode` | 原地给父节点追加子节点；先清理旧 children，校验重复 id/环，缺失父节点的节点会成为根 |
| `R` | `R.newInstance(data)` | 统一响应包装不是 HTTP 状态设置器；不要猜测存在 `R.ok()` / `R.success()` |

缓存、线程池、锁、金额、事件、服务基类等工具还存在于源码中，但本文未逐个验证其业务契约。需要时先读对应类，不仅凭类名生成调用，更不要把金额精度、分布式锁或线程安全能力自行补全。

---

<!-- source: doc/topics/90-troubleshooting.md -->
## 排障、交付检查与提示模板

> 状态：当前使用文档；来源核对基线：2026-10-01，2026-10-04 整理。
> 适用：制品版本 3.0 / JDK 25 / Spring Boot 3.5。示例未全部编译或集成验证，不等于运行测试通过。
> 源码定位：对应专题列出的源码。若与实际安装版本冲突，以该版本源码为准。

| 现象/需求 | 处理 |
| --- | --- |
| `NoSuchMethodError: ReflectionUtil.property(...)` | 编译时与运行时 JAR 不一致；检查 lang/core 的来源，统一构建和依赖树，不先归咎于 JDK 25 反射 |
| 没有可用会话 | 检查 mysql 模块、DataSource、自动配置是否被排除以及 Spring 初始化顺序 |
| HTTP 超时配置不生效 | 区分 Spring 注入客户端、HttpUtil 静态客户端、手动构造客户端和单次覆盖 |
| `isNotFound()` 返回 false | 不足以认定成功，必要时执行请求并检查状态/异常 |
| DTO 复制类型不兼容 | 显式映射，不把 copyProperties 当作任意类型转换器 |
| 深克隆私有/final/record 报错 | 按支持边界调整模型或选择适当映射方案，不静默忽略字段 |
| 需要“最快的克隆” | 用真实模型同机 JMH，比较相同引用语义；不能宣称某工具总是最快 |

生成代码交付前检查：准确 import、实际依赖版本、表字段、无参构造器、主键/where、代理事务、分页限制、HTTP 超时/鉴权/异常、大文件流式下载、复制语义及敏感数据处理。没有执行过的构建或测试要明确标注为未验证。

### 提供给外部 AI 的提示模板

```text
请基于附件《AI接入指南》为 yulinlin-data 3.0 生成代码，运行环境为 JDK 25 + Spring Boot 3.5。
仅使用指南中已确认的 API；不要套用 MyBatis-Plus/JPA 的接口。
我的需求：[填写业务动作]
表结构与实体基类：[填写建表 SQL、IdEntity 或 SuperEntity]
写操作的定位条件与事务边界：[填写]
HTTP 接口、认证、请求/响应样例：[如涉及则填写，不提供真实密钥]
复制需求：[浅复制/同类型深克隆/不同 DTO 映射，是否需要保留共享引用]
请输出准确 imports、配置、代码和验证步骤；信息不足时先列出缺失项。
不要宣称已执行未实际运行的测试；遇到指南外接口先核查当前源码。
```
