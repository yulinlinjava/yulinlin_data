# 查询条件、分页与结果

[文档首页](../README.md) / [ORM 开发](README.md)

使用 Wrapper 与查询条件对象构建页面查询。

示例使用的基础模型见[实体映射与最小模型](entity.md)；数据库配置见[数据源](../datasources/README.md)。

## 本页导航

- [查询条件与结果组织](#查询条件与结果组织)
- [大结果集逐条消费](#大结果集逐条消费)
- [计数与分页总数](#计数与分页总数)
- [查看执行计划](#查看执行计划)
- [查询条件对象](#查询条件对象)

## 查询条件与结果组织

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
| selectEach(consumer) | H2、MySQL、PG 的普通查询逐条消费；返回 long 类型的成功消费条数，不查询总数 |
| selectPage(page, size) | 数据库分页，调用前校验页码和大小 |
| useIndex("idx_name", ...) | 仅 H2 生效，支持普通/分组查询、分页及派生 count；MySQL、PostgreSQL 等其他后端忽略合法提示、不报不支持错误，继续由数据库自动选择索引，详见 [H2 索引提示](../datasources/h2.md#单次查询索引提示) |
| explain() / explainCount() | H2、MySQL、PG 的普通 EXPLAIN：分别查看当前查询和派生 count 的原生计划，详见[查看执行计划](#查看执行计划) |
| selectByMap("id") | Java 端索引，重复键由后项覆盖 |
| selectByGroup("status") | Java 端组织结果，不是 SQL GROUP BY |

## 大结果集逐条消费

`ModelSelectWrapper`、`QueryRequest` 和 `RouteSession` 提供 `selectEach`。底层 `EntitySession` 是数据源实现协议，业务仍通过 Wrapper／RouteSession 调用。当前支持 H2、MySQL、PostgreSQL 的普通查询及原生查询 SQL；分组 Wrapper 和其他数据源明确报不支持，不退回全量 List。

```java
long consumed = ModelSelectWrapper.newInstance("mysql", DemoUser.class)
        .eq("status", 1)
        .orderByAsc("id")
        .selectEach(user -> {
            // 当前线程中逐条处理，例如计算统计值、写入导出文件。
            System.out.println(user.getUsername());
        });
```

也可使用原生 SQL；参数仍走现有绑定和编解码，不拼接值：

```java
var request = QueryRequest.newInstance(
        "select id, username from ai_demo_user where status = #{status} order by id",
        java.util.Map.of("status", 1), DemoUser.class);
request.setSession("mysql");
long consumed = request.selectEach(user -> System.out.println(user.getUsername()));
```

- 边读取、边解码为新的普通实体／DTO、边调用消费者，不先构造完整 List。同步执行，消费者返回后才处理下一行；方法在全部消费结束后返回成功条数，空结果为 0，不额外执行 COUNT。
- 条件、字段投影、排序、显式分页和 useIndex 保持原语义；没有分页时读取整个结果集。需要稳定顺序时显式排序，框架不改写为多次分页查询，也不自动重试已经交付过的行。
- 不读取或写入查询缓存。显式 `.cache()`（含 CACHE_ONLY／REFRESH）报错，改用 `.cache(false)`；没有缓存也不需要额外配置。
- 不调用关联增强，不创建懒加载／自动更新代理。仅映射查询返回的字段，关联字段保留模型默认值；对普通对象的修改不会自动写库。忽略全局／注解作用域的自动更新默认值，显式 `.autoUpdate(true)` 报错，使用 `.autoUpdate(false)`。不会修改请求选项，后续 `selectList` 仍按原配置执行。
- RouteSession 保留 `IQueryFilter.beforeQuery` 与最终数据源路由；逐条解码后执行和列表共用的 `afterRow`，再交给消费者。默认 `InitFilter` 会对每个实现 `IInitEvent` 的实体调用一次 `init()`；内置 `TableLogicFilter` 继续在 SQL 中添加逻辑删除条件。查询过滤器默认支持，依赖关联或额外 SQL 时可通过 supportsSelectEach 返回 false，读取前即拒绝执行。纯写入过滤器不参与查询；见[流式查询与过滤器](filters.md#流式查询与过滤器)。
- 消费者、`afterRow` 和 `init()` 中都不能重入当前 RouteSession 的 ORM 查询、写入或事务操作；直接重入当前 JDBC Session 也会被拒绝。主结果集尚未关闭，不能在回调里查询关联、提交事务或开启嵌套事务。需要写库时在消费结束后另行处理，或显式设计独立的分批业务流程。
- 已开启 ORM 事务时复用事务连接，不自行提交／回滚外层事务；消费者异常会原样向外传播，关闭结果集和语句并归还连接借用权，由现有事务边界决定业务回滚。已执行的消费者副作用不会自动撤销。事务外关闭本次取得的连接。
- 复用原有 SQL 日志、慢查询阈值和执行监听器；成功或失败各记录一次执行。此入口的耗时包含读取、解码和消费者处理时间，不能直接当成纯数据库耗时。

三种 JDBC 适配的资源处理：

| 数据源 | selectEach 读取方式 |
| --- | --- |
| H2 | 前向、只读 ResultSet，fetchSize 提示 1000；ORM 不保留完整结果，H2 内部是否物化由执行计划决定 |
| MySQL | Connector/J 前向、只读 ResultSet，fetchSize 使用驱动逐行读取标记，不要求修改 JDBC URL |
| PostgreSQL | 前向、只读 ResultSet，fetchSize 为 1000；事务外临时关闭自动提交，读取结束后回滚只读查询事务并恢复原自动提交状态，再归还连接；已有 ORM 事务不做这段提交／回滚 |

这些设置仅作用于 `selectEach` 的语句／连接生命周期，`selectList` 的语句设置、整结果缓存、关联和代理保持原样；对象后置增强统一使用 `afterRow`。驱动前提参见 [MySQL 流式读取](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-implementation-notes.html)、[PG 游标读取](https://jdbc.postgresql.org/documentation/query/)。

消费者保存所有对象仍会占用大量内存。数据库排序、聚合及驱动缓冲也可能占用资源，流式消费不保证总耗时更短或立即取得第一行。普通原生 SQL 的可执行内容仍由业务负责；不要传多条语句或把写入命令当成查询。

测试覆盖逐行交付顺序、共享读取与解码、空结果、日期／NULL、参数绑定、分页／索引、异常资源释放、过滤器、事务复用、关联与列表行为回归。MySQL／PG 驱动调用协议有无外部库测试，另有可选真实服务器验证：

```text
mvn -pl h2,mysql,postgresql -am test -Dtest=JdbcSelectEachTest,H2SelectEachTest,MysqlSelectEachTest,PostgresqlSelectEachTest -Dsurefire.failIfNoSpecifiedTests=false
```

真实 MySQL 测试使用 `-Dmysql.test.url=... -Dmysql.test.user=...` 和环境变量 `MYSQL_TEST_PASSWORD`；真实 PG 使用 `-Dpg.test.url=... -Dpg.test.user=...` 和 `PG_TEST_PASSWORD`。未提供 URL 时跳过对应真实服务器用例；这些 selectEach 用例只运行查询，不建表或修改数据。

### 计数与分页总数

JDBC 普通查询的 `count()` 和 `selectPage()` 总数直接使用 `COUNT(1)`，保留原来的 FROM、JOIN/ON 和 WHERE，不再额外包装一层 `SELECT 1` 子查询。例如对 DemoUser 按状态计数，SQL 结构如下（省略方言的标识符引号）：

```sql
SELECT COUNT(1) AS total FROM ai_demo_user WHERE status = ?
```

统计的是满足条件的结果行数：投影字段为 NULL 的行也计入；JOIN 产生的重复行照常计入，不自动按实体主键去重。没有匹配行返回 0。列表的投影、ORDER BY、分页和行锁不带入 count；调用 count 不修改原查询，随后仍可正常查询列表。参数仍按原顺序绑定，原有请求复制、计数缓存和 SQL 日志规则保持不变。

分组查询的 `count()` 统计通过 HAVING 的分组数，继续保留子查询、分组表达式与聚合指标。例如按状态分组并筛选人数的结构为：

```sql
SELECT COUNT(1) AS total FROM (
    SELECT status, COUNT(1) AS users
    FROM ai_demo_user
    GROUP BY status
    HAVING COUNT(1) > ?
) temp
```

分组 count 同样不带列表的排序和分页。`explainCount()` 复用同一计数解析：普通查询解释直接 COUNT，分组查询解释保留子查询的 COUNT；执行计划与实际计数 SQL 保持一致。这是 SQL 生成的局部简化，实际性能取决于数据库的执行计划。

### 查看执行计划

`ModelSelectWrapper`、`ModelGroupWrapper` 和 `QueryRequest` 提供 `explain()`、`explainCount()`，返回 `List<Map<String, Object>>`。**H2、MySQL、PostgreSQL 支持这两个入口，均只发送普通 `EXPLAIN`，不启用 `ANALYZE`。** 其他内置后端明确报不支持，不会回退执行原查询。

下面沿用 [DemoUser 模型](entity.md)；把组名改为 `h2` 或 `postgresql` 即可查看对应数据源的计划：

```java
import com.yulinlin.common.model.ModelSelectWrapper;
import demo.domain.DemoUser;
import java.util.List;
import java.util.Map;

var query = ModelSelectWrapper.newInstance("mysql", DemoUser.class)
        .eq("status", 1)
        .orderByAsc("id")
        .page(2, 20);

List<Map<String, Object>> listPlan = query.explain();
List<Map<String, Object>> countPlan = query.explainCount();
```

两个调用分别发送一条诊断命令，可以独立使用：

| 方法 | 查看哪条 SQL | 保留的查询内容 |
| --- | --- | --- |
| `explain()` | 当前列表或分组查询 | 字段映射、WHERE、JOIN、GROUP BY、HAVING、排序和已设置的分页 |
| `explainCount()` | 与 `count()` / `selectPage()` 相同的计数 SQL | 普通查询直接 COUNT，保留 FROM、JOIN/ON、WHERE；分组查询保留子查询及 GROUP BY/HAVING、统计分组数；不带原列表的排序和分页 |

`count()` 返回总数，`explainCount()` 返回计算该总数的计划。`explain()` 不自动执行分页 count；查看分页的两条 SQL 时分别调用两个入口。已经包装为 `CountWrapper` 的请求不会再叠加一层 count。诊断不会把原请求改成 EXPLAIN，请求随后仍可正常调用 `selectList()`、`count()` 等。

返回值保留数据库报告的列标签、行顺序、JDBC `getObject` 值及 NULL。H2 通常返回 `PLAN` / `plan` 文本列，PG 普通格式通常返回多行 `QUERY PLAN`；MySQL 结果受服务器默认 EXPLAIN 格式影响。框架不统一扫描量、成本或计划树，不对计划应用实体映射或日期/金额等业务解码。

执行流程复用数据源路由、事务中的节点/连接选择、查询前 `IFilterManager.before`、字段映射、root 参数编码和参数绑定。过滤器替换请求后，诊断使用替换后的条件；H2 的 `useIndex` 保留在计划 SQL 中，MySQL、PG 仍忽略它。计划是诊断数据，不经过业务结果 `IFilterManager.after`、实体代理、懒加载或自动更新，也不要求因实体自动更新而额外开启事务。

每次调用直接读取数据库计划，**绕过 ORM 查询缓存**，即使原请求设置了 `.cache()`；原请求的缓存选项保持不变。SQL 日志、慢 SQL 监控和执行监听记录实际的 `EXPLAIN ...`，操作类型为 `explain`。日志参数展示仍遵循原有配置。

这两个便利入口接受内置 SelectWrapper / GroupWrapper 及其 CountWrapper；不自动包装原生 CommandNode、写操作或自定义 Wrapper。需要解释原生 SQL 时，直接通过[原生 SQL 入口](raw-sql.md)显式编写普通 EXPLAIN 并使用参数绑定。数据库权限、缺失索引等错误正常传播。普通 EXPLAIN 提供计划，不代表实测耗时或实际扫描量；`EXPLAIN ANALYZE` 会执行被分析语句，不在本接口范围内。

### 查询条件对象

业务页面查询优先定义 `XxxPo extends PagePo`，在子类业务字段上声明 `@JoinWhere`，一次复用条件、分页与排序。完整的 `PayChannelPo`、Controller 和 `ISuperService.page(po)`示例见[Common 业务开发：页面查询对象](../practice/models.md#页面查询对象pagepo--joinwhere)。本节只解释对象条件如何转换为 WHERE，也适用于不分页的独立查询 DTO。

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

---

下一步：返回[ORM 开发主题目录](README.md)按需选择相关专题。
