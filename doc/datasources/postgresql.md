# PostgreSQL 接入

[文档首页](../README.md) / [数据源](README.md)

接入 PostgreSQL、JSON 整字段存储、聚合与真实数据库回归，以及中文全文检索前置配置。

共同规则：[自动建表与索引](schema.md) · [多数据源与默认组](routing.md) · [SQL 与事务日志](../operations/sql-log.md)。

## 本页导航

- [PostgreSQL 接入](#postgresql-接入-1)
- [索引选择与 useIndex](#索引选择与-useindex)
- [JSON 整字段存储](#json-整字段存储)
- [日期与聚合](#日期与聚合)
- [运行 PostgreSQL 回归测试](#运行-postgresql-回归测试)
- [PostgreSQL 中文全文检索](#postgresql-中文全文检索)

## PostgreSQL 接入

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

现有编码器可能把枚举、BigDecimal 或对象编码为字符串；已支持的日期时间类型由 JDBC 编码器以对应类型绑定，`DateTime` / `LocalDateTime` 不依赖 `stringtype=unspecified`。该参数让驱动按目标列或 SQL 上下文推断字符串类型；它不是框架自动添加的参数，也不解决所有类型问题。无明确类型上下文时可在可信 SQL 中显式 CAST，例如 `CAST(#{payload} AS jsonb)`。不要推定原生 enum、UUID、数组、空间类型已有完整专用编解码支持。

标识符采用双引号，schema 限定名按段引用；实际列名应与模型映射一致。普通 CRUD 和实体可复用，原始 MySQL SQL、反引号、函数与 DDL 不会被自动翻译。自动 Schema 不负责序列、自增键、外键、分区表或数据迁移。

JSON 只保留整字段编解码、读取和替换；JDBC Wrapper 不再解析 `payload->profile->name`，也不自动生成 JSON 查询、类型推断或 `jsonb_set`。路径操作请显式编写参数化原生 SQL，具体迁移规则见[JSON 字段与原生 SQL](../orm/raw-sql.md#json-字段与原生-sql)。

JDBC 参数统一使用 `setObject`，业务参数不应直接携带 InputStream、bytea/oid 等二进制对象；需要保存时应先由明确的编解码策略转成文本，或把文件放到对象存储后只保存地址。原生布尔列保留 false/NULL；除布尔列外结果大多沿用字符串解码，不能推定 timestamptz 等扩展类型完整往返已验证。

### 索引选择与 useIndex

查看自动选择的执行计划可调用 `query.explain()`；查看分页总数的计划使用 `query.explainCount()`。两者返回数据库原生计划行，只执行普通 EXPLAIN，不启用 ANALYZE，不依赖 PG 扩展，详见[查看执行计划](../orm/query.md#查看执行计划)。

PostgreSQL 模块由数据库优化器自动选择索引。统一查询 API 的 `.useIndex("idx_name")` **只对 H2 生效**；PG 接受合法提示并忽略，不因调用此方法或提示中的索引不存在而报错。框架不为此接入 `pg_hint_plan`，也不修改数据库的优化器设置。

普通查询、分组、分页及派生 count 均遵循这一规则，生成的 SQL 和绑定参数与未指定提示时一致。业务仍需建立合适的索引；`@JoinIndex` 的声明、创建与校验规则不变。非法参数和真实数据库错误仍按原规则处理；完整用法、请求复制及缓存行为见 [H2 索引提示](h2.md#单次查询索引提示)。

### JSON 整字段存储

JSON 数据仍可存储在已有的 `jsonb`、`json` 或文本列中。Map、List、对象继续使用现有 JSON 编解码器，查询读取整个字段，更新替换整个字段；本次不迁移列类型或已有数据。自动 Schema 仍按原有策略处理，**不自动推断为 jsonb**。

下面是使用手工建表的示例实体，保持 `autoSchema=false`（默认）：

```java
package demo.domain;

import com.yulinlin.common.domain.IdEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import java.util.Map;

@JoinTable("pg_document_demo")
public class PgDocument extends IdEntity<PgDocument> {
    @JoinField(name = "document_data")
    private Map<String, Object> payloadData;

    public Map<String, Object> getPayloadData() { return payloadData; }
    public void setPayloadData(Map<String, Object> value) { payloadData = value; }
}
```

仅在自己的示例数据库准备表和数据：

```sql
CREATE TABLE pg_document_demo (
    id VARCHAR(32) PRIMARY KEY,
    document_data JSONB
);
INSERT INTO pg_document_demo VALUES
    ('demo-1', '{"profile":{"name":"旧名字"},"enabled":false}');
```

整字段替换和读取：

```java
import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import demo.domain.PgDocument;
import java.util.Map;

int affected = ModelUpdateWrapper.newInstance("postgresql", PgDocument.class)
        .field("payloadData", Map.of(
                "profile", Map.of("name", "张三"),
                "enabled", true))
        .eq("id", "demo-1")
        .execute();

PgDocument document = ModelSelectWrapper.newInstance("postgresql", PgDocument.class)
        .eq("id", "demo-1")
        .selectOne();
```

`payloadData` 仍按实体映射转换为 `document_data`，Map 按原来的编码器序列化。普通整字段参数沿用上文 `stringtype=unspecified` 配置，让 PG 根据目标列推断字符串类型；不自动补 JSON CAST。

整字段更新是**替换**，不是 JSON 合并；读取后修改再写回还需处理并发覆盖，不能把它当成路径原子更新。需要数据库端 JSON 查询、局部更新或特定转换时，使用[参数化原生 SQL 示例](../orm/raw-sql.md#json-字段与原生-sql)。PG 本身的 JSON 能力没有被禁用，仅取消 ORM 的自动翻译层。

### 日期与聚合

`Date` 按 JVM 默认时区转换为当地 `LocalDateTime`；`DateTime` / `LocalDateTime` 按日期时间值绑定。搭配 `timestamp without time zone` 列时保持年月日时分秒及列精度允许的小数秒，不因数据库会话时区改变而额外转换。JVM 时区应保持明确且一致；这不是 `timestamptz` / 跨时区瞬时时间的统一策略。

`LocalTime` 直接作为 JDBC 4.2 原生参数传给驱动，不再先转成 `java.sql.Time` 截去小数秒；例如 `13:14:15.123456` 可在 `time(6)` 列往返。最终舍入由驱动和目标列精度决定，不承诺纳秒全部入库；原来已经丢失的小数秒无法恢复。自定义扩展若曾假定编码结果是 `java.sql.Time`，应改为接受 `LocalTime`。读取仍兼容旧 `Time` 和字符串。驱动类型映射见 [pgJDBC 日期时间支持](https://jdbc.postgresql.org/documentation/query/#using-java-8-date-and-time-classes)。

数字区间使用 `numeric` 精确取余计算上界，避免 `FLOOR(x / step)` 在高精度边界前先舍入。例如步长为 3，`2.99999999999999999999 → 3`、`3 → 6`、`-3.00000000000000000001 → -3`。步长必须为正整数，NULL 保留；浮点数据自身已有的精度损失不会被修复。日期维度、季度格式、HAVING 和完整边界说明见[统计分析](../orm/analytics.md)。旧版舍入错误影响的持久化统计结果需要业务重新计算。

### 运行 PostgreSQL 回归测试

默认测试包含不需要数据库的解析/日期编码器用例。真实数据库测试须显式提供测试库 URL，否则跳过，不自动连接生产库或安装数据库。本轮在 PostgreSQL 18 执行，不能据此宣称验证了所有 PG 版本或 pg_jieba 插件。

从仓库根目录运行（需 JDK 25、Maven 及可访问的专用测试库）：

```text
mvn -pl postgresql -am test "-Dtest=Postgresql*Test" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dpg.test.url=jdbc:postgresql://127.0.0.1:5432/orm_test?stringtype=unspecified" "-Dpg.test.user=orm_test"
```

密码由环境变量 `PG_TEST_PASSWORD` 提供，不写入测试代码或命令参数。账号需有连接和创建临时表权限；用例仅创建连接级临时表，读写使用 `pg_temp` 限定名，连接关闭后清理，不创建或删除业务表。完整 CRUD 沿用上文 URL 的字符串类型推断配置。

覆盖入口：[JDBC API 边界](../../jdbc/src/test/java/com/yulinlin/jdbc/sql/JdbcJsonBoundaryTest.java)、[整字段编解码](../../jdbc/src/test/java/com/yulinlin/jdbc/coder/JdbcJsonColumnCoderTest.java)、[真实 JSON 整字段/原生 SQL](../../postgresql/src/test/java/com/yulinlin/jdbc/postgresql/PostgresqlJsonStorageIntegrationTest.java)、[真实聚合与高精度边界](../../postgresql/src/test/java/com/yulinlin/jdbc/postgresql/PostgresqlAggregationIntegrationTest.java)、[日期、布尔值与 JDBC CRUD/回滚](../../postgresql/src/test/java/com/yulinlin/jdbc/postgresql/PostgresqlJdbcIntegrationTest.java)。最后一项通过 JDBC 连接验证事务回滚，不代替 Spring 事务传播/多数据源事务专项测试。

### PostgreSQL 中文全文检索

全文检索是 PostgreSQL 模块的可选能力，中文分词依赖数据库服务器已安装 `pg_jieba`。框架不会下载或安装 PostgreSQL 扩展二进制；运维先按服务器版本安装插件，再由有权限的账号执行：

```sql
CREATE EXTENSION IF NOT EXISTS pg_jieba;
```

默认索引配置是 `jiebacfg`，查询配置是 `jiebaqry`。只有结构拥有者实体上显式声明 `@JoinField(fullText = true)` 的文本列才生成 GIN 表达式索引。`schema-mode=CREATE` 会创建缺失索引，`VALIDATE` 只校验；初始化遇到配置不存在时会直接说明需要安装/启用 pg_jieba。修改 index-config 会使用新的索引名称创建新索引，旧索引不会自动删除。

`index-config` 同时用于 `to_tsvector`、GIN 索引和 `ts_headline`，不能随意设置为不同分词配置；`query-config` 用于 `websearch_to_tsquery`。`max-words/min-words` 是 PostgreSQL 专属摘要长度；高亮标签、片段数和分隔符来自所有 EntitySession 共用的 `highlight` 配置，并作为 JDBC 参数绑定，不拼接用户输入。默认标记不是 HTML，前端可安全地按 `__HL_START__` / `__HL_END__` 转义后渲染；若改成 `<mark>`，仍须遵循项目的 XSS 输出策略。查询用法见[全文匹配与高亮](../orm/search.md#全文匹配与高亮)。

MySQL、SQLite、H2 当前不会创建全文索引：`fullText=true` 被忽略，`match()` 退化为包含式 LIKE，`highlight()` 返回原字段。这保证同一业务调用可以运行，但不代表大表查询具有全文检索性能。

---

下一步：返回[数据源主题目录](README.md)按需选择相关专题。
