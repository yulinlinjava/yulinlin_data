package com.yulinlin.data.core.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Framework-wide ORM behavior shared by every registered data source. */
@ConfigurationProperties("yulinlin.data")
public class DataProperties {

    /**
     * Makes entity query results transaction-managed. Managed setters are flushed before commit.
     */
    private boolean autoUpdate;

    public boolean isAutoUpdate() {
        return autoUpdate;
    }

    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }
}
