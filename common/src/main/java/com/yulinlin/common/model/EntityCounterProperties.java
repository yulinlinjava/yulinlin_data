package com.yulinlin.common.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 实体计数器的进程内聚合配置。
 */
@ConfigurationProperties("yulinlin.data.counter")
public class EntityCounterProperties {

    /** 定时刷新间隔。 */
    private Duration flushInterval = Duration.ofMinutes(1);

    /** 更新行、插入维度组及待重试插入行合计达到该数量后立即异步刷新。 */
    private int flushSize = 1_024;

    /** 计数器写入后是否清理对应查询缓存。 */
    private boolean clearCache;

    public Duration getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(Duration flushInterval) {
        if (flushInterval == null || flushInterval.isNegative() || flushInterval.isZero()
                || flushInterval.toMillis() == 0) {
            throw new IllegalArgumentException("yulinlin.data.counter.flush-interval must be at least 1ms");
        }
        this.flushInterval = flushInterval;
    }

    public int getFlushSize() {
        return flushSize;
    }

    public void setFlushSize(int flushSize) {
        if (flushSize <= 0) {
            throw new IllegalArgumentException("yulinlin.data.counter.flush-size must be positive");
        }
        this.flushSize = flushSize;
    }

    public boolean isClearCache() {
        return clearCache;
    }

    public void setClearCache(boolean clearCache) {
        this.clearCache = clearCache;
    }
}
