# JDBC 模块接入

[文档首页](../README.md) / [内部维护](README.md)

解析器、Session、自动配置、注册和时间编解码。

## 本页导航

- [JDBC 接入示例](#jdbc-接入示例)
- [解析器注册](#解析器注册)
- [Session 的差异](#session-的差异)
- [data-sync 扩展](#data-sync-扩展)
- [自动配置完整模板](#自动配置完整模板)
- [创建与注册](#创建与注册)
- [编解码](#编解码)

## JDBC 接入示例

### 解析器注册

执行计划通过 QueryRequest / Model Wrapper 的 `explain()` 和 `explainCount()` 进入 RouteSession，复用查询前过滤、路由与事务资源。EntitySession 默认拒绝诊断；H2Session、MysqlSession、PostgresqlSession 显式启用普通 `EXPLAIN`。JdbcSession 在 `parseQueryNode` 生成实际查询 SQL 后，只给新的 SqlNode 加前缀并复制已编码参数，不重新插值参数，不修改原节点。count 仍使用现有 CountWrapper 解析器：普通 SelectWrapper 直接生成 `select count(1) as total from ...`，复用 FROM/JOIN/WHERE 解析；GroupWrapper 保留子查询以统计分组数和应用 HAVING。两种计数都忽略列表的排序与分页，不改变原 Wrapper、参数绑定顺序和逻辑缓存键。两种诊断入口共用同一执行实现。

JDBC 查询和诊断共用 `withQueryConnection` 管理连接；诊断通过 `executeOperation` 产生 `ParseType.explain` 的日志和事件，保留失败传播与资源释放。结果直接按 JDBC 列标签和 `getObject` 读取到有序 Map，保留 NULL，不经过实体解码、代理、业务 after 过滤器或缓存。原生 CommandNode、自定义 Wrapper 和写节点不会被这个便利入口自动包装；原生 SQL 仍可显式写 EXPLAIN。使用说明见[查看执行计划](../orm/query.md#查看执行计划)。

显式索引提示由 core 的不可变 `QueryIndexHint` 绑定表名、别名和去重后的索引名，保存在 QueryRequest 上；BaseModelSelectWrapper / BaseModelGroupWrapper 只提供链式委托。`copy` 与过滤器执行选项继承同步传递，目标改变时拒绝。AbstractSession 的 query 解析入口复用 `createParamsContext`，因此 JDBC 仍按原流程编码 root 参数；非空提示调用 IParseManager 的三参数 parse，默认忽略提示并委托旧二参数解析，无提示查询不变。自定义 Session 如覆盖查询解析，应同时审查 `parseQueryNode` / `createParamsContext`，保留原有安全过滤；后端可以按需适配这项优化。

SqlParseManager 通过 NameParse.useIndex 方言能力生成片段；默认返回空字符串时直接按原查询解析。非空片段使用独立 SqlParamsContext 绑定到目标 Store 的对象身份。StoreParse 在表名/别名后追加片段，不替换已生成 SQL，也不影响其他表；方言提供片段但未经过目标表解析时仍明确报错。内置实现采用仅 H2 生效的策略；MySQL、PostgreSQL 等其他 JDBC 方言和非 JDBC 后端忽略合法提示，不自动映射 MySQL 原生索引提示或 PG 插件。非法名称和请求目标改变的校验独立于后端能力，仍然报错。count 解析同一目标 Store：普通 count 的提示直接跟随物理表名/别名，分组 count 的提示位于子查询内的物理表后，不会追加到临时表别名后。缓存键额外纳入不可变提示（包括忽略提示的后端）；无提示键不变、count 原有忽略分页/排序的规则不变。使用及边界见 [H2 索引提示](../datasources/h2.md#单次查询索引提示)。

SqlParseManager 复用 CRUD/条件/JOIN/聚合；它不是天然兼容所有数据库的标准 SQL 层。默认分页带 MySQL 风格，日期/区间需要模块自己注册。JSON 仅保留整字段编解码，不再通过字段名解析 JSON 路径；JSON 操作使用目标数据库的参数化原生 SQL。

当前 PostgreSQL 的注册方式：

```java
package com.yulinlin.jdbc.postgresql;

import com.yulinlin.jdbc.postgresql.parse.NameParse;
import com.yulinlin.jdbc.postgresql.parse.PageParse;
import com.yulinlin.jdbc.postgresql.parse.group.DateParse;
import com.yulinlin.jdbc.postgresql.parse.group.IntervalParse;
import com.yulinlin.jdbc.postgresql.parse.base.MatchParse;
import com.yulinlin.jdbc.postgresql.parse.select.AsFieldParse;
import com.yulinlin.jdbc.postgresql.parse.statement.SqlSelectWrapperParse;
import com.yulinlin.jdbc.sql.SqlParseManager;

public class PostgresqlParseManager extends SqlParseManager {
    public PostgresqlParseManager(PostgresqlFullTextOptions options) {
        register(new MatchParse(options));
        register(new AsFieldParse(options));
        register(new SqlSelectWrapperParse());
    }

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

SimpParseManager 以 IParse.getNodeClass 注册，相同键后注册覆盖前者；普通分派按实际 Class 查找，不自动搜父类。NameParse 的元节点入口需沿用公共契约。

构造函数会调用可重写的 init，此时子类字段尚未初始化，不能依赖构造后才赋值的成员。注册表初始化后不再动态修改；同数据库语法可以共享，跨数据库用不同注册表。

解析器不保存当前 root/参数/别名/连接。SqlParamsContext 保存本次管理器、NameParse 和 SELECT 表达式，不引用 Session，不把临时别名写入共享模型映射。

公共 `MatchParse` 是包含式 LIKE 回退；PostgreSQL 覆盖为 `to_tsvector @@ websearch_to_tsquery`，Elasticsearch 覆盖为原生 MatchQuery。`highlight` 是 AsField 上的选择标记，普通 JDBC AsFieldParse 忽略它；PostgreSQL 同时覆盖 SelectWrapperParse 与 AsFieldParse：先解析 WHERE，将当前请求的全文 query 表达式保存在 SqlParamsContext 的 request-local attribute，再解析 SELECT 并生成 ts_headline。不要把这些表达式放进 ParseManager 单例字段，否则并发请求会串数据。

全文索引 DDL 与查询必须使用同一 index-config 和完全相同的 tsvector 表达式。`@JoinField(fullText=true)` 只由 PostgreSQL SchemaManager 消费，其他 SchemaManager 不应误建普通索引；pg_jieba 是服务器扩展，框架只检查配置是否存在，不负责安装共享库或执行提权操作。

### Session 的差异

PostgresqlSession 构造时设置 PostgresqlParseManager；InputStream 绑定 setBinaryStream，BOOLEAN/BIT 用 getBoolean + wasNull，其他流程继承 JdbcSession。

常用可重写签名：

```java
protected void bindParameter(PreparedStatement statement, int index, Object value)
        throws SQLException;
protected Object readColumn(ResultSet rows, String label, int jdbcType)
        throws SQLException;
protected void beforeExecute(BaseRequest<?> request);
```

这是签名说明，不是完整类。扩展需检查实际批处理和查询入口是否调用这些方法，不能只证明单独工具方法能生成字符串。

beforeExecute 声明在 AbstractSession，实际查询、count、group 和非空写入执行时调用；写入由 RouteSession 包装事务，普通查询不因此自动开启事务。纯 SQL 解析和查询缓存命中不调用。它适合请求级执行钩子，不应用于建表：否则缓存命中、原始 SQL、DTO 和事务边界会让 Schema 行为难以预测。默认实现为空，不改变其他后端。

core 中的 SchemaEntityScanner 在自动配置创建 Session Bean 时递归扫描各模块 `schema-packages`。`autoSchema` 默认 false；扫描器只保留显式 `autoSchema=true` 的具体普通单表结构拥有者，并按不区分大小写的物理结构名检测冲突。两个完整实体拥有同一结构时立即阻止启动；DTO/统计投影保持默认值即可。CRUD 路径不再根据 BaseRequest.fromClass 执行 DDL。

SqliteSession、H2Session、MysqlSession 和 PostgresqlSession 分别接收扫描结果并在初始化阶段处理结构。H2Database 同时保留池化业务 DataSource 和未暴露为 Bean 的直连 schema DataSource；MySQL/PostgreSQL 使用主 DataSource 的独立启动连接；SQLite 使用内部单连接库。初始化完成后，业务事务只负责业务 SQL。SQLite 会话仍禁止多连接写入拆组。

EntitySession 统一声明 `createTableSql(Class<?>)` 与 `initializeSchema(Collection<Class<?>>)`。AbstractSession 为不管理结构的后端提供空实现；MySQL/PostgreSQL/H2/SQLite Session 返回并执行 DDL，ElasticsearchSession 返回索引 Mapping 预览并创建或校验索引。JDBC `initializeSchema` 持有连接与 Statement，通过 JdbcSession 的受保护执行函数逐条打印 `[group][schema]` 后执行。方言 SchemaManager 只描述、校验结构并请求 Session 执行 SQL，不再自行创建 Statement，因此自动初始化和外部预览使用同一组 DDL 生成函数。

通用配置分两层：core 的 `EntitySessionProperties` 提供 `log`、`mapUnderscoreToCamelCase`、`schemaMode`、`schemaPackages` 和公共 `highlight`（标签、片段数、片段分隔符），可被 JDBC、Elasticsearch、MongoDB 等后端继承；jdbc 的 `JdbcSessionProperties` 再增加 `parallelConnections`、`executeBatchSize` 与默认关闭的 `alterColumnType`。数据库专属的分词器、摘要长度或 highlighter 类型留在具体模块。具体模块配置类才声明 `@ConfigurationProperties`，公共父类不绑定全局前缀。旧 `JdbcProperties` 仅是弃用兼容桥，新扩展不要继续引用。

JoinIndex 是 core 中的可重复类注解，只有 fields 和 unique。jdbc.schema.EntityIndexResolver 统一把结构拥有者上的 Java 属性解析成持久化列，生成 `idx_<table>_<columns>` 或 `uk_<table>_<columns>`；超长名称使用 63 字符上限和 SHA-256 前 8 位稳定后缀。MySQL/PostgreSQL/H2/SQLite SchemaManager 只负责各自的元数据读取、DDL 和兼容性校验，不复制命名规则。各模块均保留未声明的额外索引，不自动删除或改名。

JoinField 的 `textType`、`textLength` 和 `description` 先由 `jdbc.schema.TextColumnResolver` 做跨数据库校验，再由各 SchemaManager 转成方言 DDL。字符串主键缺省长度统一引用 `TextColumnResolver.DEFAULT_PRIMARY_KEY_LENGTH`（32），显式正长度按注解生成，不设置框架专属的 128 上限；仍遵守各数据库列和索引限制。大文本禁止作为主键或自动索引。MySQL/PostgreSQL/H2 对显式 VARCHAR 长度采用容量校验：实际长度小于要求默认报错，大于等于要求直接兼容；主键和普通文本字段规则一致。内部 `validateLength` 表示是否校验显式容量，不表示必须等长。未显式指定时不校验已有长度，不自动缩短。SQLite 只校验类型 affinity，不能把 `VARCHAR(n)` 当成实际长度约束。description 会在 MySQL/PostgreSQL/H2 创建新表或补充新列时落库，但不会修改已有列的注释。

`alter-column-type` 仅属于 JDBC 模块，在各 Session.configure 捕获后传入 SchemaManager。仅 `SchemaMode.CREATE` 且开关为 true 才按现有 StorageType 分类的差异或显式容量不足生成改列语句；旧 ensureTable 重载保留，默认 false。不扫描行数据，不自动调整主键，不删默认值/约束促成转换；先为当前表全部候选列检查并生成语句，再通过 Session 的 SchemaSqlExecutor 执行并重新校验元数据。不同表之间不提供原子迁移。Jdbc 的 SchemaColumnAlter 共用自增/生成列、外键检查与中文 SQL 异常包装，各模块仍拥有 DDL 方言。MySQL 保留普通列属性，对特殊属性保守拒绝，要求严格模式；PostgreSQL 使用 USING CAST，H2 使用 SET DATA TYPE。SQLite 遇到类型变更明确拒绝，不自动重建表。元数据的表名/schema pattern 必须过滤到实际目标表，避免下划线匹配其他表。人工确认、备份、锁表和失败恢复说明见[显式允许修改列类型](../datasources/schema.md#显式允许修改列类型)。

方言维护参考：[MySQL MODIFY 属性保留说明](https://bugs.mysql.com/bug.php?id=106791)、[PostgreSQL ALTER TABLE](https://www.postgresql.org/docs/17/sql-altertable.html)、[H2 ALTER TABLE](https://h2database.github.io/html/commands.html#alter_table_alter_column)。新增方言须核对默认值、约束和 DDL 提交行为，不能仅替换 SQL 关键字。

Schema 诊断复用 `jdbc.schema.SchemaErrors`：它只格式化中文错误、原因与处理方式，不判定兼容性、不生成或执行 DDL。各数据库仍在自己的 SchemaManager 内判断类型、长度、主键和索引差异，并传入本次调用的 `SchemaMode`、`alterColumnType`；不能把配置放入静态全局变量。类型展示使用归一化存储分类，详细原始定义以数据库元数据为准。

`SchemaErrors.executor` 装饰 Session 提供的 SchemaSqlExecutor，为失败语句补充完整 SQL 和恢复建议；保留 SQLException 的 SQLState、错误码和 cause，避免破坏重复建表/建索引竞争的错误码判断。Session 仍负责日志、连接和实际执行。主键/索引冲突不应提示“开启改列即可修复”，SQLite 应明确手动迁移；DDL 失败必须提醒可能已有部分修改生效。扫描器、字段/索引注解错误同样给出中文修正建议。

诊断回归源码位于 `admin/src/test/java/com/yulinlin/regression/jdbc/SchemaErrorsTest.java`；不声明自动建表实体、不连接数据库。本次仅新增源码，未编译、未执行。用户排查示例见[表结构错误排查](../datasources/schema.md#表结构错误排查)。

### data-sync 扩展

可选模块 `data-sync` 依赖公共 jdbc（传递依赖 core），不依赖 mysql/postgresql 实现模块或数据库驱动。自动配置在 YulinlinCoreAutoConfig 前声明过滤器 Bean；SyncWriter 和 DataSyncClient 通过 ObjectProvider 延迟获取 RouteSession，避免 RouteSession → FilterManager → SyncManager → RouteSession 循环依赖。SmartInitializingSingleton 在 Session 注册完成后调用 SyncManager.start(groups)，仅校验 group 并启动增量消费；enabled 默认 false，空 rules 可仅启用人工全量 API。

当前约定是轻量同步：全量由人工调用 DataSyncClient，并由业务协调停写和排空。规则中不再包含 full 属性，启动全量执行器、实体扫描器及自动配置注册均已移除。不要重新把人工全量塞回启动初始化链；entities 通配符仅用于增量 fromClass 匹配。

扩展接口：

- `SyncTaskStore`：原子容量检查、FIFO peek、成功后 acknowledge、待处理计数。内置 MemorySyncTaskStore 按规则计节点数，保留失败头任务。替换实现要显式设计持久化快照格式和恢复，不可直接把保存对象当作源事务 Outbox；当前没有跨进程协调。
- `SyncWriter`：内置实现调用 RouteSession.replayWrite。替换后须保持“正常返回意味着目标事务完成”，不自动重试不确定的提交。

SyncSnapshots 对框架 INode 图进行身份跟踪复制，以 Spring 自带 Objenesis 创建节点壳，再复制实例字段，避免要求节点有无参构造器；只允许框架 node/wrapper 类型。普通值复用 ReflectionUtil.deepClone。原始 Expression 与第三方自定义节点明确拒绝；CommandNode 不参与自动同步。不要把业务 root、Spring Bean、数据库连接或实体代理放进快照。每次消费从保存快照再次复制请求，避免失败执行污染下一次人工重放。

全量实现分工：DataSyncClient 校验入口并选择 master Session；FullSyncMapping 校验完整单表映射/实际主键；JdbcFullSyncJob 负责主键分页、目标按主键新增/覆盖、目标本地检查点和会话锁。JdbcSession.withMaintenanceConnection 只提供独立维护连接生命周期，readMaintenanceValue/bindMaintenanceValue 复用数据库值适配，不为普通 CRUD 开放第二个事务入口。目标业务行与进度必须同连接同批提交，失败状态更新不能覆盖旧内存游标；仅确认提交后计数和清目标缓存。

SyncManager.ManualGuard 按目标 group 保护本进程所有相关增量规则，不仅按本次实体筛选；进入前要求空闲、队列为空且未暂停，否则直接拒绝，不自动等待排空。退出时释放 manual 标记并尝试恢复调度，原有错误/冲突的 paused 状态不会自动解除；普通全量失败释放保护，也不代表全量已经完成。不要在 finally 无条件调用 resumeAfterRepair。

保护期间检查 capture/enqueue 处的源写入/提交；不替代全局停写，不能检测其他进程或未匹配规则的原生操作。全量任务处理过程中检测到冲突时保存 RESTART_REQUIRED，不能继续使用旧游标。业务负责先停新写入、排空 EntityCounter/普通写队列、结束在途事务，再排空增量；失败续传期间也保持停写。数据库会话锁跨批次提交保留，释放失败应中止物理连接，避免持锁连接回到池中。维护连接回滚失败时不能用 setAutoCommit(true) 意外提交剩余数据。

回归源码仅位于 admin 的 `src/test/java/com/yulinlin/regression/sync` 和 `src/test/java/com/yulinlin/sync`，不参与业务扫描。覆盖增量快照/队列、全量映射与游标边界、维护资源清理和全量期间源提交冲突等。本轮未编译、未执行；真实 MySQL/PostgreSQL 双端事务、进程中断续传及驱动组合仍需集成验证。使用配置及人工恢复见[数据源同步](../write-sync/incremental.md#数据源增量同步)。

### 自动配置完整模板

mydb、MydbParseManager、MydbCoderManager 和驱动前缀是待实现/替换的占位名称；MydbCoderManager 应继承 JdbcCoderManager，在内部注册本模块的时间编码器，结构参考下方“编解码”，不引入其他数据库模块的编码器：

```java
package com.yulinlin.jdbc.mydb;

import com.yulinlin.jdbc.DataJdbcApplication;
import com.yulinlin.jdbc.JdbcSessionProperties;
import com.yulinlin.jdbc.mydb.coder.MydbCoderManager;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.JdbcSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import javax.sql.DataSource;

@ConfigurationProperties("yulinlin.mydb")
class MydbProperties extends JdbcSessionProperties {
    // Add only settings unique to this database.
}

@AutoConfiguration(after = {DataSourceAutoConfiguration.class, DataJdbcApplication.class})
@EnableConfigurationProperties(MydbProperties.class)
public class MydbAutoConfiguration {
    @Bean("mydbSessionFactory")
    @ConditionalOnMissingBean(name = "mydbSessionFactory")
    public JdbcSessionFactory mydbSessionFactory(MydbProperties properties) {
        // 直接使用本模块的编解码管理器，不传编码器列表。
        return new JdbcSessionFactory(new MydbParseManager(), "jdbc:mydb:", properties,
                new MydbCoderManager());
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

需要专用 Session 时使用 `new JdbcSessionFactory("jdbc:mydb:", MydbSession::new, properties, new MydbCoderManager())`，由 Session 构造安装解析器，工厂直接安装编解码管理器。公共的 log、log-params、log-sql-pretty、max-param-length、slow-sql-threshold、map-underscore-to-camel-case、schema-mode、schema-packages、parallel-connections 与 execute-batch-size 由 MydbProperties 继承并绑定在 yulinlin.mydb 下；同类某个 Session 需要例外配置时使用三参数 create 重载。前缀和 supportsJdbcUrl 仅保留兼容信息，不做自动类型校验。

资源声明文件 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 写入：

```text
com.yulinlin.jdbc.mydb.MydbAutoConfiguration
```

仅添加 AutoConfiguration 类不能保证被发现。新模块声明自己在 Spring 数据源及公共 JDBC 配置之后，不让公共 jdbc 按模块名维护选择工厂的清单。

### 创建与注册

公共 DataJdbcApplication 提供 Spring 事务注解适配器，不再注册日志 Bean 或公共 jdbcCoderManager Bean。日志由 Session 直接调用；工厂初始化可选执行监听器。各数据库模块注册自己的 Properties 和工厂，工厂直接持有所属模块的 CoderManager 并创建会话；core 收集 EntitySession Bean 注册到 RouteSession。

容器管理的工厂在 create 返回前完成 Session 的依赖初始化；create 本身不注册路由，也不保证手动 new 工厂的注入依赖齐全。非 Bean 会话可以通过已注入的工厂创建，再显式 route.registerSession(session)。注册阶段只登记负载均衡节点，不再注入缓存或补齐其他组件，不在 Session Bean 创建时反向依赖 route。

`EntitySession` 只暴露执行与资源协议，不声明 `setQueryCache`。继承 AbstractSession 的实现由工厂调用具体类的初始化方法；CacheCoreAutoConfiguration 收集 QueryCache Bean 并构造 CacheRegistry。工厂和 RouteSession 统一注入 registry.defaultCache()，分别负责查询缓存与写后失效，不在注册时互相覆盖。无实现且未指定默认值使用 NoOpQueryCache，单实现自动选中；多个实现通过 yulinlin.cache.default 选择，名称重复或显式选择不存在的名称时报错。新 Provider 应提供稳定且唯一的 providerName；三个现有模块固定为 caffeine/ehcache/redis，不增加 group 属性。

CacheRegistry 不是 QueryCache，不能再作为 Provider 注册，否则会重复计数或形成循环依赖。CacheClient 只负责 getCache()/getCache(name) 选择及只读清单，RoutingCacheClient 为每个实现复用一个 DefaultCache（实现 Cache），由 Cache 提供业务读写、泛型回源、批量和清理。不提供 ORM 表/实体手动清理入口，不引入 ORM 路由/注解桥接。批量默认逐条调用，不保证原子性；不新增调度层。ORM 不增加缓存名参数，也不广播失效。无 Provider 时默认返回空 Cache，ORM 保持无缓存运行。旧 QueryCaches.single 仅用于无显式默认配置的手动装配兼容，Spring 工厂不再使用。Provider 的资源仍由原 Bean 销毁方法关闭，注册表/客户端不能重复关闭。缓存选择、批量/泛型操作与清理隔离的回归源码位于 admin/src/test/java/com/yulinlin/regression/cache/CacheRegistryTest.java；按要求未编译、未运行，未新增 Spring YAML 绑定测试。

多个模块都可能注入同一 @Primary DataSource。group 名不同不代表物理源已隔离；业务应显式绑定工厂、池和组，完整例子在 [数据源接入](../datasources/routing.md#多数据源注册)。

### 编解码

沿用现有职责：`SqlParseManager` 解析 SQL；`ICoderManager` 管理值编解码；`IDataBuffer` 保存结果并通过绑定的管理器恢复字段类型。不让 ParseManager 兼任 CoderManager。

1. `JdbcCoderManager` 继承 MapCoderManager，提供 core 的枚举、数字、默认时间文本，以及 JDBC 集合/Map/对象的 JSON 编解码。
2. 各数据库定义自己的 `CoderManager extends JdbcCoderManager`，在 `init()` 先调用 `super.init()`，再逐一 `registerCoder(...)` 本模块的时间编码器。同类型的默认编码器会被明确替换，不依赖 HashSet 的遍历顺序。
3. 具体时间编码器仍在各模块 `*TemporalCoders` 内实现；`TemporalConversions` 只提供通用值转换，`AbstractTemporalCoder` 只复用类型声明、空值处理和 Field/Class 两个 decode 入口。公共工具不决定数据库编码输出类型、文本存储格式或精度。
4. 工厂接收一个 `ICoderManager`，创建 Session 时直接 `setCoderManager(coderManager)`。不再传列表或创建覆盖层，也不自动注入公共 CoderManager Bean。

| 模块 | 管理器 | 具体时间编码器 | Date / DateTime / LocalDateTime 编码结果 |
| --- | --- | --- | --- |
| MySQL | [MysqlCoderManager](../../mysql/src/main/java/com/yulinlin/jdbc/mysql/coder/MysqlCoderManager.java) | [MysqlTemporalCoders](../../mysql/src/main/java/com/yulinlin/jdbc/mysql/coder/MysqlTemporalCoders.java) | `LocalDateTime`，绑定 DATETIME 语义 |
| PostgreSQL | [PostgresqlCoderManager](../../postgresql/src/main/java/com/yulinlin/jdbc/postgresql/coder/PostgresqlCoderManager.java) | [PostgresqlTemporalCoders](../../postgresql/src/main/java/com/yulinlin/jdbc/postgresql/coder/PostgresqlTemporalCoders.java) | `LocalDateTime`，对应无时区 TIMESTAMP |
| H2 | [H2CoderManager](../../h2/src/main/java/com/yulinlin/jdbc/h2/coder/H2CoderManager.java) | [H2TemporalCoders](../../h2/src/main/java/com/yulinlin/jdbc/h2/coder/H2TemporalCoders.java) | `LocalDateTime`，对应无时区 TIMESTAMP |
| SQLite | [SqliteCoderManager](../../sqlite/src/main/java/com/yulinlin/jdbc/sqlite/coder/SqliteCoderManager.java) | [SqliteTemporalCoders](../../sqlite/src/main/java/com/yulinlin/jdbc/sqlite/coder/SqliteTemporalCoders.java) | `yyyy-MM-dd HH:mm:ss` 字符串 |

H2 的管理器内部完成注册：

```java
public class H2CoderManager extends JdbcCoderManager {
    @Override
    protected void init() {
        super.init();
        registerCoder(new H2TemporalCoders.DateCoder());
        registerCoder(new H2TemporalCoders.DateTimeCoder());
        registerCoder(new H2TemporalCoders.LocalDateCoder());
        registerCoder(new H2TemporalCoders.LocalDateTimeCoder());
        registerCoder(new H2TemporalCoders.LocalTimeCoder());
    }
}
```

工厂只使用管理器：

```java
@Bean("h2SessionFactory")
public JdbcSessionFactory h2SessionFactory(H2Properties properties) {
    return new JdbcSessionFactory("jdbc:h2:", H2Session::new, properties,
            new H2CoderManager());
}
```

以上是模块内置自动配置，普通业务项目无需重复声明。每个内置工厂分别创建所属数据库的管理器；同一工厂创建的 Session 可以复用该管理器，但每次查询/写入的 IDataBuffer 独立创建，不共享请求数据。编码器必须无状态、线程安全；注册应在初始化完成前进行，运行期间不要修改管理器。如果某个 Session 确实需要不同规则，使用单独的管理器实例和工厂，不向正在复用的管理器动态注册。

查询时，JdbcSession 通过 `getCoderManager().createDecoderBuffer()` 创建 Buffer，ResultSet 的列值先存入其中；随后 `decodeObject(buffer, entityClass)` 按目标字段类型调用具体解码器。写入和条件参数则使用同一管理器的 `createEncoderBuffer()`，因此双向规则一致，不需要业务手动调用时间编码器。

不带管理器参数的旧工厂构造函数仍保留，会创建工厂私有的普通 `JdbcCoderManager`，只使用公共默认规则，**不会根据 URL 猜测数据库时间编码器**。自定义数据库工厂应传入本模块管理器；原来通过公共 jdbcCoderManager Bean 添加的数据库专属注册，应迁入对应管理器子类。此前的列表参数、`all()` 注册入口和 `withCoders(...)` 覆盖层已移除。

某个数据库需要调整时间规则时，只修改本模块具体编码器；例如修改 `H2TemporalCoders.DateTimeCoder` 不会改变 MySQL/PostgreSQL/SQLite 的编码器。额外自定义类型可以在对应 CoderManager 子类的 `init()` 调用 `super.init()` 后注册。注意父类构造时就会调用 init，不要在其中读取尚未初始化的子类字段；依赖构造参数的编码器可在子类构造函数的 super 调用完成后注册。公共转换工具修改仍需考虑所有调用者，不应加入数据库专属分支。

MySQL/PostgreSQL/H2 当前各自将 `Date` / `DateTime` / `LocalDateTime` 编码为 `LocalDateTime`。其中 Date 按 JVM 默认时区提取本地日期时间，与读取无时区列时的还原规则对称。公共 JdbcSession 使用 `setObject`，不要再包成 `Timestamp` 或统一指定 `Types.TIMESTAMP`；MySQL 驱动需要据 Java 类型选择本地 DATETIME 语义。三个模块的 LocalDate / LocalTime 也统一为原值直接绑定，避免提前引入日期时区转换或丢失时间小数秒，最终精度由驱动和目标列决定。解码继续支持旧 java.sql.Date / Time、字符串与 NULL，自定义扩展不能再强制把编码结果转为旧 JDBC 日期/时间类型。

SQLite 保留自身固定文本存储适配：日期时间 `yyyy-MM-dd HH:mm:ss`、日期 `yyyy-MM-dd`、时间 `HH:mm:ss`，仍为秒级精度，不自动转换历史字段或修改格式；格式器保留在 SQLite 模块。`SqliteSession.bindValue` 为直接传入的时间对象调用 `SqliteTemporalCoders.toTextValue`，复用本模块的编码实现。公共 JdbcCoderManager 的默认时间文本规则和非 JDBC 后端不变，Oracle 当前没有独立实现，不新增原生日期绑定承诺。其他模块直接传给 JDBC 的原始参数不会自动经过编码器；使用框架的字段或条件参数编码入口才能应用上述规则。

统一的 Java 参数契约如下，字段写入、更新与条件参数均适用：

| Java 类型 | MySQL / H2 / PostgreSQL 编码类型 | SQLite 编码类型 |
| --- | --- | --- |
| Date / DateTime / LocalDateTime | LocalDateTime | 固定日期时间文本，精确到秒 |
| LocalDate | LocalDate | `yyyy-MM-dd` 文本 |
| LocalTime | LocalTime，编码阶段保留小数秒 | `HH:mm:ss` 文本 |

共享转换支持本地时间对象、旧驱动 Timestamp，以及空格/T 分隔、带 0～9 位小数秒的日期时间文本；Field/Class 两个 decode 入口一致，也允许直接传入 null。本地时间文本直接解析为 LocalDateTime，不绕经 JVM 默认时区；只有还原为 Date 时才按 JVM 默认时区转成时间点。多实例应统一 JVM 时区，不带时区列不能保存夏令时回拨的原始偏移。core 的 DateTimeCoder、非 JDBC 后端、JSON 日期展示和自动 Schema 类型不变，也不自动迁移历史偏移数据。手工建成 MySQL TIMESTAMP 或其他带时区列仍受数据库时区规则影响，不在“数字不变”的保证范围内。发布时 core、jdbc 与所用数据库模块一起更新，因为 JDBC 注册依赖 core 新增的 replaceCoder；特殊类型要把编码值、驱动参数类型、目标列和读回格式一起验证。

MySQL 的 Date/DateTime 一致性由 [MysqlDateCoderTest](../../mysql/src/test/java/com/yulinlin/jdbc/mysql/coder/MysqlDateCoderTest.java) 覆盖，包含 GMT 连接时区与 `preserveInstants` 开关组合。修改编码器时应保留这些回归断言，确保实体字段和条件参数都输出 LocalDateTime；使用示例及 DATETIME/TIMESTAMP 边界见 [MySQL 日期时间编码](../datasources/mysql.md#日期时间编码)。

驱动接口依据：[MySQL 时间语义](https://dev.mysql.com/doc/connector-j/en/connector-j-time-instants.html)、[PostgreSQL JDBC 4.2 时间映射](https://jdbc.postgresql.org/documentation/query/#using-java-8-date-and-time-classes)、[H2 PreparedStatement 参数绑定](https://h2database.com/javadoc/org/h2/jdbc/JdbcPreparedStatement.html)。原生类型支持不代替业务项目的列类型、时区和往返验证。

MapCoderManager 原有 registerCoder 行为保留；新增受保护的 replaceCoder 供 JDBC 子类使用。JdbcCoderManager.registerCoder 会移除同 Java 类型的旧编码器、加入新编码器并清空已解析类型缓存，使初始化时的覆盖确定生效；这不是并发运行期热更新接口。

---

下一步：返回[内部维护主题目录](README.md)按需选择相关专题。
