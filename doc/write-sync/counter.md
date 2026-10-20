# EntityCounter 聚合插入与更新

[文档首页](../README.md) / [批量与同步](README.md)

点击量自增、模型增量和按非数字维度聚合插入。

## 本页导航

- [实体计数器](#实体计数器)
- [通过模型提交更新增量](#通过模型提交更新增量)
- [按维度聚合插入统计数据](#按维度聚合插入统计数据)

## 实体计数器

`common` 的 `EntityCounter` 面向播放量、点击量、热度等数值累加。`update(...)` 表示按主键更新已有记录，`insert(entity)` 表示按维度聚合插入新记录；`execute()` 立即执行，`submit()` 交给后台聚合。旧 `newInstance(...)` 已移除，调用方统一改为 `update(...)`。一次更新 Builder 只描述一行，但同一行可以同时修改多个字段：

```java
import com.yulinlin.common.model.EntityCounter;

EntityCounter.update("mysql", VideoEntity.class, videoId)
        .inc(VideoEntity::getViewCount, 1)
        .inc(VideoEntity::getHotValue, 5)
        .dec(VideoEntity::getRemainingQuota, 1)
        .submit();
```

多行由业务代码正常循环，不提供容易混淆的链式 `.id(...)` 批量语法：

```java
for (VideoStat stat : stats) {
    EntityCounter.update("mysql", VideoEntity.class, stat.videoId())
            .inc(VideoEntity::getViewCount, stat.views())
            .inc(VideoEntity::getHotValue, stat.hotValue())
            .submit();
}
```

不传数据源时复用 RouteSession 的默认路由规则；单组自动选择，多组使用 `yulinlin.datasource.default-group`，实体上的 `@JoinSession` 仍可参与路由：

```java
EntityCounter.update(VideoEntity.class, videoId)
        .inc(VideoEntity::getViewCount)
        .submit();
```

终结方法有两种，Builder 终结后不能再次执行或提交：

| 方法 | 行为 | 返回值 | 事务 |
| --- | --- | --- | --- |
| `execute()` | 立即生成 `column = column + ?` 原子更新 | 数据库影响行数 | 加入当前路由事务，包括适配后的 `@Transactional` |
| `submit()` | 合并进进程内计数区，定时或达到阈值后批量写入 | `void` | 必须在事务外调用，后台使用独立事务 |

聚合键是“调用线程解析出的数据源组 + 实体类型 + 主键”，字段增量保存在该行下面。默认选库和显式指定相同组时可以合并，模型更新与链式更新也共用聚合区。因此相同视频的一百次 `viewCount + 1` 最终可以变成一次 `view_count = view_count + 100`；同一行的 `viewCount`、`hotValue` 等字段也会进入一条 UPDATE。刷新时再按数据源和实体合成批量请求，底层继续使用现有 SQL 解析、JDBC batch、缓存失效和日志能力。

```yaml
yulinlin:
  data:
    counter:
      flush-interval: 1m
      flush-size: 1024
      clear-cache: false
```

`flush-size` 统计待刷新的更新行、插入维度组及待重试插入行总数，不是调用次数；达到阈值立即异步刷新。刷新采用稳定快照，新提交进入下一轮；更新分组失败时把增量合回活动区，插入分组失败则保留原批次等待重试。Spring 正常关闭会尝试最后刷新。可以注入 `EntityCounterManager` 调用 `flush()` 手动刷新或用 `pendingRows()` 查看待处理行数（不包括正在执行的首次刷新快照）。

上述按主键更新模式要求字段是可更新的数值属性，实体必须有且只有框架可识别的主键；`@JoinField(exist=false)`、`@JoinField(update=false)` 和主键字段不能作为更新计数器。`inc(field, x)` 表示加上 x，`dec(field, x)` 表示减去 x，两者都支持正负整数、小数，零不变；`dec(field, -0.5)` 等价于 `inc(field, 0.5)`。不传数量默认 1。

数量支持 `Byte/Short/Integer/Long/Float/Double`、`BigInteger/BigDecimal`、`AtomicInteger/AtomicLong/LongAdder/DoubleAdder`，不接受 null、NaN、无穷大或未支持的自定义 Number 类型。链式调用时立即读取数字快照，之后修改原子类不会改变已记录增量。内部统一使用 BigDecimal 精确聚合，不再受 long 范围限制；正负增量抵消为零时不生成该字段更新。浮点数已有的运算误差不会自动修复，精确小数建议 `new BigDecimal("0.1")`。最终写入仍受数据库列类型、范围和精度约束，不会自动修改表结构。

```java
EntityCounter.update("mysql", VideoEntity.class, videoId)
        .inc(VideoEntity::getHeat, new BigDecimal("0.75"))
        .dec(VideoEntity::getHeat, new BigDecimal("-0.25"))
        .submit(); // 本次净增 1；改用 execute() 可立即执行
```

默认 `clear-cache=false`，避免浏览量高频更新反复清理查询缓存；业务确实要立即影响缓存查询时可单次调用 `.clearCache(true)`，或修改全局默认值。

这是内存聚合，进程异常退出可能丢失最近一个刷新周期的数据，不用于余额、库存和必须审计的数据；这些场景使用 `execute()`，或接入具有持久化确认能力的 Redis/MQ。数据库最终执行仍是原子加减，多实例同时刷新不会互相覆盖，但每个实例各自持有尚未刷新的本地增量。

### 通过模型提交更新增量

只需要设置主键和本次的数字增量，不必逐个调用 `inc/dec`；原来的链式 API 仍保留。

```java
VideoEntity delta = new VideoEntity();
delta.setId(videoId);
delta.setViewCount(10L);       // 数据库原值 + 10，不是设置为 10
delta.setHotValue(5L);        // 同一行的另一个计数
delta.setRemainingQuota(-1L); // 数据库原值 - 1

EntityCounter.update("mysql", delta).submit();
// 替代选择：EntityCounter.update(delta).submit(); // 默认选库
// 替代选择：int affected = EntityCounter.update(delta).clearCache(true).execute();
```

字段规则：

| 字段 | 模型更新行为 |
| --- | --- |
| `@JoinMeta(primaryKey=true)` | 必须恰好一个且值非空，仅生成主键条件；即使是数字也不参与累加，允许 `update=false` |
| 可更新数字字段 | 正值增加、负值减少，null 和 0 忽略；支持多个字段 |
| 字符串、时间、枚举、布尔等非数字字段 | 忽略，不作为条件或覆盖赋值 |
| `@JoinField(exist=false/update=false)`、静态、transient、合成字段 | 不参与增量更新 |

支持 byte/short/int/long/float/double 及包装类、BigInteger、BigDecimal、AtomicInteger、AtomicLong、LongAdder、DoubleAdder。原子类读取当前值或 sum，不修改/清零调用对象；NaN、无穷和不支持的 Number 子类拒绝提交。数字增量在内存中用 BigDecimal 合并，避免大整数或小数被转成 long 截断；不承诺数据库浮点列的十进制精度。

注意：

- 必须使用新建的“增量模型”，不要将查回的完整实体直接传入，否则所有非零可更新数字（包括状态码、排序值、默认值）都会成为增量。此接口不是普通 `update(entity)`。
- 在 `execute/submit` 时读取快照，随后修改实体或原子类不会改变已提交数据；同一对象重复提交仍会重复累计。不要并发修改同一个模型来期待多字段整体原子快照。
- 仅生成数字加减和主键条件，不调用实体 `updateBefore`，不自动更新时间或执行版本号乐观锁逻辑。无需通过无参构造器复制实体。
- 所有增量为空/零时 `execute()` 返回 0，`submit()` 不入队；同一窗口相反增量抵消后不执行 SQL。主键不存在则更新 0 行，不会自动插入。
- SQL 更新字段必须是支持算术运算的数据库数字列。BigDecimal/BigInteger 自动建表仍沿用原存储规则；如果列为 TEXT/VARCHAR，尤其 PostgreSQL，需要业务先迁移为合适的 NUMERIC 列，本功能不自动改列。最终数值范围/精度由数据库决定；列值为 NULL 时也不自动补零。
- 模型和链式计数更新共享刷新间隔、阈值、缓存策略和失败恢复机制；`execute()` 可加入当前事务，`submit()` 必须在事务外调用。进程内聚合不是可靠消息队列。

### 按维度聚合插入统计数据

`EntityCounter.insert(entity)` 不要求数据库中已有记录。它读取提交对象：全部非空数字字段分别求和，全部非空非数字字段的 `toString()` 作为维度；时间也作为维度，不截断为分钟。`null` 忽略，`0` 仍是有效数字，`false` 是维度。`@JoinWhere` 只影响查询条件，不控制这里的分组。

```java
import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.common.model.EntityCounter;
import com.yulinlin.data.core.anno.JoinTable;
import lombok.Data;
import java.util.concurrent.atomic.DoubleAdder;

@Data
@JoinTable("sta_metrics")
public class StaMetricsEntity extends SuperEntity<StaMetricsEntity> {
    private String name;
    private String channel;
    private String deviceType;
    private String osName;
    private DoubleAdder metrics = new DoubleAdder();
}
```

业务每次构造一份新增贡献即可：

```java
StaMetricsEntity stat = new StaMetricsEntity();
stat.setName("播放量");
stat.setChannel("渠道A");
stat.setDeviceType("web");
stat.setOsName("Windows");
stat.getMetrics().add(1.5);

EntityCounter.insert("mysql", stat).submit();       // 默认一分钟聚合插入
// 不指定数据源：EntityCounter.insert(stat).submit();
// 立即插入：int affected = EntityCounter.insert(stat).execute();
// 单次覆盖缓存策略：EntityCounter.insert(stat).clearCache(true).submit();
```

三个入口示例是替代选择，不应把同一份贡献重复提交。`submit()` 返回 void，不代表已落库；活动事务中拒绝 submit，`execute()` 则立即插入并加入当前事务。Builder 只能终结一次。

分组键为“调用线程解析出的数据源组 + 实体类型 + 非数字字段名/字符串值集合”，不是简单拼接字符串。相同维度的 `metrics=1.5` 和 `metrics=2.5` 合成一条 `metrics=4.0` 的新记录；维度不同则分别插入。下一轮继续生成新记录，不查询旧记录、不做 upsert。省略数据源时复用实体 `@JoinSession`、当前注解选库作用域和默认组规则，提交时固定组名，避免后台线程丢失调用方选库上下文；路由过滤器仍在实际执行时运行。

| 数字类型 | 插入聚合规则 |
| --- | --- |
| byte/Byte、short/Short、int/Integer、long/Long | 同名字段相加，超过原类型范围时拒绝本次提交，不部分更新其他字段 |
| float/Float、double/Double | 同名字段相加，拒绝 NaN/无穷及结果溢出；不承诺十进制精度 |
| BigInteger、BigDecimal | 内存中精确相加，不转成 double；数据库列容量仍由表结构决定 |
| AtomicInteger、AtomicLong | 提交时读取数值快照，累加后仍按原字段类型还原 |
| LongAdder、DoubleAdder | 提交时读取 sum 快照，不保留或清零调用者的累加器 |

一行可以有多个数值字段，数字的正负值和零都允许。内部用不可变精确值求和，浮点字段在还原时转回原类型。`Boolean/boolean/AtomicBoolean` 是维度；AtomicReference、原子数组、LongAccumulator/DoubleAccumulator 以及其他自定义 Number 暂不支持，直接报错，不静默取 long 导致截断。声明为 Number/Object 的字段如果实际数字类型在提交间变化，也会拒绝合并；推荐声明具体数字类型。

补充约束：

- 实体须有 `@JoinTable`、可用的无参构造器和可写字段。静态、transient、合成字段与 `@JoinField(exist=false)` 不参与；`update=false` 不影响插入。
- null 不是通配符：`channel=null` 与 `channel=A` 不同组；数字字段的 null 不增加维度，也不贡献数值。
- `submit/execute` 时复制维度值并读取数字快照，之后修改原实体不影响已提交数据。复杂维度对象需支持框架 deepClone；推荐字符串、枚举、布尔和时间等简单类型，并保证 toString 稳定。同一对象重复提交会重复累计；不要并发修改它的多个字段来期待整体原子快照，Adder.sum 本身也不保证并发下的严格原子快照。
- 不预填主键、创建时间。它们为 null 时不进入分组，真正构造插入请求时由 `insertBefore` 补齐。如果业务已经赋值，非数字 ID/时间也会成为维度，数字 ID 则与其他数字一样累加；框架不特殊排除，所以统计模型不应提前设置数字主键。
- 维度以对象实际 toString 为准。例如 DateTime 默认字符串到秒，LocalDateTime 可含小数秒；不同字符串必定分组，不自动统一精度、时区或大小写。
- 刷新间隔复用上面的 `yulinlin.data.counter` 配置，默认 1m，达到 1024 个待处理组可提前刷新。它不是自然分钟窗口；需要分钟报表时，业务自行设置统一分钟字符串维度。
- 每个数据源/实体的插入分组使用 JDBC 批处理，但不主动拆多连接；仍通过标准 RouteSession、编解码器、日志和缓存失效路径执行。与按主键更新模式的聚合区隔离，不混成同一种请求。
- 失败插入保留已构造的请求和生成主键，下一轮重试，不和新窗口重新求和；不会重新生成 ID 重复插入。但不保证 exactly-once：提交结果不确定、过滤器或回调异常、缺少唯一约束等仍需业务处理。失败组不会因其他组成功而丢弃；持续失败会积压，flush-size 不是容量上限。

MySQL/PostgreSQL/H2/SQLite 自动建表已把原子数字和 Adder 映射为对应数字类型，AtomicBoolean 对应布尔存储；Byte/Short、AtomicInteger/AtomicLong/AtomicBoolean 的标量编解码也已注册。旧表如果把这些字段建成了 TEXT，不自动做破坏性改列，请按业务数据手动迁移。BigDecimal/BigInteger 延续各数据库模块原有存储规则。

---

下一步：返回[批量与同步主题目录](README.md)按需选择相关专题。
