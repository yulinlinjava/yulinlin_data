package com.yulinlin.jdbc.sqlite;

import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.jdbc.JdbcSessionProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yulinlin.sqlite")
public class SqliteProperties extends JdbcSessionProperties {
    private String file = "data/local.db";
    private String group = "sqlite";
    private int busyTimeout = 5000;
    private Sync synchronous = Sync.NORMAL;
    public enum Sync { NORMAL, FULL }
    public SqliteProperties() {
        setParallelConnections(1);
        setSchemaMode(SchemaMode.CREATE);
    }
    @Override public void setParallelConnections(int parallelConnections) {
        if (parallelConnections != 1) {
            throw new IllegalArgumentException("SQLite parallelConnections must be 1");
        }
        super.setParallelConnections(1);
    }
    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public int getBusyTimeout() { return busyTimeout; }
    public void setBusyTimeout(int busyTimeout) { this.busyTimeout = busyTimeout; }
    public Sync getSynchronous() { return synchronous; }
    public void setSynchronous(Sync synchronous) { this.synchronous = synchronous; }
}
