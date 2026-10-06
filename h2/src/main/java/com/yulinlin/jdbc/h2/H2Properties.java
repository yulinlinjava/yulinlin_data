package com.yulinlin.jdbc.h2;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("yulinlin.h2")
public class H2Properties {
    public enum Mode {
        MYSQL("MySQL");
        private final String jdbcValue;
        Mode(String jdbcValue) { this.jdbcValue = jdbcValue; }
        String jdbcValue() { return jdbcValue; }
    }
    public enum SchemaMode { CREATE, VALIDATE, NONE }

    private String file = "data/local";
    private String group = "h2";
    private String username = "sa";
    private String password = "";
    private Mode mode = Mode.MYSQL;
    private int maxConnections = 4;
    private Duration connectionTimeout = Duration.ofSeconds(10);
    private Duration lockTimeout = Duration.ofSeconds(5);
    private int batchSize = 256;
    private SchemaMode schemaMode = SchemaMode.CREATE;
    private boolean autoServer;
    private List<String> schemaPackages = new ArrayList<>();

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
    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
    public Duration getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(Duration connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    public Duration getLockTimeout() { return lockTimeout; }
    public void setLockTimeout(Duration lockTimeout) { this.lockTimeout = lockTimeout; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public SchemaMode getSchemaMode() { return schemaMode; }
    public void setSchemaMode(SchemaMode schemaMode) { this.schemaMode = schemaMode; }
    public boolean isAutoServer() { return autoServer; }
    public void setAutoServer(boolean autoServer) { this.autoServer = autoServer; }
    public List<String> getSchemaPackages() { return schemaPackages; }
    public void setSchemaPackages(List<String> schemaPackages) {
        this.schemaPackages = schemaPackages == null ? new ArrayList<>() : new ArrayList<>(schemaPackages);
    }
}
