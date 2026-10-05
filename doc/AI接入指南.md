# yulinlin-data AI 使用指南

---

> 派生文件，维护源为 doc 下前四个使用专题。重新导出：./doc/build-ai-docs.ps1。

---

用途：给不能读取仓库的 AI 提供一个附件。包含接入、CRUD/统计/事务、关联代理和工具；内部扩展与历史性能不在此导出中，按需另提供第五专题。

---

适用 JDK 25、Spring Boot 3.5.16、制品 3.0。2026-10-05 文档核对未执行测试或编译；代码片段不等于已经验证。真实账号、表、路径和接口由业务提供。

---

<!-- source: doc/01-接入与数据源.md -->
## 项目接入与数据源

先选数据库模块，再配置数据源和会话组。本文完成 Spring Boot 接入、MySQL 与 PostgreSQL 配置、SQLite 本地存储，以及多数据源注册和默认组选取。

阅读导航：[模块选择](#模块选择) · [MySQL](#mysql-接入) · [SQLite](#sqlite-接入) · [PostgreSQL](#postgresql-接入) · [多数据源](#多数据源注册) · [默认会话组](#默认会话组) · [配置速查](#配置速查)

适用版本：JDK 25、Spring Boot 3.5.16、制品 3.0。本文于 2026-10-05 核对仓库源码；本轮未执行示例、测试、编译或打包。这里是自定义 ORM，不是 MyBatis-Plus、JPA 或 Spring Data。

### 模块选择

| 使用场景 | 依赖 |
| --- | --- |
| Spring Boot 和 MySQL ORM | `com.yulinlin:starter:3.0` + `com.yulinlin:mysql:3.0` |
| Spring Boot 和 SQLite ORM | starter + sqlite |
| Spring Boot 和 PostgreSQL ORM | postgresql；需要便利模型门面时再加 common 或 starter |
| HTTP 工具 | core，starter 已传递引入 |
| 反射、深克隆、JSON | lang |
| 实体基类、Model Wrapper、树和 ID | common |
| 示例或基准 | admin，不作为业务依赖 |

依赖应来自团队制品仓库或同一份源码的发布产物，不假设已发布 Maven Central。同组模块保持相同版本和构建来源。Lombok 示例需要业务项目提供 Lombok 与注解处理；不使用它时手写 getter/setter。

core 同时包含 ORM 自动配置，不是独立的纯 HTTP starter。只使用公共 JDBC Session 时，数据库模块不强制依赖 common/starter。

### MySQL 接入

加入依赖：

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

配置自己的数据库账号。下面的地址和环境变量是示例，不应直接用于生产：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

正常 Boot 自动配置链下无需框架启用注解。mysql 模块创建 `mysqlSessionFactory` 和 `mysqlSession`，默认 group 为 `mysql`。表需要已经存在；完整实体与第一次 CRUD 见 [第二专题](02-CRUD与统计分析.md)。

### SQLite 接入

仅加入 starter + sqlite，无需数据库服务器、账号或密码：

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

引入模块即启用，不提供额外的模块级开关。未配置时使用进程工作目录下的 `data/local.db`，默认 group 为 `sqlite`。可按需覆盖：

```yaml
yulinlin:
  sqlite:
    file: data/app.db
    group: sqlite
    busy-timeout: 5000
    synchronous: NORMAL
```

启动时创建父目录与文件，启用 WAL、外键约束和单连接池；业务表在第一次实际执行请求时按需创建。file 仅接受本地路径，不接受 JDBC URL、内存数据库或 file URI；空路径会报错。文件应放在可写的本地持久化目录，而不是 classpath 或网络共享盘。

#### 按实体自动建表

无需扫描包或配置建表开关。SqliteSession 直接读取 `BaseRequest.getFromClass()`，普通单表实体带 `@JoinTable("表名")` 时，检查并创建缺失表，再执行原来的 SQL。插入、更新、删除、查询、分页和统计入口均支持；空写请求、只解析 SQL 或命中查询缓存时不建表。

下面是业务方法体片段，DemoUser 是 [第二专题](02-CRUD与统计分析.md#最小实体和配套表)的完整实体；无需预先执行该专题的建表 SQL：

```java
import demo.domain.DemoUser;
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;

// 第一次查询也能建表，新文件返回空列表。
var users = ModelSelectWrapper.newInstance("sqlite", DemoUser.class).selectList();

DemoUser user = new DemoUser();
user.setUsername("alice");
user.setStatus(1);
ModelInsertWrapper.newInstance("sqlite", user).execute();
```

建表沿用字段别名、下划线映射、继承字段和单主键元信息。整数/布尔值对应 INTEGER，浮点数对应 REAL，其余默认 TEXT；static、transient、非持久化和关联查询字段不生成列。

需要自动建表时，`fromClass` 应是完整表实体；`entityClass` 只决定结果如何解码。框架跳过无注解、Object/Map、多表 JOIN 和带 JoinAggregations/JoinMetrics 的统计模型，不推断这些模型的底层表。普通单表查询 DTO 可能同样带 JoinTable，无法据此判断字段是否齐全，不能用它首次建表。

`fromClass` 为 null 或 Object.class 时，两者都视为没有实体来源：跳过字段名映射、类级路由注解和自动建表，仍执行原始 SQL，不从返回类型推断来源。SQL 中的表需已存在；不存在时保留数据库的正常报错。

#### 自定义 SQL 指定建表实体

原始查询默认将返回类型同时作为 fromClass；原始写入默认使用 Object。因此 Map 返回值或原始写入不会自动猜测 SQL 中的表。需要建表时显式设置完整实体，下面示例仍使用第二专题的 DemoUser：

```java
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.request.ExecuteRequest;
import demo.domain.DemoUser;
import java.util.Map;

var query = QueryRequest.newInstance(
        "select id, user_name, status from ai_demo_user", Map.of(), Map.class);
query.setSession("sqlite");
query.setFromClass(DemoUser.class);
var rows = query.selectList();

var update = ExecuteRequest.newInstance(
        "update ai_demo_user set status=#{status} where id=#{id}",
        Map.of("status", 2, "id", "existing-id"));
update.setSession("sqlite");
update.setFromClass(DemoUser.class);
int affected = update.execute();
```

一次请求只保障 fromClass 对应的表，不解析原始 SQL 中其他表的依赖。JOIN/统计模型的物理表应先通过完整实体请求创建，或者由业务显式提供 DDL。

#### 建表与事务边界

建表复用当前请求的事务连接，不额外借连接，不独立 commit；框架事务由 Session 完成，Spring 绑定事务由 Spring 完成。检查结果只在对应事务成功提交后进入 Session 缓存，失败/回滚后下次请求可重新检查。同一 Session 的并发首次访问由连接池和建表锁协调，不同数据库文件不会共用成功状态。

这不是迁移工具：已有表只做兼容性检查，不自动补列、改类型、建索引、添加外键或复合主键。结构不兼容明确报错，需要手动迁移。运行期间外部修改/删除已缓存的表后，应重新创建 Session 或重启应用；首次建表不能放在 Spring 只读事务中，需提前初始化。

日期、枚举、BigDecimal、JSON 对象等使用 TEXT；字符串与数字仍由 JDBC 编解码器恢复 Java 字段。日期范围依赖统一、可按字典序排序的固定格式与时区；TEXT BigDecimal 的字符串比较不等于数值比较，数值范围或统计需要适当列类型或显式 SQL CAST。

SQLite 使用继承 JdbcSession 的 SqliteSession，复用公共 CRUD，并增加按需建表；不需要 DataSource Bean 或 sqliteTransactionManager。SqliteDatabase 持有并关闭内部连接池。`supportsParallelWrites()` 返回 false，多连接批处理不会拆组；WAL 也不能让同一文件同时拥有多个写事务。

SQLite 不支持框架生成的 FOR UPDATE、日期分组和数值间隔分组，当前解析器明确拒绝；需用自定义 SQL 实现数据库专用功能。运行中不要单独删除 -wal/-shm 文件，也不要仅复制主文件作为可靠备份。NORMAL 偏向性能，FULL 更重视持久性；这不是业务吞吐量承诺。

### PostgreSQL 接入

最小依赖只需 postgresql，公共 jdbc/core/lang 会传递引入；不依赖 mysql 模块或 MySQL 驱动：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>postgresql</artifactId>
    <version>3.0</version>
</dependency>
```

需要 Model Wrapper 时再加入 common 或 starter。示例配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/demo?stringtype=unspecified
    username: ${PG_USERNAME}
    password: ${PG_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

模块创建 `postgresqlSessionFactory` 和 `postgresqlSession`，默认 group 为 `postgresql`，实际会话类型为 PostgresqlSession。

现有编码器可能把日期、枚举、BigDecimal 或对象编码为字符串。`stringtype=unspecified` 让驱动按目标列或 SQL 上下文推断类型；它不是框架自动添加的参数，也不解决所有类型问题。无明确类型上下文时可在可信 SQL 中显式 CAST，例如 `CAST(#{payload} AS jsonb)`。不要推定原生 enum、UUID、数组、空间类型已有完整专用编解码支持。

标识符采用双引号，schema 限定名按段引用；实际列名应与模型映射一致。普通 CRUD 和实体可复用，原始 MySQL SQL、反引号、函数与 DDL 不会被自动翻译。本模块不自动建表或回填数据库自增键。

JSON 读取支持合法 JSON 的路径，例如 `payload->profile->name`；数字/布尔条件按比较值生成 CAST。路径部分更新尚未自动生成 jsonb_set，应整字段替换或写专用 SQL。非法 JSON 和不能转换的值会报错，不是静默跳过。

InputStream 使用 setBinaryStream，原生布尔列保留 false/NULL。除布尔列外结果大多沿用字符串解码，不能推定 bytea/oid、timestamptz 等扩展类型完整往返已验证。

### 多数据源注册

三个名字不要混用：

| 名称 | 作用 |
| --- | --- |
| DataSource Bean 名 | Spring 注入具体连接池 |
| Session Bean 名 | 容器管理会话对象 |
| group | 框架运行请求时选库 |

工厂 `create(dataSource, group)` 返回已配置的 Session，但不注册路由。把它声明为 EntitySession Bean，core 就会收集到 RouteSession；无需在 Bean 创建方法中反向注入 RouteSession。

#### 两个 MySQL 数据源

下面是完整配置类。应用提供 spring.datasource 和 oss.datasource 两套 DataSourceProperties 配置；main 数据源标记 @Primary，使模块默认 mysqlSession 使用它：

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
    @Bean("mainProperties")
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mainProperties() { return new DataSourceProperties(); }

    @Bean("dataSource")
    @Primary
    public DataSource mainDataSource(@Qualifier("mainProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("ossProperties")
    @ConfigurationProperties("oss.datasource")
    public DataSourceProperties ossProperties() { return new DataSourceProperties(); }

    @Bean("ossDataSource")
    public DataSource ossDataSource(@Qualifier("ossProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("ossSession")
    public JdbcSession ossSession(@Qualifier("mysqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("ossDataSource") DataSource dataSource) {
        return factory.create(dataSource, "oss");
    }
}
```

Boot 可能因为额外的 DataSource Bean 不再创建原默认源，因此例子明确声明两个池，而不是只声明第二个源。

#### MySQL 与 PostgreSQL 共存

模块不检查 JDBC URL。两边自动配置可能注入同一个 @Primary DataSource；默认 group 不同不代表已绑定不同物理库。

以下完整配置类假设 MySQL 的 dataSource 与 PostgreSQL 的 pgDataSource 已正确创建。主会话增加兼容名 jdbcSession，让双方默认会话创建退让；别名 mysqlSession 指向同一个对象：

```java
package demo.config;

import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class MixedSessionConfig {
    @Bean(name = {"jdbcSession", "mysqlSession"})
    public JdbcSession mysqlSession(@Qualifier("mysqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("dataSource") DataSource dataSource) {
        return factory.create(dataSource, "mysql");
    }

    @Bean("postgresqlSession")
    public JdbcSession postgresqlSession(@Qualifier("postgresqlSessionFactory") JdbcSessionFactory factory,
            @Qualifier("pgDataSource") DataSource dataSource) {
        return factory.create(dataSource, "postgresql");
    }
}
```

MySQL 与 SQLite 共存时，保留原主库配置即可，SQLite 使用内部池和独立 sqlite 组。多个主 DataSource 没有唯一候选时，全部会话显式创建，不任意猜测。

手动 new JdbcSessionFactory 后不能立即假定可用：当前工厂需要容器注入编码器、缓存、日志等。`create(DataSourceProperties, group)` 会新建池，资源所有者仍须负责关闭，不自动等价于独立 DataSource Bean。

### 默认会话组

只注册一个 group 时，无组参数请求自动使用它；有多个 group 时设置应用共享 LoadBalance 的默认组：

```yaml
yulinlin:
  datasource:
    default-group: mysql
```

代码设置的业务方法体片段，loadBalance 为注入的 `com.yulinlin.data.core.loadbalan.LoadBalance`：

```java
loadBalance.setDefaultGroup("mysql");
String configured = loadBalance.getDefaultGroup();
String effective = loadBalance.defaultGroup();
```

getDefaultGroup 返回配置值，defaultGroup 返回实际默认组；单组时配置并非必需。多组未配置或默认组不存在会报错。primary 是普通组名，不自动优先。

请求显式 group → 模型 @JoinSession → 当前 Service 会话上下文 → 负载均衡默认组。选择方式举例，DemoUser 定义在第二专题：

```java
ModelSelectWrapper.newInstance("oss", DemoUser.class).selectList();
```

也可在 Spring Service 方法/类上使用 `com.yulinlin.data.core.anno.JoinSession("oss")`，需经代理调用；实体上的固定组不会被 Service 注解无条件覆盖。框架默认 LoadBalance Bean 会绑定 YAML；自定义 Bean 应自行配置。

单个可用节点直接返回，但仍检查 cluster、正权重和健康状态；同组多节点继续按权重选择。健康缓存不会把离线默认库悄悄切到另一个组。运行时修改默认组或移除资源应协调在途事务。

### 配置速查

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| yulinlin.datasource.default-group | 未设置 | 单组自动；多组指定默认 |
| yulinlin.sqlite.file | data/local.db | 进程工作目录下的路径 |
| yulinlin.sqlite.group | sqlite | 本地会话组 |
| yulinlin.sqlite.busy-timeout | 5000 ms | 等锁，不是查询超时 |
| yulinlin.sqlite.synchronous | NORMAL | 可选 FULL |
| yulinlin.datasource.jdbc.parallel-connections | 4 | 每 Session、每框架事务的连接上限；SQLite 固定 1 |
| yulinlin.datasource.jdbc.execute-batch-size | 256 | 一次 executeBatch 的行数，不是 commit |

### 接入边界与排障

- 单数据源也必须等待容器初始化，不在静态初始化块里查询。MySQL/PostgreSQL 表需要业务提前准备；SQLite 普通完整实体可按需建表。
- 不把独立库无意注册为同组节点；同组是负载均衡，不是覆盖注册。
- 多库事务与多连接事务不是 XA，详细规则见 [第二专题](02-CRUD与统计分析.md#事务使用)。
- 未发现会话：检查数据库模块、自动配置 imports、DataSource 候选、Session Bean 和 group。
- 表或字段不存在：核对实际建表、JoinField 映射和实体基类继承字段。
- PostgreSQL 报 MySQL 函数错误：检查是否使用了 mysqlSessionFactory 或原始 MySQL SQL。
- SQLite 原生库在 JDK 25 下可能提示 native-access 警告；部署时按实际环境配置 JVM 原生访问权限，不把警告当成数据库初始化成功的证明。

内部 Session 创建与扩展规则见 [第五专题](05-扩展开发与维护.md)。

---

<!-- source: doc/02-CRUD与统计分析.md -->
## ORM CRUD 自定义 SQL 统计分析与事务

本文从一个用户实体完成 CRUD，再扩展条件查询、SQL JOIN、自定义 SQL、统计模型和事务。数据库接入与 group 注册先看 [第一专题](01-接入与数据源.md)。

阅读导航：[最小实体](#最小实体和配套表) · [CRUD](#crud-完整服务) · [查询条件](#查询条件与结果组织) · [自定义 SQL](#自定义-sql-执行) · [统计分析](#统计分析) · [批量写入](#批量与多连接写入) · [事务](#事务使用) · [边界](#使用边界与排障)

### 最小实体和配套表

以下完整类放在 `demo/domain/DemoUser.java`，提供无参构造和 getter/setter。IdEntity 提供 String 主键及插入前的应用侧 ID 生成。

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

    public DemoUser() { }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
```

MySQL/PostgreSQL 需要提前准备表；SQLite 使用 SqliteSession 时，完整 DemoUser 实体的第一次请求会自动创建缺失表，不需要执行下列 DDL。手动建表仅在自己的示例数据库执行，不把它当作生产迁移脚本：

```sql
CREATE TABLE ai_demo_user (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_name VARCHAR(100),
    status INT
);
```

SuperEntity 在 IdEntity 上增加 crtTime、uptTime 和填充逻辑，使用它时表必须有对应列。非持久化属性显式用 `@JoinField(exist = false)`，不能依赖“没有注解就一定忽略”。

| 注解 | 用途 |
| --- | --- |
| JoinTable | 表或 SQL JOIN 映射 |
| JoinField(name = "...") | Java 属性与列名映射 |
| JoinField(exist = false) | 排除非数据库列 |
| JoinField(update = false) | 排除更新字段 |
| JoinMeta(primaryKey = true) | 主键元信息；不是 JoinPrimary |
| JoinWhere | 对象属性有值时参与条件 |
| JoinField(version = true) | 版本字段；具体支持路径按代理与实际更新实现核对 |

自定义主键建议同时声明 JoinField、JoinMeta、JoinWhere。重命名主键列时要检查删除、部分更新等实际映射，不仅查看 SELECT SQL。

### CRUD 完整服务

下面完整 Service 使用 mysql 组。对应表已存在、容器已初始化；通过 Spring 代理调用带事务的方法：

```java
package demo.service;

import demo.domain.DemoUser;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.common.model.ModelDeleteWrapper;
import com.yulinlin.data.lang.util.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoUserService {
    public DemoUser findById(String id) {
        requireId(id);
        return ModelSelectWrapper.newInstance("mysql", DemoUser.class)
                .eq("id", id).selectOne();
    }

    public Page<DemoUser> page(int page, int size) {
        if (page < 1 || size < 1 || size > 200) throw new IllegalArgumentException("Invalid page");
        return ModelSelectWrapper.newInstance("mysql", DemoUser.class)
                .eq("status", 1).orderByDesc("id").selectPage(page, size);
    }

    @Transactional
    public String create(String username) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username is required");
        DemoUser user = new DemoUser();
        user.setUsername(username);
        user.setStatus(1);
        ModelInsertWrapper.newInstance("mysql", user).execute();
        return user.getId();
    }

    @Transactional
    public int changeStatus(String id, int status) {
        requireId(id);
        DemoUser patch = new DemoUser();
        patch.setId(id);
        patch.setStatus(status);
        return ModelUpdateWrapper.newInstance("mysql", patch).execute();
    }

    @Transactional
    public int delete(String id) {
        requireId(id);
        return ModelDeleteWrapper.newInstance("mysql", DemoUser.class).eq("id", id).execute();
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
    }
}
```

实体也提供 `createSelectWrapper()`、`createInsertWrapper()`、`createUpdateWrapper()`、`createDeleteWrapper()`。不指定组的入口遵循全局默认组选取；多组时不要靠注册顺序。

### 查询条件与结果组织

以下为业务方法体片段，所用 DemoUser 已在上文定义：

```java
var query = ModelSelectWrapper.newInstance("mysql", DemoUser.class)
        .eq(DemoUser::getStatus, 1)
        .like(DemoUser::getUsername, "alice")
        .in("id", java.util.List.of("1", "2"))
        .orderByAsc("username");
var users = query.selectList();
```

| 入口 | 行为 |
| --- | --- |
| eq / ne / gt / gte / lt / lte | 比较条件，可用属性字符串或 Lambda |
| like / likeRight | 模糊条件 |
| in | 集合条件 |
| between | 范围；字符串字段与列类型的排序规则需要一致 |
| isNull | SQL NULL 条件 |
| and / not / where | 条件组合；按实际 Wrapper 接口使用 |
| selectOne | 无结果返回 null；取第一条，不自动保证唯一 |
| selectList / count | 列表 / 计数 |
| selectPage(page, size) | 数据库分页，调用前校验页码和大小 |
| selectByMap("id") | Java 端索引，重复键由后项覆盖 |
| selectByGroup("status") | Java 端组织结果，不是 SQL GROUP BY |

对象构造条件示例：

```java
DemoUser filter = new DemoUser();
filter.setStatus(1); // 因为字段有 JoinWhere，参与条件。
var users = ModelSelectWrapper.newInstance("mysql", filter).selectList();
```

不是所有非 null 属性都自动成为 WHERE 条件。字符串条件优先使用 Java 属性名，使 JoinField 映射生效。没有默认的全表写入保护；UPDATE/DELETE 前业务必须验证主键或条件。

#### SQL JOIN

SQL JOIN 与第三专题的 JoinQuery 不是同一功能。以下完整投影类假设 sys_user、sys_dept 表存在，列名与示例一致；使用 Lombok 生成访问方法：

```java
package demo.dto;

import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import lombok.Data;

@Data
@JoinTable(left = "sys_user a", right = "sys_dept b", on = "a.sys_dept_id = b.id")
public class UserDepartmentView {
    @JoinField(name = "a.id")
    private String id;
    @JoinField(name = "a.username")
    private String username;
    @JoinField(name = "b.dept_name")
    private String departmentName;
}
```

方法体查询：`ModelSelectWrapper.newInstance("mysql", UserDepartmentView.class).eq("id", id).selectList()`。更多表可用 JoinTableList。表表达式、别名和 ON 是可信代码，不能直接拼接 HTTP 输入；不能复用旧文档的 JoinPrimary、lambda().eq 或 getSql 调用来猜测当前 API。

### 自定义 SQL 执行

SQL 文本由调用方提供，框架不会跨数据库翻译它。所有 Request 的便利执行方法仍走 RouteSession，需要初始化好的会话。

#### 查询列表与单条

完整 imports 加方法体片段：

```java
import com.yulinlin.data.core.request.QueryRequest;
import demo.domain.DemoUser;
import java.util.Map;

// MySQL 专用命令，不是 SQLite/PostgreSQL 通用 SQL。
var tablesRequest = QueryRequest.newInstance("show tables", Map.of(), Map.class);
tablesRequest.setSession("mysql");
var tables = tablesRequest.selectList();

// SELECT 列别名与返回对象属性一致；只投影需要的列。
var userRequest = QueryRequest.newInstance(
        "select id, user_name as username, status from ai_demo_user where id=#{id}",
        Map.of("id", "existing-id"), DemoUser.class);
userRequest.setSession("mysql");
DemoUser user = userRequest.selectOne();
```

QueryRequest.newInstance(sql, params, clazz) 的 Map.class 返回按列标签组织的行 Map；对象结果按字段映射解码。selectOne 不检查“恰好一条”。多组时用 setSession 指定组；这是 void setter，不是可继续 selectList 的链式返回。

SQLite 按 `BaseRequest.getFromClass()` 自动建表，不按返回类型猜测实体。原始查询初始 fromClass 等于 clazz，原始写入初始为 Object；需要建表时调用 `request.setFromClass(DemoUser.class)` 指定完整表实体。不需要来源时可以设为 null 或 Object.class，框架跳过实体映射和自动建表，SQL 与参数绑定照常执行，结果仍按 entityClass 解码。设置实体不会改写 SQL，也不自动创建 SQL 中的其他表；完整示例见 [第一专题](01-接入与数据源.md#自定义-sql-指定建表实体)。

原始 CommandNode 不自动补分页或计数 SQL。需要时在可信 SQL 中明确写 LIMIT/OFFSET 或 COUNT，并用 selectList/selectOne 读取；不要把包装器分页能力直接套到任意原始命令。

#### 自定义写入

```java
import com.yulinlin.data.core.request.ExecuteRequest;
import java.util.Map;

var update = ExecuteRequest.newInstance(
        "update ai_demo_user set status=#{status} where id=#{id}",
        Map.of("status", 1, "id", "existing-id"));
update.setSession("mysql");
Integer affected = update.execute();
```

此入口走通用 update 执行路径，命令 ParseType 同为 update；不根据 SQL 首词猜测 INSERT/DELETE 类型。适用于后端支持的写语句，不作为返回查询结果的入口。DDL、驱动批量改写、受影响行数等行为仍由目标数据库/驱动决定。

Request 可以复用事务上下文，但可变请求对象不能跨线程共享。原始 SQL 不知道自己影响哪些业务实体，不自动提供精确的实体查询缓存失效承诺；依赖缓存的业务应显式协调。

#### 占位符与安全

```text
SQL 文本：where id=#{id}
参数 Map：{"id": 7}
SQL Node：where id=?，绑定列表：[7]
```

正则匹配 SQL 文本中的占位符，截取内部的裸键从 Map 取值；不要把参数键写成 "#{id}"。多个或重复占位符按 SQL 出现顺序绑定，Map 的迭代顺序不决定 JDBC 参数顺序。

`#{value}` 用于 PreparedStatement 绑定；`${identifier}` 是直接文本替换，仅用于业务预先校验的可信表名、列名或表达式，不用于用户数据。WHERE 必须明确限制写入目标。

当前编码缓冲区不承诺 null 参数值可正常 put：Map.of 本身也不接受 null。查询 NULL 用 IS NULL，清列可用固定的 SET column=NULL SQL；需要可空动态参数应先核对并验证对应编码路径。缺少键可能被绑定成 null，不当作可靠的参数校验。

### 统计分析

#### 注解模型

下面保留 MetricsTable 的业务结构。name 用于分组，metrics 用于 SUM；dateStr 映射 crt_time，默认不作为分组输出。

```java
package demo.statistics;

import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinAggregations;
import com.yulinlin.data.core.anno.JoinMetrics;
import com.yulinlin.data.core.anno.MetricsEnum;
import lombok.Data;

@Data
@JoinTable("sta_metrics")
public class MetricsTable {
    @JoinAggregations
    private String name;

    @JoinField(name = "crt_time")
    private String dateStr;

    @JoinMetrics(MetricsEnum.sum)
    private int metrics;

    public MetricsTable() { }
    public MetricsTable(String name) { this.name = name; }
}
```

仅用于自己的示例数据库的配套表：

```sql
CREATE TABLE sta_metrics (
    name VARCHAR(100),
    crt_time VARCHAR(19),
    metrics INT
);
```

大范围 SUM/COUNT 推荐用 Long/long 或 BigDecimal 等适当结果类型，避免 int 溢出、截断；AVG 也不应假定整数结果。编码器的 Java 类型和目标数据库返回类型一起验证。

#### 分组求和与日期筛选

```java
import com.yulinlin.common.model.ModelGroupWrapper;
import demo.statistics.MetricsTable;

var totals = ModelGroupWrapper.newInstance("mysql", MetricsTable.class)
        .gte("dateStr", "2026-10-01 00:00:00")
        .lt("dateStr", "2026-11-01 00:00:00")
        .having(h -> h.gt("metrics", 100))
        .orderByDesc("metrics")
        .selectList();
```

逻辑上是按 name 分组、SUM(metrics)，WHERE 先限定原始行的 crt_time，HAVING 再过滤聚合结果。属性到列、别名、分页和 HAVING 的具体 SQL 由数据库解析器处理，不强行用 MySQL 写法描述全部数据库。

new MetricsTable("foo") 只赋值，不自动生成 WHERE，因为 name 没有 JoinWhere。筛选用 `.eq("name", "foo")`；要让对象值参与条件则明确加 JoinWhere。

#### 指标与维度速查

| 类型 | 支持入口 |
| --- | --- |
| JoinMetrics | MetricsEnum.count、distinctCount、sum、avg、min、max |
| JoinAggregations | field、minute、hour、day、month、quarter、year、interval |
| 数字区间 | JoinAggregations(value = AggregationsEnum.interval, interval = 10) |
| ModelGroupWrapper | selectList、selectOne、page(...).selectPage、having、orderByAsc/Desc |
| 结果组织 | selectByMap / selectByGroup 是 Java 端索引或分组，不替代 SQL 聚合 |

要在 name 外增加日期维度，可在支持的数据库上补一个分组表达式：

```java
var daily = ModelGroupWrapper.newInstance("mysql", MetricsTable.class)
        .apply(w -> w.aggregations().day("dateStr", "dateStr"))
        .orderByAsc("dateStr")
        .selectList();
```

也可在 dateStr 字段增加 `@JoinAggregations(AggregationsEnum.day)`，并保留 JoinField 映射。日期维度生成分组格式字符串；没有该表达式时 dateStr 不自动出现在统计结果里。

MySQL/PostgreSQL 使用各自日期与区间解析器；SQLite 当前不支持这两个自动分组入口，需写 strftime 等专用 SQL。TEXT 日期须统一格式与时区；TEXT 数字的比较、排序和聚合不能直接当作原生数值列。自定义函数和表达式始终按目标库语法编写。

### 批量与多连接写入

方法体片段，usersToInsert 为已准备好的 DemoUser 集合：

```java
ModelInsertWrapper.newInstance("mysql", usersToInsert).execute();         // 普通 JDBC batch
ModelInsertWrapper.newInstance("mysql", usersToInsert).batch().execute(); // 申请多连接路径
```

```yaml
yulinlin:
  datasource:
    jdbc:
      parallel-connections: 4
      execute-batch-size: 256
```

两个数都必须是正整数。默认最多 4 个连接，把整批数据均匀分成最多 4 个大组，一组一个任务/连接；同 SQL 复用 PreparedStatement，每满 256 行执行一次 executeBatch，尾批也执行。128 是 ExecuteRequest 的并发启用最小请求条数，不是 JDBC 提交大小。

只有 .batch()、执行器、请求阈值和 supportsParallelWrites 等条件满足才并发；SQLite、单连接池或 Spring 绑定连接不拆组。4 是每 Session、每框架事务的上限，不是整个应用并发上限，也不保证 4 倍速度。

executeBatch 不是 commit。所有已提交任务结束后再提交/回滚及释放；失败不能让工作线程继续在已归还连接上执行。解析仍持有完整输入集合，不是流式导入。SUCCESS_NO_INFO 按成功命令计数，不保证精确行数。

多个连接有各自的本地事务，中途提交失败无法撤销已成功提交的连接；跨连接未提交数据也不保证可读。严格单库原子性和事务内读己之写使用 parallel-connections: 1。

### 事务使用

#### 框架路由事务

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

没有外层事务时，一次 CRUD 自己开始并结束事务；多个独立请求不自动合成一个业务事务。RouteSession 按实际访问加入参与者，逐个提交/回滚，只结束自己加入的事务层；不会替调用方结束独立 Session 预先开启的外层事务。

#### Spring 事务

已接入 org.springframework.transaction.annotation.Transactional。原生 Spring 管理器为相同 DataSource 绑定连接时，框架在原线程复用它，提交/回滚/释放归 Spring；不把这个连接发给并发工作线程。

框架切面本身不解析 propagation、isolation、rollbackFor、noRollbackFor 等属性。已有 Spring 拦截器处理自身语义，不代表框架路由实现了完整 REQUIRES_NEW、NESTED 或保存点。不要默认叠加两个事务注解，也不要将业务异步线程视为自动继承事务。

#### 独立 Session

以下方法体片段中 jdbcSession 已由工厂配置，request 是已构造 Request：

```java
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

所在方法须允许传播异常。独立能力指执行不依赖路由，不等于裸 new 就已初始化所有组件。事务在开始它的同一线程结束。

嵌套是共享外层事务的计数，不是保存点。执行失败/嵌套回滚标记 rollback-only，即使业务捕获异常，外层不能继续正常提交。跨库和多连接只是本地事务协调，不是分布式原子提交。

### 使用边界与排障

- 更新通常跳过 null；普通 copyProperties 或懒同步的 null 跳过不等于数据库清列。
- insertBefore/updateBefore 可能填充字段，表结构必须匹配实体实际继承字段。
- cache() 会保存并复制查询模型，模型必须符合深克隆支持范围；原始 SQL 与外部写入需要业务管理缓存一致性。
- selectOne 返回 null 时先处理“未找到”，不将它解释成解析错误或唯一性保证。
- 日期范围为空先核对列类型、格式、时区与条件，不默认归咎于数据库驱动。
- 表达式、JOIN、JSON 路径、行锁按目标库核对；没有跨库 SQL 自动翻译器。
- NoSuchMethodError 先检查编译与运行时 JAR 是否一致；源码修复不等于已经替换部署产物。

关联增强见 [第三专题](03-关联查询与代理.md)，工具与复制语义见 [第四专题](04-工具类.md)，源码扩展及验收记录见 [第五专题](05-扩展开发与维护.md)。

---

<!-- source: doc/03-关联查询与代理.md -->
## 关联查询与代理

本文说明 JoinQuery、JoinLazy 和 JoinSync：如何组装用户角色菜单、批量加载列表关联，以及在事务提交时写回 setter 修改。

阅读导航：[行为速查](#行为速查) · [级联案例](#用户角色菜单级联) · [懒加载](#懒加载) · [批量预加载](#列表批量预加载) · [懒同步](#懒同步) · [会话与边界](#会话与代理边界)

接入和实体定义见 [第一专题](01-接入与数据源.md)与 [第二专题](02-CRUD与统计分析.md)。这里是应用侧追加查询和 CGLIB 代理，不是 SQL JOIN，也不是 JPA 的实体管理。

### 行为速查

| 字段注解 | 加载 | 修改 |
| --- | --- | --- |
| JoinQuery | 查询结果增强时立即查询关联 | 普通对象，不因此自动写库 |
| JoinQuery + JoinLazy | 路由事务内首次访问 getter 时加载 | 仅延迟读取 |
| JoinQuery + JoinSync | 立即加载，路由事务内增强关联对象 | 代理 setter 的非 null 修改在提交时写回 |
| 三者组合 | getter 加载并创建关联同步代理 | 事务内 setter 修改延后写回 |

ORM 查询得到的模型会经过 EntityProxyService，由 LazyProxyFactory 处理关联，符合条件的关联再由 SyncProxyFactory 增强。通常不用业务再次代理查询结果。自己 new 出来的 DTO 需要调用公开入口。

### 用户角色菜单级联

以下完整 DTO 放在 demo.dto.RouterDetails。引用的三个业务实体必须由应用提供，不依赖 admin 模块；每个目标实体需有表映射、无参构造、getter/setter 和明确类型的 ID。

| 实体 | 示例契约 |
| --- | --- |
| demo.domain.SysUserEntity | String id、username、nickname；List<String> sysRoleIds |
| demo.domain.SysRoleEntity | String id；List<String> sysMenuIds |
| demo.domain.SysMenuEntity | String id；菜单显示属性 |

sysRoleIds/sysMenuIds 的元素类型必须与目标 id 一致，字段与表中 JSON/集合存储匹配。Lombok Data 需要业务项目配置注解处理。

```java
package demo.dto;

import com.yulinlin.data.core.anno.JoinQuery;
import demo.domain.SysUserEntity;
import demo.domain.SysRoleEntity;
import demo.domain.SysMenuEntity;
import lombok.Data;
import java.util.List;

@Data
public class RouterDetails {
    private String username;
    private String loginType;

    @JoinQuery(primary = "username", value = "${username}")
    private SysUserEntity user;

    @JoinQuery(primary = "id", value = "${user.sysRoleIds}")
    private List<SysRoleEntity> roles;

    @JoinQuery(primary = "id", value = "${roles.sysMenuIds}")
    private List<SysMenuEntity> menus;

    public RouterDetails() { }
    public RouterDetails(String username, String loginType) {
        this.username = username;
        this.loginType = loginType;
    }
}
```

业务方法体片段，username/loginType 已由调用方提供：

```java
import com.yulinlin.data.core.session.SessionUtil;
import demo.dto.RouterDetails;

RouterDetails details = SessionUtil.callable("mysql", () ->
        SessionUtil.route().getLazyProxy(new RouterDetails(username, loginType)));
```

这个类没有 JoinLazy，入口会立即尝试加载 user、roles、menus。getLazyProxy 的名称不代表所有字段都延迟。

即时关联按反射字段顺序处理，不是依赖拓扑排序。示例按 user → roles → menus 放置，但复杂图不能仅靠反射顺序保证正确，必要时分步查询或采用下面的懒字段方案。路由的默认递归深度保护是 6，不等于循环关联可以安全展开。

#### 动态值和固定值

| JoinQuery.value | 含义 |
| --- | --- |
| "${username}" | 当前对象 username |
| "${user.sysRoleIds}" | 当前对象 user 的角色 ID |
| "${roles.sysMenuIds}" | 遍历 roles 汇集菜单 ID |
| "username" | 固定字符串，不是当前属性值 |

动态取值必须带 ${...}，不能按其他 ORM 的属性名规则猜测。primary 是目标匹配属性，默认 id，不要求一定是物理主键。

放在数据库实体里的关系属性加 `@JoinField(exist = false)`，避免被当作列。纯组装 DTO 可以没有 JoinTable，但目标实体仍需完整映射和实际表。

List/Set 的元素类型必须可推断，不使用原始 List、List<?> 或不明确泛型。无匹配时单对象为 null，集合为空；单对象匹配键应保证唯一，否则取第一条。

### 懒加载

在上面的三个关联字段上各加 JoinLazy；其余类结构不变：

```java
import com.yulinlin.data.core.anno.JoinLazy;

@JoinLazy
@JoinQuery(primary = "username", value = "${username}")
private SysUserEntity user;

@JoinLazy
@JoinQuery(primary = "id", value = "${user.sysRoleIds}")
private List<SysRoleEntity> roles;

@JoinLazy
@JoinQuery(primary = "id", value = "${roles.sysMenuIds}")
private List<SysMenuEntity> menus;
```

在创建它的同一线程、同一路由事务中读取所需字段：

```java
SessionUtil.route().transaction(() ->
        SessionUtil.callable("mysql", () -> {
            RouterDetails details = SessionUtil.route()
                    .getLazyProxy(new RouterDetails(username, loginType));
            var user = details.getUser();
            var roles = details.getRoles();
            var menus = details.getMenus();
            // 在事务内将所需字段复制到普通响应 DTO。
            return null;
        }));
```

也可在 Spring Service public 方法上用 JoinTransaction，经代理调用。独立 JdbcSession 的物理事务不会自动使 RouteSession 的事务状态开启。

- 没有路由事务时，JoinLazy 字段会被跳过，不自动改成立即查询。
- getter 才触发加载，直接访问字段或原始对象不是等价调用。
- 未加载字段在事务外、其他线程或新事务读取会报错；已加载数据可在事务后读取。
- 不直接把未加载代理交给 Controller JSON 序列化或异步任务。
- 代理类不能是 final/record；需要无参构造，相关 getter/setter 可被 CGLIB 覆盖。
- 一次成功加载包括 null/空结果，不反复查询；失败不标记完成，可重试；显式 setter 值不被后续批量赋值覆盖。
- 循环懒加载会报错，仍应避免循环图。

### 列表批量预加载

普通关联会汇集同批父对象的关联键，去重后按 IN 分批查询，再按目标属性建立索引分配。默认 batchSize 为 512，表示每批去重键数，不是结果行数或事务提交大小。

同一次 selectList 的懒代理共享上下文，首次访问某个关联 getter 时为同批对象加载该字段，不逐个父对象发查询。手动 DTO 列表一次性传入：

```java
SessionUtil.route().transaction(() ->
        SessionUtil.callable("mysql", () -> {
            var details = SessionUtil.route().getLazyProxy(
                    java.util.List.of(new RouterDetails("alice", "password"),
                                      new RouterDetails("bob", "password")));
            details.getFirst().getUser(); // 汇集 alice/bob 查询并分配。
            details.get(1).getUser();     // 这一批已加载，不再单独查 bob。
            return null;
        }));
```

这段使用的是加过 JoinLazy 的 RouterDetails。不要逐个 getLazyProxy(dto)，否则不是同一批。不同查询结果不自动合并。

`@JoinQuery(value = "${ids}", batchSize = 256)` 可调每批 IN 键上限。关联集合不保证输入 ID 顺序；复杂 wheres/model 分支仍可能 N+1，不把全部关联规则都描述成自动 IN。

### 懒同步

懒同步捕获事务内的 setter 修改，在提交阶段更新数据库，不是后台线程写入。关联字段增加 JoinSync，例如：

```java
import com.yulinlin.data.core.anno.JoinSync;

@JoinSync
@JoinQuery(primary = "username", value = "${username}")
private SysUserEntity user;
```

方法体片段使用上文 DTO 和有 nickname 持久化属性的业务实体：

```java
SessionUtil.route().transaction(() ->
        SessionUtil.callable("mysql", () -> {
            RouterDetails details = SessionUtil.route()
                    .getLazyProxy(new RouterDetails(username, loginType));
            var user = details.getUser();
            if (user == null) throw new IllegalStateException("user not found");
            user.setNickname(newNickname);
            return null;
        }));
```

正常提交阶段写回，异常回滚丢弃待同步记录；不会还原 Java 对象。与 JoinLazy 组合时，必须先 getter 得到真正的关联同步代理。

#### 显式增强普通实体

普通根查询结果不等于已经具有同步代理。以下方法体片段使用第二专题的 DemoUser：

```java
SessionUtil.route().transaction(() ->
        SessionUtil.callable("mysql", () -> {
            DemoUser user = ModelSelectWrapper.newInstance("mysql", DemoUser.class)
                    .eq("id", id).selectOne();
            if (user == null) throw new IllegalStateException("user not found");
            DemoUser sync = SessionUtil.route().getSyncProxy(user);
            sync.setStatus(1);
            return null;
        }));
```

调用前校验 id。模型有 createLazyProxy/createSyncProxy/commitUpdate 快捷方法；createSyncProxy 在无路由事务时会开始一个，commitUpdate 结束一层路由事务，不只是提交当前对象。业务中优先用成对回调，避免提前结束外层事务。

类上的 JoinSync 不意味着任意根查询都自动增强；自动关联路径检查字段注解。公开入口是 RouteSession/模型方法，不直接 new 内部 SyncProxyFactory。

### 会话与代理边界

字段可以指定独立会话，下面是已有 DTO 中的字段片段：

```java
import com.yulinlin.data.core.anno.JoinSession;

@JoinSession("sqlite")
@JoinQuery(primary = "id", value = "${localUserId}")
private LocalUserEntity localUser;
```

LocalUserEntity 与 localUserId 由业务提供。未指定时关联上下文保留创建时的来源组；后续 getter 即使进入其他会话栈，也使用原组。同步更新也按原组执行。

| 参数 | 当前范围 |
| --- | --- |
| order | 普通关联排序；跨批 Java 合并与数据库 collation 不一定一致 |
| batchSize | 普通 IN 的键数上限 |
| wheres | 按父对象构造复杂条件，可能逐对象查 |
| size | 正值只在 wheres/model 分支分页，不作用于普通 primary/value 分支 |
| model | 当前进入 count 分支，不是替代集合泛型的通用参数 |

#### 修改跟踪的限制

- 目标必须是可更新实体，有完整非 null 主键；创建/提交校验主键，禁止代理改主键。
- 只捕获真正持久化属性的单参数 setter；普通方法、直接写字段、修改原对象不自动同步。
- null 跳过：setX(null) 不清列，并取消此前该字段的待同步值。
- 集合 add、Map.put、嵌套对象原地变化不自动跟踪，需要调用持久化属性 setter 或显式更新。
- 未调用的 setter 不产生部分更新，未修改的 0/false/初始化值不会被全部覆盖到数据库；同值 setter 仍可能计为修改。
- setter 捕获对象引用，不是深快照；setter 后原地修改同一对象可能改变最终编码值。
- 支持的 Integer/int、Long/long 版本字段按原始版本条件递增；写入数不匹配视为乐观锁失败，不承诺全部 Wrapper 都具备相同版本机制。
- 代理绑定原始线程与路由事务，结束后不能继续 setter 修改。缓存保存未增强数据，读取时复制并创建当前查询代理，不能复用旧事务代理。

Spring 绑定事务中的同步写回在物理提交前处理，只读事务禁止同步 setter；高级传播与分布式一致性仍按 [第二专题](02-CRUD与统计分析.md#事务使用)的边界理解。

### 常见问题

| 问题 | 检查 |
| --- | --- |
| 关联没有数据 | ${...}、源属性、泛型、键类型、目标表与会话 |
| 懒字段一直为 null | 是否经过代理入口，是否有路由事务，是否访问 getter |
| 提示原始事务不一致 | 是否跨线程、事务外读取或把旧代理带进新事务 |
| 列表查询仍很多 | 是否同批入口，是否使用复杂 wheres/model 分支 |
| setter 后没更新 | 是否同步代理、原始事务、非 null 值与可靠主键 |
| 缓存命中复制失败 | 模型是否满足深克隆边界，是否包含流/连接等资源 |

实现职责与验证范围在 [第五专题](05-扩展开发与维护.md)，不要把历史测试记录解释成当前全部业务示例已运行。

---

<!-- source: doc/04-工具类.md -->
## 工具类使用

本文提供 HTTP、反射与深克隆、JSON、日期、字符串、树和 ID 的直接用法。仅使用普通工具不要求实体映射；依赖选择见 [第一专题](01-接入与数据源.md#模块选择)。

阅读导航：[HTTP](#http-请求) · [上传下载](#上传与下载) · [超时和错误](#超时与错误处理) · [反射与克隆](#反射复制与深克隆) · [JSON](#json-转换) · [其他工具](#常用工具速查)

### HTTP 请求

公共类型在 `com.yulinlin.data.core.http`：HttpRequestClient、HttpRequest、HttpUtil、HttpResponse、HttpFile、HttpRequestException。底层基于 Spring RestClient，调用同步阻塞。

下面是完整 Service。复用客户端，每次调用创建新的请求对象；地址是示例，需替换为自己的服务：

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

    public RemoteApiService(HttpRequestClient client) { this.client = client; }

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

以下为业务方法体片段，采用静态入口：

```java
import com.yulinlin.data.core.http.HttpUtil;
import com.yulinlin.data.core.http.HttpResponse;
import java.util.Map;

HttpResponse response = HttpUtil.get("https://api.example.com/users")
        .query("page", 1).query("size", 20)
        .header("X-Request-Id", "demo")
        .execute();

String text = response.getBodyAsString();
int status = response.getStatus();

HttpUtil.post("https://api.example.com/forms")
        .form(Map.of("name", "alice", "enabled", true))
        .execute();
```

| 能力 | 入口 |
| --- | --- |
| HTTP 方法 | get/post/put/patch/delete；其他方法 request(HttpMethod, url) |
| 查询与头 | query(name, value)、header(name, value)、headers(HttpHeaders) |
| 认证 | basicAuth(user, password)、bearerToken(token) |
| JSON | json(Object) |
| 普通表单 | form(name, value)、form(Map<String,Object>) |
| 其他 body | body(Object, MediaType) |
| 响应 | getStatus、getStatusCode、getHeaders、getBody、getBodyAsString、bodyAs |

query 的 null 值会忽略。同一个请求只使用一种 body 类型，不能混合 json 与 form；请求构造器是可变对象，不跨线程共享。

### 上传与下载

```java
import com.yulinlin.data.core.http.HttpFile;
import com.yulinlin.data.core.http.HttpUtil;
import java.nio.file.Path;
import java.time.Duration;

// multipart 普通字段和文件一起上传，不使用 form() 混入。
HttpUtil.post("https://api.example.com/files")
        .multipart("category", "invoice")
        .file("file", HttpFile.of(Path.of("upload/invoice.pdf")))
        .execute();

// 大文件流式落盘。
HttpUtil.get("https://api.example.com/export")
        .timeout(Duration.ofSeconds(60))
        .downloadTo(Path.of("download/export.zip"));
```

HttpFile 支持 of(Path)、of(filename, byte[], MediaType)、of(filename, InputStream, MediaType)。输入流上传不保证可重放，不重复使用已消费的原流。

execute 会把响应 body 放入内存，大文件优先 downloadTo。下载会创建父目录并覆盖同名文件，失败可能留下部分文件，不保证原子替换；返回响应 body 为空，内容已写磁盘。业务需校验目标路径，不能直接信任远端路径。

### 超时与错误处理

Spring 注入客户端默认 10 秒，配置如下：

```yaml
yulinlin:
  http:
    timeout: 10s
```

单次 `timeout(Duration)` 覆盖默认值，必须为正数。同一 Duration 同时配置连接和响应读取超时，不提供独立链式 connectTimeout/readTimeout；不是覆盖网络、重定向、写盘等全部阶段的严格总截止时间。

静态 HttpUtil 默认也为 10 秒，但不自动读取 Spring 配置。`HttpUtil.withTimeout(duration)` 返回新客户端；`HttpUtil.setClient(client)` 替换静态默认客户端，应在初始化阶段明确配置，不在请求处理中反复切换。

`new HttpRequestClient(restClient, mapper)` 沿用给定客户端设置，不自动添加默认超时。单次 timeout 目前会新建底层客户端，大量相同超时请求优先复用预配置客户端。

#### 判断是否为 404

```java
boolean missing = HttpUtil.isNotFound("https://api.example.com/resource");
```

它执行 GET，不是 HEAD；只有明确 HTTP 404 返回 true。超时、网络错误和其他状态返回 false，所以 false 不证明存在或健康。成功响应按普通请求读入内存，不适合检查超大文件。

带认证的检查或需要区分其他失败时，自己处理异常：

```java
import com.yulinlin.data.core.http.HttpRequestException;

try {
    HttpUtil.get("https://api.example.com/resource").bearerToken(token).execute();
} catch (HttpRequestException error) {
    Integer status = error.getStatusCode(); // 网络错误可能为 null。
    if (!Integer.valueOf(404).equals(status)) throw error;
    // 业务处理确实不存在。
}
```

普通 4xx/5xx、网络调用与部分 JSON 解码失败包装为 HttpRequestException；参数校验和非法 URI 不保证都包装。204 空响应不当作 JSON 解析。不假定自动重试、自动跟随重定向或异步能力存在。

用户提供 URL 时校验协议、目的地址与内网访问策略，防止 SSRF；不要无差别记录 token、cookie、密码及错误响应体。

### 反射复制与深克隆

以下完整普通 Bean 不依赖 ORM；作为本节和 JSON 示例模型：

```java
package demo.tools;

import java.util.ArrayList;
import java.util.List;

public class ToolUser {
    private String name;
    private List<String> tags = new ArrayList<>();
    public ToolUser() { }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
```

业务方法体片段：

```java
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import demo.tools.ToolUser;

ToolUser user = ReflectionUtil.newInstance(ToolUser.class);
ReflectionUtil.invokeSetter(user, "name", "alice");
Object name = ReflectionUtil.invokeGetter(user, "name");

ReflectionUtil.PropertyAccess access = ReflectionUtil.property(ToolUser.class, "name");
access.set(user, "bob");
Object value = access.get(user);

ToolUser deep = ReflectionUtil.deepClone(user);
ToolUser target = new ToolUser();
ReflectionUtil.copyProperties(user, target);
```

| API | 语义 |
| --- | --- |
| newInstance(Class) | 可访问无参构造 |
| invokeGetter/invokeSetter | 名称和嵌套路径；不推定中间对象自动创建 |
| invokeMethod(bean, name, args...) | 根据名称和参数找方法；歧义报错，变长参数传声明数组 |
| property(Class, name) | 单一属性访问器，可缓存；不是嵌套路径解析器 |
| clone(value) | 浅复制 |
| deepClone(value) / clone(value, true) | 对象图深复制，null 返回 null |
| copyProperties(source, target) | 同名属性深复制到已有目标；跳过缺少属性及源 null |

深克隆覆盖支持的 Bean、集合、Map 键值、数组和可变值，保留对象图的循环与共享引用。每次调用使用独立身份跟踪上下文，不把 DeepCopies 改成跨请求共享单例。

整体 deepClone(list) 保留跨元素共享关系；逐条 deepClone 不保留跨调用共享关系。这两种方式不能仅凭速度互换。

使用限制：

- Bean 需要无参构造和可写属性；record、不可写 final 字段、线程/流/连接不作为通用复制目标。
- 不自动把嵌套实体转换为另一种 DTO，也不自动转换集合泛型。
- 不可变值可以复用；包装集合可能变成普通可变集合，不保证保留不可修改/同步包装语义。
- copyProperties 失败可能已部分修改目标，没有回滚；null 跳过不等于 SQL NULL 更新。
- 私有成员访问仍受 Java 模块 opens 限制，不绕过任意访问控制。
- 当前基于 MethodHandle，ReflectAsmUtil 是弃用兼容入口；Kryo 不是 lang 的克隆后端。
- 性能实测必须看相同模型、粒度和环境；历史 JMH 数据在 [第五专题](05-扩展开发与维护.md#历史克隆基准)，不作当前通用排名。

### JSON 转换

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.yulinlin.data.lang.json.JsonUtil;
import demo.tools.ToolUser;
import java.util.List;

String json = JsonUtil.toJson(new ToolUser());
ToolUser user = JsonUtil.parseJson(json, ToolUser.class);
List<ToolUser> users = JsonUtil.parseJson("[]", new TypeReference<List<ToolUser>>() {});
```

JsonUtil.to(source, Target.class) 经 JSON 做类型转换，受 Jackson 注解/配置影响，不是对象图克隆，不保持任意循环与对象身份。

JsonUtil 独立模式有自有 mapper；starter 会设置为 Spring ObjectMapper。HttpUtil 静态客户端的 mapper 是另一套，不自动与 JsonUtil 同步。

### 常用工具速查

| 类型和准确包名 | 常用方法 | 注意 |
| --- | --- | --- |
| com.yulinlin.data.lang.util.StringUtil | isNull/isNotNull、javaToColumn/columnToJava | isNull 检查 null/空串，不等同 isBlank |
| com.yulinlin.data.lang.util.DateTime | now、parse(text, format) | 框架可变日期类型，不当作不可变 java.time |
| com.yulinlin.data.lang.util.Page | of(list)、page(list, page, size) | 后者为内存分页，不执行 SQL |
| com.yulinlin.common.util.SnowflakeUtil | nextId/nextIdStr | 多实例要规划节点，随机默认配置不保证绝不冲突 |
| com.yulinlin.common.util.TreeUtil | buildTree(list) | 元素实现 common.domain.ITreeNode；原地追加 children，先处理重复 ID、环和旧 children |
| com.yulinlin.starter.domain.R | newInstance(data) | 响应包装，不设置 HTTP 状态；不猜测 R.ok/R.success |

表中提供完整类型名，按需 import；树节点接口为 `com.yulinlin.common.domain.ITreeNode`。

缓存、线程池、锁、金额与事件等类存在于源码中，未在这里承诺全部业务契约；使用前读具体实现，不仅凭类名生成调用。

### 常见问题

| 问题 | 优先检查 |
| --- | --- |
| ReflectionUtil.property 的 NoSuchMethodError | 编译与运行时 lang/core 是否同一产物 |
| DTO 复制字段缺失 | 名称、可写属性、null 跳过及嵌套类型是否相容 |
| 克隆列表后共享引用不同 | 整体克隆还是每元素独立调用 |
| HTTP 超时配置没生效 | Spring 客户端、静态客户端、手动构造和单次覆盖分别核对 |
| 下载成功但 body 为空 | downloadTo 内容在文件中 |
| 404 检查返回 false | 不代表存在，需区分网络/状态异常 |

本文为源码核对的使用说明，不表示这些外部 HTTP 地址、文件路径或全部示例已执行。
