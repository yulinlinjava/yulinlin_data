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
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("yulinlin.cache.ttl must be positive");
        }
        this.ttl = ttl;
    }
}
