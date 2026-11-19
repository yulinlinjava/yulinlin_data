package com.yulinlin.security.limit;

import com.yulinlin.security.SecurityLimitProperties;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;
import java.util.regex.Pattern;

/** Device identity validation and request-local reuse of the selected IP resolver. */
final class ClientIdentityResolver {
    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final String RESOLVED_IP_ATTRIBUTE = ClientIdentityResolver.class.getName() + ".clientIp";
    private final String deviceIdHeader;
    private final IpResolver ipResolver;

    ClientIdentityResolver(SecurityLimitProperties properties, IpResolver ipResolver) {
        this.deviceIdHeader = properties.getDeviceIdHeader();
        this.ipResolver = Objects.requireNonNull(ipResolver, "ipResolver must not be null");
    }

    String resolve(HttpServletRequest request, LimitBy by) {
        if (by == LimitBy.DEVICE) {
            String deviceId = request.getHeader(deviceIdHeader);
            if (deviceId != null) {
                String normalized = deviceId.trim();
                if (DEVICE_ID.matcher(normalized).matches()) return normalized;
            }
        }
        return resolveIp(request);
    }

    String resolveIp(HttpServletRequest request) {
        Object cached = request.getAttribute(RESOLVED_IP_ATTRIBUTE);
        if (cached instanceof ResolvedIp value && value.owner() == ipResolver) return value.ip();
        String ip = ipResolver.resolve(request);
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        if (ip == null || ip.isBlank()) ip = "unknown";
        request.setAttribute(RESOLVED_IP_ATTRIBUTE, new ResolvedIp(ipResolver, ip));
        return ip;
    }

    private record ResolvedIp(IpResolver owner, String ip) { }
}
