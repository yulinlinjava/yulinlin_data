package com.yulinlin.mcp.data;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/**
 * 数据工具（data.*）调用审计的目标字段：数据源组、命令（或脚本）开头、预演和预期行数。参数值不记录。
 * 不依赖任何数据源模块。默认审计 {@link com.yulinlin.mcp.store.McpAudit} 总是先记录这些字段，再追加业务 McpAuditTargetProvider 的字段；
 * 自己实现整个 McpAuditService 时可以调用本方法得到同样的字段。
 */
public final class DataAuditTargets {
    private DataAuditTargets() { }

    public static Map<String, String> of(String tool, JsonNode args) {
        if (tool == null || !tool.startsWith("data.") || args == null) return Map.of();
        var target = new LinkedHashMap<String, String>();
        put(target, "group", args.path("group"));
        put(target, "writeGroup", args.path("writeGroup"));
        put(target, "dryRun", args.path("dryRun"));
        put(target, "expectAffectedRows", args.path("expectAffectedRows"));
        put(target, "requestId", args.path("requestId"));
        put(target, "table", args.path("table"));
        put(target, "command", args.path("command"));
        put(target, "script", args.path("script"));
        var statements = args.path("statements");
        if (statements.isArray()) {
            target.put("statements", String.valueOf(statements.size()));
            for (int i = 0; i < Math.min(statements.size(), 8); i++) put(target, "sql" + i, statements.get(i).path("sql"));
        }
        return target;
    }

    private static void put(Map<String, String> target, String key, JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return;
        String text = value.isTextual() ? value.asText() : value.toString();
        target.put(key, text.length() > 500 ? text.substring(0, 500) + "…" : text);
    }
}
