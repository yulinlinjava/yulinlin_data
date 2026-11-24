package com.yulinlin.mcp.token;

import com.yulinlin.mcp.store.McpAudit.ManagementEntry;
import com.yulinlin.mcp.store.McpAuditService;
import com.yulinlin.mcp.store.McpStore;
import org.springframework.context.event.EventListener;

/** 把令牌管理事件写入管理审计。 */
public class McpTokenAuditListener {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(McpTokenAuditListener.class);
    private final McpAuditService audit;

    public McpTokenAuditListener(McpAuditService audit) { this.audit = audit; }

    @EventListener
    public void on(McpTokenEvent event) {
        var token = event.token();
        try {
            audit.recordManagement(new ManagementEntry(McpStore.id(), event.action().name(), token.id(), token.keyId(),
                    token.ownerId(), event.actorId(), event.ip(), event.changes(), event.time()));
        } catch (RuntimeException e) {
            log.warn("MCP 管理审计保存失败 action={} keyId={}", event.action(), token.keyId(), e);
        }
    }
}
