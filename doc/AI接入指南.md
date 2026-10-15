# yulinlin-data AI 使用指南

---

> 派生文件，维护源为 doc 下七个使用专题。重新导出：./doc/build-ai-docs.ps1。

---

用途：给不能读取仓库的 AI 提供一个附件。包含接入、CRUD/统计/事务、关联代理、工具、接口安全、查询缓存，以及 Common 公共模型和项目最佳实践；内部扩展与完整性能报告不在此导出中，按需另提供第五或第七专题。

---

适用 JDK 25、Spring Boot 3.5.16、制品 3.0。2026-10-10 已运行本地库 JMH、JDK 25 全模块测试编译、lang/缓存专项测试和 Schema 定向测试；代码片段不等于所有数据库服务器均已集成验证，真实账号、表、路径和接口由业务提供。

---

<!-- source: doc/01-接入与数据源.md -->
## 项目接入与数据源

先选数据库模块，再配置数据源和会话组。本文完成 Spring Boot 接入、MySQL 与 PostgreSQL 配置、SQLite/H2 本地存储，以及多数据源注册和默认组选取。

阅读导航：[模块选择](#模块选择) · [MySQL](#mysql-接入) · [SQLite](#sqlite-接入) · [H2](#h2-接入) · [本地库选型](#sqliteh2-选型) · [PostgreSQL](#postgresql-接入) · [多数据源](#多数据源注册) · [慢数据库监控](#慢数据库监控) · [默认会话组](#默认会话组) · [配置速查](#配置速查)

适用版本：JDK 25、Spring Boot 3.5.16、制品 3.0。本文于 2026-10-06 核对仓库源码；本地库 JMH、JDK 25 编译和 Schema 定向测试已执行。这里是自定义 ORM，不是 MyBatis-Plus、JPA 或 Spring Data。

### 模块选择

| 使用场景 | 依赖 |
| --- | --- |
| Spring Boot 和 MySQL ORM | `com.yulinlin:starter:3.0` + `com.yulinlin:mysql:3.0` |
| Spring Boot 和 SQLite ORM | starter + sqlite |
| Spring Boot 和 H2 本地 ORM | starter + h2 |
| Spring Boot 和 PostgreSQL ORM | postgresql；需要便利模型门面时再加 common 或 starter |
| HTTP 工具 | core，starter 已传递引入 |
| 反射、深克隆、JSON | lang |
| JSON 接口请求/响应加密 | security；需要 Spring MVC，独立于 ORM 数据库模块 |
| 慢数据库操作与内置管理页面 | monitor；依赖公共 JDBC 执行入口和 Spring MVC |
| 缓存公共协议与 `CacheClient` | cache-core；由 core 和具体缓存 Provider 传递引入，业务通常不需要单独声明 |
| 可选查询缓存 | 单机内存用 cache-caffeine，本地磁盘持久化用 cache-ehcache，共享 Redis 用 cache-redis；三选一 |
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

yulinlin:
  mysql:
    log: true
    log-params: false
    max-param-length: 256
    slow-sql-threshold: 1s
    map-underscore-to-camel-case: true
    parallel-connections: 4
    execute-batch-size: 256
    schema-mode: CREATE
    schema-packages:
      - demo.domain
```

正常 Boot 自动配置链下无需框架启用注解。mysql 模块创建 `mysqlSessionFactory` 和 `mysqlSession`，默认 group 为 `mysql`。为兼容已有生产项目，`schema-mode` 默认 `NONE`；不需要框架管理结构时删除整个 `yulinlin.mysql` 段并由迁移脚本准备表。设为 `CREATE` 后，启动时递归扫描 `schema-packages`，创建缺失的表、普通列和声明索引，并校验已有结构；`VALIDATE` 只校验，缺失直接阻止启动。完整实体与 CRUD 见 [第二专题](02-CRUD与统计分析.md)。

#### JDBC 执行日志

MySQL、PostgreSQL、SQLite 和 H2 都继承同一组日志配置，前缀替换为对应模块名即可：

- `log` 默认 `false`，只控制真正访问数据库后的成功日志；缓存命中没有执行 SQL，因此不会伪造 SQL 日志。
- `log-params` 默认 `false`。开启后打印绑定参数，但长值最多保留 `max-param-length` 个字符；字节数组和输入流只记录类型、长度等摘要。
- `max-param-length` 默认 `256`，必须为正整数。
- `slow-sql-threshold` 默认 `0`（关闭）。配置为 `1s` 后，框架侧耗时达到 1 秒的执行以 `WARN` 输出，即使 `log=false` 也会记录。
- SQL 异常始终以单条 `ERROR` 输出，包含实际耗时并携带原始堆栈。批量写入按执行组打印一条摘要；摘要列出最多 5 种 SQL 模板及模板总数，不再为每一行打印 INFO。

耗时是应用侧观察值，可能包含获取连接、JDBC 驱动和事务收尾时间，并不等同于数据库服务器内部的纯执行时间。查询缓存的 `HIT/MISS/REFRESH/BYPASS` 使用 `com.yulinlin.data.cache.QueryCache` 的 DEBUG 日志；延迟写队列刷新摘要使用 `com.yulinlin.data.core.queue.WriteQueueManager` 的 DEBUG 日志。需要排查缓存时可临时提高对应 logger 级别，或者使用 `.cache(CacheMode.REFRESH)` 强制访问数据库。

#### 慢数据库监控

需要在运行期查看慢 SQL 与数据库异常时，引入可选模块：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>monitor</artifactId>
    <version>3.0</version>
</dependency>
```

```yaml
yulinlin:
  monitor:
    enabled: true
    threshold: 1s
    max-records: 1000
    username: admin
    password: ${MONITOR_PASSWORD:}
```

模块默认启用；设置 `yulinlin.monitor.enabled=false` 后，不注册数据库采集器、SQL 监听器、管理接口和页面，访问 `/monitor/` 也不会得到模块页面。启用时访问 `/monitor/` 可打开内置只读页面；页面使用 Vue 3 和锁定版本的 Element Plus 2.14.7，通过 jsDelivr 加载前端资源。部署环境无法访问公共 CDN 时，应将对应资源转存到业务自己的静态资源域名，再替换模块页面中的地址。

页面可以直接打开；数据接口发现尚未验证时，页面会在原地显示账号密码对话框。验证成功后只在当前 HTTP Session 中保存一个布尔标记，再自动加载监控数据，不跳转登录页，也不依赖任何安全框架。账号默认是 `admin`，密码没有默认值，必须通过配置或环境变量提供；密码为空时登录接口拒绝验证，避免发布一个所有人都知道的默认密码。

监控会轻量聚合所有实际执行的 JDBC 操作，以 `Group + Session 类型 + 操作类型 + 来源实体 + SQL 模板` 为一组，展示执行次数、慢查询率、平均耗时、最大耗时和最近耗时。只有达到 `threshold` 的成功操作及数据库异常才保留明细，最多保存最近 `max-records` 条；SQL 聚合组也使用该数量作为内存上限。平均耗时基于全部执行计算，不会因为只统计慢操作而被放大。

页面按照优化优先级排序：`P0` 表示出现异常；`P1` 表示平均耗时达到阈值 3 倍或最大耗时达到 10 倍；`P2` 表示平均达到 2 倍、最大达到 5 倍或慢查询率达到 50%；`P3` 表示平均达到阈值、最大达到 2 倍或慢查询率达到 10%；`P4` 表示只有少量请求超过阈值；其余为正常。慢查询率参与等级判断前至少需要 10 次执行，避免首次慢请求直接产生过高等级。同等级依次比较异常次数、慢操作次数、慢查询率、平均耗时和最大耗时。

实体请求会显示来源实体、完整类名以及 `@JoinTable` / `@JoinTableList` 声明的表。自定义 SQL 的 `fromClass` 为 `null` 或 `Object.class` 时，来源显示“自定义 SQL”、表名显示 `-`，不会通过正则猜测表名。批量写入每个执行组记录一次；SQL 只保留模板，不采集绑定参数。多数据源按 `Session.group()` 和具体 Session 类型区分，`RouteSession` 不重复计数；缓存命中没有访问数据库，因此不会生成记录，延迟写队列最终执行 JDBC 时会自然纳入监控。

页面接口和 Java API 都只读查询监控数据；`DatabaseMonitor.clear()` 仅供应用内部按需调用。

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
    log: true
    map-underscore-to-camel-case: true
    execute-batch-size: 256
    busy-timeout: 5000
    synchronous: NORMAL
    schema-mode: CREATE
    schema-packages:
      - demo.domain
```

启动时创建父目录与文件，启用 WAL、外键约束和单连接池。SQLite 的 `schema-mode` 默认 `CREATE`：递归扫描 `schema-packages` 并创建缺失结构；`VALIDATE` 只校验；`NONE` 完全跳过扫描。file 仅接受本地路径，不接受 JDBC URL、内存数据库或 file URI；空路径会报错。文件应放在可写的本地持久化目录，而不是 classpath 或网络共享盘。

#### 启动扫描与表结构拥有者

MySQL、PostgreSQL、SQLite、H2 和 Elasticsearch 共用 core 中相同的扫描规则。每个配置包都会递归扫描子包，但只处理显式声明 `@JoinTable(..., autoSchema = true)` 的具体普通结构类。`autoSchema` 默认 `false`；普通查询实体、DTO、接口、抽象类、JOIN 模型和统计模型不参与。未配置扫描包时也不扫描任何类。

包配置支持通配符：`*` 只匹配一级包，`**` 匹配零到任意多级包；匹配到的包仍会递归扫描子包。建议在 YAML 中加引号。例如 `"com.example.*.*.local"` 会匹配 `com.example` 与 `local` 之间恰好两级的目录，而 `"com.example.**.local"` 允许中间为任意层级。只允许完整包段使用通配符，`jdbc*`、`***` 和空包段会在启动时报配置错误。多个配置项命中的同一个类会自动去重。

```yaml
yulinlin:
  mysql:
    schema-mode: CREATE
    schema-packages:
      - "com.example.*.*.local"
      - "com.example.shared.entity"
```

同一个物理表只允许一个完整实体明确负责结构。轻量 DTO、不同字段视图和统计模型保持默认值即可：

```java
@JoinTable(value = "ai_demo_user", autoSchema = true)
public class DemoUser { /* 完整持久化字段 */ }

@JoinTable("ai_demo_user") // autoSchema 默认 false，只做 ORM 映射
public class DemoUserSummary { /* 查询投影 */ }
```

扫描结果若发现两个 `autoSchema=true` 的类映射同一表，应用启动失败并列出冲突类，避免“小实体先创建残缺表”。建表沿用字段别名、下划线映射、继承字段、单主键和 `JoinIndex` 元信息；static、transient、非持久化和关联查询字段不生成列。

#### 文本列、长度与说明

完整结构实体可以通过 `JoinField` 明确普通文本长度、大文本类型和列用途：

```java
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.TextTypeEnum;

@JoinField(textLength = 120, description = "视频标题")
private String title;

@JoinField(textType = TextTypeEnum.text, description = "视频正文")
private String content;
```

`textType` 默认是 `auto`。正数 `textLength` 会将文本列解析为定长上限的 VARCHAR；`textType=text` 表示大文本，不能再同时设置 `textLength`。自动 Schema 的映射如下：

| 声明 | MySQL | PostgreSQL | H2 | SQLite |
| --- | --- | --- | --- | --- |
| 默认普通文本 | `VARCHAR(255)` | `VARCHAR(255)` | `CHARACTER VARYING` | `TEXT` |
| `textLength = n` | `VARCHAR(n)` | `VARCHAR(n)` | `CHARACTER VARYING(n)` | 声明为 `VARCHAR(n)`，仍采用 TEXT affinity |
| `textType = text` | `LONGTEXT` | `TEXT` | `CHARACTER LARGE OBJECT`/CLOB | `TEXT` |

字符串主键统一按短标识处理：未指定长度时自动创建为 `VARCHAR(128)`，显式 `textLength` 只能是 1～128；超过 128 或把大文本声明为主键会在启动建表前报错。框架生成的普通/唯一索引也不接受大文本列。

`description` 最多 1024 个字符。新建表或补充新列时，MySQL 写入列 `COMMENT`，PostgreSQL/H2 执行 `COMMENT ON COLUMN`；SQLite 没有原生列注释，因此只接受该元数据但不落库。已有列不会因为 description 变化而执行 ALTER，注释也不参与兼容性校验。

这些选项只服务于启动 Schema 描述，不改变运行期 SQL 编解码器，也不会在 Java 端截断或校验字符串。MySQL/PostgreSQL/H2 会校验显式 VARCHAR 长度；SQLite 不执行或校验 VARCHAR 长度限制，确需强制长度应使用业务校验或自行添加 CHECK 约束。

SQLite 的整数/布尔值对应 INTEGER，浮点数对应 REAL，其余默认 TEXT。日期、枚举、BigDecimal、JSON 对象等使用 TEXT；字符串与数字仍由 JDBC 编解码器恢复 Java 字段。日期范围依赖统一、可按字典序排序的固定格式与时区；TEXT BigDecimal 的字符串比较不等于数值比较，数值范围或统计需要适当列类型或显式 SQL CAST。

启动完成后，CRUD 和自定义 SQL 均不再检查或修改 Schema。`BaseRequest.fromClass` 为 null、Object.class 或任意类型都不会触发 DDL；它原有的映射与路由语义不变。自定义 SQL 涉及的表若不在扫描实体中，应由迁移脚本或其他初始化流程创建。

#### 预览建表 SQL

`EntitySession` 统一提供 `createTableSql(Class<?>)`。它只根据当前 Session 的数据库方言和字段命名设置生成 SQL，不获取连接、不执行 DDL；返回列表依次包含建表、列注释和声明索引。只有显式 `autoSchema=true` 的完整简单表实体会生成 DDL，投影、统计模型、null 和 Object.class 返回空列表：

```java
@Resource(name = "mysqlSession")
private EntitySession mysqlSession;

List<String> ddl = mysqlSession.createTableSql(DemoUser.class);
ddl.forEach(System.out::println);
```

`initializeSchema(Collection<Class<?>>)` 才执行初始化。MySQL、PostgreSQL、H2、SQLite 和 Elasticsearch 的启动扫描调用该 Session 接口；JDBC Session 自己管理连接和 Statement，并在执行每条语句前以 INFO 输出 `[group][schema]` 和完整 DDL。预览和实际初始化共用相同的结构描述函数。`schema-mode=NONE` 会跳过自动初始化，但不妨碍手动调用 `createTableSql` 预览。

#### Schema 边界

这不是完整的版本迁移工具：`CREATE` 会创建缺失表、缺失普通列和缺失的声明索引；新增列通过 `ALTER TABLE ... ADD COLUMN` 执行。它不会自动增加或修改主键，不修改已有列类型，也不删除旧列/旧索引或添加外键、复合主键。框架无法识别字段改名：改名会被视为“增加新列”，旧列和旧数据仍保留，但数据不会自动搬到新列，因此改名必须使用业务迁移脚本。所有实际执行的 DDL 都会先以 INFO 日志打印，并且发生在会话 Bean 初始化阶段，不进入后续业务事务。

SQLite 使用继承 JdbcSession 的 SqliteSession，复用公共 CRUD；不需要 DataSource Bean 或 sqliteTransactionManager。SqliteDatabase 持有并关闭内部连接池。`supportsParallelWrites()` 返回 false，多连接批处理不会拆组；WAL 也不能让同一文件同时拥有多个写事务。

SQLite 不支持框架生成的 FOR UPDATE、日期分组和数值间隔分组，当前解析器明确拒绝；需用自定义 SQL 实现数据库专用功能。运行中不要单独删除 -wal/-shm 文件，也不要仅复制主文件作为可靠备份。NORMAL 偏向性能，FULL 更重视持久性；这不是业务吞吐量承诺。

### H2 接入

H2 适合纯 Java 的本地文件存储，不需要数据库服务器或本机原生库。加入 starter + h2：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>starter</artifactId>
    <version>3.0</version>
</dependency>
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>h2</artifactId>
    <version>3.0</version>
</dependency>
```

引入即启用，默认数据库基路径为 `data/local`，H2 实际生成 `data/local.mv.db`；默认 group 是 `h2`。最小配置只需覆盖文件名：

```yaml
yulinlin:
  h2:
    file: data/app
```

完整配置及默认值：

```yaml
yulinlin:
  h2:
    file: data/local
    group: h2
    username: sa
    password: ""
    mode: MYSQL
    log: true
    map-underscore-to-camel-case: true
    parallel-connections: 4
    connection-timeout: 10s
    lock-timeout: 5s
    execute-batch-size: 256
    schema-mode: CREATE
    schema-packages:
      - demo.domain
    auto-server: false
```

file 是本地数据库基路径，不写 `.mv.db` 后缀，也不接受 JDBC URL 或内存 URL。mode 当前只接受 MYSQL，以复用框架普通 CRUD/分页语法；原始 SQL 仍应按 H2 实际能力编写。auto-server 仅用于确实需要多个 JVM 同时打开一个文件的场景，单进程保持 false。

模块创建 `h2SessionFactory`、`h2Session` 和 group `h2`。`H2Database` 持有连接池，但 DataSource 不注册成 Spring Bean，也不创建独立 TransactionManager。RouteSession、框架事务注解和显式 group 的用法与其他 JdbcSession 相同：

```java
var users = ModelSelectWrapper.newInstance("h2", DemoUser.class).selectList();
ModelInsertWrapper.newInstance("h2", user).execute();
```

H2 在 Session Bean 初始化时扫描配置包。schema-mode 的含义是：CREATE 创建缺失表、普通列和声明的索引并校验，VALIDATE 只校验且缺失时报错，NONE 完全跳过。自动增量只执行安全的 `ADD COLUMN`：不会自动增加或变更主键，不改已有列类型，也不删除旧列或旧索引。字段改名会被视为新增列，旧数据不会自动搬迁，必须使用版本迁移脚本。

字段默认映射为 BOOLEAN、INTEGER、BIGINT、DOUBLE PRECISION 或 CHARACTER VARYING。日期、枚举、BigDecimal/BigInteger、byte[] 与 JSON 对象继续经过公共编码器存为文本。日期范围要求统一的可排序格式；文本数字范围和统计需要显式 CAST。JSON 路径条件当前不自动生成，使用可信的 H2 自定义 SQL。

H2 的 DDL 会自动提交，因此启动创建表或索引使用独立直连连接，不会提交业务事务连接；相应地，这些结构变更也不随业务回滚。普通 CRUD、SELECT FOR UPDATE、日期/区间统计使用 H2 专用解析器。

H2 默认允许一个框架事务最多使用 4 个连接，并按 256 行调用一次 executeBatch；这适合本地批量吞吐，但多连接提交仍不是单连接原子事务。严格原子性或事务内读己之写时设置 parallel-connections: 1。connection-timeout 是取连接等待时间，lock-timeout 是数据库锁等待时间，都不是 SQL 查询执行超时。

#### SQLite/H2 选型

4 个业务线程、每次最多 128 行、总计 10 万行的 JMH 实测中，H2 为 89,401 行/秒，SQLite 为 45,456 行/秒；SQLite 的累计内存分配更低。多线程小批量且吞吐优先选 H2，单文件和写入可排队选 SQLite。测试口径、完整指标、限制和复现命令见[第七专题](07-SQLite与H2性能报告.md)。

#### MySQL/PostgreSQL/H2/SQLite 自动索引

在具体表实体上重复使用 `@JoinIndex`，fields 填 Java 属性名，顺序就是联合索引的列顺序。字段先经过 JoinField 和下划线映射，再生成数据库索引：

```java
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinIndex;
import com.yulinlin.data.core.anno.JoinTable;
import java.util.Date;

@JoinTable(value = "video_comment", autoSchema = true)
@JoinIndex(fields = {"videoId", "crtTime"})
@JoinIndex(fields = {"videoId", "userId"}, unique = true)
public class VideoComment {
    @JoinField(name = "video_id") private String videoId;
    @JoinField(name = "user_id") private String userId;
    @JoinField(name = "crt_time") private Date crtTime;
}
```

上例自动生成普通索引 `idx_video_comment_video_id_crt_time` 和唯一索引 `uk_video_comment_video_id_user_id`。用户不填写索引名；名称由类型前缀、表名和映射后的列名组成，统一小写，超过 63 个字符时截断并追加稳定的 8 位哈希。

同一有序字段组合不能重复声明；如果同时声明普通和唯一索引，只保留唯一索引。字段为空、重复、不存在、exist=false、关联字段或函数映射字段会在启动初始化时报错。索引注解只读取结构拥有者具体类上的声明，不继承父类索引；fields 仍可引用父类持久化字段。

已有同名索引会校验字段顺序、唯一性以及是否为普通升序列索引；不兼容时要求手动迁移。删除或修改注解不会自动删除旧索引，修改字段组合会创建新名称。MySQL/PostgreSQL/H2 使用独立启动连接；SQLite 在内部数据库会话初始化时执行。

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

yulinlin:
  postgresql:
    log: true
    map-underscore-to-camel-case: true
    parallel-connections: 4
    execute-batch-size: 256
    schema-mode: CREATE
    schema-packages:
      - demo.domain
    full-text:
      index-config: jiebacfg
      query-config: jiebaqry
      max-words: 40
      min-words: 15
    highlight:
      start-tag: __HL_START__
      end-tag: __HL_END__
      max-fragments: 2
      fragment-delimiter: "..."
```

模块创建 `postgresqlSessionFactory` 和 `postgresqlSession`，默认 group 为 `postgresql`，实际会话类型为 PostgresqlSession。`schema-mode` 默认 `NONE`；设为 `CREATE` 后会扫描实体并自动创建缺失表、普通列和声明索引，`VALIDATE` 只校验。`@JoinTable` 可使用普通表名，也可使用 `schema.table`，两段都会独立引用。

现有编码器可能把日期、枚举、BigDecimal 或对象编码为字符串。`stringtype=unspecified` 让驱动按目标列或 SQL 上下文推断类型；它不是框架自动添加的参数，也不解决所有类型问题。无明确类型上下文时可在可信 SQL 中显式 CAST，例如 `CAST(#{payload} AS jsonb)`。不要推定原生 enum、UUID、数组、空间类型已有完整专用编解码支持。

标识符采用双引号，schema 限定名按段引用；实际列名应与模型映射一致。普通 CRUD 和实体可复用，原始 MySQL SQL、反引号、函数与 DDL 不会被自动翻译。自动 Schema 不负责序列、自增键、外键、分区表或数据迁移。

JSON 读取支持合法 JSON 的路径，例如 `payload->profile->name`；数字/布尔条件按比较值生成 CAST。路径部分更新尚未自动生成 jsonb_set，应整字段替换或写专用 SQL。非法 JSON 和不能转换的值会报错，不是静默跳过。

JDBC 参数统一使用 `setObject`，业务参数不应直接携带 InputStream、bytea/oid 等二进制对象；需要保存时应先由明确的编解码策略转成文本，或把文件放到对象存储后只保存地址。原生布尔列保留 false/NULL；除布尔列外结果大多沿用字符串解码，不能推定 timestamptz 等扩展类型完整往返已验证。

#### PostgreSQL 中文全文检索

全文检索是 PostgreSQL 模块的可选能力，中文分词依赖数据库服务器已安装 `pg_jieba`。框架不会下载或安装 PostgreSQL 扩展二进制；运维先按服务器版本安装插件，再由有权限的账号执行：

```sql
CREATE EXTENSION IF NOT EXISTS pg_jieba;
```

默认索引配置是 `jiebacfg`，查询配置是 `jiebaqry`。只有结构拥有者实体上显式声明 `@JoinField(fullText = true)` 的文本列才生成 GIN 表达式索引。`schema-mode=CREATE` 会创建缺失索引，`VALIDATE` 只校验；初始化遇到配置不存在时会直接说明需要安装/启用 pg_jieba。修改 index-config 会使用新的索引名称创建新索引，旧索引不会自动删除。

`index-config` 同时用于 `to_tsvector`、GIN 索引和 `ts_headline`，不能随意设置为不同分词配置；`query-config` 用于 `websearch_to_tsquery`。`max-words/min-words` 是 PostgreSQL 专属摘要长度；高亮标签、片段数和分隔符来自所有 EntitySession 共用的 `highlight` 配置，并作为 JDBC 参数绑定，不拼接用户输入。默认标记不是 HTML，前端可安全地按 `__HL_START__` / `__HL_END__` 转义后渲染；若改成 `<mark>`，仍须遵循项目的 XSS 输出策略。查询用法见[全文匹配与高亮](02-CRUD与统计分析.md#全文匹配与高亮)。

MySQL、SQLite、H2 当前不会创建全文索引：`fullText=true` 被忽略，`match()` 退化为包含式 LIKE，`highlight()` 返回原字段。这保证同一业务调用可以运行，但不代表大表查询具有全文检索性能。

### Elasticsearch 结构初始化

Elasticsearch 与 JDBC 模块复用 core 的实体扫描和 Schema 配置。默认 `schema-mode=NONE`，不会访问或修改索引；启用时配置：

```yaml
yulinlin:
  elasticsearch:
    url: http://localhost:9200
    log: true
    map-underscore-to-camel-case: true
    schema-mode: CREATE
    schema-packages:
      - com.example.search.entity
    highlight:
      start-tag: "<mark>"
      end-tag: "</mark>"
      max-fragments: 2
      fragment-delimiter: "..."
```

模块使用 Elasticsearch Java Client `9.5.4` 和其默认 `Rest5Client`。`CREATE` 会创建缺失索引和基础 Mapping，并给已有索引补充缺失字段；`VALIDATE` 只检查索引、字段和字段类型，不修改结构。普通字符串映射为 `keyword`，`@JoinField(fullText = true)` 或 `textType = text` 映射为 `text`；复杂 JSON 对象保留 Elasticsearch 动态映射。已存在字段类型不一致时不会强制修改，而是要求手工迁移索引。查询调用 `.highlight(field)` 后使用公共标签与片段数，并按 `fragment-delimiter` 合并返回的多个片段到原字段。

### 多数据源注册

三个名字不要混用：

| 名称 | 作用 |
| --- | --- |
| DataSource Bean 名 | Spring 注入具体连接池 |
| Session Bean 名 | 容器管理会话对象 |
| group | 框架运行请求时选库 |

工厂 `create(dataSource, group)` 返回已配置的 Session，并继承该工厂所属模块的配置，但不注册路由。把它声明为 EntitySession Bean，core 就会收集到 RouteSession；无需在 Bean 创建方法中反向注入 RouteSession。某个同类 Session 需要独立参数时，可调用 `create(dataSource, group, properties)` 显式覆盖。

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

手动 new JdbcSessionFactory 后不能立即假定可用：当前工厂需要容器注入编码器、过滤器和日志等。缓存由 RouteSession 注册 Session 时统一注入；`create(DataSourceProperties, group)` 会新建池，资源所有者仍须负责关闭，不自动等价于独立 DataSource Bean。

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

路由优先级为：请求显式 group → 方法 `@JoinSession` → Bean 类或 Repository 接口 `@JoinSession` → 模型 `@JoinSession` → 当前普通会话上下文 → 负载均衡默认组。选择方式举例，DemoUser 定义在第二专题：

```java
ModelSelectWrapper.newInstance("oss", DemoUser.class).selectList();
```

也可在 Spring Service、Repository 接口或其方法上使用 `com.yulinlin.data.core.anno.JoinSession("oss")`，必须通过 Spring Bean 调用。方法注解覆盖类或接口注解，内层调用结束后恢复外层路由；框架默认 LoadBalance Bean 会绑定 YAML，自定义 Bean 应自行配置。

单个可用节点直接返回，但仍检查 cluster、正权重和健康状态；同组多节点继续按权重选择。健康缓存不会把离线默认库悄悄切到另一个组。运行时修改默认组或移除资源应协调在途事务。

### 配置速查

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| yulinlin.datasource.default-group | 未设置 | 单组自动；多组指定默认 |
| yulinlin.monitor.enabled | true | 是否启用数据库操作采集及内置监控接口和页面；关闭后不注册相关 Bean |
| yulinlin.monitor.threshold | 1s | 慢操作明细保留阈值，同时作为 P1–P4 优先级的计算基准；异常始终保留 |
| yulinlin.monitor.max-records | 1000 | 慢操作明细和 SQL 聚合组各自的内存上限；超出后淘汰最早或最久未访问的数据 |
| yulinlin.monitor.username | admin | 内置监控对话框账号 |
| yulinlin.monitor.password | 空 | 内置监控对话框密码；必须显式配置，推荐使用环境变量 |
| yulinlin.`<module>`.log | false | 当前模块 SQL 成功日志；module 为 mysql、postgresql、sqlite 或 h2 |
| yulinlin.`<module>`.map-underscore-to-camel-case | true | 当前模块下划线与驼峰映射 |
| yulinlin.`<module>`.parallel-connections | 4 | 当前模块每框架事务连接上限；SQLite 最终固定为 1 |
| yulinlin.`<module>`.execute-batch-size | 256 | 当前模块一次 executeBatch 的行数，不是 commit |
| yulinlin.mysql.schema-mode | NONE | CREATE、VALIDATE 或 NONE；默认不改生产结构 |
| yulinlin.mysql.schema-packages | 空 | MySQL 启动递归扫描包列表 |
| yulinlin.postgresql.schema-mode | NONE | CREATE、VALIDATE 或 NONE；默认不改生产结构 |
| yulinlin.postgresql.schema-packages | 空 | PostgreSQL 启动递归扫描包列表 |
| yulinlin.postgresql.full-text.index-config | jiebacfg | PostgreSQL 建索引、匹配和高亮使用的分词配置 |
| yulinlin.postgresql.full-text.query-config | jiebaqry | PostgreSQL 查询分词配置 |
| yulinlin.postgresql.full-text.max-words/min-words | 40 / 15 | PostgreSQL ts_headline 摘要长度 |
| yulinlin.`<module>`.highlight.start-tag/end-tag | `__HL_START__` / `__HL_END__` | PostgreSQL/Elasticsearch 返回到原字段中的高亮边界 |
| yulinlin.`<module>`.highlight.max-fragments | 2 | 最大高亮片段数，必须大于 0 |
| yulinlin.`<module>`.highlight.fragment-delimiter | `...` | 返回多个片段时的合并文本 |
| yulinlin.sqlite.file | data/local.db | 进程工作目录下的路径 |
| yulinlin.sqlite.group | sqlite | 本地会话组 |
| yulinlin.sqlite.busy-timeout | 5000 ms | 等锁，不是查询超时 |
| yulinlin.sqlite.synchronous | NORMAL | 可选 FULL |
| yulinlin.sqlite.schema-mode | CREATE | CREATE、VALIDATE 或 NONE |
| yulinlin.sqlite.schema-packages | 空 | SQLite 启动递归扫描包列表 |
| yulinlin.h2.file | data/local | 数据库基路径，实际文件追加 .mv.db |
| yulinlin.h2.group | h2 | H2 会话组 |
| yulinlin.h2.username/password | sa / 空 | 内部文件库凭据 |
| yulinlin.h2.mode | MYSQL | 当前唯一支持的兼容模式 |
| yulinlin.h2.connection-timeout | 10s | 连接池取连接等待时间 |
| yulinlin.h2.lock-timeout | 5s | H2 锁等待时间 |
| yulinlin.h2.schema-mode | CREATE | CREATE、VALIDATE 或 NONE |
| yulinlin.h2.schema-packages | 空 | H2 启动递归扫描包列表 |
| yulinlin.h2.auto-server | false | 是否允许多 JVM 打开同一文件 |
| yulinlin.elasticsearch.log | false | Elasticsearch 成功请求日志 |
| yulinlin.elasticsearch.url | http://localhost:9200 | Elasticsearch 服务地址 |
| yulinlin.elasticsearch.map-underscore-to-camel-case | true | Elasticsearch 实体字段命名转换 |
| yulinlin.elasticsearch.schema-mode | NONE | CREATE、VALIDATE 或 NONE |
| yulinlin.elasticsearch.schema-packages | 空 | Elasticsearch 启动递归扫描实体包 |

### 接入边界与排障

- 单数据源也必须等待容器初始化，不在静态初始化块里查询。所有模块只有配置扫描包并启用相应模式时才处理结构；SQLite/H2 默认模式为 CREATE，MySQL/PostgreSQL/Elasticsearch 默认为 NONE。未扫描的结构由业务迁移工具准备。
- 不把独立库无意注册为同组节点；同组是负载均衡，不是覆盖注册。
- 多库事务与多连接事务不是 XA，详细规则见 [第二专题](02-CRUD与统计分析.md#事务使用)。
- 未发现会话：检查数据库模块、自动配置 imports、DataSource 候选、Session Bean 和 group。
- 表或字段不存在：核对实际建表、JoinField 映射和实体基类继承字段。
- PostgreSQL 报 MySQL 函数错误：检查是否使用了 mysqlSessionFactory 或原始 MySQL SQL。
- SQLite 原生库在 JDK 25 下可能提示 native-access 警告；部署时按实际环境配置 JVM 原生访问权限，不把警告当成数据库初始化成功的证明。
- H2 表已存在但字段报不兼容：框架不会迁移旧结构，手动迁移后重启或重建 Session；不要直接删除正在使用的 .mv.db 文件。

内部 Session 创建与扩展规则见 [第五专题](05-扩展开发与维护.md)。

---

<!-- source: doc/02-CRUD与统计分析.md -->
## ORM CRUD 自定义 SQL 统计分析与事务

本文从一个用户实体完成 CRUD，再扩展条件查询、SQL JOIN、自定义 SQL、统计模型和事务。数据库接入与 group 注册先看 [第一专题](01-接入与数据源.md)。

阅读导航：[最小实体](#最小实体和配套表) · [CRUD](#crud-完整服务) · [查询条件](#查询条件与结果组织) · [自定义 SQL](#自定义-sql-执行) · [统计分析](#统计分析) · [批量写入](#批量与多连接写入) · [延迟写队列](#延迟写队列) · [事务](#事务使用) · [Repository](#repository-接口) · [边界](#使用边界与排障)

### 最小实体和配套表

以下完整类放在 `demo/domain/DemoUser.java`，提供无参构造和 getter/setter。IdEntity 提供 String 主键及插入前的应用侧 ID 生成。

```java
package demo.domain;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;

@JoinTable(value = "ai_demo_user", autoSchema = true)
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

MySQL/PostgreSQL/SQLite/H2 若已在第一专题配置启动扫描，DemoUser 由应用启动阶段创建/校验；否则必须提前准备。下列 DDL 只在自己的示例数据库执行，不把它当作生产迁移脚本：

```sql
CREATE TABLE ai_demo_user (
    id VARCHAR(128) NOT NULL PRIMARY KEY,
    user_name VARCHAR(100),
    status INT
);
```

SuperEntity 在 IdEntity 上增加 crtTime、uptTime 和填充逻辑，使用它时表必须有对应列。非持久化属性显式用 `@JoinField(exist = false)`，不能依赖“没有注解就一定忽略”。

| 注解 | 用途 |
| --- | --- |
| JoinTable | 表或 SQL JOIN 映射；autoSchema 默认 false，只有完整结构实体明确设为 true 才参与启动 Schema |
| JoinField(name = "...") | Java 属性与列名映射 |
| JoinField(textLength = 120) | 自动 Schema 的 VARCHAR 字符上限；字符串主键默认且最多 128 |
| JoinField(textType = TextTypeEnum.text) | 自动 Schema 使用大文本；不能作为主键或框架声明索引 |
| JoinField(description = "...") | 列用途说明；MySQL/PostgreSQL/H2 新表或新列写入注释，SQLite 不落库 |
| JoinField(fullText = true) | PostgreSQL 为该文本列维护 pg_jieba GIN 全文索引；其他数据库忽略 |
| JoinField(exist = false) | 排除非数据库列 |
| JoinField(update = false) | 排除更新字段 |
| JoinMeta(primaryKey = true) | 主键元信息；不是 JoinPrimary |
| JoinIndex(fields = {...}) | MySQL/PostgreSQL/H2/SQLite 启动维护普通或唯一联合索引；字段写 Java 属性名 |
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

#### 新增用法

以下新增、更新和删除代码均为业务方法体片段，`List` 使用 `java.util.List`，实体沿用上文 DemoUser。

单条新增直接传实体；IdEntity 会在插入前生成 ID，execute 返回数据库报告的影响行数：

```java
DemoUser user = new DemoUser();
user.setUsername("alice");
user.setStatus(1);

int affected = ModelInsertWrapper.newInstance("mysql", user).execute();
String generatedId = user.getId();
```

同一批数据传集合，默认复用 JDBC batch；是否使用多连接由调用方显式决定：

```java
List<DemoUser> users = List.of(firstUser, secondUser);
int affected = ModelInsertWrapper.newInstance("mysql", users).execute();
```

大批量才考虑 `.batch()`；连接数量、提交大小和原子性限制见[批量与多连接写入](#批量与多连接写入)。

#### 更新用法

对象更新适合“按主键修改非 null 字段”。主键生成 WHERE，其他非 null、可更新字段生成 SET：

```java
DemoUser patch = new DemoUser();
patch.setId(id);
patch.setStatus(2);

int affected = ModelUpdateWrapper.newInstance("mysql", patch).execute();
if (affected != 1) throw new IllegalStateException("User does not exist: " + id);
```

需要明确控制 SET 和 WHERE 时，以实体类型创建 Wrapper：

```java
int affected = ModelUpdateWrapper.newInstance("mysql", DemoUser.class)
        .field("status", 2)
        .eq("id", id)
        .execute();
```

数值字段支持原子增减；下面只是语法示例，status 必须确实适合做数值累加：

```java
int affected = ModelUpdateWrapper.newInstance("mysql", DemoUser.class)
        .inc("status", 1)
        .eq("id", id)
        .execute();

int restored = ModelUpdateWrapper.newInstance("mysql", DemoUser.class)
        .dec("status", 1)
        .eq("id", id)
        .execute();
```

集合更新会为每个对象建立更新节点。每个对象都应携带完整非空主键，只设置真正需要修改的字段：

```java
DemoUser first = new DemoUser();
first.setId("user-1");
first.setStatus(1);

DemoUser second = new DemoUser();
second.setId("user-2");
second.setStatus(2);

int affected = ModelUpdateWrapper.newInstance("mysql", List.of(first, second)).execute();
```

普通对象更新、自动更新代理和显式 `.field(name, null)` 都跳过 null；它们不能把数据库列清成 NULL。需要清空字段时使用参数化的[自定义写入](#自定义写入)。`@JoinField(update = false)` 字段不参与对象更新；version 字段按当前值加入条件并递增，仍应检查影响行数判断并发冲突。

#### 删除用法

按实体主键删除时，只设置主键即可：

```java
DemoUser key = new DemoUser();
key.setId(id);
int affected = ModelDeleteWrapper.newInstance("mysql", key).execute();
```

按条件删除可以直接使用属性名或 Lambda 条件：

```java
int affected = ModelDeleteWrapper.newInstance("mysql", DemoUser.class)
        .eq("id", id)
        .execute();
```

按主键集合删除前必须处理空集合：

```java
public int deleteByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) return 0;
    return ModelDeleteWrapper.newInstance("mysql", DemoUser.class)
            .in("id", ids)
            .execute();
}
```

也可以传入只包含主键的实体集合，生成多条删除节点：

```java
DemoUser first = new DemoUser();
first.setId("user-1");
DemoUser second = new DemoUser();
second.setId("user-2");

int affected = ModelDeleteWrapper.newInstance("mysql", List.of(first, second)).execute();
```

框架不默认拦截全表写入。`ModelDeleteWrapper.newInstance("mysql", DemoUser.class).execute()` 没有 WHERE，带 SET 但没有 WHERE 的更新也可能影响整表；业务入口必须先校验主键、集合和条件。execute 返回影响行数，单条更新/删除通常应检查是否为 1。

#### 写操作与查询缓存

新增、更新和删除在数据库提交成功后，默认清理本次写入涉及的查询缓存 namespace。全局默认值如下：

```yaml
yulinlin:
  cache:
    clear-on-write: true
```

单次写操作可以覆盖全局配置；`clearCache`只控制 ORM 查询缓存，不会删除通过 `CacheClient` 保存的业务缓存：

```java
int affected = ModelUpdateWrapper.newInstance("mysql", patch)
        .clearCache(false) // 本次写入不清理查询缓存；未调用时继承全局配置
        .execute();
```

写 Wrapper 只提供 `clearCache(boolean)`，不再提供写操作版 `cache(...)`、`invalidate(...)` 或 `invalidateAll()`。一般业务保留默认 `true`；只有明确接受数据在 TTL 内短暂陈旧时才关闭。回滚或执行失败不清理缓存。`clear-on-write` 属于可选缓存 Provider；未引入 `cache-caffeine`、`cache-ehcache` 或 `cache-redis` 时不会启用该配置，写路径也不会解析失效 namespace。详细规则见[查询缓存专题](08-查询缓存.md#失效和一致性)。

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
| match | 全文匹配；PostgreSQL 的 fullText 字段使用分词索引，其他 SQL 数据源退化为包含式 LIKE |
| in | 集合条件 |
| between | 范围；字符串字段与列类型的排序规则需要一致 |
| isNull | SQL NULL 条件 |
| and / not / where | 条件组合；按实际 Wrapper 接口使用 |
| selectOne | 无结果返回 null；取第一条，不自动保证唯一 |
| selectList / count | 列表 / 计数 |
| selectPage(page, size) | 数据库分页，调用前校验页码和大小 |
| selectByMap("id") | Java 端索引，重复键由后项覆盖 |
| selectByGroup("status") | Java 端组织结果，不是 SQL GROUP BY |

#### 查询条件对象

业务页面查询优先定义 `XxxPo extends PagePo`，在子类业务字段上声明 `@JoinWhere`，一次复用条件、分页与排序。完整的 `PayChannelPo`、Controller 和 `ISuperService.page(po)`示例见[Common 业务开发：页面查询对象](09-项目开发最佳实践.md#页面查询对象pagepo--joinwhere)。本节只解释对象条件如何转换为 WHERE，也适用于不分页的独立查询 DTO。

直接把实体作为查询条件：

```java
DemoUser filter = new DemoUser();
filter.setStatus(1); // 因为字段有 JoinWhere，参与条件。
var users = ModelSelectWrapper.newInstance("mysql", filter).selectList();
```

实体类型和查询参数也可以分开。推荐 Controller 接收独立查询 DTO，再把它作为第三个参数传入：

```java
package demo.query;

import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinWhere;
import lombok.Data;

import java.util.List;

@Data
public class UserQuery {
    @JoinWhere(name = "id", condition = ConditionEnum.in)
    private List<String> ids;

    @JoinWhere(name = "username", condition = ConditionEnum.like)
    private String keyword;

    @JoinWhere(name = "status", condition = ConditionEnum.between)
    private List<Integer> statusRange;
}
```

```java
UserQuery condition = new UserQuery();
condition.setIds(List.of("user-1", "user-2"));
condition.setKeyword("alice");
condition.setStatusRange(List.of(1, 3));

List<DemoUser> users = ModelSelectWrapper
        .newInstance("mysql", DemoUser.class, condition)
        .orderByDesc("crtTime")
        .selectList();
```

这里 `DemoUser.class` 决定查询表、返回字段和结果类型，`condition` 只负责生成 WHERE。查询 DTO 不需要 `@JoinTable`，但必须提供可读取的 getter。`@JoinWhere.name` 写目标实体的 Java 属性名；省略时使用查询 DTO 自己的属性名。`condition` 可使用 `eq/ne/gt/gte/lt/lte/like/likeRight/match/in/between/isNull`。

只有带 `@JoinWhere` 的属性才会参与查询。`null`、空字符串和空集合会被忽略；`between` 传两个元素的集合，`in` 传集合。`isNull` 条件需要一个非 null 的启用字段，例如 Boolean 值为 true 时生成目标列的 `IS NULL`，值为 null 时不生成该条件。生成对象条件后仍可继续调用 `.eq()`、`.orderByDesc()`、`.page()` 等链式方法追加条件和排序。

`JoinWhere.and()` 默认为 `true`。所有 `and=true` 的字段进入同一个 AND 分组；`and=false` 的字段进入外层 OR 分组。混合 AND/OR 时应先确认期望的括号语义，复杂嵌套条件优先使用 Wrapper 的 `.where(...)`、`.and(...)`、`.or(...)`，不要只靠查询 DTO 猜测组合顺序。

不是所有非 null 属性都自动成为 WHERE 条件。字符串条件优先使用 Java 属性名，使 JoinField 映射生效。没有默认的全表写入保护；UPDATE/DELETE 前业务必须验证主键或条件。

#### 全文匹配与高亮

PostgreSQL 中文全文检索先在完整表实体上标记需要建立索引的文本字段：

```java
@JoinTable(value = "video", autoSchema = true)
public class Video {
    @JoinMeta(primaryKey = true)
    private String id;

    @JoinField(name = "title", fullText = true, textLength = 200)
    private String title;

    @JoinField(name = "content", fullText = true, textType = TextTypeEnum.text)
    private String content;

    // getter/setter
}
```

启动扫描在 PostgreSQL 上为 title、content 分别生成可单字段命中的 GIN 表达式索引。查询时 `match` 指定检索字段，`highlight` 把高亮后的摘要直接写回同名结果字段，不需要额外的 titleHighlight DTO 属性：

```java
List<Video> videos = ModelSelectWrapper.newInstance("postgresql", Video.class)
        .match(Video::getTitle, "Java 性能")
        .highlight(Video::getTitle)
        .orderByDesc("id")
        .selectList();
```

也可以同时检索多个字段：

```java
List<Video> videos = ModelSelectWrapper.newInstance("postgresql", Video.class)
        .or(or -> or.match(Video::getTitle, keyword)
                    .match(Video::getContent, keyword))
        .fieldMeta(fields -> fields
                .field("title")
                .field("content")
                .highlight("title")
                .highlight("content"))
        .selectList();
```

只有同一查询中实际出现原生 `match(field, ...)` 的字段才调用 `ts_headline`。单独写 `highlight()`、字段未声明 `fullText=true`、原始 SQL查询，都会返回普通原字段。计数只解析 WHERE，不生成高亮表达式。公共高亮标记与片段设置在 `yulinlin.postgresql.highlight`，PostgreSQL 专属摘要长度在 `yulinlin.postgresql.full-text`；pg_jieba 的安装和完整 YAML 见[PostgreSQL 中文全文检索](01-接入与数据源.md#postgresql-中文全文检索)。

MySQL、SQLite、H2 当前把 `match()` 解释为 `%关键词%` LIKE，并忽略 `highlight()`；这只是兼容回退，大表检索应改用 PostgreSQL 原生全文能力或 Elasticsearch。Elasticsearch 的 `match()` 使用原生 MatchQuery；MongoDB 保持现有正则兼容语义。

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

// MySQL 专用命令，不是 SQLite/H2/PostgreSQL 通用 SQL。
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

自定义 SQL 不触发建表，`setFromClass` 也不再承担 Schema 初始化。MySQL/PostgreSQL/SQLite/H2 的结构只由启动扫描处理；原始 SQL 涉及但没有扫描实体的表，应由迁移脚本创建。`entityClass` 仅决定查询结果如何解码。

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

Request 可以复用事务上下文，但可变请求对象不能跨线程共享。原始查询 SQL 可以参与缓存，Key 会包含 SQL 与带类型参数。引入缓存 Provider 后，自定义写 SQL 默认继承 `yulinlin.cache.clear-on-write`；开启时因为无法可靠推导目标表，会在提交成功后清理全部 ORM 查询缓存，也可调用 `update.clearCache(false)` 关闭本次清理。未引入 Provider 时没有查询缓存，因此也不执行这项全量失效判定。

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

MySQL/PostgreSQL/H2 使用各自日期与区间解析器；SQLite 当前不支持这两个自动分组入口，需写 strftime 等专用 SQL。TEXT 日期须统一格式与时区；TEXT 数字的比较、排序和聚合不能直接当作原生数值列。自定义函数和表达式始终按目标库语法编写。

### 批量与多连接写入

方法体片段，usersToInsert 为已准备好的 DemoUser 集合：

```java
ModelInsertWrapper.newInstance("mysql", usersToInsert).execute();         // 普通 JDBC batch
ModelInsertWrapper.newInstance("mysql", usersToInsert).batch().execute(); // 申请多连接路径
```

```yaml
yulinlin:
  mysql:
    parallel-connections: 4
    execute-batch-size: 256
  postgresql:
    parallel-connections: 2
    execute-batch-size: 128
  h2:
    parallel-connections: 4
    execute-batch-size: 256
  sqlite:
    execute-batch-size: 256
```

这些数都必须是正整数，并按数据库模块独立生效。MySQL、PostgreSQL 与 H2 默认最多 4 个连接；调用 `.batch()` 时，把整批数据均匀分成最多 4 个大组，一组一个任务/连接。同 SQL 复用 PreparedStatement，每满配置行数执行一次 executeBatch，尾批也执行。SQLite 固定单写连接，但仍可独立设置 execute-batch-size。

只有 .batch()、执行器、请求阈值和 supportsParallelWrites 等条件满足才并发；SQLite、单连接池或 Spring 绑定连接不拆组。H2 可拆组，但多个连接仍受文件锁、索引和写入热点影响。4 是每 Session、每框架事务的上限，不是整个应用并发上限，也不保证 4 倍速度。

executeBatch 不是 commit。所有已提交任务结束后再提交/回滚及释放；失败不能让工作线程继续在已归还连接上执行。解析仍持有完整输入集合，不是流式导入。SUCCESS_NO_INFO 按成功命令计数，不保证精确行数。

多个连接有各自的本地事务，中途提交失败无法撤销已成功提交的连接；跨连接未提交数据也不保证可读。严格单库原子性和事务内读己之写使用 parallel-connections: 1。

### 延迟写队列

适合点赞、浏览统计等允许最终一致、希望把短时间内的小批写入合并执行的业务。插入、更新和删除使用同一个入口：

```java
ModelInsertWrapper.newInstance("mysql", users).queue().execute();
ModelUpdateWrapper.newInstance("mysql", user).queue().execute();
ModelDeleteWrapper.newInstance("mysql", User.class)
        .eq(User::getId, id)
        .queue()
        .execute();
```

`execute()` 在节点成功进入内存队列后立即返回 `0`，不代表数据库影响了零行，也不返回自增主键。Wrapper 创建时已经把对象当时的字段值生成到 `INode`；入队后重新给实体字段赋值不会改变这次写入，Node 中原本保存的 List、Map、Date 等可变值引用则不要再原地修改。普通请求不调用 `.queue()`，仍按原方式立即执行。

```yaml
yulinlin:
  data:
    queue:
      flush-interval: 1m
      flush-size: 1024
      concurrency: 4
      retry-count: 3
```

`flush-size` 按当前活动队列的 Node 总数计算，不按 Wrapper 调用次数，也不按单个分组计算。总数达到阈值时会立即异步刷新当前整个队列快照，不必等待 `flush-interval`；例如多个分组各有数百个 Node，合计达到默认的 `1024` 也会立即刷新。刷新快照再按实际 `EntitySession` 实例、请求类型和 `fromClass` 统一分组；该阈值不拆分请求，也不改变事务边界。

不同 `EntitySession` 最多按 `concurrency` 并发刷新，同一 `EntitySession` 内的分组仍依次执行；单个分组调用过 `.batch()` 时，仍由该数据源的 `parallel-connections` 决定组内多连接。因此 `concurrency=4` 是同时处理的 Session 上限，不是全局物理连接上限。某组失败只重试该组，不阻塞其他 Session；写入成功后才按 `clearCache` 规则清理查询缓存。队列合并同组请求时，任一请求显式 `clearCache(true)` 就清理；全部显式为 `false` 才不清理；其余组合继续继承全局配置。

这是进程内队列，应用异常退出可能丢失尚未刷新数据，不替代消息队列。队列没有硬容量上限；如果持续生产速度高于数据库写入速度，待执行和待重试 Node 会继续占用内存，应在业务入口限流并监控积压。`.queue()` 面向零散小写入的定时聚合，不是大数据导入器；大集合直接在业务异步任务中调用 `.batch().execute()`。`.queue()` 必须在事务外调用，因为延迟任务不能加入调用线程的原事务；依赖严格先后关系、自增 ID、实时影响行数或强一致读取的写操作继续同步执行。应用正常关闭时框架会尝试最后刷新一次；数据库失败按 `retry-count` 在后续刷新中重试，超过次数后记录错误并丢弃。

队列不会为每个入队对象打印日志。开启 `com.yulinlin.data.core.queue.WriteQueueManager=DEBUG` 后，每个非空刷新快照输出一条摘要，包括触发方式、Session/分组/Node 数、影响行数、耗时和剩余积压；失败、重试和最终丢弃仍分别使用 WARN/ERROR。

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

### Repository 接口

Repository 根据方法名生成普通 Wrapper 查询。引入模块后自动启用，不再需要扫描注解：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>repository</artifactId>
    <version>3.0</version>
</dependency>
```

未配置扫描路径时，默认从 `@SpringBootApplication`所在根包递归查找。需要限制范围或扫描多个业务模块时，在配置文件指定：

```yaml
yulinlin:
  repository:
    scan-packages:
      - "com.example.*.*.local"       # 每个 * 匹配一层
      - "com.example.**.repository"  # ** 跨任意层级
      - "${APP_REPOSITORY_PACKAGE:com.example.repository}"
```

扫描使用类元数据，不会加载路径下所有普通类。普通包名递归扫描；路径段支持 Ant 风格通配符：`*`匹配一级包，`**`匹配任意层级，`?`匹配单个字符。YAML 中包含 `*`的路径必须加引号。配置缺失时使用应用根包，而配置存在时只扫描列出的路径。

Repository 默认 Bean 名是接口简单类名首字母小写。同名接口会明确报错，不会静默覆盖；使用 `@JoinRepository("localUserRepository")`指定唯一名称。重复或重叠扫描路径会自动去重。`JoinRepository`、`JoinCache` 和 `JoinSession` 均从 `com.yulinlin.data.core.anno` 导入；Repository 模块只提供扫描与代理实现。

Repository 接口和方法都可以使用 `@JoinSession`切换数据源，方法注解优先于接口注解：

```java
@JoinRepository
@JoinSession("mysql")
public interface DemoUserRepository extends BaseRepository<DemoUser> {

    // 使用 mysql/master
    DemoUser findByUsernameEq(String username);

    // 仅此方法切换到 postgresql/master，返回后恢复 mysql
    @JoinSession("postgresql")
    List<DemoUser> findByStatusEq(Integer status);

    // 指定同组从库
    @JoinSession(value = "mysql", cluster = JoinCluster.slave)
    List<DemoUser> findByTypeEq(Integer type);
}
```

Repository 必须从 Spring 容器注入后调用，不能自行创建代理。显式 Request group 仍高于注解；最终选中的 group 和 cluster 会进入查询缓存 Key，因此不同数据源不会共用查询缓存。

#### Service 与 Repository 的职责边界

推荐把一个完整的业务事务放在 Service 层：Service 方法使用 `@Transactional`（或框架 `@JoinTransaction`）定义事务边界，需要 setter 自动回写时再同时添加 `@JoinSync`。Repository 主要声明查询方法、`@JoinCache` 缓存策略，以及确实需要的 `@JoinSession` 数据源路由。

```java
@JoinRepository
public interface DemoUserRepository extends BaseRepository<DemoUser> {

    @JoinCache
    DemoUser findByIdEq(String id);
}

@Service
public class DemoUserService {
    private final DemoUserRepository repository;

    public DemoUserService(DemoUserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @JoinSync
    public void rename(String id, String name) {
        DemoUser user = repository.findByIdEq(id);
        if (user == null) throw new IllegalStateException("user not found");
        user.setName(name); // 事务提交前生成部分更新
    }
}
```

`@JoinSync` 只开启查询结果的自动更新策略，不会开启事务。它可用在 Repository 查询方法上，用于精确指定某个查询返回代理对象；但 setter 仍必须发生在外层 Service 持有的原事务内。一般业务更推荐在 Service 统一使用 `@Transactional + @JoinSync`，避免把事务和更新意图分散到多个 Repository。不要只在 Repository 查询方法上开启事务后，在方法返回之外再修改实体；此时原始事务已经结束。只读查询不需要 `@JoinSync`，显式 Wrapper 查询仍可在事务内使用 `.autoUpdate()` 覆盖单次行为。

`@JoinCache` 可以标记 Repository 接口，为其中全部抽象查询方法提供默认缓存配置；方法级注解优先，可覆盖接口配置或用 `CacheMode.NONE` 关闭单个查询。父接口上的类级和方法级配置均可继承：

```java
@JoinRepository
@JoinCache(ttl = 10, unit = TimeUnit.MINUTES)
public interface DemoUserRepository extends BaseRepository<DemoUser> {

    // 继承接口配置：READ_THROUGH，缓存 10 分钟
    DemoUser findByUsernameEq(String username);

    // 明确关闭该查询的缓存
    @JoinCache(mode = CacheMode.NONE)
    List<DemoUser> findByStatusEq(Integer status);

    // 方法配置完整覆盖接口配置：强制查库，TTL 改为 30 秒
    @JoinCache(mode = CacheMode.REFRESH, ttl = 30, unit = TimeUnit.SECONDS)
    DemoUser findByIdEq(String id);

    // 额外把角色表版本纳入 Key，角色表更新后该查询也失效
    @JoinCache(namespaces = "sys_role")
    List<DemoUser> findAll();
}
```

方法级注解是完整替换而不是逐项合并：上例 `REFRESH` 方法不会继承接口的 10 分钟 TTL。`ttl = -1`是默认值，表示使用 `yulinlin.cache.ttl`；显式 TTL 必须为正数。默认 `yulinlin.cache.ttl-in-key=false`，不同 TTL 的相同查询会复用 Key；设为 `true`才按 TTL 隔离。`CACHE_ONLY`只读已有缓存，未命中抛出 `CacheMissException`。未引入缓存 Provider 时，`READ_THROUGH`和`REFRESH`安全退化为查询数据源但不保存，`CACHE_ONLY`仍然报未命中。

接口级配置只影响查询，写方法不会继承它。写方法直接添加 `@JoinCache`、TTL 非法或 namespace 为空，会在 Repository 代理创建阶段失败；已引入缓存 Provider 时，Repository 的 insert/update/delete 按 `yulinlin.cache.clear-on-write` 决定是否在提交成功后清理解析出的表缓存。需要单次覆盖时使用写 Wrapper 或 `ExecuteRequest.clearCache(boolean)`。基础对象在关联代理处理前进入缓存，懒加载和批量预加载会按关联实体单独查询并继承入口缓存模式，因此普通懒加载关系不需要额外声明关联表 namespace；`namespaces` 主要用于无法从查询节点识别全部依赖的自定义 SQL 或非懒加载聚合。

### 使用边界与排障

- 更新通常跳过 null；普通 copyProperties 或自动更新代理的 null 跳过不等于数据库清列。
- insertBefore/updateBefore 可能填充字段，表结构必须匹配实体实际继承字段。
- 查询缓存保存查询结果；标准写操作默认在事务提交成功后清理相关表命名空间，`clearCache(false)` 会关闭单次清理，TTL 是最终兜底。要求事务内读己之写或绝对强一致的查询使用 `CacheMode.NONE`。
- selectOne 返回 null 时先处理“未找到”，不将它解释成解析错误或唯一性保证。
- 日期范围为空先核对列类型、格式、时区与条件，不默认归咎于数据库驱动。
- 表达式、JOIN、JSON 路径、行锁按目标库核对；没有跨库 SQL 自动翻译器。
- NoSuchMethodError 先检查编译与运行时 JAR 是否一致；源码修复不等于已经替换部署产物。

关联增强见 [第三专题](03-关联查询与代理.md)，工具与复制语义见 [第四专题](04-工具类.md)，源码扩展及验收记录见 [第五专题](05-扩展开发与维护.md)。

---

<!-- source: doc/03-关联查询与代理.md -->
## 关联查询与代理

本文说明 JoinQuery、JoinLazy 和事务托管自动更新：如何组装用户角色菜单、批量加载列表关联，以及在事务提交前写回 setter 修改。

阅读导航：[行为速查](#行为速查) · [级联案例](#用户角色菜单级联) · [复杂条件](#复杂查询条件) · [懒加载](#懒加载) · [批量预加载](#列表批量预加载) · [自动更新](#自动更新) · [会话与边界](#会话与代理边界)

接入和实体定义见 [第一专题](01-接入与数据源.md)与 [第二专题](02-CRUD与统计分析.md)。这里是应用侧追加查询和 CGLIB 代理，不是 SQL JOIN，也不是 JPA 的实体管理。

### 行为速查

| 配置/注解 | 加载 | 修改 |
| --- | --- | --- |
| JoinQuery | 查询结果增强时立即查询关联 | 普通对象，不因此自动写库 |
| JoinQuery + JoinLazy | 路由事务内首次访问 getter 时加载 | 仅延迟读取 |
| 查询 `.autoUpdate()` | 主对象与关联对象使用同一层代理 | setter 的非 null 修改在提交前写回 |
| 方法/类 `@JoinSync` | 作用域内所有查询等同 `.autoUpdate()` | 必须同时存在路由事务 |
| `yulinlin.data.auto-update=true` | 全局查询默认自动更新 | 可由单次 `.autoUpdate(false)` 覆盖 |

ORM 查询得到的模型统一由 EntityProxyFactory 增强。一个实体最多只有一层代理；同一拦截器处理 JoinLazy getter 与持久化 setter，不再叠加 LazyProxy/SyncProxy。自己 new 出来的 DTO 仍需调用 RouteSession 的兼容代理入口。

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

#### 复杂查询条件

`wheres` 用于根据当前父对象的多个属性构造关联查询。下面的对象会查询指定视频下、状态可见、创建时间较新且正文包含关键词的前 20 条评论：

```java
package demo.dto;

import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinOrder;
import com.yulinlin.data.core.anno.JoinQuery;
import com.yulinlin.data.core.anno.JoinWhere;
import demo.domain.CommentEntity;
import lombok.Data;

import java.util.List;

@Data
public class VideoCommentDetails {
    private String videoId;
    private List<Integer> visibleStatuses;
    private String keyword;
    private String createdAfter;
    private String createdBefore;

    @JoinQuery(
        wheres = {
            @JoinWhere(name = "videoId", value = "${videoId}"),
            @JoinWhere(
                name = "status",
                value = "${visibleStatuses}",
                condition = ConditionEnum.in
            ),
            @JoinWhere(
                name = "content",
                value = "${keyword}",
                condition = ConditionEnum.like
            ),
            @JoinWhere(
                name = "crtTime",
                value = "${createdAfter}",
                condition = ConditionEnum.gte
            ),
            @JoinWhere(
                name = "crtTime",
                value = "${createdBefore}",
                condition = ConditionEnum.lte
            )
        },
        order = @JoinOrder(name = "crtTime", asc = false),
        size = 20
    )
    private List<CommentEntity> comments;
}
```

创建条件对象并执行关联增强：

```java
VideoCommentDetails details = new VideoCommentDetails();
details.setVideoId("video-1001");
details.setVisibleStatuses(List.of(1, 2));
details.setKeyword("Java");
details.setCreatedAfter("2026-10-01 00:00:00");
details.setCreatedBefore("2026-11-01 00:00:00");

VideoCommentDetails result = SessionUtil.callable("mysql", () ->
        SessionUtil.route().getLazyProxy(details));

List<CommentEntity> comments = result.getComments();
```

该示例等价于以下逻辑条件：

```sql
video_id = ?
AND status IN (?, ?)
AND content LIKE ?
AND crt_time >= ?
AND crt_time <= ?
ORDER BY crt_time DESC
LIMIT 20
```

`name` 写目标实体的 Java 属性名，字段到数据库列名的转换仍由 `@JoinField` 和具体数据源解析器完成。`value = "${...}"` 从当前父对象取值；不带 `${}` 的值会作为固定字符串，例如 `@JoinWhere(name = "type", value = "PUBLIC")`。

值为 `null`、空字符串或空集合的条件会被忽略；如果所有动态条件都没有值，框架返回空关联结果，不会退化成无条件全表查询。`ConditionEnum.in` 的值必须是集合或数组属性，其元素会展开并去重。

当前 `wheres` 中的条件统一按 AND 使用，并且每个父对象单独构造查询。因此列表场景可能产生 N+1，不享受普通 `primary/value` 分支的批量 IN 分配。`JoinWhere.and()` 当前没有接入代理查询；`between` 也不应在 `wheres` 中使用，需要范围条件时分别写 `gte` 和 `lte` 两个父对象属性。

关联计数使用 `model` 指定被统计的实体，字段使用 `Long`：

```java
@JoinQuery(
    model = CommentEntity.class,
    wheres = {
        @JoinWhere(name = "videoId", value = "${videoId}"),
        @JoinWhere(name = "status", value = "${visibleStatuses}", condition = ConditionEnum.in)
    }
)
private Long visibleCommentCount;
```

这里的 `model` 会进入 `count()` 分支，不是用来替代 `List<CommentEntity>` 泛型声明的通用参数。

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

### 自动更新

自动更新捕获事务内的 setter 修改，在提交前统一更新数据库，不是每次 setter 立即执行 SQL，也不是后台线程写入。单次查询开启：

```java
SessionUtil.route().transaction(() ->
        SessionUtil.callable("mysql", () -> {
            DemoUser user = ModelSelectWrapper.newInstance("mysql", DemoUser.class)
                    .autoUpdate()
                    .eq("id", id)
                    .selectOne();
            if (user == null) throw new IllegalStateException("user not found");
            user.setStatus(1);
            return null;
        }));
```

没有路由事务时，`.autoUpdate()` 会在执行 SQL 前提示“请开启事务”。正常提交写回，异常回滚丢弃待更新记录；回滚不会还原 Java 对象的内存值。

全局开启：

```yaml
yulinlin:
  data:
    auto-update: true
```

全局开启后，可更新实体的查询都必须处于事务中。单次查询可用 `.autoUpdate(false)` 明确返回普通对象。

方法或类级入口：

```java
@Transactional
@JoinSync
public void rename(String id, String name) {
    DemoUser user = ModelSelectWrapper.newInstance("mysql", DemoUser.class)
            .eq("id", id)
            .selectOne();
    user.setName(name);
}
```

`@JoinSync` 只开启当前调用作用域的自动更新，不创建事务；需配合 `@Transactional`、`@JoinTransaction` 或 `RouteSession.transaction(...)`。它不再允许标在字段上。

在 Spring 项目中，推荐把 `@Transactional` 和 `@JoinSync` 一起放在 Service 的 public 业务方法上，让查询、setter 修改与提交共享同一个边界。Repository 保持查询声明，通常只配置 `@JoinCache` 和必要的 `@JoinSession`。Repository 方法也可用 `@JoinSync` 精确指定该查询返回代理，但后续 setter 必须仍位于外层 Service 的活动事务内；不建议把它当作默认分层方式。代理对象不能在创建它的事务结束后继续自动更新。只读 Service 不需要 `@JoinSync`。Repository 完整示例见[第二专题](02-CRUD与统计分析.md#service-与-repository-的职责边界)。

主对象的关联查询继承相同策略。懒字段首次 getter 得到的实体或集合元素也是同一层代理，因此在原事务内调用关联实体 setter 同样会写回对应表。

通常直接使用 `.autoUpdate()` 或 `@JoinSync`，不再手动调用 `getSyncProxy`；旧公开方法只用于已有代码迁移。

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
- 未调用的 setter 不产生部分更新，未修改的 0/false/初始化值不会被全部覆盖到数据库；设回原值不会产生更新。
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
## HTTP 与 lang 工具类

本文面向外部项目，说明 HTTP 客户端以及 `lang` 模块全部公开类型。仅使用这些工具不要求定义 ORM 实体；依赖选择见 [第一专题](01-接入与数据源.md#模块选择)。

阅读导航：[依赖](#lang-模块定位) · [HTTP](#http-请求) · [反射与克隆](#反射复制与深克隆) · [JSON](#json-转换) · [日期金额分页](#日期金额与分页) · [缓存资源池](#轻量缓存与资源池) · [并发工具](#并发与调度) · [事件](#进程内事件) · [Lambda 与泛型](#lambda泛型与包扫描) · [完整类型目录](#lang-公开类型目录)

### lang 模块定位

只使用基础工具时可以直接引入：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>lang</artifactId>
    <version>3.0</version>
</dependency>
```

`lang` 不是零依赖 JAR：JSON API 使用 Jackson，注解、泛型扫描与 AOP 扩展使用 Spring/AspectJ。若项目已经引入完整框架，无需重复声明。

本页把 API 分为三类：

- 业务工具：`JsonUtil`、`ReflectionUtil`、`DateTime`、`Page`、`StringUtil` 等，可直接调用。
- 轻量基础设施：`ExpiryMap`、`SmartCache`、`ResourcePool`、事件和线程工具，调用方需要管理生命周期与并发边界。
- 框架扩展 SPI：AOP、JSON 格式器、代理和安全响应接口，只有扩展框架时才实现或直接调用。

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

`JsonUtil` 独立模式有自有 mapper；starter 会用 Spring `ObjectMapper` 替换它。`setMapper` 是进程级全局设置，应在启动阶段完成，不能在并发请求中来回切换。`HttpUtil` 静态客户端的 mapper 是另一套，不自动与 `JsonUtil` 同步。

`YulinlinSimpModule` 自动支持以下值：

| Java 类型 | JSON 形式 |
| --- | --- |
| DateTime | `yyyy-MM-dd HH:mm:ss` 字符串 |
| Money、Coin | JSON 数字 |
| LongAdder、DoubleAdder | 当前累计值 |

手动创建 Jackson mapper 时这样注册：

```java
ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new YulinlinSimpModule());
JsonUtil.setMapper(mapper);
```

`DateTimeStrJsonSerializer` 输出“刚刚、N 分钟前”等相对文本；`EnumSerializer` 输出实现 `IEnum` 的 label。它们没有加入 `YulinlinSimpModule`，必须通过 Jackson 注解或模块显式注册。`EnumFormat` 也是扩展构件，不会自动接管全部枚举。

`JsonUtil.clone` 是保留的 JSON 往返接口，只适合 Jackson 可序列化模型；异构集合、循环引用和对象身份不可靠。通用深克隆优先使用 `ReflectionUtil.deepClone`。

### 日期、金额与分页

#### DateTime

`DateTime` 是基于 `LocalDateTime` 的不可变包装：`plus*`、`with*`、`beginOf*`、`endOf*` 都返回新对象，不修改原值。

```java
DateTime now = DateTime.now();
DateTime tomorrow = now.plusDays(1);
DateTime dayStart = now.beginOfDay();
DateTime dayEnd = now.endOfDay();

DateTime a = DateTime.parse("2026-10-10 12:30:00");
DateTime b = DateTime.parse("20261010123000");
DateTime c = DateTime.parse("2026/10/10", "yyyy/MM/dd");
long millis = a.getTime();
Date legacy = a.toDate();
```

默认解析支持 `yyyy-MM-dd HH:mm:ss`、`yyyyMMddHHmmss`、日期/月/年简写，以及 10 位秒时间戳和 13 位毫秒时间戳。时间戳和 `Date` 转换使用 JVM 默认时区；跨时区协议优先直接使用 `Instant`、`OffsetDateTime`。`isDate` 只是快速形态判断，不是严格日期校验。

#### Money、Coin 与 CodeObject

```java
Money price = Money.of(12.34);
long stored = price.encode();       // 1234
Money decoded = new Money();
decoded.decode(stored);             // 12.34

Coin coin = Coin.of(8.5);
long coinStored = coin.encode();    // 85
```

`Money` 固定两位缩放，`Coin` 固定一位缩放；两者都继承 `Currency<Double>` 并实现 `CodeObject<Long>`。由于内部是 `Double`，不适合要求严格十进制精度、超大金额或复杂舍入的财务场景，这些场景使用 `BigDecimal`。

`CodeObject<T>` 是数据库值编解码约定：`encode` 输出持久化值，`decode` 把持久化值写回当前对象。

#### Page

```java
Page<User> page = Page.of(users, total);
Page<UserDto> dtoPage = page.map(this::toDto);
Page<User> active = page.filter(User::isActive);
List<User> second = Page.page(users, 2, 20);
page.putValue("traceId", traceId);
```

`Page.page` 是从 1 开始的内存分页，不发 SQL，要求 `pageNumber >= 1`、`pageSize > 0`。`map` 遇到 null 会从返回 Page 的 total 中扣除；`filter` 保留原 total，适合“当前页后处理”，不表示重新统计数据库总数。`shuffle`、`random` 返回新 Page，不修改原 list；`randomOne` 不接受空列表。

#### ListString

```java
ListString<Long> ids = new ListString<>();
ids.decode("10,20,30", Long.class);
String value = ids.encode();

ListString<String> tags = ListString.ofString("java,jdk25");
```

默认分隔符是进程级静态字段 `ListString.splitStr`。支持 String、Integer、Long、Double、Byte、Float；内容本身包含分隔符时不能无损往返，也不处理转义。业务优先保持默认逗号，避免运行期间修改全局分隔符。

### 字符串、随机数与 ID

```java
String field = StringUtil.columnToJava("user_name"); // userName
String column = StringUtil.javaToColumn("userName"); // user_name
String text = StringUtil.removeLine(" a\n b\t");     // ab
String rendered = StringUtil.renderString("hi ${name}", Map.of("name", "Lin"));

int value = RandomUtil.randomInt(10); // [0, 10)
char tokenChar = RandomUtil.randomChar();

Snowflake snowflake = new Snowflake(1, 2);
long id = snowflake.nextId();
```

- `StringUtil.isNull` 只判断 null 或空串，不等价于 `isBlank`；`removeLine` 会移除 ASCII 空白。
- `renderString` 是简单 `${key}` 替换，不是表达式引擎；值不能为 null。
- `RandomUtil` 基于 `ThreadLocalRandom`，适合抽样和非安全随机，不能生成密码、token、IV 或密钥。
- `Snowflake` 的 workerId 和 dataCenterId 均为 0～31；同一时间范围内的不同进程必须使用不同组合。`nextId` 是同步线程安全的，时钟大幅回拨会拒绝生成。

#### WeightedSelector

```java
WeightedSelector<String> selector = new WeightedSelector<>();
selector.add("A", 1);
selector.add("B", 3);
String selected = selector.select();
```

权重小于等于 0 会忽略；没有正权重项时 `select` 抛异常。添加操作同步并发布到并发有序表，热点选择路径不使用共享 `Random`，支持并发读取；`list` 返回不可修改视图。

### 轻量缓存与资源池

这些类不替代第八专题的 ORM 查询缓存。它们只管理当前 JVM 内的普通对象。

#### ExpiryMap

```java
try {
    ExpiryMap<String, User> cache = new ExpiryMap<>(
            30, TimeUnit.SECONDS, 20_000, 5,
            expired -> expired.forEach(this::afterRemoval));

    User user = cache.get("u:1", key -> loadUser(key));
    cache.invalidate("u:1");
    cache.shutdown();
} catch (RuntimeException error) {
    // loader 异常原样传播
}
```

TTL 是写入后过期；`randomTtl` 单位为秒，会在基础 TTL 上随机增加 0～N 秒。`get(key, loader)` 对同一 key 使用 `ConcurrentHashMap.compute`，避免同一时刻重复加载。容量是轻量近似上限，不提供 Caffeine 的精确淘汰算法。清理任务使用全局守护调度器；每个实例使用完都调用 `shutdown`，移除回调在框架线程池异步执行。key、value、loader 返回值均不要使用 null。

#### SmartCache

`SmartCache` 是 stale-while-revalidate：首次访问同步加载；值过期后先返回旧值，同时异步刷新。

```java
try (SmartCache<List<User>> cache = SmartCache.of(this::loadUsers, 30, true)) {
    List<User> users = cache.get();
    cache.refresh();       // 强制同步刷新
    cache.refreshAsync();  // 合并并发刷新请求
}
```

`autoRefresh=true` 会周期刷新，实例必须 `close`，否则调度任务会持续引用它；关闭后的实例不能继续 get/refresh。`SmartCache.of(key, ...)` 会在全局表中按 key 复用实例，同 key 第一次创建时的 supplier、TTL 和 autoRefresh 生效；`clearAllCache` 会关闭并移除全部带 key 实例。supplier 返回 null 时不会覆盖现有值。

#### ResourcePool

```java
ResourcePool<Client> pool = new ResourcePool<>(8, Client::new);
Client client = pool.acquire();
try {
    client.call();
} finally {
    pool.release(client); // 每次 acquire 必须且只能 release 一次
}
pool.close(); // 关闭当时空闲的资源
```

继承 `ResourcePool` 并覆盖 `protected close(T)` 可定义资源关闭动作。工厂异常不会泄漏许可证。该类不跟踪借出对象，重复 release 会破坏容量约束；`close` 也不会等待正在使用的对象，复杂连接池优先使用成熟池实现。

### 并发与调度

#### SegmentLock

```java
SegmentLock locks = new SegmentLock(64);
locks.execute(userId, () -> updateUser(userId));
Order order = locks.executeWithReturn(orderId, () -> loadOrder(orderId));
```

相同 key 一定进入同一段，不同 key 可能碰撞到同一锁。段数创建后固定；构造参数必须大于 0。它适合短临界区，不提供跨进程锁。

#### ThreadUtil

```java
Future<Result> ioFuture = ThreadUtil.submit(this::blockingHttpOrJdbcCall);
Future<Result> explicitIo = ThreadUtil.submitVirtual(this::blockingHttpOrJdbcCall);
Future<Result> cpuFuture = ThreadUtil.submitCpu(this::calculate);
ThreadUtil.submit(this::refresh, 500);       // 延迟约 500 ms 执行一次
ScheduledFuture<?> task = ThreadUtil.schedule(this::blockingPoll, 1_000);
ScheduledFuture<?> cpuTask = ThreadUtil.scheduleCpu(this::calculate, 1_000);

ThreadUtil.set("trace", traceId);
try {
    useTrace(ThreadUtil.get("trace"));
} finally {
    ThreadUtil.clear();
}
```

`ThreadUtil.submit` 默认使用 JDK 25 一任务一虚拟线程；`submitVirtual` 是语义更明确的同义入口，适合 HTTP、文件、网络、等待锁等阻塞任务。CPU 密集计算或必须由线程池限制并发的任务使用 `submitCpu`，其平台线程数按可用处理器数量固定。虚拟线程不会让单次计算更快，也不会自动限制数据库连接或远端接口并发，因此 ORM 批量写入仍使用 Session 的连接并发配置。

`schedule` 与 `scheduleVirtual` 都由轻量调度器触发、在虚拟线程执行，并在上一次尚未完成时跳过重叠执行；CPU 定时任务使用 `scheduleCpu`。`submitVirtual(task, delay)` 是一次性延迟虚拟任务。`startThread` 使用虚拟线程循环执行，回调返回 `false`、线程被中断时停止。ThreadLocal 数据必须在 finally 中 clear。Spring 应用有复杂隔离、监控或优雅停机要求时仍可注入自己的 `TaskExecutor`/`TaskScheduler`。

### 进程内事件

```java
final class UserCreated { }

final class UserCreatedHandler implements IEventHandler<UserCreated> {
    public void handle(UserCreated event) { /* ... */ }
}

EventPublishManager.instance().register(new UserCreatedHandler());
EventPublishManager.instance().publish(new UserCreated());
EventPublishManager.instance().asyncPublish(new UserCreated());
```

`IEvent` 提供 `publish`/`asyncPublish` 默认方法。处理器按事件的精确运行时 Class 分组，不向父类或接口传播；重复 register 会重复执行。注册表支持并发读写，适合启动期间注册、运行期间高频发布。同步发布的异常直接中断后续处理器；异步发布通过 `ThreadUtil` 使用虚拟线程，不返回 Future，调用方无法等待，也不会自动汇总错误。需要可靠投递、持久化、重试或跨进程通信时使用消息队列。

自定义隔离实例可以继承 `AbstractEventPublishManager<MyManager>`；全局默认实例是 `EventPublishManager.instance()`。

### Lambda、泛型与包扫描

#### LambdaUtils

```java
LambdaPropertyFunction<User> property = User::getUsername;
String method = LambdaUtils.lambdaMethodName(property);          // getUsername
Field field = LambdaUtils.lambdaMethodNameToField(property);     // username
Class<User> owner = LambdaUtils.lambdaMethodNameToClassName(property);
```

参数必须是可序列化 Lambda，通常使用 `LambdaPropertyFunction<T>`。不要传普通 `Function`、匿名内部类或任意对象。实现只缓存 lambda class 的 `writeReplace` 访问器，不缓存捕获参数，因此不会把首个捕获对象长期保留。

#### GenericUtil

```java
Class<?> entityType = GenericUtil.forClass(UserRepositoryImpl.class, BaseRepository.class, 0);
Class<?> elementType = GenericUtil.forField(User.class.getDeclaredField("roles"), 0);
Class<?> returnType = GenericUtil.forMethodReturnType(method, 0);
Class<?> parameterType = GenericUtil.forMethodParameter(method, 0, 0);
```

解析不到具体类型时返回 null。`GenericUtil` 只在首次调用时进入 Spring `ResolvableType`，之后按源/声明类使用 `ClassValue` 缓存类、字段、方法参数和返回值的最终类型；解析失败的 null 也会缓存。类卸载时其缓存可一起回收，测试或动态类替换可调用 `clearCache(Class<?>)` 或 `clearCache()`。

`BaseGeneric`、`GenericClass`、`GenericField`、`GenericMethod` 是旧的链式泛型模型，仍为兼容保留；新外部代码优先用 `GenericUtil`。`GenericClass.newInstance` 同样使用 `ClassValue` 缓存，不阻止类加载器卸载。

#### AnnotationUtil 与 ScanUtils

```java
JoinTable table = AnnotationUtil.findAnnotation(User.class, JoinTable.class);
Marker marker = AnnotationUtil.findAnnotation(method, Marker.class, 0); // 第 0 个参数上的注解

Set<Class<?>> services = ScanUtils.scanSubclass(
        "com.example.**.service", BaseService.class, true);
```

`AnnotationUtil` 使用 Spring 合成注解规则；AspectJ 重载用于切面内部。`ScanUtils.scanClass(path, false)` 返回具体类，传 true 返回接口或抽象类；扫描会读取 class 元数据并加载匹配 Class，但不会初始化静态代码。通配路径依赖 Spring resource pattern，库/应用边界上优先传明确根包，避免扫描整个 classpath。

### 序列化、元组与框架扩展

#### Java 序列化

```java
byte[] bytes = SerializableUtil.serialize(value);
Object restored = SerializableUtil.unSerialize(bytes);
SerializableType copy = SerializableUtil.clone(value);
```

整个对象图必须实现 `Serializable`。Java 原生序列化通常比专用编码慢、体积大，且反序列化不可信字节存在安全风险；只处理本进程生成且可信的数据，不作为 HTTP、消息队列或跨语言协议。普通克隆使用 `ReflectionUtil.deepClone`。

#### 元组

```java
MutablePair<String, Integer> pair = MutablePair.of("count", 3);
String left = pair.getLeft();

MutableTriple<String, Integer, Boolean> triple = MutableTriple.of("task", 1, true);
```

这些类是轻量位置容器，没有值语义的 equals/hashCode，也没有公开修改器。跨层 API、缓存 key 或领域模型优先定义 record/明确 DTO。

#### 框架扩展 SPI

| 类型 | 用途 | 外部使用建议 |
| --- | --- | --- |
| BaseAnnotationBeanPostProcessor | 基于类/方法注解创建 Spring Advisor | 仅扩展框架 AOP；实现 getAnnotation 和 invoke |
| MethodHandlesUtil | privateLookupIn 和父接口 default/special 方法句柄 | 代理框架内部使用；受 JPMS opens 限制 |
| ProxyUtil | Spring CGLIB 子类代理 | JDK 25 优先接口/JDK Proxy；final 类和模块边界受限 |
| ReflectAsmUtil | 旧反射入口 | 已弃用，转用 ReflectionUtil；clear 是兼容空操作 |
| DateTimeJsonFormat、MoneyJsonFormat、CoinJsonFormat | Jackson serializer/deserializer 工厂 | 通常直接注册 YulinlinSimpModule |
| LongAdderJsonFormat、DoubleAdderJsonFormat | Adder JSON 数值格式 | 通常直接注册 YulinlinSimpModule |
| DateTimeStrJsonSerializer、EnumSerializer、EnumFormat | 可选展示/枚举格式 | 必须显式配置，不会由默认模块自动启用 |
| IEnum | label/value 枚举约定 | ORM EnumCoder 会优先持久化 value |
| CryptoDataResponse | 响应 data 可被安全模块替换的契约 | starter 的 R 已实现；普通项目按需实现 |

### lang 公开类型目录

下面按包列出当前全部公开类型，便于外部 AI 判断准确入口。未列为首选的兼容类型也没有删除。

| 包 | 公开类型 | 定位 |
| --- | --- | --- |
| `lang.aop` | BaseAnnotationBeanPostProcessor、AnnotationAdvisor | Spring AOP 扩展 SPI |
| `lang.cache` | ExpiryMap、ResourcePool | JVM 轻量过期缓存、资源池 |
| `lang.enums` | IEnum | 枚举 label/value 契约 |
| `lang.event` | AbstractEventPublishManager、EventPublishManager、IEvent、IEventHandler | JVM 内同步/异步事件 |
| `lang.json` | JsonUtil、YulinlinSimpModule、DateTimeJsonFormat、DateTimeStrJsonSerializer、MoneyJsonFormat、CoinJsonFormat、LongAdderJsonFormat、DoubleAdderJsonFormat、EnumFormat、EnumSerializer | Jackson 转换与扩展 |
| `lang.lambda` | LambdaPropertyFunction、LambdaUtils | 可序列化属性方法引用解析 |
| `lang.lazy` | SmartCache | 首次同步、过期异步刷新的单值缓存 |
| `lang.reflection` | ReflectionUtil、GenericUtil、AnnotationUtil、SerializableUtil、MethodHandlesUtil、ProxyUtil、ReflectAsmUtil | 反射、泛型、注解、序列化和代理 |
| `lang.security` | CryptoDataResponse | security 模块响应加密 SPI |
| `lang.tuple` | Triple、MutablePair、MutableTriple | 轻量位置容器 |
| `lang.util` | DateTime、Page、StringUtil、RandomUtil、Snowflake、WeightedSelector、SegmentLock、ThreadUtil、ListString、CodeObject、Currency、Money、Coin、ClassUtil、ScanUtils、BaseGeneric、GenericClass、GenericField、GenericMethod | 通用值、并发、扫描和兼容泛型工具 |

`DeepCopies`、`ValueCopies`、`HandleAccess` 是包内实现，不是外部 API。

### 常见问题

| 问题 | 优先检查 |
| --- | --- |
| ReflectionUtil.property 的 NoSuchMethodError | 编译与运行时 lang/core 是否同一产物 |
| DTO 复制字段缺失 | 名称、可写属性、null 跳过及嵌套类型是否相容 |
| 克隆列表后共享引用不同 | 整体克隆还是每元素独立调用 |
| SmartCache 过期仍拿到旧值 | 这是 stale-while-revalidate；首次加载才阻塞 |
| ExpiryMap 实例不再使用 | 调用 shutdown 取消该实例清理任务 |
| ResourcePool 后续 acquire 卡住 | 检查每次借出是否恰好 release 一次 |
| Lambda 解析失败 | 参数是否为 LambdaPropertyFunction/其他可序列化 Lambda |
| DateTime 时间戳偏移 | JVM 默认时区是否符合业务协议 |
| Java 反序列化是否能处理请求数据 | 不要反序列化不可信输入 |
| HTTP 超时配置没生效 | Spring 客户端、静态客户端、手动构造和单次覆盖分别核对 |
| 下载成功但 body 为空 | downloadTo 内容在文件中 |
| 404 检查返回 false | 不代表存在，需区分网络/状态异常 |

本文按当前源码和 JDK 25 行为核对；HTTP 地址、文件路径及业务类型是示例，需要替换。工具类是进程内能力，不自动提供分布式一致性、持久化、重试或安全随机。

---

<!-- source: doc/06-接口安全.md -->
## 接口安全

`security` 模块为 Spring MVC JSON 接口提供请求体解密和响应体加密。默认协议是 `AES/CBC/PKCS5Padding + UTF-8 key + UTF-8 IV + 标准 Base64`；每种设备可以使用独立密钥，也可以单独切换为 AES-GCM。

阅读顺序：[完整配置](#完整主配置) · [配置规则](#配置项与默认值) · [接口注解](#接口注解) · [CBC 前端对接](#默认-cbc-前端对接) · [排除路径](#明文排除路径) · [GCM](#可选-aes-gcm) · [手动 API](#手动使用) · [安全边界](#安全边界与限制)

### 引入模块

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>security</artifactId>
    <version>3.0</version>
</dependency>
```

模块不依赖 Spring Security，也不要求引入 ORM 或数据库驱动。引入后自动注册设备密钥管理器、请求解密 Advice 和响应加密 Advice；缺少默认设备、密钥或 IV 时启动失败，不生成临时密钥，也不会静默退化成明文。

### 完整主配置

正式项目推荐使用多设备配置。下面完整展示设备请求头、默认设备、明文路径、请求大小以及 Web、Android、iOS 独立密钥：

```yaml
yulinlin:
  security:
    crypto:
      # true：严格加密；false：兼容明文/密文请求，响应统一明文
      enabled: true

      # 客户端通过该请求头选择设备密钥
      device-header: Device-Type

      # 未携带 Device-Type 时使用；必须存在于 devices 中
      default-device: web

      # 请求和响应都跳过加解密
      # 自定义该列表会替换默认值，因此需要保留 /monitor/**
      exclude-paths:
        - /monitor/**
        - /actuator/health
        - /public/**
        - /upload/**
        - /download/**

      # 同时限制收到的密文和解密后的 JSON
      max-request-size: 1MB

      devices:
        web:
          algorithm: AES/CBC/PKCS5Padding
          key: ${YULINLIN_SECURITY_WEB_KEY}
          iv: ${YULINLIN_SECURITY_WEB_IV}

        android:
          algorithm: AES/CBC/PKCS5Padding
          key: ${YULINLIN_SECURITY_ANDROID_KEY}
          iv: ${YULINLIN_SECURITY_ANDROID_IV}

        ios:
          algorithm: AES/CBC/PKCS5Padding
          key: ${YULINLIN_SECURITY_IOS_KEY}
          iv: ${YULINLIN_SECURITY_IOS_IV}
```

环境变量示例：

```properties
YULINLIN_SECURITY_WEB_KEY=MIGfMA0GCSqGSIb3
YULINLIN_SECURITY_WEB_IV=5efd3f6060e20765

YULINLIN_SECURITY_ANDROID_KEY=1234567890abcdef
YULINLIN_SECURITY_ANDROID_IV=abcdef1234567890

YULINLIN_SECURITY_IOS_KEY=fedcba0987654321
YULINLIN_SECURITY_IOS_IV=0123456789abcdef
```

示例值仅用于说明长度。生产密钥必须由环境变量或密钥管理系统提供，不能提交到仓库。

### 配置项与默认值

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否启用严格加密；关闭后注解接口接受明文或密文，响应统一明文 |
| `device-header` | `Device-Type` | 客户端设备类型请求头；同时回写到加密响应 |
| `default-device` | `web` | 请求未传设备头时使用的设备 |
| `exclude-paths` | `/monitor/**` | 同时跳过请求解密和响应加密的 Spring 路径模式 |
| `max-request-size` | `1MB` | 密文与解密后正文的大小上限 |
| `devices.<name>.algorithm` | `AES/CBC/PKCS5Padding` | 当前设备的 AES 算法 |
| `devices.<name>.key` | 无 | 必填；CBC 接受 16、24 或 32 字节 |
| `devices.<name>.iv` | 无 | CBC 必填且必须是 16 字节 |
| `devices.<name>.key-encoding` | `UTF8` | 默认字符密钥不需要填写；GCM 示例使用 `BASE64` |
| `devices.<name>.iv-encoding` | `UTF8` | 默认字符 IV 不需要填写 |

设备名会去除首尾空白并转换为小写，只允许小写字母、数字、下划线和短横线，最大长度为 32。`default-device` 必须能在 `devices` 中找到；未知 `Device-Type` 返回 400。

CBC 参数必须满足：

```text
algorithm = AES/CBC/PKCS5Padding
key       = 16、24 或 32 字节
iv        = 16 字节
encoding  = UTF-8
cipher    = 标准 Base64
padding   = PKCS5/PKCS7
```

建议 key 和 IV 使用 ASCII 字符，避免中文 UTF-8 字符占多个字节。Java 的 `PKCS5Padding` 与常见 JavaScript AES 实现的 `Pkcs7` 在 AES 分组下兼容。

### 接口注解

需要加密的业务接口使用 `@ApiCrypto`。它可以放在 Controller 类或具体方法上，方法配置优先于类配置。全局 `enabled` 控制严格加密模式，但不会删除请求解密能力：

| 全局配置 | 接口注解 | 请求 | 响应 |
| --- | --- | --- | --- |
| `enabled=true` | `@ApiCrypto` / `@ApiCrypto(true)` | 必须传整个 JSON 加密后的 Base64 密文 | 加密 |
| `enabled=true` | `@ApiCrypto(false)` | 普通 JSON 原样读取；非 JSON body 仍尝试解密 | 明文 |
| `enabled=false` | `@ApiCrypto(true/false)` | 普通 JSON 或整个 body 密文都支持 | 明文 |
| 任意值 | 没有注解 | security 完全不处理 | 明文 |
| 任意值 | 命中 `exclude-paths` | security 完全不处理 | 明文 |

`enabled=false` 是兼容模式，不是卸载安全模块。设备密钥管理器和请求 Advice 仍会注册，key 与 IV 仍然必须配置，以便继续解密尚未升级的旧客户端；此时 `@ApiCrypto(true)` 不再拒绝明文请求，响应也不会加密。已经发送密文的客户端和已经切换明文的客户端可以并行迁移。

业务 JSON 接口推荐统一使用 `@PostMapping + @RequestBody`。请求 Advice 只处理 body，不加密 query 参数和 path 变量。

```java
package demo.api;

import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.starter.domain.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @ApiCrypto
    @PostMapping("/api/user/detail")
    public R<UserVO> detail(@RequestBody UserDetailPo request) {
        return R.ok(userService.detail(request.getId()));
    }

    @ApiCrypto(false)
    @PostMapping("/api/user/public-detail")
    public R<UserVO> publicDetail(@RequestBody UserDetailPo request) {
        return R.ok(userService.detail(request.getId()));
    }
}
```

`@ApiCrypto(false)` 是兼容模式，不是完全关闭请求处理。框架把 body 转成 UTF-8 字符串并去除首尾空白：以 `{` 或 `[` 开头时直接交给 Jackson；其他内容按当前设备的 Base64 密文解密。它不会为了判断明文而预解析 JSON，因此明文请求只反序列化一次。错误 Base64、未知设备或解密失败返回 400，不会再次降级为明文。

#### 响应加密规则

- 普通 Java 对象：整个对象序列化后加密，响应 JSON 是一个 Base64 字符串。
- `R<E>`：只加密非 null 的 `data`，保留 `code`、`msg`、`ok` 和 `timestamp`，并设置 `crypt=true`。
- `R.data == null`：不生成无意义密文，`crypt` 保持 `false`。
- `ResponseVo<E>`：仅保留废弃兼容，行为与 `R<E>` 相同。

`R<E>` 加密响应示例：

```json
{
  "code": 200,
  "msg": "操作成功",
  "ok": true,
  "crypt": true,
  "data": "mFbzvANmZJZQj/3nB4zKpA==",
  "timestamp": 1792070400000
}
```

### 默认 CBC 前端对接

请求仍使用 `Content-Type: application/json`。推荐 body 直接发送未经 JSON 双引号包裹的标准 Base64 密文；为兼容 Axios 的默认 JSON 转换，服务端也接受只包裹一层 JSON 双引号的 Base64 字符串：

```http
POST /api/user/detail HTTP/1.1
Content-Type: application/json
Device-Type: web

N4H0Qf4H0m4YqQPG4Q3XWg==
```

以下 Axios 实际报文也能兼容，服务端会先移除最外层双引号再进行 Base64 解码：

```text
"N4H0Qf4H0m4YqQPG4Q3XWg=="
```

服务端加密响应会返回实际设备和算法：

```http
Device-Type: web
X-Yulinlin-Crypto: AES/CBC/PKCS5Padding
```

下面使用浏览器原生 Web Crypto API，与默认 CBC、UTF-8 key/IV 和标准 Base64 对齐。Web Crypto 的 AES-CBC 使用 PKCS7 填充，与服务端 `PKCS5Padding` 兼容：

```javascript
const encoder = new TextEncoder();
const decoder = new TextDecoder();

function bytesToBase64(bytes) {
  let binary = "";
  for (const value of new Uint8Array(bytes)) binary += String.fromCharCode(value);
  return btoa(binary);
}

function base64ToBytes(value) {
  return Uint8Array.from(atob(value), char => char.charCodeAt(0));
}

async function importCbcKey(keyText) {
  const bytes = encoder.encode(keyText);
  if (![16, 24, 32].includes(bytes.length)) throw new Error("AES key length is invalid");
  return crypto.subtle.importKey("raw", bytes, {name: "AES-CBC"}, false,
      ["encrypt", "decrypt"]);
}

function cbcIv(ivText) {
  const bytes = encoder.encode(ivText);
  if (bytes.length !== 16) throw new Error("AES-CBC IV must contain 16 bytes");
  return bytes;
}

async function encryptJson(value, keyText, ivText) {
  const key = await importCbcKey(keyText);
  const encrypted = await crypto.subtle.encrypt(
      {name: "AES-CBC", iv: cbcIv(ivText)},
      key,
      encoder.encode(JSON.stringify(value)));
  return bytesToBase64(encrypted);
}

async function decryptJson(ciphertext, keyText, ivText) {
  const key = await importCbcKey(keyText);
  const plaintext = await crypto.subtle.decrypt(
      {name: "AES-CBC", iv: cbcIv(ivText)},
      key,
      base64ToBytes(ciphertext.trim()));
  return JSON.parse(decoder.decode(plaintext));
}

const deviceType = "web";
const key = "MIGfMA0GCSqGSIb3";
const iv = "5efd3f6060e20765";
const body = await encryptJson({id: "U1001"}, key, iv);

const response = await fetch("/api/user/detail", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "Device-Type": deviceType
  },
  body
});

const responseBody = await response.json();
const result = responseBody?.crypt === true
    ? {...responseBody, data: await decryptJson(responseBody.data, key, iv)}
    : typeof responseBody === "string"
        ? await decryptJson(responseBody, key, iv)
        : responseBody;
```

跨域调用时，业务 CORS 配置必须允许请求头 `Device-Type`，并暴露响应头 `Device-Type` 和 `X-Yulinlin-Crypto`。密钥不得直接硬编码在公开源码中；上述浏览器代码只用于说明协议对接。

### 多设备选择

请求头决定当前使用哪组设备密钥：

```http
Device-Type: web
Device-Type: android
Device-Type: ios
```

没有请求头时使用 `default-device`。响应回写解析后的设备类型，客户端应优先读取响应 `Device-Type`，再选择对应密钥解密。设备请求头只负责选密钥，不是身份认证信息，不能仅凭该值判断调用方可信。

### 明文排除路径

`exclude-paths` 是服务端控制的完整排除列表，支持 Spring 路径模式。命中后，即使接口存在 `@ApiCrypto(true)`，请求体也不解密，响应体也不加密：

```yaml
yulinlin:
  security:
    crypto:
      exclude-paths:
        - /monitor/**
        - /actuator/health
        - /public/**
```

默认列表只有 `/monitor/**`。配置新列表会替换默认列表，因此使用监控模块时必须继续保留它。不要提供由客户端控制的“跳过加密”请求头，否则业务接口可能被绕过。

文件上传、文件下载、视频流、SSE、WebSocket、静态资源和健康检查不适合 JSON 加密 Advice，应保持无 `@ApiCrypto`，必要时加入排除路径。

### 可选 AES-GCM

支付、订单等需要密文完整性校验的设备可以显式切换到 GCM：

```yaml
yulinlin:
  security:
    crypto:
      default-device: web
      devices:
        web:
          key: ${YULINLIN_SECURITY_WEB_KEY}
          iv: ${YULINLIN_SECURITY_WEB_IV}
        ios:
          algorithm: AES/GCM/NoPadding
          key-encoding: BASE64
          key: ${YULINLIN_SECURITY_IOS_KEY}
```

GCM 要求 Base64 解码后恰好为 32 字节的密钥，不允许配置固定 `iv`。框架每次生成 12 字节随机 IV，Base64 解码后的报文结构为：

```text
12字节随机IV + 密文 + 16字节认证标签
```

GCM 使用以下 UTF-8 内容作为 AAD，客户端必须完全一致：

```text
1\n<规范化设备类型>\nA256GCM
```

例如 web 设备的 AAD 是 `1\nweb\nA256GCM`。密文、设备名、IV、认证标签或密钥不匹配都会导致解密失败；这是 GCM 相比默认固定 IV CBC 的主要安全优势。

### 单设备配置

只有一种设备时，可以不写 `devices`。根级 `key`/`iv` 与 `devices` 不能同时配置：

```yaml
yulinlin:
  security:
    crypto:
      enabled: true
      device-header: Device-Type
      default-device: web
      algorithm: AES/CBC/PKCS5Padding
      key: ${YULINLIN_SECURITY_KEY}
      iv: ${YULINLIN_SECURITY_IV}
      exclude-paths:
        - /monitor/**
      max-request-size: 1MB
```

### 手动使用

需要显式选择设备时，注入线程安全的 `DeviceCryptoManager`：

```java
import com.yulinlin.security.crypto.DeviceCryptoManager;

public class SecureValueService {
    private final DeviceCryptoManager cryptoManager;

    public SecureValueService(DeviceCryptoManager cryptoManager) {
        this.cryptoManager = cryptoManager;
    }

    public String encrypt(String deviceType, String value) {
        return cryptoManager.getRequired(deviceType).encryptBase64(value);
    }

    public String decrypt(String deviceType, String ciphertext) {
        return cryptoManager.getRequired(deviceType).decryptBase64ToString(ciphertext);
    }
}
```

自动配置还提供一个使用默认设备的 `AesCrypto` Bean。脱离 Spring 时可以直接创建：

```java
AesCrypto cbc = new AesCbcCrypto(keyBytes, ivBytes);
AesCrypto gcm = new AesGcmCrypto(keyBytes, "web");
```

实现每次操作创建独立 `Cipher`，可以被多个请求线程共享。

### 安全边界与限制

- 当前只支持 Spring MVC，不支持 WebFlux。
- 生产环境仍必须使用 HTTPS；接口加密不能替代 TLS。
- CBC 使用固定 IV，不能可靠发现密文篡改；高敏感业务选择 GCM。
- 浏览器必须持有共享 AES 密钥，因此网页脚本、浏览器扩展和自动化浏览器最终都能读取它。该能力只能提高普通采集脚本的成本，不能单独承担防爬虫职责。
- AES 不是密码哈希，用户密码必须使用专用密码哈希算法保存。
- 自动 Advice 只适合 JSON 请求和 JSON/字符串响应，不适合 multipart、文件流、视频流、SSE 或 WebSocket。
- 日志不得记录密钥、IV、解密后的敏感正文或完整密文。
- 当前没有实现时间戳、nonce 防重放和密钥轮换，业务不能假设已经具备这些能力。

本专题于 2026-10-15 按当前源码重写。本轮未运行测试、编译或打包；接入时应使用真实客户端验证密钥编码、Padding、Base64、CORS 和异常响应约定。

---

<!-- source: doc/08-查询缓存.md -->
## 查询缓存

查询缓存是可选能力。`cache-core` 提供缓存 SPI、`CacheClient`、Key/TTL/namespace 等公共协议和无缓存兜底；ORM `core` 只负责从 INode、Wrapper 和实体元数据构造查询 Key、判断失效范围。不引入具体缓存 Provider 时，应用照常启动，标记为缓存的查询会直接访问数据源。

### 公共配置

```yaml
yulinlin:
  cache:
    ttl: 10m
    ttl-in-key: false
    clear-on-write: true
```

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `ttl` | `10m` | 查询缓存默认有效期 |
| `ttl-in-key` | `false` | TTL 是否参与 ORM 查询缓存 Key；不影响 `CacheClient` 业务缓存 |
| `clear-on-write` | `true` | 成功写入后是否默认清理相关 ORM 查询缓存 |

`yulinlin.cache.clear-on-write`默认 `true`，控制写操作未显式选择时是否在提交成功后清理相关 ORM 查询缓存。它不控制查询是否启用缓存，也不会清理 `CacheClient` 业务缓存。`QueryCacheProperties` 只由 `cache-caffeine`、`cache-ehcache` 或 `cache-redis` 的自动配置启用；未引入 Provider 时不存在查询缓存，该配置不会生效，写路径也不执行 namespace 解析和失效处理。

默认关闭时，只要查询条件、数据源和 namespace 版本相同，不同 TTL 的查询就会复用同一个缓存条目。已有条目被命中时不会修改其过期时间；`REFRESH`或未命中重新写入时，才使用本次请求的 TTL。这样可以减少重复数据并提高缓存复用率。

需要让不同 TTL 的相同查询完全隔离时开启：

```yaml
yulinlin:
  cache:
    ttl-in-key: true
```

开启后，解析出的默认 TTL 或请求级 TTL 会参与物理 Key，同一查询使用不同 TTL 时会保存为不同条目。namespace 名称和版本始终参与 Key，不受此开关影响。

### 选择实现

只引入一个实现，不要同时引入多个。三个实现都会传递引入 `cache-core`，业务不需要重复声明；`cache-redis` 直接包含自己的 Caffeine L1，不依赖 `cache-caffeine` Provider。

#### Caffeine 内存缓存

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>cache-caffeine</artifactId>
    <version>3.0</version>
</dependency>
```

调用链为 `Caffeine → 数据源`。它适合追求最低本地延迟、允许应用重启后缓存消失的服务。

```yaml
yulinlin:
  cache:
    ttl: 10m
    ttl-in-key: false
    caffeine:
      initial-capacity: 128
      maximum-size: 10000
      expiration-policy: after-write
      use-system-scheduler: false
      executor: common-pool
      record-stats: false
```

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `initial-capacity` | `16` | Caffeine 内部表的初始容量；只影响扩容次数，不会一次性创建全部缓存对象 |
| `maximum-size` | `10000` | 最大条目数；ORM 查询缓存与 `CacheClient` 业务缓存共用该容量 |
| `expiration-policy` | `after-write` | `after-write` 写入后固定 TTL；`after-access` 每次命中后按该条目的 TTL 续期 |
| `use-system-scheduler` | `false` | 使用 JDK 系统调度器更及时地触发过期维护；关闭时由正常读写维护，过期项仍不会被命中 |
| `executor` | `common-pool` | `common-pool` 异步执行维护任务；`direct` 在调用线程执行，少一次调度但可能增加请求延迟 |
| `record-stats` | `false` | 是否记录命中率、淘汰数和加载统计；需要观测时显式开启，默认关闭以减少热点路径计数开销 |

`expiration-policy`同时作用于 ORM 查询缓存和 `CacheClient`。请求级 `cache(Duration)` 或 `CacheClient.set(..., Duration)` 仍决定各条目的 TTL，策略只决定读取命中后是否重新开始计时。大多数接口服务建议保留 `after-write + common-pool`；会话类热点数据才考虑 `after-access`。

需要监控时先配置 `record-stats: true`，再直接注入具体实现：

```java
@Resource
private CaffeineQueryCache caffeineQueryCache;

long entries = caffeineQueryCache.estimatedSize();
CacheStats stats = caffeineQueryCache.stats();
caffeineQueryCache.cleanUp(); // 主动执行待处理的过期和容量淘汰维护
```

#### Ehcache 持久化缓存

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>cache-ehcache</artifactId>
    <version>3.0</version>
</dependency>
```

调用链为 `Ehcache Heap → Ehcache Disk → 数据源`。Ehcache 自己管理内存热点层和磁盘层，不需要再引入 Caffeine。

```yaml
yulinlin:
  cache:
    ttl: 10m
    ttl-in-key: false
    ehcache:
      directory: ./data/cache
      heap-entries: 10000
      offheap-size-mb: 0
      disk-size-mb: 1024
      persistent: true
      expiration-policy: after-write
      disk-threads: 2
      serializer: json
      maximum-entry-size-mb: 16
      record-statistics: false
```

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `directory` | `data/cache` | 磁盘缓存目录，同一时间只能由一个 CacheManager 占用 |
| `heap-entries` | `10000` | JVM 堆内热点条目数，ORM 查询与 `CacheClient` 共用 |
| `offheap-size-mb` | `0` | JVM 直接内存中的可选中间层；`0` 表示关闭。可降低大缓存对 GC 的影响，速度通常快于磁盘，但会增加序列化、内存复制和进程总内存占用 |
| `disk-size-mb` | `1024` | 整个 Ehcache 磁盘层的容量上限，不是单条缓存上限；空间满后淘汰旧条目。Ehcache 磁盘资源池要求设置有限容量，不提供无限增长模式 |
| `persistent` | `true` | 是否在正常关闭并重启后保留磁盘缓存 |
| `expiration-policy` | `after-write` | `after-write` 写入后固定 TTL；`after-access` 每次命中后按该条目的 TTL 续期 |
| `disk-threads` | `2` | Ehcache 磁盘层固定工作线程数；本地 SSD 通常使用 `2` 到 `4` |
| `serializer` | `json` | Value 格式：`json`、`smile` 或高吞吐的 `fory` |
| `maximum-entry-size-mb` | `16` | 单条序列化结果上限，超出后记录并跳过，防止一个大列表挤占缓存 |
| `record-statistics` | `false` | 是否记录框架层命中、未命中、写入、超大条目和序列化失败计数；需要观测时显式开启 |

正常关闭时框架调用 `close()`。即使 `persistent: true`，缓存仍应被视为可丢失数据；缓存损坏或反序列化失败会删除对应项并回源查询。缓存量不大时保持 `offheap-size-mb: 0` 即可；只有缓存较大或 GC 压力明显时才建议从 `256` 或 `512` MB 开始启用。容器内存预算必须同时包含 Heap、Off-Heap、线程栈和 JVM 自身开销。

Ehcache 的业务 Value 统一保存为带序列化格式和 TTL 的原始 `byte[]`，Heap、Off-Heap、Disk 不再额外保存一个 Java 包装对象。格式信息随条目保存，因此切换 `json`、`smile` 或 `fory` 后仍可读取尚未过期的其他格式条目。

需要监控时先配置 `record-statistics: true`，再直接注入具体实现读取和重置统计：

```java
@Resource
private EhcacheQueryCache ehcacheQueryCache;

EhcacheQueryCache.Stats stats = ehcacheQueryCache.stats();
ehcacheQueryCache.resetStatistics();
```

#### Redis 共享缓存与可选 Caffeine L1

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>cache-redis</artifactId>
    <version>3.0</version>
</dependency>
```

模块使用 Spring Boot 默认的 Lettuce 和 `RedisConnectionFactory`，不需要业务代码创建 `RedisClient`。默认调用链为 `Caffeine L1 → Redis L2 → 数据源`；关闭 L1 后为 `Redis → 数据源`。

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379

yulinlin:
  cache:
    ttl: 10m
    ttl-in-key: false
    clear-on-write: true
    redis:
      key-prefix: video:prod
      write-mode: async
      batch-size: 128
      serializer: smile
      maximum-entry-size-mb: 16
      record-statistics: false
      caffeine:
        enabled: true
        maximum-size: 10000
```

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `key-prefix` | `dev` | 隔离不同系统、环境和版本的 Redis Key；默认用于本地开发，生产环境建议显式设置为“项目名:环境” |
| `write-mode` | `async` | `async` 先写 L1，再由虚拟线程写 Redis；`sync` 在当前线程完成 Redis 写入 |
| `batch-size` | `128` | 一次 Redis Pipeline 最多提交多少条；收到第一条任务就立即提交当前已有任务，不会等队列凑满 128 条 |
| `serializer` | `smile` | Redis Value 格式：`smile`、`json` 或高吞吐的 `fory`；JSON 系列使用 Jackson Blackbird |
| `maximum-entry-size-mb` | `16` | 单条序列化结果上限；超出后跳过缓存，避免大列表占满 Redis 和异步队列 |
| `record-statistics` | `false` | 是否记录 L1/Redis 命中、未命中、写入失败和异步丢弃统计；默认关闭热点计数 |
| `caffeine.enabled` | `true` | 是否启用进程内 L1；关闭后每次缓存读取和版本判断都访问 Redis |
| `caffeine.maximum-size` | `10000` | L1 最大条目数，ORM 查询与 `CacheClient` 共用 |

`async`只用于写入普通缓存值。删除、namespace 失效、单条件分区失效和清空操作始终同步写 Redis，避免旧缓存继续可达。后台线程取到第一条任务后立即 `drainTo` 当时已经存在的任务，并使用 Pipeline 批量发送；`batch-size`是单批上限，不是等待阈值。异步队列满时仅跳过该条 Redis L2 写入，查询仍可回源，不做重试。

`caffeine.enabled=true` 适合单实例或允许一个 L1 TTL 内短暂不一致的业务。每个节点的 L1 和本地版本快照互不广播，其他节点更新数据后，本节点可能继续命中旧 L1，直到条目过期。需要多个实例都立即依据 Redis 版本读取时设置为 `false`；本模块不引入 Pub/Sub、分布式锁或强一致协议。

L1 与 Redis 使用同一个解析后的 TTL，但 Redis 命中后重新放入 L1 会从当前时间重新计算该 TTL，因此 L1 最晚可能比 Redis 条目多存活一个 TTL。单机加速场景可以接受；需要严格以 Redis 过期时间为准时关闭 L1。

Redis 中保存的是 Smile/JSON/Fory 原始字节，不做 Base64。缓存值带有版本、序列化格式和 TTL 信封，因此修改 `serializer` 后仍能读取未过期的旧格式条目。使用 `smile` 或 `json` 时，实体和业务缓存对象必须符合 Jackson 反序列化要求。

#### 序列化格式选择

| 格式 | 适合场景 | 特点 |
| --- | --- | --- |
| `json` | 兼容性、可排障性优先 | 默认用于 Ehcache；文本可读，类升级和跨版本排障最直观 |
| `smile` | 性能、体积与兼容性的平衡 | Redis 默认；二进制 JSON，仍复用项目 Jackson 配置 |
| `fory` | 大列表、复杂对象、高频缓存 | Java 原生对象图二进制格式，通常吞吐更高、分配更少；必须把缓存存储视为可信边界 |

Fory 运行时由 Provider 单例复用，并启用异步编译，不会为每次 `get/set` 创建实例。框架无法预先知道业务实体类型，因此使用动态类型模式；不要让用户提交的任意字节直接进入 Redis 或 Ehcache 磁盘目录。读取失败时，框架会删除损坏条目并回源数据源。

JDK 25 使用 Fory 时需要加入 JVM 参数。普通 classpath 应用：

```text
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED
```

使用 module-path 时改为：

```text
--add-opens=java.base/java.lang.invoke=org.apache.fory.core
```

只使用默认 `json/smile` 时不需要该参数，Fory 运行时也不会初始化。启用示例：

```yaml
yulinlin:
  cache:
    ehcache:
      serializer: fory
    # 使用 cache-redis 时改为：
    # redis:
    #   serializer: fory
```

需要观测时开启统计并注入具体实现：

```java
@Resource
private RedisQueryCache redisQueryCache;

RedisQueryCache.Stats stats = redisQueryCache.stats();
long localEntries = redisQueryCache.estimatedLocalSize();
redisQueryCache.resetStatistics();
```

### 启用查询缓存

`cache()` 不传参数时使用系统默认值 `yulinlin.cache.ttl`，默认 10 分钟；`cache(Duration)` 只覆盖当前查询：

```java
List<SysUser> users = ModelSelectWrapper
        .newInstance("mysql", SysUser.class)
        .cache() // 使用 yulinlin.cache.ttl
        .where(where -> where.eq("status", 1))
        .selectList();

List<SysUser> shortLived = ModelSelectWrapper
        .newInstance("mysql", SysUser.class)
        .cache(Duration.ofSeconds(30))
        .where(where -> where.eq("status", 1))
        .selectList();
```

自定义 SQL 同样支持缓存：

```java
QueryRequest<SysUser> request = QueryRequest.newInstance(
        "select * from sys_user where status = #{status}",
        Map.of("status", 1),
        SysUser.class
);
request.setSession("mysql");
request.cache(); // 默认 TTL；也可 cache(Duration.ofSeconds(30))
List<SysUser> users = request.selectList();
```

列表、分页内部的 count、group、统计结果和空列表都可以进入缓存。查询异常不会缓存。级联查询、批量预加载和懒加载会继承入口查询的缓存模式与请求级 TTL。

同一条查询使用不同 TTL 时会生成不同物理 Key，互不覆盖。默认 `after-write` 下读取不会续期；Provider 配置为 `after-access` 时，命中会按该条目的 TTL 重新计时。

### CacheMode

`CacheMode`明确控制一次查询是否读缓存、访问数据源和写缓存：

| 模式 | 读缓存 | 未命中访问数据源 | 写缓存 |
| --- | --- | --- | --- |
| `NONE` | 否 | 是 | 否 |
| `CACHE_ONLY` | 是 | 否 | 否 |
| `READ_THROUGH` | 是 | 是 | 是 |
| `REFRESH` | 否 | 是 | 是 |

```java
// cache() 等价于 READ_THROUGH
List<SysUser> cached = ModelSelectWrapper.newInstance("mysql", SysUser.class)
        .cache(CacheMode.READ_THROUGH)
        .selectList();

// 只允许读取已有缓存；未命中抛出 CacheMissException，绝不查询数据库
List<SysUser> offline = ModelSelectWrapper.newInstance("mysql", SysUser.class)
        .cache(CacheMode.CACHE_ONLY)
        .selectList();

// 强制查询数据库并覆盖缓存
List<SysUser> refreshed = ModelSelectWrapper.newInstance("mysql", SysUser.class)
        .cache(CacheMode.REFRESH, Duration.ofMinutes(5))
        .selectList();
```

未调用 `cache(...)` 时为 `NONE`。没有引入缓存 Provider 时，`READ_THROUGH`和`REFRESH`仍能查询数据源，但不会保存结果；`CACHE_ONLY`一定抛出 `CacheMissException`。

### 业务缓存 CacheClient

框架始终暴露一个可注入的 `CacheClient` Bean。它与 ORM 查询缓存共用所选 Provider，但使用独立的 Key 和版本域；数据库写操作不会误删验证码、接口结果等业务缓存。

```java
@Resource
private CacheClient cacheClient;

cacheClient.set("user", "10001", user); // 使用系统默认 TTL
cacheClient.set("user", "10002", user, Duration.ofMinutes(30));

SysUser user = cacheClient.get("user", "10001", SysUser.class);
boolean exists = cacheClient.exists("user", "10001");
cacheClient.remove("user", "10001");
cacheClient.invalidate("user"); // 整个业务命名空间立即失效
cacheClient.clear();             // 只清理业务缓存，不清理 ORM 查询缓存
```

集合等泛型数据使用 Jackson `TypeReference`：

```java
List<SysUser> users = cacheClient.get(
        "user-list", "enabled",
        new TypeReference<List<SysUser>>() {}
);
```

`getOrLoad`在同一 Key 回源时使用分段锁和二次检查：

```java
SysUser user = cacheClient.getOrLoad(
        "user", userId, SysUser.class, Duration.ofMinutes(10),
        () -> userService.findById(userId)
);
```

业务缓存不接受空 namespace、空 key 或 `set(..., null)`。未引入缓存模块时，`available()`返回 false，读取为未命中，写入和失效操作安全地退化为空操作。

### Key 规则

Key 在 `core` 中统一生成，不依赖某一种数据库最终 SQL：

```text
MurmurHash3-x64-128(
    版本
  + 路由后的 group/cluster
  + Session 类型
  + entityClass/fromClass
  + 查询类型
  + 完整 INode 元数据
  + 实际 TTL
  + 全局版本与依赖表版本
)
```

元数据直接流入128位哈希器，不构建完整中间字符串。`CacheKey` 在内存中只保存两个 `long`；Caffeine 直接使用该对象作为 Key，Ehcache/Redis 持久化或日志输出时才按需生成 `v2:` 开头的32位十六进制文本。实体反射字段通过 `ClassValue` 缓存，不会在每次查询时重新扫描、排序。

`CommandNode` 的 SQL、参数名、参数类型和值都属于 INode 元数据。因此相同 SQL 的不同参数不会共用结果；MySQL、PostgreSQL、SQLite、H2、MongoDB、Elasticsearch 以及第三方 `AbstractSession` 实现都使用同一规则。Map 参数按键排序，参数插入顺序不会造成无意义的 Key 差异。

缓存命中时不会访问数据源，因此数据库模块的 `log=true` 不会打印 SQL。排障时可将 `com.yulinlin.data.cache.QueryCache` 调整为 DEBUG，查看 `HIT`、`MISS`、`REFRESH`、等待后命中以及事务/行锁绕过等事件；日志只记录哈希 Key，不记录缓存值。需要确认物理 SQL 时使用 `CacheMode.REFRESH` 或暂时关闭该次查询缓存。

分页内部的 `count` 使用独立的逻辑 Key：普通查询只保留 `from/join + where`，因此不同页码、页大小、排序、返回字段、高亮和行锁会复用同一个总数；聚合查询还会保留 `aggregations + metrics + having`，避免不同分组统计误命中。普通列表 Key 仍然包含分页和排序。未知的第三方 Wrapper 不做字段裁剪，继续使用完整节点生成 Key，以正确性优先。

数据源 group 必须进入 Key。同一个实体和条件在 `mysql`、`pgsql` 或其他 group 中不会串缓存；同一个逻辑 group 下的负载均衡节点可以共享逻辑 Key。

### 失效和一致性

缓存始终受 TTL 约束。引入缓存 Provider 后，标准 `insert/update/delete` 未指定策略时继承 `yulinlin.cache.clear-on-write`，默认在提交成功后清理相关查询缓存：

```java
int affected = ModelUpdateWrapper
        .newInstance("mysql", patch)
        .execute();

int deleted = ModelDeleteWrapper
        .newInstance("mysql", SysUser.class)
        .eq("status", 0)
        .clearCache(false) // 明确接受 TTL 窗口内的旧结果时，关闭本次清理
        .execute();
```

写操作只提供一个请求级 API：

| API | 行为 |
| --- | --- |
| 不调用 `clearCache(...)` | 继承 Provider 配置 `yulinlin.cache.clear-on-write` |
| `clearCache(true)` | 本次写入提交成功后清理相关查询缓存，覆盖全局 `false` |
| `clearCache(false)` | 本次写入不清理查询缓存，覆盖全局 `true` |

#### 单条件自动分区

分区失效是缓存 Provider 的内置能力，始终自动开启，不需要配置，也没有额外 API。只有已经调用 `cache(...)` 或使用 `@JoinCache` 的查询才会进入缓存；它不会让普通查询自动启用缓存。

当同一数据源、同一物理表的缓存查询始终使用同一个单字段等值条件时，框架会按条件值划分缓存：

```java
List<SysUser> alice = ModelSelectWrapper.newInstance("mysql", SysUser.class)
        .eq("username", "Alice")
        .cache()
        .selectList();
```

例如当前结构是 `username = ?`，更新或删除条件同样为 `username = 'Alice'` 时，提交后只让 `Alice` 分区失效，`Bob` 分区仍可命中。普通列表、分页、Count、聚合等缓存仍按表失效，不会因为分区优化而保留旧结果。

最小识别规则如下：

- 查询必须是单表、非行锁，并且有效条件只有一个 `Eq`；框架工厂产生的空 `And/Or` 包装会被忽略。
- 条件字段只识别普通列名或当前表已验证的别名前缀；JSON 路径、函数、引号字段和未知前缀直接按整表处理。
- 字段名先走实体的 `JoinField`/驼峰映射，条件值先走当前 Session 的 Coder；映射或编码结果无法安全识别时同样按整表处理。
- 更新、删除使用相同字段的单个 `Eq` 时精准失效对应值。
- 更新同时修改该条件字段、插入、自定义 SQL、多条件或无法解析的写入，会让该表当前全部分区失效。
- 如果缓存查询又使用第二个等值字段，例如先有 `username = ?`、后来又有 `id = ?`，该物理表自动升级为普通整表失效模式。
- 状态按 `Session 类型 + group + 物理表` 隔离；同表对应多个实体类时共享同一判断。

分区 Key 仍保留完整查询信息，因此不同分页、排序、投影和 TTL 不会错误命中；分区版本只负责让同一个条件值下的这些条目一起失效。没有引入缓存模块，或第三方 Provider 未实现分区能力时，会安全回退为现有整表失效。

实体写入会从 `INode`、From/Join/Store、`@JoinTable` 和 `@JoinTableList` 提取相关表。自定义 SQL 没有实体或节点表信息时，框架无法可靠猜测涉及的表；启用清理时会退化为清理全部 ORM 查询缓存并输出警告：

```java
ExecuteRequest<Object> write = ExecuteRequest.newInstance(
        "update sys_user set status=#{status} where id=#{id}",
        Map.of("status", 1, "id", 7)
);
write.setSession("mysql");
int affected = write.clearCache(true).execute();
```

自定义查询若没有带 `@JoinTable` 的 `fromClass`，可用 `cacheNamespaces("sys_user", "sys_role")` 声明依赖表。这样任一表失效时，该复杂 SQL、JOIN 或聚合结果都会同时失效。

延迟写队列在真正写入成功后才清理，不在入队时清理。相同 `Session + 操作类型 + fromClass` 的请求合并时使用三态规则：任一请求显式 `true`，合并组就是 `true`；全部显式 `false`，合并组才是 `false`；包含未指定且没有 `true` 时保持未指定，执行时继承全局配置。

失效采用“命名空间版本号”，不会遍历百万级 Key：普通查询 Key 携带 `Session 类型 + group + 表名` 的版本快照；单条件分区查询携带表策略版本和对应条件值版本。写入成功后只递增相关版本，旧 Key 立即不可达，随后按自身 TTL 自然回收。Ehcache 的普通 namespace 版本会持久化；分区条目在进程重启后使用新的运行期版本，不会重新命中重启前的旧分区结果。Redis 会把普通版本和分区版本都保存在共享 Redis 元数据中；关闭 L1 时，各节点每次都读取共享版本。

事务规则：

- 普通单次写入在底层 Session 提交成功后按 `clearCache` 策略清理。
- `RouteSession` 多数据源事务会合并重复表名，只在最外层提交成功后统一清理。
- 多数据源提交中途失败、结果无法确定时会保守清理待处理缓存；最多多一次回源，不保留可能已提交数据的旧缓存。
- Spring 事务在 `afterCommit` 阶段清理；回滚或提交失败不清理。
- 活动事务内的查询直接访问数据源，不读取也不写入全局查询缓存，防止未提交结果在回滚后残留；事务内使用 `CACHE_ONLY` 会直接报告缓存不可用。
- `lock()` 行锁查询始终访问数据库，不会因为 `cache()` 命中而跳过加锁。
- 查询回源期间若并发发生更新，旧查询只能写入旧版本 Key，不能重新污染新版本缓存。

即使有主动失效，也应把 TTL 设置为业务可接受的最大兜底陈旧时间。事务外要求绝对强一致的查询使用 `CacheMode.NONE`。

### 并发与对象边界

同一个 Key 回源时使用分段锁和二次检查，降低缓存到期瞬间的重复查询。缓存保存查询增强前的数据；返回给业务前会复制模型并创建当前调用需要的统一实体代理，旧事务代理不会被保存复用。

Ehcache 默认使用 JSON 字节保存结果，Redis 默认使用 Smile 字节保存结果；选择 JSON/Smile 时实体应符合 Jackson 的反序列化要求。连接、流、Statement、ResultSet 等资源对象不能作为缓存查询结果。

### 启动规则

| 项目依赖 | 启动结果 |
| --- | --- |
| 不引入缓存模块 | 正常启动；`CacheClient.available()`为 false，缓存优先查询直接访问数据源 |
| 只引入 `cache-caffeine` | 启用内存缓存 |
| 只引入 `cache-ehcache` | 启用 Heap + Disk 缓存 |
| 只引入 `cache-redis` | 启用 Redis L2；默认同时启用模块内置 Caffeine L1 |
| 同时引入多个实现 | 启动失败并报告多个缓存 Provider |

第一种情况下不会创建 `QueryCacheProperties`，也不会因 `clearCache(true)` 解析表 namespace。这不是“空缓存执行一遍失效”，而是写路径直接跳过整套缓存失效逻辑。

手动注册新的 Session 时，`RouteSession.registerSession` 会把当前缓存 Provider 注入 Session。第三方数据源继承 `AbstractSession` 即可获得相同行为；直接实现 `EntitySession` 时，需要实现 `setQueryCache` 才能接入查询缓存。

---

<!-- source: doc/09-项目开发最佳实践.md -->
## Common 业务开发与项目最佳实践

本文先说明 `common` 提供给业务代码直接复用的实体、分页查询模型和 Service 接口，再给出推荐的 Spring Boot 项目结构与完整业务案例。它负责回答“已有公共能力怎么用、业务代码应该放在哪里”；SQL 条件的完整解析规则仍以[CRUD 与统计](02-CRUD与统计分析.md)、[关联查询与代理](03-关联查询与代理.md)和[查询缓存](08-查询缓存.md)为准。

阅读导航：[核心规则](#核心规则) · [目录结构](#推荐目录结构) · [公共业务模型](#公共业务模型) · [基础实体](#基础实体选择) · [通用 Service](#通用-service-接口) · [完整案例](#完整业务案例) · [事务与代理](#事务与自动更新) · [缓存](#缓存规范) · [日志](#日志规范) · [多数据源](#多数据源规范) · [批量写入](#批量写入规范) · [反例](#常见反例) · [评审清单](#上线前评审清单)

### 核心规则

| 层次 | 应该负责 | 不应负责 |
| --- | --- | --- |
| Controller | HTTP 参数校验、身份与响应 DTO | 组装 Wrapper、开启事务、返回可变持久化实体 |
| Service | 业务规则、事务边界、自动更新作用域、结果组装 | 依赖 Controller 或吞掉异常 |
| Repository | 声明式查询和标准 CRUD，可选查询缓存与数据源路由 | 业务事务、跨多个 Repository 的流程 |
| Entity | 表映射、字段与索引元数据 | Controller 响应协议和与表无关的业务状态 |
| DTO/VO | 请求、响应、JoinQuery 投影或统计结果 | 拥有表结构；不要与完整 Entity 同时 `autoSchema=true` |

默认决策：

1. 事务标在 Spring 管理的 Service public 方法上。
2. 需要 setter 自动回写时，在同一个 Service 方法上组合 `@Transactional + @JoinSync`。
3. Repository 默认不承担业务事务；缓存只标记查询方法。
4. 一般查询返回 DTO；需要修改的代理 Entity 不逃离原始线程和事务。
5. 多数据源时明确 group；不依赖 Bean 注册顺序或 `primary` 名称。
6. 查询缓存是可丢失的加速层，不是事务一致性或业务状态存储。
7. 写入必须校验主键或条件，并检查影响行数；不假设“调用没报错”等于写入成功。
8. 同一物理表只有一个完整 Entity 拥有自动 Schema；字段改名、改类型和数据搬迁使用版本化脚本。
9. 默认开启驼峰转下划线映射；标准映射不要重复填写 `@JoinField.name`。

例如下面的属性会自动映射到数据库列 `base_pushes`：

```java
@JoinField(description = "基础推荐量")
private long basePushes;
```

`basePushes -> base_pushes`、`tenantId -> tenant_id`、`crtTime -> crt_time` 都属于标准映射。只有物理列名无法由属性名按该规则得到时，才显式填写 `name`，例如属性 `username` 对应历史列 `user_name`；如果关闭某个数据源的 `map-underscore-to-camel-case`，则以该数据源的实际映射规则为准。

### 推荐目录结构

按业务域组织代码，不要把整个项目的 Controller、Service、Entity 分成三个巨型目录：

```text
com.example.app
├── user
│   ├── api
│   │   ├── UserController.java
│   │   └── UserResponse.java
│   ├── domain
│   │   └── UserEntity.java
│   ├── repository
│   │   └── UserRepository.java
│   └── service
│       └── UserService.java
├── video
│   └── ...
└── shared
    ├── config
    └── error
```

Repository 扫描和 Schema 扫描是两件事：`yulinlin.repository.scan-packages` 找接口，`yulinlin.<module>.schema-packages` 找显式 `autoSchema=true` 的完整实体。两者都可按业务包递归扫描。

### 公共业务模型

框架把常见的页面查询、分页结果和 Web 响应模型集中提供给业务项目，避免每个模块重复声明 `pageNumber`、`pageSize`、排序、ID 和列表等字段。它们是对外公共 API，不是 ORM 内部临时对象。

| 类型 | 所属模块 | 用途 |
| --- | --- | --- |
| `PagePo` | `common` | 页面查询入参基类，统一分页、排序和运行时追加条件 |
| `OrderItem` | `common` | 一个排序字段及升降序 |
| `PageVo<E>` | `common` | 兼容已有业务的分页响应类型，继承 `Page<E>` |
| `Page<E>` | `lang` | ORM 和 Service 共用的分页结果，包含当前页列表、总数和扩展值 |
| `IdPo` | `starter` | Web 接口的单个字符串 ID 入参 |
| `ListPo<E>` | `starter` | Web 接口的通用列表入参 |
| `R<E>` / `ResponseVo<E>` | `starter` | 标准 Web 响应体；新代码使用 `R`，`ResponseVo`仅保留废弃兼容写法 |

这些类型按使用的门面模块引入。只使用 ORM 的项目可直接使用 `PagePo`和 `Page`，不必为了 `IdPo`或 `R`引入 Web Controller。它们也不要求实现 Java `Serializable`，HTTP JSON 仍由 Spring/Jackson 处理。

#### 页面查询对象：`PagePo + @JoinWhere`

页面列表查询推荐为每个业务页面定义一个很薄的 `XxxPo extends PagePo`。父类统一携带分页和排序，子类只声明本页面的业务条件：

```java
package com.example.app.pay.api;

import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.data.core.anno.ConditionEnum;
import com.yulinlin.data.core.anno.JoinWhere;
import com.yulinlin.data.lang.util.DateTime;
import lombok.Data;

import java.util.List;

@Data
public class PayChannelPo extends PagePo {

    @JoinWhere(condition = ConditionEnum.eq)
    private String platformId;

    @JoinWhere(condition = ConditionEnum.eq)
    private String status;

    @JoinWhere(condition = ConditionEnum.between)
    private List<DateTime> crtTime;
}
```

Controller 只接收业务查询对象，Service 复用 `ISuperService.page(PagePo)`：

```java
@PostMapping("/page")
public R<Page<PayChannelEntity>> page(@RequestBody PayChannelPo po) {
    return R.newInstance(payChannelService.page(po));
}
```

请求可以同时携带业务条件、分页和排序：

```json
{
  "pageNumber": 1,
  "pageSize": 20,
  "platformId": "web",
  "status": "enabled",
  "crtTime": ["2026-10-01 00:00:00", "2026-10-15 23:59:59"],
  "orderList": [
    { "name": "crtTime", "asc": false }
  ]
}
```

`ISuperService.page(po)`的核心过程是先把查询对象交给 Wrapper，再应用分页和排序（接口实现还会合并 `PagePo.and/or`中的运行时条件）：

```java
Page<PayChannelEntity> result = ModelSelectWrapper
        .newInstance(PayChannelEntity.class, po)
        .page(po.getPageNumber(), po.getPageSize())
        .orderBy(order -> {
            if (po.getOrderList() != null) {
                for (OrderItem item : po.getOrderList()) {
                    order.orderBy(item.getName(), item.isAsc());
                }
            }
        })
        .selectPage();
```

查询对象解析遵循以下规则：

- 只有带 `@JoinWhere` 且实际提供值的子类字段生成 WHERE；`pageNumber`、`pageSize`和 `orderList`没有该注解，不会成为查询条件。
- `@JoinWhere.name`省略时使用查询对象的 Java 属性名；查询属性与实体属性不同时显式填写实体属性名。
- `between`传入按“起始、结束”排列的两个值，`in`传集合；字段类型继续使用框架现有 SQL 编解码规则。
- `PagePo.and(...)`和 `or(...)`用于运行时才知道字段或操作符的补充条件，不是普通页面查询的必写步骤。
- `orderList.name`应使用实体 Java 属性名。直接接收前端排序时，业务层应限制允许排序的属性，避免任意字段参与查询。
- 复杂嵌套 AND/OR、子查询或数据库专用表达式继续使用 Wrapper，不要把所有 SQL 逻辑塞进 PO。

不通过 `ISuperService`时，也可以直接把同一个查询对象传给指定数据源：

```java
Page<PayChannelEntity> result = ModelSelectWrapper
        .newInstance("mysql", PayChannelEntity.class, po)
        .page(po.getPageNumber(), po.getPageSize())
        .selectPage();
```

`PayChannelEntity.class`决定表、返回字段和结果类型，`PayChannelPo`只描述 WHERE、分页和排序输入。查询 PO 不需要 `@JoinTable`，也不应开启自动 Schema。`@JoinWhere`支持的操作符、空值和 AND/OR 精确语义见[查询条件对象](02-CRUD与统计分析.md#查询条件对象)。

#### 统一 Controller 响应：`R<E>`

普通 JSON Controller 统一使用 `@PostMapping + @RequestBody`并显式返回 `R<E>`。查询、详情、创建、修改和删除都把参数放进一个 JSON 请求体，启用 `@ApiCrypto`后才能对整个 body 使用同一套明文判定和解密流程；框架不会通过全局 Advice 猜测并自动包装任意返回对象。

```java
@PostMapping("/page")
public R<Page<PayChannelEntity>> page(@RequestBody PayChannelPo po) {
    return R.ok(payChannelService.page(po));
}

@PostMapping("/delete")
public R<Void> delete(@RequestBody IdPo po) {
    payChannelService.delete(po.getId());
    return R.ok();
}
```

标准工厂方法：

| 方法 | 结果 |
| --- | --- |
| `R.ok()` | 成功且没有响应数据 |
| `R.ok(data)` | 成功并返回数据 |
| `R.ok(data, message)` | 成功并返回数据和提示 |
| `R.fail(message)` | 默认 `500`失败响应 |
| `R.fail(code, message)` | 指定错误码的失败响应 |

成功响应默认 `code=200`、`ok=true`；失败工厂始终设置 `ok=false`。每个新响应都会写入当前 `timestamp`，`crypt`默认 false。旧的 `R.newInstance(...)`和 `ResponseVo<E>`暂时保留二进制/源码兼容，但均已废弃，新业务不要继续使用。

`starter`默认注册最低优先级的异常处理器：参数绑定或 JSON 格式错误返回 `400`；`ApiException`携带 `400–599`标准 HTTP 错误码时使用该状态，其他数值按 `400`处理；`ResponseStatusException`保留 HTTP 状态；未识别异常记录完整服务端日志并返回 `500 + 系统繁忙，请稍后重试`。错误 HTTP 状态和 `R.code`保持一致；业务项目可以提供更高优先级的 `@RestControllerAdvice`覆盖细分规则。不要把 SQL、文件路径、密钥或异常堆栈写进客户端 `msg`。

启用 `@ApiCrypto`且返回 `R`时，只替换并加密 `data`，随后设置 `crypt=true`；`code`、`ok`、`msg`和 `timestamp`保持明文。`data == null`时跳过加密并保持 `crypt=false`，错误提示因此无需客户端先解密。详细协议见[接口安全](06-接口安全.md)。

业务 JSON 接口不要用 `@GetMapping`、`@PutMapping`、`@PatchMapping`或 `@DeleteMapping`承载加密参数，也不要把敏感条件放在 query/path 中。监控页面、静态资源、健康检查、文件下载、图片/视频/音频流、SSE、重定向以及直接写 `HttpServletResponse`的接口不受这条约束；流式响应不使用 `R`包装，也不进入面向 JSON 的 `@ApiCrypto`响应处理。

### 基础实体选择

`common` 模块提供了几个可复用实体。它们不只是字段集合，还实现了 `AbstractModel` 的 CRUD 便捷入口和写入前生命周期，因此应按表结构选择，不要为了少写字段而继承。

| 类型 | 持久化字段 | 自动行为 | 适用场景 |
| --- | --- | --- | --- |
| 普通 POJO | 自行定义 | 无 | 数据库自增主键、`Long`/UUID 主键、投影结果或非标准表 |
| `IdEntity<E>` | `String id` | 插入前在 `id` 为 null 或空串时生成 Snowflake 字符串 ID | 大多数由应用生成字符串主键的业务表 |
| `SuperEntity<E>` | `id`、`crtTime`、`uptTime` | 包含 `IdEntity` 行为，插入时补齐空的创建/更新时间 | 同时具有主键和审计时间列的表 |
| `TreeEntity<E>` | `id`、`crtTime`、`uptTime`、`parentId`、`parentPath` | 默认根节点 ID 为 `"0"`，`selectTree()` 可按 `ITreeNode` 组装 `children` | 菜单、部门和分类等树表 |
| `IncrementEntity` | 动态表名、列名和主键条件 | 使用 `UpdateTypeEnum.inc` 生成原子增量更新 | 计数器类的专用更新载体，不作为业务实体父类 |

#### `IdEntity`

```java
@JoinTable(value = "sys_user", autoSchema = true)
public class UserEntity extends IdEntity<UserEntity> {
    @JoinField(textLength = 64, description = "登录用户名")
    private String username;

    public UserEntity() { }
    // getter/setter
}
```

`id` 已声明为主键和默认条件字段，子类不要再次定义同名字段。插入前的 `insertBefore()` 会保留业务已设置的 ID，仅在未设置时调用 `generateId()`。需要其他应用侧 ID 策略时可在子类重写 `generateId()`；如果主键由数据库自增、序列或触发器产生，应改用普通 POJO 并显式定义主键。

`nextIncrId()` 会用表中当前最大 ID 初始化进程内计数器，之后在本进程递增。它不是数据库自增 ID，也不保证多实例唯一；分布式业务保持默认 Snowflake 策略或接入统一 ID 服务。

#### `SuperEntity`

```java
@JoinTable(value = "video_comment", autoSchema = true)
public class CommentEntity extends SuperEntity<CommentEntity> {
    @JoinField(description = "视频 ID")
    private String videoId;

    @JoinField(textType = TextTypeEnum.text, description = "评论内容")
    private String content;

    public CommentEntity() { }
    // getter/setter
}
```

`crtTime` 和 `uptTime` 的 Java 类型都是框架 `DateTime`，按驼峰转下划线规则对应 `crt_time` 和 `upt_time`。只有物理表确实具有这两列时才继承 `SuperEntity`；自动 Schema 也会把继承字段当作真实列处理。

当前 `updateBefore()` 只在 `uptTime == null` 时填入当前时间。如果业务要求每次更新都刷新时间，应在写入前显式 `setUptTime(DateTime.now())`，或在项目自定义父类中重写 `updateBefore()`，不要把“非空时保留原值”误解为无条件自动刷新。

#### `TreeEntity`

```java
@JoinTable(value = "sys_menu", autoSchema = true)
public class MenuEntity extends TreeEntity<MenuEntity> {
    @JoinField(textLength = 64, description = "菜单名称")
    private String name;

    public MenuEntity() { }
    // getter/setter
}
```

`children` 已标记 `@JoinField(exist = false)`，只用于内存树结构，不会成为表列。`parentId` 和 `parentPath` 是真实列；新节点没有指定父节点时使用全局根值 `"0"`。如果项目需要其他根值，应在启动配置阶段统一设置，不要在请求中反复修改这个静态值。

`ModelSelectWrapper.selectTree()` 会将查到的 `ITreeNode` 列表在内存中组装为树；普通 `selectList()` 仍返回扁平列表。`findChildren()` 和 `findParent()` 是使用默认 group 的便捷方法；在多数据源项目中，优先在 Service/Repository 明确路由后查询，避免树节点自身隐式选库。

#### 自定义项目基类

只有多张表真正共享同一列语义时才增加项目基类，例如所有业务表都具有租户 ID：

```java
public abstract class TenantEntity<E extends TenantEntity<E>> extends SuperEntity<E> {
    @JoinField(textLength = 64, description = "租户 ID")
    private String tenantId;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
```

实体的泛型参数始终传具体类，例如 `UserEntity extends IdEntity<UserEntity>`。不要在子类重复声明 `id`、`crtTime`、`uptTime`、`parentId` 或 `parentPath`；重名字段会让反射映射、Schema 比对和更新条件变得不确定。与表无关的计算属性仍必须显式使用 `@JoinField(exist = false)`。

### 通用 Service 接口

`common` 模块通过接口默认方法提供无状态 CRUD 和树服务能力。新项目直接 `implements ISuperService<E>` 或 `implements ITreeService<E>`，不再占用业务 Service 的类继承位。`impl.ServiceImpl/TreeImpl` 与 `impl2` 只作为已废弃兼容层。

| 接口/实现 | 用途 | 关键能力 |
| --- | --- | --- |
| `ISuperService<E>` | 通用 CRUD 契约和默认实现 | 按 ID 查询、新增、更新、删除、保存、分页 |
| `ServiceImpl<E>` | 已废弃兼容类 | 保留旧 `extends ServiceImpl` 写法和 `dataGroup()` 扩展点 |
| `ITreeService<E>` | 树业务契约和默认实现 | 维护父路径、移动子树、删除后代、内存组树 |
| `TreeImpl<E>` | 已废弃兼容类 | 保留旧 `extends TreeImpl` 写法 |

#### `ISuperService` 用法

```java
package com.example.app.user.service;

import com.example.app.user.domain.UserEntity;
import com.yulinlin.common.service.ISuperService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserCrudService implements ISuperService<UserEntity> {

    @Transactional(readOnly = true)
    public UserEntity detail(String id) {
        return findById(id);
    }

    @Transactional
    public int create(UserEntity user) {
        return insertOne(user);
    }

    @Transactional
    public int change(UserEntity user) {
        return updateOne(user);
    }
}
```

Service 接口默认方法不会注册 Spring Bean，也不会自动开启事务。具体实现类仍必须使用 `@Service`，并由业务入口定义事务边界。实现这个接口不等于自动获得事务、缓存或数据源路由。

常用方法语义：

| 方法 | 行为 |
| --- | --- |
| `findById(id)` / `findByIdIn(ids)` | 按实体主键查询 |
| `all()` / `page(PagePo)` | 查询全部或动态分页 |
| `insertOne/list` | 明确新增，会执行实体 `insertBefore()` |
| `updateOne/list` | 明确按主键更新，会执行 `updateBefore()` |
| `deleteById/IdIn` | 明确按主键删除 |
| `save/saveList` | 先查询主键是否存在，再将对象分为 insert/update 两组 |
| `newInstance()` | 通过反射创建实体实例 |

`save()` 不是数据库原生 UPSERT：它会先查后写，有额外往返，且并发时仍要依赖唯一索引处理竞态。业务明确知道是新增还是更新时，直接调用 `insert*` 或 `update*` 性能更好，语义也更清楚。

`page(PagePo)`会解析业务子类上的 `@JoinWhere`字段，再应用 `PagePo`自带的动态条件、分页和排序；完整页面写法见[公共业务模型](#页面查询对象pagepo--joinwhere)。不要把前端传入的排序字段名无限制透传；Controller/Service 应使用允许列表转换为可排序的实体属性。

#### `ITreeService` 用法

```java
package com.example.app.menu.service;

import com.example.app.menu.domain.MenuEntity;
import com.yulinlin.common.service.ITreeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MenuService implements ITreeService<MenuEntity> {

    @Transactional(readOnly = true)
    public List<MenuEntity> tree(MenuQuery query) {
        return getTree(query);
    }

    @Transactional
    public int saveMenu(MenuEntity menu) {
        return save(menu);
    }

    @Transactional
    public int removeMenu(String id) {
        return deleteById(id);
    }
}
```

`ITreeService` 继承全部通用 CRUD，并增加以下约定：

1. `save()` 会根据 `parentPath` 最后一项推导 `parentId`，空路径表示根节点。
2. `updateOne()` 在父节点变化时重新计算后代节点的 `parentPath`。
3. `deleteByIdIn()` 会同时删除 `parentPath` 匹配的后代节点，这是级联删除，调用前必须确认业务意图。
4. `getTree(query)` 先查询扁平数据，再在内存中组树；实体实现 `ISortValue` 时按 `sortValue` 递归排序。

树移动和级联删除会修改多行，必须在事务中执行。接口会拒绝 `parentPath` 包含自身 ID 的移动，防止节点移入自己的后代形成环。大树的 `getTree()` 会一次性加载数据，后台菜单等小树可直接使用；数万节点的树应改用按层/按父 ID 分页查询。

#### 与 Repository 如何选择

- 简单后台 CRUD、树维护，且始终使用默认数据源时，可实现 `ISuperService` 或 `ITreeService`。
- 需要声明式查询、方法级 `@JoinCache`、`@JoinSession` 或希望数据访问边界更清晰时，优先 Repository + 业务 Service。
- 一个业务域选择一个主入口。不要在同一个 Service 中既实现通用接口、又为同一批 CRUD 注入 Repository，造成两套规则和缓存策略。
- 这两个接口默认方法创建的 Wrapper 没有显式 group，依赖全局默认数据源。多数据源业务优先使用可明确声明 group 的 Repository 或 Wrapper。

### 完整业务案例

下面用“用户查询、创建、改名和删除”展示推荐分层。案例假设已引入 starter、mysql 和 repository；查询缓存模块仍是可选的。

#### 1. 完整实体

```java
package com.example.app.user.domain;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinIndex;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;

@JoinTable(value = "sys_user", autoSchema = true)
@JoinIndex(fields = "username", unique = true)
public class UserEntity extends IdEntity<UserEntity> {
    @JoinField(name = "user_name", textLength = 64, description = "登录用户名")
    @JoinWhere
    private String username;

    @JoinField(description = "状态：1启用，0停用")
    @JoinWhere
    private Integer status;

    public UserEntity() { }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
```

Entity 保持非 final，提供无参构造和标准 getter/setter，以便编解码和代理。上例假设框架拥有该表全部列，因此明确设置 `autoSchema=true`；配套扫描配置如下：

这里的 `username` 并不能按驼峰规则转换成 `user_name`，所以示例中的 `@JoinField(name = "user_name")` 是必要的；如果 Java 属性本身命名为 `userName`，则应省略 `name`。

```yaml
yulinlin:
  repository:
    scan-packages:
      - "com.example.app.**.repository"
  mysql:
    schema-mode: CREATE
    schema-packages:
      - "com.example.app.**.domain"
```

如果生产表由 Flyway、Liquibase 或发布脚本管理，保留 `autoSchema=false` 默认值，不配置 `CREATE` 扫描，并在迁移脚本中创建同等的唯一索引。

#### 2. Repository

```java
package com.example.app.user.repository;

import com.example.app.user.domain.UserEntity;
import com.yulinlin.data.core.anno.JoinCache;
import com.yulinlin.data.core.anno.JoinRepository;
import com.yulinlin.repository.dao.BaseRepository;

@JoinRepository
public interface UserRepository extends BaseRepository<UserEntity> {

    @JoinCache
    UserEntity findByUsernameEq(String username);
}
```

`BaseRepository` 已提供按 ID 查询、全量查询、新增、更新和删除。方法级 `@JoinCache` 让缓存意图可见；如果希望接口中全部查询默认缓存，也可标在接口上，但方法级更容易评审。不要在 insert/update/delete 方法上使用 `@JoinCache`。

#### 3. 响应 DTO

```java
package com.example.app.user.api;

import com.example.app.user.domain.UserEntity;

public record UserResponse(String id, String username, Integer status) {
    public static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getStatus());
    }
}
```

在 Service 内完成 Entity 到 DTO 的转换，可以避免 Controller 触发懒加载，也不会把事务绑定代理暴露给 JSON 序列化层。

#### 4. Service

```java
package com.example.app.user.service;

import com.example.app.user.api.UserResponse;
import com.example.app.user.domain.UserEntity;
import com.example.app.user.repository.UserRepository;
import com.yulinlin.data.core.anno.JoinSync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public UserResponse findByUsername(String username) {
        UserEntity user = repository.findByUsernameEq(requireText(username, "username"));
        if (user == null) throw new IllegalArgumentException("user not found");
        return UserResponse.from(user);
    }

    @Transactional
    public String create(String username) {
        UserEntity user = new UserEntity();
        user.setUsername(requireText(username, "username"));
        user.setStatus(1);
        int affected = repository.insert(user);
        if (affected != 1) throw new IllegalStateException("user insert failed");
        return user.getId();
    }

    @Transactional
    @JoinSync
    public void rename(String id, String username) {
        UserEntity user = repository.findByIdEq(requireText(id, "id"));
        if (user == null) throw new IllegalArgumentException("user not found");
        user.setUsername(requireText(username, "username"));
        // 无需手动 update；提交前按主键生成部分更新。
    }

    @Transactional
    public void delete(String id) {
        int affected = repository.deleteByIdEq(requireText(id, "id"));
        if (affected != 1) throw new IllegalArgumentException("user not found");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
```

创建和删除是显式写入，只需事务。`rename` 依赖 setter 跟踪，因此同时使用 `@Transactional` 和 `@JoinSync`。查询、setter 与提交全部在同一个 Service 调用内完成。

#### 5. Controller

```java
package com.example.app.user.api;

import com.example.app.user.service.UserService;
import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.starter.domain.IdPo;
import com.yulinlin.starter.domain.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@ApiCrypto
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping("/by-username")
    public R<UserResponse> findByUsername(@Valid @RequestBody FindUserRequest request) {
        return R.ok(service.findByUsername(request.username()));
    }

    @PostMapping("/create")
    public R<String> create(@Valid @RequestBody CreateUserRequest request) {
        return R.ok(service.create(request.username()), "创建成功");
    }

    @PostMapping("/rename")
    public R<Void> rename(@Valid @RequestBody RenameUserRequest request) {
        service.rename(request.id(), request.username());
        return R.ok();
    }

    @PostMapping("/delete")
    public R<Void> delete(@RequestBody IdPo request) {
        service.delete(request.getId());
        return R.ok();
    }

    public record FindUserRequest(@NotBlank String username) { }
    public record CreateUserRequest(@NotBlank @Size(max = 64) String username) { }
    public record RenameUserRequest(
            @NotBlank String id,
            @NotBlank @Size(max = 64) String username) { }
}
```

Controller 只处理 POST JSON 协议、校验和 DTO，不直接调用 Wrapper 或持有事务。查询和删除同样定义小型请求 DTO，不把参数拆到 URL。真实项目应将异常统一转换为约定的错误响应，不在每个 Controller 重复 try/catch。

### 事务与自动更新

#### 推荐写法

```java
@Transactional
@JoinSync
public void changeProfile(String id, String username) {
    UserEntity user = repository.findByIdEq(id);
    if (user == null) throw new IllegalArgumentException("user not found");
    user.setUsername(username);
}
```

- `@Transactional` 定义事务，`@JoinSync` 只决定作用域内查询是否返回自动更新代理。
- 必须通过 Spring Bean 从外部调用 public 方法；`this.changeProfile(...)` 式自调用会绕过 Spring AOP。
- 不要将代理 Entity 传给异步任务、虚拟线程或事务外的 Controller。它只能在创建它的原线程和原事务中 setter。
- 业务失败让异常离开事务方法。如果必须捕获，应继续抛出或明确设置 rollback-only，不要吞掉后继提交。
- 只读方法不加 `@JoinSync`。全局 `yulinlin.data.auto-update=true` 适合对边界非常一致的项目；一般项目优先显式方法注解。
- Repository 方法可使用 `@JoinSync` 只增强特定查询，但仍需外层 Service 活动事务。推荐默认放在 Service，避免事务和更新意图分散。

自动更新跳过 null，`setX(null)` 不表示把列清为 SQL NULL。如果业务必须清空列，使用参数化自定义 SQL；不要利用字符串拼接绕过参数绑定。

### 缓存规范

```java
@JoinCache
UserEntity findByUsernameEq(String username);
```

- 只缓存价值明确、读多写少且可接受 TTL 窗口的查询。不是每个 Repository 方法都应该加缓存。
- 引入缓存 Provider 后，`yulinlin.cache.clear-on-write`默认 `true`；标准 insert/update/delete 在事务提交成功后按表 namespace 清理缓存，回滚不清理。
- 写 Wrapper 和 `ExecuteRequest` 只使用 `clearCache(boolean)` 覆盖单次行为。一般保持默认；只有明确接受 TTL 窗口内旧结果时才使用 `clearCache(false)`。
- 自定义查询或跨表投影无法完整推导依赖时，通过 `@JoinCache.namespaces` 或查询请求的 `cacheNamespaces(...)` 补充表命名空间；自定义写 SQL 开启清理时会退化为清理全部 ORM 查询缓存。
- 事务内必须读到最新数据的查询使用 `CacheMode.NONE`；强制回源并重写缓存使用 `CacheMode.REFRESH`。
- 未引入缓存 Provider 时，普通查询安全回源数据库，`QueryCacheProperties` 不会启用，写路径也不解析 namespace 或执行失效。一个应用只选 Caffeine、Ehcache 或 Redis 之一；`cache-redis` 已自带可选 Caffeine L1，不要再重复引入 `cache-caffeine`。
- 默认 TTL 放在 `yulinlin.cache.ttl`，默认不让 TTL 参与 Key。监控统计默认关闭，只在确实需要观测时开启。

### 日志规范

面向开发和运维人员的日志描述统一使用中文；类名、SQL、异常类型以及 `group`、`cluster`、`type`、`elapsedMs`、`key`等结构化检索键保持原始技术名称。这样日志可以直接阅读，也不会破坏日志平台按固定字段检索和聚合。

推荐写法：

```java
log.info("订单创建成功 orderId={} userId={}", orderId, userId);
log.warn("缓存条目超过大小限制，已跳过 key={} sizeBytes={} limitBytes={}",
        key, actualBytes, limitBytes);
log.error("支付回调处理失败 orderId={} channel={}", orderId, channel, error);
```

统一规则：

- 描述动作和结果，例如“SQL执行成功”“延迟写队列刷新完成”“控制器发生未处理异常”，不要只写“失败”或一串对象值。
- 使用 `{}`占位符记录上下文，不使用字符串拼接；字段键保持稳定，便于日志平台查询。
- 捕获异常时把 `Throwable`作为最后一个参数传入，保留完整堆栈；不要只打印 `error.getMessage()`。
- `DEBUG`用于批次、缓存命中等高频诊断；`INFO`用于重要生命周期和明确成功事件；`WARN`用于可恢复、已降级或将重试的问题；`ERROR`用于本次操作失败、数据被丢弃或必须人工处理的问题。
- 同一异常只在能够补充业务上下文或最终处理它的层记录一次，随后继续抛出的中间层不要重复打印相同堆栈。
- 不记录密码、密钥、令牌、完整身份证号、银行卡号和未脱敏请求体。SQL参数日志也必须遵守长度限制与脱敏策略。
- 日志文字修改不应改变监控数据结构。数据库监控使用独立记录对象，`elapsedMs`等字段含义保持稳定。

框架内置日志也遵守同一约定：中文描述配合稳定英文键。例如 `SQL慢查询 group=mysql cluster=master type=select elapsedMs=800 ...`。不要依赖整段日志文本进行业务控制；程序判断应使用异常、返回值或监控接口。

### 多数据源规范

只有一个 group 时可省略指定；同时注册多个 group 时设定默认组：

```yaml
yulinlin:
  datasource:
    default-group: mysql
```

一个 Repository 始终属于同一数据源时，可在接口标记：

```java
@JoinRepository
@JoinSession("postgresql")
public interface SearchRepository extends BaseRepository<SearchEntity> {
    // 默认使用 postgresql
}
```

只有个别方法需要切换时使用方法级 `@JoinSession`。代码中显式 group 的 Request 优先级更高。不要在一条原始 SQL 中假设框架会自动翻译 MySQL、PostgreSQL、SQLite 和 H2 方言。

跨多个 group 的业务可使用 Service 层 `@JoinTransaction` 或 `RouteSession.transaction(...)` 统一收集参与会话。这是多个本地事务的协调提交，不是 2PC/XA；对严格原子性业务使用消息、补偿、幂等或专用分布式事务方案。

### 批量写入规范

```java
@Transactional
public int importUsers(List<UserEntity> users) {
    if (users == null || users.isEmpty()) return 0;
    return ModelInsertWrapper.newInstance("mysql", users)
            .batch()
            .execute();
}
```

- 普通集合写入先使用 JDBC batch；数据足够大、数据库与连接池有余量时，才启用 `.batch()` 多连接拆分。
- `parallel-connections` 控制一个框架事务的最大物理连接数，`execute-batch-size` 控制一次 `executeBatch()` 的行数，都应结合连接池和数据库实测。
- SQLite 单文件只有一个写者，Session 会拒绝多连接拆组；不要用增加写线程的方式解决 SQLite 锁竞争。
- 业务上可以每批 128 个对象提交给上层，底层仍由 `execute-batch-size` 限制 JDBC 批大小。不为每 128 条数据创建一个新线程或新事务。
- 大文本、JSON 和二进制内容应限制单条大小；不把不受控的 InputStream 或超大对象混入普通批次。

允许一分钟左右最终一致的点赞、浏览统计等小批写入，可使用 `.queue().execute()` 交给全局 `yulinlin.data.queue` 合并；当前活动队列的 Node 总数达到 `flush-size`（默认 `1024`）会立即异步刷新，即使这些 Node 分布在多个不同分组中。它返回 `0` 只表示入队成功，不能放在事务内，也不适合依赖自增 ID、实际影响行数或严格操作顺序的业务。进程内队列没有硬容量上限；持续生产快于写入时会继续占用内存，应限流并监控积压。它也不是持久化消息系统，关键数据仍使用同步事务或可靠消息；大数据导入直接在业务异步任务中调用 `.batch()`，不通过延迟队列拆事务。

### Schema 管理规范

- 生产项目默认使用版本化 DDL 迁移；框架 `CREATE` 模式更适合本地数据库、新项目和可控表。
- 只有拥有全部持久化列的 Entity 设置 `autoSchema=true`。投影 DTO、Join 模型和统计模型保持 false。
- 自动 Schema 可创建表、缺失普通列和声明索引，不会安全推断字段改名、类型变更、删列、外键或数据转换。
- 先用 `EntitySession.createTableSql(Entity.class)` 审查 DDL，再在目标环境启用自动初始化。所有执行的 Schema SQL 都应在日志中可见。

### 常见反例

| 反例 | 问题 | 推荐替代 |
| --- | --- | --- |
| Controller 直接调 Wrapper | 协议层与数据层耦合，无法统一事务 | Controller 只调 Service |
| 在 Repository 方法开事务，返回后再 setter | Repository 返回时事务可能已结束 | Service 上组合 `@Transactional + @JoinSync` |
| 实现 `ISuperService` 后不声明事务 | 接口默认方法只封装 CRUD，不会自动开启事务 | 在具体 Service 业务入口上使用 `@Transactional` |
| 把 `save()` 当成无竞态 UPSERT | 实际是先查后分组写入，并发请求可同时判断为不存在 | 已知操作类型时使用 `insert/update`，并依赖唯一索引保底 |
| 将 `ITreeService.deleteById*` 当作普通单行删除 | 该默认实现会级联删除后代节点 | 事务内执行，接口命名和二次确认明确告知级联语义 |
| 全局开启自动更新，却大量无事务查询 | 需要代理的查询会明确报“请开启事务” | 默认关闭，在写业务方法显式 `@JoinSync` |
| 将 `@JoinCache` 加到写方法 | 缓存注解是查询策略，不是写入清理开关 | 保留全局 `clear-on-write`，或在写 Wrapper 上调用 `clearCache(boolean)` |
| 在写 Wrapper 上调用旧 `cache/invalidate` API | 写入不缓存结果，旧命名容易混淆查询策略 | 统一使用 `clearCache(boolean)` |
| 多 group 不配默认值，也不显式路由 | 框架无法判断业务数据库 | 配置 `default-group` 或使用 group/`@JoinSession` |
| 在事务中启动 `CompletableFuture` 修改同一实体 | ThreadLocal、连接和代理不自动跨线程 | 同一事务同步完成；异步任务自建业务边界 |
| 日志只写英文短语、拼接对象或只打印异常消息 | 难以直接阅读、无法稳定检索且丢失堆栈 | 中文描述 + 稳定字段键 + `{}`占位符，异常放最后一个参数 |
| 用 null 期望清空数据库列 | 普通更新和自动更新都跳过 null | 参数化自定义 SQL |
| 多个不完整模型共同拥有一张表的 Schema | 可创建残缺表或发生拥有者冲突 | 只让一个完整 Entity `autoSchema=true` |
| 把跨库本地协调当作分布式原子事务 | 某库提交后另一库仍可失败 | 设计幂等、补偿或专用分布式方案 |

### 上线前评审清单

- [ ] 所有写 Service 都有清晰的事务边界，通过 Spring Bean 外部调用。
- [ ] 使用 `@JoinSync` 的查询、setter 和提交处在同一线程与事务。
- [ ] 实体基类与物理表列一致；未重复声明继承的主键、审计时间或树字段。
- [ ] 实体具有无参构造、完整主键映射和可预期的列名；非列字段明确 `exist=false`。
- [ ] 更新和删除校验主键/条件及影响行数，已考虑 null 跳过语义。
- [ ] 缓存仅用于查询，TTL、namespace、`clear-on-write` 默认值和单次覆盖策略已明确，应用未同时引入两个 Provider。
- [ ] 多数据源项目设置了默认 group 或显式路由，原始 SQL 已按目标方言审查。
- [ ] 业务日志使用中文描述和稳定字段键，异常保留完整堆栈，敏感信息已经脱敏。
- [ ] 多连接批处理已在真实驱动、连接池和数据库上压测；SQLite 未强行并发写入。
- [ ] 每张自动表只有一个 Schema 拥有者，生产字段改名和类型变更都有可回滚迁移脚本。
- [ ] 测试至少覆盖成功提交、异常回滚、缓存未命中/失效、批量失败和多数据源路由。

如果项目不使用 Repository，保留相同的分层和事务规则，在 Service 中使用 Model Wrapper 即可。Repository 是声明式入口，不是使用事务、缓存或自动更新的强制前提。
