package com.yulinlin.data.core.loadbalan;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yulinlin.datasource")
public class LoadBalanceProperties {
    private String defaultGroup;

    public String getDefaultGroup() { return defaultGroup; }
    public void setDefaultGroup(String defaultGroup) { this.defaultGroup = defaultGroup; }
}
