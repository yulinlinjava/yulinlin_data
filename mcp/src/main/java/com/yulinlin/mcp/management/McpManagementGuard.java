package com.yulinlin.mcp.management;
import jakarta.servlet.http.HttpServletRequest;
@FunctionalInterface public interface McpManagementGuard { void checkWrite(HttpServletRequest request); }
