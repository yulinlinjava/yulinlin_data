# 数据源接入开发规范

本文面向框架维护者和参与模块开发的 AI，说明如何新增数据库或第三方数据源模块。业务使用方式仍以 [ORM](../topics/10-orm.md)、[多数据源](../topics/15-datasources.md)和[事务](../topics/20-transactions.md)专题为准。

接入原则是：公共层复用请求、CRUD 和执行流程；ParseManager 注册后端语法差异；Session 处理执行、驱动读写、事务和资源。没有差异就不新增实现类，不通过依赖另一个数据库模块获得公共能力。

源码核对日期为 2026-10-05，适用 JDK 25、制品版本 3.0、Spring Boot 3.5。默认会话已改为由数据库模块自行创建；本轮没有运行测试、编译、打包或文档生成脚本。代码片段依据源码签名整理，不代表已经编译验证。

## 接入流程

1. 确认 JDBC 或非 JDBC 接入方式，列出支持能力和明确限制。
2. 建立独立模块，添加公共模块和驱动或 SDK 依赖。
3. 实现 ParseManager，只注册实际需要的解析器。
4. 复用 JdbcSession，或实现确有必要的 Session 差异。
5. 核对编解码、参数绑定和结果读取。
6. 声明工厂、自动配置及自动配置入口，落实 Session 创建路径。
7. 验证路由、事务、并发和资源所有权的设计。
8. 补齐使用专题与验收案例，再按用户授权执行验证。

### 先确定接入类型

| 接入类型 | 依赖和解析 | Session 与创建方式 |
| --- | --- | --- |
| JDBC 数据库 | 依赖 jdbc 和对应驱动；继承 SqlParseManager | 优先复用 JdbcSession；数据库模块自行创建主会话，附加数据源显式声明会话 |
| 本地嵌入式 JDBC 数据库 | 复用 jdbc；补充文件、初始化与数据库限制 | 可以内部管理 DataSource，并显式创建会话；参考 SQLite 的资源持有对象 |
| 非 JDBC 数据源 | 依赖 core 和官方 SDK；节点转换成后端请求 | 实现后端 Session 和 SessionFactory，显式创建会话 Bean |

能力清单至少覆盖 CRUD、分页、排序、JOIN、聚合、JSON、行锁、事务、批量写入和并发写入。不存在的能力应在调用时明确拒绝或提供经过说明的替代路径，不能静默忽略节点、返回伪造的成功结果。

共享 JDBC 解析器并不是自动兼容所有数据库的标准 SQL 层。例如默认分页和 JSON 表达式带有 MySQL 风格，日期与数值区间分组需要数据库模块注册解析器。必须逐项核对目标数据库，不能仅替换驱动后宣布完全兼容。

## 公共层职责

一次请求的主要路径如下：

```text
Wrapper 或 Request
  → RouteSession 选组及加入事务参与者
  → 具体 Session 构造参数上下文并调用 ParseManager
  → ParseResult 携带 SQL 或 SDK 请求
  → Session 执行并将结果转换为 IDataBuffer
  → CoderManager 解码为业务对象
```

配置完整的 Session 也可以直接执行 Request，不经过 RouteSession；Model Wrapper 的便利执行入口、关联代理等仍有路由依赖。

| 组件 | 应当负责 | 不应承担 |
| --- | --- | --- |
| Wrapper 和 Request | 描述查询或写入意图 | 判断具体数据库语法 |
| ParseManager 和 IParse | 注册节点解析器，生成 SQL 或 SDK 请求 | 获取连接、提交事务、保存请求的全局可变状态 |
| CoderManager 和 IDataBuffer | Java 值与后端值的转换 | 选库、连接池管理 |
| Session | 执行请求、绑定和读取值、管理事务与执行资源 | 重复实现已经通用的 CRUD 解析逻辑 |
| SessionFactory | 创建并配置 Session，包括 group 和公共组件 | 默认承担路由注册或数据库建表 |
| RouteSession | 选择已注册 Session，协调事务参与者 | 自动转换方言或提供跨库原子提交 |

核心实现可从 [AbstractSession](../../core/src/main/java/com/yulinlin/data/core/session/AbstractSession.java)、[SqlParseManager](../../jdbc/src/main/java/com/yulinlin/jdbc/sql/SqlParseManager.java)和 [JdbcSessionFactory](../../jdbc/src/main/java/com/yulinlin/jdbc/session/JdbcSessionFactory.java)进入。

## 建立独立模块

以下以占位模块 `mydb` 为例。`jdbc:mydb:`、驱动坐标和包名均需按真实数据库替换，不表示仓库已有该模块或驱动。

```text
mydb/
  pom.xml
  src/main/java/com/yulinlin/jdbc/mydb/
    MydbAutoConfiguration.java
    MydbParseManager.java
    MydbSession.java              仅有执行或驱动差异时需要
    parse/                       只保留实际差异解析器
  src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
  src/test/java/                 解析、执行与自动配置验收案例
```

模块继承根 POM，添加到根 POM 的 `modules`。JDBC 模块直接依赖 `com.yulinlin:jdbc` 和目标驱动，公共 core、lang 能力通过公共模块获得；不要依赖 mysql 或 postgresql 来复用解析器。驱动版本优先使用项目已有的依赖管理；未管理的驱动必须明确选择兼容版本。

下面仅是 JDBC 模块的依赖片段，驱动依赖需要另行填入：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>jdbc</artifactId>
    <version>3.0</version>
</dependency>
```

common 提供 Model Wrapper 便利门面，starter 是组合入口，二者都不是新数据库模块的强制依赖。独立使用新模块时也不能暗中依赖应用已经引入另一个数据库模块。

## 注册数据库解析器

### 公共 CRUD 先注册再覆盖差异

JDBC 解析管理器继承 `com.yulinlin.jdbc.sql.SqlParseManager`，在 `init()` 中先调用 `super.init()`，再注册差异解析器。当前 PostgreSQL 实现就是这个结构：

```java
package com.yulinlin.jdbc.postgresql;

import com.yulinlin.jdbc.postgresql.parse.NameParse;
import com.yulinlin.jdbc.postgresql.parse.PageParse;
import com.yulinlin.jdbc.postgresql.parse.group.DateParse;
import com.yulinlin.jdbc.postgresql.parse.group.IntervalParse;
import com.yulinlin.jdbc.sql.SqlParseManager;

public class PostgresqlParseManager extends SqlParseManager {
    @Override
    protected void init() {
        super.init();
        register(new NameParse());
        register(new PageParse());
        register(new DateParse());
        register(new IntervalParse());
    }
}
```

这里复用普通 CRUD、条件、JOIN 和聚合解析器，仅覆盖字段和 JSON、分页、日期分组、数值区间分组。MySQL 的日期与区间分组也由 MysqlParseManager 注册自己的实现。

没有语法差异的节点直接使用公共注册项，不再创建空继承类、重复包或仅转发的工具类。当前架构也不要求新增独立 SqlDialect，或把 SQL 拼接搬到 Session 的 `sqlXXX` 方法中。

### 注册键与线程安全

[SimpParseManager](../../core/src/main/java/com/yulinlin/data/core/parse/SimpParseManager.java) 使用 `IParse.getNodeClass()` 作为注册键，相同键后注册者覆盖前者。普通节点分派按实际 Class 精确查找，并不自动查找父类解析器；有特殊分派的公共入口则应沿用它原有的注册契约，例如 NameParse 的元节点键。

开发时注意：

- 确认 `IParse<T>` 的 T 是目标节点，而不是只改了解析器类名；复杂继承时可显式重写 `getNodeClass()`。
- SimpParseManager 构造函数会调用可重写的 `init()`。此时子类字段初始化尚未完成，不要在其中依赖构造后才赋值的成员。
- 注册表初始化后保持不变。同一数据库语法可复用 ParseManager，不同数据库使用不同注册表。
- IParse 实现不保存当前请求的 root、参数表、别名或连接。请求状态放入 IParamsContext；JDBC 的 SqlParamsContext 提供本次解析的管理器、NameParse 和选择表达式信息，不引用 Session。
- 用参数上下文编码并绑定业务值；原始 SQL、表名和表达式不能直接来自未经校验的外部输入。

例如 PostgreSQL 分页解析器使用公共节点 `SqlPage`：

```java
package com.yulinlin.jdbc.postgresql.parse;

import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.jdbc.PageSqlUtil;
import com.yulinlin.jdbc.sql.SqlPage;

public class PageParse implements IParse<SqlPage> {
    @Override
    public String parse(SqlPage page, IParamsContext params, IParseManager manager) {
        return PageSqlUtil.postgresqlPageSql(page.page(), page.size());
    }
}
```

低层子节点可以返回 SQL 片段，公共根语句解析器负责组装。非 JDBC 根请求需要产出 AbstractSession 能消费的 ParseResult，内部 request 可以是 SDK 请求或自定义命令对象；不能假定每个解析器都必须返回 SQL 字符串。

仅检查语法时，可直接调用 `new PostgresqlParseManager().parse(node, params)`，也可调用已配置会话的 `session.parseSql(node, params)`。node 和 params 必须是已准备的真实节点与上下文；这两个入口只解析，不借连接、不执行 SQL。

## 选择 Session 并核对类型转换

### 优先复用 JDBC 执行流程

只有 SQL 不同：使用通用 JdbcSession，由工厂设置 MydbParseManager。

还有驱动绑定、结果读取或执行流程差异：继承 JdbcSession，并在构造函数中设置对应 ParseManager。常见扩展点是：

```java
protected void bindParameter(PreparedStatement statement, int index, Object value)
        throws SQLException;

protected Object readColumn(ResultSet rows, String label, int jdbcType)
        throws SQLException;
```

这是方法签名说明，不是完整可编译的子类。当前 [PostgresqlSession](../../postgresql/src/main/java/com/yulinlin/jdbc/postgresql/PostgresqlSession.java) 将 InputStream 使用 setBinaryStream 绑定，并按 JDBC 类型读取布尔列，保留 false 与 SQL NULL。连接、事务、批处理和查询执行仍继承公共实现。

除非确有执行差异，不重新复制 JdbcSession、ConnectionUtil 或事务管理逻辑。通过实际调用路径检查扩展点是否被所有查询和写入入口使用，不能只让独立工具方法产生正确结果。

### 编解码与驱动读写是不同层次

JDBC 默认使用 JdbcCoderManager，复用 core 的日期、枚举、数字等编码器，并增加集合、Map 和对象的 JSON 编解码。以 DateCoder 为例，Date 先被编码为 DateTime 字符串，再交给 PreparedStatement；新增数据库需要确认目标列如何接受该值。

类型验收至少包括：

- 日期时间的格式、时区和范围查询语义。
- BigDecimal 的精度与范围查询，避免经过 double 丢失精度。
- 枚举的存储值和无法识别值的行为。
- JSON 对象、集合和 Map 的整字段往返与路径查询。
- 布尔 false、SQL NULL、空字符串、空集合及基础类型字段。
- byte[]、InputStream 的绑定、完整读取和流的关闭责任。

驱动参数类型、数据库原生类型和 Java 编码器必须一起核对。能把 JSON 字符串存进 TEXT，不表示已经支持原生 JSON 类型绑定、局部更新或索引；能写入二进制，也不表示通用 getString 读取能完整还原。

特殊类型确有需要时再增加 ICoder 或专用 CoderManager。`MapCoderManager.registerCoder` 不是覆盖注册接口：它不会自动删除原有同类型编码器，也不会清除已经缓存的选择结果。替换内置类型时应审查注册与缓存策略，不在会话运行后随意追加并假定立即生效。

IDataBuffer 是请求或结果容器，由 CoderManager 按次创建，不能作为可变单例在多个请求间共享。

## 配置工厂并落实 Session 创建

### 两种 JDBC 工厂入口

下面两段均为工厂方法体片段，相关类型需在配置类中导入。只有解析差异时：

```java
// 方法体片段，MydbParseManager 为待实现的数据库解析管理器。
return new JdbcSessionFactory(new MydbParseManager(), "jdbc:mydb:");
```

需要自定义 Session 时，当前 PostgreSQL 采用：

```java
return new JdbcSessionFactory("jdbc:postgresql:", PostgresqlSession::new);
```

第二种方式由 Session 构造函数设置解析管理器，工厂不会用默认管理器覆盖它。工厂的 URL 前缀及 supportsJdbcUrl 接口保留兼容元信息，不参与自动配置的选库或 create 的类型校验；只有一个 ParseManager 参数的旧构造函数记录 jdbc:mysql: 前缀，新增模块可以传入自身前缀，但不能把它当成运行时保护。

工厂 `create(dataSource, group)` 除了创建对象，还配置编码器、日志、缓存、过滤器、代理服务、JDBC 属性和会话组。当前 JdbcSessionFactory 依赖 Spring 注入公共组件，不能认为手动 `new JdbcSessionFactory(...)` 后马上 create 就等价于完整初始化。

### 数据库模块直接创建 Session

每个数据库模块同时注册自己的工厂和默认 Session，公共 DataJdbcApplication 只提供属性、编码器、日志与事务切面。MySQL 创建 mysqlSession，组名 mysql；PostgreSQL 创建 postgresqlSession，组名 postgresql；SQLite 按自己的文件配置创建 sqliteSession，默认组 sqlite，默认文件 data/local.db；未提供 file 也会启用，不增加模块启用开关。组名可以由用户自定义，不是 DataSource Bean 名，也不会自动按名字选择物理数据源。

新 JDBC 模块可采用下面的完整配置结构。MydbParseManager、包名、Bean 名和 URL 前缀是占位，需替换为实际实现；若需要专用 Session，替换工厂构造方式即可。

```java
package com.yulinlin.jdbc.mydb;

import com.yulinlin.jdbc.DataJdbcApplication;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import javax.sql.DataSource;

@AutoConfiguration(after = {DataSourceAutoConfiguration.class, DataJdbcApplication.class})
public class MydbAutoConfiguration {
    @Bean("mydbSessionFactory")
    @ConditionalOnMissingBean(name = "mydbSessionFactory")
    public JdbcSessionFactory mydbSessionFactory() {
        return new JdbcSessionFactory(new MydbParseManager(), "jdbc:mydb:");
    }

    @Bean("mydbSession")
    @ConditionalOnMissingBean(name = {"mydbSession", "jdbcSession"})
    @ConditionalOnSingleCandidate(DataSource.class)
    public JdbcSession mydbSession(DataSource dataSource,
            @Qualifier("mydbSessionFactory") JdbcSessionFactory factory) {
        return factory.create(dataSource, "mydb");
    }
}
```

Session Bean 方法直接调用自己的工厂，不读取 DataSource URL、不做类型匹配，也不返回 null；模块和驱动选择由应用配置负责。只要有唯一候选 DataSource 且没有显式覆盖，多个模块都可能创建自己的会话，不能宣称自动筛掉不匹配的数据库。

不同组名可以避免把两个模块的 Session 当成同组负载均衡节点，但不能保证它们绑定了不同的数据源。有 @Primary 的多数据库应用应显式声明名为 jdbcSession 的主会话让双方默认创建退让，再为其他数据库创建独立组，并用 @Qualifier 明确选择每个工厂和 DataSource。也可以不设置唯一主 DataSource，全部会话显式声明；参考 PostgreSQL 混合数据库配置示例。

在模块的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中写入：

```text
com.yulinlin.jdbc.mydb.MydbAutoConfiguration
```

仅添加 @AutoConfiguration 类而没有声明文件，不能保证它被应用自动发现。数据库配置在 Spring 数据源和公共 JDBC 组件配置之后加载；不要再要求公共 jdbc 按数据库模块名维护 afterName 列表。按需要补充驱动存在、配置开启等条件，避免未启用模块创建外部客户端或连接。

### 默认主数据源的创建链

普通 Spring Boot 主 JDBC 数据源的链条是：

```text
公共 DataJdbcApplication → 提供通用 JDBC 组件
Spring 数据源配置 → 提供唯一候选或 @Primary DataSource
数据库模块自动配置 → 注册自己的工厂
同一模块的 Session Bean 方法 → 直接调用自己的工厂 create，并设置模块会话组
YulinlinCoreAutoConfig → 收集实际 EntitySession 对象并注册到 RouteSession
```

例如 PostgresqlAutoConfiguration.postgresqlSession 直接调用 postgresqlSessionFactory.create(dataSource, "postgresql")，实际对象由工厂中的 PostgresqlSession 构造函数创建。不再有公共工厂 Map 筛选或 URL 校验，也不存在公共 jdbcSession 创建方法。

多个 DataSource 没有唯一主候选时，不会任意选择，应用必须显式创建会话。普通 DataSource 不需要提供 URL 获取方法；创建 Session 时也不借连接。启动验收应检查注册的组、工厂和物理数据源绑定关系，不能仅凭会话初始化成功推断 SQL 已兼容。

未指定组时，RegisterSession 委托 LoadBalance.defaultGroup()，不再独立选择第一个组，也不使用静态 master。AbstractLoadBalance 只有一个注册组时直接使用该组；多个组时通过应用共享 LoadBalance Bean 的 setDefaultGroup 或 yulinlin.datasource.default-group 指定，未配置或组不存在时明确报错。primary 是普通组名；显式指定不存在的组不回退到其他库。当前会话上下文和显式请求组仍优先。

AbstractLoadBalance 注册、移除使用只读的 copy-on-write 快照；RandomLoadBalance 健康快照绑定对应的注册快照，旧 ping 结果不能恢复已移除的节点。读取保持标签过滤，0 权重不参与选择，负权重拒绝，long 权重总和及 [0,total) 随机区间避免溢出和偏置。非事务请求重新选节点，路由事务内保留节点绑定；资源关闭仍须协调在途请求，这不是租户热卸载或自动数据库故障转移方案。现有 heartbeat 空钩子保持不变，本次不新增后台调度。

用户自定义对应模块名的会话 Bean 会使默认创建退让；自定义名为 jdbcSession 的 Bean 仍作为兼容覆盖入口。默认配置本身不再提供 jdbcSession 名称，旧 @Qualifier("jdbcSession") 或按名查找需要迁移到 mysqlSession、postgresqlSession，或由用户显式声明兼容会话。不要自动给所有模块同一个别名，否则会重新引入抢注册问题。

### 附加数据源显式创建会话 Bean

下面是完整配置类，但以已经存在的 `mydbSessionFactory` 和 `mydbDataSource` Bean 为前提，不负责创建连接池：

```java
package demo.config;

import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class MydbSessionConfig {
    @Bean("reportingSession")
    public JdbcSession reportingSession(
            @Qualifier("mydbSessionFactory") JdbcSessionFactory factory,
            @Qualifier("mydbDataSource") DataSource dataSource) {
        return factory.create(dataSource, "reporting");
    }
}
```

此时 `mydbSessionFactory` 是工厂 Bean 名，`mydbDataSource` 是连接池 Bean 名，`reportingSession` 是附加会话 Bean 名，`reporting` 才是业务路由组。附加会话不使用默认 mydbSession 名称，以免让主会话创建退让。手动传给 create 的 DataSource 不会再次自动校验方言，应由配置者保证工厂与数据库一致。

业务可以使用以下方法体片段；DemoUser 是已经映射好表的实体，ModelSelectWrapper 来自可选的 common 模块：

```java
var users = ModelSelectWrapper.newInstance("reporting", DemoUser.class).selectList();
```

EntitySession Bean 会被 core 自动收集注册，Bean 创建方法中不需要反向注入 RouteSession，否则可能产生初始化循环。完整连接池配置示例见 [多数据源专题](../topics/15-datasources.md)。

### 内置资源与手动注册

本地资源不必把 DataSource 暴露成 Bean。SQLite 由 [SqliteAutoConfiguration](../../sqlite/src/main/java/com/yulinlin/jdbc/sqlite/SqliteAutoConfiguration.java) 创建资源持有对象 SqliteDatabase，再用其内部 DataSource 创建 sqliteSession，默认组为 sqlite，文件默认为 data/local.db。引入模块即启用，无需额外的模块开关。资源持有对象负责 close，不额外创建 sqliteTransactionManager。

附加库应使用独立组名，例如 mydb 与 reporting；不要把独立库无意间放进同一组。用户仍可主动注册 primary 组，并显式设置 default-group=primary 兼容旧业务。相同组里的多个 Session 会被当作负载均衡节点，而不是后创建者覆盖前者。

若 Session 不由 Spring 托管，调用方在基础设施初始化后自行注册：

```java
// 方法体片段：factory 已注入公共依赖，route 和 dataSource 已完成配置。
JdbcSession session = factory.create(dataSource, "reporting");
route.registerSession(session);
```

create 本身不注册路由。上述片段也不是生产级动态租户管理方案：当前注册表没有完整的并发增删和资源治理保证。

“Session 可独立使用”指配置好的 Session 可以直接执行已准备的 Request；不等于裸 new 后所有 ORM 组件都已就绪。脱离 Spring 时，公共组件、路由便利门面、执行器和资源销毁都需要调用方明确初始化和管理。

## 事务并发与生命周期边界

### 事务不是接口名称带来的能力

新 JDBC 模块优先继承现有事务实现。每次 CRUD 没有外层事务时会自动开启和结束本次事务；RouteSession 在路由事务中按实际访问加入 Session，并在结束时逐个提交或回滚。配置完整的 Session 不依赖 RouteSession 也可单独开启事务。

这是本地事务协调，不是 XA 或分布式原子事务。一个数据库的多个写连接也各有自己的事务；部分连接已经提交后，后续失败不能撤销已提交数据。严格单库原子性及事务内读己之写应使用单连接。

Spring 已为相同 DataSource 绑定事务连接时，公共 JDBC Session 在原线程复用该连接，物理提交、回滚和释放归 Spring 管理，不把连接发送到工作线程。新模块不应再创建一套并行事务来绕过它。

Spring @Transactional 已有公共接入，但传播、隔离、保存点、注解属性与异步上下文不应推定全部兼容。新增模块不必仅为接入框架事务而额外声明 PlatformTransactionManager；若业务还需要 Spring 原生事务能力，应单独设计并验证，见 [事务专题](../topics/20-transactions.md)。

### 并发写入是明确的能力声明

EntitySession 的 `supportsParallelWrites()` 默认返回 false。只有后端允许、连接或客户端隔离正确、事务上下文传递已实现时才启用。

公共 JDBC 默认最多 4 个工作连接，底层每次 executeBatch 默认 256 条。上层只在请求启用 .batch()、达到阈值且会话支持并发等条件满足时拆组；不支持时整批留在调用线程处理。SQLite 会话固定 1 个连接，不并发分组。

executeBatch 是发送批次，不是提交事务。不能为了凑满 256 条擅自 commit，改变业务回滚边界。并发失败后必须等待已提交任务结束，再处理回滚与连接释放；连接池不足时还可能等待或超时，不能宣称连接数等于性能倍数。

非 JDBC 后端不应直接复用默认 CompletableFuture 包装后就宣称支持事务内并发；默认工作线程并不会自动获得主线程的事务状态。需要实现对应资源和上下文的传递、隔离及统一收尾。

### 资源必须有明确所有者

模块设计应写清 DataSource、客户端、流和执行器分别由谁创建、何时关闭。外部共享资源不能因为一个 Session shutdown 就被误关；模块内部创建的资源应有确定的销毁入口，异常路径同样清理。

当前 JdbcSession.shutdown 主要记录关闭日志，不能把它当作通用连接池销毁实现。工厂 `create(DataSourceProperties, group)` 会新建 DataSource，但不自动使其成为容器管理的独立 Bean；使用者或资源持有对象仍需负责关闭。

ping 应反映后端可用性并使用受控超时。部分现有模块仅返回 true，这是旧实现的限制，不是新模块应照抄的规范。日志中避免输出账号密码、完整敏感 URL 和请求数据。

## 非 JDBC 接入补充

非 JDBC 模块可参考 MongoFactory、ElasticSearchFactory 的组织方式，但不要照搬它们的能力声明和资源初始化：

1. 继承 SimpParseManager 注册支持的节点；将查询和写入转换为后端请求，根解析结果封装为 ParseResult。
2. 实现 SessionFactory<Client>，统一创建并配置后端 Session；核对编码器、日志、group、缓存、过滤器和代理等实际使用的公共组件。
3. 通常继承 AbstractSession，实现 executeUpdate、executeSelect、executeGroup、executeCount、字段名转换策略以及 ping、shutdown。
4. 将查询结果转为 IDataBuffer，让现有解码流程处理模型；确有后端类型差异时补充 CoderManager。
5. 用配置文件创建 SDK 客户端和 Session Bean，声明自动配置 imports；不要硬编码服务地址或账号，也不要重复依赖 jdbc 只为注册到路由。
6. 单独核对事务、分页、JOIN、批量部分失败与并发语义；不支持的能力必须写入使用专题。

AbstractSession 自带事务状态计数，但不会替 SDK 开启真实事务。当前 MongoSession、ElasticsearchSession 的 startTransaction、commitTransaction、rollbackTransaction 是空实现，不能据此声称真实回滚。继承 EntitySession 或 TransactionSession 同样不能证明后端支持事务。

后端支持真实事务时，要把开始、嵌套计数、rollback-only、提交、回滚和清理映射到 SDK 资源。只支持无事务执行时，应明确设计无事务路径及错误边界；直接把 startTransaction 改成抛异常也会影响 AbstractSession 的自动 CRUD 事务包装，不能当作无需联动的修复。

## 交付与验收

### 必须交付

- 独立模块和驱动或 SDK 依赖，不依赖另一个数据库模块。
- ParseManager、确有差异的解析器、配置完整的 Session 创建路径。
- 工厂、自动配置、imports 声明、唯一明确的 Bean 名和路由组。
- 类型规则、事务承诺、并发能力和资源销毁说明。
- 使用专题，包括最小依赖和配置、单库 CRUD、多数据源选组及限制。
- 验收案例与真实数据库测试的显式启用方式。

### 按需要实现

- JdbcSession 子类、特殊编码器、文件资源持有对象。
- 扫描建表或结构初始化。它不是注册 Session 的默认行为，尤其不能自动修改已有业务表。
- 真实后端事务、单写者限制、驱动批量优化等专用策略。

### 不应重复实现

- 没有差异的 CRUD、条件、排序和聚合解析器。
- 空继承兼容类、数据库模块之间的转发类或依赖。
- 已经通用的 JDBC 连接、事务、批处理流程。
- 自动收集会话 Bean 之外的重复路由注册。

### 验收案例清单

以下是待执行的验证范围，不是本次验证结果：

- 仅引入新模块即可工作；类路径上没有其他数据库模块与驱动。
- 注册表包含公共节点，差异键被正确替换，独立解析不借连接；两个数据库同时解析不串方言或别名。
- 各模块直接用自己的工厂创建命名组会话，不读取 URL，不借连接，不要求池暴露配置获取方法；无数据源或多个主候选时不任意创建，公共 jdbc 不创建默认会话。
- 多模块显式绑定正确的 DataSource 与工厂，默认组不同不代表物理源已隔离；自定义模块默认名或 jdbcSession 能让默认创建退让；内置资源不重复创建。
- 单组省略选择参数仍可执行，多组按 default-group 或请求显式组选择；primary 不自动优先，未注册的默认组和显式组都不悄悄回退。
- 默认选择也使用健康快照与 cluster，边界权重、0 权重、负权重、long 总和、注册/移除快照以及 ping 更新期间的注册变更均有案例。
- 路由事务保留已选节点，非事务请求能更新选择，默认组运行时调整不会改写其他负载均衡实例的配置。
- CRUD、分页、排序、JOIN、聚合、日期、JSON 与特殊类型往返；不支持的语法明确拒绝。
- 直接 Session 执行、路由协调、Spring 绑定连接、嵌套 rollback-only 及提交失败清理。
- 单连接与多连接批量、尾批、SUCCESS_NO_INFO、失败批次、任务提交失败和连接池等待；执行批次不提前 commit。
- 若接入 ORM 完整路径，覆盖缓存、过滤器、JoinQuery、JoinLazy、JoinSync，不能只验证 SQL 字符串。
- 客户端关闭、连接和流释放、执行器所有权、线程上下文清理及日志脱敏。

验收分为纯解析、模拟驱动或客户端、真实后端集成三个层次。真实库必须显式指定测试配置并使用隔离资源，禁止默认读取生产连接或修改业务表。性能结论需要单独测量，不能由“复用 JDBC”或“支持并发”推导。

是否执行测试、编译和打包由当前任务授权决定，验收清单本身不是执行许可。报告分别标明源码核对、已执行案例和未验证范围。

## 文档维护入口

本文是内部维护源，不并入面向业务和外部 AI 的单文件接入指南。新增数据库的用户用法放入 doc/topics，再更新 doc/README.md、llms.txt 和专题导出清单；不要把内部实现细节替代用户配置示例。

相关入口：

- [PostgreSQL 接入与实际限制](../topics/17-postgresql.md)
- [SQLite 的内置资源与单写者配置](../topics/16-sqlite.md)
- [多数据源工厂和组名配置](../topics/15-datasources.md)
- [事务协调与 Spring 边界](../topics/20-transactions.md)
- [EntitySession](../../core/src/main/java/com/yulinlin/data/core/session/EntitySession.java) 与 [IParse](../../core/src/main/java/com/yulinlin/data/core/parse/IParse.java)
- [公共 JDBC 自动配置](../../jdbc/src/main/java/com/yulinlin/jdbc/DataJdbcApplication.java) 与 [core 会话注册](../../core/src/main/java/com/yulinlin/data/core/YulinlinCoreAutoConfig.java)
