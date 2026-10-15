package com.yulinlin.data.cache.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yulinlin.cache.redis")
public class RedisCacheProperties {

    /** Isolates this application's cache keys from other Redis users. */
    private String keyPrefix = "dev";

    /** Redis writes are asynchronous by default; invalidation operations are always synchronous. */
    private RedisWriteMode writeMode = RedisWriteMode.ASYNC;

    /** Maximum commands submitted by one asynchronous pipeline; it is not a flush threshold. */
    private int batchSize = 128;

    /** Wire format used by the Redis L2. */
    private RedisValueSerializer serializer = RedisValueSerializer.SMILE;

    /** Rejects one serialized entry before it can monopolize Redis or the write queue. */
    private long maximumEntrySizeMb = 16;

    /** Disabled by default to keep counters away from the cache hot path. */
    private boolean recordStatistics;

    /** Optional in-process L1. */
    private final Caffeine caffeine = new Caffeine();

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("key-prefix must not be blank");
        }
        this.keyPrefix = trimTrailingColon(keyPrefix.trim());
    }

    public RedisWriteMode getWriteMode() {
        return writeMode;
    }

    public void setWriteMode(RedisWriteMode writeMode) {
        if (writeMode == null) throw new IllegalArgumentException("write-mode must not be null");
        this.writeMode = writeMode;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize <= 0) throw new IllegalArgumentException("batch-size must be positive");
        this.batchSize = batchSize;
    }

    public RedisValueSerializer getSerializer() {
        return serializer;
    }

    public void setSerializer(RedisValueSerializer serializer) {
        if (serializer == null) throw new IllegalArgumentException("serializer must not be null");
        this.serializer = serializer;
    }

    public long getMaximumEntrySizeMb() {
        return maximumEntrySizeMb;
    }

    public void setMaximumEntrySizeMb(long maximumEntrySizeMb) {
        if (maximumEntrySizeMb <= 0) {
            throw new IllegalArgumentException("maximum-entry-size-mb must be positive");
        }
        this.maximumEntrySizeMb = maximumEntrySizeMb;
    }

    public boolean isRecordStatistics() {
        return recordStatistics;
    }

    public void setRecordStatistics(boolean recordStatistics) {
        this.recordStatistics = recordStatistics;
    }

    public Caffeine getCaffeine() {
        return caffeine;
    }

    private static String trimTrailingColon(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ':') end--;
        if (end == 0) throw new IllegalArgumentException("key-prefix must not be blank");
        return value.substring(0, end);
    }

    public static class Caffeine {
        /** Enables the decoded-object L1. Disable it when every read must go through Redis. */
        private boolean enabled = true;

        /** Shared maximum number of ORM-query and application-cache entries in this JVM. */
        private long maximumSize = 10_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            if (maximumSize <= 0) throw new IllegalArgumentException("maximum-size must be positive");
            this.maximumSize = maximumSize;
        }
    }
}
