package com.yulinlin.security.auth;

import java.time.Instant;

/** 登录接口响应；不返回签名密钥或用户密码。expiresAt 为 null 表示永久有效。 */
public record LoginResult(String token, Instant expiresAt, IUserDetails user) {
    public static LoginResult of(LoginSession session) {
        return new LoginResult(session.token(), session.expiresAt(), session.user());
    }

    @Override public String toString() { return "LoginResult[expiresAt=" + expiresAt + "]"; }
}
