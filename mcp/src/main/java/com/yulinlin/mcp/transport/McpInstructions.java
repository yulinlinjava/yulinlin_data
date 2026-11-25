package com.yulinlin.mcp.transport;

import com.yulinlin.mcp.McpProperties;
import java.util.*;

/**
 * initialize 返回的 instructions：客户端会把它放进模型上下文，是 AI 连接后最先看到的说明。
 * 配置了 {@code yulinlin.mcp.instructions} 时原样返回；否则由三部分拼成：
 * 通用开头、各工具 Provider 按当前令牌可见工具补充的段落（见 McpToolProvider#instructions）、通用结尾。
 * 核心模块不认识任何具体工具，只讲实际可用的东西。
 */
public final class McpInstructions {
    private McpInstructions() { }

    public static String of(McpProperties properties, Collection<String> visibleTools, List<String> sections) {
        String configured = properties.getInstructions();
        if (configured != null && !configured.isBlank()) return configured.strip();
        var lines = new ArrayList<String>();
        lines.add("这是应用 " + properties.getApplicationId() + " 的 MCP 服务，当前令牌可用 " + visibleTools.size()
                + " 个工具；每个工具的用途、参数和返回值以 tools/list 中的说明为准，不要猜测参数。"
                + "数据范围和权限由服务端控制，被拒绝时不要换方式绕过。");
        for (String section : sections) if (section != null && !section.isBlank()) lines.add(section.strip());
        lines.add("写操作失败或结果不确定时，先查询确认当前状态，不要自动重试。");
        return String.join("\n", lines);
    }
}
