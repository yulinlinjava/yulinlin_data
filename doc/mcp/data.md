# MCP 数据工具：查询、写入与服务端分析

[文档首页](../README.md) / [MCP](README.md)

让外部 AI 读取项目里的业务数据（JDBC 数据库、Elasticsearch），在事务里修改 JDBC 数据，或者在服务端沙箱里跑 JavaScript 做复杂分析。默认关闭，配置了 `yulinlin.mcp.data.read` 或 `write` 才会注册工具。

所有操作都走框架自己的 `RouteSession` 和原生命令 `CommandNode`，与业务代码使用同一套数据源、路由、过滤器、执行日志和编解码，不会绕开框架另开连接。

## 能做什么

| 工具 | 所在模块 | 用途 |
| --- | --- | --- |
| `data.sources` | `mcp` | 列出可以访问的数据源：组名、类型（jdbc / elasticsearch）、是否可写、数据库产品 |
| `data.schema` | `mcp` | 查看表 / 索引，以及字段、类型、注释和主键 |
| `data.query` | `mcp` | 执行一条只读查询：SQL，或 ES 的 JSON 命令 |
| `data.execute` | `mcp` | 在一个 JDBC 事务里执行多条语句（写入和查询），支持预演。配置了 `write` 才注册 |
| `data.analyze` | `mcp-script`（可选） | 服务端 JavaScript 沙箱：查询（可跨数据源）→ 加工 →（可选）写回 JDBC，只把结论返回给 AI |

只有固定的几种查询时，不需要开放这些工具，直接写 `@McpTool` 方法更安全，见[业务工具](tools.md)。

### AI 的调用顺序

工具注册时还不知道项目有哪些数据源，所以 `group` 参数不是枚举，AI 必须先查：

1. `data.sources`：拿到组名、类型（jdbc / elasticsearch）、是否可写、数据库产品（决定 SQL 方言）。
2. `data.schema`：确认表 / 索引名和字段含义，不要猜。
3. `data.query`：小结果直接查；`truncated: true` 时改用聚合或加条件。
4. 数据量大、要跨表跨库计算：`data.analyze`（需要 mcp-script）。
5. 改数据：`data.execute` 先 `dryRun: true` 预演 → 把影响行数告诉用户并确认 → `dryRun: false` 并带上 `expectAffectedRows` 正式执行。

工具自身的 description 里已经写了这些规则，上面的顺序也是 AI 实际看到的约束。

## 接入

1. 准备数据源。**强烈建议给 AI 读的数据源使用只读账号**，这是唯一可靠的数据保护手段，模块的语句检查只是兜底。数据源配置见[多数据源](../datasources/routing.md)。
2. 配置：

```yaml
yulinlin:
  mcp:
    enabled: true
    application-id: my-service
    data:
      read: ["*"]          # 可读的数据源组；"*" 表示项目里全部 JDBC / ES 数据源
      write: [biz]         # 可选，可写的组（仅 JDBC），开放 data.execute；写组自动可读
      max-rows: 1000       # 可选，单次查询返回给 AI 的最大行数，默认 1000，最大 100000
      timeout-seconds: 30  # 可选，单次调用总时长，默认 30，最大 3600
      script-row-budget: 200000  # 可选，data.analyze 脚本内所有查询合计最多读取的行数，默认 20 万
```

只开放部分数据源时写组名：`read: [report, search-es]`。

3. 需要服务端分析时，再引入可选模块（引入即启用 `data.analyze`）：

```xml
<dependency>
    <groupId>com.yulinlin</groupId>
    <artifactId>mcp-script</artifactId>
    <version>3.0</version>
</dependency>
```

4. 在 `McpAccessPolicy.canInvoke` 中给对应账号开放这些工具名，并签发带相应范围的令牌，例如：

| 使用者 | 令牌范围 |
| --- | --- |
| 数据分析助手 | `["data.sources", "data.schema", "data.query", "data.analyze"]`，或 `["@readonly"]`（没有 `write` 时 `data.analyze` 也是只读工具） |
| 运维 / 开发 | `["data.*"]` |

启动校验：

- `read`、`write` 中写明的组必须存在，类型必须是 JDBC（MySQL、PostgreSQL、H2、SQLite 等）或 Elasticsearch，否则启动失败。`"*"` 会跳过不支持的数据源类型。
- `write` 只能写 JDBC 组，不支持 `*`。
- 没有配置 `read` 和 `write` 时，不注册任何数据工具。

## 安全机制

| 层 | JDBC 读 | ES 读 | JDBC 写 |
| --- | --- | --- | --- |
| 数据库账号 | 只读账号（需自行配置） | 只读账号 / 角色 | 有写权限的账号 |
| 执行方式 | 框架事务 `RouteSession.transaction(…, 超时)`，执行完一律回滚 | 原生只读命令，不参与事务 | 框架事务，按规则提交或回滚 |
| 语句检查 | 单条；只允许 SELECT / WITH / EXPLAIN / SHOW / VALUES；不能加锁、不能 SELECT INTO | 只能用 search / aggregate / count / mapping | 只允许 INSERT / UPDATE / DELETE / MERGE 和查询；UPDATE、DELETE 必须带 WHERE |
| 一律拒绝 | DDL、事务控制、SET、USE、多条语句、`${}` 文本替换、字符串或注释里的 `#{`；读写服务器文件、跨库连接、`sleep` 类函数 | 不能用 `*` / `_all` 查询全部索引 | 同 JDBC 读 |
| 超时 | 事务超时：每条语句按剩余时间设置 JDBC 查询超时 | 往命令里加 `timeout`（服务端超时） | 事务超时 |
| 行数 | `CommandNode.maxRows`：驱动层最多读 `max-rows + 1` 行，超出截断并标记 | 改写 `size`，超出截断并标记 | — |
| 审计 | 记录数据源组和命令开头（参数值不记录） | 同左 | 同左，另记录 dryRun、expectAffectedRows、requestId；提交后写一条 INFO 日志 |

另外：

- 所有值都通过 `#{名称}` 占位传入，不拼接进命令。SQL 的参数由框架参数化执行；ES 的参数在 JSON 节点上替换，参数里的引号、括号不会改变命令结构。
- 业务注册的查询 / 写入过滤器同样作用于 AI 的请求。
- 写事务进行中读取其他 JDBC 组时，该组登记到同一个框架事务（[多数据源事务](../relations/transactions.md#多数据源)）；ES 没有事务，直接查询。
- 写入提交后，会清空 ORM 查询缓存（因为无法推断改了哪些表）。
- 同时执行的数据工具调用最多 8 个，超出返回 429。

语句检查依据文本规则，挡不住所有情况。生产环境请务必使用只读账号，并在数据库上配置语句超时（PostgreSQL 的 `statement_timeout`、MySQL 的 `max_execution_time`）。

## data.sources

```json
{"name": "data.sources", "arguments": {}}
```

```json
{"data": {"sources": [
  {"group": "biz", "type": "jdbc", "writable": true, "database": "MySQL 8.0.36"},
  {"group": "search", "type": "elasticsearch", "writable": false}],
  "maxRows": 1000, "timeoutSeconds": 30}}
```

`database` 决定 SQL 方言，AI 据此写 MySQL 或 PostgreSQL 语法。

## data.schema

```json
{"name": "data.schema", "arguments": {"group": "biz"}}
{"name": "data.schema", "arguments": {"group": "biz", "table": "order"}}
```

| 类型 | 不传 `table` | 传 `table`（名称包含该文字，不区分大小写，最多 20 个） |
| --- | --- | --- |
| jdbc | 全部表和视图（名称、类型、注释），最多 1000 张 | 列名、类型、长度、是否可空、注释和主键 |
| elasticsearch | 全部索引和字段数 | 映射中的字段路径和类型（含 `.keyword` 等多字段） |

**表和列的注释会原样交给 AI**，注释写得越清楚（例如状态值各代表什么），AI 写出的查询越准确。

## data.query

`command` 按数据源类型书写：

```json
{"name": "data.query", "arguments": {
  "group": "biz",
  "command": "select channel, count(*) orders, sum(amount) amount from orders where created_at >= #{from} group by channel",
  "params": {"from": "2026-11-01"},
  "maxRows": 100
}}
```

```json
{"name": "data.query", "arguments": {
  "group": "search",
  "command": {"search": "orders", "query": {"term": {"status": "#{status}"}}, "_source": ["id", "amount"], "size": 20},
  "params": {"status": "paid"}
}}
```

返回：

```json
{"data": {"group": "biz", "type": "jdbc", "columns": ["channel", "orders", "amount"],
          "rows": [{"channel": "app", "orders": 812, "amount": 40213.5}],
          "rowCount": 1, "truncated": false, "elapsedMs": 23}}
```

ES 命令的完整写法见 [Elasticsearch 原生查询命令](../datasources/elasticsearch.md#原生查询命令)。`command` 可以是 JSON 对象，也可以是 JSON 字符串。

## 参数与值的类型

| 方向 | 规则 |
| --- | --- |
| SQL 参数 | `#{名称}` 占位，值可以是字符串、数字、布尔、`null`，或由它们组成的数组。数组用于 `IN (#{ids})`，展开成多个参数，最多 1000 个元素，不能是空数组。字符串、注释里不能出现 `#{`；不支持 `${}`。params 中有没用到的键时报错 |
| ES 参数 | 值恰好是 `"#{名称}"` 的字符串被替换成参数值，参数还可以是对象。`size` 必须直接写数字 |
| 日期参数 | 以字符串传入。PostgreSQL 等类型严格的数据库需要显式转换，例如 `cast(#{from} as timestamp)` |
| 返回的整数和小数 | 数字（JDBC 按数据库原始类型读取）。超出 JS 安全精度（2^53）的整数、超过 15 位有效数字的小数返回字符串 |
| 返回的日期时间 | JDBC 为 `yyyy-MM-dd HH:mm:ss[.fff]` 字符串，带时区的类型为 ISO 8601 |
| NULL | JDBC 的 NULL 列保留，值为 `null` |
| 嵌套结构 | ES 文档保留嵌套对象和数组 |
| 返回的文本 | 超过 2000 字符截断，并注明原长度（沙箱脚本内部为 65536 字符） |
| 返回的二进制 | 只返回 `[二进制 N 字节]` |
| 列名重复 | 后出现的列覆盖前面的，请在 SQL 里用别名区分 |

## data.execute

```json
{"name": "data.execute", "arguments": {
  "group": "biz",
  "statements": [
    {"sql": "update orders set status = 'closed' where id in (#{ids}) and status = 'pending'", "params": {"ids": ["a1", "a2"]}},
    {"sql": "insert into order_log(order_id, action) values (#{id}, 'close')", "params": {"id": "a1"}},
    {"sql": "select id, status from orders where id in (#{ids})", "params": {"ids": ["a1", "a2"]}}
  ],
  "dryRun": true
}}
```

- 所有语句在同一个框架事务里按顺序执行（最多 100 条），任何一条失败都整体回滚，错误信息会指出是第几条。
- 查询语句能看到本事务前面已经执行的写入；写事务里允许 `SELECT ... FOR UPDATE`。
- 一次调用只能写一个数据源组，只支持 JDBC。

返回：

```json
{"data": {"group": "biz", "dryRun": true, "committed": false, "totalAffectedRows": 3, "queries": 1, "elapsedMs": 15,
          "results": [
            {"index": 0, "type": "write", "affectedRows": 2},
            {"index": 1, "type": "write", "affectedRows": 1},
            {"index": 2, "type": "query", "columns": ["id", "status"], "rows": [], "truncated": false}]}}
```

### 预演、预期行数与幂等

| 参数 | 规则 |
| --- | --- |
| `dryRun: true` | 完整执行一遍，返回真实的影响行数和查询结果，然后回滚 |
| `dryRun: false` | 正式执行，**必须**同时传 `expectAffectedRows` |
| `expectAffectedRows: N` | 写语句的影响行数合计必须等于 N，不一致就整体回滚并报错“预期 N 行，实际 M 行” |
| `expectAffectedRows: -1` | 调用方声明无法预估。不比对行数直接提交，返回结果带 `warning`，审计中可以筛出 |
| `requestId` | 可选。同一令牌内同一编号只执行一次，重复提交会直接返回首次结果并带上 `replayed: true`。同一编号对应不同语句会返回 409。执行失败会释放编号，可以重试。只对正式执行生效，保留 `retention-days` 天 |

工具说明中已经要求 AI 按以下流程操作：先预演，把影响行数告诉用户并确认，再带上预期行数正式执行。

## data.analyze（mcp-script）

AI 写一段 JavaScript，在服务端沙箱中执行，只把 `return` 的结果返回。适合数据量大、需要跨表或跨数据源关联、分组排名、去重或精确计算，以及“查出来、算一下、再写回去”的场景。

```json
{"name": "data.analyze", "arguments": {
  "script": "...",
  "writeGroup": "biz",
  "dryRun": true,
  "expectAffectedRows": 312,
  "requestId": "level-2026-11"
}}
```

| 参数 | 说明 |
| --- | --- |
| `script` | 脚本正文，最长 64 KB，最后用 `return` 返回结果 |
| `writeGroup` | 不传就是纯分析，不能调用 `execute`。传了以后，整个脚本在这个 JDBC 组的一个事务里执行，并且**调用方还需要 `data.execute` 权限**（令牌范围和 `canInvoke` 都要满足）。只在配置了 `write` 时出现 |
| `dryRun`、`expectAffectedRows`、`requestId` | 与 data.execute 相同。传了 `writeGroup` 时必须明确给出 `dryRun` |

### 脚本 API

| 函数 | 返回 | 说明 |
| --- | --- | --- |
| `query(group, command, params?)` | `[{字段: 值}]` | `command` 是 SQL，或 ES 命令对象。写入时读 JDBC 数据源会登记到同一个框架事务，能看到本次的写入；ES 没有事务，直接执行 |
| `queryOne(group, command, params?)` | 一行或 `null` | 多于一行时报错 |
| `execute(group, sql, params?)` | 影响行数 | 只能写 `writeGroup` |
| `log(...args)` | — | 调试信息，随结果返回，最多 100 行，每行最多 2000 字符 |
| `throw new Error('原因')` | — | 主动放弃，整体回滚，原因返回给 AI |

脚本是标准 JavaScript（ES2023），值的类型与上文相同。

```js
// 按近 90 天消费额重新评定会员等级，只改变化的会员，并记录日志
const rows = query('biz', `
  select u.id, u.level, coalesce(sum(o.amount), 0) as spent
  from users u left join orders o on o.user_id = u.id and o.status = 'paid' and o.created_at >= #{since}
  group by u.id, u.level`, {since: '2026-08-26'});

// 跨数据源：从 ES 取近 90 天有投诉的用户，不参与升级
const complaints = query('search', {aggregate: 'tickets',
  query: {range: {created_at: {gte: 'now-90d'}}}, aggs: {users: {terms: {field: 'user_id', size: 10000}}}});
const blocked = new Set(complaints[0].aggregations.users.buckets.map(b => b.key));

const levelFor = spent => spent >= 10000 ? 'gold' : spent >= 2000 ? 'silver' : 'normal';
const changes = rows.filter(r => !blocked.has(r.id) && levelFor(r.spent) !== r.level)
                    .map(r => ({id: r.id, from: r.level, to: levelFor(r.spent)}));
if (changes.length > 500) throw new Error(`变更 ${changes.length} 人，超过预期，请人工确认`);

for (const c of changes) {
  // WHERE 带上读出时的旧值，防止读出后数据被他人修改
  execute('biz', 'update users set level = #{to} where id = #{id} and level = #{from}', c);
  execute('biz', `insert into user_level_log(user_id, from_level, to_level) values (#{id}, #{from}, #{to})`, c);
}
return {checked: rows.length, changed: changes.length};
```

返回：

```json
{"data": {"group": "biz", "dryRun": true, "committed": false, "totalAffectedRows": 624, "queries": 2, "elapsedMs": 842,
          "result": {"checked": 8231, "changed": 312}, "logs": []}}
```

### 沙箱限制

| 项 | 限制 |
| --- | --- |
| 隔离 | GraalJS `SandboxPolicy.CONSTRAINED`：不能访问 Java 类、文件、网络、线程和本地代码，没有 `require` / `import`。数据以 JSON 进出沙箱，不暴露任何 Java 对象 |
| 时长 | `timeout-seconds`，到时强制终止沙箱并回滚 |
| 读取行数 | 所有查询合计最多 `script-row-budget` 行（默认 200,000），超出报错（不截断，避免基于不完整数据得出错误结论）。每次 `query` 的结果会整批放进内存（Java 行、JSON、JS 对象各一份），调大前按「行数 × 列数 × 最多 8 个并发调用」评估堆内存 |
| 结果大小 | `return` 的值序列化后最多 1 MB |
| 依赖 | `org.graalvm.polyglot:polyglot` + `org.graalvm.polyglot:js` 25.2.4（MIT / UPL 许可）。在普通 OpenJDK 上以解释模式运行，启动时不会输出解释模式警告 |

社区版 GraalJS 没有可靠的内存上限，所以内存由读取行数和结果大小间接控制。

## 常见问题

**AI 查不到某张表 / 索引**：检查只读账号的权限；`data.schema` 只列出 JDBC 连接默认 schema 下的表，ES 不列出隐藏索引。

**AI 总是用截断的数据下结论**：工具说明已经要求 AI 遇到 `truncated: true` 时改用聚合查询。数据量大的分析请引入 `mcp-script`，用 `data.analyze` 在服务端完成计算。

**`data.analyze` 是只读工具吗**：配置了 `write` 时，`data.analyze` 被标记为非只读工具，范围为 `@readonly` 的令牌拿不到它，需要在范围里明确写上 `data.analyze`。

**写入后业务查询看到的还是旧数据**：模块已在提交后清空 ORM 查询缓存。如果业务还有自己的缓存（例如 Redis 业务缓存），需要业务自行处理。

**ES 能写吗**：不能，ES 只读。ES 一般是从数据库同步来的检索副本，改数据请改源头数据库；确有需要请通过业务工具（`@McpTool`）提供。
