package com.yulinlin.security.session;

import java.time.Instant;

/** 管理视图，不包含 Token、原始 Cookie 或完整 principal。无法获得的元数据为 null。 */
public record LoginSessionView(String sessionId, String userId, String username,
                               String deviceType, String deviceId, Instant createdAt,
                               Instant lastAccessAt, Instant expiresAt, Boolean current) {
    public LoginSessionView {
        SessionSupport.requireText(sessionId, "sessionId");
        SessionSupport.requireText(userId, "userId");
    }
}
