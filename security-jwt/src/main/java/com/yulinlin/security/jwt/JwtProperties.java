package com.yulinlin.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("yulinlin.security.jwt")
public class JwtProperties {
    /** 默认 Codec 使用的 BASE64 随机签名密钥，至少 32 字节；自定义 JwtTokenCodec 时无需提供。 */
    private String secret;
    private String issuer = "yulinlin";
    /** 默认 Codec 签发的固定有效期；自定义 Codec 以解析出的 expiresAt 为准，refresh 不续期。 */
    private Duration ttl = Duration.ofHours(2);

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }

    public void validate() {
        if (ttl == null || ttl.compareTo(Duration.ofSeconds(1)) < 0
                || ttl.compareTo(Duration.ofDays(365)) > 0) {
            throw new IllegalArgumentException("yulinlin.security.jwt.ttl 必须在 1s 到 365d 之间");
        }
    }
}
