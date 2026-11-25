package com.yulinlin.mcp.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandGuardTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void elasticsearchSearchSizeIsCappedAndTimeoutInjected() throws Exception {
        var command = CommandGuard.elasticsearch(CommandGuard.parse(JSON, "{\"search\": \"orders\", \"size\": 5000}"), 101, 30);
        assertEquals(101, command.get("size").asInt());
        assertEquals("30s", command.get("timeout").asText());
        var absent = CommandGuard.elasticsearch(CommandGuard.parse(JSON, Map.of("search", "orders")), 101, 30);
        assertEquals(101, absent.get("size").asInt());
        var aggregate = CommandGuard.elasticsearch(CommandGuard.parse(JSON, "{\"aggregate\": \"orders\", \"aggs\": {}}"), 101, 30);
        assertEquals(0, aggregate.get("size").asInt());
        var mapping = CommandGuard.elasticsearch(CommandGuard.parse(JSON, "{\"mapping\": \"*\"}"), 101, 30);
        assertFalse(mapping.has("size"));
    }

    @Test
    void elasticsearchRejectsAmbiguousOrPlaceholderLimits() {
        assertThrows(IllegalArgumentException.class, () -> CommandGuard.elasticsearch(CommandGuard.parse(JSON, "{\"query\": {}}"), 10, 30));
        assertThrows(IllegalArgumentException.class, () -> CommandGuard.elasticsearch(CommandGuard.parse(JSON, "{\"search\": \"a\", \"count\": \"b\"}"), 10, 30));
        assertThrows(IllegalArgumentException.class, () -> CommandGuard.elasticsearch(CommandGuard.parse(JSON, "{\"search\": \"a\", \"size\": \"#{n}\"}"), 10, 30));
        assertThrows(IllegalArgumentException.class, () -> CommandGuard.parse(JSON, "[1]"));
        assertThrows(IllegalArgumentException.class, () -> CommandGuard.parse(JSON, "{bad json"));
    }
}
