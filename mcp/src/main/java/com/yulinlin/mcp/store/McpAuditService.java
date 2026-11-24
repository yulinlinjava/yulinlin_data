package com.yulinlin.mcp.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.yulinlin.mcp.auth.McpPrincipal;
import com.yulinlin.mcp.store.McpAudit.Entry;
import com.yulinlin.mcp.store.McpAudit.ManagementEntry;
import java.util.List;

/** 审计 SPI：工具调用审计 + 令牌管理审计。与命令输出、任务结果无关。 */
public interface McpAuditService {
    /** Called before side effects. Throw to prevent execution if the audit cannot be accepted. */
    Entry begin(McpPrincipal principal, String tool, JsonNode args);
    /** Update the same entry; success means tool invocation completed, not background task success. */
    void finish(Entry entry, boolean success);
    /** One-based page, 50 records per page. Called only by authorized management endpoints. */
    List<Entry> list(int page);
    /** 令牌管理操作（签发、修改、轮换、禁用、启用、撤销、删除）。失败只影响审计，不回滚操作。 */
    default void recordManagement(ManagementEntry entry) { }
    /** One-based page, 50 records per page. */
    default List<ManagementEntry> listManagement(int page) { return List.of(); }
    /** Periodic maintenance; custom retention or external lifecycle may implement a no-op. */
    void clean();
}
