# 统计分析

[文档首页](../README.md) / [ORM 开发](README.md)

指标、分组、日期筛选和统计结果模型。

示例使用的基础模型见[实体映射与最小模型](entity.md)；数据库配置见[数据源](../datasources/README.md)。

## 本页导航

- [统计分析](#统计分析-1)
- [注解模型](#注解模型)
- [分组求和与日期筛选](#分组求和与日期筛选)
- [指标与维度速查](#指标与维度速查)
- [聚合类型的含义与结果格式](#聚合类型的含义与结果格式)
- [每种类型的注解用法](#每种类型的注解用法)
- [每种类型的链式用法](#每种类型的链式用法)
- [数字区间的边界](#数字区间的边界)
- [H2 的 HAVING 别名处理](#h2-的-having-别名处理)
- [数据库差异与当前限制](#数据库差异与当前限制)

## 统计分析

指标接口只保留 `count`、`distinctCount`、`sum`、`avg`、`min`、`max` 及对应 `field(MetricsEnum, ...)` 入口。原 `IMetricsWrapper.object(...)` 两个重载只是返回 `null` 的占位实现，现已删除；没有实现或接入解析链的 `IConditionObjectWrapper` 同时移除。需要嵌套文档统计时使用目标后端原生能力，不将普通字段聚合当成嵌套聚合。使用过这些占位 API 的调用方需要修改代码并重新编译。

### 注解模型

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

### 分组求和与日期筛选

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

### 指标与维度速查

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

### 聚合类型的含义与结果格式

`AggregationsEnum` 决定“按什么维度分组”，`MetricsEnum` 决定“每组计算什么指标”。例如按天分组后求和，需要日期字段上的 `JoinAggregations(day)` 和指标字段上的 `JoinMetrics(sum)`，而不是二选一。

以下以 SQL 数据源 MySQL、PostgreSQL、H2 为主。日期输入示例为 `2026-10-23 21:37:56.123`；格式是分组结果的形状，不是需要传给注解的格式参数。季度存在数据库差异，不应假定各模块输出完全相同。

| 类型 | 含义 | 结果格式 / 类型 | 输入 → 分组结果示例 |
| --- | --- | --- | --- |
| `field` | 按字段原值分组，不截断日期、不划数字区间；相等规则由数据库及列的排序规则决定 | 原字段值，Java 属性类型应与字段匹配 | `login` → `login` |
| `minute` | 按完整年月日和小时中的每一分钟分组，秒及小数秒不参与区分 | 字符串 `yyyy-MM-dd HH:mm:00` | `2026-10-23 21:37:56.123` → `2026-10-23 21:37:00` |
| `hour` | 按完整年月日中的每个小时分组，不是把不同日期的同一小时合并 | 字符串 `yyyy-MM-dd HH` | `2026-10-23 21:37:56.123` → `2026-10-23 21` |
| `day` | 按年月日分组，当天的时分秒不参与区分 | 字符串 `yyyy-MM-dd` | `2026-10-23 21:37:56.123` → `2026-10-23` |
| `month` | 按年月分组，不会把不同年份的同一月份合并 | 字符串 `yyyy-MM` | `2026-10-23 21:37:56.123` → `2026-10` |
| `quarter` | 按年份和自然季度分组：1–3 月 Q1、4–6 月 Q2、7–9 月 Q3、10–12 月 Q4 | MySQL / H2 为 `yyyy-Qn`；PostgreSQL 为季度首月 `yyyy-MM` | MySQL / H2 → `2026-Q4`；PostgreSQL → `2026-10` |
| `year` | 按年份分组 | 字符串 `yyyy` | `2026-10-23 21:37:56.123` → `2026` |
| `interval` | 将数值按指定正整数步长分组，MySQL / PostgreSQL / H2 以区间上界作为分组键 | 数值，不是区间字符串；示例用 `BigDecimal` 接收 | 步长 `10` 时，`12` → `20`，表示 `[10, 20)` |

日期维度建议用 `String` 接收分组键，不要把 `2026-10`、`2026-Q4` 当成完整时间交给 `Date` / `DateTime` 解码。格式化发生在分组查询中，不改变源表保存的时间，也不修改日期编码器。

### 每种类型的注解用法

下面是复用前面 `sta_metrics` 表的只读统计结果模型。它没有预设分组维度；从后面的表格选择一项，把注解加到对应属性上。不要用这个结果模型建表、插入或更新：`bucketEnd` 和 `metrics` 都映射源列 `metrics`，分别接收区间键和组内总和。

```java
package demo.statistics;

import com.yulinlin.data.core.anno.AggregationsEnum;
import com.yulinlin.data.core.anno.JoinAggregations;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMetrics;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.MetricsEnum;
import java.math.BigDecimal;
import lombok.Data;

@Data
@JoinTable("sta_metrics")
public class AggregationRow {
    private String name;

    @JoinField(name = "crt_time")
    private String dateStr;

    @JoinField(name = "metrics")
    private BigDecimal bucketEnd;

    @JoinMetrics(MetricsEnum.sum)
    private long metrics;
}
```

每一行都是一个独立的注解示例，保留该属性原有的 `JoinField` 映射即可：

| 类型 | 在 `AggregationRow` 的哪个属性上添加 | 注解写法 |
| --- | --- | --- |
| `field` | `name` | `@JoinAggregations(AggregationsEnum.field)`，也可简写为 `@JoinAggregations` |
| `minute` | `dateStr` | `@JoinAggregations(AggregationsEnum.minute)` |
| `hour` | `dateStr` | `@JoinAggregations(AggregationsEnum.hour)` |
| `day` | `dateStr` | `@JoinAggregations(AggregationsEnum.day)` |
| `month` | `dateStr` | `@JoinAggregations(AggregationsEnum.month)` |
| `quarter` | `dateStr` | `@JoinAggregations(AggregationsEnum.quarter)`；使用前检查数据库限制 |
| `year` | `dateStr` | `@JoinAggregations(AggregationsEnum.year)` |
| `interval` | `bucketEnd` | `@JoinAggregations(value = AggregationsEnum.interval, interval = 10)` |

例如按天统计时，将 `dateStr` 改为：

```java
@JoinField(name = "crt_time")
@JoinAggregations(AggregationsEnum.day)
private String dateStr;
```

按数值区间统计时，不给 `dateStr` 加分组注解，改为在 `bucketEnd` 上声明：

```java
@JoinField(name = "metrics")
@JoinAggregations(value = AggregationsEnum.interval, interval = 10)
private BigDecimal bucketEnd;
```

配置好所选注解后，查询入口相同：

```java
import com.yulinlin.common.model.ModelGroupWrapper;
import demo.statistics.AggregationRow;

// "postgresql" 是已配置的数据源名称；请替换为自己的数据源名称。
// 这里选用支持上述八种分组的 PostgreSQL；换库前请检查下文限制。
var rows = ModelGroupWrapper.newInstance("postgresql", AggregationRow.class)
        .selectList();
```

`JoinAggregations.value` 默认是 `field`。只有选择 `interval` 时，注解的 `interval` 参数才有意义；省略步长时默认 `5`，不是 `10`。可在不同属性上声明多个维度，例如 `name` 加 `field`、`dateStr` 加 `day`，结果按“名称 + 日期”组合分组。同一属性不要同时声明 `JoinAggregations` 和 `JoinMetrics`，应像 `bucketEnd` / `metrics` 一样使用不同结果属性。

### 每种类型的链式用法

动态选择分组方式时，使用上面**未添加任何 `JoinAggregations`** 的 `AggregationRow`。链式调用会追加分组，不会覆盖模型上的注解；对同一结果别名重复添加分组可能产生重复输出，不能用它来替换已有注解。

下面每条查询分别演示一种类型，按需选择执行。`name` / `dateStr` / `bucketEnd` 是模型属性名，框架通过 `JoinField` 映射到实际列；第二个参数是结果别名，应对应接收结果的 Java 属性。不要把这八种分组同时追加到同一查询。

```java
import com.yulinlin.common.model.ModelGroupWrapper;
import demo.statistics.AggregationRow;

// field：按 name 原值分组，metrics 为每组 SUM(metrics)。
var byField = ModelGroupWrapper.newInstance("postgresql", AggregationRow.class)
        .apply(w -> w.aggregations().field("name", "name"))
        .selectList();

// minute：dateStr 示例为 2026-10-23 21:37:00。
var byMinute = ModelGroupWrapper.newInstance("postgresql", AggregationRow.class)
        .apply(w -> w.aggregations().minute("dateStr", "dateStr"))
        .selectList();

// hour：dateStr 示例为 2026-10-23 21。
var byHour = ModelGroupWrapper.newInstance("postgresql", AggregationRow.class)
        .apply(w -> w.aggregations().hour("dateStr", "dateStr"))
        .selectList();

// day：dateStr 示例为 2026-10-23。
var byDay = ModelGroupWrapper.newInstance("postgresql", AggregationRow.class)
        .apply(w -> w.aggregations().day("dateStr", "dateStr"))
        .selectList();

// month：dateStr 示例为 2026-10。
var byMonth = ModelGroupWrapper.newInstance("postgresql", AggregationRow.class)
        .apply(w -> w.aggregations().month("dateStr", "dateStr"))
        .selectList();

// quarter：PostgreSQL 的 dateStr 为 2026-10；MySQL / H2 为 2026-Q4。
var byQuarter = ModelGroupWrapper.newInstance("postgresql", AggregationRow.class)
        .apply(w -> w.aggregations().quarter("dateStr", "dateStr"))
        .selectList();

// year：dateStr 示例为 2026。
var byYear = ModelGroupWrapper.newInstance("postgresql", AggregationRow.class)
        .apply(w -> w.aggregations().year("dateStr", "dateStr"))
        .selectList();

// interval：bucketEnd 为上界，例如原始 metrics=12 落入 [10, 20)，键为 20。
// MySQL / H2 也按相同的左闭右开规则返回上界键。
var byInterval = ModelGroupWrapper.newInstance("postgresql", AggregationRow.class)
        .apply(w -> w.aggregations().interval("bucketEnd", "bucketEnd", 10))
        .orderByAsc("bucketEnd")
        .selectList();
```

`interval` 请使用专用三参数方法 `.interval(name, alias, interval)`；当前 `.field(AggregationsEnum.interval, name, alias)` 重载没有处理 `interval`，不能用它生成数字区间。其他枚举可用对应的具名方法，示例优先采用这种可读性更好的写法。

### 数字区间的边界

MySQL、PostgreSQL、H2 的区间键遵循以下数学公式。MySQL 直接使用 `FLOOR` 除法表达式；PostgreSQL / H2 使用等价的精确余数表达式，避免定点转换或除法舍入影响边界。

```text
步长 s > 0
分组键 upper = (floor(x / s) + 1) * s
对应区间 [upper - s, upper)
```

这三个 SQL 模块的分组规则即**左闭右开、键为上界**。不是四舍五入，也不是向上取整 `ceil(x / s) * s`：恰好位于边界的值会进入下一组。步长 `10` 的例子：

| 原始值 x | 所属区间 | 返回的分组键 upper |
| --- | --- | --- |
| `-20`、`-10.01` | `[-20, -10)` | `-10` |
| `-10`、`-1`、`-0.01` | `[-10, 0)` | `0` |
| `0`、`1`、`9.99` | `[0, 10)` | `10` |
| `10`、`12`、`19.99` | `[10, 20)` | `20` |
| `20` | `[20, 30)` | `30` |

例如原始 `metrics` 为 `10`、`12`、`19` 时，区间查询得到一组：`bucketEnd = 20`、`metrics = 41`。前者是区间上界，后者是 `SUM(metrics)`，不是区间内的记录数；需要记录数时另用 `JoinMetrics(count)`。

- 步长必须是正整数。注解默认 `5`；链式方法必须显式传步长。H2 / PostgreSQL 在解析阶段拒绝 `0` 和负数；MySQL 当前没有同样的前置校验，调用方也必须保证为正，不能依赖无效参数的数据库执行结果。
- 负数使用数学上的向下取整 `floor`，不是 Java 整数除法的向零截断，因此 `-1` 属于 `[-10, 0)`。
- SQL 中源值 `NULL` 不属于任何数字区间，表达式结果仍为 `NULL`，分组时可能形成单独的空值组；接收属性不要用基本类型强行表示这个空值。需要排除空值时在 `WHERE` 中处理。
- 结果只包含实际存在记录的组，不会自动补出空区间。需要连续区间时由业务补齐。
- 数据库中的小数可以参与计算，但步长 API 是 `int`，不支持 `0.5` 等小数步长。边界敏感的金额等数据建议使用 `DECIMAL` / `NUMERIC`；浮点列可能在边界附近存在精度误差。
- 返回的 JDBC 数值类型由数据库决定，不保证一定是 `Integer`；示例用 `BigDecimal` 接收。源列应使用数值类型，不要把 TEXT 字段的隐式转换当作统一支持。

### H2 的 HAVING 别名处理

H2 支持在 Wrapper 的 `having(...)` 中使用结果别名，包括原值分组、日期分组、数字区间键和聚合指标。业务仍使用属性 / 别名，不需要自行拼接数据库表达式。例如使用前面未添加 `JoinAggregations` 的 `AggregationRow`：

```java
var ranges = ModelGroupWrapper.newInstance("h2", AggregationRow.class)
        .apply(w -> w.aggregations().interval("bucketEnd", "bucketEnd", 10))
        .gte("bucketEnd", 0) // WHERE：对源列 metrics 过滤，发生在分组之前。
        .having(h -> h.gt("bucketEnd", 10).gte("metrics", 40))
        .orderByAsc("bucketEnd")
        .selectList();
```

这里 HAVING 的 `bucketEnd` 是区间上界，`metrics` 是 `SUM(metrics)`；不是直接比较原始行的两个字段。原始 `metrics` 为 `10`、`12`、`19` 时，结果为 `bucketEnd = 20`、`metrics = 41`。

H2 2.3.232 对计算型分组键直接用于 HAVING 的处理存在兼容问题，简单地把别名展开成原表达式也可能报“列必须出现在 GROUP BY 中”。H2 解析器现在按以下规则生成 SQL：

- `GROUP BY` 使用完整分组表达式，避免结果别名与原列同名时误按原列分组。
- HAVING 中的分组键别名转换为 `MIN(分组表达式)`。分组表达式在同组内取值相同，因此值和 `NULL` 语义保持不变；不会把条件提前到 WHERE。
- HAVING 中的指标别名还原为原聚合表达式，例如 `SUM(metrics)`、`COUNT(...)`，不会生成 `MIN(SUM(...))` 这类嵌套聚合。
- 排序继续使用输出别名，分页发生在 HAVING 过滤之后；分组计数也复用相同的 HAVING 规则。

以上转换只针对 H2 自动生成的别名条件，不修改实体属性或源数据，也不改变 MySQL / PostgreSQL / SQLite 的别名策略。手写 `expression(...)` 是目标数据库的原生 SQL，不会自动重写其中的别名。

[H2 HAVING 回归测试](../../h2/src/test/java/com/yulinlin/jdbc/h2/parse/group/H2HavingAliasTest.java)在 H2 内存库执行生成的 SQL，覆盖注解与链式入口、全部日期维度、数值区间、原值分组、全部指标类型、原列同名别名、多个分组维度、WHERE / HAVING 区分、嵌套条件、NULL、排序分页、分组计数、参数绑定及请求间隔离。

### 数据库差异与当前限制

本页描述当前仓库实现，不把已知缺陷描述成正常功能；注解和链式入口最终使用相同解析器，换一种写法不能绕过限制。

| 数据源 | 日期分组 | 数字区间 |
| --- | --- | --- |
| MySQL | 支持日期维度；`quarter` 返回 `2026-Q4` | 上界键，左闭右开；步长需由调用方保证大于 0 |
| PostgreSQL | 支持日期维度；`quarter` 返回季度首月，例如 `2026-10` | 上界键，左闭右开；numeric 精确余数计算，解析时校验正步长 |
| H2 | 支持日期维度；`quarter` 返回 `2026-Q4` | 上界键，左闭右开；保留十进制余数精度，解析时校验正步长 |
| SQLite | 当前自动日期分组入口抛出不支持异常，需自行编写 `strftime` 等专用 SQL | 当前自动区间分组入口不支持，需自行编写表达式 |
| MongoDB | `minute` / `hour` / `day` / `year` 的格式与上表相同；`month` / `quarter` 有下述限制 | 当前未注册 `IntervalGroup` 解析器，不能直接使用此入口 |
| Elasticsearch | 使用 `date_histogram`；分钟、日、月、年格式与上表相同；小时为 `2026-10-23 21:00:00`，季度为季度首月 `2026-10` | 已生成 `histogram` 请求，但当前结果转换器未展开普通 histogram 桶；不能据此承诺 `selectList()` 返回完整区间结果，也不能套用 SQL 的上界键示例 |

MySQL 的自然季度表达式为：

```sql
CONCAT(YEAR(crt_time), '-Q', QUARTER(crt_time))
```

同年 10 月、11 月、12 月合并为 `2026-Q4`；`2026-12-31 23:59:59.999999` 与 `2027-01-01 00:00:00` 分别进入 `2026-Q4`、`2027-Q1`，不同年份不会合并。MySQL 的 `QUARTER` 返回自然季度编号，日期为 `NULL` 时返回 `NULL`，此表达式的整体结果也为 `NULL`。函数语义见 [MySQL 日期函数文档](https://dev.mysql.com/doc/refman/8.4/en/date-and-time-functions.html#function_quarter)。

兼容性注意：旧版 MySQL 季度表达式保留了原月份，未正确合并自然季度。本次修复后的键为 `yyyy-Qn`，依赖旧错误分组键的报表、缓存或持久化统计结果应重新计算；框架不会自动重写已有统计数据。

H2 先将源值和步长都转换为 `DECFLOAT`，再按下式计算。这里的 `r` 是带源值符号的余数，不是数学上的非负模：

```text
v = CAST(column AS DECFLOAT)
r = MOD(v, CAST(step AS DECFLOAT))
upper = v - r + CASE WHEN r < 0 THEN 0 ELSE step END
```

负数且不在整边界时，去掉负余数已经得到上界；余数为 0 时（包括负整边界、零、正整边界）必须再加一步长。两侧均转为 `DECFLOAT` 是必要的，因为 H2 的 `MOD` 结果类型取决于除数类型，只转换源值仍可能丢失小数余数。这里不使用二进制 `DOUBLE` 或固定小数位截断；类型与函数说明见 [H2 数值类型](https://www.h2database.com/html/datatypes.html#decfloat_type)、[H2 MOD](https://www.h2database.com/html/functions.html#mod)。

旧版 H2 区间表达式的裸 `NUMERIC` 转换和除法会丢失精度。修复后，步长 `10` 时 `-1 → 0`、`9 → 10`、`19 → 20`、`9.99 → 10`、`-10.01 → -10`，并保留 `NULL`。若业务保存了旧计算结果，应重新统计。

PostgreSQL 采用同一余数公式，但 `v = CAST(column AS numeric)`、`r = MOD(v, step)`。旧表达式即使使用 `numeric`，除法也可能先把边界附近的商舍入：步长 `3` 时，`2.99999999999999999999` 曾错误返回 `6`，修复后返回 `3`；恰好 `3` 仍返回 `6`。高精度负数、极大整数和 NULL 同样保留原有数学规则。已有错误统计结果需要重新计算；不会自动修改持久化报表或缓存。

[PostgreSQL 集成测试](../../postgresql/src/test/java/com/yulinlin/jdbc/postgresql/PostgresqlAggregationIntegrationTest.java)在真实 PostgreSQL 18 上覆盖正负边界、高精度小数、极大整数、600 组固定种子随机数值/步长、六种日期分组、HAVING 别名与源列同名、分页排序及分组计数。运行方式见 [PostgreSQL 回归测试](../datasources/postgresql.md#运行-postgresql-回归测试)；未提供测试库 URL 时会跳过集成用例。

回归覆盖：[MySQL 季度测试](../../mysql/src/test/java/com/yulinlin/jdbc/mysql/parse/group/MysqlQuarterAggregationTest.java)覆盖全部月份、季度切换、跨年和闰日，并比较注解与链式查询；日期边界 SQL 在 H2 MySQL 兼容模式运行，不等同于真实 MySQL 服务集成验证。[H2 区间测试](../../h2/src/test/java/com/yulinlin/jdbc/h2/parse/group/H2IntervalAggregationTest.java)在 H2 内存库执行实际生成 SQL，覆盖正负整边界、小数、高精度数值、大整数、不同步长、无效步长、`NULL`、组合查询、分组键排序与指标 HAVING。

H2 的分组键 HAVING 别名兼容问题已修复，使用方式、生成规则与专门的回归测试见上面的 [H2 的 HAVING 别名处理](#h2-的-having-别名处理)。

MongoDB 当前 `month` 的格式字符串为 `%Y-%m)`，结果多一个右括号，例如 `2026-10)`；`quarter` 仅按 `%Y-%m` 格式化，未截断到季度首月，实际上仍按月份分组。不要将其误认为已经实现自然季度。

以上示例假定日期值已经处于期望的统计时区。日期分组 API 没有统一的时区参数，不负责替所有数据库统一时区；带时区类型、数据库会话时区与存储方式需按具体模块验证。TEXT 日期须统一格式与时区，H2 / PostgreSQL 的当前解析器会转为 `TIMESTAMP`；TEXT 数字的比较、排序和聚合不能直接当作原生数值列。自定义函数和表达式始终按目标库语法编写。

实现核对入口：[注解默认值](../../core/src/main/java/com/yulinlin/data/core/anno/JoinAggregations.java)、[链式接口](../../core/src/main/java/com/yulinlin/data/core/wrapper/IAggregationsWrapper.java)、[MySQL 日期](../../mysql/src/main/java/com/yulinlin/jdbc/mysql/parse/group/DateParse.java)、[MySQL 区间](../../mysql/src/main/java/com/yulinlin/jdbc/mysql/parse/group/IntervalParse.java)、[PostgreSQL 日期](../../postgresql/src/main/java/com/yulinlin/jdbc/postgresql/parse/group/DateParse.java)、[PostgreSQL 区间](../../postgresql/src/main/java/com/yulinlin/jdbc/postgresql/parse/group/IntervalParse.java)、[H2 日期](../../h2/src/main/java/com/yulinlin/jdbc/h2/parse/group/DateParse.java)、[H2 区间](../../h2/src/main/java/com/yulinlin/jdbc/h2/parse/group/IntervalParse.java)、[SQLite 支持边界](../../sqlite/src/main/java/com/yulinlin/jdbc/sqlite/SqliteParseManager.java)、[MongoDB 日期](../../mongodb/src/main/java/com/yulinlin/mongodb/parse/group/DateParse.java)、[MongoDB 注册入口](../../mongodb/src/main/java/com/yulinlin/mongodb/parse/MongoParseManager.java)、[Elasticsearch 日期](../../elasticsearch/src/main/java/com/yulinlin/elasticsearch/parse/group/DateParse.java)、[Elasticsearch 区间](../../elasticsearch/src/main/java/com/yulinlin/elasticsearch/parse/group/IntervalParse.java)、[Elasticsearch 结果转换](../../elasticsearch/src/main/java/com/yulinlin/elasticsearch/util/AggregationUtil.java)。

---

下一步：返回[ORM 开发主题目录](README.md)按需选择相关专题。
