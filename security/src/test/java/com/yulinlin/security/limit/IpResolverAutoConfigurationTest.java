package com.yulinlin.security.limit;

import com.yulinlin.security.SecurityAutoConfiguration;
import com.yulinlin.security.annotation.ApiLimit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class IpResolverAutoConfigurationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class));

    static class Controller {
        @ApiLimit(count = 1) public void limited() { }
    }

    @Test void registersDefaultAndUsesForwardedIpForLimit() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(IpResolver.class);
            assertInstanceOf(HeaderIpResolver.class, context.getBean(IpResolver.class));
            var interceptor = context.getBean(ApiLimitInterceptor.class);
            var handler = new HandlerMethod(new Controller(), Controller.class.getMethod("limited"));
            var first = new MockHttpServletRequest();
            first.addHeader("X-Forwarded-For", "192.0.2.1, 192.0.2.2");
            assertTrue(interceptor.preHandle(first, new MockHttpServletResponse(), handler));
            var sameIp = new MockHttpServletRequest();
            sameIp.addHeader("X-Forwarded-For", "192.0.2.1, 192.0.2.3");
            assertThrows(ApiLimitException.class,
                    () -> interceptor.preHandle(sameIp, new MockHttpServletResponse(), handler));
            var differentIp = new MockHttpServletRequest();
            differentIp.addHeader("X-Forwarded-For", "192.0.2.4");
            assertTrue(interceptor.preHandle(differentIp, new MockHttpServletResponse(), handler));
        });
    }

    @Test void customBeanReplacesDefaultAndIsSharedByBlacklistAndRateLimit() {
        var calls = new AtomicInteger();
        var checkedIps = new ArrayList<String>();
        IpResolver custom = request -> { calls.incrementAndGet(); return "192.0.2.99"; };
        runner.withBean(IpResolver.class, () -> custom)
                .withBean(IpBlacklist.class, () -> ip -> { checkedIps.add(ip); return checkedIps.size() == 3; })
                .run(context -> {
                    assertThat(context).hasSingleBean(IpResolver.class);
                    assertSame(custom, context.getBean(IpResolver.class));
                    var interceptor = context.getBean(ApiLimitInterceptor.class);
                    var handler = new HandlerMethod(new Controller(), Controller.class.getMethod("limited"));
                    assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler));
                    assertThrows(ApiLimitException.class, () -> interceptor.preHandle(
                            new MockHttpServletRequest(), new MockHttpServletResponse(), handler));
                    var response = new MockHttpServletResponse();
                    var blocked = assertThrows(IpBlockedException.class,
                            () -> interceptor.preHandle(new MockHttpServletRequest(), response, handler));
                    assertEquals("192.0.2.99", blocked.getIp());
                    assertEquals(200, response.getStatus());
                    assertEquals("", response.getContentAsString());
                    assertEquals(3, calls.get(), "黑名单和限流在同一个请求中只解析一次");
                    assertEquals(java.util.List.of("192.0.2.99", "192.0.2.99", "192.0.2.99"), checkedIps);
                });
    }

    @Test void disablingRateLimitKeepsResolverAvailableToBusinessCode() {
        runner.withPropertyValues("yulinlin.security.limit.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(IpResolver.class).doesNotHaveBean(ApiLimitInterceptor.class);
        });
    }
}
