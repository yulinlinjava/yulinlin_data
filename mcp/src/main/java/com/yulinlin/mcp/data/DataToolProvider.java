package com.yulinlin.mcp.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.mcp.tool.*;
import java.util.*;

/**
 * 注册 data.sources、data.schema、data.query，以及配置了 write 时的 data.execute。
 * 工具在启动时注册，数据源列表不写进 schema；AI 先调用 data.sources 查看可用的数据源和类型。
 */
public class DataToolProvider implements McpToolProvider {
    private final McpDataService data;
    private final ObjectMapper json;

    public DataToolProvider(McpDataService data, ObjectMapper json) {
        this.data = data;
        this.json = json;
    }

    static Map<String, Object> paramsSchema() {
        return Map.of("type", "object",
                "description", "参数，键为 #{名称} 中的名称。SQL 参数只能是字符串、数字、布尔、null 或由它们组成的数组（用于 IN (#{ids})）；"
                        + "ES 命令的参数还可以是对象。日期请传字符串，必要时在 SQL 中显式转换，例如 PostgreSQL 写 cast(#{from} as timestamp)。");
    }

    static final String GROUP_DESCRIPTION = "数据源组名，先用 data.sources 查看";

    /** 两类数据源的命令写法，供 data.query 和 mcp-script 的 data.analyze 共用。 */
    public static final String COMMAND_GUIDE = """
            command 按数据源类型书写（类型见 data.sources）：
            - jdbc：一条 SQL。只能是 SELECT / WITH / EXPLAIN / SHOW；值用 #{名称} 占位，不要拼接，不支持 ${}。方言以 data.sources 返回的 database 为准。
            - elasticsearch：JSON 对象，必须且只能有一个操作键，值是索引（可逗号分隔或前缀通配 orders-*，不能用 * 或 _all）：
              {"search": "orders", "query": {...}, "_source": [...], "sort": [...], "size": 20} 每条命中一行（含 _id、_index、_score）；
              {"aggregate": "orders", "aggs": {...}} 一行 {total, aggregations}；{"count": "orders", "query": {...}} 一行 {count}。
              其余内容就是 ES 请求体。参数写成值恰好为 "#{名称}" 的字符串，例如 {"term": {"status": "#{status}"}}。Elasticsearch 只读。
            """;

    @Override
    public Collection<McpToolDefinition> tools() {
        var tools = new ArrayList<McpToolDefinition>();

        tools.add(define("data.sources", """
                列出可以访问的数据源：group（组名）、type（jdbc / elasticsearch）、writable（是否可写，只有 JDBC 可写），\
                JDBC 另有 database（数据库产品和版本，决定 SQL 方言）。其他数据工具的 group 参数取自这里。""", true,
                props(), List.of(), (ctx, a) -> data.describeSources()));

        tools.add(define("data.schema", """
                查看数据结构。不传 table 时列出全部表和视图（JDBC，含注释）/ 索引（Elasticsearch）；\
                传 table 时返回名称包含该文字的对象（最多 20 个）的字段：JDBC 为列名、类型、可空、注释和主键，ES 为映射中的字段路径和类型。\
                写查询前先确认名称和字段含义，不要猜测。""", true,
                props("group", Map.of("type", "string", "minLength", 1, "description", GROUP_DESCRIPTION),
                        "table", Map.of("type", "string", "maxLength", 128, "description", "可选，表 / 索引 / 集合名的一部分，不区分大小写")),
                List.of("group"),
                (ctx, a) -> data.schema(text(a, "group"), text(a, "table"))));

        tools.add(define("data.query", """
                执行一条只读查询，返回 columns、rows（每行是 {字段: 值}）、truncated。
                %s
                最多返回 %d 行，超出时 truncated=true，此时请改用聚合（GROUP BY / aggs / $group）或加过滤条件，不要基于截断的数据下结论。\
                数据量大或需要多步计算时，如果有 data.analyze 工具，请用它在服务端完成计算。
                值：日期时间为字符串（JDBC 为 yyyy-MM-dd HH:mm:ss），超出 JS 安全精度的整数和高精度小数为字符串，二进制只返回长度，ES 保留嵌套对象和数组。"""
                        .formatted(COMMAND_GUIDE, data.maxRows()), true,
                props("group", Map.of("type", "string", "minLength", 1, "description", GROUP_DESCRIPTION),
                        "command", Map.of("type", List.of("string", "object"),
                                "description", "jdbc 为 SQL 字符串；elasticsearch 为 JSON 对象（也可以是 JSON 字符串）"),
                        "params", paramsSchema(),
                        "maxRows", Map.of("type", "integer", "minimum", 1, "maximum", data.maxRows(), "default", data.maxRows(),
                                "description", "最多返回的行数，默认也是上限 " + data.maxRows())),
                List.of("group", "command"),
                (ctx, a) -> data.query(text(a, "group"), command(a.get("command")), DataValues.params(a.get("params")),
                        a.path("maxRows").isInt() ? a.get("maxRows").asInt() : data.maxRows())));

        if (data.writeEnabled()) {
            tools.add(define("data.execute", """
                    在可写的 JDBC 数据源（data.sources 中 writable=true）上，用同一个事务按顺序执行一条或多条 SQL，任何一条失败都整体回滚。\
                    写语句返回 affectedRows，查询语句返回 rows（能看到本事务之前的写入）。
                    只允许 INSERT / UPDATE / DELETE / MERGE 和查询；UPDATE、DELETE 必须带 WHERE；DDL、事务控制一律拒绝；值用 #{名称} 占位。
                    必须遵守的流程：1）先用 dryRun=true 预演，结果里 totalAffectedRows 是实际会影响的行数，执行后会回滚；\
                    2）把影响行数告诉用户并确认；3）用 dryRun=false 正式执行，并把预演得到的行数填入 expectAffectedRows，实际行数不一致会回滚并报错。\
                    只有数据持续变化、无法预演时，才在征得用户同意后传 expectAffectedRows=-1。\
                    建议正式执行时带上 requestId，网络重试时同一编号不会重复执行。""", false,
                    props("group", Map.of("type", "string", "minLength", 1, "description", "可写的数据源组名（data.sources 中 writable=true）"),
                            "statements", Map.of("type", "array", "minItems", 1, "maxItems", 100, "description", "按顺序执行的语句",
                                    "items", Map.of("type", "object", "additionalProperties", false, "required", List.of("sql"),
                                            "properties", Map.of(
                                                    "sql", Map.of("type", "string", "minLength", 1, "maxLength", 100_000, "description", "单条语句，用 #{名称} 引用参数"),
                                                    "params", paramsSchema()))),
                            "dryRun", Map.of("type", "boolean", "description", "true：执行后回滚，只看影响行数；false：正式执行并提交"),
                            "expectAffectedRows", Map.of("type", "integer", "minimum", -1,
                                    "description", "dryRun=false 时必填：预演得到的写语句影响行数合计；-1 表示无法预估（需用户同意）"),
                            "requestId", Map.of("type", "string", "pattern", "^[A-Za-z0-9_.:-]{1,128}$",
                                    "description", "可选幂等编号；同一编号重复提交会直接返回首次结果（replayed=true），不会重复执行")),
                    List.of("group", "statements", "dryRun"),
                    (ctx, a) -> data.execute(ctx, text(a, "group"), statements(a.get("statements")), a.get("dryRun").asBoolean(),
                            a.hasNonNull("expectAffectedRows") ? a.get("expectAffectedRows").asLong() : null, text(a, "requestId"))));
        }
        return tools;
    }

    private McpToolDefinition define(String name, String description, boolean readOnly, Map<String, Object> props,
                                     List<String> required, McpToolHandler handler) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return new McpToolDefinition(name, description, json.valueToTree(schema), readOnly, handler);
    }

    private static Map<String, Object> props(Object... pairs) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    private static String text(JsonNode args, String name) {
        var v = args.get(name);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    /** 字符串原样（SQL 或 JSON 文本），对象保留为 JsonNode，由服务按数据源类型处理。 */
    private static Object command(JsonNode node) {
        if (node == null || node.isNull()) throw new IllegalArgumentException("command 不能为空");
        return node.isTextual() ? node.asText() : node;
    }

    private static List<McpDataService.Statement> statements(JsonNode node) {
        var list = new ArrayList<McpDataService.Statement>();
        for (JsonNode item : node) list.add(new McpDataService.Statement(item.get("sql").asText(), DataValues.params(item.get("params"))));
        return list;
    }
}
