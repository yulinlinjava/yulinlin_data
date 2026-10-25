# SQLite 接入

[文档首页](../README.md) / [数据源](README.md)

单文件本地数据库、WAL 和单写约束。

共同规则：[自动建表与索引](schema.md) · [多数据源与默认组](routing.md) · [SQL 与事务日志](../operations/sql-log.md)。

## 本页导航

- [SQLite 接入](#sqlite-接入-1)

## SQLite 接入

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

日期时间沿用 SQLite 的固定 TEXT 适配：Date / DateTime / LocalDateTime 编码为 `yyyy-MM-dd HH:mm:ss`，LocalDate 为 `yyyy-MM-dd`，LocalTime 为 `HH:mm:ss`。Date 按 JVM 默认时区提取本地数字；原生参数绑定的兜底也使用相同格式。该模块保留秒级精度与历史存储格式，不随 MySQL/H2/PG 的原生 Java 时间参数统一而改变；类型契约见 [JDBC 编解码](../internal/jdbc.md#编解码)。

---

下一步：返回[数据源主题目录](README.md)按需选择相关专题。
