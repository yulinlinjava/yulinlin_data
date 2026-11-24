package com.yulinlin.mcp.transport;

import com.yulinlin.mcp.McpProperties;
import java.util.*;

/**
 * initialize 返回的 instructions：客户端会把它放进模型上下文，是 AI 连接后最先看到的说明。
 * 配置了 {@code yulinlin.mcp.instructions} 时原样返回；否则按当前令牌可见的工具组生成，只提到实际可用的工具。
 */
public final class McpInstructions {
    private McpInstructions() { }

    public static String of(McpProperties properties, Collection<String> visibleTools) {
        String configured = properties.getInstructions();
        if (configured != null && !configured.isBlank()) return configured.strip();
        return generate(properties.getApplicationId(), visibleTools);
    }

    static String generate(String applicationId, Collection<String> visibleTools) {
        var tools = new HashSet<>(visibleTools);
        var lines = new ArrayList<String>();
        lines.add("这是应用 " + applicationId + " 的 MCP 服务，当前令牌可用 " + tools.size()
                + " 个工具；每个工具的用途、参数和返回值以 tools/list 中的说明为准，不要猜测参数。"
                + "数据范围和权限由服务端控制，被拒绝时不要换方式绕过。");
        if (tools.stream().anyMatch(t -> t.startsWith("data."))) {
            var data = new StringBuilder("数据工具：先调用 data.sources 查看可用数据源、类型和数据库方言，再用 data.schema 确认表 / 索引和字段含义；"
                    + "data.query 结果 truncated=true 时改用聚合或加条件，不要基于截断的数据下结论。");
            if (tools.contains("data.analyze")) data.append("数据量大或需要跨表、跨数据源计算时，用 data.analyze 在服务端完成。");
            if (tools.contains("data.execute"))
                data.append("修改数据时先 dryRun=true 预演，把影响行数告诉用户并确认，再 dryRun=false 并带上 expectAffectedRows 正式执行。");
            lines.add(data.toString());
        }
        if (tools.contains("command_execute"))
            lines.add("命令工具：command_execute 立即返回后台任务，用 task_get / task_output 跟进；cwd 使用服务器上的绝对路径；"
                    + "重试变更类命令时带同一个 requestId，避免重复执行。");
        if (tools.contains("process_stop"))
            lines.add("进程工具：process_stop 必须原样使用 process_list 返回的 stopIdentity，并传 confirm=true。");
        lines.add("写操作失败或结果不确定时，先查询确认当前状态，不要自动重试。");
        return String.join("\n", lines);
    }
}
