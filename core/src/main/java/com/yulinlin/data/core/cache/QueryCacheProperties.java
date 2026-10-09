package com.yulinlin.data.core.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Common settings shared by all optional query-cache providers. */
@ConfigurationProperties("yulinlin.cache")
public class QueryCacheProperties {

    /** Queries always expire; reads never extend this duration. */
    private Duration ttl = Duration.ofMinutes(10);

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        requirePositive(ttl, "yulinlin.cache.ttl");
        this.ttl = ttl;
    }

    public Duration resolveTtl(Duration requested) {
        if (requested == null) return ttl;
        return requirePositive(requested, "cache ttl");
    }

    public static Duration requirePositive(Duration ttl, String name) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return ttl;
    }
}
