package com.yulinlin.data.cache.caffeine;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yulinlin.cache.caffeine")
public class CaffeineCacheProperties {

    /** Initial size of Caffeine's internal hash table. */
    private int initialCapacity = 16;

    /** Shared upper bound for ORM query entries and CacheClient entries. */
    private long maximumSize = 10_000;

    /** Whether a successful read renews the entry's request/default TTL. */
    private ExpirationPolicy expirationPolicy = ExpirationPolicy.AFTER_WRITE;

    /** Promptly schedules expiry instead of waiting for normal cache maintenance. */
    private boolean useSystemScheduler;

    /** Executor used by Caffeine for maintenance and notifications. */
    private ExecutorMode executor = ExecutorMode.COMMON_POOL;

    /** Disabled by default to avoid counters on the cache hot path. */
    private boolean recordStats;

    public int getInitialCapacity() {
        return initialCapacity;
    }

    public void setInitialCapacity(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException("initial-capacity must not be negative");
        this.initialCapacity = initialCapacity;
    }

    public long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
        if (maximumSize <= 0) throw new IllegalArgumentException("maximum-size must be positive");
        this.maximumSize = maximumSize;
    }

    public ExpirationPolicy getExpirationPolicy() {
        return expirationPolicy;
    }

    public void setExpirationPolicy(ExpirationPolicy expirationPolicy) {
        if (expirationPolicy == null) throw new IllegalArgumentException("expiration-policy must not be null");
        this.expirationPolicy = expirationPolicy;
    }

    public boolean isUseSystemScheduler() {
        return useSystemScheduler;
    }

    public void setUseSystemScheduler(boolean useSystemScheduler) {
        this.useSystemScheduler = useSystemScheduler;
    }

    public ExecutorMode getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorMode executor) {
        if (executor == null) throw new IllegalArgumentException("executor must not be null");
        this.executor = executor;
    }

    public boolean isRecordStats() {
        return recordStats;
    }

    public void setRecordStats(boolean recordStats) {
        this.recordStats = recordStats;
    }

    public enum ExpirationPolicy {
        /** TTL starts when an entry is written and reads never extend it. */
        AFTER_WRITE,
        /** Each successful read restarts the entry's request/default TTL. */
        AFTER_ACCESS
    }

    public enum ExecutorMode {
        /** Caffeine's default shared ForkJoinPool; minimizes request-thread maintenance work. */
        COMMON_POOL,
        /** Runs maintenance inline; predictable but can add latency to the calling thread. */
        DIRECT
    }
}
