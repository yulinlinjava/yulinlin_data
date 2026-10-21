package com.yulinlin.data.core.http;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

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

    /** 普通请求最多读入内存的响应正文；流式下载不受此限制。 */
    private DataSize maxResponseSize = DataSize.ofMegabytes(16);

    /** 错误正文最多保留的字节数，超出部分截断。 */
    private DataSize maxErrorSize = DataSize.ofKilobytes(64);

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public DataSize getMaxResponseSize() { return maxResponseSize; }
    public void setMaxResponseSize(DataSize maxResponseSize) { this.maxResponseSize = maxResponseSize; }
    public DataSize getMaxErrorSize() { return maxErrorSize; }
    public void setMaxErrorSize(DataSize maxErrorSize) { this.maxErrorSize = maxErrorSize; }
}
