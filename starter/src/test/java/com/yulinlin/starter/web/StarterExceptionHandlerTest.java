package com.yulinlin.starter.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StarterExceptionHandlerTest {
    @RestController static class Controller {
        @GetMapping("/limited") String limited() {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "默认限流文案") {
                @Override public HttpHeaders getHeaders() {
                    var headers = new HttpHeaders(); headers.set("Retry-After", "37"); return headers;
                }
            };
        }
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @RestControllerAdvice static class BusinessErrors {
        @ExceptionHandler(ResponseStatusException.class)
        ResponseEntity<?> handle(ResponseStatusException error) {
            return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders())
                    .body(Map.of("message", "业务自己的提示"));
        }
    }

    @Test void defaultEnvelopePreservesExceptionHeaders() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Controller()).setControllerAdvice(new StarterExceptionHandler()).build();
        mvc.perform(get("/limited")).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "37"))
                .andExpect(jsonPath("$.code").value(429)).andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.crypt").value(false)).andExpect(jsonPath("$.msg").value("默认限流文案"));
    }

    @Test void higherPriorityBusinessAdviceCanOverrideDefaultEnvelope() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Controller())
                .setControllerAdvice(new StarterExceptionHandler(), new BusinessErrors()).build();
        mvc.perform(get("/limited")).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "37"))
                .andExpect(jsonPath("$.message").value("业务自己的提示"))
                .andExpect(jsonPath("$.msg").doesNotExist());
    }
}
