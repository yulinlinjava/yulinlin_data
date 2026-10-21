package com.yulinlin.security.auth;

import java.time.Instant;
import java.util.Objects;

/** 请求内身份快照；user 不得包含凭证。外部认证可无 Token；expiresAt=null 表示由底层校验有效期。 */
public record LoginSession(String token, Instant expiresAt, IUserDetails user) {
    public LoginSession {
        if (token != null && token.isBlank()) throw new IllegalArgumentException("Token 不能是空字符串；无 Token 的外部认证使用 null");
        Objects.requireNonNull(user, "user");
    }

    public boolean expired() { return expiresAt != null && !expiresAt.isAfter(Instant.now()); }

    /** 避免普通日志意外泄露 Token 和用户资料。 */
    @Override public String toString() { return "LoginSession[expiresAt=" + expiresAt + "]"; }
}
