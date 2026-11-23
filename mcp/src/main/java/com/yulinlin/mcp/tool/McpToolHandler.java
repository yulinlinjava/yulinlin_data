package com.yulinlin.mcp.tool;
import com.fasterxml.jackson.databind.JsonNode;
@FunctionalInterface public interface McpToolHandler { Object execute(McpCallContext context,JsonNode arguments) throws Exception; }
