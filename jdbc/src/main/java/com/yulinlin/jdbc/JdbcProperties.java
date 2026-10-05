package com.yulinlin.jdbc;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties("yulinlin.datasource")
public class JdbcProperties {

    @Value("${log:false}")
    private boolean log;



    @Value("${mapUnderscoreToCamelCase:true}")
    private boolean mapUnderscoreToCamelCase;

    /** Maximum physical connections used by one framework JDBC transaction's parallel batches. */
    private int parallelConnections = 4;

    /** Rows sent in one executeBatch() call; this does not change the transaction commit boundary. */
    private int executeBatchSize = 256;

    public void setExecuteBatchSize(int executeBatchSize) {
        if (executeBatchSize < 1) throw new IllegalArgumentException("executeBatchSize must be positive");
        this.executeBatchSize = executeBatchSize;
    }

    public void setParallelConnections(int parallelConnections) {
        if (parallelConnections < 1) throw new IllegalArgumentException("parallelConnections must be positive");
        this.parallelConnections = parallelConnections;
    }

}
