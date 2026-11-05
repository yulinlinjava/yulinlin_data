package com.yulinlin.security.risk;

import com.yulinlin.security.auth.*;
import com.yulinlin.security.risk.RiskModels.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
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
        @GetMapping("/video/{id}") @ApiRisk(value="video",resourceParam="id")
        String url(@PathVariable("id") String id) { calls.incrementAndGet();return "link"; }
        @GetMapping("/public") String publicPage() { return "public"; }
    }
    @Test void authenticatedIdentityIsUsedAndBlockPreventsBusinessInvocation() throws Exception {
        var service=mock(RiskService.class);var manager=mock(ISecurityManager.class);var user=mock(IUserDetails.class);
        when(user.getUserId()).thenReturn("real-user");when(manager.getUserDetails()).thenReturn(user);
        when(service.checkAndRecord(any())).thenReturn(new Decision(false,Status.BLOCKED,999999,List.of()));
        var registry=new Registry();new RiskWebMvcConfigurer(service,()->manager).addInterceptors(registry);
        var controller=new Videos();
        var mvc=MockMvcBuilders.standaloneSetup(controller).addInterceptors(registry.interceptor()).build();
        mvc.perform(get("/video/v123").param("userId","victim")).andExpect(status().isTooManyRequests());
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
}

