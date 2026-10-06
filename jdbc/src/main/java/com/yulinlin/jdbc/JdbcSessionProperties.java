package com.yulinlin.jdbc;

import com.yulinlin.data.core.session.EntitySessionProperties;

/** JDBC-specific settings layered on top of the common entity-session settings. */
public class JdbcSessionProperties extends EntitySessionProperties {
    private int parallelConnections = 4;
    private int executeBatchSize = 256;

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
}
