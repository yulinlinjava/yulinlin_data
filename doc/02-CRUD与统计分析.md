# ORM CRUD 自定义 SQL 统计分析与事务

本文从一个用户实体完成 CRUD，再扩展条件查询、SQL JOIN、自定义 SQL、统计模型和事务。数据库接入与 group 注册先看 [第一专题](01-接入与数据源.md)。

阅读导航：[最小实体](#最小实体和配套表) · [CRUD](#crud-完整服务) · [查询条件](#查询条件与结果组织) · [自定义 SQL](#自定义-sql-执行) · [统计分析](#统计分析) · [批量写入](#批量与多连接写入) · [事务](#事务使用) · [Repository](#repository-接口) · [边界](#使用边界与排障)

## 最小实体和配套表

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

## CRUD 完整服务

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

### 新增用法

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

### 更新用法

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

### 删除用法

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

### 全文匹配与高亮

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

### SQL JOIN

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

## 自定义 SQL 执行

SQL 文本由调用方提供，框架不会跨数据库翻译它。所有 Request 的便利执行方法仍走 RouteSession，需要初始化好的会话。

### 查询列表与单条

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

### 自定义写入

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

### 占位符与安全

```text
SQL 文本：where id=#{id}
参数 Map：{"id": 7}
SQL Node：where id=?，绑定列表：[7]
```

正则匹配 SQL 文本中的占位符，截取内部的裸键从 Map 取值；不要把参数键写成 "#{id}"。多个或重复占位符按 SQL 出现顺序绑定，Map 的迭代顺序不决定 JDBC 参数顺序。

`#{value}` 用于 PreparedStatement 绑定；`${identifier}` 是直接文本替换，仅用于业务预先校验的可信表名、列名或表达式，不用于用户数据。WHERE 必须明确限制写入目标。

当前编码缓冲区不承诺 null 参数值可正常 put：Map.of 本身也不接受 null。查询 NULL 用 IS NULL，清列可用固定的 SET column=NULL SQL；需要可空动态参数应先核对并验证对应编码路径。缺少键可能被绑定成 null，不当作可靠的参数校验。

## 统计分析

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

MySQL/PostgreSQL/H2 使用各自日期与区间解析器；SQLite 当前不支持这两个自动分组入口，需写 strftime 等专用 SQL。TEXT 日期须统一格式与时区；TEXT 数字的比较、排序和聚合不能直接当作原生数值列。自定义函数和表达式始终按目标库语法编写。

## 批量与多连接写入

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

## 事务使用

### 框架路由事务

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

### Spring 事务

已接入 org.springframework.transaction.annotation.Transactional。原生 Spring 管理器为相同 DataSource 绑定连接时，框架在原线程复用它，提交/回滚/释放归 Spring；不把这个连接发给并发工作线程。

框架切面本身不解析 propagation、isolation、rollbackFor、noRollbackFor 等属性。已有 Spring 拦截器处理自身语义，不代表框架路由实现了完整 REQUIRES_NEW、NESTED 或保存点。不要默认叠加两个事务注解，也不要将业务异步线程视为自动继承事务。

### 独立 Session

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

## Repository 接口

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

## 使用边界与排障

- 更新通常跳过 null；普通 copyProperties 或懒同步的 null 跳过不等于数据库清列。
- insertBefore/updateBefore 可能填充字段，表结构必须匹配实体实际继承字段。
- 查询缓存保存查询结果；标准写操作在事务提交成功后自动失效相关表命名空间，TTL 是最终兜底。要求事务内读己之写或绝对强一致的查询使用 `CacheMode.NONE`。
- selectOne 返回 null 时先处理“未找到”，不将它解释成解析错误或唯一性保证。
- 日期范围为空先核对列类型、格式、时区与条件，不默认归咎于数据库驱动。
- 表达式、JOIN、JSON 路径、行锁按目标库核对；没有跨库 SQL 自动翻译器。
- NoSuchMethodError 先检查编译与运行时 JAR 是否一致；源码修复不等于已经替换部署产物。

关联增强见 [第三专题](03-关联查询与代理.md)，工具与复制语义见 [第四专题](04-工具类.md)，源码扩展及验收记录见 [第五专题](05-扩展开发与维护.md)。
