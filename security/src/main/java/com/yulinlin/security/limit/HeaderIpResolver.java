package com.yulinlin.security.limit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves forwarding headers in the legacy RequestUtil order.
 * The gateway must overwrite untrusted forwarding headers before forwarding requests.
 */
public final class HeaderIpResolver implements IpResolver {
    private static final String[] FALLBACK_HEADERS = {
            "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED", "HTTP_X_CLUSTER_CLIENT_IP", "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR", "HTTP_FORWARDED", "HTTP_VIA", "REMOTE_ADDR"
    };

    @Override
    public String resolve(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            int comma = forwarded.indexOf(',');
            String first = usable(comma < 0 ? forwarded : forwarded.substring(0, comma));
            if (first != null) return first;
        }
        for (String header : FALLBACK_HEADERS) {
            String value = usable(request.getHeader(header));
            if (value != null) return value;
        }
        return request.getRemoteAddr();
    }

    private static String usable(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "unknown".equalsIgnoreCase(trimmed) ? null : trimmed;
    }
}
