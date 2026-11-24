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
    void generatedTextOnlyMentionsVisibleToolGroups() {
        String text = McpInstructions.of(properties(), List.of("order_query", "data.sources", "data.schema", "data.query"));
        assertTrue(text.contains("demo"));
        assertTrue(text.contains("data.sources"));
        assertFalse(text.contains("dryRun"), "没有 data.execute 时不提写入流程");
        assertFalse(text.contains("command_execute"));

        String full = McpInstructions.of(properties(), List.of("data.sources", "data.execute", "data.analyze", "command_execute", "process_stop"));
        assertTrue(full.contains("dryRun=true"));
        assertTrue(full.contains("data.analyze"));
        assertTrue(full.contains("task_get"));
        assertTrue(full.contains("stopIdentity"));

        String plain = McpInstructions.of(properties(), List.of("order_query"));
        assertFalse(plain.contains("data."));
        assertFalse(plain.contains("task_get"));
    }

    @Test
    void configuredInstructionsReplaceGeneratedText() {
        var p = properties();
        p.setInstructions("  订单系统。金额单位为分。  ");
        assertEquals("订单系统。金额单位为分。", McpInstructions.of(p, List.of("data.sources")));
        p.setInstructions("   ");
        assertTrue(McpInstructions.of(p, List.of()).contains("demo"), "空白配置按未配置处理");
    }
}
