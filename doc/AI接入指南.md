# yulinlin-data AI 使用指南

---

> 派生文件，维护源为 doc 下六个使用专题。重新导出：./doc/build-ai-docs.ps1。

---

用途：给不能读取仓库的 AI 提供一个附件。包含接入、CRUD/统计/事务、关联代理、工具、接口安全和查询缓存；内部扩展与完整性能报告不在此导出中，按需另提供第五或第七专题。

---

适用 JDK 25、Spring Boot 3.5.16、制品 3.0。2026-10-09 已运行本地库 JMH、JDK 25 全模块测试编译、缓存专项测试和 Schema 定向测试；代码片段不等于所有数据库服务器均已集成验证，真实账号、表、路径和接口由业务提供。

---

<!-- source: doc/01-接入与数据源.md -->
## 项目接入与数据源

先选数据库模块，再配置数据源和会话组。本文完成 Spring Boot 接入、MySQL 与 PostgreSQL 配置、SQLite/H2 本地存储，以及多数据源注册和默认组选取。

阅读导航：[模块选择](#模块选择) · [MySQL](#mysql-接入) · [SQLite](#sqlite-接入) · [H2](#h2-接入) · [本地库选型](#sqliteh2-选型) · [PostgreSQL](#postgresql-接入) · [多数据源](#多数据源注册) · [默认会话组](#默认会话组) · [配置速查](#配置速查)

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
    map-underscore-to-camel-case: true
    parallel-connections: 4
    execute-batch-size: 256
    schema-mode: CREATE
    schema-packages:
      - demo.domain
```

正常 Boot 自动配置链下无需框架启用注解。mysql 模块创建 `mysqlSessionFactory` 和 `mysqlSession`，默认 group 为 `mysql`。为兼容已有生产项目，`schema-mode` 默认 `NONE`；不需要框架管理结构时删除整个 `yulinlin.mysql` 段并由迁移脚本准备表。设为 `CREATE` 后，启动时递归扫描 `schema-packages`，创建缺失的表、普通列和声明索引，并校验已有结构；`VALIDATE` 只校验，缺失直接阻止启动。完整实体与 CRUD 见 [第二专题](02-CRUD与统计分析.md)。

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

阅读导航：[最小实体](#最小实体和配套表) · [CRUD](#crud-完整服务) · [查询条件](#查询条件与结果组织) · [自定义 SQL](#自定义-sql-执行) · [统计分析](#统计分析) · [批量写入](#批量与多连接写入) · [事务](#事务使用) · [Repository](#repository-接口) · [边界](#使用边界与排障)

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

普通对象更新、懒同步更新和显式 `.field(name, null)` 都跳过 null；它们不能把数据库列清成 NULL。需要清空字段时使用参数化的[自定义写入](#自定义写入)。`@JoinField(update = false)` 字段不参与对象更新；version 字段按当前值加入条件并递增，仍应检查影响行数判断并发冲突。

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

对象构造条件示例：

```java
DemoUser filter = new DemoUser();
filter.setStatus(1); // 因为字段有 JoinWhere，参与条件。
var users = ModelSelectWrapper.newInstance("mysql", filter).selectList();
```

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

Request 可以复用事务上下文，但可变请求对象不能跨线程共享。原始查询 SQL 可以参与缓存，Key 会包含 SQL 与带类型参数；缓存只按 TTL 失效，写 SQL 不主动清理查询缓存。

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

这些数都必须是正整数，并按数据库模块独立生效。MySQL、PostgreSQL 与 H2 默认最多 4 个连接，把整批数据均匀分成最多 4 个大组，一组一个任务/连接；同 SQL 复用 PreparedStatement，每满配置行数执行一次 executeBatch，尾批也执行。SQLite 固定单写连接，但仍可独立设置 execute-batch-size。128 是 ExecuteRequest 的并发启用最小请求条数，不是 JDBC 提交大小。

只有 .batch()、执行器、请求阈值和 supportsParallelWrites 等条件满足才并发；SQLite、单连接池或 Spring 绑定连接不拆组。H2 可拆组，但多个连接仍受文件锁、索引和写入热点影响。4 是每 Session、每框架事务的上限，不是整个应用并发上限，也不保证 4 倍速度。

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

Repository 默认 Bean 名是接口简单类名首字母小写。同名接口会明确报错，不会静默覆盖；使用 `@JoinRepository("localUserRepository")`指定唯一名称。重复或重叠扫描路径会自动去重。

只有查询方法显式标记 `@JoinCache`才使用查询缓存；接口上不能添加该注解，也没有自动开启全部 Repository 缓存的配置：

```java
@JoinRepository
public interface DemoUserRepository extends BaseRepository<DemoUser> {

    // 没有注解：每次查询数据源
    DemoUser findByUsernameEq(String username);

    // READ_THROUGH；使用 yulinlin.cache.ttl
    @JoinCache
    List<DemoUser> findByStatusEq(Integer status);

    // 每次强制查库，并用最新结果覆盖对应缓存
    @JoinCache(mode = CacheMode.REFRESH, ttl = 30, unit = TimeUnit.SECONDS)
    DemoUser findByIdEq(String id);

    // 额外把角色表版本纳入 Key，角色表更新后该查询也失效
    @JoinCache(namespaces = "sys_role")
    List<DemoUser> findAll();
}
```

`ttl = -1`是默认值，表示使用 `yulinlin.cache.ttl`；显式 TTL 必须为正数。默认 `yulinlin.cache.ttl-in-key=false`，不同 TTL 的相同查询会复用 Key；设为 `true`才按 TTL 隔离。`CACHE_ONLY`只读已有缓存，未命中抛出 `CacheMissException`。未引入缓存 Provider 时，`READ_THROUGH`和`REFRESH`安全退化为查询数据源但不保存，`CACHE_ONLY`仍然报未命中。写方法添加 `@JoinCache`、TTL 非法或 namespace 为空，会在 Repository 代理创建阶段直接失败；insert/update/delete 继续由框架在事务提交成功后自动失效相关查询缓存。

### 使用边界与排障

- 更新通常跳过 null；普通 copyProperties 或懒同步的 null 跳过不等于数据库清列。
- insertBefore/updateBefore 可能填充字段，表结构必须匹配实体实际继承字段。
- 查询缓存保存查询结果；标准写操作在事务提交成功后自动失效相关表命名空间，TTL 是最终兜底。要求事务内读己之写或绝对强一致的查询使用 `CacheMode.NONE`。
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

---

<!-- source: doc/06-接口安全.md -->
## 接口安全

security 模块为 Spring MVC JSON 接口提供显式启用的请求解密和响应加密。默认使用 AES-256-GCM，也可以为旧客户端配置 AES-CBC 兼容协议；IP、浏览器行为和爬虫风控将在后续能力中实现。

### 接入

加入独立模块，不要求引入 ORM、数据库驱动或 Spring Security：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>security</artifactId>
    <version>3.0</version>
</dependency>
```

推荐按设备类型配置 Base64 编码的 32 字节密钥。密钥必须由外部安全配置提供，不能提交到源码仓库：

```yaml
yulinlin:
  security:
    crypto:
      device-header: X-Device-Type
      default-device: web
      devices:
        web:
          algorithm: AES/GCM/NoPadding
          key-encoding: BASE64
          key: ${YULINLIN_SECURITY_WEB_KEY}
        android:
          algorithm: AES/GCM/NoPadding
          key-encoding: BASE64
          key: ${YULINLIN_SECURITY_ANDROID_KEY}
        ios:
          algorithm: AES/GCM/NoPadding
          key-encoding: BASE64
          key: ${YULINLIN_SECURITY_IOS_KEY}
      max-request-size: 1MB
```

客户端通过 `X-Device-Type` 传递 `web`、`android`、`ios` 等设备类型，服务端选择对应密钥；没有该请求头时使用 `default-device`。设备名会去除首尾空白并转为小写，只允许小写字母、数字、下划线和短横线，最长 32 个字符。规范化后的设备名参与 AAD，客户端加密时也必须使用该值。未知设备会返回 400。响应会回写实际使用的 `X-Device-Type`，客户端据此选择响应解密密钥。

security 模块引入后自动注册加解密组件；默认设备未配置、缺少密钥或密钥长度错误时启动失败，不生成临时密钥，也不会静默退化成明文。GCM 要求 32 字节密钥，可以使用 `AesGcmCrypto.generateKeyBase64()` 生成后交给密钥管理系统保存。

只有一个设备的旧项目可以继续使用单密钥配置。该密钥归属于 `default-device`，不能与 `devices` 同时配置：

```yaml
yulinlin:
  security:
    crypto:
      key: ${YULINLIN_SECURITY_KEY}
      default-device: web
```

#### AES-CBC 旧客户端兼容配置

下面配置等价于 `AES/CBC/PKCS5Padding`、UTF-8 密钥和 UTF-8 固定 IV 的构造方式：

```yaml
yulinlin:
  security:
    crypto:
      algorithm: AES/CBC/PKCS5Padding
      key-encoding: UTF8
      iv: 5efd3f6060e20765
      iv-encoding: UTF8
      key: MIGfMA0GCSqGSIb3
      default-device: web
```

CBC 密钥转换后必须是 16、24 或 32 字节，IV 必须是 16 字节。多设备模式下，每个设备的算法、密钥编码、密钥、IV 编码和 IV 都是独立配置，可以同时兼容不同客户端协议：

```yaml
yulinlin:
  security:
    crypto:
      default-device: web
      devices:
        web:
          algorithm: AES/CBC/PKCS5Padding
          key-encoding: UTF8
          key: MIGfMA0GCSqGSIb3
          iv-encoding: UTF8
          iv: 5efd3f6060e20765
        android:
          algorithm: AES/GCM/NoPadding
          key-encoding: BASE64
          key: ${YULINLIN_SECURITY_ANDROID_KEY}
        ios:
          algorithm: AES/CBC/PKCS5Padding
          key-encoding: UTF8
          key: 1234567890abcdef
          iv-encoding: UTF8
          iv: abcdef1234567890
```

固定 IV 的 CBC 仅用于兼容已有协议：它会泄露重复数据模式，而且没有 GCM 的篡改认证能力。新客户端应继续使用默认 GCM。GCM 不接受 YAML 中的 `iv`，始终为每次加密随机生成 IV。

### 标记接口

`@ApiCrypto` 可以放在 Controller 类或具体方法上。方法配置优先于类配置：

```java
package demo.api;

import com.yulinlin.security.annotation.ApiCrypto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    @ApiCrypto
    @PostMapping("/orders")
    public OrderResult create(@RequestBody CreateOrder request) {
        return new OrderResult(request.productId(), true);
    }
}
```

Controller 收到的仍然是正常 DTO，返回值也按普通 Java 对象编写。框架在 Jackson 反序列化前解密请求。普通返回对象会整体加密；返回 `R` 或其子类 `ResponseVo` 时，只加密 `data`，保留 code、msg、ok 和 timestamp，并把 crypt 设置为 true。

注解只有一个开关。方法级设置可以覆盖类级设置：

| 写法 | 请求 | 响应 |
| --- | --- | --- |
| `@ApiCrypto` 或 `@ApiCrypto(true)` | 必须传整个 JSON 加密后的密文，否则返回 400 | 加密 |
| `@ApiCrypto(false)` | 可传普通 JSON；传整个请求体密文时仍自动解密 | 不加密 |
| 没有注解 | security 不读取、不识别请求 | 不加密 |

```java
@ApiCrypto
@RestController
public class OrderController {

    @PostMapping("/orders") // 请求和响应都加密。
    public OrderResult create(@RequestBody CreateOrder request) {
        return service.create(request);
    }

    @ApiCrypto(false)
    @PostMapping("/orders/public") // 请求可明文也可加密，响应保持明文。
    public OrderResult publicQuery(@RequestBody OrderQuery query) {
        return service.query(query);
    }
}
```

`@ApiCrypto(false)` 是兼容模式，不是完全关闭请求处理。框架把整个 body 转成 UTF-8 字符串并去掉首尾空白：以 `{` 或 `[` 开头时按明文 JSON 原样交给 Spring/Jackson，且不会读取或校验设备类型；其他内容全部作为 Base64 密文，并按请求设备选择密钥解密。不会为了判断明文而预解析 JSON，因此每个明文请求只反序列化一次。密文格式错误或设备未知会返回 400，不会再次降级成明文；GCM 还能可靠拒绝错误密钥和被篡改内容，CBC 则只提供旧协议兼容。解密结果的 JSON 合法性继续由原消息转换器校验。

没有 `@ApiCrypto` 的接口保持原行为。不提供“只加密响应”的独立开关，也不存在调试请求头或其他明文绕过开关。

### 报文格式

自动请求和响应使用一段非 JSON 的 Base64 密文：

```text
BoV8CsObM8Q3qgfMDKfQZi4m9z0p7D+ZV7cNwX9u0J0=
```

- GCM 模式解码后的格式为 `12 字节随机 IV + 密文 + 16 字节 GCM 认证标签`。
- CBC 模式只传输密文字节；解密使用 YAML 中配置的固定 16 字节 IV。
- 使用标准 Base64，可能包含 `+`、`/` 和末尾的 `=`。
- GCM 每次加密生成新的随机 IV，同一明文不会产生固定密文。
- GCM 同时校验机密性和完整性；密文、IV 或协议元数据被修改后解密失败。CBC 不具备这项保证。
- GCM 将版本、设备类型和算法以 `version + "\n" + deviceType + "\n" + algorithm` 的 UTF-8 字节作为附加认证数据（AAD）。浏览器实现必须使用相同规则；CBC 不使用 AAD。
- 密文本身不携带设备类型，自动接口通过 `X-Device-Type` 选择密钥；未传时使用默认设备。GCM 的设备类型或密钥不匹配会导致认证失败；CBC 通常会因填充或 JSON 无效而失败，但不具备可靠的篡改检测。

请求必须继续使用 `Content-Type: application/json`，但密文 body 本身不能使用 JSON 双引号包裹。无效 Base64 返回 400；GCM 的错误密钥或被篡改密文也会返回 400。超过 `max-request-size` 返回 413，限制同时应用于密文和解密后的 JSON。加密响应通过 `X-Yulinlin-Crypto` 返回实际算法。兼容模式只把 `{`、`[` 开头的对象和数组视为明文，因此不支持顶层字符串、数字、布尔值或 null 作为明文请求体。

#### 浏览器 AES-GCM Web Crypto 示例

下面代码与服务端 Base64 密文和 AAD 规则一致。`base64Key` 是配置中的同一密钥；实际项目不能把它当成浏览器无法读取的秘密：

```javascript
const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();

function base64Bytes(value) {
  return Uint8Array.from(atob(value), char => char.charCodeAt(0));
}

function bytesBase64(bytes) {
  let binary = "";
  for (const value of new Uint8Array(bytes)) binary += String.fromCharCode(value);
  return btoa(binary);
}

async function importAesKey(base64Key) {
  return crypto.subtle.importKey("raw", base64Bytes(base64Key), "AES-GCM", false,
      ["encrypt", "decrypt"]);
}

async function encryptJson(value, base64Key, deviceType = "web") {
  deviceType = deviceType.trim().toLowerCase();
  const key = await importAesKey(base64Key);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const aad = textEncoder.encode(`1\n${deviceType}\nA256GCM`);
  const data = await crypto.subtle.encrypt(
      {name: "AES-GCM", iv, additionalData: aad, tagLength: 128},
      key,
      textEncoder.encode(JSON.stringify(value)));
  const encrypted = new Uint8Array(iv.length + data.byteLength);
  encrypted.set(iv, 0);
  encrypted.set(new Uint8Array(data), iv.length);
  return bytesBase64(encrypted);
}

async function decryptJson(ciphertext, base64Key, deviceType = "web") {
  deviceType = deviceType.trim().toLowerCase();
  const encrypted = base64Bytes(ciphertext.trim());
  if (encrypted.length < 28) throw new Error("Invalid ciphertext");
  const iv = encrypted.slice(0, 12);
  const data = encrypted.slice(12);
  const key = await importAesKey(base64Key);
  const aad = textEncoder.encode(`1\n${deviceType}\nA256GCM`);
  const plaintext = await crypto.subtle.decrypt(
      {name: "AES-GCM", iv, additionalData: aad, tagLength: 128},
      key,
      data);
  return JSON.parse(textDecoder.decode(plaintext));
}

const deviceType = "web";
const encrypted = await encryptJson({productId: "P1001"}, base64Key, deviceType);
const response = await fetch("/orders", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "X-Device-Type": deviceType
  },
  body: encrypted
});
const responseBody = await response.json();
const responseDevice = response.headers.get("X-Device-Type") ?? deviceType;
const result = responseBody?.crypt === true
    ? {...responseBody, data: await decryptJson(responseBody.data, base64Key, responseDevice)}
    : typeof responseBody === "string"
        ? await decryptJson(responseBody, base64Key, responseDevice)
        : responseBody;
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

自动配置还提供 `AesCrypto` Bean，它固定使用默认设备配置。也可以通过 `new AesGcmCrypto(keyBytes, deviceType)` 或 `new AesCbcCrypto(keyBytes, ivBytes)` 脱离 Spring 使用。实现每次操作创建独立 Cipher，可被多个请求线程共享。

### 浏览器边界

这是基础共享密钥方案。浏览器若要自行加解密，就必须获得同一 AES 密钥；网页脚本、浏览器扩展和自动化浏览器最终也能读取它。因此该功能可以避免普通明文调用并提高简单采集脚本的成本，但不能把浏览器中的密钥当作秘密，也不能单独承担防爬虫职责。

生产环境仍必须使用 HTTPS。真正的爬虫治理需要后续的会话令牌、多维限流、行为评分和挑战机制；请求公钥加密及短期会话密钥也应作为独立协议演进，不能在不升级 version 的情况下改变当前信封含义。

### 使用限制

- 当前只支持 Spring MVC，不支持 WebFlux。
- 设备请求头用于选择密钥，不是身份认证信息；不能仅凭 `X-Device-Type` 判断调用方可信。
- 自动 Advice 只用于 JSON 或字符串响应；不要标记文件下载、流式响应、SSE、WebSocket 或 multipart 接口。
- 普通返回对象整体加密为 Base64 密文字符串。`R` 和 `ResponseVo` 只加密 data，其他响应字段保持明文，crypt 为 true；data 中存放 Base64 密文字符串。
- 应用日志不得记录密钥、解密后的敏感正文或完整密文。
- AES-GCM 不是密码哈希，用户密码仍应使用专用密码哈希方案保存。
- 当前未实现时间戳、nonce 防重放和密钥轮换；这些属于下一阶段，不应在文档或业务中假设已经具备。

本专题于 2026-10-06 按源码编写。本轮未运行测试、编译或打包，使用前应在业务工程中验证浏览器协议和异常响应约定。

---

<!-- source: doc/08-查询缓存.md -->
## 查询缓存

查询缓存是可选能力。`core` 只提供统一协议和无缓存兜底；不引入缓存模块时，应用照常启动，标记为缓存的查询会直接访问数据源。

### 公共配置

```yaml
yulinlin:
  cache:
    ttl: 10m
    ttl-in-key: false
```

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `ttl` | `10m` | 查询缓存默认有效期 |
| `ttl-in-key` | `false` | TTL 是否参与 ORM 查询缓存 Key；不影响 `CacheClient` 业务缓存 |

默认关闭时，只要查询条件、数据源和 namespace 版本相同，不同 TTL 的查询就会复用同一个缓存条目。已有条目被命中时不会修改其过期时间；`REFRESH`或未命中重新写入时，才使用本次请求的 TTL。这样可以减少重复数据并提高缓存复用率。

需要让不同 TTL 的相同查询完全隔离时开启：

```yaml
yulinlin:
  cache:
    ttl-in-key: true
```

开启后，解析出的默认 TTL 或请求级 TTL 会参与物理 Key，同一查询使用不同 TTL 时会保存为不同条目。namespace 名称和版本始终参与 Key，不受此开关影响。

### 选择实现

只引入一个实现，不要同时引入两个。

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
      record-stats: true
```

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `initial-capacity` | `16` | Caffeine 内部表的初始容量；只影响扩容次数，不会一次性创建全部缓存对象 |
| `maximum-size` | `10000` | 最大条目数；ORM 查询缓存与 `CacheClient` 业务缓存共用该容量 |
| `expiration-policy` | `after-write` | `after-write` 写入后固定 TTL；`after-access` 每次命中后按该条目的 TTL 续期 |
| `use-system-scheduler` | `false` | 使用 JDK 系统调度器更及时地触发过期维护；关闭时由正常读写维护，过期项仍不会被命中 |
| `executor` | `common-pool` | `common-pool` 异步执行维护任务；`direct` 在调用线程执行，少一次调度但可能增加请求延迟 |
| `record-stats` | `true` | 记录命中率、淘汰数和加载统计；不需要观测时可关闭以减少少量计数开销 |

`expiration-policy`同时作用于 ORM 查询缓存和 `CacheClient`。请求级 `cache(Duration)` 或 `CacheClient.set(..., Duration)` 仍决定各条目的 TTL，策略只决定读取命中后是否重新开始计时。大多数接口服务建议保留 `after-write + common-pool`；会话类热点数据才考虑 `after-access`。

需要监控时可以直接注入具体实现：

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
      maximum-entry-size-mb: 16
      record-statistics: true
```

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `directory` | `data/cache` | 磁盘缓存目录，同一时间只能由一个 CacheManager 占用 |
| `heap-entries` | `10000` | JVM 堆内热点条目数，ORM 查询与 `CacheClient` 共用 |
| `offheap-size-mb` | `0` | 可选堆外中间层；`0` 表示关闭，适合降低大量缓存对象对 GC 的影响 |
| `disk-size-mb` | `1024` | 磁盘层容量上限 |
| `persistent` | `true` | 是否在正常关闭并重启后保留磁盘缓存 |
| `expiration-policy` | `after-write` | `after-write` 写入后固定 TTL；`after-access` 每次命中后按该条目的 TTL 续期 |
| `disk-threads` | `2` | Ehcache 磁盘层固定工作线程数；本地 SSD 通常使用 `2` 到 `4` |
| `maximum-entry-size-mb` | `16` | 单条 JSON 序列化结果上限，超出后记录并跳过，防止一个大列表挤占缓存 |
| `record-statistics` | `true` | 记录框架层命中、未命中、写入、超大条目和序列化失败计数 |

正常关闭时框架调用 `close()`。即使 `persistent: true`，缓存仍应被视为可丢失数据；缓存损坏或反序列化失败会删除对应项并回源查询。启用堆外层会增加一次序列化/复制成本，只在缓存较大或 GC 压力明显时开启。

可以直接注入具体实现读取和重置统计：

```java
@Resource
private EhcacheQueryCache ehcacheQueryCache;

EhcacheQueryCache.Stats stats = ehcacheQueryCache.stats();
ehcacheQueryCache.resetStatistics();
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

元数据直接流入128位哈希器，不构建完整中间字符串。`CacheKey` 在内存中只保存两个 `long`；Caffeine 直接使用该对象作为 Key，只有 Ehcache 持久化或日志输出时才按需生成 `v2:` 开头的32位十六进制文本。实体反射字段通过 `ClassValue` 缓存，不会在每次查询时重新扫描、排序。

`CommandNode` 的 SQL、参数名、参数类型和值都属于 INode 元数据。因此相同 SQL 的不同参数不会共用结果；MySQL、PostgreSQL、SQLite、H2、MongoDB、Elasticsearch 以及第三方 `AbstractSession` 实现都使用同一规则。Map 参数按键排序，参数插入顺序不会造成无意义的 Key 差异。

数据源 group 必须进入 Key。同一个实体和条件在 `mysql`、`pgsql` 或其他 group 中不会串缓存；同一个逻辑 group 下的负载均衡节点可以共享逻辑 Key。

### 失效和一致性

缓存始终受 TTL 约束。标准 `insert/update/delete` 默认在提交成功后自动失效相关查询缓存，无需调用 `invalidate()`：

```java
int affected = ModelUpdateWrapper
        .newInstance("mysql", patch)
        .execute();

int deleted = ModelDeleteWrapper
        .newInstance("mysql", SysUser.class)
        .eq("status", 0)
        .invalidate("sys_user", "sys_user_role") // 显式补充依赖表
        .execute();
```

需要补充依赖或扩大范围时使用：

| API | 行为 |
| --- | --- |
| 默认行为 / `invalidate()` | 从 `INode`、`From/Join/Store`、`@JoinTable`、`@JoinTableList` 自动提取本次写入涉及的表并失效 |
| `invalidate("table", ...)` | 自动提取之外，再显式加入表名；自定义写 SQL 应使用它 |
| `invalidateAll()` | 失效当前缓存 Provider 中的全部查询 |

自定义 SQL 没有实体或节点表信息时，框架无法可靠猜测 SQL 涉及的表，默认退化为全部查询缓存失效并输出警告。显式提供表名可以缩小范围：

```java
ExecuteRequest<Object> write = ExecuteRequest.newInstance(
        "update sys_user set status=#{status} where id=#{id}",
        Map.of("status", 1, "id", 7)
);
write.setSession("mysql");
int affected = write.invalidate("sys_user").execute();
```

自定义查询若没有带 `@JoinTable` 的 `fromClass`，可用 `cacheNamespaces("sys_user", "sys_role")` 声明依赖表。这样任一表失效时，该复杂 SQL、JOIN 或聚合结果都会同时失效。

失效采用“命名空间版本号”，不会遍历百万级 Key：查询 Key 会携带 `Session 类型 + group + 表名` 的版本快照，写入成功后只递增相关版本。旧 Key 立即不可达，随后按自身 TTL 自然回收。Ehcache 会持久化版本号，应用重启后仍不会重新命中已经失效的旧数据。

事务规则：

- 普通单次写入在底层 Session 提交成功后失效。
- `RouteSession` 多数据源事务会合并重复表名，只在最外层提交成功后统一失效。
- Spring 事务在 `afterCommit` 阶段失效；回滚或提交失败不失效。
- 查询回源期间若并发发生更新，旧查询只能写入旧版本 Key，不能重新污染新版本缓存。

即使有主动失效，也应把 TTL 设置为业务可接受的最大兜底陈旧时间。要求事务内读己之写或绝对强一致的查询使用 `CacheMode.NONE`。

### 并发与对象边界

同一个 Key 回源时使用分段锁和二次检查，降低缓存到期瞬间的重复查询。缓存保存查询增强前的数据；返回给业务前会复制模型并创建当前调用需要的懒加载/懒同步代理，旧事务代理不会被保存复用。

Ehcache 使用 JSON 字节保存结果，实体应符合 Jackson 的反序列化要求。连接、流、Statement、ResultSet 等资源对象不能作为缓存查询结果。

### 启动规则

| 项目依赖 | 启动结果 |
| --- | --- |
| 不引入缓存模块 | 正常启动；`CacheClient.available()`为 false，缓存优先查询直接访问数据源 |
| 只引入 `cache-caffeine` | 启用内存缓存 |
| 只引入 `cache-ehcache` | 启用 Heap + Disk 缓存 |
| 同时引入两个实现 | 启动失败并报告多个缓存 Provider |

手动注册新的 Session 时，`RouteSession.registerSession` 会把当前缓存 Provider 注入 Session。第三方数据源继承 `AbstractSession` 即可获得相同行为；直接实现 `EntitySession` 时，需要实现 `setQueryCache` 才能接入查询缓存。
