package com.yulinlin.security.jwt;

import java.time.Instant;

/**
 * 经过验证的 Token 登录信息；不包含用户资料。tokenId 是每次登录的唯一标识。
 * 旧 Token 未携带 deviceType 时允许为 null，不依赖服务端记录补齐；不放宽签名和有效期校验。
 */
public record TokenInfo(String userId, String deviceType, String tokenId, Instant issuedAt, Instant expiresAt) {
    public TokenInfo {
        if (tokenId == null || tokenId.isBlank()) throw new IllegalArgumentException("tokenId 不能为空");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId 不能为空");
        if (deviceType != null && deviceType.isBlank()) throw new IllegalArgumentException("deviceType 不能是空字符串");
        if (issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("Token 签发时间和过期时间不能为空，且过期时间必须晚于签发时间");
        }
    }

    public boolean validAt(Instant now) {
        return !issuedAt.isAfter(now) && expiresAt.isAfter(now);
    }

    @Override public String toString() { return "TokenInfo[expiresAt=" + expiresAt + "]"; }
}
