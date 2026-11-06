package com.yulinlin.security.limit;

import com.yulinlin.security.SecurityLimitProperties;
import com.yulinlin.security.annotation.ApiLimit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiLimitInterceptorTest {
    @RestController static class Controller {
        int calls;
        @ApiLimit(count = 20) @GetMapping("/twenty")
        String twenty() { calls++; return "ok"; }
        @ApiLimit(count = 1) @GetMapping("/one")
        String one() { calls++; return "ok"; }
        @ApiLimit(count = 1, by = LimitBy.DEVICE) @GetMapping("/device")
        String device() { calls++; return "ok"; }
        @GetMapping("/public") String open() { calls++; return "ok"; }
    }

    @RestControllerAdvice static class BusinessErrors {
        @ExceptionHandler(ApiLimitException.class)
        ResponseEntity<Map<String, Object>> limited(ApiLimitException error) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(error.getHeaders())
                    .body(Map.of("code", "BUSINESS_LIMIT", "message", "请休息一下再来操作", "retryIn", error.getRetryAfterSeconds()));
        }
    }

    private static MockHttpServletRequest request(String ip) {
        var request = new MockHttpServletRequest("GET", "/test");
        request.setRemoteAddr(ip);
        return request;
    }

    @Test void twentyMeansOneMinutePerIpAndThrowsWithoutWritingResponse() throws Exception {
        var interceptor = new ApiLimitInterceptor(new SecurityLimitProperties());
        var handler = new HandlerMethod(new Controller(), Controller.class.getDeclaredMethod("twenty"));
        var response = new MockHttpServletResponse();
        for (int i = 0; i < 20; i++) assertTrue(interceptor.preHandle(request("192.0.2.1"), response, handler));
        var error = assertThrows(ApiLimitException.class,
                () -> interceptor.preHandle(request("192.0.2.1"), response, handler));
        assertEquals(20, error.getCount());
        assertEquals(Duration.ofMinutes(1), error.getInterval());
        assertEquals(LimitBy.IP, error.getBy());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertTrue(error.getRetryAfterSeconds() >= 1 && error.getRetryAfterSeconds() <= 60);
        assertEquals(Long.toString(error.getRetryAfterSeconds()), error.getHeaders().getFirst("Retry-After"));
        assertEquals(200, response.getStatus());
        assertEquals("", response.getContentAsString());
        assertNull(response.getHeader("Retry-After"));
        assertFalse(response.isCommitted());
        assertTrue(interceptor.preHandle(request("192.0.2.2"), response, handler));
        var anotherEndpoint = new HandlerMethod(new Controller(), Controller.class.getDeclaredMethod("one"));
        assertTrue(interceptor.preHandle(request("192.0.2.1"), response, anotherEndpoint));
    }

    @Test void businessExceptionHandlerOwnsResponseAndControllerIsNotCalledAgain() throws Exception {
        var controller = new Controller();
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new ApiLimitInterceptor(new SecurityLimitProperties()))
                .setControllerAdvice(new BusinessErrors()).build();
        mvc.perform(get("/one")).andExpect(status().isOk());
        mvc.perform(get("/one")).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("BUSINESS_LIMIT"))
                .andExpect(jsonPath("$.message").value("请休息一下再来操作"));
        assertEquals(1, controller.calls);
    }

    @Test void springDefaultStillReturns429AndRetryAfterWithoutBusinessAdvice() throws Exception {
        var controller = new Controller();
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new ApiLimitInterceptor(new SecurityLimitProperties())).build();
        mvc.perform(get("/one")).andExpect(status().isOk());
        mvc.perform(get("/one")).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        assertEquals(1, controller.calls);
        mvc.perform(get("/public")).andExpect(status().isOk());
    }

    @Test void deviceIsolationAndOptionsStillWork() throws Exception {
        var interceptor = new ApiLimitInterceptor(new SecurityLimitProperties());
        var handler = new HandlerMethod(new Controller(), Controller.class.getDeclaredMethod("device"));
        var response = new MockHttpServletResponse();
        var options = request("192.0.2.1"); options.setMethod("OPTIONS");
        assertTrue(interceptor.preHandle(options, response, handler));
        var first = request("192.0.2.1"); first.addHeader("Device-Id", "one");
        assertTrue(interceptor.preHandle(first, response, handler));
        assertEquals(LimitBy.DEVICE, assertThrows(ApiLimitException.class,
                () -> interceptor.preHandle(first, response, handler)).getBy());
        var second = request("192.0.2.1"); second.addHeader("Device-Id", "two");
        assertTrue(interceptor.preHandle(second, response, handler));
    }
}
