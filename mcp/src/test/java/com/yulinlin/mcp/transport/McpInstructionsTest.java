package com.yulinlin.mcp.transport;

import com.yulinlin.mcp.McpProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McpInstructionsTest {
    private static McpProperties properties() {
        var p = new McpProperties();
        p.setApplicationId("demo");
        return p;
    }

    @Test
    void generatedTextIsGenericHeaderPlusModuleSectionsPlusFooter() {
        String text = McpInstructions.of(properties(), List.of("order_query", "data.sources"), List.of("数据工具：先调用 data.sources。", "  "));
        var lines = text.split("\n");
        assertEquals(3, lines.length, "空白段落被忽略");
        assertTrue(lines[0].contains("demo") && lines[0].contains("2 个工具"));
        assertEquals("数据工具：先调用 data.sources。", lines[1]);
        assertTrue(lines[2].contains("不要自动重试"));

        String bare = McpInstructions.of(properties(), List.of("order_query"), List.of());
        assertFalse(bare.contains("data."));
        assertFalse(bare.contains("command_execute"), "核心模块不提任何具体的可选工具");
    }

    @Test
    void configuredInstructionsReplaceGeneratedText() {
        var p = properties();
        p.setInstructions("  订单系统。金额单位为分。  ");
        assertEquals("订单系统。金额单位为分。", McpInstructions.of(p, List.of("data.sources"), List.of("数据工具")));
        p.setInstructions("   ");
        assertTrue(McpInstructions.of(p, List.of(), List.of()).contains("demo"), "空白配置按未配置处理");
    }
}
