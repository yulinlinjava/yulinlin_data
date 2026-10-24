# 自动建表、列类型与索引

[文档首页](../README.md) / [数据源](README.md)

显式声明结构拥有者，再配置扫描；CRUD 不触发建表。

## 本页导航

- [启动扫描与表结构拥有者](#启动扫描与表结构拥有者)
- [文本列、长度与说明](#文本列长度与说明)
- [显式允许修改列类型](#显式允许修改列类型)
- [表结构错误排查](#表结构错误排查)
- [预览建表 SQL](#预览建表-sql)
- [Schema 边界](#schema-边界)
- [MySQL/PostgreSQL/H2/SQLite 自动索引](#mysqlpostgresqlh2sqlite-自动索引)

## 启动扫描与表结构拥有者

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

## 文本列、长度与说明

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

自动 Schema 会识别框架支持的时间类型：

| Java 类型 | MySQL | PostgreSQL | H2 | SQLite |
| --- | --- | --- | --- | --- |
| `java.util.Date`、框架 `DateTime`、`LocalDateTime` | `DATETIME` | `TIMESTAMP WITHOUT TIME ZONE` | `TIMESTAMP` | `TEXT` |
| `LocalDate` | `DATE` | `DATE` | `DATE` | `TEXT` |
| `LocalTime` | `TIME` | `TIME WITHOUT TIME ZONE` | `TIME` | `TEXT` |

各数据库模块自己的 `coder` 包提供 `MysqlCoderManager`、`PostgresqlCoderManager`、`H2CoderManager`、`SqliteCoderManager`，均继承 `JdbcCoderManager`，内部注册本模块五种时间类型的编码器。具体时间规则仍分别定义在本模块的 `*TemporalCoders` 中；公共转换复用 `TemporalConversions`，空值/接口适配复用基类。工厂直接使用对应管理器，不传编码器列表，也不注入全局管理器；普通业务无需新增配置。不同数据库的管理器与编码规则独立，修改 H2 编码器不会修改其他模块。

- MySQL、PostgreSQL、H2 各自的编码器使用原生本地时间参数：`java.util.Date` 按 JVM 默认时区提取日期时间数字后编码为 `LocalDateTime`，框架 `DateTime` 编码为其内部 `LocalDateTime`，`LocalDateTime` 原样传给驱动。三者都不向驱动传入 `java.sql.Timestamp`，避免驱动按时间点再转换时区。分别对应 `DATETIME`、`TIMESTAMP WITHOUT TIME ZONE` 和不带时区的 `TIMESTAMP`，不依赖 VARCHAR 的隐式转换；规则目前相同，但实现分别维护。
- `Date` 读取无时区日期时间时按 JVM 默认时区还原；写入、查询条件、读取使用对称规则。例如 JVM 本地 `2026-10-23 21:12:38` 写入 MySQL `DATETIME` 后仍为 `2026-10-23 21:12:38`，不会因连接使用 UTC 而变成 `13:12:38`。`LocalDate` 仍编码为 `java.sql.Date`。MySQL / H2 的 `LocalTime` 编码为秒级 `java.sql.Time`；PostgreSQL 则直接绑定 `LocalTime`，保留目标 `time(p)` 列允许的小数秒。
- SQLite 自己的编码器直接输出 TEXT：`DateTime` / `LocalDateTime` / `Date` 为 `yyyy-MM-dd HH:mm:ss`，`LocalDate` 为 `yyyy-MM-dd`，`LocalTime` 为 `HH:mm:ss`。格式器也定义在 SQLite 模块，保留旧数据的空格分隔、秒级精度；写入和查询条件使用相同规则，日期范围比较不混用带 `T` 的格式。原始参数绑定中的时间对象也调用 SQLite 自己的编码器。
- JDBC 解码支持 `LocalDateTime`、旧驱动返回的 `Timestamp`，以及空格或 `T` 分隔、可带 1～9 位小数秒的日期时间文本。`DateTime` / `LocalDateTime` 的文本直接解析为本地时间，不经 `Timestamp` 中转；还原为 `Date` 时再按 JVM 本地时区转换。MySQL/PostgreSQL/H2 实际保存的小数秒精度仍由列定义与驱动决定，SQLite 保持秒级存储。
- core 的 `DateTimeCoder`、JSON 展示，以及 MongoDB/Elasticsearch 的原有时间规则不变。扩展数据库时定义自己的 `CoderManager` 并在内部注册编码器，自行声明工厂时例如传入 `new H2CoderManager()`，参见 [编解码扩展](../internal/jdbc.md#编解码)。`SqlParseManager` 仍只负责 SQL 解析，字段编解码沿用 `ICoderManager`。

上述规则优先保证“日期时间数字不变”，不修改 JVM 或数据库时区，也不硬编码加减 8 小时。`DateTime.now()` 仍取 JVM 本地时间。`java.util.Date` 本身只有时间点，没有独立时区，因此这里以 JVM 默认时区解释它；多实例应统一 JVM 时区，且不要在运行中切换，否则同一数据库日期时间还原的 epoch 毫秒数可能不同。无时区列也无法保留夏令时回拨时重复时刻的原始偏移；需要跨时区保留绝对时间点的字段应另行定义存储和编码规则。

手工建成 MySQL `TIMESTAMP`、PostgreSQL/H2 带时区列的字段仍有数据库自身的时区语义，不能据此认为所有列都不再换算；需要数字不变时应使用上表的不带时区列。这里不会自动改列类型。历史上已经写偏的值不会自动修复，不要直接对整表统一加减 8 小时。升级时同步更新 core、jdbc 及项目使用的 mysql/postgresql/h2/sqlite 模块，避免新旧注册方法、工厂和编码器混用。

时间字段不接受 `textType` 或 `textLength`；这两个选项只用于真正按文本编解码的字段。

字符串主键未指定长度（`textLength=0`）时统一创建为 `VARCHAR(32)`；显式指定正整数 `textLength` 时按注解生成，不再限制为最多 128。长度单位是字符，不是 bit；例如下面表示最多 64 个字符的字符串主键。数据库自身的列长度、主键索引字节数等限制仍然适用，大文本仍禁止作为主键或框架自动索引。

```java
@JoinField(textLength = 64)
@JoinMeta(primaryKey = true)
private String id;
```

MySQL/PostgreSQL/H2 会约束 VARCHAR 长度，SQLite 只保留 `VARCHAR(n)` 声明，不强制字符长度。默认值变更仅影响新建列，不自动缩短已有主键；未显式指定长度时，不校验已有列长度。显式指定长度时按容量兼容：已有列长度大于等于要求即可通过，例如实体要求 32、旧列为 128，无需改表；实体要求 64、旧列只有 32，默认报错，可手动扩容或显式开启下文的 `alter-column-type`。此规则同时适用于字符串主键和普通 VARCHAR 字段；主键约束缺失仍报错，默认不自动修改已有类型。

`description` 最多 1024 个字符。新建表或补充新列时，MySQL 写入列 `COMMENT`，PostgreSQL/H2 执行 `COMMENT ON COLUMN`；SQLite 没有原生列注释，因此只接受该元数据但不落库。已有列不会因为 description 变化而执行 ALTER，注释也不参与兼容性校验。

这些选项只服务于启动 Schema 描述，不改变运行期 SQL 编解码器，也不会在 Java 端截断或校验字符串。MySQL/PostgreSQL/H2 对显式 VARCHAR 长度只检查已有列容量是否足够，实际可写长度由数据库列决定；SQLite 不执行或校验 VARCHAR 长度限制，确需强制长度应使用业务校验或自行添加 CHECK 约束。

SQLite 的整数/布尔值对应 INTEGER，浮点数对应 REAL，其余默认 TEXT。日期、枚举、BigDecimal、JSON 对象等使用 TEXT；字符串与数字仍由 JDBC 编解码器恢复 Java 字段。日期范围依赖统一、可按字典序排序的固定格式与时区；TEXT BigDecimal 的字符串比较不等于数值比较，数值范围或统计需要适当列类型或显式 SQL CAST。

自动 Schema 默认不会修改已有列类型。升级后，如果时间字段原来由自动建表创建成 VARCHAR/TEXT，会报告类型不兼容；可以先通过数据库迁移脚本转换，或在人工确认格式与数据可转换后按下文显式启用改列。SQLite 的时间仍使用 TEXT，不需要该类型迁移。

启动完成后，CRUD 和自定义 SQL 均不再检查或修改 Schema。`BaseRequest.fromClass` 为 null、Object.class 或任意类型都不会触发 DDL；它原有的映射与路由语义不变。自定义 SQL 涉及的表若不在扫描实体中，应由迁移脚本或其他初始化流程创建。

## 显式允许修改列类型

仅在用户已人工确认现有数据可转换、且已备份时启用。开关默认 `false`，各数据源独立配置，不改变其他数据源：

```yaml
yulinlin:
  mysql:
    schema-mode: CREATE
    schema-packages:
      - "com.example.domain"
    alter-column-type: true
  postgresql:
    alter-column-type: false
  h2:
    alter-column-type: false
```

只处理扫描中 `@JoinTable(autoSchema=true)` 的结构拥有者，启动阶段执行；`schema-mode=NONE` 不操作，`VALIDATE` 即使开启此开关也只校验。不扫描业务数据提前判断转换是否安全，失败由数据库报告；该开关不是忽略校验。

| 现有结构与实体目标 | 开关开启后的 CREATE 行为 |
| --- | --- |
| 列存储类型不同 | 按实体当前 Schema 映射生成改类型 DDL，执行后重新读取结构校验 |
| 同为字符串，显式要求 64，已有 32 | 扩容至 64 |
| 同为字符串，要求 32，已有 128 | 保留 128，不缩短 |
| 未显式指定长度 | 不因默认长度变化而修改旧列长度 |
| 主键缺失或主键标记不一致 | 仍报错，不自动增删主键 |
| SQLite 类型 affinity 不一致 | 明确提示不支持自动改类型，需手动重建表并迁移数据 |

数据库边界：

- MySQL 使用 `MODIFY COLUMN`，读取并保留已有非空、普通字面量默认值、注释，以及仍为文本时的字符集/排序规则；不以注解中的新 description 覆盖已有注释。要求连接启用严格 SQL 模式。含表达式默认值、ON UPDATE、不可见、UNSIGNED/ZEROFILL、ENUM/SET 等复杂属性的待修改列，保守拒绝自动修改。
- PostgreSQL 使用 `ALTER COLUMN TYPE ... USING CAST(...)`；H2 使用 `ALTER COLUMN ... SET DATA TYPE ...`。不主动删除默认值、注释、主键或索引来促成转换；默认值、约束、视图或依赖对象不能适配新类型时停止启动，请手动迁移。
- 三个支持的数据库均拒绝自动修改自增/标识列、生成列、引用外键或被外键引用的列。其他索引与约束交给数据库原生改列语句保留，数据库拒绝时不会自动删约束重试。
- 目标类型沿用现有实体映射，不新增 `columnDefinition` 或任意 `USING` 表达式配置；例如 String 改为 Long 才对应字符串转 BIGINT，BigDecimal 仍遵循各模块既有文本存储规则。

转换可能改变数据含义，例如 `"001"` 变成 `1`、小数转整数、时间解析、类型转换后唯一键冲突。需人工确认值域、格式、精度和唯一性，框架不保证无损。不执行同类字符串的长度缩短，但其他类型转换仍可能有精度/值域变化。

所有执行的 DDL 由 Session 先打印完整 SQL；改列失败的异常包含中文说明及对应 SQL，启动中止。大表可能锁表或重写数据，多列/多表修改不保证整体回滚，已经完成的 DDL 可能保留；修复后重新启动会读取实际结构，不做自动补偿。建议在维护窗口执行，完成迁移后关闭此开关。

## 表结构错误排查

MySQL、PostgreSQL、H2、SQLite 的结构冲突使用中文提示，列出数据库类型、表/列、原因、实体要求、数据库现有结构及处理方式，不再直接输出内部 `Column[...]` 对象。例如：

```text
数据库表结构检查失败
数据库类型：mysql
对象：orders.amount
原因：字段存储类型不同
实体要求：BIGINT，非主键
数据库现有：VARCHAR(255)，非主键
处理方式：自动改列未开启。请手动迁移；或确认数据可转换并备份后，
设置 yulinlin.mysql.alter-column-type: true，并确保
yulinlin.mysql.schema-mode: CREATE。修改类型可能锁表或改变数据精度
```

| 提示原因 | 处理方式 |
| --- | --- |
| 类型不同，或显式字符串容量不足 | 先核对实体是否正确。确需迁移时，备份并人工确认数据可转换，再手动改列或使用支持数据库的 `CREATE + alter-column-type=true` |
| 当前为 `VALIDATE` | 只检查，不执行 DDL；开启改列开关也不会修改。手动迁移，或明确切换 `CREATE` |
| 主键标记/字段集合不一致，缺失主键列 | 核对 `@JoinMeta(primaryKey=true)`；检查空值、重复值和外键后手动迁移。改列开关不负责增删主键 |
| 索引定义冲突 | 核对字段顺序、唯一性及索引形式；手动迁移，不会自动删除或替换已有索引 |
| SQLite 列类型不兼容 | 备份后手动重建表并迁移数据；改列开关不能解除 SQLite 的实现限制 |
| 自增、生成列、外键或特殊列属性 | 提示具体受限原因；使用迁移脚本处理，不会为了转换而删除约束 |
| 缺失表、普通列或索引 | `VALIDATE` 下手动补齐或切换 `CREATE`；`CREATE` 下仍缺失时检查实际数据库、权限及前面的 DDL 日志 |
| 同一张表存在多个自动建表实体 | 同一表只保留一个完整实体的 `autoSchema=true`；DTO 不开启；测试实体放在测试源码且位于业务扫描包之外 |
| DDL 执行失败 | 查看失败 SQL 与数据库原始错误，核对值域、格式、唯一性、默认值、约束和权限；检查此前 DDL 是否已生效再恢复 |

较长的已有 VARCHAR 不视为冲突：要求 32、已有 128 时继续使用，不缩短、不报错。提示中的类型是框架归一化后的存储类型；精确原始定义以数据库元数据/建表语句为准。`数据库类型` 不是自定义数据源 group，定位连接请结合 Session 的 `[group][schema]` 日志。

框架说明使用中文，数据库驱动原始错误原样保留，异常链及 SQLState/错误码也保留。结构 SQL 失败会中止初始化，但多条 DDL **不保证整体回滚**；不能将“重启失败”理解为“数据库完全没改动”。

## 预览建表 SQL

`EntitySession` 统一提供 `createTableSql(Class<?>)`。它只根据当前 Session 的数据库方言和字段命名设置生成 SQL，不获取连接、不执行 DDL；返回列表依次包含建表、列注释和声明索引。只有显式 `autoSchema=true` 的完整简单表实体会生成 DDL，投影、统计模型、null 和 Object.class 返回空列表：

```java
@Resource(name = "mysqlSession")
private EntitySession mysqlSession;

List<String> ddl = mysqlSession.createTableSql(DemoUser.class);
ddl.forEach(System.out::println);
```

`initializeSchema(Collection<Class<?>>)` 才执行初始化。MySQL、PostgreSQL、H2、SQLite 和 Elasticsearch 的启动扫描调用该 Session 接口；JDBC Session 自己管理连接和 Statement，并在执行每条语句前以 INFO 输出 `[group][schema]` 和完整 DDL。预览和实际初始化共用相同的结构描述函数。`schema-mode=NONE` 会跳过自动初始化，但不妨碍手动调用 `createTableSql` 预览。

## Schema 边界

这不是完整的版本迁移工具：`CREATE` 会创建缺失表、缺失普通列和缺失的声明索引；新增列通过 `ALTER TABLE ... ADD COLUMN` 执行。MySQL/PostgreSQL/H2 只有显式开启 `alter-column-type` 才允许上述受限的类型修改和扩容。它不会自动增加或修改主键，也不删除旧列/旧索引或添加外键、复合主键。框架无法识别字段改名：改名会被视为“增加新列”，旧列和旧数据仍保留，但数据不会自动搬到新列，因此改名必须使用业务迁移脚本。所有实际执行的 DDL 都会先以 INFO 日志打印，并且发生在会话 Bean 初始化阶段，不进入后续业务事务。`createTableSql` 只预览新建结构，不读取现有元数据，因此不是改列迁移计划。

SQLite 使用继承 JdbcSession 的 SqliteSession，复用公共 CRUD；不需要 DataSource Bean 或 sqliteTransactionManager。SqliteDatabase 持有并关闭内部连接池。`supportsParallelWrites()` 返回 false，多连接批处理不会拆组；WAL 也不能让同一文件同时拥有多个写事务。

SQLite 不支持框架生成的 FOR UPDATE、日期分组和数值间隔分组，当前解析器明确拒绝；需用自定义 SQL 实现数据库专用功能。运行中不要单独删除 -wal/-shm 文件，也不要仅复制主文件作为可靠备份。NORMAL 偏向性能，FULL 更重视持久性；这不是业务吞吐量承诺。

## MySQL/PostgreSQL/H2/SQLite 自动索引

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

---

下一步：返回[数据源主题目录](README.md)按需选择相关专题。
