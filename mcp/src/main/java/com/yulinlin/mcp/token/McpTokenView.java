package com.yulinlin.mcp.token;

import java.util.*;

/** 管理接口返回的令牌信息，不含任何 hash。 */
public record McpTokenView(
        String id,
        String keyId,
        String display,
        String name,
        String remark,
        String ownerId,
        String issuerId,
        McpTokenStatus status,
        long expiresAt,
        List<String> scope,
        Map<String, String> attributes,
        List<String> allowedIps,
        int rateLimit,
        long createdAt,
        long updatedAt,
        long rotatedAt,
        /** 轮换后旧令牌的失效时间，0 表示没有处于宽限期的旧令牌 */
        long previousExpiresAt,
        long lastUsedAt,
        String lastUsedIp) {

    public static McpTokenView of(McpToken t, long now) {
        return new McpTokenView(t.id(), t.keyId(), McpTokenCodec.display(t.keyId()), t.name(), t.remark(), t.ownerId(),
                t.issuerId(), t.effectiveStatus(now), t.expiresAt(), t.scope(), t.attributes(), t.allowedIps(), t.rateLimit(),
                t.createdAt(), t.updatedAt(), t.rotatedAt(), t.previousExpiresAt() > now ? t.previousExpiresAt() : 0,
                t.lastUsedAt(), t.lastUsedIp());
    }

    /** 签发或轮换结果；token 明文只在这里出现一次。 */
    public record Issued(McpTokenView credential, String token) {
        @Override public String toString() { return "McpTokenIssued[redacted]"; }
    }

    public record Page(List<McpTokenView> items, long total, int page, int size) { }
}
