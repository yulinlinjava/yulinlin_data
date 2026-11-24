package com.yulinlin.mcp;

import com.yulinlin.mcp.auth.McpScope;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class McpScopeTest {
    @Test void normalizesBareNamesWildcardsAndReservedKinds() {
        assertEquals(List.of("*"), McpScope.normalize(null));
        assertEquals(List.of("*"), McpScope.normalize(List.of("tool:*")));
        assertEquals(List.of("echo", "netdisk.*", "@readonly", "resource:file://docs/*"),
                McpScope.normalize(List.of(" echo ", "tool:echo", "tool:netdisk.*", "tool:@readonly", "resource:file://docs/*")));
        // 读回的值与传入的裸工具名一致，可以直接和 GET /api/mcp/tools 的 name 比较
        var picked = List.of("risk_calibration", "order_query");
        assertEquals(picked, McpScope.normalize(picked));
        assertEquals(McpScope.normalize(picked), McpScope.normalize(McpScope.normalize(picked)));
        for (var bad : List.<List<String>>of(List.of(), List.of("*", "echo"), List.of("bad name"), List.of("other:x"), List.of(""), Arrays.asList((String) null)))
            assertThrows(IllegalArgumentException.class, () -> McpScope.normalize(bad), bad.toString());
    }

    @Test void matchesExactPrefixAndReadOnlyOnlyForTools() {
        var scope = McpScope.normalize(List.of("echo", "netdisk.*", "@readonly", "resource:x"));
        assertTrue(McpScope.allowsTool(scope, "echo", false));
        assertTrue(McpScope.allowsTool(scope, "netdisk.list", false));
        assertTrue(McpScope.allowsTool(scope, "server_info", true));
        assertFalse(McpScope.allowsTool(scope, "command_execute", false));
        assertFalse(McpScope.allowsTool(scope, "echo2", false));
        assertFalse(McpScope.allowsTool(McpScope.normalize(List.of("resource:x")), "echo", true));
        assertTrue(McpScope.allowsTool(McpScope.normalize(null), "anything", false));
        assertEquals(List.of("echo"), McpScope.exactTools(scope));
    }
}
