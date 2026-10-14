package com.yulinlin.jdbc;

import com.yulinlin.data.core.session.EntitySessionProperties;

import java.time.Duration;

/** JDBC-specific settings layered on top of the common entity-session settings. */
public class JdbcSessionProperties extends EntitySessionProperties {
    private int parallelConnections = 4;
    private int executeBatchSize = 256;
    /** Whether bound parameters are included in SQL logs. Disabled by default to avoid leaking data. */
    private boolean logParams;
    /** Maximum number of characters retained for one logged parameter. */
    private int maxParamLength = 256;
    /** SQL duration at which one WARN is emitted; zero disables slow-SQL logging. */
    private Duration slowSqlThreshold = Duration.ZERO;

    public int getParallelConnections() {
        return parallelConnections;
    }

    public void setParallelConnections(int parallelConnections) {
        if (parallelConnections < 1) {
            throw new IllegalArgumentException("parallelConnections must be positive");
        }
        this.parallelConnections = parallelConnections;
    }

    public int getExecuteBatchSize() {
        return executeBatchSize;
    }

    public void setExecuteBatchSize(int executeBatchSize) {
        if (executeBatchSize < 1) {
            throw new IllegalArgumentException("executeBatchSize must be positive");
        }
        this.executeBatchSize = executeBatchSize;
    }

    public boolean isLogParams() {
        return logParams;
    }

    public void setLogParams(boolean logParams) {
        this.logParams = logParams;
    }

    public int getMaxParamLength() {
        return maxParamLength;
    }

    public void setMaxParamLength(int maxParamLength) {
        if (maxParamLength < 1) {
            throw new IllegalArgumentException("maxParamLength must be positive");
        }
        this.maxParamLength = maxParamLength;
    }

    public Duration getSlowSqlThreshold() {
        return slowSqlThreshold;
    }

    public void setSlowSqlThreshold(Duration slowSqlThreshold) {
        if (slowSqlThreshold == null || slowSqlThreshold.isNegative()) {
            throw new IllegalArgumentException("slowSqlThreshold must not be null or negative");
        }
        this.slowSqlThreshold = slowSqlThreshold;
    }
}
