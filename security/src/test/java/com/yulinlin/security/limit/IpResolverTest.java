package com.yulinlin.security.limit;

import com.yulinlin.security.SecurityLimitProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IpResolverTest {
    private final IpResolver resolver = new HeaderIpResolver();

    @Test void followsHeaderPriorityAndTrimsFirstForwardedAddress() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", " 192.0.2.1 , 192.0.2.2");
        request.addHeader("Proxy-Client-IP", "192.0.2.3");
        assertEquals("192.0.2.1", resolver.resolve(request));
        request.removeHeader("X-Forwarded-For");
        request.addHeader("WL-Proxy-Client-IP", "192.0.2.4");
        assertEquals("192.0.2.3", resolver.resolve(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED", "HTTP_X_CLUSTER_CLIENT_IP", "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR", "HTTP_FORWARDED", "HTTP_VIA", "REMOTE_ADDR"})
    void supportsEachLegacyFallbackHeader(String header) {
        var request = new MockHttpServletRequest();
        request.addHeader(header, " 192.0.2.10 ");
        assertEquals("192.0.2.10", resolver.resolve(request));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "UNKNOWN", " unknown ", ",", " ,192.0.2.2", "unknown,192.0.2.2"})
    void missingOrUnknownFirstForwardedAddressUsesNextHeader(String value) {
        var request = new MockHttpServletRequest();
        if (value != null) request.addHeader("X-Forwarded-For", value);
        request.addHeader("Proxy-Client-IP", " unknown ");
        request.addHeader("WL-Proxy-Client-IP", " 192.0.2.20 ");
        assertEquals("192.0.2.20", resolver.resolve(request));
    }

    @Test void supportsIpv6RemoteFallbackAndNoRequest() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8::1");
        request.addHeader("X-Real-IP", "192.0.2.30");
        assertEquals("2001:db8::1", resolver.resolve(request), "保持指定顺序，不额外增加 X-Real-IP");
        request.addHeader("X-Forwarded-For", "2001:db8::2, 2001:db8::3");
        assertEquals("2001:db8::2", resolver.resolve(request));
        assertNull(resolver.resolve(null));
    }

    @Test void trustedProxyImplementationPreservesPreviousPolicy() {
        var properties = new SecurityLimitProperties();
        properties.setTrustedProxies(List.of("192.0.2.0/24", "2001:db8::/32"));
        var trusted = new TrustedProxyIpResolver(properties);
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.1");
        request.addHeader("X-Real-IP", "203.0.113.1");
        assertEquals("198.51.100.1", trusted.resolve(request));
        request.setRemoteAddr("192.0.2.5");
        assertEquals("203.0.113.1", trusted.resolve(request));
        request.setRemoteAddr("2001:db8::5");
        assertEquals("203.0.113.1", trusted.resolve(request));
        request.removeHeader("X-Real-IP");
        request.addHeader("X-Real-IP", "203.0.113.1, 203.0.113.2");
        request.setRemoteAddr("192.0.2.5");
        assertEquals("192.0.2.5", trusted.resolve(request));
        assertNull(trusted.resolve(null));
    }

    @Test void customResolverFailuresPropagateAndBlankResultsFallBackToRemoteAddress() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        var blank = new ClientIdentityResolver(new SecurityLimitProperties(), ignored -> " ");
        assertEquals("192.0.2.1", blank.resolveIp(request));
        var failure = new IllegalStateException("resolver failed");
        var failing = new ClientIdentityResolver(new SecurityLimitProperties(), ignored -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> failing.resolveIp(request)));
    }
}
