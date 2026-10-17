package com.yulinlin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Local API rate-limit settings. */
@ConfigurationProperties("yulinlin.security.limit")
public class SecurityLimitProperties {

    /** Enables @ApiLimit processing. */
    private boolean enabled = true;

    /** Maximum number of active endpoint and client counters retained locally. */
    private long maximumSize = 50_000;

    /** Client IP header accepted only when the direct peer is trusted. */
    private String ipHeader = "X-Real-IP";

    /** Client-generated device identifier header. */
    private String deviceIdHeader = "Device-Id";

    /** Exact IP addresses or CIDR blocks allowed to supply ip-header. */
    private List<String> trustedProxies = new ArrayList<>(List.of("127.0.0.1", "::1"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
        if (maximumSize <= 0) throw new IllegalArgumentException("maximum-size must be positive");
        this.maximumSize = maximumSize;
    }

    public String getIpHeader() {
        return ipHeader;
    }

    public void setIpHeader(String ipHeader) {
        this.ipHeader = requireHeader(ipHeader, "ip-header");
    }

    public String getDeviceIdHeader() {
        return deviceIdHeader;
    }

    public void setDeviceIdHeader(String deviceIdHeader) {
        this.deviceIdHeader = requireHeader(deviceIdHeader, "device-id-header");
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null
                ? new ArrayList<>()
                : new ArrayList<>(trustedProxies);
    }

    private static String requireHeader(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
        return value.trim();
    }
}
