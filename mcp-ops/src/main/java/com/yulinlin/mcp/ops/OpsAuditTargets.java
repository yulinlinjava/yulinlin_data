package com.yulinlin.mcp.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.yulinlin.mcp.McpProperties;
import com.yulinlin.mcp.store.McpAuditTargetProvider;
import java.util.*;

/**
 * 运维工具的审计字段。运维最需要回答“谁、在哪台机器、执行了什么”，所以 command_execute 记录命令原文和工作目录
 * （单个值最多保留 512 字符），所有运维工具都记录实例（instance-id）。命令里不要写明文密码；
 * 确实不能落审计时，关闭 yulinlin.mcp.command.audit-command，只记录命令长度。
 */
public final class OpsAuditTargets implements McpAuditTargetProvider {
    private static final Set<String> TOOLS = Set.of("command_execute", "task_list", "task_get", "task_output", "task_cancel",
            "process_list", "process_stop", "server_info");
    private final McpProperties properties;

    public OpsAuditTargets(McpProperties properties) { this.properties = properties; }

    @Override
    public Map<String, String> target(String tool, JsonNode args) {
        if (tool == null || !TOOLS.contains(tool) || args == null) return Map.of();
        var target = new LinkedHashMap<String, String>();
        target.put("instance", properties.getInstanceId());
        switch (tool) {
            case "command_execute" -> {
                String command = args.path("command").asText("");
                if (properties.getCommand().isAuditCommand()) target.put("command", command);
                else target.put("commandLength", String.valueOf(command.length()));
                put(target, "cwd", args.path("cwd"));
                put(target, "timeoutSeconds", args.path("timeoutSeconds"));
                put(target, "requestId", args.path("requestId"));
            }
            case "task_get", "task_output", "task_cancel" -> put(target, "taskId", args.path("id"));
            case "process_stop" -> {
                put(target, "pid", args.path("pid"));
                put(target, "force", args.path("force"));
            }
            default -> { }
        }
        return target;
    }

    private static void put(Map<String, String> target, String key, JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return;
        target.put(key, value.isTextual() ? value.asText() : value.toString());
    }
}
