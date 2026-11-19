# 数据库监控与 SQL 风险检查

[文档首页](../README.md) / [监控与排障](README.md)

启用 monitor，查看慢操作统计、可疑 SQL，并在 MySQL 执行前进行风险检查。

## 本页导航

- [慢数据库监控](#慢数据库监控)
- [MySQL SQL 风险检查](#mysql-sql-风险检查)
- [SQL 检测缓存](#sql-检测缓存)
- [自定义检测器](#自定义检测器)

## 慢数据库监控

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
    sql-risk:
      mode: block # 默认 block；observe 只告警；off 关闭检查
```

模块默认启用；设置 `yulinlin.monitor.enabled=false` 后，不注册数据库采集器、SQL 监听器、管理接口和页面，访问 `/monitor/` 也不会得到模块页面。启用时访问 `/monitor/` 可打开内置只读页面；页面使用 Vue 3 和锁定版本的 Element Plus 2.14.7，通过 jsDelivr 加载前端资源。部署环境无法访问公共 CDN 时，应将对应资源转存到业务自己的静态资源域名，再替换模块页面中的地址。

页面可以直接打开；数据接口发现尚未验证时，页面会在原地显示账号密码对话框。验证成功后只在当前 HTTP Session 中保存一个布尔标记，再自动加载监控数据，不跳转登录页，也不依赖任何安全框架。账号默认是 `admin`，密码没有默认值，必须通过配置或环境变量提供；密码为空时登录接口拒绝验证，避免发布一个所有人都知道的默认密码。

监控会轻量聚合所有实际执行的 JDBC 操作，以 `Group + Session 类型 + 操作类型 + 来源实体 + SQL 模板` 为一组，展示执行次数、慢查询率、平均耗时、最大耗时和最近耗时。只有达到 `threshold` 的成功操作及数据库异常才保留明细，最多保存最近 `max-records` 条；SQL 聚合组也使用该数量作为内存上限。平均耗时基于全部执行计算，不会因为只统计慢操作而被放大。

页面按照优化优先级排序：`P0` 表示出现异常；`P1` 表示平均耗时达到阈值 3 倍或最大耗时达到 10 倍；`P2` 表示平均达到 2 倍、最大达到 5 倍或慢查询率达到 50%；`P3` 表示平均达到阈值、最大达到 2 倍或慢查询率达到 10%；`P4` 表示只有少量请求超过阈值；其余为正常。慢查询率参与等级判断前至少需要 10 次执行，避免首次慢请求直接产生过高等级。同等级依次比较异常次数、慢操作次数、慢查询率、平均耗时和最大耗时。

实体请求会显示来源实体、完整类名以及 `@JoinTable` / `@JoinTableList` 声明的表。自定义 SQL 的 `fromClass` 为 `null` 或 `Object.class` 时，来源显示“自定义 SQL”、表名显示 `-`，不会通过正则猜测表名。批量写入每个执行组记录一次；SQL 只保留模板，不采集绑定参数。多数据源按 `Session.group()` 和具体 Session 类型区分，`RouteSession` 不重复计数；缓存命中没有访问数据库，因此不会生成记录，延迟写队列最终执行 JDBC 时会自然纳入监控。

页面接口和 Java API 都只读查询监控数据；`DatabaseMonitor.clear()` 仅供应用内部按需调用。

## MySQL SQL 风险检查

这是 **monitor 模块自带的可选能力**：引入模块并启用 monitor 后，MySQL 默认 `block`；不引入 monitor 或设置 `yulinlin.monitor.enabled=false`，不会注册框架内置检测器与执行前检查适配器，JDBC 保持原来的行为。`sql-risk.mode=off` 仅关闭风险检查，慢操作监控仍然工作。监控页面未登录或未设置密码，不会关闭 SQL 检查。admin 已引入 monitor，因此也遵循这个默认值。

| 配置值 | 命中规则 | 检测器异常或解析失败 |
| --- | --- | --- |
| `block`（默认） | 执行前抛出 `SqlRiskException` | 拒绝执行，并标记“检查未完成” |
| `observe` | 记录告警，继续执行 | 记录“检查未完成”，继续执行 |
| `off` | 不调用检测器 | 不调用检测器 |

内置 `DruidSqlRiskDetector` 使用项目统一版本的 Druid `MySqlWallProvider`，独立检查 SQL，不更换连接池、不修改 SQL、不访问绑定参数。检查对象是最终准备提交 JDBC 的模板：`#{...}` 已变成 `?`，`${...}` 替换产生的 SQL 内容在检查范围内。检测先于 SQL 日志美化和监控截断。

内置规则禁止多语句执行、`DROP TABLE`、`TRUNCATE`、文件导出，以及 `SLEEP`、`BENCHMARK`、`LOAD_FILE` 函数，并保留 Druid 的其他 Wall 检查。对这三个函数和文件导出补充 Druid AST 检查，避免部分顶层 SELECT 被 Wall 默认规则豁免。允许普通注释、末尾分号、字符串中的分号；业务单条 CREATE TABLE / ALTER TABLE 仍需经过检查，不会仅因语句类型而拒绝。不是看到 `UNION`、注释或分号就认定攻击。规则命中表示违反当前策略，不代表已确认攻击；Druid 未覆盖的 MySQL 语法可能被标为“检查未完成”。

检查入口覆盖框架管理的 MySQL Session 的普通查询、分组、分页列表与 count、`selectEach`、`explain` / `explainCount`、原生查询和写入。Spring 注入的 `JdbcSessionFactory` 按 `jdbc:mysql:` 接入，H2、PostgreSQL、SQLite 等其他数据源跳过。手工 `new Session` 必须自行设置 `JdbcSqlGuard`；绕过框架直接使用 JDBC、维护连接回调内自行执行的 SQL、数据库触发器和存储过程内部语句不在此检查范围内。ORM 查询结果缓存命中时不执行 JDBC，因此不会触发此检查；这与下文的 SQL 检测缓存是两种不同的缓存。

**框架自动维护表结构按入口豁免，而不是按 SQL 关键词豁免：**

| 执行来源 | 是否检查 SQL 风险 |
| --- | --- |
| 框架依据模型自动建表、建索引、补字段、调整列类型（`initializeSchema` → `executeSchemaSql`） | 跳过检测器及风险记录，保留原有表结构变更与失败日志 |
| 业务通过 Wrapper、`QueryRequest` / `ExecuteRequest` 提交 SQL | 正常检查，包含业务主动执行的 CREATE / ALTER / CREATE INDEX / DROP |

自动维护表结构在 `block`、`observe` 模式下均不调用检测器，即使自定义检测器拒绝全部业务 SQL，也不会阻止框架建表和补字段。业务原生 SQL 不会因为以 `CREATE` 或 `ALTER` 开头就跳过检查，例如 `CREATE TABLE ...; DROP TABLE ...` 仍按多语句规则拒绝。该边界不依赖请求参数，也不提供业务请求级的绕过开关。

当前写请求先检查全部 SQL 模板，再开始任何 JDBC 写入或并行任务。同一请求中重复的模板只检查一次；正常 JDBC batch 不视为多语句 SQL。预检拒绝不会自动撤销此前其他请求已经完成的写入。`SqlRiskException` 交给业务方处理，框架不决定 HTTP 状态码和文案；异常逃出 `route.transaction(...)` 时按原有事务边界回滚，业务捕获检查拒绝后可按现有事务语义继续。

可疑 SQL 单独保存于有界内存记录，不计入已执行 SQL 的慢操作统计。记录数量上限复用 `max-records`，不受 `threshold` 限制；最多保留 4096 字符的 SQL、20 条原因（每条最多 1024 字符），重启后清空。SQL 模板可能包含业务自行写入的字面量，本功能不把绑定参数填入模板。

页面“可疑 SQL”区域展示时间、数据源、已拦截/仅告警、检查失败标记、原因和 SQL 模板。“仅告警”只表示允许继续执行，不表示数据库执行成功。登录后可调用 `GET /monitor/api/sql-risks?limit=200`，与其他监控 API 共用登录保护。业务代码也可以注入 `SqlRiskMonitor`，调用 `recent(limit)` 或 `clear()`。

## SQL 检测缓存

当前直接复用 Druid 自带的缓存，框架不额外包一层 Map。内置 `DruidSqlRiskDetector` 在创建时构造一个 `MySqlWallProvider`，后续请求持续复用；默认 Spring 单例下，同一进程中接入该检测器的 MySQL 数据源共享这份缓存，不按连接重新创建。

每次实际执行 SQL 仍调用检测入口，但命中 Druid 检测结果缓存时会复用之前的检查结果，不再完整执行 Wall 解析与规则检查。未命中时才进行完整检查，并按 Druid 的准入规则缓存。绑定参数不参与检测，所以 `WHERE id = ?` 的参数值改变不影响同一 SQL 模板的复用。

含分号的模板会在访问 Wall 缓存前额外使用 Druid MySQL 解析器检查语句数量，防止 Druid 1.2.28 将“单条 SQL＋末尾注释”和“注释后追加第二条 SQL”归并到同一缓存键而误放行。识别到多条语句直接返回违规；末尾分号、字符串、反引号标识符和普通注释中的分号仍正常检查，不按分号直接拆分字符串。此项检查每次执行，含分号的模板即使命中缓存也有额外解析开销；不增加第二层缓存。MySQL 可执行注释 `/*! ... */` 不属于普通注释，不能视为无条件放行。

以下容量对应项目当前使用的 **Druid 1.2.28**，均为每个 Provider 实例的内置容量，不是框架新增加的配置项：

| 缓存 | 容量 | 用途 |
| --- | --- | --- |
| 白名单缓存（whiteList） | 1024 条 | 复用检查通过的结果，不是业务手工配置的放行名单 |
| 黑名单缓存（blackList） | 256 条 | 复用命中违规规则的结果 |
| SQL 归并缓存（mergedSqlCache） | 256 条 | 复用原 SQL 到 Druid 归并形式的转换结果 |

缓存使用 Druid 的并发 LRU 实现，达到容量后淘汰旧记录；没有配置按时间过期的 TTL，也不持久化，重启或重新创建检测器后重新积累。容量与 `yulinlin.monitor.max-records` 无关，后者仅限制监控记录和聚合组。升级 Druid 时应重新核对这些默认值。

Druid 会使用自身的 SQL 参数化归并逻辑生成检测缓存键，归并失败时回退查找原 SQL；框架不自行删除空格、注释或修改 SQL。因此不同文本是否复用检测结果由 Druid 决定，不能简单理解为始终按原始字符串缓存。

原始 SQL 的 Java `String.length()` **小于 8192** 才满足 Druid 新写入上述缓存的长度条件；达到或超过 8192 的 SQL 不因此免检。是否命中已有归并结果仍由 Druid 决定。容量淘汰、模板变化、归并失败等情况可能导致重新解析；并发首次未命中也不保证只解析一次。命中缓存仍有查找、统计和结果转换开销，不代表零开销，实际性能需结合 SQL 长度、模板数量和命中率测量。

黑名单缓存命中后仍按**当前** `mode` 处理：`block` 抛出 `SqlRiskException`，`observe` 记录告警后继续执行；不会因为命中缓存而跳过风险记录。`off` 不调用检测器，也不主动清空既有缓存。`SqlRiskMonitor.clear()` / `DatabaseMonitor.clear()` 仅清理各自监控数据，不清空 Druid 缓存。

自定义 `SqlRiskDetector` Bean 替换内置实现后，框架不会自动为它提供缓存；若自定义实现持有并复用一个 `DruidSqlRiskDetector`，其内部 Druid 缓存仍有效。不要在每次 `check(sql)` 中重新创建 Druid 检测器，否则无法跨请求复用缓存。动态规则的失效策略由自定义实现自行处理，当前内置实现不提供规则热更新或额外缓存容量配置。

## 自定义检测器

业务方声明一个 `SqlRiskDetector` Bean 即替换内置实现。接口只接收 SQL 模板；实现需要线程安全，返回非空结果。`allowed=true` 时原因列表必须为空，拒绝时必须提供原因。自定义实现仍然只接入 MySQL，且遵循上面的模式配置。

```java
import com.yulinlin.monitor.DruidSqlRiskDetector;
import com.yulinlin.monitor.SqlRiskDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BusinessSqlRiskConfiguration {
    @Bean
    public SqlRiskDetector sqlRiskDetector() {
        var defaults = new DruidSqlRiskDetector();
        return sql -> {
            // 可在这里调用业务自己的 SQL 策略；不要使用简单关键字替换修改 SQL。
            return defaults.check(sql);
        };
    }
}
```

自定义实现可使用 `SqlRiskResult.allow()`、`SqlRiskResult.reject("原因")` 或 `new SqlRiskResult(false, reasons)`。业务异常处理器通过 `SqlRiskException.getReasons()` 获取原因，通过 `isInspectionFailed()` 区分策略拒绝和检查失败，再自行返回提示文案。

SQL 风险检查不能代替动态字段/表名白名单及参数绑定；未命中规则不等于 SQL 一定安全。

---

下一步：返回[监控与排障主题目录](README.md)按需选择相关专题。
