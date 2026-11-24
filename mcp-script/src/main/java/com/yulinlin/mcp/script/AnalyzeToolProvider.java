package com.yulinlin.mcp.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.mcp.McpException;
import com.yulinlin.mcp.auth.McpAccessPolicy;
import com.yulinlin.mcp.data.DataToolProvider;
import com.yulinlin.mcp.data.McpDataService;
import com.yulinlin.mcp.tool.*;
import java.util.*;

/**
 * data.analyze：在服务端沙箱里执行 JavaScript，查询（JDBC / Elasticsearch）、加工（可选写入 JDBC）后只把结果返回给 AI。
 * 带 writeGroup 写入时，调用方还必须有 data.execute 的权限（令牌范围和 canInvoke 都要满足）。
 */
public class AnalyzeToolProvider implements McpToolProvider {
    public static final String NAME = "data.analyze";
    static final String EXECUTE = "data.execute";
    private final McpDataService data;
    private final ScriptSandbox sandbox;
    private final McpAccessPolicy policy;
    private final ObjectMapper json;

    public AnalyzeToolProvider(McpDataService data, ScriptSandbox sandbox, McpAccessPolicy policy, ObjectMapper json) {
        this.data = data;
        this.sandbox = sandbox;
        this.policy = policy;
        this.json = json;
    }

    @Override
    public Collection<McpToolDefinition> tools() {
        boolean writable = data.writeEnabled();
        var props = new LinkedHashMap<String, Object>();
        props.put("script", Map.of("type", "string", "minLength", 1, "maxLength", ScriptSandbox.MAX_SCRIPT_CHARS,
                "description", "JavaScript（ES2023）脚本正文，最后用 return 返回结果"));
        if (writable) {
            props.put("writeGroup", Map.of("type", "string", "minLength", 1,
                    "description", "要写入的数据源组（data.sources 中 writable=true）。不传就是纯分析，脚本里不能调用 execute；传了则整个脚本在该组的一个事务里执行，需要 data.execute 权限"));
            props.put("dryRun", Map.of("type", "boolean",
                    "description", "传了 writeGroup 时必填。true：执行完回滚，只看结果和影响行数；false：正式提交"));
            props.put("expectAffectedRows", Map.of("type", "integer", "minimum", -1,
                    "description", "dryRun=false 时必填：预演得到的 totalAffectedRows；-1 表示无法预估（需用户同意）"));
            props.put("requestId", Map.of("type", "string", "pattern", "^[A-Za-z0-9_.:-]{1,128}$",
                    "description", "可选幂等编号；正式执行时同一编号重复提交直接返回首次结果"));
        }
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("script"));
        schema.put("additionalProperties", false);
        return List.of(new McpToolDefinition(NAME, description(writable), json.valueToTree(schema), !writable, this::call));
    }

    private Object call(McpCallContext context, JsonNode args) {
        String writeGroup = text(args, "writeGroup");
        boolean dryRun = false;
        Long expect = args.hasNonNull("expectAffectedRows") ? args.get("expectAffectedRows").asLong() : null;
        String requestId = text(args, "requestId");
        if (writeGroup != null) {
            if (!context.allowsTool(EXECUTE, false) || !policy.canInvoke(context.user(), EXECUTE))
                throw new McpException(403, "写入需要 data.execute 权限；只做分析请不要传 writeGroup");
            if (!args.hasNonNull("dryRun")) throw new IllegalArgumentException("传了 writeGroup 时必须明确 dryRun：先 true 预演，再 false 正式执行");
            dryRun = args.get("dryRun").asBoolean();
        }
        String script = args.get("script").asText();
        long timeout = data.timeoutSeconds() * 1000L;
        return data.script(context, writeGroup, dryRun, expect, requestId, script, scope -> {
            var output = sandbox.run(script, scope, timeout);
            var result = new LinkedHashMap<String, Object>();
            result.put("result", output.result());
            result.put("logs", output.logs());
            return result;
        });
    }

    private static String text(JsonNode args, String name) {
        var v = args.get(name);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private String description(boolean writable) {
        String write = !writable ? "" : """

                写入（传 writeGroup，需要 data.execute 权限，只支持 JDBC）：
                - execute(group, sql, params) 返回影响行数，group 必须等于 writeGroup；整个脚本在一个事务里，任何未捕获的错误都会整体回滚。
                - 读 writeGroup 能看到本次已执行的写入；Elasticsearch 没有事务，直接查询。写事务里允许 SELECT ... FOR UPDATE。
                - 流程：先 dryRun=true 预演（执行后回滚，返回 totalAffectedRows），告诉用户并确认；再 dryRun=false 正式执行，expectAffectedRows 填预演的行数，不一致会回滚。
                - 更新时在 WHERE 中带上读出时的旧值（如 and level = #{from}），防止读出后数据被他人修改。
                - throw new Error('原因') 可以主动放弃并回滚。
                """;
        return """
                在服务端 JavaScript 沙箱里查询并加工数据，只把 return 的结果返回给你。原始数据量大、需要跨表或跨数据源关联、分组排名、去重或精确计算时使用，比把大量行拉回来自己算更准确。
                可用函数：
                - query(group, command, params) 返回行数组 [{字段: 值}]
                - queryOne(group, command, params) 返回一行或 null，多于一行报错
                - log(...args) 记录调试信息，随结果返回（最多 100 行）
                %s
                JS 里 Elasticsearch 的 command 直接写对象即可。值的类型：数字是数字，JDBC 日期时间是 'yyyy-MM-dd HH:mm:ss' 字符串，NULL 是 null，超出精度的大整数是字符串。金额精确汇总请在 SQL / 聚合里做。
                没有 require、import、网络、文件、定时器，也不能访问 Java。
                限制：总时长 %d 秒；所有查询合计最多读取 %d 行（超出报错，请先过滤或聚合）；return 的结果序列化后最多 1 MB。
                """.formatted(DataToolProvider.COMMAND_GUIDE, data.timeoutSeconds(), McpDataService.SCRIPT_ROW_BUDGET) + write + """

                返回：{result: 脚本返回值, logs, queries, elapsedMs}，写入时另有 dryRun、committed、totalAffectedRows。""";
    }
}
