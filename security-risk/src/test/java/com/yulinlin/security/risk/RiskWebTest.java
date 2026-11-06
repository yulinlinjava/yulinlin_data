package com.yulinlin.security.risk;

import com.yulinlin.security.auth.*;
import com.yulinlin.security.risk.RiskModels.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RiskWebTest {
    static class Registry extends InterceptorRegistry { HandlerInterceptor interceptor() { return (HandlerInterceptor)getInterceptors().getFirst(); } }
    @RestController static class Videos {
        final AtomicInteger calls=new AtomicInteger();
        @GetMapping("/video/{id}") @ApiRisk(value="video",resource="id")
        String url(@PathVariable("id") String id) { calls.incrementAndGet();return "link"; }
        @GetMapping("/public") String publicPage() { return "public"; }
    }
    @RestControllerAdvice static class BusinessRiskErrors {
        @ExceptionHandler(ApiRiskException.class)
        ResponseEntity<Map<String, String>> blocked(ApiRiskException error) {
            // Applications own status, headers and body; this one deliberately omits Retry-After.
            return ResponseEntity.status(403).body(Map.of("code", "VIDEO_RISK", "message", "请稍后再获取播放链接"));
        }
    }
    @Test void authenticatedIdentityIsUsedAndBlockPreventsBusinessInvocation() throws Exception {
        var service=mock(RiskService.class);var manager=mock(ISecurityManager.class);var user=mock(IUserDetails.class);
        when(user.getUserId()).thenReturn("real-user");when(manager.getUserDetails()).thenReturn(user);
        when(service.checkAndRecord(any())).thenReturn(new Decision(false,Status.BLOCKED,999999,List.of()));
        var registry=new Registry();new RiskWebMvcConfigurer(service,()->manager).addInterceptors(registry);
        var controller=new Videos();
        var mvc=MockMvcBuilders.standaloneSetup(controller).addInterceptors(registry.interceptor()).build();
        var result=mvc.perform(get("/video/v123").param("userId","victim"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","1")).andReturn();
        var error=assertInstanceOf(ApiRiskException.class,result.getResolvedException());
        assertEquals("video",error.getEvent().behaviorType());
        assertEquals(999999,error.getDecision().blockedUntil());
        assertEquals(0,controller.calls.get());
        verify(service).checkAndRecord(new BehaviorEvent("real-user","video","v123"));
        mvc.perform(get("/public")).andExpect(status().isOk());
        when(manager.getUserDetails()).thenReturn(null);
        mvc.perform(get("/video/v123")).andExpect(status().isUnauthorized());
        verifyNoMoreInteractions(service);
    }
    @Test void observationAllowsBusinessAndAsyncRedispatchDoesNotCountAgain() throws Exception {
        var service=mock(RiskService.class);var manager=mock(ISecurityManager.class);var user=mock(IUserDetails.class);
        when(user.getUserId()).thenReturn("u");when(manager.getUserDetails()).thenReturn(user);
        when(service.checkAndRecord(any())).thenReturn(new Decision(true,Status.SUSPECTED,0,List.of()));
        var registry=new Registry();new RiskWebMvcConfigurer(service,()->manager).addInterceptors(registry);
        var controller=new Videos();
        var mvc=MockMvcBuilders.standaloneSetup(controller).addInterceptors(registry.interceptor()).build();
        var result=mvc.perform(get("/video/v")).andExpect(status().isOk()).andReturn();
        assertEquals(1,controller.calls.get());
        registry.interceptor().preHandle(result.getRequest(),result.getResponse(),
                new org.springframework.web.method.HandlerMethod(controller,Videos.class.getDeclaredMethod("url",String.class)));
        verify(service,times(1)).checkAndRecord(any());
    }
    @Test void rejectionThrowsWithoutWritingResponseAndBusinessAdviceCanHandleIt() throws Exception {
        var service=mock(RiskService.class);var manager=mock(ISecurityManager.class);var user=mock(IUserDetails.class);
        when(user.getUserId()).thenReturn("u");when(manager.getUserDetails()).thenReturn(user);
        when(service.checkAndRecord(any())).thenReturn(new Decision(false,Status.BLOCKED,System.currentTimeMillis()+120_000,List.of()));
        var registry=new Registry();new RiskWebMvcConfigurer(service,()->manager).addInterceptors(registry);
        var controller=new Videos();
        var request=new MockHttpServletRequest("GET","/video/v");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,Map.of("id","v"));
        var response=new MockHttpServletResponse();
        var handler=new HandlerMethod(controller,Videos.class.getDeclaredMethod("url",String.class));
        var error=assertThrows(ApiRiskException.class,()->registry.interceptor().preHandle(request,response,handler));
        assertEquals(new BehaviorEvent("u","video","v"),error.getEvent());
        assertEquals(200,response.getStatus());assertEquals("",response.getContentAsString());
        assertNull(response.getHeader("Retry-After"));assertFalse(response.isCommitted());

        var mvc=MockMvcBuilders.standaloneSetup(controller).addInterceptors(registry.interceptor())
                .setControllerAdvice(new BusinessRiskErrors()).build();
        mvc.perform(get("/video/v")).andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.code").value("VIDEO_RISK"))
                .andExpect(jsonPath("$.message").value("请稍后再获取播放链接"));
        assertEquals(0,controller.calls.get());
    }
}

