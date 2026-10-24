# 自定义 SQL

[文档首页](../README.md) / [ORM 开发](README.md)

QueryRequest 和 ExecuteRequest 的参数化原始 SQL。

示例使用的基础模型见[实体映射与最小模型](entity.md)；数据库配置见[数据源](../datasources/README.md)。

## 本页导航

- [自定义 SQL 执行](#自定义-sql-执行)
- [查询列表与单条](#查询列表与单条)
- [自定义写入](#自定义写入)
- [JSON 字段与原生 SQL](#json-字段与原生-sql)
- [占位符与安全](#占位符与安全)

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

### JSON 字段与原生 SQL

JDBC ORM 的职责是字段映射、参数编解码、SQL 执行和结果映射。Map、List、对象与 JSON 的整字段编解码保留；可以用 `.field("payloadData", value)` 替换整个 JSON 字段，再通过普通实体查询读取。已有列类型、JSON 格式和自动 Schema 策略不因本次调整而改变。

**自动 JSON 路径查询及局部更新已移除，不提供兼容开关。** 这是源码及二进制不兼容变更：

- 删除 `SqlJsonUtil`、MySQL 的 `MysqlJsonUtil`、PG 的 `JsonPath` 和 JSON 更新解析器，以及各方言的 `jsonExtract` 入口。
- 删除通用 `IConditionWrapper` / `ConditionManager` 的 `nested(...)`、core `Nested` 节点、`IConditionNestedWrapper` 和原路径前缀基类 `IChildrenWrapper`。相关 Wrapper 的带 `String name` 路径前缀构造器和 `getName()` 也删除；无参构造、普通字段、AND/OR/NOT 组合仍保留。
- 原 `.eq("payload->name", value)`、`.field("payload->name", value)` 等字段名路径语法在 JDBC 解析时直接报错，包括投影、条件、排序、聚合及更新；不会忽略条件、自动降级成整字段操作或产生路径更新 SQL。值中包含 `->` 不受影响。
- 自定义方言若覆盖过 `NameParse.resolve(name, params, comparisonValue)`，应改为 `resolve(name, params)`；字段解析不再根据比较值推断 JSON 类型。使用过已删除基类的扩展应改为普通 Wrapper / 节点。
- 所有模块使用同一版本，调用方重新编译；升级时清理旧构建产物，避免已删除的类残留在 JAR。没有依赖旧 API 的普通 CRUD 调用方式不变。

JDBC 自动 JSON 翻译层与通用 nested 入口均已移除；ES 专属的 `NestedNode`、`NestedParse` 和解析器注册也已删除，不保留另一套框架 nested 入口。普通条件与 AND/OR/NOT 分组仍保留；MongoDB 原生点路径查询/更新不变。确需 ES nested 查询时，由业务直接使用 Elasticsearch 原生客户端，框架不提供统一封装，也不会把点路径条件自动改写成 nested 查询。详见 [ES 查询能力边界](../datasources/elasticsearch.md#查询能力边界)。PG 数字区间和时间精度修复保留。

数据库自身的 JSON 操作仍可通过 `QueryRequest` / `ExecuteRequest` 执行。以下是 **PG 专用**示例，表和实体沿用 [PG JSON 整字段存储](../datasources/postgresql.md#json-整字段存储)：

```java
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.core.request.ExecuteRequest;
import com.yulinlin.data.lang.json.JsonUtil;
import demo.domain.PgDocument;
import java.util.Map;

var query = QueryRequest.newInstance(
        "select id, document_data as \"payloadData\" from pg_document_demo "
        + "where document_data #>> '{profile,name}' = #{name}",
        Map.of("name", "张三"), PgDocument.class);
query.setSession("postgresql");
var rows = query.selectList();

var update = ExecuteRequest.newInstance(
        "update pg_document_demo "
        + "set document_data = jsonb_set(document_data, '{profile,name}', "
        + "CAST(#{jsonValue} AS jsonb), true) where id = #{id}",
        Map.of("jsonValue", JsonUtil.toJson("李四"), "id", "demo-1"));
update.setSession("postgresql");
int affected = update.execute();
```

这里的路径和 SQL 由开发者明确编写，业务值用 `#{...}` 绑定；不要把前端输入拼接进 SQL，也不要用 `${...}` 传业务值。局部写入的新值显式 JSON 序列化：字符串、数字、对象的 JSON 类型由调用方决定，框架不再猜测。PG 的 `jsonb_set` 不自动补中间父路径，SQL NULL 文档也不自动变为对象；这些由业务和数据库原生规则决定。其他数据库需要自己的 SQL，不进行跨库翻译。

已有可信 `expression(...)`、SQL 元数据表达式入口仍保留；原生表达式应走这些明确入口，不把带 `->` 的 SQL 塞回普通字段名参数。整字段替换不会合并未提供的键；读改写场景需考虑并发覆盖，必要时使用版本条件或原生原子更新。

### 占位符与安全

```text
SQL 文本：where id=#{id}
参数 Map：{"id": 7}
SQL Node：where id=?，绑定列表：[7]
```

正则匹配 SQL 文本中的占位符，截取内部的裸键从 Map 取值；不要把参数键写成 "#{id}"。多个或重复占位符按 SQL 出现顺序绑定，Map 的迭代顺序不决定 JDBC 参数顺序。

`#{value}` 用于 PreparedStatement 绑定；`${identifier}` 是直接文本替换，仅用于业务预先校验的可信表名、列名或表达式，不用于用户数据。WHERE 必须明确限制写入目标。

当前编码缓冲区不承诺 null 参数值可正常 put：Map.of 本身也不接受 null。查询 NULL 用 IS NULL，清列可用固定的 SET column=NULL SQL；需要可空动态参数应先核对并验证对应编码路径。缺少键可能被绑定成 null，不当作可靠的参数校验。

---

下一步：返回[ORM 开发主题目录](README.md)按需选择相关专题。
