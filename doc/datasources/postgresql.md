# PostgreSQL 接入

[文档首页](../README.md) / [数据源](README.md)

接入 PostgreSQL 与中文全文检索前置配置。

共同规则：[自动建表与索引](schema.md) · [多数据源与默认组](routing.md) · [SQL 与事务日志](../operations/sql-log.md)。

## 本页导航

- [PostgreSQL 接入](#postgresql-接入-1)
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

JSON 读取支持合法 JSON 的路径，例如 `payload->profile->name`；数字/布尔条件按比较值生成 CAST。路径部分更新尚未自动生成 jsonb_set，应整字段替换或写专用 SQL。非法 JSON 和不能转换的值会报错，不是静默跳过。

JDBC 参数统一使用 `setObject`，业务参数不应直接携带 InputStream、bytea/oid 等二进制对象；需要保存时应先由明确的编解码策略转成文本，或把文件放到对象存储后只保存地址。原生布尔列保留 false/NULL；除布尔列外结果大多沿用字符串解码，不能推定 timestamptz 等扩展类型完整往返已验证。

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
