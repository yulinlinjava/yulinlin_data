package com.yulinlin.security.auth;

import java.time.Instant;
import java.util.Objects;

/** 请求内登录快照；user 不得包含密码或密钥。expiresAt 为 null 表示底层配置了永久有效。 */
public record LoginSession(String token, Instant expiresAt, IUserDetails user) {
    public LoginSession {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Token 不能为空");
        Objects.requireNonNull(user, "user");
    }

    public boolean expired() { return expiresAt != null && !expiresAt.isAfter(Instant.now()); }

    /** 避免普通日志意外泄露 Token 和用户资料。 */
    @Override public String toString() { return "LoginSession[expiresAt=" + expiresAt + "]"; }
}
