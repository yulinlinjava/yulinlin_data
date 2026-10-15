package com.yulinlin.monitor;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Settings for the optional in-memory slow database monitor. */
@ConfigurationProperties("yulinlin.monitor")
public class MonitorProperties {
    /** Controls database-operation collection and all built-in monitor HTTP endpoints. */
    private boolean enabled = true;
    private Duration threshold = Duration.ofSeconds(1);
    private int maxRecords = 1000;
    /** Username accepted by the monitor page's built-in login dialog. */
    private String username = "admin";
    /** Password accepted by the monitor page. Empty disables login until configured. */
    private String password = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getThreshold() {
        return threshold;
    }

    public void setThreshold(Duration threshold) {
        if (threshold == null || threshold.isNegative()) {
            throw new IllegalArgumentException("threshold must not be null or negative");
        }
        this.threshold = threshold;
    }

    public int getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(int maxRecords) {
        if (maxRecords < 1) throw new IllegalArgumentException("maxRecords must be positive");
        this.maxRecords = maxRecords;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("username must not be empty");
        this.username = value;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password == null ? "" : password;
    }
}
