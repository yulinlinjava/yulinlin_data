package com.yulinlin.data.core.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Global defaults for the HTTP request utility.
 */
@ConfigurationProperties("yulinlin.http")
public class HttpRequestProperties {

    /**
     * Maximum time allowed for one request to receive and read its response.
     */
    private Duration timeout = Duration.ofSeconds(10);

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
