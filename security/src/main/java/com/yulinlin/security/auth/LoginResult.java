package com.yulinlin.security.auth;

import java.time.Instant;

/** 登录接口响应；不返回签名密钥或密码。expiresAt 为 null 表示不声明到期时间，由底层决定有效性。 */
public record LoginResult(String token, Instant expiresAt, IUserDetails user, String authType) {
    public static LoginResult of(LoginSession session) {
        return new LoginResult(session.token(), session.expiresAt(), session.user(), session.authType());
    }

    @Override public String toString() { return "LoginResult[expiresAt=" + expiresAt + "]"; }
}
