package com.yulinlin.security.jwt;

import com.yulinlin.security.auth.IUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/** 默认 HS256 实现；仅此实现读取 jwt.secret 和 jwt.issuer，自定义 Codec 不需要配置默认密钥。 */
public final class DefaultJwtTokenCodec implements JwtTokenCodec {
    private final SecretKey key;
    private final JwtParser parser;
    private final String issuer;
    private final Duration ttl;

    public DefaultJwtTokenCodec(JwtProperties properties) {
        properties.validate();
        ttl = properties.getTtl();
        issuer = properties.getIssuer();
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("yulinlin.security.jwt.issuer 不能为空");
        }
        key = signingKey(properties.getSecret());
        parser = Jwts.parser().verifyWith(key).requireIssuer(issuer)
                .sig().clear().add(Jwts.SIG.HS256).and().build();
    }

    @Override
    public String generate(IUserDetails user, String deviceType, String authType) {
        Objects.requireNonNull(user, "登录用户不能为空");
        if (deviceType == null || deviceType.isBlank()) throw new IllegalArgumentException("deviceType 不能为空");
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        TokenInfo info = new TokenInfo(user.getUserId(), deviceType, authType, UUID.randomUUID().toString(),
                issuedAt, issuedAt.plus(ttl).truncatedTo(ChronoUnit.SECONDS));
        return Jwts.builder().issuer(issuer).subject(info.userId()).id(info.tokenId())
                .claim("deviceType", info.deviceType())
                .claim("authType", info.authType())
                .issuedAt(Date.from(info.issuedAt())).expiration(Date.from(info.expiresAt()))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    @Override
    public TokenInfo parse(String token) {
        if (token == null || token.isBlank() || token.length() > 8192) return null;
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            Date issuedAt = claims.getIssuedAt();
            Date expiresAt = claims.getExpiration();
            if (issuedAt == null || expiresAt == null) return null;
            TokenInfo result = new TokenInfo(claims.getSubject(), claims.get("deviceType", String.class),
                    claims.get("authType", String.class), claims.getId(), issuedAt.toInstant(), expiresAt.toInstant());
            return result.validAt(Instant.now()) ? result : null;
        } catch (JwtException | IllegalArgumentException error) {
            return null;
        }
    }

    private static SecretKey signingKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("请配置 yulinlin.security.jwt.secret：至少 32 字节随机密钥的 BASE64 值");
        }
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(secret); }
        catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("yulinlin.security.jwt.secret 必须是合法 BASE64，不能直接填写普通密码");
        }
        try {
            if (bytes.length < 32) {
                throw new IllegalArgumentException("yulinlin.security.jwt.secret 解码后必须至少 32 字节");
            }
            return new SecretKeySpec(bytes, "HmacSHA256");
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
