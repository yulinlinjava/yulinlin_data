package com.yulinlin.data.core.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Common settings shared by all optional query-cache providers. */
@ConfigurationProperties("yulinlin.cache")
public class QueryCacheProperties {

    /** Queries always expire; reads never extend this duration. */
    private Duration ttl = Duration.ofMinutes(10);

    /** Whether the resolved query TTL contributes to the physical cache key. */
    private boolean ttlInKey = false;

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        requirePositive(ttl, "yulinlin.cache.ttl");
        this.ttl = ttl;
    }

    public boolean isTtlInKey() {
        return ttlInKey;
    }

    public void setTtlInKey(boolean ttlInKey) {
        this.ttlInKey = ttlInKey;
    }

    public Duration resolveTtl(Duration requested) {
        if (requested == null) return ttl;
        return requirePositive(requested, "cache ttl");
    }

    /** Resolves and validates TTL, then returns the portion used by the cache-key policy. */
    public Duration resolveKeyTtl(Duration requested) {
        Duration resolved = resolveTtl(requested);
        return ttlInKey ? resolved : null;
    }

    public static Duration requirePositive(Duration ttl, String name) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return ttl;
    }
}
