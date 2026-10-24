package com.yulinlin.jdbc;

import com.yulinlin.data.core.session.EntitySessionProperties;

import java.time.Duration;

/** JDBC-specific settings layered on top of the common entity-session settings. */
public class JdbcSessionProperties extends EntitySessionProperties {
    /** 用户已确认数据可转换时，允许启动 CREATE 模式修改列类型或扩容；默认关闭。 */
    private boolean alterColumnType;
    private int parallelConnections = 4;
    private int executeBatchSize = 256;
    /** Whether bound parameters are included in SQL logs. Disabled by default to avoid leaking data. */
    private boolean logParams;
    /** 仅美化日志 SQL：简化标识符引号和空格，不添加展示转义；不修改执行 SQL。 */
    private boolean logSqlPretty = true;
    /** Maximum number of characters retained for one logged parameter. */
    private int maxParamLength = 256;
    /** SQL duration at which one WARN is emitted; zero disables slow-SQL logging. */
    private Duration slowSqlThreshold = Duration.ZERO;

    public boolean isAlterColumnType() { return alterColumnType; }

    public void setAlterColumnType(boolean alterColumnType) { this.alterColumnType = alterColumnType; }

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

    public boolean isLogSqlPretty() { return logSqlPretty; }

    public void setLogSqlPretty(boolean logSqlPretty) { this.logSqlPretty = logSqlPretty; }

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
