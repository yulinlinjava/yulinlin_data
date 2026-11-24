package com.yulinlin.mcp.token;

import java.util.Map;

/**
 * 令牌管理操作事件，通过 Spring ApplicationEventPublisher 发布。
 * 用 {@code @EventListener} 监听即可做通知、同步、自定义审计等。
 *
 * @param token   操作后的令牌（DELETE 为删除前），不含 hash
 * @param actorId 操作人账号
 * @param changes 变化的字段：字段名 → {from, to}；ISSUE 时为签发参数
 */
public record McpTokenEvent(Action action, McpTokenView token, String actorId, String ip,
                            Map<String, Object> changes, long time) {
    public enum Action { ISSUE, UPDATE, ROTATE, DISABLE, ENABLE, REVOKE, DELETE }

    public McpTokenEvent {
        changes = changes == null ? Map.of() : Map.copyOf(changes);
        ip = ip == null ? "" : ip;
    }
}
