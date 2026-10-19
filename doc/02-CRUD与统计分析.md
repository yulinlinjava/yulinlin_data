# ORM CRUD 自定义 SQL 统计分析与事务

本文从一个用户实体完成 CRUD，再扩展条件查询、SQL JOIN、自定义 SQL、统计模型和事务。数据库接入与 group 注册先看 [第一专题](01-接入与数据源.md)。

阅读导航：[最小实体](#最小实体和配套表) · [CRUD](#crud-完整服务) · [查询条件](#查询条件与结果组织) · [自定义 SQL](#自定义-sql-执行) · [统计分析](#统计分析) · [批量写入](#批量与多连接写入) · [实体计数器](#实体计数器) · [延迟写队列](#延迟写队列) · [事务](#事务使用) · [Repository](#repository-接口) · [边界](#使用边界与排障)

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

普通对象更新、自动更新代理和显式 `.field(name, null)` 都跳过 null；它们不能把数据库列清成 NULL。需要清空字段时使用参数化的[自定义写入](#自定义写入)。`@JoinField(update = false)` 字段不参与对象更新；version 字段按当前值加入条件并递增，仍应检查影响行数判断并发冲突。

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

### 写操作与查询缓存

新增、更新和删除在数据库提交成功后，默认清理本次写入涉及的查询缓存 namespace。全局默认值如下：

```yaml
yulinlin:
  cache:
    clear-on-write: true
```

单次写操作可以覆盖全局配置；`clearCache`只控制 ORM 查询缓存，不会删除通过 `CacheClient` 保存的业务缓存：

```java
int affected = ModelUpdateWrapper.newInstance("mysql", patch)
        .clearCache(false) // 本次写入不清理查询缓存；未调用时继承全局配置
        .execute();
```

写 Wrapper 只提供 `clearCache(boolean)`，不再提供写操作版 `cache(...)`、`invalidate(...)` 或 `invalidateAll()`。一般业务保留默认 `true`；只有明确接受数据在 TTL 内短暂陈旧时才关闭。回滚或执行失败不清理缓存。`clear-on-write` 属于可选缓存 Provider；未引入 `cache-caffeine`、`cache-ehcache` 或 `cache-redis` 时不会启用该配置，写路径也不会解析失效 namespace。详细规则见[查询缓存专题](08-查询缓存.md#失效和一致性)。

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

### 查询条件对象

业务页面查询优先定义 `XxxPo extends PagePo`，在子类业务字段上声明 `@JoinWhere`，一次复用条件、分页与排序。完整的 `PayChannelPo`、Controller 和 `ISuperService.page(po)`示例见[Common 业务开发：页面查询对象](09-项目开发最佳实践.md#页面查询对象pagepo--joinwhere)。本节只解释对象条件如何转换为 WHERE，也适用于不分页的独立查询 DTO。

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

Request 可以复用事务上下文，但可变请求对象不能跨线程共享。原始查询 SQL 可以参与缓存，Key 会包含 SQL 与带类型参数。引入缓存 Provider 后，自定义写 SQL 默认继承 `yulinlin.cache.clear-on-write`；开启时因为无法可靠推导目标表，会在提交成功后清理全部 ORM 查询缓存，也可调用 `update.clearCache(false)` 关闭本次清理。未引入 Provider 时没有查询缓存，因此也不执行这项全量失效判定。

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

这些数都必须是正整数，并按数据库模块独立生效。MySQL、PostgreSQL 与 H2 默认最多 4 个连接；调用 `.batch()` 时，把整批数据均匀分成最多 4 个大组，一组一个任务/连接。同 SQL 复用 PreparedStatement，每满配置行数执行一次 executeBatch，尾批也执行。SQLite 固定单写连接，但仍可独立设置 execute-batch-size。

只有 .batch()、执行器、请求阈值和 supportsParallelWrites 等条件满足才并发；SQLite 或有效并发数为 1 时不拆组。Hikari 池会自动限制有效并发数，其他连接池应按其容量设置 parallel-connections。H2 可拆组，但多个连接仍受文件锁、索引和写入热点影响。4 是每 Session、每框架事务的上限，不是整个应用并发上限，也不保证 4 倍速度。

executeBatch 不是 commit。所有已提交任务结束后再提交/回滚及释放；失败不能让工作线程继续在已归还连接上执行。解析仍持有完整输入集合，不是流式导入。SUCCESS_NO_INFO 按成功命令计数，不保证精确行数。

多个连接有各自的本地事务，中途提交失败无法撤销已成功提交的连接；跨连接未提交数据也不保证可读。严格单库原子性和事务内读己之写使用 parallel-connections: 1。

## 实体计数器

`common` 的 `EntityCounter` 面向播放量、点击量、热度等数值累加。一次 Builder 只描述一行，但同一行可以同时修改多个字段：

```java
import com.yulinlin.common.model.EntityCounter;

EntityCounter.newInstance("mysql", VideoEntity.class, videoId)
        .inc(VideoEntity::getViewCount, 1)
        .inc(VideoEntity::getHotValue, 5)
        .dec(VideoEntity::getRemainingQuota, 1)
        .submit();
```

多行由业务代码正常循环，不提供容易混淆的链式 `.id(...)` 批量语法：

```java
for (VideoStat stat : stats) {
    EntityCounter.newInstance("mysql", VideoEntity.class, stat.videoId())
            .inc(VideoEntity::getViewCount, stat.views())
            .inc(VideoEntity::getHotValue, stat.hotValue())
            .submit();
}
```

不传数据源时复用 RouteSession 的默认路由规则；单组自动选择，多组使用 `yulinlin.datasource.default-group`，实体上的 `@JoinSession` 仍可参与路由：

```java
EntityCounter.newInstance(VideoEntity.class, videoId)
        .inc(VideoEntity::getViewCount)
        .submit();
```

终结方法有两种，Builder 终结后不能再次执行或提交：

| 方法 | 行为 | 返回值 | 事务 |
| --- | --- | --- | --- |
| `execute()` | 立即生成 `column = column + ?` 原子更新 | 数据库影响行数 | 加入当前路由事务，包括适配后的 `@Transactional` |
| `submit()` | 合并进进程内计数区，定时或达到阈值后批量写入 | `void` | 必须在事务外调用，后台使用独立事务 |

聚合键是“数据源选择器 + 实体类型 + 主键”，字段增量保存在该行下面。因此相同视频的一百次 `viewCount + 1` 最终可以变成一次 `view_count = view_count + 100`；同一行的 `viewCount`、`hotValue` 等字段也会进入一条 UPDATE。刷新时再按数据源和实体合成批量请求，底层继续使用现有 SQL 解析、JDBC batch、缓存失效和日志能力。

```yaml
yulinlin:
  data:
    counter:
      flush-interval: 1m
      flush-size: 1024
      clear-cache: false
```

`flush-size` 统计待刷新的不同实体行数，不是调用次数；达到阈值立即异步刷新。刷新采用稳定快照，新提交进入下一轮；某个数据源/实体分组失败时把该组增量合回活动区，由后续刷新继续处理。Spring 正常关闭会尝试最后刷新。可以注入 `EntityCounterManager` 调用 `flush()` 手动刷新或用 `pendingRows()` 查看待处理行数。

字段必须是可更新的数值属性，实体必须有且只有框架可识别的主键；`@JoinField(exist=false)`、`@JoinField(update=false)` 和主键字段不能作为计数器。`inc/dec` 的数量必须是 `long` 范围内的非负整数。默认 `clear-cache=false`，避免浏览量高频更新反复清理查询缓存；业务确实要立即影响缓存查询时可单次调用 `.clearCache(true)`，或修改全局默认值。

这是内存聚合，进程异常退出可能丢失最近一个刷新周期的数据，不用于余额、库存和必须审计的数据；这些场景使用 `execute()`，或接入具有持久化确认能力的 Redis/MQ。数据库最终执行仍是原子加减，多实例同时刷新不会互相覆盖，但每个实例各自持有尚未刷新的本地增量。

## 延迟写队列

适合任意插入、更新和删除的延后批量执行。它只合并请求节点，不会把相同主键的重复数值增量先求和；播放量、点击量、热度等高频计数优先使用上一节的 `EntityCounter.submit()`。三种普通写操作使用同一个入口：

```java
ModelInsertWrapper.newInstance("mysql", users).queue().execute();
ModelUpdateWrapper.newInstance("mysql", user).queue().execute();
ModelDeleteWrapper.newInstance("mysql", User.class)
        .eq(User::getId, id)
        .queue()
        .execute();
```

`execute()` 在节点成功进入内存队列后立即返回 `0`，不代表数据库影响了零行，也不返回自增主键。Wrapper 创建时已经把对象当时的字段值生成到 `INode`；入队后重新给实体字段赋值不会改变这次写入，Node 中原本保存的 List、Map、Date 等可变值引用则不要再原地修改。普通请求不调用 `.queue()`，仍按原方式立即执行。

```yaml
yulinlin:
  data:
    queue:
      flush-interval: 1m
      flush-size: 1024
      concurrency: 4
      retry-count: 3
```

`flush-size` 按当前活动队列的 Node 总数计算，不按 Wrapper 调用次数，也不按单个分组计算。总数达到阈值时会立即异步刷新当前整个队列快照，不必等待 `flush-interval`；例如多个分组各有数百个 Node，合计达到默认的 `1024` 也会立即刷新。刷新快照再按实际 `EntitySession` 实例、请求类型和 `fromClass` 统一分组；该阈值不拆分请求，也不改变事务边界。

不同 `EntitySession` 最多按 `concurrency` 并发刷新，同一 `EntitySession` 内的分组仍依次执行；单个分组调用过 `.batch()` 时，仍由该数据源的 `parallel-connections` 决定组内多连接。因此 `concurrency=4` 是同时处理的 Session 上限，不是全局物理连接上限。某组失败只重试该组，不阻塞其他 Session；写入成功后才按 `clearCache` 规则清理查询缓存。队列合并同组请求时，任一请求显式 `clearCache(true)` 就清理；全部显式为 `false` 才不清理；其余组合继续继承全局配置。

这是进程内队列，应用异常退出可能丢失尚未刷新数据，不替代消息队列。队列没有硬容量上限；如果持续生产速度高于数据库写入速度，待执行和待重试 Node 会继续占用内存，应在业务入口限流并监控积压。`.queue()` 面向零散小写入的定时聚合，不是大数据导入器；大集合直接在业务异步任务中调用 `.batch().execute()`。`.queue()` 必须在事务外调用，因为延迟任务不能加入调用线程的原事务；依赖严格先后关系、自增 ID、实时影响行数或强一致读取的写操作继续同步执行。应用正常关闭时框架会尝试最后刷新一次；数据库失败按 `retry-count` 在后续刷新中重试，超过次数后记录错误并丢弃。

队列不会为每个入队对象打印日志。开启 `com.yulinlin.data.core.queue.WriteQueueManager=DEBUG` 后，每个非空刷新快照输出一条摘要，包括触发方式、Session/分组/Node 数、影响行数、耗时和剩余积压；失败、重试和最终丢弃仍分别使用 WARN/ERROR。

## 请求与结果增强（过滤器）

实现 `IFilter` 或 `IRequestFilter` 并注册成 Spring Bean，即可自动加入过滤链。`order()` 从小到大执行，同序保留注册顺序。过滤器是共享实例，不要将当前用户、请求或分表名存入实例字段。

增强统一由 RouteSession 执行，具体 Session 和 JdbcSessionFactory 不再注入过滤器管理器：

```text
RouteSession.before → 最终 Request/数据源 → Session 解析、缓存/数据库、解码/代理 → RouteSession.after
```

`before` 在 SQL 解析和缓存 Key 构造之前执行，可以原地修改节点，也可以返回新的同类 Request，用于动态表名、租户条件或数据源选择。查询仍须返回 QueryRequest，写入仍须返回 ExecuteRequest，允许将删除转换为逻辑更新，不能返回 null。以下为配置方法片段，DemoUser 是本文示例实体，固定后缀仅演示可信的业务分表规则：

```java
@Bean
public com.yulinlin.data.core.filter.IRequestFilter userTableFilter() {
    return new com.yulinlin.data.core.filter.IRequestFilter() {
        @Override
        public QueryRequest selectBefore(String group, QueryRequest<?> request) {
            if (request.getFromClass() != DemoUser.class) return request;
            var node = request.getWrapper();
            if (node instanceof com.yulinlin.data.core.wrapper.ICountWrapper count) {
                node = count.getWrapper();
            }
            if (node instanceof com.yulinlin.data.core.wrapper.impl.AbstractSelectWrapper<?, ?, ?, ?> select) {
                select.table("demo_user_202610");
            }
            return request;
        }
    };
}
```

表名必须来自服务端可信规则，不能直接拼接客户端输入。改表名不改变 `fromClass`，它仍负责实体映射和编解码。插入、更新、删除、聚合同样修改各自的节点，批量请求须处理全部节点。替换查询节点优先使用 `request.copy(newWrapper)`，保留模型、fromClass、参数、路由、缓存和代理选项。最终请求改变 group 或 master/slave 时重新选库；后续 SQL、Key 和失效范围使用最终请求。

| 入口 | `IFilter.after` 收到/返回 | `IRequestFilter` 默认分发 |
| --- | --- | --- |
| select、group | List | 调用一次 selectAfter |
| page | Page | 对 Page.list 调用一次 selectAfter；替换列表时保留 total、extra |
| count | Integer | 保持计数不变；需要增强时覆盖普通 IFilter.after |
| insert、update、delete | Integer | 调用 updateAfter |

`after` 的新结果会真正返回给调用者；普通 IFilter 可以替换整个 Page。返回值须符合入口类型，计数和影响行数不能为 null；不要伪造乐观锁校验依赖的影响行数。分页内部 count/select 不重复增强。缓存命中同样执行 after，基础缓存不会保存后处理结果或事务代理；分页过滤当前页列表不重算数据库 total。

延迟队列入队前执行一次 before 并保存增强后节点，后台刷新不再次改表名或选库；after 只在实际执行后调用。后台写后过滤器的 RuntimeException 只记录中文日志，保留实际执行结果，不因此重试已经执行的 Node。普通写入后处理失败则向外传播，并标记路由事务回滚。

after 不代表事务已经提交，提交成功通知使用 `TransactionUtil.afterCommit`。路由作用域按实际入栈次数清理，复用 Request 或过滤器异常不会通过旧 `sessionChange` 标记误弹外层路由。

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

`RouteSession` 是业务 CRUD 和事务的唯一入口。没有外层事务时，一次写请求由 RouteSession 自动开始并结束事务；普通查询不主动开启事务，多个独立请求不自动合成一个业务事务。RouteSession 定义边界并登记参与者，`TransactionListenerManager` 统一维护当前线程的业务上下文，各 Session 只管理自己的连接资源。

同一线程内，路由方法和事务注解共用一份 `TransactionContext`，具体 Session 不创建上下文，也不反向调用事务管理器。只有路由显式开启事务才增加 `depth`，普通 CRUD 和 Session 资源开启只登记参与者，不增加深度；同一个 Session 的资源在本事务中只开启一次。内层提交只减少深度，不提交连接、不刷新代理、不执行提交后回调；内层回滚标记整个事务 rollback-only。最外层统一写回代理、提交/回滚参与者、清理状态和通知回调。不同线程互相隔离，嵌套不模拟独立提交或保存点。

### Spring 事务注解兼容

继续使用 `org.springframework.transaction.annotation.Transactional`。JDBC 模块自动注册默认优先的 `yulinlinTransactionManager`，Spring 标准注解拦截器解析注解后，由这个轻量适配器调用 RouteSession。它不创建 Spring JDBC 事务、不绑定 Spring 连接、不注册 Spring 同步回调。原有重复的 `SpringTransactionAop` 已移除，默认 Boot JDBC 事务管理器自动配置会退让。

```java
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Exception.class)
public void createOrder() throws Exception {
    // 在 Service 中通过 Repository / Wrapper 执行业务；所有数据源加入同一路由事务。
}
```

- 支持默认 `REQUIRED`：内层加入同一业务上下文，最外层才提交；内层回滚会标记整个事务 rollback-only。
- `rollbackFor`、`noRollbackFor` 及其类名规则由 Spring 标准拦截器解析。默认对 `RuntimeException` / `Error` 回滚，对受检异常默认提交；业务需要受检异常也回滚时使用上例。已经发生 SQL 失败等导致 rollback-only 的事务不能被 `noRollbackFor` 强行提交。
- 目前仅支持默认隔离级别、默认超时和 `readOnly=false`。非默认传播方式（包括 `REQUIRES_NEW` / `NESTED`）、隔离级别、超时或 `readOnly=true` 会在进入业务方法前明确报错，不静默假装支持。
- 必须从 Spring 代理外部调用；不要叠加 `@JoinTransaction` 和 `@Transactional`，也不要认为业务异步线程会继承事务。框架 `.batch()` 由内部显式传递连接池，不等于自动传播业务线程上下文。
- 数据源仍使用原来的 Spring Bean 配置，但连接由框架独立取得和管理。原生 JdbcTemplate、MyBatis、JPA 不会加入此事务；不能把混合使用当成统一提交。
- 若项目另外指定了默认事务管理器、有其他 `@Primary` 管理器，或使用 `TransactionManagementConfigurer`，请明确使用 `@Transactional(transactionManager = "yulinlinTransactionManager", rollbackFor = Exception.class)`。显式指定其他管理器不进入本适配器，框架写请求会按自己的边界独立提交。
- 不提供 Spring `TransactionSynchronizationManager` / `@TransactionalEventListener` 的同步生命周期。提交后业务使用 `TransactionUtil.afterCommit`；手动 `TransactionTemplate` 只有选择本适配器时才进入框架事务。

迁移时更新配套的 core、jdbc 及所用数据库模块，移除业务手动注册的旧 `SpringTransactionAop`。不要同时保留另一套拦截相同 `@Transactional` 方法的自定义事务切面。

### 排查事务是否提交

开启 `yulinlin.data.transaction-log: true`（默认关闭），按 SQL 日志里的同一个 `txId` 查看开启、嵌套深度、数据源参与和连接的最终处理。SQL 日志中的 `affected=1` 只表示 JDBC 执行影响一行，不代表事务已经提交；事务日志关闭时 SQL 仍保留 ID，没有框架事务的查询显示 `txId=none`。连接均由框架持有，最终查看每个连接的 `JDBC连接提交成功` 或回滚日志。`数据源事务提交方法已返回`、`框架事务上下文已清理` 都不是物理提交凭证，多库/多连接要逐一核对，不能凭一个资源成功推断全部成功。配置、字段和日志事件详见[事务诊断日志](01-接入与数据源.md#事务诊断日志)。

### 提交成功回调

业务需要在数据库提交成功后刷新权限、清理业务缓存或发送进程内通知时，使用 `com.yulinlin.data.core.transaction.TransactionUtil.afterCommit(Runnable)`：

```java
@Transactional
@JoinSync
public void changeRole(String id, List<String> permissions) {
    RoleEntity role = roleRepository.findByIdEq(id);
    role.setPermissions(permissions);
    String code = role.getCode();
    TransactionUtil.afterCommit(() -> security.refreshRole(code));
}
```

上例是方法片段，`roleRepository` 和 `security` 为业务注入对象。`@Transactional` 经 `yulinlinTransactionManager` 接入路由事务；`@JoinTransaction` 和 `SessionUtil.route().transaction(...)` 也使用同一入口。`TransactionUtil` 只向当前框架事务注册，不为其他事务管理器提供回调兜底。

| 场景 | 行为 |
| --- | --- |
| 框架业务事务成功提交 | 先清理旧代理状态并解绑上下文，处理相关缓存失效，再通知固定 `TransactionListener.afterCommit()`，最后按注册顺序同步执行业务回调 |
| 路由事务嵌套、同时访问多个数据源 | 共用当前线程的上下文，等最外层完成后每个回调执行一次 |
| 回滚、rollback-only 或提交失败 | 不触发提交成功通知，丢弃业务回调 |
| `@Transactional` 使用框架适配器 | 与路由事务完全一致，不等待其他 Spring 事务 |
| 仅有其他 Spring 管理器的事务、没有框架事务上下文 | 拒绝注册，提示先开启框架事务 |
| 仅手动开启具体 Session 的连接资源 | 不构成业务事务，不能注册回调；必须通过 RouteSession 开启 |
| 没有活动的框架事务 | 抛出 `IllegalStateException`，不静默立即执行 |
| 清理、监听器或某个回调失败 | 仍尝试后续清理与回调，最终抛出首个异常，其他异常保留为 suppressed；已提交的数据不再回滚 |

回调在当前线程执行，不是异步任务，也不是 `.queue()`。管理器先清理原事务的代理状态、解绑上下文，再发出 `afterCompletion()` 和成功通知；回调中新开的框架事务使用新上下文，不会被原事务的收尾逻辑误清理。不要在回调里修改原事务代理；普通读取显式 `.autoUpdate(false)`，多步写业务用新的框架事务边界。RBAC 案例的 `findSnapshotById` 使用普通查询快照，避免继承外层 `@JoinSync` 的代理策略。

回调开始前，框架 JDBC 连接已完成提交与释放。因此回调中再次调用 `SessionUtil.route().transaction(...)` 会建立新的框架事务；这只是旧事务结束后的新事务，不代表支持活动事务内的 `REQUIRES_NEW`。若另外包在原生 Spring 事务中，两者完全独立，本回调不会等待该原生事务，也不能保证它提交成功。

提交后回调失败不代表数据库写入失败，调用方不能盲目重试原始写入。它是进程内通知，应用在提交后异常退出可能来不及执行；需要可靠投递的外部消息仍由业务持久化任务或 Outbox 处理。

具体 Session 的 `startTransaction/commitTransaction/rollbackTransaction` 是管理器调用的资源生命周期，不再转发管理器，也不维护业务嵌套深度。业务代码不得手动调用它们，否则会绕过统一的代理写回、跨数据源协调、缓存失效和回调；JDBC 无活动资源的写操作会报错。

### 路由是唯一业务入口

以下为方法体片段，两个 Request 已构造，并明确指定各自的数据源组：

```java
insertRequest.setSession("mysql");
updateRequest.setSession("sqlite");
RouteSession route = SessionUtil.route();
route.transaction(() -> {
    route.insert(insertRequest);
    route.update(updateRequest);
    return null;
});
```

`route.isOpenTransaction()` 表示当前线程是否存在业务事务；具体 Session 的同名方法只表示自身资源是否已开启，不等于新的业务事务。业务事务必须在开启它的同一线程结束。

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

Repository 默认 Bean 名是接口简单类名首字母小写。同名接口会明确报错，不会静默覆盖；使用 `@JoinRepository("localUserRepository")`指定唯一名称。重复或重叠扫描路径会自动去重。`JoinRepository`、`JoinCache` 和 `JoinSession` 均从 `com.yulinlin.data.core.anno` 导入；Repository 模块只提供扫描与代理实现。

Repository 接口和方法都可以使用 `@JoinSession`切换数据源，方法注解优先于接口注解：

```java
@JoinRepository
@JoinSession("mysql")
public interface DemoUserRepository extends BaseRepository<DemoUser> {

    // 使用 mysql/master
    DemoUser findByUsernameEq(String username);

    // 仅此方法切换到 postgresql/master，返回后恢复 mysql
    @JoinSession("postgresql")
    List<DemoUser> findByStatusEq(Integer status);

    // 指定同组从库
    @JoinSession(value = "mysql", cluster = JoinCluster.slave)
    List<DemoUser> findByTypeEq(Integer type);
}
```

Repository 必须从 Spring 容器注入后调用，不能自行创建代理。显式 Request group 仍高于注解；最终选中的 group 和 cluster 会进入查询缓存 Key，因此不同数据源不会共用查询缓存。

### Service 与 Repository 的职责边界

推荐把一个完整的业务事务放在 Service 层：Service 方法使用 `@Transactional`（或框架 `@JoinTransaction`）定义事务边界，需要 setter 自动回写时再同时添加 `@JoinSync`。Repository 主要声明查询方法、`@JoinCache` 缓存策略，以及确实需要的 `@JoinSession` 数据源路由。

```java
@JoinRepository
public interface DemoUserRepository extends BaseRepository<DemoUser> {

    @JoinCache
    DemoUser findByIdEq(String id);
}

@Service
public class DemoUserService {
    private final DemoUserRepository repository;

    public DemoUserService(DemoUserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @JoinSync
    public void rename(String id, String name) {
        DemoUser user = repository.findByIdEq(id);
        if (user == null) throw new IllegalStateException("user not found");
        user.setName(name); // 事务提交前生成部分更新
    }
}
```

`@JoinSync` 只开启查询结果的自动更新策略，不会开启事务。它可用在 Repository 查询方法上，用于精确指定某个查询返回代理对象；但 setter 仍必须发生在外层 Service 持有的原事务内。一般业务更推荐在 Service 统一使用 `@Transactional + @JoinSync`，避免把事务和更新意图分散到多个 Repository。不要只在 Repository 查询方法上开启事务后，在方法返回之外再修改实体；此时原始事务已经结束。只读查询不需要 `@JoinSync`，显式 Wrapper 查询仍可在事务内使用 `.autoUpdate()` 覆盖单次行为。

`@JoinCache` 可以标记 Repository 接口，为其中全部抽象查询方法提供默认缓存配置；方法级注解优先，可覆盖接口配置或用 `CacheMode.NONE` 关闭单个查询。父接口上的类级和方法级配置均可继承：

```java
@JoinRepository
@JoinCache(ttl = 10, unit = TimeUnit.MINUTES)
public interface DemoUserRepository extends BaseRepository<DemoUser> {

    // 继承接口配置：READ_THROUGH，缓存 10 分钟
    DemoUser findByUsernameEq(String username);

    // 明确关闭该查询的缓存
    @JoinCache(mode = CacheMode.NONE)
    List<DemoUser> findByStatusEq(Integer status);

    // 方法配置完整覆盖接口配置：强制查库，TTL 改为 30 秒
    @JoinCache(mode = CacheMode.REFRESH, ttl = 30, unit = TimeUnit.SECONDS)
    DemoUser findByIdEq(String id);

    // 额外把角色表版本纳入 Key，角色表更新后该查询也失效
    @JoinCache(namespaces = "sys_role")
    List<DemoUser> findAll();
}
```

方法级注解是完整替换而不是逐项合并：上例 `REFRESH` 方法不会继承接口的 10 分钟 TTL。`ttl = -1`是默认值，表示使用 `yulinlin.cache.ttl`；显式 TTL 必须为正数。默认 `yulinlin.cache.ttl-in-key=false`，不同 TTL 的相同查询会复用 Key；设为 `true`才按 TTL 隔离。`CACHE_ONLY`只读已有缓存，未命中抛出 `CacheMissException`。未引入缓存 Provider 时，`READ_THROUGH`和`REFRESH`安全退化为查询数据源但不保存，`CACHE_ONLY`仍然报未命中。

接口级配置只影响查询，写方法不会继承它。写方法直接添加 `@JoinCache`、TTL 非法或 namespace 为空，会在 Repository 代理创建阶段失败；已引入缓存 Provider 时，Repository 的 insert/update/delete 按 `yulinlin.cache.clear-on-write` 决定是否在提交成功后清理解析出的表缓存。需要单次覆盖时使用写 Wrapper 或 `ExecuteRequest.clearCache(boolean)`。基础对象在关联代理处理前进入缓存，懒加载和批量预加载会按关联实体单独查询并继承入口缓存模式，因此普通懒加载关系不需要额外声明关联表 namespace；`namespaces` 主要用于无法从查询节点识别全部依赖的自定义 SQL 或非懒加载聚合。

## 使用边界与排障

- 更新通常跳过 null；普通 copyProperties 或自动更新代理的 null 跳过不等于数据库清列。
- insertBefore/updateBefore 可能填充字段，表结构必须匹配实体实际继承字段。
- 查询缓存保存查询结果；标准写操作默认在事务提交成功后清理相关表命名空间，`clearCache(false)` 会关闭单次清理，TTL 是最终兜底。要求事务内读己之写或绝对强一致的查询使用 `CacheMode.NONE`。
- selectOne 返回 null 时先处理“未找到”，不将它解释成解析错误或唯一性保证。
- 日期范围为空先核对列类型、格式、时区与条件，不默认归咎于数据库驱动。
- 表达式、JOIN、JSON 路径、行锁按目标库核对；没有跨库 SQL 自动翻译器。
- NoSuchMethodError 先检查编译与运行时 JAR 是否一致；源码修复不等于已经替换部署产物。

关联增强见 [第三专题](03-关联查询与代理.md)，工具与复制语义见 [第四专题](04-工具类.md)，源码扩展及验收记录见 [第五专题](05-扩展开发与维护.md)。
