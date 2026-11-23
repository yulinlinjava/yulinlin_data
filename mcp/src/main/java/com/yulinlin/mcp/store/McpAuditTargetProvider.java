package com.yulinlin.mcp.store;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Extract only explicitly approved, non-sensitive target identifiers for audit display. */
@FunctionalInterface
public interface McpAuditTargetProvider {
    Map<String, String> target(String tool, JsonNode arguments);
}
