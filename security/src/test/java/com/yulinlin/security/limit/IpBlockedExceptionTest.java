package com.yulinlin.security.limit;

import com.yulinlin.security.SecurityLimitProperties;
import com.yulinlin.security.annotation.ApiLimit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IpBlockedExceptionTest {
    @RestController static class Controller {
        int calls;
        @ApiLimit(count = 1) @GetMapping("/limited")
        public String limited() { calls++; return "ok"; }
        @GetMapping("/open")
        public String open() { calls++; return "ok"; }
    }

    @RestControllerAdvice static class BusinessErrors {
        @ExceptionHandler(IpBlockedException.class)
        ResponseEntity<Map<String, String>> blocked(IpBlockedException error) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "BUSINESS_IP_BLOCKED", "message", "访问受限，请联系客服"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"limited", "open"})
    void blacklistThrowsBeforeWritingResponseEvenWithoutLimitAnnotation(String method) throws Exception {
        var interceptor = new ApiLimitInterceptor(new SecurityLimitProperties(), ip -> true, request -> "192.0.2.1");
        var handler = new HandlerMethod(new Controller(), Controller.class.getMethod(method));
        var response = new MockHttpServletResponse();
        var error = assertThrows(IpBlockedException.class,
                () -> interceptor.preHandle(new MockHttpServletRequest(), response, handler));
        assertEquals("192.0.2.1", error.getIp());
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals(200, response.getStatus());
        assertEquals("", response.getContentAsString());
        assertNull(response.getContentType());
        assertTrue(response.getHeaderNames().isEmpty());
        assertFalse(response.isCommitted());
    }

    @Test void businessAdviceControlsResponseAndControllerIsNotExecuted() throws Exception {
        var controller = new Controller();
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new ApiLimitInterceptor(new SecurityLimitProperties(), ip -> true))
                .setControllerAdvice(new BusinessErrors()).build();
        for (String path : new String[]{"/limited", "/open"}) {
            mvc.perform(get(path)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("BUSINESS_IP_BLOCKED"))
                    .andExpect(jsonPath("$.message").value("访问受限，请联系客服"))
                    .andExpect(jsonPath("$.crypt").doesNotExist());
        }
        assertEquals(0, controller.calls);
    }

    @Test void springDefaultStillReturns403WithoutBusinessAdvice() throws Exception {
        var controller = new Controller();
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new ApiLimitInterceptor(new SecurityLimitProperties(), ip -> true)).build();
        mvc.perform(get("/limited")).andExpect(status().isForbidden());
        assertEquals(0, controller.calls);
    }

    @Test void blacklistRejectionDoesNotConsumeRateLimitAndOptionsStillChecksBlacklist() throws Exception {
        var blocked = new AtomicBoolean(true);
        var interceptor = new ApiLimitInterceptor(new SecurityLimitProperties(), ip -> blocked.get());
        var handler = new HandlerMethod(new Controller(), Controller.class.getMethod("limited"));
        var response = new MockHttpServletResponse();
        assertThrows(IpBlockedException.class,
                () -> interceptor.preHandle(new MockHttpServletRequest(), response, handler));
        var options = new MockHttpServletRequest("OPTIONS", "/limited");
        assertThrows(IpBlockedException.class, () -> interceptor.preHandle(options, response, handler));
        blocked.set(false);
        assertTrue(interceptor.preHandle(options, response, handler));
        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), response, handler));
        assertThrows(ApiLimitException.class,
                () -> interceptor.preHandle(new MockHttpServletRequest(), response, handler));
    }
}
