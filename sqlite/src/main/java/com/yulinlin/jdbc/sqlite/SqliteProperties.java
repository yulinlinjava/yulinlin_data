package com.yulinlin.jdbc.sqlite;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yulinlin.sqlite")
public class SqliteProperties {
    private String file;
    private String group = "local";
    private int busyTimeout = 5000;
    private Sync synchronous = Sync.NORMAL;
    private Schema schema = new Schema();
    public Schema getSchema() { return schema; }
    public void setSchema(Schema schema) { this.schema = schema; }
    public static class Schema {
        private boolean enabled;
        private java.util.List<String> packages = new java.util.ArrayList<>();
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public java.util.List<String> getPackages() { return packages; }
        public void setPackages(java.util.List<String> packages) { this.packages = packages; }
    }
    public enum Sync { NORMAL, FULL }
    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public int getBusyTimeout() { return busyTimeout; }
    public void setBusyTimeout(int busyTimeout) { this.busyTimeout = busyTimeout; }
    public Sync getSynchronous() { return synchronous; }
    public void setSynchronous(Sync synchronous) { this.synchronous = synchronous; }
}
