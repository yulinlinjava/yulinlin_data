package com.yulinlin.data.cache.caffeine;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yulinlin.cache.caffeine")
public class CaffeineCacheProperties {

    private long maximumSize = 10_000;
    private boolean recordStats = true;

    public long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
        if (maximumSize <= 0) throw new IllegalArgumentException("maximum-size must be positive");
        this.maximumSize = maximumSize;
    }

    public boolean isRecordStats() {
        return recordStats;
    }

    public void setRecordStats(boolean recordStats) {
        this.recordStats = recordStats;
    }
}
