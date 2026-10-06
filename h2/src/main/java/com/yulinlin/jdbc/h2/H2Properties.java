package com.yulinlin.jdbc.h2;

import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.jdbc.JdbcSessionProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("yulinlin.h2")
public class H2Properties extends JdbcSessionProperties {
    public enum Mode {
        MYSQL("MySQL");
        private final String jdbcValue;
        Mode(String jdbcValue) { this.jdbcValue = jdbcValue; }
        String jdbcValue() { return jdbcValue; }
    }
    private String file = "data/local";
    private String group = "h2";
    private String username = "sa";
    private String password = "";
    private Mode mode = Mode.MYSQL;
    private Duration connectionTimeout = Duration.ofSeconds(10);
    private Duration lockTimeout = Duration.ofSeconds(5);
    private boolean autoServer;

    public H2Properties() {
        setSchemaMode(SchemaMode.CREATE);
    }

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    /** @deprecated Use parallelConnections / yulinlin.h2.parallel-connections. */
    @Deprecated
    public int getMaxConnections() { return getParallelConnections(); }
    /** @deprecated Use parallelConnections / yulinlin.h2.parallel-connections. */
    @Deprecated
    public void setMaxConnections(int maxConnections) { setParallelConnections(maxConnections); }
    public Duration getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(Duration connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    public Duration getLockTimeout() { return lockTimeout; }
    public void setLockTimeout(Duration lockTimeout) { this.lockTimeout = lockTimeout; }
    /** @deprecated Use executeBatchSize / yulinlin.h2.execute-batch-size. */
    @Deprecated
    public int getBatchSize() { return getExecuteBatchSize(); }
    /** @deprecated Use executeBatchSize / yulinlin.h2.execute-batch-size. */
    @Deprecated
    public void setBatchSize(int batchSize) { setExecuteBatchSize(batchSize); }
    public boolean isAutoServer() { return autoServer; }
    public void setAutoServer(boolean autoServer) { this.autoServer = autoServer; }
}
