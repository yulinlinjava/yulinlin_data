# MySQL 接入

[文档首页](../README.md) / [数据源](README.md)

配置 MySQL 数据源和默认会话。

共同规则：[自动建表与索引](schema.md) · [多数据源与默认组](routing.md) · [SQL 与事务日志](../operations/sql-log.md)。

## 本页导航

- [MySQL 接入](#mysql-接入-1)
- [日期时间编码](#日期时间编码)
- [索引选择与 useIndex](#索引选择与-useindex)

## MySQL 接入

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
    log-sql-pretty: true # 默认开启日志 SQL 美化，实际执行 SQL 不变
    max-param-length: 256
    slow-sql-threshold: 1s
    map-underscore-to-camel-case: true
    parallel-connections: 4
    execute-batch-size: 256
    schema-mode: CREATE
    schema-packages:
      - demo.domain
```

正常 Boot 自动配置链下无需框架启用注解。mysql 模块创建 `mysqlSessionFactory` 和 `mysqlSession`，默认 group 为 `mysql`。为兼容已有生产项目，`schema-mode` 默认 `NONE`；不需要框架管理结构时删除整个 `yulinlin.mysql` 段并由迁移脚本准备表。设为 `CREATE` 后，启动时递归扫描 `schema-packages`，创建缺失的表、普通列和声明索引，并校验已有结构；`VALIDATE` 只校验，缺失直接阻止启动。完整实体与 CRUD 见 [ORM 开发](../orm/README.md)。

## 日期时间编码

MySQL 模块将 `java.util.Date`、框架 `com.yulinlin.data.lang.util.DateTime` 和 `java.time.LocalDateTime` 统一编码为 **LocalDateTime**，由 JDBC `setObject` 绑定，使用 DATETIME 的本地日期时间语义。实体字段和查询条件参数使用相同规则，无需新增配置。

| Java 类型 | 编码规则 |
| --- | --- |
| `Date` | 按 JVM 默认时区提取年月日时分秒，保留毫秒 |
| 框架 `DateTime` | 直接取内部 LocalDateTime，保留小数秒 |
| `LocalDateTime` | 原值传递 |
| `LocalDate` | 原值传递，直接绑定 DATE |
| `LocalTime` | 原值传递，直接绑定 TIME；编码器保留小数秒 |

例如应用 JVM 使用 `Asia/Shanghai`，同一个本地时间的两种表示：

```java
import com.yulinlin.data.lang.util.DateTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

LocalDateTime local = LocalDateTime.of(2026, 10, 23, 21, 12, 38, 123_000_000);
Date payTime = Date.from(local.atZone(ZoneId.systemDefault()).toInstant());
DateTime crtTime = DateTime.date(local);
// 将 payTime / crtTime 赋给实体字段，或作为 Wrapper 条件值。
// 两者都会编码为 LocalDateTime：2026-10-23T21:12:38.123。
```

即使 JDBC URL 使用 `serverTimezone=GMT`，这两种值也按相同的本地日期时间数字传入驱动。编码器输出的是有类型的时间值，不格式化为字符串；日志中的 `T` 是 LocalDateTime 的显示形式。DATETIME 列的小数秒精度决定最终存储精度，例如保留上述毫秒需使用 `DATETIME(3)` 或更高精度，见 [MySQL 小数秒精度](https://dev.mysql.com/doc/refman/8.4/en/fractional-seconds.html)。

读取 DATETIME 到 Date 时，仍按 JVM 默认时区将本地数字还原为时间点；应用各实例应使用一致的 JVM 时区。这里“保持数字不变”针对 DATETIME，手工建立的 TIMESTAMP 列仍受数据库会话时区规则影响，见 [MySQL 时间语义](https://dev.mysql.com/doc/connector-j/en/connector-j-time-instants.html)。历史已写入的偏移数据不会自动修正。

回归测试见 [MysqlDateCoderTest](../../mysql/src/test/java/com/yulinlin/jdbc/mysql/coder/MysqlDateCoderTest.java)：覆盖不同 JVM 时区、Date/DateTime 一致性、字段与条件参数，以及 GMT/UTC/东京连接时区下驱动的文本和二进制编码。测试使用真实 Connector/J 编码器，无需连接业务数据库；完整注册和解码规则见 [JDBC 编解码](../internal/jdbc.md#编解码)。

纯日期和纯时间与 H2、PG 统一采用 JDBC 4.2 的 `LocalDate` / `LocalTime`，字段和查询条件都不会先转成 `java.sql.Date` / `java.sql.Time`。例如 `LocalTime.parse("13:14:15.123456")` 可向 `TIME(6)` 列传递完整微秒；普通 `TIME` 默认不保留小数秒。编码器保留 Java 值中的纳秒，MySQL 驱动和列最多处理六位小数秒，最终舍入由驱动配置和列精度决定；若显式设置 `sendFractionalSeconds=false`，驱动仍会去掉小数秒。框架不覆盖这些原生选项，不自动修改列精度或修复历史丢失的数据。

`LocalTime` 表示一天中的时间，不用于映射 MySQL TIME 的负数或超过 24 小时的时长。原有整秒值、NULL 和旧 `java.sql.Date` / `Time` 解码继续兼容；自定义扩展若强制将编码值转成这两个旧 JDBC 类型，需要改为接受 `LocalDate` / `LocalTime`。[纯日期与时间测试](../../mysql/src/test/java/com/yulinlin/jdbc/mysql/coder/MysqlLocalTemporalCoderTest.java) 覆盖字段、条件、旧值读取、闰日及驱动文本/二进制编码。

## 索引选择与 useIndex

查看自动选择的执行计划可调用 `query.explain()`；查看分页总数的计划使用 `query.explainCount()`。两者返回数据库原生计划行，只执行普通 EXPLAIN，不启用 ANALYZE，详见[查看执行计划](../orm/query.md#查看执行计划)。

MySQL 模块由数据库优化器自动选择索引。统一查询 API 的 `.useIndex("idx_name")` **只对 H2 生效**；MySQL 接受合法提示并忽略，不生成 `USE INDEX` 或 `FORCE INDEX`，不因调用此方法或提示中的索引不存在而报错。这是框架的兼容策略。

普通查询、分组、分页及派生 count 均遵循这一规则，生成的 SQL 和绑定参数与未指定提示时一致。业务仍需建立合适的索引；`@JoinIndex` 的声明、创建与校验规则不变。非法参数和真实数据库错误仍按原规则处理；完整用法、请求复制及缓存行为见 [H2 索引提示](h2.md#单次查询索引提示)。

---

下一步：返回[数据源主题目录](README.md)按需选择相关专题。
