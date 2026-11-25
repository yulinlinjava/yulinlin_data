package com.yulinlin.mcp;

import com.yulinlin.mcp.store.McpAudit;
import com.yulinlin.mcp.store.McpAuditTargetProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.yulinlin.mcp.McpTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** 多个 McpAuditTargetProvider（可选模块的和业务的）互相追加，不互相替换。 */
class McpAuditTargetsTest {
    @Test
    void providersAreMergedInOrderAndLaterOnesWinOnSameKey() throws Exception {
        McpAuditTargetProvider module = (tool, args) -> tool.startsWith("data.") ? Map.of("group", args.path("group").asText(), "kind", "module") : Map.of();
        McpAuditTargetProvider business = (tool, args) -> Map.of("tenant", "t1", "kind", "business");
        var audit = new McpAudit(new MemoryStore(), properties(), List.of(module, business));

        var target = audit.begin(principal("c1"), "data.query", JSON.readTree("{\"group\":\"biz\"}")).target();
        assertEquals("biz", target.get("group"), "业务 Provider 不会让模块的字段消失");
        assertEquals("t1", target.get("tenant"));
        assertEquals("business", target.get("kind"), "同名字段后者覆盖");

        var other = audit.begin(principal("c1"), "order_query", JSON.readTree("{}")).target();
        assertEquals(Map.of("tenant", "t1", "kind", "business"), other);
    }

    @Test
    void nullResultsAndNoProvidersAreTolerated() throws Exception {
        assertTrue(new McpAudit(new MemoryStore(), properties()).begin(principal("c1"), "x", JSON.readTree("{}")).target().isEmpty());
        var audit = new McpAudit(new MemoryStore(), properties(), List.of((tool, args) -> null));
        assertTrue(audit.begin(principal("c1"), "x", JSON.readTree("{}")).target().isEmpty());
    }
}
